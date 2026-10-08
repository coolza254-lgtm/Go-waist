package com.gowaist.app.data.repo

import androidx.room.withTransaction
import com.gowaist.app.data.db.ExerciseDao
import com.gowaist.app.data.db.ExerciseEntity
import com.gowaist.app.data.db.GoWaistDatabase
import com.gowaist.app.data.db.PersonalRecordEntity
import com.gowaist.app.data.db.SessionDao
import com.gowaist.app.data.db.SessionEntity
import com.gowaist.app.data.db.SessionExerciseEntity
import com.gowaist.app.data.db.SetEntity
import com.gowaist.app.data.db.SetRow
import com.gowaist.app.data.key
import com.gowaist.app.data.toFacts
import com.gowaist.app.data.toLocalDateTime
import com.gowaist.core.stats.WorkoutMath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

data class SessionExerciseView(
    val entry: SessionExerciseEntity,
    val exercise: ExerciseEntity,
    val sets: List<SetEntity>,
)

data class SessionDetail(val session: SessionEntity, val exercises: List<SessionExerciseView>)

data class SessionSummary(
    val session: SessionEntity,
    val exerciseNames: List<String>,
    val summary: WorkoutMath.Summary,
)

data class FinishResult(
    val sessionId: Long,
    val durationSec: Long,
    val summary: WorkoutMath.Summary,
    val records: List<PersonalRecordEntity>,
)

/** What the user did last time and their best for an exercise, shown next to the input. */
data class ExerciseMemory(val lastSets: List<SetEntity>, val bests: WorkoutMath.Bests)

@Singleton
class WorkoutRepository @Inject constructor(
    private val db: GoWaistDatabase,
    private val dao: SessionDao,
    private val exerciseDao: ExerciseDao,
    private val library: LibraryRepository,
    private val body: BodyRepository,
) {
    val draft: Flow<SessionEntity?> = dao.observeDraft()

    val setRows: Flow<List<SetRow>> = dao.observeFinishedSetRows()

    val finishedSessions: Flow<List<SessionEntity>> = dao.observeFinished()

    val history: Flow<List<SessionSummary>> =
        combine(dao.observeFinished(), dao.observeFinishedSetRows(), exerciseDao.observeAll(), body.bodyweightKg) { sessions, rows, exercises, bw ->
            val exById = exercises.associateBy { it.id }
            val facts = exById.mapValues { it.value.toFacts() }
            val bySession = rows.groupBy { it.sessionId }
            sessions.map { s ->
                val r = bySession[s.id].orEmpty()
                SessionSummary(
                    session = s,
                    exerciseNames = r.map { it.exerciseId }.distinct().mapNotNull { exById[it]?.nameTh },
                    summary = WorkoutMath.summarize(r.map { it.toFacts() }, facts, bw),
                )
            }
        }

    fun observeDetail(sessionId: Long): Flow<SessionDetail?> =
        combine(dao.observe(sessionId), dao.observeExercises(sessionId), dao.observeSets(sessionId), exerciseDao.observeAll()) { s, entries, sets, exercises ->
            if (s == null) return@combine null
            val exById = exercises.associateBy { it.id }
            val setsBy = sets.groupBy { it.sessionExerciseId }
            SessionDetail(s, entries.mapNotNull { e -> exById[e.exerciseId]?.let { SessionExerciseView(e, it, setsBy[e.id].orEmpty().sortedBy { st -> st.setOrder }) } })
        }

    suspend fun session(id: Long) = dao.get(id)

    suspend fun currentDraft() = dao.getDraft()

    /** Starts a session (or returns the open draft). Template items become the exercise list with targets. */
    suspend fun start(
        templateId: Long? = null,
        planDayId: Long? = null,
        name: String = "",
        startAt: Long = System.currentTimeMillis(),
        draft: Boolean = true,
        timerMode: String? = null,
    ): Long = db.withTransaction {
        if (draft) dao.getDraft()?.let { return@withTransaction it.id }
        val template = templateId?.let { library.template(it) }
        val date = startAt.toLocalDateTime().toLocalDate()
        val id = dao.insert(
            SessionEntity(
                startAt = startAt,
                endAt = if (draft) null else startAt + 45 * 60_000,
                localDate = date.key(),
                name = name.ifBlank { template?.template?.name ?: "" },
                templateId = templateId,
                planDayId = planDayId,
                isDraft = draft,
                timerMode = timerMode,
            ),
        )
        template?.items?.forEachIndexed { i, it ->
            dao.insertExercise(
                SessionExerciseEntity(
                    sessionId = id, exerciseId = it.exercise.id, itemOrder = i, supersetGroup = it.item.supersetGroup,
                    restSec = it.item.restSec, targetSets = it.item.targetSets, targetRepsOrSec = it.item.targetRepsOrSec,
                ),
            )
        }
        id
    }

    suspend fun addExercise(sessionId: Long, exercise: ExerciseEntity, restSec: Int? = null, targetSets: Int? = null, targetRepsOrSec: Int? = null): Long {
        val order = (dao.getExercises(sessionId).maxOfOrNull { it.itemOrder } ?: -1) + 1
        return dao.insertExercise(
            SessionExerciseEntity(
                sessionId = sessionId, exerciseId = exercise.id, itemOrder = order,
                restSec = restSec ?: exercise.defaultRestSec, targetSets = targetSets, targetRepsOrSec = targetRepsOrSec,
            ),
        )
    }

    suspend fun removeExercise(entryId: Long) = dao.deleteExercise(entryId)

    suspend fun updateExercise(entry: SessionExerciseEntity) = dao.updateExercise(entry)

    suspend fun reorder(sessionId: Long, orderedEntryIds: List<Long>) {
        val entries = dao.getExercises(sessionId).associateBy { it.id }
        dao.updateExercises(orderedEntryIds.mapIndexedNotNull { i, id -> entries[id]?.copy(itemOrder = i) })
    }

    /** Puts the given entries in one superset group (or removes grouping when [group] is null). */
    suspend fun setGroup(sessionId: Long, entryIds: Set<Long>, group: Int?) {
        val entries = dao.getExercises(sessionId)
        dao.updateExercises(entries.filter { it.id in entryIds }.map { it.copy(supersetGroup = group) })
    }

    suspend fun nextGroupNumber(sessionId: Long): Int = (dao.getExercises(sessionId).mapNotNull { it.supersetGroup }.maxOrNull() ?: 0) + 1

    suspend fun logSet(set: SetEntity): Long = dao.insertSet(set)

    suspend fun updateSet(set: SetEntity) = dao.updateSet(set)

    suspend fun deleteSet(id: Long) = dao.deleteSet(id)

    suspend fun updateSession(s: SessionEntity) = dao.update(s)

    suspend fun discard(sessionId: Long) = dao.delete(sessionId)

    suspend fun delete(sessionId: Long) = dao.delete(sessionId)

    /** Marks the session finished and stores the personal records it set. */
    suspend fun finish(sessionId: Long, feeling: Int?, note: String, endAt: Long = System.currentTimeMillis()): FinishResult {
        val s = dao.get(sessionId) ?: error("session $sessionId missing")
        val finished = s.copy(isDraft = false, endAt = s.endAt?.takeIf { !s.isDraft } ?: endAt, feeling = feeling, note = note)
        dao.update(finished)
        // Drop exercises that were added but never logged.
        val logged = dao.setRowsForSession(sessionId).map { it.set.sessionExerciseId }.toSet()
        dao.getExercises(sessionId).filter { it.id !in logged }.forEach { dao.deleteExercise(it.id) }
        val records = recomputeRecords(sessionId)
        val rows = dao.setRowsForSession(sessionId)
        val facts = exerciseDao.getAll().associate { it.id to it.toFacts() }
        val summary = WorkoutMath.summarize(rows.map { it.toFacts() }, facts, body.currentBodyweight())
        return FinishResult(sessionId, ((finished.endAt ?: endAt) - finished.startAt) / 1000, summary, records)
    }

    /** Recomputes the records set by one session against all earlier sessions. */
    suspend fun recomputeRecords(sessionId: Long): List<PersonalRecordEntity> = db.withTransaction {
        val recordDao = db.recordDao()
        recordDao.deleteForSession(sessionId)
        val session = dao.get(sessionId) ?: return@withTransaction emptyList()
        if (session.isDraft) return@withTransaction emptyList()
        val startById = dao.getAll().associate { it.id to it.startAt }
        val all = dao.finishedSetRows()
        val mine = all.filter { it.sessionId == sessionId }
        val before = all.filter { (startById[it.sessionId] ?: 0) < session.startAt }
        val bw = body.currentBodyweight()
        val exercises = exerciseDao.getAll().associateBy { it.id }
        val records = mine.map { it.exerciseId }.distinct().flatMap { exId ->
            val ex = exercises[exId] ?: return@flatMap emptyList()
            WorkoutMath.newRecords(mine.map { it.toFacts() }, before.map { it.toFacts() }, ex.toFacts(), bw).map {
                PersonalRecordEntity(
                    exerciseId = exId, type = it.type, value = it.value, previous = it.previous,
                    achievedAt = session.startAt, localDate = session.localDate, sessionId = sessionId,
                )
            }
        }
        if (records.isNotEmpty()) recordDao.insertAll(records)
        records
    }

    suspend fun memory(exerciseId: Long, excludeSessionId: Long): ExerciseMemory {
        val rows = dao.finishedSetRows().filter { it.exerciseId == exerciseId && it.sessionId != excludeSessionId }
        val ex = exerciseDao.get(exerciseId) ?: return ExerciseMemory(emptyList(), WorkoutMath.Bests())
        val lastSession = rows.lastOrNull()?.sessionId
        return ExerciseMemory(
            lastSets = rows.filter { it.sessionId == lastSession }.map { it.set },
            bests = WorkoutMath.bests(rows.map { it.toFacts() }, ex.toFacts(), body.currentBodyweight()),
        )
    }

    suspend fun allSetRows(): List<SetRow> = dao.finishedSetRows()

    suspend fun finishedSessionsNow(): List<SessionEntity> = finishedSessions.first()
}

package com.gowaist.app.data.repo

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.gowaist.app.data.Seeder
import com.gowaist.app.data.db.BadgeEntity
import com.gowaist.app.data.db.BodyMetricEntity
import com.gowaist.app.data.db.ChainEntity
import com.gowaist.app.data.db.ChainStepEntity
import com.gowaist.app.data.db.ExerciseEntity
import com.gowaist.app.data.db.GoWaistDatabase
import com.gowaist.app.data.db.GoalEntity
import com.gowaist.app.data.db.PersonalRecordEntity
import com.gowaist.app.data.db.PlanDayEntity
import com.gowaist.app.data.db.PlanEntity
import com.gowaist.app.data.db.RewardEntity
import com.gowaist.app.data.db.RunEntity
import com.gowaist.app.data.db.SessionEntity
import com.gowaist.app.data.db.SessionExerciseEntity
import com.gowaist.app.data.db.SetEntity
import com.gowaist.app.data.db.SuggestionStateEntity
import com.gowaist.app.data.db.TemplateEntity
import com.gowaist.app.data.db.TemplateItemEntity
import com.gowaist.app.data.settings.AppSettings
import com.gowaist.app.data.settings.SettingsRepository
import com.gowaist.app.data.toLocalDateTime
import com.gowaist.core.Format
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/** Everything the app stores, in one versioned JSON document. */
@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val version: Int = CURRENT_VERSION,
    val exportedAt: Long = System.currentTimeMillis(),
    val settings: AppSettings = AppSettings(),
    val runs: List<RunEntity> = emptyList(),
    val exercises: List<ExerciseEntity> = emptyList(),
    val chains: List<ChainEntity> = emptyList(),
    val chainSteps: List<ChainStepEntity> = emptyList(),
    val templates: List<TemplateEntity> = emptyList(),
    val templateItems: List<TemplateItemEntity> = emptyList(),
    val sessions: List<SessionEntity> = emptyList(),
    val sessionExercises: List<SessionExerciseEntity> = emptyList(),
    val sets: List<SetEntity> = emptyList(),
    val plans: List<PlanEntity> = emptyList(),
    val planDays: List<PlanDayEntity> = emptyList(),
    val goals: List<GoalEntity> = emptyList(),
    val rewards: List<RewardEntity> = emptyList(),
    val badges: List<BadgeEntity> = emptyList(),
    val bodyMetrics: List<BodyMetricEntity> = emptyList(),
    val records: List<PersonalRecordEntity> = emptyList(),
    val suggestions: List<SuggestionStateEntity> = emptyList(),
    val programs: ProgramState = ProgramState(),
) {
    companion object {
        const val FORMAT = "gowaist-backup"
        const val CURRENT_VERSION = 1
    }
}

class BackupException(message: String) : Exception(message)

@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: GoWaistDatabase,
    private val settings: SettingsRepository,
    private val seeder: Seeder,
    private val programs: ProgramRepository,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }

    suspend fun snapshot(): BackupFile = db.withTransaction {
        BackupFile(
            settings = settings.current(),
            runs = db.runDao().getAll(),
            exercises = db.exerciseDao().getAll(),
            chains = db.chainDao().getAll().map { it.chain },
            chainSteps = db.chainDao().allSteps(),
            templates = db.templateDao().getAllTemplates(),
            templateItems = db.templateDao().getAllItems(),
            sessions = db.sessionDao().getAll(),
            sessionExercises = db.sessionDao().getAllExercises(),
            sets = db.sessionDao().getAllSets(),
            plans = db.planDao().getAll(),
            planDays = db.planDao().getAllDays(),
            goals = db.goalDao().getAll(),
            rewards = db.goalDao().getAllRewards(),
            badges = db.goalDao().getBadges(),
            bodyMetrics = db.bodyDao().getAll(),
            records = db.recordDao().getAll(),
            suggestions = db.suggestionDao().getAll(),
            programs = programs.current(),
        )
    }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    fun decode(text: String): BackupFile {
        val file = runCatching { json.decodeFromString(BackupFile.serializer(), text) }
            .getOrElse { throw BackupException("ไฟล์ไม่ใช่ข้อมูลสำรองของ Go waist") }
        if (file.format != BackupFile.FORMAT) throw BackupException("ไฟล์ไม่ใช่ข้อมูลสำรองของ Go waist")
        if (file.version > BackupFile.CURRENT_VERSION) throw BackupException("ไฟล์มาจากแอปเวอร์ชันใหม่กว่า กรุณาอัปเดตแอปก่อน")
        return file
    }

    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        val text = encode(snapshot())
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            ?: throw BackupException("เขียนไฟล์ไม่ได้")
    }

    suspend fun importFrom(uri: Uri): BackupFile = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw BackupException("อ่านไฟล์ไม่ได้")
        val file = decode(text)
        restore(file)
        file
    }

    /**
     * Replaces all data with the backup content in a single transaction (all or nothing).
     * The current data is first saved to app storage as a safety copy.
     */
    suspend fun restore(file: BackupFile) = withContext(Dispatchers.IO) {
        runCatching { File(context.filesDir, "before_restore.json").writeText(encode(snapshot())) }
        db.withTransaction {
            deleteAllRows()
            db.exerciseDao().insertAll(file.exercises)
            db.chainDao().insertAllChains(file.chains)
            db.chainDao().insertAllSteps(file.chainSteps)
            db.templateDao().insertAllTemplates(file.templates)
            db.templateDao().insertAllItems(file.templateItems)
            db.planDao().insertAllPlans(file.plans)
            db.planDao().insertAllDays(file.planDays)
            db.sessionDao().insertAllSessions(file.sessions)
            db.sessionDao().insertAllExercises(file.sessionExercises)
            db.sessionDao().insertAllSets(file.sets)
            db.runDao().insertAll(file.runs.map { r -> r.copy(sourceImagePath = r.sourceImagePath?.takeIf { File(it).exists() }) })
            db.goalDao().insertAllGoals(file.goals)
            db.goalDao().insertAllRewards(file.rewards)
            db.goalDao().insertAllBadges(file.badges)
            db.bodyDao().insertAll(file.bodyMetrics)
            db.recordDao().replaceAll(file.records)
            db.suggestionDao().insertAll(file.suggestions)
        }
        settings.replace(file.settings.copy(onboardingDone = true))
        programs.replace(file.programs)
        seeder.ensureSeeded()
    }

    /** Deletes every record and stored image, then reinstalls the built-in library. */
    suspend fun wipeAll() = withContext(Dispatchers.IO) {
        db.withTransaction { deleteAllRows() }
        File(context.filesDir, "run_images").deleteRecursively()
        seeder.ensureSeeded()
    }

    private fun deleteAllRows() {
        val sql = db.openHelper.writableDatabase
        listOf(
            "personal_records", "sets", "session_exercises", "sessions", "plan_days", "plans", "rewards", "goals",
            "template_items", "templates", "chain_steps", "chains", "runs", "exercises", "badges", "body_metrics", "suggestion_states",
        ).forEach { sql.execSQL("DELETE FROM `$it`") }
    }

    // ------------------------------------------------------------------ CSV

    private fun csv(vararg values: Any?): String = values.joinToString(",") { v ->
        val s = v?.toString() ?: ""
        if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }

    suspend fun runsCsv(): String {
        val dateFmt = DateTimeFormatter.ofPattern("HH:mm")
        val sb = StringBuilder("﻿")
        sb.appendLine(csv("date", "start_time", "distance_km", "duration", "duration_sec", "pace_per_km", "calories", "avg_hr", "tags", "note"))
        db.runDao().getAll().sortedBy { it.startAt }.forEach { r ->
            sb.appendLine(
                csv(
                    r.localDate, r.startAt.toLocalDateTime().format(dateFmt), Format.decimal(r.distanceM / 1000.0, 3),
                    Format.duration(r.durationSec), r.durationSec, Format.pace(r.avgPaceSecPerKm), r.calories, r.avgHr,
                    r.tags.joinToString("|"), r.note,
                ),
            )
        }
        return sb.toString()
    }

    suspend fun setsCsv(): String {
        val exercises = db.exerciseDao().getAll().associateBy { it.id }
        val sessions = db.sessionDao().getAll().associateBy { it.id }
        val sb = StringBuilder("﻿")
        sb.appendLine(
            csv("date", "session_id", "session_name", "exercise", "exercise_en", "set_no", "set_type", "reps", "duration_sec", "added_kg", "assist_kg", "distance_m", "rpe", "rir", "note"),
        )
        db.sessionDao().finishedSetRows().forEach { row ->
            val s = row.set
            val ex = exercises[row.exerciseId]
            sb.appendLine(
                csv(
                    row.localDate, row.sessionId, sessions[row.sessionId]?.name, ex?.nameTh, ex?.nameEn, s.setOrder + 1, s.setType.name,
                    s.reps, s.durationSec, s.addedWeightKg, s.assistKg, s.distanceM, s.rpe, s.rir, s.note,
                ),
            )
        }
        return sb.toString()
    }

    suspend fun writeText(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            ?: throw BackupException("เขียนไฟล์ไม่ได้")
    }
}

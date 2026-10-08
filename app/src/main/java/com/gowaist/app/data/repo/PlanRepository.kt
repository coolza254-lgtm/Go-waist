package com.gowaist.app.data.repo

import androidx.room.withTransaction
import com.gowaist.app.data.db.GoWaistDatabase
import com.gowaist.app.data.db.PlanDao
import com.gowaist.app.data.db.PlanDayEntity
import com.gowaist.app.data.db.PlanEntity
import com.gowaist.app.data.db.PlanMode
import com.gowaist.app.data.db.RunDao
import com.gowaist.app.data.db.SessionDao
import com.gowaist.app.data.key
import com.gowaist.app.data.toLocalDate
import com.gowaist.app.data.toPlanned
import com.gowaist.core.model.PlanDayStatus
import com.gowaist.core.model.PlanDayType
import com.gowaist.core.plan.AdaptiveRules
import com.gowaist.core.plan.AdjustmentType
import com.gowaist.core.plan.DayActuals
import com.gowaist.core.plan.PlanAdjustment
import com.gowaist.core.plan.PlanMatcher
import com.gowaist.core.seed.PlanSeed
import com.gowaist.core.stats.weekStart
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** One weekday of a user-defined weekly schedule. */
@Serializable
data class CustomDaySpec(
    val type: PlanDayType = PlanDayType.REST,
    val distanceKm: Double? = null,
    val durationMin: Int? = null,
    val templateId: Long? = null,
    val note: String = "",
)

@Singleton
class PlanRepository @Inject constructor(
    private val db: GoWaistDatabase,
    private val dao: PlanDao,
    private val runDao: RunDao,
    private val sessionDao: SessionDao,
    private val library: LibraryRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }

    val activePlans: Flow<List<PlanEntity>> = dao.observeActive()
    val allPlans: Flow<List<PlanEntity>> = dao.observeAll()

    fun days(from: LocalDate, to: LocalDate): Flow<List<PlanDayEntity>> = dao.observeActiveDays(from.key(), to.key())

    suspend fun daysNow(from: LocalDate, to: LocalDate) = dao.activeDays(from.key(), to.key())

    suspend fun day(id: Long) = dao.getDay(id)

    suspend fun plan(id: Long) = dao.get(id)

    suspend fun daysOf(planId: Long) = dao.daysOf(planId)

    suspend fun createFromSeed(key: String, start: LocalDate, adaptive: Boolean): Long = db.withTransaction {
        val def = PlanSeed.all.first { it.key == key }
        val monday = start.weekStart()
        val planId = dao.insert(
            PlanEntity(
                name = def.nameTh, mode = if (adaptive) PlanMode.ADAPTIVE else PlanMode.PREBUILT, sourceKey = key,
                startDate = monday.key(), weeks = def.weeks.size,
            ),
        )
        val templateIds = mutableMapOf<String, Long?>()
        val days = def.weeks.flatMapIndexed { w, week ->
            week.mapIndexedNotNull { d, spec ->
                val date = monday.plusDays(w * 7L + d)
                if (date.isBefore(start)) return@mapIndexedNotNull null
                val tid = spec.templateKey?.let { k -> templateIds.getOrPut(k) { library.templateBySeedKey(k)?.id } }
                PlanDayEntity(
                    planId = planId, date = date.key(), type = spec.type,
                    targetDistanceM = spec.distanceKm?.times(1000), targetDurationMin = spec.durationMin,
                    templateId = tid, note = spec.note.orEmpty(),
                )
            }
        }
        dao.insertDays(days)
        planId
    }

    suspend fun createCustom(name: String, pattern: List<CustomDaySpec>, weeks: Int, start: LocalDate, adaptive: Boolean): Long = db.withTransaction {
        require(pattern.size == 7)
        val monday = start.weekStart()
        val planId = dao.insert(
            PlanEntity(
                name = name, mode = if (adaptive) PlanMode.ADAPTIVE else PlanMode.CUSTOM, startDate = monday.key(), weeks = weeks,
                weeklyPattern = json.encodeToString(ListSerializer(CustomDaySpec.serializer()), pattern),
            ),
        )
        val days = (0 until weeks).flatMap { w ->
            pattern.mapIndexedNotNull { d, spec ->
                val date = monday.plusDays(w * 7L + d)
                if (date.isBefore(start)) return@mapIndexedNotNull null
                PlanDayEntity(
                    planId = planId, date = date.key(), type = spec.type, targetDistanceM = spec.distanceKm?.times(1000),
                    targetDurationMin = spec.durationMin, templateId = spec.templateId, note = spec.note,
                )
            }
        }
        dao.insertDays(days)
        planId
    }

    fun patternOf(plan: PlanEntity): List<CustomDaySpec>? =
        plan.weeklyPattern?.let { runCatching { json.decodeFromString(ListSerializer(CustomDaySpec.serializer()), it) }.getOrNull() }

    suspend fun setActive(planId: Long, active: Boolean) {
        dao.get(planId)?.let { dao.update(it.copy(isActive = active)) }
    }

    suspend fun deletePlan(planId: Long) = dao.delete(planId)

    suspend fun updateDay(day: PlanDayEntity) = dao.updateDay(day)

    suspend fun addDay(day: PlanDayEntity) = dao.insertDays(listOf(day))

    suspend fun deleteDay(id: Long) = dao.deleteDay(id)

    /** Moves every not-yet-done day from [from] onwards by [days] (used to postpone a plan). */
    suspend fun shiftPlan(planId: Long, from: LocalDate, days: Long) = db.withTransaction {
        val list = dao.daysOf(planId).filter { !it.date.toLocalDate().isBefore(from) && it.status == PlanDayStatus.PENDING }
        dao.updateDays(list.map { it.copy(date = it.date.toLocalDate().plusDays(days).key()) })
    }

    /** Matches real runs and sessions to plan days and stores the resulting statuses. */
    suspend fun syncStatuses(today: LocalDate = LocalDate.now()) {
        val days = dao.getAllDays()
        if (days.isEmpty()) return
        val runsByDate = runDao.getAll().groupBy { it.localDate }
        val sessionsByDate = sessionDao.getAll().filter { !it.isDraft }.groupBy { it.localDate }
        val changed = days.mapNotNull { d ->
            val runs = runsByDate[d.date].orEmpty()
            val actual = DayActuals(runs.sumOf { it.distanceM }, runs.size, sessionsByDate[d.date].orEmpty().size)
            val status = PlanMatcher.status(d.toPlanned(), actual, today)
            if (status != d.status) d.copy(status = status) else null
        }
        if (changed.isNotEmpty()) dao.updateDays(changed)
    }

    /** Rule-based adjustments for adaptive plans, evaluated on the last two full weeks. */
    suspend fun adjustments(today: LocalDate = LocalDate.now()): List<Pair<PlanEntity, PlanAdjustment>> {
        syncStatuses(today)
        val plans = dao.getAll().filter { it.isActive && it.mode == PlanMode.ADAPTIVE }
        if (plans.isEmpty()) return emptyList()
        val runs = runDao.getAll()
        val recentFrom = today.minusDays(14)
        val olderFrom = today.minusDays(42)
        val easyDates = dao.getAllDays().filter { it.type == PlanDayType.RUN_EASY }.map { it.date }.toSet()
        fun paces(from: LocalDate, to: LocalDate) = runs.filter {
            val d = it.localDate.toLocalDate()
            !d.isBefore(from) && d.isBefore(to) && (it.localDate in easyDates || easyDates.isEmpty()) && it.distanceM >= 1000
        }.mapNotNull { it.avgPaceSecPerKm ?: (it.durationSec / (it.distanceM / 1000)) }
        return plans.flatMap { plan ->
            val days = dao.daysOf(plan.id)
            val statuses = days.map { it.toPlanned() to it.status }
            AdaptiveRules.evaluate(statuses, today, paces(recentFrom, today), paces(olderFrom, recentFrom), plan.targetPaceSecPerKm)
                .map { plan to it }
        }
    }

    suspend fun apply(plan: PlanEntity, adj: PlanAdjustment, today: LocalDate = LocalDate.now()) = db.withTransaction {
        val end = adj.weekStart.plusDays(13)
        val pending = dao.daysOf(plan.id).filter {
            val d = it.date.toLocalDate()
            !d.isBefore(today) && !d.isAfter(end) && it.status == PlanDayStatus.PENDING
        }
        when (adj.type) {
            AdjustmentType.INCREASE_VOLUME, AdjustmentType.REDUCE_VOLUME -> dao.updateDays(
                pending.filter { it.type.isRun && it.targetDistanceM != null }.map {
                    it.copy(targetDistanceM = AdaptiveRules.scaleDistance(it.targetDistanceM!!, adj.value))
                },
            )
            AdjustmentType.NEW_TARGET_PACE -> {
                dao.update(plan.copy(targetPaceSecPerKm = adj.value))
                dao.updateDays(pending.filter { it.type == PlanDayType.RUN_EASY || it.type == PlanDayType.RUN_LONG }.map { it.copy(targetPaceSecPerKm = adj.value) })
            }
        }
    }
}

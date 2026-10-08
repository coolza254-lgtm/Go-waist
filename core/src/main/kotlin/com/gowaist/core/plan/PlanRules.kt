package com.gowaist.core.plan

import com.gowaist.core.model.PlanDayStatus
import com.gowaist.core.model.PlanDayType
import com.gowaist.core.stats.weekStart
import java.time.LocalDate
import kotlin.math.min

data class PlannedDay(
    val id: Long,
    val date: LocalDate,
    val type: PlanDayType,
    val targetDistanceM: Double? = null,
    val targetDurationMin: Int? = null,
    val targetPaceSecPerKm: Double? = null,
)

/** What the user actually did on a calendar day. */
data class DayActuals(
    val runDistanceM: Double = 0.0,
    val runCount: Int = 0,
    val bwSessions: Int = 0,
) {
    val any: Boolean get() = runCount > 0 || bwSessions > 0
}

object PlanMatcher {
    /**
     * Status of a planned day given what happened that day.
     * Runs reaching 80% of the target count as done, more than 125% as "over".
     */
    fun status(day: PlannedDay, actual: DayActuals, today: LocalDate): PlanDayStatus {
        val past = day.date.isBefore(today)
        return when {
            day.type == PlanDayType.REST -> if (actual.any) PlanDayStatus.OVER else PlanDayStatus.REST
            day.type.isRun -> {
                if (actual.runCount == 0) {
                    when {
                        actual.bwSessions > 0 -> PlanDayStatus.PARTIAL
                        past -> PlanDayStatus.MISSED
                        else -> PlanDayStatus.PENDING
                    }
                } else {
                    val target = day.targetDistanceM
                    when {
                        target == null || target <= 0 -> PlanDayStatus.DONE
                        actual.runDistanceM > target * 1.25 -> PlanDayStatus.OVER
                        actual.runDistanceM >= target * 0.8 -> PlanDayStatus.DONE
                        else -> PlanDayStatus.PARTIAL
                    }
                }
            }
            else -> when {
                actual.bwSessions > 1 -> PlanDayStatus.OVER
                actual.bwSessions == 1 -> PlanDayStatus.DONE
                actual.runCount > 0 -> PlanDayStatus.PARTIAL
                past -> PlanDayStatus.MISSED
                else -> PlanDayStatus.PENDING
            }
        }
    }

    fun isCompleted(status: PlanDayStatus) = status == PlanDayStatus.DONE || status == PlanDayStatus.OVER
}

enum class LoadWarningType { HEAVY_LEGS_BEFORE_LONG, NO_REST_DAY, LOAD_SPIKE, HARD_BACK_TO_BACK }

data class LoadWarning(val type: LoadWarningType, val date: LocalDate, val value: Double = 0.0)

object LoadRules {
    /** Rough weekly load: kilometres planned plus 5 "km-equivalents" per bodyweight session. */
    fun weekLoad(days: List<PlannedDay>): Double =
        days.sumOf { d ->
            when {
                d.type.isRun -> (d.targetDistanceM ?: (d.targetDurationMin?.let { it * 1000.0 / 6.5 } ?: 5000.0)) / 1000.0
                d.type.isBodyweight -> 5.0
                else -> 0.0
            }
        }

    private fun isHard(t: PlanDayType) = t == PlanDayType.RUN_INTERVAL || t == PlanDayType.RUN_TEMPO || t == PlanDayType.RUN_LONG || t.isHeavyLegs

    fun check(days: List<PlannedDay>): List<LoadWarning> {
        val out = mutableListOf<LoadWarning>()
        val byDate = days.groupBy { it.date }
        // Heavy legs the day before a long run.
        days.filter { it.type.isHeavyLegs }.forEach { d ->
            if (byDate[d.date.plusDays(1)]?.any { it.type == PlanDayType.RUN_LONG } == true) {
                out += LoadWarning(LoadWarningType.HEAVY_LEGS_BEFORE_LONG, d.date)
            }
        }
        // Two hard days in a row (other than the legs→long case above).
        days.filter { isHard(it.type) }.forEach { d ->
            val next = byDate[d.date.plusDays(1)].orEmpty()
            if (next.any { isHard(it.type) } && !(d.type.isHeavyLegs && next.any { it.type == PlanDayType.RUN_LONG })) {
                out += LoadWarning(LoadWarningType.HARD_BACK_TO_BACK, d.date)
            }
        }
        val weeks = days.groupBy { it.date.weekStart() }.toSortedMap()
        weeks.forEach { (ws, list) ->
            val dates = (0L..6L).map { ws.plusDays(it) }
            val hasRest = dates.any { date -> byDate[date].isNullOrEmpty() || byDate[date]!!.any { it.type == PlanDayType.REST } }
            if (!hasRest) out += LoadWarning(LoadWarningType.NO_REST_DAY, ws)
            val prev = weeks[ws.minusWeeks(1)]
            if (prev != null) {
                val a = weekLoad(prev)
                val b = weekLoad(list)
                if (a > 0 && b > a * 1.3) out += LoadWarning(LoadWarningType.LOAD_SPIKE, ws, (b - a) / a)
            }
        }
        return out.distinct()
    }
}

enum class AdjustmentType { INCREASE_VOLUME, REDUCE_VOLUME, NEW_TARGET_PACE }

data class PlanAdjustment(
    val type: AdjustmentType,
    /** Multiplier for run distances (INCREASE/REDUCE) or new pace in s/km (NEW_TARGET_PACE). */
    val value: Double,
    val weekStart: LocalDate,
    val completionLastWeek: Double,
    val completionPrevWeek: Double,
    val oldPace: Double? = null,
)

/**
 * Adaptive plan rules:
 *  - ≥ 90% of planned days done in each of the last two weeks → next week's run distances +10%
 *    (never more than 10%, the usual safe weekly increase).
 *  - < 50% done last week → next week's run distances −20% so the user can catch up.
 *  - Average easy-run pace improved by more than 3% → suggest the faster pace as the new target.
 */
object AdaptiveRules {

    fun completion(days: List<Pair<PlannedDay, PlanDayStatus>>): Double? {
        val work = days.filter { it.first.type != PlanDayType.REST }
        if (work.isEmpty()) return null
        val score = work.sumOf {
            when {
                PlanMatcher.isCompleted(it.second) -> 1.0
                it.second == PlanDayStatus.PARTIAL -> 0.5
                else -> 0.0
            }
        }
        return score / work.size
    }

    fun evaluate(
        statuses: List<Pair<PlannedDay, PlanDayStatus>>,
        today: LocalDate,
        recentEasyPaces: List<Double>,
        olderEasyPaces: List<Double>,
        currentTargetPace: Double?,
    ): List<PlanAdjustment> {
        val thisWeek = today.weekStart()
        val last = statuses.filter { it.first.date.weekStart() == thisWeek.minusWeeks(1) }
        val prev = statuses.filter { it.first.date.weekStart() == thisWeek.minusWeeks(2) }
        val cLast = completion(last)
        val cPrev = completion(prev)
        val out = mutableListOf<PlanAdjustment>()
        if (cLast != null && cPrev != null && cLast >= 0.9 && cPrev >= 0.9) {
            out += PlanAdjustment(AdjustmentType.INCREASE_VOLUME, 1.10, thisWeek, cLast, cPrev)
        } else if (cLast != null && cLast < 0.5) {
            out += PlanAdjustment(AdjustmentType.REDUCE_VOLUME, 0.80, thisWeek, cLast, cPrev ?: 0.0)
        }
        if (recentEasyPaces.size >= 2 && olderEasyPaces.size >= 2) {
            val recent = recentEasyPaces.average()
            val older = olderEasyPaces.average()
            val reference = currentTargetPace ?: older
            if (recent < older * 0.97 && recent < reference) {
                out += PlanAdjustment(AdjustmentType.NEW_TARGET_PACE, recent, thisWeek, cLast ?: 0.0, cPrev ?: 0.0, oldPace = reference)
            }
        }
        return out
    }

    /** Applies a distance multiplier, rounding to 100 m and capping growth at 10%. */
    fun scaleDistance(meters: Double, factor: Double): Double {
        val f = min(factor, 1.10)
        return (meters * f / 100.0).let { kotlin.math.round(it) * 100.0 }
    }
}

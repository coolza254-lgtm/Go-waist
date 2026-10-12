package com.gowaist.core.goals

import com.gowaist.core.model.GoalType
import com.gowaist.core.run.RunFacts
import com.gowaist.core.stats.RunStats
import com.gowaist.core.stats.Streaks
import java.time.LocalDate
import kotlin.math.abs

data class GoalSpec(
    val type: GoalType,
    /** Metres, counts, seconds, kg or cm depending on [type]. */
    val target: Double,
    val start: LocalDate,
    val end: LocalDate,
    val exerciseId: Long? = null,
    /** Starting value for body-weight / waist goals. */
    val baseline: Double? = null,
)

data class BodyPoint(val date: LocalDate, val weightKg: Double?, val waistCm: Double?)

data class GoalData(
    val runs: List<RunFacts>,
    val activeDays: Set<LocalDate>,
    val body: List<BodyPoint>,
)

data class GoalProgress(val current: Double?, val fraction: Double, val achieved: Boolean)

object GoalEvaluator {

    fun progress(goal: GoalSpec, data: GoalData): GoalProgress {
        fun inRange(d: LocalDate) = !d.isBefore(goal.start) && !d.isAfter(goal.end)
        val runs = data.runs.filter { inRange(it.localDate) }
        val t = goal.target
        return when (goal.type) {
            GoalType.RUN_DISTANCE -> atLeast(runs.sumOf { it.distanceM }, t)
            GoalType.RUN_COUNT -> atLeast(runs.size.toDouble(), t)
            GoalType.RUN_5K_TIME -> {
                val best = RunStats.bestTimeFor(runs, 5000.0)?.second?.toDouble()
                if (best == null) GoalProgress(null, 0.0, false)
                else GoalProgress(best, (t / best).coerceIn(0.0, 1.0), best <= t)
            }
            // Workout goals are no longer tracked (the app only follows running).
            GoalType.BW_SESSIONS, GoalType.EXERCISE_MAX_REPS, GoalType.EXERCISE_HOLD, GoalType.EXERCISE_TOTAL_REPS -> GoalProgress(null, 0.0, false)
            GoalType.STREAK_DAYS -> {
                val active = data.activeDays.filter { inRange(it) }.toSet()
                atLeast(Streaks.longest(active, emptySet()).toDouble(), t)
            }
            GoalType.BODY_WEIGHT -> towards(goal, data.body.filter { inRange(it.date) }.mapNotNull { p -> p.weightKg?.let { p.date to it } })
            GoalType.WAIST -> towards(goal, data.body.filter { inRange(it.date) }.mapNotNull { p -> p.waistCm?.let { p.date to it } })
        }
    }

    private fun atLeast(current: Double, target: Double) =
        GoalProgress(current, if (target <= 0) 1.0 else (current / target).coerceIn(0.0, 1.0), current >= target && target > 0)

    /** Progress from a baseline towards a target in either direction (lose or gain). */
    private fun towards(goal: GoalSpec, points: List<Pair<LocalDate, Double>>): GoalProgress {
        val latest = points.maxByOrNull { it.first }?.second ?: return GoalProgress(null, 0.0, false)
        val base = goal.baseline ?: points.minBy { it.first }.second
        val total = goal.target - base
        if (abs(total) < 1e-9) return GoalProgress(latest, 1.0, true)
        val done = (latest - base) / total
        return GoalProgress(latest, done.coerceIn(0.0, 1.0), done >= 1.0)
    }
}

package com.gowaist.core.goals

import com.gowaist.core.model.GoalType
import com.gowaist.core.run.RunFacts
import com.gowaist.core.stats.RunStats
import com.gowaist.core.stats.SetFacts
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
    val sets: List<SetFacts>,
    /** Session id → date of every finished bodyweight session. */
    val sessions: Map<Long, LocalDate>,
    val activeDays: Set<LocalDate>,
    val restDays: Set<LocalDate>,
    val body: List<BodyPoint>,
)

data class GoalProgress(val current: Double?, val fraction: Double, val achieved: Boolean)

object GoalEvaluator {

    fun progress(goal: GoalSpec, data: GoalData): GoalProgress {
        fun inRange(d: LocalDate) = !d.isBefore(goal.start) && !d.isAfter(goal.end)
        val runs = data.runs.filter { inRange(it.localDate) }
        val sets = data.sets.filter { inRange(it.date) && it.isWorking }
        val t = goal.target
        return when (goal.type) {
            GoalType.RUN_DISTANCE -> atLeast(runs.sumOf { it.distanceM }, t)
            GoalType.RUN_COUNT -> atLeast(runs.size.toDouble(), t)
            GoalType.RUN_5K_TIME -> {
                val best = RunStats.bestTimeFor(runs, 5000.0)?.second?.toDouble()
                if (best == null) GoalProgress(null, 0.0, false)
                else GoalProgress(best, (t / best).coerceIn(0.0, 1.0), best <= t)
            }
            GoalType.BW_SESSIONS -> atLeast(data.sessions.values.count { inRange(it) }.toDouble(), t)
            GoalType.EXERCISE_MAX_REPS ->
                atLeast(sets.filter { it.exerciseId == goal.exerciseId }.maxOfOrNull { it.reps ?: 0 }?.toDouble() ?: 0.0, t)
            GoalType.EXERCISE_HOLD ->
                atLeast(sets.filter { it.exerciseId == goal.exerciseId }.maxOfOrNull { it.durationSec ?: 0 }?.toDouble() ?: 0.0, t)
            GoalType.EXERCISE_TOTAL_REPS ->
                atLeast(sets.filter { it.exerciseId == goal.exerciseId }.sumOf { it.reps ?: 0 }.toDouble(), t)
            GoalType.STREAK_DAYS -> {
                val active = data.activeDays.filter { inRange(it) }.toSet()
                atLeast(Streaks.longest(active, data.restDays).toDouble(), t)
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

enum class BadgeKey {
    FIRST_RUN,
    FIRST_SESSION,
    FIRST_PB,
    STREAK_7,
    STREAK_30,
    RUN_5K,
    RUN_10K,
    RUN_HALF,
    TOTAL_100K,
    SESSIONS_10,
    SESSIONS_50,
    CHAIN_LEVEL_UP,
    FIRST_GOAL,
    EARLY_BIRD,
    PB_10,
}

data class BadgeFacts(
    val runCount: Int,
    val totalRunM: Double,
    val longestRunM: Double,
    val sessionCount: Int,
    val prCount: Int,
    val longestStreak: Int,
    val chainPromotions: Int,
    val goalsAchieved: Int,
    val hasEarlyRun: Boolean,
)

object BadgeRules {
    fun unlocked(f: BadgeFacts): Set<BadgeKey> = buildSet {
        if (f.runCount >= 1) add(BadgeKey.FIRST_RUN)
        if (f.sessionCount >= 1) add(BadgeKey.FIRST_SESSION)
        if (f.prCount >= 1) add(BadgeKey.FIRST_PB)
        if (f.prCount >= 10) add(BadgeKey.PB_10)
        if (f.longestStreak >= 7) add(BadgeKey.STREAK_7)
        if (f.longestStreak >= 30) add(BadgeKey.STREAK_30)
        if (f.longestRunM >= 4990) add(BadgeKey.RUN_5K)
        if (f.longestRunM >= 9990) add(BadgeKey.RUN_10K)
        if (f.longestRunM >= 21000) add(BadgeKey.RUN_HALF)
        if (f.totalRunM >= 100_000) add(BadgeKey.TOTAL_100K)
        if (f.sessionCount >= 10) add(BadgeKey.SESSIONS_10)
        if (f.sessionCount >= 50) add(BadgeKey.SESSIONS_50)
        if (f.chainPromotions >= 1) add(BadgeKey.CHAIN_LEVEL_UP)
        if (f.goalsAchieved >= 1) add(BadgeKey.FIRST_GOAL)
        if (f.hasEarlyRun) add(BadgeKey.EARLY_BIRD)
    }
}

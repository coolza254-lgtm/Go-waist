package com.gowaist.core.progression

import com.gowaist.core.model.TrackingType
import com.gowaist.core.stats.ExerciseFacts
import com.gowaist.core.stats.SetFacts
import com.gowaist.core.stats.WorkoutMath
import java.time.LocalDate

/** Target that decides when an exercise is "done" and the athlete should move on. */
data class ProgressionTarget(
    val sets: Int = 3,
    /** Reps per set, or seconds per set for hold exercises. */
    val repsOrSec: Int = 12,
    /** Sessions in a row that must hit the target. */
    val consecutiveSessions: Int = 2,
    /** Working sets above this average RPE do not count as "easy enough". */
    val rpeCeiling: Double = 8.0,
)

enum class SuggestionType { INCREASE_REPS, INCREASE_HOLD, ADD_WEIGHT, REDUCE_ASSIST, PROMOTE, PLATEAU, DELOAD }

/**
 * A rule-based recommendation. [key] identifies the situation so the app can remember that the
 * user accepted, rejected or snoozed it; a new session that changes the situation yields a new key.
 */
data class Suggestion(
    val key: String,
    val type: SuggestionType,
    val exerciseId: Long?,
    val nextExerciseId: Long? = null,
    /** Numbers backing the reason text (shown to the user). */
    val evidence: Evidence,
)

data class Evidence(
    val sessions: Int = 0,
    val targetSets: Int = 0,
    val targetRepsOrSec: Int = 0,
    val avgRpe: Double? = null,
    val bestRecent: Double = 0.0,
    val bestBefore: Double = 0.0,
    val suggestedValue: Double = 0.0,
    val decliningExercises: Int = 0,
)

data class ExerciseHistory(
    val exercise: ExerciseFacts,
    val sets: List<SetFacts>,
    val target: ProgressionTarget,
    /** Next exercise in the user's progression chain, if any. */
    val nextExerciseId: Long?,
)

/**
 * Rules (all local, no network):
 *  - Double progression: every working set reached the target with average RPE at or below the
 *    ceiling for N sessions in a row → add reps / seconds / load, or promote to the next chain step.
 *  - Plateau: the best set of the last 3 sessions is not better than anything before them.
 *  - Deload: average RPE ≥ 8.5 over the last two weeks, or several exercises declining for three
 *    sessions straight.
 */
class ProgressionAdvisor(private val bodyweightKg: Double) {

    fun suggest(histories: List<ExerciseHistory>, today: LocalDate): List<Suggestion> {
        val out = mutableListOf<Suggestion>()
        var declining = 0
        histories.forEach { h ->
            val sessions = sessionsOf(h)
            if (sessions.isEmpty()) return@forEach
            progressionFor(h, sessions)?.let { out += it } ?: plateauFor(h, sessions)?.let { out += it }
            if (isDeclining(h, sessions)) declining++
        }
        deload(histories, today, declining)?.let { out += it }
        return out
    }

    private data class Session(val id: Long, val date: LocalDate, val sets: List<SetFacts>)

    private fun sessionsOf(h: ExerciseHistory): List<Session> =
        h.sets.filter { it.isWorking && it.exerciseId == h.exercise.id }
            .groupBy { it.sessionId }
            .map { (id, s) -> Session(id, s.first().date, s) }
            .sortedWith(compareBy({ it.date }, { it.id }))

    private fun avgRpe(sets: List<SetFacts>): Double? = sets.mapNotNull { it.rpe }.takeIf { it.isNotEmpty() }?.average()

    private fun meetsTarget(h: ExerciseHistory, s: Session): Boolean {
        val t = h.target
        val good = s.sets.count { set ->
            when (h.exercise.trackingType) {
                TrackingType.HOLD -> (set.durationSec ?: 0) >= t.repsOrSec
                else -> (set.reps ?: 0) >= t.repsOrSec
            }
        }
        val rpe = avgRpe(s.sets)
        return good >= t.sets && (rpe == null || rpe <= t.rpeCeiling)
    }

    private fun progressionFor(h: ExerciseHistory, sessions: List<Session>): Suggestion? {
        val n = h.target.consecutiveSessions.coerceAtLeast(1)
        if (sessions.size < n) return null
        val recent = sessions.takeLast(n)
        if (!recent.all { meetsTarget(h, it) }) return null
        val last = recent.last()
        val evidence = Evidence(
            sessions = n,
            targetSets = h.target.sets,
            targetRepsOrSec = h.target.repsOrSec,
            avgRpe = avgRpe(recent.flatMap { it.sets }),
        )
        val ex = h.exercise
        val keyBase = "${ex.id}:${last.id}"
        if (h.nextExerciseId != null) {
            return Suggestion("promote:$keyBase", SuggestionType.PROMOTE, ex.id, h.nextExerciseId, evidence)
        }
        return when (ex.trackingType) {
            TrackingType.WEIGHTED -> {
                val current = last.sets.maxOf { it.addedKg ?: 0.0 }
                Suggestion("weight:$keyBase", SuggestionType.ADD_WEIGHT, ex.id, evidence = evidence.copy(suggestedValue = current + 2.5))
            }
            TrackingType.ASSISTED -> {
                val current = last.sets.maxOf { it.assistKg ?: 0.0 }
                Suggestion("assist:$keyBase", SuggestionType.REDUCE_ASSIST, ex.id, evidence = evidence.copy(suggestedValue = (current - 5.0).coerceAtLeast(0.0)))
            }
            TrackingType.HOLD -> Suggestion(
                "hold:$keyBase", SuggestionType.INCREASE_HOLD, ex.id,
                evidence = evidence.copy(suggestedValue = h.target.repsOrSec + 10.0),
            )
            else -> Suggestion(
                "reps:$keyBase", SuggestionType.INCREASE_REPS, ex.id,
                evidence = evidence.copy(suggestedValue = h.target.repsOrSec + 2.0),
            )
        }
    }

    private fun bestOf(h: ExerciseHistory, s: Session): Double =
        s.sets.maxOf { WorkoutMath.setScore(it, h.exercise, bodyweightKg) }

    private fun plateauFor(h: ExerciseHistory, sessions: List<Session>): Suggestion? {
        if (sessions.size < 5) return null
        val recent = sessions.takeLast(3)
        val before = sessions.dropLast(3)
        val bestRecent = recent.maxOf { bestOf(h, it) }
        val bestBefore = before.maxOf { bestOf(h, it) }
        if (bestRecent > bestBefore) return null
        return Suggestion(
            key = "plateau:${h.exercise.id}:${recent.last().id}",
            type = SuggestionType.PLATEAU,
            exerciseId = h.exercise.id,
            evidence = Evidence(sessions = 3, bestRecent = bestRecent, bestBefore = bestBefore, avgRpe = avgRpe(recent.flatMap { it.sets })),
        )
    }

    private fun isDeclining(h: ExerciseHistory, sessions: List<Session>): Boolean {
        if (sessions.size < 3) return false
        val scores = sessions.takeLast(3).map { bestOf(h, it) }
        return scores[0] > scores[1] && scores[1] > scores[2]
    }

    private fun deload(histories: List<ExerciseHistory>, today: LocalDate, declining: Int): Suggestion? {
        val from = today.minusDays(13)
        val recentSets = histories.flatMap { it.sets }.filter { it.isWorking && !it.date.isBefore(from) }
        val rated = recentSets.mapNotNull { it.rpe }
        val highRpe = rated.size >= 6 && rated.average() >= 8.5
        if (!highRpe && declining < 2) return null
        val week = today.minusDays(today.dayOfWeek.value - 1L)
        return Suggestion(
            key = "deload:$week",
            type = SuggestionType.DELOAD,
            exerciseId = null,
            evidence = Evidence(avgRpe = rated.takeIf { it.isNotEmpty() }?.average(), decliningExercises = declining, sessions = recentSets.map { it.sessionId }.distinct().size),
        )
    }
}

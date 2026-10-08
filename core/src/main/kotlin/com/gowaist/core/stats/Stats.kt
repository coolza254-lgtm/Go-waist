package com.gowaist.core.stats

import com.gowaist.core.model.Muscle
import com.gowaist.core.model.MovementPattern
import com.gowaist.core.model.PrType
import com.gowaist.core.model.SetType
import com.gowaist.core.model.TrackingType
import com.gowaist.core.run.RunFacts
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.max

fun LocalDate.weekStart(): LocalDate = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

data class ExerciseFacts(
    val id: Long,
    val pattern: MovementPattern,
    val primary: Set<Muscle>,
    val secondary: Set<Muscle>,
    val trackingType: TrackingType,
    /** Share of body weight moved by one rep (push-up ≈ 0.64, pull-up ≈ 1.0). */
    val bodyweightFactor: Double,
)

data class SetFacts(
    val exerciseId: Long,
    val sessionId: Long,
    val date: LocalDate,
    val setType: SetType,
    val reps: Int?,
    val durationSec: Int?,
    val addedKg: Double?,
    val assistKg: Double?,
    val rpe: Double?,
    val distanceM: Double? = null,
) {
    val isWorking: Boolean get() = setType != SetType.WARMUP
}

object WorkoutMath {

    fun effectiveLoadKg(set: SetFacts, factor: Double, bodyweightKg: Double): Double =
        max(0.0, bodyweightKg * factor + (set.addedKg ?: 0.0) - (set.assistKg ?: 0.0))

    /** Kilograms moved in a set (reps × effective load). Holds and cardio sets count as 0 kg. */
    fun setVolumeKg(set: SetFacts, factor: Double, bodyweightKg: Double): Double =
        (set.reps ?: 0) * effectiveLoadKg(set, factor, bodyweightKg)

    /** Epley estimated one-rep max. */
    fun epley1Rm(loadKg: Double, reps: Int): Double = when {
        reps <= 0 || loadKg <= 0 -> 0.0
        reps == 1 -> loadKg
        else -> loadKg * (1 + reps / 30.0)
    }

    data class Summary(val sets: Int, val reps: Int, val holdSec: Int, val volumeKg: Double)

    fun summarize(sets: List<SetFacts>, exercises: Map<Long, ExerciseFacts>, bodyweightKg: Double): Summary {
        val working = sets.filter { it.isWorking }
        return Summary(
            sets = working.size,
            reps = working.sumOf { it.reps ?: 0 },
            holdSec = working.sumOf { it.durationSec ?: 0 },
            volumeKg = working.sumOf { s -> setVolumeKg(s, exercises[s.exerciseId]?.bodyweightFactor ?: 1.0, bodyweightKg) },
        )
    }

    /** Best values for one exercise over a list of sets (warm-ups ignored). */
    data class Bests(
        val maxReps: Int = 0,
        val longestHoldSec: Int = 0,
        val maxSessionVolumeKg: Double = 0.0,
        val maxSessionReps: Int = 0,
        val bestSetScore: Double = 0.0,
        val maxAddedKg: Double = 0.0,
        val est1RmKg: Double = 0.0,
    )

    fun bests(sets: List<SetFacts>, exercise: ExerciseFacts, bodyweightKg: Double): Bests {
        val working = sets.filter { it.isWorking && it.exerciseId == exercise.id }
        if (working.isEmpty()) return Bests()
        val bySession = working.groupBy { it.sessionId }
        return Bests(
            maxReps = working.maxOf { it.reps ?: 0 },
            longestHoldSec = working.maxOf { it.durationSec ?: 0 },
            maxSessionVolumeKg = bySession.values.maxOf { s -> s.sumOf { setVolumeKg(it, exercise.bodyweightFactor, bodyweightKg) } },
            maxSessionReps = bySession.values.maxOf { s -> s.sumOf { it.reps ?: 0 } },
            bestSetScore = working.maxOf { setScore(it, exercise, bodyweightKg) },
            maxAddedKg = working.maxOf { it.addedKg ?: 0.0 },
            est1RmKg = if (exercise.trackingType == TrackingType.WEIGHTED) {
                working.maxOf { epley1Rm(effectiveLoadKg(it, exercise.bodyweightFactor, bodyweightKg), it.reps ?: 0) }
            } else 0.0,
        )
    }

    /** Single number used to compare sets: e1RM for weighted, seconds for holds, reps otherwise. */
    fun setScore(set: SetFacts, exercise: ExerciseFacts, bodyweightKg: Double): Double = when (exercise.trackingType) {
        TrackingType.WEIGHTED -> epley1Rm(effectiveLoadKg(set, exercise.bodyweightFactor, bodyweightKg), set.reps ?: 0)
        TrackingType.HOLD -> (set.durationSec ?: 0).toDouble()
        TrackingType.ASSISTED -> (set.reps ?: 0) * effectiveLoadKg(set, exercise.bodyweightFactor, bodyweightKg).coerceAtLeast(1.0)
        TrackingType.CARDIO -> (set.distanceM ?: 0.0) + (set.reps ?: 0)
        TrackingType.REPS -> (set.reps ?: 0).toDouble()
    }

    data class NewRecord(val exerciseId: Long, val type: PrType, val value: Double, val previous: Double)

    /**
     * Records broken by [sessionSets] compared to [history] (sets from earlier sessions).
     * The very first time an exercise is logged sets a baseline, not a record.
     */
    fun newRecords(
        sessionSets: List<SetFacts>,
        history: List<SetFacts>,
        exercise: ExerciseFacts,
        bodyweightKg: Double,
    ): List<NewRecord> {
        val before = history.filter { it.exerciseId == exercise.id && it.isWorking }
        val now = sessionSets.filter { it.exerciseId == exercise.id && it.isWorking }
        if (before.isEmpty() || now.isEmpty()) return emptyList()
        val old = bests(before, exercise, bodyweightKg)
        val cur = bests(now, exercise, bodyweightKg)
        val out = mutableListOf<NewRecord>()
        fun check(type: PrType, newV: Double, oldV: Double) {
            if (newV > 0 && newV > oldV + 1e-9) out += NewRecord(exercise.id, type, newV, oldV)
        }
        when (exercise.trackingType) {
            TrackingType.HOLD -> check(PrType.LONGEST_HOLD, cur.longestHoldSec.toDouble(), old.longestHoldSec.toDouble())
            TrackingType.WEIGHTED -> {
                check(PrType.EST_1RM, cur.est1RmKg, old.est1RmKg)
                check(PrType.MAX_ADDED_WEIGHT, cur.maxAddedKg, old.maxAddedKg)
                check(PrType.MAX_REPS, cur.maxReps.toDouble(), old.maxReps.toDouble())
                check(PrType.MAX_VOLUME_SESSION, cur.maxSessionVolumeKg, old.maxSessionVolumeKg)
            }
            TrackingType.CARDIO -> check(PrType.BEST_SET, cur.bestSetScore, old.bestSetScore)
            TrackingType.REPS, TrackingType.ASSISTED -> {
                check(PrType.MAX_REPS, cur.maxReps.toDouble(), old.maxReps.toDouble())
                check(PrType.MAX_VOLUME_SESSION, cur.maxSessionReps.toDouble(), old.maxSessionReps.toDouble())
            }
        }
        return out
    }
}

object RunStats {

    data class Totals(val count: Int, val distanceM: Double, val durationSec: Long) {
        val avgPaceSecPerKm: Double? get() = if (distanceM > 0 && durationSec > 0) durationSec / (distanceM / 1000.0) else null
    }

    fun totals(runs: List<RunFacts>): Totals =
        Totals(runs.size, runs.sumOf { it.distanceM }, runs.sumOf { it.durationSec })

    fun inRange(runs: List<RunFacts>, from: LocalDate, toInclusive: LocalDate) =
        runs.filter { !it.localDate.isBefore(from) && !it.localDate.isAfter(toInclusive) }

    /** Distance per week for the last [weeks] weeks ending with the week of [today] (oldest first). */
    fun weeklyDistance(runs: List<RunFacts>, today: LocalDate, weeks: Int): List<Pair<LocalDate, Double>> {
        val thisWeek = today.weekStart()
        val byWeek = runs.groupBy { it.localDate.weekStart() }
        return (weeks - 1 downTo 0).map { i ->
            val ws = thisWeek.minusWeeks(i.toLong())
            ws to (byWeek[ws]?.sumOf { it.distanceM } ?: 0.0)
        }
    }

    /**
     * Best estimated time for [targetM] from runs at least that long, using each run's average
     * pace. A run of N km at average pace P contains some segment at least as fast as P, so the
     * estimate is a conservative (never too optimistic) personal best.
     */
    fun bestTimeFor(runs: List<RunFacts>, targetM: Double): Pair<RunFacts, Long>? =
        runs.filter { it.distanceM >= targetM * 0.995 && it.durationSec > 0 }
            .map { it to (it.durationSec * targetM / it.distanceM).toLong() }
            .minByOrNull { it.second }

    fun longest(runs: List<RunFacts>): RunFacts? = runs.maxByOrNull { it.distanceM }

    fun fastestPace(runs: List<RunFacts>, minDistanceM: Double = 1000.0): Pair<RunFacts, Double>? =
        runs.filter { it.distanceM >= minDistanceM && it.durationSec > 0 }
            .map { it to it.durationSec / (it.distanceM / 1000.0) }
            .minByOrNull { it.second }

    data class RunRecord(val type: PrType, val value: Double, val previous: Double?)

    /** Records a new run sets against [history]; nothing on the very first run. */
    fun newRecords(run: RunFacts, history: List<RunFacts>): List<RunRecord> {
        val prior = history.filter { it.id != run.id }
        if (prior.isEmpty()) return emptyList()
        val out = mutableListOf<RunRecord>()
        val longest = prior.maxOf { it.distanceM }
        if (run.distanceM > longest) out += RunRecord(PrType.RUN_LONGEST, run.distanceM, longest)
        val targets = listOf(PrType.RUN_1K to 1000.0, PrType.RUN_5K to 5000.0, PrType.RUN_10K to 10000.0, PrType.RUN_HALF to 21097.5)
        for ((type, d) in targets) {
            val mine = bestTimeFor(listOf(run), d)?.second ?: continue
            val old = bestTimeFor(prior, d)?.second
            if (old == null || mine < old) out += RunRecord(type, mine.toDouble(), old?.toDouble())
        }
        return out
    }
}

object Streaks {
    /**
     * Current streak of consecutive active days ending today (or yesterday, so the streak is not
     * lost before today's workout). Planned rest days keep the streak alive but do not add to it.
     */
    fun current(activeDays: Set<LocalDate>, restDays: Set<LocalDate>, today: LocalDate): Int {
        var day = if (today in activeDays || today in restDays) today else today.minusDays(1)
        var count = 0
        var guard = 0
        while ((day in activeDays || day in restDays) && guard < 3660) {
            if (day in activeDays) count++
            day = day.minusDays(1)
            guard++
        }
        return count
    }

    fun longest(activeDays: Set<LocalDate>, restDays: Set<LocalDate>): Int {
        if (activeDays.isEmpty()) return 0
        val all = (activeDays + restDays).sorted()
        var best = 0
        var run = 0
        var prev: LocalDate? = null
        for (d in all) {
            if (prev != null && d != prev.plusDays(1)) run = 0
            if (d in activeDays) run++
            best = max(best, run)
            prev = d
        }
        return best
    }
}

object Balance {
    data class MuscleLoad(val muscle: Muscle, val sets: Double)

    /** Working sets per muscle: primary muscles count 1, secondary 0.5. */
    fun muscleSets(sets: List<SetFacts>, exercises: Map<Long, ExerciseFacts>): Map<Muscle, Double> {
        val out = Muscle.entries.associateWith { 0.0 }.toMutableMap()
        sets.filter { it.isWorking }.forEach { s ->
            val ex = exercises[s.exerciseId] ?: return@forEach
            ex.primary.forEach { out[it] = out.getValue(it) + 1.0 }
            ex.secondary.forEach { out[it] = out.getValue(it) + 0.5 }
        }
        return out
    }

    data class Ratios(val pushSets: Int, val pullSets: Int, val upperSets: Double, val lowerSets: Double) {
        val pushPull: Double? get() = if (pullSets == 0) null else pushSets.toDouble() / pullSets
        val upperLower: Double? get() = if (lowerSets == 0.0) null else upperSets / lowerSets
        val pushPullImbalanced: Boolean get() = (pushSets >= 6 && pullSets == 0) || (pushPull?.let { it > 1.5 || it < 0.67 } ?: false)
        val upperLowerImbalanced: Boolean get() = (upperSets >= 6 && lowerSets == 0.0) || (upperLower?.let { it > 2.0 || it < 0.5 } ?: false)
    }

    fun ratios(sets: List<SetFacts>, exercises: Map<Long, ExerciseFacts>): Ratios {
        var push = 0
        var pull = 0
        var upper = 0.0
        var lower = 0.0
        sets.filter { it.isWorking }.forEach { s ->
            val ex = exercises[s.exerciseId] ?: return@forEach
            when (ex.pattern) {
                MovementPattern.PUSH -> push++
                MovementPattern.PULL -> pull++
                else -> Unit
            }
            val ups = ex.primary.count { it.isUpper && it !in CORE }
            val lows = ex.primary.count { !it.isUpper }
            val total = (ups + lows).coerceAtLeast(1)
            upper += ups.toDouble() / total
            lower += lows.toDouble() / total
        }
        return Ratios(push, pull, upper, lower)
    }

    private val CORE = setOf(Muscle.ABS, Muscle.OBLIQUES, Muscle.LOWER_BACK)
}

object Smoothing {
    /** Trailing moving average over the previous [days] calendar days (inclusive). */
    fun movingAverage(points: List<Pair<LocalDate, Double>>, days: Int = 7): List<Pair<LocalDate, Double>> {
        val sorted = points.sortedBy { it.first }
        return sorted.map { (date, _) ->
            val from = date.minusDays((days - 1).toLong())
            val window = sorted.filter { !it.first.isBefore(from) && !it.first.isAfter(date) }
            date to window.map { it.second }.average()
        }
    }
}

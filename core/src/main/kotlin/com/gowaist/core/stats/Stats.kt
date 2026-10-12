package com.gowaist.core.stats

import com.gowaist.core.model.PrType
import com.gowaist.core.run.RunFacts
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.max

fun LocalDate.weekStart(): LocalDate = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

object RunStats {

    /** [timedDistanceM] only counts runs with a recorded time (long runs may track distance only). */
    data class Totals(val count: Int, val distanceM: Double, val durationSec: Long, val timedDistanceM: Double = distanceM) {
        val avgPaceSecPerKm: Double? get() = if (timedDistanceM > 0 && durationSec > 0) durationSec / (timedDistanceM / 1000.0) else null
    }

    fun totals(runs: List<RunFacts>): Totals =
        Totals(runs.size, runs.sumOf { it.distanceM }, runs.sumOf { it.durationSec }, runs.filter { it.durationSec > 0 }.sumOf { it.distanceM })

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

package com.gowaist.core.perf

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Serializable
enum class Sex { MALE, FEMALE }

/** Kind of run, chosen by the program that recorded it (or by the user for imported runs). */
@Serializable
enum class RunType { FREE, COOPER, INTERVAL, LONG }

data class Profile(
    val age: Int? = null,
    val sex: Sex? = null,
    val heightCm: Double? = null,
    val restHr: Int? = null,
    val maxHr: Int? = null,
) {
    /** Tanaka formula when no measured maximum is known. */
    val effectiveMaxHr: Int get() = maxHr ?: age?.let { (208 - 0.7 * it).roundToInt() } ?: 190
    val effectiveRestHr: Int get() = restHr ?: 60
    val isComplete: Boolean get() = age != null && sex != null
}

/** One run as needed by the performance maths. */
data class RunSample(
    val id: Long,
    val date: LocalDate,
    val distanceM: Double,
    val durationSec: Long,
    val avgHr: Int?,
    val type: RunType,
    val rpe: Double?,
)

enum class Vo2Method { COOPER, HEART_RATE, RACE_PACE }

data class Vo2Estimate(val runId: Long, val date: LocalDate, val value: Double, val method: Vo2Method, val weight: Double)

data class DailyVo2(val date: LocalDate, val trend: Double, val measured: Double?)

enum class FitnessLevel { VERY_POOR, POOR, FAIR, GOOD, EXCELLENT, SUPERIOR }

/**
 * VO2 max estimation without a lab:
 *  - Cooper 12-minute test: VO2max = (metres − 504.9) / 44.73 (most reliable, full weight).
 *  - Heart rate: running VO2 from speed (ACSM: 0.2·v + 3.5 ml/kg/min, v in m/min) scaled by the
 *    share of heart-rate reserve used (Swain: %HRR ≈ %VO2R).
 *  - Race pace (Daniels/Gilbert VDOT) for hard efforts, scaled by the reported effort (RPE).
 * A run can produce several estimates; the daily value is a recency-weighted average so one odd
 * run does not swing the number, and every calendar day gets a value ("day by day" progress).
 */
object Vo2Max {

    fun cooper(distanceM: Double): Double = (distanceM - 504.9) / 44.73

    /** Daniels/Gilbert oxygen cost of running at [v] metres per minute. */
    fun vo2AtVelocity(v: Double): Double = -4.60 + 0.182258 * v + 0.000104 * v * v

    /** Fraction of VO2max sustainable for a race lasting [minutes]. */
    fun fractionForDuration(minutes: Double): Double =
        0.8 + 0.1894393 * exp(-0.012778 * minutes) + 0.2989558 * exp(-0.1932605 * minutes)

    fun vdot(distanceM: Double, durationSec: Long): Double {
        val t = durationSec / 60.0
        return vo2AtVelocity(distanceM / t) / fractionForDuration(t)
    }

    /** Inverse of [vo2AtVelocity]: speed in metres per minute that costs [vo2]. */
    fun velocityForVo2(vo2: Double): Double {
        val a = 0.000104
        val b = 0.182258
        val c = -(vo2 + 4.60)
        return (-b + sqrt(b * b - 4 * a * c)) / (2 * a)
    }

    fun fromHeartRate(distanceM: Double, durationSec: Long, avgHr: Int, profile: Profile): Double? {
        val hrr = (avgHr - profile.effectiveRestHr).toDouble() / (profile.effectiveMaxHr - profile.effectiveRestHr)
        if (hrr !in 0.45..0.97) return null
        val v = distanceM / (durationSec / 60.0)
        val vo2Run = 0.2 * v + 3.5
        return 3.5 + (vo2Run - 3.5) / hrr
    }

    /** Effort scale from RPE (10 = race) used to turn an easier run's VDOT into a VO2max guess. */
    private fun effortFactor(rpe: Double): Double = when {
        rpe >= 9.5 -> 1.0
        rpe >= 8.5 -> 0.95
        rpe >= 7.5 -> 0.9
        rpe >= 6.5 -> 0.85
        rpe >= 5.5 -> 0.8
        else -> 0.75
    }

    fun estimates(run: RunSample, profile: Profile): List<Vo2Estimate> {
        if (run.distanceM < 400 || run.durationSec < 180) return emptyList()
        val out = mutableListOf<Vo2Estimate>()
        fun add(v: Double?, m: Vo2Method, w: Double) {
            if (v != null && v in 15.0..90.0) out += Vo2Estimate(run.id, run.date, v, m, w)
        }
        val minutes = run.durationSec / 60.0
        if (run.type == RunType.COOPER && minutes in 11.0..13.0) {
            add(cooper(run.distanceM * 720.0 / run.durationSec), Vo2Method.COOPER, 1.0)
        }
        // Interval averages mix work and rest, so their pace and heart rate say little about VO2max.
        if (run.type == RunType.INTERVAL) return out
        if (run.avgHr != null && minutes >= 10) {
            add(fromHeartRate(run.distanceM, run.durationSec, run.avgHr, profile), Vo2Method.HEART_RATE, if (profile.isComplete) 0.6 else 0.4)
        }
        if (run.type != RunType.COOPER && minutes >= 6) {
            val rpe = run.rpe
            when {
                rpe != null -> add(vdot(run.distanceM, run.durationSec) / effortFactor(rpe), Vo2Method.RACE_PACE, if (rpe >= 8.5) 0.7 else 0.35)
                run.avgHr == null -> add(vdot(run.distanceM, run.durationSec) / 0.82, Vo2Method.RACE_PACE, 0.15)
            }
        }
        return out
    }

    /**
     * One value per day from the first estimate until [today]: an exponentially weighted mean of
     * all earlier estimates (half-life [halfLifeDays]), so the line moves smoothly and keeps
     * showing the current fitness on days without runs.
     */
    fun daily(estimates: List<Vo2Estimate>, today: LocalDate, halfLifeDays: Double = 21.0): List<DailyVo2> {
        if (estimates.isEmpty()) return emptyList()
        val sorted = estimates.sortedBy { it.date }
        val byDate = sorted.groupBy { it.date }
        val first = sorted.first().date
        val days = ChronoUnit.DAYS.between(first, today).coerceAtLeast(0)
        return (0..days).map { i ->
            val d = first.plusDays(i)
            var num = 0.0
            var den = 0.0
            for (e in sorted) {
                if (e.date.isAfter(d)) break
                val age = ChronoUnit.DAYS.between(e.date, d).toDouble()
                if (age > 180) continue
                val w = e.weight * 0.5.pow(age / halfLifeDays)
                num += w * e.value
                den += w
            }
            val measured = byDate[d]?.let { list -> list.sumOf { it.value * it.weight } / list.sumOf { it.weight } }
            DailyVo2(d, if (den > 0) num / den else measured ?: sorted.first().value, measured)
        }
    }

    private val male = listOf(
        29 to doubleArrayOf(33.0, 36.5, 42.4, 46.4, 52.4),
        39 to doubleArrayOf(31.5, 35.4, 40.9, 44.9, 49.4),
        49 to doubleArrayOf(30.2, 33.5, 38.9, 43.7, 48.0),
        59 to doubleArrayOf(26.1, 30.9, 35.7, 40.9, 45.3),
        200 to doubleArrayOf(20.5, 26.0, 32.2, 36.4, 44.2),
    )
    private val female = listOf(
        29 to doubleArrayOf(23.6, 28.9, 32.9, 36.9, 41.0),
        39 to doubleArrayOf(22.8, 26.9, 31.4, 35.6, 40.0),
        49 to doubleArrayOf(21.0, 24.4, 28.9, 32.8, 36.9),
        59 to doubleArrayOf(20.2, 22.7, 26.9, 31.4, 35.7),
        200 to doubleArrayOf(17.5, 20.1, 24.4, 30.2, 31.4),
    )

    /** Upper bounds of VERY_POOR, POOR, FAIR, GOOD, EXCELLENT for the profile (Heyward norms). */
    fun thresholds(profile: Profile): DoubleArray {
        val table = if (profile.sex == Sex.FEMALE) female else male
        val age = profile.age ?: 30
        return table.first { age <= it.first }.second
    }

    fun level(vo2: Double, profile: Profile): FitnessLevel {
        val t = thresholds(profile)
        val idx = t.indexOfFirst { vo2 < it }.let { if (it < 0) t.size else it }
        return FitnessLevel.entries[idx]
    }
}

/** Race predictions and training paces derived from VDOT. */
object RunPaces {
    /** Time in seconds to race [distanceM] at the given VDOT. */
    fun predictSeconds(vdot: Double, distanceM: Double): Long {
        var lo = distanceM / 1000 * 100.0
        var hi = distanceM / 1000 * 1500.0
        repeat(60) {
            val mid = (lo + hi) / 2
            if (Vo2Max.vdot(distanceM, mid.toLong()) > vdot) lo = mid else hi = mid
        }
        return ((lo + hi) / 2).toLong()
    }

    enum class Zone(val fraction: Double) { EASY(0.70), MARATHON(0.80), THRESHOLD(0.88), INTERVAL(0.98), REPETITION(1.05) }

    /** Pace in seconds per km for a training zone. */
    fun paceSecPerKm(vdot: Double, zone: Zone): Double = 1000.0 / Vo2Max.velocityForVo2(vdot * zone.fraction) * 60.0

    /** Karvonen heart-rate zones (lower bound of each of 5 zones, plus max). */
    fun hrZones(profile: Profile): List<Int> {
        val rest = profile.effectiveRestHr
        val reserve = profile.effectiveMaxHr - rest
        return listOf(0.5, 0.6, 0.7, 0.8, 0.9, 1.0).map { (rest + reserve * it).roundToInt() }
    }
}

data class LoadPoint(val date: LocalDate, val load: Double, val fitness: Double, val fatigue: Double) {
    /** "Form" (training stress balance): positive = fresh, negative = tired. */
    val form: Double get() = fitness - fatigue
    val acuteChronic: Double? get() = if (fitness > 1) fatigue / fitness else null
}

enum class FormState { FRESH, OPTIMAL, TIRED, OVERREACHING }

/** Training load from heart rate (Banister TRIMP), session RPE, or duration as a fallback. */
object TrainingLoad {

    fun trimp(durationSec: Long, avgHr: Int, profile: Profile): Double {
        val hrr = ((avgHr - profile.effectiveRestHr).toDouble() / (profile.effectiveMaxHr - profile.effectiveRestHr)).coerceIn(0.0, 1.0)
        val k = if (profile.sex == Sex.FEMALE) 0.86 * exp(1.67 * hrr) else 0.64 * exp(1.92 * hrr)
        return durationSec / 60.0 * hrr * k
    }

    /** Load of one activity on a TRIMP-like scale (~70 for a moderate 45-minute run). */
    fun load(durationSec: Long, avgHr: Int?, rpe: Double?, profile: Profile): Double {
        if (durationSec <= 0) return 0.0
        val minutes = durationSec / 60.0
        return when {
            avgHr != null -> trimp(durationSec, avgHr, profile)
            rpe != null -> minutes * rpe / 3.5
            else -> minutes * 1.6
        }
    }

    /** Daily fitness (42-day) and fatigue (7-day) exponentially weighted loads. */
    fun series(loads: Map<LocalDate, Double>, from: LocalDate, to: LocalDate): List<LoadPoint> {
        val start = loads.keys.minOrNull()?.let { if (it.isBefore(from)) it else from } ?: from
        var ctl = 0.0
        var atl = 0.0
        val out = mutableListOf<LoadPoint>()
        var d = start
        while (!d.isAfter(to)) {
            val l = loads[d] ?: 0.0
            ctl += (l - ctl) / 42.0
            atl += (l - atl) / 7.0
            if (!d.isBefore(from)) out += LoadPoint(d, l, ctl, atl)
            d = d.plusDays(1)
        }
        return out
    }

    fun state(p: LoadPoint): FormState {
        val ratio = p.acuteChronic
        return when {
            ratio != null && ratio > 1.5 -> FormState.OVERREACHING
            p.form < -10 -> FormState.TIRED
            p.form > 5 -> FormState.FRESH
            else -> FormState.OPTIMAL
        }
    }
}

data class WeightStats(
    val latest: Double,
    val average7: Double,
    val change7: Double?,
    val change30: Double?,
    /** kg per week from a least-squares fit of the last 28 days. */
    val weeklyRate: Double?,
    val bmi: Double?,
    /** Date the goal would be reached at the current rate, if moving towards it. */
    val goalDate: LocalDate?,
)

object WeightTrend {
    fun stats(points: List<Pair<LocalDate, Double>>, heightCm: Double?, goalKg: Double?, today: LocalDate): WeightStats? {
        if (points.isEmpty()) return null
        val sorted = points.sortedBy { it.first }
        val latest = sorted.last()
        fun avgBetween(from: LocalDate, to: LocalDate) = sorted.filter { !it.first.isBefore(from) && !it.first.isAfter(to) }.map { it.second }.takeIf { it.isNotEmpty() }?.average()
        val avg7 = avgBetween(latest.first.minusDays(6), latest.first) ?: latest.second
        val prev7 = avgBetween(latest.first.minusDays(13), latest.first.minusDays(7))
        val prev30 = avgBetween(latest.first.minusDays(37), latest.first.minusDays(30))
        val recent = sorted.filter { !it.first.isBefore(latest.first.minusDays(27)) }
        val rate = if (recent.size >= 3 && ChronoUnit.DAYS.between(recent.first().first, recent.last().first) >= 6) {
            val xs = recent.map { ChronoUnit.DAYS.between(recent.first().first, it.first).toDouble() }
            val ys = recent.map { it.second }
            val mx = xs.average()
            val my = ys.average()
            val num = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) }
            val den = xs.sumOf { (it - mx) * (it - mx) }
            if (den > 0) num / den * 7 else null
        } else null
        val bmi = heightCm?.takeIf { it > 50 }?.let { latest.second / ((it / 100) * (it / 100)) }
        val goalDate = if (goalKg != null && rate != null && abs(rate) > 0.01) {
            val weeks = (goalKg - avg7) / rate
            if (weeks > 0 && weeks < 520) today.plusDays((weeks * 7).roundToInt().toLong()) else null
        } else null
        return WeightStats(latest.second, avg7, prev7?.let { avg7 - it }, prev30?.let { avg7 - it }, rate, bmi, goalDate)
    }

    enum class BmiClass { UNDER, NORMAL, OVER, OBESE }

    /** Asian cut-offs (WHO expert consultation): 18.5 / 23 / 25. */
    fun bmiClass(bmi: Double): BmiClass = when {
        bmi < 18.5 -> BmiClass.UNDER
        bmi < 23.0 -> BmiClass.NORMAL
        bmi < 25.0 -> BmiClass.OVER
        else -> BmiClass.OBESE
    }
}

/** Efficiency index: metres covered per heartbeat (higher = fitter at the same effort). */
fun metresPerBeat(distanceM: Double, durationSec: Long, avgHr: Int): Double? =
    if (durationSec <= 0 || avgHr <= 0) null else distanceM / (durationSec / 60.0) / avgHr

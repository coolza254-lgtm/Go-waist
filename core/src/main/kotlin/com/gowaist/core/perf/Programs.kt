package com.gowaist.core.perf

import com.gowaist.core.timer.Phase
import com.gowaist.core.timer.PhaseKind
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** Interval running session: warm-up, [reps] × (work + rest), cool-down. Times in seconds/minutes. */
@Serializable
data class IntervalConfig(
    val warmupMin: Int = 10,
    val workSec: Int = 60,
    val restSec: Int = 90,
    val reps: Int = 6,
    val cooldownMin: Int = 5,
    /** Optional pace target for the work bouts (s/km); filled from VO2 max when known. */
    val targetPaceSecPerKm: Double? = null,
)

@Serializable
data class LongRunConfig(val targetKm: Double = 8.0)

@Serializable
data class FreeRunConfig(val targetMin: Int = 30)

@Serializable
data class CooperConfig(val lastTest: String? = null)

/** User-adjustable settings for all running programs. */
@Serializable
data class RunPrograms(
    val interval: IntervalConfig = IntervalConfig(),
    val longRun: LongRunConfig = LongRunConfig(),
    val freeRun: FreeRunConfig = FreeRunConfig(),
    val cooper: CooperConfig = CooperConfig(),
    /** Apply Gogo's adjustments automatically after each session instead of asking. */
    val autoAdjust: Boolean = false,
)

@Serializable
data class IntervalResult(val config: IntervalConfig, val completedReps: Int)

enum class AdjustReason { EASY, ON_TARGET, TOO_HARD, INCOMPLETE, TARGET_REACHED, SHORT_OF_TARGET, NONE }

data class ProgramAdjustment<T>(val next: T, val reason: AdjustReason)

object RunProgramRules {

    fun intervalPhases(c: IntervalConfig, prepSec: Int = 10): List<Phase> {
        val reps = c.reps.coerceAtLeast(1)
        val out = mutableListOf<Phase>()
        if (prepSec > 0) out += Phase(PhaseKind.PREP, prepSec, 0, reps)
        if (c.warmupMin > 0) out += Phase(PhaseKind.WARMUP, c.warmupMin * 60, 0, reps)
        for (r in 1..reps) {
            out += Phase(PhaseKind.WORK, c.workSec.coerceAtLeast(5), r, reps)
            if (r < reps && c.restSec > 0) out += Phase(PhaseKind.REST, c.restSec, r, reps)
        }
        if (c.cooldownMin > 0) out += Phase(PhaseKind.COOLDOWN, c.cooldownMin * 60, reps, reps)
        return out
    }

    fun cooperPhases(prepSec: Int = 10): List<Phase> = listOf(Phase(PhaseKind.PREP, prepSec, 0, 1), Phase(PhaseKind.WORK, 12 * 60, 1, 1))

    /** Difficulty level 1–10 from total hard work and work:rest density. */
    fun intervalLevel(c: IntervalConfig): Int {
        val workMin = c.reps * c.workSec / 60.0
        val density = c.workSec.toDouble() / (c.workSec + c.restSec).coerceAtLeast(1)
        return (workMin / 2.0 + density * 6).roundToInt().coerceIn(1, 10)
    }

    fun longRunLevel(c: LongRunConfig): Int = (c.targetKm / 3).roundToInt().coerceIn(1, 10)

    /**
     * Interval progression after a session:
     *  - all reps done with RPE ≤ 6 → one more rep (max 12), then longer work bouts;
     *  - all reps at RPE 7–8 → stay;
     *  - RPE ≥ 9 or reps missed → one rep fewer / longer rest.
     */
    fun adaptInterval(result: IntervalResult, rpe: Double?): ProgramAdjustment<IntervalConfig> {
        val c = result.config
        val done = result.completedReps >= c.reps
        return when {
            !done -> ProgramAdjustment(
                if (c.reps > 3) c.copy(reps = c.reps - 1) else c.copy(restSec = c.restSec + 15),
                AdjustReason.INCOMPLETE,
            )
            rpe != null && rpe >= 9 -> ProgramAdjustment(c.copy(restSec = c.restSec + 15), AdjustReason.TOO_HARD)
            rpe != null && rpe <= 6 -> ProgramAdjustment(
                if (c.reps < 12) c.copy(reps = c.reps + 1) else c.copy(workSec = c.workSec + 15),
                AdjustReason.EASY,
            )
            else -> ProgramAdjustment(c, AdjustReason.ON_TARGET)
        }
    }

    /** Long run target grows by ≤10% (max +2 km) when reached, eases back when far short. */
    fun adaptLongRun(c: LongRunConfig, distanceKm: Double): ProgramAdjustment<LongRunConfig> = when {
        distanceKm >= c.targetKm * 0.98 -> {
            val next = minOf(c.targetKm * 1.10, c.targetKm + 2.0)
            ProgramAdjustment(LongRunConfig(roundHalf(next).coerceAtLeast(c.targetKm + 0.5)), AdjustReason.TARGET_REACHED)
        }
        distanceKm < c.targetKm * 0.75 -> ProgramAdjustment(LongRunConfig(roundHalf(maxOf(distanceKm + 1.0, c.targetKm * 0.9)).coerceAtLeast(3.0)), AdjustReason.SHORT_OF_TARGET)
        else -> ProgramAdjustment(c, AdjustReason.ON_TARGET)
    }

    private fun roundHalf(km: Double) = (km * 2).roundToInt() / 2.0

    /** A Cooper re-test is due every 4 weeks. */
    fun cooperDue(c: CooperConfig, today: LocalDate): Boolean =
        c.lastTest?.let { runCatching { ChronoUnit.DAYS.between(LocalDate.parse(it), today) >= 28 }.getOrDefault(true) } ?: true

    fun daysUntilCooper(c: CooperConfig, today: LocalDate): Int =
        c.lastTest?.let { runCatching { (28 - ChronoUnit.DAYS.between(LocalDate.parse(it), today)).toInt() }.getOrNull() }?.coerceAtLeast(0) ?: 0
}

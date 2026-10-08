package com.gowaist.core.timer

import kotlinx.serialization.Serializable

@Serializable
enum class TimerMode { EMOM, AMRAP, TABATA, CUSTOM }

@Serializable
data class TimerConfig(
    val mode: TimerMode,
    /** EMOM: minutes; others: rounds. */
    val rounds: Int,
    val workSec: Int,
    val restSec: Int,
    val prepSec: Int = 10,
) {
    companion object {
        fun emom(minutes: Int) = TimerConfig(TimerMode.EMOM, minutes, 60, 0)
        fun amrap(minutes: Int) = TimerConfig(TimerMode.AMRAP, 1, minutes * 60, 0)
        fun tabata(rounds: Int = 8) = TimerConfig(TimerMode.TABATA, rounds, 20, 10)
        fun custom(work: Int, rest: Int, rounds: Int) = TimerConfig(TimerMode.CUSTOM, rounds, work, rest)
    }
}

enum class PhaseKind { PREP, WORK, REST }

data class Phase(val kind: PhaseKind, val durationSec: Int, val round: Int, val totalRounds: Int)

data class TimerPosition(val phaseIndex: Int, val phase: Phase, val remainingMs: Long, val finished: Boolean)

object IntervalProgram {

    fun phases(c: TimerConfig): List<Phase> {
        val rounds = c.rounds.coerceAtLeast(1)
        val out = mutableListOf<Phase>()
        if (c.prepSec > 0) out += Phase(PhaseKind.PREP, c.prepSec, 0, rounds)
        for (r in 1..rounds) {
            out += Phase(PhaseKind.WORK, c.workSec.coerceAtLeast(1), r, rounds)
            // No trailing rest after the last round.
            if (c.restSec > 0 && r < rounds) out += Phase(PhaseKind.REST, c.restSec, r, rounds)
        }
        return out
    }

    fun totalSec(c: TimerConfig): Int = phases(c).sumOf { it.durationSec }

    /** Where the program is after [elapsedMs]; stable for any elapsed value (clamped). */
    fun locate(phases: List<Phase>, elapsedMs: Long): TimerPosition {
        var acc = 0L
        phases.forEachIndexed { i, p ->
            val end = acc + p.durationSec * 1000L
            if (elapsedMs < end) return TimerPosition(i, p, end - elapsedMs, false)
            acc = end
        }
        val last = phases.last()
        return TimerPosition(phases.lastIndex, last, 0, true)
    }
}

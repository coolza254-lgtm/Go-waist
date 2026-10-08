package com.gowaist.core.run

import com.gowaist.core.Pace
import kotlin.math.abs

enum class RunWarning {
    PACE_MISMATCH,
    PACE_IMPLAUSIBLE,
    DISTANCE_IMPLAUSIBLE,
    DURATION_IMPLAUSIBLE,
    HR_IMPLAUSIBLE,
    DATE_IN_FUTURE,
}

/** Minimal view of a stored run used for duplicate checks and stats. */
data class RunFacts(
    val id: Long,
    val startAtMillis: Long,
    val localDate: java.time.LocalDate,
    val distanceM: Double,
    val durationSec: Long,
)

object RunChecks {

    /** Sanity checks shown on the review screen; none of them block saving. */
    fun validate(
        distanceM: Double?,
        durationSec: Long?,
        readPaceSecPerKm: Double?,
        avgHr: Int?,
        startAtMillis: Long?,
        nowMillis: Long,
    ): Set<RunWarning> {
        val w = mutableSetOf<RunWarning>()
        if (distanceM != null && (distanceM < 100 || distanceM > 250_000)) w += RunWarning.DISTANCE_IMPLAUSIBLE
        if (durationSec != null && (durationSec < 60 || durationSec > 24 * 3600)) w += RunWarning.DURATION_IMPLAUSIBLE
        val computed = if (distanceM != null && durationSec != null) Pace.secPerKm(distanceM, durationSec) else null
        if (computed != null && (computed < 150 || computed > 1500)) w += RunWarning.PACE_IMPLAUSIBLE
        if (computed != null && readPaceSecPerKm != null && !Pace.matches(computed, readPaceSecPerKm, 0.05)) {
            w += RunWarning.PACE_MISMATCH
        }
        if (avgHr != null && (avgHr < 40 || avgHr > 230)) w += RunWarning.HR_IMPLAUSIBLE
        if (startAtMillis != null && startAtMillis > nowMillis + 10 * 60_000) w += RunWarning.DATE_IN_FUTURE
        return w
    }

    /**
     * A run is a likely duplicate when it starts within 30 minutes of an existing one (or on the
     * same day if [timeKnown] is false) and the distance differs by less than 3%.
     */
    fun findDuplicate(
        candidateStartMillis: Long,
        candidateDate: java.time.LocalDate,
        candidateDistanceM: Double,
        timeKnown: Boolean,
        existing: List<RunFacts>,
        excludeId: Long? = null,
    ): RunFacts? = existing.firstOrNull { r ->
        if (r.id == excludeId) return@firstOrNull false
        val closeDistance = candidateDistanceM > 0 && abs(r.distanceM - candidateDistanceM) / candidateDistanceM < 0.03
        val closeTime = if (timeKnown) abs(r.startAtMillis - candidateStartMillis) <= 30 * 60_000 else r.localDate == candidateDate
        closeDistance && closeTime
    }
}

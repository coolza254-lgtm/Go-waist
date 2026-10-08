package com.gowaist.app.data.repo

import com.gowaist.app.data.db.RunEntity
import com.gowaist.app.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import com.gowaist.app.data.db.SetRow
import com.gowaist.app.data.settings.SettingsRepository
import com.gowaist.app.data.toLocalDate
import com.gowaist.core.perf.DailyVo2
import com.gowaist.core.perf.FitnessLevel
import com.gowaist.core.perf.LoadPoint
import com.gowaist.core.perf.Profile
import com.gowaist.core.perf.RunSample
import com.gowaist.core.perf.RunType
import com.gowaist.core.perf.TrainingLoad
import com.gowaist.core.perf.Vo2Estimate
import com.gowaist.core.perf.Vo2Max
import com.gowaist.core.perf.metresPerBeat
import com.gowaist.core.stats.weekStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class CooperResult(val run: RunEntity, val vo2: Double, val level: FitnessLevel)

data class PerformanceData(
    val profile: Profile = Profile(),
    val estimates: List<Vo2Estimate> = emptyList(),
    val daily: List<DailyVo2> = emptyList(),
    val load: List<LoadPoint> = emptyList(),
    val cooper: List<CooperResult> = emptyList(),
    val efficiency: List<Pair<LocalDate, Double>> = emptyList(),
    val weeklyKm: List<Pair<LocalDate, Double>> = emptyList(),
    val typeCounts: Map<RunType, Pair<Int, Double>> = emptyMap(),
    val runsById: Map<Long, RunEntity> = emptyMap(),
) {
    val current: Double? get() = daily.lastOrNull()?.trend
    val level: FitnessLevel? get() = current?.let { Vo2Max.level(it, profile) }

    /** Change of the daily VO2 max trend over the last [days] days. */
    fun change(days: Int): Double? {
        if (daily.size < 2) return null
        val now = daily.last()
        val then = daily.getOrNull(daily.size - 1 - days) ?: daily.first()
        return now.trend - then.trend
    }
}

fun RunEntity.toSample() = RunSample(id, localDate.toLocalDate(), distanceM, durationSec, avgHr, runType, rpe)

/**
 * Everything the VO2 max and performance screens show, computed off the main thread from runs,
 * workout sessions and the profile. Shared so several screens reuse one computation.
 */
@Singleton
class PerformanceRepository @Inject constructor(
    runs: RunRepository,
    workouts: WorkoutRepository,
    settings: SettingsRepository,
    @ApplicationScope scope: CoroutineScope,
) {
    val data: Flow<PerformanceData> = combine(runs.runs, workouts.finishedSessions, workouts.setRows, settings.settings) { runList, sessions, rows, s ->
        compute(runList, sessions.map { Triple(it.id, it.localDate, ((it.endAt ?: it.startAt) - it.startAt) / 1000) }, rows, s.profile, LocalDate.now())
    }.flowOn(Dispatchers.Default).shareIn(scope, SharingStarted.WhileSubscribed(10_000), replay = 1)

    companion object {
        fun compute(
            runList: List<RunEntity>,
            sessions: List<Triple<Long, String, Long>>,
            rows: List<SetRow>,
            profile: Profile,
            today: LocalDate,
        ): PerformanceData {
            val samples = runList.map { it.toSample() }
            val estimates = samples.flatMap { Vo2Max.estimates(it, profile) }
            val daily = Vo2Max.daily(estimates, today)

            val loads = HashMap<LocalDate, Double>()
            samples.forEach { r -> loads.merge(r.date, TrainingLoad.load(r.durationSec, r.avgHr, r.rpe, profile), Double::plus) }
            val rpeBySession = rows.groupBy { it.sessionId }.mapValues { (_, l) -> l.mapNotNull { it.set.rpe }.takeIf { it.isNotEmpty() }?.average() }
            sessions.forEach { (id, date, sec) ->
                loads.merge(date.toLocalDate(), TrainingLoad.load(sec, null, rpeBySession[id] ?: 5.5, profile), Double::plus)
            }
            val load = TrainingLoad.series(loads, today.minusDays(89), today)

            val cooper = runList.filter { it.runType == RunType.COOPER && it.durationSec > 0 }.sortedByDescending { it.startAt }.map {
                val v = Vo2Max.cooper(it.distanceM * 720.0 / it.durationSec)
                CooperResult(it, v, Vo2Max.level(v, profile))
            }
            val efficiency = runList.filter { it.avgHr != null && it.runType != RunType.INTERVAL && it.durationSec >= 600 }
                .sortedBy { it.startAt }
                .mapNotNull { r -> metresPerBeat(r.distanceM, r.durationSec, r.avgHr!!)?.let { r.localDate.toLocalDate() to it } }
            val thisWeek = today.weekStart()
            val byWeek = samples.groupBy { it.date.weekStart() }
            val weekly = (11 downTo 0).map { i -> thisWeek.minusWeeks(i.toLong()).let { w -> w to (byWeek[w]?.sumOf { it.distanceM } ?: 0.0) / 1000 } }
            val recent = samples.filter { !it.date.isBefore(today.minusDays(29)) }
            val types = RunType.entries.associateWith { t -> recent.filter { it.type == t }.let { it.size to it.sumOf { r -> r.distanceM } / 1000 } }
            return PerformanceData(profile, estimates, daily, load, cooper, efficiency, weekly, types, runList.associateBy { it.id })
        }
    }
}

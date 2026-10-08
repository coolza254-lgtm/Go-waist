package com.gowaist.app.data.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gowaist.core.perf.AdjustReason
import com.gowaist.core.perf.IntervalConfig
import com.gowaist.core.perf.IntervalResult
import com.gowaist.core.perf.LongRunConfig
import com.gowaist.core.perf.RunProgramRules
import com.gowaist.core.perf.RunPrograms
import com.gowaist.core.perf.RunType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Program settings plus Gogo's pending (not yet accepted) adjustments. */
@Serializable
data class ProgramState(
    val programs: RunPrograms = RunPrograms(),
    val pendingInterval: IntervalConfig? = null,
    val pendingIntervalReason: AdjustReason? = null,
    val pendingLongRun: LongRunConfig? = null,
    val pendingLongRunReason: AdjustReason? = null,
)

private val Context.programStore: DataStore<Preferences> by preferencesDataStore(name = "programs")

@Singleton
class ProgramRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private val key = stringPreferencesKey("state")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun decode(p: Preferences): ProgramState =
        p[key]?.let { runCatching { json.decodeFromString(ProgramState.serializer(), it) }.getOrNull() } ?: ProgramState()

    val state: Flow<ProgramState> = context.programStore.data.map(::decode)

    suspend fun current(): ProgramState = state.first()

    suspend fun update(f: (ProgramState) -> ProgramState) {
        context.programStore.edit { p -> p[key] = json.encodeToString(ProgramState.serializer(), f(decode(p))) }
    }

    suspend fun replace(s: ProgramState) = update { s }

    suspend fun updatePrograms(f: (RunPrograms) -> RunPrograms) = update { it.copy(programs = f(it.programs)) }

    /**
     * Records a finished program session: remembers the Cooper date and works out the next
     * interval / long-run settings. With auto-adjust on they are applied directly, otherwise they
     * wait for the user to accept them.
     */
    suspend fun onSessionSaved(type: RunType, distanceM: Double, interval: IntervalResult?, rpe: Double?, date: LocalDate) {
        update { s ->
            val p = s.programs
            when (type) {
                RunType.COOPER -> s.copy(programs = p.copy(cooper = p.cooper.copy(lastTest = date.toString())))
                RunType.INTERVAL -> {
                    val adj = RunProgramRules.adaptInterval(interval ?: IntervalResult(p.interval, p.interval.reps), rpe)
                    when {
                        adj.next == p.interval -> s.copy(pendingInterval = null, pendingIntervalReason = null)
                        p.autoAdjust -> s.copy(programs = p.copy(interval = adj.next), pendingInterval = null, pendingIntervalReason = adj.reason)
                        else -> s.copy(pendingInterval = adj.next, pendingIntervalReason = adj.reason)
                    }
                }
                RunType.LONG -> {
                    val adj = RunProgramRules.adaptLongRun(p.longRun, distanceM / 1000.0)
                    when {
                        adj.next == p.longRun -> s.copy(pendingLongRun = null, pendingLongRunReason = null)
                        p.autoAdjust -> s.copy(programs = p.copy(longRun = adj.next), pendingLongRun = null, pendingLongRunReason = adj.reason)
                        else -> s.copy(pendingLongRun = adj.next, pendingLongRunReason = adj.reason)
                    }
                }
                RunType.FREE -> s
            }
        }
    }

    suspend fun acceptInterval() = update { s -> s.pendingInterval?.let { s.copy(programs = s.programs.copy(interval = it), pendingInterval = null) } ?: s }
    suspend fun dismissInterval() = update { it.copy(pendingInterval = null) }
    suspend fun acceptLongRun() = update { s -> s.pendingLongRun?.let { s.copy(programs = s.programs.copy(longRun = it), pendingLongRun = null) } ?: s }
    suspend fun dismissLongRun() = update { it.copy(pendingLongRun = null) }
}

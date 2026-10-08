package com.gowaist.app.ui.train

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.gowaist.app.data.db.ExerciseEntity
import com.gowaist.app.data.db.SessionEntity
import com.gowaist.app.data.db.SetEntity
import com.gowaist.app.data.key
import com.gowaist.app.data.repo.ExerciseMemory
import com.gowaist.app.data.repo.LibraryRepository
import com.gowaist.app.data.repo.SessionDetail
import com.gowaist.app.data.repo.SessionExerciseView
import com.gowaist.app.data.repo.WorkoutRepository
import com.gowaist.app.data.settings.AppSettings
import com.gowaist.app.data.settings.SettingsRepository
import com.gowaist.app.data.toLocalDateTime
import com.gowaist.app.domain.ActivityCoordinator
import com.gowaist.app.timer.TimerController
import com.gowaist.app.ui.nav.SessionRoute
import com.gowaist.core.model.SetType
import com.gowaist.core.model.TrackingType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Values in the input panel of one exercise card. Weights are kilograms. */
data class SetInput(
    val reps: Int = 10,
    val sec: Int = 30,
    val addedKg: Double = 0.0,
    val assistKg: Double = 0.0,
    val distanceM: Int = 0,
    val rpe: Double? = null,
    val rir: Int? = null,
    val setType: SetType = SetType.NORMAL,
)

fun SetEntity.toInput() = SetInput(
    reps = reps ?: 10, sec = durationSec ?: 30, addedKg = addedWeightKg ?: 0.0, assistKg = assistKg ?: 0.0,
    distanceM = distanceM?.toInt() ?: 0, rpe = rpe, rir = rir, setType = setType,
)

@HiltViewModel
class SessionViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val workouts: WorkoutRepository,
    library: LibraryRepository,
    settings: SettingsRepository,
    val timer: TimerController,
    private val coordinator: ActivityCoordinator,
) : ViewModel() {
    val sessionId = handle.toRoute<SessionRoute>().id

    val detail: StateFlow<SessionDetail?> = workouts.observeDetail(sessionId).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val exercises: StateFlow<List<ExerciseEntity>> = library.exercises.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val settings: StateFlow<AppSettings> = settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val inputs = mutableStateMapOf<Long, SetInput>()
    val memories = mutableStateMapOf<Long, ExerciseMemory>()
    var focused by mutableStateOf<Long?>(null)
    var closed by mutableStateOf(false)
    var summaryFor by mutableStateOf<Long?>(null)

    /** Prepares inputs and "last time" memory for newly added exercises. */
    fun sync(d: SessionDetail) {
        d.exercises.forEach { v ->
            if (v.exercise.id !in memories) {
                viewModelScope.launch {
                    val m = workouts.memory(v.exercise.id, sessionId)
                    memories[v.exercise.id] = m
                    if (v.sets.isEmpty() && inputs[v.entry.id] == defaultInput(v)) {
                        m.lastSets.firstOrNull { it.setType != SetType.WARMUP }?.let { inputs[v.entry.id] = it.toInput().copy(rpe = null, rir = null, setType = SetType.NORMAL) }
                    }
                }
            }
            if (v.entry.id !in inputs) inputs[v.entry.id] = v.sets.lastOrNull()?.toInput() ?: defaultInput(v)
        }
        if (focused == null || d.exercises.none { it.entry.id == focused }) {
            focused = d.exercises.firstOrNull { (it.entry.targetSets ?: 3) > it.sets.size }?.entry?.id ?: d.exercises.firstOrNull()?.entry?.id
        }
    }

    private fun defaultInput(v: SessionExerciseView): SetInput {
        val t = v.entry.targetRepsOrSec
        return when (v.exercise.trackingType) {
            TrackingType.HOLD -> SetInput(sec = t ?: 30)
            TrackingType.CARDIO -> SetInput(sec = t ?: 60)
            else -> SetInput(reps = t ?: 10)
        }
    }

    fun update(entryId: Long, f: (SetInput) -> SetInput) {
        inputs[entryId] = f(inputs[entryId] ?: SetInput())
    }

    fun log(v: SessionExerciseView, overrideSec: Int? = null) = viewModelScope.launch {
        val input = inputs[v.entry.id] ?: SetInput()
        val t = v.exercise.trackingType
        val set = SetEntity(
            sessionExerciseId = v.entry.id,
            setOrder = (v.sets.maxOfOrNull { it.setOrder } ?: -1) + 1,
            setType = input.setType,
            reps = if (t == TrackingType.HOLD || t == TrackingType.CARDIO) null else input.reps,
            durationSec = if (t == TrackingType.HOLD || t == TrackingType.CARDIO) (overrideSec ?: input.sec) else null,
            addedWeightKg = if (t == TrackingType.WEIGHTED) input.addedKg.takeIf { it > 0 } else null,
            assistKg = if (t == TrackingType.ASSISTED) input.assistKg.takeIf { it > 0 } else null,
            distanceM = if (t == TrackingType.CARDIO) input.distanceM.toDouble().takeIf { it > 0 } else null,
            rpe = input.rpe,
            rir = input.rir,
        )
        workouts.logSet(set)
        if (overrideSec != null) update(v.entry.id) { it.copy(sec = overrideSec) }
        afterSet(v)
    }

    /** One tap: log exactly what the previous set was. */
    fun repeatPrevious(v: SessionExerciseView) = viewModelScope.launch {
        val last = v.sets.lastOrNull() ?: return@launch
        workouts.logSet(last.copy(id = 0, setOrder = last.setOrder + 1, completedAt = System.currentTimeMillis(), note = ""))
        afterSet(v)
    }

    /** Rest timer and focus handling; supersets rotate through the group before resting. */
    private fun afterSet(v: SessionExerciseView) {
        val d = detail.value ?: return
        val isDraft = d.session.isDraft
        val group = v.entry.supersetGroup
        val members = if (group != null) d.exercises.filter { it.entry.supersetGroup == group } else listOf(v)
        val idx = members.indexOfFirst { it.entry.id == v.entry.id }
        if (group != null && idx < members.lastIndex) {
            focused = members[idx + 1].entry.id
            return
        }
        val nextFocus = if (group != null) members.first() else v
        focused = nextFocus.entry.id
        if (isDraft && v.entry.restSec > 0) timer.startRest(v.entry.restSec, nextFocus.exercise.nameTh)
    }

    fun startHold(v: SessionExerciseView) {
        timer.startHold(v.exercise.nameTh, v.entry.targetRepsOrSec, v.entry.id)
    }

    fun stopHold() {
        val d = detail.value ?: return
        val state = timer.state.value as? com.gowaist.app.timer.TimerState.Hold ?: return
        val sec = timer.stopHold()
        val v = d.exercises.firstOrNull { it.entry.id == state.entryId } ?: return
        if (sec > 0) log(v, overrideSec = sec)
    }

    fun updateSet(s: SetEntity) = viewModelScope.launch { workouts.updateSet(s) }
    fun deleteSet(id: Long) = viewModelScope.launch { workouts.deleteSet(id) }

    fun addExercises(list: List<ExerciseEntity>) = viewModelScope.launch {
        var last: Long? = null
        list.forEach { last = workouts.addExercise(sessionId, it, restSec = it.defaultRestSec.takeIf { r -> r > 0 } ?: settings.value.defaultRestSec) }
        last?.let { focused = it }
    }

    fun remove(v: SessionExerciseView) = viewModelScope.launch { workouts.removeExercise(v.entry.id) }

    fun move(v: SessionExerciseView, delta: Int) = viewModelScope.launch {
        val ids = detail.value?.exercises?.map { it.entry.id }?.toMutableList() ?: return@launch
        val i = ids.indexOf(v.entry.id)
        val j = (i + delta).coerceIn(0, ids.lastIndex)
        if (i == j) return@launch
        ids.add(j, ids.removeAt(i))
        workouts.reorder(sessionId, ids)
    }

    fun linkWithNext(v: SessionExerciseView) = viewModelScope.launch {
        val list = detail.value?.exercises ?: return@launch
        val next = list.getOrNull(list.indexOfFirst { it.entry.id == v.entry.id } + 1) ?: return@launch
        val group = v.entry.supersetGroup ?: next.entry.supersetGroup ?: workouts.nextGroupNumber(sessionId)
        workouts.setGroup(sessionId, setOf(v.entry.id, next.entry.id), group)
    }

    fun unlink(v: SessionExerciseView) = viewModelScope.launch { workouts.setGroup(sessionId, setOf(v.entry.id), null) }

    fun setRest(v: SessionExerciseView, sec: Int) = viewModelScope.launch { workouts.updateExercise(v.entry.copy(restSec = sec.coerceIn(0, 600))) }

    fun updateSession(s: SessionEntity) = viewModelScope.launch {
        val date = s.startAt.toLocalDateTime().toLocalDate()
        workouts.updateSession(s.copy(localDate = date.key()))
    }

    fun finish(feeling: Int?, note: String) = viewModelScope.launch {
        timer.stop()
        val r = workouts.finish(sessionId, feeling, note)
        // Records are celebrated on the summary screen; goals and badges via the global overlay.
        coordinator.afterChange(records = emptyList())
        summaryFor = r.sessionId
    }

    fun saveEdits() = viewModelScope.launch {
        workouts.recomputeRecords(sessionId)
        coordinator.afterChange(celebrate = false)
        closed = true
    }

    fun discard() = viewModelScope.launch {
        timer.stop()
        workouts.discard(sessionId)
        coordinator.afterChange(celebrate = false)
        closed = true
    }
}

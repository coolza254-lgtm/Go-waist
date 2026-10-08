package com.gowaist.app.ui.train

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.toRoute
import com.gowaist.app.R
import com.gowaist.app.data.db.ExerciseEntity
import com.gowaist.app.data.db.SetEntity
import com.gowaist.app.data.repo.LibraryRepository
import com.gowaist.app.data.repo.WorkoutRepository
import com.gowaist.app.domain.ActivityCoordinator
import com.gowaist.app.timer.TimerController
import com.gowaist.app.timer.TimerState
import com.gowaist.app.ui.Fmt
import com.gowaist.app.ui.GwTopBar
import com.gowaist.app.ui.LocalAppSettings
import com.gowaist.app.ui.components.GameButton
import com.gowaist.app.ui.components.GameCard
import com.gowaist.app.ui.components.GameProgressBar
import com.gowaist.app.ui.components.NumberStepper
import com.gowaist.app.ui.nav.IntervalTimerRoute
import com.gowaist.app.ui.nav.SessionSummaryRoute
import com.gowaist.app.ui.theme.Gw
import com.gowaist.app.ui.theme.NumberStyles
import com.gowaist.app.ui.theme.Palette
import com.gowaist.core.model.TrackingType
import com.gowaist.core.timer.IntervalProgram
import com.gowaist.core.timer.PhaseKind
import com.gowaist.core.timer.TimerConfig
import com.gowaist.core.timer.TimerMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class IntervalTimerViewModel @Inject constructor(
    handle: SavedStateHandle,
    val timer: TimerController,
    private val workouts: WorkoutRepository,
    library: LibraryRepository,
    private val coordinator: ActivityCoordinator,
) : ViewModel() {
    private val planDayId = handle.toRoute<IntervalTimerRoute>().planDayId.takeIf { it != 0L }
    var mode by mutableStateOf(TimerMode.TABATA)
    var minutes by mutableStateOf(10)
    var rounds by mutableStateOf(8)
    var work by mutableStateOf(40)
    var rest by mutableStateOf(20)
    var startedAt = 0L
    var savedSession by mutableStateOf<Long?>(null)
    val exercises = library.exercises.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun config(): TimerConfig = when (mode) {
        TimerMode.EMOM -> TimerConfig.emom(minutes)
        TimerMode.AMRAP -> TimerConfig.amrap(minutes)
        TimerMode.TABATA -> TimerConfig.tabata(rounds)
        TimerMode.CUSTOM -> TimerConfig.custom(work, rest, rounds)
    }

    fun start() {
        startedAt = System.currentTimeMillis()
        timer.startInterval(config())
    }

    fun save(config: TimerConfig, roundsDone: Int, items: List<Pair<ExerciseEntity, Int>>) = viewModelScope.launch {
        timer.stop()
        val start = startedAt.takeIf { it > 0 } ?: System.currentTimeMillis()
        val id = workouts.start(planDayId = planDayId, name = config.mode.name, startAt = start, draft = false, timerMode = config.mode.name)
        val total = IntervalProgram.totalSec(config)
        workouts.session(id)?.let { workouts.updateSession(it.copy(endAt = start + total * 1000L, timerResult = "$roundsDone")) }
        items.forEach { (ex, reps) ->
            val entry = workouts.addExercise(id, ex, restSec = 0)
            repeat(roundsDone.coerceAtLeast(1)) { r ->
                val hold = ex.trackingType == TrackingType.HOLD || ex.trackingType == TrackingType.CARDIO
                workouts.logSet(
                    SetEntity(
                        sessionExerciseId = entry, setOrder = r,
                        reps = if (hold) null else reps,
                        durationSec = if (hold) (if (config.mode == TimerMode.EMOM) reps else config.workSec) else null,
                    ),
                )
            }
        }
        workouts.recomputeRecords(id)
        coordinator.afterChange()
        savedSession = id
    }
}

@Composable
fun IntervalTimerScreen(nav: NavHostController, vm: IntervalTimerViewModel = hiltViewModel()) {
    val state by vm.timer.state.collectAsStateWithLifecycle()
    val now by vm.timer.now.collectAsStateWithLifecycle()
    LaunchedEffect(vm.savedSession) { vm.savedSession?.let { nav.navigate(SessionSummaryRoute(it)) { popUpTo<IntervalTimerRoute> { inclusive = true } } } }
    val interval = state as? TimerState.Interval
    KeepScreenOn(interval != null && !interval.finished)
    if (interval == null) {
        Setup(nav, vm)
    } else {
        Running(interval, now, vm, onStop = { vm.timer.stop() })
        if (interval.finished) SaveDialog(vm, interval.config, onDiscard = { vm.timer.stop() })
    }
}

@Composable
private fun Setup(nav: NavHostController, vm: IntervalTimerViewModel) {
    Scaffold(
        topBar = { GwTopBar(stringResource(R.string.it_title), onBack = { nav.popBackStack() }) },
        bottomBar = {
            Column(Modifier.navigationBarsPadding().padding(16.dp)) {
                Text(stringResource(R.string.it_total, Fmt.duration(IntervalProgram.totalSec(vm.config()).toLong())), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                GameButton(stringResource(R.string.it_start), { vm.start() }, color = Gw.colors.danger, icon = Icons.Rounded.PlayArrow, big = true, modifier = Modifier.fillMaxWidth())
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(
                Triple(TimerMode.EMOM, R.string.it_emom, R.string.it_emom_desc),
                Triple(TimerMode.AMRAP, R.string.it_amrap, R.string.it_amrap_desc),
                Triple(TimerMode.TABATA, R.string.it_tabata, R.string.it_tabata_desc),
                Triple(TimerMode.CUSTOM, R.string.it_custom, R.string.it_custom_desc),
            ).forEach { (m, title, desc) ->
                GameCard(accent = if (vm.mode == m) Gw.colors.danger else null, onClick = { vm.mode = m }) {
                    Text(stringResource(title), style = MaterialTheme.typography.titleLarge, color = if (vm.mode == m) Gw.colors.danger else MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (vm.mode == m) {
                        Spacer(Modifier.height(10.dp))
                        when (m) {
                            TimerMode.EMOM, TimerMode.AMRAP -> NumberStepper("${vm.minutes}", stringResource(R.string.it_minutes), { vm.minutes = (vm.minutes - 1).coerceAtLeast(1) }, { vm.minutes = (vm.minutes + 1).coerceAtMost(90) }, color = Gw.colors.danger)
                            TimerMode.TABATA -> NumberStepper("${vm.rounds}", stringResource(R.string.it_rounds), { vm.rounds = (vm.rounds - 1).coerceAtLeast(1) }, { vm.rounds = (vm.rounds + 1).coerceAtMost(40) }, color = Gw.colors.danger)
                            TimerMode.CUSTOM -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                NumberStepper("${vm.work}", stringResource(R.string.it_work), { vm.work = (vm.work - 5).coerceAtLeast(5) }, { vm.work += 5 }, color = Gw.colors.danger)
                                NumberStepper("${vm.rest}", stringResource(R.string.it_rest), { vm.rest = (vm.rest - 5).coerceAtLeast(0) }, { vm.rest += 5 }, color = Gw.colors.goal)
                                NumberStepper("${vm.rounds}", stringResource(R.string.it_rounds), { vm.rounds = (vm.rounds - 1).coerceAtLeast(1) }, { vm.rounds = (vm.rounds + 1).coerceAtMost(60) }, color = Gw.colors.plan)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Running(s: TimerState.Interval, now: Long, vm: IntervalTimerViewModel, onStop: () -> Unit) {
    val pos = IntervalProgram.locate(s.phases, s.elapsedMs(now))
    val bg by animateColorAsState(
        when {
            s.finished -> Gw.colors.success
            pos.phase.kind == PhaseKind.WORK -> Gw.colors.danger
            pos.phase.kind == PhaseKind.REST -> Gw.colors.goal
            else -> Gw.colors.plan
        },
        label = "bg",
    )
    Box(Modifier.fillMaxSize().background(bg)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(s.config.mode.name, style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(
                        when {
                            s.finished -> R.string.it_done
                            pos.phase.kind == PhaseKind.PREP -> R.string.timer_phase_prep
                            pos.phase.kind == PhaseKind.WORK -> R.string.timer_phase_work
                            else -> R.string.timer_phase_rest
                        },
                    ),
                    style = MaterialTheme.typography.displaySmall, color = Color.White,
                )
                Text(Fmt.duration((pos.remainingMs + 999) / 1000), style = NumberStyles.huge.copy(fontSize = 110.sp, lineHeight = 116.sp), color = Color.White)
                if (pos.phase.round > 0) Text(stringResource(R.string.it_round_now, pos.phase.round, pos.phase.totalRounds), style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Spacer(Modifier.height(16.dp))
                val total = s.phases.sumOf { it.durationSec } * 1000f
                GameProgressBar(s.elapsedMs(now) / total, color = Color.White, height = 14.dp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!s.finished) {
                    GameButton(
                        stringResource(if (s.pausedAt == null) R.string.it_pause else R.string.it_resume), { vm.timer.togglePause() },
                        color = Color.White.copy(alpha = 0.9f), icon = if (s.pausedAt == null) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, modifier = Modifier.weight(1f),
                    )
                    GameButton(stringResource(R.string.it_stop), onStop, color = Palette.Ink, icon = Icons.Rounded.Stop, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SaveDialog(vm: IntervalTimerViewModel, config: TimerConfig, onDiscard: () -> Unit) {
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    var roundsDone by remember { mutableStateOf(config.rounds.toString()) }
    var items by remember { mutableStateOf(listOf<Pair<ExerciseEntity, Int>>()) }
    var picker by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.it_save_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(roundsDone, { roundsDone = it.filter(Char::isDigit).take(3) }, label = { Text(stringResource(R.string.it_rounds_done)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                Text(stringResource(R.string.it_exercises), style = MaterialTheme.typography.labelLarge)
                items.forEachIndexed { i, (ex, reps) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(ex.nameTh, Modifier.weight(1f))
                        SmallNumber(stringResource(if (ex.trackingType == TrackingType.HOLD || ex.trackingType == TrackingType.CARDIO) R.string.unit_sec else R.string.it_reps_per_round), reps, Modifier.width(110.dp)) { v ->
                            items = items.mapIndexed { j, p -> if (j == i) p.first to v else p }
                        }
                        IconButton({ items = items.filterIndexed { j, _ -> j != i } }) { Icon(Icons.Rounded.Delete, stringResource(R.string.action_delete)) }
                    }
                }
                TextButton({ picker = true }) { Icon(Icons.Rounded.Add, null); Text(stringResource(R.string.tpl_add_exercise)) }
            }
        },
        confirmButton = {
            TextButton({ vm.save(config, roundsDone.toIntOrNull() ?: config.rounds, items) }) { Icon(Icons.Rounded.Save, null); Text(stringResource(R.string.it_save)) }
        },
        dismissButton = { TextButton(onDiscard) { Text(stringResource(R.string.it_dont_save)) } },
    )
    if (picker) {
        ExercisePickerDialog(exercises, LocalAppSettings.current.equipment, multi = true, onDismiss = { picker = false }) { picked ->
            picker = false
            items = items + picked.map { it to if (it.trackingType == TrackingType.HOLD || it.trackingType == TrackingType.CARDIO) config.workSec.coerceAtMost(60) else 10 }
        }
    }
}

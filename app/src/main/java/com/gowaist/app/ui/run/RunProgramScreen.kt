package com.gowaist.app.ui.run

import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.gowaist.app.data.db.RunEntity
import com.gowaist.app.data.ocr.OcrService
import com.gowaist.app.data.repo.CooperResult
import com.gowaist.app.data.repo.PerformanceRepository
import com.gowaist.app.data.repo.ProgramRepository
import com.gowaist.app.data.repo.ProgramState
import com.gowaist.app.data.repo.RunRepository
import com.gowaist.app.data.repo.toSample
import com.gowaist.app.data.settings.SettingsRepository
import com.gowaist.app.data.toLocalDate
import com.gowaist.app.domain.ActivityCoordinator
import com.gowaist.app.timer.TimerController
import com.gowaist.app.timer.TimerState
import com.gowaist.app.ui.Fmt
import com.gowaist.app.ui.GwTopBar
import com.gowaist.app.ui.components.ConfirmDialog
import com.gowaist.app.ui.components.GameBanner
import com.gowaist.app.ui.components.GameButton
import com.gowaist.app.ui.components.GameCard
import com.gowaist.app.ui.components.GameProgressBar
import com.gowaist.app.ui.components.Mascot
import com.gowaist.app.ui.components.NumberStepper
import com.gowaist.app.ui.components.Pill
import com.gowaist.app.ui.components.RoundGameButton
import com.gowaist.app.ui.components.SectionHeader
import com.gowaist.app.ui.components.StatTile
import com.gowaist.app.ui.perf.color
import com.gowaist.app.ui.perf.label
import com.gowaist.app.ui.perf.summary
import com.gowaist.app.ui.nav.RunDetailRoute
import com.gowaist.app.ui.nav.RunProgramRoute
import com.gowaist.app.ui.theme.Gw
import com.gowaist.app.ui.theme.Palette
import com.gowaist.app.ui.theme.shade
import com.gowaist.core.Format
import com.gowaist.core.Pace
import com.gowaist.core.mascot.MascotMood
import com.gowaist.core.ocr.RunTextParser
import com.gowaist.core.perf.AdjustReason
import com.gowaist.core.perf.IntervalConfig
import com.gowaist.core.perf.IntervalResult
import com.gowaist.core.perf.RunPaces
import com.gowaist.core.perf.RunProgramRules
import com.gowaist.core.perf.RunType
import com.gowaist.core.perf.Vo2Max
import com.gowaist.core.timer.IntervalProgram
import com.gowaist.core.timer.Phase
import com.gowaist.core.timer.PhaseKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

enum class ProgramStage { SETUP, RUNNING, RESULT, SAVED }

@HiltViewModel
class RunProgramViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val programs: ProgramRepository,
    private val timer: TimerController,
    private val runs: RunRepository,
    private val settings: SettingsRepository,
    private val ocr: OcrService,
    perf: PerformanceRepository,
    coordinator: ActivityCoordinator,
) : ViewModel() {
    val type: RunType = runCatching { RunType.valueOf(handle.toRoute<RunProgramRoute>().type) }.getOrDefault(RunType.FREE)
    private val saver = RunSaver(runs, coordinator)

    val state = programs.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val perf = perf.data.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val history = runs.runs.map { l -> l.filter { it.runType == type }.take(6) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val existingTags = runs.runs.map { l -> l.flatMap { it.tags }.distinct() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val timerState: StateFlow<TimerState> = timer.state
    val now: StateFlow<Long> = timer.now

    var stage by mutableStateOf(if (isMine(timer.state.value)) ProgramStage.RUNNING else ProgramStage.SETUP)
        private set
    var form by mutableStateOf<RunForm?>(null)
        private set
    var repsDone by mutableStateOf(0)
    var reading by mutableStateOf(false)
        private set
    var ocrFailed by mutableStateOf(false)
        private set
    var duplicate by mutableStateOf<RunEntity?>(null)
    var missing by mutableStateOf(false)
    var saved by mutableStateOf<RunEntity?>(null)
        private set
    var savedVo2 by mutableStateOf<Double?>(null)
        private set

    private fun isMine(s: TimerState) = (s is TimerState.Program && s.tag == type.name) || (s is TimerState.Stopwatch && s.tag == type.name)

    fun isMineNow(s: TimerState) = isMine(s)

    fun editInterval(f: (IntervalConfig) -> IntervalConfig) = viewModelScope.launch {
        programs.updatePrograms { p ->
            val c = f(p.interval)
            p.copy(
                interval = c.copy(
                    warmupMin = c.warmupMin.coerceIn(0, 30),
                    workSec = c.workSec.coerceIn(10, 900),
                    restSec = c.restSec.coerceIn(0, 600),
                    reps = c.reps.coerceIn(1, 20),
                    cooldownMin = c.cooldownMin.coerceIn(0, 30),
                ),
            )
        }
    }

    fun editLongTarget(deltaKm: Double) = viewModelScope.launch {
        programs.updatePrograms { p -> p.copy(longRun = p.longRun.copy(targetKm = (p.longRun.targetKm + deltaKm).coerceIn(1.0, 60.0))) }
    }

    fun editFreeTarget(deltaMin: Int) = viewModelScope.launch {
        programs.updatePrograms { p -> p.copy(freeRun = p.freeRun.copy(targetMin = (p.freeRun.targetMin + deltaMin).coerceIn(0, 300))) }
    }

    fun setAuto(on: Boolean) = viewModelScope.launch { programs.updatePrograms { it.copy(autoAdjust = on) } }
    fun acceptSuggestion() = viewModelScope.launch { if (type == RunType.INTERVAL) programs.acceptInterval() else programs.acceptLongRun() }
    fun dismissSuggestion() = viewModelScope.launch { if (type == RunType.INTERVAL) programs.dismissInterval() else programs.dismissLongRun() }

    fun start() {
        val p = state.value?.programs ?: return
        when (type) {
            RunType.COOPER -> timer.startProgram(type.name, RunProgramRules.cooperPhases())
            RunType.INTERVAL -> timer.startProgram(type.name, RunProgramRules.intervalPhases(p.interval))
            RunType.FREE -> timer.startStopwatch(type.name, p.freeRun.targetMin.takeIf { it > 0 }?.times(60))
            RunType.LONG -> timer.startStopwatch(type.name, null)
        }
        stage = ProgramStage.RUNNING
    }

    fun togglePause() = timer.togglePause()

    /** Stops the timer and opens the result form with the measured time. */
    fun finish() {
        val s = timer.state.value
        val nowMs = SystemClock.elapsedRealtime()
        var wallStart = System.currentTimeMillis()
        var sec = 0L
        var reps = 0
        when (s) {
            is TimerState.Program -> {
                val elapsed = s.elapsedMs(nowMs)
                val prep = s.phases.takeWhile { it.kind == PhaseKind.PREP }.sumOf { it.durationSec }
                val total = s.phases.sumOf { it.durationSec }
                sec = (elapsed / 1000).coerceAtMost(total.toLong()) - prep
                reps = completedReps(s.phases, elapsed)
                wallStart = s.wallStart + prep * 1000L
            }
            is TimerState.Stopwatch -> {
                sec = s.elapsedMs(nowMs) / 1000
                wallStart = s.wallStart
            }
            else -> Unit
        }
        stage = ProgramStage.RESULT
        if (isMine(s)) timer.stop()
        viewModelScope.launch { openResult(wallStart, sec.coerceAtLeast(0), reps, timeKnown = true) }
    }

    /** Records a run done without the in-app timer (e.g. with a watch). */
    fun recordOnly() {
        stage = ProgramStage.RESULT
        viewModelScope.launch {
            val reps = state.value?.programs?.interval?.reps ?: 0
            openResult(System.currentTimeMillis(), if (type == RunType.COOPER) 720 else 0, reps, timeKnown = false)
        }
    }

    fun timerLost() {
        if (stage == ProgramStage.RUNNING) stage = ProgramStage.SETUP
    }

    fun discard() {
        if (isMine(timer.state.value)) timer.stop()
        form = null
        ocrFailed = false
        stage = ProgramStage.SETUP
    }

    private suspend fun openResult(wallStart: Long, sec: Long, reps: Int, timeKnown: Boolean) {
        val st = settings.current()
        val dt = Instant.ofEpochMilli(wallStart).atZone(ZoneId.systemDefault()).toLocalDateTime()
        form = RunForm(st.distanceUnit).apply {
            date = dt.toLocalDate()
            time = if (timeKnown) dt.toLocalTime().withSecond(0).withNano(0) else null
            if (sec > 0) setDuration(sec)
            runType = type
            keepImage = st.keepRunImages
        }
        repsDone = reps
        ocrFailed = false
    }

    fun importImage(uri: Uri) = viewModelScope.launch {
        val f = form ?: return@launch
        reading = true
        val parsed = runCatching { RunTextParser(LocalDate.now()).parseLines(ocr.readLines(uri)) }.getOrNull()?.takeUnless { it.isEmpty }
        reading = false
        ocrFailed = parsed == null
        if (parsed != null) {
            val keep = f.runType
            f.fill(parsed)
            f.runType = keep
            f.imageUri = uri
        }
    }

    fun save(force: Boolean = false) = viewModelScope.launch {
        val f = form ?: return@launch
        val cfg = state.value?.programs?.interval ?: IntervalConfig()
        val result = if (f.runType == RunType.INTERVAL) IntervalResult(cfg, repsDone.coerceIn(0, cfg.reps)) else null
        f.intervalJson = result?.let { Json.encodeToString(IntervalResult.serializer(), it) }
        when (val r = saver.save(f, force)) {
            RunSaver.Result.Missing -> missing = true
            is RunSaver.Result.Duplicate -> duplicate = r.existing
            is RunSaver.Result.Saved -> {
                val run = runs.get(r.id) ?: return@launch
                programs.onSessionSaved(run.runType, run.distanceM, result, run.rpe, run.localDate.toLocalDate())
                savedVo2 = Vo2Max.estimates(run.toSample(), settings.current().profile).maxByOrNull { it.weight }?.value
                saved = run
                stage = ProgramStage.SAVED
            }
        }
    }

    companion object {
        /** Work bouts fully completed after [elapsedMs] (a second of slack for the last tick). */
        fun completedReps(phases: List<Phase>, elapsedMs: Long): Int {
            var acc = 0L
            var done = 0
            for (p in phases) {
                acc += p.durationSec * 1000L
                if (p.kind == PhaseKind.WORK && acc <= elapsedMs + 1000) done++
                if (acc > elapsedMs + 1000) break
            }
            return done
        }
    }
}

fun RunType.icon(): ImageVector = when (this) {
    RunType.COOPER -> Icons.Rounded.Timer
    RunType.INTERVAL -> Icons.Rounded.Bolt
    RunType.FREE -> Icons.AutoMirrored.Rounded.DirectionsRun
    RunType.LONG -> Icons.Rounded.Route
}

@Composable
fun RunProgramScreen(nav: NavHostController, vm: RunProgramViewModel = hiltViewModel()) {
    val color = ProgramColors.of(vm.type)
    var quit by remember { mutableStateOf(false) }
    BackHandler(enabled = vm.stage == ProgramStage.RESULT) { quit = true }
    when (vm.stage) {
        ProgramStage.SETUP -> ProgramSetup(nav, vm, color)
        ProgramStage.RUNNING -> RunningHud(vm, color, onQuit = { quit = true })
        ProgramStage.RESULT -> ProgramResult(vm, color, onBack = { quit = true })
        ProgramStage.SAVED -> ProgramSaved(nav, vm, color)
    }
    if (quit) {
        ConfirmDialog(
            title = stringResource(R.string.hud_quit),
            message = stringResource(R.string.hud_quit_text),
            confirmText = stringResource(R.string.hud_quit),
            onConfirm = { vm.discard() },
            onDismiss = { quit = false },
            destructive = true,
        )
    }
}

// ------------------------------------------------------------------------------------------ setup

@Composable
private fun ProgramSetup(nav: NavHostController, vm: RunProgramViewModel, color: Color) {
    val state by vm.state.collectAsStateWithLifecycle()
    val perf by vm.perf.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val s = state ?: return
    val p = s.programs
    Scaffold(
        topBar = { GwTopBar(vm.type.label(), onBack = { nav.popBackStack() }) },
        bottomBar = {
            Column(Modifier.navigationBarsPadding().padding(16.dp, 8.dp, 16.dp, 8.dp)) {
                GameButton(stringResource(R.string.setup_start), vm::start, color = color, icon = Icons.Rounded.PlayArrow, big = true, modifier = Modifier.fillMaxWidth().testTag("program_start"))
                TextButton(vm::recordOnly, Modifier.fillMaxWidth().testTag("program_record")) { Text(stringResource(R.string.setup_record_only)) }
            }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SetupBanner(vm.type, s, color)
            when (vm.type) {
                RunType.COOPER -> CooperSetup(perf?.cooper.orEmpty(), color)
                RunType.INTERVAL -> {
                    Suggestion(s.pendingInterval?.summary(), s.pendingIntervalReason, color, vm::acceptSuggestion, vm::dismissSuggestion)
                    IntervalSetup(p.interval, perf?.current, color, vm)
                    AutoSwitch(p.autoAdjust, vm::setAuto)
                }
                RunType.FREE -> GameCard {
                    NumberStepper(
                        if (p.freeRun.targetMin > 0) "${p.freeRun.targetMin}" else "∞",
                        stringResource(R.string.setup_free_target),
                        { vm.editFreeTarget(-5) }, { vm.editFreeTarget(5) }, color = color,
                    )
                }
                RunType.LONG -> {
                    Suggestion(s.pendingLongRun?.let { Fmt.distance(it.targetKm * 1000, 1) }, s.pendingLongRunReason, color, vm::acceptSuggestion, vm::dismissSuggestion)
                    GameCard {
                        NumberStepper(Fmt.distance(p.longRun.targetKm * 1000, 1), stringResource(R.string.setup_long_target), { vm.editLongTarget(-0.5) }, { vm.editLongTarget(0.5) }, color = color)
                    }
                    AutoSwitch(p.autoAdjust, vm::setAuto)
                    if (history.isNotEmpty()) {
                        SectionHeader(stringResource(R.string.setup_long_history))
                        history.forEach { r ->
                            GameCard(onClick = { nav.navigate(RunDetailRoute(r.id)) }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(Fmt.dateShort(r.localDate.toLocalDate()), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                                    Text(Fmt.distance(r.distanceM, 1), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                                }
                                Spacer(Modifier.height(6.dp))
                                GameProgressBar((r.distanceM / 1000 / p.longRun.targetKm).toFloat(), color = color, height = 12.dp)
                            }
                        }
                    }
                }
            }
            if (vm.type != RunType.LONG && history.isNotEmpty() && vm.type != RunType.COOPER) {
                SectionHeader(stringResource(R.string.hub_recent))
                history.take(3).forEach { r -> RunRow(r) { nav.navigate(RunDetailRoute(r.id)) } }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SetupBanner(type: RunType, s: ProgramState, color: Color) {
    val p = s.programs
    val today = LocalDate.now()
    GameBanner(listOf(color, color.shade(0.75f)), seed = type.ordinal + 2) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).background(Color.White.copy(alpha = 0.25f), androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                Icon(type.icon(), null, tint = Color.White, modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(type.label(), style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Black)
                val sub = when (type) {
                    RunType.COOPER -> if (RunProgramRules.cooperDue(p.cooper, today)) stringResource(R.string.prog_cooper_due) else stringResource(R.string.prog_cooper_in, RunProgramRules.daysUntilCooper(p.cooper, today))
                    RunType.INTERVAL -> stringResource(R.string.prog_level, RunProgramRules.intervalLevel(p.interval)) + " · " +
                        stringResource(R.string.setup_total, Format.duration(RunProgramRules.intervalPhases(p.interval, prepSec = 0).sumOf { it.durationSec }.toLong()))
                    RunType.FREE -> if (p.freeRun.targetMin > 0) stringResource(R.string.prog_free_sub, p.freeRun.targetMin) else stringResource(R.string.prog_free_sub_none)
                    RunType.LONG -> stringResource(R.string.prog_level, RunProgramRules.longRunLevel(p.longRun)) + " · " + stringResource(R.string.prog_long_sub, Fmt.distance(p.longRun.targetKm * 1000, 1))
                }
                Text(sub, style = MaterialTheme.typography.titleSmall, color = Color.White.copy(alpha = 0.95f))
            }
        }
    }
}

@Composable
private fun Suggestion(text: String?, reason: AdjustReason?, color: Color, onApply: () -> Unit, onDismiss: () -> Unit) {
    if (text == null) return
    GameCard(accent = Palette.Yellow) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Mascot(MascotMood.CHEER, Modifier.size(56.dp), animate = false)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.setup_suggest_title), style = MaterialTheme.typography.titleMedium)
                reason?.label()?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = color)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GameButton(stringResource(R.string.setup_suggest_apply), onApply, color = color, modifier = Modifier.weight(1f).testTag("suggest_apply"))
            TextButton(onDismiss) { Text(stringResource(R.string.action_skip)) }
        }
    }
}

@Composable
private fun AutoSwitch(on: Boolean, set: (Boolean) -> Unit) {
    GameCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.setup_auto), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.setup_auto_text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(on, set)
        }
    }
}

@Composable
private fun IntervalSetup(c: IntervalConfig, vo2: Double?, color: Color, vm: RunProgramViewModel) {
    GameCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberStepper("${c.warmupMin}", stringResource(R.string.setup_warmup), { vm.editInterval { it.copy(warmupMin = it.warmupMin - 1) } }, { vm.editInterval { it.copy(warmupMin = it.warmupMin + 1) } }, color = color)
            NumberStepper(Format.duration(c.workSec.toLong()), stringResource(R.string.setup_work), { vm.editInterval { it.copy(workSec = it.workSec - 15) } }, { vm.editInterval { it.copy(workSec = it.workSec + 15) } }, color = color)
            NumberStepper(Format.duration(c.restSec.toLong()), stringResource(R.string.setup_rest), { vm.editInterval { it.copy(restSec = it.restSec - 15) } }, { vm.editInterval { it.copy(restSec = it.restSec + 15) } }, color = color)
            NumberStepper("${c.reps}", stringResource(R.string.setup_reps), { vm.editInterval { it.copy(reps = it.reps - 1) } }, { vm.editInterval { it.copy(reps = it.reps + 1) } }, color = color, modifier = Modifier.testTag("interval_reps"))
            NumberStepper("${c.cooldownMin}", stringResource(R.string.setup_cooldown), { vm.editInterval { it.copy(cooldownMin = it.cooldownMin - 1) } }, { vm.editInterval { it.copy(cooldownMin = it.cooldownMin + 1) } }, color = color)
        }
    }
    GameCard(accent = color) {
        val pace = vo2?.let { RunPaces.paceSecPerKm(it, RunPaces.Zone.INTERVAL) }
        Text(
            if (pace != null) stringResource(R.string.setup_target_pace, Fmt.pace(pace)) else stringResource(R.string.setup_target_pace_none),
            style = if (pace != null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun CooperSetup(results: List<CooperResult>, color: Color) {
    GameCard(accent = color) {
        Text(stringResource(R.string.setup_cooper_how), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.setup_cooper_steps), style = MaterialTheme.typography.bodyMedium)
    }
    SectionHeader(stringResource(R.string.setup_cooper_last))
    if (results.isEmpty()) {
        Text(stringResource(R.string.setup_cooper_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    results.take(6).forEach { c ->
        GameCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(Fmt.dateMedium(c.run.localDate.toLocalDate()), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Fmt.distance(c.run.distanceM, 2), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("VO2 " + Format.decimal(c.vo2, 1), style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Black)
                    Pill(c.level.label(), c.level.color(), filled = true)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------- running

private data class HudTick(val phaseIndex: Int, val remainingSec: Long, val elapsedSec: Long, val finished: Boolean)

private fun PhaseKind.hudColor(program: Color): Color = when (this) {
    PhaseKind.PREP -> Color(0xFFFFA62B)
    PhaseKind.WARMUP -> Palette.Teal
    PhaseKind.WORK -> program
    PhaseKind.REST -> Palette.Blue
    PhaseKind.COOLDOWN -> Palette.Purple
}

@Composable
private fun PhaseKind.label(): String = stringResource(
    when (this) {
        PhaseKind.PREP -> R.string.timer_phase_prep
        PhaseKind.WARMUP -> R.string.timer_phase_warmup
        PhaseKind.WORK -> R.string.timer_phase_work
        PhaseKind.REST -> R.string.timer_phase_rest
        PhaseKind.COOLDOWN -> R.string.timer_phase_cooldown
    },
)

@Composable
private fun RunningHud(vm: RunProgramViewModel, color: Color, onQuit: () -> Unit) {
    val s by vm.timerState.collectAsStateWithLifecycle()
    val perf by vm.perf.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    when (val t = s) {
        is TimerState.Program -> if (vm.isMineNow(t)) {
            val pace = if (vm.type == RunType.INTERVAL) perf?.current?.let { RunPaces.paceSecPerKm(it, RunPaces.Zone.INTERVAL) } else null
            ProgramHud(t, vm.now, color, pace, vm::togglePause, vm::finish, onQuit)
        } else LaunchedEffect(Unit) { vm.timerLost() }
        is TimerState.Stopwatch -> if (vm.isMineNow(t)) {
            val targetKm = if (vm.type == RunType.LONG) state?.programs?.longRun?.targetKm else null
            StopwatchHud(t, vm.now, color, targetKm, vm::togglePause, vm::finish, onQuit)
        } else LaunchedEffect(Unit) { vm.timerLost() }
        else -> LaunchedEffect(Unit) { vm.timerLost() }
    }
}

@Composable
private fun HudFrame(background: Color, paused: Boolean, onQuit: () -> Unit, onPause: () -> Unit, onFinish: () -> Unit, content: @Composable () -> Unit) {
    val bg by animateColorAsState(background, tween(400), label = "hud")
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(bg, bg.shade(0.7f))))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onQuit) { Icon(Icons.Rounded.Close, stringResource(R.string.hud_quit), tint = Color.White) }
            Spacer(Modifier.weight(1f))
            if (paused) Pill(stringResource(R.string.timer_paused), Color.White, filled = true)
        }
        Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            content()
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundGameButton(
                if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                stringResource(if (paused) R.string.hud_resume else R.string.hud_pause),
                onPause, color = Color.White.copy(alpha = 0.3f), size = 72.dp,
            )
            GameButton(stringResource(R.string.hud_finish), onFinish, color = Color.White, icon = Icons.Rounded.Flag, big = true, modifier = Modifier.weight(1f).testTag("hud_finish"))
        }
    }
}

@Composable
private fun ProgramHud(
    t: TimerState.Program,
    now: StateFlow<Long>,
    color: Color,
    targetPace: Double?,
    onPause: () -> Unit,
    onFinish: () -> Unit,
    onQuit: () -> Unit,
) {
    val nowMs by now.collectAsStateWithLifecycle()
    // Recompose once per displayed second, not on every 250 ms clock tick.
    val tick by remember(t) {
        derivedStateOf {
            val el = t.elapsedMs(nowMs)
            val pos = IntervalProgram.locate(t.phases, el)
            HudTick(pos.phaseIndex, (pos.remainingMs + 999) / 1000, el / 1000, pos.finished || t.finished)
        }
    }
    LaunchedEffect(tick.finished) { if (tick.finished) onFinish() }
    val phase = t.phases[tick.phaseIndex]
    val total = remember(t.phases) { t.phases.sumOf { it.durationSec } }
    HudFrame(phase.kind.hudColor(color), t.pausedAt != null, onQuit, onPause, onFinish) {
        Text(if (tick.finished) stringResource(R.string.hud_done) else phase.kind.label(), color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Black)
        Text(Format.duration(tick.remainingSec), color = Color.White, fontSize = 96.sp, fontWeight = FontWeight.Black, lineHeight = 100.sp, modifier = Modifier.testTag("hud_clock"))
        if (phase.kind == PhaseKind.WORK || phase.kind == PhaseKind.REST) {
            if (phase.totalRounds > 1) Text(stringResource(R.string.hud_rep, phase.round, phase.totalRounds), color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (phase.kind == PhaseKind.WORK && targetPace != null) {
                Text(stringResource(R.string.setup_target_pace, Fmt.pace(targetPace)), color = Color.White.copy(alpha = 0.95f), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            }
        }
        t.phases.getOrNull(tick.phaseIndex + 1)?.let { next ->
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.hud_next, next.kind.label() + " " + Format.duration(next.durationSec.toLong())), color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(24.dp))
        GameProgressBar((tick.elapsedSec.toFloat() / total).coerceIn(0f, 1f), color = Color.White, height = 14.dp)
    }
}

@Composable
private fun StopwatchHud(
    t: TimerState.Stopwatch,
    now: StateFlow<Long>,
    color: Color,
    targetKm: Double?,
    onPause: () -> Unit,
    onFinish: () -> Unit,
    onQuit: () -> Unit,
) {
    val nowMs by now.collectAsStateWithLifecycle()
    val sec by remember(t) { derivedStateOf { t.elapsedMs(nowMs) / 1000 } }
    HudFrame(color, t.pausedAt != null, onQuit, onPause, onFinish) {
        Text(stringResource(R.string.hud_elapsed), color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(Format.duration(sec), color = Color.White, fontSize = 88.sp, fontWeight = FontWeight.Black, lineHeight = 92.sp, modifier = Modifier.testTag("hud_clock"))
        val target = t.targetSec
        if (target != null) {
            Text(
                if (sec >= target) stringResource(R.string.hud_target_done) else stringResource(R.string.hud_target_left, Format.duration(target - sec)),
                color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(16.dp))
            GameProgressBar(sec.toFloat() / target, color = Color.White, height = 14.dp)
        }
        if (targetKm != null) {
            Text(stringResource(R.string.prog_long_sub, Fmt.distance(targetKm * 1000, 1)), color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(16.dp))
        Mascot(MascotMood.CHEER, Modifier.size(120.dp))
    }
}

// ----------------------------------------------------------------------------------------- result

@Composable
private fun ProgramResult(vm: RunProgramViewModel, color: Color, onBack: () -> Unit) {
    val form = vm.form
    val tags by vm.existingTags.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(vm::importImage) }
    Scaffold(
        topBar = { GwTopBar(stringResource(R.string.result_title, vm.type.label()), onBack = onBack) },
        bottomBar = {
            Box(Modifier.navigationBarsPadding().imePadding().padding(16.dp)) {
                GameButton(stringResource(R.string.result_save), { vm.save() }, color = color, icon = Icons.Rounded.Check, big = true, modifier = Modifier.fillMaxWidth().testTag("result_save"))
            }
        },
    ) { pad ->
        if (form == null) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MascotSaysResult(color)
            if (form.runType == RunType.INTERVAL) {
                val reps = state?.programs?.interval?.reps ?: 1
                GameCard {
                    NumberStepper(
                        "${vm.repsDone}/$reps", stringResource(R.string.result_reps_done),
                        { vm.repsDone = (vm.repsDone - 1).coerceAtLeast(0) }, { vm.repsDone = (vm.repsDone + 1).coerceAtMost(reps) }, color = color,
                    )
                }
            }
            OutlinedButton({ picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.fillMaxWidth().height(52.dp), enabled = !vm.reading) {
                if (vm.reading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.PhotoLibrary, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.result_import))
            }
            if (vm.ocrFailed) Text(stringResource(R.string.run_ocr_failed), color = Gw.colors.warning)
            RunFormContent(form, tags)
            Spacer(Modifier.height(16.dp))
        }
    }
    RunDialogs(vm.duplicate, vm.missing, onForce = { vm.duplicate = null; vm.save(force = true) }, onDismissDup = { vm.duplicate = null }, onDismissMissing = { vm.missing = false })
}

@Composable
private fun MascotSaysResult(color: Color) {
    GameCard(accent = color) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Mascot(MascotMood.PROUD, Modifier.size(56.dp), animate = false)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.result_need_distance), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun ProgramSaved(nav: NavHostController, vm: RunProgramViewModel, color: Color) {
    val run = vm.saved ?: return
    val perf by vm.perf.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GameBanner(listOf(color, color.shade(0.75f)), seed = 9) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Mascot(MascotMood.PROUD, Modifier.size(96.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(stringResource(R.string.result_saved), style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Black)
                    Text(Fmt.distance(run.distanceM), fontSize = 40.sp, color = Color.White, fontWeight = FontWeight.Black, lineHeight = 44.sp)
                    if (run.durationSec > 0) {
                        Text(
                            Fmt.duration(run.durationSec) + " · " + Fmt.pace(Pace.secPerKm(run.distanceM, run.durationSec)),
                            style = MaterialTheme.typography.titleMedium, color = Color.White,
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(stringResource(R.string.result_vo2_run), vm.savedVo2?.let { Format.decimal(it, 1) } ?: "-", Modifier.weight(1f), color = Palette.Pink)
            StatTile(stringResource(R.string.result_vo2_now), perf?.current?.let { Format.decimal(it, 1) } ?: "-", Modifier.weight(1f), color = Palette.Purple)
        }
        if (run.runType == RunType.COOPER && vm.savedVo2 != null) {
            val level = Vo2Max.level(vm.savedVo2!!, perf?.profile ?: com.gowaist.core.perf.Profile())
            Pill(level.label(), level.color(), filled = true)
        }
        val s = state
        if (s != null) {
            val next: String? = when (run.runType) {
                RunType.INTERVAL -> (s.pendingInterval ?: s.programs.interval).summary()
                RunType.LONG -> Fmt.distance((s.pendingLongRun ?: s.programs.longRun).targetKm * 1000, 1)
                RunType.COOPER -> stringResource(R.string.prog_cooper_in, RunProgramRules.daysUntilCooper(s.programs.cooper, LocalDate.now()))
                RunType.FREE -> null
            }
            val reason = when (run.runType) {
                RunType.INTERVAL -> s.pendingIntervalReason
                RunType.LONG -> s.pendingLongRunReason
                else -> null
            }
            if (next != null) {
                GameCard(Modifier.fillMaxWidth(), accent = color) {
                    Text(stringResource(R.string.result_next_target, next), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    reason?.label()?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        GameButton(stringResource(R.string.action_done), { nav.popBackStack() }, color = color, big = true, modifier = Modifier.fillMaxWidth().testTag("saved_done"))
        TextButton({ nav.navigate(RunDetailRoute(run.id)) { popUpTo<RunProgramRoute> { inclusive = true } } }) { Text(stringResource(R.string.run_detail_title)) }
    }
}

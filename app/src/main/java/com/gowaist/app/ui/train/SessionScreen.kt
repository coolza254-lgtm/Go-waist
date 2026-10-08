package com.gowaist.app.ui.train

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.gowaist.app.R
import com.gowaist.app.data.db.SetEntity
import com.gowaist.app.data.repo.ExerciseMemory
import com.gowaist.app.data.repo.SessionExerciseView
import com.gowaist.app.data.toEpochMillis
import com.gowaist.app.data.toLocalDateTime
import com.gowaist.app.timer.TimerState
import com.gowaist.app.ui.Fmt
import com.gowaist.app.ui.GwTopBar
import com.gowaist.app.ui.LocalAppSettings
import com.gowaist.app.ui.components.ConfirmDialog
import com.gowaist.app.ui.components.EmptyState
import com.gowaist.app.ui.components.GameButton
import com.gowaist.app.ui.components.GameProgressBar
import com.gowaist.app.ui.components.IconBlob
import com.gowaist.app.ui.components.Mascot
import com.gowaist.app.ui.components.NumberStepper
import com.gowaist.app.ui.components.Pill
import com.gowaist.app.ui.components.color
import com.gowaist.app.ui.components.icon
import com.gowaist.app.ui.components.label
import com.gowaist.app.ui.components.labelRes
import com.gowaist.app.ui.nav.SessionSummaryRoute
import com.gowaist.app.ui.theme.Gw
import com.gowaist.app.ui.theme.NumberStyles
import com.gowaist.core.Format
import com.gowaist.core.Units
import com.gowaist.core.WeightUnit
import com.gowaist.core.mascot.MascotMood
import com.gowaist.core.model.SetType
import com.gowaist.core.model.TrackingType
import kotlinx.coroutines.delay
import java.time.LocalDateTime

@Composable
fun SessionScreen(nav: NavHostController, vm: SessionViewModel = hiltViewModel()) {
    val detail by vm.detail.collectAsStateWithLifecycle()
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val timerState by vm.timer.state.collectAsStateWithLifecycle()
    val now by vm.timer.now.collectAsStateWithLifecycle()
    var picker by remember { mutableStateOf(false) }
    var finishDialog by remember { mutableStateOf(false) }
    var discardDialog by remember { mutableStateOf(false) }
    var infoDialog by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var editSet by remember { mutableStateOf<Pair<SetEntity, TrackingType>?>(null) }
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(vm.closed) { if (vm.closed) nav.popBackStack() }
    LaunchedEffect(vm.summaryFor) {
        vm.summaryFor?.let { id -> nav.navigate(SessionSummaryRoute(id)) { popUpTo<com.gowaist.app.ui.nav.SessionRoute> { inclusive = true } } }
    }
    val d = detail ?: return
    LaunchedEffect(d) { vm.sync(d) }
    val isDraft = d.session.isDraft
    KeepScreenOn(isDraft && settings.keepScreenOn)
    BackHandler(enabled = isDraft) { nav.popBackStack() } // draft stays saved; resume from Home

    val elapsed by produceState(0L, d.session.startAt, isDraft) {
        while (isDraft) {
            value = (System.currentTimeMillis() - d.session.startAt) / 1000
            delay(1000)
        }
    }

    Scaffold(
        topBar = {
            GwTopBar(
                title = d.session.name.ifBlank { stringResource(if (isDraft) R.string.sess_title else R.string.sess_edit_title) },
                onBack = { nav.popBackStack() },
            ) {
                if (isDraft) Text(Fmt.duration(elapsed), style = MaterialTheme.typography.titleMedium, color = Gw.colors.train, modifier = Modifier.padding(end = 4.dp))
                IconButton({ picker = true }) { Icon(Icons.Rounded.Add, stringResource(R.string.sess_add_exercise)) }
                IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.cd_menu)) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text(stringResource(R.string.sess_name) + " / " + stringResource(R.string.sess_date)) }, { menu = false; infoDialog = true }, leadingIcon = { Icon(Icons.Rounded.Edit, null) })
                    DropdownMenuItem(
                        { Text(stringResource(if (isDraft) R.string.sess_discard else R.string.sess_delete), color = Gw.colors.danger) },
                        { menu = false; discardDialog = true }, leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = Gw.colors.danger) },
                    )
                }
            }
        },
        bottomBar = {
            Column(Modifier.navigationBarsPadding()) {
                (timerState as? TimerState.Rest)?.let { RestBar(it, now, onAdjust = { s -> vm.timer.adjustRest(s) }, onSkip = { vm.timer.stop() }) }
                Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    if (isDraft) {
                        GameButton(stringResource(R.string.sess_finish), { finishDialog = true }, color = Gw.colors.success, icon = Icons.Rounded.Flag, modifier = Modifier.fillMaxWidth())
                    } else {
                        GameButton(stringResource(R.string.sess_save_changes), { vm.saveEdits() }, color = Gw.colors.success, icon = Icons.Rounded.Check, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
    ) { pad ->
        if (d.exercises.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(stringResource(R.string.sess_empty), "", mood = MascotMood.CHEER, actionText = stringResource(R.string.sess_add_exercise), onAction = { picker = true })
            }
        } else {
            LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(d.exercises, key = { it.entry.id }) { v ->
                    ExerciseCard(
                        v = v,
                        input = vm.inputs[v.entry.id] ?: SetInput(),
                        memory = vm.memories[v.exercise.id],
                        focused = vm.focused == v.entry.id,
                        isFirst = v == d.exercises.first(),
                        isLast = v == d.exercises.last(),
                        onFocus = { vm.focused = v.entry.id },
                        onInput = { f -> vm.update(v.entry.id, f) },
                        onLog = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); vm.log(v) },
                        onRepeat = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); vm.repeatPrevious(v) },
                        onHold = { vm.startHold(v) },
                        onEditSet = { editSet = it to v.exercise.trackingType },
                        onMove = { vm.move(v, it) },
                        onLink = { vm.linkWithNext(v) },
                        onUnlink = { vm.unlink(v) },
                        onRemove = { vm.remove(v) },
                        onRest = { vm.setRest(v, it) },
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    (timerState as? TimerState.Hold)?.let { HoldOverlay(it, now, onStop = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); vm.stopHold() }) }

    if (picker) {
        ExercisePickerDialog(exercises, LocalAppSettings.current.equipment, multi = true, onDismiss = { picker = false }) { picked ->
            picker = false
            vm.addExercises(picked)
        }
    }
    if (finishDialog) FinishDialog(onDismiss = { finishDialog = false }) { feeling, note -> finishDialog = false; vm.finish(feeling, note) }
    if (discardDialog) {
        ConfirmDialog(
            stringResource(if (isDraft) R.string.sess_discard else R.string.sess_delete), stringResource(R.string.sess_discard_text),
            stringResource(R.string.action_delete), onConfirm = { vm.discard() }, onDismiss = { discardDialog = false }, destructive = true,
        )
    }
    if (infoDialog) SessionInfoDialog(d.session, onDismiss = { infoDialog = false }) { vm.updateSession(it); infoDialog = false }
    editSet?.let { (set, type) ->
        EditSetDialog(set, type, onDismiss = { editSet = null }, onSave = { vm.updateSet(it); editSet = null }, onDelete = { vm.deleteSet(set.id); editSet = null })
    }
}

@Composable
fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
fun setText(s: SetEntity, type: TrackingType): String {
    val reps = stringResource(R.string.unit_reps)
    val sec = stringResource(R.string.unit_sec)
    return when (type) {
        TrackingType.REPS -> "${s.reps ?: 0} $reps"
        TrackingType.HOLD -> Fmt.duration((s.durationSec ?: 0).toLong()).let { if ((s.durationSec ?: 0) < 60) "${s.durationSec ?: 0} $sec" else it }
        TrackingType.WEIGHTED -> "${s.reps ?: 0} $reps" + (s.addedWeightKg?.let { " +" + Fmt.weight(it) } ?: "")
        TrackingType.ASSISTED -> "${s.reps ?: 0} $reps" + (s.assistKg?.let { " (−" + Fmt.weight(it) + ")" } ?: "")
        TrackingType.CARDIO -> listOfNotNull(s.durationSec?.let { Fmt.duration(it.toLong()) }, s.distanceM?.let { Format.decimal(it, 0) + " " + stringResource(R.string.unit_m) }, s.reps?.takeIf { it > 0 }?.let { "$it $reps" }).joinToString(" · ")
    }
}

@Composable
private fun memoryText(m: ExerciseMemory?, type: TrackingType): Pair<String?, String?> {
    if (m == null || m.lastSets.isEmpty()) return null to null
    val last = m.lastSets.filter { it.setType != SetType.WARMUP }.joinToString(", ") { s ->
        when (type) {
            TrackingType.HOLD, TrackingType.CARDIO -> "${s.durationSec ?: 0}s"
            TrackingType.WEIGHTED -> "${s.reps ?: 0}×" + Format.decimal(s.addedWeightKg ?: 0.0, 1)
            else -> "${s.reps ?: 0}"
        }
    }
    val pb = when (type) {
        TrackingType.HOLD -> stringResource(R.string.ex_longest_hold) + " " + m.bests.longestHoldSec + " " + stringResource(R.string.unit_sec)
        TrackingType.WEIGHTED -> stringResource(R.string.ex_e1rm) + " " + Fmt.weight(m.bests.est1RmKg)
        TrackingType.CARDIO -> null
        else -> stringResource(R.string.ex_max_reps) + " " + m.bests.maxReps + " " + stringResource(R.string.unit_reps)
    }
    return last to pb
}

@Composable
private fun ExerciseCard(
    v: SessionExerciseView,
    input: SetInput,
    memory: ExerciseMemory?,
    focused: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onFocus: () -> Unit,
    onInput: ((SetInput) -> SetInput) -> Unit,
    onLog: () -> Unit,
    onRepeat: () -> Unit,
    onHold: () -> Unit,
    onEditSet: (SetEntity) -> Unit,
    onMove: (Int) -> Unit,
    onLink: () -> Unit,
    onUnlink: () -> Unit,
    onRemove: () -> Unit,
    onRest: (Int) -> Unit,
) {
    val ex = v.exercise
    val type = ex.trackingType
    val accent = ex.pattern.color()
    var menu by remember { mutableStateOf(false) }
    var restDialog by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(Gw.radii.m)
    val group = v.entry.supersetGroup
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Gw.colors.card)
            .border(if (focused) 3.dp else 2.dp, if (focused) accent else Gw.colors.cardBorder, shape),
    ) {
        if (group != null) {
            Box(Modifier.fillMaxWidth().background(Gw.colors.plan).padding(horizontal = 14.dp, vertical = 4.dp)) {
                Text(stringResource(R.string.sess_superset, ('A' + (group - 1) % 26).toString()), color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }
        Row(Modifier.fillMaxWidth().clickable(onClick = onFocus).padding(start = 14.dp, top = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBlob(ex.pattern.icon(), accent, size = 40.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(ex.nameTh, style = MaterialTheme.typography.titleMedium)
                val target = v.entry.targetSets?.let { sets ->
                    val unit = if (type == TrackingType.HOLD || type == TrackingType.CARDIO) stringResource(R.string.unit_sec) else stringResource(R.string.unit_reps)
                    stringResource(R.string.sess_target, sets, "${v.entry.targetRepsOrSec ?: "-"} $unit")
                }
                Text(
                    listOfNotNull(target, "${stringResource(R.string.sess_rest)} ${v.entry.restSec}s").joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { restDialog = true },
                )
            }
            Pill("${v.sets.count { it.setType != SetType.WARMUP }}/${v.entry.targetSets ?: "-"}", accent)
            IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.cd_menu)) }
            DropdownMenu(menu, { menu = false }) {
                if (!isFirst) DropdownMenuItem({ Text(stringResource(R.string.sess_move_up)) }, { menu = false; onMove(-1) }, leadingIcon = { Icon(Icons.Rounded.ArrowUpward, null) })
                if (!isLast) DropdownMenuItem({ Text(stringResource(R.string.sess_move_down)) }, { menu = false; onMove(1) }, leadingIcon = { Icon(Icons.Rounded.ArrowDownward, null) })
                if (!isLast) DropdownMenuItem({ Text(stringResource(R.string.sess_link_next)) }, { menu = false; onLink() }, leadingIcon = { Icon(Icons.Rounded.Link, null) })
                if (group != null) DropdownMenuItem({ Text(stringResource(R.string.sess_unlink)) }, { menu = false; onUnlink() }, leadingIcon = { Icon(Icons.Rounded.LinkOff, null) })
                DropdownMenuItem({ Text(stringResource(R.string.sess_rest)) }, { menu = false; restDialog = true }, leadingIcon = { Icon(Icons.Rounded.Timer, null) })
                DropdownMenuItem({ Text(stringResource(R.string.sess_remove), color = Gw.colors.danger) }, { menu = false; onRemove() }, leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = Gw.colors.danger) })
            }
        }
        val (last, pb) = memoryText(memory, type)
        if (last != null || pb != null) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                last?.let { Pill(stringResource(R.string.sess_last, it), Gw.colors.muted) }
                pb?.let { Pill(stringResource(R.string.sess_pb, it), Gw.colors.gold) }
            }
        }
        if (v.sets.isNotEmpty()) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                v.sets.forEachIndexed { i, s ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = 0.08f)).clickable { onEditSet(s) }.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = accent, modifier = Modifier.width(24.dp))
                        Text(setText(s, type), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (s.setType != SetType.NORMAL) Pill(stringResource(s.setType.labelRes()), Gw.colors.warning)
                        s.rpe?.let { Text(" RPE " + Format.decimal(it, 1), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Icon(Icons.Rounded.Check, null, tint = Gw.colors.success, modifier = Modifier.size(18.dp).padding(start = 4.dp))
                    }
                }
            }
        }
        AnimatedVisibility(focused) {
            InputPanel(type, input, accent, hasPrevious = v.sets.isNotEmpty(), onInput = onInput, onLog = onLog, onRepeat = onRepeat, onHold = onHold)
        }
        if (!focused) Spacer(Modifier.height(10.dp))
    }
    if (restDialog) {
        NumberDialog(stringResource(R.string.sess_rest) + " (" + stringResource(R.string.unit_sec) + ")", v.entry.restSec, onDismiss = { restDialog = false }) { onRest(it); restDialog = false }
    }
}

@Composable
private fun InputPanel(
    type: TrackingType,
    input: SetInput,
    accent: Color,
    hasPrevious: Boolean,
    onInput: ((SetInput) -> SetInput) -> Unit,
    onLog: () -> Unit,
    onRepeat: () -> Unit,
    onHold: () -> Unit,
) {
    val wUnit = LocalAppSettings.current.weightUnit
    val weightStep = if (wUnit == WeightUnit.KG) 1.0 else Units.toKg(WeightUnit.LB, 2.5)
    var rirMode by remember { mutableStateOf(input.rir != null) }
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (type) {
            TrackingType.REPS, TrackingType.WEIGHTED, TrackingType.ASSISTED -> {
                NumberStepper("${input.reps}", stringResource(R.string.sess_reps), { onInput { it.copy(reps = (it.reps - 1).coerceAtLeast(0)) } }, { onInput { it.copy(reps = it.reps + 1) } }, color = accent)
            }
            TrackingType.HOLD, TrackingType.CARDIO -> {
                NumberStepper("${input.sec}", stringResource(R.string.sess_seconds), { onInput { it.copy(sec = (it.sec - 5).coerceAtLeast(0)) } }, { onInput { it.copy(sec = it.sec + 5) } }, color = accent)
            }
        }
        if (type == TrackingType.WEIGHTED) {
            NumberStepper(
                Format.decimal(Units.kgTo(wUnit, input.addedKg), 1), stringResource(R.string.sess_added_weight) + " (" + wUnit.label() + ")",
                { onInput { it.copy(addedKg = (it.addedKg - weightStep).coerceAtLeast(0.0)) } }, { onInput { it.copy(addedKg = it.addedKg + weightStep) } }, color = Gw.colors.warning,
            )
        }
        if (type == TrackingType.ASSISTED) {
            NumberStepper(
                Format.decimal(Units.kgTo(wUnit, input.assistKg), 1), stringResource(R.string.sess_assist) + " (" + wUnit.label() + ")",
                { onInput { it.copy(assistKg = (it.assistKg - weightStep).coerceAtLeast(0.0)) } }, { onInput { it.copy(assistKg = it.assistKg + weightStep) } }, color = Gw.colors.goal,
            )
        }
        if (type == TrackingType.CARDIO) {
            NumberStepper("${input.distanceM}", stringResource(R.string.sess_distance_m), { onInput { it.copy(distanceM = (it.distanceM - 10).coerceAtLeast(0)) } }, { onInput { it.copy(distanceM = it.distanceM + 10) } }, color = Gw.colors.run)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(if (rirMode) R.string.sess_rir else R.string.sess_rpe), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            TextButton({ rirMode = !rirMode; onInput { it.copy(rpe = null, rir = null) } }) { Text(if (rirMode) "RPE" else "RIR") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (rirMode) {
                listOf(4, 3, 2, 1, 0).forEach { r ->
                    val on = input.rir == r
                    FilterChip(on, { onInput { s -> if (on) s.copy(rir = null, rpe = null) else s.copy(rir = r, rpe = (10 - r).toDouble()) } }, label = { Text("$r") }, modifier = Modifier.weight(1f))
                }
            } else {
                listOf(6.0, 7.0, 8.0, 9.0, 10.0).forEach { r ->
                    val on = input.rpe == r
                    FilterChip(on, { onInput { s -> s.copy(rpe = if (on) null else r, rir = null) } }, label = { Text(Format.decimal(r, 0)) }, modifier = Modifier.weight(1f))
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SetType.entries.forEach { t ->
                FilterChip(input.setType == t, { onInput { it.copy(setType = t) } }, label = { Text(stringResource(t.labelRes())) })
            }
        }
        if (type == TrackingType.HOLD) {
            GameButton(stringResource(R.string.sess_hold_start), onHold, color = Gw.colors.warning, icon = Icons.Rounded.Timer, modifier = Modifier.fillMaxWidth())
        }
        GameButton(stringResource(R.string.sess_log_set), onLog, color = accent, icon = Icons.Rounded.Check, big = true, modifier = Modifier.fillMaxWidth())
        if (hasPrevious) {
            GameButton(stringResource(R.string.sess_repeat), onRepeat, color = Gw.colors.muted, icon = Icons.Rounded.Replay, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun RestBar(state: TimerState.Rest, now: Long, onAdjust: (Int) -> Unit, onSkip: () -> Unit) {
    val rem = state.remainingMs(now)
    Surface(color = Gw.colors.goal, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.sess_rest), color = Color.White, style = MaterialTheme.typography.labelLarge)
                    Text(Fmt.duration((rem + 999) / 1000), color = Color.White, style = NumberStyles.big)
                }
                TextButton({ onAdjust(-15) }) { Text("−15", color = Color.White, style = MaterialTheme.typography.titleMedium) }
                TextButton({ onAdjust(15) }) { Text("+15", color = Color.White, style = MaterialTheme.typography.titleMedium) }
                IconButton(onSkip) { Icon(Icons.Rounded.SkipNext, stringResource(R.string.sess_rest_skip), tint = Color.White) }
            }
            GameProgressBar(1f - rem / (state.totalSec * 1000f), color = Color.White.copy(alpha = 0.9f), height = 10.dp)
            Text(stringResource(R.string.timer_rest_next, state.label), color = Color.White, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun HoldOverlay(state: TimerState.Hold, now: Long, onStop: () -> Unit) {
    val sec = state.elapsedMs(now) / 1000
    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Column(
            Modifier.fillMaxSize().background(Gw.colors.warning).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Mascot(MascotMood.CHEER, Modifier.size(160.dp))
            Text(state.label, style = MaterialTheme.typography.titleLarge, color = Color.White, textAlign = TextAlign.Center)
            Text(stringResource(R.string.sess_holding), style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Text("$sec", style = NumberStyles.huge.copy(fontSize = androidx.compose.ui.unit.TextUnit(120f, androidx.compose.ui.unit.TextUnitType.Sp)), color = Color.White)
            state.targetSec?.let { t ->
                GameProgressBar(sec / t.toFloat(), color = Color.White, modifier = Modifier.padding(vertical = 12.dp))
                Text(stringResource(R.string.sess_target, 1, "$t ${stringResource(R.string.unit_sec)}"), color = Color.White)
            }
            Spacer(Modifier.height(32.dp))
            GameButton(stringResource(R.string.sess_hold_stop), onStop, color = Gw.colors.danger, icon = Icons.Rounded.Stop, big = true, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun FinishDialog(onDismiss: () -> Unit, onFinish: (Int?, String) -> Unit) {
    var feeling by remember { mutableStateOf<Int?>(null) }
    var note by remember { mutableStateOf("") }
    val names = stringArrayResource(R.array.feelings)
    val emojis = listOf("😫", "😓", "🙂", "😄", "🤩")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sess_finish_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.sess_feeling))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    emojis.forEachIndexed { i, e ->
                        Column(
                            Modifier.clip(RoundedCornerShape(12.dp)).background(if (feeling == i + 1) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable { feeling = i + 1 }.padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(e, style = MaterialTheme.typography.headlineMedium)
                            Text(names[i], style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                OutlinedTextField(note, { note = it.take(500) }, label = { Text(stringResource(R.string.sess_finish_note)) }, minLines = 2)
            }
        },
        confirmButton = { TextButton({ onFinish(feeling, note) }) { Text(stringResource(R.string.sess_finish), fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun EditSetDialog(set: SetEntity, type: TrackingType, onDismiss: () -> Unit, onSave: (SetEntity) -> Unit, onDelete: () -> Unit) {
    var reps by remember { mutableStateOf(set.reps?.toString().orEmpty()) }
    var sec by remember { mutableStateOf(set.durationSec?.toString().orEmpty()) }
    var added by remember { mutableStateOf(set.addedWeightKg?.let { Format.decimal(it, 2) }.orEmpty()) }
    var assist by remember { mutableStateOf(set.assistKg?.let { Format.decimal(it, 2) }.orEmpty()) }
    var dist by remember { mutableStateOf(set.distanceM?.let { Format.decimal(it, 0) }.orEmpty()) }
    var rpe by remember { mutableStateOf(set.rpe?.let { Format.decimal(it, 1) }.orEmpty()) }
    var rir by remember { mutableStateOf(set.rir?.toString().orEmpty()) }
    var setType by remember { mutableStateOf(set.setType) }
    var note by remember { mutableStateOf(set.note) }
    val num = KeyboardOptions(keyboardType = KeyboardType.Number)
    val dec = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sess_edit_set)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (type != TrackingType.HOLD) OutlinedTextField(reps, { reps = it.filter(Char::isDigit).take(4) }, label = { Text(stringResource(R.string.sess_reps)) }, keyboardOptions = num, singleLine = true)
                        if (type == TrackingType.HOLD || type == TrackingType.CARDIO) OutlinedTextField(sec, { sec = it.filter(Char::isDigit).take(5) }, label = { Text(stringResource(R.string.sess_seconds)) }, keyboardOptions = num, singleLine = true)
                        if (type == TrackingType.WEIGHTED) OutlinedTextField(added, { added = it.filter { c -> c.isDigit() || c == '.' }.take(6) }, label = { Text(stringResource(R.string.sess_added_weight) + " (kg)") }, keyboardOptions = dec, singleLine = true)
                        if (type == TrackingType.ASSISTED) OutlinedTextField(assist, { assist = it.filter { c -> c.isDigit() || c == '.' }.take(6) }, label = { Text(stringResource(R.string.sess_assist) + " (kg)") }, keyboardOptions = dec, singleLine = true)
                        if (type == TrackingType.CARDIO) OutlinedTextField(dist, { dist = it.filter(Char::isDigit).take(6) }, label = { Text(stringResource(R.string.sess_distance_m)) }, keyboardOptions = num, singleLine = true)
                        OutlinedTextField(rpe, { rpe = it.filter { c -> c.isDigit() || c == '.' }.take(4) }, label = { Text(stringResource(R.string.sess_rpe)) }, supportingText = { Text(stringResource(R.string.sess_rpe_hint)) }, keyboardOptions = dec, singleLine = true)
                        OutlinedTextField(rir, { rir = it.filter(Char::isDigit).take(2) }, label = { Text(stringResource(R.string.sess_rir)) }, keyboardOptions = num, singleLine = true)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SetType.entries.forEach { t -> FilterChip(setType == t, { setType = t }, label = { Text(stringResource(t.labelRes())) }) }
                        }
                        OutlinedTextField(note, { note = it.take(200) }, label = { Text(stringResource(R.string.sess_note)) })
            }
        },
        confirmButton = {
            TextButton({
                onSave(
                    set.copy(
                        reps = reps.toIntOrNull(), durationSec = sec.toIntOrNull(), addedWeightKg = added.toDoubleOrNull(), assistKg = assist.toDoubleOrNull(),
                        distanceM = dist.toDoubleOrNull(), rpe = rpe.toDoubleOrNull()?.coerceIn(1.0, 10.0), rir = rir.toIntOrNull(), setType = setType, note = note,
                    ),
                )
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            Row {
                TextButton(onDelete) { Text(stringResource(R.string.sess_delete_set), color = Gw.colors.danger) }
                TextButton(onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

@Composable
fun NumberDialog(title: String, initial: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var text by remember { mutableStateOf(initial.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it.filter(Char::isDigit).take(4) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true) },
        confirmButton = { TextButton({ text.toIntOrNull()?.let(onSave) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun SessionInfoDialog(s: com.gowaist.app.data.db.SessionEntity, onDismiss: () -> Unit, onSave: (com.gowaist.app.data.db.SessionEntity) -> Unit) {
    val start = s.startAt.toLocalDateTime()
    var name by remember { mutableStateOf(s.name) }
    var dateText by remember { mutableStateOf(start.toLocalDate().toString()) }
    var timeText by remember { mutableStateOf(String.format(java.util.Locale.US, "%02d:%02d", start.hour, start.minute)) }
    var minutes by remember { mutableStateOf(s.endAt?.let { ((it - s.startAt) / 60000).toString() }.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sess_name) + " / " + stringResource(R.string.sess_date)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it.take(40) }, label = { Text(stringResource(R.string.sess_name)) }, singleLine = true)
                OutlinedTextField(dateText, { dateText = it.take(10) }, label = { Text("YYYY-MM-DD") }, singleLine = true)
                OutlinedTextField(timeText, { timeText = it.take(5) }, label = { Text("HH:MM") }, singleLine = true)
                if (!s.isDraft) OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit).take(4) }, label = { Text(stringResource(R.string.sess_duration_min)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            }
        },
        confirmButton = {
            TextButton({
                val date = runCatching { java.time.LocalDate.parse(dateText.trim()) }.getOrNull() ?: start.toLocalDate()
                val time = runCatching { java.time.LocalTime.parse(timeText.trim()) }.getOrNull() ?: start.toLocalTime()
                val startAt = LocalDateTime.of(date, time).toEpochMillis()
                val end = if (s.isDraft) s.endAt else minutes.toLongOrNull()?.let { startAt + it * 60_000 } ?: s.endAt
                onSave(s.copy(name = name.trim(), startAt = startAt, endAt = end))
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}


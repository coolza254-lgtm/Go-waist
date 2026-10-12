package com.gowaist.app.ui.goals

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.toRoute
import com.gowaist.app.R
import com.gowaist.app.data.db.GoalEntity
import com.gowaist.app.data.key
import com.gowaist.app.data.repo.BodyRepository
import com.gowaist.app.data.repo.GoalRepository
import com.gowaist.app.data.settings.AppSettings
import com.gowaist.app.data.settings.SettingsRepository
import com.gowaist.app.data.toLocalDate
import com.gowaist.app.domain.ActivityCoordinator
import com.gowaist.app.ui.Fmt
import com.gowaist.app.ui.GwTopBar
import com.gowaist.app.ui.LocalAppSettings
import com.gowaist.app.ui.components.ConfirmDialog
import com.gowaist.app.ui.components.EmptyState
import com.gowaist.app.ui.components.GameButton
import com.gowaist.app.ui.components.GameCard
import com.gowaist.app.ui.components.GameProgressBar
import com.gowaist.app.ui.components.Pill
import com.gowaist.app.ui.components.SectionHeader
import com.gowaist.app.ui.components.label
import com.gowaist.app.ui.components.labelRes
import com.gowaist.app.ui.nav.GoalEditRoute
import com.gowaist.app.ui.theme.Gw
import com.gowaist.core.Format
import com.gowaist.core.Units
import com.gowaist.core.goals.GoalProgress
import com.gowaist.core.model.GoalPeriod
import com.gowaist.core.model.GoalStatus
import com.gowaist.core.model.GoalType
import com.gowaist.core.stats.weekStart
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import javax.inject.Inject

import com.gowaist.app.data.db.RunEntity
import com.gowaist.app.data.repo.RunRepository
import com.gowaist.app.data.toFacts
import com.gowaist.app.ui.components.StatBlock
import com.gowaist.app.ui.home.goalValue
import com.gowaist.core.stats.RunStats

/** Goal types the running tracker supports. */
val RUN_GOALS = listOf(GoalType.RUN_DISTANCE, GoalType.RUN_COUNT, GoalType.RUN_5K_TIME, GoalType.STREAK_DAYS, GoalType.BODY_WEIGHT, GoalType.WAIST)

@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val goals: GoalRepository,
    runs: RunRepository,
    private val coordinator: ActivityCoordinator,
) : ViewModel() {
    val list = goals.goals.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val runs = runs.runs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    var live by mutableStateOf<Map<Long, GoalProgress>>(emptyMap())
        private set

    fun refresh() = viewModelScope.launch {
        coordinator.afterChange()
        live = goals.liveProgress()
    }

    fun delete(id: Long) = viewModelScope.launch { goals.delete(id) }
}

@Composable
fun GoalsScreen(nav: NavHostController, vm: GoalsViewModel = hiltViewModel()) {
    val goals by vm.list.collectAsStateWithLifecycle()
    val runs by vm.runs.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }
    val shown = goals.filter { it.type in RUN_GOALS }
    val active = shown.filter { it.status == GoalStatus.ACTIVE }
    val past = shown.filter { it.status != GoalStatus.ACTIVE }
    Scaffold(
        topBar = { GwTopBar(stringResource(R.string.goals_title), onBack = { nav.popBackStack() }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                modifier = Modifier.testTag("fab_goal"), onClick = { nav.navigate(GoalEditRoute()) },
                icon = { Icon(Icons.Rounded.Add, null) }, text = { Text(stringResource(R.string.goals_add)) },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item(key = "pb_title") { SectionHeader(stringResource(R.string.run_pbs)) }
            item(key = "pb") { PersonalBests(runs) }
            item(key = "active_title") { SectionHeader(stringResource(R.string.goals_active)) }
            if (active.isEmpty()) {
                item(key = "empty") { Text(stringResource(R.string.goals_empty_text), color = Gw.colors.muted) }
            }
            items(active, key = { "a" + it.id }) { g -> GoalCard(g, vm.live[g.id], onEdit = { nav.navigate(GoalEditRoute(g.id)) }, onDelete = { vm.delete(g.id) }) }
            if (past.isNotEmpty()) {
                item(key = "past_title") { SectionHeader(stringResource(R.string.goals_done)) }
                items(past, key = { "p" + it.id }) { g -> GoalCard(g, vm.live[g.id], onEdit = null, onDelete = { vm.delete(g.id) }) }
            }
        }
    }
}

@Composable
private fun PersonalBests(runs: List<RunEntity>) {
    val facts = remember(runs) { runs.map { it.toFacts() } }
    GameCard(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("1K" to 1000.0, "5K" to 5000.0, "10K" to 10000.0, "21K" to 21097.5).forEach { (label, d) ->
                val best = remember(facts) { RunStats.bestTimeFor(facts, d) }
                StatBlock(label, best?.let { Fmt.duration(it.second) } ?: "-", Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val longest = remember(facts) { RunStats.longest(facts) }
            val fastest = remember(facts) { RunStats.fastestPace(facts) }
            StatBlock(stringResource(R.string.run_longest), longest?.let { Fmt.distance(it.distanceM, 2) } ?: "-", Modifier.weight(1f))
            StatBlock(stringResource(R.string.run_fastest), fastest?.let { Fmt.pace(it.second) } ?: "-", Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.run_pb_note), style = MaterialTheme.typography.bodySmall, color = Gw.colors.muted)
    }
}

@Composable
private fun GoalCard(goal: GoalEntity, live: GoalProgress?, onEdit: (() -> Unit)?, onDelete: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    val color = when (goal.status) {
        GoalStatus.ACHIEVED -> Gw.colors.success
        GoalStatus.EXPIRED -> Gw.colors.muted
        GoalStatus.ACTIVE -> MaterialTheme.colorScheme.primary
    }
    GameCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(goal.title, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(goal.type.labelRes()), style = MaterialTheme.typography.bodySmall, color = Gw.colors.muted)
            }
            if (onEdit != null) IconButton(onEdit) { Icon(Icons.Rounded.Edit, stringResource(R.string.action_edit)) }
            IconButton({ confirm = true }) { Icon(Icons.Rounded.Delete, stringResource(R.string.goals_delete)) }
        }
        val fraction = if (goal.status == GoalStatus.ACHIEVED) 1.0 else live?.fraction ?: goal.progress
        GameProgressBar(fraction.toFloat(), color = color)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(goalValue(goal, live), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            when (goal.status) {
                GoalStatus.ACTIVE -> {
                    val left = ChronoUnit.DAYS.between(LocalDate.now(), goal.endDate.toLocalDate()).toInt()
                    Pill(if (left <= 0) stringResource(R.string.goals_last_day) else stringResource(R.string.goals_days_left, left), Gw.colors.muted)
                }
                GoalStatus.ACHIEVED -> Pill(stringResource(R.string.goals_achieved), Gw.colors.success, filled = true)
                GoalStatus.EXPIRED -> Pill(stringResource(R.string.goals_expired), Gw.colors.muted)
            }
        }
    }
    if (confirm) ConfirmDialog(stringResource(R.string.goals_delete), stringResource(R.string.goals_delete_text), stringResource(R.string.action_delete), onDelete, { confirm = false }, destructive = true)
}

// ------------------------------------------------------------------------------- edit

@HiltViewModel
class GoalEditViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val goals: GoalRepository,
    private val body: BodyRepository,
    private val settings: SettingsRepository,
    private val coordinator: ActivityCoordinator,
) : ViewModel() {
    val id = handle.toRoute<GoalEditRoute>().id
    private var units = AppSettings()
    var type by mutableStateOf(GoalType.RUN_DISTANCE)
    var target by mutableStateOf("")
    var targetSec by mutableStateOf("")
    var period by mutableStateOf(GoalPeriod.WEEK)
    var customRange by mutableStateOf(LocalDate.now() to LocalDate.now().plusWeeks(4))
    var title by mutableStateOf("")
    var titleEdited by mutableStateOf(false)
    var baseline by mutableStateOf("")
    var invalid by mutableStateOf(false)
    var done by mutableStateOf(false)
    private var existing: GoalEntity? = null

    init {
        viewModelScope.launch {
            units = settings.current()
            if (id != 0L) {
                val g = goals.get(id) ?: return@launch
                existing = g
                type = g.type
                period = g.period
                customRange = g.startDate.toLocalDate() to g.endDate.toLocalDate()
                title = g.title
                titleEdited = true
                setTargetFrom(g.target)
                baseline = g.baseline?.let { Format.decimal(if (g.type == GoalType.BODY_WEIGHT) fromWeight(it) else fromLength(it), 1) }.orEmpty()
            }
        }
    }

    private fun unitDistance(v: Double) = Units.toMeters(units.distanceUnit, v)
    private fun unitWeight(v: Double) = Units.toKg(units.weightUnit, v)
    private fun unitLength(v: Double) = Units.toCm(units.lengthUnit, v)
    private fun fromDistance(v: Double) = Units.metersTo(units.distanceUnit, v)
    private fun fromWeight(v: Double) = Units.kgTo(units.weightUnit, v)
    private fun fromLength(v: Double) = Units.cmTo(units.lengthUnit, v)

    private fun setTargetFrom(v: Double) {
        when (type) {
            GoalType.RUN_DISTANCE -> target = Format.decimal(fromDistance(v), 1)
            GoalType.RUN_5K_TIME -> { target = (v.toLong() / 60).toString(); targetSec = (v.toLong() % 60).toString() }
            GoalType.BODY_WEIGHT -> target = Format.decimal(fromWeight(v), 2)
            GoalType.WAIST -> target = Format.decimal(fromLength(v), 1)
            else -> target = v.toInt().toString()
        }
    }

    suspend fun suggestBaseline() {
        val latest = body.all()
        baseline = when (type) {
            GoalType.BODY_WEIGHT -> latest.firstOrNull { it.weightKg != null }?.weightKg?.let { Format.decimal(fromWeight(it), 2) }.orEmpty()
            GoalType.WAIST -> latest.firstOrNull { it.waistCm != null }?.waistCm?.let { Format.decimal(fromLength(it), 1) }.orEmpty()
            else -> ""
        }
    }

    fun targetValue(): Double? {
        val t = target.replace(',', '.').toDoubleOrNull()
        return when (type) {
            GoalType.RUN_DISTANCE -> t?.let(::unitDistance)
            GoalType.RUN_5K_TIME -> ((target.toIntOrNull() ?: 0) * 60 + (targetSec.toIntOrNull() ?: 0)).toDouble().takeIf { it > 0 }
            GoalType.BODY_WEIGHT -> t?.let(::unitWeight)
            GoalType.WAIST -> t?.let(::unitLength)
            else -> t
        }?.takeIf { it > 0 }
    }

    fun range(): Pair<LocalDate, LocalDate> {
        val today = LocalDate.now()
        return when (period) {
            GoalPeriod.WEEK -> today.weekStart() to today.weekStart().plusDays(6)
            GoalPeriod.MONTH -> today.withDayOfMonth(1) to YearMonth.from(today).atEndOfMonth()
            GoalPeriod.CUSTOM -> customRange
        }
    }

    fun save(autoTitle: String) = viewModelScope.launch {
        val t = targetValue()
        if (t == null) { invalid = true; return@launch }
        val (start, end) = range()
        val base = baseline.replace(',', '.').toDoubleOrNull()?.let { if (type == GoalType.BODY_WEIGHT) unitWeight(it) else unitLength(it) }
        val g = (existing ?: GoalEntity(type = type, title = "", target = t, period = period, startDate = start.key(), endDate = end.key())).copy(
            type = type, title = title.ifBlank { autoTitle }.trim(), target = t,
            period = period, startDate = start.key(), endDate = end.key(),
            baseline = if (type == GoalType.BODY_WEIGHT || type == GoalType.WAIST) base else null,
            status = GoalStatus.ACTIVE, progress = 0.0, achievedAt = null,
        )
        if (existing == null) goals.create(g) else goals.update(g)
        coordinator.afterChange()
        done = true
    }
}

@Composable
fun GoalEditScreen(nav: NavHostController, vm: GoalEditViewModel = hiltViewModel()) {
    val s = LocalAppSettings.current
    var pickRange by remember { mutableStateOf(false) }
    LaunchedEffect(vm.done) { if (vm.done) nav.popBackStack() }
    LaunchedEffect(vm.type) {
        if (vm.id == 0L && (vm.type == GoalType.BODY_WEIGHT || vm.type == GoalType.WAIST)) vm.suggestBaseline()
    }
    val autoTitle = autoTitle(vm)
    Scaffold(
        topBar = { GwTopBar(stringResource(R.string.goal_edit_title), onBack = { nav.popBackStack() }) },
        bottomBar = { Box(Modifier.navigationBarsPadding().imePadding().padding(16.dp)) { GameButton(stringResource(R.string.action_save), { vm.save(autoTitle) }, icon = Icons.Rounded.Check, modifier = Modifier.fillMaxWidth()) } },
    ) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.goal_type), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RUN_GOALS.filter { s.bodyMetricsEnabled || (it != GoalType.BODY_WEIGHT && it != GoalType.WAIST) }.forEach { t ->
                    FilterChip(vm.type == t, { vm.type = t; vm.invalid = false }, label = { Text(stringResource(t.labelRes())) })
                }
            }
            when (vm.type) {
                GoalType.RUN_5K_TIME -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(vm.target, { vm.target = it.filter(Char::isDigit).take(3) }, label = { Text(stringResource(R.string.goal_minutes)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(vm.targetSec, { vm.targetSec = it.filter(Char::isDigit).take(2) }, label = { Text(stringResource(R.string.goal_seconds)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                }
                else -> OutlinedTextField(
                    vm.target, { vm.target = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(7); vm.invalid = false },
                    label = { Text(stringResource(R.string.goal_target)) }, suffix = { Text(targetUnit(vm.type)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.titleLarge,
                )
            }
            if (vm.type == GoalType.BODY_WEIGHT || vm.type == GoalType.WAIST) {
                OutlinedTextField(vm.baseline, { vm.baseline = it.filter { c -> c.isDigit() || c == '.' }.take(6) }, label = { Text(stringResource(R.string.goal_baseline)) }, suffix = { Text(targetUnit(vm.type)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            if (vm.invalid) Text(stringResource(R.string.goal_invalid), color = Gw.colors.danger)
            Text(stringResource(R.string.goal_period), style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                GoalPeriod.entries.forEachIndexed { i, p ->
                    SegmentedButton(vm.period == p, { vm.period = p; if (p == GoalPeriod.CUSTOM) pickRange = true }, SegmentedButtonDefaults.itemShape(i, 3)) { Text(stringResource(p.labelRes())) }
                }
            }
            val (a, b) = vm.range()
            TextButton({ vm.period = GoalPeriod.CUSTOM; pickRange = true }) {
                Icon(Icons.Rounded.DateRange, null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.goal_range, Fmt.dateMedium(a), Fmt.dateMedium(b)))
            }
            OutlinedTextField(vm.title, { vm.title = it.take(60); vm.titleEdited = true }, label = { Text(stringResource(R.string.goal_name)) }, placeholder = { Text(autoTitle) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(24.dp))
        }
    }
    if (pickRange) {
        val state = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { pickRange = false },
            confirmButton = {
                TextButton({
                    val x = state.selectedStartDateMillis
                    val y = state.selectedEndDateMillis ?: x
                    if (x != null && y != null) vm.customRange = Instant.ofEpochMilli(x).atZone(ZoneOffset.UTC).toLocalDate() to Instant.ofEpochMilli(y).atZone(ZoneOffset.UTC).toLocalDate()
                    pickRange = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton({ pickRange = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DateRangePicker(state, Modifier.height(480.dp)) }
    }
}

@Composable
private fun targetUnit(type: GoalType): String {
    val s = LocalAppSettings.current
    return when (type) {
        GoalType.RUN_DISTANCE -> s.distanceUnit.label()
        GoalType.RUN_COUNT -> stringResource(R.string.unit_runs)
        GoalType.STREAK_DAYS -> stringResource(R.string.unit_days)
        GoalType.BODY_WEIGHT -> s.weightUnit.label()
        GoalType.WAIST -> s.lengthUnit.label()
        else -> ""
    }
}

@Composable
private fun autoTitle(vm: GoalEditViewModel): String {
    val n = vm.target.replace(',', '.').toDoubleOrNull()
    val i = n?.toInt() ?: 0
    return when (vm.type) {
        GoalType.RUN_DISTANCE -> stringResource(R.string.goal_auto_run_distance, vm.targetValue()?.let { Fmt.distance(it, 1) } ?: "")
        GoalType.RUN_COUNT -> stringResource(R.string.goal_auto_run_count, i)
        GoalType.RUN_5K_TIME -> stringResource(R.string.goal_auto_5k, vm.targetValue()?.let { Fmt.duration(it.toLong()) } ?: "")
        GoalType.STREAK_DAYS -> stringResource(R.string.goal_auto_streak, i)
        GoalType.BODY_WEIGHT -> stringResource(R.string.goal_auto_weight, vm.targetValue()?.let { Fmt.weight(it) } ?: "")
        GoalType.WAIST -> stringResource(R.string.goal_auto_waist, vm.targetValue()?.let { Fmt.length(it) } ?: "")
        else -> ""
    }
}

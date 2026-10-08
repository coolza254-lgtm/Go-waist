package com.gowaist.app.ui.plan

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.gowaist.app.R
import com.gowaist.app.data.db.PlanDayEntity
import com.gowaist.app.data.db.PlanEntity
import com.gowaist.app.data.db.PlanMode
import com.gowaist.app.data.repo.LibraryRepository
import com.gowaist.app.data.repo.PlanRepository
import com.gowaist.app.data.repo.TemplateView
import com.gowaist.app.data.repo.WorkoutRepository
import com.gowaist.app.data.toLocalDate
import com.gowaist.app.data.toPlanned
import com.gowaist.app.domain.ActivityCoordinator
import com.gowaist.app.domain.SuggestionEngine
import com.gowaist.app.domain.SuggestionItem
import com.gowaist.app.ui.Fmt
import com.gowaist.app.ui.LocalAppSettings
import com.gowaist.app.ui.components.ConfirmDialog
import com.gowaist.app.ui.components.EmptyState
import com.gowaist.app.ui.components.GameButton
import com.gowaist.app.ui.components.GameCard
import com.gowaist.app.ui.components.GameProgressBar
import com.gowaist.app.ui.components.Pill
import com.gowaist.app.ui.components.PlanDayRow
import com.gowaist.app.ui.components.SectionHeader
import com.gowaist.app.ui.components.SuggestionCard
import com.gowaist.app.ui.components.label
import com.gowaist.app.ui.components.labelRes
import com.gowaist.app.ui.nav.PlanCreateRoute
import com.gowaist.app.ui.nav.RunImportRoute
import com.gowaist.app.ui.nav.SessionRoute
import com.gowaist.app.ui.theme.Gw
import com.gowaist.core.Format
import com.gowaist.core.Units
import com.gowaist.core.mascot.MascotMood
import com.gowaist.core.model.PlanDayStatus
import com.gowaist.core.model.PlanDayType
import com.gowaist.core.plan.LoadRules
import com.gowaist.core.plan.LoadWarning
import com.gowaist.core.plan.LoadWarningType
import com.gowaist.core.plan.PlanMatcher
import com.gowaist.core.stats.weekStart
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class PlanViewModel @Inject constructor(
    private val plans: PlanRepository,
    library: LibraryRepository,
    private val workouts: WorkoutRepository,
    private val engine: SuggestionEngine,
    private val coordinator: ActivityCoordinator,
) : ViewModel() {
    val weekOffset = MutableStateFlow(0L)
    val allPlans = plans.allPlans.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val templates = library.templates.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Three weeks around the displayed one, so load rules can see the neighbours. */
    val days: StateFlow<List<PlanDayEntity>> = weekOffset.flatMapLatest { off ->
        val monday = LocalDate.now().weekStart().plusWeeks(off)
        plans.days(monday.minusWeeks(1), monday.plusWeeks(2).minusDays(1))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _suggestions = MutableStateFlow<List<SuggestionItem>>(emptyList())
    val suggestions: StateFlow<List<SuggestionItem>> = _suggestions

    fun refresh() = viewModelScope.launch {
        plans.syncStatuses()
        _suggestions.value = runCatching { engine.open().filterIsInstance<SuggestionItem.PlanChange>() }.getOrDefault(emptyList())
    }

    fun answer(item: SuggestionItem, action: Int) = viewModelScope.launch {
        when (action) {
            0 -> engine.accept(item)
            1 -> engine.reject(item)
            else -> engine.snooze(item)
        }
        refresh()
    }

    fun setActive(p: PlanEntity, active: Boolean) = viewModelScope.launch { plans.setActive(p.id, active); coordinator.afterChange(celebrate = false) }
    fun delete(p: PlanEntity) = viewModelScope.launch { plans.deletePlan(p.id); coordinator.afterChange(celebrate = false) }
    fun shift(p: PlanEntity) = viewModelScope.launch { plans.shiftPlan(p.id, LocalDate.now(), 7) }
    fun saveDay(d: PlanDayEntity) = viewModelScope.launch { plans.updateDay(d); plans.syncStatuses() }
    fun deleteDay(d: PlanDayEntity) = viewModelScope.launch { plans.deleteDay(d.id) }
    fun startBodyweight(d: PlanDayEntity, open: (Long) -> Unit) = viewModelScope.launch { open(workouts.start(templateId = d.templateId, planDayId = d.id)) }
}

@Composable
fun PlanScreen(nav: NavHostController, vm: PlanViewModel = hiltViewModel()) {
    val allPlans by vm.allPlans.collectAsStateWithLifecycle()
    val days by vm.days.collectAsStateWithLifecycle()
    val offset by vm.weekOffset.collectAsStateWithLifecycle()
    val templates by vm.templates.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<PlanDayEntity?>(null) }
    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }
    val today = LocalDate.now()
    val monday = today.weekStart().plusWeeks(offset)
    val sunday = monday.plusDays(6)
    val names = templates.associate { it.template.id to it.template.name }
    val weekDays = days.filter { val d = it.date.toLocalDate(); !d.isBefore(monday) && !d.isAfter(sunday) }
    val warnings = LoadRules.check(days.map { it.toPlanned() }).filter { !it.date.isBefore(monday) && !it.date.isAfter(sunday) }
    val active = allPlans.filter { it.isActive }

    Scaffold(
        modifier = Modifier.statusBarsPadding(),
        floatingActionButton = {
            ExtendedFloatingActionButton(modifier = Modifier.testTag("fab_plan"), onClick = { nav.navigate(PlanCreateRoute) }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text(stringResource(R.string.plan_add)) }, containerColor = Gw.colors.plan, contentColor = MaterialTheme.colorScheme.surface)
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text(stringResource(R.string.plan_title), style = MaterialTheme.typography.headlineMedium) }
            if (allPlans.isEmpty()) {
                item {
                    EmptyState(stringResource(R.string.plan_empty_title), stringResource(R.string.plan_empty_text), mood = MascotMood.CALM, actionText = stringResource(R.string.plan_choose), onAction = { nav.navigate(PlanCreateRoute) })
                }
                return@LazyColumn
            }
            items(allPlans, key = { "p" + it.id }) { p -> PlanHeader(p, vm) }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ vm.weekOffset.value = offset - 1 }) { Icon(Icons.Rounded.ChevronLeft, null) }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.plan_week_range, Fmt.dateShort(monday), Fmt.dateShort(sunday)), style = MaterialTheme.typography.titleMedium)
                        if (offset != 0L) TextButton({ vm.weekOffset.value = 0 }) { Text(stringResource(R.string.action_today)) }
                    }
                    IconButton({ vm.weekOffset.value = offset + 1 }) { Icon(Icons.Rounded.ChevronRight, null) }
                }
            }
            if (active.isNotEmpty()) {
                val work = weekDays.filter { it.type != PlanDayType.REST }
                item {
                    Text(stringResource(R.string.plan_progress, work.count { PlanMatcher.isCompleted(it.status) }, work.size), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    GameProgressBar(if (work.isEmpty()) 0f else work.count { PlanMatcher.isCompleted(it.status) } / work.size.toFloat(), color = Gw.colors.plan, height = 12.dp)
                }
            }
            items(weekDays, key = { "d" + it.id }) { d ->
                PlanDayRow(d, d.templateId?.let { names[it] }, showDate = true, isToday = d.date.toLocalDate() == today, onClick = { editing = d })
            }
            if (warnings.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.plan_warnings)) }
                items(warnings.size) { i -> WarningRow(warnings[i]) }
            }
            if (suggestions.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.sug_section)) }
                items(suggestions, key = { it.key }) { s -> SuggestionCard(s, { vm.answer(s, 0) }, { vm.answer(s, 1) }, { vm.answer(s, 2) }) }
            }
        }
    }
    editing?.let { d ->
        DayEditDialog(
            day = d, templates = templates,
            onDismiss = { editing = null },
            onSave = { vm.saveDay(it); editing = null },
            onDelete = { vm.deleteDay(d); editing = null },
            onStart = {
                editing = null
                if (d.type.isRun) nav.navigate(RunImportRoute) else if (d.type.isBodyweight) vm.startBodyweight(d) { nav.navigate(SessionRoute(it)) }
            },
        )
    }
}

@Composable
private fun PlanHeader(p: PlanEntity, vm: PlanViewModel) {
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val start = p.startDate.toLocalDate()
    val week = ((java.time.temporal.ChronoUnit.WEEKS.between(start, LocalDate.now().weekStart())) + 1).coerceIn(1, p.weeks.toLong()).toInt()
    GameCard(accent = Gw.colors.plan, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(stringResource(R.string.plan_week_of, week, p.weeks), Gw.colors.plan)
                    Pill(stringResource(if (p.mode == PlanMode.CUSTOM) R.string.plan_mode_custom else R.string.plan_mode_prebuilt), Gw.colors.muted)
                    if (p.mode == PlanMode.ADAPTIVE) Pill(stringResource(R.string.plan_adaptive), Gw.colors.success)
                    if (!p.isActive) Pill(stringResource(R.string.plan_paused), Gw.colors.warning)
                    p.targetPaceSecPerKm?.let { Pill(Fmt.pace(it), Gw.colors.run) }
                }
            }
            IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.cd_menu)) }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem({ Text(stringResource(if (p.isActive) R.string.plan_pause else R.string.plan_resume)) }, { menu = false; vm.setActive(p, !p.isActive) })
                if (p.isActive) DropdownMenuItem({ Text(stringResource(R.string.plan_shift)) }, { menu = false; vm.shift(p) })
                DropdownMenuItem({ Text(stringResource(R.string.plan_delete), color = Gw.colors.danger) }, { menu = false; confirm = true })
            }
        }
    }
    if (confirm) ConfirmDialog(stringResource(R.string.plan_delete), stringResource(R.string.plan_delete_text), stringResource(R.string.action_delete), { vm.delete(p) }, { confirm = false }, destructive = true)
}

@Composable
fun WarningRow(w: LoadWarning) {
    val date = Fmt.dateShort(w.date)
    val text = when (w.type) {
        LoadWarningType.HEAVY_LEGS_BEFORE_LONG -> stringResource(R.string.warn_heavy_legs, date)
        LoadWarningType.NO_REST_DAY -> stringResource(R.string.warn_no_rest, date)
        LoadWarningType.LOAD_SPIKE -> stringResource(R.string.warn_load_spike, date, (w.value * 100).roundToInt())
        LoadWarningType.HARD_BACK_TO_BACK -> stringResource(R.string.warn_back_to_back, date)
    }
    Row(verticalAlignment = Alignment.Top) {
        Icon(Icons.Rounded.WarningAmber, null, tint = Gw.colors.warning)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DayEditDialog(
    day: PlanDayEntity,
    templates: List<TemplateView>,
    onDismiss: () -> Unit,
    onSave: (PlanDayEntity) -> Unit,
    onDelete: () -> Unit,
    onStart: () -> Unit,
) {
    val unit = LocalAppSettings.current.distanceUnit
    var type by remember { mutableStateOf(day.type) }
    var distance by remember { mutableStateOf(day.targetDistanceM?.let { Format.decimal(Units.metersTo(unit, it), 2) }.orEmpty()) }
    var minutes by remember { mutableStateOf(day.targetDurationMin?.toString().orEmpty()) }
    var templateId by remember { mutableStateOf(day.templateId) }
    var note by remember { mutableStateOf(day.note) }
    var reminderOff by remember { mutableStateOf(day.reminderOff) }
    var templateMenu by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.day_edit) + " · " + Fmt.dateShort(day.date.toLocalDate())) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.day_type), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PlanDayType.entries.forEach { t -> FilterChip(type == t, { type = t }, label = { Text(stringResource(t.labelRes())) }) }
                }
                if (type.isRun) {
                    OutlinedTextField(distance, { distance = it.filter { c -> c.isDigit() || c == '.' }.take(6) }, label = { Text(stringResource(R.string.day_distance)) }, suffix = { Text(unit.label()) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                }
                if (type != PlanDayType.REST) {
                    OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit).take(3) }, label = { Text(stringResource(R.string.day_duration)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                }
                if (type.isBodyweight) {
                    Box {
                        TextButton({ templateMenu = true }) { Text(stringResource(R.string.day_template) + ": " + (templates.firstOrNull { it.template.id == templateId }?.template?.name ?: stringResource(R.string.day_template_none))) }
                        DropdownMenu(templateMenu, { templateMenu = false }) {
                            DropdownMenuItem({ Text(stringResource(R.string.day_template_none)) }, { templateId = null; templateMenu = false })
                            templates.forEach { t -> DropdownMenuItem({ Text(t.template.name) }, { templateId = t.template.id; templateMenu = false }) }
                        }
                    }
                }
                OutlinedTextField(note, { note = it.take(120) }, label = { Text(stringResource(R.string.day_note)) })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.day_reminder_off), Modifier.weight(1f))
                    Switch(reminderOff, { reminderOff = it })
                }
                if (type != PlanDayType.REST && day.status != PlanDayStatus.DONE && !day.date.toLocalDate().isAfter(LocalDate.now())) {
                    GameButton(stringResource(R.string.day_start), onStart, color = if (type.isRun) Gw.colors.run else Gw.colors.train, modifier = Modifier.fillMaxWidth())
                }
                TextButton(onDelete) { Text(stringResource(R.string.day_delete), color = Gw.colors.danger) }
            }
        },
        confirmButton = {
            TextButton({
                onSave(
                    day.copy(
                        type = type,
                        targetDistanceM = if (type.isRun) distance.toDoubleOrNull()?.let { Units.toMeters(unit, it) } else null,
                        targetDurationMin = minutes.toIntOrNull(),
                        templateId = if (type.isBodyweight) templateId else null,
                        note = note.trim(),
                        reminderOff = reminderOff,
                        status = if (type != day.type) PlanDayStatus.PENDING else day.status,
                    ),
                )
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

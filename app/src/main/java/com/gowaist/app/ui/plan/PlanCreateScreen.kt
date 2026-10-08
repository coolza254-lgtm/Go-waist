package com.gowaist.app.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.gowaist.app.R
import com.gowaist.app.data.repo.CustomDaySpec
import com.gowaist.app.data.repo.LibraryRepository
import com.gowaist.app.data.repo.PlanRepository
import com.gowaist.app.domain.ActivityCoordinator
import com.gowaist.app.ui.GwTopBar
import com.gowaist.app.ui.LocalAppSettings
import com.gowaist.app.ui.components.GameButton
import com.gowaist.app.ui.components.GameCard
import com.gowaist.app.ui.components.IconBlob
import com.gowaist.app.ui.components.NumberStepper
import com.gowaist.app.ui.components.Pill
import com.gowaist.app.ui.components.color
import com.gowaist.app.ui.components.icon
import com.gowaist.app.ui.components.label
import com.gowaist.app.ui.components.labelRes
import com.gowaist.app.ui.theme.Gw
import com.gowaist.core.Units
import com.gowaist.core.model.PlanDayType
import com.gowaist.core.plan.LoadRules
import com.gowaist.core.plan.PlannedDay
import com.gowaist.core.seed.PlanDef
import com.gowaist.core.seed.PlanSeed
import com.gowaist.core.stats.weekStart
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class PlanCreateViewModel @Inject constructor(
    private val plans: PlanRepository,
    library: LibraryRepository,
    private val coordinator: ActivityCoordinator,
) : ViewModel() {
    val templates = library.templates.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    var done by mutableStateOf(false)
    var nextWeek by mutableStateOf(false)
    var adaptive by mutableStateOf(true)
    var name by mutableStateOf("")
    var weeks by mutableStateOf(8)
    var pattern by mutableStateOf(
        listOf(
            CustomDaySpec(PlanDayType.RUN_EASY, 3.0), CustomDaySpec(PlanDayType.BW_FULL), CustomDaySpec(PlanDayType.REST),
            CustomDaySpec(PlanDayType.RUN_INTERVAL, 3.0), CustomDaySpec(PlanDayType.BW_CORE), CustomDaySpec(PlanDayType.REST),
            CustomDaySpec(PlanDayType.RUN_LONG, 5.0),
        ),
    )

    private fun startDate(): LocalDate = if (nextWeek) LocalDate.now().weekStart().plusWeeks(1) else LocalDate.now()

    fun usePrebuilt(def: PlanDef) = viewModelScope.launch {
        plans.createFromSeed(def.key, startDate(), adaptive)
        coordinator.afterChange(celebrate = false)
        done = true
    }

    fun createCustom(defaultName: String, toKm: (Double) -> Double) = viewModelScope.launch {
        plans.createCustom(name.ifBlank { defaultName }, pattern.map { it.copy(distanceKm = it.distanceKm?.let(toKm)) }, weeks, startDate(), adaptive)
        coordinator.afterChange(celebrate = false)
        done = true
    }
}

@Composable
fun PlanCreateScreen(nav: NavHostController, vm: PlanCreateViewModel = hiltViewModel()) {
    var tab by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(vm.done) { if (vm.done) nav.popBackStack() }
    Scaffold(topBar = { GwTopBar(stringResource(R.string.create_title), onBack = { nav.popBackStack() }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                Tab(tab == 0, { tab = 0 }, text = { Text(stringResource(R.string.create_tab_prebuilt)) })
                Tab(tab == 1, { tab = 1 }, text = { Text(stringResource(R.string.create_tab_custom)) })
            }
            if (tab == 0) Prebuilt(vm) else Custom(vm)
        }
    }
}

@Composable
private fun CommonOptions(vm: PlanCreateViewModel) {
    Text(stringResource(R.string.create_start), style = MaterialTheme.typography.labelLarge)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        SegmentedButton(!vm.nextWeek, { vm.nextWeek = false }, SegmentedButtonDefaults.itemShape(0, 2)) { Text(stringResource(R.string.create_this_week)) }
        SegmentedButton(vm.nextWeek, { vm.nextWeek = true }, SegmentedButtonDefaults.itemShape(1, 2)) { Text(stringResource(R.string.create_next_week)) }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.create_adaptive), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.create_adaptive_text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(vm.adaptive, { vm.adaptive = it })
    }
}

@Composable
private fun weekSummary(days: List<PlanDayType>): String =
    stringResource(R.string.create_runs_per_week, days.count { it.isRun }, days.count { it.isBodyweight }, days.count { it == PlanDayType.REST })

@Composable
private fun Prebuilt(vm: PlanCreateViewModel) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(PlanSeed.all, key = { it.key }) { def ->
            val isSel = selected == def.key
            val firstWeek = def.weeks.first().map { it.type }
            GameCard(accent = if (isSel) Gw.colors.plan else null, onClick = { selected = if (isSel) null else def.key }) {
                Text(def.nameTh, style = MaterialTheme.typography.titleLarge)
                Text(def.descriptionTh, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(stringResource(R.string.create_weeks, def.weeks.size), Gw.colors.plan)
                    Pill(stringResource(R.string.create_level, def.level), Gw.colors.muted)
                }
                Text(weekSummary(firstWeek), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    firstWeek.forEach { t -> IconBlob(t.icon(), t.color(), size = 30.dp) }
                }
                if (isSel) {
                    Spacer(Modifier.height(10.dp))
                    CommonOptions(vm)
                    Spacer(Modifier.height(8.dp))
                    GameButton(stringResource(R.string.create_use), { vm.usePrebuilt(def) }, color = Gw.colors.plan, icon = Icons.Rounded.Check, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun Custom(vm: PlanCreateViewModel) {
    val unit = LocalAppSettings.current.distanceUnit
    val templates by vm.templates.collectAsStateWithLifecycle()
    val weekdays = stringArrayResource(R.array.weekdays_long)
    val defaultName = stringResource(R.string.create_name_default)
    val start = LocalDate.now().weekStart()
    val preview = (0..1).flatMap { w ->
        vm.pattern.mapIndexed { d, s -> PlannedDay(0, start.plusDays(w * 7L + d), s.type, s.distanceKm?.let { Units.toMeters(unit, it) }, s.durationMin) }
    }
    val warnings = LoadRules.check(preview).filter { it.date.isBefore(start.plusDays(7)) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            OutlinedTextField(vm.name, { vm.name = it.take(40) }, label = { Text(stringResource(R.string.create_name)) }, placeholder = { Text(defaultName) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            NumberStepper("${vm.weeks}", stringResource(R.string.create_weeks_label), { vm.weeks = (vm.weeks - 1).coerceAtLeast(1) }, { vm.weeks = (vm.weeks + 1).coerceAtMost(52) }, color = Gw.colors.plan)
        }
        items(7) { d ->
            val spec = vm.pattern[d]
            DaySpecRow(weekdays[d], spec, unit.label(), templates.map { it.template.id to it.template.name }) { new ->
                vm.pattern = vm.pattern.mapIndexed { i, s -> if (i == d) new else s }
            }
        }
        item {
            GameCard(accent = if (warnings.isEmpty()) Gw.colors.success else Gw.colors.warning) {
                Text(stringResource(R.string.create_preview_warn), style = MaterialTheme.typography.titleMedium)
                Text(weekSummary(vm.pattern.map { it.type }), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(6.dp))
                if (warnings.isEmpty()) Text(stringResource(R.string.create_no_warn), color = Gw.colors.success)
                warnings.forEach { WarningRow(it) }
            }
        }
        item { CommonOptions(vm) }
        item {
            GameButton(stringResource(R.string.action_create), { vm.createCustom(defaultName) { Units.toMeters(unit, it) / 1000.0 } }, color = Gw.colors.plan, icon = Icons.Rounded.Check, big = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DaySpecRow(dayName: String, spec: CustomDaySpec, unitLabel: String, templates: List<Pair<Long, String>>, onChange: (CustomDaySpec) -> Unit) {
    var typeMenu by remember { mutableStateOf(false) }
    var templateMenu by remember { mutableStateOf(false) }
    GameCard(contentPadding = PaddingValues(12.dp), accent = spec.type.color()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(dayName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(84.dp))
            Box(Modifier.weight(1f)) {
                OutlinedButton({ typeMenu = true }, Modifier.fillMaxWidth()) {
                    Icon(spec.type.icon(), null, tint = spec.type.color())
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(spec.type.labelRes()), maxLines = 1)
                }
                DropdownMenu(typeMenu, { typeMenu = false }) {
                    PlanDayType.entries.forEach { t ->
                        DropdownMenuItem({ Text(stringResource(t.labelRes())) }, {
                            typeMenu = false
                            onChange(CustomDaySpec(t, if (t.isRun) spec.distanceKm ?: 3.0 else null, spec.durationMin, if (t.isBodyweight) spec.templateId else null))
                        }, leadingIcon = { Icon(t.icon(), null, tint = t.color()) })
                    }
                }
            }
        }
        if (spec.type.isRun) {
            var text by remember(spec.distanceKm) { mutableStateOf(spec.distanceKm?.let { com.gowaist.core.Format.decimal(it, 1) }.orEmpty()) }
            OutlinedTextField(
                text, { t -> text = t.filter { c -> c.isDigit() || c == '.' }.take(5); onChange(spec.copy(distanceKm = text.toDoubleOrNull())) },
                label = { Text(stringResource(R.string.day_distance)) }, suffix = { Text(unitLabel) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
        if (spec.type.isBodyweight) {
            Box(Modifier.padding(top = 6.dp)) {
                OutlinedButton({ templateMenu = true }, Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.day_template) + ": " + (templates.firstOrNull { it.first == spec.templateId }?.second ?: stringResource(R.string.day_template_none)), maxLines = 1)
                }
                DropdownMenu(templateMenu, { templateMenu = false }) {
                    DropdownMenuItem({ Text(stringResource(R.string.day_template_none)) }, { templateMenu = false; onChange(spec.copy(templateId = null)) })
                    templates.forEach { (id, name) -> DropdownMenuItem({ Text(name) }, { templateMenu = false; onChange(spec.copy(templateId = id)) }) }
                }
            }
        }
    }
}

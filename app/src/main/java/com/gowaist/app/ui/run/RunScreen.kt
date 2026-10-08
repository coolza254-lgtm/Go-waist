package com.gowaist.app.ui.run

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.gowaist.app.R
import com.gowaist.app.data.db.RunEntity
import com.gowaist.app.data.repo.RunRepository
import com.gowaist.app.data.toFacts
import com.gowaist.app.data.toLocalDate
import com.gowaist.app.data.toLocalDateTime
import com.gowaist.app.ui.Fmt
import com.gowaist.app.ui.LocalAppSettings
import com.gowaist.app.ui.components.BarChart
import com.gowaist.app.ui.components.ChartPoint
import com.gowaist.app.ui.components.EmptyState
import com.gowaist.app.ui.components.GameCard
import com.gowaist.app.ui.components.IconBlob
import com.gowaist.app.ui.components.LineChart
import com.gowaist.app.ui.components.MonthCalendar
import com.gowaist.app.ui.components.Pill
import com.gowaist.app.ui.components.SectionHeader
import com.gowaist.app.ui.components.StatTile
import com.gowaist.app.ui.components.label
import com.gowaist.app.ui.nav.RunDetailRoute
import com.gowaist.app.ui.nav.RunEditRoute
import com.gowaist.app.ui.nav.RunImportRoute
import com.gowaist.app.ui.theme.Gw
import com.gowaist.core.Pace
import com.gowaist.core.Units
import com.gowaist.core.stats.RunStats
import com.gowaist.core.stats.weekStart
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import javax.inject.Inject

enum class RunRange { ALL, WEEK, MONTH, YEAR, CUSTOM }
enum class StatPeriod { WEEK, MONTH, YEAR }

@HiltViewModel
class RunViewModel @Inject constructor(runs: RunRepository) : ViewModel() {
    val runs = runs.runs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@Composable
fun RunScreen(nav: NavHostController, vm: RunViewModel = hiltViewModel()) {
    val runs by vm.runs.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    var fabMenu by remember { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier.statusBarsPadding(),
        floatingActionButton = {
            Box {
                ExtendedFloatingActionButton(
                    modifier = Modifier.testTag("fab_run"),
                    onClick = { fabMenu = true },
                    icon = { Icon(Icons.Rounded.Add, null) },
                    text = { Text(stringResource(R.string.run_add)) },
                    containerColor = Gw.colors.run,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
                DropdownMenu(fabMenu, { fabMenu = false }) {
                    DropdownMenuItem({ Text(stringResource(R.string.run_import)) }, { fabMenu = false; nav.navigate(RunImportRoute) }, leadingIcon = { Icon(Icons.Rounded.PhotoLibrary, null) })
                    DropdownMenuItem({ Text(stringResource(R.string.run_manual)) }, { fabMenu = false; nav.navigate(RunEditRoute()) }, leadingIcon = { Icon(Icons.Rounded.EditNote, null) })
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Text(stringResource(R.string.run_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
            PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                listOf(R.string.run_tab_history, R.string.run_tab_stats, R.string.run_tab_calendar).forEachIndexed { i, t ->
                    Tab(tab == i, { tab = i }, text = { Text(stringResource(t)) })
                }
            }
            if (runs.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.run_empty_title), message = stringResource(R.string.run_empty_text),
                    actionText = stringResource(R.string.run_import), onAction = { nav.navigate(RunImportRoute) },
                )
                return@Column
            }
            when (tab) {
                0 -> RunHistory(runs) { nav.navigate(RunDetailRoute(it)) }
                1 -> RunStatsTab(runs)
                else -> RunCalendar(runs) { nav.navigate(RunDetailRoute(it)) }
            }
        }
    }
}

@Composable
private fun RunHistory(runs: List<RunEntity>, open: (Long) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var range by rememberSaveable { mutableStateOf(RunRange.ALL) }
    var custom by remember { mutableStateOf<Pair<LocalDate, LocalDate>?>(null) }
    var pickRange by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val filtered = runs.filter { r ->
        val d = r.localDate.toLocalDate()
        val inRange = when (range) {
            RunRange.ALL -> true
            RunRange.WEEK -> !d.isBefore(today.weekStart())
            RunRange.MONTH -> YearMonth.from(d) == YearMonth.from(today)
            RunRange.YEAR -> d.year == today.year
            RunRange.CUSTOM -> custom?.let { (a, b) -> !d.isBefore(a) && !d.isAfter(b) } ?: true
        }
        val q = query.trim()
        inRange && (q.isEmpty() || r.note.contains(q, true) || r.tags.any { it.contains(q, true) })
    }
    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            OutlinedTextField(query, { query = it }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, placeholder = { Text(stringResource(R.string.run_search_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(RunRange.ALL to R.string.run_range_all, RunRange.WEEK to R.string.run_range_week, RunRange.MONTH to R.string.run_range_month, RunRange.YEAR to R.string.run_range_year, RunRange.CUSTOM to R.string.run_range_custom).forEach { (r, l) ->
                    FilterChip(range == r, { range = r; if (r == RunRange.CUSTOM) pickRange = true }, label = {
                        Text(if (r == RunRange.CUSTOM && custom != null) Fmt.dayMonth(custom!!.first) + "–" + Fmt.dayMonth(custom!!.second) else stringResource(l))
                    })
                }
            }
            val t = RunStats.totals(filtered.map { it.toFacts() })
            Text(stringResource(R.string.run_count, t.count) + " · " + Fmt.distance(t.distanceM, 1), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (filtered.isEmpty()) item { Text(stringResource(R.string.run_empty_filter), modifier = Modifier.padding(24.dp)) }
        val byMonth = filtered.groupBy { YearMonth.from(it.localDate.toLocalDate()) }
        byMonth.forEach { (month, list) ->
            item(key = "m$month") { SectionHeader(Fmt.monthYear(month.atDay(1))) }
            items(list, key = { it.id }) { r -> RunRow(r) { open(r.id) } }
        }
    }
    if (pickRange) {
        val state = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { pickRange = false },
            confirmButton = {
                TextButton({
                    val a = state.selectedStartDateMillis
                    val b = state.selectedEndDateMillis ?: a
                    if (a != null && b != null) custom = Instant.ofEpochMilli(a).atZone(ZoneOffset.UTC).toLocalDate() to Instant.ofEpochMilli(b).atZone(ZoneOffset.UTC).toLocalDate()
                    pickRange = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton({ pickRange = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DateRangePicker(state, Modifier.height(480.dp)) }
    }
}

@Composable
fun RunRow(r: RunEntity, onClick: () -> Unit) {
    GameCard(onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBlob(Icons.AutoMirrored.Rounded.DirectionsRun, Gw.colors.run, size = 46.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(Fmt.dateShort(r.localDate.toLocalDate()) + " · " + Fmt.time(r.startAt.toLocalDateTime()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Fmt.distance(r.distanceM), style = MaterialTheme.typography.titleLarge)
                Text(Fmt.duration(r.durationSec) + " · " + Fmt.pace(r.avgPaceSecPerKm ?: Pace.secPerKm(r.distanceM, r.durationSec)), style = MaterialTheme.typography.bodyMedium)
            }
            if (r.sourceImagePath != null) Icon(Icons.Rounded.Image, null, tint = Gw.colors.muted)
        }
        if (r.tags.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { r.tags.forEach { Pill(it, Gw.colors.run) } }
        }
    }
}

@Composable
private fun RunStatsTab(runs: List<RunEntity>) {
    var period by rememberSaveable { mutableStateOf(StatPeriod.WEEK) }
    val unit = LocalAppSettings.current.distanceUnit
    val today = LocalDate.now()
    val facts = runs.map { it.toFacts() }
    val from = when (period) {
        StatPeriod.WEEK -> today.weekStart()
        StatPeriod.MONTH -> today.withDayOfMonth(1)
        StatPeriod.YEAR -> today.withDayOfYear(1)
    }
    val inPeriod = RunStats.inRange(facts, from, today)
    val t = RunStats.totals(inPeriod)
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(StatPeriod.WEEK to R.string.run_stats_week, StatPeriod.MONTH to R.string.run_stats_month, StatPeriod.YEAR to R.string.run_stats_year).forEachIndexed { i, (p, l) ->
                SegmentedButton(period == p, { period = p }, SegmentedButtonDefaults.itemShape(i, 3)) { Text(stringResource(l)) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(stringResource(R.string.run_total_distance), Fmt.distanceValue(t.distanceM, 1), Modifier.weight(1f), unit = unit.label(), icon = Icons.Rounded.Straighten, color = Gw.colors.run)
            StatTile(stringResource(R.string.run_total_runs), "${t.count}", Modifier.weight(1f), unit = stringResource(R.string.unit_sessions), icon = Icons.AutoMirrored.Rounded.DirectionsRun, color = Gw.colors.plan)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(stringResource(R.string.run_avg_pace), Fmt.paceValue(t.avgPaceSecPerKm), Modifier.weight(1f), icon = Icons.Rounded.Speed, color = Gw.colors.goal)
            StatTile(stringResource(R.string.run_total_time), Fmt.duration(t.durationSec), Modifier.weight(1f), icon = Icons.Rounded.Timer, color = Gw.colors.train)
        }
        val longest = RunStats.longest(inPeriod)
        val fastest = RunStats.fastestPace(facts)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(stringResource(R.string.run_longest), longest?.let { Fmt.distanceValue(it.distanceM, 2) } ?: "-", Modifier.weight(1f), unit = unit.label(), color = Gw.colors.warning)
            StatTile(stringResource(R.string.run_fastest), fastest?.let { Fmt.paceValue(it.second) } ?: "-", Modifier.weight(1f), color = Gw.colors.danger)
        }

        SectionHeader(stringResource(R.string.run_pbs))
        Text(stringResource(R.string.run_pb_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("1K" to 1000.0, "5K" to 5000.0, "10K" to 10000.0, "21K" to 21097.5).forEach { (label, d) ->
                val best = RunStats.bestTimeFor(facts, d)
                StatTile(label, best?.let { Fmt.duration(it.second) } ?: "-", Modifier.weight(1f), color = Gw.colors.gold)
            }
        }

        GameCard {
            Text(stringResource(R.string.run_weekly_chart), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            val weekly = RunStats.weeklyDistance(facts, today, 12)
            BarChart(weekly.map { (ws, m) -> ChartPoint(Fmt.dayMonth(ws), Units.metersTo(unit, m).toFloat()) }, color = Gw.colors.run, description = stringResource(R.string.run_weekly_chart))
        }
        val recent = runs.sortedBy { it.startAt }.takeLast(20).filter { it.distanceM >= 500 }
        if (recent.size >= 2) {
            GameCard {
                Text(stringResource(R.string.run_pace_chart), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                LineChart(
                    recent.map { ChartPoint(Fmt.dayMonth(it.localDate.toLocalDate()), Pace.secPerUnit(it.avgPaceSecPerKm ?: Pace.secPerKm(it.distanceM, it.durationSec) ?: 0.0, unit).toFloat()) },
                    color = Gw.colors.run, invert = true, yLabel = { com.gowaist.core.Format.pace(it.toDouble()) },
                    description = stringResource(R.string.run_pace_chart),
                )
            }
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun RunCalendar(runs: List<RunEntity>, open: (Long) -> Unit) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf<LocalDate?>(LocalDate.now()) }
    val byDate = runs.groupBy { it.localDate.toLocalDate() }
    val runColor = Gw.colors.run
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton({ month = month.minusMonths(1) }) { Icon(Icons.Rounded.ChevronLeft, null) }
            Text(Fmt.monthYear(month.atDay(1)), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            IconButton({ month = month.plusMonths(1) }) { Icon(Icons.Rounded.ChevronRight, null) }
        }
        val monthRuns = runs.filter { YearMonth.from(it.localDate.toLocalDate()) == month }
        Text(
            stringResource(R.string.run_count, monthRuns.size) + " · " + Fmt.distance(monthRuns.sumOf { it.distanceM }, 1),
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        MonthCalendar(
            month = month,
            markers = byDate.mapValues { (_, l) -> l.map { runColor } },
            today = LocalDate.now(),
            selected = selected,
            onSelect = { selected = it },
            dayNames = stringArrayResource(R.array.weekdays_short).toList(),
        )
        Spacer(Modifier.height(12.dp))
        val dayRuns = selected?.let { byDate[it] }.orEmpty()
        if (dayRuns.isEmpty()) Text(stringResource(R.string.run_no_runs_day), color = MaterialTheme.colorScheme.onSurfaceVariant)
        dayRuns.forEach { r -> RunRow(r) { open(r.id) }; Spacer(Modifier.height(8.dp)) }
        Spacer(Modifier.height(80.dp))
    }
}

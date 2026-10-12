package com.gowaist.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.gowaist.app.R
import androidx.compose.ui.graphics.Color
import com.gowaist.app.data.db.BodyMetricEntity
import com.gowaist.app.data.repo.BodyRepository
import com.gowaist.app.data.repo.PerformanceData
import com.gowaist.app.data.repo.ShoeWithStats
import com.gowaist.app.data.toFacts
import com.gowaist.app.ui.components.paceLabel
import com.gowaist.app.ui.nav.BodyRoute
import com.gowaist.app.ui.nav.PerformanceRoute
import com.gowaist.app.ui.perf.color
import com.gowaist.app.ui.perf.label
import com.gowaist.app.ui.shoes.baht
import com.gowaist.core.Units
import com.gowaist.core.perf.RunPaces
import com.gowaist.core.perf.TrainingLoad
import com.gowaist.core.perf.WeightTrend
import com.gowaist.core.stats.RunStats
import com.gowaist.app.data.db.GoalEntity
import com.gowaist.app.data.db.RunEntity
import com.gowaist.app.data.repo.GoalRepository
import com.gowaist.app.data.repo.PerformanceRepository
import com.gowaist.app.data.repo.RunRepository
import com.gowaist.app.data.repo.ShoeRepository
import com.gowaist.app.data.settings.AppSettings
import com.gowaist.app.data.settings.SettingsRepository
import com.gowaist.app.data.toLocalDate
import com.gowaist.app.data.toLocalDateTime
import com.gowaist.app.ui.Fmt
import com.gowaist.app.ui.LocalAppSettings
import com.gowaist.app.ui.switchTab
import com.gowaist.app.ui.components.GameButton
import com.gowaist.app.ui.components.GameCard
import com.gowaist.app.ui.components.GameProgressBar
import com.gowaist.app.ui.components.HpBar
import com.gowaist.app.ui.components.Pill
import com.gowaist.app.ui.components.SectionHeader
import com.gowaist.app.ui.components.ShoeIcon
import com.gowaist.app.ui.components.Sparkline
import com.gowaist.app.ui.components.StatBlock
import com.gowaist.app.ui.components.WeekBars
import com.gowaist.app.ui.components.contentColor
import com.gowaist.app.ui.components.label
import com.gowaist.app.ui.components.labelRes
import com.gowaist.app.ui.nav.GoalsRoute
import com.gowaist.app.ui.nav.RunDetailRoute
import com.gowaist.app.ui.nav.RunEditRoute
import com.gowaist.app.ui.nav.RunImportRoute
import com.gowaist.app.ui.nav.SettingsRoute
import com.gowaist.app.ui.nav.ShoesRoute
import com.gowaist.app.ui.nav.Vo2Route
import com.gowaist.app.ui.perf.ProfileDialog
import com.gowaist.app.ui.theme.Gw
import com.gowaist.app.ui.theme.NumberStyles
import com.gowaist.core.Format
import com.gowaist.core.goals.GoalProgress
import com.gowaist.core.model.GoalStatus
import com.gowaist.core.model.GoalType
import com.gowaist.core.stats.Streaks
import com.gowaist.core.stats.weekStart
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    runs: RunRepository,
    perf: PerformanceRepository,
    shoes: ShoeRepository,
    body: BodyRepository,
    private val goalRepo: GoalRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    val runs = runs.runs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val perf = perf.data.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val shoes = shoes.shoes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val body = body.metrics.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val goals = goalRepo.goals.map { l -> l.filter { it.status == GoalStatus.ACTIVE }.take(3) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    var progress by mutableStateOf<Map<Long, GoalProgress>>(emptyMap())
        private set

    fun refreshProgress() = viewModelScope.launch { progress = goalRepo.liveProgress() }

    fun saveProfile(p: AppSettings) = viewModelScope.launch {
        settings.update { it.copy(age = p.age, sex = p.sex, heightCm = p.heightCm, restHr = p.restHr, maxHr = p.maxHr) }
    }
}

@Composable
fun HomeScreen(nav: NavHostController, vm: HomeViewModel = hiltViewModel()) {
    val runs by vm.runs.collectAsStateWithLifecycle()
    val perf by vm.perf.collectAsStateWithLifecycle()
    val shoes by vm.shoes.collectAsStateWithLifecycle()
    val goals by vm.goals.collectAsStateWithLifecycle()
    val body by vm.body.collectAsStateWithLifecycle()
    val settings = LocalAppSettings.current
    var editProfile by remember { mutableStateOf(false) }
    val list = runs.orEmpty()
    LaunchedEffect(list, goals) { vm.refreshProgress() }
    val today = LocalDate.now()
    val streak = remember(list) { Streaks.current(list.map { it.localDate.toLocalDate() }.toSet(), emptySet(), today) }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Fmt.dateLong(today), style = MaterialTheme.typography.labelMedium, color = Gw.colors.muted)
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            }
            if (streak > 0) Pill(stringResource(R.string.home_streak, streak), MaterialTheme.colorScheme.primary, icon = Icons.Rounded.LocalFireDepartment)
            IconButton({ nav.navigate(SettingsRoute) }) { Icon(Icons.Rounded.Settings, stringResource(R.string.set_title)) }
        }

        if (!settings.profile.isComplete) {
            GameCard(onClick = { editProfile = true }, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Person, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.home_profile_title), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.home_profile_text), style = MaterialTheme.typography.bodySmall, color = Gw.colors.muted)
                    }
                }
            }
        }

        if (runs != null) PerformanceOverview(nav, list, perf, body, shoes, today)

        val last = list.firstOrNull()
        if (last != null) {
            SectionHeader(stringResource(R.string.home_last_run))
            LastRunCard(last, shoes.firstOrNull { it.shoe.id == last.shoeId }?.shoe?.name) { nav.navigate(RunDetailRoute(last.id)) }
        }

        GameButton(
            stringResource(R.string.home_import), { nav.navigate(RunImportRoute) }, icon = Icons.Rounded.Download, big = true,
            modifier = Modifier.fillMaxWidth().testTag("home_import"),
        )
        TextButton({ nav.navigate(RunEditRoute()) }, Modifier.fillMaxWidth().testTag("home_manual")) { Text(stringResource(R.string.run_manual)) }
    }
    if (editProfile) {
        ProfileDialog(settings, onSave = { vm.saveProfile(it); editProfile = false }, onDismiss = { editProfile = false })
    }
}

@Composable
private fun LastRunCard(r: RunEntity, shoe: String?, onClick: () -> Unit) {
    GameCard(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag("home_last_run")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.home_last_run) + " · " + Fmt.dateShort(r.localDate.toLocalDate()) + " " + Fmt.time(r.startAt.toLocalDateTime()),
                style = MaterialTheme.typography.labelMedium, color = Gw.colors.muted, modifier = Modifier.weight(1f),
            )
            if (shoe != null) Pill(shoe, Gw.colors.ink, filled = true)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(Fmt.distanceValue(r.distanceM), style = NumberStyles.huge)
            Text(" " + LocalAppSettings.current.distanceUnit.label(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatBlock(stringResource(R.string.run_duration), if (r.durationSec > 0) Fmt.duration(r.durationSec) else "-", Modifier.weight(1f))
            StatBlock(stringResource(R.string.run_pace), Fmt.paceValue(r.avgPaceSecPerKm), Modifier.weight(1f))
            StatBlock(stringResource(R.string.run_avg_hr), r.avgHr?.toString() ?: "-", Modifier.weight(1f), unit = if (r.avgHr != null) "bpm" else null)
        }
    }
}

@Composable
private fun GoalLine(g: GoalEntity, p: GoalProgress?) {
    val frac = (p?.fraction ?: g.progress).toFloat()
    Row {
        Text(g.title.ifBlank { stringResource(g.type.labelRes()) }, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text(goalValue(g, p), style = MaterialTheme.typography.labelLarge)
    }
    Spacer(Modifier.height(4.dp))
    GameProgressBar(frac, color = if (frac >= 1f) Gw.colors.goal else MaterialTheme.colorScheme.primary)
}

/** "current / target" text in the user's units. */
@Composable
fun goalValue(g: GoalEntity, p: GoalProgress?): String {
    val cur = p?.current
    return when (g.type) {
        GoalType.RUN_DISTANCE -> Fmt.distanceValue(cur ?: 0.0, 1) + " / " + Fmt.distance(g.target, 0)
        GoalType.RUN_COUNT, GoalType.STREAK_DAYS -> "${(cur ?: 0.0).toInt()} / ${g.target.toInt()}"
        GoalType.RUN_5K_TIME -> (cur?.let { Format.duration(it.toLong()) } ?: "-") + " / " + Format.duration(g.target.toLong())
        GoalType.BODY_WEIGHT -> (cur?.let { Fmt.weight(it) } ?: "-") + " → " + Fmt.weight(g.target)
        GoalType.WAIST -> (cur?.let { Fmt.length(it) } ?: "-") + " → " + Fmt.length(g.target)
        else -> ""
    }
}

/** One labelled number; shows "no data" when [value] is null. */
@Composable
private fun MetricTile(label: String, value: String?, modifier: Modifier = Modifier, unit: String? = null, sub: String? = null, subColor: Color? = null, onClick: (() -> Unit)? = null) {
    GameCard(modifier, onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Gw.colors.muted, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        if (value == null) {
            Text(stringResource(R.string.no_data), style = MaterialTheme.typography.titleSmall, color = Gw.colors.muted, modifier = Modifier.padding(vertical = 4.dp))
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = NumberStyles.medium, maxLines = 1)
                if (unit != null) Text(" $unit", style = MaterialTheme.typography.labelSmall, color = Gw.colors.muted, modifier = Modifier.padding(bottom = 2.dp))
            }
            if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall, color = subColor ?: Gw.colors.muted, maxLines = 1)
        }
    }
}

@Composable
private fun TileRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

/** Current performance in every area, newest data only; empty areas say so. */
@Composable
private fun PerformanceOverview(
    nav: NavHostController,
    runs: List<RunEntity>,
    perf: PerformanceData?,
    body: List<BodyMetricEntity>,
    shoes: List<ShoeWithStats>,
    today: LocalDate,
) {
    val s = LocalAppSettings.current
    val unit = s.distanceUnit.label()
    val recent = remember(runs) { runs.filter { !it.localDate.toLocalDate().isBefore(today.minusDays(29)) } }
    val facts = remember(runs) { runs.map { it.toFacts() } }

    // Fitness
    SectionHeader(stringResource(R.string.home_sec_fitness))
    val vo2 = perf?.current
    val load = perf?.load?.lastOrNull()?.takeIf { perf.runsById.isNotEmpty() }
    TileRow {
        MetricTile(
            stringResource(R.string.hub_vo2_label), vo2?.let { Format.decimal(it, 1) }, Modifier.weight(1f).testTag("home_vo2"),
            sub = perf?.level?.label(), subColor = perf?.level?.color(), onClick = { nav.navigate(Vo2Route) },
        )
        val state = load?.let { TrainingLoad.state(it) }
        MetricTile(
            stringResource(R.string.perf_form), load?.let { (if (it.form >= 0) "+" else "") + Format.decimal(it.form, 0) }, Modifier.weight(1f),
            sub = state?.label(), subColor = state?.color(), onClick = { nav.navigate(PerformanceRoute) },
        )
    }
    TileRow {
        MetricTile(stringResource(R.string.perf_fitness), load?.let { Format.decimal(it.fitness, 0) }, Modifier.weight(1f), onClick = { nav.navigate(PerformanceRoute) })
        MetricTile(stringResource(R.string.perf_fatigue), load?.let { Format.decimal(it.fatigue, 0) }, Modifier.weight(1f), onClick = { nav.navigate(PerformanceRoute) })
    }

    // Speed
    SectionHeader(stringResource(R.string.home_sec_speed))
    val pace30 = remember(recent) {
        val timed = recent.filter { it.durationSec > 0 }
        if (timed.isEmpty()) null else timed.sumOf { it.durationSec } / (timed.sumOf { it.distanceM } / 1000)
    }
    TileRow {
        MetricTile(stringResource(R.string.home_pace_30), pace30?.let { Fmt.paceValue(it) }, Modifier.weight(1f), unit = s.distanceUnit.paceLabel())
        val pb5 = remember(facts) { RunStats.bestTimeFor(facts, 5000.0)?.second }
        MetricTile(stringResource(R.string.home_pb_5k), pb5?.let { Format.duration(it) }, Modifier.weight(1f), onClick = { nav.navigate(GoalsRoute) })
    }
    TileRow {
        MetricTile(stringResource(R.string.home_predict_5k), vo2?.let { Format.duration(RunPaces.predictSeconds(it, 5000.0)) }, Modifier.weight(1f), onClick = { nav.navigate(PerformanceRoute) })
        MetricTile(stringResource(R.string.home_predict_10k), vo2?.let { Format.duration(RunPaces.predictSeconds(it, 10000.0)) }, Modifier.weight(1f), onClick = { nav.navigate(PerformanceRoute) })
    }

    // Volume
    SectionHeader(stringResource(R.string.home_sec_volume))
    val ws = today.weekStart()
    val weekM = remember(runs) { runs.filter { !it.localDate.toLocalDate().isBefore(ws) }.sumOf { it.distanceM } }
    TileRow {
        MetricTile(stringResource(R.string.home_this_week), runs.takeIf { it.isNotEmpty() }?.let { Fmt.distanceValue(weekM, 1) }, Modifier.weight(1f), unit = unit)
        MetricTile(
            stringResource(R.string.home_30_days), recent.takeIf { it.isNotEmpty() }?.let { Fmt.distanceValue(it.sumOf { r -> r.distanceM }, 1) }, Modifier.weight(1f),
            unit = unit, sub = recent.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.run_count, it.size) },
        )
    }
    TileRow {
        MetricTile(stringResource(R.string.home_longest_30), recent.maxOfOrNull { it.distanceM }?.let { Fmt.distanceValue(it, 2) }, Modifier.weight(1f), unit = unit)
        val streak = remember(runs) { Streaks.current(runs.map { it.localDate.toLocalDate() }.toSet(), emptySet(), today) }
        MetricTile(stringResource(R.string.home_streak_label), streak.takeIf { it > 0 }?.toString(), Modifier.weight(1f), unit = stringResource(R.string.unit_days))
    }

    // Heart
    SectionHeader(stringResource(R.string.home_sec_heart))
    val hr30 = remember(recent) { recent.mapNotNull { it.avgHr }.takeIf { it.isNotEmpty() }?.average() }
    TileRow {
        MetricTile(stringResource(R.string.home_hr_30), hr30?.let { Format.decimal(it, 0) }, Modifier.weight(1f), unit = "bpm")
        MetricTile(
            stringResource(R.string.home_efficiency), perf?.efficiency?.lastOrNull()?.second?.let { Format.decimal(it, 2) }, Modifier.weight(1f),
            sub = stringResource(R.string.home_efficiency_unit), onClick = { nav.navigate(PerformanceRoute) },
        )
    }

    // Body
    if (s.bodyMetricsEnabled) {
        SectionHeader(stringResource(R.string.home_sec_body))
        val weights = remember(body) { body.mapNotNull { m -> m.weightKg?.let { m.date.toLocalDate() to it } } }
        val ws2 = remember(weights, s.heightCm) { WeightTrend.stats(weights, s.heightCm, null, today) }
        TileRow {
            MetricTile(
                stringResource(R.string.weight_title), ws2?.let { Fmt.weightValue(it.average7) }, Modifier.weight(1f),
                unit = s.weightUnit.label(), sub = ws2?.let { stringResource(R.string.weight_avg7) }, onClick = { nav.navigate(BodyRoute) },
            )
            val cls = ws2?.bmi?.let { WeightTrend.bmiClass(it) }
            MetricTile(stringResource(R.string.weight_bmi), ws2?.bmi?.let { Format.decimal(it, 1) }, Modifier.weight(1f), sub = cls?.label(), subColor = cls?.color(), onClick = { nav.navigate(BodyRoute) })
        }
    }

    // Main shoe
    SectionHeader(stringResource(R.string.home_sec_shoe))
    val main = shoes.firstOrNull { it.shoe.isMain && !it.shoe.retired }
    TileRow {
        MetricTile(
            main?.shoe?.name ?: stringResource(R.string.shoe_main), main?.let { Fmt.distanceValue(it.stats.distanceM, 0) }, Modifier.weight(1f),
            unit = unit, sub = main?.stats?.costPerKm?.let { baht(it, 1) + stringResource(R.string.shoe_per_km_suffix) }, onClick = { nav.switchTab(ShoesRoute) },
        )
        MetricTile(
            stringResource(R.string.shoe_durability), main?.shoe?.durabilityPct?.let { "$it%" }, Modifier.weight(1f),
            sub = main?.kmLeft?.let { stringResource(R.string.shoe_km_left, Fmt.distance(it * 1000, 0)) }, onClick = { nav.switchTab(ShoesRoute) },
        )
    }
}

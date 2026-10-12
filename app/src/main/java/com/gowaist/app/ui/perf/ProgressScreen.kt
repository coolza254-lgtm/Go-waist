package com.gowaist.app.ui.perf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.gowaist.app.data.repo.BodyRepository
import com.gowaist.app.data.repo.PerformanceRepository
import com.gowaist.app.data.repo.RunRepository
import com.gowaist.app.data.toFacts
import com.gowaist.app.data.toLocalDate
import com.gowaist.app.ui.Fmt
import com.gowaist.app.ui.LocalAppSettings
import com.gowaist.app.ui.components.BarChart
import com.gowaist.app.ui.components.CalendarHeatmap
import com.gowaist.app.ui.components.ChartPoint
import com.gowaist.app.ui.components.GameCard
import com.gowaist.app.ui.components.Pill
import com.gowaist.app.ui.components.SectionHeader
import com.gowaist.app.ui.components.Sparkline
import com.gowaist.app.ui.components.StatBlock
import com.gowaist.app.ui.nav.BodyRoute
import com.gowaist.app.ui.nav.GoalsRoute
import com.gowaist.app.ui.nav.PerformanceRoute
import com.gowaist.app.ui.nav.Vo2Route
import com.gowaist.app.ui.theme.Gw
import com.gowaist.app.ui.theme.NumberStyles
import com.gowaist.core.Format
import com.gowaist.core.Units
import com.gowaist.core.perf.TrainingLoad
import com.gowaist.core.perf.WeightTrend
import com.gowaist.core.stats.RunStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class ProgressViewModel @Inject constructor(perf: PerformanceRepository, body: BodyRepository, runs: RunRepository) : ViewModel() {
    val perf = perf.data.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val body = body.metrics.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val runs = runs.runs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

/** Progress tab: one card per tracking area, each opening its detail screen. */
@Composable
fun ProgressScreen(nav: NavHostController, vm: ProgressViewModel = hiltViewModel()) {
    val perf by vm.perf.collectAsStateWithLifecycle()
    val body by vm.body.collectAsStateWithLifecycle()
    val runs by vm.runs.collectAsStateWithLifecycle()
    val settings = LocalAppSettings.current
    val today = LocalDate.now()
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.nav_progress), style = MaterialTheme.typography.headlineMedium)

        val p = perf
        HubCard(stringResource(R.string.vo2_title), { nav.navigate(Vo2Route) }, Modifier.testTag("progress_vo2")) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(p?.current?.let { Format.decimal(it, 1) } ?: "--", style = NumberStyles.big)
                Text(" " + stringResource(R.string.vo2_unit), style = MaterialTheme.typography.labelMedium, color = Gw.colors.muted, modifier = Modifier.padding(bottom = 4.dp))
                Spacer(Modifier.weight(1f))
                p?.level?.let { Pill(it.label(), it.color(), filled = true) }
            }
            p?.change(30)?.let { Signed(it, style = MaterialTheme.typography.labelMedium) }
            val trend = remember(p) { p?.daily?.takeLast(90)?.map { it.trend.toFloat() }.orEmpty() }
            if (trend.size >= 2) {
                Spacer(Modifier.height(8.dp))
                Sparkline(trend, Gw.colors.goal)
            } else if (p?.current == null) {
                Text(stringResource(R.string.vo2_empty_text), style = MaterialTheme.typography.bodySmall, color = Gw.colors.muted)
            }
        }

        HubCard(stringResource(R.string.perf_title), { nav.navigate(PerformanceRoute) }, Modifier.testTag("progress_perf")) {
            val last = p?.load?.lastOrNull()
            if (last != null && p.runsById.isNotEmpty()) {
                val state = TrainingLoad.state(last)
                Pill(state.label(), state.color(), filled = true)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatBlock(stringResource(R.string.perf_fitness), Format.decimal(last.fitness, 0), Modifier.weight(1f))
                    StatBlock(stringResource(R.string.perf_fatigue), Format.decimal(last.fatigue, 0), Modifier.weight(1f))
                    StatBlock(stringResource(R.string.perf_form), (if (last.form >= 0) "+" else "") + Format.decimal(last.form, 0), Modifier.weight(1f))
                }
            } else {
                Text(stringResource(R.string.perf_no_data), style = MaterialTheme.typography.bodySmall, color = Gw.colors.muted)
            }
        }

        if (settings.bodyMetricsEnabled) {
            val weights = remember(body) { body.mapNotNull { m -> m.weightKg?.let { m.date.toLocalDate() to it } } }
            val stats = remember(weights, settings.heightCm) { WeightTrend.stats(weights, settings.heightCm, null, today) }
            HubCard(stringResource(R.string.weight_title), { nav.navigate(BodyRoute) }, Modifier.testTag("progress_weight")) {
                if (stats != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatBlock(stringResource(R.string.body_latest), Format.decimal(Units.kgTo(settings.weightUnit, stats.latest), 1), Modifier.weight(1f))
                        StatBlock(stringResource(R.string.weight_avg7), Format.decimal(Units.kgTo(settings.weightUnit, stats.average7), 1), Modifier.weight(1f))
                        StatBlock(stringResource(R.string.weight_bmi), stats.bmi?.let { Format.decimal(it, 1) } ?: "-", Modifier.weight(1f))
                    }
                } else {
                    Text(stringResource(R.string.body_empty_text), style = MaterialTheme.typography.bodySmall, color = Gw.colors.muted)
                }
            }
        }

        val facts = remember(runs) { runs.map { it.toFacts() } }
        HubCard(stringResource(R.string.goals_title), { nav.navigate(GoalsRoute) }, Modifier.testTag("progress_goals")) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("5K" to 5000.0, "10K" to 10000.0).forEach { (label, d) ->
                    val best = remember(facts) { RunStats.bestTimeFor(facts, d) }
                    StatBlock(label, best?.let { Fmt.duration(it.second) } ?: "-", Modifier.weight(1f))
                }
                val longest = remember(facts) { RunStats.longest(facts) }
                StatBlock(stringResource(R.string.run_longest), longest?.let { Fmt.distanceValue(it.distanceM, 1) } ?: "-", Modifier.weight(1f))
            }
        }

        if (runs.isNotEmpty()) {
            SectionHeader(stringResource(R.string.progress_consistency))
            GameCard(Modifier.fillMaxWidth()) {
                val byDay = remember(runs) { runs.groupBy { it.localDate.toLocalDate() }.mapValues { (_, l) -> l.sumOf { it.distanceM }.toFloat() } }
                CalendarHeatmap(byDay, weeks = 16, today = today, description = stringResource(R.string.progress_consistency))
            }
            p?.weeklyKm?.let { weekly ->
                GameCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.perf_weekly), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    BarChart(
                        weekly.map { (w, km) -> ChartPoint(Fmt.dayMonth(w), Units.metersTo(settings.distanceUnit, km * 1000).toFloat()) },
                        color = MaterialTheme.colorScheme.primary, description = stringResource(R.string.perf_weekly),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun HubCard(title: String, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    GameCard(modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Gw.colors.muted)
        }
        Spacer(Modifier.height(6.dp))
        content()
    }
}

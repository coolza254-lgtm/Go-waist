package com.gowaist.app.ui.perf

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.gowaist.app.R
import com.gowaist.app.data.repo.PerformanceData
import com.gowaist.app.ui.Fmt
import com.gowaist.app.ui.GwTopBar
import com.gowaist.app.ui.LocalAppSettings
import com.gowaist.app.ui.components.BarChart
import com.gowaist.app.ui.components.ChartPoint
import com.gowaist.app.ui.components.ChartSeries
import com.gowaist.app.ui.components.EmptyState
import com.gowaist.app.ui.components.GameBanner
import com.gowaist.app.ui.components.GameCard
import com.gowaist.app.ui.components.IconBlob
import com.gowaist.app.ui.components.LineChart
import com.gowaist.app.ui.components.MultiLineChart
import com.gowaist.app.ui.components.SectionHeader
import com.gowaist.app.ui.components.StatTile
import com.gowaist.app.ui.run.ProgramColors
import com.gowaist.app.ui.run.icon
import com.gowaist.app.ui.run.label
import com.gowaist.app.ui.theme.Palette
import com.gowaist.app.ui.theme.shade
import com.gowaist.core.Format
import com.gowaist.core.Units
import com.gowaist.core.mascot.MascotMood
import com.gowaist.core.perf.RunPaces
import com.gowaist.core.perf.RunType
import com.gowaist.core.perf.TrainingLoad

@Composable
fun PerformanceScreen(nav: NavHostController, vm: PerfViewModel = hiltViewModel()) {
    val data by vm.data.collectAsStateWithLifecycle()
    Scaffold(topBar = { GwTopBar(stringResource(R.string.perf_title), onBack = { nav.popBackStack() }) }) { pad ->
        val d = data
        when {
            d == null -> Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            d.runsById.isEmpty() && d.load.all { it.load == 0.0 } -> EmptyState(stringResource(R.string.perf_title), stringResource(R.string.perf_no_data), Modifier.padding(pad), mood = MascotMood.CALM)
            else -> PerfContent(d, Modifier.padding(pad))
        }
    }
}

@Composable
private fun PerfContent(d: PerformanceData, modifier: Modifier) {
    val unit = LocalAppSettings.current.distanceUnit
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val last = d.load.lastOrNull()
        if (last != null) {
            val state = TrainingLoad.state(last)
            GameBanner(listOf(state.color(), state.color().shade(0.7f)), seed = 6) {
                Text(state.label(), color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, modifier = Modifier.testTag("perf_state"))
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BannerNumber(stringResource(R.string.perf_fitness), Format.decimal(last.fitness, 0), Modifier.weight(1f))
                    BannerNumber(stringResource(R.string.perf_fatigue), Format.decimal(last.fatigue, 0), Modifier.weight(1f))
                    BannerNumber(stringResource(R.string.perf_form), (if (last.form >= 0) "+" else "") + Format.decimal(last.form, 0), Modifier.weight(1f))
                }
                last.acuteChronic?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.perf_acwr, Format.decimal(it, 2)), color = Color.White.copy(alpha = 0.95f), style = MaterialTheme.typography.labelLarge)
                }
            }
            GameCard {
                Text(stringResource(R.string.perf_load_chart), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                MultiLineChart(
                    listOf(
                        ChartSeries(d.load.map { it.fitness.toFloat() }, Palette.Blue, label = stringResource(R.string.perf_fitness)),
                        ChartSeries(d.load.map { it.fatigue.toFloat() }, Palette.Pink, label = stringResource(R.string.perf_fatigue)),
                        ChartSeries(d.load.map { it.form.toFloat() }, Color(0xFFFFB020), width = 4f, label = stringResource(R.string.perf_form)),
                    ),
                    firstLabel = Fmt.dayMonth(d.load.first().date),
                    lastLabel = Fmt.dayMonth(d.load.last().date),
                    reference = 0f,
                    yLabel = { Format.decimal(it.toDouble(), 0) },
                    description = stringResource(R.string.perf_load_chart),
                )
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.perf_load_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        val vo2 = d.current
        if (vo2 != null) {
            SectionHeader(stringResource(R.string.perf_predictions))
            val races = listOf("5K" to 5000.0, "10K" to 10000.0, "21.1K" to 21097.5, "42.2K" to 42195.0)
            val times = remember(vo2) { races.map { (_, m) -> RunPaces.predictSeconds(vo2, m) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                races.take(2).forEachIndexed { i, (label, m) -> RaceTile(label, times[i], m, Modifier.weight(1f)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                races.drop(2).forEachIndexed { i, (label, m) -> RaceTile(label, times[i + 2], m, Modifier.weight(1f)) }
            }

            SectionHeader(stringResource(R.string.perf_paces))
            GameCard {
                val zones = listOf(
                    RunPaces.Zone.EASY to R.string.pace_easy, RunPaces.Zone.MARATHON to R.string.pace_marathon, RunPaces.Zone.THRESHOLD to R.string.pace_threshold,
                    RunPaces.Zone.INTERVAL to R.string.pace_interval, RunPaces.Zone.REPETITION to R.string.pace_repetition,
                )
                val colors = listOf(Palette.Teal, Palette.Blue, Color(0xFFFFB020), Palette.Orange, Palette.Red)
                zones.forEachIndexed { i, (z, l) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(colors[i]))
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(l), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Text(Fmt.pace(RunPaces.paceSecPerKm(vo2, z)), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    }
                }
            }
        }

        SectionHeader(stringResource(R.string.perf_hr_zones))
        GameCard {
            val hr = RunPaces.hrZones(d.profile)
            val colors = listOf(Color(0xFF9AA5B1), Palette.Blue, Palette.Green, Color(0xFFFFB020), Palette.Red)
            (0 until 5).forEach { i ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.height(26.dp).width((40 + i * 22).dp).clip(RoundedCornerShape(13.dp)).background(colors[i]), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", color = Color.White, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.perf_zone, i + 1), Modifier.weight(1f))
                    Text("${hr[i]}–${hr[i + 1]} " + stringResource(R.string.unit_bpm), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (d.efficiency.size >= 2) {
            GameCard {
                Text(stringResource(R.string.perf_efficiency), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                val pts = d.efficiency.takeLast(20)
                LineChart(pts.map { ChartPoint(Fmt.dayMonth(it.first), it.second.toFloat()) }, color = Palette.Teal, yLabel = { Format.decimal(it.toDouble(), 2) }, description = stringResource(R.string.perf_efficiency))
                Text(stringResource(R.string.perf_efficiency_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        GameCard {
            Text(stringResource(R.string.perf_weekly), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            BarChart(d.weeklyKm.map { (w, km) -> ChartPoint(Fmt.dayMonth(w), Units.metersTo(unit, km * 1000).toFloat()) }, color = Palette.Orange, description = stringResource(R.string.perf_weekly))
        }

        if (d.cooper.isNotEmpty()) {
            GameCard {
                Text(stringResource(R.string.perf_cooper), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                val asc = d.cooper.reversed()
                if (asc.size >= 2) {
                    LineChart(asc.map { ChartPoint(Fmt.dayMonth(java.time.LocalDate.parse(it.run.localDate)), it.vo2.toFloat()) }, color = ProgramColors.cooper, description = stringResource(R.string.perf_cooper))
                }
                d.cooper.take(4).forEach { c ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(Fmt.dateMedium(java.time.LocalDate.parse(c.run.localDate)), Modifier.weight(1f))
                        Text(Fmt.distance(c.run.distanceM, 2) + " · VO2 " + Format.decimal(c.vo2, 1), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        SectionHeader(stringResource(R.string.perf_types))
        RunType.entries.forEach { t ->
            val (count, km) = d.typeCounts[t] ?: (0 to 0.0)
            GameCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBlob(t.icon(), ProgramColors.of(t), size = 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(t.label(), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.perf_type_line, count, Fmt.distance(km * 1000, 1)), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun BannerNumber(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.2f)).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
        Text(label, color = Color.White.copy(alpha = 0.92f), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun RaceTile(label: String, sec: Long, metres: Double, modifier: Modifier) {
    Column(modifier) {
        StatTile(label, Format.duration(sec), color = Palette.Purple)
        Text(Fmt.pace(sec / (metres / 1000)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp, top = 2.dp))
    }
}

package com.gowaist.app.ui.perf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.gowaist.app.R
import com.gowaist.app.data.repo.PerformanceData
import com.gowaist.app.data.repo.PerformanceRepository
import com.gowaist.app.data.settings.AppSettings
import com.gowaist.app.data.settings.SettingsRepository
import com.gowaist.app.ui.Fmt
import com.gowaist.app.ui.GwTopBar
import com.gowaist.app.ui.LocalAppSettings
import com.gowaist.app.ui.components.ArcGauge
import com.gowaist.app.ui.components.ChartSeries
import com.gowaist.app.ui.components.EmptyState
import com.gowaist.app.ui.components.GameBanner
import com.gowaist.app.ui.components.GameButton
import com.gowaist.app.ui.components.GameCard
import com.gowaist.app.ui.components.LevelScale
import com.gowaist.app.ui.components.MultiLineChart
import com.gowaist.app.ui.components.Pill
import com.gowaist.app.ui.components.SectionHeader
import com.gowaist.app.ui.nav.RunDetailRoute
import com.gowaist.app.ui.nav.RunProgramRoute
import com.gowaist.app.ui.theme.Gw
import com.gowaist.app.ui.theme.Palette
import com.gowaist.core.Format
import com.gowaist.core.mascot.MascotMood
import com.gowaist.core.perf.Profile
import com.gowaist.core.perf.RunType
import com.gowaist.core.perf.Vo2Max
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

@HiltViewModel
class PerfViewModel @Inject constructor(perf: PerformanceRepository, private val settings: SettingsRepository) : ViewModel() {
    val data = perf.data.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun saveProfile(p: AppSettings) = viewModelScope.launch {
        settings.update { it.copy(age = p.age, sex = p.sex, heightCm = p.heightCm, restHr = p.restHr, maxHr = p.maxHr) }
    }
}

/** Position 0–1 of [vo2] on the six-band level scale of [profile]. */
fun levelPosition(vo2: Double, profile: Profile): Float {
    val t = Vo2Max.thresholds(profile)
    val bounds = listOf(t[0] - 8) + t.toList() + listOf(t[4] + 8)
    val band = (0 until 6).firstOrNull { vo2 < bounds[it + 1] } ?: 5
    val lo = bounds[band]
    val hi = bounds[band + 1]
    val within = ((vo2 - lo) / (hi - lo)).coerceIn(0.0, 1.0)
    return ((band + within) / 6).toFloat()
}

@Composable
fun Signed(value: Double, digits: Int = 1, goodWhenUp: Boolean = true, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.labelLarge) {
    val up = value >= 0
    val c = when {
        abs(value) < 0.05 -> Gw.colors.muted
        up == goodWhenUp -> Gw.colors.success
        else -> Gw.colors.danger
    }
    Text((if (up) "▲ +" else "▼ ") + Format.decimal(value, digits), color = c, style = style, fontWeight = FontWeight.Bold)
}

@Composable
fun Vo2Screen(nav: NavHostController, vm: PerfViewModel = hiltViewModel()) {
    val data by vm.data.collectAsStateWithLifecycle()
    var editProfile by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            GwTopBar(stringResource(R.string.vo2_title), onBack = { nav.popBackStack() }) {
                IconButton({ editProfile = true }) { Icon(Icons.Rounded.Person, stringResource(R.string.profile_title)) }
            }
        },
    ) { pad ->
        val d = data
        when {
            d == null -> Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            d.daily.isEmpty() -> EmptyState(
                stringResource(R.string.vo2_empty_title), stringResource(R.string.vo2_empty_text), Modifier.padding(pad),
                mood = MascotMood.CHEER, actionText = stringResource(R.string.prog_cooper), onAction = { nav.navigate(RunProgramRoute(RunType.COOPER.name)) },
            )
            else -> Vo2Content(d, Modifier.padding(pad), onProfile = { editProfile = true }, openRun = { nav.navigate(RunDetailRoute(it)) }, cooper = { nav.navigate(RunProgramRoute(RunType.COOPER.name)) })
        }
    }
    if (editProfile) {
        ProfileDialog(LocalAppSettings.current, onSave = { vm.saveProfile(it); editProfile = false }, onDismiss = { editProfile = false })
    }
}

@Composable
private fun Vo2Content(d: PerformanceData, modifier: Modifier, onProfile: () -> Unit, openRun: (Long) -> Unit, cooper: () -> Unit) {
    var range by rememberSaveable { mutableStateOf(90) }
    val current = d.current ?: return
    val level = d.level
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GameBanner(listOf(Palette.Pink, Palette.Purple), seed = 4) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ArcGauge(Format.decimal(current, 1), stringResource(R.string.vo2_unit), ((current - 20) / 45).toFloat(), size = 150.dp, modifier = Modifier.testTag("vo2_value"))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.vo2_level), color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelLarge)
                    if (level != null) Text(level.label(), color = Palette.Yellow, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(6.dp))
                    d.change(30)?.let { ch ->
                        Text(
                            (if (ch >= 0) "▲ +" else "▼ ") + stringResource(R.string.vo2_change, Format.decimal(ch, 1)),
                            color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        GameCard {
            Text(stringResource(R.string.vo2_level), style = MaterialTheme.typography.titleMedium)
            LevelScale(LevelColors, levelPosition(current, d.profile))
            Row(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.level_very_poor), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.level_superior), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!d.profile.isComplete) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.vo2_profile_needed), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                GameButton(stringResource(R.string.vo2_set_profile), onProfile, color = Palette.Purple, icon = Icons.Rounded.Person, modifier = Modifier.fillMaxWidth())
            }
        }

        SectionHeader(stringResource(R.string.vo2_daily))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(30 to R.string.vo2_range_30, 90 to R.string.vo2_range_90, 365 to R.string.vo2_range_365).forEach { (n, l) ->
                FilterChip(range == n, { range = n }, label = { Text(stringResource(l)) })
            }
        }
        val shown = remember(d.daily, range) { d.daily.takeLast(range) }
        if (shown.size >= 2) {
            GameCard {
                MultiLineChart(
                    listOf(
                        ChartSeries(shown.map { it.trend.toFloat() }, Palette.Purple, label = stringResource(R.string.vo2_trend)),
                        ChartSeries(shown.map { it.measured?.toFloat() }, Palette.Pink, line = false, dots = true, label = stringResource(R.string.vo2_measured)),
                    ),
                    firstLabel = Fmt.dayMonth(shown.first().date),
                    lastLabel = Fmt.dayMonth(shown.last().date),
                    description = stringResource(R.string.vo2_daily),
                )
            }
        }
        GameCard {
            // Day-by-day list, newest first, with the change from the day before.
            val days = d.daily.takeLast(15)
            days.drop(1).reversed().forEachIndexed { i, day ->
                val prev = days[days.size - 2 - i]
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(Fmt.dateShort(day.date), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    if (day.measured != null) {
                        Pill(Format.decimal(day.measured!!, 1), Palette.Pink)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(Format.decimal(day.trend, 1), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, modifier = Modifier.width(56.dp))
                    Box(Modifier.width(72.dp), contentAlignment = Alignment.CenterEnd) { Signed(day.trend - prev.trend, digits = 2) }
                }
            }
            if (days.size < 2) Text(Fmt.dateShort(days.last().date) + " · " + Format.decimal(days.last().trend, 1), style = MaterialTheme.typography.titleMedium)
        }

        SectionHeader(stringResource(R.string.vo2_estimates))
        d.estimates.sortedByDescending { it.date }.take(8).forEach { e ->
            GameCard(onClick = { openRun(e.runId) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(Fmt.dateMedium(e.date), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        d.runsById[e.runId]?.let { r -> Text(Fmt.distance(r.distanceM) + " · " + Fmt.duration(r.durationSec), style = MaterialTheme.typography.bodyMedium) }
                    }
                    Pill(e.method.label(), if (e.method == com.gowaist.core.perf.Vo2Method.COOPER) Palette.Blue else Palette.Purple)
                    Spacer(Modifier.width(8.dp))
                    Text(Format.decimal(e.value, 1), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                }
            }
        }
        GameButton(stringResource(R.string.prog_cooper), cooper, color = Palette.Blue, icon = Icons.Rounded.Timer, modifier = Modifier.fillMaxWidth())

        GameCard {
            Text(stringResource(R.string.vo2_how), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.vo2_how_text), style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(16.dp))
    }
}

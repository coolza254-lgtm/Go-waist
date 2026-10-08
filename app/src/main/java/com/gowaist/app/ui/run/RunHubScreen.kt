package com.gowaist.app.ui.run

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.gowaist.app.data.db.RunEntity
import com.gowaist.app.data.repo.PerformanceData
import com.gowaist.app.data.repo.PerformanceRepository
import com.gowaist.app.data.repo.ProgramRepository
import com.gowaist.app.data.repo.ProgramState
import com.gowaist.app.data.repo.RunRepository
import com.gowaist.app.data.toLocalDate
import com.gowaist.app.ui.Fmt
import com.gowaist.app.ui.components.ArcGauge
import com.gowaist.app.ui.components.GameBanner
import com.gowaist.app.ui.components.Mascot
import com.gowaist.app.ui.components.MenuTile
import com.gowaist.app.ui.components.SectionHeader
import com.gowaist.app.ui.components.TileGrid
import com.gowaist.app.ui.nav.BodyRoute
import com.gowaist.app.ui.nav.PerformanceRoute
import com.gowaist.app.ui.nav.RunDetailRoute
import com.gowaist.app.ui.nav.RunImportRoute
import com.gowaist.app.ui.nav.RunLogRoute
import com.gowaist.app.ui.nav.RunProgramRoute
import com.gowaist.app.ui.nav.Vo2Route
import com.gowaist.app.ui.perf.label
import com.gowaist.app.ui.theme.Palette
import com.gowaist.core.Format
import com.gowaist.core.mascot.MascotMood
import com.gowaist.core.perf.RunProgramRules
import com.gowaist.core.perf.RunType
import com.gowaist.core.stats.weekStart
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

/** Bright colours of the four running programs, shared by the hub and the program screens. */
object ProgramColors {
    val cooper = Palette.Blue
    val interval = Palette.Purple
    val free = Palette.Teal
    val long = Palette.Orange

    fun of(t: RunType): Color = when (t) {
        RunType.COOPER -> cooper
        RunType.INTERVAL -> interval
        RunType.FREE -> free
        RunType.LONG -> long
    }
}

@HiltViewModel
class RunHubViewModel @Inject constructor(
    perf: PerformanceRepository,
    programs: ProgramRepository,
    runs: RunRepository,
) : ViewModel() {
    val perf = perf.data.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val programs = programs.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProgramState())
    val runs = runs.runs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

/** Run tab: a game-style menu with the four programs, tracking screens and recent runs. */
@Composable
fun RunHubScreen(nav: NavHostController, vm: RunHubViewModel = hiltViewModel()) {
    val perf by vm.perf.collectAsStateWithLifecycle()
    val programs by vm.programs.collectAsStateWithLifecycle()
    val runs by vm.runs.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val weekKm = remember(runs) { runs.filter { !it.localDate.toLocalDate().isBefore(today.weekStart()) }.sumOf { it.distanceM } }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HubBanner(perf, weekKm) { nav.navigate(Vo2Route) }

        SectionHeader(stringResource(R.string.hub_modes))
        ProgramTiles(programs, runs, today) { nav.navigate(RunProgramRoute(it.name)) }

        SectionHeader(stringResource(R.string.hub_menu))
        TileGrid(
            columns = 2,
            content = listOf(
                { MenuTile(stringResource(R.string.menu_vo2), Icons.Rounded.MonitorHeart, Palette.Pink, { nav.navigate(Vo2Route) }, subtitle = stringResource(R.string.menu_vo2_sub), minHeight = 112.dp, seed = 5, modifier = Modifier.testTag("menu_vo2")) },
                { MenuTile(stringResource(R.string.menu_perf), Icons.Rounded.Insights, Color(0xFF00A6C8), { nav.navigate(PerformanceRoute) }, subtitle = stringResource(R.string.menu_perf_sub), minHeight = 112.dp, seed = 6, modifier = Modifier.testTag("menu_perf")) },
                { MenuTile(stringResource(R.string.menu_weight), Icons.Rounded.MonitorWeight, Palette.Green, { nav.navigate(BodyRoute) }, subtitle = stringResource(R.string.menu_weight_sub), minHeight = 112.dp, seed = 7, modifier = Modifier.testTag("menu_weight")) },
                { MenuTile(stringResource(R.string.menu_log), Icons.Rounded.History, Color(0xFFFF9F1C), { nav.navigate(RunLogRoute) }, subtitle = stringResource(R.string.menu_log_sub), minHeight = 112.dp, seed = 8, modifier = Modifier.testTag("menu_log")) },
                { MenuTile(stringResource(R.string.menu_import), Icons.Rounded.PhotoLibrary, Color(0xFF5B6CFF), { nav.navigate(RunImportRoute) }, subtitle = stringResource(R.string.menu_import_sub), minHeight = 112.dp, seed = 9, modifier = Modifier.testTag("menu_import")) },
            ),
        )

        if (runs.isNotEmpty()) {
            SectionHeader(stringResource(R.string.hub_recent)) {
                TextButton({ nav.navigate(RunLogRoute) }) { Text(stringResource(R.string.action_see_all)) }
            }
            runs.take(3).forEach { r -> RunRow(r) { nav.navigate(RunDetailRoute(r.id)) } }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun HubBanner(perf: PerformanceData?, weekM: Double, openVo2: () -> Unit) {
    GameBanner(listOf(Palette.Orange, Palette.Pink), seed = 3) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.hub_title), style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Black)
                Text(stringResource(R.string.hub_subtitle), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.92f))
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.hub_week_km, Fmt.distance(weekM, 1)),
                    style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold,
                )
                val level = perf?.level
                if (level != null) {
                    Text(level.label(), style = MaterialTheme.typography.titleSmall, color = Palette.Yellow, fontWeight = FontWeight.Black)
                }
                Mascot(if (perf?.current != null) MascotMood.CHEER else MascotMood.HAPPY, Modifier.size(64.dp), animate = false)
            }
            val v = perf?.current
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ArcGauge(
                    value = v?.let { Format.decimal(it, 1) } ?: "--",
                    label = stringResource(R.string.hub_vo2_label),
                    fraction = v?.let { ((it - 20) / 45).toFloat() } ?: 0f,
                    modifier = Modifier.testTag("hub_vo2"),
                )
                if (v == null) {
                    Text(stringResource(R.string.hub_no_vo2), style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.width(132.dp))
                } else {
                    TextButton(openVo2) { Text(stringResource(R.string.vo2_daily), color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun ProgramTiles(state: ProgramState, runs: List<RunEntity>, today: LocalDate, open: (RunType) -> Unit) {
    val p = state.programs
    val i = p.interval
    val cooperDue = RunProgramRules.cooperDue(p.cooper, today)
    val lastLong = runs.firstOrNull { it.runType == RunType.LONG }
    TileGrid(
        columns = 2,
        content = listOf(
            {
                MenuTile(
                    stringResource(R.string.prog_cooper), Icons.Rounded.Timer, ProgramColors.cooper, { open(RunType.COOPER) },
                    subtitle = stringResource(R.string.prog_cooper_sub),
                    badge = if (cooperDue) stringResource(R.string.prog_cooper_due) else stringResource(R.string.prog_cooper_in, RunProgramRules.daysUntilCooper(p.cooper, today)),
                    badgeColor = if (cooperDue) Palette.Yellow else Color.White,
                    seed = 1, modifier = Modifier.testTag("prog_COOPER"),
                )
            },
            {
                MenuTile(
                    stringResource(R.string.prog_interval), Icons.Rounded.Bolt, ProgramColors.interval, { open(RunType.INTERVAL) },
                    subtitle = stringResource(R.string.prog_interval_sub, i.reps, Format.duration(i.workSec.toLong()), Format.duration(i.restSec.toLong())),
                    badge = if (state.pendingInterval != null) stringResource(R.string.prog_new_tip) else stringResource(R.string.prog_level, RunProgramRules.intervalLevel(i)),
                    seed = 2, modifier = Modifier.testTag("prog_INTERVAL"),
                )
            },
            {
                MenuTile(
                    stringResource(R.string.prog_free), Icons.AutoMirrored.Rounded.DirectionsRun, ProgramColors.free, { open(RunType.FREE) },
                    subtitle = if (p.freeRun.targetMin > 0) stringResource(R.string.prog_free_sub, p.freeRun.targetMin) else stringResource(R.string.prog_free_sub_none),
                    seed = 3, modifier = Modifier.testTag("prog_FREE"),
                )
            },
            {
                MenuTile(
                    stringResource(R.string.prog_long), Icons.Rounded.Route, ProgramColors.long, { open(RunType.LONG) },
                    subtitle = stringResource(R.string.prog_long_sub, Fmt.distance(p.longRun.targetKm * 1000, 1)),
                    badge = if (state.pendingLongRun != null) stringResource(R.string.prog_new_tip) else stringResource(R.string.prog_level, RunProgramRules.longRunLevel(p.longRun)),
                    progress = lastLong?.let { (it.distanceM / 1000 / p.longRun.targetKm).toFloat() },
                    seed = 4, modifier = Modifier.testTag("prog_LONG"),
                )
            },
        ),
    )
}

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
    private val goalRepo: GoalRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    val runs = runs.runs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val perf = perf.data.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val shoes = shoes.shoes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
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

        val last = list.firstOrNull()
        if (last != null) {
            LastRunCard(last, shoes.firstOrNull { it.shoe.id == last.shoeId }?.shoe?.name) { nav.navigate(RunDetailRoute(last.id)) }
        } else if (runs != null) {
            GameCard(modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.home_first_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.home_first_text), style = MaterialTheme.typography.bodyMedium, color = Gw.colors.muted)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val teal = Gw.colors.goal
            GameCard(Modifier.weight(1f), fill = teal, onClick = { nav.navigate(Vo2Route) }) {
                val fg = teal.contentColor()
                Text(stringResource(R.string.hub_vo2_label), style = MaterialTheme.typography.labelMedium, color = fg)
                Text(perf?.current?.let { Format.decimal(it, 1) } ?: "--", style = NumberStyles.big, color = fg, modifier = Modifier.testTag("home_vo2"))
                Text(
                    perf?.change(30)?.let { (if (it >= 0) "▲ +" else "▼ ") + stringResource(R.string.vo2_change, Format.decimal(it, 1)) } ?: stringResource(R.string.home_vo2_none),
                    style = MaterialTheme.typography.labelSmall, color = fg, maxLines = 2,
                )
                Spacer(Modifier.height(6.dp))
                val trend = remember(perf) { perf?.daily?.takeLast(30)?.map { it.trend.toFloat() }.orEmpty() }
                Sparkline(trend, fg)
            }
            val ws = today.weekStart()
            val week = remember(list) {
                (0..6).map { d -> list.filter { it.localDate.toLocalDate() == ws.plusDays(d.toLong()) }.sumOf { it.distanceM } }
            }
            GameCard(Modifier.weight(1f)) {
                Text(stringResource(R.string.home_this_week), style = MaterialTheme.typography.labelMedium, color = Gw.colors.muted)
                Text(Fmt.distanceValue(week.sum(), 1), style = NumberStyles.big)
                Text(
                    LocalAppSettings.current.distanceUnit.label() + " · " + stringResource(R.string.run_count, list.count { !it.localDate.toLocalDate().isBefore(ws) }),
                    style = MaterialTheme.typography.labelSmall, color = Gw.colors.muted,
                )
                Spacer(Modifier.height(6.dp))
                WeekBars(week, ChronoUnit.DAYS.between(ws, today).toInt(), Gw.colors.ink, MaterialTheme.colorScheme.primary)
            }
        }

        if (goals.isNotEmpty()) {
            GameCard(onClick = { nav.navigate(GoalsRoute) }, modifier = Modifier.fillMaxWidth()) {
                goals.forEachIndexed { i, g ->
                    if (i > 0) Spacer(Modifier.height(10.dp))
                    GoalLine(g, vm.progress[g.id])
                }
            }
        } else {
            TextButton({ nav.navigate(GoalsRoute) }) { Text(stringResource(R.string.home_set_goal)) }
        }

        shoes.firstOrNull { it.shoe.isMain && !it.shoe.retired }?.let { main ->
            GameCard(onClick = { nav.switchTab(ShoesRoute) }, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(ShoeIcon, null, tint = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(8.dp))
                    Text(main.shoe.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(Fmt.distance(main.stats.distanceM, 0), style = MaterialTheme.typography.labelLarge)
                }
                main.shoe.durabilityPct?.let {
                    Spacer(Modifier.height(8.dp))
                    HpBar(it, height = 12.dp)
                }
            }
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

package com.gowaist.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.gowaist.app.R
import com.gowaist.app.data.db.GoalWithReward
import com.gowaist.app.data.db.PlanDayEntity
import com.gowaist.app.data.db.RecordDao
import com.gowaist.app.data.db.SessionEntity
import com.gowaist.app.data.repo.GoalRepository
import com.gowaist.app.data.repo.LibraryRepository
import com.gowaist.app.data.repo.PlanRepository
import com.gowaist.app.data.repo.RunRepository
import com.gowaist.app.data.repo.WorkoutRepository
import com.gowaist.app.data.settings.SettingsRepository
import com.gowaist.app.data.toLocalDate
import com.gowaist.app.data.toLocalDateTime
import com.gowaist.app.domain.SuggestionEngine
import com.gowaist.app.domain.SuggestionItem
import com.gowaist.app.ui.Fmt
import com.gowaist.app.ui.components.GameButton
import com.gowaist.app.ui.components.GameCard
import com.gowaist.app.ui.components.GameProgressBar
import com.gowaist.app.ui.components.MascotSays
import com.gowaist.app.ui.components.PlanDayRow
import com.gowaist.app.ui.components.SectionHeader
import com.gowaist.app.ui.components.StatTile
import com.gowaist.app.ui.components.SuggestionCard
import com.gowaist.app.ui.components.WeekStrip
import com.gowaist.app.ui.nav.BodyRoute
import com.gowaist.app.ui.nav.GoalsTabRoute
import com.gowaist.app.ui.nav.PlanTabRoute
import com.gowaist.app.ui.nav.RunEditRoute
import com.gowaist.app.ui.nav.RunImportRoute
import com.gowaist.app.ui.nav.SessionRoute
import com.gowaist.app.ui.nav.SettingsRoute
import com.gowaist.app.ui.switchTab
import com.gowaist.app.ui.theme.Gw
import com.gowaist.core.mascot.MascotBrain
import com.gowaist.core.mascot.MascotContext
import com.gowaist.core.mascot.MascotLine
import com.gowaist.core.model.GoalStatus
import com.gowaist.core.model.PlanDayStatus
import com.gowaist.core.plan.PlanMatcher
import com.gowaist.core.stats.Streaks
import com.gowaist.core.stats.weekStart
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class HomeState(
    val loaded: Boolean = false,
    val nickname: String = "",
    val mascot: MascotLine = MascotLine.DEFAULT,
    val today: LocalDate = LocalDate.now(),
    val todayDays: List<PlanDayEntity> = emptyList(),
    val weekDays: Map<LocalDate, List<PlanDayEntity>> = emptyMap(),
    val activeDays: Set<LocalDate> = emptySet(),
    val weekRunM: Double = 0.0,
    val weekSessions: Int = 0,
    val weekActive: Int = 0,
    val streak: Int = 0,
    val goals: List<GoalWithReward> = emptyList(),
    val draft: SessionEntity? = null,
    val templateNames: Map<Long, String> = emptyMap(),
    val bodyEnabled: Boolean = true,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    settings: SettingsRepository,
    private val plans: PlanRepository,
    runs: RunRepository,
    private val workouts: WorkoutRepository,
    goals: GoalRepository,
    library: LibraryRepository,
    records: RecordDao,
    private val engine: SuggestionEngine,
) : ViewModel() {
    private val today = LocalDate.now()
    private val monday = today.weekStart()

    private val activity = combine(runs.runs, workouts.finishedSessions, plans.days(monday.minusWeeks(52), monday.plusDays(6)), records.observeAll()) { r, s, d, rec ->
        Quad(r, s, d, rec)
    }

    val state: StateFlow<HomeState> = combine(
        settings.settings, activity, goals.goals, workouts.draft, library.templates,
    ) { st, act, goalList, draft, templates ->
        val (runList, sessions, planDays, recs) = act
        val runDates = runList.map { it.localDate.toLocalDate() }
        val sessionDates = sessions.map { it.localDate.toLocalDate() }
        val active = (runDates + sessionDates).toSet()
        val restDays = planDays.filter { it.status == PlanDayStatus.REST || it.type == com.gowaist.core.model.PlanDayType.REST }.map { it.date.toLocalDate() }.toSet()
        val week = planDays.filter { !it.date.toLocalDate().isBefore(monday) }.groupBy { it.date.toLocalDate() }
        val todayDays = week[today].orEmpty()
        val weekWork = week.values.flatten().filter { it.type != com.gowaist.core.model.PlanDayType.REST }
        val lastActivity = active.maxOrNull()
        val lastPb = recs.maxOfOrNull { it.achievedAt }?.toLocalDateTime()?.toLocalDate()
        val ctx = MascotContext(
            hasAnyActivity = active.isNotEmpty(),
            daysSinceLastActivity = lastActivity?.let { ChronoUnit.DAYS.between(it, today).toInt() },
            pbWithinDays = lastPb?.let { ChronoUnit.DAYS.between(it, today).toInt() },
            todayPlan = todayDays.firstOrNull()?.type,
            todayStatus = todayDays.firstOrNull()?.status,
            weekPlanned = weekWork.size,
            weekDone = weekWork.count { PlanMatcher.isCompleted(it.status) },
            streak = Streaks.current(active, restDays, today),
            missedThisWeek = weekWork.count { it.status == PlanDayStatus.MISSED },
        )
        HomeState(
            loaded = true,
            nickname = st.nickname,
            mascot = MascotBrain.line(ctx),
            today = today,
            todayDays = todayDays,
            weekDays = week,
            activeDays = active,
            weekRunM = runList.filter { !it.localDate.toLocalDate().isBefore(monday) }.sumOf { it.distanceM },
            weekSessions = sessions.count { !it.localDate.toLocalDate().isBefore(monday) },
            weekActive = active.count { !it.isBefore(monday) && !it.isAfter(today) },
            streak = ctx.streak,
            goals = goalList.filter { it.goal.status == GoalStatus.ACTIVE }.take(3),
            draft = draft,
            templateNames = templates.associate { it.template.id to it.template.name },
            bodyEnabled = st.bodyMetricsEnabled,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeState())

    init {
        // Days that passed since the last launch become "missed" / "done".
        viewModelScope.launch { plans.syncStatuses() }
    }

    private val _suggestions = MutableStateFlow<List<SuggestionItem>>(emptyList())
    val suggestions: StateFlow<List<SuggestionItem>> = _suggestions

    fun refreshSuggestions() = viewModelScope.launch { _suggestions.value = runCatching { engine.open() }.getOrDefault(emptyList()) }

    fun answer(item: SuggestionItem, action: Int) = viewModelScope.launch {
        when (action) {
            0 -> engine.accept(item)
            1 -> engine.reject(item)
            else -> engine.snooze(item)
        }
        refreshSuggestions()
    }

    fun startBodyweight(day: PlanDayEntity?, onStarted: (Long) -> Unit) = viewModelScope.launch {
        onStarted(workouts.start(templateId = day?.templateId, planDayId = day?.id))
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

@Composable
fun HomeScreen(nav: NavHostController, vm: HomeViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        vm.refreshSuggestions()
        onPauseOrDispose { }
    }
    val lines = stringArrayResource(mascotArray(s.mascot))
    val line = lines[(s.today.dayOfYear + s.mascot.ordinal) % lines.size]
    val dayNames = stringArrayResource(R.array.weekdays_short).toList()
    var menu by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (s.nickname.isBlank()) stringResource(R.string.home_hello_anon) else stringResource(R.string.home_hello, s.nickname),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(Fmt.dateLong(s.today), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.cd_menu)) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text(stringResource(R.string.home_menu_settings)) }, { menu = false; nav.navigate(SettingsRoute) }, leadingIcon = { Icon(Icons.Rounded.Settings, null) })
                    if (s.bodyEnabled) DropdownMenuItem({ Text(stringResource(R.string.home_menu_body)) }, { menu = false; nav.navigate(BodyRoute) }, leadingIcon = { Icon(Icons.Rounded.MonitorWeight, null) })
                    DropdownMenuItem({ Text(stringResource(R.string.home_menu_backup)) }, { menu = false; nav.navigate(SettingsRoute) }, leadingIcon = { Icon(Icons.Rounded.Backup, null) })
                }
            }
        }
        item { MascotSays(line, s.mascot.mood) }

        s.draft?.let { draft ->
            item {
                GameCard(accent = Gw.colors.warning, onClick = { nav.navigate(SessionRoute(draft.id)) }) {
                    Text(stringResource(R.string.home_resume_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.home_resume_text, Fmt.time(draft.startAt.toLocalDateTime())), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    GameButton(stringResource(R.string.action_continue), { nav.navigate(SessionRoute(draft.id)) }, color = Gw.colors.warning, icon = Icons.Rounded.PlayArrow, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        item { SectionHeader(stringResource(R.string.home_today)) }
        if (s.todayDays.isEmpty()) {
            item {
                GameCard {
                    Text(stringResource(R.string.home_no_plan_today), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.home_no_plan_text), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    GameButton(stringResource(R.string.nav_plan), { nav.switchTab(PlanTabRoute) }, color = Gw.colors.plan, icon = Icons.Rounded.CalendarMonth, modifier = Modifier.fillMaxWidth())
                }
            }
        } else {
            items(s.todayDays, key = { it.id }) { day ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlanDayRow(day, day.templateId?.let { s.templateNames[it] }, isToday = true)
                    val pending = day.status == PlanDayStatus.PENDING || day.status == PlanDayStatus.PARTIAL
                    when {
                        day.type == com.gowaist.core.model.PlanDayType.REST -> Text(stringResource(R.string.home_rest_today), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        day.type.isRun && pending -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GameButton(stringResource(R.string.home_import_run), { nav.navigate(RunImportRoute) }, color = Gw.colors.run, icon = Icons.Rounded.PhotoLibrary, modifier = Modifier.weight(1f))
                            GameButton(stringResource(R.string.home_manual_run), { nav.navigate(RunEditRoute()) }, color = Gw.colors.run.copy(alpha = 0.85f), icon = Icons.Rounded.EditNote, modifier = Modifier.weight(1f))
                        }
                        day.type.isBodyweight && pending -> GameButton(
                            stringResource(R.string.action_start_now), { vm.startBodyweight(day) { nav.navigate(SessionRoute(it)) } },
                            color = Gw.colors.train, icon = Icons.Rounded.PlayArrow, big = true, modifier = Modifier.fillMaxWidth(),
                        )
                        else -> Unit
                    }
                }
            }
        }

        item {
            SectionHeader(stringResource(R.string.home_this_week))
            WeekStrip(s.today.weekStart(), s.weekDays, s.activeDays, s.today, dayNames)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(stringResource(R.string.home_run_distance), Fmt.distanceValue(s.weekRunM, 1), Modifier.weight(1f), unit = com.gowaist.app.ui.LocalAppSettings.current.distanceUnit.let { stringResource(Fmt.unitLabelRes(it)) }, icon = Icons.AutoMirrored.Rounded.DirectionsRun, color = Gw.colors.run)
                StatTile(stringResource(R.string.home_bw_sessions), "${s.weekSessions}", Modifier.weight(1f), unit = stringResource(R.string.unit_sessions), icon = Icons.Rounded.FitnessCenter, color = Gw.colors.train)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(stringResource(R.string.home_active_days), stringResource(R.string.home_days_of, s.weekActive, 7), Modifier.weight(1f), icon = Icons.Rounded.CalendarMonth, color = Gw.colors.plan)
                StatTile(stringResource(R.string.home_streak), "${s.streak}", Modifier.weight(1f), unit = stringResource(R.string.unit_days), icon = Icons.Rounded.LocalFireDepartment, color = Gw.colors.warning)
            }
        }

        if (s.goals.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.home_goals)) { androidx.compose.material3.TextButton({ nav.switchTab(GoalsTabRoute) }) { Text(stringResource(R.string.action_see_all)) } } }
            items(s.goals, key = { it.goal.id }) { g ->
                GameCard(accent = Gw.colors.goal, onClick = { nav.switchTab(GoalsTabRoute) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(g.goal.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(stringResource(R.string.home_goal_progress, (g.goal.progress * 100).toInt()), style = MaterialTheme.typography.titleMedium, color = Gw.colors.goal)
                    }
                    Spacer(Modifier.height(8.dp))
                    GameProgressBar(g.goal.progress.toFloat(), color = Gw.colors.goal)
                    g.reward?.let { Text("🎁 " + it.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp)) }
                }
            }
        }

        if (suggestions.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.sug_section)) }
            items(suggestions.take(3), key = { it.key }) { item ->
                SuggestionCard(item, { vm.answer(item, 0) }, { vm.answer(item, 1) }, { vm.answer(item, 2) })
            }
        }

        item {
            SectionHeader(stringResource(R.string.home_quick))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GameButton(stringResource(R.string.home_import_run), { nav.navigate(RunImportRoute) }, color = Gw.colors.run, icon = Icons.Rounded.PhotoLibrary, modifier = Modifier.weight(1f))
                GameButton(stringResource(R.string.home_start_bw), { vm.startBodyweight(null) { nav.navigate(SessionRoute(it)) } }, color = Gw.colors.train, icon = Icons.Rounded.FitnessCenter, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.width(1.dp))
        }
    }
}

fun mascotArray(line: MascotLine): Int = when (line) {
    MascotLine.WELCOME -> R.array.mascot_welcome
    MascotLine.NEW_PB -> R.array.mascot_new_pb
    MascotLine.TODAY_DONE -> R.array.mascot_today_done
    MascotLine.WEEK_COMPLETE -> R.array.mascot_week_complete
    MascotLine.STREAK -> R.array.mascot_streak
    MascotLine.TODAY_RUN -> R.array.mascot_today_run
    MascotLine.TODAY_TRAIN -> R.array.mascot_today_train
    MascotLine.REST_DAY -> R.array.mascot_rest_day
    MascotLine.MISSED_SOME -> R.array.mascot_missed_some
    MascotLine.LONG_BREAK -> R.array.mascot_long_break
    MascotLine.DEFAULT -> R.array.mascot_default
}

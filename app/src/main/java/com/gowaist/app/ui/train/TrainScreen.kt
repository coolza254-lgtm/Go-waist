package com.gowaist.app.ui.train

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.gowaist.app.R
import com.gowaist.app.data.db.ExerciseEntity
import com.gowaist.app.data.db.PersonalRecordEntity
import com.gowaist.app.data.db.RecordDao
import com.gowaist.app.data.repo.ChainView
import com.gowaist.app.data.repo.LibraryRepository
import com.gowaist.app.data.repo.PlanRepository
import com.gowaist.app.data.repo.SessionSummary
import com.gowaist.app.data.repo.TemplateView
import com.gowaist.app.data.repo.WorkoutRepository
import com.gowaist.app.data.settings.SettingsRepository
import com.gowaist.app.data.toEpochMillis
import com.gowaist.app.data.toFacts
import com.gowaist.app.data.toLocalDate
import com.gowaist.app.domain.SuggestionEngine
import com.gowaist.app.domain.SuggestionItem
import com.gowaist.app.ui.Fmt
import com.gowaist.app.ui.LocalAppSettings
import com.gowaist.app.ui.components.BarChart
import com.gowaist.app.ui.components.CalendarHeatmap
import com.gowaist.app.ui.components.ChartPoint
import com.gowaist.app.ui.components.EmptyState
import com.gowaist.app.ui.components.GameButton
import com.gowaist.app.ui.components.GameCard
import com.gowaist.app.ui.components.IconBlob
import com.gowaist.app.ui.components.LineChart
import com.gowaist.app.ui.components.MuscleMap
import com.gowaist.app.ui.components.Pill
import com.gowaist.app.ui.components.SectionHeader
import com.gowaist.app.ui.components.StatTile
import com.gowaist.app.ui.components.SuggestionCard
import com.gowaist.app.ui.components.color
import com.gowaist.app.ui.components.icon
import com.gowaist.app.ui.components.labelRes
import com.gowaist.app.ui.nav.ChainEditRoute
import com.gowaist.app.ui.nav.ExerciseDetailRoute
import com.gowaist.app.ui.nav.ExerciseEditRoute
import com.gowaist.app.ui.nav.IntervalTimerRoute
import com.gowaist.app.ui.nav.SessionRoute
import com.gowaist.app.ui.nav.TemplateEditRoute
import com.gowaist.app.ui.theme.Gw
import com.gowaist.core.Format
import com.gowaist.core.mascot.MascotMood
import com.gowaist.core.model.Equipment
import com.gowaist.core.model.MovementPattern
import com.gowaist.core.model.Muscle
import com.gowaist.core.model.PlanDayType
import com.gowaist.core.model.TrackingType
import com.gowaist.core.stats.Balance
import com.gowaist.core.stats.Streaks
import com.gowaist.core.stats.WorkoutMath
import com.gowaist.core.stats.weekStart
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import javax.inject.Inject

data class TrainStats(
    val weeklyVolume: List<Pair<LocalDate, Double>> = emptyList(),
    val weeklySessions: List<Pair<LocalDate, Int>> = emptyList(),
    val heat: Map<LocalDate, Float> = emptyMap(),
    val streak: Int = 0,
    val totalTimeSec: Long = 0,
    val totalSessions: Int = 0,
    val muscles7: Map<Muscle, Double> = emptyMap(),
    val muscles30: Map<Muscle, Double> = emptyMap(),
    val ratios: Balance.Ratios = Balance.Ratios(0, 0, 0.0, 0.0),
    val weeklyRpe: List<Pair<LocalDate, Double>> = emptyList(),
    val records: List<Pair<PersonalRecordEntity, String>> = emptyList(),
)

@HiltViewModel
class TrainViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val workouts: WorkoutRepository,
    private val plans: PlanRepository,
    private val settingsRepo: SettingsRepository,
    records: RecordDao,
    private val engine: SuggestionEngine,
) : ViewModel() {
    val templates = library.templates.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val exercises = library.exercises.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val chains = library.chains.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val history = workouts.history.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val draft = workouts.draft.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val todayPlan = plans.days(LocalDate.now(), LocalDate.now()).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val stats: StateFlow<TrainStats> = combine(
        workouts.setRows, workouts.finishedSessions, library.exercises, records.observeAll(), plans.days(LocalDate.now().minusYears(1), LocalDate.now()),
    ) { rows, sessions, exercises, recs, planDays ->
        val today = LocalDate.now()
        val exById = exercises.associateBy { it.id }
        val facts = exById.mapValues { it.value.toFacts() }
        val sets = rows.map { it.toFacts() }
        val bw = settingsRepo.current().bodyweightKg
        val weeks = (11 downTo 0).map { today.weekStart().minusWeeks(it.toLong()) }
        val byWeek = sets.groupBy { it.date.weekStart() }
        val sessionsByWeek = sessions.groupBy { it.localDate.toLocalDate().weekStart() }
        val sessionDays = sessions.map { it.localDate.toLocalDate() }.toSet()
        val restDays = planDays.filter { it.type == PlanDayType.REST }.map { it.date.toLocalDate() }.toSet()
        TrainStats(
            weeklyVolume = weeks.map { w -> w to WorkoutMath.summarize(byWeek[w].orEmpty(), facts, bw).volumeKg },
            weeklySessions = weeks.map { w -> w to (sessionsByWeek[w]?.size ?: 0) },
            heat = sets.filter { it.isWorking }.groupBy { it.date }.mapValues { it.value.size.toFloat() },
            streak = Streaks.current(sessionDays, restDays, today),
            totalTimeSec = sessions.sumOf { ((it.endAt ?: it.startAt) - it.startAt) / 1000 },
            totalSessions = sessions.size,
            muscles7 = Balance.muscleSets(sets.filter { !it.date.isBefore(today.minusDays(6)) }, facts),
            muscles30 = Balance.muscleSets(sets.filter { !it.date.isBefore(today.minusDays(29)) }, facts),
            ratios = Balance.ratios(sets.filter { !it.date.isBefore(today.minusDays(29)) }, facts),
            weeklyRpe = weeks.mapNotNull { w -> byWeek[w].orEmpty().mapNotNull { it.rpe }.takeIf { it.isNotEmpty() }?.let { w to it.average() } },
            records = recs.filter { it.exerciseId != null }.take(15).map { it to (exById[it.exerciseId]?.nameTh ?: "") },
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TrainStats())

    private val _suggestions = MutableStateFlow<List<SuggestionItem>>(emptyList())
    val suggestions: StateFlow<List<SuggestionItem>> = _suggestions

    var askDraft by mutableStateOf<(() -> Unit)?>(null)

    fun refreshSuggestions() = viewModelScope.launch {
        _suggestions.value = runCatching { engine.open().filterIsInstance<SuggestionItem.Progression>() }.getOrDefault(emptyList())
    }

    fun answer(item: SuggestionItem, action: Int) = viewModelScope.launch {
        when (action) {
            0 -> engine.accept(item)
            1 -> engine.reject(item)
            else -> engine.snooze(item)
        }
        refreshSuggestions()
    }

    /** Starts a session; if a draft exists the user chooses between resuming and discarding it. */
    fun start(templateId: Long?, planDayId: Long?, open: (Long) -> Unit) {
        viewModelScope.launch {
            val existing = workouts.currentDraft()
            if (existing == null) {
                open(workouts.start(templateId = templateId, planDayId = planDayId))
            } else {
                askDraft = {
                    viewModelScope.launch {
                        workouts.discard(existing.id)
                        open(workouts.start(templateId = templateId, planDayId = planDayId))
                    }
                }
            }
        }
    }

    fun logPast(date: LocalDate, open: (Long) -> Unit) = viewModelScope.launch {
        open(workouts.start(startAt = date.atTime(LocalTime.of(18, 0)).toEpochMillis(), draft = false))
    }

    fun toggleFavorite(e: ExerciseEntity) = viewModelScope.launch { library.setFavorite(e.id, !e.isFavorite) }

    fun setChainStep(chainId: Long, step: Int) = viewModelScope.launch { library.setChainStep(chainId, step) }
}

@Composable
fun TrainScreen(nav: NavHostController, vm: TrainViewModel = hiltViewModel()) {
    var tab by rememberSaveable { mutableStateOf(0) }
    LifecycleResumeEffect(Unit) {
        vm.refreshSuggestions()
        onPauseOrDispose { }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text(stringResource(R.string.train_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
        PrimaryScrollableTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background, edgePadding = 8.dp) {
            listOf(R.string.train_tab_start, R.string.train_tab_library, R.string.train_tab_chains, R.string.train_tab_stats).forEachIndexed { i, t ->
                Tab(tab == i, { tab = i }, text = { Text(stringResource(t)) })
            }
        }
        when (tab) {
            0 -> StartTab(nav, vm)
            1 -> LibraryTab(nav, vm)
            2 -> ChainsTab(nav, vm)
            else -> StatsTab(vm)
        }
    }
    vm.askDraft?.let { discardAndStart ->
        AlertDialog(
            onDismissRequest = { vm.askDraft = null },
            title = { Text(stringResource(R.string.train_draft_title)) },
            text = { Text(stringResource(R.string.train_draft_text)) },
            confirmButton = {
                TextButton({
                    vm.askDraft = null
                    vm.draft.value?.let { nav.navigate(SessionRoute(it.id)) }
                }) { Text(stringResource(R.string.action_continue)) }
            },
            dismissButton = { TextButton({ vm.askDraft = null; discardAndStart() }) { Text(stringResource(R.string.train_draft_discard), color = Gw.colors.danger) } },
        )
    }
}

@Composable
private fun StartTab(nav: NavHostController, vm: TrainViewModel) {
    val templates by vm.templates.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val today by vm.todayPlan.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val owned = LocalAppSettings.current.equipment
    var onlyDoable by rememberSaveable { mutableStateOf(true) }
    var pickDate by remember { mutableStateOf(false) }
    val open: (Long) -> Unit = { nav.navigate(SessionRoute(it)) }
    val bwToday = today.firstOrNull { it.type.isBodyweight }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        draft?.let { d ->
            item {
                GameButton(stringResource(R.string.home_resume_title), { open(d.id) }, color = Gw.colors.warning, icon = Icons.Rounded.PlayArrow, big = true, modifier = Modifier.fillMaxWidth())
            }
        }
        if (bwToday != null) {
            item {
                GameButton(
                    stringResource(R.string.train_start_plan) + " · " + stringResource(bwToday.type.labelRes()),
                    { vm.start(bwToday.templateId, bwToday.id, open) }, color = Gw.colors.plan, icon = Icons.Rounded.CalendarMonth, big = true, modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GameButton(stringResource(R.string.train_start_empty), { vm.start(null, null, open) }, color = Gw.colors.train, icon = Icons.Rounded.Add, modifier = Modifier.weight(1f))
                GameButton(stringResource(R.string.train_log_past), { pickDate = true }, color = Gw.colors.muted, icon = Icons.Rounded.History, modifier = Modifier.weight(1f))
            }
        }
        item { GameButton(stringResource(R.string.train_interval), { nav.navigate(IntervalTimerRoute(bwToday?.id ?: 0)) }, color = Gw.colors.danger, icon = Icons.Rounded.Timer, modifier = Modifier.fillMaxWidth()) }

        if (suggestions.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.sug_section)) }
            items(suggestions.take(4), key = { it.key }) { s -> SuggestionCard(s, { vm.answer(s, 0) }, { vm.answer(s, 1) }, { vm.answer(s, 2) }) }
        }

        item {
            SectionHeader(stringResource(R.string.train_templates)) {
                TextButton({ nav.navigate(TemplateEditRoute()) }) { Icon(Icons.Rounded.Add, null); Text(stringResource(R.string.train_template_new)) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.train_only_doable), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(onlyDoable, { onlyDoable = it })
            }
        }
        val shown = templates.filter { !onlyDoable || it.doableWith(owned) }
        items(shown, key = { "t" + it.template.id }) { t ->
            TemplateCard(t, onStart = { vm.start(t.template.id, null, open) }, onEdit = { nav.navigate(TemplateEditRoute(t.template.id)) })
        }

        item { SectionHeader(stringResource(R.string.train_recent)) }
        if (history.isEmpty()) item { Text(stringResource(R.string.train_no_sessions), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(history.take(15), key = { "s" + it.session.id }) { s -> SessionRow(s) { open(s.session.id) } }
        item { Spacer(Modifier.height(24.dp)) }
    }
    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = LocalDate.now().minusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton({
                    pickDate = false
                    state.selectedDateMillis?.let { vm.logPast(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate(), open) }
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton({ pickDate = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state) }
    }
}

@Composable
private fun TemplateCard(t: TemplateView, onStart: () -> Unit, onEdit: () -> Unit) {
    GameCard(accent = Gw.colors.train, onClick = onStart) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBlob(Icons.Rounded.FitnessCenter, Gw.colors.train, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.template.name, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.train_template_info, t.items.size, t.estimatedMinutes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onEdit) { Icon(Icons.Rounded.Edit, stringResource(R.string.action_edit)) }
            IconButton(onStart) { Icon(Icons.Rounded.PlayArrow, stringResource(R.string.action_start), tint = Gw.colors.train) }
        }
        if (t.template.description.isNotBlank()) Text(t.template.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        val eq = t.equipment
        Pill(
            if (eq.isEmpty()) stringResource(R.string.train_no_equipment) else stringResource(R.string.train_needs, eq.map { stringResource(it.labelRes()) }.joinToString(", ")),
            if (eq.isEmpty()) Gw.colors.success else Gw.colors.warning,
        )
        Spacer(Modifier.height(4.dp))
        Text(t.items.joinToString(" · ") { it.exercise.nameTh }, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SessionRow(s: SessionSummary, onClick: () -> Unit) {
    GameCard(onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Text(Fmt.dateShort(s.session.localDate.toLocalDate()) + (if (s.session.name.isNotBlank()) " · " + s.session.name else ""), style = MaterialTheme.typography.titleMedium)
        val dur = ((s.session.endAt ?: s.session.startAt) - s.session.startAt) / 1000
        Text(stringResource(R.string.train_session_info, s.summary.sets, s.summary.reps, Fmt.duration(dur)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (s.exerciseNames.isNotEmpty()) Text(s.exerciseNames.joinToString(" · "), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LibraryTab(nav: NavHostController, vm: TrainViewModel) {
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val owned = LocalAppSettings.current.equipment
    var query by rememberSaveable { mutableStateOf("") }
    var pattern by rememberSaveable { mutableStateOf<MovementPattern?>(null) }
    var muscle by rememberSaveable { mutableStateOf<Muscle?>(null) }
    var difficulty by rememberSaveable { mutableStateOf<Int?>(null) }
    var favorites by rememberSaveable { mutableStateOf(false) }
    var showHidden by rememberSaveable { mutableStateOf(false) }
    var onlyDoable by rememberSaveable { mutableStateOf(false) }
    var muscleMenu by remember { mutableStateOf(false) }
    val list = exercises.filter { e ->
        (showHidden || !e.isHidden) && e.matches(query) &&
            (pattern == null || e.pattern == pattern) &&
            (muscle == null || muscle in e.primaryMuscles || muscle in e.secondaryMuscles) &&
            (difficulty == null || e.difficulty == difficulty) &&
            (!favorites || e.isFavorite) &&
            (!onlyDoable || e.equipment.all { it == Equipment.NONE || it in owned })
    }
    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            OutlinedTextField(query, { query = it }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, placeholder = { Text(stringResource(R.string.lib_search)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { FilterChip(pattern == null, { pattern = null }, label = { Text(stringResource(R.string.lib_all)) }) }
                items(MovementPattern.entries) { p -> FilterChip(pattern == p, { pattern = if (pattern == p) null else p }, label = { Text(stringResource(p.labelRes())) }) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box {
                    FilterChip(muscle != null, { muscleMenu = true }, label = { Text(muscle?.let { stringResource(it.labelRes()) } ?: stringResource(R.string.lib_muscle)) })
                    DropdownMenu(muscleMenu, { muscleMenu = false }) {
                        DropdownMenuItem({ Text(stringResource(R.string.lib_all)) }, { muscle = null; muscleMenu = false })
                        Muscle.entries.forEach { m -> DropdownMenuItem({ Text(stringResource(m.labelRes())) }, { muscle = m; muscleMenu = false }) }
                    }
                }
                (1..5).forEach { d -> FilterChip(difficulty == d, { difficulty = if (difficulty == d) null else d }, label = { Text("★$d") }) }
                FilterChip(favorites, { favorites = !favorites }, label = { Text(stringResource(R.string.lib_favorites)) }, leadingIcon = { Icon(Icons.Rounded.Star, null, Modifier.size(16.dp)) })
                FilterChip(onlyDoable, { onlyDoable = !onlyDoable }, label = { Text(stringResource(R.string.train_only_doable)) })
                FilterChip(showHidden, { showHidden = !showHidden }, label = { Text(stringResource(R.string.lib_show_hidden)) })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.lib_count, list.size), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton({ nav.navigate(ExerciseEditRoute()) }) { Icon(Icons.Rounded.Add, null); Text(stringResource(R.string.lib_add)) }
            }
        }
        if (list.isEmpty()) item { EmptyState(stringResource(R.string.lib_empty), "", mood = MascotMood.SAD) }
        items(list, key = { it.id }) { e -> ExerciseRow(e, owned, onFavorite = { vm.toggleFavorite(e) }) { nav.navigate(ExerciseDetailRoute(e.id)) } }
    }
}

@Composable
fun ExerciseRow(e: ExerciseEntity, owned: Set<Equipment>, onFavorite: () -> Unit, onClick: () -> Unit) {
    val doable = e.equipment.all { it == Equipment.NONE || it in owned }
    GameCard(onClick = onClick, contentPadding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBlob(e.pattern.icon(), e.pattern.color().copy(alpha = if (doable) 1f else 0.45f), size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(e.nameTh, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(e.nameEn, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("★".repeat(e.difficulty) + "☆".repeat(5 - e.difficulty), color = Gw.colors.gold, style = MaterialTheme.typography.labelMedium)
                    Text(" · " + stringResource(e.trackingType.labelRes()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (e.isCustom) Pill(stringResource(R.string.lib_custom), Gw.colors.plan)
                    if (e.isHidden) Icon(Icons.Rounded.VisibilityOff, stringResource(R.string.lib_hidden), Modifier.size(16.dp), tint = Gw.colors.muted)
                }
            }
            IconButton(onFavorite) {
                Icon(if (e.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder, stringResource(R.string.cd_favorite), tint = if (e.isFavorite) Gw.colors.gold else Gw.colors.muted)
            }
        }
    }
}

@Composable
private fun ChainsTab(nav: NavHostController, vm: TrainViewModel) {
    val chains by vm.chains.collectAsStateWithLifecycle()
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                TextButton({ nav.navigate(ChainEditRoute()) }) { Icon(Icons.Rounded.Add, null); Text(stringResource(R.string.chain_new)) }
            }
        }
        items(chains, key = { it.chain.id }) { c -> ChainCard(c, onStep = { vm.setChainStep(c.chain.id, it) }, onExercise = { nav.navigate(ExerciseDetailRoute(it)) }, onEdit = { nav.navigate(ChainEditRoute(c.chain.id)) }) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ChainCard(c: ChainView, onStep: (Int) -> Unit, onExercise: (Long) -> Unit, onEdit: () -> Unit) {
    var menuFor by remember { mutableStateOf<Int?>(null) }
    GameCard(accent = Gw.colors.plan) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(c.chain.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onEdit) { Icon(Icons.Rounded.Edit, stringResource(R.string.action_edit)) }
        }
        c.steps.forEachIndexed { i, s ->
            val current = i == c.chain.currentStep
            val passed = i < c.chain.currentStep
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(36.dp)) {
                    Box(
                        Modifier.size(if (current) 30.dp else 22.dp).clip(CircleShape)
                            .background(if (passed || current) Gw.colors.plan else Gw.colors.track),
                        contentAlignment = Alignment.Center,
                    ) { Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = if (passed || current) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface) }
                }
                Spacer(Modifier.width(8.dp))
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                        .background(if (current) Gw.colors.plan.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { menuFor = i }.padding(8.dp),
                ) {
                    Text(s.exercise.nameTh, style = if (current) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge)
                    if (current) {
                        val unit = if (s.exercise.trackingType == TrackingType.HOLD) stringResource(R.string.unit_sec) else stringResource(R.string.unit_reps)
                        Text(stringResource(R.string.chain_rule, s.step.targetSets, s.step.targetRepsOrSec, unit, s.step.sessionsRequired), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (current) Pill(stringResource(R.string.chain_current), Gw.colors.plan, filled = true)
                DropdownMenu(menuFor == i, { menuFor = null }) {
                    DropdownMenuItem({ Text(stringResource(R.string.chain_set_current)) }, { menuFor = null; onStep(i) })
                    DropdownMenuItem({ Text(stringResource(R.string.ex_detail)) }, { menuFor = null; onExercise(s.exercise.id) })
                }
            }
        }
    }
}

@Composable
private fun StatsTab(vm: TrainViewModel) {
    val s by vm.stats.collectAsStateWithLifecycle()
    var days30 by rememberSaveable { mutableStateOf(false) }
    if (s.totalSessions == 0) {
        EmptyState(stringResource(R.string.st_no_data), "", mood = MascotMood.SLEEPY)
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(stringResource(R.string.st_streak), "${s.streak}", Modifier.weight(1f), unit = stringResource(R.string.unit_days), icon = Icons.Rounded.LocalFireDepartment, color = Gw.colors.warning)
                StatTile(stringResource(R.string.st_total_sessions), "${s.totalSessions}", Modifier.weight(1f), icon = Icons.Rounded.FitnessCenter, color = Gw.colors.train)
                StatTile(stringResource(R.string.st_total_time), Format.decimal(s.totalTimeSec / 3600.0, 1), Modifier.weight(1f), unit = stringResource(R.string.unit_hours), icon = Icons.Rounded.Timer, color = Gw.colors.goal)
            }
        }
        item {
            GameCard {
                Text(stringResource(R.string.st_weekly_volume), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                BarChart(s.weeklyVolume.map { ChartPoint(Fmt.dayMonth(it.first), it.second.toFloat()) }, color = Gw.colors.train, valueLabel = { if (it == 0f) "" else Format.decimal(it.toDouble(), 0) }, description = stringResource(R.string.st_weekly_volume))
            }
        }
        item {
            GameCard {
                Text(stringResource(R.string.st_weekly_sessions), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                BarChart(s.weeklySessions.map { ChartPoint(Fmt.dayMonth(it.first), it.second.toFloat()) }, color = Gw.colors.plan, height = 120.dp, valueLabel = { if (it == 0f) "" else it.toInt().toString() }, description = stringResource(R.string.st_weekly_sessions))
            }
        }
        item {
            GameCard {
                Text(stringResource(R.string.st_consistency), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                CalendarHeatmap(s.heat, 16, LocalDate.now(), color = Gw.colors.train, description = stringResource(R.string.st_consistency))
            }
        }
        item {
            GameCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.st_muscles), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    SingleChoiceSegmentedButtonRow {
                        SegmentedButton(!days30, { days30 = false }, SegmentedButtonDefaults.itemShape(0, 2)) { Text(stringResource(R.string.st_days7)) }
                        SegmentedButton(days30, { days30 = true }, SegmentedButtonDefaults.itemShape(1, 2)) { Text(stringResource(R.string.st_days30)) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                val load = if (days30) s.muscles30 else s.muscles7
                MuscleMap(load, stringResource(R.string.st_front), stringResource(R.string.st_back), description = stringResource(R.string.st_muscles))
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    load.entries.filter { it.value > 0 }.sortedByDescending { it.value }.forEach { (m, v) ->
                        Pill(stringResource(m.labelRes()) + " " + Format.decimal(v, 1), Gw.colors.heatHigh)
                    }
                }
            }
        }
        item {
            GameCard {
                Text(stringResource(R.string.st_balance), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                val r = s.ratios
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(stringResource(R.string.st_push_pull), r.pushPull?.let { stringResource(R.string.st_ratio, Format.decimal(it, 1)) } ?: stringResource(R.string.st_ratio_na), Modifier.weight(1f), color = if (r.pushPullImbalanced) Gw.colors.warning else Gw.colors.success)
                    StatTile(stringResource(R.string.st_upper_lower), r.upperLower?.let { stringResource(R.string.st_ratio, Format.decimal(it, 1)) } ?: stringResource(R.string.st_ratio_na), Modifier.weight(1f), color = if (r.upperLowerImbalanced) Gw.colors.warning else Gw.colors.success)
                }
                Spacer(Modifier.height(8.dp))
                if (r.pushPullImbalanced) WarnLine(stringResource(R.string.st_pushpull_warn))
                if (r.upperLowerImbalanced) WarnLine(stringResource(R.string.st_upperlower_warn))
                if (!r.pushPullImbalanced && !r.upperLowerImbalanced) Text(stringResource(R.string.st_balanced), color = Gw.colors.success)
            }
        }
        if (s.weeklyRpe.size >= 2) {
            item {
                GameCard {
                    Text(stringResource(R.string.st_rpe), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    LineChart(s.weeklyRpe.map { ChartPoint(Fmt.dayMonth(it.first), it.second.toFloat()) }, color = Gw.colors.danger, description = stringResource(R.string.st_rpe))
                }
            }
        }
        if (s.records.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.st_records)) }
            items(s.records, key = { it.first.id }) { (r, name) -> RecordRow(r, name) }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun WarnLine(text: String) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 2.dp)) {
        Icon(Icons.Rounded.WarningAmber, null, tint = Gw.colors.warning)
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun RecordRow(r: PersonalRecordEntity, name: String) {
    GameCard(contentPadding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🏅", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(name.ifBlank { stringResource(r.type.labelRes()) }, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(r.type.labelRes()) + " · " + Fmt.dateShort(r.localDate.toLocalDate()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(recordValue(r), style = MaterialTheme.typography.titleMedium, color = Gw.colors.gold)
        }
    }
}

@Composable
fun recordValue(r: PersonalRecordEntity): String = when (r.type) {
    com.gowaist.core.model.PrType.LONGEST_HOLD -> Fmt.duration(r.value.toLong())
    com.gowaist.core.model.PrType.MAX_ADDED_WEIGHT, com.gowaist.core.model.PrType.EST_1RM -> Fmt.weight(r.value)
    com.gowaist.core.model.PrType.MAX_VOLUME_SESSION, com.gowaist.core.model.PrType.MAX_REPS, com.gowaist.core.model.PrType.BEST_SET -> Format.decimal(r.value, 0)
    com.gowaist.core.model.PrType.RUN_LONGEST -> Fmt.distance(r.value)
    com.gowaist.core.model.PrType.RUN_FASTEST_PACE -> Fmt.pace(r.value)
    else -> Fmt.duration(r.value.toLong())
}


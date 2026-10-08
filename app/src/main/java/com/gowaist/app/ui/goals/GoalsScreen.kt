package com.gowaist.app.ui.goals

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.toRoute
import com.gowaist.app.R
import com.gowaist.app.data.db.ExerciseEntity
import com.gowaist.app.data.db.GoalEntity
import com.gowaist.app.data.db.GoalWithReward
import com.gowaist.app.data.db.RewardEntity
import com.gowaist.app.data.key
import com.gowaist.app.data.repo.BodyRepository
import com.gowaist.app.data.repo.GoalRepository
import com.gowaist.app.data.repo.LibraryRepository
import com.gowaist.app.data.settings.AppSettings
import com.gowaist.app.data.settings.SettingsRepository
import com.gowaist.app.data.toLocalDate
import com.gowaist.app.domain.ActivityCoordinator
import com.gowaist.app.ui.Fmt
import com.gowaist.app.ui.GwTopBar
import com.gowaist.app.ui.LocalAppSettings
import com.gowaist.app.ui.components.ConfirmDialog
import com.gowaist.app.ui.components.EmptyState
import com.gowaist.app.ui.components.GameButton
import com.gowaist.app.ui.components.GameCard
import com.gowaist.app.ui.components.GameProgressBar
import com.gowaist.app.ui.components.Pill
import com.gowaist.app.ui.components.SectionHeader
import com.gowaist.app.ui.components.label
import com.gowaist.app.ui.components.labelRes
import com.gowaist.app.ui.nav.GoalEditRoute
import com.gowaist.app.ui.theme.Gw
import com.gowaist.app.ui.train.ExercisePickerDialog
import com.gowaist.core.Format
import com.gowaist.core.Units
import com.gowaist.core.goals.BadgeKey
import com.gowaist.core.goals.GoalProgress
import com.gowaist.core.mascot.MascotMood
import com.gowaist.core.model.GoalPeriod
import com.gowaist.core.model.GoalStatus
import com.gowaist.core.model.GoalType
import com.gowaist.core.stats.weekStart
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val goals: GoalRepository,
    private val coordinator: ActivityCoordinator,
) : ViewModel() {
    val list = goals.goals.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val badges = goals.badges.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    var live by mutableStateOf<Map<Long, GoalProgress>>(emptyMap())

    fun refresh() = viewModelScope.launch {
        coordinator.afterChange()
        live = goals.liveProgress()
    }

    fun claim(r: RewardEntity, claimed: Boolean) = viewModelScope.launch { goals.claimReward(r, claimed) }
    fun delete(id: Long) = viewModelScope.launch { goals.delete(id) }
}

@Composable
fun goalValue(type: GoalType, value: Double?): String {
    if (value == null) return "-"
    return when (type) {
        GoalType.RUN_DISTANCE -> Fmt.distance(value, 1)
        GoalType.RUN_COUNT, GoalType.BW_SESSIONS, GoalType.EXERCISE_MAX_REPS, GoalType.EXERCISE_TOTAL_REPS -> "${value.toInt()} " + stringResource(R.string.unit_reps)
        GoalType.RUN_5K_TIME -> Fmt.duration(value.toLong())
        GoalType.EXERCISE_HOLD -> "${value.toInt()} " + stringResource(R.string.unit_sec)
        GoalType.STREAK_DAYS -> "${value.toInt()} " + stringResource(R.string.unit_days)
        GoalType.BODY_WEIGHT -> Fmt.weight(value)
        GoalType.WAIST -> Fmt.length(value)
    }
}

@Composable
fun GoalsScreen(nav: NavHostController, vm: GoalsViewModel = hiltViewModel()) {
    val goals by vm.list.collectAsStateWithLifecycle()
    val badges by vm.badges.collectAsStateWithLifecycle()
    var rewardTab by rememberSaveable { mutableStateOf(1) }
    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }
    val active = goals.filter { it.goal.status == GoalStatus.ACTIVE }
    val achieved = goals.filter { it.goal.status == GoalStatus.ACHIEVED }
    val expired = goals.filter { it.goal.status == GoalStatus.EXPIRED }
    Scaffold(
        modifier = Modifier.statusBarsPadding(),
        floatingActionButton = {
            ExtendedFloatingActionButton(modifier = Modifier.testTag("fab_goal"), onClick = { nav.navigate(GoalEditRoute()) }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text(stringResource(R.string.goals_add)) }, containerColor = Gw.colors.goal, contentColor = MaterialTheme.colorScheme.surface)
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text(stringResource(R.string.goals_title), style = MaterialTheme.typography.headlineMedium) }
            if (goals.isEmpty()) {
                item { EmptyState(stringResource(R.string.goals_empty_title), stringResource(R.string.goals_empty_text), mood = MascotMood.CHEER, actionText = stringResource(R.string.goals_add), onAction = { nav.navigate(GoalEditRoute()) }) }
            }
            if (active.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.goals_active)) }
                items(active, key = { "a" + it.goal.id }) { g -> GoalCard(g, vm.live[g.goal.id], onEdit = { nav.navigate(GoalEditRoute(g.goal.id)) }, onDelete = { vm.delete(g.goal.id) }) }
            }

            item { SectionHeader(stringResource(R.string.goals_rewards)) }
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(R.string.goals_rewards_waiting, R.string.goals_rewards_unlocked, R.string.goals_rewards_claimed).forEachIndexed { i, l ->
                        SegmentedButton(rewardTab == i, { rewardTab = i }, SegmentedButtonDefaults.itemShape(i, 3)) { Text(stringResource(l), maxLines = 1) }
                    }
                }
            }
            val rewards = goals.mapNotNull { g -> g.reward?.let { g to it } }.filter { (g, r) ->
                when (rewardTab) {
                    0 -> r.unlockedAt == null && g.goal.status == GoalStatus.ACTIVE
                    1 -> r.unlockedAt != null && r.claimedAt == null
                    else -> r.claimedAt != null
                }
            }
            if (rewards.isEmpty()) item { Text(stringResource(R.string.goals_no_rewards), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(rewards, key = { "r" + it.second.id }) { (g, r) -> RewardRow(g, r, onClaim = { vm.claim(r, it) }) }

            item {
                SectionHeader(stringResource(R.string.goals_badges)) {
                    Text(stringResource(R.string.goals_badges_count, badges.size, BadgeKey.entries.size), style = MaterialTheme.typography.titleMedium, color = Gw.colors.goal)
                }
            }
            item {
                val have = badges.map { it.key }.toSet()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BadgeKey.entries.forEach { b -> BadgeTile(b, b.name in have) }
                }
            }
            if (achieved.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.goals_done)) }
                items(achieved, key = { "d" + it.goal.id }) { g -> GoalCard(g, vm.live[g.goal.id], onEdit = null, onDelete = { vm.delete(g.goal.id) }) }
            }
            if (expired.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.goals_expired)) }
                items(expired, key = { "e" + it.goal.id }) { g -> GoalCard(g, vm.live[g.goal.id], onEdit = { nav.navigate(GoalEditRoute(g.goal.id)) }, onDelete = { vm.delete(g.goal.id) }) }
            }
        }
    }
}

@Composable
private fun GoalCard(g: GoalWithReward, live: GoalProgress?, onEdit: (() -> Unit)?, onDelete: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    val goal = g.goal
    val color = when (goal.status) {
        GoalStatus.ACHIEVED -> Gw.colors.success
        GoalStatus.EXPIRED -> Gw.colors.muted
        GoalStatus.ACTIVE -> Gw.colors.goal
    }
    GameCard(accent = color) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(goal.title, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(goal.type.labelRes()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onEdit != null) IconButton(onEdit) { Icon(Icons.Rounded.Edit, stringResource(R.string.action_edit)) }
            IconButton({ confirm = true }) { Icon(Icons.Rounded.Delete, stringResource(R.string.goals_delete)) }
        }
        val fraction = if (goal.status == GoalStatus.ACHIEVED) 1.0 else live?.fraction ?: goal.progress
        GameProgressBar(fraction.toFloat(), color = color)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val text = if (goal.type == GoalType.RUN_5K_TIME) {
                live?.current?.let { stringResource(R.string.goals_best_5k, Fmt.duration(it.toLong()), Fmt.duration(goal.target.toLong())) } ?: stringResource(R.string.goals_no_5k)
            } else {
                stringResource(R.string.goals_progress_of, goalValue(goal.type, live?.current), goalValue(goal.type, goal.target))
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (goal.status == GoalStatus.ACTIVE) {
                val left = ChronoUnit.DAYS.between(LocalDate.now(), goal.endDate.toLocalDate()).toInt()
                Pill(if (left <= 0) stringResource(R.string.goals_last_day) else stringResource(R.string.goals_days_left, left), Gw.colors.plan)
            }
        }
        g.reward?.let { r ->
            Spacer(Modifier.height(6.dp))
            Text(if (r.unlockedAt == null) stringResource(R.string.goals_reward_locked, r.text) else "🎁 " + r.text, style = MaterialTheme.typography.bodyMedium, color = if (r.unlockedAt == null) MaterialTheme.colorScheme.onSurfaceVariant else Gw.colors.goal)
        }
    }
    if (confirm) ConfirmDialog(stringResource(R.string.goals_delete), stringResource(R.string.goals_delete_text), stringResource(R.string.action_delete), onDelete, { confirm = false }, destructive = true)
}

@Composable
private fun RewardRow(g: GoalWithReward, r: RewardEntity, onClaim: (Boolean) -> Unit) {
    GameCard(contentPadding = PaddingValues(12.dp), accent = if (r.unlockedAt != null) Gw.colors.gold else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (r.unlockedAt == null) "🔒" else if (r.claimedAt == null) "🎁" else "✅", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(r.text, style = MaterialTheme.typography.titleMedium)
                Text(g.goal.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (r.unlockedAt != null && r.claimedAt == null) TextButton({ onClaim(true) }) { Text(stringResource(R.string.goals_claim)) }
            if (r.claimedAt != null) TextButton({ onClaim(false) }) { Text(stringResource(R.string.goals_unclaim)) }
        }
    }
}

@Composable
private fun BadgeTile(b: BadgeKey, unlocked: Boolean) {
    Column(
        Modifier.width(104.dp).clip(RoundedCornerShape(18.dp))
            .background(if (unlocked) Gw.colors.gold.copy(alpha = 0.18f) else Gw.colors.track)
            .padding(10.dp).alpha(if (unlocked) 1f else 0.45f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(if (unlocked) badgeEmoji(b) else "🔒", style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(badgeTitleRes(b)), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2)
        Text(stringResource(badgeDescRes(b)), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
    }
}

// ------------------------------------------------------------------------------- edit

@HiltViewModel
class GoalEditViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val goals: GoalRepository,
    library: LibraryRepository,
    private val body: BodyRepository,
    private val settings: SettingsRepository,
    private val coordinator: ActivityCoordinator,
) : ViewModel() {
    val id = handle.toRoute<GoalEditRoute>().id
    private var units = AppSettings()
    val exercises = library.exercises.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    var type by mutableStateOf(GoalType.RUN_DISTANCE)
    var target by mutableStateOf("")
    var targetSec by mutableStateOf("")
    var exercise by mutableStateOf<ExerciseEntity?>(null)
    var period by mutableStateOf(GoalPeriod.WEEK)
    var customRange by mutableStateOf(LocalDate.now() to LocalDate.now().plusWeeks(4))
    var title by mutableStateOf("")
    var titleEdited by mutableStateOf(false)
    var reward by mutableStateOf("")
    var baseline by mutableStateOf("")
    var invalid by mutableStateOf(false)
    var done by mutableStateOf(false)
    private var existing: GoalEntity? = null

    init {
        viewModelScope.launch {
            units = settings.current()
            if (id != 0L) {
                val g = goals.goals.first().firstOrNull { it.goal.id == id } ?: return@launch
                existing = g.goal
                type = g.goal.type
                period = g.goal.period
                customRange = g.goal.startDate.toLocalDate() to g.goal.endDate.toLocalDate()
                title = g.goal.title
                titleEdited = true
                reward = g.reward?.text.orEmpty()
                exercise = g.goal.exerciseId?.let { exId -> exercises.first { it.isNotEmpty() }.firstOrNull { it.id == exId } }
                setTargetFrom(g.goal.target)
                baseline = g.goal.baseline?.let { Format.decimal(it, 1) }.orEmpty()
            }
        }
    }

    private fun unitDistance(v: Double) = Units.toMeters(units.distanceUnit, v)
    private fun unitWeight(v: Double) = Units.toKg(units.weightUnit, v)
    private fun unitLength(v: Double) = Units.toCm(units.lengthUnit, v)
    private fun fromDistance(v: Double) = Units.metersTo(units.distanceUnit, v)
    private fun fromWeight(v: Double) = Units.kgTo(units.weightUnit, v)
    private fun fromLength(v: Double) = Units.cmTo(units.lengthUnit, v)

    private fun setTargetFrom(v: Double) {
        when (type) {
            GoalType.RUN_DISTANCE -> target = Format.decimal(fromDistance(v), 1)
            GoalType.RUN_5K_TIME -> { target = (v.toLong() / 60).toString(); targetSec = (v.toLong() % 60).toString() }
            GoalType.BODY_WEIGHT -> target = Format.decimal(fromWeight(v), 1)
            GoalType.WAIST -> target = Format.decimal(fromLength(v), 1)
            else -> target = v.toInt().toString()
        }
    }

    suspend fun suggestBaseline() {
        val latest = body.all()
        baseline = when (type) {
            GoalType.BODY_WEIGHT -> latest.firstOrNull { it.weightKg != null }?.weightKg?.let { Format.decimal(fromWeight(it), 1) }.orEmpty()
            GoalType.WAIST -> latest.firstOrNull { it.waistCm != null }?.waistCm?.let { Format.decimal(fromLength(it), 1) }.orEmpty()
            else -> ""
        }
    }

    fun targetValue(): Double? {
        val t = target.replace(',', '.').toDoubleOrNull()
        return when (type) {
            GoalType.RUN_DISTANCE -> t?.let(::unitDistance)
            GoalType.RUN_5K_TIME -> ((target.toIntOrNull() ?: 0) * 60 + (targetSec.toIntOrNull() ?: 0)).toDouble().takeIf { it > 0 }
            GoalType.BODY_WEIGHT -> t?.let(::unitWeight)
            GoalType.WAIST -> t?.let(::unitLength)
            else -> t
        }?.takeIf { it > 0 }
    }

    val needsExercise get() = type == GoalType.EXERCISE_MAX_REPS || type == GoalType.EXERCISE_HOLD || type == GoalType.EXERCISE_TOTAL_REPS

    fun range(): Pair<LocalDate, LocalDate> {
        val today = LocalDate.now()
        return when (period) {
            GoalPeriod.WEEK -> today.weekStart() to today.weekStart().plusDays(6)
            GoalPeriod.MONTH -> today.withDayOfMonth(1) to YearMonth.from(today).atEndOfMonth()
            GoalPeriod.CUSTOM -> customRange
        }
    }

    fun save(autoTitle: String) = viewModelScope.launch {
        val t = targetValue()
        if (t == null || (needsExercise && exercise == null)) { invalid = true; return@launch }
        val (start, end) = range()
        val base = baseline.replace(',', '.').toDoubleOrNull()?.let { if (type == GoalType.BODY_WEIGHT) unitWeight(it) else unitLength(it) }
        val g = (existing ?: GoalEntity(type = type, title = "", target = t, period = period, startDate = start.key(), endDate = end.key())).copy(
            type = type, title = title.ifBlank { autoTitle }.trim(), target = t, exerciseId = exercise?.id?.takeIf { needsExercise },
            period = period, startDate = start.key(), endDate = end.key(),
            baseline = if (type == GoalType.BODY_WEIGHT || type == GoalType.WAIST) base else null,
            status = GoalStatus.ACTIVE, progress = 0.0, achievedAt = null,
        )
        if (existing == null) goals.create(g, reward) else goals.update(g, reward)
        coordinator.afterChange()
        done = true
    }
}

@Composable
fun GoalEditScreen(nav: NavHostController, vm: GoalEditViewModel = hiltViewModel()) {
    val s = LocalAppSettings.current
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    var picker by remember { mutableStateOf(false) }
    var pickRange by remember { mutableStateOf(false) }
    LaunchedEffect(vm.done) { if (vm.done) nav.popBackStack() }
    LaunchedEffect(vm.type) {
        if (vm.id == 0L && (vm.type == GoalType.BODY_WEIGHT || vm.type == GoalType.WAIST)) vm.suggestBaseline()
        if (vm.type == GoalType.EXERCISE_HOLD && vm.exercise == null) vm.exercise = exercises.firstOrNull { it.seedKey == "plank" }
    }
    val autoTitle = autoTitle(vm)
    Scaffold(
        topBar = { GwTopBar(stringResource(R.string.goal_edit_title), onBack = { nav.popBackStack() }) },
        bottomBar = { Box(Modifier.navigationBarsPadding().imePadding().padding(16.dp)) { GameButton(stringResource(R.string.action_save), { vm.save(autoTitle) }, color = Gw.colors.goal, icon = Icons.Rounded.Check, modifier = Modifier.fillMaxWidth()) } },
    ) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.goal_type), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GoalType.entries.filter { s.bodyMetricsEnabled || (it != GoalType.BODY_WEIGHT && it != GoalType.WAIST) }.forEach { t ->
                    FilterChip(vm.type == t, { vm.type = t; vm.invalid = false }, label = { Text(stringResource(t.labelRes())) })
                }
            }
            if (vm.needsExercise) {
                OutlinedButton({ picker = true }, Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.goal_exercise) + ": " + (vm.exercise?.nameTh ?: stringResource(R.string.goal_pick_exercise)))
                }
            }
            when (vm.type) {
                GoalType.RUN_5K_TIME -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(vm.target, { vm.target = it.filter(Char::isDigit).take(3) }, label = { Text(stringResource(R.string.goal_minutes)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(vm.targetSec, { vm.targetSec = it.filter(Char::isDigit).take(2) }, label = { Text(stringResource(R.string.goal_seconds)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                }
                else -> OutlinedTextField(
                    vm.target, { vm.target = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(7); vm.invalid = false },
                    label = { Text(stringResource(R.string.goal_target)) }, suffix = { Text(targetUnit(vm.type)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.titleLarge,
                )
            }
            if (vm.type == GoalType.BODY_WEIGHT || vm.type == GoalType.WAIST) {
                OutlinedTextField(vm.baseline, { vm.baseline = it.filter { c -> c.isDigit() || c == '.' }.take(6) }, label = { Text(stringResource(R.string.goal_baseline)) }, suffix = { Text(targetUnit(vm.type)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            if (vm.invalid) Text(stringResource(R.string.goal_invalid), color = Gw.colors.danger)
            Text(stringResource(R.string.goal_period), style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                GoalPeriod.entries.forEachIndexed { i, p ->
                    SegmentedButton(vm.period == p, { vm.period = p; if (p == GoalPeriod.CUSTOM) pickRange = true }, SegmentedButtonDefaults.itemShape(i, 3)) { Text(stringResource(p.labelRes())) }
                }
            }
            val (a, b) = vm.range()
            TextButton({ vm.period = GoalPeriod.CUSTOM; pickRange = true }) {
                Icon(Icons.Rounded.DateRange, null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.goal_range, Fmt.dateMedium(a), Fmt.dateMedium(b)))
            }
            OutlinedTextField(vm.title, { vm.title = it.take(60); vm.titleEdited = true }, label = { Text(stringResource(R.string.goal_name)) }, placeholder = { Text(autoTitle) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(vm.reward, { vm.reward = it.take(80) }, label = { Text(stringResource(R.string.goal_reward)) }, placeholder = { Text(stringResource(R.string.goal_reward_hint)) }, leadingIcon = { Text("🎁") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(24.dp))
        }
    }
    if (picker) {
        ExercisePickerDialog(exercises, s.equipment, multi = false, onDismiss = { picker = false }) { picked -> picker = false; vm.exercise = picked.firstOrNull() }
    }
    if (pickRange) {
        val state = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { pickRange = false },
            confirmButton = {
                TextButton({
                    val x = state.selectedStartDateMillis
                    val y = state.selectedEndDateMillis ?: x
                    if (x != null && y != null) vm.customRange = Instant.ofEpochMilli(x).atZone(ZoneOffset.UTC).toLocalDate() to Instant.ofEpochMilli(y).atZone(ZoneOffset.UTC).toLocalDate()
                    pickRange = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton({ pickRange = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DateRangePicker(state, Modifier.height(480.dp)) }
    }
}

@Composable
private fun targetUnit(type: GoalType): String {
    val s = LocalAppSettings.current
    return when (type) {
        GoalType.RUN_DISTANCE -> s.distanceUnit.label()
        GoalType.RUN_COUNT, GoalType.BW_SESSIONS, GoalType.EXERCISE_MAX_REPS, GoalType.EXERCISE_TOTAL_REPS -> stringResource(R.string.unit_reps)
        GoalType.EXERCISE_HOLD -> stringResource(R.string.unit_sec)
        GoalType.STREAK_DAYS -> stringResource(R.string.unit_days)
        GoalType.BODY_WEIGHT -> s.weightUnit.label()
        GoalType.WAIST -> s.lengthUnit.label()
        GoalType.RUN_5K_TIME -> ""
    }
}

@Composable
private fun autoTitle(vm: GoalEditViewModel): String {
    val n = vm.target.replace(',', '.').toDoubleOrNull()
    val i = n?.toInt() ?: 0
    val ex = vm.exercise?.nameTh.orEmpty()
    return when (vm.type) {
        GoalType.RUN_DISTANCE -> stringResource(R.string.goal_auto_run_distance, vm.targetValue()?.let { Fmt.distance(it, 1) } ?: "")
        GoalType.RUN_COUNT -> stringResource(R.string.goal_auto_run_count, i)
        GoalType.RUN_5K_TIME -> stringResource(R.string.goal_auto_5k, vm.targetValue()?.let { Fmt.duration(it.toLong()) } ?: "")
        GoalType.BW_SESSIONS -> stringResource(R.string.goal_auto_sessions, i)
        GoalType.EXERCISE_MAX_REPS -> stringResource(R.string.goal_auto_max_reps, ex, i)
        GoalType.EXERCISE_HOLD -> stringResource(R.string.goal_auto_hold, ex, i)
        GoalType.EXERCISE_TOTAL_REPS -> stringResource(R.string.goal_auto_total, ex, i)
        GoalType.STREAK_DAYS -> stringResource(R.string.goal_auto_streak, i)
        GoalType.BODY_WEIGHT -> stringResource(R.string.goal_auto_weight, vm.targetValue()?.let { Fmt.weight(it) } ?: "")
        GoalType.WAIST -> stringResource(R.string.goal_auto_waist, vm.targetValue()?.let { Fmt.length(it) } ?: "")
    }
}

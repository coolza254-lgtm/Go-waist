package com.gowaist.app.ui.train

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.toRoute
import com.gowaist.app.R
import com.gowaist.app.data.db.ChainEntity
import com.gowaist.app.data.db.ChainStepEntity
import com.gowaist.app.data.db.ExerciseEntity
import com.gowaist.app.data.db.PersonalRecordEntity
import com.gowaist.app.data.db.RecordDao
import com.gowaist.app.data.repo.BodyRepository
import com.gowaist.app.data.repo.LibraryRepository
import com.gowaist.app.data.repo.WorkoutRepository
import com.gowaist.app.data.toFacts
import com.gowaist.app.data.toLocalDate
import com.gowaist.app.ui.Fmt
import com.gowaist.app.ui.GwTopBar
import com.gowaist.app.ui.LocalAppSettings
import com.gowaist.app.ui.components.BarChart
import com.gowaist.app.ui.components.ChartPoint
import com.gowaist.app.ui.components.ConfirmDialog
import com.gowaist.app.ui.components.GameButton
import com.gowaist.app.ui.components.GameCard
import com.gowaist.app.ui.components.IconBlob
import com.gowaist.app.ui.components.LineChart
import com.gowaist.app.ui.components.Pill
import com.gowaist.app.ui.components.SectionHeader
import com.gowaist.app.ui.components.StatTile
import com.gowaist.app.ui.components.color
import com.gowaist.app.ui.components.icon
import com.gowaist.app.ui.components.labelRes
import com.gowaist.app.ui.nav.ChainEditRoute
import com.gowaist.app.ui.nav.ExerciseDetailRoute
import com.gowaist.app.ui.nav.ExerciseEditRoute
import com.gowaist.app.ui.theme.Gw
import com.gowaist.core.Format
import com.gowaist.core.model.Equipment
import com.gowaist.core.model.MovementPattern
import com.gowaist.core.model.Muscle
import com.gowaist.core.model.SetType
import com.gowaist.core.model.TrackingType
import com.gowaist.core.stats.SetFacts
import com.gowaist.core.stats.WorkoutMath
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// ------------------------------------------------------------------------------- detail

data class ExerciseDetailState(
    val exercise: ExerciseEntity? = null,
    val sessions: List<Pair<Long, List<com.gowaist.app.data.db.SetRow>>> = emptyList(),
    val bests: WorkoutMath.Bests = WorkoutMath.Bests(),
    val records: List<PersonalRecordEntity> = emptyList(),
    val chains: List<Pair<String, Int>> = emptyList(),
    val bodyweight: Double = 70.0,
)

@HiltViewModel
class ExerciseDetailViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val library: LibraryRepository,
    workouts: WorkoutRepository,
    records: RecordDao,
    body: BodyRepository,
) : ViewModel() {
    val id = handle.toRoute<ExerciseDetailRoute>().id
    val state = combine(library.observeExercise(id), workouts.setRows, records.observeAll(), library.chains, body.bodyweightKg) { ex, rows, recs, chains, bw ->
        val mine = rows.filter { it.exerciseId == id }
        ExerciseDetailState(
            exercise = ex,
            sessions = mine.groupBy { it.sessionId }.toList(),
            bests = ex?.let { WorkoutMath.bests(mine.map { r -> r.toFacts() }, it.toFacts(), bw) } ?: WorkoutMath.Bests(),
            records = recs.filter { it.exerciseId == id },
            chains = chains.mapNotNull { c -> c.steps.indexOfFirst { it.exercise.id == id }.takeIf { it >= 0 }?.let { c.chain.name to it + 1 } },
            bodyweight = bw,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExerciseDetailState())

    fun favorite(e: ExerciseEntity) = viewModelScope.launch { library.setFavorite(e.id, !e.isFavorite) }
    fun hide(e: ExerciseEntity) = viewModelScope.launch { library.setHidden(e.id, !e.isHidden) }
}

@Composable
fun ExerciseDetailScreen(nav: NavHostController, vm: ExerciseDetailViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val e = s.exercise ?: return
    Scaffold(
        topBar = {
            GwTopBar(e.nameTh, onBack = { nav.popBackStack() }) {
                IconButton({ vm.favorite(e) }) { Icon(if (e.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder, stringResource(R.string.cd_favorite), tint = if (e.isFavorite) Gw.colors.gold else MaterialTheme.colorScheme.onSurface) }
                IconButton({ vm.hide(e) }) { Icon(if (e.isHidden) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff, stringResource(if (e.isHidden) R.string.ex_unhide else R.string.ex_hide)) }
                IconButton({ nav.navigate(ExerciseEditRoute(e.id)) }) { Icon(Icons.Rounded.Edit, stringResource(R.string.action_edit)) }
            }
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                GameCard(accent = e.pattern.color()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBlob(e.pattern.icon(), e.pattern.color(), size = 52.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(e.nameEn, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("★".repeat(e.difficulty) + "☆".repeat(5 - e.difficulty), color = Gw.colors.gold, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    InfoLine(stringResource(R.string.ex_pattern), stringResource(e.pattern.labelRes()))
                    InfoLine(stringResource(R.string.ex_primary), e.primaryMuscles.map { stringResource(it.labelRes()) }.joinToString(", "))
                    if (e.secondaryMuscles.isNotEmpty()) InfoLine(stringResource(R.string.ex_secondary), e.secondaryMuscles.map { stringResource(it.labelRes()) }.joinToString(", "))
                    InfoLine(stringResource(R.string.ex_tracking), stringResource(e.trackingType.labelRes()))
                    InfoLine(stringResource(R.string.ex_equipment), e.equipment.map { stringResource(it.labelRes()) }.joinToString(", "))
                    s.chains.forEach { (name, step) -> Pill(stringResource(R.string.ex_in_chain, name, step), Gw.colors.plan, modifier = Modifier.padding(top = 6.dp)) }
                }
            }
            if (e.description.isNotBlank()) item { GameCard { Text(stringResource(R.string.ex_description), style = MaterialTheme.typography.titleMedium); Text(e.description) } }
            if (e.caution.isNotBlank()) item {
                GameCard(accent = Gw.colors.warning) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.WarningAmber, null, tint = Gw.colors.warning)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.ex_caution), style = MaterialTheme.typography.titleMedium)
                    }
                    Text(e.caution)
                }
            }
            item { SectionHeader(stringResource(R.string.ex_records)) }
            if (s.sessions.isEmpty()) {
                item { Text(stringResource(R.string.ex_no_history), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                item { BestTiles(e, s.bests) }
                item { ProgressCharts(e, s.sessions.map { (_, rows) -> rows.first().localDate to rows.map { it.toFacts() } }, s.bodyweight) }
                item { SectionHeader(stringResource(R.string.ex_history)) }
                s.sessions.asReversed().forEach { (sid, rows) ->
                    item(key = "h$sid") {
                        GameCard(contentPadding = PaddingValues(12.dp)) {
                            Text(Fmt.dateShort(rows.first().localDate.toLocalDate()), style = MaterialTheme.typography.titleSmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                rows.forEach { r ->
                                    Pill(setText(r.set, e.trackingType) + (r.set.rpe?.let { " @" + Format.decimal(it, 0) } ?: ""), if (r.set.setType == SetType.WARMUP) Gw.colors.muted else e.pattern.color())
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(130.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun BestTiles(e: ExerciseEntity, b: WorkoutMath.Bests) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            when (e.trackingType) {
                TrackingType.HOLD -> StatTile(stringResource(R.string.ex_longest_hold), Fmt.duration(b.longestHoldSec.toLong()), Modifier.weight(1f), color = Gw.colors.gold)
                TrackingType.CARDIO -> StatTile(stringResource(R.string.ex_longest_hold), Fmt.duration(b.longestHoldSec.toLong()), Modifier.weight(1f), color = Gw.colors.gold)
                else -> StatTile(stringResource(R.string.ex_max_reps), "${b.maxReps}", Modifier.weight(1f), unit = stringResource(R.string.unit_reps), color = Gw.colors.gold)
            }
            StatTile(stringResource(R.string.ex_best_volume), if (e.trackingType == TrackingType.REPS || e.trackingType == TrackingType.ASSISTED) "${b.maxSessionReps}" else Format.decimal(b.maxSessionVolumeKg, 0), Modifier.weight(1f), unit = if (e.trackingType == TrackingType.REPS || e.trackingType == TrackingType.ASSISTED) stringResource(R.string.unit_reps) else stringResource(R.string.unit_kg), color = Gw.colors.train)
        }
        if (e.trackingType == TrackingType.WEIGHTED) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(stringResource(R.string.ex_max_added), Fmt.weight(b.maxAddedKg), Modifier.weight(1f), color = Gw.colors.warning)
                StatTile(stringResource(R.string.ex_e1rm), Fmt.weight(b.est1RmKg), Modifier.weight(1f), color = Gw.colors.danger)
            }
        }
    }
}

@Composable
private fun ProgressCharts(e: ExerciseEntity, sessions: List<Pair<String, List<SetFacts>>>, bw: Double) {
    val recent = sessions.takeLast(16)
    val factsEx = e.toFacts()
    GameCard {
        Text(stringResource(R.string.ex_best_per_session), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (recent.size >= 2) {
            LineChart(
                recent.map { (d, sets) -> ChartPoint(Fmt.dayMonth(d.toLocalDate()), sets.filter { it.isWorking }.maxOfOrNull { WorkoutMath.setScore(it, factsEx, bw) }?.toFloat() ?: 0f) },
                color = e.pattern.color(), description = stringResource(R.string.ex_best_per_session),
            )
        } else {
            Text(Format.decimal(recent.first().second.maxOfOrNull { WorkoutMath.setScore(it, factsEx, bw) } ?: 0.0, 1), style = MaterialTheme.typography.headlineMedium)
        }
    }
    Spacer(Modifier.height(10.dp))
    GameCard {
        val repsMode = e.trackingType == TrackingType.REPS || e.trackingType == TrackingType.ASSISTED
        val holdMode = e.trackingType == TrackingType.HOLD || e.trackingType == TrackingType.CARDIO
        Text(stringResource(if (repsMode) R.string.ex_reps_per_session else if (holdMode) R.string.sum_hold else R.string.ex_volume_per_session), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        BarChart(
            recent.map { (d, sets) ->
                val s = WorkoutMath.summarize(sets, mapOf(e.id to factsEx), bw)
                ChartPoint(Fmt.dayMonth(d.toLocalDate()), (if (repsMode) s.reps.toDouble() else if (holdMode) s.holdSec.toDouble() else s.volumeKg).toFloat())
            },
            color = e.pattern.color(), valueLabel = { if (it == 0f) "" else Format.decimal(it.toDouble(), 0) },
        )
    }
}

// ------------------------------------------------------------------------------- edit exercise

@HiltViewModel
class ExerciseEditViewModel @Inject constructor(handle: SavedStateHandle, private val library: LibraryRepository) : ViewModel() {
    private val id = handle.toRoute<ExerciseEditRoute>().id
    var form by mutableStateOf(
        ExerciseEntity(nameTh = "", nameEn = "", pattern = MovementPattern.PUSH, primaryMuscles = setOf(Muscle.CHEST), secondaryMuscles = emptySet(), trackingType = TrackingType.REPS, difficulty = 2, isCustom = true),
    )
    var factorText by mutableStateOf("0.65")
    var restText by mutableStateOf("90")
    var done by mutableStateOf(false)
    var error by mutableStateOf(false)

    init {
        if (id != 0L) viewModelScope.launch {
            library.exercise(id)?.let { form = it; factorText = Format.decimal(it.bodyweightFactor, 2); restText = it.defaultRestSec.toString() }
        }
    }

    fun save() = viewModelScope.launch {
        if (form.nameTh.isBlank()) { error = true; return@launch }
        library.saveExercise(
            form.copy(
                nameEn = form.nameEn.ifBlank { form.nameTh },
                bodyweightFactor = factorText.toDoubleOrNull()?.coerceIn(0.0, 1.5) ?: form.bodyweightFactor,
                defaultRestSec = restText.toIntOrNull()?.coerceIn(0, 600) ?: form.defaultRestSec,
                equipment = form.equipment.ifEmpty { setOf(Equipment.NONE) },
            ),
        )
        done = true
    }
}

@Composable
fun ExerciseEditScreen(nav: NavHostController, vm: ExerciseEditViewModel = hiltViewModel()) {
    LaunchedEffect(vm.done) { if (vm.done) nav.popBackStack() }
    val f = vm.form
    Scaffold(
        topBar = { GwTopBar(stringResource(if (f.id == 0L) R.string.ex_new_title else R.string.ex_edit_title), onBack = { nav.popBackStack() }) },
        bottomBar = { Box(Modifier.navigationBarsPadding().imePadding().padding(16.dp)) { GameButton(stringResource(R.string.action_save), { vm.save() }, icon = Icons.Rounded.Check, modifier = Modifier.fillMaxWidth()) } },
    ) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(f.nameTh, { vm.form = f.copy(nameTh = it.take(60)); vm.error = false }, label = { Text(stringResource(R.string.ex_name_th)) }, isError = vm.error, supportingText = if (vm.error) { { Text(stringResource(R.string.ex_name_required)) } } else null, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(f.nameEn, { vm.form = f.copy(nameEn = it.take(60)) }, label = { Text(stringResource(R.string.ex_name_en)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            ChipGroup(stringResource(R.string.ex_pattern), MovementPattern.entries, { setOf(f.pattern) }, { stringResource(it.labelRes()) }) { vm.form = f.copy(pattern = it) }
            ChipGroup(stringResource(R.string.ex_tracking), TrackingType.entries, { setOf(f.trackingType) }, { stringResource(it.labelRes()) }) { vm.form = f.copy(trackingType = it) }
            ChipGroup(stringResource(R.string.ex_primary), Muscle.entries, { f.primaryMuscles }, { stringResource(it.labelRes()) }) { m ->
                vm.form = f.copy(primaryMuscles = if (m in f.primaryMuscles) f.primaryMuscles - m else f.primaryMuscles + m, secondaryMuscles = f.secondaryMuscles - m)
            }
            ChipGroup(stringResource(R.string.ex_secondary), Muscle.entries, { f.secondaryMuscles }, { stringResource(it.labelRes()) }) { m ->
                vm.form = f.copy(secondaryMuscles = if (m in f.secondaryMuscles) f.secondaryMuscles - m else f.secondaryMuscles + m, primaryMuscles = f.primaryMuscles - m)
            }
            ChipGroup(stringResource(R.string.ex_equipment), Equipment.entries, { f.equipment }, { stringResource(it.labelRes()) }) { e ->
                vm.form = f.copy(equipment = if (e in f.equipment) f.equipment - e else f.equipment + e)
            }
            ChipGroup(stringResource(R.string.ex_difficulty), (1..5).toList(), { setOf(f.difficulty) }, { "★$it" }) { vm.form = f.copy(difficulty = it) }
            OutlinedTextField(f.description, { vm.form = f.copy(description = it.take(600)) }, label = { Text(stringResource(R.string.ex_description)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(f.caution, { vm.form = f.copy(caution = it.take(400)) }, label = { Text(stringResource(R.string.ex_caution)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(vm.restText, { vm.restText = it.filter(Char::isDigit).take(3) }, label = { Text(stringResource(R.string.ex_rest)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(vm.factorText, { vm.factorText = it.filter { c -> c.isDigit() || c == '.' }.take(4) }, label = { Text(stringResource(R.string.ex_factor)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f))
            }
            Text(stringResource(R.string.ex_factor_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun <T> ChipGroup(title: String, options: List<T>, selected: () -> Set<T>, label: @Composable (T) -> String, onToggle: (T) -> Unit) {
    Text(title, style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val sel = selected()
        options.forEach { o -> FilterChip(o in sel, { onToggle(o) }, label = { Text(label(o)) }) }
    }
}

// ------------------------------------------------------------------------------- chain editor

data class StepDraft(val exercise: ExerciseEntity, val sets: Int, val target: Int, val sessions: Int)

@HiltViewModel
class ChainEditViewModel @Inject constructor(handle: SavedStateHandle, private val library: LibraryRepository) : ViewModel() {
    private val id = handle.toRoute<ChainEditRoute>().id
    var name by mutableStateOf("")
    var steps by mutableStateOf(listOf<StepDraft>())
    var chain: ChainEntity? = null
    var done by mutableStateOf(false)
    var error by mutableStateOf(false)
    val exercises = library.exercises.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        if (id != 0L) viewModelScope.launch {
            library.chains.collect { list ->
                val c = list.firstOrNull { it.chain.id == id } ?: return@collect
                if (chain == null) {
                    chain = c.chain
                    name = c.chain.name
                    steps = c.steps.map { StepDraft(it.exercise, it.step.targetSets, it.step.targetRepsOrSec, it.step.sessionsRequired) }
                }
            }
        }
    }

    fun move(i: Int, d: Int) {
        val l = steps.toMutableList()
        val j = (i + d).coerceIn(0, l.lastIndex)
        l.add(j, l.removeAt(i))
        steps = l
    }

    fun save() = viewModelScope.launch {
        if (name.isBlank() || steps.size < 2) { error = true; return@launch }
        val base = chain ?: ChainEntity(name = name, isCustom = true)
        library.saveChain(
            base.copy(name = name.trim(), currentStep = base.currentStep.coerceAtMost(steps.lastIndex)),
            steps.map { ChainStepEntity(chainId = 0, exerciseId = it.exercise.id, stepOrder = 0, targetSets = it.sets, targetRepsOrSec = it.target, sessionsRequired = it.sessions) },
        )
        done = true
    }

    fun delete() = viewModelScope.launch { chain?.let { library.deleteChain(it.id) }; done = true }
}

@Composable
fun ChainEditScreen(nav: NavHostController, vm: ChainEditViewModel = hiltViewModel()) {
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    var picker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(vm.done) { if (vm.done) nav.popBackStack() }
    Scaffold(
        topBar = {
            GwTopBar(stringResource(if (vm.chain == null) R.string.chain_new else R.string.chain_edit), onBack = { nav.popBackStack() }) {
                if (vm.chain != null) IconButton({ confirmDelete = true }) { Icon(Icons.Rounded.Delete, stringResource(R.string.chain_delete)) }
            }
        },
        bottomBar = { Box(Modifier.navigationBarsPadding().imePadding().padding(16.dp)) { GameButton(stringResource(R.string.action_save), { vm.save() }, color = Gw.colors.plan, icon = Icons.Rounded.Check, modifier = Modifier.fillMaxWidth()) } },
    ) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                OutlinedTextField(vm.name, { vm.name = it.take(40); vm.error = false }, label = { Text(stringResource(R.string.chain_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (vm.error) Text(stringResource(R.string.chain_steps_required), color = Gw.colors.danger)
            }
            itemsIndexed(vm.steps) { i, s ->
                GameCard(contentPadding = PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Pill(stringResource(R.string.chain_step_n, i + 1), Gw.colors.plan, filled = true)
                        Spacer(Modifier.width(8.dp))
                        Text(s.exercise.nameTh, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        IconButton({ vm.move(i, -1) }, enabled = i > 0) { Icon(Icons.Rounded.ArrowUpward, stringResource(R.string.sess_move_up)) }
                        IconButton({ vm.move(i, 1) }, enabled = i < vm.steps.lastIndex) { Icon(Icons.Rounded.ArrowDownward, stringResource(R.string.sess_move_down)) }
                        IconButton({ vm.steps = vm.steps.filterIndexed { j, _ -> j != i } }) { Icon(Icons.Rounded.Delete, stringResource(R.string.action_delete)) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallNumber(stringResource(R.string.chain_sets), s.sets, Modifier.weight(1f)) { v -> vm.steps = vm.steps.mapIndexed { j, x -> if (j == i) x.copy(sets = v) else x } }
                        SmallNumber(stringResource(R.string.chain_target), s.target, Modifier.weight(1f)) { v -> vm.steps = vm.steps.mapIndexed { j, x -> if (j == i) x.copy(target = v) else x } }
                        SmallNumber(stringResource(R.string.chain_sessions), s.sessions, Modifier.weight(1f)) { v -> vm.steps = vm.steps.mapIndexed { j, x -> if (j == i) x.copy(sessions = v) else x } }
                    }
                }
            }
            item {
                TextButton({ picker = true }) { Icon(Icons.Rounded.Add, null); Text(stringResource(R.string.chain_add_step)) }
            }
        }
    }
    if (picker) {
        ExercisePickerDialog(exercises, LocalAppSettings.current.equipment, multi = true, onDismiss = { picker = false }) { picked ->
            picker = false
            vm.steps = vm.steps + picked.map { StepDraft(it, 3, if (it.trackingType == TrackingType.HOLD) 30 else 10, 2) }
        }
    }
    if (confirmDelete) ConfirmDialog(stringResource(R.string.chain_delete), vm.name, stringResource(R.string.action_delete), { vm.delete() }, { confirmDelete = false }, destructive = true)
}

@Composable
fun SmallNumber(label: String, value: Int, modifier: Modifier = Modifier, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        text, { t -> text = t.filter(Char::isDigit).take(3); text.toIntOrNull()?.takeIf { it > 0 }?.let(onChange) },
        label = { Text(label, maxLines = 1) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = modifier,
    )
}

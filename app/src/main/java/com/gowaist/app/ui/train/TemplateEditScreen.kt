package com.gowaist.app.ui.train

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.toRoute
import com.gowaist.app.R
import com.gowaist.app.data.db.ExerciseEntity
import com.gowaist.app.data.db.TemplateEntity
import com.gowaist.app.data.db.TemplateItemEntity
import com.gowaist.app.data.repo.LibraryRepository
import com.gowaist.app.data.repo.WorkoutRepository
import com.gowaist.app.ui.GwTopBar
import com.gowaist.app.ui.LocalAppSettings
import com.gowaist.app.ui.components.ConfirmDialog
import com.gowaist.app.ui.components.GameButton
import com.gowaist.app.ui.components.GameCard
import com.gowaist.app.ui.components.Pill
import com.gowaist.app.ui.nav.SessionRoute
import com.gowaist.app.ui.nav.TemplateEditRoute
import com.gowaist.app.ui.theme.Gw
import com.gowaist.core.model.TrackingType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ItemDraft(val exercise: ExerciseEntity, val sets: Int, val target: Int, val rest: Int, val linked: Boolean)

@HiltViewModel
class TemplateEditViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val library: LibraryRepository,
    private val workouts: WorkoutRepository,
) : ViewModel() {
    val id = handle.toRoute<TemplateEditRoute>().id
    var name by mutableStateOf("")
    var description by mutableStateOf("")
    var items by mutableStateOf(listOf<ItemDraft>())
    private var template: TemplateEntity? = null
    var done by mutableStateOf(false)
    var error by mutableStateOf(false)
    var startedSession by mutableStateOf<Long?>(null)
    val exercises = library.exercises.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        if (id != 0L) viewModelScope.launch {
            library.template(id)?.let { t ->
                template = t.template
                name = t.template.name
                description = t.template.description
                var prevGroup: Int? = null
                items = t.items.map { iv ->
                    val g = iv.item.supersetGroup
                    val linked = g != null && g == prevGroup
                    prevGroup = g
                    ItemDraft(iv.exercise, iv.item.targetSets, iv.item.targetRepsOrSec, iv.item.restSec, linked)
                }
            }
        }
    }

    fun move(i: Int, d: Int) {
        val l = items.toMutableList()
        val j = (i + d).coerceIn(0, l.lastIndex)
        l.add(j, l.removeAt(i))
        items = l
    }

    /** "Linked" items join the previous item's superset group. */
    private fun toEntities(): List<TemplateItemEntity> {
        var group = 0
        var current: Int? = null
        return items.mapIndexed { i, d ->
            val nextLinked = items.getOrNull(i + 1)?.linked == true
            current = when {
                d.linked && current != null -> current
                nextLinked -> (++group)
                else -> null
            }
            TemplateItemEntity(templateId = 0, exerciseId = d.exercise.id, itemOrder = i, targetSets = d.sets, targetRepsOrSec = d.target, restSec = d.rest, supersetGroup = current)
        }
    }

    fun save(thenStart: Boolean = false) = viewModelScope.launch {
        if (name.isBlank() || items.isEmpty()) { error = true; return@launch }
        val t = (template ?: TemplateEntity(name = name)).copy(name = name.trim(), description = description.trim())
        val tid = library.saveTemplate(t, toEntities())
        if (thenStart) startedSession = workouts.start(templateId = tid) else done = true
    }

    fun delete() = viewModelScope.launch { template?.let { library.deleteTemplate(it.id) }; done = true }
}

@Composable
fun TemplateEditScreen(nav: NavHostController, vm: TemplateEditViewModel = hiltViewModel()) {
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    var picker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(vm.done) { if (vm.done) nav.popBackStack() }
    LaunchedEffect(vm.startedSession) { vm.startedSession?.let { nav.navigate(SessionRoute(it)) { popUpTo<TemplateEditRoute> { inclusive = true } } } }
    Scaffold(
        topBar = {
            GwTopBar(stringResource(if (vm.id == 0L) R.string.tpl_new else R.string.tpl_edit), onBack = { nav.popBackStack() }) {
                if (vm.id != 0L) IconButton({ confirmDelete = true }) { Icon(Icons.Rounded.Delete, stringResource(R.string.tpl_delete)) }
            }
        },
        bottomBar = {
            Row(Modifier.navigationBarsPadding().imePadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GameButton(stringResource(R.string.action_save), { vm.save() }, color = Gw.colors.train, icon = Icons.Rounded.Check, modifier = Modifier.weight(1f))
                GameButton(stringResource(R.string.action_start), { vm.save(thenStart = true) }, color = Gw.colors.success, icon = Icons.Rounded.PlayArrow, modifier = Modifier.weight(1f))
            }
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                OutlinedTextField(vm.name, { vm.name = it.take(40); vm.error = false }, label = { Text(stringResource(R.string.tpl_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(vm.description, { vm.description = it.take(200) }, label = { Text(stringResource(R.string.tpl_desc)) }, modifier = Modifier.fillMaxWidth())
                if (vm.error) Text(stringResource(R.string.tpl_required), color = Gw.colors.danger)
            }
            itemsIndexed(vm.items) { i, d ->
                GameCard(contentPadding = PaddingValues(12.dp), accent = if (d.linked) Gw.colors.plan else null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}. " + d.exercise.nameTh, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        IconButton({ vm.move(i, -1) }, enabled = i > 0) { Icon(Icons.Rounded.ArrowUpward, stringResource(R.string.sess_move_up)) }
                        IconButton({ vm.move(i, 1) }, enabled = i < vm.items.lastIndex) { Icon(Icons.Rounded.ArrowDownward, stringResource(R.string.sess_move_down)) }
                        IconButton({ vm.items = vm.items.filterIndexed { j, _ -> j != i } }) { Icon(Icons.Rounded.Delete, stringResource(R.string.action_delete)) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val unit = if (d.exercise.trackingType == TrackingType.HOLD || d.exercise.trackingType == TrackingType.CARDIO) stringResource(R.string.unit_sec) else stringResource(R.string.unit_reps)
                        SmallNumber(stringResource(R.string.tpl_sets), d.sets, Modifier.weight(1f)) { v -> vm.items = vm.items.mapIndexed { j, x -> if (j == i) x.copy(sets = v) else x } }
                        SmallNumber(stringResource(R.string.tpl_target) + " ($unit)", d.target, Modifier.weight(1f)) { v -> vm.items = vm.items.mapIndexed { j, x -> if (j == i) x.copy(target = v) else x } }
                        SmallNumber(stringResource(R.string.tpl_rest), d.rest, Modifier.weight(1f)) { v -> vm.items = vm.items.mapIndexed { j, x -> if (j == i) x.copy(rest = v) else x } }
                    }
                    if (i > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(d.linked, { c -> vm.items = vm.items.mapIndexed { j, x -> if (j == i) x.copy(linked = c) else x } })
                            Text(stringResource(R.string.tpl_link), style = MaterialTheme.typography.bodyMedium)
                            if (d.linked) { Spacer(Modifier.width(6.dp)); Pill("Superset", Gw.colors.plan) }
                        }
                    }
                }
            }
            item { TextButton({ picker = true }) { Icon(Icons.Rounded.Add, null); Text(stringResource(R.string.tpl_add_exercise)) } }
            item { Box(Modifier.padding(bottom = 24.dp)) }
        }
    }
    if (picker) {
        ExercisePickerDialog(exercises, LocalAppSettings.current.equipment, multi = true, onDismiss = { picker = false }) { picked ->
            picker = false
            vm.items = vm.items + picked.map {
                ItemDraft(it, 3, when (it.trackingType) { TrackingType.HOLD -> 30; TrackingType.CARDIO -> 45; else -> 10 }, it.defaultRestSec, false)
            }
        }
    }
    if (confirmDelete) ConfirmDialog(stringResource(R.string.tpl_delete), vm.name, stringResource(R.string.action_delete), { vm.delete() }, { confirmDelete = false }, destructive = true)
}

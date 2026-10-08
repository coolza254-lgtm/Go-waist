package com.gowaist.app.ui.run

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.toRoute
import com.gowaist.app.R
import com.gowaist.app.data.db.RunEntity
import com.gowaist.app.data.ocr.OcrService
import com.gowaist.app.data.repo.RunRepository
import com.gowaist.app.data.settings.SettingsRepository
import com.gowaist.app.data.toLocalDate
import com.gowaist.app.domain.ActivityCoordinator
import com.gowaist.app.ui.Fmt
import com.gowaist.app.ui.GwTopBar
import com.gowaist.app.ui.components.ConfirmDialog
import com.gowaist.app.ui.components.EmptyState
import com.gowaist.app.ui.components.GameButton
import com.gowaist.app.ui.components.LocalImage
import com.gowaist.app.ui.components.Mascot
import com.gowaist.app.ui.nav.RunEditRoute
import com.gowaist.app.ui.theme.Gw
import com.gowaist.core.mascot.MascotMood
import com.gowaist.core.ocr.ParsedRun
import com.gowaist.core.ocr.RunTextParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Shared save flow: required fields → duplicate check → store image → save → progress updates. */
class RunSaver(
    private val runs: RunRepository,
    private val coordinator: ActivityCoordinator,
) {
    sealed interface Result {
        data object Missing : Result
        data class Duplicate(val existing: RunEntity) : Result
        data class Saved(val id: Long) : Result
    }

    suspend fun save(form: RunForm, force: Boolean): Result {
        val draft = form.toEntity(form.existingImagePath) ?: return Result.Missing
        if (!force) {
            runs.findDuplicate(draft.startAt, draft.localDate, draft.distanceM, form.time != null, draft.id.takeIf { it != 0L })
                ?.let { return Result.Duplicate(it) }
        }
        var path = form.existingImagePath
        val uri = form.imageUri
        if (uri != null && form.keepImage && path == null) path = runs.keepImage(uri)
        if (!form.keepImage && form.existingImagePath != null) {
            runs.deleteImageFile(form.existingImagePath)
            path = null
        }
        val (id, records) = runs.save(draft.copy(sourceImagePath = path))
        coordinator.afterChange(records)
        return Result.Saved(id)
    }
}

@HiltViewModel
class RunEditViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val runs: RunRepository,
    private val settings: SettingsRepository,
    coordinator: ActivityCoordinator,
) : ViewModel() {
    private val runId = handle.toRoute<RunEditRoute>().runId
    private val saver = RunSaver(runs, coordinator)
    var form by mutableStateOf<RunForm?>(null)
    var duplicate by mutableStateOf<RunEntity?>(null)
    var missing by mutableStateOf(false)
    var done by mutableStateOf(false)
    val existingTags = runs.runs.map { list -> list.flatMap { it.tags }.distinct() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            val s = settings.current()
            val f = RunForm(s.distanceUnit)
            f.keepImage = s.keepRunImages
            if (runId != 0L) runs.get(runId)?.let { f.fill(it); f.keepImage = it.sourceImagePath != null || s.keepRunImages }
            form = f
        }
    }

    fun save(force: Boolean = false) = viewModelScope.launch {
        val f = form ?: return@launch
        when (val r = saver.save(f, force)) {
            RunSaver.Result.Missing -> missing = true
            is RunSaver.Result.Duplicate -> duplicate = r.existing
            is RunSaver.Result.Saved -> done = true
        }
    }
}

@Composable
fun RunEditScreen(nav: NavHostController, vm: RunEditViewModel = hiltViewModel()) {
    val form = vm.form
    val tags by vm.existingTags.collectAsStateWithLifecycle()
    LaunchedEffect(vm.done) { if (vm.done) nav.popBackStack() }
    Scaffold(
        topBar = { GwTopBar(stringResource(if (form?.id ?: 0L != 0L) R.string.run_edit_title else R.string.run_new_title), onBack = { nav.popBackStack() }) },
        bottomBar = {
            Box(Modifier.navigationBarsPadding().imePadding().padding(16.dp)) {
                GameButton(stringResource(R.string.action_save), { vm.save() }, color = Gw.colors.run, icon = Icons.Rounded.Check, big = true, modifier = Modifier.fillMaxWidth())
            }
        },
    ) { pad ->
        if (form == null) return@Scaffold
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            if (form.existingImagePath != null) {
                LocalImage(form.existingImagePath, null, Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
                KeepImageSwitch(form)
            }
            RunFormContent(form, tags)
            Spacer(Modifier.height(24.dp))
        }
    }
    RunDialogs(vm.duplicate, vm.missing, onForce = { vm.duplicate = null; vm.save(force = true) }, onDismissDup = { vm.duplicate = null }, onDismissMissing = { vm.missing = false })
}

@Composable
fun RunDialogs(duplicate: RunEntity?, missing: Boolean, onForce: () -> Unit, onDismissDup: () -> Unit, onDismissMissing: () -> Unit) {
    duplicate?.let { d ->
        ConfirmDialog(
            title = stringResource(R.string.run_dup_title),
            message = stringResource(R.string.run_dup_text, Fmt.dateMedium(d.localDate.toLocalDate()), Fmt.distance(d.distanceM)),
            confirmText = stringResource(R.string.run_dup_save),
            onConfirm = onForce,
            onDismiss = onDismissDup,
        )
    }
    if (missing) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onDismissMissing,
            confirmButton = { TextButton(onDismissMissing) { Text(stringResource(R.string.action_ok)) } },
            text = { Text(stringResource(R.string.run_required)) },
        )
    }
}

// --------------------------------------------------------------------------------------- import

data class ImportItem(val uri: Uri, val parsed: ParsedRun?)

@HiltViewModel
class RunImportViewModel @Inject constructor(
    private val runs: RunRepository,
    private val settings: SettingsRepository,
    private val ocr: OcrService,
    coordinator: ActivityCoordinator,
) : ViewModel() {
    private val saver = RunSaver(runs, coordinator)
    var items by mutableStateOf<List<ImportItem>>(emptyList())
    var index by mutableStateOf(0)
    var reading by mutableStateOf(false)
    var form by mutableStateOf<RunForm?>(null)
    var duplicate by mutableStateOf<RunEntity?>(null)
    var missing by mutableStateOf(false)
    var saved by mutableStateOf(0)
    val existingTags = runs.runs.map { list -> list.flatMap { it.tags }.distinct() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val finished: Boolean get() = items.isNotEmpty() && index >= items.size

    fun onPicked(uris: List<Uri>) = viewModelScope.launch {
        if (uris.isEmpty()) return@launch
        reading = true
        val parser = RunTextParser(LocalDate.now())
        items = uris.map { uri ->
            val parsed = runCatching { parser.parseLines(ocr.readLines(uri)) }.getOrNull()?.takeUnless { it.isEmpty }
            ImportItem(uri, parsed)
        }
        index = 0
        reading = false
        loadCurrent()
    }

    private suspend fun loadCurrent() {
        val item = items.getOrNull(index) ?: run { form = null; return }
        val s = settings.current()
        form = RunForm(s.distanceUnit).apply {
            item.parsed?.let { fill(it) } ?: run { lowConfidence = RunField.entries.toSet() - RunField.PACE }
            imageUri = item.uri
            keepImage = s.keepRunImages
        }
    }

    fun save(force: Boolean = false) = viewModelScope.launch {
        val f = form ?: return@launch
        when (val r = saver.save(f, force)) {
            RunSaver.Result.Missing -> missing = true
            is RunSaver.Result.Duplicate -> duplicate = r.existing
            is RunSaver.Result.Saved -> { saved++; next() }
        }
    }

    fun next() = viewModelScope.launch {
        index++
        loadCurrent()
    }
}

@Composable
fun RunImportScreen(nav: NavHostController, vm: RunImportViewModel = hiltViewModel()) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { vm.onPicked(it) }
    val tags by vm.existingTags.collectAsStateWithLifecycle()
    var zoom by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (vm.items.isEmpty()) picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    val form = vm.form
    Scaffold(
        topBar = {
            GwTopBar(
                if (form != null) stringResource(R.string.run_review_title, vm.index + 1, vm.items.size) else stringResource(R.string.run_import),
                onBack = { nav.popBackStack() },
            ) {
                if (form != null) TextButton({ vm.next() }) { Text(stringResource(R.string.run_skip_image)) }
            }
        },
        bottomBar = {
            if (form != null) {
                Box(Modifier.navigationBarsPadding().imePadding().padding(16.dp)) {
                    GameButton(stringResource(R.string.action_save), { vm.save() }, color = Gw.colors.run, icon = Icons.Rounded.Check, big = true, modifier = Modifier.fillMaxWidth())
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when {
                vm.reading -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Mascot(MascotMood.CHEER, Modifier.size(140.dp))
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.run_reading), style = MaterialTheme.typography.titleMedium)
                }
                vm.finished -> EmptyState(
                    title = stringResource(R.string.run_all_done),
                    message = stringResource(R.string.run_count, vm.saved),
                    mood = MascotMood.PROUD,
                    actionText = stringResource(R.string.action_done),
                    onAction = { nav.popBackStack() },
                    modifier = Modifier.align(Alignment.Center),
                )
                form != null -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    val item = vm.items[vm.index]
                    LocalImage(
                        item.uri, stringResource(R.string.run_view_image),
                        Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp)).clickable { zoom = true },
                        contentScale = ContentScale.Crop,
                    )
                    if (item.parsed == null) Text(stringResource(R.string.run_ocr_failed), color = Gw.colors.warning, modifier = Modifier.padding(top = 8.dp))
                    KeepImageSwitch(form)
                    RunFormContent(form, tags)
                    Spacer(Modifier.height(24.dp))
                }
                else -> Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Mascot(MascotMood.HAPPY, Modifier.size(140.dp))
                    Text(stringResource(R.string.run_pick_images), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.run_pick_text), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    GameButton(stringResource(R.string.run_pick_images), { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, color = Gw.colors.run, icon = Icons.Rounded.PhotoLibrary, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
    if (zoom && form != null) {
        Dialog({ zoom = false }, DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().clickable { zoom = false }, contentAlignment = Alignment.Center) {
                LocalImage(vm.items[vm.index].uri, null, Modifier.fillMaxSize(), maxSize = 2400)
            }
        }
    }
    RunDialogs(vm.duplicate, vm.missing, onForce = { vm.duplicate = null; vm.save(force = true) }, onDismissDup = { vm.duplicate = null }, onDismissMissing = { vm.missing = false })
}

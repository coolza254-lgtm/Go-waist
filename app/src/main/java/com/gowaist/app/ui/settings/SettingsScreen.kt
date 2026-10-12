package com.gowaist.app.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.gowaist.app.BuildConfig
import com.gowaist.app.R
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.OutlinedButton
import com.gowaist.app.ui.perf.ProfileDialog
import com.gowaist.app.data.repo.BackupRepository
import com.gowaist.app.data.settings.AppSettings
import com.gowaist.app.data.settings.SettingsRepository
import com.gowaist.app.data.settings.ThemeMode
import com.gowaist.app.domain.ActivityCoordinator
import com.gowaist.app.ui.GwTopBar
import com.gowaist.app.ui.components.ConfirmDialog
import com.gowaist.app.ui.components.GameButton
import com.gowaist.app.ui.components.GameCard
import com.gowaist.app.ui.theme.Gw
import com.gowaist.core.DistanceUnit
import com.gowaist.core.Format
import com.gowaist.core.LengthUnit
import com.gowaist.core.Units
import com.gowaist.core.WeightUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val repo: SettingsRepository,
    private val backup: BackupRepository,
    private val coordinator: ActivityCoordinator,
) : ViewModel() {
    val settings = repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    var message by mutableStateOf<String?>(null)

    fun update(f: (AppSettings) -> AppSettings) = viewModelScope.launch {
        repo.update(f)
    }

    private fun run(done: String, block: suspend () -> Unit) = viewModelScope.launch {
        message = runCatching { block(); done }.getOrElse { context.getString(R.string.error_generic, it.message ?: it.javaClass.simpleName) }
    }

    fun export(uri: android.net.Uri, done: String) = run(done) { backup.exportTo(uri) }
    fun import(uri: android.net.Uri, done: String) = run(done) { backup.importFrom(uri); coordinator.afterChange() }
    fun runsCsv(uri: android.net.Uri, done: String) = run(done) { backup.writeText(uri, backup.runsCsv()) }
    fun wipe(done: String) = run(done) { backup.wipeAll(); coordinator.afterChange() }
}

@Composable
fun SettingsScreen(nav: NavHostController, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snack = remember { SnackbarHostState() }
    val today = LocalDate.now()
    val doneExport = stringResource(R.string.set_done_export)
    val doneImport = stringResource(R.string.set_done_import)
    val doneCsv = stringResource(R.string.set_done_csv)
    val doneWipe = stringResource(R.string.set_done_wipe)
    var pendingImport by remember { mutableStateOf<android.net.Uri?>(null) }
    var wipeStep by remember { mutableStateOf(0) }
    var editProfile by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let { u -> vm.export(u, doneExport) } }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { pendingImport = it }
    val runsCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let { u -> vm.runsCsv(u, doneCsv) } }

    LaunchedEffect(vm.message) { vm.message?.let { snack.showSnackbar(it); vm.message = null } }

    Scaffold(topBar = { GwTopBar(stringResource(R.string.set_title), onBack = { nav.popBackStack() }) }, snackbarHost = { SnackbarHost(snack) }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Section(stringResource(R.string.set_profile)) {
                OutlinedButton({ editProfile = true }, Modifier.fillMaxWidth().testTag("open_profile")) {
                    Text(
                        stringResource(R.string.profile_title) + listOfNotNull(
                            s.age?.let { " · " + it },
                            s.heightCm?.let { " · " + Format.decimal(Units.cmTo(s.lengthUnit, it), 0) },
                        ).joinToString(""),
                    )
                }
            }
            Section(stringResource(R.string.set_units)) {
                Seg(listOf(DistanceUnit.KM to R.string.unit_km, DistanceUnit.MI to R.string.unit_mi), s.distanceUnit) { u -> vm.update { it.copy(distanceUnit = u) } }
                Seg(listOf(WeightUnit.KG to R.string.unit_kg, WeightUnit.LB to R.string.unit_lb), s.weightUnit) { u -> vm.update { it.copy(weightUnit = u) } }
                Seg(listOf(LengthUnit.CM to R.string.unit_cm, LengthUnit.IN to R.string.unit_in), s.lengthUnit) { u -> vm.update { it.copy(lengthUnit = u) } }
            }
            Section(stringResource(R.string.set_display)) {
                Seg(listOf(ThemeMode.SYSTEM to R.string.set_theme_system, ThemeMode.LIGHT to R.string.set_theme_light, ThemeMode.DARK to R.string.set_theme_dark), s.themeMode) { m -> vm.update { it.copy(themeMode = m) } }
            }
            Section(stringResource(R.string.set_modules)) {
                Toggle(stringResource(R.string.set_body_module), s.bodyMetricsEnabled) { v -> vm.update { it.copy(bodyMetricsEnabled = v) } }
                Toggle(stringResource(R.string.set_keep_images), s.keepRunImages) { v -> vm.update { it.copy(keepRunImages = v) } }
            }
            Section(stringResource(R.string.set_data)) {
                ActionRow(Icons.Rounded.Backup, stringResource(R.string.set_export), stringResource(R.string.set_export_text)) { exportLauncher.launch("gowaist-backup-$today.json") }
                ActionRow(Icons.Rounded.Restore, stringResource(R.string.set_import), stringResource(R.string.set_import_text)) { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }
                ActionRow(Icons.Rounded.Description, stringResource(R.string.set_csv_runs), null) { runsCsv.launch("gowaist-runs-$today.csv") }
                Spacer(Modifier.height(4.dp))
                GameButton(stringResource(R.string.set_wipe), { wipeStep = 1 }, color = Gw.colors.danger, icon = Icons.Rounded.DeleteForever, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.set_wipe_text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Section(stringResource(R.string.set_about)) {
                Text(stringResource(R.string.set_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.set_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    pendingImport?.let { uri ->
        ConfirmDialog(stringResource(R.string.set_import_confirm), stringResource(R.string.set_import_confirm_text), stringResource(R.string.action_confirm), { vm.import(uri, doneImport) }, { pendingImport = null }, destructive = true)
    }
    if (wipeStep == 1) {
        ConfirmDialog(stringResource(R.string.set_wipe), stringResource(R.string.set_wipe_confirm_1), stringResource(R.string.action_next), { wipeStep = 2 }, { if (wipeStep == 1) wipeStep = 0 }, destructive = true)
    }
    if (wipeStep == 2) {
        var typed by remember { mutableStateOf("") }
        val word = stringResource(R.string.set_wipe_word)
        AlertDialog(
            onDismissRequest = { wipeStep = 0 },
            title = { Text(stringResource(R.string.set_wipe)) },
            text = {
                Column {
                    Text(stringResource(R.string.set_wipe_confirm_2))
                    OutlinedTextField(typed, { typed = it }, singleLine = true)
                }
            },
            confirmButton = { TextButton({ wipeStep = 0; vm.wipe(doneWipe) }, enabled = typed.trim() == word) { Text(stringResource(R.string.action_delete), color = Gw.colors.danger) } },
            dismissButton = { TextButton({ wipeStep = 0 }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (editProfile) {
        ProfileDialog(s, onSave = { p -> vm.update { it.copy(age = p.age, sex = p.sex, heightCm = p.heightCm, restHr = p.restHr, maxHr = p.maxHr) }; editProfile = false }, onDismiss = { editProfile = false })
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    GameCard(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(value, onChange)
    }
}

@Composable
private fun <T> Seg(options: List<Pair<T, Int>>, selected: T, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (v, l) -> SegmentedButton(selected == v, { onSelect(v) }, SegmentedButtonDefaults.itemShape(i, options.size)) { Text(stringResource(l)) } }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

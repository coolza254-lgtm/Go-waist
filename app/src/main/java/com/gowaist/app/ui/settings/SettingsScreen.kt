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
import androidx.compose.material.icons.rounded.NotificationsActive
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
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
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
import com.gowaist.app.data.repo.BackupRepository
import com.gowaist.app.data.settings.AppSettings
import com.gowaist.app.data.settings.SettingsRepository
import com.gowaist.app.data.settings.ThemeMode
import com.gowaist.app.domain.ActivityCoordinator
import com.gowaist.app.notify.Notifications
import com.gowaist.app.notify.ReminderWorker
import com.gowaist.app.ui.GwTopBar
import com.gowaist.app.ui.components.ConfirmDialog
import com.gowaist.app.ui.components.GameButton
import com.gowaist.app.ui.components.GameCard
import com.gowaist.app.ui.components.labelRes
import com.gowaist.app.ui.theme.Gw
import com.gowaist.core.DistanceUnit
import com.gowaist.core.Format
import com.gowaist.core.LengthUnit
import com.gowaist.core.Units
import com.gowaist.core.WeightUnit
import com.gowaist.core.model.Equipment
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
        val s = repo.current()
        ReminderWorker.schedule(context, s.reminderHour, s.reminderMinute)
    }

    private fun run(done: String, block: suspend () -> Unit) = viewModelScope.launch {
        message = runCatching { block(); done }.getOrElse { context.getString(R.string.error_generic, it.message ?: it.javaClass.simpleName) }
    }

    fun export(uri: android.net.Uri, done: String) = run(done) { backup.exportTo(uri) }
    fun import(uri: android.net.Uri, done: String) = run(done) { backup.importFrom(uri); coordinator.afterChange(celebrate = false) }
    fun runsCsv(uri: android.net.Uri, done: String) = run(done) { backup.writeText(uri, backup.runsCsv()) }
    fun setsCsv(uri: android.net.Uri, done: String) = run(done) { backup.writeText(uri, backup.setsCsv()) }
    fun wipe(done: String) = run(done) { backup.wipeAll(); coordinator.afterChange(celebrate = false) }
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
    var pickTime by remember { mutableStateOf(false) }
    var notifAllowed by remember { mutableStateOf(Notifications.canPost(context)) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let { u -> vm.export(u, doneExport) } }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { pendingImport = it }
    val runsCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let { u -> vm.runsCsv(u, doneCsv) } }
    val setsCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let { u -> vm.setsCsv(u, doneCsv) } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifAllowed = it }

    LaunchedEffect(vm.message) { vm.message?.let { snack.showSnackbar(it); vm.message = null } }

    Scaffold(topBar = { GwTopBar(stringResource(R.string.set_title), onBack = { nav.popBackStack() }) }, snackbarHost = { SnackbarHost(snack) }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Section(stringResource(R.string.set_profile)) {
                var nick by remember(s.nickname) { mutableStateOf(s.nickname) }
                OutlinedTextField(nick, { nick = it.take(24); vm.update { st -> st.copy(nickname = nick) } }, label = { Text(stringResource(R.string.set_nickname)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                var bw by remember(s.bodyweightKg, s.weightUnit) { mutableStateOf(Format.decimal(Units.kgTo(s.weightUnit, s.bodyweightKg), 1)) }
                OutlinedTextField(
                    bw, { t -> bw = t.filter { it.isDigit() || it == '.' }.take(5); bw.toDoubleOrNull()?.takeIf { it in 20.0..660.0 }?.let { v -> vm.update { st -> st.copy(bodyweightKg = Units.toKg(st.weightUnit, v)) } } },
                    label = { Text(stringResource(R.string.set_bodyweight)) }, supportingText = { Text(stringResource(R.string.set_bodyweight_text)) },
                    suffix = { Text(stringResource(if (s.weightUnit == WeightUnit.KG) R.string.unit_kg else R.string.unit_lb)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.set_equipment), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Equipment.entries.filter { it != Equipment.NONE }.forEach { e ->
                        val on = e in s.equipment
                        FilterChip(on, { vm.update { st -> st.copy(equipment = if (on) st.equipment - e else st.equipment + e) } }, label = { Text(stringResource(e.labelRes())) })
                    }
                }
            }
            Section(stringResource(R.string.set_units)) {
                Seg(listOf(DistanceUnit.KM to R.string.unit_km, DistanceUnit.MI to R.string.unit_mi), s.distanceUnit) { u -> vm.update { it.copy(distanceUnit = u) } }
                Seg(listOf(WeightUnit.KG to R.string.unit_kg, WeightUnit.LB to R.string.unit_lb), s.weightUnit) { u -> vm.update { it.copy(weightUnit = u) } }
                Seg(listOf(LengthUnit.CM to R.string.unit_cm, LengthUnit.IN to R.string.unit_in), s.lengthUnit) { u -> vm.update { it.copy(lengthUnit = u) } }
            }
            Section(stringResource(R.string.set_training)) {
                Text(stringResource(R.string.set_default_rest), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(30, 45, 60, 90, 120, 180).forEach { r -> FilterChip(s.defaultRestSec == r, { vm.update { it.copy(defaultRestSec = r) } }, label = { Text(stringResource(R.string.set_sec, r)) }) }
                }
                Toggle(stringResource(R.string.set_keep_screen), s.keepScreenOn) { v -> vm.update { it.copy(keepScreenOn = v) } }
                Toggle(stringResource(R.string.set_haptics), s.hapticsEnabled) { v -> vm.update { it.copy(hapticsEnabled = v) } }
                Toggle(stringResource(R.string.set_sound), s.soundEnabled) { v -> vm.update { it.copy(soundEnabled = v) } }
            }
            Section(stringResource(R.string.set_reminders)) {
                Toggle(stringResource(R.string.set_reminders), s.remindersEnabled) { v -> vm.update { it.copy(remindersEnabled = v) } }
                Text(stringResource(R.string.set_reminder_text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth().clickable { pickTime = true }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.set_reminder_time), Modifier.weight(1f))
                    Text(String.format(java.util.Locale.US, "%02d:%02d", s.reminderHour, s.reminderMinute), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
                if (!notifAllowed && Build.VERSION.SDK_INT >= 33) {
                    TextButton({ permission.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                        Icon(Icons.Rounded.NotificationsActive, null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.set_notifications_off))
                    }
                }
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
                ActionRow(Icons.Rounded.Description, stringResource(R.string.set_csv_sets), null) { setsCsv.launch("gowaist-sets-$today.csv") }
                Spacer(Modifier.height(4.dp))
                GameButton(stringResource(R.string.set_wipe), { wipeStep = 1 }, color = Gw.colors.danger, icon = Icons.Rounded.DeleteForever, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.set_wipe_text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Section(stringResource(R.string.set_about)) {
                Text(stringResource(R.string.set_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.set_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton({ vm.update { it.copy(onboardingDone = false) } }) { Text(stringResource(R.string.set_onboarding)) }
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
    if (pickTime) {
        val state = rememberTimePickerState(s.reminderHour, s.reminderMinute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = { TextButton({ vm.update { it.copy(reminderHour = state.hour, reminderMinute = state.minute) }; pickTime = false }) { Text(stringResource(R.string.action_ok)) } },
            dismissButton = { TextButton({ pickTime = false }) { Text(stringResource(R.string.action_cancel)) } },
            text = { TimePicker(state) },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    GameCard {
        Text(title, style = MaterialTheme.typography.titleLarge)
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

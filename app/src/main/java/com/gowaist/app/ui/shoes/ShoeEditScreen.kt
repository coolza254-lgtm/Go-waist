package com.gowaist.app.ui.shoes

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.toRoute
import com.gowaist.app.R
import com.gowaist.app.data.db.ShoeEntity
import com.gowaist.app.data.key
import com.gowaist.app.data.repo.ShoeRepository
import com.gowaist.app.data.repo.ShoeWithStats
import com.gowaist.app.data.toLocalDate
import com.gowaist.app.ui.Fmt
import com.gowaist.app.ui.GwTopBar
import com.gowaist.app.ui.components.ConfirmDialog
import com.gowaist.app.ui.components.GameButton
import com.gowaist.app.ui.components.GameCard
import com.gowaist.app.ui.components.HpBar
import com.gowaist.app.ui.components.LocalImage
import com.gowaist.app.ui.components.NumberStepper
import com.gowaist.app.ui.components.StatBlock
import com.gowaist.app.ui.components.panel
import com.gowaist.app.ui.nav.ShoeEditRoute
import com.gowaist.app.ui.theme.Gw
import com.gowaist.core.Format
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class ShoeEditViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val repo: ShoeRepository,
) : ViewModel() {
    val id = handle.toRoute<ShoeEditRoute>().id
    var name by mutableStateOf("")
    var price by mutableStateOf("")
    var purchaseDate by mutableStateOf<LocalDate?>(LocalDate.now())
    var durability by mutableStateOf<Int?>(100)
    var isMain by mutableStateOf(false)
    var retired by mutableStateOf(false)
    var note by mutableStateOf("")
    var newImage by mutableStateOf<Uri?>(null)
    var stats by mutableStateOf<ShoeWithStats?>(null)
        private set
    var invalid by mutableStateOf(false)
    var done by mutableStateOf(false)
        private set
    private var existing: ShoeEntity? = null

    init {
        viewModelScope.launch {
            if (id == 0L) {
                isMain = repo.main() == null
                return@launch
            }
            val s = repo.get(id) ?: return@launch
            existing = s
            name = s.name
            price = s.priceBaht?.let { Format.decimal(it, 0) }.orEmpty()
            purchaseDate = s.purchaseDate?.toLocalDate()
            durability = s.durabilityPct
            isMain = s.isMain
            retired = s.retired
            note = s.note
            stats = repo.shoes.first().firstOrNull { it.shoe.id == id }
        }
    }

    val imagePath: String? get() = existing?.imagePath

    fun save() = viewModelScope.launch {
        if (name.isBlank()) { invalid = true; return@launch }
        val base = existing ?: ShoeEntity(name = "")
        repo.save(
            base.copy(
                name = name.trim(),
                priceBaht = price.replace(",", "").toDoubleOrNull()?.takeIf { it > 0 },
                purchaseDate = purchaseDate?.key(),
                durabilityPct = durability,
                isMain = isMain && !retired,
                retired = retired,
                note = note.trim(),
            ),
            newImage,
        )
        done = true
    }

    fun delete() = viewModelScope.launch {
        repo.delete(id)
        done = true
    }
}

@Composable
fun ShoeEditScreen(nav: NavHostController, vm: ShoeEditViewModel = hiltViewModel()) {
    var pickDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.newImage = uri }
    LaunchedEffect(vm.done) { if (vm.done) nav.popBackStack() }
    Scaffold(
        topBar = {
            GwTopBar(stringResource(if (vm.id == 0L) R.string.shoe_add else R.string.shoe_edit), onBack = { nav.popBackStack() }) {
                if (vm.id != 0L) IconButton({ confirmDelete = true }) { Icon(Icons.Rounded.Delete, stringResource(R.string.action_delete)) }
            }
        },
        bottomBar = {
            Box(Modifier.navigationBarsPadding().imePadding().padding(16.dp)) {
                GameButton(stringResource(R.string.action_save), vm::save, icon = Icons.Rounded.Check, big = true, modifier = Modifier.fillMaxWidth().testTag("shoe_save"))
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.fillMaxWidth().height(170.dp).panel(MaterialTheme.colorScheme.surface, Gw.colors.ink, shadow = 0.dp)
                    .clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                contentAlignment = Alignment.Center,
            ) {
                val src: Any? = vm.newImage ?: vm.imagePath
                if (src != null) {
                    LocalImage(src, stringResource(R.string.shoe_photo), Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)), maxSize = 960, contentScale = ContentScale.Crop)
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.PhotoCamera, null, tint = Gw.colors.muted, modifier = Modifier.size(32.dp))
                        Text(stringResource(R.string.shoe_add_photo), color = Gw.colors.muted, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            vm.stats?.let { s ->
                GameCard(Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatBlock(stringResource(R.string.shoe_km_used), Fmt.distanceValue(s.stats.distanceM, 0), Modifier.weight(1f))
                        StatBlock(stringResource(R.string.shoe_cost_per_km), s.stats.costPerKm?.let { baht(it, 1) } ?: "-", Modifier.weight(1f))
                        StatBlock(stringResource(R.string.shoe_runs), "${s.stats.runs}", Modifier.weight(1f))
                    }
                }
            }

            OutlinedTextField(
                vm.name, { vm.name = it.take(40); vm.invalid = false }, label = { Text(stringResource(R.string.shoe_name)) },
                singleLine = true, isError = vm.invalid, modifier = Modifier.fillMaxWidth().testTag("shoe_name"),
                supportingText = if (vm.invalid) { { Text(stringResource(R.string.shoe_name_required)) } } else null,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    vm.price, { vm.price = it.filter { c -> c.isDigit() }.take(6) }, label = { Text(stringResource(R.string.shoe_price)) },
                    prefix = { Text("฿") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f),
                )
                OutlinedButton({ pickDate = true }, Modifier.weight(1f).height(56.dp)) {
                    Icon(Icons.Rounded.CalendarMonth, null)
                    Spacer(Modifier.width(6.dp))
                    Text(vm.purchaseDate?.let { Fmt.dateMedium(it) } ?: stringResource(R.string.shoe_bought_unknown), maxLines = 1)
                }
            }

            GameCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.shoe_durability), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.shoe_durability_hint), style = MaterialTheme.typography.bodySmall, color = Gw.colors.muted)
                    }
                    Switch(vm.durability != null, { vm.durability = if (it) 100 else null })
                }
                vm.durability?.let { d ->
                    Spacer(Modifier.height(8.dp))
                    NumberStepper("$d%", stringResource(R.string.shoe_durability), { vm.durability = (d - 5).coerceAtLeast(0) }, { vm.durability = (d + 5).coerceAtMost(100) })
                    Slider(d.toFloat(), { vm.durability = (it / 5).roundToInt() * 5 }, valueRange = 0f..100f, steps = 19)
                    HpBar(d)
                }
            }

            GameCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.shoe_set_main), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.shoe_set_main_hint), style = MaterialTheme.typography.bodySmall, color = Gw.colors.muted)
                    }
                    Switch(vm.isMain && !vm.retired, { vm.isMain = it }, enabled = !vm.retired)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.shoe_retire), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Switch(vm.retired, { vm.retired = it })
                }
            }
            OutlinedTextField(vm.note, { vm.note = it.take(200) }, label = { Text(stringResource(R.string.run_note)) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            Spacer(Modifier.height(16.dp))
        }
    }
    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = (vm.purchaseDate ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let { vm.purchaseDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickDate = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton({ vm.purchaseDate = null; pickDate = false }) { Text(stringResource(R.string.shoe_bought_unknown)) } },
        ) { DatePicker(state) }
    }
    if (confirmDelete) {
        ConfirmDialog(
            stringResource(R.string.shoe_delete_title), stringResource(R.string.shoe_delete_text), stringResource(R.string.action_delete),
            onConfirm = vm::delete, onDismiss = { confirmDelete = false }, destructive = true,
        )
    }
}

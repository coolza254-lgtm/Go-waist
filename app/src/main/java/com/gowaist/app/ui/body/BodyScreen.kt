package com.gowaist.app.ui.body

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.gowaist.app.ui.components.ChartSeries
import com.gowaist.app.ui.components.GameBanner
import com.gowaist.app.ui.components.GameButton
import com.gowaist.app.ui.components.Mascot
import com.gowaist.app.ui.components.MultiLineChart
import com.gowaist.app.ui.components.NumberStepper
import com.gowaist.app.ui.components.Pill
import com.gowaist.app.ui.perf.color
import com.gowaist.app.ui.perf.label
import com.gowaist.app.ui.theme.Palette
import com.gowaist.core.perf.WeightStats
import kotlin.math.roundToLong
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.gowaist.app.R
import com.gowaist.app.data.db.BodyMetricEntity
import com.gowaist.app.data.key
import com.gowaist.app.data.repo.BodyRepository
import com.gowaist.app.data.repo.GoalRepository
import com.gowaist.core.model.GoalStatus
import com.gowaist.core.model.GoalType
import com.gowaist.core.perf.WeightTrend
import kotlinx.coroutines.flow.map
import com.gowaist.app.data.toLocalDate
import com.gowaist.app.domain.ActivityCoordinator
import com.gowaist.app.ui.Fmt
import com.gowaist.app.ui.GwTopBar
import com.gowaist.app.ui.LocalAppSettings
import com.gowaist.app.ui.components.ChartPoint
import com.gowaist.app.ui.components.ConfirmDialog
import com.gowaist.app.ui.components.EmptyState
import com.gowaist.app.ui.components.GameCard
import com.gowaist.app.ui.components.LineChart
import com.gowaist.app.ui.components.StatTile
import com.gowaist.app.ui.components.label
import com.gowaist.app.ui.theme.Gw
import com.gowaist.core.Format
import com.gowaist.core.Units
import com.gowaist.core.mascot.MascotMood
import com.gowaist.core.stats.Smoothing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject

@HiltViewModel
class BodyViewModel @Inject constructor(
    private val body: BodyRepository,
    private val coordinator: ActivityCoordinator,
    goals: GoalRepository,
) : ViewModel() {
    val metrics = body.metrics.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    /** Target of the active body-weight goal, if any. */
    val goalKg = goals.goals.map { l -> l.firstOrNull { it.goal.type == GoalType.BODY_WEIGHT && it.goal.status == GoalStatus.ACTIVE }?.goal?.target }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun quickWeight(kg: Double) = save(BodyMetricEntity(date = LocalDate.now().key(), weightKg = kg))

    fun save(m: BodyMetricEntity) = viewModelScope.launch { body.save(m); coordinator.afterChange() }
    fun delete(id: Long) = viewModelScope.launch { body.delete(id); coordinator.afterChange(celebrate = false) }
}

@Composable
fun BodyScreen(nav: NavHostController, vm: BodyViewModel = hiltViewModel()) {
    val list by vm.metrics.collectAsStateWithLifecycle()
    val goalKg by vm.goalKg.collectAsStateWithLifecycle()
    val s = LocalAppSettings.current
    var adding by remember { mutableStateOf(false) }
    var waist by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<BodyMetricEntity?>(null) }
    Scaffold(
        topBar = { GwTopBar(stringResource(R.string.weight_title), onBack = { nav.popBackStack() }) },
        floatingActionButton = { ExtendedFloatingActionButton(modifier = Modifier.testTag("fab_body"), onClick = { adding = true }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text(stringResource(R.string.weight_more)) }, containerColor = Gw.colors.goal, contentColor = MaterialTheme.colorScheme.surface) },
    ) { pad ->
        if (!s.bodyMetricsEnabled) {
            EmptyState(stringResource(R.string.body_title), stringResource(R.string.body_disabled), Modifier.padding(pad), mood = MascotMood.SLEEPY)
            return@Scaffold
        }
        val weights = remember(list) { list.mapNotNull { m -> m.weightKg?.let { m.date.toLocalDate() to it } }.sortedBy { it.first } }
        val waists = remember(list) { list.mapNotNull { m -> m.waistCm?.let { m.date.toLocalDate() to it } }.sortedBy { it.first } }
        val stats = remember(weights, s.heightCm, goalKg) { WeightTrend.stats(weights, s.heightCm, goalKg, LocalDate.now()) }
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item(key = "weight_banner") { WeightBanner(stats, goalKg) }
            item(key = "weight_quick") { QuickWeight(weights.lastOrNull()?.second ?: s.bodyweightKg, vm::quickWeight) }
            if (weights.size >= 2) item(key = "weight_chart") { WeightChart(weights, goalKg) }
            if (list.isEmpty()) {
                item(key = "empty") { EmptyState(stringResource(R.string.body_empty_title), stringResource(R.string.body_empty_text), mood = MascotMood.CALM) }
                return@LazyColumn
            }
            item(key = "tiles") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(stringResource(R.string.body_weight), weights.lastOrNull()?.let { Format.decimal(Units.kgTo(s.weightUnit, it.second), 1) } ?: "-", Modifier.weight(1f), unit = s.weightUnit.label(), icon = Icons.Rounded.MonitorWeight, color = Gw.colors.goal)
                    StatTile(stringResource(R.string.body_waist), waists.lastOrNull()?.let { Format.decimal(Units.cmTo(s.lengthUnit, it.second), 1) } ?: "-", Modifier.weight(1f), unit = s.lengthUnit.label(), icon = Icons.Rounded.Straighten, color = Gw.colors.run)
                }
            }
            item(key = "trend") {
                GameCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.body_trend), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    }
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        SegmentedButton(!waist, { waist = false }, SegmentedButtonDefaults.itemShape(0, 2)) { Text(stringResource(R.string.body_weight)) }
                        SegmentedButton(waist, { waist = true }, SegmentedButtonDefaults.itemShape(1, 2)) { Text(stringResource(R.string.body_waist)) }
                    }
                    val series = (if (waist) waists else weights).takeLast(60)
                    if (series.size >= 2) {
                        val conv: (Double) -> Double = { v -> if (waist) Units.cmTo(s.lengthUnit, v) else Units.kgTo(s.weightUnit, v) }
                        val smooth = Smoothing.movingAverage(series, 7)
                        LineChart(
                            series.map { ChartPoint(Fmt.dayMonth(it.first), conv(it.second).toFloat()) },
                            secondary = smooth.map { conv(it.second).toFloat() },
                            color = if (waist) Gw.colors.run.copy(alpha = 0.5f) else Gw.colors.goal.copy(alpha = 0.5f),
                            secondaryColor = if (waist) Gw.colors.run else Gw.colors.goal,
                            description = stringResource(R.string.body_trend),
                        )
                    } else {
                        Text("—", style = MaterialTheme.typography.headlineMedium)
                    }
                }
            }
            item(key = "history") { Text(stringResource(R.string.body_history), style = MaterialTheme.typography.titleLarge) }
            items(list, key = { it.id }) { m ->
                GameCard(contentPadding = PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(Fmt.dateShort(m.date.toLocalDate()), style = MaterialTheme.typography.titleSmall)
                            Text(
                                listOfNotNull(
                                    m.weightKg?.let { Fmt.weight(it) },
                                    m.waistCm?.let { stringResource(R.string.body_waist) + " " + Fmt.length(it) },
                                    m.hipCm?.let { stringResource(R.string.body_hip) + " " + Fmt.length(it) },
                                    m.chestCm?.let { stringResource(R.string.body_chest) + " " + Fmt.length(it) },
                                    m.armCm?.let { stringResource(R.string.body_arm) + " " + Fmt.length(it) },
                                    m.thighCm?.let { stringResource(R.string.body_thigh) + " " + Fmt.length(it) },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (m.note.isNotBlank()) Text(m.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton({ deleting = m }) { Icon(Icons.Rounded.Delete, stringResource(R.string.action_delete)) }
                    }
                }
            }
        }
    }
    if (adding) AddMetricDialog(onDismiss = { adding = false }) { vm.save(it); adding = false }
    deleting?.let { m -> ConfirmDialog(stringResource(R.string.body_delete), Fmt.dateMedium(m.date.toLocalDate()), stringResource(R.string.action_delete), { vm.delete(m.id) }, { deleting = null }, destructive = true) }
}

@Composable
private fun AddMetricDialog(onDismiss: () -> Unit, onSave: (BodyMetricEntity) -> Unit) {
    val s = LocalAppSettings.current
    var date by remember { mutableStateOf(LocalDate.now()) }
    var weight by remember { mutableStateOf("") }
    var waist by remember { mutableStateOf("") }
    var hip by remember { mutableStateOf("") }
    var chest by remember { mutableStateOf("") }
    var arm by remember { mutableStateOf("") }
    var thigh by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var pickDate by remember { mutableStateOf(false) }
    val dec = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    fun num(t: String) = t.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
    fun clean(t: String) = t.filter { it.isDigit() || it == '.' || it == ',' }.take(6)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.body_add)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton({ pickDate = true }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.CalendarMonth, null); Text(" " + Fmt.dateMedium(date)) }
                OutlinedTextField(weight, { weight = clean(it) }, label = { Text(stringResource(R.string.body_weight)) }, suffix = { Text(s.weightUnit.label()) }, keyboardOptions = dec, singleLine = true)
                OutlinedTextField(waist, { waist = clean(it) }, label = { Text(stringResource(R.string.body_waist)) }, suffix = { Text(s.lengthUnit.label()) }, keyboardOptions = dec, singleLine = true)
                Text(stringResource(R.string.body_other), style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(hip, { hip = clean(it) }, label = { Text(stringResource(R.string.body_hip)) }, suffix = { Text(s.lengthUnit.label()) }, keyboardOptions = dec, singleLine = true)
                OutlinedTextField(chest, { chest = clean(it) }, label = { Text(stringResource(R.string.body_chest)) }, suffix = { Text(s.lengthUnit.label()) }, keyboardOptions = dec, singleLine = true)
                OutlinedTextField(arm, { arm = clean(it) }, label = { Text(stringResource(R.string.body_arm)) }, suffix = { Text(s.lengthUnit.label()) }, keyboardOptions = dec, singleLine = true)
                OutlinedTextField(thigh, { thigh = clean(it) }, label = { Text(stringResource(R.string.body_thigh)) }, suffix = { Text(s.lengthUnit.label()) }, keyboardOptions = dec, singleLine = true)
                OutlinedTextField(note, { note = it.take(120) }, label = { Text(stringResource(R.string.body_note)) })
                Spacer(Modifier.height(4.dp))
            }
        },
        confirmButton = {
            TextButton({
                val cm = { t: String -> num(t)?.let { Units.toCm(s.lengthUnit, it) } }
                onSave(
                    BodyMetricEntity(
                        date = date.key(), weightKg = num(weight)?.let { Units.toKg(s.weightUnit, it) }, waistCm = cm(waist),
                        hipCm = cm(hip), chestCm = cm(chest), armCm = cm(arm), thighCm = cm(thigh), note = note.trim(),
                    ),
                )
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = { TextButton({ state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }; pickDate = false }) { Text(stringResource(R.string.action_ok)) } },
            dismissButton = { TextButton({ pickDate = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state) }
    }
}

@Composable
private fun WeightBanner(stats: WeightStats?, goalKg: Double?) {
    val s = LocalAppSettings.current
    GameBanner(listOf(Palette.Green, Palette.Teal), seed = 7) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.weight_title), color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    stats?.let { Format.decimal(Units.kgTo(s.weightUnit, it.latest), 1) } ?: "--",
                    color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Black, lineHeight = 56.sp, modifier = Modifier.testTag("weight_latest"),
                )
                Text(s.weightUnit.label(), color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelLarge)
            }
            Mascot(MascotMood.HAPPY, Modifier.size(84.dp), animate = false)
        }
        if (stats != null) {
            Spacer(Modifier.height(10.dp))
            // Losing weight counts as progress unless the goal is above the current weight.
            val downIsGood = goalKg == null || goalKg < stats.average7
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WeightChip(stringResource(R.string.weight_avg7), Format.decimal(Units.kgTo(s.weightUnit, stats.average7), 1), Modifier.weight(1f))
                WeightChip(stringResource(R.string.weight_rate), stats.weeklyRate?.let { signed(Units.kgTo(s.weightUnit, it), 2) } ?: "-", Modifier.weight(1f), good = stats.weeklyRate?.let { (it < 0) == downIsGood })
                WeightChip(stringResource(R.string.weight_change30), stats.change30?.let { signed(Units.kgTo(s.weightUnit, it), 1) } ?: "-", Modifier.weight(1f), good = stats.change30?.let { (it < 0) == downIsGood })
            }
            Spacer(Modifier.height(8.dp))
            val bmi = stats.bmi
            if (bmi != null) {
                val cls = WeightTrend.bmiClass(bmi)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.weight_bmi) + " " + Format.decimal(bmi, 1) + "  ", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    Pill(cls.label(), cls.color(), filled = true)
                }
            } else {
                Text(stringResource(R.string.weight_need_height), color = Color.White, style = MaterialTheme.typography.bodySmall)
            }
            val goalDate = stats.goalDate
            if (goalKg != null && goalDate != null) {
                Text(stringResource(R.string.weight_goal_date, Fmt.weight(goalKg), Fmt.dateMedium(goalDate)), color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun signed(v: Double, digits: Int) = (if (v > 0) "+" else "") + Format.decimal(v, digits)

@Composable
private fun WeightChip(label: String, value: String, modifier: Modifier, good: Boolean? = null) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.22f)).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = when (good) { true -> Color.White; false -> Palette.Yellow; null -> Color.White }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, maxLines = 1)
        Text(label, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

/** One-tap weigh-in: a stepper preset to the last weight (0.1 steps in the user's unit). */
@Composable
private fun QuickWeight(lastKg: Double, onSave: (Double) -> Unit) {
    val s = LocalAppSettings.current
    var value by rememberSaveable(lastKg, s.weightUnit) { mutableStateOf(Math.round(Units.kgTo(s.weightUnit, lastKg) * 10) / 10.0) }
    GameCard(accent = Palette.Green) {
        Text(stringResource(R.string.weight_quick), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        NumberStepper(
            Format.decimal(value, 1) + " " + s.weightUnit.label(), stringResource(R.string.body_weight),
            { value = ((value - 0.1).coerceAtLeast(20.0) * 10).roundToLong() / 10.0 },
            { value = ((value + 0.1).coerceAtMost(660.0) * 10).roundToLong() / 10.0 },
            color = Palette.Green,
        )
        Spacer(Modifier.height(8.dp))
        GameButton(stringResource(R.string.action_save), { onSave(Units.toKg(s.weightUnit, value)) }, color = Palette.Green, modifier = Modifier.fillMaxWidth().testTag("weight_save"))
    }
}

@Composable
private fun WeightChart(weights: List<Pair<LocalDate, Double>>, goalKg: Double?) {
    val s = LocalAppSettings.current
    val shown = weights.takeLast(90)
    val smooth = remember(shown) { Smoothing.movingAverage(shown, 7) }
    GameCard {
        Text(stringResource(R.string.body_trend), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        MultiLineChart(
            listOf(
                ChartSeries(shown.map { Units.kgTo(s.weightUnit, it.second).toFloat() }, Palette.Green.copy(alpha = 0.45f), width = 4f, dots = true, label = stringResource(R.string.body_weight)),
                ChartSeries(smooth.map { Units.kgTo(s.weightUnit, it.second).toFloat() }, Palette.Teal, width = 7f, label = stringResource(R.string.weight_avg7)),
            ),
            firstLabel = Fmt.dayMonth(shown.first().first),
            lastLabel = Fmt.dayMonth(shown.last().first),
            reference = goalKg?.let { Units.kgTo(s.weightUnit, it).toFloat() },
            description = stringResource(R.string.body_trend),
        )
    }
}

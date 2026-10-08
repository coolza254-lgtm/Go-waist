package com.gowaist.app.ui.run

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gowaist.app.R
import com.gowaist.app.data.db.RunEntity
import com.gowaist.app.data.key
import com.gowaist.app.data.toEpochMillis
import com.gowaist.app.data.toLocalDate
import com.gowaist.app.data.toLocalDateTime
import com.gowaist.app.ui.Fmt
import com.gowaist.app.ui.components.label
import com.gowaist.app.ui.theme.Gw
import com.gowaist.core.DistanceUnit
import com.gowaist.core.Format
import com.gowaist.core.Pace
import com.gowaist.core.Units
import com.gowaist.core.ocr.Confidence
import com.gowaist.core.ocr.ParsedRun
import com.gowaist.core.perf.RunType
import com.gowaist.core.run.RunChecks
import com.gowaist.core.run.RunWarning
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.math.roundToInt

enum class RunField { DATE, TIME, DISTANCE, DURATION, PACE, CALORIES, HR }

/** Editable run values held as text so fields can be partially typed. */
class RunForm(val unit: DistanceUnit) {
    var id by mutableStateOf(0L)
    var date by mutableStateOf(LocalDate.now())
    var time by mutableStateOf<LocalTime?>(null)
    var distance by mutableStateOf("")
    var hours by mutableStateOf("")
    var minutes by mutableStateOf("")
    var seconds by mutableStateOf("")
    var readPaceSecPerKm by mutableStateOf<Double?>(null)
    var calories by mutableStateOf("")
    var hr by mutableStateOf("")
    var tags by mutableStateOf(listOf<String>())
    var note by mutableStateOf("")
    var imageUri by mutableStateOf<Uri?>(null)
    var existingImagePath by mutableStateOf<String?>(null)
    var keepImage by mutableStateOf(true)
    var lowConfidence by mutableStateOf(setOf<RunField>())
    var runType by mutableStateOf(RunType.FREE)
    var rpe by mutableStateOf<Int?>(null)
    var intervalJson: String? = null
    var createdAt = System.currentTimeMillis()

    /** Long runs only track distance, so their time may be left empty. */
    val durationOptional: Boolean get() = runType == RunType.LONG

    val distanceM: Double? get() = distance.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }?.let { Units.toMeters(unit, it) }
    val durationSec: Long?
        get() {
            val h = hours.toLongOrNull() ?: 0
            val m = minutes.toLongOrNull() ?: 0
            val s = seconds.toLongOrNull() ?: 0
            return (h * 3600 + m * 60 + s).takeIf { it > 0 }
        }
    val computedPace: Double? get() = distanceM?.let { d -> durationSec?.let { Pace.secPerKm(d, it) } }

    val startAtMillis: Long get() = date.atTime(time ?: LocalTime.NOON).toEpochMillis()

    fun warnings(): Set<RunWarning> = RunChecks.validate(distanceM, durationSec, readPaceSecPerKm, hr.toIntOrNull(), startAtMillis, System.currentTimeMillis())

    fun setDuration(totalSec: Long) {
        hours = (totalSec / 3600).takeIf { it > 0 }?.toString() ?: ""
        minutes = ((totalSec % 3600) / 60).toString()
        seconds = (totalSec % 60).toString().padStart(2, '0')
    }

    fun fill(p: ParsedRun) {
        val low = mutableSetOf<RunField>()
        p.date?.let { date = it.value; if (it.confidence == Confidence.LOW) low += RunField.DATE } ?: run { low += RunField.DATE }
        p.time?.let { time = it.value; if (it.confidence == Confidence.LOW) low += RunField.TIME }
        p.distanceM?.let { distance = Format.decimal(Units.metersTo(unit, it.value), 2); if (it.confidence == Confidence.LOW) low += RunField.DISTANCE } ?: run { low += RunField.DISTANCE }
        p.durationSec?.let { setDuration(it.value); if (it.confidence == Confidence.LOW) low += RunField.DURATION } ?: run { low += RunField.DURATION }
        p.paceSecPerKm?.let { if (it.confidence == Confidence.HIGH) readPaceSecPerKm = it.value else low += RunField.PACE }
        p.calories?.let { calories = it.value.toString(); if (it.confidence == Confidence.LOW) low += RunField.CALORIES }
        p.avgHr?.let { hr = it.value.toString(); if (it.confidence == Confidence.LOW) low += RunField.HR }
        lowConfidence = low
    }

    fun fill(r: RunEntity) {
        id = r.id
        val dt = r.startAt.toLocalDateTime()
        date = r.localDate.toLocalDate()
        time = dt.toLocalTime().withSecond(0).withNano(0)
        distance = Format.decimal(Units.metersTo(unit, r.distanceM), 2)
        setDuration(r.durationSec)
        calories = r.calories?.toString().orEmpty()
        hr = r.avgHr?.toString().orEmpty()
        tags = r.tags
        note = r.note
        existingImagePath = r.sourceImagePath
        runType = r.runType
        rpe = r.rpe?.roundToInt()
        intervalJson = r.intervalJson
        createdAt = r.createdAt
    }

    fun toEntity(imagePath: String?): RunEntity? {
        val d = distanceM ?: return null
        val t = durationSec ?: if (durationOptional) 0L else return null
        return RunEntity(
            id = id,
            startAt = startAtMillis,
            localDate = date.key(),
            distanceM = d,
            durationSec = t,
            avgPaceSecPerKm = if (t > 0) Pace.secPerKm(d, t) else null,
            calories = calories.toIntOrNull(),
            avgHr = hr.toIntOrNull(),
            note = note.trim(),
            tags = tags,
            sourceImagePath = imagePath,
            createdAt = createdAt,
            runType = runType,
            rpe = rpe?.toDouble(),
            intervalJson = intervalJson,
        )
    }
}

@Composable
private fun fieldColors(low: Boolean) = if (low) {
    OutlinedTextFieldDefaults.colors(
        unfocusedBorderColor = Gw.colors.warning,
        focusedBorderColor = Gw.colors.warning,
        unfocusedContainerColor = Gw.colors.warning.copy(alpha = 0.08f),
        focusedContainerColor = Gw.colors.warning.copy(alpha = 0.08f),
    )
} else OutlinedTextFieldDefaults.colors()

@Composable
fun RunFormContent(form: RunForm, existingTags: List<String>, modifier: Modifier = Modifier) {
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    val low = form.lowConfidence
    fun touched(f: RunField) { form.lowConfidence = form.lowConfidence - f }
    val numbers = KeyboardOptions(keyboardType = KeyboardType.Number)
    val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (low.isNotEmpty()) {
            Surface(color = Gw.colors.warning.copy(alpha = 0.15f), shape = RoundedCornerShape(14.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.WarningAmber, null, tint = Gw.colors.warning)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.run_ocr_low), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { pickDate = true }, modifier = Modifier.weight(1f).height(56.dp),
                border = BorderStroke(2.dp, if (RunField.DATE in low) Gw.colors.warning else MaterialTheme.colorScheme.outline),
            ) {
                Icon(Icons.Rounded.CalendarMonth, null)
                Spacer(Modifier.width(6.dp))
                Text(Fmt.dateMedium(form.date))
            }
            OutlinedButton(
                onClick = { pickTime = true }, modifier = Modifier.weight(1f).height(56.dp),
                border = BorderStroke(2.dp, if (RunField.TIME in low) Gw.colors.warning else MaterialTheme.colorScheme.outline),
            ) {
                Icon(Icons.Rounded.Schedule, null)
                Spacer(Modifier.width(6.dp))
                Text(form.time?.let { String.format(java.util.Locale.US, "%02d:%02d", it.hour, it.minute) } ?: stringResource(R.string.run_time_unknown))
            }
        }
        OutlinedTextField(
            form.distance, { form.distance = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(7); touched(RunField.DISTANCE) },
            label = { Text(stringResource(R.string.run_distance)) }, suffix = { Text(form.unit.label()) },
            keyboardOptions = decimal, singleLine = true, modifier = Modifier.fillMaxWidth(),
            colors = fieldColors(RunField.DISTANCE in low), textStyle = MaterialTheme.typography.titleLarge,
            supportingText = if (RunField.DISTANCE in low) { { Text(stringResource(R.string.run_check_field)) } } else null,
        )
        Text(stringResource(R.string.run_duration), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val dl = RunField.DURATION in low
            OutlinedTextField(form.hours, { form.hours = it.filter(Char::isDigit).take(2); touched(RunField.DURATION) }, label = { Text(stringResource(R.string.run_hours)) }, keyboardOptions = numbers, singleLine = true, modifier = Modifier.weight(1f), colors = fieldColors(dl), textStyle = MaterialTheme.typography.titleLarge)
            OutlinedTextField(form.minutes, { form.minutes = it.filter(Char::isDigit).take(2); touched(RunField.DURATION) }, label = { Text(stringResource(R.string.run_minutes)) }, keyboardOptions = numbers, singleLine = true, modifier = Modifier.weight(1f), colors = fieldColors(dl), textStyle = MaterialTheme.typography.titleLarge)
            OutlinedTextField(form.seconds, { form.seconds = it.filter(Char::isDigit).take(2); touched(RunField.DURATION) }, label = { Text(stringResource(R.string.run_seconds)) }, keyboardOptions = numbers, singleLine = true, modifier = Modifier.weight(1f), colors = fieldColors(dl), textStyle = MaterialTheme.typography.titleLarge)
        }
        Surface(color = Gw.colors.run.copy(alpha = 0.1f), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(stringResource(R.string.run_pace_computed, Fmt.pace(form.computedPace)), style = MaterialTheme.typography.titleMedium)
                form.readPaceSecPerKm?.let { Text(stringResource(R.string.run_pace_read, Fmt.pace(it)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        WarningList(form.warnings())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(form.calories, { form.calories = it.filter(Char::isDigit).take(5); touched(RunField.CALORIES) }, label = { Text(stringResource(R.string.run_calories)) }, suffix = { Text(stringResource(R.string.unit_kcal)) }, keyboardOptions = numbers, singleLine = true, modifier = Modifier.weight(1f), colors = fieldColors(RunField.CALORIES in low))
            OutlinedTextField(form.hr, { form.hr = it.filter(Char::isDigit).take(3); touched(RunField.HR) }, label = { Text(stringResource(R.string.run_avg_hr)) }, suffix = { Text(stringResource(R.string.unit_bpm)) }, keyboardOptions = numbers, singleLine = true, modifier = Modifier.weight(1f), colors = fieldColors(RunField.HR in low))
        }
        RunTypePicker(form.runType) { form.runType = it }
        RpePicker(form.rpe) { form.rpe = it }
        TagEditor(form.tags, (stringArrayResource(R.array.run_tag_presets).toList() + existingTags).distinct()) { form.tags = it }
        OutlinedTextField(form.note, { form.note = it.take(500) }, label = { Text(stringResource(R.string.run_note)) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
    }

    if (pickDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = form.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let { form.date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    touched(RunField.DATE)
                    pickDate = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton({ pickDate = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state) }
    }
    if (pickTime) {
        val t = form.time ?: LocalTime.of(6, 0)
        val state = rememberTimePickerState(t.hour, t.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = { TextButton({ form.time = LocalTime.of(state.hour, state.minute); touched(RunField.TIME); pickTime = false }) { Text(stringResource(R.string.action_ok)) } },
            dismissButton = { TextButton({ form.time = null; pickTime = false }) { Text(stringResource(R.string.run_time_unknown)) } },
            text = { TimePicker(state) },
        )
    }
}

@Composable
fun WarningList(warnings: Set<RunWarning>) {
    warnings.forEach { w ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.WarningAmber, null, tint = Gw.colors.warning)
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(
                    when (w) {
                        RunWarning.PACE_MISMATCH -> R.string.warn_pace_mismatch
                        RunWarning.PACE_IMPLAUSIBLE -> R.string.warn_pace_implausible
                        RunWarning.DISTANCE_IMPLAUSIBLE -> R.string.warn_distance_implausible
                        RunWarning.DURATION_IMPLAUSIBLE -> R.string.warn_duration_implausible
                        RunWarning.HR_IMPLAUSIBLE -> R.string.warn_hr_implausible
                        RunWarning.DATE_IN_FUTURE -> R.string.warn_date_future
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = Gw.colors.warning,
            )
        }
    }
}

@Composable
fun TagEditor(tags: List<String>, suggestions: List<String>, onChange: (List<String>) -> Unit) {
    var adding by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    Text(stringResource(R.string.run_tags), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (suggestions + tags).distinct().forEach { tag ->
            val on = tag in tags
            FilterChip(on, { onChange(if (on) tags - tag else tags + tag) }, label = { Text(tag) })
        }
        IconButton(onClick = { adding = true }) { Icon(Icons.Rounded.Add, stringResource(R.string.run_tag_add)) }
    }
    if (adding) {
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text(stringResource(R.string.run_tag_add)) },
            text = { OutlinedTextField(text, { text = it.take(20) }, singleLine = true) },
            confirmButton = {
                TextButton({
                    val t = text.trim()
                    if (t.isNotEmpty() && t !in tags) onChange(tags + t)
                    text = ""
                    adding = false
                }) { Text(stringResource(R.string.action_add)) }
            },
            dismissButton = { TextButton({ adding = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
fun KeepImageSwitch(form: RunForm) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.run_keep_image), Modifier.weight(1f))
        Switch(form.keepImage, { form.keepImage = it })
    }
}

@Composable
fun RunType.label(): String = stringResource(
    when (this) {
        RunType.FREE -> R.string.runtype_free
        RunType.COOPER -> R.string.runtype_cooper
        RunType.INTERVAL -> R.string.runtype_interval
        RunType.LONG -> R.string.runtype_long
    },
)

@Composable
fun RunTypePicker(value: RunType, onChange: (RunType) -> Unit) {
    Text(stringResource(R.string.run_type), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        RunType.entries.forEach { t -> FilterChip(value == t, { onChange(t) }, label = { Text(t.label()) }) }
    }
}

/** Perceived effort 1–10 as a row of tappable chips (tap again to clear). */
@Composable
fun RpePicker(value: Int?, onChange: (Int?) -> Unit) {
    Text(stringResource(R.string.run_rpe), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        (1..10).forEach { n -> FilterChip(value == n, { onChange(if (value == n) null else n) }, label = { Text("$n") }) }
    }
    Text(stringResource(R.string.run_rpe_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

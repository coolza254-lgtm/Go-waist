package com.gowaist.app.ui.perf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gowaist.app.R
import com.gowaist.app.data.settings.AppSettings
import com.gowaist.core.Format
import com.gowaist.core.Units
import com.gowaist.core.perf.Sex

/** Age, sex, height and heart-rate profile used by VO2 max, heart-rate zones and BMI. */
@Composable
fun ProfileDialog(initial: AppSettings, onSave: (AppSettings) -> Unit, onDismiss: () -> Unit) {
    var age by remember { mutableStateOf(initial.age?.toString().orEmpty()) }
    var sex by remember { mutableStateOf(initial.sex) }
    var height by remember { mutableStateOf(initial.heightCm?.let { Format.decimal(Units.cmTo(initial.lengthUnit, it), 1) }.orEmpty()) }
    var rest by remember { mutableStateOf(initial.restHr?.toString().orEmpty()) }
    var max by remember { mutableStateOf(initial.maxHr?.toString().orEmpty()) }
    val numbers = KeyboardOptions(keyboardType = KeyboardType.Number)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.profile_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(age, { age = it.filter(Char::isDigit).take(3) }, label = { Text(stringResource(R.string.profile_age)) }, keyboardOptions = numbers, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("profile_age"))
                Text(stringResource(R.string.profile_sex), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(sex == Sex.MALE, { sex = if (sex == Sex.MALE) null else Sex.MALE }, label = { Text(stringResource(R.string.profile_male)) })
                    FilterChip(sex == Sex.FEMALE, { sex = if (sex == Sex.FEMALE) null else Sex.FEMALE }, label = { Text(stringResource(R.string.profile_female)) })
                }
                OutlinedTextField(
                    height, { height = it.filter { c -> c.isDigit() || c == '.' }.take(5) },
                    label = { Text(stringResource(R.string.profile_height)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(rest, { rest = it.filter(Char::isDigit).take(3) }, label = { Text(stringResource(R.string.profile_rest_hr)) }, keyboardOptions = numbers, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(max, { max = it.filter(Char::isDigit).take(3) }, label = { Text(stringResource(R.string.profile_max_hr)) }, keyboardOptions = numbers, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                {
                    onSave(
                        initial.copy(
                            age = age.toIntOrNull()?.takeIf { it in 8..100 },
                            sex = sex,
                            heightCm = height.toDoubleOrNull()?.let { Units.toCm(initial.lengthUnit, it) }?.takeIf { it in 80.0..250.0 },
                            restHr = rest.toIntOrNull()?.takeIf { it in 30..110 },
                            maxHr = max.toIntOrNull()?.takeIf { it in 120..230 },
                        ),
                    )
                },
                Modifier.testTag("profile_save"),
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

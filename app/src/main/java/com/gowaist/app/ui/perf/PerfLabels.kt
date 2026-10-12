package com.gowaist.app.ui.perf

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.gowaist.app.R
import com.gowaist.app.ui.theme.Palette
import com.gowaist.core.perf.FitnessLevel
import com.gowaist.core.perf.FormState
import com.gowaist.core.perf.Vo2Method
import com.gowaist.core.perf.WeightTrend

@Composable
fun FitnessLevel.label(): String = stringResource(
    when (this) {
        FitnessLevel.VERY_POOR -> R.string.level_very_poor
        FitnessLevel.POOR -> R.string.level_poor
        FitnessLevel.FAIR -> R.string.level_fair
        FitnessLevel.GOOD -> R.string.level_good
        FitnessLevel.EXCELLENT -> R.string.level_excellent
        FitnessLevel.SUPERIOR -> R.string.level_superior
    },
)

/** Colour scale from red (very poor) to purple (superior), used by gauges and level scales. */
val LevelColors = listOf(Palette.Red, Palette.Orange, Palette.Yellow, Palette.Green, Palette.Teal, Palette.Blue)

fun FitnessLevel.color(): Color = LevelColors[ordinal]

@Composable
fun FormState.label(): String = stringResource(
    when (this) {
        FormState.FRESH -> R.string.form_fresh
        FormState.OPTIMAL -> R.string.form_optimal
        FormState.TIRED -> R.string.form_tired
        FormState.OVERREACHING -> R.string.form_overreaching
    },
)

fun FormState.color(): Color = when (this) {
    FormState.FRESH -> Palette.Teal
    FormState.OPTIMAL -> Palette.Green
    FormState.TIRED -> Palette.Orange
    FormState.OVERREACHING -> Palette.Red
}

@Composable
fun Vo2Method.label(): String = stringResource(
    when (this) {
        Vo2Method.COOPER -> R.string.method_cooper
        Vo2Method.HEART_RATE -> R.string.method_hr
        Vo2Method.RACE_PACE -> R.string.method_pace
    },
)

@Composable
fun WeightTrend.BmiClass.label(): String = stringResource(
    when (this) {
        WeightTrend.BmiClass.UNDER -> R.string.bmi_under
        WeightTrend.BmiClass.NORMAL -> R.string.bmi_normal
        WeightTrend.BmiClass.OVER -> R.string.bmi_over
        WeightTrend.BmiClass.OBESE -> R.string.bmi_obese
    },
)

fun WeightTrend.BmiClass.color(): Color = when (this) {
    WeightTrend.BmiClass.UNDER -> Palette.Blue
    WeightTrend.BmiClass.NORMAL -> Palette.Green
    WeightTrend.BmiClass.OVER -> Palette.Orange
    WeightTrend.BmiClass.OBESE -> Palette.Red
}

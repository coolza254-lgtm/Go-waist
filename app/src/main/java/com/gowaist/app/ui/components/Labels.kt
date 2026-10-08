package com.gowaist.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.SportsGymnastics
import androidx.compose.material.icons.rounded.SportsMartialArts
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.gowaist.app.R
import com.gowaist.app.ui.theme.Gw
import com.gowaist.core.DistanceUnit
import com.gowaist.core.LengthUnit
import com.gowaist.core.WeightUnit
import com.gowaist.core.model.Equipment
import com.gowaist.core.model.GoalPeriod
import com.gowaist.core.model.GoalType
import com.gowaist.core.model.MovementPattern
import com.gowaist.core.model.Muscle
import com.gowaist.core.model.PlanDayStatus
import com.gowaist.core.model.PlanDayType
import com.gowaist.core.model.PrType
import com.gowaist.core.model.SetType
import com.gowaist.core.model.TrackingType

@StringRes fun MovementPattern.labelRes() = when (this) {
    MovementPattern.PUSH -> R.string.pattern_push
    MovementPattern.PULL -> R.string.pattern_pull
    MovementPattern.LEGS -> R.string.pattern_legs
    MovementPattern.CORE -> R.string.pattern_core
    MovementPattern.FULL_BODY -> R.string.pattern_full_body
    MovementPattern.SKILL -> R.string.pattern_skill
}

@StringRes fun Muscle.labelRes() = when (this) {
    Muscle.CHEST -> R.string.muscle_chest
    Muscle.SHOULDERS -> R.string.muscle_shoulders
    Muscle.TRICEPS -> R.string.muscle_triceps
    Muscle.BACK -> R.string.muscle_back
    Muscle.BICEPS -> R.string.muscle_biceps
    Muscle.GLUTES -> R.string.muscle_glutes
    Muscle.LEGS -> R.string.muscle_legs
    Muscle.CALVES -> R.string.muscle_calves
    Muscle.ABS -> R.string.muscle_abs
    Muscle.OBLIQUES -> R.string.muscle_obliques
    Muscle.LOWER_BACK -> R.string.muscle_lower_back
}

@StringRes fun TrackingType.labelRes() = when (this) {
    TrackingType.REPS -> R.string.tracking_reps
    TrackingType.HOLD -> R.string.tracking_hold
    TrackingType.WEIGHTED -> R.string.tracking_weighted
    TrackingType.ASSISTED -> R.string.tracking_assisted
    TrackingType.CARDIO -> R.string.tracking_cardio
}

@StringRes fun Equipment.labelRes() = when (this) {
    Equipment.NONE -> R.string.equipment_none
    Equipment.BAR -> R.string.equipment_bar
    Equipment.BAND -> R.string.equipment_band
    Equipment.RINGS -> R.string.equipment_rings
    Equipment.DIP_BARS -> R.string.equipment_dip_bars
}

@StringRes fun SetType.labelRes() = when (this) {
    SetType.WARMUP -> R.string.settype_warmup
    SetType.NORMAL -> R.string.settype_normal
    SetType.DROP -> R.string.settype_drop
    SetType.FAILURE -> R.string.settype_failure
}

@StringRes fun PlanDayType.labelRes() = when (this) {
    PlanDayType.RUN_EASY -> R.string.day_run_easy
    PlanDayType.RUN_TEMPO -> R.string.day_run_tempo
    PlanDayType.RUN_INTERVAL -> R.string.day_run_interval
    PlanDayType.RUN_LONG -> R.string.day_run_long
    PlanDayType.BW_PUSH -> R.string.day_bw_push
    PlanDayType.BW_PULL -> R.string.day_bw_pull
    PlanDayType.BW_LEGS -> R.string.day_bw_legs
    PlanDayType.BW_CORE -> R.string.day_bw_core
    PlanDayType.BW_FULL -> R.string.day_bw_full
    PlanDayType.BW_UPPER -> R.string.day_bw_upper
    PlanDayType.BW_LOWER -> R.string.day_bw_lower
    PlanDayType.REST -> R.string.day_rest
}

@StringRes fun PlanDayStatus.labelRes() = when (this) {
    PlanDayStatus.PENDING -> R.string.status_pending
    PlanDayStatus.DONE -> R.string.status_done
    PlanDayStatus.PARTIAL -> R.string.status_partial
    PlanDayStatus.MISSED -> R.string.status_missed
    PlanDayStatus.OVER -> R.string.status_over
    PlanDayStatus.REST -> R.string.status_rest
}

@StringRes fun GoalType.labelRes() = when (this) {
    GoalType.RUN_DISTANCE -> R.string.goal_run_distance
    GoalType.RUN_COUNT -> R.string.goal_run_count
    GoalType.RUN_5K_TIME -> R.string.goal_run_5k_time
    GoalType.BW_SESSIONS -> R.string.goal_bw_sessions
    GoalType.EXERCISE_MAX_REPS -> R.string.goal_exercise_max_reps
    GoalType.EXERCISE_HOLD -> R.string.goal_exercise_hold
    GoalType.EXERCISE_TOTAL_REPS -> R.string.goal_exercise_total_reps
    GoalType.STREAK_DAYS -> R.string.goal_streak_days
    GoalType.BODY_WEIGHT -> R.string.goal_body_weight
    GoalType.WAIST -> R.string.goal_waist
}

@StringRes fun GoalPeriod.labelRes() = when (this) {
    GoalPeriod.WEEK -> R.string.period_week
    GoalPeriod.MONTH -> R.string.period_month
    GoalPeriod.CUSTOM -> R.string.period_custom
}

@StringRes fun PrType.labelRes() = when (this) {
    PrType.MAX_REPS -> R.string.pr_max_reps
    PrType.LONGEST_HOLD -> R.string.pr_longest_hold
    PrType.MAX_VOLUME_SESSION -> R.string.pr_max_volume_session
    PrType.BEST_SET -> R.string.pr_best_set
    PrType.MAX_ADDED_WEIGHT -> R.string.pr_max_added_weight
    PrType.EST_1RM -> R.string.pr_est_1rm
    PrType.RUN_LONGEST -> R.string.pr_run_longest
    PrType.RUN_FASTEST_PACE -> R.string.pr_run_fastest_pace
    PrType.RUN_1K -> R.string.pr_run_1k
    PrType.RUN_5K -> R.string.pr_run_5k
    PrType.RUN_10K -> R.string.pr_run_10k
    PrType.RUN_HALF -> R.string.pr_run_half
}

@Composable fun DistanceUnit.label() = stringResource(if (this == DistanceUnit.KM) R.string.unit_km else R.string.unit_mi)
@Composable fun DistanceUnit.paceLabel() = stringResource(if (this == DistanceUnit.KM) R.string.unit_per_km else R.string.unit_per_mi)
@Composable fun WeightUnit.label() = stringResource(if (this == WeightUnit.KG) R.string.unit_kg else R.string.unit_lb)
@Composable fun LengthUnit.label() = stringResource(if (this == LengthUnit.CM) R.string.unit_cm else R.string.unit_in)

fun PlanDayType.icon(): ImageVector = when {
    this == PlanDayType.REST -> Icons.Rounded.Bedtime
    this == PlanDayType.RUN_INTERVAL -> Icons.Rounded.Bolt
    this == PlanDayType.RUN_TEMPO -> Icons.Rounded.Timer
    isRun -> Icons.AutoMirrored.Rounded.DirectionsRun
    this == PlanDayType.BW_CORE -> Icons.Rounded.SelfImprovement
    this == PlanDayType.BW_LEGS || this == PlanDayType.BW_LOWER -> Icons.Rounded.SportsMartialArts
    this == PlanDayType.BW_FULL -> Icons.Rounded.SportsGymnastics
    else -> Icons.Rounded.FitnessCenter
}

@Composable
fun PlanDayType.color(): Color = when {
    this == PlanDayType.REST -> Gw.colors.rest
    isRun -> Gw.colors.run
    else -> Gw.colors.train
}

fun MovementPattern.icon(): ImageVector = when (this) {
    MovementPattern.PUSH -> Icons.Rounded.FitnessCenter
    MovementPattern.PULL -> Icons.Rounded.SportsGymnastics
    MovementPattern.LEGS -> Icons.Rounded.SportsMartialArts
    MovementPattern.CORE -> Icons.Rounded.SelfImprovement
    MovementPattern.FULL_BODY -> Icons.Rounded.Bolt
    MovementPattern.SKILL -> Icons.Rounded.Accessibility
}

@Composable
fun MovementPattern.color(): Color = when (this) {
    MovementPattern.PUSH -> Gw.colors.run
    MovementPattern.PULL -> Gw.colors.train
    MovementPattern.LEGS -> Gw.colors.plan
    MovementPattern.CORE -> Gw.colors.goal
    MovementPattern.FULL_BODY -> Gw.colors.warning
    MovementPattern.SKILL -> Gw.colors.danger
}

@Composable
fun PlanDayStatus.color(): Color = when (this) {
    PlanDayStatus.PENDING -> Gw.colors.muted
    PlanDayStatus.DONE -> Gw.colors.success
    PlanDayStatus.PARTIAL -> Gw.colors.warning
    PlanDayStatus.MISSED -> Gw.colors.danger
    PlanDayStatus.OVER -> Gw.colors.plan
    PlanDayStatus.REST -> Gw.colors.rest
}

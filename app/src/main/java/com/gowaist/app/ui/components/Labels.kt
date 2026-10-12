package com.gowaist.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.gowaist.app.R
import com.gowaist.core.DistanceUnit
import com.gowaist.core.LengthUnit
import com.gowaist.core.WeightUnit
import com.gowaist.core.model.GoalPeriod
import com.gowaist.core.model.GoalType
import com.gowaist.core.model.PrType

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

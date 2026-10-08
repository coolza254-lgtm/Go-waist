package com.gowaist.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Upgrade
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gowaist.app.R
import com.gowaist.app.domain.SuggestionItem
import com.gowaist.app.ui.Fmt
import com.gowaist.app.ui.theme.Gw
import com.gowaist.core.Format
import com.gowaist.core.model.TrackingType
import com.gowaist.core.plan.AdjustmentType
import com.gowaist.core.progression.SuggestionType
import kotlin.math.roundToInt

/** A rule-based suggestion with its reason and accept / reject / later buttons. */
@Composable
fun SuggestionCard(
    item: SuggestionItem,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onSnooze: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (title, reason, color) = describe(item)
    val icon = when {
        item is SuggestionItem.PlanChange && item.adjustment.type == AdjustmentType.NEW_TARGET_PACE -> Icons.Rounded.Speed
        item is SuggestionItem.PlanChange -> Icons.AutoMirrored.Rounded.TrendingUp
        item is SuggestionItem.Progression && item.suggestion.type == SuggestionType.PROMOTE -> Icons.Rounded.Upgrade
        item is SuggestionItem.Progression && item.suggestion.type == SuggestionType.DELOAD -> Icons.Rounded.Spa
        else -> Icons.Rounded.Lightbulb
    }
    GameCard(modifier.fillMaxWidth(), accent = color) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBlob(icon, color, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Text(reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onReject) { Text(stringResource(R.string.action_reject)) }
            TextButton(onClick = onSnooze) { Text(stringResource(R.string.action_snooze)) }
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onAccept) { Text(stringResource(R.string.action_accept)) }
        }
    }
}

@Composable
private fun describe(item: SuggestionItem): Triple<String, String, androidx.compose.ui.graphics.Color> = when (item) {
    is SuggestionItem.PlanChange -> {
        val a = item.adjustment
        val plan = stringResource(R.string.sug_plan_label, item.plan.name)
        when (a.type) {
            AdjustmentType.INCREASE_VOLUME -> Triple(
                stringResource(R.string.plan_increase_title),
                plan + "\n" + stringResource(R.string.plan_increase_reason, (a.completionPrevWeek * 100).roundToInt(), (a.completionLastWeek * 100).roundToInt()),
                Gw.colors.success,
            )
            AdjustmentType.REDUCE_VOLUME -> Triple(
                stringResource(R.string.plan_reduce_title),
                plan + "\n" + stringResource(R.string.plan_reduce_reason, (a.completionLastWeek * 100).roundToInt()),
                Gw.colors.warning,
            )
            AdjustmentType.NEW_TARGET_PACE -> Triple(
                stringResource(R.string.plan_pace_title, Fmt.pace(a.value)),
                plan + "\n" + stringResource(R.string.plan_pace_reason, Fmt.pace(a.value), Fmt.pace(a.oldPace)),
                Gw.colors.run,
            )
        }
    }
    is SuggestionItem.Progression -> {
        val s = item.suggestion
        val e = s.evidence
        val name = item.exercise?.nameTh.orEmpty()
        val rpe = e.avgRpe?.let { stringResource(R.string.sug_rpe_suffix, Format.decimal(it, 1)) }.orEmpty()
        val targetReason = stringResource(R.string.sug_target_reason, e.targetSets, e.targetRepsOrSec, e.sessions, rpe)
        val unit = if (item.exercise?.trackingType == TrackingType.HOLD) stringResource(R.string.unit_sec) else stringResource(R.string.unit_reps)
        when (s.type) {
            SuggestionType.PROMOTE -> Triple(stringResource(R.string.sug_promote_title, name, item.next?.nameTh.orEmpty()), targetReason, Gw.colors.plan)
            SuggestionType.INCREASE_REPS -> Triple(stringResource(R.string.sug_reps_title, name, e.suggestedValue.roundToInt()), targetReason, Gw.colors.train)
            SuggestionType.INCREASE_HOLD -> Triple(stringResource(R.string.sug_hold_title, name, e.suggestedValue.roundToInt()), targetReason, Gw.colors.train)
            SuggestionType.ADD_WEIGHT -> Triple(stringResource(R.string.sug_weight_title, name, Fmt.weight(e.suggestedValue)), targetReason, Gw.colors.train)
            SuggestionType.REDUCE_ASSIST -> Triple(stringResource(R.string.sug_assist_title, name, Fmt.weight(e.suggestedValue)), targetReason, Gw.colors.train)
            SuggestionType.PLATEAU -> Triple(
                stringResource(R.string.sug_plateau_title, name),
                stringResource(R.string.sug_plateau_reason, Format.decimal(e.bestRecent, 1) + " " + unit, Format.decimal(e.bestBefore, 1) + " " + unit),
                Gw.colors.warning,
            )
            SuggestionType.DELOAD -> {
                val why = if (e.avgRpe != null && e.avgRpe!! >= 8.5) stringResource(R.string.sug_deload_reason_rpe, Format.decimal(e.avgRpe!!, 1))
                else stringResource(R.string.sug_deload_reason_decline, e.decliningExercises)
                Triple(stringResource(R.string.sug_deload_title), why + "\n" + stringResource(R.string.sug_deload_tip), Gw.colors.goal)
            }
        }
    }
}

package com.gowaist.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gowaist.app.R
import com.gowaist.app.data.db.PlanDayEntity
import com.gowaist.app.ui.Fmt
import com.gowaist.app.ui.theme.Gw
import com.gowaist.core.model.PlanDayStatus
import java.time.LocalDate

/** Target text for a plan day such as "5 กม. · 6'00\"/กม." or a template name. */
@Composable
fun planDayTarget(day: PlanDayEntity, templateName: String?): String {
    val parts = mutableListOf<String>()
    day.targetDistanceM?.let { parts += Fmt.distance(it, 1) }
    if (day.targetDistanceM == null) day.targetDurationMin?.takeIf { !day.type.isBodyweight || templateName == null }?.let { parts += stringResource(R.string.home_target_minutes, it) }
    day.targetPaceSecPerKm?.let { parts += Fmt.pace(it) }
    templateName?.let { parts += it }
    return parts.joinToString(" · ")
}

@Composable
fun PlanDayRow(
    day: PlanDayEntity,
    templateName: String?,
    modifier: Modifier = Modifier,
    showDate: Boolean = false,
    isToday: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val color = day.type.color()
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isToday) color.copy(alpha = 0.10f) else Gw.colors.card)
            .border(2.dp, if (isToday) color.copy(alpha = 0.6f) else Gw.colors.cardBorder, shape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showDate) {
            val d = LocalDate.parse(day.date)
            Column(Modifier.width(44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(Fmt.dateShort(d).substringBefore(' '), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${d.dayOfMonth}", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.width(8.dp))
        }
        IconBlob(day.type.icon(), color, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(day.type.labelRes()), style = MaterialTheme.typography.titleMedium)
            val target = planDayTarget(day, templateName)
            if (target.isNotBlank()) Text(target, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (day.note.isNotBlank()) Text(day.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (day.reminderOff) Icon(Icons.Rounded.NotificationsOff, null, tint = Gw.colors.muted, modifier = Modifier.size(18.dp).padding(end = 4.dp))
        StatusBadge(day.status)
    }
}

@Composable
fun StatusBadge(status: PlanDayStatus) {
    if (status == PlanDayStatus.REST) return
    if (status == PlanDayStatus.DONE) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(Gw.colors.success), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Check, stringResource(R.string.status_done), tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(20.dp))
        }
    } else {
        Pill(stringResource(status.labelRes()), status.color())
    }
}

/** Seven circles Monday–Sunday coloured by the day's plan status / activity. */
@Composable
fun WeekStrip(
    monday: LocalDate,
    days: Map<LocalDate, List<PlanDayEntity>>,
    active: Set<LocalDate>,
    today: LocalDate,
    dayNames: List<String>,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for (i in 0..6) {
            val d = monday.plusDays(i.toLong())
            val planned = days[d].orEmpty()
            val done = d in active
            val missed = planned.any { it.status == PlanDayStatus.MISSED }
            val isRest = planned.isNotEmpty() && planned.all { it.status == PlanDayStatus.REST }
            val bg = when {
                done -> Gw.colors.success
                missed -> Gw.colors.danger.copy(alpha = 0.8f)
                isRest -> Gw.colors.rest.copy(alpha = 0.35f)
                planned.isNotEmpty() -> planned.first().type.color().copy(alpha = 0.25f)
                else -> Gw.colors.track
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(dayNames[i], style = MaterialTheme.typography.labelMedium, color = if (d == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Box(
                    Modifier.padding(top = 4.dp).size(38.dp).clip(CircleShape).background(bg)
                        .border(if (d == today) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(22.dp))
                    else Text("${d.dayOfMonth}", style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

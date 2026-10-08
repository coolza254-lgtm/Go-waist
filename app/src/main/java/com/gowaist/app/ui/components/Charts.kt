package com.gowaist.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gowaist.app.ui.theme.Gw
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

data class ChartPoint(val label: String, val value: Float)

/** Vertical bars with labels under them. Highlights the last bar. */
@Composable
fun BarChart(
    points: List<ChartPoint>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    height: Dp = 160.dp,
    valueLabel: (Float) -> String = { if (it == 0f) "" else com.gowaist.core.Format.decimal(it.toDouble(), 1) },
    description: String = "",
) {
    val anim = remember(points) { Animatable(0f) }
    LaunchedEffect(points) { anim.animateTo(1f, tween(700)) }
    val max = (points.maxOfOrNull { it.value } ?: 0f).coerceAtLeast(0.0001f)
    val track = Gw.colors.track
    Column(modifier.semantics { contentDescription = description }) {
        Row(Modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
            points.forEachIndexed { i, p ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    Text(valueLabel(p.value), fontSize = 10.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val frac = (p.value / max * anim.value).coerceIn(0f, 1f)
                    Canvas(Modifier.fillMaxWidth().height(height - 18.dp)) {
                        val r = CornerRadius(size.width * 0.3f, size.width * 0.3f)
                        drawRoundRect(track, cornerRadius = r)
                        val h = size.height * frac
                        if (h > 0f) {
                            drawRoundRect(
                                if (i == points.lastIndex) color else color.copy(alpha = 0.55f),
                                topLeft = Offset(0f, size.height - h), size = Size(size.width, h), cornerRadius = r,
                            )
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            points.forEach { Text(it.label, Modifier.weight(1f), fontSize = 10.sp, textAlign = TextAlign.Center, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/**
 * Line chart with dots; optional second (smoothed) series drawn on top.
 * [invert] puts smaller values higher (useful for pace, where lower is better).
 */
@Composable
fun LineChart(
    points: List<ChartPoint>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    secondary: List<Float>? = null,
    secondaryColor: Color = MaterialTheme.colorScheme.tertiary,
    invert: Boolean = false,
    height: Dp = 170.dp,
    yLabel: (Float) -> String = { com.gowaist.core.Format.decimal(it.toDouble(), 1) },
    description: String = "",
) {
    if (points.isEmpty()) return
    val anim = remember(points) { Animatable(0f) }
    LaunchedEffect(points) { anim.animateTo(1f, tween(800)) }
    val all = points.map { it.value } + secondary.orEmpty()
    val minV = all.min()
    val maxV = all.max()
    val span = (maxV - minV).takeIf { it > 0.0001f } ?: 1f
    val grid = Gw.colors.track
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier.semantics { contentDescription = description }) {
        Row {
            Column(Modifier.height(height).width(44.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text(yLabel(if (invert) minV else maxV), fontSize = 10.sp, color = labelColor)
                Text(yLabel(if (invert) maxV else minV), fontSize = 10.sp, color = labelColor)
            }
            Canvas(Modifier.weight(1f).height(height)) {
                val pad = 10f
                fun x(i: Int, n: Int) = if (n <= 1) size.width / 2 else pad + i * (size.width - 2 * pad) / (n - 1)
                fun y(v: Float): Float {
                    val f = (v - minV) / span
                    val ff = if (invert) f else 1 - f
                    return pad + ff * (size.height - 2 * pad)
                }
                for (g in 0..3) {
                    val gy = pad + g * (size.height - 2 * pad) / 3
                    drawLine(grid, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 2f)
                }
                val n = points.size
                val visible = (n * anim.value).toInt().coerceAtLeast(1)
                val path = Path()
                points.take(visible).forEachIndexed { i, p -> if (i == 0) path.moveTo(x(i, n), y(p.value)) else path.lineTo(x(i, n), y(p.value)) }
                drawPath(path, color, style = Stroke(6f, cap = StrokeCap.Round))
                points.take(visible).forEachIndexed { i, p ->
                    drawCircle(Color.White, 9f, Offset(x(i, n), y(p.value)))
                    drawCircle(color, 6f, Offset(x(i, n), y(p.value)))
                }
                secondary?.let { s ->
                    val sp = Path()
                    s.take(visible).forEachIndexed { i, v -> if (i == 0) sp.moveTo(x(i, n), y(v)) else sp.lineTo(x(i, n), y(v)) }
                    drawPath(sp, secondaryColor, style = Stroke(5f, cap = StrokeCap.Round))
                }
            }
        }
        if (points.size <= 12) {
            Row(Modifier.fillMaxWidth().padding(start = 44.dp)) {
                points.forEach { Text(it.label, Modifier.weight(1f), fontSize = 10.sp, textAlign = TextAlign.Center, maxLines = 1, color = labelColor) }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(start = 44.dp)) {
                Text(points.first().label, fontSize = 10.sp, color = labelColor)
                Spacer(Modifier.weight(1f))
                Text(points.last().label, fontSize = 10.sp, color = labelColor)
            }
        }
    }
}

/** GitHub-style consistency heatmap: columns are weeks (oldest left), rows Monday–Sunday. */
@Composable
fun CalendarHeatmap(
    values: Map<LocalDate, Float>,
    weeks: Int,
    today: LocalDate,
    modifier: Modifier = Modifier,
    color: Color = Gw.colors.heatHigh,
    description: String = "",
) {
    val low = Gw.colors.heatLow
    val empty = Gw.colors.track
    val max = (values.values.maxOrNull() ?: 0f).coerceAtLeast(0.0001f)
    val start = today.with(DayOfWeek.MONDAY).minusWeeks((weeks - 1).toLong())
    Canvas(modifier.fillMaxWidth().aspectRatio(weeks / 7f * 1.0f).semantics { contentDescription = description }) {
        val cell = size.width / weeks
        val gap = cell * 0.16f
        for (w in 0 until weeks) for (d in 0..6) {
            val date = start.plusDays(w * 7L + d)
            if (date.isAfter(today)) continue
            val v = values[date] ?: 0f
            val c = if (v <= 0f) empty else lerp(low, color, (v / max).coerceIn(0.15f, 1f))
            drawRoundRect(c, topLeft = Offset(w * cell + gap / 2, d * cell + gap / 2), size = Size(cell - gap, cell - gap), cornerRadius = CornerRadius(cell * 0.25f))
        }
    }
}

/** Month grid with coloured markers on active days. */
@Composable
fun MonthCalendar(
    month: YearMonth,
    markers: Map<LocalDate, List<Color>>,
    today: LocalDate,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
    dayNames: List<String>,
    modifier: Modifier = Modifier,
) {
    val first = month.atDay(1)
    val lead = first.dayOfWeek.value - 1
    val days = month.lengthOfMonth()
    val cells = ((lead + days + 6) / 7) * 7
    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            dayNames.forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        for (row in 0 until cells / 7) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0..6) {
                    val idx = row * 7 + col - lead
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (idx in 0 until days) {
                            val date = first.plusDays(idx.toLong())
                            val isSel = date == selected
                            val isToday = date == today
                            val bg = when {
                                isSel -> MaterialTheme.colorScheme.primary
                                isToday -> MaterialTheme.colorScheme.primaryContainer
                                else -> Color.Transparent
                            }
                            Column(
                                Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(bg).clickable { onSelect(date) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    "${date.dayOfMonth}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    markers[date].orEmpty().take(3).forEach { c ->
                                        Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(if (isSel) Color.White else c))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

package com.gowaist.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gowaist.app.ui.theme.shade

/** Bubbles and sparkles drawn behind menu tiles and banners for a playful 2D-game look. */
private fun DrawScope.decorations(seed: Int) {
    val w = size.width
    val h = size.height
    val white = Color.White
    drawCircle(white.copy(alpha = 0.12f), radius = h * 0.55f, center = Offset(w * 0.95f, h * 0.05f))
    drawCircle(white.copy(alpha = 0.08f), radius = h * 0.35f, center = Offset(w * (0.15f + (seed % 3) * 0.1f), h * 1.05f))
    val sx = w * (0.62f + (seed % 4) * 0.06f)
    val sy = h * 0.2f
    val r = h * 0.05f
    drawLine(white.copy(alpha = 0.5f), Offset(sx - r, sy), Offset(sx + r, sy), strokeWidth = r * 0.45f, cap = StrokeCap.Round)
    drawLine(white.copy(alpha = 0.5f), Offset(sx, sy - r), Offset(sx, sy + r), strokeWidth = r * 0.45f, cap = StrokeCap.Round)
    drawCircle(white.copy(alpha = 0.35f), radius = r * 0.35f, center = Offset(w * 0.08f, h * 0.18f))
}

/**
 * Chunky game-menu tile: gradient face with bubbles, icon medallion, title, subtitle and an
 * optional badge or progress bar. Sinks into its darker base when pressed, with a bounce.
 */
@Composable
fun MenuTile(
    title: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    badge: String? = null,
    badgeColor: Color = Color(0xFFFFD23F),
    progress: Float? = null,
    minHeight: Dp = 132.dp,
    seed: Int = 0,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val depth = 6.dp
    val offset by animateDpAsState(if (pressed) depth else 0.dp, spring(stiffness = Spring.StiffnessHigh), label = "press")
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(dampingRatio = 0.35f), label = "bounce")
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier
            .scale(scale)
            .heightIn(min = minHeight + depth)
            .clickable(interaction, indication = null, role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
    ) {
        Box(Modifier.matchParentSize().padding(top = depth).clip(shape).background(color.shade(0.68f)))
        Box(
            Modifier
                .offset(y = offset)
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .clip(shape)
                .background(Brush.linearGradient(listOf(color, color.shade(0.86f)))),
        ) {
            Canvas(Modifier.matchParentSize()) { decorations(seed) }
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    if (badge != null) {
                        Text(
                            badge,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(badgeColor).padding(horizontal = 9.dp, vertical = 3.dp),
                            color = badgeColor.contentColor(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(subtitle, color = Color.White.copy(alpha = 0.92f), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (progress != null) {
                    Spacer(Modifier.height(8.dp))
                    GameProgressBar(progress, color = Color.White, height = 10.dp)
                }
            }
        }
    }
}

/** Wide gradient banner used as the header of game-style screens. */
@Composable
fun GameBanner(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    seed: Int = 1,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(30.dp)
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(colors)),
    ) {
        Canvas(Modifier.matchParentSize()) { decorations(seed) }
        Column(Modifier.padding(18.dp), content = content)
    }
}

/** Arc gauge with a big number in the middle (VO2 max, form…). */
@Composable
fun ArcGauge(
    value: String,
    label: String,
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    track: Color = Color.White.copy(alpha = 0.25f),
    size: Dp = 132.dp,
) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(900), label = "gauge")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = this.size.minDimension * 0.11f
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 135f, 270f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            if (f > 0f) drawArc(color, 135f, 270f * f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = color, fontSize = (size.value * 0.26f).sp, fontWeight = FontWeight.Black, lineHeight = (size.value * 0.28f).sp)
            Text(label, color = color.copy(alpha = 0.9f), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
        }
    }
}

/** Segmented colour scale (poor → superior) with a marker for the current position. */
@Composable
fun LevelScale(segments: List<Color>, position: Float, modifier: Modifier = Modifier, height: Dp = 16.dp) {
    val p by animateFloatAsState(position.coerceIn(0f, 1f), tween(900), label = "scale")
    Canvas(modifier.fillMaxWidth().height(height + 10.dp)) {
        val gap = 4f
        val segW = (size.width - gap * (segments.size - 1)) / segments.size
        val h = height.toPx()
        segments.forEachIndexed { i, c ->
            drawRoundRect(c, Offset(i * (segW + gap), 10.dp.toPx()), Size(segW, h), CornerRadius(h / 2))
        }
        val x = (size.width * p).coerceIn(6f, size.width - 6f)
        drawCircle(Color.White, radius = h * 0.75f, center = Offset(x, 10.dp.toPx() + h / 2))
        drawCircle(Color(0xFF2B2140), radius = h * 0.45f, center = Offset(x, 10.dp.toPx() + h / 2))
    }
}

/** Grid of tiles with a fixed number of columns, inside a vertically scrolling parent. */
@Composable
fun TileGrid(columns: Int, modifier: Modifier = Modifier, spacing: Dp = 12.dp, content: List<@Composable BoxScope.() -> Unit>) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing)) {
        content.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                row.forEach { cell -> Box(Modifier.weight(1f), content = cell) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}


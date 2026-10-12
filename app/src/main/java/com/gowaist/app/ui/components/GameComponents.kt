package com.gowaist.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gowaist.app.R
import com.gowaist.app.ui.theme.Gw
import com.gowaist.app.ui.theme.NumberStyles

/** Readable text colour on top of [this] fill. */
fun Color.contentColor(): Color = if (luminance() > 0.5f) Color(0xFF2A2420) else Color(0xFFFFF9EE)

/**
 * The app's signature surface: a fill with a thin ink outline and a small solid shadow offset to
 * the bottom right, like a flat 2D game panel. [shadow] 0 gives a flat outlined panel.
 */
fun Modifier.panel(fill: Color, ink: Color, radius: Dp = 12.dp, shadow: Dp = 3.dp, border: Dp = 1.5.dp, space: Dp = shadow): Modifier = this
    .padding(end = space, bottom = space)
    .drawBehind {
        if (shadow > 0.dp) {
            val r = radius.toPx()
            drawRoundRect(ink, topLeft = Offset(shadow.toPx(), shadow.toPx()), size = Size(size.width, size.height), cornerRadius = CornerRadius(r, r))
        }
    }
    .clip(RoundedCornerShape(radius))
    .background(fill)
    .border(BorderStroke(border, ink), RoundedCornerShape(radius))

@Composable
fun GameButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    big: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    val shadow by animateDpAsState(if (pressed || !enabled) 0.dp else 3.dp, tween(60), label = "press")
    val face = if (enabled) color else Gw.colors.track
    val fg = if (enabled) face.contentColor() else Gw.colors.muted
    Box(
        modifier
            .padding(end = 3.dp, bottom = 3.dp)
            .heightIn(min = if (big) Gw.space.bigTouch else Gw.space.touch)
            .offset(x = 3.dp - shadow, y = 3.dp - shadow)
            .panel(face, Gw.colors.ink, radius = 12.dp, shadow = shadow, space = 0.dp)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, color = fg, style = if (big) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun RoundGameButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Gw.colors.card,
    size: Dp = 52.dp,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shadow by animateDpAsState(if (pressed) 0.dp else 2.dp, tween(60), label = "press")
    Box(
        modifier
            .padding(end = 2.dp, bottom = 2.dp)
            .size(size)
            .offset(x = 2.dp - shadow, y = 2.dp - shadow)
            .panel(if (enabled) color else Gw.colors.track, Gw.colors.ink, radius = 10.dp, shadow = shadow, space = 0.dp)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = if (enabled) color.contentColor() else Gw.colors.muted, modifier = Modifier.size(size * 0.45f))
    }
}

@Composable
fun GameCard(
    modifier: Modifier = Modifier,
    fill: Color? = null,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val base = modifier.panel(fill ?: Gw.colors.card, Gw.colors.ink)
    val clickable = if (onClick != null) base.clickable(onClick = onClick) else base
    Column(clickable.padding(contentPadding), content = content)
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: (@Composable RowScope.() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        action?.invoke(this)
    }
}

/** A labelled number with a dashed-rule look: value on top, small caption under it. */
@Composable
fun StatBlock(label: String, value: String, modifier: Modifier = Modifier, unit: String? = null, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    val line = Gw.colors.line
    Column(
        modifier
            .drawBehind {
                val dash = 6.dp.toPx()
                var x = 0f
                while (x < size.width) {
                    drawLine(line, Offset(x, 0f), Offset((x + dash).coerceAtMost(size.width), 0f), strokeWidth = 1.5.dp.toPx())
                    x += dash * 1.8f
                }
            }
            .padding(top = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = NumberStyles.medium, color = valueColor, maxLines = 1)
            if (unit != null) Text(" $unit", style = MaterialTheme.typography.labelMedium, color = Gw.colors.muted, modifier = Modifier.padding(bottom = 2.dp))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = Gw.colors.muted, maxLines = 1)
    }
}

/** Compact outlined tile with a big number. */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    icon: ImageVector? = null,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(modifier.panel(Gw.colors.card, Gw.colors.ink, radius = 10.dp, shadow = 0.dp, border = 1.dp).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = Gw.colors.muted, maxLines = 1)
        }
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = NumberStyles.medium, color = color, maxLines = 1)
            if (unit != null) Text(" $unit", style = MaterialTheme.typography.labelMedium, color = Gw.colors.muted, modifier = Modifier.padding(bottom = 2.dp))
        }
    }
}

/** Outlined progress bar. */
@Composable
fun GameProgressBar(progress: Float, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary, height: Dp = 14.dp) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(700), label = "progress")
    val ink = Gw.colors.ink
    val track = Gw.colors.track
    Canvas(modifier.fillMaxWidth().height(height)) {
        val r = CornerRadius(4.dp.toPx())
        val b = 1.5.dp.toPx()
        drawRoundRect(track, cornerRadius = r)
        if (p > 0f) drawRoundRect(color, size = Size(size.width * p, size.height), cornerRadius = r)
        drawRoundRect(ink, topLeft = Offset(b / 2, b / 2), size = Size(size.width - b, size.height - b), cornerRadius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(b))
    }
}

/** Segmented "HP" bar for shoe durability: [segments] blocks, filled by [percent]. */
@Composable
fun HpBar(percent: Int, modifier: Modifier = Modifier, color: Color = Gw.colors.goal, segments: Int = 10, height: Dp = 16.dp) {
    val ink = Gw.colors.ink
    val filled = ((percent.coerceIn(0, 100) / 100f) * segments + 0.001f).toInt()
    val low = percent < 25
    val fill = if (low) Gw.colors.danger else color
    Row(
        modifier.fillMaxWidth().height(height).border(1.5.dp, ink, RoundedCornerShape(4.dp)).padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(segments) { i -> Box(Modifier.weight(1f).height(height).background(if (i < filled) fill else Color.Transparent)) }
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, icon: ImageVector? = null, filled: Boolean = false) {
    Row(
        modifier
            .clip(RoundedCornerShape(5.dp))
            .background(if (filled) color else color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (filled) color.contentColor() else MaterialTheme.colorScheme.onSurface
        if (icon != null) {
            Icon(icon, null, tint = if (filled) fg else color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun IconBlob(icon: ImageVector, color: Color, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    Box(modifier.size(size).panel(color.copy(alpha = 0.16f), Gw.colors.ink, radius = 8.dp, shadow = 0.dp, border = 1.dp), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = color, modifier = Modifier.size(size * 0.52f))
    }
}

/** −/value/+ control. */
@Composable
fun NumberStepper(
    value: String,
    label: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    onValueClick: (() -> Unit)? = null,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        RoundGameButton(Icons.Rounded.Remove, stringResource(R.string.cd_decrease, label), onMinus)
        Column(
            Modifier.weight(1f).let { if (onValueClick != null) it.clickable(onClick = onValueClick) else it },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = NumberStyles.big, textAlign = TextAlign.Center, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelMedium, color = Gw.colors.muted)
        }
        RoundGameButton(Icons.Rounded.Add, stringResource(R.string.cd_increase, label), onPlus, color = color)
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (icon != null) {
            IconBlob(icon, MaterialTheme.colorScheme.primary, size = 64.dp)
            Spacer(Modifier.height(14.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = Gw.colors.muted, textAlign = TextAlign.Center)
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            GameButton(actionText, onAction)
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirmText, color = if (destructive) Gw.colors.danger else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun ScreenPadding(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp), content = content)
}

package com.gowaist.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gowaist.app.R
import com.gowaist.app.ui.theme.Gw
import com.gowaist.app.ui.theme.NumberStyles
import com.gowaist.app.ui.theme.shade

fun Color.contentColor(): Color = if (luminance() > 0.55f) Color(0xFF2B2140) else Color.White

/**
 * Chunky "game" button: a coloured top face over a darker base that it sinks into when pressed.
 */
@Composable
fun GameButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    big: Boolean = false,
    haptic: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val depth = if (big) 6.dp else 5.dp
    val offset by animateDpAsState(if (pressed) depth else 0.dp, spring(stiffness = Spring.StiffnessHigh), label = "press")
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, spring(dampingRatio = 0.4f), label = "bounce")
    val haptics = LocalHapticFeedback.current
    val face = if (enabled) color else Gw.colors.muted.copy(alpha = 0.5f)
    val shape = RoundedCornerShape(if (big) 24.dp else 18.dp)
    val height = if (big) Gw.space.bigTouch else Gw.space.touch
    Box(
        modifier = modifier
            .scale(scale)
            .heightIn(min = height + depth)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
            ) {
                if (haptic) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
    ) {
        Box(
            Modifier
                .matchParentSize()
                .padding(top = depth)
                .clip(shape)
                .background(face.shade(0.72f)),
        )
        Row(
            modifier = Modifier
                .offset(y = offset)
                .fillMaxWidth()
                .heightIn(min = height)
                .clip(shape)
                .background(Brush.verticalGradient(listOf(face.copy(alpha = 1f), face.shade(0.93f))))
                .padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val fg = face.contentColor()
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(if (big) 30.dp else 24.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text,
                color = fg,
                style = if (big) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Round icon-only game button (e.g. +/− steppers). */
@Composable
fun RoundGameButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 64.dp,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val offset by animateDpAsState(if (pressed) 4.dp else 0.dp, label = "press")
    val haptics = LocalHapticFeedback.current
    val face = if (enabled) color else Gw.colors.muted.copy(alpha = 0.5f)
    Box(
        modifier
            .size(size + 4.dp)
            .semantics { this.contentDescription = contentDescription }
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
    ) {
        Box(Modifier.size(size).offset(y = 4.dp).clip(CircleShape).background(face.shade(0.72f)))
        Box(
            Modifier.size(size).offset(y = offset).clip(CircleShape).background(face),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = face.contentColor(), modifier = Modifier.size(size * 0.5f))
        }
    }
}

@Composable
fun GameCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(Gw.radii.m)
    val base = modifier
        .clip(shape)
        .background(Gw.colors.card)
        .border(BorderStroke(2.dp, accent?.copy(alpha = 0.35f) ?: Gw.colors.cardBorder), shape)
    val clickable = if (onClick != null) base.clickable(onClick = onClick) else base
    Column(clickable.padding(contentPadding), content = content)
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: (@Composable RowScope.() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        action?.invoke(this)
    }
}

@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    icon: ImageVector? = null,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(Gw.radii.m),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(2.dp, color.copy(alpha = 0.25f)),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = color.contentColor(), modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = NumberStyles.medium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                if (unit != null) {
                    Spacer(Modifier.width(4.dp))
                    Text(unit, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
                }
            }
        }
    }
}

/** Rounded progress bar with an animated fill and a shine strip, like a game XP bar. */
@Composable
fun GameProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    height: Dp = 18.dp,
) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(900), label = "progress")
    val track = Gw.colors.track
    Canvas(modifier.fillMaxWidth().height(height)) {
        val r = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        if (p > 0f) {
            val w = (size.width * p).coerceAtLeast(size.height)
            drawRoundRect(color, size = Size(w, size.height), cornerRadius = r)
            drawRoundRect(
                Color.White.copy(alpha = 0.35f),
                topLeft = Offset(size.height / 3, size.height * 0.18f),
                size = Size((w - size.height * 2 / 3).coerceAtLeast(0f), size.height * 0.22f),
                cornerRadius = r,
            )
        }
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, icon: ImageVector? = null, filled: Boolean = false) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (filled) color else color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (filled) color.contentColor() else color.shade(0.85f).let { if (MaterialTheme.colorScheme.background.luminance() < 0.3f) color else it }
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun IconBlob(icon: ImageVector, color: Color, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(color),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = color.contentColor(), modifier = Modifier.size(size * 0.56f))
    }
}

/** Big −/value/+ control used for reps, seconds and weight. */
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
        RoundGameButton(Icons.Rounded.Remove, stringResource(R.string.cd_decrease, label), onMinus, color = color.copy(alpha = 0.9f), size = 60.dp)
        Column(
            Modifier.weight(1f).let { if (onValueClick != null) it.clickable(onClick = onValueClick) else it },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = NumberStyles.big, textAlign = TextAlign.Center, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        RoundGameButton(Icons.Rounded.Add, stringResource(R.string.cd_increase, label), onPlus, color = color, size = 60.dp)
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    mood: com.gowaist.core.mascot.MascotMood = com.gowaist.core.mascot.MascotMood.HAPPY,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Mascot(mood = mood, modifier = Modifier.size(132.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            GameButton(actionText, onAction, modifier = Modifier.widthIn(min = 200.dp))
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

/** Box filling the screen width with standard horizontal padding. */
@Composable
fun ScreenPadding(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp), content = content)
}

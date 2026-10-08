package com.gowaist.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.gowaist.app.ui.theme.Gw
import com.gowaist.core.model.Muscle

/**
 * Simple cartoon body (front and back) where each muscle group is tinted by how much it was
 * trained. [load] values are working sets; colours scale to the maximum.
 */
@Composable
fun MuscleMap(
    load: Map<Muscle, Double>,
    frontLabel: String,
    backLabel: String,
    modifier: Modifier = Modifier,
    description: String = "",
) {
    val max = (load.values.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    val base = Gw.colors.track
    val skin = MaterialTheme.colorScheme.surfaceVariant
    val low = Gw.colors.heatLow
    val high = Gw.colors.heatHigh
    fun c(m: Muscle): Color {
        val v = load[m] ?: 0.0
        return if (v <= 0) base else lerp(low, high, (v / max).toFloat().coerceIn(0.2f, 1f))
    }
    Row(modifier.fillMaxWidth().semantics { contentDescription = description }, horizontalArrangement = Arrangement.SpaceEvenly) {
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.fillMaxWidth(0.8f).aspectRatio(0.5f)) { body(skin) { front(::c) } }
            Text(frontLabel, style = MaterialTheme.typography.labelMedium)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.fillMaxWidth(0.8f).aspectRatio(0.5f)) { body(skin) { back(::c) } }
            Text(backLabel, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Draws the neutral silhouette in a 100 × 200 unit space, then the muscle overlay. */
private fun DrawScope.body(skin: Color, overlay: Painter.() -> Unit) {
    val p = Painter(this, size.width / 100f, size.height / 200f)
    p.oval(skin, 38f, 2f, 24f, 26f) // head
    p.rect(skin, 45f, 26f, 10f, 8f) // neck
    p.rect(skin, 26f, 32f, 48f, 62f, 14f) // torso
    p.rect(skin, 12f, 36f, 13f, 56f, 7f) // left arm
    p.rect(skin, 75f, 36f, 13f, 56f, 7f) // right arm
    p.rect(skin, 28f, 92f, 20f, 100f, 9f) // left leg
    p.rect(skin, 52f, 92f, 20f, 100f, 9f) // right leg
    p.overlay()
}

private class Painter(val s: DrawScope, val sx: Float, val sy: Float) {
    fun rect(c: Color, x: Float, y: Float, w: Float, h: Float, r: Float = 6f) =
        s.drawRoundRect(c, Offset(x * sx, y * sy), Size(w * sx, h * sy), CornerRadius(r * sx, r * sx))

    fun oval(c: Color, x: Float, y: Float, w: Float, h: Float) = s.drawOval(c, Offset(x * sx, y * sy), Size(w * sx, h * sy))
}

private fun Painter.front(c: (Muscle) -> Color) {
    oval(c(Muscle.SHOULDERS), 20f, 32f, 15f, 14f)
    oval(c(Muscle.SHOULDERS), 65f, 32f, 15f, 14f)
    rect(c(Muscle.CHEST), 30f, 38f, 19f, 15f, 7f)
    rect(c(Muscle.CHEST), 51f, 38f, 19f, 15f, 7f)
    rect(c(Muscle.BICEPS), 13f, 46f, 11f, 18f, 6f)
    rect(c(Muscle.BICEPS), 76f, 46f, 11f, 18f, 6f)
    rect(c(Muscle.ABS), 41f, 56f, 18f, 32f, 6f)
    rect(c(Muscle.OBLIQUES), 31f, 58f, 8f, 26f, 5f)
    rect(c(Muscle.OBLIQUES), 61f, 58f, 8f, 26f, 5f)
    rect(c(Muscle.LEGS), 29f, 98f, 18f, 44f, 8f)
    rect(c(Muscle.LEGS), 53f, 98f, 18f, 44f, 8f)
    rect(c(Muscle.CALVES), 31f, 150f, 14f, 32f, 7f)
    rect(c(Muscle.CALVES), 55f, 150f, 14f, 32f, 7f)
}

private fun Painter.back(c: (Muscle) -> Color) {
    oval(c(Muscle.SHOULDERS), 20f, 32f, 15f, 14f)
    oval(c(Muscle.SHOULDERS), 65f, 32f, 15f, 14f)
    rect(c(Muscle.BACK), 30f, 36f, 40f, 30f, 10f)
    rect(c(Muscle.TRICEPS), 13f, 46f, 11f, 18f, 6f)
    rect(c(Muscle.TRICEPS), 76f, 46f, 11f, 18f, 6f)
    rect(c(Muscle.LOWER_BACK), 38f, 68f, 24f, 20f, 7f)
    oval(c(Muscle.GLUTES), 29f, 88f, 21f, 18f)
    oval(c(Muscle.GLUTES), 50f, 88f, 21f, 18f)
    rect(c(Muscle.LEGS), 29f, 108f, 18f, 36f, 8f)
    rect(c(Muscle.LEGS), 53f, 108f, 18f, 36f, 8f)
    rect(c(Muscle.CALVES), 30f, 148f, 16f, 34f, 8f)
    rect(c(Muscle.CALVES), 54f, 148f, 16f, 34f, 8f)
}

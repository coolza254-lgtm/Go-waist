package com.gowaist.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import com.gowaist.app.ui.theme.Palette
import kotlin.random.Random

private data class Piece(
    val x: Float,
    val delay: Float,
    val speed: Float,
    val drift: Float,
    val spin: Float,
    val size: Float,
    val color: Color,
    val circle: Boolean,
)

/**
 * Lightweight confetti burst (~2.8 s). Drawn in one Canvas with a fixed number of pieces so it
 * stays smooth on mid-range phones. [key] restarts the animation when it changes.
 */
@Composable
fun Confetti(key: Any, modifier: Modifier = Modifier.fillMaxSize(), pieces: Int = 90, onDone: () -> Unit = {}) {
    val colors = listOf(Palette.Orange, Palette.Yellow, Palette.Teal, Palette.Blue, Palette.Purple, Palette.Pink, Palette.Green)
    val items = remember(key) {
        val rnd = Random(key.hashCode())
        List(pieces) {
            Piece(
                x = rnd.nextFloat(),
                delay = rnd.nextFloat() * 0.35f,
                speed = 0.55f + rnd.nextFloat() * 0.6f,
                drift = (rnd.nextFloat() - 0.5f) * 0.25f,
                spin = (rnd.nextFloat() - 0.5f) * 1080f,
                size = 8f + rnd.nextFloat() * 10f,
                color = colors[rnd.nextInt(colors.size)],
                circle = rnd.nextBoolean(),
            )
        }
    }
    var progress by remember(key) { mutableFloatStateOf(0f) }
    LaunchedEffect(key) {
        val start = withFrameNanos { it }
        val durationNs = 2_800_000_000L
        while (progress < 1f) {
            val now = withFrameNanos { it }
            progress = ((now - start).toFloat() / durationNs).coerceAtMost(1f)
        }
        onDone()
    }
    if (progress >= 1f) return
    Canvas(modifier) {
        items.forEach { p ->
            val t = ((progress - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
            if (t <= 0f) return@forEach
            val y = -40f + t * p.speed * size.height * 1.4f
            val x = p.x * size.width + p.drift * size.width * t + kotlin.math.sin(t * 12f + p.x * 6f) * 18f
            val alpha = if (t > 0.8f) (1f - t) / 0.2f else 1f
            rotate(p.spin * t, pivot = Offset(x, y)) {
                if (p.circle) {
                    drawCircle(p.color.copy(alpha = alpha), radius = p.size / 2, center = Offset(x, y))
                } else {
                    drawRect(p.color.copy(alpha = alpha), topLeft = Offset(x - p.size / 2, y - p.size / 4), size = Size(p.size, p.size / 2))
                }
            }
        }
    }
}

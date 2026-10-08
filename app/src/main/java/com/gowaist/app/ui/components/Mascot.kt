package com.gowaist.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gowaist.app.R
import com.gowaist.app.ui.theme.Gw
import com.gowaist.app.ui.theme.Palette
import com.gowaist.core.mascot.MascotMood
import kotlin.math.PI
import kotlin.math.sin

/**
 * "Gogo", the app mascot: a round orange buddy with a sweatband and sneakers, drawn entirely
 * with Compose Canvas so it scales crisply and needs no image assets.
 */
@Composable
fun Mascot(mood: MascotMood, modifier: Modifier = Modifier, animate: Boolean = true) {
    val t = rememberInfiniteTransition(label = "mascot")
    val phase by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(if (mood == MascotMood.CHEER) 700 else 1600, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    val blink by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "blink",
    )
    val desc = stringResource(R.string.cd_mascot)
    Canvas(modifier.semantics { contentDescription = desc }) {
        val p = if (animate) phase else 0f
        val bounce = sin(p * 2 * PI).toFloat()
        val eyesClosed = animate && blink > 0.96f
        drawMascot(mood, bounce, eyesClosed)
    }
}

private fun DrawScope.drawMascot(mood: MascotMood, bounce: Float, blinkNow: Boolean) {
    val w = size.minDimension
    val cx = size.width / 2
    val lift = when (mood) {
        MascotMood.CHEER -> -w * 0.06f * (bounce + 1) / 2
        MascotMood.SLEEPY -> w * 0.01f * bounce
        else -> -w * 0.025f * (bounce + 1) / 2
    }
    val squish = 1f + 0.03f * bounce
    val bodyR = w * 0.34f
    val cy = size.height * 0.5f + lift

    // Shadow
    drawOval(Color.Black.copy(alpha = 0.12f), topLeft = Offset(cx - bodyR * 0.8f, size.height * 0.9f), size = Size(bodyR * 1.6f, w * 0.06f))

    // Sneakers
    val shoeY = cy + bodyR * 0.88f
    for (side in listOf(-1f, 1f)) {
        val sx = cx + side * bodyR * 0.45f
        drawRoundRect(Color.White, topLeft = Offset(sx - bodyR * 0.3f, shoeY - bodyR * 0.08f), size = Size(bodyR * 0.6f, bodyR * 0.26f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(bodyR * 0.13f))
        drawLine(Palette.Orange, Offset(sx - bodyR * 0.25f, shoeY + bodyR * 0.12f), Offset(sx + bodyR * 0.25f, shoeY + bodyR * 0.12f), strokeWidth = bodyR * 0.05f, cap = StrokeCap.Round)
        drawLine(Palette.Yellow, Offset(sx - bodyR * 0.05f, shoeY - bodyR * 0.02f), Offset(sx + bodyR * 0.12f, shoeY - bodyR * 0.02f), strokeWidth = bodyR * 0.045f, cap = StrokeCap.Round)
    }

    // Arms
    val armUp = mood == MascotMood.CHEER || mood == MascotMood.PROUD
    for (side in listOf(-1f, 1f)) {
        val sx = cx + side * bodyR * 0.92f
        val sy = cy + bodyR * 0.05f
        val ex = sx + side * bodyR * 0.35f
        val ey = if (armUp) sy - bodyR * (0.55f + 0.1f * bounce) else sy + bodyR * 0.3f
        drawLine(Palette.OrangeDark, Offset(sx, sy), Offset(ex, ey), strokeWidth = bodyR * 0.16f, cap = StrokeCap.Round)
        drawCircle(Color.White, radius = bodyR * 0.11f, center = Offset(ex, ey))
    }

    // Body
    translate(top = 0f) {
        drawOval(Palette.Orange, topLeft = Offset(cx - bodyR, cy - bodyR / squish), size = Size(bodyR * 2, bodyR * 2 / squish))
        drawOval(Color(0xFFFF8A66), topLeft = Offset(cx - bodyR * 0.62f, cy - bodyR * 0.15f), size = Size(bodyR * 1.24f, bodyR * 0.95f))
        // highlight
        drawOval(Color.White.copy(alpha = 0.35f), topLeft = Offset(cx - bodyR * 0.7f, cy - bodyR * 0.85f), size = Size(bodyR * 0.45f, bodyR * 0.28f))
    }

    // Sweatband
    val bandY = cy - bodyR * 0.62f
    val band = Path().apply {
        moveTo(cx - bodyR * 0.82f, bandY + bodyR * 0.02f)
        quadraticTo(cx, bandY - bodyR * 0.18f, cx + bodyR * 0.82f, bandY + bodyR * 0.02f)
        lineTo(cx + bodyR * 0.88f, bandY + bodyR * 0.2f)
        quadraticTo(cx, bandY + 0f, cx - bodyR * 0.88f, bandY + bodyR * 0.2f)
        close()
    }
    drawPath(band, Palette.Yellow)
    // band tails
    rotate(20f + 8f * bounce, pivot = Offset(cx + bodyR * 0.85f, bandY + bodyR * 0.1f)) {
        drawRoundRect(Palette.Yellow, topLeft = Offset(cx + bodyR * 0.82f, bandY + bodyR * 0.02f), size = Size(bodyR * 0.36f, bodyR * 0.12f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(bodyR * 0.06f))
    }

    // Face
    val eyeY = cy - bodyR * 0.18f
    val eyeDx = bodyR * 0.36f
    val ink = Color(0xFF2B2140)
    val closed = blinkNow || mood == MascotMood.SLEEPY || mood == MascotMood.CALM
    for (side in listOf(-1f, 1f)) {
        val ex = cx + side * eyeDx
        when {
            mood == MascotMood.CALM -> drawArc(ink, 200f, 140f, false, topLeft = Offset(ex - bodyR * 0.13f, eyeY - bodyR * 0.08f), size = Size(bodyR * 0.26f, bodyR * 0.2f), style = Stroke(bodyR * 0.06f, cap = StrokeCap.Round))
            closed -> drawLine(ink, Offset(ex - bodyR * 0.12f, eyeY), Offset(ex + bodyR * 0.12f, eyeY), strokeWidth = bodyR * 0.06f, cap = StrokeCap.Round)
            mood == MascotMood.PROUD -> drawStar(Offset(ex, eyeY), bodyR * 0.15f, Palette.Yellow, ink)
            else -> {
                drawOval(Color.White, topLeft = Offset(ex - bodyR * 0.14f, eyeY - bodyR * 0.17f), size = Size(bodyR * 0.28f, bodyR * 0.34f))
                drawCircle(ink, radius = bodyR * 0.1f, center = Offset(ex + side * bodyR * 0.01f, eyeY + bodyR * 0.02f))
                drawCircle(Color.White, radius = bodyR * 0.035f, center = Offset(ex + bodyR * 0.03f, eyeY - bodyR * 0.03f))
            }
        }
        // cheeks
        drawCircle(Palette.Pink.copy(alpha = 0.55f), radius = bodyR * 0.1f, center = Offset(cx + side * bodyR * 0.58f, eyeY + bodyR * 0.24f))
    }

    // Mouth
    val my = cy + bodyR * 0.2f
    when (mood) {
        MascotMood.SAD -> drawArc(ink, 200f, 140f, false, topLeft = Offset(cx - bodyR * 0.18f, my), size = Size(bodyR * 0.36f, bodyR * 0.26f), style = Stroke(bodyR * 0.06f, cap = StrokeCap.Round))
        MascotMood.SLEEPY -> drawOval(ink, topLeft = Offset(cx - bodyR * 0.07f, my), size = Size(bodyR * 0.14f, bodyR * 0.12f))
        MascotMood.CHEER, MascotMood.PROUD -> {
            drawArc(ink, 0f, 180f, true, topLeft = Offset(cx - bodyR * 0.24f, my - bodyR * 0.1f), size = Size(bodyR * 0.48f, bodyR * 0.4f))
            drawArc(Palette.Pink, 20f, 140f, true, topLeft = Offset(cx - bodyR * 0.14f, my + bodyR * 0.02f), size = Size(bodyR * 0.28f, bodyR * 0.24f))
        }
        else -> drawArc(ink, 20f, 140f, false, topLeft = Offset(cx - bodyR * 0.2f, my - bodyR * 0.1f), size = Size(bodyR * 0.4f, bodyR * 0.28f), style = Stroke(bodyR * 0.06f, cap = StrokeCap.Round))
    }

    // Extras
    if (mood == MascotMood.SAD) {
        val d = Path().apply {
            moveTo(cx + bodyR * 0.75f, cy - bodyR * 0.55f)
            quadraticTo(cx + bodyR * 0.62f, cy - bodyR * 0.3f, cx + bodyR * 0.75f, cy - bodyR * 0.28f)
            quadraticTo(cx + bodyR * 0.88f, cy - bodyR * 0.3f, cx + bodyR * 0.75f, cy - bodyR * 0.55f)
        }
        drawPath(d, Palette.Blue.copy(alpha = 0.8f))
    }
    if (mood == MascotMood.SLEEPY) {
        val zx = cx + bodyR * 0.9f
        val zy = cy - bodyR * (1.0f + 0.08f * bounce)
        for (i in 0..1) {
            val s = bodyR * (0.18f - i * 0.06f)
            val ox = zx + i * bodyR * 0.25f
            val oy = zy - i * bodyR * 0.3f
            val z = Path().apply { moveTo(ox, oy); lineTo(ox + s, oy); lineTo(ox, oy + s); lineTo(ox + s, oy + s) }
            drawPath(z, Palette.Purple, style = Stroke(bodyR * 0.045f, cap = StrokeCap.Round))
        }
    }
    if (mood == MascotMood.PROUD || mood == MascotMood.CHEER) {
        listOf(Offset(-1.15f, -0.9f), Offset(1.2f, -1.0f), Offset(-1.25f, 0.2f)).forEachIndexed { i, o ->
            val s = bodyR * (0.09f + 0.03f * ((bounce + i) % 1f))
            drawStar(Offset(cx + o.x * bodyR, cy + o.y * bodyR), s, Palette.Yellow, null)
        }
    }
}

private fun DrawScope.drawStar(center: Offset, r: Float, fill: Color, outline: Color?) {
    val path = Path()
    for (i in 0 until 10) {
        val radius = if (i % 2 == 0) r else r * 0.45f
        val a = -PI / 2 + i * PI / 5
        val x = center.x + (radius * kotlin.math.cos(a)).toFloat()
        val y = center.y + (radius * kotlin.math.sin(a)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, fill)
    if (outline != null) drawPath(path, outline, style = Stroke(r * 0.18f))
}

/** Mascot with a speech bubble. */
@Composable
fun MascotSays(text: String, mood: MascotMood, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Mascot(mood, Modifier.size(96.dp))
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 22.dp, bottomEnd = 22.dp, bottomStart = 22.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(2.dp, Gw.colors.cardBorder, RoundedCornerShape(topStart = 6.dp, topEnd = 22.dp, bottomEnd = 22.dp, bottomStart = 22.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

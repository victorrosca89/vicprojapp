package md.vicproj.app

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Fundal ambiental „Aurora”: mai multe bule albastre, blurate, care plutesc lent si permanent.
 * Singura exceptie de la paleta stricta alb-negru — doar nuante de albastru, foarte difuze, peste negru.
 * Bulele sunt desenate cu gradient radial (margini moi din start) si suplimentar blurate pe Android 12+.
 */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier) {
    val tr = rememberInfiniteTransition(label = "aurora")
    val a by tr.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Reverse),
        label = "a",
    )
    val b by tr.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(21000, easing = LinearEasing), RepeatMode.Reverse),
        label = "b",
    )
    val c by tr.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(26000, easing = LinearEasing), RepeatMode.Reverse),
        label = "c",
    )
    val d by tr.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(19000, easing = LinearEasing), RepeatMode.Reverse),
        label = "d",
    )

    Canvas(modifier = modifier.fillMaxSize().blur(72.dp)) {
        val w = size.width
        val h = size.height

        fun blob(fx: Float, fy: Float, radiusFraction: Float, color: Color) {
            val r = minOf(w, h) * radiusFraction
            val center = Offset(w * fx, h * fy)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color, Color.Transparent),
                    center = center,
                    radius = r,
                ),
                radius = r,
                center = center,
            )
        }

        // Bule albastre care plutesc lent intre doua pozitii (miscare permanenta, de tip aurora).
        blob(lerp(0.14f, 0.42f, a), lerp(0.18f, 0.34f, b), 0.78f, VicColors.AuroraBlue1)
        blob(lerp(0.86f, 0.58f, b), lerp(0.30f, 0.14f, c), 0.72f, VicColors.AuroraBlue2)
        blob(lerp(0.20f, 0.46f, c), lerp(0.82f, 0.64f, d), 0.82f, VicColors.AuroraBlue3)
        blob(lerp(0.82f, 0.54f, d), lerp(0.86f, 0.70f, a), 0.74f, VicColors.AuroraBlue4)
    }
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float = start + (stop - start) * fraction

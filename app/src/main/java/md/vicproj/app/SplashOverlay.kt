package md.vicproj.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Ecran de deschidere animat (~2,5 secunde), discret si elegant:
 *   1) logoul apare (fade-in + zoom fin, cu o decelerare „de lux”),
 *   2) apare cercul de incarcare, care se roteste LENT si respira,
 *   3) cercul dispare,
 *   4) logoul dispare (fade-out + un zoom foarte fin),
 *   5) tot stratul se stinge spre aplicatie.
 * Strict alb-negru: doar miscare, fara culoare.
 */
@Composable
fun SplashOverlay(onFinished: () -> Unit) {
    val logoAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.88f) }
    val spinnerAlpha = remember { Animatable(0f) }
    val overlayAlpha = remember { Animatable(1f) }

    // Rotatie lenta (o tura in 1,8 s) + o usoara „respiratie” a arcului.
    val spin = rememberInfiniteTransition(label = "spin")
    val rotation by spin.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "rotation",
    )
    val sweep by spin.animateFloat(
        70f, 280f,
        infiniteRepeatable(
            tween(1400, easing = VicMotion.EaseInOut),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "sweep",
    )

    LaunchedEffect(Unit) {
        // 1) logoul apare: fade-in + zoom cu decelerare fina
        launch { logoScale.animateTo(1f, tween(820, easing = VicMotion.EaseOutLux)) }
        logoAlpha.animateTo(1f, tween(560, easing = VicMotion.EaseOut))
        // 2) apare cercul de incarcare
        spinnerAlpha.animateTo(1f, tween(260, easing = VicMotion.EaseOut))
        delay(760)
        // 3) cercul dispare
        spinnerAlpha.animateTo(0f, tween(260, easing = VicMotion.EaseIn))
        delay(60)
        // 4) logoul dispare: fade-out + zoom foarte fin
        launch { logoScale.animateTo(1.06f, tween(420, easing = VicMotion.EaseInOut)) }
        logoAlpha.animateTo(0f, tween(380, easing = VicMotion.EaseIn))
        // 5) tot stratul se stinge, lasand aplicatia la vedere
        overlayAlpha.animateTo(0f, tween(220, easing = VicMotion.EaseInOut))
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = overlayAlpha.value }
            .background(VicColors.Background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.logo),
                contentDescription = "VicProj",
                modifier = Modifier
                    .size(112.dp)
                    .graphicsLayer {
                        alpha = logoAlpha.value
                        scaleX = logoScale.value
                        scaleY = logoScale.value
                    },
            )
            Spacer(Modifier.height(40.dp))
            Canvas(
                modifier = Modifier
                    .size(34.dp)
                    .graphicsLayer {
                        alpha = spinnerAlpha.value
                        rotationZ = rotation
                    },
            ) {
                val stroke = 3.dp.toPx()
                // Inel de fundal foarte discret, ca indicatorul sa nu „sara” in gol.
                drawArc(
                    color = VicColors.HoverDark,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = Size(size.width - stroke, size.height - stroke),
                )
                drawArc(
                    color = VicColors.Foreground,
                    startAngle = 0f,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = Size(size.width - stroke, size.height - stroke),
                )
            }
        }
    }
}

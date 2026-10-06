package md.vicproj.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
 * Ecran de deschidere animat (~4 secunde), discret si elegant:
 *   1) logoul apare (fade-in + un zoom usor),
 *   2) apare un cerc care se invarte (indicator de incarcare),
 *   3) cercul dispare,
 *   4) logoul dispare (fade-out + un zoom fin),
 *   5) tot stratul se stinge spre aplicatie.
 * Totul strict alb-negru, doar miscare.
 */
@Composable
fun SplashOverlay(onFinished: () -> Unit) {
    val logoAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.82f) }
    val spinnerAlpha = remember { Animatable(0f) }
    val overlayAlpha = remember { Animatable(1f) }

    val spin = rememberInfiniteTransition(label = "spin")
    val rotation by spin.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(950, easing = LinearEasing)),
        label = "rotation",
    )

    LaunchedEffect(Unit) {
        // 1) logoul apare: fade-in + zoom usor (in paralel)
        launch { logoScale.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
        logoAlpha.animateTo(1f, tween(700, easing = LinearEasing))
        // 2) apare cercul care se invarte
        spinnerAlpha.animateTo(1f, tween(400))
        delay(1500)
        // 3) cercul dispare
        spinnerAlpha.animateTo(0f, tween(400))
        delay(120)
        // 4) logoul dispare: fade-out + zoom fin
        launch { logoScale.animateTo(1.07f, tween(650, easing = FastOutSlowInEasing)) }
        logoAlpha.animateTo(0f, tween(650))
        // 5) tot stratul se stinge, lasand aplicatia la vedere
        overlayAlpha.animateTo(0f, tween(280))
        onFinished()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = overlayAlpha.value }
            .background(VicColors.Background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
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
            drawArc(
                color = VicColors.Foreground,
                startAngle = 0f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = Size(size.width - stroke, size.height - stroke),
            )
        }
    }
}

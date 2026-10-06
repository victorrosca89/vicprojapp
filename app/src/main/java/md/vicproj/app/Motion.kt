package md.vicproj.app

import android.view.HapticFeedbackConstants
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Limbajul de miscare al aplicatiei (stil Windows 11 / Fluent):
 * accelerari si decelerari fine, fara „saritura” si fara culoare.
 */
object VicMotion {
    /** Intrare: porneste repede, aterizeaza lin (Fluent „decelerate”). */
    val EaseOut: Easing = CubicBezierEasing(0f, 0f, 0f, 1f)

    /** Decelerare lunga, „de lux”, pentru deplasari si scalari. */
    val EaseOutLux: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Ieșire: pleaca lin, dispare rapid. */
    val EaseIn: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)

    /** Standard, pentru schimbari de stare. */
    val EaseInOut: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    /** Arc elastic discret, pentru apasari si ferestre. */
    val SoftSpring: SpringSpec<Float> = spring(
        dampingRatio = 0.72f,
        stiffness = Spring.StiffnessMediumLow,
    )

    const val ScreenIn = 440
    const val ScreenOut = 200
}

/** Tranzitia de intrare a unui ecran: fade + o scalare si o ridicare aproape imperceptibile. */
fun vicScreenEnter(): EnterTransition =
    fadeIn(tween(VicMotion.ScreenIn, delayMillis = 70, easing = VicMotion.EaseOut)) +
        scaleIn(
            initialScale = 0.97f,
            animationSpec = tween(VicMotion.ScreenIn, delayMillis = 70, easing = VicMotion.EaseOutLux),
        ) +
        slideInVertically(
            animationSpec = tween(VicMotion.ScreenIn, delayMillis = 70, easing = VicMotion.EaseOutLux),
            initialOffsetY = { it / 22 },
        )

/** Tranzitia de ieșire a unui ecran: se stinge rapid si se retrage foarte putin. */
fun vicScreenExit(): ExitTransition =
    fadeOut(tween(VicMotion.ScreenOut, easing = VicMotion.EaseIn)) +
        scaleOut(targetScale = 1.015f, animationSpec = tween(VicMotion.ScreenOut, easing = VicMotion.EaseIn)) +
        slideOutVertically(
            animationSpec = tween(VicMotion.ScreenOut, easing = VicMotion.EaseIn),
            targetOffsetY = { -it / 40 },
        )

/**
 * Vibratie scurta la atingere (respecta setarea de sistem a utilizatorului).
 * Se foloseste pe butoane, chipsuri si rândurile apasabile.
 */
@Composable
fun rememberTapHaptic(): () -> Unit {
    val view = LocalView.current
    return remember(view) {
        {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }
}

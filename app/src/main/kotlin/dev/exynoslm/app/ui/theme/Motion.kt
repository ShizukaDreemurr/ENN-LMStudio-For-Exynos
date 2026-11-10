package dev.exynoslm.app.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/** Motion tokens lifted from the Lucian elements used in this app (durations in ms). */
object LxMotion {
    /** --ux-ease */
    val Ease: Easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

    /** --ux-spring: the overshooting curve every Lucian element uses for "alive" motion. */
    val Spring: Easing = CubicBezierEasing(0.34f, 1.45f, 0.64f, 1f)

    /** #269 dialog pop-in. */
    val Pop: Easing = CubicBezierEasing(0.3f, 1.3f, 0.5f, 1f)

    /** #237 headline slide-up. */
    val RevealEase: Easing = CubicBezierEasing(0.2f, 0.9f, 0.2f, 1f)

    /** #098 rolling digits. */
    val RollEase: Easing = CubicBezierEasing(0.3f, 1.25f, 0.5f, 1f)

    /** #083 indeterminate bar segments. */
    val BarA: Easing = CubicBezierEasing(0.65f, 0.05f, 0.35f, 1f)
    val BarB: Easing = CubicBezierEasing(0.25f, 0.6f, 0.4f, 1f)

    /** ease-in-out used for the infinite float / pulse loops. */
    val InOut: Easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

    const val PressMs = 300
    const val FastMs = 250
    const val BaseMs = 350
    const val SlowMs = 500
    const val PillMs = 500    // #039
    const val SwitchMs = 500  // #013
    const val ToastMs = 500   // #070
    const val PopInMs = 450   // #086 / #091 bubble pop
    const val RollMs = 900    // #098
    const val RevealMs = 900  // #237
}

/** True when the user disabled animations (Developer options / Remove animations => ANIMATOR_DURATION_SCALE == 0). */
val LocalReducedMotion = staticCompositionLocalOf { false }

@Composable
fun rememberReducedMotion(): Boolean {
    val ctx = LocalContext.current
    return remember(ctx) {
        runCatching {
            Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}

/** tween() that collapses to snap() when motion is reduced. */
fun <T> lxTween(reduced: Boolean, durationMs: Int, easing: Easing = LxMotion.Ease, delayMs: Int = 0): FiniteAnimationSpec<T> =
    if (reduced) snap<T>() else tween<T>(durationMillis = durationMs, delayMillis = delayMs, easing = easing)

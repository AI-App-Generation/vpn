package com.thanhmich.veil.vpn.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

object Motion {
    const val FAST = 150
    const val STD = 220
    const val EMPH = 300

    const val STAGGER_STEP = 40
    const val STAGGER_MAX = 8

    const val PULSE_PERIOD = 1600
    const val PULSE_RINGS = 3

    val EaseOutCubic: Easing = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)
}

/** Setting "Hiệu ứng chuyển động": false → mọi animation snap, không pulse/stagger/count-up. */
val LocalMotionEnabled = staticCompositionLocalOf { true }

@Composable
@ReadOnlyComposable
fun <T> motionFast(): FiniteAnimationSpec<T> =
    if (LocalMotionEnabled.current) tween(Motion.FAST, easing = FastOutSlowInEasing) else snap()

@Composable
@ReadOnlyComposable
fun <T> motionStd(delayMillis: Int = 0): FiniteAnimationSpec<T> =
    if (LocalMotionEnabled.current) tween(Motion.STD, delayMillis, Motion.EaseOutCubic) else snap()

@Composable
@ReadOnlyComposable
fun <T> motionEmph(): FiniteAnimationSpec<T> =
    if (LocalMotionEnabled.current) tween(Motion.EMPH, easing = FastOutSlowInEasing) else snap()

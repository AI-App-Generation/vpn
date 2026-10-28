package com.thanhmich.veil.vpn.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.thanhmich.veil.vpn.data.domain.AccentTheme

private val Typography = AppTypography()

@Composable
fun VeilTheme(
    accent: AccentTheme = AccentTheme.Default,
    motionEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val target = accent.palette()
    val spec = if (motionEnabled) tween<androidx.compose.ui.graphics.Color>(Motion.EMPH, easing = FastOutSlowInEasing) else snap()
    val a by animateColorAsState(target.accent, spec, label = "accent")
    val aStrong by animateColorAsState(target.accentStrong, spec, label = "accentStrong")
    val aContainer by animateColorAsState(target.accentContainer, spec, label = "accentContainer")
    val onAContainer by animateColorAsState(target.onAccentContainer, spec, label = "onAccentContainer")
    val colors = appColorsOf(AccentPalette(a, aStrong, aContainer, onAContainer))

    val materialScheme = lightColorScheme(
        primary = colors.accent,
        onPrimary = colors.onAccent,
        primaryContainer = colors.accentContainer,
        onPrimaryContainer = colors.onAccentContainer,
        background = colors.bg,
        onBackground = colors.textPrimary,
        surface = colors.surface,
        onSurface = colors.textPrimary,
        surfaceVariant = colors.surfaceMuted,
        onSurfaceVariant = colors.textSecondary,
        outline = colors.stroke,
        error = colors.poor,
    )
    val materialTypography = remember { Typography.toMaterial() }

    CompositionLocalProvider(
        LocalAppColors provides colors,
        LocalAppTypography provides Typography,
        LocalMotionEnabled provides motionEnabled,
    ) {
        MaterialTheme(colorScheme = materialScheme, typography = materialTypography, content = content)
    }
}

/** Truy cập token: `AppTheme.colors.accent`, `AppTheme.type.body`. */
object AppTheme {
    val colors: AppColors
        @Composable @ReadOnlyComposable get() = LocalAppColors.current
    val type: AppTypography
        @Composable @ReadOnlyComposable get() = LocalAppTypography.current
    val motionEnabled: Boolean
        @Composable @ReadOnlyComposable get() = LocalMotionEnabled.current
}

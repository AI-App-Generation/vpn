package com.thanhmich.veil.vpn.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/** `shadow_card`: textPrimary @6%, blur 24dp, offsetY 8dp. */
@Composable
fun Modifier.shadowCard(shape: Shape, alpha: Float = 1f): Modifier {
    val c = AppTheme.colors.textPrimary
    return dropShadow(shape, Shadow(radius = 24.dp, color = c.copy(alpha = 0.06f * alpha), offset = DpOffset(0.dp, 8.dp)))
}

/** `shadow_nav`: textPrimary @8%, blur 20dp, offsetY −4dp. */
@Composable
fun Modifier.shadowNav(shape: Shape): Modifier {
    val c = AppTheme.colors.textPrimary
    return dropShadow(shape, Shadow(radius = 20.dp, color = c.copy(alpha = 0.08f), offset = DpOffset(0.dp, (-4).dp)))
}

/** `glow_accent`: accent @40%, blur 48dp, spread 4dp. [alpha] 0..1 để animate bật/tắt. */
@Composable
fun Modifier.glowAccent(shape: Shape, alpha: Float = 1f): Modifier {
    if (alpha <= 0f) return this
    val c = AppTheme.colors.accent
    return dropShadow(shape, Shadow(radius = 48.dp, color = c.copy(alpha = 0.4f * alpha), spread = 4.dp))
}

package com.thanhmich.veil.vpn.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.thanhmich.veil.vpn.R
import com.thanhmich.veil.vpn.ui.theme.AppTheme
import com.thanhmich.veil.vpn.ui.theme.Sizes
import com.thanhmich.veil.vpn.ui.theme.glowAccent
import com.thanhmich.veil.vpn.ui.theme.shadowCard

/**
 * The real launcher artwork as an in-app logo tile (Splash, Welcome page, Welcome back).
 * Uses the raster `mipmap/ic_launcher_foreground`: `R.mipmap.ic_launcher` is an
 * `<adaptive-icon>` XML on API 26+, which `painterResource` cannot draw.
 *
 * [glow] 0..1 fades the accent glow in/out (animate it for a breathing effect).
 */
@Composable
fun AppLogoTile(size: Dp, modifier: Modifier = Modifier, glow: Float = 0f) {
    val shape = RoundedCornerShape(size * 0.24f)
    Image(
        painter = painterResource(R.mipmap.ic_launcher_foreground),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .glowAccent(shape, alpha = glow)
            .shadowCard(shape)
            .clip(shape)
            .border(Sizes.hairline, AppTheme.colors.stroke, shape),
    )
}

/**
 * Full-bleed soft "veil ribbons" artwork (AI-generated at build time) on the `bg` colour.
 * Shared by Splash and Welcome back so returning to the app looks like the same front door.
 */
@Composable
fun EntryBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppTheme.colors.bg),
    ) {
        Image(
            painter = painterResource(R.drawable.bg_entry_soft),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        content()
    }
}

/**
 * Soft accent disc that sits behind an illustration — the one gradient allowed on the screen.
 * Follows the user's accent theme, so the pastel artwork always sits on "their" colour.
 */
@Composable
fun AccentHalo(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    val c = AppTheme.colors
    Box(
        modifier = modifier
            .clip(CircleShape)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(c.accentContainer, c.accentContainer.copy(alpha = 0.5f), Color.Transparent),
                        center = center,
                        radius = size.minDimension / 2f,
                    )
                )
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

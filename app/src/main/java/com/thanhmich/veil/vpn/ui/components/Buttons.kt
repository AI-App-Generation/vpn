package com.thanhmich.veil.vpn.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thanhmich.veil.vpn.ui.theme.AppShapes
import com.thanhmich.veil.vpn.ui.theme.AppTheme
import com.thanhmich.veil.vpn.ui.theme.Sizes
import com.thanhmich.veil.vpn.ui.theme.Spacing
import com.thanhmich.veil.vpn.ui.theme.motionFast
import com.thanhmich.veil.vpn.ui.theme.semiBold

/** Nút chính: height 56, pill, nền accent. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes iconRes: Int? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val c = AppTheme.colors
    PillButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        iconRes = iconRes,
        enabled = enabled,
        loading = loading,
        height = Sizes.primaryButton,
        horizontalPadding = Spacing.space24,
        container = c.accent,
        pressedContainer = c.accentStrong,
        content = c.onAccent,
        textStyle = AppTheme.type.headline,
    )
}

/** Nút phụ: height 48, pill, nền accentContainer. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes iconRes: Int? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val c = AppTheme.colors
    PillButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        iconRes = iconRes,
        enabled = enabled,
        loading = loading,
        height = Sizes.secondaryButton,
        horizontalPadding = Spacing.space20,
        container = c.accentContainer,
        pressedContainer = c.accentContainer,
        content = c.onAccentContainer,
        textStyle = AppTheme.type.headline,
    )
}

@Composable
private fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    @DrawableRes iconRes: Int?,
    enabled: Boolean,
    loading: Boolean,
    height: Dp,
    horizontalPadding: Dp,
    container: Color,
    pressedContainer: Color,
    content: Color,
    textStyle: TextStyle,
) {
    val c = AppTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val active = enabled && !loading
    val scale by animateFloatAsState(if (pressed && active) 0.97f else 1f, motionFast(), label = "btnScale")
    val bg by animateColorAsState(
        when {
            !enabled -> c.surfaceMuted
            pressed -> pressedContainer
            else -> container
        },
        motionFast(), label = "btnBg",
    )
    val fg = if (enabled) content else c.textTertiary

    Box(
        modifier = modifier
            .height(height)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(AppShapes.pill)
            .background(bg)
            .clickable(
                interactionSource = interaction,
                indication = androidx.compose.material3.ripple(),
                enabled = active,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        val hideLabel = loading && iconRes == null
        Row(
            modifier = Modifier.alpha(if (hideLabel) 0f else 1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (iconRes != null) {
                if (loading) ButtonSpinner(fg) else Icon(painterResource(iconRes), null, Modifier.size(Sizes.icon), tint = fg)
                Spacer(Modifier.width(Spacing.space8))
            }
            Text(text, style = textStyle, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (hideLabel) ButtonSpinner(fg)
    }
}

@Composable
private fun ButtonSpinner(color: Color) {
    CircularProgressIndicator(modifier = Modifier.size(Sizes.icon), color = color, strokeWidth = 2.dp)
}

/** Nút chữ: height 36 (vùng chạm 48), không nền, chữ accent. */
@Composable
fun TextActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes iconRes: Int? = null,
    enabled: Boolean = true,
    color: Color = AppTheme.colors.accent,
) {
    val fg = if (enabled) color else AppTheme.colors.textTertiary
    Box(modifier = modifier.padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier
                .height(Sizes.textButton)
                .clip(AppShapes.pill)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(PaddingValues(horizontal = Spacing.space12)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, style = AppTheme.type.body.semiBold, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (iconRes != null) {
                Spacer(Modifier.width(Spacing.space4))
                Icon(painterResource(iconRes), null, Modifier.size(Sizes.iconSm), tint = fg)
            }
        }
    }
}

/** Nút icon: vùng chạm 48, phần nhìn thấy 40 tròn, icon 24. */
@Composable
fun AppIconButton(
    @DrawableRes iconRes: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = AppTheme.colors.textSecondary,
    tonal: Boolean = false,
    enabled: Boolean = true,
    iconModifier: Modifier = Modifier,
) {
    Box(modifier = modifier.size(Spacing.minTouch), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (tonal) AppTheme.colors.surfaceMuted else Color.Transparent)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .alpha(if (enabled) 1f else 0.4f),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(iconRes), contentDescription, iconModifier.size(Sizes.iconLg), tint = tint)
        }
    }
}

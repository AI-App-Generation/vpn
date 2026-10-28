package com.thanhmich.veil.vpn.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thanhmich.veil.vpn.R
import com.thanhmich.veil.vpn.ui.theme.AppShapes
import com.thanhmich.veil.vpn.ui.theme.AppTheme
import com.thanhmich.veil.vpn.ui.theme.Sizes
import com.thanhmich.veil.vpn.ui.theme.Spacing
import com.thanhmich.veil.vpn.ui.theme.motionFast

/**
 * Top bar cố định (ngoài vùng cuộn). Màn cha lo `statusBarsPadding()`.
 * [leading]: nút back hoặc logo 32dp; [trailing]: action.
 */
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(Sizes.topBar)
            .padding(horizontal = Spacing.screenHorizontal),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(Spacing.space8))
        }
        Text(
            text = title,
            style = AppTheme.type.title,
            color = AppTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/** Công tắc 52×32 (vùng chạm cao 48). */
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = AppTheme.colors
    val track by animateColorAsState(if (checked) c.accent else c.stroke, motionFast(), label = "switchTrack")
    val thumbOffset by animateDpAsState(if (checked) 24.dp else 4.dp, motionFast(), label = "switchThumb")
    Box(
        modifier = modifier
            .heightIn(min = Spacing.minTouch)
            .toggleable(
                value = checked,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 52.dp, height = 32.dp)
                .clip(AppShapes.pill)
                .background(track),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .offset(x = thumbOffset)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(c.surface),
            )
        }
    }
}

/** Hàng cài đặt: icon container 36 + title + value + chevron/trailing. */
@Composable
fun SettingRow(
    @DrawableRes iconRes: Int,
    title: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = AppTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(AppShapes.md)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(Sizes.iconContainer).clip(CircleShape).background(c.surfaceMuted),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(iconRes), null, Modifier.size(Sizes.icon), tint = c.textSecondary)
        }
        Spacer(Modifier.width(Spacing.space12))
        Text(
            title,
            style = AppTheme.type.body,
            color = c.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            Spacer(Modifier.width(Spacing.space8))
            Text(
                value,
                style = AppTheme.type.caption,
                color = c.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 120.dp),
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(Spacing.space8))
            trailing()
        }
        if (showChevron) {
            Spacer(Modifier.width(Spacing.space4))
            Icon(painterResource(R.drawable.ic_chevron_right), null, Modifier.size(Sizes.icon), tint = c.textTertiary)
        }
    }
}

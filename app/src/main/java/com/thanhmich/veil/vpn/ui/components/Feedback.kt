package com.thanhmich.veil.vpn.ui.components

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thanhmich.veil.vpn.R
import com.thanhmich.veil.vpn.ui.model.BannerVariant
import com.thanhmich.veil.vpn.ui.theme.AppShapes
import com.thanhmich.veil.vpn.ui.theme.AppTheme
import com.thanhmich.veil.vpn.ui.theme.Sizes
import com.thanhmich.veil.vpn.ui.theme.Spacing
import com.thanhmich.veil.vpn.ui.theme.motionEmph
import com.thanhmich.veil.vpn.ui.theme.motionStd
import com.thanhmich.veil.vpn.ui.theme.regular

/** Banner thông báo trong luồng (không popup). */
@Composable
fun InlineBanner(
    variant: BannerVariant,
    text: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val c = AppTheme.colors
    val (bg, fg, icon) = when (variant) {
        BannerVariant.Info -> Triple(c.accentContainer, c.onAccentContainer, R.drawable.ic_info_circle)
        BannerVariant.Warning -> Triple(c.fairContainer, c.fair, R.drawable.ic_alert_triangle_filled)
        BannerVariant.Error -> Triple(c.poorContainer, c.poor, R.drawable.ic_alert_triangle_filled)
        BannerVariant.Offline -> Triple(c.surfaceMuted, c.textSecondary, R.drawable.ic_wifi_off)
    }
    Row(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(AppShapes.md)
            .background(bg)
            .padding(start = Spacing.space16, end = if (actionText != null) Spacing.space4 else Spacing.space16)
            .padding(vertical = Spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), null, Modifier.size(Sizes.icon), tint = fg)
        Spacer(Modifier.width(Spacing.space12))
        Text(
            text = text,
            style = AppTheme.type.body.regular,
            color = fg,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (actionText != null && onAction != null) {
            Spacer(Modifier.width(Spacing.space4))
            TextActionButton(
                text = actionText,
                onClick = onAction,
                color = if (variant == BannerVariant.Info) c.accent else fg,
            )
        }
    }
}

/** Trạng thái rỗng/lỗi: sticker 120dp hoặc icon 48 trong circle 80. */
@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    @DrawableRes imageRes: Int? = null,
    @DrawableRes iconRes: Int? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val c = AppTheme.colors
    Column(
        modifier = modifier.padding(horizontal = Spacing.space32),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            imageRes != null -> Image(painterResource(imageRes), null, Modifier.size(120.dp), contentScale = ContentScale.Fit)
            iconRes != null -> Box(
                Modifier.size(80.dp).clip(CircleShape).background(c.surfaceMuted),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(iconRes), null, Modifier.size(Spacing.space48), tint = c.textTertiary)
            }
        }
        Spacer(Modifier.height(Spacing.space16))
        Text(
            title,
            style = AppTheme.type.headline,
            color = c.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(Spacing.space4))
        Text(
            message,
            style = AppTheme.type.caption,
            color = c.textSecondary,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(Spacing.space20))
            SecondaryButton(text = actionText, onClick = onAction, iconRes = R.drawable.ic_refresh)
        }
    }
}

/**
 * Bottom sheet tự dựng (không dùng Dialog): scrim + sheet bám đáy.
 * Đặt ở lớp trên cùng (lớp phủ shell / Box fillMaxSize của màn), tự áp `navigationBarsPadding()`.
 * Chiều cao tối đa = vùng chứa − status bar − 24dp; nội dung vượt quá thì cuộn (handle đứng yên).
 */
@Composable
fun BottomSheetOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = AppTheme.colors
    BackHandler(enabled = visible, onBack = onDismiss)
    BoxWithConstraints(modifier.fillMaxSize()) {
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val maxSheet = (maxHeight - statusTop - Spacing.space24).coerceAtLeast(0.dp)
        AnimatedVisibility(visible = visible, enter = fadeIn(motionStd()), exit = fadeOut(motionStd())) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(c.scrim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(motionEmph()) { it } + fadeIn(motionEmph()),
            exit = slideOutVertically(motionStd()) { it } + fadeOut(motionStd()),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxSheet)
                    .clip(AppShapes.sheetTop)
                    .background(c.surface)
                    // chặn chạm xuyên xuống scrim
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = Spacing.space24)
                    .padding(bottom = Spacing.space24),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(Spacing.space12))
                Box(Modifier.size(width = 36.dp, height = 4.dp).clip(AppShapes.pill).background(c.stroke))
                Spacer(Modifier.height(Spacing.space16))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    content = content,
                )
            }
        }
    }
}


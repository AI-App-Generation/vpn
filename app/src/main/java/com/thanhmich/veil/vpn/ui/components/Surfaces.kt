package com.thanhmich.veil.vpn.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.thanhmich.veil.vpn.R
import com.thanhmich.veil.vpn.ui.theme.AppShapes
import com.thanhmich.veil.vpn.ui.theme.AppTheme
import com.thanhmich.veil.vpn.ui.theme.Sizes
import com.thanhmich.veil.vpn.ui.theme.Spacing
import com.thanhmich.veil.vpn.ui.theme.motionEmph
import com.thanhmich.veil.vpn.ui.theme.motionFast
import com.thanhmich.veil.vpn.ui.theme.motionStd
import com.thanhmich.veil.vpn.ui.theme.shadowCard
import com.thanhmich.veil.vpn.ui.theme.strongLabel
import com.thanhmich.veil.vpn.ui.theme.tnum
import java.util.Locale

/** Card nền surface, bo 24, shadow_card. `selected` → viền 1.5dp accent. */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(Spacing.space16),
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = AppTheme.colors
    val borderColor by animateColorAsState(if (selected) c.accent else Color.Transparent, motionFast(), label = "cardBorder")
    Column(
        modifier = modifier
            .shadowCard(AppShapes.lg)
            .clip(AppShapes.lg)
            .background(c.surface)
            .border(Sizes.borderSelected, borderColor, AppShapes.lg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}

/** Cờ tròn: ảnh remote ([url]) hoặc drawable local ([res]); lỗi/rỗng → ic_world. */
@Composable
fun FlagAvatar(
    modifier: Modifier = Modifier,
    url: String? = null,
    @DrawableRes res: Int? = null,
    size: Dp = Sizes.flag,
) {
    val c = AppTheme.colors
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(c.surfaceMuted)
            .border(Sizes.hairline, c.stroke, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val fallback: @Composable () -> Unit = {
            Icon(painterResource(R.drawable.ic_world), null, Modifier.size(size * 0.5f), tint = c.textTertiary)
        }
        val model: Any? = res ?: url?.takeIf { it.isNotBlank() }
        if (model == null) {
            fallback()
        } else {
            SubcomposeAsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { Box(Modifier.fillMaxSize().background(c.surfaceMuted)) },
                error = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { fallback() } },
            )
        }
    }
}

enum class PingQuality { Good, Fair, Poor, Unknown }

fun pingQualityOf(pingMs: Int?): PingQuality = when {
    pingMs == null -> PingQuality.Unknown
    pingMs <= 80 -> PingQuality.Good
    pingMs <= 180 -> PingQuality.Fair
    else -> PingQuality.Poor
}

/** Badge ping: chấm 8 + "14 ms", màu theo chất lượng. */
@Composable
fun PingBadge(pingMs: Int?, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val (bgTarget, fgTarget) = when (pingQualityOf(pingMs)) {
        PingQuality.Good -> c.goodContainer to c.good
        PingQuality.Fair -> c.fairContainer to c.fair
        PingQuality.Poor -> c.poorContainer to c.poor
        PingQuality.Unknown -> c.surfaceMuted to c.textSecondary
    }
    val bg by animateColorAsState(bgTarget, motionFast(), label = "pingBg")
    val fg by animateColorAsState(fgTarget, motionFast(), label = "pingFg")
    Row(
        modifier = modifier
            .height(24.dp)
            .clip(AppShapes.pill)
            .background(bg)
            .padding(horizontal = Spacing.space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(fg))
        Spacer(Modifier.width(Spacing.space4))
        Text(
            text = if (pingMs == null) stringResource(R.string.ping_unknown) else stringResource(R.string.ping_ms, pingMs),
            style = AppTheme.type.caption.strongLabel.tnum,
            color = fg,
            maxLines = 1,
        )
    }
}

/** Hàng chuẩn: leading 40 + title/subtitle (weight 1) + trailing. minHeight 64. */
@Composable
fun CountryRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: @Composable () -> Unit,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = AppTheme.colors
    Row(
        modifier = modifier.heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(Spacing.space12))
        Column(Modifier.weight(1f)) {
            Text(title, style = AppTheme.type.body, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = AppTheme.type.caption, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(Spacing.space8))
            Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
        }
    }
}

enum class StatusPillVariant { Disconnected, Connecting, Connected }

/** Pill trạng thái: chấm 8 + caption. Connecting: chấm nhấp nháy. */
@Composable
fun StatusPill(variant: StatusPillVariant, text: String, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val bg by animateColorAsState(
        if (variant == StatusPillVariant.Disconnected) c.surfaceMuted else c.accentContainer, motionStd(), label = "pillBg",
    )
    val fg by animateColorAsState(
        if (variant == StatusPillVariant.Disconnected) c.textSecondary else c.onAccentContainer, motionStd(), label = "pillFg",
    )
    val dot by animateColorAsState(
        if (variant == StatusPillVariant.Disconnected) c.textTertiary else c.accent, motionStd(), label = "pillDot",
    )
    val blinking = variant == StatusPillVariant.Connecting && AppTheme.motionEnabled
    val dotAlpha = if (blinking) {
        val t = rememberInfiniteTransition(label = "pillBlink")
        t.animateFloat(1f, 0.3f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "pillBlinkAlpha").value
    } else 1f

    Row(
        modifier = modifier
            .height(32.dp)
            .clip(AppShapes.pill)
            .background(bg)
            .padding(horizontal = Spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).alpha(dotAlpha).clip(CircleShape).background(dot))
        Spacer(Modifier.width(Spacing.space8))
        Text(text, style = AppTheme.type.caption.strongLabel, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Ô số liệu (S03): icon + label + value (count-up) + unit. */
@Composable
fun MetricTile(
    @DrawableRes iconRes: Int,
    label: String,
    value: Double?,
    unit: String,
    modifier: Modifier = Modifier,
    decimals: Int = 1,
    highlighted: Boolean = false,
) {
    val c = AppTheme.colors
    val animated by animateFloatAsState((value ?: 0.0).toFloat(), motionEmph(), label = "metricValue")
    SurfaceCard(modifier = modifier, selected = highlighted, contentPadding = PaddingValues(Spacing.space12)) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(c.surfaceMuted),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(iconRes), null, Modifier.size(Sizes.iconSm), tint = if (highlighted) c.accent else c.textSecondary)
        }
        Spacer(Modifier.height(Spacing.space8))
        Text(label, style = AppTheme.type.caption, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(Spacing.space4))
        Text(
            text = if (value == null) "—" else String.format(Locale.US, "%.${decimals}f", animated),
            style = AppTheme.type.headline.tnum,
            color = if (value == null) c.textTertiary else c.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(unit, style = AppTheme.type.caption, color = c.textTertiary, maxLines = 1)
    }
}

/** Tiêu đề section: caption uppercase textTertiary. */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    first: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.padding(top = if (first) 0.dp else Spacing.space24, bottom = Spacing.space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text.uppercase(),
            style = AppTheme.type.caption.strongLabel,
            color = AppTheme.colors.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/** Dấu chọn 20dp; luôn giữ chỗ để hàng không nhảy. */
@Composable
fun SelectedMark(selected: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.size(Sizes.icon), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn(motionFast()) + scaleIn(motionFast(), initialScale = 0.6f),
            exit = fadeOut(motionFast()) + scaleOut(motionFast(), targetScale = 0.6f),
        ) {
            Icon(painterResource(R.drawable.ic_circle_check_filled), null, Modifier.size(Sizes.icon), tint = AppTheme.colors.accent)
        }
    }
}

/** Khối skeleton shimmer (alpha). */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, shape: Shape = AppShapes.xs) {
    val alpha = if (AppTheme.motionEnabled) {
        val t = rememberInfiniteTransition(label = "skeleton")
        t.animateFloat(0.5f, 1f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "skeletonAlpha").value
    } else 0.7f
    Box(modifier.clip(shape).alpha(alpha).background(AppTheme.colors.surfaceMuted))
}

/** Khoảng đệm cuối vùng cuộn (240dp). */
@Composable
fun EndSpacer() {
    Spacer(Modifier.height(Spacing.endSpacer))
}

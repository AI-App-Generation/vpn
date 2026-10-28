package com.thanhmich.veil.vpn.features.vpn

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thanhmich.veil.vpn.R
import com.thanhmich.veil.vpn.data.domain.ServerSelection
import com.thanhmich.veil.vpn.data.domain.VpnServer
import com.thanhmich.veil.vpn.ui.components.CountryRow
import com.thanhmich.veil.vpn.ui.components.FlagAvatar
import com.thanhmich.veil.vpn.ui.components.PingBadge
import com.thanhmich.veil.vpn.ui.components.PrimaryButton
import com.thanhmich.veil.vpn.ui.components.SkeletonBlock
import com.thanhmich.veil.vpn.ui.components.SurfaceCard
import com.thanhmich.veil.vpn.ui.components.TextActionButton
import com.thanhmich.veil.vpn.ui.theme.AppTheme
import com.thanhmich.veil.vpn.ui.theme.Motion
import com.thanhmich.veil.vpn.ui.theme.Sizes
import com.thanhmich.veil.vpn.ui.theme.Spacing
import com.thanhmich.veil.vpn.ui.theme.glowAccent
import com.thanhmich.veil.vpn.ui.theme.motionEmph
import com.thanhmich.veil.vpn.ui.theme.motionFast
import com.thanhmich.veil.vpn.ui.theme.regular
import com.thanhmich.veil.vpn.ui.theme.shadowCard
import com.thanhmich.veil.vpn.ui.theme.strongLabel
import com.thanhmich.veil.vpn.ui.theme.tnum
import kotlinx.coroutines.flow.StateFlow

/** Kích thước riêng của S01 (chỉ màn này dùng — không đưa vào ui/theme). */
internal object VpnDimens {
    /** Logo trên top bar. */
    val logo = 32.dp

    /** Vùng ConnectButton (gồm quầng gradient + pulse). */
    val connectArea = 216.dp

    /** Lõi nút tròn — vật thể lớn nhất app. */
    val connectCore = 152.dp
    val connectIcon = 56.dp

    /** Viền accentContainer khi disconnected; cũng là độ dày vòng tĩnh khi motion off. */
    val connectBorder = 8.dp
    val pulseStroke = 2.dp

    /** = minHeight của CountryRow (skeleton giữ đúng chiều cao hàng thật). */
    val rowMinHeight = 64.dp

    /** Sticker CommunityNoticeSheet. */
    val sticker = 120.dp
    val stickerIcon = 64.dp
}

/** Token `pulse`: scale 1→1.42, alpha 0.35→0, lệch 533ms. */
private const val PULSE_SCALE = 1.42f
private const val PULSE_ALPHA = 0.35f
private const val PULSE_OFFSET_MS = Motion.PULSE_PERIOD / Motion.PULSE_RINGS
private const val STATIC_RING_ALPHA = 0.3f
private const val GRADIENT_ALPHA = 0.12f
private const val PRESS_SCALE = 0.96f
private const val DISABLED_ALPHA = 0.5f

private val VpnPhase.isBusy: Boolean
    get() = this == VpnPhase.Connecting || this == VpnPhase.AwaitingPermission || this == VpnPhase.Disconnecting

/**
 * Nút Connect: Box 216 = quầng radial gradient (alpha theo pha) + pulse rings (Connecting) + lõi 152.
 * Kích thước không đổi theo trạng thái; disabled → alpha 0.5.
 */
@Composable
internal fun ConnectButton(
    phase: VpnPhase,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = AppTheme.colors
    val connected = phase == VpnPhase.Connected
    val idle = phase == VpnPhase.Disconnected || phase == VpnPhase.Restoring

    val coreBg by animateColorAsState(
        when {
            connected -> c.accent
            phase.isBusy -> c.accentContainer
            else -> c.surface
        },
        motionEmph(), label = "connectBg",
    )
    val contentColor by animateColorAsState(if (connected) c.onAccent else c.accent, motionEmph(), label = "connectFg")
    val gradientAlpha by animateFloatAsState(
        when {
            connected -> 1f
            phase.isBusy -> 0.7f
            else -> 0.4f
        },
        motionEmph(), label = "connectGradient",
    )
    val idleAlpha by animateFloatAsState(if (idle) 1f else 0f, motionEmph(), label = "connectIdle")
    val glowAlpha by animateFloatAsState(if (connected) 1f else 0f, motionEmph(), label = "connectGlow")
    val enabledAlpha by animateFloatAsState(if (enabled) 1f else DISABLED_ALPHA, motionFast(), label = "connectEnabled")

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) PRESS_SCALE else 1f, motionFast(), label = "connectScale")

    val labelRes = when (phase) {
        VpnPhase.Disconnected, VpnPhase.Restoring -> R.string.vpn_button_connect
        VpnPhase.AwaitingPermission, VpnPhase.Connecting -> R.string.vpn_button_cancel
        VpnPhase.Connected, VpnPhase.Disconnecting -> R.string.vpn_button_disconnect
    }

    Box(
        modifier = modifier
            .size(VpnDimens.connectArea)
            // ModulateAlpha: không dựng layer offscreen → glow/shadow tràn ra ngoài 216dp không bị cắt vuông.
            .graphicsLayer {
                alpha = enabledAlpha
                compositingStrategy = CompositingStrategy.ModulateAlpha
            },
        contentAlignment = Alignment.Center,
    ) {
        // Gradient duy nhất của màn: quầng radial accent@12% → trong suốt.
        val glowColor = c.accent.copy(alpha = GRADIENT_ALPHA)
        Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = gradientAlpha }) {
            drawCircle(
                brush = Brush.radialGradient(listOf(glowColor, Color.Transparent), center, size.minDimension / 2f),
                radius = size.minDimension / 2f,
            )
        }

        if (phase == VpnPhase.Connecting || phase == VpnPhase.AwaitingPermission) {
            if (AppTheme.motionEnabled) {
                PulseRings(c.accent)
            } else {
                // Motion off: vòng tĩnh accent@30% dày 8dp bao quanh lõi.
                Box(
                    Modifier
                        .size(VpnDimens.connectCore + VpnDimens.connectBorder * 2)
                        .border(VpnDimens.connectBorder, c.accent.copy(alpha = STATIC_RING_ALPHA), CircleShape),
                )
            }
        }

        Box(
            modifier = Modifier
                .size(VpnDimens.connectCore)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .shadowCard(CircleShape, idleAlpha)
                .glowAccent(CircleShape, glowAlpha)
                .clip(CircleShape)
                .background(coreBg)
                .border(VpnDimens.connectBorder, c.accentContainer.copy(alpha = idleAlpha), CircleShape)
                .clickable(
                    interactionSource = interaction,
                    indication = ripple(),
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = Spacing.space16),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(painterResource(R.drawable.ic_power), null, Modifier.size(VpnDimens.connectIcon), tint = contentColor)
                Spacer(Modifier.height(Spacing.space4))
                Text(
                    text = stringResource(labelRes).uppercase(),
                    style = AppTheme.type.caption.strongLabel,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 3 vòng pulse (token `pulse`), chỉ tồn tại trong composition khi Connecting (R15). */
@Composable
private fun PulseRings(color: Color) {
    val transition = rememberInfiniteTransition(label = "pulse")
    repeat(Motion.PULSE_RINGS) { i ->
        val progress = transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(Motion.PULSE_PERIOD, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
                initialStartOffset = StartOffset(i * PULSE_OFFSET_MS),
            ),
            label = "pulse$i",
        )
        Box(
            Modifier
                .size(VpnDimens.connectCore)
                .graphicsLayer {
                    val p = progress.value
                    val s = 1f + (PULSE_SCALE - 1f) * p
                    scaleX = s
                    scaleY = s
                    alpha = PULSE_ALPHA * (1f - p)
                }
                .border(VpnDimens.pulseStroke, color, CircleShape),
        )
    }
}

/** Nhãn + đồng hồ phiên; luôn chiếm chỗ (textTertiary khi chưa Connected). */
@Composable
internal fun SessionTimer(elapsed: StateFlow<Long>, active: Boolean, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    // Collect riêng ở đây: tick 1s chỉ recompose timer, không dựng lại cả màn.
    val elapsedSec by elapsed.collectAsStateWithLifecycle()
    val color by animateColorAsState(if (active) c.textPrimary else c.textTertiary, motionEmph(), label = "timerColor")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.vpn_timer_label).uppercase(),
            style = AppTheme.type.caption.strongLabel,
            color = c.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(Spacing.space4))
        Text(
            text = VpnUiLogic.formatElapsed(elapsedSec),
            style = AppTheme.type.display.tnum,
            color = color,
            maxLines = 1,
        )
    }
}

/** Card server đang chọn → chạm để sang tab Locations. */
@Composable
internal fun ServerSelectorCard(
    server: VpnServer?,
    selection: ServerSelection,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = AppTheme.colors
    SurfaceCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        when {
            server == null && loading -> ServerSkeletonRow()

            server == null -> CountryRow(
                title = stringResource(R.string.vpn_server_none),
                subtitle = stringResource(R.string.vpn_server_none_hint),
                leading = { FlagAvatar(url = null) },
                trailing = { Chevron() },
            )

            selection is ServerSelection.Auto -> CountryRow(
                title = stringResource(R.string.vpn_server_auto),
                subtitle = stringResource(R.string.vpn_server_auto_resolved, server.country),
                leading = {
                    Box(
                        Modifier.size(Sizes.flag).clip(CircleShape).background(c.accentContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(R.drawable.ic_bolt_filled), null, Modifier.size(Sizes.icon), tint = c.accent)
                    }
                },
                trailing = {
                    PingBadge(server.pingMs)
                    Spacer(Modifier.width(Spacing.space8))
                    Chevron()
                },
            )

            else -> CountryRow(
                title = server.country,
                subtitle = VpnUiLogic.shortHost(server.hostname),
                leading = { FlagAvatar(url = server.countryFlag) },
                trailing = {
                    PingBadge(server.pingMs)
                    Spacer(Modifier.width(Spacing.space8))
                    Chevron()
                },
            )
        }
    }
}

@Composable
private fun Chevron() {
    Icon(
        painterResource(R.drawable.ic_chevron_right),
        null,
        Modifier.size(Sizes.icon),
        tint = AppTheme.colors.textTertiary,
    )
}

/** Skeleton cùng khung với CountryRow (minHeight 64, leading 40). */
@Composable
private fun ServerSkeletonRow() {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = VpnDimens.rowMinHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonBlock(Modifier.size(Sizes.flag), shape = CircleShape)
        Spacer(Modifier.width(Spacing.space12))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
            SkeletonBlock(Modifier.fillMaxWidth(0.5f).height(Spacing.space16))
            SkeletonBlock(Modifier.fillMaxWidth(0.35f).height(Spacing.space12))
        }
    }
}

/** Nội dung CommunityNoticeSheet (đặt trong BottomSheetOverlay — sheet tự giới hạn chiều cao + cuộn). */
@Composable
internal fun CommunityNoticeContent(
    onAccept: () -> Unit,
    onLearnMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = AppTheme.colors
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // sticker_community (bản native): tròn 120, gradient accentContainer → surface, icon world 64 accent.
        Box(
            Modifier
                .size(VpnDimens.sticker)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(c.accentContainer, c.surface))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_world_filled), null, Modifier.size(VpnDimens.stickerIcon), tint = c.accent)
        }
        Spacer(Modifier.height(Spacing.space16))
        Text(
            text = stringResource(R.string.community_title),
            style = AppTheme.type.title,
            color = c.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(Spacing.space8))
        Text(
            text = stringResource(R.string.community_notice_short),
            style = AppTheme.type.body.regular,
            color = c.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.space24))
        PrimaryButton(
            text = stringResource(R.string.community_accept),
            onClick = onAccept,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.space8))
        TextActionButton(
            text = stringResource(R.string.community_learn_more),
            onClick = onLearnMore,
            iconRes = R.drawable.ic_external_link,
        )
    }
}

package com.thanhmich.veil.vpn.features.vpn

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thanhmich.veil.vpn.R
import com.thanhmich.veil.vpn.data.domain.ConnectionState
import com.thanhmich.veil.vpn.features.main.LocalShellOverlay
import com.thanhmich.veil.vpn.ui.components.AppTopBar
import com.thanhmich.veil.vpn.ui.components.BottomSheetOverlay
import com.thanhmich.veil.vpn.ui.components.BrandMark
import com.thanhmich.veil.vpn.ui.components.EndSpacer
import com.thanhmich.veil.vpn.ui.components.InlineBanner
import com.thanhmich.veil.vpn.ui.components.StatusPill
import com.thanhmich.veil.vpn.ui.components.StatusPillVariant
import com.thanhmich.veil.vpn.ui.components.SurfaceCard
import com.thanhmich.veil.vpn.ui.components.TextActionButton
import com.thanhmich.veil.vpn.ui.model.BannerAction
import com.thanhmich.veil.vpn.ui.model.BannerModel
import com.thanhmich.veil.vpn.ui.model.MainTab
import com.thanhmich.veil.vpn.ui.theme.AppTheme
import com.thanhmich.veil.vpn.ui.theme.Motion
import com.thanhmich.veil.vpn.ui.theme.Spacing
import com.thanhmich.veil.vpn.ui.theme.motionStd
import com.thanhmich.veil.vpn.ui.theme.regular
import com.thanhmich.veil.vpn.utils.IntentUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

/**
 * S01 VPN. Màn tab: shell đã chừa padding đáy cho BottomNavBar → không tự áp navigationBarsPadding.
 * Tự giữ launcher quyền VPN + POST_NOTIFICATIONS (xem `## Điều hướng`).
 */
@Composable
fun VpnScreen(
    onNavigateTab: (MainTab) -> Unit,
    viewModel: VpnViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val navigate by rememberUpdatedState(onNavigateTab)

    val vpnPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.onVpnPermissionResult(it.resultCode == Activity.RESULT_OK)
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.onNotificationPermissionResult()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        // Chỉ xử lý event khi màn STARTED (không launch hộp thoại quyền khi app ở nền).
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    is VpnEvent.RequestVpnPermission -> try {
                        vpnPermissionLauncher.launch(event.intent)
                    } catch (_: ActivityNotFoundException) {
                        viewModel.onVpnPermissionResult(false)
                    }

                    VpnEvent.RequestNotificationPermission -> {
                        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                            PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            viewModel.onNotificationPermissionResult()
                        } else {
                            try {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } catch (_: ActivityNotFoundException) {
                                viewModel.onNotificationPermissionResult()
                            }
                        }
                    }

                    is VpnEvent.OpenUrl -> IntentUtils.openUrl(context, event.url)
                    is VpnEvent.NavigateTab -> navigate(event.tab)
                }
            }
        }
    }

    // CommunityNoticeSheet vẽ ở lớp phủ của shell (phủ cả BottomNavBar).
    val overlayHost = LocalShellOverlay.current
    val noticeVisible by rememberUpdatedState(state.showCommunityNotice)
    DisposableEffect(overlayHost, viewModel) {
        val sheet: @Composable () -> Unit = {
            CommunityNoticeSheet(
                visible = noticeVisible,
                onAccept = viewModel::onNoticeAccepted,
                onDismiss = viewModel::onNoticeDismissed,
                onLearnMore = viewModel::onLearnMoreClick,
            )
        }
        overlayHost.content = sheet
        onDispose {
            if (overlayHost.content === sheet) overlayHost.content = null
            // Rời tab VPN khi sheet đang mở → đóng hẳn, quay lại không tự bật (giữ nguyên khi xoay màn).
            if (context.findActivity()?.isChangingConfigurations != true) viewModel.onNoticeDismissed()
        }
    }

    VpnContent(
        state = state,
        onConnectClick = viewModel::onConnectClick,
        onServerCardClick = viewModel::onServerCardClick,
        onOpenSpeed = { onNavigateTab(MainTab.Speed) },
        onBannerAction = viewModel::onBannerAction,
        elapsed = viewModel.elapsedSec,
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun VpnContent(
    state: VpnUiState,
    onConnectClick: () -> Unit,
    onServerCardClick: () -> Unit,
    onOpenSpeed: () -> Unit,
    onBannerAction: (BannerAction) -> Unit,
    elapsed: StateFlow<Long>,
) {
    val c = AppTheme.colors
    val scrollState = rememberScrollState()
    // Banner đã được tự cuộn tới (sống qua đổi tab / xoay màn) → không cuộn lại, giữ vị trí đã restore.
    var lastRevealedBanner by rememberSaveable { mutableStateOf<String?>(null) }
    Box(
        Modifier
            .fillMaxSize()
            .background(c.bg),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            AppTopBar(
                title = stringResource(R.string.app_name),
                leading = { BrandMark(VpnDimens.logo) },
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = Spacing.screenHorizontal),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(Spacing.space8))
                StatusHeader(state)
                Spacer(Modifier.height(Spacing.space24))
                ConnectButton(
                    phase = state.phase,
                    enabled = state.connectEnabled,
                    onClick = onConnectClick,
                )
                Spacer(Modifier.height(Spacing.space24))
                SessionTimer(
                    elapsed = elapsed,
                    active = state.phase == VpnPhase.Connected,
                )
                Spacer(Modifier.height(Spacing.space32))
                ServerSelectorCard(
                    server = state.displayServer,
                    selection = state.selection,
                    loading = state.catalogLoading || state.phase == VpnPhase.Restoring,
                    onClick = onServerCardClick,
                )
                if (!state.catalogLoading && !state.catalogReady && state.phase == VpnPhase.Disconnected) {
                    Spacer(Modifier.height(Spacing.space12))
                    NoServerDataCard(
                        onRetry = { onBannerAction(BannerAction.RefreshCatalog) },
                        onOpenSpeed = onOpenSpeed,
                    )
                }
                BannerSlot(
                    banner = state.banner,
                    lastRevealed = lastRevealedBanner,
                    onRevealed = { lastRevealedBanner = it },
                    onAction = onBannerAction,
                )
                EndSpacer()
            }
        }
    }
}

/** Trạng thái production khi catalog chưa có dữ liệu: không giả server, không chặn các tab khác. */
@Composable
private fun NoServerDataCard(onRetry: () -> Unit, onOpenSpeed: () -> Unit) {
    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.vpn_no_data_title),
            style = AppTheme.type.headline,
            color = AppTheme.colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(Spacing.space4))
        Text(
            text = stringResource(R.string.vpn_no_data_body),
            style = AppTheme.type.body.regular,
            color = AppTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(Spacing.space8))
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth()) {
            TextActionButton(stringResource(R.string.retry), onRetry)
            Spacer(Modifier.weight(1f))
            TextActionButton(stringResource(R.string.vpn_open_speed), onOpenSpeed)
        }
    }
}

/** Pill + tiêu đề trạng thái + subtitle (giữ 2 dòng để không nhảy layout). */
@Composable
private fun StatusHeader(state: VpnUiState) {
    val c = AppTheme.colors
    val (pillVariant, pillRes) = when (state.phase) {
        VpnPhase.Restoring -> StatusPillVariant.Connecting to R.string.vpn_pill_checking
        VpnPhase.Disconnected -> StatusPillVariant.Disconnected to R.string.vpn_pill_disconnected
        VpnPhase.AwaitingPermission -> StatusPillVariant.Connecting to R.string.vpn_pill_permission
        VpnPhase.Connecting -> StatusPillVariant.Connecting to R.string.vpn_pill_connecting
        VpnPhase.Connected -> StatusPillVariant.Connected to R.string.vpn_pill_connected
        VpnPhase.Disconnecting -> StatusPillVariant.Connecting to R.string.vpn_pill_disconnecting
    }
    val titleRes = when (state.phase) {
        VpnPhase.Restoring -> R.string.vpn_title_checking
        VpnPhase.Disconnected -> R.string.vpn_title_disconnected
        VpnPhase.AwaitingPermission, VpnPhase.Connecting -> R.string.vpn_title_connecting
        VpnPhase.Connected -> R.string.vpn_title_connected
        VpnPhase.Disconnecting -> R.string.vpn_title_disconnecting
    }
    val connection = state.connection
    val subtitle = when {
        connection is ConnectionState.Connecting -> stringResource(
            R.string.vpn_subtitle_connecting, connection.server.country, connection.attempt, connection.maxAttempts,
        )
        connection is ConnectionState.Connected && connection.server.ipAddress.isNotBlank() ->
            stringResource(R.string.vpn_subtitle_connected, connection.server.ipAddress)
        connection is ConnectionState.Connected -> connection.server.country
        else -> stringResource(
            when (state.phase) {
                VpnPhase.Restoring -> R.string.vpn_subtitle_checking
                VpnPhase.AwaitingPermission -> R.string.vpn_subtitle_permission
                VpnPhase.Disconnecting -> R.string.vpn_subtitle_disconnecting
                else -> R.string.vpn_subtitle_disconnected
            },
        )
    }

    StatusPill(variant = pillVariant, text = stringResource(pillRes))
    Spacer(Modifier.height(Spacing.space12))
    val fade = motionStd<Float>()
    AnimatedContent(
        targetState = titleRes,
        transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) },
        label = "statusTitle",
    ) { res ->
        Text(
            text = stringResource(res),
            style = AppTheme.type.title,
            color = c.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Spacer(Modifier.height(Spacing.space4))
    Text(
        text = subtitle,
        style = AppTheme.type.body.regular,
        color = c.textSecondary,
        textAlign = TextAlign.Center,
        minLines = 2,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Banner: null → không chiếm chỗ; xuất hiện/ẩn expand + fade motion_std. */
@Composable
private fun BannerSlot(
    banner: BannerModel?,
    lastRevealed: String?,
    onRevealed: (String?) -> Unit,
    onAction: (BannerAction) -> Unit,
) {
    val currentOnRevealed by rememberUpdatedState(onRevealed)
    LaunchedEffect(banner == null) {
        if (banner == null) currentOnRevealed(null) // banner mất → lần xuất hiện sau lại được tự cuộn
    }
    val fade = motionStd<Float>()
    val size = motionStd<IntSize>()
    AnimatedContent(
        targetState = banner,
        // Đổi tham số (vd. lần thử i/n) chỉ cập nhật text, không chạy lại hiệu ứng.
        contentKey = { it?.let { b -> b.variant to b.textRes } },
        transitionSpec = {
            (fadeIn(fade) + expandVertically(size, expandFrom = Alignment.Top)) togetherWith
                (fadeOut(fade) + shrinkVertically(size, shrinkTowards = Alignment.Top))
        },
        modifier = Modifier.fillMaxWidth(),
        label = "banner",
    ) { b ->
        if (b == null) {
            Box(Modifier.fillMaxWidth())
        } else {
            val requester = remember { BringIntoViewRequester() }
            val motion = AppTheme.motionEnabled
            // Banner mới (lỗi/tiến trình) thường nằm dưới mép màn → cuộn vừa đủ để thấy, sau khi expand xong.
            val key = "${b.variant}:${b.textRes}"
            LaunchedEffect(key) {
                if (key == lastRevealed) return@LaunchedEffect
                currentOnRevealed(key)
                if (motion) delay(Motion.STD.toLong())
                requester.bringIntoView()
            }
            Column(Modifier.fillMaxWidth().bringIntoViewRequester(requester)) {
                Spacer(Modifier.height(Spacing.space12))
                InlineBanner(
                    variant = b.variant,
                    text = stringResource(b.textRes, *b.args.toTypedArray()),
                    modifier = Modifier.fillMaxWidth(),
                    actionText = b.actionRes?.let { stringResource(it) },
                    onAction = b.action?.let { action -> { onAction(action) } },
                )
            }
        }
    }
}

/** CommunityNoticeSheet — render trong lớp phủ shell (xem `ShellOverlayHost`). */
@Composable
private fun CommunityNoticeSheet(
    visible: Boolean,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    onLearnMore: () -> Unit,
) {
    BottomSheetOverlay(visible = visible, onDismiss = onDismiss) {
        CommunityNoticeContent(onAccept = onAccept, onLearnMore = onLearnMore)
    }
}

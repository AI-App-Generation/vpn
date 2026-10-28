package com.thanhmich.veil.vpn.features.vpn

import android.content.Intent
import com.thanhmich.veil.vpn.data.domain.ConnectionState
import com.thanhmich.veil.vpn.data.domain.ServerSelection
import com.thanhmich.veil.vpn.data.domain.VpnServer
import com.thanhmich.veil.vpn.ui.model.BannerModel
import com.thanhmich.veil.vpn.ui.model.MainTab

/**
 * Pha hiển thị của S01, gộp từ [ConnectionState] + `isRestoring` + `isAwaitingPermission`
 * của VpnConnectionManager (Connection state ưu tiên cao nhất).
 */
enum class VpnPhase {
    /** Đang kiểm tra phiên cũ lúc khởi động: nút khoá, pill "Đang kiểm tra…". */
    Restoring,
    Disconnected,

    /** Đang chờ user trả lời hộp thoại quyền VPN (state vẫn Disconnected): nút = Huỷ. */
    AwaitingPermission,
    Connecting,
    Connected,

    /** Nút khoá, không pulse. */
    Disconnecting,
}

data class VpnUiState(
    val connection: ConnectionState = ConnectionState.Disconnected(null),
    val selection: ServerSelection = ServerSelection.Auto,
    /** Connected/Connecting: server thật đang dùng; còn lại: `ServerPicker.resolveDisplay`. */
    val displayServer: VpnServer? = null,
    /** Catalog có ≥ 1 server (Ready, hoặc Failed còn cache). */
    val catalogReady: Boolean = false,
    val banner: BannerModel? = null,
    val showCommunityNotice: Boolean = false,
    // elapsedSec: tách ra `VpnViewModel.elapsedSec: StateFlow<Long>` (chỉ SessionTimer collect) để
    // tick 1s không dựng lại toàn bộ UiState / recompose cả màn.
    // ---- thêm ở T08 ----
    val phase: VpnPhase = VpnPhase.Disconnected,
    /** Catalog đang tải lần đầu (chưa có server nào) → card skeleton. */
    val catalogLoading: Boolean = true,
    /** Nút Connect bấm được (false → alpha 0.5). */
    val connectEnabled: Boolean = false,
)

sealed interface VpnEvent {
    data class RequestVpnPermission(val intent: Intent) : VpnEvent
    /** Android 13+: xin POST_NOTIFICATIONS 1 lần trước lần connect đầu (từ chối vẫn connect). */
    data object RequestNotificationPermission : VpnEvent
    data class OpenUrl(val url: String) : VpnEvent
    data class NavigateTab(val tab: MainTab) : VpnEvent
}

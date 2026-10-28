package com.thanhmich.veil.vpn.features.vpn

import com.thanhmich.veil.vpn.R
import com.thanhmich.veil.vpn.corefeatures.vpn.ServerPicker
import com.thanhmich.veil.vpn.data.domain.CatalogState
import com.thanhmich.veil.vpn.data.domain.CatalogError
import com.thanhmich.veil.vpn.data.domain.ConnectError
import com.thanhmich.veil.vpn.data.domain.ConnectionState
import com.thanhmich.veil.vpn.data.domain.ServerCatalog
import com.thanhmich.veil.vpn.data.domain.ServerSelection
import com.thanhmich.veil.vpn.data.domain.VpnServer
import com.thanhmich.veil.vpn.ui.model.BannerAction
import com.thanhmich.veil.vpn.ui.model.BannerModel
import com.thanhmich.veil.vpn.ui.model.BannerVariant
import java.util.Locale

/** Lệnh suy ra từ 1 lần chạm nút Connect. */
internal enum class ConnectCommand { None, ShowNotice, Start, Cancel, Disconnect }

/** Hàm thuần của S01 (mapping state → UI), tách để unit test. */
internal object VpnUiLogic {

    fun catalogOf(state: CatalogState): ServerCatalog? = when (state) {
        is CatalogState.Ready -> state.catalog
        is CatalogState.Failed -> state.cached
        CatalogState.Loading -> null
    }

    fun isCatalogReady(state: CatalogState): Boolean = !catalogOf(state)?.servers.isNullOrEmpty()

    /** Chưa có server nào và đang tải → skeleton. */
    fun isCatalogLoading(state: CatalogState): Boolean = when (state) {
        CatalogState.Loading -> true
        is CatalogState.Ready -> state.refreshing && state.catalog.servers.isEmpty()
        is CatalogState.Failed -> false
    }

    fun phaseOf(connection: ConnectionState, isRestoring: Boolean, awaitingPermission: Boolean): VpnPhase = when {
        connection is ConnectionState.Connected -> VpnPhase.Connected
        connection is ConnectionState.Connecting -> VpnPhase.Connecting
        connection is ConnectionState.Disconnecting -> VpnPhase.Disconnecting
        awaitingPermission -> VpnPhase.AwaitingPermission
        isRestoring -> VpnPhase.Restoring
        else -> VpnPhase.Disconnected
    }

    fun displayServer(connection: ConnectionState, selection: ServerSelection, catalog: ServerCatalog?): VpnServer? =
        when (connection) {
            is ConnectionState.Connected -> connection.server
            is ConnectionState.Connecting -> connection.server
            else -> ServerPicker.resolveDisplay(selection, catalog)
        }

    fun isConnectEnabled(phase: VpnPhase, catalogReady: Boolean): Boolean = when (phase) {
        VpnPhase.Restoring, VpnPhase.Disconnecting -> false
        VpnPhase.Disconnected -> catalogReady
        VpnPhase.AwaitingPermission, VpnPhase.Connecting, VpnPhase.Connected -> true
    }

    fun connectCommand(phase: VpnPhase, catalogReady: Boolean, noticeSeen: Boolean): ConnectCommand = when (phase) {
        VpnPhase.Restoring, VpnPhase.Disconnecting -> ConnectCommand.None
        VpnPhase.AwaitingPermission, VpnPhase.Connecting -> ConnectCommand.Cancel
        VpnPhase.Connected -> ConnectCommand.Disconnect
        VpnPhase.Disconnected -> when {
            !catalogReady -> ConnectCommand.None
            !noticeSeen -> ConnectCommand.ShowNotice
            else -> ConnectCommand.Start
        }
    }

    /**
     * PendingSwitch (R13): đang Connected, user đã đổi selection so với lúc bắt đầu phiên
     * ([sessionSelection]) và selection mới resolve ra server khác server đang dùng.
     * So với "selection resolve ≠ server đang dùng" thuần tuý, điều kiện "đã đổi" tránh báo sai khi
     * Auto/Specific đã failover sang server khác trong cùng phiên.
     * @return server đích nếu đang PendingSwitch, ngược lại null.
     */
    fun pendingSwitchTarget(
        connection: ConnectionState,
        selection: ServerSelection,
        sessionSelection: ServerSelection?,
        catalog: ServerCatalog?,
    ): VpnServer? {
        if (connection !is ConnectionState.Connected) return null
        if (sessionSelection == null || sessionSelection == selection) return null
        val target = ServerPicker.resolveDisplay(selection, catalog) ?: return null
        return target.takeIf { it.id != connection.server.id }
    }

    /** Banner theo bảng M06 (thứ tự ưu tiên: lỗi/tiến trình kết nối → PendingSwitch → catalog). */
    fun banner(
        connection: ConnectionState,
        catalogState: CatalogState,
        selection: ServerSelection,
        pendingTarget: VpnServer?,
    ): BannerModel? = when (connection) {
        is ConnectionState.Connecting ->
            if (connection.attempt >= 2) {
                BannerModel(
                    BannerVariant.Info,
                    R.string.banner_retrying_server,
                    listOf(connection.server.country, connection.attempt, connection.maxAttempts),
                )
            } else null

        is ConnectionState.Disconnected -> connection.error?.let(::errorBanner) ?: catalogBanner(catalogState)

        is ConnectionState.Connected -> when {
            pendingTarget == null -> catalogBanner(catalogState)
            selection is ServerSelection.Auto -> BannerModel(
                BannerVariant.Info, R.string.banner_pending_switch_auto,
                actionRes = R.string.action_switch_now, action = BannerAction.SwitchNow,
            )
            else -> BannerModel(
                BannerVariant.Info, R.string.banner_pending_switch, listOf(pendingTarget.country),
                actionRes = R.string.action_switch_now, action = BannerAction.SwitchNow,
            )
        }

        ConnectionState.Disconnecting -> null
    }

    fun errorBanner(error: ConnectError): BannerModel = when (error) {
        ConnectError.NoNetwork -> BannerModel(
            BannerVariant.Offline, R.string.no_network_connection,
            actionRes = R.string.retry, action = BannerAction.RetryConnect,
        )
        ConnectError.PermissionDenied -> BannerModel(
            BannerVariant.Warning, R.string.banner_permission_denied,
            actionRes = R.string.retry, action = BannerAction.RetryConnect,
        )
        ConnectError.AllServersFailed -> BannerModel(
            BannerVariant.Error, R.string.banner_all_servers_failed,
            actionRes = R.string.retry, action = BannerAction.RetryConnect,
        )
        ConnectError.ConnectionLost -> BannerModel(
            BannerVariant.Error, R.string.banner_connection_lost,
            actionRes = R.string.action_reconnect, action = BannerAction.Reconnect,
        )
        ConnectError.NoServers -> BannerModel(
            BannerVariant.Error, R.string.banner_no_servers,
            actionRes = R.string.retry, action = BannerAction.RefreshCatalog,
        )
    }

    /** Catalog lỗi không có data → catalog_failed; tải xong nhưng rỗng → no_servers. */
    fun catalogBanner(state: CatalogState): BannerModel? = when (state) {
        is CatalogState.Failed ->
            if (state.cached?.servers.isNullOrEmpty()) {
                BannerModel(
                    if (state.reason == CatalogError.Network) BannerVariant.Offline else BannerVariant.Error,
                    if (state.reason == CatalogError.Network) R.string.banner_catalog_offline else R.string.banner_catalog_failed,
                    actionRes = R.string.retry, action = BannerAction.RefreshCatalog,
                )
            } else null
        is CatalogState.Ready ->
            if (!state.refreshing && state.catalog.servers.isEmpty()) errorBanner(ConnectError.NoServers) else null
        CatalogState.Loading -> null
    }

    /** Cancel đến trong [DOUBLE_TAP_GUARD_MS] sau Start bị bỏ qua (double-tap vô tình). */
    const val DOUBLE_TAP_GUARD_MS = 400L

    fun isWithinDoubleTapGuard(nowMs: Long, lastStartAtMs: Long): Boolean =
        lastStartAtMs > 0L && nowMs - lastStartAtMs in 0 until DOUBLE_TAP_GUARD_MS

    fun elapsedSec(nowWallMs: Long, connectedAtWallMs: Long): Long =
        ((nowWallMs - connectedAtWallMs) / 1000L).coerceAtLeast(0L)

    /** "HH:mm:ss" (giờ không giới hạn 2 chữ số), số ASCII. */
    fun formatElapsed(sec: Long): String {
        val s = sec.coerceAtLeast(0L)
        return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    }

    /** Hostname rút gọn: phần trước dấu '.' đầu tiên. */
    fun shortHost(hostname: String): String = hostname.substringBefore('.').ifBlank { hostname }
}

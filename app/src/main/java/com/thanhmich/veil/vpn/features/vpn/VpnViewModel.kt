package com.thanhmich.veil.vpn.features.vpn

import android.os.Build
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thanhmich.veil.vpn.core.AppGraph
import com.thanhmich.veil.vpn.core.SettingManager
import com.thanhmich.veil.vpn.data.domain.CatalogState
import com.thanhmich.veil.vpn.data.domain.ConnectionState
import com.thanhmich.veil.vpn.data.domain.ServerSelection
import com.thanhmich.veil.vpn.ui.model.BannerAction
import com.thanhmich.veil.vpn.ui.model.MainTab
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * S01 — gộp state từ VpnConnectionManager / ServerRepo / SettingManager thành [VpnUiState].
 * Mọi lệnh gọi từ main thread (Compose) → an toàn với yêu cầu main thread của M06.
 */
class VpnViewModel : ViewModel() {

    private val connectionManager = AppGraph.connectionManager
    private val serverRepo = AppGraph.serverRepo

    private val showNotice = MutableStateFlow(false)

    /**
     * Selection lúc bắt đầu lượt connect hiện tại (null khi không Connecting/Connected).
     * Dùng để phát hiện PendingSwitch: user đổi selection sau khi đã kết nối.
     */
    private val sessionSelection = MutableStateFlow<ServerSelection?>(null)

    /** Đã phát RequestNotificationPermission, chờ kết quả rồi mới connect. */
    private var connectAfterNotification = false

    /** elapsedRealtime của lần Start gần nhất (chống double-tap Start → Cancel). */
    private var lastStartAt = 0L

    private val _events = Channel<VpnEvent>(Channel.BUFFERED)
    val events: Flow<VpnEvent> = _events.receiveAsFlow()

    private data class Inputs(
        val connection: ConnectionState,
        val restoring: Boolean,
        val awaitingPermission: Boolean,
        val catalog: CatalogState,
        val selection: ServerSelection,
    )

    /** Giây kể từ connectedAtWallMs (0 khi không Connected), tick 1s — chỉ SessionTimer collect. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val elapsedSec: StateFlow<Long> = connectionManager.state
        .map { (it as? ConnectionState.Connected)?.connectedAtWallMs }
        .distinctUntilChanged()
        .flatMapLatest { connectedAt ->
            if (connectedAt == null) flowOf(0L) else flow {
                while (true) {
                    // Timer tính từ thời điểm đã lưu (R11), tick khớp ranh giới giây.
                    val now = System.currentTimeMillis()
                    emit(VpnUiLogic.elapsedSec(now, connectedAt))
                    val intoSecond = (now - connectedAt).mod(1000L)
                    delay((1000L - intoSecond).coerceIn(1L, 1000L))
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), currentElapsed())

    private val inputs: Flow<Inputs> = combine(
        connectionManager.state,
        connectionManager.isRestoring,
        connectionManager.isAwaitingPermission,
        serverRepo.state,
        SettingManager.selection,
    ) { connection, restoring, awaiting, catalog, selection ->
        Inputs(connection, restoring, awaiting, catalog, selection)
    }

    val uiState: StateFlow<VpnUiState> = combine(inputs, showNotice, sessionSelection) { i, notice, session ->
        build(i, notice, session)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        build(currentInputs(), showNotice.value, sessionSelection.value),
    )

    init {
        viewModelScope.launch {
            connectionManager.permissionRequests.collect { _events.send(VpnEvent.RequestVpnPermission(it)) }
        }
        viewModelScope.launch {
            var prev: ConnectionState? = null
            connectionManager.state.collect { s ->
                when (s) {
                    // Lượt connect/failover mới dùng selection tại thời điểm này.
                    is ConnectionState.Connecting ->
                        if (prev !is ConnectionState.Connecting) sessionSelection.value = SettingManager.selection.value
                    // Phiên khôi phục (VM mới) → coi selection hiện tại là selection của phiên.
                    is ConnectionState.Connected ->
                        if (sessionSelection.value == null) sessionSelection.value = SettingManager.selection.value
                    is ConnectionState.Disconnected, ConnectionState.Disconnecting -> sessionSelection.value = null
                }
                prev = s
            }
        }
    }

    // ---------------------------------------------------------------- lệnh UI

    fun onConnectClick() {
        // Đọc nguồn đồng bộ (không qua uiState — có thể trễ 1 nhịp combine khi bấm lặp nhanh).
        val phase = VpnUiLogic.phaseOf(
            connectionManager.state.value,
            connectionManager.isRestoring.value,
            connectionManager.isAwaitingPermission.value,
        )
        val catalogReady = VpnUiLogic.isCatalogReady(serverRepo.state.value)
        when (VpnUiLogic.connectCommand(phase, catalogReady, SettingManager.communityNoticeSeen.value)) {
            ConnectCommand.None -> Unit
            ConnectCommand.ShowNotice -> showNotice.value = true
            ConnectCommand.Start -> startConnect()
            ConnectCommand.Cancel ->
                if (!VpnUiLogic.isWithinDoubleTapGuard(SystemClock.elapsedRealtime(), lastStartAt)) connectionManager.cancel()
            ConnectCommand.Disconnect -> connectionManager.disconnect()
        }
    }

    /** "Tôi hiểu, kết nối" trong CommunityNoticeSheet. */
    fun onNoticeAccepted() {
        if (!showNotice.value) return // chống bấm lặp / sheet đã đóng
        SettingManager.setCommunityNoticeSeen()
        showNotice.value = false
        startConnect()
    }

    fun onNoticeDismissed() {
        showNotice.value = false
    }

    fun onLearnMoreClick() {
        _events.trySend(VpnEvent.OpenUrl(VPNGATE_URL))
    }

    fun onServerCardClick() {
        _events.trySend(VpnEvent.NavigateTab(MainTab.Locations))
    }

    fun onBannerAction(action: BannerAction) {
        when (action) {
            BannerAction.RetryConnect, BannerAction.Reconnect -> requestConnect()
            BannerAction.SwitchNow -> connectionManager.reconnect()
            BannerAction.RefreshCatalog -> serverRepo.refresh()
        }
    }

    /** Kết quả hộp thoại quyền VPN (`VpnService.prepare`). */
    fun onVpnPermissionResult(granted: Boolean) {
        connectionManager.onPermissionResult(granted)
    }

    /** Kết quả POST_NOTIFICATIONS (từ chối vẫn connect — R8). */
    fun onNotificationPermissionResult() {
        SettingManager.setNotificationAsked()
        if (connectAfterNotification) {
            connectAfterNotification = false
            connectionManager.connect()
        }
    }

    // ---------------------------------------------------------------- nội bộ

    /** Connect qua notice sheet nếu chưa xem (dùng cho banner "Thử lại"). */
    private fun requestConnect() {
        if (!SettingManager.communityNoticeSeen.value) showNotice.value = true else startConnect()
    }

    /** Android 13+: hỏi POST_NOTIFICATIONS 1 lần trước lần connect đầu, rồi connect. */
    private fun startConnect() {
        lastStartAt = SystemClock.elapsedRealtime()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !SettingManager.notificationAsked.value) {
            if (connectAfterNotification) return // đang chờ kết quả, chống bấm lặp
            connectAfterNotification = true
            _events.trySend(VpnEvent.RequestNotificationPermission)
        } else {
            connectionManager.connect()
        }
    }

    private fun currentInputs() = Inputs(
        connectionManager.state.value,
        connectionManager.isRestoring.value,
        connectionManager.isAwaitingPermission.value,
        serverRepo.state.value,
        SettingManager.selection.value,
    )

    private fun currentElapsed(): Long =
        (connectionManager.state.value as? ConnectionState.Connected)
            ?.let { VpnUiLogic.elapsedSec(System.currentTimeMillis(), it.connectedAtWallMs) } ?: 0L

    private fun build(i: Inputs, notice: Boolean, session: ServerSelection?): VpnUiState {
        val catalog = VpnUiLogic.catalogOf(i.catalog)
        val catalogReady = VpnUiLogic.isCatalogReady(i.catalog)
        val phase = VpnUiLogic.phaseOf(i.connection, i.restoring, i.awaitingPermission)
        val pending = VpnUiLogic.pendingSwitchTarget(i.connection, i.selection, session, catalog)
        return VpnUiState(
            connection = i.connection,
            selection = i.selection,
            displayServer = VpnUiLogic.displayServer(i.connection, i.selection, catalog),
            catalogReady = catalogReady,
            banner = VpnUiLogic.banner(i.connection, i.catalog, i.selection, pending),
            showCommunityNotice = notice,
            phase = phase,
            catalogLoading = VpnUiLogic.isCatalogLoading(i.catalog),
            connectEnabled = VpnUiLogic.isConnectEnabled(phase, catalogReady),
        )
    }

    private companion object {
        const val VPNGATE_URL = "https://www.vpngate.net"
    }
}

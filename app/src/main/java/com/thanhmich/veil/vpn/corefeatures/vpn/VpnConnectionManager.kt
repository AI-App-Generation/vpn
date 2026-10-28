package com.thanhmich.veil.vpn.corefeatures.vpn

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Looper
import com.thanhmich.veil.vpn.BuildConfig
import com.thanhmich.veil.vpn.core.AppGraph
import com.thanhmich.veil.vpn.core.SettingManager
import com.thanhmich.veil.vpn.corefeatures.vpn.engine.EngineStatus
import com.thanhmich.veil.vpn.corefeatures.vpn.engine.VpnEngine
import com.thanhmich.veil.vpn.data.domain.ActiveSession
import com.thanhmich.veil.vpn.data.domain.CatalogState
import com.thanhmich.veil.vpn.data.domain.ConnectError
import com.thanhmich.veil.vpn.data.domain.ConnectStep
import com.thanhmich.veil.vpn.data.domain.ConnectionState
import com.thanhmich.veil.vpn.data.domain.ServerCatalog
import com.thanhmich.veil.vpn.data.domain.ServerSelection
import com.thanhmich.veil.vpn.data.domain.VpnServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Kiểm tra có mạng trước khi connect (M06 bước 1). */
fun interface NetworkChecker {
    fun isOnline(): Boolean
}

/** `ConnectivityManager.activeNetwork != null` (minSdk 24 ≥ 23). */
class ConnectivityNetworkChecker(context: Context) : NetworkChecker {
    private val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?

    override fun isOnline(): Boolean = try {
        cm?.activeNetwork != null
    } catch (_: SecurityException) {
        true // không kiểm được → để bước connect tự thất bại
    }
}

/** Nơi lưu [ActiveSession] (R11). Mặc định: [SettingManager]. */
interface SessionStore {
    /** Chờ dữ liệu persist nạp xong (giá trị [current] là thật). */
    suspend fun awaitLoaded()
    val current: ActiveSession?
    fun save(session: ActiveSession?)
}

object SettingSessionStore : SessionStore {
    override suspend fun awaitLoaded() {
        SettingManager.isLoaded.first { it }
    }

    override val current: ActiveSession? get() = SettingManager.activeSession.value
    override fun save(session: ActiveSession?) = SettingManager.setActiveSession(session)
}

/**
 * M06 — state machine kết nối + failover + phiên.
 *
 * **Thread**: mọi lệnh public gọi từ main thread (debug build có check); coroutine chạy trên [scope]
 * (mặc định `Dispatchers.Main.immediate`) → state chỉ bị đổi trên 1 thread, không cần khoá.
 *
 * Luồng `connect()`: mạng → quyền VPN → `ServerPicker.candidates` (≤ 6, bỏ `failedIds`) → mỗi
 * candidate: FetchingProfile (≤ 12s) → Handshaking (`engine.start`, chờ Connected ≤ 20s tính từ
 * start; Idle thoáng qua bị bỏ qua, chỉ AuthFailed/Error/timeout là thất bại) → lỗi: `engine.stop()`,
 * chờ Idle ≤ 3s (bỏ qua nếu start lỗi đồng bộ), `failedIds += id`, candidate kế. Hết candidate hoặc
 * hết trần 120s cho cả lượt → `AllServersFailed` + xoá failedIds.
 *
 * Trong Connected: engine về Idle/Error/AuthFailed, hoặc Connecting liên tục > 30s → server bị đánh
 * dấu failed, failover lại từ đầu (đếm attempt mới); NoNetwork kéo dài > 20s → `ConnectionLost`.
 *
 * Bất biến phiên: [ActiveSession] được lưu khi vào Connected và xoá khi rời Connected.
 */
class VpnConnectionManager(
    private val engine: VpnEngine = AppGraph.vpnEngine,
    private val catalogState: StateFlow<CatalogState> = AppGraph.serverRepo.state,
    private val selectionProvider: () -> ServerSelection = { SettingManager.selection.value },
    private val sessionStore: SessionStore = SettingSessionStore,
    private val network: NetworkChecker = ConnectivityNetworkChecker(AppGraph.app),
    private val profileLoader: ProfileLoader = OvpnProfileLoader(),
    private val wallClock: () -> Long = System::currentTimeMillis,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected(null))
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    /** Intent xin quyền VPN (`VpnService.prepare`); UI launch rồi gọi [onPermissionResult]. */
    private val permissionChannel = Channel<Intent>(Channel.CONFLATED)
    val permissionRequests: Flow<Intent> = permissionChannel.receiveAsFlow()

    private val _isAwaitingPermission = MutableStateFlow(false)
    /** true khi đang chờ user trả lời hộp thoại quyền VPN. */
    val isAwaitingPermission: StateFlow<Boolean> = _isAwaitingPermission.asStateFlow()

    private val _isRestoring = MutableStateFlow(false)
    /** true khi đang kiểm tra phiên cũ lúc khởi động (UI khoá nút Connect, pill "Đang kiểm tra…"). */
    val isRestoring: StateFlow<Boolean> = _isRestoring.asStateFlow()

    /** Job duy nhất đang điều khiển kết nối (connect/failover/monitor/disconnect). */
    private var job: Job? = null
    private var restoreJob: Job? = null
    private var permissionWaiter: CompletableDeferred<Boolean>? = null
        set(value) {
            field = value
            _isAwaitingPermission.value = value != null
        }

    /** Server lỗi trong process này (in-memory). */
    private val failedIds = LinkedHashSet<String>()

    /** Chỉ để test/debug. */
    internal val failedServerIds: Set<String> get() = failedIds.toSet()

    init {
        _isRestoring.value = true
        restoreJob = scope.launch {
            try {
                restoreSession()
            } finally {
                _isRestoring.value = false
            }
        }
    }

    // ---------------------------------------------------------------- lệnh

    /** Kết nối theo selection hiện tại. Chỉ nhận khi Disconnected (đang chờ quyền → xin lại). */
    fun connect() {
        assertMainThread()
        if (_state.value !is ConnectionState.Disconnected) return
        launchExclusive { connectFlow() }
    }

    /** Kết quả hộp thoại quyền VPN. Bỏ qua nếu không có yêu cầu đang chờ. */
    fun onPermissionResult(granted: Boolean) {
        assertMainThread()
        val waiter = permissionWaiter ?: return
        permissionWaiter = null
        waiter.complete(granted)
    }

    /** Huỷ khi đang Connecting (hoặc đang chờ quyền). */
    fun cancel() {
        assertMainThread()
        when {
            _state.value is ConnectionState.Connecting -> stopAndDisconnect()
            permissionWaiter != null -> {
                job?.cancel()
                job = null
                clearPermissionRequest()
                _state.value = ConnectionState.Disconnected(null)
            }
        }
    }

    /** Ngắt khi Connected (hoặc Connecting). */
    fun disconnect() {
        assertMainThread()
        val s = _state.value
        if (s is ConnectionState.Connected || s is ConnectionState.Connecting) stopAndDisconnect()
    }

    /** PendingSwitch: Connected → ngắt rồi connect theo selection mới. Disconnected → như [connect]. */
    fun reconnect() {
        assertMainThread()
        when (_state.value) {
            is ConnectionState.Connected -> stopAndDisconnect { connectFlow() }
            is ConnectionState.Disconnected -> connect()
            else -> Unit
        }
    }

    // ---------------------------------------------------------------- nội bộ

    private fun assertMainThread() {
        if (BuildConfig.DEBUG) {
            check(Looper.myLooper() == Looper.getMainLooper()) { "VpnConnectionManager: gọi từ main thread" }
        }
    }

    private fun cancelRestore() {
        restoreJob?.cancel()
        restoreJob = null
        _isRestoring.value = false
    }

    /** Bỏ yêu cầu quyền đang chờ + intent cũ chưa được UI nhận. */
    private fun clearPermissionRequest() {
        permissionWaiter = null
        while (permissionChannel.tryReceive().isSuccess) Unit
    }

    /** Huỷ job cũ (và restore) trước khi đổi state (R12), rồi chạy [block]. */
    private fun launchExclusive(block: suspend () -> Unit) {
        cancelRestore()
        job?.cancel()
        clearPermissionRequest()
        job = scope.launch { block() }
    }

    private fun stopAndDisconnect(then: (suspend () -> Unit)? = null) {
        val s = _state.value
        // Đang tải profile và engine chưa chạy → không cần stop (tránh bind/khởi tạo service :openvpn).
        val needsStop = s is ConnectionState.Connected ||
            (s is ConnectionState.Connecting && s.step == ConnectStep.Handshaking) ||
            engine.status.value != EngineStatus.Idle
        cancelRestore()
        job?.cancel()
        clearPermissionRequest()
        _state.value = ConnectionState.Disconnecting
        sessionStore.save(null)
        if (needsStop) engine.stop()
        job = scope.launch {
            if (needsStop) awaitIdle()
            _state.value = ConnectionState.Disconnected(null)
            then?.invoke()
        }
    }

    private suspend fun connectFlow() {
        _state.value = ConnectionState.Disconnected(null)
        if (!network.isOnline()) {
            toDisconnected(ConnectError.NoNetwork)
            return
        }
        val intent = engine.prepareIntent()
        if (intent != null) {
            val waiter = CompletableDeferred<Boolean>()
            permissionWaiter = waiter
            permissionChannel.trySend(intent)
            val granted = try {
                waiter.await()
            } finally {
                if (permissionWaiter === waiter) permissionWaiter = null
            }
            if (!granted) {
                toDisconnected(ConnectError.PermissionDenied)
                return
            }
        }
        runSession(startServer = null, fromLoss = false)
    }

    /**
     * Vòng đời phiên: failover tới khi Connected → theo dõi → mất kết nối thì failover lại.
     * [startServer] ≠ null: đã Connected sẵn (khôi phục phiên), bắt đầu từ bước theo dõi.
     */
    private suspend fun runSession(startServer: VpnServer?, fromLoss: Boolean) {
        var server = startServer
        var lossMode = fromLoss
        while (true) {
            val current = server ?: failover(lossMode) ?: return
            server = null
            when (monitorConnected()) {
                Loss.NetworkLost -> {
                    engine.stop()
                    toDisconnected(ConnectError.ConnectionLost)
                    return
                }
                Loss.Dropped -> {
                    failedIds += current.id
                    // Không giữ Connected sai trong lúc chờ engine dừng.
                    val next = ServerPicker.candidates(selectionProvider(), currentCatalog(), failedIds, MAX_CANDIDATES)
                    _state.value = ConnectionState.Connecting(current, 1, next.size.coerceAtLeast(1), ConnectStep.Handshaking)
                    sessionStore.save(null)
                    engine.stop()
                    awaitIdle()
                    if (!network.isOnline()) {
                        toDisconnected(ConnectError.ConnectionLost)
                        return
                    }
                    lossMode = true
                }
            }
        }
    }

    private sealed interface Attempt {
        data class Success(val server: VpnServer) : Attempt
        data class Exhausted(val allBackgroundBlocked: Boolean) : Attempt
    }

    /**
     * 1 lượt failover (trần [FAILOVER_BUDGET_MS]). Thành công → server (state Connected, đã lưu
     * session); hết candidate / hết giờ → null (state Disconnected).
     */
    private suspend fun failover(fromLoss: Boolean): VpnServer? {
        val catalog = currentCatalog()
        val list = ServerPicker.candidates(selectionProvider(), catalog, failedIds, MAX_CANDIDATES)
        if (list.isEmpty()) {
            if (catalog == null || catalog.servers.isEmpty()) {
                toDisconnected(ConnectError.NoServers)
            } else {
                failedIds.clear()
                toDisconnected(ConnectError.AllServersFailed)
            }
            return null
        }

        val attempt = withTimeoutOrNull(FAILOVER_BUDGET_MS) { tryCandidates(list) }
        currentCoroutineContext().ensureActive()
        when (attempt) {
            is Attempt.Success -> return attempt.server
            is Attempt.Exhausted -> {
                failedIds.clear()
                // Failover từ nền mà mọi lần đều bị Android chặn start FGS → coi là mất kết nối.
                val error = if (fromLoss && attempt.allBackgroundBlocked) ConnectError.ConnectionLost
                else ConnectError.AllServersFailed
                toDisconnected(error)
            }
            null -> {
                // Hết trần thời gian: dừng candidate đang dở.
                if (engine.status.value != EngineStatus.Idle ||
                    (_state.value as? ConnectionState.Connecting)?.step == ConnectStep.Handshaking
                ) {
                    engine.stop()
                }
                failedIds.clear()
                toDisconnected(ConnectError.AllServersFailed)
            }
        }
        return null
    }

    private suspend fun tryCandidates(list: List<VpnServer>): Attempt {
        val total = list.size
        var allBackgroundBlocked = true
        for ((index, server) in list.withIndex()) {
            val attempt = index + 1
            _state.value = ConnectionState.Connecting(server, attempt, total, ConnectStep.FetchingProfile)
            val profile = loadProfile(server)
            currentCoroutineContext().ensureActive()
            if (profile == null) {
                allBackgroundBlocked = false
                failedIds += server.id
                continue
            }

            _state.value = ConnectionState.Connecting(server, attempt, total, ConnectStep.Handshaking)
            val outcome = startAndAwait(server, profile)
            if (outcome.status == EngineStatus.Connected) {
                val connected = ConnectionState.Connected(server, wallClock())
                _state.value = connected
                sessionStore.save(ActiveSession(server, connected.connectedAtWallMs))
                return Attempt.Success(server)
            }
            val status = outcome.status
            if (!(status is EngineStatus.Error && isBackgroundStartBlocked(status))) allBackgroundBlocked = false
            if (!outcome.synchronousFailure) {
                engine.stop()
                awaitIdle()
            }
            failedIds += server.id
        }
        return Attempt.Exhausted(allBackgroundBlocked)
    }

    private suspend fun loadProfile(server: VpnServer): String? = try {
        withTimeoutOrNull(PROFILE_TIMEOUT_MS) { profileLoader.load(server) }?.getOrNull()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    /** [status] null = timeout. [synchronousFailure]: start lỗi ngay (chưa từng Connecting) → không cần stop. */
    private data class StartOutcome(val status: EngineStatus?, val synchronousFailure: Boolean)

    /**
     * `engine.start` rồi chờ tối đa [HANDSHAKE_TIMEOUT_MS] Connected/AuthFailed/Error.
     * Idle/NoNetwork/Connecting trong lúc bắt tay KHÔNG kết thúc chờ (Idle thoáng qua ngay sau start;
     * openvpn thoát vì lỗi fatal cũng về Idle → chỉ bắt được bằng timeout).
     *
     * Dựa vào hợp đồng engine: `start()` đặt status Connecting/Error đồng bộ, nên status cũ
     * (Connected/Error của lần trước) không bị hiểu nhầm, và Error ngay sau start = lỗi đồng bộ.
     */
    private suspend fun startAndAwait(server: VpnServer, profile: String): StartOutcome {
        try {
            engine.start(profile, VPN_USERNAME, VPN_PASSWORD, displayName(server))
        } catch (e: RuntimeException) {
            return StartOutcome(EngineStatus.Error("start threw ${e.javaClass.simpleName}: ${e.message}"), true)
        }
        val immediate = engine.status.value
        if (immediate is EngineStatus.Error) return StartOutcome(immediate, synchronousFailure = true)
        val result = withTimeoutOrNull(HANDSHAKE_TIMEOUT_MS) {
            engine.status.first {
                it == EngineStatus.Connected || it == EngineStatus.AuthFailed || it is EngineStatus.Error
            }
        }
        return StartOutcome(result, synchronousFailure = false)
    }

    private enum class Loss { Dropped, NetworkLost }

    /**
     * Chờ tới khi phiên Connected kết thúc không do user:
     * - Idle/Error/AuthFailed → Dropped.
     * - Connecting liên tục > [RECONNECT_GRACE_MS] (openvpn tự reconnect vô hạn khi server chết) → Dropped.
     * - NoNetwork mà > [NO_NETWORK_GRACE_MS] chưa Connected lại → NetworkLost.
     */
    private suspend fun monitorConnected(): Loss {
        while (true) {
            when (engine.status.value) {
                EngineStatus.Connected -> engine.status.first { it != EngineStatus.Connected }
                EngineStatus.Connecting -> withTimeoutOrNull(RECONNECT_GRACE_MS) {
                    engine.status.first { it != EngineStatus.Connecting }
                } ?: return Loss.Dropped
                EngineStatus.NoNetwork -> {
                    val resolved = withTimeoutOrNull(NO_NETWORK_GRACE_MS) {
                        engine.status.first {
                            it == EngineStatus.Connected || it == EngineStatus.Idle ||
                                it == EngineStatus.AuthFailed || it is EngineStatus.Error
                        }
                    } ?: return Loss.NetworkLost
                    if (resolved != EngineStatus.Connected) return Loss.Dropped
                }
                else -> return Loss.Dropped
            }
        }
    }

    private suspend fun awaitIdle() {
        if (engine.status.value == EngineStatus.Idle) return
        withTimeoutOrNull(STOP_IDLE_TIMEOUT_MS) { engine.status.first { it == EngineStatus.Idle } }
    }

    /**
     * Khôi phục khi process app khởi động lại (R11). `engine.status` là Idle tới khi StatusListener
     * bind xong → chờ tối đa [RESTORE_WAIT_MS].
     * - Có session + engine Connected/Connecting/NoNetwork → dựng Connected (giữ connectedAtWallMs) và theo dõi.
     * - Có session, engine không sống → xoá session.
     * - Không session mà engine vẫn Connected/Connecting sau 2s → tunnel mồ côi → `engine.stop()`.
     */
    private suspend fun restoreSession() {
        sessionStore.awaitLoaded()
        val session = sessionStore.current
        if (session == null) {
            _isRestoring.value = false
            delay(RESTORE_WAIT_MS)
            val s = engine.status.value
            if (_state.value == ConnectionState.Disconnected(null) && job?.isActive != true &&
                (s == EngineStatus.Connected || s == EngineStatus.Connecting)
            ) {
                engine.stop()
            }
            restoreJob = null
            return
        }
        val alive = isAliveForRestore(engine.status.value) ||
            withTimeoutOrNull(RESTORE_WAIT_MS) { engine.status.first { isAliveForRestore(it) } } != null
        if (_state.value != ConnectionState.Disconnected(null) || job?.isActive == true) return
        if (alive) {
            _state.value = ConnectionState.Connected(session.server, session.connectedAtWallMs)
            job = scope.launch { runSession(startServer = session.server, fromLoss = false) }
        } else {
            sessionStore.save(null)
        }
        restoreJob = null
    }

    private fun isAliveForRestore(s: EngineStatus) =
        s == EngineStatus.Connected || s == EngineStatus.Connecting || s == EngineStatus.NoNetwork

    private fun toDisconnected(error: ConnectError?) {
        _state.value = ConnectionState.Disconnected(error)
        sessionStore.save(null)
    }

    private fun currentCatalog(): ServerCatalog? = when (val s = catalogState.value) {
        is CatalogState.Ready -> s.catalog
        is CatalogState.Failed -> s.cached
        CatalogState.Loading -> null
    }

    companion object {
        const val MAX_CANDIDATES = ServerPicker.DEFAULT_MAX_CANDIDATES
        const val PROFILE_TIMEOUT_MS = 12_000L
        const val HANDSHAKE_TIMEOUT_MS = 20_000L
        const val STOP_IDLE_TIMEOUT_MS = 3_000L
        const val NO_NETWORK_GRACE_MS = 20_000L
        const val RECONNECT_GRACE_MS = 30_000L
        const val FAILOVER_BUDGET_MS = 120_000L
        const val RESTORE_WAIT_MS = 2_000L

        /** VPNGate: username/password cố định (MOBILE_INTEGRATION bước 5). */
        const val VPN_USERNAME = "vpn"
        const val VPN_PASSWORD = "vpn"

        internal fun displayName(server: VpnServer): String = "${server.country} · ${server.hostname}"

        /** Android 12+ chặn start foreground service từ nền (IcsOpenVpnEngine ghi tên exception vào message). */
        internal fun isBackgroundStartBlocked(error: EngineStatus.Error): Boolean =
            error.message.contains("ForegroundServiceStartNotAllowed")
    }
}

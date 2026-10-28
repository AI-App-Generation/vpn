package com.thanhmich.veil.vpn.core

import android.app.Application
import com.thanhmich.veil.vpn.corefeatures.speedtest.SpeedRoute
import com.thanhmich.veil.vpn.corefeatures.speedtest.SpeedTestEngine
import com.thanhmich.veil.vpn.corefeatures.vpn.ConnectivityNetworkChecker
import com.thanhmich.veil.vpn.corefeatures.vpn.VpnConnectionManager
import com.thanhmich.veil.vpn.corefeatures.vpn.engine.IcsOpenVpnEngine
import com.thanhmich.veil.vpn.corefeatures.vpn.engine.VpnEngine
import com.thanhmich.veil.vpn.data.api.ServerRepo

/**
 * Composition root thay cho DI: giữ các singleton của app, khởi tạo 1 lần trong
 * `GlobalApp.onCreate` (sau `SettingManager.init`). ViewModel lấy dependency từ đây.
 *
 * Các singleton sẽ được thêm ở task sau (khai báo `lateinit var ... private set`
 * và gán trong [init]):
 * - `serverRepo: ServerRepo`                     (M02, `data/api/ServerRepo.kt`) — đã có
 * - `vpnEngine: VpnEngine`                       (M05, `corefeatures/vpn/engine/`) — đã có
 * - `connectionManager: VpnConnectionManager`    (M06, `corefeatures/vpn/VpnConnectionManager.kt`) — đã có
 * - `speedTestEngine: SpeedTestEngine`           (M07, `corefeatures/speedtest/SpeedTestEngine.kt`) — đã có
 */
object AppGraph {

    lateinit var app: Application
        private set

    val isInitialized: Boolean get() = ::app.isInitialized

    /** M02 — catalog server từ Firestore. */
    lateinit var serverRepo: ServerRepo
        private set

    /** M05 — engine OpenVPN (ics-openvpn). Cần `StatusListener().init` chạy trước (GlobalApp). */
    lateinit var vpnEngine: VpnEngine
        private set

    /** M06 — state machine kết nối + failover + phiên. Lệnh gọi từ main thread. */
    lateinit var connectionManager: VpnConnectionManager
        private set

    /** M07 — speed test HTTP (Cloudflare). Tuyến VPN lấy từ [connectionManager] lúc bắt đầu đo. */
    lateinit var speedTestEngine: SpeedTestEngine
        private set

    fun init(app: Application) {
        if (isInitialized) return
        this.app = app
        // Thứ tự khởi tạo: serverRepo → vpnEngine → connectionManager → speedTestEngine
        serverRepo = ServerRepo().also { it.start() }
        vpnEngine = IcsOpenVpnEngine(app)
        connectionManager = VpnConnectionManager(
            engine = vpnEngine,
            catalogState = serverRepo.state,
            network = ConnectivityNetworkChecker(app),
        )
        speedTestEngine = SpeedTestEngine(
            network = ConnectivityNetworkChecker(app),
            routeProvider = { SpeedRoute.from(connectionManager.state.value) },
        )
    }
}

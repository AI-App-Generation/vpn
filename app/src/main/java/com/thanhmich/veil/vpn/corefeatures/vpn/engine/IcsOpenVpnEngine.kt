package com.thanhmich.veil.vpn.corefeatures.vpn.engine

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.VpnService
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import de.blinkt.openvpn.core.ConfigParser
import de.blinkt.openvpn.core.ConnectionStatus
import de.blinkt.openvpn.core.IOpenVPNServiceInternal
import de.blinkt.openvpn.core.OpenVPNService
import de.blinkt.openvpn.core.ProfileManager
import de.blinkt.openvpn.core.VPNLaunchHelper
import de.blinkt.openvpn.core.VpnStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * [VpnEngine] dùng ics-openvpn (OpenVPN 2.x, module `:vpnLib`).
 *
 * `OpenVPNService` chạy ở process `:openvpn`; status tới được process app nhờ
 * `StatusListener().init(app)` gọi trong `GlobalApp` (R3) — engine chỉ nghe [VpnStatus].
 * Chỉ tạo 1 instance (AppGraph), trong main process.
 *
 * Quy tắc trạng thái:
 * - [EngineStatus.Idle] chỉ đến từ listener (NOTCONNECTED) — engine không tự đoán Idle.
 * - [EngineStatus.Error] được giữ tới lần [start]/[stop] kế tiếp (NOTCONNECTED đến muộn không đè).
 * - Sau [stop], nếu service lại báo Connecting/Connected (lệnh start đến muộn) → gọi stop lại.
 *
 * Thread: [start]/[stop] gọi từ thread bất kỳ; vòng stop chạy trên main looper; listener có thể
 * chạy trên binder thread → trạng thái dùng Atomic/Volatile + MutableStateFlow.
 */
class IcsOpenVpnEngine(context: Context) : VpnEngine {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _status = MutableStateFlow<EngineStatus>(EngineStatus.Idle)
    override val status: StateFlow<EngineStatus> = _status.asStateFlow()

    /** Tăng mỗi lần start/stop để vòng stop cũ tự huỷ khi có lệnh mới. */
    private val generation = AtomicInteger(0)

    /** true từ [stop] tới [start] kế tiếp: mọi Connecting/Connected nhận được đều bị dừng lại. */
    @Volatile
    private var stopRequested = false

    /** true khi đang giữ [EngineStatus.Error] (tới lần start/stop kế tiếp). */
    @Volatile
    private var errorLatched = false

    /** Có vòng stop (bind + retry) đang chạy. */
    private val stopLoopActive = AtomicBoolean(false)

    private val stateListener = object : VpnStatus.StateListener {
        override fun updateState(
            state: String?,
            logmessage: String?,
            localizedResId: Int,
            level: ConnectionStatus?,
            intent: Intent?,
        ) = onLevel(level)

        override fun setConnectedVPN(uuid: String?) = Unit
    }

    init {
        // addStateListener phát ngay trạng thái cuối (khôi phục khi process UI khởi động lại).
        VpnStatus.addStateListener(stateListener)
    }

    override fun prepareIntent(): Intent? = VpnService.prepare(appContext)

    override fun start(ovpn: String, username: String, password: String, displayName: String) {
        generation.incrementAndGet()
        stopRequested = false
        errorLatched = false

        val profile = try {
            IcsProfileBuilder.build(ovpn, username, password, displayName)
        } catch (e: ConfigParser.ConfigParseError) {
            fail("Config parse error: ${e.message}")
            return
        } catch (e: IOException) {
            fail("Config read error: ${e.message}")
            return
        }

        // Vd. cipher BF-CBC → checkProfile báo lỗi → Error (M06 coi là server lỗi).
        val checkRes = profile.checkProfile(appContext, false)
        if (checkRes != de.blinkt.openvpn.R.string.no_error_found) {
            fail("Invalid profile: ${appContext.getString(checkRes)}")
            return
        }

        _status.value = EngineStatus.Connecting
        try {
            ProfileManager.setTemporaryProfile(appContext, profile)
            VPNLaunchHelper.startOpenVpn(profile, appContext, START_REASON, true)
        } catch (e: RuntimeException) {
            // vd. ForegroundServiceStartNotAllowedException, SecurityException, lỗi ghi profile.
            fail("Cannot start VPN service: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    override fun stop() {
        stopRequested = true
        errorLatched = false
        val myGeneration = generation.incrementAndGet()
        mainHandler.post { runStopLoop(myGeneration) }
    }

    /** Chỉ dùng cho test: gỡ listener khỏi [VpnStatus] (static). */
    internal fun release() {
        VpnStatus.removeStateListener(stateListener)
    }

    internal fun onLevel(level: ConnectionStatus?) {
        val mapped = mapLevel(level) ?: return
        if (errorLatched) return
        if (stopRequested && (mapped == EngineStatus.Connecting || mapped == EngineStatus.Connected)) {
            // Service vẫn/lại chạy sau khi user đã yêu cầu dừng → dừng tiếp, không báo Connected giả.
            if (!stopLoopActive.get()) {
                val g = generation.get()
                mainHandler.post { runStopLoop(g) }
            }
            return
        }
        _status.value = mapped
    }

    /** Main thread. Bind OpenVPNService rồi gọi stopVPN(false) tới khi thành công hoặc hết [STOP_WINDOW_MS]. */
    private fun runStopLoop(myGeneration: Int) {
        if (generation.get() != myGeneration || !stopRequested) return
        if (!stopLoopActive.compareAndSet(false, true)) return

        val deadline = SystemClock.uptimeMillis() + STOP_WINDOW_MS
        val intent = Intent(appContext, OpenVPNService::class.java).setAction(OpenVPNService.START_SERVICE)
        val connection = object : ServiceConnection {
            private var service: IOpenVPNServiceInternal? = null
            private var finished = false

            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                service = IOpenVPNServiceInternal.Stub.asInterface(binder)
                tryStop()
            }

            fun tryStop() {
                if (finished) return
                val svc = service
                if (svc == null || generation.get() != myGeneration) {
                    finish()
                    return
                }
                val stopped = try {
                    svc.stopVPN(false)
                } catch (e: Exception) {
                    Log.w(TAG, "stopVPN failed", e)
                    false
                }
                when {
                    // Idle sẽ đến từ listener (NOTCONNECTED).
                    stopped -> finish()
                    // Management chưa sẵn sàng (service vừa nhận lệnh start) → thử lại theo thời gian.
                    SystemClock.uptimeMillis() < deadline ->
                        mainHandler.postDelayed({ tryStop() }, STOP_RETRY_DELAY_MS)
                    // Hết cửa sổ: không có tiến trình OpenVPN nào để dừng. Không tự gán Idle;
                    // nếu sau này service báo Connecting/Connected, onLevel sẽ chạy lại vòng stop.
                    else -> finish()
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                // Process :openvpn chết: StatusListener phát NOTCONNECTED → Idle.
                service = null
            }

            private fun finish() {
                if (finished) return
                finished = true
                try {
                    appContext.unbindService(this)
                } catch (_: IllegalArgumentException) {
                }
                stopLoopActive.set(false)
            }
        }
        val bound = try {
            appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } catch (e: SecurityException) {
            Log.w(TAG, "bind OpenVPNService failed", e)
            false
        }
        if (!bound) {
            Log.w(TAG, "cannot bind OpenVPNService")
            stopLoopActive.set(false)
        }
    }

    private fun fail(message: String) {
        Log.w(TAG, message)
        errorLatched = true
        _status.value = EngineStatus.Error(message)
    }

    companion object {
        private const val TAG = "IcsOpenVpnEngine"
        private const val START_REASON = "Veil VPN connect"
        internal const val STOP_WINDOW_MS = 8_000L
        internal const val STOP_RETRY_DELAY_MS = 300L

        /**
         * Map theo spec M05. `null` = bỏ qua (giữ trạng thái cũ).
         * LEVEL_VPNPAUSED (user bấm Pause trên notification) coi là Connecting: tunnel sẽ quay lại
         * khi Resume, không phải lỗi server.
         */
        internal fun mapLevel(level: ConnectionStatus?): EngineStatus? = when (level) {
            ConnectionStatus.LEVEL_CONNECTED -> EngineStatus.Connected
            ConnectionStatus.LEVEL_CONNECTING_NO_SERVER_REPLY_YET,
            ConnectionStatus.LEVEL_CONNECTING_SERVER_REPLIED,
            ConnectionStatus.LEVEL_START,
            ConnectionStatus.LEVEL_WAITING_FOR_USER_INPUT,
            ConnectionStatus.LEVEL_VPNPAUSED -> EngineStatus.Connecting
            ConnectionStatus.LEVEL_AUTH_FAILED -> EngineStatus.AuthFailed
            ConnectionStatus.LEVEL_NONETWORK -> EngineStatus.NoNetwork
            ConnectionStatus.LEVEL_NOTCONNECTED -> EngineStatus.Idle
            ConnectionStatus.UNKNOWN_LEVEL, null -> null
        }
    }
}

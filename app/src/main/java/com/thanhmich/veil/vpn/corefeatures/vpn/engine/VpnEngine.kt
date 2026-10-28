package com.thanhmich.veil.vpn.corefeatures.vpn.engine

import android.content.Intent
import kotlinx.coroutines.flow.StateFlow

/** Trạng thái thô của engine VPN (M05). M06 dịch sang `ConnectionState`. */
sealed interface EngineStatus {
    data object Idle : EngineStatus
    data object Connecting : EngineStatus
    data object Connected : EngineStatus
    data object AuthFailed : EngineStatus
    data object NoNetwork : EngineStatus
    data class Error(val message: String) : EngineStatus
}

/** Bọc thư viện VPN (ics-openvpn, module `:vpnLib`) sau interface — M05. */
interface VpnEngine {
    val status: StateFlow<EngineStatus>

    /** `VpnService.prepare(context)`: khác null → cần mở Intent để user cấp quyền VPN. */
    fun prepareIntent(): Intent?

    /**
     * Parse [ovpn] rồi khởi động OpenVPN (thay kết nối đang chạy nếu có).
     * Lỗi parse/config → [status] = [EngineStatus.Error], không throw.
     */
    fun start(ovpn: String, username: String, password: String, displayName: String)

    /** Dừng kết nối hiện tại; [status] về [EngineStatus.Idle] khi service báo NOTCONNECTED. */
    fun stop()
}

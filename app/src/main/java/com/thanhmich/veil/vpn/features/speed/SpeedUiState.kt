package com.thanhmich.veil.vpn.features.speed

import androidx.annotation.StringRes
import com.thanhmich.veil.vpn.R
import com.thanhmich.veil.vpn.data.domain.SpeedError
import com.thanhmich.veil.vpn.data.domain.SpeedPhase
import com.thanhmich.veil.vpn.data.domain.SpeedResult

sealed interface SpeedStatus {
    data object Idle : SpeedStatus
    data class Running(val phase: SpeedPhase) : SpeedStatus
    data object Done : SpeedStatus
    data class Error(val error: SpeedError) : SpeedStatus
}

enum class GaugeUnit { Mbps, Ms }

/** Màu chữ StatusLine: Tertiary (Idle), Secondary (Running/Done), Poor (Error/Offline). */
enum class StatusTone { Tertiary, Secondary, Poor }

/** Dòng trạng thái dưới gauge; resolve bằng `stringResource(textRes, *args.toTypedArray())`. */
data class StatusLineModel(
    @param:StringRes val textRes: Int,
    val args: List<Any> = emptyList(),
    val tone: StatusTone,
) {
    companion object {
        fun idle() = StatusLineModel(R.string.speed_status_idle, tone = StatusTone.Tertiary)

        fun running(phase: SpeedPhase) = StatusLineModel(
            textRes = when (phase) {
                SpeedPhase.Ping -> R.string.speed_status_running_ping
                SpeedPhase.Download -> R.string.speed_status_running_download
                SpeedPhase.Upload -> R.string.speed_status_running_upload
            },
            tone = StatusTone.Secondary,
        )

        /** [timeText] đã format sẵn, ví dụ "14:05". */
        fun done(timeText: String) =
            StatusLineModel(R.string.speed_status_done, listOf(timeText), StatusTone.Secondary)

        fun error(error: SpeedError) = StatusLineModel(
            textRes = when (error) {
                SpeedError.NoNetwork -> R.string.speed_error_no_network
                SpeedError.ServerUnreachable -> R.string.speed_error_server_unreachable
                SpeedError.VpnChanged -> R.string.speed_error_vpn_changed
                SpeedError.Unknown -> R.string.speed_error_unknown
            },
            tone = StatusTone.Poor,
        )

        fun offline() = StatusLineModel(R.string.no_network_connection, tone = StatusTone.Poor)
    }
}

/**
 * Đường đi traffic cho StatusPill S03.
 * [connecting] = true khi VPN đang Connecting ("VPN đang kết nối…"); [viaVpn] = Connected.
 */
data class RouteInfo(
    val viaVpn: Boolean = false,
    val country: String? = null,
    val connecting: Boolean = false,
)

data class SpeedUiState(
    val status: SpeedStatus = SpeedStatus.Idle,
    val gaugeValue: Double = 0.0,
    val gaugeUnit: GaugeUnit = GaugeUnit.Mbps,
    val ping: Double? = null,
    val down: Double? = null,
    val up: Double? = null,
    val statusLine: StatusLineModel = StatusLineModel.idle(),
    /** Mới nhất đầu, tối đa SPEED_HISTORY_LIMIT. */
    val history: List<SpeedResult> = emptyList(),
    val route: RouteInfo = RouteInfo(),
    val offline: Boolean = false,
)

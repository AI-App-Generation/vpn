package com.thanhmich.veil.vpn.data.domain

import kotlinx.serialization.Serializable

enum class SpeedPhase { Ping, Download, Upload }

sealed interface SpeedProgress {
    /** [value]: Mbps (Download/Upload) hoặc ms (Ping). */
    data class Sample(val phase: SpeedPhase, val value: Double) : SpeedProgress
    data class PhaseDone(val phase: SpeedPhase, val value: Double) : SpeedProgress
    data class Finished(val result: SpeedResult) : SpeedProgress
}

@Serializable
data class SpeedResult(
    /** Epoch millis lúc hoàn tất. */
    val timestamp: Long,
    val pingMs: Int,
    val jitterMs: Int,
    val downMbps: Double,
    val upMbps: Double,
    val viaVpn: Boolean,
    val vpnCountry: String?,
)

enum class SpeedError { NoNetwork, ServerUnreachable, VpnChanged, Unknown }

/** Số kết quả speed test tối đa được giữ trong lịch sử. */
const val SPEED_HISTORY_LIMIT = 10

/** Thêm [result] vào đầu lịch sử (mới nhất đầu), cắt còn tối đa [limit] phần tử. */
fun List<SpeedResult>.prependSpeedResult(
    result: SpeedResult,
    limit: Int = SPEED_HISTORY_LIMIT,
): List<SpeedResult> = (listOf(result) + this).take(limit)

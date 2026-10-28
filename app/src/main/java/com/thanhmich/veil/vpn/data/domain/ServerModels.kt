package com.thanhmich.veil.vpn.data.domain

import kotlinx.serialization.Serializable

/**
 * 1 server VPNGate sau khi parse + dedupe. [id] = [hostname] (duy nhất sau dedupe).
 * Persist (trong [ActiveSession]) dưới dạng JSON.
 */
@Serializable
data class VpnServer(
    val id: String,
    val country: String,
    val countryFlag: String,
    val tier: Int,
    val hostname: String,
    val ipAddress: String,
    val ovpnUrl: String,
    /** Parse từ String, lỗi → 0. */
    val score: Long,
    /** Parse "(\d+)\s*ms" từ lineQuality; không có → null. */
    val pingMs: Int?,
    val ovpnContent: String?,
)

data class CountryGroup(
    val country: String,
    val flagUrl: String,
    val tier: Int,
    val servers: List<VpnServer>,
    val bestPingMs: Int?,
    val topScore: Long,
)

/**
 * @param crawledAt epoch millis (UTC). Dùng Long thay cho `java.time.Instant`
 *   vì minSdk 24 và project không bật core library desugaring.
 */
data class ServerCatalog(
    val groups: List<CountryGroup>,
    val servers: List<VpnServer>,
    val crawledAt: Long?,
    val fromCache: Boolean,
)

enum class CatalogError { Network, Permission, Unknown }

sealed interface CatalogState {
    data object Loading : CatalogState
    data class Ready(val catalog: ServerCatalog, val refreshing: Boolean) : CatalogState
    data class Failed(val reason: CatalogError, val cached: ServerCatalog?) : CatalogState
}

/**
 * Lựa chọn server của user. Persist dạng chuỗi:
 * `"auto"` | `"server|<hostname>|<country>"` — xem [encode] / [ServerSelection.decode].
 */
sealed interface ServerSelection {
    data object Auto : ServerSelection
    data class Specific(val serverId: String, val country: String) : ServerSelection

    fun encode(): String = when (this) {
        Auto -> AUTO_TOKEN
        is Specific -> "$SERVER_PREFIX$SEPARATOR$serverId$SEPARATOR$country"
    }

    companion object {
        private const val AUTO_TOKEN = "auto"
        private const val SERVER_PREFIX = "server"
        private const val SEPARATOR = "|"

        /** Chuỗi null / rỗng / sai định dạng → [Auto]. Country được phép chứa '|'. */
        fun decode(raw: String?): ServerSelection {
            if (raw.isNullOrBlank() || raw == AUTO_TOKEN) return Auto
            val parts = raw.split(SEPARATOR, limit = 3)
            if (parts.size != 3 || parts[0] != SERVER_PREFIX) return Auto
            val hostname = parts[1]
            if (hostname.isBlank()) return Auto
            return Specific(serverId = hostname, country = parts[2])
        }
    }
}

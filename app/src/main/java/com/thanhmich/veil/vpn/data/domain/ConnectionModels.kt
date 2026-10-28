package com.thanhmich.veil.vpn.data.domain

import kotlinx.serialization.Serializable

enum class ConnectStep { FetchingProfile, Handshaking }

enum class ConnectError { NoNetwork, NoServers, PermissionDenied, AllServersFailed, ConnectionLost }

sealed interface ConnectionState {
    data class Disconnected(val error: ConnectError?) : ConnectionState
    data class Connecting(
        val server: VpnServer,
        /** 1-based. */
        val attempt: Int,
        val maxAttempts: Int,
        val step: ConnectStep,
    ) : ConnectionState

    data class Connected(val server: VpnServer, val connectedAtWallMs: Long) : ConnectionState
    data object Disconnecting : ConnectionState
}

/** Phiên đang connect, persist JSON để khôi phục timer/server khi process UI bị kill (R11). */
@Serializable
data class ActiveSession(
    val server: VpnServer,
    val connectedAtWallMs: Long,
)

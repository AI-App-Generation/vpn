package com.thanhmich.veil.vpn.features.vpn

import com.thanhmich.veil.vpn.R
import com.thanhmich.veil.vpn.data.api.ServerCatalogParser
import com.thanhmich.veil.vpn.data.domain.CatalogError
import com.thanhmich.veil.vpn.data.domain.CatalogState
import com.thanhmich.veil.vpn.data.domain.ConnectError
import com.thanhmich.veil.vpn.data.domain.ConnectStep
import com.thanhmich.veil.vpn.data.domain.ConnectionState
import com.thanhmich.veil.vpn.data.domain.ServerCatalog
import com.thanhmich.veil.vpn.data.domain.ServerSelection
import com.thanhmich.veil.vpn.ui.model.BannerAction
import com.thanhmich.veil.vpn.ui.model.BannerVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnUiLogicTest {

    private fun item(host: String, country: String, tier: Long, score: String) = mapOf<String, Any?>(
        "country" to country, "countryFlag" to "", "tier" to tier, "hostname" to host,
        "ipAddress" to "1.1.1.1", "openVpnOvpnUrl" to "https://x/$host.ovpn", "score" to score,
        "lineQuality" to "Ping: 10 ms",
    )

    private val catalog: ServerCatalog = ServerCatalogParser.parse(
        listOf(
            item("jp-a.opengw.net", "Japan", 1, "500"),
            item("jp-b.opengw.net", "Japan", 1, "300"),
            item("kr-a.opengw.net", "Korea", 1, "400"),
        ),
        null, false,
    )
    private val empty = ServerCatalogParser.parse(emptyList(), null, false)
    private val ready = CatalogState.Ready(catalog, refreshing = false)
    private val jpA = catalog.servers.first { it.id == "jp-a.opengw.net" }
    private val jpB = catalog.servers.first { it.id == "jp-b.opengw.net" }
    private val krA = catalog.servers.first { it.id == "kr-a.opengw.net" }

    // ---- timer ----

    @Test fun formatElapsed() {
        assertEquals("00:00:00", VpnUiLogic.formatElapsed(0))
        assertEquals("00:01:05", VpnUiLogic.formatElapsed(65))
        assertEquals("01:00:00", VpnUiLogic.formatElapsed(3600))
        assertEquals("123:59:59", VpnUiLogic.formatElapsed(123 * 3600L + 3599))
        assertEquals("00:00:00", VpnUiLogic.formatElapsed(-5))
    }

    @Test fun elapsedSec_clampsClockSkew() {
        assertEquals(0L, VpnUiLogic.elapsedSec(1_000, 5_000))
        assertEquals(2L, VpnUiLogic.elapsedSec(7_999, 5_000))
    }

    // ---- phase / enable / command ----

    @Test fun phase_priority() {
        val disc = ConnectionState.Disconnected(null)
        assertEquals(VpnPhase.Restoring, VpnUiLogic.phaseOf(disc, isRestoring = true, awaitingPermission = false))
        assertEquals(VpnPhase.AwaitingPermission, VpnUiLogic.phaseOf(disc, isRestoring = true, awaitingPermission = true))
        assertEquals(VpnPhase.Connected, VpnUiLogic.phaseOf(ConnectionState.Connected(jpA, 0), true, false))
        assertEquals(VpnPhase.Disconnecting, VpnUiLogic.phaseOf(ConnectionState.Disconnecting, false, false))
    }

    @Test fun connectEnabled() {
        assertFalse(VpnUiLogic.isConnectEnabled(VpnPhase.Disconnected, catalogReady = false))
        assertTrue(VpnUiLogic.isConnectEnabled(VpnPhase.Disconnected, catalogReady = true))
        assertFalse(VpnUiLogic.isConnectEnabled(VpnPhase.Restoring, true))
        assertFalse(VpnUiLogic.isConnectEnabled(VpnPhase.Disconnecting, true))
        assertTrue(VpnUiLogic.isConnectEnabled(VpnPhase.Connected, catalogReady = false))
        assertTrue(VpnUiLogic.isConnectEnabled(VpnPhase.Connecting, catalogReady = false))
    }

    @Test fun connectCommand() {
        assertEquals(ConnectCommand.ShowNotice, VpnUiLogic.connectCommand(VpnPhase.Disconnected, true, noticeSeen = false))
        assertEquals(ConnectCommand.Start, VpnUiLogic.connectCommand(VpnPhase.Disconnected, true, noticeSeen = true))
        assertEquals(ConnectCommand.None, VpnUiLogic.connectCommand(VpnPhase.Disconnected, false, true))
        assertEquals(ConnectCommand.Cancel, VpnUiLogic.connectCommand(VpnPhase.Connecting, true, true))
        assertEquals(ConnectCommand.Cancel, VpnUiLogic.connectCommand(VpnPhase.AwaitingPermission, true, true))
        assertEquals(ConnectCommand.Disconnect, VpnUiLogic.connectCommand(VpnPhase.Connected, true, true))
        assertEquals(ConnectCommand.None, VpnUiLogic.connectCommand(VpnPhase.Disconnecting, true, true))
        assertEquals(ConnectCommand.None, VpnUiLogic.connectCommand(VpnPhase.Restoring, true, true))
    }

    // ---- catalog ----

    @Test fun catalogFlags() {
        assertTrue(VpnUiLogic.isCatalogLoading(CatalogState.Loading))
        assertTrue(VpnUiLogic.isCatalogLoading(CatalogState.Ready(empty, refreshing = true)))
        assertFalse(VpnUiLogic.isCatalogLoading(CatalogState.Ready(catalog, refreshing = true)))
        assertFalse(VpnUiLogic.isCatalogLoading(CatalogState.Failed(CatalogError.Network, null)))
        assertTrue(VpnUiLogic.isCatalogReady(CatalogState.Failed(CatalogError.Network, catalog)))
        assertFalse(VpnUiLogic.isCatalogReady(CatalogState.Ready(empty, false)))
    }

    @Test fun displayServer_usesRealServerWhileConnected() {
        val sel = ServerSelection.Specific(krA.id, "Korea")
        assertEquals(krA, VpnUiLogic.displayServer(ConnectionState.Disconnected(null), sel, catalog))
        assertEquals(jpB, VpnUiLogic.displayServer(ConnectionState.Connected(jpB, 0), sel, catalog))
        assertEquals(jpB, VpnUiLogic.displayServer(ConnectionState.Connecting(jpB, 1, 6, ConnectStep.Handshaking), sel, catalog))
        assertNull(VpnUiLogic.displayServer(ConnectionState.Disconnected(null), ServerSelection.Auto, null))
    }

    // ---- banner (bảng M06) ----

    @Test fun banner_errors() {
        fun b(e: ConnectError) = VpnUiLogic.banner(ConnectionState.Disconnected(e), ready, ServerSelection.Auto, null)!!
        b(ConnectError.NoNetwork).let {
            assertEquals(BannerVariant.Offline, it.variant); assertEquals(R.string.no_network_connection, it.textRes)
            assertEquals(BannerAction.RetryConnect, it.action)
        }
        b(ConnectError.PermissionDenied).let { assertEquals(BannerVariant.Warning, it.variant); assertEquals(BannerAction.RetryConnect, it.action) }
        b(ConnectError.AllServersFailed).let { assertEquals(BannerVariant.Error, it.variant); assertEquals(R.string.retry, it.actionRes) }
        b(ConnectError.ConnectionLost).let {
            assertEquals(BannerVariant.Error, it.variant); assertEquals(R.string.action_reconnect, it.actionRes)
            assertEquals(BannerAction.Reconnect, it.action)
        }
        b(ConnectError.NoServers).let { assertEquals(R.string.banner_no_servers, it.textRes); assertEquals(BannerAction.RefreshCatalog, it.action) }
    }

    @Test fun banner_retryingOnlyFromSecondAttempt() {
        assertNull(VpnUiLogic.banner(ConnectionState.Connecting(jpA, 1, 6, ConnectStep.FetchingProfile), ready, ServerSelection.Auto, null))
        val b = VpnUiLogic.banner(ConnectionState.Connecting(krA, 2, 6, ConnectStep.Handshaking), ready, ServerSelection.Auto, null)!!
        assertEquals(BannerVariant.Info, b.variant)
        assertEquals(listOf<Any>("Korea", 2, 6), b.args)
        assertNull(b.action)
    }

    @Test fun banner_catalog() {
        val failed = VpnUiLogic.banner(ConnectionState.Disconnected(null), CatalogState.Failed(CatalogError.Network, null), ServerSelection.Auto, null)!!
        assertEquals(R.string.banner_catalog_offline, failed.textRes)
        assertEquals(BannerVariant.Offline, failed.variant)
        assertEquals(BannerAction.RefreshCatalog, failed.action)
        // Còn cache → không banner.
        assertNull(VpnUiLogic.banner(ConnectionState.Disconnected(null), CatalogState.Failed(CatalogError.Network, catalog), ServerSelection.Auto, null))
        // Rỗng sau khi tải xong → NoServers; đang tải → không.
        assertEquals(R.string.banner_no_servers, VpnUiLogic.banner(ConnectionState.Disconnected(null), CatalogState.Ready(empty, false), ServerSelection.Auto, null)!!.textRes)
        assertNull(VpnUiLogic.banner(ConnectionState.Disconnected(null), CatalogState.Loading, ServerSelection.Auto, null))
        // Lỗi kết nối ưu tiên hơn lỗi catalog.
        assertEquals(
            R.string.banner_permission_denied,
            VpnUiLogic.banner(ConnectionState.Disconnected(ConnectError.PermissionDenied), CatalogState.Failed(CatalogError.Network, null), ServerSelection.Auto, null)!!.textRes,
        )
    }

    // ---- PendingSwitch (R13) ----

    @Test fun pendingSwitch() {
        val connected = ConnectionState.Connected(jpA, 0)
        val toKr = ServerSelection.Specific(krA.id, "Korea")
        // Chưa đổi selection → không pending (kể cả khi Auto đã failover sang server khác).
        assertNull(VpnUiLogic.pendingSwitchTarget(ConnectionState.Connected(jpB, 0), ServerSelection.Auto, ServerSelection.Auto, catalog))
        // Đổi sang Korea → pending, banner có country.
        val target = VpnUiLogic.pendingSwitchTarget(connected, toKr, ServerSelection.Auto, catalog)
        assertEquals(krA, target)
        val b = VpnUiLogic.banner(connected, ready, toKr, target)!!
        assertEquals(R.string.banner_pending_switch, b.textRes)
        assertEquals(listOf<Any>("Korea"), b.args)
        assertEquals(BannerAction.SwitchNow, b.action)
        // Đổi sang server chính là server đang dùng → không pending.
        assertNull(VpnUiLogic.pendingSwitchTarget(connected, ServerSelection.Specific(jpA.id, "Japan"), toKr, catalog))
        // Đổi sang Auto (resolve ra kr? không: jp-a) → jp-a đang dùng → không pending.
        assertNull(VpnUiLogic.pendingSwitchTarget(connected, ServerSelection.Auto, toKr, catalog))
        // Auto resolve ra server khác → banner bản Auto.
        val onKr = ConnectionState.Connected(krA, 0)
        val autoTarget = VpnUiLogic.pendingSwitchTarget(onKr, ServerSelection.Auto, toKr, catalog)
        assertEquals(jpA, autoTarget)
        assertEquals(R.string.banner_pending_switch_auto, VpnUiLogic.banner(onKr, ready, ServerSelection.Auto, autoTarget)!!.textRes)
        // Không Connected → không pending.
        assertNull(VpnUiLogic.pendingSwitchTarget(ConnectionState.Disconnected(null), toKr, ServerSelection.Auto, catalog))
    }

    @Test fun doubleTapGuard() {
        assertFalse(VpnUiLogic.isWithinDoubleTapGuard(10_000, 0)) // chưa từng Start
        assertTrue(VpnUiLogic.isWithinDoubleTapGuard(10_399, 10_000))
        assertFalse(VpnUiLogic.isWithinDoubleTapGuard(10_400, 10_000))
        assertFalse(VpnUiLogic.isWithinDoubleTapGuard(9_000, 10_000))
    }

    @Test fun shortHost() {
        assertEquals("public-vpn-12", VpnUiLogic.shortHost("public-vpn-12.opengw.net"))
        assertEquals("host", VpnUiLogic.shortHost("host"))
        assertEquals(".x", VpnUiLogic.shortHost(".x"))
    }
}

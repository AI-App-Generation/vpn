package com.thanhmich.veil.vpn.corefeatures.vpn

import android.app.Application
import android.content.Intent
import com.thanhmich.veil.vpn.corefeatures.vpn.engine.EngineStatus
import com.thanhmich.veil.vpn.corefeatures.vpn.engine.VpnEngine
import com.thanhmich.veil.vpn.data.domain.ActiveSession
import com.thanhmich.veil.vpn.data.domain.CatalogState
import com.thanhmich.veil.vpn.data.domain.ConnectError
import com.thanhmich.veil.vpn.data.domain.ConnectStep
import com.thanhmich.veil.vpn.data.domain.ConnectionState
import com.thanhmich.veil.vpn.data.domain.ConnectionState.Connected
import com.thanhmich.veil.vpn.data.domain.ConnectionState.Connecting
import com.thanhmich.veil.vpn.data.domain.ConnectionState.Disconnected
import com.thanhmich.veil.vpn.data.domain.ServerCatalog
import com.thanhmich.veil.vpn.data.domain.ServerSelection
import com.thanhmich.veil.vpn.data.domain.VpnServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Robolectric chỉ để tạo được [Intent] thật; mọi thời gian là virtual time của TestScope. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class VpnConnectionManagerTest {

    // ------------------------------------------------------------ fakes

    sealed interface Behavior {
        data class Connect(val afterMs: Long = 1_000) : Behavior
        data class Auth(val afterMs: Long = 1_000) : Behavior
        data class Err(val message: String = "boom") : Behavior
        data object Hang : Behavior
        /** Idle thoáng qua ở [idleAtMs] rồi Connected ở [connectAtMs]. */
        data class IdleThenConnect(val idleAtMs: Long, val connectAtMs: Long) : Behavior
    }

    class FakeEngine(private val scope: CoroutineScope) : VpnEngine {
        val flow = MutableStateFlow<EngineStatus>(EngineStatus.Idle)
        override val status get() = flow
        var permissionIntent: Intent? = null
        var prepareCalls = 0
        val started = mutableListOf<String>()
        var stopCalls = 0
        val behaviors = mutableMapOf<String, Behavior>()
        private var pending: Job? = null

        override fun prepareIntent(): Intent? {
            prepareCalls++
            return permissionIntent
        }

        override fun start(ovpn: String, username: String, password: String, displayName: String) {
            val id = ovpn.removePrefix("profile:")
            started += id
            pending?.cancel()
            val b = behaviors[id] ?: Behavior.Connect()
            if (b is Behavior.Err) {
                flow.value = EngineStatus.Error(b.message)
                return
            }
            flow.value = EngineStatus.Connecting
            pending = scope.launch {
                when (b) {
                    is Behavior.Connect -> { delay(b.afterMs); flow.value = EngineStatus.Connected }
                    is Behavior.Auth -> { delay(b.afterMs); flow.value = EngineStatus.AuthFailed }
                    is Behavior.IdleThenConnect -> {
                        delay(b.idleAtMs); flow.value = EngineStatus.Idle
                        delay(b.connectAtMs - b.idleAtMs); flow.value = EngineStatus.Connected
                    }
                    Behavior.Hang, is Behavior.Err -> Unit
                }
            }
        }

        /**
         * Mode giống IcsOpenVpnEngine: stop() không tự phát Idle (Idle chỉ đến từ "listener" sau
         * [stopIdleAfterMs], null = không bao giờ); Error được giữ tới lệnh kế.
         */
        var realistic = false
        var stopIdleAfterMs: Long? = null

        override fun stop() {
            stopCalls++
            pending?.cancel()
            if (!realistic) {
                flow.value = EngineStatus.Idle
                return
            }
            val d = stopIdleAfterMs ?: return
            pending = scope.launch { delay(d); flow.value = EngineStatus.Idle }
        }
    }

    class FakeLoader : ProfileLoader {
        val failing = mutableSetOf<String>()
        val hanging = mutableSetOf<String>()
        val calls = mutableListOf<String>()
        override suspend fun load(server: VpnServer): Result<String> {
            calls += server.id
            if (server.id in hanging) awaitCancellation()
            return if (server.id in failing) Result.failure(OvpnError.Invalid("html"))
            else Result.success("profile:${server.id}")
        }
    }

    class FakeSessionStore(initial: ActiveSession? = null, loaded: Boolean = true) : SessionStore {
        val loadedFlow = MutableStateFlow(loaded)
        override var current: ActiveSession? = initial
        override suspend fun awaitLoaded() { loadedFlow.first { it } }
        override fun save(session: ActiveSession?) { current = session }
    }

    private fun server(id: String, country: String = "Japan", tier: Int = 1, score: Long = 100) = VpnServer(
        id = id, country = country, countryFlag = "", tier = tier, hostname = id, ipAddress = "1.1.1.1",
        ovpnUrl = "https://x/$id.ovpn", score = score, pingMs = 10, ovpnContent = null,
    )

    private fun catalogOf(vararg servers: VpnServer) =
        CatalogState.Ready(ServerCatalog(emptyList(), servers.toList(), null, false), refreshing = false)

    // s1 > s2 > s3 theo score (Auto: tier asc → score desc)
    private val s1 = server("s1", score = 300)
    private val s2 = server("s2", score = 200)
    private val s3 = server("s3", score = 100)

    private inner class Env(val test: TestScope, servers: List<VpnServer> = listOf(s1, s2, s3), session: FakeSessionStore = FakeSessionStore()) {
        /**
         * Job con của backgroundScope (tự huỷ khi test xong) nhưng dispatcher không mang dấu
         * "background" → advanceUntilIdle vẫn chạy các task này.
         */
        val scope = CoroutineScope(
            SupervisorJob(test.backgroundScope.coroutineContext[Job]) + StandardTestDispatcher(test.testScheduler)
        )
        val engine = FakeEngine(scope)
        val loader = FakeLoader()
        val catalog = MutableStateFlow<CatalogState>(catalogOf(*servers.toTypedArray()))
        var selection: ServerSelection = ServerSelection.Auto
        var online = true
        val sessions = session
        val states = mutableListOf<ConnectionState>()
        lateinit var manager: VpnConnectionManager

        fun build(): Env {
            manager = VpnConnectionManager(
                engine = engine,
                catalogState = catalog,
                selectionProvider = { selection },
                sessionStore = sessions,
                network = { online },
                profileLoader = loader,
                wallClock = { 1_000_000L + test.currentTime },
                scope = scope,
            )
            test.backgroundScope.launch(UnconfinedTestDispatcher(test.testScheduler)) { manager.state.toList(states) }
            return this
        }
    }

    private fun TestScope.env(
        servers: List<VpnServer> = listOf(s1, s2, s3),
        session: FakeSessionStore = FakeSessionStore(),
        setup: Env.() -> Unit = {},
    ): Env = Env(this, servers, session).apply(setup).build().also { runCurrent() }

    private fun Env.connecting() = states.filterIsInstance<Connecting>()

    // ------------------------------------------------------------ tests

    @Test fun success_firstCandidate() = runTest {
        val e = env()
        e.manager.connect()
        runCurrent()
        assertEquals(Connecting(s1, 1, 3, ConnectStep.Handshaking), e.manager.state.value)
        advanceTimeBy(1_001)
        val st = e.manager.state.value as Connected
        assertEquals(s1, st.server)
        assertEquals(ActiveSession(s1, st.connectedAtWallMs), e.sessions.current)
        assertEquals(listOf("s1"), e.engine.started)
        assertEquals(0, e.engine.stopCalls)
        assertTrue(e.connecting().any { it.step == ConnectStep.FetchingProfile && it.server == s1 })
    }

    @Test fun profileError_movesToNextServer() = runTest {
        val e = env { loader.failing += "s1" }
        e.manager.connect()
        advanceUntilIdle()
        assertEquals(s2, (e.manager.state.value as Connected).server)
        assertEquals(listOf("s2"), e.engine.started)
        assertTrue(e.connecting().contains(Connecting(s2, 2, 3, ConnectStep.Handshaking)))
        assertEquals(setOf("s1"), e.manager.failedServerIds)
    }

    @Test fun profileLoadTimeout12s_movesToNextServer() = runTest {
        val e = env { loader.hanging += "s1" }
        e.manager.connect()
        advanceTimeBy(11_999)
        assertEquals(Connecting(s1, 1, 3, ConnectStep.FetchingProfile), e.manager.state.value)
        advanceTimeBy(2)
        assertEquals(Connecting(s2, 2, 3, ConnectStep.Handshaking), e.manager.state.value)
    }

    @Test fun authFailed_stopsAndMovesToNextServer() = runTest {
        val e = env { engine.behaviors["s1"] = Behavior.Auth() }
        e.manager.connect()
        advanceUntilIdle()
        assertEquals(s2, (e.manager.state.value as Connected).server)
        assertEquals(listOf("s1", "s2"), e.engine.started)
        assertEquals(1, e.engine.stopCalls)
        assertEquals(setOf("s1"), e.manager.failedServerIds)
    }

    @Test fun engineError_movesToNextServer() = runTest {
        val e = env { engine.behaviors["s1"] = Behavior.Err("Config parse error") }
        e.manager.connect()
        advanceUntilIdle()
        assertEquals(s2, (e.manager.state.value as Connected).server)
    }

    @Test fun handshakeTimeout20s_fromStart_movesToNextServer() = runTest {
        val e = env { engine.behaviors["s1"] = Behavior.Hang }
        e.manager.connect()
        runCurrent()
        advanceTimeBy(19_999)
        assertEquals(Connecting(s1, 1, 3, ConnectStep.Handshaking), e.manager.state.value)
        assertEquals(0, e.engine.stopCalls)
        advanceTimeBy(2)
        assertEquals(1, e.engine.stopCalls)
        assertEquals(Connecting(s2, 2, 3, ConnectStep.Handshaking), e.manager.state.value)
        advanceUntilIdle()
        assertEquals(s2, (e.manager.state.value as Connected).server)
    }

    @Test fun transientIdleDuringHandshake_isNotFailure() = runTest {
        val e = env { engine.behaviors["s1"] = Behavior.IdleThenConnect(idleAtMs = 100, connectAtMs = 5_000) }
        e.manager.connect()
        advanceTimeBy(200)
        assertEquals(EngineStatus.Idle, e.engine.flow.value)
        assertEquals(Connecting(s1, 1, 3, ConnectStep.Handshaking), e.manager.state.value)
        advanceUntilIdle()
        assertEquals(s1, (e.manager.state.value as Connected).server)
        assertEquals(listOf("s1"), e.engine.started)
        assertEquals(0, e.engine.stopCalls)
    }

    @Test fun allCandidatesFail_allServersFailed_andFailedIdsCleared() = runTest {
        val e = env {
            engine.behaviors["s1"] = Behavior.Auth()
            engine.behaviors["s2"] = Behavior.Hang
            loader.failing += "s3"
        }
        e.manager.connect()
        advanceUntilIdle()
        assertEquals(Disconnected(ConnectError.AllServersFailed), e.manager.state.value)
        assertTrue(e.manager.failedServerIds.isEmpty())
        assertNull(e.sessions.current)
        // "Thử lại" thử lại toàn bộ.
        e.manager.connect()
        runCurrent()
        assertEquals(Connecting(s1, 1, 3, ConnectStep.Handshaking), e.manager.state.value)
    }

    @Test fun maxSixCandidates() = runTest {
        val many = (1..8).map { server("h$it", score = 100L - it) }
        val e = env(servers = many) { many.forEach { engine.behaviors[it.id] = Behavior.Auth() } }
        e.manager.connect()
        advanceUntilIdle()
        assertEquals(6, e.engine.started.size)
        assertEquals(6, e.connecting().maxOf { it.maxAttempts })
        assertEquals(Disconnected(ConnectError.AllServersFailed), e.manager.state.value)
    }

    @Test fun emptyCatalog_noServers() = runTest {
        val e = env(servers = emptyList())
        e.manager.connect()
        advanceUntilIdle()
        assertEquals(Disconnected(ConnectError.NoServers), e.manager.state.value)
    }

    @Test fun permissionDenied() = runTest {
        val intent = Intent("test.PREPARE")
        val e = env { engine.permissionIntent = intent }
        val received = mutableListOf<Intent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { e.manager.permissionRequests.collect { received += it } }
        e.manager.connect()
        runCurrent()
        assertEquals(listOf(intent), received)
        assertTrue(e.manager.isAwaitingPermission.value)
        e.manager.onPermissionResult(false)
        advanceUntilIdle()
        assertEquals(Disconnected(ConnectError.PermissionDenied), e.manager.state.value)
        assertTrue(e.engine.started.isEmpty())
    }

    @Test fun permissionGranted_continues() = runTest {
        val e = env { engine.permissionIntent = Intent("test.PREPARE") }
        e.manager.connect()
        runCurrent()
        assertTrue(e.engine.started.isEmpty())
        e.manager.onPermissionResult(true)
        advanceUntilIdle()
        assertEquals(s1, (e.manager.state.value as Connected).server)
    }

    @Test fun noNetwork() = runTest {
        val e = env { online = false }
        e.manager.connect()
        advanceUntilIdle()
        assertEquals(Disconnected(ConnectError.NoNetwork), e.manager.state.value)
        assertEquals(0, e.engine.prepareCalls)
        assertTrue(e.loader.calls.isEmpty())
    }

    @Test fun cancelWhileHandshaking_stopsEngine_disconnectedNull() = runTest {
        val e = env { engine.behaviors["s1"] = Behavior.Hang }
        e.manager.connect()
        advanceTimeBy(1_000)
        e.manager.cancel()
        assertEquals(ConnectionState.Disconnecting, e.manager.state.value)
        advanceUntilIdle()
        assertEquals(Disconnected(null), e.manager.state.value)
        assertEquals(1, e.engine.stopCalls)
        assertNull(e.sessions.current)
        advanceTimeBy(60_000)
        assertEquals(listOf("s1"), e.engine.started) // job cũ không chạy tiếp
        assertEquals(Disconnected(null), e.manager.state.value)
    }

    @Test fun cancelWhileFetchingProfile_noEngineStop() = runTest {
        val e = env { loader.hanging += "s1" }
        e.manager.connect()
        advanceTimeBy(500)
        e.manager.cancel()
        advanceUntilIdle()
        assertEquals(Disconnected(null), e.manager.state.value)
        assertEquals(0, e.engine.stopCalls)
        assertTrue(e.engine.started.isEmpty())
    }

    @Test fun disconnectWhenConnected_clearsSession() = runTest {
        val e = env()
        e.manager.connect()
        advanceUntilIdle()
        assertTrue(e.sessions.current != null)
        e.manager.disconnect()
        advanceUntilIdle()
        assertEquals(Disconnected(null), e.manager.state.value)
        assertEquals(1, e.engine.stopCalls)
        assertNull(e.sessions.current)
    }

    @Test fun connectionDropped_failsOverToNextServer_countingAnew() = runTest {
        val e = env()
        e.manager.connect()
        advanceUntilIdle()
        assertEquals(s1, (e.manager.state.value as Connected).server)
        e.states.clear()
        e.engine.flow.value = EngineStatus.Idle // openvpn thoát không do user
        advanceUntilIdle()
        assertEquals(s2, (e.manager.state.value as Connected).server)
        // Không giữ Connected sai: chuyển Connecting(s1, 1, n, Handshaking) trước khi stop, session xoá.
        assertEquals(Connecting(s1, 1, 2, ConnectStep.Handshaking), e.states.first())
        // Đếm lại: s1 bị loại → danh sách mới [s2, s3], attempt 1/2.
        assertEquals(Connecting(s2, 1, 2, ConnectStep.FetchingProfile), e.connecting()[1])
        assertEquals(setOf("s1"), e.manager.failedServerIds)
        assertEquals(s2, e.sessions.current?.server)
    }

    @Test fun connectionError_failsOver() = runTest {
        val e = env()
        e.manager.connect()
        advanceUntilIdle()
        e.engine.flow.value = EngineStatus.Error("x")
        advanceUntilIdle()
        assertEquals(s2, (e.manager.state.value as Connected).server)
    }

    @Test fun noNetworkOver20s_whileConnected_connectionLost() = runTest {
        val e = env()
        e.manager.connect()
        advanceUntilIdle()
        e.engine.flow.value = EngineStatus.NoNetwork
        advanceTimeBy(19_000)
        assertTrue(e.manager.state.value is Connected)
        e.engine.flow.value = EngineStatus.Connecting // vẫn chưa có lại kết nối
        advanceTimeBy(1_100)
        assertEquals(Disconnected(ConnectError.ConnectionLost), e.manager.state.value)
        assertNull(e.sessions.current)
        assertEquals(1, e.engine.stopCalls)
    }

    @Test fun noNetworkShort_whileConnected_staysConnected() = runTest {
        val e = env()
        e.manager.connect()
        advanceUntilIdle()
        val connected = e.manager.state.value
        e.engine.flow.value = EngineStatus.NoNetwork
        advanceTimeBy(10_000)
        e.engine.flow.value = EngineStatus.Connected
        advanceTimeBy(60_000)
        assertEquals(connected, e.manager.state.value)
        assertEquals(listOf("s1"), e.engine.started)
    }

    @Test fun duplicateAndInvalidCommands_ignored() = runTest {
        val e = env { engine.behaviors["s1"] = Behavior.Connect(afterMs = 5_000) }
        e.manager.disconnect() // Disconnected → bỏ qua
        e.manager.cancel()
        assertEquals(0, e.engine.stopCalls)
        e.manager.connect()
        runCurrent()
        e.manager.connect() // đang Connecting → bỏ qua
        e.manager.reconnect() // đang Connecting → bỏ qua
        runCurrent()
        assertEquals(1, e.loader.calls.size)
        assertEquals(Connecting(s1, 1, 3, ConnectStep.Handshaking), e.manager.state.value)
        advanceUntilIdle()
        e.manager.connect() // Connected → bỏ qua
        e.engine.behaviors["s1"] = Behavior.Hang
        e.manager.disconnect()
        e.manager.connect() // Disconnecting → bỏ qua
        e.manager.disconnect()
        advanceUntilIdle()
        assertEquals(1, e.engine.stopCalls)
        assertEquals(listOf("s1"), e.engine.started)
        assertEquals(Disconnected(null), e.manager.state.value)
    }

    @Test fun reconnect_switchesToNewSelection() = runTest {
        val e = env()
        e.manager.connect()
        advanceUntilIdle()
        e.selection = ServerSelection.Specific("s3", "Japan")
        e.manager.reconnect()
        assertEquals(ConnectionState.Disconnecting, e.manager.state.value)
        advanceUntilIdle()
        assertEquals(s3, (e.manager.state.value as Connected).server)
        assertEquals(s3, e.sessions.current?.server)
    }

    @Test fun restore_engineConnectedWithin2s_restoresSession() = runTest {
        val saved = ActiveSession(s2, connectedAtWallMs = 42L)
        val store = FakeSessionStore(saved, loaded = false)
        val e = env(session = store)
        advanceTimeBy(500)
        assertEquals(Disconnected(null), e.manager.state.value)
        store.loadedFlow.value = true
        advanceTimeBy(1_500)
        e.engine.flow.value = EngineStatus.Connected // StatusListener bind xong
        runCurrent()
        assertEquals(Connected(s2, 42L), e.manager.state.value)
        assertEquals(saved, store.current)
        // Phiên khôi phục vẫn được theo dõi: rớt → failover (s2 bị loại).
        e.engine.flow.value = EngineStatus.Idle
        advanceUntilIdle()
        assertEquals(s1, (e.manager.state.value as Connected).server)
    }

    @Test fun restore_engineNotConnected_clearsSessionAfter2s() = runTest {
        val store = FakeSessionStore(ActiveSession(s2, 42L))
        val e = env(session = store)
        advanceTimeBy(1_999)
        assertTrue(store.current != null)
        advanceTimeBy(2)
        assertNull(store.current)
        assertEquals(Disconnected(null), e.manager.state.value)
    }

    @Test fun backgroundStartBlockedOnEveryCandidate_afterLoss_connectionLost() = runTest {
        val e = env()
        e.manager.connect()
        advanceUntilIdle()
        val err = Behavior.Err("Cannot start VPN service: ForegroundServiceStartNotAllowedException: x")
        e.engine.behaviors["s2"] = err
        e.engine.behaviors["s3"] = err
        e.engine.flow.value = EngineStatus.Idle
        advanceUntilIdle()
        assertEquals(Disconnected(ConnectError.ConnectionLost), e.manager.state.value)
        assertFalse(e.manager.failedServerIds.isNotEmpty())
    }

    // ------------------------------------------------------------ chốt sau review T05

    @Test fun realisticEngine_stopWithoutIdle_waits3sThenNextCandidate() = runTest {
        val e = env {
            engine.realistic = true
            engine.behaviors["s1"] = Behavior.Auth(afterMs = 1_000)
        }
        e.manager.connect()
        advanceTimeBy(1_001) // AuthFailed → stop, Idle không đến
        assertEquals(1, e.engine.stopCalls)
        advanceTimeBy(2_990)
        assertEquals(Connecting(s1, 1, 3, ConnectStep.Handshaking), e.manager.state.value)
        assertEquals(listOf("s1"), e.engine.started)
        advanceTimeBy(20)
        assertEquals(Connecting(s2, 2, 3, ConnectStep.Handshaking), e.manager.state.value)
        advanceUntilIdle()
        assertEquals(s2, (e.manager.state.value as Connected).server)
    }

    @Test fun realisticEngine_idleArrivesLate_continuesEarly() = runTest {
        val e = env {
            engine.realistic = true
            engine.stopIdleAfterMs = 500
            engine.behaviors["s1"] = Behavior.Auth(afterMs = 1_000)
        }
        e.manager.connect()
        advanceTimeBy(1_001)
        advanceTimeBy(501)
        assertEquals(Connecting(s2, 2, 3, ConnectStep.Handshaking), e.manager.state.value)
    }

    @Test fun synchronousStartError_noStopNoWait() = runTest {
        val e = env {
            engine.realistic = true
            engine.behaviors["s1"] = Behavior.Err("Config parse error")
        }
        e.manager.connect()
        runCurrent()
        assertEquals(0, e.engine.stopCalls)
        assertEquals(listOf("s1", "s2"), e.engine.started)
        assertEquals(Connecting(s2, 2, 3, ConnectStep.Handshaking), e.manager.state.value)
        assertEquals(0L, currentTime)
    }

    @Test fun connectingOver30sWhileConnected_failsOver() = runTest {
        val e = env()
        e.manager.connect()
        advanceUntilIdle()
        e.engine.flow.value = EngineStatus.Connecting // openvpn tự reconnect vì server chết
        advanceTimeBy(29_900)
        assertEquals(s1, (e.manager.state.value as Connected).server)
        assertEquals(0, e.engine.stopCalls)
        advanceTimeBy(200)
        assertEquals(1, e.engine.stopCalls)
        advanceUntilIdle()
        assertEquals(s2, (e.manager.state.value as Connected).server)
        assertEquals(setOf("s1"), e.manager.failedServerIds)
    }

    @Test fun connectingShortWhileConnected_staysConnected() = runTest {
        val e = env()
        e.manager.connect()
        advanceUntilIdle()
        e.engine.flow.value = EngineStatus.Connecting
        advanceTimeBy(20_000)
        e.engine.flow.value = EngineStatus.Connected
        advanceTimeBy(60_000)
        assertEquals(s1, (e.manager.state.value as Connected).server)
        assertEquals(0, e.engine.stopCalls)
    }

    @Test fun restore_engineConnecting_restoresSession() = runTest {
        val saved = ActiveSession(s2, connectedAtWallMs = 42L)
        val store = FakeSessionStore(saved)
        val e = env(session = store)
        assertTrue(e.manager.isRestoring.value)
        advanceTimeBy(500)
        e.engine.flow.value = EngineStatus.Connecting
        runCurrent()
        assertEquals(Connected(s2, 42L), e.manager.state.value)
        assertFalse(e.manager.isRestoring.value)
        assertEquals(saved, store.current)
        // Vẫn Connecting quá 30s → failover.
        advanceTimeBy(30_100)
        advanceUntilIdle()
        assertEquals(s1, (e.manager.state.value as Connected).server)
    }

    @Test fun restore_engineNoNetwork_restoresSession() = runTest {
        val store = FakeSessionStore(ActiveSession(s2, 42L))
        val e = env(session = store) { engine.flow.value = EngineStatus.NoNetwork }
        runCurrent()
        assertEquals(Connected(s2, 42L), e.manager.state.value)
    }

    @Test fun restore_noSession_orphanTunnel_isStopped() = runTest {
        val e = env { engine.flow.value = EngineStatus.Connected }
        assertFalse(e.manager.isRestoring.value) // không có session → không khoá UI
        advanceTimeBy(1_999)
        assertEquals(0, e.engine.stopCalls)
        advanceTimeBy(2)
        assertEquals(1, e.engine.stopCalls)
        assertEquals(Disconnected(null), e.manager.state.value)
    }

    @Test fun restore_noSession_engineIdle_noStop() = runTest {
        val e = env()
        advanceTimeBy(10_000)
        assertEquals(0, e.engine.stopCalls)
    }

    @Test fun connectDuringRestore_cancelsRestore() = runTest {
        val store = FakeSessionStore(ActiveSession(s2, 42L))
        val e = env(session = store)
        advanceTimeBy(500)
        assertTrue(e.manager.isRestoring.value)
        e.manager.connect()
        assertFalse(e.manager.isRestoring.value)
        advanceUntilIdle()
        val st = e.manager.state.value as Connected
        assertEquals(s1, st.server)
        assertEquals(ActiveSession(s1, st.connectedAtWallMs), store.current)
        assertTrue(e.states.none { it == Connected(s2, 42L) })
    }

    @Test fun droppedWhileOffline_connectionLost() = runTest {
        val e = env()
        e.manager.connect()
        advanceUntilIdle()
        e.online = false
        e.engine.flow.value = EngineStatus.Idle
        advanceUntilIdle()
        assertEquals(Disconnected(ConnectError.ConnectionLost), e.manager.state.value)
        assertNull(e.sessions.current)
        assertEquals(listOf("s1"), e.engine.started)
    }

    @Test fun cancelWhileAwaitingPermission_clearsStaleIntent() = runTest {
        val e = env { engine.permissionIntent = Intent("test.PREPARE") }
        e.manager.connect() // chưa có UI collect → intent nằm trong channel
        runCurrent()
        assertTrue(e.manager.isAwaitingPermission.value)
        e.manager.cancel()
        assertFalse(e.manager.isAwaitingPermission.value)
        assertEquals(Disconnected(null), e.manager.state.value)
        val received = mutableListOf<Intent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { e.manager.permissionRequests.collect { received += it } }
        runCurrent()
        assertTrue(received.isEmpty())
        e.manager.onPermissionResult(true) // trả lời muộn → bỏ qua
        advanceUntilIdle()
        assertTrue(e.engine.started.isEmpty())
        assertEquals(Disconnected(null), e.manager.state.value)
    }

    @Test fun reconnect_requiringPermission() = runTest {
        val e = env()
        e.manager.connect()
        advanceUntilIdle()
        e.engine.permissionIntent = Intent("test.PREPARE")
        e.selection = ServerSelection.Specific("s3", "Japan")
        val received = mutableListOf<Intent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { e.manager.permissionRequests.collect { received += it } }
        e.manager.reconnect()
        advanceUntilIdle()
        assertEquals(1, received.size)
        assertTrue(e.manager.isAwaitingPermission.value)
        assertEquals(Disconnected(null), e.manager.state.value)
        e.manager.onPermissionResult(true)
        advanceUntilIdle()
        assertEquals(s3, (e.manager.state.value as Connected).server)
    }

    @Test fun failoverBudget120s_allServersFailed() = runTest {
        val many = (1..8).map { server("h$it", score = 100L - it) }
        val e = env(servers = many) {
            engine.realistic = true // stop không phát Idle → mỗi candidate tốn 20s + 3s
            many.forEach { engine.behaviors[it.id] = Behavior.Hang }
        }
        e.manager.connect()
        runCurrent()
        advanceTimeBy(119_900)
        assertEquals(Connecting(many[5], 6, 6, ConnectStep.Handshaking), e.manager.state.value)
        assertEquals(5, e.engine.stopCalls)
        advanceTimeBy(200)
        assertEquals(Disconnected(ConnectError.AllServersFailed), e.manager.state.value)
        assertEquals(6, e.engine.stopCalls) // candidate dở bị dừng
        assertTrue(e.manager.failedServerIds.isEmpty())
        advanceTimeBy(60_000)
        assertEquals(6, e.engine.started.size)
    }
}

package com.thanhmich.veil.vpn.corefeatures.vpn.engine

import android.app.Application
import android.content.ComponentName
import android.os.Looper
import de.blinkt.openvpn.core.ConnectionStatus
import de.blinkt.openvpn.core.IOpenVPNServiceInternal
import de.blinkt.openvpn.core.OpenVPNService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class IcsOpenVpnEngineTest {

    /** Fake binder của OpenVPNService: stopVPN trả [results] lần lượt (hết danh sách → phần tử cuối). */
    private class FakeService(private val results: List<Boolean>) : IOpenVPNServiceInternal.Stub() {
        var stopCalls = 0
        override fun stopVPN(replaceConnection: Boolean): Boolean =
            results[minOf(stopCalls++, results.lastIndex)]
        override fun protect(fd: Int) = false
        override fun userPause(b: Boolean) = Unit
        override fun addAllowedExternalApp(packagename: String?) = Unit
        override fun isAllowedExternalApp(packagename: String?) = false
        override fun challengeResponse(repsonse: String?) = Unit
    }

    private lateinit var app: Application
    private lateinit var engine: IcsOpenVpnEngine

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        engine = IcsOpenVpnEngine(app)
    }

    @After
    fun tearDown() = engine.release()

    private fun bindFake(results: List<Boolean>): FakeService {
        val fake = FakeService(results)
        shadowOf(app).setComponentNameAndServiceForBindService(
            ComponentName(app, OpenVPNService::class.java), fake,
        )
        return fake
    }

    private fun idleFor(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    @Test
    fun mapLevelFollowsSpec() {
        assertEquals(EngineStatus.Connected, IcsOpenVpnEngine.mapLevel(ConnectionStatus.LEVEL_CONNECTED))
        listOf(
            ConnectionStatus.LEVEL_CONNECTING_NO_SERVER_REPLY_YET,
            ConnectionStatus.LEVEL_CONNECTING_SERVER_REPLIED,
            ConnectionStatus.LEVEL_START,
            ConnectionStatus.LEVEL_WAITING_FOR_USER_INPUT,
            ConnectionStatus.LEVEL_VPNPAUSED,
        ).forEach { assertEquals(it.name, EngineStatus.Connecting, IcsOpenVpnEngine.mapLevel(it)) }
        assertEquals(EngineStatus.AuthFailed, IcsOpenVpnEngine.mapLevel(ConnectionStatus.LEVEL_AUTH_FAILED))
        assertEquals(EngineStatus.NoNetwork, IcsOpenVpnEngine.mapLevel(ConnectionStatus.LEVEL_NONETWORK))
        assertEquals(EngineStatus.Idle, IcsOpenVpnEngine.mapLevel(ConnectionStatus.LEVEL_NOTCONNECTED))
        assertNull(IcsOpenVpnEngine.mapLevel(ConnectionStatus.UNKNOWN_LEVEL))
        assertNull(IcsOpenVpnEngine.mapLevel(null))
    }

    @Test
    fun stopRetriesUntilServiceAcceptsAndIdleComesFromListener() {
        engine.onLevel(ConnectionStatus.LEVEL_CONNECTED)
        val fake = bindFake(listOf(false, false, true))

        engine.stop()
        idleFor(2_000)

        assertEquals(3, fake.stopCalls)
        // Engine không tự đoán Idle; chờ service báo NOTCONNECTED.
        assertEquals(EngineStatus.Connected, engine.status.value)
        engine.onLevel(ConnectionStatus.LEVEL_NOTCONNECTED)
        assertEquals(EngineStatus.Idle, engine.status.value)
    }

    @Test
    fun stopGivesUpAfterWindowWithoutFakeIdle() {
        engine.onLevel(ConnectionStatus.LEVEL_CONNECTING_NO_SERVER_REPLY_YET)
        val fake = bindFake(listOf(false))

        engine.stop()
        idleFor(IcsOpenVpnEngine.STOP_WINDOW_MS + 2_000)
        val callsAtEnd = fake.stopCalls
        idleFor(5_000)

        val expected = (IcsOpenVpnEngine.STOP_WINDOW_MS / IcsOpenVpnEngine.STOP_RETRY_DELAY_MS).toInt()
        assertTrue("retried for ~8s: $callsAtEnd", callsAtEnd in expected - 2..expected + 2)
        assertEquals("no retry after the window", callsAtEnd, fake.stopCalls)
        assertNotEquals(EngineStatus.Idle, engine.status.value)
    }

    @Test
    fun lateConnectAfterStopTriggersStopAgain() {
        val fake = bindFake(listOf(true))
        engine.stop()
        idleFor(500)
        assertEquals(1, fake.stopCalls)

        // Lệnh start đến muộn: service báo Connected sau khi user đã stop.
        engine.onLevel(ConnectionStatus.LEVEL_CONNECTED)
        assertNotEquals(EngineStatus.Connected, engine.status.value)
        idleFor(500)
        assertEquals(2, fake.stopCalls)
    }

    @Test
    fun errorIsKeptUntilNextStartOrStop() {
        bindFake(listOf(true))
        engine.start("<html><body>503</body></html>", "vpn", "vpn", "x")
        assertTrue(engine.status.value is EngineStatus.Error)

        engine.onLevel(ConnectionStatus.LEVEL_NOTCONNECTED) // đến muộn
        assertTrue(engine.status.value is EngineStatus.Error)

        engine.stop()
        engine.onLevel(ConnectionStatus.LEVEL_NOTCONNECTED)
        assertEquals(EngineStatus.Idle, engine.status.value)
    }
}

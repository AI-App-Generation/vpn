package com.thanhmich.veil.vpn.corefeatures.speedtest

import com.thanhmich.veil.vpn.data.domain.SpeedError
import com.thanhmich.veil.vpn.data.domain.SpeedPhase
import com.thanhmich.veil.vpn.data.domain.SpeedProgress
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class SpeedTestEngineTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    /** Hành vi server tuỳ test. */
    private var pingFails: (index: Int) -> Boolean = { false }
    private var downloadFails: (index: Int) -> Boolean = { false }
    private val pingCount = AtomicInteger()
    private val downCount = AtomicInteger()
    private val upCount = AtomicInteger()

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: ""
                return when {
                    path == "/__down?bytes=0" -> {
                        val i = pingCount.getAndIncrement()
                        if (pingFails(i)) MockResponse().setResponseCode(500) else MockResponse().setBody("")
                    }
                    path.startsWith("/__down?bytes=") -> {
                        val i = downCount.getAndIncrement()
                        if (downloadFails(i)) MockResponse().setResponseCode(500)
                        else {
                            val n = path.substringAfter("bytes=").toLong()
                            MockResponse().setBody(Buffer().write(ByteArray(n.toInt())))
                                .throttleBody(64 * 1024, 20, TimeUnit.MILLISECONDS)
                        }
                    }
                    path == "/__up" && request.method == "POST" -> {
                        upCount.incrementAndGet()
                        MockResponse().setBody("ok")
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        client = SpeedTestEngine.defaultClient()
    }

    @After fun tearDown() {
        client.dispatcher.cancelAll()
        server.shutdown()
    }

    private fun config(downloadMs: Long = 800, uploadMs: Long = 800) = SpeedTestConfig(
        baseUrl = server.url("/").toString(),
        downloadDurationMs = downloadMs,
        downloadBytesPerRequest = 2_000_000,
        uploadDurationMs = uploadMs,
        uploadBytesPerRequest = 300_000,
        warmupSkipMs = 200,
        sampleIntervalMs = 100,
        windowMs = 400,
        retryBackoffMs = 20,
    )

    private fun engine(
        online: Boolean = true,
        cfg: SpeedTestConfig = config(),
        stats: MutableList<PhaseStats> = mutableListOf(),
    ) = SpeedTestEngine(
        network = { online },
        routeProvider = { SpeedRoute(viaVpn = true, vpnCountry = "Japan") },
        config = cfg,
        client = client,
        onPhaseStats = { synchronized(stats) { stats.add(it) } },
    )

    private fun label(p: SpeedProgress) = when (p) {
        is SpeedProgress.Sample -> "S:${p.phase}"
        is SpeedProgress.PhaseDone -> "D:${p.phase}"
        is SpeedProgress.Finished -> "F"
    }

    private fun awaitNoRunningCalls() {
        val deadline = System.currentTimeMillis() + 3_000
        while (client.dispatcher.runningCallsCount() > 0 && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertEquals("calls still running", 0, client.dispatcher.runningCallsCount())
    }

    @Test fun phasesInOrderAndFinished() = runBlocking {
        val stats = mutableListOf<PhaseStats>()
        val events = engine(stats = stats).run().toList()

        val compressed = events.map(::label).fold(mutableListOf<String>()) { acc, l ->
            if (acc.lastOrNull() != l) acc.add(l); acc
        }
        assertEquals(
            listOf("S:Ping", "D:Ping", "S:Download", "D:Download", "S:Upload", "D:Upload", "F"),
            compressed,
        )
        assertEquals(10, events.count { it is SpeedProgress.Sample && it.phase == SpeedPhase.Ping })
        assertEquals(11, pingCount.get()) // 1 warm-up + 10

        val done = events.filterIsInstance<SpeedProgress.PhaseDone>().associate { it.phase to it.value }
        val result = (events.last() as SpeedProgress.Finished).result
        assertTrue(result.downMbps > 0)
        assertTrue(result.upMbps > 0)
        assertEquals(done[SpeedPhase.Download]!!, result.downMbps, 0.0)
        assertEquals(done[SpeedPhase.Upload]!!, result.upMbps, 0.0)
        assertEquals(Math.round(done[SpeedPhase.Ping]!!).toInt(), result.pingMs)
        assertTrue(result.viaVpn)
        assertEquals("Japan", result.vpnCountry)

        // 4 luồng download song song, 3 luồng upload
        assertTrue("download requests=${downCount.get()}", downCount.get() >= 4)
        assertTrue("upload requests=${upCount.get()}", upCount.get() >= 3)
        val down = stats.first { it.phase == SpeedPhase.Download }
        assertTrue(down.bytes > 0)
        assertTrue("download duration ${down.durationMs}", down.durationMs in 700..1500)
        // Hết pha → mọi call đã huỷ (download 2MB throttled không thể xong trong 800ms)
        awaitNoRunningCalls()
    }

    @Test fun consecutiveRuns_reuseClientWithoutFailing() = runBlocking {
        val lightweight = config(downloadMs = 400, uploadMs = 400).copy(
            downloadBytesPerRequest = 64 * 1024,
            uploadBytesPerRequest = 64 * 1024,
            sampleIntervalMs = 100,
        )
        repeat(2) {
            val events = engine(cfg = lightweight).run().toList()
            assertTrue("run ${it + 1} did not finish", events.last() is SpeedProgress.Finished)
            awaitNoRunningCalls()
        }
    }

    @Test fun lessThanFivePingSuccessIsServerUnreachable() = runBlocking {
        // warm-up (0) OK; 6/10 request đo lỗi → 4 thành công
        pingFails = { it in 1..6 }
        val events = mutableListOf<SpeedProgress>()
        try {
            engine().run().collect { events.add(it) }
            fail("expected SpeedTestException")
        } catch (e: SpeedTestException) {
            assertEquals(SpeedError.ServerUnreachable, e.error)
        }
        assertEquals(4, events.count { it is SpeedProgress.Sample })
        assertEquals(0, downCount.get())
    }

    @Test fun fivePingSuccessIsEnough() = runBlocking {
        pingFails = { it in 1..5 }
        val events = engine().run().toList()
        assertTrue(events.last() is SpeedProgress.Finished)
    }

    @Test fun noNetworkAtStart() = runBlocking {
        try {
            engine(online = false).run().toList()
            fail("expected SpeedTestException")
        } catch (e: SpeedTestException) {
            assertEquals(SpeedError.NoNetwork, e.error)
        }
        assertEquals(0, server.requestCount)
    }

    @Test fun wholeDownloadPhaseFailingIsServerUnreachableAndEndsEarly() = runBlocking {
        downloadFails = { true }
        val started = System.currentTimeMillis()
        try {
            engine(cfg = config(downloadMs = 5_000)).run().toList()
            fail("expected SpeedTestException")
        } catch (e: SpeedTestException) {
            assertEquals(SpeedError.ServerUnreachable, e.error)
        }
        val took = System.currentTimeMillis() - started
        assertTrue("took $took ms", took < 3_000)
        assertEquals(0, upCount.get())
    }

    @Test fun singleConnectionErrorsAreIgnored() = runBlocking {
        downloadFails = { it % 2 == 0 } // mọi request chẵn lỗi 500
        val result = (engine().run().toList().last() as SpeedProgress.Finished).result
        assertTrue(result.downMbps > 0)
    }

    @Test fun cancellingCollectorCancelsCalls() = runBlocking {
        var sawDownload = false
        engine(cfg = config(downloadMs = 10_000)).run()
            .takeWhile { !(it is SpeedProgress.Sample && it.phase == SpeedPhase.Download).also { d -> sawDownload = sawDownload || d } }
            .toList()
        assertTrue(sawDownload)
        assertTrue(downCount.get() >= 1)
        awaitNoRunningCalls()
    }

    @Test fun routeFromConnectionState() {
        assertFalse(SpeedRoute.from(com.thanhmich.veil.vpn.data.domain.ConnectionState.Disconnected(null)).viaVpn)
        assertNull(SpeedRoute.from(com.thanhmich.veil.vpn.data.domain.ConnectionState.Disconnecting).vpnCountry)
        val s = com.thanhmich.veil.vpn.data.domain.VpnServer(
            id = "h", country = "Japan", countryFlag = "", tier = 1, hostname = "h", ipAddress = "1.1.1.1",
            ovpnUrl = "", score = 0, pingMs = null, ovpnContent = null,
        )
        assertFalse(SpeedRoute.from(com.thanhmich.veil.vpn.data.domain.ConnectionState.Connecting(s, 1, 6,
            com.thanhmich.veil.vpn.data.domain.ConnectStep.Handshaking)).viaVpn)
        assertEquals(SpeedRoute(true, "Japan"),
            SpeedRoute.from(com.thanhmich.veil.vpn.data.domain.ConnectionState.Connected(s, 0)))
    }
}

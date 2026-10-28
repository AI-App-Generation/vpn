package com.thanhmich.veil.vpn.corefeatures.speedtest

import com.thanhmich.veil.vpn.data.domain.SpeedProgress
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.Locale

/**
 * Kiểm chứng bằng mạng thật (opt-in, mặc định skip): `VEIL_LIVE_SPEED=1` chạy [SpeedTestEngine]
 * với cấu hình mặc định (speed.cloudflare.com, 4×download 10s, 3×upload 8s) y như app.
 */
class SpeedTestEngineLiveTest {

    @Test fun liveCloudflare() = runBlocking {
        assumeTrue("set VEIL_LIVE_SPEED=1 to run", System.getenv("VEIL_LIVE_SPEED") == "1")
        val stats = mutableListOf<PhaseStats>()
        val engine = SpeedTestEngine(
            network = { true },
            routeProvider = { SpeedRoute(false, null) },
            onPhaseStats = { synchronized(stats) { stats.add(it) } },
        )
        val started = System.currentTimeMillis()
        var samples = 0
        var finished: SpeedProgress.Finished? = null
        engine.run().collect { p ->
            when (p) {
                is SpeedProgress.Sample -> {
                    samples++
                    if (samples % 5 == 0) println("  sample ${p.phase} ${"%.1f".format(Locale.US, p.value)}")
                }
                is SpeedProgress.PhaseDone -> println("PHASE DONE ${p.phase} = ${"%.2f".format(Locale.US, p.value)}")
                is SpeedProgress.Finished -> finished = p
            }
        }
        stats.forEach {
            println(
                "STATS ${it.phase}: value=${"%.2f".format(Locale.US, it.value)} duration=${it.durationMs}ms " +
                    "bytes=${it.bytes} requests=${it.requests} failures=${it.failures}" +
                    if (it.rttsMs.isNotEmpty()) " rtts=${it.rttsMs}" else "",
            )
        }
        println("RESULT ${finished!!.result} total=${System.currentTimeMillis() - started}ms samples=$samples")
    }
}

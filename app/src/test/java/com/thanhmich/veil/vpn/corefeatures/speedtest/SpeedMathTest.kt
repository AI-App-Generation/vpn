package com.thanhmich.veil.vpn.corefeatures.speedtest

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedMathTest {

    @Test fun median() {
        assertEquals(0.0, SpeedMath.median(emptyList()), 0.0)
        assertEquals(20.0, SpeedMath.median(listOf(30, 10, 20)), 0.0)
        assertEquals(25.0, SpeedMath.median(listOf(40, 10, 20, 30)), 0.0)
    }

    @Test fun jitterIsMeanAbsDeltaInOrder() {
        assertEquals(0.0, SpeedMath.jitter(listOf(10)), 0.0)
        // |20-10| + |15-20| + |25-15| = 25 / 3
        assertEquals(25.0 / 3, SpeedMath.jitter(listOf(10, 20, 15, 25)), 1e-9)
    }

    @Test fun mbps() {
        assertEquals(8.0, SpeedMath.mbps(1_000_000, 1_000), 1e-9)
        assertEquals(0.0, SpeedMath.mbps(1_000_000, 0), 0.0)
    }

    @Test fun meterInterpolatesAndSlidesWindow() {
        val m = ThroughputMeter(startMs = 1_000)
        // 1 MB mỗi 200ms = 40 Mbps
        for (i in 1..10) m.record(1_000L + i * 200, i * 1_000_000L)
        assertEquals(500_000.0, m.bytesAt(1_100), 1e-6)
        assertEquals(40.0, m.windowMbps(3_000, 1_000), 1e-9)
        // cửa sổ bị cắt ở đầu pha: 0..300ms
        assertEquals(40.0, m.windowMbps(1_300, 1_000), 1e-9)
        assertEquals(10_000_000L, m.totalBytes)
    }

    @Test fun resultSkipsWarmup() {
        val m = ThroughputMeter(startMs = 0)
        // 2s đầu: 1 MB (slow start), 8s sau: 80 MB → 80 Mbps
        m.record(2_000, 1_000_000)
        m.record(10_000, 81_000_000)
        assertEquals(80.0, m.resultMbps(10_000, 2_000), 1e-9)
        // cả pha thì thấp hơn
        assertEquals(64.8, m.mbpsBetween(0, 10_000), 1e-9)
    }

    @Test fun resultFallsBackToWholePhaseWhenEndedEarly() {
        val m = ThroughputMeter(startMs = 0)
        m.record(1_000, 1_000_000)
        assertEquals(8.0, m.resultMbps(1_000, 2_000), 1e-9)
    }
}

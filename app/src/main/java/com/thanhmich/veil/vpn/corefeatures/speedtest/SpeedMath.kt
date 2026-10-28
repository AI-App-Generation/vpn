package com.thanhmich.veil.vpn.corefeatures.speedtest

import kotlin.math.abs

/** Các phép tính thuần của speed test (không I/O, unit test được). */
object SpeedMath {

    /** Trung vị; rỗng → 0. */
    fun median(values: List<Long>): Double {
        if (values.isEmpty()) return 0.0
        val s = values.sorted()
        val mid = s.size / 2
        return if (s.size % 2 == 1) s[mid].toDouble() else (s[mid - 1] + s[mid]) / 2.0
    }

    /** Jitter = trung bình |Δ| giữa các RTT liên tiếp (theo thứ tự đo); < 2 mẫu → 0. */
    fun jitter(values: List<Long>): Double {
        if (values.size < 2) return 0.0
        var sum = 0L
        for (i in 1 until values.size) sum += abs(values[i] - values[i - 1])
        return sum.toDouble() / (values.size - 1)
    }

    /** Mbps = bytes × 8 / 1e6 / giây; [durationMs] ≤ 0 → 0. */
    fun mbps(bytes: Long, durationMs: Long): Double =
        if (durationMs <= 0 || bytes <= 0) 0.0 else bytes * 8.0 / 1e6 / (durationMs / 1000.0)
}

/**
 * Ghi các điểm (thời điểm ms, tổng byte tích luỹ) của 1 pha, rồi tính Mbps giữa 2 thời điểm
 * bằng nội suy tuyến tính. Thời gian tính tương đối so với [startMs].
 * Không thread-safe: chỉ sampler gọi.
 */
class ThroughputMeter(val startMs: Long) {
    private val times = ArrayList<Long>().apply { add(startMs) }
    private val bytes = ArrayList<Long>().apply { add(0L) }

    val lastTimeMs: Long get() = times.last()
    val totalBytes: Long get() = bytes.last()

    /** Ghi tổng byte [totalBytes] tại [nowMs] (thời gian không giảm). */
    fun record(nowMs: Long, totalBytes: Long) {
        val t = maxOf(nowMs, times.last())
        times.add(t)
        bytes.add(maxOf(totalBytes, bytes.last()))
    }

    /** Tổng byte nội suy tại [t]. */
    fun bytesAt(t: Long): Double {
        if (t <= times.first()) return bytes.first().toDouble()
        if (t >= times.last()) return bytes.last().toDouble()
        var i = times.binarySearch(t)
        if (i >= 0) {
            // có thể trùng thời gian: lấy điểm cuối cùng có cùng t
            while (i + 1 < times.size && times[i + 1] == t) i++
            return bytes[i].toDouble()
        }
        val hi = -i - 1
        val lo = hi - 1
        val t0 = times[lo]; val t1 = times[hi]
        val b0 = bytes[lo].toDouble(); val b1 = bytes[hi].toDouble()
        return b0 + (b1 - b0) * (t - t0) / (t1 - t0).toDouble()
    }

    /** Mbps trung bình trong [fromMs, toMs]. */
    fun mbpsBetween(fromMs: Long, toMs: Long): Double {
        val d = toMs - fromMs
        if (d <= 0) return 0.0
        val b = bytesAt(toMs) - bytesAt(fromMs)
        return if (b <= 0) 0.0 else b * 8.0 / 1e6 / (d / 1000.0)
    }

    /** Mbps tức thời: cửa sổ trượt [windowMs] kết thúc ở [nowMs] (cắt tại đầu pha). */
    fun windowMbps(nowMs: Long, windowMs: Long): Double =
        mbpsBetween(maxOf(startMs, nowMs - windowMs), nowMs)

    /**
     * Kết quả pha: Mbps từ `start + skipMs` tới [endMs] (bỏ TCP slow start).
     * Pha kết thúc sớm trước khi qua [skipMs] → dùng toàn pha.
     */
    fun resultMbps(endMs: Long, skipMs: Long): Double {
        val from = startMs + skipMs
        return if (endMs - from > 0) mbpsBetween(from, endMs) else mbpsBetween(startMs, endMs)
    }
}

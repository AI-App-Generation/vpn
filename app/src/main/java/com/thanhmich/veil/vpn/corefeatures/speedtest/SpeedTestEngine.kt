package com.thanhmich.veil.vpn.corefeatures.speedtest

import com.thanhmich.veil.vpn.corefeatures.vpn.NetworkChecker
import com.thanhmich.veil.vpn.data.domain.ConnectionState
import com.thanhmich.veil.vpn.data.domain.SpeedError
import com.thanhmich.veil.vpn.data.domain.SpeedPhase
import com.thanhmich.veil.vpn.data.domain.SpeedProgress
import com.thanhmich.veil.vpn.data.domain.SpeedResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.IOException
import java.util.Collections
import java.util.Random
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

/** Lỗi của [SpeedTestEngine.run]. */
class SpeedTestException(val error: SpeedError, cause: Throwable? = null) :
    Exception("Speed test failed: $error", cause)

/** Tuyến đo lúc bắt đầu (M07: lấy từ `VpnConnectionManager.state`). */
data class SpeedRoute(val viaVpn: Boolean, val vpnCountry: String?) {
    companion object {
        /** Chỉ `Connected` mới tính là đi qua VPN. */
        fun from(state: ConnectionState): SpeedRoute = when (state) {
            is ConnectionState.Connected -> SpeedRoute(true, state.server.country)
            else -> SpeedRoute(false, null)
        }
    }
}

/** Tham số đo; mặc định đúng M07, test thu nhỏ lại. */
data class SpeedTestConfig(
    /** Phải kết thúc bằng `/`. */
    val baseUrl: String = "https://speed.cloudflare.com/",
    val pingCount: Int = 10,
    val minPingSuccess: Int = 5,
    val downloadStreams: Int = 4,
    val downloadDurationMs: Long = 10_000,
    // Cloudflare currently rate-limits 10–25 MB __down requests with HTTP 429.
    // Repeating smaller chunks keeps the streams busy without tripping that limit.
    val downloadBytesPerRequest: Long = 5_000_000,
    val uploadStreams: Int = 3,
    val uploadDurationMs: Long = 8_000,
    val uploadBytesPerRequest: Long = 5_000_000,
    /** Bỏ giai đoạn đầu mỗi pha throughput (TCP slow start). */
    val warmupSkipMs: Long = 2_000,
    val sampleIntervalMs: Long = 200,
    val windowMs: Long = 1_000,
    val bufferSize: Int = 64 * 1024,
    /** 1 connection lỗi liên tiếp chừng này lần → dừng connection đó. */
    val maxConsecutiveFailures: Int = 3,
    val retryBackoffMs: Long = 200,
)

/** Số liệu thô của 1 pha (debug / bằng chứng). */
data class PhaseStats(
    val phase: SpeedPhase,
    val value: Double,
    val durationMs: Long,
    val bytes: Long,
    val requests: Int,
    val failures: Int,
    /** RTT từng request thành công (chỉ pha Ping). */
    val rttsMs: List<Long> = emptyList(),
)

/**
 * M07 — đo ping/download/upload bằng HTTP trên thiết bị (Cloudflare `speed.cloudflare.com`).
 *
 * [run] là cold flow: mỗi lần collect = 1 lượt đo; huỷ collector → huỷ mọi call đang chạy.
 * Lỗi: `SpeedTestException(NoNetwork | ServerUnreachable | Unknown)`.
 * `VpnChanged` do `SpeedViewModel` phát hiện (theo dõi `ConnectionState`), engine không phát.
 */
class SpeedTestEngine(
    private val network: NetworkChecker,
    private val routeProvider: () -> SpeedRoute,
    private val config: SpeedTestConfig = SpeedTestConfig(),
    private val client: OkHttpClient = defaultClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    /** Đồng hồ đơn điệu (ms) để đo thời gian pha. */
    private val monoClock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val wallClock: () -> Long = System::currentTimeMillis,
    private val onPhaseStats: ((PhaseStats) -> Unit)? = null,
) {

    private val base: HttpUrl = config.baseUrl.toHttpUrl()

    fun run(): Flow<SpeedProgress> = flow {
        if (!network.isOnline()) throw SpeedTestException(SpeedError.NoNetwork)
        // Throughput phases intentionally cancel in-flight calls when their timer expires.
        // Do not let a following test inherit an idle HTTP/1.1 connection from that run.
        client.connectionPool.evictAll()
        val route = routeProvider()

        val ping = measurePing()
        emit(SpeedProgress.PhaseDone(SpeedPhase.Ping, ping.first))

        val down = measureThroughput(
            SpeedPhase.Download, config.downloadStreams, config.downloadDurationMs,
        ) { calls, counter -> downloadOnce(calls, counter) }
        emit(SpeedProgress.PhaseDone(SpeedPhase.Download, down))

        val up = measureThroughput(
            SpeedPhase.Upload, config.uploadStreams, config.uploadDurationMs,
        ) { calls, counter -> uploadOnce(calls, counter) }
        emit(SpeedProgress.PhaseDone(SpeedPhase.Upload, up))

        emit(
            SpeedProgress.Finished(
                SpeedResult(
                    timestamp = wallClock(),
                    pingMs = ping.first.roundToInt(),
                    jitterMs = ping.second.roundToInt(),
                    downMbps = down,
                    upMbps = up,
                    viaVpn = route.viaVpn,
                    vpnCountry = route.vpnCountry,
                ),
            ),
        )
    }.flowOn(ioDispatcher).catch { e ->
        when (e) {
            is SpeedTestException, is CancellationException -> throw e
            else -> throw SpeedTestException(SpeedError.Unknown, e)
        }
    }

    // ---------------------------------------------------------------- Ping

    /** @return (median ms, jitter ms). */
    private suspend fun FlowCollector<SpeedProgress>.measurePing(): Pair<Double, Double> {
        val calls = CallTracker()
        val started = monoClock()
        val rtts = ArrayList<Long>()
        var failures = 0
        try {
            // Warm-up: mở TCP + TLS, không tính.
            runCatching { pingOnce(calls) }
            repeat(config.pingCount) {
                currentCoroutineContext().ensureActive()
                val rtt = try {
                    pingOnce(calls)
                } catch (e: IOException) {
                    null
                }
                if (rtt == null) failures++ else {
                    rtts.add(rtt)
                    emit(SpeedProgress.Sample(SpeedPhase.Ping, rtt.toDouble()))
                }
            }
        } finally {
            calls.cancelAll()
        }
        val median = SpeedMath.median(rtts)
        val jitter = SpeedMath.jitter(rtts)
        onPhaseStats?.invoke(
            PhaseStats(SpeedPhase.Ping, median, monoClock() - started, 0, config.pingCount, failures, rtts),
        )
        if (rtts.size < config.minPingSuccess) throw SpeedTestException(SpeedError.ServerUnreachable)
        return median to jitter
    }

    /** RTT 1 request `__down?bytes=0`; lỗi/HTTP không 2xx → IOException. */
    private fun pingOnce(calls: CallTracker): Long {
        val call = calls.newCall(downloadRequest(0)) ?: throw IOException("stopped")
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                resp.body?.source()?.readAll(okio.blackholeSink())
                return (resp.receivedResponseAtMillis - resp.sentRequestAtMillis).coerceAtLeast(0)
            }
        } finally {
            calls.remove(call)
        }
    }

    // ---------------------------------------------------------- Throughput

    /**
     * Chạy [streams] worker song song trong [durationMs], sampler mỗi [SpeedTestConfig.sampleIntervalMs]
     * emit Mbps cửa sổ trượt. Hết giờ (hoặc mọi worker bỏ cuộc) → huỷ mọi call.
     * [once] thực hiện trọn 1 request (tạo call qua tracker), cộng byte vào counter; lỗi → IOException.
     */
    private suspend fun FlowCollector<SpeedProgress>.measureThroughput(
        phase: SpeedPhase,
        streams: Int,
        durationMs: Long,
        once: (calls: CallTracker, counter: AtomicLong) -> Unit,
    ): Double {
        val counter = AtomicLong()
        val calls = CallTracker()
        val requests = AtomicInteger()
        val failures = AtomicInteger()
        val alive = AtomicInteger(streams)
        val start = monoClock()
        val meter = ThroughputMeter(start)
        var end = start
        coroutineScope {
            val workers: List<Job> = List(streams) {
                launch {
                    var consecutive = 0
                    try {
                        while (isActive && !calls.stopped) {
                            requests.incrementAndGet()
                            val before = counter.get()
                            val ok = try {
                                once(calls, counter)
                                true
                            } catch (e: IOException) {
                                false
                            }
                            if (calls.stopped) break
                            if (ok) consecutive = 0 else {
                                failures.incrementAndGet()
                                // Có chuyển được byte thì vẫn coi connection còn sống.
                                consecutive = if (counter.get() > before) 0 else consecutive + 1
                                if (consecutive >= config.maxConsecutiveFailures) break
                                delay(config.retryBackoffMs)
                            }
                        }
                    } finally {
                        alive.decrementAndGet()
                    }
                }
            }
            try {
                while (true) {
                    val now = monoClock()
                    val remaining = start + durationMs - now
                    if (remaining <= 0 || alive.get() == 0) break
                    delay(minOf(config.sampleIntervalMs, remaining))
                    val t = monoClock()
                    meter.record(t, counter.get())
                    if (t - start < durationMs && alive.get() > 0) {
                        emit(SpeedProgress.Sample(phase, meter.windowMbps(t, config.windowMs)))
                    }
                }
                end = monoClock()
                meter.record(end, counter.get())
            } finally {
                calls.cancelAll()
                workers.forEach { it.cancel() }
            }
            workers.joinAll()
        }
        val bytes = meter.totalBytes
        val value = meter.resultMbps(end, config.warmupSkipMs)
        onPhaseStats?.invoke(PhaseStats(phase, value, end - start, bytes, requests.get(), failures.get()))
        if (bytes <= 0) throw SpeedTestException(SpeedError.ServerUnreachable)
        return value
    }

    /** 1 lượt GET `__down?bytes=N`, đọc buffer 64KB cộng dần vào [counter]. */
    private fun downloadOnce(calls: CallTracker, counter: AtomicLong) {
        val call = calls.newCall(downloadRequest(config.downloadBytesPerRequest)) ?: return
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                val stream = resp.body?.byteStream() ?: throw IOException("no body")
                val buf = ByteArray(config.bufferSize)
                while (!calls.stopped) {
                    val n = stream.read(buf)
                    if (n < 0) break
                    counter.addAndGet(n.toLong())
                }
            }
        } finally {
            calls.remove(call)
        }
    }

    /** 1 lượt POST `__up` body streaming [SpeedTestConfig.uploadBytesPerRequest] byte; đếm khi ghi vào sink. */
    private fun uploadOnce(calls: CallTracker, counter: AtomicLong) {
        val call = calls.newCall(uploadRequest(UploadBody(config.uploadBytesPerRequest, calls, counter))) ?: return
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                resp.body?.source()?.readAll(okio.blackholeSink())
            }
        } finally {
            calls.remove(call)
        }
    }

    /** Body lặp 1 buffer ngẫu nhiên 64KB; dừng (IOException) khi pha đã dừng. */
    private inner class UploadBody(
        private val length: Long,
        private val calls: CallTracker,
        private val counter: AtomicLong,
    ) : RequestBody() {
        override fun contentType(): MediaType = OCTET
        override fun contentLength(): Long = length
        override fun writeTo(sink: BufferedSink) {
            var left = length
            while (left > 0) {
                if (calls.stopped) throw IOException("stopped")
                val n = minOf(left, uploadChunk.size.toLong()).toInt()
                sink.write(uploadChunk, 0, n)
                counter.addAndGet(n.toLong())
                left -= n
            }
        }
    }

    private val uploadChunk: ByteArray by lazy { ByteArray(config.bufferSize).also { Random().nextBytes(it) } }

    // ------------------------------------------------------------- Requests

    private fun downloadRequest(bytes: Long): Request = Request.Builder()
        .url(base.newBuilder().addPathSegment("__down").addQueryParameter("bytes", bytes.toString()).build())
        .header("Cache-Control", "no-cache")
        .get()
        .build()

    private fun uploadRequest(body: RequestBody): Request = Request.Builder()
        .url(base.newBuilder().addPathSegment("__up").build())
        .post(body)
        .build()

    /** Tập call đang chạy của 1 pha; [cancelAll] dừng pha (call tạo sau đó bị huỷ ngay). */
    private inner class CallTracker {
        private val active: MutableSet<Call> = Collections.synchronizedSet(HashSet())

        @Volatile
        var stopped = false
            private set

        fun newCall(request: Request): Call? {
            if (stopped) return null
            val call = client.newCall(request)
            active.add(call)
            if (stopped) {
                call.cancel(); active.remove(call); return null
            }
            return call
        }

        fun remove(call: Call) {
            active.remove(call)
        }

        fun cancelAll() {
            stopped = true
            synchronized(active) { active.toList() }.forEach { it.cancel() }
        }
    }

    companion object {
        private val OCTET: MediaType = "application/octet-stream".toMediaType()

        /**
         * OkHttp riêng cho speed test (M07). Ép HTTP/1.1 để mỗi luồng là 1 kết nối TCP riêng
         * (HTTP/2 sẽ ghép mọi luồng vào 1 kết nối → không còn "nhiều kết nối song song").
         */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
            .cache(null)
            .retryOnConnectionFailure(false)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}

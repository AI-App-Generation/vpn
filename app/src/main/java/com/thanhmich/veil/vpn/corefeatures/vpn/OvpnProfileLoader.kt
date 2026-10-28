package com.thanhmich.veil.vpn.corefeatures.vpn

import com.thanhmich.veil.vpn.data.domain.VpnServer
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** Lỗi tải/validate `.ovpn` (M04). Caller (M06) coi mọi lỗi là "server hỏng" → thử server kế. */
sealed class OvpnError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** HTTP không phải 2xx. */
    class Http(val code: Int) : OvpnError("HTTP $code")

    /** Nội dung không phải profile OpenVPN (HTML, thiếu `client`/`dev`/`remote`, quá lớn, URL hỏng). */
    class Invalid(val reason: String) : OvpnError("Invalid profile: $reason")

    /** Quá [OvpnProfileLoader.CALL_TIMEOUT_MS] (callTimeout của OkHttp hoặc socket timeout). */
    class Timeout(cause: Throwable? = null) : OvpnError("Timeout", cause)

    /** Lỗi IO khác (DNS, kết nối bị từ chối, TLS...). */
    class Network(cause: Throwable? = null) : OvpnError("Network error: ${cause?.message}", cause)
}

/** Nguồn profile `.ovpn` — interface để M06 test bằng fake. */
fun interface ProfileLoader {
    /** Trả nội dung `.ovpn` hợp lệ, hoặc failure chứa [OvpnError]. Không throw (trừ CancellationException). */
    suspend fun load(server: VpnServer): Result<String>
}

/** Validate thuần nội dung `.ovpn` (M04 bước 3). */
object OvpnValidator {

    private val CLIENT_LINE = Regex("(?m)^\\s*client\\s*$")
    private val DEV_LINE = Regex("(?m)^\\s*dev\\s+(tun|tap)\\b")
    private val REMOTE_LINE = Regex("(?m)^\\s*remote\\s+\\S+\\s+\\d+")

    /** null = hợp lệ; khác null = lý do không hợp lệ. */
    fun invalidReason(content: String): String? {
        val text = content.removePrefix("﻿")
        val trimmed = text.trimStart()
        return when {
            trimmed.isEmpty() -> "empty"
            trimmed.startsWith("<") -> "starts with '<' (HTML/XML)"
            text.contains("<html", ignoreCase = true) -> "contains <html"
            !CLIENT_LINE.containsMatchIn(text) -> "missing 'client'"
            !DEV_LINE.containsMatchIn(text) -> "missing 'dev tun|tap'"
            !REMOTE_LINE.containsMatchIn(text) -> "missing 'remote <host> <port>'"
            else -> null
        }
    }

    fun isValid(content: String): Boolean = invalidReason(content) == null
}

/**
 * M04: lấy nội dung `.ovpn` hợp lệ cho [VpnServer].
 *
 * 1. `server.ovpnContent` không rỗng và hợp lệ → trả luôn (không hợp lệ → thử URL nếu có).
 * 2. GET `ovpnUrl` với [HEADER_ACCEPT] + [USER_AGENT] (Chrome Android), follow redirect,
 *    callTimeout 12s, đọc tối đa [MAX_BYTES].
 * 3. Validate bằng [OvpnValidator]. Không lưu file/URL.
 */
class OvpnProfileLoader(
    private val client: OkHttpClient = defaultClient(),
) : ProfileLoader {

    override suspend fun load(server: VpnServer): Result<String> {
        val inline = server.ovpnContent
        var inlineError: OvpnError? = null
        if (!inline.isNullOrBlank()) {
            val reason = OvpnValidator.invalidReason(inline)
            if (reason == null) return Result.success(inline)
            inlineError = OvpnError.Invalid("inline: $reason")
        }
        if (server.ovpnUrl.isBlank()) {
            return Result.failure(inlineError ?: OvpnError.Invalid("no ovpnUrl"))
        }
        return download(server.ovpnUrl)
    }

    /** Tải + validate 1 URL. Public để kiểm chứng bằng mạng thật. */
    suspend fun download(url: String): Result<String> {
        val httpUrl = url.trim().toHttpUrlOrNull()
            ?: return Result.failure(OvpnError.Invalid("bad url"))
        val request = buildRequest(httpUrl.toString())
        val call = client.newCall(request)
        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resume(Result.failure(mapIoError(e)))
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = try {
                        response.use { readBody(it) }
                    } catch (e: IOException) {
                        Result.failure(mapIoError(e))
                    }
                    if (cont.isActive) cont.resume(result)
                }
            })
        }
    }

    private fun readBody(response: Response): Result<String> {
        if (!response.isSuccessful) return Result.failure(OvpnError.Http(response.code))
        val body = response.body ?: return Result.failure(OvpnError.Invalid("no body"))
        val source = body.source()
        // request(n) đọc tới khi buffer có n byte hoặc EOF.
        source.request(MAX_BYTES + 1)
        val size = source.buffer.size
        if (size > MAX_BYTES) return Result.failure(OvpnError.Invalid("larger than $MAX_BYTES bytes"))
        val text = source.buffer.readString(size, Charsets.UTF_8)
        val reason = OvpnValidator.invalidReason(text)
        return if (reason == null) Result.success(text) else Result.failure(OvpnError.Invalid(reason))
    }

    companion object {
        const val CALL_TIMEOUT_MS = 12_000L
        const val MAX_BYTES = 128L * 1024
        const val HEADER_ACCEPT = "application/x-openvpn-profile,text/plain,*/*"
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/131.0.0.0 Mobile Safari/537.36"

        fun buildRequest(url: String): Request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", HEADER_ACCEPT)
            .header("User-Agent", USER_AGENT)
            .build()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .callTimeout(CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()

        /** callTimeout → InterruptedIOException("timeout"); SocketTimeoutException là lớp con. */
        internal fun mapIoError(e: IOException): OvpnError =
            if (e is InterruptedIOException) OvpnError.Timeout(e) else OvpnError.Network(e)
    }
}

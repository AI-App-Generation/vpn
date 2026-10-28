package com.thanhmich.veil.vpn.corefeatures.vpn

import kotlinx.coroutines.runBlocking
import okhttp3.Request
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.Base64

/**
 * Kiểm chứng bằng mạng thật (opt-in, mặc định bị skip):
 * - `VEIL_LIVE_OVPN_URL=<url openvpn_download.aspx...>` → gọi [OvpnProfileLoader.download] y như app.
 * - `VEIL_LIVE_CSV=1` → tải `https://www.vpngate.net/api/iphone/`, decode base64 cột cuối, validate.
 */
class OvpnProfileLoaderLiveTest {

    @Test fun liveDownload() = runBlocking {
        val url = System.getenv("VEIL_LIVE_OVPN_URL")
        assumeTrue("set VEIL_LIVE_OVPN_URL to run", !url.isNullOrBlank())
        val req = OvpnProfileLoader.buildRequest(url!!)
        println("LIVE GET $url")
        println("  Accept: ${req.header("Accept")}")
        println("  User-Agent: ${req.header("User-Agent")}")
        val started = System.currentTimeMillis()
        val result = OvpnProfileLoader().download(url)
        println("  took ${System.currentTimeMillis() - started} ms")
        result.onSuccess { text ->
            println("  OK bytes=${text.length} validate=${OvpnValidator.invalidReason(text) ?: "valid"}")
            text.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.take(8)
                .forEach { println("    | $it") }
        }.onFailure { println("  FAIL $it") }
        check(result.isSuccess) { "live download failed: ${result.exceptionOrNull()}" }
    }

    @Test fun liveCsvConfigsValidate() {
        assumeTrue("set VEIL_LIVE_CSV=1 to run", System.getenv("VEIL_LIVE_CSV") == "1")
        val client = OvpnProfileLoader.defaultClient().newBuilder()
            .callTimeout(60, java.util.concurrent.TimeUnit.SECONDS).build()
        val req = Request.Builder().url("https://www.vpngate.net/api/iphone/")
            .header("User-Agent", OvpnProfileLoader.USER_AGENT).build()
        val body = client.newCall(req).execute().use { r ->
            println("CSV HTTP ${r.code}")
            r.body!!.string()
        }
        val rows = body.lineSequence().filter { it.isNotBlank() && !it.startsWith("*") && !it.startsWith("#") }.toList()
        var valid = 0
        var invalid = 0
        rows.forEachIndexed { i, row ->
            val b64 = row.substringAfterLast(',').trim()
            val text = try {
                String(Base64.getDecoder().decode(b64), Charsets.UTF_8)
            } catch (e: IllegalArgumentException) {
                invalid++; return@forEachIndexed
            }
            val reason = OvpnValidator.invalidReason(text)
            if (reason == null) valid++ else { invalid++; println("  row $i invalid: $reason") }
            if (i == 0) {
                println("  first row host=${row.split(',')[0]} decoded=${text.length} chars, validate=${reason ?: "valid"}")
                text.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.take(8)
                    .forEach { println("    | $it") }
            }
        }
        println("CSV rows=${rows.size} valid=$valid invalid=$invalid")
        check(valid > 0)
        // Kiểm chứng phía ngược: HTML thật của vpngate bị loại.
        println("HTML page validate=" + OvpnValidator.invalidReason("<!DOCTYPE html>\n<html>...</html>"))
    }
}

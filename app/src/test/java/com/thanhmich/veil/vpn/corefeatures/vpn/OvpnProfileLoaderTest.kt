package com.thanhmich.veil.vpn.corefeatures.vpn

import com.thanhmich.veil.vpn.data.domain.VpnServer
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.io.InterruptedIOException

class OvpnProfileLoaderTest {

    private val valid = """
        ###############################################################################
        # OpenVPN 2.0 Sample Configuration File
        # remote example.com 1194   (comment, không tính)
        ###############################################################################
        dev tun
        proto tcp
        remote 219.100.37.10 443
        cipher AES-128-CBC
        auth SHA1
        resolv-retry infinite
        nobind
        persist-key
        persist-tun
        client
        verb 3
        <ca>
        -----BEGIN CERTIFICATE-----
        MIIF
        -----END CERTIFICATE-----
        </ca>
    """.trimIndent().replace("\n", "\r\n")

    // ---------------- validate (thuần)

    @Test fun validate_validProfile() {
        assertNull(OvpnValidator.invalidReason(valid))
        assertNull(OvpnValidator.invalidReason("﻿" + valid))
    }

    @Test fun validate_html() {
        assertNotNull(OvpnValidator.invalidReason("<!DOCTYPE html><html><body>Not found</body></html>"))
        assertNotNull(OvpnValidator.invalidReason("   \n<html>"))
        // Không bắt đầu bằng '<' nhưng có <html ở giữa.
        assertNotNull(OvpnValidator.invalidReason("error\n<HTML>\n$valid"))
    }

    @Test fun validate_missingRemote() {
        val noRemote = valid.lines().filterNot { it.startsWith("remote ") }.joinToString("\n")
        assertEquals("missing 'remote <host> <port>'", OvpnValidator.invalidReason(noRemote))
        // remote không có port
        assertNotNull(OvpnValidator.invalidReason(noRemote + "\nremote host.example"))
    }

    @Test fun validate_missingClientOrDev() {
        assertNotNull(OvpnValidator.invalidReason(valid.replace("client\r\n", "")))
        assertNotNull(OvpnValidator.invalidReason(valid.replace("dev tun", "dev xyz")))
        assertNull(OvpnValidator.invalidReason(valid.replace("dev tun", "dev tap")))
        assertNotNull(OvpnValidator.invalidReason(""))
    }

    // ---------------- loader (OkHttp với interceptor giả, không cần mạng)

    private class Fake(val respond: (Request) -> Response) : Interceptor {
        var last: Request? = null
        override fun intercept(chain: Interceptor.Chain): Response {
            last = chain.request()
            return respond(chain.request())
        }
    }

    private fun resp(req: Request, code: Int, body: String) = Response.Builder()
        .request(req).protocol(Protocol.HTTP_1_1).code(code).message("x")
        .body(body.toResponseBody("text/plain".toMediaType())).build()

    private fun loaderWith(fake: Fake) = OvpnProfileLoader(
        OvpnProfileLoader.defaultClient().newBuilder().addInterceptor(fake).build()
    )

    private fun server(url: String = "https://www.vpngate.net/common/openvpn_download.aspx?x=1", content: String? = null) =
        VpnServer("h", "Japan", "", 1, "h", "1.1.1.1", url, 1, null, content)

    @Test fun load_success_sendsHeaders() = runBlocking {
        val fake = Fake { resp(it, 200, valid) }
        val r = loaderWith(fake).load(server())
        assertEquals(valid, r.getOrThrow())
        val req = fake.last!!
        assertEquals(OvpnProfileLoader.HEADER_ACCEPT, req.header("Accept"))
        assertTrue(req.header("User-Agent")!!.contains("Chrome/"))
        assertTrue(req.header("User-Agent")!!.contains("Android"))
    }

    @Test fun load_http404() = runBlocking {
        val r = loaderWith(Fake { resp(it, 404, "nope") }).load(server())
        assertEquals(404, (r.exceptionOrNull() as OvpnError.Http).code)
    }

    @Test fun load_html200_isInvalid() = runBlocking {
        val r = loaderWith(Fake { resp(it, 200, "<html>dead</html>") }).load(server())
        assertTrue(r.exceptionOrNull() is OvpnError.Invalid)
    }

    @Test fun load_tooLarge_isInvalid() = runBlocking {
        val big = valid + "\n#" + "a".repeat(OvpnProfileLoader.MAX_BYTES.toInt())
        val r = loaderWith(Fake { resp(it, 200, big) }).load(server())
        assertTrue(r.exceptionOrNull() is OvpnError.Invalid)
    }

    @Test fun load_timeoutAndNetworkErrors() = runBlocking {
        val t = loaderWith(Fake { throw InterruptedIOException("timeout") }).load(server())
        assertTrue(t.exceptionOrNull() is OvpnError.Timeout)
        val n = loaderWith(Fake { throw IOException("refused") }).load(server())
        assertTrue(n.exceptionOrNull() is OvpnError.Network)
    }

    @Test fun load_inlineContent_noHttp() = runBlocking {
        val fake = Fake { error("không được gọi HTTP") }
        assertEquals(valid, loaderWith(fake).load(server(content = valid)).getOrThrow())
        assertNull(fake.last)
    }

    @Test fun load_invalidInline_fallsBackToUrl() = runBlocking {
        val fake = Fake { resp(it, 200, valid) }
        assertEquals(valid, loaderWith(fake).load(server(content = "<html>")).getOrThrow())
    }

    @Test fun load_blankUrl_invalid() = runBlocking {
        val r = loaderWith(Fake { error("x") }).load(server(url = ""))
        assertTrue(r.exceptionOrNull() is OvpnError.Invalid)
    }
}

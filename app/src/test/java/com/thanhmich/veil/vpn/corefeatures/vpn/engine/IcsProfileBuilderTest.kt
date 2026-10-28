package com.thanhmich.veil.vpn.corefeatures.vpn.engine

import android.app.Application
import de.blinkt.openvpn.VpnProfile
import de.blinkt.openvpn.core.ConfigParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** ConfigParser/VpnProfile dùng android.text.* → chạy dưới Robolectric (JVM). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class IcsProfileBuilderTest {

    private val pem = "MIIBszCCAVmgAwIBAgIUQmFzZTY0RHVtbXlDZXJ0aWZpY2F0ZTAKBggqhkjOPQQDAjAS\n" +
        "MRAwDgYDVQQDDAdWUE5HYXRlMB4XDTI2MDEwMTAwMDAwMFoXDTM2MDEwMTAwMDAwMFow\n"

    private val vpnGateOvpn = """
        ###############################################################################
        # OpenVPN 2.0 Sample Configuration File (VPN Gate style)
        ###############################################################################
        client
        dev tun
        proto tcp
        remote 1.2.3.4 443
        cipher AES-128-CBC
        auth SHA1
        resolv-retry infinite
        nobind
        persist-key
        persist-tun
        verb 3
        #auth-user-pass
        <ca>
        -----BEGIN CERTIFICATE-----
        $pem-----END CERTIFICATE-----
        </ca>
        <cert>
        -----BEGIN CERTIFICATE-----
        $pem-----END CERTIFICATE-----
        </cert>
        <key>
        -----BEGIN PRIVATE KEY-----
        $pem-----END PRIVATE KEY-----
        </key>
    """.trimIndent()

    @Test
    fun parsesVpnGateConfig() {
        val profile = IcsProfileBuilder.build(vpnGateOvpn, "vpn", "vpn", "Japan · public-vpn-1")

        assertEquals("Japan · public-vpn-1", profile.mName)
        assertEquals(1, profile.mConnections.size)
        val conn = profile.mConnections[0]
        assertEquals("1.2.3.4", conn.mServerName)
        assertEquals("443", conn.mServerPort)
        assertFalse("proto tcp → không UDP", conn.mUseUdp)
        assertEquals("vpn", profile.mUsername)
        assertEquals("vpn", profile.mPassword)
        assertEquals(VpnProfile.TYPE_USERPASS_CERTIFICATES, profile.mAuthenticationType)
        assertEquals("AES-128-CBC", profile.mCipher)
        assertEquals("SHA1", profile.mAuth)
        assertEquals("AES-256-GCM:AES-128-GCM:CHACHA20-POLY1305:AES-128-CBC", profile.mDataCiphers)
        assertTrue(profile.mCaFilename.contains("BEGIN CERTIFICATE"))
        assertTrue(profile.mClientKeyFilename.contains("BEGIN PRIVATE KEY"))
        // R10: không app nào bị loại khỏi tunnel.
        assertTrue(profile.mAllowedAppsVpnAreDisallowed)
        assertTrue(profile.mAllowedAppsVpn.isEmpty())

        // Cùng kiểm tra engine chạy trước khi start.
        val check = profile.checkProfile(RuntimeEnvironment.getApplication(), false)
        assertEquals(de.blinkt.openvpn.R.string.no_error_found, check)

        // Config OpenVPN 2 thật sự sinh ra cho process :openvpn.
        val cfg = profile.getConfigFile(RuntimeEnvironment.getApplication(), false)
        println(cfg.lines().filter { l ->
            listOf("remote ", "proto", "cipher", "data-ciphers", "auth ", "auth-user-pass", "management-query-passwords", "client", "tls-client")
                .any { l.startsWith(it) }
        }.joinToString("\n"))
        assertTrue(cfg.contains("data-ciphers AES-256-GCM:AES-128-GCM:CHACHA20-POLY1305:AES-128-CBC"))
        assertTrue(cfg.contains("auth-user-pass"))
        assertTrue(cfg.contains("data-ciphers-fallback AES-128-CBC"))
    }

    @Test
    fun noClientCertUsesUserPass() {
        val ovpn = vpnGateOvpn.substringBefore("<cert>")
        val profile = IcsProfileBuilder.build(ovpn, "vpn", "vpn", "x")
        assertEquals(VpnProfile.TYPE_USERPASS, profile.mAuthenticationType)
    }

    @Test(expected = ConfigParser.ConfigParseError::class)
    fun htmlIsRejected() {
        IcsProfileBuilder.build("<html><body>503</body></html>", "vpn", "vpn", "x")
    }

    @Test
    fun dangerousCustomOptionsAreDropped() {
        val ovpn = vpnGateOvpn.replace(
            "verb 3\n",
            "verb 3\nlog /data/data/com.bblvn.composebaseproject/files/x.log\nlog-append /sdcard/a\n" +
                "writepid /data/local/tmp/pid\nup /system/bin/sh\nscript-security 2\n" +
                "tls-verify /bin/true\nsetenv-safe FOO bar\nhand-window 90\n",
        )
        val profile = IcsProfileBuilder.build(ovpn, "vpn", "vpn", "x")
        val cfg = profile.getConfigFile(RuntimeEnvironment.getApplication(), false)
        println("---- custom options ----\n" + profile.mCustomConfigOptions)
        listOf("log ", "log-append", "writepid", "up /system", "script-security", "tls-verify", "setenv-safe")
            .forEach { bad -> assertFalse("leaked: $bad", cfg.lines().any { it.trim().startsWith(bad) }) }
        assertTrue(profile.mUseCustomConfig)
        assertTrue(cfg.contains("hand-window 90"))
        assertTrue(cfg.contains("data-ciphers-fallback AES-128-CBC"))
    }

    @Test
    fun onlyBlockedOptionsDisablesCustomConfig() {
        val ovpn = vpnGateOvpn.replace("cipher AES-128-CBC\n", "cipher AES-256-GCM\n")
            .replace("resolv-retry infinite\n", "")
            .replace("verb 3\n", "verb 3\nlog /data/x.log\n")
        val profile = IcsProfileBuilder.build(ovpn, "vpn", "vpn", "x")
        // Bộ lọc trực tiếp (không phụ thuộc parser có tự bỏ `log` hay không).
        assertEquals(
            listOf("# header", "hand-window 90"),
            IcsProfileBuilder.filterCustomOptions(
                "# header\nlog /data/data/x/files/a.log\r\nLOG-APPEND /sdcard/b\nhand-window 90\n\twritepid /tmp/p\n",
            ),
        )
        assertFalse(profile.mUseCustomConfig)
        assertEquals("", profile.mCustomConfigOptions)
    }

    @Test
    fun authTypeFollowsParser() {
        val p = VpnProfile("t")
        p.mAuthenticationType = VpnProfile.TYPE_CERTIFICATES
        assertEquals(VpnProfile.TYPE_USERPASS_CERTIFICATES, IcsProfileBuilder.userPassAuthType(p))
        p.mAuthenticationType = VpnProfile.TYPE_PKCS12
        assertEquals(VpnProfile.TYPE_USERPASS_PKCS12, IcsProfileBuilder.userPassAuthType(p))
        p.mAuthenticationType = VpnProfile.TYPE_KEYSTORE
        p.mPKCS12Filename = "[[INLINE]]abc"
        assertEquals(VpnProfile.TYPE_USERPASS_PKCS12, IcsProfileBuilder.userPassAuthType(p))
        p.mPKCS12Filename = null
        assertEquals(VpnProfile.TYPE_USERPASS, IcsProfileBuilder.userPassAuthType(p))
    }

    @Test
    fun mergeKeepsExtraCiphers() {
        assertEquals(
            "AES-256-GCM:AES-128-GCM:CHACHA20-POLY1305:AES-128-CBC:AES-256-CBC",
            IcsProfileBuilder.mergeDataCiphers("aes-256-cbc", "AES-128-CBC"),
        )
    }
}

package com.thanhmich.veil.vpn.corefeatures.vpn.engine

import de.blinkt.openvpn.VpnProfile
import de.blinkt.openvpn.core.ConfigParser
import java.io.StringReader

/**
 * Dựng [VpnProfile] của ics-openvpn từ nội dung `.ovpn` (tách khỏi engine để unit test được).
 * Throw [ConfigParser.ConfigParseError] / [java.io.IOException] khi config hỏng.
 */
internal object IcsProfileBuilder {

    /** R4: VPNGate còn dùng AES-128-CBC; OpenVPN 2.6 chỉ chấp nhận cipher có trong data-ciphers. */
    val REQUIRED_DATA_CIPHERS = listOf("AES-256-GCM", "AES-128-GCM", "CHACHA20-POLY1305", "AES-128-CBC")

    private val AEAD_CIPHERS = setOf("AES-256-GCM", "AES-128-GCM", "CHACHA20-POLY1305")

    fun build(ovpn: String, username: String, password: String, displayName: String): VpnProfile {
        val parser = ConfigParser()
        parser.parseConfig(StringReader(ovpn))
        val profile = parser.convertProfile()

        profile.mName = displayName
        profile.mUsername = username
        profile.mPassword = password
        profile.mAuthenticationType = userPassAuthType(profile)
        profile.mDataCiphers = mergeDataCiphers(profile.mDataCiphers, profile.mCipher)

        // Option không map được của .ovpn (tải từ nguồn công cộng) bị chèn nguyên văn vào config →
        // lọc bỏ directive ghi file / chạy lệnh / đổi process trước khi đưa cho OpenVPN.
        val custom = filterCustomOptions(profile.mCustomConfigOptions).toMutableList()
        // R4: server cũ (không NCP) không báo cipher → OpenVPN 2.6 dùng data-ciphers-fallback.
        val legacyCipher = profile.mCipher?.trim()?.uppercase().orEmpty()
        if (legacyCipher.isNotEmpty() && legacyCipher != "NONE" && legacyCipher !in AEAD_CIPHERS) {
            custom += "data-ciphers-fallback $legacyCipher"
        }
        val hasDirective = custom.any { it.isNotBlank() && !it.trimStart().startsWith("#") }
        profile.mUseCustomConfig = hasDirective
        profile.mCustomConfigOptions = if (hasDirective) custom.joinToString("\n", postfix = "\n") else ""

        // R10: không loại app nào khỏi tunnel (kể cả chính app) → speed test đi qua VPN.
        profile.mAllowedAppsVpn.clear()
        profile.mAllowedAppsVpnAreDisallowed = true
        return profile
    }

    /**
     * Directive nguy hiểm nếu lọt vào custom options: ghi file (log/status/writepid/replay-persist…),
     * chạy script/lệnh (up, down, tls-verify, client-connect, iproute, plugin…), đổi process/thư mục
     * (daemon/chroot/cd/user/group) hoặc mở management/config khác.
     */
    val BLOCKED_DIRECTIVES = setOf(
        "log", "log-append", "writepid", "tls-export-cert", "cd", "chroot", "daemon", "syslog",
        "learn-address", "tls-verify", "client-connect", "client-disconnect", "iproute", "setenv-safe",
        "up", "down", "route-up", "route-pre-down", "ipchange", "auth-user-pass-verify",
        "tls-crypt-v2-verify", "script-security", "plugin", "status", "status-version",
        "replay-persist", "ifconfig-pool-persist", "management", "management-client-user",
        "management-client-group", "config", "askpass", "tmp-dir", "dns-updown", "user", "group",
        "inetd", "mlock", "setenv", "echo",
    )

    /** Bỏ dòng có directive đầu thuộc [BLOCKED_DIRECTIVES]; giữ comment/dòng khác. */
    fun filterCustomOptions(options: String?): List<String> =
        options.orEmpty().split('\n').map { it.trimEnd('\r') }.filter { line ->
            val directive = line.trim().substringBefore(' ').substringBefore('\t').lowercase()
            directive !in BLOCKED_DIRECTIVES
        }.filter { it.isNotBlank() }

    /**
     * Server VPNGate nhận user/pass `vpn`/`vpn`; giữ phần chứng chỉ mà parser suy ra:
     * cert → USERPASS_CERTIFICATES; pkcs12 (parser gán KEYSTORE + mPKCS12Filename) → USERPASS_PKCS12;
     * còn lại → USERPASS. Cert thiếu key / cipher BF-CBC → checkProfile báo lỗi → Error (chấp nhận).
     */
    fun userPassAuthType(profile: VpnProfile): Int = when (profile.mAuthenticationType) {
        VpnProfile.TYPE_CERTIFICATES, VpnProfile.TYPE_USERPASS_CERTIFICATES ->
            VpnProfile.TYPE_USERPASS_CERTIFICATES
        VpnProfile.TYPE_PKCS12, VpnProfile.TYPE_USERPASS_PKCS12 -> VpnProfile.TYPE_USERPASS_PKCS12
        VpnProfile.TYPE_KEYSTORE, VpnProfile.TYPE_USERPASS_KEYSTORE ->
            if (!profile.mPKCS12Filename.isNullOrEmpty()) VpnProfile.TYPE_USERPASS_PKCS12
            else VpnProfile.TYPE_USERPASS
        else -> VpnProfile.TYPE_USERPASS
    }

    /** Danh sách R4 trước (thứ tự ưu tiên), rồi các cipher có sẵn trong config mà R4 chưa có. */
    fun mergeDataCiphers(existing: String?, cipher: String?): String {
        val result = LinkedHashSet<String>()
        REQUIRED_DATA_CIPHERS.forEach { result += it }
        existing.orEmpty().split(':').map { it.trim().uppercase() }.filter { it.isNotEmpty() }
            .forEach { result += it }
        cipher?.trim()?.uppercase()?.takeIf { it.isNotEmpty() && it != "NONE" }?.let { result += it }
        return result.joinToString(":")
    }
}

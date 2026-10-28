package com.thanhmich.veil.vpn.data.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainModelsTest {

    private fun server(host: String = "public-vpn-1.opengw.net", ping: Int? = 23, ovpn: String? = null) = VpnServer(
        id = host,
        country = "Japan",
        countryFlag = "https://flagcdn.com/w80/jp.png",
        tier = 1,
        hostname = host,
        ipAddress = "219.100.37.1",
        ovpnUrl = "https://example.com/$host.ovpn",
        score = 1_234_567L,
        pingMs = ping,
        ovpnContent = ovpn,
    )

    private fun result(ts: Long) = SpeedResult(
        timestamp = ts, pingMs = 18, jitterMs = 3, downMbps = 87.4, upMbps = 23.1,
        viaVpn = ts % 2 == 0L, vpnCountry = if (ts % 2 == 0L) "Japan" else null,
    )

    // ---- ServerSelection ----

    @Test fun selection_auto_roundTrip() {
        assertEquals("auto", ServerSelection.Auto.encode())
        assertEquals(ServerSelection.Auto, ServerSelection.decode("auto"))
    }

    @Test fun selection_specific_roundTrip() {
        val s = ServerSelection.Specific("public-vpn-1.opengw.net", "Japan")
        assertEquals("server|public-vpn-1.opengw.net|Japan", s.encode())
        assertEquals(s, ServerSelection.decode(s.encode()))
    }

    @Test fun selection_countryWithSpacesAndPipe_roundTrip() {
        val s = ServerSelection.Specific("h1", "Korea Republic of|X")
        assertEquals(s, ServerSelection.decode(s.encode()))
    }

    @Test fun selection_emptyCountry_roundTrip() {
        val s = ServerSelection.Specific("h1", "")
        assertEquals(s, ServerSelection.decode(s.encode()))
    }

    @Test fun selection_invalid_fallsBackToAuto() {
        listOf(null, "", "  ", "garbage", "server", "server|", "server||Japan", "server|h1", "foo|h1|Japan")
            .forEach { assertEquals("input=$it", ServerSelection.Auto, ServerSelection.decode(it)) }
    }

    // ---- JSON round-trip ----

    @Test fun vpnServer_jsonRoundTrip() {
        listOf(server(), server(ping = null, ovpn = "client\ndev tun\n'quote\" ü")).forEach { s ->
            val json = DomainJson.encodeToString(VpnServer.serializer(), s)
            assertEquals(s, DomainJson.decodeFromString(VpnServer.serializer(), json))
        }
    }

    @Test fun speedResult_jsonRoundTrip() {
        val r = result(1_727_500_000_000L)
        val json = DomainJson.encodeToString(SpeedResult.serializer(), r)
        assertEquals(r, DomainJson.decodeFromString(SpeedResult.serializer(), json))
        val r2 = result(1_727_500_000_001L)
        assertEquals(listOf(r, r2), decodeSpeedHistory(encodeSpeedHistory(listOf(r, r2))))
    }

    @Test fun activeSession_jsonRoundTrip() {
        val a = ActiveSession(server(), connectedAtWallMs = 1_727_500_000_000L)
        assertEquals(a, decodeActiveSession(encodeActiveSession(a)))
    }

    @Test fun unknownKeys_ignored() {
        val json = encodeActiveSession(ActiveSession(server(), 5L)).replaceFirst("{", "{\"futureField\":1,")
        assertEquals(5L, decodeActiveSession(json)?.connectedAtWallMs)
    }

    @Test fun brokenJson_returnsDefaults() {
        listOf(null, "", "{", "not json", "[1,2]", "{\"server\":{}}").forEach {
            assertNull("input=$it", decodeActiveSession(it))
        }
        listOf(null, "", "[", "{}", "[{\"timestamp\":\"x\"}]").forEach {
            assertTrue("input=$it", decodeSpeedHistory(it).isEmpty())
        }
    }

    // ---- Speed history limit ----

    @Test fun speedHistory_newestFirst_cappedAt10() {
        var history = emptyList<SpeedResult>()
        for (ts in 1L..15L) history = history.prependSpeedResult(result(ts))
        assertEquals(SPEED_HISTORY_LIMIT, history.size)
        assertEquals((15L downTo 6L).toList(), history.map { it.timestamp })
    }

    @Test fun speedHistory_decode_capsOversizedStoredList() {
        val stored = (1L..12L).map { result(it) }
        assertEquals(10, decodeSpeedHistory(encodeSpeedHistory(stored)).size)
    }

    @Test fun accent_fromName() {
        assertEquals(AccentTheme.Teal, AccentTheme.fromName("Teal"))
        assertEquals(AccentTheme.Indigo, AccentTheme.fromName(null))
        assertEquals(AccentTheme.Indigo, AccentTheme.fromName("Pink"))
    }
}

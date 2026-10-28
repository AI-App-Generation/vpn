package com.thanhmich.veil.vpn.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fixture theo schema `api_specs/MOBILE_INTEGRATION.md` mục 2, với kiểu giá trị như Firestore SDK trả (số nguyên → Long). */
class ServerCatalogParserTest {

    private fun item(
        host: String?,
        country: String = "Japan",
        tier: Any? = 1L,
        score: Any? = "1000",
        lineQuality: Any? = "Ping: 14 ms",
        flag: Any? = "https://flagcdn.com/w80/jp.png",
        url: Any? = "https://www.vpngate.net/common/openvpn_download.aspx?sid=1&host=$host&/vpngate_${host}_tcp_443.ovpn",
        content: Any? = null,
    ): Map<String, Any?> = buildMap {
        put("country", country)
        put("countryFlag", flag)
        put("tier", tier)
        put("hostname", host)
        put("ipAddress", "219.100.37.10")
        put("openVpnOvpnUrl", url)
        put("score", score)
        put("lineQuality", lineQuality)
        if (content != null) put("openVpnOvpnContent", content)
    }

    @Test fun parse_specExample() {
        val c = ServerCatalogParser.parse(
            listOf(item("public-vpn-189.opengw.net", score = "1234567")),
            crawledAt = "2026-09-04T09:59:03.353Z",
            fromCache = true,
        )
        assertEquals(1, c.servers.size)
        val s = c.servers[0]
        assertEquals("public-vpn-189.opengw.net", s.id)
        assertEquals("public-vpn-189.opengw.net", s.hostname)
        assertEquals("Japan", s.country)
        assertEquals(1, s.tier)
        assertEquals(1_234_567L, s.score)
        assertEquals(14, s.pingMs)
        assertEquals("219.100.37.10", s.ipAddress)
        assertTrue(s.ovpnUrl.endsWith("_tcp_443.ovpn"))
        assertNull(s.ovpnContent)
        assertEquals(1_788_515_943_353L, c.crawledAt)
        assertTrue(c.fromCache)
    }

    @Test fun tier_variants() {
        assertEquals(1, ServerCatalogParser.parseTier(1L))
        assertEquals(1, ServerCatalogParser.parseTier(1.0))
        assertEquals(1, ServerCatalogParser.parseTier("1"))
        assertEquals(2, ServerCatalogParser.parseTier("x"))
        assertEquals(2, ServerCatalogParser.parseTier(null))
        assertEquals(2, ServerCatalogParser.parseTier(0L))
        assertEquals(2, ServerCatalogParser.parseTier(listOf(1)))
    }

    @Test fun score_variants() {
        assertEquals(1234L, ServerCatalogParser.parseScore("1234"))
        assertEquals(1234L, ServerCatalogParser.parseScore(" 1234 "))
        assertEquals(1234L, ServerCatalogParser.parseScore(1234L))
        assertEquals(1234L, ServerCatalogParser.parseScore(1234.0))
        assertEquals(0L, ServerCatalogParser.parseScore("abc"))
        assertEquals(0L, ServerCatalogParser.parseScore(""))
        assertEquals(0L, ServerCatalogParser.parseScore(null))
    }

    @Test fun ping_variants() {
        assertEquals(14, ServerCatalogParser.parsePing("Ping: 14 ms"))
        assertEquals(7, ServerCatalogParser.parsePing("7ms"))
        assertEquals(120, ServerCatalogParser.parsePing("Line: 1 Gbps, Ping: 120 MS"))
        assertNull(ServerCatalogParser.parsePing("Ping: - "))
        assertNull(ServerCatalogParser.parsePing(""))
        assertNull(ServerCatalogParser.parsePing(null))
        assertNull(ServerCatalogParser.parsePing(14L))
    }

    @Test fun badItems_areDropped() {
        val c = ServerCatalogParser.parse(
            listOf(
                item(null),                                  // thiếu hostname
                item("  "),                                  // hostname rỗng
                item("no-url", url = ""),                    // không url, không content
                item("no-url-null", url = null),
                item("content-only", url = "", content = "client\ndev tun\nremote 1.2.3.4 443"),
                item("no-country", country = ""),
                item("ok"),
            ),
            crawledAt = null,
            fromCache = false,
        )
        assertEquals(listOf("content-only", "ok"), c.servers.map { it.id }.sorted())
        assertEquals("client\ndev tun\nremote 1.2.3.4 443", c.servers.first { it.id == "content-only" }.ovpnContent)
        assertNull(c.crawledAt)
    }

    @Test fun dedupe_keepsHighestScore() {
        val c = ServerCatalogParser.parse(
            listOf(
                item("dup", score = "10", lineQuality = "Ping: 50 ms"),
                item("dup", score = 99L, lineQuality = "Ping: 5 ms"),
                item("dup", score = "20"),
            ),
            null, false,
        )
        assertEquals(1, c.servers.size)
        assertEquals(99L, c.servers[0].score)
        assertEquals(5, c.servers[0].pingMs)
    }

    @Test fun grouping_andSorting() {
        val c = ServerCatalogParser.parse(
            listOf(
                item("jp-low", country = "Japan", tier = 1L, score = "100", lineQuality = "Ping: 30 ms", flag = ""),
                item("jp-high", country = "Japan", tier = 1L, score = "900", lineQuality = null, flag = "https://flagcdn.com/w80/jp.png"),
                item("kr-1", country = "Korea", tier = 1L, score = "5000", lineQuality = "Ping: 12 ms"),
                item("th-1", country = "Thailand", tier = 2L, score = "99999", lineQuality = "weird"),
                item("vn-1", country = "Viet Nam", tier = "2", score = 50L, flag = null),
                item("us-1", country = "United States", tier = 1.0, score = "700"),
            ),
            null, false,
        )
        assertEquals(listOf("Korea", "Japan", "United States", "Thailand", "Viet Nam"), c.groups.map { it.country })
        val jp = c.groups.first { it.country == "Japan" }
        assertEquals(listOf("jp-high", "jp-low"), jp.servers.map { it.id })
        assertEquals(30, jp.bestPingMs)
        assertEquals(900L, jp.topScore)
        assertEquals("https://flagcdn.com/w80/jp.png", jp.flagUrl)
        assertNull(c.groups.first { it.country == "Thailand" }.bestPingMs)
        assertEquals("", c.groups.first { it.country == "Viet Nam" }.flagUrl)
        assertEquals(2, c.groups.first { it.country == "Viet Nam" }.tier)
        // servers phẳng theo thứ tự group
        assertEquals(listOf("kr-1", "jp-high", "jp-low", "us-1", "th-1", "vn-1"), c.servers.map { it.id })
    }

    @Test fun emptyItems_emptyCatalog() {
        val c = ServerCatalogParser.parse(emptyList(), "2026-09-04T09:59:03Z", false)
        assertTrue(c.groups.isEmpty())
        assertTrue(c.servers.isEmpty())
        assertFalse(c.fromCache)
        assertEquals(1_788_515_943_000L, c.crawledAt)
    }

    @Test fun normalizeItems_defensive() {
        assertTrue(ServerCatalogParser.normalizeItems(null).isEmpty())
        assertTrue(ServerCatalogParser.normalizeItems("x").isEmpty())
        val raw: Any = listOf(mapOf("hostname" to "a", 1 to "bad"), "str", null, 5L, mapOf("hostname" to "b"))
        val items = ServerCatalogParser.normalizeItems(raw)
        assertEquals(listOf(mapOf("hostname" to "a"), mapOf("hostname" to "b")), items)
    }

    @Test fun chunkCount_variants() {
        assertEquals(2, ServerCatalogParser.parseChunkCount(2L))
        assertEquals(3, ServerCatalogParser.parseChunkCount("3"))
        assertEquals(0, ServerCatalogParser.parseChunkCount(0L))
        assertEquals(0, ServerCatalogParser.parseChunkCount(-1L))
        assertEquals(0, ServerCatalogParser.parseChunkCount(null))
        assertEquals(0, ServerCatalogParser.parseChunkCount("x"))
    }

    @Test fun chunkedItems_mergedInOrder() {
        val chunk0 = ServerCatalogParser.normalizeItems(listOf(item("a", score = "3")))
        val chunk1 = ServerCatalogParser.normalizeItems(listOf(item("b", score = "2"), item("a", score = "1")))
        val c = ServerCatalogParser.parse(chunk0 + chunk1, null, false)
        assertEquals(listOf("a", "b"), c.servers.map { it.id })
        assertEquals(3L, c.servers[0].score)
    }

    @Test fun crawledAt_variants() {
        val p = ServerCatalogParser::parseCrawledAt
        assertEquals(1_788_515_943_353L, p("2026-09-04T09:59:03.353Z"))
        assertEquals(1_788_515_943_000L, p("2026-09-04T09:59:03Z"))
        assertEquals(1_788_515_943_300L, p("2026-09-04T09:59:03.3Z"))
        assertEquals(1_788_515_943_353L, p("2026-09-04T09:59:03.353123Z"))
        assertEquals(1_788_515_943_353L, p("2026-09-04T16:59:03.353+07:00"))
        assertEquals(1_788_515_943_353L, p("2026-09-04T04:59:03.353-0500"))
        assertEquals(1_788_515_943_000L, p("2026-09-04T09:59:03")) // không zone → UTC
        assertEquals(0L, p("1970-01-01T00:00:00Z"))
        assertNull(p(null))
        assertNull(p(""))
        assertNull(p("not a date"))
        assertNull(p("2026-13-04T09:59:03Z"))
        assertNull(p("2026-09-04 09:59:03Z"))
    }
}

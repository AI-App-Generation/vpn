package com.thanhmich.veil.vpn.corefeatures.vpn

import com.thanhmich.veil.vpn.data.api.ServerCatalogParser
import com.thanhmich.veil.vpn.data.domain.ServerCatalog
import com.thanhmich.veil.vpn.data.domain.ServerSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerPickerTest {

    private fun item(host: String, country: String, tier: Long, score: String) = mapOf<String, Any?>(
        "country" to country, "countryFlag" to "", "tier" to tier, "hostname" to host,
        "ipAddress" to "1.1.1.1", "openVpnOvpnUrl" to "https://x/$host.ovpn", "score" to score,
        "lineQuality" to "Ping: 10 ms",
    )

    // tier1: jp-a 500, jp-b 300, kr-a 400 ; tier2: th-a 9000, th-b 100, vn-a 800
    private val catalog: ServerCatalog = ServerCatalogParser.parse(
        listOf(
            item("jp-a", "Japan", 1, "500"),
            item("jp-b", "Japan", 1, "300"),
            item("kr-a", "Korea", 1, "400"),
            item("th-a", "Thailand", 2, "9000"),
            item("th-b", "Thailand", 2, "100"),
            item("vn-a", "Viet Nam", 2, "800"),
        ),
        null, false,
    )

    private val empty = ServerCatalogParser.parse(emptyList(), null, false)

    // ---- candidates ----

    @Test fun auto_tierThenScore() {
        val ids = ServerPicker.candidates(ServerSelection.Auto, catalog, emptySet()).map { it.id }
        assertEquals(listOf("jp-a", "kr-a", "jp-b", "th-a", "vn-a", "th-b"), ids)
    }

    @Test fun specific_selectedThenSameCountryThenScore() {
        val sel = ServerSelection.Specific("jp-b", "Japan")
        val ids = ServerPicker.candidates(sel, catalog, emptySet()).map { it.id }
        assertEquals(listOf("jp-b", "jp-a", "th-a", "vn-a", "kr-a", "th-b"), ids)
    }

    @Test fun specific_missingServer_sameCountryFirst() {
        val sel = ServerSelection.Specific("th-gone", "Thailand")
        val ids = ServerPicker.candidates(sel, catalog, emptySet()).map { it.id }
        assertEquals(listOf("th-a", "th-b", "vn-a", "jp-a", "kr-a", "jp-b"), ids)
    }

    @Test fun specific_countryGone_behavesLikeAuto() {
        val sel = ServerSelection.Specific("x", "Atlantis")
        val ids = ServerPicker.candidates(sel, catalog, emptySet()).map { it.id }
        assertEquals(listOf("jp-a", "kr-a", "jp-b", "th-a", "vn-a", "th-b"), ids)
        assertEquals(ServerPicker.resolveDisplay(sel, catalog)?.id, ids.first())
    }

    @Test fun excludesFailed() {
        val sel = ServerSelection.Specific("jp-b", "Japan")
        val ids = ServerPicker.candidates(sel, catalog, setOf("jp-b", "th-a")).map { it.id }
        assertEquals(listOf("jp-a", "vn-a", "kr-a", "th-b"), ids)
    }

    @Test fun cutsToMax() {
        assertEquals(listOf("jp-a", "kr-a"), ServerPicker.candidates(ServerSelection.Auto, catalog, emptySet(), max = 2).map { it.id })
        assertEquals(6, ServerPicker.candidates(ServerSelection.Auto, catalog, emptySet()).size)
        assertTrue(ServerPicker.candidates(ServerSelection.Auto, catalog, emptySet(), max = 0).isEmpty())
    }

    @Test fun defaultMax_isSix() {
        val many = ServerCatalogParser.parse((1..10).map { item("s$it", "C$it", 1, "$it") }, null, false)
        assertEquals(6, ServerPicker.candidates(ServerSelection.Auto, many, emptySet()).size)
    }

    @Test fun candidates_nullOrEmptyCatalog() {
        assertTrue(ServerPicker.candidates(ServerSelection.Auto, null, emptySet()).isEmpty())
        assertTrue(ServerPicker.candidates(ServerSelection.Auto, empty, emptySet()).isEmpty())
    }

    // ---- resolveDisplay ----

    @Test fun resolveDisplay_auto() {
        assertEquals("jp-a", ServerPicker.resolveDisplay(ServerSelection.Auto, catalog)?.id)
    }

    @Test fun resolveDisplay_specificPresent() {
        assertEquals("th-b", ServerPicker.resolveDisplay(ServerSelection.Specific("th-b", "Thailand"), catalog)?.id)
    }

    @Test fun resolveDisplay_specificGone_fallsBackLikeSanitize() {
        assertEquals("th-a", ServerPicker.resolveDisplay(ServerSelection.Specific("th-x", "Thailand"), catalog)?.id)
        assertEquals("jp-a", ServerPicker.resolveDisplay(ServerSelection.Specific("x", "Atlantis"), catalog)?.id)
    }

    @Test fun resolveDisplay_noCatalog() {
        assertNull(ServerPicker.resolveDisplay(ServerSelection.Auto, null))
        assertNull(ServerPicker.resolveDisplay(ServerSelection.Specific("jp-a", "Japan"), empty))
    }

    // ---- sanitizeSelection ----

    @Test fun sanitize_auto_unchanged() {
        assertEquals(ServerSelection.Auto, ServerPicker.sanitizeSelection(ServerSelection.Auto, catalog))
    }

    @Test fun sanitize_present_kept() {
        val sel = ServerSelection.Specific("jp-b", "Japan")
        assertEquals(sel, ServerPicker.sanitizeSelection(sel, catalog))
    }

    @Test fun sanitize_present_countryUpdated() {
        assertEquals(
            ServerSelection.Specific("jp-b", "Japan"),
            ServerPicker.sanitizeSelection(ServerSelection.Specific("jp-b", "Old Name"), catalog),
        )
    }

    @Test fun sanitize_serverGone_bestInCountry() {
        assertEquals(
            ServerSelection.Specific("jp-a", "Japan"),
            ServerPicker.sanitizeSelection(ServerSelection.Specific("jp-gone", "Japan"), catalog),
        )
    }

    @Test fun sanitize_countryGone_auto() {
        assertEquals(
            ServerSelection.Auto,
            ServerPicker.sanitizeSelection(ServerSelection.Specific("x", "Atlantis"), catalog),
        )
    }
}

package com.thanhmich.veil.vpn.corefeatures.vpn

import com.thanhmich.veil.vpn.data.domain.ServerCatalog
import com.thanhmich.veil.vpn.data.domain.ServerSelection
import com.thanhmich.veil.vpn.data.domain.VpnServer

/**
 * Hàm thuần: server hiển thị cho selection hiện tại và thứ tự server để thử khi connect.
 *
 * - Auto: mọi server sort `tier` asc → `score` desc.
 * - Specific(s): [s] + server cùng country (score desc) + phần còn lại (score desc).
 * - Loại `failedIds`, cắt `max`.
 */
object ServerPicker {

    const val DEFAULT_MAX_CANDIDATES = 6

    /** Server hiển thị ở S01. Specific đã mất → theo [sanitizeSelection]; catalog null/rỗng → null. */
    fun resolveDisplay(selection: ServerSelection, catalog: ServerCatalog?): VpnServer? {
        if (catalog == null || catalog.servers.isEmpty()) return null
        return when (val s = sanitizeSelection(selection, catalog)) {
            ServerSelection.Auto -> autoOrder(catalog.servers).firstOrNull()
            is ServerSelection.Specific -> catalog.servers.firstOrNull { it.id == s.serverId }
        }
    }

    /** Thứ tự thử khi connect (không chứa [failedIds], tối đa [max] phần tử). */
    fun candidates(
        selection: ServerSelection,
        catalog: ServerCatalog?,
        failedIds: Set<String>,
        max: Int = DEFAULT_MAX_CANDIDATES,
    ): List<VpnServer> {
        if (catalog == null || max <= 0) return emptyList()
        val all = catalog.servers
        // Sanitize trước để thứ tự thử khớp resolveDisplay (country mất → như Auto).
        val sel = if (all.isEmpty()) selection else sanitizeSelection(selection, catalog)
        val ordered = when (sel) {
            ServerSelection.Auto -> autoOrder(all)
            is ServerSelection.Specific -> {
                val chosen = all.firstOrNull { it.id == sel.serverId }
                val country = chosen?.country ?: sel.country
                val sameCountry = all
                    .filter { it.country == country && it.id != sel.serverId }
                    .sortedByDescending { it.score }
                val rest = all
                    .filter { it.country != country && it.id != sel.serverId }
                    .sortedByDescending { it.score }
                listOfNotNull(chosen) + sameCountry + rest
            }
        }
        return ordered.asSequence()
            .filter { it.id !in failedIds }
            .distinctBy { it.id }
            .take(max)
            .toList()
    }

    /**
     * Server còn trong catalog → giữ (cập nhật country nếu đổi); mất → server score cao nhất
     * cùng country; country cũng mất → Auto.
     */
    fun sanitizeSelection(selection: ServerSelection, catalog: ServerCatalog): ServerSelection {
        if (selection !is ServerSelection.Specific) return selection
        val present = catalog.servers.firstOrNull { it.id == selection.serverId }
        if (present != null) {
            return if (present.country == selection.country) selection
            else ServerSelection.Specific(present.id, present.country)
        }
        val best = catalog.servers
            .filter { it.country == selection.country }
            .maxByOrNull { it.score }
            ?: return ServerSelection.Auto
        return ServerSelection.Specific(best.id, best.country)
    }

    private fun autoOrder(servers: List<VpnServer>): List<VpnServer> =
        servers.sortedWith(compareBy<VpnServer> { it.tier }.thenByDescending { it.score })
}

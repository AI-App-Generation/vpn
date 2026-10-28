package com.thanhmich.veil.vpn.data.api

import com.thanhmich.veil.vpn.data.domain.CountryGroup
import com.thanhmich.veil.vpn.data.domain.ServerCatalog
import com.thanhmich.veil.vpn.data.domain.VpnServer
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Logic thuần (không phụ thuộc Android/Firebase) biến dữ liệu thô của document
 * `vpn_config/vpn_list` thành [ServerCatalog]. Parse phòng thủ từng field (R6):
 * Firestore có thể trả `tier`/`score` là Long, Double hoặc String.
 */
object ServerCatalogParser {

    const val DEFAULT_TIER = 2

    private val PING_REGEX = Regex("""(\d+)\s*ms""", RegexOption.IGNORE_CASE)

    // yyyy-MM-ddTHH:mm:ss[.fraction][Z|±hh:mm|±hhmm]
    private val ISO_REGEX = Regex(
        """^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d{1,9}))?(Z|[+-]\d{2}:?\d{2})?$""",
    )

    /** [items] + `crawledAt` dạng ISO-8601 (lỗi → null). */
    fun parse(items: List<Map<String, Any?>>, crawledAt: String?, fromCache: Boolean): ServerCatalog =
        buildCatalog(items, parseCrawledAt(crawledAt), fromCache)

    /** Như [parse] nhưng `crawledAt` đã là epoch millis (vd. field là Firestore Timestamp). */
    fun buildCatalog(items: List<Map<String, Any?>>, crawledAtMillis: Long?, fromCache: Boolean): ServerCatalog {
        val servers = dedupe(items.mapNotNull(::parseItem))
        val groups = groupByCountry(servers)
        return ServerCatalog(
            groups = groups,
            servers = groups.flatMap { it.servers },
            crawledAt = crawledAtMillis,
            fromCache = fromCache,
        )
    }

    /**
     * Chuẩn hoá giá trị thô của field `items` (từ Firestore là `List<Map<String, Any?>>`
     * nhưng không được đảm bảo kiểu) → chỉ giữ phần tử là Map có key String.
     */
    fun normalizeItems(raw: Any?): List<Map<String, Any?>> {
        if (raw !is List<*>) return emptyList()
        return raw.mapNotNull { element ->
            val map = element as? Map<*, *> ?: return@mapNotNull null
            buildMap {
                map.forEach { (k, v) -> if (k is String) put(k, v) }
            }
        }
    }

    /** `chunkCount` Long/Number/String → Int (> 0), còn lại → 0. */
    fun parseChunkCount(raw: Any?): Int {
        val n = when (raw) {
            is Number -> raw.toLong()
            is String -> raw.trim().toLongOrNull() ?: 0L
            else -> 0L
        }
        return if (n in 1..MAX_CHUNKS) n.toInt() else 0
    }

    /** 1 item → [VpnServer]; null nếu thiếu `hostname`, thiếu country, hoặc không có cả URL lẫn nội dung ovpn. */
    fun parseItem(item: Map<String, Any?>): VpnServer? {
        val hostname = str(item["hostname"])
        if (hostname.isEmpty()) return null
        val ovpnUrl = str(item["openVpnOvpnUrl"])
        val ovpnContent = str(item["openVpnOvpnContent"]).takeIf { it.isNotEmpty() }
        if (ovpnUrl.isEmpty() && ovpnContent == null) return null
        val country = str(item["country"])
        if (country.isEmpty()) return null
        return VpnServer(
            id = hostname,
            country = country,
            countryFlag = str(item["countryFlag"]),
            tier = parseTier(item["tier"]),
            hostname = hostname,
            ipAddress = str(item["ipAddress"]),
            ovpnUrl = ovpnUrl,
            score = parseScore(item["score"]),
            pingMs = parsePing(item["lineQuality"]),
            ovpnContent = ovpnContent,
        )
    }

    fun parseTier(raw: Any?): Int {
        val t = when (raw) {
            is Number -> raw.toDouble().takeIf { !it.isNaN() }?.toInt()
            is String -> raw.trim().toIntOrNull() ?: raw.trim().toDoubleOrNull()?.toInt()
            else -> null
        }
        return if (t != null && t > 0) t else DEFAULT_TIER
    }

    /** `score` là chuỗi theo spec; Long/Double cũng chấp nhận. Lỗi → 0. */
    fun parseScore(raw: Any?): Long = when (raw) {
        null -> 0L
        is Long, is Int, is Short, is Byte -> (raw as Number).toLong()
        is Number -> raw.toDouble().takeIf { !it.isNaN() && !it.isInfinite() }?.toLong() ?: 0L
        else -> raw.toString().trim().toLongOrNull() ?: 0L
    }

    fun parsePing(raw: Any?): Int? {
        val s = raw as? String ?: return null
        return PING_REGEX.find(s)?.groupValues?.get(1)?.toIntOrNull()
    }

    /** ISO-8601 UTC/offset, có hoặc không phần giây lẻ → epoch millis; lỗi → null. */
    fun parseCrawledAt(raw: String?): Long? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return null
        val m = ISO_REGEX.matchEntire(s) ?: return null
        val (base, fraction, zone) = m.destructured
        val millisPart = fraction.padEnd(3, '0').take(3)
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
            isLenient = false
        }
        val utcMillis = try {
            fmt.parse("$base.$millisPart")?.time ?: return null
        } catch (_: ParseException) {
            return null
        }
        return utcMillis - zoneOffsetMillis(zone)
    }

    private fun zoneOffsetMillis(zone: String): Long {
        if (zone.isEmpty() || zone == "Z") return 0L
        val sign = if (zone[0] == '-') -1 else 1
        val digits = zone.substring(1).replace(":", "")
        val h = digits.substring(0, 2).toLong()
        val min = digits.substring(2, 4).toLong()
        return sign * (h * 3_600_000L + min * 60_000L)
    }

    /** Dedupe theo hostname: giữ score cao nhất; bằng nhau giữ bản xuất hiện trước. Giữ thứ tự xuất hiện đầu. */
    private fun dedupe(servers: List<VpnServer>): List<VpnServer> {
        val byHost = LinkedHashMap<String, VpnServer>()
        for (s in servers) {
            val existing = byHost[s.hostname]
            if (existing == null || s.score > existing.score) byHost[s.hostname] = s
        }
        return byHost.values.toList()
    }

    private fun groupByCountry(servers: List<VpnServer>): List<CountryGroup> =
        servers.groupBy { it.country }
            .map { (country, list) ->
                val sorted = list.sortedByDescending { it.score } // stable
                CountryGroup(
                    country = country,
                    flagUrl = sorted.firstOrNull { it.countryFlag.isNotEmpty() }?.countryFlag.orEmpty(),
                    tier = sorted.minOf { it.tier },
                    servers = sorted,
                    bestPingMs = sorted.mapNotNull { it.pingMs }.minOrNull(),
                    topScore = sorted.first().score,
                )
            }
            .sortedWith(compareBy<CountryGroup> { it.tier }.thenByDescending { it.topScore })

    private fun str(raw: Any?): String = when (raw) {
        is String -> raw.trim()
        is Number, is Boolean -> raw.toString()
        else -> ""
    }

    private const val MAX_CHUNKS = 1000L
}

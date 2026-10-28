package com.thanhmich.veil.vpn.data.domain

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Json dùng chung cho mọi model persist. Bỏ qua field lạ để tương thích khi model đổi. */
val DomainJson: Json = Json {
    ignoreUnknownKeys = true
}

private val speedHistorySerializer = ListSerializer(SpeedResult.serializer())

fun encodeSpeedHistory(history: List<SpeedResult>): String =
    DomainJson.encodeToString(speedHistorySerializer, history)

/** null / JSON hỏng → danh sách rỗng. Kết quả luôn được cắt còn [SPEED_HISTORY_LIMIT]. */
fun decodeSpeedHistory(raw: String?): List<SpeedResult> {
    if (raw.isNullOrBlank()) return emptyList()
    return try {
        DomainJson.decodeFromString(speedHistorySerializer, raw).take(SPEED_HISTORY_LIMIT)
    } catch (e: IllegalArgumentException) { // gồm SerializationException
        emptyList()
    }
}

fun encodeActiveSession(session: ActiveSession): String =
    DomainJson.encodeToString(ActiveSession.serializer(), session)

/** null / JSON hỏng → null. */
fun decodeActiveSession(raw: String?): ActiveSession? {
    if (raw.isNullOrBlank()) return null
    return try {
        DomainJson.decodeFromString(ActiveSession.serializer(), raw)
    } catch (e: IllegalArgumentException) { // gồm SerializationException
        null
    }
}

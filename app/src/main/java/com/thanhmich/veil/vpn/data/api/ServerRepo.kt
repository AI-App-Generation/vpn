package com.thanhmich.veil.vpn.data.api

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.thanhmich.veil.vpn.core.SettingManager
import com.thanhmich.veil.vpn.corefeatures.vpn.ServerPicker
import com.thanhmich.veil.vpn.data.domain.CatalogError
import com.thanhmich.veil.vpn.data.domain.CatalogState
import com.thanhmich.veil.vpn.data.domain.ServerCatalog
import com.thanhmich.veil.vpn.data.domain.VpnServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.Date

/**
 * M02 — đọc `vpn_config/vpn_list` từ Firestore (offline persistence mặc định làm cache),
 * parse bằng [ServerCatalogParser] và phát [CatalogState].
 *
 * Singleton: khởi tạo 1 lần trong `AppGraph.init` (main process, main thread) rồi gọi [start].
 * Mọi thay đổi state chạy trên main thread.
 */
class ServerRepo(
    private val firestoreProvider: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() },
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<CatalogState>(CatalogState.Loading)
    val state: StateFlow<CatalogState> = _state.asStateFlow()

    private var refreshJob: Job? = null
    private var lastCatalog: ServerCatalog? = null
    private var lastSuccessAt: Long? = null // elapsedRealtime
    private var started = false

    /** Gọi 1 lần từ main thread: refresh đầu, auto refresh ON_START, đồng bộ selection (R7). */
    fun start() {
        if (started) return
        started = true
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = refreshIfStale()
        })
        observeSelectionSanitize()
        refresh()
    }

    /** Bỏ qua nếu đang refresh. Có catalog cũ → phát `Ready(cũ, refreshing = true)`; chưa có → `Loading`. */
    fun refresh() {
        scope.launch {
            if (refreshJob?.isActive == true) return@launch
            val cached = lastCatalog
            _state.value = if (cached != null) CatalogState.Ready(cached, refreshing = true) else CatalogState.Loading
            refreshJob = scope.launch { doRefresh() }
        }
    }

    /** Tìm server theo id (= hostname) trong catalog gần nhất (kể cả khi đang Failed có cache). */
    fun findServer(id: String): VpnServer? = currentCatalog()?.servers?.firstOrNull { it.id == id }

    private fun currentCatalog(): ServerCatalog? = when (val s = _state.value) {
        is CatalogState.Ready -> s.catalog
        is CatalogState.Failed -> s.cached
        CatalogState.Loading -> lastCatalog
    }

    private fun refreshIfStale() {
        val last = lastSuccessAt
        if (last == null || SystemClock.elapsedRealtime() - last > STALE_AFTER_MS) refresh()
    }

    private suspend fun doRefresh() {
        val newState = try {
            val catalog = withTimeout(TIMEOUT_MS) { fetchCatalog() }
            lastCatalog = catalog
            lastSuccessAt = SystemClock.elapsedRealtime()
            Log.i(TAG, "Catalog: ${catalog.servers.size} servers / ${catalog.groups.size} countries, fromCache=${catalog.fromCache}")
            CatalogState.Ready(catalog, refreshing = false)
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Firestore read timed out after ${TIMEOUT_MS}ms")
            CatalogState.Failed(CatalogError.Network, lastCatalog)
        } catch (e: CancellationException) {
            // Coroutine của repo bị huỷ → ném lại; còn lại (vd. Task Firestore bị cancel) → Failed, không kẹt state.
            currentCoroutineContext().ensureActive()
            Log.w(TAG, "Firestore task cancelled", e)
            CatalogState.Failed(CatalogError.Unknown, lastCatalog)
        } catch (e: FirebaseFirestoreException) {
            CatalogState.Failed(mapError(e), lastCatalog)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error reading catalog", e)
            CatalogState.Failed(CatalogError.Unknown, lastCatalog)
        }
        _state.value = newState
    }

    private fun mapError(e: FirebaseFirestoreException): CatalogError = when (e.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED,
        FirebaseFirestoreException.Code.UNAUTHENTICATED -> {
            Log.e(
                TAG,
                "PERMISSION_DENIED khi đọc $COLLECTION/$DOC_ID — kiểm tra Firestore rules " +
                    "(match /$COLLECTION/{doc} { allow read: if true; }), Cloud Firestore API đã bật " +
                    "và google-services.json đúng project.",
                e,
            )
            CatalogError.Permission
        }
        FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> {
            Log.w(TAG, "Firestore unavailable: ${e.message}")
            CatalogError.Network
        }
        else -> {
            Log.e(TAG, "Firestore error ${e.code}", e)
            CatalogError.Unknown
        }
    }

    private suspend fun fetchCatalog(): ServerCatalog {
        val col = firestoreProvider().collection(COLLECTION)
        val snap = col.document(DOC_ID).get(Source.DEFAULT).await()
        if (!snap.exists()) {
            Log.w(TAG, "$COLLECTION/$DOC_ID không tồn tại → catalog rỗng")
            return ServerCatalogParser.buildCatalog(emptyList(), null, snap.metadata.isFromCache)
        }
        val (mainItems, chunkCount) = withContext(Dispatchers.Default) {
            val items = ServerCatalogParser.normalizeItems(snap.get(FIELD_ITEMS))
            val count = if (items.isEmpty()) ServerCatalogParser.parseChunkCount(snap.get(FIELD_CHUNK_COUNT)) else 0
            items to count
        }
        var fromCache = snap.metadata.isFromCache
        var chunks: List<DocumentSnapshot> = emptyList()
        if (chunkCount > 0) {
            chunks = coroutineScope {
                (0 until chunkCount).map { i ->
                    async { col.document("${DOC_ID}_$i").get(Source.DEFAULT).await() }
                }.awaitAll() // giữ đúng thứ tự 0..n-1
            }
            // Thiếu chunk → lỗi (không phát catalog thiếu, tránh sanitize đổi selection của user).
            chunks.firstOrNull { !it.exists() }?.let { throw MissingChunkException(it.id) }
            fromCache = fromCache || chunks.any { it.metadata.isFromCache }
        }
        return withContext(Dispatchers.Default) {
            val items = if (chunkCount > 0) {
                chunks.flatMap { ServerCatalogParser.normalizeItems(it.get(FIELD_ITEMS)) }
            } else {
                mainItems
            }
            ServerCatalogParser.buildCatalog(items, crawledAtMillis(snap), fromCache)
        }
    }

    private class MissingChunkException(id: String) : Exception("Thiếu chunk $COLLECTION/$id")

    private fun crawledAtMillis(snap: DocumentSnapshot): Long? = when (val raw = snap.get(FIELD_CRAWLED_AT)) {
        is String -> ServerCatalogParser.parseCrawledAt(raw)
        is Timestamp -> raw.toDate().time
        is Date -> raw.time
        is Number -> raw.toLong()
        else -> null
    }

    /**
     * R7: mỗi lần catalog Ready đổi, sanitize selection đã lưu và ghi lại nếu khác.
     * Catalog rỗng không sanitize (tránh xoá lựa chọn của user vì 1 lần đọc rỗng tạm thời).
     */
    private fun observeSelectionSanitize() {
        scope.launch {
            SettingManager.isLoaded.first { it }
            var lastSanitized: ServerCatalog? = null
            state.collect { s ->
                val catalog = (s as? CatalogState.Ready)?.catalog ?: return@collect
                if (catalog === lastSanitized || catalog.servers.isEmpty()) return@collect
                lastSanitized = catalog
                val current = SettingManager.selection.value
                val sanitized = ServerPicker.sanitizeSelection(current, catalog)
                if (sanitized != current) {
                    Log.i(TAG, "Selection $current không còn hợp lệ → $sanitized")
                    SettingManager.setSelection(sanitized)
                }
            }
        }
    }

    private companion object {
        const val TAG = "ServerRepo"
        const val COLLECTION = "vpn_config"
        const val DOC_ID = "vpn_list"
        const val FIELD_ITEMS = "items"
        const val FIELD_CHUNK_COUNT = "chunkCount"
        const val FIELD_CRAWLED_AT = "crawledAt"
        const val TIMEOUT_MS = 15_000L
        const val STALE_AFTER_MS = 10 * 60 * 1000L
    }
}

package com.thanhmich.veil.vpn.core

import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.thanhmich.veil.vpn.data.domain.AccentTheme
import com.thanhmich.veil.vpn.data.domain.ActiveSession
import com.thanhmich.veil.vpn.data.domain.ServerSelection
import com.thanhmich.veil.vpn.data.domain.SpeedResult
import com.thanhmich.veil.vpn.data.domain.decodeActiveSession
import com.thanhmich.veil.vpn.data.domain.decodeSpeedHistory
import com.thanhmich.veil.vpn.data.domain.encodeActiveSession
import com.thanhmich.veil.vpn.data.domain.encodeSpeedHistory
import com.thanhmich.veil.vpn.data.domain.prependSpeedResult
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale

private const val TAG = "SettingManager"

private val Context.localDataStore by preferencesDataStore(
    name = "local_data",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

object SettingManager {

    private lateinit var dataStore: DataStore<Preferences>

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO +
            CoroutineExceptionHandler { _, e -> Log.e(TAG, "DataStore write failed", e) }
    )

    // For app
    private val IS_FIRST_TIME = booleanPreferencesKey("isFirstTime")
    private val APP_LOCALE = stringPreferencesKey("appLocale")

    // For business
    private val IS_PURCHASED = booleanPreferencesKey("isPurchased")
    private val ONBOARDING_FLOW_DONE = booleanPreferencesKey("onBoardingFlowDone")
    private val CHECK_CONSENT_DONE = booleanPreferencesKey("checkConsentDone")

    // For VPN app
    private val ACCENT = stringPreferencesKey("accent")
    private val MOTION_ENABLED = booleanPreferencesKey("motionEnabled")
    private val SELECTION = stringPreferencesKey("selection")
    private val COMMUNITY_NOTICE_SEEN = booleanPreferencesKey("communityNoticeSeen")
    private val NOTIFICATION_ASKED = booleanPreferencesKey("notificationAsked")
    private val ACTIVE_SESSION = stringPreferencesKey("activeSession")
    private val SPEED_HISTORY = stringPreferencesKey("speedHistory")

    /**
     * true sau khi init() nạp xong prefs (kể cả khi đọc lỗi → dùng mặc định).
     * Consumer cần giá trị thật thì chờ `isLoaded.first { it }`.
     */
    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    /**
     * Setter gọi trước khi nạp xong được xếp hàng ở đây (đúng thứ tự gọi) và chạy lại
     * ngay sau khi nạp, để giá trị trên disk không đè lên lệnh của user.
     */
    private val pendingLock = Any()
    private val pendingOps = ArrayList<() -> Unit>()

    private val _isFirstTime = MutableStateFlow(true)
    val isFirstTime: StateFlow<Boolean> = _isFirstTime.asStateFlow()

    private val _appLocale = MutableStateFlow("")
    val appLocale = _appLocale.asStateFlow()


    private val _isPurchased = MutableStateFlow(false)
    val isPurchased: StateFlow<Boolean> = _isPurchased.asStateFlow()

    private val _onBoardingFlowDone = MutableStateFlow(false)
    val onBoardingFlowDone: StateFlow<Boolean> = _onBoardingFlowDone.asStateFlow()

    private val _checkConsentDone = MutableStateFlow(false)
    val checkConsentDone: StateFlow<Boolean> = _checkConsentDone.asStateFlow()

    private val _accent = MutableStateFlow(AccentTheme.Default)
    val accent: StateFlow<AccentTheme> = _accent.asStateFlow()

    private val _motionEnabled = MutableStateFlow(true)
    val motionEnabled: StateFlow<Boolean> = _motionEnabled.asStateFlow()

    private val _selection = MutableStateFlow<ServerSelection>(ServerSelection.Auto)
    val selection: StateFlow<ServerSelection> = _selection.asStateFlow()

    private val _communityNoticeSeen = MutableStateFlow(false)
    val communityNoticeSeen: StateFlow<Boolean> = _communityNoticeSeen.asStateFlow()

    /** Đã xin POST_NOTIFICATIONS (Android 13+) 1 lần trước lần connect đầu. */
    private val _notificationAsked = MutableStateFlow(false)
    val notificationAsked: StateFlow<Boolean> = _notificationAsked.asStateFlow()

    private val _activeSession = MutableStateFlow<ActiveSession?>(null)
    val activeSession: StateFlow<ActiveSession?> = _activeSession.asStateFlow()

    /** Mới nhất đầu, tối đa SPEED_HISTORY_LIMIT (10). */
    private val _speedHistory = MutableStateFlow<List<SpeedResult>>(emptyList())
    val speedHistory: StateFlow<List<SpeedResult>> = _speedHistory.asStateFlow()

    fun init(context: Context) {
        val appContext = context.applicationContext
        dataStore = appContext.localDataStore

        scope.launch {
            val prefs = try {
                dataStore.data.first()
            } catch (e: IOException) {
                Log.e(TAG, "Read prefs failed, using defaults", e)
                emptyPreferences()
            }

            // Mặc định trước; mọi lỗi đọc từng key (vd. sai kiểu → ClassCastException) chỉ log.
            _appLocale.value = Locale.getDefault().language
            try {
                readPrefs(prefs)
            } catch (e: Exception) {
                Log.e(TAG, "Parse prefs failed, keeping defaults", e)
            }
            val savedLocale = _appLocale.value
            withContext(Dispatchers.Main) {
                try {
                    applyLocale(savedLocale)
                } catch (e: Exception) {
                    Log.e(TAG, "Apply locale failed", e)
                }
                // Đánh dấu loaded + chạy các setter đã xếp hàng (kể cả setAppLocale mới hơn,
                // áp sau locale đã lưu) trong cùng 1 khoá: lệnh gọi sau từ thread khác phải chờ
                // hàng đợi chạy xong → giữ đúng thứ tự gọi. Chạy trên Main vì setAppLocale gọi AppCompatDelegate.
                synchronized(pendingLock) {
                    _isLoaded.value = true
                    val ops = pendingOps.toList()
                    pendingOps.clear()
                    ops.forEach { op ->
                        try {
                            op()
                        } catch (e: Exception) {
                            Log.e(TAG, "Deferred setter failed", e)
                        }
                    }
                }
            }

            if (_isFirstTime.value) dataStore.edit { it[IS_FIRST_TIME] = false }

            Log.i(TAG, "isFirstTime=${_isFirstTime.value}, locale=${_appLocale.value}")
        }
    }

    private fun readPrefs(prefs: Preferences) {
        _isFirstTime.value = prefs[IS_FIRST_TIME] ?: true
        prefs[APP_LOCALE]?.let { _appLocale.value = it }

        _isPurchased.value = prefs[IS_PURCHASED] ?: false
        _onBoardingFlowDone.value = prefs[ONBOARDING_FLOW_DONE] ?: false
        _checkConsentDone.value = prefs[CHECK_CONSENT_DONE] ?: false

        _accent.value = AccentTheme.fromName(prefs[ACCENT])
        _motionEnabled.value = prefs[MOTION_ENABLED] ?: true
        _selection.value = ServerSelection.decode(prefs[SELECTION])
        _communityNoticeSeen.value = prefs[COMMUNITY_NOTICE_SEEN] ?: false
        _notificationAsked.value = prefs[NOTIFICATION_ASKED] ?: false
        _activeSession.value = decodeActiveSession(prefs[ACTIVE_SESSION]) // JSON hỏng → null
        _speedHistory.value = decodeSpeedHistory(prefs[SPEED_HISTORY])    // JSON hỏng → rỗng
    }

    /** Chưa nạp xong → xếp [op] vào hàng đợi và trả true (setter return ngay). */
    private fun deferUntilLoaded(op: () -> Unit): Boolean {
        synchronized(pendingLock) {
            if (_isLoaded.value) return false
            pendingOps += op
            return true
        }
    }

    private fun applyLocale(localeId: String) {
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(localeId)
        )
    }
    fun setAppLocale(localeId: String) {
        if (deferUntilLoaded { setAppLocale(localeId) }) return
        val changed = _appLocale.value != localeId
        _appLocale.value = localeId
        applyLocale(localeId)

        if (changed) {
            scope.launch {
                dataStore.edit {
                    it[APP_LOCALE] = _appLocale.value
                }
            }
        }
    }

    fun setPurchased(purchased: Boolean) {
        if (deferUntilLoaded { setPurchased(purchased) }) return
        if (!purchased || _isPurchased.value) return

        _isPurchased.value = true

        scope.launch {
            dataStore.edit {
                it[IS_PURCHASED] = true
            }
        }
    }

    fun setOnBoardingFlowDone() {
        if (deferUntilLoaded { setOnBoardingFlowDone() }) return
        if (_onBoardingFlowDone.value) return

        _onBoardingFlowDone.value = true

        scope.launch {
            dataStore.edit {
                it[ONBOARDING_FLOW_DONE] = true
            }
        }
    }

    fun setCheckConsentDone() {
        if (deferUntilLoaded { setCheckConsentDone() }) return
        if (_checkConsentDone.value) return

        _checkConsentDone.value = true

        scope.launch {
            dataStore.edit {
                it[CHECK_CONSENT_DONE] = true
            }
        }
    }

    fun setAccent(theme: AccentTheme) {
        if (deferUntilLoaded { setAccent(theme) }) return
        if (_accent.value == theme) return
        _accent.value = theme
        scope.launch {
            dataStore.edit { it[ACCENT] = _accent.value.name }
        }
    }

    fun setMotionEnabled(enabled: Boolean) {
        if (deferUntilLoaded { setMotionEnabled(enabled) }) return
        if (_motionEnabled.value == enabled) return
        _motionEnabled.value = enabled
        scope.launch {
            dataStore.edit { it[MOTION_ENABLED] = _motionEnabled.value }
        }
    }

    fun setSelection(selection: ServerSelection) {
        if (deferUntilLoaded { setSelection(selection) }) return
        if (_selection.value == selection) return
        _selection.value = selection
        scope.launch {
            dataStore.edit { it[SELECTION] = _selection.value.encode() }
        }
    }

    fun setCommunityNoticeSeen() {
        if (deferUntilLoaded { setCommunityNoticeSeen() }) return
        if (_communityNoticeSeen.value) return
        _communityNoticeSeen.value = true
        scope.launch {
            dataStore.edit { it[COMMUNITY_NOTICE_SEEN] = true }
        }
    }

    fun setNotificationAsked() {
        if (deferUntilLoaded { setNotificationAsked() }) return
        if (_notificationAsked.value) return
        _notificationAsked.value = true
        scope.launch {
            dataStore.edit { it[NOTIFICATION_ASKED] = true }
        }
    }

    /** null → xoá phiên. */
    fun setActiveSession(session: ActiveSession?) {
        if (deferUntilLoaded { setActiveSession(session) }) return
        if (_activeSession.value == session) return
        _activeSession.value = session
        scope.launch {
            dataStore.edit { prefs ->
                // Ghi giá trị hiện tại lúc edit để các lần gọi dồn dập không ghi đè sai thứ tự.
                val current = _activeSession.value
                if (current == null) prefs.remove(ACTIVE_SESSION)
                else prefs[ACTIVE_SESSION] = encodeActiveSession(current)
            }
        }
    }

    /** Thêm vào đầu lịch sử, giữ 10 kết quả mới nhất. */
    fun addSpeedResult(result: SpeedResult) {
        if (deferUntilLoaded { addSpeedResult(result) }) return
        _speedHistory.update { it.prependSpeedResult(result) }
        scope.launch {
            dataStore.edit { it[SPEED_HISTORY] = encodeSpeedHistory(_speedHistory.value) }
        }
    }
}

package com.thanhmich.veil.vpn.ads

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.bbl.ads.modules.AdsManager
import com.bbl.ads.modules.billing.AppPurchase
import com.bbl.ads.modules.configs.RemoteConfigManager
import com.thanhmich.veil.vpn.core.SettingManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object AdsLoader {
    private const val REMOTE_CONFIG_TIMEOUT_MS = 5_000L
    private const val ADS_READY_TIMEOUT_MS = 5_000L

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var started = false

    private val _finished = MutableStateFlow(false)
    val finished = _finished.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    val isActive = _isActive.asStateFlow()

    fun setActive(isPurchased: Boolean) {
        _isActive.value = !isPurchased
    }

    fun isAdsEnabled(): Boolean = _isActive.value

    private fun isPurchased(): Boolean = AppPurchase.getInstance().isPurchased || SettingManager.isPurchased.value

    fun initEarly(context: Context) {
        val appContext = context.applicationContext

        if (started || _finished.value) return
        started = true

        waitForRemoteConfig(appContext)
    }

    private fun waitForRemoteConfig(context: Context) {
        var remoteReady = false

        RemoteConfigManager.getInstance(context)
            .addOnConfigReadyListener {
                if (remoteReady || _finished.value) return@addOnConfigReadyListener
                remoteReady = true
                onRemoteConfigReady(context)
            }

        mainHandler.postDelayed({
            if (remoteReady || _finished.value) return@postDelayed
            remoteReady = true
            onRemoteConfigReady(context)
        }, REMOTE_CONFIG_TIMEOUT_MS)
    }

    private fun onRemoteConfigReady(context: Context) {
        AdModeManager.syncFromRemoteConfig(context)
        if (isPurchased()) {
            setActive(true)
            _finished.value = true
            return
        }
        setActive(false)
        waitForAdsManager()
    }

    private fun waitForAdsManager() {

        if (_finished.value) return

        if (AdsManager.getInstance().isReady) {
            _finished.value = true
            return
        }

        AdsManager.getInstance().addOnReadyListener {
            if (_finished.value) return@addOnReadyListener
            _finished.value = true
            setActive(isPurchased())
        }

        mainHandler.postDelayed({
            if (_finished.value) return@postDelayed
            _finished.value = true
            setActive(isPurchased())
        }, ADS_READY_TIMEOUT_MS)
    }
}

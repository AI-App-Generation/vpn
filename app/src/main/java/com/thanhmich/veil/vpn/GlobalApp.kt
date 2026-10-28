package com.thanhmich.veil.vpn

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.bbl.ads.modules.AdsManager
import com.bbl.ads.modules.application.AdsMultiDexApplication
import com.bbl.ads.modules.billing.AppPurchase
import com.bbl.ads.modules.configs.GlobalConfig
import com.thanhmich.veil.vpn.ads.AdsLoader
import com.thanhmich.veil.vpn.ads.AdsPolicy
import com.thanhmich.veil.vpn.core.AppGraph
import com.thanhmich.veil.vpn.core.SettingManager
import com.thanhmich.veil.vpn.features.iap.Iap
import de.blinkt.openvpn.api.AppRestrictions
import de.blinkt.openvpn.core.OpenVPNService
import de.blinkt.openvpn.core.StatusListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class GlobalApp : AdsMultiDexApplication() {

    private val _shouldShowWelcomeBack = MutableStateFlow(false)
    val shouldShowWelcomeBack = _shouldShowWelcomeBack.asStateFlow()

    override fun createDefaultGlobalConfig(): GlobalConfig {
        return GlobalConfig().apply {
            appId = BuildConfig.ADMOB_APP_ID
            isConsentRequiredBeforeInit = false
            isEnableRemoteConfig = true
            adLoggingType = "realtime_database"
            isVariantDev = BuildConfig.DEBUG
            loadStrategy = GlobalConfig.LOAD_STRATEGY_WATERFALL
            adjustToken = ""
            appsflyerToken = ""
            facebookClientToken = ""
            testDevices = arrayOf("727854F084C6F0CDC793A5A192B72FCC", "8D9B9371B8704790A1C041FAF7C14071", "B7DE3A60025A6EBCAC171F608A5EB87B", "EB52EFE4C726897953885CB827CA2EFC")
        }
    }

    // Process phụ (":openvpn" của ics-openvpn) return TRƯỚC super.onCreate(): AdsMultiDexApplication.onCreate
    // không kiểm process (init Ads/tracking); Application/MultiDexApplication.onCreate rỗng, MultiDex cài ở
    // attachBaseContext. isMainProcess() chỉ dùng API framework, không phụ thuộc gì do super khởi tạo.
    @SuppressLint("MissingSuperCall")
    override fun onCreate() {
        // OpenVPNService lives in :openvpn, where all static library state starts empty.
        // This call initializes GlobalPreferences (and managed restrictions when present)
        // in every process before the service builds its first notification.
        AppRestrictions.getInstance(this).checkRestrictions(this)
        if (!isMainProcess()) return
        super.onCreate()

        SettingManager.init(this)
        initVpnLibrary()
        AppGraph.init(this)

        AdsManager.getInstance().apply {
            disableAppResumeWithActivity(com.google.android.libraries.ads.mobile.sdk.common.AdActivity::class.java)
            disableAppResumeWithActivity(MainActivity::class.java)
        }

        AdsLoader.initEarly(this)

        observeAppBackground()
        initBilling()
    }

    /**
     * ics-openvpn (M05): kênh notification + nhận status từ process `:openvpn` (R3).
     * Phải chạy trước `AppGraph.init` (engine đăng ký VpnStatus listener).
     */
    private fun initVpnLibrary() {
        OpenVPNService.createNotificationChannels(this)
        StatusListener().init(this)
    }

    private fun isMainProcess(): Boolean {
        val processName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val pid = Process.myPid()
            am?.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName
        }
        // Không xác định được tên process → coi là main để không bỏ sót khởi tạo.
        return processName == null || processName == packageName
    }

    private fun observeAppBackground() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                if (!AdsPolicy.isShowingInterAd && AdsLoader.isAdsEnabled()) {
                    _shouldShowWelcomeBack.value = true
                }
            }
        })
    }

    fun consumeWelcomeBack() {
        _shouldShowWelcomeBack.value = false
    }

    private fun initBilling() {
        val listIAPId: ArrayList<String> = ArrayList()
        val listSubsId: ArrayList<String> = ArrayList()
        listSubsId.add(Iap.weeklyId)
        listSubsId.add(Iap.monthlyId)
        listIAPId.add(Iap.lifetimeId)
        AppPurchase.getInstance().initBilling(this, listIAPId, listSubsId, this.globalConfig)
    }
}

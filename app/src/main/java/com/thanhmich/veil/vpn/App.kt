package com.thanhmich.veil.vpn

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.bbl.ads.modules.AdsManager
import com.bbl.ads.modules.consent.ConsentCallback
import com.thanhmich.veil.vpn.core.SettingManager
import com.thanhmich.veil.vpn.features.onboarding.SplashScreen
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavBackStackEntry
import com.thanhmich.veil.vpn.ads.AdsPolicy
import com.thanhmich.veil.vpn.features.language.LanguageScreen
import com.thanhmich.veil.vpn.features.onboarding.OnboardingScreen
import com.thanhmich.veil.vpn.features.main.MainContent
import com.thanhmich.veil.vpn.features.select.SelectScreen

private const val ENTER_TIME = 400
private const val EXIT_TIME = 300

/*
Đây là bộ hiệu ứng do tôi soạn, bạn nên lựa chọn và tái sử dụng chúng một cách phù hợp khi kết hợp với NavHost hoặc AnimatedContent
Đối với các app có NavBar, transition cơ bản giữa các màn cùng level trên thanh đó thì dùng fade với 150ms
*/

val ZoomTransition = Pair(
    fadeIn(animationSpec = tween(ENTER_TIME)) + scaleIn(initialScale = 1.3f, animationSpec = tween(ENTER_TIME)),
    fadeOut(animationSpec = tween(EXIT_TIME)) + scaleOut(targetScale = 1.3f, animationSpec = tween(EXIT_TIME)),
)

val SlideHorizontalTransition = Pair(
    slideInHorizontally(initialOffsetX = { it }),
    slideOutHorizontally(targetOffsetX = { it }),
)

val FadeSlideHorizontal = Pair(
    fadeIn(animationSpec = tween(ENTER_TIME / 2)) + slideInHorizontally(initialOffsetX = { it / 3 }),
    fadeOut(animationSpec = tween(EXIT_TIME / 2)),
)

val FadeSlideUp = Pair(
    fadeIn(animationSpec = tween(ENTER_TIME / 2)) + slideInVertically(initialOffsetY = { it / 4 }),
    fadeOut(animationSpec = tween(EXIT_TIME / 2)),
)

val FlowTransition = Pair(
    fadeIn(animationSpec = tween(ENTER_TIME / 2)) + slideInHorizontally(
        initialOffsetX = { it / 3 },
        animationSpec = tween(ENTER_TIME)
    ),
    slideOutHorizontally(
        targetOffsetX = { -it / 3 },
        animationSpec = tween(ENTER_TIME * 2)
    ),
)

// Bắt buộc dùng trong màn Home thật sự
@Composable
fun CheckForConsent() {
    val activity = LocalActivity.current

    LaunchedEffect(Unit) {
        if (SettingManager.checkConsentDone.value) return@LaunchedEffect

        AdsManager.getInstance().runConsentFlow(activity, object : ConsentCallback {
            override fun onConsentComplete(canPersonalized: Boolean) {
                AdsManager.getInstance().logCustomEvent("consent_success", mutableMapOf<String, Any?>("result" to canPersonalized))
                AdsManager.getInstance().reloadConsent()
                SettingManager.setCheckConsentDone()
            }

            override fun onConsentError(message: String?) {
                AdsManager.getInstance().logCustomEvent("consent_error", mutableMapOf<String, Any?>("message" to (message ?: "")))
                AdsManager.getInstance().reloadConsent()
                SettingManager.setCheckConsentDone()
            }
        })
    }
}

@Composable
fun LogScreenView(screenName: String) {
    val context = LocalContext.current
    LaunchedEffect(screenName) {
        if (BuildConfig.DEBUG) return@LaunchedEffect
        FirebaseAnalytics.getInstance(context).logEvent(screenName, null)
    }
}

// Dùng ở màn nào được tôi yêu cầu vô hiệu hóa show Welcome back screen
@Composable
fun DisableWelcomeOverlayTemporarily() {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                AdsPolicy.allowShowWelcomeBack = false
            }
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                AdsPolicy.allowShowWelcomeBack = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            AdsPolicy.allowShowWelcomeBack = true
        }
    }
}



// Đây là flow chuẩn và cố định của App, bạn bắt đầu cần xây dựng từ MainContent() và thay thế vào flow bên dưới
private enum class AppScreen { Splash, Language, Select, Onboarding, Main }

@Composable
fun App() {
    var screenOrdinal by rememberSaveable { mutableStateOf(AppScreen.Splash.ordinal) }
    val screen = AppScreen.entries[screenOrdinal]
    fun goTo(s: AppScreen) { screenOrdinal = s.ordinal }

    AnimatedContent(
        targetState = screen,
        transitionSpec = { FlowTransition.first togetherWith FlowTransition.second },
        label = "app_flow"
    ) { target ->
        when (target) {
            AppScreen.Splash -> SplashScreen(
                onNext = { goTo(AppScreen.Language) }
            )
            AppScreen.Language -> LanguageScreen(
                onNext = { goTo(AppScreen.Select) }
            )
            AppScreen.Select -> SelectScreen(
                onNext = { goTo(AppScreen.Onboarding) }
            )
            AppScreen.Onboarding -> OnboardingScreen(
                onNext = {
                    goTo(AppScreen.Main)
                }
            )
            AppScreen.Main -> {
                MainContent()
            }
        }
    }
}



fun NavBackStackEntry.lifecycleIsResumed(): Boolean = this.lifecycle.currentState == Lifecycle.State.RESUMED

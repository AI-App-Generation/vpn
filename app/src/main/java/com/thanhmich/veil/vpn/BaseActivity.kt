package com.thanhmich.veil.vpn

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.bbl.ads.modules.AdsManager
import com.thanhmich.veil.vpn.ads.AdsLoader
import com.thanhmich.veil.vpn.ads.AdsPolicy
import com.thanhmich.veil.vpn.features.welcomeback.WelcomeBackScreen
import com.thanhmich.veil.vpn.core.SettingManager
import com.thanhmich.veil.vpn.ui.theme.VeilTheme
import kotlinx.coroutines.flow.MutableStateFlow

abstract class BaseActivity : AppCompatActivity() {
    protected val app by lazy { application as GlobalApp }

    private val _showWelcomeBack = MutableStateFlow(false)

    @Composable
    protected fun AppWrapperWithWelcomeBack(content: @Composable () -> Unit) {
        val showWelcomeBack by _showWelcomeBack.collectAsState()
        val isWaitingInterAd by AdsPolicy.isWaitingInterAd.collectAsState()

        val accent by SettingManager.accent.collectAsState()
        val motionEnabled by SettingManager.motionEnabled.collectAsState()

        VeilTheme(accent = accent, motionEnabled = motionEnabled) {
            Box(Modifier.fillMaxSize()) {
                content()

                InterAdLoadingOverlay(isWaitingInterAd)

                AnimatedVisibility(
                    visible = showWelcomeBack,
                    enter = EnterTransition.None,
                    exit = fadeOut(animationSpec = tween(150)),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    WelcomeBackScreen(
                        onDismiss = { _showWelcomeBack.value = false },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AdsManager.getInstance().currentActivity = this
        window.decorView.post { maybeShowWelcomeBack() }
    }

    private fun maybeShowWelcomeBack() {
        if (!app.shouldShowWelcomeBack.value) return
        val show = AdsLoader.isAdsEnabled() && AdsPolicy.allowShowWelcomeBack
        app.consumeWelcomeBack()
        if (show) _showWelcomeBack.value = true
    }
}

private val LOADING_ASSETS = listOf(
    "loading_01.json",
    "loading_02.json",
    "loading_03.json",
    "loading_04.json",
    "loading_05.json",
)

@Composable
fun InterAdLoadingOverlay(visible: Boolean) {
    if (!visible) return

    var animationAsset by remember { mutableStateOf(LOADING_ASSETS.random()) }
    LaunchedEffect(visible) {
        if (visible) animationAsset = LOADING_ASSETS.random()
    }

    val composition by rememberLottieComposition(LottieCompositionSpec.Asset(animationAsset))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LottieAnimation(
                composition = composition,
                iterations = LottieConstants.IterateForever,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.loading_ad),
                color = Color(0xFF9E9E9E),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

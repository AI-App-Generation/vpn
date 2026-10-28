package com.thanhmich.veil.vpn.features.welcomeback

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thanhmich.veil.vpn.LogScreenView
import com.thanhmich.veil.vpn.R
import com.thanhmich.veil.vpn.ads.AdsPlacementHolder
import com.thanhmich.veil.vpn.ads.AdsPolicy
import com.thanhmich.veil.vpn.ui.components.AppLogoTile
import com.thanhmich.veil.vpn.ui.components.EntryBackground
import com.thanhmich.veil.vpn.ui.components.PrimaryButton
import com.thanhmich.veil.vpn.ui.components.ads.ComposeNativeAdFrame
import com.thanhmich.veil.vpn.ui.theme.AppTheme
import com.thanhmich.veil.vpn.ui.theme.LocalMotionEnabled
import com.thanhmich.veil.vpn.ui.theme.Spacing
import com.thanhmich.veil.vpn.ui.theme.regular

/**
 * Overlay shown when the user returns from background (see BaseActivity). Same artwork and logo
 * treatment as Splash, so coming back feels like the same front door.
 */
@Composable
fun WelcomeBackScreen(
    onDismiss: () -> Unit
) {
    LogScreenView("Welcome Back")

    val activity = LocalActivity.current as? Activity

    LaunchedEffect(activity) {
        activity?.let {
            AdsPolicy.showAppOpenResumeAd(
                activity = it,
                onContinue = {},
            )
        }
    }

    // It covers the whole app, so Back dismisses it instead of reaching the screen below.
    BackHandler(onBack = onDismiss)

    val c = AppTheme.colors
    val motion = LocalMotionEnabled.current

    var entered by remember { mutableStateOf(!motion) }
    LaunchedEffect(Unit) { entered = true }
    val logoScale by animateFloatAsState(
        if (entered) 1f else 0.7f,
        if (motion) spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow) else snap(),
        label = "wbLogoScale",
    )
    val logoAlpha by animateFloatAsState(if (entered) 1f else 0f, if (motion) tween(400) else snap(), label = "wbLogoAlpha")
    val textAppear by animateFloatAsState(
        if (entered) 1f else 0f,
        if (motion) tween(450, delayMillis = 180, easing = FastOutSlowInEasing) else snap(),
        label = "wbText",
    )
    val ctaAppear by animateFloatAsState(
        if (entered) 1f else 0f,
        if (motion) tween(450, delayMillis = 320, easing = FastOutSlowInEasing) else snap(),
        label = "wbCta",
    )
    val glow by rememberInfiniteTransition(label = "wbAmbient").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "wbGlow",
    )

    EntryBackground(
        // Swallow taps so nothing underneath the overlay can be hit.
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = {},
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Spacer(Modifier.weight(1f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.space32),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AppLogoTile(
                    size = 96.dp,
                    glow = if (motion) glow * logoAlpha else 0.6f,
                    modifier = Modifier.graphicsLayer {
                        scaleX = logoScale
                        scaleY = logoScale
                        // ModulateAlpha: no offscreen buffer, so the glow/shadow outside the bounds isn't clipped while fading.
                        alpha = logoAlpha
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    },
                )
                Spacer(Modifier.height(Spacing.space24))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.graphicsLayer {
                        alpha = textAppear
                        translationY = (1f - textAppear) * 16.dp.toPx()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.welcome_back_title),
                        style = AppTheme.type.title,
                        color = c.textPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(Spacing.space8))
                    Text(
                        text = stringResource(R.string.welcome_back_body),
                        style = AppTheme.type.body.regular,
                        color = c.textSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            PrimaryButton(
                text = stringResource(R.string.welcome_back_continue),
                iconRes = R.drawable.ic_shield_check_filled,
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screenHorizontal)
                    .graphicsLayer {
                        alpha = ctaAppear
                        translationY = (1f - ctaAppear) * 12.dp.toPx()
                    },
            )
            Spacer(Modifier.height(Spacing.space24))

            ComposeNativeAdFrame(
                adPlacement = AdsPlacementHolder.NATIVE,
                reloadOnResume = false,
            )
        }
    }
}

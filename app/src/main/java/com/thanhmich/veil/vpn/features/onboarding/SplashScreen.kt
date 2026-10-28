package com.thanhmich.veil.vpn.features.onboarding

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thanhmich.veil.vpn.LogScreenView
import com.thanhmich.veil.vpn.R
import com.thanhmich.veil.vpn.ads.AdsLoader
import com.thanhmich.veil.vpn.ads.AdsPlacementHolder
import com.thanhmich.veil.vpn.ads.AdsPolicy
import com.thanhmich.veil.vpn.ui.components.AppLogoTile
import com.thanhmich.veil.vpn.ui.components.EntryBackground
import com.thanhmich.veil.vpn.ui.components.ads.AdBanner
import com.thanhmich.veil.vpn.ui.theme.AppShapes
import com.thanhmich.veil.vpn.ui.theme.AppTheme
import com.thanhmich.veil.vpn.ui.theme.LocalMotionEnabled
import com.thanhmich.veil.vpn.ui.theme.Spacing
import com.thanhmich.veil.vpn.ui.theme.regular
import com.thanhmich.veil.vpn.ui.theme.semiBold
import com.thanhmich.veil.vpn.ui.theme.tnum
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val LOADING_PROGRESS_ANIMATION_MS = 1200

@Composable
fun SplashScreen(onNext: () -> Unit = {}) {
    LogScreenView("Splash")

    val activity = LocalActivity.current as Activity

    val ready by AdsLoader.finished.collectAsState()
    var progressComplete by remember { mutableStateOf(false) }

    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect

        progressComplete = true
        delay(LOADING_PROGRESS_ANIMATION_MS + 1500L)

        AdsPolicy.showInterAd(
            activity = activity,
            adPlacement = AdsPlacementHolder.INTER,
            force = true,
            onContinue = onNext
        )
    }

    val motion = LocalMotionEnabled.current
    val c = AppTheme.colors

    // One-shot entrance: logo springs in, wordmark then loader rise after it.
    var entered by remember { mutableStateOf(!motion) }
    LaunchedEffect(Unit) { entered = true }
    val logoScale by animateFloatAsState(
        if (entered) 1f else 0.7f,
        if (motion) spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow) else snap(),
        label = "splashLogoScale",
    )
    val logoAlpha by animateFloatAsState(if (entered) 1f else 0f, if (motion) tween(400) else snap(), label = "splashLogoAlpha")
    val textAppear by animateFloatAsState(
        if (entered) 1f else 0f,
        if (motion) tween(500, delayMillis = 220, easing = FastOutSlowInEasing) else snap(),
        label = "splashText",
    )
    val loaderAppear by animateFloatAsState(
        if (entered) 1f else 0f,
        if (motion) tween(500, delayMillis = 400, easing = FastOutSlowInEasing) else snap(),
        label = "splashLoader",
    )

    // Breathing accent glow + a gentle float; static when "Motion effects" is off.
    val ambient = rememberInfiniteTransition(label = "splashAmbient")
    val glow by ambient.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "splashGlow",
    )
    val float by ambient.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "splashFloat",
    )

    EntryBackground {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .statusBarsPadding()
                .offset(y = (-32).dp)
                .padding(horizontal = Spacing.space32),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppLogoTile(
                size = 104.dp,
                glow = if (motion) glow * logoAlpha else 0.6f,
                modifier = Modifier.graphicsLayer {
                    translationY = if (motion) float.dp.toPx() else 0f
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
                    text = stringResource(R.string.app_name),
                    style = AppTheme.type.title,
                    color = c.textPrimary,
                    maxLines = 1,
                )
                Spacer(Modifier.height(Spacing.space4))
                Text(
                    text = stringResource(R.string.splash_tagline),
                    style = AppTheme.type.body.regular,
                    color = c.textSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SplashLoader(
                isDone = progressComplete,
                modifier = Modifier
                    .padding(horizontal = Spacing.space48)
                    .graphicsLayer {
                        alpha = loaderAppear
                        translationY = (1f - loaderAppear) * 12.dp.toPx()
                    },
            )
            // Fixed slot so the loader stays put whether or not the banner has loaded.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                if (ready) {
                    AdBanner(placement = AdsPlacementHolder.BANNER)
                }
            }
        }
    }
}

/** Status line, progress bar and the "may contain ads" notice. */
@Composable
private fun SplashLoader(isDone: Boolean, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val progress = remember { Animatable(0f) }
    val target = remember { (60..85).random() / 100f }

    LaunchedEffect(isDone) {
        if (isDone) {
            progress.animateTo(1f, tween(LOADING_PROGRESS_ANIMATION_MS, easing = FastOutSlowInEasing))
        } else {
            progress.animateTo(target, tween(LOADING_PROGRESS_ANIMATION_MS, easing = FastOutSlowInEasing))
            // Keep creeping while ads/remote config load, so the bar never looks frozen.
            progress.animateTo(0.94f, tween(12_000, easing = LinearOutSlowInEasing))
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.splash_loading),
                style = AppTheme.type.caption,
                color = c.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${(progress.value * 100).roundToInt()}%",
                style = AppTheme.type.caption.semiBold.tnum,
                color = c.textPrimary,
            )
        }
        Spacer(Modifier.height(Spacing.space8))
        SplashProgressBar(progress = progress.value)
        Spacer(Modifier.height(Spacing.space16))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_info_circle),
                contentDescription = null,
                tint = c.textTertiary,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(Spacing.space4))
            Text(
                text = stringResource(R.string.splash_ad_notice),
                style = AppTheme.type.caption,
                color = c.textTertiary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * 8dp bar: `surfaceMuted` track, solid `accent` fill (no gradient — Splash already has its
 * one), a white sheen sweeping across the fill and a glowing head on the leading edge.
 */
@Composable
private fun SplashProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    val motion = LocalMotionEnabled.current
    val fraction = progress.coerceIn(0f, 1f)
    val loops = rememberInfiniteTransition(label = "barLoops")
    val sheenT by loops.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "barSheen",
    )
    val headPulse by loops.animateFloat(
        initialValue = 0.8f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "barHead",
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        val barWidth = maxWidth
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(AppShapes.pill)
                .background(c.surfaceMuted)
        )
        if (fraction > 0f) {
            Box(
                Modifier
                    .width(barWidth * fraction)
                    .height(8.dp)
                    .clip(AppShapes.pill)
                    .background(c.accent)
                    .drawWithContent {
                        drawContent()
                        if (!motion) return@drawWithContent
                        val band = 56.dp.toPx()
                        val x = -band + (size.width + band) * sheenT
                        drawRect(
                            brush = Brush.horizontalGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = 0.5f), Color.Transparent),
                                startX = x,
                                endX = x + band,
                            ),
                            topLeft = Offset(x, 0f),
                            size = Size(band, size.height),
                        )
                    }
            )
            Box(
                Modifier
                    .offset(x = (barWidth * fraction - 8.dp).coerceAtLeast(0.dp))
                    .size(16.dp)
                    .graphicsLayer {
                        val s = if (motion) headPulse else 1f
                        scaleX = s
                        scaleY = s
                    }
                    .clip(CircleShape)
                    .background(c.surface)
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(c.accent)
            )
        }
    }
}

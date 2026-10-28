package com.thanhmich.veil.vpn.features.onboarding

import android.app.Activity
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.thanhmich.veil.vpn.ui.components.AccentHalo
import com.thanhmich.veil.vpn.ui.theme.LocalMotionEnabled
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.thanhmich.veil.vpn.core.SettingManager
import com.thanhmich.veil.vpn.ui.components.PrimaryButton
import com.thanhmich.veil.vpn.ui.components.TextActionButton
import com.thanhmich.veil.vpn.ui.theme.AppShapes
import com.thanhmich.veil.vpn.ui.theme.AppTheme
import com.thanhmich.veil.vpn.ui.theme.Sizes
import com.thanhmich.veil.vpn.ui.theme.Spacing
import com.thanhmich.veil.vpn.ui.theme.motionStd
import com.thanhmich.veil.vpn.ui.theme.regular
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.thanhmich.veil.vpn.LogScreenView
import com.thanhmich.veil.vpn.R
import com.thanhmich.veil.vpn.ads.AdsLoader
import com.thanhmich.veil.vpn.ads.AdsPlacementHolder
import com.thanhmich.veil.vpn.ads.AdsPolicy
import com.thanhmich.veil.vpn.ui.components.ads.ComposeNativeAdFrame
import com.thanhmich.veil.vpn.ui.components.ads.NativeAdOverlay
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Pages that trigger a native-full overlay when the user taps "Next"
private val PAGES_WITH_NATIVE_FULL = setOf(0, 1)

@Composable
fun OnboardingScreen(onNext: () -> Unit = {}) {
    val pageCount = 3
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { pageCount })
    val scope = rememberCoroutineScope()
    val activity = LocalActivity.current ?: return
    var showNativeFull by remember { mutableStateOf(false) }
    val isAdActive by AdsLoader.isActive.collectAsState()

    LogScreenView("Onboarding${pagerState.settledPage + 1}")

    LaunchedEffect(showNativeFull, pagerState.settledPage) {
        val blockWelcome = showNativeFull || pagerState.settledPage in setOf(0, 1)
        AdsPolicy.allowShowWelcomeBack = !blockWelcome
    }
    DisposableEffect(Unit) {
        onDispose { AdsPolicy.allowShowWelcomeBack = true }
    }

    fun navigateToNextReal() {
        showNativeFull = false
        scope.launch {
            delay(200)
            val nextPage = pagerState.currentPage + 1
            if (nextPage < pagerState.pageCount) {
                pagerState.animateScrollToPage(nextPage)
            } else {
                SettingManager.setOnBoardingFlowDone()
                onNext()
            }
        }
    }

    fun finishOnboarding() {
        showNativeFull = false
        SettingManager.setOnBoardingFlowDone()
        onNext()
    }

    fun navigateToNext() {
        if (isAdActive && pagerState.currentPage in PAGES_WITH_NATIVE_FULL) {
            showNativeFull = true
        } else {
            navigateToNextReal()
        }
    }

    BackHandler {
        if (showNativeFull) {
            showNativeFull = false
        } else {
            scope.launch {
                pagerState.animateScrollToPage(
                    (pagerState.currentPage - 1).coerceIn(0, pagerState.pageCount - 1)
                )
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val isInViewport = pagerState.settledPage == page
            when (page) {
                0 -> OnboardingPage(
                    page = 0,
                    pageCount = pageCount,
                    adPlacement = AdsPlacementHolder.NATIVE,
                    isInViewport = isInViewport,
                    onNext = { navigateToNext() },
                    onSkip = ::finishOnboarding,
                )
                1 -> OnboardingPage(
                    page = 1,
                    pageCount = pageCount,
                    adPlacement = AdsPlacementHolder.NATIVE,
                    isInViewport = isInViewport,
                    onNext = { navigateToNext() },
                    onSkip = ::finishOnboarding,
                )
                2 -> OnboardingPage(
                    page = 2,
                    pageCount = pageCount,
                    adPlacement = null,
                    isInViewport = isInViewport,
                    onNext = { navigateToNextReal() },
                    onSkip = ::finishOnboarding,
                )
            }
        }

        if (showNativeFull) {
            val placement = when (pagerState.currentPage) {
                0 -> AdsPlacementHolder.NATIVE
                else -> AdsPlacementHolder.NATIVE
            }
            NativeAdOverlay(
                layoutResId = R.layout.frame_ad_full,
                closeDelaySeconds = 0,
                onClose = { navigateToNextReal() }
            ) { container, shimmer ->
                AdsPolicy.bindNativeAd(
                    activity = activity as Activity,
                    adPlacement = placement,
                    container = container,
                    shimmer = shimmer,
                    templateAdLayoutRes = R.layout.template_ad_full,
                    onAdFailed = { navigateToNextReal() }
                )
            }
        }
    }
}

private data class OnboardingContent(
    @DrawableRes val imageRes: Int,
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
)

private val ONBOARDING_PAGES = listOf(
    OnboardingContent(R.drawable.onboarding_secure, R.string.onboarding_title_1, R.string.onboarding_body_1),
    OnboardingContent(R.drawable.onboarding_global, R.string.onboarding_title_2, R.string.onboarding_body_2),
    // Last page is "Welcome to <app>"; its title is rendered with the app name in accent.
    OnboardingContent(R.drawable.onboarding_welcome, R.string.onboarding_title_welcome, R.string.onboarding_body_welcome),
)

/**
 * 1 trang onboarding (S07): minh hoạ clay (sinh lúc build) trên quầng accent, tiêu đề, mô tả.
 * Native ad (nếu [adPlacement] != null) giữ ở đáy như BaseProject.
 */
@Composable
fun OnboardingPage(
    page: Int,
    pageCount: Int,
    adPlacement: String? = null,
    isInViewport: Boolean = true,
    onNext: () -> Unit = {},
    onSkip: () -> Unit = {},
) {
    val c = AppTheme.colors
    val motion = LocalMotionEnabled.current
    val content = ONBOARDING_PAGES[page]
    val isLast = page == pageCount - 1

    // Each time the page lands: artwork settles from a slight zoom, copy rises in after it.
    val imageAppear by animateFloatAsState(
        targetValue = if (isInViewport) 1f else 0f,
        animationSpec = if (motion) spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow) else snap(),
        label = "onbImage",
    )
    val textAppear by animateFloatAsState(
        targetValue = if (isInViewport) 1f else 0f,
        animationSpec = if (motion) tween(420, delayMillis = if (isInViewport) 120 else 0, easing = FastOutSlowInEasing) else snap(),
        label = "onbText",
    )
    val float by rememberInfiniteTransition(label = "onbFloat").animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "onbFloatY",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Sizes.topBar)
                .padding(horizontal = Spacing.screenHorizontal),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            TextActionButton(
                text = stringResource(R.string.skip),
                onClick = onSkip,
                enabled = !isLast,
                color = c.textSecondary,
                modifier = Modifier.alpha(if (isLast) 0f else 1f),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = Spacing.space32),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AccentHalo(
                modifier = Modifier
                    .fillMaxWidth(0.86f)
                    .weight(1f, fill = false)
                    .aspectRatio(1f)
                    .graphicsLayer {
                        val s = 0.85f + 0.15f * imageAppear
                        scaleX = s
                        scaleY = s
                        alpha = imageAppear
                    },
            ) {
                Image(
                    painter = painterResource(content.imageRes),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize(0.9f)
                        .graphicsLayer { translationY = if (motion) float.dp.toPx() else 0f },
                )
            }
            Spacer(Modifier.height(Spacing.space24))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer {
                    alpha = textAppear
                    translationY = (1f - textAppear) * 16.dp.toPx()
                },
            ) {
                if (isLast) {
                    WelcomeTitle(content.titleRes)
                } else {
                    Text(
                        text = stringResource(content.titleRes),
                        style = AppTheme.type.title,
                        color = c.textPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(Spacing.space12))
                Text(
                    text = stringResource(content.bodyRes),
                    style = AppTheme.type.body.regular,
                    color = c.textSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(Spacing.space24))
        PageDots(current = page, count = pageCount, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(Spacing.space24))
        PrimaryButton(
            text = stringResource(if (isLast) R.string.onboarding_start else R.string.next),
            iconRes = if (isLast) R.drawable.ic_shield_check_filled else null,
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenHorizontal),
        )
        Spacer(Modifier.height(Spacing.space24))

        if (adPlacement != null && isInViewport) {
            ComposeNativeAdFrame(
                adPlacement = adPlacement,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * "Welcome to <app>" with the app name in `accent`. The name is located inside the formatted
 * string, so every translation keeps whatever word order it needs.
 */
@Composable
private fun WelcomeTitle(@StringRes titleRes: Int) {
    val c = AppTheme.colors
    val appName = stringResource(R.string.app_name)
    val full = stringResource(titleRes, appName)
    val start = full.indexOf(appName)
    val text = buildAnnotatedString {
        if (start < 0) {
            append(full)
        } else {
            append(full.substring(0, start))
            withStyle(SpanStyle(color = c.accent)) { append(appName) }
            append(full.substring(start + appName.length))
        }
    }
    Text(
        text = text,
        style = AppTheme.type.title,
        color = c.textPrimary,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun PageDots(current: Int, count: Int, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        repeat(count) { i ->
            val selected = i == current
            val width by animateDpAsState(if (selected) 24.dp else 8.dp, motionStd(), label = "dotWidth")
            Box(
                Modifier
                    .size(width = width, height = 8.dp)
                    .clip(AppShapes.pill)
                    .background(if (selected) c.accent else c.stroke)
            )
        }
    }
}

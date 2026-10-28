package com.thanhmich.veil.vpn.ui.components.ads

import android.util.Log
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.activity.compose.LocalActivity
import androidx.annotation.LayoutRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.thanhmich.veil.vpn.ads.AdsLoader
import com.thanhmich.veil.vpn.ads.AdsPolicy
import com.facebook.shimmer.ShimmerFrameLayout
import com.thanhmich.veil.vpn.R
import kotlinx.coroutines.delay

@Composable
fun ComposeNativeAdFrame(
    adPlacement: String,
    modifier: Modifier = Modifier,
    @LayoutRes layoutResId: Int = R.layout.frame_ad,
    @LayoutRes templateAdLayoutRes: Int = R.layout.template_ad,
    onReloadReady: (reload: (String?) -> Unit) -> Unit = {},
    reloadOnResume: Boolean = true,
    reloadIntervalMs: Long? = null,
) {
    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isVisible by remember { mutableStateOf(true) }
    var isBinding by remember { mutableStateOf(false) }
    var hasShownAd by remember { mutableStateOf(false) }
    var hasLoaded by remember { mutableStateOf(false) }
    var rootView by remember { mutableStateOf<FrameLayout?>(null) }
    var isResumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }

    val isActive by AdsLoader.isActive.collectAsState()
    val placement by rememberUpdatedState(adPlacement)
    val onReloadReadyState by rememberUpdatedState(onReloadReady)

    fun bindAd(root: FrameLayout, placement: String) {
        val act = activity ?: return
        val container = root.findViewById<FrameLayout>(R.id.frAds) ?: return
        val shimmer = root.findViewById<ShimmerFrameLayout>(R.id.shimmerNative) ?: return

        isBinding = true
        AdsPolicy.bindNativeAd(
            activity = act,
            adPlacement = placement,
            container = container,
            shimmer = shimmer,
            parentContainer = root,
            templateAdLayoutRes = templateAdLayoutRes,
            onAdFailed = {
                isBinding = false
                if (!hasShownAd) isVisible = false
            },
            onAdLoaded = {
                isBinding = false
                hasShownAd = true
            },
        )
    }

    fun reinflateAndBind(placement: String) {
        if (!isVisible || isBinding) return
        val root = rootView ?: return
        root.removeAllViews()
        LayoutInflater.from(root.context).inflate(layoutResId, root, true)
        bindAd(root, placement)
    }

    fun reloadAd(overridePlacement: String? = null) {
        if (!isVisible || isBinding) return
        reinflateAndBind(overridePlacement ?: placement)
    }

    LaunchedEffect(rootView) {
        if (rootView == null) return@LaunchedEffect
        onReloadReadyState { reloadAd(it) }
    }

    DisposableEffect(lifecycleOwner) {
        var isFirstResumeEvent = true
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    isResumed = true
                    if (isFirstResumeEvent) {
                        isFirstResumeEvent = false
                    } else if (hasLoaded && reloadOnResume) {
                        reloadAd(null)
                        Log.i("AdsLib", "Ad reload on resume")
                    }
                }
                Lifecycle.Event.ON_PAUSE -> isResumed = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    reloadIntervalMs?.takeIf { it > 0L }?.let { interval ->
        LaunchedEffect(interval, hasLoaded, isActive, isVisible, isResumed) {
            if (!hasLoaded || !isActive || !isVisible || !isResumed) return@LaunchedEffect
            while (hasLoaded && isActive && isVisible && isResumed) {
                delay(interval)
                if (!isActive || !isVisible || !isResumed) break
                reloadAd(null)
            }
        }
    }

    if (isActive && isVisible && activity != null) {
        AndroidView(
            modifier = modifier,
            factory = { FrameLayout(it) },
            update = { root ->
                if (rootView != null) return@AndroidView
                rootView = root
                hasLoaded = true
                reinflateAndBind(placement)
            },
        )
    }
}

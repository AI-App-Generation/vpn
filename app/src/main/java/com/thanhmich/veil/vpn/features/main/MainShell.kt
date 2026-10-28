package com.thanhmich.veil.vpn.features.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.thanhmich.veil.vpn.features.locations.LocationsScreen
import com.thanhmich.veil.vpn.features.settings.SettingsScreen
import com.thanhmich.veil.vpn.features.speed.SpeedScreen
import com.thanhmich.veil.vpn.features.vpn.VpnScreen
import com.thanhmich.veil.vpn.lifecycleIsResumed
import com.thanhmich.veil.vpn.ui.model.MainTab
import com.thanhmich.veil.vpn.ui.theme.AppTheme
import com.thanhmich.veil.vpn.ui.theme.Motion
import com.thanhmich.veil.vpn.ui.theme.Sizes
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.dp
import com.thanhmich.veil.vpn.ui.components.BottomNavBar

private fun NavBackStackEntry.tab(): MainTab? =
    MainTab.entries.firstOrNull { it.route == destination.route }

/** Shell 4 tab: NavHost tab + BottomNavBar bám đáy. */
@Composable
fun MainShell(onOpenLanguage: () -> Unit) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.tab() ?: MainTab.Vpn
    val motion = AppTheme.motionEnabled

    fun select(tab: MainTab) {
        // Read the controller at click time. A callback retained by BottomNavBar must not
        // decide from the `current` value captured during an earlier composition.
        if (nav.currentBackStackEntry?.lifecycleIsResumed() != true) return
        if (nav.currentDestination?.route == tab.route) return
        nav.navigate(tab.route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Back ở tab khác VPN → về VPN; ở VPN → để hệ thống xử lý (thoát app).
    BackHandler(enabled = current != MainTab.Vpn) { select(MainTab.Vpn) }

    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val overlay = remember { ShellOverlayHost() }

    Box(
        Modifier
            .fillMaxSize()
            .background(AppTheme.colors.bg)
    ) {
        val density = LocalDensity.current
        val shift = with(density) { 24.dp.roundToPx() }

        CompositionLocalProvider(LocalShellOverlay provides overlay) {
            NavHost(
                navController = nav,
                startDestination = MainTab.Vpn.route,
                modifier = Modifier.padding(bottom = Sizes.bottomNav + navBarBottom),
                enterTransition = {
                    if (!motion) EnterTransition.None else {
                        val from = initialState.tab()?.ordinal ?: 0
                        val to = targetState.tab()?.ordinal ?: 0
                        val dir = if (to >= from) 1 else -1
                        fadeIn(tween(Motion.STD, easing = Motion.EaseOutCubic)) +
                            slideInHorizontally(tween(Motion.STD, easing = Motion.EaseOutCubic)) { dir * shift }
                    }
                },
                exitTransition = { if (!motion) ExitTransition.None else fadeOut(tween(Motion.FAST)) },
                popEnterTransition = { if (!motion) EnterTransition.None else fadeIn(tween(Motion.STD)) },
                popExitTransition = { if (!motion) ExitTransition.None else fadeOut(tween(Motion.FAST)) },
            ) {
                composable(MainTab.Vpn.route) {
                    VpnScreen(onNavigateTab = ::select)
                }
                composable(MainTab.Locations.route) {
                    LocationsScreen(onNavigateTab = ::select)
                }
                composable(MainTab.Speed.route) {
                    SpeedScreen()
                }
                composable(MainTab.Settings.route) {
                    SettingsScreen(onOpenLanguage = onOpenLanguage)
                }
            }
        }

        BottomNavBar(
            current = current,
            onSelect = ::select,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        // Lớp phủ cấp shell (vd. CommunityNoticeSheet của S01): trên cùng, phủ cả BottomNavBar.
        overlay.content?.invoke()
    }
}


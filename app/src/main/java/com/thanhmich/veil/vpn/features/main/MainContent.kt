package com.thanhmich.veil.vpn.features.main

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.thanhmich.veil.vpn.CheckForConsent
import com.thanhmich.veil.vpn.ui.theme.Motion
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import com.thanhmich.veil.vpn.features.language.LanguageScreen
import com.thanhmich.veil.vpn.lifecycleIsResumed

private const val ROUTE_TABS = "tabs"
private const val ROUTE_LANGUAGE = "language"

/**
 * Main thật của app (thay MainContentSample của BaseProject).
 * NavHost ngoài: `tabs` (MainShell có BottomNavBar) và `language` (S06 từ Setting, che nav bar).
 */
@Composable
fun MainContent() {
    CheckForConsent()

    val nav = rememberNavController()

    fun goTo(route: String) {
        if (nav.currentBackStackEntry?.lifecycleIsResumed() == true) {
            nav.navigate(route) { launchSingleTop = true }
        }
    }

    fun goBack() {
        if (nav.currentBackStackEntry?.lifecycleIsResumed() == true) {
            nav.popBackStack()
        }
    }

    NavHost(
        navController = nav,
        startDestination = ROUTE_TABS,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
    ) {
        composable(ROUTE_TABS) {
            MainShell(onOpenLanguage = { goTo(ROUTE_LANGUAGE) })
        }
        composable(
            route = ROUTE_LANGUAGE,
            enterTransition = {
                fadeIn(tween(Motion.STD)) + slideInHorizontally(tween(Motion.STD)) { it / 4 }
            },
            popExitTransition = {
                fadeOut(tween(Motion.FAST)) + slideOutHorizontally(tween(Motion.STD)) { it / 4 }
            },
        ) {
            LanguageScreen(fromSettings = true, onBack = ::goBack)
        }
    }
}

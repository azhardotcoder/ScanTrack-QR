package com.scantrack.qr.presentation.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.scantrack.qr.presentation.home.HomeScreen
import com.scantrack.qr.presentation.home.HomeViewModel
import com.scantrack.qr.presentation.home.HomeViewModelFactory
import com.scantrack.qr.presentation.history.HistoryScreen
import com.scantrack.qr.presentation.history.HistoryViewModel
import com.scantrack.qr.presentation.history.HistoryViewModelFactory
import com.scantrack.qr.presentation.scanner.ScannerScreen
import com.scantrack.qr.presentation.scanner.ScannerViewModel
import com.scantrack.qr.presentation.scanner.ScannerViewModelFactory
import com.scantrack.qr.presentation.settings.SettingsScreen
import com.scantrack.qr.presentation.settings.SettingsViewModel
import com.scantrack.qr.presentation.settings.SettingsViewModelFactory
import com.scantrack.qr.ScanTrackApp
import com.scantrack.qr.presentation.navigation.Screen

@Composable
fun NavGraph(
    navController: NavHostController,
    paddingValues: PaddingValues
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as ScanTrackApp

    val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModelFactory(app.repository))
    val scannerViewModel: ScannerViewModel = viewModel(factory = ScannerViewModelFactory(app.repository, app.settingsManager))
    val historyViewModel: HistoryViewModel = viewModel(factory = HistoryViewModelFactory(app.repository))
    val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(app.settingsManager, app.repository))

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = Modifier.padding(paddingValues),
        enterTransition = { androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(300)) },
        exitTransition = { androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(300)) },
        popEnterTransition = { androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(300)) },
        popExitTransition = { androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(300)) }
    ) {
        composable(Screen.Home.route) {
            HomeScreen(viewModel = homeViewModel)
        }
        composable(Screen.History.route) {
            HistoryScreen(
                viewModel = historyViewModel,
                onNavigateBack = { 
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { 
                            inclusive = false 
                        }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(viewModel = settingsViewModel)
        }
        composable(
            route = Screen.Scanner.route,
            enterTransition = {
                androidx.compose.animation.slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = androidx.compose.animation.core.tween(300)
                ) + androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(300))
            },
            exitTransition = {
                androidx.compose.animation.slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = androidx.compose.animation.core.tween(300)
                ) + androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(300))
            },
            popEnterTransition = {
                androidx.compose.animation.slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = androidx.compose.animation.core.tween(300)
                ) + androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(300))
            },
            popExitTransition = {
                androidx.compose.animation.slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = androidx.compose.animation.core.tween(300)
                ) + androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(300))
            }
        ) {
            ScannerScreen(
                viewModel = scannerViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

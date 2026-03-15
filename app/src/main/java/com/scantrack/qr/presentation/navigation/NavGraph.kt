package com.scantrack.qr.presentation.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.scantrack.qr.ScanTrackApp
import com.scantrack.qr.presentation.history.HistoryScreen
import com.scantrack.qr.presentation.history.HistoryViewModel
import com.scantrack.qr.presentation.history.HistoryViewModelFactory
import com.scantrack.qr.presentation.home.HomeScreen
import com.scantrack.qr.presentation.home.HomeViewModel
import com.scantrack.qr.presentation.home.HomeViewModelFactory
import com.scantrack.qr.presentation.scanner.ScannerScreen
import com.scantrack.qr.presentation.scanner.ScannerViewModel
import com.scantrack.qr.presentation.scanner.ScannerViewModelFactory
import com.scantrack.qr.presentation.settings.SettingsScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    paddingValues: PaddingValues
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as ScanTrackApp

    val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModelFactory(app.repository))
    val scannerViewModel: ScannerViewModel = viewModel(factory = ScannerViewModelFactory(app.repository))
    val historyViewModel: HistoryViewModel = viewModel(factory = HistoryViewModelFactory(app.repository))

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = Modifier.padding(paddingValues)
    ) {
        composable(Screen.Home.route) {
            HomeScreen(viewModel = homeViewModel)
        }
        composable(Screen.History.route) {
            HistoryScreen(
                viewModel = historyViewModel,
                onNavigateBack = { 
                    if (!navController.popBackStack()) {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Home.route) { inclusive = true }
                        }
                    }
                }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen()
        }
        composable(Screen.Scanner.route) {
            ScannerScreen(
                viewModel = scannerViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

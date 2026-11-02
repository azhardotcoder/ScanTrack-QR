package com.scantrack.qr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.scantrack.qr.presentation.components.*
import com.scantrack.qr.presentation.navigation.NavGraph
import com.scantrack.qr.presentation.navigation.Screen
import com.scantrack.qr.presentation.payment.LocalPaymentController
import com.scantrack.qr.presentation.payment.PaymentLaunchController
import com.scantrack.qr.presentation.theme.ScanTrackQRTheme

class MainActivity : ComponentActivity() {

    private val paymentLaunchController = PaymentLaunchController(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        paymentLaunchController.register()
        enableEdgeToEdge()
        setContent {
            val app = application as ScanTrackApp
            val darkMode by app.settingsManager.darkMode.collectAsStateWithLifecycle(initialValue = "SYSTEM")
            val hapticEnabled by app.settingsManager.hapticEnabled.collectAsStateWithLifecycle(initialValue = true)
            val reducedMotion = rememberReducedMotion()
            val darkTheme = when (darkMode) { "LIGHT" -> false; "DARK" -> true; else -> isSystemInDarkTheme() }
            ScanTrackQRTheme(darkTheme) {
                CompositionLocalProvider(
                    LocalHapticsEnabled provides hapticEnabled,
                    LocalReduceMotion provides reducedMotion,
                    LocalPaymentController provides paymentLaunchController
                ) {
                    val navController = rememberNavController()
                    val entry by navController.currentBackStackEntryAsState()
                    val currentRoute = entry?.destination?.route
                    val scannerVisible = currentRoute?.startsWith(Screen.Scanner.route) == true
                    val view = LocalView.current
                    SideEffect {
                        WindowCompat.getInsetsController(window, view).apply {
                            isAppearanceLightStatusBars = !darkTheme && !scannerVisible
                            isAppearanceLightNavigationBars = !darkTheme && !scannerVisible
                        }
                    }
                    Scaffold(
                        modifier = Modifier.fillMaxSize(), containerColor = MaterialTheme.colorScheme.background,
                        contentWindowInsets = if (scannerVisible) WindowInsets(0, 0, 0, 0) else WindowInsets.safeDrawing,
                        bottomBar = {
                            if (!scannerVisible) PremiumBottomNavigation(currentRoute ?: Screen.Home.route, onNavigate = { route ->
                                if (route != currentRoute) {
                                    if (route == Screen.Scanner.route) navController.navigate(route)
                                    else navController.navigate(route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            })
                        }
                    ) { padding -> NavGraph(navController, padding) }
                }
            }
        }
    }
}

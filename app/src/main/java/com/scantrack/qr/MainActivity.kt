package com.scantrack.qr

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.scantrack.qr.presentation.components.GlassmorphismDock
import com.scantrack.qr.presentation.navigation.NavGraph
import com.scantrack.qr.presentation.navigation.Screen
import com.scantrack.qr.presentation.theme.ScanTrackQRTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScanTrackQRTheme {
                val navController = rememberNavController()
                val snackbarHostState = remember { SnackbarHostState() }
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Transparent, // Root mesh background is behind this
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        // Show dock on Home, History, and Settings only
                        if (currentRoute in listOf(Screen.Home.route, Screen.History.route, Screen.Settings.route)) {
                            GlassmorphismDock(
                                currentRoute = currentRoute,
                                onHistoryClick = { 
                                    if (currentRoute != Screen.History.route) {
                                        navController.navigate(Screen.History.route) {
                                            launchSingleTop = true
                                        }
                                    }
                                },
                                onScanClick = { navController.navigate(Screen.Scanner.route) },
                                onSettingsClick = { 
                                    if (currentRoute != Screen.Settings.route) {
                                        navController.navigate(Screen.Settings.route) {
                                            launchSingleTop = true
                                        }
                                    }
                                }
                            )
                        }
                    }
                ) { innerPadding ->
                    NavGraph(
                        navController = navController,
                        paddingValues = innerPadding
                    )
                }
            }
        }
    }
}

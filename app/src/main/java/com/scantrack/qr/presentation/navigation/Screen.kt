package com.scantrack.qr.presentation.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object History : Screen("history")
    object Settings : Screen("settings")
    object Scanner : Screen("scanner")
}

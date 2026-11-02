package com.scantrack.qr

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.view.WindowCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.scantrack.qr.data.local.entity.QrEntity
import com.scantrack.qr.presentation.components.LocalHapticsEnabled
import com.scantrack.qr.presentation.components.LocalReduceMotion
import com.scantrack.qr.presentation.components.PremiumBottomNavigation
import com.scantrack.qr.presentation.components.QRResultBottomSheet
import com.scantrack.qr.presentation.history.HistoryScreenContent
import com.scantrack.qr.presentation.home.HomeScreenContent
import com.scantrack.qr.presentation.navigation.Screen
import com.scantrack.qr.presentation.scanner.ScanResult
import com.scantrack.qr.presentation.settings.SettingsScreenContent
import com.scantrack.qr.presentation.theme.ScanTrackQRTheme
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Captures the real production composables on the attached Android device.
 * Fixtures live only in this test; this harness never writes the user's database.
 * Run with qaPrefix=393dp (or another device configuration), qaCompact=true for
 * eight representative dark views, or omit qaCompact for the full phone suite.
 */
class VisualQaCaptureTest {
    @get:Rule val compose = createComposeRule()

    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    @Test
    fun capturePremiumUi() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        val prefix = (arguments.getString("qaPrefix") ?: "phone")
            .replace(Regex("[^A-Za-z0-9_-]"), "-").take(80)
        val compact = arguments.getString("qaCompact").equals("true", ignoreCase = true)
        val output = File(instrumentation.targetContext.getExternalFilesDir(null), "ui-qa")
        check(output.mkdirs() || output.isDirectory)

        var screen by mutableStateOf("home-populated")
        var dark by mutableStateOf(true)
        var renderKey by mutableStateOf(0)
        var haptics by mutableStateOf(true)
        var theme by mutableStateOf("DARK")
        val now = System.currentTimeMillis()
        val fixtures = listOf(
            QrEntity(id = 1, rawValue = "https://example.com/projects/scantrack", label = "Project workspace",
                type = "URL", createdAt = now - 86400000L, lastUsedAt = now - 240000L, usageCount = 8, isPinned = true),
            QrEntity(id = 2, rawValue = "upi://pay?pa=qa.store@upi&pn=QA%20Store&am=149.00&cu=INR&tr=QA-101&mc=5411&mode=02",
                label = "QA Store", type = "PAYMENT", createdAt = now - 86400000L,
                lastUsedAt = now - 1200000L, usageCount = 12),
            QrEntity(id = 3, rawValue = "WIFI:T:WPA;S:Studio Guest;P:qa-only-password;;",
                label = "Studio Guest", type = "WIFI", lastUsedAt = now - 3600000L, usageCount = 5),
            QrEntity(id = 4, rawValue = "tel:+12025550123", label = "Front desk", type = "PHONE",
                lastUsedAt = now - 7200000L),
            QrEntity(id = 5, rawValue = "mailto:hello@example.com?subject=Hello", label = "Contact email",
                type = "EMAIL", lastUsedAt = now - 10800000L),
            QrEntity(id = 6, rawValue = "Meeting notes\nA clear place for useful information.\n".repeat(12),
                label = "Meeting notes", type = "TEXT", lastUsedAt = now - 21600000L),
            QrEntity(id = 7, rawValue = "https://example.org/a/longer/destination?reference=qr-preview",
                label = "Reading list", type = "URL", lastUsedAt = now - 43200000L)
        )

        compose.setContent {
            CompositionLocalProvider(LocalHapticsEnabled provides false, LocalReduceMotion provides true) {
                ScanTrackQRTheme(darkTheme = dark) {
                    val view = LocalView.current
                    SideEffect {
                        view.context.findActivity()?.window?.let { window ->
                            WindowCompat.getInsetsController(window, view).apply {
                                isAppearanceLightStatusBars = !dark
                                isAppearanceLightNavigationBars = !dark
                            }
                        }
                    }
                    key(renderKey) {
                        val route = when {
                            screen.startsWith("history") -> Screen.History.route
                            screen.startsWith("settings") -> Screen.Settings.route
                            else -> Screen.Home.route
                        }
                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            containerColor = MaterialTheme.colorScheme.background,
                            bottomBar = { PremiumBottomNavigation(currentRoute = route, onNavigate = {}) }
                        ) { innerPadding ->
                            Box(Modifier.fillMaxSize().padding(innerPadding)) {
                                when {
                                    screen.startsWith("settings") -> SettingsScreenContent(
                                        hapticEnabled = haptics, darkMode = theme, versionName = "1.0.6",
                                        onHapticChanged = { haptics = it }, onThemeSelected = { theme = it },
                                        onClearHistory = {}, onOpenSource = {}
                                    )
                                    screen.startsWith("history") -> HistoryScreenContent(
                                        historyItems = when (screen) {
                                            "history-empty", "history-no-results" -> emptyList()
                                            "history-payment" -> fixtures.filter { it.type == "PAYMENT" }
                                            else -> fixtures
                                        },
                                        searchQuery = when (screen) {
                                            "history-payment" -> "QA"
                                            "history-no-results" -> "missing scan"
                                            else -> ""
                                        },
                                        selectedType = if (screen == "history-payment") "PAYMENT" else "All",
                                        onQueryChange = {}, onTypeSelected = {}, onQrClick = {}, onNavigateBack = {}
                                    )
                                    else -> HomeScreenContent(
                                        pinnedQrs = if (screen == "home-empty") emptyList() else fixtures.filter { it.isPinned },
                                        quickAccessQrs = if (screen == "home-empty") emptyList() else fixtures.filter { !it.isPinned },
                                        recentScans = if (screen == "home-empty") emptyList() else fixtures,
                                        onQrClick = {}
                                    )
                                }
                            }
                        }
                        if (screen.startsWith("result-")) {
                            val type = screen.removePrefix("result-")
                            val qr = fixtures.first { it.type == type }
                            QRResultBottomSheet(ScanResult(qr.rawValue, qr.label.orEmpty(), qr.type), onDismiss = {})
                        }
                    }
                }
            }
        }

        fun render(page: String, darkTheme: Boolean = true) {
            compose.runOnIdle {
                dark = darkTheme
                screen = page
                theme = if (darkTheme) "DARK" else "LIGHT"
                renderKey++
            }
            compose.waitForIdle()
        }

        fun capture(name: String) {
            compose.waitForIdle()
            instrumentation.waitForIdleSync()
            // Compose's test clock can finish before SurfaceFlinger presents a newly
            // created window (especially a Dialog/ModalBottomSheet). This real-time
            // settling is exclusive to screenshot QA; it adds no production delay.
            SystemClock.sleep(750L)
            compose.waitForIdle()
            instrumentation.waitForIdleSync()
            instrumentation.uiAutomation.waitForIdle(100L, 3_000L)
            SystemClock.sleep(100L)
            val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot()) {
                "Android could not capture the screen for $name"
            }
            File(output, "$prefix-$name.png").outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            bitmap.recycle()
        }

        render("home-populated")
        capture("home-populated-dark")
        render("history-populated")
        capture("history-populated-dark")
        render("settings")
        capture("settings-dark")
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Private by design"))
        capture("settings-about-dark")
        render("result-WIFI")
        compose.onNodeWithText("QR code captured").assertIsDisplayed()
        capture("result-wifi-dark")
        compose.onNodeWithText("Copy password").performScrollTo().assertIsDisplayed()
        capture("result-wifi-actions-dark")
        render("result-PAYMENT")
        compose.onNodeWithText("QR code captured").assertIsDisplayed()
        capture("result-upi-dark")
        compose.onNodeWithText("Continue to UPI App").performScrollTo().assertIsDisplayed()
        capture("result-upi-actions-dark")

        if (!compact) {
            render("home-empty", darkTheme = false)
            capture("home-empty-light")
            render("home-empty")
            capture("home-empty-dark")
            render("home-populated", darkTheme = false)
            capture("home-populated-light")
            render("home-populated")
            compose.onAllNodes(hasScrollToIndexAction())[0]
                .performScrollToNode(hasText("Recent activity"))
            capture("home-activity-dark")
            render("history-empty")
            capture("history-empty-dark")
            render("history-payment")
            capture("history-filtered-payment-dark")
            render("history-no-results")
            capture("history-no-results-dark")
            render("history-populated", darkTheme = false)
            capture("history-populated-light")
            render("settings", darkTheme = false)
            capture("settings-light")
            compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Private by design"))
            capture("settings-about-light")
            render("settings")
            compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Clear scan history"))
            compose.onNodeWithText("Clear scan history").performClick()
            capture("settings-clear-confirmation-dark")
            listOf("URL", "PHONE", "EMAIL", "TEXT").forEach { type ->
                render("result-$type")
                capture("result-${type.lowercase()}-dark")
            }
            listOf("URL", "WIFI", "PAYMENT").forEach { type ->
                render("result-$type", darkTheme = false)
                capture("result-${if (type == "PAYMENT") "upi" else type.lowercase()}-light")
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

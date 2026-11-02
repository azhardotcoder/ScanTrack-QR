package com.scantrack.qr

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.scantrack.qr.data.local.entity.QrEntity
import com.scantrack.qr.presentation.components.LocalHapticsEnabled
import com.scantrack.qr.presentation.components.LocalReduceMotion
import com.scantrack.qr.presentation.components.PremiumBottomNavigation
import com.scantrack.qr.presentation.home.HomeScreenContent
import com.scantrack.qr.presentation.navigation.Screen
import com.scantrack.qr.presentation.theme.ScanTrackQRTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeNavigationUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun emptyHomeOffersIndependentScanAndGalleryActions() {
        var scans = 0
        var galleries = 0
        compose.setContent {
            ScanTrackQRTheme {
                CompositionLocalProvider(LocalReduceMotion provides true) {
                    HomeScreenContent(
                        pinnedQrs = emptyList(),
                        quickAccessQrs = emptyList(),
                        recentScans = emptyList(),
                        onQrClick = {},
                        onScan = { scans++ },
                        onGalleryScan = { galleries++ }
                    )
                }
            }
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Gallery scan"))
        compose.onNodeWithText("Gallery scan").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(1, galleries)
            assertEquals(0, scans)
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Scan QR"))
        compose.onNodeWithText("Scan QR").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(1, galleries)
            assertEquals(1, scans)
        }
    }

    @Test
    fun emptyHomeActionsRemainReachableAt320DpAndDoubleText() {
        var scans = 0
        var galleries = 0
        compose.setContent {
            val density = LocalDensity.current.density
            ScanTrackQRTheme {
                CompositionLocalProvider(
                    LocalDensity provides Density(density, fontScale = 2f),
                    LocalReduceMotion provides true
                ) {
                    Box(Modifier.size(width = 320.dp, height = 640.dp)) {
                        HomeScreenContent(
                            pinnedQrs = emptyList(),
                            quickAccessQrs = emptyList(),
                            recentScans = emptyList(),
                            onQrClick = {},
                            onScan = { scans++ },
                            onGalleryScan = { galleries++ }
                        )
                    }
                }
            }
        }
        compose.onNodeWithText("Scan QR").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("Gallery scan").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(1, scans)
            assertEquals(1, galleries)
        }
    }

    @Test
    fun homeDoesNotRepeatFrequentScanAndWifiPreviewHidesCredentials() {
        val frequent = QrEntity(
            id = 1,
            rawValue = "https://example.org/portal",
            label = "Frequent portal",
            type = "URL",
            usageCount = 8
        )
        val wifi = QrEntity(
            id = 2,
            rawValue = "WIFI:T:WPA;S:Studio;P:private-password-123;;",
            label = "Studio network",
            type = "WIFI"
        )
        compose.setContent {
            ScanTrackQRTheme(darkTheme = true) {
                CompositionLocalProvider(LocalReduceMotion provides true) {
                    // Keep Quick access and Recent activity composed together so
                    // the assertion detects the same record in both sections.
                    Box(Modifier.width(360.dp).requiredHeight(1600.dp)) {
                        HomeScreenContent(
                            pinnedQrs = emptyList(),
                            quickAccessQrs = listOf(frequent, wifi),
                            recentScans = listOf(frequent, wifi),
                            onQrClick = {}
                        )
                    }
                }
            }
        }
        compose.onAllNodesWithText("Frequent portal", useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithText("Studio network", useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithText("Studio · WPA", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("private-password-123", substring = true, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText(wifi.rawValue, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun allNavigationLabelsAndSettingsRemainAccessibleAt320DpAndDoubleText() {
        var destination: String? = null
        compose.setContent {
            val density = LocalDensity.current.density
            ScanTrackQRTheme {
                CompositionLocalProvider(
                    LocalDensity provides Density(density, fontScale = 2f),
                    LocalHapticsEnabled provides false
                ) {
                    Box(Modifier.width(320.dp)) {
                        PremiumBottomNavigation(
                            currentRoute = Screen.Home.route,
                            onNavigate = { destination = it }
                        )
                    }
                }
            }
        }
        listOf("Home", "History", "Scan", "Settings").forEach { label ->
            compose.onNodeWithText(label).assertIsDisplayed()
        }
        compose.onNodeWithText("Home").assertIsSelected()
        compose.onNodeWithText("Settings").performClick()
        compose.runOnIdle { assertEquals(Screen.Settings.route, destination) }
    }
}

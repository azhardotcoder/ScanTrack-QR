package com.scantrack.qr

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.scantrack.qr.presentation.history.HistoryScreenContent
import com.scantrack.qr.presentation.settings.SettingsScreenContent
import com.scantrack.qr.presentation.theme.ScanTrackQRTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HistorySettingsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun clearingHistoryRequiresConfirmationAndCancelPreservesIt() {
        var clears = 0
        compose.setContent {
            ScanTrackQRTheme {
                SettingsScreenContent(
                    hapticEnabled = true,
                    darkMode = "SYSTEM",
                    versionName = "test",
                    onHapticChanged = {},
                    onThemeSelected = {},
                    onClearHistory = { clears++ },
                    onOpenSource = {}
                )
            }
        }
        compose.onNodeWithTag("settings-list")
            .performScrollToNode(hasText("Clear scan history"))
        compose.onNodeWithText("Clear scan history").performClick()
        compose.onNodeWithText("Clear all scan history?").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, clears) }
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(0, clears) }
        compose.onNodeWithText("Clear scan history").performClick()
        compose.onNodeWithText("Clear all history").performClick()
        compose.runOnIdle { assertEquals(1, clears) }
    }

    @Test
    fun themeAndHapticRowsExposeTheirSelectionAndToggleActions() {
        var theme = "SYSTEM"
        var haptics = true
        compose.setContent {
            ScanTrackQRTheme {
                SettingsScreenContent(
                    hapticEnabled = haptics,
                    darkMode = theme,
                    versionName = "test",
                    onHapticChanged = { haptics = it },
                    onThemeSelected = { theme = it },
                    onClearHistory = {},
                    onOpenSource = {}
                )
            }
        }
        compose.onNodeWithText("System").assertIsSelected()
        compose.onNodeWithText("Dark").performClick()
        compose.runOnIdle { assertEquals("DARK", theme) }
        compose.onNodeWithTag("settings-list")
            .performScrollToNode(hasText("Haptic feedback"))
        compose.onNodeWithText("Haptic feedback").assertIsOn().performClick()
        compose.runOnIdle { assertEquals(false, haptics) }
    }

    @Test
    fun emptyFilteredHistoryOffersClearSearchAndTypeTogether() {
        var query = "missing"
        var type = "PAYMENT"
        compose.setContent {
            ScanTrackQRTheme {
                HistoryScreenContent(
                    historyItems = emptyList(),
                    searchQuery = query,
                    selectedType = type,
                    onQueryChange = { query = it },
                    onTypeSelected = { type = it },
                    onQrClick = {},
                    onNavigateBack = {}
                )
            }
        }
        compose.onNodeWithTag("history-list")
            .performScrollToNode(hasText("Clear filters"))
        compose.onNodeWithText("Clear filters").performClick()
        compose.runOnIdle {
            assertEquals("", query)
            assertEquals("All", type)
        }
    }
}

package com.scantrack.qr

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scantrack.qr.data.local.entity.QrEntity
import com.scantrack.qr.data.parser.QrTypeDetector
import com.scantrack.qr.presentation.components.QRResultBottomSheet
import com.scantrack.qr.presentation.components.QrDetailSheet
import com.scantrack.qr.presentation.scanner.ScanResult
import com.scantrack.qr.presentation.theme.ScanTrackQRTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResultSheetUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun freshWifiResult_masksCredentialsUntilExplicitlyRevealedAndCanHideAgain() {
        val password = "PrivatePass123"
        val raw = "WIFI:T:WPA;S:Studio Network;P:$password;;"
        compose.setContent {
            ScanTrackQRTheme {
                QRResultBottomSheet(
                    ScanResult(raw, "Wi-Fi: Studio Network", QrTypeDetector.TYPE_WIFI),
                    onDismiss = {}
                )
            }
        }

        compose.onNodeWithText("QR code captured").assertExists()
        compose.onNodeWithText("Studio Network", useUnmergedTree = true).assertExists()
        compose.onNodeWithText(password, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText(raw, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Show password").performScrollTo().performClick()
        compose.onNodeWithText(password, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Hide password").performScrollTo().performClick()
        compose.onNodeWithText(password, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Show password").assertIsDisplayed()
    }

    @Test
    fun invalidUpi_isNotOpenedAutomaticallyAndContinueIsDisabled() {
        val qr = QrEntity(
            id = 4,
            rawValue = "upi://pay?pa=store@upi&pn=Store&am=-1&cu=INR",
            label = "Store payment",
            type = QrTypeDetector.TYPE_PAYMENT
        )
        var openCount = 0
        showHistorySheet(qr, onOpen = { openCount++ })

        compose.onNodeWithText("store@upi", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Continue to UPI App").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, openCount) }
    }

    @Test
    fun merchantUpi_displaysIdentityAndPassesTheOriginalRecordOnlyAfterContinue() {
        val originalUri = "upi://pay?pa=studio@upi&pn=Studio%20Cafe&am=125.50&cu=INR&mc=5812&tr=order-007&mode=02"
        val qr = QrEntity(
            id = 5,
            rawValue = originalUri,
            label = "Lunch",
            type = QrTypeDetector.TYPE_PAYMENT
        )
        var opened: QrEntity? = null
        var openCount = 0
        showHistorySheet(qr, onOpen = { opened = it; openCount++ })

        compose.onNodeWithText("Merchant payment").assertExists()
        compose.onNodeWithText("Studio Cafe", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("studio@upi", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("125.50", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("INR", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("order-007", useUnmergedTree = true).assertExists()
        compose.runOnIdle { assertEquals(0, openCount) }
        compose.onNodeWithText("Continue to UPI App").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(1, openCount)
            assertEquals(originalUri, opened?.rawValue)
        }
        compose.onNodeWithContentDescription("Close QR details").assertExists()
    }

    @Test
    fun deleteRequiresConfirmationAndCancelPreservesTheRecord() {
        val qr = QrEntity(id = 6, rawValue = "Keep this note", type = QrTypeDetector.TYPE_TEXT)
        var deleteCount = 0
        showHistorySheet(qr, onDelete = { deleteCount++ })

        compose.runOnIdle { assertEquals(0, deleteCount) }
        compose.onNodeWithText("Delete scan").performScrollTo().performClick()
        compose.onNodeWithText("Delete this scan?").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, deleteCount) }
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(0, deleteCount) }
        compose.onNodeWithText("Delete scan").performScrollTo().performClick()
        compose.onNode(hasText("Delete scan") and hasAnyAncestor(isDialog())).performClick()
        compose.runOnIdle { assertEquals(1, deleteCount) }
    }

    @Test
    fun wifiShareRequiresConfirmationAndCancelKeepsPasswordMasked() {
        val password = "PrivatePass123"
        val qr = QrEntity(
            id = 7,
            rawValue = "WIFI:T:WPA;S:Studio Network;P:$password;;",
            type = QrTypeDetector.TYPE_WIFI
        )
        showHistorySheet(qr)

        compose.onNodeWithText("Share").performScrollTo().performClick()
        compose.onNodeWithText("Share Wi-Fi details?").assertIsDisplayed()
        compose.onNodeWithText(password, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Share Wi-Fi details?").assertDoesNotExist()
        compose.onNodeWithText(password, useUnmergedTree = true).assertDoesNotExist()
    }

    private fun showHistorySheet(
        qr: QrEntity,
        onOpen: (QrEntity) -> Unit = {},
        onDelete: (QrEntity) -> Unit = {}
    ) {
        compose.setContent {
            ScanTrackQRTheme {
                QrDetailSheet(
                    qr = qr,
                    onDismiss = {},
                    onPinToggle = {},
                    onRename = { _, _ -> },
                    onDelete = onDelete,
                    onOpen = onOpen
                )
            }
        }
    }
}

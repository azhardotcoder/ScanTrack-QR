package com.scantrack.qr

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.scantrack.qr.presentation.components.LocalHapticsEnabled
import com.scantrack.qr.presentation.scanner.PermissionRationale
import com.scantrack.qr.presentation.scanner.ScannerActionButton
import com.scantrack.qr.presentation.theme.ScanTrackQRTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ScannerPermissionUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun firstRequestOffersCameraAndIndependentGallery() {
        var cameraRequests = 0
        var galleryRequests = 0
        compose.setContent {
            ScanTrackQRTheme {
                PermissionRationale(
                    permanentlyDenied = false,
                    onRequestPermission = { cameraRequests++ },
                    onPickFromGallery = { galleryRequests++ },
                    onNavigateBack = {}
                )
            }
        }
        compose.onNodeWithText("Open Settings").assertDoesNotExist()
        compose.onNodeWithText("Scan from Gallery").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(1, galleryRequests)
            assertEquals(0, cameraRequests)
        }
        compose.onNodeWithText("Allow Camera").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, cameraRequests) }
    }

    @Test
    fun permanentDenialOffersSettingsAndKeepsGalleryAvailable() {
        var cameraRequests = 0
        var galleryRequests = 0
        compose.setContent {
            ScanTrackQRTheme(darkTheme = true) {
                PermissionRationale(
                    permanentlyDenied = true,
                    onRequestPermission = { cameraRequests++ },
                    onPickFromGallery = { galleryRequests++ },
                    onNavigateBack = {}
                )
            }
        }
        compose.onNodeWithText("Allow Camera").assertDoesNotExist()
        compose.onNodeWithText("Open Settings").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Scan from Gallery").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(1, galleryRequests)
            assertEquals(0, cameraRequests)
        }
    }

    @Test
    fun permissionActionsRemainReachableOnSmallScreenWithLargeText() {
        var galleryRequests = 0
        compose.setContent {
            val density = LocalDensity.current.density
            ScanTrackQRTheme {
                CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f)) {
                    Box(Modifier.size(width = 320.dp, height = 400.dp)) {
                        PermissionRationale(
                            permanentlyDenied = false,
                            onRequestPermission = {},
                            onPickFromGallery = { galleryRequests++ },
                            onNavigateBack = {}
                        )
                    }
                }
            }
        }
        compose.onNodeWithText("Allow Camera").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Scan from Gallery").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, galleryRequests) }
    }

    @Test
    fun flashlightControlAnnouncesItsStateAndToggles() {
        compose.setContent {
            var on by remember { mutableStateOf(true) }
            ScanTrackQRTheme {
                CompositionLocalProvider(LocalHapticsEnabled provides false) {
                    ScannerActionButton(
                        icon = if (on) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff,
                        label = if (on) "Turn flashlight off" else "Turn flashlight on",
                        selected = on,
                        onClick = { on = !on }
                    )
                }
            }
        }
        compose.onNodeWithContentDescription("Turn flashlight off")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "On"))
            .performClick()
        compose.onNodeWithContentDescription("Turn flashlight on")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Off"))
    }
}

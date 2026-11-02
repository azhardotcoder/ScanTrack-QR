package com.scantrack.qr

import com.scantrack.qr.data.local.entity.QrEntity
import com.scantrack.qr.presentation.components.qrContentPreview
import com.scantrack.qr.presentation.components.qrDisplayTitle
import org.junit.Assert.*
import org.junit.Test

class QrPresentationTest {
    @Test fun wifiPreviewDoesNotExposeCredentials() {
        val qr = QrEntity(rawValue = "WIFI:T:WPA;S:Guest;P:SecretPass123;;", type = "WIFI")
        val preview = qrContentPreview(qr)
        assertTrue(preview.contains("Guest"))
        assertFalse(preview.contains("SecretPass123"))
        assertFalse(preview.contains("P:"))
    }

    @Test fun missingLabelFallsBackToHumanReadableType() {
        val qr = QrEntity(rawValue = "WIFI:T:WPA;S:Guest;P:SecretPass123;;", label = " ", type = "WIFI")
        assertEquals("Wi-Fi: Guest", qrDisplayTitle(qr))
    }

    @Test fun paymentPreviewShowsIdentityRatherThanEntirePaymentInstructions() {
        val qr = QrEntity(rawValue = "upi://pay?pa=shop@upi&pn=Shop&tr=private-order&mc=5411", type = "PAYMENT")
        assertEquals("shop@upi", qrContentPreview(qr))
    }
}

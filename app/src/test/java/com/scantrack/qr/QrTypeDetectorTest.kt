package com.scantrack.qr

import com.scantrack.qr.data.parser.QrTypeDetector
import org.junit.Assert.*
import org.junit.Test

class QrTypeDetectorTest {

    @Test
    fun detect_httpAndHttps_classifiedAsUrl() {
        assertEquals(QrTypeDetector.TYPE_URL, QrTypeDetector.detect("https://google.com"))
        assertEquals(QrTypeDetector.TYPE_URL, QrTypeDetector.detect("http://sub.example.org/path?k=v"))
        assertEquals(QrTypeDetector.TYPE_URL, QrTypeDetector.detect("HTTPS://GITHUB.COM/TEST"))
    }

    @Test
    fun detect_dangerousSchemes_classifiedAsText() {
        // javascript:, file:, data: must NEVER be classified as clickable URL
        assertEquals(QrTypeDetector.TYPE_TEXT, QrTypeDetector.detect("javascript:alert(1)"))
        assertEquals(QrTypeDetector.TYPE_TEXT, QrTypeDetector.detect("file:///data/user/0/com.app/databases/app.db"))
        assertEquals(QrTypeDetector.TYPE_TEXT, QrTypeDetector.detect("data:text/html,<script>alert(1)</script>"))
        assertEquals(QrTypeDetector.TYPE_TEXT, QrTypeDetector.detect("content://media/external/images/media/1"))
    }

    @Test
    fun detect_upiSchemes_classifiedAsPayment() {
        assertEquals(QrTypeDetector.TYPE_PAYMENT, QrTypeDetector.detect("upi://pay?pa=store@upi"))
        assertEquals(QrTypeDetector.TYPE_PAYMENT, QrTypeDetector.detect("UPI://PAY?PA=TEST@UPI"))
    }

    @Test
    fun detect_wifiScheme_classifiedAsWifi() {
        assertEquals(QrTypeDetector.TYPE_WIFI, QrTypeDetector.detect("WIFI:T:WPA;S:Home;P:Secret;;"))
        assertEquals(QrTypeDetector.TYPE_WIFI, QrTypeDetector.detect("wifi:t:nopass;s:guest;;"))
    }

    @Test
    fun detect_emailAndPhoneSchemes_classifiedCorrectly() {
        assertEquals(QrTypeDetector.TYPE_EMAIL, QrTypeDetector.detect("mailto:support@scantrack.app"))
        assertEquals(QrTypeDetector.TYPE_PHONE, QrTypeDetector.detect("tel:+1234567890"))
    }

    @Test
    fun detect_plainText_classifiedAsText() {
        assertEquals(QrTypeDetector.TYPE_TEXT, QrTypeDetector.detect("Meeting notes for Monday"))
        assertEquals(QrTypeDetector.TYPE_TEXT, QrTypeDetector.detect(""))
    }

    @Test
    fun deriveLabel_extractsSensibleNames() {
        assertEquals("google.com", QrTypeDetector.deriveLabel("https://www.google.com/search?q=qr", QrTypeDetector.TYPE_URL))
        assertEquals("Pay: Coffee Shop", QrTypeDetector.deriveLabel("upi://pay?pa=coffee@upi&pn=Coffee%20Shop", QrTypeDetector.TYPE_PAYMENT))
        assertEquals("Wi-Fi: HomeNet", QrTypeDetector.deriveLabel("WIFI:T:WPA;S:HomeNet;P:12345678;;", QrTypeDetector.TYPE_WIFI))
        assertEquals("Call: +1987654321", QrTypeDetector.deriveLabel("tel:+1987654321", QrTypeDetector.TYPE_PHONE))
        assertEquals("Email: info@corp.com", QrTypeDetector.deriveLabel("mailto:info@corp.com", QrTypeDetector.TYPE_EMAIL))
    }
}


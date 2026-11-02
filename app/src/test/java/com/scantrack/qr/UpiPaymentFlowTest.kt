package com.scantrack.qr

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.scantrack.qr.data.parser.QrTypeDetector
import com.scantrack.qr.data.parser.UpiPaymentParser
import com.scantrack.qr.presentation.utils.ActionUtils
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpiPaymentFlowTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun realPersonalP2PPaymentFlow_detectedParsedAndDispatchedSuccessfully() {
        val realP2PQr = "upi://pay?pa=rohit.sharma@okhdfcbank&pn=Rohit%20Sharma&am=125.50&cu=INR&tn=Dinner%20Share"

        // 1. Detection
        val detectedType = QrTypeDetector.detect(realP2PQr)
        assertEquals(QrTypeDetector.TYPE_PAYMENT, detectedType)

        // 2. Label derivation
        val label = QrTypeDetector.deriveLabel(realP2PQr, detectedType)
        assertEquals("Pay: Rohit Sharma", label)

        // 3. Strict Parsing
        val parsed = UpiPaymentParser.parse(realP2PQr)
        assertTrue("Expected valid UPI payload", parsed.isValid)
        assertFalse("Personal QR should not be flagged as merchant", parsed.isMerchant)
        assertEquals("rohit.sharma@okhdfcbank", parsed.payeeVpa)
        assertEquals("Rohit Sharma", parsed.payeeName)
        assertEquals("125.50", parsed.amount)
        assertEquals("INR", parsed.currency)
        assertEquals("Dinner Share", parsed.transactionNote)
        assertEquals(realP2PQr, parsed.originalUri)

        // 4. Intent Construction
        val intent = ActionUtils.buildUpiPaymentIntent(realP2PQr)
        assertNotNull(intent)
        assertEquals(Intent.ACTION_VIEW, intent?.action)
        assertEquals(realP2PQr, intent?.dataString)
        assertTrue((intent?.flags ?: 0) and Intent.FLAG_ACTIVITY_NEW_TASK != 0)

        // 5. Action Execution (Robolectric Shadow Activity Verification)
        ActionUtils.openUpiPayment(context, realP2PQr)
        val shadowApp = shadowOf(ApplicationProvider.getApplicationContext() as android.app.Application)
        val nextStartedIntent = shadowApp.nextStartedActivity
        assertNotNull("Expected chooser intent to be launched", nextStartedIntent)
        assertEquals(Intent.ACTION_CHOOSER, nextStartedIntent.action)
        val targetIntent = nextStartedIntent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertNotNull(targetIntent)
        assertEquals(Intent.ACTION_VIEW, targetIntent?.action)
        assertEquals(realP2PQr, targetIntent?.dataString)
    }

    @Test
    fun realMerchantDynamicPosQr_preservesAllMerchantParametersAndSigns() {
        val realMerchantQr = "upi://pay?pa=store99@icici&pn=Reliance%20Retail&mc=5411&tr=REC9876543210&tid=MUM01001&mode=02&am=1499.00&cu=INR&orgid=180001&sign=MEYCIQC9876543210abcdef=="

        // 1. Detection
        val detectedType = QrTypeDetector.detect(realMerchantQr)
        assertEquals(QrTypeDetector.TYPE_PAYMENT, detectedType)

        // 2. Parsing with Merchant Identification
        val parsed = UpiPaymentParser.parse(realMerchantQr)
        assertTrue("Expected valid merchant UPI payload", parsed.isValid)
        assertTrue("Must be classified as merchant", parsed.isMerchant)
        assertEquals("store99@icici", parsed.payeeVpa)
        assertEquals("Reliance Retail", parsed.payeeName)
        assertEquals("5411", parsed.merchantCode)
        assertEquals("REC9876543210", parsed.transactionRef)
        assertEquals("MUM01001", parsed.terminalId)
        assertEquals("02", parsed.mode)
        assertEquals("180001", parsed.orgId)
        assertEquals("1499.00", parsed.amount)
        assertEquals("INR", parsed.currency)

        // 3. Complete byte-for-byte URI preservation in Intent
        val intent = ActionUtils.buildUpiPaymentIntent(realMerchantQr)
        assertNotNull(intent)
        assertEquals(realMerchantQr, intent?.dataString)
        assertTrue(intent?.dataString?.contains("mc=5411") == true)
        assertTrue(intent?.dataString?.contains("mode=02") == true)
        assertTrue(intent?.dataString?.contains("tid=MUM01001") == true)
        assertTrue(intent?.dataString?.contains("orgid=180001") == true)
        assertTrue(intent?.dataString?.contains("sign=MEYCIQC9876543210abcdef==") == true)

        // 4. Action Execution
        ActionUtils.openUpiPayment(context, realMerchantQr)
        val shadowApp = shadowOf(ApplicationProvider.getApplicationContext() as android.app.Application)
        val nextStartedIntent = shadowApp.nextStartedActivity
        assertNotNull(nextStartedIntent)
        val targetIntent = nextStartedIntent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertEquals(realMerchantQr, targetIntent?.dataString)
    }

    @Test
    fun realStaticCounterMerchantQr_correctlyIdentifiedAndDispatched() {
        val realStaticQr = "upi://pay?pa=paytm-68721@paytm&pn=Chai%20Point&mc=5812&mode=01"

        val detectedType = QrTypeDetector.detect(realStaticQr)
        assertEquals(QrTypeDetector.TYPE_PAYMENT, detectedType)

        val parsed = UpiPaymentParser.parse(realStaticQr)
        assertTrue(parsed.isValid)
        assertTrue(parsed.isMerchant)
        assertEquals("01", parsed.mode)
        assertEquals("5812", parsed.merchantCode)
        assertNull("Static counter QR has no pre-filled amount", parsed.amount)

        val intent = ActionUtils.buildUpiPaymentIntent(realStaticQr)
        assertNotNull(intent)
        assertEquals(realStaticQr, intent?.dataString)
    }

    @Test
    fun maliciousDuplicateParameterQr_rejectedWithoutDispatchingIntent() {
        // Attempted parameter collision / injection
        val maliciousQr = "upi://pay?pa=attacker@upi&pa=victim@upi&pn=Test&am=500.00"

        val parsed = UpiPaymentParser.parse(maliciousQr)
        assertFalse(parsed.isValid)
        assertTrue(parsed.validationError?.contains("duplicate 'pa' parameter") == true)

        val intent = ActionUtils.buildUpiPaymentIntent(maliciousQr)
        assertNull("Malicious ambiguous QR must return null intent", intent)

        val shadowApp = shadowOf(ApplicationProvider.getApplicationContext() as android.app.Application)
        shadowApp.clearNextStartedActivities()
        ActionUtils.openUpiPayment(context, maliciousQr)
        assertNull("No intent should be launched for invalid payment QR", shadowApp.nextStartedActivity)
    }
}


package com.scantrack.qr

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import com.scantrack.qr.data.parser.QrTypeDetector
import com.scantrack.qr.data.parser.UpiPaymentParser
import com.scantrack.qr.presentation.payment.PaymentLaunchController
import com.scantrack.qr.presentation.utils.ActionUtils
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Unit & Robolectric harness tests for UPI payload parsing, activity intent building,
 * and the [PaymentLaunchController] lifecycle contract.
 *
 * NOTE: These tests verify only client-side intent construction, caller flags,
 * single-in-flight debouncing, and result callbacks. They do NOT provide evidence
 * of bank-side acceptance, switch routing, or merchant acquirer onboarding.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpiPaymentFlowTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun mockPersonalP2PPayload_detectedParsedAndDispatchedWithoutNewTask() {
        val dummyP2PQr = "upi://pay?pa=dummy.user@okhdfcbank&pn=Dummy%20User&am=125.50&cu=INR&tn=Dinner%20Share"

        // 1. Detection
        val detectedType = QrTypeDetector.detect(dummyP2PQr)
        assertEquals(QrTypeDetector.TYPE_PAYMENT, detectedType)

        // 2. Label derivation
        val label = QrTypeDetector.deriveLabel(dummyP2PQr, detectedType)
        assertEquals("Pay: Dummy User", label)

        // 3. Strict Parsing
        val parsed = UpiPaymentParser.parse(dummyP2PQr)
        assertTrue("Expected valid UPI payload", parsed.isValid)
        assertFalse("Personal QR should not be flagged as merchant metadata", parsed.hasMerchantMetadata)
        assertEquals("dummy.user@okhdfcbank", parsed.payeeVpa)
        assertEquals("Dummy User", parsed.payeeName)
        assertEquals("125.50", parsed.amount)
        assertEquals("INR", parsed.currency)
        assertEquals("Dinner Share", parsed.transactionNote)
        assertEquals(dummyP2PQr, parsed.originalUri)

        // 4. Intent Construction - MUST NOT contain FLAG_ACTIVITY_NEW_TASK for activity result flow
        val intent = ActionUtils.buildUpiPaymentIntent(dummyP2PQr)
        assertNotNull(intent)
        assertEquals(Intent.ACTION_VIEW, intent?.action)
        assertEquals(dummyP2PQr, intent?.dataString)
        assertEquals(
            "Intent for Activity Result contract must not have FLAG_ACTIVITY_NEW_TASK",
            0,
            (intent?.flags ?: 0) and Intent.FLAG_ACTIVITY_NEW_TASK
        )
    }

    @Test
    fun mockMerchantDynamicPosPayload_preservesAllParametersWithoutNewTask() {
        val dummyMerchantQr = "upi://pay?pa=dummy.store@icici&pn=Dummy%20Store&mc=5411&tr=REC9876543210&tid=TERM001&mode=02&am=1499.00&cu=INR&orgid=180001&sign=MEYCIQC9876543210abcdef=="

        // 1. Detection
        val detectedType = QrTypeDetector.detect(dummyMerchantQr)
        assertEquals(QrTypeDetector.TYPE_PAYMENT, detectedType)

        // 2. Parsing with Merchant Metadata Identification
        val parsed = UpiPaymentParser.parse(dummyMerchantQr)
        assertTrue("Expected valid merchant UPI payload", parsed.isValid)
        assertTrue("Must indicate merchant metadata is present", parsed.hasMerchantMetadata)
        assertEquals("dummy.store@icici", parsed.payeeVpa)
        assertEquals("Dummy Store", parsed.payeeName)
        assertEquals("5411", parsed.merchantCode)
        assertEquals("REC9876543210", parsed.transactionRef)
        assertEquals("TERM001", parsed.terminalId)
        assertEquals("02", parsed.mode)
        assertEquals("180001", parsed.orgId)
        assertEquals("1499.00", parsed.amount)
        assertEquals("INR", parsed.currency)

        // 3. Complete byte-for-byte URI preservation in Intent
        val intent = ActionUtils.buildUpiPaymentIntent(dummyMerchantQr)
        assertNotNull(intent)
        assertEquals(dummyMerchantQr, intent?.dataString)
        assertTrue(intent?.dataString?.contains("mc=5411") == true)
        assertTrue(intent?.dataString?.contains("mode=02") == true)
        assertTrue(intent?.dataString?.contains("tid=TERM001") == true)
        assertTrue(intent?.dataString?.contains("orgid=180001") == true)
        assertTrue(intent?.dataString?.contains("sign=MEYCIQC9876543210abcdef==") == true)
        assertEquals(
            "Intent must not have FLAG_ACTIVITY_NEW_TASK",
            0,
            (intent?.flags ?: 0) and Intent.FLAG_ACTIVITY_NEW_TASK
        )
    }

    @Test
    fun mockStaticCounterMerchantPayload_correctlyIdentified() {
        val dummyStaticQr = "upi://pay?pa=dummy-counter@paytm&pn=Chai%20Point&mc=5812&mode=01"

        val detectedType = QrTypeDetector.detect(dummyStaticQr)
        assertEquals(QrTypeDetector.TYPE_PAYMENT, detectedType)

        val parsed = UpiPaymentParser.parse(dummyStaticQr)
        assertTrue(parsed.isValid)
        assertTrue(parsed.hasMerchantMetadata)
        assertEquals("01", parsed.mode)
        assertEquals("5812", parsed.merchantCode)
        assertNull("Static counter QR has no pre-filled amount", parsed.amount)

        val intent = ActionUtils.buildUpiPaymentIntent(dummyStaticQr)
        assertNotNull(intent)
        assertEquals(dummyStaticQr, intent?.dataString)
    }

    @Test
    fun maliciousDuplicateParameterPayload_rejectedWithoutDispatchingIntent() {
        val maliciousQr = "upi://pay?pa=attacker@upi&pa=victim@upi&pn=Test&am=500.00"

        val parsed = UpiPaymentParser.parse(maliciousQr)
        assertFalse(parsed.isValid)
        assertEquals("ERR_DUPLICATE_PARAM", parsed.errorCode)
        assertTrue(parsed.validationError?.contains("duplicate 'pa' parameter") == true)

        val intent = ActionUtils.buildUpiPaymentIntent(maliciousQr)
        assertNull("Malicious ambiguous QR must return null intent", intent)
    }

    @Test
    fun paymentLaunchController_singleInFlightGuard_preventsDuplicateDispatches() {
        val activityController = Robolectric.buildActivity(ComponentActivity::class.java)
        val activity = activityController.get()
        val controller = PaymentLaunchController(activity)
        controller.register()
        activityController.setup()

        val dummyQr = "upi://pay?pa=store@icici&pn=Test&am=10.00&cu=INR"

        // First tap succeeds
        val firstLaunch = controller.launchPayment(activity, dummyQr)
        assertTrue("First launch should succeed", firstLaunch)
        assertTrue("Controller must be in-flight", controller.isPaymentInFlight.value)

        // Second immediate tap is blocked by single-in-flight guard
        val secondLaunch = controller.launchPayment(activity, dummyQr)
        assertFalse("Second launch must be blocked while payment is in-flight", secondLaunch)

        // Reset in-flight
        controller.resetInFlight()
        assertFalse("Controller in-flight should be false after reset", controller.isPaymentInFlight.value)
    }

    @Test
    fun paymentLaunchController_explicitTargetPackage_routesDirectlyWithoutChooser() {
        val activityController = Robolectric.buildActivity(ComponentActivity::class.java)
        val activity = activityController.get()
        val controller = PaymentLaunchController(activity)
        controller.register()
        activityController.setup()

        val dummyQr = "upi://pay?pa=merchant@icici&pn=Test&am=50.00&cu=INR"
        val explicitPsp = "com.google.android.apps.nbu.paisa.user"

        val launched = controller.launchPayment(activity, dummyQr, targetPackage = explicitPsp)
        assertTrue(launched)

        val shadowActivity = shadowOf(activity)
        val nextIntent = shadowActivity.nextStartedActivityForResult?.intent
        assertNotNull("Expected intent launched for result", nextIntent)
        assertEquals(Intent.ACTION_VIEW, nextIntent?.action)
        assertEquals(explicitPsp, nextIntent?.`package`)
        assertEquals(dummyQr, nextIntent?.dataString)
    }
}

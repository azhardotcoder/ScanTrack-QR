package com.scantrack.qr

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scantrack.qr.data.parser.UpiPaymentParser
import com.scantrack.qr.presentation.payment.PaymentLaunchController
import com.scantrack.qr.presentation.utils.ActionUtils
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation tests verifying the UPI payment launch contract on an actual Android runtime.
 *
 * NOTE: Uses purely dummy, non-functional test payloads. These tests verify only Android IPC
 * contracts, URI fidelity, single-in-flight guards, and result delivery. They do NOT test or
 * imply actual bank/PSP settlement.
 */
@RunWith(AndroidJUnit4::class)
class UpiPaymentLaunchInstrumentedTest {

    private val dummyPaymentUri = "upi://pay?pa=testdummy@icici&pn=DummyTest&mc=5411&mode=02&am=1.00&cu=INR"

    @Test
    fun receivedUriMatchesIntendedUri_byteForByte() {
        val parsed = UpiPaymentParser.parse(dummyPaymentUri)
        assertTrue(parsed.isValid)
        assertEquals(dummyPaymentUri, parsed.originalUri)

        val intent = ActionUtils.buildUpiPaymentIntent(dummyPaymentUri)
        assertNotNull(intent)
        assertEquals(Intent.ACTION_VIEW, intent?.action)
        assertEquals(dummyPaymentUri, intent?.dataString)
        assertEquals(Uri.parse(dummyPaymentUri), intent?.data)
    }

    @Test
    fun paymentIntentDoesNotForceNewTaskFlag_forSupportedActivityFlow() {
        val intent = ActionUtils.buildUpiPaymentIntent(dummyPaymentUri)
        assertNotNull(intent)

        // For Activity Result flows, FLAG_ACTIVITY_NEW_TASK must NOT be forced,
        // so that the target PSP activity can verify caller identity via getCallingPackage().
        val hasNewTask = ((intent?.flags ?: 0) and Intent.FLAG_ACTIVITY_NEW_TASK) != 0
        assertFalse("Payment intent must not have FLAG_ACTIVITY_NEW_TASK in activity flow", hasNewTask)
    }

    @Test
    fun chooserIntentDoesNotForceNewTaskFlag() {
        val paymentIntent = ActionUtils.buildUpiPaymentIntent(dummyPaymentUri)!!
        val chooserIntent = ActionUtils.buildUpiChooserIntent(paymentIntent, "Choose Payment App")

        val hasNewTask = (chooserIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0
        assertFalse("Chooser intent must not force FLAG_ACTIVITY_NEW_TASK", hasNewTask)
    }

    @Test
    fun singleInFlightGuard_preventsDuplicatePaymentLaunches() {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            val controller = PaymentLaunchController(activity)
            controller.register()

            // First launch
            val first = controller.launchPayment(activity, dummyPaymentUri)
            assertTrue("First launch attempt should succeed", first)
            assertTrue("Controller state must be in-flight", controller.isPaymentInFlight.value)

            // Repeated tap while in-flight
            val second = controller.launchPayment(activity, dummyPaymentUri)
            assertFalse("Second launch attempt must be blocked by single-in-flight guard", second)

            // Cancellation / Return resets in-flight state
            controller.resetInFlight()
            assertFalse("Controller state must reset after completion/cancellation", controller.isPaymentInFlight.value)
        }
        scenario.close()
    }

    @Test
    fun missingHandlerIsHandledGracefullyWithoutCrashing() {
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { activity ->
            val controller = PaymentLaunchController(activity)
            controller.register()

            // Attempt to route to a guaranteed non-existent package
            val launched = controller.launchPayment(
                context = activity,
                originalUpiUri = dummyPaymentUri,
                targetPackage = "com.nonexistent.fake.psp.app"
            )
            // Must catch ActivityNotFoundException, return false, and reset in-flight state
            assertFalse("Launch to missing package should return false", launched)
            assertFalse("In-flight state must be cleared after exception", controller.isPaymentInFlight.value)
        }
        scenario.close()
    }
}


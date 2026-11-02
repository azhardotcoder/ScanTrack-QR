package com.scantrack.qr.presentation.payment

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.scantrack.qr.data.diagnostics.PaymentDiagnostics
import com.scantrack.qr.data.parser.UpiPaymentParser
import com.scantrack.qr.presentation.utils.ActionUtils

/**
 * Lifecycle-aware controller that manages UPI payment launches through the Android Activity Result API.
 *
 * Rules:
 * 1. Registered strictly in a stable lifecycle owner ([ComponentActivity]), never in a transient bottom sheet.
 * 2. Removes forced FLAG_ACTIVITY_NEW_TASK, preserving caller identity ([ComponentActivity.getCallingPackage])
 *    and result delivery per NPCI specification.
 * 3. Enforces a single-in-flight guard against double-taps.
 * 4. Never claims payment success merely on client-supplied status or RESULT_OK.
 * 5. Does not automatically retry or reopen the payment app.
 */
class PaymentLaunchController(private val activity: ComponentActivity) {

    private var launcher: ActivityResultLauncher<Intent>? = null

    private val _isPaymentInFlight = mutableStateOf(false)
    val isPaymentInFlight: State<Boolean> = _isPaymentInFlight

    /**
     * Must be called in [ComponentActivity.onCreate] before the activity reaches RESUMED.
     */
    fun register() {
        launcher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            handlePaymentResult(result.resultCode, result.data)
        }
    }

    /**
     * Launches a UPI payment intent.
     *
     * @param originalUpiUri The exact, unmodified scanned UPI URI.
     * @param targetPackage Optional explicit package (e.g., for controlled test routing). If null, launches chooser.
     * @return true if dispatch was initiated; false if blocked by single-in-flight guard or validation failure.
     */
    fun launchPayment(
        context: Context,
        originalUpiUri: String,
        targetPackage: String? = null
    ): Boolean {
        // Single-in-flight guard: prevent duplicate launches on rapid consecutive taps
        if (_isPaymentInFlight.value) {
            return false
        }

        val upiData = UpiPaymentParser.parse(originalUpiUri)
        if (!upiData.isValid) {
            PaymentDiagnostics.recordValidationError(upiData.errorCode ?: "ERR_INVALID_PAYLOAD")
            Toast.makeText(
                context,
                upiData.validationError ?: "Invalid UPI payment format",
                Toast.LENGTH_LONG
            ).show()
            return false
        }

        val intentLauncher = launcher
        if (intentLauncher == null) {
            Toast.makeText(context, "Payment launcher is not initialized", Toast.LENGTH_SHORT).show()
            return false
        }

        val targetIntent = ActionUtils.buildUpiPaymentIntent(originalUpiUri, targetPackage)
        if (targetIntent == null) {
            Toast.makeText(context, "Failed to construct payment intent", Toast.LENGTH_SHORT).show()
            return false
        }

        val intentToLaunch = if (targetPackage != null) {
            targetIntent
        } else {
            ActionUtils.buildUpiChooserIntent(targetIntent)
        }

        PaymentDiagnostics.recordDispatch(
            context = context,
            upiData = upiData,
            targetPackage = targetPackage,
            flags = intentToLaunch.flags
        )

        _isPaymentInFlight.value = true

        return try {
            intentLauncher.launch(intentToLaunch)
            Toast.makeText(
                context,
                "Opening payment app... Complete transaction in your UPI app",
                Toast.LENGTH_SHORT
            ).show()
            true
        } catch (e: ActivityNotFoundException) {
            _isPaymentInFlight.value = false
            Toast.makeText(
                context,
                "No UPI payment application found on this device",
                Toast.LENGTH_LONG
            ).show()
            false
        } catch (e: Exception) {
            _isPaymentInFlight.value = false
            Toast.makeText(
                context,
                "Failed to launch payment app: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
            false
        }
    }

    private fun handlePaymentResult(resultCode: Int, data: Intent?) {
        _isPaymentInFlight.value = false
        PaymentDiagnostics.recordResult(resultCode, data)

        when (resultCode) {
            Activity.RESULT_OK -> {
                // Notice: RESULT_OK does NOT mean funds were transferred.
                // It only indicates the payment application finished and returned control.
                Toast.makeText(
                    activity,
                    "Returned from payment app. Please verify payment status in your UPI app or bank SMS.",
                    Toast.LENGTH_LONG
                ).show()
            }
            Activity.RESULT_CANCELED -> {
                Toast.makeText(
                    activity,
                    "Payment was cancelled or dismissed in the payment app.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            else -> {
                Toast.makeText(
                    activity,
                    "Returned from payment app (code $resultCode). Check your UPI app for details.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Resets the in-flight flag if needed (e.g. on configuration recreation or test cleanup).
     */
    fun resetInFlight() {
        _isPaymentInFlight.value = false
    }
}

val LocalPaymentController = staticCompositionLocalOf<PaymentLaunchController?> { null }


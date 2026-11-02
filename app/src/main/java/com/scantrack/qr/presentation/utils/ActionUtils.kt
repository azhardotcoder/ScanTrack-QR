package com.scantrack.qr.presentation.utils

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.scantrack.qr.data.parser.QrTypeDetector
import com.scantrack.qr.data.parser.UpiPaymentParser
import com.scantrack.qr.data.parser.WiFiQrParser

object ActionUtils {

    private const val TAG_UPI = "ScanTrack_UPI"

    /**
     * Unified handler to safely dispatch actions based on the detected QR type.
     * All actions require explicit user intent and never execute untrusted scripts or modify payment payloads.
     */
    fun openQrContent(context: Context, rawValue: String) {
        val trimmed = rawValue.trim()
        if (trimmed.isEmpty()) return

        val type = QrTypeDetector.detect(trimmed)

        try {
            when (type) {
                QrTypeDetector.TYPE_PAYMENT -> openUpiPayment(context, trimmed)
                QrTypeDetector.TYPE_WIFI -> connectToWifi(context, trimmed)
                QrTypeDetector.TYPE_URL -> openWebUrl(context, trimmed)
                QrTypeDetector.TYPE_PHONE -> openDialer(context, trimmed)
                QrTypeDetector.TYPE_EMAIL -> openEmail(context, trimmed)
                else -> shareText(context, trimmed)
            }
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No application found to handle this content", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(context, "Permission denied by system", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Error: ${e.localizedMessage ?: "Unknown error"}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Builds and validates the explicit Intent.ACTION_VIEW payment intent for a scanned UPI URI.
     * Preserves the original URI completely without rewriting, replacing, or fabricating fields.
     * Does NOT force FLAG_ACTIVITY_NEW_TASK for activity-based / ActivityResult flows.
     *
     * @param originalUpiUri The raw scanned UPI URI.
     * @param targetPackage Optional explicit package (e.g., for controlled test routing).
     * @return Intent ready for dispatch, or null if validation fails.
     */
    fun buildUpiPaymentIntent(originalUpiUri: String, targetPackage: String? = null): Intent? {
        val upiData = UpiPaymentParser.parse(originalUpiUri)
        if (!upiData.isValid) return null
        return Intent(Intent.ACTION_VIEW, Uri.parse(originalUpiUri)).apply {
            if (!targetPackage.isNullOrBlank()) {
                setPackage(targetPackage)
            }
        }
    }

    /**
     * Builds a chooser intent targeting UPI payment applications without forcing FLAG_ACTIVITY_NEW_TASK.
     */
    fun buildUpiChooserIntent(paymentIntent: Intent, title: CharSequence = "Pay via UPI"): Intent {
        return Intent.createChooser(paymentIntent, title)
    }

    /**
     * Opens UPI Payment strictly preserving the original scanned URI.
     * Never removes merchant codes (mc), mode, or transaction references (tr),
     * and never converts merchant payments to P2P. Rejects ambiguous duplicate instructions.
     */
    fun openUpiPayment(context: Context, originalUpiUri: String, targetPackage: String? = null) {
        val upiData = UpiPaymentParser.parse(originalUpiUri)
        if (!upiData.isValid) {
            com.scantrack.qr.data.diagnostics.PaymentDiagnostics.recordValidationError(upiData.errorCode ?: "ERR_INVALID_PAYLOAD")
            Toast.makeText(
                context,
                upiData.validationError ?: "Invalid UPI payment format",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        try {
            // Preserve the original payment URI completely
            val paymentIntent = buildUpiPaymentIntent(originalUpiUri, targetPackage) ?: return
            val intentToLaunch = if (!targetPackage.isNullOrBlank()) {
                paymentIntent
            } else {
                buildUpiChooserIntent(paymentIntent)
            }

            // In Android, starting an activity from a non-Activity context requires FLAG_ACTIVITY_NEW_TASK.
            // For Activity contexts, we strictly omit it to preserve caller identity and result delivery.
            if (context !is Activity) {
                intentToLaunch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            com.scantrack.qr.data.diagnostics.PaymentDiagnostics.recordDispatch(
                context = context,
                upiData = upiData,
                targetPackage = targetPackage,
                flags = intentToLaunch.flags
            )

            context.startActivity(intentToLaunch)

            // Authoritative confirmation notice: Inform user to complete payment in external app
            Toast.makeText(
                context,
                "Opening payment app... Complete transaction in your UPI app",
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG_UPI, "No UPI payment application installed on device")
            Toast.makeText(context, "No UPI payment app found (e.g. Google Pay, PhonePe, Paytm)", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG_UPI, "Failed to launch payment app: ${e.message}")
            Toast.makeText(context, "Failed to launch payment app: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openWebUrl(context: Context, url: String) {
        val uri = Uri.parse(url)
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            Toast.makeText(context, "Only web links (http/https) are supported", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun openDialer(context: Context, phoneValue: String) {
        val uri = if (phoneValue.startsWith("tel:", ignoreCase = true)) {
            Uri.parse(phoneValue)
        } else {
            Uri.parse("tel:$phoneValue")
        }
        val intent = Intent(Intent.ACTION_DIAL, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun openEmail(context: Context, emailValue: String) {
        val uri = if (emailValue.startsWith("mailto:", ignoreCase = true)) {
            Uri.parse(emailValue)
        } else {
            Uri.parse("mailto:$emailValue")
        }
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun connectToWifi(context: Context, rawValue: String) {
        val wifiData = WiFiQrParser.parse(rawValue)
        if (!wifiData.isValid) {
            Toast.makeText(context, wifiData.validationError ?: "Invalid Wi-Fi QR code", Toast.LENGTH_LONG).show()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            WifiConnectionManager.connect(context, wifiData) { statusMessage ->
                Toast.makeText(context, statusMessage, Toast.LENGTH_SHORT).show()
            }
        } else {
            // Legacy Android: copy password and direct user to Settings
            copyToClipboard(context, "Wi-Fi Password", wifiData.password)
            Toast.makeText(
                context,
                "Network: ${wifiData.ssid}\nPassword copied. Connect via Wi-Fi Settings.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Explicit disconnect/cancellation of active Wi-Fi connection request.
     */
    fun disconnectWifi(context: Context) {
        WifiConnectionManager.disconnect(context)
    }

    fun copyToClipboard(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    fun shareText(context: Context, text: String) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Share QR Content")
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(shareIntent)
    }
}

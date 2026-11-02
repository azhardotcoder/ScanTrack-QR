package com.scantrack.qr.presentation.utils

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
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.scantrack.qr.data.parser.QrTypeDetector
import com.scantrack.qr.data.parser.UpiPaymentParser
import com.scantrack.qr.data.parser.WiFiQrParser

object ActionUtils {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeWifiCallback: ConnectivityManager.NetworkCallback? = null
    private var activeTimeoutRunnable: Runnable? = null

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
     * Opens UPI Payment strictly preserving the original scanned URI.
     * Never removes merchant codes (mc), mode, or transaction references (tr),
     * and never converts merchant payments to P2P. Rejects ambiguous duplicate instructions.
     */
    fun openUpiPayment(context: Context, originalUpiUri: String) {
        val upiData = UpiPaymentParser.parse(originalUpiUri)
        if (!upiData.isValid) {
            Toast.makeText(
                context,
                upiData.validationError ?: "Invalid UPI payment format",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        try {
            // Preserve the original payment URI completely
            val paymentIntent = Intent(Intent.ACTION_VIEW, Uri.parse(originalUpiUri)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(paymentIntent, "Pay via UPI")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No UPI payment app found (e.g. Google Pay, PhonePe, Paytm)", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
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
            connectToWifiApi29(context, wifiData)
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

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun connectToWifiApi29(context: Context, wifiData: com.scantrack.qr.data.parser.WiFiNetworkData) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Clean up any previously pending Wi-Fi connection request
        cleanupActiveWifiRequest(connectivityManager)

        try {
            val specifierBuilder = WifiNetworkSpecifier.Builder()
                .setSsid(wifiData.ssid)

            if (wifiData.isHidden) {
                specifierBuilder.setIsHiddenSsid(true)
            }

            when (wifiData.security) {
                "WPA" -> {
                    if (wifiData.password.isNotEmpty()) {
                        specifierBuilder.setWpa2Passphrase(wifiData.password)
                    }
                }
                "WPA3" -> {
                    if (wifiData.password.isNotEmpty()) {
                        specifierBuilder.setWpa3Passphrase(wifiData.password)
                    }
                }
                "nopass" -> {
                    // Open network - no passphrase needed
                }
                else -> {
                    Toast.makeText(context, "Unsupported Wi-Fi security type: ${wifiData.security}", Toast.LENGTH_SHORT).show()
                    return
                }
            }

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifierBuilder.build())
                .build()

            var isConnectionEstablished = false

            val networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    isConnectionEstablished = true

                    // Cancel pending timeout: KEEP the Wi-Fi connection active!
                    activeTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
                    activeTimeoutRunnable = null

                    mainHandler.post {
                        Toast.makeText(context, "Connected to ${wifiData.ssid}", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onUnavailable() {
                    super.onUnavailable()
                    cleanupActiveWifiRequest(connectivityManager)
                    mainHandler.post {
                        Toast.makeText(context, "Could not connect to ${wifiData.ssid}", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onLost(network: Network) {
                    super.onLost(network)
                    cleanupActiveWifiRequest(connectivityManager)
                    mainHandler.post {
                        Toast.makeText(context, "Disconnected from ${wifiData.ssid}", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            activeWifiCallback = networkCallback

            // Safe connection attempt timeout: 30 seconds only if NOT connected yet
            val timeoutRunnable = Runnable {
                if (!isConnectionEstablished && activeWifiCallback === networkCallback) {
                    cleanupActiveWifiRequest(connectivityManager)
                    Toast.makeText(context, "Wi-Fi connection attempt timed out", Toast.LENGTH_SHORT).show()
                }
            }
            activeTimeoutRunnable = timeoutRunnable
            mainHandler.postDelayed(timeoutRunnable, 30_000L)

            connectivityManager.requestNetwork(request, networkCallback)
            Toast.makeText(context, "Requesting connection to ${wifiData.ssid}...", Toast.LENGTH_SHORT).show()

        } catch (e: SecurityException) {
            cleanupActiveWifiRequest(connectivityManager)
            Toast.makeText(context, "Wi-Fi network request permission denied by system", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            cleanupActiveWifiRequest(connectivityManager)
            Toast.makeText(context, "Wi-Fi connection failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cleanupActiveWifiRequest(connectivityManager: ConnectivityManager) {
        activeTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        activeTimeoutRunnable = null
        activeWifiCallback?.let { callback ->
            try {
                connectivityManager.unregisterNetworkCallback(callback)
            } catch (ignored: Exception) {
            }
        }
        activeWifiCallback = null
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

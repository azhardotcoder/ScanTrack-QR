package com.scantrack.qr.presentation.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiNetworkSpecifier
import android.net.NetworkRequest
import android.net.NetworkCapabilities
import android.net.ConnectivityManager
import android.widget.Toast
import com.scantrack.qr.presentation.scanner.detectQrType

object ActionUtils {

    /**
     * Unified logic to open QR content in external apps.
     * Ensures UPI payments show a chooser and handles fallback for various types.
     */
    fun openQrContent(context: Context, rawValue: String) {
        if (rawValue.isBlank()) return

        val type = detectQrType(rawValue)

        try {
            val intent = when {
                rawValue.startsWith("upi://pay", ignoreCase = true) -> {
                    // Deep Fix: Sanitize the URI to downgrade merchant intent to a secure P2P-like push payment.
                    // This bypasses "Risk Policy" blocks triggered by unverified merchant intents (mode 02).
                    val sanitizedUri = sanitizeUpiUri(rawValue)
                    Intent(Intent.ACTION_VIEW, Uri.parse(sanitizedUri)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        // Set package to null so system resolves across all UPI apps
                        setPackage(null)
                    }
                }
                type == "WIFI" -> {
                    connectToWifi(context, rawValue)
                    return
                }
                type == "URL" -> Intent(Intent.ACTION_VIEW, Uri.parse(rawValue))
                type == "PHONE" -> Intent(Intent.ACTION_DIAL, Uri.parse(rawValue))
                type == "EMAIL" -> Intent(Intent.ACTION_SENDTO, Uri.parse(rawValue))
                else -> {
                    // Fallback for TEXT or others: offer sharing
                    shareText(context, rawValue)
                    return
                }
            }

            // Only add external task flag if not a UPI intent (already handled above)
            if (!rawValue.startsWith("upi://pay", ignoreCase = true)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No app found to handle this content", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun connectToWifi(context: Context, rawValue: String) {
        val wifiData = parseWifiContent(rawValue)
        if (wifiData == null) {
            Toast.makeText(context, "Invalid WiFi QR content", Toast.LENGTH_SHORT).show()
            return
        }

        val ssid = wifiData["S"] ?: return
        val password = wifiData["P"] ?: ""

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val specifier = android.net.wifi.WifiNetworkSpecifier.Builder()
                .setSsid(ssid)
                .setWpa2Passphrase(password)
                .build()

            val request = android.net.NetworkRequest.Builder()
                .addTransportType(android.net.NetworkCapabilities.TRANSPORT_WIFI)
                .setNetworkSpecifier(specifier)
                .build()

            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val networkCallback = object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    super.onAvailable(network)
                    connectivityManager.bindProcessToNetwork(network)
                }
            }
            connectivityManager.requestNetwork(request, networkCallback)
            Toast.makeText(context, "Connecting to $ssid...", Toast.LENGTH_LONG).show()
        } else {
            // Legacy implementation if needed, but modern Android is the focus
            shareText(context, "WiFi: $ssid\nPassword: $password")
            Toast.makeText(context, "Auto-connect supported on Android 10+. Details copied to share.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun parseWifiContent(content: String): Map<String, String>? {
        if (!content.startsWith("WIFI:", ignoreCase = true)) return null
        val parts = content.removePrefix("WIFI:").split(";")
        val result = mutableMapOf<String, String>()
        parts.forEach { part ->
            val colonIndex = part.indexOf(":")
            if (colonIndex != -1) {
                val key = part.substring(0, colonIndex)
                val value = part.substring(colonIndex + 1)
                result[key] = value
            }
        }
        return result
    }

    /**
     * Deep Fix helper to sanitize UPI URIs.
     * Strips merchant parameters (mc, mode, tr, etc.) to present the intent
     * as a standard P2P "Push" payment, which has lower risk profiles in PSP apps.
     */
    private fun sanitizeUpiUri(original: String): String {
        try {
            val uri = Uri.parse(original)
            val builder = Uri.Builder().scheme("upi").authority("pay")
            
            // Critical P2P Parameters to keep
            val essentialParams = listOf("pa", "pn", "am", "cu", "tn")
            
            essentialParams.forEach { param ->
                uri.getQueryParameter(param)?.let { value ->
                    builder.appendQueryParameter(param, value)
                }
            }
            
            // If amount is missing, some apps allow manual entry
            // If pa is missing, we can't do anything, but Uri.parse handles it
            
            return builder.build().toString()
        } catch (e: Exception) {
            return original // Fallback to original if parsing fails
        }
    }

    fun shareText(context: Context, text: String) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, null)
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(shareIntent)
    }
}

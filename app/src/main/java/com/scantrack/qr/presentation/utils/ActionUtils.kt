package com.scantrack.qr.presentation.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
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
                    val baseIntent = Intent(Intent.ACTION_VIEW, Uri.parse(rawValue))
                    Intent.createChooser(baseIntent, "Pay with")
                }
                type == "URL" -> Intent(Intent.ACTION_VIEW, Uri.parse(rawValue))
                type == "PHONE" -> Intent(Intent.ACTION_DIAL, Uri.parse(rawValue))
                type == "EMAIL" -> Intent(Intent.ACTION_SENDTO, Uri.parse(rawValue))
                else -> {
                    // Fallback for TEXT, WIFI or others: offer sharing
                    shareText(context, rawValue)
                    return
                }
            }

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No app found to handle this content", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
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

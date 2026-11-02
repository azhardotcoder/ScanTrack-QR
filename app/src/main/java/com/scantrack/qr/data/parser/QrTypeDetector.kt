package com.scantrack.qr.data.parser

import java.net.URI

object QrTypeDetector {

    const val TYPE_URL = "URL"
    const val TYPE_PAYMENT = "PAYMENT"
    const val TYPE_WIFI = "WIFI"
    const val TYPE_EMAIL = "EMAIL"
    const val TYPE_PHONE = "PHONE"
    const val TYPE_TEXT = "TEXT"

    /**
     * Determines the semantic type of a scanned QR payload safely.
     * Dangerous pseudo-schemes (e.g. javascript:, file:, data:, content:) are rejected from URL classification
     * and treated strictly as plain text to avoid arbitrary code execution or local file access.
     */
    fun detect(rawValue: String): String {
        val trimmed = rawValue.trim()
        if (trimmed.isEmpty()) return TYPE_TEXT

        val lower = trimmed.lowercase()

        // UPI Payment
        if (lower.startsWith("upi://pay") || lower.startsWith("upi:pay")) {
            return TYPE_PAYMENT
        }

        // Wi-Fi
        if (lower.startsWith("wifi:")) {
            return TYPE_WIFI
        }

        // Email
        if (lower.startsWith("mailto:")) {
            return TYPE_EMAIL
        }

        // Phone
        if (lower.startsWith("tel:")) {
            return TYPE_PHONE
        }

        // Web URL: strictly validate http:// and https://
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return try {
                val uri = URI(trimmed)
                val scheme = uri.scheme?.lowercase()
                val host = uri.host
                if ((scheme == "http" || scheme == "https") && !host.isNullOrBlank()) {
                    TYPE_URL
                } else {
                    TYPE_TEXT
                }
            } catch (e: Exception) {
                // If standard URI parse fails due to unencoded chars, perform safe structural check
                val afterScheme = if (lower.startsWith("https://")) trimmed.substring(8) else trimmed.substring(7)
                val hostCandidate = afterScheme.substringBefore('/').substringBefore('?')
                if (hostCandidate.contains('.') && !hostCandidate.contains(' ')) {
                    TYPE_URL
                } else {
                    TYPE_TEXT
                }
            }
        }

        return TYPE_TEXT
    }

    /**
     * Derives a clean, readable label for display in history and headers.
     */
    fun deriveLabel(rawValue: String, type: String): String {
        val trimmed = rawValue.trim()
        return when (type) {
            TYPE_URL -> {
                try {
                    val uri = URI(trimmed)
                    val host = uri.host ?: trimmed
                    host.removePrefix("www.").take(40)
                } catch (e: Exception) {
                    trimmed.removePrefix("https://").removePrefix("http://")
                        .substringBefore("/").substringBefore("?").removePrefix("www.").take(40)
                }
            }
            TYPE_PAYMENT -> {
                val upi = UpiPaymentParser.parse(trimmed)
                when {
                    !upi.payeeName.isNullOrBlank() -> "Pay: ${upi.payeeName}"
                    !upi.payeeVpa.isNullOrBlank() -> "UPI: ${upi.payeeVpa}"
                    else -> "UPI Payment"
                }
            }
            TYPE_WIFI -> {
                val wifi = WiFiQrParser.parse(trimmed)
                if (wifi.ssid.isNotBlank()) "Wi-Fi: ${wifi.ssid}" else "Wi-Fi Network"
            }
            TYPE_EMAIL -> {
                val email = trimmed.removePrefix("mailto:").removePrefix("MAILTO:").substringBefore("?")
                if (email.isNotBlank()) "Email: $email" else "Email"
            }
            TYPE_PHONE -> {
                val phone = trimmed.removePrefix("tel:").removePrefix("TEL:").take(30)
                if (phone.isNotBlank()) "Call: $phone" else "Phone Number"
            }
            else -> trimmed.lines().firstOrNull()?.take(40) ?: "Text Scan"
        }
    }
}

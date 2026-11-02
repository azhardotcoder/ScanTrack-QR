package com.scantrack.qr.data.parser

import java.net.URLDecoder

/**
 * Structured representation of a UPI payment URI payload.
 *
 * Adheres strictly to the NPCI Unified Payment Interface specification.
 * Preserves the original URI without stripping or downgrading merchant parameters (mc, mode, tr).
 */
data class UpiPaymentData(
    val originalUri: String,
    val payeeVpa: String? = null,
    val payeeName: String? = null,
    val amount: String? = null,
    val currency: String? = null,
    val transactionRef: String? = null,
    val merchantCode: String? = null,
    val transactionNote: String? = null,
    val isValid: Boolean = false,
    val validationError: String? = null
)

object UpiPaymentParser {

    /**
     * Parses a UPI URI string strictly, validating the scheme and extracting metadata
     * while guaranteeing that the raw URI is preserved in its entirety.
     */
    fun parse(rawUri: String): UpiPaymentData {
        val trimmed = rawUri.trim()
        if (trimmed.isBlank()) {
            return UpiPaymentData(
                originalUri = rawUri,
                isValid = false,
                validationError = "Empty UPI payload"
            )
        }

        // Scheme validation
        val isUpiScheme = trimmed.startsWith("upi://pay", ignoreCase = true) || trimmed.startsWith("upi:pay", ignoreCase = true)
        if (!isUpiScheme) {
            return UpiPaymentData(
                originalUri = rawUri,
                isValid = false,
                validationError = "Unsupported payment scheme: expected upi://pay"
            )
        }

        return try {
            val queryIndex = trimmed.indexOf('?')
            val queryString = if (queryIndex != -1 && queryIndex < trimmed.length - 1) {
                trimmed.substring(queryIndex + 1)
            } else {
                ""
            }

            val queryParams = mutableMapOf<String, String>()
            if (queryString.isNotBlank()) {
                queryString.split('&').forEach { pair ->
                    val eqIndex = pair.indexOf('=')
                    if (eqIndex != -1) {
                        val key = pair.substring(0, eqIndex).trim()
                        val rawValue = pair.substring(eqIndex + 1)
                        val decodedValue = try {
                            URLDecoder.decode(rawValue, "UTF-8")
                        } catch (e: Exception) {
                            rawValue
                        }
                        // Keep first occurrence of parameter
                        if (!queryParams.containsKey(key)) {
                            queryParams[key] = decodedValue.trim()
                        }
                    }
                }
            }

            val pa = queryParams["pa"]
            val pn = queryParams["pn"]
            val am = queryParams["am"]
            val cu = queryParams["cu"] ?: "INR"
            val tr = queryParams["tr"]
            val mc = queryParams["mc"]
            val tn = queryParams["tn"]

            // VPA (Virtual Private Address) validation: must contain '@' and no spaces
            val isValidVpa = !pa.isNullOrBlank() && pa.contains("@") && !pa.contains(" ")

            if (!isValidVpa) {
                return UpiPaymentData(
                    originalUri = rawUri,
                    payeeVpa = pa,
                    payeeName = pn,
                    amount = am,
                    currency = cu,
                    transactionRef = tr,
                    merchantCode = mc,
                    transactionNote = tn,
                    isValid = false,
                    validationError = if (pa.isNullOrBlank()) "Missing payee UPI ID (pa)" else "Malformed payee UPI ID"
                )
            }

            // Amount validation (if present, must be a positive number)
            val isAmountValid = am.isNullOrBlank() || (am.toDoubleOrNull()?.let { it > 0 } == true)
            if (!isAmountValid) {
                return UpiPaymentData(
                    originalUri = rawUri,
                    payeeVpa = pa,
                    payeeName = pn,
                    amount = am,
                    currency = cu,
                    transactionRef = tr,
                    merchantCode = mc,
                    transactionNote = tn,
                    isValid = false,
                    validationError = "Invalid transaction amount"
                )
            }

            UpiPaymentData(
                originalUri = rawUri,
                payeeVpa = pa,
                payeeName = pn,
                amount = am,
                currency = cu,
                transactionRef = tr,
                merchantCode = mc,
                transactionNote = tn,
                isValid = true
            )
        } catch (e: Exception) {
            UpiPaymentData(
                originalUri = rawUri,
                isValid = false,
                validationError = "Failed to parse URI: ${e.message}"
            )
        }
    }
}

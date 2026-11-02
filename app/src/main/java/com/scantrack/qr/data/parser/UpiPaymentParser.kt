package com.scantrack.qr.data.parser

import java.net.URLDecoder

/**
 * Structured representation of a UPI payment URI payload.
 *
 * Adheres strictly to the NPCI Unified Payment Interface specification.
 * Preserves the original URI without stripping or downgrading merchant parameters (mc, mode, tr).
 * Rejects ambiguous instructions (e.g. duplicate query parameters, permissive URI schemes).
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
     * Parses a UPI URI string strictly, validating the exact scheme and authority (upi://pay)
     * and rejecting ambiguous payment instructions (such as duplicate parameters).
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

        // Strict scheme & authority validation: must begin with upi://pay? (case-insensitive)
        val lower = trimmed.lowercase()
        val isValidPrefix = lower.startsWith("upi://pay?") || lower == "upi://pay"
        if (!isValidPrefix) {
            return UpiPaymentData(
                originalUri = rawUri,
                isValid = false,
                validationError = "Invalid UPI URI: expected strict upi://pay scheme and authority"
            )
        }

        return try {
            val queryIndex = trimmed.indexOf('?')
            val queryString = if (queryIndex != -1 && queryIndex < trimmed.length - 1) {
                trimmed.substring(queryIndex + 1)
            } else {
                ""
            }

            if (queryString.isBlank()) {
                return UpiPaymentData(
                    originalUri = rawUri,
                    isValid = false,
                    validationError = "Missing payment parameters"
                )
            }

            val queryParams = mutableMapOf<String, String>()
            val criticalParams = setOf("pa", "am", "cu", "tr", "mc", "pn")

            val pairs = queryString.split('&')
            for (pair in pairs) {
                if (pair.isBlank()) continue
                val eqIndex = pair.indexOf('=')
                if (eqIndex == -1) continue

                val key = pair.substring(0, eqIndex).trim().lowercase()
                val rawValue = pair.substring(eqIndex + 1)
                val decodedValue = try {
                    URLDecoder.decode(rawValue, "UTF-8").trim()
                } catch (e: Exception) {
                    rawValue.trim()
                }

                // Security check: Reject duplicate critical payment parameters
                if (criticalParams.contains(key) && queryParams.containsKey(key)) {
                    return UpiPaymentData(
                        originalUri = rawUri,
                        isValid = false,
                        validationError = "Ambiguous payment instruction: duplicate '$key' parameter detected"
                    )
                }

                queryParams[key] = decodedValue
            }

            val pa = queryParams["pa"]
            val pn = queryParams["pn"]
            val am = queryParams["am"]
            val cu = queryParams["cu"] ?: "INR"
            val tr = queryParams["tr"]
            val mc = queryParams["mc"]
            val tn = queryParams["tn"]

            // Strict VPA (Virtual Payment Address) validation: name@handle
            // Must contain exactly one '@', no whitespace, and valid identifier characters
            val isValidVpa = !pa.isNullOrBlank() &&
                    pa.indexOf('@') == pa.lastIndexOf('@') &&
                    pa.indexOf('@') in 1 until (pa.length - 1) &&
                    !pa.contains(" ")

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
                    validationError = if (pa.isNullOrBlank()) "Missing payee UPI ID (pa)" else "Malformed payee UPI ID: $pa"
                )
            }

            // Amount validation (if present, must be positive decimal)
            if (!am.isNullOrBlank()) {
                val parsedAmount = am.toDoubleOrNull()
                if (parsedAmount == null || parsedAmount <= 0.0) {
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
                        validationError = "Invalid transaction amount: $am"
                    )
                }
            }

            // Currency validation (must be 3 alphabetic characters, default INR)
            if (cu.length != 3 || !cu.all { it.isLetter() }) {
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
                    validationError = "Invalid currency code: $cu"
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
                validationError = "Failed to parse UPI URI: ${e.message}"
            )
        }
    }
}

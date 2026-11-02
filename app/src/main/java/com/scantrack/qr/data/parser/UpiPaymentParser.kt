package com.scantrack.qr.data.parser

import java.math.BigDecimal
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

            // Strict BigDecimal Amount Validation:
            // 1. Must match strict standard currency decimal format: digits only, optional dot with 1-2 digits
            // 2. Rejects scientific notation (e.g. 1e5, 2E3), signs (+/-), NaN, infinity, and excessive fractional digits
            // 3. Must be strictly positive and within valid transaction bounds
            if (!am.isNullOrBlank()) {
                val amountPattern = Regex("^[0-9]+(\\.[0-9]{1,2})?$")
                val isFormatValid = amountPattern.matches(am)

                val parsedAmount = if (isFormatValid) {
                    try {
                        BigDecimal(am)
                    } catch (e: Exception) {
                        null
                    }
                } else {
                    null
                }

                val maxTransactionLimit = BigDecimal("10000000.00") // 10,000,000 limit

                if (parsedAmount == null || parsedAmount <= BigDecimal.ZERO || parsedAmount > maxTransactionLimit) {
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
                        validationError = "Invalid transaction amount: $am (must be a positive decimal with up to 2 decimal places, no scientific notation)"
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

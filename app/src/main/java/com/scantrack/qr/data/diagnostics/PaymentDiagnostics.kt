package com.scantrack.qr.data.diagnostics

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.scantrack.qr.BuildConfig
import com.scantrack.qr.data.parser.UpiPaymentData

/**
 * Privacy-safe diagnostic logger for UPI payment operations.
 *
 * Adheres strictly to security and privacy guidelines:
 * 1. Never logs full QR payloads, raw query strings, customer usernames, account numbers, PINs, or signatures.
 * 2. Replaces content-bearing validation messages with structured error codes.
 * 3. Enforces strict whitelisting / bounds on status strings and response codes.
 * 4. Never treats a launched app or client-side callback as authoritative proof of payment success.
 */
object PaymentDiagnostics {
    private const val TAG = "ScanTrack_UPI_Diag"

    private val KNOWN_STATUSES = setOf("SUCCESS", "FAILURE", "SUBMITTED", "PENDING", "FAILED")

    fun recordDispatch(
        context: Context,
        upiData: UpiPaymentData,
        targetPackage: String?,
        flags: Int
    ) {
        val pspVersion = targetPackage?.let { pkg ->
            try {
                val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(pkg, 0)
                }
                val verCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    pInfo.versionCode.toLong()
                }
                "v${pInfo.versionName ?: verCode.toString()}"
            } catch (e: Exception) {
                "not_installed"
            }
        } ?: "chooser"

        val hasAmount = !upiData.amount.isNullOrBlank()
        val hasRef = !upiData.transactionRef.isNullOrBlank()
        val hasMode = !upiData.mode.isNullOrBlank()
        val hasMerchantMeta = upiData.hasMerchantMetadata

        Log.i(
            TAG,
            "PAYMENT_DISPATCH | buildSha=${BuildConfig.BUILD_COMMIT_SHA} " +
                    "| targetPkg=${targetPackage ?: "system_chooser"} " +
                    "| pspVer=$pspVersion " +
                    "| flags=0x${Integer.toHexString(flags)} " +
                    "| params=[merchantMeta=$hasMerchantMeta, mode=$hasMode, amount=$hasAmount, ref=$hasRef, cu=${upiData.currency ?: "INR"}]"
        )
    }

    fun recordResult(
        resultCode: Int,
        dataIntent: Intent?
    ) {
        val resultCodeStr = when (resultCode) {
            android.app.Activity.RESULT_OK -> "RESULT_OK"
            android.app.Activity.RESULT_CANCELED -> "RESULT_CANCELED"
            else -> "RESULT_CODE_$resultCode"
        }

        val rawStatus = dataIntent?.getStringExtra("Status")
            ?: dataIntent?.getStringExtra("status")
            ?: dataIntent?.data?.getQueryParameter("Status")
            ?: dataIntent?.data?.getQueryParameter("status")

        // Strictly bound status token
        val boundedStatus = when {
            rawStatus == null -> "NONE"
            rawStatus.uppercase() in KNOWN_STATUSES -> rawStatus.uppercase()
            else -> "OTHER"
        }

        val rawResponseCode = dataIntent?.getStringExtra("responseCode")
            ?: dataIntent?.data?.getQueryParameter("responseCode")

        // Strictly bound response code: alphanumeric 1-4 chars (e.g. 00, U16, U30, ZM, U13)
        val boundedResponseCode = if (rawResponseCode != null && rawResponseCode.length in 1..4 && rawResponseCode.all { it.isLetterOrDigit() }) {
            rawResponseCode
        } else if (rawResponseCode != null) {
            "NON_STANDARD"
        } else {
            "NONE"
        }

        Log.i(
            TAG,
            "PAYMENT_RESULT | result=$resultCodeStr " +
                    "| hasData=${dataIntent != null} " +
                    "| status=$boundedStatus " +
                    "| responseCode=$boundedResponseCode"
        )
    }

    fun recordValidationError(errorCode: String) {
        Log.w(TAG, "PAYMENT_VALIDATION_ERROR | code=$errorCode")
    }
}


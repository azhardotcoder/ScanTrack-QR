package com.scantrack.qr.presentation.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scantrack.qr.data.local.SettingsManager
import com.scantrack.qr.data.local.entity.QrEntity
import com.scantrack.qr.data.repository.QrRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Detects the semantic type of a QR raw value */
fun detectQrType(rawValue: String): String = when {
    rawValue.startsWith("http://") || rawValue.startsWith("https://") -> "URL"
    rawValue.startsWith("upi://pay") -> "PAYMENT"
    rawValue.startsWith("WIFI:") -> "WIFI"
    rawValue.startsWith("mailto:") -> "EMAIL"
    rawValue.startsWith("tel:") -> "PHONE"
    else -> "TEXT"
}

data class ScanResult(
    val rawValue: String,
    val label: String,
    val type: String
)

sealed class ScanState {
    object Idle : ScanState()
    object Scanning : ScanState()
    data class Result(val result: ScanResult) : ScanState()
    data class Error(val message: String) : ScanState()
}

class ScannerViewModel(
    private val repository: QrRepository,
    private val settingsManager: SettingsManager
) : ViewModel() {

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Scanning)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _isFlashOn = MutableStateFlow(false)
    val isFlashOn: StateFlow<Boolean> = _isFlashOn.asStateFlow()

    val hapticEnabled: StateFlow<Boolean> = settingsManager.hapticEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /** Throttle: track the last scanned value to avoid duplicates in quick succession */
    private var lastScannedValue: String? = null

    fun onQrDetected(rawValue: String) {
        if (rawValue.isBlank()) return
        if (rawValue == lastScannedValue) return // throttle repeated hits
        lastScannedValue = rawValue

        val type = detectQrType(rawValue)
        val label = deriveLabelFromRaw(rawValue, type)
        val result = ScanResult(rawValue = rawValue, label = label, type = type)
        _scanState.value = ScanState.Result(result)

        // Persist to Room database
        viewModelScope.launch {
            repository.saveOrUpdateQr(
                QrEntity(
                    rawValue = rawValue,
                    label = label,
                    type = type,
                    createdAt = System.currentTimeMillis(),
                    lastUsedAt = System.currentTimeMillis(),
                    usageCount = 1
                )
            )
        }
    }

    fun toggleFlash() {
        _isFlashOn.value = !_isFlashOn.value
    }

    fun dismissResult() {
        // Allow the user to scan again after dismissing
        lastScannedValue = null
        _scanState.value = ScanState.Scanning
    }

    fun onGalleryImageScanned(uri: android.net.Uri) {
        // Signal can be handled here if we needed to track gallery scans specifically
    }

    private fun deriveLabelFromRaw(rawValue: String, type: String): String = when (type) {
        "URL" -> rawValue.removePrefix("https://").removePrefix("http://").substringBefore("/").take(40)
        "PAYMENT" -> "UPI Payment"
        "WIFI" -> {
            val ssid = rawValue.substringAfter("S:").substringBefore(";")
            if (ssid.isNotEmpty()) "WiFi: $ssid" else "WiFi Network"
        }
        "EMAIL" -> rawValue.removePrefix("mailto:").substringBefore("?")
        "PHONE" -> rawValue.removePrefix("tel:")
        else -> rawValue.take(40)
    }
}

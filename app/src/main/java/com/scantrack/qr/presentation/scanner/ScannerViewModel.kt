package com.scantrack.qr.presentation.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scantrack.qr.data.local.SettingsManager
import com.scantrack.qr.data.local.entity.QrEntity
import com.scantrack.qr.data.parser.QrTypeDetector
import com.scantrack.qr.data.repository.QrRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Legacy delegate for backward compatibility */
fun detectQrType(rawValue: String): String = QrTypeDetector.detect(rawValue)

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
    private val settingsManager: SettingsManager? = null,
    hapticFlowOverride: Flow<Boolean>? = null
) : ViewModel() {

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Scanning)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _isFlashOn = MutableStateFlow(false)
    val isFlashOn: StateFlow<Boolean> = _isFlashOn.asStateFlow()

    val hapticEnabled: StateFlow<Boolean> = (hapticFlowOverride ?: settingsManager?.hapticEnabled ?: flowOf(true))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /** Concurrency lock: true when actively accepting QR detections */
    private val isAcceptingScans = AtomicBoolean(true)

    /** Monotonically increasing session generation counter to drop stale async results */
    private val sessionGeneration = AtomicLong(0)

    fun getCurrentSessionToken(): Long = sessionGeneration.get()

    fun onQrDetected(rawValue: String, sessionToken: Long? = null) {
        val trimmed = rawValue.trim()
        if (trimmed.isBlank()) return

        // Verify session validity for async sources (e.g. gallery picker)
        if (sessionToken != null && sessionToken != sessionGeneration.get()) {
            return
        }

        // State Machine check: Only process if in Scanning state and lock acquired
        if (!isAcceptingScans.compareAndSet(true, false)) {
            return
        }

        val type = QrTypeDetector.detect(trimmed)
        val label = QrTypeDetector.deriveLabel(trimmed, type)
        val result = ScanResult(rawValue = trimmed, label = label, type = type)

        _scanState.value = ScanState.Result(result)

        // Persist atomically to Room database
        viewModelScope.launch {
            try {
                repository.saveOrUpdateQr(
                    QrEntity(
                        rawValue = trimmed,
                        label = label,
                        type = type,
                        createdAt = System.currentTimeMillis(),
                        lastUsedAt = System.currentTimeMillis(),
                        usageCount = 1
                    )
                )
            } catch (e: Exception) {
                // Database write error handled safely
            }
        }
    }

    fun toggleFlash() {
        _isFlashOn.value = !_isFlashOn.value
    }

    fun dismissResult() {
        // Reset state machine to scanning mode and increment session generation
        sessionGeneration.incrementAndGet()
        isAcceptingScans.set(true)
        _scanState.value = ScanState.Scanning
    }

    fun resetScanner() {
        sessionGeneration.incrementAndGet()
        isAcceptingScans.set(true)
        _scanState.value = ScanState.Scanning
    }
}

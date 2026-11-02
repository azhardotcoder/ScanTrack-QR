package com.scantrack.qr.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scantrack.qr.data.local.SettingsManager
import com.scantrack.qr.data.repository.QrRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsManager: SettingsManager,
    private val repository: QrRepository
) : ViewModel() {

    val hapticEnabled: StateFlow<Boolean> = settingsManager.hapticEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val darkMode: StateFlow<String> = settingsManager.darkMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "SYSTEM")

    private val _eventFlow = MutableSharedFlow<String>()
    val eventFlow: SharedFlow<String> = _eventFlow.asSharedFlow()

    fun toggleHaptic(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setHapticEnabled(enabled)
        }
    }

    fun setDarkMode(mode: String) {
        viewModelScope.launch {
            settingsManager.setDarkMode(mode)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            val count = repository.clearAll()
            _eventFlow.emit(if (count > 0) "Scan history cleared successfully" else "History is already empty")
        }
    }
}

package com.scantrack.qr.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scantrack.qr.data.local.SettingsManager
import com.scantrack.qr.data.repository.QrRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
            repository.allQrs.stateIn(viewModelScope).value.forEach {
                repository.deleteQr(it)
            }
        }
    }
}

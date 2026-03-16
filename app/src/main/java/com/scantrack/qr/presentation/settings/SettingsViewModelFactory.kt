package com.scantrack.qr.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.scantrack.qr.data.local.SettingsManager
import com.scantrack.qr.data.repository.QrRepository

class SettingsViewModelFactory(
    private val settingsManager: SettingsManager,
    private val repository: QrRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(settingsManager, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

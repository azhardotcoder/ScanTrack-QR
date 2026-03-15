package com.scantrack.qr.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scantrack.qr.data.local.entity.QrEntity
import com.scantrack.qr.data.repository.QrRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HomeViewModel(private val repository: QrRepository) : ViewModel() {

    val pinnedQrs: StateFlow<List<QrEntity>> = repository.pinnedQrs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val quickAccessQrs: StateFlow<List<QrEntity>> = repository.quickAccessQrs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentQrs: StateFlow<List<QrEntity>> = repository.recentQrs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun togglePin(qr: QrEntity) {
        viewModelScope.launch {
            repository.togglePin(qr)
        }
    }

    fun renameQr(qr: QrEntity, newLabel: String) {
        viewModelScope.launch {
            repository.renameQr(qr, newLabel)
        }
    }

    fun deleteQr(qr: QrEntity) {
        viewModelScope.launch {
            repository.deleteQr(qr)
        }
    }

    fun markAsUsed(qr: QrEntity) {
        viewModelScope.launch {
            repository.markQrUsed(qr)
        }
    }

    fun onQrScanned(rawValue: String, type: String) {
        viewModelScope.launch {
            repository.saveOrUpdateQr(QrEntity(rawValue = rawValue, type = type))
        }
    }
}

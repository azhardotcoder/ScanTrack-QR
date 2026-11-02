package com.scantrack.qr.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scantrack.qr.data.local.entity.QrEntity
import com.scantrack.qr.data.repository.QrRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
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
            repository.togglePin(qr.id, qr.isPinned)
        }
    }

    fun renameQr(qr: QrEntity, newLabel: String) {
        viewModelScope.launch {
            repository.renameQr(qr.id, newLabel)
        }
    }

    fun deleteQr(qr: QrEntity) {
        viewModelScope.launch {
            repository.deleteQrById(qr.id)
        }
    }

    fun markAsUsed(qr: QrEntity) {
        viewModelScope.launch {
            repository.markQrUsed(qr.id)
        }
    }
}

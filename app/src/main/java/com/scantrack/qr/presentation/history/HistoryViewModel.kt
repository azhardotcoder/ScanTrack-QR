package com.scantrack.qr.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scantrack.qr.data.local.entity.QrEntity
import com.scantrack.qr.data.repository.QrRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HistoryViewModel(private val repository: QrRepository) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedType = MutableStateFlow("All")
    val selectedType = _selectedType.asStateFlow()

    val historyItems: StateFlow<List<QrEntity>> = combine(
        _searchQuery,
        _selectedType
    ) { query, type ->
        Pair(query, type)
    }.flatMapLatest { (query, type) ->
        repository.searchQrs(query, type)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun onTypeSelected(type: String) {
        _selectedType.value = type
    }

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

    fun deleteQrById(id: Long) {
        viewModelScope.launch {
            repository.deleteQrById(id)
        }
    }

    fun markAsUsed(qr: QrEntity) {
        viewModelScope.launch {
            repository.markQrUsed(qr)
        }
    }
}

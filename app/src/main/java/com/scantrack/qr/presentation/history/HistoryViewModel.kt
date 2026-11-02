package com.scantrack.qr.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scantrack.qr.data.local.entity.QrEntity
import com.scantrack.qr.data.repository.QrRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class HistoryViewModel(private val repository: QrRepository) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedType = MutableStateFlow("All")
    val selectedType: StateFlow<String> = _selectedType.asStateFlow()

    val historyItems: StateFlow<List<QrEntity>> = combine(
        _searchQuery.debounce(300L),
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
            repository.togglePin(qr.id)
        }
    }

    fun renameQr(qr: QrEntity, newLabel: String) {
        viewModelScope.launch {
            repository.renameQr(qr.id, newLabel)
        }
    }

    fun deleteQrById(id: Long) {
        viewModelScope.launch {
            repository.deleteQrById(id)
        }
    }

    fun markAsUsed(qr: QrEntity) {
        viewModelScope.launch {
            repository.markQrUsed(qr.id)
        }
    }
}

package com.scantrack.qr.presentation.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scantrack.qr.data.local.entity.QrEntity
import com.scantrack.qr.presentation.components.*
import com.scantrack.qr.presentation.utils.ActionUtils

@Composable
fun HistoryScreen(viewModel: HistoryViewModel, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedType by viewModel.selectedType.collectAsStateWithLifecycle()
    val historyItems by viewModel.historyItems.collectAsStateWithLifecycle()
    var selectedQr by remember { mutableStateOf<QrEntity?>(null) }

    // Refresh an open sheet after Room emits an updated label, pin, or usage count.
    LaunchedEffect(historyItems) {
        selectedQr?.let { selected ->
            historyItems.firstOrNull { it.id == selected.id }?.let { selectedQr = it }
        }
    }

    HistoryScreenContent(
        historyItems = historyItems,
        searchQuery = searchQuery,
        selectedType = selectedType,
        onQueryChange = viewModel::onSearchQueryChanged,
        onTypeSelected = viewModel::onTypeSelected,
        onQrClick = { selectedQr = it },
        onNavigateBack = onNavigateBack
    )

    selectedQr?.let { qr ->
        QrDetailSheet(
            qr = qr,
            onDismiss = { selectedQr = null },
            onPinToggle = {
                viewModel.togglePin(it)
                selectedQr = it.copy(isPinned = !it.isPinned)
            },
            onRename = { entity, label ->
                viewModel.renameQr(entity, label)
                selectedQr = entity.copy(label = label.trim())
            },
            onDelete = {
                viewModel.deleteQrById(it.id)
                selectedQr = null
            },
            onOpen = {
                viewModel.markAsUsed(it)
                ActionUtils.openQrContent(context, it.rawValue)
            }
        )
    }
}

/** One lazy scroll surface keeps search and actions reachable even in a short window. */
@Composable
fun HistoryScreenContent(
    historyItems: List<QrEntity>,
    searchQuery: String,
    selectedType: String,
    onQueryChange: (String) -> Unit,
    onTypeSelected: (String) -> Unit,
    onQrClick: (QrEntity) -> Unit,
    onNavigateBack: () -> Unit
) {
    val isFiltered = searchQuery.isNotBlank() || selectedType != "All"
    val filters = listOf(
        "All" to "All", "URL" to "URL", "PAYMENT" to "Payment",
        "WIFI" to "Wi-Fi", "PHONE" to "Phone", "EMAIL" to "Email", "TEXT" to "Text"
    )

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 840.dp).fillMaxSize().imePadding().testTag("history-list"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "heading") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        AppTopBar("History", "A place for everything you've scanned.")
                    }
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back to Home")
                    }
                }
            }
            item(key = "search") {
                SearchBar(query = searchQuery, onQueryChange = onQueryChange)
            }
            item(key = "filters") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filters, key = { it.first }) { (value, label) ->
                        QRFilterChip(
                            label = label,
                            selected = selectedType == value,
                            onClick = { onTypeSelected(value) }
                        )
                    }
                }
            }
            item(key = "count") {
                Text(
                    text = if (isFiltered) {
                        "${historyItems.size} ${if (historyItems.size == 1) "result" else "results"}"
                    } else {
                        "${historyItems.size} saved ${if (historyItems.size == 1) "scan" else "scans"}"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (historyItems.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = if (isFiltered) "No matching scans" else "Your history starts here",
                        message = if (isFiltered) {
                            "Try another search or choose a different type."
                        } else {
                            "Scan a QR code or choose one from your gallery. It will be saved here for next time."
                        },
                        icon = if (isFiltered) Icons.Outlined.SearchOff else Icons.Outlined.History,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        actions = {
                            if (isFiltered) {
                                TextButton(onClick = {
                                    onQueryChange("")
                                    onTypeSelected("All")
                                }) { Text("Clear filters") }
                            }
                        }
                    )
                }
            } else {
                items(historyItems, key = { "qr-${it.id}" }, contentType = { "qr" }) { qr ->
                    QRHistoryItem(qr = qr, onClick = { onQrClick(qr) })
                }
            }
        }
    }
}

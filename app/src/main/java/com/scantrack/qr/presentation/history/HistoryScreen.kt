package com.scantrack.qr.presentation.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scantrack.qr.data.local.entity.QrEntity
import com.scantrack.qr.presentation.components.ModernBackground
import com.scantrack.qr.presentation.components.QrDetailSheet
import com.scantrack.qr.presentation.components.RecentScanCard
import com.scantrack.qr.presentation.components.SectionHeader
import com.scantrack.qr.presentation.utils.ActionUtils
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedType by viewModel.selectedType.collectAsStateWithLifecycle()
    val historyItems by viewModel.historyItems.collectAsStateWithLifecycle()
    
    var selectedQrForDetail by remember { mutableStateOf<QrEntity?>(null) }

    ModernBackground {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            TopAppBar(
                title = { Text("History", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
            ) {
                // Search Bar
                TextField(
                    value = searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    placeholder = { Text("Search labels or content...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFFF1F5F9).copy(alpha = 0.8f),
                        unfocusedContainerColor = Color(0xFFF1F5F9).copy(alpha = 0.7f),
                        disabledContainerColor = Color(0xFFF1F5F9),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    )
                )

                // Filter Chips
                val types = listOf("All", "URL", "PAYMENT", "WIFI", "TEXT", "EMAIL", "PHONE")
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(types) { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { viewModel.onTypeSelected(type) },
                            label = { Text(type) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF2563EB),
                                selectedLabelColor = Color.White
                            ),
                            border = null
                        )
                    }
                }

                // History List
                if (historyItems.isEmpty()) {
                    EmptyHistory(searchQuery.isNotEmpty() || selectedType != "All")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(historyItems) { qr ->
                            RecentScanCard(
                                title = qr.label ?: "QR Code",
                                subtitle = qr.rawValue,
                                time = formatTime(qr.lastUsedAt),
                                onClick = { selectedQrForDetail = qr }
                            )
                        }
                    }
                }
            }
        }

        if (selectedQrForDetail != null) {
            QrDetailSheet(
                qr = selectedQrForDetail,
                onDismiss = { selectedQrForDetail = null },
                onPinToggle = { viewModel.togglePin(it) },
                onRename = { entity, label -> viewModel.renameQr(entity, label) },
                onDelete = { 
                    viewModel.deleteQrById(it.id)
                    selectedQrForDetail = null
                },
                onOpen = { 
                    viewModel.markAsUsed(it)
                    ActionUtils.openQrContent(context, it.rawValue)
                }
            )
        }
    }
}

@Composable
private fun EmptyHistory(isFiltered: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (isFiltered) "No matches found" else "No history yet",
                style = MaterialTheme.typography.titleMedium,
                color = Color.Gray
            )
            if (!isFiltered) {
                Text(
                    text = "Your scanned QR codes will appear here",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray.copy(alpha = 0.7f)
                )
            }
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000} mins ago"
        diff < 86400_000 -> "${diff / 3600_000} hours ago"
        else -> {
            val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}

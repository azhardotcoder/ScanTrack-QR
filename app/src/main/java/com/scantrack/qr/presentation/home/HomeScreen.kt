package com.scantrack.qr.presentation.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.scantrack.qr.R
import com.scantrack.qr.data.local.entity.QrEntity
import com.scantrack.qr.presentation.components.*
import com.scantrack.qr.presentation.utils.ActionUtils

@Composable
fun HomeScreen(
    viewModel: HomeViewModel
) {
    val pinnedQrs by viewModel.pinnedQrs.collectAsStateWithLifecycle()
    val quickAccessQrs by viewModel.quickAccessQrs.collectAsStateWithLifecycle()
    val recentScans by viewModel.recentQrs.collectAsStateWithLifecycle()
    
    var selectedQrForDetail by remember { mutableStateOf<QrEntity?>(null) }
    val context = LocalContext.current

    ModernBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            HomeScreenContent(
                pinnedQrs = pinnedQrs,
                quickAccessQrs = quickAccessQrs,
                recentScans = recentScans,
                onQrClick = { selectedQrForDetail = it }
            )

            if (selectedQrForDetail != null) {
                QrDetailSheet(
                    qr = selectedQrForDetail,
                    onDismiss = { selectedQrForDetail = null },
                    onPinToggle = { viewModel.togglePin(it) },
                    onRename = { entity, label -> viewModel.renameQr(entity, label) },
                    onDelete = { 
                        viewModel.deleteQr(it)
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenContent(
    pinnedQrs: List<QrEntity>,
    quickAccessQrs: List<QrEntity>,
    recentScans: List<QrEntity>,
    onQrClick: (QrEntity) -> Unit
) {
    // Remove individual container padding that might overlap background blocks
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // --- TOP SECTION ---
        TopAppBar(
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_app_logo),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ScanTrack QR",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
            },
            // Removed navigationIcon (back arrow)
            // Removed actions (settings icon)
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp), // Padding applied to content only
            contentPadding = PaddingValues(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // --- PINNED QR SECTION ---
            if (pinnedQrs.isNotEmpty()) {
                item {
                    SectionHeader(title = "Pinned QR")
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        val chunks = pinnedQrs.chunked(2)
                        items(chunks) { chunk ->
                            PinnedQrGroup(
                                items = chunk.map { 
                                    PinnedItem(
                                        title = it.label ?: it.rawValue,
                                        subtitle = "Used ${it.usageCount} times",
                                        iconColor = getIconColorForType(it.type)
                                    )
                                },
                                onClick = { index -> onQrClick(chunk[index]) }
                            )
                        }
                    }
                }
            }

            // --- QUICK ACCESS SECTION ---
            if (quickAccessQrs.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    SectionHeader(title = "Quick Access")
                    val itemsToShow = quickAccessQrs.take(2)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        itemsToShow.forEachIndexed { index, qr ->
                            QuickAccessCard(
                                title = qr.label ?: "Fast Action",
                                subtitle = qr.rawValue.take(15) + "...",
                                backgroundColor = if (index == 0) Color(0xFF2563EB) else Color(0xFF10B981),
                                modifier = Modifier.weight(1f),
                                onClick = { onQrClick(qr) }
                            )
                        }
                    }
                }
            }

            // --- RECENT SCANS SECTION ---
            if (recentScans.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    SectionHeader(title = "Recent Scans")
                }

                items(recentScans) { scan ->
                    RecentScanCard(
                        title = scan.label ?: "QR Code",
                        subtitle = scan.rawValue,
                        time = formatTime(scan.lastUsedAt),
                        onClick = { onQrClick(scan) }
                    )
                }
            }
            
            if (pinnedQrs.isEmpty() && quickAccessQrs.isEmpty() && recentScans.isEmpty()) {
                item {
                    EmptyHomeState()
                }
            }
        }
    }
}

@Composable
private fun EmptyHomeState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                color = Color(0xFFF1F5F9),
                shape = CircleShape,
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Settings, // Placeholder for an empty icon
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = Color.Gray
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("No scans found", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Tap the center button to start scanning", color = Color.Gray, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
    }
}

private fun getIconColorForType(type: String): Color = when (type) {
    "URL" -> Color(0xFF2563EB)
    "PAYMENT" -> Color(0xFF10B981)
    "WIFI" -> Color(0xFFF59E0B)
    else -> Color(0xFF64748B)
}

private fun formatTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000} mins ago"
        diff < 86400_000 -> "${diff / 3600_000} hours ago"
        else -> "Earlier today"
    }
}

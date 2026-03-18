package com.scantrack.qr.presentation.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
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

    // ModernBackground is handled in MainActivity Scaffold
    HomeScreenContent(
        pinnedQrs = pinnedQrs,
        quickAccessQrs = quickAccessQrs,
        recentScans = recentScans,
        onQrClick = { selectedQrForDetail = it }
    )

    // Details sheet logic remains same
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenContent(
    pinnedQrs: List<QrEntity>,
    quickAccessQrs: List<QrEntity>,
    recentScans: List<QrEntity>,
    onQrClick: (QrEntity) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)
    ) {
        // --- TOP BRANDING (Centered, Clean) ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "ScanTrack QR",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        // --- PINNED QR SECTION (Horizontal Scroll) ---
        if (pinnedQrs.isNotEmpty()) {
            SectionHeader(title = "Pinned QR")
            Spacer(modifier = Modifier.height(12.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(end = 12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(pinnedQrs) { qr ->
                    PinnedQrCard(
                        title = qr.label ?: "Label",
                        iconColor = getIconColorForType(qr.type),
                        onClick = { onQrClick(qr) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // --- QUICK ACCESS SECTION (Two big cards) ---
        if (quickAccessQrs.isNotEmpty()) {
            SectionHeader(title = "Quick Access")
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                quickAccessQrs.take(2).forEachIndexed { index, qr ->
                    val color = if (index == 0) Color(0xFF2563EB) else Color(0xFF10B981)
                    QuickAccessCard(
                        title = qr.label ?: "QR Code",
                        backgroundColor = color,
                        modifier = Modifier.weight(1f),
                        onClick = { onQrClick(qr) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // --- RECENT SCANS SECTION (Vertical Stack) ---
        if (recentScans.isNotEmpty()) {
            SectionHeader(title = "Recent Scans")
            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(recentScans) { scan ->
                    RecentScanCard(
                        title = scan.label ?: "QR Code",
                        time = "Last used: " + formatTime(scan.lastUsedAt),
                        onClick = { onQrClick(scan) }
                    )
                }
            }
        } else if (pinnedQrs.isEmpty() && quickAccessQrs.isEmpty()) {
            EmptyHomeState()
        }
    }
}

@Composable
private fun EmptyHomeState() {
    Box(
        modifier = Modifier.fillMaxSize().padding(top = 80.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = CircleShape,
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("No scans found", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            Text("Tap the center button to start", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f), fontSize = 14.sp)
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
        diff < 60_000 -> "just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> "${diff / 3600_000}h ago"
        else -> "today"
    }
}

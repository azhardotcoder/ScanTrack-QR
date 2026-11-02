package com.scantrack.qr.presentation.home

import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scantrack.qr.data.local.entity.QrEntity
import com.scantrack.qr.presentation.components.*
import com.scantrack.qr.presentation.theme.ScanTrackQRTheme
import com.scantrack.qr.presentation.utils.ActionUtils

@Composable
fun HomeScreen(viewModel: HomeViewModel, onScan: () -> Unit, onGalleryScan: () -> Unit, onHistory: () -> Unit) {
    val pinned by viewModel.pinnedQrs.collectAsStateWithLifecycle()
    val frequent by viewModel.quickAccessQrs.collectAsStateWithLifecycle()
    val recent by viewModel.recentQrs.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<QrEntity?>(null) }
    val context = LocalContext.current

    HomeScreenContent(pinned, frequent, recent, { selected = it }, onScan, onGalleryScan, onHistory)
    selected?.let { selectedQr ->
        val current = (pinned + frequent + recent).firstOrNull { it.id == selectedQr.id } ?: selectedQr
        QrDetailSheet(qr = current, onDismiss = { selected = null },
            onPinToggle = viewModel::togglePin, onRename = viewModel::renameQr,
            onDelete = { viewModel.deleteQr(it); selected = null },
            onOpen = { viewModel.markAsUsed(it); ActionUtils.openQrContent(context, it.rawValue) })
    }
}

@Composable
fun HomeScreenContent(
    pinnedQrs: List<QrEntity>, quickAccessQrs: List<QrEntity>, recentScans: List<QrEntity>,
    onQrClick: (QrEntity) -> Unit, onScan: () -> Unit = {}, onGalleryScan: () -> Unit = {}, onHistory: () -> Unit = {}
) {
    val quick = remember(quickAccessQrs, pinnedQrs) {
        val pinnedIds = pinnedQrs.map { it.id }.toSet()
        quickAccessQrs.asSequence().filter { it.usageCount >= 3 && it.id !in pinnedIds }.take(2).toList()
    }
    val recent = remember(recentScans, quick) {
        val quickIds = quick.map { it.id }.toSet()
        recentScans.asSequence().filter { it.id !in quickIds }.take(8).toList()
    }
    val empty = pinnedQrs.isEmpty() && quickAccessQrs.isEmpty() && recentScans.isEmpty()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = AppSpacing.ContentWidth).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = AppSpacing.Page, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item("header") { AppTopBar("ScanTrack QR", "Everything you scan, beautifully organized.") }
            item("scan") { ScanHero(onScan, onGalleryScan) }
            if (empty) {
                item("empty") {
                    EmptyState("Your next scan starts here", "Links, payments, Wi-Fi and more. Your scans stay organized on this device.", Icons.Outlined.QrCode2)
                }
            } else {
                item("pinned_title") { SectionHeader("Pinned") }
                item("pinned") {
                    if (pinnedQrs.isEmpty()) {
                        PremiumCard {
                            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Outlined.PushPin, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Keep the essentials close", style = MaterialTheme.typography.titleSmall)
                                    Text("Open a scan and pin it for easy access.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(pinnedQrs, key = { it.id }) { qr -> PinnedQrCard(qr, { onQrClick(qr) }) }
                        }
                    }
                }
                if (quick.isNotEmpty()) {
                    item("quick_title") { SectionHeader("Quick access") }
                    items(quick, key = { "quick_${it.id}" }) { qr -> QRHistoryItem(qr, { onQrClick(qr) }) }
                }
                item("recent_title") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        SectionHeader("Recent activity", Modifier.weight(1f))
                        TextButton(onClick = onHistory) { Text("View all") }
                    }
                }
                items(recent, key = { "recent_${it.id}" }) { qr -> QRHistoryItem(qr, { onQrClick(qr) }) }
                if (recent.isEmpty()) item("recent_hint") {
                    Text("Your frequently used scans are in Quick access.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ScanHero(onScan: () -> Unit, onGalleryScan: () -> Unit) {
    val reduceMotion = LocalReduceMotion.current
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val iconScale by animateFloatAsState(
        targetValue = if (entered || reduceMotion) 1f else 0.9f,
        animationSpec = tween(if (reduceMotion) 0 else 220), label = "Scan icon entrance"
    )
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            BoxWithConstraints {
                val stackedHeading = maxWidth < 400.dp && LocalDensity.current.fontScale > 1.4f
                val heading: @Composable () -> Unit = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("One scan. All the details.", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("Point your camera or choose a photo.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                val icon: @Composable () -> Unit = {
                    Icon(Icons.Outlined.QrCodeScanner, null, Modifier.size(40.dp).graphicsLayer(scaleX = iconScale, scaleY = iconScale), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                if (stackedHeading) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { icon(); heading() }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        icon()
                        Box(Modifier.weight(1f)) { heading() }
                    }
                }
            }
            BoxWithConstraints {
                if (maxWidth >= 480.dp) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PrimaryActionButton("Scan QR", onScan, Modifier.weight(1f), Icons.Outlined.QrCodeScanner)
                        SecondaryActionButton("Gallery scan", onGalleryScan, Modifier.weight(1f), Icons.Outlined.PhotoLibrary)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrimaryActionButton("Scan QR", onScan, Modifier.fillMaxWidth(), Icons.Outlined.QrCodeScanner)
                        SecondaryActionButton("Gallery scan", onGalleryScan, Modifier.fillMaxWidth(), Icons.Outlined.PhotoLibrary)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun HomeLightPreview() = ScanTrackQRTheme(false) { HomeScreenContent(emptyList(), emptyList(), emptyList(), {}) }

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun HomeDarkPreview() = ScanTrackQRTheme(true) { HomeScreenContent(emptyList(), emptyList(), emptyList(), {}) }

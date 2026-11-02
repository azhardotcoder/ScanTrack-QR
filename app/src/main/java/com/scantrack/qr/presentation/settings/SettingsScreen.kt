package com.scantrack.qr.presentation.settings

import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scantrack.qr.presentation.components.*
import com.scantrack.qr.presentation.utils.ActionUtils

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val hapticEnabled by viewModel.hapticEnabled.collectAsStateWithLifecycle()
    val darkMode by viewModel.darkMode.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val versionName = remember(context) {
        try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unknown"
        } catch (_: PackageManager.NameNotFoundException) {
            "Unknown"
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    SettingsScreenContent(
        hapticEnabled = hapticEnabled,
        darkMode = darkMode,
        versionName = versionName,
        onHapticChanged = viewModel::toggleHaptic,
        onThemeSelected = viewModel::setDarkMode,
        onClearHistory = viewModel::clearHistory,
        onOpenSource = {
            ActionUtils.openQrContent(context, "https://github.com/azhardotcoder/ScanTrack-QR")
        }
    )
}

@Composable
fun SettingsScreenContent(
    hapticEnabled: Boolean,
    darkMode: String,
    versionName: String,
    onHapticChanged: (Boolean) -> Unit,
    onThemeSelected: (String) -> Unit,
    onClearHistory: () -> Unit,
    onOpenSource: () -> Unit
) {
    var showClearHistoryDialog by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 840.dp).fillMaxSize().testTag("settings-list"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "heading") { AppTopBar("Settings", "Make ScanTrack feel like yours.") }
            item(key = "appearance-heading") { SectionHeader("Appearance") }
            item(key = "appearance") {
                PremiumCard {
                    SettingsHeading(
                        title = "App theme",
                        subtitle = "Choose a look, or follow your device.",
                        icon = Icons.Outlined.Palette
                    )
                    Column(Modifier.fillMaxWidth().selectableGroup()) {
                        listOf(
                            Triple("SYSTEM", "System", Icons.Outlined.BrightnessAuto),
                            Triple("LIGHT", "Light", Icons.Outlined.LightMode),
                            Triple("DARK", "Dark", Icons.Outlined.DarkMode)
                        ).forEach { (mode, label, icon) ->
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .selectable(
                                        selected = darkMode == mode,
                                        onClick = { onThemeSelected(mode) },
                                        role = Role.RadioButton
                                    )
                                    .defaultMinSize(minHeight = 52.dp)
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(label, Modifier.weight(1f).padding(start = 16.dp),
                                    style = MaterialTheme.typography.bodyLarge)
                                RadioButton(selected = darkMode == mode, onClick = null)
                            }
                        }
                    }
                }
            }
            item(key = "scanner-heading") { SectionHeader("Scanner") }
            item(key = "scanner") {
                PremiumCard {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .toggleable(
                                value = hapticEnabled,
                                onValueChange = onHapticChanged,
                                role = Role.Switch
                            )
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Vibration, null,
                            tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                            Text("Haptic feedback", style = MaterialTheme.typography.titleSmall)
                            Text("A gentle vibration for scans and actions.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = hapticEnabled, onCheckedChange = null)
                    }
                    Text(
                        "Animations follow your device's motion settings.",
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item(key = "data-heading") { SectionHeader("Your data") }
            item(key = "data") {
                PremiumCard {
                    SettingsActionRow(
                        title = "Clear scan history",
                        subtitle = "Delete all scans, including pinned items.",
                        icon = Icons.Outlined.DeleteSweep,
                        tint = MaterialTheme.colorScheme.error,
                        onClick = { showClearHistoryDialog = true }
                    )
                }
            }
            item(key = "about-heading") { SectionHeader("About") }
            item(key = "about") {
                PremiumCard {
                    SettingsHeading(
                        title = "ScanTrack QR",
                        subtitle = "Version $versionName",
                        icon = Icons.Outlined.QrCode2
                    )
                    Divider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsHeading(
                        title = "Private by design",
                        subtitle = "Scanning works offline. Your scan history is stored on this device and excluded from cloud backup. Opening or sharing a QR sends its content to the app you choose.",
                        icon = Icons.Outlined.PrivacyTip
                    )
                    Divider(color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsActionRow(
                        title = "Source code",
                        subtitle = "View the ScanTrack QR project on GitHub.",
                        icon = Icons.Outlined.Code,
                        onClick = onOpenSource
                    )
                }
            }
        }
    }

    if (showClearHistoryDialog) {
        ConfirmationDialog(
            title = "Clear all scan history?",
            message = "All saved scans, including pinned and frequent items, will be permanently deleted. This cannot be undone.",
            confirmLabel = "Clear all history",
            onConfirm = {
                showClearHistoryDialog = false
                onClearHistory()
            },
            onDismiss = { showClearHistoryDialog = false }
        )
    }
}

@Composable
private fun SettingsHeading(title: String, subtitle: String, icon: ImageVector) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 2.dp).size(24.dp))
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(
            Modifier.fillMaxWidth().defaultMinSize(minHeight = 64.dp).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = tint)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp))
            }
            Icon(Icons.Outlined.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

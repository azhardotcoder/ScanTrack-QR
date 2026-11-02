package com.scantrack.qr.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.scantrack.qr.presentation.navigation.Screen

@Composable
fun PremiumBottomNavigation(currentRoute: String?, onNavigate: (String) -> Unit, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    val hapticsEnabled = LocalHapticsEnabled.current
    val destinations = listOf(
        Triple(Screen.Home.route, "Home", Icons.Outlined.Home),
        Triple(Screen.History.route, "History", Icons.Outlined.History),
        Triple(Screen.Scanner.route, "Scan", Icons.Outlined.QrCodeScanner),
        Triple(Screen.Settings.route, "Settings", Icons.Outlined.Settings)
    )
    val navigate: (String) -> Unit = { route ->
        if (hapticsEnabled && route != currentRoute) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onNavigate(route)
    }
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        Box(Modifier.navigationBarsPadding(), contentAlignment = Alignment.Center) {
            BoxWithConstraints(Modifier.widthIn(max = AppSpacing.ContentWidth).fillMaxWidth()
                .selectableGroup().padding(horizontal = 8.dp, vertical = 8.dp)) {
                // At large accessibility sizes a two-row arrangement keeps every label
                // readable and every destination visible, without reducing the font.
                val largeText = maxWidth < 440.dp && LocalDensity.current.fontScale > 1.5f
                if (largeText) {
                    Column {
                        destinations.chunked(2).forEach { pair ->
                            Row {
                                pair.forEach { item -> NavigationItem(item, item.first == currentRoute,
                                    { navigate(item.first) }, Modifier.weight(1f), horizontal = true) }
                            }
                        }
                    }
                } else {
                    Row {
                        destinations.forEach { item -> NavigationItem(item, item.first == currentRoute,
                            { navigate(item.first) }, Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavigationItem(item: Triple<String, String, ImageVector>, selected: Boolean,
    onClick: () -> Unit, modifier: Modifier, horizontal: Boolean = false) {
    val (route, label, icon) = item
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val emphasized = route == Screen.Scanner.route
    val iconContent: @Composable () -> Unit = {
        Surface(color = if (selected || emphasized) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.small) {
            Icon(icon, null,
                Modifier.padding(horizontal = if (horizontal) 4.dp else 16.dp, vertical = 4.dp).size(24.dp),
                tint = if (emphasized) MaterialTheme.colorScheme.onPrimaryContainer else color)
        }
    }
    val labelContent: @Composable () -> Unit = {
        Text(label, style = MaterialTheme.typography.labelMedium, color = color,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
    val touchTarget = modifier.clip(MaterialTheme.shapes.medium)
        .selectable(selected = selected, role = Role.Tab, onClick = onClick)
        .heightIn(min = 64.dp).padding(horizontal = 4.dp, vertical = 6.dp)
    if (horizontal) {
        Row(touchTarget, verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            iconContent()
            Box(Modifier.weight(1f)) { labelContent() }
        }
    } else {
        Column(touchTarget, horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)) { iconContent(); labelContent() }
    }
}

package com.scantrack.qr.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scantrack.qr.data.local.entity.QrEntity
import com.scantrack.qr.data.parser.QrTypeDetector
import com.scantrack.qr.data.parser.UpiPaymentParser
import com.scantrack.qr.data.parser.WiFiQrParser
import com.scantrack.qr.presentation.utils.TimeUtils

fun qrTypeLabel(type: String): String = when (type) {
    "URL" -> "URL"
    "PAYMENT" -> "UPI payment"
    "WIFI" -> "Wi-Fi"
    "PHONE" -> "Phone"
    "EMAIL" -> "Email"
    else -> "Text"
}

private fun qrTypeVector(type: String): ImageVector = when (type) {
    "URL" -> Icons.Outlined.Language
    "PAYMENT" -> Icons.Outlined.AccountBalanceWallet
    "WIFI" -> Icons.Outlined.Wifi
    "PHONE" -> Icons.Outlined.Phone
    "EMAIL" -> Icons.Outlined.MailOutline
    else -> Icons.Outlined.Notes
}

@Composable
fun qrTypeColor(type: String): Color {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return when (type) {
        "URL" -> MaterialTheme.colorScheme.primary
        "PAYMENT" -> MaterialTheme.colorScheme.secondary
        "WIFI" -> MaterialTheme.colorScheme.tertiary
        "PHONE" -> if (dark) Color(0xFFBDB5FF) else Color(0xFF6650A4)
        "EMAIL" -> if (dark) Color(0xFF80D6E8) else Color(0xFF00687A)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

fun qrDisplayTitle(qr: QrEntity): String = qr.label?.takeIf { it.isNotBlank() }
    ?: QrTypeDetector.deriveLabel(qr.rawValue, qr.type)

/** List previews intentionally never include a Wi-Fi payload or password. */
fun qrContentPreview(qr: QrEntity): String = when (qr.type) {
    "WIFI" -> WiFiQrParser.parse(qr.rawValue).let { "${it.ssid.ifBlank { "Wi-Fi network" }} · ${it.security}" }
    "PAYMENT" -> UpiPaymentParser.parse(qr.rawValue).let { it.payeeVpa ?: "Review payment details" }
    "PHONE" -> qr.rawValue.substringAfter(':', qr.rawValue)
    "EMAIL" -> qr.rawValue.substringAfter(':', qr.rawValue).substringBefore('?')
    else -> qr.rawValue.replace('\n', ' ').replace('\r', ' ')
}

@Composable
fun QrTypeIcon(type: String, modifier: Modifier = Modifier) {
    val color = qrTypeColor(type)
    Surface(modifier = modifier, color = color.copy(alpha = 0.12f), shape = MaterialTheme.shapes.medium) {
        Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
            Icon(qrTypeVector(type), null, Modifier.size(23.dp), tint = color)
        }
    }
}

@Composable
fun QRTypeBadge(type: String) {
    val color = qrTypeColor(type)
    Surface(color = color.copy(alpha = 0.12f), shape = MaterialTheme.shapes.extraSmall) {
        Text(qrTypeLabel(type), Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
fun QRHistoryItem(qr: QrEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val title = remember(qr.label, qr.rawValue, qr.type) { qrDisplayTitle(qr) }
    val preview = remember(qr.rawValue, qr.type) { qrContentPreview(qr) }
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QrTypeIcon(qr.type)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(preview, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${qrTypeLabel(qr.type)} · ${TimeUtils.formatRelativeTime(qr.lastUsedAt)}",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (qr.isPinned) Icon(Icons.Outlined.PushPin, "Pinned", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun PinnedQrCard(qr: QrEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, modifier = modifier.width(224.dp),
        shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                QrTypeIcon(qr.type)
                Icon(Icons.Outlined.PushPin, "Pinned", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Text(qrDisplayTitle(qr), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(qrTypeLabel(qr.type), style = MaterialTheme.typography.labelMedium, color = qrTypeColor(qr.type))
        }
    }
}

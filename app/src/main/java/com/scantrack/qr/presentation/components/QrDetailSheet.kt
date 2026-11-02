package com.scantrack.qr.presentation.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scantrack.qr.data.local.entity.QrEntity
import com.scantrack.qr.data.parser.QrTypeDetector
import com.scantrack.qr.data.parser.UpiPaymentParser
import com.scantrack.qr.data.parser.WiFiQrParser
import com.scantrack.qr.presentation.scanner.ScanResult
import com.scantrack.qr.presentation.utils.ActionUtils
import com.scantrack.qr.presentation.utils.TimeUtils
import java.text.DateFormat
import java.util.Date

/** History management and fresh detections share the same content and action presentation. */
@Composable
fun QrDetailSheet(
    qr: QrEntity?,
    onDismiss: () -> Unit,
    onPinToggle: (QrEntity) -> Unit,
    onRename: (QrEntity, String) -> Unit,
    onDelete: (QrEntity) -> Unit,
    onOpen: (QrEntity) -> Unit
) {
    if (qr == null) return
    val haptics = LocalHapticFeedback.current
    val hapticsEnabled = LocalHapticsEnabled.current
    var label by remember(qr.id, qr.label) { mutableStateOf(qrDisplayTitle(qr)) }
    var pinned by remember(qr.id, qr.isPinned) { mutableStateOf(qr.isPinned) }
    var renameVisible by remember(qr.id) { mutableStateOf(false) }
    var deleteVisible by remember(qr.id) { mutableStateOf(false) }
    var draftLabel by remember(qr.id) { mutableStateOf(label) }

    ResultSheetFrame(type = qr.type, title = label, onDismiss = onDismiss) {
        QrContentDetails(rawValue = qr.rawValue, type = qr.type, onOpen = { onOpen(qr) })
        ShareQrAction(rawValue = qr.rawValue, type = qr.type)
        Divider(color = MaterialTheme.colorScheme.outlineVariant)
        Text("Organize", style = MaterialTheme.typography.titleSmall)
        DetailActionRow(
            icon = Icons.Outlined.PushPin,
            label = if (pinned) "Unpin QR code" else "Pin QR code",
            onClick = {
                pinned = !pinned
                onPinToggle(qr)
                if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        )
        DetailActionRow(Icons.Outlined.Edit, "Rename label", onClick = {
            draftLabel = label
            renameVisible = true
        })
        DetailActionRow(
            Icons.Outlined.DeleteOutline, "Delete scan", tint = MaterialTheme.colorScheme.error,
            onClick = { deleteVisible = true }
        )
        Divider(color = MaterialTheme.colorScheme.outlineVariant)
        MetadataItem("Saved", remember(qr.createdAt) {
            DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(qr.createdAt))
        })
        MetadataItem("Last used", TimeUtils.formatRelativeTime(qr.lastUsedAt))
        MetadataItem("Times used", qr.usageCount.toString())
    }

    if (renameVisible) {
        AlertDialog(
            onDismissRequest = { renameVisible = false },
            title = { Text("Rename QR code") },
            text = {
                OutlinedTextField(
                    value = draftLabel,
                    onValueChange = { draftLabel = it.take(100) },
                    label = { Text("Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("Choose a name that is easy to find.") }
                )
            },
            confirmButton = {
                TextButton(enabled = draftLabel.isNotBlank(), onClick = {
                    label = draftLabel.trim()
                    onRename(qr, label)
                    renameVisible = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renameVisible = false }) { Text("Cancel") } }
        )
    }
    if (deleteVisible) {
        ConfirmationDialog(
            title = "Delete this scan?",
            message = "This QR code will be removed from your history, pinned items, and quick access. This cannot be undone.",
            confirmLabel = "Delete scan",
            onConfirm = { deleteVisible = false; onDelete(qr) },
            onDismiss = { deleteVisible = false }
        )
    }
}

/** Only an explicit dismissal resumes scanning; launching another app leaves this result visible. */
@Composable
fun QRResultBottomSheet(result: ScanResult, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val entity = remember(result.rawValue) {
        QrEntity(rawValue = result.rawValue, label = result.label, type = result.type)
    }
    ResultSheetFrame(
        type = result.type,
        title = qrDisplayTitle(entity),
        onDismiss = onDismiss,
        captured = true
    ) {
        QrContentDetails(
            rawValue = result.rawValue,
            type = result.type,
            onOpen = { ActionUtils.openQrContent(context, result.rawValue) }
        )
        ShareQrAction(rawValue = result.rawValue, type = result.type)
        SecondaryActionButton(
            text = "Scan another QR code", onClick = onDismiss,
            icon = Icons.Outlined.QrCodeScanner, modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultSheetFrame(
    type: String,
    title: String,
    onDismiss: () -> Unit,
    captured: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.9f).dp
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    QRTypeBadge(type)
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() },
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close QR details")
                }
            }
            if (captured) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                ) {
                    Icon(Icons.Outlined.CheckCircle, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("QR code captured", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            content()
        }
    }
}

@Composable
private fun QrContentDetails(rawValue: String, type: String, onOpen: () -> Unit) {
    val context = LocalContext.current
    when (type) {
        QrTypeDetector.TYPE_PAYMENT -> UpiDetailCard(rawValue, onOpen)
        QrTypeDetector.TYPE_WIFI -> WiFiDetailCard(rawValue, onOpen)
        QrTypeDetector.TYPE_URL -> {
            val domain = remember(rawValue) {
                runCatching { Uri.parse(rawValue).host }.getOrNull() ?: "Website"
            }
            DetailContentCard {
                DetailField("Website", domain, prominent = true)
                DetailField("Full destination", rawValue)
                Text("Review the destination before opening. This website has not been verified.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PrimaryActionButton("Open Website", onOpen, Modifier.fillMaxWidth(), Icons.Outlined.OpenInNew)
            SecondaryActionButton("Copy URL", {
                ActionUtils.copyToClipboard(context, "URL", rawValue)
            }, Modifier.fillMaxWidth(), Icons.Outlined.ContentCopy)
        }
        QrTypeDetector.TYPE_PHONE -> {
            val number = rawValue.substringAfter(':', rawValue)
            DetailContentCard { DetailField("Phone number", number, prominent = true) }
            PrimaryActionButton("Dial", onOpen, Modifier.fillMaxWidth(), Icons.Outlined.Call)
            SecondaryActionButton("Copy number", {
                ActionUtils.copyToClipboard(context, "Phone number", number)
            }, Modifier.fillMaxWidth(), Icons.Outlined.ContentCopy)
        }
        QrTypeDetector.TYPE_EMAIL -> {
            val address = rawValue.substringAfter(':', rawValue).substringBefore('?')
            DetailContentCard { DetailField("Email address", address, prominent = true) }
            PrimaryActionButton("Compose email", onOpen, Modifier.fillMaxWidth(), Icons.Outlined.MailOutline)
            SecondaryActionButton("Copy email address", {
                ActionUtils.copyToClipboard(context, "Email address", address)
            }, Modifier.fillMaxWidth(), Icons.Outlined.ContentCopy)
        }
        else -> TextDetailCard(rawValue)
    }
}

@Composable
private fun UpiDetailCard(rawValue: String, onContinue: () -> Unit) {
    val upi = remember(rawValue) { UpiPaymentParser.parse(rawValue) }
    DetailContentCard {
        Text(if (upi.isMerchant) "Merchant payment" else "UPI payment",
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        DetailField("Payee name", upi.payeeName ?: "Not supplied", prominent = true)
        DetailField("UPI ID", upi.payeeVpa ?: "Not supplied")
        upi.amount?.takeIf { it.isNotBlank() }?.let { DetailField("Amount", it, prominent = true) }
        DetailField("Currency", upi.currency ?: "Not supplied")
        upi.transactionRef?.takeIf { it.isNotBlank() }?.let { DetailField("Transaction reference", it) }
        upi.merchantCode?.takeIf { it.isNotBlank() }?.let { DetailField("Merchant category", it) }
        upi.transactionNote?.takeIf { it.isNotBlank() }?.let { DetailField("Payment note", it) }
        if (!upi.isValid) {
            Text(upi.validationError ?: "This payment QR code is invalid.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
    }
    Text("Confirm the payee and amount in your UPI app. ScanTrack does not verify the payee or confirm payment.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    PrimaryActionButton(
        "Continue to UPI App", onContinue, Modifier.fillMaxWidth(), Icons.Outlined.AccountBalanceWallet,
        enabled = upi.isValid
    )
}

@Composable
private fun WiFiDetailCard(rawValue: String, onConnect: () -> Unit) {
    val wifi = remember(rawValue) { WiFiQrParser.parse(rawValue) }
    var passwordVisible by remember(rawValue) { mutableStateOf(false) }
    val context = LocalContext.current
    DetailContentCard {
        DetailField("Network name", wifi.ssid.ifBlank { "Not supplied" }, prominent = true)
        DetailField("Security", if (wifi.security == "nopass") "Open network" else wifi.security)
        if (wifi.isHidden) DetailField("Visibility", "Hidden network")
        if (wifi.password.isNotEmpty()) {
            Text("Password", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (passwordVisible) {
                SelectionContainer { Text(wifi.password, style = MaterialTheme.typography.bodyLarge) }
            } else {
                Text("••••••••", style = MaterialTheme.typography.bodyLarge)
            }
            TextButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (passwordVisible) "Hide password" else "Show password")
            }
        }
        if (!wifi.isValid) {
            Text(wifi.validationError ?: "This Wi-Fi QR code is invalid.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
    }
    PrimaryActionButton("Connect to Wi-Fi", onConnect, Modifier.fillMaxWidth(), Icons.Outlined.Wifi,
        enabled = wifi.isValid)
    if (wifi.password.isNotEmpty()) {
        SecondaryActionButton("Copy password", {
            copyWifiPassword(context, wifi.password)
        }, Modifier.fillMaxWidth(), Icons.Outlined.ContentCopy)
    }
    SecondaryActionButton("Copy network name", {
        ActionUtils.copyToClipboard(context, "Network name", wifi.ssid)
    }, Modifier.fillMaxWidth(), Icons.Outlined.ContentCopy)
}

@Composable
private fun TextDetailCard(value: String) {
    val context = LocalContext.current
    var expanded by remember(value) { mutableStateOf(false) }
    var canExpand by remember(value) { mutableStateOf(false) }
    DetailContentCard {
        Text("Text content", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer {
            Text(
                value, style = MaterialTheme.typography.bodyLarge,
                maxLines = if (expanded) Int.MAX_VALUE else 6,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { if (!expanded) canExpand = it.hasVisualOverflow }
            )
        }
        if (canExpand || expanded) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Show less" else "Show full content")
            }
        }
    }
    PrimaryActionButton("Copy text", {
        ActionUtils.copyToClipboard(context, "Text", value)
    }, Modifier.fillMaxWidth(), Icons.Outlined.ContentCopy)
}

@Composable
private fun ShareQrAction(rawValue: String, type: String) {
    val context = LocalContext.current
    var confirmShare by remember(rawValue) { mutableStateOf(false) }
    SecondaryActionButton("Share", {
        if (type == QrTypeDetector.TYPE_WIFI) confirmShare = true
        else ActionUtils.shareText(context, rawValue)
    }, Modifier.fillMaxWidth(), Icons.Outlined.Share)
    if (confirmShare) {
        ConfirmationDialog(
            title = "Share Wi-Fi details?",
            message = "The shared QR content includes the network password. Share only with people you trust.",
            confirmLabel = "Share details",
            onConfirm = { confirmShare = false; ActionUtils.shareText(context, rawValue) },
            onDismiss = { confirmShare = false },
            destructive = false
        )
    }
}

@Composable
private fun DetailContentCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun DetailField(label: String, value: String, prominent: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer {
            Text(value, style = if (prominent) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun MetadataItem(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DetailActionRow(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Surface(onClick = onClick, color = Color.Transparent, shape = RoundedCornerShape(12.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                color = tint, modifier = Modifier.weight(1f))
        }
    }
}

/** Mark credentials sensitive so Android's clipboard preview does not expose the password. */
private fun copyWifiPassword(context: Context, password: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Wi-Fi password", password)
    clip.description.extras = PersistableBundle().apply {
        putBoolean("android.content.extra.IS_SENSITIVE", true)
    }
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Wi-Fi password copied", Toast.LENGTH_SHORT).show()
}

package com.scantrack.qr.presentation.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scantrack.qr.data.local.entity.QrEntity
import com.scantrack.qr.data.parser.QrTypeDetector
import com.scantrack.qr.data.parser.UpiPaymentParser
import com.scantrack.qr.data.parser.WiFiQrParser
import com.scantrack.qr.presentation.utils.ActionUtils
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
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
    val context = LocalContext.current
    var isRenaming by remember { mutableStateOf(false) }
    var newLabel by remember { mutableStateOf(qr.label ?: "") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header: Type Chip, Label / Rename & Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Surface(
                        color = Color(0xFF2563EB).copy(alpha = 0.1f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = qr.type,
                            color = Color(0xFF2563EB),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    if (isRenaming) {
                        TextField(
                            value = newLabel,
                            onValueChange = { newLabel = it },
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = {
                                    onRename(qr, newLabel)
                                    isRenaming = false
                                }) {
                                    Icon(Icons.Default.Check, contentDescription = "Save")
                                }
                            }
                        )
                    } else {
                        Text(
                            text = qr.label ?: "QR Code",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface)
                }
            }

            Spacer(Modifier.height(14.dp))

            // Metadata Row: Created, Uses, Last Used
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MetadataItem(label = "Created", value = formatDate(qr.createdAt))
                MetadataItem(label = "Scans", value = qr.usageCount.toString())
                MetadataItem(label = "Last Used", value = formatDate(qr.lastUsedAt))
            }

            Spacer(Modifier.height(16.dp))

            // Content-Specific Section
            when (qr.type) {
                QrTypeDetector.TYPE_PAYMENT -> {
                    UpiDetailCard(
                        rawValue = qr.rawValue,
                        onPayClick = { onOpen(qr) }
                    )
                }
                QrTypeDetector.TYPE_WIFI -> {
                    WiFiDetailCard(
                        rawValue = qr.rawValue,
                        onConnectClick = { onOpen(qr) }
                    )
                }
                QrTypeDetector.TYPE_URL -> {
                    UrlDetailCard(
                        url = qr.rawValue,
                        onOpen = { onOpen(qr) }
                    )
                }
                QrTypeDetector.TYPE_PHONE -> {
                    PhoneDetailCard(
                        phone = qr.rawValue,
                        onDial = { onOpen(qr) }
                    )
                }
                QrTypeDetector.TYPE_EMAIL -> {
                    EmailDetailCard(
                        email = qr.rawValue,
                        onSend = { onOpen(qr) }
                    )
                }
                else -> {
                    TextDetailCard(
                        text = qr.rawValue
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            // Secondary Actions List
            ActionRow(
                icon = Icons.Default.PushPin,
                label = if (qr.isPinned) "Unpin from top" else "Pin to top",
                tint = if (qr.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                onClick = { onPinToggle(qr) }
            )
            ActionRow(
                icon = Icons.Default.Edit,
                label = "Rename label",
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = { isRenaming = true }
            )
            ActionRow(
                icon = Icons.Default.Share,
                label = "Share with others",
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = { ActionUtils.shareText(context, qr.rawValue) }
            )
            ActionRow(
                icon = Icons.Default.Delete,
                label = "Delete scan",
                tint = MaterialTheme.colorScheme.error,
                onClick = { onDelete(qr) }
            )
        }
    }
}

@Composable
private fun UpiDetailCard(
    rawValue: String,
    onPayClick: () -> Unit
) {
    val upi = remember(rawValue) { UpiPaymentParser.parse(rawValue) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (!upi.payeeName.isNullOrBlank()) {
                Text(text = "Payee", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Text(text = upi.payeeName, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))
            }

            Text(text = "UPI ID / VPA", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Text(text = upi.payeeVpa ?: "Not specified", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)

            if (!upi.amount.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(text = "Amount", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Text(
                    text = "${upi.currency ?: "INR"} ${upi.amount}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (!upi.transactionRef.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(text = "Transaction Ref (tr)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Text(text = upi.transactionRef, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
            }

            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onPayClick,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
            ) {
                Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Pay with UPI App", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun WiFiDetailCard(
    rawValue: String,
    onConnectClick: () -> Unit
) {
    val wifi = remember(rawValue) { WiFiQrParser.parse(rawValue) }
    var passwordVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Network Name (SSID)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Text(text = wifi.ssid, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

            Spacer(Modifier.height(8.dp))
            Text(text = "Security Type", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Text(text = wifi.security, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)

            if (wifi.password.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(text = "Password", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (passwordVisible) wifi.password else "••••••••",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle password",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(onClick = {
                            ActionUtils.copyToClipboard(context, "Wi-Fi Password", wifi.password)
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy password", modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onConnectClick,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Connect", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = { ActionUtils.copyToClipboard(context, "SSID", wifi.ssid) },
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Copy SSID")
                }
            }
        }
    }
}

@Composable
private fun UrlDetailCard(
    url: String,
    onOpen: () -> Unit
) {
    val context = LocalContext.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Web Address", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Text(
                text = url,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onOpen,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Open Browser", fontWeight = FontWeight.Bold)
                }
                IconButton(
                    onClick = { ActionUtils.copyToClipboard(context, "URL", url) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Link")
                }
            }
        }
    }
}

@Composable
private fun PhoneDetailCard(
    phone: String,
    onDial: () -> Unit
) {
    val context = LocalContext.current
    val cleanPhone = phone.removePrefix("tel:")
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Phone Number", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Text(text = cleanPhone, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onDial,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Call Number", fontWeight = FontWeight.Bold)
                }
                IconButton(
                    onClick = { ActionUtils.copyToClipboard(context, "Phone Number", cleanPhone) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                }
            }
        }
    }
}

@Composable
private fun EmailDetailCard(
    email: String,
    onSend: () -> Unit
) {
    val context = LocalContext.current
    val cleanEmail = email.removePrefix("mailto:").substringBefore("?")
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Email Address", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Text(text = cleanEmail, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onSend,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Send Email", fontWeight = FontWeight.Bold)
                }
                IconButton(
                    onClick = { ActionUtils.copyToClipboard(context, "Email", cleanEmail) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                }
            }
        }
    }
}

@Composable
private fun TextDetailCard(
    text: String
) {
    val context = LocalContext.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Content", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Text(
                text = text,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { ActionUtils.copyToClipboard(context, "Text Content", text) },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Copy Text", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = { ActionUtils.shareText(context, text) },
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share")
                }
            }
        }
    }
}

@Composable
private fun MetadataItem(label: String, value: String) {
    Column {
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    tint: Color = Color.Black,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(16.dp))
            Text(text = label, fontSize = 15.sp, color = tint)
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

package com.scantrack.qr.presentation.scanner

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Size
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.scantrack.qr.presentation.utils.ActionUtils
import java.util.concurrent.Executors

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import android.media.AudioManager
import android.media.ToneGenerator

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel,
    onNavigateBack: () -> Unit
) {
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val isFlashOn by viewModel.isFlashOn.collectAsStateWithLifecycle()
    val hapticEnabled by viewModel.hapticEnabled.collectAsStateWithLifecycle()
    
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            uri?.let {
                processGalleryImage(context, it) { rawValue ->
                    viewModel.onQrDetected(rawValue)
                }
            }
        }
    )

    // Handle feedback on scan
    LaunchedEffect(scanState) {
        if (scanState is ScanState.Result) {
            if (hapticEnabled) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        when {
            cameraPermissionState.status.isGranted -> {
                // ---- Camera Preview -----
                CameraPreviewView(
                    isFlashOn = isFlashOn,
                    onQrDetected = { viewModel.onQrDetected(it) }
                )

                // ---- Dark overlay with scanner cutout ----
                ScannerOverlay()

                // ---- Top Controls: Close + Flash ----
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ScannerControlButton(
                        icon = Icons.Default.Close,
                        onClick = onNavigateBack
                    )
                    Text(
                        text = "Scan QR Code",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp
                    )
                    ScannerControlButton(
                        icon = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        onClick = { viewModel.toggleFlash() }
                    )
                }

                // ---- Center Hint ----
                Text(
                    text = "Point at a QR code to scan",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = 160.dp)
                )

                // ---- Bottom Controls (Gallery) ----
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ScannerControlButton(
                        icon = Icons.Default.Image,
                        onClick = { 
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )
                }

                // ---- Scan result bottom sheet ----
                if (scanState is ScanState.Result) {
                    val result = (scanState as ScanState.Result).result
                    ScanResultSheet(
                        result = result,
                        onDismiss = { viewModel.dismissResult() },
                        onNavigateBack = onNavigateBack
                    )
                }
            }
            cameraPermissionState.status.shouldShowRationale -> {
                PermissionRationale(
                    onRequestPermission = { cameraPermissionState.launchPermissionRequest() },
                    onNavigateBack = onNavigateBack
                )
            }
            else -> {
                LaunchedEffect(Unit) { cameraPermissionState.launchPermissionRequest() }
                PermissionRationale(
                    onRequestPermission = { cameraPermissionState.launchPermissionRequest() },
                    onNavigateBack = onNavigateBack
                )
            }
        }
    }
}

/** ML Kit Processing for Gallery Images */
private fun processGalleryImage(context: Context, uri: android.net.Uri, onDetected: (String) -> Unit) {
    try {
        val image = InputImage.fromFilePath(context, uri)
        val scanner = BarcodeScanning.getClient()
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                barcodes.firstOrNull { it.format == Barcode.FORMAT_QR_CODE }
                    ?.rawValue
                    ?.let { onDetected(it) } ?: Toast.makeText(context, "No QR Code found in image", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(context, "Failed to process image", Toast.LENGTH_SHORT).show()
            }
    } catch (e: Exception) {
        Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun CameraPreviewView(
    isFlashOn: Boolean,
    onQrDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }

    LaunchedEffect(isFlashOn) {
        camera?.cameraControl?.enableTorch(isFlashOn)
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize()
    )

    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val barcodeScanner = BarcodeScanning.getClient()

            @androidx.annotation.OptIn(ExperimentalGetImage::class)
            val imageAnalyzer = ImageAnalysis.Builder()
                // Dynamic resolution: prefer higher quality if available, falling back to 720p
                .setTargetResolution(Size(1280, 720)) 
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(executor) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage != null) {
                            val image = InputImage.fromMediaImage(
                                mediaImage, imageProxy.imageInfo.rotationDegrees
                            )
                            barcodeScanner.process(image)
                                .addOnSuccessListener { barcodes ->
                                    barcodes
                                        .firstOrNull { it.format == Barcode.FORMAT_QR_CODE }
                                        ?.rawValue
                                        ?.let { onQrDetected(it) }
                                }
                                .addOnCompleteListener { imageProxy.close() }
                        } else {
                            imageProxy.close()
                        }
                    }
                }

            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalyzer
            )
        }
        cameraProviderFuture.addListener(listener, ContextCompat.getMainExecutor(context))

        onDispose {
            executor.shutdown()
            cameraProviderFuture.get().unbindAll()
        }
    }
}

@Composable
private fun ScannerOverlay() {
    val frameSize = 260.dp
    // Animated scan line
    val infiniteTransition = rememberInfiniteTransition(label = "scanLine")
    val scanLineY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanLineY"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Dark overlay around the scan frame
        // Top overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.5f - 0.15f)
                .background(Color.Black.copy(alpha = 0.6f))
        )
        // Bottom overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.5f - 0.15f)
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.6f))
        )

        // Scan frame in center
        Box(
            modifier = Modifier
                .size(frameSize)
                .align(Alignment.Center)
                .border(2.dp, Color.White, RoundedCornerShape(16.dp))
        ) {
            // Corner accents
            CornerAccent(Alignment.TopStart)
            CornerAccent(Alignment.TopEnd)
            CornerAccent(Alignment.BottomStart)
            CornerAccent(Alignment.BottomEnd)

            // Animated scan line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .offset(y = (frameSize * scanLineY) - 1.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFF2563EB),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
    }
}

@Composable
private fun BoxScope.CornerAccent(alignment: Alignment) {
    val isTop = alignment == Alignment.TopStart || alignment == Alignment.TopEnd
    val isLeft = alignment == Alignment.TopStart || alignment == Alignment.BottomStart
    val cornerLength = 24.dp
    val cornerWidth = 3.dp

    Box(
        modifier = Modifier
            .size(cornerLength)
            .align(alignment)
            .padding(
                top = if (!isTop) cornerLength - cornerWidth else 0.dp,
                bottom = if (isTop) cornerLength - cornerWidth else 0.dp,
                start = if (!isLeft) cornerLength - cornerWidth else 0.dp,
                end = if (isLeft) cornerLength - cornerWidth else 0.dp
            )
            .background(Color(0xFF2563EB), RoundedCornerShape(4.dp))
    )
}

@Composable
private fun ScannerControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(44.dp),
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.15f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanResultSheet(
    result: ScanResult,
    onDismiss: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    // Type chip
                    Surface(
                        color = Color(0xFF2563EB).copy(alpha = 0.1f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = result.type,
                            color = Color(0xFF2563EB),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = result.label,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                ScannerControlButton(
                    icon = Icons.Default.Close,
                    onClick = onDismiss
                )
            }

            Spacer(Modifier.height(8.dp))

            // Raw value preview
            Surface(
                color = Color(0xFFF1F5F9),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = result.rawValue,
                    fontSize = 13.sp,
                    color = Color(0xFF475569),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Spacer(Modifier.height(20.dp))

            // Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Open
                ResultActionButton(
                    label = "Open",
                    icon = Icons.Default.OpenInNew,
                    isPrimary = true,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        // Dismiss BEFORE launching the intent to reset state and prevent task coupling
                        onDismiss()
                        ActionUtils.openQrContent(context, result.rawValue)
                    }
                )
                // Copy
                ResultActionButton(
                    label = "Copy",
                    icon = Icons.Default.ContentCopy,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("QR Value", result.rawValue))
                        Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            Spacer(Modifier.height(12.dp))

            // Scan again
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Scan Another")
            }
        }
    }
}

@Composable
private fun ResultActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isPrimary) Color(0xFF2563EB) else Color(0xFFF1F5F9),
            contentColor = if (isPrimary) Color.White else Color(0xFF334155)
        )
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
private fun PermissionRationale(
    onRequestPermission: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Back", tint = Color.White)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Default.CameraAlt,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(72.dp)
            )
            Text(
                "Camera Access Needed",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            )
            Text(
                "ScanTrack needs camera access to scan QR codes. Your camera is only used while scanning — no photos are saved.",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                lineHeight = 22.sp
            )
            Button(
                onClick = onRequestPermission,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
            ) {
                Text("Allow Camera Access", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

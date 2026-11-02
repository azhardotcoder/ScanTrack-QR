package com.scantrack.qr.presentation.scanner

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Size
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.scantrack.qr.presentation.components.LocalHapticsEnabled
import com.scantrack.qr.presentation.components.LocalReduceMotion
import com.scantrack.qr.presentation.components.PrimaryActionButton
import com.scantrack.qr.presentation.components.QRResultBottomSheet
import com.scantrack.qr.presentation.components.SecondaryActionButton
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel,
    onNavigateBack: () -> Unit,
    openGalleryOnEntry: Boolean = false
) {
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val isFlashOn by viewModel.isFlashOn.collectAsStateWithLifecycle()
    val hapticEnabled by viewModel.hapticEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    // A false rationale also describes a fresh install, not just permanent denial.
    val permissionPreferences = remember(context) {
        context.getSharedPreferences("scanner_permission_ui", Context.MODE_PRIVATE)
    }
    var hasRequestedCamera by rememberSaveable {
        mutableStateOf(permissionPreferences.getBoolean("camera_requested", false))
    }
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    var isScreenDisposed by remember { mutableStateOf(false) }
    var galleryOpenedOnEntry by rememberSaveable { mutableStateOf(false) }
    var resultFeedbackDelivered by rememberSaveable { mutableStateOf(false) }
    val activity = remember(context) { context.findActivity() }
    val view = LocalView.current
    val darkSystemIcons = !cameraPermissionState.status.isGranted &&
        MaterialTheme.colorScheme.background.luminance() > 0.5f
    SideEffect {
        activity?.let {
            WindowCompat.getInsetsController(it.window, view).apply {
                isAppearanceLightStatusBars = darkSystemIcons
                isAppearanceLightNavigationBars = darkSystemIcons
            }
        }
    }

    DisposableEffect(Unit) {
        isScreenDisposed = false
        // A ViewModel result survives activity recreation until explicit dismissal.
        if (viewModel.scanState.value !is ScanState.Result) viewModel.resetScanner()
        onDispose {
            isScreenDisposed = true
            // Still invalidate pending gallery results when actually leaving.
            if (activity?.isChangingConfigurations != true) viewModel.resetScanner()
        }
    }

    // Gallery does not depend on camera permission. Keep the existing session
    // guards so a late result cannot reach a different screen or scan session.
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            uri?.let {
                val sessionToken = viewModel.getCurrentSessionToken()
                processGalleryImage(
                    context = context,
                    uri = it,
                    isDisposed = { isScreenDisposed || viewModel.getCurrentSessionToken() != sessionToken }
                ) { rawValue ->
                    if (!isScreenDisposed && viewModel.getCurrentSessionToken() == sessionToken) {
                        viewModel.onQrDetected(rawValue, sessionToken)
                    }
                }
            }
        }
    )
    val pickFromGallery: () -> Unit = {
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }
    LaunchedEffect(openGalleryOnEntry) {
        if (openGalleryOnEntry && !galleryOpenedOnEntry) {
            galleryOpenedOnEntry = true
            pickFromGallery()
        }
    }
    LaunchedEffect(scanState) {
        if (scanState is ScanState.Result) {
            if (!resultFeedbackDelivered) {
                resultFeedbackDelivered = true
                if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        } else {
            resultFeedbackDelivered = false
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (cameraPermissionState.status.isGranted) {
            CameraPreviewView(
                isFlashOn = isFlashOn,
                isScanningActive = scanState is ScanState.Scanning,
                onQrDetected = { viewModel.onQrDetected(it) }
            )
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ScannerActionButton(
                        icon = Icons.Outlined.ArrowBack,
                        label = "Back",
                        onClick = onNavigateBack
                    )
                    Text(
                        "Scan QR",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                    )
                    ScannerActionButton(
                        icon = if (isFlashOn) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff,
                        label = if (isFlashOn) "Turn flashlight off" else "Turn flashlight on",
                        selected = isFlashOn,
                        onClick = viewModel::toggleFlash
                    )
                }
                ScannerOverlay(
                    scanning = scanState is ScanState.Scanning,
                    success = scanState is ScanState.Result,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
                Column(
                    modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        if (scanState is ScanState.Result) "QR captured" else "Place a QR code inside the frame",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    OutlinedButton(
                        onClick = pickFromGallery,
                        modifier = Modifier.heightIn(min = 48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Outlined.PhotoLibrary, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Scan from Gallery")
                    }
                }
            }
        } else {
            PermissionRationale(
                permanentlyDenied = hasRequestedCamera && !cameraPermissionState.status.shouldShowRationale,
                onRequestPermission = {
                    hasRequestedCamera = true
                    permissionPreferences.edit().putBoolean("camera_requested", true).apply()
                    cameraPermissionState.launchPermissionRequest()
                },
                onPickFromGallery = pickFromGallery,
                onNavigateBack = onNavigateBack
            )
        }
        if (scanState is ScanState.Result) {
            QRResultBottomSheet(
                result = (scanState as ScanState.Result).result,
                onDismiss = { viewModel.dismissResult() }
            )
        }
    }
}

/** Existing ML Kit gallery processing with lifecycle and session cancellation guards. */
private fun processGalleryImage(
    context: Context,
    uri: Uri,
    isDisposed: () -> Boolean,
    onDetected: (String) -> Unit
) {
    try {
        val image = InputImage.fromFilePath(context, uri)
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        val scanner = BarcodeScanning.getClient(options)

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                if (isDisposed()) return@addOnSuccessListener
                val detected = barcodes.firstOrNull { it.format == Barcode.FORMAT_QR_CODE }?.rawValue
                if (!detected.isNullOrBlank()) {
                    onDetected(detected)
                } else {
                    if (!isDisposed()) {
                        Toast.makeText(context, "No QR Code found in selected image", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .addOnFailureListener {
                if (!isDisposed()) {
                    Toast.makeText(context, "Failed to read image", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnCompleteListener {
                scanner.close()
            }
    } catch (e: Exception) {
        if (!isDisposed()) {
            Toast.makeText(context, "Could not open selected image", Toast.LENGTH_SHORT).show()
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun CameraPreviewView(
    isFlashOn: Boolean,
    isScanningActive: Boolean,
    onQrDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val currentScanningState by rememberUpdatedState(isScanningActive)
    val currentOnDetected by rememberUpdatedState(onQrDetected)

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    var camera by remember { mutableStateOf<Camera?>(null) }

    LaunchedEffect(camera, isFlashOn) {
        try {
            camera?.cameraControl?.enableTorch(isFlashOn)
        } catch (ignored: Exception) {
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize()
    )

    DisposableEffect(lifecycleOwner) {
        var isDisposed = false
        val executor = Executors.newSingleThreadExecutor()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        val barcodeScanner = BarcodeScanning.getClient(options)

        val listener = Runnable {
            // Guard against late execution if disposed before future completes
            if (isDisposed) return@Runnable

            try {
                val cameraProvider = cameraProviderFuture.get()
                if (isDisposed) {
                    cameraProvider.unbindAll()
                    return@Runnable
                }

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val resolutionSelector = ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(1280, 720),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        )
                    )
                    .build()

                @androidx.annotation.OptIn(ExperimentalGetImage::class)
                val imageAnalyzer = ImageAnalysis.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(executor) { imageProxy ->
                            // Bounded frame processing: Skip ML Kit if result sheet is already open or disposed
                            if (isDisposed || !currentScanningState) {
                                imageProxy.close()
                                return@setAnalyzer
                            }

                            val mediaImage = imageProxy.image
                            if (mediaImage != null) {
                                val image = InputImage.fromMediaImage(
                                    mediaImage,
                                    imageProxy.imageInfo.rotationDegrees
                                )
                                barcodeScanner.process(image)
                                    .addOnSuccessListener { barcodes ->
                                        if (!isDisposed && currentScanningState) {
                                            barcodes.firstOrNull { it.format == Barcode.FORMAT_QR_CODE }
                                                ?.rawValue
                                                ?.let { currentOnDetected(it) }
                                        }
                                    }
                                    .addOnCompleteListener {
                                        imageProxy.close()
                                    }
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
            } catch (e: Exception) {
                // Camera binding or provider resolution error
            }
        }

        cameraProviderFuture.addListener(listener, ContextCompat.getMainExecutor(context))

        onDispose {
            isDisposed = true
            try {
                if (cameraProviderFuture.isDone) {
                    cameraProviderFuture.get().unbindAll()
                }
            } catch (ignored: Exception) {
            }
            barcodeScanner.close()
            executor.shutdown()
        }
    }
}

/** Use the space left by the controls, including large font and landscape layouts. */
@Composable
private fun ScannerOverlay(
    scanning: Boolean,
    success: Boolean,
    modifier: Modifier = Modifier
) {
    val reduceMotion = LocalReduceMotion.current
    val progress: State<Float> = if (scanning && !reduceMotion) {
        val transition = rememberInfiniteTransition(label = "scanner sweep")
        transition.animateFloat(
            initialValue = 0.08f,
            targetValue = 0.92f,
            animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "scan line position"
        )
    } else {
        rememberUpdatedState(0.5f)
    }
    Canvas(modifier = modifier.semantics { contentDescription = "QR scanning area" }) {
        val side = minOf(size.width - 48.dp.toPx(), size.height - 32.dp.toPx(), 320.dp.toPx())
            .coerceAtLeast(0f)
        val left = (size.width - side) / 2f
        val top = (size.height - side) / 2f
        val right = left + side
        val bottom = top + side
        val scrim = Color.Black.copy(alpha = 0.55f)
        drawRect(scrim, size = ComposeSize(size.width, top))
        drawRect(scrim, Offset(0f, bottom), ComposeSize(size.width, size.height - bottom))
        drawRect(scrim, Offset(0f, top), ComposeSize(left, side))
        drawRect(scrim, Offset(right, top), ComposeSize(size.width - right, side))
        val corner = minOf(28.dp.toPx(), side / 4f)
        val radius = minOf(14.dp.toPx(), corner / 2f)
        val outline = if (success) Color(0xFF6EE7B7) else Color.White
        val corners = Path().apply {
            moveTo(left, top + corner)
            lineTo(left, top + radius)
            quadraticBezierTo(left, top, left + radius, top)
            lineTo(left + corner, top)
            moveTo(right - corner, top)
            lineTo(right - radius, top)
            quadraticBezierTo(right, top, right, top + radius)
            lineTo(right, top + corner)
            moveTo(right, bottom - corner)
            lineTo(right, bottom - radius)
            quadraticBezierTo(right, bottom, right - radius, bottom)
            lineTo(right - corner, bottom)
            moveTo(left + corner, bottom)
            lineTo(left + radius, bottom)
            quadraticBezierTo(left, bottom, left, bottom - radius)
            lineTo(left, bottom - corner)
        }
        drawPath(corners, outline, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        if (scanning && side > 0f) {
            val y = top + side * progress.value
            drawLine(
                color = Color(0xFF9DB8FF).copy(alpha = 0.8f),
                start = Offset(left + 14.dp.toPx(), y),
                end = Offset(right - 14.dp.toPx(), y),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

/** A 48 dp control with ripple, an explicit label, and a spoken toggle state. */
@Composable
fun ScannerActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean? = null
) {
    val haptic = LocalHapticFeedback.current
    val hapticsEnabled = LocalHapticsEnabled.current
    Surface(
        onClick = {
            if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        modifier = modifier.size(48.dp).semantics {
            if (selected != null) stateDescription = if (selected) "On" else "Off"
        },
        shape = CircleShape,
        color = if (selected == true) Color.White else Color(0xFF242B38),
        contentColor = if (selected == true) Color(0xFF111827) else Color.White
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
internal fun PermissionRationale(
    permanentlyDenied: Boolean,
    onRequestPermission: () -> Unit,
    onPickFromGallery: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding().verticalScroll(rememberScrollState())
    ) {
        IconButton(onClick = onNavigateBack, modifier = Modifier.padding(12.dp)) {
            Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
        }
        Column(
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()
                .align(Alignment.CenterHorizontally).padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(88.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.QrCodeScanner, null, modifier = Modifier.size(40.dp))
                }
            }
            Text(
                "A little access.\nA lot less typing.",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                if (permanentlyDenied) {
                    "Camera access is turned off. Enable it in app settings to scan with your camera, or choose a QR image from your gallery."
                } else {
                    "Allow camera access to scan QR codes. You can also choose a QR image from your gallery without camera permission."
                },
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            PrimaryActionButton(
                text = if (permanentlyDenied) "Open Settings" else "Allow Camera",
                icon = Icons.Outlined.CameraAlt,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (permanentlyDenied) {
                        try {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            })
                        } catch (_: Exception) {
                            Toast.makeText(context, "Open this app's permissions in Android Settings", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        onRequestPermission()
                    }
                }
            )
            SecondaryActionButton(
                text = "Scan from Gallery",
                icon = Icons.Outlined.PhotoLibrary,
                modifier = Modifier.fillMaxWidth(),
                onClick = onPickFromGallery
            )
            Text(
                "Your scans stay on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

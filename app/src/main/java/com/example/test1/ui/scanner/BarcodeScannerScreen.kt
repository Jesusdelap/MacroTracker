package com.example.test1.ui.scanner

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.test1.MacroApp
import com.example.test1.R
import com.example.test1.data.api.BarcodeLookupError
import com.example.test1.data.api.BarcodeLookupException
import com.example.test1.data.api.BarcodeResult
import com.example.test1.ui.theme.Spacing
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch

private sealed class ScannerState {
    object Scanning : ScannerState()
    object Loading : ScannerState()
    data class Error(val reason: BarcodeLookupError, val barcode: String) : ScannerState()
}

@Composable
fun BarcodeScannerScreen(
    onProductFound: (BarcodeResult) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app     = context.applicationContext as MacroApp
    val scope   = rememberCoroutineScope()

    var state       by remember { mutableStateOf<ScannerState>(ScannerState.Scanning) }
    var lastBarcode by remember { mutableStateOf("") }
    var lastScanMs  by remember { mutableStateOf(0L) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    fun onBarcodeDetected(barcode: String) {
        val now = System.currentTimeMillis()
        if (barcode == lastBarcode && now - lastScanMs < 3_000) return
        if (state is ScannerState.Loading) return
        lastBarcode = barcode
        lastScanMs  = now
        state = ScannerState.Loading
        scope.launch {
            app.barcodeNutritionService.lookup(barcode)
                .onSuccess { result -> state = ScannerState.Scanning; onProductFound(result) }
                .onFailure { e ->
                    state = ScannerState.Error(
                        reason = (e as? BarcodeLookupException)?.reason ?: BarcodeLookupError.Unknown,
                        barcode = barcode
                    )
                }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        if (hasCameraPermission) {
            CameraPreviewWithScanner(onBarcodeDetected = ::onBarcodeDetected)
        }

        ScannerOverlay()

        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.scanner_back_cd),
                    tint = Color.White
                )
            }
            Text(
                stringResource(R.string.scanner_title),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
        }

        when (val s = state) {
            is ScannerState.Loading -> LoadingOverlay()
            is ScannerState.Error   -> ErrorCard(
                reason = s.reason,
                barcode = s.barcode,
                onRetry = { state = ScannerState.Scanning; lastBarcode = "" },
                onCancel = onBack
            )
            ScannerState.Scanning   -> HintLabel()
        }

        if (!hasCameraPermission) {
            PermissionDeniedOverlay(
                onGrant = { permissionLauncher.launch(Manifest.permission.CAMERA) }
            )
        }
    }
}

@Composable
private fun BoxScope.HintLabel() {
    Text(
        stringResource(R.string.scanner_hint),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(bottom = Spacing.xl),
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.85f)
    )
}

@Composable
private fun BoxScope.LoadingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            CircularProgressIndicator(color = Color.White)
            Text(
                stringResource(R.string.scanner_looking_up),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun BoxScope.ErrorCard(
    reason: BarcodeLookupError,
    barcode: String,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    val title = when (reason) {
        BarcodeLookupError.NotFound -> stringResource(R.string.scanner_error_not_found_title)
        BarcodeLookupError.Network -> stringResource(R.string.scanner_error_network_title)
        BarcodeLookupError.ServiceUnavailable -> stringResource(R.string.scanner_error_service_title)
        BarcodeLookupError.Configuration -> stringResource(R.string.scanner_error_config_title)
        BarcodeLookupError.Unknown -> stringResource(R.string.scanner_error_unknown_title)
    }
    val message = when (reason) {
        BarcodeLookupError.NotFound -> stringResource(R.string.scanner_error_not_found_message)
        BarcodeLookupError.Network -> stringResource(R.string.scanner_error_network_message)
        BarcodeLookupError.ServiceUnavailable -> stringResource(R.string.scanner_error_service_message)
        BarcodeLookupError.Configuration -> stringResource(R.string.scanner_error_config_message)
        BarcodeLookupError.Unknown -> stringResource(R.string.scanner_error_unknown_message)
    }

    Surface(
        modifier = Modifier
            .align(Alignment.Center)
            .padding(Spacing.xl),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Column(
            modifier = Modifier.padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Surface(
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.10f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    stringResource(R.string.scanner_error_code_label, barcode),
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedButton(onClick = onRetry) {
                    Text(stringResource(R.string.scanner_retry))
                }
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    }
}

@Composable
private fun BoxScope.PermissionDeniedOverlay(onGrant: () -> Unit) {
    Column(
        modifier = Modifier.align(Alignment.Center),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Text(
            stringResource(R.string.scanner_permission_required),
            color = Color.White,
            style = MaterialTheme.typography.titleSmall
        )
        Button(onClick = onGrant) {
            Text(stringResource(R.string.scanner_grant_permission))
        }
    }
}

@Composable
private fun ScannerOverlay() {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    ) {
        val frameW = size.width * 0.78f
        val frameH = frameW * 0.55f
        val left   = (size.width  - frameW) / 2f
        val top    = (size.height - frameH) / 2f
        val radius = CornerRadius(20.dp.toPx())

        drawRect(Color.Black.copy(alpha = 0.55f))
        drawRoundRect(
            color        = Color.Transparent,
            topLeft      = Offset(left, top),
            size         = Size(frameW, frameH),
            cornerRadius = radius,
            blendMode    = BlendMode.Clear
        )
        drawRoundRect(
            color        = Color.White.copy(alpha = 0.9f),
            topLeft      = Offset(left, top),
            size         = Size(frameW, frameH),
            cornerRadius = radius,
            style        = Stroke(width = 2.dp.toPx())
        )
    }
}

@Composable
private fun CameraPreviewWithScanner(onBarcodeDetected: (String) -> Unit) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scanner        = remember { BarcodeScanning.getClient() }
    val previewView    = remember { PreviewView(context) }

    DisposableEffect(lifecycleOwner) {
        var cameraProvider: ProcessCameraProvider? = null
        val future = ProcessCameraProvider.getInstance(context)

        future.addListener({
            cameraProvider = future.get()
            val preview = Preview.Builder().build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { ia ->
                    ia.setAnalyzer(ContextCompat.getMainExecutor(context)) { proxy ->
                        analyzeBarcode(proxy, scanner, onBarcodeDetected)
                    }
                }

            runCatching {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            cameraProvider?.unbindAll()
            scanner.close()
        }
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

@OptIn(ExperimentalGetImage::class)
private fun analyzeBarcode(
    imageProxy: ImageProxy,
    scanner: BarcodeScanner,
    onDetected: (String) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) { imageProxy.close(); return }
    val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    scanner.process(input)
        .addOnSuccessListener { barcodes ->
            barcodes.firstOrNull()?.rawValue?.let { onDetected(it) }
        }
        .addOnCompleteListener { imageProxy.close() }
}

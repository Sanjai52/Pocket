package `in`.marxen.pocket.ui.qr

import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import `in`.marxen.pocket.ui.theme.PocketBeige
import `in`.marxen.pocket.ui.theme.PocketGreen
import `in`.marxen.pocket.ui.theme.PocketText
import `in`.marxen.pocket.ui.theme.PocketTextSecondary

@Composable
fun QRScannerScreen(
    onBack: () -> Unit,
    onScanned: (String) -> Unit,
    viewModel: QRScannerViewModel = viewModel(factory = QRScannerViewModel.Factory()),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var hasCameraPermission by remember { mutableStateOf(false) }
    var flashEnabled by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        val perm = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        if (perm == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            hasCameraPermission = true
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(uiState.scannedPayment) {
        uiState.scannedPayment?.let { result ->
            if (result is `in`.marxen.pocket.data.qr.ParseResult.Valid) {
                onScanned(result.payment.rawUri)
            }
        }
    }

    if (!hasCameraPermission) {
        CameraPermissionRequired(onBack = onBack)
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CameraPreview(
            flashEnabled = flashEnabled,
            onBarcodeScanned = { viewModel.onBarcodeScanned(it) },
            modifier = Modifier.fillMaxSize(),
        )

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.3f)),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                    )
                }

                IconButton(
                    onClick = { flashEnabled = !flashEnabled },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.3f)),
                ) {
                    Text(
                        text = if (flashEnabled) "\u26A1" else "\uD83D\uDD26",
                        fontSize = 18.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Point camera at a UPI QR code",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Scans automatically",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        if (uiState.error != null) {
            AlertDialog(
                onDismissRequest = { viewModel.clearError() },
                title = { Text("Scan Error") },
                text = { Text(uiState.error!!) },
                confirmButton = {
                    Button(onClick = { viewModel.clearError() }) {
                        Text("OK")
                    }
                },
            )
        }
    }
}

@Composable
private fun CameraPreview(
    flashEnabled: Boolean,
    onBarcodeScanned: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    var lastScanTime by remember { mutableStateOf(0L) }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            val scanner = BarcodeScanning.getClient(
                BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .build(),
            )

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { imageAnalysis ->
                    imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                        processImage(
                            imageProxy = imageProxy,
                            scanner = scanner,
                            onScanned = { raw ->
                                val now = System.currentTimeMillis()
                                if (now - lastScanTime > 1500) {
                                    lastScanTime = now
                                    onBarcodeScanned(raw)
                                }
                            },
                        )
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, analysis)
            } catch (_: Exception) {}

            previewView
        },
        modifier = modifier,
    )
}

@Composable
private fun CameraPermissionRequired(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PocketBeige)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "\uD83D\uDCF7",
            fontSize = 48.sp,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Camera permission required",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = PocketText,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Pocket needs camera access to scan UPI QR codes. You can enable it in Settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = PocketTextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedButton(onClick = onBack) {
            Text("Go Back")
        }
    }
}

private fun processImage(
    imageProxy: ImageProxy,
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    onScanned: (String) -> Unit,
) {
    @Suppress("UnsafeOptInUsageError")
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val qr = barcodes.firstOrNull { it.valueType == Barcode.TYPE_URL || it.rawValue?.startsWith("upi://pay") == true }
                qr?.rawValue?.let { onScanned(it) }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    } else {
        imageProxy.close()
    }
}

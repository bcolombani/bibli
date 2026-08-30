package fr.bcolombani.bibli.ui.scan

import android.content.Context
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Aperçu caméra plein écran + analyse ML Kit.
 *
 * `STRATEGY_KEEP_ONLY_LATEST` : on analyse toujours l'image la plus récente et on jette
 * le retard accumulé — c'est ce qui permet d'enchaîner les livres sans latence perçue.
 *
 * Quand [analysing] passe à `false` (feuille de saisie manuelle ouverte), l'analyseur est
 * détaché : plus aucune image n'est traitée, sans le clignotement d'un rebind complet.
 */
@Composable
fun CameraScanner(
    analysing: Boolean,
    torchOn: Boolean,
    onBarcode: (String) -> Unit,
    onTorchAvailability: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnBarcode by rememberUpdatedState(onBarcode)
    val currentOnTorchAvailability by rememberUpdatedState(onTorchAvailability)

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val executor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val analyzer = remember { BarcodeAnalyzer { currentOnBarcode(it) } }
    var imageAnalysis by remember { mutableStateOf<ImageAnalysis?>(null) }
    var cameraControl by remember { mutableStateOf<Camera?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            analyzer.close()
            executor.shutdown()
        }
    }

    LaunchedEffect(lifecycleOwner) {
        val provider = runCatching { context.awaitCameraProvider() }
            .onFailure { Log.e(TAG, "CameraX indisponible", it) }
            .getOrNull() ?: return@LaunchedEffect

        val preview = Preview.Builder().build().apply {
            setSurfaceProvider(previewView.surfaceProvider)
        }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        runCatching {
            provider.unbindAll()
            val camera = provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis,
            )
            cameraControl = camera
            imageAnalysis = analysis
            currentOnTorchAvailability(camera.cameraInfo.hasFlashUnit())
        }.onFailure { Log.e(TAG, "Impossible de démarrer la caméra", it) }
    }

    LaunchedEffect(imageAnalysis, analysing) {
        val analysis = imageAnalysis ?: return@LaunchedEffect
        if (analysing) analysis.setAnalyzer(executor, analyzer) else analysis.clearAnalyzer()
    }

    LaunchedEffect(cameraControl, torchOn) {
        runCatching { cameraControl?.cameraControl?.enableTorch(torchOn) }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

/** `ProcessCameraProvider.getInstance` (ListenableFuture) exposé en suspend, annulable. */
private suspend fun Context.awaitCameraProvider(): ProcessCameraProvider {
    val future = ProcessCameraProvider.getInstance(this)
    return suspendCancellableCoroutine { continuation ->
        future.addListener(
            {
                try {
                    continuation.resume(future.get())
                } catch (error: Exception) {
                    continuation.resumeWithException(error)
                }
            },
            ContextCompat.getMainExecutor(this),
        )
    }
}

private const val TAG = "CameraScanner"

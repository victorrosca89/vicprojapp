package md.vicproj.app

import androidx.activity.compose.BackHandler
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.delay
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private const val STATUS_STARTING = "Se pornește camera…"
private const val STATUS_READY = "Îndreaptă camera spre codul QR…"
private const val STATUS_NO_FRAMES =
    "Camera nu trimite imagine. Închide aplicațiile care o folosesc (Zoom, Teams, Camera) " +
        "sau verifică Setări › Aplicații › VicProj › Permisiuni. Apoi apasă REÎNCEARCĂ."

/**
 * Scaner QR (CameraX + ML Kit, model inclus - merge offline).
 * Respecta separarea din documentul tehnic: scanarea extrage DOAR codul de 9 cifre;
 * PIN-ul se introduce mereu manual.
 * Ca in aplicatia Windows: daca nu sosesc cadre in 5 secunde, apare un mesaj clar + REINCEARCA,
 * iar erorile la pornire deschid fereastra de eroare #3.
 */
@OptIn(ExperimentalGetImage::class)
@Composable
fun ScannerScreen(
    onResult: (String) -> Unit,
    onClose: () -> Unit,
    onFatal: (String?) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnFatal by rememberUpdatedState(onFatal)

    var attempt by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf(STATUS_STARTING) }
    var torchOn by remember { mutableStateOf(false) }
    var hasTorch by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    val frames = remember { AtomicInteger(0) }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    BackHandler { onClose() }

    DisposableEffect(attempt) {
        frames.set(0)
        status = STATUS_STARTING
        torchOn = false
        hasTorch = false

        val executor = Executors.newSingleThreadExecutor()
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build(),
        )
        val handled = AtomicBoolean(false)
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null

        future.addListener({
            try {
                val p = future.get()
                provider = p

                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(previewView.surfaceProvider)

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analysis.setAnalyzer(executor) { proxy ->
                    frames.incrementAndGet()
                    val media = proxy.image
                    if (media == null || handled.get()) {
                        proxy.close()
                        return@setAnalyzer
                    }
                    val input = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                    scanner.process(input)
                        .addOnSuccessListener { barcodes ->
                            for (b in barcodes) {
                                val m = Regex("(\\d{9})").find(b.rawValue ?: "")
                                if (m != null && handled.compareAndSet(false, true)) {
                                    currentOnResult(m.groupValues[1])
                                    break
                                }
                            }
                        }
                        .addOnCompleteListener { proxy.close() }
                }

                p.unbindAll()
                val cam = p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                camera = cam
                hasTorch = cam.cameraInfo.hasFlashUnit()
            } catch (e: Exception) {
                currentOnFatal(e.message ?: e.javaClass.simpleName)
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            try {
                provider?.unbindAll()
            } catch (e: Exception) {
                // ignoram
            }
            camera = null
            try {
                scanner.close()
            } catch (e: Exception) {
                // ignoram
            }
            executor.shutdown()
        }
    }

    // Watchdog: dupa 5 secunde fara cadre, camera e ocupata / blocata / nefunctionala.
    LaunchedEffect(attempt) {
        var waited = 0
        while (frames.get() == 0 && waited < 5000) {
            delay(250)
            waited += 250
        }
        status = if (frames.get() > 0) STATUS_READY else STATUS_NO_FRAMES
    }

    // Linia de scanare: urca si coboara lent in interiorul ramei de vizare.
    val scanTransition = rememberInfiniteTransition(label = "scan")
    val scanY by scanTransition.animateFloat(
        0.06f, 0.94f,
        infiniteRepeatable(tween(2400, easing = VicMotion.EaseInOut), RepeatMode.Reverse),
        label = "scanY",
    )

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // Rama de vizare (colturi albe)
        Canvas(
            modifier = Modifier
                .size(250.dp)
                .align(Alignment.Center),
        ) {
            val len = 44.dp.toPx()
            val stroke = 4.dp.toPx()
            val w = size.width
            val h = size.height
            val c = Color.White
            fun line(a: Offset, b: Offset) = drawLine(c, a, b, strokeWidth = stroke, cap = StrokeCap.Square)
            line(Offset(0f, 0f), Offset(len, 0f)); line(Offset(0f, 0f), Offset(0f, len))
            line(Offset(w, 0f), Offset(w - len, 0f)); line(Offset(w, 0f), Offset(w, len))
            line(Offset(0f, h), Offset(len, h)); line(Offset(0f, h), Offset(0f, h - len))
            line(Offset(w, h), Offset(w - len, h)); line(Offset(w, h), Offset(w, h - len))

            // Linia de scanare + o aura difuza deasupra ei (doar alb, fara culoare)
            val y = h * scanY
            val glow = 26.dp.toPx()
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.16f)),
                    startY = y - glow,
                    endY = y,
                ),
                topLeft = Offset(0f, (y - glow).coerceAtLeast(0f)),
                size = androidx.compose.ui.geometry.Size(w, glow.coerceAtMost(y)),
            )
            drawLine(
                Color.White.copy(alpha = 0.9f),
                Offset(0f, y),
                Offset(w, y),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        // Sus: inapoi + lanterna
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MonoButton("←  ÎNAPOI", onClick = onClose, height = 42.dp)
            if (hasTorch) {
                MonoButton(
                    if (torchOn) "LANTERNĂ ✓" else "LANTERNĂ",
                    onClick = {
                        torchOn = !torchOn
                        try {
                            camera?.cameraControl?.enableTorch(torchOn)
                        } catch (e: Exception) {
                            // ignoram
                        }
                    },
                    height = 42.dp,
                )
            }
        }

        // Jos: stare + butoane
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xB3000000))
                .padding(16.dp),
        ) {
            Text(
                text = status,
                color = Color.White,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MonoButton("REÎNCEARCĂ", onClick = { attempt++ }, modifier = Modifier.weight(1f))
                MonoButtonPrimary("INTRODU MANUAL", onClick = onClose, modifier = Modifier.weight(1f))
            }
        }
    }
}

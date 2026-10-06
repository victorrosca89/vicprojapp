package md.vicproj.app

import android.content.Context
import android.content.ContextWrapper
import android.app.Activity
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/**
 * Inchiderea animata a ferestrei: butoanele din interior cer inchiderea prin aceasta functie,
 * ca fereastra sa aibe timp sa dispara (fade + scalare) inainte sa se execute actiunea.
 */
val LocalDialogCloser = androidx.compose.runtime.staticCompositionLocalOf<(action: (() -> Unit)?) -> Unit> {
    { action -> action?.invoke() }
}

/** Cadrul comun al ferestrelor: card negru-gri, bordura subtire, colturi rotunjite (ca AppErrorDialog din Windows). */
@Composable
fun VicDialog(
    onDismiss: () -> Unit,
    dismissable: Boolean = true,
    content: @Composable () -> Unit,
) {
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val scale = remember { Animatable(0.9f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { alpha.animateTo(1f, tween(200, easing = VicMotion.EaseOut)) }
        scale.animateTo(1f, VicMotion.SoftSpring)
    }

    val closer: (action: (() -> Unit)?) -> Unit = { action ->
        if (!closing) {
            closing = true
            scope.launch {
                launch { scale.animateTo(0.94f, tween(180, easing = VicMotion.EaseIn)) }
                alpha.animateTo(0f, tween(160, easing = VicMotion.EaseIn))
                (action ?: onDismiss).invoke()
            }
        }
    }

    Dialog(
        onDismissRequest = { if (dismissable) closer(null) },
        properties = DialogProperties(
            dismissOnBackPress = dismissable,
            dismissOnClickOutside = dismissable,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.runtime.CompositionLocalProvider(LocalDialogCloser provides closer) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 420.dp)
                        .fillMaxWidth()
                        .graphicsLayer {
                            this.alpha = alpha.value
                            scaleX = scale.value
                            scaleY = scale.value
                        }
                        .background(VicColors.Surface, RoundedCornerShape(14.dp))
                        .border(1.dp, VicColors.Border, RoundedCornerShape(14.dp))
                        .padding(20.dp),
                ) {
                    content()
                }
            }
        }
    }
}

/** Fereastra de eroare numerotata: "#1  Fara conexiune", mesaj, detalii tehnice derulabile, "AM INTELES". */
@Composable
fun AppErrorDialog(info: ErrorInfo, onDismiss: () -> Unit) {
    VicDialog(onDismiss = onDismiss) {
        val close = LocalDialogCloser.current
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .background(VicColors.Foreground)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text("#${info.code}", color = VicColors.Background, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                AppError.title(info.code),
                color = VicColors.Foreground,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(AppError.message(info.code), color = VicColors.Muted, fontSize = 14.sp, lineHeight = 20.sp)

        val detail = info.detail
        if (!detail.isNullOrBlank()) {
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 140.dp)
                    .border(1.dp, VicColors.Border)
                    .verticalScroll(rememberScrollState())
                    .padding(10.dp),
            ) {
                Text(
                    "Detalii tehnice: $detail",
                    color = VicColors.MutedDim,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        MonoButtonPrimary("AM ÎNȚELES", onClick = { close(null) }, modifier = Modifier.fillMaxWidth())
    }
}

/** Echivalentul MessageBox (mesaje de validare de la server, "Alege intai un fisier" etc.). */
@Composable
fun MessageDialog(info: MessageInfo, onDismiss: () -> Unit) {
    VicDialog(onDismiss = onDismiss) {
        val close = LocalDialogCloser.current
        Text(info.title, color = VicColors.Foreground, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(10.dp))
        Text(info.message, color = VicColors.Muted, fontSize = 14.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(18.dp))
        MonoButtonPrimary("OK", onClick = { close(null) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun ConfirmDialog(
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    VicDialog(onDismiss = onDismiss) {
        val close = LocalDialogCloser.current
        Text("VicProj", color = VicColors.Foreground, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(10.dp))
        Text(text, color = VicColors.Muted, fontSize = 14.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MonoButton("ANULEAZĂ", onClick = { close(null) }, modifier = Modifier.weight(1f))
            MonoButtonPrimary(confirmLabel, onClick = { close(onConfirm) }, modifier = Modifier.weight(1f))
        }
    }
}

/** Cererea de permisiune din aplicatie, inaintea celei din Android (ca in aplicatia Windows). */
@Composable
fun CameraConsentDialog(onAllow: () -> Unit, onDeny: () -> Unit) {
    VicDialog(onDismiss = onDeny) {
        val close = LocalDialogCloser.current
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .background(VicColors.Foreground)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text("QR", color = VicColors.Background, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                "Permiți accesul la cameră?",
                color = VicColors.Foreground,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "VicProj vrea să folosească camera ca să scaneze codul QR. Imaginea rămâne pe telefonul tău: nu este salvată și nu este trimisă nicăieri.",
            color = VicColors.Muted,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MonoButton("NU ACUM", onClick = { close(null) }, modifier = Modifier.weight(1f))
            MonoButtonPrimary("PERMITE", onClick = { close(onAllow) }, modifier = Modifier.weight(1f))
        }
    }
}

/** Permisiunea a fost refuzata definitiv in Android: singura cale e din Setari. */
@Composable
fun CameraBlockedDialog(onOpenSettings: () -> Unit, onClose: () -> Unit) {
    VicDialog(onDismiss = onClose) {
        val close = LocalDialogCloser.current
        Text("Camera e blocată", color = VicColors.Foreground, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            "Android nu mai permite VicProj să folosească camera. Deschide Setările aplicației, intră la Permisiuni › Cameră și alege „Permite”. Poți introduce codul și manual.",
            color = VicColors.Muted,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MonoButton("ÎNCHIDE", onClick = { close(null) }, modifier = Modifier.weight(1f))
            MonoButtonPrimary("SETĂRI", onClick = { close(onOpenSettings) }, modifier = Modifier.weight(1f))
        }
    }
}

/** Contract propriu: "Salveaza ca" cu nume si tip MIME alese de noi (SAF, fara permisiuni de stocare). */
class CreateDocWithMime : androidx.activity.result.contract.ActivityResultContract<Pair<String, String>, android.net.Uri?>() {
    override fun createIntent(context: Context, input: Pair<String, String>): android.content.Intent {
        return android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
            type = input.second
            putExtra(android.content.Intent.EXTRA_TITLE, input.first)
        }
    }

    override fun parseResult(resultCode: Int, intent: android.content.Intent?): android.net.Uri? {
        return if (resultCode == Activity.RESULT_OK) intent?.data else null
    }
}

/** Codul QR al fisierului: imagine mare, copiere, salvare PNG, distribuire. QR-ul contine DOAR linkul (fara PIN). */
@Composable
fun QrDialog(request: QrRequest, onClose: () -> Unit, onFailed: (String) -> Unit, onFlash: (String) -> Unit) {
    val context = LocalContext.current
    val url = SHARE_URL_PREFIX + request.code

    val bmp: Bitmap? = remember(request) {
        try {
            QrGenerator.generareCodQR(url)
        } catch (e: Throwable) {
            null
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(CreateDocWithMime()) { uri ->
        if (uri != null && bmp != null) {
            if (QrExport.saveToUri(context, bmp, uri)) onFlash("Imagine salvată.") else onFlash("Nu am putut salva imaginea.")
        }
    }

    if (bmp == null) {
        LaunchedEffect(request) { onFailed("Codul QR nu a putut fi generat.") }
        return
    }

    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1800)
            copied = false
        }
    }

    VicDialog(onDismiss = onClose) {
        val close = LocalDialogCloser.current
        Text(
            "Cod QR",
            color = VicColors.Foreground,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            request.filename,
            color = VicColors.Muted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .background(androidx.compose.ui.graphics.Color.White)
                    .padding(6.dp),
            ) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Cod QR",
                    filterQuality = FilterQuality.None,
                    modifier = Modifier.size(240.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            url,
            color = VicColors.Muted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        val pin = request.pin
        Text(
            if (pin.isNullOrEmpty()) "Scanează cu telefonul; PIN-ul se trimite separat." else "PIN: $pin  ·  se arată o singură dată",
            color = if (pin.isNullOrEmpty()) VicColors.MutedDim else VicColors.Foreground,
            fontSize = 12.sp,
            fontFamily = if (pin.isNullOrEmpty()) FontFamily.Default else FontFamily.Monospace,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MonoButton(
                if (copied) "COPIAT ✓" else "COPIAZĂ",
                onClick = {
                    if (QrExport.copyImage(context, bmp, request.code)) {
                        copied = true
                    } else {
                        onFlash("Nu am putut copia imaginea.")
                    }
                },
                modifier = Modifier.weight(1f),
                height = 44.dp,
            )
            MonoButton(
                "SALVEAZĂ",
                onClick = { saveLauncher.launch(Pair("VicProj-QR-${request.code}.png", "image/png")) },
                modifier = Modifier.weight(1f),
                height = 44.dp,
            )
            MonoButton(
                "TRIMITE",
                onClick = {
                    if (!QrExport.shareImage(context, bmp, request.code, url)) onFlash("Nu am putut deschide meniul de distribuire.")
                },
                modifier = Modifier.weight(1f),
                height = 44.dp,
            )
        }
        Spacer(Modifier.height(10.dp))
        MonoButtonPrimary("ÎNCHIDE", onClick = { close(null) }, modifier = Modifier.fillMaxWidth())
    }
}

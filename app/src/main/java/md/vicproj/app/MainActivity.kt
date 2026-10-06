package md.vicproj.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
        )
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            VicTheme {
                AppRoot(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        vm.startMonitoring()
    }

    override fun onStop() {
        vm.stopMonitoring()
        super.onStop()
    }

    private fun handleIntent(i: Intent?) {
        if (i?.action == Intent.ACTION_VIEW) vm.handleDeepLink(i.dataString)
    }
}

private fun mimeFor(fileName: String): String {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
}

@Composable
fun AppRoot(vm: MainViewModel) {
    val ctx = LocalContext.current

    // ---------- lansatoare (selector de fisiere, "Salveaza ca", permisiune camera) ----------
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        vm.pickFile(uri)
    }
    val saveAs = rememberLauncherForActivityResult(CreateDocWithMime()) { uri ->
        if (uri != null) vm.startDownload(uri)
    }
    val camPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            vm.openScanner()
        } else {
            val act = ctx.findActivity()
            val canAskAgain = act != null &&
                ActivityCompat.shouldShowRequestPermissionRationale(act, Manifest.permission.CAMERA)
            if (canAskAgain) {
                vm.showFlash("Permisiunea pentru cameră a fost refuzată.")
            } else {
                vm.showCameraBlocked = true
            }
        }
    }

    val requestCamera: () -> Unit = {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            vm.openScanner()
        } else {
            camPermission.launch(Manifest.permission.CAMERA)
        }
    }

    val onScan: () -> Unit = {
        if (!ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            vm.scannerFailed("Acest dispozitiv nu are cameră.")
        } else if (!vm.hasCameraConsent) {
            vm.showCameraConsent = true
        } else {
            requestCamera()
        }
    }

    val onDownload: () -> Unit = {
        if (vm.validateBeforeDownload()) {
            val name = vm.verified?.filename ?: "fisier"
            saveAs.launch(Pair(name, mimeFor(name)))
        }
    }

    val onOpenFile: () -> Unit = {
        val uri = vm.downloadedUri
        if (uri != null) {
            try {
                val mime = ctx.contentResolver.getType(uri) ?: "*/*"
                val i = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mime)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(i)
            } catch (e: Exception) {
                vm.showFlash("Nu există nicio aplicație care să deschidă fișierul.")
            }
        }
    }

    val openUrl: (String) -> Unit = { url ->
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            vm.error = ErrorInfo(AppError.CANNOT_OPEN_LINK, url)
        }
    }

    val update = vm.update
    val forcedManifest: UpdateManifest? =
        if (update != null && update.urgency == UpdateUrgency.Mandatory) update.manifest else null
    val optionalManifest: UpdateManifest? =
        if (update != null && update.urgency == UpdateUrgency.Optional) update.manifest else null
    val forced = forcedManifest != null

    BackHandler(enabled = forced) { /* actualizare obligatorie: nu se poate iesi */ }
    BackHandler(enabled = !forced && vm.screen != Screen.Public && !vm.showScanner) { vm.backToPublic() }

    // ---------- continut ----------
    var showSplash by remember { mutableStateOf(true) }

    Box(
        Modifier
            .fillMaxSize()
            .background(VicColors.Background),
    ) {
        // Fundal: aurora ambientala (bule albastre, blurate, in miscare permanenta)
        AuroraBackground(Modifier.matchParentSize())

        Box(
            Modifier
                .fillMaxSize()
                .systemBarsPadding(),
        ) {
            if (forcedManifest != null) {
                ForcedUpdateScreen(forcedManifest) { openUrl(forcedManifest.downloadUrl) }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        Crossfade(
                            targetState = vm.screen,
                            animationSpec = tween(420),
                            label = "screen",
                        ) { screen ->
                            when (screen) {
                                Screen.Public -> {
                                    val blocked = vm.connState == ConnectivityState.Offline ||
                                        vm.connState == ConnectivityState.ServerUnreachable
                                    Crossfade(
                                        targetState = blocked && !vm.showScanner,
                                        animationSpec = tween(420),
                                        label = "conn",
                                    ) { isBlocked ->
                                        if (isBlocked) {
                                            ConnectivityScreen(
                                                state = vm.connState,
                                                checking = vm.checkingConnection,
                                                statusText = vm.connStatusText,
                                                onRetry = { vm.retryConnection() },
                                                onLogoTap = { vm.onLogoTap() },
                                            )
                                        } else {
                                            PublicScreen(vm, onScan = onScan, onDownload = onDownload, onOpenFile = onOpenFile)
                                        }
                                    }
                                }
                                Screen.Login -> LoginScreen(vm, onBack = { vm.backToPublic() })
                                Screen.Admin -> AdminScreen(vm, onPickFile = { pickFile.launch(arrayOf("*/*")) })
                            }
                        }

                        if (vm.showScanner) {
                            ScannerScreen(
                                onResult = { vm.onScanned(it) },
                                onClose = { vm.closeScanner() },
                                onFatal = { vm.scannerFailed(it) },
                            )
                        }
                    }
                    StatusBar(vm.connState, vm.flash)
                }
            }
        }

        // Splash animat (~4s): logo fade-in + zoom, cerc care se invarte, apoi totul se stinge.
        if (showSplash) {
            SplashOverlay(onFinished = { showSplash = false })
        }
    }

    // ---------- ferestre ----------
    if (vm.showCameraConsent) {
        CameraConsentDialog(
            onAllow = {
                vm.grantCameraConsent()
                requestCamera()
            },
            onDeny = { vm.showCameraConsent = false },
        )
    }
    if (vm.showCameraBlocked) {
        CameraBlockedDialog(
            onOpenSettings = {
                vm.showCameraBlocked = false
                try {
                    ctx.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                } catch (e: Exception) {
                    vm.showFlash("Deschide manual Setări › Aplicații › VicProj.")
                }
            },
            onClose = { vm.showCameraBlocked = false },
        )
    }

    vm.qrRequest?.let { req ->
        QrDialog(
            request = req,
            onClose = { vm.qrRequest = null },
            onFailed = { detail ->
                vm.qrRequest = null
                vm.error = ErrorInfo(AppError.UNEXPECTED, detail)
            },
            onFlash = { vm.showFlash(it) },
        )
    }

    vm.actionFile?.let { f ->
        FileActionsDialog(
            f = f,
            onCopyLink = {
                vm.actionFile = null
                if (copyText(ctx, "Link VicProj", vm.linkOf(f))) vm.showFlash("Link copiat.")
            },
            onQr = {
                vm.actionFile = null
                vm.qrRequest = QrRequest(f.sharedCode, f.filename, null)
            },
            onRegenerate = {
                vm.actionFile = null
                vm.regeneratePin(f)
            },
            onPause = {
                vm.actionFile = null
                vm.changeStatus(f, "paused")
            },
            onResume = {
                vm.actionFile = null
                vm.changeStatus(f, "active")
            },
            onBlock = {
                vm.actionFile = null
                vm.confirmBlock = f
            },
            onClose = { vm.actionFile = null },
        )
    }

    vm.confirmBlock?.let { f ->
        ConfirmDialog(
            text = "Blochezi definitiv „${f.filename}”? Nu mai poate fi reactivat după asta.",
            confirmLabel = "BLOCHEAZĂ",
            onConfirm = {
                vm.confirmBlock = null
                vm.changeStatus(f, "blocked")
            },
            onDismiss = { vm.confirmBlock = null },
        )
    }

    vm.error?.let { AppErrorDialog(it) { vm.error = null } }
    vm.message?.let { MessageDialog(it) { vm.message = null } }

    if (optionalManifest != null) {
        UpdateDialog(
            manifest = optionalManifest,
            onDownload = {
                vm.dismissUpdate()
                openUrl(optionalManifest.downloadUrl)
            },
            onLater = { vm.dismissUpdate() },
        )
    }
}

package md.vicproj.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============================================================================
//  Logo + antet comun (5 atingeri pe logo = acces ascuns de administrator)
// ============================================================================

@Composable
private fun LogoHeader(onLogoTap: () -> Unit, logoSize: Int = 64, subtitle: String? = "Un cod. Un fișier. Atât.") {
    Image(
        painter = painterResource(R.drawable.logo),
        contentDescription = "VicProj",
        modifier = Modifier
            .size(logoSize.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onLogoTap,
            ),
    )
    Spacer(Modifier.height(10.dp))
    Text("VicProj", color = VicColors.Foreground, fontSize = 30.sp, fontWeight = FontWeight.Bold)
    if (subtitle != null) {
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = VicColors.Muted, fontSize = 13.sp)
    }
}

// ============================================================================
//  Pagina publica: cod (9 cifre) + PIN (6 cifre)
// ============================================================================

@Composable
fun PublicScreen(vm: MainViewModel, onScan: () -> Unit, onDownload: () -> Unit, onOpenFile: () -> Unit) {
    CenteredScroll {
        LogoHeader(onLogoTap = { vm.onLogoTap() })
        Spacer(Modifier.height(30.dp))

        Column(Modifier.widthIn(max = 460.dp).fillMaxWidth()) {
            SectionLabel("COD (9 CIFRE)")
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MonoInput(
                    value = vm.code,
                    onValueChange = { vm.onCodeChange(it) },
                    modifier = Modifier.weight(1f),
                    enabled = !vm.busy,
                    mono = true,
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Go,
                    onAction = { vm.checkCode() },
                    minHeight = 52.dp,
                )
                Spacer(Modifier.size(8.dp))
                MonoButton("QR", onClick = onScan, modifier = Modifier.size(width = 60.dp, height = 52.dp), enabled = !vm.busy, height = 52.dp)
            }
            Spacer(Modifier.height(10.dp))
            MonoButtonPrimary("VERIFICĂ CODUL", onClick = { vm.checkCode() }, modifier = Modifier.fillMaxWidth(), enabled = !vm.busy)

            Spacer(Modifier.height(14.dp))
            Text(
                text = vm.info,
                color = if (vm.infoStrong) VicColors.Foreground else VicColors.Muted,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
            )

            Spacer(Modifier.height(8.dp))
            SectionLabel("PIN (6 CIFRE)")
            Spacer(Modifier.height(6.dp))
            MonoInput(
                value = vm.pin,
                onValueChange = { vm.onPinChange(it) },
                modifier = Modifier.fillMaxWidth(),
                enabled = vm.canDownload,
                mono = true,
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Go,
                onAction = { onDownload() },
                minHeight = 52.dp,
            )
            Spacer(Modifier.height(10.dp))
            MonoButtonPrimary("DESCARCĂ", onClick = onDownload, modifier = Modifier.fillMaxWidth(), enabled = vm.canDownload)

            if (vm.downloadProgress != null) {
                Spacer(Modifier.height(14.dp))
                MonoProgress(vm.downloadProgress)
            }
            if (vm.downloadedUri != null) {
                Spacer(Modifier.height(14.dp))
                MonoButton("DESCHIDE FIȘIERUL", onClick = onOpenFile, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

// ============================================================================
//  Fara internet / server picat: ilustratia (offline.jpg / server_down.png) + Reincearca
// ============================================================================

@Composable
fun ConnectivityScreen(
    state: ConnectivityState,
    checking: Boolean,
    statusText: String,
    onRetry: () -> Unit,
    onLogoTap: () -> Unit,
) {
    val offline = state == ConnectivityState.Offline
    CenteredScroll {
        LogoHeader(onLogoTap = onLogoTap, logoSize = 52, subtitle = null)
        Spacer(Modifier.height(18.dp))

        Image(
            painter = painterResource(if (offline) R.drawable.offline else R.drawable.server_down),
            contentDescription = if (offline) "Fără conexiune la internet" else "Serverul nu răspunde",
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 280.dp)
                .aspectRatio(1f),
        )

        Spacer(Modifier.height(14.dp))
        Text(
            text = if (offline) "Fără conexiune la internet" else "Serverul VicProj nu răspunde",
            color = VicColors.Foreground,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (offline) {
                "Verifică rețeaua Wi‑Fi sau datele mobile. Câmpurile pentru cod și PIN revin automat imediat ce conexiunea este restabilită."
            } else {
                "Ai conexiune la internet, dar serverul nu poate fi contactat momentan. Încearcă din nou în câteva secunde."
            },
            color = VicColors.Muted,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 380.dp),
        )
        Spacer(Modifier.height(20.dp))
        MonoButtonPrimary(
            text = if (checking) "SE VERIFICĂ…" else "REÎNCEARCĂ",
            onClick = onRetry,
            enabled = !checking,
            modifier = Modifier.widthIn(max = 280.dp).fillMaxWidth(),
            height = 50.dp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = statusText,
            color = VicColors.Foreground,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.heightIn(min = 18.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Se reverifică automat la fiecare 5 secunde.",
            color = VicColors.MutedDim,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
        )
    }
}

// ============================================================================
//  Accesare Administrator
// ============================================================================

@Composable
fun LoginScreen(vm: MainViewModel, onBack: () -> Unit) {
    var accessCode by remember { mutableStateOf("") }
    var magicToken by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize()) {
        CenteredScroll {
            Column(Modifier.widthIn(max = 360.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Accesare Administrator",
                    color = VicColors.Foreground,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text("Introdu codul de acces primit.", color = VicColors.Muted, fontSize = 13.sp, textAlign = TextAlign.Center)

                Spacer(Modifier.height(24.dp))
                SectionLabel("COD DE ACCES", center = true)
                Spacer(Modifier.height(6.dp))
                MonoInput(
                    value = accessCode,
                    onValueChange = { accessCode = it },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !vm.loginBusy,
                    mono = true,
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Go,
                    onAction = { vm.loginWithPin(accessCode) },
                    visualTransformation = PasswordVisualTransformation('•'),
                    minHeight = 52.dp,
                )
                Spacer(Modifier.height(10.dp))
                MonoButtonPrimary(
                    if (vm.loginBusy) "SE VERIFICĂ…" else "CONTINUĂ",
                    onClick = { vm.loginWithPin(accessCode) },
                    enabled = !vm.loginBusy,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(20.dp))
                Text("─────────  sau  ─────────", color = VicColors.MutedDim, fontSize = 12.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(20.dp))

                SectionLabel("JETON MAGIC LINK", center = true)
                Spacer(Modifier.height(6.dp))
                MonoInput(
                    value = magicToken,
                    onValueChange = { magicToken = it },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !vm.loginBusy,
                    mono = true,
                    imeAction = ImeAction.Go,
                    onAction = { vm.loginWithMagic(magicToken) },
                    minHeight = 52.dp,
                )
                Spacer(Modifier.height(10.dp))
                MonoButton(
                    "FOLOSEȘTE JETONUL",
                    onClick = { vm.loginWithMagic(magicToken) },
                    enabled = !vm.loginBusy,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(16.dp))
                Text(
                    text = vm.loginStatus,
                    color = VicColors.Foreground,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
                )
            }
        }
        Box(Modifier.padding(12.dp)) {
            MonoButton("←  ÎNAPOI", onClick = onBack, height = 40.dp)
        }
    }
}

// ============================================================================
//  Administrare: publica fisier, rezultat (link + PIN), lista cu actiuni
// ============================================================================

@Composable
fun AdminScreen(vm: MainViewModel, onPickFile: () -> Unit) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // ---------- antet ----------
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MonoButton("←  PAGINA PRINCIPALĂ", onClick = { vm.backToPublic() }, height = 40.dp)
                Spacer(Modifier.weight(1f))
                Text("CONECTAT", color = VicColors.Muted, fontSize = 11.sp, letterSpacing = 1.sp)
            }
        }

        // ---------- publica un fisier nou ----------
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(VicColors.Surface)
                    .border(1.dp, VicColors.Border)
                    .padding(16.dp),
            ) {
                Text("Publică un fișier nou", color = VicColors.Foreground, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))

                SectionLabel("MATERIE / CATEGORIE")
                Spacer(Modifier.height(6.dp))
                MonoInput(
                    value = vm.materia,
                    onValueChange = { vm.onMateriaChange(it) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !vm.publishing,
                    imeAction = ImeAction.Next,
                )

                Spacer(Modifier.height(12.dp))
                SectionLabel("DESCRIERE (OPȚIONAL)")
                Spacer(Modifier.height(6.dp))
                MonoInput(
                    value = vm.description,
                    onValueChange = { vm.onDescriptionChange(it) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !vm.publishing,
                    singleLine = false,
                    minHeight = 84.dp,
                    imeAction = ImeAction.Default,
                )

                Spacer(Modifier.height(12.dp))
                SectionLabel("FIȘIER")
                Spacer(Modifier.height(6.dp))
                val picked = vm.pickedFile
                MonoInput(
                    value = if (picked == null) "" else picked.name + if (picked.size > 0) "  ·  " + formatSize(picked.size) else "",
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = true,
                    placeholder = "Niciun fișier ales",
                )
                Spacer(Modifier.height(8.dp))
                MonoButton("ALEGE FIȘIER…", onClick = onPickFile, enabled = !vm.publishing, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                MonoButtonPrimary(
                    if (vm.publishing) "SE PUBLICĂ…" else "PUBLICĂ",
                    onClick = { vm.publish() },
                    enabled = !vm.publishing,
                    modifier = Modifier.fillMaxWidth(),
                    height = 50.dp,
                )
                if (vm.publishing) {
                    Spacer(Modifier.height(12.dp))
                    MonoProgress(vm.publishProgress)
                }
            }
        }

        // ---------- rezultat: link + PIN ----------
        val res = vm.result
        if (res != null) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(VicColors.Surface)
                        .border(1.dp, VicColors.Foreground)
                        .padding(16.dp),
                ) {
                    SectionLabel("LINK DIRECT")
                    Spacer(Modifier.height(6.dp))
                    MonoInput(value = res.url, onValueChange = {}, readOnly = true, modifier = Modifier.fillMaxWidth(), mono = false)
                    Spacer(Modifier.height(12.dp))
                    SectionLabel("PIN — SE ARATĂ O SINGURĂ DATĂ")
                    Spacer(Modifier.height(6.dp))
                    MonoInput(value = res.pin, onValueChange = {}, readOnly = true, modifier = Modifier.fillMaxWidth(), big = true, minHeight = 56.dp)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MonoButton(
                            "COPIAZĂ",
                            onClick = {
                                if (copyText(context, "Link VicProj", res.url)) vm.showFlash("Link copiat.")
                            },
                            modifier = Modifier.weight(1f),
                            height = 44.dp,
                        )
                        MonoButton(
                            "DISTRIBUIE",
                            onClick = {
                                shareText(context, ShareTextBuilder.build(res.filename, res.pin, res.code, res.url))
                            },
                            modifier = Modifier.weight(1f),
                            height = 44.dp,
                        )
                        MonoButton(
                            "QR",
                            onClick = { vm.qrRequest = QrRequest(res.code, res.filename, res.pin) },
                            modifier = Modifier.weight(1f),
                            height = 44.dp,
                        )
                    }
                }
            }
        }

        // ---------- bara de cautare / filtre ----------
        item {
            Column {
                SectionLabel("CAUTĂ")
                Spacer(Modifier.height(6.dp))
                MonoInput(
                    value = vm.search,
                    onValueChange = { vm.onSearchChange(it) },
                    modifier = Modifier.fillMaxWidth(),
                    imeAction = ImeAction.Search,
                    onAction = { vm.loadFiles() },
                    placeholder = "Nume fișier, materie…",
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip("Toate", vm.statusFilter == null) { vm.onStatusFilterChange(null) }
                    FilterChip("Active", vm.statusFilter == "active") { vm.onStatusFilterChange("active") }
                    FilterChip("Pauzate", vm.statusFilter == "paused") { vm.onStatusFilterChange("paused") }
                    FilterChip("Blocate", vm.statusFilter == "blocked") { vm.onStatusFilterChange("blocked") }
                }
                Spacer(Modifier.height(10.dp))
                MonoButton(
                    if (vm.loadingFiles) "SE ÎNCARCĂ…" else "REÎMPROSPĂTEAZĂ",
                    onClick = { vm.loadFiles() },
                    enabled = !vm.loadingFiles,
                    modifier = Modifier.fillMaxWidth(),
                    height = 44.dp,
                )
            }
        }

        // ---------- lista ----------
        if (vm.files.isEmpty()) {
            item {
                Text(
                    if (vm.loadingFiles) "Se încarcă…" else "Niciun fișier.",
                    color = VicColors.Muted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                )
            }
        } else {
            items(vm.files, key = { it.id }) { f ->
                FileRow(f) { vm.actionFile = f }
            }
        }

        item {
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                Text(
                    "DECONECTARE",
                    color = VicColors.MutedDim,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { vm.logout() }
                        .padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(if (selected) VicColors.Foreground else VicColors.Background)
            .border(1.dp, if (selected) VicColors.Foreground else VicColors.MutedDim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(
            label,
            color = if (selected) VicColors.Background else VicColors.Foreground,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun FileRow(f: FileOut, onClick: () -> Unit) {
    val active = f.status == "active"
    val blocked = f.status == "blocked"
    Column(
        Modifier
            .fillMaxWidth()
            .background(VicColors.Surface)
            .border(1.dp, VicColors.Border)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                f.filename,
                color = if (blocked) VicColors.MutedDim else VicColors.Foreground,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (blocked) TextDecoration.LineThrough else TextDecoration.None,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.size(10.dp))
            Box(
                modifier = Modifier
                    .background(if (active) VicColors.Foreground else VicColors.Background)
                    .border(1.dp, if (active) VicColors.Foreground else VicColors.MutedDim)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    f.statusRomana.uppercase(),
                    color = if (active) VicColors.Background else VicColors.Muted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        val sub = listOf(f.materia, f.createdAtLocal).filter { it.isNotBlank() }.joinToString("  ·  ")
        Text(sub, color = VicColors.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Text(f.sharedCode, color = VicColors.Foreground, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
    }
}

/** Fereastra cu actiunile unui fisier (echivalentul meniului clic-dreapta din Windows). */
@Composable
fun FileActionsDialog(
    f: FileOut,
    onCopyLink: () -> Unit,
    onQr: () -> Unit,
    onRegenerate: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onBlock: () -> Unit,
    onClose: () -> Unit,
) {
    VicDialog(onDismiss = onClose) {
        Text(f.filename, color = VicColors.Foreground, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Text("${f.statusRomana}  ·  ${f.sharedCode}", color = VicColors.Muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(16.dp))
        MonoButton("COPIAZĂ LINKUL", onClick = onCopyLink, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        MonoButton("COD QR", onClick = onQr, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        MonoButton("GENEREAZĂ PIN NOU", onClick = onRegenerate, modifier = Modifier.fillMaxWidth())
        if (f.status == "active") {
            Spacer(Modifier.height(8.dp))
            MonoButton("PUNE PE PAUZĂ", onClick = onPause, modifier = Modifier.fillMaxWidth())
        }
        if (f.status == "paused") {
            Spacer(Modifier.height(8.dp))
            MonoButton("REACTIVEAZĂ", onClick = onResume, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(8.dp))
        MonoButton("BLOCHEAZĂ DEFINITIV", onClick = onBlock, enabled = f.status != "blocked", modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))
        MonoButtonPrimary("ÎNCHIDE", onClick = onClose, modifier = Modifier.fillMaxWidth())
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(java.util.Locale.US, "%.0f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(java.util.Locale.US, "%.1f MB", mb)
    return String.format(java.util.Locale.US, "%.2f GB", mb / 1024.0)
}

private fun shareText(context: android.content.Context, text: String) {
    try {
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, text)
        }
        val chooser = android.content.Intent.createChooser(send, "Distribuie")
        chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    } catch (e: Exception) {
        // nu avem ce face daca sistemul nu poate deschide meniul de distribuire
    }
}

// ============================================================================
//  Actualizari (UpdatePopup / ForcedUpdateForm din Windows)
// ============================================================================

@Composable
fun UpdateDialog(manifest: UpdateManifest, onDownload: () -> Unit, onLater: () -> Unit) {
    VicDialog(onDismiss = onLater) {
        Text("ACTUALIZARE DISPONIBILĂ", color = VicColors.Foreground, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(10.dp))
        val text = if (manifest.message.isNotBlank()) {
            "Versiunea ${manifest.latestVersion} — ${manifest.message}"
        } else {
            "Versiunea ${manifest.latestVersion} e gata de descărcat."
        }
        Text(text, color = VicColors.Muted, fontSize = 14.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MonoButton("MAI TÂRZIU", onClick = onLater, modifier = Modifier.weight(1f))
            MonoButtonPrimary("DESCARCĂ", onClick = onDownload, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun ForcedUpdateScreen(manifest: UpdateManifest, onUpdate: () -> Unit) {
    CenteredScroll {
        LogoHeader(onLogoTap = {}, logoSize = 64, subtitle = null)
        Spacer(Modifier.height(28.dp))
        Text(
            "E nevoie de o actualizare",
            color = VicColors.Foreground,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            if (manifest.message.isNotBlank()) manifest.message
            else "Versiunea instalată a VicProj nu mai este acceptată. Actualizează aplicația ca să poți continua.",
            color = VicColors.Muted,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 380.dp),
        )
        Spacer(Modifier.height(24.dp))
        MonoButtonPrimary("ACTUALIZEAZĂ ACUM", onClick = onUpdate, modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth(), height = 52.dp)
    }
}

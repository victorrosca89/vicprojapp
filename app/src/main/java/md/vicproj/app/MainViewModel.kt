package md.vicproj.app

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar

const val API_BASE_URL = "https://victorrosca89-room89.hf.space"
const val SHARE_URL_PREFIX = "https://vicproj.netlify.app/cod/"

private const val DEFAULT_INFO = "Introdu codul de 9 cifre primit și apasă „Verifică codul”."

enum class Screen { Public, Login, Admin }

class PickedFile(val uri: Uri, val name: String, val size: Long)

class ResultInfo(val filename: String, val pin: String, val code: String, val url: String)

class QrRequest(val code: String, val filename: String, val pin: String?)

/** Oglindeste ShareTextBuilder.cs / buildShareText() din js/app.js. */
object ShareTextBuilder {
    fun timeGreeting(): String {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            h in 5..11 -> "Bună dimineața"
            h in 12..17 -> "Bună ziua"
            else -> "Bună seara"
        }
    }

    fun build(filename: String, pin: String, code: String, url: String): String =
        "${timeGreeting()}! Doresc să vă trimit fișierul meu ($filename) prin intermediul " +
            "aplicației web VicProj creată de mine, cu PIN-ul de securitate ($pin) și cu " +
            "ShareID-ul ($code). Vă mulțumesc!\n$url"
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx: Context = app.applicationContext
    val api = ApiClient(API_BASE_URL, ctx.contentResolver)
    private val store = SecureStore(ctx)
    private val checker = ConnectivityChecker(ctx, API_BASE_URL)

    // ===================== stare globala =====================

    var screen by mutableStateOf(Screen.Public)
        private set
    var connState by mutableStateOf(ConnectivityState.Unknown)
        private set
    var checkingConnection by mutableStateOf(false)
        private set
    var connStatusText by mutableStateOf("")
        private set
    var flash by mutableStateOf("")
        private set

    var error by mutableStateOf<ErrorInfo?>(null)
    var message by mutableStateOf<MessageInfo?>(null)
    var update by mutableStateOf<UpdateCheckResult?>(null)
        private set

    var showScanner by mutableStateOf(false)
        private set
    var showCameraConsent by mutableStateOf(false)
    var showCameraBlocked by mutableStateOf(false)
    var qrRequest by mutableStateOf<QrRequest?>(null)

    val hasCameraConsent: Boolean get() = store.cameraConsent

    init {
        Sounds.init(ctx)
        api.token = store.loadToken()
        checkUpdate()
    }

    // ===================== mesaje scurte (bara de jos) =====================

    private var flashJob: Job? = null

    fun showFlash(text: String) {
        flash = text
        flashJob?.cancel()
        flashJob = viewModelScope.launch {
            delay(3500)
            flash = ""
        }
    }

    private fun friendlyError(e: ApiException): String = when {
        e.statusCode == 0 -> AppError.format(AppError.NETWORK)
        e.statusCode >= 500 -> AppError.format(AppError.SERVER, "HTTP ${e.statusCode}")
        // 4xx: mesaj de validare normal, trimis de backend (cod gresit, PIN gresit etc.)
        else -> e.message ?: "Eroare."
    }

    // ===================== conectivitate =====================

    private var monitorJob: Job? = null
    private var netCallback: ConnectivityManager.NetworkCallback? = null

    fun startMonitoring() {
        if (monitorJob?.isActive == true) return
        monitorJob = viewModelScope.launch {
            while (isActive) {
                runCheck()
                delay(5000)
            }
        }
        registerNetworkCallback()
    }

    fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
        unregisterNetworkCallback()
    }

    private suspend fun runCheck() {
        connState = checker.check()
    }

    private fun registerNetworkCallback() {
        if (netCallback != null) return
        try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    recheckSoon()
                }

                override fun onLost(network: Network) {
                    recheckSoon()
                }
            }
            cm.registerDefaultNetworkCallback(cb)
            netCallback = cb
        } catch (e: Exception) {
            // verificarea periodica (5s) ramane oricum activa
        }
    }

    private fun unregisterNetworkCallback() {
        val cb = netCallback ?: return
        netCallback = null
        try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.unregisterNetworkCallback(cb)
        } catch (e: Exception) {
            // ignoram
        }
    }

    private fun recheckSoon() {
        viewModelScope.launch {
            delay(700) // sistemului ii trebuie o clipa sa stabilizeze reteaua
            runCheck()
        }
    }

    fun retryConnection() {
        if (checkingConnection) return
        viewModelScope.launch {
            checkingConnection = true
            connStatusText = ""
            try {
                runCheck()
            } finally {
                checkingConnection = false
            }
            if (connState != ConnectivityState.Online) {
                connStatusText = if (connState == ConnectivityState.Offline) {
                    "Încă nu există conexiune la internet."
                } else {
                    "Serverul încă nu răspunde. Mai încearcă în câteva secunde."
                }
                delay(4000)
                connStatusText = ""
            }
        }
    }

    override fun onCleared() {
        stopMonitoring()
        super.onCleared()
    }

    // ===================== actualizari =====================

    private fun checkUpdate() {
        viewModelScope.launch {
            val r = UpdateChecker.check()
            if (r.urgency != UpdateUrgency.None) update = r
        }
    }

    fun dismissUpdate() {
        if (update?.urgency == UpdateUrgency.Optional) update = null
    }

    // ===================== pagina publica: cod + PIN =====================

    var code by mutableStateOf("")
        private set
    var pin by mutableStateOf("")
        private set
    var info by mutableStateOf(DEFAULT_INFO)
        private set
    var infoStrong by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var verified by mutableStateOf<SharePublic?>(null)
        private set
    var downloadProgress by mutableStateOf<Int?>(null)
        private set
    var downloadedUri by mutableStateOf<Uri?>(null)
        private set

    val canDownload: Boolean get() = !busy && verified?.downloadAvailable == true

    private fun setInfo(text: String, strong: Boolean) {
        info = text
        infoStrong = strong
    }

    fun onCodeChange(value: String) {
        val digits = value.filter { it.isDigit() }.take(9)
        if (digits != code) {
            code = digits
            if (verified != null) {
                verified = null
                downloadedUri = null
                pin = ""
                setInfo(DEFAULT_INFO, false)
            }
        }
    }

    fun onPinChange(value: String) {
        pin = value.filter { it.isDigit() }.take(6)
    }

    fun checkCode() {
        if (busy) return
        val c = code.trim()
        if (c.length < 4) {
            setInfo("Introdu codul primit (de obicei 9 cifre).", false)
            return
        }
        viewModelScope.launch {
            busy = true
            verified = null
            downloadedUri = null
            try {
                val share = api.getShareByCode(c)
                verified = share
                if (!share.downloadAvailable) {
                    setInfo("„${share.filename}” — indisponibil momentan.", false)
                } else {
                    var text = "Fișier găsit: ${share.filename}"
                    if (share.materia.isNotBlank()) text += "  ·  ${share.materia}"
                    setInfo(text, true)
                }
            } catch (e: ApiException) {
                setInfo("⚠  " + friendlyError(e), true)
                Sounds.error()
            } finally {
                busy = false
            }
        }
    }

    /** true = se poate alege locul de salvare. */
    fun validateBeforeDownload(): Boolean {
        if (verified == null || busy) return false
        if (pin.trim().length < 4) {
            setInfo("Introdu PIN-ul de 6 cifre.", false)
            return false
        }
        return true
    }

    fun startDownload(dest: Uri) {
        val share = verified ?: return
        val c = code.trim()
        val p = pin.trim()
        viewModelScope.launch {
            busy = true
            downloadProgress = 0
            downloadedUri = null
            try {
                api.verifyPin(c, p)
                api.downloadFile(c, p, dest) { pct -> downloadProgress = pct }
                setInfo("Descărcat cu succes: ${share.filename}", true)
                showFlash("Descărcare finalizată.")
                Sounds.success()
                downloadedUri = dest
            } catch (e: ApiException) {
                val msg = friendlyError(e)
                setInfo("⚠  $msg", true)
                Sounds.error()
                when {
                    e.statusCode == 0 -> error = ErrorInfo(AppError.NETWORK)
                    e.statusCode >= 500 -> error = ErrorInfo(AppError.SERVER, "HTTP ${e.statusCode}")
                    else -> message = MessageInfo("VicProj", msg)
                }
            } catch (e: LocalFileException) {
                Sounds.error()
                error = ErrorInfo(AppError.FILE_SYSTEM, e.message)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Sounds.error()
                error = ErrorInfo(AppError.UNEXPECTED, e.message)
            } finally {
                busy = false
                downloadProgress = null
            }
        }
    }

    // ===================== scanner QR =====================

    fun openScanner() {
        showScanner = true
    }

    fun closeScanner() {
        showScanner = false
    }

    fun onScanned(scanned: String) {
        showScanner = false
        applyIncomingCode(scanned)
    }

    fun scannerFailed(detail: String?) {
        showScanner = false
        Sounds.error()
        error = ErrorInfo(AppError.CAMERA_MODULE, detail)
    }

    fun grantCameraConsent() {
        store.cameraConsent = true
        showCameraConsent = false
    }

    /** Link deschis din exterior (https://vicproj.netlify.app/cod/123456789 sau vicproj://share/123456789). */
    fun handleDeepLink(raw: String?) {
        val m = Regex("(\\d{9})").find(raw ?: "") ?: return
        if (screen != Screen.Public) screen = Screen.Public
        applyIncomingCode(m.groupValues[1])
    }

    private fun applyIncomingCode(scanned: String) {
        val m = Regex("(\\d{9})").find(scanned) ?: return
        code = m.groupValues[1]
        pin = ""
        verified = null
        downloadedUri = null
        checkCode()
    }

    // ===================== acces ascuns de administrator =====================

    private var logoTaps = 0
    private var lastLogoTap = 0L

    /** 5 atingeri pe logo, la cel mult 1,2 s una de alta (ca cele 5 click-uri din Windows). */
    fun onLogoTap() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastLogoTap > 1200) logoTaps = 0
        lastLogoTap = now
        logoTaps++
        if (logoTaps >= 5) {
            logoTaps = 0
            if (!api.token.isNullOrEmpty()) {
                screen = Screen.Admin
                loadFiles()
            } else {
                screen = Screen.Login
            }
        }
    }

    fun backToPublic() {
        screen = Screen.Public
    }

    fun logout() {
        api.token = null
        store.clearToken()
        files = emptyList()
        result = null
        screen = Screen.Public
        showFlash("Deconectat.")
    }

    // ===================== Accesare Administrator =====================

    var loginBusy by mutableStateOf(false)
        private set
    var loginStatus by mutableStateOf("")
        private set

    fun loginWithPin(text: String) = doLogin(text) { api.loginPin(it) }

    fun loginWithMagic(text: String) = doLogin(text) { api.magicVerify(it) }

    private fun showLoginStatus(text: String) {
        if (text.isEmpty()) {
            loginStatus = ""
            return
        }
        loginStatus = "⚠  $text"
        Sounds.error()
    }

    private fun doLogin(text: String, action: suspend (String) -> TokenResponse) {
        if (loginBusy) return
        if (text.isBlank()) {
            showLoginStatus("Completează câmpul de mai sus.")
            return
        }
        viewModelScope.launch {
            loginBusy = true
            showLoginStatus("")
            try {
                val tok = action(text.trim())
                api.token = tok.accessToken
                store.saveToken(tok.accessToken)
                screen = Screen.Admin
                refreshFiles()
            } catch (e: ApiException) {
                showLoginStatus(
                    if (e.statusCode == 0) "Nu ne putem conecta la server. Verifică internetul."
                    else (e.message ?: "Eroare."),
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                showLoginStatus("Eroare neașteptată: ${e.message}")
            } finally {
                loginBusy = false
            }
        }
    }

    fun clearLoginStatus() {
        loginStatus = ""
    }

    // ===================== administrare =====================

    var files by mutableStateOf<List<FileOut>>(emptyList())
        private set
    var loadingFiles by mutableStateOf(false)
        private set
    var search by mutableStateOf("")
        private set
    var statusFilter by mutableStateOf<String?>(null)
        private set

    var materia by mutableStateOf("")
        private set
    var description by mutableStateOf("")
        private set
    var pickedFile by mutableStateOf<PickedFile?>(null)
        private set
    var publishing by mutableStateOf(false)
        private set
    var publishProgress by mutableStateOf<Int?>(null)
        private set
    var result by mutableStateOf<ResultInfo?>(null)
        private set

    var actionFile by mutableStateOf<FileOut?>(null)
    var confirmBlock by mutableStateOf<FileOut?>(null)

    fun onSearchChange(v: String) {
        search = v
    }

    fun onMateriaChange(v: String) {
        materia = v
    }

    fun onDescriptionChange(v: String) {
        description = v
    }

    fun linkOf(f: FileOut): String = SHARE_URL_PREFIX + f.sharedCode

    fun onStatusFilterChange(value: String?) {
        statusFilter = value
        loadFiles()
    }

    fun loadFiles() {
        viewModelScope.launch { refreshFiles() }
    }

    private suspend fun refreshFiles() {
        if (screen != Screen.Admin) return
        loadingFiles = true
        try {
            val items = api.listFiles(search.trim(), statusFilter)
            files = items.sortedByDescending { it.createdAtMillis ?: Long.MIN_VALUE }
        } catch (e: ApiException) {
            handleAdminError(e)
        } finally {
            loadingFiles = false
        }
    }

    private fun handleAdminError(e: ApiException) {
        Sounds.error()
        when {
            e.statusCode == 401 -> {
                store.clearToken()
                api.token = null
                screen = Screen.Public
                message = MessageInfo("VicProj", "Sesiunea a expirat. Accesează din nou.")
            }
            e.statusCode == 0 -> error = ErrorInfo(AppError.NETWORK)
            e.statusCode >= 500 -> error = ErrorInfo(AppError.SERVER, "HTTP ${e.statusCode}")
            else -> message = MessageInfo("VicProj", friendlyError(e))
        }
    }

    fun pickFile(uri: Uri?) {
        if (uri == null) return
        var name: String? = null
        var size = -1L
        try {
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val si = c.getColumnIndex(OpenableColumns.SIZE)
                if (c.moveToFirst()) {
                    if (ni >= 0 && !c.isNull(ni)) name = c.getString(ni)
                    if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                }
            }
        } catch (e: Exception) {
            // numele ramane implicit
        }
        pickedFile = PickedFile(uri, name ?: (uri.lastPathSegment ?: "fisier"), size)
    }

    fun publish() {
        val f = pickedFile
        if (f == null) {
            Sounds.error()
            message = MessageInfo("VicProj", "Alege întâi un fișier.")
            return
        }
        if (materia.isBlank()) {
            Sounds.error()
            message = MessageInfo("VicProj", "Completează materia / categoria.")
            return
        }
        if (publishing) return

        viewModelScope.launch {
            publishing = true
            publishProgress = 0
            try {
                val res = api.uploadFile(materia.trim(), description.trim(), f.uri, f.name, f.size) { publishProgress = it }
                val url = SHARE_URL_PREFIX + res.file.sharedCode
                result = ResultInfo(res.file.filename, res.downloadPin, res.file.sharedCode, url)

                materia = ""
                description = ""
                pickedFile = null

                showFlash("Fișier publicat.")
                Sounds.success()
                // codul QR apare imediat dupa publicare (cu PIN-ul, afisat o singura data)
                qrRequest = QrRequest(res.file.sharedCode, res.file.filename, res.downloadPin)
                refreshFiles()
            } catch (e: ApiException) {
                handleAdminError(e)
            } catch (e: LocalFileException) {
                Sounds.error()
                error = ErrorInfo(AppError.FILE_SYSTEM, e.message)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Sounds.error()
                error = ErrorInfo(AppError.UNEXPECTED, e.message)
            } finally {
                publishing = false
                publishProgress = null
            }
        }
    }

    fun regeneratePin(f: FileOut) {
        viewModelScope.launch {
            try {
                val r = api.regeneratePin(f.id)
                result = ResultInfo(f.filename, r.downloadPin, f.sharedCode, linkOf(f))
                showFlash("PIN nou generat.")
                qrRequest = QrRequest(f.sharedCode, f.filename, r.downloadPin)
            } catch (e: ApiException) {
                handleAdminError(e)
            }
        }
    }

    fun changeStatus(f: FileOut, newStatus: String) {
        viewModelScope.launch {
            try {
                api.updateStatus(f.id, newStatus)
                refreshFiles()
            } catch (e: ApiException) {
                handleAdminError(e)
            }
        }
    }
}

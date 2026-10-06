package md.vicproj.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

enum class ConnectivityState {
    Unknown,
    Online,            // internet + server VicProj raspund
    ServerUnreachable, // internet OK, dar serverul VicProj nu raspunde (Hugging Face oprit/adormit)
    Offline,           // nu exista conexiune reala la internet
}

/**
 * Aceeasi logica in 3 pasi ca ConnectivityMonitor.cs din aplicatia Windows:
 *  1. exista o retea cu internet?            nu  -> Offline
 *  2. raspunde serverul VicProj?             da  -> Online
 *  3. exista internet real (probe-uri)?      da  -> ServerUnreachable, nu -> Offline
 * Serverul "nu raspunde" si la 502/503/504 (proxy-ul traieste, aplicatia din spate e oprita).
 */
class ConnectivityChecker(context: Context, baseUrl: String) {

    private val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    // cod inexistent, cu format valid - cerem doar un raspuns HTTP, nu conteaza continutul
    private val probeUrl = baseUrl.trimEnd('/') + "/api/v1/share/000000000"

    private val serverHttp = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .followRedirects(false)
        .retryOnConnectionFailure(false)
        .build()

    private val probeHttp = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .followRedirects(false)
        .retryOnConnectionFailure(false)
        .build()

    private class Probe(val url: String, val ok: (Int, String) -> Boolean)

    // HTTPS (Android blocheaza HTTP simplu). Alese pentru ca sunt minuscule si foarte disponibile.
    private val probes = listOf(
        Probe("https://connectivitycheck.gstatic.com/generate_204") { code, _ -> code == 204 },
        Probe("https://cp.cloudflare.com/generate_204") { code, _ -> code == 204 },
        Probe("https://detectportal.firefox.com/success.txt") { _, body -> body.trim().equals("success", ignoreCase = true) },
        // Cloudflare pe IP direct - merge si cand DNS-ul e stricat
        Probe("https://1.1.1.1/cdn-cgi/trace") { _, body -> body.contains("colo=", ignoreCase = true) },
    )

    suspend fun check(): ConnectivityState = withContext(Dispatchers.IO) {
        if (!hasNetworkWithInternet()) {
            ConnectivityState.Offline
        } else if (serverResponds()) {
            ConnectivityState.Online
        } else if (hasRealInternet()) {
            ConnectivityState.ServerUnreachable
        } else {
            ConnectivityState.Offline
        }
    }

    private fun hasNetworkWithInternet(): Boolean {
        return try {
            val network = cm.activeNetwork
            val caps = if (network != null) cm.getNetworkCapabilities(network) else null
            caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            true // daca nu putem intreba sistemul, lasam probele HTTP sa decida
        }
    }

    private fun serverResponds(): Boolean {
        return try {
            val request = Request.Builder().url(probeUrl).get().build()
            serverHttp.newCall(request).execute().use { res ->
                val code = res.code
                // 502/503/504 = reverse-proxy-ul raspunde, dar aplicatia din spate e oprita sau adormita
                !(code == 502 || code == 503 || code == 504)
            }
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun hasRealInternet(): Boolean = coroutineScope {
        probes.map { p -> async { runProbe(p) } }.awaitAll().any { it }
    }

    private fun runProbe(p: Probe): Boolean {
        return try {
            val request = Request.Builder()
                .url(p.url)
                .header("Cache-Control", "no-cache")
                .header("User-Agent", "VicProj/1.0 (connectivity check)")
                .get()
                .build()
            probeHttp.newCall(request).execute().use { res ->
                val body = if (res.code == 204) "" else (res.body?.string() ?: "")
                p.ok(res.code, body.take(2000))
            }
        } catch (e: Exception) {
            false
        }
    }
}

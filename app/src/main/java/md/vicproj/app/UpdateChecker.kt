package md.vicproj.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class UpdateManifest(
    val latestVersion: String,
    val minRequired: String,
    val downloadUrl: String,
    val force: Boolean,
    val message: String,
)

enum class UpdateUrgency { None, Optional, Mandatory }

data class UpdateCheckResult(val urgency: UpdateUrgency, val manifest: UpdateManifest?)

/**
 * Citeste https://vicproj.netlify.app/version.json (acelasi fisier ca la Windows).
 * Campurile pentru Android (separate, ca sa nu se amestece cu versiunea de Windows):
 *   "latest_version_android": "1.4.0"
 *   "min_required_version_android": "1.3.0"
 *   "force_update_android": false
 *   "download_url_android": "https://vicproj.netlify.app/vicproj.apk"   (optional)
 *   "message_android": "Ce e nou..."                                   (optional)
 * Daca fisierul nu are aceste campuri sau nu se poate citi, aplicatia continua normal.
 */
object UpdateChecker {
    private const val MANIFEST_URL = "https://vicproj.netlify.app/version.json"
    private const val DEFAULT_APK_URL = "https://vicproj.netlify.app/vicproj.apk"

    private fun none() = UpdateCheckResult(UpdateUrgency.None, null)

    suspend fun check(): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder().callTimeout(8, TimeUnit.SECONDS).build()
            val request = Request.Builder().url(MANIFEST_URL).header("Cache-Control", "no-cache").get().build()
            val (ok, text) = client.newCall(request).execute().use { res ->
                Pair(res.isSuccessful, res.body?.string() ?: "")
            }
            if (!ok) return@withContext none()

            val o = JSONObject(text)
            val latest = o.str("latest_version_android")
            val min = o.str("min_required_version_android")
            val force = o.optBoolean("force_update_android", false)
            if (latest.isBlank() && min.isBlank() && !force) return@withContext none()

            val manifest = UpdateManifest(
                latestVersion = latest,
                minRequired = min,
                downloadUrl = o.str("download_url_android").ifBlank { DEFAULT_APK_URL },
                force = force,
                message = o.str("message_android").ifBlank { o.str("message") },
            )

            val current = BuildConfig.VERSION_NAME

            var mandatory = force
            if (!mandatory && min.isNotBlank() && compareVersions(current, min) < 0) mandatory = true
            if (mandatory) return@withContext UpdateCheckResult(UpdateUrgency.Mandatory, manifest)

            if (latest.isNotBlank() && compareVersions(latest, current) > 0) {
                return@withContext UpdateCheckResult(UpdateUrgency.Optional, manifest)
            }
            none()
        } catch (e: Exception) {
            none() // fara internet, JSON invalid, fisierul lipseste etc. - continuam normal
        }
    }

    private fun parts(v: String): List<Int> {
        val list = v.split('.').map { part -> part.filter { it.isDigit() }.toIntOrNull() ?: 0 }.toMutableList()
        while (list.size < 4) list.add(0)
        return list
    }

    /** < 0 daca a < b, 0 daca egale, > 0 daca a > b */
    private fun compareVersions(a: String, b: String): Int {
        val pa = parts(a)
        val pb = parts(b)
        for (i in 0 until 4) {
            if (pa[i] != pb[i]) return pa[i].compareTo(pb[i])
        }
        return 0
    }
}

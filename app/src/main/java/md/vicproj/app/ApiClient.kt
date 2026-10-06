package md.vicproj.app

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit

/**
 * Oglindeste ApiClient.cs din aplicatia Windows (aceleasi endpoint-uri,
 * aceleasi mesaje de eroare). statusCode == 0 => serverul nu a putut fi atins.
 */
class ApiClient(baseUrl: String, private val resolver: ContentResolver) {

    private val base = baseUrl.trimEnd('/')

    @Volatile
    var token: String? = null

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private fun req(path: String, auth: Boolean): Request.Builder {
        val builder = Request.Builder().url(base + path)
        val t = token
        if (auth && !t.isNullOrEmpty()) builder.header("Authorization", "Bearer $t")
        return builder
    }

    private fun jsonBody(vararg pairs: Pair<String, String>): RequestBody {
        val o = JSONObject()
        for ((k, v) in pairs) o.put(k, v)
        return o.toString().toRequestBody(jsonType)
    }

    private fun networkError() =
        ApiException("Nu ne putem conecta la server. Verifică conexiunea la internet.", 0)

    private fun timeoutError() =
        ApiException("Serverul nu a răspuns la timp (timeout).", 0)

    private suspend fun send(request: Request): String = withContext(Dispatchers.IO) {
        try {
            http.newCall(request).execute().use { res ->
                val body = res.body?.string() ?: ""
                if (!res.isSuccessful) throw buildError(res.code, body)
                body
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: InterruptedIOException) {
            throw timeoutError()
        } catch (e: IOException) {
            throw networkError()
        }
    }

    private fun buildError(code: Int, body: String): ApiException {
        var message = "Eroare server ($code)."
        try {
            val obj = JSONObject(body)
            val detail = obj.opt("detail")
            if (detail is String) {
                if (detail.isNotBlank()) message = detail
            } else if (detail is JSONArray) {
                val first = detail.optJSONObject(0)
                val msg = first?.optString("msg", "") ?: ""
                if (msg.isNotBlank()) message = msg
            }
        } catch (e: Exception) {
            // corpul nu era JSON valid - pastram mesajul generic
        }
        return ApiException(message, code)
    }

    private fun unexpected(e: Exception, code: Int = 200) =
        ApiException("Răspuns neașteptat de la server: ${e.message}", code)

    // ---------------- Auth ----------------

    suspend fun loginPin(pin: String): TokenResponse {
        val s = send(req("/api/v1/auth/login-pin", false).post(jsonBody("pin" to pin)).build())
        return parseToken(s)
    }

    suspend fun magicVerify(token: String): TokenResponse {
        val s = send(req("/api/v1/auth/magic-verify", false).post(jsonBody("token" to token)).build())
        return parseToken(s)
    }

    private fun parseToken(s: String): TokenResponse = try {
        TokenResponse(JSONObject(s).getString("access_token"))
    } catch (e: Exception) {
        throw unexpected(e)
    }

    // ---------------- Fisiere (Admin) ----------------

    suspend fun listFiles(search: String?, statusFilter: String?): List<FileOut> {
        val qp = mutableListOf<String>()
        if (!search.isNullOrBlank()) qp.add("search=" + Uri.encode(search))
        if (!statusFilter.isNullOrBlank() && statusFilter != "all") qp.add("status_filter=" + Uri.encode(statusFilter))
        val qs = if (qp.isEmpty()) "" else "?" + qp.joinToString("&")
        val s = send(req("/api/v1/files$qs", true).get().build())
        return try {
            val arr = JSONArray(s)
            (0 until arr.length()).map { FileOut.from(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            throw unexpected(e)
        }
    }

    suspend fun uploadFile(
        materia: String,
        description: String,
        uri: Uri,
        fileName: String,
        size: Long,
        onProgress: (Int) -> Unit,
    ): FileUploadOut {
        val fileBody = object : RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun contentLength(): Long = size
            override fun writeTo(sink: BufferedSink) {
                val input = resolver.openInputStream(uri)
                    ?: throw LocalFileException("Fișierul ales nu poate fi citit.")
                input.use { ins ->
                    val buf = ByteArray(65536)
                    var total = 0L
                    var last = -1
                    while (true) {
                        val n = try {
                            ins.read(buf)
                        } catch (e: IOException) {
                            throw LocalFileException(e.message ?: "Citirea fișierului a eșuat.")
                        }
                        if (n < 0) break
                        sink.write(buf, 0, n)
                        total += n
                        if (size > 0) {
                            val p = (total * 100 / size).toInt().coerceAtMost(100)
                            if (p != last) {
                                last = p
                                onProgress(p)
                            }
                        }
                    }
                }
            }
        }

        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("materia", materia)
            .addFormDataPart("description", description)
            .addFormDataPart("file", fileName, fileBody)
            .build()

        val s = send(req("/api/v1/files/upload", true).post(multipart).build())
        return try {
            val o = JSONObject(s)
            FileUploadOut(FileOut.from(o), o.str("download_pin"))
        } catch (e: Exception) {
            throw unexpected(e)
        }
    }

    suspend fun updateStatus(fileId: String, newStatus: String): FileOut {
        val s = send(req("/api/v1/files/$fileId/status", true).patch(jsonBody("status" to newStatus)).build())
        return try {
            FileOut.from(JSONObject(s))
        } catch (e: Exception) {
            throw unexpected(e)
        }
    }

    suspend fun regeneratePin(fileId: String): RegeneratePinOut {
        val s = send(req("/api/v1/files/$fileId/regenerate-pin", true).patch("".toRequestBody(jsonType)).build())
        return try {
            val o = JSONObject(s)
            RegeneratePinOut(o.str("id"), o.str("download_pin"))
        } catch (e: Exception) {
            throw unexpected(e)
        }
    }

    // ---------------- Partajare (public) ----------------

    suspend fun getShareByCode(code: String): SharePublic {
        val s = send(req("/api/v1/share/" + Uri.encode(code), false).get().build())
        return try {
            SharePublic.from(JSONObject(s))
        } catch (e: Exception) {
            throw unexpected(e)
        }
    }

    suspend fun verifyPin(code: String, pin: String): SharePublic {
        val s = send(req("/api/v1/share/verify-pin", false).post(jsonBody("code" to code, "pin" to pin)).build())
        return try {
            SharePublic.from(JSONObject(s))
        } catch (e: Exception) {
            throw unexpected(e)
        }
    }

    /** Descarca direct in Uri-ul ales de utilizator (Storage Access Framework), raportand progresul 0-100. */
    suspend fun downloadFile(code: String, pin: String, dest: Uri, onProgress: (Int) -> Unit) {
        val url = "$base/api/v1/share/${Uri.encode(code)}/download?pin=${Uri.encode(pin)}"
        val request = Request.Builder().url(url).get().build()

        withContext(Dispatchers.IO) {
            try {
                http.newCall(request).execute().use { res ->
                    if (!res.isSuccessful) {
                        throw buildError(res.code, res.body?.string() ?: "")
                    }
                    val body = res.body ?: throw ApiException("Răspuns gol de la server.", res.code)
                    val total = body.contentLength()
                    val out = resolver.openOutputStream(dest, "wt")
                        ?: throw LocalFileException("Nu pot scrie în fișierul ales.")
                    out.use { os ->
                        body.byteStream().use { ins ->
                            val buf = ByteArray(81920)
                            var read = 0L
                            var last = -1
                            while (true) {
                                val n = ins.read(buf) // IOException aici = problema de retea
                                if (n < 0) break
                                try {
                                    os.write(buf, 0, n)
                                } catch (e: IOException) {
                                    throw LocalFileException(e.message ?: "Scrierea fișierului a eșuat.")
                                }
                                read += n
                                if (total > 0) {
                                    val p = (read * 100 / total).toInt().coerceAtMost(100)
                                    if (p != last) {
                                        last = p
                                        onProgress(p)
                                    }
                                }
                            }
                            os.flush()
                        }
                    }
                }
            } catch (e: ApiException) {
                throw e
            } catch (e: LocalFileException) {
                throw e
            } catch (e: InterruptedIOException) {
                throw timeoutError()
            } catch (e: IOException) {
                throw networkError()
            }
        }
    }
}

package md.vicproj.app

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Eroare provenita din API. statusCode == 0 inseamna ca nu s-a putut ajunge deloc la server. */
class ApiException(message: String, val statusCode: Int) : Exception(message)

/** Eroare la citirea/scrierea unui fisier local (nu la retea). */
class LocalFileException(message: String) : Exception(message)

data class TokenResponse(val accessToken: String)

internal fun JSONObject.str(name: String): String =
    if (isNull(name)) "" else optString(name, "")

private val ISO_REGEX =
    Regex("""^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2}):(\d{2})(?:\.\d+)?(Z|[+-]\d{2}:?\d{2})?$""")

/** FastAPI trimite ISO 8601 (uneori fara fus orar = UTC). */
internal fun parseIsoMillis(text: String): Long? {
    val m = ISO_REGEX.find(text.trim()) ?: return null
    val g = m.groupValues
    return try {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.clear()
        cal.set(g[1].toInt(), g[2].toInt() - 1, g[3].toInt(), g[4].toInt(), g[5].toInt(), g[6].toInt())
        var millis = cal.timeInMillis
        val tz = g[7]
        if (tz.isNotEmpty() && tz != "Z") {
            val sign = if (tz[0] == '-') -1 else 1
            val digits = tz.substring(1).replace(":", "")
            val minutes = digits.substring(0, 2).toInt() * 60 + digits.substring(2, 4).toInt()
            millis -= sign * minutes * 60_000L
        }
        millis
    } catch (e: Exception) {
        null
    }
}

data class FileOut(
    val id: String,
    val filename: String,
    val materia: String,
    val description: String,
    val sharedCode: String,
    val status: String,
    val createdAt: String,
) {
    val statusRomana: String
        get() = when (status) {
            "active" -> "Activ"
            "paused" -> "Pauzat"
            "blocked" -> "Blocat"
            else -> status
        }

    val createdAtMillis: Long? get() = parseIsoMillis(createdAt)

    val createdAtLocal: String
        get() {
            val ms = createdAtMillis ?: return createdAt
            return SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(ms))
        }

    companion object {
        fun from(o: JSONObject) = FileOut(
            id = o.str("id"),
            filename = o.str("filename"),
            materia = o.str("materia"),
            description = o.str("description"),
            sharedCode = o.str("shared_code"),
            status = o.str("status"),
            createdAt = o.str("created_at"),
        )
    }
}

data class FileUploadOut(val file: FileOut, val downloadPin: String)

data class RegeneratePinOut(val id: String, val downloadPin: String)

data class SharePublic(
    val filename: String,
    val materia: String,
    val description: String,
    val status: String,
    val sharedCode: String,
    val downloadAvailable: Boolean,
) {
    companion object {
        fun from(o: JSONObject) = SharePublic(
            filename = o.str("filename"),
            materia = o.str("materia"),
            description = o.str("description"),
            status = o.str("status"),
            sharedCode = o.str("shared_code"),
            downloadAvailable = o.optBoolean("download_available", false),
        )
    }
}

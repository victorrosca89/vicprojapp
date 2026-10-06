package md.vicproj.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil
import kotlin.math.max

/**
 * GenerareCodQR(text_sau_url): primeste un text / link si intoarce imaginea QR.
 * Complet nativ (ZXing), functioneaza offline si nu trimite nimic nicaieri.
 * Bitmap-ul se construieste din matricea QR, cu module de dimensiune intreaga,
 * alb/negru pur, fara estompare - ramane scanabil oricat se mareste.
 */
object QrGenerator {

    fun generareCodQR(text: String, minSize: Int = 1024): Bitmap {
        require(text.isNotBlank()) { "Textul pentru codul QR lipsește." }

        val hints = HashMap<EncodeHintType, Any>()
        hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.M
        hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
        hints[EncodeHintType.MARGIN] = 4 // zona alba ("quiet zone") standard

        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
        val w = matrix.width
        val h = matrix.height
        val scale = max(1, ceil(minSize / max(w, h).toDouble()).toInt())

        val outW = w * scale
        val outH = h * scale
        val pixels = IntArray(outW * outH)
        val black = 0xFF000000.toInt()
        val white = 0xFFFFFFFF.toInt()
        for (y in 0 until outH) {
            val my = y / scale
            val row = y * outW
            for (x in 0 until outW) {
                pixels[row + x] = if (matrix.get(x / scale, my)) black else white
            }
        }
        val bmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        bmp.setPixels(pixels, 0, outW, 0, 0, outW, outH)
        return bmp
    }
}

/** Copiere / salvare / distribuire a imaginii QR. */
object QrExport {

    private fun cacheUri(ctx: Context, bmp: Bitmap, name: String): Uri {
        val dir = File(ctx.cacheDir, "qr")
        dir.mkdirs()
        dir.listFiles()?.forEach {
            if (System.currentTimeMillis() - it.lastModified() > 3_600_000L) it.delete()
        }
        val file = File(dir, name)
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
    }

    fun copyImage(ctx: Context, bmp: Bitmap, code: String): Boolean {
        return try {
            val uri = cacheUri(ctx, bmp, "VicProj-QR-$code.png")
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newUri(ctx.contentResolver, "Cod QR VicProj", uri))
            true
        } catch (e: Exception) {
            false
        }
    }

    fun saveToUri(ctx: Context, bmp: Bitmap, dest: Uri): Boolean {
        return try {
            val out = ctx.contentResolver.openOutputStream(dest, "wt") ?: return false
            out.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun shareImage(ctx: Context, bmp: Bitmap, code: String, linkText: String): Boolean {
        return try {
            val uri = cacheUri(ctx, bmp, "VicProj-QR-$code.png")
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, linkText)
                clipData = ClipData.newRawUri("", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(send, "Distribuie codul QR")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(chooser)
            true
        } catch (e: Exception) {
            false
        }
    }
}

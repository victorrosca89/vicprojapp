package md.vicproj.app

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/** Aceleasi sunete (error.wav / success.wav) ca in aplicatia Windows. */
object Sounds {
    private const val MIN_REPEAT_MS = 400L

    private var pool: SoundPool? = null
    private var errorId = 0
    private var successId = 0
    private var lastError = 0L
    private var lastSuccess = 0L

    fun init(context: Context) {
        if (pool != null) return
        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val p = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(attrs).build()
            errorId = p.load(context.applicationContext, R.raw.error, 1)
            successId = p.load(context.applicationContext, R.raw.success, 1)
            pool = p
        } catch (e: Exception) {
            pool = null
        }
    }

    fun error() {
        val now = System.currentTimeMillis()
        if (now - lastError < MIN_REPEAT_MS) return
        lastError = now
        play(errorId)
    }

    fun success() {
        val now = System.currentTimeMillis()
        if (now - lastSuccess < MIN_REPEAT_MS) return
        lastSuccess = now
        play(successId)
    }

    private fun play(id: Int) {
        try {
            pool?.play(id, 1f, 1f, 1, 0, 1f)
        } catch (e: Exception) {
            // sunetul e un bonus, nu ceva critic
        }
    }
}

package md.vicproj.app

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Sesiunea de administrator se pastreaza criptat (Android Keystore, AES-256),
 * echivalentul SecureStore.cs (DPAPI) din aplicatia Windows.
 * Daca stocarea criptata nu poate fi creata pe un dispozitiv, tokenul NU se
 * salveaza deloc (administratorul va introduce din nou codul) - nu ajunge niciodata necriptat pe disc.
 */
class SecureStore(context: Context) {

    private val app = context.applicationContext
    private val secure: SharedPreferences? = createSecure()
    private val plain: SharedPreferences = app.getSharedPreferences("vicproj_prefs", Context.MODE_PRIVATE)

    private fun createSecure(): SharedPreferences? {
        return try {
            val key = MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            EncryptedSharedPreferences.create(
                app,
                "vicproj_secure",
                key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            try {
                app.deleteSharedPreferences("vicproj_secure")
            } catch (e2: Exception) {
                // ignoram
            }
            null
        }
    }

    fun saveToken(token: String) {
        try {
            secure?.edit()?.putString("token", token)?.apply()
        } catch (e: Exception) {
            // ignoram
        }
    }

    fun loadToken(): String? = try {
        secure?.getString("token", null)
    } catch (e: Exception) {
        null
    }

    fun clearToken() {
        try {
            secure?.edit()?.remove("token")?.apply()
        } catch (e: Exception) {
            // ignoram
        }
    }

    /** Permisiunea data in aplicatie pentru camera (cererea din VicProj, inainte de cea din Android). */
    var cameraConsent: Boolean
        get() = plain.getBoolean("camera_consent", false)
        set(value) {
            plain.edit().putBoolean("camera_consent", value).apply()
        }
}

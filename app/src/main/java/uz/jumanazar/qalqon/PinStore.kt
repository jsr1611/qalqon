package uz.jumanazar.qalqon

import android.content.Context
import java.security.MessageDigest

/** Stores a salted SHA-256 hash of the parent PIN. Default PIN is 1234 until changed. */
class PinStore(context: Context) {

    private val prefs =
        context.getSharedPreferences("qalqon_secure", Context.MODE_PRIVATE)

    val isConfigured: Boolean get() = prefs.contains(KEY_HASH)

    fun verify(pin: String): Boolean {
        val stored = prefs.getString(KEY_HASH, hash(DEFAULT_PIN))
        return stored == hash(pin)
    }

    fun setPin(pin: String) {
        prefs.edit().putString(KEY_HASH, hash(pin)).apply()
    }

    private fun hash(pin: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(("qalqon::$pin").toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val KEY_HASH = "pin_hash"
        private const val DEFAULT_PIN = "1234"
    }
}

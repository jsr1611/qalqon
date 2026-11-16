package uz.jumanazar.qalqon

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Stores a salted SHA-256 hash of the parent PIN (default 1234 until changed) plus a
 * one-time 10-digit RECOVERY CODE. The recovery code is the only way back in if the
 * parent forgets the password, since Settings are locked and the app can't be
 * uninstalled. It is generated once and kept so the dashboard can show it again.
 */
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

    /** The 10-digit recovery code, generated and stored on first access. */
    val recoveryCode: String
        get() {
            prefs.getString(KEY_RECOVERY, null)?.let { return it }
            val code = generateCode()
            prefs.edit().putString(KEY_RECOVERY, code).apply()
            return code
        }

    /** True if [code] matches the recovery code (ignoring spaces). */
    fun verifyRecovery(code: String): Boolean =
        code.filter { it.isDigit() } == recoveryCode

    private fun generateCode(): String {
        val rnd = SecureRandom()
        return buildString { repeat(10) { append(rnd.nextInt(10)) } }
    }

    private fun hash(pin: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(("qalqon::$pin").toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val KEY_HASH = "pin_hash"
        private const val KEY_RECOVERY = "recovery_code"
        private const val DEFAULT_PIN = "1234"
    }
}

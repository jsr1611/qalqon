package uz.jumanazar.qalqon

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Stores a salted SHA-256 hash of the parent PIN (default 1234 until changed), a one-time
 * 10-digit static RECOVERY CODE, and a stable 6-digit DEVICE ID used for developer
 * challenge-response recovery.
 *
 * Two ways back in if the parent forgets the password (Settings are locked and the app
 * can't be uninstalled):
 *   1. Static recovery code — self-service; shown in the dashboard, parent must save it.
 *   2. Challenge-response — parent reads the Device ID to the developer, who runs a
 *      private script (holding secret [recoverySecret]) to produce a day-specific 8-digit
 *      code. Changes daily, per-device; the developer never stores anything.
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

    /** The 10-digit static recovery code, generated and stored on first access. */
    val recoveryCode: String
        get() {
            prefs.getString(KEY_RECOVERY, null)?.let { return it }
            val code = buildString { val rnd = SecureRandom(); repeat(10) { append(rnd.nextInt(10)) } }
            prefs.edit().putString(KEY_RECOVERY, code).apply()
            return code
        }

    /** True if [code] matches the static recovery code (ignoring non-digits). */
    fun verifyStaticRecovery(code: String): Boolean =
        code.filter { it.isDigit() }.let { it.isNotEmpty() && it == recoveryCode }

    /** Stable 6-digit device identifier, generated once. Shown on the recovery screen. */
    val deviceId: String
        get() {
            prefs.getString(KEY_DEVICE_ID, null)?.let { return it }
            val id = buildString {
                val rnd = SecureRandom()
                append(1 + rnd.nextInt(9))          // never a leading zero
                repeat(5) { append(rnd.nextInt(10)) }
            }
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
            return id
        }

    /**
     * True if [code] is a valid developer recovery code for this device. The code is
     * HMAC-SHA256(secret, "deviceId|yyyyMMdd") reduced to 8 digits. Today plus a ±1-day
     * window is accepted so a timezone difference between parent and developer is harmless.
     */
    fun verifyChallenge(code: String): Boolean {
        val entered = code.filter { it.isDigit() }
        if (entered.length != CHALLENGE_DIGITS) return false
        val cal = Calendar.getInstance()
        val fmt = SimpleDateFormat("yyyyMMdd", Locale.US)
        for (dayOffset in -1..1) {
            val c = cal.clone() as Calendar
            c.add(Calendar.DAY_OF_YEAR, dayOffset)
            if (entered == challengeCode(deviceId, fmt.format(c.time))) return true
        }
        return false
    }

    /** Accepts either the static recovery code or a developer challenge code. */
    fun verifyRecovery(code: String): Boolean =
        verifyStaticRecovery(code) || verifyChallenge(code)

    private fun challengeCode(deviceId: String, yyyymmdd: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(BuildConfig.RECOVERY_SECRET.toByteArray(), "HmacSHA256"))
        val digest = mac.doFinal("$deviceId|$yyyymmdd".toByteArray())
        // Dynamic truncation (RFC 4226 style) to a positive number, then last 8 digits.
        val offset = digest[digest.size - 1].toInt() and 0x0f
        val bin = ((digest[offset].toInt() and 0x7f) shl 24) or
            ((digest[offset + 1].toInt() and 0xff) shl 16) or
            ((digest[offset + 2].toInt() and 0xff) shl 8) or
            (digest[offset + 3].toInt() and 0xff)
        return (bin % 100_000_000).toString().padStart(CHALLENGE_DIGITS, '0')
    }

    private fun hash(pin: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(("qalqon::$pin").toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val KEY_HASH = "pin_hash"
        private const val KEY_RECOVERY = "recovery_code"
        private const val KEY_DEVICE_ID = "device_id"
        private const val DEFAULT_PIN = "1234"
        private const val CHALLENGE_DIGITS = 8
    }
}

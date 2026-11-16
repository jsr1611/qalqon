package uz.jumanazar.qalqon

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Fingerprint / face unlock via BiometricPrompt. */
object BiometricAuth {

    // WEAK covers BOTH fingerprint and face (many phones are face-only, and Samsung
    // face is Class 2 / "weak"). Confirmation is required below so passive face
    // recognition still needs a deliberate tap to unlock.
    private const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK

    /** A fingerprint or face is enrolled and usable. */
    fun isAvailable(ctx: Context): Boolean =
        BiometricManager.from(ctx).canAuthenticate(AUTHENTICATORS) ==
            BiometricManager.BIOMETRIC_SUCCESS

    fun prompt(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onFallback: () -> Unit
    ) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }
                override fun onAuthenticationError(code: Int, msg: CharSequence) {
                    onFallback() // user cancelled or hit "Use password"
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock")
            .setSubtitle("Verify to continue")
            .setNegativeButtonText("Use password")
            .setAllowedAuthenticators(AUTHENTICATORS)
            .setConfirmationRequired(true) // face must be confirmed, not a passive glance
            .build()
        prompt.authenticate(info)
    }
}

/** Persisted "unlock with fingerprint/face" toggle. */
class BioPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("qalqon_bio", Context.MODE_PRIVATE)
    var enabled: Boolean
        get() = prefs.getBoolean("bio_enabled", false)
        set(value) = prefs.edit().putBoolean("bio_enabled", value).apply()
}

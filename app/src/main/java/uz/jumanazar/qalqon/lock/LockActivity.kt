package uz.jumanazar.qalqon.lock

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import uz.jumanazar.qalqon.BioPrefs
import uz.jumanazar.qalqon.BiometricAuth
import uz.jumanazar.qalqon.PinStore
import uz.jumanazar.qalqon.R

/**
 * Full-screen PIN gate drawn over a protected app.
 *  - Correct PIN  -> unlock that package and return to it.
 *  - Wrong PIN    -> just clears the field. No counter, no timeout, no penalty.
 *  - Back / Home  -> leaves the app (sends the user to the launcher).
 */
class LockActivity : FragmentActivity() {

    private lateinit var targetPackage: String
    private lateinit var pinStore: PinStore

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(uz.jumanazar.qalqon.LocaleHelper.wrap(newBase))
    }

    private fun unlockAndReturn() {
        LockState.unlock(targetPackage)
        finish() // returns to the app behind us
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        uz.jumanazar.qalqon.LocaleHelper.apply(this)
        targetPackage = intent.getStringExtra(EXTRA_PACKAGE) ?: run { finish(); return }
        pinStore = PinStore(this)
        setShowWhenLocked(true)

        val bioEnabled = BioPrefs(this).enabled && BiometricAuth.isAvailable(this)

        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    LockScreen(
                        bioEnabled = bioEnabled,
                        onSubmit = { pin ->
                            if (pinStore.verify(pin)) { unlockAndReturn(); true } else false
                        },
                        onBiometric = { promptBiometric() },
                        onLeave = { goHome() }
                    )
                }
            }
        }

        if (bioEnabled) promptBiometric()
        if (isInMultiWindowMode) escapeMultiWindow()
    }

    override fun onResume() {
        super.onResume()
        if (isInMultiWindowMode) escapeMultiWindow()
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        if (isInMultiWindowMode) escapeMultiWindow()
    }

    /**
     * Split screen / pop-up view: the gate would cover only part of the screen
     * while the protected app stays usable next to it. Keep the app locked and
     * press Home, which closes the split.
     */
    private fun escapeMultiWindow() {
        LockState.relock(targetPackage)
        if (LockAccessibilityService.pressHome()) finish() else goHome()
    }

    private fun promptBiometric() {
        BiometricAuth.prompt(
            activity = this,
            onSuccess = { unlockAndReturn() },
            onFallback = { /* stay on PIN screen */ }
        )
    }

    private fun goHome() {
        val home = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(home)
        finish()
    }

    // Back button = leave the protected app (do not expose it).
    override fun onBackPressed() {
        goHome()
    }

    companion object {
        const val EXTRA_PACKAGE = "target_package"
    }
}

@Composable
private fun LockScreen(
    bioEnabled: Boolean,
    onSubmit: (String) -> Boolean,
    onBiometric: () -> Unit,
    onLeave: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Deliberately generic: no app name, no branding. The child must not learn
        // which app is doing the blocking.
        Text("🔒", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.lock_enter_password),
            style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 12) { pin = it; wrong = false } },
            label = { Text(stringResource(R.string.password)) },
            isError = wrong,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
        )
        if (wrong) {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.incorrect), color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = {
                val ok = onSubmit(pin)
                if (!ok) { wrong = true; pin = "" }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.unlock)) }
        if (bioEnabled) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onBiometric, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.use_biometric))
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onLeave, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.cancel))
        }
    }
}

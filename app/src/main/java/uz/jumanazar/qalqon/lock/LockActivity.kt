package uz.jumanazar.qalqon.lock

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import uz.jumanazar.qalqon.PinStore

/**
 * Full-screen PIN gate drawn over a protected app.
 *  - Correct PIN  -> unlock that package and return to it.
 *  - Wrong PIN    -> just clears the field. No counter, no timeout, no penalty.
 *  - Back / Home  -> leaves the app (sends the user to the launcher).
 */
class LockActivity : ComponentActivity() {

    private lateinit var targetPackage: String
    private lateinit var pinStore: PinStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        targetPackage = intent.getStringExtra(EXTRA_PACKAGE) ?: run { finish(); return }
        pinStore = PinStore(this)
        setShowWhenLocked(true)

        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    LockScreen(
                        onSubmit = { pin ->
                            if (pinStore.verify(pin)) {
                                LockState.unlock(targetPackage)
                                finish() // returns to the app behind us
                                true
                            } else {
                                false // wrong -> stay, no penalty
                            }
                        },
                        onLeave = { goHome() }
                    )
                }
            }
        }
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
private fun LockScreen(onSubmit: (String) -> Boolean, onLeave: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🛡 Aqlli Qalqon", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text("Enter password to open this app", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 12) { pin = it; wrong = false } },
            label = { Text("Password") },
            isError = wrong,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
        )
        if (wrong) {
            Spacer(Modifier.height(6.dp))
            Text("Wrong password — try again", color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = {
                val ok = onSubmit(pin)
                if (!ok) { wrong = true; pin = "" }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Unlock") }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onLeave, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
    }
}

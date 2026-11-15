package uz.jumanazar.qalqon

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import uz.jumanazar.qalqon.policy.PolicyManager
import uz.jumanazar.qalqon.vpn.DnsFilterVpnService

/**
 * Single-activity, PIN-gated parent dashboard. Shows a lock screen first; once the
 * parent PIN is verified it exposes the policy toggles.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val policy = PolicyManager(this)
        val pinStore = PinStore(this)

        setContent {
            MaterialTheme {
                var unlocked by remember { mutableStateOf(false) }
                Surface(Modifier.fillMaxSize()) {
                    if (unlocked) {
                        Dashboard(policy)
                    } else {
                        PinGate(pinStore) { unlocked = true }
                    }
                }
            }
        }
    }
}

@Composable
private fun PinGate(pinStore: PinStore, onUnlock: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Aqlli Qalqon", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text("Parent PIN", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 8) { pin = it; error = false } },
            label = { Text("PIN") },
            isError = error,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
        )
        if (error) {
            Spacer(Modifier.height(4.dp))
            Text("Incorrect PIN", color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            if (pinStore.verify(pin)) onUnlock() else { error = true; pin = "" }
        }) { Text("Unlock") }
        Spacer(Modifier.height(24.dp))
        Text(
            "Default PIN is 1234 — change it after first unlock.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun Dashboard(policy: PolicyManager) {
    val ctx = LocalContextSafe()
    var appsSuspended by remember { mutableStateOf(policy.isDeviceOwner) }
    var dnsOn by remember { mutableStateOf(true) }
    var adbDisabled by remember { mutableStateOf(true) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)
    ) {
        Text("Dashboard", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        val mode = if (policy.isDeviceOwner) "Device Owner ✓"
        else if (policy.isAdminActive) "Device Admin (limited)"
        else "Not provisioned"
        Text("Mode: $mode", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(20.dp))

        ToggleRow("Block apps (Roblox, YouTube, Stores)", appsSuspended) {
            appsSuspended = it; policy.setAppsSuspended(it)
        }
        ToggleRow("Content DNS filter", dnsOn) {
            dnsOn = it
            if (it) DnsFilterVpnService.start(ctx)
            else ctx.stopService(android.content.Intent(ctx, DnsFilterVpnService::class.java))
        }
        ToggleRow("Block USB debugging (ADB)", adbDisabled) {
            adbDisabled = it; policy.setDebuggingDisabled(it)
        }

        Spacer(Modifier.height(28.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))
        Text("Danger zone", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { policy.releaseDevice() }) {
            Text("Release device (remove all controls)")
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun LocalContextSafe() = androidx.compose.ui.platform.LocalContext.current

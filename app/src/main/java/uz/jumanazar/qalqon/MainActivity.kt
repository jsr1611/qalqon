package uz.jumanazar.qalqon

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import uz.jumanazar.qalqon.admin.QalqonAdminReceiver
import uz.jumanazar.qalqon.lock.LockAccessibilityService
import uz.jumanazar.qalqon.lock.ProtectedAppsStore

data class AppInfo(val label: String, val pkg: String)

class MainActivity : ComponentActivity() {

    private val accessibilityOn = mutableStateOf(false)
    private val adminOn = mutableStateOf(false)
    private lateinit var dpm: DevicePolicyManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val pinStore = PinStore(this)
        val store = ProtectedAppsStore(this)
        val apps = loadLaunchableApps()

        setContent {
            MaterialTheme {
                var unlocked by remember { mutableStateOf(false) }
                Surface(Modifier.fillMaxSize()) {
                    if (unlocked) {
                        Dashboard(
                            apps = apps,
                            store = store,
                            pinStore = pinStore,
                            accessibilityOn = accessibilityOn.value,
                            adminOn = adminOn.value,
                            onOpenAccessibility = {
                                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            },
                            onEnableAdmin = { requestAdmin() }
                        )
                    } else {
                        PinGate(pinStore) { unlocked = true }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        accessibilityOn.value = isAccessibilityEnabled(this)
        adminOn.value = dpm.isAdminActive(QalqonAdminReceiver.component(this))
    }

    private fun requestAdmin() {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, QalqonAdminReceiver.component(this@MainActivity))
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Enable so the app can't be uninstalled without the parent password."
            )
        }
        startActivity(intent)
    }

    private fun loadLaunchableApps(): List<AppInfo> {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .asSequence()
            .map { it.activityInfo }
            .filter { it.packageName != packageName }
            .map { AppInfo(it.loadLabel(pm).toString(), it.packageName) }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    private fun isAccessibilityEnabled(ctx: Context): Boolean {
        val flat = Settings.Secure.getString(
            ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val target = ComponentName(ctx, LockAccessibilityService::class.java).flattenToString()
        return flat.split(':').any { it.equals(target, ignoreCase = true) }
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
        Text("🛡 Aqlli Qalqon", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 12) { pin = it; error = false } },
            label = { Text("Parent password") },
            isError = error,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
        )
        if (error) {
            Spacer(Modifier.height(4.dp))
            Text("Incorrect password", color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            if (pinStore.verify(pin)) onUnlock() else { error = true; pin = "" }
        }) { Text("Unlock") }
        Spacer(Modifier.height(20.dp))
        Text(
            "Default password is 1234 — change it below after unlocking.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun Dashboard(
    apps: List<AppInfo>,
    store: ProtectedAppsStore,
    pinStore: PinStore,
    accessibilityOn: Boolean,
    adminOn: Boolean,
    onOpenAccessibility: () -> Unit,
    onEnableAdmin: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    // Local reactive copy of the protected set.
    var protectedSet by remember { mutableStateOf(store.all()) }

    Column(Modifier.fillMaxSize()) {
        // --- Status + settings header (scrolls with nothing; fixed top) ---
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Text("Aqlli Qalqon", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(10.dp))

            StatusRow(
                label = "Protection service",
                ok = accessibilityOn,
                okText = "On",
                badText = "Off — tap to enable",
                onClick = onOpenAccessibility
            )
            StatusRow(
                label = "Uninstall protection",
                ok = adminOn,
                okText = "On",
                badText = "Off — tap to enable",
                onClick = onEnableAdmin
            )
            Spacer(Modifier.height(10.dp))
            ChangePin(pinStore)
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text("Protected apps (need password to open)",
                style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search apps") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        val filtered = remember(query, apps) {
            if (query.isBlank()) apps
            else apps.filter { it.label.contains(query, true) || it.pkg.contains(query, true) }
        }

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            items(filtered, key = { it.pkg }) { app ->
                val isProtected = protectedSet.contains(app.pkg)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.bodyLarge)
                        Text(app.pkg, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = isProtected,
                        onCheckedChange = { on ->
                            store.setProtected(app.pkg, on)
                            protectedSet = store.all()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusRow(
    label: String, ok: Boolean, okText: String, badText: String, onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f))
        Text(
            if (ok) "● $okText" else "● $badText",
            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun ChangePin(pinStore: PinStore) {
    var newPin by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = newPin,
            onValueChange = { if (it.length <= 12) { newPin = it; saved = false } },
            label = { Text(if (saved) "Password saved" else "New password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = { if (newPin.length >= 4) { pinStore.setPin(newPin); newPin = ""; saved = true } },
            enabled = newPin.length >= 4
        ) { Text("Set") }
    }
}

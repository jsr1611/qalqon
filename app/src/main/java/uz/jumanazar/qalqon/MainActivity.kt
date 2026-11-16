package uz.jumanazar.qalqon

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
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
import uz.jumanazar.qalqon.stealth.AppDisguise
import uz.jumanazar.qalqon.vpn.DnsFilterVpnService
import uz.jumanazar.qalqon.vpn.DnsPrefs

data class AppInfo(val label: String, val pkg: String)

class MainActivity : FragmentActivity() {

    private val accessibilityOn = mutableStateOf(false)
    private val adminOn = mutableStateOf(false)
    private val dnsOn = mutableStateOf(false)
    private val iconMode = mutableStateOf(AppDisguise.Mode.NORMAL)
    private val bioOn = mutableStateOf(false)
    private lateinit var dpm: DevicePolicyManager
    private lateinit var dnsPrefs: DnsPrefs
    private lateinit var bioPrefs: BioPrefs

    private val vpnConsent = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            DnsFilterVpnService.start(this)
            dnsPrefs.enabled = true
            dnsOn.value = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        dnsPrefs = DnsPrefs(this)
        bioPrefs = BioPrefs(this)
        val bioAvailable = BiometricAuth.isAvailable(this)
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
                            dnsOn = dnsOn.value,
                            iconMode = iconMode.value,
                            bioAvailable = bioAvailable,
                            bioOn = bioOn.value,
                            onToggleBio = { on -> bioPrefs.enabled = on; bioOn.value = on },
                            onOpenAccessibility = {
                                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            },
                            onEnableAdmin = { requestAdmin() },
                            onToggleDns = { on -> toggleDns(on) },
                            onSetIconMode = { mode ->
                                AppDisguise.setMode(this@MainActivity, mode)
                                iconMode.value = mode
                            }
                        )
                    } else {
                        PinGate(
                            pinStore = pinStore,
                            bioEnabled = bioAvailable && bioPrefs.enabled,
                            onBiometric = { promptDashboardBiometric { unlocked = true } },
                            onUnlock = { unlocked = true }
                        )
                    }
                }
            }
        }
    }

    private fun promptDashboardBiometric(onSuccess: () -> Unit) {
        BiometricAuth.prompt(activity = this, onSuccess = onSuccess, onFallback = { })
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        accessibilityOn.value = isAccessibilityEnabled(this)
        adminOn.value = dpm.isAdminActive(QalqonAdminReceiver.component(this))
        dnsOn.value = dnsPrefs.enabled
        iconMode.value = AppDisguise.currentMode(this)
        bioOn.value = bioPrefs.enabled
    }

    private fun toggleDns(on: Boolean) {
        if (on) {
            val consent = VpnService.prepare(this)
            if (consent != null) {
                vpnConsent.launch(consent) // asks the user to allow the VPN once
            } else {
                DnsFilterVpnService.start(this)
                dnsPrefs.enabled = true
                dnsOn.value = true
            }
        } else {
            DnsFilterVpnService.stop(this)
            dnsPrefs.enabled = false
            dnsOn.value = false
        }
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
private fun PinGate(
    pinStore: PinStore,
    bioEnabled: Boolean,
    onBiometric: () -> Unit,
    onUnlock: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var recoveryMode by remember { mutableStateOf(false) }
    var recovery by remember { mutableStateOf("") }
    var recoveryError by remember { mutableStateOf(false) }

    // Offer biometric first when enabled; password stays available as fallback.
    LaunchedEffect(Unit) { if (bioEnabled) onBiometric() }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🛡 Aqlli Qalqon", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        if (!recoveryMode) {
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
            if (bioEnabled) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onBiometric) { Text("Use fingerprint / face") }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { recoveryMode = true }) { Text("Forgot password?") }
            if (!pinStore.isConfigured) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "First time: default password is 1234 — change it after unlocking.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else {
            Text("Enter your 10-digit recovery code", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = recovery,
                onValueChange = { if (it.length <= 14) { recovery = it; recoveryError = false } },
                label = { Text("Recovery code") },
                isError = recoveryError,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
            )
            if (recoveryError) {
                Spacer(Modifier.height(4.dp))
                Text("Incorrect recovery code", color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                if (pinStore.verifyRecovery(recovery)) onUnlock()
                else { recoveryError = true; recovery = "" }
            }) { Text("Recover") }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { recoveryMode = false }) { Text("Back to password") }
        }
    }
}

@Composable
private fun IconModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) }
    )
}

@Composable
private fun Dashboard(
    apps: List<AppInfo>,
    store: ProtectedAppsStore,
    pinStore: PinStore,
    accessibilityOn: Boolean,
    adminOn: Boolean,
    dnsOn: Boolean,
    iconMode: AppDisguise.Mode,
    bioAvailable: Boolean,
    bioOn: Boolean,
    onToggleBio: (Boolean) -> Unit,
    onOpenAccessibility: () -> Unit,
    onEnableAdmin: () -> Unit,
    onToggleDns: (Boolean) -> Unit,
    onSetIconMode: (AppDisguise.Mode) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var protectedSet by remember { mutableStateOf(store.all()) }
    var settingsExpanded by remember { mutableStateOf(false) }

    val filtered = remember(query, apps) {
        if (query.isBlank()) apps
        else apps.filter { it.label.contains(query, true) || it.pkg.contains(query, true) }
    }

    // Everything lives in one scrolling list; the settings block collapses so the
    // app list gets the screen.
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        item {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Aqlli Qalqon", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Service: ${if (accessibilityOn) "On" else "Off"}   ·   " +
                            "Uninstall: ${if (adminOn) "On" else "Off"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (accessibilityOn && adminOn)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                }
                TextButton(onClick = { settingsExpanded = !settingsExpanded }) {
                    Text(if (settingsExpanded) "Hide ▲" else "Settings ▼")
                }
            }

            if (settingsExpanded) {
                StatusRow("Protection service", accessibilityOn, "On",
                    "Off — tap to enable", onOpenAccessibility)
                StatusRow("Uninstall protection", adminOn, "On",
                    "Off — tap to enable", onEnableAdmin)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Block YouTube/Roblox in browsers")
                        Text(
                            "Optional DNS filter. Also blocks them for you until turned off.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(checked = dnsOn, onCheckedChange = onToggleDns)
                }
                Text("Home-screen icon", Modifier.padding(top = 8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconModeChip("Normal", iconMode == AppDisguise.Mode.NORMAL) {
                        onSetIconMode(AppDisguise.Mode.NORMAL)
                    }
                    IconModeChip("Calculator", iconMode == AppDisguise.Mode.DISGUISE) {
                        onSetIconMode(AppDisguise.Mode.DISGUISE)
                    }
                    IconModeChip("Hidden", iconMode == AppDisguise.Mode.HIDDEN) {
                        onSetIconMode(AppDisguise.Mode.HIDDEN)
                    }
                }
                if (iconMode == AppDisguise.Mode.HIDDEN) {
                    Text(
                        "⚠ No icon. Reopen by pressing Volume Up 5× quickly (needs the " +
                            "protection service on). If that fails, reinstall the app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (bioAvailable) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Unlock with fingerprint / face", Modifier.weight(1f))
                        Switch(checked = bioOn, onCheckedChange = onToggleBio)
                    }
                }
                Spacer(Modifier.height(8.dp))
                ChangePin(pinStore)
                Spacer(Modifier.height(10.dp))
                Text(
                    "🔑 Recovery code: ${pinStore.recoveryCode}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Write this down and keep it safe. It's the ONLY way back in if you " +
                        "forget your password (via \"Forgot password?\" on the unlock screen).",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(10.dp))
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
            Spacer(Modifier.height(8.dp))
        }

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

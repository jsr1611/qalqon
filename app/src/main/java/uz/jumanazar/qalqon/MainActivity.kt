package uz.jumanazar.qalqon

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import uz.jumanazar.qalqon.admin.QalqonAdminReceiver
import uz.jumanazar.qalqon.lock.LockAccessibilityService
import uz.jumanazar.qalqon.lock.ProtectedAppsStore
import uz.jumanazar.qalqon.stealth.AppDisguise
import uz.jumanazar.qalqon.vpn.DnsFilterVpnService
import uz.jumanazar.qalqon.vpn.DnsPrefs

data class AppInfo(
    val label: String,
    val pkg: String,
    val icon: android.graphics.drawable.Drawable? = null
)

/** Languages offered in the in-app picker. Native names are shown as-is (not translated). */
data class LangOption(val tag: String, val flag: String, val own: String, val english: String)

val APP_LANGUAGES = listOf(
    LangOption("uz", "🇺🇿", "Özbekça", "Uzbek"),
    LangOption("en", "🇬🇧", "English", "English"),
    LangOption("ru", "🇷🇺", "Русский", "Russian"),
    LangOption("ko", "🇰🇷", "한국어", "Korean"),
    LangOption("ar", "🇸🇦", "العربية", "Arabic")
)

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

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
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
            .map { AppInfo(it.loadLabel(pm).toString(), it.packageName, it.loadIcon(pm)) }
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
        Text("🛡 " + stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        if (!recoveryMode) {
            OutlinedTextField(
                value = pin,
                onValueChange = { if (it.length <= 12) { pin = it; error = false } },
                label = { Text(stringResource(R.string.parent_password)) },
                isError = error,
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
            )
            if (error) {
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.incorrect_password),
                    color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                if (pinStore.verify(pin)) onUnlock() else { error = true; pin = "" }
            }) { Text(stringResource(R.string.unlock)) }
            if (bioEnabled) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onBiometric) {
                    Text(stringResource(R.string.use_biometric))
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { recoveryMode = true }) {
                Text(stringResource(R.string.forgot_password))
            }
            if (!pinStore.isConfigured) {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.first_time_hint),
                    style = MaterialTheme.typography.bodySmall)
            }
        } else {
            val today = remember {
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                    .format(java.util.Date())
            }
            Text(stringResource(R.string.recovery_intro),
                style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.device_id_line, pinStore.deviceId),
                style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.date_line, today),
                style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = recovery,
                onValueChange = { if (it.length <= 14) { recovery = it; recoveryError = false } },
                label = { Text(stringResource(R.string.recovery_code)) },
                isError = recoveryError,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
            )
            if (recoveryError) {
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.incorrect_recovery),
                    color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                if (pinStore.verifyRecovery(recovery)) onUnlock()
                else { recoveryError = true; recovery = "" }
            }) { Text(stringResource(R.string.recover)) }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { recoveryMode = false }) {
                Text(stringResource(R.string.back_to_password))
            }
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

private val GoodColor = androidx.compose.ui.graphics.Color(0xFF1AA06D)
private val WarnColor = androidx.compose.ui.graphics.Color(0xFFC6761A)
private val LockedColor = androidx.compose.ui.graphics.Color(0xFFD84A4A)

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
    var paused by remember { mutableStateOf(store.paused) }
    var settingsExpanded by remember { mutableStateOf(false) }
    var showLangPicker by remember { mutableStateOf(false) }

    if (showLangPicker) {
        LanguagePickerDialog(onDismiss = { showLangPicker = false })
    }

    // Undo banner state for "Unlock all".
    var undoBackup by remember { mutableStateOf<Set<String>?>(null) }
    var undoTick by remember { mutableStateOf(0) }
    LaunchedEffect(undoTick) {
        if (undoBackup != null) {
            kotlinx.coroutines.delay(5000)
            undoBackup = null
        }
    }

    val filtered = remember(query, apps) {
        if (query.isBlank()) apps
        else apps.filter { it.label.contains(query, true) || it.pkg.contains(query, true) }
    }
    val lockedApps = remember(filtered, protectedSet) {
        filtered.filter { protectedSet.contains(it.pkg) }
    }
    val openApps = remember(filtered, protectedSet) {
        filtered.filter { !protectedSet.contains(it.pkg) }
    }
    val setupDone = accessibilityOn && adminOn && pinStore.isConfigured

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        item {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🛡", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f))
                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.clip(androidx.compose.foundation.shape.CircleShape)
                        .clickable { showLangPicker = true }
                ) {
                    Text("🌐", style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(8.dp))
                }
            }
            Spacer(Modifier.height(12.dp))

            // ---- STATUS HERO ----
            val heroColor = when {
                paused -> MaterialTheme.colorScheme.surfaceVariant
                setupDone -> GoodColor.copy(alpha = 0.12f)
                else -> WarnColor.copy(alpha = 0.12f)
            }
            Surface(color = heroColor, shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val dot = when {
                            paused -> MaterialTheme.colorScheme.onSurfaceVariant
                            setupDone -> GoodColor
                            else -> WarnColor
                        }
                        Box(
                            Modifier.size(11.dp)
                                .background(dot, androidx.compose.foundation.shape.CircleShape)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(when {
                                    paused -> R.string.protection_paused
                                    setupDone -> R.string.protection_on
                                    else -> R.string.setup_not_finished
                                }),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                if (paused) stringResource(R.string.hero_paused_sub)
                                else stringResource(R.string.hero_locked_sub, protectedSet.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = !paused,
                            onCheckedChange = {
                                paused = !paused
                                store.paused = paused
                            }
                        )
                    }
                    if (!paused) {
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusPill(accessibilityOn,
                                stringResource(R.string.service_active),
                                stringResource(R.string.service_off))
                            StatusPill(adminOn,
                                stringResource(R.string.uninstall_blocked),
                                stringResource(R.string.uninstall_off))
                        }
                    }
                }
            }

            // ---- SETUP CHECKLIST (only while something is undone) ----
            if (!setupDone) {
                Spacer(Modifier.height(12.dp))
                Surface(color = WarnColor.copy(alpha = 0.12f), shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("⚠ " + stringResource(R.string.finish_setup),
                            style = MaterialTheme.typography.titleSmall, color = WarnColor)
                        SetupStep(pinStore.isConfigured,
                            stringResource(R.string.step_change_password), null)
                        SetupStep(accessibilityOn,
                            stringResource(R.string.step_accessibility),
                            if (accessibilityOn) null else onOpenAccessibility)
                        SetupStep(adminOn,
                            stringResource(R.string.step_uninstall),
                            if (adminOn) null else onEnableAdmin)
                    }
                }
            }

            // ---- QUICK ACTIONS (only while there's a recommended app not yet locked) ----
            val recommendedPending = remember(protectedSet) {
                store.recommended() - protectedSet
            }
            if (recommendedPending.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = {
                        store.lockAll(store.recommended())
                        protectedSet = store.all()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.lock_recommended)) }
            }

            // ---- APP LIST ----
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.search_apps)) },
                leadingIcon = { Text("🔎") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            if (lockedApps.isNotEmpty()) {
                Text(stringResource(R.string.locked_group, lockedApps.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        items(lockedApps, key = { "L_" + it.pkg }) { app ->
            AppRow(app, true) { on ->
                store.setProtected(app.pkg, on); protectedSet = store.all()
            }
        }

        item {
            if (openApps.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.open_group),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        items(openApps, key = { "U_" + it.pkg }) { app ->
            AppRow(app, false) { on ->
                store.setProtected(app.pkg, on); protectedSet = store.all()
            }
        }

        // ---- SETTINGS (collapsible) ----
        item {
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = { settingsExpanded = !settingsExpanded }) {
                Text(stringResource(
                    if (settingsExpanded) R.string.settings_hide else R.string.settings_show))
            }
            if (settingsExpanded) {
                OutlinedButton(
                    onClick = {
                        undoBackup = protectedSet
                        store.unlockAll()
                        protectedSet = store.all()
                        undoTick++
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.unlock_all)) }
                undoBackup?.let { backup ->
                    Spacer(Modifier.height(8.dp))
                    Surface(color = MaterialTheme.colorScheme.inverseSurface,
                        shape = MaterialTheme.shapes.medium) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.unlocked_all), Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.inverseOnSurface)
                            TextButton(onClick = {
                                store.lockAll(backup)
                                protectedSet = store.all()
                                undoBackup = null
                            }) { Text(stringResource(R.string.undo)) }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (bioAvailable) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.bio_toggle_title))
                            Text(stringResource(R.string.bio_toggle_sub),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = bioOn, onCheckedChange = onToggleBio)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.dns_toggle_title))
                        Text(stringResource(R.string.dns_toggle_sub),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = dnsOn, onCheckedChange = onToggleDns)
                }
                Text(stringResource(R.string.icon_label), Modifier.padding(top = 8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconModeChip(stringResource(R.string.icon_normal),
                        iconMode == AppDisguise.Mode.NORMAL) {
                        onSetIconMode(AppDisguise.Mode.NORMAL)
                    }
                    IconModeChip(stringResource(R.string.icon_calculator),
                        iconMode == AppDisguise.Mode.DISGUISE) {
                        onSetIconMode(AppDisguise.Mode.DISGUISE)
                    }
                    IconModeChip(stringResource(R.string.icon_hidden),
                        iconMode == AppDisguise.Mode.HIDDEN) {
                        onSetIconMode(AppDisguise.Mode.HIDDEN)
                    }
                }
                if (iconMode == AppDisguise.Mode.HIDDEN) {
                    Text(stringResource(R.string.icon_hidden_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = LockedColor)
                }
                Spacer(Modifier.height(8.dp))
                ChangePin(pinStore)
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.recovery_code_label, pinStore.recoveryCode),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.recovery_code_note),
                    style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.device_id_display, pinStore.deviceId),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.device_id_note),
                    style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.about), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.about_developer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                val ctx = LocalContext.current
                TextButton(
                    contentPadding = PaddingValues(0.dp),
                    onClick = {
                        runCatching {
                            ctx.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/jsr1611"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                ) { Text(stringResource(R.string.telegram_handle)) }
                TextButton(
                    contentPadding = PaddingValues(0.dp),
                    onClick = {
                        runCatching {
                            ctx.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/mydailyITblog"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                ) { Text(stringResource(R.string.telegram_channel)) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatusPill(ok: Boolean, okText: String, badText: String) {
    val c = if (ok) GoodColor else WarnColor
    Surface(
        color = c.copy(alpha = 0.15f),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Text(
            (if (ok) "✓ " else "⚠ ") + if (ok) okText else badText,
            style = MaterialTheme.typography.labelMedium,
            color = c,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun SetupStep(done: Boolean, label: String, onOpen: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (done) "✓" else "○",
            color = if (done) GoodColor else WarnColor,
            modifier = Modifier.width(22.dp))
        Text(label, Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = if (done) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface)
        if (onOpen != null) {
            TextButton(onClick = onOpen, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(stringResource(R.string.open_step))
            }
        }
    }
}

@Composable
private fun LanguagePickerDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val currentTag = LocaleHelper.current(ctx)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        title = { Text("🌐  " + stringResource(R.string.language)) },
        text = {
            Column {
                APP_LANGUAGES.forEach { lang ->
                    val selected = lang.tag == currentTag
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .clickable {
                                if (lang.tag != currentTag) {
                                    LocaleHelper.set(ctx, lang.tag)
                                    (ctx as? android.app.Activity)?.recreate()
                                }
                                onDismiss()
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(lang.flag, style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(lang.own, style = MaterialTheme.typography.bodyLarge)
                            Text(lang.english, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (selected) {
                            Text("✓", color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun AppRow(app: AppInfo, locked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val bmp = remember(app.pkg) { app.icon?.toBitmap(96, 96)?.asImageBitmap() }
        if (bmp != null) {
            Image(bmp, contentDescription = null,
                modifier = Modifier.size(38.dp).clip(MaterialTheme.shapes.small))
        } else {
            Box(Modifier.size(38.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(
                    if (locked) R.string.opens_with_password else R.string.opens_freely),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (locked) {
            Surface(color = LockedColor.copy(alpha = 0.15f),
                shape = MaterialTheme.shapes.extraLarge) {
                Text(stringResource(R.string.locked_chip),
                    style = MaterialTheme.typography.labelSmall, color = LockedColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
            }
            Spacer(Modifier.width(8.dp))
        }
        Switch(checked = locked, onCheckedChange = onToggle)
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
            label = { Text(stringResource(
                if (saved) R.string.password_saved else R.string.new_password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = { if (newPin.length >= 4) { pinStore.setPin(newPin); newPin = ""; saved = true } },
            enabled = newPin.length >= 4
        ) { Text(stringResource(R.string.set)) }
    }
}

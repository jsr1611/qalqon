package uz.jumanazar.qalqon.lock

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import uz.jumanazar.qalqon.MainActivity
import uz.jumanazar.qalqon.PinStore

/**
 * Watches which app comes to the foreground. When a protected app appears and is
 * not currently unlocked, it launches the full-screen PIN gate over it. Going to
 * the home screen re-locks it immediately; a short detour to another app (camera,
 * gallery, a link) keeps it unlocked for [LockState.GRACE_MS]. Overlays such as
 * the keyboard, system UI and permission dialogs are ignored entirely.
 */
class LockAccessibilityService : AccessibilityService() {

    private lateinit var protectedApps: ProtectedAppsStore
    private lateinit var pinStore: PinStore
    private var lastForeground: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        protectedApps = ProtectedAppsStore(this)
        pinStore = PinStore(this)
        LockState.clearAll()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return

        // Master pause: parent temporarily freed the phone — gate nothing.
        if (protectedApps.paused) return

        // Ignore our own windows (the PIN gate and the dashboard).
        if (pkg == packageName) return
        // Ignore overlays that sit on top of the current app without replacing it:
        // system UI, the keyboard (typing in a browser address bar fires these),
        // permission dialogs, the share/"open with" chooser.
        if (isTransientOverlay(pkg)) return

        val previous = lastForeground
        if (previous != null && previous != pkg && protectedApps.requiresPin(previous)) {
            if (isHomeLauncher(pkg) || previous in ProtectedAppsStore.INSTALLER_PACKAGES) {
                // Went back to the home screen, or finished an uninstall -> lock right away.
                LockState.relock(previous)
            } else {
                // Short detour (camera, gallery, link to another app...) -> grace period.
                LockState.markLeft(previous)
            }
        }
        lastForeground = pkg

        if (protectedApps.requiresPin(pkg)) {
            if (LockState.isUnlocked(pkg)) {
                LockState.markReturned(pkg)
            } else {
                showLock(pkg)
            }
        }
    }

    private fun isTransientOverlay(pkg: String): Boolean {
        if (pkg in TRANSIENT_PACKAGES) return true
        return pkg in inputMethodPackages()
    }

    private fun inputMethodPackages(): Set<String> = runCatching {
        val imm = getSystemService(InputMethodManager::class.java)
        imm.enabledInputMethodList.map { it.packageName }.toSet()
    }.getOrDefault(emptySet())

    private fun isHomeLauncher(pkg: String): Boolean = runCatching {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        packageManager.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
            .any { it.activityInfo.packageName == pkg }
    }.getOrDefault(false)

    private fun showLock(pkg: String) {
        val intent = Intent(this, LockActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
            putExtra(LockActivity.EXTRA_PACKAGE, pkg)
        }
        startActivity(intent)
    }

    // --- Secret reopen combo: Volume Up pressed 5x within 3 seconds ---
    private val volumeUpTimes = ArrayDeque<Long>()

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event != null &&
            event.action == KeyEvent.ACTION_DOWN &&
            event.keyCode == KeyEvent.KEYCODE_VOLUME_UP
        ) {
            val now = System.currentTimeMillis()
            volumeUpTimes.addLast(now)
            while (volumeUpTimes.isNotEmpty() && now - volumeUpTimes.first() > WINDOW_MS) {
                volumeUpTimes.removeFirst()
            }
            if (volumeUpTimes.size >= REQUIRED_PRESSES) {
                volumeUpTimes.clear()
                openDashboard()
            }
        }
        return super.onKeyEvent(event) // never consume; volume keeps working
    }

    private fun openDashboard() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    override fun onInterrupt() { /* no-op */ }

    companion object {
        @Volatile private var instance: LockAccessibilityService? = null

        /** System Home press (also exits split screen). False if the service isn't running. */
        fun pressHome(): Boolean =
            instance?.performGlobalAction(GLOBAL_ACTION_HOME) ?: false

        private const val REQUIRED_PRESSES = 5
        private const val WINDOW_MS = 3000L

        private val TRANSIENT_PACKAGES = setOf(
            "com.android.systemui",
            "android", // system chooser / dialogs
            "com.android.intentresolver",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
        )
    }
}

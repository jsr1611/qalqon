package uz.jumanazar.qalqon.lock

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import uz.jumanazar.qalqon.PinStore

/**
 * Watches which app comes to the foreground. When a protected app appears and is
 * not currently unlocked, it launches the full-screen PIN gate over it. Leaving a
 * protected app re-locks it, so returning requires the PIN again.
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
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return

        // Ignore our own windows (the PIN gate and the dashboard).
        if (pkg == packageName) return
        // Ignore system UI transitions (status bar, recents overlay, etc.).
        if (pkg == "com.android.systemui") return

        // Left a protected app -> re-lock it so re-entry needs the PIN again.
        val previous = lastForeground
        if (previous != null && previous != pkg && protectedApps.isProtected(previous)) {
            LockState.relock(previous)
        }
        lastForeground = pkg

        if (protectedApps.isProtected(pkg) && !LockState.isUnlocked(pkg)) {
            if (!pinStore.isConfigured) {
                // No PIN set yet -> default PIN path still gates via LockActivity.
            }
            showLock(pkg)
        }
    }

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

    override fun onInterrupt() { /* no-op */ }
}

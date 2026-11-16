package uz.jumanazar.qalqon.lock

import android.content.Context

/** Persisted set of package names that require the parent PIN to open. */
class ProtectedAppsStore(context: Context) {

    private val prefs =
        context.getSharedPreferences("qalqon_protected", Context.MODE_PRIVATE)

    fun all(): Set<String> = prefs.getStringSet(KEY, defaults())!!.toSet()

    fun isProtected(pkg: String): Boolean = all().contains(pkg)

    /**
     * Master pause. While paused, the service lets every app open freely without
     * touching the per-app list, so resuming restores the exact same protected set.
     */
    var paused: Boolean
        get() = prefs.getBoolean(KEY_PAUSED, false)
        set(value) { prefs.edit().putBoolean(KEY_PAUSED, value).apply() }

    /** The recommended starter set — one-tap "Lock recommended apps". */
    fun recommended(): Set<String> = defaults()

    fun lockAll(pkgs: Collection<String>) {
        prefs.edit().putStringSet(KEY, (all() + pkgs).toMutableSet()).apply()
    }

    fun unlockAll() {
        prefs.edit().putStringSet(KEY, mutableSetOf()).apply()
    }

    fun setProtected(pkg: String, protected: Boolean) {
        val next = all().toMutableSet()
        if (protected) next.add(pkg) else next.remove(pkg)
        prefs.edit().putStringSet(KEY, next).apply()
    }

    private fun defaults(): MutableSet<String> = mutableSetOf(
        "com.android.settings",              // Settings (blocks Accessibility/uninstall paths)
        "com.roblox.client",                 // Roblox
        "com.google.android.youtube",        // YouTube
        "com.android.chrome",                // Chrome
        "com.sec.android.app.sbrowser",      // Samsung Internet
        "com.android.vending",               // Play Store
        "com.sec.android.app.samsungapps"    // Galaxy Store
    )

    companion object {
        private const val KEY = "protected_packages"
        private const val KEY_PAUSED = "master_paused"
    }
}

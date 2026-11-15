package uz.jumanazar.qalqon.lock

import android.content.Context

/** Persisted set of package names that require the parent PIN to open. */
class ProtectedAppsStore(context: Context) {

    private val prefs =
        context.getSharedPreferences("qalqon_protected", Context.MODE_PRIVATE)

    fun all(): Set<String> = prefs.getStringSet(KEY, defaults())!!.toSet()

    fun isProtected(pkg: String): Boolean = all().contains(pkg)

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
    }
}

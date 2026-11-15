package uz.jumanazar.qalqon.vpn

import android.content.Context

/** Remembers whether the parent turned the optional DNS filter on. */
class DnsPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("qalqon_dns", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY, false)
        set(value) { prefs.edit().putBoolean(KEY, value).apply() }

    companion object {
        private const val KEY = "dns_enabled"
    }
}

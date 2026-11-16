package uz.jumanazar.qalqon

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Per-app language, applied by wrapping each activity's base context. Works on every
 * supported API level (no AppCompat needed). Defaults to Uzbek until the parent picks
 * another language in Settings.
 */
object LocaleHelper {
    const val DEFAULT = "uz"
    private const val PREFS = "qalqon_lang"
    private const val KEY = "lang_tag"

    fun current(ctx: Context): String =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, DEFAULT) ?: DEFAULT

    fun set(ctx: Context, tag: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tag).apply()
    }

    /** Returns [base] reconfigured to the chosen language (and layout direction). */
    fun wrap(base: Context): Context {
        val locale = Locale.forLanguageTag(current(base))
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale) // RTL for Arabic
        return base.createConfigurationContext(config)
    }
}

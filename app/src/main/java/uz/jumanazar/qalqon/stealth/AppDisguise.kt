package uz.jumanazar.qalqon.stealth

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Controls how the app appears on the home screen via two launcher aliases:
 *   NORMAL    -> ".Launcher" (shield / "Aqlli Qalqon")
 *   DISGUISE  -> ".LauncherDisguise" (calculator / "Calculator")
 *   HIDDEN    -> neither alias enabled; no icon at all.
 *
 * In HIDDEN mode there is no icon, so the app is reopened with the secret combo
 * (Volume Up x5) handled by LockAccessibilityService. We always enable the target
 * before disabling the others, so NORMAL/DISGUISE never leave zero icons.
 */
object AppDisguise {

    enum class Mode { NORMAL, DISGUISE, HIDDEN }

    private const val REAL = "uz.jumanazar.qalqon.Launcher"
    private const val DECOY = "uz.jumanazar.qalqon.LauncherDisguise"

    fun setMode(ctx: Context, mode: Mode) {
        when (mode) {
            Mode.NORMAL -> {
                enable(ctx, REAL)
                disable(ctx, DECOY)
            }
            Mode.DISGUISE -> {
                enable(ctx, DECOY)
                disable(ctx, REAL)
            }
            Mode.HIDDEN -> {
                disable(ctx, REAL)
                disable(ctx, DECOY)
            }
        }
    }

    fun currentMode(ctx: Context): Mode {
        val decoyOn = isEnabled(ctx, DECOY)
        val realOn = isEnabled(ctx, REAL)
        return when {
            decoyOn -> Mode.DISGUISE
            realOn -> Mode.NORMAL
            else -> Mode.HIDDEN
        }
    }

    private fun enable(ctx: Context, name: String) =
        ctx.packageManager.setComponentEnabledSetting(
            ComponentName(ctx.packageName, name),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )

    private fun disable(ctx: Context, name: String) =
        ctx.packageManager.setComponentEnabledSetting(
            ComponentName(ctx.packageName, name),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )

    /** DEFAULT counts as enabled only for ".Launcher" (manifest enabled=true). */
    private fun isEnabled(ctx: Context, name: String): Boolean {
        val state = ctx.packageManager.getComponentEnabledSetting(
            ComponentName(ctx.packageName, name)
        )
        return when (state) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> name == REAL
            else -> false
        }
    }
}

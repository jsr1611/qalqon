package uz.jumanazar.qalqon.stealth

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Switches the launcher icon between the real one (".Launcher", shield / "Aqlli
 * Qalqon") and a decoy (".LauncherDisguise", calculator / "Calculator").
 *
 * EXACTLY ONE alias is ever enabled, and we always enable the target before
 * disabling the other — so there is always a working icon on the home screen and
 * the user can never be locked out. The app toggles its OWN components, which is
 * always permitted (unlike `adb pm enable`, which the OEM blocks).
 */
object AppDisguise {

    private const val REAL = "uz.jumanazar.qalqon.Launcher"
    private const val DECOY = "uz.jumanazar.qalqon.LauncherDisguise"

    fun setDisguised(ctx: Context, disguised: Boolean) {
        val pm = ctx.packageManager
        val enable = if (disguised) DECOY else REAL
        val disable = if (disguised) REAL else DECOY
        // Enable the one we want FIRST, so an icon always exists.
        pm.setComponentEnabledSetting(
            ComponentName(ctx.packageName, enable),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
        pm.setComponentEnabledSetting(
            ComponentName(ctx.packageName, disable),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    fun isDisguised(ctx: Context): Boolean {
        val state = ctx.packageManager.getComponentEnabledSetting(
            ComponentName(ctx.packageName, DECOY)
        )
        return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    }
}

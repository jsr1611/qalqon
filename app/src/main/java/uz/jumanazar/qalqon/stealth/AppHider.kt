package uz.jumanazar.qalqon.stealth

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Hides/shows the launcher icon by toggling the ".Launcher" activity-alias.
 * While hidden, the app is opened by dialing the secret code (see SecretCodeReceiver).
 */
object AppHider {

    const val SECRET_CODE = "725766" // dial *#*#725766#*#*  (7-2-5-7-6-6 = "QALQON")

    private fun alias(ctx: Context) =
        ComponentName(ctx.packageName, "uz.jumanazar.qalqon.Launcher")

    fun setHidden(ctx: Context, hidden: Boolean) {
        val state = if (hidden)
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        else
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        ctx.packageManager.setComponentEnabledSetting(
            alias(ctx), state, PackageManager.DONT_KILL_APP
        )
    }

    fun isHidden(ctx: Context): Boolean {
        return ctx.packageManager.getComponentEnabledSetting(alias(ctx)) ==
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }
}

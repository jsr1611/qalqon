package uz.jumanazar.qalqon.policy

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.UserManager
import android.util.Log
import uz.jumanazar.qalqon.admin.KidGuardAdminReceiver

/**
 * Thin wrapper over DevicePolicyManager. Every call is guarded so the dashboard
 * degrades gracefully when the app is only a device-admin (not full device-owner),
 * e.g. during development before `dpm set-device-owner` has been run.
 */
class PolicyManager(private val context: Context) {

    private val dpm =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private val admin: ComponentName = KidGuardAdminReceiver.component(context)

    val isDeviceOwner: Boolean
        get() = dpm.isDeviceOwnerApp(context.packageName)

    val isAdminActive: Boolean
        get() = dpm.isAdminActive(admin)

    /** Packages the child must not be able to open. */
    val suspendedPackages = arrayOf(
        "com.roblox.client",                 // Roblox
        "com.google.android.youtube",        // YouTube
        "com.android.vending",               // Google Play Store
        "com.sec.android.app.samsungapps",   // Samsung Galaxy Store
        "com.samsung.knox.securefolder"      // Secure Folder loophole
    )

    /** Apply the full policy set. Called from onEnabled and from the dashboard. */
    fun applyBaselinePolicies() {
        if (!isDeviceOwner) {
            Log.w(TAG, "Not device owner; skipping owner-only policies.")
            return
        }
        setAppsSuspended(true)
        setUserRestriction(UserManager.DISALLOW_INSTALL_APPS, true)
        setUserRestriction(UserManager.DISALLOW_UNINSTALL_APPS, true)
        setUserRestriction(UserManager.DISALLOW_SAFE_BOOT, true)
        setUserRestriction(UserManager.DISALLOW_ADD_USER, true)
        setUserRestriction(UserManager.DISALLOW_FACTORY_RESET, true)
        setUserRestriction(UserManager.DISALLOW_DEBUGGING_FEATURES, true)
        setUninstallBlocked(true)
    }

    fun setAppsSuspended(suspended: Boolean) {
        if (!isDeviceOwner) return
        try {
            dpm.setPackagesSuspended(admin, suspendedPackages, suspended)
        } catch (e: Exception) {
            Log.e(TAG, "setPackagesSuspended failed", e)
        }
    }

    fun setUninstallBlocked(blocked: Boolean) {
        if (!isDeviceOwner) return
        try {
            dpm.setUninstallBlocked(admin, context.packageName, blocked)
        } catch (e: Exception) {
            Log.e(TAG, "setUninstallBlocked failed", e)
        }
    }

    /** Toggle ADB / developer features (parent re-enables this to push updates). */
    fun setDebuggingDisabled(disabled: Boolean) {
        setUserRestriction(UserManager.DISALLOW_DEBUGGING_FEATURES, disabled)
    }

    fun setAlwaysOnVpn(vpnPackage: String?) {
        if (!isDeviceOwner) return
        try {
            // lockdown = true: no traffic may leave while the VPN is down.
            dpm.setAlwaysOnVpnPackage(admin, vpnPackage, /* lockdownEnabled = */ true)
        } catch (e: Exception) {
            Log.e(TAG, "setAlwaysOnVpnPackage failed", e)
        }
    }

    /** Full removal path, exposed behind the parent PIN. */
    fun releaseDevice() {
        setAppsSuspended(false)
        setUninstallBlocked(false)
        listOf(
            UserManager.DISALLOW_INSTALL_APPS,
            UserManager.DISALLOW_UNINSTALL_APPS,
            UserManager.DISALLOW_SAFE_BOOT,
            UserManager.DISALLOW_ADD_USER,
            UserManager.DISALLOW_FACTORY_RESET,
            UserManager.DISALLOW_DEBUGGING_FEATURES
        ).forEach { setUserRestriction(it, false) }
        setAlwaysOnVpn(null)
        if (isDeviceOwner) {
            try {
                dpm.clearDeviceOwnerApp(context.packageName)
            } catch (e: Exception) {
                Log.e(TAG, "clearDeviceOwnerApp failed", e)
            }
        }
    }

    private fun setUserRestriction(key: String, enable: Boolean) {
        if (!isDeviceOwner) return
        try {
            if (enable) dpm.addUserRestriction(admin, key)
            else dpm.clearUserRestriction(admin, key)
        } catch (e: Exception) {
            Log.e(TAG, "userRestriction $key failed", e)
        }
    }

    companion object {
        private const val TAG = "QalqonPolicy"
    }
}

package uz.jumanazar.qalqon.admin

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import uz.jumanazar.qalqon.policy.PolicyManager
import uz.jumanazar.qalqon.vpn.DnsFilterVpnService

class QalqonAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Device admin enabled; applying baseline policies.")
        val policy = PolicyManager(context)
        policy.applyBaselinePolicies()
        // Pin the always-on, locked-down VPN to our own package.
        policy.setAlwaysOnVpn(context.packageName)
        DnsFilterVpnService.start(context)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.i(TAG, "Device admin disabled.")
    }

    companion object {
        private const val TAG = "QalqonAdmin"
        fun component(context: Context) =
            ComponentName(context, QalqonAdminReceiver::class.java)
    }
}

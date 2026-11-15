package uz.jumanazar.qalqon.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import uz.jumanazar.qalqon.vpn.DnsFilterVpnService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            DnsFilterVpnService.start(context)
        }
    }
}

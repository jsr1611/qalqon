package uz.jumanazar.qalqon.stealth

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import uz.jumanazar.qalqon.MainActivity

/**
 * Opens the hidden dashboard when the parent dials *#*#725766#*#* in the phone app.
 * The dialer fires an android.provider.Telephony.SECRET_CODE broadcast whose host is
 * the dialed code.
 */
class SecretCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val host = intent.data?.host ?: return
        if (host == AppHider.SECRET_CODE) {
            val open = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(open)
        }
    }
}

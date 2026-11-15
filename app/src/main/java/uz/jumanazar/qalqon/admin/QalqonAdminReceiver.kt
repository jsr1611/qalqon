package uz.jumanazar.qalqon.admin

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context

/**
 * Plain device-admin (NOT device-owner). Being an active device admin means the
 * app can't be uninstalled from the launcher in one tap — the user must first
 * deactivate admin in Settings, which adds enough friction to stop a child.
 */
class QalqonAdminReceiver : DeviceAdminReceiver() {
    companion object {
        fun component(context: Context) =
            ComponentName(context, QalqonAdminReceiver::class.java)
    }
}

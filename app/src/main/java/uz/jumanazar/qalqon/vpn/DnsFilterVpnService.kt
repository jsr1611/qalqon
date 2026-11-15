package uz.jumanazar.qalqon.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.concurrent.thread

/**
 * Local, server-less DNS filter. Opens a TUN interface, captures outbound UDP/53
 * packets, answers blocked names with NXDOMAIN, and forwards everything else to an
 * upstream resolver through a protected socket (so forwarded queries skip the TUN).
 *
 * This is a minimal packet pipeline: IPv4 + UDP + single-question DNS. That covers
 * the standard resolver behaviour on the target Samsung firmware.
 */
class DnsFilterVpnService : VpnService() {

    @Volatile private var running = false
    private var tun: ParcelFileDescriptor? = null
    private var worker: Thread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (running) return START_STICKY
        startForeground(NOTIF_ID, buildNotification())
        establish()
        return START_STICKY
    }

    private fun establish() {
        val builder = Builder()
            .setSession("Qalqon DNS")
            .addAddress(TUN_ADDRESS, 32)
            .addDnsServer(TUN_DNS)
            .addRoute(TUN_DNS, 32)   // only DNS traffic is routed through us
            .setBlocking(true)
        try {
            builder.addDisallowedApplication(packageName)
        } catch (_: Exception) { /* self already excluded by default */ }

        val descriptor = builder.establish() ?: run {
            Log.e(TAG, "Failed to establish VPN interface")
            return
        }
        tun = descriptor
        running = true
        worker = thread(name = "qalqon-dns") { pump(descriptor) }
    }

    private fun pump(descriptor: ParcelFileDescriptor) {
        val input = FileInputStream(descriptor.fileDescriptor)
        val output = FileOutputStream(descriptor.fileDescriptor)
        val upstream = DatagramSocket().also { protect(it) }
        val buffer = ByteArray(32767)

        while (running) {
            val length = try {
                input.read(buffer)
            } catch (e: Exception) {
                if (running) Log.e(TAG, "read error", e); break
            }
            if (length <= 0) continue
            try {
                handlePacket(buffer, length, output, upstream)
            } catch (e: Exception) {
                Log.w(TAG, "packet drop: ${e.message}")
            }
        }
        upstream.close()
    }

    private fun handlePacket(
        packet: ByteArray,
        length: Int,
        tunOut: FileOutputStream,
        upstream: DatagramSocket
    ) {
        if (length < 28) return
        if ((packet[0].toInt() ushr 4 and 0xF) != 4) return   // IPv4 only
        val ihl = (packet[0].toInt() and 0xF) * 4
        if (packet[9].toInt() and 0xFF != 17) return            // UDP only

        val udpStart = ihl
        val dstPort = ((packet[udpStart + 2].toInt() and 0xFF) shl 8) or
            (packet[udpStart + 3].toInt() and 0xFF)
        if (dstPort != 53) return

        val dnsStart = udpStart + 8
        val host = DnsPacket.parseQuestionName(packet, dnsStart, length) ?: return

        if (Blocklist.isBlocked(host)) {
            Log.i(TAG, "BLOCK $host")
            val response = DnsPacket.buildNxDomain(packet, ihl, udpStart, dnsStart, length)
            if (response != null) tunOut.write(response)
            return
        }

        // Forward to the real resolver, relay the answer back into the TUN.
        val dnsLen = length - dnsStart
        val query = packet.copyOfRange(dnsStart, dnsStart + dnsLen)
        val reply = ByteArray(4096)
        upstream.send(
            DatagramPacket(query, dnsLen, InetAddress.getByName(UPSTREAM_DNS), 53)
        )
        val replyPacket = DatagramPacket(reply, reply.size)
        upstream.soTimeout = 5000
        upstream.receive(replyPacket)

        val forged = DnsPacket.wrapResponse(
            packet, ihl, udpStart, dnsStart, reply, replyPacket.length
        )
        if (forged != null) tunOut.write(forged)
    }

    private fun buildNotification(): Notification {
        val mgr = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID, "Qalqon protection",
            NotificationManager.IMPORTANCE_LOW
        )
        mgr.createNotificationChannel(channel)
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Qalqon is active")
            .setContentText("Content filtering is on.")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        running = false
        worker?.interrupt()
        try { tun?.close() } catch (_: Exception) {}
        super.onDestroy()
    }

    companion object {
        private const val TAG = "QalqonVpn"
        private const val CHANNEL_ID = "qalqon_vpn"
        private const val NOTIF_ID = 1001
        private const val TUN_ADDRESS = "10.0.0.2"
        private const val TUN_DNS = "10.0.0.1"
        private const val UPSTREAM_DNS = "1.1.1.1"

        const val ACTION_STOP = "uz.jumanazar.qalqon.STOP_DNS"

        /** Call only after VpnService.prepare() has returned null (consent granted). */
        fun start(context: Context) {
            val intent = Intent(context, DnsFilterVpnService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, DnsFilterVpnService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}

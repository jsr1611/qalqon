package uz.jumanazar.qalqon.vpn

/**
 * Minimal IPv4/UDP/DNS surgery helpers. All offsets are into the original
 * outbound packet captured from the TUN interface.
 */
object DnsPacket {

    /** Read the QNAME of the first question as a dotted host string. */
    fun parseQuestionName(packet: ByteArray, dnsStart: Int, length: Int): String? {
        var pos = dnsStart + 12 // skip 12-byte DNS header
        if (pos >= length) return null
        val sb = StringBuilder()
        while (pos < length) {
            val len = packet[pos].toInt() and 0xFF
            if (len == 0) break
            if (len and 0xC0 != 0) return null // compression not expected in a query
            pos++
            if (pos + len > length) return null
            if (sb.isNotEmpty()) sb.append('.')
            for (i in 0 until len) sb.append((packet[pos + i].toInt() and 0xFF).toChar())
            pos += len
        }
        return if (sb.isEmpty()) null else sb.toString()
    }

    /** Build a full IPv4/UDP DNS response that echoes the query with RCODE=3 (NXDOMAIN). */
    fun buildNxDomain(
        req: ByteArray, ihl: Int, udpStart: Int, dnsStart: Int, length: Int
    ): ByteArray? {
        val dnsLen = length - dnsStart
        val dns = req.copyOfRange(dnsStart, dnsStart + dnsLen)
        // Flags: QR=1, Opcode copied, AA=0, RD copied, RA=1, RCODE=3
        dns[2] = (0x80 or (dns[2].toInt() and 0x01)).toByte() // QR + RD bit
        dns[3] = (0x80 or 0x03).toByte()                      // RA + NXDOMAIN
        // zero answer/authority/additional counts
        dns[6] = 0; dns[7] = 0; dns[8] = 0; dns[9] = 0; dns[10] = 0; dns[11] = 0
        return assemble(req, ihl, udpStart, dns)
    }

    /** Wrap an upstream DNS reply payload back into an IPv4/UDP packet toward the client. */
    fun wrapResponse(
        req: ByteArray, ihl: Int, udpStart: Int, dnsStart: Int,
        reply: ByteArray, replyLen: Int
    ): ByteArray? {
        val dns = reply.copyOfRange(0, replyLen)
        return assemble(req, ihl, udpStart, dns)
    }

    /** Swap src/dst, rebuild lengths and checksums around the given DNS payload. */
    private fun assemble(
        req: ByteArray, ihl: Int, udpStart: Int, dns: ByteArray
    ): ByteArray {
        val udpLen = 8 + dns.size
        val totalLen = ihl + udpLen
        val out = ByteArray(totalLen)

        // ---- IPv4 header (copy then rewrite) ----
        System.arraycopy(req, 0, out, 0, ihl)
        out[2] = (totalLen ushr 8).toByte()
        out[3] = (totalLen and 0xFF).toByte()
        out[8] = 64 // TTL
        // swap src/dst addresses (bytes 12..15 and 16..19)
        for (i in 0 until 4) {
            out[12 + i] = req[16 + i]
            out[16 + i] = req[12 + i]
        }
        out[10] = 0; out[11] = 0
        val ipChecksum = checksum(out, 0, ihl)
        out[10] = (ipChecksum ushr 8).toByte()
        out[11] = (ipChecksum and 0xFF).toByte()

        // ---- UDP header (swap ports) ----
        val u = ihl
        out[u] = req[udpStart + 2]; out[u + 1] = req[udpStart + 3]     // src = old dst (53)
        out[u + 2] = req[udpStart]; out[u + 3] = req[udpStart + 1]     // dst = old src
        out[u + 4] = (udpLen ushr 8).toByte()
        out[u + 5] = (udpLen and 0xFF).toByte()
        out[u + 6] = 0; out[u + 7] = 0 // checksum optional for IPv4; leave 0
        System.arraycopy(dns, 0, out, u + 8, dns.size)
        return out
    }

    private fun checksum(data: ByteArray, start: Int, len: Int): Int {
        var sum = 0
        var i = start
        val end = start + len
        while (i + 1 < end) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < end) sum += (data[i].toInt() and 0xFF) shl 8
        while (sum ushr 16 != 0) sum = (sum and 0xFFFF) + (sum ushr 16)
        return sum.inv() and 0xFFFF
    }
}

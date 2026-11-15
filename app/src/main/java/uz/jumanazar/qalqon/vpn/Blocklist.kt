package uz.jumanazar.qalqon.vpn

/** Domains (and any subdomain of them) that resolve to NXDOMAIN. */
object Blocklist {

    private val blockedSuffixes = listOf(
        // YouTube
        "youtube.com", "youtu.be", "googlevideo.com", "ytimg.com", "yt3.ggpht.com",
        // Roblox
        "roblox.com", "rbxcdn.com", "roblox.cn",
        // Browser-game portals
        "poki.com", "crazygames.com", "y8.com", "miniclip.com",
        "coolmathgames.com", "friv.com", "addictinggames.com"
    )

    /** True when [host] equals or is a subdomain of any blocked suffix. */
    fun isBlocked(host: String): Boolean {
        val h = host.trimEnd('.').lowercase()
        return blockedSuffixes.any { suffix ->
            h == suffix || h.endsWith(".$suffix")
        }
    }
}

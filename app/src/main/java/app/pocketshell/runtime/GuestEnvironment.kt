package app.pocketshell.runtime

import java.io.File

/**
 * Guest environment repair hooks that the pinned minirootfs does not ship but
 * a real package manager needs.
 *
 * DNS (device lessons, Samsung SM-F711B, 2026-09-01/09-02):
 * Alpine's musl resolver reads /etc/resolv.conf — the minirootfs ships none.
 * v0.4.0 wrote hardcoded public resolvers (1.1.1.1 / 8.8.8.8) and they WORKED
 * (the v0.4.0 fetch actually downloaded, then died at the SELinux linkat
 * commit — see RuntimeProcessLauncher for that fix).
 * v0.4.1–v0.4.2 switched to DEVICE-only resolvers (LinkProperties); on the
 * 2026-09-02 device screenshots that single resolver (172.20.10.1, a hotspot
 * gateway) timed out and every apk fetch died with "DNS: transient error".
 * A one-resolver file is a single point of failure.
 *
 * v0.4.3 therefore writes a COMBINED file, capped at musl's MAXNS=3:
 * device resolvers first (on-link, fastest), public fallbacks after. musl
 * queries all configured nameservers in parallel and takes the first answer,
 * so one dead resolver can no longer block resolution. The file carries a
 * `# managed by PocketShell` marker; every package operation refreshes
 * managed files to the CURRENT network's resolvers (routers change), and
 * never touches anything that is not ours (user comments/options/anything).
 *
 * Applied in two places:
 *  - RuntimeInstaller.configureAndWriteMetadata → fresh installs ship with
 *    working DNS from the start (public pair until the first package op
 *    refreshes it with the device's resolvers).
 *  - AlpinePackageManager before every operation → runtimes installed by
 *    v0.2.x–v0.4.2 (including the flaky device-only files) are upgraded in
 *    place, no reinstall needed.
 *
 * apk workspace (same device lesson, defensive half): the pinned minirootfs
 * already ships var/cache/apk (0755), but the on-device runtime may predate
 * layout fixes or live on a filesystem that mangled modes. apk 3.0.x opens
 * its cache dir (etc/apk/cache, falling back to var/cache/apk) and writes
 * APKINDEX.<hash>.tar.gz there; a non-writable cache fails every fetch with
 * "Permission denied". We guarantee the directories exist with sane modes,
 * and RuntimeProcessLauncher additionally binds app-owned host cache dirs
 * over the guest cache paths so rootfs-internal permissions can never block
 * apk again.
 */
object GuestEnvironment {

    /**
     * Legacy v0.4.0 public-only constant, kept byte-identical so the upgrade
     * rule below still recognizes files written by that version. v0.4.3+ uses
     * [resolvConfContent] (marker + device resolvers + public fallbacks).
     */
    const val RESOLV_CONF_CONTENT = "nameserver 1.1.1.1\nnameserver 8.8.8.8\n"

    /**
     * Marks resolv.conf files this app owns. A file starting with this line
     * is refreshed on every package operation; anything else is user content
     * and is never touched (except the exact legacy shapes we wrote — see
     * [managedByUs]).
     */
    const val RESOLV_CONF_MARKER =
        "# managed by PocketShell — device resolvers first, public fallback after"

    /** Conservative public fallbacks, appended after the device resolvers. */
    val PUBLIC_RESOLVER_FALLBACKS = listOf("1.1.1.1", "8.8.8.8")

    /** musl reads at most MAXNS=3 nameservers and queries them in parallel. */
    const val MUSL_MAXNS = 3

    const val RESOLV_CONF_RELATIVE = "etc/resolv.conf"

    /** apk cache locations inside the guest (apk-tools 3 default + fallback). */
    const val APK_CACHE_ETC_RELATIVE = "etc/apk/cache"
    const val APK_CACHE_VAR_RELATIVE = "var/cache/apk"
    const val APK_TMP_RELATIVE = "tmp"

    /**
     * Guest-relative app-identity stamp (m6.0.2 device-gate lesson), rewritten
     * best-effort on EVERY session spawn: "<versionName> (versionCode N)".
     * The on-device suite PREFLIGHT reads it to prove WHICH app build owns
     * this rootfs — "the layer should have installed" claims become checkable
     * from inside the guest, where "app too old" and "install failed" were
     * previously indistinguishable (the vc40/vc41 gate lesson).
     */
    const val APP_VERSION_RELATIVE = "etc/pocketshell/app-version"

    /**
     * A nameserver entry musl can actually parse and use: a numeric IPv4/
     * IPv6 literal, no zone suffix ("fe80::1%wlan0" is LinkProperties scope
     * syntax — inet_pton rejects it, so the line would be dead weight), no
     * hostnames (a hostname in resolv.conf is resolved... by DNS).
     */
    internal fun resolvConfUsable(server: String): Boolean =
        server.isNotBlank() && !server.contains('%') && server.all {
            it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == '.' || it == ':'
        }

    /**
     * Legacy bare-list server token: a numeric IPv4/IPv6 literal, optionally
     * with a zone suffix — exactly what the v0.4.0–v0.4.2 writers produced
     * (including the `%wlan0` form LinkProperties handed them). Hostnames
     * ("dns.my.lan") do not match.
     */
    private val LEGACY_SERVER = Regex("^[0-9a-fA-F:.]+(%[0-9A-Za-z0-9_.\\-]+)?$")

    /**
     * True when [content] is a file PocketShell wrote: marked, the exact
     * legacy v0.4.0 constant, or a bare `nameserver <literal>` list (every
     * version v0.4.0–v0.4.2 wrote exactly that shape — including the
     * zone-suffixed link-locals — and the minirootfs ships no resolv.conf of
     * its own). Anything with comments, options, search lines or hostnames
     * is user content and is never touched.
     */
    internal fun managedByUs(content: String): Boolean {
        if (content.startsWith(RESOLV_CONF_MARKER)) return true
        if (content == RESOLV_CONF_CONTENT) return true
        val lines = content.lines().filter { it.isNotBlank() }
        return lines.isNotEmpty() && lines.all { line ->
            val parts = line.trim().split(Regex("\\s+"))
            parts.size == 2 && parts[0] == "nameserver" && LEGACY_SERVER.matches(parts[1])
        }
    }

    /**
     * Build the resolv.conf body: usable device resolvers first, public
     * fallbacks after, capped at musl's MAXNS=3. musl queries ALL of them in
     * parallel and takes the first answer — one dead resolver (a flaky
     * hotspot gateway, an unreachable IPv6) can no longer block resolution.
     */
    fun resolvConfContent(dnsServers: List<String>): String {
        val servers = (dnsServers.filter { resolvConfUsable(it) } + PUBLIC_RESOLVER_FALLBACKS)
            .distinct()
            .take(MUSL_MAXNS)
        return buildString {
            appendLine(RESOLV_CONF_MARKER)
            for (server in servers) appendLine("nameserver $server")
        }
    }

    /**
     * Ensure [rootfsDir]/etc/resolv.conf exists with usable nameservers.
     * [dnsServers] are the DEVICE's own resolvers (caller-derived, e.g. from
     * ConnectivityManager); public fallbacks are always appended (musl
     * parallel query — see [resolvConfContent]). Files PocketShell wrote
     * ([managedByUs]) are refreshed to the CURRENT network's resolvers on
     * every call — a resolv.conf pointing at yesterday's hotspot gateway is
     * a guaranteed failure tomorrow. Anything NOT ours is never touched.
     * Returns true when the file exists afterwards. Never throws for a
     * read-only/busy rootfs — returns false and lets the caller report the
     * honest failure.
     */
    fun ensureDnsResolvers(rootfsDir: File, dnsServers: List<String> = emptyList()): Boolean {
        val file = File(rootfsDir, RESOLV_CONF_RELATIVE)
        val desired = resolvConfContent(dnsServers)
        return try {
            if (file.isFile) {
                val current = file.readText()
                if (current.isNotBlank()) {
                    if (managedByUs(current) && current != desired) {
                        file.writeText(desired)
                    }
                    return true
                }
            }
            file.parentFile?.mkdirs()
            file.writeText(desired)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Guarantee the apk working directories exist inside [rootfsDir] with
     * writable modes. Best-effort per path: a failure on one path does not
     * block the others (the proot cache binds below cover the cache paths
     * anyway; tmp is the one apk genuinely cannot work without).
     */
    fun ensureApkWorkspace(rootfsDir: File): Boolean = try {
        val results = listOf(
            repairDir(rootfsDir, APK_CACHE_ETC_RELATIVE, "rwxr-xr-x"),
            repairDir(rootfsDir, APK_CACHE_VAR_RELATIVE, "rwxr-xr-x"),
            repairDir(rootfsDir, APK_TMP_RELATIVE, "rwxrwxrwt"),
        )
        results.all { it }
    } catch (_: Exception) {
        false
    }

    private fun repairDir(rootfsDir: File, relative: String, permissions: String): Boolean = try {
        val dir = File(rootfsDir, relative)
        if (!dir.isDirectory) dir.mkdirs()
        if (dir.isDirectory) {
            applyMode(dir, permissions)
            true
        } else {
            false
        }
    } catch (_: Exception) {
        false
    }

    private fun applyMode(target: File, permissions: String) {
        try {
            val perms = java.util.HashSet<java.nio.file.attribute.PosixFilePermission>()
            val map = mapOf(
                0 to java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                1 to java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,
                2 to java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE,
                3 to java.nio.file.attribute.PosixFilePermission.GROUP_READ,
                4 to java.nio.file.attribute.PosixFilePermission.GROUP_WRITE,
                5 to java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE,
                6 to java.nio.file.attribute.PosixFilePermission.OTHERS_READ,
                7 to java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE,
                8 to java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE,
            )
            val triple = permissions.padEnd(9, '-')
            for ((index, permission) in map) {
                if (triple[index] != '-') perms.add(permission)
            }
            java.nio.file.Files.setPosixFilePermissions(target.toPath(), perms)
        } catch (_: Exception) {
            // Mode repair is best-effort on exotic filesystems; existence is
            // the load-bearing part (proot binds + app uid own the files).
        }
    }
}

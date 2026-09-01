package app.pocketshell.runtime

import java.io.File

/**
 * Guest environment repair hooks that the pinned minirootfs does not ship but
 * a real package manager needs.
 *
 * DNS (v0.4.1 device lesson, Samsung SM-F711B recording 2026-09-01 13:37):
 * Alpine's musl resolver reads /etc/resolv.conf — the minirootfs ships none.
 * v0.4.0 wrote hardcoded public resolvers (1.1.1.1 / 8.8.8.8); on the user's
 * network those are UNREACHABLE (port-53 egress blocked / strict Private DNS),
 * so every apk fetch died with "DNS: transient error" / raw EACCES while the
 * ANDROID side had perfect connectivity. The repair now prefers the device's
 * OWN resolvers (LinkProperties — on-link, actually working) passed in by the
 * caller, and only falls back to the public pair when the OS answer is empty.
 *
 * A resolv.conf that exactly equals the v0.4.0 public fallback is UPGRADED in
 * place to device-derived resolvers (we wrote it, we may replace it). Any
 * other non-blank content — Alpine's own or user-edited — is never touched.
 *
 * Applied in two places:
 *  - RuntimeInstaller.configureAndWriteMetadata → fresh installs ship with
 *    working DNS from the start.
 *  - AlpinePackageManager before every operation → runtimes installed by
 *    v0.2.x–v0.4.0 are repaired in place, no reinstall needed.
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
     * Conservative public fallback, used ONLY when the OS reports no usable
     * resolvers. Kept byte-identical to the v0.4.0 constant so the upgrade
     * rule below can recognize files we wrote ourselves.
     */
    const val RESOLV_CONF_CONTENT = "nameserver 1.1.1.1\nnameserver 8.8.8.8\n"

    const val RESOLV_CONF_RELATIVE = "etc/resolv.conf"

    /** apk cache locations inside the guest (apk-tools 3 default + fallback). */
    const val APK_CACHE_ETC_RELATIVE = "etc/apk/cache"
    const val APK_CACHE_VAR_RELATIVE = "var/cache/apk"
    const val APK_TMP_RELATIVE = "tmp"

    /** Build the resolv.conf body for [servers]; empty list → public fallback. */
    fun resolvConfContent(dnsServers: List<String>): String =
        if (dnsServers.isEmpty()) {
            RESOLV_CONF_CONTENT
        } else {
            dnsServers.joinToString(separator = "") { "nameserver $it\n" }
        }

    /**
     * Ensure [rootfsDir]/etc/resolv.conf exists with usable nameservers.
     * [dnsServers] are the DEVICE's own resolvers (caller-derived, e.g. from
     * ConnectivityManager); the public fallback is used only when that list is
     * empty. Returns true when the file exists afterwards. Never throws for a
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
                    // Upgrade ONLY the hardcoded v0.4.0 fallback we wrote —
                    // device-derived resolvers replace it when they exist.
                    // Anything else is the user's/Alpine's: never overwritten.
                    if (current == RESOLV_CONF_CONTENT && desired != RESOLV_CONF_CONTENT) {
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

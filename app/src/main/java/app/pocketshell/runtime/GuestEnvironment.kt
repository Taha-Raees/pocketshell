package app.pocketshell.runtime

import java.io.File

/**
 * Guest environment repair hooks that the pinned minirootfs does not ship but
 * a real package manager needs.
 *
 * DNS: Alpine's musl resolver reads /etc/resolv.conf — the minirootfs
 * intentionally ships none (upstream expects the target machine to configure
 * it). Inside proot there is no Android netd to fall back on, so without this
 * file every guest name lookup fails (`apk update` → "unable to resolve
 * host"). We write conservative public resolvers; the write is honest machine
 * configuration of the runtime WE installed, never a silent behavior change:
 * the file only appears when missing/blank and is never overwritten once the
 * user (or Alpine) has real content there.
 *
 * Applied in two places:
 *  - RuntimeInstaller.configureAndWriteMetadata → fresh installs ship with
 *    working DNS from the start.
 *  - AlpinePackageManager before every operation → runtimes installed by
 *    v0.2.x–v0.3.x (which predate this) are repaired in place, so the user's
 *    existing 9.3 MB install keeps working without a reinstall.
 */
object GuestEnvironment {

    /** Rehearsal-proven resolvers (scripts/rehearse_m24_packages.sh). */
    const val RESOLV_CONF_CONTENT = "nameserver 1.1.1.1\nnameserver 8.8.8.8\n"

    const val RESOLV_CONF_RELATIVE = "etc/resolv.conf"

    /**
     * Ensure [rootfsDir]/etc/resolv.conf exists with usable nameservers.
     * Returns true when the file exists afterwards (created now, or already
     * present with content). Never throws for a read-only/busy rootfs —
     * returns false and lets the caller report the honest failure.
     */
    fun ensureDnsResolvers(rootfsDir: File): Boolean {
        val file = File(rootfsDir, RESOLV_CONF_RELATIVE)
        if (file.isFile && file.readText().isNotBlank()) return true
        return try {
            file.parentFile?.mkdirs()
            file.writeText(RESOLV_CONF_CONTENT)
            true
        } catch (_: Exception) {
            false
        }
    }
}

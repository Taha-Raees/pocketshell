package app.pocketshell.widget

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * M8.1 — the Servers detail's cwd mapping: host-side anchors become Linux
 * paths a developer recognizes; unknown host paths are shown stripped of
 * the app-data prefix, never reinterpreted.
 */
class ServersPathsTest {

    private val dataDir = "/data/user/0/app.pocketshell"

    @Test
    fun `the guest home maps to tilde`() {
        assertEquals(
            "~ (Linux home)",
            displayCwdPaths("$dataDir/files/home", dataDir, rootfsPath = "$dataDir/no_backup/runtime/rootfs"),
        )
    }

    @Test
    fun `subdirectories of the guest home map under tilde`() {
        assertEquals(
            "~/projects/app",
            displayCwdPaths("$dataDir/files/home/projects/app", dataDir, rootfsPath = null),
        )
    }

    @Test
    fun `rootfs paths map to guest-absolute paths`() {
        val rootfs = "$dataDir/no_backup/runtime/rootfs"
        assertEquals(
            "/var/www (guest)",
            displayCwdPaths("$rootfs/var/www", dataDir, rootfsPath = rootfs),
        )
        assertEquals(
            "/ (guest root)",
            displayCwdPaths(rootfs, dataDir, rootfsPath = rootfs),
        )
    }

    @Test
    fun `unknown paths pass through with the app prefix stripped`() {
        assertEquals(
            "cache/scratch",
            displayCwdPaths("$dataDir/cache/scratch", dataDir, rootfsPath = null),
        )
        assertEquals(
            "/opt/elsewhere",
            displayCwdPaths("/opt/elsewhere", dataDir, rootfsPath = null),
        )
    }

    @Test
    fun `trailing slashes in anchors are tolerated`() {
        val rootfs = "$dataDir/no_backup/runtime/rootfs"
        assertEquals(
            "/tmp (guest)",
            displayCwdPaths("$rootfs/tmp", dataDir, rootfsPath = "$rootfs/"),
        )
    }
}

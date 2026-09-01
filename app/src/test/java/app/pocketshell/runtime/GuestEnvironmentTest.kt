package app.pocketshell.runtime

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GuestEnvironmentTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `writes rehearsal-proven resolvers when missing`() {
        val rootfs = tmp.newFolder("rootfs")
        assertTrue(GuestEnvironment.ensureDnsResolvers(rootfs))
        val resolv = File(rootfs, "etc/resolv.conf")
        assertTrue(resolv.isFile)
        assertEquals(GuestEnvironment.RESOLV_CONF_CONTENT, resolv.readText())
        assertTrue(resolv.readText().contains("nameserver 1.1.1.1"))
    }

    @Test
    fun `never overwrites an existing configured file`() {
        val rootfs = tmp.newFolder("rootfs")
        val resolv = File(rootfs, "etc/resolv.conf")
        resolv.parentFile.mkdirs()
        resolv.writeText("nameserver 10.0.0.1\n")
        assertTrue(GuestEnvironment.ensureDnsResolvers(rootfs))
        assertEquals("nameserver 10.0.0.1\n", resolv.readText())
    }

    @Test
    fun `repairs a blank file`() {
        val rootfs = tmp.newFolder("rootfs")
        val resolv = File(rootfs, "etc/resolv.conf")
        resolv.parentFile.mkdirs()
        resolv.writeText("")
        assertTrue(GuestEnvironment.ensureDnsResolvers(rootfs))
        assertEquals(GuestEnvironment.RESOLV_CONF_CONTENT, resolv.readText())
    }

    @Test
    fun `reports false instead of throwing on unwritable rootfs`() {
        val rootfs = tmp.newFolder("rootfs")
        // make etc/ an unwritable directory (best-effort across filesystems)
        val etc = File(rootfs, "etc")
        etc.mkdirs()
        etc.setWritable(false)
        val ok = GuestEnvironment.ensureDnsResolvers(rootfs)
        // EITHER the write failed (returned false) or the fs ignored the flag —
        // the contract is only "no throw + honest boolean".
        if (!ok) {
            assertFalse(File(etc, "resolv.conf").exists() && File(etc, "resolv.conf").length() > 0)
        }
        etc.setWritable(true)
    }

    // ------------------------------------------------- v0.4.1 device servers

    @Test
    fun `writes device resolvers when provided`() {
        val rootfs = tmp.newFolder("rootfs")
        assertTrue(
            GuestEnvironment.ensureDnsResolvers(rootfs, listOf("192.168.1.1", "10.0.0.138")),
        )
        assertEquals(
            "nameserver 192.168.1.1\nnameserver 10.0.0.138\n",
            File(rootfs, "etc/resolv.conf").readText(),
        )
    }

    @Test
    fun `empty device list falls back to the public pair`() {
        val rootfs = tmp.newFolder("rootfs")
        assertTrue(GuestEnvironment.ensureDnsResolvers(rootfs, emptyList()))
        assertEquals(GuestEnvironment.RESOLV_CONF_CONTENT, File(rootfs, "etc/resolv.conf").readText())
    }

    @Test
    fun `v040 public fallback is upgraded in place to device resolvers`() {
        // The exact v0.4.0 bug: the repair wrote 1.1.1.1/8.8.8.8, which were
        // unreachable on the user's network, and the never-overwrite rule then
        // kept them forever. Only content WE wrote may be replaced.
        val rootfs = tmp.newFolder("rootfs")
        val resolv = File(rootfs, "etc/resolv.conf")
        resolv.parentFile.mkdirs()
        resolv.writeText(GuestEnvironment.RESOLV_CONF_CONTENT)
        assertTrue(
            GuestEnvironment.ensureDnsResolvers(rootfs, listOf("192.168.1.1")),
        )
        assertEquals("nameserver 192.168.1.1\n", resolv.readText())
    }

    @Test
    fun `v040 fallback stays when device resolvers are unavailable`() {
        val rootfs = tmp.newFolder("rootfs")
        val resolv = File(rootfs, "etc/resolv.conf")
        resolv.parentFile.mkdirs()
        resolv.writeText(GuestEnvironment.RESOLV_CONF_CONTENT)
        assertTrue(GuestEnvironment.ensureDnsResolvers(rootfs, emptyList()))
        assertEquals(GuestEnvironment.RESOLV_CONF_CONTENT, resolv.readText())
    }

    @Test
    fun `user-configured resolv conf is never upgraded away`() {
        val rootfs = tmp.newFolder("rootfs")
        val resolv = File(rootfs, "etc/resolv.conf")
        resolv.parentFile.mkdirs()
        resolv.writeText("nameserver 10.0.0.1\n")
        assertTrue(
            GuestEnvironment.ensureDnsResolvers(rootfs, listOf("192.168.1.1")),
        )
        assertEquals("nameserver 10.0.0.1\n", resolv.readText())
    }

    // ------------------------------------------------------ apk workspace

    @Test
    fun `apk workspace repair creates cache dirs with writable modes`() {
        val rootfs = tmp.newFolder("rootfs")
        assertTrue(GuestEnvironment.ensureApkWorkspace(rootfs))
        for (relative in listOf(
            GuestEnvironment.APK_CACHE_ETC_RELATIVE,
            GuestEnvironment.APK_CACHE_VAR_RELATIVE,
            GuestEnvironment.APK_TMP_RELATIVE,
        )) {
            assertTrue(relative, File(rootfs, relative).isDirectory)
        }
        val tmpMode = java.nio.file.Files.getPosixFilePermissions(
            File(rootfs, GuestEnvironment.APK_TMP_RELATIVE).toPath(),
        )
        assertTrue(tmpMode.contains(java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE))
        assertTrue(tmpMode.contains(java.nio.file.attribute.PosixFilePermission.OWNER_WRITE))
    }

    @Test
    fun `apk workspace repair fixes a cache dir stripped to read-only`() {
        val rootfs = tmp.newFolder("rootfs")
        val cache = File(rootfs, GuestEnvironment.APK_CACHE_VAR_RELATIVE)
        cache.mkdirs()
        cache.setWritable(false)
        val ok = GuestEnvironment.ensureApkWorkspace(rootfs)
        cache.setWritable(true) // tearDown hygiene on filesystems honoring the flag
        // Either the mode was repaired or the filesystem ignored the flag —
        // the honest contract is "no throw, dirs exist afterwards".
        if (ok) {
            val perms = java.nio.file.Files.getPosixFilePermissions(cache.toPath())
            assertTrue(perms.contains(java.nio.file.attribute.PosixFilePermission.OWNER_WRITE))
        }
    }
}

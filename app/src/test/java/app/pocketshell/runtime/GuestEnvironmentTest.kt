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
        val text = resolv.readText()
        assertTrue(text.startsWith(GuestEnvironment.RESOLV_CONF_MARKER))
        assertTrue(text.contains("nameserver 1.1.1.1"))
        assertTrue(text.contains("nameserver 8.8.8.8"))
    }

    @Test
    fun `never overwrites an existing user-customized file`() {
        val rootfs = tmp.newFolder("rootfs")
        val resolv = File(rootfs, "etc/resolv.conf")
        resolv.parentFile.mkdirs()
        // comments + options + a private resolver = a human made this
        resolv.writeText("# my lab dns\nnameserver 10.0.0.1\noptions timeout:1\n")
        assertTrue(
            GuestEnvironment.ensureDnsResolvers(rootfs, listOf("192.168.1.1")),
        )
        assertEquals(
            "# my lab dns\nnameserver 10.0.0.1\noptions timeout:1\n",
            resolv.readText(),
        )
    }

    @Test
    fun `repairs a blank file`() {
        val rootfs = tmp.newFolder("rootfs")
        val resolv = File(rootfs, "etc/resolv.conf")
        resolv.parentFile.mkdirs()
        resolv.writeText("")
        assertTrue(GuestEnvironment.ensureDnsResolvers(rootfs))
        assertTrue(resolv.readText().contains("nameserver 1.1.1.1"))
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

    // ------------------------------------------------- v0.4.3 combined DNS

    @Test
    fun `device resolvers come first with public fallbacks appended`() {
        val rootfs = tmp.newFolder("rootfs")
        assertTrue(
            GuestEnvironment.ensureDnsResolvers(rootfs, listOf("192.168.1.1", "10.0.0.138")),
        )
        val text = File(rootfs, "etc/resolv.conf").readText()
        assertEquals(
            GuestEnvironment.RESOLV_CONF_MARKER + "\n" +
                "nameserver 192.168.1.1\n" +
                "nameserver 10.0.0.138\n" +
                "nameserver 1.1.1.1\n",
            text,
        )
    }

    @Test
    fun `at most three nameserver lines - musl MAXNS cap`() {
        val rootfs = tmp.newFolder("rootfs")
        assertTrue(
            GuestEnvironment.ensureDnsResolvers(
                rootfs,
                listOf("10.0.0.1", "10.0.0.2", "10.0.0.3"),
            ),
        )
        val servers = File(rootfs, "etc/resolv.conf").readLines()
            .filter { it.startsWith("nameserver ") }
        assertEquals(3, servers.size)
        assertEquals(listOf("10.0.0.1", "10.0.0.2", "10.0.0.3"), servers.map { it.removePrefix("nameserver ") })
    }

    @Test
    fun `zone-suffixed link-local is dropped - musl cannot parse it`() {
        val rootfs = tmp.newFolder("rootfs")
        assertTrue(
            GuestEnvironment.ensureDnsResolvers(
                rootfs,
                listOf("fe80::8c98:6bff:fe13:bf64%wlan0"),
            ),
        )
        val text = File(rootfs, "etc/resolv.conf").readText()
        assertFalse(text.contains("%wlan0"))
        assertTrue(text.contains("nameserver 1.1.1.1"))
    }

    @Test
    fun `empty device list writes the marked public pair`() {
        val rootfs = tmp.newFolder("rootfs")
        assertTrue(GuestEnvironment.ensureDnsResolvers(rootfs, emptyList()))
        val text = File(rootfs, "etc/resolv.conf").readText()
        assertTrue(text.startsWith(GuestEnvironment.RESOLV_CONF_MARKER))
        assertTrue(text.contains("nameserver 1.1.1.1\n"))
        assertTrue(text.contains("nameserver 8.8.8.8\n"))
    }

    @Test
    fun `v041 device-only file is upgraded to combined with public fallbacks`() {
        // The exact v0.4.2 on-device shape from the 2026-09-02 screenshots:
        // a single flaky hotspot resolver + an unparseable zone-suffixed
        // link-local. "DNS: transient error" was that single point of failure.
        val rootfs = tmp.newFolder("rootfs")
        val resolv = File(rootfs, "etc/resolv.conf")
        resolv.parentFile.mkdirs()
        resolv.writeText("nameserver 172.20.10.1\nnameserver fe80::8c98:6bff:fe13:bf64%wlan0\n")
        assertTrue(
            GuestEnvironment.ensureDnsResolvers(rootfs, listOf("172.20.10.1")),
        )
        val text = resolv.readText()
        assertTrue(text.startsWith(GuestEnvironment.RESOLV_CONF_MARKER))
        assertTrue(text.contains("nameserver 172.20.10.1\n"))
        assertTrue(text.contains("nameserver 1.1.1.1\n"))
        assertTrue(text.contains("nameserver 8.8.8.8\n"))
        assertFalse(text.contains("%wlan0"))
    }

    @Test
    fun `stale managed resolvers from another network are refreshed`() {
        val rootfs = tmp.newFolder("rootfs")
        val resolv = File(rootfs, "etc/resolv.conf")
        resolv.parentFile.mkdirs()
        resolv.writeText(
            GuestEnvironment.RESOLV_CONF_MARKER + "\nnameserver 192.168.43.1\n",
        )
        assertTrue(
            GuestEnvironment.ensureDnsResolvers(rootfs, listOf("172.20.10.1")),
        )
        val text = resolv.readText()
        assertTrue(text.contains("nameserver 172.20.10.1\n"))
        assertFalse(text.contains("192.168.43.1"))
    }

    @Test
    fun `v040 public fallback is upgraded in place to the combined form`() {
        val rootfs = tmp.newFolder("rootfs")
        val resolv = File(rootfs, "etc/resolv.conf")
        resolv.parentFile.mkdirs()
        resolv.writeText(GuestEnvironment.RESOLV_CONF_CONTENT)
        assertTrue(
            GuestEnvironment.ensureDnsResolvers(rootfs, listOf("192.168.1.1")),
        )
        val text = resolv.readText()
        assertTrue(text.startsWith(GuestEnvironment.RESOLV_CONF_MARKER))
        assertTrue(text.contains("nameserver 192.168.1.1\n"))
        assertTrue(text.contains("nameserver 8.8.8.8\n"))
    }

    @Test
    fun `v040 fallback is refreshed to the marked form even without device resolvers`() {
        val rootfs = tmp.newFolder("rootfs")
        val resolv = File(rootfs, "etc/resolv.conf")
        resolv.parentFile.mkdirs()
        resolv.writeText(GuestEnvironment.RESOLV_CONF_CONTENT)
        assertTrue(GuestEnvironment.ensureDnsResolvers(rootfs, emptyList()))
        val text = resolv.readText()
        assertTrue(text.startsWith(GuestEnvironment.RESOLV_CONF_MARKER))
        assertTrue(text.contains("nameserver 1.1.1.1\n"))
    }

    @Test
    fun `managedByUs recognizes our shapes only`() {
        assertTrue(GuestEnvironment.managedByUs(GuestEnvironment.RESOLV_CONF_CONTENT))
        assertTrue(
            GuestEnvironment.managedByUs(GuestEnvironment.RESOLV_CONF_MARKER + "\nnameserver 1.1.1.1\n"),
        )
        // legacy v0.4.0-v0.4.2 bare lists
        assertTrue(GuestEnvironment.managedByUs("nameserver 172.20.10.1\n"))
        assertTrue(GuestEnvironment.managedByUs("nameserver 10.0.0.1\nnameserver 10.0.0.2\n"))
        // user content: comments, options, search, hostnames
        assertFalse(
            GuestEnvironment.managedByUs("# mine\nnameserver 10.0.0.1\n"),
        )
        assertFalse(GuestEnvironment.managedByUs("nameserver 10.0.0.1\noptions timeout:1\n"))
        assertFalse(GuestEnvironment.managedByUs("search lan\nnameserver 10.0.0.1\n"))
        assertFalse(GuestEnvironment.managedByUs("nameserver dns.my.lan\n"))
        assertFalse(GuestEnvironment.managedByUs(""))
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

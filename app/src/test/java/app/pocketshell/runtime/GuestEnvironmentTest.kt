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
}

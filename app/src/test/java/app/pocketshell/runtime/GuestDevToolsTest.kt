package app.pocketshell.runtime

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.MessageDigest

/**
 * Workstation phase 2 (docs/runtime/DEV_WORKSTATION.md): the pinned
 * dev-workstation toolset — extraction, marker contract, self-healing,
 * exec-bit preservation, path guards, and the pin-vs-asset integrity check.
 */
class GuestDevToolsTest {

    // ------------------------------------------------------------ tar builder

    /** Minimal devtools-shaped tar: lib payload (5 files) + bin entry points. */
    private fun devtoolsTar(): ByteArray {
        val bytes = ByteArrayOutputStream()
        TarArchiveOutputStream(bytes).use { tar ->
            fun dir(name: String) {
                val e = TarArchiveEntry(name)
                e.mode = 0b101_101_101
                tar.putArchiveEntry(e); tar.closeArchiveEntry()
            }
            fun file(name: String, content: String, mode: Int) {
                val e = TarArchiveEntry(name)
                e.mode = mode
                e.size = content.toByteArray().size.toLong()
                tar.putArchiveEntry(e)
                tar.write(content.toByteArray())
                tar.closeArchiveEntry()
            }
            dir("usr/")
            dir("usr/local/")
            dir("usr/local/lib/")
            dir("usr/local/lib/pocketshell-dev/")
            dir("usr/local/bin/")
            file(
                "usr/local/lib/pocketshell-dev/manifest.conf",
                "JDK_VERSION=21.0.12.1+1\n",
                0b110_100_100,
            )
            file(
                "usr/local/lib/pocketshell-dev/pocketshell-dev-bootstrap",
                "#!/bin/sh\necho bootstrap\n",
                0b111_101_101,
            )
            file(
                "usr/local/lib/pocketshell-dev/build-minimal-apk.sh",
                "#!/bin/sh\necho build\n",
                0b111_101_101,
            )
            file(
                "usr/local/lib/pocketshell-dev/pocketshell-adb",
                "#!/bin/sh\necho adb-helper\n",
                0b111_101_101,
            )
            file(
                "usr/local/lib/pocketshell-dev/provenance.aarch64.template",
                "community aarch64 host tools\n",
                0b110_100_100,
            )
            file(
                "usr/local/bin/pocketshell-dev-bootstrap",
                "#!/bin/sh\necho bootstrap\n",
                0b111_101_101,
            )
            file(
                "usr/local/bin/pocketshell-adb",
                "#!/bin/sh\necho adb-helper\n",
                0b111_101_101,
            )
        }
        return bytes.toByteArray()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun open(bytes: ByteArray): () -> java.io.InputStream = { bytes.inputStream() }

    // ------------------------------------------------------------------ tests

    @Test
    fun `extract writes payload with exec bits and marker last`() {
        val root = createTempRootfs()
        val tar = devtoolsTar()
        val result = GuestDevTools.ensureInstalled(root, sha256(tar), open(tar))
        assertTrue("expected Installed, got $result", result is GuestDevTools.Result.Installed)
        assertEquals(GuestDevTools.markerContent(), File(root, GuestDevTools.MARKER_RELATIVE).readText())
        assertTrue(GuestDevTools.isCurrent(root))
        assertTrue(GuestDevTools.structuralIntegrityPasses(root))
        // exec bits preserved on the bin entry points (the whole point)
        val bin = File(root, "usr/local/bin/pocketshell-dev-bootstrap")
        assertTrue(Files.isExecutable(bin.toPath()))
        // status mirror written for the guest
        assertTrue(File(root, GuestDevTools.STATUS_RELATIVE).readText().startsWith("state=OK source=extractor"))
    }

    @Test
    fun `second ensure is the marker fast path`() {
        val root = createTempRootfs()
        val tar = devtoolsTar()
        GuestDevTools.ensureInstalled(root, sha256(tar), open(tar))
        // clobber a payload file's CONTENT but keep the marker: the marker is
        // the completeness contract, the structural probe catches the rest
        File(root, "usr/local/lib/pocketshell-dev/manifest.conf").writeText("tampered")
        File(root, "usr/local/bin/pocketshell-dev-bootstrap").setExecutable(false)
        assertFalse(GuestDevTools.structuralIntegrityPasses(root))
        val healed = GuestDevTools.ensureInstalled(root, sha256(tar), open(tar))
        assertTrue("expected self-heal, got $healed", healed is GuestDevTools.Result.Installed)
        assertEquals(
            "JDK_VERSION=21.0.12.1+1\n",
            File(root, "usr/local/lib/pocketshell-dev/manifest.conf").readText(),
        )
        assertTrue(Files.isExecutable(File(root, "usr/local/bin/pocketshell-dev-bootstrap").toPath()))
    }

    @Test
    fun `healthy layer takes the fast path`() {
        val root = createTempRootfs()
        val tar = devtoolsTar()
        GuestDevTools.ensureInstalled(root, sha256(tar), open(tar))
        val second = GuestDevTools.ensureInstalled(root, sha256(tar), open(tar))
        assertTrue(second is GuestDevTools.Result.Current)
        assertTrue(File(root, GuestDevTools.STATUS_RELATIVE).readText().startsWith("state=OK source=fastpath"))
    }

    @Test
    fun `sha mismatch fails closed with no marker`() {
        val root = createTempRootfs()
        val tar = devtoolsTar()
        val result = GuestDevTools.ensureInstalled(root, "deadbeef".repeat(8), open(tar))
        assertTrue(result is GuestDevTools.Result.Failed)
        assertFalse(GuestDevTools.isCurrent(root))
        assertFalse(File(root, GuestDevTools.MARKER_RELATIVE).exists())
        assertNull(
            (result as GuestDevTools.Result.Failed).reason.takeIf { !it.contains("sha mismatch") },
        )
    }

    @Test
    fun `path traversal entry is refused`() {
        val root = createTempRootfs()
        val bytes = ByteArrayOutputStream()
        TarArchiveOutputStream(bytes).use { tar ->
            val e = TarArchiveEntry("../../etc/evil")
            e.mode = 0b110_100_100
            e.size = 4L
            tar.putArchiveEntry(e)
            tar.write("evil".toByteArray())
            tar.closeArchiveEntry()
        }
        val result = GuestDevTools.ensureInstalled(root, null, open(bytes.toByteArray()))
        assertTrue(result is GuestDevTools.Result.Failed)
        assertFalse(File(root.parentFile, "etc/evil").exists())
    }

    @Test
    fun `link entry fails closed (payload is plain files by contract)`() {
        val root = createTempRootfs()
        val bytes = ByteArrayOutputStream()
        TarArchiveOutputStream(bytes).use { tar ->
            val e = TarArchiveEntry("usr/local/bin/evil", TarArchiveEntry.LF_SYMLINK)
            e.linkName = "/system/bin/sh"
            tar.putArchiveEntry(e); tar.closeArchiveEntry()
        }
        val result = GuestDevTools.ensureInstalled(root, null, open(bytes.toByteArray()))
        assertTrue(result is GuestDevTools.Result.Failed)
        assertFalse(
            Files.isSymbolicLink(File(root, "usr/local/bin/evil").toPath()),
        )
    }

    @Test
    fun `old-revision file at a bin target is replaced not followed`() {
        val root = createTempRootfs()
        val binDir = File(root, "usr/local/bin")
        binDir.mkdirs()
        File(binDir, "pocketshell-dev-bootstrap").writeText("stale previous install")
        val tar = devtoolsTar()
        val result = GuestDevTools.ensureInstalled(root, sha256(tar), open(tar))
        assertTrue(result is GuestDevTools.Result.Installed)
        assertTrue(
            File(root, "usr/local/bin/pocketshell-dev-bootstrap").readText().startsWith("#!/bin/sh"),
        )
        assertTrue(
            Files.isRegularFile(
                File(root, "usr/local/bin/pocketshell-dev-bootstrap").toPath(),
                LinkOption.NOFOLLOW_LINKS,
            ),
        )
    }

    private fun createTempRootfs(): File =
        Files.createTempDirectory("ps-devtools-test").toFile()
}

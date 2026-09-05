package app.pocketshell.runtime

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.MessageDigest

/**
 * M6.0 — the PocketShell glibc runtime layer (docs/runtime/DUAL_LIBC.md):
 * extraction, marker contract, self-healing, musl isolation, path guards,
 * and the pin-vs-asset integrity check.
 */
class GuestGlibcRuntimeTest {

    // ------------------------------------------------------------ tar builder

    /** Minimal glibc-layer-shaped tar: dirs, file (mode), symlink, nested file. */
    private fun layerTar(): ByteArray {
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
            fun link(name: String, target: String) {
                val e = TarArchiveEntry(name, TarArchiveEntry.LF_SYMLINK)
                e.linkName = target
                e.mode = 0b101_101_101
                tar.putArchiveEntry(e); tar.closeArchiveEntry()
            }
            dir("./")
            dir("./usr/")
            dir("./usr/lib/")
            dir("./usr/lib/aarch64-linux-gnu/")
            file("./usr/lib/aarch64-linux-gnu/libc.so.6", "REAL-GLIBC-LIBC", 0b100_101_101)
            file("./usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1", "REAL-LOADER", 0b101_101_101)
            dir("./lib/")
            link("./lib/ld-linux-aarch64.so.1", "/usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1")
            dir("./usr/local/")
            dir("./usr/local/bin/")
            file("./usr/local/bin/pocketshell-doctor", "#!/bin/sh\necho doctor\n", 0b101_101_101)
        }
        return bytes.toByteArray()
    }

    private fun gz(bytes: ByteArray): InputStream =
        java.util.zip.GZIPInputStream(ByteArrayInputStream(bytes))

    private fun tarStream(): InputStream {
        val gzBytes = ByteArrayOutputStream()
        java.util.zip.GZIPOutputStream(gzBytes).use { it.write(layerTar()) }
        return ByteArrayInputStream(gzBytes.toByteArray())
    }

    private fun newRootfs(): File {
        val root = Files.createTempDirectory("glibc-layer-test").toFile()
        // musl baseline: a file the layer must never touch.
        File(root, "bin").mkdirs()
        File(root, "bin/busybox").writeText("MUSL-SENTINEL")
        return root
    }

    // ----------------------------------------------------------------- tests

    @Test
    fun `extracts layer, writes marker, preserves exec bits and symlinks`() {
        val root = newRootfs()
        val result = GuestGlibcRuntime.ensureInstalled(root) { tarStream() }
        assertTrue("expected Installed, got $result", result is GuestGlibcRuntime.Result.Installed)

        assertEquals(
            GlibcRuntimePin.markerContent(),
            File(root, GlibcRuntimePin.MARKER_RELATIVE).readText(),
        )
        assertEquals(
            "REAL-GLIBC-LIBC",
            File(root, "usr/lib/aarch64-linux-gnu/libc.so.6").readText(),
        )
        val loader = File(root, "usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1")
        assertTrue("loader file must be executable", loader.canExecute())
        val link = File(root, "lib/ld-linux-aarch64.so.1").toPath()
        assertTrue(Files.isSymbolicLink(link))
        assertEquals(
            "/usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1",
            Files.readSymbolicLink(link).toString(),
        )
        assertTrue("guest tool must be executable", File(root, "usr/local/bin/pocketshell-doctor").canExecute())
    }

    @Test
    fun `second ensure is the marker fast path`() {
        val root = newRootfs()
        GuestGlibcRuntime.ensureInstalled(root) { tarStream() }
        val result = GuestGlibcRuntime.ensureInstalled(root) {
            throw IllegalStateException("asset must not be re-opened on the fast path")
        }
        assertEquals(GuestGlibcRuntime.Result.Current, result)
    }

    @Test
    fun `corrupt or stale marker triggers self-healing re-extraction`() {
        val root = newRootfs()
        GuestGlibcRuntime.ensureInstalled(root) { tarStream() }
        File(root, GlibcRuntimePin.MARKER_RELATIVE).writeText("tampered\n")
        val result = GuestGlibcRuntime.ensureInstalled(root) { tarStream() }
        assertTrue("expected re-Installed, got $result", result is GuestGlibcRuntime.Result.Installed)
        assertTrue(GuestGlibcRuntime.isCurrent(root))
    }

    @Test
    fun `musl files are never touched`() {
        val root = newRootfs()
        GuestGlibcRuntime.ensureInstalled(root) { tarStream() }
        assertEquals("MUSL-SENTINEL", File(root, "bin/busybox").readText())
    }

    @Test
    fun `path traversal entry is rejected and writes nothing`() {
        val bytes = ByteArrayOutputStream()
        TarArchiveOutputStream(bytes).use { tar ->
            val e = TarArchiveEntry("./../../evil.so")
            e.mode = 0b100_101_101
            val content = "EVIL".toByteArray()
            e.size = content.size.toLong()
            tar.putArchiveEntry(e); tar.write(content); tar.closeArchiveEntry()
        }
        val root = newRootfs()
        val result = GuestGlibcRuntime.ensureInstalled(root) {
            gz(bytes.toByteArray())
        }
        assertTrue(result is GuestGlibcRuntime.Result.Failed)
        assertFalse(File(root.parentFile, "evil.so").exists())
        assertFalse(GuestGlibcRuntime.isCurrent(root))
    }

    @Test
    fun `absolute path entry is rejected`() {
        val bytes = ByteArrayOutputStream()
        TarArchiveOutputStream(bytes).use { tar ->
            val e = TarArchiveEntry("/etc/evil.so")
            e.mode = 0b100_101_101
            val content = "EVIL".toByteArray()
            e.size = content.size.toLong()
            tar.putArchiveEntry(e); tar.write(content); tar.closeArchiveEntry()
        }
        val root = newRootfs()
        val result = GuestGlibcRuntime.ensureInstalled(root) { gz(bytes.toByteArray()) }
        assertTrue(result is GuestGlibcRuntime.Result.Failed)
        assertFalse(File(root, "etc/evil.so").exists())
    }

    @Test
    fun `truncated layer is not current and the next ensure heals it`() {
        val root = newRootfs()
        val truncated = layerTar().copyOfRange(0, layerTar().size / 3)
        val result = GuestGlibcRuntime.ensureInstalled(root) {
            gz(truncated) // truncated gzip → mid-archive failure
        }
        assertTrue("expected Failed, got $result", result is GuestGlibcRuntime.Result.Failed)
        assertFalse("a partial layer must never claim currency", GuestGlibcRuntime.isCurrent(root))

        // The next call with the real artifact heals the layer completely.
        val healed = GuestGlibcRuntime.ensureInstalled(root) { tarStream() }
        assertTrue("expected Installed, got $healed", healed is GuestGlibcRuntime.Result.Installed)
        assertTrue(GuestGlibcRuntime.isCurrent(root))
    }

    // ------------------------------------------------- m6.0.1 observability

    @Test
    fun `install outcome is mirrored to the guest-visible status file`() {
        val root = newRootfs()
        GuestGlibcRuntime.ensureInstalled(root) { tarStream() }
        val status = File(root, GuestGlibcRuntime.STATUS_RELATIVE)
        val line = status.readText()
        assertTrue("status must record OK extractor, got: $line", line.startsWith("state=OK source=extractor entries="))
        assertTrue(line.contains("ts="))
    }

    @Test
    fun `failed install writes its reason to the status file`() {
        val root = newRootfs()
        val truncated = layerTar().copyOfRange(0, layerTar().size / 3)
        GuestGlibcRuntime.ensureInstalled(root) { gz(truncated) }
        val line = File(root, GuestGlibcRuntime.STATUS_RELATIVE).readText()
        assertTrue("status must record FAILED, got: $line", line.startsWith("state=FAILED reason="))
        assertTrue("reason must be single-line", !line.substringBefore(" ts=").contains('\n'))
    }

    @Test
    fun `fast path refreshes the status file`() {
        val root = newRootfs()
        GuestGlibcRuntime.ensureInstalled(root) { tarStream() }
        File(root, GuestGlibcRuntime.STATUS_RELATIVE).writeText("stale\n")
        val result = GuestGlibcRuntime.ensureInstalled(root) {
            throw IllegalStateException("asset must not be re-opened on the fast path")
        }
        assertEquals(GuestGlibcRuntime.Result.Current, result)
        val line = File(root, GuestGlibcRuntime.STATUS_RELATIVE).readText()
        assertTrue("status must record fastpath, got: $line", line.startsWith("state=OK source=fastpath"))
    }

    // ------------------------------------- device-condition (gcompat) pin

    @Test
    fun `gcompat stub at the loader path is replaced by the real loader symlink`() {
        // The m6.0.0 device gate: the rootfs carries gcompat (installed by an
        // earlier era, persisted across app updates). Its
        // /lib/ld-linux-aarch64.so.1 is a REGULAR FILE (the interpreter stub).
        // The layer extraction must replace it — never skip, never fail.
        val root = newRootfs()
        File(root, "lib").mkdirs()
        File(root, "lib/ld-linux-aarch64.so.1").writeText("GCOMPAT-STUB")
        File(root, "lib64").mkdirs()
        File(root, "lib64/ld-linux-aarch64.so.1").writeText("GCOMPAT-STUB")

        val result = GuestGlibcRuntime.ensureInstalled(root) { tarStream() }
        assertTrue("expected Installed over the stub, got $result", result is GuestGlibcRuntime.Result.Installed)

        val link = File(root, "lib/ld-linux-aarch64.so.1").toPath()
        assertTrue("stub must be replaced by the real-loader symlink", Files.isSymbolicLink(link))
        assertEquals(
            "/usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1",
            Files.readSymbolicLink(link).toString(),
        )
        assertEquals("REAL-LOADER", File(root, "usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1").readText())
        assertTrue(GuestGlibcRuntime.isCurrent(root))
    }

    @Test
    fun `pinned asset bytes match the pin`() {
        val candidates = listOf(
            File("src/main/assets/guest"),
            File("app/src/main/assets/guest"),
        )
        val dir = candidates.firstOrNull { File(it, GlibcRuntimePin.ARTIFACT_NAME).isFile }
        org.junit.Assume.assumeTrue(
            "pinned asset not found on this test runner (CI packaging test covers it)",
            dir != null,
        )
        val asset = File(dir, GlibcRuntimePin.ARTIFACT_NAME)
        assertEquals(GlibcRuntimePin.SIZE_BYTES, asset.length())
        val sha = asset.inputStream().use { stream ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(64 * 1024)
            while (true) {
                val r = stream.read(buf)
                if (r < 0) break
                digest.update(buf, 0, r)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
        assertEquals(GlibcRuntimePin.SHA256, sha)
    }
}

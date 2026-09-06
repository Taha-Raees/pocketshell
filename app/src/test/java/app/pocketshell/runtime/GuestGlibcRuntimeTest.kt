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

    /**
     * m6.0.3 DOCTOR-CORRECTNESS GATE: the layer payload changed (doctor v2)
     * while the glibc files stayed byte-identical. The ONLY thing that makes
     * an already-installed device re-extract is the marker text — so the
     * marker must carry the revision, and a rev=1 (vc42-era) marker must be
     * treated as stale. This is the test that keeps the fix propagating.
     */
    @Test
    fun `layer revision bump forces re-extraction of an older layer`() {
        assertTrue(
            "LAYER_REVISION must advance when the payload changes (rev=2 = doctor v2)",
            GlibcRuntimePin.LAYER_REVISION >= 2,
        )
        val root = newRootfs()
        val oldMarker = GlibcRuntimePin.markerContent()
            .replace("rev=${GlibcRuntimePin.LAYER_REVISION}", "rev=1")
        assertTrue("guard: the substituted marker must differ", oldMarker != GlibcRuntimePin.markerContent())
        File(root, GlibcRuntimePin.MARKER_RELATIVE).parentFile!!.mkdirs()
        File(root, GlibcRuntimePin.MARKER_RELATIVE).writeText(oldMarker)

        assertFalse("a rev=1 layer must not claim currency", GuestGlibcRuntime.isCurrent(root))

        val result = GuestGlibcRuntime.ensureInstalled(root) { tarStream() }
        assertTrue("expected re-extraction, got $result", result is GuestGlibcRuntime.Result.Installed)
        assertEquals(
            "the marker must now carry the current revision",
            GlibcRuntimePin.markerContent(),
            File(root, GlibcRuntimePin.MARKER_RELATIVE).readText(),
        )
        assertTrue(GuestGlibcRuntime.isCurrent(root))
    }

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
    fun `release artifact bytes match the artifact pin`() {
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
        assertEquals(GlibcRuntimePin.SHA256, sha256Hex(asset.inputStream()))
    }

    /**
     * m6.0.2 DEVICE-GATE REGRESSION PIN (the vc40/vc41 root cause): the JVM
     * suite verified the SOURCE-tree .tar.gz while AGP's asset merge silently
     * repackaged it as a PLAIN tar under a DIFFERENT name — every device spawn
     * failed with FileNotFoundException and the layer never installed. This
     * test reads the BUILT APK (when present — run assembleDebug first) and
     * asserts the packaged asset entry name, size and sha EXACTLY match the
     * packaged-form pin. This is the check that was missing.
     */
    @Test
    fun `built APK carries the packaged asset under its pinned name and sha`() {
        val apk = listOf(
            File("build/outputs/apk/debug/app-debug.apk"),
            File("app/build/outputs/apk/debug/app-debug.apk"),
        ).firstOrNull { it.isFile }
        org.junit.Assume.assumeTrue(
            "built APK not present on this test runner (run assembleDebug first)",
            apk != null,
        )
        java.util.zip.ZipFile(apk).use { zip ->
            // AssetManager path "guest/…" maps to the zip entry "assets/guest/…".
            val entry = zip.getEntry("assets/" + GlibcRuntimePin.ASSET_PATH)
            assertTrue(
                "APK is missing the pinned asset ${GlibcRuntimePin.ASSET_PATH} — " +
                    "this is exactly the vc40/vc41 device-gate defect",
                entry != null,
            )
            assertEquals(
                "packaged asset size drifted from the pin",
                GlibcRuntimePin.ASSET_SIZE_BYTES,
                entry.size,
            )
            assertEquals(
                "packaged asset sha drifted from the pin",
                GlibcRuntimePin.ASSET_SHA256,
                sha256Hex(zip.getInputStream(entry)),
            )
        }
    }

    /**
     * m6.0.2: the PLAIN-tar form (what AGP actually packages from the pinned
     * .tar.gz) must extract identically through the format-sniffing extractor.
     */
    @Test
    fun `plain-tar asset (the AGP-packaged form) extracts identically`() {
        val root = newRootfs()
        val plainTar = layerTar()
        val result = GuestGlibcRuntime.ensureInstalled(
            root,
            assetSha256 = sha256Hex(ByteArrayInputStream(plainTar)),
        ) { ByteArrayInputStream(plainTar) }
        assertTrue("expected Installed, got $result", result is GuestGlibcRuntime.Result.Installed)
        assertTrue(GuestGlibcRuntime.isCurrent(root))
        assertEquals("REAL-GLIBC-LIBC", File(root, "usr/lib/aarch64-linux-gnu/libc.so.6").readText())
    }

    /** m6.0.2: a sha-pinned asset that does NOT match is a FAILED result — never a half-extraction. */
    @Test
    fun `asset sha mismatch is a FAILED result and writes nothing`() {
        val root = newRootfs()
        val result = GuestGlibcRuntime.ensureInstalled(
            root,
            assetSha256 = "0".repeat(64),
        ) { tarStream() }
        assertTrue("expected Failed, got $result", result is GuestGlibcRuntime.Result.Failed)
        assertFalse("no marker may exist after a sha mismatch", File(root, GlibcRuntimePin.MARKER_RELATIVE).exists())
        assertFalse("no layer bytes may exist after a sha mismatch", File(root, "usr/lib/aarch64-linux-gnu/libc.so.6").exists())
    }

    /**
     * m6.0.2 DEVICE-GATE REGRESSION PIN (the observable half): the vc40/vc41
     * device condition — the asset open throws (wrong name in the shipped
     * APK) — must surface as state=FAILED + the reason in the guest-visible
     * status file, so a missing layer is diagnosable from inside the guest.
     */
    @Test
    fun `asset open failure surfaces its reason in the guest status file`() {
        val root = newRootfs()
        val result = GuestGlibcRuntime.ensureInstalled(root) {
            throw java.io.FileNotFoundException(GlibcRuntimePin.ASSET_PATH)
        }
        assertTrue("expected Failed, got $result", result is GuestGlibcRuntime.Result.Failed)
        val status = File(root, GuestGlibcRuntime.STATUS_RELATIVE).readText()
        assertTrue("status must record the failure: $status", status.startsWith("state=FAILED"))
        assertTrue("status must carry the reason: $status", status.contains(GlibcRuntimePin.ASSET_PATH))
    }

    // ------------------------------------------------------------- sha helper

    private fun sha256Hex(stream: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(64 * 1024)
        while (true) {
            val r = stream.read(buf)
            if (r < 0) break
            digest.update(buf, 0, r)
        }
        stream.close()
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

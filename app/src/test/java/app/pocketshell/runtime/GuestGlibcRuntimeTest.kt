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
import kotlin.concurrent.thread

/**
 * M6.0 — the PocketShell glibc runtime layer (docs/runtime/DUAL_LIBC.md):
 * extraction, marker contract, self-healing, musl isolation, path guards,
 * and the pin-vs-asset integrity check.
 */
class GuestGlibcRuntimeTest {

    // ------------------------------------------------------------ tar builder

    /**
     * Minimal glibc-layer-shaped tar with the REAL layer's entry ORDER and
     * shapes (Phase-C audit fixture): the directory symlink
     * lib/aarch64-linux-gnu -> ../usr/lib/aarch64-linux-gnu comes BEFORE the
     * multiarch files (exactly the shape that made a naïve recursive delete
     * wipe the whole directory during in-place re-extraction), the loader
     * symlink is absolute, and the core-lib/tool set the structural
     * integrity probe checks is present.
     */
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
            dir("./etc/")
            dir("./usr/")
            dir("./usr/lib/")
            dir("./usr/lib/aarch64-linux-gnu/")
            file("./usr/lib/aarch64-linux-gnu/libc.so.6", "REAL-GLIBC-LIBC", 0b100_101_101)
            file("./usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1", "REAL-LOADER", 0b101_101_101)
            file("./usr/lib/aarch64-linux-gnu/libm.so.6", "REAL-LIBM", 0b100_101_101)
            file("./usr/lib/aarch64-linux-gnu/libpthread.so.0", "REAL-PTHREAD", 0b100_101_101)
            file("./usr/lib/aarch64-linux-gnu/libdl.so.2", "REAL-DL", 0b100_101_101)
            file("./usr/lib/aarch64-linux-gnu/libstdc++.so.6", "REAL-STDCXX", 0b100_101_101)
            dir("./lib/")
            // The REAL layer's directory symlink — placed BEFORE the files it
            // points at, exactly like the pinned sidecar.
            link("./lib/aarch64-linux-gnu", "../usr/lib/aarch64-linux-gnu")
            link("./lib/ld-linux-aarch64.so.1", "/usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1")
            dir("./usr/local/")
            dir("./usr/local/bin/")
            file("./usr/local/bin/pocketshell-doctor", "#!/bin/sh\necho doctor\n", 0b101_101_101)
            file("./usr/local/bin/pocketshell-exec", "#!/bin/sh\necho exec\n", 0b101_101_101)
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

    // ------------------------------------- M6 Phase-C adversarial closure pins

    /**
     * PHASE-C F1 PIN (C3/C12): in-place re-extraction must replace an
     * existing symlink by deleting the LINK NODE — never by walking through
     * it. The real layer ships lib/aarch64-linux-gnu -> ../usr/lib/aarch64-
     * linux-gnu BEFORE the multiarch files; the old deleteRecursively-based
     * replaceSymlink FOLLOWED the directory symlink and wiped every layer
     * library mid-re-extraction (converged only because the files were
     * rewritten right after; the window was observable to concurrent apk
     * ops and running sessions). A foreign sentinel in the pointed-to
     * directory is the deterministic witness: the old code deletes it.
     */
    @Test
    fun `re-extraction never deletes through the layer's directory symlink`() {
        val root = newRootfs()
        GuestGlibcRuntime.ensureInstalled(root) { tarStream() }
        val multarch = File(root, "usr/lib/aarch64-linux-gnu")
        val sentinel = File(multarch, "foreign-sentinel")
        sentinel.writeText("NOT-FROM-THE-ARCHIVE")
        // Force the in-place re-extraction path with the exact real order.
        File(root, GlibcRuntimePin.MARKER_RELATIVE).delete()

        val result = GuestGlibcRuntime.ensureInstalled(root) { tarStream() }
        assertTrue("expected re-Installed, got $result", result is GuestGlibcRuntime.Result.Installed)
        assertTrue(
            "the re-extraction must not delete foreign files through the directory symlink",
            sentinel.isFile && sentinel.readText() == "NOT-FROM-THE-ARCHIVE",
        )
        assertTrue("layer content must be intact after re-extraction", GuestGlibcRuntime.isCurrent(root))
    }

    /** PHASE-C F2 PIN (C2.2): a deleted loader behind a valid marker must self-heal. */
    @Test
    fun `deleted loader behind a valid marker is detected and self-heals`() {
        val root = newRootfs()
        GuestGlibcRuntime.ensureInstalled(root) { tarStream() }
        assertTrue(GuestGlibcRuntime.isCurrent(root))
        File(root, GuestGlibcRuntime.LOADER_RELATIVE).delete()
        assertFalse("a missing loader must not pass the integrity probe", GuestGlibcRuntime.isCurrent(root))

        val result = GuestGlibcRuntime.ensureInstalled(root) { tarStream() }
        assertTrue("expected self-healing re-extraction, got $result", result is GuestGlibcRuntime.Result.Installed)
        assertTrue(Files.isSymbolicLink(File(root, GuestGlibcRuntime.LOADER_RELATIVE).toPath()))
        assertTrue(GuestGlibcRuntime.isCurrent(root))
    }

    /**
     * PHASE-C F3 PIN (C4/C2.2): the proven gcompat reclaim — Alpine's gcompat
     * package owns lib/ld-linux-aarch64.so.1 and ships a REAL ELF shim there;
     * `apk fix/reinstall/upgrade gcompat` can put it back behind a perfectly
     * valid marker. The structural probe must detect the shape and the next
     * ensure must restore the real loader symlink.
     */
    @Test
    fun `gcompat-shaped loader reclaim behind a valid marker is detected and self-heals`() {
        val root = newRootfs()
        GuestGlibcRuntime.ensureInstalled(root) { tarStream() }
        // The reclaim: the symlink becomes a regular file (the shim ELF).
        val loader = File(root, GuestGlibcRuntime.LOADER_RELATIVE)
        loader.delete()
        loader.writeText("GCOMPAT-SHIM-ELF-BYTES")
        assertFalse("a shim at the loader path must not pass the probe", GuestGlibcRuntime.isCurrent(root))

        val result = GuestGlibcRuntime.ensureInstalled(root) { tarStream() }
        assertTrue("expected self-healing re-extraction, got $result", result is GuestGlibcRuntime.Result.Installed)
        val link = loader.toPath()
        assertTrue("the real-loader symlink must be restored", Files.isSymbolicLink(link))
        assertEquals(GuestGlibcRuntime.LOADER_CANONICAL_TARGET, Files.readSymbolicLink(link).toString())
        assertTrue(GuestGlibcRuntime.isCurrent(root))
    }

    /** PHASE-C F2 PIN (C2.3): a deleted core library must self-heal, not report healthy. */
    @Test
    fun `deleted core library behind a valid marker is detected and self-heals`() {
        val root = newRootfs()
        GuestGlibcRuntime.ensureInstalled(root) { tarStream() }
        File(root, "usr/lib/aarch64-linux-gnu/libpthread.so.0").delete()
        assertFalse(GuestGlibcRuntime.isCurrent(root))

        val result = GuestGlibcRuntime.ensureInstalled(root) { tarStream() }
        assertTrue("expected self-healing re-extraction, got $result", result is GuestGlibcRuntime.Result.Installed)
        assertTrue(GuestGlibcRuntime.isCurrent(root))
    }

    /** PHASE-C F2 PIN: a deleted diagnostic tool must self-heal too. */
    @Test
    fun `deleted pocketshell-doctor behind a valid marker is detected and self-heals`() {
        val root = newRootfs()
        GuestGlibcRuntime.ensureInstalled(root) { tarStream() }
        File(root, "usr/local/bin/pocketshell-doctor").delete()
        assertFalse(GuestGlibcRuntime.isCurrent(root))
        assertTrue(
            "expected self-healing re-extraction",
            GuestGlibcRuntime.ensureInstalled(root) { tarStream() } is GuestGlibcRuntime.Result.Installed,
        )
        assertTrue(GuestGlibcRuntime.isCurrent(root))
    }

    /** PHASE-C F2: a healthy layer passes the probe and keeps the marker fast path. */
    @Test
    fun `structural integrity passes on a healthy layer and keeps the fast path`() {
        val root = newRootfs()
        GuestGlibcRuntime.ensureInstalled(root) { tarStream() }
        assertTrue(GuestGlibcRuntime.structuralIntegrityPasses(root))
        val result = GuestGlibcRuntime.ensureInstalled(root) {
            throw IllegalStateException("a healthy layer must not re-extract")
        }
        assertEquals(GuestGlibcRuntime.Result.Current, result)
    }

    /** PHASE-C (C3): concurrent ensures are single-flight — exactly one extraction. */
    @Test
    fun `concurrent ensures extract exactly once`() {
        val root = newRootfs()
        val threads = 8
        val barrier = java.util.concurrent.CyclicBarrier(threads)
        val outcomes = java.util.Collections.synchronizedList(mutableListOf<GuestGlibcRuntime.Result>())
        val workers = List(threads) {
            thread(start = false) {
                barrier.await()
                outcomes.add(GuestGlibcRuntime.ensureInstalled(root) { tarStream() })
            }
        }
        workers.forEach { it.start() }
        workers.forEach { it.join() }
        val installed = outcomes.count { it is GuestGlibcRuntime.Result.Installed }
        val current = outcomes.count { it == GuestGlibcRuntime.Result.Current }
        assertEquals(
            "exactly one thread may run the extraction (got $installed Installed, $current Current)",
            1,
            installed,
        )
        assertEquals(threads - 1, current)
        assertTrue(GuestGlibcRuntime.isCurrent(root))
    }

    /** PHASE-C F5 PIN (C12): an entry routed through an earlier symlink entry is refused. */
    @Test
    fun `archive entry routed through an earlier symlink is refused`() {
        val bytes = ByteArrayOutputStream()
        TarArchiveOutputStream(bytes).use { tar ->
            fun link(name: String, target: String) {
                val e = TarArchiveEntry(name, TarArchiveEntry.LF_SYMLINK)
                e.linkName = target
                e.mode = 0b101_101_101
                tar.putArchiveEntry(e); tar.closeArchiveEntry()
            }
            fun file(name: String, content: String) {
                val e = TarArchiveEntry(name)
                e.mode = 0b100_101_101
                e.size = content.toByteArray().size.toLong()
                tar.putArchiveEntry(e)
                tar.write(content.toByteArray())
                tar.closeArchiveEntry()
            }
            // Entry 1: a legitimate-looking directory symlink.
            link("./lib/aarch64-linux-gnu", "../usr/lib/aarch64-linux-gnu")
            // Entry 2: a write routed THROUGH entry 1 — the hostile shape.
            file("./lib/aarch64-linux-gnu/evil.so", "EVIL")
        }
        val root = newRootfs()
        val result = GuestGlibcRuntime.ensureInstalled(root) { gz(bytes.toByteArray()) }
        assertTrue("expected Failed, got $result", result is GuestGlibcRuntime.Result.Failed)
        assertFalse(
            "nothing may be written through the symlink",
            File(root, "usr/lib/aarch64-linux-gnu/evil.so").exists(),
        )
        assertFalse(GuestGlibcRuntime.isCurrent(root))
    }

    /**
     * PHASE-C: the REAL pinned layer archive (103 entries, absolute loader
     * symlink, directory symlink before its targets) must extract AND
     * re-extract in place under the hardened extractor — the guards must
     * never reject the artifact we actually ship.
     */
    @Test
    fun `real pinned layer archive extracts and re-extracts under the hardened extractor`() {
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

        val root = newRootfs()
        val first = GuestGlibcRuntime.ensureInstalled(root) { asset.inputStream() }
        assertTrue("expected Installed, got $first", first is GuestGlibcRuntime.Result.Installed)
        assertTrue(GuestGlibcRuntime.isCurrent(root))
        assertTrue(GuestGlibcRuntime.structuralIntegrityPasses(root))

        // In-place re-extraction over the REAL shapes (dir symlink + absolute
        // loader link + 26 symlinks) must succeed and stay complete.
        File(root, GlibcRuntimePin.MARKER_RELATIVE).delete()
        val second = GuestGlibcRuntime.ensureInstalled(root) { asset.inputStream() }
        assertTrue("expected re-Installed, got $second", second is GuestGlibcRuntime.Result.Installed)
        assertTrue(GuestGlibcRuntime.isCurrent(root))
        assertTrue(
            "the real loader file must survive the re-extraction",
            File(root, "usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1").isFile,
        )
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

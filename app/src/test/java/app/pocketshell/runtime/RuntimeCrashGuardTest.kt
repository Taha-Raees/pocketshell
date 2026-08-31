package app.pocketshell.runtime

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.util.zip.GZIPOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream

/**
 * Regression tests for the v0.2.1 crash fix: the guard guarantees that ANY
 * runtime pipeline failure lands in an honest retryable state instead of
 * escaping the coroutine and killing the process.
 *
 * The v0.2.0-m2.2-wip incident: tapping "Install" on a device without the
 * INTERNET permission threw SecurityException("Permission denied (missing
 * INTERNET permission?)") from the first HTTPS connect; nothing contained it,
 * so Android killed the process (user recording: spinner → instant launcher).
 * Test 1 reproduces exactly that throwable through the REAL installer.
 */
class RuntimeCrashGuardTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // ------------------------------------------------------------- fixtures

    private fun tarGz(build: (TarArchiveOutputStream) -> Unit): ByteArray {
        val raw = ByteArrayOutputStream()
        val gzip = GZIPOutputStream(raw)
        val tar = TarArchiveOutputStream(gzip)
        tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU)
        build(tar)
        tar.finish()
        tar.close()
        return raw.toByteArray()
    }

    private fun addFile(tar: TarArchiveOutputStream, name: String, content: String, mode: Int) {
        val bytes = content.toByteArray()
        val entry = TarArchiveEntry(name)
        entry.setSize(bytes.size.toLong())
        entry.modTime = java.util.Date(0)
        entry.mode = mode
        tar.putArchiveEntry(entry)
        tar.write(bytes)
        tar.closeArchiveEntry()
    }

    private fun addDir(tar: TarArchiveOutputStream, name: String) {
        val entry = TarArchiveEntry(name.trimEnd('/') + "/")
        entry.modTime = java.util.Date(0)
        tar.putArchiveEntry(entry)
        tar.closeArchiveEntry()
    }

    private fun addSymlink(tar: TarArchiveOutputStream, name: String, target: String) {
        val entry = TarArchiveEntry(name, TarArchiveEntry.LF_SYMLINK)
        entry.linkName = target
        entry.modTime = java.util.Date(0)
        tar.putArchiveEntry(entry)
        tar.closeArchiveEntry()
    }

    private fun alpineShapedArchive(): ByteArray = tarGz { tar ->
        addDir(tar, "etc")
        addFile(tar, "etc/alpine-release", "3.24.1\n", 0b110_100_100)
        addDir(tar, "bin")
        addFile(tar, "bin/busybox", "ELF-fixture-busybox", 0b111_101_101)
        addFile(tar, "bin/sh", "ELF-fixture-sh", 0b111_101_101)
        addSymlink(tar, "usr/bin/ash", "../../bin/busybox")
    }

    private fun specFor(archive: ByteArray) = RuntimeInstaller.RootfsSpec(
        url = "https://dl-cdn.invalid/alpine-minirootfs-guard-aarch64.tar.gz",
        expectedSha256 = RuntimeChecksum.sha256Hex(archive),
        expectedSizeBytes = archive.size.toLong(),
        distribution = "Alpine",
        distributionVersion = "3.24.1",
        architecture = "aarch64",
    )

    private fun fakeConnection(bytes: ByteArray): HttpURLConnection =
        object : HttpURLConnection(URL("https://dl-cdn.invalid/rootfs.tar.gz")) {
            override fun connect() {
                connected = true
            }
            override fun disconnect() = Unit
            override fun usingProxy(): Boolean = false
            override fun getContentLengthLong(): Long = bytes.size.toLong()
            override fun getResponseCode(): Int = 200
            override fun getInputStream(): java.io.InputStream = ByteArrayInputStream(bytes)
        }

    private class StateRecorder {
        val states = mutableListOf<RuntimeState>()
        val events = mutableListOf<RuntimeInstallEvent>()

        val lastState: RuntimeState? get() = states.lastOrNull()
        fun failedEvents() = events.filterIsInstance<RuntimeInstallEvent.Failed>()
    }

    // ---------------------------------------------------------------- tests

    @Test
    fun `securityException from connect lands FAILED and never escapes`() = runBlocking {
        // The exact v0.2.0 incident: first HTTPS connect without INTERNET.
        val storage = RuntimeStorage(tmp.newFolder())
        val installer = RuntimeInstaller(storage, connectionOpener = {
            throw SecurityException("Permission denied (missing INTERNET permission?)")
        })
        val rec = StateRecorder()

        RuntimeCrashGuard.install( // must NOT throw — that is the regression
            storage = storage,
            installer = installer,
            spec = RuntimeInstaller.RootfsSpec(
                url = "https://dl-cdn.alpinelinux.org/alpine/v3.24/releases/aarch64/x.tar.gz",
                expectedSha256 = "0".repeat(64),
                expectedSizeBytes = 1L,
                distribution = "Alpine",
                distributionVersion = "3.24.1",
                architecture = "aarch64",
            ),
            onEvent = { rec.events.add(it) },
            onState = { rec.states.add(it) },
            forceState = { rec.states.add(it) },
        )

        assertEquals(RuntimeState.FAILED, rec.lastState)
        val failure = rec.failedEvents().last()
        assertTrue(
            "failure message should name the actual cause",
            failure.message?.contains("Permission denied") == true,
        )
        assertFalse("transient download file must be cleaned", storage.downloadTmp.exists())
        assertFalse("no runtime may appear after failure", storage.runtimeDirExists())
    }

    @Test
    fun `nonException Error still lands FAILED`() = runBlocking {
        val storage = RuntimeStorage(tmp.newFolder())
        val installer = RuntimeInstaller(storage, connectionOpener = {
            throw OutOfMemoryError("oom-drill")
        })
        val rec = StateRecorder()

        RuntimeCrashGuard.install(
            storage = storage,
            installer = installer,
            spec = RuntimeInstaller.RootfsSpec(
                url = "https://dl-cdn.invalid/x.tar.gz",
                expectedSha256 = "0".repeat(64),
                expectedSizeBytes = 1L,
                distribution = "Alpine",
                distributionVersion = "3.24.1",
                architecture = "aarch64",
            ),
            onEvent = { rec.events.add(it) },
            onState = { rec.states.add(it) },
            forceState = { rec.states.add(it) },
        )

        // The installer only catches Exception; Errors reach the guard's net.
        assertEquals(RuntimeState.FAILED, rec.lastState)
        assertTrue(rec.failedEvents().isNotEmpty())
        assertFalse(storage.downloadTmp.exists())
    }

    @Test
    fun `successful install passes through to READY`() = runBlocking {
        val archive = alpineShapedArchive()
        val storage = RuntimeStorage(tmp.newFolder())
        val installer = RuntimeInstaller(storage, connectionOpener = { fakeConnection(archive) })
        val rec = StateRecorder()

        RuntimeCrashGuard.install(
            storage = storage,
            installer = installer,
            spec = specFor(archive),
            onEvent = { rec.events.add(it) },
            onState = { rec.states.add(it) },
            forceState = { rec.states.add(it) },
        )

        assertEquals(RuntimeState.READY, rec.lastState)
        assertTrue(rec.failedEvents().isEmpty())
        assertNotNull(rec.events.filterIsInstance<RuntimeInstallEvent.Ready>().singleOrNull())
        assertTrue(storage.runtimeDirExists())
        assertTrue(storage.structuralProbePasses())
    }

    @Test
    fun `remove with undeletable files lands REPAIR_REQUIRED and never escapes`() {
        val base = tmp.newFolder()
        val storage = RuntimeStorage(base)
        // A "runtime" the user wants gone.
        val rootfs = storage.rootfsDir
        rootfs.mkdirs()
        java.io.File(rootfs, "etc").mkdirs()
        java.io.File(rootfs, "etc/alpine-release").writeText("3.24.1\n")
        // Make the base dir read-only so the runtime dir itself cannot be unlinked.
        val perms = Files.getPosixFilePermissions(base.toPath())
        val readOnly = perms.toMutableSet().apply {
            removeAll(setOf(PosixFilePermission.OWNER_WRITE, PosixFilePermission.OTHERS_WRITE))
        }
        Files.setPosixFilePermissions(base.toPath(), readOnly)
        val rec = StateRecorder()
        try {
            RuntimeCrashGuard.remove( // must NOT throw
                storage = storage,
                onEvent = { rec.events.add(it) },
                onState = { rec.states.add(it) },
                forceState = { rec.states.add(it) },
            )
        } finally {
            Files.setPosixFilePermissions(base.toPath(), perms)
        }

        assertEquals(RuntimeState.REPAIR_REQUIRED, rec.lastState)
        val failure = rec.failedEvents().single()
        assertTrue(failure.stage == RuntimeState.REPAIR_REQUIRED)
        assertTrue("runtime dir must still exist (honest: not fully removed)", storage.runtimeDirExists())
    }

    @Test
    fun `remove success lands NOT_INSTALLED`() {
        val base = tmp.newFolder()
        val storage = RuntimeStorage(base)
        storage.rootfsDir.mkdirs()
        java.io.File(storage.rootfsDir, "marker").writeText("x")
        val rec = StateRecorder()

        RuntimeCrashGuard.remove(
            storage = storage,
            onEvent = { rec.events.add(it) },
            onState = { rec.states.add(it) },
            forceState = { rec.states.add(it) },
        )

        assertEquals(RuntimeState.NOT_INSTALLED, rec.lastState)
        assertTrue(rec.failedEvents().isEmpty())
        assertFalse(storage.runtimeDirExists())
    }

    @Test
    fun `retry from FAILED reaches DOWNLOADING again`() {
        // The recovery path the incident UI depends on: FAILED -> retry.
        assertTrue(RuntimeState.FAILED.canTransitionTo(RuntimeState.DOWNLOADING))
        assertTrue(RuntimeState.canStartInstall(RuntimeState.FAILED))
        assertTrue(RuntimeState.REPAIR_REQUIRED.canTransitionTo(RuntimeState.DOWNLOADING))
        assertTrue(RuntimeState.canStartInstall(RuntimeState.REPAIR_REQUIRED))
    }
}

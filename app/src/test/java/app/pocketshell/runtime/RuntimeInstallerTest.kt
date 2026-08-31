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

class RuntimeInstallerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // ------------------------------------------------------------- fixtures

    private fun tarGz(build: (TarArchiveOutputStream) -> Unit): ByteArray {
        val raw = ByteArrayOutputStream()
        val gzip = GZIPOutputStream(raw)
        val tar = TarArchiveOutputStream(gzip)
        // GNU longname entries — what GNU tar / Alpine minirootfs actually produce.
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

    private fun addLongNameFile(tar: TarArchiveOutputStream, content: String) {
        val deep = "usr/share/doc/" + "subpackage/".repeat(9) + "a-very-long-file-name.txt"
        check(deep.length > 100) { "fixture must exercise GNU longname path" }
        addFile(tar, deep, content, 0b110_100_100)
    }

    /** Minimal-but-honest Alpine-shaped archive: markers + real tar features. */
    private fun alpineShapedArchive(): ByteArray = tarGz { tar ->
        addDir(tar, "etc")
        addFile(tar, "etc/alpine-release", "3.24.1\n", 0b110_100_100)
        addDir(tar, "bin")
        addFile(tar, "bin/busybox", "ELF-fixture-busybox", 0b111_101_101)
        addFile(tar, "bin/sh", "ELF-fixture-sh", 0b111_101_101)
        addSymlink(tar, "usr/bin/ash", "../../bin/busybox")
        addLongNameFile(tar, "long name payload")
    }

    private fun specFor(archive: ByteArray) = RuntimeInstaller.RootfsSpec(
        url = "https://dl-cdn.invalid/alpine-minirootfs-test-aarch64.tar.gz",
        expectedSha256 = RuntimeChecksum.sha256Hex(archive),
        expectedSizeBytes = archive.size.toLong(),
        distribution = "Alpine",
        distributionVersion = "3.24.1",
        architecture = "aarch64",
    )

    private fun fakeConnection(bytes: ByteArray, status: Int = 200): HttpURLConnection =
        object : HttpURLConnection(URL("https://dl-cdn.invalid/rootfs.tar.gz")) {
            override fun connect() = Unit
            override fun disconnect() = Unit
            override fun usingProxy(): Boolean = false
            override fun getContentLengthLong(): Long = bytes.size.toLong()
            override fun getResponseCode(): Int = status
            override fun getInputStream(): java.io.InputStream = ByteArrayInputStream(bytes)
        }

    private fun installer(archive: ByteArray, status: Int = 200): RuntimeInstaller {
        val storage = RuntimeStorage(tmp.newFolder("base-${System.nanoTime()}"))
        return RuntimeInstaller(storage) { fakeConnection(archive, status) }
    }

    // --------------------------------------------------------------- tests

    @Test
    fun `happy path - full pipeline to READY with modes, symlink and metadata`() = runBlocking {
        val archive = alpineShapedArchive()
        val installer = installer(archive)
        val events = mutableListOf<RuntimeInstallEvent>()
        val states = mutableListOf<RuntimeState>()

        val metadata = installer.install(
            specFor(archive),
            onEvent = { events.add(it) },
            onState = { states.add(it) },
        )

        assertEquals("Alpine", metadata.distribution)
        assertEquals(RuntimeState.READY.name, metadata.state)
        assertEquals(
            listOf(
                RuntimeState.DOWNLOADING,
                RuntimeState.VERIFYING,
                RuntimeState.EXTRACTING,
                RuntimeState.CONFIGURING,
                RuntimeState.READY,
            ),
            states,
        )
        assertTrue(events.contains(RuntimeInstallEvent.Ready))

        // storage expectations on the ORIGINAL storage wrapper of this installer
        val storage = installerFieldStorage(installer)
        assertTrue(storage.runtimeDirExists())
        assertEquals(metadata, RuntimeMetadata.read(storage.metadataFile))
        assertTrue(storage.structuralProbePasses())

        // exec bits preserved (0755)
        val perms = Files.getPosixFilePermissions(
            storage.rootfsDir.resolve("bin/busybox").toPath(),
        )
        assertTrue(perms.contains(PosixFilePermission.OWNER_EXECUTE))

        // symlink created
        assertTrue(Files.isSymbolicLink(storage.rootfsDir.resolve("usr/bin/ash").toPath()))

        // GNU long-name entry extracted
        val longFiles = storage.rootfsDir.walkTopDown().filter { it.isFile }
            .map { it.name }.toList()
        assertTrue(longFiles.contains("a-very-long-file-name.txt"))
    }

    @Test
    fun `checksum mismatch fails at VERIFYING and leaves previous runtime untouched`() =
        runBlocking {
            val good = alpineShapedArchive()
            val storage = RuntimeStorage(tmp.newFolder("base-${System.nanoTime()}"))
            // Pre-existing "good" runtime the user depends on.
            java.io.File(storage.rootfsDir, "etc").mkdirs()
            java.io.File(storage.rootfsDir, "etc/sentinel").writeText("precious")

            val tampered = good.copyOf().also { it[it.size / 2] = (it[it.size / 2] + 1).toByte() }
            val installer = RuntimeInstaller(storage) { fakeConnection(tampered) }
            val events = mutableListOf<RuntimeInstallEvent>()

            try {
                installer.install(
                    specFor(good), // spec says the ORIGINAL checksum
                    onEvent = { events.add(it) },
                    onState = {},
                )
                throw AssertionError("expected verification failure")
            } catch (expected: RuntimeInstaller.InstallException) {
                assertEquals(RuntimeState.VERIFYING, expected.stage)
            }

            assertTrue(events.any { it is RuntimeInstallEvent.Failed })
            // old runtime untouched, tmp cleaned
            assertTrue(java.io.File(storage.rootfsDir, "etc/sentinel").isFile)
            assertFalse(storage.downloadTmp.exists())
            assertFalse(storage.extractTmp.exists())
        }

    @Test
    fun `path traversal entry is rejected and staging cleaned`() = runBlocking {
        val evil = tarGz { tar ->
            addFile(tar, "etc/alpine-release", "3.24.1\n", 0b110_100_100)
            addFile(tar, "bin/busybox", "x", 0b111_101_101)
            addFile(tar, "../../../escaped.txt", "evil", 0b110_100_100)
        }
        val storage = RuntimeStorage(tmp.newFolder("base-${System.nanoTime()}"))
        val outside = java.io.File(storage.rootDir.parentFile, "escaped.txt")
        val installer = RuntimeInstaller(storage) { fakeConnection(evil) }

        try {
            installer.install(specFor(evil), onEvent = {}, onState = {})
            throw AssertionError("expected traversal rejection")
        } catch (expected: RuntimeInstaller.InstallException) {
            assertEquals(RuntimeState.EXTRACTING, expected.stage)
        }
        assertFalse("traversal must not escape", outside.exists())
        assertFalse(storage.extractTmp.exists())
    }

    @Test
    fun `http error surfaces as DOWNLOADING failure`() = runBlocking {
        val storage = RuntimeStorage(tmp.newFolder("base-${System.nanoTime()}"))
        val installer = RuntimeInstaller(storage) { fakeConnection(ByteArray(0), status = 503) }
        try {
            installer.install(specFor(ByteArray(1)), onEvent = {}, onState = {})
            throw AssertionError("expected HTTP failure")
        } catch (expected: RuntimeInstaller.InstallException) {
            assertEquals(RuntimeState.DOWNLOADING, expected.stage)
        }
    }

    @Test
    fun `size mismatch against spec fails`() = runBlocking {
        val archive = alpineShapedArchive()
        val spec = specFor(archive).copy(expectedSizeBytes = archive.size.toLong() + 1)
        val installer = installer(archive)
        try {
            installer.install(spec, onEvent = {}, onState = {})
            throw AssertionError("expected size failure")
        } catch (expected: RuntimeInstaller.InstallException) {
            assertEquals(RuntimeState.DOWNLOADING, expected.stage)
        }
    }

    /**
     * The installer's storage is private; recover it reflectively only for
     * assertions (never used by production code).
     */
    private fun installerFieldStorage(installer: RuntimeInstaller): RuntimeStorage {
        val field = RuntimeInstaller::class.java.getDeclaredField("storage")
        field.isAccessible = true
        return field.get(installer) as RuntimeStorage
    }
}

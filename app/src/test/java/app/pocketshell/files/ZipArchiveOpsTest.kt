package app.pocketshell.files

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * M7.2-A — ZIP compress/extract pins, exercised end-to-end through the real
 * Phase 2 [FileDirArea] engine (real temp directories, real bytes).
 *
 * What is pinned:
 *  - entry-name safety: traversal/absolute/NUL/dot components REFUSE before
 *    anything is created (Zip-Slip can never escape the destination);
 *  - compress: single file, multiple files, nested folders (structure
 *    preserved), empty folders (directory entries), symlink skip + warning;
 *  - compress refusals: existing target, archive-inside-source;
 *  - compress honesty: cancelled compress keeps NO archive; committed size
 *    is verified against what was written;
 *  - extract: round-trip byte equality, implied parent directories created,
 *    existing files skipped WITHOUT replace and overwritten ONLY with the
 *    explicit replace choice;
 *  - hostile archives: "../evil" refuses the operation and reports honestly;
 *  - caps: entry-count / total-byte limits stop a possible archive bomb;
 *  - garbage input fails honestly, never throws to the caller.
 */
class ZipArchiveOpsTest {

    private lateinit var root: Path

    private val areaId = AreaId(AreaKind.ANDROID_SHELF)

    @Before
    fun setUp() {
        root = Files.createTempDirectory("pocketshell-zip-area")
    }

    @After
    fun tearDown() {
        root.toFile().deleteRecursively() // test scaffolding only, host-side
    }

    private fun area(): StorageArea = FileDirArea.create(
        root = root.toFile(),
        id = areaId,
        displayName = "Downloads",
        policy = FileDirArea.MutationPolicy.OPEN,
    )!!

    private fun pathOf(raw: String): AreaPath = PathSafety.validatePath(raw)!!

    private fun write(area: StorageArea, rawPath: String, content: ByteArray) {
        val target = pathOf(rawPath)
        // Missing parents are created so tests describe content, not setup.
        val components = target.components.dropLast(1)
        var current = "/"
        for (component in components) {
            current = if (current == "/") "/$component" else "$current/$component"
            if (area.stat(pathOf(current)) == null) {
                assertTrue(area.createDirectory(pathOf(current)).success)
            }
        }
        val result = area.writeBytesAtomic(target, content)
        assertTrue("setup write failed: ${result.reason}", result.success)
    }

    private fun readAll(area: StorageArea, rawPath: String): ByteArray =
        when (val opened = area.openRead(pathOf(rawPath))) {
            is StreamRead.Error -> throw AssertionError(opened.reason)
            is StreamRead.Ok -> opened.stream.use { it.readBytes() }
        }

    /** Entry names of a stored zip (as a plain reader would see them). */
    private fun zipEntryNames(area: StorageArea, rawPath: String): List<String> =
        when (val opened = area.openRead(pathOf(rawPath))) {
            is StreamRead.Error -> throw AssertionError(opened.reason)
            is StreamRead.Ok -> opened.stream.use { input ->
                ZipArchiveInputStream(input.buffered(), "UTF-8").use { zip ->
                    buildList {
                        while (true) {
                            val entry = zip.nextZipEntry ?: break
                            add(entry.name)
                        }
                    }
                }
            }
        }

    // ------------------------------------------------------------ name safety

    @Test
    fun `entry names - ordinary relative names resolve inside the destination`() {
        val destination = pathOf("/extract")
        assertEquals("/extract/file.txt", ZipArchiveOps.entryTarget(destination, "file.txt")!!.value)
        assertEquals(
            "/extract/project/src/Main.kt",
            ZipArchiveOps.entryTarget(destination, "project/src/Main.kt")!!.value,
        )
        assertEquals("/extract/project", ZipArchiveOps.entryTarget(destination, "project/")!!.value)
        // The area root as destination (no fake leading double slash).
        assertEquals("/file.txt", ZipArchiveOps.entryTarget(pathOf("/"), "file.txt")!!.value)
    }

    @Test
    fun `entry names - traversal, absolute and malformed names are rejected`() {
        val destination = pathOf("/extract")
        assertNull(ZipArchiveOps.entryTarget(destination, "../evil.txt"))
        assertNull(ZipArchiveOps.entryTarget(destination, "a/../../evil.txt"))
        assertNull(ZipArchiveOps.entryTarget(destination, "/absolute.txt"))
        assertNull(ZipArchiveOps.entryTarget(destination, ""))
        assertNull(ZipArchiveOps.entryTarget(destination, "a//b.txt"))
        assertNull(ZipArchiveOps.entryTarget(destination, "./here.txt"))
        assertNull(ZipArchiveOps.entryTarget(destination, ".."))
        assertNull(ZipArchiveOps.entryTarget(destination, "bad\u0000name.txt"))
    }

    // --------------------------------------------------------------- compress

    @Test
    fun `compress single file - entry carries the name and exact bytes`() {
        val area = area()
        write(area, "/notes.txt", "hello zip".toByteArray())

        val result = ZipArchiveOps.compress(
            area = area,
            sources = listOf(pathOf("/notes.txt")),
            target = pathOf("/notes.zip"),
        )
        assertTrue(result.message, result.success)
        assertEquals(listOf("notes.txt"), zipEntryNames(area, "/notes.zip"))

        // Round-trip the content through extract.
        area.createDirectory(pathOf("/out"))
        val extracted = ZipArchiveOps.extract(area, pathOf("/notes.zip"), pathOf("/out"))
        assertTrue(extracted.message, extracted.success)
        assertEquals("hello zip", String(readAll(area, "/out/notes.txt")))
    }

    @Test
    fun `compress multiple files and a folder - structure preserved`() {
        val area = area()
        write(area, "/README.md", "# project".toByteArray())
        write(area, "/build.gradle", "plugins {}".toByteArray())
        write(area, "/project/src/Main.kt", "fun main() {}".toByteArray())
        write(area, "/project/README.md", "nested".toByteArray())
        assertTrue(area.createDirectory(pathOf("/project/empty-dir")).success)

        val result = ZipArchiveOps.compress(
            area = area,
            sources = listOf(pathOf("/README.md"), pathOf("/build.gradle"), pathOf("/project")),
            target = pathOf("/bundle.zip"),
        )
        assertTrue(result.message, result.success)

        val names = zipEntryNames(area, "/bundle.zip")
        assertTrue(names.containsAll(
            listOf("README.md", "build.gradle", "project/", "project/src/", "project/src/Main.kt", "project/README.md", "project/empty-dir/"),
        ))

        // Byte-exact round trip of the nested file.
        area.createDirectory(pathOf("/out"))
        val extracted = ZipArchiveOps.extract(area, pathOf("/bundle.zip"), pathOf("/out"))
        assertTrue(extracted.message, extracted.success)
        assertEquals("fun main() {}", String(readAll(area, "/out/project/src/Main.kt")))
        assertTrue(area.stat(pathOf("/out/project/empty-dir"))?.kind == EntryKind.DIRECTORY)
    }

    @Test
    fun `compress refuses an existing target and an archive inside a source folder`() {
        val area = area()
        write(area, "/a.txt", "a".toByteArray())
        write(area, "/old.zip", "stale".toByteArray())

        val existing = ZipArchiveOps.compress(area, listOf(pathOf("/a.txt")), pathOf("/old.zip"))
        assertFalse(existing.success)
        assertTrue(existing.message.contains("already exists"))
        assertEquals("stale", String(readAll(area, "/old.zip")))

        val insideSource = ZipArchiveOps.compress(
            area = area,
            sources = listOf(pathOf("/folder")),
            target = pathOf("/folder/self.zip"),
        )
        // (source /folder does not exist → the stat refusal comes first)
        assertFalse(insideSource.success)

        write(area, "/folder/x.txt", "x".toByteArray())
        val containsItself = ZipArchiveOps.compress(
            area = area,
            sources = listOf(pathOf("/folder")),
            target = pathOf("/folder/self.zip"),
        )
        assertFalse(containsItself.success)
        assertTrue(containsItself.message.contains("inside"))
        assertNull(area.stat(pathOf("/folder/self.zip")))
    }

    @Test
    fun `compress skips symlinks with an honest warning and keeps no archive on cancel`() {
        val area = area()
        write(area, "/real.txt", "real".toByteArray())
        val link = root.resolve("link.txt")
        Files.createSymbolicLink(link, root.resolve("real.txt"))
        assertTrue(area.stat(pathOf("/link.txt"))?.kind == EntryKind.SYMLINK)

        val withLink = ZipArchiveOps.compress(
            area = area,
            sources = listOf(pathOf("/real.txt"), pathOf("/link.txt")),
            target = pathOf("/with-link.zip"),
        )
        assertTrue(withLink.success)
        assertTrue(withLink.warnings.any { it.contains("link.txt") && it.contains("symlink") })

        val cancelled = ZipArchiveOps.compress(
            area = area,
            sources = listOf(pathOf("/real.txt")),
            target = pathOf("/cancelled.zip"),
            isCancelled = { true },
        )
        assertTrue(cancelled.cancelled)
        assertFalse(cancelled.success)
        assertNull(area.stat(pathOf("/cancelled.zip")))
    }

    // ---------------------------------------------------------------- extract

    @Test
    fun `extract creates implied parent directories for zips without dir entries`() {
        val area = area()
        // A zip written by java.util.zip with NO explicit directory entries.
        val rawZip = root.resolve("flat.zip")
        ZipOutputStream(Files.newOutputStream(rawZip)).use { zip ->
            zip.putNextEntry(ZipEntry("deeply/nested/tree/file.txt"))
            zip.write("flat-made".toByteArray())
            zip.closeEntry()
        }
        write(area, "/flat.zip", Files.readAllBytes(rawZip))

        area.createDirectory(pathOf("/here"))
        val result = ZipArchiveOps.extract(area, pathOf("/flat.zip"), pathOf("/here"))
        assertTrue(result.message, result.success)
        assertEquals("flat-made", String(readAll(area, "/here/deeply/nested/tree/file.txt")))
    }

    @Test
    fun `extract never silently overwrites - skip without replace, overwrite only with it`() {
        val area = area()
        write(area, "/data.txt", "NEW".toByteArray())
        val result = ZipArchiveOps.compress(area, listOf(pathOf("/data.txt")), pathOf("/data.zip"))
        assertTrue(result.success)

        // Pre-existing conflicting content at the destination.
        area.createDirectory(pathOf("/dest"))
        write(area, "/dest/data.txt", "OLD".toByteArray())

        val keep = ZipArchiveOps.extract(area, pathOf("/data.zip"), pathOf("/dest"), replace = false)
        assertTrue(keep.success)
        assertEquals("OLD", String(readAll(area, "/dest/data.txt")))
        assertTrue(keep.message.contains("skipped"))
        assertTrue(keep.warnings.any { it.contains("data.txt") })

        val replace = ZipArchiveOps.extract(area, pathOf("/data.zip"), pathOf("/dest"), replace = true)
        assertTrue(replace.message, replace.success)
        assertEquals("NEW", String(readAll(area, "/dest/data.txt")))
    }

    @Test
    fun `extract refuses path traversal fail-closed and reports what happened`() {
        val area = area()
        // Hand-built hostile archive: a legal entry, then a Zip-Slip entry.
        val rawZip = root.resolve("evil.zip")
        ZipOutputStream(Files.newOutputStream(rawZip)).use { zip ->
            zip.putNextEntry(ZipEntry("innocent.txt"))
            zip.write("ok".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("../evil.txt"))
            zip.write("escaped".toByteArray())
            zip.closeEntry()
        }
        write(area, "/evil.zip", Files.readAllBytes(rawZip))

        area.createDirectory(pathOf("/dest"))
        val result = ZipArchiveOps.extract(area, pathOf("/evil.zip"), pathOf("/dest"))
        assertFalse(result.success)
        assertTrue(result.message.contains("REFUSED"))
        assertTrue(result.message.contains("../evil.txt"))
        // The legal entry landed; the traversal did NOT escape the destination.
        assertEquals("ok", String(readAll(area, "/dest/innocent.txt")))
        assertNull(area.stat(pathOf("/evil.txt")))
        assertFalse(Files.exists(root.resolve("evil.txt")))
        assertNull(area.stat(pathOf("/dest/evil.txt")))
    }

    @Test
    fun `extract enforces entry and byte caps (archive-bomb guard)`() {
        val area = area()
        write(area, "/one.txt", "11111".toByteArray())
        write(area, "/two.txt", "22222".toByteArray())
        val compressed = ZipArchiveOps.compress(
            area = area,
            sources = listOf(pathOf("/one.txt"), pathOf("/two.txt")),
            target = pathOf("/two-files.zip"),
        )
        assertTrue(compressed.success)

        area.createDirectory(pathOf("/capped"))
        val entriesCap = ZipArchiveOps.extract(
            area = area,
            zipPath = pathOf("/two-files.zip"),
            destinationDir = pathOf("/capped"),
            limits = ZipArchiveOps.Limits(maxEntries = 1),
        )
        assertFalse(entriesCap.success)
        assertTrue(entriesCap.message.contains("bomb"))
        assertEquals(1, entriesCap.entriesDone)
        assertEquals("11111", String(readAll(area, "/capped/one.txt")))
        assertNull(area.stat(pathOf("/capped/two.txt")))

        area.createDirectory(pathOf("/capped2"))
        val byteCap = ZipArchiveOps.extract(
            area = area,
            zipPath = pathOf("/two-files.zip"),
            destinationDir = pathOf("/capped2"),
            limits = ZipArchiveOps.Limits(maxTotalUncompressedBytes = 6),
        )
        assertFalse(byteCap.success)
        assertTrue(byteCap.message.contains("bomb"))
        assertTrue(area.stat(pathOf("/capped2/one.txt")) != null)
        assertNull(area.stat(pathOf("/capped2/two.txt")))
    }

    @Test
    fun `extract of garbage and of a non-file fails honestly`() {
        val area = area()
        write(area, "/garbage.zip", "this is not a zip".toByteArray())
        area.createDirectory(pathOf("/dest"))

        val garbage = ZipArchiveOps.extract(area, pathOf("/garbage.zip"), pathOf("/dest"))
        assertFalse(garbage.success)
        assertTrue(
            garbage.message,
            garbage.message.contains("not a ZIP archive") || garbage.message.contains("failed"),
        )

        val noDestination = ZipArchiveOps.extract(area, pathOf("/garbage.zip"), pathOf("/missing"))
        assertFalse(noDestination.success)
        assertTrue(noDestination.message.contains("not a folder"))

        write(area, "/folder-thing", "x".toByteArray())
        area.delete(pathOf("/garbage.zip"))
        // rename is per-directory: build the not-a-file case via a directory entry.
        assertTrue(area.createDirectory(pathOf("/dir.zip")).success)
        val notAFile = ZipArchiveOps.extract(area, pathOf("/dir.zip"), pathOf("/dest"))
        assertFalse(notAFile.success)
        assertTrue(notAFile.message.contains("not a file"))
    }

    @Test
    fun `extract is cancellable and reports the partial result`() {
        val area = area()
        write(area, "/one.txt", "first".toByteArray())
        write(area, "/two.txt", "second".toByteArray())
        assertTrue(ZipArchiveOps.compress(
            area, listOf(pathOf("/one.txt"), pathOf("/two.txt")), pathOf("/pair.zip"),
        ).success)

        area.createDirectory(pathOf("/dest"))
        // Cancel only AFTER the first entry has fully landed (progress-driven):
        // isCancelled is consulted mid-entry too, so an early flag would stop
        // inside entry one.
        var cancelNow = false
        val result = ZipArchiveOps.extract(
            area = area,
            zipPath = pathOf("/pair.zip"),
            destinationDir = pathOf("/dest"),
            isCancelled = { cancelNow },
            onProgress = { if (it.entriesDone >= 1) cancelNow = true },
        )
        assertTrue(result.cancelled)
        assertTrue(result.message.contains("cancelled"))
        assertTrue(result.message.contains("not fully extracted"))
        // The first entry landed; the count in the message is honest.
        assertEquals(1, result.entriesDone)
        assertEquals("first", String(readAll(area, "/dest/one.txt")))
        assertNull(area.stat(pathOf("/dest/two.txt")))
    }

    @Test
    fun `isZipName is honest about the extension`() {
        assertTrue(ZipArchiveOps.isZipName("archive.zip"))
        assertTrue(ZipArchiveOps.isZipName("ARCHIVE.ZIP"))
        assertTrue(ZipArchiveOps.isZipName("archive.Zip"))
        assertFalse(ZipArchiveOps.isZipName("archive.tgz"))
        assertFalse(ZipArchiveOps.isZipName("zip"))
        assertFalse(ZipArchiveOps.isZipName("archive.zip.txt"))
    }

    @Test
    fun `empty archive extracts to nothing, honestly`() {
        val area = area()
        val rawZip = root.resolve("empty.zip")
        ZipOutputStream(Files.newOutputStream(rawZip)).use { zip ->
            zip.finish()
        }
        write(area, "/empty.zip", Files.readAllBytes(rawZip))
        area.createDirectory(pathOf("/dest"))
        val result = ZipArchiveOps.extract(area, pathOf("/empty.zip"), pathOf("/dest"))
        assertTrue(result.message, result.success)
        assertEquals(0, result.entriesDone)
    }

    // ------------------------------------------------------------- progress

    @Test
    fun `compress progress reports known totals for plain files and bytes for entries`() {
        val area = area()
        write(area, "/a.bin", ByteArray(300_000))
        write(area, "/b.bin", ByteArray(200_000))
        var last: ZipArchiveOps.Progress? = null
        val result = ZipArchiveOps.compress(
            area = area,
            sources = listOf(pathOf("/a.bin"), pathOf("/b.bin")),
            target = pathOf("/progress.zip"),
            onProgress = { last = it },
        )
        assertTrue(result.success)
        assertNotNull(last)
        assertEquals(500_000L, last!!.totalBytes)
        assertEquals(500_000L, last!!.bytesDone)
        assertEquals(2, last!!.entriesDone)
        assertNotNull(last!!.currentName)
    }
}

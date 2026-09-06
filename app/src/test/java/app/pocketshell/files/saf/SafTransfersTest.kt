package app.pocketshell.files.saf

import app.pocketshell.files.AreaId
import app.pocketshell.files.AreaKind
import app.pocketshell.files.FileDirArea
import app.pocketshell.files.PathSafety
import app.pocketshell.files.StorageArea
import app.pocketshell.files.StreamRead
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * M7.0.0 Phase 5 pins — the verified import/export/share bridges over REAL
 * temporary [FileDirArea]s (and one SAF area over the fake backend), so the
 * stream-injected transfer core is exercised exactly as the ViewModel wires
 * it on device.
 *
 * Pinned: byte-exact verified imports (incl. into a SAF area), the Phase 4
 * Replace composition for collisions, verify-failure cleanup (bad copy
 * removed / created export deleted), directory refusal, and the share
 * staging contract (byte-exact, self-cleaning, files-only).
 */
class SafTransfersTest {

    private lateinit var rootfs: Path
    private lateinit var stagingDir: Path

    private val guestId = AreaId(AreaKind.GUEST_LINUX)

    @Before
    fun setUp() {
        rootfs = Files.createTempDirectory("pocketshell-saf-rootfs")
        stagingDir = Files.createTempDirectory("pocketshell-saf-staging")
        Files.createDirectories(rootfs.resolve("root"))
    }

    @After
    fun tearDown() {
        rootfs.toFile().deleteRecursively() // test scaffolding only, host-side
        stagingDir.toFile().deleteRecursively()
    }

    private fun guestArea(): StorageArea =
        FileDirArea.create(
            root = rootfs.toFile(),
            id = guestId,
            displayName = "PocketShell Linux",
            policy = FileDirArea.MutationPolicy.guestRuntime(),
        )!!

    private fun safArea(backend: FakeDocumentBackend): AndroidDocumentArea =
        AndroidDocumentArea.create(
            id = AndroidDocumentArea.areaIdFor("content://test/tree/primary%3AMyProject"),
            displayName = "MyProject",
            backend = backend,
        )

    private fun path(raw: String) = PathSafety.validatePath(raw)!!

    /** Wraps an area but serves poisoned bytes on re-read (verify breaker). */
    private class CorruptReadArea(private val inner: StorageArea) : StorageArea by inner {
        override fun openRead(path: app.pocketshell.files.AreaPath): StreamRead =
            StreamRead.Ok(ByteArrayInputStream("corrupt!!".toByteArray()))
    }

    // -------------------------------------------------------------- import

    @Test
    fun `import copies byte-exact into the current directory and verifies`() {
        val area = guestArea()
        val payload = ByteArray(200_000) { (it % 251).toByte() }

        val result = SafTransfers.importDocument(
            targetArea = area,
            target = path("/root/project.zip"),
            openSource = { ByteArrayInputStream(payload) },
            replace = false,
        )

        assertTrue(result.success)
        assertEquals(payload.size.toLong(), result.bytesCopied)
        assertTrue(payload.contentEquals(area.readBytes(path("/root/project.zip"), 1_000_000).let {
            (it as app.pocketshell.files.ReadResult.Ok).bytes
        }))
    }

    @Test
    fun `import works into a SAF area too`() {
        val backend = FakeDocumentBackend()
        backend.mkdir("docs")
        val area = safArea(backend)
        val payload = "saf import".toByteArray()

        val result = SafTransfers.importDocument(
            targetArea = area,
            target = path("/docs/note.txt"),
            openSource = { ByteArrayInputStream(payload) },
            replace = false,
        )

        assertTrue(result.success)
        assertTrue(payload.contentEquals(backend.content("/docs/note.txt")!!))
    }

    @Test
    fun `import refuses an existing destination without replace and keeps it intact`() {
        val area = guestArea()
        Files.write(rootfs.resolve("root/project.zip"), "the original".toByteArray())

        val result = SafTransfers.importDocument(
            targetArea = area,
            target = path("/root/project.zip"),
            openSource = { ByteArrayInputStream("new bytes".toByteArray()) },
            replace = false,
        )

        assertFalse(result.success)
        assertTrue(result.reason!!.contains("already exists"))
        assertEquals("the original", String(Files.readAllBytes(rootfs.resolve("root/project.zip"))))
    }

    @Test
    fun `import with explicit replace deletes then imports the same uniform way`() {
        val area = guestArea()
        Files.write(rootfs.resolve("root/project.zip"), "the original".toByteArray())

        val result = SafTransfers.importDocument(
            targetArea = area,
            target = path("/root/project.zip"),
            openSource = { ByteArrayInputStream("replacement".toByteArray()) },
            replace = true,
        )

        assertTrue(result.success)
        assertEquals("replacement", String(Files.readAllBytes(rootfs.resolve("root/project.zip"))))
    }

    @Test
    fun `a failed import verification removes the copy and reports honestly`() {
        val honest = guestArea()
        val observed = CorruptReadArea(honest)
        val payload = "important data".toByteArray()

        val result = SafTransfers.importDocument(
            targetArea = observed,
            target = path("/root/incoming.zip"),
            openSource = { ByteArrayInputStream(payload) },
            replace = false,
        )

        assertFalse(result.success)
        assertTrue(result.reason!!.contains("verification"))
        assertNull("the bad copy must be removed", honest.stat(path("/root/incoming.zip")))
    }

    @Test
    fun `an unreadable source is an honest failure with nothing written`() {
        val area = guestArea()

        val result = SafTransfers.importDocument(
            targetArea = area,
            target = path("/root/nothing.zip"),
            openSource = { throw java.io.FileNotFoundException("picker doc vanished") },
            replace = false,
        )

        assertFalse(result.success)
        assertTrue(result.reason!!.contains("could not open the selected file"))
        assertNull(area.stat(path("/root/nothing.zip")))
    }

    // -------------------------------------------------------------- export

    @Test
    fun `export streams the file out and verifies the read-back`() {
        val area = guestArea()
        val payload = ByteArray(150_000) { (it % 199).toByte() }
        area.writeBytesAtomic(path("/root/build.zip"), payload)
        val captured = mutableListOf<ByteArray>()

        val result = SafTransfers.exportDocument(
            sourceArea = area,
            source = path("/root/build.zip"),
            openOutput = {
                object : java.io.ByteArrayOutputStream() {
                    override fun close() {
                        captured.add(toByteArray())
                    }
                }
            },
            openVerify = { ByteArrayInputStream(captured.last()) },
        )

        assertTrue(result.success)
        assertEquals(payload.size.toLong(), result.bytesCopied)
        assertTrue(payload.contentEquals(captured.single()))
    }

    @Test
    fun `export refuses directories`() {
        val area = guestArea()
        Files.createDirectories(rootfs.resolve("root/projects"))

        val result = SafTransfers.exportDocument(
            sourceArea = area,
            source = path("/root/projects"),
            openOutput = { throw IllegalStateException("never called") },
            openVerify = { throw IllegalStateException("never called") },
        )

        assertFalse(result.success)
        assertTrue(result.reason!!.contains("only files"))
    }

    @Test
    fun `a failed export verification removes the created document`() {
        val area = guestArea()
        area.writeBytesAtomic(path("/root/app.zip"), "real bytes".toByteArray())
        val removed = AtomicInteger(0)

        val result = SafTransfers.exportDocument(
            sourceArea = area,
            source = path("/root/app.zip"),
            openOutput = {
                object : java.io.ByteArrayOutputStream() {
                    override fun close() { /* the "created" document */ }
                }
            },
            openVerify = { ByteArrayInputStream("different bytes".toByteArray()) },
            onRemoveCreated = { removed.incrementAndGet() },
        )

        assertFalse(result.success)
        assertEquals(1, removed.get())
        assertTrue(result.reason!!.contains("verification"))
    }

    // -------------------------------------------------------- share staging

    @Test
    fun `share staging copies byte-exact into the staging directory`() {
        val area = guestArea()
        val payload = "share me".toByteArray()
        area.writeBytesAtomic(path("/root/archive.zip"), payload)

        val staged = FileShareOps.stageForShare(area, path("/root/archive.zip"), stagingDir.toFile())

        assertTrue(staged is FileShareOps.Staging.Ok)
        assertEquals("archive.zip", (staged as FileShareOps.Staging.Ok).file.name)
        assertTrue(payload.contentEquals(staged.file.readBytes()))
    }

    @Test
    fun `share staging self-cleans previous staged files`() {
        val area = guestArea()
        area.writeBytesAtomic(path("/root/new.zip"), "new".toByteArray())
        Files.write(stagingDir.resolve("stale.zip"), "stale".toByteArray())

        val staged = FileShareOps.stageForShare(area, path("/root/new.zip"), stagingDir.toFile())

        assertTrue(staged is FileShareOps.Staging.Ok)
        assertEquals(listOf("new.zip"), stagingDir.toFile().list()!!.sorted())
    }

    @Test
    fun `share staging refuses directories and missing files honestly`() {
        val area = guestArea()
        Files.createDirectories(rootfs.resolve("root/projects"))

        val dirResult = FileShareOps.stageForShare(area, path("/root/projects"), stagingDir.toFile())
        assertTrue(dirResult is FileShareOps.Staging.Error)
        assertTrue((dirResult as FileShareOps.Staging.Error).reason.contains("only files"))

        val missing = FileShareOps.stageForShare(area, path("/root/gone.zip"), stagingDir.toFile())
        assertTrue(missing is FileShareOps.Staging.Error)
    }

    // ------------------------------------------------------------- mime map

    @Test
    fun `mime guessing stays honest for known and unknown extensions`() {
        assertEquals("application/zip", FileShareOps.guessMimeType("archive.ZIP"))
        assertEquals("application/pdf", FileShareOps.guessMimeType("paper.pdf"))
        assertEquals("text/plain", FileShareOps.guessMimeType("notes.txt"))
        assertEquals("image/png", FileShareOps.guessMimeType("shot.png"))
        assertEquals("application/octet-stream", FileShareOps.guessMimeType("data.bin"))
        assertEquals("application/octet-stream", FileShareOps.guessMimeType("noextension"))
    }
}

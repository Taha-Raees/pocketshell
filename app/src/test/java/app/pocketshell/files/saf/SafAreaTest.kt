package app.pocketshell.files.saf

import app.pocketshell.files.AreaCapability
import app.pocketshell.files.AreaKind
import app.pocketshell.files.OpResult
import app.pocketshell.files.PathSafety
import app.pocketshell.files.ReadResult
import app.pocketshell.files.ListResult
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * M7.0.0 Phase 5 pins — [AndroidDocumentArea] over the [DocumentBackend]
 * seam, driven by an in-memory provider fake. This is the same production
 * area class the device will run, exercised end-to-end:
 *
 *  - capability behavior (the full honest set — no faked support);
 *  - path resolution against display names (never POSIX, never faked);
 *  - create/read/write/rename/move/copy/delete contracts and collisions
 *    (engine parity with the file-backed areas);
 *  - the write-swap discipline: a failed commit RESTORES the previous file;
 *  - revocation: SecurityException becomes an honest error + exactly ONE
 *    access-lost callback, never a crash, never a fake empty folder;
 *  - the SAF shape: no symlink kind is ever reported.
 */
class SafAreaTest {

    private lateinit var backend: FakeDocumentBackend
    private lateinit var area: AndroidDocumentArea
    private val accessLostCount = AtomicInteger(0)

    private val areaId = AndroidDocumentArea.areaIdFor("content://test/tree/primary%3AMyProject")

    @Before
    fun setUp() {
        backend = FakeDocumentBackend(rootName = "MyProject")
        accessLostCount.set(0)
        area = AndroidDocumentArea.create(
            id = areaId,
            displayName = "MyProject",
            backend = backend,
            onAccessLost = { accessLostCount.incrementAndGet() },
        )
    }

    private fun path(raw: String) = PathSafety.validatePath(raw)!!

    // ------------------------------------------------------- identity/model

    @Test
    fun `area identity is the document-tree kind keyed by the uri`() {
        assertEquals(AreaKind.ANDROID_DOCUMENT_TREE, area.id.kind)
        assertEquals("content://test/tree/primary%3AMyProject", area.id.key)
    }

    @Test
    fun `capabilities advertise every genuinely implemented operation`() {
        assertEquals(
            setOf(
                AreaCapability.LIST,
                AreaCapability.READ,
                AreaCapability.WRITE,
                AreaCapability.CREATE_FILE,
                AreaCapability.CREATE_DIR,
                AreaCapability.RENAME,
                AreaCapability.COPY,
                AreaCapability.MOVE,
                AreaCapability.DELETE,
            ),
            area.capabilities,
        )
    }

    // ------------------------------------------------------------- listing

    @Test
    fun `list returns created entries directories-first case-insensitive`() {
        backend.write("/zeta.txt", "z".toByteArray())
        backend.mkdir("Beta")
        backend.write("/alpha.txt", "a".toByteArray())
        backend.mkdir("assets")

        val listing = area.list(path("/"))
        assertTrue(listing is ListResult.Ok)
        val names = (listing as ListResult.Ok).entries.map { it.name }
        assertEquals(listOf("assets", "Beta", "alpha.txt", "zeta.txt"), names)
    }

    @Test
    fun `saf entries are never reported as symlinks`() {
        backend.write("/plain.txt", "x".toByteArray())
        val listing = area.list(path("/")) as ListResult.Ok
        assertTrue(listing.entries.none { it.kind == app.pocketshell.files.EntryKind.SYMLINK })
    }

    @Test
    fun `stat resolves nested paths against display names`() {
        backend.mkdir("src/main")
        backend.write("src/main/app.zip", ByteArray(11))

        val entry = area.stat(path("/src/main/app.zip"))
        assertEquals("app.zip", entry?.name)
        assertEquals(11L, entry?.sizeBytes)
        assertNull(area.stat(path("/src/main/missing")))
        assertNull(area.stat(path("/no/such/dir")))
    }

    // --------------------------------------------------------- create/write

    @Test
    fun `create file and directory refuse an existing target engine parity`() {
        backend.write("/keep.txt", "old".toByteArray())
        val collision = area.createFile(path("/keep.txt"))
        assertEquals(OpResult.Kind.FAILED, collision.kind)
        assertTrue(collision.reason!!.contains("already exists"))
        assertEquals("old", String(backend.content("/keep.txt")!!))

        backend.mkdir("docs")
        val dirCollision = area.createDirectory(path("/docs"))
        assertEquals(OpResult.Kind.FAILED, dirCollision.kind)
    }

    @Test
    fun `writeBytesAtomic creates then replaces with no temp leftovers`() {
        val created = area.writeBytesAtomic(path("/notes.txt"), "v1".toByteArray())
        assertTrue(created.success)
        assertEquals("v1", String(backend.content("/notes.txt")!!))

        val replaced = area.writeBytesAtomic(path("/notes.txt"), "version two".toByteArray())
        assertTrue(replaced.success)
        assertEquals("version two", String(backend.content("/notes.txt")!!))

        val names = (area.list(path("/")) as ListResult.Ok).entries.map { it.name }
        assertTrue(names.none { it.startsWith(".pp-") })
        assertEquals(listOf("notes.txt"), names)
    }

    @Test
    fun `a failed write commit restores the previous file intact`() {
        backend.write("/precious.txt", "original bytes".toByteArray())
        // Simulate a provider refusing the temp→final rename swap.
        backend.failNextRename = "provider busy"

        val opened = area.openWriteAtomic(path("/precious.txt"))
        assertTrue(opened is app.pocketshell.files.StreamWriteOpen.Ok)
        (opened as app.pocketshell.files.StreamWriteOpen.Ok).session.use { session ->
            session.stream.write("NEW CONTENT".toByteArray())
            val commit = session.commit()
            assertFalse(commit.success)
        }

        assertEquals("original bytes", String(backend.content("/precious.txt")!!))
        val names = (area.list(path("/")) as ListResult.Ok).entries.map { it.name }
        assertTrue(names.none { it.startsWith(".pp-") })
    }

    @Test
    fun `a swap failure mid-commit restores the backup and reports it`() {
        backend.write("/report.txt", "the real report".toByteArray())
        // The backup staging (".pp-old-…") succeeds; only the temp→final
        // rename onto "report.txt" fails — the restore path must fire.
        backend.failRenameFor = "report.txt"

        val opened = area.openWriteAtomic(path("/report.txt"))
        assertTrue(opened is app.pocketshell.files.StreamWriteOpen.Ok)
        val commit = (opened as app.pocketshell.files.StreamWriteOpen.Ok).session.let { session ->
            session.stream.write("half-written garbage".toByteArray())
            val result = session.commit()
            session.close()
            result
        }
        assertFalse(commit.success)
        assertTrue(commit.reason!!.contains("restored"))

        assertEquals("the real report", String(backend.content("/report.txt")!!))
        val names = (area.list(path("/")) as ListResult.Ok).entries.map { it.name }
        assertTrue(names.none { it.startsWith(".pp-") })
    }

    @Test
    fun `a provider that alters the requested name fails the write honestly`() {
        backend.alterRenameResults = true
        val result = area.writeBytesAtomic(path("/report.txt"), "body".toByteArray())
        assertFalse(result.success)
        assertTrue(result.reason!!.contains("provider"))
    }

    @Test
    fun `writing over a directory refuses honestly`() {
        backend.mkdir("bundle")
        val result = area.writeBytesAtomic(path("/bundle"), "nope".toByteArray())
        assertFalse(result.success)
        assertTrue(result.reason!!.contains("folder"))
    }

    // --------------------------------------------------------------- reads

    @Test
    fun `readBytes round-trips and honors the cap with TooLarge`() {
        backend.write("/data.bin", ByteArray(32) { it.toByte() })

        val full = area.readBytes(path("/data.bin"), maxBytes = 64)
        assertTrue(full is ReadResult.Ok)
        assertEquals(32, (full as ReadResult.Ok).bytes.size)

        val tooLarge = area.readBytes(path("/data.bin"), maxBytes = 8)
        assertTrue(tooLarge is ReadResult.TooLarge)
        assertEquals(32L, (tooLarge as ReadResult.TooLarge).sizeBytes)

        assertTrue(area.readBytes(path("/missing"), 10) is ReadResult.Error)
        assertTrue(area.readBytes(path("/"), 10) is ReadResult.Error)
    }

    // -------------------------------------------------------------- rename

    @Test
    fun `rename works refuses collisions and invalid names`() {
        backend.write("/old.txt", "x".toByteArray())

        assertTrue(area.rename(path("/old.txt"), "new.txt").success)
        assertTrue(backend.exists("/new.txt"))
        assertFalse(backend.exists("/old.txt"))

        backend.write("/taken.txt", "y".toByteArray())
        val collision = area.rename(path("/new.txt"), "taken.txt")
        assertEquals(OpResult.Kind.FAILED, collision.kind)

        val invalid = area.rename(path("/new.txt"), "a/b")
        assertEquals(OpResult.Kind.FAILED, invalid.kind)

        val rootRename = area.rename(path("/"), "root2")
        assertEquals(OpResult.Kind.DENIED, rootRename.kind)
    }

    // ----------------------------------------------------------- copy/move

    @Test
    fun `copy is recursive byte-exact and refuses collisions and dir-into-self`() {
        backend.write("src/a.txt", "AAA".toByteArray())
        backend.write("src/sub/b.txt", "BBB".toByteArray())

        assertTrue(area.copy(path("/src"), path("/dst")).success)
        assertEquals("AAA", String(backend.content("/dst/a.txt")!!))
        assertEquals("BBB", String(backend.content("/dst/sub/b.txt")!!))

        val collision = area.copy(path("/src"), path("/dst"))
        assertEquals(OpResult.Kind.FAILED, collision.kind)

        val intoSelf = area.copy(path("/src"), path("/src/inner"))
        assertEquals(OpResult.Kind.FAILED, intoSelf.kind)
        assertFalse(backend.exists("/src/inner"))

        val same = area.copy(path("/src"), path("/src"))
        assertEquals(OpResult.Kind.FAILED, same.kind)
    }

    @Test
    fun `move renames inside a parent and moves across parents`() {
        backend.mkdir("inbox")
        backend.mkdir("archive")
        backend.write("inbox/item.zip", "ZIP".toByteArray())

        // Same parent → rename path.
        assertTrue(area.move(path("/inbox/item.zip"), path("/inbox/renamed.zip")).success)
        assertTrue(backend.exists("/inbox/renamed.zip"))

        // Different parent → moveDocument path.
        assertTrue(area.move(path("/inbox/renamed.zip"), path("/archive/done.zip")).success)
        assertTrue(backend.exists("/archive/done.zip"))
        assertFalse(backend.exists("/inbox/renamed.zip"))

        // Collision refuses (done.zip already sits in archive after the move).
        backend.write("inbox/new.zip", "NEW".toByteArray())
        val collision = area.move(path("/inbox/new.zip"), path("/archive/done.zip"))
        assertEquals(OpResult.Kind.FAILED, collision.kind)
        assertEquals("NEW", String(backend.content("/inbox/new.zip")!!))
    }

    // -------------------------------------------------------------- delete

    @Test
    fun `delete removes files and directory trees and refuses the root`() {
        backend.write("tree/a.txt", "A".toByteArray())
        backend.write("tree/sub/b.txt", "B".toByteArray())

        assertTrue(area.delete(path("/tree/a.txt")).success)
        assertFalse(backend.exists("/tree/a.txt"))

        assertTrue(area.delete(path("/tree")).success)
        assertFalse(backend.exists("/tree"))

        val rootDelete = area.delete(path("/"))
        assertEquals(OpResult.Kind.DENIED, rootDelete.kind)
    }

    // ---------------------------------------------------------- revocation

    @Test
    fun `revocation becomes honest errors and exactly one access-lost callback`() {
        backend.revoked = true

        assertTrue(area.list(path("/")) is ListResult.Error)
        assertNull(area.stat(path("/")))
        assertEquals(OpResult.Kind.FAILED, area.delete(path("/whatever")).kind)
        assertEquals(OpResult.Kind.FAILED, area.createFile(path("/whatever")).kind)
        assertTrue(area.readBytes(path("/whatever"), 10) is ReadResult.Error)

        // One honest announcement per area instance — not a callback storm.
        assertEquals(1, accessLostCount.get())
    }

    @Test
    fun `a folder revoked BEFORE construction still exists honestly`() {
        backend.revoked = true
        val revokedArea = AndroidDocumentArea.create(
            id = areaId,
            displayName = "MyProject",
            backend = backend,
        )
        assertFalse(revokedArea.startsAvailable)
        val listing = revokedArea.list(path("/"))
        assertTrue(listing is ListResult.Error)
        assertTrue((listing as ListResult.Error).reason.contains("no longer available"))
    }

    @Test
    fun `revocation mid-session keeps the last message honest not a crash`() {
        backend.write("/f.txt", "x".toByteArray())
        assertTrue(area.readBytes(path("/f.txt"), 10) is ReadResult.Ok)
        backend.revoked = true
        val after = area.readBytes(path("/f.txt"), 10)
        assertTrue(after is ReadResult.Error)
        assertTrue((after as ReadResult.Error).reason.contains("no longer available"))
        assertEquals(1, accessLostCount.get())
    }
}

package app.pocketshell.files

import java.nio.file.Files
import java.nio.file.Path
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * M7.0.0 Phase 4 pins — the file-operation layer over REAL temporary
 * directories (the Phase 2 [FileDirArea] engine underneath, so every rule
 * here is exercised end-to-end through the storage abstraction — the ops
 * layer never touches java.io itself).
 *
 * What is pinned:
 *  - same-area copy/move through the ops layer, source kept for copy;
 *  - cross-area VERIFIED copy/move, source kept for copy / deleted for move;
 *  - collision refusal WITHOUT replace — including the cross-area gate that
 *    prevents CrossArea's by-design overwrite from ever firing silently;
 *  - explicit overwrite ONLY with replace (delete-then-perform composition);
 *  - protected runtime denial (guest policy) surfaced as refused outcomes;
 *  - invalid names refused honestly;
 *  - symlink deletion keeps the target (NOFOLLOW), directory deletion takes
 *    the contents, escaping links inside a deleted tree keep their targets;
 *  - the refresh-after-operation sequence the ViewModel uses
 *    (ops mutate → core.refresh() → the new listing is visible).
 */
class ExplorerOpsTest {

    private lateinit var rootfs: Path
    private lateinit var shelf: Path

    private val guestId = AreaId(AreaKind.GUEST_LINUX)
    private val shelfId = AreaId(AreaKind.ANDROID_SHELF)

    @Before
    fun setUp() {
        rootfs = Files.createTempDirectory("pocketshell-ops-rootfs")
        shelf = Files.createTempDirectory("pocketshell-ops-shelf")
        Files.createDirectories(rootfs.resolve("root"))
        Files.createDirectories(rootfs.resolve("etc"))
        Files.createDirectories(shelf.resolve("docs"))
    }

    @After
    fun tearDown() {
        rootfs.toFile().deleteRecursively() // test scaffolding only, host-side
        shelf.toFile().deleteRecursively()
    }

    private fun guestArea(): StorageArea =
        FileDirArea.create(
            root = rootfs.toFile(),
            id = guestId,
            displayName = "PocketShell Linux",
            policy = FileDirArea.MutationPolicy.guestRuntime(),
        )!!

    private fun shelfArea(): StorageArea =
        FileDirArea.create(
            root = shelf.toFile(),
            id = shelfId,
            displayName = StorageAreas.Labels.SHELF_DISPLAY_NAME,
            policy = FileDirArea.MutationPolicy.OPEN,
        )!!

    private fun guestPath(raw: String): AreaPath = PathSafety.validatePath(raw)!!
    private fun shelfPath(raw: String): AreaPath = PathSafety.validatePath(raw)!!

    private fun pending(
        areaId: AreaId,
        raw: String,
        name: String? = null,
        move: Boolean = false,
        label: String = "Linux",
    ): PendingTransfer {
        val path = PathSafety.validatePath(raw)!!
        return PendingTransfer(
            areaId = areaId,
            areaLabel = label,
            path = path,
            name = name ?: path.components.last(),
            kind = EntryKind.FILE,
            move = move,
        )
    }

    private fun textOf(area: StorageArea, raw: String): String {
        val read = area.readBytes(PathSafety.validatePath(raw)!!, 1 shl 20)
        check(read is ReadResult.Ok) { "expected readable $raw: $read" }
        return String(read.bytes)
    }

    // ------------------------------------------------------------ name logic

    @Test
    fun `composeChild builds valid children and refuses invalid names`() {
        val dir = guestPath("/root")

        assertEquals("/root/notes.txt", ExplorerOps.composeChild(dir, "notes.txt")!!.value)
        assertEquals("/root/.env", ExplorerOps.composeChild(dir, ".env")!!.value)
        assertEquals("/bin-link", ExplorerOps.composeChild(guestPath("/"), "bin-link")!!.value)

        assertNull(ExplorerOps.composeChild(dir, ".."))
        assertNull(ExplorerOps.composeChild(dir, "."))
        assertNull(ExplorerOps.composeChild(dir, "a/b"))
        assertNull(ExplorerOps.composeChild(dir, ""))
        assertNull(ExplorerOps.composeChild(dir, " "))
        assertNull(ExplorerOps.composeChild(dir, "nu\u0000l"))
    }

    @Test
    fun `nameError explains invalid names and accepts dotfiles`() {
        assertNull(ExplorerOps.nameError(".env"))
        assertNull(ExplorerOps.nameError("notes.txt"))

        assertTrue(ExplorerOps.nameError("")!!.isNotEmpty())
        assertTrue(ExplorerOps.nameError("  ")!!.isNotEmpty())
        assertTrue(ExplorerOps.nameError("..")!!.contains("reserved"))
        assertTrue(ExplorerOps.nameError("a/b")!!.contains("single name"))
        assertTrue(ExplorerOps.nameError("x".repeat(256))!!.contains("too long"))
        assertTrue(ExplorerOps.nameError("nu\u0000l")!!.isNotEmpty())
    }

    // ------------------------------------------------------------ paste check

    @Test
    fun `checkPaste is clear when the destination is free`() {
        val guest = guestArea()
        // Source in a subdirectory; the composed destination /root/notes.txt is free.
        val check = ExplorerOps.checkPaste(
            guest,
            pending(guestId, "/root/sub/notes.txt"),
            guest,
            guestPath("/root"),
        )
        assertTrue("expected Clear, got $check", check is ExplorerOps.PasteCheck.Clear)
        assertEquals("/root/notes.txt", (check as ExplorerOps.PasteCheck.Clear).target.value)
    }

    @Test
    fun `checkPaste reports collisions with the existing entry kind`() {
        val guest = guestArea()
        Files.write(rootfs.resolve("root/notes.txt"), "old".toByteArray())
        Files.createDirectories(rootfs.resolve("root/folder"))

        val fileCheck = ExplorerOps.checkPaste(
            guest, pending(guestId, "/root/other/notes.txt"), guest, guestPath("/root"),
        )
        assertTrue(fileCheck is ExplorerOps.PasteCheck.Collision)
        assertEquals(EntryKind.FILE, (fileCheck as ExplorerOps.PasteCheck.Collision).existing.kind)

        val dirCheck = ExplorerOps.checkPaste(
            guest, pending(guestId, "/root/other/folder"), guest, guestPath("/root"),
        )
        assertTrue(dirCheck is ExplorerOps.PasteCheck.Collision)
        assertEquals(EntryKind.DIRECTORY, (dirCheck as ExplorerOps.PasteCheck.Collision).existing.kind)
    }

    @Test
    fun `checkPaste refuses pasting onto the source itself`() {
        val guest = guestArea()
        Files.write(rootfs.resolve("root/notes.txt"), "old".toByteArray())

        val check = ExplorerOps.checkPaste(
            guest, pending(guestId, "/root/notes.txt"), guest, guestPath("/root"),
        )
        assertEquals(ExplorerOps.PasteCheck.SameAsSource, check)
    }

    // ------------------------------------------------- same-area paste copy

    @Test
    fun `same-area paste copy places a copy and keeps the source`() {
        val guest = guestArea()
        Files.write(rootfs.resolve("root/notes.txt"), "hello notes".toByteArray())

        val outcome = ExplorerOps.executePaste(
            guest,
            pending(guestId, "/root/notes.txt"),
            guest,
            guestPath("/root/copy.txt"),
            replace = false,
        )

        assertTrue(outcome.message, outcome.success)
        assertFalse(outcome.refused)
        assertEquals("hello notes", textOf(guest, "/root/copy.txt"))
        assertEquals("hello notes", textOf(guest, "/root/notes.txt"))
    }

    @Test
    fun `same-area paste copy refuses a collision without replace and changes nothing`() {
        val guest = guestArea()
        Files.write(rootfs.resolve("root/notes.txt"), "new bytes".toByteArray())
        Files.write(rootfs.resolve("root/copy.txt"), "precious old".toByteArray())

        val outcome = ExplorerOps.executePaste(
            guest,
            pending(guestId, "/root/notes.txt"),
            guest,
            guestPath("/root/copy.txt"),
            replace = false,
        )

        assertFalse(outcome.success)
        assertEquals("precious old", textOf(guest, "/root/copy.txt"))
        assertEquals("new bytes", textOf(guest, "/root/notes.txt"))
    }

    @Test
    fun `same-area paste copy replace overwrites the existing target and keeps the source`() {
        val guest = guestArea()
        Files.write(rootfs.resolve("root/notes.txt"), "fresh content".toByteArray())
        Files.write(rootfs.resolve("root/copy.txt"), "old content".toByteArray())

        val outcome = ExplorerOps.executePaste(
            guest,
            pending(guestId, "/root/notes.txt"),
            guest,
            guestPath("/root/copy.txt"),
            replace = true,
        )

        assertTrue(outcome.message, outcome.success)
        assertEquals("fresh content", textOf(guest, "/root/copy.txt"))
        assertEquals("the source of a copy survives a replace", "fresh content", textOf(guest, "/root/notes.txt"))
    }

    @Test
    fun `same-area paste move relocates and removes the source`() {
        val guest = guestArea()
        Files.write(rootfs.resolve("root/notes.txt"), "move me".toByteArray())

        val outcome = ExplorerOps.executePaste(
            guest,
            pending(guestId, "/root/notes.txt", move = true),
            guest,
            guestPath("/root/moved.txt"),
            replace = false,
        )

        assertTrue(outcome.message, outcome.success)
        assertEquals("move me", textOf(guest, "/root/moved.txt"))
        assertNull(guest.stat(guestPath("/root/notes.txt")))
    }

    @Test
    fun `same-area paste move replace replaces and removes the source`() {
        val guest = guestArea()
        Files.write(rootfs.resolve("root/notes.txt"), "new tree".toByteArray())
        Files.write(rootfs.resolve("root/target.txt"), "old target".toByteArray())

        val outcome = ExplorerOps.executePaste(
            guest,
            pending(guestId, "/root/notes.txt", move = true),
            guest,
            guestPath("/root/target.txt"),
            replace = true,
        )

        assertTrue(outcome.message, outcome.success)
        assertEquals("new tree", textOf(guest, "/root/target.txt"))
        assertNull(guest.stat(guestPath("/root/notes.txt")))
    }

    @Test
    fun `same-area paste onto itself is refused without changing anything`() {
        val guest = guestArea()
        Files.write(rootfs.resolve("root/notes.txt"), "do not lose me".toByteArray())

        val outcome = ExplorerOps.executePaste(
            guest,
            pending(guestId, "/root/notes.txt", move = true),
            guest,
            guestPath("/root/notes.txt"),
            replace = true, // even replace must not delete the source first
        )

        assertFalse(outcome.success)
        assertEquals("do not lose me", textOf(guest, "/root/notes.txt"))
    }

    // --------------------------------------------------- cross-area transfers

    @Test
    fun `cross-area copy is verified byte-exact and keeps the source`() {
        val guest = guestArea()
        val shelfArea = shelfArea()
        val payload = buildString { repeat(4000) { append("pocketshell-cross-area-0123456789\n") } }.toByteArray()
        Files.write(rootfs.resolve("root/notes.txt"), payload)

        val outcome = ExplorerOps.executePaste(
            guest,
            pending(guestId, "/root/notes.txt"),
            shelfArea,
            shelfPath("/notes.txt"),
            replace = false,
        )

        assertTrue(outcome.message, outcome.success)
        assertTrue(payload.contentEquals(Files.readAllBytes(shelf.resolve("notes.txt"))))
        assertTrue(
            "the source of a cross-area copy survives",
            payload.contentEquals(Files.readAllBytes(rootfs.resolve("root/notes.txt"))),
        )
    }

    @Test
    fun `cross-area copy refuses an existing target unless replace - the silent-overwrite gate`() {
        val guest = guestArea()
        val shelfArea = shelfArea()
        Files.write(rootfs.resolve("root/notes.txt"), "guest version".toByteArray())
        Files.write(shelf.resolve("notes.txt"), "shelf version".toByteArray())

        val outcome = ExplorerOps.executePaste(
            guest,
            pending(guestId, "/root/notes.txt"),
            shelfArea,
            shelfPath("/notes.txt"),
            replace = false,
        )

        // CrossArea alone would overwrite the file; the ops-layer gate refuses instead.
        assertFalse(outcome.message, outcome.success)
        assertEquals("shelf version", textOf(shelfArea, "/notes.txt"))
        assertEquals("guest version", textOf(guest, "/root/notes.txt"))
    }

    @Test
    fun `cross-area copy replace replaces an existing file target`() {
        val guest = guestArea()
        val shelfArea = shelfArea()
        Files.write(rootfs.resolve("root/notes.txt"), "guest version".toByteArray())
        Files.write(shelf.resolve("notes.txt"), "shelf version".toByteArray())

        val outcome = ExplorerOps.executePaste(
            guest,
            pending(guestId, "/root/notes.txt"),
            shelfArea,
            shelfPath("/notes.txt"),
            replace = true,
        )

        assertTrue(outcome.message, outcome.success)
        assertEquals("guest version", textOf(shelfArea, "/notes.txt"))
        assertEquals("guest version", textOf(guest, "/root/notes.txt"))
    }

    @Test
    fun `cross-area move verifies then deletes the source`() {
        val guest = guestArea()
        val shelfArea = shelfArea()
        Files.write(rootfs.resolve("root/notes.txt"), "relocate me".toByteArray())

        val outcome = ExplorerOps.executePaste(
            guest,
            pending(guestId, "/root/notes.txt", move = true),
            shelfArea,
            shelfPath("/docs/notes.txt"),
            replace = false,
        )

        assertTrue(outcome.message, outcome.success)
        assertEquals("relocate me", textOf(shelfArea, "/docs/notes.txt"))
        assertNull("a successful cross-area move deletes the source", guest.stat(guestPath("/root/notes.txt")))
    }

    @Test
    fun `cross-area move replace replaces the target and removes the source`() {
        val guest = guestArea()
        val shelfArea = shelfArea()
        Files.write(rootfs.resolve("root/notes.txt"), "guest version".toByteArray())
        Files.write(shelf.resolve("notes.txt"), "old shelf file".toByteArray())

        val outcome = ExplorerOps.executePaste(
            guest,
            pending(guestId, "/root/notes.txt", move = true),
            shelfArea,
            shelfPath("/notes.txt"),
            replace = true,
        )

        assertTrue(outcome.message, outcome.success)
        assertEquals("guest version", textOf(shelfArea, "/notes.txt"))
        assertNull(guest.stat(guestPath("/root/notes.txt")))
    }

    @Test
    fun `cross-area paste into a protected guest prefix is refused and the source stays intact`() {
        val guest = guestArea()
        val shelfArea = shelfArea()
        Files.write(shelf.resolve("evil.zip"), "evil bytes".toByteArray())

        val outcome = ExplorerOps.executePaste(
            shelfArea,
            pending(shelfId, "/evil.zip", move = true, label = StorageAreas.Labels.SHELF_SHORT_LABEL),
            guest,
            guestPath("/etc/evil.zip"),
            replace = false,
        )

        assertFalse("the frozen runtime prefixes must refuse app-side writes", outcome.success)
        assertTrue("policy refusals must be marked as refused", outcome.refused)
        assertNull(guest.stat(guestPath("/etc/evil.zip")))
        assertEquals("evil bytes", textOf(shelfArea, "/evil.zip"))
    }

    @Test
    fun `cross-area directory copy reports skipped nested symlinks as warnings`() {
        val guest = guestArea()
        val shelfArea = shelfArea()
        Files.createDirectories(rootfs.resolve("root/project"))
        Files.write(rootfs.resolve("root/project/file.txt"), "plain".toByteArray())
        Files.createSymbolicLink(rootfs.resolve("root/project/bin-link"), rootfs.resolve("bin"))

        val outcome = ExplorerOps.executePaste(
            guest,
            pending(guestId, "/root/project"),
            shelfArea,
            shelfPath("/project"),
            replace = false,
        )

        assertTrue(outcome.message, outcome.success)
        assertEquals("plain", textOf(shelfArea, "/project/file.txt"))
        assertTrue(
            "the skipped symlink must be disclosed",
            outcome.message.contains("symlink") || outcome.message.contains("skipped"),
        )
    }

    // ---------------------------------------------------------------- rename

    @Test
    fun `rename applies a valid new name inside the same directory`() {
        val guest = guestArea()
        Files.write(rootfs.resolve("root/notes.txt"), "rename me".toByteArray())

        val outcome = ExplorerOps.executeRename(
            guest, guestPath("/root/notes.txt"), "renamed.txt", replace = false,
        )

        assertTrue(outcome.message, outcome.success)
        assertEquals("rename me", textOf(guest, "/root/renamed.txt"))
        assertNull(guest.stat(guestPath("/root/notes.txt")))
    }

    @Test
    fun `rename refuses invalid names and collisions without changing anything`() {
        val guest = guestArea()
        Files.write(rootfs.resolve("root/notes.txt"), "keep".toByteArray())
        Files.write(rootfs.resolve("root/taken.txt"), "taken".toByteArray())

        val invalid = ExplorerOps.executeRename(
            guest, guestPath("/root/notes.txt"), "../escape", replace = false,
        )
        assertFalse(invalid.success)
        assertTrue(ExplorerOps.nameError("../escape") != null)

        val collision = ExplorerOps.executeRename(
            guest, guestPath("/root/notes.txt"), "taken.txt", replace = false,
        )
        assertFalse(collision.success)

        assertEquals("keep", textOf(guest, "/root/notes.txt"))
        assertEquals("taken", textOf(guest, "/root/taken.txt"))
    }

    @Test
    fun `rename with explicit replace replaces an existing target`() {
        val guest = guestArea()
        Files.write(rootfs.resolve("root/notes.txt"), "winner".toByteArray())
        Files.write(rootfs.resolve("root/taken.txt"), "loser".toByteArray())

        val outcome = ExplorerOps.executeRename(
            guest, guestPath("/root/notes.txt"), "taken.txt", replace = true,
        )

        assertTrue(outcome.message, outcome.success)
        assertEquals("winner", textOf(guest, "/root/taken.txt"))
        assertNull(guest.stat(guestPath("/root/notes.txt")))
    }

    @Test
    fun `rename of a file inside a protected prefix is refused as policy`() {
        val guest = guestArea()
        Files.write(rootfs.resolve("etc/existing.conf"), "runtime config".toByteArray())

        val outcome = ExplorerOps.executeRename(
            guest, guestPath("/etc/existing.conf"), "moved.conf", replace = false,
        )

        assertFalse(outcome.success)
        assertTrue(outcome.refused)
        assertEquals("runtime config", textOf(guest, "/etc/existing.conf"))
    }

    @Test
    fun `rename at the shelf root works - parent resolution handles top-level entries`() {
        val shelfArea = shelfArea()
        Files.write(shelf.resolve("site.zip"), "zippy".toByteArray())

        val outcome = ExplorerOps.executeRename(
            shelfArea, shelfPath("/site.zip"), "download.zip", replace = false,
        )

        assertTrue(outcome.message, outcome.success)
        assertEquals("zippy", textOf(shelfArea, "/download.zip"))
    }

    // -------------------------------------------------------- create file/dir

    @Test
    fun `create file and directory succeed and appear in the next listing`() {
        val guest = guestArea()
        val core = ExplorerCore(
            listOf(
                ExplorerCore.AreaHandle(guest, guestPath("/root"), "Linux"),
            ),
        )
        core.initial()

        val fileOutcome = ExplorerOps.executeCreateFile(guest, guestPath("/root"), "new.txt")
        val dirOutcome = ExplorerOps.executeCreateDirectory(guest, guestPath("/root"), "folder")

        assertTrue(fileOutcome.message, fileOutcome.success)
        assertTrue(dirOutcome.message, dirOutcome.success)

        // The refresh-after-operation sequence the ViewModel runs:
        val state = core.refresh()
        val names = state.entries.map { it.name }
        assertTrue("created file visible after refresh", "new.txt" in names)
        assertTrue("created folder visible after refresh", "folder" in names)
        assertTrue("directories sort first", state.entries.first().kind == EntryKind.DIRECTORY)
    }

    @Test
    fun `create refuses collisions invalid names and protected prefixes honestly`() {
        val guest = guestArea()
        Files.write(rootfs.resolve("root/taken.txt"), "taken".toByteArray())

        val collision = ExplorerOps.executeCreateFile(guest, guestPath("/root"), "taken.txt")
        assertFalse(collision.success)
        assertTrue(collision.message.contains("already exists"))

        val invalid = ExplorerOps.executeCreateFile(guest, guestPath("/root"), "a/b")
        assertFalse(invalid.success)

        val protectedDir = ExplorerOps.executeCreateDirectory(guest, guestPath("/etc"), "sneaky")
        assertFalse(protectedDir.success)
        assertTrue("policy refusal must be marked", protectedDir.refused)

        val protectedFile = ExplorerOps.executeCreateFile(guest, guestPath("/etc"), "sneaky.conf")
        assertFalse(protectedFile.success)
        assertTrue(protectedFile.refused)
    }

    // ----------------------------------------------------------------- delete

    @Test
    fun `delete removes a file`() {
        val guest = guestArea()
        Files.write(rootfs.resolve("root/notes.txt"), "gone soon".toByteArray())

        val outcome = ExplorerOps.executeDelete(guest, guestPath("/root/notes.txt"))

        assertTrue(outcome.message, outcome.success)
        assertNull(guest.stat(guestPath("/root/notes.txt")))
    }

    @Test
    fun `delete of a symlink removes only the node - the target survives`() {
        val guest = guestArea()
        Files.write(rootfs.resolve("root/data.txt"), "target content".toByteArray())
        Files.createSymbolicLink(rootfs.resolve("root/link"), rootfs.resolve("root/data.txt"))

        val outcome = ExplorerOps.executeDelete(guest, guestPath("/root/link"))

        assertTrue(outcome.message, outcome.success)
        assertNull("the symlink node is gone", guest.stat(guestPath("/root/link")))
        assertEquals("the target must survive the link's deletion", "target content", textOf(guest, "/root/data.txt"))
    }

    @Test
    fun `delete of a directory takes the contents but not the targets of escaping symlinks`() {
        val guest = guestArea()
        Files.write(rootfs.resolve("root/keep.txt"), "outside the tree".toByteArray())
        Files.createDirectories(rootfs.resolve("root/doomed"))
        Files.write(rootfs.resolve("root/doomed/inner.txt"), "inside".toByteArray())
        // A symlink INSIDE the doomed tree that points OUTSIDE it.
        Files.createSymbolicLink(rootfs.resolve("root/doomed/escape"), rootfs.resolve("root/keep.txt"))

        val outcome = ExplorerOps.executeDelete(guest, guestPath("/root/doomed"))

        assertTrue(outcome.message, outcome.success)
        assertNull(guest.stat(guestPath("/root/doomed")))
        assertNull(guest.stat(guestPath("/root/doomed/inner.txt")))
        assertEquals(
            "the escaped symlink's target must survive the tree delete",
            "outside the tree",
            textOf(guest, "/root/keep.txt"),
        )
    }

    @Test
    fun `delete refuses protected areas and the area root`() {
        val guest = guestArea()
        Files.write(rootfs.resolve("etc/existing.conf"), "runtime config".toByteArray())

        val protectedDelete = ExplorerOps.executeDelete(guest, guestPath("/etc/existing.conf"))
        assertFalse(protectedDelete.success)
        assertTrue(protectedDelete.refused)
        assertEquals("runtime config", textOf(guest, "/etc/existing.conf"))

        val rootDelete = ExplorerOps.executeDelete(guest, guestPath("/"))
        assertFalse(rootDelete.success)
        assertNull("nothing was deleted", null)
        assertNotNull(guest.stat(guestPath("/root")))
    }

    // --------------------------------------------------- human-facing copy

    @Test
    fun `delete warning copy is honest per kind`() {
        assertEquals(
            "The folder and everything inside it will be deleted.",
            ExplorerOps.deleteWarning(EntryKind.DIRECTORY),
        )
        assertEquals(
            "Only the link is deleted — the target it points to is not touched.",
            ExplorerOps.deleteWarning(EntryKind.SYMLINK),
        )
        assertNull(ExplorerOps.deleteWarning(EntryKind.FILE))
    }

    @Test
    fun `pending banner copy explains what is held and what to do next`() {
        val copyText = ExplorerOps.bannerText(pending(guestId, "/root/notes.txt", move = false))
        assertTrue(copyText.contains("copy"))
        assertTrue(copyText.contains("notes.txt"))
        assertTrue(copyText.contains("paste"))

        val moveText = ExplorerOps.bannerText(pending(guestId, "/root/notes.txt", move = true))
        assertTrue(moveText.contains("move"))
    }
}

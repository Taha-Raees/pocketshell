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
 * M7.0.0 Phase 8.1 pins — the multi-select operation layer over REAL
 * temporary directories (the Phase 2 [FileDirArea] engine underneath, the
 * same discipline as [ExplorerOpsTest]).
 *
 * What is pinned:
 *  - queue building: listing order, kind carrying, unknown/invalid selected
 *    names become honest failures, never guessed paths;
 *  - multi delete through the area engine: files, nested directories
 *    (contents), symlink NODES only (targets survive), per-item failures
 *    keep the walk going and carry reasons, guest-policy DENIED included;
 *  - the multi paste pass: engine order, sources kept for copy / gone for
 *    move, first collision interrupts with the exact rest, earlier
 *    completions carried in `done`, SameAsSource / vanished / invalid items
 *    fail individually without stopping the pass, cross-area copy works;
 *  - the resolve composition the ViewModel performs (execute current with
 *    replace, then pass the rest) aggregates both halves honestly;
 *  - presentation: exact summary sentences (singular/plural/failures/
 *    cancellation), exact banner texts, deduped delete warnings.
 */
class MultiSelectOpsTest {

    private lateinit var rootfs: Path
    private lateinit var shelf: Path

    private val guestId = AreaId(AreaKind.GUEST_LINUX)
    private val shelfId = AreaId(AreaKind.ANDROID_SHELF)

    @Before
    fun setUp() {
        rootfs = Files.createTempDirectory("pocketshell-multi-rootfs")
        shelf = Files.createTempDirectory("pocketshell-multi-shelf")
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

    /** A source area listing built from REAL files, in engine order. */
    private fun listing(area: StorageArea, dir: AreaPath): List<FsEntry> {
        val result = area.list(dir)
        check(result is ListResult.Ok) { "expected a listing of ${dir.value}: $result" }
        return result.entries
    }

    private fun transfer(
        areaId: AreaId,
        entry: FsEntry,
        dir: AreaPath,
        move: Boolean,
        label: String = "Linux",
    ): PendingTransfer = PendingTransfer(
        areaId = areaId,
        areaLabel = label,
        path = ExplorerOps.composeChild(dir, entry.name)!!,
        name = entry.name,
        kind = entry.kind,
        move = move,
    )

    // -------------------------------------------------------- queue building

    @Test
    fun `buildTransfers keeps listing order and carries kinds`() {
        Files.write(rootfs.resolve("root/b.txt"), "b".toByteArray())
        Files.write(rootfs.resolve("root/a file.txt"), "a".toByteArray())
        Files.createSymbolicLink(rootfs.resolve("root/z-link"), rootfs.resolve("root/b.txt"))
        Files.createDirectories(rootfs.resolve("root/mid dir"))
        val area = guestArea()
        val dir = guestPath("/root")

        val entries = listing(area, dir)
        val selected = setOf("z-link", "b.txt", "mid dir", "a file.txt")
        val (queue, outcome) = MultiSelectOps.buildTransfers(
            guestId, "Linux", dir, entries, selected, move = false,
        )

        assertEquals(MultiSelectOps.MultiOutcome.EMPTY, outcome)
        assertEquals(selected.toSet(), queue.mapTo(HashSet()) { it.name })
        // Listing order (the engine's directories-first sort), not set order.
        assertEquals(listOf("mid dir", "a file.txt", "b.txt", "z-link"), queue.map { it.name })
        assertEquals(EntryKind.DIRECTORY, queue.first { it.name == "mid dir" }.kind)
        assertEquals(EntryKind.SYMLINK, queue.first { it.name == "z-link" }.kind)
        assertTrue(queue.all { it.path.value.startsWith("/root/") })
    }

    @Test
    fun `buildTransfers reports vanished selections and refuses path-like names`() {
        Files.write(rootfs.resolve("root/real.txt"), "x".toByteArray())
        val area = guestArea()
        val dir = guestPath("/root")

        val fake = FsEntry("..", EntryKind.DIRECTORY, null, null) // can never come from a listing
        val (queue, outcome) = MultiSelectOps.buildTransfers(
            guestId, "Linux", dir,
            listing(area, dir) + fake,
            selected = setOf("real.txt", "ghost.txt", ".."),
            move = false,
        )

        assertEquals(listOf("real.txt"), queue.map { it.name })
        assertEquals(2, outcome.failed.size)
        val ghost = outcome.failed.first { it.name == "ghost.txt" }
        assertTrue("vanished names are disclosed", ghost.reason.contains("no longer listed"))
        val dots = outcome.failed.first { it.name == ".." }
        assertTrue("path-like names never compose", dots.reason.contains("valid entry"))
    }

    @Test
    fun `empty selection builds an empty queue with nothing failed`() {
        val area = guestArea()
        val dir = guestPath("/root")
        val (queue, outcome) = MultiSelectOps.buildTransfers(
            guestId, "Linux", dir, listing(area, dir), emptySet(), move = false,
        )
        assertTrue(queue.isEmpty())
        assertEquals(MultiSelectOps.MultiOutcome.EMPTY, outcome)
    }

    // ---------------------------------------------------------------- delete

    @Test
    fun `deleteAll removes files and nested directories and counts honestly`() {
        Files.write(rootfs.resolve("root/one.txt"), "1".toByteArray())
        Files.write(rootfs.resolve("root/two.txt"), "2".toByteArray())
        Files.createDirectories(rootfs.resolve("root/tree/deep"))
        Files.write(rootfs.resolve("root/tree/deep/leaf.txt"), "leaf".toByteArray())
        val area = guestArea()
        val dir = guestPath("/root")

        val outcome = MultiSelectOps.deleteAll(
            area, dir, listOf("one.txt", "tree", "two.txt"),
        )

        assertEquals(3, outcome.ok)
        assertTrue(outcome.failed.isEmpty())
        assertNull(area.stat(guestPath("/root/one.txt")))
        assertNull(area.stat(guestPath("/root/tree")))
        assertFalse("the whole tree went with its folder", Files.exists(rootfs.resolve("root/tree")))
    }

    @Test
    fun `deleteAll removes symlink nodes only — targets survive`() {
        Files.write(rootfs.resolve("root/data.txt"), "keep me".toByteArray())
        Files.createSymbolicLink(rootfs.resolve("root/link"), rootfs.resolve("root/data.txt"))
        Files.createSymbolicLink(rootfs.resolve("root/self"), rootfs.resolve("root/self"))
        val area = guestArea()
        val dir = guestPath("/root")

        val outcome = MultiSelectOps.deleteAll(area, dir, listOf("link", "self"))

        assertEquals(2, outcome.ok)
        assertNull(area.stat(guestPath("/root/link")))
        assertNull(area.stat(guestPath("/root/self")))
        assertNotNull("the symlink target is never touched", area.stat(guestPath("/root/data.txt")))
        assertEquals("keep me", String(Files.readAllBytes(rootfs.resolve("root/data.txt"))))
    }

    @Test
    fun `deleteAll keeps going after a vanished item and reports a policy denial`() {
        Files.write(rootfs.resolve("root/keep.txt"), "k".toByteArray())
        Files.write(rootfs.resolve("etc/frozen.conf"), "frozen".toByteArray())
        val area = guestArea()
        val dir = guestPath("/root")

        val outcome = MultiSelectOps.deleteAll(
            area, dir, listOf("vanished.txt", "keep.txt"),
        )

        assertEquals("the walk never aborts on one bad item", 1, outcome.ok)
        assertEquals(1, outcome.failed.size)
        assertEquals("vanished.txt", outcome.failed.single().name)
        assertNull("the present item was still deleted", area.stat(guestPath("/root/keep.txt")))

        // /etc is a frozen runtime prefix — the area DENIES the delete.
        val denied = MultiSelectOps.deleteAll(area, guestPath("/"), listOf("etc"))
        assertEquals(0, denied.ok)
        assertEquals(1, denied.failed.size)
        assertEquals("etc", denied.failed.single().name)
        assertTrue(denied.failed.single().reason.isNotEmpty())
        assertEquals("frozen", String(Files.readAllBytes(rootfs.resolve("etc/frozen.conf"))))
    }

    // ------------------------------------------------------------ multi paste

    @Test
    fun `pastePass copies the whole queue in order and keeps sources`() {
        Files.write(rootfs.resolve("root/a.txt"), "a".toByteArray())
        Files.write(rootfs.resolve("root/b.txt"), "b".toByteArray())
        Files.createDirectories(rootfs.resolve("root/sub"))
        Files.write(rootfs.resolve("root/sub/c.txt"), "c".toByteArray())
        val guest = guestArea()
        val shelfArea = shelfArea()
        val dir = guestPath("/root")
        val entries = listing(guest, dir)

        val queue = listOf("b.txt", "sub", "a.txt").map { name ->
            transfer(guestId, entries.first { it.name == name }, dir, move = false)
        }
        val pass = MultiSelectOps.pastePass(guest, queue, shelfArea, shelfPath("/docs"))

        val completed = pass as MultiSelectOps.PastePassResult.Completed
        assertEquals(3, completed.outcome.ok)
        assertTrue(completed.outcome.failed.isEmpty())
        assertEquals("b", String(Files.readAllBytes(shelf.resolve("docs/b.txt"))))
        assertEquals("c", String(Files.readAllBytes(shelf.resolve("docs/sub/c.txt"))))
        assertNotNull("copy keeps every source", guest.stat(guestPath("/root/b.txt")))
    }

    @Test
    fun `pastePass move clears the sources`() {
        Files.write(rootfs.resolve("root/x.txt"), "x".toByteArray())
        Files.write(rootfs.resolve("root/y.txt"), "y".toByteArray())
        val guest = guestArea()
        val shelfArea = shelfArea()
        val dir = guestPath("/root")
        val entries = listing(guest, dir)

        val queue = listOf("x.txt", "y.txt").map {
            transfer(guestId, entries.first { e -> e.name == it }, dir, move = true)
        }
        val completed = MultiSelectOps.pastePass(guest, queue, shelfArea, shelfPath("/docs"))
            as MultiSelectOps.PastePassResult.Completed

        assertEquals(2, completed.outcome.ok)
        assertNull(guest.stat(guestPath("/root/x.txt")))
        assertNull(guest.stat(guestPath("/root/y.txt")))
        assertEquals("x", String(Files.readAllBytes(shelf.resolve("docs/x.txt"))))
    }

    @Test
    fun `pastePass stops at the FIRST collision with the exact rest and nothing executed after it`() {
        Files.write(rootfs.resolve("root/one.txt"), "1".toByteArray())
        Files.write(rootfs.resolve("root/two.txt"), "2".toByteArray())
        Files.write(shelf.resolve("docs/two.txt"), "older two".toByteArray())
        val guest = guestArea()
        val shelfArea = shelfArea()
        val dir = guestPath("/root")
        val entries = listing(guest, dir)

        val queue = listOf("one.txt", "two.txt", "three.txt").mapNotNull { name ->
            // three.txt does not exist — it stays in the queue shape as a
            // pending transfer; the pass must never reach it.
            val entry = entries.firstOrNull { it.name == name }
                ?: FsEntry(name, EntryKind.FILE, null, null)
            transfer(guestId, entry, dir, move = false)
        }
        val pass = MultiSelectOps.pastePass(guest, queue, shelfArea, shelfPath("/docs"))

        val needs = pass as MultiSelectOps.PastePassResult.NeedsReplace
        assertEquals("two.txt", needs.current.name)
        assertEquals(EntryKind.FILE, needs.existingKind)
        assertEquals("/docs/two.txt", needs.target.value)
        assertEquals("one.txt already landed before the collision", 1, needs.done.ok)
        assertTrue(needs.done.failed.isEmpty())
        assertEquals(listOf("three.txt"), needs.rest.map { it.name })
        assertEquals("older two", String(Files.readAllBytes(shelf.resolve("docs/two.txt"))))
    }

    @Test
    fun `resolve composition — replace the current then pass the rest aggregates both halves`() {
        Files.write(rootfs.resolve("root/two.txt"), "2".toByteArray())
        Files.write(rootfs.resolve("root/three.txt"), "3".toByteArray())
        Files.write(shelf.resolve("docs/two.txt"), "older two".toByteArray())
        val guest = guestArea()
        val shelfArea = shelfArea()
        val dir = guestPath("/root")
        val entries = listing(guest, dir)

        val current = transfer(
            guestId, entries.first { it.name == "two.txt" }, dir, move = false,
        )
        val rest = listOf(
            transfer(guestId, entries.first { it.name == "three.txt" }, dir, move = false),
        )

        // What the ViewModel does on Replace: executePaste(replace = true)
        // for the interrupted item, then one more pass over the rest.
        val replaced = ExplorerOps.executePaste(
            guest, current, shelfArea,
            ExplorerOps.composeChild(shelfPath("/docs"), "two.txt")!!,
            replace = true,
        )
        assertTrue(replaced.success)
        val pass = MultiSelectOps.pastePass(guest, rest, shelfArea, shelfPath("/docs"))

        val completed = pass as MultiSelectOps.PastePassResult.Completed
        val total = MultiSelectOps.MultiOutcome(1, emptyList()) + completed.outcome
        assertEquals(2, total.ok)
        assertEquals("2", String(Files.readAllBytes(shelf.resolve("docs/two.txt"))))
        assertEquals("3", String(Files.readAllBytes(shelf.resolve("docs/three.txt"))))
    }

    @Test
    fun `pastePass fails every SameAsSource item individually — nothing changes`() {
        Files.write(rootfs.resolve("root/here.txt"), "h".toByteArray())
        Files.write(rootfs.resolve("root/next.txt"), "n".toByteArray())
        val guest = guestArea()
        val dir = guestPath("/root")
        val entries = listing(guest, dir)

        val queue = listOf("here.txt", "next.txt").map {
            transfer(guestId, entries.first { e -> e.name == it }, dir, move = false)
        }
        val pass = MultiSelectOps.pastePass(guest, queue, guest, dir)

        val completed = pass as MultiSelectOps.PastePassResult.Completed
        assertEquals("pasting a folder into itself lands every item on itself", 0, completed.outcome.ok)
        assertEquals(2, completed.outcome.failed.size)
        assertEquals(listOf("here.txt", "next.txt"), completed.outcome.failed.map { it.name })
        assertEquals("h", String(Files.readAllBytes(rootfs.resolve("root/here.txt"))))
        assertEquals("n", String(Files.readAllBytes(rootfs.resolve("root/next.txt"))))
    }

    @Test
    fun `pastePass reports vanished sources per item without stopping the pass`() {
        val guest = guestArea()
        val shelfArea = shelfArea()
        Files.write(rootfs.resolve("root/gone.txt"), "g".toByteArray())
        val gone = transfer(
            guestId, FsEntry("gone.txt", EntryKind.FILE, null, null), guestPath("/root"), move = false,
        )
        rootfs.resolve("root/gone.txt").toFile().delete() // vanished after marking
        Files.write(rootfs.resolve("root/stays.txt"), "s".toByteArray())
        val stays = transfer(
            guestId,
            listing(guest, guestPath("/root")).first { it.name == "stays.txt" },
            guestPath("/root"), move = false,
        )

        val pass = MultiSelectOps.pastePass(guest, listOf(gone, stays), shelfArea, shelfPath("/docs"))

        val completed = pass as MultiSelectOps.PastePassResult.Completed
        assertEquals(1, completed.outcome.ok)
        assertEquals("gone.txt", completed.outcome.failed.single().name)
        assertTrue(completed.outcome.failed.single().reason.isNotEmpty())
    }

    @Test
    fun `pastePass refuses an invalid destination name as one failed item`() {
        val guest = guestArea()
        val shelfArea = shelfArea()
        val bad = PendingTransfer(
            areaId = guestId, areaLabel = "Linux",
            path = guestPath("/root/x"), name = "..", kind = EntryKind.DIRECTORY, move = false,
        )
        Files.write(rootfs.resolve("root/ok.txt"), "o".toByteArray())
        val ok = transfer(
            guestId,
            listing(guest, guestPath("/root")).first { it.name == "ok.txt" },
            guestPath("/root"), move = false,
        )

        val pass = MultiSelectOps.pastePass(guest, listOf(bad, ok), shelfArea, shelfPath("/docs"))

        val completed = pass as MultiSelectOps.PastePassResult.Completed
        assertEquals(1, completed.outcome.ok)
        assertEquals("..", completed.outcome.failed.single().name)
    }

    @Test
    fun `multi cross-area move of a folder keeps the honest boundary`() {
        Files.createDirectories(rootfs.resolve("root/moveme"))
        Files.write(rootfs.resolve("root/moveme/inner.txt"), "i".toByteArray())
        val guest = guestArea()
        val shelfArea = shelfArea()
        val moveme = transfer(
            guestId,
            listing(guest, guestPath("/root")).first { it.name == "moveme" },
            guestPath("/root"), move = true,
        )

        val pass = MultiSelectOps.pastePass(guest, listOf(moveme), shelfArea, shelfPath("/docs"))

        val completed = pass as MultiSelectOps.PastePassResult.Completed
        assertEquals(1, completed.outcome.ok)
        assertEquals("i", String(Files.readAllBytes(shelf.resolve("docs/moveme/inner.txt"))))
        assertNull(guest.stat(guestPath("/root/moveme")))
    }

    // ----------------------------------------------------------- presentation

    @Test
    fun `summary sentences are exact for every shape`() {
        assertEquals(
            "Deleted 1 item.",
            MultiSelectOps.summary("Deleted", MultiSelectOps.MultiOutcome(1, emptyList())),
        )
        assertEquals(
            "Deleted 4 items.",
            MultiSelectOps.summary("Deleted", MultiSelectOps.MultiOutcome(4, emptyList())),
        )
        assertEquals(
            "Nothing was copied.",
            MultiSelectOps.summary("Copied", MultiSelectOps.MultiOutcome(0, emptyList())),
        )
        val mixed = MultiSelectOps.summary(
            "Copied",
            MultiSelectOps.MultiOutcome(
                2,
                listOf(
                    MultiSelectOps.MultiFailure("x.txt", "vanished"),
                    MultiSelectOps.MultiFailure("y", "refused"),
                ),
            ),
        )
        assertEquals(
            "Copied 2 items. Failed: \"x.txt\" (vanished); \"y\" (refused)",
            mixed,
        )
        val many = MultiSelectOps.summary(
            "Moved",
            MultiSelectOps.MultiOutcome(
                0,
                (1..5).map { MultiSelectOps.MultiFailure("f$it", "no") },
            ),
        )
        assertTrue(many.startsWith("Nothing was moved. Failed: "))
        assertTrue("only three failures are listed, then a count", many.contains("and 2 more."))
        val cancelled = MultiSelectOps.summary(
            "Copied",
            MultiSelectOps.MultiOutcome(2, emptyList()),
            cancelledAt = "z.txt",
        )
        assertEquals(
            "Copied 2 items. Cancelled at \"z.txt\" — the remaining items were not touched.",
            cancelled,
        )
    }

    @Test
    fun `banner text discloses the count only for multi clipboards`() {
        val single = PendingTransfer(
            guestId, "Linux", guestPath("/root/a.txt"), "a.txt", EntryKind.FILE, move = false,
        )
        assertEquals(
            "the single wording is byte-identical to the historical one",
            ExplorerOps.bannerText(single),
            MultiSelectOps.bannerText(single, 1),
        )
        assertEquals(
            "Holding 3 items to copy from Linux — open a destination and paste here.",
            MultiSelectOps.bannerText(single, 3),
        )
        assertEquals(
            "Holding 5 items to move from Linux — open a destination and paste here.",
            MultiSelectOps.bannerText(single.copy(move = true), 5),
        )
        assertEquals(
            "blank labels stay invisible",
            "Holding 2 items to copy — open a destination and paste here.",
            MultiSelectOps.bannerText(single.copy(areaLabel = ""), 2),
        )
    }

    @Test
    fun `delete warnings are deduped per kind`() {
        val entries = listOf(
            FsEntry("d1", EntryKind.DIRECTORY, null, null),
            FsEntry("d2", EntryKind.DIRECTORY, null, null),
            FsEntry("l1", EntryKind.SYMLINK, null, null, symlinkTarget = "/root/d1"),
            FsEntry("f1", EntryKind.FILE, 3L, null),
        )
        val warnings = MultiSelectOps.deleteWarnings(entries, setOf("d1", "d2", "l1", "f1"))
        assertEquals(
            listOf(
                "Selected folders and everything inside them will be deleted.",
                "Only links are deleted — the targets they point to are not touched.",
            ),
            warnings,
        )
        assertTrue("plain files add no warning", MultiSelectOps.deleteWarnings(entries, setOf("f1")).isEmpty())
    }
}

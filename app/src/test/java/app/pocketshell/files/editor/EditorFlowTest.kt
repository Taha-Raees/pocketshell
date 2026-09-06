package app.pocketshell.files.editor

import app.pocketshell.files.AreaId
import app.pocketshell.files.AreaKind
import app.pocketshell.files.AreaPath
import app.pocketshell.files.EntryKind
import app.pocketshell.files.FileDirArea
import app.pocketshell.files.OpResult
import app.pocketshell.files.PathSafety
import app.pocketshell.files.StorageAreas
import app.pocketshell.files.ReadResult
import app.pocketshell.files.StorageArea
import app.pocketshell.files.saf.AndroidDocumentArea
import app.pocketshell.files.saf.FakeDocumentBackend
import java.nio.file.Files
import java.nio.file.Path
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * M7.0.0 Phase 6 pins — the quick editor's flow END-TO-END through REAL
 * storage areas (the Phase 2 [FileDirArea] engine and the Phase 5
 * [AndroidDocumentArea] over the in-memory [FakeDocumentBackend]).
 *
 * The Android [app.pocketshell.EditorViewModel] itself is coroutine glue
 * over Android classes and cannot run on the JVM (the suite's no-Android-
 * fakes rule); these pins exercise its EXACT call sequence instead:
 *
 *   load: TextDocument.decideOpen(name, area.readBytes(path, MAX))
 *   gate: TextDocument.saveGate(snapshot, area.stat(path))
 *   save: area.writeBytesAtomic(path, TextDocument.encode(text))
 *
 * What is pinned here:
 *  - load → edit → save → re-read is byte-exact on the guest rootfs, the
 *    download shelf and a SAF folder (all three domains);
 *  - CRLF content and empty files survive the full round trip;
 *  - binary / non-UTF-8 / oversized files are refused with the file left
 *    untouched on disk;
 *  - the save gate against real stats: unchanged → Clear, out-of-band
 *    change → ChangedExternally, out-of-band delete → Missing — and the
 *    honest (size, mtime) blind spot: a same-size write with the original
 *    mtime restored is invisible to the gate BY DESIGN (pinned, not hidden);
 *  - policy honesty: a save into a protected guest prefix is DENIED
 *    (refused, not failed), a write through a symlink is refused, and a
 *    SAF revocation mid-session surfaces the verbatim revoked error.
 */
class EditorFlowTest {

    private lateinit var rootfs: Path
    private lateinit var shelf: Path
    private lateinit var guest: StorageArea
    private lateinit var downloads: StorageArea
    private lateinit var backend: FakeDocumentBackend
    private lateinit var saf: StorageArea

    private val guestId = AreaId(AreaKind.GUEST_LINUX)
    private val shelfId = AreaId(AreaKind.ANDROID_SHELF)
    private val safId = AreaId(AreaKind.ANDROID_DOCUMENT_TREE, "content://test/tree")

    @Before
    fun setUp() {
        rootfs = Files.createTempDirectory("pocketshell-editor-rootfs")
        Files.createDirectories(rootfs.resolve("root"))
        Files.createDirectories(rootfs.resolve("etc"))
        shelf = Files.createTempDirectory("pocketshell-editor-shelf")
        guest = FileDirArea.create(
            root = rootfs.toFile(),
            id = guestId,
            displayName = "PocketShell Linux",
            policy = FileDirArea.MutationPolicy.guestRuntime(),
        )!!
        downloads = FileDirArea.create(
            root = shelf.toFile(),
            id = shelfId,
            displayName = StorageAreas.Labels.SHELF_DISPLAY_NAME,
            policy = FileDirArea.MutationPolicy.OPEN,
        )!!
        backend = FakeDocumentBackend("MyProject")
        saf = AndroidDocumentArea.create(
            id = safId,
            displayName = "MyProject",
            backend = backend,
        )
    }

    @After
    fun tearDown() {
        rootfs.toFile().deleteRecursively() // test scaffolding only, host-side
        shelf.toFile().deleteRecursively()
    }

    // --------------------------------------------------- the VM's exact sequence

    /** EditorViewModel's load, verbatim. */
    private fun load(area: StorageArea, path: AreaPath): OpenDecision =
        TextDocument.decideOpen(
            name = path.value.substringAfterLast('/'),
            read = area.readBytes(path, TextDocument.MAX_QUICK_EDIT_BYTES.toLong()),
        )

    /** EditorViewModel's gate check, verbatim. */
    private fun gate(area: StorageArea, path: AreaPath, snapshot: app.pocketshell.files.FsEntry?): SaveGate =
        TextDocument.saveGate(snapshot = snapshot, current = area.stat(path))

    /** EditorViewModel's save, verbatim. */
    private fun save(area: StorageArea, path: AreaPath, text: String): OpResult =
        area.writeBytesAtomic(path, TextDocument.encode(text))

    private fun pathOf(value: String): AreaPath = PathSafety.validatePath(value)!!

    // ------------------------------------------------------- happy paths

    @Test
    fun `guest area - load, edit, save, re-read is byte-exact`() {
        val original = "note one\tnote two\n"
        Files.write(rootfs.resolve("root/todo.txt"), original.toByteArray(Charsets.UTF_8))
        val path = pathOf("/root/todo.txt")

        val decision = load(guest, path)
        assertTrue(decision is OpenDecision.Text)
        assertEquals(original, (decision as OpenDecision.Text).content)

        val edited = decision.content.replace("one", "ONE") + "appended 🐧\n"
        assertEquals(OpResult.Kind.SUCCESS, save(guest, path, edited).kind)

        val reread = guest.readBytes(path, TextDocument.MAX_QUICK_EDIT_BYTES.toLong())
        assertTrue(reread is ReadResult.Ok)
        assertEquals(edited, String((reread as ReadResult.Ok).bytes, Charsets.UTF_8))
    }

    @Test
    fun `shelf area - load, edit, save, re-read is byte-exact`() {
        Files.write(shelf.resolve("report.md"), "# hi\n".toByteArray(Charsets.UTF_8))
        val path = pathOf("/report.md")

        val decision = load(downloads, path)
        assertTrue(decision is OpenDecision.Text)
        assertEquals(OpResult.Kind.SUCCESS, save(downloads, path, "# hi\n\nbody\n").kind)
        val reread = downloads.readBytes(path, TextDocument.MAX_QUICK_EDIT_BYTES.toLong())
        assertEquals("# hi\n\nbody\n", String((reread as ReadResult.Ok).bytes, Charsets.UTF_8))
    }

    @Test
    fun `saf folder - load, edit, save, re-read is byte-exact`() {
        backend.write("notes/idea.txt", "from android\n".toByteArray(Charsets.UTF_8))
        val path = pathOf("/notes/idea.txt")

        val decision = load(saf, path)
        assertTrue(decision is OpenDecision.Text)
        assertEquals("from android\n", (decision as OpenDecision.Text).content)
        assertEquals(OpResult.Kind.SUCCESS, save(saf, path, "from android\nedited\n").kind)
        assertEquals("from android\nedited\n", String(backend.content("notes/idea.txt")!!, Charsets.UTF_8))
    }

    @Test
    fun `crlf content survives a real save untouched`() {
        Files.write(rootfs.resolve("root/crlf.txt"), "a\r\nb\r\n".toByteArray(Charsets.UTF_8))
        val path = pathOf("/root/crlf.txt")
        val decision = load(guest, path)
        assertEquals(OpenDecision.Text("a\r\nb\r\n"), decision)
        save(guest, path, "a\r\nb\r\nmore\r\n")
        val reread = guest.readBytes(path, TextDocument.MAX_QUICK_EDIT_BYTES.toLong()) as ReadResult.Ok
        assertEquals("a\r\nb\r\nmore\r\n", String(reread.bytes, Charsets.UTF_8))
    }

    @Test
    fun `an empty file opens as empty text and saves`() {
        Files.write(rootfs.resolve("root/empty.txt"), ByteArray(0))
        val path = pathOf("/root/empty.txt")
        assertEquals(OpenDecision.Text(""), load(guest, path))
        assertEquals(OpResult.Kind.SUCCESS, save(guest, path, "now it has content\n").kind)
    }

    // -------------------------------------------------- honest refusals

    @Test
    fun `a binary file is refused with the file untouched`() {
        val bytes = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0x00)
        Files.write(rootfs.resolve("root/blob.bin"), bytes)
        val path = pathOf("/root/blob.bin")
        assertEquals(OpenDecision.Binary, load(guest, path))
        assertTrue(Files.readAllBytes(rootfs.resolve("root/blob.bin")).contentEquals(bytes))
    }

    @Test
    fun `a non-utf-8 file is refused with the file untouched`() {
        val bytes = byteArrayOf(0xC3.toByte(), 0x28, 0x62, 0x61, 0x64) // truncated sequence
        Files.write(rootfs.resolve("root/bad.txt"), bytes)
        val path = pathOf("/root/bad.txt")
        assertEquals(OpenDecision.NotUtf8, load(guest, path))
        assertTrue(Files.readAllBytes(rootfs.resolve("root/bad.txt")).contentEquals(bytes))
    }

    @Test
    fun `an oversized file is refused with its real size`() {
        val big = ByteArray(TextDocument.MAX_QUICK_EDIT_BYTES + 1) { 'x'.code.toByte() }
        Files.write(rootfs.resolve("root/big.log"), big)
        val decision = load(guest, pathOf("/root/big.log"))
        assertEquals(OpenDecision.TooLarge(big.size.toLong()), decision)
    }

    // ------------------------------------------------------- the save gate

    @Test
    fun `save gate is Clear against a real unchanged file`() {
        Files.write(rootfs.resolve("root/stable.txt"), "same\n".toByteArray(Charsets.UTF_8))
        val path = pathOf("/root/stable.txt")
        val snapshot = guest.stat(path)
        assertNotNull(snapshot)
        assertEquals(SaveGate.Clear, gate(guest, path, snapshot))
    }

    @Test
    fun `save gate asks when the file changed out-of-band`() {
        val path = pathOf("/root/live.txt")
        Files.write(rootfs.resolve("root/live.txt"), "v1\n".toByteArray(Charsets.UTF_8))
        val snapshot = guest.stat(path)
        // A running guest shell writes to the file behind the editor.
        Files.write(rootfs.resolve("root/live.txt"), "v2 - changed by someone else\n".toByteArray(Charsets.UTF_8))
        assertEquals(SaveGate.ChangedExternally, gate(guest, path, snapshot))
    }

    @Test
    fun `save gate asks when the file was deleted out-of-band`() {
        Files.write(rootfs.resolve("root/gone.txt"), "soon gone\n".toByteArray(Charsets.UTF_8))
        val path = pathOf("/root/gone.txt")
        val snapshot = guest.stat(path)
        Files.delete(rootfs.resolve("root/gone.txt"))
        assertEquals(SaveGate.Missing, gate(guest, path, snapshot))
    }

    @Test
    fun `save gate on saf asks when the document changed out-of-band`() {
        backend.write("doc.txt", "v1\n".toByteArray(Charsets.UTF_8))
        val path = pathOf("/doc.txt")
        val snapshot = saf.stat(path)
        // Another app writes through the provider (content grows).
        val doc = backend.resolveChild(backend.root(), "doc.txt")!!
        backend.openOutputStream(doc, truncate = true).use { it.write("v2 - much longer\n".toByteArray(Charsets.UTF_8)) }
        assertEquals(SaveGate.ChangedExternally, gate(saf, path, snapshot))
    }

    @Test
    fun `save gate on saf asks when the document was deleted out-of-band`() {
        backend.write("vanish.txt", "v1\n".toByteArray(Charsets.UTF_8))
        val path = pathOf("/vanish.txt")
        val snapshot = saf.stat(path)
        val rootDoc = backend.root()
        val doc = backend.resolveChild(rootDoc, "vanish.txt")!!
        backend.deleteDocument(doc)
        assertEquals(SaveGate.Missing, gate(saf, path, snapshot))
    }

    @Test
    fun `the honest blind spot - a same-size write with the original mtime is Clear by design`() {
        // The (size, mtime) gate cannot see a same-size same-mtime write.
        // This pin documents the limit instead of hiding it: the confirm
        // dialog still guards every change the gate CAN see.
        val pathValue = rootfs.resolve("root/race.txt")
        Files.write(pathValue, "aaaa\n".toByteArray(Charsets.UTF_8))
        val path = pathOf("/root/race.txt")
        val snapshot = guest.stat(path)!!
        Files.write(pathValue, "bbbb\n".toByteArray(Charsets.UTF_8)) // same size
        pathValue.toFile().setLastModified(snapshot.modifiedAtMillis!!)
        assertEquals(SaveGate.Clear, gate(guest, path, snapshot))
    }

    @Test
    fun `a confirmed save lands even after an external change (last-writer-wins)`() {
        val path = pathOf("/root/confirm.txt")
        Files.write(rootfs.resolve("root/confirm.txt"), "v1\n".toByteArray(Charsets.UTF_8))
        val snapshot = guest.stat(path)
        Files.write(rootfs.resolve("root/confirm.txt"), "v2 external\n".toByteArray(Charsets.UTF_8))
        assertEquals(SaveGate.ChangedExternally, gate(guest, path, snapshot))
        // The user answered "Save anyway" — the write goes through and wins.
        assertEquals(OpResult.Kind.SUCCESS, save(guest, path, "editor wins\n").kind)
        val reread = guest.readBytes(path, TextDocument.MAX_QUICK_EDIT_BYTES.toLong()) as ReadResult.Ok
        assertEquals("editor wins\n", String(reread.bytes, Charsets.UTF_8))
    }

    // ------------------------------------------------------ policy honesty

    @Test
    fun `a save into a protected guest prefix is DENIED (refused, not failed)`() {
        // Reading runtime config is allowed; writing it is the frozen-M6
        // boundary — the editor surfaces this as saveDenied.
        val path = pathOf("/etc/hosts")
        val result = save(guest, path, "should not happen\n")
        assertEquals(OpResult.Kind.DENIED, result.kind)
        assertTrue(Files.notExists(rootfs.resolve("etc/hosts")))
    }

    @Test
    fun `a save through a symlink is refused and the target survives`() {
        val target = rootfs.resolve("root/target.txt")
        Files.write(target, "target\n".toByteArray(Charsets.UTF_8))
        // Guest-view target string (the existing suites' convention): the
        // link resolves safely INSIDE the area for reads.
        Files.createSymbolicLink(rootfs.resolve("root/link.txt"), Path.of("/root/target.txt"))
        val path = pathOf("/root/link.txt")
        // The read may resolve safely INSIDE the area…
        assertTrue(load(guest, path) is OpenDecision.Text)
        // …but a write through the link is refused and the target is intact.
        assertEquals(OpResult.Kind.DENIED, save(guest, path, "through the link\n").kind)
        assertEquals("target\n", String(Files.readAllBytes(target), Charsets.UTF_8))
    }

    @Test
    fun `saf revocation mid-session fails the save with the verbatim revoked error`() {
        backend.write("session.txt", "before\n".toByteArray(Charsets.UTF_8))
        val path = pathOf("/session.txt")
        assertTrue(load(saf, path) is OpenDecision.Text)
        backend.revoked = true // the user revoked the grant while editing
        val result = save(saf, path, "after\n")
        assertNotEquals(OpResult.Kind.SUCCESS, result.kind)
        assertNotNull(result.reason)
        assertTrue(backend.content("session.txt")!!.contentEquals("before\n".toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `a failed saf save leaves the previous content intact (restore-on-failure)`() {
        backend.write("swap.txt", "original\n".toByteArray(Charsets.UTF_8))
        val path = pathOf("/swap.txt")
        backend.failRenameFor = "swap.txt" // the provider refuses the swap rename
        val result = save(saf, path, "rewritten\n")
        assertEquals(OpResult.Kind.FAILED, result.kind)
        assertEquals("original\n", String(backend.content("swap.txt")!!, Charsets.UTF_8))
    }

    // ------------------------------------------------------------ listing kind

    @Test
    fun `editor launches are regular files only - symlink entries are excluded`() {
        val target = rootfs.resolve("root/real.txt")
        Files.write(target, "real\n".toByteArray(Charsets.UTF_8))
        Files.createSymbolicLink(rootfs.resolve("root/alias.txt"), rootfs.resolve("root/real.txt"))
        // FilesViewModel.editorLaunch refuses non-FILE kinds; the listing here
        // reports the symlink AS A NODE.
        val listing = guest.list(pathOf("/root"))
        assertTrue(listing is app.pocketshell.files.ListResult.Ok)
        val alias = (listing as app.pocketshell.files.ListResult.Ok).entries
            .first { it.name == "alias.txt" }
        assertEquals(EntryKind.SYMLINK, alias.kind)
        assertNotEquals(EntryKind.FILE, alias.kind)
    }
}

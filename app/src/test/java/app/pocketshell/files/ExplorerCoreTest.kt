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
 * M7.0.0 Phase 3 pins — the explorer's state machine over REAL temporary
 * directories (the Phase 2 [FileDirArea] engine underneath, so every rule
 * here is exercised end-to-end: validation, sorting, listing, errors).
 *
 * Boundary discipline under test:
 *  - navigation starts at the guest landing path /root;
 *  - up-navigation stops at the area root — the logical area is never escaped;
 *  - the UI's only navigation input is a listing NAME; traversal shapes
 *    ("..", ".", "a/b", blank, NUL, absolute forms) are refused with honest
 *    errors and the location does not move;
 *  - failures are data (State.error), never exceptions, never silent.
 */
class ExplorerCoreTest {

    private lateinit var rootfs: Path
    private lateinit var shelf: Path
    private lateinit var core: ExplorerCore

    private val guestId = AreaId(AreaKind.GUEST_LINUX)
    private val shelfId = AreaId(AreaKind.ANDROID_SHELF)

    @Before
    fun setUp() {
        rootfs = Files.createTempDirectory("pocketshell-explorer-rootfs")
        shelf = Files.createTempDirectory("pocketshell-explorer-shelf")

        // A believable little guest: user home with a project, a dotfile,
        // a file with a known size, a symlink node — plus protected prefixes.
        Files.createDirectories(rootfs.resolve("root/projects/empty-dir"))
        Files.write(rootfs.resolve("root/projects/notes.txt"), "hello notes".toByteArray())
        Files.write(rootfs.resolve("root/welcome.txt"), "hello world".toByteArray()) // 11 bytes
        Files.write(rootfs.resolve("root/.env"), "A=1".toByteArray())
        Files.createSymbolicLink(rootfs.resolve("root/bin-link"), rootfs.resolve("bin"))
        Files.createDirectories(rootfs.resolve("bin"))
        Files.createDirectories(rootfs.resolve("etc"))

        Files.write(shelf.resolve("site.zip"), "ZIPBYTES".toByteArray())

        core = ExplorerCore(
            listOf(
                ExplorerCore.AreaHandle(
                    area = newGuestArea(),
                    startPath = PathSafety.validatePath("/root")!!,
                    shortLabel = "Linux",
                ),
                ExplorerCore.AreaHandle(
                    area = newShelfArea(),
                    startPath = PathSafety.validatePath("/")!!,
                    shortLabel = "Downloads",
                ),
            ),
        )
    }

    @After
    fun tearDown() {
        rootfs.toFile().deleteRecursively() // test scaffolding only, host-side
        shelf.toFile().deleteRecursively()
    }

    private fun newGuestArea(): FileDirArea =
        FileDirArea.create(
            root = rootfs.toFile(),
            id = guestId,
            displayName = "PocketShell Linux",
            policy = FileDirArea.MutationPolicy.guestRuntime(),
        )!!

    private fun newShelfArea(): FileDirArea =
        FileDirArea.create(
            root = shelf.toFile(),
            id = shelfId,
            displayName = "Android Downloads (app storage)",
            policy = FileDirArea.MutationPolicy.OPEN,
        )!!

    // ------------------------------------------------------------- landing

    @Test
    fun `initial state lands on the Linux area at root`() {
        val state = core.initial()

        assertEquals(guestId, state.areaId)
        assertEquals("PocketShell Linux", state.areaName)
        assertEquals("/root", state.path!!.value)
        assertFalse(state.loading)
        assertNull(state.error)
        val names = state.entries.map { it.name }
        assertTrue("projects in $names", "projects" in names)
        assertTrue("welcome.txt in $names", "welcome.txt" in names)
        assertTrue("dotfiles are listed", ".env" in names)
        // directories first
        assertEquals(EntryKind.DIRECTORY, state.entries.first().kind)
    }

    @Test
    fun `area options carry labels and selection`() {
        val state = core.initial()

        assertEquals(2, state.areas.size)
        assertEquals("Linux", state.areas[0].label)
        assertTrue(state.areas[0].selected)
        assertEquals(guestId, state.areas[0].id)
        assertEquals("Downloads", state.areas[1].label)
        assertFalse(state.areas[1].selected)
        assertEquals(shelfId, state.areas[1].id)
    }

    @Test
    fun `entries are directories first then case-insensitive by name`() {
        core.initial()
        Files.createDirectories(rootfs.resolve("root/sort"))
        Files.createDirectories(rootfs.resolve("root/sort/Beta"))
        Files.write(rootfs.resolve("root/sort/Zeta"), "z".toByteArray())
        Files.write(rootfs.resolve("root/sort/alpha"), "a".toByteArray())

        val state = core.openChild("sort")

        // Explicit expectation: the directory "Beta" first, then files
        // alpha < Zeta under case-insensitive comparison.
        assertEquals(listOf("Beta", "alpha", "Zeta"), state.entries.map { it.name })
        assertEquals(EntryKind.DIRECTORY, state.entries[0].kind)
        assertEquals(EntryKind.FILE, state.entries[1].kind)
        assertEquals(EntryKind.FILE, state.entries[2].kind)
    }

    // ---------------------------------------------------------- navigation

    @Test
    fun `opening a directory navigates and lists its contents`() {
        core.initial()

        val projects = core.openChild("projects")
        assertEquals("/root/projects", projects.path!!.value)
        assertNull(projects.error)
        assertEquals(EntryKind.DIRECTORY, projects.entries.first { it.name == "empty-dir" }.kind)
        assertEquals(11L, projects.entries.first { it.name == "notes.txt" }.sizeBytes)
        assertTrue(projects.canNavigateUp)

        val empty = core.openChild("empty-dir")
        assertEquals("/root/projects/empty-dir", empty.path!!.value)
        assertTrue(empty.entries.isEmpty())
        assertNull(empty.error)

        val up = core.navigateUp()
        assertEquals("/root/projects", up.path!!.value)
        val upAgain = core.navigateUp()
        assertEquals("/root", upAgain.path!!.value)
    }

    @Test
    fun `navigate up stops at the area root and never escapes it`() {
        core.initial()

        val atRoot = core.navigateUp()
        assertEquals("/", atRoot.path!!.value)
        assertFalse(atRoot.canNavigateUp)

        // At the boundary: further up is a NO-OP — no error, no move, no fake parent.
        val stuck = core.navigateUp()
        assertEquals("/", stuck.path!!.value)
        assertFalse(stuck.canNavigateUp)
        assertNull(stuck.error)
        assertEquals(guestId, stuck.areaId)
    }

    @Test
    fun `child navigation refuses traversal and malformed names without moving`() {
        core.initial()

        for (bad in listOf("..", ".", "a/b", "", " ", "\u0000x", "/etc", "/etc/passwd")) {
            val state = core.openChild(bad)
            assertNotNull("error expected for '$bad'", state.error)
            assertEquals("location unchanged after '$bad'", "/root", state.path!!.value)
            assertFalse(state.loading)
        }
    }

    @Test
    fun `loading stage keeps the visible location and clears stale errors`() {
        core.initial()
        core.openChild("no-such-dir") // seed a stale error

        val staged = core.stageLoading()
        assertTrue(staged.loading)
        assertNull("stale error cleared", staged.error)
        assertEquals("/root", staged.path!!.value)

        val landed = core.openChild("projects")
        assertFalse(landed.loading)
        assertEquals("/root/projects", landed.path!!.value)
    }

    // --------------------------------------------------------- area switch

    @Test
    fun `switching areas lands on the start path and remembers per-area location`() {
        core.initial()
        core.openChild("projects")

        val shelfState = core.switchArea(shelfId)
        assertEquals(shelfId, shelfState.areaId)
        assertEquals("/", shelfState.path!!.value)
        assertTrue("site.zip" in shelfState.entries.map { it.name })
        assertTrue(shelfState.areas.first { it.id == shelfId }.selected)
        assertFalse(shelfState.areas.first { it.id == guestId }.selected)

        val back = core.switchArea(guestId)
        assertEquals(guestId, back.areaId)
        assertEquals("remembered location restored", "/root/projects", back.path!!.value)
        assertTrue("notes.txt" in back.entries.map { it.name })
    }

    @Test
    fun `switching to the current area is a no-op`() {
        val initial = core.initial()
        val again = core.switchArea(guestId)
        assertEquals(initial.path, again.path)
        assertEquals(initial.entries, again.entries)
        assertNull(again.error)
    }

    @Test
    fun `switching to an unknown area is refused honestly`() {
        core.initial()
        val state = core.switchArea(AreaId(AreaKind.ANDROID_DOCUMENT_TREE, "ghost"))
        assertNotNull(state.error)
        assertEquals(guestId, state.areaId)
        assertEquals("/root", state.path!!.value)
    }

    // -------------------------------------------------------------- errors

    @Test
    fun `listing a vanished directory reports an honest error and retry recovers`() {
        core.initial()
        core.openChild("projects")
        val projectDir = rootfs.resolve("root/projects")

        // Vanish BEHIND the core (external change) — children first so the
        // host-side delete of the scaffolding dir succeeds.
        projectDir.resolve("notes.txt").toFile().delete()
        projectDir.resolve("empty-dir").toFile().delete()
        Files.delete(projectDir)

        val failed = core.refresh()
        assertNotNull("honest error required", failed.error)
        assertTrue(failed.error!!.contains("/root/projects"))
        assertEquals("/root/projects", failed.path!!.value) // location kept, not faked
        assertFalse(failed.loading)

        Files.createDirectories(projectDir)
        val recovered = core.refresh()
        assertNull(recovered.error)
        assertTrue(recovered.entries.isEmpty())
        assertFalse(recovered.loading)
    }

    @Test
    fun `initial listing of a missing start path is honest`() {
        val bare = Files.createTempDirectory("pocketshell-bare-rootfs")
        try {
            val bareCore = ExplorerCore(
                listOf(
                    ExplorerCore.AreaHandle(
                        area = FileDirArea.create(
                            root = bare.toFile(),
                            id = guestId,
                            displayName = "PocketShell Linux",
                            policy = FileDirArea.MutationPolicy.guestRuntime(),
                        )!!,
                        startPath = PathSafety.validatePath("/root")!!,
                        shortLabel = "Linux",
                    ),
                ),
            )
            val state = bareCore.initial()
            assertNotNull(state.error)
            assertEquals("/root", state.path!!.value) // attempted honestly, not hidden
            assertFalse(state.loading)
        } finally {
            bare.toFile().deleteRecursively()
        }
    }

    // ------------------------------------------------------- entry fidelity

    @Test
    fun `symlink entries are reported as nodes with their raw target`() {
        core.initial()

        val entry = core.snapshot().entries.first { it.name == "bin-link" }
        assertEquals(EntryKind.SYMLINK, entry.kind)
        assertEquals(rootfs.resolve("bin").toString(), entry.symlinkTarget)
        assertNull(entry.sizeBytes)
    }

    @Test
    fun `file sizes are surfaced for the listing metadata`() {
        core.initial()

        val entry = core.snapshot().entries.first { it.name == "welcome.txt" }
        assertEquals(11L, entry.sizeBytes)
    }

    @Test
    fun `no available areas is an honest empty state`() {
        val emptyCore = ExplorerCore(emptyList())
        val state = emptyCore.initial()
        assertNull(state.areaId)
        assertFalse(state.loading)
        assertNotNull(state.error)
    }
}

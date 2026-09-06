package app.pocketshell.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * M7.0.0 Phase 7 — the pure "Open Terminal Here" model and area-kind gate.
 *
 * The launch is offered for exactly ONE storage world: [AreaKind.GUEST_LINUX].
 * Android-owned areas get the honest boundary explanation — never a launch,
 * never a fabricated POSIX path for Android storage. The request carries the
 * explorer's own validated [AreaPath] untouched: Phase 7 introduces NO second
 * validation and NO second path representation.
 */
class TerminalLaunchTest {

    // ------------------------------------------------------------- the gate

    @Test
    fun `guest linux is allowed - the gate returns null`() {
        assertEquals(null, openTerminalHereProblem(AreaKind.GUEST_LINUX))
    }

    @Test
    fun `android shelf is denied with the honest boundary message`() {
        assertEquals(
            TerminalLaunchSupport.ANDROID_BOUNDARY_MESSAGE,
            openTerminalHereProblem(AreaKind.ANDROID_SHELF),
        )
    }

    @Test
    fun `android document tree is denied with the honest boundary message`() {
        assertEquals(
            TerminalLaunchSupport.ANDROID_BOUNDARY_MESSAGE,
            openTerminalHereProblem(AreaKind.ANDROID_DOCUMENT_TREE),
        )
    }

    @Test
    fun `the boundary message is the exact product wording and stays honest`() {
        val message = TerminalLaunchSupport.ANDROID_BOUNDARY_MESSAGE
        // The exact product-mandated sentence, pinned character for character.
        assertEquals(
            "Android folders are not Linux guest directories. " +
                "Copy or move files into PocketShell Linux to work with them in Terminal.",
            message,
        )
        // Honesty properties (defense in depth against future rewording):
        // it states the boundary truth…
        assertTrue(message.contains("not Linux guest directories"))
        // …it names the REAL workflow instead of promising a fake launch…
        assertTrue(message.contains("Copy or move files into PocketShell Linux"))
        // …and it never fabricates an Android storage path as a Linux cwd
        // (no forged /storage/emulated path, no content:// URI dressed up
        // as a POSIX directory).
        assertFalse(message.contains("/storage/emulated"))
        assertFalse(message.contains("content://"))
    }

    // -------------------------------------------------------- the launch request

    @Test
    fun `launch request preserves area kind identity and the exact validated AreaPath`() {
        val path = PathSafety.validatePath("/root/projects/my app")!!
        val id = AreaId(AreaKind.GUEST_LINUX)
        val launch = TerminalLaunch(areaId = id, kind = AreaKind.GUEST_LINUX, directory = path)
        assertSame("area identity preserved", id, launch.areaId)
        assertEquals("area kind preserved", AreaKind.GUEST_LINUX, launch.kind)
        assertSame("the exact validated AreaPath is preserved", path, launch.directory)
        assertEquals("/root/projects/my app", launch.directory.value)
    }

    @Test
    fun `no second validation or path representation is introduced`() {
        // The directory type in the launch model IS the Phase 2 AreaPath, and
        // the SAME instance that enters comes out: no re-validation (which
        // could silently differ), no String/File/URI shadow representation.
        val path = PathSafety.validatePath("/root/my 'quoted' \$dir")!!
        val launch = TerminalLaunch(AreaId(AreaKind.GUEST_LINUX), AreaKind.GUEST_LINUX, path)
        assertSame(path, launch.directory)
        assertEquals(path.value, launch.directory.value)
        assertEquals(
            listOf("root", "my 'quoted' \$dir"),
            launch.directory.components,
        )
    }

    @Test
    fun `the launch model refuses non-linux kinds at construction`() {
        // Fail fast if a refused area ever reaches the launch model — the
        // data class itself upholds what the gate promises.
        for (kind in listOf(AreaKind.ANDROID_SHELF, AreaKind.ANDROID_DOCUMENT_TREE)) {
            try {
                TerminalLaunch(AreaId(kind), kind, PathSafety.validatePath("/")!!)
                fail("kind $kind must never construct a terminal launch")
            } catch (expected: IllegalArgumentException) {
                // the honest refusal
            }
        }
    }

    @Test
    fun `resolution is exhaustively ready or not-supported - the compile-time sealed contract`() {
        // No reflection needed: constructing both outcomes and matching them
        // EXHAUSTIVELY proves the compiler knows of no third state — a launch
        // is either really ready or honestly refused.
        val ready: TerminalLaunchResolution = TerminalLaunchResolution.Ready(
            TerminalLaunch(AreaId(AreaKind.GUEST_LINUX), AreaKind.GUEST_LINUX, PathSafety.validatePath("/root")!!),
        )
        val denied: TerminalLaunchResolution = TerminalLaunchResolution.NotSupported("honest reason")
        assertTrue(ready is TerminalLaunchResolution.Ready)
        assertTrue(denied is TerminalLaunchResolution.NotSupported)
        for (outcome in listOf(ready, denied)) {
            when (outcome) {
                is TerminalLaunchResolution.Ready -> assertEquals("/root", outcome.launch.directory.value)
                is TerminalLaunchResolution.NotSupported -> assertEquals("honest reason", outcome.reason)
            }
        }
    }

    // --------------------------------------- p7.1 — the TAPPED-folder resolution

    @Test
    fun `p7-1 the launch resolves the TAPPED folder not the browsed location`() {
        // The p7.0 device report: tapping "Open Terminal Here" on a folder
        // opened the terminal in the browsed parent. The resolution composes
        // the SELECTED entry under the browsed location — never the browsed
        // location itself.
        val browsed = PathSafety.validatePath("/root")!!
        val entries = listOf(
            FsEntry("projects", EntryKind.DIRECTORY, null, null),
            FsEntry("notes.txt", EntryKind.FILE, 10L, null),
        )
        val resolved = terminalLaunchDirectory(browsed, entries, "projects")!!
        assertEquals("/root/projects", resolved.value)
        // …and the resolved directory is genuinely NOT the browsed location.
        assertTrue(resolved !== browsed)
    }

    @Test
    fun `p7-1 metachar folder names compose as literal validated data`() {
        // The name the user tapped is what pwd must print — composed through
        // the SAME validated path system, still pure DATA (quoting happens
        // once, later, at PTY-argv build time).
        val browsed = PathSafety.validatePath("/root")!!
        val entries = listOf(
            FsEntry("my 'quoted' \$dir", EntryKind.DIRECTORY, null, null),
            FsEntry("it's-here", EntryKind.DIRECTORY, null, null),
        )
        assertEquals(
            "/root/my 'quoted' \$dir",
            terminalLaunchDirectory(browsed, entries, "my 'quoted' \$dir")!!.value,
        )
        assertEquals(
            "/root/it's-here",
            terminalLaunchDirectory(browsed, entries, "it's-here")!!.value,
        )
    }

    @Test
    fun `p7-1 non-directory entries never resolve - no somewhere-else fallback`() {
        val browsed = PathSafety.validatePath("/root")!!
        val entries = listOf(FsEntry("notes.txt", EntryKind.FILE, 10L, null))
        // A file (or symlink/other) can never become a terminal cwd; returning
        // the browsed directory instead would be exactly the p7.0 bug again.
        for (name in listOf("notes.txt", "missing-folder")) {
            assertEquals(null, terminalLaunchDirectory(browsed, entries, name))
        }
    }

    @Test
    fun `p7-1 a name that cannot validate resolves to null - the honest refusal`() {
        // Path traversal in a (hypothetical, sheet-forged) name is refused by
        // the SAME PathSafety validation every navigation uses — no second
        // validator, and never a silent fallback to the browsed directory.
        val browsed = PathSafety.validatePath("/root")!!
        val entries = listOf(FsEntry("..", EntryKind.DIRECTORY, null, null))
        assertEquals(null, terminalLaunchDirectory(browsed, entries, ".."))
    }
}

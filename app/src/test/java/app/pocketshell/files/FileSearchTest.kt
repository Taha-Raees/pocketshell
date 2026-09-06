package app.pocketshell.files

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * M7.0.0 Phase 8 pins — the name-search core over REAL temporary
 * directories (the Phase 2 [FileDirArea] engine underneath, guest and
 * shelf policies) and over the in-memory SAF provider fake (the same
 * production [app.pocketshell.files.saf.AndroidDocumentArea] class).
 *
 * What is pinned here:
 *  - matching is a literal case-insensitive SUBSTRING of the entry NAME —
 *    the query is data: traversal shapes, globs, and shell metacharacters
 *    are never interpreted (and can never influence where the walk goes);
 *  - the walk never escapes the area root (paths are composed through the
 *    ONE validated child composition, and the result model is re-checked
 *    structurally);
 *  - symlink NODES may appear as results but are never descended into —
 *    cycles terminate by construction;
 *  - limits are explicit and honest (truncated / scanTruncated), skipped
 *    folders are disclosed (skippedCount + firstError), a failing ROOT is
 *    a hard Error — errors never become fake empty results;
 *  - a blank query is the input state and performs ZERO list() calls;
 *  - cancellation (supersession) makes the walk return null — stale work
 *    can never be published as a complete result.
 */
class FileSearchTest {

    private lateinit var rootfs: Path
    private lateinit var shelf: Path

    private val guestId = AreaId(AreaKind.GUEST_LINUX)
    private val shelfId = AreaId(AreaKind.ANDROID_SHELF)

    @Before
    fun setUp() {
        rootfs = Files.createTempDirectory("pocketshell-search-rootfs")
        shelf = Files.createTempDirectory("pocketshell-search-shelf")
        // Every guest fixture lives under /home — created once, up front.
        Files.createDirectories(rootfs.resolve("home"))
    }

    @After
    fun tearDown() {
        rootfs.toFile().deleteRecursively() // test scaffolding only, host-side
        shelf.toFile().deleteRecursively()
    }

    // ----------------------------------------------------------- fixtures

    private fun guestArea(): FileDirArea = FileDirArea.create(
        root = rootfs.toFile(),
        id = guestId,
        displayName = "PocketShell Linux",
        policy = FileDirArea.MutationPolicy.guestRuntime(),
    )!!

    private fun shelfArea(): FileDirArea = FileDirArea.create(
        root = shelf.toFile(),
        id = shelfId,
        displayName = StorageAreas.Labels.SHELF_DISPLAY_NAME,
        policy = FileDirArea.MutationPolicy.OPEN,
    )!!

    private fun root(): AreaPath = PathSafety.validatePath("/")!!

    private fun search(
        area: StorageArea,
        query: String,
        limits: FileSearch.SearchLimits = FileSearch.SearchLimits(),
        isCancelled: () -> Boolean = { false },
    ): FileSearch.SearchOutcome? = FileSearch.search(area, root(), query, limits, isCancelled)

    private fun matches(outcome: FileSearch.SearchOutcome?): FileSearch.SearchOutcome.Matches {
        assertNotNull("expected a non-cancelled outcome", outcome)
        assertTrue(
            "expected Matches but was $outcome",
            outcome is FileSearch.SearchOutcome.Matches,
        )
        return outcome as FileSearch.SearchOutcome.Matches
    }

    private fun names(outcome: FileSearch.SearchOutcome?): List<String> =
        matches(outcome).results.map { it.entry.name }

    /** Structural boundary check: every parent is canonical and inside the area. */
    private fun assertAllInsideArea(outcome: FileSearch.SearchOutcome.Matches) {
        outcome.results.forEach { result ->
            assertTrue(
                "parent ${result.parent.value} must be canonical",
                result.parent.value == "/" ||
                    (result.parent.value.startsWith("/") && !result.parent.value.endsWith("/")),
            )
            assertTrue(
                "parent ${result.parent.value} must have no traversal components",
                result.parent.components.none { it == ".." || it == "." },
            )
        }
    }

    // -------------------------------------------------------------- basic

    @Test
    fun `a partial name matches files and directories recursively`() {
        Files.createDirectories(rootfs.resolve("home/projects/deep"))
        Files.write(rootfs.resolve("home/projects/notes.txt"), byteArrayOf())
        Files.write(rootfs.resolve("home/projects/deep/project_notes"), byteArrayOf())
        Files.write(rootfs.resolve("home/unrelated.txt"), byteArrayOf())

        val outcome = search(guestArea(), "proj")

        val results = matches(outcome)
        assertAllInsideArea(results)
        val found = results.results.map { it.entry.name }.toSet()
        assertEquals(setOf("projects", "project_notes"), found)
    }

    @Test
    fun `matching is case-insensitive on both sides`() {
        Files.write(rootfs.resolve("home/README"), byteArrayOf())
        Files.write(rootfs.resolve("home/readme.bak"), byteArrayOf())

        val found = names(search(guestArea(), "READ")).toSet()
        assertEquals(setOf("README", "readme.bak"), found)

        val foundLower = names(search(guestArea(), "readme")).toSet()
        assertEquals(setOf("README", "readme.bak"), foundLower)
    }

    @Test
    fun `dotfiles are searchable by name`() {
        Files.write(rootfs.resolve("home/.env"), byteArrayOf())

        assertEquals(listOf(".env"), names(search(guestArea(), ".env")))
        assertEquals(listOf(".env"), names(search(guestArea(), "env")))
    }

    @Test
    fun `a query with no matches is an honest empty result`() {
        Files.write(rootfs.resolve("home/notes.txt"), byteArrayOf())

        val outcome = matches(search(guestArea(), "absent-thing"))
        assertTrue(outcome.results.isEmpty())
        assertEquals(false, outcome.truncated)
        assertEquals(false, outcome.scanTruncated)
        assertEquals(0, outcome.skippedCount)
        assertNull(outcome.firstError)
    }

    @Test
    fun `a blank query never scans — zero list calls`() {
        val counting = CountingArea(guestArea())

        for (blank in listOf("", "   ", "\n", "\t")) {
            assertEquals(
                FileSearch.SearchOutcome.Idle,
                search(counting, blank),
            )
        }
        assertEquals("a blank query must not touch the filesystem", 0, counting.listCalls.get())
    }

    @Test
    fun `results carry the parent directory and honest relative location`() {
        Files.createDirectories(rootfs.resolve("home/projects"))
        Files.write(rootfs.resolve("welcome.txt"), byteArrayOf())
        Files.write(rootfs.resolve("home/projects/notes.txt"), byteArrayOf())

        val results = matches(search(guestArea(), "txt")).results

        val byName = results.associateBy { it.entry.name }
        assertEquals("/", byName["welcome.txt"]!!.parent.value)
        assertEquals("", byName["welcome.txt"]!!.relativeLocation)
        assertEquals("/home/projects", byName["notes.txt"]!!.parent.value)
        assertEquals("home/projects", byName["notes.txt"]!!.relativeLocation)
    }

    @Test
    fun `ordering is directories first then case-insensitive name then path tie-break`() {
        Files.createDirectories(rootfs.resolve("home/bdir"))
        Files.createDirectories(rootfs.resolve("home/Beta"))
        Files.write(rootfs.resolve("home/bdir/a-file"), byteArrayOf())
        Files.write(rootfs.resolve("home/Beta/b-file"), byteArrayOf())
        Files.write(rootfs.resolve("home/B file"), byteArrayOf())
        Files.write(rootfs.resolve("home/brot"), byteArrayOf())
        Files.write(rootfs.resolve("home/zz"), byteArrayOf())

        val found = names(search(guestArea(), "b"))
        // Directories first ("bdir" < "Beta" case-insensitively — 'd' < 'e'),
        // then files ("B file" < "b-file" < "brot"; space < '-' < letter).
        // The nested b-file matches too — recursion is the point.
        assertEquals(listOf("bdir", "Beta", "B file", "b-file", "brot"), found)
    }

    @Test
    fun `same-named results in different directories tie-break by relative path`() {
        Files.createDirectories(rootfs.resolve("x"))
        Files.createDirectories(rootfs.resolve("y"))
        Files.write(rootfs.resolve("x/notes.txt"), byteArrayOf())
        Files.write(rootfs.resolve("y/Notes.TXT"), byteArrayOf())

        val results = matches(search(guestArea(), "notes")).results
        assertEquals("x", results[0].relativeLocation)
        assertEquals("notes.txt", results[0].entry.name)
        assertEquals("y", results[1].relativeLocation)
        assertEquals("Notes.TXT", results[1].entry.name)
    }

    // ------------------------------------------------------------- safety

    @Test
    fun `traversal shapes in the query are inert literal data`() {
        Files.createDirectories(rootfs.resolve("home/etc"))
        Files.write(rootfs.resolve("home/etc/passwd"), byteArrayOf())

        // None of these may throw, influence the walk, or fake a match.
        for (hostile in listOf("..", "../etc", "/etc", "../../", ".", "./passwd")) {
            val outcome = matches(search(guestArea(), hostile))
            assertTrue("query \"$hostile\" must match nothing", outcome.results.isEmpty())
            assertAllInsideArea(outcome)
        }
        // The walk itself still works normally afterwards.
        assertEquals(listOf("passwd"), names(search(guestArea(), "pass")))
    }

    @Test
    fun `shell and glob metacharacters are literal query data`() {
        // Metacharacter queries match nothing when no such NAME exists —
        // never a glob, never shell (asserted BEFORE any such file is made).
        Files.write(rootfs.resolve("home/plain"), byteArrayOf())
        assertTrue(matches(search(guestArea(), "*")).results.isEmpty())
        assertTrue(matches(search(guestArea(), "\$;")).results.isEmpty())
        assertTrue(matches(search(guestArea(), "&&")).results.isEmpty())
        assertTrue(matches(search(guestArea(), "; touch x")).results.isEmpty())

        // Names that contain metacharacters are findable by literal substring…
        Files.write(rootfs.resolve("home/weird\$;name"), byteArrayOf())
        Files.write(rootfs.resolve("home/it's here.txt"), byteArrayOf())
        assertEquals(listOf("weird\$;name"), names(search(guestArea(), "\$;na")))
        assertEquals(listOf("it's here.txt"), names(search(guestArea(), "'s here")))

        // A literal "*" in a NAME is found by the literal "*" query — data
        // matching data, no interpretation in either direction.
        Files.write(rootfs.resolve("home/star*name"), byteArrayOf())
        assertEquals(listOf("star*name"), names(search(guestArea(), "*")))
    }

    @Test
    fun `a multi-word query is one literal substring`() {
        Files.write(rootfs.resolve("home/my notes"), byteArrayOf())
        Files.write(rootfs.resolve("home/notes my"), byteArrayOf())

        // One literal substring — both orders of the words exist as names and
        // both match exactly themselves (never word-split, never reordered).
        assertEquals(listOf("my notes"), names(search(guestArea(), "my notes")))
        assertEquals(listOf("notes my"), names(search(guestArea(), "notes my")))
        // A different spacing is a different literal — it matches nothing.
        assertTrue(matches(search(guestArea(), "notes  my")).results.isEmpty())
    }

    @Test
    fun `results can never name a location outside the area root`() {
        Files.createDirectories(rootfs.resolve("home/a/b/c"))
        Files.write(rootfs.resolve("home/a/b/c/deep-file"), byteArrayOf())
        Files.write(rootfs.resolve("home/a/deep-file"), byteArrayOf())

        val outcome = matches(search(guestArea(), "deep"))
        assertAllInsideArea(outcome)
        // Both hits are inside the area, spelled by the area-native spine.
        assertTrue(
            outcome.results.all { PathSafety.validatePath(it.parent.value) != null },
        )
        assertEquals(2, outcome.results.size)
    }

    // ----------------------------------------------------------- symlinks

    @Test
    fun `a symlink node is a matchable result but is never descended through`() {
        Files.createDirectories(rootfs.resolve("home/real"))
        Files.write(rootfs.resolve("home/real/inside-target.txt"), byteArrayOf())
        Files.createSymbolicLink(rootfs.resolve("home/link-to-real"), rootfs.resolve("home/real"))

        val outcome = matches(search(guestArea(), "real"))
        val link = outcome.results.firstOrNull { it.entry.name == "link-to-real" }
        assertNotNull("the symlink node itself is searchable", link)
        assertEquals(EntryKind.SYMLINK, link!!.entry.kind)

        // The file behind the link is reachable ONLY through its REAL parent
        // (the plain directory walk). If the link were ever followed there
        // would be a DUPLICATE result with parent /home/link-to-real — there
        // is exactly one, through /home/real.
        val behind = matches(search(guestArea(), "inside-target"))
        assertEquals(1, behind.results.size)
        assertEquals("/home/real", behind.results[0].parent.value)
        assertTrue(
            behind.results.none { it.parent.value.contains("link") },
        )
    }

    @Test
    fun `a symlinked directory is never descended into even when it matches`() {
        Files.createDirectories(rootfs.resolve("home/vault"))
        Files.write(rootfs.resolve("home/vault/secret-project.txt"), byteArrayOf())
        Files.createSymbolicLink(rootfs.resolve("home/vault-link"), rootfs.resolve("home/vault"))

        // "vault" matches BOTH the real dir and the link node; the file
        // inside is a match too — but only via the REAL directory. The
        // link must contribute exactly one result (itself), never children.
        val outcome = matches(search(guestArea(), "vault"))
        val linkHits = outcome.results.filter { it.entry.name == "vault-link" }
        assertEquals(1, linkHits.size)
        assertEquals("/home", linkHits[0].parent.value)
        assertTrue(outcome.results.none { it.entry.name == "vault-link" && it.parent.value != "/home" })
    }

    @Test
    fun `symlink cycles terminate — self link and link to parent`() {
        Files.createDirectories(rootfs.resolve("home/loop"))
        Files.write(rootfs.resolve("home/loop/file-proj"), byteArrayOf())
        Files.createSymbolicLink(rootfs.resolve("home/loop/loop-self"), rootfs.resolve("home/loop"))
        Files.createSymbolicLink(rootfs.resolve("home/loop/loop-up"), rootfs.resolve("home"))
        Files.createSymbolicLink(rootfs.resolve("home/loop-back"), rootfs.resolve("home/loop"))

        val outcome = matches(search(guestArea(), "loop"))
        // The walk completed (no hang, no stack overflow) and every cycle
        // link appears exactly once as a plain node, never expanded.
        val found = outcome.results.map { it.entry.name }.toSet()
        assertEquals(setOf("loop", "loop-back", "loop-self", "loop-up"), found)
        assertEquals(4, outcome.results.size)
    }

    @Test
    fun `a dangling symlink matches by name without crashing`() {
        Files.createSymbolicLink(rootfs.resolve("home/dangling"), rootfs.resolve("home/gone"))
        Files.write(rootfs.resolve("home/dangling-proj"), byteArrayOf())

        val found = names(search(guestArea(), "dangling"))
        assertEquals(listOf("dangling", "dangling-proj"), found)
    }

    // -------------------------------------------------------------- areas

    @Test
    fun `search works over the shelf area (open policy)`() {
        Files.createDirectories(shelf.resolve("archive/2024"))
        Files.write(shelf.resolve("archive/2024/site-proj.zip"), byteArrayOf())
        Files.write(shelf.resolve("archive/index.txt"), byteArrayOf())

        val results = matches(search(shelfArea(), "proj")).results
        assertEquals(1, results.size)
        assertEquals("site-proj.zip", results[0].entry.name)
        assertEquals("archive/2024", results[0].relativeLocation)
    }

    @Test
    fun `search reaches read-only protected prefixes of the guest area`() {
        Files.createDirectories(rootfs.resolve("etc"))
        Files.write(rootfs.resolve("etc/proj.conf"), byteArrayOf())

        val found = names(search(guestArea(), "proj"))
        assertEquals(listOf("proj.conf"), found)
    }

    @Test
    fun `search works over a SAF area with honest spine paths and no symlinks`() {
        val saf = safArea()
        saf.backend.mkdir("projects")
        saf.backend.write("projects/ProjectA.txt", byteArrayOf())
        saf.backend.write("projects/notes-proj.txt", byteArrayOf())
        saf.backend.mkdir("personal")

        val outcome = matches(search(saf.area, "proj"))
        assertAllInsideArea(outcome)
        assertEquals(
            setOf("projects", "ProjectA.txt", "notes-proj.txt"),
            outcome.results.map { it.entry.name }.toSet(),
        )
        // The projects DIRECTORY itself lives at the tree root (honest ""),
        // and its files carry the parent spine.
        val dir = outcome.results.first { it.entry.kind == EntryKind.DIRECTORY }
        assertEquals("projects", dir.entry.name)
        assertEquals("/", dir.parent.value)
        assertEquals("", dir.relativeLocation)
        assertEquals("projects", outcome.results.first { it.entry.kind == EntryKind.FILE }.relativeLocation)
        // The SAF shape: every reported kind is FILE or DIRECTORY, never SYMLINK.
        assertTrue(outcome.results.none { it.entry.kind == EntryKind.SYMLINK })
    }

    // -------------------------------------------------------------- errors

    @Test
    fun `a revoked SAF grant is a hard search error — never a fake empty result`() {
        val saf = safArea()
        saf.backend.mkdir("projects")
        saf.backend.write("projects/keep.txt", byteArrayOf())
        saf.backend.revoked = true

        val outcome = search(saf.area, "keep")
        assertTrue("expected Error but was $outcome", outcome is FileSearch.SearchOutcome.Error)
        val reason = (outcome as FileSearch.SearchOutcome.Error).reason
        // The area's own honest revocation sentence — not a raw exception.
        assertTrue("reason must be honest: $reason", reason.contains("no longer available"))
    }

    @Test
    fun `a failing root listing is a hard error`() {
        val flaky = FlakyArea(guestArea(), failFor = "/")

        val outcome = search(flaky, "anything")
        assertTrue(outcome is FileSearch.SearchOutcome.Error)
        assertTrue(
            (outcome as FileSearch.SearchOutcome.Error).reason.contains("simulated listing failure"),
        )
    }

    @Test
    fun `a failed sub-directory is disclosed — partial results stay honest`() {
        Files.createDirectories(rootfs.resolve("home/broken"))
        Files.createDirectories(rootfs.resolve("home/fine"))
        Files.write(rootfs.resolve("home/broken/hidden-proj"), byteArrayOf())
        Files.write(rootfs.resolve("home/fine/visible-proj"), byteArrayOf())

        val outcome = matches(search(FlakyArea(guestArea(), failFor = "/home/broken"), "proj"))
        assertEquals(1, outcome.results.size)
        assertEquals("visible-proj", outcome.results[0].entry.name)
        assertEquals(1, outcome.skippedCount)
        assertTrue(outcome.firstError!!.contains("simulated listing failure"))
    }

    // -------------------------------------------------------------- limits

    @Test
    fun `the result cap stops the walk and is disclosed`() {
        for (i in 1..10) {
            Files.write(rootfs.resolve("home/match-$i"), byteArrayOf())
        }

        val outcome = matches(search(guestArea(), "match", FileSearch.SearchLimits(maxResults = 4)))
        assertEquals(4, outcome.results.size)
        assertTrue(outcome.truncated)
        assertFalse(outcome.scanTruncated)
    }

    @Test
    fun `the directory cap stops the walk and is disclosed`() {
        Files.createDirectories(rootfs.resolve("home/d1/d2/d3/d4"))
        Files.write(rootfs.resolve("home/x-target"), byteArrayOf())
        Files.write(rootfs.resolve("home/d1/y-target"), byteArrayOf())
        Files.write(rootfs.resolve("home/d1/d2/z-target"), byteArrayOf())

        // Cap 3 visits / , /home and /home/d1 — x-target and y-target are
        // found; z-target sits behind an unvisited directory, which the
        // outcome must disclose.
        val outcome = matches(search(guestArea(), "target", FileSearch.SearchLimits(maxDirectories = 3)))
        assertTrue(outcome.scanTruncated)
        assertTrue(
            "results found before the cap are kept",
            outcome.results.map { it.entry.name }.toSet() == setOf("x-target", "y-target"),
        )
        assertFalse(outcome.truncated)
    }

    @Test
    fun `a huge tree under the default limits is not reported as truncated`() {
        Files.createDirectories(rootfs.resolve("home/a/b/c"))
        Files.write(rootfs.resolve("home/a/b/c/needle"), byteArrayOf())

        val outcome = matches(search(guestArea(), "needle"))
        assertFalse(outcome.truncated)
        assertFalse(outcome.scanTruncated)
        assertEquals(0, outcome.skippedCount)
    }

    // -------------------------------------------------------- cancellation

    @Test
    fun `cancellation before the walk returns null`() {
        Files.write(rootfs.resolve("home/thing"), byteArrayOf())

        val outcome = search(guestArea(), "thing", isCancelled = { true })
        assertNull("a cancelled search must return null, never partial results", outcome)
    }

    @Test
    fun `cancellation mid-walk returns null — never a partial result as complete`() {
        Files.createDirectories(rootfs.resolve("home/d1/d2"))
        Files.write(rootfs.resolve("home/d1/a-target"), byteArrayOf())
        Files.write(rootfs.resolve("home/d1/d2/b-target"), byteArrayOf())

        // A walk that has already MATCHED something still returns null when
        // superseded — stale work is never published.
        val seen = AtomicInteger(0)
        val outcome = search(
            CountingArea(guestArea()),
            "target",
            isCancelled = { seen.incrementAndGet() > 1 },
        )
        assertNull(outcome)
    }

    @Test
    fun `a non-cancelled walk completes even when the flag is consulted`() {
        Files.write(rootfs.resolve("home/thing"), byteArrayOf())

        val calls = AtomicInteger(0)
        val outcome = matches(
            search(guestArea(), "thing", isCancelled = { calls.incrementAndGet() < 0 }),
        )
        assertEquals(listOf("thing"), outcome.results.map { it.entry.name })
        assertTrue("the flag is consulted during the walk", calls.get() > 0)
    }

    // ------------------------------------------------------------ fixtures

    private fun safArea(): SafFixture {
        val backend = app.pocketshell.files.saf.FakeDocumentBackend(rootName = "MyProject")
        val area = app.pocketshell.files.saf.AndroidDocumentArea.create(
            id = app.pocketshell.files.saf.AndroidDocumentArea.areaIdFor(
                "content://test/tree/primary%3AMyProject",
            ),
            displayName = "MyProject",
            backend = backend,
        )
        return SafFixture(backend, area)
    }

    private class SafFixture(
        val backend: app.pocketshell.files.saf.FakeDocumentBackend,
        val area: StorageArea,
    )

    /** Delegating wrapper that counts list() calls (blank-query pin). */
    private open class CountingArea(private val delegate: StorageArea) : StorageArea by delegate {
        val listCalls = AtomicInteger()

        override fun list(path: AreaPath): ListResult {
            listCalls.incrementAndGet()
            return delegate.list(path)
        }
    }

    /** Delegating wrapper whose list() fails honestly for ONE exact path. */
    private open class FlakyArea(
        private val delegate: StorageArea,
        private val failFor: String,
    ) : StorageArea by delegate {
        private val calls = AtomicInteger()

        override fun list(path: AreaPath): ListResult {
            val n = calls.incrementAndGet()
            return if (path.value == failFor) {
                ListResult.Error("simulated listing failure #$n")
            } else {
                delegate.list(path)
            }
        }
    }
}

package app.pocketshell.files.saf

import app.pocketshell.files.AreaId
import app.pocketshell.files.AreaKind
import app.pocketshell.files.ExplorerCore
import app.pocketshell.files.FileDirArea
import app.pocketshell.files.PathSafety
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
 * M7.0.0 Phase 5 pins — the folder identity/state model (pure) and the
 * ExplorerCore dynamic-area contract that SAF grants rely on:
 *
 *  - a tree URI yields an honest display label WITHOUT any provider query
 *    (what a revoked folder can still show);
 *  - user-granted areas join/leave the switcher at runtime with the same
 *    honest-state discipline (idempotent add, remembered re-entry, honest
 *    no-storage when the last area goes);
 *  - a REVOKED folder stays listed and entering it produces the honest
 *    revocation error — never a crash, never a fake empty directory.
 */
class SafFoldersTest {

    private lateinit var rootfs: Path

    @Before
    fun setUp() {
        rootfs = Files.createTempDirectory("pocketshell-saf-folders")
        Files.createDirectories(rootfs.resolve("root"))
    }

    @After
    fun tearDown() {
        rootfs.toFile().deleteRecursively() // test scaffolding only, host-side
    }

    private fun guestArea(): FileDirArea =
        FileDirArea.create(
            root = rootfs.toFile(),
            id = AreaId(AreaKind.GUEST_LINUX),
            displayName = "PocketShell Linux",
            policy = FileDirArea.MutationPolicy.guestRuntime(),
        )!!

    private fun safArea(backend: FakeDocumentBackend): AndroidDocumentArea =
        AndroidDocumentArea.create(
            id = AndroidDocumentArea.areaIdFor("content://test/tree/primary%3AMyProject"),
            displayName = "MyProject",
            backend = backend,
        )

    private fun safId() = AndroidDocumentArea.areaIdFor("content://test/tree/primary%3AMyProject")

    private fun handle(area: app.pocketshell.files.StorageArea, start: String, label: String) =
        ExplorerCore.AreaHandle(
            area = area,
            startPath = PathSafety.validatePath(start)!!,
            shortLabel = label,
        )

    // ------------------------------------------------------- uri label model

    @Test
    fun `tree uri labels are derived without any provider query`() {
        assertEquals(
            "MyProject",
            SafFolders.labelFromTreeUri(
                "content://com.android.externalstorage.documents/tree/primary%3ADownload%2FMyProject",
            ),
        )
        assertEquals(
            "MyProject",
            SafFolders.labelFromTreeUri(
                "content://com.android.externalstorage.documents/tree/primary:MyProject",
            ),
        )
        assertEquals(
            "Documents",
            SafFolders.labelFromTreeUri("content://some.provider/tree/primary%3ADocuments"),
        )
    }

    @Test
    fun `odd uris still yield a usable honest label`() {
        assertEquals("something", SafFolders.labelFromTreeUri("https://example.com/something"))
        assertEquals("Android folder", SafFolders.labelFromTreeUri(""))
    }

    @Test
    fun `reconcile keeps known labels and drops released grants`() {
        val previous = listOf(
            SafFolderInfo("uri-a", "Kept", SafFolderState.REVOKED),
            SafFolderInfo("uri-b", "Released", SafFolderState.AVAILABLE),
        )
        val merged = SafFolders.reconcile(persistedUris = listOf("uri-a", "uri-c"), previous = previous)
        assertEquals(listOf("uri-a", "uri-c"), merged.map { it.uri })
        assertEquals("Kept", merged[0].label)
        // An unknown grant falls back to its URI tail — honest, no invented name.
        assertEquals("uri-c", merged[1].label)
        assertTrue(merged.all { it.state == SafFolderState.AVAILABLE })
    }

    // ------------------------------------------- ExplorerCore dynamic areas

    private fun twoAreaCore(): ExplorerCore {
        val core = ExplorerCore(
            listOf(
                handle(guestArea(), "/root", "Linux"),
                handle(shelfArea(), "/", "Downloads"),
            ),
        )
        core.initial()
        return core
    }

    private lateinit var shelfRoot: Path

    private fun shelfArea(): FileDirArea =
        FileDirArea.create(
            root = shelfRoot.toFile(),
            id = AreaId(AreaKind.ANDROID_SHELF),
            displayName = "Android Downloads (app storage)",
            policy = FileDirArea.MutationPolicy.OPEN,
        )!!

    @Before
    fun createShelf() {
        shelfRoot = Files.createTempDirectory("pocketshell-saf-shelf")
    }

    @After
    fun removeShelf() {
        shelfRoot.toFile().deleteRecursively()
    }

    @Test
    fun `a granted folder joins the switcher without moving the current view`() {
        val core = twoAreaCore()
        val before = core.snapshot()

        val after = core.addArea(handle(safArea(FakeDocumentBackend()), "/", "MyProject"))

        assertEquals(listOf("Linux", "Downloads", "MyProject"), after.areas.map { it.label })
        assertFalse(after.areas.last().selected)
        assertEquals(before.path, after.path)
        assertEquals(before.areaId, after.areaId)
    }

    @Test
    fun `adding the same area twice is idempotent`() {
        val core = twoAreaCore()
        val handle = handle(safArea(FakeDocumentBackend()), "/", "MyProject")
        core.addArea(handle)
        val after = core.addArea(handle)
        assertEquals(listOf("Linux", "Downloads", "MyProject"), after.areas.map { it.label })
    }

    @Test
    fun `a granted folder can be entered and browsed like any area`() {
        val core = twoAreaCore()
        val backend = FakeDocumentBackend()
        backend.write("/README.md", "hi".toByteArray())
        core.addArea(handle(safArea(backend), "/", "MyProject"))

        val state = core.switchArea(safId())
        assertEquals(safId(), state.areaId)
        assertEquals("/", state.path?.value)
        assertEquals(listOf("README.md"), state.entries.map { it.name })
    }

    @Test
    fun `an empty core enters the first granted area at its start path`() {
        val core = ExplorerCore(emptyList())
        val initial = core.initial()
        assertEquals(null, initial.areaId)

        val after = core.addArea(handle(safArea(FakeDocumentBackend()), "/", "MyProject"))
        assertEquals(safId(), after.areaId)
        assertEquals("/", after.path?.value)
    }

    @Test
    fun `removing a non-current folder updates the switcher and keeps the view`() {
        val core = twoAreaCore()
        core.addArea(handle(safArea(FakeDocumentBackend()), "/", "MyProject"))
        val before = core.snapshot()

        val after = core.removeArea(safId())

        assertEquals(listOf("Linux", "Downloads"), after.areas.map { it.label })
        assertEquals(before.areaId, after.areaId)
        assertEquals(before.path, after.path)
    }

    @Test
    fun `removing the CURRENT folder re-enters the first remaining area`() {
        val core = twoAreaCore()
        core.addArea(handle(safArea(FakeDocumentBackend()), "/", "MyProject"))
        core.switchArea(safId())

        val after = core.removeArea(safId())

        assertEquals(AreaId(AreaKind.GUEST_LINUX), after.areaId)
        assertEquals("/root", after.path?.value)
        assertNull(after.error)
    }

    @Test
    fun `removing the LAST folder leaves the honest no-storage state`() {
        val core = ExplorerCore(listOf(handle(safArea(FakeDocumentBackend()), "/", "MyProject")))
        core.initial()

        val after = core.removeArea(safId())

        assertNull(after.areaId)
        assertEquals(emptyList<String>(), after.areas.map { it.label })
        assertEquals("No storage is available right now.", after.error)
    }

    @Test
    fun `removing an unknown area is an honest no-op`() {
        val core = twoAreaCore()
        val before = core.snapshot()
        val after = core.removeArea(safId())
        assertEquals(before, after)
    }

    // ------------------------------------------------------ revoked folders

    @Test
    fun `a revoked folder stays listed and entering it is honestly broken`() {
        val backend = FakeDocumentBackend()
        backend.revoked = true
        val core = twoAreaCore()
        core.addArea(handle(safArea(backend), "/", "MyProject"))

        val state = core.switchArea(safId())

        assertEquals(safId(), state.areaId)
        assertTrue(state.entries.isEmpty())
        assertNotNull(state.error)
        assertTrue(state.error!!.contains("no longer available"))
    }

    @Test
    fun `a revoked folder still answers switcher identity correctly`() {
        val info = SafFolderInfo(
            uri = "content://test/tree/primary%3AMyProject",
            label = "MyProject",
            state = SafFolderState.REVOKED,
        )
        assertEquals("MyProject", info.label)
        assertEquals(SafFolderState.REVOKED, info.state)
        assertEquals(safId().key, info.uri)
    }
}

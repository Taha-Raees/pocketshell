package app.pocketshell.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The Home "Recent" folder record (owner iteration): the persisted triple
 * round-trips, and EVERYTHING unvalidated degrades to "no recent folder" —
 * the stored path is never trusted without revalidation (PathSafety).
 */
class RecentFolderStoreTest {

    private val root = PathSafety.validatePath("/root/projects")!!

    @Test
    fun `serialize then deserialize round-trips the record`() {
        val folder = RecentFolder(AreaKind.GUEST_LINUX, "default", root)
        val map = RecentFolderStore.serialize(folder)
        assertEquals(
            folder,
            RecentFolderStore.deserialize(
                map[RecentFolderStore.KEY_AREA_KIND],
                map[RecentFolderStore.KEY_AREA_KEY],
                map[RecentFolderStore.KEY_PATH],
            ),
        )
    }

    @Test
    fun `deserialize survives SAF-style area keys`() {
        val folder = RecentFolder(AreaKind.ANDROID_DOCUMENT_TREE, "primary:Duty", root)
        val map = RecentFolderStore.serialize(folder)
        assertEquals(
            "primary:Duty",
            RecentFolderStore.deserialize(
                map[RecentFolderStore.KEY_AREA_KIND],
                map[RecentFolderStore.KEY_AREA_KEY],
                map[RecentFolderStore.KEY_PATH],
            )?.areaKey,
        )
    }

    @Test
    fun `absent or unknown values degrade to null - never to a fake folder`() {
        assertNull(RecentFolderStore.deserialize(null, null, null))
        assertNull(RecentFolderStore.deserialize("GUEST_LINUX", "default", null))
        assertNull(RecentFolderStore.deserialize("NO_SUCH_KIND", "default", "/root"))
        assertNull(RecentFolderStore.deserialize("GUEST_LINUX", "default", "../../etc"))
        assertNull(RecentFolderStore.deserialize("GUEST_LINUX", "default", "not/a/canonical/path"))
    }

    @Test
    fun `names and storage labels are honest per area kind`() {
        assertEquals(
            "projects",
            RecentFolder(AreaKind.GUEST_LINUX, "default", root).name,
        )
        // an area root has no components — the label carries the meaning
        assertEquals(
            "Linux",
            RecentFolder(AreaKind.GUEST_LINUX, "default", PathSafety.validatePath("/")!!).name,
        )
        assertEquals(
            "Downloads",
            RecentFolder(AreaKind.ANDROID_SHELF, "default", PathSafety.validatePath("/")!!).name,
        )
        assertEquals(
            "PocketShell Linux",
            RecentFolder(AreaKind.GUEST_LINUX, "default", root).storageLabel,
        )
    }

    @Test
    fun `the terminal gate for the recent row matches the Files sheet gate`() {
        // the menu item only exists for guest-Linux folders — the SAME kind
        // openTerminalHereProblem allows
        assertEquals(
            null,
            openTerminalHereProblem(AreaKind.GUEST_LINUX),
        )
        val boundary = openTerminalHereProblem(AreaKind.ANDROID_SHELF)
        assertEquals(TerminalLaunchSupport.ANDROID_BOUNDARY_MESSAGE, boundary)
    }
}

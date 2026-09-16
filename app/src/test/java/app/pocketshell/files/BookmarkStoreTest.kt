package app.pocketshell.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit pins for [BookmarkStore] (owner iteration: user folder bookmarks).
 * Persistence must survive restart/process death and must NEVER resurrect
 * an unvalidated path — the same contract RecentFolderStore pins.
 */
class BookmarkStoreTest {

    private fun folder(kind: AreaKind, key: String, path: String) =
        RecentFolder(kind, key, PathSafety.validatePath(path)!!)

    // ---- round trip ---------------------------------------------------------

    @Test fun `serialize then deserialize preserves bookmarks in order`() {
        val bookmarks = listOf(
            folder(AreaKind.GUEST_LINUX, "default", "/root/projects/pocketshell"),
            folder(AreaKind.ANDROID_SHELF, "default", "/Download/apps"),
            folder(AreaKind.GUEST_LINUX, "default", "/root/notes"),
        )
        val restored = BookmarkStore.deserialize(BookmarkStore.serialize(bookmarks))
        assertEquals(bookmarks, restored)
        assertEquals(3, restored.size)
        assertEquals("pocketshell", restored[0].name)
    }

    @Test fun `empty list round trips`() {
        assertTrue(BookmarkStore.deserialize(BookmarkStore.serialize(emptyList())).isEmpty())
    }

    // ---- honest degradation ---------------------------------------------------

    @Test fun `null and blank raw deserialize to empty`() {
        assertTrue(BookmarkStore.deserialize(null).isEmpty())
        assertTrue(BookmarkStore.deserialize("").isEmpty())
        assertTrue(BookmarkStore.deserialize("   ").isEmpty())
    }

    @Test fun `corrupt json deserializes to empty not crash`() {
        assertTrue(BookmarkStore.deserialize("{not json at all").isEmpty())
        assertTrue(BookmarkStore.deserialize("[{\"kind\":").isEmpty())
    }

    @Test fun `unknown area kind drops only that entry`() {
        val good = folder(AreaKind.GUEST_LINUX, "default", "/root/keep")
        val raw = BookmarkStore.serialize(listOf(good))
            .replace("\"GUEST_LINUX\"", "\"WAS_NOT_A_KIND\"", )
        val restored = BookmarkStore.deserialize(raw)
        assertTrue(restored.isEmpty())
    }

    @Test fun `unvalidatable path drops only that entry`() {
        val good = folder(AreaKind.GUEST_LINUX, "default", "/root/keep")
        val raw = BookmarkStore.serialize(listOf(good, good))
            .replace("/root/keep", "/root/../escape")
        val restored = BookmarkStore.deserialize(raw)
        assertTrue(restored.all { it.path.value == "/root/keep" })
    }

    // ---- membership ------------------------------------------------------------

    @Test fun `isSameTarget matches on all three fields`() {
        val bookmark = folder(AreaKind.GUEST_LINUX, "default", "/root/projects")
        assertTrue(bookmark.isSameTarget(AreaKind.GUEST_LINUX, "default", bookmark.path))
        assertTrue(!bookmark.isSameTarget(AreaKind.ANDROID_SHELF, "default", bookmark.path))
        assertTrue(!bookmark.isSameTarget(AreaKind.GUEST_LINUX, "other", bookmark.path))
        val other = PathSafety.validatePath("/root/other")!!
        assertTrue(!bookmark.isSameTarget(AreaKind.GUEST_LINUX, "default", other))
    }
}

package app.pocketshell.widget.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The STORAGE application's PURE state machine: derived numbers, honest
 * state lines, preview/clear copy and the responsive layout — every
 * decision the card renders, tested with no device and no composition.
 */
class StorageUiTest {

    // --------------------------------------------------------- fixtures

    private fun size(bytes: Long, files: Int = 1, truncated: Boolean = false, exists: Boolean = true) =
        CategoryScan.SizeResult(bytes = bytes, files = files, truncated = truncated, exists = exists)

    private fun snapshot(
        runtimeBytes: Long? = 1_000L,
        runtimeTruncated: Boolean = false,
        apk: CategoryScan.SizeResult = size(0),
        staging: CategoryScan.SizeResult = size(0),
        guest: GuestCaches = GuestCaches.Sizes(emptyList()),
        free: Long? = 5_000L,
        hostTruncated: Boolean = false,
    ) = StorageSnapshot(
        runtime = RuntimeFacts(
            present = runtimeBytes != null,
            bytes = runtimeBytes,
            fileCount = if (runtimeBytes != null) 42 else null,
            truncated = runtimeTruncated,
        ),
        apkCache = apk,
        staging = staging,
        guest = guest,
        freeBytes = free,
        hostTruncated = hostTruncated,
        scannedAtMillis = 1_000L,
    )

    // ------------------------------------------------------- categories

    @Test
    fun `the four categories round-trip by id`() {
        StorageCategory.entries.forEach { category ->
            assertEquals(category, StorageCategory.byId(category.id))
        }
        assertNull(StorageCategory.byId("nope"))
        assertEquals(4, StorageCategory.entries.size)
    }

    @Test
    fun `only the two app-owned caches are clearable`() {
        assertFalse(StorageCategory.RUNTIME.clearable)
        assertTrue(StorageCategory.PACKAGE_CACHE.clearable)
        assertTrue(StorageCategory.SHARE_STAGING.clearable)
        assertFalse(StorageCategory.GUEST_CACHES.clearable)
    }

    // ---------------------------------------------------------- layout

    @Test
    fun `a phone card is COMPACT - one-line rows only`() {
        assertEquals(StorageLayout.COMPACT, StorageLayout.from(320f, 208f))
        assertEquals(StorageLayout.COMPACT, StorageLayout.from(390f, 172f))
        val layout = StorageLayout.COMPACT
        assertFalse(layout.showsStatusHeader)
        assertFalse(layout.showsFooter)
        assertFalse(layout.scrollsRows)
    }

    @Test
    fun `a tablet card is ROOMY - status header, footer, scroll`() {
        assertEquals(StorageLayout.ROOMY, StorageLayout.from(680f, 208f))
        assertEquals(StorageLayout.ROOMY, StorageLayout.from(420f, 251f))
        val layout = StorageLayout.ROOMY
        assertTrue(layout.showsStatusHeader)
        assertTrue(layout.showsFooter)
        assertTrue(layout.scrollsRows)
    }

    @Test
    fun `the roomy threshold requires BOTH width and height`() {
        assertEquals(StorageLayout.COMPACT, StorageLayout.from(680f, 199.9f))
        assertEquals(StorageLayout.COMPACT, StorageLayout.from(419.9f, 200f))
        assertEquals(StorageLayout.ROOMY, StorageLayout.from(420f, 200f))
    }

    // ---------------------------------------------------- derived numbers

    @Test
    fun `reclaimable is exactly the two app-owned caches`() {
        val s = snapshot(apk = size(300), staging = size(56))
        assertEquals(356L, reclaimableBytes(s))
        assertEquals(356L + 1_000L, totalHostBytes(s))
    }

    @Test
    fun `an absent runtime contributes nothing to the host total`() {
        val s = snapshot(runtimeBytes = null, apk = size(10), staging = size(0))
        assertEquals(10L, totalHostBytes(s))
    }

    @Test
    fun `guest totals sum only the entries du could size`() {
        val mixed = GuestCaches.Sizes(
            listOf(GuestCacheEntry("npm", 10L), GuestCacheEntry("cache", null)),
        )
        assertEquals(10L * 1024, guestTotalBytes(mixed))
        // Every entry unknown → the honest answer is "unknown", not 0.
        val allUnknown = GuestCaches.Sizes(listOf(GuestCacheEntry("cache", null)))
        assertNull(guestTotalBytes(allUnknown))
        assertNull(guestTotalBytes(GuestCaches.NotProbed))
        assertNull(guestTotalBytes(GuestCaches.Unavailable))
        assertNull(guestTotalBytes(GuestCaches.Failed("du died")))
    }

    // ----------------------------------------------------- state lines

    @Test
    fun `the overview state line is honest about every state`() {
        assertEquals("Measuring…", overviewStateLine(StorageUi.Measuring))
        assertEquals(
            "Linux not ready",
            overviewStateLine(
                StorageUi.Ready(snapshot(runtimeBytes = null, guest = GuestCaches.Unavailable)),
            ),
        )
        val reclaimable = StorageUi.Ready(snapshot(apk = size(4096L)))
        assertTrue(overviewStateLine(reclaimable).endsWith("reclaimable"))
        assertTrue(overviewStateLine(reclaimable).startsWith("4.0 KB"))
        assertEquals(
            "Nothing reclaimable",
            overviewStateLine(StorageUi.Ready(snapshot())),
        )
    }

    @Test
    fun `the state line is accented only on a real actionable number`() {
        assertFalse(overviewStateAccented(StorageUi.Measuring))
        assertFalse(overviewStateAccented(StorageUi.Ready(snapshot())))
        assertTrue(overviewStateAccented(StorageUi.Ready(snapshot(apk = size(1)))))
    }

    @Test
    fun `category values never invent numbers`() {
        val s = snapshot(
            runtimeBytes = 2048L,
            apk = size(1024L),
            staging = size(0, files = 0),
            guest = GuestCaches.NotProbed,
        )
        assertEquals("2.0 KB", categoryValue(StorageCategory.RUNTIME, s))
        assertEquals("1.0 KB", categoryValue(StorageCategory.PACKAGE_CACHE, s))
        assertEquals("0 B", categoryValue(StorageCategory.SHARE_STAGING, s))
        assertEquals("—", categoryValue(StorageCategory.GUEST_CACHES, s))
    }

    @Test
    fun `an absent runtime says so instead of zero`() {
        val s = snapshot(runtimeBytes = null)
        assertEquals("not installed", categoryValue(StorageCategory.RUNTIME, s))
        assertFalse(s.runtime.present)
        assertNull(s.runtime.bytes)
        assertNull(s.runtime.fileCount)
    }

    @Test
    fun `the guest section states its own honest state`() {
        assertEquals("Not probed", guestStateLine(GuestCaches.NotProbed))
        assertEquals("Linux not ready", guestStateLine(GuestCaches.Unavailable))
        assertEquals("Probe failed", guestStateLine(GuestCaches.Failed("boom")))
        assertEquals(
            "No guest caches found",
            guestStateLine(GuestCaches.Sizes(emptyList())),
        )
        assertEquals(
            "Measured in the guest",
            guestStateLine(GuestCaches.Sizes(listOf(GuestCacheEntry("npm", 1L)))),
        )
    }

    // ------------------------------------------------- preview + clear

    @Test
    fun `the preview headline says what will be removed`() {
        assertEquals("Empty — nothing to clear.", previewHeadline(size(0, files = 0)))
        assertEquals(
            "Empty — nothing to clear.",
            previewHeadline(size(0, files = 0, exists = false)),
        )
        val one = previewHeadline(size(512, files = 1))
        assertTrue(one.contains("1 file"))
        assertTrue(one.contains("512 B"))
        val many = previewHeadline(size(4096, files = 7))
        assertTrue(many.contains("7 files"))
        assertTrue(many.contains("4.0 KB"))
    }

    @Test
    fun `the clear flow reports every outcome honestly`() {
        assertEquals("", clearStateLine(ClearState.Idle))
        assertEquals("Clearing…", clearStateLine(ClearState.Running))
        val done = clearStateLine(ClearState.Done(filesDeleted = 3, bytesFreed = 3072L))
        assertTrue(done.contains("Cleared 3 files"))
        assertTrue(done.contains("freed 3.0 KB"))
        assertTrue(clearStateLine(ClearState.Done(1, 1L)).contains("Cleared 1 file "))
        assertTrue(
            clearStateLine(ClearState.Stopped).contains("Stopped early"),
        )
        assertTrue(
            clearStateLine(ClearState.Failed("2 item(s) could not be removed"))
                .startsWith("Could not clear:"),
        )
    }

    // ------------------------------------------------------- truncation

    @Test
    fun `truncated walks surface as floors - never silently`() {
        val s = snapshot(runtimeTruncated = true, hostTruncated = true)
        assertTrue(s.runtime.truncated)
        assertTrue(s.hostTruncated)
        // The note text exists and says floors (rendered by the card when
        // hostTruncated is true).
        assertTrue(TRUNCATION_NOTE.contains("floors"))
    }
}

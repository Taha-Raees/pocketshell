package app.pocketshell.launchers

import app.pocketshell.apps.CommandAppCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

/**
 * M7.1 P2 — the bundled curated icon map (PART D/E).
 *
 * Pins the contract that keeps the bundled-asset layer honest:
 *   - the map covers EXACTLY the curated set (the four built-in companion
 *     seeds + every CLI registry id) — no stale entries (a removed launcher
 *     must never keep a packaged asset mapping) and no gaps;
 *   - every mapped path is a safe plain relative name;
 *   - every mapped asset EXISTS in the source tree with bytes (the
 *     GuestGlibcRuntimeTest source-pin pattern at icon weight);
 *   - the BUILT APK actually packages every asset (the vc40/vc41 lesson:
 *     source-tree presence proves nothing about the merge — when an APK is
 *     present, its entries are checked).
 */
class LauncherBundledIconsTest {

    private val curatedIds: Set<String> =
        BuiltInCompanions.SEED_IDS + CommandAppCatalog.registry.map { it.id }.toSet()

    @Test
    fun `the bundled map covers exactly the curated launcher set`() {
        assertEquals(curatedIds, LauncherBundledIcons.coveredIds)
    }

    @Test
    fun `every seed and registry id has an asset path`() {
        for (id in curatedIds) {
            val path = LauncherBundledIcons.assetPathFor(id)
            assertNotNull("curated launcher $id must have a bundled icon path", path)
            assertEquals("launcher_icons/$id.webp", path)
        }
    }

    @Test
    fun `unknown and custom ids never hit the bundled map`() {
        // Custom launchers (user config) and junk ids have no bundled icon —
        // they fall through to the imported-copy/badge path untouched.
        assertNull(LauncherBundledIcons.assetPathFor("tool-2f0a6aa1-1111-4ccc-9dd2-000000000000"))
        assertNull(LauncherBundledIcons.assetPathFor(""))
        assertNull(LauncherBundledIcons.assetPathFor("builtin-chatgpt/../../secret"))
        assertNull(LauncherBundledIcons.assetPathFor("../builtin-zai"))
        assertNull(LauncherBundledIcons.assetPathFor("gemini"))
    }

    @Test
    fun `mapped paths are safe plain relative names`() {
        for (id in LauncherBundledIcons.coveredIds) {
            val path = LauncherBundledIcons.assetPathFor(id)!!
            assertTrue(path.startsWith("launcher_icons/") && path.endsWith(".webp"))
            assertEquals(path, "launcher_icons/${File(path).name}") // no separators inside
            assertTrue(!path.contains(".."))
        }
    }

    @Test
    fun `every mapped asset exists in the source tree with bytes`() {
        val dir = listOf(
            File("app/src/main/assets/launcher_icons"),
            File("src/main/assets/launcher_icons"),
        ).firstOrNull { it.isDirectory }
        org.junit.Assume.assumeTrue(
            "asset dir not found on this runner (packaging test covers the merged set)",
            dir != null,
        )
        val packaged = dir!!.listFiles { f -> f.isFile && f.extension == "webp" }
            ?.map { it.nameWithoutExtension }?.toSet() ?: emptySet()
        assertEquals(
            "the asset directory must carry exactly the mapped icons (no stale files, no gaps)",
            LauncherBundledIcons.coveredIds,
            packaged,
        )
        for (id in LauncherBundledIcons.coveredIds) {
            val file = File(dir, "$id.webp")
            assertTrue("bundled asset missing/empty for $id", file.isFile && file.length() > 0)
        }
    }

    @Test
    fun `built APK packages every bundled icon`() {
        val apk = listOf(
            File("build/outputs/apk/debug/app-debug.apk"),
            File("app/build/outputs/apk/debug/app-debug.apk"),
        ).firstOrNull { it.isFile }
        org.junit.Assume.assumeTrue(
            "built APK not present on this runner (run assembleDebug first)",
            apk != null,
        )
        ZipFile(apk).use { zip ->
            for (id in LauncherBundledIcons.coveredIds) {
                val entry = zip.getEntry("assets/launcher_icons/$id.webp")
                assertTrue(
                    "APK is missing the bundled launcher icon for $id " +
                        "(the vc40/vc41 asset-merge lesson)",
                    entry != null && entry.size > 0,
                )
            }
        }
    }
}

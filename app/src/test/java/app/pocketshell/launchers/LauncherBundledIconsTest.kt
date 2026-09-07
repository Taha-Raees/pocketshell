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
 * M7.1 P2/P2.2 — the bundled curated icon table (PART D/E).
 *
 * Pins the contract that keeps the bundled-asset layer honest:
 *   - the table covers EXACTLY the curated set (the four built-in companion
 *     seeds + every CLI registry id) — no stale entries (a removed launcher
 *     must never keep a packaged asset mapping) and no gaps;
 *   - EVERY curated id resolves in BOTH theme directions (P2.2: the dark
 *     variant and the `-light` variant), and only curated ids do;
 *   - every mapped path is a safe plain relative name;
 *   - every mapped asset EXISTS in the source tree with bytes, and the
 *     asset directory carries EXACTLY the two-variant set (no stale files,
 *     no gaps) — the GuestGlibcRuntimeTest source-pin pattern at icon weight;
 *   - the BUILT APK actually packages every variant (the vc40/vc41 lesson:
 *     source-tree presence proves nothing about the merge — when an APK is
 *     present, its entries are checked).
 */
class LauncherBundledIconsTest {

    private val curatedIds: Set<String> =
        BuiltInCompanions.SEED_IDS + CommandAppCatalog.registry.map { it.id }.toSet()

    @Test
    fun `the bundled table covers exactly the curated launcher set`() {
        assertEquals(curatedIds, LauncherBundledIcons.coveredIds)
    }

    @Test
    fun `every seed and registry id has an asset path in BOTH theme variants`() {
        for (id in curatedIds) {
            assertEquals(
                "launcher_icons/$id.webp",
                LauncherBundledIcons.assetPathFor(id, light = false),
            )
            assertEquals(
                "launcher_icons/$id-light.webp",
                LauncherBundledIcons.assetPathFor(id, light = true),
            )
        }
    }

    @Test
    fun `unknown and custom ids never hit the bundled table in either theme`() {
        // Custom launchers (user config) and junk ids have no bundled icon —
        // they fall through to the imported-copy/badge path untouched.
        for (light in listOf(false, true)) {
            assertNull(LauncherBundledIcons.assetPathFor("tool-2f0a6aa1-1111-4ccc-9dd2-000000000000", light))
            assertNull(LauncherBundledIcons.assetPathFor("", light))
            assertNull(LauncherBundledIcons.assetPathFor("builtin-chatgpt/../../secret", light))
            assertNull(LauncherBundledIcons.assetPathFor("../builtin-zai", light))
            assertNull(LauncherBundledIcons.assetPathFor("builtin-zai-light", light))
            assertNull(LauncherBundledIcons.assetPathFor("gemini", light))
            assertNull(LauncherBundledIcons.assetPathFor("aider", light))
        }
    }

    @Test
    fun `mapped paths are safe plain relative names`() {
        for (id in LauncherBundledIcons.coveredIds) {
            for (light in listOf(false, true)) {
                val path = LauncherBundledIcons.assetPathFor(id, light)!!
                assertTrue(path.startsWith("launcher_icons/") && path.endsWith(".webp"))
                assertEquals(path, "launcher_icons/${File(path).name}") // no separators inside
                assertTrue(!path.contains(".."))
            }
        }
    }

    @Test
    fun `the asset directory carries exactly the two-variant set with bytes`() {
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
        val expected = LauncherBundledIcons.coveredIds.flatMap { listOf(it, "$it-light") }.toSet()
        assertEquals(
            "the asset directory must carry exactly the mapped icon variants " +
                "(no stale files, no gaps)",
            expected,
            packaged,
        )
        for (id in LauncherBundledIcons.coveredIds) {
            for (variant in listOf("$id.webp", "$id-light.webp")) {
                val file = File(dir, variant)
                assertTrue("bundled asset missing/empty for $variant", file.isFile && file.length() > 0)
            }
        }
    }

    @Test
    fun `built APK packages every bundled icon variant`() {
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
                for (variant in listOf("$id.webp", "$id-light.webp")) {
                    val entry = zip.getEntry("assets/launcher_icons/$variant")
                    assertTrue(
                        "APK is missing the bundled launcher icon variant $variant " +
                            "(the vc40/vc41 asset-merge lesson)",
                        entry != null && entry.size > 0,
                    )
                }
            }
        }
    }
}

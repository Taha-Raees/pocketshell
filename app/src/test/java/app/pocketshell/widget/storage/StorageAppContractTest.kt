package app.pocketshell.widget.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * The STORAGE application's structural contract (source-reading pins in
 * the established HomeWidgetContractTest / GitAppContractTest style — the
 * JVM suite has no device runner, so boundaries are pinned by reading what
 * ships). Scope: the widget/storage sources only; shared files belong to
 * the shared contract.
 *
 * Pinned here:
 *   1. THEME INDEPENDENCE: only the shared PocketShell tokens — no
 *      hardcoded colors, no theme-name literals.
 *   2. THE BUDGETED COLLECTOR: no unbounded walkTopDown anywhere, no
 *      deleteRecursively; the runtime facts are REUSED from
 *      RuntimeStorageFacts, never re-walked; every walk is NOFOLLOW and
 *      budgeted; heavy work lives on Dispatchers.IO.
 *   3. DELETION SAFETY: the clear primitive removes regular files only,
 *      never follows symlinks, never deletes the cache root; the
 *      application offers Clear for the two APP-OWNED categories only;
 *      the guest probe script is strictly read-only (v1 never deletes
 *      anything inside the guest).
 *   4. IN-CARD NAVIGATION: the application owns its page state and its
 *      own back handler; actions ride the one WidgetNav seam.
 *   5. LAYER PURITY: the state/probe layers have no android dependencies.
 *   6. M8.4.2 PROCESS-SCOPED STATE: the measurement, page, clear-flow and
 *      probe state live in the HomeAppStateStore holder (re-entry renders
 *      the cache — no refresh-on-navigation); refresh is the explicit,
 *      named header action.
 *   7. M8.4.3 ANALYZER SURFACE: every category page renders the pure
 *      WHY/CONSEQUENCE copy block; guest rows ride the pure largest-first
 *      sorter; overview rows carry the snapshot's secondary facts; the
 *      clear arc says "re-measuring" while the AFTER size is pending.
 */
class StorageAppContractTest {

    // ------------------------------------------------------------ helpers

    private fun storageSource(relative: String): String {
        val file = listOf(
            "app/src/main/java/app/pocketshell/widget/storage/$relative",
            "../app/src/main/java/app/pocketshell/widget/storage/$relative",
        ).map { File(it) }.firstOrNull { it.isFile }
        assumeTrue("source not found on this runner: $relative", file != null)
        return file!!.readText()
    }

    /** Comments + string CONTENTS stripped — structural tokens only. */
    private fun stripCommentsAndStrings(source: String): String {
        val out = StringBuilder(source.length)
        var i = 0
        while (i < source.length) {
            val c = source[i]
            when {
                c == '/' && i + 1 < source.length && source[i + 1] == '*' -> {
                    i = source.indexOf("*/", i + 2).let { if (it < 0) source.length else it + 2 }
                }
                c == '/' && i + 1 < source.length && source[i + 1] == '/' -> {
                    while (i < source.length && source[i] != '\n') i++
                }
                c == '"' -> {
                    i++
                    while (i < source.length && source[i] != '"') {
                        if (source[i] == '\\') i++
                        i++
                    }
                    i++
                    out.append("\"\"")
                }
                else -> {
                    out.append(c)
                    i++
                }
            }
        }
        return out.toString()
    }

    private fun stringLiterals(source: String): List<String> {
        val literals = mutableListOf<String>()
        var i = 0
        while (i < source.length) {
            if (source[i] == '"') {
                val start = i + 1
                var j = start
                while (j < source.length && source[j] != '"') {
                    if (source[j] == '\\') j++
                    j++
                }
                literals += source.substring(start, j.coerceAtMost(source.length))
                i = j + 1
            } else {
                i++
            }
        }
        return literals
    }

    private val allSources =
        listOf("StorageApp.kt", "StorageUi.kt", "CategoryScan.kt", "GuestCacheProbe.kt")

    // -------------------------------------- 1. theme independence

    @Test
    fun `the Storage application follows the shared theme - no private theme`() {
        val app = storageSource("StorageApp.kt")
        val code = stripCommentsAndStrings(app)
        assertTrue("must read the shared tokens", code.contains("HomeTokens."))
        assertTrue("must read the theme typeface", code.contains("TerminalTheme."))
        assertFalse(
            "no canvas-tone text pair (the card is a chrome-surface citizen)",
            code.contains("onHero"),
        )
        assertFalse(
            "no hardcoded colors — the selected theme is the only palette",
            Regex("""Color\(0x""").containsMatchIn(code),
        )
        assertFalse(
            "no theme-name literals (Aurora is ONE theme, never a dependency)",
            stringLiterals(app).any { it.contains("Aurora", ignoreCase = true) },
        )
        allSources.filter { it != "StorageApp.kt" }.forEach { name ->
            assertFalse(
                "no hardcoded colors in $name either",
                Regex("""Color\(0x""").containsMatchIn(stripCommentsAndStrings(storageSource(name))),
            )
        }
    }

    // -------------------------------------- 2. the budgeted collector

    @Test
    fun `no unbounded walks - the runtime facts are reused, every walk budgeted and NOFOLLOW`() {
        allSources.forEach { name ->
            val code = stripCommentsAndStrings(storageSource(name))
            assertFalse(
                "$name must never walkTopDown (the M8.2 Diagnostics ANR pattern)",
                code.contains("walkTopDown"),
            )
            assertFalse(
                "$name must never deleteRecursively (it follows directory symlinks)",
                code.contains("deleteRecursively"),
            )
        }
        val app = stripCommentsAndStrings(storageSource("StorageApp.kt"))
        assertTrue(
            "runtime facts must be REUSED from RuntimeStorageFacts (never re-walked)",
            app.contains("RuntimeStorageFacts.collect"),
        )
        assertTrue(
            "no main-thread I/O — the measurement runs on Dispatchers.IO",
            app.contains("Dispatchers.IO"),
        )
        val scan = stripCommentsAndStrings(storageSource("CategoryScan.kt"))
        assertTrue(
            "sizing uses a walkFileTree pass",
            scan.contains("walkFileTree"),
        )
        assertTrue(
            "every walk is NOFOLLOW (no FileVisitOption.FOLLOW_LINKS set)",
            scan.contains("noneOf(FileVisitOption"),
        )
        assertTrue(
            "the file-count budget bounds the walk honestly",
            scan.contains("DEFAULT_MAX_FILES") && scan.contains("maxFiles"),
        )
        assertTrue(
            "the cancel poll bounds an in-flight scan's lifetime",
            scan.contains("isCancelled"),
        )
    }

    // -------------------------------------- 3. deletion safety

    @Test
    fun `the clear primitive removes regular files only and never leaves the cache dir`() {
        val scan = stripCommentsAndStrings(storageSource("CategoryScan.kt"))
        assertTrue(
            "files are deleted only after the isRegularFile guard",
            scan.contains("if (!attrs.isRegularFile)"),
        )
        assertTrue(
            "the cache root itself always survives",
            scan.contains("dirPath == root"),
        )
    }

    @Test
    fun `Clear is offered for the app-owned categories only`() {
        val app = stripCommentsAndStrings(storageSource("StorageApp.kt"))
        assertTrue(
            "the apk package cache is the app-owned, safe-to-clear category",
            app.contains("StorageCategory.PACKAGE_CACHE ->"),
        )
        assertTrue(
            "the share staging dir is the second app-owned category",
            app.contains("StorageCategory.SHARE_STAGING ->"),
        )
        assertTrue(
            "any other category refuses to resolve a clear target",
            app.contains("else -> return"),
        )
        assertFalse(
            "no auto-delete timers — clearing happens only on an explicit tap",
            app.contains("autoClear") || app.contains("scheduleClear"),
        )
    }

    /**
     * The probe script is a Kotlin RAW string (triple-quoted), which the
     * single-quote literal scanner cannot extract — locate it by its
     * markers instead.
     */
    private fun probeScriptLiteral(): String? {
        val source = storageSource("GuestCacheProbe.kt")
        val startMarker = "PROBE_SCRIPT = \"\"\""
        val start = source.indexOf(startMarker)
        if (start < 0) return null
        val bodyStart = start + startMarker.length
        val end = source.indexOf("\"\"\"", bodyStart)
        if (end < 0) return null
        return source.substring(bodyStart, end)
    }

    @Test
    fun `the guest probe script is strictly read-only - v1 never deletes inside the guest`() {
        val script = probeScriptLiteral()
        assertNotNull("the probe script must ship in GuestCacheProbe.kt", script)
        assertTrue("measurement is du -sk", script!!.contains("du -sk"))
        assertTrue("absence is honest silence, not a zero", script.contains("[ -d "))
        assertTrue("the protocol terminates with DONE", script.contains("@@DONE"))
        val banned = listOf(
            " rm ", "rm -", "-delete", " mv ", " cp ", "chmod", "chown", "shred",
            "truncate", "apk ", "adduser", "deluser", "wget", "curl", "apkadd",
        )
        val found = banned.filter { script.contains(it) }
        assertTrue("the probe never mutates the guest; found: $found", found.isEmpty())
    }

    // -------------------------------------- 4. in-card navigation

    @Test
    fun `the application owns its page state and its own back`() {
        val code = stripCommentsAndStrings(storageSource("StorageApp.kt"))
        assertTrue(
            "page selection must live in the process-scoped holder (survives navigation and carousel disposal)",
            code.contains("var pageId by mutableStateOf"),
        )
        assertTrue(
            "back inside the card returns to the overview before leaving Home",
            code.contains("BackHandler(enabled = page != null)"),
        )
        // Actions ride ONLY the one navigation seam.
        assertTrue(code.contains("nav.openDiagnostics()"))
        assertTrue(code.contains("nav.openLinuxShell()"))
        val banned = listOf("startActivity", "Intent(")
        val found = banned.filter { code.contains(it) }
        assertTrue("no second navigation mechanism; found: $found", found.isEmpty())
    }

    // -------------------------------------- 5. layer purity + identity

    @Test
    fun `the state and probe layers have no android dependencies`() {
        listOf("StorageUi.kt", "CategoryScan.kt", "GuestCacheProbe.kt").forEach { name ->
            val code = stripCommentsAndStrings(storageSource(name))
            val banned = listOf("android.", "androidx", "Context", "Composable")
            val found = banned.filter { code.contains(it) }
            assertTrue("$name must stay JVM-pure; found: $found", found.isEmpty())
        }
        assertEquals(
            "the registry id must match the application's spec id",
            StorageApp.STORAGE_ID,
            StorageApp.spec.id,
        )
    }

    // -------------------------------------- 6. single-source paths

    @Test
    fun `the app-owned cache dirs come from their canonical seams - no path drift`() {
        val app = stripCommentsAndStrings(storageSource("StorageApp.kt"))
        assertTrue(
            "the package cache path is PackageGateway's ONE definition",
            app.contains("PackageGateway.apkCacheDir"),
        )
        assertTrue(
            "the share staging path is FileShareOps' ONE definition",
            app.contains("FileShareOps.STAGING_DIR_NAME"),
        )
    }

    // ------------------------------ 7. M8.4.2 process-scoped state + refresh

    @Test
    fun `the measurement state lives in the process-scoped holder - re-entry renders the cache`() {
        val code = stripCommentsAndStrings(storageSource("StorageApp.kt"))
        assertTrue(
            "state must be wired through the HomeAppStateStore (survives Home disposal)",
            code.contains("stateStore.forApp"),
        )
        assertTrue(
            "the holder owns the last snapshot (var ui by mutableStateOf)",
            code.contains("var ui by mutableStateOf"),
        )
        assertFalse(
            "no per-composition ui remember (that was refresh-on-navigation)",
            Regex("""remember \{ mutableStateOf<StorageUi>""").containsMatchIn(code),
        )
    }

    @Test
    fun `refresh is the compact named header action`() {
        val raw = storageSource("StorageApp.kt")
        assertTrue(
            "the header refresh must exist and be named for accessibility",
            stringLiterals(raw).any { it == "Refresh storage" },
        )
        val code = stripCommentsAndStrings(raw)
        assertTrue(
            "the refresh icon is the outlined Refresh glyph",
            code.contains("Icons.Outlined.Refresh"),
        )
    }

    // ------------------------------ 8. M8.4.3 analyzer surface

    @Test
    fun `every category page carries the why-consequence copy - guest rows sorted - arc honest`() {
        val app = stripCommentsAndStrings(storageSource("StorageApp.kt"))
        val uiRaw = storageSource("StorageUi.kt")
        val ui = stripCommentsAndStrings(uiRaw)
        assertTrue(
            "the pure copy model must exist (one place per category)",
            ui.contains("fun categoryCopy(") && ui.contains("data class CategoryCopy"),
        )
        assertTrue(
            "the runtime, clearable and guest pages must each render the copy block",
            Regex("CategoryCopyBlock\\(").findAll(app).count() >= 3,
        )
        assertTrue(
            "guest rows must be ordered by the pure sorter (largest first)",
            app.contains("sortedGuestCaches("),
        )
        assertTrue(
            "overview rows must carry the snapshot's secondary facts (no new scanning)",
            app.contains("categoryDetail("),
        )
        assertTrue(
            "the clear arc must say re-measuring while the AFTER size is pending",
            uiRaw.contains("re-measuring"),
        )
    }
}

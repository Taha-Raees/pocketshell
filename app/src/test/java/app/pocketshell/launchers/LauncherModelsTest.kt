package app.pocketshell.launchers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M7.1 Phase 1 — the launcher model pins. The headline rule: a launcher is
 * not an app — the model stores name/command/URL/visibility metadata and
 * NEVER an install state. Hiding is hide-only; badges are deterministic;
 * the built-in companion seed merge is additive by fixed id.
 */
class LauncherModelsTest {

    // ------------------------------------------------------------ badges

    @Test
    fun `badges - the brief's companion example reproduces exactly`() {
        val badges = LauncherBadges.assign(listOf("ChatGPT", "Claude", "Z.ai", "GitHub"))
        assertEquals(listOf("C", "Cl", "Z", "G"), badges)
    }

    @Test
    fun `badges - the brief's tool example reproduces exactly`() {
        val badges = LauncherBadges.assign(
            listOf("Kilo Code", "Cline", "Hermes Agent", "Claude Code", "Codex"),
        )
        assertEquals(listOf("K", "C", "H", "Cl", "Co"), badges)
    }

    @Test
    fun `badges - a lone launcher keeps one letter`() {
        assertEquals(listOf("N"), LauncherBadges.assign(listOf("Nano")))
        assertEquals(listOf("K"), LauncherBadges.assign(listOf("Kilo Code")))
    }

    @Test
    fun `badges - collisions extend case-insensitively and stay deterministic`() {
        // lowercase duplicate of a visible name must resolve, not crash
        val badges = LauncherBadges.assign(listOf("Claude", "claude"))
        assertEquals("C", badges[0])
        assertEquals("Cl", badges[1])
        // re-running yields the same answer (pure, no hidden state)
        assertEquals(badges, LauncherBadges.assign(listOf("Claude", "claude")))
    }

    @Test
    fun `badges - order decides who keeps the short prefix`() {
        // Claude before Codex: Claude takes C, Codex extends to Co
        assertEquals(listOf("C", "Co"), LauncherBadges.assign(listOf("Claude", "Codex")))
        // reversed input: Codex takes C, Claude extends to Cl
        assertEquals(listOf("C", "Cl"), LauncherBadges.assign(listOf("Codex", "Claude")))
    }

    @Test
    fun `badges - names without letters or digits degrade to question mark`() {
        assertEquals(listOf("?"), LauncherBadges.assign(listOf("!!!")))
        assertEquals(listOf("?", "A"), LauncherBadges.assign(listOf("---", "A")))
    }

    @Test
    fun `badges - extension never runs past the available letters`() {
        // "aa" vs "ab": the first keeps ONE letter, the second extends
        assertEquals(listOf("A", "Ab"), LauncherBadges.assign(listOf("aa", "ab")))
        // a full-name extension stops at the last available letter
        val badges = LauncherBadges.assign(listOf("aa", "aa"))
        assertEquals(listOf("A", "Aa"), badges)
    }

    // --------------------------------------------------- custom tool validation

    @Test
    fun `custom tool names trim, reject blank and cap length`() {
        assertEquals("My Tool", CustomToolValidation.validateName("  My Tool  "))
        assertNull(CustomToolValidation.validateName("   "))
        assertNull(CustomToolValidation.validateName("x".repeat(CustomToolValidation.MAX_NAME_LENGTH + 1)))
        assertNotNull(CustomToolValidation.validateName("x".repeat(CustomToolValidation.MAX_NAME_LENGTH)))
    }

    @Test
    fun `custom tool commands stay verbatim after hygiene trim`() {
        // the command is USER CONFIGURATION — content is not reinterpreted
        assertEquals(
            "npm install -g @kilocode/cli",
            CustomToolValidation.validateCommand("  npm install -g @kilocode/cli  "),
        )
        assertEquals(
            "my-tool --serve --port 8080",
            CustomToolValidation.validateCommand("my-tool --serve --port 8080"),
        )
    }

    @Test
    fun `custom tool commands reject blank, newlines, carriage returns and NULs`() {
        assertNull(CustomToolValidation.validateCommand("   "))
        assertNull(CustomToolValidation.validateCommand("echo one\necho two"))
        assertNull(CustomToolValidation.validateCommand("echo one\recho two"))
        assertNull(CustomToolValidation.validateCommand("echo one\u0000two"))
        assertNull(CustomToolValidation.validateCommand("x".repeat(CustomToolValidation.MAX_COMMAND_LENGTH + 1)))
    }

    @Test
    fun `custom tool head is the first whitespace token`() {
        val tool = CustomTool(id = "tool-1", name = "Installer", command = "npm install -g something")
        assertEquals("npm", tool.commandHead())
        assertEquals("my-tool", CustomTool("tool-2", "T", "my-tool").commandHead())
    }

    // -------------------------------------------------------- visibility math

    @Test
    fun `hiding removes launchers from Home only - never from the model`() {
        val kilo = app.pocketshell.apps.CommandAppCatalog.byId("kilo")!!
        val tool = CustomTool(id = "tool-9", name = "My Tool", command = "my-tool")

        // both visible launchers hidden → an empty Home grid, but the
        // registry entry and the persisted tool are untouched objects
        val visible = visibleTools(
            registry = listOf(kilo),
            customTools = listOf(tool),
            hiddenIds = setOf("kilo", "tool-9"),
        )
        assertTrue(visible.isEmpty())
        assertNotNull(kilo)
        assertEquals("my-tool", tool.command)

        // unhidden: registry entries first (registry order), customs after
        val all = visibleTools(
            app.pocketshell.apps.CommandAppCatalog.registry, listOf(tool), emptySet(),
        )
        assertEquals(app.pocketshell.apps.CommandAppCatalog.registry.first().id, all.first().id)
        assertEquals(app.pocketshell.apps.CommandAppCatalog.registry.last().id, all[all.size - 2].id)
        assertEquals("tool-9", all.last().id)
        assertTrue(all.first() is ToolLauncher.Builtin)
        assertTrue(all.last() is ToolLauncher.Custom)
        assertFalse(visibleTools(listOf(kilo), emptyList(), setOf("kilo")).any { it.id == "kilo" })
    }

    @Test
    fun `hiding companions removes them from Home only`() {
        val defs = listOf(
            app.pocketshell.companion.CompanionDef("builtin-chatgpt", "ChatGPT", "https://chatgpt.com"),
            app.pocketshell.companion.CompanionDef("uuid-1", "My AI", "https://my.ai"),
        )
        val visible = visibleCompanions(defs, hiddenIds = setOf("builtin-chatgpt"))
        assertEquals(listOf("uuid-1"), visible.map { it.id })
        // hide-only: the input list is untouched
        assertEquals(2, defs.size)
    }

    // ------------------------------------------------------------- seed merge

    @Test
    fun `fresh install seeds exactly the four built-in companions in order`() {
        val merged = mergeBuiltInCompanionSeeds(emptyList())
        assertEquals(
            listOf("builtin-chatgpt", "builtin-claude", "builtin-zai", "builtin-github"),
            merged.map { it.id },
        )
        assertEquals(listOf("ChatGPT", "Claude", "Z.ai", "GitHub"), merged.map { it.name })
        // every seed URL normalized through the EXISTING companion validation
        assertTrue(merged.all { it.url.startsWith("https://") })
        assertEquals("https://z.ai", merged[2].url)
    }

    @Test
    fun `seed merge never duplicates and never reorders existing definitions`() {
        val existing = listOf(
            app.pocketshell.companion.CompanionDef("user-1", "My AI", "https://my.ai"),
            app.pocketshell.companion.CompanionDef("builtin-claude", "Claude (edited)", "https://claude.ai/custom"),
        )
        val merged = mergeBuiltInCompanionSeeds(existing)
        // the user-edited seed survives VERBATIM (id match → skip)
        val claude = merged.first { it.id == "builtin-claude" }
        assertEquals("Claude (edited)", claude.name)
        assertEquals("https://claude.ai/custom", claude.url)
        // the missing seeds append after the existing definitions
        assertEquals(
            listOf("user-1", "builtin-claude", "builtin-chatgpt", "builtin-zai", "builtin-github"),
            merged.map { it.id },
        )
        // idempotent: merging again changes nothing
        assertEquals(merged, mergeBuiltInCompanionSeeds(merged))
    }

    @Test
    fun `restore after deletion re-adds the seed with its fixed id`() {
        val afterDelete = mergeBuiltInCompanionSeeds(emptyList())
            .filterNot { it.id == "builtin-github" }
        val restored = mergeBuiltInCompanionSeeds(afterDelete)
        assertEquals(4, restored.size)
        assertNotNull(restored.firstOrNull { it.id == "builtin-github" })
    }

    @Test
    fun `the built-in seed set is exactly the intended four services`() {
        assertEquals(4, BuiltInCompanions.SEEDS.size)
        assertEquals(
            setOf("builtin-chatgpt", "builtin-claude", "builtin-zai", "builtin-github"),
            BuiltInCompanions.SEED_IDS,
        )
    }

    // ---------------------------------------------- M7.1 P2 — retired defaults

    @Test
    fun `stale hidden ids from retired defaults stay inert`() {
        // A device that hid Gemini CLI under P1 keeps a stale "gemini" id in
        // the persisted hidden set. The registry no longer carries the id, so
        // nothing can resurrect it — and the stale id must not disturb any
        // CURRENT launcher's visibility (restore stays exact).
        val hidden = setOf("gemini", "kilo")
        val visible = app.pocketshell.launchers.visibleTools(
            app.pocketshell.apps.CommandAppCatalog.registry, emptyList(), hidden,
        )
        assertFalse(visible.any { it.id == "gemini" }) // nothing stale exists to resurrect
        assertFalse(visible.any { it.id == "kilo" }) // the real hidden id is still honored
        val restored = app.pocketshell.launchers.visibleTools(
            app.pocketshell.apps.CommandAppCatalog.registry, emptyList(), hidden - "kilo",
        )
        assertTrue(restored.any { it.id == "kilo" }) // restore brings back CURRENT ids only
        assertEquals(
            "unhide must restore every registry launcher (the retired gemini id never reappears)",
            app.pocketshell.apps.CommandAppCatalog.registry.size,
            restored.size,
        )
    }
}

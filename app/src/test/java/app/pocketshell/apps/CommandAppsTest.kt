package app.pocketshell.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 3.2 invariants (docs/PHASE-3.2-DESIGN.md §4): packages are
 * infrastructure, apps are experiences. The registry contains only meaningful
 * interactive command apps, classification is purely guest-driven, and the
 * forbidden package list can never leak onto the launcher.
 */
class CommandAppsTest {

    @Test
    fun `registry is unique and complete`() {
        val reg = CommandAppCatalog.registry
        assertTrue("registry must not be empty", reg.isNotEmpty())
        assertEquals(reg.size, reg.map { it.id }.toSet().size)
        assertEquals(reg.size, reg.map { it.launchCommand.first() }.toSet().size)
        for (app in reg) {
            assertTrue(app.id.isNotBlank())
            assertTrue(app.displayName.isNotBlank())
            assertTrue(app.description.isNotBlank())
            assertTrue("monogram required: ${app.id}", app.monogram.isNotBlank())
            assertEquals("launch command is the plain executable name", app.id, app.launchCommand.first())
        }
    }

    @Test
    fun `seed registry contains the brief's command apps`() {
        assertNotNull(CommandAppCatalog.byId("hermes"))
        assertEquals("Hermes Agent", CommandAppCatalog.byId("hermes")?.displayName)
        assertEquals(listOf("hermes"), CommandAppCatalog.byId("hermes")?.launchCommand)
        assertNotNull(CommandAppCatalog.byId("opencode"))
        assertNotNull(CommandAppCatalog.byId("claude"))
        assertNotNull(CommandAppCatalog.byId("zcode"))
    }

    @Test
    fun `packages are infrastructure - never launcher apps`() {
        // The exact Phase 3.2 brief list, pinned forever.
        val forbidden = setOf(
            "git", "nano", "python", "python3", "node", "npm",
            "gcc", "g++", "htop", "vim",
            // and every normal shell utility
            "sh", "ls", "cat", "pwd", "df", "ps", "ping", "top", "echo", "which",
        )
        for (app in CommandAppCatalog.registry) {
            assertFalse(
                "${app.id} is a package/toolchain, not a launcher app",
                app.launchCommand.first() in forbidden,
            )
        }
        assertFalse("no catalog id may collide with a package name", forbidden.contains("hermes"))
    }

    @Test
    fun `launch command tokens are argv-safe`() {
        val safe = Regex("[A-Za-z0-9._/+%-]+")
        for (app in CommandAppCatalog.registry) {
            for (token in app.launchCommand) {
                assertNotEquals("", token)
                assertTrue("token '$token' must be argv-safe", token.matches(safe))
            }
        }
    }

    @Test
    fun `probeName is the launch command head`() {
        for (app in CommandAppCatalog.registry) {
            assertEquals(app.launchCommand.first(), app.probeName())
        }
    }

    @Test
    fun `availableCommandApps maps only real guest answers in registry order`() {
        // The registry subset the guest confirmed — nothing invented.
        val paths = mapOf(
            "hermes" to "/root/.local/bin/hermes",
            "claude" to "/usr/local/bin/claude",
            "git" to "/usr/bin/git", // a real probe answer that is NOT a registry app
        )
        val apps = availableCommandApps(paths)
        assertEquals(listOf("hermes", "claude"), apps.map { it.id })
        // registry order preserved regardless of the map's order
        val reordered = availableCommandApps(mapOf("claude" to "/x", "hermes" to "/y"))
        assertEquals(listOf("hermes", "claude"), reordered.map { it.id })
        // empty real answer = honest empty launcher
        assertTrue(availableCommandApps(emptyMap()).isEmpty())
    }

    @Test
    fun `absent binary can never render a tile`() {
        // zcode is seeded but the guest does not have it: it must not appear.
        val paths = mapOf("hermes" to "/root/.local/bin/hermes")
        val apps = availableCommandApps(paths)
        assertFalse(apps.any { it.id == "zcode" })
        assertTrue(apps.all { paths.containsKey(it.launchCommand.first()) })
    }

    @Test
    fun `byId resolves and rejects`() {
        assertEquals("hermes", CommandAppCatalog.byId("hermes")?.id)
        assertEquals(null, CommandAppCatalog.byId("not-a-real-app"))
    }

    private fun assertNotEquals(expected: String, actual: String) {
        assertFalse("expected not to equal '$expected'", expected == actual)
    }
}

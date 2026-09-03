package app.pocketshell.packages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Launchable-app detection rules (docs/UI-REDESIGN.md §9): a small,
 * hand-documented registry of INTERACTIVE applications only — ordinary CLI
 * tools are categorically excluded (they are packages, reached via search).
 */
class LaunchableAppsTest {

    @Test
    fun `registry stays small and hand-documented`() {
        assertTrue(
            "The registry is deliberately tiny; if you are adding many entries, " +
                "revisit the detection design first",
            LaunchableApps.entries.size <= 8,
        )
    }

    @Test
    fun `entries are unique by id and executable`() {
        assertEquals(
            LaunchableApps.entries.map { it.id }.toSet().size,
            LaunchableApps.entries.size,
        )
        assertEquals(
            LaunchableApps.entries.map { it.executable }.toSet().size,
            LaunchableApps.entries.size,
        )
    }

    @Test
    fun `no ordinary CLI tool is registered as a launchable app`() {
        // git/python/nano/htop/vim/gcc/node and friends are infrastructure,
        // not launchers (the brief is explicit). Their executables must never
        // appear here.
        val forbidden = setOf(
            "git", "python", "python3", "nano", "htop", "vim", "gcc", "g++",
            "node", "npm", "ls", "cat", "sh", "bash", "top", "less", "curl",
        )
        val offenders = LaunchableApps.entries.map { it.executable }.intersect(forbidden)
        assertTrue("CLI tools must stay packages: $offenders", offenders.isEmpty())
    }

    @Test
    fun `every entry launches its own executable`() {
        LaunchableApps.entries.forEach { app ->
            assertTrue(
                "${app.id}: launchCommand must start with the verified executable",
                app.launchCommand.firstOrNull() == app.executable,
            )
        }
    }
}

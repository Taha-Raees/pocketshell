package app.pocketshell.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M8.2 — the Home Application registry: the ONE card hosts ONE
 * application; Servers is the first and the default.
 */
class HomeApplicationsTest {

    @Test
    fun `ids are unique and well-shaped`() {
        val ids = HomeApplications.specs.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        ids.forEach { id ->
            assertTrue("id shape: $id", id.matches(Regex("""[a-z][a-z0-9.-]{1,63}""")))
        }
    }

    @Test
    fun `Servers is the first and default Home application`() {
        assertEquals("servers", HomeApplications.DEFAULT_ID)
        assertEquals("servers", HomeApplications.specs.first().id)
        val resolved = HomeApplications.resolve(HomeApplications.DEFAULT_ID)
        assertTrue(resolved is HomeApplications.Resolved.Found)
    }

    @Test
    fun `unknown ids resolve as Missing - never substituted`() {
        val resolved = HomeApplications.resolve("gone-app")
        assertTrue(resolved is HomeApplications.Resolved.Missing)
        assertEquals("gone-app", (resolved as HomeApplications.Resolved.Missing).id)
    }

    @Test
    fun `every application carries picker-ready metadata`() {
        HomeApplications.specs.forEach { spec ->
            assertTrue(spec.name.isNotBlank())
            assertTrue(spec.summary.length in 5..80)
        }
    }

    @Test
    fun `the Servers application copy is agent-vendor agnostic`() {
        val summary = HomeApplications.specs.first { it.id == "servers" }.summary.lowercase()
        listOf("claude", "codex", "zcode", "cline", "kilo").forEach { vendor ->
            assertFalse("copy must not name a vendor: $vendor", summary.contains(vendor))
        }
    }

    @Test
    fun `the id codec is shape-checked and defaults honestly`() {
        assertEquals("servers", HomeAppIdCodec.decode("servers"))
        assertEquals("servers", HomeAppIdCodec.decode(null))
        assertEquals("servers", HomeAppIdCodec.decode(""))
        assertEquals("servers", HomeAppIdCodec.decode("DROP"))
        assertEquals("servers", HomeAppIdCodec.decode("  "))
        assertEquals("tmux-app", HomeAppIdCodec.decode("tmux-app"))
        assertEquals("servers", HomeAppIdCodec.encode("servers"))
    }
}

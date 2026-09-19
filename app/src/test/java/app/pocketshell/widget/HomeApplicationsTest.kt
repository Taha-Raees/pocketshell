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
    fun `Todo is the first and default Home application`() {
        // M8.4.4: Servers and SSH moved to the downloadable catalog; the
        // fresh-install default is the local-first application.
        assertEquals("todo", HomeApplications.DEFAULT_ID)
        assertTrue(HomeApplications.all.first().spec.id == "todo")
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
    fun `every builtin application copy is agent-vendor agnostic`() {
        HomeApplications.specs.forEach { spec ->
            listOf("claude", "codex", "zcode", "cline", "kilo").forEach { vendor ->
                assertFalse(
                    "copy must not name a vendor: $vendor in ${spec.id}",
                    spec.summary.lowercase().contains(vendor),
                )
            }
        }
    }

    @Test
    fun `the list codec round-trips an ordered configuration`() {
        val ids = listOf("servers", "git", "ssh")
        assertEquals(ids, HomeAppIdCodec.decodeList(raw = HomeAppIdCodec.encode(ids), legacySingle = null))
    }

    @Test
    fun `absent corrupt or empty list decodes to the default application`() {
        assertEquals(listOf("todo"), HomeAppIdCodec.decodeList(raw = null, legacySingle = null))
        assertEquals(listOf("todo"), HomeAppIdCodec.decodeList(raw = "", legacySingle = null))
        assertEquals(listOf("todo"), HomeAppIdCodec.decodeList(raw = "not json", legacySingle = null))
        assertEquals(listOf("todo"), HomeAppIdCodec.decodeList(raw = "[]", legacySingle = null))
        assertEquals(
            listOf("todo"),
            HomeAppIdCodec.decodeList(raw = "[\"DROP\",\"..\"]", legacySingle = null),
        )
    }

    @Test
    fun `the M8_2 single-application record migrates to a one-entry list`() {
        // The legacy id is kept verbatim — a stored "servers" keeps its
        // carousel slot (it resolves as an installed catalog widget).
        assertEquals(
            listOf("servers"),
            HomeAppIdCodec.decodeList(raw = null, legacySingle = "servers"),
        )
        assertEquals(
            listOf("git"),
            HomeAppIdCodec.decodeList(raw = null, legacySingle = "git"),
        )
        // The list key wins whenever it exists — the legacy key is only a
        // seed, and the raw list passes through verbatim.
        assertEquals(
            listOf("servers"),
            HomeAppIdCodec.decodeList(raw = "[\"servers\"]", legacySingle = "git"),
        )
        // A corrupt legacy id still lands on the default, never on junk.
        assertEquals(
            listOf("todo"),
            HomeAppIdCodec.decodeList(raw = null, legacySingle = "DROP"),
        )
    }

    @Test
    fun `duplicates are collapsed and the list is capped at a reasonable size`() {
        assertEquals(
            listOf("servers", "git"),
            HomeAppIdCodec.decodeList(raw = "[\"servers\",\"servers\",\"git\"]", legacySingle = null),
        )
        val many = (1..40).joinToString(",") { i -> "\"a$i\"" }
        assertTrue(
            HomeAppIdCodec.decodeList(raw = "[$many]", legacySingle = null).size
                <= HomeAppIdCodec.MAX_APPS,
        )
    }

    @Test
    fun `unknown well-shaped ids survive decode and resolve as Missing`() {
        // A catalog application id that no longer resolves must render the
        // honest Missing card — the codec deliberately does NOT drop it.
        val decoded = HomeAppIdCodec.decodeList(raw = "[\"gone\",\"servers\"]", legacySingle = null)
        assertEquals(listOf("gone", "servers"), decoded)
        assertTrue(HomeApplications.resolve("gone") is HomeApplications.Resolved.Missing)
    }
}

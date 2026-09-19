package app.pocketshell.widget.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M8.4 — the SYNC/BACKUP model + store codec tests: the JSON round trip,
 * the corrupt-record honesty (decode NEVER invents profiles), the
 * validation gate (the leading-dash injection guard) and the pure
 * classification helpers.
 */
class SyncProfileTest {

    private fun profile(
        id: String = "p1",
        backend: SyncBackend = SyncBackend.RSYNC,
        source: String = "/root/project",
        destination: String = "/mnt/backup",
        lastRunMs: Long? = null,
    ) = SyncProfile(
        id = id,
        backend = backend,
        source = source,
        destination = destination,
        createdAtMs = 1_700_000_000_000,
        lastRunMs = lastRunMs,
    )

    // ------------------------------------------------------- round trip

    @Test
    fun `a profile survives the JSON round trip byte-for-byte in shape`() {
        val original = listOf(
            profile(),
            profile(
                id = "p2",
                backend = SyncBackend.RCLONE,
                source = "/root/photos",
                destination = "user@host:/srv/photos",
                lastRunMs = 1_700_000_100_000,
            ),
        )
        val decoded = SyncStoreCodec.decode(SyncStoreCodec.encode(original))
        assertEquals(original, decoded)
    }

    @Test
    fun `an empty list round trips as an empty list`() {
        val decoded = SyncStoreCodec.decode(SyncStoreCodec.encode(emptyList()))
        assertTrue(decoded.isEmpty())
    }

    // ---------------------------------------------------------- honesty

    @Test
    fun `a null record decodes to empty - no seeded profiles`() {
        assertTrue(SyncStoreCodec.decode(null).isEmpty())
    }

    @Test
    fun `a corrupt record decodes to empty - never invented profiles`() {
        assertTrue(SyncStoreCodec.decode("not json at all {{{").isEmpty())
        assertTrue(SyncStoreCodec.decode("[]trailing").isEmpty())
        assertTrue(SyncStoreCodec.decode("\"a string\"").isEmpty())
    }

    @Test
    fun `records with blank specs are dropped - they cannot be probed honestly`() {
        val raw = SyncStoreCodec.encode(
            listOf(
                profile(id = "good"),
                profile(id = "blank-src", source = "   "),
                profile(id = "blank-dst", destination = ""),
            ),
        )
        val decoded = SyncStoreCodec.decode(raw)
        assertEquals(listOf("good"), decoded.map { it.id })
    }

    @Test
    fun `records with leading-dash specs are dropped at decode too`() {
        // Refused at creation, but a record that arrives with one (hand edit,
        // future writer bug) must not reach the exec layer either.
        val raw = SyncStoreCodec.encode(
            listOf(
                profile(id = "dash", source = "--delete"),
                profile(id = "ok"),
            ),
        )
        val decoded = SyncStoreCodec.decode(raw)
        assertEquals(listOf("ok"), decoded.map { it.id })
    }

    @Test
    fun `duplicate ids dedupe first-wins and the list is capped`() {
        val raw = SyncStoreCodec.encode(
            listOf(
                profile(id = "a", source = "/one"),
                profile(id = "a", source = "/two"),
                profile(id = "b"),
            ),
        )
        val decoded = SyncStoreCodec.decode(raw)
        assertEquals(listOf("a", "b"), decoded.map { it.id })
        assertEquals("/one", decoded.first { it.id == "a" }.source)

        val overCap = (1..(SyncProfiles.MAX_PROFILES + 3)).map { profile(id = "p$it") }
        assertEquals(SyncProfiles.MAX_PROFILES, SyncStoreCodec.decode(SyncStoreCodec.encode(overCap)).size)
    }

    @Test
    fun `unknown json keys are ignored - older app reads newer store`() {
        val raw =
            """[{"id":"p1","backend":"RSYNC","source":"/a","destination":"/b","createdAtMs":1,"futureField":42}]"""
        val decoded = SyncStoreCodec.decode(raw)
        assertEquals(1, decoded.size)
        assertEquals("/a", decoded[0].source)
    }

    // ------------------------------------------------------- validation

    @Test
    fun `the validation gate refuses leading-dash specs - the argv option guard`() {
        assertNull(SyncProfiles.validate("/root/project", "/mnt/backup"))
        assertEquals(
            "paths must not start with \"-\"",
            SyncProfiles.validate("-oProxyCommand=evil", "/mnt/backup"),
        )
        assertEquals(
            "paths must not start with \"-\"",
            SyncProfiles.validate("/root/project", "--dry-run"),
        )
    }

    @Test
    fun `the validation gate refuses blanks and identical specs`() {
        assertEquals("source is required", SyncProfiles.validate("  ", "/mnt/backup"))
        assertEquals("destination is required", SyncProfiles.validate("/root", ""))
        assertEquals(
            "source and destination are the same path",
            SyncProfiles.validate("/root", "/root"),
        )
    }

    @Test
    fun `the validation gate enforces the length cap`() {
        val long = "a".repeat(SyncProfiles.SPEC_MAX_LENGTH + 1)
        assertEquals(
            "paths are limited to ${SyncProfiles.SPEC_MAX_LENGTH} characters",
            SyncProfiles.validate(long, "/mnt/backup"),
        )
        assertNull(SyncProfiles.validate("a".repeat(SyncProfiles.SPEC_MAX_LENGTH), "/mnt/backup"))
    }

    // ---------------------------------------------------- classification

    @Test
    fun `remote detection - colon before any slash means remote`() {
        assertTrue(SyncProfiles.isRemote("user@host:/srv/backup"))
        assertTrue(SyncProfiles.isRemote("host:rel/path"))
        assertTrue(SyncProfiles.isRemote("gdrive:photos"))
        assertTrue(SyncProfiles.isRemote(":sftp,host=h:/path"))
        org.junit.Assert.assertFalse(SyncProfiles.isRemote("/mnt/backup"))
        org.junit.Assert.assertFalse(SyncProfiles.isRemote("/root/deep/er:name"))
        org.junit.Assert.assertFalse(SyncProfiles.isRemote("relative/path"))
    }

    @Test
    fun `guest home displays as tilde - everything else verbatim`() {
        assertEquals("~/project", SyncProfiles.displayPath("/root/project"))
        assertEquals("~", SyncProfiles.displayPath("/root"))
        assertEquals("/mnt/backup", SyncProfiles.displayPath("/mnt/backup"))
        assertEquals("user@host:/srv", SyncProfiles.displayPath("user@host:/srv"))
    }

    @Test
    fun `the overview line joins source and destination`() {
        assertEquals(
            "~/project → /mnt/backup",
            SyncProfiles.line(profile(source = "/root/project", destination = "/mnt/backup")),
        )
    }

    // ----------------------------------------------------------- upsert

    @Test
    fun `upsert replaces by id and caps the list`() {
        val list = listOf(profile("a"), profile("b"))
        val updated = SyncProfiles.upsert(list, profile("b", source = "/new"))
        assertEquals(2, updated.size)
        assertEquals("/new", updated.first { it.id == "b" }.source)

        val full = (1..SyncProfiles.MAX_PROFILES).map { profile("p$it") }
        val added = SyncProfiles.upsert(full, profile("new"))
        assertEquals(SyncProfiles.MAX_PROFILES, added.size)
        assertEquals("new", added.last().id)
        assertEquals("p2", added.first().id)
    }
}

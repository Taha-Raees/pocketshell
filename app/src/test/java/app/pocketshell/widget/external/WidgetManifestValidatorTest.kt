package app.pocketshell.widget.external

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M8 — manifest validation: the catalog can only hand us data, and every
 * malformed or oversized field is a REJECTION, never a partially trusted
 * widget. The security property: probe kinds are built-in primitives only;
 * there is no surface for commands.
 */
class WidgetManifestValidatorTest {

    private val valid = WidgetManifest(
        id = "servers",
        name = "Servers",
        version = "1.0.0",
        summary = "Listening ports and what owns them",
        author = "PocketShell core (example)",
        minAppVersion = "0.13.0",
        capabilities = listOf("proc.net", "guest.ready"),
        probe = WidgetManifest.Probe(kind = "proc.net.listen"),
        card = WidgetManifest.Card(
            headline = "Servers",
            emptyLine = "Nothing listening",
            itemTemplate = ":{port}  {process}",
            maxLines = 3,
        ),
    )

    @Test
    fun `a well-formed manifest validates`() {
        val result = WidgetManifestValidator.validate(valid)
        assertEquals(valid, (result as WidgetManifestValidator.Result.Valid).manifest)
    }

    @Test
    fun `json parses strictly - unknown keys reject`() {
        val good = WidgetManifestValidator.parse(
            """{"id":"servers","name":"Servers","version":"1.0.0","minAppVersion":"0.13.0",""" +
                """"capabilities":["proc.net"],"probe":{"kind":"proc.net.listen"},""" +
                """"card":{"maxLines":3}}""",
        )
        assertTrue(good is WidgetManifestValidator.Result.Valid)

        val drift = WidgetManifestValidator.parse(
            """{"id":"servers","name":"Servers","version":"1.0.0","probe":{"kind":"proc.net.listen",""" +
                """"card":{"maxLines":3},"command":"rm -rf /"}}""",
        )
        assertTrue(drift is WidgetManifestValidator.Result.Invalid)
    }

    @Test
    fun `ids names and versions are shape-checked`() {
        listOf(
            "ID" to valid.copy(id = "Servers"),
            "ID" to valid.copy(id = "x"),
            "VERSION" to valid.copy(version = "1.0"),
            "MINAPP" to valid.copy(minAppVersion = "latest"),
            "NAME" to valid.copy(name = ""),
            "NAME" to valid.copy(name = "a".repeat(25)),
        ).forEach { (what, candidate) ->
            assertTrue(
                "$what must reject",
                WidgetManifestValidator.validate(candidate) is WidgetManifestValidator.Result.Invalid,
            )
        }
    }

    @Test
    fun `probe kinds are built-in primitives only - no command surface`() {
        val result = WidgetManifestValidator.validate(
            valid.copy(probe = WidgetManifest.Probe(kind = "sh", params = mapOf("cmd" to "rm -rf /"))),
        )
        val reasons = (result as WidgetManifestValidator.Result.Invalid).reasons
        assertTrue(reasons.any { it.contains("built-in primitive") })
    }

    @Test
    fun `a probe requires its declared capability`() {
        val result = WidgetManifestValidator.validate(
            valid.copy(capabilities = listOf("guest.ready")),
        )
        val reasons = (result as WidgetManifestValidator.Result.Invalid).reasons
        assertTrue(reasons.any { it.contains("requires capability 'proc.net'") })
    }

    @Test
    fun `ssh guest manifests are a built-in primitive with their own capability`() {
        val result = WidgetManifestValidator.validate(
            valid.copy(
                probe = WidgetManifest.Probe(kind = WidgetManifestValidator.SSH_GUEST),
                capabilities = listOf("guest.ready"),
            ),
        )
        assertTrue(result is WidgetManifestValidator.Result.Valid)
    }

    @Test
    fun `ssh guest manifests refuse without their capability`() {
        val result = WidgetManifestValidator.validate(
            valid.copy(
                probe = WidgetManifest.Probe(kind = WidgetManifestValidator.SSH_GUEST),
                capabilities = listOf("proc.net"),
            ),
        )
        val reasons = (result as WidgetManifestValidator.Result.Invalid).reasons
        assertTrue(reasons.any { it.contains("requires capability 'guest.ready'") })
    }

    @Test
    fun `ssh guest takes no probe params - no command surface`() {
        val result = WidgetManifestValidator.validate(
            valid.copy(
                probe = WidgetManifest.Probe(
                    kind = WidgetManifestValidator.SSH_GUEST,
                    params = mapOf("host" to "evil.example.com"),
                ),
                capabilities = listOf("guest.ready"),
            ),
        )
        val reasons = (result as WidgetManifestValidator.Result.Invalid).reasons
        assertTrue(reasons.any { it.contains("unknown probe params") })
    }

    @Test
    fun `unknown capabilities reject`() {
        val result = WidgetManifestValidator.validate(
            valid.copy(capabilities = listOf("proc.net", "root.shell")),
        )
        val reasons = (result as WidgetManifestValidator.Result.Invalid).reasons
        assertTrue(reasons.any { it.contains("unknown capabilities") })
    }

    @Test
    fun `card fields are bounded`() {
        assertTrue(
            WidgetManifestValidator.validate(valid.copy(card = valid.card.copy(maxLines = 7)))
                is WidgetManifestValidator.Result.Invalid,
        )
        assertTrue(
            WidgetManifestValidator.validate(valid.copy(card = valid.card.copy(itemTemplate = "{x}".repeat(30))))
                is WidgetManifestValidator.Result.Invalid,
        )
        assertTrue(
            WidgetManifestValidator.validate(valid.copy(card = valid.card.copy(headline = "h".repeat(25))))
                is WidgetManifestValidator.Result.Invalid,
        )
    }
}

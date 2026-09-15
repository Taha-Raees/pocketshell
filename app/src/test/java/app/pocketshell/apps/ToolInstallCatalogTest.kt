package app.pocketshell.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One-click install (owner iteration 4): every Home tool has an honest
 * install story, every installer line is hygiene-clean, and the chain
 * transports the command verbatim into the same `sh -l -c …; exec` delivery
 * every launch uses.
 */
class ToolInstallCatalogTest {

    @Test
    fun `every registry tool has an install spec`() {
        val missing = CommandAppCatalog.registry.map { it.id } - ToolInstallCatalog.specsKeys()
        assertTrue("specs missing for: $missing", missing.isEmpty())
    }

    @Test
    fun `every installable spec carries EXACTLY ONE of command or script, never a failure note`() {
        ToolInstallCatalog.specsKeys().forEach { id ->
            val spec = ToolInstallCatalog.specFor(id)!!
            val hasCommand = spec.command != null
            val hasScript = spec.script != null
            assertTrue(
                "$id: exactly one of command/script (command=${'$'}hasCommand script=${'$'}hasScript)",
                hasCommand xor hasScript,
            )
            assertNull("$id: installable spec must not carry a failure note", spec.note)
            // NPM specs always ride a command line; SCRIPT specs may carry
            // either a one-line pipeline (hermes) or a full script (agy)
            if (spec.method == InstallMethod.NPM) {
                assertTrue("$id: NPM spec must carry a command", hasCommand)
            }
            spec.attribution?.let {
                assertTrue("$id: attribution must be the validated form", it == ToolInstallCatalog.validateAttribution(it))
            }
        }
    }

    @Test
    fun `zcode rides the UNOFFICIAL community client with the honest attribution`() {
        val spec = ToolInstallCatalog.specFor("zcode")!!
        assertEquals(InstallMethod.NPM, spec.method)
        assertTrue(spec.command!!.contains("npm install -g zcode-app-cli"))
        assertTrue(spec.attribution!!.contains("Unofficial"))
        assertTrue(spec.attribution.contains("not affiliated with Z.ai"))
    }

    @Test
    fun `agy installs from the OFFICIAL release manifest with sha512 verification`() {
        val spec = ToolInstallCatalog.specFor("agy")!!
        assertEquals(InstallMethod.SCRIPT, spec.method)
        val script = spec.script!!
        // the honest path: official manifest -> sha512 verify -> install -> prove
        assertTrue(script.contains("manifests/linux_arm64.json"))
        assertTrue(script.contains("sha512sum -c -"))
        assertTrue(script.contains("install -m 0755"))
        assertTrue(script.contains("agy --version"))
        // no musl spoofing, no checksum skipping — the two things the
        // ANTIGRAVITY-PLATFORM audit forbade
        assertTrue(!script.contains("linux_arm64_musl"))
        assertTrue(!script.lowercase().contains("--no-check"))
    }

    @Test
    fun `the script transport chain round-trips the payload byte-for-byte`() {
        val script = "set -e\necho one\nagy --version\n"
        val chain = guestInstallScriptChain(
            displayName = "Antigravity",
            installScript = script,
            guestShell = "/bin/sh",
            attribution = "test note",
        )
        assertTrue("single line only", !chain.contains('\n'))
        assertTrue(chain.contains("sh -x")) // every script line traced visibly
        assertEquals(script, decodeInstallScriptPayload(chain))
    }

    @Test
    fun `every install command passes hygiene and survives it unchanged`() {
        ToolInstallCatalog.specsKeys().forEach { id ->
            val spec = ToolInstallCatalog.specFor(id)!!
            val command = spec.command ?: return@forEach
            assertEquals("$id: command must be the validated form", command, ToolInstallCatalog.validateCommand(command))
        }
    }

    @Test
    fun `hygiene rejects quotes dollars backticks newlines and oversize lines`() {
        assertNull(ToolInstallCatalog.validateCommand("npm install -g \"pkg\""))
        assertNull(ToolInstallCatalog.validateCommand("echo \$HOME"))
        assertNull(ToolInstallCatalog.validateCommand("echo `id`"))
        assertNull(ToolInstallCatalog.validateCommand("one\ntwo"))
        assertNull(ToolInstallCatalog.validateCommand(""))
        assertNull(ToolInstallCatalog.validateCommand(null))
        assertNull(ToolInstallCatalog.validateCommand("x".repeat(513)))
        // the practical shapes all pass
        assertNotNull(ToolInstallCatalog.validateCommand("npm install -g @openai/codex"))
        assertNotNull(
            ToolInstallCatalog.validateCommand(
                "command -v npm >/dev/null 2>&1 || apk add --no-cache nodejs npm; npm install -g @openai/codex",
            ),
        )
    }

    @Test
    fun `the hermes installer carries the guest-required UV_LINK_MODE workaround`() {
        // device-validated contract (docs/M2.6-RESEARCH.md §8.5): without
        // UV_LINK_MODE=copy uv's hardlink cache sync dies on the l2s EPERM
        val command = ToolInstallCatalog.specFor("hermes")!!.command!!
        assertTrue(command.contains("UV_LINK_MODE=copy"))
        assertTrue(command.contains("install.sh | bash"))
    }

    @Test
    fun `the install chain runs the command verbatim and lands at a real prompt`() {
        val command = "npm install -g @openai/codex"
        val chain = guestInstallChain(
            displayName = "Codex",
            installCommand = command,
            guestShell = "/bin/sh",
        )
        assertTrue("single line only", !chain.contains('\n'))
        assertTrue(chain.contains(command)) // verbatim transport — nothing rewritten
        assertTrue(chain.startsWith("echo \"PocketShell · installing Codex\""))
        assertTrue(chain.endsWith("exec /bin/sh -l"))
    }
}

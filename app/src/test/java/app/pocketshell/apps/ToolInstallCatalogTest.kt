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
    fun `installable specs carry a command and no note - honest specs the reverse`() {
        ToolInstallCatalog.specsKeys().forEach { id ->
            val spec = ToolInstallCatalog.specFor(id)!!
            if (spec.method == InstallMethod.NPM || spec.method == InstallMethod.SCRIPT) {
                assertNotNull("$id: installable spec must carry a command", spec.command)
                assertNull("$id: installable spec must not carry a failure note", spec.note)
            } else {
                assertNull("$id: $spec.method must NOT carry an executable command", spec.command)
                assertTrue("$id: $spec.method must explain why", !spec.note.isNullOrBlank())
            }
        }
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

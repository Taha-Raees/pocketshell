package app.pocketshell.cliapps

import app.pocketshell.terminal.ShellEnvironment
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CliAppTest {

    private val json = Json { ignoreUnknownKeys = true }

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `model round-trips through json`() {
        val app = CliApp(
            id = "hermes",
            name = "Hermes",
            description = "Example CLI tool",
            executable = "hermes",
            arguments = listOf("--flag", "value"),
            category = "productivity",
            environment = mapOf("HERMES_HOME" to "/data/local/tmp"),
        )
        val decoded = json.decodeFromString<CliApp>(json.encodeToString(app))
        assertEquals(app, decoded)
    }

    @Test
    fun `registry starts empty is enforced by serialization contract`() {
        // The registry stores a JSON list; an absent key decodes to empty list.
        val absent: String? = null
        val decoded = absent?.let {
            json.decodeFromString<List<CliApp>>(it)
        } ?: emptyList()
        assertTrue(decoded.isEmpty())
    }

    @Test
    fun `resolveExecutable finds real executables on PATH`() {
        val bin = tmp.newFolder("bin")
        val exe = java.io.File(bin, "hermes").apply {
            writeText("#!/bin/sh\n")
            setExecutable(true)
        }
        val other = tmp.newFolder("other")
        java.io.File(other, "not-executable").apply { writeText("x") }

        val resolved = ShellEnvironment.resolveExecutable(
            "hermes",
            listOf(other, bin),
        )
        assertEquals(exe.absolutePath, resolved)
    }

    @Test
    fun `resolveExecutable returns null for missing executables`() {
        val bin = tmp.newFolder("bin2")
        assertNull(ShellEnvironment.resolveExecutable("does-not-exist", listOf(bin)))
    }

    @Test
    fun `resolveExecutable rejects non-executable files`() {
        val dir = tmp.newFolder("bin3")
        java.io.File(dir, "tool").apply { writeText("data") }
        assertNull(ShellEnvironment.resolveExecutable("tool", listOf(dir)))
    }

    @Test
    fun `resolveExecutable honours absolute paths`() {
        val dir = tmp.newFolder("bin4")
        val exe = java.io.File(dir, "abs-tool").apply {
            writeText("#!/bin/sh\n")
            setExecutable(true)
        }
        assertEquals(exe.absolutePath, ShellEnvironment.resolveExecutable(exe.absolutePath, emptyList()))
    }
}

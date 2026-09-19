package app.pocketshell.widget.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * M8.3 — the SSH application's structural contract, read from the sources
 * that ship (the established technique: the JVM suite has no device
 * runner, so boundaries are pinned by reading what ships).
 *
 * Pinned here:
 *   1. THEME: only the shared HomeTokens/TerminalTheme — no hardcoded
 *      colors, no theme-name literals, no canvas-tone text pair.
 *   2. IN-CARD NAVIGATION: process-scoped-holder detail + the card's own
 *      BackHandler; actions ONLY through the WidgetNav seam.
 *   3. NO EXECUTION SURFACE: the application never spawns anything —
 *      "never auto-connect" is structural, not a comment.
 *   4. KEY SAFETY: identity keys are handled as NAMES only. The pure
 *      layers never touch files; the single ~/.ssh reader opens exactly
 *      "config" and "known_hosts"; no source fabricates default key
 *      names; nothing is ever written to ~/.ssh.
 *   5. HONESTY: the UI never claims "connected" — argv evidence is
 *      worded as processes, not sessions.
 */
class SshAppContractTest {

    // ------------------------------------------------------------ helpers

    private fun readSource(relative: String): String {
        val file = listOf(relative, "../$relative")
            .map { File(it) }
            .firstOrNull { it.isFile }
        assumeTrue("source not found on this runner: $relative", file != null)
        return file!!.readText()
    }

    /** Comments + string CONTENTS stripped — structural tokens only. */
    private fun stripCommentsAndStrings(source: String): String {
        val out = StringBuilder(source.length)
        var i = 0
        while (i < source.length) {
            val c = source[i]
            when {
                c == '/' && i + 1 < source.length && source[i + 1] == '*' -> {
                    i = source.indexOf("*/", i + 2).let { if (it < 0) source.length else it + 2 }
                }
                c == '/' && i + 1 < source.length && source[i + 1] == '/' -> {
                    while (i < source.length && source[i] != '\n') i++
                }
                c == '"' -> {
                    i++
                    while (i < source.length && source[i] != '"') {
                        if (source[i] == '\\') i++
                        i++
                    }
                    i++
                    out.append("\"\"")
                }
                else -> {
                    out.append(c)
                    i++
                }
            }
        }
        return out.toString()
    }

    private fun stringLiterals(source: String): List<String> {
        val literals = mutableListOf<String>()
        var i = 0
        while (i < source.length) {
            if (source[i] == '"') {
                val start = i + 1
                var j = start
                while (j < source.length && source[j] != '"') {
                    if (source[j] == '\\') j++
                    j++
                }
                literals += source.substring(start, j.coerceAtMost(source.length))
                i = j + 1
            } else {
                i++
            }
        }
        return literals
    }

    private fun sshDir(): File =
        File("app/src/main/java/app/pocketshell/widget/ssh")
            .takeIf { it.isDirectory }
            ?: File("../app/src/main/java/app/pocketshell/widget/ssh")

    private fun sshSources(): List<Pair<String, String>> {
        val dir = sshDir()
        assumeTrue("ssh source dir not found on this runner", dir.isDirectory)
        return dir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".kt") }
            .map { it.name to it.readText() }
            .toList()
    }

    private fun source(name: String): String =
        readSource("app/src/main/java/app/pocketshell/widget/ssh/$name")

    // ------------------------------------------------------- 1. theme only

    @Test
    fun `the SSH application follows the shared theme - no private palette`() {
        val raw = source("SshApp.kt")
        val code = stripCommentsAndStrings(raw)
        assertTrue("must read the shared tokens", code.contains("HomeTokens."))
        assertTrue("must read the theme typeface", code.contains("TerminalTheme."))
        assertFalse("no canvas-tone text pair", code.contains("onHero"))
        assertFalse(
            "no hardcoded colors — the selected theme is the only palette",
            Regex("""Color\(0x""").containsMatchIn(code),
        )
        assertFalse(
            "no theme-name literals (a theme is ONE option, never a dependency)",
            stringLiterals(raw).any { it.contains("Aurora", ignoreCase = true) },
        )
    }

    // ------------------------------------------------- 2. in-card navigation

    @Test
    fun `detail state lives in the process-scoped holder and the card owns its back`() {
        val code = stripCommentsAndStrings(source("SshApp.kt"))
        assertTrue(
            "snapshot + detail selection must live in the HomeAppStateStore holder (survives Home disposal)",
            code.contains("stateStore.forApp"),
        )
        assertTrue(
            "back inside the card returns to the overview before leaving Home",
            code.contains("BackHandler(enabled = selected != null)"),
        )
        assertTrue("the terminal is the only way to connect", code.contains("nav.openTerminal()"))
        assertTrue("the guest is where ssh runs", code.contains("nav.openLinuxShell()"))
    }

    // -------------------------------------------------- 3. no execution surface

    @Test
    fun `the application never executes anything - no auto-connect is structural`() {
        sshSources().forEach { (name, text) ->
            val code = stripCommentsAndStrings(text)
            val banned = listOf(
                "ProcessBuilder", "Runtime.getRuntime", ".exec(",
                "Runtime.exec", "libproot", "su ",
            )
            val found = banned.filter { code.contains(it) }
            assertTrue("$name must never execute anything; found: $found", found.isEmpty())
        }
    }

    // ------------------------------------------------------- 4. key safety

    @Test
    fun `the pure layers never touch files`() {
        listOf("SshConfigParser.kt", "KnownHosts.kt").forEach { name ->
            val code = stripCommentsAndStrings(source(name))
            val banned = listOf("java.io.File", "readText", "readBytes", "readLines")
            val found = banned.filter { code.contains(it) }
            assertTrue("$name must stay pure (String in, data out); found: $found", found.isEmpty())
        }
    }

    @Test
    fun `the process probe reads cmdlines and never the ssh directory`() {
        val raw = source("SshProcessProbe.kt")
        val code = stripCommentsAndStrings(raw)
        // "cmdline" lives only inside string templates, which the stripper
        // blanks — so this pin reads the raw source.
        assertTrue("the probe's evidence is /proc cmdlines", raw.contains("cmdline"))
        // ".ssh/" with the slash: the bare token would false-positive on the
        // package declaration (…widget.ssh).
        listOf(".ssh/", "known_hosts", "\"config\"").forEach { token ->
            assertFalse(
                "the process probe must never look at ~/.ssh; found $token",
                code.contains(token) || raw.contains(token),
            )
        }
    }

    @Test
    fun `SshFiles is the only ssh reader and opens exactly config and known_hosts`() {
        val raw = source("SshFiles.kt")
        val code = stripCommentsAndStrings(raw)
        // The two fixed NAMES, and no write path, ever.
        assertTrue(raw.contains("\"config\""))
        assertTrue(raw.contains("\"known_hosts\""))
        val banned = listOf(
            "writeText", "writeBytes", "FileOutputStream", "delete()",
            "deleteOnExit", "IdentityFile", "identity",
        )
        val found = banned.filter { code.lowercase().contains(it.lowercase()) }
        assertTrue(
            "the ~/.ssh reader must be read-only and name-blind to keys; found: $found",
            found.isEmpty(),
        )
        // Every OTHER ssh source is free of file-content reads.
        sshSources().forEach { (name, text) ->
            if (name == "SshFiles.kt" || name == "SshProcessProbe.kt") return@forEach
            val other = stripCommentsAndStrings(text)
            val reads = listOf("readText", "readBytes", "readLines", "java.io.File")
                .filter { other.contains(it) }
            assertTrue("$name must not read files; found: $reads", reads.isEmpty())
        }
    }

    @Test
    fun `no source fabricates default key names - identities come from the config only`() {
        sshSources().forEach { (name, text) ->
            val banned = listOf("id_rsa", "id_ed25519", "id_ecdsa", "id_dsa")
            val found = stringLiterals(text)
                .flatMap { literal -> banned.filter { literal.lowercase().contains(it) } }
            assertTrue("$name must never guess a key name; found: $found", found.isEmpty())
        }
    }

    // ----------------------------------------------------------- 5. honesty

    @Test
    fun `the UI words argv evidence as processes - never as connected sessions`() {
        val literals = stringLiterals(source("SshApp.kt"))
        val banned = listOf("connected", "connection is", "online", "reachable", "latency", "health")
        val violations = literals.flatMap { literal ->
            banned.filter { literal.lowercase().contains(it) }.map { token -> token to literal }
        }
        assertTrue("invented session-state claims: $violations", violations.isEmpty())
    }

    // -------------------------------------------------------------- spec

    @Test
    fun `the spec carries the registry identity`() {
        assertEquals("ssh", SshApp.spec.id)
        assertEquals("SSH", SshApp.spec.name)
        assertTrue(SshApp.spec.summary.isNotBlank())
    }
}

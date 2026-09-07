package app.pocketshell.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun `phase 3_4 expansion pins the terminal agent CLIs`() {
        // The user-reported gap: an installed Kilo Code CLI (command `kilo`)
        // must be a known app so the guest probe can surface it.
        assertEquals("kilo", CommandAppCatalog.byId("kilo")?.id)
        assertEquals("Kilo Code", CommandAppCatalog.byId("kilo")?.displayName)
        assertEquals(listOf("kilo"), CommandAppCatalog.byId("kilo")?.launchCommand)
        assertEquals("K", CommandAppCatalog.byId("kilo")?.monogram)
        // The peer terminal agents seeded alongside it (M7.1 P2: Antigravity
        // REPLACED Gemini CLI in the curated default set — see the dedicated
        // P2 section below).
        assertEquals("Antigravity", CommandAppCatalog.byId("agy")?.displayName)
        assertEquals("Codex", CommandAppCatalog.byId("codex")?.displayName)
        // (M7.1 P2.1: Aider left the curated set — absence pinned in the
        // dedicated P2.1 test below.)
        assertEquals("Qwen Code", CommandAppCatalog.byId("qwen")?.displayName)
        // Expansion appends AFTER the brief's four — device launcher order
        // for already-known apps never shuffles.
        assertEquals(
            listOf("hermes", "opencode", "claude", "zcode"),
            CommandAppCatalog.registry.take(4).map { it.id },
        )
    }

    @Test
    fun `expansion entries stay probe-gated like every other app`() {
        // An installed-but-unregistered binary never appeared before, and an
        // installed REGISTERED app appears only when the guest names it.
        val paths = mapOf("kilo" to "/usr/local/bin/kilo")
        val apps = availableCommandApps(paths)
        assertEquals(listOf("kilo"), apps.map { it.id })
        // Absent binary can never render — the honesty contract holds for
        // every expansion entry.
        val none = availableCommandApps(mapOf("git" to "/usr/bin/git"))
        assertTrue(none.isEmpty())
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
        // Phase 3.4 expansion ids are applications, not toolchains
        // (M7.1 P2: the Antigravity CLI's official binary name joined the set;
        // M7.1 P2.1: Aider left the set entirely).
        for (id in listOf("kilo", "agy", "codex", "qwen")) {
            assertFalse(
                "$id must stay out of the forbidden package namespace",
                id in forbidden,
            )
        }
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

    // --------------------------------------------------- v0.7.0-m3.5 launch fix

    @Test
    fun `guestLaunchChain runs the command through shell argv with an exec fallback`() {
        // The m3.4 regression: the PTY write after construction was a silent
        // no-op (the process does not exist until the view renders it), so a
        // tapped tile opened a PLAIN shell. The chain must deliver the
        // command as the login shell's -c argv and exec a fresh login shell
        // after the app exits.
        val chain = guestLaunchChain(listOf("kilo"), "/bin/sh")
        assertEquals("kilo; exec /bin/sh -l", chain)
    }

    @Test
    fun `guestLaunchChain joins multi-token commands and keeps the fallback last`() {
        val chain = guestLaunchChain(listOf("npm", "exec", "kilo", "--profile", "ci"), "/bin/sh")
        assertEquals("npm exec kilo --profile ci; exec /bin/sh -l", chain)
    }

    @Test
    fun `guestLaunchChain quotes unsafe tokens as defense in depth`() {
        val chain = guestLaunchChain(listOf("my app", "--flag"), "/bin/sh")
        assertEquals("'my app' --flag; exec /bin/sh -l", chain)
    }

    @Test
    fun `every registry app builds a chain that execs back to the guest shell`() {
        for (app in CommandAppCatalog.registry) {
            val chain = guestLaunchChain(app.launchCommand, "/bin/sh")
            assertTrue(
                "${app.id} chain must end with the login-shell fallback",
                chain.endsWith("; exec /bin/sh -l"),
            )
            assertTrue(
                "${app.id} command must lead the chain",
                chain.startsWith(app.launchCommand.first()),
            )
        }
    }

    // --------------------------------------- M7.0.0 Phase 7 — Open Terminal Here

    /**
     * The Phase 7 sibling chain, pinned exact-string per metacharacter class.
     * The directory is ONE quoted POSIX word — the assertions below prove the
     * path travels as literal DATA (an exact expected string, not a
     * "contains quotes" hint): `cd --`, POSIX `'` → `'\''` escaping, and the
     * `exec <shell> -l` interactive takeover are all pinned here.
     */
    @Test
    fun `guestTerminalChain cds into the directory and execs the login shell`() {
        assertEquals(
            "cd -- '/root/projects' && exec /bin/sh -l",
            guestTerminalChain("/root/projects", "/bin/sh"),
        )
    }

    @Test
    fun `guestTerminalChain treats every shell metacharacter as literal path data`() {
        val cases: List<Pair<String, String>> = listOf(
            // (directory value, the exact chain it must produce)
            "my folder" to "cd -- '/root/my folder' && exec /bin/sh -l",
            "it's-here" to "cd -- '/root/it'\\''s-here' && exec /bin/sh -l",
            "a\"b" to "cd -- '/root/a\"b' && exec /bin/sh -l",
            "\$HOME" to "cd -- '/root/\$HOME' && exec /bin/sh -l",
            "semi;colon" to "cd -- '/root/semi;colon' && exec /bin/sh -l",
            "a && b" to "cd -- '/root/a && b' && exec /bin/sh -l",
            "a | b" to "cd -- '/root/a | b' && exec /bin/sh -l",
            "`cmd`" to "cd -- '/root/`cmd`' && exec /bin/sh -l",
            "multi\nline" to "cd -- '/root/multi\nline' && exec /bin/sh -l",
            "-dash-start" to "cd -- '/root/-dash-start' && exec /bin/sh -l",
        )
        for ((directory, expected) in cases) {
            assertEquals(
                "directory '$directory' must travel as one literal value",
                expected,
                guestTerminalChain("/root/$directory", "/bin/sh"),
            )
        }
    }

    @Test
    fun `guestTerminalChain pins the structural contract`() {
        val directories = listOf(
            "/root/projects",
            "/root/my app",
            "/root/it's-here",
            "/root/\$HOME",
            "/root/semi;colon",
            "/root/a && b",
            "/root/a | b",
            "/root/`cmd`",
            "/root/multi\nline",
        )
        for (directory in directories) {
            val chain = guestTerminalChain(directory, "/bin/sh")
            assertTrue(
                "cd -- must lead the chain (option parsing ends before the path): $chain",
                chain.startsWith("cd -- '"),
            )
            assertTrue(
                "the interactive login shell must follow a SUCCESSFUL cd only (&&): $chain",
                chain.contains(" && exec /bin/sh -l"),
            )
            assertFalse(
                "the joiner must be &&, never ';' (a failed cd must not silently land the user somewhere else): $chain",
                chain.contains("; exec"),
            )
            assertTrue(
                "the chain must end with the exec'd login shell: $chain",
                chain.endsWith(" && exec /bin/sh -l"),
            )
        }
    }

    /**
     * Execution-level proof (temporary shell fixture): run the REAL chain
     * through /bin/sh on the test host. The final exec'd shell reads `pwd`
     * from stdin and must print the directory LITERALLY — every metacharacter
     * at once. If the quoting ever let shell syntax through, this directory
     * name would execute as commands and the assertion would fail.
     */
    @Test
    fun `guestTerminalChain executes - every metacharacter stays one literal value`() {
        org.junit.Assume.assumeTrue(
            "POSIX shell required for the execution fixture",
            java.io.File("/bin/sh").exists(),
        )
        val name = "my 'dir' with \$dollar ; semi && amp | pipe `cmd` \"quote\"\nsecond line"
        val root = java.nio.file.Files.createTempDirectory("p7-terminal-chain").toFile()
        try {
            val dir = java.io.File(root, name)
            assertTrue("fixture directory must exist", dir.mkdirs())
            val chain = guestTerminalChain(dir.absolutePath, "/bin/sh")
            val process = ProcessBuilder("/bin/sh", "-l", "-c", chain).start()
            process.outputStream.use { stream ->
                stream.write("pwd\n".toByteArray())
                stream.flush()
            } // stdin EOF ends the exec'd login shell after pwd
            val output = process.inputStream.bufferedReader().readText()
            assertTrue(
                "the chain must terminate",
                process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS),
            )
            // Substring containment (not per-line): the fixture name itself
            // contains a newline, so the correct output spans two lines. The
            // FULL path appearing contiguously is the proof — it can only be
            // printed by a cwd that IS the literal directory (a broken quote
            // would have failed the cd and produced no pwd line at all).
            assertTrue(
                "pwd must print the directory EXACTLY as one literal value, got:\n$output",
                output.contains(dir.absolutePath),
            )
        } finally {
            root.deleteRecursively()
        }
    }

    /**
     * Execution-level injection proof: the value below is an actual injection
     * ATTEMPT. If `guestTerminalChain` ever allowed the directory to become
     * shell syntax, `touch injected-marker` would run and create the marker
     * file — the test fails. With correct quoting the whole string is one
     * directory name, cd succeeds, and nothing extra ever executes.
     */
    @Test
    fun `guestTerminalChain executes - shell metacharacters cannot escape the cd argument`() {
        org.junit.Assume.assumeTrue(
            "POSIX shell required for the execution fixture",
            java.io.File("/bin/sh").exists(),
        )
        val injection = "x'; touch injected-marker ; echo \"pwned\" ; ls | wc -l'"
        val root = java.nio.file.Files.createTempDirectory("p7-terminal-injection").toFile()
        try {
            val dir = java.io.File(root, injection)
            assertTrue("fixture directory must exist", dir.mkdirs())
            assertFalse(
                "precondition: the marker must not pre-exist",
                java.io.File(root, "injected-marker").exists(),
            )
            val chain = guestTerminalChain(dir.absolutePath, "/bin/sh")
            val process = ProcessBuilder("/bin/sh", "-l", "-c", chain).start()
            process.outputStream.use { stream ->
                stream.write("pwd\n".toByteArray())
                stream.flush()
            }
            val output = process.inputStream.bufferedReader().readText()
            assertTrue(
                "the chain must terminate",
                process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS),
            )
            assertTrue(
                "pwd must print the hostile name literally, got:\n$output",
                output.lines().any { it == dir.absolutePath },
            )
            assertFalse(
                "the injected command must NEVER have executed",
                java.io.File(root, "injected-marker").exists(),
            )
        } finally {
            root.deleteRecursively()
        }
    }

    private fun assertNotEquals(expected: String, actual: String) {
        assertFalse("expected not to equal '$expected'", expected == actual)
    }

    // ------------------------------------------------- M7.1 P1 — custom tools

    @Test
    fun `cline is in the registry as a plain single-token launcher`() {
        val cline = CommandAppCatalog.byId("cline")
        assertNotNull("Cline must be a built-in launcher (M7.1 named set)", cline)
        assertEquals(listOf("cline"), cline!!.launchCommand)
        assertEquals("cline", cline.probeName())
        assertTrue(cline.displayName.isNotBlank())
    }

    @Test
    fun `guestCustomCommandChain carries the user line verbatim with the exec fallback`() {
        assertEquals(
            "my-tool --serve; exec /bin/sh -l",
            guestCustomCommandChain("my-tool --serve", "/bin/sh"),
        )
        // user configuration is DATA: no quoting, no rewriting, no parsing
        assertEquals(
            "npm install -g something; exec sh -l",
            guestCustomCommandChain("npm install -g something", "sh"),
        )
    }

    @Test
    fun `guestCustomCommandChain pins the structural contract`() {
        val chain = guestCustomCommandChain("my-tool", "sh")
        // the exact same delivery structure as the registry chain
        assertTrue("must end with the login-shell exec fallback", chain.endsWith("; exec sh -l"))
        // single line only — the upstream hygiene validation forbids newlines
        assertFalse(chain.contains('\n'))
        // structural equality with guestLaunchChain's output shape for a
        // single token proves ONE delivery mechanism, two entry types
        assertEquals(
            guestLaunchChain(listOf("my-tool"), "sh"),
            guestCustomCommandChain("my-tool", "sh"),
        )
    }

    // ------------------------------------- M7.1 P2 — Antigravity replaces Gemini

    /**
     * The curated default launcher set is user-facing product surface: P2
     * removes Gemini CLI from it and seats Antigravity. The command is NOT
     * invented — `agy` is the binary name Google's own installer ships
     * (docs/ANTIGRAVITY-PLATFORM.md §1, antigravity.google/cli/install.sh).
     */
    @Test
    fun `antigravity is a curated default launcher and gemini is gone`() {
        val agy = CommandAppCatalog.byId("agy")
        assertNotNull("Antigravity must be in the curated default launcher set", agy)
        assertEquals("Antigravity", agy!!.displayName)
        assertEquals(listOf("agy"), agy.launchCommand)
        assertEquals("agy", agy.probeName())
        // Gemini CLI left the curated defaults — registry, ids, commands, and
        // display names, with no stale trace anywhere in the catalog.
        assertNull(CommandAppCatalog.byId("gemini"))
        assertTrue(CommandAppCatalog.registry.none { it.id == "gemini" })
        assertTrue(CommandAppCatalog.registry.none { it.displayName.contains("Gemini") })
        assertTrue(CommandAppCatalog.registry.none { it.launchCommand.contains("gemini") })
    }

    @Test
    fun `aider is gone from the curated set with no stale trace`() {
        // M7.1 P2.1: the owner removed Aider from the curated default
        // launcher set — registry, ids, commands, and display names, with
        // no stale trace anywhere in the catalog (the Gemini CLI pattern).
        assertNull(CommandAppCatalog.byId("aider"))
        assertTrue(CommandAppCatalog.registry.none { it.id == "aider" })
        assertTrue(CommandAppCatalog.registry.none { it.displayName.contains("Aider") })
        assertTrue(CommandAppCatalog.registry.none { it.launchCommand.contains("aider") })
        // The rest of the expansion set keeps its registry order untouched.
        assertEquals(
            listOf("hermes", "opencode", "claude", "zcode", "kilo", "cline", "agy", "codex", "qwen"),
            CommandAppCatalog.registry.map { it.id },
        )
    }

    @Test
    fun `antigravity is probe-gated like every other launcher`() {
        // The launcher is not an install claim (upstream ships no musl build
        // today — docs/ANTIGRAVITY-PLATFORM.md): absent from the REAL guest
        // answer means absent from Home, exactly like every other entry.
        val absent = availableCommandApps(mapOf("hermes" to "/usr/bin/hermes"))
        assertFalse(absent.any { it.id == "agy" })
        val present = availableCommandApps(mapOf("agy" to "/usr/local/bin/agy"))
        assertEquals(listOf("agy"), present.map { it.id })
    }
}

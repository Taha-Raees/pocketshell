package app.pocketshell.widget.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M8.3 — the ~/.ssh/config subset parser, exercised with real-world-shaped
 * fixtures: multi-pattern Host lines, defaults inheritance (wildcard block
 * and global options), comments, Include/Match honesty counters, the
 * deprecated Keyword=value form, quoting, and out-of-range ports.
 */
class SshConfigParserTest {

    private fun parse(text: String): SshConfigParse = SshConfigParser.parse(text)
    private fun resolved(text: String): List<SshHostEntry> = parse(text).resolvedHosts()

    // ------------------------------------------------------- Host blocks

    @Test
    fun `a multi pattern Host line becomes one block with all patterns`() {
        val entry = parse(
            "Host alpha beta lab-*\n  HostName 10.0.0.5\n  User deploy\n",
        ).entries.single()
        assertEquals(listOf("alpha", "beta", "lab-*"), entry.patterns)
        assertEquals("10.0.0.5", entry.hostName)
        assertEquals("deploy", entry.user)
        assertTrue("lab-* makes the block a pattern block", entry.wildcard)
        assertEquals("the first concrete pattern names the block", "alpha", entry.displayName)
    }

    @Test
    fun `keywords are case insensitive and indentation is free`() {
        val entry = parse(
            "host a\nHOSTNAME a.example.com\nPoRt 2200\nuser u1\n",
        ).entries.single()
        assertEquals("a.example.com", entry.hostName)
        assertEquals(2200, entry.port)
        assertEquals("u1", entry.user)
    }

    @Test
    fun `comments and blank lines are ignored`() {
        val entries = parse(
            "# top comment\n" +
                "\n" +
                "   # indented comment\n" +
                "Host a\n" +
                "  # option comment\n" +
                "  HostName a.example.com\n" +
                "\n",
        ).entries
        assertEquals(1, entries.size)
        assertEquals("a.example.com", entries.single().hostName)
    }

    @Test
    fun `a Host line with no patterns is skipped honestly`() {
        val parsed = parse("Host\nHostName a.example.com\nHost b\nHostName b.example.com\n")
        assertEquals(listOf("b"), parsed.entries.map { it.displayName })
    }

    // ------------------------------------------------- defaults inheritance

    @Test
    fun `entries inherit from the first wildcard block - first obtained wins`() {
        val hosts = resolved(
            "Host web1\n" +
                "  HostName web1.example.com\n" +
                "Host web2\n" +
                "Host *\n" +
                "  User deploy\n" +
                "  Port 2222\n" +
                "  IdentityFile ~/.ssh/id_work\n" +
                "Host *\n" +
                "  User late\n",
        )
        val web1 = hosts.first { it.displayName == "web1" }
        assertEquals("web1.example.com", web1.hostName)
        assertEquals("deploy", web1.user)
        assertEquals(2222, web1.port)
        assertEquals(listOf("~/.ssh/id_work"), web1.identityFiles)
        val web2 = hosts.first { it.displayName == "web2" }
        assertEquals("the SECOND wildcard block must not override the first", "deploy", web2.user)
        assertEquals(2, hosts.count { it.wildcard })
    }

    @Test
    fun `global options before any Host line are the lowest-priority defaults`() {
        val host = resolved(
            "User globalu\n" +
                "Port 2200\n" +
                "Host a\n" +
                "  HostName a.example.com\n",
        ).single()
        assertEquals("a.example.com", host.hostName)
        assertEquals("globalu", host.user)
        assertEquals(2200, host.port)
    }

    @Test
    fun `an entry without any defaults keeps honest nulls`() {
        val host = resolved("Host a\n  HostName a.example.com\n").single()
        assertEquals("a.example.com", host.hostName)
        assertNull(host.user)
        assertNull(host.port)
        assertTrue(host.identityFiles.isEmpty())
    }

    // --------------------------------------------- Include / Match honesty

    @Test
    fun `Include directives are ignored and counted`() {
        val parsed = parse(
            "Include ~/.ssh/config.d/*\n" +
                "Include ~/.config/ssh/conf\n" +
                "Host a\n" +
                "  HostName a.example.com\n",
        )
        assertEquals(2, parsed.includesIgnored)
        assertEquals(1, parsed.entries.size)
        assertEquals("a.example.com", parsed.entries.single().hostName)
    }

    @Test
    fun `Match blocks are ignored as a region until the next Host line`() {
        val parsed = parse(
            "Host a\n" +
                "  HostName a.example.com\n" +
                "Match exec \"true\"\n" +
                "  User risky\n" +
                "  HostName evil.example.com\n" +
                "Host b\n" +
                "  HostName b.example.com\n",
        )
        assertEquals(1, parsed.matchBlocksIgnored)
        val a = parsed.entries.first { it.displayName == "a" }
        assertNull("a Match block's options must never leak into earlier blocks", a.user)
        val b = parsed.entries.first { it.displayName == "b" }
        assertEquals("b.example.com", b.hostName)
        assertNull(b.user)
    }

    // ------------------------------------------------------- value shapes

    @Test
    fun `the deprecated equals form parses`() {
        val entry = parse("Host=alpha\nHostName=alpha.example.com\nPort=2200\n").entries.single()
        assertEquals("alpha", entry.patterns.single())
        assertEquals("alpha.example.com", entry.hostName)
        assertEquals(2200, entry.port)
    }

    @Test
    fun `quoted values keep spaces`() {
        val entry = parse(
            "Host \"my host\"\nIdentityFile \"~/.ssh/my key\"\n",
        ).entries.single()
        assertEquals("my host", entry.patterns.single())
        assertEquals(listOf("~/.ssh/my key"), entry.identityFiles)
    }

    @Test
    fun `ports out of range or non numeric are ignored not clamped`() {
        assertNull(parse("Host a\nPort 99999\n").entries.single().port)
        assertNull(parse("Host a\nPort 0\n").entries.single().port)
        assertNull(parse("Host a\nPort abc\n").entries.single().port)
        assertEquals(22, parse("Host a\nPort 22\n").entries.single().port)
    }

    @Test
    fun `identity files accumulate across lines`() {
        val entry = parse(
            "Host a\nIdentityFile ~/.ssh/id_one\nIdentityFile ~/.ssh/id_two\n",
        ).entries.single()
        assertEquals(listOf("~/.ssh/id_one", "~/.ssh/id_two"), entry.identityFiles)
    }

    @Test
    fun `wildcard-only blocks are marked and named by their patterns`() {
        val entry = parse("Host *.example.com !secret.example.com\nUser u\n").entries.single()
        assertTrue(entry.wildcard)
        assertEquals(
            "no concrete pattern exists — the whole pattern list names the block",
            "*.example.com !secret.example.com",
            entry.displayName,
        )
        assertEquals("u", entry.user)
    }

    @Test
    fun `two host blocks stay two entries in file order`() {
        val parsed = parse(
            "Host b\nHostName b.example.com\nHost a\nHostName a.example.com\n",
        )
        assertEquals(listOf("b", "a"), parsed.entries.map { it.displayName })
    }

    // ------------------------------------------------------------ matcher

    @Test
    fun `the matcher matches concrete entries on hostname pattern and port`() {
        val entry = resolved(
            "Host web1\nHostName web1.example.com\nPort 2222\nIdentityFile ~/.ssh/k\n",
        ).single()
        assertTrue(
            sshEntryMatchesTarget(
                entry,
                SshArgvTarget(user = "u", host = "web1.example.com", port = 2222, destinationRaw = "u@web1.example.com"),
            ),
        )
        assertTrue(
            "the alias pattern is also a matchable name",
            sshEntryMatchesTarget(
                entry,
                SshArgvTarget(user = null, host = "web1", port = 2222, destinationRaw = "web1"),
            ),
        )
        assertFalse(
            "a default-port client does not match an entry pinned to 2222",
            sshEntryMatchesTarget(
                entry,
                SshArgvTarget(user = "u", host = "web1.example.com", port = null, destinationRaw = "u@web1.example.com"),
            ),
        )
    }

    @Test
    fun `the matcher never matches pattern blocks or unknown hosts`() {
        val wildcard = resolved("Host *\nUser deploy\nPort 2222\n").single()
        assertTrue(wildcard.wildcard)
        assertFalse(
            "pattern blocks are never 'matched' — evaluating wildcards would be a guess",
            sshEntryMatchesTarget(wildcard, SshArgvTarget(user = "u", host = "anything", port = 2222, destinationRaw = "anything")),
        )
        val concrete = resolved("Host web1\nHostName web1.example.com\n").single()
        assertFalse(
            sshEntryMatchesTarget(
                concrete,
                SshArgvTarget(user = null, host = "other.example.com", port = null, destinationRaw = "other.example.com"),
            ),
        )
        assertFalse(
            "a client with no destination host matches nothing",
            sshEntryMatchesTarget(concrete, SshArgvTarget(user = null, host = null, port = null, destinationRaw = null)),
        )
    }
}

package app.pocketshell.widget.ssh

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * M8.3 — known_hosts counting: entries vs comments/blanks, hashed
 * (`|1|…`) entries, and ported (`[host]:2222`) entries. CONTENTS are never
 * surfaced — this layer's whole output is three counts.
 */
class KnownHostsTest {

    @Test
    fun `plain hashed and ported entries are classified`() {
        val stats = KnownHosts.stats(
            """
            host.example.com ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAI comment here
            |1|someroundofsalt|somehashofthehost ssh-rsa AAAAB3NzaC1yc2EAAA
            [gw.example.com]:2222 ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAI
            """.trimIndent(),
        )
        assertEquals(3, stats.entries)
        assertEquals(1, stats.hashed)
        assertEquals(1, stats.ported)
    }

    @Test
    fun `comments and blank lines are not entries`() {
        val stats = KnownHosts.stats(
            "# a commented-out host\n" +
                "\n" +
                "   \n" +
                "host.example.com ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAI\n",
        )
        assertEquals(1, stats.entries)
        assertEquals(0, stats.hashed)
        assertEquals(0, stats.ported)
    }

    @Test
    fun `comma separated host lists are one entry - ported when any part is ported`() {
        val stats = KnownHosts.stats(
            "a.example.com,b.example.com ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAI\n" +
                "[c.example.com]:2223,d.example.com ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAI\n",
        )
        assertEquals(2, stats.entries)
        assertEquals(0, stats.hashed)
        assertEquals(1, stats.ported)
    }

    @Test
    fun `empty and comment-only files count as zero entries`() {
        val empty = KnownHosts.stats("")
        assertEquals(0, empty.entries)
        val onlyComments = KnownHosts.stats("# nothing yet\n\n# still nothing\n")
        assertEquals(0, onlyComments.entries)
    }

    @Test
    fun `a hashed entry is never counted as ported`() {
        val stats = KnownHosts.stats("|1|salt|hash ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAI\n")
        assertEquals(1, stats.entries)
        assertEquals(1, stats.hashed)
        assertEquals(0, stats.ported)
    }
}

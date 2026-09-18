package app.pocketshell.widget.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/**
 * M8.3 — argv classification (the ONE honest connection-state evidence
 * PocketShell has) and the /proc scan over a fixture proc tree.
 */
class SshArgvTest {

    private fun classify(vararg argv: String): SshArgvTarget? = SshArgv.classify(argv.toList())

    // ------------------------------------------------------- not ssh at all

    @Test
    fun `non ssh processes are never classified`() {
        assertNull(classify("sshd", "-D"))
        assertNull(classify("scp", "a", "b"))
        assertNull(classify("sftp", "user@host"))
        assertNull(classify("bash"))
        assertNull(classify())
    }

    @Test
    fun `a full path argv0 is still the ssh basename`() {
        val target = classify("/usr/bin/ssh", "deploy@host1")
        assertEquals("host1", target!!.host)
        assertEquals("deploy", target.user)
    }

    // ------------------------------------------------------- destinations

    @Test
    fun `a plain destination parses user at host`() {
        val target = classify("ssh", "deploy@web1.example.com")
        assertEquals("deploy", target!!.user)
        assertEquals("web1.example.com", target.host)
        assertNull(target.port)
        assertEquals("deploy@web1.example.com", target.destinationRaw)
    }

    @Test
    fun `a destination without user has an honest null user`() {
        val target = classify("ssh", "host1")
        assertNull(target!!.user)
        assertEquals("host1", target.host)
        assertEquals("host1", target.destinationRaw)
    }

    @Test
    fun `an argv with no destination is still an ssh client`() {
        val target = classify("ssh", "-v")
        assertTrue("an ssh client with no target yet must still be listed", target != null)
        assertNull(target!!.host)
        assertNull(target.destinationRaw)
    }

    @Test
    fun `a remote command after the destination is not part of the target`() {
        val target = classify("ssh", "host1", "ls", "-la", "some", "args")
        assertEquals("host1", target!!.host)
        assertEquals("host1", target.destinationRaw)
    }

    // ------------------------------------------------------------- ports

    @Test
    fun `a separate -p value becomes the port`() {
        val target = classify("ssh", "-p", "2222", "host1")
        assertEquals(2222, target!!.port)
        assertEquals("host1", target.host)
    }

    @Test
    fun `a glued -pN becomes the port`() {
        val target = classify("ssh", "-p2222", "host1")
        assertEquals(2222, target!!.port)
        assertEquals("host1", target.host)
    }

    @Test
    fun `an invalid -p value is ignored not clamped`() {
        assertNull(classify("ssh", "-p", "99999", "host1")!!.port)
        assertNull(classify("ssh", "-p", "abc", "host1")!!.port)
        assertNull(classify("ssh", "-pabc", "host1")!!.port)
    }

    @Test
    fun `the bracket port form parses - a bare host_colon_port does not`() {
        val bracket = classify("ssh", "user@[10.0.0.5]:2222")
        assertEquals("10.0.0.5", bracket!!.host)
        assertEquals("user", bracket.user)
        assertEquals(2222, bracket.port)
        val bare = classify("ssh", "user@host1:2222")
        assertEquals("host1:2222 is scp spelling — ssh shows it as the literal destination",
            "host1:2222", bare!!.host)
        assertNull(bare.port)
    }

    // -------------------------------------------------------- option values

    @Test
    fun `-l gives the user but the destination user wins`() {
        assertEquals("bob", classify("ssh", "-l", "bob", "host1")!!.user)
        assertEquals(
            "deploy",
            classify("ssh", "-l", "bob", "deploy@host1")!!.user,
        )
    }

    @Test
    fun `an -i identity file is consumed and never becomes the destination`() {
        val target = classify("ssh", "-i", "id_ed25519", "host1")
        assertEquals("host1", target!!.host)
    }

    @Test
    fun `-o and tunnel options consume their values and never become the destination`() {
        assertEquals(
            "host1",
            classify("ssh", "-o", "StrictHostKeyChecking=no", "host1")!!.host,
        )
        val tunnel = classify("ssh", "-L", "8080:localhost:80", "user@host1")
        assertEquals("host1", tunnel!!.host)
        assertEquals("user", tunnel.user)
    }

    @Test
    fun `valueless flag clusters are skipped`() {
        val target = classify("ssh", "-vvv", "-4", "-t", "host1")
        assertEquals("host1", target!!.host)
    }

    @Test
    fun `an option at the end without its value does not crash or misparse`() {
        val target = classify("ssh", "-p")
        assertNull(target!!.host)
        assertNull(target.port)
    }

    // ------------------------------------------------- the /proc scan itself

    private fun fixtureProc(): java.io.File {
        val root = Files.createTempDirectory("sshprobe").toFile()
        Files.write(
            java.io.File(root, "101").apply { mkdirs() }.resolve("cmdline").toPath(),
            "ssh\u0000deploy@host1".toByteArray(),
        )
        Files.write(
            java.io.File(root, "102").apply { mkdirs() }.resolve("cmdline").toPath(),
            "sshd\u0000-D".toByteArray(),
        )
        Files.write(
            java.io.File(root, "103").apply { mkdirs() }.resolve("cmdline").toPath(),
            "bash".toByteArray(),
        )
        // pid 104: a vanished/zombie-style pid dir without a readable cmdline
        java.io.File(root, "104").mkdirs()
        return root
    }

    @Test
    fun `the probe lists only own-UID ssh clients from the proc fixture`() {
        val probe = SshProcessProbe(procRoot = fixtureProc().path, selfPid = 999)
        val clients = probe.snapshot()
        assertEquals(1, clients.size)
        val client = clients.single()
        assertEquals(101, client.pid)
        assertEquals("ssh", client.processName)
        assertEquals("host1", client.target.host)
        assertEquals("deploy", client.target.user)
        assertEquals("deploy@host1", client.targetDisplay)
    }

    @Test
    fun `the probe excludes self and unreadable pids`() {
        val root = fixtureProc()
        val probe = SshProcessProbe(procRoot = root.path, selfPid = 101)
        assertTrue(probe.snapshot().isEmpty())
    }

    @Test
    fun `an unreadable proc root is an honest empty list`() {
        val missing = Files.createTempDirectory("sshprobe-empty").toFile().resolve("proc")
        val probe = SshProcessProbe(procRoot = missing.path, selfPid = 0)
        assertTrue(probe.snapshot().isEmpty())
    }
}

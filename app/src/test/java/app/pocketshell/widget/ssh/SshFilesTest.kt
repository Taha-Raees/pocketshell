package app.pocketshell.widget.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * M8.3 — SshFiles: the one reader of the guest's ~/.ssh directory. The
 * fixtures are real temp dirs written with the file NAMES the guest would
 * have ("config", "known_hosts"); the tests pin the honest
 * present/absent/race behavior and the guest-home path mapping.
 */
class SshFilesTest {

    private fun emptyProbe() = SshProcessProbe(
        procRoot = Files.createTempDirectory("sshfiles-proc").toFile().resolve("proc").path,
        selfPid = 0,
    )

    @Test
    fun `the guest ssh dir is the bound home under the app data dir`() {
        assertEquals(
            "/data/user/0/app.pocketshell/files/home/.ssh",
            SshFiles.guestSshDir("/data/user/0/app.pocketshell").path,
        )
        assertEquals(
            "/data/user/0/app.pocketshell/files/home/.ssh",
            SshFiles.guestSshDir("/data/user/0/app.pocketshell/").path,
        )
    }

    @Test
    fun `an empty ssh dir is an honest all-zero snapshot - not an error`() {
        val sshDir = Files.createTempDirectory("sshfiles-empty").toFile()
        val snapshot = SshFiles.snapshotIn(sshDir, emptyProbe())
        assertFalse(snapshot.configFound)
        assertTrue(snapshot.hosts.isEmpty())
        assertNull(snapshot.knownHosts)
        assertTrue(snapshot.processes.isEmpty())
    }

    @Test
    fun `a config and known_hosts fixture flows through as resolved facts`() {
        val sshDir = Files.createTempDirectory("sshfiles-full").toFile()
        File(sshDir, "config").writeText(
            "Host web1\n" +
                "  HostName web1.example.com\n" +
                "Include ~/.ssh/config.d/*\n" +
                "Host *\n" +
                "  User deploy\n" +
                "  Port 2222\n",
        )
        File(sshDir, "known_hosts").writeText(
            "host.example.com ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAI\n" +
                "|1|salt|hash ssh-rsa AAAAB3NzaC1yc2EAAA\n" +
                "# commented\n",
        )
        val snapshot = SshFiles.snapshotIn(sshDir, emptyProbe())
        assertTrue(snapshot.configFound)
        assertEquals(2, snapshot.hosts.size)
        val web1 = snapshot.hosts.first { it.displayName == "web1" }
        assertEquals("web1.example.com", web1.hostName)
        assertEquals("defaults resolved from the wildcard block", "deploy", web1.user)
        assertEquals(2222, web1.port)
        assertEquals(1, snapshot.includesIgnored)
        assertEquals(0, snapshot.matchBlocksIgnored)
        assertEquals(2, snapshot.knownHosts!!.entries)
        assertEquals(1, snapshot.knownHosts!!.hashed)
    }

    @Test
    fun `a missing known_hosts stays null while the config still parses`() {
        val sshDir = Files.createTempDirectory("sshfiles-nohosts").toFile()
        File(sshDir, "config").writeText("Host a\nHostName a.example.com\n")
        val snapshot = SshFiles.snapshotIn(sshDir, emptyProbe())
        assertTrue(snapshot.configFound)
        assertEquals(1, snapshot.hosts.size)
        assertNull(snapshot.knownHosts)
    }

    @Test
    fun `running ssh clients from the proc fixture join the snapshot`() {
        val sshDir = Files.createTempDirectory("sshfiles-procclients").toFile()
        val procRoot = Files.createTempDirectory("sshfiles-proc2").toFile().resolve("proc")
        Files.write(
            File(procRoot, "777").apply { mkdirs() }.resolve("cmdline").toPath(),
            "ssh\u0000web1.example.com".toByteArray(),
        )
        val snapshot = SshFiles.snapshotIn(sshDir, SshProcessProbe(procRoot = procRoot.path, selfPid = 0))
        assertEquals(1, snapshot.processes.size)
        assertEquals("web1.example.com", snapshot.processes.single().target.host)
    }
}

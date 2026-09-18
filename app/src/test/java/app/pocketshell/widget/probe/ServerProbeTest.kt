package app.pocketshell.widget.probe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import java.nio.file.Files

/**
 * M8.1 — the Servers widget's probe, verified with REAL sockets: a JVM
 * test can bind 127.0.0.1, so endpoint verification, the cmdline-candidate
 * pipeline, and the attribution rules are exercised against actual
 * listening sockets, not mocks. Attribution's held-connection fd-diff is
 * additionally pinned against synthetic fd snapshots (the accept event
 * itself belongs to the test JVM, whose pid is excluded via selfPid).
 */
class ServerProbeTest {

    // ------------------------------------------------------------ cmdline

    @Test
    fun `explicit port naming is extracted from argv`() {
        assertEquals(
            setOf(8080),
            ServerProbe.extractPortCandidates(listOf("python3", "-m", "http.server", "8080")),
        )
        assertEquals(
            setOf(5173),
            ServerProbe.extractPortCandidates(listOf("node", "server.js", "--port", "5173")),
        )
        assertEquals(
            setOf(3000),
            ServerProbe.extractPortCandidates(listOf("vite", "--port", "3000")),
        )
        assertEquals(
            setOf(3000),
            ServerProbe.extractPortCandidates(listOf("vite", "-p", "3000")),
        )
        assertEquals(
            setOf(8000),
            ServerProbe.extractPortCandidates(listOf("uvicorn", "app:main", "0.0.0.0:8000")),
        )
    }

    @Test
    fun `no port naming - no candidates - never guessed from a process name`() {
        assertEquals(emptySet<Int>(), ServerProbe.extractPortCandidates(listOf("npm", "run", "dev")))
        assertEquals(emptySet<Int>(), ServerProbe.extractPortCandidates(listOf("node", "server.js")))
        assertEquals(emptySet<Int>(), ServerProbe.extractPortCandidates(listOf("python3", "-m", "http.server")))
    }

    @Test
    fun `out-of-range and malformed tokens are not candidates`() {
        assertEquals(emptySet<Int>(), ServerProbe.extractPortCandidates(listOf("x", "99999")))
        assertEquals(emptySet<Int>(), ServerProbe.extractPortCandidates(listOf("x", "0")))
        assertEquals(emptySet<Int>(), ServerProbe.extractPortCandidates(listOf("x", "-1")))
        assertEquals(emptySet<Int>(), ServerProbe.extractPortCandidates(listOf("x", "http.server")))
    }

    @Test
    fun `the dev-port canon is bounded and well-formed`() {
        assertTrue("canon must stay bounded", ServerProbe.DEV_PORT_CANON.size <= 32)
        assertEquals(ServerProbe.DEV_PORT_CANON.size, ServerProbe.DEV_PORT_CANON.distinct().size)
        ServerProbe.DEV_PORT_CANON.forEach { port ->
            assertTrue("canon port in range: $port", port in 1..65535)
        }
    }

    // ------------------------------------------------- endpoint verification

    @Test
    fun `a real listening socket verifies`() {
        ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1")).use { server ->
            val probe = ServerProbe(canon = emptyList())
            assertTrue(probe.verifyEndpoint(server.localPort))
        }
    }

    @Test
    fun `a closed port does not verify`() {
        val probe = ServerProbe(canon = emptyList())
        // port 1 on loopback: nothing listens (verified refused on device)
        assertFalse(probe.verifyEndpoint(1))
    }

    // ------------------------------------------------ the pipeline (fixture)

    /**
     * Fixture proc tree + a REAL listening socket. The fixture pid owns a
     * socket fd (a symlink to the TEST JVM's own listener link) and its
     * cmdline names the port — the pipeline must surface it through
     * verification and the cmdline attribution rule.
     */
    private fun fixtureWithServer(port: Int): File {
        val root = Files.createTempDirectory("serverprobe").toFile()
        val pid = File(root, "proc/4242").apply { mkdirs() }
        Files.write(File(pid, "cmdline").toPath(), "python3\u0000-m\u0000http.server\u0000$port".toByteArray())
        val cwdTarget = File(root, "home/projects/app").apply { mkdirs() }
        Files.createSymbolicLink(File(pid, "cwd").toPath(), cwdTarget.toPath())
        val fdDir = File(pid, "fd").apply { mkdirs() }
        Files.createSymbolicLink(File(fdDir, "3").toPath(), File("socket:[777000]").toPath())
        // a second own-UID process with NO sockets and no port naming
        File(root, "proc/500").apply { mkdirs() }
        Files.write(File(root, "proc/500/cmdline").toPath(), "sh\u0000-c\u0000sleep".toByteArray())
        return root
    }

    @Test
    fun `a named port that really listens is attributed to its process`() {
        ServerSocket(0).use { server ->
            val port = server.localPort
            val root = fixtureWithServer(port)
            val probe = ServerProbe(procRoot = File(root, "proc").path, canon = listOf(port))
            val servers = probe.snapshot()
            assertEquals(1, servers.size)
            val serverInfo = servers.single()
            assertEquals(port, serverInfo.port)
            assertEquals(4242, serverInfo.pid)
            assertEquals("python3", serverInfo.processName)
            assertEquals(ServerInfo.Attribution.CMDLINE_MATCH, serverInfo.attribution)
            assertTrue(serverInfo.cwd?.endsWith("projects/app") == true)
        }
    }

    @Test
    fun `a named port that does NOT listen is never shown`() {
        // port 1 is not listening; the fixture names it anyway
        val root = fixtureWithServer(1)
        val probe = ServerProbe(procRoot = root.resolve("proc").path, canon = listOf(1))
        assertTrue(probe.snapshot().isEmpty())
    }

    @Test
    fun `a canon hit with no own-UID attribution is excluded - not claimed`() {
        // REAL listener owned by the TEST JVM (selfPid excluded in tests):
        // canon discovers the port, but nothing own-UID accepts or names
        // it — a foreign local service. The widget must not claim it.
        ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1")).use { server ->
            val port = server.localPort
            val root = fixtureWithServer(1) // names nothing that listens
            val probe = ServerProbe(procRoot = root.resolve("proc").path, canon = listOf(port))
            assertTrue(probe.snapshot().isEmpty())
        }
    }

    @Test
    fun `idle gate skips the pipeline only while nothing changed`() {
        val root = fixtureWithServer(1)
        val probe = ServerProbe(procRoot = root.resolve("proc").path, canon = emptyList())
        val pidSet = probe.peekPidSet()
        assertEquals(setOf(4242, 500), pidSet)
        assertTrue(probe.shouldFullScan(pidSet))
        probe.snapshot()
        assertFalse("same pid set + no servers → no rescan", probe.shouldFullScan(pidSet))
        assertTrue("changed pid set → full scan", probe.shouldFullScan(pidSet + 900))
    }

    @Test
    fun `self pid is excluded from candidates`() {
        ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1")).use { server ->
            val port = server.localPort
            val root = fixtureWithServer(port)
            // The fixture pid 4242 IS "self" here — everything must vanish.
            val probe = ServerProbe(
                procRoot = root.resolve("proc").path,
                canon = listOf(port),
                selfPid = 4242,
            )
            assertTrue(probe.snapshot().isEmpty())
        }
    }

    @Test
    fun `the table path stays probe-gated - denied here means pipeline path`() {
        // The fixture proc tree has no readable net/tcp → tablePathAvailable
        // must be false, and the endpoint pipeline is the active path.
        val root = fixtureWithServer(1)
        val probe = ServerProbe(procRoot = root.resolve("proc").path, canon = emptyList())
        assertFalse(probe.tablePathAvailable)
    }

    @Test
    fun `the table path takes over when a policy ever allows it`() {
        val root = Files.createTempDirectory("serverprobe-table").toFile()
        val proc = File(root, "proc/4242").apply { mkdirs() }
        Files.write(File(proc, "cmdline").toPath(), "python3\u0000-m\u0000http.server\u00008080".toByteArray())
        // A "readable" net/tcp table: one LISTEN row owned by inode 42,
        // plus the fd link that maps the inode back to the process.
        File(proc, "net").mkdirs()
        Files.write(
            File(proc, "net/tcp").toPath(),
            """
              sl local_address rem_address   st tx_rxqueue tr_tmwhen retrnsmt uid timeout inode
               0: 00000000:1F90 00000000:0000 0A 00000000:0000 00:00000000 00000000  0 0 42
            """.trimIndent().toByteArray(),
        )
        val fdDir = File(proc, "fd").apply { mkdirs() }
        Files.createSymbolicLink(File(fdDir, "3").toPath(), File("socket:[42]").toPath())

        val probe = ServerProbe(procRoot = File(root, "proc").path, canon = emptyList())
        assertTrue(probe.tablePathAvailable)
        val servers = probe.snapshot()
        assertEquals(1, servers.size)
        assertEquals(8080, servers.single().port)
        assertEquals(4242, servers.single().pid)
        assertEquals(ServerInfo.Attribution.TABLE_MATCH, servers.single().attribution)
    }

    @Test
    fun `companion urls are loopback http`() {
        assertEquals("http://127.0.0.1:8080/", ServerProbe.companionUrl(8080))
        assertEquals("http://127.0.0.1:5173/", ServerProbe.companionUrl(5173))
    }
}

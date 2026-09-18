package app.pocketshell.widget.probe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * M8 — the Servers widget's probe: pure table parsing + the full
 * snapshot against a synthetic /proc fixture tree (real symlinks for the
 * socket:[inode] fd links, real files for the tables and cmdlines).
 */
class PortProbeTest {

    // ------------------------------------------------------------ pure parsing

    @Test
    fun `parses LISTEN rows only`() {
        val table = """
            sl local_address rem_address   st tx_rxqueue tr_tmwhen retrnsmt uid timeout inode
             0: 0100007F:0BB8 00000000:0000 0A 00000000:0000 00:00000000 00000000     0        0 12345
             1: 0100007F:1F90 00000000:0000 01 00000000:0000 00:00000000 00000000     0        0 12346
             2: 00000000:1F91 00000000:0000 0A 00000000:0000 00:00000000 00000000     0        0 12347
        """.trimIndent()
        assertEquals(
            listOf(0x0BB8 to 12345L, 0x1F91 to 12347L),
            PortTableParser.parseListeners(table),
        )
    }

    @Test
    fun `parses ipv6 listeners by their port`() {
        val table = """
            sl local_address rem_address   st tx_rxqueue tr_tmwhen retrnsmt uid timeout inode
             0: 00000000000000000000000000000000:1F90 00000000000000000000000000000000:0000 0A 00000000:0000 00:00000000 00000000     0        0 555
        """.trimIndent()
        assertEquals(listOf(0x1F90 to 555L), PortTableParser.parseListeners(table))
    }

    @Test
    fun `malformed rows are skipped - never guessed`() {
        val table = """
            sl local_address rem_address   st tx_rxqueue tr_tmwhen retrnsmt uid timeout inode
             0: garbage 00000000:0000 0A 00000000:0000 00:00000000 00000000     0        0 1
             1: 0100007F:0BB8 00000000:0000 0A short
             2: zz:xx 00000000:0000 0A 00000000:0000 00:00000000 00000000     0        0 notanumber
        """.trimIndent()
        assertTrue(PortTableParser.parseListeners(table).isEmpty())
    }

    @Test
    fun `comm parses to the outermost parens`() {
        assertEquals("node", PortTableParser.parseComm(" 123 (node) S 1 1 0 -1 -1"))
        assertEquals("a)b", PortTableParser.parseComm(" 9 (a)b) S 1 1 0 -1 -1"))
        assertEquals(null, PortTableParser.parseComm("no parens here"))
    }

    // -------------------------------------------------- snapshot vs /proc tree

    private val tcpHeader = "  sl  local_address rem_address   st tx_rxqueue tr tm->when retrnsmt   uid  timeout inode"

    private fun fixture(
        temp: Path,
        listeners: String,
        sockets: Map<Long, Pair<Int, String>>, // inode -> (pid, argv0 or null)
    ): PortProbe {
        val net = Files.createDirectories(temp.resolve("net"))
        Files.write(net.resolve("tcp"), "$tcpHeader\n$listeners".toByteArray())
        val proc = Files.createDirectories(temp.resolve("proc"))
        sockets.forEach { (inode, owner) ->
            val (pid, argv0) = owner
            val dir = Files.createDirectories(proc.resolve(pid.toString()).resolve("fd"))
            Files.createSymbolicLink(dir.resolve("fd$inode"), java.io.File("socket:[$inode]").toPath())
            val cmd = if (argv0 != null) "$argv0\u0000--flag\u0000" else ""
            Files.write(proc.resolve(pid.toString()).resolve("cmdline"), cmd.toByteArray())
            if (argv0 == null) {
                Files.write(
                    proc.resolve(pid.toString()).resolve("stat"),
                    " $pid (kworker) S 1".toByteArray(),
                )
            }
        }
        return PortProbe(netDir = net.toString(), procRoot = proc.toString())
    }

    @Test
    fun `snapshot resolves owners through fd symlinks`() {
        val temp = Files.createTempDirectory("portprobe")
        val probe = fixture(
            temp,
            listOf(
                " 0: 0100007F:0BB8 00000000:0000 0A 00000000:0000 00:00000000 00000000 0 0 100",
                " 1: 00000000:1466 00000000:0000 0A 00000000:0000 00:00000000 00000000 0 0 101",
            ).joinToString("\n"),
            mapOf(100L to (4242 to "node"), 101L to (4243 to "/usr/local/bin/python3")),
        )
        val snap = probe.snapshot()
        assertTrue(snap.readable)
        assertEquals(2, snap.listeners.size)
        assertEquals(3000, snap.listeners[0].port)
        assertEquals("node", snap.listeners[0].processName)
        assertEquals(4242, snap.listeners[0].pid)
        // argv[0]'s basename is the name, even with a full path.
        assertEquals(5222, snap.listeners[1].port)
        assertEquals("python3", snap.listeners[1].processName)
    }

    @Test
    fun `sockets without an own-uid owner are not claimed as servers`() {
        val temp = Files.createTempDirectory("portprobe")
        val probe = fixture(
            temp,
            " 0: 0100007F:0BB8 00000000:0000 0A 00000000:0000 00:00000000 00000000 0 0 777",
            emptyMap(), // no visible process owns inode 777 (foreign uid)
        )
        val snap = probe.snapshot()
        assertTrue(snap.readable)
        assertTrue(snap.listeners.isEmpty())
    }

    @Test
    fun `duplicate ports collapse to one row`() {
        val temp = Files.createTempDirectory("portprobe")
        val probe = fixture(
            temp,
            listOf(
                " 0: 0100007F:0BB8 00000000:0000 0A 00000000:0000 00:00000000 00000000 0 0 100",
                " 1: 00000000000000000000000001000000:0BB8 00:0000 0A 00:00 00:00 00 0 0 101",
            ).joinToString("\n"),
            mapOf(100L to (10 to "vite"), 101L to (11 to "vite")),
        )
        assertEquals(1, probe.snapshot().listeners.size)
    }

    @Test
    fun `the owner scan is reused while the listener inode set is unchanged`() {
        val temp = Files.createTempDirectory("portprobe")
        val probe = fixture(
            temp,
            " 0: 0100007F:0BB8 00000000:0000 0A 00000000:0000 00:00000000 00000000 0 0 100",
            mapOf(100L to (10 to "node")),
        )
        assertTrue(probe.snapshot().listeners.isNotEmpty())
        // The process disappears (finished server); the cached owners keep
        // the previous snapshot consistent until the TABLE changes — the
        // parse is the cheap heartbeat, the fd scan is event-driven.
        val procDir = temp.resolve("proc").resolve("10")
        procDir.toFile().deleteRecursively()
        assertTrue(probe.snapshot().listeners.isNotEmpty())
    }

    @Test
    fun `an unreadable table degrades to the honest unreadable state`() {
        val temp = Files.createTempDirectory("portprobe")
        val probe = PortProbe(netDir = temp.resolve("absent").toString(), procRoot = temp.toString())
        val snap = probe.snapshot()
        assertFalse(snap.readable)
        assertTrue(snap.listeners.isEmpty())
        assertFalse(probe.isReadable())
    }

    @Test
    fun `an empty table is readable and empty - not an error`() {
        val temp = Files.createTempDirectory("portprobe")
        val net = Files.createDirectories(temp.resolve("net"))
        Files.write(net.resolve("tcp"), "$tcpHeader\n".toByteArray())
        val probe = PortProbe(netDir = net.toString(), procRoot = temp.toString())
        val snap = probe.snapshot()
        assertTrue(snap.readable)
        assertTrue(snap.listeners.isEmpty())
    }
}

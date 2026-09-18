package app.pocketshell.widget.probe

import java.io.File
import java.nio.file.Files

/**
 * M8 — listening-port probe for the Servers widget.
 *
 * The app and the Linux guest share one UID, so the app-side procfs view
 * IS the guest's own-UID world (hidepid=2, docs/PROCFS-CONTRACT.md §1):
 * `/proc/net/tcp`+`tcp6` LISTEN rows give (port, socket-inode), and a
 * `/proc/<pid>/fd` readlink scan resolves inodes to owning processes.
 * NO proot spawn, no PTY, no guest command — the kernel's own tables are
 * the source, read directly from the app's UID.
 *
 * Cost shape (lab-measured, docs/M8-WIDGET-RESEARCH.md §1): the table parse
 * is the cheap heartbeat (~2 ms); the fd scan (~30-90 ms on the lab host)
 * runs ONLY when the listener inode set changed — a steady server fleet
 * costs parse-only ticks.
 *
 * `/proc/net` readability on a given kernel is probed, never assumed:
 * modern Android scopes it per-UID (exactly the view needed), but an
 * unreadable or empty table degrades to the honest Unreadable state —
 * the widget never invents data (device gate: TESTING.md §64).
 */

/** One listening port with its resolved owner (null when unresolvable). */
data class ListeningSocket(
    val port: Int,
    val ipVersion: Int,
    val inode: Long,
    val pid: Int?,
    val processName: String?,
)

/** Pure parse of one /proc/net/tcp{,6} BODY (header included, ignored). */
object PortTableParser {

    /** (port, inode) pairs of LISTEN rows (st == "0A"), table order. */
    fun parseListeners(tableText: String): List<Pair<Int, Long>> {
        val out = ArrayList<Pair<Int, Long>>()
        for (line in tableText.lineSequence()) {
            val columns = line.trim().split(WHITESPACE)
            // sl local rem st … inode(idx 9): proc_net_tcp(5). Fewer columns
            // than the header promises is a malformed/foreign line — skip.
            if (columns.size < 10 || columns[3] != "0A") continue
            val local = columns[1]
            val colon = local.lastIndexOf(':')
            if (colon <= 0) continue
            val port = local.substring(colon + 1).toIntOrNull(16) ?: continue
            val inode = columns[9].toLongOrNull() ?: continue
            out.add(port to inode)
        }
        return out
    }

    /** comm from /proc/<pid>/stat — everything inside the outermost parens. */
    fun parseComm(statText: String): String? {
        val open = statText.indexOf('(')
        val close = statText.lastIndexOf(')')
        if (open < 0 || close <= open) return null
        return statText.substring(open + 1, close)
    }

    private val WHITESPACE = Regex("""\s+""")
}

/**
 * The stateful snapshotter: parse-driven heartbeat, fd-scan only on change.
 * UI-domain cache (held by the widget) — it owns no lifecycle truth.
 */
class PortProbe(
    private val netDir: String = "/proc/net",
    private val procRoot: String = "/proc",
) {

    data class Snapshot(
        /** false: the kernel denied the tables — the widget says so. */
        val readable: Boolean,
        /** DISTINCT listening ports (v4/v6 duplicates collapsed), port order. */
        val listeners: List<ListeningSocket>,
    )

    private var lastInodes: Set<Long> = emptySet()
    private var lastOwners: Map<Long, Owner> = emptyMap()

    private data class Owner(val pid: Int, val name: String?)

    fun isReadable(): Boolean = try {
        File(netDir, "tcp").let { it.exists() && readHead(it) != null }
    } catch (_: Exception) {
        false
    }

    fun snapshot(): Snapshot {
        val tcp = try { File(netDir, "tcp").readText() } catch (_: Exception) {
            return Snapshot(readable = false, listeners = emptyList())
        }
        val tcp6 = try { File(netDir, "tcp6").readText() } catch (_: Exception) { "" }
        val raw = buildList {
            PortTableParser.parseListeners(tcp).forEach { (port, inode) -> add(Triple(port, 4, inode)) }
            PortTableParser.parseListeners(tcp6).forEach { (port, inode) -> add(Triple(port, 6, inode)) }
        }
        if (raw.isEmpty()) return Snapshot(readable = true, listeners = emptyList())

        val inodes = raw.map { it.third }.toSet()
        if (inodes != lastInodes) {
            lastOwners = scanOwners(inodes)
            lastInodes = inodes
        }

        // One row per DISTINCT port (a v4+v6 twin is one server). Sockets
        // whose owner does not resolve are OTHER-UID sockets — not the
        // user's servers, and never claimed as such: the widget shows the
        // own-UID world only.
        val listeners = raw
            .filter { lastOwners.containsKey(it.third) }
            .sortedBy { it.first }
            .distinctBy { it.first }
            .map { (port, version, inode) ->
                val owner = lastOwners.getValue(inode)
                ListeningSocket(
                    port = port,
                    ipVersion = version,
                    inode = inode,
                    pid = owner.pid,
                    processName = owner.name,
                )
            }
        return Snapshot(readable = true, listeners = listeners)
    }

    /** inode → (pid, name) for the OWN-UID processes this UID may inspect. */
    private fun scanOwners(inodes: Set<Long>): Map<Long, Owner> {
        if (inodes.isEmpty()) return emptyMap()
        val owners = HashMap<Long, Owner>()
        val pidDirs = File(procRoot).listFiles() ?: return owners
        for (dir in pidDirs) {
            val pid = dir.name.toIntOrNull() ?: continue
            val fdDir = File(dir, "fd")
            val fds = try { fdDir.list() } catch (_: Exception) { continue } ?: continue
            var name: String? = null
            for (fd in fds) {
                val target = try {
                    Files.readSymbolicLink(File(fdDir, fd).toPath()).toString()
                } catch (_: Exception) {
                    continue // EACCES (foreign uid) or vanished mid-scan
                }
                if (!target.startsWith("socket:[")) continue
                val inode = target.substring(8, target.length - 1).toLongOrNull() ?: continue
                if (!inodes.contains(inode) || owners.containsKey(inode)) continue
                if (name == null) name = processName(pid) ?: continue
                owners[inode] = Owner(pid, name)
            }
        }
        return owners
    }

    /** argv[0] basename; kernel comm as the fallback; null when unresolvable. */
    private fun processName(pid: Int): String? {
        val argv = try { File("$procRoot/$pid/cmdline").readBytes() } catch (_: Exception) { null }
        if (argv != null && argv.isNotEmpty()) {
            val text = String(argv, Charsets.UTF_8)
            val first = text.substringBefore('\u0000')
            if (first.isNotEmpty()) {
                val slash = first.lastIndexOf('/')
                return if (slash >= 0) first.substring(slash + 1) else first
            }
        }
        val stat = try { File("$procRoot/$pid/stat").readText() } catch (_: Exception) { null }
        return stat?.let { PortTableParser.parseComm(it) }
    }

    private fun readHead(file: File): String? = try {
        file.useLines { it.firstOrNull() }
    } catch (_: Exception) {
        null
    }
}

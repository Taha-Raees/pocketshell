package app.pocketshell.widget.probe

import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.file.Files

/**
 * M8.1 — the Servers widget's probe, redesigned from the device-gate
 * evidence (docs/TESTING.md §64/§65):
 *
 * - `/proc/net` AND `/proc/<pid>/net` are SELinux-denied to the app
 *   domain (per-pid tables are the SAME inode as the global one —
 *   device-verified) — so the kernel table path exists only as a
 *   probed-for future capability ([tablePathAvailable], probe-first/
 *   real-wins pattern).
 * - What IS readable as the app's own UID (device-verified): numeric pid
 *   dirs, `cmdline`, `cwd` readlink, `stat`, and the `fd` socket links.
 * - Guest and app share ONE network namespace (proot creates none), so
 *   a guest listener on 127.0.0.1:P is connectable by the app
 *   (device-verified): a successful connect is KERNEL-FACT evidence
 *   that something listens on that port.
 *
 * The pipeline (arbitrary servers, no per-agent integration):
 *
 *   1. DISCOVER candidates:
 *        own-UID cmdlines that explicitly name a port (all-digits token,
 *        `-p N`, `--port N`, `:N`) ∪ a bounded dev-port canon (≤32 ports).
 *   2. VERIFY each candidate: TCP connect to 127.0.0.1:P. Refused → the
 *      port is not listening; dropped. No server is EVER shown without a
 *      verified endpoint.
 *   3. ATTRIBUTE the accepting process: hold one connection open per
 *      verified port and diff candidate pids' `fd` socket links — the pid
 *      that GAINS a `socket:[inode]` while we hold the connection is the
 *      acceptor (kernel fact; lab-proven for threaded and single-accept
 *      servers). Fallback rule: exactly one socket-owning pid whose
 *      cmdline names the port (and no conflicting acceptor evidence).
 *   4. Canon-only hits that attribute to NOTHING own-UID are EXCLUDED —
 *      the widget is a Linux-server surface, not a device-wide port
 *      scanner; foreign (other-UID) local services are never claimed.
 *
 * Idle cost: the caller may skip a full scan while the numeric pid set is
 * unchanged AND the previous snapshot found nothing ([shouldFullScan]).
 */

/** One verified dev server (or an own-UID endpoint) on the shared loopback. */
data class ServerInfo(
    val port: Int,
    val pid: Int?,
    val processName: String?,
    val cwd: String?,
    /** How the owning process was attributed — kernel-fact-grounded rules. */
    val attribution: Attribution,
) {
    enum class Attribution {
        /** The pid gained an accepted socket while we held a connection. */
        ACCEPTOR_MATCH,
        /** Exactly one socket-owning own-UID pid names this port in argv. */
        CMDLINE_MATCH,
        /** The kernel net tables were readable: direct inode ownership. */
        TABLE_MATCH,
    }

    /** Display name: argv[0] basename, or the honest fallback. */
    val displayName: String get() = processName ?: "Local service"
}

class ServerProbe(
    private val procRoot: String = "/proc",
    private val loopbackHost: String = "127.0.0.1",
    private val connectTimeoutMs: Int = CONNECT_TIMEOUT_MS,
    private val canon: List<Int> = DEV_PORT_CANON,
    private val selfPid: Int = currentProcessPid(),
) {

    // ---------------- layer 0: the kernel-table path, probed once ---------

    /**
     * True iff SOME own-UID pid's `net/tcp` was readable at first probe.
     * On current Android this is false (device-verified); if a future
     * policy un-does the denial, the cheap table path takes over via
     * [PortTableParser] — probe-first, real-wins.
     */
    val tablePathAvailable: Boolean by lazy { probeTablePath() }

    /** The net dir whose table proved readable (probed once). */
    private var tableNetDir: File? = null

    private fun probeTablePath(): Boolean {
        val dirs = File(procRoot).listFiles() ?: return false
        for (dir in dirs) {
            if (dir.name.toIntOrNull() == null) continue
            try {
                File(dir, "net/tcp").reader().use { it.read() }
                tableNetDir = File(dir, "net")
                return true
            } catch (_: Exception) {
                // denied or vanished: try the next pid (cheap; probed once)
            }
        }
        return false
    }

    // ---------------- cached state for the caller's tick -------------------

    private var lastPidSet: Set<Int>? = null
    private var lastSnapshotHadServers = false

    /** Whether the last full snapshot could see any own-UID process at all. */
    var lastScanSawProcesses: Boolean = false
        private set

    /** Cheap gate input: the numeric pid set (one /proc readdir). */
    internal fun peekPidSet(): Set<Int> = readPidSet()

    /**
     * The idle gate: while the numeric pid set is unchanged AND the last
     * full snapshot found no servers, a tick needs no /proc work at all.
     * A changed pid set (session, agent, server start/exit) triggers the
     * full pipeline; a snapshot WITH servers always re-verifies.
     */
    fun shouldFullScan(currentPidSet: Set<Int>): Boolean =
        currentPidSet != lastPidSet || lastSnapshotHadServers

    // ---------------- the pipeline ----------------------------------------

    /** Full discovery → verification → attribution pass. */
    fun snapshot(): List<ServerInfo> {
        val pidSet = readPidSet()
        lastPidSet = pidSet
        lastScanSawProcesses = pidSet.isNotEmpty()

        // Probe-first, real-wins: if a policy ever exposes the kernel net
        // tables, the cheap authoritative path takes over.
        if (tablePathAvailable) return tableSnapshot()

        // 1. own-UID process facts
        val procs = ArrayList<ProcSnapshot>(pidSet.size)
        for (pid in pidSet) {
            if (pid == selfPid) continue
            val argv = readArgv(pid) ?: continue // foreign-UID / vanished
            val dir = File(procRoot, pid.toString())
            val inodes = readSocketInodes(dir)
            procs.add(
                ProcSnapshot(
                    pid = pid,
                    name = argv.firstOrNull()?.substringAfterLast('/')?.ifEmpty { null },
                    cmdlinePorts = extractPortCandidates(argv),
                    socketInodes = inodes,
                ),
            )
        }

        // 2. port candidates: explicit cmdline names ∪ bounded canon
        val cmdlineNamed = HashMap<Int, MutableList<ProcSnapshot>>()
        procs.forEach { proc ->
            proc.cmdlinePorts.forEach { port ->
                cmdlineNamed.getOrPut(port) { mutableListOf() }.add(proc)
            }
        }
        val candidates = LinkedHashSet<Int>()
        candidates.addAll(cmdlineNamed.keys)
        candidates.addAll(canon.filter { it in 1..65535 })

        // 3. VERIFY: a successful TCP connect is kernel-fact "listening"
        val listening = candidates.filter { verifyEndpoint(it) }
        if (listening.isEmpty()) {
            lastSnapshotHadServers = false
            return emptyList()
        }

        // 4+5. ATTRIBUTE per verified port and assemble with the honest
        // attribution rules. Canon-only ports that attribute to NOTHING
        // own-UID are EXCLUDED (foreign local service).
        val servers = ArrayList<ServerInfo>()
        for (port in listening) {
            attribute(port, procs, cmdlineNamed.containsKey(port))?.let { servers.add(it) }
        }
        val result = servers.sortedBy { it.port }
        lastSnapshotHadServers = result.isNotEmpty()
        return result
    }

    /**
     * Attribute one verified port to its owning process, or null when the
     * evidence does not support a display (canon-only hits end here).
     *
     * Primary rule — ACCEPTOR_MATCH: hold a real connection open to the
     * port; among own-UID pids that already owned sockets (a listener
     * always does), the pid whose fd set GAINS a socket inode while we
     * hold the connection is the acceptor (kernel fact; lab-proven for
     * threaded and single-accept servers). The `before` set is re-read
     * immediately before the connect to keep the race window ~SETTLE_MS.
     *
     * Fallback rule — CMDLINE_MATCH: no unique acceptor, but EXACTLY ONE
     * socket-owning own-UID pid names this port in argv.
     *
     * Ambiguous evidence → an owner-less [ServerInfo] when the port came
     * from an own-UID cmdline, never for canon-only ports.
     */
    private fun attribute(
        port: Int,
        procs: List<ProcSnapshot>,
        namedByCmdline: Boolean,
    ): ServerInfo? {
        val candidates = procs.filter { it.pid != selfPid && it.socketInodes.isNotEmpty() }
        val before = candidates.associate { it.pid to readSocketInodes(File(procRoot, it.pid.toString())) }
        val held = HeldConnection(loopbackHost, port, connectTimeoutMs)
        val acceptor: ProcSnapshot? = try {
            Thread.sleep(SETTLE_MS)
            candidates.map { proc ->
                val gained = try {
                    readSocketInodes(File(procRoot, proc.pid.toString()))
                } catch (_: Exception) {
                    emptySet()
                } - (before[proc.pid] ?: emptySet())
                proc to gained
            }.firstOrNull { it.second.isNotEmpty() }?.first
        } finally {
            held.close()
        }
        if (acceptor != null) {
            return ServerInfo(
                port = port,
                pid = acceptor.pid,
                processName = acceptor.name,
                cwd = readCwd(acceptor.pid),
                attribution = ServerInfo.Attribution.ACCEPTOR_MATCH,
            )
        }
        val named = procs.filter {
            it.pid != selfPid && port in it.cmdlinePorts && it.socketInodes.isNotEmpty()
        }
        return when {
            named.size == 1 -> ServerInfo(
                port = port,
                pid = named.single().pid,
                processName = named.single().name,
                cwd = readCwd(named.single().pid),
                attribution = ServerInfo.Attribution.CMDLINE_MATCH,
            )
            namedByCmdline -> ServerInfo(
                port = port, pid = null, processName = null, cwd = null,
                attribution = ServerInfo.Attribution.CMDLINE_MATCH,
            )
            else -> null
        }
    }

    /** The per-process facts the pipeline needs, captured once per scan. */
    private data class ProcSnapshot(
        val pid: Int,
        val name: String?,
        val cmdlinePorts: Set<Int>,
        val socketInodes: Set<String>,
    )

    // ---------------- primitives ------------------------------------------

    private fun readPidSet(): Set<Int> {
        val out = File(procRoot).list { _, name -> name.all { it in '0'..'9' } } ?: return emptySet()
        return out.mapNotNull { it.toIntOrNull() }.toSet()
    }

    /** argv or null when unreadable (foreign-UID / zombie / vanished). */
    private fun readArgv(pid: Int): List<String>? {
        val bytes = try {
            File("$procRoot/$pid/cmdline").readBytes()
        } catch (_: Exception) {
            return null
        }
        if (bytes.isEmpty()) return null
        val text = String(bytes, Charsets.UTF_8)
        val parts = text.split('\u0000').filter { it.isNotEmpty() }
        return parts.ifEmpty { null }
    }

    private fun readSocketInodes(dir: File): Set<String> {
        val fdDir = File(dir, "fd")
        val fds = try {
            fdDir.list()
        } catch (_: Exception) {
            return emptySet()
        } ?: return emptySet()
        val out = HashSet<String>()
        for (fd in fds) {
            val target = try {
                Files.readSymbolicLink(File(fdDir, fd).toPath()).toString()
            } catch (_: Exception) {
                continue
            }
            if (target.startsWith("socket:[")) out.add(target)
        }
        return out
    }

    private fun readCwd(pid: Int): String? = try {
        val target = File("$procRoot/$pid/cwd").canonicalPath
        if (target == "$procRoot/$pid/cwd") null else target
    } catch (_: Exception) {
        null
    }

    /**
     * The authoritative path, taken ONLY when the kernel tables proved
     * readable ([tablePathAvailable]) — LISTEN rows from the shared netns
     * tables, owners from the fd scan (PortProbe's proven mechanism).
     */
    private fun tableSnapshot(): List<ServerInfo> {
        val netDir = tableNetDir ?: return emptyList()
        val probe = PortProbe(netDir = netDir.path, procRoot = procRoot)
        val snapshot = probe.snapshot()
        if (!snapshot.readable) return emptyList()
        return snapshot.listeners.map { listener ->
            ServerInfo(
                port = listener.port,
                pid = listener.pid,
                processName = listener.processName,
                cwd = listener.pid?.let { readCwd(it) },
                attribution = ServerInfo.Attribution.TABLE_MATCH,
            )
        }
    }

    /** Kernel-fact endpoint check: a real TCP connect on the shared loopback. */
    internal fun verifyEndpoint(port: Int): Boolean {
        if (port !in 1..65535) return false
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(loopbackHost, port), connectTimeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    /** An open client connection held for the attribution window. */
    private class HeldConnection(host: String, val port: Int, timeoutMs: Int) {
        private val socket: Socket? = try {
            Socket().apply {
                connect(InetSocketAddress(host, port), timeoutMs)
            }
        } catch (_: Exception) {
            null // verification raced: the listener vanished mid-pass
        }

        fun close() {
            try {
                socket?.close()
            } catch (_: Exception) {
            }
        }
    }

    companion object {

        /**
         * Explicit port naming in argv — CANDIDATES only (every candidate
         * is endpoint-verified before it can ever be shown).
         */
        fun extractPortCandidates(argv: List<String>): Set<Int> {
            val out = HashSet<Int>()
            var prevWantsPort = false
            for (token in argv) {
                when {
                    token == "--port" || token == "-p" -> prevWantsPort = true
                    prevWantsPort && token.toIntOrNull()?.let { it in 1..65535 } == true -> {
                        out.add(token.toInt())
                        prevWantsPort = false
                    }
                    prevWantsPort -> prevWantsPort = false
                    token.toIntOrNull()?.let { it in 1..65535 } == true -> out.add(token.toInt())
                    token.startsWith(":") &&
                        token.drop(1).toIntOrNull()?.let { it in 1..65535 } == true ->
                        out.add(token.drop(1).toInt())
                    // host:port forms (uvicorn 0.0.0.0:8000, ssh -L …:8080)
                    token.contains(':') &&
                        token.substringAfterLast(':').toIntOrNull()?.let { it in 1..65535 } == true ->
                        out.add(token.substringAfterLast(':').toInt())
                }
            }
            return out
        }

        /**
         * The bounded dev-port canon: the ports development tools actually
         * use by default. BOUNDED by contract (≤32) — this is not a port
         * scan; canon hits also require own-UID attribution to be shown.
         */
        val DEV_PORT_CANON: List<Int> = listOf(
            3000, 3001, 3002, 3333, // react / node / next-ish
            4000, 4200,             // phoenix-ish / angular
            5000, 5001,             // flask
            5173, 5174,             // vite
            8000, 8001, 8080, 8081, 8082, 8083, 8888, // python/jetty/jupyter
            9000, 9001, 9090,       // php/php-fpm/prometheus-ish
            9229,                   // node --inspect
            19000, 19001, 19006,    // metro/expo
        )

        const val CONNECT_TIMEOUT_MS = 500
        const val SETTLE_MS = 120L

        /** The Companion URL for a verified local endpoint. */
        fun companionUrl(port: Int): String = "http://127.0.0.1:$port/"
    }
}

/**
 * The app's own pid, excluded from candidate scans (our verification
 * connects would otherwise make the app look like an acceptor). On the
 * JVM test runner android.os.Process.myPid() degrades to 0 under the
 * module's returnDefaultValues harness — pid 0 never appears in /proc.
 */
internal fun currentProcessPid(): Int = try {
    android.os.Process.myPid()
} catch (_: Throwable) {
    0
}

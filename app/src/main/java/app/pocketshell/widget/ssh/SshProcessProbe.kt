package app.pocketshell.widget.ssh

import app.pocketshell.widget.probe.currentProcessPid
import java.io.File

/**
 * M8.3 — the SSH application's connection-state evidence: outbound `ssh`
 * CLIENT processes, found the only honest way PocketShell can see them.
 *
 * PocketShell runs no SSH daemon and owns no connection manager — there is
 * no session API to query. What IS device-proven (the M8.1 probe layer,
 * HostProcfsReader): the guest and the app share one UID, hidepid=2 shows
 * own-UID processes, and /proc/<pid>/cmdline is readable app-side. A
 * process whose argv[0] basename is `ssh` is REAL evidence of an active
 * outbound ssh client; its argv is shown AS-IS (destination token, -p
 * port) — never enriched with guesses.
 *
 * HONESTY LIMIT (stated in the UI too): argv says what the client was
 * ASKED to connect to, not what state the connection is in — a client at
 * a password prompt, a dead route, and a healthy session all look
 * identical here. The card therefore says "ssh client process", never
 * "connected".
 */

/** What an ssh client's argv literally says about its destination. */
data class SshArgvTarget(
    val user: String?,
    val host: String?,
    /** Explicit port from -p / -pN / the [host]:port bracket form; null = ssh's default 22. */
    val port: Int?,
    /** The raw destination token as typed (null when argv carries no destination). */
    val destinationRaw: String?,
)

data class SshClientProcess(
    val pid: Int,
    val processName: String,
    val target: SshArgvTarget,
) {
    /** The display line: what the argv says, or the honest nothing. */
    val targetDisplay: String get() = target.destinationRaw ?: "(target not in argv)"
}

class SshProcessProbe(
    private val procRoot: String = "/proc",
    private val selfPid: Int = currentProcessPid(),
) {

    /**
     * All own-UID ssh client processes, pid order. Foreign-UID dirs and
     * vanished pids yield unreadable cmdlines and are skipped (data, not
     * errors — the established /proc reading discipline).
     */
    fun snapshot(): List<SshClientProcess> {
        val dirs = File(procRoot).list { _, name -> name.all { it in '0'..'9' } }
            ?: return emptyList()
        val out = ArrayList<SshClientProcess>()
        for (name in dirs) {
            val pid = name.toIntOrNull() ?: continue
            if (pid == selfPid) continue
            val argv = readArgv(pid) ?: continue
            val target = SshArgv.classify(argv) ?: continue // not an ssh client
            out += SshClientProcess(
                pid = pid,
                processName = argv.firstOrNull()?.substringAfterLast('/')?.ifEmpty { "ssh" } ?: "ssh",
                target = target,
            )
        }
        return out.sortedBy { it.pid }
    }

    private fun readArgv(pid: Int): List<String>? {
        val bytes = try {
            File("$procRoot/$pid/cmdline").readBytes()
        } catch (_: Exception) {
            return null
        }
        if (bytes.isEmpty()) return null
        val text = String(bytes, Charsets.UTF_8)
        return text.split('\u0000').filter { it.isNotEmpty() }.ifEmpty { null }
    }
}

/** Pure argv classification (JVM-tested): is this argv an ssh client, and what does it name? */
object SshArgv {

    /**
     * ssh(1) options that consume a separate value. Deliberately the value-
     * consuming set only; everything else (-v, -4, -t clusters, long
     * options) is skipped without value assumptions. Options' VALUES are
     * consumed and never parsed into the target (an identity file, a
     * config path, a tunnel spec -L/-J/-W is not the destination) except
     * -p (port) and -l (login user), which ARE destination facts.
     */
    private val VALUE_OPTIONS = "pilFbcDeEIJLmOoQRSWBw".toSet()

    /**
     * Null when argv[0]'s basename is not exactly `ssh` (sshd, scp, sftp
     * and dropbear variants are different binaries and never claimed).
     */
    internal fun classify(argv: List<String>): SshArgvTarget? {
        val argv0 = argv.firstOrNull()?.substringAfterLast('/') ?: return null
        if (argv0 != "ssh") return null

        var userFromOption: String? = null
        var portFromOption: Int? = null
        var destination: String? = null
        var index = 1
        while (index < argv.size && destination == null) {
            val token = argv[index]
            when {
                token == "--" || token.startsWith("--") -> Unit
                token.length >= 2 && token[0] == '-' -> {
                    val flags = token.substring(1)
                    if (flags[0] in VALUE_OPTIONS) {
                        // -p 2222 (separate) or -p2222 / -ikey (glued): both
                        // are argv facts; read exactly what is there.
                        val value = if (flags.length == 1) {
                            argv.getOrNull(index + 1).also { if (it != null) index += 1 }
                        } else {
                            flags.substring(1)
                        }
                        when (flags[0]) {
                            'p' -> portFromOption = value?.toIntOrNull()?.takeIf { it in 1..65535 }
                            'l' -> if (!value.isNullOrEmpty()) userFromOption = value
                        }
                    }
                    // else: a valueless flag cluster (-vvv, -4) — nothing to skip.
                }
                else -> destination = token // first positional; everything after is the remote command
            }
            index += 1
        }

        var destUser: String? = null
        var host: String? = null
        var portFromDestination: Int? = null
        if (destination != null) {
            val at = destination.indexOf('@')
            if (at > 0) {
                destUser = destination.substring(0, at)
                host = destination.substring(at + 1)
            } else {
                // No '@', or a bare "@host": the user part is honestly absent.
                host = if (at == 0) destination.substring(1) else destination
            }
            // Bracket-port form "[host]:2222" (ssh's documented non-default-
            // port destination spelling). A bare host:port is NOT parsed —
            // that is scp/sftp spelling, and showing it as a port would be
            // invention.
            if (host != null && host.startsWith("[")) {
                val close = host.indexOf(']')
                if (close > 1) {
                    val inner = host.substring(1, close)
                    val tail = host.substring(close + 1)
                    if (tail.startsWith(":")) {
                        tail.drop(1).toIntOrNull()?.takeIf { it in 1..65535 }?.let { portFromDestination = it }
                        host = inner
                    } else {
                        host = "$inner$tail" // pure [ipv6] literal: brackets stripped, port absent
                    }
                }
            }
            if (host.isNullOrEmpty()) host = null
        }

        return SshArgvTarget(
            user = destUser ?: userFromOption, // the destination's user wins, as ssh does
            host = host,
            port = portFromDestination ?: portFromOption,
            destinationRaw = destination,
        )
    }
}

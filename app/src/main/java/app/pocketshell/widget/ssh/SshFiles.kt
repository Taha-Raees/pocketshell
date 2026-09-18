package app.pocketshell.widget.ssh

import android.content.Context
import java.io.File

/**
 * M8.3 — the ONE reader of the guest's ~/.ssh directory inside the whole
 * SSH application (every other file in this package is pure).
 *
 * SECURITY CONTRACT (pinned by SshAppContractTest):
 *   - exactly two fixed file NAMES are ever opened here: "config" and
 *     "known_hosts" — both are PUBLIC-SHAPE data (host aliases, counts);
 *   - identity keys are NEVER opened: the config parser carries identity
 *     file NAMES, the UI displays the names, no code path constructs a
 *     File from an identity value — there is nothing here to pin a key
 *     path to;
 *   - known_hosts contents are reduced to [KnownHostsStats] counts before
 *     leaving this object (hashed hostnames stay hashed — count + shape
 *     only);
 *   - nothing is ever WRITTEN to ~/.ssh and nothing about it is persisted
 *     into app storage: this is a read-only, in-memory, re-derived view.
 *
 * The guest home is BOUND to the app's own storage (device-verified):
 * guest `~` = <applicationInfo.dataDir>/files/home, so ~/.ssh is directly
 * readable with plain java.io.File — no proot spawn, exactly the same
 * access class as ServersApp's cwd mapping.
 */
internal object SshFiles {

    /** guest `~` → the .ssh directory the application reads. */
    fun guestSshDir(dataDir: String): File =
        File(dataDir.removeSuffix("/"), "files/home/.ssh")

    fun snapshot(context: Context, probe: SshProcessProbe = SshProcessProbe()): SshSnapshot {
        val dataDir = context.applicationInfo.dataDir
            ?: return SshSnapshot(emptyList(), false, 0, 0, null, emptyList())
        return snapshotIn(guestSshDir(dataDir), probe)
    }

    /** The JVM-testable core: one snapshot of a given .ssh directory + /proc. */
    fun snapshotIn(sshDir: File, probe: SshProcessProbe): SshSnapshot {
        val configFile = File(sshDir, "config")
        val configFound = configFile.isFile
        val parse = if (configFound) {
            try {
                SshConfigParser.parse(configFile.readText())
            } catch (_: Exception) {
                // The file vanished or became unreadable mid-read: an honest
                // empty parse, not an error state — the next refresh retries.
                SshConfigParser.parse("")
            }
        } else {
            SshConfigParser.parse("")
        }

        val knownHostsFile = File(sshDir, "known_hosts")
        val knownHosts = if (knownHostsFile.isFile) {
            try {
                KnownHosts.stats(knownHostsFile.readText())
            } catch (_: Exception) {
                null // same race discipline: counted next refresh, never faked
            }
        } else {
            null
        }

        return SshSnapshot(
            hosts = parse.resolvedHosts(),
            configFound = configFound,
            includesIgnored = parse.includesIgnored,
            matchBlocksIgnored = parse.matchBlocksIgnored,
            knownHosts = knownHosts,
            processes = probe.snapshot(),
        )
    }
}

/** One refresh cycle's honest state: what the config says + what /proc shows. */
data class SshSnapshot(
    val hosts: List<SshHostEntry>,
    val configFound: Boolean,
    val includesIgnored: Int,
    val matchBlocksIgnored: Int,
    val knownHosts: KnownHostsStats?,
    val processes: List<SshClientProcess>,
)

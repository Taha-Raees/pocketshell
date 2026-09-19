package app.pocketshell.widget.external

import app.pocketshell.widget.git.GitStatusParser
import app.pocketshell.widget.git.RepoSnapshot
import app.pocketshell.widget.probe.ListeningSocket
import app.pocketshell.widget.probe.StorageBreakdown
import app.pocketshell.widget.probe.StorageScan
import app.pocketshell.widget.probe.StorageSubtree
import app.pocketshell.widget.ssh.SshArgvTarget
import app.pocketshell.widget.ssh.SshClientProcess
import app.pocketshell.widget.ssh.SshHostEntry
import app.pocketshell.widget.sync.SyncBackend
import app.pocketshell.widget.sync.SyncProfile
import app.pocketshell.widget.sync.SyncProfiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M8 — the declarative renderer: templates are PLAIN field substitution
 * over built-in probe output. No expression language, no commands, and an
 * unknown field renders as "—", never as code or a leak.
 */
class DeclarativeWidgetRendererTest {

    private val servers = WidgetManifest(
        id = "servers", name = "Servers", version = "1.0.0",
        capabilities = listOf("proc.net"),
        probe = WidgetManifest.Probe("proc.net.listen"),
        card = WidgetManifest.Card(headline = "Servers", emptyLine = "Nothing listening", itemTemplate = ":{port}  {process}"),
    )

    @Test
    fun `listener rows substitute port and process`() {
        val card = DeclarativeWidgetRenderer.render(
            servers,
            DeclarativeWidgetRenderer.ProbeResult.Listeners(
                listOf(
                    ListeningSocket(3000, 4, 1, 10, "node"),
                    ListeningSocket(8080, 6, 2, 11, null),
                ),
            ),
        )
        assertEquals("Servers", card.headline)
        assertEquals("2 running", card.countLine)
        assertEquals(listOf(":3000  node", ":8080  unknown"), card.rows)
        assertTrue(!card.empty)
    }

    @Test
    fun `an empty probe renders the manifest's empty line`() {
        val card = DeclarativeWidgetRenderer.render(
            servers,
            DeclarativeWidgetRenderer.ProbeResult.Listeners(emptyList()),
        )
        assertTrue(card.empty)
        assertEquals(null, card.countLine)
    }

    @Test
    fun `unavailability is stated - never dressed up as data`() {
        val card = DeclarativeWidgetRenderer.render(
            servers,
            DeclarativeWidgetRenderer.ProbeResult.Unavailable,
        )
        assertTrue(card.unavailable)
        assertTrue(card.rows.isEmpty())
    }

    @Test
    fun `storage rows substitute name and size with the cache label`() {
        val storage = WidgetManifest(
            id = "storage", name = "Storage", version = "1.0.0",
            capabilities = listOf("storage.rootfs"),
            probe = WidgetManifest.Probe("storage.rootfs"),
            card = WidgetManifest.Card(itemTemplate = "{name}  {size}", maxLines = 3),
        )
        val card = DeclarativeWidgetRenderer.render(
            storage,
            DeclarativeWidgetRenderer.ProbeResult.Storage(
                app.pocketshell.widget.probe.StorageBreakdown(
                    totalBytes = 38L * 1024 * 1024 * 1024,
                    subtrees = listOf(
                        StorageSubtree(StorageScan.APK_CACHE_NAME, 33L * 1024 * 1024 * 1024),
                        StorageSubtree("usr", 5L * 1024 * 1024),
                    ),
                    truncated = false,
                    scannedAtMillis = 0L,
                ),
            ),
        )
        assertEquals("38 GB", card.countLine)
        assertEquals(listOf("Package cache  33 GB", "usr  5.0 MB"), card.rows)
    }

    @Test
    fun `unknown template fields render as an em-dash placeholder`() {
        assertEquals(
            "a — b",
            DeclarativeWidgetRenderer.substitute("{known} {mystery} b", mapOf("known" to "a")),
        )
        assertEquals("plain", DeclarativeWidgetRenderer.substitute("plain", emptyMap()))
    }

    @Test
    fun `maxLines bounds the rows`() {
        val capped = servers.copy(card = servers.card.copy(maxLines = 1))
        val card = DeclarativeWidgetRenderer.render(
            capped,
            DeclarativeWidgetRenderer.ProbeResult.Listeners(
                List(4) { ListeningSocket(it, 4, it.toLong(), null, "p") },
            ),
        )
        assertEquals(1, card.rows.size)
    }

    // ------------------------------------------------------- ssh.guest (M8.4.4)

    private val ssh = WidgetManifest(
        id = "ssh", name = "SSH", version = "1.0.0",
        capabilities = listOf("guest.ready"),
        probe = WidgetManifest.Probe("ssh.guest"),
        card = WidgetManifest.Card(headline = "SSH", emptyLine = "No hosts yet", itemTemplate = "{text}", maxLines = 3),
    )

    private val client = SshClientProcess(
        pid = 42,
        processName = "ssh",
        target = SshArgvTarget(user = "root", host = "web1", port = 2222, destinationRaw = "root@web1"),
    )

    private val host = SshHostEntry(
        patterns = listOf("web"),
        hostName = "web.example.com",
        user = "root",
        port = 22,
        identityFiles = emptyList(),
        wildcard = false,
    )

    private val hostWithoutName = SshHostEntry(
        patterns = listOf("db"),
        hostName = null,
        user = null,
        port = null,
        identityFiles = emptyList(),
        wildcard = false,
    )

    @Test
    fun `ssh guest rows list running clients then saved hosts`() {
        val card = DeclarativeWidgetRenderer.render(
            ssh,
            DeclarativeWidgetRenderer.ProbeResult.SshGuest(processes = listOf(client), hosts = listOf(host)),
        )
        assertEquals(listOf("→ root@web1", "web"), card.rows)
        assertEquals("2 hosts", card.countLine)
        assertTrue(!card.empty)
        assertTrue(!card.unavailable)
    }

    @Test
    fun `ssh guest templates substitute target and name fields`() {
        val templated = ssh.copy(card = ssh.card.copy(itemTemplate = "{target} ({name})"))
        val card = DeclarativeWidgetRenderer.render(
            templated,
            DeclarativeWidgetRenderer.ProbeResult.SshGuest(
                processes = listOf(client),
                hosts = listOf(host, hostWithoutName),
            ),
        )
        // An absent HostName renders as the placeholder — never a guess.
        assertEquals(listOf("root@web1 (ssh)", "web (web.example.com)", "db (—)"), card.rows)
    }

    @Test
    fun `an empty ssh guest renders the manifest empty line`() {
        val card = DeclarativeWidgetRenderer.render(
            ssh,
            DeclarativeWidgetRenderer.ProbeResult.SshGuest(processes = emptyList(), hosts = emptyList()),
        )
        assertTrue(card.empty)
        assertEquals(null, card.countLine)
        assertTrue(card.rows.isEmpty())
    }

    @Test
    fun `ssh guest maxLines caps across processes and hosts`() {
        val card = DeclarativeWidgetRenderer.render(
            ssh,
            DeclarativeWidgetRenderer.ProbeResult.SshGuest(
                processes = listOf(client, client.copy(pid = 43)),
                hosts = listOf(host, hostWithoutName),
            ),
        )
        assertEquals(3, card.rows.size)
        assertEquals("4 hosts", card.countLine)
    }

    @Test
    fun `ssh guest unavailability is stated - never dressed up as data`() {
        val card = DeclarativeWidgetRenderer.render(
            ssh,
            DeclarativeWidgetRenderer.ProbeResult.Unavailable,
        )
        assertTrue(card.unavailable)
        assertTrue(card.rows.isEmpty())
    }

    // ---------------------------------------------------- storage.rootfs

    private val storage = WidgetManifest(
        id = "storage", name = "Storage", version = "1.0.0",
        capabilities = listOf("storage.rootfs"),
        probe = WidgetManifest.Probe("storage.rootfs"),
        card = WidgetManifest.Card(headline = "Storage", emptyLine = "Empty", itemTemplate = "{text}", maxLines = 3),
    )

    private fun breakdown(
        vararg subtrees: StorageSubtree,
        total: Long = subtrees.sumOf { it.bytes },
    ) = StorageBreakdown(
        totalBytes = total,
        subtrees = subtrees.toList(),
        truncated = false,
        scannedAtMillis = 0L,
    )

    @Test
    fun `storage rows map the breakdown's subtrees in its own order`() {
        // StorageScan hands the renderer its subtrees already sorted
        // descending; the renderer maps them verbatim — one row per
        // subtree, no re-sorting, no invented rows.
        val card = DeclarativeWidgetRenderer.render(
            storage,
            DeclarativeWidgetRenderer.ProbeResult.Storage(
                breakdown(StorageSubtree("usr", 50L), StorageSubtree("etc", 10L)),
            ),
        )
        assertEquals(listOf("usr  50 B", "etc  10 B"), card.rows)
        assertEquals("60 B", card.countLine)
        assertTrue(!card.empty)
        assertTrue(!card.unavailable)
    }

    @Test
    fun `a zero-byte storage scan renders the empty line with no count`() {
        val card = DeclarativeWidgetRenderer.render(
            storage,
            DeclarativeWidgetRenderer.ProbeResult.Storage(breakdown()),
        )
        assertTrue(card.empty)
        assertEquals(null, card.countLine)
        assertTrue(card.rows.isEmpty())
    }

    @Test
    fun `storage unavailability is stated - never dressed up as data`() {
        val card = DeclarativeWidgetRenderer.render(
            storage,
            DeclarativeWidgetRenderer.ProbeResult.Unavailable,
        )
        assertTrue(card.unavailable)
        assertTrue(card.rows.isEmpty())
        assertTrue(!card.empty)
    }

    @Test
    fun `storage maxLines caps the subtree rows`() {
        val card = DeclarativeWidgetRenderer.render(
            storage,
            DeclarativeWidgetRenderer.ProbeResult.Storage(
                breakdown(
                    StorageSubtree("usr", 40L),
                    StorageSubtree("var", 30L),
                    StorageSubtree("etc", 20L),
                    StorageSubtree("opt", 10L),
                ),
            ),
        )
        assertEquals(3, card.rows.size)
        assertEquals("100 B", card.countLine)
    }

    // ---------------------------------------------------- git.overview (M8.4.4)

    private val git = WidgetManifest(
        id = "git", name = "Git", version = "1.0.0",
        capabilities = listOf("guest.ready"),
        probe = WidgetManifest.Probe("git.overview"),
        card = WidgetManifest.Card(
            headline = "Git",
            emptyLine = "No repositories",
            itemTemplate = "{name} · {branch} · {dirty}",
            maxLines = 6,
        ),
    )

    private fun repo(
        name: String,
        statusText: String?,
        error: String? = null,
    ) = RepoSnapshot(
        path = "/root/$name",
        name = name,
        status = statusText?.let { GitStatusParser.parse(it) },
        error = error,
    )

    @Test
    fun `git overview rows map one repository per row with the real facts`() {
        val card = DeclarativeWidgetRenderer.render(
            git,
            DeclarativeWidgetRenderer.ProbeResult.GitOverview(
                listOf(
                    repo("api", "## main\n M app.kt\n?? note.txt"),
                    repo("dotfiles", "## main"),
                ),
            ),
        )
        assertEquals(listOf("api · main · dirty", "dotfiles · main · clean"), card.rows)
        assertEquals("2 repos", card.countLine)
        assertTrue(!card.empty)
        assertTrue(!card.unavailable)
    }

    @Test
    fun `git overview ahead and behind render only what git reported`() {
        val templated = git.copy(card = git.card.copy(itemTemplate = "{name} ↑{ahead} ↓{behind}"))
        val card = DeclarativeWidgetRenderer.render(
            templated,
            DeclarativeWidgetRenderer.ProbeResult.GitOverview(
                listOf(
                    repo("diverged", "## main...origin/main [ahead 1, behind 2]"),
                    repo("insync", "## main...origin/main"),
                ),
            ),
        )
        // An absent divergence is the placeholder — never a guessed zero.
        assertEquals(listOf("diverged ↑1 ↓2", "insync ↑— ↓—"), card.rows)
    }

    @Test
    fun `git overview states the branch verdict including detached and error`() {
        val templated = git.copy(card = git.card.copy(itemTemplate = "{name} {branch} {status}"))
        val card = DeclarativeWidgetRenderer.render(
            templated,
            DeclarativeWidgetRenderer.ProbeResult.GitOverview(
                listOf(
                    repo("clean", "## main"),
                    repo("hooked", "## HEAD (no branch)"),
                    // git could not read this one — it degrades ALONE.
                    RepoSnapshot(path = "/root/broken", name = "broken", status = null, error = "git exited with 128"),
                ),
            ),
        )
        assertEquals(listOf("clean main OK", "hooked detached OK", "broken unknown ERROR"), card.rows)
    }

    @Test
    fun `an empty git overview renders the manifest empty line`() {
        val card = DeclarativeWidgetRenderer.render(
            git,
            DeclarativeWidgetRenderer.ProbeResult.GitOverview(emptyList()),
        )
        assertTrue(card.empty)
        assertEquals(null, card.countLine)
        assertTrue(card.rows.isEmpty())
    }

    @Test
    fun `git overview maxLines caps the repository rows`() {
        val card = DeclarativeWidgetRenderer.render(
            git,
            DeclarativeWidgetRenderer.ProbeResult.GitOverview(
                List(4) { repo("r$it", "## main") },
            ),
        )
        assertEquals(4, card.rows.size)
        val capped = git.copy(card = git.card.copy(maxLines = 2))
        val cappedCard = DeclarativeWidgetRenderer.render(
            capped,
            DeclarativeWidgetRenderer.ProbeResult.GitOverview(
                List(4) { repo("r$it", "## main") },
            ),
        )
        assertEquals(2, cappedCard.rows.size)
        assertEquals("4 repos", card.countLine)
    }

    @Test
    fun `git overview unavailability is stated - never dressed up as data`() {
        val card = DeclarativeWidgetRenderer.render(
            git,
            DeclarativeWidgetRenderer.ProbeResult.Unavailable,
        )
        assertTrue(card.unavailable)
        assertTrue(card.rows.isEmpty())
    }

    // --------------------------------------------------- sync.overview (M8.4.4)

    private val sync = WidgetManifest(
        id = "sync", name = "Sync", version = "1.0.0",
        capabilities = listOf("guest.ready"),
        probe = WidgetManifest.Probe("sync.overview"),
        card = WidgetManifest.Card(
            headline = "Sync",
            emptyLine = "No profiles",
            itemTemplate = "{source} → {destination} · {status}",
            maxLines = 6,
        ),
    )

    private val nowMs = 3_600_000L

    private fun profile(
        id: String,
        source: String,
        destination: String,
        backend: SyncBackend = SyncBackend.RSYNC,
        lastRunMs: Long? = null,
        lastResult: String? = null,
        lastExit: Int? = null,
    ) = SyncProfile(
        id = id,
        backend = backend,
        source = source,
        destination = destination,
        createdAtMs = 0L,
        lastRunMs = lastRunMs,
        lastResult = lastResult,
        lastExit = lastExit,
    )

    @Test
    fun `sync overview rows map source destination backend and the run facts`() {
        val card = DeclarativeWidgetRenderer.render(
            sync,
            DeclarativeWidgetRenderer.ProbeResult.SyncOverview(
                profiles = listOf(
                    profile(
                        "home",
                        source = "/root/docs",
                        destination = "box:/mnt/backup",
                        lastRunMs = 0L,
                        lastResult = SyncProfiles.RESULT_OK,
                        lastExit = 0,
                    ),
                    profile("pics", source = "/root/pics", destination = "/root/backup"),
                ),
                nowMs = nowMs,
            ),
        )
        assertEquals(
            listOf(
                "~/docs → box:/mnt/backup · 1h ago · OK (exit 0)",
                "~/pics → ~/backup · never run",
            ),
            card.rows,
        )
        assertEquals("2 profiles", card.countLine)
        assertTrue(!card.empty)
        assertTrue(!card.unavailable)
    }

    @Test
    fun `sync overview carries the backend name as a template field`() {
        val templated = sync.copy(card = sync.card.copy(itemTemplate = "{backend}: {source}"))
        val card = DeclarativeWidgetRenderer.render(
            templated,
            DeclarativeWidgetRenderer.ProbeResult.SyncOverview(
                profiles = listOf(
                    profile("r", source = "/root/a", destination = "/root/b", backend = SyncBackend.RCLONE),
                    profile("s", source = "/root/c", destination = "/root/d"),
                ),
                nowMs = nowMs,
            ),
        )
        assertEquals(listOf("rclone: ~/a", "rsync: ~/c"), card.rows)
    }

    @Test
    fun `an empty sync overview renders the manifest empty line`() {
        val card = DeclarativeWidgetRenderer.render(
            sync,
            DeclarativeWidgetRenderer.ProbeResult.SyncOverview(profiles = emptyList(), nowMs = nowMs),
        )
        assertTrue(card.empty)
        assertEquals(null, card.countLine)
        assertTrue(card.rows.isEmpty())
    }

    @Test
    fun `sync overview maxLines caps the profile rows`() {
        val card = DeclarativeWidgetRenderer.render(
            sync.copy(card = sync.card.copy(maxLines = 2)),
            DeclarativeWidgetRenderer.ProbeResult.SyncOverview(
                profiles = List(4) { profile("p$it", source = "/root/s", destination = "/root/d") },
                nowMs = nowMs,
            ),
        )
        assertEquals(2, card.rows.size)
        assertEquals("4 profiles", card.countLine)
    }

    @Test
    fun `sync overview unavailability is stated - never dressed up as data`() {
        val card = DeclarativeWidgetRenderer.render(
            sync,
            DeclarativeWidgetRenderer.ProbeResult.Unavailable,
        )
        assertTrue(card.unavailable)
        assertTrue(card.rows.isEmpty())
    }
}

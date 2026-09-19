package app.pocketshell.widget.external

import app.pocketshell.widget.git.GitSnapshot
import app.pocketshell.widget.git.RepoSnapshot
import app.pocketshell.widget.git.ScanResult
import app.pocketshell.widget.sync.BackendStatus
import app.pocketshell.widget.sync.ProbeResult as SyncProbeResult
import app.pocketshell.widget.sync.SyncProfile
import app.pocketshell.widget.sync.SyncSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M8.4.4 — the external widget dispatch's PURE mapping half: a finished
 * probe pass → the declarative renderer's primitive result. The gate and
 * the exec stay Android-bound (state holder + guest runner); what is
 * testable without a device is pinned here: a Done scan becomes rows, a
 * failed exec becomes the honest Unavailable — never "no repositories" /
 * "no profiles" (the compiled cards' rule), and the profiles ride the
 * mapping verbatim with the render clock.
 */
class ExternalWidgetProbeMappingTest {

    private fun repo(name: String) = RepoSnapshot(
        path = "/root/$name",
        name = name,
        status = null,
        error = null,
    )

    private fun profile(id: String) = SyncProfile(
        id = id,
        backend = app.pocketshell.widget.sync.SyncBackend.RSYNC,
        source = "/root/$id",
        destination = "/root/$id-bak",
        createdAtMs = 0L,
    )

    @Test
    fun `a done git scan maps its repository snapshots`() {
        val repos = listOf(repo("api"), repo("dotfiles"))
        val result = gitOverviewResult(ScanResult.Done(GitSnapshot(gitVersion = "2.34.1", repos = repos)))
        val overview = result as DeclarativeWidgetRenderer.ProbeResult.GitOverview
        assertEquals(repos, overview.repos)
        assertTrue(result !is DeclarativeWidgetRenderer.ProbeResult.Unavailable)
    }

    @Test
    fun `a failed git scan is unavailable - never no repositories`() {
        val result = gitOverviewResult(ScanResult.Failed("guest exec exited with 1"))
        assertEquals(DeclarativeWidgetRenderer.ProbeResult.Unavailable, result)
    }

    @Test
    fun `a git-less guest scans done with no repositories - the empty line`() {
        // The probe reports a missing git binary as a DONE scan with an
        // empty repository list: the manifest's empty line, never a fake
        // row and never an Unavailable (the guest IS ready).
        val result = gitOverviewResult(ScanResult.Done(GitSnapshot(gitVersion = null, repos = emptyList())))
        val manifest = WidgetManifest(
            id = "git", name = "Git", version = "1.0.0",
            capabilities = listOf("guest.ready"),
            probe = WidgetManifest.Probe("git.overview"),
            card = WidgetManifest.Card(emptyLine = "No repositories"),
        )
        val card = DeclarativeWidgetRenderer.render(manifest, result)
        assertTrue(card.empty)
        assertTrue(!card.unavailable)
        assertTrue(card.rows.isEmpty())
    }

    @Test
    fun `a done sync probe maps the profiles with the render clock`() {
        val profiles = listOf(profile("home"), profile("pics"))
        val nowMs = 3_600_000L
        val result = syncOverviewResult(
            profiles = profiles,
            nowMs = nowMs,
            scan = SyncProbeResult.Done(
                SyncSnapshot(
                    rsync = BackendStatus("/bin/rsync", "3.2.3"),
                    rclone = BackendStatus(null, null),
                    pairs = emptyList(),
                    complete = true,
                ),
            ),
        )
        val overview = result as DeclarativeWidgetRenderer.ProbeResult.SyncOverview
        assertEquals(profiles, overview.profiles)
        assertEquals(nowMs, overview.nowMs)
    }

    @Test
    fun `a failed sync probe is unavailable - never no profiles`() {
        val result = syncOverviewResult(
            profiles = listOf(profile("home")),
            nowMs = 0L,
            scan = SyncProbeResult.Failed("unrecognized probe output"),
        )
        assertEquals(DeclarativeWidgetRenderer.ProbeResult.Unavailable, result)
    }
}

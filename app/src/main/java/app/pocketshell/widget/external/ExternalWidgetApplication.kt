package app.pocketshell.widget.external

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import app.pocketshell.runtime.RuntimeState
import app.pocketshell.ui.home.HomeTokens
import app.pocketshell.ui.theme.TerminalTheme
import app.pocketshell.widget.HomeAppContext
import app.pocketshell.widget.HomeAppSpec
import app.pocketshell.widget.HomeApplication
import app.pocketshell.widget.probe.ListeningSocket
import app.pocketshell.widget.probe.ServerInfo
import app.pocketshell.widget.probe.ServerProbe
import app.pocketshell.widget.ssh.SshFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * M8.4.4 — the DOWNLOADABLE Home Application: a validated [WidgetManifest]
 * (data, per the M8 trust model — docs/M8-WIDGET-SYSTEM.md §6) rendered by
 * this FIXED app code into the one-card pattern. This is the replacement
 * surface for the retired compiled Servers/SSH cards: same probes, same
 * honesty, but the card's identity, title, empty line, row template and
 * line budget now come from the installed manifest.
 *
 * The nothing-executed contract, structurally: this class renders and
 * probes ONLY — the manifest contributes Strings (title, template, empty
 * line) that are substituted over fixed probe output by
 * [DeclarativeWidgetRenderer], and the probe is one of the built-in
 * primitives dispatched below. A manifest field can never reach a
 * File(), a Process, a shell, or a network call; an unknown probe kind
 * renders as the honest Unavailable state, never as a fallback attempt.
 *
 * Lifecycle: exactly the compiled cards' pattern (ServersApp/SshApp) —
 * READY required (honest "Linux not ready" otherwise), RESUMED-gated
 * collection, a 5s tick with each probe's own cheapness (the Servers
 * pid-set idle gate; SSH's plain file/proc re-read), and the last card +
 * probing flag living in the process-scoped state holder under
 * "external:<id>" so navigation neither loses the snapshot nor re-probes
 * on arrival.
 */
class ExternalWidgetApplication(private val manifest: WidgetManifest) : HomeApplication() {

    init {
        // Defense in depth: only a fully valid manifest can become an
        // application (the store's decode already guarantees this; a
        // violation here is a programming error and fails loudly).
        when (val check = WidgetManifestValidator.validate(manifest)) {
            is WidgetManifestValidator.Result.Invalid ->
                throw IllegalArgumentException(
                    "external widget '${manifest.id}' is invalid: ${check.reasons.joinToString("; ")}",
                )
            is WidgetManifestValidator.Result.Valid -> Unit
        }
    }

    override val spec = HomeAppSpec(
        id = manifest.id,
        name = manifest.name,
        summary = manifest.summary,
    )

    @Composable
    override fun Content(context: HomeAppContext) {
        // The process-scoped holder (M8.4.2 contract): the rendered card,
        // the probing flag and the ServerProbe instance (its pid-set gate
        // memory is part of the cache) survive navigation.
        val state = remember {
            context.stateStore.forApp(HOLDER_PREFIX + manifest.id) { ExternalWidgetState() }
        }
        val lifecycleOwner = LocalLifecycleOwner.current
        val appContext = LocalContext.current.applicationContext

        LaunchedEffect(context.runtimeState) {
            if (context.runtimeState != RuntimeState.READY) {
                state.card = DeclarativeWidgetRenderer.render(
                    manifest,
                    DeclarativeWidgetRenderer.ProbeResult.Unavailable,
                )
                state.probing = false
                return@LaunchedEffect
            }
            lifecycleOwner.lifecycle.currentStateFlow
                .map { it.isAtLeast(Lifecycle.State.RESUMED) }
                .distinctUntilChanged()
                .collectLatest { active ->
                    if (!active) return@collectLatest
                    while (true) {
                        state.probing = true
                        val result = withContext(Dispatchers.IO) {
                            state.probe(manifest, appContext)
                        }
                        // null = the idle gate says nothing changed: the
                        // cached card stands, no re-render, no invention.
                        if (result != null) {
                            state.card = DeclarativeWidgetRenderer.render(manifest, result)
                        }
                        state.probing = false
                        delay(REFRESH_MS)
                    }
                }
        }

        ExternalWidgetCard(state)
    }

    // ---------------------------------------------------------------- card

    @Composable
    private fun ExternalWidgetCard(state: ExternalWidgetState) {
        val card = state.card
        Column(modifier = Modifier.fillMaxSize()) {
            // Header — the manifest's overridable headline, both densities.
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(
                    text = card?.headline ?: manifest.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = HomeTokens.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                val count = card?.countLine
                when {
                    count != null -> Text(
                        text = count,
                        fontFamily = TerminalTheme.mono,
                        fontSize = 11.sp,
                        color = HomeTokens.textDim,
                        maxLines = 1,
                    )
                    // No data yet, but a probe is in flight — say so.
                    state.probing -> Text(
                        text = "…",
                        fontFamily = TerminalTheme.mono,
                        fontSize = 11.sp,
                        color = HomeTokens.textDim,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))

            // Rows — the renderer's data (already template-substituted and
            // maxLines-capped), always scrolling, never re-interpreted.
            val rows = card?.rows.orEmpty()
            if (rows.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    rows.forEachIndexed { index, row ->
                        Text(
                            text = row,
                            fontFamily = TerminalTheme.mono,
                            fontSize = 12.sp,
                            color = HomeTokens.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                        if (index != rows.lastIndex) {
                            HorizontalDivider(color = HomeTokens.hairline.copy(alpha = 0.6f))
                        }
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }

            Spacer(Modifier.height(4.dp))
            // The honest state line: no data / not ready / the manifest's
            // own empty line — never a fabricated row.
            val stateLine = when {
                card == null -> "Looking…"
                card?.unavailable == true -> "Linux not ready"
                card?.empty == true -> manifest.card.emptyLine
                else -> null
            }
            if (stateLine != null) {
                Text(
                    text = stateLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeTokens.textDim,
                    maxLines = 1,
                )
            }
        }
    }

    companion object {
        private const val REFRESH_MS = 5_000L

        /** The state-store namespace keeping external cards off builtin ids. */
        const val HOLDER_PREFIX = "external:"
    }
}

/**
 * The application's process-scoped state (M8.4.2): the last rendered card,
 * the in-flight flag, and the probe plumbing (the Servers probe instance —
 * its pid-set idle gate IS its cache).
 */
internal class ExternalWidgetState {

    var card by mutableStateOf<DeclarativeWidgetRenderer.CardData?>(null)
    var probing by mutableStateOf(false)

    private val serverProbe = ServerProbe()

    /**
     * One probe pass for the manifest's built-in primitive, or null when
     * the idle gate says the last snapshot still stands (nothing changed
     * in /proc and nothing was listening): the caller keeps the cached
     * card — refresh-on-purpose, never refresh-for-show.
     */
    fun probe(
        manifest: WidgetManifest,
        appContext: Context,
    ): DeclarativeWidgetRenderer.ProbeResult? = when (manifest.probe.kind) {
        WidgetManifestValidator.PROC_NET_LISTEN -> {
            // The M8.1 gate, holder-owned: a changed pid set (or a last
            // snapshot WITH servers) runs the full pipeline; otherwise the
            // cached card stands and this pass costs one /proc readdir.
            if (serverProbe.shouldFullScan(serverProbe.peekPidSet())) {
                val sockets = serverProbe.snapshot().map { it.toListenerSocket() }
                DeclarativeWidgetRenderer.ProbeResult.Listeners(sockets)
            } else {
                null
            }
        }
        WidgetManifestValidator.SSH_GUEST ->
            SshFiles.snapshot(appContext).let { snapshot ->
                DeclarativeWidgetRenderer.ProbeResult.SshGuest(
                    processes = snapshot.processes,
                    hosts = snapshot.hosts,
                )
            }
        // The validator admits nothing else; a kind this build predates
        // renders as the honest unavailable state.
        else -> DeclarativeWidgetRenderer.ProbeResult.Unavailable
    }

    /**
     * A verified [ServerInfo] as the renderer's listener row data. ipVersion
     * and inode are plumbing the declarative card does not display (the
     * shared-loopback connect verification is version-agnostic); the name
     * uses the probe's honest "Local service" fallback — the compiled
     * card's exact row text.
     */
    private fun ServerInfo.toListenerSocket() = ListeningSocket(
        port = port,
        ipVersion = 4,
        inode = 0L,
        pid = pid,
        processName = displayName,
    )
}

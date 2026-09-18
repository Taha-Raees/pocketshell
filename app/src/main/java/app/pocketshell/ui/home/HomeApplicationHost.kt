package app.pocketshell.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pocketshell.runtime.RuntimeState
import app.pocketshell.settings.CardSize
import app.pocketshell.ui.theme.LocalAuroraPhase
import app.pocketshell.ui.theme.TerminalTheme
import app.pocketshell.ui.theme.auroraEdge
import app.pocketshell.widget.HomeAppContext
import app.pocketshell.widget.HomeApplications
import app.pocketshell.widget.WidgetNav

/**
 * M8.2 — the ONE Home Application Card: the entire hero area of Home is a
 * single persistent surface hosting ONE PocketShell-native single-page
 * application ([HomeApplications]; Servers is the reference implementation
 * and the default). This file is the card's HOST and nothing else:
 *
 *   - the chrome, derived from the existing Home layout (full content
 *     width; [HomeTokens.homeAppCardHeight] from the shared height budget
 *     and the user's Control Center card-size setting);
 *   - resolution of the persisted application id (an id that no longer
 *     resolves renders the honest Missing card — stated, never
 *     substituted, identical to every persisted-id discipline here);
 *   - the [WidgetNav] seam, implemented once from the existing callbacks.
 *
 * Theme discipline: the chrome reads ONLY the shared theme tokens
 * ([HomeTokens] → TerminalTheme) and the theme-gated aurora edge — the
 * card follows the user's selected theme; nothing here is Aurora-specific.
 * Host discipline (unchanged from M8): no probing, no polling, no
 * detector access — the application owns its content; the host owns the
 * card.
 */
@Composable
fun HomeApplicationHost(
    appId: String,
    cardSize: CardSize,
    runtimeState: RuntimeState,
    onOpenTerminal: () -> Unit,
    onOpenLinuxShell: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenGuestFiles: () -> Unit,
    onOpenWidgetSettings: () -> Unit,
    onOpenCompanionUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val height = HomeTokens.homeAppCardHeight(cardSize.scale)
    val auroraPhase = LocalAuroraPhase.current
    val nav = remember(
        onOpenTerminal, onOpenLinuxShell, onOpenDiagnostics,
        onOpenGuestFiles, onOpenWidgetSettings, onOpenCompanionUrl,
    ) {
        object : WidgetNav {
            override fun openTerminal() = onOpenTerminal()
            override fun openLinuxShell() = onOpenLinuxShell()
            override fun openDiagnostics() = onOpenDiagnostics()
            override fun openGuestFiles() = onOpenGuestFiles()
            override fun openWidgetSettings() = onOpenWidgetSettings()
            override fun openCompanion(url: String) = onOpenCompanionUrl(url)
        }
    }
    when (val resolved = HomeApplications.resolve(appId)) {
        is HomeApplications.Resolved.Found ->
            ApplicationCard(heightDp = height, auroraPhase = auroraPhase, modifier = modifier) {
                resolved.application.Content(HomeAppContext(nav = nav, runtimeState = runtimeState))
            }
        is HomeApplications.Resolved.Missing ->
            MissingApplicationCard(
                appId = resolved.id,
                heightDp = height,
                auroraPhase = auroraPhase,
                onClick = onOpenWidgetSettings,
                modifier = modifier,
            )
    }
}

/** The standard application chrome: clip → chrome tone → aurora edge → padding. */
@Composable
private fun ApplicationCard(
    heightDp: androidx.compose.ui.unit.Dp,
    auroraPhase: androidx.compose.runtime.State<Float>,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(heightDp)
            .clip(RoundedCornerShape(HomeTokens.heroRadius))
            .background(HomeTokens.surfaceEnv)
            // Aurora identity: the Home Application Card is the page's
            // important surface — when the SELECTED THEME provides the
            // aurora (and motion is allowed) it carries the glow edge;
            // on every other theme this is inert (the shared phase is
            // theme-gated at the root).
            .auroraEdge(auroraPhase, HomeTokens.heroRadius)
            .padding(16.dp),
    ) {
        content()
    }
}

/** A persisted application id that no longer resolves — stated, never faked. */
@Composable
private fun MissingApplicationCard(
    appId: String,
    heightDp: androidx.compose.ui.unit.Dp,
    auroraPhase: androidx.compose.runtime.State<Float>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        ApplicationCard(heightDp = heightDp, auroraPhase = auroraPhase) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClickLabel = "Open Home application settings") {
                        onClick()
                    },
            ) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "Missing application",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = HomeTokens.textPrimary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Manage in Settings → Home application",
                    style = MaterialTheme.typography.bodySmall,
                    color = HomeTokens.textDim,
                )
                Text(
                    text = appId,
                    fontFamily = TerminalTheme.mono,
                    fontSize = 11.sp,
                    color = HomeTokens.textDim.copy(alpha = 0.7f),
                )
            }
        }
    }
}

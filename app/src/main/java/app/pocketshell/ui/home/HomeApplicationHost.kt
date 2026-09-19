package app.pocketshell.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pocketshell.runtime.RuntimeState
import app.pocketshell.settings.CardSize
import app.pocketshell.ui.theme.LocalAuroraPhase
import app.pocketshell.ui.theme.TerminalTheme
import app.pocketshell.ui.theme.auroraEdge
import app.pocketshell.widget.HomeAppContext
import app.pocketshell.widget.HomeApplication
import app.pocketshell.widget.HomeApplications
import app.pocketshell.widget.HomeAppStateStore
import app.pocketshell.widget.WidgetNav

/**
 * M8.3 — the Home Application CAROUSEL: the hero area of Home is one
 * horizontally swipeable surface whose pages are the user's configured
 * Home Applications (Servers, Git, SSH, …), each a PocketShell-native
 * single-page application inside its own full-size canvas
 * (docs/M8-WIDGET-SYSTEM.md §9).
 *
 * Carousel discipline:
 *   - Horizontal swipe snaps to one application; page dots show the
 *     current one (only when there is something to swipe between).
 *   - Page identity is the application ID (key = id), so reordering or
 *     removal never mixes two applications' state; each application keeps
 *     its holders in the process-scoped HomeAppStateStore (M8.4.2) —
 *     cached probes and UI state survive swiping away, leaving Home and
 *     configuration changes, and the last-used application is restored
 *     on return (persisted `home_app_selected`).
 *   - Only pages adjacent to the current one stay composed — inactive
 *     applications do no work (their probe ticks are lifecycle- and
 *     composition-driven, so an off-screen page simply idles).
 *   - Theme discipline unchanged: chrome + dots read ONLY the shared
 *     theme tokens; the aurora edge appears only when the selected theme
 *     provides it (the shared root phase is theme-gated).
 *   - An EMPTY configuration is stated honestly with the one real action
 *     (Manage) — never silently substituted with a default app.
 */
@Composable
fun HomeApplicationHost(
    appIds: List<String>,
    cardSize: CardSize,
    runtimeState: RuntimeState,
    stateStore: HomeAppStateStore,
    externalApps: List<HomeApplication>,
    selectedAppId: String?,
    onSelectedAppChanged: (String) -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenLinuxShell: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenGuestFiles: () -> Unit,
    onOpenWidgetSettings: () -> Unit,
    onOpenCompanionUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val height = HomeTokens.homeAppCardHeight(cardSize.scale)
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

    Column(modifier = modifier.fillMaxWidth()) {
        if (appIds.isEmpty()) {
            EmptyCarouselCard(heightDp = height, onManage = onOpenWidgetSettings)
        } else {
            val pagerState = rememberPagerState(pageCount = { appIds.size })

            // M8.4.2 — returning to Home reopens the LAST USED application,
            // not page 0. The restore is one-shot per Home visit and only
            // waits until the persisted id actually appears in the loaded
            // configuration (a removed application simply never restores;
            // the settled-page effect below re-records where the user is).
            var restored by remember { mutableStateOf(false) }
            LaunchedEffect(appIds, selectedAppId) {
                val index = selectedAppId?.let(appIds::indexOf) ?: -1
                if (!restored && index >= 0 && pagerState.currentPage != index) {
                    pagerState.scrollToPage(index)
                    restored = true
                } else if (index >= 0) {
                    restored = true
                }
            }
            // The user's settled page IS the selection — recorded for the
            // next Home visit (persisted by the ViewModel; survives both
            // navigation and process death).
            val currentAppIds by rememberUpdatedState(appIds)
            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.settledPage }.collect { page ->
                    currentAppIds.getOrNull(page)?.let(onSelectedAppChanged)
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth(),
                key = appIds::get,
                pageSpacing = 12.dp,
            ) { page ->
                when (val resolved = HomeApplications.resolve(appIds[page], externalApps)) {
                    is HomeApplications.Resolved.Found ->
                        ApplicationCard(
                            heightDp = height,
                            auroraPhase = LocalAuroraPhase.current,
                        ) {
                            resolved.application.Content(
                                HomeAppContext(
                                    nav = nav,
                                    runtimeState = runtimeState,
                                    stateStore = stateStore,
                                ),
                            )
                        }
                    is HomeApplications.Resolved.Missing ->
                        MissingApplicationCard(appId = resolved.id, heightDp = height)
                }
            }
            if (appIds.size > 1) {
                Spacer(Modifier.height(6.dp))
                CarouselDots(
                    count = appIds.size,
                    active = pagerState.currentPage,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** The standard application chrome: clip → chrome tone → aurora edge → padding. */
@Composable
private fun ApplicationCard(
    heightDp: Dp,
    auroraPhase: androidx.compose.runtime.State<Float>,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
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

/** A configured application id that no longer resolves — stated, never faked. */
@Composable
private fun MissingApplicationCard(appId: String, heightDp: Dp) {
    ApplicationCard(heightDp = heightDp, auroraPhase = LocalAuroraPhase.current) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Missing application",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = HomeTokens.textPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Manage in Settings → Home applications",
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

/** An empty carousel is a real configuration — stated with its one action. */
@Composable
private fun EmptyCarouselCard(heightDp: Dp, onManage: () -> Unit) {
    ApplicationCard(heightDp = heightDp, auroraPhase = LocalAuroraPhase.current) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "No Home applications",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = HomeTokens.textPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Add Servers, Git, SSH or more from Settings.",
                style = MaterialTheme.typography.bodySmall,
                color = HomeTokens.textDim,
            )
            TextButton(onClick = onManage, modifier = Modifier.padding(top = 2.dp)) {
                Text("Manage", color = HomeTokens.accent)
            }
        }
    }
}

/** Carousel wayfinding: the accent pill marks the current application. */
@Composable
private fun CarouselDots(count: Int, active: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val selected = index == active
            val dotWidth by androidx.compose.animation.core.animateDpAsState(
                targetValue = if (selected) 16.dp else 6.dp,
                animationSpec = tween(150, easing = FastOutSlowInEasing),
                label = "carouselDot",
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(width = dotWidth, height = 6.dp)
                    .background(
                        if (selected) HomeTokens.accent else HomeTokens.hairline,
                        CircleShape,
                    ),
            )
        }
    }
}

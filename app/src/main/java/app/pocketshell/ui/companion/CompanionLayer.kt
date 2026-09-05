package app.pocketshell.ui.companion

import android.app.Activity
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pocketshell.companion.CompanionBackAction
import app.pocketshell.companion.CompanionDef
import app.pocketshell.companion.CompanionFailure
import app.pocketshell.companion.CompanionHeights
import app.pocketshell.companion.CompanionWebHost
import app.pocketshell.companion.CompanionViewModel
import app.pocketshell.companion.decideBackAction
import app.pocketshell.keyboard.KeyboardInputRouter
import app.pocketshell.ui.home.HomeTokens
import app.pocketshell.ui.system.MidnightFilledButton
import app.pocketshell.ui.theme.TerminalTheme
import android.widget.Toast

/**
 * Phase 4 — the Companion layer (docs/PHASE-4-COMPANION-DESIGN.md §9–§11).
 *
 * A persistent workspace surface that lives ABOVE the current PocketShell
 * screen, bottom-anchored, resized ONLY by its handle (brief R1/R5):
 *
 *   collapsed → the handle alone, at the bottom, over every screen
 *   raised    → handle rides the surface's top edge; below it the
 *               tab strip + the web canvas; the panel fills down to the
 *               bottom of the screen
 *
 * Smooth-as-silk mechanics (§9): the drag is 1:1 with the finger; while
 * dragging, the WebView's measured height stays FROZEN at the last settled
 * value, bottom-aligned in the growing/shrinking panel — the page never
 * reflows under the finger; one resize on release. Release snaps to an
 * anchor only within the gentle window, otherwise stays exactly where
 * released, and the settled height is persisted.
 */
@Composable
fun CompanionLayer(
    viewModel: CompanionViewModel,
    onOpenCompanionSettings: () -> Unit,
    keyboardBottomInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val defs by viewModel.defs.collectAsStateWithLifecycle()
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val activeTabId by viewModel.activeTabId.collectAsStateWithLifecycle()
    val settledFraction by viewModel.panelHeight.collectAsStateWithLifecycle()
    val pageTitles by viewModel.pageTitles.collectAsStateWithLifecycle()
    val pageFailures by viewModel.pageFailures.collectAsStateWithLifecycle()

    // m4.0.2 (kept): bumped by Retry — re-keys the canvas's remember so
    // prepare() runs again on a freshly created WebView.
    var webRetrySeed by remember { mutableStateOf(0) }

    // m4.0.3: the "+" affordance now opens the Companion picker sheet.
    var pickerOpen by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // In-flight drag fraction; null when the pointer is up (settled state).
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val currentFraction = dragFraction ?: settledFraction
    val raised = CompanionHeights.isRaised(currentFraction)

    val activeTab = tabs.firstOrNull { it.defId == activeTabId }
    val activeDef = defs.firstOrNull { it.id == activeTabId }

    // ---- pool wiring (listener + file chooser + lifecycle) -----------------

    DisposableEffect(viewModel) {
        CompanionWebHost.setListener(object : CompanionWebHost.Listener {
            override fun onVisitStarted(defId: String, url: String) {
                viewModel.recordLastUrl(defId, url)
            }

            override fun onTitleReceived(defId: String, title: String) {
                viewModel.pageTitles.value = viewModel.pageTitles.value + (defId to title)
            }

            override fun onMainFrameError(defId: String, description: String) {
                viewModel.recordMainFrameError(defId, description)
            }

            override fun onRendererGone(defId: String) {
                viewModel.recordRendererGone(defId)
            }
        })
        onDispose { CompanionWebHost.setListener(null) }
    }

    var chooserCallback by remember { mutableStateOf<((Uri?) -> Unit)?>(null) }
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        chooserCallback?.invoke(uri)
        chooserCallback = null
    }
    DisposableEffect(Unit) {
        CompanionWebHost.setFileChooserHost(object : CompanionWebHost.FileChooserHost {
            override fun launch(acceptType: String?, onResult: (Uri?) -> Unit) {
                chooserCallback = onResult
                // acceptTypes may carry extensions ("​.pdf") — GetContent wants a mime.
                val mime = acceptType
                    ?.takeIf { it.isNotBlank() && !it.startsWith(".") }
                    ?.takeIf { it.contains('/') }
                    ?: "*/*"
                filePicker.launch(mime)
            }
        })
        onDispose { CompanionWebHost.setFileChooserHost(null) }
    }

    // Activity pause/resume: park everything / wake the active tab (§8).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> CompanionWebHost.pauseAll()
                Lifecycle.Event.ON_RESUME -> CompanionWebHost.resumeActive()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ---- layout -------------------------------------------------------------

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val containerHeightPx = constraints.maxHeight.toFloat()
        val density = LocalDensity.current
        val handleZonePx = with(density) { HANDLE_ZONE.toPx() }
        val stripPx = with(density) { STRIP_HEIGHT.toPx() }

        // The web canvas keeps its settled measured height during a drag (§9).
        val settledWebPx = (containerHeightPx * settledFraction - stripPx).coerceAtLeast(0f)

        val panelHeightPx = containerHeightPx * currentFraction
        val webHeightPx = (panelHeightPx - stripPx).coerceAtLeast(0f)

        // m4.0.3 (device feedback): the keyboard is the bottom-most surface —
        // while the shared deck is visible on the terminal screen its measured
        // height arrives as [keyboardBottomInset] and the panel rides ABOVE
        // it, so the keyboard never opens on top of anything. Without the
        // deck the panel keeps its normal system-inset behavior.
        val bottomModifier = if (keyboardBottomInset > 0.dp) {
            Modifier.imePadding().padding(bottom = keyboardBottomInset)
        } else {
            Modifier.navigationBarsPadding().imePadding()
        }

        // m5.0 final correction — the sheet's drag math, shared VERBATIM by
        // both vertical drag surfaces (the dedicated handle, and — near full
        // height — the tab strip). Up = taller, down = shorter, release =
        // stay exactly there; only a release below the collapse threshold
        // minimizes. No snap points, ever.
        fun startSheetDrag() {
            dragFraction = settledFraction.takeIf { CompanionHeights.isRaised(it) } ?: 0f
        }
        fun dragSheetBy(deltaPx: Float) {
            val base = dragFraction ?: settledFraction
            dragFraction = (base - deltaPx / containerHeightPx).coerceIn(0f, CompanionHeights.FULL)
        }
        fun endSheetDrag() {
            val released = dragFraction
            dragFraction = null
            if (released != null) viewModel.settleHeight(released)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .then(bottomModifier),
        ) {
            CompanionHandle(
                dragging = dragFraction != null,
                onTap = {
                    // Phase 5 §1 — a single tap anywhere on the bar IMMEDIATELY
                    // minimizes the Companion, at every raised height (25%,
                    // 50%, 80%, near-full — no drag needed). When minimized the
                    // tap does nothing: the bar is dragged UP to restore.
                    if (raised) viewModel.collapse()
                },
                onDragStart = { startSheetDrag() },
                onDrag = { delta -> dragSheetBy(delta) },
                onDragEnd = { endSheetDrag() },
            )
            if (raised) {
                if (activeDef == null) {
                    // Definitions exist? offer add — else the honest empty state.
                    CompanionEmptyState(
                        hasDefinitions = defs.isNotEmpty(),
                        onAdd = viewModel::openDefault,
                        onOpenSettings = onOpenCompanionSettings,
                    )
                } else {
                    // m5.0 final correction — NEAR-FULL DRAG SURFACE. Once
                    // the sheet sits at/above 90% of the container, the tiny
                    // handle at the very top edge is hard to reach, so the
                    // TAB STRIP joins it as an ADDITIONAL vertical drag
                    // surface. Strictly a drag surface:
                    //   • detectVerticalDragGestures claims a gesture only
                    //     AFTER the vertical touch slop is crossed — tab
                    //     taps, close buttons, +, refresh and horizontal
                    //     tab scrolling behave exactly as before;
                    //   • NOTHING here minimizes on touch (tap-to-minimize
                    //     stays the dedicated handle's exclusive duty);
                    //   • the surface also stays attached while ANY drag is
                    //     in flight, so a tab-bar drag travelling below the
                    //     threshold is not cut mid-gesture.
                    val tabDragArmed =
                        CompanionHeights.tabBarDragSurface(settledFraction) || dragFraction != null
                    Box(
                        modifier = if (tabDragArmed) {
                            Modifier.pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragStart = { startSheetDrag() },
                                    onVerticalDrag = { change, dragAmount ->
                                        change.consume()
                                        dragSheetBy(dragAmount)
                                    },
                                    onDragEnd = { endSheetDrag() },
                                    onDragCancel = { endSheetDrag() },
                                )
                            }
                        } else {
                            Modifier
                        },
                    ) {
                        CompanionTabStrip(
                            tabs = tabs,
                            defs = defs,
                            activeId = activeTabId,
                            onSelect = viewModel::selectTab,
                            onClose = viewModel::closeTab,
                            // m4.0.3: "+" opens the picker sheet (list + add),
                            // it no longer silently opens the default tab.
                            onAdd = { pickerOpen = true },
                            // m4.0.12 §3/§4: refresh (tap) and hard refresh
                            // (long-press) act on the ACTIVE tab only. The hard
                            // path announces itself once, quietly.
                            onRefresh = { CompanionWebHost.reload(activeTabId) },
                            onHardRefresh = {
                                Toast.makeText(context, "Hard reloading…", Toast.LENGTH_SHORT).show()
                                CompanionWebHost.reloadHard(activeTabId)
                            },
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(with(density) { webHeightPx.toDp() })
                            .clipToBounds()
                            .background(TerminalTheme.canvas),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        CompanionWebCanvas(
                            defId = activeDef.id,
                            url = activeTab?.lastUrl ?: activeDef.url,
                            // Frozen measured height during drag (§9).
                            webHeightPx = if (dragFraction != null) settledWebPx else webHeightPx,
                            failure = pageFailures[activeDef.id],
                            retrySeed = webRetrySeed,
                            onRetry = {
                                // A Retry is the user asking the question again —
                                // a fresh WebView on the proven recipe.
                                viewModel.retryTab(activeDef.id)
                                webRetrySeed++
                            },
                            // m4.0.4 (kept): the raw canvas as-is — the site's
                            // own consent banner lives there and may still work.
                            onDismiss = { viewModel.dismissFailure(activeDef.id) },
                        )
                    }
                }
            }
        }

        // m4.0.3 — the Companion picker sheet: lists every Companion to open
        // plus "Add Companion" (the management page). Composed above the
        // panel with the same keyboard inset, so it too rides above the deck.
        if (pickerOpen) {
            CompanionPickerSheet(
                defs = defs,
                openDefIds = tabs.map { it.defId }.toSet(),
                activeId = activeTabId,
                bottomModifier = bottomModifier,
                onSelect = { defId ->
                    pickerOpen = false
                    viewModel.openCompanion(defId)
                },
                onAddNew = {
                    pickerOpen = false
                    onOpenCompanionSettings()
                },
                onDismiss = { pickerOpen = false },
            )
        }

        // Back policy (§14): web history → collapse → fall through. The
        // handler is composed AFTER the screens' handlers, so while raised
        // it wins; when collapsed it disables itself (PASS_THROUGH).
        BackHandler(enabled = raised) {
            when (decideBackAction(CompanionWebHost.canGoBack(activeTabId), raised)) {
                CompanionBackAction.WEB_BACK -> CompanionWebHost.goBack(activeTabId)
                CompanionBackAction.COLLAPSE -> viewModel.collapse()
                CompanionBackAction.PASS_THROUGH -> {}
            }
        }

        // m4.0.3: the picker is the topmost surface — Back closes it first.
        BackHandler(enabled = pickerOpen) { pickerOpen = false }

        // m4.0.12 §14 — clean focus restoration: when the Companion closes,
        // the terminal underneath becomes the visible typeable surface again.
        // Handing focus back explicitly prevents input from continuing to
        // flow to the (now hidden) WebView or the keyboard flickering while
        // ownership is ambiguous.
        //
        // m5.1 — TAB RESOURCE POLICY (the idle-CPU rule): a MINIMIZED sheet
        // must not keep burning CPU. Only Activity-level pause/resume and
        // tab switches parked WebViews before — minimizing left the ACTIVE
        // tab running JavaScript, timers and layout at full rate while
        // completely invisible under the workspace. Now: collapse → pause
        // EVERYTHING (fully reversible — onPause suspends timers/layout/
        // parsing and flushes cookies; no state is destroyed, no reload on
        // raise), raise → wake ONLY the active tab (the same §8 contract a
        // tab switch already uses). A drag back up crosses isRaised on the
        // way, so the page is alive again exactly when it becomes visible.
        LaunchedEffect(raised) {
            if (!raised) {
                KeyboardInputRouter.terminalTarget?.let { terminal ->
                    runCatching { terminal.requestFocus() }
                }
                CompanionWebHost.pauseAll()
            } else {
                CompanionWebHost.resumeActive()
            }
        }
    }
}

/**
 * The single drag affordance — no text, no label (brief R1).
 *
 * Phase 5 §1 — the visible bar is TWICE the width (72×4dp, still slim), the
 * invisible full-width touch zone stays 40dp of vertical drag area. A single
 * TAP on the bar (anywhere on the zone) minimizes the raised sheet at any
 * height; the height itself changes ONLY by dragging and settles exactly
 * where released (free positioning — no snap points).
 *
 * Layer placement guarantee: the zone is the FIRST child of the layer's
 * column — the tab strip and the web canvas sit strictly BELOW it, so no
 * sibling can ever cover it (it cannot hide behind tabs or content).
 */
@Composable
private fun CompanionHandle(
    dragging: Boolean,
    onTap: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val barColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (dragging) HomeTokens.accent else HomeTokens.hairline,
        animationSpec = tween(120, easing = FastOutSlowInEasing),
        label = "companionHandle",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(HANDLE_ZONE)
            // Tap and drag coexist on separate detectors: a gesture without
            // movement is a tap (minimize), a gesture past the touch slop is
            // a drag (resize). One clear behavior per gesture, no ambiguity.
            .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { onDragStart() },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 72.dp, height = 4.dp)
                .background(barColor, RoundedCornerShape(2.dp)),
        )
    }
}

/**
 * m4.1.0 — the live web canvas, the Phase 4.1 native host. The WebViews
 * live in ONE process-scoped plain FrameLayout ([CompanionWebHost.canvas]);
 * this composable never creates, swaps or destroys views itself — it hands
 * Compose the stable container and asks the host to put the active tab on
 * top. Exactly the proven baseline's shape, one layer down:
 *
 *   Activity → (Compose panel chrome) → plain FrameLayout → WebView(activity)
 *     → attach → first layout → loadUrl
 *
 * Everything the m4.0.9 control experiment proved unnecessary — config
 * contexts, UA spoofs, background overrides, keyed swap hosts, attach
 * kicks, watchdogs, retry ladders — simply does not exist here.
 */
@Composable
private fun CompanionWebCanvas(
    defId: String,
    url: String,
    webHeightPx: Float,
    failure: CompanionFailure?,
    retrySeed: Int,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    // The failure card comes FIRST — a renderer-gone tab has NO WebView,
    // and prepare() below would instantly re-create one behind the card.
    if (failure != null) {
        CompanionFailureCard(failure, url, onRetry, onDismiss)
        return
    }
    val context = LocalContext.current
    // The PROVEN constructor takes the real Activity — never a wrapper.
    val activity = remember(context) { context.activityOrNull() }
    if (activity == null) {
        Box(Modifier.fillMaxSize())
        return
    }
    // One WebView per tab, created with the baseline recipe (guarded; null
    // only when the provider itself is broken). retrySeed re-keys on Retry
    // so a destroyed WebView is re-created.
    val webView = remember(defId, retrySeed) {
        CompanionWebHost.prepare(defId, url, activity)
    }
    if (webView == null) {
        // m4.0.1 (kept): the provider itself is broken — say so honestly;
        // the terminal keeps working. Blank only while not yet initialized.
        if (CompanionWebHost.runtimeFailed) CompanionRuntimeUnavailable()
        else Box(Modifier.fillMaxSize())
        return
    }
    DisposableEffect(defId) {
        CompanionWebHost.setActive(defId)
        onDispose { }
    }
    AndroidView(
        // The SAME stable native container every time — panel collapse,
        // tab switches and retries never re-create or swap WebViews.
        factory = { CompanionWebHost.canvas(activity) },
        // Native view surgery (idempotent): puts this tab on top and
        // schedules its URL AFTER first layout — the proven sequence.
        update = { CompanionWebHost.present(defId, webView) },
        modifier = Modifier
            .fillMaxWidth()
            .height(with(LocalDensity.current) { webHeightPx.toDp() }),
    )
}

/** The real Activity behind any Compose context (wrappers included). */
private tailrec fun android.content.Context.activityOrNull(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activityOrNull()
    else -> null
}

/**
 * m4.0.3 — the Companion picker sheet behind the "+" button: a Midnight
 * panel listing every defined Companion (open ones marked), plus the
 * "Add Companion" entry into the management page. Scrim tap or Back
 * dismisses; the panel rides above the keyboard inset like the layer.
 */
@Composable
private fun CompanionPickerSheet(
    defs: List<CompanionDef>,
    openDefIds: Set<String>,
    activeId: String?,
    bottomModifier: Modifier,
    onSelect: (String) -> Unit,
    onAddNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .then(bottomModifier)
                .background(
                    TerminalTheme.deck,
                    RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                )
                .padding(top = 10.dp, bottom = 8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Companions",
                    fontFamily = TerminalTheme.mono,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.3.sp,
                    color = HomeTokens.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close",
                        tint = TerminalTheme.textDim,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            defs.forEach { def ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(def.id) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(TerminalTheme.keyActive, RoundedCornerShape(9.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = def.name.trim().take(1).uppercase().ifBlank { "?" },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TerminalTheme.accentBright,
                        )
                    }
                    Text(
                        text = def.name,
                        fontSize = 15.sp,
                        fontWeight = if (def.id == activeId) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (def.id == activeId) HomeTokens.textPrimary else HomeTokens.textDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                    )
                    if (def.id in openDefIds) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = "Open tab",
                            tint = TerminalTheme.accent,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAddNew() }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    tint = TerminalTheme.accentBright,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "Add Companion",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = TerminalTheme.accentBright,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}

/**
 * Empty state (brief §24): mono title, one dim line, one action. No cards,
 * no illustrations.
 */
@Composable
private fun CompanionEmptyState(
    hasDefinitions: Boolean,
    onAdd: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .background(TerminalTheme.canvas),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Text(
            text = "Your Companion",
            fontFamily = TerminalTheme.mono,
            fontSize = 19.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.4.sp,
            color = HomeTokens.onHero,
        )
        Text(
            text = "Add a website you use while working.",
            fontSize = 13.sp,
            color = HomeTokens.onHeroDim,
            modifier = Modifier.padding(top = 6.dp, bottom = 18.dp),
        )
        MidnightFilledButton(
            text = if (hasDefinitions) "Open Companion" else "+ Add Companion",
            onClick = if (hasDefinitions) onAdd else onOpenSettings,
            modifier = Modifier.padding(horizontal = 48.dp),
        )
    }
}

/**
 * m4.0.2 — an honest failure state: the canvas is NEVER mysteriously white
 * or black. Mono title, the real detail (error + installed WebView version),
 * one dim hint, and m4.0.4's three actions: Retry, plus the two escape
 * hatches — the same address in the device's real browser, or the raw
 * canvas as-is (the site's own consent banner lives there and may work).
 */
@Composable
private fun CompanionFailureCard(
    failure: CompanionFailure,
    url: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TerminalTheme.canvas),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Text(
            text = failure.title,
            fontFamily = TerminalTheme.mono,
            fontSize = 19.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.4.sp,
            color = HomeTokens.onHero,
        )
        if (failure.body.isNotBlank()) {
            Text(
                text = failure.body,
                fontSize = 13.sp,
                color = HomeTokens.onHeroDim,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .padding(horizontal = 32.dp),
            )
        }
        Text(
            text = failure.hint,
            fontSize = 13.sp,
            color = HomeTokens.onHeroDim,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .padding(top = 4.dp)
                .padding(horizontal = 32.dp),
        )
        MidnightFilledButton(
            text = "Retry",
            onClick = onRetry,
            modifier = Modifier.padding(top = 18.dp),
        )
        Row(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text(
                text = "Open in browser",
                fontSize = 13.sp,
                color = HomeTokens.textDim,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (_: Exception) {
                            // No browser on the device — the card stays; honest.
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
            Text(
                text = "Continue anyway",
                fontSize = 13.sp,
                color = HomeTokens.textDim,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onDismiss() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * m4.0.1 — the WebView provider itself is broken on this device. Same
 * minimal language as the empty state (mono title, one dim line); the
 * message names the actual system component and the way out. No cards,
 * no crash — the rest of PocketShell is unaffected.
 */
@Composable
private fun CompanionRuntimeUnavailable() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .background(TerminalTheme.canvas),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Text(
            text = "Companion unavailable",
            fontFamily = TerminalTheme.mono,
            fontSize = 19.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.4.sp,
            color = HomeTokens.onHero,
        )
        val webviewVersion = remember { CompanionWebHost.webViewVersion() }
        Text(
            text = "Android System WebView is missing or crashing on this device " +
                "(installed: $webviewVersion). Update or reinstall it, then reopen PocketShell.",
            fontSize = 13.sp,
            color = HomeTokens.onHeroDim,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .padding(top = 6.dp)
                .padding(horizontal = 32.dp),
        )
    }
}

private val HANDLE_ZONE = 40.dp
private val STRIP_HEIGHT = 40.dp

package app.pocketshell.ui.companion

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
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pocketshell.companion.CompanionBackAction
import app.pocketshell.companion.CompanionDef
import app.pocketshell.companion.CompanionFailure
import app.pocketshell.companion.CompanionHeights
import app.pocketshell.companion.CompanionWebPool
import app.pocketshell.companion.CompanionViewModel
import app.pocketshell.companion.decideBackAction
import app.pocketshell.ui.home.HomeTokens
import app.pocketshell.ui.system.MidnightFilledButton
import app.pocketshell.ui.theme.TerminalTheme

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

    // m4.0.2: bumped by Retry — re-keys the web host's remember so acquire()
    // runs again on a freshly created WebView.
    var webRetrySeed by remember { mutableStateOf(0) }

    // m4.0.3: per-tab compatibility-render flag. Retry toggles it — the
    // second attempt creates the WebView with LAYER_TYPE_SOFTWARE, the
    // escape hatch for devices whose GPU path paints nothing.
    val compatRenders = remember { mutableStateMapOf<String, Boolean>() }

    // m4.0.3: the "+" affordance now opens the Companion picker sheet.
    var pickerOpen by remember { mutableStateOf(false) }

    // In-flight drag fraction; null when the pointer is up (settled state).
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val currentFraction = dragFraction ?: settledFraction
    val raised = CompanionHeights.isRaised(currentFraction)

    val activeTab = tabs.firstOrNull { it.defId == activeTabId }
    val activeDef = defs.firstOrNull { it.id == activeTabId }

    // ---- pool wiring (listener + file chooser + lifecycle) -----------------

    DisposableEffect(viewModel) {
        CompanionWebPool.setListener(object : CompanionWebPool.Listener {
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

            override fun onRenderStuck(defId: String) {
                viewModel.recordRenderStalled(defId)
            }
        })
        onDispose { CompanionWebPool.setListener(null) }
    }

    var chooserCallback by remember { mutableStateOf<((Uri?) -> Unit)?>(null) }
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        chooserCallback?.invoke(uri)
        chooserCallback = null
    }
    DisposableEffect(Unit) {
        CompanionWebPool.setFileChooserHost(object : CompanionWebPool.FileChooserHost {
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
        onDispose { CompanionWebPool.setFileChooserHost(null) }
    }

    // Activity pause/resume: park everything / wake the active tab (§8).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> CompanionWebPool.pauseAll()
                Lifecycle.Event.ON_RESUME -> CompanionWebPool.resumeActive()
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

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .then(bottomModifier),
        ) {
            CompanionHandle(
                dragging = dragFraction != null,
                onDragStart = { dragFraction = settledFraction.takeIf { CompanionHeights.isRaised(it) } ?: 0f },
                onDrag = { delta ->
                    val base = dragFraction ?: settledFraction
                    dragFraction = (base - delta / containerHeightPx).coerceIn(0f, CompanionHeights.FULL)
                },
                onDragEnd = {
                    val released = dragFraction
                    dragFraction = null
                    if (released != null) viewModel.settleHeight(released)
                },
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
                    CompanionTabStrip(
                        tabs = tabs,
                        defs = defs,
                        activeId = activeTabId,
                        onSelect = viewModel::selectTab,
                        onClose = viewModel::closeTab,
                        // m4.0.3: "+" opens the picker sheet (list + add),
                        // it no longer silently opens the default tab.
                        onAdd = { pickerOpen = true },
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(with(density) { webHeightPx.toDp() })
                            .clipToBounds()
                            .background(TerminalTheme.canvas),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        CompanionWebHost(
                            defId = activeDef.id,
                            url = activeTab?.lastUrl ?: activeDef.url,
                            // Frozen measured height during drag (§9).
                            webHeightPx = if (dragFraction != null) settledWebPx else webHeightPx,
                            failure = pageFailures[activeDef.id],
                            retrySeed = webRetrySeed,
                            compatRender = compatRenders[activeDef.id] ?: false,
                            onRetry = {
                                viewModel.retryTab(activeDef.id)
                                // m4.0.3: each Retry alternates GPU → software
                                // rendering (then back) on the fresh WebView.
                                compatRenders[activeDef.id] =
                                    !(compatRenders[activeDef.id] ?: false)
                                webRetrySeed++
                            },
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
            when (decideBackAction(CompanionWebPool.canGoBack(activeTabId), raised)) {
                CompanionBackAction.WEB_BACK -> CompanionWebPool.goBack(activeTabId)
                CompanionBackAction.COLLAPSE -> viewModel.collapse()
                CompanionBackAction.PASS_THROUGH -> {}
            }
        }

        // m4.0.3: the picker is the topmost surface — Back closes it first.
        BackHandler(enabled = pickerOpen) { pickerOpen = false }
    }
}

/** The single drag affordance — no text, no label (brief R1). */
@Composable
private fun CompanionHandle(
    dragging: Boolean,
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
                .size(width = 36.dp, height = 4.dp)
                .background(barColor, RoundedCornerShape(2.dp)),
        )
    }
}

/** The live WebView host — the invisible engine (brief R3). */
@Composable
private fun CompanionWebHost(
    defId: String,
    url: String,
    webHeightPx: Float,
    failure: CompanionFailure?,
    retrySeed: Int,
    compatRender: Boolean,
    onRetry: () -> Unit,
) {
    // m4.0.2: the failure card comes FIRST — a renderer-gone tab has NO
    // WebView in the pool, and acquire() below would instantly re-create
    // one behind the card.
    if (failure != null) {
        CompanionFailureCard(failure, onRetry)
        return
    }
    // m4.0.3: creation uses the ACTIVITY context — the application context
    // used since m4.0 is a documented source of blank-canvas WebViews.
    val context = LocalContext.current
    // One WebView per definition, owned by the pool; (re)attached here.
    // retrySeed re-keys on Retry so a destroyed WebView is re-created.
    val webView = remember(defId, retrySeed) {
        CompanionWebPool.acquire(defId, url, context, compatRender)
    }
    DisposableEffect(defId) {
        CompanionWebPool.setActive(defId)
        onDispose { }
    }
    if (webView == null) {
        // m4.0.1: the provider itself is broken (missing/crashing WebView
        // package) — say so honestly; the terminal keeps working. Blank
        // only for the not-yet-initialized case.
        if (CompanionWebPool.runtimeFailed) CompanionRuntimeUnavailable()
        else Box(Modifier.fillMaxSize())
        return
    }
    AndroidView(
        factory = { webView },
        modifier = Modifier
            .fillMaxWidth()
            .height(with(LocalDensity.current) { webHeightPx.toDp() }),
    )
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
            color = HomeTokens.textPrimary,
        )
        Text(
            text = "Add a website you use while working.",
            fontSize = 13.sp,
            color = HomeTokens.textDim,
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
 * m4.0.2 — an honest failure state: the canvas is NEVER mysteriously white.
 * Mono title, the real detail (error + installed WebView version), one dim
 * hint, one action. Same language as the empty state.
 */
@Composable
private fun CompanionFailureCard(
    failure: CompanionFailure,
    onRetry: () -> Unit,
) {
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
            color = HomeTokens.textPrimary,
        )
        if (failure.body.isNotBlank()) {
            Text(
                text = failure.body,
                fontSize = 13.sp,
                color = HomeTokens.textDim,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .padding(horizontal = 32.dp),
            )
        }
        Text(
            text = failure.hint,
            fontSize = 13.sp,
            color = HomeTokens.textDim,
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
            color = HomeTokens.textPrimary,
        )
        val webviewVersion = remember { CompanionWebPool.webViewVersion() }
        Text(
            text = "Android System WebView is missing or crashing on this device " +
                "(installed: $webviewVersion). Update or reinstall it, then reopen PocketShell.",
            fontSize = 13.sp,
            color = HomeTokens.textDim,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .padding(top = 6.dp)
                .padding(horizontal = 32.dp),
        )
    }
}

private val HANDLE_ZONE = 28.dp
private val STRIP_HEIGHT = 40.dp

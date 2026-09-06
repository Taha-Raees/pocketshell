package app.pocketshell

import android.app.Activity
import android.content.ComponentCallbacks2
import android.os.Build
import android.os.Bundle
import android.view.WindowInsets
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.pocketshell.companion.CompanionWebHost
import app.pocketshell.keyboard.KeyboardInputRouter
import app.pocketshell.keyboard.KeyboardState
import app.pocketshell.keyboard.TerminalKeyDispatcher
import app.pocketshell.keyboard.TerminalKeyboardDeck
import app.pocketshell.settings.ThemeMode
import app.pocketshell.ui.apps.ExploreAppsScreen
import app.pocketshell.ui.diagnostics.DiagnosticsScreen
import app.pocketshell.ui.home.HomeScreen
import app.pocketshell.ui.settings.SettingsScreen
import app.pocketshell.ui.terminal.TerminalScreen
import app.pocketshell.ui.theme.PocketShellTheme
import app.pocketshell.ui.theme.TerminalTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // PocketShell keyboard is THE keyboard, everywhere (m4.0.12 §6/§11):
        // the system IME is hard-blocked for the whole window for the entire
        // lifetime of the app. FLAG_ALT_FOCUSABLE_IM makes the window itself
        // not IME-targetable — the Android/Samsung keyboard can never appear,
        // not over the terminal, not over a Companion WebView input, not over
        // any Compose text field. The shared PocketShell deck serves every
        // typeable surface through the input router (its presses are real
        // KeyEvents to the focused view — no JavaScript, no IME hacks).
        window.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        )
        window.setFlags(
            WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
            WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
        )
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.hide(WindowInsets.Type.ime())
        }
        setContent {
            val settingsViewModel: SettingsViewModel = viewModel()
            val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
            val dynamicColor by settingsViewModel.dynamicColor.collectAsStateWithLifecycle()
            val defaultFontSize by settingsViewModel.defaultFontSize.collectAsStateWithLifecycle()

            PocketShellTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
                PocketShellRoot(
                    terminalViewModel = viewModel(),
                    settingsViewModel = settingsViewModel,
                    defaultFontSize = defaultFontSize,
                )
            }
        }
    }

    /**
     * m5.1 — memory-pressure hook: when the system reports real pressure,
     * cookie/session state is persisted deterministically (the same flush
     * the Activity ON_PAUSE path already performs). Strictly gated on
     * [CompanionWebHost.hasLiveTabs] so a device whose WebView provider was
     * never touched (the m4.0.1 startup rule) is never forced to load it
     * here; the call itself is contained — a broken provider can slow a
     * flush, never kill the process.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW &&
            CompanionWebHost.hasLiveTabs()
        ) {
            runCatching {
                android.webkit.CookieManager.getInstance().flush()
            }
        }
    }
}

@Composable
fun PocketShellRoot(
    terminalViewModel: TerminalViewModel,
    settingsViewModel: SettingsViewModel,
    defaultFontSize: Int,
) {
    // keyboardState lives at root so the state survives screen switches while
    // remaining per-process (cleared on session switch inside TerminalScreen).
    val keyboardState = remember { KeyboardState() }
    var screen by rememberSaveable { mutableStateOf("home") }

    // m4.0.3/m4.0.12 — the shared keyboard is a ROOT concern: ONE deck for
    // the whole app (terminal, Companion over any screen, Compose text
    // fields). Its visibility (the [⌨] toggle unmounts the WHOLE deck; a
    // small floating icon brings it back — on every screen now) and its
    // measured height (the Companion layer and every screen push themselves
    // ABOVE the deck — the keyboard never has anything underneath it).
    var keyboardExpanded by rememberSaveable { mutableStateOf(true) }
    var keyboardInsetPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val keyboardInset = with(density) { keyboardInsetPx.toDp() }

    // The deck unmounts without a size callback — clear the contributed
    // inset explicitly (same rule as before, now at root level).
    LaunchedEffect(keyboardExpanded) {
        if (!keyboardExpanded) keyboardInsetPx = 0
    }

    // m4.0.12 §13 — focus follows input: when a Companion WebView input
    // gains focus, the ONE keyboard opens for it automatically. (The
    // terminal canvas already opens it via its tap client.)
    DisposableEffect(Unit) {
        KeyboardInputRouter.onWebFocusGained = { keyboardExpanded = true }
        onDispose { KeyboardInputRouter.onWebFocusGained = null }
    }

    // Phase 4: created once here so Companion state (definitions, tabs,
    // height, the in-process WebView pool's identity) survives screen switches.
    val companionViewModel: app.pocketshell.companion.CompanionViewModel = viewModel()

    // M7 Phase 3: the explorer's location/state is process-scoped too —
    // Home↔Files round-trips and rotation never reset the user's directory.
    val filesViewModel: app.pocketshell.FilesViewModel = viewModel()
    val filesState by filesViewModel.state.collectAsStateWithLifecycle()

    // M7 Phase 6: the quick text editor is process-scoped as well — its
    // loaded document and dirty buffer survive Home↔Editor navigation and
    // rotation (the back guard keeps the screen from ever being left dirty;
    // process death losing unsaved content is stated honestly in the guard).
    val editorViewModel: app.pocketshell.EditorViewModel = viewModel()
    val editorState by editorViewModel.state.collectAsStateWithLifecycle()

    val sessions by terminalViewModel.sessions.collectAsStateWithLifecycle()
    val creating by terminalViewModel.creating.collectAsStateWithLifecycle()
    val selectedId by terminalViewModel.selectedId.collectAsStateWithLifecycle()
    val runtimeState by terminalViewModel.runtimeState.collectAsStateWithLifecycle()
    val launchError by terminalViewModel.launchError.collectAsStateWithLifecycle()

    val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by settingsViewModel.dynamicColor.collectAsStateWithLifecycle()

    // Phase 3.2 / Phase 5 §8 — status-bar icon appearance follows the THEME:
    // light icons on the dark palettes, dark icons on Daylight. The Midnight
    // chrome no longer owns every screen — the chrome follows the selected
    // theme now, and the status bar follows the chrome.
    val view = LocalView.current
    val themeDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
    }
    DisposableEffect(screen, themeDark) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (controller != null) {
            controller.isAppearanceLightStatusBars = !themeDark
        }
        onDispose { }
    }

    // m4.0.12 — one-keyboard policy, unconditional: the deck is present on
    // EVERY screen now, so the system IME block is permanent (set once in
    // onCreate) and no longer toggled by screen or deck state. Deck presses
    // reach the focused surface — Companion WebView, terminal canvas, or
    // any focused Compose text field — through the universal dispatch chain
    // below (real KeyEvents; no JavaScript, no IME round-trip).
    val dispatcher = remember(keyboardState) {
        TerminalKeyDispatcher(keyboardState) { event ->
            val routed = KeyboardInputRouter.resolve()
                ?: (view.context as? Activity)?.currentFocus
            routed?.dispatchKeyEvent(event)
        }
    }

    BackHandler(enabled = screen != "home") { screen = "home" }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        when (screen) {
            "terminal" -> TerminalScreen(
                sessions = sessions,
                selectedId = selectedId,
                keyboardState = keyboardState,
                creating = creating,
                initialFontSize = defaultFontSize,
                keyboardExpanded = keyboardExpanded,
                onKeyboardExpandedChange = { keyboardExpanded = it },
                keyboardBottomInset = keyboardInset,
                onSelect = terminalViewModel::select,
                onClose = terminalViewModel::closeSession,
                // p7.1: the "+" adds a session matching the CURRENT session's
                // environment — a Linux shell while the user is in a Linux
                // session (canonical openLinuxShell path), the historical
                // Android shell otherwise. The kind decision lives in the
                // ViewModel (spawn-time registration + the pinned guest
                // label); the screen stays intent-only.
                onNewSession = { terminalViewModel.newSessionMatchingCurrent() },
                onBack = { screen = "home" },
                // Phase 3.1: the Terminal screen consumes the system-bar insets
                // itself so its Midnight chrome extends edge-to-edge (the deck
                // pads for the gesture bar). Every other screen keeps the
                // Scaffold padding.
                modifier = Modifier,
            )

            "explore" -> ExploreAppsScreen(
                terminalViewModel = terminalViewModel,
                onBack = { screen = "home" },
                onOpenedSession = { screen = "terminal" },
                // Phase 3.4: the not-ready state carries a real affordance —
                // the runtime is installed from Diagnostics.
                onOpenDiagnostics = { screen = "diagnostics" },
                modifier = Modifier.padding(padding),
            )

            "files" -> app.pocketshell.ui.files.FilesScreen(
                state = filesState,
                guestUnavailable = filesViewModel.guestUnavailable,
                ops = filesViewModel,
                onBack = { screen = "home" },
                onNavigateUp = filesViewModel::navigateUp,
                onOpenChild = filesViewModel::openChild,
                onSwitchArea = filesViewModel::switchArea,
                onRefresh = filesViewModel::refresh,
                onOpenFile = { name ->
                    // Validate + resolve here; navigate only when the listing
                    // entry genuinely launches (never over a fake open).
                    val launch = filesViewModel.editorLaunch(name)
                    if (launch != null) {
                        editorViewModel.open(launch)
                        screen = "editor"
                    }
                },
                onOpenTerminal = { selectedEntry ->
                    // Phase 7 "Open Terminal Here" (p7.1): resolve the TAPPED
                    // directory entry through the ops surface (pure area gate
                    // + the ONE validated child composition — no I/O, no
                    // session creation here). On Ready the TerminalViewModel
                    // spawns a NORMAL Alpine session IN THAT FOLDER and only
                    // its onReady navigates — never "navigate first and hope".
                    // NotSupported keeps the user in Files with the honest
                    // boundary notice already surfaced by the view model.
                    when (val resolution = filesViewModel.terminalLaunch(selectedEntry)) {
                        is app.pocketshell.files.TerminalLaunchResolution.Ready ->
                            terminalViewModel.openLinuxShellAt(
                                resolution.launch.directory.value,
                            ) { screen = "terminal" }
                        is app.pocketshell.files.TerminalLaunchResolution.NotSupported -> {
                            // Honest refusal — stay in Files.
                        }
                    }
                },
                onOpenDiagnostics = { screen = "diagnostics" },
                modifier = Modifier.padding(padding),
            )

            "editor" -> app.pocketshell.ui.files.EditorScreen(
                state = editorState,
                surface = editorViewModel,
                // The deck is mounted at root over EVERY screen; the editor
                // body clears it the same way the Terminal screen does.
                keyboardBottomInset = keyboardInset,
                onBack = { screen = "home" },
                modifier = Modifier.padding(padding),
            )

            "settings" -> SettingsScreen(
                themeMode = themeMode,
                dynamicColor = dynamicColor,
                defaultFontSize = defaultFontSize,
                onThemeMode = settingsViewModel::setThemeMode,
                onDynamicColor = settingsViewModel::setDynamicColor,
                onFontSize = settingsViewModel::setDefaultFontSize,
                onOpenCompanions = { screen = "companionSettings" },
                onBack = { screen = "home" },
                modifier = Modifier.padding(padding),
            )

            "companionSettings" -> app.pocketshell.ui.companion.CompanionSettingsScreen(
                viewModel = companionViewModel,
                onBack = { screen = "settings" },
                modifier = Modifier.padding(padding),
            )

            "diagnostics" -> DiagnosticsScreen(
                onBack = { screen = "home" },
                modifier = Modifier.padding(padding),
            )

            else -> HomeScreen(
                terminalViewModel = terminalViewModel,
                activeSessions = sessions,
                runtimeState = runtimeState,
                launchError = launchError,
                onDismissLaunchError = terminalViewModel::dismissLaunchError,
                onOpenTerminal = {
                    // Navigate only on a real spawn: a refused launch is
                    // surfaced honestly on Home (launchError), never fatal.
                    if (terminalViewModel.openTerminal()) screen = "terminal"
                },
                onNewTerminal = {
                    // Quick action: always a FRESH session (never reuse).
                    if (terminalViewModel.newSession()) screen = "terminal"
                },
                onOpenLinuxShell = {
                    // Async since the M6 Phase-C audit: heavy guest prep runs
                    // on IO; navigate only when a real session was created.
                    terminalViewModel.openLinuxShell { screen = "terminal" }
                },
                onOpenSession = { id ->
                    terminalViewModel.select(id)
                    screen = "terminal"
                },
                onOpenCommandApp = { app ->
                    // Verify-then-launch inside the ViewModel; navigate only
                    // when a real session was created (refusal = honest banner).
                    terminalViewModel.openCommandApp(app) { screen = "terminal" }
                },
                onExplorePackages = { screen = "explore" },
                onOpenFiles = { screen = "files" },
                onOpenSettings = { screen = "settings" },
                onOpenDiagnostics = { screen = "diagnostics" },
                // Phase 3.2: Home is an edge-to-edge launcher — it consumes
                // the status-bar inset itself (like the Terminal branch).
                modifier = Modifier,
            )
        }

        // Phase 4 — the Companion layer: a persistent workspace surface
        // overlaying EVERY screen, resized only by its bottom handle.
        // Composed after the screens so its BackHandler (web history →
        // collapse → screen navigation) is registered last.
        app.pocketshell.ui.companion.CompanionLayer(
            viewModel = companionViewModel,
            onOpenCompanionSettings = { screen = "companionSettings" },
            // m4.0.3: the deck's measured height — the Companion panel and
            // its picker sheet push themselves ABOVE the keyboard.
            keyboardBottomInset = keyboardInset,
        )

        // m4.0.12 §12 (device feedback carried from m4.0.4) — the deck's
        // rebirth affordance, now on EVERY screen: with the keyboard toggled
        // off the [⌨] key parks as the SAME rectangular key box it wears in
        // the deck row (no round bubble). Phase 5 §4 — it is ANCHORED to the
        // bottom-right corner: 12dp from the right edge, 8dp above the
        // gesture-bar inset, still a 44×36dp touch target, never clipped,
        // never over the system navigation.
        if (!keyboardExpanded) {
            Box(modifier = Modifier.fillMaxSize()) {
                val haptics = LocalHapticFeedback.current
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(end = 12.dp, bottom = 8.dp)
                        .size(width = 44.dp, height = 36.dp)
                        .clip(RoundedCornerShape(TerminalTheme.keyRadius))
                        .background(TerminalTheme.keyAlt)
                        .border(1.dp, TerminalTheme.divider, RoundedCornerShape(TerminalTheme.keyRadius))
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            keyboardExpanded = true
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Keyboard,
                        contentDescription = "Show keyboard",
                        tint = TerminalTheme.textDim,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }

        // m4.0.12 §6/§8/§15 — the ONE keyboard, at the root: the deck now
        // exists over EVERY screen (Terminal, Linux, CLI Apps, Home, and the
        // Companion over all of them). Bottom-anchored and topmost; screens
        // and the Companion panel end ABOVE it via the reported inset. Deck
        // presses flow through the dispatcher to the focused surface: the
        // Companion WebView (ChatGPT/Z.ai inputs), the terminal canvas, or
        // any focused Compose text field (Companion settings, and any
        // future app-level input) — one keyboard, one layout, one experience.
        if (keyboardExpanded) {
            TerminalKeyboardDeck(
                keyboardState = keyboardState,
                dispatcher = dispatcher,
                expanded = true,
                onToggleExpanded = { keyboardExpanded = false },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { keyboardInsetPx = it.height },
            )
        }
    }
    }
}


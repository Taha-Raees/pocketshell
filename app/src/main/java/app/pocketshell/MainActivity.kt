package app.pocketshell

import android.app.Activity
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.pocketshell.keyboard.KeyboardState
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
        // PocketShell keyboard is the primary input (brief §7) — never pop the
        // system IME automatically; it stays hidden unless the user is in a
        // context that explicitly needs it.
        window.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        )
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

    // m4.0.3 — the shared keyboard is a root concern: its visibility (the
    // [⌨] toggle unmounts the WHOLE deck; a small floating icon brings it
    // back) and its measured height (reported by TerminalScreen so the
    // Companion layer pushes itself ABOVE the deck — the keyboard never has
    // anything underneath it).
    var keyboardExpanded by rememberSaveable { mutableStateOf(true) }
    var keyboardInsetPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val keyboardInset = with(density) { keyboardInsetPx.toDp() }

    // Leaving the terminal screen unmounts the deck without a size callback
    // — clear the contributed inset explicitly.
    LaunchedEffect(screen) {
        if (screen != "terminal") keyboardInsetPx = 0
    }

    // Phase 4: created once here so Companion state (definitions, tabs,
    // height, the in-process WebView pool's identity) survives screen switches.
    val companionViewModel: app.pocketshell.companion.CompanionViewModel = viewModel()

    val sessions by terminalViewModel.sessions.collectAsStateWithLifecycle()
    val creating by terminalViewModel.creating.collectAsStateWithLifecycle()
    val selectedId by terminalViewModel.selectedId.collectAsStateWithLifecycle()
    val runtimeState by terminalViewModel.runtimeState.collectAsStateWithLifecycle()
    val launchError by terminalViewModel.launchError.collectAsStateWithLifecycle()

    val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by settingsViewModel.dynamicColor.collectAsStateWithLifecycle()

    // Phase 3.2 — status-bar icon appearance follows the SURFACE under the
    // bar: since Phase 3.5 every screen renders the fixed Midnight Sapphire
    // chrome (Home, Terminal, and the system pages alike), so every screen
    // takes light icons; the when remains for any future app-themed screen.
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
            controller.isAppearanceLightStatusBars = when (screen) {
                "home", "terminal", "explore", "settings", "diagnostics" -> false
                else -> !themeDark
            }
        }
        onDispose { }
    }

    // m4.0.3 — one-keyboard policy: while the PocketShell deck is the
    // keyboard (terminal screen, expanded), the system IME is hard-blocked
    // for the window; deck presses already reach the focused surface
    // (terminal canvas OR Companion WebView). With the deck toggled off the
    // block is lifted so Companion inputs can still summon the system IME.
    val imeBlocked = screen == "terminal" && keyboardExpanded
    DisposableEffect(imeBlocked) {
        val window = (view.context as? Activity)?.window
        if (imeBlocked) {
            window?.setFlags(
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
            )
            if (Build.VERSION.SDK_INT >= 30) {
                window?.insetsController?.hide(WindowInsets.Type.ime())
            }
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        }
        onDispose { }
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
                onKeyboardInsetChanged = { keyboardInsetPx = it },
                onSelect = terminalViewModel::select,
                onClose = terminalViewModel::closeSession,
                onNewSession = terminalViewModel::newSession,
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
                    if (terminalViewModel.openLinuxShell()) screen = "terminal"
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

        // m4.0.3 — the deck's rebirth affordance: with the keyboard toggled
        // OFF, a small Midnight icon floats at the bottom-right corner —
        // above the Companion layer — to bring it back at any time.
        if (screen == "terminal" && !keyboardExpanded) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(14.dp)
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(TerminalTheme.deck.copy(alpha = 0.94f))
                        .border(1.dp, TerminalTheme.divider, CircleShape)
                        .clickable { keyboardExpanded = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Keyboard,
                        contentDescription = "Show keyboard",
                        tint = TerminalTheme.accentBright,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
    }
}


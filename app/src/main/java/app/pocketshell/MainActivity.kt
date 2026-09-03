package app.pocketshell

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.pocketshell.keyboard.KeyboardState
import app.pocketshell.ui.apps.ExploreAppsScreen
import app.pocketshell.ui.diagnostics.DiagnosticsScreen
import app.pocketshell.ui.home.HomeScreen
import app.pocketshell.ui.settings.SettingsScreen
import app.pocketshell.ui.terminal.TerminalScreen
import app.pocketshell.ui.theme.PocketShellTheme

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

    val sessions by terminalViewModel.sessions.collectAsStateWithLifecycle()
    val creating by terminalViewModel.creating.collectAsStateWithLifecycle()
    val selectedId by terminalViewModel.selectedId.collectAsStateWithLifecycle()
    val runtimeState by terminalViewModel.runtimeState.collectAsStateWithLifecycle()
    val launchError by terminalViewModel.launchError.collectAsStateWithLifecycle()

    val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by settingsViewModel.dynamicColor.collectAsStateWithLifecycle()

    BackHandler(enabled = screen != "home") { screen = "home" }

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        when (screen) {
            "terminal" -> TerminalScreen(
                sessions = sessions,
                selectedId = selectedId,
                keyboardState = keyboardState,
                creating = creating,
                initialFontSize = defaultFontSize,
                onSelect = terminalViewModel::select,
                onClose = terminalViewModel::closeSession,
                onNewSession = terminalViewModel::newSession,
                onBack = { screen = "home" },
                modifier = Modifier.padding(padding),
            )

            "explore" -> ExploreAppsScreen(
                terminalViewModel = terminalViewModel,
                onBack = { screen = "home" },
                onOpenedSession = { screen = "terminal" },
                modifier = Modifier.padding(padding),
            )

            "settings" -> SettingsScreen(
                themeMode = themeMode,
                dynamicColor = dynamicColor,
                defaultFontSize = defaultFontSize,
                onThemeMode = settingsViewModel::setThemeMode,
                onDynamicColor = settingsViewModel::setDynamicColor,
                onFontSize = settingsViewModel::setDefaultFontSize,
                onBack = { screen = "home" },
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
                onOpenLinuxShell = {
                    if (terminalViewModel.openLinuxShell()) screen = "terminal"
                },
                onOpenSession = { id ->
                    terminalViewModel.select(id)
                    screen = "terminal"
                },
                onOpenedSession = { screen = "terminal" },
                onExploreApps = { screen = "explore" },
                onOpenSettings = { screen = "settings" },
                onOpenDiagnostics = { screen = "diagnostics" },
                modifier = Modifier.padding(padding),
            )
        }
    }
}

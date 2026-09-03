package app.pocketshell

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DismissibleNavigationDrawer
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.pocketshell.keyboard.KeyboardState
import app.pocketshell.ui.apps.PackagesScreen
import app.pocketshell.ui.apps.LaunchableAppsScreen
import app.pocketshell.ui.components.PSNavDrawerContent
import app.pocketshell.ui.diagnostics.DiagnosticsScreen
import app.pocketshell.ui.home.HomeScreen
import app.pocketshell.ui.navigation.Screen
import app.pocketshell.ui.settings.SettingsScreen
import app.pocketshell.ui.terminal.TerminalScreen
import app.pocketshell.ui.theme.PocketShellTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // The Android IME is toggled explicitly by the keyboard accessory row
        // (docs/UI-REDESIGN.md §7); the window never pops it on its own.
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
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val sessions by terminalViewModel.sessions.collectAsStateWithLifecycle()
    val creating by terminalViewModel.creating.collectAsStateWithLifecycle()
    val selectedId by terminalViewModel.selectedId.collectAsStateWithLifecycle()
    val runtimeState by terminalViewModel.runtimeState.collectAsStateWithLifecycle()
    val launchError by terminalViewModel.launchError.collectAsStateWithLifecycle()

    val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by settingsViewModel.dynamicColor.collectAsStateWithLifecycle()
    val aiKey by settingsViewModel.openRouterApiKey.collectAsStateWithLifecycle()
    val aiModel by settingsViewModel.openRouterModel.collectAsStateWithLifecycle()

    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }
    val closeDrawer: () -> Unit = { scope.launch { drawerState.close() } }

    fun navigate(destination: Screen) {
        screen = destination
    }

    // Drawer entries that SPAWN rather than just switch: refusals land in the
    // Home launch-error banner, so a refused spawn navigates Home to show it.
    fun navigateTerminalAfterSpawn(spawn: () -> Boolean) {
        if (spawn()) screen = Screen.TERMINAL else screen = Screen.HOME
    }

    val onDrawerNavigate: (Screen) -> Unit = { destination ->
        closeDrawer()
        when (destination) {
            Screen.TERMINAL -> navigateTerminalAfterSpawn { terminalViewModel.openTerminal() }
            else -> navigate(destination)
        }
    }

    val onDrawerLinuxShell: () -> Unit = {
        closeDrawer()
        navigateTerminalAfterSpawn { terminalViewModel.openLinuxShell() }
    }

    BackHandler(enabled = drawerState.isOpen || screen != Screen.HOME) {
        if (drawerState.isOpen) closeDrawer() else screen = Screen.HOME
    }

    DismissibleNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            PSNavDrawerContent(
                current = screen,
                onNavigate = onDrawerNavigate,
                onLinuxShell = onDrawerLinuxShell,
                runtimeStatusLabel = runtimeState.name,
                runtimeStatusOk = runtimeState == app.pocketshell.runtime.RuntimeState.READY,
                drawerState = drawerState,
            )
        },
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
            when (screen) {
                Screen.TERMINAL -> TerminalScreen(
                    sessions = sessions,
                    selectedId = selectedId,
                    keyboardState = keyboardState,
                    creating = creating,
                    initialFontSize = defaultFontSize,
                    onSelect = terminalViewModel::select,
                    onClose = terminalViewModel::closeSession,
                    onNewSession = terminalViewModel::newSession,
                    onMenu = openDrawer,
                    modifier = Modifier.padding(padding),
                )

                Screen.PACKAGES -> PackagesScreen(
                    terminalViewModel = terminalViewModel,
                    onMenu = openDrawer,
                    onOpenedSession = { screen = Screen.TERMINAL },
                    modifier = Modifier.padding(padding),
                )

                Screen.APPS -> LaunchableAppsScreen(
                    terminalViewModel = terminalViewModel,
                    runtimeState = runtimeState,
                    onMenu = openDrawer,
                    onOpenedSession = { screen = Screen.TERMINAL },
                    onOpenPackages = { screen = Screen.PACKAGES },
                    modifier = Modifier.padding(padding),
                )

                Screen.SETTINGS -> SettingsScreen(
                    themeMode = themeMode,
                    dynamicColor = dynamicColor,
                    defaultFontSize = defaultFontSize,
                    onThemeMode = settingsViewModel::setThemeMode,
                    onDynamicColor = settingsViewModel::setDynamicColor,
                    onFontSize = settingsViewModel::setDefaultFontSize,
                    onMenu = openDrawer,
                    openRouterApiKey = aiKey,
                    openRouterModel = aiModel,
                    onSaveApiKey = settingsViewModel::setOpenRouterApiKey,
                    onSaveModel = settingsViewModel::setOpenRouterModel,
                    modifier = Modifier.padding(padding),
                )

                Screen.DIAGNOSTICS -> DiagnosticsScreen(
                    onMenu = openDrawer,
                    modifier = Modifier.padding(padding),
                )

                Screen.HOME -> HomeScreen(
                    terminalViewModel = terminalViewModel,
                    activeSessions = sessions,
                    runtimeState = runtimeState,
                    launchError = launchError,
                    onDismissLaunchError = terminalViewModel::dismissLaunchError,
                    onMenu = openDrawer,
                    onOpenTerminal = {
                        // Navigate only on a real spawn: a refused launch is
                        // surfaced honestly on Home (launchError), never fatal.
                        if (terminalViewModel.openTerminal()) screen = Screen.TERMINAL
                    },
                    onOpenLinuxShell = {
                        if (terminalViewModel.openLinuxShell()) screen = Screen.TERMINAL
                    },
                    onOpenSession = { id ->
                        terminalViewModel.select(id)
                        screen = Screen.TERMINAL
                    },
                    onOpenApps = { screen = Screen.APPS },
                    onOpenPackages = { screen = Screen.PACKAGES },
                    onOpenDiagnostics = { screen = Screen.DIAGNOSTICS },
                    onOpenAiSettings = { screen = Screen.SETTINGS },
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

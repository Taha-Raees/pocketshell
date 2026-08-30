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
import app.pocketshell.ui.home.HomeScreen
import app.pocketshell.ui.terminal.TerminalScreen
import app.pocketshell.ui.theme.PocketShellTheme

/** Top-level screens. Deliberately tiny — no nav library needed at this size. */
sealed interface Screen {
    data object Home : Screen
    data object Terminal : Screen
    data object Explore : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PocketShellTheme {
                PocketShellRoot()
            }
        }
    }
}

@Composable
fun PocketShellRoot(viewModel: TerminalViewModel = viewModel()) {
    // keyboardState lives at root so the state survives screen switches while
    // remaining per-process (cleared on session switch inside TerminalScreen).
    val keyboardState = remember { KeyboardState() }
    var screen by rememberSaveable { mutableStateOf("home") }

    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val creating by viewModel.creating.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedId.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()

    BackHandler(enabled = screen != "home") { screen = "home" }

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        when (screen) {
            "terminal" -> TerminalScreen(
                sessions = sessions,
                selectedId = selectedId,
                keyboardState = keyboardState,
                creating = creating,
                onSelect = viewModel::select,
                onClose = viewModel::closeSession,
                onNewSession = viewModel::newSession,
                onBack = { screen = "home" },
                modifier = Modifier.padding(padding),
            )

            "explore" -> ExploreAppsScreen(
                onBack = { screen = "home" },
                modifier = Modifier.padding(padding),
            )

            else -> HomeScreen(
                installedApps = installedApps,
                activeSessions = sessions,
                onOpenTerminal = viewModel::openTerminal,
                onOpenSession = { id ->
                    viewModel.select(id)
                    screen = "terminal"
                },
                onLaunchApp = { app ->
                    viewModel.launchApp(app)
                    screen = "terminal"
                },
                onExploreApps = { screen = "explore" },
                modifier = Modifier.padding(padding),
            )
        }
    }
}

package app.pocketshell

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.pocketshell.apps.CommandApp
import app.pocketshell.apps.availableCommandApps
import app.pocketshell.apps.guestLaunchChain
import app.pocketshell.packages.CliAppCatalog
import app.pocketshell.packages.CliAppCatalogEntry
import app.pocketshell.packages.InstalledCatalogApp
import app.pocketshell.packages.PackageGateway
import app.pocketshell.packages.PackageProbeException
import app.pocketshell.packages.installedCatalogApps
import app.pocketshell.runtime.RuntimeManager
import app.pocketshell.runtime.RuntimeProcessLauncher
import app.pocketshell.runtime.RuntimeStorage
import app.pocketshell.terminal.ShellEnvironment
import app.pocketshell.terminal.TerminalSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.os.SystemClock

/**
 * UI-side state holder. Sessions themselves live in the process-scoped
 * [TerminalSessionManager]; this ViewModel only tracks selection and provides
 * flows — so Activity recreation can never destroy sessions (brief §24).
 */
class TerminalViewModel(application: Application) : AndroidViewModel(application) {

    val sessions = TerminalSessionManager.sessions
    val creating = TerminalSessionManager.creating

    /** Runtime state (M2) — Home shows the honest Linux Shell availability. */
    val runtimeState = RuntimeManager.state

    private val _selectedId = MutableStateFlow<Long?>(null)
    val selectedId = _selectedId.asStateFlow()

    /**
     * Honest, non-fatal launch failure surface (v0.3.1 regression fix): any
     * session-spawn failure is reported HERE — as a message the Home screen
     * renders — instead of escaping a click handler and killing the process.
     */
    private val _launchError = MutableStateFlow<String?>(null)
    val launchError = _launchError.asStateFlow()

    fun dismissLaunchError() {
        _launchError.value = null
    }

    /** Open the terminal: reuse the newest live session, else create a real one. */
    fun openTerminal(): Boolean {
        val existing = sessions.value.lastOrNull { !it.isFinished }
        if (existing != null) {
            _selectedId.value = existing.id
            return true
        }
        return safeSpawn("Terminal") { createSession() }
    }

    fun newSession(): Boolean = safeSpawn("Terminal") { createSession() }

    /**
     * Enter the installed Alpine guest (M2.3). Caller must only invoke this
     * when [runtimeState] is READY (Home routes otherwise). ASYNC since the
     * M6 Phase-C audit (C1.1/C3): the heavy guest prep (layer fast path —
     * or, on updates/corruption repair, a full re-extraction) runs on
     * Dispatchers.IO, the PTY spawn keeps the main thread, and [onReady]
     * fires exactly when a real session was created and selected (caller
     * navigates). Refusals land in [launchError] — the app must NEVER die
     * from a refused launch, and the UI never freezes on a re-extraction.
     */
    fun openLinuxShell(onReady: () -> Unit) {
        val application = getApplication<Application>()
        // Preflight for an honest, specific message before any side effects.
        val preflight = RuntimeProcessLauncher.preconditionProblem(
            nativeLibraryDir = application.applicationInfo.nativeLibraryDir,
            rootfsDir = RuntimeStorage(application.noBackupFilesDir).rootfsDir,
        )
        if (preflight != null) {
            _launchError.value = preflight
            return
        }
        viewModelScope.launch {
            try {
                val sysDataBinds = withContext(Dispatchers.IO) {
                    TerminalSessionManager.prepareLinuxSession(application)
                }
                val ok = withContext(Dispatchers.Main) {
                    try {
                        _selectedId.value = TerminalSessionManager.spawnLinuxSession(
                            application,
                            listOf(ShellEnvironment.SHELL_PATH_GUEST, "-l"),
                            "Alpine Linux",
                            sysDataBinds,
                        ).id
                        true
                    } catch (t: Throwable) {
                        _launchError.value =
                            "Linux shell could not start: ${t.message ?: t.javaClass.simpleName}"
                        false
                    }
                }
                if (ok) onReady()
            } catch (t: Throwable) {
                _launchError.value =
                    "Linux shell could not start: ${t.message ?: t.javaClass.simpleName}"
            }
        }
    }

    /** Single no-crash boundary around every process spawn. */
    private inline fun safeSpawn(what: String, block: () -> Long): Boolean {
        _launchError.value = null
        return try {
            _selectedId.value = block()
            true
        } catch (t: Throwable) {
            // Containment, not concealment: the real cause is shown, the
            // process stays alive, and the user keeps a working app.
            _launchError.value = "$what could not start: ${t.message ?: t.javaClass.simpleName}"
            false
        }
    }

    fun select(id: Long) {
        _selectedId.value = id
    }

    fun closeSession(id: Long) {
        TerminalSessionManager.closeSession(id)
        if (_selectedId.value == id) {
            _selectedId.value = sessions.value
                .filter { it.id != id && !it.isFinished }
                .lastOrNull()?.id
        }
    }

    // ------------------------------------------------- Phase 3.2 command apps

    /**
     * Home launcher state for command-launchable apps (docs/PHASE-3.2-DESIGN.md
     * §4.4). [apps] is only ever what the guest's login-shell probe confirmed;
     * [probeError] renders as "could not be checked" — on failure the LAST REAL
     * app list stays on screen (a dead probe never renders as "no apps", the
     * v0.4.4 honesty rule applied to command apps). [checked] distinguishes a
     * real empty answer from "not asked yet".
     */
    data class CommandAppsState(
        val apps: List<CommandApp> = emptyList(),
        val probeError: String? = null,
        val checked: Boolean = false,
    )

    private val _commandApps = MutableStateFlow(CommandAppsState())
    val commandApps = _commandApps.asStateFlow()

    /**
     * ONE batched login-shell probe for the whole registry — never a loop of
     * proot startups. Called when Home becomes visible (runtime READY) and
     * after package operations reach a terminal state.
     *
     * m5.1 — the probe is a REAL guest spawn (a proot login-shell exec),
     * so re-running it on EVERY Home visit burned CPU/RAM for an answer
     * that rarely changed. The visible-Home call site now goes through a
     * freshness window (a successful answer is trusted for [COMMAND_PROBE_FRESHNESS_MS])
     * and an in-flight guard; the operation-landing path passes [force] =
     * true because an install may genuinely add an app, and a FAILED probe
     * always re-probes (honesty over caching).
     */
    fun refreshCommandApps(force: Boolean = false) {
        if (!PackageGateway.isRuntimeReady()) return
        if (commandProbeInFlight) return
        if (!force) {
            val state = _commandApps.value
            val fresh =
                state.checked && state.probeError == null &&
                    SystemClock.elapsedRealtime() - lastCommandProbeAt < COMMAND_PROBE_FRESHNESS_MS
            if (fresh) return
        }
        commandProbeInFlight = true
        viewModelScope.launch {
            try {
                val names = app.pocketshell.apps.CommandAppCatalog.registry
                    .map { it.launchCommand.first() }
                val paths = withContext(Dispatchers.IO) {
                    PackageGateway.commandPaths(names)
                }
                _commandApps.value = CommandAppsState(
                    apps = availableCommandApps(paths),
                    probeError = null,
                    checked = true,
                )
            } catch (e: PackageProbeException) {
                // Keep the last real answer; surface the failure next to it.
                _commandApps.value = _commandApps.value.copy(
                    probeError = e.message,
                    checked = true,
                )
            } finally {
                lastCommandProbeAt = SystemClock.elapsedRealtime()
                commandProbeInFlight = false
            }
        }
    }

    private companion object {
        /** A successful probe answer is trusted for this long on Home revisits. */
        const val COMMAND_PROBE_FRESHNESS_MS = 60_000L
    }

    private var commandProbeInFlight = false
    private var lastCommandProbeAt = 0L

    /**
     * Launch a command app from the Home launcher — verify-then-launch, the
     * same discipline as [openCatalogApp]: runtime gate, a FRESH single
     * command probe (login-shell semantics), then a NEW dedicated guest
     * session whose PTY receives the app's launch command. Refusals land in
     * [launchError]; the app never dies and never fakes.
     */
    fun openCommandApp(app: CommandApp, onReady: () -> Unit) {
        val application = getApplication<Application>()
        if (!PackageGateway.isRuntimeReady()) {
            safeFailure(
                "${app.displayName} needs the Linux runtime — install or repair it from Diagnostics",
            )
            return
        }
        _launchError.value = null
        _verifyingApp.value = app.displayName
        viewModelScope.launch {
            try {
                val execPath = withContext(Dispatchers.IO) {
                    PackageGateway.commandPath(app.launchCommand.first())
                }
                if (execPath == null) {
                    safeFailure(
                        "${app.displayName} is not available in the Linux environment right now — " +
                            "the '${app.launchCommand.first()}' command was not found " +
                            "(verified with the real guest shell)",
                    )
                    return@launch
                }
                var newId: Long? = null
                // M6 Phase-C: heavy guest prep on IO, PTY spawn on Main.
                val sysDataBinds = withContext(Dispatchers.IO) {
                    TerminalSessionManager.prepareLinuxSession(application)
                }
                val ok = withContext(Dispatchers.Main) {
                    // TerminalSession construction belongs on the main thread
                    // (upstream MainThreadHandler contract, same as M2.3 flow)
                    try {
                        newId = TerminalSessionManager.spawnLinuxSession(
                            application,
                            listOf(
                                ShellEnvironment.SHELL_PATH_GUEST,
                                "-l",
                                "-c",
                                guestLaunchChain(
                                    launchCommand = app.launchCommand,
                                    guestShell = ShellEnvironment.SHELL_PATH_GUEST,
                                ),
                            ),
                            app.displayName,
                            sysDataBinds,
                        ).id
                        _selectedId.value = newId
                        true
                    } catch (t: Throwable) {
                        _launchError.value =
                            "${app.displayName} could not start: ${t.message ?: t.javaClass.simpleName}"
                        false
                    }
                }
                if (ok && newId != null) {
                    onReady()
                }
            } catch (t: Throwable) {
                safeFailure("${app.displayName} could not start: ${t.message ?: t.javaClass.simpleName}")
            } finally {
                _verifyingApp.value = null
            }
        }
    }

    // ------------------------------------------------------------- M2.4 flows

    /**
     * Home's "Installed CLI Apps" rows — the catalog subset the REAL apk
     * database confirms installed (v0.4.4). The M1-era DataStore registry this
     * replaces was written by nobody (M2.4 installs never touched it), so Home
     * claimed "No apps installed yet" over a genuinely installed nano — a
     * second, invented source of installed state. There is exactly one source
     * now: [PackageGateway.installedVersions].
     */
    private val _installedCatalogApps = MutableStateFlow<List<InstalledCatalogApp>>(emptyList())
    val installedCatalogApps: StateFlow<List<InstalledCatalogApp>> =
        _installedCatalogApps.asStateFlow()

    /** Real probe failure for the installed list — rendered, never swallowed. */
    private val _installedProbeError = MutableStateFlow<String?>(null)
    val installedProbeError: StateFlow<String?> = _installedProbeError.asStateFlow()

    /**
     * Ask the guest's apk database which catalog apps are installed. Called
     * when Home becomes visible (runtime READY) and after every package
     * operation reaches a terminal state — a terminal install via Explore must
     * light up Home without a trip through the process killer.
     */
    fun refreshInstalledCatalogApps() {
        if (!PackageGateway.isRuntimeReady()) return
        viewModelScope.launch {
            try {
                val versions = withContext(Dispatchers.IO) {
                    PackageGateway.installedVersions(
                        CliAppCatalog.entries.map { it.apkPackageName },
                    )
                }
                _installedCatalogApps.value = installedCatalogApps(versions)
                _installedProbeError.value = null
            } catch (e: PackageProbeException) {
                // Keep the last real answer on screen; the failure is surfaced
                // next to it — "Not installed" over a dead probe is a lie.
                _installedProbeError.value = e.message
            }
        }
    }

    /** Package operation state/busy from the process-scoped gateway. */
    val packageOperation = PackageGateway.operations.current
    val packageBusy = PackageGateway.operations.busy

    /** True while an "Open" preflight (installed + executable) is running. */
    private val _verifyingApp = MutableStateFlow<String?>(null)
    val verifyingApp = _verifyingApp.asStateFlow()

    /**
     * Open an installed catalog app (M2.4) — from Explore or Home. Verifies
     * the REAL state first — runtime READY, package in apk's database,
     * executable via command -v — then creates a NEW dedicated guest session
     * and launches the program in it. Refusals land in [launchError]; the app
     * never dies and never fakes.
     *
     * @param onReady called on the main thread exactly when a real session was
     *   created and selected (caller navigates).
     */
    fun openCatalogApp(entry: CliAppCatalogEntry, onReady: () -> Unit) {
        val application = getApplication<Application>()
        if (!PackageGateway.isRuntimeReady()) {
            safeFailure("${entry.name} needs the Linux runtime — install or repair it from Diagnostics")
            return
        }
        _launchError.value = null
        _verifyingApp.value = entry.name
        viewModelScope.launch {
            try {
                // preflight against the real guest: package db + executable
                val installed = withContext(Dispatchers.IO) {
                    PackageGateway.installedVersion(entry.apkPackageName)
                }
                if (installed == null) {
                    safeFailure(
                        "${entry.name} is not installed — install it from Explore CLI Apps first " +
                            "(state verified against the real Alpine package database)",
                    )
                    return@launch
                }
                val execPath = withContext(Dispatchers.IO) {
                    PackageGateway.executablePath(entry.executable)
                }
                if (execPath == null) {
                    safeFailure(
                        "${entry.name} is recorded as installed, but its executable " +
                            "'${entry.executable}' was not found via command -v — " +
                            "try reinstalling it",
                    )
                    return@launch
                }
                var newId: Long? = null
                // M6 Phase-C: heavy guest prep on IO, PTY spawn on Main.
                val sysDataBinds = withContext(Dispatchers.IO) {
                    TerminalSessionManager.prepareLinuxSession(application)
                }
                val ok = withContext(Dispatchers.Main) {
                    // TerminalSession construction belongs on the main thread
                    // (upstream MainThreadHandler contract, same as M2.3 flow)
                    try {
                        newId = TerminalSessionManager.spawnLinuxSession(
                            application,
                            listOf(
                                ShellEnvironment.SHELL_PATH_GUEST,
                                "-l",
                                "-c",
                                guestLaunchChain(
                                    launchCommand = entry.launchCommand,
                                    guestShell = ShellEnvironment.SHELL_PATH_GUEST,
                                ),
                            ),
                            entry.name,
                            sysDataBinds,
                        ).id
                        _selectedId.value = newId
                        true
                    } catch (t: Throwable) {
                        _launchError.value =
                            "${entry.name} could not start: ${t.message ?: t.javaClass.simpleName}"
                        false
                    }
                }
                if (ok && newId != null) {
                    onReady()
                }
            } catch (t: Throwable) {
                safeFailure("${entry.name} could not start: ${t.message ?: t.javaClass.simpleName}")
            } finally {
                _verifyingApp.value = null
            }
        }
    }

    /** Trigger a real uninstall of a catalog app (runs in the gateway scope). */
    fun uninstallCatalogApp(entry: CliAppCatalogEntry) {
        PackageGateway.operations.uninstall(entry)
    }

    /** Trigger a real install of a catalog app (runs in the gateway scope). */
    fun installCatalogApp(entry: CliAppCatalogEntry) {
        PackageGateway.operations.install(entry)
    }

    /**
     * M2.5: install an arbitrary searched package by its exact apk name —
     * no Open promise (the launcher binary name is unknown for non-catalog
     * packages; e.g. nodejs ships `node`), SUCCESS = `apk info -e` confirms.
     */
    fun installSearchResult(packageName: String) {
        PackageGateway.operations.installPackage(packageName)
    }

    /** Re-run the failed repository update (honest Retry on the FAILED banner). */
    fun retryRepositoryUpdate() {
        PackageGateway.operations.updateRepositories()
    }

    /** Real apk search; results delivered on the gateway's IO completion. */
    fun searchPackages(query: String, onResult: (List<app.pocketshell.packages.PackageSearchResult>) -> Unit) {
        PackageGateway.operations.search(query, onResult)
    }

    private fun safeFailure(message: String): Boolean {
        _launchError.value = message
        return false
    }

    private fun createSession(): Long =
        TerminalSessionManager.createSession(getApplication()).id
}

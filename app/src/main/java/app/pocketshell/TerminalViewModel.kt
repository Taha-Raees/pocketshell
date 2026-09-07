package app.pocketshell

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.pocketshell.apps.CommandApp
import app.pocketshell.apps.availableCommandApps
import app.pocketshell.apps.guestCustomCommandChain
import app.pocketshell.apps.guestLaunchChain
import app.pocketshell.apps.guestTerminalChain
import app.pocketshell.apps.probeName
import app.pocketshell.launchers.CustomTool
import app.pocketshell.launchers.commandHead
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
     * p7.1 — the terminal screen's "+": add a session that MATCHES the
     * environment the user is in. When the current session (the selected
     * one, else the newest live one) is a Linux guest session, a new Linux
     * shell is spawned through the UNCHANGED canonical [openLinuxShell] path
     * (runtime preflight → prepare on IO → spawn on Main → select — already
     * on the terminal screen, so no navigation is needed); otherwise the
     * historical Android-shell [newSession] runs. No session is closed,
     * reused or written into.
     *
     * Guest knowledge has TWO honest sources: spawn-time registration
     * ([guestSessionIds] — covers Linux shells, command apps and catalog
     * apps) and the pinned guest label ([GUEST_SESSION_LABEL] — survives
     * ViewModel recreation for the plain Linux shells, whose label the
     * manager keeps). A session registered by neither falls back to the
     * historical Android behavior — a wrong-guess spawn is never faked.
     */
    fun newSessionMatchingCurrent() {
        val currentId = _selectedId.value
            ?: sessions.value.lastOrNull { !it.isFinished }?.id
        val current = sessions.value.firstOrNull { it.id == currentId }
        val currentIsGuest = current != null &&
            (current.id in guestSessionIds || current.label == GUEST_SESSION_LABEL)
        if (currentIsGuest) {
            openLinuxShell {}
        } else {
            newSession()
        }
    }

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
                        val newId = TerminalSessionManager.spawnLinuxSession(
                            application,
                            listOf(ShellEnvironment.SHELL_PATH_GUEST, "-l"),
                            GUEST_SESSION_LABEL,
                            sysDataBinds,
                        ).id
                        guestSessionIds.add(newId)
                        _selectedId.value = newId
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

    /**
     * M7.0.0 Phase 7 — "Open Terminal Here": a NORMAL Alpine Linux session
     * whose guest working directory starts at [directory] (the validated
     * [app.pocketshell.files.AreaPath] value of the folder the user tapped
     * in the explorer — p7.1: the SELECTED entry, composed and validated by
     * the same child composition every navigation uses).
     *
     * This MIRRORS the proven [openLinuxShell] path exactly — same runtime
     * preflight, same `prepareLinuxSession` on IO, same `spawnLinuxSession`
     * on Main, same honest [launchError] refusals, `onReady` fires only when
     * a real session was created and selected — with ONE difference: the PTY
     * argv carries the directory through the guest shell chain
     * ([guestTerminalChain]) instead of a bare login shell. That is the
     * EXISTING command-app launch shape (`sh -l -c "<chain>"`, argv-based,
     * never a PTY write — the m3.5 lesson) applied to `cd -- '<dir>' &&
     * exec <shell> -l`, so the user lands at a real interactive prompt in
     * the directory; no new PTY path, no session reuse, no writes into a
     * running session. Existing sessions are untouched: a NEW session joins
     * the list and is selected by the same spawn-and-select behavior as
     * every other launch.
     *
     * @param directory the guest-native directory path (DATA — quoted as a
     *   single POSIX word by the chain, never interpreted as shell syntax).
     * @param onReady called on the main thread exactly when a real session
     *   was created and selected (caller navigates).
     */
    fun openLinuxShellAt(directory: String, onReady: () -> Unit) {
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
                        val newId = TerminalSessionManager.spawnLinuxSession(
                            application,
                            listOf(
                                ShellEnvironment.SHELL_PATH_GUEST,
                                "-l",
                                "-c",
                                guestTerminalChain(
                                    directory = directory,
                                    guestShell = ShellEnvironment.SHELL_PATH_GUEST,
                                ),
                            ),
                            GUEST_SESSION_LABEL,
                            sysDataBinds,
                        ).id
                        guestSessionIds.add(newId)
                        _selectedId.value = newId
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

        /**
         * p7.1 — the pinned fallback label of a plain Linux-shell session
         * (openLinuxShell / openLinuxShellAt). One constant, referenced by
         * both spawn sites and the "+" kind check, so the string can never
         * drift apart. Command-app and catalog-app sessions carry their own
         * display names — their guest kind is known from spawn-time
         * registration instead.
         */
        const val GUEST_SESSION_LABEL = "Alpine Linux"
    }

    private var commandProbeInFlight = false
    private var lastCommandProbeAt = 0L

    /**
     * p7.1 — ids of sessions this ViewModel spawned as LINUX GUEST sessions
     * (Linux shells, command apps, catalog apps). Main-thread only (every
     * spawn site runs on Dispatchers.Main). Process-scoped truth: sessions
     * die with the process, so the set can never outlive its sessions — but
     * a ViewModel RECREATION (configuration change) starts empty, which is
     * why the [GUEST_SESSION_LABEL] fallback exists.
     */
    private val guestSessionIds = mutableSetOf<Long>()

    /**
     * Launch a command app from the Home launcher — verify-then-launch, the
     * same discipline as [openCatalogApp]: runtime gate, a FRESH single
     * command probe (login-shell semantics), then a NEW dedicated guest
     * session whose PTY receives the app's launch command. Refusals land in
     * [launchError]; the app never dies and never fakes.
     *
     * M7.1 P1: the body moved into the shared [launchGuestCommand] core so
     * the custom tools launch through EXACTLY this proven path — the
     * behavior is byte-identical.
     */
    fun openCommandApp(app: CommandApp, onReady: () -> Unit) {
        launchGuestCommand(
            displayName = app.displayName,
            probeName = app.probeName(),
            commandChain = guestLaunchChain(
                launchCommand = app.launchCommand,
                guestShell = ShellEnvironment.SHELL_PATH_GUEST,
            ),
            onReady = onReady,
        )
    }

    /**
     * M7.1 P1 — launch a user-defined custom tool through the ONE proven
     * guest path. The command is user configuration: the tap-time probe
     * checks the command's HEAD token with the real guest shell (an absent
     * binary is an honest refusal, never a broken session), and the FULL
     * validated line travels verbatim through [guestCustomCommandChain]
     * into the same `sh -l -c …; exec` structure every other launch uses.
     * No new spawn system; the same refusals, the same banner.
     */
    fun openCustomTool(tool: CustomTool, onReady: () -> Unit) {
        launchGuestCommand(
            displayName = tool.name,
            probeName = tool.commandHead(),
            commandChain = guestCustomCommandChain(
                command = tool.command,
                guestShell = ShellEnvironment.SHELL_PATH_GUEST,
            ),
            onReady = onReady,
        )
    }

    /**
     * The ONE verify-then-launch core for command-line launchers: runtime
     * gate → fresh single-command guest probe → guest prep (IO) → PTY
     * spawn (Main, the upstream MainThreadHandler contract) → select +
     * navigate on real success. Extracted verbatim from openCommandApp in
     * M7.1 P1 — no behavior change for registry apps.
     */
    private fun launchGuestCommand(
        displayName: String,
        probeName: String,
        commandChain: String,
        onReady: () -> Unit,
    ) {
        val application = getApplication<Application>()
        if (!PackageGateway.isRuntimeReady()) {
            safeFailure(
                "$displayName needs the Linux runtime — install or repair it from Diagnostics",
            )
            return
        }
        _launchError.value = null
        _verifyingApp.value = displayName
        viewModelScope.launch {
            try {
                val execPath = withContext(Dispatchers.IO) {
                    PackageGateway.commandPath(probeName)
                }
                if (execPath == null) {
                    safeFailure(
                        "$displayName is not available in the Linux environment right now — " +
                            "the '$probeName' command was not found " +
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
                                commandChain,
                            ),
                            displayName,
                            sysDataBinds,
                        ).id
                        guestSessionIds.add(newId)
                        _selectedId.value = newId
                        true
                    } catch (t: Throwable) {
                        _launchError.value =
                            "$displayName could not start: ${t.message ?: t.javaClass.simpleName}"
                        false
                    }
                }
                if (ok && newId != null) {
                    onReady()
                }
            } catch (t: Throwable) {
                safeFailure("$displayName could not start: ${t.message ?: t.javaClass.simpleName}")
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
                        guestSessionIds.add(newId)
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

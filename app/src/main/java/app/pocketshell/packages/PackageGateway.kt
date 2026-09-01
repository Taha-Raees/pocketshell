package app.pocketshell.packages

import android.content.Context
import app.pocketshell.runtime.GuestEnvironment
import app.pocketshell.runtime.RuntimeManager
import app.pocketshell.runtime.RuntimeProcessLauncher
import app.pocketshell.runtime.RuntimeState
import app.pocketshell.runtime.RuntimeStorage
import app.pocketshell.terminal.ShellEnvironment
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Process-scoped gateway for the M2.4 package layer — the Android wiring
 * (context, paths, proot specs, runtime gate) around [PackageOperationManager].
 * UI code observes [operations]; no screen ever builds guest commands itself.
 */
object PackageGateway {

    private var appContext: Context? = null
    private var storage: RuntimeStorage? = null

    lateinit var operations: PackageOperationManager
        private set

    /**
     * The launcher-side dependency for "Open": the same READY gate the Linux
     * Shell uses, exposed for preflights that need a plain boolean.
     */
    fun isRuntimeReady(): Boolean = RuntimeManager.state.value == RuntimeState.READY

    /**
     * Real "is it installed (+version)" answer for preflights — a fresh
     * `apk info -e -v` exec, never a cached assumption. Null = not installed
     * (or runtime not READY / invalid name: in every case the honest answer
     * is "cannot claim installed").
     */
    suspend fun installedVersion(packageName: String): String? {
        val context = appContext ?: return null
        val storage = storage ?: return null
        val manager = AlpinePackageManager(
            rootfsDir = storage.rootfsDir,
            specFactory = { guestCommand -> buildSpec(context, storage, guestCommand) },
            runner = ProcessBuilderGuestCommandRunner(),
            readyGuard = { if (isRuntimeReady()) null else "runtime not READY" },
        )
        val info = manager.getPackageInfo(packageName)
        return if (info.installed) info.version else null
    }

    /** Real `command -v` preflight for "Open" (null = not found / refused). */
    suspend fun executablePath(executable: String): String? {
        val context = appContext ?: return null
        val storage = storage ?: return null
        val manager = AlpinePackageManager(
            rootfsDir = storage.rootfsDir,
            specFactory = { guestCommand -> buildSpec(context, storage, guestCommand) },
            runner = ProcessBuilderGuestCommandRunner(),
            readyGuard = { if (isRuntimeReady()) null else "runtime not READY" },
        )
        return manager.guestExecutablePath(executable)
    }

    /** Real batch installed-status answer for the catalog screen (one exec). */
    suspend fun installedVersions(packageNames: List<String>): Map<String, String> {
        val context = appContext ?: return emptyMap()
        val storage = storage ?: return emptyMap()
        val manager = AlpinePackageManager(
            rootfsDir = storage.rootfsDir,
            specFactory = { guestCommand -> buildSpec(context, storage, guestCommand) },
            runner = ProcessBuilderGuestCommandRunner(),
            readyGuard = { if (isRuntimeReady()) null else "runtime not READY" },
        )
        return manager.getInstalledVersions(packageNames)
    }

    fun init(context: Context) {
        if (this::operations.isInitialized) return
        val appContext = context.applicationContext
        this.appContext = appContext
        val storage = RuntimeStorage(appContext.noBackupFilesDir)
        this.storage = storage

        val readyGuard: () -> String? = {
            if (RuntimeManager.state.value == RuntimeState.READY) {
                null
            } else {
                "the Linux runtime is not READY — install or repair it from Diagnostics first"
            }
        }

        operations = PackageOperationManager(
            runner = ProcessBuilderGuestCommandRunner(),
            packagesFactory = { runner ->
                AlpinePackageManager(
                    rootfsDir = storage.rootfsDir,
                    specFactory = { guestCommand -> buildSpec(appContext, storage, guestCommand) },
                    runner = runner,
                    readyGuard = readyGuard,
                )
            },
            runtimeReady = { RuntimeManager.state.value == RuntimeState.READY },
        )
    }

    /**
     * One proot spec builder shared by every package command — literally the
     * same [RuntimeProcessLauncher.buildLaunchSpec] the Linux Shell uses, with
     * a different guest argv (apk commands instead of /bin/sh -l).
     */
    private fun buildSpec(
        context: Context,
        storage: RuntimeStorage,
        guestCommand: List<String>,
    ): RuntimeProcessLauncher.LaunchSpec =
        RuntimeProcessLauncher.buildLaunchSpec(
            nativeLibraryDir = context.applicationInfo.nativeLibraryDir,
            rootfsDir = storage.rootfsDir,
            hostCwd = ShellEnvironment.homeDir(context),
            prootTmpDir = File(context.cacheDir, "proot-tmp").apply { mkdirs() },
            guestCommand = guestCommand,
        )

    // ------------------------------------------------------- diagnostics only

    /**
     * Read-mostly package environment report for Diagnostics' explicit
     * "Check package environment" button. NOT called on screen open: the only
     * network-ish part (`apk --version` is offline, but it still execs the
     * guest) runs because the user asked. Never mutates the package database.
     */
    suspend fun checkEnvironment(): PackageEnvironmentReport = withContext(Dispatchers.IO) {
        val context = appContext
            ?: return@withContext PackageEnvironmentReport(apkError = "gateway not initialized")
        val storage = storage
            ?: return@withContext PackageEnvironmentReport(apkError = "gateway not initialized")

        val ready = RuntimeManager.state.value == RuntimeState.READY
        val repositoriesFile = File(storage.rootfsDir, "etc/apk/repositories")
        val worldFile = File(storage.rootfsDir, "etc/apk/world")
        val resolvFile = File(storage.rootfsDir, GuestEnvironment.RESOLV_CONF_RELATIVE)

        var apkVersion: String? = null
        var apkExitCode: Int? = null
        var apkError: String? = null
        if (ready) {
            val manager = AlpinePackageManager(
                rootfsDir = storage.rootfsDir,
                specFactory = { guestCommand -> buildSpec(context, storage, guestCommand) },
                runner = ProcessBuilderGuestCommandRunner(),
                readyGuard = { null },
            )
            // one bounded offline exec: the version banner proves apk links
            // and runs inside proot (the libtalloc/LD_LIBRARY_PATH chain)
            val outcome = runCatching {
                manager.let { execOffline(it, listOf(AlpinePackageManager.APK, "--version")) }
            }
            val pair = outcome.getOrNull()
            apkExitCode = pair?.first
            apkVersion = pair?.second?.lineSequence()?.firstOrNull { it.isNotBlank() }
            apkError = when {
                outcome.isFailure ->
                    "apk --version could not run: " +
                        (outcome.exceptionOrNull()?.message ?: "unknown error")
                apkExitCode != 0 -> "apk --version exited with $apkExitCode"
                else -> null
            }
        } else {
            apkError = "runtime not READY"
        }

        PackageEnvironmentReport(
            runtimeReady = ready,
            apkVersion = apkVersion,
            apkExitCode = apkExitCode,
            apkError = apkError,
            repositories = repositoriesFile.takeIf { it.isFile }
                ?.readText()?.lineSequence()
                ?.filter { it.isNotBlank() }?.toList(),
            worldPackages = worldFile.takeIf { it.isFile }
                ?.readLines()?.count { it.isNotBlank() },
            dnsConfigured = resolvFile.isFile && resolvFile.readText().isNotBlank(),
        )
    }

    private fun execOffline(
        manager: AlpinePackageManager,
        guestCommand: List<String>,
    ): Pair<Int, String?> {
        // minimal local runner: no DNS ensure needed for --version
        val spec = manager.specFactory.specFor(guestCommand)
        val process = ProcessBuilderGuestCommandRunner().start(spec)
        val exec = process.waitFor(AlpinePackageManager.QUICK_TIMEOUT_MS)
        return if (exec.success) exec.exitCode!! to exec.stdout
        else (exec.exitCode ?: -1) to null
    }
}

/** Immutable snapshot shown under "Package environment" in Diagnostics. */
data class PackageEnvironmentReport(
    val runtimeReady: Boolean = false,
    val apkVersion: String? = null,
    val apkExitCode: Int? = null,
    val apkError: String? = null,
    val repositories: List<String>? = null,
    val worldPackages: Int? = null,
    val dnsConfigured: Boolean = false,
)

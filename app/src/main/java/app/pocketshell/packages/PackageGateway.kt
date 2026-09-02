package app.pocketshell.packages

import android.content.Context
import android.net.ConnectivityManager
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
        val manager = newPackageManager(readyGuard = { if (isRuntimeReady()) null else "runtime not READY" })
            ?: return null
        val info = manager.getPackageInfo(packageName)
        return if (info.installed) info.version else null
    }

    /** Real `command -v` preflight for "Open" (null = not found / refused). */
    suspend fun executablePath(executable: String): String? {
        val manager = newPackageManager(readyGuard = { if (isRuntimeReady()) null else "runtime not READY" })
            ?: return null
        return manager.guestExecutablePath(executable)
    }

    /**
     * Real batch installed-status answer for the catalog screens (one exec).
     * Throws [PackageProbeException] when the guest exec fails — callers must
     * render that as "state unavailable", never as "Not installed" (v0.4.4).
     */
    suspend fun installedVersions(packageNames: List<String>): Map<String, String> {
        val manager = newPackageManager(readyGuard = { if (isRuntimeReady()) null else "runtime not READY" })
            ?: return emptyMap()
        return manager.getInstalledVersions(packageNames)
    }

    fun init(context: Context) {
        if (this::operations.isInitialized) return
        val appContext = context.applicationContext
        this.appContext = appContext
        val storage = RuntimeStorage(appContext.noBackupFilesDir)
        this.storage = storage

        operations = PackageOperationManager(
            runner = ProcessBuilderGuestCommandRunner(),
            packagesFactory = { runner ->
                newPackageManager(runner = runner, readyGuard = defaultReadyGuard())
                    ?: throw IllegalStateException("gateway not initialized")
            },
            runtimeReady = { RuntimeManager.state.value == RuntimeState.READY },
        )
    }

    private fun defaultReadyGuard(): () -> String? = {
        if (RuntimeManager.state.value == RuntimeState.READY) {
            null
        } else {
            "the Linux runtime is not READY — install or repair it from Diagnostics first"
        }
    }

    /**
     * One construction path for every [AlpinePackageManager] this gateway
     * hands out, so the DNS-provider and spec-factory wiring can never drift
     * between call sites. Null only before [init].
     */
    private fun newPackageManager(
        runner: GuestCommandRunner = ProcessBuilderGuestCommandRunner(),
        readyGuard: () -> String?,
    ): AlpinePackageManager? {
        val context = appContext ?: return null
        val storage = storage ?: return null
        return AlpinePackageManager(
            rootfsDir = storage.rootfsDir,
            specFactory = { guestCommand -> buildSpec(context, storage, guestCommand) },
            runner = runner,
            readyGuard = readyGuard,
            dnsServers = { deviceDnsServers(context) },
        )
    }

    /**
     * The device's OWN live DNS resolvers (ConnectivityManager LinkProperties),
     * IPv4 first. v0.4.1 device lesson: hardcoded public resolvers were
     * unreachable on the user's network while the OS resolvers worked — musl
     * in the guest must be pointed at the same servers Android itself uses.
     * Empty on any failure → GuestEnvironment falls back to the public pair.
     */
    internal fun deviceDnsServers(context: Context): List<String> = try {
        val cm = context.getSystemService(ConnectivityManager::class.java)
            ?: return emptyList()
        val seen = LinkedHashSet<String>()
        for (network in cm.allNetworks) {
            val links = runCatching { cm.getLinkProperties(network) }.getOrNull() ?: continue
            for (address in links.dnsServers) {
                val host = runCatching { address.hostAddress }.getOrNull() ?: continue
                // Drop zone-suffixed link-locals ("fe80::…%wlan0"): that is
                // Android scope syntax, musl's inet_pton rejects it, so the
                // line was dead weight in the guest resolv.conf (v0.4.3).
                if (!host.isNullOrBlank() && !host.contains('%')) seen.add(host)
            }
        }
        // IPv4 first: on-device IPv6 egress is frequently absent and musl
        // would burn its retry budget on unreachable v6 resolvers.
        seen.filterNot { it.contains(':') } + seen.filter { it.contains(':') }
    } catch (_: Exception) {
        emptyList()
    }.let { list -> list.distinct().take(3) }

    /**
     * App-owned host directory bound over the guest's apk cache paths — the
     * package cache lives OUTSIDE the rootfs so rootfs-internal permissions
     * can never block apk (v0.4.1). Sits beside the runtime under
     * noBackupFilesDir; the download cache is disposable by design.
     *
     * v0.5.0: also used by INTERACTIVE sessions ([buildSessionSpec] shape) so
     * a manual `apk` inside the shell shares ONE index/package cache with the
     * app-side operations.
     */
    fun apkCacheDir(storage: RuntimeStorage): File =
        File(storage.baseDir, "apk-cache").apply { mkdirs() }

    /**
     * Best-effort guest environment repair before an interactive session
     * spawns (v0.5.0): refresh the managed resolv.conf to the CURRENT
     * network's resolvers and make sure the apk cache/tmp dirs exist with
     * sane modes. Best-effort by design — a failure here never blocks the
     * session; whatever is genuinely broken surfaces with its real error the
     * moment apk runs. (Package operations run the same repairs strictly —
     * see [AlpinePackageManager.runApk].)
     */
    fun prepareGuestForSession(context: Context, rootfsDir: File) {
        runCatching {
            GuestEnvironment.ensureDnsResolvers(rootfsDir, deviceDnsServers(context))
            GuestEnvironment.ensureApkWorkspace(rootfsDir)
        }
    }

    /**
     * One proot spec builder shared by every package command — literally the
     * same [RuntimeProcessLauncher.buildLaunchSpec] the Linux Shell uses, with
     * a different guest argv (apk commands instead of /bin/sh -l) plus the apk
     * cache binds. The shell path passes no apkCacheDir and stays untouched.
     *
     * v0.4.2: package specs pass bindProc=false — see
     * [RuntimeProcessLauncher] KDoc (apk's O_TMPFILE+linkat download commit is
     * SELinux-neverallowed for untrusted apps; without /proc apk uses its
     * named-tmpfile+renameat path, which is allowed).
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
            apkCacheDir = apkCacheDir(storage),
            bindProc = false,
        )

    // ------------------------------------------------------- diagnostics only

    /**
     * Read-mostly package environment report for Diagnostics' explicit
     * "Check package environment" button. NOT called on screen open: the
     * execs (including the REAL `apk update` probe — the same command the
     * install flow runs) happen only because the user pressed the button.
     * The probe refreshes the download cache but never mutates the package
     * database (world / installed are read-only here).
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

        val deviceServers = deviceDnsServers(context)

        var apkVersion: String? = null
        var apkExitCode: Int? = null
        var apkError: String? = null
        var updateProbeOk: Boolean? = null
        var updateProbeDetail: String? = null
        if (ready) {
            val manager = AlpinePackageManager(
                rootfsDir = storage.rootfsDir,
                specFactory = { guestCommand -> buildSpec(context, storage, guestCommand) },
                runner = ProcessBuilderGuestCommandRunner(),
                readyGuard = { null },
                dnsServers = { deviceServers },
            )
            // one bounded offline exec: the version banner proves apk links
            // and runs inside proot (the libtalloc/LD_LIBRARY_PATH chain)
            val outcome = runCatching {
                execOffline(manager, listOf(AlpinePackageManager.APK, "--version"))
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

            // REAL fetch probe (bounded): exactly what `apk add` runs first.
            // Honest result either way — this is the button's whole point.
            if (apkError == null) {
                val probe = runCatching { manager.updateRepositories() }
                probe.onSuccess { result ->
                    updateProbeOk = result.success
                    updateProbeDetail = if (result.success) {
                        result.stdout.lineSequence()
                            .lastOrNull { it.isNotBlank() }
                            ?: "repositories updated"
                    } else {
                        result.error
                            ?: result.stderr.lineSequence().lastOrNull { it.isNotBlank() }
                            ?: "apk update exited with ${result.exitCode}"
                    }
                }.onFailure {
                    updateProbeOk = false
                    updateProbeDetail = it.message ?: it.javaClass.simpleName
                }
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
            dnsServers = resolvFile.takeIf { it.isFile }
                ?.readLines()?.filter {
                    it.isNotBlank() && !it.trimStart().startsWith("#")
                },
            dnsSource = when {
                deviceServers.isEmpty() -> "public fallback (device resolvers unavailable)"
                else -> "device resolvers first, public fallback (musl queries all in parallel)"
            },
            updateProbeOk = updateProbeOk,
            updateProbeDetail = updateProbeDetail,
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
    val dnsServers: List<String>? = null,
    val dnsSource: String? = null,
    val updateProbeOk: Boolean? = null,
    val updateProbeDetail: String? = null,
)

package app.pocketshell.packages

import app.pocketshell.runtime.GuestEnvironment
import app.pocketshell.runtime.RuntimeProcessLauncher
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Real apk-tools (Alpine) implementation of [PackageManager] — M2.4.
 *
 * Architecture (Master Prompt, Critical M2.4 Architecture Decision):
 *
 *   Android UI → PackageOperationManager → [AlpinePackageManager]
 *     → RuntimeProcessLauncher.buildLaunchSpec (the M2.3 launcher, reused —
 *       proot binary, loader, LD_LIBRARY_PATH, argv contract all identical)
 *     → proot → real `apk` → real Alpine package database.
 *
 * This class NEVER touches Alpine package files (no /var/lib/apk reading, no
 * world-file editing) and NEVER reports an installed state the guest did not
 * confirm: installation status comes from `apk info -e` exit codes, versions
 * from real `apk info -e -v` stdout, executables from POSIX `command -v`.
 *
 * Rehearsed end-to-end on the sandbox (scripts/rehearse_m24_packages.sh,
 * apk-tools 3.0.6 / Alpine 3.24.1): update → search → add → info -e →
 * command -v → nano runs → del → info -e exit 1.
 *
 * @param rootfsDir the installed runtime rootfs (DNS repair target).
 * @param specFactory builds the proot spec for a guest argv (same builder the
 *   terminal uses — guaranteed single exec infrastructure).
 * @param runner executes specs as dedicated background guest processes.
 * @param readyGuard returns null when work may proceed, else an honest reason.
 */
class AlpinePackageManager(
    private val rootfsDir: File,
    val specFactory: SpecFactory,
    private val runner: GuestCommandRunner,
    private val readyGuard: () -> String?,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PackageManager {

    /** Builds a spec whose proot argv tail is exactly [guestCommand]. */
    fun interface SpecFactory {
        fun specFor(guestCommand: List<String>): RuntimeProcessLauncher.LaunchSpec
    }

    override suspend fun updateRepositories(): PackageResult = withContext(ioDispatcher) {
        readyGuard()?.let { return@withContext PackageResult.failure(it) }
        runApk(listOf(APK, "update"), timeoutMs = UPDATE_TIMEOUT_MS)
    }

    override suspend fun search(query: String): List<PackageSearchResult> = withContext(ioDispatcher) {
        val trimmed = query.trim()
        if (trimmed.isEmpty() || readyGuard() != null) return@withContext emptyList()
        val result = runApk(listOf(APK, "search", trimmed), timeoutMs = QUICK_TIMEOUT_MS)
        if (!result.success) return@withContext emptyList()
        ApkOutputParser.parseSearch(result.stdout)
    }

    override suspend fun getPackageInfo(packageName: String): PackageInfoResult =
        withContext(ioDispatcher) {
            if (!ApkOutputParser.isValidPackageName(packageName)) {
                return@withContext PackageInfoResult(installed = false)
            }
            if (readyGuard() != null) return@withContext PackageInfoResult(installed = false)
            val result = runApk(
                listOf(APK, "info", "-e", "-v", packageName),
                timeoutMs = QUICK_TIMEOUT_MS,
            )
            ApkOutputParser.parseInfoInstalled(result.exitCode, result.stdout)
        }

    override suspend fun getInstalledVersions(packageNames: List<String>): Map<String, String> =
        withContext(ioDispatcher) {
            val names = packageNames.filter { ApkOutputParser.isValidPackageName(it) }
            if (names.isEmpty() || readyGuard() != null) return@withContext emptyMap()
            // One exec for the whole list — the launcher UI refreshes with a
            // single guest round-trip (POSIX sh; positional args, no quoting).
            val script = "for p in \"\$@\"; do v=\$(apk info -e -v \"\$p\" 2>/dev/null) && echo \"\$p \$v\"; done"
            val result = runApk(
                listOf("/bin/sh", "-c", script, "sh") + names,
                timeoutMs = QUICK_TIMEOUT_MS,
            )
            if (!result.success) return@withContext emptyMap()
            result.stdout.lineSequence().mapNotNull { line ->
                val parts = line.trim().split(' ', limit = 2)
                if (parts.size == 2 && ApkOutputParser.isValidPackageName(parts[0])) {
                    parts[0] to parts[1]
                } else {
                    null
                }
            }.toMap()
        }

    override suspend fun install(packageName: String): PackageResult = withContext(ioDispatcher) {
        if (!ApkOutputParser.isValidPackageName(packageName)) {
            return@withContext PackageResult.failure("invalid package name: $packageName")
        }
        runApk(listOf(APK, "add", packageName), timeoutMs = null)
    }

    override suspend fun uninstall(packageName: String): PackageResult = withContext(ioDispatcher) {
        if (!ApkOutputParser.isValidPackageName(packageName)) {
            return@withContext PackageResult.failure("invalid package name: $packageName")
        }
        runApk(listOf(APK, "del", packageName), timeoutMs = null)
    }

    override suspend fun guestExecutablePath(executable: String): String? = withContext(ioDispatcher) {
        // catalog executables are plain names (nano, htop, …) — still routed
        // through a positional arg so nothing is ever string-built.
        val clean = executable.trim()
        if (clean.isEmpty() || clean.contains(Regex("[^a-zA-Z0-9._/+%-]"))) {
            return@withContext null
        }
        if (readyGuard() != null) return@withContext null
        val result = runApk(
            listOf("/bin/sh", "-c", "command -v \"\$1\"", "sh", clean),
            timeoutMs = QUICK_TIMEOUT_MS,
        )
        if (!result.success) return@withContext null
        result.stdout.lineSequence().firstOrNull { it.isNotBlank() }
    }

    // ------------------------------------------------------------------ core

    /**
     * One real guest exec. Ensures guest DNS first (runtimes installed before
     * v0.4.0 lack /etc/resolv.conf — see [GuestEnvironment]); a failed repair
     * fails the operation honestly instead of dying later on DNS errors.
     */
    private fun runApk(guestCommand: List<String>, timeoutMs: Long?): PackageResult {
        if (!GuestEnvironment.ensureDnsResolvers(rootfsDir)) {
            return PackageResult.failure(
                "could not prepare guest DNS (${GuestEnvironment.RESOLV_CONF_RELATIVE}) — " +
                    "guest name resolution would fail; repair the runtime from Diagnostics",
            )
        }
        val spec = specFactory.specFor(guestCommand)
        val process = runner.start(spec)
        return try {
            val exec = process.waitFor(timeoutMs)
            PackageResult(
                success = exec.success,
                exitCode = exec.exitCode,
                stdout = exec.stdout,
                stderr = exec.stderr,
                error = exec.error
                    ?: if (!exec.success && exec.stderr.isBlank() && exec.exitCode != 0) {
                        "guest process exited with code ${exec.exitCode}"
                    } else {
                        null
                    },
            )
        } catch (t: Throwable) {
            process.destroy()
            throw t
        }
    }

    companion object {
        /** apk's real location in the pinned Alpine rootfs (rehearsed). */
        const val APK = "/sbin/apk"

        /** Quick checks (info/-v/search/command -v) — generous but bounded. */
        const val QUICK_TIMEOUT_MS = 30_000L

        /** apk update — indexes are small; still bounded. */
        const val UPDATE_TIMEOUT_MS = 120_000L
    }
}

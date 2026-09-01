package app.pocketshell.runtime

import java.io.File

/**
 * Builds the proot launch line that puts the Alpine guest behind the existing
 * M1 PTY (docs/M2-ARCHITECTURE §7). Pure and unit-testable: every path arrives
 * as a parameter, no android.* imports.
 *
 * Contract provenance — rehearsed end-to-end in the sandbox with the SAME
 * source pin and the SAME argv (scripts/rehearse_m23_gate.sh): proot
 * v5.1.107.92 accepts long options ONLY in the "--opt=value" joined form;
 * the tests below pin that so a future proot bump cannot silently break argv.
 *
 * Loader strategy (pin's src/execve/enter.c): the runtime env PROOT_LOADER
 * overrides the loader path in BOTH build modes, while the loader embedded in
 * libproot.so stays as a fallback. On Android the loader file lives in
 * nativeLibraryDir — the one location that stays executable at targetSdk >= 29
 * (docs/M2-RESEARCH §1.2/§1.3). No app-data file is ever execve()'d.
 */
object RuntimeProcessLauncher {

    const val PROOT_LIB = "libproot.so"
    const val LOADER_LIB = "libproot-loader.so"
    const val LOADER32_LIB = "libproot-loader32.so"

    /** Guest entry process: Alpine's busybox ash as a login shell. */
    const val GUEST_SHELL = "/bin/sh"

    /**
     * Honest gate: only a structurally READY runtime may be entered. The
     * terminal-level gate (`uname; id; echo hello` in the guest) is the user's
     * M2.3 device acceptance (docs/TESTING.md §8).
     */
    fun canEnterLinuxShell(state: RuntimeState): Boolean = state == RuntimeState.READY

    /**
     * Pure preflight: returns a human-readable reason why a Linux shell launch
     * would fail, or null when every precondition holds. [buildLaunchSpec]
     * throws with exactly this message, and the UI calls this first so a
     * failure is surfaced honestly IN the app — never as a process crash
     * (v0.3.0 regression: extractNativeLibs=false left nativeLibraryDir empty
     * and a bare require() escaped the click handler, killing the process).
     */
    fun preconditionProblem(nativeLibraryDir: String, rootfsDir: File): String? {
        if (!rootfsDir.isDirectory) {
            return "Runtime rootfs not found at ${rootfsDir.absolutePath} — install or repair the Linux environment from Diagnostics."
        }
        val proot = File(nativeLibraryDir, PROOT_LIB)
        if (!proot.isFile) {
            return "proot binary is not present in the app's native library directory ($nativeLibraryDir). " +
                "This build cannot start the Linux guest — please report which APK you installed."
        }
        val loader = File(nativeLibraryDir, LOADER_LIB)
        if (!loader.isFile) {
            return "proot loader is not present in the app's native library directory ($nativeLibraryDir). " +
                "This build cannot start the Linux guest — please report which APK you installed."
        }
        return null
    }

    data class LaunchSpec(
        val executable: String,
        val arguments: List<String>,
        val environment: List<String>,
        val workingDirectory: String,
        val guestLabel: String,
    )

    fun buildLaunchSpec(
        nativeLibraryDir: String,
        rootfsDir: File,
        hostCwd: File,
        prootTmpDir: File,
        term: String = "xterm-256color",
    ): LaunchSpec {
        // Same contract as [preconditionProblem], thrown so programmatic
        // callers get a hard, honest failure (UI callers preflight instead).
        when (val problem = preconditionProblem(nativeLibraryDir, rootfsDir)) {
            null -> Unit
            else -> throw IllegalArgumentException(problem)
        }

        val arguments = listOf(
            "--kill-on-exit",
            "--rootfs=${rootfsDir.absolutePath}",
            "--root-id",
            "--cwd=/root",
            "--bind=/dev",
            "--bind=/proc",
            "--bind=/sys",
            GUEST_SHELL,
            "-l",
        )

        val proot = File(nativeLibraryDir, PROOT_LIB)
        val loader = File(nativeLibraryDir, LOADER_LIB)
        val environment = mutableListOf(
            "PROOT_LOADER=${loader.absolutePath}",
            "PROOT_TMP_DIR=${prootTmpDir.absolutePath}",
            "HOME=/root",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "TERM=$term",
            "LANG=C.UTF-8",
            "TMPDIR=/tmp",
        )
        // 32-bit-guest loader (arm64 and x86_64 builds only) when shipped.
        val loader32 = File(nativeLibraryDir, LOADER32_LIB)
        if (loader32.isFile) {
            environment.add("PROOT_LOADER_32=${loader32.absolutePath}")
        }

        return LaunchSpec(
            executable = proot.absolutePath,
            arguments = arguments,
            environment = environment,
            workingDirectory = hostCwd.absolutePath,
            guestLabel = "Alpine Linux",
        )
    }
}

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
 *
 * v0.3.2 device lesson (Samsung SM-F711B recording 2026-09-01 11:02): bionic's
 * dynamic linker searches only its default paths (/system/lib64, /vendor/…)
 * plus LD_LIBRARY_PATH — it does NOT search the app's nativeLibraryDir. proot
 * DT_NEEDED libtalloc.so therefore died with `CANNOT LINK EXECUTABLE …:
 * library "libtalloc.so" not found` the moment it was execve()'d. The sandbox
 * rehearsal masked this: scripts/rehearse_m23_gate.sh exports LD_LIBRARY_PATH
 * in the shell, glibc-style. The spec environment now ships LD_LIBRARY_PATH
 * itself, and argv[0] is the executable path (exec convention; bionic names
 * argv[0] in link errors and proot's getopt would otherwise never see the
 * first real flag).
 */
object RuntimeProcessLauncher {

    const val PROOT_LIB = "libproot.so"
    const val TALLOC_LIB = "libtalloc.so"
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
        val talloc = File(nativeLibraryDir, TALLOC_LIB)
        if (!talloc.isFile) {
            return "proot's runtime dependency $TALLOC_LIB is not present in the app's native library " +
                "directory ($nativeLibraryDir) — the guest would die with a linker error. " +
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
        guestCommand: List<String> = listOf(GUEST_SHELL, "-l"),
    ): LaunchSpec {
        // Same contract as [preconditionProblem], thrown so programmatic
        // callers get a hard, honest failure (UI callers preflight instead).
        when (val problem = preconditionProblem(nativeLibraryDir, rootfsDir)) {
            null -> Unit
            else -> throw IllegalArgumentException(problem)
        }
        require(guestCommand.isNotEmpty()) {
            "guestCommand must not be empty — proot would have nothing to exec"
        }

        val proot = File(nativeLibraryDir, PROOT_LIB)
        val loader = File(nativeLibraryDir, LOADER_LIB)

        // execvp(cmd, argv) passes this array as argv verbatim
        // (terminal-emulator jni/termux.c), so argv[0] MUST be the executable
        // path: proot's getopt starts at argv[1], and bionic quotes argv[0] in
        // link/exec error messages (v0.3.1 device recording showed
        // CANNOT LINK EXECUTABLE "--kill-on-exit" for exactly this reason).
        val arguments = listOf(
            proot.absolutePath,
            "--kill-on-exit",
            "--rootfs=${rootfsDir.absolutePath}",
            "--root-id",
            "--cwd=/root",
            "--bind=/dev",
            "--bind=/proc",
            "--bind=/sys",
        ) + guestCommand

        val environment = mutableListOf(
            // v0.3.2: bionic resolves proot's DT_NEEDED libtalloc.so from here
            // (see class KDoc) — the rehearsal exported this in the shell, the
            // device has no shell to do it, so the spec carries it itself.
            "LD_LIBRARY_PATH=${nativeLibraryDir}",
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

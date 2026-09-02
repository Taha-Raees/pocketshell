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
 *
 * v0.4.2 device lesson (same device, 2026-09-02 screenshots: "Permission
 * denied" on every apk fetch while bytes clearly flowed): package-command
 * specs do NOT bind /proc into the guest. apk-tools 3.0.x (HAVE_O_TMPFILE
 * build, src/io.c __apk_ostream_to_file/fdo_close) downloads every cached
 * object — APKINDEX and packages alike — into an ANONYMOUS O_TMPFILE file
 * and commits it with `linkat(AT_FDCWD, "/proc/self/fd/N", atfd, name,
 * AT_SYMLINK_FOLLOW)`. AOSP system/sepolicy app_neverallows.te forbids that
 * for every untrusted app domain: `neverallow all_untrusted_apps
 * file_type:file link;` — apps keep create/rename/unlink on their own data
 * (create_file_perms deliberately has no `link`), so the kernel returns
 * EACCES and apk cancels the WHOLE download (linkat failure →
 * apk_ostream_cancel(-errno), no retry/fallback). Observed shape on the
 * device: index bytes download, then `updating and opening …: Permission
 * denied` — invisible to host rehearsals (no SELinux) and immune to v0.4.1's
 * cache binds (the denial is on the link OPERATION, not the path). Without
 * a /proc bind apk's own is_proc_fd_ok() (access("/proc/self/fd")) is false
 * and it uses the named-tmpfile + renameat commit path — plain
 * create/rename/unlink, fully allowed. apk needs /proc for nothing else in
 * this flow (find_mountpoint degrades to a no-op; the cache remount path
 * only triggers for read-only caches).
 *
 * v0.5.0 device report (2026-09-02 10:03: manual `apk update` in the Linux
 * Shell died with the SAME Permission denied): INTERACTIVE sessions dropped
 * the /proc bind too — apk worked everywhere, but the guest had no procfs at
 * all, so `ps`, `top`, `htop` had nothing to read. That trade is what M2.6
 * reverses (docs/M2.6-RESEARCH.md): the guest's libapk is patched (one
 * checksum-pinned byte — see GuestApkCompat) so is_proc_fd_ok() is
 * permanently false, and interactive sessions bind a REAL /proc again via
 * [GuestExecutionProfile.INTERACTIVE_TERMINAL]. The two launch policies are
 * now explicit profiles on the SAME builder/proot/launcher — configuration
 * only, never a duplicated runtime:
 *
 *  - INTERACTIVE_TERMINAL: full guest devices, PTY, shared apk cache binds
 *    and /proc WHEN the patched apk library is verified; without that
 *    verification sessions degrade honestly to the v0.5.0 shape (no /proc).
 *  - PACKAGE_OPERATION: minimal mounts (no /proc — enforced in the builder
 *    with require()), the proven-safe apk commit environment.
 */

/**
 * The two guest launch policies (M2.6, docs/M2.6-RESEARCH.md §4.2). Both run
 * the SAME proot binary, the SAME rootfs, the SAME shared apk cache — only
 * the mount configuration differs, and the difference is pinned by tests so
 * the profiles cannot drift.
 */
enum class GuestExecutionProfile(val description: String) {
    /**
     * Linux Shell and catalog-app sessions: PTY, full guest devices, shared
     * apk cache, and a real /proc bind when the guest apk is fd-link-safe
     * ([GuestApkCompat] verified). /proc exposes the Android host procfs
     * filtered by the kernel's hidepid=2 app isolation — real process tools
     * see the app's own real process tree, honestly.
     */
    INTERACTIVE_TERMINAL(
        "interactive shell: full devices, PTY, /proc when the guest apk is fd-link-safe",
    ),

    /**
     * Every app-side apk exec (update/search/add/del/info): minimal mounts,
     * NO /proc — the environment where apk's stock commit path cannot hit the
     * SELinux linkat neverallow. Never trade this away; the builder refuses.
     */
    PACKAGE_OPERATION(
        "package operation: minimal mounts, no /proc (SELinux-safe apk commit environment)",
    ),
}

object RuntimeProcessLauncher {

    const val PROOT_LIB = "libproot.so"
    const val TALLOC_LIB = "libtalloc.so"
    const val LOADER_LIB = "libproot-loader.so"
    const val LOADER32_LIB = "libproot-loader32.so"

    /** Guest entry process: Alpine's busybox ash as a login shell. */
    const val GUEST_SHELL = "/bin/sh"

    /** Guest cache paths covered by the apk cache binds (apk-tools 3 layout). */
    const val GUEST_APK_CACHE_ETC = "/etc/apk/cache"
    const val GUEST_APK_CACHE_VAR = "/var/cache/apk"

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

    /**
     * v0.6.0 — THE interactive-session policy (M2.6). Every guest session the
     * app spawns (Linux Shell and catalog-app sessions alike) uses this
     * shape, parameterised on [procEnabled]:
     *
     * - procEnabled=true: REAL /proc is bound — `ps`, `top`, `htop` work.
     *   Callers may only pass true after [GuestApkCompat] reported the guest
     *   apk library fd-link-safe; otherwise the session's own manual `apk`
     *   would die at the SELinux linkat neverallow again.
     * - procEnabled=false (the honest default): the v0.5.0 shape — no /proc;
     *   apk works via its renameat commit, process tools fail with their own
     *   errors, Diagnostics explains why.
     * - The SAME app-owned apk cache binds the package operations use, so
     *   the session's manual apk shares one index/package cache with the
     *   UI (the 2026-09-02 10:03 session also showed "31 distinct packages"
     *   from a rootfs-internal stale cache — now impossible: one cache).
     */
    fun buildSessionSpec(
        nativeLibraryDir: String,
        rootfsDir: File,
        hostCwd: File,
        prootTmpDir: File,
        guestCommand: List<String>,
        apkCacheDir: File,
        procEnabled: Boolean = false,
    ): LaunchSpec = buildLaunchSpec(
        nativeLibraryDir = nativeLibraryDir,
        rootfsDir = rootfsDir,
        hostCwd = hostCwd,
        prootTmpDir = prootTmpDir,
        guestCommand = guestCommand,
        apkCacheDir = apkCacheDir,
        profile = GuestExecutionProfile.INTERACTIVE_TERMINAL,
        procEnabled = procEnabled,
    )

    data class LaunchSpec(
        val executable: String,
        val arguments: List<String>,
        val environment: List<String>,
        val workingDirectory: String,
        val guestLabel: String,
        val profile: GuestExecutionProfile = GuestExecutionProfile.INTERACTIVE_TERMINAL,
    )

    fun buildLaunchSpec(
        nativeLibraryDir: String,
        rootfsDir: File,
        hostCwd: File,
        prootTmpDir: File,
        term: String = "xterm-256color",
        guestCommand: List<String> = listOf(GUEST_SHELL, "-l"),
        apkCacheDir: File? = null,
        profile: GuestExecutionProfile = GuestExecutionProfile.INTERACTIVE_TERMINAL,
        procEnabled: Boolean = profile == GuestExecutionProfile.INTERACTIVE_TERMINAL,
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
        // Profile-drift guard (M2.6): the package-operation environment exists
        // precisely because apk must run WITHOUT /proc on Android. A caller
        // asking for /proc here is a bug, not a preference.
        require(!(profile == GuestExecutionProfile.PACKAGE_OPERATION && procEnabled)) {
            "PACKAGE_OPERATION must not bind /proc — the SELinux linkat neverallow " +
                "makes every apk commit fail; use the patched-guest INTERACTIVE_TERMINAL profile"
        }

        val proot = File(nativeLibraryDir, PROOT_LIB)
        val loader = File(nativeLibraryDir, LOADER_LIB)

        // execvp(cmd, argv) passes this array as argv verbatim
        // (terminal-emulator jni/termux.c), so argv[0] MUST be the executable
        // path: proot's getopt starts at argv[1], and bionic quotes argv[0] in
        // link/exec error messages (v0.3.1 device recording showed
        // CANNOT LINK EXECUTABLE "--kill-on-exit" for exactly this reason).
        val arguments = mutableListOf(
            proot.absolutePath,
            "--kill-on-exit",
            "--rootfs=${rootfsDir.absolutePath}",
            "--root-id",
            "--cwd=/root",
            "--bind=/dev",
        )
        // /proc policy (see class KDoc + docs/M2.6-RESEARCH.md): interactive
        // sessions bind a REAL /proc only when the guest apk is fd-link-safe
        // (patched libapk verified — GuestApkCompat); package operations NEVER
        // do (require-guarded above), so apk always keeps its allowed
        // named-tmpfile + renameat commit path.
        if (procEnabled) {
            arguments.add("--bind=/proc")
        }
        arguments.add("--bind=/sys")
        if (apkCacheDir != null) {
            // v0.4.1 device lesson (Samsung SM-F711B): apk fetches died with
            // EACCES before ever reaching the network — the cache write path
            // must never depend on rootfs-internal permissions. Bind two
            // app-owned host dirs over apk-tools 3's cache locations
            // (etc/apk/cache is the default, var/cache/apk the fallback), so
            // the package cache lives OUTSIDE the rootfs entirely. Same proot
            // bind mechanism as /dev,/proc,/sys — no new exec infrastructure.
            val etcCache = File(apkCacheDir, "etc").apply { mkdirs() }
            val varCache = File(apkCacheDir, "var").apply { mkdirs() }
            arguments.add("--bind=${etcCache.absolutePath}:${GUEST_APK_CACHE_ETC}")
            arguments.add("--bind=${varCache.absolutePath}:${GUEST_APK_CACHE_VAR}")
        }
        arguments.addAll(guestCommand)

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
            profile = profile,
        )
    }
}

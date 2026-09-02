package app.pocketshell.terminal

import android.content.Context
import java.io.File

/**
 * Builds the real shell environment for PocketShell sessions (brief §1/§19).
 *
 * M1 runs the Android system shell (/system/bin/sh — mksh) with toybox applets
 * providing the M1 minimum command set (echo, pwd, ls, cd, mkdir, rm, cat,
 * clear). No custom userspace exists yet — that is M2 scope (docs/RESEARCH.md
 * §3.2, Decision D3).
 */
object ShellEnvironment {

    const val SHELL_PATH = "/system/bin/sh"
    /** The guest shell inside the Alpine rootfs (proot argv tail). */
    const val SHELL_PATH_GUEST = "/bin/sh"
    const val TRANSCRIPT_ROWS = 2000

    fun homeDir(context: Context): File =
        File(context.filesDir, "home").apply { mkdirs() }

    fun tmpDir(context: Context): File =
        File(context.cacheDir, "tmp").apply { mkdirs() }

    fun ensureDirs(context: Context) {
        homeDir(context)
        tmpDir(context)
    }

    /** Environment handed to the shell process. Kept minimal and honest. */
    fun environment(context: Context): Array<String> = arrayOf(
        "PATH=/system/bin:/system/xbin",
        "HOME=${homeDir(context).absolutePath}",
        "TMPDIR=${tmpDir(context).absolutePath}",
        "TERM=xterm-256color",
        "LANG=C.UTF-8",
        "SHELL=$SHELL_PATH",
        "ANDROID_ROOT=/system",
        "ANDROID_DATA=/data",
        "POCKETSHELL=1",
    )
}

package app.pocketshell.diagnostics

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import app.pocketshell.terminal.ShellEnvironment
import app.pocketshell.terminal.TerminalSessionManager

/**
 * Read-only runtime facts (brief §34: diagnostics must be true or absent).
 * Every value here is queried from the real system at snapshot time.
 */
object Diagnostics {

    data class Row(
        val label: String,
        val value: String,
        /** null = informational; true/false = pass/fail colouring. */
        val ok: Boolean? = null,
    )

    fun snapshot(context: Context): List<Row> {
        val appContext = context.applicationContext
        val versionName = try {
            val pm = appContext.packageManager
            pm.getPackageInfo(appContext.packageName, 0).versionName ?: "unknown"
        } catch (e: PackageManager.NameNotFoundException) {
            "unknown"
        }

        val shellExists = java.io.File(ShellEnvironment.SHELL_PATH).exists()
        val home = ShellEnvironment.homeDir(appContext)
        val tmp = ShellEnvironment.tmpDir(appContext)
        val abis = Build.SUPPORTED_ABIS.joinToString(", ")
        val sessions = TerminalSessionManager.sessions.value

        return listOf(
            Row("PocketShell version", versionName),
            Row("Android version", "API ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})"),
            Row("Device", "${Build.MANUFACTURER} ${Build.MODEL}"),
            Row("Supported ABIs", abis),
            Row("64-bit runtime", if (Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()) "yes" else "no"),
            Row(
                "System shell",
                "${ShellEnvironment.SHELL_PATH} — ${if (shellExists) "present" else "MISSING"}",
                shellExists,
            ),
            Row("PTY native library", "libtermux.so loaded in this process", true),
            Row("HOME directory", home.absolutePath, home.isDirectory),
            Row("TMPDIR", tmp.absolutePath, tmp.isDirectory),
            Row(
                "Open sessions",
                if (sessions.isEmpty()) "none"
                else "${sessions.size} total, ${sessions.count { !it.isFinished }} running",
            ),
            Row("Permissions requested", "FOREGROUND_SERVICE, POST_NOTIFICATIONS only"),
        )
    }
}

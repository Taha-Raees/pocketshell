package app.pocketshell.gui

import android.content.Context
import app.pocketshell.runtime.RuntimeProcessLauncher
import app.pocketshell.runtime.RuntimeStorage
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Proot-backed [GuiRuntimeManager.GuestClientRunner]: the SAME launcher
 * contract as every other guest exec (one proot argv/env shape), pointed at
 * the GUI client command. stdout/stderr are drained on demand so a chatty
 * client cannot block; [GuestClientProcess.destroy] is the lifecycle kill.
 */
class ProcessBuilderGuestClientRunner(
    private val context: Context,
) : GuiRuntimeManager.GuestClientRunner {

    override fun startGuestCommand(
        command: List<String>,
        guestCwd: String,
    ): GuiRuntimeManager.GuestClientProcess {
        val appContext = context.applicationContext
        val storage = RuntimeStorage(appContext.noBackupFilesDir)
        val spec = RuntimeProcessLauncher.buildLaunchSpec(
            nativeLibraryDir = appContext.applicationInfo.nativeLibraryDir,
            rootfsDir = storage.rootfsDir,
            hostCwd = appContext.filesDir,
            prootTmpDir = File(appContext.noBackupFilesDir, "tmp"),
            guestCommand = command,
            guestCwd = guestCwd,
        )
        val process = ProcessBuilder(spec.executable, *spec.arguments.toTypedArray())
            .directory(File(spec.workingDirectory))
            .apply {
                environment().clear()
                spec.environment.forEach { pair -> environment()[pair.substringBefore("=")] = pair.substringAfter("=") }
            }
            .start()
        // keep pipes drained on daemon threads: a blocked client stdout
        // would otherwise stall the guest when the OS pipe buffer fills.
        fun drain(stream: java.io.InputStream) = Thread {
            try {
                stream.readBytes()
            } catch (_: java.io.IOException) {
            }
        }.apply { isDaemon = true; start() }
        drain(process.inputStream)
        drain(process.errorStream)
        return object : GuiRuntimeManager.GuestClientProcess {
            private val exited = Thread {
                try {
                    process.waitFor()
                } catch (_: InterruptedException) {
                }
            }.apply { isDaemon = true; start() }

            override fun pollExit(): Int? =
                if (exited.isAlive) null else process.exitValue()

            override fun destroy() {
                process.destroy()
                if (!process.waitFor(3, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                }
            }
        }
    }
}

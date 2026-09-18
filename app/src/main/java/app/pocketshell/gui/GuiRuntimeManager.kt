package app.pocketshell.gui

import android.content.Context
import android.view.Surface
import app.pocketshell.runtime.RuntimeStorage
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Process-scoped owner of the GUI runtime lifecycle (P3):
 *
 *     prepare (dirs, xkb data, client binary) → start (libmc thread)
 *     → attach surface → guest client session → stop (clean teardown)
 *
 * Honest states only: every transition is caused by a real event; [error]
 * carries the failure reason verbatim. A stopped runtime leaves no socket
 * file and no guest process behind (checked in unit + device tests).
 */
class GuiRuntimeManager(
    context: Context,
    private val runner: GuestClientRunner,
) {
    /** The guest client side (deterministic v1 client / Electron). */
    interface GuestClientRunner {
        fun startGuestCommand(command: List<String>, guestCwd: String): GuestClientProcess
    }

    interface GuestClientProcess {
        /** Non-blocking; null while alive. */
        fun pollExit(): Int?

        /** Forceful terminate. Idempotent. */
        fun destroy()
    }

    sealed class State {
        data object Idle : State()
        data object Preparing : State()
        data object Running : State()
        data object Stopped : State()
        data class Error(val reason: String) : State()
    }

    private val appContext = context.applicationContext
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    private var guestProcess: GuestClientProcess? = null

    val storage: RuntimeStorage = RuntimeStorage(appContext.noBackupFilesDir)

    fun guiDirOnHost(): File = GuiGuestContract.guiDir(storage.rootfsDir)
    fun socketFile(): File = GuiGuestContract.socketPath(storage.rootfsDir)
    fun xkbRoot(): File = File(appContext.filesDir, "gui/xkb")
    fun clientBinaryOnHost(): File = GuiGuestContract.guiDir(storage.rootfsDir)
        .resolve(GuiGuestContract.GUEST_CLIENT)

    /** True when the structural preflight passes (runtime present + ABI). */
    fun preflightProblem(): String? {
        if (!File(storage.rootfsDir, "etc/alpine-release").isFile) {
            return "Linux runtime is not installed yet"
        }
        if (McGuiRuntimeHolder.abi != "arm64-v8a") {
            return "GUI runtime supports arm64-v8a only (this device: ${McGuiRuntimeHolder.abi})"
        }
        return null
    }

    /**
     * Idempotent prepare: socket dir inside the guest rootfs, xkb data (from
     * the packaged asset), and the static guest client binary.
     */
    fun prepare(xkbAssetBytes: ByteArray, clientBinaryBytes: ByteArray): File {
        _state.value = State.Preparing
        try {
            val dir = guiDirOnHost()
            dir.mkdirs()
            val xkb = xkbRoot()
            if (xkbAssetBytes.isNotEmpty() && !File(xkb, ".installed").exists()) {
                extractTarGz(xkbAssetBytes, xkb)
                File(xkb, ".installed").createNewFile()
            }
            val client = clientBinaryOnHost()
            if (!client.isFile || client.length() != clientBinaryBytes.size.toLong()) {
                client.parentFile?.mkdirs()
                client.writeBytes(clientBinaryBytes)
                client.setExecutable(true)
            }
            // stale socket from a crashed generation must never survive prepare
            socketFile().delete()
            _state.value = State.Idle
            return dir
        } catch (t: Throwable) {
            _state.value = State.Error("prepare failed: ${t.message}")
            throw t
        }
    }

    /** Starts the compositor loop thread. 0 = running. */
    fun start(width: Int, height: Int): Int {
        if (_state.value == State.Running) return 0
        val rc = McGuiRuntime.start(
            socketFile().absolutePath,
            xkbRoot().absolutePath,
            width,
            height,
        )
        if (rc == 0) {
            _state.value = State.Running
        } else {
            _state.value = State.Error("start failed rc=$rc")
        }
        return rc
    }

    fun attach(surface: Surface, width: Int, height: Int) {
        McGuiRuntime.attachSurface(surface, width, height)
    }

    fun detach() {
        McGuiRuntime.detachSurface()
    }

    /* ---- input pass-throughs (UI thread → command queue) ---- */
    fun key(evdevCode: Int, down: Boolean) = McGuiRuntime.key(evdevCode, down)
    fun pointerMotion(x: Int, y: Int) = McGuiRuntime.pointerMotion(x, y)
    fun pointerButton(evdevBtn: Int, down: Boolean) = McGuiRuntime.pointerButton(evdevBtn, down)
    fun pointerScroll(steps: Int) = McGuiRuntime.pointerScroll(steps)
    fun touch(action: Int, id: Int, x: Int, y: Int) = McGuiRuntime.touch(action, id, x, y)

    fun killClient() {
        guestProcess?.destroy()
        guestProcess = null
    }

    /** Extracts packaged xkb data + guest client from APK assets. */
    fun prepareFromAssets() {
        val xkb = appContext.assets.open("gui/xkb-data.tar.gz").use { it.readBytes() }
        val client = appContext.assets.open("gui/libguiclient.so").use { it.readBytes() }
        prepare(xkb, client)
    }

    /** Launches [program] (guest path) in the guest wired to the compositor. */
    fun startGuestClient(program: String): Unit {
        guestProcess = runner.startGuestCommand(
            GuiGuestContract.guestCommand(program),
            GuiGuestContract.GUEST_GUI_DIR,
        )
    }

    fun guestClientExit(): Int? = guestProcess?.pollExit()

    /** Clean shutdown: client, compositor, socket. Idempotent. */
    fun stop() {
        guestProcess?.destroy()
        guestProcess = null
        McGuiRuntime.stop()
        socketFile().delete()
        if (_state.value !is State.Error) _state.value = State.Stopped
    }

    private fun extractTarGz(bytes: ByteArray, target: File) {
        val tin = java.util.zip.GZIPInputStream(bytes.inputStream())
        val tarIn = org.apache.commons.compress.archivers.tar.TarArchiveInputStream(tin)
        target.mkdirs()
        while (true) {
            val entry = tarIn.nextTarEntry ?: break
            val out = File(target, entry.name)
            if (entry.isDirectory) {
                out.mkdirs()
                continue
            }
            out.parentFile?.mkdirs()
            out.outputStream().use { tarIn.copyTo(it) }
        }
    }
}

/** ABI + load guard kept in one place (tests stub around it). */
object McGuiRuntimeHolder {
    val abi: String = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"

    private val instance = AtomicReference<GuiRuntimeManager?>(null)

    fun get(context: Context, runner: GuiRuntimeManager.GuestClientRunner): GuiRuntimeManager =
        instance.updateAndGet { it ?: GuiRuntimeManager(context, runner) }!!
}

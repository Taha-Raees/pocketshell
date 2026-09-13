package app.pocketshell.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient

/**
 * PocketShell implementation of the upstream [TerminalSessionClient] contract.
 *
 * One instance per session. Screen-update callbacks ([onTextChanged],
 * [onColorsChanged]) are dispatched by [TerminalSession]'s MainThreadHandler —
 * they arrive on the **main thread**, so it is safe to touch views from
 * [onScreenUpdate]. The manager additionally posts its own state transitions
 * to the main thread.
 */
class PocketShellSessionClient(
    private val context: Context,
    private val onTitleChanged: () -> Unit = {},
    private val onSessionFinished: () -> Unit = {},
    /**
     * M7.2 P2 — invoked ONCE per session, on the main thread, right after the
     * PTY child was actually forked (upstream fires [setTerminalShellPid] from
     * initializeEmulator before any waiter thread can deliver an exit). This is
     * the REAL "process started" signal the lifecycle machine requires — the
     * lazy fork (audit §1.3) means entry-existence alone proves nothing.
     */
    private val onProcessStarted: () -> Unit = {},
    /** Invoked on the main thread whenever this session's screen changes. */
    private val onScreenUpdate: () -> Unit = {},
    /**
     * M7.2 P6 — invoked on the main thread when the running program rings
     * the terminal bell. The FACT travels out through this lambda; this
     * class names no detection/notification symbols (the waiting-evidence
     * boundary stays intact — the client is a PTY callback adapter).
     */
    private val onBell: () -> Unit = {},
    /**
     * M7.2: in-band terminal notification (OSC 9 / OSC 777) received.
     * Leaves through this plain lambda seam; names no detection/notification
     * symbols, preserving the waiting-evidence boundary.
     */
    private val onNotification: (title: String, message: String) -> Unit = { _, _ -> },
) : TerminalSessionClient {

    private val clipboard: ClipboardManager? =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

    // ---- lifecycle -----------------------------------------------------------

    override fun onTextChanged(changedSession: TerminalSession) {
        // Upstream contract (see Termux TermuxTerminalSessionSessionClient):
        // TerminalView does NOT observe session data — the host must call
        // TerminalView#onScreenUpdated() or output stays invisible until an
        // unrelated layout pass forces a repaint.
        onScreenUpdate()
    }

    override fun onTitleChanged(changedSession: TerminalSession) = onTitleChanged()

    override fun onSessionFinished(finishedSession: TerminalSession) = onSessionFinished()

    override fun onBell(session: TerminalSession) {
        // M7.2 P6: the bell is a FACT (the program requested attention).
        // It leaves through the [onBell] seam to the manager's activity
        // pipeline; visual/audio bell polish remains M1.3.
        Log.d(LOG_TAG, "bell")
        onBell()
    }

    override fun onNotification(session: TerminalSession, title: String, message: String) {
        Log.d(LOG_TAG, "notification title=\"$title\" message=\"$message\"")
        onNotification(title, message)
    }

    // ---- clipboard -------------------------------------------------------------

    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        clipboard?.setPrimaryClip(ClipData.newPlainText("PocketShell", text))
    }

    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        // The vendored TextSelectionCursorController's Paste action ends HERE:
        // TerminalSession.onPasteTextFromClipboard() only forwards to this
        // client callback — nothing upstream performs the paste itself.
        // v0.4.4 device lesson (user report 2026-09-02: "I can see option for
        // paste but nothing paste when choosed"): the previous implementation
        // was an empty body, so the Paste item silently did nothing.
        // Read the real clipboard and paste through TerminalEmulator.paste —
        // the same semantics upstream Termux relies on (strips escape/C1
        // control bytes, converts LF/CRLF to CR, honours bracketed paste
        // mode). An absent/empty clip pastes nothing — an honest no-op.
        if (session == null) return
        val text = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
        if (!text.isNullOrEmpty()) {
            session.emulator?.paste(text)
        }
    }

    // ---- colors / cursor -------------------------------------------------------

    override fun onColorsChanged(session: TerminalSession) {
        // Same upstream contract as onTextChanged: refresh the view so the
        // new color scheme is applied immediately.
        onScreenUpdate()
    }

    override fun onTerminalCursorStateChange(state: Boolean) {
        // M1: ignore cursor enable/disable requests.
    }

    override fun getTerminalCursorStyle(): Int? = null // null → upstream default

    override fun setTerminalShellPid(session: TerminalSession, pid: Int) {
        Log.d(LOG_TAG, "session pid=$pid")
        onProcessStarted()
    }

    // ---- logging (honest, no-op safe) ------------------------------------------

    override fun logError(tag: String, message: String) { Log.e(sanitize(tag), message) }
    override fun logWarn(tag: String, message: String) { Log.w(sanitize(tag), message) }
    override fun logInfo(tag: String, message: String) { Log.i(sanitize(tag), message) }
    override fun logDebug(tag: String, message: String) { Log.d(sanitize(tag), message) }
    override fun logVerbose(tag: String, message: String) { Log.v(sanitize(tag), message) }

    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {
        Log.e(sanitize(tag), message, e)
    }

    override fun logStackTrace(tag: String, e: Exception) {
        Log.e(sanitize(tag), "stack trace", e)
    }

    private fun sanitize(tag: String): String = if (tag.length > 23) tag.take(23) else tag

    companion object {
        const val LOG_TAG = "PocketShell"
    }
}

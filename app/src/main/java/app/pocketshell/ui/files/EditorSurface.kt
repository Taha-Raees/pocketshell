package app.pocketshell.ui.files

import kotlinx.coroutines.flow.StateFlow

/**
 * M7.0.0 Phase 6 — the narrow contract between the quick text editor screen
 * and [app.pocketshell.EditorViewModel], in the exact spirit of
 * [FilesOpsSurface]: state flows to render plus intent methods to dispatch.
 * The screen performs ZERO filesystem operations and never sees a
 * [app.pocketshell.files.StorageArea] — it renders [EditorState] verbatim
 * and sends edits and dialog decisions.
 *
 * Editor scope (the product rule): a QUICK TEXT VIEWER/EDITOR — open a
 * file, read it, change it, save it, leave. No syntax highlighting, no line
 * numbers, no search, no undo history, no autosave, no save-as, no
 * encoding choice: UTF-8 only, honestly refused when a file is not text.
 */
interface EditorSurface {

    /** The whole editor state, rendered verbatim. */
    val state: StateFlow<EditorState>

    /** The user typed in the buffer. */
    fun editText(text: String)

    /** Save the buffer to the file it was opened from (gates may ask first). */
    fun save()

    /** The user answered an external-change / deleted-file save confirmation. */
    fun resolveSaveConfirm(saveAnyway: Boolean)

    /** Dismiss the save error banner. */
    fun dismissSaveError()

    /** Back was pressed while the buffer is dirty — show the guard dialog. */
    fun requestBackGuard()

    /** The back-guard dialog's "Keep editing" — stay in the editor. */
    fun keepEditing()

    /** The back-guard dialog's "Save" — close the guard, save, and leave when
     *  the save lands (through any confirmation it needs). */
    fun saveFromGuard()

    /** The back-guard dialog's "Discard" — drop the edits; the screen then leaves. */
    fun discardAndLeave()

    /** Re-open the current file (the load-error state's Retry). */
    fun retryLoad()
}

/**
 * The editor's render state. One data class, rendered verbatim; every
 * nullable field is an honest sub-state (a refusal, an error, a dialog).
 */
data class EditorState(
    /** True once a file has been launched into the editor at all. */
    val hasDocument: Boolean = false,
    val loading: Boolean = false,
    /** Compact area label for the header ("Linux", "Downloads", …). */
    val areaLabel: String = "",
    /** The file's listing name (header title). */
    val fileName: String = "",
    /** The area-native path string ("/root/notes.txt" — never a faked path). */
    val pathDisplay: String = "",
    /** The live buffer. */
    val text: String = "",
    /** The content as last loaded or saved — the baseline of [dirty]. */
    val savedText: String = "",
    // --------------------------------------------------- honest refusal states
    /** Non-null: the file exceeds the quick-editor cap (value = real size). */
    val tooLargeBytes: Long? = null,
    /** Non-null: the content has a NUL byte — binary, never decoded. */
    val binaryRefused: Boolean = false,
    /** Non-null: the content is not valid UTF-8 — refused, not mangled. */
    val notUtf8Refused: Boolean = false,
    /** Non-null: the read failed (verbatim area reason); Retry is offered. */
    val loadError: String? = null,
    /** The file's size at load/last-save, for the status line. */
    val sizeBytes: Long? = null,
    // ------------------------------------------------------------ save state
    val saving: Boolean = false,
    /** The last save failure, verbatim; persists until dismissed or re-saved. */
    val saveError: String? = null,
    /** True when [saveError] was a policy refusal (DENIED) vs a failure. */
    val saveDenied: Boolean = false,
    /** Bumped on every successful save — re-triggers the UI's auto-clear notice. */
    val saveNoticeSeq: Long = 0L,
    // ---------------------------------------------------------------- dialogs
    /** Non-null: a save needs explicit confirmation (external change / deleted). */
    val confirmSave: SaveConfirmState? = null,
    /** The dirty-back guard dialog is showing. */
    val backGuard: Boolean = false,
    /** Set when the user chose Save/Discard in the guard; the screen leaves
     *  once the save completes (or immediately for Discard). */
    val pendingLeave: Boolean = false,
) {
    /** The buffer differs from what is on disk as last seen by the editor. */
    val dirty: Boolean get() = text != savedText

    /** True when the document is loaded and shown as editable text. */
    val readable: Boolean get() = hasDocument && !loading &&
        tooLargeBytes == null && !binaryRefused && !notUtf8Refused && loadError == null
}

/** The wording of the save confirmation ([missing] = file deleted vs changed). */
data class SaveConfirmState(
    val missing: Boolean,
)

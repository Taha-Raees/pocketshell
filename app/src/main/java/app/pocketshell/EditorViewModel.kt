package app.pocketshell

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.pocketshell.files.FsEntry
import app.pocketshell.files.OpResult
import app.pocketshell.files.editor.EditorLaunch
import app.pocketshell.files.editor.OpenDecision
import app.pocketshell.files.editor.SaveGate
import app.pocketshell.files.editor.TextDocument
import app.pocketshell.ui.files.EditorSurface
import app.pocketshell.ui.files.EditorState
import app.pocketshell.ui.files.SaveConfirmState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * M7.0.0 Phase 6 — the quick text editor's state holder.
 *
 * THIN like [FilesViewModel]: every decision lives in the pure
 * [TextDocument] model (decode, caps, save gate) and every byte moves
 * through the Phase 2 [app.pocketshell.files.StorageArea] the launch carried
 * — this class only dispatches to its serial worker and publishes state.
 * It never touches java.io and never sees a raw path beyond the validated
 * [EditorLaunch] it was handed.
 *
 * THREADING (the same discipline as the Files screen, zero shared state):
 * one confined serial worker ([editorIo]) and one in-flight job slot — the
 * newest intent wins, a superseded job's result is never published. The
 * Files screen keeps its own separate serial worker; the two never share
 * mutable state, so cross-ViewModel safety rests on the storage engine's
 * atomic writes (a listing can only ever see a file before or after a
 * save, never half of it) plus the (size, mtime) save gate, which also
 * catches changes made by explorer operations or a guest shell.
 *
 * PROCESS SCOPED (like every screen's ViewModel): the loaded document and
 * its dirty buffer survive Home↔Editor navigation and rotation. The screen
 * can only be left while clean (the back guard forces Save/Discard first),
 * so [open] is by construction never called over unsaved edits. Process
 * death loses unsaved content — that is stated honestly to the user in the
 * guard dialog, never faked with an autosave.
 */
class EditorViewModel(application: Application) : AndroidViewModel(application), EditorSurface {

    private val editorIo = Dispatchers.IO.limitedParallelism(1)

    private val _state = MutableStateFlow(EditorState())
    override val state: StateFlow<EditorState> = _state.asStateFlow()

    /** The launch currently open — null until the first [open]. */
    private var launch: EditorLaunch? = null

    /**
     * The (size, mtime) snapshot of the file as the editor last saw it —
     * captured at load and refreshed after every save; the save gate
     * compares it against a fresh stat. Null means "never seen" and always
     * forces the explicit confirmation (never a blind save).
     */
    private var snapshot: FsEntry? = null

    private var loadJob: kotlinx.coroutines.Job? = null
    private var saveJob: kotlinx.coroutines.Job? = null

    // ------------------------------------------------------------- open

    /**
     * Open a file for quick editing. Only reachable with a clean buffer (the
     * back guard guarantees the screen is left clean), so this replaces any
     * previous document. The load itself runs on the serial worker.
     */
    fun open(newLaunch: EditorLaunch) {
        launch = newLaunch
        _state.value = EditorState(
            hasDocument = true,
            loading = true,
            areaLabel = newLaunch.areaLabel,
            fileName = newLaunch.name,
            pathDisplay = newLaunch.path.value,
        )
        loadInternal(newLaunch)
    }

    override fun retryLoad() {
        val current = launch ?: return
        if (_state.value.saving) return
        // Retry exists for the load-error state only — never over a buffer
        // the user may have edited (there is no "reload and lose edits").
        if (_state.value.readable) return
        _state.value = _state.value.copy(loading = true, loadError = null)
        loadInternal(current)
    }

    private fun loadInternal(current: EditorLaunch?) {
        val target = current ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val decision = withContext(editorIo) {
                TextDocument.decideOpen(
                    name = target.name,
                    read = target.area.readBytes(target.path, TextDocument.MAX_QUICK_EDIT_BYTES.toLong()),
                )
            }
            // A superseded load (a newer open/retry) never publishes.
            if (launch !== target) return@launch
            when (decision) {
                is OpenDecision.Text -> {
                    snapshot = withContext(editorIo) { target.area.stat(target.path) }
                    _state.value = _state.value.copy(
                        loading = false,
                        text = decision.content,
                        savedText = decision.content,
                        sizeBytes = snapshot?.sizeBytes,
                        tooLargeBytes = null,
                        binaryRefused = false,
                        notUtf8Refused = false,
                        loadError = null,
                    )
                }
                is OpenDecision.TooLarge -> _state.value = _state.value.copy(
                    loading = false,
                    tooLargeBytes = decision.sizeBytes,
                    text = "",
                    savedText = "",
                )
                is OpenDecision.Binary -> _state.value = _state.value.copy(
                    loading = false,
                    binaryRefused = true,
                )
                is OpenDecision.NotUtf8 -> _state.value = _state.value.copy(
                    loading = false,
                    notUtf8Refused = true,
                )
                is OpenDecision.Failed -> _state.value = _state.value.copy(
                    loading = false,
                    loadError = decision.reason,
                )
            }
        }
    }

    // ------------------------------------------------------------- edit

    override fun editText(text: String) {
        val s = _state.value
        if (!s.readable || s.saving) return
        _state.value = s.copy(text = text)
    }

    // ------------------------------------------------------------- save

    override fun save() {
        val current = launch ?: return
        val s = _state.value
        if (!s.dirty || s.saving || s.confirmSave != null) return
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            _state.value = _state.value.copy(saving = true)
            val gate = withContext(editorIo) {
                TextDocument.saveGate(snapshot, current.area.stat(current.path))
            }
            if (launch !== current) return@launch
            when (gate) {
                is SaveGate.Clear -> write(current, confirmed = false)
                is SaveGate.ChangedExternally -> {
                    _state.value = _state.value.copy(saving = false)
                    _confirmSave(missing = false)
                }
                is SaveGate.Missing -> {
                    _state.value = _state.value.copy(saving = false)
                    _confirmSave(missing = true)
                }
            }
        }
    }

    private fun _confirmSave(missing: Boolean) {
        _state.value = _state.value.copy(confirmSave = SaveConfirmState(missing = missing))
    }

    override fun resolveSaveConfirm(saveAnyway: Boolean) {
        val current = launch ?: return
        _state.value = _state.value.copy(confirmSave = null)
        if (!saveAnyway) {
            // A cancelled confirm also cancels a pending leave from the guard.
            _state.value = _state.value.copy(pendingLeave = false)
            return
        }
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            _state.value = _state.value.copy(saving = true)
            // The gate was answered by the user; no re-gate (that would be
            // another race). Quick-editor semantics: an explicitly confirmed
            // save is last-writer-wins through the area's atomic write.
            write(current, confirmed = true)
        }
    }

    /**
     * The actual write — runs on the serial worker with the gate already
     * decided. [confirmed] marks saves that answered an external-change or
     * deleted-file confirmation (reported verbatim in the notice).
     */
    private suspend fun write(current: EditorLaunch, confirmed: Boolean) {
        val s = _state.value
        val bytes = TextDocument.encode(s.text)
        val result = withContext(editorIo) {
            try {
                current.area.writeBytesAtomic(current.path, bytes)
            } catch (t: Throwable) {
                OpResult.failed("could not save: ${t.message ?: t.javaClass.simpleName}")
            }
        }
        if (launch !== current) return
        if (result.success) {
            snapshot = withContext(editorIo) { current.area.stat(current.path) }
            _state.value = _state.value.copy(
                saving = false,
                savedText = s.text,
                sizeBytes = snapshot?.sizeBytes,
                saveError = null,
                saveDenied = false,
                saveNoticeSeq = _state.value.saveNoticeSeq + 1,
            )
        } else {
            _state.value = _state.value.copy(
                saving = false,
                saveError = result.reason ?: "the save failed",
                saveDenied = result.kind == OpResult.Kind.DENIED,
            )
        }
    }

    override fun dismissSaveError() {
        _state.value = _state.value.copy(saveError = null, saveDenied = false)
    }

    // -------------------------------------------------------- back guard

    override fun requestBackGuard() {
        val s = _state.value
        if (s.readable && s.dirty && !s.backGuard) {
            _state.value = s.copy(backGuard = true)
        }
    }

    override fun keepEditing() {
        _state.value = _state.value.copy(backGuard = false, pendingLeave = false)
    }

    override fun saveFromGuard() {
        val s = _state.value
        _state.value = s.copy(backGuard = false, pendingLeave = true)
        save()
    }

    override fun discardAndLeave() {
        val s = _state.value
        // Drop the buffer back to the on-disk baseline; the screen sees the
        // clean state and performs the actual back navigation.
        _state.value = s.copy(
            backGuard = false,
            pendingLeave = false,
            text = s.savedText,
        )
    }
}

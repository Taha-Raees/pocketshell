package app.pocketshell.ui.files

import app.pocketshell.files.AreaId
import app.pocketshell.files.AreaPath
import app.pocketshell.files.EntryKind
import app.pocketshell.files.PendingTransfer
import kotlinx.coroutines.flow.StateFlow

/**
 * M7.0.0 Phase 4 — the operation surface the Files UI is allowed to see.
 *
 * The narrow contract between the screen and [app.pocketshell.FilesViewModel]:
 * state flows to render plus intent methods to dispatch. The UI performs ZERO
 * filesystem operations and never sees a [app.pocketshell.files.StorageArea] —
 * it sends NAMES (from listings) and dialog decisions, exactly like the Phase 3
 * navigation discipline.
 */
interface FilesOpsSurface {

    // ------------------------------------------------------------ render state

    /** The one pending copy/move, shown as the paste banner. */
    val pending: StateFlow<PendingTransfer?>

    /** The outcome of the last operation (honest success / failure / refusal). */
    val notice: StateFlow<OpsNotice?>

    /** A Replace/Cancel confirmation the user must decide. */
    val confirmReplace: StateFlow<ReplaceRequest?>

    /** The New Folder / New File prompt (owned here so errors can round-trip). */
    val newDialog: StateFlow<NewEntryDialog?>

    /** The Rename prompt (same round-trip). */
    val renameDialog: StateFlow<RenameEntryDialog?>

    /** The Delete confirmation. */
    val deleteConfirm: StateFlow<DeleteConfirmState?>

    // ---------------------------------------------------------------- intents

    /** Mark the listing entry [name] to be copied (one operation at a time). */
    fun startCopy(name: String)

    /** Mark the listing entry [name] to be moved. */
    fun startMove(name: String)

    /** Drop the pending copy/move without doing anything. */
    fun cancelPending()

    /** Paste the pending entry into the CURRENT directory (asks on collision). */
    fun pasteHere()

    /** The user answered a Replace confirmation. */
    fun resolveReplace(replace: Boolean)

    /** Open the New Folder / New File prompt for the CURRENT directory. */
    fun openNewDialog(folder: Boolean)

    /** Open the prompt INSIDE the folder [folderName] from the listing. */
    fun openNewDialogInside(folderName: String, folder: Boolean)

    fun dismissNewDialog()

    /** Submit a name for the open New prompt; errors come back on the dialog. */
    fun submitNewName(name: String)

    /** Open the Rename prompt for the listing entry [name]. */
    fun openRenameDialog(name: String)

    fun dismissRenameDialog()

    /** Submit a new name for the open Rename prompt; collisions ask Replace. */
    fun submitRename(newName: String)

    /** Open the Delete confirmation for the listing entry [name]. */
    fun openDeleteConfirm(name: String)

    fun dismissDeleteConfirm()

    /** The user confirmed the deletion shown by the confirmation. */
    fun confirmDelete()

    fun dismissNotice()
}

/** One operation outcome, rendered verbatim. [seq] re-triggers auto-dismiss. */
data class OpsNotice(
    val text: String,
    val isError: Boolean,
    val seq: Long,
)

/** What to run if the user explicitly chooses Replace. Carries its own area so a
 * navigation between the collision check and the answer can never retarget it. */
data class ReplaceRequest(
    val opLabel: String,
    val name: String,
    val existingKind: EntryKind,
    val command: OpsCommand,
)

sealed interface OpsCommand {
    data class Paste(
        val pending: PendingTransfer,
        val targetAreaId: AreaId,
        val target: AreaPath,
    ) : OpsCommand

    data class Rename(
        val areaId: AreaId,
        val path: AreaPath,
        val newName: String,
    ) : OpsCommand
}

/** The New Folder / New File prompt state; [error] round-trips honest failures. */
data class NewEntryDialog(
    val areaId: AreaId,
    val targetDir: AreaPath,
    val folder: Boolean,
    val error: String? = null,
)

/** The Rename prompt state. */
data class RenameEntryDialog(
    val areaId: AreaId,
    val path: AreaPath,
    val currentName: String,
    val error: String? = null,
)

/** The Delete confirmation state (the warning line derives from [kind]). */
data class DeleteConfirmState(
    val name: String,
    val kind: EntryKind,
)

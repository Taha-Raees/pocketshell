package app.pocketshell.ui.files

import app.pocketshell.files.AreaId
import app.pocketshell.files.AreaPath
import app.pocketshell.files.EntryKind
import app.pocketshell.files.PendingTransfer
import app.pocketshell.files.TerminalLaunchResolution
import app.pocketshell.files.saf.SafFolderInfo
import kotlinx.coroutines.flow.StateFlow

/**
 * M7.0.0 Phase 4 (+ Phase 5 Android bridge) — the operation surface the
 * Files UI is allowed to see.
 *
 * The narrow contract between the screen and [app.pocketshell.FilesViewModel]:
 * state flows to render plus intent methods to dispatch. The UI performs ZERO
 * filesystem operations and never sees a [app.pocketshell.files.StorageArea] —
 * it sends NAMES (from listings), picker RESULTS and dialog decisions,
 * exactly like the Phase 3 navigation discipline.
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

    // ------------------------------------------- Phase 5 — Android bridge

    /** The user-granted Android folders with their honest access state. */
    val safFolders: StateFlow<List<SafFolderInfo>>

    /** A staged file ready to be handed to the Android share sheet. */
    val shareReady: StateFlow<ShareReady?>

    /** Everything the UI needs to open the system save dialog for an export. */
    val exportPrompt: StateFlow<ExportPrompt?>

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

    // ------------------------------------------- Phase 5 — Android bridge

    /**
     * The system folder picker returned a tree URI (add OR reconnect). The
     * picker callback must already have taken the persistable permission.
     * A URI that matches an existing folder replaces it (fresh access
     * probe); a new one joins the switcher.
     */
    fun addSafFolderPicked(uriString: String)

    /** Drop a user-granted folder and release its permission. */
    fun removeSafFolder(uriString: String)

    /** Stage the listing entry [name] (a file) for the Android share sheet. */
    fun requestShare(name: String)

    /** Open the system save dialog for the listing entry [name] (a file). */
    fun requestExport(name: String)

    /** The share sheet was launched (or dismissed) — drop the staged offer. */
    fun consumeShareReady()

    /** The save dialog was opened (or dismissed) — drop the prompt. */
    fun consumeExportPrompt()

    /**
     * The document picker returned a file to IMPORT into the CURRENT
     * directory; collisions go through the existing Replace/Cancel flow.
     */
    fun importPicked(uriString: String)

    /** The save dialog returned the destination for the pending export. */
    fun exportTargetPicked(uriString: String)

    // --------------------------------------------- Phase 7 — Open Terminal Here

    /**
     * Resolve the TAPPED directory entry ([selectedEntry], from the action
     * sheet) into an "Open Terminal Here" launch (M7.0.0 Phase 7; p7.1 — the
     * launch opens THE TAPPED FOLDER, not the browsed location) — the narrow
     * terminal-launch intent: validate + resolve ONLY, exactly like every
     * other intent here. No filesystem I/O, no session creation, no
     * navigation ever happens inside this call — the caller receives either
     * [TerminalLaunchResolution.Ready] (it then asks the TerminalViewModel
     * for a real session and navigates only on its `onReady`) or
     * [TerminalLaunchResolution.NotSupported] (the honest area-boundary or
     * stale-entry reason, already surfaced as this VM's notice; the UI
     * stays where it is).
     */
    fun terminalLaunch(selectedEntry: String): TerminalLaunchResolution
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

    /** Phase 5: an imported document whose destination collided (Replace flow). */
    data class ImportFile(
        val uriString: String,
        val targetAreaId: AreaId,
        val target: AreaPath,
        val name: String,
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

/** Phase 5: a staged file the UI hands to the Android share sheet. The URI
 * belongs to the share-staged COPY in the app cache — never to the original
 * location — and carries temporary read permission only. */
data class ShareReady(
    val name: String,
    val uriString: String,
    val mimeType: String,
)

/** Phase 5: the parameters for the system save dialog (ACTION_CREATE_DOCUMENT).
 * The ViewModel keeps the source file privately; the UI only sees what the
 * dialog needs. */
data class ExportPrompt(
    val name: String,
    val mimeType: String,
)

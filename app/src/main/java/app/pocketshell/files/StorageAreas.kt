package app.pocketshell.files

import android.content.Context
import android.os.Environment
import app.pocketshell.files.saf.AndroidDocumentArea
import app.pocketshell.files.saf.DocumentsContractBackend
import app.pocketshell.files.saf.SafFolders
import app.pocketshell.runtime.RuntimeStorage
import java.io.File

/**
 * M7.0.0 Phase 2 — the concrete areas the File Explorer exposes.
 *
 * One factory per domain. Both return null honestly when the domain is not
 * available (guest runtime not installed, external storage not mounted) — the
 * UI renders that as an unavailable area, never as an empty fake.
 *
 * No permissions are involved anywhere: the guest rootfs and the shelf are
 * app-owned storage. SAF tree grants (Phase 5) will add their own factory
 * over persisted document-tree URIs.
 */
object StorageAreas {

    /**
     * The PocketShell Linux storage — direct File IO over the guest rootfs.
     * Guest "/" is the rootfs; /root is the user's home; mutations in
     * runtime-critical prefixes are refused by the guest policy.
     */
    fun guest(context: Context): FileDirArea? {
        val appContext = context.applicationContext
        val rootfs = RuntimeStorage(appContext.noBackupFilesDir).rootfsDir
        return FileDirArea.create(
            root = rootfs,
            id = AreaId(AreaKind.GUEST_LINUX),
            displayName = "PocketShell Linux",
            policy = FileDirArea.MutationPolicy.guestRuntime(),
        )
    }

    /**
     * The app's own external downloads directory — the SAME destination the
     * Companion DownloadManager has always written to ("Downloading to app
     * storage…"). This is where a ZIP downloaded from an AI website in the
     * Companion browser already lands, so it is the natural first stop of the
     * "download → import → unzip" workflow. Plain app-owned storage: full
     * operations, no policy, no permission.
     */
    fun androidShelf(context: Context): FileDirArea? {
        val appContext = context.applicationContext
        val dir: File = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: return null
        return FileDirArea.create(
            root = dir,
            id = AreaId(AreaKind.ANDROID_SHELF),
            displayName = "Android Downloads (app storage)",
            policy = FileDirArea.MutationPolicy.OPEN,
        )
    }

    /**
     * M7.0.0 Phase 5 — ONE user-granted SAF document tree ("Android folder").
     *
     * Unlike the two factories above this NEVER returns null: a revoked
     * grant still produces an area whose every operation reports the
     * revocation honestly, so the switcher can keep showing the folder and
     * the Files screen can offer Reconnect / Remove — never a silent
     * disappearance and never a fake empty folder. The display name comes
     * from the provider when reachable, otherwise from the URI itself
     * (a display name — never a POSIX path).
     *
     * No permission is involved here beyond the grant the USER chose in the
     * system picker; this factory never discovers or touches any other
     * Android storage.
     */
    fun safTree(
        context: Context,
        treeUri: String,
        label: String? = null,
        onAccessLost: (() -> Unit)? = null,
    ): AndroidDocumentArea {
        val appContext = context.applicationContext
        val backend = DocumentsContractBackend.fromContext(appContext, treeUri)
        val display = label
            ?: backend.rootDisplayName()
            ?: SafFolders.labelFromTreeUri(treeUri)
        return AndroidDocumentArea.create(
            id = AndroidDocumentArea.areaIdFor(treeUri),
            displayName = display,
            backend = backend,
            onAccessLost = onAccessLost,
        )
    }
}

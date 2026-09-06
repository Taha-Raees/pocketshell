package app.pocketshell.ui.files

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.sp
import app.pocketshell.files.saf.SafFolderInfo
import app.pocketshell.ui.home.HomeTokens
import app.pocketshell.ui.system.MidnightBanner
import app.pocketshell.ui.theme.TerminalTheme

/**
 * M7.0.0 Phase 5 — the ANDROID-side surfaces of the Files screen, extracted
 * so FilesScreen keeps being a listing+navigation screen. PRESENTATION +
 * SYSTEM-PICKER WIRING only: every composable here renders bridge state and
 * dispatches intents; none of them touch the filesystem.
 *
 * Security properties this wiring exists to keep:
 *  - folders enter ONLY through the system OpenDocumentTree picker, and the
 *    persistable permission is taken right there (the ViewModel never
 *    discovers storage on its own);
 *  - shared files go out ONLY as FileProvider content URIs over the staged
 *    CACHE copy, with temporary read permission — never as real paths;
 *  - imports/exports are one-shot documents; a dismissed dialog drops the
 *    pending prompt honestly.
 */

/** The three launch callbacks the Files screen menu items need. */
class FilesBridgeLaunchers internal constructor(
    /** Opens the system folder picker (OpenDocumentTree). */
    val pickFolder: () -> Unit,
    /** Opens the system document picker for an import (OpenDocument). */
    val pickImportFile: () -> Unit,
)

@Composable
fun rememberFilesBridgeLaunchers(ops: FilesOpsSurface): FilesBridgeLaunchers {
    val context = LocalContext.current

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            // Persist the grant HERE, at the moment the user chose the
            // folder — with exactly the flags the system handed over.
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            ops.addSafFolderPicked(uri.toString())
        }
    }

    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { ops.importPicked(it.toString()) }
    }

    return FilesBridgeLaunchers(
        pickFolder = { folderPicker.launch(null) },
        pickImportFile = { importPicker.launch(arrayOf("*/*")) },
    )
}

/**
 * The effects side of the bridge: launches the Android share sheet when a
 * staged file is ready, and the system save dialog when an export prompt is
 * open. Both consume their offer immediately after launching — a dismissed
 * system dialog simply means nothing happens.
 */
@Composable
fun FilesBridgeEffects(ops: FilesOpsSurface) {
    val context = LocalContext.current
    val shareReady by ops.shareReady.collectAsStateWithLifecycle()
    val exportPrompt by ops.exportPrompt.collectAsStateWithLifecycle()

    val savePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val uri: Uri? = result.data?.data
        if (uri != null) {
            ops.exportTargetPicked(uri.toString())
        }
        // else: the user dismissed the save dialog — the prompt was already
        // consumed at launch; nothing pending remains either way.
    }

    LaunchedEffect(shareReady) {
        shareReady?.let { ready ->
            val stagedUri = Uri.parse(ready.uriString)
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = ready.mimeType
                putExtra(Intent.EXTRA_STREAM, stagedUri)
                // Grant temporary read permission to the CHOSEN receiver only.
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newRawUri(ready.name, stagedUri)
            }
            runCatching {
                context.startActivity(Intent.createChooser(sendIntent, "Share \"${ready.name}\""))
            }
            ops.consumeShareReady()
        }
    }

    LaunchedEffect(exportPrompt) {
        exportPrompt?.let { prompt ->
            val saveIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = prompt.mimeType
                putExtra(Intent.EXTRA_TITLE, prompt.name)
            }
            runCatching { savePicker.launch(saveIntent) }
            ops.consumeExportPrompt()
        }
    }
}

/**
 * The honest banner for a folder whose grant Android took back: no fake
 * empty directory, no crash — exactly the two real options.
 */
@Composable
fun SafRevokedBanner(
    folder: SafFolderInfo,
    onReconnect: () -> Unit,
    onRemove: () -> Unit,
) {
    MidnightBanner(
        message = "Access to \"${folder.label}\" is no longer available.",
        failed = true,
        actions = {
            TextButton(onClick = onRemove) {
                Text(
                    "Remove",
                    fontFamily = TerminalTheme.mono,
                    fontSize = 13.sp,
                    color = HomeTokens.textDim,
                )
            }
            TextButton(onClick = onReconnect) {
                Text(
                    "Reconnect",
                    fontFamily = TerminalTheme.mono,
                    fontSize = 13.sp,
                    color = HomeTokens.accent,
                )
            }
        },
    )
}

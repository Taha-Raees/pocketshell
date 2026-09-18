package app.pocketshell.widget

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pocketshell.packages.PackageGateway
import app.pocketshell.runtime.RuntimeManager
import app.pocketshell.runtime.RuntimeState
import app.pocketshell.runtime.RuntimeStorage
import app.pocketshell.ui.home.HomeTokens
import app.pocketshell.ui.theme.TerminalTheme
import app.pocketshell.widget.probe.StorageBreakdown
import app.pocketshell.widget.probe.StorageScan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * M8 — the Storage widget: LINUX storage (the guest rootfs + the bound apk
 * cache), sized where the bytes actually live (app-owned host dirs). The
 * scan is a real tree walk — expensive by nature — so the refresh policy is
 * ON-DEMAND + CACHED: first Home visit scans, then results refresh only
 * after [STALE_MS]; nothing ever polls (docs/M8-WIDGET-SYSTEM.md §5).
 *
 * The tap opens Files at the guest root — the place storage is actually
 * cleaned from. A budget-truncated scan says so; numbers are never silent
 * guesses.
 */
object StorageWidget : HomeWidget() {

    override val spec = WidgetSpec(
        id = "storage",
        name = "Storage",
        summary = "Linux-side storage — rootfs, caches, what eats the space",
        isCore = false,
    )

    override fun tapLabel(context: WidgetContentContext): String = "Open Files"

    override fun tapAction(context: WidgetContentContext): (() -> Unit) = { context.nav.openGuestFiles() }

    private const val MAX_ROWS = 3
    internal const val STALE_MS = 30 * 60 * 1000L

    @Composable
    override fun Content(context: WidgetContentContext) {
        val ui by StorageWidgetModel.state.collectAsStateWithLifecycle()
        val appContext = LocalContext.current.applicationContext

        LaunchedEffect(context.runtimeState) {
            if (context.runtimeState == RuntimeState.READY) {
                StorageWidgetModel.scanIfNeeded(appContext)
            }
        }

        val palette = WidgetPalette.of(spec.tone)
        val breakdown = (ui as? StorageWidgetModel.Ui.Data)?.breakdown

        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                StorageMark(size = 32.dp)
                Spacer(Modifier.weight(1f))
                if (breakdown != null) {
                    Text(
                        text = formatBytes(breakdown.totalBytes),
                        fontFamily = TerminalTheme.mono,
                        fontSize = 11.sp,
                        color = palette.dim,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            breakdown?.subtrees?.take(MAX_ROWS)?.forEach { subtree ->
                Text(
                    text = "${displaySubtreeName(subtree.name)}  ${formatBytes(subtree.bytes)}",
                    fontFamily = TerminalTheme.mono,
                    fontSize = 11.sp,
                    color = palette.title,
                    maxLines = 1,
                )
            }
            if (breakdown != null && breakdown.subtrees.size > MAX_ROWS) {
                Text(
                    text = "+${breakdown.subtrees.size - MAX_ROWS} more",
                    fontFamily = TerminalTheme.mono,
                    fontSize = 11.sp,
                    color = palette.dim,
                    maxLines = 1,
                )
            }
            if (breakdown != null) Spacer(Modifier.height(4.dp))
            Text(
                text = "Storage",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = palette.title,
            )
            Spacer(Modifier.height(2.dp))
            val stateLine = when {
                context.runtimeState != RuntimeState.READY -> "Linux not installed yet"
                breakdown == null -> "Scanning…"
                breakdown.truncated -> "Used so far (partial scan)"
                else -> "Used by Linux"
            }
            Text(
                text = stateLine,
                style = MaterialTheme.typography.bodySmall,
                color = if (context.runtimeState != RuntimeState.READY) palette.dim else palette.accent,
                maxLines = 1,
            )
        }
    }

    /** Raw top-level names are Linux truths; only the cache row gets a label. */
    private fun displaySubtreeName(name: String): String = when (name) {
        StorageScan.APK_CACHE_NAME -> "Package cache"
        else -> name
    }

    /** Human bytes: one decimal under 10 units, whole otherwise, KiB base. */
    internal fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kib = bytes / 1024.0
        if (kib < 1024) return number(kib) + " KB"
        val mib = kib / 1024.0
        if (mib < 1024) return number(mib) + " MB"
        val gib = mib / 1024.0
        return number(gib) + " GB"
    }

    private fun number(value: Double): String =
        if (value < 10) String.format(java.util.Locale.US, "%.1f", value)
        else value.toLong().toString()
}

/**
 * The scan cache: process-scoped, no polling — a scan runs only when Home
 * shows the widget AND the cached result is absent or stale.
 */
internal object StorageWidgetModel {

    sealed interface Ui {
        data object Idle : Ui
        data class Data(val breakdown: StorageBreakdown) : Ui
    }

    private val _state = MutableStateFlow<Ui>(Ui.Idle)
    val state: StateFlow<Ui> = _state

    suspend fun scanIfNeeded(context: Context) {
        if (RuntimeManager.state.value != RuntimeState.READY) return
        val current = (_state.value as? Ui.Data)?.breakdown
        val age = current?.let { System.currentTimeMillis() - it.scannedAtMillis } ?: Long.MAX_VALUE
        if (current != null && age < StorageWidget.STALE_MS) return
        val storage = RuntimeStorage(context.applicationContext.noBackupFilesDir)
        val apkCache = PackageGateway.apkCacheDir(storage)
        val breakdown = withContext(Dispatchers.IO) {
            StorageScan.scanGuestStorage(storage.rootfsDir, apkCache)
        }
        _state.value = Ui.Data(breakdown)
    }
}

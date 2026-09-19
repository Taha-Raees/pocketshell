package app.pocketshell.widget.storage

/**
 * M8.4 STORAGE — the application's PURE state model. No android imports:
 * every decision the card renders is a function over these types, which is
 * what makes the honest-state discipline testable on the JVM
 * (StorageUiTest) the same way ServersLayout / GitLayout are.
 */

// --------------------------------------------------------------- categories

/** The four rows of the overview — the fixed, honest partition of the answer. */
internal enum class StorageCategory(
    val id: String,
    val label: String,
    val clearable: Boolean,
) {
    RUNTIME("runtime", "Linux runtime", clearable = false),
    PACKAGE_CACHE("package-cache", "Package cache", clearable = true),
    SHARE_STAGING("share-staging", "Share staging", clearable = true),
    GUEST_CACHES("guest-caches", "Guest caches", clearable = false);

    companion object {
        fun byId(id: String): StorageCategory? = entries.firstOrNull { it.id == id }
    }
}

/**
 * The responsive contract — the card geometry is identical to the Servers
 * and Git applications, so the device-derived thresholds carry over:
 * COMPACT keeps one-line rows; ROOMY adds the status header, the footer
 * statistics and scrolling rows. Pure + JVM-tested (StorageUiTest).
 */
internal enum class StorageLayout(
    val showsStatusHeader: Boolean,
    val showsFooter: Boolean,
    val scrollsRows: Boolean,
) {
    COMPACT(showsStatusHeader = false, showsFooter = false, scrollsRows = false),
    ROOMY(showsStatusHeader = true, showsFooter = true, scrollsRows = true);

    companion object {
        const val ROOMY_MIN_WIDTH_DP = 420f
        const val ROOMY_MIN_HEIGHT_DP = 200f

        fun from(widthDp: Float, heightDp: Float): StorageLayout =
            if (widthDp >= ROOMY_MIN_WIDTH_DP && heightDp >= ROOMY_MIN_HEIGHT_DP) ROOMY else COMPACT
    }
}

// ------------------------------------------------------------------ snapshot

/** The runtime row (RuntimeStorageFacts, reused verbatim — never re-walked). */
internal data class RuntimeFacts(
    /** False when no runtime is on disk (bytes/fileCount are null then). */
    val present: Boolean,
    val bytes: Long?,
    val fileCount: Int?,
    val truncated: Boolean,
)

/** The guest-cache section's honest state (probe-driven, never invented). */
internal sealed interface GuestCaches {
    /** No probe has run yet this composition. */
    data object NotProbed : GuestCaches

    /** The runtime is not READY — the guest cannot be asked. */
    data object Unavailable : GuestCaches

    /** A real exec/parse failure, with its real reason. */
    data class Failed(val reason: String) : GuestCaches

    /** Measured sizes; absent caches simply have no row. */
    data class Sizes(val entries: List<GuestCacheEntry>) : GuestCaches
}

/** One full host measurement + the last guest probe result. */
internal data class StorageSnapshot(
    val runtime: RuntimeFacts,
    val apkCache: CategoryScan.SizeResult,
    val staging: CategoryScan.SizeResult,
    val guest: GuestCaches,
    /** Bytes available on the volume hosting the runtime; null when unreadable. */
    val freeBytes: Long?,
    /** True when ANY host walk hit its budget — every host size is a floor. */
    val hostTruncated: Boolean,
    val scannedAtMillis: Long,
)

// ------------------------------------------------------------- ui states

/** The card's top-level state: measuring until the first snapshot lands. */
internal sealed interface StorageUi {
    data object Measuring : StorageUi
    data class Ready(val snapshot: StorageSnapshot) : StorageUi
}

/** The two-step clear flow's state (explicit, cancellable, never auto-run). */
internal sealed interface ClearState {
    data object Idle : ClearState
    data object Running : ClearState
    data class Done(val filesDeleted: Int, val bytesFreed: Long) : ClearState
    /** Cancellation stopped the clear midway (sizes re-measure honestly). */
    data object Stopped : ClearState
    data class Failed(val reason: String) : ClearState
}

// ------------------------------------------------------------------ derived

/** What the two app-owned cache categories could reclaim right now. */
internal fun reclaimableBytes(snapshot: StorageSnapshot): Long =
    snapshot.apkCache.bytes + snapshot.staging.bytes

/** Total host-side bytes the app owns (runtime + both cache categories). */
internal fun totalHostBytes(snapshot: StorageSnapshot): Long =
    (snapshot.runtime.bytes ?: 0L) + snapshot.apkCache.bytes + snapshot.staging.bytes

/**
 * The guest caches' combined size; null when nothing was measured (not
 * probed / unavailable / failed / every entry unknown). Entries the du
 * could not size contribute nothing rather than a guess.
 */
internal fun guestTotalBytes(guest: GuestCaches): Long? {
    val sizes = guest as? GuestCaches.Sizes ?: return null
    val known = sizes.entries.filter { it.kilobytes != null }
    return if (known.isEmpty()) null else known.sumOf { it.kilobytes!! } * 1024L
}

/** The overview's honest one-line state (Servers/Git state-line discipline). */
internal fun overviewStateLine(ui: StorageUi): String {
    val ready = ui as? StorageUi.Ready
    return when {
        ready == null -> "Measuring…"
        !ready.snapshot.runtime.present &&
            ready.snapshot.guest == GuestCaches.Unavailable -> "Linux not ready"
        reclaimableBytes(ready.snapshot) > 0L ->
            "${app.pocketshell.widget.probe.StorageScan.formatBytes(reclaimableBytes(ready.snapshot))} reclaimable"
        else -> "Nothing reclaimable"
    }
}

/** The state line is accented when it carries a real, actionable number. */
internal fun overviewStateAccented(ui: StorageUi): Boolean =
    ui is StorageUi.Ready && reclaimableBytes(ui.snapshot) > 0L

/** The truncation honesty line (shown only when a walk hit its budget). */
internal const val TRUNCATION_NOTE =
    "Scan stopped at its file budget — sizes are floors, not exact totals."

/** A category row's right-hand value in the overview. */
internal fun categoryValue(category: StorageCategory, snapshot: StorageSnapshot): String {
    val format = app.pocketshell.widget.probe.StorageScan::formatBytes
    return when (category) {
        StorageCategory.RUNTIME ->
            snapshot.runtime.bytes?.let(format) ?: "not installed"
        StorageCategory.PACKAGE_CACHE -> format(snapshot.apkCache.bytes)
        StorageCategory.SHARE_STAGING -> format(snapshot.staging.bytes)
        StorageCategory.GUEST_CACHES -> guestTotalBytes(snapshot.guest)?.let(format) ?: "—"
    }
}

/** The preview page's headline: what will be removed, with its approx size. */
internal fun previewHeadline(size: CategoryScan.SizeResult): String = when {
    !size.exists || size.files == 0 -> "Empty — nothing to clear."
    else -> "${size.files} file${if (size.files == 1) "" else "s"} · about " +
        app.pocketshell.widget.probe.StorageScan.formatBytes(size.bytes)
}

/** The clear flow's honest line, per state. */
internal fun clearStateLine(state: ClearState): String = when (state) {
    is ClearState.Idle -> ""
    is ClearState.Running -> "Clearing…"
    is ClearState.Done ->
        "Cleared ${state.filesDeleted} file${if (state.filesDeleted == 1) "" else "s"} · " +
            "freed ${app.pocketshell.widget.probe.StorageScan.formatBytes(state.bytesFreed)}"
    is ClearState.Stopped -> "Stopped early — remaining sizes re-measured."
    is ClearState.Failed -> "Could not clear: ${state.reason}"
}

/** The guest section's honest one-line state. */
internal fun guestStateLine(guest: GuestCaches): String = when (guest) {
    is GuestCaches.NotProbed -> "Not probed"
    is GuestCaches.Unavailable -> "Linux not ready"
    is GuestCaches.Failed -> "Probe failed"
    is GuestCaches.Sizes ->
        if (guest.entries.isEmpty()) "No guest caches found" else "Measured in the guest"
}

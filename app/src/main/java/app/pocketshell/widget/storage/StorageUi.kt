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
 * COMPACT keeps one-line rows; ROOMY adds the status header. The four
 * category rows scroll at both densities. Pure + JVM-tested (StorageUiTest).
 */
internal enum class StorageLayout(
    val showsStatusHeader: Boolean,
) {
    COMPACT(showsStatusHeader = false),
    ROOMY(showsStatusHeader = true);

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

// ------------------------------------------------------------- M8.4.3 copy

/**
 * The analyzer copy every category page carries: WHY the category exists
 * (first line) and WHAT clearing it means (second line). Static explanatory
 * text — one place per category, written honestly, JVM-pinned by
 * StorageUiTest.
 */
internal data class CategoryCopy(
    val why: String,
    val consequence: String,
)

internal fun categoryCopy(category: StorageCategory): CategoryCopy = when (category) {
    StorageCategory.RUNTIME -> CategoryCopy(
        why = "The installed Linux guest itself — every package, tool and " +
            "guest cache lives inside this tree.",
        consequence = "Do NOT clear from outside the app — removing it by hand " +
            "is an uninstall; install, repair and remove live in Diagnostics.",
    )
    StorageCategory.PACKAGE_CACHE -> CategoryCopy(
        why = "Downloaded apk archives kept so package installs do not " +
            "re-download them every time.",
        consequence = "Safe to clear — apk re-downloads whatever it needs on " +
            "the next install; installed packages are not touched.",
    )
    StorageCategory.SHARE_STAGING -> CategoryCopy(
        why = "Short-lived staged copies of files shared out of this card, " +
            "kept inside the app's own cache.",
        consequence = "Safe to clear — the area refills on the next share and " +
            "cleans itself before each one.",
    )
    StorageCategory.GUEST_CACHES -> CategoryCopy(
        why = "Build-tool caches inside the guest (~/.npm, ~/.gradle, " +
            "~/.cargo, /tmp) — a breakdown of the runtime total, not extra space.",
        consequence = "Clear from a terminal — this card never deletes guest " +
            "files (for example: npm cache clean --force, or rm -rf ~/.cache/…).",
    )
}

/**
 * The guest-cache breakdown's row order: largest measured size first; an
 * entry du could not size sorts last (an honest unknown is not a zero to
 * be ranked). Stable for equal sizes. Pure — JVM-tested.
 */
internal fun sortedGuestCaches(entries: List<GuestCacheEntry>): List<GuestCacheEntry> =
    entries.sortedWith(compareByDescending<GuestCacheEntry> { it.kilobytes ?: Long.MIN_VALUE })

/**
 * The overview row's secondary fact — only numbers the snapshot already
 * holds (file counts from the existing walks, the guest entry count). No
 * new scanning, no invented numbers: null when the snapshot cannot say.
 */
internal fun categoryDetail(category: StorageCategory, snapshot: StorageSnapshot): String? =
    when (category) {
        StorageCategory.RUNTIME -> snapshot.runtime.fileCount?.let { files(it) }
        StorageCategory.PACKAGE_CACHE ->
            snapshot.apkCache.takeIf { it.exists }?.let { files(it.files) }
        StorageCategory.SHARE_STAGING ->
            snapshot.staging.takeIf { it.exists }?.let { files(it.files) }
        StorageCategory.GUEST_CACHES ->
            (snapshot.guest as? GuestCaches.Sizes)
                ?.takeIf { it.entries.isNotEmpty() }
                ?.let { "${it.entries.size} caches" }
    }

private fun files(count: Int): String = "${count} file${if (count == 1) "" else "s"}"

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

/**
 * The preview page's headline: what will be removed, with its approx size —
 * plus the census (largest single file) when the walk saw one. A lone file
 * IS the total, so the largest is stated only when it adds information
 * (never the same number twice).
 */
internal fun previewHeadline(size: CategoryScan.SizeResult): String = when {
    !size.exists || size.files == 0 -> "Empty — nothing to clear."
    else -> buildString {
        append("${size.files} file${if (size.files == 1) "" else "s"} · about ")
        append(app.pocketshell.widget.probe.StorageScan.formatBytes(size.bytes))
        val largest = size.largestFileName?.takeIf { size.files > 1 }
        if (largest != null) {
            append(" · largest: $largest (")
            append(app.pocketshell.widget.probe.StorageScan.formatBytes(size.largestFileBytes))
            append(")")
        }
    }
}

/**
 * The clear flow's honest line, per state — and for [ClearState.Done] the
 * whole BEFORE → OPERATION → AFTER arc in one line: what was freed, then
 * the cache's re-measured size once it arrives ("cache now Y"), or an
 * explicit "re-measuring…" while that measurement is still in flight.
 */
internal fun clearStateLine(
    state: ClearState,
    current: CategoryScan.SizeResult,
    measuring: Boolean,
): String = when (state) {
    is ClearState.Idle -> ""
    is ClearState.Running -> "Clearing…"
    is ClearState.Done -> {
        val freed = "Freed ${app.pocketshell.widget.probe.StorageScan.formatBytes(state.bytesFreed)} " +
            "(${state.filesDeleted} file${if (state.filesDeleted == 1) "" else "s"})"
        if (measuring) {
            "$freed · re-measuring…"
        } else {
            "$freed · cache now ${app.pocketshell.widget.probe.StorageScan.formatBytes(current.bytes)}"
        }
    }
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

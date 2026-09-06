package app.pocketshell.files

/**
 * M7.0.0 Phase 8 — file/folder NAME search inside ONE storage area.
 *
 * Deliberately NOT an indexing system: no background work, no persistent
 * index, no database, no content search, no fuzzy matching. The whole
 * feature is a bounded recursive walk over the Phase 2 [StorageArea.list]
 * API — the same primitive the explorer uses — so every safety rule the
 * areas already enforce (validated canonical paths, NOFOLLOW symlinks,
 * honest errors, SAF containment) applies to search unchanged. The storage
 * abstraction is NOT extended: search composes exactly what navigation
 * composes ([ExplorerOps.composeChild] — the one validated child
 * composition), which means a search result can never name a location the
 * explorer could not navigate to.
 *
 * QUERY SEMANTICS (pinned by FileSearchTest): the query is always DATA.
 * Matching is a literal, case-insensitive SUBSTRING test on the entry NAME
 * — the same lowercase convention the explorer's listing order uses. The
 * query is never interpreted as a regex, glob, shell fragment, or path:
 * a query "../etc" simply fails to match any name and can never influence
 * WHERE the search walks, because traversal targets come from listings,
 * never from the query.
 *
 * SYMLINK POLICY (mirrors the Phase 2 NOFOLLOW discipline): a SYMLINK node
 * is a matchable RESULT (by its own name) but is NEVER descended into —
 * not even when its target is a directory inside the same area. There is
 * no cycle-check bookkeeping to get wrong: without following links the
 * walked graph is the real directory tree, which is acyclic. SAF areas
 * have no symlink concept at all.
 *
 * SCOPE: the walk starts at the AREA ROOT and can never escape it — every
 * descended path is composed from the previous validated path and
 * re-validated. Results carry the area-native parent spine (the same shape
 * the explorer's location row already renders), so SAF areas stay honest:
 * a content:// tree is never dressed up as a POSIX path.
 *
 * LIMITS (explicit, honest, surfaced — never silently dropped): the walk
 * stops at [SearchLimits.maxResults] matches or [SearchLimits.maxDirectories]
 * visited directories, and the outcome says so ([SearchOutcome.Matches.truncated]
 * / [SearchOutcome.Matches.scanTruncated]). A sub-directory that cannot be
 * listed (vanished folder, provider hiccup, revoked grant mid-walk) is
 * counted in [SearchOutcome.Matches.skippedCount] with the first reason
 * carried verbatim — errors never masquerade as an empty result list. A
 * failing ROOT listing is a hard [SearchOutcome.Error].
 *
 * THREADING: blocking like every StorageArea consumer — the ViewModel runs
 * one search at a time on its own serial worker and cancels cooperatively
 * through [isCancelled] (checked between directories; a superseded search
 * returns null so its caller can never publish stale results).
 */
object FileSearch {

    /** Explicit traversal bounds. Defaults are the shipped product limits. */
    data class SearchLimits(
        /** Stop collecting matches after this many results. */
        val maxResults: Int = 200,
        /** Stop walking after this many visited directories. */
        val maxDirectories: Int = 2_000,
    )

    /** One match: the entry (exactly as its directory listed it) + WHERE. */
    data class SearchResult(
        val entry: FsEntry,
        /**
         * The validated area-native parent directory. Root-level results
         * have parent "/" — the area root itself is never a result.
         */
        val parent: AreaPath,
    ) {
        /**
         * Relative location context for display ("projects" for
         * /projects/notes.txt; "" at the area root). This is the area's own
         * navigation spine — honest for SAF areas too.
         */
        val relativeLocation: String get() = parent.value.trimStart('/')
    }

    /** The one outcome type of a finished (non-cancelled) search. */
    sealed interface SearchOutcome {

        /** A blank query — the input state. NEVER triggers a scan. */
        data object Idle : SearchOutcome

        /**
         * A completed walk. [truncated] / [scanTruncated] are the honest
         * limit disclosures; [skippedCount] / [firstError] are the honest
         * partial-failure disclosures (a folder that could not be listed
         * was NOT searched — never silently treated as empty).
         */
        data class Matches(
            val query: String,
            val results: List<SearchResult>,
            val truncated: Boolean = false,
            val scanTruncated: Boolean = false,
            val skippedCount: Int = 0,
            val firstError: String? = null,
        ) : SearchOutcome

        /** The area root itself could not be listed (revoked, vanished…). */
        data class Error(val reason: String) : SearchOutcome
    }

    /**
     * Search [area] from its root for names containing [query] as a literal
     * case-insensitive substring. Returns [SearchOutcome.Idle] for a blank
     * query (defensively — the ViewModel also refuses to dispatch one) and
     * null when [isCancelled] fires (the caller discards the work; partial
     * results are never published as complete).
     */
    fun search(
        area: StorageArea,
        root: AreaPath,
        query: String,
        limits: SearchLimits = SearchLimits(),
        isCancelled: () -> Boolean = { false },
    ): SearchOutcome? {
        if (query.isBlank()) return SearchOutcome.Idle
        if (isCancelled()) return null
        val needle = query.lowercase()

        val results = ArrayList<SearchResult>()
        var truncated = false
        var scanTruncated = false
        var skippedCount = 0
        var firstError: String? = null
        // Set when a match was found but DISCARDED because the result cap was
        // already full — the honest "there was more" signal (a silently
        // dropped match is exactly what this core must never do).
        var capReached = false

        // BFS from the area root: deterministic discovery order (top-level
        // matches surface first), FIFO of directories still to visit.
        val queue = ArrayDeque<AreaPath>()
        queue.addLast(root)
        var visited = 0

        while (queue.isNotEmpty()) {
            if (isCancelled()) return null
            if (visited >= limits.maxDirectories) {
                scanTruncated = true
                break
            }
            val dir = queue.removeFirst()
            visited++
            when (val listing = area.list(dir)) {
                is ListResult.Error -> {
                    // The ROOT failing is a hard error — a walk that cannot
                    // start must not pretend it found nothing.
                    if (dir.value == root.value) {
                        return SearchOutcome.Error(
                            "Could not search ${area.displayName}: ${listing.reason}",
                        )
                    }
                    // A failed sub-directory is an honest partial: count it,
                    // keep the first reason, keep going.
                    skippedCount++
                    if (firstError == null) firstError = listing.reason
                }
                is ListResult.Ok -> for (entry in listing.entries) {
                    val isMatch = entry.name.lowercase().contains(needle)
                    if (isMatch) {
                        if (results.size < limits.maxResults) {
                            results.add(SearchResult(entry = entry, parent = dir))
                        } else {
                            capReached = true
                        }
                    }
                    if (entry.kind == EntryKind.DIRECTORY && !capReached) {
                        // The ONE validated composition — the same call
                        // navigation uses; listings never produce invalid
                        // names, so a null here is fail-closed skipping.
                        ExplorerOps.composeChild(dir, entry.name)?.let { queue.addLast(it) }
                    }
                    // SYMLINK / OTHER: matchable above, never descended.
                }
            }
            // After a full directory: the cap is only "truncation" when the
            // walk actually stopped early — matches were dropped, or
            // directories remain unscanned. Hitting the cap exactly on the
            // last match of the last directory is NOT truncation.
            if (results.size >= limits.maxResults && (capReached || queue.isNotEmpty())) {
                truncated = true
                break
            }
        }
        if (capReached) truncated = true
        if (isCancelled()) return null

        results.sortWith(COMPARATOR)
        return SearchOutcome.Matches(
            query = query,
            results = results,
            truncated = truncated,
            scanTruncated = scanTruncated,
            skippedCount = skippedCount,
            firstError = firstError,
        )
    }

    /**
     * Deterministic result order, consistent with the explorer's listing
     * order: directories first, then case-insensitive name, then the
     * relative parent path as tie-break (duplicates distinguishable), then
     * the exact name.
     */
    private val COMPARATOR = compareBy<SearchResult>(
        { it.entry.kind != EntryKind.DIRECTORY },
        { it.entry.name.lowercase() },
        { it.parent.value },
        { it.entry.name },
    )
}

/**
 * The Files screen's search publication state (the ViewModel's search
 * StateFlow holds exactly one of these). Kept in the pure file so the UI
 * and tests share the model without touching the ViewModel.
 */
sealed interface FilesSearchState {

    /** No search mode work: mode closed, or open with an empty field. */
    data object Idle : FilesSearchState

    /** A walk is running for [query]. */
    data class Running(val query: String) : FilesSearchState

    /** The walk finished with the results (possibly honest partial/limits). */
    data class Done(val outcome: FileSearch.SearchOutcome.Matches) : FilesSearchState

    /** The walk could not start (root listing failed) — the honest reason. */
    data class Failed(val query: String, val reason: String) : FilesSearchState
}

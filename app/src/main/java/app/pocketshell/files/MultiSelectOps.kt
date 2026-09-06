package app.pocketshell.files

/**
 * M7.0.0 Phase 8.1 — multi-select operations.
 *
 * PURE and SYNCHRONOUS like [ExplorerOps]: no Android imports, no
 * coroutines. Every function performs blocking I/O only through the Phase 2
 * [StorageArea] abstraction and composes destinations only through
 * [ExplorerOps.composeChild] — the ONE validated child composition, so a
 * selected name can never address anything outside its area. NOTHING here
 * touches java.io directly.
 *
 * DESIGN (the smallest honest multi):
 *  - Selection is a set of NAMES from the CURRENT listing only. It never
 *    carries paths, so it cannot outlive the directory it was made in.
 *  - Copy/move reuse the EXISTING single-transfer engine verbatim: a multi
 *    clipboard is an ordered queue of fully-validated [PendingTransfer]s,
 *    executed one at a time by [pastePass]. Collisions interrupt the pass
 *    and go through the SAME Replace/Cancel confirmation the single paste
 *    has always used; Cancel stops with an honest partial summary and the
 *    remaining items untouched.
 *  - Delete reuses [StorageArea.delete] per name with the honest aggregate.
 *  - Every outcome is an honest aggregate: per-item failures keep their own
 *    reason and are surfaced, never swallowed, never turned into silence.
 */
object MultiSelectOps {

    /** One item that did not make it, with its honest reason. */
    data class MultiFailure(val name: String, val reason: String)

    /** The running aggregate of a multi operation. */
    data class MultiOutcome(
        val ok: Int,
        val failed: List<MultiFailure>,
    ) {
        operator fun plus(other: MultiOutcome): MultiOutcome =
            MultiOutcome(ok = ok + other.ok, failed = failed + other.failed)

        companion object {
            val EMPTY = MultiOutcome(0, emptyList())
        }
    }

    // -------------------------------------------------------- queue building

    /**
     * Build the transfer queue from the selected names of [entries] (in
     * LISTING order — deterministic, matches what the user sees). Every
     * name is re-composed through [ExplorerOps.composeChild] and its kind
     * taken from the listing; a selected name that is not in the listing
     * (or cannot be composed) becomes an honest failure, never a guessed
     * path. Returns the queue plus the failures of the names it dropped.
     */
    fun buildTransfers(
        areaId: AreaId,
        areaLabel: String,
        dir: AreaPath,
        entries: List<FsEntry>,
        selected: Set<String>,
        move: Boolean,
    ): Pair<List<PendingTransfer>, MultiOutcome> {
        val queue = ArrayList<PendingTransfer>(selected.size)
        val failed = ArrayList<MultiFailure>(0)
        for (entry in entries) {
            if (entry.name !in selected) continue
            val child = ExplorerOps.composeChild(dir, entry.name)
            if (child == null) {
                failed += MultiFailure(entry.name, "does not name a valid entry")
                continue
            }
            queue += PendingTransfer(
                areaId = areaId,
                areaLabel = areaLabel,
                path = child,
                name = entry.name,
                kind = entry.kind,
                move = move,
            )
        }
        // Selected names that vanished from the listing between selection
        // and this call are failures too — never silently skipped.
        val listed = entries.mapTo(HashSet()) { it.name }
        for (name in selected) if (name !in listed) {
            failed += MultiFailure(name, "is no longer listed here")
        }
        return Pair(queue, MultiOutcome(ok = 0, failed = failed))
    }

    // ---------------------------------------------------------------- delete

    /**
     * Delete every [names] entry inside [dir], in order, through the area's
     * own delete (directories take their contents, symlink nodes keep their
     * targets — the unchanged Phase 2/4 semantics). Per-item failures carry
     * their reason; the walk never aborts on one bad item.
     */
    fun deleteAll(area: StorageArea, dir: AreaPath, names: List<String>): MultiOutcome {
        var ok = 0
        val failed = ArrayList<MultiFailure>(0)
        for (name in names) {
            val path = ExplorerOps.composeChild(dir, name)
            if (path == null) {
                failed += MultiFailure(name, "does not name a valid entry")
                continue
            }
            val result = area.delete(path)
            if (result.success) {
                ok += 1
            } else {
                failed += MultiFailure(
                    name,
                    result.reason ?: if (result.kind == OpResult.Kind.DENIED) "refused" else "delete failed",
                )
            }
        }
        return MultiOutcome(ok = ok, failed = failed)
    }

    /** The honest warning lines for a multi delete confirmation, deduped. */
    fun deleteWarnings(entries: List<FsEntry>, names: Collection<String>): List<String> {
        val selected = entries.filterTo(ArrayList(entries.size)) { it.name in names }
        val warnings = ArrayList<String>(2)
        if (selected.any { it.kind == EntryKind.DIRECTORY }) {
            warnings += "Selected folders and everything inside them will be deleted."
        }
        if (selected.any { it.kind == EntryKind.SYMLINK }) {
            warnings += "Only links are deleted — the targets they point to are not touched."
        }
        return warnings
    }

    // ------------------------------------------------------------ multi paste

    /**
     * One pass of the multi paste into [targetDir]: items run in queue
     * order; each is checked exactly like the single paste
     * ([ExplorerOps.checkPaste]) and executed with replace = false.
     * The FIRST collision stops the pass — the user must answer the same
     * Replace/Cancel question the single paste always asks before anything
     * after it is touched.
     */
    sealed interface PastePassResult {
        /** Every item was handled (executed, or failed with its reason). */
        data class Completed(val outcome: MultiOutcome) : PastePassResult

        /**
         * [current] collides at [target] — ask the user. [done] carries
         * everything already handled; [rest] is what still remains after
         * the answered item.
         */
        data class NeedsReplace(
            val done: MultiOutcome,
            val current: PendingTransfer,
            val existingKind: EntryKind,
            val target: AreaPath,
            val rest: List<PendingTransfer>,
        ) : PastePassResult
    }

    fun pastePass(
        sourceArea: StorageArea,
        queue: List<PendingTransfer>,
        targetArea: StorageArea,
        targetDir: AreaPath,
    ): PastePassResult {
        var ok = 0
        val failed = ArrayList<MultiFailure>(0)
        for ((index, pending) in queue.withIndex()) {
            when (val check = ExplorerOps.checkPaste(sourceArea, pending, targetArea, targetDir)) {
                is ExplorerOps.PasteCheck.Invalid ->
                    failed += MultiFailure(pending.name, check.reason)

                ExplorerOps.PasteCheck.SameAsSource ->
                    failed += MultiFailure(
                        pending.name,
                        "source and destination are the same",
                    )

                is ExplorerOps.PasteCheck.Clear -> {
                    val outcome = ExplorerOps.executePaste(
                        sourceArea, pending, targetArea, check.target, replace = false,
                    )
                    if (outcome.success) {
                        ok += 1
                    } else {
                        failed += MultiFailure(pending.name, outcome.message)
                    }
                }

                is ExplorerOps.PasteCheck.Collision -> return PastePassResult.NeedsReplace(
                    done = MultiOutcome(ok = ok, failed = failed),
                    current = pending,
                    existingKind = check.existing.kind,
                    target = check.target,
                    rest = queue.subList(index + 1, queue.size),
                )
            }
        }
        return PastePassResult.Completed(MultiOutcome(ok = ok, failed = failed))
    }

    // ----------------------------------------------------------- presentation

    /**
     * The honest aggregate sentence. [cancelledAt] (the item the user
     * cancelled on) explains why a multi paste stopped early.
     */
    fun summary(verb: String, outcome: MultiOutcome, cancelledAt: String? = null): String {
        val text = StringBuilder()
        if (outcome.ok > 0) {
            text.append(verb).append(' ').append(outcome.ok)
            text.append(if (outcome.ok == 1) " item." else " items.")
        } else {
            text.append("Nothing was ").append(verb.lowercase()).append('.')
        }
        if (outcome.failed.isNotEmpty()) {
            text.append(" Failed: ")
            val shown = outcome.failed.take(3)
            text.append(
                shown.joinToString("; ") { "\"${it.name}\" (${it.reason})" },
            )
            val rest = outcome.failed.size - shown.size
            if (rest > 0) text.append("; and ").append(rest).append(" more.")
        }
        if (cancelledAt != null) {
            text.append(" Cancelled at \"").append(cancelledAt)
                .append("\" — the remaining items were not touched.")
        }
        return text.toString()
    }

    /**
     * The clipboard banner text. One item keeps the exact historical
     * single-transfer wording; more items disclose the count.
     */
    fun bannerText(pending: PendingTransfer, count: Int): String {
        if (count <= 1) return ExplorerOps.bannerText(pending)
        val verb = if (pending.move) "move" else "copy"
        val area = if (pending.areaLabel.isBlank()) "" else " from ${pending.areaLabel}"
        return "Holding $count items to $verb$area — open a destination and paste here."
    }
}

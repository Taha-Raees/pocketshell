package app.pocketshell.widget.git

/**
 * Pure presentation rules for the Git card's overview pane: the grouping
 * of porcelain entries into the STAGED / UNSTAGED sections and the branch
 * tracking + worktree glyphs. No android imports — JVM-tested
 * (GitPresentationTest), exactly like the parser it reads.
 *
 * The grouping follows git's own index/worktree split on the porcelain XY
 * columns (git-scm.com/docs/git-status):
 *
 *   STAGED   — the index column X carries a real change (X != ' ' and
 *              X != '?', so untracked paths never count as staged);
 *   UNSTAGED — the worktree column Y carries a change, or the entry is
 *              untracked ("??").
 *
 * An entry changed on BOTH sides (e.g. "MM") appears in both sections —
 * that is git's model, not a rendering bug: the staged half and the
 * worktree half are two facts. A conflicted entry ("UU") likewise, since
 * both its index and worktree state need attention.
 */
internal object GitPresentation {

    /** One rendered file row: the single status letter + the display path. */
    data class EntryRow(
        val letter: Char,
        /** Renames render as the parser provides them: "old -> new". */
        val label: String,
    )

    /** The STAGED section: X is a real change letter (never '?'). */
    fun stagedRows(entries: List<GitStatusParser.PorcelainEntry>): List<EntryRow> =
        entries.filter { it.x != ' ' && it.x != '?' }
            .map { EntryRow(letter = it.x, label = displayPath(it)) }

    /** The UNSTAGED section: a worktree change, or an untracked path. */
    fun unstagedRows(entries: List<GitStatusParser.PorcelainEntry>): List<EntryRow> =
        entries.filter { it.y != ' ' || it.untracked }
            .map { EntryRow(letter = it.y, label = displayPath(it)) }

    private fun displayPath(entry: GitStatusParser.PorcelainEntry): String =
        if (entry.origPath != null) "${entry.origPath} -> ${entry.path}" else entry.path

    /**
     * The tracking glyphs after the branch name: "↑N" when ahead, "↓N"
     * when behind, in that order; nothing when in sync or when the head
     * line carries no divergence info at all. Zero counters render nothing
     * — git never emits them, and the glyph means a nonzero divergence —
     * so a rendered glyph is always worth the accent color.
     */
    fun trackingGlyphs(ahead: Int?, behind: Int?): String = buildString {
        if (ahead != null && ahead > 0) append('↑').append(ahead)
        if (behind != null && behind > 0) append('↓').append(behind)
    }

    /** The worktree marker: ● changes pending, ○ clean. */
    fun worktreeGlyph(dirty: Boolean): Char = if (dirty) '●' else '○'
}

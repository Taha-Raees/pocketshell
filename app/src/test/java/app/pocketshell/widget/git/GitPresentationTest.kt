package app.pocketshell.widget.git

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The overview pane's pure presentation rules: the STAGED / UNSTAGED
 * grouping of porcelain entries (the parser's XY columns, git's own
 * index/worktree split) and the tracking + worktree glyphs. Every fixture
 * is a literal `git status --porcelain=v1 -b` block, so the grouping is
 * tested against the exact bytes git prints.
 */
class GitPresentationTest {

    // ----------------------------------------------------- file grouping

    @Test
    fun `staged and unstaged and untracked partition into the right sections`() {
        val status = GitStatusParser.parse("## main\nM  a.txt\n M b.txt\nMM c.txt\n?? d.txt\n")
        val staged = GitPresentation.stagedRows(status.entries)
        val unstaged = GitPresentation.unstagedRows(status.entries)
        // STAGED: the index column carries the change (M  a, MM c)
        assertEquals(listOf("a.txt", "c.txt"), staged.map { it.label })
        assertEquals(listOf('M', 'M'), staged.map { it.letter })
        // UNSTAGED: a worktree change or untracked ( M b, MM c, ?? d)
        assertEquals(listOf("b.txt", "c.txt", "d.txt"), unstaged.map { it.label })
        assertEquals(listOf('M', 'M', '?'), unstaged.map { it.letter })
        // an untracked path is never staged
        assertFalse(staged.any { it.label == "d.txt" })
    }

    @Test
    fun `a staged-only deletion never appears under unstaged`() {
        val status = GitStatusParser.parse("## main\nD  gone.txt\n")
        assertEquals(listOf('D'), GitPresentation.stagedRows(status.entries).map { it.letter })
        assertTrue(GitPresentation.unstagedRows(status.entries).isEmpty())
    }

    @Test
    fun `a worktree-only change never appears under staged`() {
        val status = GitStatusParser.parse("## main\n M dirty.txt\n")
        assertTrue(GitPresentation.stagedRows(status.entries).isEmpty())
        assertEquals(listOf("dirty.txt"), GitPresentation.unstagedRows(status.entries).map { it.label })
    }

    @Test
    fun `an entry changed on both sides appears in both sections`() {
        // git's model, not a rendering bug: the staged half and the
        // worktree half are two facts
        val status = GitStatusParser.parse("## main\nMM c.txt\n")
        assertEquals(listOf("c.txt"), GitPresentation.stagedRows(status.entries).map { it.label })
        assertEquals(listOf("c.txt"), GitPresentation.unstagedRows(status.entries).map { it.label })
    }

    @Test
    fun `a conflicted entry needs attention on both sides`() {
        val status = GitStatusParser.parse("## main\nUU both.txt\n")
        assertEquals('U', GitPresentation.stagedRows(status.entries).single().letter)
        assertEquals('U', GitPresentation.unstagedRows(status.entries).single().letter)
    }

    @Test
    fun `renames render the arrow form the parser provides`() {
        val status = GitStatusParser.parse("## main\nR  old.txt -> new.txt\n")
        val row = GitPresentation.stagedRows(status.entries).single()
        assertEquals('R', row.letter)
        assertEquals("old.txt -> new.txt", row.label)
        assertTrue(GitPresentation.unstagedRows(status.entries).isEmpty())
    }

    @Test
    fun `quoted rename paths render decoded with the arrow`() {
        val status = GitStatusParser.parse("## main\nR  \"old name.txt\" -> \"new name.txt\"\n")
        assertEquals(
            listOf("old name.txt -> new name.txt"),
            GitPresentation.stagedRows(status.entries).map { it.label },
        )
    }

    @Test
    fun `an empty worktree renders no rows in either section`() {
        val status = GitStatusParser.parse("## main\n")
        assertTrue(GitPresentation.stagedRows(status.entries).isEmpty())
        assertTrue(GitPresentation.unstagedRows(status.entries).isEmpty())
    }

    // --------------------------------------------------- tracking glyphs

    @Test
    fun `tracking glyphs - ahead, behind, both, none`() {
        assertEquals("↑2", GitPresentation.trackingGlyphs(ahead = 2, behind = null))
        assertEquals("↓5", GitPresentation.trackingGlyphs(ahead = null, behind = 5))
        assertEquals("↑1↓2", GitPresentation.trackingGlyphs(ahead = 1, behind = 2))
        assertEquals("", GitPresentation.trackingGlyphs(ahead = null, behind = null))
    }

    @Test
    fun `zero counters render no glyph - a glyph means a nonzero divergence`() {
        assertEquals("", GitPresentation.trackingGlyphs(ahead = 0, behind = 0))
        assertEquals("↓3", GitPresentation.trackingGlyphs(ahead = 0, behind = 3))
        assertEquals("↑4", GitPresentation.trackingGlyphs(ahead = 4, behind = 0))
    }

    // ---------------------------------------------------- worktree marker

    @Test
    fun `the worktree marker is full when dirty and hollow when clean`() {
        assertEquals('●', GitPresentation.worktreeGlyph(dirty = true))
        assertEquals('○', GitPresentation.worktreeGlyph(dirty = false))
    }

    @Test
    fun `the marker derives from the parser's own dirtiness`() {
        val dirty = GitStatusParser.parse("## main\n M a.txt\n?? b\n").dirty
        val clean = GitStatusParser.parse("## main...origin/main\n").dirty
        assertTrue(dirty)
        assertFalse(clean)
        assertEquals('●', GitPresentation.worktreeGlyph(dirty))
        assertEquals('○', GitPresentation.worktreeGlyph(clean))
    }
}

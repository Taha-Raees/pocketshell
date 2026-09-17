package app.pocketshell.terminal

import com.termux.terminal.WcWidth

/**
 * Conservative HTTP/HTTPS URL detection over terminal row text (owner
 * iteration: "terminal links → Companion").
 *
 * Hit-testing strategy: coordinates are mapped to a screen cell by the view
 * (TerminalView#getColumnAndRow), the row's text is pulled from the emulator
 * buffer, and THIS object decides whether the tapped column sits inside an
 * URL. The emulator/renderer/PTY are never touched — read-only over text
 * that already went through escape decoding, so ANSI state cannot be
 * damaged and the renderer stays exactly as it was.
 *
 * Deliberately conservative: a candidate must carry an explicit http(s)
 * scheme AND a host that is localhost, an IPv4 literal, or a dotted domain
 * with an alphabetic TLD. Bare "www.*" text, single-label hosts, versions
 * and file names never become links. Trailing punctuation that almost
 * always belongs to the surrounding sentence (".", ",", quotes, an
 * unbalanced closing paren) is trimmed off the match.
 *
 * Column math: a terminal column is not a string index once wide or
 * combining characters are in the row, so match boundaries are reported in
 * COLUMNS via [com.termux.terminal.WcWidth] — the same unit the tap lands
 * in. Wrapped rows are handled by the caller passing the row segments plus
 * the tapped segment index; every full segment is [columnsPerSegment]
 * columns wide, which keeps the concatenation coordinate space trivial.
 */
object TerminalUrlDetector {

    private val RAW = Regex("https?://\\S+")
    private val HOST = Regex(
        "^(localhost|(\\d{1,3}\\.){3}\\d{1,3}|([A-Za-z0-9](-?[A-Za-z0-9])*\\.)+[A-Za-z]{2,63})(:\\d{1,5})?(/|\\?|#|$)",
    )

    /** Punctuation that ends a sentence, never an URL — always trimmed. */
    private const val TRAILING = ".,;:!?\"'“”‘’»›…"

    /**
     * Does a tap at [column] (terminal columns, within segment [segment])
     * land on an URL across the concatenated [segments]? Returns the
     * trimmed URL or null. Any caller-level failure should degrade to null
     * (the tap then keeps its previous meaning).
     */
    fun findAt(segments: List<String>, columnsPerSegment: Int, segment: Int, column: Int): String? {
        // Global column space: every full segment spans [columnsPerSegment]
        // columns on screen, regardless of how much of its text survived
        // extraction (trailing blanks are trimmed). Matching runs on the
        // joined text so a URL split across a wrap boundary is found whole,
        // while each match's columns are anchored at its OWN segment
        // boundary — earlier segments' trimming cannot shift it.
        val starts = IntArray(segments.size)
        var acc = 0
        for ((i, seg) in segments.withIndex()) {
            starts[i] = acc
            acc += seg.length
        }
        val tapped = segment * columnsPerSegment + column
        for (raw in RAW.findAll(segments.joinToString(""))) {
            val url = trimTrailing(raw.value)
            if (url.isEmpty() || !hasValidHost(url)) continue
            var seg = segments.size - 1
            while (seg > 0 && starts[seg] > raw.range.first) seg -= 1
            val startCol = seg * columnsPerSegment +
                columnsOf(segments[seg], raw.range.first - starts[seg])
            val endCol = startCol + columnsOf(url, url.length)
            if (tapped >= startCol && tapped < endCol) return url
        }
        return null
    }

    private fun hasValidHost(url: String): Boolean {
        val afterScheme = url.substringAfter("://")
        return HOST.containsMatchIn(afterScheme)
    }

    private fun trimTrailing(raw: String): String {
        var url = raw
        while (url.isNotEmpty()) {
            val last = url.last()
            when {
                last in TRAILING -> url = url.dropLast(1)
                last == ')' && url.count { it == '(' } < url.count { it == ')' } ->
                    url = url.dropLast(1)
                last == ']' && url.count { it == '[' } < url.count { it == ']' } ->
                    url = url.dropLast(1)
                last == '}' && url.count { it == '{' } < url.count { it == '}' } ->
                    url = url.dropLast(1)
                else -> return url
            }
        }
        return url
    }

    /** Terminal columns spanned by line[0, endIndex). */
    private fun columnsOf(line: String, endIndex: Int): Int {
        var columns = 0
        var index = 0
        while (index < endIndex) {
            val codePoint = line.codePointAt(index)
            // combining marks attach to the previous column; wide chars span two
            columns += WcWidth.width(codePoint).coerceIn(0, 2)
            index += Character.charCount(codePoint)
        }
        return columns
    }
}

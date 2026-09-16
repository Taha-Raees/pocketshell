package app.pocketshell.ui.terminal

import android.view.MotionEvent
import app.pocketshell.terminal.TerminalUrlDetector
import com.termux.view.TerminalLinkProbe
import com.termux.view.TerminalView

/**
 * App-side bridge to the terminal-link probe (owner iteration: terminal
 * links → Companion).
 *
 * Text extraction lives in the VIEW module ([TerminalLinkProbe]) — the app
 * layer sits behind the no-screen-scraping boundary (P7 audit) and never
 * reads rendered terminal text. This side owns only the URL policy,
 * injected as the probe's matcher, and receives just the match result.
 */
object TerminalLinkTap {

    private val matcher = object : TerminalLinkProbe.UrlMatcher {
        override fun find(
            segments: MutableList<String>,
            columnsPerSegment: Int,
            segment: Int,
            column: Int,
        ): String? = TerminalUrlDetector.findAt(segments, columnsPerSegment, segment, column)
    }

    /** The URL under the tap, or null (the tap keeps its previous meaning). */
    fun urlAt(view: TerminalView, event: MotionEvent): String? =
        TerminalLinkProbe.urlAt(view, event, matcher)
}

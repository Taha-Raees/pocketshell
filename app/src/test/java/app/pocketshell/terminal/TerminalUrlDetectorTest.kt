package app.pocketshell.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit pins for [TerminalUrlDetector] (owner iteration: terminal links →
 * Companion). The detector must stay CONSERVATIVE — every accepted shape
 * here is a real developer URL; every rejected one is prose/terminal noise.
 */
class TerminalUrlDetectorTest {

    private fun at(line: String, column: Int): String? =
        TerminalUrlDetector.findAt(listOf(line), columnsPerSegment = 120, segment = 0, column = column)

    // ---- accepted shapes (owner spec §2) ------------------------------------

    @Test fun `localhost with port`() {
        val line = "Local: http://localhost:3000"
        assertEquals("http://localhost:3000", at(line, 12))
    }

    @Test fun `loopback ip with port and path`() {
        val line = "http://127.0.0.1:8080/test"
        assertEquals(line, at(line, 4))
    }

    @Test fun `lan ip address`() {
        val line = "ready on http://192.168.1.20:3000"
        assertEquals("http://192.168.1.20:3000", at(line, 12))
    }

    @Test fun `https domain with path`() {
        val line = "see https://github.com/user/repo"
        assertEquals("https://github.com/user/repo", at(line, 6))
    }

    @Test fun `query string and fragment`() {
        val line = "https://example.com/path?a=1&b=2#frag"
        assertEquals(line, at(line, 0))
    }

    @Test fun `tap inside span only`() {
        val line = "open http://localhost:3000 now"
        assertNull(at(line, 3)) // before the URL
        assertEquals("http://localhost:3000", at(line, 6))
        assertEquals("http://localhost:3000", at(line, 25)) // last column of URL
        assertNull(at(line, 27)) // trailing space
    }

    @Test fun `tap on second wrapped segment`() {
        val segments = listOf("listening on http://localhost:9", "000/dev-server ready")
        assertEquals(
            "http://localhost:9000/dev-server",
            TerminalUrlDetector.findAt(segments, columnsPerSegment = 31, segment = 1, column = 3),
        )
        // same URL hit from the first segment too
        assertEquals(
            "http://localhost:9000/dev-server",
            TerminalUrlDetector.findAt(segments, columnsPerSegment = 31, segment = 0, column = 14),
        )
    }

    // ---- trailing punctuation belongs to the sentence -----------------------

    @Test fun `trailing period is trimmed`() {
        val line = "docs at https://example.com."
        assertEquals("https://example.com", at(line, 9))
    }

    @Test fun `unbalanced closing paren is trimmed`() {
        val line = "(visit https://example.com)"
        assertEquals("https://example.com", at(line, 8))
        assertNull(at(line, 0)) // the "(" itself is not a link
    }

    @Test fun `balanced parens stay in the url`() {
        val line = "https://en.wikipedia.org/wiki/Foo_(bar)"
        assertEquals(line, at(line, 0))
    }

    // ---- rejected (false-positive guards) -----------------------------------

    @Test fun `no scheme no link`() {
        assertNull(at("www.example.com", 2))
    }

    @Test fun `single-label host rejected`() {
        assertNull(at("http://jar-dir", 3))
    }

    @Test fun `plain text rejected`() {
        assertNull(at("npm run dev — ready in 312ms", 5))
    }

    @Test fun `non-http scheme rejected`() {
        assertNull(at("file:///tmp/x", 3))
    }

    @Test fun `tap outside any url is null`() {
        assertNull(at("total 42K", 4))
    }
}

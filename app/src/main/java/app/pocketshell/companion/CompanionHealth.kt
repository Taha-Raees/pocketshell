package app.pocketshell.companion

/**
 * m4.0.6 — the always-on page-health testimony (device 2026-09-05: the
 * canvas stayed BLACK under m4.0.5 with no failure card, because the two
 * witnesses both stood down on a server-rendered shell — pixels painted
 * the site's dark body and the element floor was beaten by inert SSR
 * markup — so the evidence the app had gathered never reached the user).
 *
 * The fix is a standing surface: the tab strip carries a health chip that
 * opens a Midnight sheet with EVERYTHING the runtime knows about the
 * active tab and a one-tap COPY REPORT. Whatever the next mystery is, the
 * user pastes the page's own testimony into the chat — the guess loop is
 * over. Pure composition here (facts in, bounded text out); the WebView
 * stays faked-free per the testing contract.
 */
object CompanionHealth {

    /** Upper bound so even a chatty page can never blow out the sheet. */
    const val MAX_REPORT_LENGTH = 900

    /** Everything the pool knows about one tab at one moment. */
    data class Facts(
        val name: String,
        val url: String?,
        val webViewVersion: String,
        /** "GPU" or "SOFTWARE" — which rasterizer this WebView was created with. */
        val renderer: String,
        /** "painted" / "never painted" / "unknown" — the pixel probe's last word. */
        val pixels: String,
        val truth: BootWitness.Truth?,
        val console: List<String>,
        val userAgent: String?,
        /** m4.0.8 — what the glass ACTUALLY shows (dominant color, near-black
         *  share, distinct colors); null until the probe has sampled. */
        val colors: RenderProbe.ColorTruth? = null,
        /** m4.0.8 — the color-scheme recipe this view was created with
         *  ("forced light" / "inherits app"). */
        val scheme: String? = null,
    )

    /**
     * The full report block: one fact per line, stable order, every line
     * labeled. Pure; unit-pinned; hard-capped.
     */
    fun compose(f: Facts): String = buildList {
        add("PocketShell Companion health — ${f.name.ifBlank { "tab" }}")
        add("url: ${f.url ?: "unknown"}")
        add("webview: ${f.webViewVersion} · renderer: ${f.renderer} · pixels: ${f.pixels}")
        // m4.0.8: the two facts the "painted but black" mystery was missing —
        // what color scheme the page was served and WHAT COLOR the glass is.
        if (!f.scheme.isNullOrBlank()) add("scheme: ${f.scheme}")
        f.colors?.let {
            add("glass: dominant ${it.dominantHex} · ${it.nearBlackPct}% near-black · ${it.distinctColors} colors")
        }
        val t = f.truth
        if (t == null) {
            add("page: no DOM reading yet")
        } else {
            add(
                "page: readyState=${t.readyState} · ${t.elementCount} elements · " +
                    (if (t.interactiveCount >= 0) "${t.interactiveCount} interactive · " else "") +
                    "${t.textLength} text chars",
            )
            // m4.0.8: the page's own voice — its title and first visible
            // words name the exact page state (login wall, consent, shell).
            val voice = listOf(t.title.take(80), t.textSample.take(100))
                .filter { it.isNotBlank() }
                .joinToString(" — ")
            if (voice.isNotBlank()) add("page says: \"$voice\"")
            if (t.bootErrors.isEmpty()) {
                add("boot errors: none captured")
            } else {
                t.bootErrors.forEachIndexed { i, e -> add("boot error ${i + 1}: $e") }
            }
        }
        if (f.console.isEmpty()) {
            add("console: empty")
        } else {
            add("console (last ${f.console.size}):")
            f.console.forEach { add("  > $it") }
        }
        if (!f.userAgent.isNullOrBlank()) add("ua: ${f.userAgent.take(140)}")
    }.joinToString("\n").take(MAX_REPORT_LENGTH)
}

package app.pocketshell.diagnostic

/**
 * Phase 4.0.9 — Companion Rendering Reset. The control experiment's PURE
 * specification (device 2026-09-05: eight iterations of symptom patches —
 * force-dark levers, creation recipes, swap-safe hosts, attach kicks,
 * glass probes — never proved WHICH architectural layer breaks the visible
 * presentation, and the m4.0.8 reports made the case sharp: ChatGPT paints
 * a blank WHITE canvas (the page's own light background presents!) while
 * Z.ai paints a blank dark canvas — page pixels present, page UI absent).
 *
 * The rule for this investigation: the baseline is the SIMPLEST possible
 * WebView — real Activity context, plain FrameLayout parent, normal view
 * attachment, JavaScript + DOM storage on, everything else Android
 * defaults, URL loaded AFTER first layout. Every other variant differs
 * from the baseline by EXACTLY ONE variable ([isSingleVariable] pins
 * that invariant) so a device run names the first breaking layer without
 * confounds. No pool, no Compose, no probes in the render path, no boot
 * witness, no retry — correctness before cleverness.
 *
 * Pure decisions live here and are unit-pinned; the Activity itself is
 * device code (the testing contract forbids fake WebView tests).
 */
object BaselineMatrix {

    /** One harness configuration. Flags default to the Android baseline. */
    data class Variant(
        val key: String,
        val label: String,
        val chromeUa: Boolean = false,
        val forcedLight: Boolean = false,
        val midnightBg: Boolean = false,
        val loadBeforeAttach: Boolean = false,
        val wideViewport: Boolean = false,
    )

    /** The control: everything Android default, load after first layout. */
    val BASELINE = Variant("baseline", "BASELINE (Android defaults)")

    /** One variable at a time, against [BASELINE] — the A/B discipline. */
    val VARIANTS = listOf(
        BASELINE,
        Variant("chrome-ua", "+CHROME UA", chromeUa = true),
        Variant("forced-light", "+FORCED LIGHT CTX", forcedLight = true),
        Variant("midnight-bg", "+MIDNIGHT BG", midnightBg = true),
        Variant("load-early", "+LOAD BEFORE ATTACH", loadBeforeAttach = true),
        Variant("wide-viewport", "+WIDE VIEWPORT", wideViewport = true),
    )

    /** The test matrix URLs, in the mandated order (gates A–D). */
    val URLS = listOf(
        "https://example.com/",
        "https://www.wikipedia.org/",
        "https://chatgpt.com/",
        "https://chat.z.ai/",
    )

    /** How many Companion flags a variant turns on (0 = the baseline). */
    private fun flagCount(v: Variant): Int =
        listOf(v.chromeUa, v.forcedLight, v.midnightBg, v.loadBeforeAttach, v.wideViewport)
            .count { it }

    /**
     * The experiment's core invariant: the baseline differs from Android
     * defaults by ZERO flags; every other variant by EXACTLY ONE. A device
     * result can then name the breaking variable with no confounds.
     */
    fun isSingleVariable(v: Variant): Boolean = when (v.key) {
        BASELINE.key -> flagCount(v) == 0
        else -> flagCount(v) == 1
    }

    fun nextUrl(current: String): String {
        val i = URLS.indexOf(current)
        return URLS[(i + 1).mod(URLS.size)]
    }

    fun nextVariant(currentKey: String): Variant {
        val i = VARIANTS.indexOfFirst { it.key == currentKey }
        return VARIANTS[(i + 1).mod(VARIANTS.size)]
    }

    /** What the harness knows about the WebView VIEW itself (no JS). */
    data class ViewFacts(
        val attached: Boolean,
        val width: Int,
        val height: Int,
        val visibleLeft: Int,
        val visibleTop: Int,
        val visibleRight: Int,
        val visibleBottom: Int,
        val layerType: String,
    )

    /** The opt-in INSPECT reading of the page's own viewport (one probe). */
    data class PageFacts(
        val title: String,
        val innerWidth: Int,
        val innerHeight: Int,
        val devicePixelRatio: Double,
        val visualWidth: Int,
        val visualHeight: Int,
    )

    /**
     * The one-shot, read-only page metrics probe behind the INSPECT
     * button. It is NOT part of the render path (the render path has no
     * injections at all) — it exists because "blank but painted" has a
     * classic cause this can catch instantly: a page whose UI sizes to a
     * bogus viewport. Read-only, side-effect-free, on demand only.
     */
    const val PAGE_METRICS_JS: String =
        "(function(){try{return JSON.stringify({" +
            "t:(document.title||'').slice(0,60)," +
            "iw:innerWidth,ih:innerHeight,dpr:devicePixelRatio," +
            "vw:(visualViewport?Math.round(visualViewport.width):-1)," +
            "vh:(visualViewport?Math.round(visualViewport.height):-1)" +
            "})}catch(e){return JSON.stringify(" +
            "{t:'inspect-error',iw:-1,ih:-1,dpr:-1,vw:-1,vh:-1})}})()"

    /** Upper bound so one status line can never flood the screen. */
    const val MAX_STATUS_LENGTH = 700

    /**
     * The status block: exact config + view truth + (opt-in) page truth.
     * Pure; unit-pinned.
     */
    fun status(
        variant: Variant,
        url: String,
        webviewVersion: String,
        userAgent: String,
        view: ViewFacts?,
        page: PageFacts?,
    ): String = buildList {
        add("Render baseline — ${variant.label}")
        add("url: $url")
        add("webview: $webviewVersion")
        if (userAgent.isNotBlank()) add("ua: ${userAgent.take(90)}")
        if (view == null) {
            add("view: not created")
        } else {
            add(
                "view: attached=${view.attached} ${view.width}x${view.height}px " +
                    "visible=${view.visibleLeft},${view.visibleTop}-${view.visibleRight},${view.visibleBottom} " +
                    "layer=${view.layerType}",
            )
        }
        if (page != null) {
            add(
                "page: title=\"${page.title}\" viewport=${page.innerWidth}x${page.innerHeight} " +
                    "dpr=${page.devicePixelRatio} visualViewport=${page.visualWidth}x${page.visualHeight}",
            )
        }
    }.joinToString("\n").take(MAX_STATUS_LENGTH)

    /** View layer type, named. */
    fun layerTypeName(type: Int): String = when (type) {
        android.view.View.LAYER_TYPE_SOFTWARE -> "software"
        android.view.View.LAYER_TYPE_HARDWARE -> "hardware"
        else -> "none"
    }
}

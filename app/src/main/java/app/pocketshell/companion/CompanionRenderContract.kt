package app.pocketshell.companion

/**
 * m4.0.11 — THE FROZEN RENDER CONTRACT ("Replace Renderer Only").
 *
 * The user's directive, executed verbatim: the existing Companion sheet,
 * drag handle, remembered height, tab strip, tab system, picker and
 * destination storage stay EXACTLY as they are; the ONLY thing that ever
 * changed is the tab content renderer, which is now the proven m4.0.9
 * baseline implementation copied as a unit —
 *
 *   Baseline screen:   Activity → FrameLayout → WebView(activity) → layout → load
 *   Companion screen:  Activity → (sheet chrome) → FrameLayout → WebView(activity) → layout → load
 *
 * [CompanionWebHost.createWebView] implements THIS file; if the two ever
 * disagree, this file wins and the host is wrong. The winning mode is the
 * zero-delta BASELINE ("baseline") — selected by the device sweep and
 * frozen here; the sweep's evidence lives in docs/RENDER-RESET-M4.0.9.md
 * and the git history (the harness code was retired in m4.0.12).
 *
 * Pure data only (the testing contract forbids fake WebView tests): these
 * pins are enforced by CompanionRenderContractTest, and the on-device
 * truth is gated manually in docs/TESTING.md (Gates A–H).
 */
object CompanionRenderContract {

    /** The winning harness mode this renderer copies (the zero-delta control). */
    const val WINNER_KEY = "baseline"

    // ---- the winning configuration, FROZEN (Android defaults everywhere) ----
    // Every lever the eight iterations added and the baseline proved
    // unnecessary is permanently FALSE — they must never return.

    /** No createConfigurationContext uiMode pinning (m4.0.6 lever — exonerated). */
    const val FORCED_LIGHT_CONTEXT = false

    /** No user-agent spoof in the render path (device: default UA rendered chat.z.ai fully). */
    const val CHROME_UA_OVERRIDE = false

    /** No flash-guard background on the WebView (device: default background rendered chatgpt.com fully). */
    const val MIDNIGHT_BACKGROUND_OVERRIDE = false

    /** The load never fires before attachment — the proven order is below. */
    const val LOAD_BEFORE_ATTACH = false

    /** No useWideViewPort/loadWithOverviewMode overrides. */
    const val WIDE_VIEWPORT_OVERRIDES = false

    /** No LAYER_TYPE_SOFTWARE compat renderer, no darkening levers, no attach kick. */
    const val SOFTWARE_LAYER_COMPAT = false

    // ---- what the production host IS allowed to touch ----

    /**
     * The EXACT WebSettings surface of [CompanionWebHost.createWebView]:
     * the two baseline settings plus the two §16 hardening flags that
     * cannot affect https rendering (they only deny file:// and content://
     * inside the canvas). NOTHING else — no UA, no background, no viewport,
     * no layer type, no config context, no renderer policy.
     */
    val SETTINGS_TOUCHES = listOf(
        "javaScriptEnabled = true",
        "domStorageEnabled = true",
        "allowFileAccess = false",
        "allowContentAccess = false",
    )

    /** The lifecycle, in the proven order — never reordered. */
    const val LOAD_SEQUENCE = "create -> attach -> first layout -> loadUrl"

    /**
     * The WebView's constructor context: the real Activity, exactly like
     * the baseline's `WebView(activity)`. Never a wrapper, never a
     * configuration context.
     */
    const val CREATION_CONTEXT = "activity"

    /** The WebView's parent: the plain container class the baseline used. */
    const val HOST_CONTAINER = "android.widget.FrameLayout"

    /**
     * The m4.0.9 rule, enforced: the RENDER PATH carries zero diagnostics.
     * No pixel probes, no boot witness, no console tail, no health polling,
     * no evaluateJavascript in the render path. m4.0.12: the render-baseline
     * harness was RETIRED COMPLETELY — the ⓘ chip, its launch path, the
     * diagnostic Activity and its matrix are deleted; the Companion shows
     * only the real website.
     */
    val DIAGNOSTICS_IN_RENDER_PATH = emptyList<String>()

    /**
     * Tab switching / retries / panel collapse are plain native view
     * surgery on the ONE stable container — Compose never re-creates or
     * swaps WebViews (the m4.0.7 lesson, kept as law).
     */
    const val TAB_SWITCH_MECHANISM = "native view surgery (removeAllViews + addView), one stable canvas"

    // ---- m4.0.12 — the finalization layer (refresh; NEVER the renderer) ----

    /** §3: Refresh acts on the ACTIVE tab only — one WebView, plain reload(),
     *  same URL, same tab; other tabs and the session are untouched. */
    const val REFRESH_SCOPE = "active tab only: reload() on one WebView, same URL, same tab"

    /** §4: Hard refresh is a FRESH RELOAD, not a data reset. For the single
     *  hard-reloaded page the HTTP cache is bypassed and the mode restored
     *  on page finish; cookies, login sessions and the other tabs are
     *  never touched. Pinned as the exact behavioral surface. */
    val HARD_RELOAD = listOf(
        "cacheMode = LOAD_NO_CACHE for this one load only",
        "restored to LOAD_DEFAULT on page finish",
        "cookies and login sessions preserved",
        "other tabs untouched",
    )
}

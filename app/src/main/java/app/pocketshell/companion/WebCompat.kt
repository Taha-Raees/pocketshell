package app.pocketshell.companion

/**
 * m4.0.5 — embedded-webview compatibility (device-reported 2026-09-05: the
 * site's own cookie banner painted and answered taps while the site's app
 * never started — the classic second-class-WebView-client signature).
 *
 * Sites and their fronting CDNs treat the default WebView UA — with its
 * "; wv" marker and legacy "Version/4.0" token — as an embedded client:
 * Google login answers `disallowed_useragent` outright, and bot-fronted
 * sites quietly serve degraded or challenged bundles. Presenting the exact
 * Chrome-mobile UA of the same device removes that whole class of
 * divergence; pure and unit-pinned.
 */
object WebCompat {

    /**
     * The device's WebView default UA minus the two embedded-browser
     * markers — "; wv" and "Version/4.0 " — which makes it byte-identical
     * to the Chrome mobile UA on the same device. Idempotent: a UA already
     * without the markers passes through unchanged, and a blank default is
     * never turned into something worse.
     */
    fun chromeLikeUserAgent(default: String): String =
        default
            .replace("; wv)", ")")
            .replace("Version/4.0 ", "")
            .trim()
}

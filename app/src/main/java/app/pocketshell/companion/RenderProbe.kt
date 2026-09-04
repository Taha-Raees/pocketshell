package app.pocketshell.companion

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.webkit.WebView

/**
 * m4.0.4 — the pixel-truth paint probe (device-reported 2026-09-05: the
 * m4.0.3 watchdog stood down on LOAD events — progress ≥15, commit-visible —
 * while the compositor never rasterized a frame, leaving the canvas a bare
 * black flash-guard rectangle with no failure card).
 *
 * Second device lesson (2026-09-05, the cookie-banner screenshot): the page
 * can paint a PARTIAL frame — the site's own bottom-docked consent overlay
 * drew while the main content stayed a dead flash-guard canvas — and the
 * original "any differing pixel stands the probe down" rule read that as
 * healthy. The probes therefore judge only the MAIN region: everything
 * above the bottom [BANNER_ZONE_FRACTION] of the canvas, where sites dock
 * consent bars and snackbars. A canvas whose main region is uniformly the
 * flash-guard color has not rendered anything the user can use.
 *
 * The lesson is baked in: load events say what the PAGE pipeline did; only
 * PIXELS say what the user sees. Two probes, in order:
 *
 *  1. Software readback — the WebView is drawn into a tiny scaled bitmap.
 *     Uniform flash-guard color ⇒ the renderer produced nothing at all.
 *  2. API 29+ glass readback — [PixelCopy] lifts the frame exactly as it was
 *     PRESENTED. Content in software but a uniform flash-guard on the glass
 *     means the frames exist and never reach the screen (the composition
 *     stall) — that too is "nothing painted".
 *
 * Pure decision ([hasPainted]) is unit-pinned; the captures are device code
 * and are never faked in tests (contract: no fake WebView tests).
 */
object RenderProbe {

    /**
     * The Midnight flash-guard background every tab WebView carries
     * ([CompanionWebPool] paints it at creation). A canvas that shows
     * EXACTLY this color everywhere has never drawn a page pixel.
     */
    const val WEBVIEW_BACKGROUND: Int = 0xFF080F1D.toInt()

    /** Longest side of the probe bitmap — keeps the main-thread cost trivial. */
    const val SAMPLE_MAX_DIMENSION = 96

    /**
     * The bottom slice of the canvas excluded from the verdict — consent
     * bars, cookie banners and snackbars are docked there by the SITE, and
     * their few painted pixels must not vouch for a dead page (device
     * evidence 2026-09-05).
     */
    const val BANNER_ZONE_FRACTION = 0.25f

    /**
     * Rows of a [totalRows]-row sample that belong to the judged MAIN region
     * (everything above the banner zone; at least one row, never more than
     * the sample). Pure so the unit suite pins the arithmetic.
     */
    fun mainRegionRows(totalRows: Int): Int =
        (totalRows * (1f - BANNER_ZONE_FRACTION)).toInt().coerceIn(1, totalRows)

    /**
     * True when at least one sampled pixel differs from [background]. The
     * caller hands in the MAIN-region sample only (the banner zone is
     * cropped before this runs). A page that painted its own solid color —
     * even white, even a near-identical dark — counts as painted; ONLY a
     * main region uniformly in the flash-guard color counts as "nothing
     * drawn".
     */
    fun hasPainted(pixels: IntArray, background: Int): Boolean {
        for (pixel in pixels) if (pixel != background) return true
        return false
    }

    /**
     * Asynchronous two-probe capture of [view]. [onResult] receives true the
     * moment the evidence says the user can actually be seeing page pixels;
     * false only when both readbacks agree the glass shows the bare
     * flash-guard background. Never throws to the caller: every failure is
     * resolved in favor of "painted" so a probe can never manufacture a
     * false stall. Main-thread in/out.
     */
    fun captureHasPainted(view: WebView, onResult: (Boolean) -> Unit) {
        val softwarePainted = try {
            softwareCaptureHasPainted(view)
        } catch (_: Throwable) {
            false
        }
        if (!softwarePainted) {
            // Renderer produced nothing — the glass can't have content either.
            onResult(false)
            return
        }
        // Software path HAS content — confirm it actually reaches the glass
        // (the composition stall: frames exist, presentation never happens).
        copyFromGlass(view) { glassPainted ->
            onResult(glassPainted)
        }
    }

    /** Probe 1 — the WebView's own draw path into a scaled-down bitmap. */
    private fun softwareCaptureHasPainted(view: WebView): Boolean {
        val width = view.width
        val height = view.height
        if (width <= 0 || height <= 0) return false
        val scale = SAMPLE_MAX_DIMENSION.toFloat() / maxOf(width, height)
        val bitmapWidth = (width * scale).toInt().coerceIn(1, SAMPLE_MAX_DIMENSION)
        val bitmapHeight = (height * scale).toInt().coerceIn(1, SAMPLE_MAX_DIMENSION)
        val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.scale(bitmapWidth.toFloat() / width, bitmapHeight.toFloat() / height)
            view.draw(canvas)
            // Judge the MAIN region only — the bottom banner zone is the
            // site's own overlay territory and never vouches for content.
            val mainRows = mainRegionRows(bitmapHeight)
            val pixels = IntArray(bitmapWidth * mainRows)
            bitmap.getPixels(pixels, 0, bitmapWidth, 0, 0, bitmapWidth, mainRows)
            return hasPainted(pixels, WEBVIEW_BACKGROUND)
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Probe 2 — [PixelCopy] of the WINDOW cropped to the view's bounds, i.e.
     * the frame as actually presented. (The View-direct overload is not in
     * this compile surface; the Window overload exists since API 26 — the
     * app's minSdk.) Copy errors and probe exceptions resolve to "painted":
     * an unreadable glass must never create a false stall, only a READABLE
     * flash-guard region may.
     */
    private fun copyFromGlass(view: WebView, onResult: (Boolean) -> Unit) {
        val window = (view.context as? android.app.Activity)?.window
        if (window == null) {
            onResult(true)
            return
        }
        try {
            val capture = Bitmap.createBitmap(
                view.rootView.width.coerceAtLeast(1),
                view.rootView.height.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
            PixelCopy.request(
                window,
                capture,
                PixelCopy.OnPixelCopyFinishedListener { result ->
                    onResult(readGlassRegion(view, capture, result))
                },
                Handler(Looper.getMainLooper()),
            )
        } catch (_: Throwable) {
            onResult(true)
        }
    }

    /** Crops the window capture to the view's window bounds and judges it. */
    private fun readGlassRegion(view: WebView, capture: Bitmap, copyResult: Int): Boolean {
        try {
            if (copyResult != PixelCopy.SUCCESS) return true
            val location = IntArray(2)
            view.getLocationInWindow(location)
            val left = location[0].coerceIn(0, capture.width - 1)
            val top = location[1].coerceIn(0, capture.height - 1)
            val width = minOf(view.width, capture.width - left)
            val height = minOf(view.height, capture.height - top)
            if (width <= 0 || height <= 0) return true
            // Same main-region rule as the software probe: a consent bar on
            // an otherwise dead canvas is a stall signature, not content.
            val mainHeight = mainRegionRows(height)
            val pixels = IntArray(width * mainHeight)
            capture.getPixels(pixels, 0, width, left, top, width, mainHeight)
            return hasPainted(pixels, WEBVIEW_BACKGROUND)
        } finally {
            capture.recycle()
        }
    }
}

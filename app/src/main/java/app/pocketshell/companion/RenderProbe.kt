package app.pocketshell.companion

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.webkit.WebView
import java.util.concurrent.atomic.AtomicBoolean

/**
 * m4.0.4 — the pixel-truth paint probe (device-reported 2026-09-05: the
 * m4.0.3 watchdog stood down on LOAD events — progress/commit fire faithfully
 * on the broken build while the compositor rasterizes NOTHING — the watchdog
 * stood down and the canvas stayed a bare black flash-guard rectangle).
 *
 * Second device lesson (2026-09-05, the cookie-banner screenshot): the page
 * can paint a PARTIAL frame — the site's own bottom-docked consent overlay
 * drew while the main content stayed a dead flash-guard canvas — so the
 * verdict judges only the MAIN region: everything above the bottom
 * [BANNER_ZONE_FRACTION] of the canvas, where sites dock consent bars and
 * snackbars.
 *
 * m4.0.7 — VERDICT ORDER REWRITE, evidence-driven (the health sheet's
 * testimony): the page DOM is fully alive under the stall (761 elements,
 * hydrated text, no boot errors) while the probe reported "never painted" —
 * but the probe's primary evidence was the SOFTWARE READBACK
 * ([view.draw] into a bitmap), and a modern composited Chromium is allowed
 * to answer that path with only the background color EVEN WHEN THE GLASS
 * SHOWS CONTENT. Inferring "the renderer produced nothing" from it is
 * unsound. The probes therefore run in the honest order now:
 *
 *  1. GLASS first — [PixelCopy] of the view as actually PRESENTED. This is
 *     the ground truth of what the user sees; a readable glass that shows
 *     page content IS paint, full stop. The judged region is the top
 *     [GLASS_REGION_FRACTION] of the view — the system keyboard can ride
 *     over the lower part, and its Midnight pixels must never vouch for the
 *     page.
 *  2. SOFTWARE readback only as FALLBACK — when the glass is unreadable
 *     (copy error, request threw, or no answer within [GLASS_TIMEOUT_MS] —
 *     a hung PixelCopy must never leave the verdict "unknown" forever, the
 *     exact the-device-showed state). A fallback readback that itself
 *     THROWS resolves to "painted": a broken probe may never manufacture a
 *     stall — only a READABLE surface uniformly in the flash-guard color
 *     may produce a false verdict.
 *
 * Pure decisions ([hasPainted], region arithmetic) are unit-pinned; the
 * captures are device code and are never faked in tests (contract: no fake
 * WebView tests).
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
     * The bottom slice of the canvas excluded from the software fallback's
     * verdict — consent bars, cookie banners and snackbars are docked there
     * by the SITE, and their few painted pixels must not vouch for a dead
     * page (device evidence 2026-09-05).
     */
    const val BANNER_ZONE_FRACTION = 0.25f

    /**
     * m4.0.7 — the bottom slice of the view excluded from the GLASS verdict.
     * The system keyboard docks over the canvas's lower part; its pixels
     * appear on the presented surface and would read as "content". The top
     * half of the canvas is keyboard-free whenever the sheet is raised, and
     * a working page always paints there.
     */
    const val GLASS_REGION_FRACTION = 0.5f

    /**
     * m4.0.7 — how long the glass readback may take before the probe falls
     * back to the software readback. A PixelCopy that never answers must
     * not leave the chain verdict-less ("unknown" forever was the device's
     * compat-mode state).
     */
    const val GLASS_TIMEOUT_MS = 1_500L

    /**
     * Rows of a [totalRows]-row sample that belong to the judged MAIN region
     * (everything above the banner zone; at least one row, never more than
     * the sample). Pure so the unit suite pins the arithmetic.
     */
    fun mainRegionRows(totalRows: Int): Int =
        (totalRows * (1f - BANNER_ZONE_FRACTION)).toInt().coerceIn(1, totalRows)

    /**
     * m4.0.7 — rows of a [totalRows]-row sample that belong to the judged
     * GLASS region (the top half; at least one row, never more than the
     * sample). Pure so the unit suite pins the arithmetic.
     */
    fun glassRegionRows(totalRows: Int): Int =
        (totalRows * GLASS_REGION_FRACTION).toInt().coerceIn(1, totalRows)

    /**
     * True when at least one sampled pixel differs from [background]. The
     * caller hands in the judged-region sample only. A page that painted
     * its own solid color — even white, even a near-identical dark — counts
     * as painted; ONLY a judged region uniformly in the flash-guard color
     * counts as "nothing drawn".
     */
    fun hasPainted(pixels: IntArray, background: Int): Boolean {
        for (pixel in pixels) if (pixel != background) return true
        return false
    }

    /**
     * m4.0.8 — THE COLOR TRUTH. The device cracked the "painted but black"
     * mystery: hasPainted only asks "any pixel differs from the flash-guard",
     * so a #000000 or #212121 canvas — the site's dark body, possibly
     * force-darkened — trivially reads as "painted" and the health report
     * vouches for a page the user calls black. This reading names WHAT is
     * actually on the glass: the dominant color (16-levels-per-channel
     * bucket mean), the share of near-black pixels and the distinct color
     * count. A pasted report that says "dominant #000000 · 99% near-black"
     * is a page-state answer, not a guess. Null for an empty sample.
     * Pure; unit-pinned.
     */
    fun colorTruth(pixels: IntArray): ColorTruth? {
        if (pixels.isEmpty()) return null
        val bucketCount = IntArray(16 * 16 * 16)
        val bucketR = IntArray(16 * 16 * 16)
        val bucketG = IntArray(16 * 16 * 16)
        val bucketB = IntArray(16 * 16 * 16)
        var nearBlack = 0
        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            if (0.299f * r + 0.587f * g + 0.114f * b < NEAR_BLACK_LUMA) nearBlack++
            val bucket = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
            bucketCount[bucket]++
            bucketR[bucket] += r
            bucketG[bucket] += g
            bucketB[bucket] += b
        }
        var best = 0
        for (i in 1 until bucketCount.size) if (bucketCount[i] > bucketCount[best]) best = i
        val count = bucketCount[best]
        val dominant = String.format(
            "#%02X%02X%02X",
            bucketR[best] / count,
            bucketG[best] / count,
            bucketB[best] / count,
        )
        val distinct = bucketCount.count { it > 0 }
        return ColorTruth(
            dominantHex = dominant,
            nearBlackPct = (nearBlack * 100L / pixels.size).toInt(),
            distinctColors = distinct,
        )
    }

    /** Below this luma (0–255) a pixel counts as near-black in [colorTruth]. */
    const val NEAR_BLACK_LUMA = 32

    /** The glass's own testimony — see [colorTruth]. Pure data. */
    data class ColorTruth(
        val dominantHex: String,
        val nearBlackPct: Int,
        val distinctColors: Int,
    )

    /** One probe answer: the paint verdict AND what the glass actually shows. */
    data class Reading(val painted: Boolean, val colors: ColorTruth?)

    /**
     * m4.0.8 — the WebView's host Activity, found through ANY context:
     * the forced-light creation recipe wraps the activity in a
     * configuration context, so the old `context as? Activity` cast
     * silently broke the glass probe's window lookup (the m4.0.6
     * regression). Pure unwrapping; unit-pinned.
     */
    fun findActivity(context: Context?): Activity? {
        var current: Context? = context
        var hops = 0
        while (current != null && hops < 16) {
            if (current is Activity) return current
            current = (current as? ContextWrapper)?.baseContext
            hops++
        }
        return null
    }

    /**
     * m4.0.8 — uiMode arithmetic for the forced-light creation recipe:
     * the WebView's configuration is pinned to UI_MODE_NIGHT_NO so sites
     * always answer prefers-color-scheme: light (the app itself stays
     * Midnight). Pure; unit-pinned.
     */
    fun forcedLightUiMode(current: Int): Int =
        (current and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO

    /**
     * Asynchronous two-probe capture of [view]. [onResult] receives the
     * [Reading] the moment the evidence says what the user can actually be
     * seeing: painted=true + the glass's COLOR truth whenever any probe
     * path found non-flash-guard pixels; painted=false only when the GLASS
     * shows the bare flash-guard background (or — glass unreadable — the
     * software fallback does). Never leaves the caller without a verdict:
     * every failure resolves toward "painted" so a probe can never
     * manufacture a false stall. Main-thread in/out.
     */
    fun captureHasPainted(view: WebView, activity: Activity?, onResult: (Reading) -> Unit) {
        glassVerdict(view, activity) { glass ->
            if (glass != null) {
                onResult(glass)
                return@glassVerdict
            }
            // Glass unreadable — the software readback is the fallback
            // witness. A throw here resolves to "painted": an unreadable
            // probe must never create a stall on its own.
            onResult(
                try {
                    softwareReading(view)
                } catch (_: Throwable) {
                    Reading(painted = true, colors = null)
                },
            )
        }
    }

    /**
     * Probe 1 — the presented truth. [PixelCopy] lifts the WINDOW's frame
     * (the View-direct overload does not exist in the platform — Window,
     * Surface and SurfaceView only), and the capture is cropped to the
     * view's on-window location, judged on the top [glassRegionRows] rows:
     * the keyboard can dock over the lower part of the window, and its
     * Midnight pixels must never vouch for the page. SUCCESS + any
     * non-flash-guard pixel in that region is paint. Any failure (no
     * activity window, request threw, copy error, timeout) yields
     * null = unreadable, and the caller falls back to the software
     * readback. The timeout guard is what keeps a dead copy path from
     * leaving the watchdog chain hanging verdict-less forever.
     */
    private fun glassVerdict(view: WebView, activity: Activity?, onResult: (Reading?) -> Unit) {
        val window = activity?.window ?: findActivity(view.context)?.window
        if (window == null || view.width <= 0 || view.height <= 0) {
            onResult(null)
            return
        }
        val capture = try {
            Bitmap.createBitmap(
                view.rootView.width.coerceAtLeast(1),
                view.rootView.height.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
        } catch (_: Throwable) {
            onResult(null)
            return
        }
        // The late-answer guard: whichever of the timeout and the listener
        // runs first owns the verdict; the other is ignored.
        val answered = AtomicBoolean(false)
        val mainHandler = Handler(Looper.getMainLooper())
        val timeoutRunnable: Runnable = object : Runnable {
            override fun run() {
                if (answered.getAndSet(true)) return
                onResult(null)
            }
        }
        mainHandler.postDelayed(timeoutRunnable, GLASS_TIMEOUT_MS)
        try {
            PixelCopy.request(
                window,
                capture,
                { result ->
                    val verdict: Reading? = try {
                        if (result == PixelCopy.SUCCESS) glassJudge(view, capture) else null
                    } catch (_: Throwable) {
                        null
                    } finally {
                        try {
                            capture.recycle()
                        } catch (_: Throwable) {
                        }
                    }
                    if (answered.getAndSet(true)) return@request
                    mainHandler.removeCallbacks(timeoutRunnable)
                    onResult(verdict)
                },
                mainHandler,
            )
        } catch (_: Throwable) {
            try {
                capture.recycle()
            } catch (_: Throwable) {
            }
            if (answered.getAndSet(true)) return
            mainHandler.removeCallbacks(timeoutRunnable)
            onResult(null)
        }
    }

    /**
     * Crops the presented WINDOW capture to the view's bounds and judges the
     * keyboard-free top region. Returns null when the copy itself failed
     * (unreadable — the caller falls back), never when the region merely
     * shows the flash-guard color (that is a real "nothing painted").
     * m4.0.8: the same sample also yields the COLOR truth — what the glass
     * actually shows — reported alongside the verdict.
     */
    private fun glassJudge(view: WebView, capture: Bitmap): Reading? {
        val location = IntArray(2)
        view.getLocationInWindow(location)
        val left = location[0].coerceIn(0, capture.width - 1)
        val top = location[1].coerceIn(0, capture.height - 1)
        val width = minOf(view.width, capture.width - left)
        val regionHeight = glassRegionRows(view.height)
        val height = minOf(regionHeight, capture.height - top)
        if (width <= 0 || height <= 0) return null
        val pixels = IntArray(width * height)
        capture.getPixels(pixels, 0, width, left, top, width, height)
        return Reading(hasPainted(pixels, WEBVIEW_BACKGROUND), colorTruth(pixels))
    }

    /** Probe 2 (fallback) — the WebView's own draw path into a scaled bitmap. */
    private fun softwareReading(view: WebView): Reading {
        val width = view.width
        val height = view.height
        if (width <= 0 || height <= 0) return Reading(painted = false, colors = null)
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
            return Reading(hasPainted(pixels, WEBVIEW_BACKGROUND), colorTruth(pixels))
        } finally {
            bitmap.recycle()
        }
    }
}

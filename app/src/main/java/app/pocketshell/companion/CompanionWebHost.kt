package app.pocketshell.companion

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebSettings
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.view.doOnLayout
import app.pocketshell.keyboard.KeyboardInputRouter
import java.lang.ref.WeakReference

/**
 * Phase 4.1.0 — Companion Native Rebuild (the Rendering Reset's verdict).
 *
 * The m4.0.9 control experiment SETTLED THE CASE on the physical device
 * (video, 2026-09-05): the minimal baseline — `WebView(activity)` inside a
 * plain FrameLayout, JS + DOM storage only, Android defaults everywhere
 * else, URL loaded AFTER first layout — rendered example.com, wikipedia.org,
 * chatgpt.com AND chat.z.ai with their complete real UIs, in this same app,
 * process, theme and WebView package (151.0.7922.199) — SECONDS after the
 * real Companion blanked on the same two sites. Android WebView, the device
 * and the sites are exonerated; the old Companion hosting stack was the
 * failure. So the architecture is REBUILT around what physically works:
 *
 *   PocketShell Activity
 *     → Companion overlay (Compose panel chrome: handle, strip, cards)
 *       → ONE stable native host container (plain FrameLayout)
 *         → one WebView per tab, created with the EXACT baseline recipe
 *
 * What is GONE, permanently (the suspect family the harness exonerated or
 * outlived): forced-light configuration context, darkening-off levers,
 * Chrome-like UA in the render path, flash-guard background, compat
 * software layer, wide-viewport overrides, load-before-attach, the keyed
 * swap host, the attach kick, the pixel watchdog, the boot witness, the
 * console tail, the health sheet, LRU eviction and saveState/restore.
 * Every removed lever either appeared in the proven-baseline's A/B delta
 * or served the watchdogs that watched them.
 *
 * What is KEPT (product contract, none of it render-path): Name+URL
 * definitions, multiple tabs, persistence, cookies (incl. third-party +
 * flush), file upload bridge, DownloadManager, drag handle + remembered
 * height (Compose layer), back navigation, §15 navigation allowlist,
 * §16 permission denial, the m4.0.1 guarded-creation degradation, the
 * renderer-death guard, and the Phase 3.1 shared-deck focus bridge.
 *
 * The ONE remaining delta between the working baseline and this host is
 * the parent chain (the overlay's AndroidView node instead of the
 * activity's content view) — unavoidable by product definition, and now
 * the only suspect left if anything should still blank.
 *
 * m4.0.11 — FROZEN as the shipped renderer ("Replace Renderer Only"):
 * the sheet, drag handle, remembered height, tab strip, tab system,
 * picker and destination storage above this file are untouched; the only
 * change across the iterations was swapping the tab content renderer for
 * the exact baseline implementation below, diagnostics stripped. The
 * frozen configuration is specified in [CompanionRenderContract] and
 * unit-pinned; this file must implement that contract verbatim.
 */
object CompanionWebHost {

    /** File-chooser bridge: the UI layer installs a launcher; the client calls it. */
    interface FileChooserHost {
        fun launch(acceptType: String?, onResult: (Uri?) -> Unit)
    }

    /** UI-facing signals (all optional; the layer wires what it needs). */
    interface Listener {
        fun onVisitStarted(defId: String, url: String) {}
        fun onTitleReceived(defId: String, title: String) {}
        fun onExternalLinkUnhandled(uri: Uri) {}

        /** The MAIN frame failed to load — the canvas must say why. */
        fun onMainFrameError(defId: String, description: String) {}

        /** The page renderer died (default behavior kills the app; this
         *  host destroys ONLY the view and lets the UI explain). */
        fun onRendererGone(defId: String) {}
    }

    private var appContext: Context? = null

    /** One live WebView per OPEN tab — created lazily, kept until closed. */
    private val tabs = LinkedHashMap<String, WebView>()

    /** Tabs created but not yet loaded: their URL fires after first layout
     *  (the proven attach → layout → load sequence). */
    private val pendingLoad = HashMap<String, String>()

    private var activeDefId: String? = null
    private var listener: Listener? = null
    private var fileChooserHost: FileChooserHost? = null
    private var pendingFileChooser: ValueCallback<Array<Uri>>? = null

    /** m4.0.12 — tabs with a hard reload in flight; [client] restores the
     *  default cache mode on their next page finish. */
    private val hardReloadInFlight = HashSet<String>()

    /**
     * The single native host container. Compose's AndroidView hands this
     * SAME instance back on every (re)composition of the panel — the
     * WebViews inside it live at the process level, untouched by panel
     * collapse, tab switches or retries. Plain FrameLayout: the exact
     * parent class the proven baseline used.
     */
    private var canvasRef: WeakReference<FrameLayout>? = null
    private var canvasActivityRef: WeakReference<Activity>? = null

    /**
     * m4.0.1 (kept): a broken or freshly-updated WebView package must never
     * crash the process. Creation is the only provider-load point; when it
     * fails, [runtimeFailed] flips and the Companion surface degrades to an
     * honest notice while everything else keeps working.
     */
    @Volatile
    var runtimeFailed: Boolean = false
        private set

    private var cookiesConfigured = false

    fun init(context: Context) {
        // Context handoff ONLY (§22): runs in Application.onCreate — it must
        // NEVER touch android.webkit here.
        appContext = context.applicationContext
    }

    fun setListener(value: Listener?) {
        listener = value
    }

    fun setFileChooserHost(value: FileChooserHost?) {
        fileChooserHost = value
    }

    fun setActive(defId: String?) {
        activeDefId = defId
    }

    /** The installed WebView provider's version, honestly (failure cards
     *  and the render-baseline harness surface it). */
    fun webViewVersion(): String = try {
        WebView.getCurrentWebViewPackage()?.versionName?.takeIf { it.isNotBlank() } ?: "unknown"
    } catch (_: Throwable) {
        "unknown"
    }

    // ------------------------------------------------------------ the canvas

    /**
     * The stable native container for the overlay's AndroidView. Created
     * once per live Activity; if the Activity was recreated (process
     * survived), every WebView belongs to the dead one — the honest reset
     * is a fresh canvas and fresh tabs (cookies persist regardless).
     * Main thread only (Compose factory/update).
     */
    fun canvas(activity: Activity): FrameLayout {
        val existing = canvasRef?.get()
        if (existing != null && canvasActivityRef?.get() === activity) return existing
        if (existing != null) clearAll()
        return FrameLayout(activity).also {
            canvasRef = WeakReference(it)
            canvasActivityRef = WeakReference(activity)
        }
    }

    /**
     * The tab's WebView, creating it on first need with the EXACT proven
     * baseline recipe and scheduling its URL for the post-layout load.
     * Returns null only when the provider itself is broken ([runtimeFailed]).
     * NO loading, NO attachment here — [present] does both, in the proven
     * order. Main thread only.
     */
    fun prepare(defId: String, url: String, activity: Activity): WebView? {
        // An Activity recreation invalidates every live WebView (they belong
        // to the dead one) — the honest reset happens before anything else.
        if (canvasActivityRef?.get() != null && canvasActivityRef?.get() !== activity) {
            clearAll()
        }
        activeDefId = defId
        tabs[defId]?.let { return it }
        configureCookiesOnce()
        val webView = try {
            createWebView(activity, defId)
        } catch (_: Throwable) {
            runtimeFailed = true
            return null
        }
        runtimeFailed = false
        tabs[defId] = webView
        pendingLoad[defId] = url
        return webView
    }

    /**
     * Puts the tab's view on top of the native canvas — plain view surgery
     * (removeAllViews + addView), exactly the baseline's attachment step.
     * Idempotent and cheap: a re-composition with the same tab on top is a
     * no-op. The first presentation schedules the load AFTER first layout.
     */
    fun present(defId: String, webView: WebView) {
        val canvas = canvasRef?.get() ?: return
        val pending = pendingLoad[defId]
        if (pending == null && canvas.childCount == 1 && canvas.getChildAt(0) === webView) return
        canvas.removeAllViews()
        canvas.addView(
            webView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        webView.onResume()
        // §8: background tabs are paused, never destroyed for switching.
        tabs.forEach { (id, wv) -> if (id !== defId) runCatching { wv.onPause() } }
        if (pending != null) {
            pendingLoad.remove(defId)
            // The PROVEN sequence: attach → first layout → THEN loadUrl.
            webView.doOnLayout {
                if (tabs[defId] === webView) runCatching { webView.loadUrl(pending) }
            }
        }
    }

    /** Tab closed (or definition deleted): its state is dropped for good. */
    fun forgetTab(defId: String) {
        pendingLoad.remove(defId)
        hardReloadInFlight.remove(defId)
        val webView = tabs.remove(defId) ?: return
        canvasRef?.get()?.let { canvas -> if (canvas.getChildAt(0) === webView) canvas.removeAllViews() }
        destroyQuietly(webView)
    }

    fun forgetDefinition(defId: String) = forgetTab(defId)

    fun clearAll() {
        pendingLoad.clear()
        hardReloadInFlight.clear()
        canvasRef?.get()?.removeAllViews()
        tabs.values.toList().forEach { destroyQuietly(it) }
        tabs.clear()
        activeDefId = null
    }

    /**
     * m4.0.12 §3 — Refresh: a plain reload of ONE tab. The caller passes
     * the ACTIVE tab id; no other tab, no reset, no navigation — the page
     * reloads in place at its current URL.
     */
    fun reload(defId: String?): Boolean {
        val webView = defId?.let { tabs[it] } ?: return false
        return try {
            webView.reload()
            true
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * m4.0.12 §4 — Hard refresh, NOT a reset: the freshest possible reload
     * of ONE tab that never touches user data. For this single load the
     * HTTP cache is bypassed ([WebSettings.LOAD_NO_CACHE]); [client] restores
     * the default mode on page finish. Cookies, login sessions, DOM storage
     * and every other tab are untouched — the user stays logged in.
     */
    fun reloadHard(defId: String?): Boolean {
        val webView = defId?.let { tabs[it] } ?: return false
        return try {
            webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
            hardReloadInFlight.add(defId)
            webView.reload()
            true
        } catch (_: Throwable) {
            hardReloadInFlight.remove(defId)
            false
        }
    }

    /** Back-navigation probe for the §14 decision (false = no live history). */
    fun canGoBack(defId: String?): Boolean =
        defId?.let { tabs[it]?.canGoBack() } ?: false

    fun goBack(defId: String?): Boolean {
        val webView = defId?.let { tabs[it] } ?: return false
        if (!webView.canGoBack()) return false
        webView.goBack()
        return true
    }

    /** Activity pause: park everything and make cookie persistence deterministic. */
    fun pauseAll() {
        tabs.values.forEach { runCatching { it.onPause() } }
        if (tabs.isEmpty()) return
        try {
            CookieManager.getInstance().flush()
        } catch (_: Throwable) {
        }
    }

    /** Activity resume: only the active tab wakes (§8). */
    fun resumeActive() {
        activeDefId?.let { id -> tabs[id]?.onResume() }
    }

    // ------------------------------------------------------------------ core

    /**
     * THE PROVEN RECIPE — verbatim from the m4.0.9 baseline harness that
     * the device video verified on all four gate sites: `WebView(activity)`
     * with the real Activity, JavaScript + DOM storage, Android defaults
     * everywhere else. The only additions are the product contract's
     * site-facing clients (navigation allowlist, downloads, upload bridge,
     * permission denial) and two §16 hardening flags that cannot affect
     * https rendering. NO background override, NO UA override, NO
     * configuration context, NO layer type, NO darkening levers, NO
     * viewport overrides.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(activity: Activity, defId: String): WebView {
        val webView = WebView(activity)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            // §16 defense in depth (https rendering untouched): no file://
            // and no content:// inside the Companion canvas.
            allowFileAccess = false
            allowContentAccess = false
        }
        // m4.0.3 (kept): whichever surface the user taps last owns the
        // Phase 3.1 shared deck.
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                KeyboardInputRouter.webTarget = v
                // m4.0.12 §13: a typeable surface appeared — the app root
                // opens the shared deck so the SAME keyboard serves the
                // focused input (no system IME is ever involved).
                KeyboardInputRouter.onWebFocusGained?.invoke()
            }
        }
        webView.setDownloadListener(downloadListener())
        webView.webViewClient = client(defId)
        webView.webChromeClient = chromeClient(defId)
        return webView
    }

    /** §15 navigation policy: http(s) stays inside; everything else resolves out. */
    private fun client(defId: String): WebViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            return when (uri.scheme?.lowercase()) {
                "http", "https" -> false
                "intent" -> {
                    try {
                        val intent = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
                        appContext?.startActivity(intent)
                    } catch (_: Exception) {
                        listener?.onExternalLinkUnhandled(uri)
                    }
                    true
                }
                else -> {
                    // mailto/tel/sms/geo/unknown schemes: try the system;
                    // honest callback on failure — never silently broken (§15).
                    resolveExternally(uri)
                    true
                }
            }
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            if (url != null) listener?.onVisitStarted(defId, url)
        }

        override fun onPageFinished(view: WebView, url: String?) {
            // m4.0.12: a hard reload bypassed the HTTP cache for THIS load
            // only — restore the default mode so normal caching resumes for
            // the tab (and the other tabs never saw anything).
            if (hardReloadInFlight.remove(defId)) {
                try {
                    view.settings.cacheMode = WebSettings.LOAD_DEFAULT
                } catch (_: Throwable) {
                }
            }
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            // Subresource noise is ignored; a failed MAIN frame is surfaced
            // in the canvas — never a silent blank page.
            if (!request.isForMainFrame) return
            val description = error.description?.toString()?.takeIf { it.isNotBlank() }
                ?: "error ${error.errorCode}"
            listener?.onMainFrameError(defId, description)
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            // The renderer died — the default behavior KILLS THE APP; instead
            // destroy ONLY this view, keep PocketShell alive, and let the
            // canvas explain (broken WebView builds do this).
            tabs.remove(defId)
            pendingLoad.remove(defId)
            hardReloadInFlight.remove(defId)
            canvasRef?.get()?.let { canvas ->
                if (canvas.getChildAt(0) === view) canvas.removeAllViews()
            }
            destroyQuietly(view)
            listener?.onRendererGone(defId)
            return true // consumed
        }
    }

    private fun resolveExternally(uri: Uri) {
        val context = appContext ?: return
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: Exception) {
            listener?.onExternalLinkUnhandled(uri)
        }
    }

    /** §12/§16: file chooser bridged to the UI launcher; device permissions denied. */
    private fun chromeClient(defId: String): WebChromeClient = object : WebChromeClient() {
        override fun onShowFileChooser(
            webView: WebView?,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams?,
        ): Boolean {
            val host = fileChooserHost ?: run {
                filePathCallback.onReceiveValue(null)
                return false
            }
            // Exactly one pending callback (§12); a stale one is cancelled.
            pendingFileChooser?.onReceiveValue(null)
            pendingFileChooser = filePathCallback
            val accept = fileChooserParams?.acceptTypes
                ?.firstOrNull { it.isNotBlank() }
            host.launch(accept) { uri ->
                pendingFileChooser = null
                filePathCallback.onReceiveValue(if (uri == null) null else arrayOf(uri))
            }
            return true
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            request.deny() // §16: no device capabilities granted in Phase 4
        }

        override fun onGeolocationPermissionsShowPrompt(
            origin: String?,
            callback: GeolocationPermissions.Callback?,
        ) {
            callback?.invoke(origin, false, false) // denied, remembered quietly
        }

        override fun onReceivedTitle(view: WebView?, title: String?) {
            if (!title.isNullOrBlank()) listener?.onTitleReceived(defId, title)
        }
    }

    /** §13: DownloadManager into app-specific storage — no permission, no crash. */
    private fun downloadListener(): DownloadListener =
        DownloadListener { url, _, _, mimeType, _ ->
            val context = appContext ?: return@DownloadListener
            val uri = Uri.parse(url)
            val scheme = uri.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") return@DownloadListener
            try {
                val base = (uri.lastPathSegment ?: "download")
                    .substringAfterLast('/')
                    .ifBlank { "download" }
                val name = "$base-${System.currentTimeMillis() % 100000L}"
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED,
                    )
                    setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, name)
                    mimeType?.takeIf { it.isNotBlank() }?.let { setMimeType(it) }
                }
                (context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager)
                    ?.enqueue(request)
                Toast.makeText(context, "Downloading to app storage…", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                // §13: never crash on a download; the site stays usable.
            }
        }

    /** R4 (kept): cookie acceptance is global; third-party cookies are
     *  required by real login flows (§7). Runs once, lazily, guarded —
     *  never during Application startup (m4.0.1). */
    private fun configureCookiesOnce() {
        if (cookiesConfigured) return
        try {
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(null, true)
            }
            cookiesConfigured = true
        } catch (_: Throwable) {
            // Provider broken — retried on the next creation attempt.
        }
    }

    private fun destroyQuietly(webView: WebView) {
        try {
            if (KeyboardInputRouter.webTarget === webView) KeyboardInputRouter.webTarget = null
        } catch (_: Throwable) {
        }
        try {
            webView.stopLoading()
            webView.onPause()
            webView.clearHistory()
            webView.destroy()
        } catch (_: Exception) {
        }
    }
}

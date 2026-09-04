package app.pocketshell.companion

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import app.pocketshell.keyboard.KeyboardInputRouter

/**
 * Phase 4 — the embedded web runtime (docs/PHASE-4-COMPANION-DESIGN.md §8).
 *
 * Process-scoped pool (the TerminalSessionManager/PackageGateway pattern):
 * one lazily-created WebView per open tab, an LRU alive-set cap of
 * active + 4 background, saveState-on-evict → restore-on-reactivate, and a
 * trim-memory hook that drops every background WebView first (its tab
 * remains in the strip and rehydrates on demand).
 *
 * The WebView is the invisible engine (brief R3) — every site-facing
 * policy (navigation allowlist, external intents, permissions, downloads,
 * file chooser) lives in the two clients set up in [createWebView]; the UI
 * layer only receives callbacks.
 */
object CompanionWebPool {

    private const val ALIVE_BACKGROUND_CAP = 4

    /** File-chooser bridge: the UI layer installs a launcher; the client calls it. */
    interface FileChooserHost {
        fun launch(acceptType: String?, onResult: (Uri?) -> Unit)
    }

    /** UI-facing signals (all optional; the layer wires what it needs). */
    interface Listener {
        fun onVisitStarted(defId: String, url: String) {}
        fun onTitleReceived(defId: String, title: String) {}
        fun onExternalLinkUnhandled(uri: Uri) {}

        /** m4.0.2: the MAIN frame failed to load — the canvas must say why. */
        fun onMainFrameError(defId: String, description: String) {}

        /** m4.0.2: the page renderer died (the classic white canvas on
         *  broken WebView builds). The pool destroyed the view; the app
         *  stays alive and the UI explains. */
        fun onRendererGone(defId: String) {}

        /** m4.0.3: the page never painted ANYTHING within the watchdog
         *  window (no error event, no renderer death — the silent-white
         *  signature the device kept hitting). The UI explains honestly. */
        fun onRenderStuck(defId: String) {}

        /** m4.0.5: pixels DID paint, but the page's own app never mounted
         *  (the device's cookie-banner state: pipeline alive, page dead).
         *  [diagnostics] is the page's own testimony — readyState, DOM
         *  element count, first script error, first console line. */
        fun onAppNotBooted(defId: String, diagnostics: String) {}
    }

    private class TabEntry(val webView: WebView) {
        var lastUsed: Long = System.currentTimeMillis()
    }

    private var appContext: Context? = null
    private val pool = LinkedHashMap<String, TabEntry>()

    /** saveState bundles of EVICTED tabs — restore-on-reactivate (§8). */
    private val evictedStates = HashMap<String, Bundle>()

    private var activeDefId: String? = null
    private var listener: Listener? = null
    private var fileChooserHost: FileChooserHost? = null
    private var pendingFileChooser: ValueCallback<Array<Uri>>? = null

    /**
     * m4.0.1 hotfix (device-reported 2026-09-05): a broken or freshly-updated
     * WebView package crashed provider load — and because m4.0 called
     * CookieManager.getInstance() during Application.onCreate, EVERY
     * PocketShell launch died before any UI. The provider's health must
     * never gate app startup. Every provider touch now happens lazily at
     * first WebView creation and is guarded; when it fails, [runtimeFailed]
     * flips true so the Companion surface shows an honest notice while the
     * terminal, Home and all other screens keep working untouched.
     */
    @Volatile
    var runtimeFailed: Boolean = false
        private set

    private var cookiesConfigured = false

    /**
     * m4.0.4 PIXEL-TRUTH render watchdog (rewritten from the m4.0.3 event
     * version, which the device defeated: progress/commit fire faithfully on
     * the broken build while the compositor rasterizes NOTHING — the watchdog
     * stood down and the canvas stayed a bare black flash-guard rectangle).
     * Nothing but actual page pixels stand it down now: every
     * [POLL_INTERVAL_MS] the tab is probed via [RenderProbe] (software draw
     * readback, plus an API 29+ glass readback that catches frames lost
     * between render and presentation). A tab still showing the bare
     * flash-guard color after [MAX_PAINT_PROBES] probes raises
     * [Listener.onRenderStuck].
     */
    private val watchdogHandler = Handler(Looper.getMainLooper())
    private val renderStallArmed = HashSet<String>()

    /** m4.0.5: tabs whose DOM boot-witness is running (see BootWitness). */
    private val bootWitnessArmed = HashSet<String>()

    /** m4.0.5: per-tab console testimony (cleared per document, dropped
     *  with the tab — it exists to explain a failure card, nothing else). */
    private val consoleTails = HashMap<String, ConsoleTail>()

    fun init(context: Context) {
        // Context handoff ONLY. This runs in Application.onCreate — it must
        // NEVER touch android.webkit here (see [runtimeFailed] note). Cookie
        // configuration moved to [configureCookiesOnce], called under guard
        // at first WebView creation.
        appContext = context.applicationContext
    }

    fun setListener(value: Listener?) {
        listener = value
    }

    fun setFileChooserHost(value: FileChooserHost?) {
        fileChooserHost = value
    }

    /** Track which tab is on top; drives resume/pause and the LRU order. */
    fun setActive(defId: String?) {
        activeDefId = defId
        defId?.let { id -> pool[id]?.let { it.lastUsed = System.currentTimeMillis() } }
    }

    /**
     * The live WebView for a tab, creating it on first need and rehydrating
     * an evicted one from its saved navigation state (contract §8).
     * Returns null before init — or when the WebView provider itself is
     * broken (m4.0.1: [runtimeFailed] then tells the UI why).
     *
     * m4.0.3: [context] — the Activity context — is used for creation when
     * provided. m4.0/m4.0.2 created every WebView with the APPLICATION
     * context, a documented source of blank-canvas / dead-IME WebViews on
     * several OEM builds. [compatRender] creates the view with
     * LAYER_TYPE_SOFTWARE — the Retry escape hatch for devices whose GPU
     * path inside a broken WebView build never rasterizes.
     */
    fun acquire(
        defId: String,
        url: String,
        context: Context? = null,
        compatRender: Boolean = false,
    ): WebView? {
        val creationContext = context ?: appContext ?: return null
        val isNew = !pool.containsKey(defId)
        val entry = pool.getOrPut(defId) {
            // m4.0.1: creation is the ONLY step that loads the provider —
            // a broken WebView package now degrades the Companion to the
            // UI's "unavailable" notice instead of crashing the process.
            configureCookiesOnce()
            try {
                val created = TabEntry(createWebView(creationContext, defId, compatRender))
                runtimeFailed = false
                created
            } catch (_: Throwable) {
                runtimeFailed = true
                return null
            }
        }
        entry.lastUsed = System.currentTimeMillis()
        if (isNew) {
            // m4.0.3: the load path is guarded too — a provider that loads
            // but explodes at first load must degrade, never crash.
            try {
                val saved = evictedStates.remove(defId)
                if (saved != null) {
                    entry.webView.restoreState(saved)
                    // restoreState may not commit a load when the stack is empty.
                    if (entry.webView.url == null) entry.webView.loadUrl(url)
                } else {
                    entry.webView.loadUrl(url)
                }
                armRenderWatchdog(defId)
                armBootWitness(defId)
            } catch (_: Throwable) {
                runtimeFailed = true
                disarmTab(defId)
                pool.remove(defId)?.webView?.destroyQuietly()
                return null
            }
            enforceCapacity()
        }
        entry.webView.onResume()
        // §8: background tabs are paused, never destroyed for switching.
        pool.forEach { (id, e) -> if (id != defId) e.webView.onPause() }
        return entry.webView
    }

    /** Tab closed (or definition deleted): its state is dropped for good. */
    fun forgetTab(defId: String) {
        evictedStates.remove(defId)
        disarmTab(defId)
        pool.remove(defId)?.webView?.destroyQuietly()
    }

    fun forgetDefinition(defId: String) = forgetTab(defId)

    fun clearAll() {
        evictedStates.clear()
        pool.keys.toList().forEach { disarmTab(it) }
        pool.values.toList().forEach { it.webView.destroyQuietly() }
        pool.clear()
    }

    /** Back-navigation probe for the §14 decision (false = no live history). */
    fun canGoBack(defId: String?): Boolean =
        defId?.let { pool[it]?.webView?.canGoBack() } ?: false

    fun goBack(defId: String?): Boolean {
        val webView = defId?.let { pool[it]?.webView } ?: return false
        if (!webView.canGoBack()) return false
        webView.goBack()
        return true
    }

    /** Activity pause: park everything and make cookie persistence deterministic. */
    fun pauseAll() {
        pool.values.forEach { it.webView.onPause() }
        // m4.0.1: nothing to persist while no tab ever created a WebView —
        // and getInstance() would load the provider, which is exactly what
        // must not happen incidentally. Guarded even when the pool is live.
        if (pool.isEmpty()) return
        try {
            CookieManager.getInstance().flush()
        } catch (_: Throwable) {
        }
    }

    /** Activity resume: only the active tab wakes (§8). */
    fun resumeActive() {
        activeDefId?.let { id -> pool[id]?.webView?.onResume() }
    }

    /**
     * Trim callback (§8): destroy every background WebView (saveState first);
     * tabs remain in the strip and restore on demand. The active tab survives.
     */
    fun onTrimMemory(level: Int) {
        if (level < ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) return
        pool.entries
            .filter { it.key != activeDefId }
            .sortedBy { it.value.lastUsed }
            .toList()
            .forEach { (id, entry) ->
                evictedStates[id] = Bundle().also { entry.webView.saveState(it) }
                disarmTab(id)
                entry.webView.destroyQuietly()
                pool.remove(id)
            }
    }

    /** One stop for every witness/console channel of a tab (m4.0.5). */
    private fun disarmTab(defId: String) {
        renderStallArmed.remove(defId)
        bootWitnessArmed.remove(defId)
        consoleTails.remove(defId)
    }

    // ------------------------------------------------------------------ core

    // ---- render-stall watchdog (m4.0.3; pixel-truth rewrite m4.0.4) --------

    /** Idempotent: re-arming an armed tab (every navigation) keeps the
     *  running probe chain instead of stacking a second one. */
    private fun armRenderWatchdog(defId: String) {
        if (!renderStallArmed.add(defId)) return
        schedulePaintProbe(defId, 0)
    }

    /** m4.0.5 — the DOM boot-witness (same arming discipline as the pixel
     *  probe; the two are independent witnesses over the same tab). */
    private fun armBootWitness(defId: String) {
        if (!bootWitnessArmed.add(defId)) return
        scheduleBootProbe(defId, 0, 0, null)
    }

    /**
     * Polls the page's own truth every [POLL_INTERVAL_MS]. MOUNTED stands it
     * down; budget exhaustion raises [Listener.onAppNotBooted] with the
     * page's own diagnostics. evaluateJavascript's callback returns on the
     * main thread, so the chain continues FROM the callback — never by
     * blocking it.
     */
    private fun scheduleBootProbe(defId: String, attempt: Int, idlePosts: Int, last: BootWitness.Truth?) {
        watchdogHandler.postDelayed({
            if (!bootWitnessArmed.contains(defId)) return@postDelayed
            val view = pool[defId]?.webView ?: run {
                bootWitnessArmed.remove(defId)
                return@postDelayed
            }
            if ((view.width <= 0 || view.height <= 0 || !view.isShown) && idlePosts < MAX_IDLE_POSTS) {
                scheduleBootProbe(defId, attempt, idlePosts + 1, last)
                return@postDelayed
            }
            try {
                view.evaluateJavascript(BootWitness.DOM_TRUTH_JS) { raw ->
                    if (!bootWitnessArmed.contains(defId)) return@evaluateJavascript
                    val truth = BootWitness.parseTruth(raw)
                    if (truth != null && BootWitness.mounted(truth)) {
                        bootWitnessArmed.remove(defId)
                    } else if (attempt + 1 >= MAX_BOOT_PROBES) {
                        bootWitnessArmed.remove(defId)
                        listener?.onAppNotBooted(
                            defId,
                            BootWitness.diagnose(truth ?: last, consoleTails[defId]?.snapshot().orEmpty()),
                        )
                    } else {
                        scheduleBootProbe(defId, attempt + 1, 0, truth ?: last)
                    }
                }
            } catch (_: Throwable) {
                if (attempt + 1 >= MAX_BOOT_PROBES) {
                    bootWitnessArmed.remove(defId)
                    listener?.onAppNotBooted(
                        defId,
                        BootWitness.diagnose(last, consoleTails[defId]?.snapshot().orEmpty()),
                    )
                } else {
                    scheduleBootProbe(defId, attempt + 1, 0, last)
                }
            }
        }, POLL_INTERVAL_MS)
    }

    private fun schedulePaintProbe(defId: String, attempt: Int, idlePosts: Int = 0) {
        watchdogHandler.postDelayed({
            // A closed/forgotten/stood-down tab must never raise a ghost.
            if (!renderStallArmed.contains(defId)) return@postDelayed
            val view = pool[defId]?.webView ?: run {
                renderStallArmed.remove(defId)
                return@postDelayed
            }
            // A view with no chance to draw yet (not laid out, or detached
            // while the sheet is down) waits without burning its probes —
            // up to MAX_IDLE_POSTS, then the honest attempts proceed.
            if ((view.width <= 0 || view.height <= 0 || !view.isShown) && idlePosts < MAX_IDLE_POSTS) {
                schedulePaintProbe(defId, attempt, idlePosts + 1)
                return@postDelayed
            }
            try {
                RenderProbe.captureHasPainted(view) { painted ->
                    // Main-thread callback; the tab may have been forgotten
                    // while the capture ran — the arm-set is the gate.
                    if (!renderStallArmed.contains(defId)) return@captureHasPainted
                    if (painted) {
                        renderStallArmed.remove(defId)
                    } else if (attempt + 1 >= MAX_PAINT_PROBES) {
                        renderStallArmed.remove(defId)
                        listener?.onRenderStuck(defId)
                    } else {
                        schedulePaintProbe(defId, attempt + 1)
                    }
                }
            } catch (_: Throwable) {
                // A throwing probe must never kill the poll loop — treat as
                // not painted and continue the chain.
                if (attempt + 1 >= MAX_PAINT_PROBES) {
                    renderStallArmed.remove(defId)
                    listener?.onRenderStuck(defId)
                } else {
                    schedulePaintProbe(defId, attempt + 1)
                }
            }
        }, POLL_INTERVAL_MS)
    }

    /**
     * m4.0.3 — the installed WebView provider's version, honestly.
     * Surfaced on every failure card so "white canvas" mysteries become a
     * concrete fact the user can act on (update / roll back).
     */
    fun webViewVersion(): String = try {
        WebView.getCurrentWebViewPackage()?.versionName?.takeIf { it.isNotBlank() } ?: "unknown"
    } catch (_: Throwable) {
        "unknown"
    }

    /** R4: cookie acceptance is global and persistent; third-party cookies
     *  are required by real login flows (§7). Runs once, lazily, guarded —
     *  never during Application startup (m4.0.1 hotfix). */
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

    @SuppressLint("SetJavaScriptEnabled")
    @Suppress("DEPRECATION")
    private fun createWebView(context: Context, defId: String, compatRender: Boolean): WebView {
        val webView = WebView(context)
        // Midnight canvas — no white load flash; ALSO the probe's reference
        // color: a canvas uniformly in this exact value never drew a pixel.
        webView.setBackgroundColor(RenderProbe.WEBVIEW_BACKGROUND)
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        if (compatRender) {
            // m4.0.3 Retry escape hatch: GPU rasterization inside a broken
            // WebView build can silently paint nothing; software rendering
            // is the honest second attempt.
            webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        }
        // m4.0.3: whichever surface the user taps last owns the shared deck.
        webView.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) KeyboardInputRouter.webTarget = v
        }
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = false
            allowContentAccess = false
            mediaPlaybackRequiresUserGesture = true
            useWideViewPort = true
            loadWithOverviewMode = true
            // m4.0.5 — the black-canvas fix, layer 2 of 3: WebView Force
            // Dark is ACTIVE by default for legacy-target apps (targetSdk 28)
            // in dark mode, and its algorithmic darkening is a documented
            // mangler of exactly this state — site shell paints dark, real
            // content never becomes usable. The site renders as authored.
            // (Layer 1: the theme flag; layer 3: this tiered runtime call —
            // API 33+ replaced Force Dark with algorithmic darkening, whose
            // runtime lever is setAlgorithmicDarkeningAllowed.)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                setAlgorithmicDarkeningAllowed(false)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                forceDark = WebSettings.FORCE_DARK_OFF
            }
            // m4.0.5 — layer 4: present this device's exact Chrome-mobile
            // UA. Google login answers disallowed_useragent to the "; wv"
            // marker outright, and bot-fronted sites quietly serve degraded
            // or challenged bundles to embedded clients.
            val chromeUa = WebCompat.chromeLikeUserAgent(userAgentString)
            if (chromeUa.isNotBlank()) userAgentString = chromeUa
        }
        webView.setDownloadListener(downloadListener(context))
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
                    // honest Toast on failure — never silently broken (§15).
                    resolveExternally(uri)
                    true
                }
            }
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            if (url != null) listener?.onVisitStarted(defId, url)
            // m4.0.4: every navigation re-arms the pixel probe (idempotent).
            // This also covers the failure-clearing path in recordLastUrl —
            // a tab navigating away from a stall gets a fresh probe budget.
            armRenderWatchdog(defId)
            // m4.0.5: and the DOM boot-witness rides along (idempotent — a
            // mounted page stands it down on the first probe).
            armBootWitness(defId)
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            // m4.0.5: fresh document — fresh console testimony, and the
            // boot-error trap goes in at the earliest main-frame moment so
            // even first-bundle syntax failures are captured.
            consoleTails.getOrPut(defId) { ConsoleTail() }.clear()
            try {
                view.evaluateJavascript(BootWitness.BOOT_TRAP_JS, null)
            } catch (_: Throwable) {
            }
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            // m4.0.2: subresource noise is ignored; a failed MAIN frame is
            // surfaced in the canvas — never a silent white page.
            if (!request.isForMainFrame) return
            val description = error.description?.toString()?.takeIf { it.isNotBlank() }
                ?: "error ${error.errorCode}"
            listener?.onMainFrameError(defId, description)
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            // m4.0.2: the renderer died — the default behavior KILLS THE APP;
            // instead destroy ONLY this view, keep PocketShell alive, and
            // let the canvas explain (broken WebView builds do this).
            try {
                pool.remove(defId)
            } catch (_: Throwable) {
            }
            disarmTab(defId)
            try {
                view.destroy()
            } catch (_: Throwable) {
            }
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

        override fun onConsoleMessage(message: ConsoleMessage?): Boolean {
            // m4.0.5: keep the site's last words — the first console line is
            // often the whole diagnosis (SyntaxError from an old Chromium,
            // a refused request, a CSP denial).
            try {
                if (message?.message()?.isNotBlank() == true) {
                    val source = message.sourceId()?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
                    val line = buildString {
                        append(message.message())
                        if (source != null) append(" @").append(source)
                        if (message.lineNumber() > 0) append(":").append(message.lineNumber())
                    }
                    consoleTails.getOrPut(defId) { ConsoleTail() }.append(line)
                }
            } catch (_: Throwable) {
            }
            return super.onConsoleMessage(message)
        }

    }

    /** §13: DownloadManager into app-specific storage — no permission, no crash. */
    private fun downloadListener(context: Context): DownloadListener =
        DownloadListener { url, _, _, mimeType, _ ->
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

    /** LRU eviction beyond active + cap; evicted tabs keep a restore bundle. */
    private fun enforceCapacity() {
        val over = pool.size - (ALIVE_BACKGROUND_CAP + 1)
        if (over <= 0) return
        pool.entries
            .filter { it.key != activeDefId }
            .sortedBy { it.value.lastUsed }
            .take(over)
            .toList()
            .forEach { (id, entry) ->
                evictedStates[id] = Bundle().also { entry.webView.saveState(it) }
                disarmTab(id)
                entry.webView.destroyQuietly()
                pool.remove(id)
            }
    }

    private fun WebView.destroyQuietly() {
        try {
            if (KeyboardInputRouter.webTarget === this) KeyboardInputRouter.webTarget = null
        } catch (_: Throwable) {
        }
        try {
            stopLoading()
            onPause()
            clearHistory()
            destroy()
        } catch (_: Exception) {
        }
    }

    /** m4.0.4: probe cadence and the stall budget — POLL_INTERVAL_MS ×
     *  MAX_PAINT_PROBES = the same generous 15s the m4.0.3 single-shot timer
     *  allowed, now spent actually verifying pixels. */
    internal const val POLL_INTERVAL_MS = 2_500L
    internal const val MAX_PAINT_PROBES = 6

    /** Idle waits (no chance to draw yet) before attempts must proceed. */
    internal const val MAX_IDLE_POSTS = 8

    /** The documented total budget before the canvas says so honestly. */
    internal const val RENDER_STALL_TIMEOUT_MS = POLL_INTERVAL_MS * MAX_PAINT_PROBES

    /** m4.0.5: boot-witness budget — 8 probes × 2.5s = a 20s window for the
     *  page's own app to mount before the canvas carries the page's
     *  testimony. Generous on purpose: a slow carrier must not be told its
     *  page is broken while it is merely still loading. */
    internal const val MAX_BOOT_PROBES = 8
}

package app.pocketshell.companion

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

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

    fun init(context: Context) {
        appContext = context.applicationContext
        // R4: cookie acceptance is global and persistent; third-party cookies
        // are required by real login flows (§7). CookieManager persists to
        // disk automatically; flush() at pause makes it deterministic.
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(null, true)
        }
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
     * Returns null only before init.
     */
    fun acquire(defId: String, url: String): WebView? {
        val context = appContext ?: return null
        val isNew = !pool.containsKey(defId)
        val entry = pool.getOrPut(defId) { TabEntry(createWebView(context, defId)) }
        entry.lastUsed = System.currentTimeMillis()
        if (isNew) {
            val saved = evictedStates.remove(defId)
            if (saved != null) {
                entry.webView.restoreState(saved)
                // restoreState may not commit a load when the stack is empty.
                if (entry.webView.url == null) entry.webView.loadUrl(url)
            } else {
                entry.webView.loadUrl(url)
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
        pool.remove(defId)?.webView?.destroyQuietly()
    }

    fun forgetDefinition(defId: String) = forgetTab(defId)

    fun clearAll() {
        evictedStates.clear()
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
        CookieManager.getInstance().flush()
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
                entry.webView.destroyQuietly()
                pool.remove(id)
            }
    }

    // ------------------------------------------------------------------ core

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(context: Context, defId: String): WebView {
        val webView = WebView(context)
        webView.setBackgroundColor(0xFF080F1D.toInt()) // Midnight canvas — no white load flash
        webView.isFocusableInTouchMode = true
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = false
            allowContentAccess = false
            mediaPlaybackRequiresUserGesture = true
            useWideViewPort = true
            loadWithOverviewMode = true
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
                entry.webView.destroyQuietly()
                pool.remove(id)
            }
    }

    private fun WebView.destroyQuietly() {
        try {
            stopLoading()
            onPause()
            clearHistory()
            destroy()
        } catch (_: Exception) {
        }
    }
}

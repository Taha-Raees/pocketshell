package app.pocketshell.diagnostic

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import app.pocketshell.companion.CompanionWebPool
import app.pocketshell.companion.WebCompat
import org.json.JSONObject

/**
 * Phase 4.0.9 — the MINIMAL BASELINE WEBVIEW EXPERIMENT (the user's
 * mandated control, built exactly to the brief):
 *
 *   Activity context
 *     → plain FrameLayout / LinearLayout
 *       → one normal WebView
 *         → load https://example.com
 *
 * …then the same with one Companion variable switched on at a time
 * ([BaselineMatrix.VARIANTS]). No pool, no Compose, no forced-light
 * context in the baseline, no custom UA in the baseline, no pixel
 * watchdog, no attach kick, no boot witness, no evaluateJavascript in the
 * render path, no compat retry. Android defaults first — so a physical
 * device run separates
 *   (A) Android System WebView itself cannot display these sites here
 * from
 *   (B) the Companion implementation prevents an otherwise working
 *       WebView from displaying.
 *
 * This Activity is PocketShell itself (same process, same theme, same
 * signing identity) — NOT a separate test app. It never runs at startup:
 * the WebView is created in onCreate, so §22 (Application startup must
 * never touch android.webkit) stays intact.
 *
 * The lifecycle is the simplest one the brief asks for:
 *   create WebView → attach to visible parent → layout → load URL.
 */
class BaselineWebViewActivity : Activity() {

    private lateinit var container: FrameLayout
    private lateinit var statusView: TextView
    private var webView: WebView? = null

    private var variant = BaselineMatrix.VARIANTS.first()
    private var url = BaselineMatrix.URLS.first()
    private var lastPageFacts: BaselineMatrix.PageFacts? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = buildUi()
        setContentView(root)
        rebuildWebView()
    }

    /** Programmatic Midnight UI — one status line, one control row, one canvas. */
    private fun buildUi(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0B0F1A.toInt())
        }

        statusView = TextView(this).apply {
            setTextColor(0xFFD7DEE8.toInt())
            setPadding(dp(14), dp(12), dp(14), dp(8))
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
        }

        fun control(label: String, onClick: () -> Unit): Button =
            Button(this).apply {
                text = label
                textSize = 12f
                isAllCaps = false
                setOnClickListener { onClick() }
            }

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), 0, dp(8), dp(8))
            gravity = Gravity.CENTER_VERTICAL
        }
        controls.addView(control("URL ▸") {
            url = BaselineMatrix.nextUrl(url)
            loadOrRebuild()
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        controls.addView(control("MODE ▸") {
            variant = BaselineMatrix.nextVariant(variant.key)
            rebuildWebView()
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        controls.addView(control("INSPECT") {
            inspectPage()
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        controls.addView(control("COPY") {
            copyStatus()
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        container = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }

        root.addView(statusView)
        root.addView(controls)
        root.addView(
            container,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )
        return root
    }

    /**
     * The exact creation recipe per variant. Baseline = `WebView(activity)`
     * with JS + DOM storage on (the brief's only allowed settings), nothing
     * else touched; every flag is guarded behind its variant.
     */
    private fun rebuildWebView() {
        lastPageFacts = null
        webView?.let { old ->
            try {
                old.stopLoading()
                container.removeAllViews()
                old.destroy()
            } catch (_: Throwable) {
            }
        }
        val creation = if (variant.forcedLight) {
            try {
                val config = Configuration(resources.configuration)
                config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    Configuration.UI_MODE_NIGHT_NO
                createConfigurationContext(config)
            } catch (_: Throwable) {
                this
            }
        } else {
            this
        }
        val wv = WebView(creation)
        try {
            wv.settings.javaScriptEnabled = true
            wv.settings.domStorageEnabled = true
            if (variant.wideViewport) {
                wv.settings.useWideViewPort = true
                wv.settings.loadWithOverviewMode = true
            }
            if (variant.chromeUa) {
                val ua = WebCompat.chromeLikeUserAgent(wv.settings.userAgentString)
                if (ua.isNotBlank()) wv.settings.userAgentString = ua
            }
            if (variant.midnightBg) {
                wv.setBackgroundColor(0xFF080F1D.toInt())
            }
        } catch (_: Throwable) {
        }
        webView = wv

        // The mandated lifecycle, with the ONE load-timing variable:
        //   create → attach → layout → load  (baseline)
        //   create → load → attach → layout  (+LOAD BEFORE ATTACH)
        if (variant.loadBeforeAttach) {
            try {
                wv.loadUrl(url)
            } catch (_: Throwable) {
            }
        }
        container.addView(
            wv,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        if (!variant.loadBeforeAttach) {
            wv.doOnLayout {
                try {
                    wv.loadUrl(url)
                } catch (_: Throwable) {
                }
                updateStatus()
            }
        }
        updateStatus()
    }

    /** URL switch inside the current WebView (same instance, fresh load). */
    private fun loadOrRebuild() {
        val wv = webView
        if (wv == null) {
            rebuildWebView()
            return
        }
        lastPageFacts = null
        try {
            wv.loadUrl(url)
        } catch (_: Throwable) {
        }
        updateStatus()
    }

    /** View truth only — no JavaScript, always current. */
    private fun currentViewFacts(): BaselineMatrix.ViewFacts? {
        val wv = webView ?: return null
        val rect = Rect()
        try {
            wv.getGlobalVisibleRect(rect)
        } catch (_: Throwable) {
        }
        return BaselineMatrix.ViewFacts(
            attached = wv.isAttachedToWindow,
            width = wv.width,
            height = wv.height,
            visibleLeft = rect.left,
            visibleTop = rect.top,
            visibleRight = rect.right,
            visibleBottom = rect.bottom,
            layerType = BaselineMatrix.layerTypeName(wv.layerType),
        )
    }

    /**
     * The OPT-IN page metrics probe (read-only, on demand, never in the
     * render path): catches the classic "blank but painted" cause — a page
     * whose UI sized itself to a bogus viewport.
     */
    private fun inspectPage() {
        val wv = webView ?: return
        try {
            wv.evaluateJavascript(BaselineMatrix.PAGE_METRICS_JS) { raw ->
                runOnUiThread {
                    lastPageFacts = parsePageFacts(raw)
                    updateStatus()
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun parsePageFacts(raw: String?): BaselineMatrix.PageFacts? {
        val text = raw?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
        return try {
            // evaluateJavascript returns the JSON-encoded string literal.
            val inner = if (text.startsWith("\"")) JSONObject("""{"v":$text}""").getString("v") else text
            val obj = JSONObject(inner)
            BaselineMatrix.PageFacts(
                title = obj.optString("t"),
                innerWidth = obj.optInt("iw", -1),
                innerHeight = obj.optInt("ih", -1),
                devicePixelRatio = obj.optDouble("dpr", -1.0),
                visualWidth = obj.optInt("vw", -1),
                visualHeight = obj.optInt("vh", -1),
            )
        } catch (_: Throwable) {
            null
        }
    }

    private fun copyStatus() {
        try {
            val clip = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clip?.setPrimaryClip(ClipData.newPlainText("PocketShell render baseline", statusView.text))
            Toast.makeText(this, "Baseline status copied — paste it in the chat", Toast.LENGTH_SHORT).show()
        } catch (_: Throwable) {
        }
    }

    private fun updateStatus() {
        statusView.text = BaselineMatrix.status(
            variant = variant,
            url = url,
            webviewVersion = CompanionWebPool.webViewVersion(),
            userAgent = try {
                webView?.settings?.userAgentString.orEmpty()
            } catch (_: Throwable) {
                ""
            },
            view = currentViewFacts(),
            page = lastPageFacts,
        )
    }

    override fun onDestroy() {
        webView?.let { wv ->
            try {
                wv.stopLoading()
                wv.destroy()
            } catch (_: Throwable) {
            }
        }
        webView = null
        super.onDestroy()
    }

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics)
            .toInt()

    private fun View.doOnLayout(action: () -> Unit) {
        if (width > 0 && height > 0) {
            action()
        } else {
            addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
                override fun onLayoutChange(
                    v: View, l: Int, t: Int, r: Int, b: Int,
                    ol: Int, ot: Int, or: Int, ob: Int,
                ) {
                    removeOnLayoutChangeListener(this)
                    action()
                }
            })
        }
    }
}

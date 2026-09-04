package app.pocketshell.companion

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * m4.0.5 — the DOM ground-truth witness (device-reported 2026-09-05: with
 * the site's own cookie banner visible and answering taps, the m4.0.4 pixel
 * probe stood down — the page pipeline was alive and SOME pixels painted —
 * while the site's app never started; the canvas stayed the site's empty
 * darkened body).
 *
 * Pixels cannot tell "page painted its empty shell" from "page is alive".
 * The witness asks the page itself: a boot-error trap injected at page
 * start records script failures from the first moment, and a poll of
 * document.readyState + DOM element count decides whether the page's app
 * ever actually MOUNTED. A consent banner is a few dozen DOM nodes; any
 * real app shell is hundreds. All decisions are pure and unit-pinned —
 * no WebView fakes, per the testing contract.
 */
object BootWitness {

    /**
     * Injected via evaluateJavascript at every page start (onPageStarted —
     * the earliest main-frame moment): records window errors and unhandled
     * promise rejections into window.__psBoot for the witness to collect.
     */
    const val BOOT_TRAP_JS: String =
        "(function(){if(window.__psBoot)return;window.__psBoot={errs:[]};" +
            "window.addEventListener('error',function(e){try{" +
            "window.__psBoot.errs.push(String(e.message||e.error||'script error').slice(0,180))" +
            "}catch(_){}});window.addEventListener('unhandledrejection',function(e){try{" +
            "var r=e.reason;window.__psBoot.errs.push('promise:'+String((r&&r.message)?r.message:r)" +
            ".slice(0,180))}catch(_){}})})()"

    /** One probe of the page's own truth (safe against a hostile/odd DOM). */
    const val DOM_TRUTH_JS: String =
        "(function(){try{var b=window.__psBoot;return JSON.stringify({" +
            "rs:document.readyState,n:document.getElementsByTagName('*').length," +
            "t:(document.body?document.body.innerText.length:0)," +
            "e:(b?b.errs.slice(0,2):[])})}catch(err){return JSON.stringify(" +
            "{rs:'probe-error',n:-1,t:0,e:[String(err).slice(0,120)]})}})()"

    /**
     * A page whose app mounted has hundreds of DOM nodes; a bare shell with
     * only a consent overlay — the device's exact captured state — stays in
     * the dozens. Above this floor the witness declares the page alive.
     */
    const val MOUNT_ELEMENT_FLOOR = 60

    /** One reading of the page's truth (parsed from [DOM_TRUTH_JS]). */
    data class Truth(
        val readyState: String,
        val elementCount: Int,
        val textLength: Int,
        val bootErrors: List<String>,
    )

    /**
     * Parses the raw evaluateJavascript answer — a JSON-encoded STRING (the
     * probe JSON.stringify-ed its reading) — into a [Truth]. Null when the
     * answer is unreadable (the page navigated away, was destroyed, or the
     * probe crashed): the caller keeps polling, never trusts garbage.
     */
    fun parseTruth(raw: String?): Truth? {
        if (raw.isNullOrBlank()) return null
        return try {
            // Level 1: the callback text is itself a JSON string literal.
            val inner = Json.parseToJsonElement(raw).jsonPrimitive.content
            // Level 2: that inner text is the probe's JSON object.
            val obj = Json.parseToJsonElement(inner).jsonObject
            Truth(
                readyState = obj["rs"]?.jsonPrimitive?.content ?: "unknown",
                elementCount = obj["n"]?.jsonPrimitive?.content?.toIntOrNull() ?: -1,
                textLength = obj["t"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                bootErrors = obj["e"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
            )
        } catch (_: Throwable) {
            null
        }
    }

    /** True when the page's app shell actually exists in the DOM. */
    fun mounted(truth: Truth): Boolean = truth.elementCount >= MOUNT_ELEMENT_FLOOR

    /**
     * The honest detail line for the failure card: exactly what the PAGE
     * said about itself, then the first console line. Pure; unit-pinned;
     * hard-capped so a chatty page can never blow out the card.
     */
    fun diagnose(truth: Truth?, consoleTail: List<String>): String {
        val parts = mutableListOf<String>()
        if (truth == null) {
            parts.add("page never answered the health probe")
        } else {
            parts.add("readyState=${truth.readyState}")
            parts.add(
                if (truth.elementCount >= 0) "${truth.elementCount} DOM elements"
                else "DOM unreadable",
            )
            truth.bootErrors.firstOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { parts.add("error: $it") }
        }
        consoleTail.firstOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { parts.add("console: $it") }
        return parts.joinToString(" · ").take(MAX_DIAGNOSIS_LENGTH)
    }

    const val MAX_DIAGNOSIS_LENGTH = 320
}

/**
 * The last few console lines of one tab (m4.0.5). Sites log their own
 * death — a SyntaxError from a too-old Chromium, a refused request, a CSP
 * denial — and the first line of that testimony is exactly what turns the
 * next device report into a diagnosis. Bounded ring: newest kept, oldest
 * dropped, every line length-capped.
 */
class ConsoleTail(
    private val cap: Int = 8,
    private val maxLineLength: Int = 160,
) {
    private val lines = ArrayDeque<String>()

    fun append(line: String) {
        val cleaned = line.replace('\n', ' ').trim().take(maxLineLength)
        if (cleaned.isEmpty()) return
        lines.addLast(cleaned)
        while (lines.size > cap) lines.removeFirst()
    }

    fun snapshot(): List<String> = lines.toList()

    fun clear() = lines.clear()
}

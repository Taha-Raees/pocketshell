package app.pocketshell.companion

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI

/**
 * Phase 4 — Companion (docs/PHASE-4-COMPANION-DESIGN.md §4).
 *
 * A Companion is exactly Name + URL — nothing else (brief R2: the
 * architecture is fully generic; there is no provider concept anywhere).
 * [TabRecord]s are workspace instances of a definition; their page state
 * lives in the in-process WebView pool, their cold-restore anchor is
 * [TabRecord.lastUrl].
 */

@Serializable
data class CompanionDef(
    val id: String,
    val name: String,
    val url: String,
)

@Serializable
data class TabRecord(
    val defId: String,
    val lastUrl: String? = null,
)

/** Shared JSON instance for the persisted companion collections. */
val CompanionJson: Json = Json { ignoreUnknownKeys = true }

/**
 * Validation + normalization (§4 of the contract). Pure functions — every
 * rule here is unit-pinned. All rules fail CLOSED: an invalid input returns
 * null and the caller refuses to save.
 */
object CompanionValidation {

    const val MAX_NAME_LENGTH = 40
    const val MAX_URL_LENGTH = 2048

    private val ALLOWED_SCHEMES = setOf("http", "https")
    private val FORBIDDEN_SCHEME_PREFIXES = listOf(
        "javascript:", "file:", "data:", "about:", "intent:", "blob:",
    )
    private val WHITESPACE = Regex("\\s")

    /**
     * Normalized https URL or null. No scheme → https:// prepended; only
     * http/https survive; javascript:/file:/data:/about:/intent: and every
     * other scheme are rejected at definition time (§16: defense in depth —
     * the WebView client enforces the same allowlist at navigation time).
     */
    fun normalizeUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_URL_LENGTH) return null
        if (WHITESPACE.containsMatchIn(trimmed)) return null
        val lowered = trimmed.lowercase()
        if (FORBIDDEN_SCHEME_PREFIXES.any { lowered.startsWith(it) }) return null
        val withScheme = if (ALLOWED_SCHEMES.any { lowered.startsWith("$it://") }) {
            trimmed
        } else {
            "https://$trimmed"
        }
        val uri = try {
            URI(withScheme)
        } catch (_: Exception) {
            return null
        }
        val host = uri.host ?: return null
        if (host.isBlank() || !host.contains('.')) return null
        // The exact normalized string is persisted (canonical by construction).
        return withScheme
    }

    /** Trimmed display name or null (non-blank, length-capped). */
    fun validateName(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_NAME_LENGTH) return null
        return trimmed
    }
}

/**
 * m4.0.2 — honest in-canvas failure state (device-reported 2026-09-05: a
 * broken/rolled-back WebView package rendered the canvas pure WHITE with
 * no explanation). The Companion canvas must never be mysteriously blank:
 * a failed main-frame load or a dead page renderer becomes a labeled
 * Midnight card with the real detail + the installed WebView version and
 * a Retry action. Pure state + text — unit-pinned, no WebView fakes.
 *
 * m4.1.0: the RENDER_STALLED / APP_NOT_BOOTED kinds are RETIRED with the
 * watchdog family that produced them — the m4.0.9 control experiment
 * proved the render path they policed was never the problem.
 */
enum class CompanionFailureKind { LOAD_ERROR, RENDERER_GONE }

fun failureTitle(kind: CompanionFailureKind): String = when (kind) {
    CompanionFailureKind.LOAD_ERROR -> "Page didn't load"
    CompanionFailureKind.RENDERER_GONE -> "Page renderer crashed"
}

fun failureHint(kind: CompanionFailureKind): String = when (kind) {
    CompanionFailureKind.LOAD_ERROR ->
        "Check the network/VPN for this site — or update Android System WebView — then retry."
    CompanionFailureKind.RENDERER_GONE ->
        "This WebView build looks broken on this device. Update or roll it back, then retry."
}

data class CompanionFailure(
    val kind: CompanionFailureKind,
    val detail: String? = null,
    val webViewVersion: String? = null,
) {
    val title: String
        get() = failureTitle(kind)

    val body: String
        get() = listOfNotNull(
            detail?.takeIf { it.isNotBlank() },
            webViewVersion?.takeIf { it.isNotBlank() }?.let { "Android System WebView $it" },
        ).joinToString(" · ")

    val hint: String
        get() = failureHint(kind)
}

/**
 * Back-navigation decision (§14) — pure, unit-pinned. The composable wires
 * PASS_THROUGH by disabling its BackHandler, so the runtime logic is exactly
 * this function plus the two actions.
 */
enum class CompanionBackAction { WEB_BACK, COLLAPSE, PASS_THROUGH }

fun decideBackAction(canGoBack: Boolean, raised: Boolean): CompanionBackAction = when {
    !raised -> CompanionBackAction.PASS_THROUGH
    canGoBack -> CompanionBackAction.WEB_BACK
    else -> CompanionBackAction.COLLAPSE
}

/**
 * Panel height anchors + release math (§9) — pure, unit-pinned. Fractions of
 * the full container height.
 */
object CompanionHeights {
    const val HALF = 0.55f
    const val FULL = 0.94f

    /** Snap to an anchor only within this window; otherwise stay put (R5). */
    const val SNAP_WINDOW = 0.06f

    /** Below this the release collapses (dragging down "closes" the sheet). */
    const val COLLAPSE_THRESHOLD = 0.08f

    const val MIN_RAISED = 0.02f

    /** The settled fraction for a released drag position. */
    fun settled(released: Float): Float {
        val clamped = released.coerceIn(0f, FULL)
        if (clamped < COLLAPSE_THRESHOLD) return 0f
        for (anchor in floatArrayOf(HALF, FULL)) {
            if (kotlin.math.abs(clamped - anchor) <= SNAP_WINDOW) return anchor
        }
        return clamped
    }

    /** Clamps a persisted/serialized fraction back into the legal band. */
    fun clamp(fraction: Float): Float = fraction.coerceIn(0f, FULL)

    /** True when a fraction means the panel is raised (handle always exists). */
    fun isRaised(fraction: Float): Boolean = fraction >= MIN_RAISED
}

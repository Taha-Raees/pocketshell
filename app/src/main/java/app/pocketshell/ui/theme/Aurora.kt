package app.pocketshell.ui.theme

import android.app.ActivityManager
import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Aurora visual layer (Control Center task §4) — the ONE premium
 * animated theme, treated as a shared system rather than per-component
 * effects:
 *
 *   ONE clock     [rememberAuroraPhase] runs a single frame loop at the app
 *                 root; every aurora surface reads the SAME phase state, so
 *                 there is exactly one invalidation source in the process.
 *   DRAW-ONLY     all aurora rendering happens inside drawBehind; content
 *                 never recomposes for the aurora, only redraws.
 *   THEME-GATED   surfaces render aurora only when [TerminalTheme.isAurora]
 *                 (and their own enabled gate) — every other theme pays zero.
 *   MOTION-HONEST [AuroraMotion.resolve] maps the system reduced-motion
 *                 signals (animator duration scale 0, low-RAM device) to a
 *                 STATIC aurora: same palette, fixed phase, no frame loop.
 *
 * Design note: the standing PocketShell motion rule ("nothing loops",
 * docs/PHASE-3.2-DESIGN.md §12) is deliberately excepted HERE — the Aurora
 * identity is an explicit product decision — but the exception keeps the
 * rule's spirit: 26s period, low alpha, no per-component loops, no blur.
 */

/** Fixed phase for the static (reduced-motion) aurora — a chosen pleasing frame. */
const val STATIC_AURORA_PHASE = 0.35f

/** Full cycle length in seconds — slow enough to read as ambience, not motion. */
const val AURORA_PERIOD_SECONDS = 26f

/**
 * The reduced-motion / capability policy. Pure and JVM-test-pinned.
 */
enum class AuroraMotionPolicy { ANIMATED, STATIC }

object AuroraMotion {
    /**
     * [animatorDurationScale] is the system animator scale (0 = the user's
     * "remove animations" accessibility choice); [isLowRamDevice] is the
     * ActivityManager's own verdict. Either signal forces the STATIC
     * aurora.
     */
    fun resolve(animatorDurationScale: Float, isLowRamDevice: Boolean): AuroraMotionPolicy =
        if (animatorDurationScale <= 0f || isLowRamDevice) {
            AuroraMotionPolicy.STATIC
        } else {
            AuroraMotionPolicy.ANIMATED
        }
}

/**
 * The shared aurora phase, provided once at the app root. Surfaces read the
 * State inside their draw scopes; the default (a fixed state) means
 * previews/tests and non-aurora themes get a harmless static value.
 */
val LocalAuroraPhase = compositionLocalOf<State<Float>> {
    mutableFloatStateOf(STATIC_AURORA_PHASE)
}

/**
 * Resolves the device's real motion policy (system animator scale +
 * ActivityManager low-RAM verdict). Reads system settings ONCE per
 * composition of the caller.
 */
@Composable
fun rememberAuroraMotionPolicy(): AuroraMotionPolicy {
    val context = LocalContext.current
    return remember {
        val scale = runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }.getOrDefault(1f)
        val lowRam = runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            am?.isLowRamDevice == true
        }.getOrDefault(false)
        AuroraMotion.resolve(scale, lowRam)
    }
}

/**
 * The ONE aurora clock. Composed only when the app wants aurora motion
 * (aurora theme + ANIMATED policy); any other case returns a fixed state
 * and launches no frame loop at all.
 */
@Composable
fun rememberAuroraPhase(motionPolicy: AuroraMotionPolicy): State<Float> {
    val static = remember { mutableFloatStateOf(STATIC_AURORA_PHASE) }
    if (motionPolicy == AuroraMotionPolicy.STATIC) return static
    val phase = remember { mutableFloatStateOf(STATIC_AURORA_PHASE) }
    androidx.compose.runtime.LaunchedEffect(motionPolicy) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                if (TerminalTheme.isAurora) {
                    val elapsedSeconds = (now - start) / 1_000_000_000f
                    phase.floatValue = (elapsedSeconds / AURORA_PERIOD_SECONDS) % 1f
                } else {
                    // not the aurora identity: pin the static phase and stop
                    // invalidating — no aurora surface redraws on other themes
                    phase.floatValue = STATIC_AURORA_PHASE
                }
            }
        }
    }
    return phase
}

// ------------------------------------------------------------------ surfaces

/**
 * The aurora backdrop: four slow-orbiting radial washes drawn behind the
 * page content. Alpha is deliberately low — the effect must read as ambient
 * polar light behind the chrome, never compete with text. The light variant
 * uses the DEEPENED stop set (auroraStopsLight) so the sweep stays visible
 * on pale paper instead of washing out.
 */
fun Modifier.auroraBackdrop(phase: State<Float>): Modifier = composed {
    // keyed on the light/dark flip so the stop set is never stale
    val stops = remember(TerminalTheme.isLight) {
        (if (TerminalTheme.isLight) ThemeCatalog.auroraStopsLight else ThemeCatalog.auroraStops)
            .map { Color(it) }
    }
    drawBehind {
        // DRAW-TIME gate: the snapshot read happens at draw, so the state
        // flip to a non-aurora theme invalidates and repaints this surface
        // directly — immune to modifier-rebuild staleness (the owner device
        // reported aurora leaking on non-aurora themes; this closes that
        // entire class).
        if (!TerminalTheme.isAurora) return@drawBehind
        val p = phase.value
        val maxDim = maxOf(size.width, size.height)
        val alpha = if (TerminalTheme.isLight) 0.17f else 0.16f
        stops.forEachIndexed { i, color ->
            val t = 2.0 * Math.PI * (p + i / stops.size.toDouble())
            val cx = size.width * (0.5 + 0.42 * sin(t)).toFloat()
            val cy = size.height * (0.5 + 0.38 * cos(t * 0.8)).toFloat()
            val radius = maxDim * (0.42f + 0.1f * sin(t * 1.3).toFloat())
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = alpha), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = radius,
                ),
                radius = radius,
                center = Offset(cx, cy),
            )
        }
    }
}

/**
 * The terminal's Aurora scrim alpha (Control Center II §4): the terminal
 * surface composites over the aurora backdrop through a translucent canvas
 * tone so the environment stays visible behind/around the content. Aurora
 * only — every other theme keeps the fully opaque canvas. Pure value, pinned
 * by test; the vendored TerminalView paints NO default background itself
 * (TerminalRenderer draws only non-default cell fills), so this Compose
 * scrim IS the visible terminal background — no renderer change needed.
 */
const val AURORA_TERMINAL_SCRIM_ALPHA = 0.84f

fun terminalScrimColor(isAurora: Boolean): Color =
    TerminalTheme.canvas.copy(alpha = if (isAurora) AURORA_TERMINAL_SCRIM_ALPHA else 1f)

/**
 * The aurora edge: a slowly circulating multi-stop stroke for IMPORTANT
 * surfaces only (primary buttons, selected theme card, hero tiles) — the
 * "glow flowing through the UI" the brief asks for, kept to a hairline so
 * it reads as premium, not gaming-RGB. Applies only when [enabled].
 */
fun Modifier.auroraEdge(
    phase: State<Float>,
    cornerRadius: Dp,
    strokeWidth: Dp = 1.5.dp,
    baseAlpha: Float = 0.6f,
    preview: Boolean = false,
): Modifier = composed {
    drawBehind {
        // DRAW-TIME gate (see auroraBackdrop). [preview] is the Appearance
        // page's explicit override: the Aurora theme card shows its edge
        // even while another identity is active.
        if (!TerminalTheme.isAurora && !preview) return@drawBehind
        val p = phase.value
        val accent = TerminalTheme.accent
        val bright = TerminalTheme.accentBright
        val stops = listOf(bright, accent, Color(ThemeCatalog.auroraStops[1]), bright)
        // circulating color stops — the same rotation the backdrop uses
        val n = stops.size
        val colorStops = buildList {
            for (i in 0 until n) {
                val offset = ((p + i.toFloat() / n) % 1f)
                add(offset to stops[i].copy(alpha = baseAlpha))
            }
            add(1f to stops[0].copy(alpha = baseAlpha))
        }.sortedBy { it.first }
        drawRoundRect(
            brush = Brush.sweepGradient(*colorStops.toTypedArray()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius.toPx()),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth.toPx()),
        )
    }
}

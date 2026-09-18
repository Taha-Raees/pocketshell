package app.pocketshell.widget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pocketshell.ui.home.HomeTokens

/**
 * M8 — the widgets' own marks, drawn in the HomeMarks stroke family (one
 * stroke language, Sapphire pair, original art — no emoji, no third-party
 * logos; docs/PHASE-3.3-DESIGN.md §12 identity rule). A mark appears ONLY
 * on the widget card it belongs to.
 */

/** Servers: three nodes joined by a listening chain — a fleet, not a graph. */
@Composable
fun ServersMark(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.085f, cap = StrokeCap.Round)
        val chain = Path().apply {
            moveTo(w * 0.22f, h * 0.26f)
            lineTo(w * 0.50f, h * 0.50f)
            lineTo(w * 0.22f, h * 0.74f)
            moveTo(w * 0.78f, h * 0.26f)
            lineTo(w * 0.50f, h * 0.50f)
            lineTo(w * 0.78f, h * 0.74f)
        }
        drawPath(chain, HomeTokens.textDim.copy(alpha = 0.75f), style = stroke)
        fun node(cx: Float, cy: Float, color: androidx.compose.ui.graphics.Color) {
            drawCircle(color = color, radius = w * 0.105f, center = Offset(cx * w, cy * h))
        }
        node(0.50f, 0.50f, HomeTokens.accentBright)
        node(0.22f, 0.26f, HomeTokens.accent)
        node(0.22f, 0.74f, HomeTokens.accent)
        node(0.78f, 0.26f, HomeTokens.accent)
        node(0.78f, 0.74f, HomeTokens.accent)
    }
}

/** Storage: a stack of two plates on a base bar — sized bytes, not a pie. */
@Composable
fun StorageMark(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.085f, cap = StrokeCap.Round)
        drawRoundRect(
            color = HomeTokens.accent,
            topLeft = Offset(w * 0.18f, h * 0.16f),
            size = androidx.compose.ui.geometry.Size(w * 0.64f, h * 0.28f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.08f, w * 0.08f),
            style = stroke,
        )
        drawRoundRect(
            color = HomeTokens.accentBright,
            topLeft = Offset(w * 0.18f, h * 0.36f),
            size = androidx.compose.ui.geometry.Size(w * 0.64f, h * 0.28f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.08f, w * 0.08f),
        )
        drawLine(
            color = HomeTokens.textDim.copy(alpha = 0.75f),
            start = Offset(w * 0.18f, h * 0.80f),
            end = Offset(w * 0.82f, h * 0.80f),
            strokeWidth = w * 0.085f,
            cap = StrokeCap.Round,
        )
    }
}

/** Agents: a rising attention spark over a steady baseline. */
@Composable
fun AgentsMark(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.085f, cap = StrokeCap.Round)
        drawLine(
            color = HomeTokens.textDim.copy(alpha = 0.75f),
            start = Offset(w * 0.14f, h * 0.78f),
            end = Offset(w * 0.86f, h * 0.78f),
            strokeWidth = w * 0.075f,
            cap = StrokeCap.Round,
        )
        val spark = Path().apply {
            moveTo(w * 0.20f, h * 0.66f)
            lineTo(w * 0.42f, h * 0.34f)
            lineTo(w * 0.58f, h * 0.52f)
            lineTo(w * 0.80f, h * 0.20f)
        }
        drawPath(spark, HomeTokens.accent, style = stroke)
        drawCircle(
            color = HomeTokens.accentBright,
            radius = w * 0.075f,
            center = Offset(w * 0.80f, h * 0.20f),
        )
    }
}

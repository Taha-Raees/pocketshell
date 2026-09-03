package app.pocketshell.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pocketshell.ui.theme.TerminalTheme

/**
 * PocketShell's own icon language (Phase 3.2/3.3): original drawn marks, one
 * stroke family, Sapphire on blue-dark. No emoji, no third-party logos (the
 * mountain is original art; no distro logo is copied), no broken placeholders
 * — apps without icons get the neutral monogram plate. Marks are IDENTITY:
 * they appear in the header and on the environment tiles they belong to —
 * never as decoration elsewhere (docs/PHASE-3.3-DESIGN.md §12).
 */

/** The PocketShell brand mark: a pocket-shaped tile with a prompt chevron + cursor. */
@Composable
fun BrandMark(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .background(HomeTokens.surfaceApp, RoundedCornerShape(size * 0.30f))
            .border(1.dp, HomeTokens.hairline, RoundedCornerShape(size * 0.30f)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(size * 0.24f)) {
            val w = this.size.width
            val h = this.size.height
            val stroke = Stroke(width = w * 0.14f, cap = StrokeCap.Round)
            // prompt chevron
            val chevron = Path().apply {
                moveTo(w * 0.08f, h * 0.12f)
                lineTo(w * 0.52f, h * 0.5f)
                lineTo(w * 0.08f, h * 0.88f)
            }
            drawPath(chevron, HomeTokens.accent, style = stroke)
            // block cursor
            drawRoundRect(
                color = HomeTokens.accentBright,
                topLeft = Offset(w * 0.66f, h * 0.52f),
                size = Size(w * 0.30f, h * 0.44f),
                cornerRadius = CornerRadius(w * 0.06f, w * 0.06f),
            )
        }
    }
}

/** The Terminal environment mark: a large prompt chevron + block cursor (no container). */
@Composable
fun TerminalMark(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.09f, cap = StrokeCap.Round)
        val chevron = Path().apply {
            moveTo(w * 0.06f, h * 0.16f)
            lineTo(w * 0.46f, h * 0.5f)
            lineTo(w * 0.06f, h * 0.84f)
        }
        drawPath(chevron, HomeTokens.accent, style = stroke)
        drawRoundRect(
            color = HomeTokens.accentBright,
            topLeft = Offset(w * 0.60f, h * 0.56f),
            size = Size(w * 0.34f, h * 0.30f),
            cornerRadius = CornerRadius(w * 0.05f, w * 0.05f),
        )
    }
}

/**
 * The Linux environment mark: original twin-peak mountain (a quiet nod to
 * Alpine — NOT a distro logo), with a small accent star above the ridge.
 */
@Composable
fun MountainMark(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.075f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        // far peak (dimmer)
        val far = Path().apply {
            moveTo(w * 0.04f, h * 0.82f)
            lineTo(w * 0.36f, h * 0.30f)
            lineTo(w * 0.62f, h * 0.82f)
        }
        drawPath(far, HomeTokens.textDim.copy(alpha = 0.75f), style = stroke)
        // near peak (accent)
        val near = Path().apply {
            moveTo(w * 0.34f, h * 0.82f)
            lineTo(w * 0.68f, h * 0.22f)
            lineTo(w * 0.98f, h * 0.82f)
        }
        drawPath(near, HomeTokens.accent, style = stroke)
        // snow tick on the near peak (single quiet detail)
        drawLine(
            color = HomeTokens.accentBright,
            start = Offset(w * 0.585f, h * 0.40f),
            end = Offset(w * 0.68f, h * 0.50f),
            strokeWidth = w * 0.075f,
            cap = StrokeCap.Round,
        )
        // star above the ridge
        drawCircle(
            color = HomeTokens.accentBright,
            radius = w * 0.045f,
            center = Offset(w * 0.16f, h * 0.16f),
        )
    }
}

/**
 * A command app's launcher icon: the neutral monogram plate — a borderless
 * tone step (keyAlt) with the app's monogram in the terminal typeface. An app
 * icon on the workspace canvas, not a card: no border, no shadow (Phase 3.3
 * §6). Polished, consistent, honest (never a broken placeholder logo).
 */
@Composable
fun MonogramTile(monogram: String, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .background(HomeTokens.surfaceApp, RoundedCornerShape(HomeTokens.appTileRadius)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = monogram,
            fontFamily = TerminalTheme.mono,
            fontWeight = FontWeight.Medium,
            fontSize = (size.value * 0.34f).sp,
            color = HomeTokens.accentBright,
        )
    }
}

/** Small drawn "+" used by the quick-action control (dark glyph on Sapphire). */
@Composable
fun PlusGlyph(size: Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val stroke = Stroke(width = w * 0.14f, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.5f, w * 0.18f), Offset(w * 0.5f, w * 0.82f), stroke.width, StrokeCap.Round)
        drawLine(color, Offset(w * 0.18f, w * 0.5f), Offset(w * 0.82f, w * 0.5f), stroke.width, StrokeCap.Round)
    }
}

/** Section label — small, dim, spaced (the workspace's quiet wayfinding). */
@Composable
fun HomeSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(horizontal = 4.dp),
        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
        fontFamily = TerminalTheme.mono,
        letterSpacing = 1.6.sp,
        color = HomeTokens.textDim,
    )
}


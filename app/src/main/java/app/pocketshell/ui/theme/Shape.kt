package app.pocketshell.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * PocketShell shape + spacing + motion tokens (docs/UI-REDESIGN.md §3.3/§3.6).
 *
 * Radius system: 12 (inputs/chips) · 16 (cards) · 20 (hero/sheets/dialogs) ·
 * circles for pills/FAB/dots. Nothing exceeds 20 except circles — smooth
 * without becoming bubbly.
 */
val PSShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

/** 4dp-base spacing scale — the only horizontal/vertical paddings allowed. */
object PSSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val hero = 40.dp
    val brand = 48.dp

    /** Screen gutter (horizontal padding of every screen). */
    val gutter = 20.dp
}

/** Motion tokens (docs/UI-REDESIGN.md §3.6): 180–260ms, standard easing. */
object PSMotion {
    const val FAST_MS = 120
    const val NORMAL_MS = 200
    const val SLOW_MS = 260
}

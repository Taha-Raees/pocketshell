package app.pocketshell.widget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pocketshell.ui.home.HomeTokens
import app.pocketshell.ui.theme.TerminalTheme

/**
 * M8.4.3 — the SHARED action/section primitives for Home Applications.
 * Every application used to hand-roll these (IconAction, RefreshAction,
 * NewButton, CompactAction, section labels) with drifting sizes and
 * paddings; one implementation keeps the action language identical
 * across the carousel:
 *
 *   - [IconAction]     — one glyph, 28dp hit area, named for a11y.
 *   - [LabelledAction] — glyph + word (RUN NOW / DRY RUN class actions:
 *     icon-first, word kept because the verb matters).
 *   - [SectionLabel]   — the STAGED/UNSTAGED/PINNED class of group label.
 *
 * The canvas's action language: icons where the meaning is clear, words
 * where the verb matters, and ALWAYS an accessibility name.
 */

/** One compact icon action — the 28dp hit area is the house standard. */
@Composable
fun IconAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = HomeTokens.accent,
) {
    Icon(
        imageVector = icon,
        contentDescription = label,
        tint = tint,
        modifier = Modifier
            .size(28.dp)
            .clickable(role = Role.Button, onClickLabel = label) { onClick() }
            .padding(5.dp),
    )
}

/** Icon + word in one 28dp-tall control — for verbs too important to icon-only. */
@Composable
fun LabelledAction(
    icon: ImageVector,
    label: String,
    word: String,
    onClick: () -> Unit,
    tint: Color = HomeTokens.accent,
) {
    Row(
        modifier = Modifier
            .clickable(role = Role.Button, onClickLabel = label) { onClick() }
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = word,
            fontFamily = TerminalTheme.mono,
            fontSize = 11.sp,
            color = tint,
        )
    }
}

/** A group label inside a scrolling card (STAGED, UNSTAGED, PINNED, RECENT…). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontFamily = TerminalTheme.mono,
        fontSize = 10.sp,
        letterSpacing = 1.sp,
        color = HomeTokens.textDim,
        modifier = modifier.padding(top = 6.dp, bottom = 1.dp),
    )
}

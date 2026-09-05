package app.pocketshell.ui.companion

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pocketshell.companion.CompanionDef
import app.pocketshell.companion.CompanionTabs
import app.pocketshell.companion.TabRecord
import app.pocketshell.ui.theme.TerminalTheme

/**
 * Phase 4 — Companion tab strip (docs/PHASE-4-COMPANION-DESIGN.md §11).
 *
 * The Phase 3.1 editor-tab language, INVERTED for a top strip: tabs hang
 * DOWN with rounded bottom corners; the active tab is painted in the exact
 * canvas color and CUTS the strip's bottom hairline so it opens into the
 * web content; the active tab carries the 2.5dp Sapphire hairline at its
 * bottom edge (the mirror of the terminal's top edge). NOT pills, no
 * per-tab outlines — inactive tabs are transparent with a quiet right
 * separator, exactly like their terminal siblings.
 */
@Composable
fun CompanionTabStrip(
    tabs: List<TabRecord>,
    defs: List<CompanionDef>,
    activeId: String?,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
    onAdd: () -> Unit,
    onRefresh: () -> Unit = {},
    onHardRefresh: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val defsById = remember(defs) { defs.associateBy { it.id } }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(TerminalTheme.tabStrip),
    ) {
        // Hairline under the strip; the active tab paints over (cuts) it.
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(1.dp)
                .background(TerminalTheme.divider),
        )
        Row(modifier = Modifier.fillMaxSize().padding(start = 6.dp)) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Top,
                contentPadding = PaddingValues(bottom = 0.dp),
            ) {
                items(tabs, key = { it.defId }) { tab ->
                    val def = defsById[tab.defId]
                    CompanionTab(
                        label = def?.name ?: "Web",
                        isSelected = tab.defId == activeId,
                        onSelect = { onSelect(tab.defId) },
                        onClose = { onClose(tab.defId) },
                    )
                }
            }
            RefreshTabButton(
                onRefresh = onRefresh,
                onHardRefresh = onHardRefresh,
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .padding(start = 6.dp),
            )
            AddTabButton(
                onAdd = onAdd,
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .padding(start = 2.dp, end = 4.dp),
            )
        }
    }
}

@Composable
private fun CompanionTab(
    label: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
) {
    val height by animateDpAsState(
        targetValue = if (isSelected) 40.dp else 30.dp,
        animationSpec = tween(140),
        label = "companionTabHeight",
    )
    val container by animateColorAsState(
        targetValue = if (isSelected) TerminalTheme.canvas else Color.Transparent,
        animationSpec = tween(140),
        label = "companionTabContainer",
    )
    val shape = RoundedCornerShape(
        bottomStart = TerminalTheme.tabTopRadius,
        bottomEnd = TerminalTheme.tabTopRadius,
    )

    Box(
        modifier = Modifier
            .height(height)
            .widthIn(min = 84.dp, max = 160.dp)
            .clip(shape)
            .background(container)
            .clickable { onSelect() }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // The active tab is painted in the canvas tone (pinned dark
                // in every theme) — its label rides the pinned-light onCanvas
                // token, never the theme-swapped text colors.
                color = if (isSelected) TerminalTheme.onCanvas else TerminalTheme.textDim,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (isSelected) {
                CloseTabButton(onClose = onClose, modifier = Modifier.padding(start = 6.dp))
            }
        }
        if (!isSelected) {
            // The quiet right separator (terminal tab language, mirrored).
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(1.dp)
                    .height(18.dp)
                    .background(TerminalTheme.divider),
            )
        }
        if (isSelected) {
            // The structured bottom edge: one 2.5dp Sapphire hairline —
            // the mirror of the terminal tab's top edge.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 8.dp)
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .background(TerminalTheme.accent, RoundedCornerShape(2.dp)),
            )
        }
    }
}

@Composable
private fun CloseTabButton(onClose: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClose() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Close,
            contentDescription = "Close tab",
            tint = TerminalTheme.textDim,
            modifier = Modifier.size(13.dp),
        )
    }
}

@Composable
private fun AddTabButton(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onAdd() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = "New Companion tab",
            tint = TerminalTheme.textDim,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * m4.0.12 — the refresh control in the diagnostics chip's old slot.
 * Tap: a plain reload of the ACTIVE tab (same URL, same tab, nothing else
 * touched — §3). Long-press: a HARD refresh of the same page — the
 * freshest possible load that still preserves cookies, sessions and every
 * other tab (§4). One quiet glyph, the exact strip-button language of its
 * siblings.
 */
@Composable
private fun RefreshTabButton(
    onRefresh: () -> Unit,
    onHardRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onRefresh() },
                    onLongPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onHardRefresh()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Refresh,
            contentDescription = "Refresh — hold for hard refresh",
            tint = TerminalTheme.textDim,
            modifier = Modifier.size(16.dp),
        )
    }
}

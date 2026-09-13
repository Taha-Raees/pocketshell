package app.pocketshell.ui.companion

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pocketshell.companion.CompanionDef
import app.pocketshell.companion.CompanionTabs
import app.pocketshell.companion.TabRecord
import app.pocketshell.launchers.LauncherBundledIcons
import app.pocketshell.ui.theme.TerminalTheme
import java.util.concurrent.ConcurrentHashMap

/**
 * M7.2 companion polish — in-memory bitmap cache for companion tab logos.
 *
 * Resolution strategy (NO network, NO duplicate assets):
 * 1. If the companion's [CompanionDef.id] is a known builtin-* id, use it
 *    directly — [LauncherBundledIcons] maps it to the packaged asset.
 * 2. Otherwise fall back to text-only (returns null).
 *
 * Bitmaps are decoded ONCE and stored in a [ConcurrentHashMap]; the cache
 * lives for the process lifetime (icons are tiny WebP files — negligible
 * memory). Thread-safe: multiple Compose recompositions can call [get]
 * concurrently without duplicating decodes.
 */
object CompanionTabIcons {

    private val cache = ConcurrentHashMap<String, Bitmap?>()

    /**
     * Returns a [Bitmap] for the companion logo, or null if no bundled icon
     * exists. [assets] is the app's AssetManager; [light] mirrors the current
     * [app.pocketshell.ui.theme.TerminalTheme.isLight] value.
     */
    fun get(def: CompanionDef, assets: AssetManager, light: Boolean): Bitmap? {
        val iconId = resolveIconId(def.id) ?: return null
        val cacheKey = "$iconId:$light"
        return cache.getOrPut(cacheKey) {
            val path = LauncherBundledIcons.assetPathFor(iconId, light) ?: return@getOrPut null
            try {
                assets.open(path).use { BitmapFactory.decodeStream(it) }
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Maps a companion def id to a [LauncherBundledIcons] curated id, or
     * null for fully custom companions. Internal visibility for unit tests.
     */
    internal fun resolveIconId(defId: String): String? {
        // Builtin seeds already carry the exact curated id.
        if (defId in LauncherBundledIcons.coveredIds) return defId
        return null
    }

    /** Clears cached bitmaps (e.g. after a theme change). */
    fun invalidate() = cache.clear()
}

/**
 * Phase 4 — Companion tab strip (docs/PHASE-4-COMPANION-DESIGN.md §11).
 *
 * The Phase 3.1 editor-tab language, INVERTED for a top strip: tabs hang
 * DOWN with rounded bottom corners; the active tab is painted in the exact
 * canvas color and CUTS the strip's bottom hairline so it opens into the
 * web content; the active tab carries the accent hairline at its bottom
 * edge (the mirror of the terminal's top edge). NOT pills, no per-tab
 * outlines — inactive tabs are transparent with a quiet right separator,
 * exactly like their terminal siblings.
 *
 * M7.2 companion polish — tab logos added: builtin companions show their
 * bundled icon (15dp, from [CompanionTabIcons]); custom companions fall
 * back to text-only. Tab padding widened to 10dp horizontal for breathing
 * room: |  [Logo] Label  |. Width range updated to 72–152dp.
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
    val listState = rememberLazyListState()
    // Keep the ACTIVE tab in view when a switch lands outside the visible
    // window — the strip scrolls horizontally, the active tab never hides.
    LaunchedEffect(activeId, tabs.size) {
        val idx = tabs.indexOfFirst { it.defId == activeId }
        if (idx >= 0 && listState.layoutInfo.visibleItemsInfo.none { it.index == idx }) {
            listState.animateScrollToItem(idx)
        }
    }
    val sheetShape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(sheetShape)
            .background(TerminalTheme.tabStrip)
            .border(1.dp, TerminalTheme.divider, sheetShape),
    ) {
        // Hairline under the strip; the active tab paints over (cuts) it.
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(1.dp)
                .background(TerminalTheme.divider),
        )
        Row(modifier = Modifier.fillMaxSize().padding(start = 2.dp)) {
            LazyRow(
                modifier = Modifier.weight(1f),
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.Top,
                contentPadding = PaddingValues(bottom = 0.dp),
            ) {
                items(tabs, key = { it.defId }) { tab ->
                    val def = defsById[tab.defId]
                    CompanionTab(
                        def = def,
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
    def: CompanionDef?,
    label: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
) {
    val height by animateDpAsState(
        targetValue = if (isSelected) 34.dp else 26.dp,
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

    // M7.2 companion polish — resolve the companion's bundled icon.
    // Bitmap is decoded once and cached; null = no logo (custom companion).
    val assets = LocalContext.current.assets
    val isLight = TerminalTheme.isLight
    val logoBitmap = remember(def?.id, isLight) {
        def?.let { CompanionTabIcons.get(it, assets, isLight) }
    }

    Box(
        modifier = Modifier
            .height(height)
            // M7.2: wider min/max for logo + breathing room.
            .widthIn(min = 72.dp, max = 152.dp)
            .clip(shape)
            .background(container)
            .clickable { onSelect() }
            // M7.2: 10dp horizontal padding for |  [Logo] Label  | spacing.
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 2.dp),
        ) {
            // Logo (15dp) — shown when a bundled icon is available.
            if (logoBitmap != null) {
                Image(
                    bitmap = logoBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
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
            // The structured bottom edge: one 2dp Sapphire hairline —
            // the mirror of the terminal tab's top edge. Subtle and clean.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 6.dp)
                    .fillMaxWidth()
                    .height(2.dp)
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

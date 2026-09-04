package app.pocketshell.companion

/**
 * Phase 4 — tab reducer (docs/PHASE-4-COMPANION-DESIGN.md §6 + §18).
 *
 * Pure functions over the ordered tab list, unit-pinned; the ViewModel and
 * the pool both call these so the tab semantics live in exactly one place.
 *
 * Tab semantics (contract §6):
 *  - a tab IS an instance of a definition; "+" on an already-open
 *    definition FOCUSES it instead of duplicating;
 *  - closing the active tab selects the nearest surviving neighbor
 *    (left, else right, else none);
 *  - deleting a definition removes every tab that references it and
 *    repairs the active selection the same way.
 */
object CompanionTabs {

    /** Open a definition: focus an existing tab or append a new one. */
    fun opened(tabs: List<TabRecord>, defId: String): List<TabRecord> {
        if (tabs.any { it.defId == defId }) return tabs
        return tabs + TabRecord(defId = defId)
    }

    /** The tab index to select after opening [defId] (existing or appended). */
    fun indexAfterOpen(tabs: List<TabRecord>, defId: String): Int =
        tabs.indexOfFirst { it.defId == defId }.takeIf { it >= 0 } ?: tabs.size

    /** Close a tab; returns the surviving list and the next active defId. */
    fun closed(
        tabs: List<TabRecord>,
        defId: String,
        activeDefId: String?,
    ): Pair<List<TabRecord>, String?> {
        val index = tabs.indexOfFirst { it.defId == defId }
        if (index < 0) return tabs to activeDefId
        val surviving = tabs.filterIndexed { i, _ -> i != index }
        val nextDef = when {
            surviving.isEmpty() -> null
            defId != activeDefId -> activeDefId?.takeIf { id -> surviving.any { it.defId == id } }
            else -> (surviving.getOrNull(index - 1) ?: surviving.getOrNull(index))?.defId
        }
        return surviving to nextDef
    }

    /** Remove every tab of a deleted definition; repair the active selection. */
    fun defsRemoved(
        tabs: List<TabRecord>,
        removedDefId: String,
        activeDefId: String?,
    ): Pair<List<TabRecord>, String?> =
        closed(tabs, removedDefId, activeDefId)

    /** The active tab id, honest about a stale/absent selection. */
    fun resolvedActive(tabs: List<TabRecord>, activeDefId: String?): String? =
        activeDefId?.takeIf { id -> tabs.any { it.defId == id } }
            ?: tabs.firstOrNull()?.defId
}

/**
 * Quick-add templates (contract §4/§4.2): Name+URL pre-fill pairs only —
 * the dialog is editable and nothing anywhere special-cases them at
 * runtime. Pure data, deliberately generic.
 */
object CompanionTemplates {
    data class Template(val name: String, val host: String)

    val ALL = listOf(
        Template("ChatGPT", "chatgpt.com"),
        Template("Claude", "claude.ai"),
        Template("Gemini", "gemini.google.com"),
        Template("DeepSeek", "chat.deepseek.com"),
        Template("GitHub", "github.com"),
    )
}

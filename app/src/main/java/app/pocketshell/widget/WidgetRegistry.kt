package app.pocketshell.widget

/**
 * M8 — the ONE registry of Home widgets. Built-in widgets are compiled in;
 * adding widget #N is one object + one line here (docs/M8-WIDGET-SYSTEM.md
 * §2). Optional external widgets (a future catalog install) join through
 * the same [byId] seam as declarative, data-only renderings — the registry
 * never grows by Home's core learning about individual widgets.
 */
object WidgetRegistry {

    const val TERMINAL_ID = "core.terminal"
    const val LINUX_ID = "core.linux"

    /** Home's historical layout: Terminal (wide) + Linux, bit-for-bit. */
    val DEFAULT_SLOTS: List<String> = listOf(TERMINAL_ID, LINUX_ID)

    val builtIns: List<HomeWidget> = listOf(
        TerminalWidget,
        LinuxWidget,
        ServersWidget,
        StorageWidget,
        AgentsWidget,
    )

    /** The Control Center's picker listing (registry order = display order). */
    val specs: List<WidgetSpec> get() = builtIns.map { it.spec }

    fun byId(id: String): HomeWidget? = builtIns.firstOrNull { it.spec.id == id }

    /**
     * Slot ids → slot entries. Unknown ids render the honest Missing card
     * (a catalog widget that was removed, a renamed id); they NEVER crash
     * Home and NEVER silently substitute another widget.
     */
    fun resolve(slotIds: List<String>): List<WidgetSlotEntry> = slotIds.map { id ->
        byId(id)?.let { WidgetSlotEntry.Resolved(it) } ?: WidgetSlotEntry.Missing(id)
    }
}

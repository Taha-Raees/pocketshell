package app.pocketshell.widget

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * M8 — the process-scoped owner of the Home widget slots (root-scoped in
 * MainActivity like every other process-scoped state holder, so rotation
 * and Home↔Settings round-trips never reset the user's assignment).
 *
 * The seam is assignment-only: the ViewModel writes slot ids through the
 * ONE repository and exposes the flow; it knows nothing about rendering,
 * probes, or any individual widget.
 */
class HomeWidgetsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HomeWidgetSlotsRepository(application)

    val slots: StateFlow<List<String>> = repository.slots
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WidgetRegistry.DEFAULT_SLOTS)

    /** Assign a widget to a slot (index 0/1; out-of-range is a no-op). */
    fun assignSlot(slotIndex: Int, widgetId: String) {
        if (slotIndex !in 0..1) return
        if (WidgetRegistry.byId(widgetId) == null) return
        viewModelScope.launch {
            val current = slots.value.toMutableList()
            while (current.size < 2) current.add(WidgetRegistry.DEFAULT_SLOTS[current.size])
            current[slotIndex] = widgetId
            repository.setSlots(WidgetSlotsCodec.fillToTwo(current))
        }
    }

    fun restoreDefaults() {
        viewModelScope.launch { repository.restoreDefaults() }
    }
}

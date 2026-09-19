package app.pocketshell.widget

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * M8.3 — the process-scoped owner of the Home Application carousel's
 * configured order (root-scoped in MainActivity like every other
 * process-scoped state holder). Assignment-only: add / remove / reorder /
 * restore through the ONE repository; it knows nothing about rendering or
 * any individual application.
 */
class HomeApplicationViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HomeApplicationRepository(application)

    val homeAppIds: StateFlow<List<String>> = repository.homeAppIds
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            listOf(HomeApplications.DEFAULT_ID),
        )

    /** Add an application to the end of the carousel (registry members only). */
    fun add(id: String) {
        if (HomeApplications.byId(id) == null) return
        viewModelScope.launch {
            val current = homeAppIds.value
            if (!current.contains(id) && current.size < HomeAppIdCodec.MAX_APPS) {
                repository.setHomeAppIds(current + id)
            }
        }
    }

    /** Remove an application from the carousel (an empty carousel is a
     *  valid, honestly-rendered state — the host says so). */
    fun remove(id: String) {
        viewModelScope.launch {
            repository.setHomeAppIds(homeAppIds.value - id)
        }
    }

    /** Move an application one position up (towards the front). */
    fun moveUp(id: String) = move(id, -1)

    /** Move an application one position down (towards the end). */
    fun moveDown(id: String) = move(id, +1)

    private fun move(id: String, delta: Int) {
        viewModelScope.launch {
            val current = homeAppIds.value.toMutableList()
            val index = current.indexOf(id)
            if (index < 0) return@launch
            val target = index + delta
            if (target !in current.indices) return@launch
            current.removeAt(index)
            current.add(target, id)
            repository.setHomeAppIds(current)
        }
    }

    fun restoreDefault() {
        viewModelScope.launch { repository.restoreDefault() }
    }

    // ------------------------------------------------------ M8.4.2 state

    /**
     * The process-scoped store the applications keep their holders in —
     * cached snapshots, probe instances (with their idle-gate memory) and
     * transient UI state survive navigation and page disposal because THIS
     * ViewModel outlives Home's composition.
     */
    val stateStore = HomeAppStateStore()

    /** The last application the user looked at (restores the carousel page). */
    val selectedAppId: StateFlow<String?> = repository.selectedAppId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun select(id: String) {
        if (HomeApplications.byId(id) == null) return
        // The settled-page flow re-emits the current page on every Home
        // entry — skip the DataStore write when nothing actually changed.
        if (selectedAppId.value == id) return
        viewModelScope.launch { repository.setSelectedAppId(id) }
    }
}

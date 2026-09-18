package app.pocketshell.widget

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * M8.2 — the process-scoped owner of the Home Application Card's
 * application choice (root-scoped in MainActivity like every other
 * process-scoped state holder). Assignment-only: it writes the id through
 * the ONE repository and exposes the flow; it knows nothing about
 * rendering or any individual application.
 */
class HomeApplicationViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HomeApplicationRepository(application)

    val homeAppId: StateFlow<String> = repository.homeAppId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeApplications.DEFAULT_ID)

    /** Select the Home application (unknown ids are ignored here; the
     *  host renders unknown persisted ids as the honest Missing card). */
    fun select(id: String) {
        if (HomeApplications.byId(id) == null) return
        viewModelScope.launch { repository.setHomeAppId(id) }
    }

    fun restoreDefault() {
        viewModelScope.launch { repository.restoreDefault() }
    }
}

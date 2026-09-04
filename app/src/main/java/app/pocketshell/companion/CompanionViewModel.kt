package app.pocketshell.companion

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Phase 4 — Companion state holder (docs/PHASE-4-COMPANION-DESIGN.md §3/§5).
 *
 * Created ONCE in PocketShellRoot (like the terminal + settings ViewModels)
 * so Companion state survives screen switches; the Activity is singleTask
 * with full configChanges handling, so the in-process WebView pool survives
 * rotation as well.
 *
 * Every mutation funnels through the [CompanionTabs] reducer and the
 * repository — the ViewModel holds no tab logic of its own.
 */
class CompanionViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = CompanionRepository(application)

    val defs: StateFlow<List<CompanionDef>> = repo.defs.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList(),
    )
    val defaultId: StateFlow<String?> = repo.defaultId.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), null,
    )
    val tabs: StateFlow<List<TabRecord>> = repo.tabs.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList(),
    )
    val panelHeight: StateFlow<Float> = repo.panelHeight.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), 0f,
    )

    /** Active tab (defId), stale selections repaired against the tab list. */
    val activeTabId: StateFlow<String?> =
        combine(repo.tabs, repo.activeTabId) { tabs, active ->
            CompanionTabs.resolvedActive(tabs, active)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Runtime page titles (defId → last title); never persisted. */
    val pageTitles = MutableStateFlow<Map<String, String>>(emptyMap())

    // ---- definitions -------------------------------------------------------

    fun addCompanion(name: String, url: String, onResult: (Boolean) -> Unit = {}) {
        val validName = CompanionValidation.validateName(name)
        val validUrl = CompanionValidation.normalizeUrl(url)
        if (validName == null || validUrl == null) {
            onResult(false)
            return
        }
        viewModelScope.launch {
            val updated = repo.defs.first() + CompanionDef(
                id = UUID.randomUUID().toString(),
                name = validName,
                url = validUrl,
            )
            repo.setDefs(updated)
            // The first Companion ever added becomes the default (sensible
            // zero-config behavior; changeable in Settings at any time).
            if (updated.size == 1) repo.setDefaultId(updated.first().id)
            onResult(true)
        }
    }

    fun updateCompanion(id: String, name: String, url: String, onResult: (Boolean) -> Unit = {}) {
        val validName = CompanionValidation.validateName(name)
        val validUrl = CompanionValidation.normalizeUrl(url)
        if (validName == null || validUrl == null) {
            onResult(false)
            return
        }
        viewModelScope.launch {
            val current = repo.defs.first()
            if (current.none { it.id == id }) {
                onResult(false)
                return@launch
            }
            repo.setDefs(current.map { def ->
                if (def.id == id) def.copy(name = validName, url = validUrl) else def
            })
            // A URL edit re-anchors the tab: the live page is dropped and the
            // next open loads the new address. The login session survives —
            // it lives in cookies, not in the tab.
            CompanionWebPool.forgetDefinition(id)
            onResult(true)
        }
    }

    fun deleteCompanion(id: String) {
        viewModelScope.launch {
            val defs = repo.defs.first()
            val tabs = repo.tabs.first()
            val active = repo.activeTabId.first()
            val remainingDefs = defs.filterNot { it.id == id }
            repo.setDefs(remainingDefs)
            val (survivingTabs, nextActive) = CompanionTabs.defsRemoved(tabs, id, active)
            repo.setTabs(survivingTabs)
            repo.setActiveTabId(nextActive)
            if (repo.defaultId.first() == id) repo.setDefaultId(remainingDefs.firstOrNull()?.id)
            // Destroy any live WebView of the removed definition + its tabs.
            CompanionWebPool.forgetDefinition(id)
        }
    }

    fun setDefault(id: String) {
        viewModelScope.launch { repo.setDefaultId(id) }
    }

    // ---- tabs --------------------------------------------------------------

    /** Raise a Companion into the workspace: open (or focus) its tab + height. */
    fun openCompanion(defId: String) {
        viewModelScope.launch {
            val tabs = repo.tabs.first()
            repo.setTabs(CompanionTabs.opened(tabs, defId))
            repo.setActiveTabId(defId)
            if (!CompanionHeights.isRaised(repo.panelHeight.first())) {
                repo.setPanelHeight(CompanionHeights.HALF)
            }
        }
    }

    /** The "+" affordance: opens the default Companion (or the first one). */
    fun openDefault() {
        viewModelScope.launch {
            val defs = repo.defs.first()
            val target = repo.defaultId.first()
                ?.let { id -> defs.firstOrNull { it.id == id } }
                ?: defs.firstOrNull()
                ?: return@launch
            openCompanion(target.id)
        }
    }

    fun selectTab(defId: String) {
        viewModelScope.launch { repo.setActiveTabId(defId) }
    }

    fun closeTab(defId: String) {
        viewModelScope.launch {
            val (surviving, nextActive) = CompanionTabs.closed(
                repo.tabs.first(), defId, repo.activeTabId.first(),
            )
            repo.setTabs(surviving)
            repo.setActiveTabId(nextActive)
            CompanionWebPool.forgetTab(defId)
        }
    }

    /** Update a tab's cold-restore anchor (called by the pool's listener). */
    fun recordLastUrl(defId: String, url: String?) {
        viewModelScope.launch {
            val tabs = repo.tabs.first()
            val index = tabs.indexOfFirst { it.defId == defId }
            if (index >= 0) {
                repo.setTabs(tabs.mapIndexed { i, tab ->
                    if (i == index) tab.copy(lastUrl = url) else tab
                })
            }
        }
    }

    fun collapse() {
        viewModelScope.launch { repo.setPanelHeight(0f) }
    }

    /**
     * Settings → Companions → Clear web data (contract §7): cookies, DOM
     * storage and form data are wiped; live WebViews are destroyed so the
     * next open starts truly clean. Definitions/tabs are untouched.
     */
    fun clearWebData() {
        viewModelScope.launch {
            CompanionWebPool.clearAll()
            // m4.0.1: all three calls load provider components — a broken
            // WebView package must not crash the Settings screen either.
            runCatching {
                android.webkit.CookieManager.getInstance().apply {
                    removeAllCookies(null)
                    flush()
                }
                android.webkit.WebStorage.getInstance().deleteAllData()
                android.webkit.WebViewDatabase.getInstance(getApplication()).clearFormData()
            }
        }
    }

    /** Persist a settled drag height (contract §9: stay where released). */
    fun settleHeight(fraction: Float) {
        viewModelScope.launch { repo.setPanelHeight(CompanionHeights.settled(fraction)) }
    }
}

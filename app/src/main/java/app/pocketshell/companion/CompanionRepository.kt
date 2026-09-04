package app.pocketshell.companion

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.companionDataStore by preferencesDataStore(name = "companion")

/**
 * Phase 4 — Companion persistence (docs/PHASE-4-COMPANION-DESIGN.md §5).
 *
 * One DataStore file ("companion", the SettingsRepository pattern) holding
 * exactly the keys the contract lists: definitions, default, open tabs,
 * active tab, panel height. Tab page bundles are deliberately NOT here —
 * they are in-process only (the platform's 1 MB savedInstanceState cap and
 * cross-process Bundle portability both forbid persisting them).
 */
class CompanionRepository(private val context: Context) {

    private val defsKey = stringPreferencesKey("defs")
    private val defaultIdKey = stringPreferencesKey("default_id")
    private val tabsKey = stringPreferencesKey("tabs")
    private val activeTabKey = stringPreferencesKey("active_tab")
    private val panelHeightKey = floatPreferencesKey("panel_height")

    val defs: Flow<List<CompanionDef>> = context.companionDataStore.data.map { prefs ->
        prefs[defsKey]?.let { encoded ->
            try {
                CompanionJson.decodeFromString<List<CompanionDef>>(encoded)
            } catch (_: Exception) {
                emptyList()
            }
        } ?: emptyList()
    }

    val defaultId: Flow<String?> = context.companionDataStore.data.map { prefs ->
        prefs[defaultIdKey]?.takeIf { it.isNotEmpty() }
    }

    val tabs: Flow<List<TabRecord>> = context.companionDataStore.data.map { prefs ->
        prefs[tabsKey]?.let { encoded ->
            try {
                CompanionJson.decodeFromString<List<TabRecord>>(encoded)
            } catch (_: Exception) {
                emptyList()
            }
        } ?: emptyList()
    }

    val activeTabId: Flow<String?> = context.companionDataStore.data.map { prefs ->
        prefs[activeTabKey]?.takeIf { it.isNotEmpty() }
    }

    /** Last settled panel height fraction; 0 = collapsed. */
    val panelHeight: Flow<Float> = context.companionDataStore.data.map { prefs ->
        CompanionHeights.clamp(prefs[panelHeightKey] ?: 0f)
    }

    suspend fun setDefs(defs: List<CompanionDef>) {
        context.companionDataStore.edit { it[defsKey] = CompanionJson.encodeToString(defs) }
    }

    suspend fun setDefaultId(id: String?) {
        context.companionDataStore.edit { it[defaultIdKey] = id ?: "" }
    }

    suspend fun setTabs(tabs: List<TabRecord>) {
        context.companionDataStore.edit { it[tabsKey] = CompanionJson.encodeToString(tabs) }
    }

    suspend fun setActiveTabId(id: String?) {
        context.companionDataStore.edit { it[activeTabKey] = id ?: "" }
    }

    suspend fun setPanelHeight(fraction: Float) {
        context.companionDataStore.edit { it[panelHeightKey] = CompanionHeights.clamp(fraction) }
    }
}

package app.pocketshell.cliapps

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.cliAppDataStore by preferencesDataStore(name = "cliapps")

/**
 * Persistent registry of user-installed CLI apps.
 *
 * Empty by default (brief §3). Entries are added only when a real installation
 * has occurred (M2 installer) — the M1 UI therefore always shows the honest
 * empty state on a fresh install.
 */
class CliAppRegistry(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private val key = stringPreferencesKey("apps_json")

    val apps: Flow<List<CliApp>> = context.cliAppDataStore.data.map { prefs ->
        prefs[key]?.let { raw ->
            runCatching { json.decodeFromString<List<CliApp>>(raw) }.getOrDefault(emptyList())
        } ?: emptyList()
    }

    suspend fun add(app: CliApp) {
        context.cliAppDataStore.edit { prefs ->
            val current = prefs[key]?.let {
                runCatching { json.decodeFromString<List<CliApp>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            val updated = current.filterNot { it.id == app.id } + app
            prefs[key] = json.encodeToString(updated.sortedBy { it.name })
        }
    }

    suspend fun remove(id: String) {
        context.cliAppDataStore.edit { prefs ->
            val current = prefs[key]?.let {
                runCatching { json.decodeFromString<List<CliApp>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            prefs[key] = json.encodeToString(current.filterNot { it.id == id })
        }
    }
}

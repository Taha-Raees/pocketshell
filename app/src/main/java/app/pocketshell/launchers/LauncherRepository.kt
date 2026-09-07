package app.pocketshell.launchers

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val Context.launcherDataStore by preferencesDataStore(name = "launchers")

/** Launcher-domain JSON (same config as the companion collection's). */
private val LauncherJson: Json = Json { ignoreUnknownKeys = true }

/**
 * M7.1 Phase 1 — launcher persistence (PART G).
 *
 * ONE DataStore file ("launchers"), the established per-domain pattern
 * (settings, companion) — NOT a second database and not a new storage
 * engine. It holds exactly the launcher state:
 *
 *   hidden_launcher_ids  launchers removed from Home (hide-only, restorable)
 *   custom_tools         the user's custom CLI launchers (JSON)
 *   companion_icons      defId → stored icon filename (side-car map: the
 *                        frozen CompanionDef schema stays untouched)
 *   tool_icons           custom tool id → stored icon filename
 *   builtins_seeded      the one-shot built-in companion seed flag
 *
 * Launcher ids are opaque strings across all keys: registry ids (`kilo`),
 * seeded companion ids (`builtin-chatgpt`), user-created companion ids
 * (UUID) and custom tool ids (`tool-…`).
 */
class LauncherRepository(private val context: Context) {

    private val hiddenKey = stringSetPreferencesKey("hidden_launcher_ids")
    private val customToolsKey = stringPreferencesKey("custom_tools")
    private val companionIconsKey = stringPreferencesKey("companion_icons")
    private val toolIconsKey = stringPreferencesKey("tool_icons")
    private val seededKey = booleanPreferencesKey("builtins_seeded")

    val hiddenIds: Flow<Set<String>> = context.launcherDataStore.data.map { prefs ->
        prefs[hiddenKey] ?: emptySet()
    }

    val customTools: Flow<List<CustomTool>> = context.launcherDataStore.data.map { prefs ->
        prefs[customToolsKey]?.let { encoded ->
            try {
                LauncherJson.decodeFromString(ListSerializer(CustomTool.serializer()), encoded)
            } catch (_: Exception) {
                emptyList()
            }
        } ?: emptyList()
    }

    val companionIcons: Flow<Map<String, String>> = context.launcherDataStore.data.map { prefs ->
        prefs[companionIconsKey]?.let { decodeIcons(it) } ?: emptyMap()
    }

    val toolIcons: Flow<Map<String, String>> = context.launcherDataStore.data.map { prefs ->
        prefs[toolIconsKey]?.let { decodeIcons(it) } ?: emptyMap()
    }

    val builtinsSeeded: Flow<Boolean> = context.launcherDataStore.data.map { prefs ->
        prefs[seededKey] == true
    }

    suspend fun setHiddenIds(ids: Set<String>) {
        context.launcherDataStore.edit { it[hiddenKey] = ids }
    }

    suspend fun setCustomTools(tools: List<CustomTool>) {
        context.launcherDataStore.edit {
            it[customToolsKey] = LauncherJson.encodeToString(
                ListSerializer(CustomTool.serializer()), tools,
            )
        }
    }

    suspend fun setCompanionIcons(icons: Map<String, String>) {
        context.launcherDataStore.edit { it[companionIconsKey] = encodeIcons(icons) }
    }

    suspend fun setToolIcons(icons: Map<String, String>) {
        context.launcherDataStore.edit { it[toolIconsKey] = encodeIcons(icons) }
    }

    suspend fun setBuiltinsSeeded(seeded: Boolean) {
        context.launcherDataStore.edit { it[seededKey] = seeded }
    }

    private fun encodeIcons(icons: Map<String, String>): String =
        LauncherJson.encodeToString(MapSerializer(String.serializer(), String.serializer()), icons)

    private fun decodeIcons(encoded: String): Map<String, String>? = try {
        LauncherJson.decodeFromString(
            MapSerializer(String.serializer(), String.serializer()), encoded,
        )
    } catch (_: Exception) {
        null
    }
}

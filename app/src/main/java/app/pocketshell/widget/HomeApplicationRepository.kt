package app.pocketshell.widget

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val Context.homeWidgetsDataStore by preferencesDataStore(name = "home_widgets")

/**
 * M8.3 — Home-application persistence: the carousel hosts an ORDERED LIST
 * of applications (M8.2's single application is the list of one, migrated).
 *
 * Same "home_widgets" DataStore file, keys:
 *   home_app_ids      — the ordered list (JSON array of registry ids)
 *   home_app_selected — M8.4.2: the LAST USED application id, so returning
 *                       to Home reopens the carousel on the same page
 *                       (never silently back to the first application).
 *   home_app_id       — the M8.2 single-application record, kept ONLY as
 *                       the migration source: when the list key is absent
 *                       and the legacy key exists, the list is seeded
 *                       [legacy].
 *
 * Absent/corrupt/empty → the default application. Ids are shape-checked
 * here; an id that no longer resolves in the registry renders the honest
 * Missing card (stated, never substituted).
 */
class HomeApplicationRepository(private val context: Context) {

    private val appIdsKey = stringPreferencesKey("home_app_ids")
    private val legacyAppIdKey = stringPreferencesKey("home_app_id")
    private val selectedAppIdKey = stringPreferencesKey("home_app_selected")

    val homeAppIds: Flow<List<String>> = context.homeWidgetsDataStore.data.map { prefs ->
        HomeAppIdCodec.decodeList(
            raw = prefs[appIdsKey],
            legacySingle = prefs[legacyAppIdKey],
        )
    }

    /** The last application the user actually looked at (raw; validated at use). */
    val selectedAppId: Flow<String?> = context.homeWidgetsDataStore.data.map { prefs ->
        prefs[selectedAppIdKey]?.trim().takeUnless { it.isNullOrEmpty() }
    }

    suspend fun setHomeAppIds(ids: List<String>) {
        context.homeWidgetsDataStore.edit { it[appIdsKey] = HomeAppIdCodec.encode(ids) }
    }

    suspend fun setSelectedAppId(id: String) {
        context.homeWidgetsDataStore.edit { it[selectedAppIdKey] = id }
    }

    suspend fun restoreDefault() {
        setHomeAppIds(listOf(HomeApplications.DEFAULT_ID))
    }
}

/** List codec: JSON array of well-shaped ids, M8.2 migration, honest default. */
object HomeAppIdCodec {

    private val pattern = Regex("""[a-z][a-z0-9.-]{1,63}""")

    /** Sanity cap: an "arbitrary reasonable number" of Home applications. */
    const val MAX_APPS = 12

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(ids: List<String>): String =
        json.encodeToString(ListSerializer(String.serializer()), ids)

    fun decode(raw: String?): List<String> = decodeList(raw = raw, legacySingle = null)

    fun decodeList(raw: String?, legacySingle: String?): List<String> {
        val parsed = raw?.let {
            try {
                json.decodeFromString(ListSerializer(String.serializer()), it)
            } catch (_: Exception) {
                null
            }
        }
        val ids = (parsed ?: emptyList())
            .filter { pattern.matches(it.trim()) }
            .map { it.trim() }
            .distinct()
            .take(MAX_APPS)
        if (ids.isNotEmpty()) return ids
        // M8.2 migration: a single-application record seeds the list.
        val legacy = legacySingle?.trim().orEmpty()
        if (pattern.matches(legacy)) return listOf(legacy)
        return listOf(HomeApplications.DEFAULT_ID)
    }
}

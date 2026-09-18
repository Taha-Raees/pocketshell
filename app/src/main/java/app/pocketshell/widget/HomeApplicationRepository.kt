package app.pocketshell.widget

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.homeWidgetsDataStore by preferencesDataStore(name = "home_widgets")

/**
 * M8.2 — Home-application persistence, in the established per-domain
 * pattern (the SAME "home_widgets" DataStore file the two-slot era used;
 * the M8 slot record key is obsolete and simply no longer read).
 *
 * ONE key: "home_app_id" — the id of the application hosted by the Home
 * Application Card. Absent/corrupt → the default application. There is
 * deliberately NO migration machinery: the old value described two hero
 * slots, a concept that no longer exists; the new record starts fresh at
 * the default.
 */
class HomeApplicationRepository(private val context: Context) {

    private val appIdKey = stringPreferencesKey("home_app_id")

    val homeAppId: Flow<String> = context.homeWidgetsDataStore.data.map { prefs ->
        HomeAppIdCodec.decode(prefs[appIdKey])
    }

    suspend fun setHomeAppId(id: String) {
        context.homeWidgetsDataStore.edit { it[appIdKey] = HomeAppIdCodec.encode(id) }
    }

    suspend fun restoreDefault() {
        setHomeAppId(HomeApplications.DEFAULT_ID)
    }
}

/** Id codec: shape-checked, absent/corrupt → the default application. */
object HomeAppIdCodec {

    private val pattern = Regex("""[a-z][a-z0-9.-]{1,63}""")

    fun encode(id: String): String = id

    fun decode(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        return if (pattern.matches(trimmed)) trimmed else HomeApplications.DEFAULT_ID
    }
}

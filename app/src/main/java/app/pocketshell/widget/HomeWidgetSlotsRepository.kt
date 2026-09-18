package app.pocketshell.widget

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.homeWidgetsDataStore by preferencesDataStore(name = "home_widgets")

/**
 * M8 — slot persistence, in the established per-domain pattern (ONE
 * Preferences DataStore file, JSON codec, absent/corrupt → honest default;
 * the LauncherRepository discipline). It holds exactly the two Home slot
 * assignments — no widget state, no widget payloads, nothing else.
 *
 * There is deliberately NO versioned migration: the only record is a list
 * of two well-formed widget ids; a future format change degrades through
 * [WidgetSlotsCodec.decode] to the defaults rather than guess.
 */
class HomeWidgetSlotsRepository(private val context: Context) {

    private val slotsKey = stringPreferencesKey("slot_widget_ids")

    val slots: Flow<List<String>> = context.homeWidgetsDataStore.data.map { prefs ->
        WidgetSlotsCodec.decode(prefs[slotsKey])
    }

    suspend fun setSlots(ids: List<String>) {
        context.homeWidgetsDataStore.edit { it[slotsKey] = WidgetSlotsCodec.encode(ids) }
    }

    suspend fun restoreDefaults() {
        setSlots(WidgetRegistry.DEFAULT_SLOTS)
    }
}

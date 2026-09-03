package app.pocketshell.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** Restrained theme options (brief §25): light / dark / AMOLED / dynamic / system. */
enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }

/** Persisted user settings. Defaults are honest and minimal. */
class SettingsRepository(private val context: Context) {

    private val themeKey = stringPreferencesKey("theme_mode")
    private val dynamicKey = stringPreferencesKey("dynamic_color")
    private val fontSizeKey = intPreferencesKey("default_font_size")
    private val openRouterKey = stringPreferencesKey("openrouter_api_key")
    private val openRouterModelKey = stringPreferencesKey("openrouter_model")

    val themeMode: Flow<ThemeMode> = context.settingsDataStore.data.map { prefs ->
        when (prefs[themeKey]) {
            "LIGHT" -> ThemeMode.LIGHT
            "DARK" -> ThemeMode.DARK
            "AMOLED" -> ThemeMode.AMOLED
            else -> ThemeMode.SYSTEM
        }
    }

    val dynamicColor: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[dynamicKey] == "true"
    }

    /** Default terminal font size; the terminal itself may change it via pinch. */
    val defaultFontSize: Flow<Int> = context.settingsDataStore.data.map { prefs ->
        prefs[fontSizeKey] ?: DEFAULT_FONT_SIZE
    }

    /**
     * OpenRouter API key for the future built-in assistant (docs/UI-REDESIGN.md
     * §10). Stored in the app's PRIVATE DataStore — never logged, never in
     * Diagnostics, never in crash surfaces. The UI masks it after entry and
     * the settings screen states honestly that OS-keystore-backed storage is
     * a planned upgrade.
     */
    val openRouterApiKey: Flow<String?> = context.settingsDataStore.data.map { prefs ->
        prefs[openRouterKey]?.takeIf { it.isNotBlank() }
    }

    /** OpenRouter model id — free text, no fixed list (owner requirement). */
    val openRouterModel: Flow<String?> = context.settingsDataStore.data.map { prefs ->
        prefs[openRouterModelKey]?.takeIf { it.isNotBlank() }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[themeKey] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.settingsDataStore.edit { it[dynamicKey] = if (enabled) "true" else "false" }
    }

    suspend fun setDefaultFontSize(size: Int) {
        context.settingsDataStore.edit { it[fontSizeKey] = size }
    }

    suspend fun setOpenRouterApiKey(key: String?) {
        context.settingsDataStore.edit {
            if (key.isNullOrBlank()) it.remove(openRouterKey) else it[openRouterKey] = key
        }
    }

    suspend fun setOpenRouterModel(model: String?) {
        context.settingsDataStore.edit {
            if (model.isNullOrBlank()) it.remove(openRouterModelKey) else it[openRouterModelKey] = model
        }
    }

    companion object {
        const val DEFAULT_FONT_SIZE = 28
        const val MIN_FONT_SIZE = 12
        const val MAX_FONT_SIZE = 40

        /** UI mask helper: show only the last 4 characters of a stored key. */
        fun maskKey(key: String?): String? = key?.let {
            if (it.length <= 4) "••••" else "••••••••${it.takeLast(4)}"
        }
    }
}

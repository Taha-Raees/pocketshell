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

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[themeKey] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.settingsDataStore.edit { it[dynamicKey] = if (enabled) "true" else "false" }
    }

    suspend fun setDefaultFontSize(size: Int) {
        context.settingsDataStore.edit { it[fontSizeKey] = size }
    }

    companion object {
        const val DEFAULT_FONT_SIZE = 28
        const val MIN_FONT_SIZE = 12
        const val MAX_FONT_SIZE = 40
    }
}

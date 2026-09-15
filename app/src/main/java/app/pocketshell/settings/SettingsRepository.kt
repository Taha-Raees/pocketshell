package app.pocketshell.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/**
 * Persisted user settings. Defaults are honest and minimal.
 *
 * Control Center appearance model (Settings/Control Center task §2/§10):
 * THEME ([AppTheme], key `app_theme`) and MODE ([ThemeMode], the historical
 * `theme_mode` key) are SEPARATE settings — every theme ships Light + Dark;
 * a persisted "AMOLED" degrades to DARK via [parseThemeMode]. Text/icon/card
 * scale and the icons-per-row preference are the appearance density knobs,
 * all in THIS one DataStore — no duplicate sources of truth.
 */
class SettingsRepository(private val context: Context) {

    private val themeKey = stringPreferencesKey("theme_mode")
    private val themeIdentityKey = stringPreferencesKey("app_theme")
    private val dynamicKey = stringPreferencesKey("dynamic_color")
    private val fontSizeKey = intPreferencesKey("default_font_size")
    private val textScaleKey = stringPreferencesKey("text_scale")
    private val iconSizeKey = stringPreferencesKey("icon_size")
    private val cardSizeKey = stringPreferencesKey("card_size")
    private val iconColumnsKey = stringPreferencesKey("icon_columns")

    // The recently-browsed folder (Home "Recent" row). Raw strings on
    // purpose: the typed model + revalidation live in the files domain
    // (files/RecentFolder.kt) — this store never interprets a path.
    private val recentFolderKindKey = stringPreferencesKey("recent_folder_area_kind")
    private val recentFolderAreaKeyKey = stringPreferencesKey("recent_folder_area_key")
    private val recentFolderPathKey = stringPreferencesKey("recent_folder_path")

    // M7.1.1 — the persistent "On-screen keyboard" On/Off preference: the
    // user's baseline for the shared deck. External-keyboard detection is a
    // temporary runtime override and NEVER writes this key (spec: the
    // setting represents the user's preference; the old M7.1
    // auto_hide_keyboard_on_external opt-out is retired with its
    // overlay-policy — the new model's override is unconditional while the
    // preference is On, and this Off is honored on every disconnect).
    private val onscreenKeyboardKey = stringPreferencesKey("onscreen_keyboard_enabled")

    /** The visual identity (PocketShell, Nord, Aurora, …). */
    val theme: Flow<AppTheme> = context.settingsDataStore.data.map { prefs ->
        parseAppTheme(prefs[themeIdentityKey])
    }

    /** Light / Dark / System — the VARIANT selector; "AMOLED" reads as DARK. */
    val themeMode: Flow<ThemeMode> = context.settingsDataStore.data.map { prefs ->
        parseThemeMode(prefs[themeKey])
    }

    val dynamicColor: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[dynamicKey] == "true"
    }

    /** The persistent On-screen keyboard preference; default ON. */
    val onscreenKeyboardEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[onscreenKeyboardKey] != "false"
    }

    /** Default terminal font size; the terminal itself may change it via pinch. */
    val defaultFontSize: Flow<Int> = context.settingsDataStore.data.map { prefs ->
        prefs[fontSizeKey] ?: DEFAULT_FONT_SIZE
    }

    /** App UI text scale (multiplier on the system font scale). */
    val textScale: Flow<TextScale> = context.settingsDataStore.data.map { prefs ->
        parseTextScale(prefs[textScaleKey])
    }

    /** Launcher icon tile size. */
    val iconSize: Flow<IconSize> = context.settingsDataStore.data.map { prefs ->
        parseIconSize(prefs[iconSizeKey])
    }

    /** Card weight (environment/hero surfaces). */
    val cardSize: Flow<CardSize> = context.settingsDataStore.data.map { prefs ->
        parseCardSize(prefs[cardSizeKey])
    }

    /** Icons-per-row preference (AUTO follows the screen width). */
    val iconColumns: Flow<IconColumns> = context.settingsDataStore.data.map { prefs ->
        parseIconColumns(prefs[iconColumnsKey])
    }

    /** The recently-browsed folder's raw record, or all-null when absent. */
    val recentFolderRecord: Flow<Triple<String?, String?, String?>> =
        context.settingsDataStore.data.map { prefs ->
            Triple(
                prefs[recentFolderKindKey],
                prefs[recentFolderAreaKeyKey],
                prefs[recentFolderPathKey],
            )
        }

    /** Writes (or, with nulls, clears) the recent-folder record. */
    suspend fun setRecentFolderRecord(kind: String?, areaKey: String?, path: String?) {
        context.settingsDataStore.edit { prefs ->
            if (kind == null || path == null) {
                prefs.remove(recentFolderKindKey)
                prefs.remove(recentFolderAreaKeyKey)
                prefs.remove(recentFolderPathKey)
            } else {
                prefs[recentFolderKindKey] = kind
                prefs[recentFolderAreaKeyKey] = areaKey ?: "default"
                prefs[recentFolderPathKey] = path
            }
        }
    }

    suspend fun setTheme(theme: AppTheme) {
        context.settingsDataStore.edit { it[themeIdentityKey] = theme.name }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[themeKey] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.settingsDataStore.edit { it[dynamicKey] = if (enabled) "true" else "false" }
    }

    suspend fun setOnscreenKeyboardEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[onscreenKeyboardKey] = if (enabled) "true" else "false" }
    }

    suspend fun setDefaultFontSize(size: Int) {
        context.settingsDataStore.edit { it[fontSizeKey] = size }
    }

    suspend fun setTextScale(scale: TextScale) {
        context.settingsDataStore.edit { it[textScaleKey] = scale.name }
    }

    suspend fun setIconSize(size: IconSize) {
        context.settingsDataStore.edit { it[iconSizeKey] = size.name }
    }

    suspend fun setCardSize(size: CardSize) {
        context.settingsDataStore.edit { it[cardSizeKey] = size.name }
    }

    suspend fun setIconColumns(columns: IconColumns) {
        context.settingsDataStore.edit { it[iconColumnsKey] = columns.name }
    }

    companion object {
        const val DEFAULT_FONT_SIZE = 28
        const val MIN_FONT_SIZE = 12
        const val MAX_FONT_SIZE = 40
    }
}

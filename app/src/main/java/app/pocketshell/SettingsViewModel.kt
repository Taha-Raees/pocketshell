package app.pocketshell

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.pocketshell.settings.AppTheme
import app.pocketshell.settings.CardSize
import app.pocketshell.settings.IconColumns
import app.pocketshell.settings.IconSize
import app.pocketshell.settings.SettingsRepository
import app.pocketshell.settings.ThemeMode
import app.pocketshell.settings.TextScale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Holds persisted user settings as lifecycle-aware state; applies via repository. */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = SettingsRepository(application)

    // Control Center II — the FRESH-INSTALL identity is Aurora × Dark.
    // Owner no-glimpse fix (2026-09-16): the first-frame seeds come from
    // PocketShellApp's synchronous startup read of the SAVED preference —
    // a saved theme renders from the first frame (no Aurora flash while
    // DataStore warms up); a fresh install reads the Aurora × Dark
    // defaults, so that contract is unchanged.
    val theme: StateFlow<AppTheme> = repo.theme.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), PocketShellApp.startupTheme,
    )
    val themeMode: StateFlow<ThemeMode> = repo.themeMode.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), PocketShellApp.startupThemeMode,
    )
    val dynamicColor: StateFlow<Boolean> = repo.dynamicColor.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), false,
    )
    val defaultFontSize: StateFlow<Int> = repo.defaultFontSize.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsRepository.DEFAULT_FONT_SIZE,
    )
    val textScale: StateFlow<TextScale> = repo.textScale.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), TextScale.DEFAULT,
    )
    val iconSize: StateFlow<IconSize> = repo.iconSize.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), IconSize.DEFAULT,
    )
    val cardSize: StateFlow<CardSize> = repo.cardSize.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), CardSize.DEFAULT,
    )
    val iconColumns: StateFlow<IconColumns> = repo.iconColumns.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), IconColumns.AUTO,
    )

    // M7.1.1 — the persistent "On-screen keyboard" preference. Default OFF
    // (owner 2026-09-16: the app opens with the deck hidden; canvas tap,
    // the ⌨ affordance and input focus open it on demand).
    val onscreenKeyboardEnabled: StateFlow<Boolean> = repo.onscreenKeyboardEnabled.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), false,
    )

    fun setTheme(theme: AppTheme) = viewModelScope.launch { repo.setTheme(theme) }
    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { repo.setThemeMode(mode) }
    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { repo.setDynamicColor(enabled) }
    fun setDefaultFontSize(size: Int) = viewModelScope.launch { repo.setDefaultFontSize(size) }
    fun setTextScale(scale: TextScale) = viewModelScope.launch { repo.setTextScale(scale) }
    fun setIconSize(size: IconSize) = viewModelScope.launch { repo.setIconSize(size) }
    fun setCardSize(size: CardSize) = viewModelScope.launch { repo.setCardSize(size) }
    fun setIconColumns(columns: IconColumns) = viewModelScope.launch { repo.setIconColumns(columns) }
    fun setOnscreenKeyboardEnabled(enabled: Boolean) =
        viewModelScope.launch { repo.setOnscreenKeyboardEnabled(enabled) }
}

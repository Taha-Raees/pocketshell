package app.pocketshell

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.pocketshell.settings.SettingsRepository
import app.pocketshell.settings.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Holds persisted user settings as lifecycle-aware state; applies via repository. */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = SettingsRepository(application)

    val themeMode: StateFlow<ThemeMode> = repo.themeMode.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM,
    )
    val dynamicColor: StateFlow<Boolean> = repo.dynamicColor.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), false,
    )
    val defaultFontSize: StateFlow<Int> = repo.defaultFontSize.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsRepository.DEFAULT_FONT_SIZE,
    )

    // M7.1.1 — the persistent "On-screen keyboard" On/Off preference; default ON.
    val onscreenKeyboardEnabled: StateFlow<Boolean> = repo.onscreenKeyboardEnabled.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), true,
    )

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { repo.setThemeMode(mode) }
    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { repo.setDynamicColor(enabled) }
    fun setDefaultFontSize(size: Int) = viewModelScope.launch { repo.setDefaultFontSize(size) }
    fun setOnscreenKeyboardEnabled(enabled: Boolean) =
        viewModelScope.launch { repo.setOnscreenKeyboardEnabled(enabled) }
}

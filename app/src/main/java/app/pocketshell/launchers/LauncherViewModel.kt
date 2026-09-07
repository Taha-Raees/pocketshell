package app.pocketshell.launchers

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.pocketshell.companion.CompanionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * M7.1 Phase 1 — launcher state holder (PART C/G/F).
 *
 * Created once in PocketShellRoot (the established pattern) so launcher
 * state survives screen switches. All persistence flows through
 * [LauncherRepository]; all companion-definition access goes through the
 * EXISTING [CompanionRepository] public API (the frozen companion package
 * is used, never modified).
 *
 * Hiding is the ONLY remove-from-Home semantics (PART C): the launcher —
 * built-in or custom — stays persisted and restorable; nothing is
 * uninstalled, deleted or cleaned by hiding.
 */
class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = LauncherRepository(application)
    private val companionRepo = CompanionRepository(application)
    private val iconStore = LauncherIconStore

    val hiddenIds: StateFlow<Set<String>> = repo.hiddenIds.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet(),
    )
    val customTools: StateFlow<List<CustomTool>> = repo.customTools.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList(),
    )
    val companionIcons: StateFlow<Map<String, String>> = repo.companionIcons.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap(),
    )
    val toolIcons: StateFlow<Map<String, String>> = repo.toolIcons.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap(),
    )

    /** One-shot built-in companion seeding result (true once done). */
    val builtinsSeeded = MutableStateFlow(false)

    // ------------------------------------------------------------ visibility

    /** Remove a launcher from Home — hide-only, restorable (PART C). */
    fun hideFromHome(id: String) {
        viewModelScope.launch { repo.setHiddenIds(repo.hiddenIds.first() + id) }
    }

    /** Put a hidden launcher back on Home. */
    fun restoreToHome(id: String) {
        viewModelScope.launch { repo.setHiddenIds(repo.hiddenIds.first() - id) }
    }

    // ---------------------------------------------------------- custom tools

    /**
     * Add a custom CLI launcher. Returns null on success or the ID of the
     * created tool; onResult(false-shape) is avoided — validation failures
     * return null so the caller can show its inline error.
     */
    suspend fun addCustomTool(name: String, command: String): CustomTool? {
        val validName = CustomToolValidation.validateName(name) ?: return null
        val validCommand = CustomToolValidation.validateCommand(command) ?: return null
        val tool = CustomTool(
            id = "tool-" + UUID.randomUUID().toString(),
            name = validName,
            command = validCommand,
        )
        repo.setCustomTools(repo.customTools.first() + tool)
        return tool
    }

    /** Replace a custom tool's name/command (icon mapping survives). */
    suspend fun updateCustomTool(id: String, name: String, command: String): Boolean {
        val validName = CustomToolValidation.validateName(name) ?: return false
        val validCommand = CustomToolValidation.validateCommand(command) ?: return false
        val current = repo.customTools.first()
        if (current.none { it.id == id }) return false
        repo.setCustomTools(current.map { tool ->
            if (tool.id == id) tool.copy(name = validName, command = validCommand) else tool
        })
        return true
    }

    /**
     * Delete a custom tool entirely (user config removal — distinct from
     * hide). Its icon copy is removed with it; nothing else is touched.
     */
    fun removeCustomTool(id: String) {
        viewModelScope.launch {
            repo.setCustomTools(repo.customTools.first().filterNot { it.id == id })
            iconStore.remove(getApplication(), id)
            repo.setToolIcons(repo.toolIcons.first() - id)
        }
    }

    // ----------------------------------------------------------------- icons

    /**
     * Import a picked image as a launcher's icon (PART D): the bytes are
     * copied into app storage NOW — the picker URI is used once and never
     * persisted. Failures return false and simply leave the text badge.
     */
    fun importIcon(launcherId: String, uri: Uri, companion: Boolean, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val resolver = getApplication<Application>().contentResolver
                    resolver.openInputStream(uri)?.use { stream ->
                        val filename = iconStore.import(getApplication(), launcherId, stream)
                        if (filename != null) {
                            if (companion) {
                                repo.setCompanionIcons(repo.companionIcons.first() + (launcherId to filename))
                            } else {
                                repo.setToolIcons(repo.toolIcons.first() + (launcherId to filename))
                            }
                            true
                        } else {
                            false
                        }
                    } ?: false
                } catch (_: Exception) {
                    false
                }
            }
            onResult(ok)
        }
    }

    /** Drop a launcher's icon copy (falls back to the text badge). */
    fun clearIcon(launcherId: String, companion: Boolean) {
        viewModelScope.launch {
            iconStore.remove(getApplication(), launcherId)
            if (companion) {
                repo.setCompanionIcons(repo.companionIcons.first() - launcherId)
            } else {
                repo.setToolIcons(repo.toolIcons.first() - launcherId)
            }
        }
    }

    // ------------------------------------------------- built-in companion ops

    /**
     * Restore a built-in companion whose DEFINITION was deleted (distinct
     * from unhide): re-adds the seed with its FIXED id through the existing
     * companion API. No-op when the definition still exists.
     */
    fun restoreBuiltInCompanion(seedId: String) {
        val seed = BuiltInCompanions.SEEDS.firstOrNull { it.id == seedId } ?: return
        viewModelScope.launch {
            val defs = companionRepo.defs.first()
            if (defs.any { it.id == seedId }) return@launch
            // Same merge used by the one-shot seeding — fixed id, normalized
            // URL, appended after the existing definitions.
            companionRepo.setDefs(mergeBuiltInCompanionSeeds(defs))
        }
    }

    /** Run the one-shot seeding (called from PocketShellApp at startup). */
    fun seedBuiltInCompanionsIfNeeded() {
        viewModelScope.launch {
            if (repo.builtinsSeeded.first()) {
                builtinsSeeded.value = true
                return@launch
            }
            val defs = withContext(Dispatchers.IO) { companionRepo.defs.first() }
            companionRepo.setDefs(mergeBuiltInCompanionSeeds(defs))
            repo.setBuiltinsSeeded(true)
            builtinsSeeded.value = true
        }
    }
}

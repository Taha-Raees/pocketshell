package app.pocketshell.cliapps

import kotlinx.serialization.Serializable

/**
 * Data-driven CLI application model (brief §16).
 *
 * Nothing here is hard-coded to a specific product — Hermes, Git, Python, etc.
 * are all future instances of this model. Registry starts EMPTY on fresh
 * install; only genuinely installed apps may appear in it (brief §2/§3/§34).
 */
@Serializable
data class CliApp(
    val id: String,
    val name: String,
    val description: String = "",
    /** Executable name resolved via PATH, or an absolute path. */
    val executable: String,
    val arguments: List<String> = emptyList(),
    /** M2: package id once a real package manager exists. */
    val packageName: String? = null,
    /** Version reported by a real installation only — never fabricated. */
    val version: String? = null,
    val category: String = "general",
    val workingDirectory: String? = null,
    val environment: Map<String, String> = emptyMap(),
)

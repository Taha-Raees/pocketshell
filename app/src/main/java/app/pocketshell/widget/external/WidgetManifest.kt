package app.pocketshell.widget.external

import kotlinx.serialization.Serializable

/**
 * M8 — the OPTIONAL (catalog) widget manifest schema.
 *
 * The trust model's core property (docs/M8-WIDGET-SYSTEM.md §6): an
 * external widget is DATA, never code. A manifest can only reference
 * BUILT-IN probe primitives (an exact `kind` from a fixed vocabulary,
 * with parameters confined by the validator) and a small card template —
 * the renderer is fixed app code. Nothing in a manifest is executed,
 * downloaded artifacts are never binaries, and there is deliberately no
 * escape hatch to "run this shell command": shell-probing manifests are a
 * possible FUTURE stage requiring signature verification + explicit user
 * consent, not something checksums could make safe.
 */
@Serializable
data class WidgetManifest(
    /** Stable registry id ("servers", "tmux-…"): lower-case, digits, dashes. */
    val id: String,
    val name: String,
    /** Semver of the widget itself. */
    val version: String,
    val summary: String = "",
    val author: String = "",
    /** Minimum PocketShell version that understands this schema. */
    val minAppVersion: String = "0.0.0",
    /** Capability vocabulary — must COVER the probe (validator-enforced). */
    val capabilities: List<String> = emptyList(),
    val probe: Probe,
    val card: Card,
) {

    @Serializable
    data class Probe(
        /** A built-in primitive: "proc.net.listen" or "storage.rootfs". */
        val kind: String,
        /** Confined parameters per kind (validator-checked, never a command). */
        val params: Map<String, String> = emptyMap(),
    )

    @Serializable
    data class Card(
        /** Overridable title line (defaults to the widget name). */
        val headline: String = "",
        /** Shown when the probe yields no rows. */
        val emptyLine: String = "Nothing yet",
        /**
         * Row template over probe fields: "{port} {process}" for listeners,
         * "{name} {size}" for storage. Plain substitution, no expressions.
         */
        val itemTemplate: String = "{text}",
        val maxLines: Int = 3,
    )
}

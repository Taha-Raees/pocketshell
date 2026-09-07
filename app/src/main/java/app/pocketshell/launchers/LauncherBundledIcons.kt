package app.pocketshell.launchers

/**
 * M7.1 P2/P2.1/P2.2 — the bundled curated icons (PART D/E).
 *
 * A tiny STATIC table: curated launcher id → packaged asset pair inside
 * the APK (`app/src/main/assets/…`), one variant per app theme. The P2.2
 * theme-scheme contract: `{id}.webp` is the Midnight (dark) treatment and
 * `{id}-light.webp` the Daylight (light) treatment — glyph marks carry the
 * theme's own plate tone (the same keyAlt surface the badge tiles paint),
 * so the launcher grid follows the selected theme. The assets were sourced
 * from the official first-party web origins / owner-supplied vectors at
 * build time and normalized onto one square canvas — there is NO runtime
 * download, NO network icon discovery, and NO third icon source beyond
 * the two the launcher system already has:
 *
 *   curated launcher:  bundled icon (theme variant) → (unavailable) → badge
 *   custom launcher:   user's imported copy → (unavailable) → badge
 *
 * Unknown ids (every custom tool/companion) miss the table and behave
 * exactly as before. Pure and JVM-pinned by [LauncherBundledIconsTest]
 * in BOTH theme directions; the source-tree/built-APK packaging tests
 * assert every mapped file actually ships (the GuestGlibcRuntimeTest
 * asset-pin pattern, at icon weight).
 */
object LauncherBundledIcons {

    private const val DIR = "launcher_icons"

    /**
     * Curated ids only — the four built-in companion seeds (their fixed
     * `builtin-*` ids) and the built-in CLI registry ids. Keys must stay in
     * lockstep with [BuiltInCompanions.SEEDS] and
     * [app.pocketshell.apps.CommandAppCatalog.registry]; both directions are
     * pinned by tests. M7.1 P2.1: Aider left the curated set — its id left
     * with it (a removed launcher never keeps a packaged asset).
     */
    private val IDS: Set<String> = setOf(
        // Companions (the P1 seed set)
        "builtin-chatgpt",
        "builtin-claude",
        "builtin-zai",
        "builtin-github",
        // CLI tools (the full built-in registry, M7.1 P2.1 curated set —
        // Aider removed by the owner, the nine owner-supplied marks refreshed)
        "hermes",
        "opencode",
        "claude",
        "zcode",
        "kilo",
        "cline",
        "agy",
        "codex",
        "qwen",
    )

    /**
     * The packaged asset path for [launcherId] under the theme variant
     * [light], or null when this launcher has no bundled icon (custom
     * launchers, unknown ids). The path is always a plain safe relative
     * name — ids become filenames only from this fixed table, never from
     * user input, and the only suffix ever appended is the internal
     * `-light` variant marker.
     */
    fun assetPathFor(launcherId: String, light: Boolean): String? {
        if (launcherId !in IDS) return null
        return if (light) "$DIR/$launcherId-light.webp" else "$DIR/$launcherId.webp"
    }

    /** Every curated id that claims a bundled asset pair (test surface). */
    val coveredIds: Set<String> = IDS
}

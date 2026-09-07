package app.pocketshell.launchers

/**
 * M7.1 P2 — the bundled curated icons (PART D/E).
 *
 * A tiny STATIC map: curated launcher id → asset path packaged inside the
 * APK (`app/src/main/assets/…`). The assets were sourced from the official
 * first-party web origins at build time and normalized onto one square
 * canvas — there is NO runtime download, NO network icon discovery, and NO
 * third icon source beyond the two the launcher system already has:
 *
 *   curated launcher:  bundled icon → (unavailable) → generated badge
 *   custom launcher:   user's imported copy → (unavailable) → badge
 *
 * Unknown ids (every custom tool/companion) miss the map and behave exactly
 * as before. Pure and JVM-pinned by [app.pocketshell.launchers.
 * LauncherBundledIconsTest]; the source-tree/built-APK packaging tests
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
     * pinned by tests. M7.1 P2.1: Aider left the curated set — its mapping
     * left with it (a removed launcher never keeps a packaged asset).
     */
    private val PATHS: Map<String, String> = mapOf(
        // Companions (the P1 seed set)
        "builtin-chatgpt" to "$DIR/builtin-chatgpt.webp",
        "builtin-claude" to "$DIR/builtin-claude.webp",
        "builtin-zai" to "$DIR/builtin-zai.webp",
        "builtin-github" to "$DIR/builtin-github.webp",
        // CLI tools (the full built-in registry, M7.1 P2.1 curated set —
        // Aider removed by the owner, the nine owner-supplied marks refreshed)
        "hermes" to "$DIR/hermes.webp",
        "opencode" to "$DIR/opencode.webp",
        "claude" to "$DIR/claude.webp",
        "zcode" to "$DIR/zcode.webp",
        "kilo" to "$DIR/kilo.webp",
        "cline" to "$DIR/cline.webp",
        "agy" to "$DIR/agy.webp",
        "codex" to "$DIR/codex.webp",
        "qwen" to "$DIR/qwen.webp",
    )

    /**
     * The packaged asset path for [launcherId], or null when this launcher
     * has no bundled icon (custom launchers, unknown ids). The path is
     * always a plain safe relative name — ids become filenames only from
     * this fixed table, never from user input.
     */
    fun assetPathFor(launcherId: String): String? = PATHS[launcherId]

    /** Every curated id that claims a bundled asset (test surface). */
    val coveredIds: Set<String> = PATHS.keys
}

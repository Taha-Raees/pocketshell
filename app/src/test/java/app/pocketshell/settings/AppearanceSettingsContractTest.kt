package app.pocketshell.settings

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Control Center persistence contract (task §10) — the same source-structure
 * pinning style the ExternalKeyboard and LauncherRows suites use: the
 * appearance keys MUST live in the ONE settings DataStore (no duplicate
 * sources of truth), and the Control Center routes MUST be wired. Runtime
 * persistence across restart/recreation is device-verified (docs/TESTING).
 */
class AppearanceSettingsContractTest {

    private fun readSource(relative: String): String {
        val file = listOf(
            "app/src/main/java/$relative",
            "src/main/java/$relative",
        ).map { File(it) }.firstOrNull { it.isFile }
        org.junit.Assume.assumeTrue("source not found on this runner: $relative", file != null)
        return file!!.readText()
    }

    private val repositoryCode: String by lazy {
        readSource("app/pocketshell/settings/SettingsRepository.kt")
    }

    private val mainActivityCode: String by lazy {
        readSource("app/pocketshell/MainActivity.kt")
    }

    private val appearanceCode: String by lazy {
        readSource("app/pocketshell/ui/settings/AppearanceScreen.kt")
    }

    @Test
    fun `every appearance setting persists through the ONE settings DataStore`() {
        listOf(
            "app_theme", "theme_mode", "dynamic_color",
            "text_scale", "icon_size", "card_size", "icon_columns",
            "onscreen_keyboard_enabled", "default_font_size",
        ).forEach { key ->
            assertTrue(
                "SettingsRepository must own the \"$key\" key",
                repositoryCode.contains("\"$key\""),
            )
        }
    }

    @Test
    fun `every appearance setting has a repository writer`() {
        listOf(
            "suspend fun setTheme(", "suspend fun setThemeMode(",
            "suspend fun setTextScale(", "suspend fun setIconSize(",
            "suspend fun setCardSize(", "suspend fun setIconColumns(",
        ).forEach { fn ->
            assertTrue("SettingsRepository must declare $fn", repositoryCode.contains(fn))
        }
    }

    @Test
    fun `the AMOLED mode is gone from the selector and the appearance route is wired`() {
        // the Appearance page renders modes from the enum (no hardcoded AMOLED row)
        assertTrue(appearanceCode.contains("ThemeMode.entries.forEach"))
        assertTrue(
            "AMOLED must not be a selectable mode",
            !appearanceCode.contains("ThemeMode.AMOLED"),
        )
        assertTrue(
            "MainActivity must route the Appearance page",
            mainActivityCode.contains("\"appearance\" -> AppearanceScreen("),
        )
    }

    @Test
    fun `the theme switch keeps its no-flash ordering contract`() {
        // TerminalTheme.applyTheme must still be invoked synchronously inside
        // PocketShellTheme composition BEFORE content (Phase 5 §5 contract).
        val themeCode = readSource("app/pocketshell/ui/theme/Theme.kt")
        assertTrue(themeCode.contains("TerminalTheme.applyTheme(theme, dark.not())"))
        assertTrue(themeCode.indexOf("TerminalTheme.applyTheme") < themeCode.indexOf("MaterialTheme("))
    }
}

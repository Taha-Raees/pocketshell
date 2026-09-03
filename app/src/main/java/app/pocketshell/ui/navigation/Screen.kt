package app.pocketshell.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Typed navigation spine (docs/UI-REDESIGN.md §4).
 *
 * The app has NO permanent tab bar; every screen is reachable from the
 * hamburger drawer. Future destinations (GUI Apps, SSH, Distributions, an AI
 * chat surface) are added as enum entries + one drawer row — the navigation
 * architecture never has to be rewritten.
 */
enum class Screen(
    val label: String,
    val icon: ImageVector,
) {
    HOME("Home", Icons.Outlined.Home),
    TERMINAL("Terminal", Icons.Outlined.Terminal),
    APPS("Apps", Icons.Outlined.SmartToy),
    PACKAGES("Packages", Icons.Outlined.Explore),
    DIAGNOSTICS("Diagnostics", Icons.Outlined.Info),
    SETTINGS("Settings", Icons.Outlined.Settings),
}

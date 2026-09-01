package app.pocketshell.packages

/**
 * M2.4 curated catalog — METADATA ONLY, deliberately tiny.
 *
 * The catalog says "this app exists as an installable package". It NEVER says
 * "this app is installed": the runtime answers that (`apk info -e`), always.
 * There is deliberately no `installed` field here — hard-coding one is the
 * exact lie this project refuses.
 *
 * Deliberately NOT in this list: sh, ls, cat, pwd, df, ps, ping, top, … —
 * normal shell commands never become launcher cards. Only meaningful CLI
 * applications do (brief: Home = Terminal + real installed CLI apps).
 *
 * All five entries are verified real Alpine v3.24 packages (rehearsal +
 * dl-cdn); each is installable with plain `apk add <apkPackageName>` and its
 * [executable] is what `command -v` must find before "Open" is offered.
 */
data class CliAppCatalogEntry(
    val id: String,
    val name: String,
    val description: String,
    val apkPackageName: String,
    val executable: String,
    val category: String,
    /** Guest command for "Open" — the real program, no shell wrapping. */
    val launchCommand: List<String>,
)

object CliAppCatalog {

    val entries: List<CliAppCatalogEntry> = listOf(
        CliAppCatalogEntry(
            id = "nano",
            name = "Nano",
            description = "Terminal text editor",
            apkPackageName = "nano",
            executable = "nano",
            category = "editors",
            launchCommand = listOf("nano"),
        ),
        CliAppCatalogEntry(
            id = "htop",
            name = "HTop",
            description = "Interactive process viewer",
            apkPackageName = "htop",
            executable = "htop",
            category = "system",
            launchCommand = listOf("htop"),
        ),
        CliAppCatalogEntry(
            id = "vim",
            name = "Vim",
            description = "Powerful modal text editor",
            apkPackageName = "vim",
            executable = "vim",
            category = "editors",
            launchCommand = listOf("vim"),
        ),
        CliAppCatalogEntry(
            id = "git",
            name = "Git",
            description = "Distributed version control",
            apkPackageName = "git",
            executable = "git",
            category = "development",
            launchCommand = listOf("git"),
        ),
        CliAppCatalogEntry(
            id = "python3",
            name = "Python",
            description = "Python 3 interpreter and REPL",
            apkPackageName = "python3",
            executable = "python3",
            category = "development",
            launchCommand = listOf("python3"),
        ),
    )

    fun byId(id: String): CliAppCatalogEntry? = entries.firstOrNull { it.id == id }
}

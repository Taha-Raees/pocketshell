package app.pocketshell.packages

/**
 * The real Alpine package management contract (M2.4). Every method runs the
 * actual `apk` inside the proot guest — nothing here may read or write Alpine
 * package files directly, and no result may be invented.
 */
interface PackageManager {

    /** `apk update` — refresh repository indexes. */
    suspend fun updateRepositories(): PackageResult

    /** `apk search <query>` — real repository index search. */
    suspend fun search(query: String): List<PackageSearchResult>

    /** `apk info -e -v <packageName>` — installed + real version, or not. */
    suspend fun getPackageInfo(packageName: String): PackageInfoResult

    /**
     * Batch variant over one guest exec (UI list refresh). Returns only real
     * answers; a failed guest exec throws [PackageProbeException] so callers
     * can never mistake "probe failed" for "nothing installed".
     */
    suspend fun getInstalledVersions(packageNames: List<String>): Map<String, String>

    /** `apk add <packageName>` — caller orchestrates verification states. */
    suspend fun install(packageName: String): PackageResult

    /** `apk del <packageName>`. */
    suspend fun uninstall(packageName: String): PackageResult

    /** POSIX `command -v <executable>` inside the guest — the launcher gate. */
    suspend fun guestExecutablePath(executable: String): String?
}

package app.pocketshell.packages

/**
 * M2.4 package layer models. The UI state may ONLY ever come from real guest
 * answers (exit codes + stdout), never from assumptions — the same honesty
 * contract as the runtime installer.
 */

/** Explicit operation state — displayed verbatim, never faked (no percent). */
enum class PackageOperationState {
    IDLE,
    UPDATING_REPOSITORIES,
    SEARCHING,
    INSTALLING,
    VERIFYING,
    UNINSTALLING,
    SUCCESS,
    FAILED,
}

/** What kind of mutating/search operation is (or was) running. */
enum class PackageOperationKind { UPDATE_REPOSITORIES, INSTALL, UNINSTALL, SEARCH }

/**
 * Result of a single real guest command. [exitCode] is the guest process's
 * own exit code; [stdout]/[stderr] carry the real output (tails may be
 * truncated by the collector — see [PackageOperationManager]).
 */
data class PackageResult(
    val success: Boolean,
    val exitCode: Int?,
    val stdout: String = "",
    val stderr: String = "",
    val error: String? = null,
) {
    companion object {
        fun failure(error: String, exitCode: Int? = null, stderr: String = "") =
            PackageResult(success = false, exitCode = exitCode, stderr = stderr, error = error)
    }
}

/** One parsed `apk search` hit. Description is NOT available in apk-tools 3
 *  search output (bare `name-version-release` lines) — the UI shows catalog
 *  descriptions for known packages and never invents one. */
data class PackageSearchResult(
    val name: String,
    val version: String,
    val release: String?,
)

/** `apk info -e -v <pkg>` answer: exit code decides, version is real. */
data class PackageInfoResult(
    val installed: Boolean,
    val version: String? = null,
)

/** Full result of an orchestrated operation (install/uninstall/update). */
data class PackageOperationResult(
    val success: Boolean,
    val packageName: String?,
    val state: PackageOperationState,
    val error: String? = null,
)

/**
 * Thrown when a read-only probe (the installed-state batch check) could not
 * get a real guest answer — timeout, destroyed process, non-zero exec exit.
 * An EMPTY map means "apk was asked and answered: none of these are
 * installed"; this exception means "apk never (fully) answered". Rendering
 * "Not installed" over a failed probe is a lie — exactly what v0.4.3's
 * exit-code misread did to a genuinely installed nano on the device
 * (v0.4.4 device lesson, user screenshots 2026-09-02 09:10).
 */
class PackageProbeException(
    override val message: String,
    val exitCode: Int? = null,
) : Exception(message)

/**
 * One Home-screen row: a catalog entry whose installed state the REAL apk
 * database confirmed (`apk info -e -v`), with the version apk reported.
 * Built only by [installedCatalogApps] — never from assumptions.
 */
data class InstalledCatalogApp(
    val entry: CliAppCatalogEntry,
    val version: String,
)

/**
 * The catalog subset the real apk database confirms installed, in catalog
 * order, carrying the real versions. Anything not in the answer is absent —
 * this function never adds a package the guest did not name.
 */
fun installedCatalogApps(versions: Map<String, String>): List<InstalledCatalogApp> =
    CliAppCatalog.entries.mapNotNull { entry ->
        versions[entry.apkPackageName]?.let { InstalledCatalogApp(entry, it) }
    }

/** Immutable snapshot of a (running or finished) operation for the UI. */
data class PackageOperation(
    val id: Long,
    val kind: PackageOperationKind,
    val packageName: String? = null,
    val query: String? = null,
    val state: PackageOperationState = PackageOperationState.FAILED,
    val startTimeEpochMs: Long = 0L,
    val endTimeEpochMs: Long? = null,
    val stdoutTail: String = "",
    val stderrTail: String = "",
    val exitCode: Int? = null,
    val error: String? = null,
)

/**
 * v0.4.2 honesty rule for catalog cards: a card may show "Working…" ONLY
 * while the RUNNING mutating operation targets THIS card's package (or the
 * card itself is being verified). v0.4.1 rendered the global single-flight
 * busy flag on every card, so installing nano flipped all five buttons to
 * "Working…" — state those cards did not have (user screenshot 2026-09-02).
 * Other cards keep their true labels; the mutation lock itself is still
 * enforced by the manager's single-flight guard. Only ACTIVE operation
 * states may claim work — a finished (SUCCESS/FAILED) snapshot never does,
 * even defensively.
 */
private val ACTIVE_OPERATION_STATES = setOf(
    PackageOperationState.IDLE,
    PackageOperationState.UPDATING_REPOSITORIES,
    PackageOperationState.INSTALLING,
    PackageOperationState.VERIFYING,
    PackageOperationState.UNINSTALLING,
)

fun packageOperationTargetsCard(
    packageBusy: Boolean,
    operation: PackageOperation?,
    apkPackageName: String,
): Boolean = packageBusy &&
    operation != null &&
    operation.state in ACTIVE_OPERATION_STATES &&
    operation.packageName == apkPackageName

/**
 * Strict parser for apk-tools 3 output, pinned by the sandbox rehearsal
 * (scripts/rehearse_m24_packages.sh, apk-tools 3.0.6):
 *
 *   `apk search nano`  → lines like  nano-9.2-r0 / openvdb-nanovdb-13.0.0-r1
 *   `apk info -e -v nano` → nano-9.2-r0 (exit 0) or nothing (exit 1)
 *
 * The name may contain hyphens and digits; the version starts at the first
 * digit AFTER the name — split at the LAST hyphen that is followed by a digit
 * and followed later by an `-r<release>` token. Lines that do not parse are
 * skipped (never guessed).
 */
object ApkOutputParser {

    // name = greedy-any, ver = starts with a digit, optional release token.
    private val LINE = Regex("^(.+?)-([0-9][^-]*)(-r[0-9][A-Za-z0-9._]*)?$")

    fun parseVersionLine(line: String): PackageSearchResult? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null
        val m = LINE.matchEntire(trimmed) ?: return null
        val (name, version, release) = m.destructured
        if (name.isBlank()) return null
        return PackageSearchResult(
            name = name,
            version = version,
            release = release.removePrefix("-").ifEmpty { null },
        )
    }

    fun parseSearch(output: String): List<PackageSearchResult> =
        output.lineSequence().mapNotNull { parseVersionLine(it) }.toList()

    /** `apk info -e -v <pkg>`: exit 0 + one line = installed+version. */
    fun parseInfoInstalled(exitCode: Int?, stdout: String): PackageInfoResult {
        if (exitCode != 0) return PackageInfoResult(installed = false)
        val hit = parseVersionLine(stdout.lineSequence().firstOrNull { it.isNotBlank() } ?: "")
            ?: return PackageInfoResult(installed = false)
        return PackageInfoResult(
            installed = true,
            version = "${hit.version}${hit.release?.let { "-$it" } ?: ""}",
        )
    }

    /**
     * Package names apk accepts; used as a defensive guard so nothing odd is
     * ever placed into a guest argv. (argv passing is already injection-safe;
     * this only turns garbage input into an honest early failure.)
     */
    private val NAME = Regex("^[a-zA-Z0-9][a-zA-Z0-9._+-]*$")

    fun isValidPackageName(name: String): Boolean = name.matches(NAME)
}

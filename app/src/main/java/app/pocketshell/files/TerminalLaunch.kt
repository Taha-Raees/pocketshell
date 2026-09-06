package app.pocketshell.files

/**
 * M7.0.0 Phase 7 — everything "Open Terminal Here" needs to launch ONE
 * terminal session, resolved as pure data.
 *
 * The product contract: the action creates a NORMAL PocketShell Linux
 * terminal session whose guest working directory is the folder the user
 * tapped (p7.1 — the launch resolves the SELECTED directory ENTRY under the
 * browsed location, never the browsed location itself: opening terminal on
 * folder X must land in X, not in X's parent). The directory stays DATA the
 * whole way: it is a validated [AreaPath] composed by the SAME child
 * composition every navigation uses ([terminalLaunchDirectory] →
 * `ExplorerOps.composeChild` — no second validator, no second path
 * representation, never turned into shell syntax — the quoting happens
 * once, in `apps/CommandApps.kt#guestTerminalChain`, at PTY-argv build
 * time).
 *
 * The strict storage boundary: a terminal session is a LINUX guest process
 * with a Linux working directory. It is therefore offered ONLY for
 * [AreaKind.GUEST_LINUX]. Android-owned areas ([AreaKind.ANDROID_SHELF],
 * [AreaKind.ANDROID_DOCUMENT_TREE]) are different storage worlds — their
 * paths are NOT guest paths, no bind mount may fabricate one, and a
 * `content://` URI can never become a POSIX cwd. For those areas the gate
 * returns the honest boundary explanation instead of a launch, and the UI
 * shows that explanation instead of an actionable button. No proot bind
 * mounts, no fabricated `/storage/emulated/0` paths, no fake launches.
 */
data class TerminalLaunch(
    /** Which storage area the launch came from (GUEST_LINUX only). */
    val areaId: AreaId,
    /** The area kind, carried explicitly so consumers never re-derive it. */
    val kind: AreaKind,
    /**
     * The tapped directory entry, resolved and validated against the
     * explorer's current location — the [AreaPath] the terminal session
     * must open in (p7.1: the folder the user tapped, not the browsed
     * parent).
     */
    val directory: AreaPath,
) {
    init {
        // Fail fast if a non-Linux area ever reaches the launch model: this
        // class only ever carries launches the pure gate has allowed.
        require(kind == AreaKind.GUEST_LINUX) {
            "Open Terminal Here is a Linux-guest launch; refusing kind $kind"
        }
    }
}

/**
 * The one honest outcome type of `FilesViewModel.terminalLaunch()`:
 * a validated launch request, or the honest reason no launch may happen.
 * No third state — a refused launch is DATA, never a silent null and never
 * a fake button.
 */
sealed interface TerminalLaunchResolution {
    /** The explorer's current directory may open a terminal session. */
    data class Ready(val launch: TerminalLaunch) : TerminalLaunchResolution

    /** The current area can never open a terminal; [reason] renders verbatim. */
    data class NotSupported(val reason: String) : TerminalLaunchResolution
}

/**
 * The pure area-kind gate for "Open Terminal Here".
 *
 * - [AreaKind.GUEST_LINUX] → null (launch allowed).
 * - [AreaKind.ANDROID_SHELF] / [AreaKind.ANDROID_DOCUMENT_TREE] → the honest
 *   Android-boundary explanation (the exact product wording, pinned by
 *   tests): Android folders are not Linux guest directories; the workflow is
 *   copy/move into PocketShell Linux. The message never offers a fake
 *   terminal launch and never displays a fabricated Linux path.
 */
fun openTerminalHereProblem(kind: AreaKind): String? = when (kind) {
    AreaKind.GUEST_LINUX -> null
    AreaKind.ANDROID_SHELF,
    AreaKind.ANDROID_DOCUMENT_TREE,
    -> TerminalLaunchSupport.ANDROID_BOUNDARY_MESSAGE
}

/**
 * M7.0.0 Phase 7 constants with exactly one home (pinned by [TerminalLaunchTest]).
 */
object TerminalLaunchSupport {
    /**
     * The honest explanation shown instead of an actionable terminal launch
     * for Android-owned areas — in the action sheet AND as the refusal
     * reason if a launch is ever resolved there.
     */
    const val ANDROID_BOUNDARY_MESSAGE: String =
        "Android folders are not Linux guest directories. " +
            "Copy or move files into PocketShell Linux to work with them in Terminal."
}

/**
 * p7.1 — the pure resolution behind "Open Terminal Here" on a TAPPED
 * directory entry: the launch must open THE TAPPED FOLDER, not the location
 * the explorer is currently browsing (the p7.0 device report: tapping the
 * action on a folder opened the terminal in the browsed parent instead —
 * the browsed location was launched because it was what the resolver held,
 * not because it was what the user chose).
 *
 * The resolution reuses the EXACT pieces every child navigation uses —
 * the listing the user tapped from ([entries] is the explorer's current
 * listing), the [EntryKind.DIRECTORY] check the sheet itself applies, and
 * [ExplorerOps.composeChild] (the ONE validated name+path composition —
 * no second validator, no second path representation). The directory
 * remains DATA: what comes back is a validated [AreaPath], quoted only
 * once at PTY-argv build time.
 *
 * Returns null — never a fallback — when the resolution honestly cannot
 * happen: the name is not in the current listing (stale sheet after a
 * refresh), the entry is not a directory, or the composed path does not
 * validate. A null here becomes an honest refusal in the caller; silently
 * launching in the browsed directory instead would be exactly the
 * somewhere-else launch this function exists to prevent.
 */
fun terminalLaunchDirectory(
    browsed: AreaPath,
    entries: List<FsEntry>,
    selectedName: String,
): AreaPath? {
    val kind = entries.firstOrNull { it.name == selectedName }?.kind ?: return null
    if (kind != EntryKind.DIRECTORY) return null
    return ExplorerOps.composeChild(browsed, selectedName)
}

package app.pocketshell.files

/**
 * M7.0.0 Phase 7 — everything "Open Terminal Here" needs to launch ONE
 * terminal session, resolved as pure data.
 *
 * The product contract: the action creates a NORMAL PocketShell Linux
 * terminal session whose guest working directory is the exact directory
 * currently selected in the Files explorer. The directory stays DATA the
 * whole way: it is the already-validated [AreaPath] the explorer is
 * browsing (never re-validated, never re-represented as a second path type,
 * never turned into shell syntax — the quoting happens once, in
 * `apps/CommandApps.kt#guestTerminalChain`, at PTY-argv build time).
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
     * The exact directory currently selected in the explorer — the SAME
     * validated [AreaPath] instance the explorer state already holds.
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

/** Phase 7 constants with exactly one home (pinned by [TerminalLaunchTest]). */
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

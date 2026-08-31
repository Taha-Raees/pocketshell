package app.pocketshell.runtime

/**
 * Lifecycle states of the Linux runtime (docs/M2-ARCHITECTURE §4).
 *
 * The state is always *derived from disk* — it is never invented by the UI.
 * [READY] means structural on-disk validation passed; guest-level validation
 * (`uname` inside proot) is a later milestone's concern and is never implied.
 */
enum class RuntimeState {
    /** No runtime on disk, no in-flight installation. */
    NOT_INSTALLED,

    /** Device ABI cannot host the pinned rootfs (aarch64-only in M2). */
    UNSUPPORTED_ABI,

    /** Rootfs archive downloading into runtime-download.tmp. */
    DOWNLOADING,

    /** Size + SHA-256 verification of the downloaded archive. */
    VERIFYING,

    /** Extracting the archive into runtime-extract.tmp (staging). */
    EXTRACTING,

    /** Structural validation + runtime.json write, before atomic promotion. */
    CONFIGURING,

    /** Runtime installed, promoted and structurally validated. */
    READY,

    /** Installation failed; transient tmp state has been cleaned. Retry allowed. */
    FAILED,

    /** Runtime exists on disk but failed structural validation (or metadata is damaged). */
    REPAIR_REQUIRED,
    ;

    /** Legal transitions (docs/M2-ARCHITECTURE §4). Anything not listed is rejected. */
    fun canTransitionTo(target: RuntimeState): Boolean = when (this) {
        NOT_INSTALLED -> target == DOWNLOADING || target == UNSUPPORTED_ABI
        UNSUPPORTED_ABI -> target == NOT_INSTALLED
        DOWNLOADING -> target == VERIFYING || target == FAILED || target == NOT_INSTALLED
        VERIFYING -> target == EXTRACTING || target == FAILED
        EXTRACTING -> target == CONFIGURING || target == FAILED
        CONFIGURING -> target == READY || target == FAILED || target == REPAIR_REQUIRED
        READY -> target == REPAIR_REQUIRED || target == NOT_INSTALLED
        FAILED -> target == DOWNLOADING || target == NOT_INSTALLED
        REPAIR_REQUIRED -> target == DOWNLOADING || target == NOT_INSTALLED
    }

    /** True when a runtime directory is expected to exist on disk. */
    val expectsRuntimeOnDisk: Boolean
        get() = this == READY || this == REPAIR_REQUIRED

    companion object {
        /** States from which a (re-)installation may be started. */
        fun canStartInstall(state: RuntimeState): Boolean =
            state == NOT_INSTALLED ||
                state == FAILED ||
                state == REPAIR_REQUIRED
    }
}

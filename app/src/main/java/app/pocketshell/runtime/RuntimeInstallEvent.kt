package app.pocketshell.runtime

/**
 * Real, source-driven progress events for the install pipeline
 * (Master Prompt §14 — the terminal/process is the source of truth; the app
 * never fabricates percentages).
 */
sealed interface RuntimeInstallEvent {

    /** Rootfs archive bytes actually read from the network. */
    data class DownloadProgress(val bytesRead: Long, val totalBytes: Long) : RuntimeInstallEvent

    /** Download finished; expected size matched Content-Length. */
    data object Downloaded : RuntimeInstallEvent

    /** Archive bytes actually hashed. */
    data class VerifyProgress(val bytesHashed: Long) : RuntimeInstallEvent

    /** Archive entries actually extracted so far. */
    data class ExtractProgress(val entriesProcessed: Int, val lastEntry: String) : RuntimeInstallEvent

    /** Staging validated, runtime.json written, about to promote. */
    data object Configured : RuntimeInstallEvent

    /** Atomic promotion + read-back succeeded. */
    data object Ready : RuntimeInstallEvent

    /** Stage where the failure happened + root cause (message only, no fake detail). */
    data class Failed(val stage: RuntimeState, val message: String) : RuntimeInstallEvent
}

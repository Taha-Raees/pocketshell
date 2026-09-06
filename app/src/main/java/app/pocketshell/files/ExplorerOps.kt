package app.pocketshell.files

/**
 * One pending copy/move (the "clipboard" — app state, never the system
 * clipboard). Exactly one at a time; starting a new one replaces it.
 */
data class PendingTransfer(
    val areaId: AreaId,
    val areaLabel: String,
    /** Full validated source path inside the source area. */
    val path: AreaPath,
    /** Display name (= last path component). */
    val name: String,
    val kind: EntryKind,
    val move: Boolean,
)

/**
 * M7.0.0 Phase 4 — the file-operation layer.
 *
 * PURE and SYNCHRONOUS like [ExplorerCore]: no Android imports, no
 * coroutines. Every function performs blocking I/O through the Phase 2
 * [StorageArea] abstraction (and [CrossArea] for cross-domain transfers) —
 * the ViewModel invokes them on its serial IO dispatcher. NOTHING here
 * touches java.io directly: the storage abstraction is never bypassed.
 *
 * COLLISION CONTRACT (the Phase 2 asymmetry, made explicit):
 *  - Same-area copy/move/rename REFUSE an existing target at engine level.
 *  - CrossArea deliberately overwrites existing FILE targets (its KDoc puts
 *    collision policy with the caller) — so [checkPaste] / [executePaste]
 *    pre-check the destination for cross-area operations. A cross-area paste
 *    without replace=true can NEVER overwrite anything; this is pinned by
 *    tests.
 *
 * REPLACE semantics (uniform, the smallest honest policy): when the user
 * explicitly chooses Replace, the existing destination is deleted first and
 * the operation is then performed through the normal primitives. The user
 * confirmed the loss of the old content; any failure after the delete is
 * reported verbatim and never silently retried. No merge behavior is
 * invented anywhere.
 */
object ExplorerOps {

    // ------------------------------------------------------------- flow state

    /**
     * Uniform operation outcome. [refused] marks a POLICY refusal (protected
     * runtime area, path safety — OpResult DENIED) as opposed to a failure —
     * the UI renders "refused" differently from "went wrong".
     */
    data class OpOutcome(
        val success: Boolean,
        val refused: Boolean,
        val message: String,
    ) {
        companion object {
            fun ok(message: String) = OpOutcome(true, false, message)
            fun fail(message: String) = OpOutcome(false, false, message)
            fun refused(message: String) = OpOutcome(false, true, message)
        }
    }

    /** Result of [checkPaste]. */
    sealed interface PasteCheck {
        /** Destination is free — the paste may run directly. */
        data class Clear(val target: AreaPath) : PasteCheck

        /** Something exists at the destination — ask Replace / Cancel. */
        data class Collision(val target: AreaPath, val existing: FsEntry) : PasteCheck

        /** The paste would land on the source itself — refuse with guidance. */
        data object SameAsSource : PasteCheck

        /** The composed destination is not a valid location (never expected from listings). */
        data class Invalid(val reason: String) : PasteCheck
    }

    /** Result of [checkRename]. */
    sealed interface RenameCheck {
        data class Clear(val target: AreaPath) : RenameCheck
        data class Collision(val target: AreaPath, val existing: FsEntry) : RenameCheck
        /** New name equals the current name — a no-op, not an error. */
        data object Unchanged : RenameCheck
        data class Invalid(val reason: String) : RenameCheck
    }

    // ----------------------------------------------------------- name logic

    /**
     * Human explanation when [raw] is not a valid single name component,
     * or null when it is valid. Dotfiles are legitimate (".env").
     */
    fun nameError(raw: String): String? {
        if (raw.isEmpty()) return "Enter a name."
        if (raw.isBlank()) return "A name cannot be empty or only spaces."
        if (raw.length > 255) return "That name is too long (over 255 characters)."
        if (raw.contains('\u0000')) return "A name cannot contain NUL characters."
        if (raw.contains('/')) return "A name cannot contain \"/\" — it must be a single name, not a path."
        if (raw == "." || raw == "..") return "\"$raw\" is reserved and cannot be used as a name."
        return null
    }

    /** The validated child path of [dir] named [name], or null when invalid. */
    fun composeChild(dir: AreaPath, name: String): AreaPath? {
        if (PathSafety.validateName(name) == null) return null
        return PathSafety.validatePath(
            if (dir.value == "/") "/$name" else "${dir.value}/$name",
        )
    }

    /**
     * The parent directory of [path], or null when [path] IS the area root
     * (the root has no parent and cannot be renamed).
     */
    private fun parentOf(path: AreaPath): AreaPath? {
        if (path.value == "/") return null
        val parentValue = path.value.substringBeforeLast('/', "").ifEmpty { "/" }
        return PathSafety.validatePath(parentValue)
    }

    // ---------------------------------------------------------------- paste

    /**
     * Decide what a paste into [targetDir] means BEFORE running it: clear,
     * collision (ask the user), landing on the source, or invalid. The
     * destination pre-check is what makes cross-area pastes safe — CrossArea
     * alone would overwrite existing file targets by design.
     */
    fun checkPaste(
        sourceArea: StorageArea,
        pending: PendingTransfer,
        targetArea: StorageArea,
        targetDir: AreaPath,
    ): PasteCheck {
        val target = composeChild(targetDir, pending.name)
            ?: return PasteCheck.Invalid("\"${pending.name}\" does not name a valid destination.")
        if (sourceArea.id == targetArea.id && target.value == pending.path.value) {
            return PasteCheck.SameAsSource
        }
        val existing = targetArea.stat(target) ?: return PasteCheck.Clear(target)
        return PasteCheck.Collision(target, existing)
    }

    /**
     * Execute a pending paste. [replace] must come ONLY from an explicit user
     * confirmation of a collision that [checkPaste] reported.
     *
     * Cross-area without replace re-checks the destination as a safety net
     * (defense in depth — the UI already asked): an existing target REFUSES
     * the paste instead of silently overwriting.
     */
    fun executePaste(
        sourceArea: StorageArea,
        pending: PendingTransfer,
        targetArea: StorageArea,
        target: AreaPath,
        replace: Boolean,
    ): OpOutcome {
        val sameArea = sourceArea.id == targetArea.id
        if (sameArea && target.value == pending.path.value) {
            return OpOutcome.fail("Source and destination are the same — nothing was changed.")
        }

        if (replace) {
            // Uniform Replace = delete the confirmed destination first, then
            // perform the operation through the normal primitives.
            val removed = targetArea.delete(target)
            if (!removed.success) {
                return if (removed.kind == OpResult.Kind.DENIED) {
                    OpOutcome.refused(
                        "Could not replace ${target.value}: ${removed.reason ?: "delete refused"}",
                    )
                } else {
                    OpOutcome.fail(
                        "Could not replace ${target.value}: ${removed.reason ?: "delete failed"} " +
                            "— nothing was changed.",
                    )
                }
            }
        } else if (!sameArea) {
            // The CrossArea gate: never let a verified copy silently overwrite.
            if (targetArea.stat(target) != null) {
                return OpOutcome.fail(
                    "${target.value} already exists in ${targetArea.displayName} — " +
                        "choose Replace to overwrite it.",
                )
            }
        }

        return if (sameArea) {
            val verb = if (pending.move) "Moved" else "Copied"
            val result = if (pending.move) {
                targetArea.move(pending.path, target)
            } else {
                targetArea.copy(pending.path, target)
            }
            outcomeOf(result, "$verb ${pending.name} → ${target.value}")
        } else {
            val verb = if (pending.move) "Moved" else "Copied"
            if (!replace) {
                // Policy pre-flight: CrossResult erases the DENIED/FAILED
                // distinction, so a protected destination is detected HERE —
                // a cheap open+abort write probe whose Error honestly carries
                // denied=true — before a single byte moves.
                when (val probe = targetArea.openWriteAtomic(target)) {
                    is StreamWriteOpen.Error -> if (probe.denied) return OpOutcome.refused(probe.reason)
                    is StreamWriteOpen.Ok -> probe.session.abort()
                }
            }
            val result = if (pending.move) {
                CrossArea.move(sourceArea, pending.path, targetArea, target)
            } else {
                CrossArea.copy(sourceArea, pending.path, targetArea, target)
            }
            when {
                result.success -> {
                    var message = "$verb ${pending.name} to ${targetArea.displayName} (${target.value})"
                    message += result.warnings.take(3).joinToString("") { " — $it" }
                    OpOutcome.ok(message)
                }
                else -> OpOutcome.fail(result.reason ?: "transfer failed — the source is intact")
            }
        }
    }

    // --------------------------------------------------------------- rename

    /**
     * Decide what renaming [path] to [newName] means before running it.
     * Rename stays inside the path's own directory (the Phase 2 engine rule).
     */
    fun checkRename(area: StorageArea, path: AreaPath, newName: String): RenameCheck {
        nameError(newName)?.let { return RenameCheck.Invalid(it) }
        val parent = parentOf(path)
            ?: return RenameCheck.Invalid("The storage root itself cannot be renamed.")
        val target = composeChild(parent, newName)
            ?: return RenameCheck.Invalid("\"$newName\" does not name a valid destination.")
        if (target.value == path.value) return RenameCheck.Unchanged
        val existing = area.stat(target) ?: return RenameCheck.Clear(target)
        return RenameCheck.Collision(target, existing)
    }

    /** Execute a rename; [replace] only after explicit user confirmation. */
    fun executeRename(area: StorageArea, path: AreaPath, newName: String, replace: Boolean): OpOutcome {
        nameError(newName)?.let { return OpOutcome.fail(it) }
        val parent = parentOf(path)
            ?: return OpOutcome.fail("The storage root itself cannot be renamed.")
        val target = composeChild(parent, newName)
            ?: return OpOutcome.fail("\"$newName\" does not name a valid destination.")
        if (target.value == path.value) return OpOutcome.ok("The name is unchanged.")

        if (replace && area.stat(target) != null) {
            val removed = area.delete(target)
            if (!removed.success) {
                return if (removed.kind == OpResult.Kind.DENIED) {
                    OpOutcome.refused(
                        "Could not replace ${target.value}: ${removed.reason ?: "delete refused"}",
                    )
                } else {
                    OpOutcome.fail(
                        "Could not replace ${target.value}: ${removed.reason ?: "delete failed"} " +
                            "— nothing was changed.",
                    )
                }
            }
        }
        val result = area.rename(path, newName)
        return outcomeOf(result, "Renamed to $newName")
    }

    // ------------------------------------------------------- create file/dir

    fun executeCreateFile(area: StorageArea, dir: AreaPath, name: String): OpOutcome {
        val target = composeChild(dir, name)
            ?: return OpOutcome.fail(nameError(name) ?: "\"$name\" is not a valid file name.")
        val result = area.createFile(target)
        return mapCreation(result, target, "file")
    }

    fun executeCreateDirectory(area: StorageArea, dir: AreaPath, name: String): OpOutcome {
        val target = composeChild(dir, name)
            ?: return OpOutcome.fail(nameError(name) ?: "\"$name\" is not a valid folder name.")
        val result = area.createDirectory(target)
        return mapCreation(result, target, "folder")
    }

    private fun mapCreation(result: OpResult, target: AreaPath, what: String): OpOutcome = when {
        result.success -> OpOutcome.ok("Created $what ${target.value}")
        result.kind == OpResult.Kind.DENIED -> OpOutcome.refused(result.reason ?: "refused")
        else -> OpOutcome.fail(result.reason ?: "could not create the $what")
    }

    /** One uniform DENIED-vs-FAILED mapping for OpResult-producing calls. */
    private fun outcomeOf(result: OpResult, okMessage: String): OpOutcome = when {
        result.success -> OpOutcome.ok(okMessage)
        result.kind == OpResult.Kind.DENIED -> OpOutcome.refused(result.reason ?: "refused")
        else -> OpOutcome.fail(result.reason ?: "the operation failed")
    }

    // ---------------------------------------------------------------- delete

    /**
     * The honest extra line for a delete confirmation, per entry kind:
     * directories lose their contents, symlinks lose only the link.
     * Null for a plain file (nothing extra to disclose).
     */
    fun deleteWarning(kind: EntryKind): String? = when (kind) {
        EntryKind.DIRECTORY -> "The folder and everything inside it will be deleted."
        EntryKind.SYMLINK -> "Only the link is deleted — the target it points to is not touched."
        else -> null
    }

    /** Execute a delete (file / symlink node / NOFOLLOW directory tree). */
    fun executeDelete(area: StorageArea, path: AreaPath): OpOutcome {
        val result = area.delete(path)
        return when {
            result.success -> OpOutcome.ok("Deleted ${path.value}")
            result.kind == OpResult.Kind.DENIED -> OpOutcome.refused(result.reason ?: "refused")
            else -> OpOutcome.fail(result.reason ?: "delete failed")
        }
    }

    // ------------------------------------------------------------------ copy

    /** The pending-transfer banner: what is held, and what to do next. */
    fun bannerText(pending: PendingTransfer): String {
        val verb = if (pending.move) "move" else "copy"
        val area = if (pending.areaLabel.isBlank()) "" else " from ${pending.areaLabel}"
        return "Marked to $verb \"${pending.name}\"$area — open a destination and paste here."
    }
}

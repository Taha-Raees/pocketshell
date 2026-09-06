package app.pocketshell.files.saf

import java.io.InputStream
import java.io.OutputStream

/**
 * M7.0.0 Phase 5 — the seam between the SAF [AndroidDocumentArea] and the
 * Android framework.
 *
 * Raw android.provider.DocumentsContract / ContentResolver calls cannot run
 * in JVM tests, so every framework touchpoint is isolated behind this small
 * interface:
 *
 *     DocumentBackend  →  AndroidDocumentArea  →  StorageArea
 *        (android)          (pure over the seam)     (the abstraction)
 *
 * The production implementation is [DocumentsContractBackend]; the JVM test
 * suite drives the area through an in-memory fake. The area therefore never
 * imports android.* — capability behavior, revocation handling and collision
 * semantics are all pinned by plain JVM tests against the same code that
 * ships.
 *
 * ERROR MODEL: implementations signal failure by THROWING — exactly like the
 * framework does:
 *  - [SecurityException]  → permission revoked / grant lost (the area maps
 *    this to the honest "access no longer available" outcome and notifies
 *    its access-lost callback);
 *  - [java.io.FileNotFoundException] → the document is gone;
 *  - [java.io.IOException] / [UnsupportedOperationException] → provider
 *    limitation or I/O problem — surfaced verbatim, never swallowed.
 */
interface DocumentBackend {

    /** The granted tree's root document. Throws on revoked/unavailable access. */
    fun root(): SafDoc

    /**
     * Look up ONE child of [parent] by display name. Null when no child with
     * that name exists. Throws [SecurityException] on revoked access.
     */
    fun resolveChild(parent: SafDoc, name: String): SafDoc?

    /** All children of [parent] (unsorted — the area applies the order). */
    fun listChildren(parent: SafDoc): List<SafDoc>

    /** Create a directory inside [parent]; returns the created document. */
    fun createDirectory(parent: SafDoc, name: String): SafDoc

    /** Create an empty file inside [parent]; returns the created document. */
    fun createFile(parent: SafDoc, name: String, mimeType: String): SafDoc

    /**
     * Rename [doc] within its directory. Returns the renamed document — the
     * caller MUST verify the resulting name matches what it asked for
     * (providers are allowed to alter names) and treat a mismatch as failure.
     */
    fun renameDocument(doc: SafDoc, newName: String): SafDoc

    /** Move [doc] from [sourceParent] to [targetParent] (same provider tree). */
    fun moveDocument(doc: SafDoc, sourceParent: SafDoc, targetParent: SafDoc): SafDoc

    /** Delete [doc]; directories delete recursively (the provider's job). */
    fun deleteDocument(doc: SafDoc)

    /** Streaming read of a regular file. Caller closes. */
    fun openInputStream(doc: SafDoc): InputStream

    /** Streaming write; [truncate] opens "w" (existing content discarded). */
    fun openOutputStream(doc: SafDoc, truncate: Boolean): OutputStream

    /** The granted root folder's own display name (e.g. "MyProject"), or null. */
    fun rootDisplayName(): String?
}

/** One SAF document as the area sees it — no android types. */
data class SafDoc(
    /** Opaque provider document id (never shown to the user). */
    val id: String,
    /** Display name (what the explorer lists — never a POSIX path). */
    val name: String,
    val isDirectory: Boolean,
    /** Bytes, when the provider reports one (null = unknown, honestly). */
    val sizeBytes: Long?,
    /** Epoch millis, when the provider reports one (null = unknown). */
    val modifiedAtMillis: Long?,
)

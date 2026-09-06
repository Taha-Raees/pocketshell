package app.pocketshell.files.saf

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream

/**
 * M7.0.0 Phase 5 — the production [DocumentBackend]: a thin adapter from the
 * seam to android.provider.DocumentsContract + ContentResolver.
 *
 * Deliberately DUMB: no caching, no retry, no capability invention — every
 * call maps 1:1 onto a framework call and lets its exceptions propagate to
 * [AndroidDocumentArea], which owns all error mapping and revocation
 * handling. Queries project exactly the four document columns the explorer
 * model needs.
 *
 * URI BOUNDARY: document URIs are always built with
 * [DocumentsContract.buildDocumentUriUsingTree] against the granted tree —
 * the tree grant is what carries the permission. A document URI is never
 * exposed as a path anywhere; it never leaves this class.
 */
class DocumentsContractBackend(
    private val resolver: ContentResolver,
    private val treeUri: Uri,
) : DocumentBackend {

    companion object {

        /** Build from an application context and a persisted tree URI string. */
        fun fromContext(context: Context, treeUri: String): DocumentsContractBackend =
            DocumentsContractBackend(context.applicationContext.contentResolver, Uri.parse(treeUri))

        /**
         * The display name of ONE document URI (the import flow's picked
         * file). Null when unreadable/absent — the caller turns that into an
         * honest error, never a guessed name.
         */
        fun queryDisplayName(resolver: ContentResolver, documentUri: Uri): String? = try {
            resolver.query(
                documentUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
    )

    private fun documentUri(docId: String): Uri =
        DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)

    private fun docFromCursor(cursor: android.database.Cursor): SafDoc {
        val id = cursor.getString(0)
        val name = cursor.getString(1) ?: id
        val mime = cursor.getString(2)
        val size = if (cursor.isNull(3)) null else cursor.getLong(3)
        val modified = if (cursor.isNull(4)) null else cursor.getLong(4)
        return SafDoc(
            id = id,
            name = name,
            isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR,
            sizeBytes = size?.takeIf { it >= 0 },
            modifiedAtMillis = modified,
        )
    }

    private fun queryDoc(uri: Uri): SafDoc = resolver.query(uri, projection, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) docFromCursor(cursor) else throw FileNotFoundException(uri.toString())
    } ?: throw FileNotFoundException(uri.toString())

    override fun root(): SafDoc = queryDoc(documentUri(DocumentsContract.getTreeDocumentId(treeUri)))

    override fun resolveChild(parent: SafDoc, name: String): SafDoc? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            parent.id,
        ) ?: throw FileNotFoundException("the provider refused a child lookup for ${parent.name}")
        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val doc = docFromCursor(cursor)
                if (doc.name == name) return doc
            }
        } ?: throw FileNotFoundException(childrenUri.toString())
        return null
    }

    override fun listChildren(parent: SafDoc): List<SafDoc> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            parent.id,
        ) ?: throw FileNotFoundException("the provider refused a child lookup for ${parent.name}")
        val result = mutableListOf<SafDoc>()
        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                result.add(docFromCursor(cursor))
            }
        } ?: throw FileNotFoundException(childrenUri.toString())
        return result
    }

    override fun createDirectory(parent: SafDoc, name: String): SafDoc = queryDoc(
        DocumentsContract.createDocument(
            resolver,
            documentUri(parent.id),
            DocumentsContract.Document.MIME_TYPE_DIR,
            name,
        ) ?: throw FileNotFoundException("the provider refused to create the folder '$name'"),
    )

    override fun createFile(parent: SafDoc, name: String, mimeType: String): SafDoc = queryDoc(
        DocumentsContract.createDocument(resolver, documentUri(parent.id), mimeType, name)
            ?: throw FileNotFoundException("the provider refused to create the file '$name'"),
    )

    override fun renameDocument(doc: SafDoc, newName: String): SafDoc = queryDoc(
        DocumentsContract.renameDocument(resolver, documentUri(doc.id), newName)
            ?: throw FileNotFoundException("the provider refused the rename to '$newName'"),
    )

    override fun moveDocument(doc: SafDoc, sourceParent: SafDoc, targetParent: SafDoc): SafDoc = queryDoc(
        DocumentsContract.moveDocument(
            resolver,
            documentUri(doc.id),
            documentUri(sourceParent.id),
            documentUri(targetParent.id),
        ) ?: throw FileNotFoundException("the provider refused the move of ${doc.name}"),
    )

    override fun deleteDocument(doc: SafDoc) {
        if (!DocumentsContract.deleteDocument(resolver, documentUri(doc.id))) {
            throw FileNotFoundException(doc.name)
        }
    }

    override fun openInputStream(doc: SafDoc): InputStream =
        resolver.openInputStream(documentUri(doc.id))
            ?: throw FileNotFoundException(doc.name)

    override fun openOutputStream(doc: SafDoc, truncate: Boolean): OutputStream =
        resolver.openOutputStream(documentUri(doc.id), if (truncate) "w" else "wa")
            ?: throw FileNotFoundException(doc.name)

    override fun rootDisplayName(): String? = try {
        root().name
    } catch (_: Exception) {
        null
    }
}

package app.pocketshell.files.saf

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * TEST FIXTURE — an in-memory [DocumentBackend] standing in for
 * DocumentsContract + ContentResolver.
 *
 * Faithful to the framework where it matters for the area's contract:
 *  - every method throws [SecurityException] while [revoked] (the way a
 *    lost grant surfaces);
 *  - [failNextRename] makes exactly one rename fail (a provider refusing
 *    the swap — the path where a committed write must RESTORE the backup);
 *  - [alterRenameResults] simulates a provider that does not apply the
 *    requested name;
 *  - [rootLabel] can be hidden (null) the way exotic providers behave.
 */
class FakeDocumentBackend(rootName: String = "MyProject") : DocumentBackend {

    class Node(
        val id: String,
        var name: String,
        var isDirectory: Boolean,
        var parentId: String?,
        var content: ByteArray = ByteArray(0),
    )

    val nodes = LinkedHashMap<String, Node>()
    private var nextId = 1

    /** When true, every backend call throws SecurityException (revoked). */
    var revoked = false

    /** Makes the NEXT rename throw with this message (one-shot). */
    var failNextRename: String? = null

    /** Makes the FIRST rename TO this target name throw (the swap path —
     * one-shot so a subsequent restore rename can succeed). */
    var failRenameFor: String? = null

    /** Simulates a provider that renames to something else than requested. */
    var alterRenameResults = false

    /** The root folder's display name, or null when the provider hides it. */
    var rootLabel: String? = rootName

    init {
        nodes["root"] = Node("root", rootName, isDirectory = true, parentId = null)
    }

    // ------------------------------------------------------------ framework

    private fun checkRevoked() {
        if (revoked) throw SecurityException("Permission revoked")
    }

    private fun childId(parentId: String, name: String): String? =
        nodes.values.firstOrNull { it.parentId == parentId && it.name == name }?.id

    private fun toDoc(node: Node): SafDoc = SafDoc(
        id = node.id,
        name = node.name,
        isDirectory = node.isDirectory,
        sizeBytes = if (node.isDirectory) null else node.content.size.toLong(),
        modifiedAtMillis = 1_000L,
    )

    override fun root(): SafDoc {
        checkRevoked()
        return toDoc(nodes.getValue("root"))
    }

    override fun resolveChild(parent: SafDoc, name: String): SafDoc? {
        checkRevoked()
        return childId(parent.id, name)?.let { toDoc(nodes.getValue(it)) }
    }

    override fun listChildren(parent: SafDoc): List<SafDoc> {
        checkRevoked()
        return nodes.values.filter { it.parentId == parent.id }.map { toDoc(it) }
    }

    override fun createDirectory(parent: SafDoc, name: String): SafDoc {
        checkRevoked()
        if (childId(parent.id, name) != null) throw IllegalStateException("already exists")
        val id = "n${nextId++}"
        nodes[id] = Node(id, name, isDirectory = true, parentId = parent.id)
        return toDoc(nodes.getValue(id))
    }

    override fun createFile(parent: SafDoc, name: String, mimeType: String): SafDoc {
        checkRevoked()
        if (childId(parent.id, name) != null) throw IllegalStateException("already exists")
        val id = "n${nextId++}"
        nodes[id] = Node(id, name, isDirectory = false, parentId = parent.id)
        return toDoc(nodes.getValue(id))
    }

    override fun renameDocument(doc: SafDoc, newName: String): SafDoc {
        checkRevoked()
        failRenameFor?.let { target ->
            if (newName == target) {
                failRenameFor = null
                throw RuntimeException("provider refuses '$target'")
            }
        }
        failNextRename?.let { message ->
            failNextRename = null
            throw RuntimeException(message)
        }
        val node = nodes.getValue(doc.id)
        node.name = newName
        val result = toDoc(node)
        return if (alterRenameResults) result.copy(name = "$newName (1)") else result
    }

    override fun moveDocument(doc: SafDoc, sourceParent: SafDoc, targetParent: SafDoc): SafDoc {
        checkRevoked()
        val node = nodes.getValue(doc.id)
        node.parentId = targetParent.id
        return toDoc(node)
    }

    override fun deleteDocument(doc: SafDoc) {
        checkRevoked()
        fun purge(id: String) {
            nodes.values.filter { it.parentId == id }.map { it.id }.toList().forEach { purge(it) }
            nodes.remove(id)
        }
        purge(doc.id)
    }

    override fun openInputStream(doc: SafDoc): InputStream {
        checkRevoked()
        val node = nodes.getValue(doc.id)
        return ByteArrayInputStream(node.content)
    }

    override fun openOutputStream(doc: SafDoc, truncate: Boolean): OutputStream =
        object : java.io.ByteArrayOutputStream() {
            override fun close() {
                val node = nodes[doc.id]
                if (node != null) node.content = toByteArray()
            }
        }

    override fun rootDisplayName(): String? = rootLabel

    // --------------------------------------------------- test conveniences

    /** Walk-or-create directories under the root; returns the final doc. */
    fun mkdir(path: String): SafDoc {
        var parentId = "root"
        for (component in path.split('/').filter { it.isNotEmpty() }) {
            val existing = childId(parentId, component)
            parentId = existing
                ?: createDirectory(toDoc(nodes.getValue(parentId)), component).id
        }
        return toDoc(nodes.getValue(parentId))
    }

    /** Create parent directories, then a file with [bytes] content. */
    fun write(path: String, bytes: ByteArray): SafDoc {
        val parentPath = path.substringBeforeLast('/', "")
        val name = path.substringAfterLast('/')
        val parentDoc = if (parentPath.isEmpty()) root() else mkdir(parentPath)
        val doc = createFile(parentDoc, name, "application/octet-stream")
        nodes.getValue(doc.id).content = bytes
        return doc
    }

    /** The content at [path], or null when absent (or a directory). */
    fun content(path: String): ByteArray? {
        var parentId: String? = "root"
        for (component in path.split('/').filter { it.isNotEmpty() }) {
            parentId = parentId?.let { childId(it, component) } ?: return null
        }
        val node = parentId?.let { nodes[it] } ?: return null
        return if (node.isDirectory) null else node.content
    }

    /** True when something (file or directory) exists at [path]. */
    fun exists(path: String): Boolean {
        var parentId: String? = "root"
        for (component in path.split('/').filter { it.isNotEmpty() }) {
            parentId = parentId?.let { childId(it, component) } ?: return false
        }
        return parentId != null
    }
}

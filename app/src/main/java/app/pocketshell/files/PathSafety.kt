package app.pocketshell.files

import java.io.File
import java.nio.file.FileVisitOption
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.util.EnumSet

/**
 * M7.0.0 Phase 2 — pure safety primitives shared by every file-backed area.
 *
 * Everything here exists so the safety rules (traversal rejection, canonical
 * containment, NOFOLLOW recursion, refuse-symlink-parents) live in exactly ONE
 * place instead of being re-derived per implementation. The patterns mirror
 * the hardened walk/replace code the M6 runtime layer earned in the Phase-C
 * audit (C12/F1) — reused as a discipline, NOT by touching frozen files.
 */
object PathSafety {

    /** Hard lexical cap for an area path (Linux itself allows 4096). */
    private const val MAX_PATH_LENGTH = 4096

    /** Hard lexical cap for one name component (typical filesystem limit). */
    private const val MAX_NAME_LENGTH = 255

    // ------------------------------------------------------------- validation

    /**
     * Validate + canonicalize an area-native absolute path.
     *
     * Accepted: "/", and absolute paths of non-empty components where no
     * component is "." or ".." (traversal is REJECTED, not resolved — the UI
     * only ever receives canonical paths from listings, so nothing legitimate
     * ever needs dot components).
     *
     * Rejected: null/blank, NUL bytes, relative paths, empty components
     * (double/trailing slashes), any "." / ".." component, over-long paths.
     */
    fun validatePath(raw: String?): AreaPath? {
        if (raw.isNullOrEmpty()) return null
        if (raw.length > MAX_PATH_LENGTH) return null
        if (raw.indexOf('\u0000') >= 0) return null
        if (!raw.startsWith("/")) return null
        if (raw == "/") return AreaPath.unchecked("/")
        val components = raw.split('/')
        if (components.first().isNotEmpty()) return null // must start with '/'
        for (component in components.drop(1)) {
            if (component.isEmpty()) return null
            if (component == "." || component == "..") return null
        }
        return AreaPath.unchecked("/" + components.drop(1).joinToString("/"))
    }

    /**
     * Validate a bare file/directory NAME (one component, never a path).
     * Dotfiles are legitimate (".env" is an M7 editor example); traversal,
     * separators, NUL and blanks are not.
     */
    fun validateName(raw: String?): String? {
        if (raw.isNullOrEmpty()) return null
        if (raw.isBlank()) return null
        if (raw.length > MAX_NAME_LENGTH) return null
        if (raw.indexOf('\u0000') >= 0) return null
        if (raw.contains('/')) return null
        if (raw == "." || raw == "..") return null
        return raw
    }

    /** True when [candidate] (canonical) equals [root] or lies underneath it. */
    fun isInside(root: String, candidate: String): Boolean =
        candidate == root || candidate.startsWith(root + "/")

    // --------------------------------------------------------- lstat helpers

    fun existsNoFollow(file: File): Boolean =
        Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS)

    fun isSymbolicLink(file: File): Boolean = Files.isSymbolicLink(file.toPath())

    /** Raw symlink target string, or null when not a symlink / unreadable. */
    fun symlinkTarget(file: File): String? = try {
        Files.readSymbolicLink(file.toPath()).toString()
    } catch (_: Exception) {
        null
    }

    /**
     * NOFOLLOW kind of a node: symlinks stay symlinks (never classified by
     * what they point at).
     */
    fun kindOf(file: File): EntryKind = when {
        Files.isSymbolicLink(file.toPath()) -> EntryKind.SYMLINK
        Files.isDirectory(file.toPath(), LinkOption.NOFOLLOW_LINKS) -> EntryKind.DIRECTORY
        Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS) -> EntryKind.FILE
        else -> EntryKind.OTHER
    }

    // ------------------------------------------------------ structural guards

    /**
     * The refuse-symlink-parents guard (fail-closed). Walks every intermediate
     * component of [path] from [root]: each must be a REAL directory. A
     * symlinked component (e.g. /root/link -> /somewhere, then operating on
     * /root/link/x) refuses the whole operation — the host-side resolution of
     * such a link is not the guest's view and must never be traversed.
     *
     * Returns the human-readable refusal reason, or null when every parent is
     * a real directory.
     */
    fun parentsProblem(root: File, path: AreaPath): String? {
        val components = path.components
        if (components.isEmpty()) return null
        var current = root
        for (index in 0 until components.size - 1) {
            val component = components[index]
            current = File(current, component)
            if (isSymbolicLink(current)) {
                return "path component '${component}' in ${path.value} is a symlink — " +
                    "refusing to operate through it"
            }
            if (!Files.isDirectory(current.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                return "directory '${component}' in ${path.value} does not exist or is not a directory"
            }
        }
        return null
    }

    /**
     * NOFOLLOW recursive delete: files and symlinks are deleted as nodes,
     * directories are walked WITHOUT following links (a symlinked directory
     * inside the tree is deleted as a node, its target untouched). This is the
     * only delete primitive an area may use — File.deleteRecursively is
     * FORBIDDEN for guest storage (it follows directory symlinks; the C12/F1
     * audit proved that wipes targets).
     */
    fun deleteTreeNoFollow(target: File): Boolean {
        if (!existsNoFollow(target)) return true
        return try {
            Files.walkFileTree(
                target.toPath(),
                EnumSet.noneOf(FileVisitOption::class.java),
                Int.MAX_VALUE,
                object : SimpleFileVisitor<Path>() {
                    override fun visitFile(
                        file: Path,
                        attrs: java.nio.file.attribute.BasicFileAttributes,
                    ): FileVisitResult {
                        Files.delete(file) // symlink nodes land here, target untouched
                        return FileVisitResult.CONTINUE
                    }

                    override fun postVisitDirectory(
                        dir: Path,
                        exc: java.io.IOException?,
                    ): FileVisitResult {
                        Files.delete(dir)
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFileFailed(
                        file: Path,
                        exc: java.io.IOException?,
                    ): FileVisitResult {
                        try {
                            Files.delete(file)
                        } catch (_: Exception) {
                            // best-effort; the overall result reports honestly
                        }
                        return FileVisitResult.CONTINUE
                    }
                },
            )
            true
        } catch (_: Exception) {
            false
        }
    }
}

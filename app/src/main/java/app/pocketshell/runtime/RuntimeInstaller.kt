package app.pocketshell.runtime

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

/**
 * The real install pipeline (docs/M2-ARCHITECTURE §5):
 * DOWNLOADING → VERIFYING → EXTRACTING → CONFIGURING → atomic promotion → READY.
 *
 * Every failure cleans transient state and leaves any existing runtime
 * untouched. All work happens on [Dispatchers.IO]. Progress events reflect
 * real work only — bytes read, bytes hashed, entries extracted.
 */
class RuntimeInstaller(
    private val storage: RuntimeStorage,
    /** Injectable for unit tests; production uses the default HTTPS opener. */
    private val connectionOpener: (String) -> HttpURLConnection = ::openHttpsConnection,
) {

    /** Pinned, checksum-verified rootfs artifact (Master Prompt §10). */
    data class RootfsSpec(
        val url: String,
        val expectedSha256: String,
        val expectedSizeBytes: Long,
        val distribution: String,
        val distributionVersion: String,
        val architecture: String,
    )

    class InstallException(val stage: RuntimeState, message: String, cause: Throwable? = null) :
        IOException(message, cause)

    /**
     * Runs the full pipeline. Returns the promoted metadata.
     * Emits [RuntimeInstallEvent]s and stage transitions as real work progresses.
     */
    suspend fun install(
        spec: RootfsSpec,
        onEvent: (RuntimeInstallEvent) -> Unit,
        onState: (RuntimeState) -> Unit,
    ): RuntimeMetadata = withContext(Dispatchers.IO) {
        var stage = RuntimeState.DOWNLOADING

        try {
            // ---- DOWNLOADING -------------------------------------------------
            enter(stage, onState)
            storage.cleanupTransient()
            storage.downloadTmp.parentFile?.mkdirs()
            download(spec, onEvent)

            // ---- VERIFYING ---------------------------------------------------
            stage = RuntimeState.VERIFYING
            enter(stage, onState)
            verify(spec, onEvent)

            // ---- EXTRACTING --------------------------------------------------
            stage = RuntimeState.EXTRACTING
            enter(stage, onState)
            val entryCount = extract(onEvent)

            // ---- CONFIGURING -------------------------------------------------
            stage = RuntimeState.CONFIGURING
            enter(stage, onState)
            configureAndWriteMetadata(spec, entryCount)

            // ---- PROMOTE (atomic) --------------------------------------------
            storage.promoteStagedToRuntime()
            val promoted = RuntimeMetadata.read(storage.metadataFile)
                ?: throw InstallException(
                    RuntimeState.CONFIGURING,
                    "promoted runtime.json unreadable",
                )
            check(promoted.rootfsSha256 == spec.expectedSha256) {
                "promoted metadata checksum mismatch"
            }

            stage = RuntimeState.READY
            enter(stage, onState)
            onEvent(RuntimeInstallEvent.Ready)
            promoted
        } catch (e: CancellationException) {
            // Cancelled: leave nothing half-done; report honest not-installed.
            storage.cleanupTransient()
            enter(RuntimeState.NOT_INSTALLED, onState)
            throw e
        } catch (e: InstallException) {
            storage.cleanupTransient()
            enter(RuntimeState.FAILED, onState)
            onEvent(RuntimeInstallEvent.Failed(e.stage, e.message ?: "install failed"))
            throw e
        } catch (e: Exception) {
            storage.cleanupTransient()
            enter(RuntimeState.FAILED, onState)
            onEvent(RuntimeInstallEvent.Failed(stage, e.message ?: e.javaClass.simpleName))
            throw InstallException(stage, e.message ?: "install failed", e)
        }
    }

    // ---------------------------------------------------------------- stages

    private fun download(spec: RootfsSpec, onEvent: (RuntimeInstallEvent) -> Unit) {
        val connection = connectionOpener(spec.url)
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.connect()
            val response = connection.responseCode
            if (response !in 200..299) {
                throw InstallException(RuntimeState.DOWNLOADING, "HTTP $response for ${spec.url}")
            }
            val declaredLength = connection.contentLengthLong
            if (declaredLength > 0 && declaredLength != spec.expectedSizeBytes) {
                throw InstallException(
                    RuntimeState.DOWNLOADING,
                    "Content-Length $declaredLength != expected ${spec.expectedSizeBytes}",
                )
            }
            connection.inputStream.use { input ->
                storage.downloadTmp.outputStream().use { output ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                    var total = 0L
                    var sinceEvent = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read > 0) {
                            output.write(buffer, 0, read)
                            total += read
                            sinceEvent += read
                            if (sinceEvent >= PROGRESS_INTERVAL_BYTES) {
                                sinceEvent = 0
                                onEvent(
                                    RuntimeInstallEvent.DownloadProgress(
                                        total,
                                        if (declaredLength > 0) declaredLength else spec.expectedSizeBytes,
                                    ),
                                )
                            }
                        }
                    }
                    if (total != spec.expectedSizeBytes) {
                        throw InstallException(
                            RuntimeState.DOWNLOADING,
                            "downloaded $total bytes, expected ${spec.expectedSizeBytes}",
                        )
                    }
                    onEvent(
                        RuntimeInstallEvent.DownloadProgress(total, spec.expectedSizeBytes),
                    )
                }
            }
            onEvent(RuntimeInstallEvent.Downloaded)
        } finally {
            connection.disconnect()
        }
    }

    private fun verify(spec: RootfsSpec, onEvent: (RuntimeInstallEvent) -> Unit) {
        val actual = storage.downloadTmp.length()
        if (actual != spec.expectedSizeBytes) {
            throw InstallException(
                RuntimeState.VERIFYING,
                "archive size $actual != expected ${spec.expectedSizeBytes}",
            )
        }
        val hashed = AtomicLong(0)
        val digest = MessageDigest.getInstance("SHA-256")
        countingStream(storage.downloadTmp, hashed).use { counting ->
            val digestStream = DigestInputStream(counting, digest)
            val buffer = ByteArray(VERIFY_BUFFER_SIZE)
            while (true) {
                val read = digestStream.read(buffer)
                if (read < 0) break
                if (read >= PROGRESS_INTERVAL_BYTES) {
                    onEvent(RuntimeInstallEvent.VerifyProgress(hashed.get()))
                }
            }
        }
        val actualHex = digest.digest().joinToString("") { "%02x".format(it) }
        if (!RuntimeChecksum.matches(actualHex, spec.expectedSha256)) {
            throw InstallException(
                RuntimeState.VERIFYING,
                "sha256 mismatch: archive is not the pinned rootfs",
            )
        }
    }

    private fun extract(onEvent: (RuntimeInstallEvent) -> Unit): Int {
        val stagingRootfs = File(storage.extractTmp, RuntimeStorage.DIR_ROOTFS)
        if (!stagingRootfs.mkdirs() && !stagingRootfs.isDirectory) {
            throw InstallException(RuntimeState.EXTRACTING, "cannot create staging rootfs dir")
        }
        var count = 0
        try {
            TarArchiveInputStream(java.util.zip.GZIPInputStream(storage.downloadTmp.inputStream().buffered())).use { tar ->
                while (true) {
                    val entry = tar.nextTarEntry ?: break
                    val target = resolveSecure(stagingRootfs, entry.name)
                    when {
                        entry.isDirectory -> {
                            if (!Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
                                Files.createDirectories(target)
                            }
                        }

                        entry.isSymbolicLink -> createSymlink(stagingRootfs, entry, target)

                        entry.isLink -> createHardLink(stagingRootfs, entry, target)

                        entry.isFile -> extractFile(tar, entry, target)

                        else -> Unit // devices/fifos: not creatable unprivileged; proot supplies them
                    }
                    count++
                    if (count % EXTRACT_EVENT_EVERY == 0) {
                        onEvent(RuntimeInstallEvent.ExtractProgress(count, entry.name))
                    }
                }
            }
        } catch (e: InstallException) {
            throw e
        } catch (e: Exception) {
            throw InstallException(RuntimeState.EXTRACTING, e.message ?: "extraction failed", e)
        }
        onEvent(RuntimeInstallEvent.ExtractProgress(count, "(done)"))
        return count
    }

    private fun extractFile(tar: TarArchiveInputStream, entry: TarArchiveEntry, target: Path) {
        target.parent?.toFile()?.mkdirs()
        java.nio.file.Files.copy(tar, target, StandardCopyOption.REPLACE_EXISTING)
        applyMode(target, entry.mode)
    }

    private fun createSymlink(stagingRootfs: File, entry: TarArchiveEntry, target: Path) {
        target.parent?.toFile()?.mkdirs()
        // Guest-internal link targets may legitimately be absolute (e.g. /proc/...);
        // creating the link is contained — it only writes the link node itself.
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            deleteRecursivelyPath(target)
        }
        try {
            Files.createSymbolicLink(target, Path(entry.linkName))
        } catch (e: Exception) {
            throw InstallException(
                RuntimeState.EXTRACTING,
                "symlink failed: ${entry.name} -> ${entry.linkName}: ${e.message}",
                e,
            )
        }
    }

    private fun createHardLink(stagingRootfs: File, entry: TarArchiveEntry, target: Path) {
        val source = resolveSecure(stagingRootfs, entry.linkName)
        target.parent?.toFile()?.mkdirs()
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            deleteRecursivelyPath(target)
        }
        try {
            if (Files.exists(source, LinkOption.NOFOLLOW_LINKS)) {
                Files.createLink(target, source)
            } else {
                throw InstallException(
                    RuntimeState.EXTRACTING,
                    "hardlink target not yet extracted: ${entry.name} -> ${entry.linkName}",
                )
            }
        } catch (e: InstallException) {
            throw e
        } catch (e: Exception) {
            // Some filesystems (FUSE) refuse hardlinks — fall back to a copy so
            // the guest stays intact.
            try {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
                applyMode(target, entry.mode)
            } catch (e2: Exception) {
                throw InstallException(
                    RuntimeState.EXTRACTING,
                    "hardlink failed: ${entry.name}: ${e2.message}",
                    e2,
                )
            }
        }
    }

    private fun configureAndWriteMetadata(spec: RootfsSpec, entryCount: Int) {
        val stagingRootfs = File(storage.extractTmp, RuntimeStorage.DIR_ROOTFS)
        val release = File(stagingRootfs, "etc/alpine-release")
        val busybox = File(stagingRootfs, "bin/busybox")
        if (!release.isFile) {
            throw InstallException(
                RuntimeState.CONFIGURING,
                "etc/alpine-release missing after extraction (${entryCount} entries)",
            )
        }
        if (!busybox.isFile) {
            throw InstallException(
                RuntimeState.CONFIGURING,
                "bin/busybox missing after extraction",
            )
        }
        // Guest DNS: the minirootfs ships no /etc/resolv.conf (upstream leaves
        // it to the target machine), but musl's resolver needs one or every
        // guest name lookup fails (apk update). See GuestEnvironment KDoc —
        // never overwrites a file that already has content.
        if (!GuestEnvironment.ensureDnsResolvers(stagingRootfs)) {
            throw InstallException(
                RuntimeState.CONFIGURING,
                "could not write ${GuestEnvironment.RESOLV_CONF_RELATIVE} into the staging rootfs",
            )
        }
        val metadata = RuntimeMetadata(
            distribution = spec.distribution,
            distributionVersion = spec.distributionVersion,
            architecture = spec.architecture,
            installedAtEpochMs = System.currentTimeMillis(),
            rootfsSha256 = spec.expectedSha256,
            state = RuntimeState.READY.name,
        )
        RuntimeMetadata.write(File(storage.extractTmp, RuntimeStorage.FILE_METADATA), metadata)
    }

    // ---------------------------------------------------------------- utils

    /** Lexical path-containment guard (zip-slip). Checksum-verified archive + this = solid. */
    private fun resolveSecure(stagingRootfs: File, entryName: String): Path {
        if (entryName.isEmpty() || entryName.endsWith("/")) {
            // directory-style names still go through the same check below
        }
        if (Path(entryName).isAbsolute) {
            throw InstallException(RuntimeState.EXTRACTING, "absolute path in archive: $entryName")
        }
        val root = Path(stagingRootfs.absolutePath).normalize()
        val resolved = root.resolve(entryName).normalize()
        if (!resolved.startsWith(root)) {
            throw InstallException(RuntimeState.EXTRACTING, "path traversal in archive: $entryName")
        }
        return resolved
    }

    private fun applyMode(target: Path, mode: Int) {
        try {
            val perms = java.util.HashSet<java.nio.file.attribute.PosixFilePermission>()
            if (mode and 0b100_000_000 != 0) perms.add(java.nio.file.attribute.PosixFilePermission.OWNER_READ)
            if (mode and 0b010_000_000 != 0) perms.add(java.nio.file.attribute.PosixFilePermission.OWNER_WRITE)
            if (mode and 0b001_000_000 != 0) perms.add(java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE)
            if (mode and 0b000_100_000 != 0) perms.add(java.nio.file.attribute.PosixFilePermission.GROUP_READ)
            if (mode and 0b000_010_000 != 0) perms.add(java.nio.file.attribute.PosixFilePermission.GROUP_WRITE)
            if (mode and 0b000_001_000 != 0) perms.add(java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE)
            if (mode and 0b000_000_100 != 0) perms.add(java.nio.file.attribute.PosixFilePermission.OTHERS_READ)
            if (mode and 0b000_000_010 != 0) perms.add(java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE)
            if (mode and 0b000_000_001 != 0) perms.add(java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE)
            Files.setPosixFilePermissions(target, perms)
        } catch (_: Exception) {
            // Mode preservation is best-effort on exotic filesystems.
        }
    }

    private fun deleteRecursivelyPath(target: Path) {
        val file = target.toFile()
        if (file.isDirectory) file.deleteRecursively() else file.delete()
    }

    private fun countingStream(file: File, counter: AtomicLong): InputStream =
        object : InputStream() {
            val inner = file.inputStream().buffered()

            override fun read(): Int {
                val r = inner.read()
                if (r >= 0) counter.addAndGet(1)
                return r
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val r = inner.read(b, off, len)
                if (r > 0) counter.addAndGet(r.toLong())
                return r
            }

            override fun close() = inner.close()
        }

    private fun enter(state: RuntimeState, onState: (RuntimeState) -> Unit) {
        onState(state)
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val DOWNLOAD_BUFFER_SIZE = 64 * 1024
        private const val VERIFY_BUFFER_SIZE = 64 * 1024
        private const val PROGRESS_INTERVAL_BYTES = 512 * 1024L
        private const val EXTRACT_EVENT_EVERY = 250

        fun openHttpsConnection(url: String): HttpURLConnection {
            val connection = URL(url).openConnection() as HttpURLConnection
            return connection
        }

        /** JSON used only for diagnostics formatting inside tests/tools. */
        val prettyJson = Json { prettyPrint = true }
    }
}

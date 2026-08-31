package app.pocketshell.runtime

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * On-disk runtime metadata — `runtime/` + `runtime.json` (Master Prompt §31).
 *
 * Written LAST inside the staging root, before atomic promotion, and read
 * back after promotion. An unreadable or missing file means the runtime is
 * structurally untrustworthy → [RuntimeState.REPAIR_REQUIRED].
 */
@Serializable
data class RuntimeMetadata(
    /** Schema version of this file itself — bump on incompatible changes. */
    @SerialName("runtimeVersion") val runtimeVersion: Int = SCHEMA_VERSION,
    @SerialName("distribution") val distribution: String,
    @SerialName("distributionVersion") val distributionVersion: String,
    /** Guest architecture, e.g. "aarch64". */
    @SerialName("architecture") val architecture: String,
    @SerialName("installedAtEpochMs") val installedAtEpochMs: Long,
    /** SHA-256 of the rootfs archive that produced this runtime. */
    @SerialName("rootfsSha256") val rootfsSha256: String,
    /** [RuntimeState] name at write time (expected: READY). */
    @SerialName("state") val state: String,
) {
    fun toJson(): String = JSON.encodeToString(this)

    companion object {
        const val SCHEMA_VERSION: Int = 1

        private val JSON = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        /** Parse from file; any I/O or syntax problem yields null (never throws). */
        fun read(file: File): RuntimeMetadata? = try {
            JSON.decodeFromString<RuntimeMetadata>(file.readText())
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: java.io.IOException) {
            null
        }

        /** Serialize + write atomically-ish (tmp file + rename within same dir). */
        fun write(file: File, metadata: RuntimeMetadata) {
            val tmp = File(file.parentFile, file.name + ".write")
            tmp.writeText(metadata.toJson())
            if (!tmp.renameTo(file)) {
                tmp.delete()
                throw java.io.IOException("runtime.json rename failed: ${file.path}")
            }
        }
    }
}

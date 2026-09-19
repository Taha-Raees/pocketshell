package app.pocketshell.widget.external

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * M8 — strict manifest validation. Everything a catalog could ever hand us
 * passes through here before a renderer ever sees it: unknown JSON keys are
 * REJECTED (no schema drift), ids/versions are shape-checked, and the probe
 * must be a BUILT-IN primitive whose capability is declared. Malformed
 * metadata can only produce a rejection — never a partially trusted widget.
 */
object WidgetManifestValidator {

    sealed interface Result {
        data class Valid(val manifest: WidgetManifest) : Result
        data class Invalid(val reasons: List<String>) : Result
    }

    /** The exact built-in probe primitives v1 understands. */
    val probeKinds: Set<String> = setOf(PROC_NET_LISTEN, STORAGE_ROOTFS, SSH_GUEST)

    const val PROC_NET_LISTEN = "proc.net.listen"
    const val STORAGE_ROOTFS = "storage.rootfs"

    /**
     * M8.4.4 — the guest SSH surface as a primitive: own-UID `ssh` client
     * argv + the guest's `~/.ssh/config` host aliases (fixed app code, the
     * compiled SshApp's probe — nothing is connected, nothing is executed).
     */
    const val SSH_GUEST = "ssh.guest"

    /** The exact capability vocabulary a manifest may declare. */
    val capabilities: Set<String> = setOf(
        "proc.net",          // reads the app-UID procfs network tables
        "storage.rootfs",    // walks the app-owned Linux storage tree
        "guest.ready",       // only meaningful while the guest is READY
    )

    /** Strict JSON: unknown keys reject (schema drift is a rejection). */
    private val strictJson: Json = Json {
        ignoreUnknownKeys = false
        encodeDefaults = false
    }

    fun parse(text: String): Result = try {
        validate(strictJson.decodeFromString(WidgetManifest.serializer(), text))
    } catch (error: SerializationException) {
        Result.Invalid(listOf("malformed manifest: ${error.message ?: "unparseable"}"))
    } catch (error: IllegalArgumentException) {
        Result.Invalid(listOf("malformed manifest: ${error.message ?: "unparseable"}"))
    }

    fun validate(manifest: WidgetManifest): Result {
        val reasons = mutableListOf<String>()

        if (!manifest.id.matches(Regex("""[a-z][a-z0-9-]{1,31}"""))) {
            reasons.add("id must match [a-z][a-z0-9-]{1,31}")
        }
        if (manifest.name.isEmpty() || manifest.name.length > 24 ||
            manifest.name.any { it.isISOControl() }
        ) {
            reasons.add("name must be 1-24 printable characters")
        }
        if (!manifest.version.matches(Regex("""\d+\.\d+\.\d+"""))) {
            reasons.add("version must be semver (MAJOR.MINOR.PATCH)")
        }
        if (!manifest.minAppVersion.matches(Regex("""\d+\.\d+\.\d+"""))) {
            reasons.add("minAppVersion must be semver")
        }
        if (manifest.summary.length > 80) reasons.add("summary must be ≤80 characters")
        if (manifest.author.length > 40) reasons.add("author must be ≤40 characters")

        val unknownCaps = manifest.capabilities.filter { it !in capabilities }
        if (unknownCaps.isNotEmpty()) reasons.add("unknown capabilities: $unknownCaps")

        if (manifest.probe.kind !in probeKinds) {
            reasons.add("probe.kind must be a built-in primitive: $probeKinds")
        } else {
            val requiredCap = when (manifest.probe.kind) {
                PROC_NET_LISTEN -> "proc.net"
                SSH_GUEST -> "guest.ready"
                else -> "storage.rootfs"
            }
            if (requiredCap !in manifest.capabilities) {
                reasons.add("probe '${manifest.probe.kind}' requires capability '$requiredCap'")
            }
            reasons.addAll(validateParams(manifest.probe))
        }

        if (manifest.card.maxLines !in 1..6) reasons.add("card.maxLines must be 1..6")
        if (manifest.card.itemTemplate.length > 64) reasons.add("card.itemTemplate must be ≤64 characters")
        if (manifest.card.emptyLine.isEmpty() || manifest.card.emptyLine.length > 40) {
            reasons.add("card.emptyLine must be 1-40 characters")
        }
        if (manifest.card.headline.length > 24) reasons.add("card.headline must be ≤24 characters")

        return if (reasons.isEmpty()) Result.Valid(manifest) else Result.Invalid(reasons)
    }

    /** Per-primitive parameter confinement. Every param value is bounded. */
    private fun validateParams(probe: WidgetManifest.Probe): List<String> {
        val reasons = mutableListOf<String>()
        val allowed: Set<String> = when (probe.kind) {
            PROC_NET_LISTEN -> emptySet()
            STORAGE_ROOTFS -> emptySet()
            SSH_GUEST -> emptySet()
            else -> return reasons
        }
        val unknown = probe.params.keys.filter { it !in allowed }
        if (unknown.isNotEmpty()) reasons.add("unknown probe params for '${probe.kind}': $unknown")
        probe.params.values.filter { it.length > 64 }.forEach {
            reasons.add("probe param values must be ≤64 characters")
        }
        return reasons
    }
}

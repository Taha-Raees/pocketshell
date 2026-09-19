package app.pocketshell.widget.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * M8.4 — the SYNC/BACKUP application's data model (pure, JVM-tested).
 *
 * A profile is a RECORD of an intent: "copy [source] to [destination]
 * with [backend]". It is NOT a backup — nothing here, or anywhere in this
 * package, ever claims that data was copied. v1 performs dry-run previews
 * only; the run fields ([lastRunMs], [lastRunSummary]) exist so a future
 * real-run can record FACTS, and stay null until then ("never run").
 *
 * Secrets are structurally absent: a destination may be an ssh remote
 * STRING ("user@host:/path") that references the user's existing guest
 * ~/.ssh setup, but no credential field exists to fill — there is no
 * password, no key path and no key material anywhere in this model.
 */
@Serializable
data class SyncProfile(
    val id: String,
    val backend: SyncBackend,
    val source: String,
    val destination: String,
    val createdAtMs: Long,
    /** Real-run record; null = this card has never run this profile. */
    val lastRunMs: Long? = null,
    /** Real-run outcome summary (facts only); null alongside [lastRunMs]. */
    val lastRunSummary: String? = null,
)

/** The v1 backends — both exist in Alpine 3.24 aarch64 (verified). */
@Serializable
enum class SyncBackend {
    RSYNC,
    RCLONE,
}

/**
 * The pure profile domain: validation, classification, ordering. String in
 * / data out, no I/O (the contract-test pin keeps the exec layer away).
 */
object SyncProfiles {

    /** An "arbitrary reasonable number" of profiles for one Home card. */
    const val MAX_PROFILES = 6

    /** Path length cap — a guest path or remote spec, not a document. */
    const val SPEC_MAX_LENGTH = 512

    /**
     * The honest input gate. Returns the problem text, or null when the
     * pair is acceptable. Leading "-" is rejected outright: the specs are
     * passed to rsync/rclone as argv elements, and a leading-dash element
     * would be parsed as an OPTION — this is the injection guard, and it
     * also keeps every path displayable verbatim.
     */
    fun validate(source: String, destination: String): String? {
        val src = source.trim()
        val dst = destination.trim()
        return when {
            src.isEmpty() -> "source is required"
            dst.isEmpty() -> "destination is required"
            src.startsWith("-") || dst.startsWith("-") ->
                "paths must not start with \"-\""
            src.length > SPEC_MAX_LENGTH || dst.length > SPEC_MAX_LENGTH ->
                "paths are limited to $SPEC_MAX_LENGTH characters"
            src == dst -> "source and destination are the same path"
            else -> null
        }
    }

    /** Insert-or-replace by id, capped — the repository's whole write logic. */
    fun upsert(profiles: List<SyncProfile>, profile: SyncProfile): List<SyncProfile> {
        val others = profiles.filterNot { it.id == profile.id }
        return (others + profile).takeLast(MAX_PROFILES)
    }

    /**
     * Remote or guest-local? rsync treats "host:path" / "user@host:path"
     * as remote; rclone treats "remote:path" as its configured remote. The
     * heuristic: a colon in the first segment (before any "/") means
     * remote. A local guest path with a colon in its first directory name
     * would be misread — exotic, and the probe/dry-run report the REAL
     * answer anyway (this classification only picks which honesty the
     * overview line shows; [SyncProbe] never trusts it).
     */
    fun isRemote(spec: String): Boolean {
        val firstSlash = spec.indexOf('/')
        val head = if (firstSlash < 0) spec else spec.substring(0, firstSlash)
        return head.contains(':')
    }

    /**
     * Guest paths for humans: the guest home shows as "~", everything else
     * passes through verbatim — never reinterpreted (the GitApp rule).
     */
    fun displayPath(path: String, guestHome: String = "/root"): String = when {
        path == guestHome -> "~"
        path.startsWith("$guestHome/") -> "~" + path.removePrefix(guestHome)
        else -> path
    }

    /** One-line "src → dst" for the overview row. */
    fun line(profile: SyncProfile): String =
        "${displayPath(profile.source)} → ${displayPath(profile.destination)}"
}

/**
 * The JSON codec for the profile list — pure String in / List out (the
 * established codec discipline, mirroring [app.pocketshell.widget.HomeAppIdCodec]
 * and the todo store): sanitize, cap, honest degradation.
 */
object SyncStoreCodec {

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(profiles: List<SyncProfile>): String =
        json.encodeToString(ListSerializer(SyncProfile.serializer()), profiles.take(SyncProfiles.MAX_PROFILES))

    /**
     * Corrupt/absent → EMPTY (never invented profiles). Well-shaped but
     * dirty records are sanitized: blank source/destination dropped, specs
     * trimmed, leading-dash specs dropped (they are refused at creation —
     * a record that arrives with one cannot be probed honestly), duplicate
     * ids deduped (first wins), capped. Unknown JSON keys are ignored, so
     * an older app reading a newer store degrades instead of crashing.
     */
    fun decode(raw: String?): List<SyncProfile> {
        val parsed = raw?.let {
            try {
                json.decodeFromString(ListSerializer(SyncProfile.serializer()), it)
            } catch (_: Exception) {
                null
            }
        } ?: emptyList()
        return parsed
            .map { it.copy(source = it.source.trim(), destination = it.destination.trim()) }
            .filter { it.source.isNotEmpty() && it.destination.isNotEmpty() }
            .filter { !it.source.startsWith("-") && !it.destination.startsWith("-") }
            .distinctBy { it.id }
            .take(SyncProfiles.MAX_PROFILES)
    }
}

package app.pocketshell.widget.storage

import app.pocketshell.packages.ExecResult

/**
 * M8.4 STORAGE — the guest-side cache probe. ONE batched, READ-ONLY guest
 * exec (`du -sk` over a fixed existence-checked list) reports how much the
 * common caches inside the Linux guest occupy. The GitProbe discipline,
 * copied: marker-line protocol, one proot spawn per probe, honest
 * degradation (a real failure is never dressed up as "no caches"), and a
 * time-based idle gate so an open card re-exec at most once per
 * AUTO_RESCAN_MS.
 *
 * Read-only by contract: `du` measures, it never deletes. v1 deliberately
 * does NOT clear guest caches from this card — deletion semantics belong
 * to a terminal the user controls. The probe script contains no mutating
 * command at all (pinned by StorageAppContractTest).
 *
 * Honesty about the numbers: every probed path lives INSIDE the rootfs, so
 * these sizes are a BREAKDOWN of the runtime total, not additional space.
 * The guest's /var/cache/apk is deliberately NOT probed: proot binds it to
 * the host-side apk-cache category — listing it here would double-count.
 */
internal class GuestCacheProbe(
    private val exec: GuestExec,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    /** Runs one guest command (argv tail after proot) and waits, bounded. */
    fun interface GuestExec {
        fun exec(guestCommand: List<String>): ExecResult
    }

    sealed interface ProbeResult {
        /** Real measured sizes (a missing cache dir simply yields no row). */
        data class Done(val entries: List<GuestCacheEntry>) : ProbeResult

        /** The real failure text — never mapped to "no caches". */
        data class Failed(val reason: String) : ProbeResult
    }

    private val lock = Any()
    private var hasProbed = false
    private var lastProbeAtMs: Long = 0L
    private var lastResult: ProbeResult? = null

    /**
     * The idle gate (GitProbe's shape): a probe request that fires too soon
     * after the last one does nothing at all — no guest exec, no cost.
     */
    fun shouldProbe(nowMs: Long): Boolean = synchronized(lock) {
        !hasProbed || nowMs - lastProbeAtMs >= AUTO_RESCAN_MS
    }

    /** The most recent probe result, if any (for instant re-render). */
    val cachedResult: ProbeResult?
        get() = synchronized(lock) { lastResult }

    /** Forget the gate + cache — the manual REFRESH path forces a real exec. */
    fun invalidate() = synchronized(lock) {
        hasProbed = false
        lastResult = null
    }

    /**
     * One probe pass. Blocking (proot spawn + du) — callers wrap in
     * Dispatchers.IO, exactly like GitProbe.snapshot().
     */
    fun snapshot(): ProbeResult = synchronized(lock) {
        hasProbed = true
        lastProbeAtMs = clock()
        val result: ProbeResult = try {
            val out = exec.exec(listOf("/bin/sh", "-c", PROBE_SCRIPT, "sh"))
            when {
                !out.success && out.error != null -> ProbeResult.Failed(out.error!!)
                !out.success -> ProbeResult.Failed(
                    out.stderr.lineSequence().lastOrNull { it.isNotBlank() }
                        ?: "guest exec exited with ${out.exitCode}",
                )
                else -> {
                    val parsed = parseCacheOutput(out.stdout)
                    when {
                        parsed == null -> ProbeResult.Failed("unrecognized probe output")
                        !parsed.complete -> ProbeResult.Failed("probe output truncated")
                        else -> ProbeResult.Done(parsed.entries)
                    }
                }
            }
        } catch (t: Throwable) {
            ProbeResult.Failed(t.message ?: t.javaClass.simpleName)
        }
        lastResult = result
        result
    }

    companion object {

        /** Bounded: a full guest round-trip (proot spawn + a handful of du). */
        const val SCAN_TIMEOUT_MS = 15_000L

        /** Minimum space between guest execs on an open, watched card. */
        const val AUTO_RESCAN_MS = 60_000L

        /** The lone "$" — the probe script is a shell script, not a template. */
        private const val D = "$"

        /**
         * THE one batched script for a whole guest probe (busybox ash, Alpine):
         * for each well-known cache dir under $HOME (plus /tmp), print one
         * "@@CACHE:name:kilobytes" marker ONLY when the dir exists — absence
         * is honest silence, never a zero. A failed du yields "?" (unknown).
         * Terminal "@@DONE" + `exit 0`: a completed scan is a successful
         * probe no matter what any single du printed (v0.4.4 lesson).
         *
         * READ-ONLY: du, cut, echo — nothing else (contract-pinned).
         */
        val PROBE_SCRIPT = """
            for d in .npm .cache .gradle .cargo .m2; do
              [ -d "${D}HOME/${D}d" ] || continue
              kb=${D}(du -sk "${D}HOME/${D}d" 2>/dev/null | cut -f1)
              echo "@@CACHE:${D}d:${D}{kb:-?}"
            done
            if [ -d /tmp ]; then
              kb=${D}(du -sk /tmp 2>/dev/null | cut -f1)
              echo "@@CACHE:tmp:${D}{kb:-?}"
            fi
            echo "@@DONE"
            exit 0
        """.trimIndent()
    }
}

/** One probed cache: guest name + size in KiB (null = du could not say). */
internal data class GuestCacheEntry(
    val name: String,
    val kilobytes: Long?,
)

internal data class CacheProbeOutput(
    val entries: List<GuestCacheEntry>,
    /** False = the stream ended before "@@DONE" — output not trusted. */
    val complete: Boolean,
)

/**
 * Names the protocol vouches for. Anything else on the wire is ignored —
 * a cache name would have to be both probed by our own fixed list AND
 * spoofed to matter, and the strict allowlist costs nothing.
 */
internal val KNOWN_CACHE_NAMES = setOf("npm", "cache", "gradle", "cargo", "m2", "tmp")

/**
 * Parse the probe's stdout. Null when no "@@CACHE:"/"@@DONE" marker was
 * seen at all — output the protocol cannot vouch for is a Failed probe,
 * never data.
 */
internal fun parseCacheOutput(stdout: String): CacheProbeOutput? {
    var sawMarker = false
    var complete = false
    val entries = ArrayList<GuestCacheEntry>()
    for (line in stdout.lineSequence()) {
        when {
            line.startsWith("@@CACHE:") -> {
                sawMarker = true
                val body = line.removePrefix("@@CACHE:")
                val name = body.substringBefore(':')
                val kb = body.substringAfter(':', "").trim().toLongOrNull()
                if (name in KNOWN_CACHE_NAMES) {
                    entries += GuestCacheEntry(name = name, kilobytes = kb)
                }
            }
            line == "@@DONE" -> {
                complete = true
            }
        }
    }
    return if (sawMarker || complete) CacheProbeOutput(entries, complete) else null
}

/** Guest paths for humans: probed home caches show as "~/.name". */
internal fun guestCacheLabel(name: String): String = when (name) {
    "tmp" -> "/tmp"
    else -> "~/.${name}"
}

package app.pocketshell.widget.ssh

/**
 * M8.3 — known_hosts COUNTER, pure (text in, stats out; no java.io).
 *
 * SECURITY CONTRACT: known_hosts may be hashed (`|1|salt|hash` markers)
 * precisely so its hostnames stay secret — so this layer returns COUNTS
 * and shape classifications only. No hostname, key, or line content ever
 * leaves this object toward the UI.
 */

data class KnownHostsStats(
    /** Non-blank, non-comment lines — one entry each, whatever their shape. */
    val entries: Int,
    /** Entries whose marker field is a bcrypt/`|1|` hash — hostnames NOT readable. */
    val hashed: Int,
    /** Entries carrying an explicit port (`[host]:2222` marker field), non-hashed only. */
    val ported: Int,
)

object KnownHosts {

    /** A ported marker field: `[something]:2222` (ssh's non-standard-port form). */
    private val PORTED = Regex("""^\[[^\]]+\]:\d+$""")

    fun stats(text: String): KnownHostsStats {
        var entries = 0
        var hashed = 0
        var ported = 0
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            entries++
            // Field one may be a single marker or a comma-separated pattern
            // list ("a.example.com,b.example.com"); hashed entries carry the
            // hash in field one directly.
            val firstField = line.split(WHITESPACE).firstOrNull().orEmpty()
            when {
                firstField.startsWith("|1|") -> hashed++
                firstField.split(',').any { PORTED.matches(it) } -> ported++
            }
        }
        return KnownHostsStats(entries, hashed, ported)
    }

    private val WHITESPACE = Regex("""\s+""")
}

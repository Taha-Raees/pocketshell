package app.pocketshell.widget.ssh

/**
 * M8.3 — the SSH Home application's ~/.ssh/config reader. PURE: String in,
 * data out — no java.io, no IO of any kind (SshFiles is the only reader of
 * the guest's ~/.ssh directory; the key-content safety pins live there and
 * in SshAppContractTest).
 *
 * The supported subset is the real-world core of ssh_config:
 *   - `Host` lines with MULTIPLE space-separated (optionally quoted)
 *     patterns — one block per line;
 *   - HostName / User / Port / IdentityFile options (the identity value is
 *     carried as a NAME and is never opened anywhere in this application);
 *   - `Include` and `Match` blocks: present in real configs, deliberately
 *     NOT followed — they are COUNTED so the card can say so honestly
 *     instead of silently showing a wrong picture (a Match block's options
 *     apply conditionally; guessing would be dishonest).
 *   - the deprecated `Keyword=value` form and case-insensitive keywords,
 *     because real configs in the wild use them.
 *
 * Defaults inheritance approximates ssh's first-obtained-value-wins
 * WITHOUT target-host pattern evaluation (we do not know which host the
 * user will connect to): an entry's own values win, then the FIRST
 * wildcard-containing block (`Host *` by convention), then options stated
 * globally before any Host line. Documented approximation — every shown
 * value still comes verbatim from the user's config file.
 */

/** The parse result: Host blocks in file order (options unresolved), plus honest counters. */
data class SshConfigParse(
    val entries: List<SshHostEntry>,
    /** Options stated before any Host line (ssh's implicit global defaults). */
    val global: SshGlobalDefaults,
    /** `Include` directives seen but not followed (the file may add hosts we do not show). */
    val includesIgnored: Int,
    /** `Match` blocks seen but not evaluated (their options are conditional). */
    val matchBlocksIgnored: Int,
)

data class SshGlobalDefaults(
    val hostName: String? = null,
    val user: String? = null,
    val port: Int? = null,
    val identityFiles: List<String> = emptyList(),
)

/**
 * One `Host` block. The option fields hold the block's OWN values after
 * [SshConfigParser.resolvedHosts] has layered the defaults on top (the
 * parser produces unresolved blocks; the UI consumes resolved ones).
 */
data class SshHostEntry(
    val patterns: List<String>,
    val hostName: String?,
    val user: String?,
    val port: Int?,
    /** Config-declared identity file NAMES/paths. Never opened — display only. */
    val identityFiles: List<String>,
    /** True when ANY pattern is a wildcard/negation — a pattern block, not one saved host. */
    val wildcard: Boolean,
) {
    /**
     * Display key: the first concrete pattern (what the user typed as the
     * alias), or the whole pattern list for pattern-only blocks.
     */
    val displayName: String =
        patterns.firstOrNull { !SshConfigParser.isWildcardPattern(it) }
            ?: patterns.joinToString(" ").ifEmpty { "?" }
}

object SshConfigParser {

    fun parse(text: String): SshConfigParse {
        var pending: Block? = null
        val global = Block()
        val entries = mutableListOf<SshHostEntry>()
        var inMatchBlock = false
        var includesIgnored = 0
        var matchBlocksIgnored = 0

        fun flush() {
            pending?.let { block ->
                if (block.patterns.isNotEmpty()) {
                    entries += SshHostEntry(
                        patterns = block.patterns.toList(),
                        hostName = block.hostName,
                        user = block.user,
                        port = block.port,
                        identityFiles = block.identityFiles.toList(),
                        wildcard = block.patterns.any { isWildcardPattern(it) },
                    )
                }
                // A Host line with no patterns is skipped: ssh rejects it too.
            }
            pending = null
        }

        for (raw in text.lines()) {
            // ssh(5): empty lines and lines STARTING with '#' are comments;
            // a '#' mid-line is part of the value, and we only read the
            // first value token anyway.
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val tokens = splitDeprecatedEquals(tokenize(line))
            val keyword = tokens.first().lowercase()
            when (keyword) {
                "host" -> {
                    flush()
                    inMatchBlock = false
                    pending = Block(tokens.drop(1))
                }
                "match" -> {
                    // Match blocks are conditional (exec/user/... criteria we
                    // cannot evaluate) — ignored as a region until the next
                    // Host line, counted, never half-applied.
                    flush()
                    inMatchBlock = true
                    matchBlocksIgnored++
                }
                "include" -> {
                    includesIgnored++
                }
                else -> {
                    // Inside a Match region nothing is applied. Before any
                    // Host line the options are ssh's global defaults.
                    val target = if (inMatchBlock) null else (pending ?: global)
                    if (target != null) {
                        when (keyword) {
                            "hostname" -> target.hostName = tokens.getOrNull(1)
                            "user" -> target.user = tokens.getOrNull(1)
                            "port" -> target.port = tokens.getOrNull(1)
                                ?.toIntOrNull()?.takeIf { it in 1..65535 }
                            "identityfile" -> tokens.drop(1).forEach { target.identityFiles += it }
                        }
                    }
                }
            }
        }
        flush()
        return SshConfigParse(
            entries = entries,
            global = SshGlobalDefaults(global.hostName, global.user, global.port, global.identityFiles.toList()),
            includesIgnored = includesIgnored,
            matchBlocksIgnored = matchBlocksIgnored,
        )
    }

    /** Wildcard/`?`/negation patterns cannot name ONE saved host. */
    internal fun isWildcardPattern(pattern: String): Boolean =
        pattern.isEmpty() || pattern.contains('*') || pattern.contains('?') || pattern.startsWith('!')
    /** A tiny mutable accumulator for options between Host lines. */
    private class Block(val patterns: List<String> = emptyList()) {
        var hostName: String? = null
        var user: String? = null
        var port: Int? = null
        val identityFiles = mutableListOf<String>()
    }

    /**
     * Quote-aware tokenizer (double quotes only, as ssh(5) allows), so
     * `Host "my host"` and quoted identity paths survive intact.
     */
    internal fun tokenize(line: String): List<String> {
        val out = ArrayList<String>(4)
        val sb = StringBuilder()
        var inQuotes = false
        var started = false
        for (ch in line) {
            when {
                ch == '"' -> {
                    if (inQuotes) {
                        out += sb.toString()
                        sb.clear()
                        started = false
                    }
                    inQuotes = !inQuotes
                }
                ch.isWhitespace() && !inQuotes -> {
                    if (started) {
                        out += sb.toString()
                        sb.clear()
                        started = false
                    }
                }
                else -> {
                    sb.append(ch)
                    started = true
                }
            }
        }
        if (started) out += sb.toString()
        return out
    }

    /**
     * The deprecated `Keyword=value` form: the tokenizer would glue
     * "HostName=a.example.com" into one token — split it at the first '='
     * back into keyword + value.
     */
    private fun splitDeprecatedEquals(tokens: List<String>): List<String> {
        val first = tokens.firstOrNull() ?: return tokens
        val eq = first.indexOf('=')
        if (eq <= 0) return tokens
        val keyword = first.substring(0, eq)
        val value = first.substring(eq + 1)
        return if (value.isEmpty()) listOf(keyword) + tokens.drop(1)
        else listOf(keyword, value) + tokens.drop(1)
    }
}

/**
 * The display view: each entry with defaults layered own → first wildcard
 * block → global options (see the class doc for the honest approximation
 * this implements). Top-level extension so every consumer (SshFiles,
 * tests) shares one definition.
 */
fun SshConfigParse.resolvedHosts(): List<SshHostEntry> {
    val defaultsIndex = entries.indexOfFirst { it.wildcard }
    return entries.mapIndexed { index, own ->
        var hostName = own.hostName
        var user = own.user
        var port = own.port
        var identityFiles = own.identityFiles
        if (defaultsIndex >= 0 && defaultsIndex != index) {
            val defaults = entries[defaultsIndex]
            hostName = hostName ?: defaults.hostName
            user = user ?: defaults.user
            port = port ?: defaults.port
            if (identityFiles.isEmpty()) identityFiles = defaults.identityFiles
        }
        SshHostEntry(
            patterns = own.patterns,
            hostName = hostName ?: global.hostName,
            user = user ?: global.user,
            port = port ?: global.port,
            identityFiles = identityFiles.ifEmpty { global.identityFiles },
            wildcard = own.wildcard,
        )
    }
}

/**
 * Whether a running ssh client's argv target matches a saved entry —
 * pure, honest matching: concrete entries only (pattern blocks are never
 * "matched" — evaluating wildcards would be a guess), name must equal a
 * pattern or the resolved HostName case-insensitively, and ports must
 * agree with ssh's default of 22 when either side omits one.
 */
internal fun sshEntryMatchesTarget(entry: SshHostEntry, target: SshArgvTarget): Boolean {
    if (entry.wildcard) return false
    val host = target.host ?: return false
    val names = buildSet {
        entry.patterns.forEach { add(it.lowercase()) }
        entry.hostName?.let { add(it.lowercase()) }
    }
    return host.lowercase() in names && (target.port ?: 22) == (entry.port ?: 22)
}

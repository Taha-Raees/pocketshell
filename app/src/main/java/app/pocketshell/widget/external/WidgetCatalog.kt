package app.pocketshell.widget.external

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * M8.4.4 — the widget distribution catalog: the machine-readable listing of
 * the `ps-widget-repo` repository (branch main), fetched as DATA when the
 * user opens the widget catalog in Control Center. A catalog entry is a
 * POINTER to a [WidgetManifest] file — never code, never a binary: the
 * listing itself is display data, and the trust boundary stays where the
 * M8 model put it ([WidgetManifestValidator], at manifest fetch time).
 *
 * There is deliberately NO catalog cache persistence: fetching the catalog
 * is an explicit user action; only INSTALLED manifests are local
 * ([WidgetInstallStore]). Every bound below is contractual — timeouts, a
 * hard response cap, HTTPS-only — and a bound breach is an honest
 * failure, never a truncated parse.
 */

/** The distribution repo's catalog, resolved against branch main. */
const val DEFAULT_CATALOG_URL =
    "https://raw.githubusercontent.com/Taha-Raees/ps-widget-repo/main/catalog.json"

@Serializable
data class WidgetCatalogEntry(
    val id: String,
    val name: String,
    val summary: String = "",
    val author: String = "",
    /** Semver of the widget itself (display + drift detection). */
    val version: String,
    /** Minimum PocketShell version — enforced AGAIN at manifest fetch. */
    val minAppVersion: String = "0.0.0",
    /** Manifest path RELATIVE to the catalog URL's directory. */
    val file: String,
)

@Serializable
data class WidgetCatalog(val widgets: List<WidgetCatalogEntry> = emptyList())

/**
 * The catalog codec: strict STRUCTURE (an unparseable catalog is a
 * failure, not an empty listing), lenient ENTRY sanitization (unknown
 * entry keys are ignored so an older app reading a newer catalog
 * degrades; entries failing the shape checks are dropped, duplicate ids
 * keep the FIRST entry). Dropping a bad LISTING entry is safe by
 * construction — the manifest it points to is validated in full before
 * anything is ever rendered or installed.
 */
object WidgetCatalogCodec {

    /** The exact id rule of [WidgetManifestValidator]. */
    private val idRegex = Regex("""[a-z][a-z0-9-]{1,31}""")
    private val semverRegex = Regex("""\d+\.\d+\.\d+""")
    private val json = Json { ignoreUnknownKeys = true }

    /** Structured parse; null = the text is not a catalog at all. */
    fun parse(text: String): WidgetCatalog? = try {
        json.decodeFromString(WidgetCatalog.serializer(), text)
    } catch (_: Exception) {
        null
    }

    /** Shape-checked, id-deduped view of a parsed catalog. */
    fun sanitize(catalog: WidgetCatalog): WidgetCatalog = WidgetCatalog(
        catalog.widgets
            .filter {
                it.id.matches(idRegex) &&
                    it.name.isNotEmpty() &&
                    it.version.matches(semverRegex) &&
                    it.minAppVersion.matches(semverRegex) &&
                    isSafeRelativePath(it.file)
            }
            .distinctBy { it.id },
    )
}

/**
 * The catalog downloader. HTTPS only (the platform bans cleartext and the
 * class refuses non-https URLs outright); bounded 10s connect/read
 * timeouts; a hard [MAX_RESPONSE_BYTES] cap where one byte over is a
 * FAILURE — never a truncated parse. The app version is injected per call
 * (the production caller passes the real versionName) so the version gate
 * stays pure and JVM-testable.
 */
class WidgetCatalogDownloader(
    private val catalogUrl: String = DEFAULT_CATALOG_URL,
) {

    init {
        require(catalogUrl.startsWith("https://")) { "catalog url must be https" }
    }

    /** Fetch outcome for the whole catalog listing. */
    sealed interface CatalogResult {
        data class Done(val catalog: WidgetCatalog) : CatalogResult
        data class Failed(val reason: String) : CatalogResult
    }

    /** Fetch outcome for one catalog entry's manifest. */
    sealed interface ManifestResult {
        data class Done(val manifest: WidgetManifest) : ManifestResult
        data class Failed(val reason: String) : ManifestResult
    }

    /** Fetch + parse + sanitize the listing. */
    suspend fun fetch(): CatalogResult {
        if (catalogDirectory(catalogUrl) == null) {
            return CatalogResult.Failed("catalog url must be https")
        }
        return when (val response = httpsGet(catalogUrl)) {
            is HttpResult.Failed -> CatalogResult.Failed(response.reason)
            is HttpResult.Done -> {
                val catalog = WidgetCatalogCodec.parse(response.text)
                    ?: return CatalogResult.Failed("catalog is not valid widget-catalog JSON")
                CatalogResult.Done(WidgetCatalogCodec.sanitize(catalog))
            }
        }
    }

    /**
     * Fetch one entry's manifest: [relPath] resolves against the catalog
     * URL's directory after the traversal guard; the fetched text must
     * parse STRICTLY, pass the validator in full, carry the catalog
     * entry's exact id, and require no newer PocketShell than the
     * [appVersion] build the caller states.
     */
    suspend fun fetchManifest(
        relPath: String,
        expectedId: String,
        appVersion: String,
    ): ManifestResult {
        val url = resolveManifestUrl(catalogUrl, relPath)
            ?: return ManifestResult.Failed("unsafe widget path (traversal, absolute, or non-URL characters)")
        return when (val response = httpsGet(url)) {
            is HttpResult.Failed -> ManifestResult.Failed(response.reason)
            is HttpResult.Done -> when (val validated = WidgetManifestValidator.parse(response.text)) {
                is WidgetManifestValidator.Result.Invalid ->
                    ManifestResult.Failed("manifest rejected: " + validated.reasons.joinToString("; "))
                is WidgetManifestValidator.Result.Valid -> {
                    val refusal = acceptFetchedManifest(validated.manifest, expectedId, appVersion)
                    if (refusal != null) ManifestResult.Failed(refusal) else ManifestResult.Done(validated.manifest)
                }
            }
        }
    }

    /** The one network primitive: a bounded HTTPS GET returning body text. */
    private sealed interface HttpResult {
        data class Done(val text: String) : HttpResult
        data class Failed(val reason: String) : HttpResult
    }

    private suspend fun httpsGet(url: String): HttpResult = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpsURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = true
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                HttpResult.Failed("HTTP $code for $url")
            } else {
                val text = connection.inputStream.use { readCapped(it, MAX_RESPONSE_BYTES) }
                if (text == null) {
                    HttpResult.Failed("response exceeds the $MAX_RESPONSE_BYTES-byte cap")
                } else {
                    HttpResult.Done(text)
                }
            }
        } catch (error: Exception) {
            // The honest cause, class name included — no swallowed errors,
            // no invented "offline" shorthand.
            HttpResult.Failed("${error.javaClass.simpleName}: ${error.message ?: "no detail"}")
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        /** Connect AND read timeout, each. */
        const val TIMEOUT_MS = 10_000

        /** One byte over this is a failed fetch, never a truncated parse. */
        const val MAX_RESPONSE_BYTES = 256 * 1024
    }
}

/**
 * The installed app's real versionName — the value a caller passes to
 * [WidgetCatalogDownloader.fetchManifest] as `appVersion`. The
 * BuildConfig-free path: the version comes from the package manager, so
 * the version gate never depends on module build wiring.
 */
fun currentAppVersion(context: Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
} catch (_: Exception) {
    ""
}

// ------------------------------------------------- pure, JVM-tested helpers

/**
 * The path guard for catalog-relative manifest paths: no traversal (".."),
 * no absolute paths (leading "/"), no backslashes (Windows-style or
 * URL-escaped tricks), no whitespace (a raw space is never a valid raw
 * GitHub path — refuse rather than guess an encoding).
 */
internal fun isSafeRelativePath(path: String): Boolean =
    path.isNotEmpty() &&
        !path.contains("..") &&
        !path.startsWith('/') &&
        !path.contains('\\') &&
        !path.any { it.isWhitespace() }

/** The catalog URL's directory (trailing slash), or null for non-https. */
internal fun catalogDirectory(catalogUrl: String): String? {
    if (!catalogUrl.startsWith("https://")) return null
    val cut = catalogUrl.lastIndexOf('/')
    if (cut < "https://".length) return null
    return catalogUrl.substring(0, cut + 1)
}

/** Catalog-directory resolution of a guarded relative path; null = refused. */
internal fun resolveManifestUrl(catalogUrl: String, relPath: String): String? {
    if (!isSafeRelativePath(relPath)) return null
    return catalogDirectory(catalogUrl)?.plus(relPath)
}

/**
 * The leading numeric MAJOR.MINOR.PATCH of a version string —
 * "0.13.0-m7.3" is the triple 0.13.0; anything without a leading triple
 * ("latest", "v1.2.3") is malformed and refuses downstream.
 */
internal fun versionTriple(version: String): List<Int>? {
    val match = Regex("""^(\d+)\.(\d+)\.(\d+)""").find(version.trim()) ?: return null
    return listOf(
        match.groupValues[1].toInt(),
        match.groupValues[2].toInt(),
        match.groupValues[3].toInt(),
    )
}

/**
 * The version gate: does this build satisfy the manifest's
 * minAppVersion? A malformed EITHER side refuses (fail-closed) — an
 * unverifiable version is never waved through.
 */
internal fun appSupports(appVersion: String, minAppVersion: String): Boolean {
    val app = versionTriple(appVersion) ?: return false
    val min = versionTriple(minAppVersion) ?: return false
    for (index in 0..2) {
        when {
            app[index] > min[index] -> return true
            app[index] < min[index] -> return false
        }
    }
    return true
}

/**
 * The post-download acceptance gate for one fetched manifest: the id must
 * match the catalog entry the user tapped, and the min app version must
 * be satisfied. Null = accepted; non-null = the honest refusal reason.
 */
internal fun acceptFetchedManifest(
    manifest: WidgetManifest,
    expectedId: String,
    appVersion: String,
): String? = when {
    manifest.id != expectedId ->
        "manifest id '${manifest.id}' does not match the catalog entry '$expectedId'"
    !appSupports(appVersion, manifest.minAppVersion) ->
        "requires PocketShell ${manifest.minAppVersion} (this build is ${appVersion})"
    else -> null
}

/**
 * The cap read shared by every fetch: the whole body, or null when the
 * stream exceeds [maxBytes] — one byte over is a failure, so a parse
 * below never sees truncated data.
 */
internal fun readCapped(input: InputStream, maxBytes: Int): String? {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        out.write(buffer, 0, read)
        if (out.size() > maxBytes) return null
    }
    return out.toString("UTF-8")
}

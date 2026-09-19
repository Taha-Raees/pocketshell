package app.pocketshell.widget.external

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * M8.4.4 — the ONE local store for downloaded external widgets, owning its
 * own per-domain DataStore file ("widget_catalog", exactly the established
 * per-domain pattern: home_widgets, settings, todo_store, …). ONE key,
 * "installed_manifests": the JSON array of full [WidgetManifest] objects
 * the user installed from the catalog.
 *
 * The store persists NO catalog cache: fetching the catalog is an explicit
 * user action over the network; what is local is only what the user
 * chose to install. Reads are a cold Flow emitting on change; writes go
 * through DataStore's `edit` transaction (atomic read-modify-write, no
 * main-thread IO).
 *
 * Sanitization is on DECODE (the established codec discipline): every
 * stored manifest must still pass [WidgetManifestValidator] in full,
 * duplicate ids keep the FIRST manifest, the set is capped at
 * [WidgetInstallCodec.MAX_INSTALLED], and a corrupt record decodes to
 * EMPTY — never to a partially trusted widget. Install and remove refuse
 * loudly rather than pretend: an invalid manifest is a thrown
 * IllegalArgumentException (defense in depth — the downloader already
 * validated it), and installing past the cap is a thrown
 * IllegalStateException the caller can show, never a silent eviction.
 */
class WidgetInstallStore(private val context: Context) {

    private val installedKey = stringPreferencesKey("installed_manifests")

    /** The installed widgets, sanitized, install order preserved. */
    val installed: Flow<List<WidgetManifest>> =
        context.widgetCatalogStore.data.map { prefs ->
            WidgetInstallCodec.decode(prefs[installedKey])
        }

    /** Install (or overwrite the same-id) manifest, validator-checked. */
    suspend fun install(manifest: WidgetManifest) {
        when (val check = WidgetManifestValidator.validate(manifest)) {
            is WidgetManifestValidator.Result.Invalid ->
                throw IllegalArgumentException(
                    "refusing to install an invalid manifest: ${check.reasons.joinToString("; ")}",
                )
            is WidgetManifestValidator.Result.Valid -> Unit
        }
        context.widgetCatalogStore.edit { prefs ->
            val next = WidgetInstallCodec.install(
                current = WidgetInstallCodec.decode(prefs[installedKey]),
                manifest = manifest,
            )
            prefs[installedKey] = WidgetInstallCodec.encode(next)
        }
    }

    /** Remove by id; removing an absent id is a harmless no-op write. */
    suspend fun remove(id: String) {
        context.widgetCatalogStore.edit { prefs ->
            prefs[installedKey] = WidgetInstallCodec.encode(
                WidgetInstallCodec.decode(prefs[installedKey]).filterNot { it.id == id },
            )
        }
    }
}

private val Context.widgetCatalogStore by preferencesDataStore(name = "widget_catalog")

/**
 * The installed-manifests codec — pure String in / List out (the
 * established codec discipline: sanitize, cap, honest degradation).
 * Unknown JSON keys are IGNORED on decode, so an older app reading a
 * newer store degrades instead of rejecting its own installed widgets.
 */
object WidgetInstallCodec {

    /** The installed-widget cap: one full store never grows unbounded. */
    const val MAX_INSTALLED = 12

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(manifests: List<WidgetManifest>): String =
        json.encodeToString(ListSerializer(WidgetManifest.serializer()), manifests.take(MAX_INSTALLED))

    /**
     * Corrupt/absent → empty. Well-shaped but dirty records are
     * sanitized: only manifests that STILL pass the full validator are
     * kept, duplicate ids keep the first, capped at [MAX_INSTALLED].
     */
    fun decode(raw: String?): List<WidgetManifest> {
        val parsed = raw?.let {
            try {
                json.decodeFromString(ListSerializer(WidgetManifest.serializer()), it)
            } catch (_: Exception) {
                null
            }
        } ?: emptyList()
        return parsed
            .filter { WidgetManifestValidator.validate(it) is WidgetManifestValidator.Result.Valid }
            .distinctBy { it.id }
            .take(MAX_INSTALLED)
    }

    /**
     * The pure install step: overwrite the same id in place-order, never
     * exceed [MAX_INSTALLED] — installing a NEW widget into a full store
     * throws (the caller shows it; eviction is never silent).
     */
    fun install(current: List<WidgetManifest>, manifest: WidgetManifest): List<WidgetManifest> {
        val replacing = current.any { it.id == manifest.id }
        check(replacing || current.size < MAX_INSTALLED) {
            "widget limit ($MAX_INSTALLED) reached — remove one before installing another"
        }
        return (current.filterNot { it.id == manifest.id } + manifest).take(MAX_INSTALLED)
    }
}

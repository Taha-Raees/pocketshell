package app.pocketshell.runtime

/**
 * Pinned PocketShell glibc runtime layer (docs/runtime/DUAL_LIBC.md §4).
 *
 * The layer is REAL glibc (Debian 13 trixie arm64) assembled at canonical
 * multiarch paths inside the Alpine guest by scripts/runtime/build_glibc_sidecar.sh.
 * It ships inside the APK as a compressed asset — atomic with the app update,
 * no network dependency, no user action. Its SHA-256 and size are pinned here
 * and verified at extraction time by the tests; the artifact bytes themselves
 * are produced by the pinned Debian pool inputs (INPUTS.sha256 in the build
 * output directory, committed alongside).
 *
 * Version bumps happen through app updates, never through loose URLs.
 */
object GlibcRuntimePin {
    const val ARTIFACT_NAME = "pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz"
    const val SHA256 = "2242f8ef8f18df06c6bf37d55f6ae526cccb6d835f76bc048b877266d252ad11"
    const val SIZE_BYTES = 6_761_290L

    /** Upstream glibc version the layer provides (RuntimePin-style honesty). */
    const val GLIBC_VERSION = "2.41"

    /** Full layer label (libc6 package version the layer was cut from). */
    const val LAYER_VERSION = "2.41-12.deb13u3"

    /** APK asset location. */
    const val ASSET_PATH = "guest/$ARTIFACT_NAME"

    /**
     * Guest-relative marker file, written LAST after every entry extracted.
     * Presence + exact content = the layer is complete and current. Anything
     * else (missing, stale, corrupt) → re-extraction on the next ensure call
     * (idempotent, self-healing). `pocketshell-doctor` parses the "glibc x.y"
     * part for the required-symbol-version check.
     */
    const val MARKER_RELATIVE = "etc/pocketshell/glibc-runtime"

    fun markerContent(): String =
        "PocketShell glibc runtime layer $LAYER_VERSION (glibc $GLIBC_VERSION)\n"
}

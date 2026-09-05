package app.pocketshell.runtime

/**
 * Pinned PocketShell glibc runtime layer (docs/runtime/DUAL_LIBC.md §4).
 *
 * The layer is REAL glibc (Debian 13 trixie arm64) assembled at canonical
 * multiarch paths inside the Alpine guest by scripts/runtime/build_glibc_sidecar.sh.
 * It ships inside the APK as an asset — atomic with the app update, no network
 * dependency, no user action. Both artifact forms are pinned here and verified
 * (JVM tests; the packaged form additionally at extraction time): the artifact
 * bytes themselves are produced by the pinned Debian pool inputs (INPUTS.sha256
 * in the build output directory, committed alongside).
 *
 * m6.0.2 DEVICE-GATE LESSON (the m6.0.0/m6.0.1 root cause): AGP's asset merge
 * DECOMPRESSES `*.gz` assets and strips the `.gz` suffix. The repo committed the
 * pinned `.tar.gz`, the code opened `guest/….tar.gz` — and the shipped APK
 * contained only the PLAIN tar under the `.tar` name. Every session spawn then
 * failed with FileNotFoundException before touching the rootfs (best-effort
 * swallowed on vc40; status-file-only on vc41, unread by the v1 device suite).
 * The pin therefore describes BOTH forms explicitly:
 *
 *  - [ASSET_PATH]/[ASSET_SHA256]/[ASSET_SIZE_BYTES] — what the APK must carry
 *    (the PLAIN tar, exactly what AGP produces from the pinned artifact),
 *    verified at extraction time inside the app.
 *  - [ARTIFACT_NAME]/[SHA256]/[SIZE_BYTES] — the release artifact (`.tar.gz`)
 *    mirrored for the suite hatch and provenance verification.
 *
 * Version bumps happen through app updates, never through loose URLs.
 */
object GlibcRuntimePin {

    /** Release artifact (mirror + suite hatch + payload verification). */
    const val ARTIFACT_NAME = "pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz"
    const val SHA256 = "2242f8ef8f18df06c6bf37d55f6ae526cccb6d835f76bc048b877266d252ad11"
    const val SIZE_BYTES = 6_761_290L

    /**
     * The APK asset form: AGP ships the DECOMPRESSED tar under the `.tar` name
     * (see class doc). Pinned by name, size and sha; verified at extraction.
     */
    const val ASSET_PATH = "guest/pocketshell-glibc-aarch64-2.41-12.deb13u3.tar"
    const val ASSET_SHA256 = "5be400dd13ca05f569924f598a0c4c2c0b7331af8a8bd5f586c6d7535fccd3c0"
    const val ASSET_SIZE_BYTES = 17_909_760L

    /** Upstream glibc version the layer provides (RuntimePin-style honesty). */
    const val GLIBC_VERSION = "2.41"

    /** Full layer label (libc6 package version the layer was cut from). */
    const val LAYER_VERSION = "2.41-12.deb13u3"

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

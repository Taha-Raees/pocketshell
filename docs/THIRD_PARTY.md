# PocketShell — Third-Party Components

Maintained per §5 and §33 of the project brief. Every vendored component
records upstream source, pinned version, license, and exact modifications.

## Vendored: Termux `terminal-emulator`

| Field | Value |
|---|---|
| Upstream project | Termux (termux-app monorepo) |
| Repository | https://github.com/termux/termux-app |
| Pinned upstream commit | `3b66f8799635a4dba4a206563048ff0e6792c487` (branch `master`, commit date 2026-08-24, verified during M0) |
| License | **GPL-3.0-only** (upstream states "GPLv3 only"; historical lineage from jackpal/Android-Terminal-Emulator, Apache-2.0, per upstream LICENSE.md) |
| Vendored path | `terminal-emulator/` |
| Packages kept | `com.termux.terminal` (unchanged, eases future re-sync) |
| Android compatibility | minSdk 21 upstream; PocketShell sets 26 (see below) |
| ARM64 support | Yes (`arm64-v8a` ABI filter; also armeabi-v7a, x86, x86_64) |
| Maintenance status | Actively maintained upstream at pin time |
| Integration strategy | Vendored Gradle library module `:terminal-emulator`; upstream `build.gradle` ported to Kotlin DSL |
| Modifications required | Minimal (list below), all license-preserving |

### Modifications applied to `terminal-emulator`

1. Build script converted from Groovy `build.gradle` to `build.gradle.kts`;
   publishing/maven tasks dropped (not used); version numbers taken from
   PocketShell's version catalog.
2. `minSdk` raised 21 → 26 per PocketShell product decision (upstream Java
   sources untouched — no API above 26 is introduced by us in this module).
3. ABI filters kept as upstream (all four). NDK pinned to `28.2.13676358`.
4. **No changes** to any `.java`, `.c`, `.mk` file. Upstream test suite vendored
   unchanged and run as JVM unit tests.

## Vendored: Termux `terminal-view`

| Field | Value |
|---|---|
| Upstream project | Termux (termux-app monorepo) |
| Repository | https://github.com/termux/termux-app |
| Pinned upstream commit | `3b66f8799635a4dba4a206563048ff0e6792c487` (same snapshot as above) |
| License | **GPL-3.0-only** (same lineage note as above) |
| Vendored path | `terminal-view/` |
| Packages kept | `com.termux.view` (unchanged) |
| Android compatibility | minSdk 26 (PocketShell decision) |
| Maintenance status | Actively maintained upstream at pin time |
| Integration strategy | Vendored Gradle library module `:terminal-view`; depends on `:terminal-emulator` |
| Modifications required | Build script ported to Kotlin DSL; resources kept as-is. **No changes** to any `.java`/`.xml` file |

## License consequence for PocketShell

Because GPLv3-only code is vendored, **PocketShell as a whole is distributed
under GPL-3.0-only**. This was an explicit M0 decision (research: permissively
licensed alternatives — jackpal ATE — are unmaintained; see `RESEARCH.md` §2).
A `LICENSE` file ships at the repository root, and the vendored directories
retain upstream notices verbatim.

## Runtime dependencies (application)

| Artifact | Version | License | Role |
|---|---|---|---|
| androidx.core:core-ktx | 1.19.0 | Apache-2.0 | KTX extensions |
| androidx.activity:activity-compose | 1.13.0 | Apache-2.0 | Compose Activity |
| androidx.compose BOM | 2026.08.00 | Apache-2.0 | Compose versions alignment |
| androidx.compose.material3 | via BOM | Apache-2.0 | Material 3 UI |
| androidx.compose.material:material-icons-extended | 1.7.8 | Apache-2.0 | Icons (frozen upstream artifact version) |
| androidx.lifecycle:* | 2.11.0 | Apache-2.0 | ViewModels |
| androidx.datastore:datastore-preferences | 1.2.1 | Apache-2.0 | Settings + CLI app registry |
| org.jetbrains.kotlinx:kotlinx-serialization-json | 1.9.0 | Apache-2.0 | CliApp registry persistence |
| org.apache.commons:commons-compress | 1.28.0 | Apache-2.0 | M2 runtime installer: safe tar.gz extraction of the Alpine minirootfs (GNU longname, symlink, mode handling) |
| junit:junit | 4.13.2 | EPL-1.0 | JVM tests (upstream emulator suite) |

All versions above were verified against Google Maven / Maven Central metadata
at M0 time (2026-08-30); the version catalog `gradle/libs.versions.toml` is the
single source of truth. commons-compress pinned at M2.1 (2026-09-01).

## M2 runtime artifacts (not code-vendored — distributed/downloaded)

| Component | Origin | License | Distribution form |
|---|---|---|---|
| PRoot | https://github.com/termux/proot — **pinned tag `v5.1.107.92` @ `7266fb3e8516535682f5a9c8f3a7e70f6506eddb`** (proot 5.1.0 + Termux Android patches) | GPL-2.0 | Compiled by PocketShell from pinned source (scripts/build_proot_m23.sh, NDK r28c, arm64-v8a/armeabi-v7a/x86/x86_64); bundled in APK as `libproot.so` + `libproot-loader.so` (jniLibs → `nativeLibraryDir`). Build-time micro-patches applied by the build script to the working clone (documented, upstreamable): `<string.h>` include in `extension/ashmem_memfd/ashmem_memfd.c` (bionic + clang ≥ 16 strictness); portable mawk-compatible `loader/loader-info.awk` (upstream requires gawk `strtonum`). License text + source reference ship with the app. |
| libtalloc | https://www.samba.org/ftp/talloc/ — **pinned 2.4.2** | LGPL-3.0-or-later | Compiled by PocketShell (waf cross-compile with canned cross-answers; SONAME normalized to `libtalloc.so`); linked **dynamically** and bundled as `libtalloc.so` per ABI (LGPL obligations met by shipping the full source archive). |
| Alpine Linux minirootfs | https://dl-cdn.alpinelinux.org/alpine/v3.24/releases/aarch64/ (official CDN artifact, pinned: `alpine-minirootfs-3.24.1-aarch64.tar.gz`, sha256 `f55a90f69052c5bd6f92cb09a8f47065970830b194c917a006fb94028e721259`) | Alpine packages under their respective OSS licenses (busybox GPL-2.0, musl MIT, etc.) | Downloaded at runtime over HTTPS, size + SHA-256 verified before extraction (Master Prompt §10). Alpine's license notices are available inside the rootfs (`/usr/share/licenses`, package metadata via `apk info`). |

Note: Termux's `libandroid-shmem` is **not** shipped — it exists to backport
SysV shm to bionic below API 26; PocketShell's minSdk is 26, where bionic
provides native SysV shm, so proot's sysvipc extension compiles against
bionic directly.

All resolved from Google Maven / Maven Central at build time with pinned
versions (version catalog: `gradle/libs.versions.toml`).

## Upstream re-sync procedure

1. `git fetch` upstream; select new release/commit; record it here.
2. Copy both modules' `src/` trees over the vendored ones (packages unchanged).
3. Diff against vendored tree; port any PocketShell-side build-script changes.
4. Run the full upstream test suite (`./gradlew :terminal-emulator:test`).
5. Execute the manual terminal acceptance checklist (`TESTING.md`) on device.
6. Update this file and `CHANGELOG.md`.

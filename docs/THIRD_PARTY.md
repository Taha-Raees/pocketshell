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
| androidx.core:core-ktx | 1.15.0 | Apache-2.0 | KTX extensions |
| androidx.activity:activity-compose | 1.10.1 | Apache-2.0 | Compose Activity |
| androidx.compose BOM | 2025.12.01 | Apache-2.0 | Compose versions alignment |
| androidx.compose.material3 | via BOM | Apache-2.0 | Material 3 UI |
| androidx.lifecycle:* | 2.9.4 | Apache-2.0 | ViewModels |
| androidx.datastore:datastore-preferences | 1.1.7 | Apache-2.0 | Settings + CLI app registry |
| org.jetbrains.kotlinx:kotlinx-serialization-json | 1.9.0 | Apache-2.0 | CliApp registry persistence |
| junit:junit | 4.13.2 | EPL-1.0 | JVM tests (upstream emulator suite) |

All resolved from Google Maven / Maven Central at build time with pinned
versions (version catalog: `gradle/libs.versions.toml`).

## Upstream re-sync procedure

1. `git fetch` upstream; select new release/commit; record it here.
2. Copy both modules' `src/` trees over the vendored ones (packages unchanged).
3. Diff against vendored tree; port any PocketShell-side build-script changes.
4. Run the full upstream test suite (`./gradlew :terminal-emulator:test`).
5. Execute the manual terminal acceptance checklist (`TESTING.md`) on device.
6. Update this file and `CHANGELOG.md`.

# JAVA_RUNTIME — supported Java runtime in the PocketShell guest

Status: **implemented 2026-09-12** (workstation-hardening phase, after the
master environment audit). Companion: `DEV_WORKSTATION.md`,
`scripts/runtime/devtools/`.

## 1. Decision

The supported Java runtime is **Eclipse Temurin JDK 21 (glibc, aarch64)**,
installed at `/usr/lib/jvm/temurin-21` and delivered by the guest glibc
sidecar loader (`/lib/ld-linux-aarch64.so.1`, M6 layer). No APK change is
required to run it; delivery is download-on-demand with pinned integrity
(`scripts/runtime/devtools/manifest.conf`).

The Alpine **musl `openjdk21` package is broken in this environment** and
must not be installed: it fails at startup with a single
`mprotect(<4096B anon page>, RWX) = EACCES` (HotSpot polling page). The
master audit (§H) proved the same operation succeeds for the glibc Temurin
build, node/V8 and plain C programs in the identical sandbox — the deny is
build-specific to the musl OpenJDK, not a platform boundary. The audit's
earlier "seccomp blocks the JVM" conclusion was wrong. Keeping musl openjdk
installed only creates PATH ambiguity (`/usr/bin/java` shadowing) — the
bootstrap removes it.

## 2. Pin

| Field | Value |
|---|---|
| Version | 21.0.12.1+1 (`jdk-21.0.12.1+1`, Adoptium) |
| Artifact | `OpenJDK21U-jdk_aarch64_linux_hotspot_21.0.12_1.tar.gz` |
| Size | 205,641,175 B |
| SHA-256 | `23e37e026f12f3e706f18938ff611db3032d075b09d0879a25d06718c773e223` |
| Source | Adoptium temurin21-binaries GitHub release (pinned URL in manifest) |

Install model: download-on-demand (196 MB — deliberately NOT an APK asset),
cached under `/root/.cache/pocketshell-dev/`, sha256-verified before and
after extraction, idempotent re-runs. Vendor-side cross-check via the
Adoptium checksum API returned 404 at implementation time (API shape
changed); the manifest pins the checksum of the exact bytes verified
working on device. Re-downloads verify against that pin.

Wiring: `/etc/profile.d/pocketshell-java.sh` (login shells: `JAVA_HOME`,
`PATH`), `/usr/local/bin/{java,javac,keytool,jar}` shims (non-login shells;
also guarantees the glibc JVM wins if a musl JDK ever reappears).

## 3. Gradle

Official Gradle 8.14.5 at `/opt/gradle-8.14.5` (`/usr/local/bin/gradle`),
pinned to Gradle's published distribution SHA-256
(`6f74b601422d6d6fc4e1f9a1ab6522f642c2fdcbc15ae33ebd30ba3d7198e854`).
Device-memory defaults in `/root/.gradle/gradle.properties`:
`-Xmx1792m` heap, 512m metaspace, 4 workers, Kotlin daemon `-Xmx1024m` —
tuned for a 7.4 GB-RAM phone sharing RAM with Android (see §5).

## 4. Verified on device (2026-09-12)

- fresh login shell: `java -version`, `javac -version`, `gradle --version` ✓
- `gradle compileJava` + run of a compiled class (demo project) ✓
- full CLI APK build: aapt2 → javac → d8 → zipalign → apksigner
  (`build-minimal-apk.sh`) → signed APK, badging-verified ✓
- official `sdkmanager` (cmdline-tools) installing platform-36 +
  build-tools 36.0.0 on this JVM ✓
- PocketShell repo `assembleDebug` — see `DEV_WORKSTATION.md` for the
  recorded result.

## 5. Memory defaults (why)

Device: 7.4 GB RAM total, shared with Android (LMK active). Defaults target
ONE heavy JVM workflow at a time (Gradle build OR agent fleet OR language
server), not all concurrently:

| Component | Default |
|---|---|
| Gradle daemon/launcher heap | 1792 MB |
| Gradle metaspace | 512 MB |
| Gradle workers | 4 (of 8 cores) |
| Kotlin daemon heap | 1024 MB |
| node (agents/MCP) | unchanged (V8 defaults) |

These are floor-of-sanity, not limits: per-project `gradle.properties`
override freely. Do not raise globally without measuring free RAM
(`free -m`) under the real workload.

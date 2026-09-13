# ARTIFACT NAMING — PocketShell build/artifact convention

Adopted 2026-09-12 (owner rule). Binding for every agent/workstream build
from now on. `versionName`/`versionCode` are NOT changed by this convention
— the suffix is an artifact-identity label, not a release bump.

## 1. The rule

```
<MILESTONE>-<AGENT>.apk      agent/workstream build      (e.g. M7.1.2-A.apk)
<MILESTONE>.apk              OFFICIAL INTEGRATION BUILD  (RESERVED)
```

- Every agent/workstream build MUST carry its agent suffix.
- The **unsuffixed** `<MILESTONE>.apk` is RESERVED. It may only be produced
  from `main` after: all intended agent branches merged, PRs reviewed,
  required tests pass, required device/integration gates pass, and `main`
  is the actual integrated state.
- Never label an agent-branch build with the unsuffixed milestone name.

## 2. Determining `<MILESTONE>`

The milestone is the app milestone the build targets — taken from the
`versionName` milestone component in `app/build.gradle.kts`
(e.g. `0.11.2-m7.1.1` → milestone `M7.1.1`).

- Do NOT blindly reuse the previous milestone number.
- Workstream/phase labels (e.g. workstation `P2.1`) describe the WORK; the
  FILENAME milestone is the app milestone being delivered against. Record
  the workstream in the delivery ledger (§4), not in the filename.

## 3. Agent registry (the `<AGENT>` letter)

| Letter | Agent / workstream |
|---|---|
| `A` | Kilo Code — primary phone development agent (workstation program) |
| `B`–`Z` | unassigned — reserved; assign on first use and record here |

One letter per agent/workstream. Two agents on the same milestone never
share or overwrite each other's artifacts — the suffix guarantees it.

Agent builds are stored/committed under their build commit (the ledger is
the index); the same agent may rebuild the same milestone — each delivery
is a new ledger row with its own SHA-256.

## 4. Delivery record (REQUIRED for every agent APK)

```
### <FILENAME>
- date:        YYYY-MM-DD
- git:         <commit sha> (branch)
- agent:       <letter> — <agent name / workstream>
- workstream:  <e.g. workstation Phase 2 / P2.1>
- apk sha256:  <sha256>
- tests:       <exact status: which suites ran, results>
- device gate: <exact status: what was verified on which device>
```

## 5. Delivery ledger

### M7.1.1-A.apk (self-build #1)
- date:        2026-09-12
- git:         9305b11 (main at build time)
- agent:       A — Kilo Code
- workstream:  workstation Phase 0/1 (master audit + JDK/Gradle/SDK/NDK bring-up)
- apk sha256:  6aa511a9abf2dbdf97ba83188fecf0b1b3709312024ca94d37b03cca85579dd0
- tests:       GuestDevToolsTest not yet existing; repo unit suite NOT run locally (CI is suite authority); compile+assembleDebug green on device
- device gate: sideloaded by owner on SM-F711B (Android 15); in-place update over the CI build succeeded; the updated app is the environment this session runs in (runtime verified live)

### M7.1.1-A.apk (self-build #2 — tab deploy)
- date:        2026-09-12
- git:         4c59781 (main at build time; + docs-only fc59046 in tree)
- agent:       A — Kilo Code
- workstream:  workstation Phase 2 / P2.1 (devtools APK asset + locale fix)
- apk sha256:  e196a6b17dbd98626d8c263ddb1fe714784823457e0d88025579f8f6bc606818
- tests:       GuestDevToolsTest 7/7 green ON DEVICE (first in-guest JVM unit tests, 2m42s)
- device gate: streamed-installed + launched on Galaxy Tab S7 SM_T870 via self-adb; topResumedActivity = app.pocketshell/.MainActivity; screenshot shows launcher UI with Linux card "Alpine · ready"; fresh rootfs provisioned on first spawn

### M7.3.apk (official integration build #1 — owner-directed local build)
- date:        2026-09-13
- git:         fa610e6 (main; integration of agent-B/ui-polish 40bd47b + agent-A/m7.2-file-explorer 7c01a21 + milestone bump)
- agent:       OFFICIAL INTEGRATION BUILD — merged+gated main (owner-directed; §6's CI-only pattern unchanged going forward)
- workstream:  M7.3 integration — Agent A File Explorer (breadcrumbs / Open-with / ZIP) + Agent B companion & UI polish
- apk sha256:  6086eb0695337eb4a7ad5eff0cd24071b4234997be9ef8ef5ab454bb1e804a7f
- tests:       integrated main: 977 unit tests; 4 failures all pre-existing on pristine main @ fc59046 in this environment (root-UID /proc ×3, /proc self-visibility ×1) + 2 device/CI-bound skips; new M7.2-A suites 28/28 green (BreadcrumbsTest 7, ZipArchiveOpsTest 21)
- device gate: Galaxy Tab S7 SM_T870 via self-adb — `adb install -r` Success (in-place, versionCode 49 / 0.13.0-m7.3 verified via dumpsys), launch verified (topResumedActivity = app.pocketshell); Files/breadcrumb UI exercised live on device on the same workstream's M7.2-A builds during development; owner confirmed the final M7.3 APK working on the adb device. Full record: docs/M7.3-INTEGRATION.md

## 6. Integration builds

The unsuffixed `M<...>.apk` continues to be produced by the existing
`android-ci` workflow from `main` (run #5 pattern: audited, suite-gated,
SHA256SUMS artifact). CI-side adoption of this filename for its artifact
labels is deferred to the integration layer; agent builds never touch it.

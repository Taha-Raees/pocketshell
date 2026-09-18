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
| `B` | Agent B — notifications / companion & UI polish (live worktree) |
| `Z` | Agent Z (ZCode) — terminal audit (Phase 4) + Settings Control Center |
| `L` | Agent L (ZCode) — M7.2 P10 agent signal bridge + M8 Home widget system |
| `C`–`Y` | unassigned — reserved; assign on first use and record here (except `L`, recorded above) |

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

### M7.3-Z.apk (agent Z — Settings Control Center)
- date:        2026-09-14
- git:         1228a06 (agent-Z/settings-control-center; first Z-registry use)
- agent:       Z — Agent Z (ZCode)
- workstream:  Settings / Control Center overhaul — THEME × MODE architecture, 10 identities (incl. animated Aurora), density controls, AMOLED-mode removal
- apk sha256:  5bf81c4c974750b012bd2eac25c958e0a2da089ada1c7782a219492e91fee34d
- tests:       full :app:testDebugUnitTest 1012 tests — 4 failures, ALL the pre-existing environment baseline on this aarch64 box (root-UID /proc denial ×3, /proc self-visibility ×1; identical set on pristine main fe45250); +24 new Control Center suites green (ThemeCatalogContrastTest 20-palette readability matrix, ThemeModeTest, AppearanceDensityTest, AuroraMotionTest, AppearanceSettingsContractTest); existing source-pin suites (HomeLauncherRowsTest, LauncherRowLayoutTest, ExternalKeyboardIntegrationTest) green
- device gate: PARTIAL — `adb install -r` Success on Galaxy Tab S7 SM_T870 (2026-09-14, network adb); interactive §59 gate NOT yet run (device dropped off adb mid-gate — hotspot doze); owner to run docs/TESTING.md §59 (18 steps). APK staged at /tmp/M7.3-Z.apk on the workstation

### M7.3-Z.apk (agent Z — Control Center II: Aurora default + responsive grid + terminal Aurora)
- date:        2026-09-15
- git:         c14ea22 (agent-Z/settings-control-center)
- agent:       Z — Agent Z (ZCode)
- workstream:  Control Center II — Aurora × Dark fresh-install default, dedicated Light Aurora palette (+ light aurora stops), terminal Aurora scrim (translucent surface, no renderer change), responsive Home grid (requested→width→min-cell→actual columns)
- apk sha256:  94786ddd828ec17f8571ca5d727080b179fb8e971b8ea419a4c642d494b9e9a3
- tests:       full :app:testDebugUnitTest 1022 tests — the SAME 4 pre-existing aarch64 environment failures only; +10 updated/new suites for CC-II (fresh-default + saved-pref preservation, responsive columns, icon×column fit, aurora scrim + light palette)
- device gate: NOT RUN — Tab SM_T870 unreachable (off hotspot) at delivery; owner to run docs/TESTING.md §60 (14 steps incl. the saved-preference migration check). APK staged at /tmp/M7.3-Z-cc2.apk

### M7.3-Z.apk (agent Z — Control Center + small iterations 1–4b, self-build #2)
- date:        2026-09-15
- git:         9f27e9f (agent-Z/settings-control-center; iteration series b143678→9f27e9f)
- agent:       Z — Agent Z (ZCode)
- workstream:  Control Center I+II plus small iterations — recent-folder row, Files toolbar terminal, keyboard-button removal, ONE-CLICK INSTALL for all nine Home tools (incl. agy direct-manifest glibc install + unofficial zcode client)
- apk sha256:  58e967269ea3556a57002ce0f79220b95345acd19f54cbeda741f1579b6f4fe9
- tests:       full :app:testDebugUnitTest 1036 tests at 9f27e9f — the SAME 4 pre-existing aarch64 environment failures only (root-UID /proc denial ×3, /proc self-visibility ×1); all new suites green (ToolInstallCatalog, RecentFolderStore, CC-I/CC-II appearance/density/scrim/contrast suites)
- device gate: NOT RUN on this build — owner to verify on device: §59/§60 pending gates PLUS the iteration-4 gates: tap an uninstalled tool (one-click install session, e.g. Cline), then the two new installers end-to-end (agy via the glibc manifest; zcode via the unofficial client)

### M7.3-Z.apk (agent Z — self-build #3: aurora-leak + drag-handle fixes)
- date:        2026-09-15
- git:         64f7264 (agent-Z/settings-control-center)
- agent:       Z — Agent Z (ZCode)
- workstream:  owner device-report fixes — (1) aurora visible on non-aurora themes (Terminal/Files top+bottom): aurora gating moved to DRAW-TIME snapshot reads (isAurora read inside drawBehind; frame loop advances only while aurora active — also a battery win), (2) Companion drag-bar touch target narrowed from full screen width to the 72dp bar
- apk sha256:  bd89f6331e40287c0ec0f4fe8e7045707f82c85ae13e72edc95ab5b14d8e27d8
- tests:       compileDebugKotlin + ui.theme suites + HomeLauncherRows source pins green; compile-level only otherwise (small-fix cadence)
- device gate: OWNER — verify: Solarized/Nord/Dracula show ZERO aurora wash anywhere (esp. Terminal + Files edges), Aurora animates as before, Appearance's Aurora card preview still glows under other themes; Companion drag bar resizes only when grabbed on the bar itself (72dp) — content on both edges receives its own touches again

### M7.3-Z.apk (agent Z — perf pass: Companion sheet drag + keyboard/terminal repaint)

- date:        2026-09-16
- git:         agent-Z/perf-companion-keyboard — perf commits d6fbaab (sheet) + 21763b4 (keyboard/terminal) + docs
- agent:       Z — Agent Z (ZCode)
- workstream:  Play-Store-blocker performance pass — (1) Companion sheet drag rebuilt on a deferred layout read (mutableFloatStateOf consumed ONLY in the canvas box's layout block): pointer moves invalidate layout alone — zero recomposition, zero WebView work per frame; canvas measures once per transition (CompanionHeights.canvasTarget, unit-pinned) and is only clipped while the sheet moves; raised/composition decisions key on SETTLED state — no more mid-gesture WebView detach + pauseAll when crossing the threshold; open/toggle/collapse animate ~220ms through the same layout-only path; release-below-threshold glides closed. (2) TerminalViewHost update no longer repaints the terminal on every recomposition (theme-generation + emulator-identity keyed; TerminalTheme.generation). (3) Deck key pressed-colors animate in draw phase (PSKey/EnterKey/ModifierButton drawBehind). (4) ModifierButtons subscribe per-slot. (5) clearOneShots early-out. NOT changed (deliberate): hold-keys dispatch on lift (m4.0.3 fix), external-keyboard visibility model, WebView settings (frozen render contract).
- apk sha256:  5ec8c1f7c4e3e0dfbde0aaa4d6067cf19be3785541bab41a01842bc13917a7c1 (staged /tmp/M7.3-Z-perf.apk); A/B baseline main@82cd3f5 staged /tmp/M7.3-Z-perf-base.apk sha256 de2681507460e385000ec7e6f05b2bb8525309db313d599c20211f6920480312
- tests:       full :app:testDebugUnitTest 1049 tests — only the SAME 4 pre-existing aarch64 environment failures (root-UID /proc denial x3, /proc self-visibility x1); new: CompanionHeights.canvasTarget pins (2) green; companion suite 42 green; keyboard suite 75 green
- device gate: OWNER — docs/TESTING.md §61 (A/B frame measurement with scripts/runtime/devtools/companion-perf-gate): sheet drag jank% + percentiles baseline vs perf, no reflow/detach mid-drag, glide-close on collapse release, deck typing under load, one-shot modifier isolation, physical-keyboard regression

### M7.3-Z.apk (agent Z — perf pass #2: owner device-feedback round)

- date:        2026-09-16
- git:         agent-Z/perf-companion-keyboard — follow-up to d6fbaab/21763b4 (Task 53 in worklog)
- agent:       Z — Agent Z (ZCode)
- workstream:  owner screenshot + report fixes — (1) deck ALWAYS pushes the Companion above it: panel height + drag cap clamp to the space above the deck (was: panel ignored the inset and overflowed under the keyboard at taller fractions — page input bars unreachable); (2) drag-up fix from the owner screenshot: while the panel moves the canvas TOP is glued under the tab strip (page rides the finger 1:1; was: frozen page left at the old height with a blank band above it); at rest the page bottom-anchors so bottom-edge input bars stay visible; (3) deck entrance inset consumed in LAYOUT only (expandVertically frames no longer recompose the root); (4) parked keyboard button back on Home (owner override — Companion makes Home typeable); vestigial imePadding dropped
- apk sha256:  84e44772493054939fc769a1889db49cb239250ac2d356125a08a5e57903b2ff (staged /tmp/M7.3-Z.apk)
- tests:       companion 42 + keyboard 75 green; full suite at the tip = 1049 tests, the SAME 4 known aarch64 env failures only
- device gate: OWNER — docs/TESTING.md §61 gates 19-22 (added this round) plus §A/§B/§C

### M7.3-Z.apk (agent Z — perf pass #3: no theme glimpse, deck off at open, tools row-major)

- date:        2026-09-16
- git:         agent-Z/perf-companion-keyboard — owner round 3 (worklog Task 54)
- agent:       Z — Agent Z (ZCode)
- workstream:  three owner reports — (1) NO THEME GLIMPSE: startup palette is the SAVED theme (synchronous onCreate read + ViewModel first-frame seeds; fresh installs still open Aurora x Dark); (2) DECK OFF AT OPEN: On-screen keyboard preference defaults to explicit-ON (owner override of the M7.1.1 default-ON contract; canvas tap / keyboard button / input focus still open the deck on demand); (3) TOOLS ROW-MAJOR on Home: one row while it fits, second row only when full, beyond that the next page (scroll dots) — replaces the fixed chunked(2) two-row layout; pins updated in HomeLauncherRowsTest + ExternalKeyboardIntegrationTest
- apk sha256:  89c30d72f45261911cc1de83d20a89ea92bba6d545a0a1230f062019819fa407 (staged /tmp/M7.3-Z.apk)
- tests:       launchers+keyboard+settings+companion 183 green; full suite at the tip = 1049 with the SAME 4 known aarch64 env failures
- device gate: OWNER — app open shows the saved theme with no Aurora flash and NO keyboard deck; Settings > On-screen keyboard ON restores always-on; tool list single row -> second row -> next page on the tablet

## 6. Integration builds

The unsuffixed `M<...>.apk` continues to be produced by the existing
`android-ci` workflow from `main` (run #5 pattern: audited, suite-gated,
SHA256SUMS artifact). CI-side adoption of this filename for its artifact
labels is deferred to the integration layer; agent builds never touch it.

### M8-L.apk
- date:        2026-09-18
- git:         4c0c65e (agent-L/m8-home-widgets)
- agent:       L — Agent L (ZCode)
- workstream:  M8 — Home Linux Widget System (slots + Servers/Storage/Agents widgets + declarative catalog layer)
- apk sha256:  524d43589f559b6bc0aa590315c960b8ca203803b324a0e39ff0e358d34cacaf
- tests:       full :app JVM suite 1199/1199 green (60 new M8 pins incl. source contract); :app:assembleDebug green
- device gate: PERFORMED 2026-09-18 on SM-T870 (Android 13) — §64 RESULTS recorded in docs/TESTING.md (A/B/D/E/F/G pass; C honest-degrade verified with the platform boundary proven closed: procfs app+guest EACCES, netlink sock_diag EACCES; aurora idle-draw cost attributed to pre-existing Control Center II)

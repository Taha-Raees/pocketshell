# PocketShell

A modern Android application that provides a **real** Linux/Unix terminal with
an Android-native UI built around it — not a terminal awkwardly wrapped in an
app.

```
REUSE → INTEGRATE → OPTIMIZE → IMPROVE
```

The terminal engine is **not** reinvented: PocketShell vendors the mature,
battle-tested Termux `terminal-emulator` + `terminal-view` modules (GPLv3,
pinned upstream commit — see `docs/THIRD_PARTY.md`) and builds a clean, modern,
honest product experience on top: a built-in terminal keyboard, a simple Home
screen, real multi-session management, and a data-driven CLI-app architecture
for genuinely user-installed software.

**License: GPL-3.0-only** (consequence of vendoring GPLv3 terminal modules).

## Product principles

1. The terminal is real: real PTY (`fork`/JNI), real shell, real programs.
2. Nothing is faked: no fake installed apps, no fake sessions, no fake installs.
3. System commands (`ls`, `vim`, `top`, …) are **commands inside the terminal**,
   never presented as "installed CLI apps" on Home.
4. The built-in keyboard lives inside PocketShell (not a system-wide IME) and
   can type everything a terminal needs.
5. Small architecture, strict milestones, upstream reuse, honest documentation.

## Repository layout

```
app/                  PocketShell application (Kotlin, Compose, Material 3)
terminal-emulator/    vendored Termux module (GPLv3, pinned) — emulator + PTY JNI
terminal-view/        vendored Termux module (GPLv3, pinned) — TerminalView
docs/                 RESEARCH · ARCHITECTURE · THIRD_PARTY · ROADMAP · TESTING · CHANGELOG
```

## Building

Requirements: JDK 17+, Android SDK (platform 36, build-tools 36.0.0) and NDK
28.2.13676358 — see `docs/RESEARCH.md §7` for the exact pinned toolchain.

```bash
./gradlew :app:assembleDebug      # debug APK → app/build/outputs/apk/debug/
./gradlew testDebugUnitTest       # upstream emulator suite + PocketShell tests
```

`local.properties` (not committed) must point `sdk.dir` at your SDK, or set
`ANDROID_HOME`.

## Status

Current state: **M7.2 P8 — home sessions integration & unified agent
activity (the consumer/UI phase: the existing Home Sessions rows now
state the same authoritative agent activity as the notifications, on
the inherited v0.11.2-m7.1.1 / versionCode 47 stamp; M7.2 P7, P6, P5,
P4, P3c, P3b, P3a, P2, P1, the M7.1.1 fix release and M7.1 release
below it)**
(see `docs/ROADMAP.md`). The stack: native PTY terminal (M1), Linux Alpine
runtime via proot with real package management and the pinned glibc layer
(M2 + M6), the Midnight/Daylight design system and workspace (M3 + M5), the
Companion embedded-web layer (M4), the Files explorer across Linux/Downloads/
SAF storage with operations, editor, Open-Terminal-Here and search (M7.0),
and M7.1 — Home launchers (Companions + Your tools grids with hide/restore,
custom tools over the ONE verify-then-launch path, 13 curated official marks
as two-variant theme pairs, x-scroll rows with scroll dots, the tools-header
packages affordance) and live external-keyboard detection, rebuilt in
M7.1.1 after the real-device failure: ONE authoritative keyboard-state
model (persistent On-screen keyboard preference + hardware state + explicit
user request), the gated terminal-canvas tap, the confirm deadline against
event storms, the configuration-change cross-check as a second detection
mechanism, and both-direction transition notices — event-driven, no
polling, no new permissions. M7.2 P0 produced the full architecture audit
(`docs/M7.2-P0-AUDIT.md`); M7.2 P1 built the notification foundation —
POST_NOTIFICATIONS requested exactly once per install on Android 13+
(when the first session exists, never re-asked), the
`notifications/` coordinator layer (event channel, deterministic ids,
DataStore posted-id ledger, startup stale sweep, FLAG_IMMUTABLE tap
intents), and intent routing on both activity paths — infrastructure
only: no event notifications posted yet, no agent detection, no
heuristics. M7.2 P2 built the session lifecycle engine: a typed
STARTING→RUNNING→FINISHED machine with the waitpid exit status surfaced
as structured exited(code)/signaled(signal) values, structured
SpawnOrigin/AgentHint launch identity at every spawn site, race-safe
transitions (duplicate-callback idempotency, the kill(0) close guard),
typed lifecycle events and the derived AgentActivityRepository —
in-memory only, no notifications, no agent claims. M7.2 P3a audited every
trustworthy signal and classified the launch identity of every
registry/catalog/custom launcher (`LaunchIdentity`: known agents vs known
non-agent tools vs never-promoted custom commands) — the type-level truth
rule being that "PocketShell launched X" is proven at spawn while "X is
running" and "X completed" remain unrepresentable. M7.2 P3b implemented
the second truth level: the /proc descendant scanner with fork-proven
correlation (ppid-chain ∪ process-group under the session's recorded
root), graded exact-token matching (`PROCFS_EXE`/`PROCFS_CMDLINE`), the
four-state contract (`NOT_APPLICABLE / UNKNOWN / NOT_RUNNING / RUNNING`
— never a completion claim, never a false RUNNING), and 2-second polling
that exists only while a known-agent session is live. M7.2 P3c implemented
the third truth level: the transition/event engine — a pure reducer folding
the detector's observations plus the manager's authoritative session state
into the deduplicated typed event vocabulary (`Launched` /
`ConfirmedRunning` with the exact pids + grade / `NoLongerDetected` —
runtime disappearance only, never completion — / `RuntimeUnknown` /
`SessionEnded`), staleness-safe, replay-free, zero own polling, no
notifications yet: the substrate the future notification phase subscribes
to without ever seeing /proc or PID trees. M7.2 P4 connected that stream to
Android's notification system — the first user-visible M7.2 phase: ONE
consumer subscribing exactly once at Application start (the replay-free
stream's correctness requirement), ONE pure truth contract turning events
into honest surfaces (confirmed running → "<RegistryName> is running";
runtime unknown → honest uncertainty in place; no longer detected → the
running claim withdrawn, never re-labeled; session ended → a one-shot
factual "Session ended" / "exited (code X)" statement about the session's
own status), deterministic per-session notification identity, defensive
dedup + tombstones, one calm `agent_runtime` channel, the FGS retention
notification untouched, zero new permission machinery, and stale surfaces
removed in-process, across processes and after process death — with the
honesty line pinned over the shipped strings: no notification can claim
completed/success/finished/failed. M7.2 P5 made those honest surfaces
ACTIONABLE: tapping a running / runtime-unknown / eligible session-ended
notification now opens PocketShell into the terminal context of the session
the notification already names — the route carries only the authoritative
session id (no PID, no /proc, no detector, no rediscovery), a pure routing
model resolves it against the manager's live list (a listed id selects
through the existing ViewModel seam; a stale id opens the app normally — a
notification can never recreate a session or respawn a process), and the
PendingIntent identity stays deterministic (request code = the notification
id). The shade's wording is byte-identical to P4 — P5 changed what a tap
DOES, never what the shade may SAY — with zero new permissions, channels or
manifest entries and the FGS untouched. M7.2 P6 verified those transitions
as a continuous device story with zero production delta (the rebuild is
byte-identical to the P5 APK). M7.2 P7 audited whether PocketShell can
KNOW that an agent is waiting for the user — and honestly concluded no
trustworthy evidence channel exists (the only documented agent channels
— Claude Code hooks, Codex notify, OpenCode plugins — require config
staging, an app-side receiver, per-session binding and a
generation concept that do not exist, and every protocol-level seam is
spoofable by arbitrary output), so NO NeedsInput state shipped: the
boundary is pinned instead (no screen scraping, no file watching, no
notification-to-terminal write path — tap-to-terminal stays the only
interaction) and the future-integration requirements are documented
(docs/M7.2-P7-WAITING-EVIDENCE-AUDIT.md). M7.2 P8 made the existing
Home → Sessions rows state the SAME runtime truth as the shade — one
pure projection over the manager's sessions + the detector's
observations (`AgentHomeSessionClaims` / `AgentActivityRepository.
homeSessionClaims`), a compact status line per row: `<Agent> — Running`
or `<Agent> — Runtime unknown` only, birth-unknown/withdrawal/non-agent
rows stay normal, claims keyed by the authoritative session id, the
notification-parity fold test-pinned, no detector, no polling, no
needs-input claim, no dashboard (docs/
M7.2-P8-HOME-SESSION-INTEGRATION.md). Full JVM suite
2074/2074 green (app 892 + terminal-emulator 145 per variant,
0 skipped, forced clean
rerun at the tip) and `assembleDebug` produces a working APK; the
**manual on-device acceptance checklists** in `docs/TESTING.md` remain
the hardware pass — the M7.1.1 gate is §47, the M7.2 P1 gate is §48, the
M7.2 P2 parity gate is §49, the M7.2 P3b runtime-detection gate is §51
(the per-agent /proc shape table), P3c requires no device gate (§52
records its observability note), the M7.2 P4 notification-shade gate
is §53 (ten steps), the M7.2 P5 notification-interaction gate is §54
(eight steps — a green build alone never completes the phase), and the
M7.2 P6 transition gate is §55 (the naturally reproducible subset — the
mid-flight unknown cycle is honestly recorded as not device-reproducible),
and the M7.2 P7 evidence-audit gate is §56 (the negative-result gate —
running wording unchanged, the on-device spoof proof, P5/P6/FGS
regressions; no needs-input device instructions exist because the state
does not exist), and the M7.2 P8 home-integration gate is §57 (Home and
the shade agree about running / unknown / absence — baseline, running,
multi-session, withdrawal, session end, regressions).

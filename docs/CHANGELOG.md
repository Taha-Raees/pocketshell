# PocketShell — Changelog

All notable changes. Milestone checkpoints are named git commits
(`M0-…`, `M1-…`, `M1.1-…` etc. — see ROADMAP.md discipline).

## [0.11.2-m7.1.1-m72p9] — 2026-09-09 — M7.2 P9 universal agent activity detection & the Linux ↔ Android signal bridge

The eleventh M7.2 phase (investigation + implementation; verdict P9A+B)
makes agent activity detection UNIVERSAL: an agent run in ANY live guest
session is now stated from real process evidence — whether it was launched
by a Home launcher tap or typed by the user into a plain Terminal session,
at any working directory.

- **THE FIX (Part-B root cause):** the reported "Kilo detected at root but
  not in a subdirectory" discrepancy was never directory-dependent — the
  old eligibility gate scanned only registry-launcher sessions
  (`SpawnOrigin.CommandApp`), so agents typed into plain Terminal sessions
  were structurally invisible (no scan, no events, no notification), while
  the "root" that worked was the launcher session's own post-agent prompt
  (spawn identity persists there). P9 replaces the gate with TWO classes:
  PREDICTED (spawn-truth token, unchanged) and DISCOVERED (every other
  live guest session scanned against the whole registry token set — the
  matched token RESOLVES the agent identity the claim names; exact-token
  rules, the correlation domain and the four-state honesty contract all
  unchanged; host-side shells stay excluded).
- **THE BRIDGE:** a session-bound launch-record channel — the registry
  launch chain now anchors the agent at exec time (a nested `sh -c`
  records its own pid/pgrp//proc-starttime and execs the agent: exec
  preserves all three, prototype-proven on real Linux) and records the
  agent's REAL exit status (P2's truth-loss point 3 restored as a fact,
  never an interpretation). The JSONL records live in the app-owned
  rootfs (`var/lib/pocketshell-agent/<token>.jsonl` — one fresh file per
  launch tap = the runtime generation; deleted with the session; 24h
  orphans swept at spawn, no timers). The detector validates the anchor
  against the live snapshot (pid + starttime — pid-reuse-proof) INSIDE
  the session's correlation domain (anti-spoof), as the new
  `LAUNCH_ANCHOR` grade. No daemon, no new polling, no PTY changes.
- **UNCHANGED:** the notification vocabulary (Running / Runtime unknown /
  factual exit wording / cancel-on-absence), the P5 routing, the P6
  transition matrix, the P8 Home claim parity (discovered sessions claim
  the resolved registry name through the same pure step), P7's NeedsInput
  verdict (no waiting claim exists), the rejected completion/success
  tiers, versionCode 47 / versionName 0.11.2-m7.1.1, the 6-permission
  set, the certificate.
- **TESTS:** +52/variant (AgentLaunchRecordsTest 12 — parser strictness,
  chain composition, real /bin/sh execution fixtures proving the anchor
  records the exec'd agent's live pid/starttime and the real exit status,
  and that stdin/stdout pass through untouched; AgentRuntimeDiscoveryTest
  19 — the discovery matrix, anchor anti-reuse/anti-spoof, birth silence,
  multi-session independence, purity; AgentDiscoveryEventTest 10 — lazy
  tracking, identity switches, staleness, discovered-session endings;
  AgentDiscoveryHomeClaimTest 7 — shade/Home parity for discovered
  claims; AgentObservationTopologyTest 4 — the Part-B reproduction pinned
  against REAL processes and REAL /proc). Two disclosed test-file
  re-scopes (the P3b-era eligibility boundary pin and the
  AgentMatchedBy enum pin now pin the P9 contract). Full JVM forced
  rerun 2178/2178 (app 944×2, terminal-emulator 145×2), 0 failures /
  0 errors / 0 skipped.

## [0.11.2-m7.1.1-m72p8] — 2026-09-09 — M7.2 P8 home sessions integration & unified agent activity (consumer/UI phase)

The tenth M7.2 phase makes the EXISTING Home → Sessions section state the
SAME authoritative agent activity the notification system already states —
one runtime truth, two views. The integration is the smallest existing
seam, not a new system: a new PURE decision step (`AgentHomeSessionClaims`)
consumes exactly the two authorities P3c/P4 already consume (the manager's
session StateFlow + the P3b detector's graded observation StateFlow),
`AgentActivityRepository` gains one more derived projection
(`homeSessionClaims` — the ONE read model's charter since P2; it stores
nothing and decides nothing), `TerminalViewModel` re-exposes it verbatim,
and `HomeScreen` collects it lifecycle-aware to render a compact status
line on the existing session rows. A row can now say only
`<Agent> — Running` (the registry display name, the same source the P4
wording uses) or `<Agent> — Runtime unknown`; a birth-unknown agent
session (the shade is silent there too — the detector's
`everObservedRunning` gates the claim exactly to P4's surface semantics),
a withdrawn (`NOT_RUNNING`) runtime, non-agent tools, custom launchers and
plain shells render the normal row, and ended sessions keep the
pre-existing `(exited)` session fact. Claims are keyed by the manager's
authoritative session id, so a removed middle session corrupts nothing, a
finished session cannot hold a claim, and a new session inherits nothing.
The NOTIFICATION PARITY is test-pinned (`AgentRuntimeHomeParityTest`, 5
tests/variant): the same authoritative sequence folded through the shipped
engine→mapping chain AND the Home projection must agree about running /
unknown / absence for every session at every step — covering the §55
device story (spawn → birth-unknown → running → unknown → withdrawal →
reappearance → exit 3 → removal), mixed multi-session worlds with
middle-session close, the R→U→R→U→R flapping storm, and the
never-announced ending; exit 0 still ends in the factual session wording.
The pure claim matrix (18 tests/variant: the two-claim vocabulary, the
withdrawal arm, non-agent silence, multi-session isolation, stale-claim
impossibility, purity) and the structural observer rules (6 tests/variant:
the claim step is decision-only — no /proc, no matching, no PID discovery,
no timers, no parsing, no regex, no persistence, no notifications; the
projection consumes only the two authorities; Home collects
lifecycle-aware and never polls; Home has NO PTY write path; the Sessions
section keeps exactly one clickable — the existing `onOpenSession` seam;
the honesty ban list holds over every Sessions literal) pin the phase
against becoming another detector. P7's verdict is untouched: generic
"Needs input" cannot truthfully exist, so P8 states no waiting claim and
adds no detector, no polling, no process heuristics, no dashboard, no Home
redesign, no second navigation, no second lifecycle owner; versionCode 47
/ `0.11.2-m7.1.1` unchanged. One pre-existing structural pin
(`LaunchIdentityIntegrationTest`'s running-vocabulary confinement) gained
the P8 consumer authorization — the same ROADMAP-authorized consumer
boundary the P3c event engine used, disclosed in the freeze audit.
Full JVM forced rerun 2074/2074 (app 892×2 = 863+29 new,
terminal-emulator 145×2), 0 failures / 0 errors / 0 skipped. Full
contract: `docs/M7.2-P8-HOME-SESSION-INTEGRATION.md`; device gate:
TESTING §57. This phase IS user-visible (the Home Sessions status line):
a new APK and bundle are delivered.

## [0.11.2-m7.1.1-m72p7] — 2026-09-09 — M7.2 P7 trusted waiting-for-user evidence audit (evidence-audit phase, P7B)

The ninth M7.2 phase, and an evidence audit by design: the mandate allowed a
production `NeedsInput` state ONLY if a trustworthy evidence source exists, so
P7 audited first and shipped the honest negative result. The real I/O path was
mapped end-to-end (raw PTY bytes are unreachable outside the vendored emulator;
stdin is one untagged door with kernel echo; the emulator's only structured
seams — title/colors/clipboard/BEL/cursor/exit — are spoofable by arbitrary
output, and OSC 133/9;4/777 + APC are silently swallowed), all nine curated
launchers were audited (bare commands, zero config staging, exit status
structurally discarded by the `sh -c; exec` chain) alongside external
documentation for their machine-readable channels, and the eight-condition
evidence standard was applied: Claude Code hooks, Codex `notify` and OpenCode
plugins classify **B — strong but agent-specific** (all require config staging,
an app-side receiver, per-session binding and a runtime-generation concept —
none of which exist — and the strongest arms are inactivity-based or
completion-flavored), the remaining six curated agents classify **D — no
signal found**, so NO candidate passes and NO `NeedsInput` state shipped.
Production delta is ZERO; the boundary is pinned instead:
`AgentRuntimeWaitingEvidenceBoundaryTest` (4 tests/variant — the session
client's attention seams have no path into the evidence machinery; zero
screen-scraping references app-wide; the runtime surface watches no files and
reads no terminal text; the notification layer cannot write to the terminal —
tap-to-terminal stays the only interaction), plus `needs input` added to the
honesty ban lists. TESTING §56 records the negative-result device gate
(running wording unchanged, the on-device spoof proof, P5/P6/FGS regressions).
Rejected heuristics (text regex, BEL/title claims, exit-code-derived state,
inactivity/CPU/sleep, OSC 9/777 bodies) are documented with the spoofability
proof, never shipped. The future-integration requirements (versioned per-agent
evidence contract, env-var session binding, runtime-generation concept,
app-side receiver, launcher-staging policy, typed attention event orthogonal
to RUNNING, and the response-action safety contract) are recorded in the audit.
No APK was built — no production behavior changed; the P6-audited APK remains
the current device artifact (the phase's assembleDebug rebuild measured
byte-identical, sha256 `69ab4402…c57f`). Full JVM suite forced rerun
2016/2016 (app 863×2 = 859+4 new, terminal-emulator 145×2),
0 failures / 0 errors / 0 skipped. Full contract:
docs/M7.2-P7-WAITING-EVIDENCE-AUDIT.md.

## [0.11.2-m7.1.1-m72p6] — 2026-09-09 — M7.2 P6 runtime notification device-state refinement (verification phase)

The eighth M7.2 phase, and a verification phase by design: P6 adds NO
production behavior and NO new detection power. The runtime state
transitions the P3c event engine and the P4 truth contract already
implement were audited end-to-end, pinned as one continuous JVM story
(new `AgentRuntimeNotificationTransitionMatrixTest`, 11 tests/variant:
the RUNNING→UNKNOWN→RUNNING in-place cycle on ONE deterministic
notification identity, the RUNNING/UNKNOWN→NOT_RUNNING cancellation-only
arms, UNKNOWN at session end yielding the factual exit statement, the
duplicate-delivery storm, the flapping anti-accumulation case, and the
exit-0-never-success pin through the whole fold), and gated on hardware
where naturally reproducible (TESTING §55: withdrawal, in-session
reappearance, birth silence, `exit` / `exit 3` facts, tab-close, the §54
tap regression, FGS). The mid-flight notification-level UNKNOWN cycle is
honestly documented as NOT device-reproducible in P6. The debug APK
rebuild is byte-identical to the P5 audited build (sha256 `69ab4402…c57f`)
— the strongest possible proof of zero production delta; versionCode 47 /
0.11.2-m7.1.1 unchanged. Full JVM suite forced rerun: 2008/2008 (app
859×2 = 848+11 new, terminal-emulator 145×2), 0 failures / 0 errors /
0 skipped. Full contract: docs/M7.2-P6-RUNTIME-STATE-TRANSITIONS.md.

## [0.11.2-m7.1.1-m72p5] — 2026-09-09 — M7.2 P5 notification interaction, session context & honest state transitions

The seventh M7.2 phase, and the second user-visible one: the honest
agent-runtime notifications became actionable. Tapping a running /
runtime-unknown / eligible session-ended surface now opens PocketShell into
the terminal context of the session the notification already names. The
route carries only the authoritative session id (no PID, no /proc, no
detector, no rediscovery); a new pure model
(`AgentRuntimeNotificationRouting`) resolves the tap against the session
manager's live list — a listed id selects the session through the existing
ViewModel seam; a stale id opens the app normally. A notification can never
recreate a session, respawn a process, or fake a selection. PendingIntents
stay deterministic (request code = the notification id = base + sessionId),
so per-session taps can never collide or mutate another session's surface.
P4's truth contract is byte-unchanged: no completion/success/finished/failed
claim exists anywhere in what the shade may say; the unknown surface stays
uncertain; NoLongerDetected still withdraws; exit codes stay verbatim.
Zero new permissions, channels, or manifest entries; the FGS retention
notification and every frozen subsystem untouched. JVM suite forced rerun:
1986/1986 (app 848×2 = 821+27 new, terminal-emulator 145×2), 0 failures /
0 errors / 0 skipped. APK at the inherited versionCode 47 /
0.11.2-m7.1.1 stamp (the -m72p5 suffix is filename-only). Device gate:
TESTING §54 (eight steps — the tap paths are hardware-verifiable only).
Full contract: docs/M7.2-P5-NOTIFICATION-INTERACTION.md.

## [0.11.2-m7.1.1-m72p4] — 2026-09-09 — M7.2 P4 notification consumption of the runtime event engine

The sixth M7.2 phase, and the first USER-VISIBLE one: P3c's deduplicated
runtime-transition stream now drives honest Android notifications. One
consumer (`AgentRuntimeNotificationConsumer`) subscribes exactly once at
Application start (the replay-free stream's correctness requirement) and
folds every event through one pure truth contract
(`AgentRuntimeNotificationMapping`): confirmed running → ongoing
"<RegistryName> is running" surface; runtime unknown → the surface updates
to honest uncertainty in place; no longer detected → the running claim is
withdrawn (never re-labeled as finished); session ended → a one-shot factual
"Session ended" / "Terminal session N exited (code X)" statement about the
session's own waitpid status — exit 0 is never agent success. Deterministic
notification identity (AGENT_RUNTIME_BASE + sessionId), defensive dedup
(posted/everPosted/tombstones), one calm new channel (`agent_runtime`,
"Agent activity") with setOnlyAlertOnce, the FGS retention notification
untouched and pinned, zero new permission machinery (the P1 gate remains the
only path), stale surfaces removed in-process (tombstones), across processes
(the P1 startup sweep) and after process death (nothing restored, nothing
faked). The honesty line is pinned over the shipped string literals: no
notification can claim completed/success/finished/failed. No version bump
(inherited vc47 / `0.11.2-m7.1.1` stamp), no new permissions, no UI beyond
the shade surfaces. Full contract: `docs/M7.2-P4-NOTIFICATION-CONSUMPTION.md`;
device gate: `docs/TESTING.md` §53 (ten steps on hardware).

## [0.11.2-m7.1.1-m72p3c] — 2026-09-09 — M7.2 P3c runtime transition & event engine

The fifth M7.2 phase: the third truth level — trusted runtime transitions.
The pure `AgentRuntimeTransitions.reduce` folds the P3b detector's
observations plus the manager's authoritative session state into the typed
`AgentRuntimeEvent` vocabulary (Launched / ConfirmedRunning with the exact
pids + grade / NoLongerDetected — runtime disappearance only, never
completion — / RuntimeUnknown / SessionEnded with the session's own
waitpid status or the removal cause), deduplicated per session in memory
(same-state re-observation = no event; the silent birth-UNKNOWN emits
nothing and can never seed an absence), staleness-safe (a detector result
arriving after session closure can never fabricate an event), and
published on a replay-free SharedFlow the future notification system
subscribes to without ever needing /proc, process groups, PID ancestry,
token matching or polling. Internal infrastructure only: no user-visible
change, no notifications posted, no UI, no /proc access in the event
layer, no version bump (inherited vc47 / `0.11.2-m7.1.1` stamp), no new
permissions. Full contract: `docs/M7.2-P3C-EVENT-ENGINE.md`; observability
note: `docs/TESTING.md` §52.

- **Added**: `terminal/AgentRuntimeEvents.kt` — the pure event vocabulary,
  inputs, per-session transition memory and the exhaustive transition
  matrix (launch / confirm / disappearance / uncertainty / session end,
  with reappearance and the defensive contract guards).
- **Added**: `terminal/AgentRuntimeEventEngine.kt` — the observer-only
  seam: ONE sequential collector over the manager's `sessions` and the
  detector's `observations` StateFlows, zero own polling, woken from the
  manager's spawn path beside the detector, `Log.d AgentRuntimeEvents`
  transitions as the diagnostic channel.
- **Added**: `AgentActivityRepository.agentRuntimeEvents` — the re-exposed
  event stream (stores nothing, decides nothing, no replay).
- **Changed**: `TerminalSessionManager.spawn` wakes the event engine
  (one idempotent line beside the P3b detector wake — no transition path
  touched); the P3a running-vocabulary confinement pin names the two P3c
  files as the authorized consumer seam.
- **Tests**: +43 per variant — `AgentRuntimeEventsTest` (29 pure) +
  `AgentRuntimeEventEngineIntegrationTest` (14 structural). FULL JVM
  forced `--rerun-tasks`: 1850/1850 green (app 780×2 + terminal-emulator
  145×2), 0 failures / 0 errors / 0 skipped.
- **Build**: app-debug.apk 30,530,112 B sha256 976cca91…e0c at the
  inherited stamp; the unchanged 6-permission set; cert d96a6f66…8bf659;
  all five P3c symbol groups in classes14.dex. Delivery: the git bundle
  cut at the P3c record tip (the APK is the regression gate, not the
  delivery — no device-visible change exists).

## [0.11.2-m7.1.1-m72p3b] — 2026-09-09 — M7.2 P3b runtime agent detection via /proc

The fourth M7.2 phase: the second truth level — runtime process evidence
that a launched agent is currently running — with the honesty contract
intact. Internal evidence layer only: no user-visible change, no
notifications, no completion claims, no version bump (inherited vc47 /
`0.11.2-m7.1.1` stamp), no new permissions. The full investigation is
`docs/M7.2-P3B-RUNTIME-DETECTION.md`.

- **Controlled procfs experiments first** (E1–E5,
  `scripts/procfs_experiments_p3b.sh`): same-UID cmdline/stat/exe
  readability; the launch-chain shape (the chain rides ONE argv element
  of the intermediate shell — exact-element matching cannot false-hit
  it); the kernel shebang contract (interpreter-hosted CLIs carry the
  invoked name at argv[1], exe reads the interpreter); orphans keep their
  process group while losing their ppid chain; the double-escape blind
  spot; the unrelated same-name negative control.
- **Correlation, not global name matching** (`AgentDescendantCorrelator`):
  a process is attributed to a session ONLY through its fork-proven
  correlation root — `SessionEntry.shellPid`, recorded by the manager ON
  the real fork signal — via PPID-chain descent OR process-group
  membership (`pgrp == rootPid`). Both domains are kernel-safe; the union
  covers orphans and regrouped descendants; a random same-name process
  elsewhere on the device satisfies neither.
- **Graded matching** (`AgentProcessMatcher`; `AgentMatchedBy` + the
  ROADMAP-named `PROCFS_EXE` / `PROCFS_CMDLINE` compile-time-forced
  extensions): the exe image basename, or an exact argv element
  (argv[0], or the shebang script path at argv[1]). No substring matching
  ever — `codex` cannot match `my-codex-wrapper`, `codex-helper`,
  `something-codex`; the confidence grade travels with every claim.
- **The four-state contract** (`AgentRuntimeState`:
  `NOT_APPLICABLE / UNKNOWN / NOT_RUNNING / RUNNING`): a scanner that
  never saw the agent answers UNKNOWN (not-started-yet vs already-exited
  is indistinguishable); proven disappearance is NOT_RUNNING — explicitly
  not completion, not success; a failed scan is UNKNOWN (missing evidence
  is not absence); ambiguity is UNKNOWN; RUNNING always carries the pids
  and grade that caused the claim. The pure decision step
  (`AgentRuntimeDetection.compute`) is deterministic and fully tested.
- **Conservative polling** (`RuntimeAgentDetector`): a 2-second tick that
  exists ONLY while a live `LaunchIdentity.KnownAgent` session exists;
  parks with zero scheduled work otherwise; one cheap hidepid-truncated
  /proc pass per tick; no AlarmManager/WakeLock/WorkManager; state
  transitions logged (existing logs as the diagnostic channel — no
  debugging UI).
- **One lifecycle authority preserved**: the detector only OBSERVES the
  manager's StateFlow; `AgentActivityRepository.runtimeActivities` is the
  derived four-state read model (stores nothing, decides nothing).
  Custom tools and known non-agent tools never activate the scanner and
  map to NOT_APPLICABLE explicitly.
- **Tests (+49 per variant)**: `AgentRuntimeDetectionTest` (38 pure —
  matching safety incl. the mandated substring negatives, correlation
  incl. orphans/double-escape/negative controls, the full state decision
  table, purity, pruning) + `AgentRuntimeDetectionIntegrationTest`
  (11 structural pins — no notification APIs, no completion vocabulary,
  no OSC/terminal-output parsing, /proc confined to the reader seam, the
  minimal manager extension on the real fork signal, observer-only
  detector, the parking polling discipline); the two P3a boundary pins
  evolved exactly as the ROADMAP authorized (completion vocabulary banned
  repo-wide; running-state vocabulary confined to the evidence seam;
  `AgentMatchedBy` now exactly LAUNCH_METADATA + the two procfs grades).
- Full JVM forced rerun: **1764/1764 green** (app 737 = 688+49 new,
  terminal-emulator 145, both variants, 0 failures / 0 errors / 0
  skipped); APK assembled and audited at the inherited stamp (6-permission
  set unchanged, cert d96a6f66…8bf659 unchanged, all P3b symbols in the
  dex). Device-gated remainder: TESTING §51 (each registry agent's real
  /proc shape; hidepid/SELinux listing behavior).

## [0.11.2-m7.1.1-m72p3a] — 2026-09-08 — M7.2 P3a trusted agent signal audit & detection design

The third M7.2 phase: audit-first, then the minimum internal metadata
plumbing the evidence supports — no user-visible change, no version bump
(phase checkpoint on the inherited vc47 / `0.11.2-m7.1.1` stamp), no new
permissions. The audit and the detection-classification matrix are
`docs/M7.2-P3A-DETECTION-MATRIX.md`.

- **The truth rule, type-level**: "PocketShell launched X" (spawn
  metadata, proven), "X is currently running" (NOT knowable at the
  session layer — the direct child is proot, the `<command>; exec <sh>`
  chain keeps the session alive after the agent exits, and the fork
  signal fires before the command line even runs) and "X completed" (NOT
  knowable — the agent's own exit status is structurally discarded by the
  launch chain) are now distinct in the type system: `LaunchIdentity`
  carries the launch evidence and NO running/completion/exit field.
- **Launch identity classification** (`terminal/LaunchIdentity.kt`): the
  sealed `KnownAgent` / `KnownNonAgentTool` / `CustomOrUnknown` model,
  resolved by a pure `of(origin, agent)` against the REAL registry
  (9 agent launchers) and catalog (5 known non-agent tools) objects.
  Custom tools resolve against NO registry — a user-chosen name ("My
  Agent") never promotes a command to an agent; unresolvable ids degrade
  honestly; plain shells claim no identity.
- **Derived projection**: `AgentActivityRepository.classifiedLaunches` —
  per-session classified launch identity computed from the manager's
  authoritative state; stores nothing, decides nothing (the P2 read-model
  contract preserved). One lifecycle authority unchanged
  (`TerminalSessionManager` untouched; no spawn-site change).
- **Truth-boundary tests** (23 new): `LaunchIdentityTest` (14 pure — the
  full matrix incl. never-promotion and phase-independence) and
  `LaunchIdentityIntegrationTest` (9 structural — the manager stays
  registry-free, no agent running/completion API exists anywhere in the
  main sources, the P1 notification / P2 vocabulary boundaries carried).
  Full JVM forced rerun 833/833 (app 688 + TE 145, 0 skipped); APK
  audited at the inherited stamp (30,802,996 B, 598829a8…, cert and
  6-permission set unchanged). JVM-verified only by design — TESTING §50.

## [0.11.2-m7.1.1-m72p2] — 2026-09-08 — M7.2 P2 session lifecycle engine & structured exit status

The second M7.2 phase: internal lifecycle infrastructure built exactly on
the P0 audit's provable state machine (`docs/M7.2-P0-AUDIT.md` §10.3 /
PART I). Phase build on the inherited vc47 / `0.11.2-m7.1.1` stamp
(`-m72p2` is filename-only). No new permissions; TerminalService, the
P1 notification foundation and the M7.1.1 keyboard system untouched; no
user-visible UI change.

- **Typed lifecycle model** (`terminal/SessionLifecycle.kt`): each
  session's phase is a real-state value — `STARTING` (entry exists, the
  PTY child is NOT forked yet; the audit's lazy-fork fact),
  `RUNNING` (the direct child is alive), `FINISHED` (the child exited
  with its recorded status) — with REMOVED modeled by tab removal plus a
  typed event, never a stored flag. `SessionLifecycleState`'s private
  constructor makes invalid combinations unrepresentable: FINISHED always
  carries a non-null exit status, STARTING/RUNNING never do. The stored
  `isFinished: Boolean` is retired from the entry (the getter is now
  DERIVED from the phase — no second truth inside the record).
- **Structured exit status**: `ExitStatus.Exited(code)` /
  `ExitStatus.Signaled(signal)` — a faithful mapping of what
  `JNI.waitFor`/waitpid actually provides (positive = `WEXITSTATUS`,
  negative = negated `WTERMSIG`); no "unknown" variant is invented (a
  FINISHED state can only be produced by a real waitpid delivery).
- **Real signals only, ONE owner**: `TerminalSessionManager` stays the
  single authoritative lifecycle owner; transitions run through the pure
  machine and only on the main handler. The previously-discarded
  `setTerminalShellPid` upstream callback is now the REAL
  STARTING→RUNNING fork signal (the lazy fork is observed, never polled);
  the existing `onSessionFinished` waitpid delivery drives →FINISHED with
  the structured status. Duplicate/out-of-order callbacks are REJECTED
  with a logged reason and can never corrupt a recorded status; a finish
  delivery for a removed session is logged, never swallowed.
- **Close-path race safety**: `closeSession` guards `finishIfRunning()`
  behind `pid > 0` — upstream `isRunning()` is true for a never-forked
  `mShellPid == 0`, where the SIGKILL would go to pid 0, the caller's
  whole process group. A STARTING session closes by clean removal.
- **Structured launch identity**: every spawn site passes its
  `SpawnOrigin` (`Shell`, `LinuxShell`, `FilesTerminal`,
  `CommandApp(id)`, `CatalogApp(id)`, `CustomTool(id)`); the three
  named-launcher paths additionally carry `AgentHint(displayName,
  command, matchedBy=LAUNCH_METADATA)` — real spawn-time metadata only,
  never a process claim (the audit's Tier-1 line; procfs-graded evidence
  stays P3b and is deliberately not pre-declared).
- **Typed lifecycle events + the derived read model**: the manager emits
  `SessionLifecycleEvent` (Started/Finished/Removed, each with the full
  identity block) at exactly the mutation sites; `AgentActivityRepository`
  is the ONE derived projection (`runningLaunchedSessions`,
  `finishedLaunchedSessions`) — it stores nothing, decides nothing, and
  never touches notifications.
- **In-memory by design**: lifecycle/exit state is process-scoped like
  the sessions themselves — process death loses everything honestly
  (START_NOT_STICKY, no restoration); no DataStore is added (a different
  concern from P1's permission flag).
- **Tests**: +30 JVM tests (810/810 total, forced rerun, 0 skipped):
  the pure transition truth table (16) and 14 structural integration
  pins. Includes the verification-honesty fix: the P1 structural pins
  previously resolved sources only from a project-root CWD and silently
  skipped under the standard module-dir runner — they now RUN (dual
  candidates, the established convention) and pass.
- **Deliberately NOT in P2**: no notifications posted, no agent
  detection, no completion/waiting claims, no `/proc` scanning, no
  OSC 133, no persistence, no UI changes.
- Real-device regression checks are the mandatory docs/TESTING.md §49
  gate (a parity gate — P2 should change nothing user-visible).

## [0.11.2-m7.1.1-m72p1] — 2026-09-08 — M7.2 P1 notification foundation

The first M7.2 production code: infrastructure only, per the approved P0
audit (`docs/M7.2-P0-AUDIT.md`). Phase build on the inherited
vc47 / `0.11.2-m7.1.1` stamp (the M7.x phase-build precedent — the
`-m72p1` suffix is filename-only). No new permissions; TerminalService
and the M7.1.1 keyboard system untouched.

- **POST_NOTIFICATIONS is finally requested at runtime** (the P0 audit's
  largest ready-made gap): on Android 13+ the native permission dialog is
  shown exactly once per install, fired when the FIRST terminal session
  exists — the moment notifications become meaningful. The request flag
  is persisted to the new `notifications` DataStore BEFORE the dialog
  opens, so rotation, activity recreation and process death can never
  re-arm it; a denial (or the system's own implicit prompt being denied —
  the pre-33-targetSdk behavior) is respected and never re-asked; denial
  never blocks any terminal functionality. Pre-13 devices never see a
  request.
- **NotificationCoordinator** (`app.pocketshell.notifications/`) — the
  ONE output/integration layer for M7.2 notifications: owns the new
  `session_events` channel (importance Default, created idempotently
  here and only here — the `terminal_sessions` FGS channel stays
  TerminalService's), posts with deterministic ids (`EVENT_BASE +
  sessionId`, refusing silent wraparound), builds `FLAG_IMMUTABLE`
  content intents carrying the tap-routing extra, and records posted ids
  in a DataStore ledger. It owns no session/agent state, scans nothing,
  times nothing, infers nothing — the no-second-truth-source rule.
- **Startup stale-notification sweep**: at app start the coordinator
  cancels exactly the coordinator-owned event notifications that
  outlived the process (process death loses session state —
  START_NOT_STICKY, no restoration), and can never touch the FGS
  notification (id 1) or anything posted outside the coordinator.
  P1 posts no production events yet, so the sweep is dormant until P2's
  first real posts.
- **Tap routing on both activity paths**: MainActivity now processes
  notification intents at cold start (onCreate) AND for the existing
  instance (onNewIntent — unhandled since forever, the P0 audit §8.4
  finding) through one exhaustive `NotificationRoute` handler; unknown
  extras stay honestly unrouted.
- **Tests**: +34 JVM tests (780/780 total, forced rerun): the permission
  truth table, deterministic identity, the routing parser, and 14
  structural integration pins over the real sources.
- **Deliberately NOT in P1**: no production event notifications, no
  agent detection, no completion claims, no waiting-for-input
  heuristics, no `/proc` scanning, no OSC 133 (P2+ scope, per the audit).
- Real-device verification of the permission flows, both tap paths and
  the channel layout is the mandatory docs/TESTING.md §48 gate.

## [0.11.2-m7.1.1] — 2026-09-08 — M7.1.1 external-keyboard detection fix

The M7.1 P3 real-device failure fix (Samsung SM-F711B / Galaxy Z Flip 3,
One UI, API 35): detection passed every JVM suite and still failed on
hardware. The release rebuilds the detection and visibility layers against
the audited causes and defines the mandatory real-device gate (TESTING
§47). No new permissions; the M7.1 architecture and the M6 frozen surfaces
are untouched outside the keyboard system.

- **Fix 1 — the canvas tap no longer cancels the auto-hide** (the primary
  defect): the terminal-canvas tap used to ride the manual-open path and
  clear the suppression, so the deck popped back up on the next touch of
  the terminal — the most-used gesture in a terminal app. The gate now
  lives in the authoritative model (`canvasTapReopen`): with an external
  keyboard connected the tap never reopens the deck; without one the
  m4.0.12 tap-to-reopen behavior is unchanged.
- **Fix 2 — the confirm deadline**: the M7.1 stability window restarted on
  every input-device event with no ceiling, so periodic
  `onInputDeviceChanged` re-announcements (Bluetooth LE HID reality on OEM
  stacks) could starve the confirmation forever. A hard 2,000 ms deadline
  from the first unconfirmed event now guarantees the scan and at most one
  transition under any event storm; sub-window flaps still never flicker.
- **Fix 3 — a second detection mechanism**: the Application-level
  `ComponentCallbacks2.onConfigurationChanged` cross-check feeds the same
  coalescing detector — an independent system path that covers OEM stacks
  which can miss `InputManager.InputDeviceListener` callbacks for
  Bluetooth HID (re)connection. The launch scan and the ON_RESUME rescan
  are unchanged.
- **Fix 4 — both-direction notices**: exactly one notice per confirmed
  transition — "External keyboard detected — onscreen keyboard disabled."
  and the spec's "External keyboard disconnected." (preference-aware: the
  disconnect copy never claims the deck returned when the preference keeps
  it off). Still in-app only: zero new permissions, no channel, no spam.
- **Fix 5 — the persistent On-screen keyboard preference**: the runtime
  deck state + the M7.1 auto-hide opt-out are replaced by
  `onscreen_keyboard_enabled` (DataStore, default On) — the user's
  preference; detection is a temporary runtime override that never writes
  it; a disconnect re-evaluates the preference (On returns the deck, Off
  never forces it back on). The user's explicit [⌨] / deck actions always
  win and never touch the preference.
- **One authoritative state system**: `ExternalKeyboardVisibilityModel`
  (userEnabled + externalConnected + manualRequest →
  shouldShowOnscreenKeyboard) owns the derivation; the root composable
  observes it and holds no local copy (the M7.1 suppression memory and the
  SUPPRESS/RESTORE policy are retired).
- **Tests**: ExternalKeyboardVisibilityModelTest (14: decision matrix, the
  four spec transitions, the request lifecycle, the canvas-tap gate),
  ExternalKeyboardDetectorTest (14: directional notices + the
  storm/deadline proofs alongside the M7.1 debounce semantics),
  ExternalKeyboardPredicateTest (11, unchanged), the structural
  ExternalKeyboardIntegrationTest (11: the new wiring pins). The
  ExternalKeyboardPolicyTest is retired with the policy. Clean rerun:
  746/746 (app 601 + terminal-emulator 145), 0 failures, 0 errors.
- **Stamp**: versionCode 47, versionName 0.11.2-m7.1.1 (the sub-milestone
  precedent; the guest-side app-stamp gate accepts it). APK 30,451,377 B,
  sha256 5d876141…e5dc1; the UNCHANGED 6-permission set; exactly 26
  launcher icon entries; dex carries ExternalKeyboardViewModel / Detector
  / Notice / Scanner / VisibilityModel; cert d96a6f66…8bf659 unchanged —
  installs in place over every earlier build.

## [0.11.1-m7.1.0] — 2026-09-08 — M7.1 release (launchers, themed marks, external-keyboard intelligence)

The M7.1 milestone, closed and frozen: five delivered phases — P1 Home
launchers, P2 the launcher UI repair + official icons + Antigravity, P2.1
the owner-supplied mark refresh + Aider removal, P2.2 the theme-scheme
two-variant icon system + x-scroll home rows + the packages affordance, and
P3 live external-keyboard detection with automatic on-screen keyboard
control. Each phase's detail lives in its own commit and the phase entries
below; this entry records the release cut itself.

- **M7.1 release stamp**: versionCode 46, versionName 0.11.1-m7.1.0 (the
  M5.1.0 precedent — a sub-milestone release bumps the semantic patch digit
  and the milestone suffix; the five phase builds deliberately shipped on
  the inherited vc45 / 0.11.0-m7.0.0 stamp). Installs in place over every
  earlier pinned-cert build (cert d96a6f66…8bf659, unchanged since v0.4.1).
- **Clean-rerun release verification**: the FULL JVM suite forced
  --rerun-tasks at the release stamp — 734/734 effective green (app 589 +
  terminal-emulator 145), 0 failures, 0 errors — including the four P3
  suites (device predicate, virtual-time detector transitions, suppression
  policy, structural integration contract), the P2.2 dual-theme + home-rows
  pins, and the P2.1 aider-absence pins.
- **Fresh release APK audits** (on the exact release bytes, sha256
  2d298c85…27eaa0, 30,448,845 B): aapt2 badging versionCode='46'
  versionName='0.11.1-m7.1.0'; the UNCHANGED 6-permission set; exactly 26
  launcher icon entries (13 dark + 13 light); dex carries
  ExternalKeyboardDetector / ExternalKeyboardViewModel /
  ExternalKeyboardPolicy / ExternalKeyboardNoticeBar (P3) and
  LauncherScroller / ScrollDots (P2.2) with PackagesFooterLink and every
  aider string ABSENT (P2.1); targetSdk 28; cert d96a6f66…8bf659.
- **Cross-phase functional verification retained** (structural + JVM, per
  the honesty discipline): P1 launcher model (companion + CLI tool grids,
  hide/restore, custom tools over the ONE verify-then-launch path), P2
  launcher UI repair (the shared weighted-row shape), P2.1/P2.2 icon
  provenance and theming (26 assets, live theme flips, imported copies
  outrank), P3 detection→suppression→restore loop (policy state machine:
  the user's manual keyboard state is never destroyed, an explicit reopen
  cancels suppression, a disconnect hands the state back exactly). The
  REAL attach/detach, launch-attached, persistence-across-restart and
  full-regression passes stay honest DEVICE gates (§41-§45).
- **Freeze**: M7.1 is closed — no further M7.1 work. The next milestone
  (M7.2 — notification & agent-activity system) starts AFTER this freeze
  and does not ride in this release.

## [0.11.0-m7.0.0] — 2026-09-07 — M7.0 release (Linux/Android files, search, editor, terminal integration)

The M7.0 milestone: a real file explorer across the PocketShell Linux area,
the app-owned PocketShell Downloads shelf, and user-granted Android folders
(SAF), with copy/move/paste/delete, single and multi-select, a quick text
editor, Open Terminal Here, and recursive name search — closed by the P9
integration pass and three device-reported search-UX fixes.

- **P9 search scrolling (root cause)**: the Files screen never cleared the
  shared keyboard deck's inset — the Terminal, Editor, and Companion
  surfaces push themselves above the root-mounted deck, but Files did not,
  so the listing AND search-results viewport extended behind the deck. A
  result list shorter than the (too tall) viewport had nothing to scroll
  while its tail sat hidden under the deck ("cannot scroll"); a long one
  could never reveal its last rows. FilesScreen now follows the established
  inset rule (deck height when mounted, navigation-bars padding when
  collapsed), so every row of both surfaces is reachable.
- **P9 search-result actions**: long-pressing a result lands on the
  result's parent directory (the tap behavior) and opens the SAME
  contextual action sheet as an explorer row — resolved from the FRESH
  listing, so Open / Open Terminal Here (Linux area, directories only) /
  Copy / Move / Share / Export / Rename / Delete all route through the
  existing per-entry operations with the landed directory as context.
  No second operations engine; vanished entries never open a stale sheet.
- **P9 one close behavior**: the duplicated field-row close arrow is gone.
  The header X (the same icon that opens search) and system Back close
  search; the in-field ✕ only clears the query. Search triggering itself is
  unchanged — no Search button, no submit UI.
- **M7 features carried by this release** (each phase's detail lives in its
  own entry/commit): P1–P4 explorer + per-entry operations; P5 SAF bridge
  (system picker grants, share/import/export); P6 quick text editor +
  P6.1 Downloads labels; P7/P7.1 Open Terminal Here (tapped folder) +
  environment-matching terminal "+"; P8 recursive name search inside the
  selected area (literal, case-insensitive, symlink-safe, honestly
  limited); P8.1 multi-select copy/move/delete through the unchanged
  per-entry engine with honest aggregates.
- Version: versionCode 45, versionName 0.11.0-m7.0.0. The 6-permission set,
  the M6 frozen architecture, and the embedded rev=2 glibc layer asset are
  byte-for-byte unchanged; full JVM suite 653 green.

## [0.10.0-m6.0.4] — 2026-09-06 — M6 Phase-C adversarial closure audit

The M6.0.3 device gate reached 27/27 ALL GREEN (doctor correctness proven on
the real device). Per the Phase-C mandate, the runtime phase was then
audited adversarially before closure: corruption/self-healing, concurrency,
loader ownership vs the gcompat package, extractor security, environment
contamination, filesystem collisions, doctor prediction accuracy, and
lifecycle sizing. Three real engineering issues were found and fixed (all
regression-pinned), and the audit tooling ships as a permanent drill suite.

- **Integrity probe behind the marker (C2.2/C2.3/C2.6)**: the layer fast
  path trusted ONLY the marker text — a deleted/clobbered loader or core
  library reported healthy. Alpine's gcompat package provably owns
  `lib/ld-linux-aarch64.so.1` and ships a real ELF shim there (verified from
  the actual 1.1.0-r4 package bytes), so `apk fix/reinstall/upgrade gcompat`
  could reclaim the loader behind a valid marker. The fast path now also
  runs a structural integrity probe (loader symlink must resolve to the
  canonical Debian loader; load-bearing files must exist) and self-heals by
  re-extraction. Pins: loader-deleted, gcompat-reclaim, lib-deleted,
  doctor-deleted, healthy-fast-path tests.
- **In-place re-extraction wiped the multiarch directory mid-run (C3/C12)**:
  the layer ships `lib/aarch64-linux-gnu -> ../usr/lib/aarch64-linux-gnu`
  BEFORE the files it points to, and the old symlink replacement used
  `File.deleteRecursively`, which FOLLOWS directory symlinks — every
  re-extraction erased all layer libraries, then rewrote them. Converged,
  but opened a no-libs window observable to concurrent apk operations.
  Fixed: symlink nodes are deleted as nodes; all recursive deletes are
  NOFOLLOW; entries routed through earlier symlink entries are refused
  (fail closed). Pins: foreign-sentinel survival test, real-archive
  extract+re-extract test, hostile-intermediate-symlink test.
- **Session prep ran on the UI thread (C1.1/C3)**: the heavy guest
  preparation (asset read + sha-256 + re-extraction path) executed
  synchronously in the click handler. Session creation is now two-phase —
  `prepareLinuxSession` on Dispatchers.IO, the PTY spawn on the main
  thread — and `GuestGlibcRuntime.ensureInstalled` is single-flight, so
  concurrent sessions serialize instead of racing the extraction.
- **Extractor hardening (C12, both extractors)**: NOFOLLOW tree walks for
  every staging/runtime cleanup; symlink-parent fail-closed guard;
  (`RuntimeStorage.cleanupTransient`/`clearRuntime` included — the minirootfs
  ships hundreds of symlinks). Both pinned archives surveyed: 361 symlinks,
  zero escaping the guest root (`scripts/audit_c12_symlink_containment.py`).
- **Permanent audit tooling**: `runtime-tests/adversarial_closure_audit.sh`
  (probe = C7 doctor prediction accuracy + C8 environment + C9 filesystem
  ownership + C10/C11 timing; drill-c2 = corruption drills with honest
  detection checks; drill-c4 = gcompat loader-reclaim drill; drill-c5 =
  apk update/upgrade/add/del survival; heal = post-session self-heal
  verification). `scripts/audit_c6_doctor_evidence.sh` (20/20) pins the
  doctor's decision tree and malformed-input battery in the sandbox.
- **Documentation**: DUAL_LIBC.md §8 (precise engineering claim, integrity
  probe contract, gcompat coexistence verdict, environment disclosure,
  extractor hardening), KNOWN_LIMITATIONS.md §5–§7 (classified).
- JVM suite 397 -> 406 leaf cases, 0 failures (incl. the built-APK asset
  pin against the vc44 APK). versionCode 44, versionName 0.10.0-m6.0.4.

## [0.10.0-m6.0.3] — 2026-09-06 — M6.0.3: the doctor correctness gate

Device gate #3 PASSED 24/24 on real hardware (real Debian glibc 2.41 loader,
Cline 3.0.61 end-to-end). What failed was the DIAGNOSTIC TOOL:
`pocketshell-doctor` verdicted UNSUPPORTED for every versioned glibc binary —
the real Cline binary (max required GLIBC_2.17, layer provides 2.41) reported
UNSUPPORTED with the reason "binary requires GLIBC_2.17 but the installed
layer provides glibc 2.41", which is semantically backwards. This release
fixes the doctor, closes the suite gap that let it escape, and re-cuts the
layer (rev=2) so the fix reaches already-installed devices with zero user
action. The glibc files themselves are byte-identical to the proven rev=1
layer.

- **Doctor v1 root cause (structurally always-false, not a reversed or
  lexical compare)**: the version "comparison" concatenated the required
  version (GLIBC_ prefix stripped) and the installed version, then
  string-compared the concatenation against the installed version alone —
  `"2.17\n2.41" = "2.41"` is never true for ANY input, including exact
  matches. Every binary with a versioned glibc symbol requirement was
  condemned; binaries without one (or judged without binutils) were waved
  through. The suite masked it: the doctor row grepped "SUPPORTED"
  UNANCHORED — `UNSUPPORTED` contains `SUPPORTED` as a substring — so the
  row PASSED while the doctor was wrong (t_cline_shape requires GLIBC_2.34
  and was mis-verdicted on the very device that scored 24/24).
- **Doctor v2 (scripts/runtime/pocketshell-doctor)**:
  `ver_le` — numeric component-wise semantic comparison (2.9 < 2.10 < 2.17;
  2.2.5 three components; zero padding; leading-zero normalization against
  busybox-octal arithmetic); numeric-aware maximum extraction over ALL
  `GLIBC_x.y` tokens (no `sort -V` dependence — busybox sort is not GNU
  sort); the real-loader resolution check is exit-code-authoritative
  (empirically probed: glibc prints "error while loading shared libraries:
  … cannot open shared object file" and exits 127 for a missing library —
  the v1 grep for "not found" NEVER matched it, so unresolvable DT_NEEDED
  was reported as "all shared objects resolved"); installed version parsed
  from the REAL loader first, marker as fallback, drift warning if they
  disagree; trailing sentence period handled ("stable release version 2.41.");
  the full fact hierarchy (ELF/class/machine/interp/runtime/DT_NEEDED/
  required-glibc/version-gate/loader-resolution) prints BEFORE the verdict —
  no successful check can hide a failed one; `--selftest` runs the permanent
  15-case regression matrix (the exact Phase-7 table plus numeric-max traps).
- **Suite v2.1 → v2.2**: all doctor rows anchor on `^Compatibility:
  SUPPORTED`; three permanent doctor rows join the device suite (the
  selftest matrix, musl classification, real-Cline verdict) — 24 rows → 27.
- **Layer rev=2 (propagation contract)**: rebuild-by-patch of the rev=1
  artifact — byte-compare of every extracted file proves EXACTLY ONE tar
  member changed (`usr/local/bin/pocketshell-doctor`); metadata (modes,
  owners, mtimes, symlinks) identical. The marker now ends `rev=2`, so
  `GuestGlibcRuntime.isCurrent` treats every rev=1 device as stale and
  re-extracts on the next session prep. The suite repair hatch writes the
  same rev=2 marker.
- **Tests 392 → 397** (all green): the doctor script's selftest executed on
  the JVM, usage/not-a-file exit codes, real-fixture fact extraction
  (t_cline_shape → `Required GLIBC: GLIBC_2.34` — the numeric max of
  {2.17, 2.34}), static classification, and the marker-rev re-extraction
  regression. New rig evidence: scripts/probe_loader_behavior.sh (loader
  exit codes + message formats) and scripts/test_doctor_integration.sh
  (22 dash assertions incl. both loader-resolution branches).

## [0.10.0-m6.0.2] — 2026-09-06 — M6.0.2: the actual install-path fix (proven root cause)

Device gate #2 failed identically to #1 — this time the failure was traced to
a single proven seam, reproduced, and closed. No layer bytes changed
(2242f8ef… then, 2242f8ef… now); what changed is everything that stood
between the layer and the rootfs.

- **Root cause (proven, not inferred)**: AGP's asset merge DECOMPRESSES
  `*.gz` assets and strips the suffix — reproduced twice from a clean
  `mergeDebugAssets` run on a source tree containing only the pinned
  `.tar.gz`. The shipped APK (vc40 AND vc41) therefore carried
  `assets/guest/pocketshell-glibc-aarch64-2.41-12.deb13u3.tar` (plain,
  17,909,760 B, sha 5be400dd… = gunzip of the artifact) while
  `GlibcRuntimePin.ASSET_PATH` declared `…tar.gz`. Every session spawn threw
  FileNotFoundException at `AssetManager.open` BEFORE touching the rootfs:
  best-effort-swallowed on vc40 (no consumer of
  `GuestSessionPreparation.glibcRuntime`), status-file-only on vc41 (the v1
  suite the device ran cannot read it). The sandbox rig never caught it —
  it extracts the layer with system tar, never through the app's asset path;
  the JVM suite verified the SOURCE-tree asset, not the BUILT APK.
- **GlibcRuntimePin: two explicit pins** — the release artifact
  (`.tar.gz`, 6,761,290 B, 2242f8ef…, for mirror/hatch/provenance) and the
  PACKAGED asset form (`guest/….tar`, 17,909,760 B, `ASSET_SHA256`
  5be400dd…), the latter now passed to `ensureInstalled` and verified at
  every extraction.
- **GuestGlibcRuntime: format sniffing + pre-extraction sha verify** — gzip
  magic (0x1f8b) selects GZIP vs plain tar (immune to which form AGP
  packages); a sha mismatch is a FAILED result, never a half-extraction;
  every outcome additionally logged to logcat (tag `GuestGlibcRuntime`).
- **App identity stamp** (`/etc/pocketshell/app-version`, best-effort on
  every `prepareGuestForSession`): the guest can now PROVE which build owns
  the rootfs — "app too old" vs "install failed" is readable, not guessable.
- **Built-APK regression pin**: a JVM test opens the assembled APK
  (ZipFile) and asserts the packaged asset entry name+size+sha — the exact
  check whose absence let this ship twice. Plus pins for plain-tar
  extraction, sha-mismatch refusal, and asset-open-failure status mirroring.
- **Release checklist**: `scripts/mirror_m6002.sh` extracts the embedded
  asset from the APK and sha-verifies it (three-way mirror + APK asset
  check) — mandatory on every future release.
- **Suite v2.1** (device-gate #2 lessons): self-locating binaries (flat AND
  the served tarball's `bin/` layout — a second packaging defect found by
  this gate), suite version printed in the header, PREFLIGHT extended with
  the app-version stamp + `/etc/pocketshell` listing + loader `ls`/readlink
  evidence; verdict fix-path updated to vc42.
- Full JVM suite: 780 executions, 0 failures (4 net-new pins). Rig
  device-state validation: 3 phases green (20+1 skip / 21+1 skip / 24-24
  ALL GREEN) + custom-dir self-location proven. versionCode 42.

## [0.10.0-m6.0.1] — 2026-09-06 — M6.0.1: install observability + suite diagnosis (device-gate lesson)

The m6.0.0 device gate (§33.1) ran the suite before the glibc layer had
installed; Tier 2/3 fell back to the gcompat stub (rootfs carries gcompat from
the Antigravity era — it persists across app updates) and reported 6 FAILs
that were one root cause. Nothing in the m6.0.0 layer was wrong — the failure
mode was *silence*: a swallowed best-effort `Failed` is indistinguishable from
"app too old" from inside the guest.

- **GuestGlibcRuntime status file** (`/etc/pocketshell/glibc-runtime.status`):
  every ensure outcome mirrored to the guest — `state=OK source=extractor |
  fastpath entries=<n>` or `state=FAILED reason=<one-line>` + `ts`. Diagnostics
  only; marker stays the completeness contract; never throws, never blocks a
  session, never masks the result. 4 new JVM pins (OK/FAILED/fastpath +
  gcompat-stub replacement).
- **Suite v2** (`runtime-tests/run_on_device.sh`): PREFLIGHT diagnosis section
  (marker, status, real loader identity, layer file count, gcompat, disk);
  missing layer → SKIP-with-fix-path instead of FAIL noise (tier 1 musl/static
  still runs — base-guest health is layer-independent); layer presence is a
  capability probe (real loader `--version`), not marker paperwork;
  `cline --help` no longer false-PASSes on an error line; verdict keys off
  layer state, not skip count.
- **Escape hatch**: `POCKETSHELL_INSTALL_LAYER=1` repairs the layer in-guest
  from `POCKETSHELL_LAYER_URL` or a `pocketshell-glibc-*.tar.gz` beside the
  suite (writes the exact app marker + `source=manual-hatch` status — the app's
  fast path recognizes the repair; self-healing still guards drift).
- Validated end-to-end under emulation in four device states: no layer (gcompat
  stub) → musl green + 3 SKIPs + fix path; hatch repair on a
  gcompat-contaminated rootfs → full matrix green incl. Cline 3.0.61; working
  layer without marker → runs (idempotent re-extract covers it); full install →
  24/24 ALL GREEN.

## [0.10.0-m6.0.0] — 2026-09-06 — M6.0: Universal Runtime Compatibility (musl + glibc + static in ONE Alpine guest)

The engineering phase that removes the single hard blocker the two forensic
reports identified: glibc-linked ARM64 binaries failing at the loader stage
(gcompat shims only `libc.so.6`; Cline needs `libpthread.so.0`,
`libdl.so.2`, `libm.so.6` too).

### 1. The architecture (docs/runtime/DUAL_LIBC.md — decision record)
- **REAL glibc (Debian 13 trixie arm64, glibc 2.41) at canonical multiarch
  paths inside the Alpine rootfs** — `/lib/ld-linux-aarch64.so.1` (real
  loader) + `/lib/aarch64-linux-gnu` + `/usr/lib/aarch64-linux-gnu` +
  `/etc/nsswitch.conf`. musl paths are disjoint by construction and never
  touched; apk/musl behavior is bit-identical (loader names, SONAMEs and
  directories do not overlap).
- Transparent by construction: glibc binaries (and ALL their children) exec
  through the real loader with zero env vars, zero proot changes, zero
  per-binary wrappers; both launch profiles benefit automatically.
- Rejected on evidence: gcompat (shim-only — the device failure), sgerrand
  alpine-pkg-glibc (dead/unmaintained), patchelf-per-binary (invasive),
  global LD_LIBRARY_PATH (musl mixing risk), a second distro (forbidden).

### 2. The layer (PocketShell-owned, pinned, no user action)
- `pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz` (6,761,290 B,
  sha256 2242f8ef…) built by `scripts/runtime/build_glibc_sidecar.sh` from
  15 pinned Debian pool packages (INPUTS.sha256 committed); ships in the APK
  (`assets/guest/`) — atomic with the app update, offline, no loose URLs.
- Core: libc6 (loader, libc, libm, post-2.34 pthread/dl/rt/resolv semantics,
  NSS), libstdc++6, libgcc-s1, zlib; common set: ssl/crypto, lzma, bz2,
  expat, ffi, pcre2, yaml, ncurses(w), tinfo, readline. gconv excluded
  (documented, KNOWN_LIMITATIONS).
- Guest tools ride the layer: `pocketshell-exec` (universal ELF routing) and
  `pocketshell-doctor` (arch/class/interp/DT_NEEDED/max-GLIBC version + a
  REAL loader resolution check → SUPPORTED/UNSUPPORTED with the reason).

### 3. The delivery (the GuestApkCompat self-healing pattern)
- `GuestGlibcRuntime.ensureInstalled(rootfsDir)` — idempotent marker fast
  path (one small read), self-healing re-extraction, marker written LAST,
  zip-slip/absolute-path guards, best-effort: musl sessions NEVER depend on
  it. Called from the one existing per-session prep seam
  (`PackageGateway.prepareGuestForSession`) — fresh and existing installs
  converge without any user step.

### 4. The proof (docs/runtime/TESTING.md)
- New permanent suite `runtime-tests/` (sources + cross-compiled binaries):
  `t_hello/t_pthread/t_dlopen/t_libm/t_cpp/t_fork_exec/t_getpwnam/
  t_getaddrinfo/t_static/t_cline_shape` (the last replicates Cline's exact
  DT_NEEDED class: libc.so.6 + libpthread.so.0 + libdl.so.2 + libm.so.6).
- Sandbox rig (proot + qemu-aarch64 + the SAME pinned rootfs + layer):
  **20/20 PASS**; the device runner validated under emulation: **24/24
  ALL GREEN**, including the REAL Cline 3.0.61 binary (151,062,848 B —
  byte-identical to the device report) running `--version`, `--help`, the
  node-spawn wrapper chain and relaunch ×3. The failure the Kilo/M3 report
  captured is CLOSED at the loader level and the full CLI level.
- JVM suite: 768 executions / 0 failures (8 new `GuestGlibcRuntimeTest`
  pins incl. pinned-asset integrity).

### 5. Already sound, deliberately untouched
- Terminal, keyboard, Companion, session model, package layer, procfs
  contract, launch profiles — no behavior change; the layer is additive.
- The rootfs pin stays the pristine upstream Alpine minirootfs.

## [0.9.1-m5.1.0] — 2026-09-06 — M5.1: ARM64 Performance & Architecture Optimization

The audit-first optimization phase: measure the real architecture, change
only what the evidence supports, break nothing. The goal — PocketShell
stays fast and smooth on ARM64 devices with limited RAM as features grow.

### 1. What the audit found already sound (deliberately untouched)
- **Startup is lazy**: Application.onCreate touches only the palette,
  shell dirs and state reconciliation — NO WebView provider, NO Linux
  init, NO package scanning (the m4.0.1 rule held). The terminal
  initializes when Terminal opens; the Companion when raised.
- **Companion renderer recipe is already efficient**: one WebView per
  tab, created once (tab switches never recreate/reload — state, login
  and scroll survive), attach → layout → load only on first creation,
  the WebView's measured height is FROZEN during sheet drags (one
  resize on release — no resize thrashing), present() is idempotent.
- **Background tabs were already platform-paused** on every tab switch
  (onPause suspends JS timers/layout/parsing) and at Activity pause,
  with only the active tab waking on resume.
- **Terminal**: process-scoped sessions, scrollback capped at 2000 rows,
  no polling loops, repaint driven only by emulator callbacks, the
  cursor blinker stops on ON_PAUSE, and the repaint hook is unregistered
  when the terminal screen leaves composition.
- **Linux environment**: one real process per session, spawned only by
  explicit user action; no duplicate daemons; the honest FGS stops
  itself when the last session closes.
- **Keyboard**: static layouts, per-key local state, real KeyEvent
  dispatch, no per-press allocation churn beyond two short-lived
  coroutine jobs.

### 2. What the audit found and FIXED (four surgical changes)
- **F1 · Minimized Companion no longer burns CPU**: collapsing the sheet
  parked background tabs but left the ACTIVE tab running JavaScript,
  timers and layout at full rate while completely invisible. Now
  collapse → pause EVERYTHING (reversible; cookies flushed), raise →
  wake only the active tab. A page that was minimized comes back alive
  exactly when it becomes visible, with no reload and no state loss.
- **F2 · Home no longer spawns a guest shell on every visit**: the
  command-app availability probe is a REAL proot login-shell exec; it
  re-ran each time Home re-entered composition. A 60s freshness window
  and an in-flight guard now gate the visibility-triggered probe;
  package-operation landings still force a fresh probe, and a FAILED
  probe always re-probes (honesty over caching).
- **F3 · Background terminal output no longer repaints the screen**: the
  global screen-update hook fired for every session, repainting the
  (unchanged) visible view at the background session's output rate —
  N sessions multiplied the load. The repaint now happens only when
  the producer IS the visible session; session switches remain
  correct by construction (attachSession nulls the emulator and
  invalidates the view).
- **F4 · Deterministic web-state persistence under memory pressure**:
  onTrimMemory(≥ RUNNING_LOW) flushes cookies while Companion tabs are
  alive, strictly gated on the provider already being loaded (the
  m4.0.1 broken-provider rule is untouched) and fully contained.
- **The tab resource policy, stated honestly**: active tab = full
  rendering and interaction; background tabs = platform-paused (state
  preserved; timers/layout suspended); minimized sheet = everything
  paused; tabs live until the user closes them — the m4.0.11 verdict
  REMOVED saveState/restore and LRU eviction as the render-breaking
  suspect family, so they are deliberately NOT reintroduced. No user
  state is ever destroyed behind their back.

### 3. Not touched
- The frozen Companion renderer/pool/recipe, the ONE keyboard, themes,
  the terminal implementation, the Linux lifecycle contract, providers
  and logins. Full suite green: 752 executions / 0 failures.
- versionCode 39 — in-place update over 16..38; same pinned cert.
- Device measurement gate: docs/TESTING.md §32 (the A–G scenario
  matrix, before/after evidence).

## [0.9.0-m5.0.1] — 2026-09-06 — M5.0 Final UI Correction: the Workspace Bar

A surgical pass ordered by the field report — no redesign, no new
features, the working Companion implementation untouched
architecturally (renderer, sheet mechanics, tab system, pool,
refresh/hard-refresh all preserved).

### 1. Workspace header removed; back lives in the tab bar
- The terminal workspace's large top title row (`← Kilo CLI | …`) is
  GONE — the active session's name already lives in its tab, and the
  row wasted vertical space. The `ChromeHeader` composable (and its
  gradient) is deleted outright.
- The workspace bar IS the top chrome now: `← [Tab] [Tab] [Tab] +`,
  with the back control as a compact integrated glyph (36×34dp strip
  slot, 18dp icon) at the far LEFT — aligned with the tabs, not a
  header-sized button in its own row.
- The strip consumes the status-bar inset itself, so the workspace
  starts directly under the Android status area.

### 2. Compact IDE tabs — both strips
- Strip 40 → 34dp; active tab 34 / inactive 26 (was 40/30 — the
  active tab no longer looks oversized and heavy); gaps 4 → 2dp;
  horizontal tab padding 10 → 8dp; tab width 84–160 → 64–136dp (more
  tabs fit on screen); corner radius 10 → 6dp (shared token); the
  active indicator is a subtle 2dp hairline (was 2.5dp).
- The editor language is untouched: the active tab still opens into
  the canvas color and cuts the hairline, inactive tabs keep the
  quiet right separator, no pill outlines anywhere.
- Long titles truncate with an ellipsis and the close button always
  stays reachable; both strips scroll horizontally and the ACTIVE tab
  is always scrolled back into view when a switch lands off-screen.

### 3. Companion near-full drag surface (the ≥90% rule)
- Below 90% height NOTHING changed: the dedicated drag bar is the
  only sheet drag control; the tab bar behaves normally (taps switch
  tabs, close/+ /refresh work, horizontal scroll scrolls).
- At/above 90% of the available height (`CompanionHeights.
  TAB_BAR_DRAG_THRESHOLD = 0.90`, pure-pinned) the Companion TAB
  STRIP also becomes a vertical drag surface — the tiny handle is
  hard to reach when the sheet is near-fullscreen.
- The gesture is gated behind the vertical touch slop
  (`detectVerticalDragGestures`): a genuine vertical drag moves the
  whole sheet (up = taller, down = shorter, release = stay exactly
  there); tab taps, close, + and refresh are NEVER mistaken for
  drags; the strip NEVER minimizes on touch — tap-to-minimize stays
  the dedicated bar's exclusive duty.
- The strip drag math is the handle's math, shared verbatim
  (`startSheetDrag`/`dragSheetBy`/`endSheetDrag`); no snap points,
  ever. The drag surface also stays attached while any drag is in
  flight, so a tab-bar drag travelling below the threshold is not cut
  mid-gesture.

### 4. Not touched
- The frozen Companion renderer/pool/sheet mechanics, the ONE
  keyboard system, themes, packages, settings, session persistence.
- Full suite green: 752 executions, 0 failures (new pure pins for the
  90% drag-surface gate). versionCode 38 — in-place update over
  16..37; same pinned cert. Device gate: docs/TESTING.md §31.

## [0.9.0-m5.0.0] — 2026-09-05 — UI & Interaction Polish: free-position Companion, decluttered Home, one theme system (Light done fully)

The directive bounded Phase 5 as a **refinement phase — no redesign, no
new features, the working Companion implementation untouched**. Three
themes run through the iteration: remove redundancy, reduce wasted
space, finish theming.

### 1. Companion drag bar — exact required behavior
- The visible bar is TWICE as wide (36→72dp, still 4dp slim) inside the
  same 40dp invisible full-width touch zone; the zone remains the first
  child of the layer's column, so it can never hide behind tabs or
  content (placement guarantee, by construction).
- **Tap-to-minimize**: a single tap anywhere on the bar IMMEDIATELY
  collapses the raised sheet — at 25%, 50%, 80%, near-full, any height.
  No drag-down required. When minimized, the bar is dragged UP to
  restore (no floating button — the persistent bar remains the only
  Companion affordance, unchanged).
- **Free positioning**: the old HALF/FULL snap windows are RETIRED. The
  height changes ONLY by dragging (up = taller, down = shorter) and a
  release settles EXACTLY where the user left it — any fraction, no
  forced 25/50/75/full anchors. The only special release remains the
  collapse threshold (drag near the bar → minimized). Pinned by the
  reworked `CompanionHeights` unit tests.
- What was NOT touched: the renderer, the sheet mechanics, the tab
  system, refresh/hard-refresh, the pool, session persistence.

### 2. Home decluttered
- The floating action button (the rotating + quick-action overlay) is
  REMOVED — code deleted (`QuickActions.kt`), including its 140dp page
  clearance. Session creation lives where sessions live: the Terminal's
  own "+" (now integrated into the tab bar) and its empty state. No
  floating replacement.
- The duplicate "CLI Apps ▾" dropdown is RETIRED: it listed exactly the
  apps the "Your tools" grid below already launches through the same
  pipeline. ONE clear path remains — the tools grid (+ Packages to
  install more). No functionality lost.

### 3. Compact workspace chrome
- Tabs: terminal strip 44→40dp, Companion strip tabs compacted, tab
  minimum width 96→84dp, horizontal padding 12→10dp, gaps 6→4dp. Active
  labels ride the new pinned `onCanvas` token (the active tab is
  canvas-dark in every theme — readable in Light too). Horizontal
  scrolling for overflow was already the LazyRow behavior; unchanged.
- The terminal "+" is no longer a large bordered circle: it is the same
  quiet integrated glyph as its Companion-strip sibling —
  `[ Tab ] [ Tab ] [ Tab ]  +`.
- App-wide padding trim: Home section rhythm (24/20/16 → 16/14/12),
  MidnightPage header/cards/buttons/radio rows, Packages search (field
  and action now share one row), Settings grouping. Touch targets kept
  ≥44–48dp everywhere; nothing cramped.

### 4. Keyboard toggle anchored to the corner
- The [⌨] rebirth icon moved from 64dp-inset to the true bottom-right
  corner: 12dp from the right edge, 8dp above the gesture-bar inset,
  same 44×36dp key-box design, same slot on every screen; safe insets
  respected, never clipped, never over system navigation. The ONE
  keyboard system is otherwise untouched (Task 12: no second layout, no
  IME, same dispatch chain).

### 5. Light Theme — full application-wide support (System / Light / Dark / AMOLED)
- The Midnight Sapphire token vocabulary (`TerminalTheme`) became
  snapshot-state: `applyTheme(light)` swaps the whole chrome palette
  between **Midnight** (dark — the historical values, byte-for-byte) and
  **Daylight Sapphire** (light — paper surfaces, deepened sapphire
  accents). ZERO call-site changes: every screen recomposes on switch;
  `HomeTokens` became read-through accessors.
- Sync runs during `PocketShellTheme` composition BEFORE children read
  tokens — a Light selection (persisted or fresh) never flashes dark.
  Switching is immediate (no app restart); persistence via the existing
  DataStore settings (survives process recreation).
- The **content surface is pinned**: the terminal canvas stays Midnight
  in every theme (the TerminalView background and the session color
  scheme — TerminalPalette defaults, OSC authority — are built on it;
  a terminal is a dark professional surface), and websites keep owning
  their appearance — NO theme injection into Companion pages ever. The
  new pinned `onCanvas`/`onCanvasDim` tokens keep text on those dark
  surfaces readable in both themes (active tabs, failure cards, the
  Terminal hero tile and its mark).
- M3 schemes now match the vocabulary (de-purpled light/dark surface
  stack), so Scaffold/menus/dialogs blend in; AMOLED is preserved as it
  was (pure-black M3 surfaces + Midnight chrome). Dynamic color intact.
- Status-bar icons now follow the theme on every screen (dark icons on
  Daylight, light on Midnight/AMOLED).

### 6. Packages & Settings polish
- Packages: compacted search (one-row field + action), tighter list
  rhythm, same honest apk-backed states — every probe/version/action
  contract preserved verbatim.
- Settings: regrouped into Appearance (theme + dynamic color) /
  Terminal (font size) / Companion (websites entry); compact, fully
  theme-aware, nothing invented.

### 7. Tests + docs
- Full suite green: 750 executions / 0 failures (app debug+release and
  terminal modules; the height-math pins reworked for free
  positioning).
- docs: TESTING §30 (Phase 5 gates), ROADMAP Phase 5.0.0,
  CHANGELOG [0.9.0-m5.0.0].
- versionCode 37 — in place over 16..36, same pinned cert.

## [0.8.0-m4.0.12] — 2026-09-05 — Companion Finalization: cleanup, polish, ONE keyboard

The directive bounded this iteration as a **surgical cleanup and polish
pass only**: the working baseline renderer is the source of truth and was
not modified. Five finalization items were executed around it, and the
temporary diagnostic/debug UI was retired completely.

### 1. Diagnostics removed — completely
- The ⓘ chip is gone from the Companion tab strip; the render-baseline
  harness (`BaselineWebViewActivity`, `BaselineMatrix`), its launch path
  and its manifest entry are DELETED. The Companion now shows only the
  real website — no Render-baseline header, no URL/WebView-version/
  user-agent/attached-bounds/layer readouts, no URL/MODE/INSPECT/COPY
  buttons, no test controls, anywhere.
- The frozen render contract (`CompanionRenderContract`) is unchanged in
  substance; the winner is now pinned by VALUE (the sweep's evidence
  lives in docs/RENDER-RESET-M4.0.9.md and the git history). The canvas's
  DIAGNOSTICS_IN_RENDER_PATH list stays empty and pinned.
- What was NOT touched: the WebView/container implementation — the proven
  recipe (`WebView(realActivity)`, JS + DOM storage + two §16 denials,
  plain FrameLayout, attach → first layout → loadUrl) is byte-identical.

### 2. Refresh (tap) and hard refresh (long-press)
- The refresh glyph sits in the tab strip (the ⓘ chip's old slot). Tap:
  plain `reload()` of the ACTIVE tab only — same URL, same tab, other
  tabs and the session untouched.
- Long-press: HARD refresh of the active page — the freshest possible
  reload that is NOT a data reset: one transient
  `cacheMode = LOAD_NO_CACHE` around the single reload, restored to
  `LOAD_DEFAULT` on page finish. Cookies, login sessions and the other
  tabs are never touched; a subtle haptic tick + a "Hard reloading…" toast
  announce it. The behavioral surface is pinned in the contract
  (`REFRESH_SCOPE`, `HARD_RELOAD`) and unit-tested.

### 3. Drag handle: easier to grab, same design
- The visible bar stays 36×4dp; the invisible full-width touch zone grew
  from 28dp to 40dp of vertical drag area. It sits above the tab strip in
  the layer's column (nothing slides under it) and does not overlap the
  web canvas — website scrolling is untouched.

### 4. ONE PocketShell keyboard — everywhere (§6–§15)
- The deck is now composed at the app ROOT (`PocketShellRoot`), not inside
  the terminal screen: the same keyboard with the same layout exists over
  Terminal, Linux, CLI Apps, Home, and the Companion over all of them.
- The system IME is hard-blocked for the app's lifetime
  (`FLAG_ALT_FOCUSABLE_IM` set once in `onCreate` + the IME inset hidden):
  the Android/Samsung keyboard can never appear — previously it leaked
  back whenever the deck was toggled off. No JavaScript typing hacks: the
  deck dispatches real KeyEvents to the focused view, now with a
  universal fallback (`KeyboardInputRouter.pickWithFallback`) so focused
  Compose text fields (Companion settings Name/URL, future app inputs)
  are served by the same deck too.
- Focus follows input: a Companion WebView input gaining focus auto-opens
  the deck (the terminal canvas already did this via its tap client).
  The bottom-right [⌨] rebirth button now appears on EVERY screen (not
  just Terminal). Closing the Companion hands focus back to the terminal
  explicitly — no flicker, no wrong-target input, no double-open.

### 5. Not touched (the freeze held)
- Renderer, sheet, drag mechanics, remembered height, tab system, tab
  state, destination storage, Companion navigation, provider management.
- Full suite green: **750 executions / 0 failures** (the harness's
  BaselineMatrixTest pins retired with the code; the contract test now
  pins the winner by value + the refresh layer; +3 universal-dispatch
  pins). versionCode 36 — in-place update over 16..35; same pinned cert.
- Device gate: docs/TESTING.md §29 — website rendering, refresh (normal +
  hard), drag handle, universal keyboard, and the regression ladder.

## [0.8.0-m4.0.11] — 2026-09-05 — Replace Renderer Only: the winner frozen and shipped

The user's closing directive bounded this iteration precisely: **do not
redesign the Companion, do not touch the sheet architecture — copy the
winning baseline WebView container/view implementation as the actual
content renderer inside the existing sheet, then remove the diagnostics
around it.** This is the delivery build of that directive, finalizing the
m4.1.0 intermediate (vc34, committed but never fully delivered) under its
final name. Nothing new was designed; the winning renderer was frozen,
pinned and shipped.

### The winner, selected and frozen
- Mode-sweep evidence completed: BASELINE rendered all four gate sites
  (user recording, m4.0.9); **+CHROME UA rendered chat.z.ai completely**
  (device screenshot 03:00) and **+MIDNIGHT BG rendered chatgpt.com
  completely** (device screenshot 03:25) — both exonerated as suspects.
  The remaining three variants are moot for the decision.
- The winner is **BASELINE** — the most stable and least invasive mode
  BY CONSTRUCTION (zero deltas from Android defaults). Frozen as
  `BaselineMatrix.WINNER` and specified in the new pure
  `CompanionRenderContract`: the exact settings surface (JS + DOM
  storage + the two §16 denials — nothing else), the
  `create -> attach -> first layout -> loadUrl` sequence, the plain
  `FrameLayout` host, the real-Activity constructor context, and an
  EMPTY diagnostics-in-render-path list.

### The surgical replacement (what shipped)
- Companion sheet, drag handle, remembered height, tab strip, tab
  system (+/close/picker), destination storage, provider management:
  **UNTOUCHED** — by directive.
- The tab content area renders through the copied baseline unit:
  `Activity → (sheet chrome) → ONE stable plain FrameLayout → one
  WebView(realActivity) per tab → attach → first layout → loadUrl`.
- The visible diagnostic wrapper is gone from production: no URL ▸ /
  MODE ▸ / INSPECT / COPY chrome, no status header, no health sheet in
  the render path — only the page. The harness survives as the
  diagnostic Activity behind the tab strip's ⓘ chip.

### Tests
- 758 executions / 0 failures (both modules × debug+release). New:
  `CompanionRenderContractTest` (6 pins — winner identity, flag
  equivalence with the baseline, the exact four-touch settings surface,
  the load sequence, the host structure, the diagnostic-free render
  path) + the winner pin in `BaselineMatrixTest`.

### Build
- versionCode 35 (`0.8.0-m4.0.11`), pinned debug cert (installs in
  place). Device gate: docs/TESTING.md §28 — Gates A–H on the real
  Companion; full sweep table and copy map in
  docs/RENDER-RESET-M4.0.9.md §8.

## [0.8.0-m4.1.0] — 2026-09-05 — Companion Native Rebuild: the Companion is rebuilt around the proven baseline (decision B, executed)

The m4.0.9 control experiment settled the blank-canvas case on the
physical device (screen recording, full verdict in
docs/RENDER-RESET-M4.0.9.md §7): the minimal baseline — `WebView(activity)`
in a plain FrameLayout, JS + DOM storage only, Android defaults, URL
loaded after first layout — rendered **example.com, wikipedia.org,
chatgpt.com AND chat.z.ai with their complete real UIs**, in this same
app/process/theme/WebView package, seconds after the real Companion
blanked on the same two sites. Android WebView, the device and the sites
are exonerated; the old Companion hosting stack was the failure. Per the
pre-agreed decision rule, the architecture is REBUILT around what
physically works — complexity is not preserved merely because it was
written.

### The new architecture (CompanionWebHost)
- **One stable native container**: a plain `FrameLayout` owned by the
  process-scoped `CompanionWebHost`, handed to Compose's `AndroidView`
  once. Panel collapse, tab switches and retries never re-create or swap
  WebViews — they are plain `addView`/`removeAllViews` view surgery.
- **One WebView per tab**, created with the EXACT proven recipe:
  `WebView(realActivity)`, JavaScript + DOM storage, §16 file/content
  hardening, nothing else — no configuration context, no UA spoof, no
  background override, no layer type, no darkening levers, no viewport
  overrides.
- **The proven load sequence**: attach → first layout → THEN `loadUrl`
  (the sequence the device video verified on all four gate sites).
- Kept product contract, none of it render-path: Name+URL definitions,
  multiple tabs, persistence, cookies (incl. third-party + flush on
  pause), upload bridge, DownloadManager, drag handle + remembered
  height, back navigation, §15 navigation allowlist, §16 permission
  denial, the m4.0.1 guarded-creation degradation, the renderer-death
  guard, the Phase 3.1 shared-deck focus bridge.

### Deleted, permanently (the suspect + witness family)
- `CompanionWebPool` (pooled acquire, LRU eviction, saveState/restore,
  forced-light config context, Chrome UA, flash-guard background, compat
  software layer, wide-viewport overrides, pre-attach loads).
- `RenderProbe` (software + glass pixel probes), `BootWitness`
  (+ `ConsoleTail`), `CompanionHealth` — the watchdogs that watched a
  render path that was never the problem.
- The keyed-swap Compose host, the attach kick, the silent-retry ladders
  (compat-render alternation, boot-retry), the page-health sheet, and the
  `RENDER_STALLED` / `APP_NOT_BOOTED` failure kinds (pinned retired).
- Failure surfaces reduced to the two honest states that remain real:
  main-frame load errors and renderer death.

### Diagnostics kept
- The render-baseline harness stays (Companion → ⓘ), now the standing
  render diagnostic; `WebCompat.chromeLikeUserAgent` survives solely for
  its `+CHROME UA` harness variant.

### Tests
- 744 executions / 0 failures (both modules × debug+release). Retired
  machinery pins removed; one new pin locks the watchdog failure kinds'
  retirement. No WebView fakes (project rule) — device behavior stays
  gated by TESTING.md §27.

### Version
- versionCode 34 (in-place update chain intact, same pinned debug key
  d96a6f66…8bf659).

## [0.7.0-m4.0.9] — 2026-09-05 — Companion Rendering Reset: the minimal baseline WebView experiment (no Companion changes, zero symptom patches)

Scope: the user's hard reset, honored exactly. Eight iterations of
evidence-backed fixes never proved WHICH architectural layer fails to
present a fully loaded page's UI. The m4.0.8 reports sharpened the case:
**ChatGPT paints a blank WHITE canvas** — the page's own light background
PRESENTS (the forced-light scheme lever demonstrably worked) — **while the
UI does not**; **Z.ai paints a blank dark canvas**. Page pixels present,
page UI absent: the dark-rendering theory family is dead permanently, and
the open question is which layer between a working WebView and the user's
eyes breaks the presentation of content.

### What this build is (and is not)
- The Companion render path is FROZEN — no reload tricks, no new
  watchdogs, no renderer toggles, no more settings churn. Zero behavior
  changes.
- It ships the mandated control experiment INSIDE PocketShell:
  **BaselineWebViewActivity** — plain `Activity → LinearLayout →
  FrameLayout → ONE WebView(activity)`, JavaScript + DOM storage on,
  everything else Android defaults, URL loaded AFTER first layout
  (create → attach → layout → load). No pool, no Compose, no
  configuration context, no custom UA, no pixel watchdog, no attach
  kick, no boot witness, no evaluateJavascript in the render path, no
  compat retry.
- Entry: Companion ⓘ Page health → **"Render baseline (diagnostic)"** —
  the ONLY Companion-side change (a launch button).

### The experiment
- **One variable at a time** (pinned invariant): BASELINE (zero flags),
  `+CHROME UA`, `+FORCED LIGHT CTX`, `+MIDNIGHT BG`, `+LOAD BEFORE
  ATTACH`, `+WIDE VIEWPORT` — each turns on EXACTLY ONE Companion
  suspect, so a device result names the breaking layer with no confounds.
- **URL matrix** (gates A–D): example.com → wikipedia.org → chatgpt.com
  → chat.z.ai.
- **Real evidence per run**: the status line always shows the exact
  config plus VIEW truth (attached, measured size, global visible rect,
  layer type); **INSPECT** (opt-in, read-only, on demand) adds the
  page's own viewport — `innerWidth/innerHeight`, `devicePixelRatio`,
  `visualViewport`, title — the direct test of the bogus-viewport theory
  the white-canvas evidence makes plausible; **COPY** hands the whole
  status over for the chat.
- The harness is non-exported, never launched at startup (§22 holds:
  its WebView is created in its own onCreate), and lives in the app's
  process/theme/identity — PocketShell itself, not a separate test app.

### The deliverable report
`docs/RENDER-RESET-M4.0.9.md`: the facts table (what eight iterations
proved), the exact working architecture (baseline) vs the exact current
Companion architecture, the full 15-layer A/B comparison with device
rows PENDING, the suspect→variant map, the device gate protocol, and the
evidence-based decision rule (single-variable removal / native ViewGroup
host rebuild / Chrome Custom Tabs control / GeckoView research).

### Tests & build
- +8 unit pins (baseline purity, single-variable invariant, cycling
  wrap-around, gate URL order, status composition incl. honest
  missing-view state, layer naming, read-only probe guarantee). Full
  suite: **788 executions / 0 failures**.
- versionCode 33 / 0.7.0-m4.0.9; pinned cert `d96a6f66…8bf659`; APK
  sha256 `2c83af33…0840e2c9`.

## [0.7.0-m4.0.8] — 2026-09-05 — The painted-but-black decode: the light package returns, on top of the fixed host (still the one job)

Scope: still exactly one job. The user shipped the m4.0.7 health report
back: **both tabs answered `pixels: painted` — a GPU tab AND a software-
layer tab — while the screen stayed black** (`chat.z.ai`: readyState=
complete · 242 elements · 24 interactive · 139 text chars; `chatgpt.com`:
896 elements · 88 interactive · 394 text chars; boot errors none).

### The decode
- A software-layer (`LAYER_TYPE_SOFTWARE`) view cannot paint pixels that
  fail to reach the screen — the rest of the app renders through the same
  window. With m4.0.7's keyed host the attached view IS the live view.
  Therefore the black **is the page's own painted output**: the site's
  near-black body.
- The probe could not say so because its verdict is
  `any pixel ≠ flash-guard #080F1D` — a `#000000`/`#212121`/`#0d0d0d`
  canvas passes trivially. "Painted" vouched for a black canvas.
- Why the page is dark: m4.0.7's creation rollback re-armed BOTH dark
  sources. (a) `targetSdk 28` is a deliberate proot/W^X constraint, and a
  legacy-target app on Android 15 gets WebView **algorithmic darkening ON
  by default**; (b) `prefers-color-scheme` answers **dark** because the
  app is Midnight everywhere, so sites serve dark themes onto a dark
  body. The m4.0.5/m4.0.6 light levers had looked guilty only because the
  never-attaching host (fixed in m4.0.7) made every recipe paint nothing —
  the rollback over-corrected.

### Fixes (m4.0.7's attachment fixes all kept: keyed host, attach kick, glass-first probe, compat swap)
- **Forced-light scheme**: the WebView's configuration is pinned to
  `UI_MODE_NIGHT_NO` via the activity's `createConfigurationContext` —
  the documented `prefers-color-scheme` lever. Sites always serve their
  LIGHT themes: a white body with dark text the user can SEE even when a
  page's app shell is thin.
- **Darkening OFF at every API level**: framework
  `setAlgorithmicDarkeningAllowed(false)` on API 33+
  (the device is Android 15), deprecated `setForceDark(FORCE_DARK_OFF)`
  on 29–32; the theme already carries `android:forceDarkAllowed=false`.
  (The renderer-priority lever was dropped: android-36's stubs removed
  `setRendererPriorityPolicy` from `WebSettings` — it moved to
  androidx.webkit, which this project deliberately does not carry.)
- **The Activity lookup the config context breaks, fixed at the source**:
  `RenderProbe.findActivity` unwraps any `ContextWrapper` chain (a
  configuration context is NOT an Activity — a hidden m4.0.6 glass-probe
  regression), and the pool remembers the host Activity (WeakReference)
  from every `acquire` and hands it to the glass probe.
- **The probe cannot be fooled again** — the health report gains three
  decisive lines: `scheme: forced light`; `glass: dominant #0D0D0D · 97%
  near-black · 3 colors` (new pure `RenderProbe.colorTruth`: 16-levels-
  per-channel bucket mean, near-black share, distinct colors); and the
  **page's own voice** — `page says: "<title> — <first 100 visible
  chars>"` via the DOM truth probe (`BootWitness.Truth` gains `title` +
  `textSample`). One pasted report now names the page state (login wall,
  consent, empty shell) with zero guessing.

### Tests
- +4 pins: glass color truth (flash-guard/black/mixed/empty), context
  unwrapping, forced-light uiMode arithmetic (the pin itself was fixed —
  `mask.inv()` strips NIGHT_NO), page-voice parsing incl. the legacy
  shape. Full suite: **772 executions / 0 failures** (app + terminal,
  debug + release; one unrelated concurrency flake in
  PackageOperationManagerTest passed on re-run).

### Build & delivery
- versionCode 32 / 0.7.0-m4.0.8; aapt2 badging verified; apksigner
  pinned cert `d96a6f66…8bf659`; APK sha256 `6626d8dd…3f22c`.
- Payload cut at fix tip `25b826d`: zip `7d9b738a…`, tgz `8e9bd2b5…`,
  bundle `91a01edc…`, apk `6626d8dd…`; stale m4.0.7 artifacts withdrawn
  from all three mirrors; `CompanionHealth.kt`/`RenderProbe.kt`/
  `BootWitness.kt` remain on the must-carry list.

## [0.7.0-m4.0.7] — 2026-09-05 — The health sheet cracked it: the compat renderer never reached the screen, and the creation recipe was the regression (still the one job)

Scope: still exactly one job. The user shipped the m4.0.6 health sheet's
"Copy report" testimony back — five screenshots (`Screenshot_20260905_
0026*–0028*`) that finally separate the suspects. The verdict inside
them: **the page is fully alive** (chat.com: `readyState=complete · 761
elements · 62 interactive · 394 text chars`, boot errors NONE, console
empty; chat.z.ai: 240 elements, the site's own JS logging normally) —
and yet `pixels: never painted` on GPU, `pixels: unknown` forever on the
compatibility renderer, ending in the "Page never rendered" card. A
hydrated app with zero presented frames is a PRESENTATION failure — and
two long-hidden code bugs fell out of that fact.

### Root cause 1 — the compatibility renderer was NEVER on screen (AndroidView factory bug)
- `AndroidView(factory = { webView })` runs its factory exactly ONCE per
  composed node. Every silent swap path — the first-stall compat swap
  (`forgetTab` + re-seed, no card), the boot-retry reload, and plain TAB
  SWITCHING (`defId` change → `remember` acquires a different view) —
  swapped the WebView INSTANCE while the host stayed composed. The new
  view never attached.
- On the device this was the whole show: after the first stall the OLD
  (destroyed) view stayed attached — the dead black canvas — while the
  fresh SOFTWARE view sat stranded in the pool loading a perfect DOM it
  never displayed; its probes idled to "unknown" and the honest card's
  claim "already retried it on the compatibility renderer" was FALSE.
- Fix: the host is now `key(webView) { AndroidView(...) }` — any instance
  change re-creates the node, the factory re-runs, and the CURRENT view
  is THE attached view. This also fixes latent wrong-page display on tab
  switching and lets software mode get its first REAL device test.

### Root cause 2 — the m4.0.5/m4.0.6 creation recipe was the painting regression
- Since m4.0.5 the WebView was created from a forced-light
  `createConfigurationContext` (plus `setAlgorithmicDarkeningAllowed(false)`)
  — chasing the dark-CSS theory the DOM evidence now REFUTES (a fully
  hydrated page was present all along; nothing presented it).
- The regression line is exact: m4.0.4 (plain ACTIVITY context, no
  darkening calls) still painted a partial frame (the cookie banner);
  m4.0.5/m4.0.6 (config context + levers) painted NOTHING. A
  configuration context is also not an Activity — it silently broke the
  glass probe's `(context as? Activity)` lookup on top.
- Fix: creation rolled back to the m4.0.4 recipe — `WebView(activityContext)`,
  no darkening levers. Kept: the Chrome-like UA (orthogonal to painting;
  Google login's `disallowed_useragent` fix) and the Midnight flash-guard
  background.

### Fix 3 — the pixel probe now judges the GLASS first
- The old verdict order (software readback first) is unsound on modern
  composited Chromium: `view.draw(softwareCanvas)` may legitimately
  answer with only the background color EVEN WHEN THE SCREEN SHOWS
  CONTENT. The probe now asks [PixelCopy] — the presented window, cropped
  to the view's keyboard-free top half (`glassRegionRows`, 50%) — FIRST;
  the software readback is only the fallback when the glass is unreadable.
- A hung PixelCopy (the device's compat-mode "unknown forever") now times
  out after 1.5 s into the fallback instead of leaving the chain
  verdict-less; a THROWING fallback resolves "painted" — a broken probe
  may never manufacture a stall.

### Fix 4 — the attach kick (surface rebind)
- The pool loads every URL before the view is attached; a load begun with
  no live surface can leave the compositor's frame sink unbound on some
  Chromium builds (DOM/JS/input alive, pixels never present — the exact
  device signature). Once per view, if the render watchdog is STILL armed
  3.5 s after first layout, one silent `reload()` re-runs the load on a
  live, attached, laid-out surface.

### Tests & delivery
- +1 pin (glass region arithmetic); full suite green: 764 executions,
  0 failures. Existing m4.0.4/m4.0.5/m4.0.6 pins untouched.
- versionCode 31 / 0.7.0-m4.0.7 — in-place update over 16..30; same
  pinned cert. Device gate: docs/TESTING.md §24.

## [0.7.0-m4.0.6] — 2026-09-05 — The last dark lever off, the witness de-fooled, and the page can now TELL us everything (still the one job)

Scope: still exactly one job. m4.0.5 shipped a root fix (Force Dark off,
Chrome UA) and a testifying witness — yet the device report stayed
"From 4.0.5 apk. Still black screen no page no error" (screenshot
`Screenshot_20260905_000506`: tabs render, canvas pure black, NO failure
card). The absence of the card was itself the diagnosis.

### What the no-card black canvas proved
1. Both m4.0.5 witnesses stood DOWN on the broken page. The pixel probe
   passed because the page painted its own near-black body (anything
   that is not the flash-guard color counts as painted — it cannot know
   which pixels are "content").
2. The DOM witness passed because of a **server-rendered blind spot**:
   chatgpt.com's shell lands in the DOM with hundreds of inert nodes
   BEFORE its JavaScript app hydrates — instantly clearing the
   60-element mount floor even if the script bundle dies on load. SSR
   markup vouched for an app that never started.
3. And one dark-theme lever was STILL armed: Force Dark off stops the
   framework from INVERTING pages, but the WebView still **answers
   `prefers-color-scheme: dark`** (it reads the app's ambient uiMode —
   and PocketShell is Midnight everywhere), so sites kept serving their
   native dark CSS. A dark shell + a dead hydration = a black canvas.

### Fix 1 — the WebView is created in a FORCED-LIGHT configuration
- The creation context is rebuilt with `UI_MODE_NIGHT_NO`
  (`createConfigurationContext`, derived from the ACTIVITY context per
  the m4.0.3 lesson), so the page always sees
  `prefers-color-scheme: light` and renders as authored for daylight.
  ChatGPT now serves its light theme — the black-shell path is gone at
  the source. (Confirmed direction by web research: WebView derives
  `prefers-color-scheme` from the app's uiMode — Android Developers
  "Darken web content in WebView" + Chromium issue 40189461.)

### Fix 2 — the boot witness can no longer be fooled by SSR shells
- A captured boot error is now DECISIVE: an erroring page only counts
  as alive when it also shows real visible text (≥ 200 chars). A
  SyntaxError-dead SSR shell with 800 inert nodes now gets the honest
  "Page won't start" card WITH the error, instead of silently passing.
- The DOM probe also reads the count of INTERACTIVE elements (buttons,
  inputs, `[role=button]`, contenteditable) and the body text length —
  both surface in every diagnosis line.

### Fix 3 — the page can now TELL us everything, always (Page health)
- The tab strip carries a quiet info chip. It opens a Midnight
  "Page health" sheet with a LIVE reading of the active tab's full
  testimony: current URL, WebView version, renderer (GPU/SOFTWARE),
  pixel-probe verdict, readyState, DOM/interactive/text counts,
  captured boot errors, the last console lines, and the exact UA.
- **Copy report** puts all of it on the clipboard — whatever the next
  canvas mystery is, the device's answer lands in the chat verbatim.
  Plus **Refresh**, **Reload**, and **Reload in compatibility mode**
  (software renderer) as standing escapes. Pure report composition is
  unit-pinned; the guess loop is over.

### Tests & delivery
- +8 pins across variants (SSR-defeats-floor verdict, interactive
  parsing incl. legacy answers, health-report composition + hard cap).
  Full suite: 762 executions, 0 failures.
- versionCode 30 / 0.7.0-m4.0.6 — in-place update over 16..29; same
  pinned cert. Device gate: docs/TESTING.md §23.

## [0.7.0-m4.0.5] — 2026-09-05 — The black page, fixed at the root — and the page now testifies (the one job)

Scope: exactly one job, the user's words — "Bro only one job now, please
fix this time… Still black screen and yes banner goes away when
selected". The m4.0.4 pixel probe + the cookie-banner screenshot finally
yielded a complete diagnosis; this build removes the root causes and
makes any future failure self-describing. No data-model or storage
change; logins/tabs/height survive the in-place update.

### The diagnosis the evidence forced (2026-09-05)
1. The banner dismissing on tap proved the whole page pipeline is ALIVE
   on the device: network, HTML, CSS, JS, layout, touch, compositing.
2. The absence of any m4.0.4 failure card proved the MAIN region DID
   paint non-flash-guard pixels — the page painted its own darkened
   body — while the site's app (ChatGPT's React shell: "What can I help
   with?", composer) never mounted.
3. Two app-side classics fit every observation, and both were still
   armed: (a) WebView **Force Dark / algorithmic darkening** — active
   BY DEFAULT for legacy-target apps (`targetSdk = 28`, a documented
   proot constraint) once the device is in dark mode, and a notorious
   mangler of complex SPAs; (b) the WebView default **user-agent** with
   its `; wv` marker — the second-class client that Google login
   answers `disallowed_useragent` and bot-fronted sites serve degraded
   or challenged bundles.
4. And if neither was the culprit, the app had no way to know WHY:
   pixels cannot distinguish "empty shell" from "alive page".

### Fix 1 — Force Dark off, three layers
- Theme: `android:forceDarkAllowed=false` (API 29+ behavior).
- Runtime, tiered by API: `WebSettings.setForceDark(FORCE_DARK_OFF)`
  on API 29–32; `WebSettings.setAlgorithmicDarkeningAllowed(false)` on
  API 33+ (the attribute-based lever; the framework method is called
  directly — no new dependency).
- Companion sites now render exactly as their authors made them: own
  theme, own colors, unmangled.

### Fix 2 — Chrome-identical user agent
- `WebCompat.chromeLikeUserAgent`: the WebView default UA minus
  `; wv)` and `Version/4.0 ` — byte-for-byte the Chrome mobile UA of
  the same device. Pure, idempotent, blank-safe; unit-pinned.

### Fix 3 — the DOM ground-truth witness (mystery canvases end here)
- `BootWitness.BOOT_TRAP_JS` injected at every `onPageStarted` —
  records `window.onerror` and unhandled rejections from the first
  moment of every document.
- `ConsoleTail` keeps each tab's last 8 console lines (length-capped),
  cleared per document, dropped with the tab.
- `BootWitness.DOM_TRUTH_JS` polled every 2.5s for up to 8 probes
  (20s): `readyState` + DOM element count + captured boot errors. A
  mounted app shell is hundreds of elements; a consent banner is
  dozens — floor 60, pure and unit-pinned.
- A tab is now healthy only when BOTH witnesses agree: pixels painted
  AND the app mounted. On budget exhaustion the canvas raises
  APP_NOT_BOOTED carrying the page's OWN testimony
  (`readyState=… · N DOM elements · error: … · console: …`) plus the
  WebView version; the layer first answers with ONE silent fresh
  reload (flaky networks happen), then the honest card with the
  established escapes (Retry / Open in browser / Continue anyway).
- Disarm/cleanup discipline: every eviction/destruction path clears
  both witness channels (`disarmTab`).

### Tests & delivery
- +12 pins/variant (UA compat idempotence, mount floor, probe-answer
  parsing incl. the double-encoded callback form, garbage-answer
  rejection, diagnosis composition + cap, console ring, APP_NOT_BOOTED
  card text). Full suite: **754 executions / 0 failures**.
- versionCode 29 / 0.7.0-m4.0.5 — in-place update over 16..28; same
  pinned cert (`d96a6f66…8bf659`). Device gate: docs/TESTING.md §22.

## [0.7.0-m4.0.4] — 2026-09-05 — The cookie-banner lesson: pixel-truth stall detection + one-spot keyboard toggle (device bug batch)

Scope: two device-reported bugs on the m4.0.3 build, plus the failure
model they rewrote. No data-model or storage change; logins/tabs/height
survive the in-place update. Follow-up to m4.0.3 on the same device.

### Device findings (2026-09-05, screenshots analyzed)
1. The Companion canvas is now BLACK (was white in m4.0.2) — and the new
   screenshot finally caught the culprit mid-crime: the SITE'S OWN
   cookie-consent banner (ChatGPT/OpenAI) had painted at the bottom of
   an otherwise dead canvas. The page pipeline fires faithfully; the
   compositor rasterizes almost nothing; m4.0.3's event-based watchdog
   trusted those events and never fired.
2. The keyboard toggle is inconsistent: in the deck it sits at the far
   LEFT, but toggled off it becomes a ROUND bubble at the bottom-RIGHT.
   The user asked for one spot (between Enter and Space) and one shape
   (the rectangular key box) in both states.
3. (Informational) first open of a Companion showed the site's cookie
   banner — that banner belongs to the website, not PocketShell; the
   correct behavior is a one-time Accept/Reject that persists.

### Fix 1 — the watchdog reads pixels, not promises
- New `RenderProbe` (m4.0.4 rewrite of the m4.0.3 timer): every ~2.5s a
  tab is probed by TWO readbacks — the WebView drawn into a tiny bitmap
  (software truth), then on API 29+ a `PixelCopy` of the window cropped
  to the view (the frame as PRESENTED on the glass). Six probes ≈ the
  same generous 15s budget. Only actual page pixels stand the probe
  down; load events no longer count for anything.
- PARTIAL PAINT IS NOT CONTENT: the whole-canvas "any differing pixel"
  rule would have been defeated by exactly the cookie-banner state.
  The verdict now samples the MAIN region only — everything above the
  bottom 25% of the canvas, where sites dock consent bars and
  snackbars. A banner can never vouch for a dead page. The arithmetic
  is pure and unit-pinned.
- First stall self-heals SILENTLY: the tab re-creates itself on the
  SOFTWARE renderer (the classic fix for GPU paths that rasterize
  nothing). Only a second stall — compatibility mode already tried —
  becomes the honest "Page never rendered" card.

### Fix 2 — the failure card gets escape hatches
- The card now offers three ways out: Retry (each press alternates
  GPU → SOFTWARE rendering, and lifts any dismissal), "Open in browser"
  (the same address in the device's real browser — settles whether the
  site or the device's WebView build is at fault), and "Continue
  anyway" (the raw canvas as-is: the site's own consent banner lives
  there and may work). Dismissal silences the probe for that tab until
  the user Retries — the app never fights the user for the canvas.

### Fix 3 — one toggle, one shape, one place, both states
- The [⌨] key moved from the far left into the deck row slot between
  Space and Enter (Ctrl · Alt · Space · Shift · [⌨] · Enter).
- Toggled off, the deck's rebirth icon is no longer a round bottom-right
  bubble: it is the SAME rectangular key box (44×36dp, same fill,
  border, radius and icon) parked at that same right-hand spot.
- (m4.0.3's tap-anywhere-on-canvas re-expansion remains.)

### The cookie banner, answered
- It is the website's own consent UI, rendered by the site inside the
  WebView — PocketShell neither adds nor can remove it. Choose
  Accept/Reject once: cookies are flushed to storage on every Activity
  pause (m4.0.1 mechanism), so the choice — and logins — persist across
  launches. If it ever reappears every launch on YOUR device, that is a
  cookie-persistence bug to report.

### Tests + delivery
- +2 unit pins per variant (main-region arithmetic incl. degenerate
  samples). Full suite green: 0 failures across app debug/release and
  terminal modules — no WebView fakes (spec §32).
- versionCode 28 / 0.7.0-m4.0.4 — in-place update over 16..27, same
  pinned cert (d96a6f66…8bf659). Device gate: docs/TESTING.md §21.

## [0.7.0-m4.0.3] — 2026-09-05 — Keyboard everywhere + honest render-stall + Companion picker (device bug batch)

Scope: the m4.0.2 device session's bug list, fixed one by one. No
data-model or storage change; logins/tabs/height survive the in-place
update. Follow-up to m4.0.2 on the same device.

### Device findings (2026-09-05, screenshot analyzed)
1. The keyboard deck served ONLY the terminal; the Companion could not be
   typed into at all.
2. The deck rendered UNDER/behind the Companion panel ("between terminal
   and companion") — the keyboard sat on top of something instead of
   pushing everything up.
3. Toggling the keyboard left a stranded accessory bar; no clean way to
   bring the deck back.
4. Arrow keys too small; the "-" key (and every long-press-capable key)
   dispatched NOTHING on a quick tap.
5. The Companion canvas was STILL pure white (no error card fired), and
   the tab strip's "+" did nothing visible.

### Fix 1 — one keyboard for both surfaces (routing)
- New `KeyboardInputRouter`: the terminal canvas AND the active Companion
  WebView register as dispatch targets; deck presses go to whichever
  surface currently holds window focus (last tap wins). Tapping the
  Companion focuses it → the deck types into the page; tapping the
  terminal hands focus back. Pure decision logic unit-pinned.
- One-keyboard policy: while the deck is up, the system IME is
  hard-blocked for the window (FLAG_ALT_FOCUSABLE_IM) — no double
  keyboard. With the deck toggled off, the block lifts so Companion
  inputs can still summon the system IME (and the panel rides above it
  via imePadding). Known honest boundary: Ctrl/Alt state is consumed by
  the terminal pipeline only; synthetic events reach the WebView WITHOUT
  meta state (plain chars/arrows/Enter/Tab/Backspace work in-page).

### Fix 2 — the keyboard is the bottom-most surface (stacking)
- Deck visibility + measured height moved to the ROOT (PocketShellRoot).
  TerminalScreen reports the deck's height (navigation padding included);
  the Companion layer (and its picker) pads itself ABOVE the deck — the
  keyboard never opens on top of anything; nothing is underneath it.
- Toggling the keyboard now unmounts the WHOLE deck; a small Midnight
  keyboard icon floats at the bottom-right corner (above every layer) to
  bring it back; tapping the terminal canvas still re-expands too.
  With the deck gone the terminal canvas still ends above the gesture bar.

### Fix 3 — every key works
- ROOT CAUSE of the dead "-": keys with a long-press layer (the whole
  digit row, "-", tablet -/=/`) took the hold path EXCLUSIVELY — a quick
  tap dispatched nothing. The primary action now commits on release for
  short taps; holds still win the threshold race and commit the Fn layer.
- Arrow keys are 12dp longer horizontally (still in the grouped panel).

### Fix 4 — the white canvas gets an engine AND an explanation
- WebView creation now uses the ACTIVITY context (m4.0–m4.0.2 used the
  application context — a documented source of blank-canvas WebViews on
  OEM builds). The load/restore path is guarded like creation.
- Render-stall watchdog: every fresh load arms a 15s timer; first paint
  evidence (onPageCommitVisible or progress ≥ 15) disarms it. A page that
  paints NOTHING — m4.0.2's unexplained white, no error event, no
  renderer death — now raises "Page never rendered" + the installed
  WebView version + guidance, instead of a silent white box.
- Retry now alternates GPU → SOFTWARE rendering per tab (compatibility
  mode): a broken WebView build whose GPU path never rasterizes gets a
  second, honest chance. The card's hint says so.

### Fix 5 — "+" opens the Companion picker
- The tab strip's "+" now opens a Midnight sheet listing every Companion
  (open tabs marked, active highlighted): tap to open/raise it,
  "+ Add Companion" goes to the management page. Scrim tap or Back
  dismisses (Back wins over the layer's collapse handler).

### Tests + delivery
- +6 unit pins per variant (5 routing-decision, 1 render-stall failure
  model). Full suite: 724 executions, 0 failures (both modules × both
  variants) — no WebView fakes (spec §32).
- versionCode 27 / 0.7.0-m4.0.3 — in-place update over 16..26, same
  pinned cert (d96a6f66…8bf659). Device gate: docs/TESTING.md §20.

## [0.7.0-m4.0.2] — 2026-09-05 — Honest Companion failure surfaces (never a mysteriously white canvas)

Scope: Companion failure reporting only. No feature, storage or contract
change; on healthy devices the Companion behaves exactly as before. Follow-up
to the m4.0.1 startup hotfix, from the same device session.

### Device finding (2026-09-05, continued)
- After m4.0.1 the app starts; pulling the Companion up showed the ChatGPT
  tab strip and a PURE WHITE canvas — no page, no error, no explanation.
  Every PocketShell-owned surface is dark, so the white came from the
  WebView content side. Candidate causes (device-dependent): the page
  cannot load (network/VPN to the site), the installed WebView build is
  too old for a modern site (after Samsung's rollback the factory build
  can be ancient), or the updated build renders blank. None of these were
  visible to the user — a silent white rectangle violates the project's
  "nothing faked, ever" rule.

### Fix — the canvas can no longer be silently blank
- Main-frame load failures (`onReceivedError`, main frame only —
  subresource noise ignored) now render an in-canvas Midnight card:
  "Page didn't load" + the REAL error string (e.g.
  `net::ERR_NAME_NOT_RESOLVED`) + the installed Android System WebView
  version + a hint (network/VPN or WebView update) + Retry.
- A dead page renderer (`onRenderProcessGone` — the classic white-canvas
  signature of broken WebView builds, whose default behavior KILLS THE
  APP) is now handled: only the crashed view is destroyed, PocketShell
  stays alive, and the card reads "Page renderer crashed" + the WebView
  version + update/rollback hint + Retry.
- The "Companion unavailable" notice (m4.0.1) now includes the installed
  WebView version too.
- Retry re-creates the tab's WebView from scratch (failure cleared; a
  successful navigation also clears the failure automatically).
- Known honest boundary: a page that "loads" but renders blank due to an
  ancient WebView's JavaScript failing fires NO error event — the version
  line on the cards plus a static-site test (e.g. example.com as a second
  Companion) are how that case is identified.

### Tests + delivery
- +4 unit pins on the pure failure model (titles, detail+version body
  join, blank-part skipping, hints) — one pin caught a real defect
  (blank-string formatting) before delivery. Full suite: 712 tests,
  0 failures (both modules × both variants).
- versionCode 26 / 0.7.0-m4.0.2 — in-place update over 16..25, same
  pinned cert. Device gate: docs/TESTING.md §19.

## [0.7.0-m4.0.1] — 2026-09-05 — Hotfix: startup crash when the WebView provider is broken

Scope: the Companion web runtime's initialization + failure paths ONLY.
No feature, UI, storage or contract change; the Companion experience is
exactly as shipped in m4.0 whenever the device's WebView is healthy.

### Root cause (device-reported 2026-09-05)
- Symptom: on a Samsung device (microG-based), m4.0 crashed on EVERY
  launch before any UI appeared; Samsung Device Care attributed the crash
  to the freshly updated Android System WebView and offered "Uninstall
  WebView updates?".
- Mechanism: `PocketShellApp.onCreate()` → `CompanionWebPool.init()` →
  `CookieManager.getInstance()`. `getInstance()` synchronously loads the
  entire WebView provider (`WebViewFactory.getProvider()`) inside
  Application startup — before any UI, on every launch. When the device's
  updated WebView package itself crashes during provider initialization
  (fragile on microG devices where Trichrome components can mismatch),
  every PocketShell launch died with it — although the Companion was
  never opened. An optional layer's engine held the whole terminal app
  hostage: a direct violation of the Companion's own contract (a purely
  optional secondary workspace that must never take PocketShell down).

### Fix
- `Application.onCreate` no longer touches `android.webkit` at all:
  `CompanionWebPool.init()` is a context handoff only.
- Cookie configuration (R4/§7) moved into the guarded lazy path
  (`configureCookiesOnce()` at first WebView creation; retried
  automatically if the provider was broken at the first attempt).
- WebView creation is now the single guarded provider-load point: on
  failure `acquire()` returns null and `CompanionWebPool.runtimeFailed`
  flips true — the Companion layer renders an honest Midnight Sapphire
  "Companion unavailable" notice naming Android System WebView and the
  way out, while the terminal, Home, packages, Diagnostics and Settings
  keep working untouched.
- `pauseAll()` skips the cookie flush while no WebView was ever created
  (no incidental provider load) and guards it otherwise;
  `clearWebData()` wraps all provider touches in `runCatching`.
- No code path can crash the process on a broken WebView provider.

### Tests + delivery
- Full suite: 704 tests, 0 failures (both modules × both variants).
  No new unit pins: the guards wrap Android-only provider calls, which
  the spec forbids faking (§32); the fix is pinned by device gate §18.
- versionCode 25 / 0.7.0-m4.0.1 — in-place update over 16..24, same
  pinned cert. App data (logins included) survives the update; the
  Companion works normally once the device has a healthy WebView.

## [0.7.0-m4.0] — 2026-09-05 — Phase 4: Companion (the embedded web workspace)

Scope: a NEW feature layer. Terminal, Home, Packages, Diagnostics and the
guest pipeline are untouched. Contract: `docs/PHASE-4-COMPANION-DESIGN.md`
(committed before implementation).

- Companion is a lightweight embedded web workspace inside PocketShell —
  a persistent layer below every screen, pulled up by a bottom drag
  handle. No floating button, no browser chrome: a Companion is exactly
  **Name + URL**, fully generic (ChatGPT, GitHub, docs sites, local
  dashboards — the user decides; nothing AI-specific anywhere).
- Engine: android.webkit System WebView ONLY — zero new dependencies.
  Chromium renders in its own sandboxed process; cookies and DOM storage
  persist to the app's private web storage (logins survive restarts,
  `CookieManager.flush()` at pause), Safe Browsing on, mixed content
  never, file/content access off, device permissions (camera/mic/geo)
  denied, no UA spoofing.
- Drag: 1:1 with the finger from the 28dp handle zone ONLY; while
  dragging the WebView's measured height is frozen (bottom-aligned) so
  the page never reflows under the finger — one resize on release.
  Gentle anchors (half 0.55 / near-full 0.94) snap only within 6%;
  otherwise the panel stays exactly where released and the height is
  persisted. The handle is reachable at every height — never trapped.
- Tabs: inverted Phase 3.1 editor language (active tab cuts the strip's
  hairline and opens into the web canvas, 2.5dp Sapphire bottom edge).
  Switching never reloads: background tabs stay alive-but-paused in a
  process-scoped pool (active + 4 LRU; eviction saveStates and restores
  on reactivation; `onTrimMemory` drops background pages first).
- Integration: Back = web history → collapse → normal PocketShell
  navigation (never traps); http(s) stays in Companion, mailto/tel/intent
  resolve to the system with an honest Toast when nothing handles them;
  file uploads use the normal Android picker; downloads go to
  app-private storage via DownloadManager (no permission, no crash).
  `.imePadding()` lifts the panel above the keyboard for chat inputs.
- Settings → Companion: add/edit/delete definitions (inline Midnight
  editor), quick-add templates as editable pre-fills, default Companion
  radio rows, Clear web data (quiet destructive).
- 20 unit pins in CompanionTest (validation, tab reducer, back decision,
  height math, JSON round-trips). Full suite: 704 executions, 0 failures.
- versionCode 24 / 0.7.0-m4.0 — in-place update over vc16..vc23, same
  pinned cert; device gate: docs/TESTING.md §17.

## [0.7.0-m3.6] — 2026-09-04 — Phase 3.6: Procfs Contract (the "kilo ENOENT" fix)

Scope: the Linux environment initialization layer only. Terminal UI, session
lifecycle, package-operation specs, and the guest pipeline are unchanged.
Contract: `docs/PROCFS-CONTRACT.md`.

### Root cause (device-reported 2026-09-04)
- An in-guest `apk update && apk upgrade` replaced the checksum-pinned
  patched `libapk.so.3.0.0`; the M2.6 conditional `/proc` gate
  (bind procfs only when the patched library verifies) failed, and every
  newly spawned session silently degraded to the v0.5.0 no-`/proc` shape.
- Bun-compiled CLIs (Kilo Code's embedded runtime) resolve canonical paths
  through `/proc/self/fd` on aarch64 — the kernel has no `realpath` syscall
  there — so `realpath()` of EXISTING directories returned
  `ENOENT: no such file or directory` while coreutils' userspace `realpath`
  worked fine. `cat /proc/version`, `ls /proc/self`, `ps` were equally dead.

### The fix
- **`/proc` is unconditional for interactive sessions.** The `procEnabled`
  parameter is gone from the launcher; the bind is derived from the profile
  (interactive ⇒ always, package operation ⇒ never — still require-guarded).
  There is no code path left that can express a no-`/proc` user session.
- **apk fd-link gate self-heals.** `GuestApkCompat` now scans the guest's
  `libapk.so.3*` libraries for the standalone `"/proc/self/fd"` gate literal
  and the `"/proc/self/fd/%d"` format literal, and applies the same one-byte
  patch (`d`→`X`) to whatever apk-tools build carries them — including
  post-upgrade builds (the literal layout is identical in 3.0.6 and 3.0.8).
  Ambiguous or alien binaries are refused without writes. Byte equivalence
  with the M2.6 asset re-proven on the pinned minirootfs: repairing
  `ef1c9d8d…db4` yields exactly `b8cd95e2…de9` (`PATCHED_LIBAPK_SHA256`).
- **Spawn-time environment audit.** `procContractProblem()` verifies every
  interactive spec carries `--bind=/proc`, `--bind=/dev`, `--bind=/sys`
  before it can be returned — a future regression fails loud with a
  diagnostic instead of silently starting a broken guest.

### Mount audit (documented, unchanged where correct)
- `/dev` (+ `/dev/ptmx`, `/dev/pts` via the host devpts) and `/sys`: real
  binds in both profiles since v0.3 — functional, pinned by tests.
- `/tmp`: intentionally rootfs-internal (mode 1777 guaranteed; `TMPDIR=/tmp`
  in the spec env) — device-proven writable by the same session that
  reported the bug.
- `resolv.conf`, apk cache binds, sysdata overlays: unchanged.

### Tests
- Launcher pins rewritten for the absolute contract (interactive always
  `/proc`; package never; overlays directly after the bind; audit catches
  stripped specs). GuestApkCompat pins rewritten for the byte-scan decision
  table (safe / repairable / ambiguous / alien / junk / sibling libraries /
  read-only probe). Full suite: 664 executions, 0 failures.

### Environment investigations (real-command evidence, docs/ANTIGRAVITY-PLATFORM.md)
- **Antigravity CLI 404 diagnosed**: `linux_arm64_musl` is the installer's
  CORRECT reading of the guest (musl marker file + `uname -m`); the updater
  serves NO musl manifests for ANY architecture (`linux_amd64_musl` also
  404) — upstream gap, not networking, not a detection bug. The glibc
  `linux_arm64` build was downloaded (SHA512 VERIFIED) and executed under
  qemu-aarch64: runs on real glibc (`--version` → 1.1.26), fails on stock
  Alpine gcompat (missing `__read`/`__open`/`__lseek`/`pvalloc`) and dies in
  its embedded runtime even shimmed. Verdict: UNSUPPORTED BY UPSTREAM FOR
  ARM64 MUSL; platform string NOT patched, checksum verification untouched.
- **Environment rehearsal** (real proot 5.4.0 + Alpine 3.24 aarch64 +
  qemu): reproduced the no-`/proc` regression shape (`/proc/version` ENOENT,
  empty `ps`) and verified the fixed shape; CLI battery green in fresh
  sessions: node 24.18.1, npm 11.12.1, Python 3.12.14, git 2.54.0, curl
  8.22.0 (real HTTPS → 200), OpenSSH 10.3p1, htop 3.5.3, apk 3.0.6.
- Added `scripts/diagnose_platform.sh`: guest-runnable /proc + virtual-fs +
  libc-identity smoke gate (exit 0/1), verified PASS=11/FAIL=0 on the fixed
  shape and FAIL=7 on the broken shape in the same rehearsal.

## [0.7.0-m3.5] — 2026-09-04 — Phase 3.5: Tap-to-Launch Fix + Midnight System Pages

- Fixed the "Kilo tile opens a plain shell" bug for EVERY command app: the
  launch command was PTY-written right after session construction, but
  TerminalSession forks lazily on first view render and `write()` drops
  bytes while no process exists. The launch chain now travels through ARGV
  (`sh -l -c "<command>; exec sh -l"`, pure `guestLaunchChain`) —
  deterministic, no PTY timing, exiting the app still returns to the guest
  prompt.
- Midnight design kit (`ui/system/MidnightPage.kt`); Diagnostics, Packages,
  Settings rebuilt on it; light status-bar icons on all five screens.
- 656/0 tests; versionCode 22. Contract: `docs/PHASE-3.5-DESIGN.md`.

## [0.7.0-m3.4] — 2026-09-04 — Phase 3.4: Registry Expansion + System Pages

Scope: one registry data fix + applying the Phase 3.3 design guidelines to
the non-Home screens. No pipeline, terminal, or runtime changes — probing,
verify-before-launch, guest sessions, and the forbidden package list are
untouched. Contract: `docs/PHASE-3.4-DESIGN.md` (committed before
implementation, plan-first).

### Command registry (the "installed Kilo CLI doesn't show up" fix)
- Root cause: discovery = registry ∩ guest PATH. The probe asks the login
  shell `command -v <name>` only for REGISTRY names; Kilo Code CLI (command
  `kilo`, from `npm i -g @kilocode/cli`) had no entry, so it was never probed
  and could never appear. Correct per the honesty contract (no guessing from
  unknown PATH binaries) — the registry is the designed extension point.
- Five terminal AI agents seeded: **Kilo Code** (`kilo`), **Gemini CLI**
  (`gemini`), **Codex** (`codex`), **Aider** (`aider`), **Qwen Code**
  (`qwen`) — appended after the brief's four, so launcher order on installed
  devices never shuffles. Each still surfaces ONLY when the guest's login
  shell finds its command; uninstall makes it disappear.
- Test pins added: kilo identity (id/name/command/monogram), the four peers,
  append-order stability, probe-gating for expansion entries, and
  forbidden-namespace exclusion. Home needs zero changes — the tools grid and
  the CLI Apps ▾ menu render from the registry automatically.

### Packages screen
- The "runtime not installed" state is now inline text on the canvas (the
  Phase 3.3 §9 rule: empty/blocked states are never containers) with a real
  **Open Diagnostics** accent link — the action the copy always pointed at.
  The `InfoCard` container is deleted.
- Title renamed "Explore CLI Apps" → **"Packages"** (one name per object,
  matching the Home affordances). Per-package surfaces stay — real objects
  (an installable package with actions) are explicitly allowed.

### Settings
- Whole-row selection: theme radio rows and the dynamic-color switch row are
  ≥48dp full-row touch targets with the correct accessibility roles; the
  radio dot / switch are state renderers only. Visual language unchanged
  (app-theme Material, per the 3.3 §10 decision).

### Diagnostics
- One hierarchy for every section: full-width divider + header —
  **System / Linux runtime / Package environment** (the first block had no
  header) — then plain label/value fact rows in one uniform rhythm (the
  snapshot block's internal per-row dividers removed).

### Validation
- 648 test executions green (179 app × 2 variants + 145 terminal-emulator
  × 2 variants; 644 baseline + 4 new registry assertions); assembleDebug OK;
  versionCode 21 / 0.7.0-m3.4; cert chain intact (d96a6f66…8bf659) for
  in-place update 16→21. Device gate: TESTING §15.

## [0.7.0-m3.3] — 2026-09-04 — Phase 3.3: Home & System UI Redesign

Scope: **visual architecture + Home interaction cleanup ONLY**. No backend
changes — command-app discovery, login-shell probing, verify-before-launch,
dedicated guest sessions, the forbidden package list, the Phase 3.1
terminal/keyboard and every runtime behavior are byte-identical. Design
contract: `docs/PHASE-3.3-DESIGN.md` (committed before implementation,
plan-first).

### The structural problem, fixed
- Phase 3.2's Home was a stack of rounded rectangles: an empty-state card,
  bordered session rows, a chip inside the Terminal tile, an "Explore
  packages" action twice, fake ghost-tile placeholders, and a FAB whose menu
  duplicated the tools grid with decorative icon circles. Surfaces were used
  for GROUPING; Phase 3.3 uses them only for OBJECTS.
- New surface philosophy (contract §3): content lives directly on the canvas;
  separation = spacing → section labels → hairline dividers → tone steps. A
  surface is drawn ONLY for a real object: the two environments, the CLI Apps
  menu, the floating create control, an actionable banner, a pressed row/tile.

### Home (§2 hierarchy: identity → foundations → tools → sessions → float)
- **Foundations, flatter**: Terminal and Linux are borderless tone-step
  surfaces (canvas / chrome), radius 14dp (was 20dp), no borders, no chip
  boxes. Terminal shows `N running` as plain mono text; Linux shows an honest
  state line (`Alpine · ready` accent when READY, else the true state +
  `Diagnostics`). Truncating copy ("Native PocketShell envir…", "Enter the
  guest s…") replaced with short always-fitting lines.
- **ONE CLI control**: a quiet `CLI Apps ▾` trigger in the header area (only
  when the guest confirmed apps — never a dead button) opening a compact
  Midnight launcher menu (chrome surface, 14dp, zero tonal elevation,
  hairline): monogram plate + name + dim secondary command. Row taps run the
  exact Phase 3.2 verify-then-launch pipeline. Replaces every floating CLI
  affordance; no logos, no dialog.
- **"Your tools"**: command apps as icon + label launcher entries on the
  canvas (52dp borderless monogram plates, no per-app cards; surfaces only on
  press). Column breakpoints unchanged (3/4/6, 720dp cap).
- **Empty state, lightweight** (§9): `No CLI apps yet.` + one sentence + the
  page's only `Explore packages` link — three text lines directly on the
  canvas. No container, no ghost placeholders. When apps DO exist the footer
  carries a single quiet `Packages` link instead — exactly ONE packages
  affordance in every state, by construction.
- **Sessions flat** (§2a): dot + label + mono id rows between hairline
  dividers; a pressed row is the only surface the section ever draws. Green
  still means ONLY a live process.
- **FAB single-purpose** (§7): the floating control now means "create a new
  session" — `New Terminal` / `New Linux session` ONLY, as text-only chips
  (no icon circles, no logo marks). Command apps left the FAB (they live in
  the grid + menu); the `QuickAction` model is now `id/label/enabled/onRun`.
- Removed (contract §12): the giant empty-state card, `GhostTiles`, the
  duplicate "Explore packages", FAB app actions, `RunningChip`, bordered
  session cards, tile borders, 20dp hero radius, dead `PromptHint`, and
  "Command apps" naming on the page (→ "Your tools"; the term stays in code).

### Other pages (§10, review-level)
- Settings / Diagnostics reviewed: already divider-based, full-width, no
  card groupings — no changes needed (rule recorded for future screens).
- Packages screen: per-entry surfaces represent real interactive objects
  (installable packages with actions) — allowed under the card rules.

### Verification
- 644 test executions green (177 app × 2 variants + 145 terminal-emulator ×
  2), 0 failures — Phase 3.1/3.2 baselines intact, `CommandAppsTest`
  untouched and green.
- assembleDebug OK; versionCode 20 / 0.7.0-m3.3; cert
  d96a6f66…8bf659 → in-place update chain 16→17→18→19→20 unbroken.

## [0.7.0-m3.2] — 2026-09-04 — Phase 3.2: Home / OS Launcher + Command Apps

Scope: the **HOME screen ONLY** + the command-launchable app architecture.
The Phase 3.1 terminal redesign (chrome, tabs, keyboard, canvas, palette, PTY
pipeline), the runtime, the Linux environment, the package manager, installed
packages, Hermes' installation and user data are untouched. Design contract:
`docs/PHASE-3.2-DESIGN.md` (committed before implementation, plan-first).

### Concept
- PocketShell Home is the launcher / workspace of a Linux-centric environment
  — not a terminal dashboard: identity on top, the two foundations (Terminal,
  Linux) in the center, command apps below, sessions quiet, one floating
  quick-action control. No permanent bottom navigation bar.

### Packages ≠ Apps (the architecture change)
- **Packages are infrastructure; apps are experiences.** The Home screen NO
  LONGER renders installed packages: the "Installed CLI Apps" section (apk
  probes over the M2.4 catalog — nano/htop/vim/git/python3) is removed from
  Home. git, nano, python, node, npm, gcc, g++, htop, vim and every normal
  shell utility can never become launcher tiles (test-pinned forbidden list).
- New command-launchable app layer (`apps/CommandApps.kt`): extensible
  registry (unique id, display name, launch command, description, monogram;
  seeds: Hermes Agent `hermes`, OpenCode `opencode`, Claude Code `claude`,
  ZCode `zcode`) + pure guest-driven classification — a tile exists ONLY when
  the guest confirms the command.
- Availability probe with LOGIN-shell semantics
  (`AlpinePackageManager.guestCommandPaths`, one batched exec, terminal
  `exit 0`, PackageProbeException on real failure): the question asked is
  exactly "would a fresh guest login shell find this command?" — the same
  environment the user's typing sees, where uv-installed launchers
  (`hermes`, M2.6) are reachable (the spec's static PATH lacks
  `/root/.local/bin`; a non-login `sh -c` probe would answer a false
  absence). New `PackageGateway.commandPaths/commandPath` wrappers; every
  pre-existing package-manager method is untouched.
- Launch flow (`TerminalViewModel.openCommandApp`, verify-then-launch):
  runtime gate → fresh single login-shell probe → NEW dedicated guest session
  (`TerminalSessionManager.createLinuxCommandSession`) whose PTY receives the
  launch command — what the launcher does is exactly what typing would do.
  The existing `createLinuxAppSession` delegates to it (Explore behavior
  byte-identical). Refusals land in the honest non-fatal banner.
- Honesty discipline carried over (v0.4.4 rule): a failed probe renders
  "availability could not be checked right now" and KEEPS the last real app
  list — a dead probe never renders as "no apps"; an in-flight probe renders
  "Checking the Linux environment…", never a fake empty answer.

### The launcher (Midnight Sapphire, same system as Phase 3.1)
- Fixed blue-dark identity in every app theme: page `#0B1424`, Terminal tile
  in the exact canvas color `#080F1D` (it IS the terminal), Linux tile
  `#101B30`, app tiles `#16233F`, chips `#131F38`. No pure black, NO
  gradients on this page — depth from surface steps, hairlines, restrained
  shadows. ONE accent (Sapphire `#7FA3EF`); green `#5FB572` appears only on a
  dot that really means "process running".
- Brand header: drawn PocketShell mark (pocket tile + prompt chevron) +
  mono wordmark + tagline "Your Linux workspace on Android" + two quiet
  icon actions (Diagnostics, Settings). Not an app bar; scrolls with content.
- Environment launchers: asymmetric duo (weights 1.25/1, 168dp) — Terminal
  (drawn prompt mark, "Native PocketShell environment", live "N running"
  chip) and Linux (original twin-peak mountain mark, honest runtime state
  line: "Alpine Linux · ready" / not installed / in progress / failed /
  repair needed / unsupported ABI; READY enters the guest, every other state
  routes to Diagnostics exactly as before). Distro-agnostic by construction.
- Command app grid: launcher-style tiles (64dp monogram square + name), 3
  columns on phones / 4 on ≥600dp / 6 on ≥840dp, content capped at 720dp and
  centered on tablets. Verifying tile shows a real progress state.
- Empty state: drawn ghost tiles + "Your tools will appear here" + honest
  body + "Explore packages" quiet action (real screen; no fake marketplace).
- Sessions: compact continuation area (max 4 rows + "+N more in Terminal"),
  green dot only for live processes, "(exited)" dimmed, mono session id,
  tap returns to the session.
- Floating quick actions: ONE custom 56dp Sapphire control (bottom-right,
  position stable, + rotates to ×), scrim dim (42%, 150ms), labeled chips
  emerge upward with 30ms stagger (160–180ms, no bounce) — real actions only
  (New Terminal = fresh session; New Linux session when READY; each available
  command app, capped at 4). The action list is data: future capabilities
  become new entries; nothing fake is ever rendered. Back/scrim dismiss.
- Edge-to-edge: Home consumes the status-bar inset itself (like the Terminal
  branch); status-bar icon appearance now coordinated per screen (light icons
  on the Midnight home/terminal surfaces, theme-following elsewhere).
- Motion: 80ms tile press scale; nothing looping, nothing bouncy, no blur.

### Tests
- 644 executions green (628 baseline + 8 new `CommandAppsTest` invariants:
  registry completeness/uniqueness, the brief's forbidden package list,
  argv-safe launch commands, registry-order classification from real probe
  answers, absent-binary-never-renders).
- Phase 3.1 keyboard/tab/palette contracts untouched and still green.

Version: versionCode 19, `0.7.0-m3.2` (in-place update chain 16→17→18→19,
cert d96a6f66…8bf659 unchanged). Device gate: docs/TESTING.md §13.


## [0.7.0-m3.1] — 2026-09-03 — Phase 3.1: Terminal Experience Redesign ("Midnight Sapphire")

Scope: the **Terminal screen ONLY** (chrome, session tabs, terminal workspace,
terminal keyboard). Home / Explore / Packages / Settings / Diagnostics /
navigation and the entire M2.6 runtime layer are untouched. Design contract:
`docs/PHASE-3.1-DESIGN.md` (committed before implementation, plan-first).

### Visual identity
- Deep blue-toned dark theme for the terminal page in every app theme — the
  darkest surface is blue-black `#080F1D`, never pure black. Exactly one
  accent (Sapphire `#7FA3EF`) for cursor, active tab, modifier states, Enter.
- Real 16-color ANSI palette override installed at process start
  (`TerminalPalette` → `TerminalColors.COLOR_SCHEME`): blue-tinted, muted but
  legible. Programs that set their own OSC colors still win — the terminal
  stays a real terminal, never a painted mockup.
- Terminal typeface: **JetBrains Mono NL** (no-ligature build, OFL 1.1,
  Regular/Bold/Italic in `res/font`; docs/THIRD_PARTY.md) — character-exact
  output (no `->` merging), distinct 0/O and 1/l/I, applied via the vendored
  `TerminalView#setTypeface`. Cursor stays the upstream block cursor, now
  Sapphire, blinker behavior unchanged; DECSCUSR bar/underline still honored.

### Session tabs (editor-style, not pills)
- Rounded-TOP tabs; inactive tabs recessed (36dp) and quiet with a single
  right hairline separator; the active tab (40dp, filled in the exact canvas
  color, 2.5dp Sapphire top hairline) covers the strip's bottom hairline and
  visually opens into the terminal workspace — the asymmetric-border
  direction from the brief, no fully-outlined rounded boxes.
- `+` is a stable circular key at the strip end; closing still kills the real
  session; `(exited)` marking preserved; live OSC titles preserved.

### Keyboard — custom from scratch (no system IME anywhere)
- Final layout exactly per brief: TOP `Esc Tab ←↑↓→` (arrows grouped in an
  inset panel, auto-repeat kept — held ↑ cycles shell history) · MIDDLE
  PocketShell QWERTY (pages: digits with shifted symbols, letters, terminal
  punctuation row `- / : ; , . $ ' " @`, symbols with INS/DEL/HOME/END/
  PGUP/PGDN — the v0.6.2 symbol coverage is fully preserved, test-pinned) ·
  BOTTOM `[⌨] Ctrl Alt Space Shift Enter` (the exact order; `⌨` icon-only,
  far-left, permanent, no ON/OFF text; Enter is the one accent-filled key).
- The dedicated FN key is **GONE** (brief §18): F1–F10 are the number-row
  keys' long-press actions (hold ≥350ms → bubble → release commits), F11/F12
  on the tablet rows' `-`/`=` long-press — fully reliable because PocketShell
  owns the keyboard; `readFnKey()` honestly returns false (upstream interface
  method retained).
- The `⌨` toggle collapses only the QWERTY body (180ms vertical motion);
  both accessory rows always remain available; the terminal reclaims the
  space. Landscape compresses to 4 rows; tablets get 14/15-column rows of the
  same identity. Dispatch pipeline unchanged (synthetic KeyEvents → vendored
  KeyHandler → PTY); one-shot/lock modifier machine unchanged (Ctrl/Alt/Shift).
- A11y: every key carries role + contentDescription, modifier state has a
  non-color cue (outline + dot + stateDescription), touch targets ≥36dp.

### Housekeeping
- versionCode 18 / versionName `0.7.0-m3.1` (in-place update over 16/17; same
  pinned debug cert `d96a6f66…8bf659`).
- Tests: 628 executions (169 app + 145 terminal-emulator × 2 variants),
  0 failures — keyboard contract tests rewritten for the new layout.
- Docs: PHASE-3.1-DESIGN.md (contract), ARCHITECTURE §4 (deck layout, Fn
  removal), THIRD_PARTY (JetBrains Mono), TESTING §12 (Phase 3.1 device
  gate), ROADMAP Phase 3.1.
- NOT yet device-proven: TESTING §12 is the gate (human with a device).

## [0.6.2-m2.6] — 2026-09-02 — M2.6.12+M2.6.13: selective /proc sysdata overlay + hardlink extraction fix

### Why this release exists (both layers device-reported 2026-09-02, SM-F711B)

**M2.6.13 — `apk add binutils gcc g++` failed to extract exactly their hardlink
entries.** The transcript showed 19 "failed to extract … Permission denied"
errors — byte-for-byte the `hrwxr-xr-x` hardlink entries of the three Alpine
packages (binutils 11, gcc 5, g++ 3; verified by downloading the packages and
listing their tar entry types). Every regular file extracted fine, including
the real driver `usr/bin/aarch64-alpine-linux-musl-gcc`. Root cause: apk's
extractor materializes hardlink entries with `link()`, and the SAME AOSP
neverallow that M2.6 worked around for download commits
(`neverallow all_untrusted_apps file_type:file link`) forbids `link()` to
untrusted apps outright — EACCES, per entry. The fd-link patch fixed
downloads only; extraction hardlinks are a separate surface of the same
neverallow.

**The fix — Termux's own `link2symlink` proot extension (M2.6.13):** our
shipped `libproot.so` is built from termux/proot pin `7266fb3e`, which
contains the extension, and Termux PRoot-Distro enables it BY DEFAULT for
every non-Termux distro (`--link2symlink`). It intercepts `link()`/`linkat()`
at the ptrace layer and emulates the hard link as a symlink chain (with
link-count translation for stat/statx), so the kernel never evaluates the
denied operation. v0.6.2 passes `--link2symlink` in BOTH guest profiles.
Honest differences, documented and visible: links appear as symlinks
(`ls -l /usr/bin/ld`), and each emulated link costs the file's disk space.
Host rehearsal (scripts/rehearse_m262.sh): binutils installs, `ld/ar/readelf
--version` work through the emulated links, and the emulated binary is
byte-identical to the control install's real file.

**M2.6.12 — selective /proc sysdata overlay.** The same 2026-09-02 session
confirmed the kernel also denies this app read access to the STANDARD procfs
files (/proc/version confirmed on-device; proc_stat/proc_uptime/proc_loadavg/
proc_vmstat are the same AOSP neverallow class). v0.6.1 documented those
denials; v0.6.2 repairs them the way Termux PRoot-Distro does
(proot_distro/sysdata.py, `setup_fake_sysdata()` + `fake_sysdata_bindings()`
— architecture studied and ADAPTED, not copied):

- PROBE FIRST, REAL WINS (upstream's core honesty mechanism, kept 1:1): at
  every interactive spawn the app probes each real file with a one-byte
  read; kernel-readable files are NEVER overlaid — real data always wins.
  Only genuinely-denied files get a verified compatibility file bound
  file-over-file ON TOP of the real /proc bind.
- HONEST CONTENT (adapted from upstream's static constants): /proc/version
  is composed from the REAL uname(2) identity with an explicit attribution
  marker ("PocketShell sysdata overlay: kernel identity via uname(2); the
  kernel's own file is denied to apps by Android SELinux") — this
  SUPERSEDES v0.6.1's refusal to synthesize /proc/version, per the project
  owner's direction to adopt the probe-gated overlay model; the marker
  keeps the no-fake rule intact (any reader sees it IS an overlay).
  /proc/uptime field 1 is the REAL elapsedRealtime clock; /proc/stat has
  the REAL core count and REAL btime (epoch now − uptime) with documented
  zero placeholders for the unreadable global jiffies; /proc/loadavg's
  process-count tail is the REAL hidepid-filtered pid set; /proc/vmstat is
  the standard counter-name skeleton with zero values. Scope is exactly the
  five standard files the gate names — upstream's sysctl entries and
  /sys/fs/selinux empty-dir bind are deliberately not shipped.
- WRITE HARDENING (proportionate port of upstream's descriptor discipline):
  entries are validated before use (regular file, not a symlink, exactly
  one link — planted entries are dropped and remade), writes are
  CREATE_NEW+NOFOLLOW, and every overlay is verified by content round-trip
  before it may be bound. Unlike upstream's write-if-missing, files REFRESH
  at every spawn.
- DIAGNOSTICS: new read-only "sysdata overlays" row (probe-only — the
  Diagnostics button never writes), plus per-spawn overlay status.

### Other
- GuestSysDataCompat (new, unit-tested — 20 pins: probe matrix, generator
  formats, planted symlink/hardlink handling, per-entry honest degradation)
  and launcher guard pins (sysdata binds only on /proc-bound interactive
  sessions; refused for PACKAGE_OPERATION and no-/proc sessions).
- Suite: 312 tests/variant (167 app + 145 terminal-emulator), 624
  executions, 0 failures. Rehearsal scripts/rehearse_m262.sh FULL PASS 19/19.
- v0.6.2 installs IN PLACE over v0.6.1/v0.6.0/v0.5.0 (same pinned signing
  key); runtime, packages and cache untouched. Existing broken binutils/gcc/
  g++ states self-heal on the next `apk fix` after updating (the failed
  packages were recorded but incomplete; `apk fix` re-extracts them).

### Why this patch release exists
- The 2026-09-02 device test (SM-F711B) CONFIRMED the M2.6 architecture end to
  end: Diagnostics shows `apk fd-link patch: applied` and the interactive
  session binds a real /proc — `cat /proc/meminfo` returns the host's real
  values and numeric pid dirs appear. The same test surfaced two behaviors the
  docs and Diagnostics wording had not prepared anyone for, and one Gate A
  bullet (`cat /proc/version`) that reads as a failure but is actually
  device policy working as designed.
- What the test showed, and what each means:
  1. `ls /proc` prints a wall of `Permission denied` lines (kmsg, kcore,
     vmcore, kpage*, sched_debug, timer_list, sysrq-trigger, …) before the
     readable tail. REAL and EXPECTED: the guest's /proc IS the Android host
     procfs (the design — no re-export, no simulation), and SELinux genuinely
     denies this app getattr on kernel-internal nodes; busybox `ls` reports
     each denial. `ps`/`top`/`htop` skip unreadable entries silently, so only
     directory listings are noisy.
  2. `cat /proc/version` → `Permission denied` on this Samsung/One UI kernel:
     the OEM policy does not grant untrusted_app read on `proc_version` (the
     legacy grant disappears at targetSdk 28). Informational only —
     `uname -a` shows the kernel banner. Synthesizing /proc/version from
     uname() was considered and REJECTED: fabricated content violates the
     project's no-fake rule.
  3. The readable set is genuinely useful and real: pid dirs (host pids,
     hidepid=2-filtered to this app), meminfo, cpuinfo, cmdline, uptime,
     loadavg, mounts, self, thread-self, sys, tty, fs, bus, irq, driver,
     plus Samsung extras (memsize, memextra, uid_0_procstat).

### Changed
- Diagnostics "Interactive /proc" row (patched-libapk state) now states the
  full truth up front: `interactive sessions bind /proc (real process
  tools). Host procfs: kernel-internal entries show 'Permission denied' —
  Android SELinux policy, expected`. The no-patch fallback wording is
  unchanged.
- docs/TESTING.md §10 Gate A re-anchored: the PASS signals are the readable
  tail (`ls /proc`), `cat /proc/meminfo`, `cat /proc/cpuinfo`; the denial
  wall is documented as expected noise; `cat /proc/version` is INFORMATIONAL
  (OEM-dependent). New subsection "Expected on-device (NOT bugs)" records
  the 2026-09-02 observations verbatim.
- No runtime, launcher, profile, patch, or rootfs changes — the artifacts
  (APK payload beyond the one string) are behaviorally identical to v0.6.0.

## [0.6.0-m2.6] — 2026-09-02 — M2.6: Linux compatibility recovery — real /proc + real apk, no trade

### Why this milestone exists
- M2.5 made the interactive guest apk-capable by removing the /proc bind from
  EVERY guest session. The package manager worked; `ps`, `top` and `htop` had
  no procfs to read. That violates the product's own compatibility list
  (top/htop/ps/tmux/vim/…) and the Golden Rule: PocketShell must not trade
  one Linux feature for another.

### Architecture (docs/M2.6-RESEARCH.md — full evidence chain)
- **Root cause, source-verified:** apk-tools 3.0.x picks its download-commit
  strategy with `is_proc_fd_ok()` = `access("/proc/self/fd", F_OK) == 0`
  (src/io.c, identical in 3.0.6 and 3.0.8 incl. upstream master). With /proc
  visible it commits every download through
  `linkat("/proc/self/fd/N", …, AT_SYMLINK_FOLLOW)`; AOSP
  `app_neverallows.te` (`neverallow all_untrusted_apps file_type:file link`)
  makes the kernel return EACCES and apk cancels the whole download — no
  fallback for that errno. Without /proc it uses the named-tmpfile + renameat
  path (allowed; device-proven since v0.4.2).
- **The M2.6 fix — GuestApkCompat:** a ONE-BYTE, checksum-pinned patch to
  Alpine's OWN `usr/lib/libapk.so.3.0.0` (3.0.6-r0 from the pinned
  minirootfs) turns the gate literal `"/proc/self/fd"` into
  `"/proc/self/fX"`, so `is_proc_fd_ok()` is permanently false and apk always
  commits via renameat. Same binary version, same real downloads/output/exit
  codes, same database. The `"/proc/self/fd/%d"` script-execution literal is
  untouched. Reproducible: `scripts/patch_apk_fdlink.py` (two literals,
  exactly one code reference each — disassembly-verified per arch).
- **GuestExecutionProfile (M2.6.3):** the two launch policies are now
  explicit on the SAME builder/proot/launcher —
  `INTERACTIVE_TERMINAL` (sessions; binds a REAL /proc when the patched
  library is verified, honest no-/proc fallback otherwise) and
  `PACKAGE_OPERATION` (app-side apk execs; minimal mounts, NEVER /proc —
  refuse-guarded in the builder and pinned by tests). One rootfs, one shared
  cache, one database; no duplicated runtime.
- **Expected process semantics (documented, not faked):** with /proc bound the
  guest sees the Android host procfs filtered by the kernel's hidepid=2 app
  isolation — `ps`/`top` show the app's real process tree with host pids;
  system-wide `/proc/stat`/`meminfo` are real. No filtering, no fake table.

### Added
- `GuestApkCompat` — hash-driven, idempotent patch installer/verifier:
  Ready (patched verified) / NotApplicable (user-modified rootfs — never
  touched) / Failed (honest reason). The patched library ships as an app
  asset and is verified against its pinned sha256 BEFORE anything is written;
  install is temp-file + rename with a post-write re-verification.
- `PackageGateway.prepareGuestForSession` now also verifies/installs the
  patch and its result decides the session's /proc bind
  (`TerminalSessionManager`).
- Diagnostics (M2.6.11): "apk fd-link patch" and "Interactive /proc" rows in
  the package-environment report — read-only, never installs from the
  button; explains the exact state and how to fix it.
- Regression tests: profile pins (proc-enabled session shape, no-drift argv
  equivalence, PACKAGE_OPERATION refuse-guard), GuestApkCompat matrix
  (Ready/rewrite/unknown-untouched/missing/corrupt/status-only/unreadable).
  292 tests per variant, 584 executions, 0 failures.

### Compatibility restored (device gate pending — docs/TESTING.md §10)
- `ls /proc`, `cat /proc/version`, `/proc/meminfo` real again in the guest;
  `ps` and `top` work; `apk update/search/add/del` still work BOTH in the
  shell (patched apk with /proc bound) and from the app UI (no-/proc profile).
- If the guest rootfs was modified by an in-guest `apk upgrade`, the patch
  reports NotApplicable, sessions degrade honestly to the v0.5.0 shape, and
  Diagnostics explains the reinstall path — the user's runtime is never
  overwritten blindly.

## [0.5.0-m2.5] — 2026-09-02 — M2.5: an apk-capable guest shell + install-any-searched-package

### Device context (v0.4.4 confirmed working)
- The 2026-09-02 10:04 screenshots (SM-F711B, v0.4.4) confirm the state-sync
  fixes on real hardware: Home "Installed CLI Apps" lists **Nano
  9.2-r0 AND Git 2.54.0-r0** straight from the real apk database; Explore
  shows Nano "Installed · 9.2-r0" with Open/Uninstall. Clipboard paste
  works ("I can copy paste"). M2.4's device gate is PASSED; M2.5's Home
  integration (verified-entries-only) is realized.

### Fixed — the guest shell could not run `apk` (the "small errors when trying to add node")
- **What the 10:03 terminal screenshot showed:** a MANUAL `apk update` and
  `apk add nodejs npm` typed INSIDE the Linux Shell died with
  `updating and opening …/APKINDEX.tar.gz: Permission denied` — the v0.4.2
  SELinux failure shape — while app-side installs (the user's Git install)
  worked fine. With the failed index update only the stale rootfs-internal
  cache remained ("2 unavailable, 0 stale; 31 distinct packages available"),
  so apk answered `nodejs (no such package)`.
- **Root cause:** v0.4.2 dropped the /proc bind from APP-SIDE package
  commands only; INTERACTIVE sessions kept it. With /proc visible, apk-tools
  3.0.x commits downloads via `linkat("/proc/self/fd/N", …)` — the exact
  AOSP neverallow for untrusted apps — so every in-session apk fetch died,
  and the session's apk also read the rootfs-internal cache (no cache binds)
  instead of the app's healthy index.
- **Fix — one session shape, `RuntimeProcessLauncher.buildSessionSpec`:**
  every interactive guest session (Linux Shell AND catalog-app sessions) now
  uses the SAME SELinux-driven shape as package commands — **no /proc** —
  plus **the same shared apk cache binds**, and spawns after a best-effort
  DNS/workspace refresh (`PackageGateway.prepareGuestForSession`). The
  session's manual `apk update && apk add …` now commits via renameat into
  the ONE cache the UI uses — install from the terminal or from the UI, the
  result is the same real database (apk's own lock keeps them serialized).
- **Honest cost, documented:** the guest can no longer see /proc, so process
  tools (`ps`, `top`, htop's process list) have nothing to read inside the
  guest and fail with their own errors. A working package manager in the
  shell outweighs process listing; Android's SELinux forces the choice.

### Fixed — searching "node" buried nodejs behind description hits
- `apk search` matches names AND descriptions and returns everything
  alphabetically — the user's "node" search showed abseil-cpp-dev, ceph18,
  certbot-dns-linode… while `nodejs` was cut off by the 8-hit limit.
- **Ranking:** exact-name → name-prefix ("nodejs", "nodejs-current") →
  name-contains ("dpdk-node", "certbot-dns-li**node**") → description-only
  matches, alphabetical within groups; 12 hits shown with a "…and N more"
  note.

### Added — install any searched package (M2.5 catalog surface)
- Search hits are now **installable**: one tap runs the same honest
  operation pipeline (`apk update` → `apk add` → `apk info -e` verify) by
  the exact package name (`PackageOperationManager.installPackage`). No
  executable promise is made for non-catalog packages (nodejs ships `node`,
  not `nodejs` — the install verifies ONLY what the real database
  confirms); installed hits show "Installed · version — run 'name' from the
  shell". Catalog-known hits keep their descriptions. Per-hit "Working…"
  busy state follows the running operation exactly (v0.4.2 rule).
- Search results join the installed-state probe, so a fresh install flips
  the hit row to Installed without leaving the screen.

### Notes
- 7 new/updated pins: the apk-capable session spec (no /proc + shared cache
  binds + guest argv tail), search ranking over the exact device query
  shape ("node" → nodejs first; the first expectation even caught that
  certbot-dns-linode is a NAME match via "linode"), and the
  search-install flow (SUCCESS without any `command -v` call, executable
  gate kept when an executable IS known). 281 tests per variant (136 app +
  145 terminal-emulator), 562 executions, 0 failures.
- v0.4.4→v0.5.0 installs as an in-place update (same pinned signing key);
  the runtime, its packages and the shared apk cache are untouched. NOTE:
  the first Linux Shell session opened after updating spawns with the new
  apk-capable spec — `ps`/`top` inside the guest will honestly report they
  cannot read /proc; everything else behaves as before, plus working apk.

## [0.4.4-m2.4] — 2026-09-02 — the app finally sees what apk installed (state-sync hotfix) + clipboard paste that pastes

### Device context (v0.4.3 confirmed working)
- The 2026-09-02 09:09–09:10 screenshots (SM-F711B, v0.4.3) are the M2.4
  gate PASSING on real hardware: GNU nano 9.2 running inside the Alpine
  guest, Diagnostics showing `apk-tools 3.0.6-r0`, combined Guest DNS and
  `Repository fetch: OK — OK: 28546 distinct packages available`. The DNS
  and SELinux chains are closed; what remained were two UI-layer bugs the
  user found immediately.

### Fixed — installed packages stayed invisible in the UI (Explore "Not installed", Home "No apps installed yet")
- **Bug 1, deterministic root cause (batch probe exit-code misread):** the
  Explore screen's installed-state probe runs one guest exec:
  `for p in "$@"; do v=$(apk info -e -v "$p" 2>/dev/null) && echo "$p $v"; done`.
  A POSIX for-loop's exit status is the LAST command it ran — and the
  catalog's last package is `python3`, which was not installed, so the last
  iteration ended with `apk info`'s exit 1 and the loop (and the whole
  probe) "failed". The caller treated the exec as failed and returned an
  EMPTY map — discarding the perfectly good stdout that contained
  `nano nano-9.2-r0`. A genuinely installed nano rendered as "Not
  installed" on every entry, deterministically, whenever the answer was
  mixed. The rehearsal missed it because it pinned the single-package
  `getPackageInfo` path, never the batch script.
- **Fix:** the probe script now calls the absolute `"/sbin/apk"` (the
  PATH-free form every other apk invocation already uses — this was the
  only PATH-dependent apk call in the codebase) and ends with `; exit 0` —
  a completed loop is a successful probe no matter how many listed packages
  are absent. The version column is now parsed by the same strict parser as
  the single probe, so both paths report identical versions ("9.2-r0", not
  "nano-9.2-r0").
- **Honesty hardening:** a failed probe can no longer masquerade as
  "nothing installed". The manager now throws `PackageProbeException`
  (timeout / destroyed / non-zero exec) instead of returning a silent empty
  map; Explore keeps the last real answer, shows an "Installed state
  unavailable: …" banner and renders untouched cards as "Installed state
  unknown"; Home does the same for its list. "Not installed" is now
  exclusively a real apk answer.
- **Bug 2, Home's second invented source of truth:** Home's "Installed CLI
  Apps" read an M1-era DataStore registry (`CliAppRegistry`) that NOTHING
  in the M2.4 flow ever wrote — M2.4 installs go through apk, not the
  registry — so Home claimed "No apps installed yet" over a genuinely
  installed nano. Home now renders the catalog subset the real apk database
  confirms (`installedCatalogApps`, probed when Home becomes visible and
  after every package operation reaches a terminal state), with the real
  version on each row. Tapping a row runs the same verify-then-launch flow
  as Explore's Open (`apk info -e` + `command -v`, then a dedicated guest
  session). The orphaned legacy chain (`CliApp`, `CliAppRegistry`,
  `CliAppLauncher`, `TerminalSessionManager.createSessionForApp`,
  `ShellEnvironment.resolveExecutable`) is removed — an unused registry
  that claims installed state is exactly the kind of fake this project
  refuses to keep around.

### Fixed — terminal Paste did nothing
- The vendored Termux selection toolbar's Paste action ends in
  `TerminalSession.onPasteTextFromClipboard()` → the session CLIENT callback
  — and PocketShell's implementation was an empty body with a comment
  claiming "upstream TerminalView performs the actual paste internally"
  (false: nothing did). The menu item was enabled whenever the clipboard
  had content, then silently did nothing — the user's exact report
  ("I can see option for paste but nothing paste when choosed").
- `PocketShellSessionClient.onPasteTextFromClipboard` now reads the real
  clipboard and pastes via `TerminalEmulator.paste` — upstream semantics:
  strips escape/C1 control bytes, converts LF/CRLF to CR, honours bracketed
  paste mode (nano/auto-indent aware). An absent or empty clip pastes
  nothing (honest no-op).

### Notes
- 5 new/updated pins: the v0.4.4 probe script (absolute `/sbin/apk` +
  `exit 0`) parsing the exact device case (nano installed, python3 last and
  absent) with the plain version form; `PackageProbeException` on timeout
  and on exec failure (exit codes carried); not-ready refusal; and the
  `installedCatalogApps` catalog-order mapping. Legacy `CliAppTest`
  removed with the chain it tested. 278 tests per variant (133 app + 145
  terminal-emulator), 556 executions, 0 failures.
- v0.4.3→v0.4.4 installs as an in-place update (same pinned signing key).
  No runtime reinstall: the fixes are all in the Android layer; the first
  Home/Explore visit re-probes the real apk database.

## [0.4.3-m2.4] — 2026-09-02 — guest DNS can no longer be a single point of failure (device-screenshot hotfix)

### Fixed — "DNS: transient error (try again later)" on every fetch (v0.4.2 device screenshots)
- **What the 2026-09-02 08:13 device screenshots showed (SM-F711B,
  v0.4.2):** the SELinux fix WORKED — the old "Permission denied" is gone,
  and only the tapped card shows "Working…" (both v0.4.2 fixes confirmed on
  device). The remaining failure moved to name resolution: Diagnostics
  showed the guest resolv.conf containing `nameserver 172.20.10.1` (the
  hotspot's gateway) plus `nameserver fe80::8c98:6bff:fe13:bf64%wlan0`, and
  both `apk update` attempts died with `DNS: transient error (try again
  later)`.
- **Root cause:** v0.4.1–v0.4.2 wrote a DEVICE-ONLY resolv.conf — a single
  usable resolver (the second entry is LinkProperties scope syntax;
  musl's `inet_pton` rejects the `%wlan0` zone suffix, so that line was
  dead weight). When that one resolver doesn't answer, musl exhausts its
  retry budget and getaddrinfo returns EAI_AGAIN — apk's "DNS: transient
  error". One resolver is a single point of failure; v0.4.0 had already
  proven the public resolvers ARE reachable on this network (its fetch
  downloaded the index fine before dying at the linkat commit).
- **Fix — combined, self-healing guest DNS** (`GuestEnvironment`): the
  resolv.conf now lists usable device resolvers FIRST, then the public
  fallbacks (1.1.1.1, 8.8.8.8), capped at musl's MAXNS=3. musl queries all
  configured nameservers in parallel and takes the first answer, so one
  dead resolver can no longer block a fetch. Files carry a
  `# managed by PocketShell` marker and are refreshed on EVERY package
  operation to the CURRENT network's resolvers (a resolv.conf pointing at
  yesterday's hotspot gateway is a guaranteed failure tomorrow — the old
  never-overwrite rule kept stale resolvers forever). Legacy shapes we
  wrote (v0.4.0 public-only constant, v0.4.0–v0.4.2 bare
  `nameserver <literal>` lists) are upgraded in place on the first package
  operation; anything with comments/options/search lines/hostnames is user
  content and is never touched. Zone-suffixed link-locals are dropped at
  the source (`deviceDnsServers`) and by shape when building the file.
- Diagnostics "Guest DNS" now renders without comment lines and reports
  the source honestly: "device resolvers first, public fallback (musl
  queries all in parallel)".

### Notes
- 9 new/updated DNS pins in `GuestEnvironmentTest` (combined content, MAXNS
  cap, zone-suffix drop, v0.4.1→combined upgrade of the exact on-device
  file, stale-network refresh, user-content protection, managed-shape
  recognition) + the pre-op repair pins in `AlpinePackageManagerTest`.
  281 tests total, 0 failures.
- v0.4.2→v0.4.3 installs as an in-place update (same pinned signing key);
  the DNS repair applies on the first package operation — no reinstall.

## [0.4.2-m2.4] — 2026-09-02 — fix the SELinux hardlink neverallow that killed every apk download (device-screenshot hotfix)

### Fixed — the real reason `apk update` died with "Permission denied" (v0.4.1 device screenshots)
- **Root cause, verified in apk-tools 3.0.6 source + AOSP sepolicy:** the
  2026-09-02 device screenshots (SM-F711B, v0.4.1 with working device-DNS)
  still showed every fetch failing with `updating and opening …
  APKINDEX.tar.gz: Permission denied` — while the status bar showed real
  download traffic (3–10 KB/s). That shape (bytes flow, then EACCES) pointed
  away from DNS entirely and at apk's download COMMIT step: apk-tools 3.0.x
  (the `HAVE_O_TMPFILE` build Alpine ships) downloads every cached object —
  APKINDEX **and** packages — into an anonymous `O_TMPFILE` file and commits
  it with `linkat(AT_FDCWD, "/proc/self/fd/N", atfd, name,
  AT_SYMLINK_FOLLOW)` (`src/io.c`, `__apk_ostream_to_file`/`fdo_close`). AOSP
  system/sepolicy `app_neverallows.te` answers that with
  `neverallow all_untrusted_apps file_type:file link;` — untrusted apps keep
  create/rename/unlink on their own data (`create_file_perms` deliberately
  contains no `link`), so the kernel denies the hardlink with EACCES and apk
  **cancels the whole download** (no retry, no fallback). This is why
  v0.4.1's cache binds changed nothing (the denial is on the link
  *operation*, not the path), why host rehearsals never saw it (no SELinux),
  and why the v0.4.1 DNS fix could not cure it (DNS was never the whole
  story — the v0.4.1 changelog's "instant blocking" reading of the first
  EACCES was wrong).
- **Fix — package commands no longer bind /proc into the guest**
  (`RuntimeProcessLauncher.buildLaunchSpec(bindProc=…)`, package specs pass
  `false`; interactive shell/app sessions keep `/proc` unchanged). Without a
  visible `/proc`, apk's own `is_proc_fd_ok()` (a bare
  `access("/proc/self/fd")`) is false and it downloads through the
  named-tmpfile + `renameat` commit path — plain create/rename/unlink, fully
  allowed for apps. apk needs `/proc` for nothing else in this flow: the
  only other consumer (`find_mountpoint` → `/proc/mounts`) degrades to a
  no-op and only matters for read-only cache remounts. Rehearsed end-to-end
  with the same apk-tools 3.0.6: `update → search → add → run → del` all
  pass without `/proc`, and the committed `APKINDEX.<hash>.tar.gz` files
  land in the bound host cache dir with zero leftover temp files.

### Fixed — catalog cards no longer claim work they are not doing
- v0.4.1 rendered the global single-flight busy flag on every card, so
  installing nano flipped all five featured cards to "Working…" at once
  (user screenshot). A card now shows "Working…" only while the running
  operation targets *that* card's package (`packageOperationTargetsCard`,
  pinned by unit tests); other cards keep their true Install/Open labels and
  stay disabled only because the one-mutation-at-a-time lock is honest.
- The failed-operation banner no longer repeats apk stderr lines verbatim
  when the summary line already contains them.

### Fixed — a cancel racing the operation start can no longer wedge the manager
- Found by the `cancel destroys the process and lands FAILED` pin while
  rebuilding v0.4.2 (it caught a real scheduling race, not a flake): if
  `cancelCurrent()` fired after the operation was requested but BEFORE the
  IO dispatcher first ran the job body, the body never executed — so its
  `finally` never released the single-flight lock or the busy flag, and
  `_current` never reached a terminal state. Every later package op would
  then be refused forever with "another package operation is already
  running" until the app process died. The operation job now starts
  `CoroutineStart.ATOMIC` with `ensureActive()` first: the block ALWAYS
  begins, the cancel lands as an honest `FAILED("cancelled")` (or the real
  result when the process was already started and destroyed), and the
  cleanup `finally` is unavoidable. The same pin passes deterministically
  on both race orderings.

### Notes
- 4 new/updated unit pins: package specs never carry `/proc` while shell
  specs keep it (exact argv pins), and the per-card busy rule. 277 tests
  (132 app + 145 terminal-emulator), 0 failures.
- v0.4.1→v0.4.2 installs as an in-place update (same pinned signing key).

## [0.4.1-m2.4] — 2026-09-01 — fix guest DNS + apk cache for real networks (device-recording hotfix)

### Fixed — why every package operation failed on the device (v0.4.0 recording)
- **Root cause (from the SM-F711B recording, 2026-09-01 13:37):** Explore CLI
  Apps → Install failed twice, in two different ways, for the SAME underlying
  reason: the v0.4.0 DNS repair wrote hardcoded public resolvers
  (`1.1.1.1` / `8.8.8.8`), and on the user's network those are UNREACHABLE
  (port-53 egress blocked / strict Private DNS is common on carrier and
  hotspot networks). With `resolv.conf` pointing at dead servers, musl's
  resolver retried for ~10 s and apk reported `DNS: transient error (try
  again later)`; under different (instant) blocking the same failure surfaced
  as the raw errno `Permission denied` — apk-tools 3 passes socket-layer
  errnos through verbatim (`FETCH_ERRCAT_ERRNO`), which is why the first
  failure never mentioned DNS at all. No package operation ever reached the
  network; the Android side had perfect connectivity the whole time.
- **Fix — guest DNS now uses the DEVICE's own resolvers**
  (`ConnectivityManager` → `LinkProperties.dnsServers`, IPv4 first, top 3,
  re-read per operation). The public pair remains only as a fallback when the
  OS reports nothing usable. A `resolv.conf` that exactly equals the v0.4.0
  fallback is upgraded in place (PocketShell wrote it, PocketShell replaces
  it); user- or Alpine-written content is never touched. Requires one new
  normal permission: `ACCESS_NETWORK_STATE` (read-only, no traffic).
- **Hardened — apk cache can no longer be blocked by rootfs permissions.**
  apk-tools 3 keeps its download cache in `etc/apk/cache` (fallback
  `var/cache/apk`) and failed v0.4.0 installs never got past opening the
  index there. The package spec now binds two app-owned host directories
  over both guest cache paths (`--bind=<cache>/etc:/etc/apk/cache`,
  `--bind=<cache>/var:/var/cache/apk`) — the same proot mechanism as the
  `/dev`, `/proc`, `/sys` binds, so the cache lives OUTSIDE the rootfs and
  rootfs-internal ownership/modes can never block a fetch again. The cache
  dir sits beside the runtime under `noBackupFilesDir`.
- **Pre-op workspace repair:** every package operation now also guarantees
  `etc/apk/cache`, `var/cache/apk` and `tmp` exist inside the rootfs with
  sane modes (best-effort; the binds are the hard guarantee) — runtimes
  installed by v0.2.x–v0.4.0 are repaired in place, no reinstall needed.
- **Honest failure surface:** the Explore FAILED banner now also shows apk's
  real stderr (up to 4 lines) and offers **Retry** for repository updates;
  Diagnostics' "Check package environment" now runs ONE real bounded
  `apk update` probe (explicit button press — nothing automatic) and reports
  the true outcome plus which DNS servers the guest got and where they came
  from ("device resolvers" vs "public fallback").

### Signing certificate changed — ONE-TIME uninstall required (read first)
- The sandbox reset (#5) destroyed `~/.android/debug.keystore`. Android
  debug-APK signatures ARE the update identity: a build signed with a
  regenerated key cannot install over v0.3.x–v0.4.0. The key is
  unrecoverable (it never left the sandbox), so **v0.4.1 must be installed
  after uninstalling the old app** — the runtime (9.3 MB) is reinstalled
  from Diagnostics in one tap; no packages were installed yet (M2.4 never
  succeeded on v0.4.0).
- **This is the last time.** The (new) debug keystore is now committed at
  `keystore/debug.keystore` and pinned via `signingConfigs.debug` — every
  future build signs identically regardless of sandbox resets, and remains
  an in-place update over v0.4.1+. New cert SHA-256:
  `d96a6f664d7f8d7194733672dcd6eb9f33f40a0078ab07ff66bfd605138bf659`
  (was `34391676…cf3f`).

### Tests
- 12 new unit tests (271 total): device-resolver flow into the pre-op repair,
  v0.4.0-fallback upgrade rule, never-overwrite for user content, cache-bind
  argv pins (after the fixed binds, before the guest argv), workspace repair
  creates/fixes dirs, workspace-repair failure refuses the op without exec.
- Rehearsal (scripts/rehearse_m24_packages.sh) extended with the exact cache
  binds + workspace repair steps; x86_64 end-to-end apk flow re-run.

## [0.4.0-m2.4] — 2026-09-01 — real Alpine package management (apk) + CLI app installation foundation

### Added — the first real package-management layer (M2.4)
- **Real `apk`, zero fakery.** Explore CLI Apps is now a working frontend for
  the real Alpine package manager inside the proot guest:
  `Android UI → PackageOperationManager → AlpinePackageManager →
  RuntimeProcessLauncher (the M2.3 launcher, reused — same proot, loader,
  LD_LIBRARY_PATH, argv contract) → proot → real apk → real package
  database`. PocketShell never touches Alpine package files directly; every
  installed/removed claim is confirmed by `apk info -e` exit codes, versions
  by real `apk info -e -v` stdout, executables by POSIX `command -v`.
- **M2.4.4 proven in the sandbox first** (scripts/rehearse_m24_packages.sh,
  apk-tools 3.0.6 on Alpine 3.24.1 x86_64): `apk update` (28,645 packages) →
  `apk search` → `apk add nano` → `apk info -e nano` → `command -v nano` →
  nano 9.2 actually runs → `apk del nano` → `apk info -e` exit 1. Device
  validation (§9) is the actual gate — sandbox ≠ device.
- **Dedicated background exec, not a PTY session** (Option A): package
  commands run through a new `GuestCommandRunner` (ProcessBuilder on the same
  proot spec) with captured stdout/stderr and real exit codes. Nothing is
  ever typed into a user-visible terminal session; active PTY sessions are
  untouched. Non-interactive by design (stdin → /dev/null).
- **Honest operation state machine** (`PackageOperationManager`): IDLE →
  UPDATING_REPOSITORIES → INSTALLING → VERIFYING → SUCCESS/FAILED
  (UNINSTALLING for removal, SEARCHING for searches). Every state maps to
  real in-flight apk work; no percentages, no delays. SUCCESS is emitted only
  after the guest confirms BOTH the package database entry and the
  executable; verification failure after a successful `apk add` is reported
  as FAILED("refusing to claim installation"), never as success.
- **Single-flight**: one mutating operation at a time (atomic guard). A
  second concurrent request fails immediately with a readable reason — never
  queued silently, never overlapping. Operations run in a process-scoped
  scope (Activity recreation cannot kill a real apk transaction); explicit
  Cancel destroys the real guest process. Cancel/timeout handling survived a
  deep sandbox hunt: poll-based wait (a JDK-21 blocking-waitFor quirk), and
  EOF-drain with grace on cancel (an orphaned child inherits the pipe FDs and
  would otherwise hang cancellation for the orphan's lifetime).
- **Guest DNS repair (critical device discovery)**: the Alpine minirootfs
  ships NO /etc/resolv.conf, so every guest name lookup would fail. Fresh
  installs now get one written during configure; existing v0.2.x–v0.3.x
  runtimes are repaired in place before the first package operation
  (never overwriting a file that already has content). Without this the
  user's existing 9.3 MB install could never run `apk update`.
- **Curated catalog (metadata only)**: nano, htop, vim, git, python3 — tiny
  by design. The catalog cannot claim installation status (no such field
  exists; pinned by reflection test). Normal shell commands (sh/ls/cat/df/…)
  are explicitly never launcher cards.
- **UI**: Explore CLI Apps = search (real `apk search`, apk-tools 3 output
  parsed strictly, junk lines skipped) + Featured cards with real
  Install / Open / Uninstall; honest progress card with real apk output tail
  and Cancel. "Open" re-verifies READY + installed + executable, then starts
  a NEW dedicated guest session and types the launch command into THAT
  session only — exiting the app returns to the real guest shell prompt.
- **Diagnostics**: "Package environment" section with an explicit check
  button (apk version banner, configured repositories, world package count,
  DNS state) — nothing runs automatically on screen open.
- RuntimeProcessLauncher.buildLaunchSpec grew a `guestCommand` parameter
  (default unchanged: /bin/sh -l — the M2.3 contract is untouched and
  re-pinned by tests).

### Fixed
- Payload hygiene follow-up: none this cycle (v0.3.2 carried the fix).
- `apk info -e` on a runtime without resolv.conf would have failed DNS for
  ALL networked commands — see GuestEnvironment above (repaired pre-op).

### Changed
- Version 0.4.0-m2.4 (code 8). Same signing cert → direct update, installed
  runtime + installed packages kept.
- 41 new unit tests (259 total, 0 failures): parser pins from real apk-tools
  3 output, command construction, exit-code mapping, DNS repair, single
  flight, verification-gated SUCCESS, cancellation process-destroy, real
  /bin/sh process executor (streams/timeout/destroy/large-output deadlock),
  catalog invariants.

## [0.3.2-m2.3] — 2026-09-01 — device fix: guest dies at dynamic linking (`libtalloc.so not found`)

### Fixed — Linux Shell session died at exec with a linker error (user recording, Samsung SM-F711B / Android 15)
- **What the recording shows:** v0.3.1's crash fix works — install runs to
  READY (9.3 MB), tapping "Linux Shell" no longer kills the app; the session
  opens and honestly prints the guest's death:
  `CANNOT LINK EXECUTABLE "--kill-on-exit": library "libtalloc.so" not found:
  needed by main executable` → `[Process completed (code 1)]`. Two launcher
  defects, both fixed in `RuntimeProcessLauncher.buildLaunchSpec`:
  1. **`LD_LIBRARY_PATH` was never set.** proot's `DT_NEEDED` is
     `libtalloc.so` (verified at build time), which lives in the app's
     `nativeLibraryDir` — but bionic's dynamic linker searches only its
     default system paths plus `LD_LIBRARY_PATH`, never `nativeLibraryDir`.
     The exec'd proot died the instant the linker resolved dependencies. The
     sandbox rehearsal masked exactly this:
     `scripts/rehearse_m23_gate.sh` exports `LD_LIBRARY_PATH` in the shell
     (glibc happily used it), while the device has no shell to do that. Fix:
     the spec environment now carries
     `LD_LIBRARY_PATH=<nativeLibraryDir>` itself.
  2. **argv was misaligned: no argv[0].** The JNI layer execs
     `execvp(cmd, argv)` with the args array verbatim, and v0.3.1's array
     started at `"--kill-on-exit"`. Consequences visible in the recording:
     bionic quoted argv[0] as the executable name (`CANNOT LINK EXECUTABLE
     "--kill-on-exit"`), and proot's getopt — which starts at argv[1] —
     silently swallowed the flag. Fix: argv[0] is now the proot path (standard
     exec convention).
- **Preflight grew a tooth:** `preconditionProblem()` now also verifies
  `libtalloc.so` is present next to proot/loader, so this failure class is
  reported as an honest actionable message instead of spawning a process that
  can only die.
- Version 0.3.2-m2.3 (code 7). Same signing cert → direct update over
  v0.3.1, runtime data kept.
- 3 new unit tests (218 total): `LD_LIBRARY_PATH` pinned to equal
  nativeLibraryDir; argv[0] pinned to the executable with `--kill-on-exit` at
  argv[1]; missing-`libtalloc.so` preflight message pin. The argv-contract
  test now pins argv[0] too.

## [0.3.1-m2.3] — 2026-09-01 — device crash fix: Linux Shell tap killed the app

### Fixed — device crash (user recording, Samsung SM-F711B / Android 15)
- **Tapping "Linux Shell" exited the app instantly to the launcher.** Two
  independent root causes, both fixed:
  1. **Native libraries were never on the filesystem.** AGP 8 defaults to
     `extractNativeLibs=false`: `.so` files ship only inside the APK,
     `System.loadLibrary` still works (PT ran, install ran, 9.3 MB runtime
     installed fine — everything the recording shows), but
     `applicationInfo.nativeLibraryDir` is EMPTY, so the path-based execve()
     proot needs is impossible. `buildLaunchSpec`'s bare `require(proot.isFile)`
     then threw from the Compose click handler → unhandled main-thread
     exception → process death. Fix: `packaging { jniLibs {
     useLegacyPackaging = true } }` → `extractNativeLibs=true` in the merged
     manifest (verified with aapt2).
  2. **targetSdk 36 could never run the guest anyway (W^X).** AOSP policy
     (`app_neverallows.te`) neverallows `execute_no_trans` on
     `app_data_file` for every untrusted-app domain except the legacy ones,
     and `seapp_contexts` maps targetSdk 28 → `untrusted_app_27` (29+ →
     blocked domains). proot's whole job is execve()ing the guest shell
     inside app data, so no amount of loader plumbing fixes targetSdk ≥ 29 —
     this is precisely why Termux targets 28. **targetSdk 36 → 28** (the
     pre-documented Plan B, promoted by evidence; side-load distribution is
     unaffected, Android 14+ installs targetSdk ≥ 23).
- **Launch path is now crash-proof by construction** (defense in depth — a
  refused launch can never again kill the process regardless of cause):
  - `RuntimeProcessLauncher.preconditionProblem()` — pure preflight returning
    an honest, actionable reason (missing rootfs → "install or repair from
    Diagnostics"; missing proot/loader → names the directory). `buildLaunchSpec`
    now throws with exactly that message (consistency pinned by tests).
  - `TerminalViewModel` routes every spawn (Terminal / Linux Shell / CLI app /
    new session) through a single `safeSpawn` no-crash boundary; failures set
    a `launchError` StateFlow instead of propagating.
  - Home renders a dismissible error banner with a Diagnostics shortcut;
    navigation to the terminal happens only on a real spawn.
  - `TerminalSessionManager.spawn()` wraps PTY construction in try/finally so
    `_creating` can never stick true.
- If the guest process itself dies on-device (e.g. a vendor policy surprise),
  the terminal shows the exec error and `[process exited]` — visible, honest,
  and the app stays alive.

### Changed
- Version 0.3.1-m2.3 (code 6). Same debug signing cert as v0.2.x/v0.3.0
  (SHA-256 34391676…cf3f) → installs as a direct update, runtime data kept.
- 4 new unit tests (215 total, 0 failures): preflight messaging pins for the
  exact v0.3.0 crash conditions.

## [0.3.0-m2.3] — 2026-09-01 — Linux shell: proot guest behind the existing PTY

### Added
- **Linux Shell (Home card) enters the installed Alpine guest** when the
  runtime is READY: `RuntimeProcessLauncher` builds the proot launch line,
  `TerminalSessionManager.createLinuxSession()` spawns it on the SAME PTY and
  session machinery as the system shell — no second terminal implementation.
  In every other runtime state the Home card shows the truth (not installed /
  installing / failed / repair / unsupported ABI) and routes to Diagnostics.
- proot stack compiled from pinned source and bundled via jniLibs for all
  four ABIs: `libproot.so` + `libproot-loader.so` (termux/proot pinned tag
  **v5.1.107.92** @ 7266fb3e8516535682f5a9c8f3a7e70f6506eddb, GPL-2.0) and
  `libtalloc.so` 2.4.2 (LGPL-3.0+, dynamic, SONAME normalized). See
  scripts/build_proot_m23.sh + docs/THIRD_PARTY.md.
- Loader strategy (the M2 risk item): runtime env `PROOT_LOADER` points at
  `nativeLibraryDir/libproot-loader.so` — the one location that stays
  executable at targetSdk ≥ 29 — while the loader embedded in libproot.so
  remains a fallback. No execve() on app-data files, ever.
- argv contract: `--kill-on-exit --rootfs=<rootfs> --root-id --cwd=/root
  --bind=/dev --bind=/proc --bind=/sys /bin/sh -l`. Long options use the
  joined `=` form (proot rejects the separated form — rehearsed and pinned
  by unit tests).
- 8 new unit tests (211 total, 0 failures): argv/env pins, missing-artifact
  refusal, READY-only gate.

### Verified (sandbox rehearsal)
- The exact launch contract was rehearsed end-to-end on the sandbox host
  with the SAME proot source and the x86_64 variant of the SAME pinned
  Alpine 3.24.1 rootfs: `uname; id; echo hello; cat /etc/alpine-release`
  returned the guest kernel view, uid=0(root), hello, 3.24.1 and a working
  BusyBox — in BOTH loader modes, exit 0 (scripts/rehearse_m23_gate.sh).
- The Android-specific exec/ptrace policy is the remaining risk and stays
  the M2.3 device gate (docs/TESTING.md §8).

### Changed
- versionCode 5, versionName 0.3.0-m2.3.

## [0.2.1-m2.2] — 2026-09-01 — Fix: crash when tapping "Install Linux environment"

### Fixed
- **The app crashed (silent process death, no dialog) immediately after tapping
  "Install Linux environment" in Diagnostics** (observed in a device screen
  recording: spinner on the button → instant return to launcher). Two
  independent defects, both fixed:
  1. *Missing `INTERNET` permission.* The M1 app legitimately needed no
     network, and the manifest said so. M2.2 added a real HTTPS downloader but
     nobody revisited the permission set — the first connect threw
     `SecurityException("Permission denied (missing INTERNET permission?)")`.
     Fix: `INTERNET` is now declared, with the honest justification inline
     (used solely to fetch the checksum-pinned Alpine minirootfs from
     dl-cdn.alpinelinux.org).
  2. *No crash containment around runtime coroutines.* The installer converts
     its own failures to `FAILED` + a `Failed` event but rethrows; nothing
     caught the rethrow, so ANY pipeline failure (missing permission, offline
     device, DNS failure, even an `Error`) killed the whole app mid-install.
     Fix: `RuntimeCrashGuard` contains install/remove failures — they now land
     in the retryable `FAILED` / `REPAIR_REQUIRED` states the state machine
     already designed for them. A scope-level `CoroutineExceptionHandler` is
     the last-resort net.

### Added
- `RuntimeCrashGuard` (internal): the containment extracted into a unit-testable
  unit; `RuntimeManager` delegates to it.
- Regression tests (`RuntimeCrashGuardTest`, 6 new): the incident's exact
  `SecurityException` driven through the REAL installer pipeline must land in
  `FAILED` with transient state cleaned and must NOT escape; non-Exception
  `Error`s likewise; successful install passes through to `READY`; partially
  undeletable runtime on remove lands `REPAIR_REQUIRED` (and `clearRuntime()`
  returning false is now treated as the failure it is); retry transitions from
  `FAILED`/`REPAIR_REQUIRED` remain legal.

### Notes
- Emulator re-verification was attempted in the sandbox and is NOT possible
  there: Android 11+ (API 30+) system images are the only ones exposing
  `arm64-v8a` (required by the arm64-gated runtime), and the emulator enforces
  a fixed ~6 GB userdata floor for them (~7.2 GiB free required at boot) which
  cannot coexist with the SDK on the 9.9 GB sandbox disk. DEVICE VALIDATION
  for the install flow remains the M2.2 gate (docs/TESTING.md).
- Live CDN re-check (2026-09-01): pinned Alpine 3.24.1 aarch64 minirootfs
  still matches `SIZE_BYTES` + `SHA256` byte-for-byte.
- 58 app-module unit tests (52 + 6 new), 145 terminal-emulator tests, all
  green; assembleDebug clean.

## [0.2.0-m2.2-wip] — 2026-09-01 — M2.2: real Linux runtime installation layer

### Added
- `runtime/` package: Linux runtime installation vertical slice
  (docs/M2-ARCHITECTURE). Alpine minirootfs 3.24.1 (aarch64) pinned by URL,
  size and SHA-256; pipeline DOWNLOADING → VERIFYING → EXTRACTING →
  CONFIGURING → atomic promotion → READY.
- `RuntimeManager` facade + `RuntimeState` machine (illegal transitions
  rejected; state derived from disk at startup, incl. orphaned-tmp recovery
  and honest REPAIR_REQUIRED for damaged metadata).
- Safe extractor: zip-slip guard, GNU longnames, symlink/hardlink handling,
  POSIX mode preservation. Validated against the REAL Alpine 3.24.1 aarch64
  minirootfs (410 files, 635 symlinks) in a sandbox test run.
- Diagnostics screen: Linux runtime section (state, size, free space,
  metadata, install/retry/remove controls). No Home screen changes.
- New dependency: commons-compress 1.28.0 (Apache-2.0).

### Notes
- proot integration is M2.3; no process execution exists yet in M2.2.
- DEVICE VALIDATION REQUIRED: install flow must be exercised on real
  hardware (download over mobile network, state transitions, recovery).

## [0.1.1-m1] — 2026-08-31 — Fix: terminal did not repaint on session output

### Fixed
- **Terminal did not show typed input while the built-in keyboard was visible;
  text only appeared after toggling the keyboard off** (observed in a device
  screen recording). Root cause: the vendored `TerminalView` follows the
  upstream contract that the *host* must call `TerminalView#onScreenUpdated()`
  when a session's screen changes — the view never observes session data
  itself. Our `PocketShellSessionClient.onTextChanged` was an empty stub with
  an incorrect comment ("TerminalView invalidates itself").
  Fix: session clients now forward screen updates through a new
  `TerminalSessionManager.onScreenUpdateListener` hook, installed by
  `TerminalScreen` to call `onScreenUpdated()` (main thread — `TerminalSession`
  dispatches via its `MainThreadHandler`).
- Cursor never blinked: `setTerminalCursorBlinkerState` (upstream-documented
  host duty) was never called. Now started in `onEmulatorSet` and toggled with
  host lifecycle (ON_RESUME/ON_PAUSE).
- `TerminalView` was never focused: hardware (Bluetooth) keyboard input could
  not reach the terminal. The view now takes focus after attach.

### Verification
- All 166 unit tests pass; APK rebuilt and re-signed as `v0.1.1-m1`
  (versionCode 2). Regression items added to `docs/TESTING.md` §4 for the
  mandatory on-device re-check.

## [0.1.0-m1.1 / m1.2 / m1.3] — 2026-08-31 — Keyboard hardening · Input reliability · Polish

### M1.1 — Built-in keyboard hardening
- System IME fully suppressed inside the terminal (window `SOFT_INPUT_STATE_ALWAYS_HIDDEN`;
  PocketShell keyboard is the sole typing surface, brief §7).
- Terminal font size state plumbing (default from Settings, live pinch changes).
- App shell refactor: screens wired through a single root with settings-aware theming.

### M1.2 — Input reliability
- Foreground service (`specialUse`) keeps real session processes alive while
  backgrounded; runs only while sessions exist, stops itself when the last one
  closes/finishes (brief §24); notification states session count truthfully.
- Pinch-to-resize font with PTY reflow (scale thresholds → `setTextSize` →
  upstream `TIOCSWINSZ`).
- Clipboard copy/paste via upstream selection ActionMode (wired through
  `PocketShellSessionClient` to the system clipboard).

### M1.3 — UI/UX polish
- Settings screen: theme mode (System/Light/Dark/AMOLED), dynamic color
  (Android 12+, honest fallback), default terminal font size (DataStore-persisted).
- Diagnostics screen: read-only runtime facts only — version, API level, device,
  ABIs, shell presence, PTY library state, HOME/TMPDIR, real session counts,
  exact permission list (brief §34).
- Theme system: restrained brand scheme + AMOLED pure-black surfaces + dynamic
  color where the platform provides it.
- Home header gains Settings/Diagnostics entries.

### Verification
- Full build + all unit tests pass.
- **Manual on-device acceptance for M1/M1.1/M1.2/M1.3 remains pending
  (docs/TESTING.md §3–§5) — mandatory human step before M2 (brief §27/§28).**

---

## [0.1.0-m1] — 2026-08-31 — M1: Terminal Foundation

### Added
- Gradle skeleton: settings/root/version catalog (`gradle/libs.versions.toml`),
  wrapper (Gradle 8.14.5), pinned AGP 8.13.2 / Kotlin 2.4.10 / platform 36 /
  NDK 28.2.13676358.
- Vendored `:terminal-emulator` + `:terminal-view` (byte-identical upstream
  sources at pinned commit; Kotlin-DSL build ports documented in THIRD_PARTY.md).
- `ShellEnvironment` — real `/system/bin/sh` runtime (HOME/TMPDIR/TERM/PATH),
  executable resolver.
- `TerminalSessionManager` — process-scoped multi-session owner; per-session
  PTY/env/cwd/title; finished sessions marked, never faked; CLI-app session
  factory with executable verification.
- `PocketShellSessionClient` / `PocketShellTerminalViewClient` — upstream
  client implementations bridging events, clipboard, logging and keyboard
  modifier hooks.
- PocketShell keyboard v1 (full §8 coverage): QWERTY + digits + 28-symbol page
  + extended row (INS/DEL/HOME/END/PGUP/PGDN), ESC/TAB/ENTER/BACKSPACE,
  arrows, FN layer (F1–F12, HOME/END/PGUP/PGDN, DEL), one-shot/locked
  modifiers with always-visible state, hold-to-repeat, haptics,
  phone/tablet adaptive layouts.
- `TerminalKeyDispatcher` — single input pipeline: keyboard → synthetic
  KeyEvents → vendored TerminalView → upstream KeyHandler → PTY.
- Compose UI: Home (honest empty states), Terminal (tabs + TerminalView +
  keyboard), Explore placeholder (honest M2 notice); navigation without extra
  dependencies.
- CLI app architecture: `CliApp` model, DataStore registry (empty by default),
  verified launcher.
- Tests: upstream emulator suite (19 classes, all pass), keyboard state
  machine, §8 symbol coverage, FN remaps, CLI app model/resolver.

### Fixed
- Kotlin `KeyAction.Char` name clashed with `kotlin.Char` → renamed to
  `KeyAction.Text`.
- `KeyCharacterMap.getEvents` requires an instance; use `load(VIRTUAL_KEYBOARD)`.
- Upstream `TerminalView` exposes only `(Context, AttributeSet)` constructor.

### Verification
- `./gradlew :app:assembleDebug` → 20 MB debug APK containing `libtermux.so`
  for all 4 ABIs, 16 KB-aligned (align 2**14).
- All unit test suites pass (emulator + app).
- **Manual on-device acceptance pending (docs/TESTING.md §M1) — requires a
  human with real hardware; no device exists in this sandbox.**

---

## [0.1.0-m0] — 2026-08-30 — M0: Research + Architecture

### Added
- Project brief compliance docs: `README.md`, `docs/RESEARCH.md`,
  `docs/ARCHITECTURE.md`, `docs/THIRD_PARTY.md`, `docs/ROADMAP.md`,
  `docs/TESTING.md`, `docs/CHANGELOG.md`.
- Build environment installed and pinned: Gradle 8.14.5, AGP 8.13.2,
  Kotlin 2.4.20, SDK platform android-36, build-tools 36.0.0, NDK 28.2.13676358.

### Decisions
- D1: vendor Termux `terminal-emulator` + `terminal-view` at pinned upstream
  commit `3b66f8799635a4dba4a206563048ff0e6792c487` (GPLv3 consequence accepted).
- D2: reuse upstream PTY JNI (`libtermux`) verbatim.
- D3: M1 shell = `/system/bin/sh` + toybox applets; no userspace runtime until M2.
- D4: PocketShell keyboard routes input as synthetic KeyEvents through the
  upstream `TerminalView` pipeline with `KeyboardState` answering modifier hooks.

---

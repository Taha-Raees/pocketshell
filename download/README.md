# download/ — delivery masters

Current: **v0.11.2-m7.1.1-m72p4** (M7.2 P4 — notification consumption of the
runtime event engine; the FIRST USER-VISIBLE M7.2 phase: the P3c event stream
now drives honest Android notifications — confirmed running / runtime unknown /
no-longer-detected / the session's own exit fact — with no completion claim
anywhere; phase build on the inherited versionCode 47 / versionName
0.11.2-m7.1.1 stamp per the M7.x phase-build precedent — the -m72p4 suffix is
filename-only; the M7.1.1 fix release v0.11.2-m7.1.1 remains the frozen
fix-record below it in history).

The P4 pair (APK + bundle) is the CURRENT SERVED SET on the delivery page.
The M7.2 P3b pair (the previous served set) is superseded by this phase build
(same stamp, same cert — the P4 build adds the notification consumer layer on
top of P3b/P3c) and is withdrawn from the serving surface — its insurance copy
SURVIVES in upload/ byte-exact (APK fc1edb51…), and its content and history
ride in this bundle (the P3b record tip 0f091e7 and the P3c record tip 66d91da
are direct ancestors of the P4 record tip 5b236df, which is an ancestor of
the P4 delivery tip e385021). P3c's bundle-only
convention ends here: P4 IS user-visible, so it delivers BOTH the APK and the
bundle (docs/TESTING.md §53 owns the on-device notification-shade gate).

CUT NOTE: the P4 set was cut fresh from the P4 delivery tip e385021 (chain
bede512 implementation+tests → 49039e2 docs → the Task 43 worklog record
5b236df → the delivery record 7444d28 → the page re-pin 2e8f0c6 → the
doc-number correction e385021) on
the re-provisioned toolchain (Temurin 21.0.12.1+1, cmdline-tools 11076708,
platform-36, build-tools 36.0.0, NDK 28.2.13676358 — reinstalled by
scripts/install_toolchain.sh after the latest sandbox reset, per worklog
1c9ce59). The M7.2 P1-era note below is retained for the insurance-copy
ledger: that set is SUPERSEDED by the later phase builds (same stamp, same
cert) and its insurance copies SURVIVE in upload/ byte-exact.

RESTORE NOTE (insurance, carried): upload/ holds byte-identical copies of
the FULL current set (APK, bundle, zip, tar.gz — glibc regenerable from
the tracked in-tree asset), so any future sandbox reset restores the
whole delivery byte-identically. Source-archive bytes remain not
re-cut-stable across sandbox toolchain builds, as always disclosed.

- PocketShell-v0.11.2-m7.1.1-m72p2-debug.apk  sha256 4a144d23710e7cd3893d1cdb59b0e3892a7b585ba1d4ed9015a7988360d2dfce  (30,802,996 B, versionCode 47 / 0.11.2-m7.1.1 — the inherited phase stamp)
  Installs IN PLACE over the M7.2 P1 phase build (vc47 — same versionCode,
  updated content, same pinned cert), the M7.1.1 fix (vc47), the M7.1
  release (vc46), every M7.1 phase build (vc45), the M7.0 release, and
  every previous pinned-cert build (vc16..47, same cert d96a6f66…8bf659).
  App data survives; P2 adds NO persistence (the lifecycle engine is
  in-memory, process-scoped like the sessions themselves) and posts NO
  notifications. Semantic pins verified on these exact bytes: version
  (aapt2 badging versionCode='47' versionName='0.11.2-m7.1.1', targetSdk
  28), the UNCHANGED 6-permission merged set (INTERNET,
  ACCESS_NETWORK_STATE, FOREGROUND_SERVICE,
  FOREGROUND_SERVICE_SPECIAL_USE, POST_NOTIFICATIONS,
  DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION), embedded rev=2 layer asset
  sha 898131ff… / 17,920,000 B, dex carries the six new P2 symbols
  (SessionLifecycleState / SessionLifecycleEvent / ExitStatus /
  SpawnOrigin / AgentHint / AgentActivityRepository) beside the P1
  notifications symbols, the M7.1.1 keyboard symbols and the launcher
  phases' symbols. Verification executed at this tip (docs/TESTING.md
  §49 + the honest JVM-vs-device split recorded there): FULL JVM suite
  forced --rerun-tasks 810/810 green (app 665 = 635 + 30 new lifecycle
  tests + terminal-emulator 145, 0 failures / 0 errors / 0 SKIPPED),
  including the verification-honesty fix (the P1 structural pins
  silently skipped under the module-dir runner and now run and pass).
  What P2 adds:
    - The typed lifecycle model: SessionLifecycleState (STARTING →
      RUNNING → FINISHED; REMOVED = tab removal plus a typed event)
      with a private constructor making invalid combinations
      unrepresentable; the stored isFinished boolean retired (the
      getter is DERIVED).
    - Structured exit status faithful to the underlying API:
      ExitStatus.Exited(code) / ExitStatus.Signaled(signal) — exactly
      what JNI.waitFor/waitpid provides; nothing invented, no polling.
    - ONE authoritative owner with race-safe pure transitions: the real
      fork signal (the previously-discarded setTerminalShellPid
      callback) and the real waitpid delivery drive every change;
      duplicate/out-of-order callbacks are rejected with logged reasons
      and can never corrupt a recorded status; the close path is
      guarded against the upstream kill(0) hazard for a never-forked
      pid.
    - Structured launch identity at every spawn site: SpawnOrigin
      (Shell / LinuxShell / FilesTerminal / CommandApp(id) /
      CatalogApp(id) / CustomTool(id)) + AgentHint(displayName, command,
      matchedBy=LAUNCH_METADATA) for the three named-launcher paths —
      spawn metadata only, never a process claim.
    - Typed SessionLifecycleEvents emitted only at mutation sites +
      AgentActivityRepository (the derived read model that stores
      nothing, decides nothing, touches no notifications).
    - HONEST SCOPE: no notifications posted (the P1 sweep stays
      dormant), no agent detection, no waiting-for-input heuristics, no
      /proc scanning, no OSC 133, no persistence, NO user-visible UI
      change. The real-device gate is §49 (10 steps — a parity
      regression pass).

- PocketShell-v0.11.2-m7.1.1-m72p2-source.zip  sha256 996148b0fe4573ef14f44b43880efa8b2d02a6304fa2438e4d23773c7c78714e  (50,446,875 B — tracked source cut at the M7.2 P2 record tip 3b144be via git archive, web shim + platform noise excluded, zeroed mtimes; RESTORE.txt + the m7.2-p2 git bundle embedded)
- PocketShell-v0.11.2-m7.1.1-m72p2-source.tar.gz  sha256 ea19e8232d9c4294046f730843e763c2f7e05bb077ff2dafee817eda3d326300  (50,144,080 B — same cut, sorted tar, gzip -n)
- pocketshell-m7.2-p2.gitbundle  sha256 d821d94fe21181574a706ceba88368e4defd336f01ec340393af947b91b0c548  (37,687,491 B — full history M0 → main tip 3b144be, the P2 record tip (implementation 0c9a792 → tests 577e1e9 → docs 133e656 → worklog 3b144be) that the source archives are cut at; the APK was built from the identical app sources (the app tree is unchanged since 0c9a792); bundle pack bytes are not re-cut-stable, so the pins refer to this ONE delivered cut — re-cutting requires re-pinning)

- PocketShell-v0.11.2-m7.1.1-m72p4-debug.apk  sha256 ab73b24ab52e40662de45ad5c0c2aacb50494e1889d8428717266f080634442f  (30,859,664 B, versionCode 47 / 0.11.2-m7.1.1 — the inherited phase stamp; the P4 notification-consumption build: same cert d96a6f66…8bf659, the unchanged 6-permission set, the P4 symbols (AgentRuntimeNotificationConsumer / AgentRuntimeNotificationMapping / AGENT_RUNTIME_BASE / CHANNEL_AGENT_RUNTIME) in the dex; the notification surfaces are device-verifiable in the shade per TESTING §53 — the ten-step gate: FGS regression, confirmed running, no duplicates, withdrawal on no-longer-detected, factual exit wording (code/signal preserved), honest uncertainty, the three permission arms, no stale surfaces after force-stop, multi-session isolation, the shade-wide honesty sweep)
  Installs IN PLACE over the M7.2 P3b/P3a/P2/P1 phase builds (vc47 — same versionCode, signature-identical). The manual gate is docs/TESTING.md §53 (10 steps; requires a supported agent install in the guest for the runtime arms).

- pocketshell-m7.2-p4.gitbundle  sha256 9c6e4a1bcc258ca65fefcf8102ffd7095a71ef40e7dec19c99e6717fd8e2e5a7  (37,874,595 B — full history M0 → HEAD tip e385021, the P4 DELIVERY tip = the worklog record 5b236df + the delivery record 7444d28 + the mandated page re-pin 2e8f0c6 + the measured-counts doc correction e385021, so the delivered docs carry the exact verification numbers (the worklog record tip 5b236df is the implementation/worklog boundary the phase convention names; one commit beyond it was taken deliberately so the bundle's own contract doc says 1932/1932, not the pre-run estimate — disclosed, not silent); the P4 chain: implementation+tests bede512 → docs 49039e2 → worklog 5b236df; P4 = the notification consumption layer per docs/M7.2-P4-NOTIFICATION-CONSUMPTION.md — the ONE consumer subscribed once at Application start folding the P3c events through the pure truth contract, deterministic per-session identity (AGENT_RUNTIME_BASE + sessionId), defensive dedup (posted/everPosted/tombstones), the calm agent_runtime channel, the untouched FGS, zero new permission machinery, three-way stale cleanup; 1932/1932 JVM forced rerun, 0 skipped; clone drill green — clone HEAD == e385021 == this cut, 330 commits, clean tree, P4 sources + doc + TESTING §53 present, versionCode 47 / 0.11.2-m7.1.1; insurance copy byte-identical in upload/; the delivery page re-pinned to the P4 pair — P4 IS user-visible)

- pocketshell-m7.2-p3c.gitbundle  sha256 3ca73dc9101cd7304f88c101e5a4628ec3350a9aed40a2692a88873ddb441532  (37,816,877 B — the M7.2 P3c INTERNAL/INFRASTRUCTURE bundle: full history M0 → main tip 66d91da, the P3c record tip (implementation+tests f782272 → docs 15d2ee4 → worklog 66d91da); P3c = the runtime transition & event engine per docs/M7.2-P3C-EVENT-ENGINE.md — the pure AgentRuntimeTransitions reducer folding P3b observations + the manager's authoritative session state into the typed AgentRuntimeEvent vocabulary (Launched / ConfirmedRunning with pids+grade / NoLongerDetected — never completion / RuntimeUnknown / SessionEnded with the session's own waitpid status or the removal cause), per-session dedup (same-state = no event), staleness rejection, the replay-free SharedFlow the future notification phase consumes; 1850/1850 JVM forced rerun, 0 skipped; APK audited but NOT the delivery (no user-visible change — TESTING §52); clone drill green — clone HEAD == 66d91da == this cut, 322 commits, clean tree, P3c sources + doc + TESTING §52 present, versionCode 47 / 0.11.2-m7.1.1; insurance copy byte-identical in upload/; the served page stays pinned to the P3b pair — P3c posts no page re-pin)
- pocketshell-m7.2-p3b.gitbundle  sha256 e6d22571be67cebc4c0793af9278b3010fcdffa40b7e78e2b81ea0bdd3569c05  (37,771,483 B — the M7.2 P3b INTERNAL/RUNTIME CHECKPOINT bundle: full history M0 → main tip 0f091e7, the P3b record tip (implementation+tests 5993fd3 → docs 3683fd1 → worklog 0f091e7); P3b = the runtime agent detection layer per docs/M7.2-P3B-RUNTIME-DETECTION.md — the /proc descendant scanner with fork-proven correlation (ppid-chain ∪ process-group), graded PROCFS_EXE/PROCFS_CMDLINE exact-token matching, the four-state NOT_APPLICABLE/UNKNOWN/NOT_RUNNING/RUNNING contract, gated 2-second polling; 1764/1764 JVM forced rerun, 0 skipped; clone drill green — clone HEAD == 0f091e7 == this cut, 315 commits, clean tree, P3b sources + docs present, versionCode 47 / 0.11.2-m7.1.1; insurance copy byte-identical in upload/)

- [SUPERSEDED by the P4 phase build above; insurance copy survives in upload/] PocketShell-v0.11.2-m7.1.1-m72p3b-debug.apk  sha256 fc1edb518bdb853f2d42a89d12d20209b36974578dd894e54941bd71804e704e  (30,802,996 B, versionCode 47 / 0.11.2-m7.1.1 — the inherited phase stamp; the P3b runtime-detection build: same cert d96a6f66…8bf659, the unchanged 6-permission set, all P3b symbols in the dex; the runtime scanner is device-verifiable via adb logcat per TESTING §51 — NO agent-status UI exists in P3b by design, the only observable channel is the AgentRuntimeDetector log)
  Installs IN PLACE over the M7.2 P2/P3a phase builds (vc47 — same versionCode, signature-identical). The manual gate is docs/TESTING.md §51 (12 steps; requires a real agent install in the guest).

- pocketshell-m7.2-p3a.gitbundle  sha256 45bdecea4c0008bc472c3e97feade3e9776acfec3ff9f0ebbc9d2b65adad3211  (37,723,474 B — the M7.2 P3a INTERNAL CHECKPOINT bundle (no new APK, no source archives, no page re-pin — the P2 set above stays the served build): full history M0 → main tip 5fe3602, the P3a record tip (signal model + tests f4c8afd → docs 8ff2e12 → worklog 5fe3602); P3a = the trusted-agent-signal audit + the LaunchIdentity truth-boundary model per docs/M7.2-P3A-DETECTION-MATRIX.md, JVM-verified only (833/833, TESTING §50) with NO user-visible change; clone drill green — clone HEAD == 5fe3602 == this cut, 310 commits, clean tree; insurance copy byte-identical in upload/)

- pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz  sha256 ed82daa8b0d487080d833913bfa74def01a628d58a4f7258e31eb3bef56a7c3d  (6,764,916 B — the glibc layer artifact rev=2, UNCHANGED since m6.0.4; the APK ships the identical bytes decompressed (sha 898131ff…, 17,920,000 B, pinned as GlibcRuntimePin.ASSET_SHA256) and installs them itself)

Withdrawn (superseded by this M7.2 P2 phase build; withdrawn from the serving surface — insurance copies survive byte-exact in upload/, history in the bundle): PocketShell-v0.11.2-m7.1.1-m72p1-debug.apk (e63fb9b5…, 30,473,480 B, the M7.2 P1 notification-foundation APK), the m72p1 source.zip/tar.gz (b4c9eb53…/147441d4…), pocketshell-m7.2-p1.gitbundle (23c9117e…).

Withdrawn (superseded by the M7.2 P1 phase build, stands): PocketShell-v0.11.2-m7.1.1-debug.apk (5d876141…, 30,451,377 B, the M7.1.1 FIX RELEASE APK — stays the frozen fix-record), the m7.1.1 source.zip/tar.gz (d8c1ef85…/d183c3eb…), pocketshell-m7.1.1.gitbundle (af73c57b…). Also withdrawn: pocketshell-m7.2-p0.gitbundle (0d8c5eb0…, head 02e8166, 37,607,042 B — the M7.2 P0 docs-only audit bundle; its entire content is contained in this bundle's history; the audit itself is docs/M7.2-P0-AUDIT.md at c8d0059).

Withdrawn (superseded by the M7.1.1 fix, stands): PocketShell-v0.11.1-m7.1.0-debug.apk (2d298c85…, 30,448,845 B, the OFFICIAL M7.1 RELEASE APK — stays the frozen milestone record), the m7.1 source.zip/tar.gz (0b4e8843…/d00d076f…), pocketshell-m7.1.gitbundle (7f677222…).

Withdrawn (superseded by the M7.1 release, stands): PocketShell-v0.11.0-m7.0.0-m7p3-debug.apk (46fb0d8b…, 30,448,845 B, the P3 external-keyboard APK), the m7p3 source.zip/tar.gz (d71cc7a2…/8bc28a92…), pocketshell-m7.1-p3.gitbundle (8b0544b5…).

Withdrawn (superseded by P3, stands): PocketShell-v0.11.0-m7.0.0-m7p2.2-debug.apk (7e0e99a9…, 30,664,394 B, the P2.2 theme-icons + x-scroll-rows APK), the m7p2.2 source.zip/tar.gz (0e2b9d3d…/a45a0f02…), pocketshell-m7.1-p2.2.gitbundle (1e8822fc…).

Withdrawn (superseded by P2.2, stands): PocketShell-v0.11.0-m7.0.0-m7p2.1-debug.apk (97c04120…, 30,342,970 B, the P2.1 Aider-removal + owner-marks APK), the m7p2.1 source.zip/tar.gz (1bb4c41f…/a38213b3…), pocketshell-m7.1-p2.1.gitbundle (fb0887b5…).

Withdrawn (superseded by P2.1, stands): PocketShell-v0.11.0-m7.0.0-m7p2-debug.apk (ae6f6445…, 30,447,567 B, the P2 UI-repair APK — itself a post-reset re-serve), the m7p2 source.zip/tar.gz (e1dbbe6d…/260f0163…, the post-reset re-cut), pocketshell-m7.1-p2.gitbundle (3e1dcaaf…).

Withdrawn (superseded earlier, stands): the M7.1 P1 set (4d7349f7… APK, m7p1 cuts 3bdd755f…/f6bad9aa…, bundle 98a80b0c…) — withdrawn by P2 with the same-stamp reason; and the M7.0 release set (8826d30d… APK, fc354ee3…/a5a86f5f… cuts, b1931f9c… bundle) — withdrawn by P1 with the same-stamp reason.

Withdrawn (lost to earlier sandbox resets, NOT re-servable): the m7p8.1 set (f316ec67… APK, 5f1f3eb2…/788970c0… cuts, 1068ed17… bundle), the m7p7.1 set (189deebb… APK, 200c4e2a…/f12df8c5… cuts, 0f8fd0a3… bundle — the user's copy of that bundle WAS the recovery source), the m7p6 APK (35ae7a48…), the m6.0.4 APK (e633ca3c…) and its source cut, pocketshell-runtime-tests-aarch64.tar.gz (90009339… — re-cut from the bundle sources at the next M6 runtime gate), the forensic audit PDF.

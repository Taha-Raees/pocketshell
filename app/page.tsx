const VERSION = "v0.11.2-m7.1.1-m72p6";

// SHA pins — the M7.2 P6 (runtime notification device-state refinement)
// delivery set. The bundle is cut at the P6 record tip (08cad7e; the page
// re-pin and the delivery record ride after the cut — every P6 contract doc
// inside the bundle is final); the APK is the audited phase build at the
// inherited stamp (versionCode 47, versionName 0.11.2-m7.1.1 — the -m72p6
// suffix is filename-only), cert d96a6f66…8bf659, the unchanged
// 6-permission set. The P6 rebuild is BYTE-IDENTICAL to the P5 audited APK
// (69ab4402… on both — the reproducible-build proof of zero production
// delta). The glibc layer is UNCHANGED rev=2 (byte-identical artifact
// ed82daa8…). The source zip is a git-archive snapshot of the record tip —
// by construction it contains NO APK and NO bundle.
const HASHES = {
  apk: "69ab44021b607307e06197b5acafbb53de1df840a4bc802f63da57852ccfc57f",
  bundle: "c16552e28fd4a29e94dda20bb994d4926fe5160408d5ee169eafaaaf7bc3ed97",
  sourceZip: "0d776ca390095ba52903177812be6433c1abf21c8370acdc1f350f7df6439686",
  glibc: "ed82daa8b0d487080d833913bfa74def01a628d58a4f7258e31eb3bef56a7c3d",
};

function Sha({ text }: { text: string }) {
  return <div className="mono">sha256 {text}</div>;
}

export default function Home() {
  return (
    <main className="wrap">
      <h1>
        PocketShell <span className="badge">{VERSION}</span>
      </h1>
      <p className="sub">
        A real Linux terminal for Android — Termux-grade terminal emulator,
        Alpine Linux guest via proot, real <code>apk</code> package manager.
        REUSE → INTEGRATE → OPTIMIZE → IMPROVE. Nothing faked, ever.
      </p>

      <div className="card primary">
        <h2>
          M7.2 P6 — RUNTIME NOTIFICATION DEVICE-STATE REFINEMENT: the
          verification phase — every honest runtime transition audited,
          pinned as one continuous story, and gated on hardware where
          naturally reproducible — with ZERO production change{" "}
          <span className="badge">record tip 08cad7e · vc47</span>
        </h2>
        <p>
          <b>
            Eighth M7.2 phase, and a VERIFICATION phase by design: P6 adds NO
            production behavior and NO new detection power — what changed is
            how tightly the existing truthful transitions are pinned.{" "}
            <b>THE TRANSITION STORY (one JVM fold, 11 new tests/variant)</b>:
            running → unknown updates ONE deterministic notification identity
            in place (uncertainty-only wording); unknown → running restores
            it on the same identity; running/unknown → no-longer-detected
            CANCELS it (never re-worded as completed/success/finished); an
            ever-announced session&apos;s end yields only the factual exit
            statement — exit 0 is never success through every fold;
            duplicate-delivery storms produce one surface per real state;
            runtime flapping never accumulates notifications or tombstones.{" "}
            <b>ONE IDENTITY PER SESSION STORY</b>: every ShowRuntime /
            ShowExitFact / Cancel action of a story lands on the single id
            NotificationIds.agentRuntime(sessionId) — assertOneIdentity over
            every fold. <b>THE DEVICE GATE IS HONESTLY SPLIT (§55)</b>:
            naturally reproducible on hardware — withdrawal (quit the agent
            inside a live session), in-session reappearance (relaunch), birth
            silence (no uncontextualized unknown notification at spawn),
            exit / exit-3 / tab-close facts, the §54 tap regression, FGS —
            and NOT DEVICE-REPRODUCIBLE IN P6: the mid-flight
            notification-level unknown cycle (needs a failed procfs scan or a
            matched-pid argv-shape mutation — neither deterministically
            producible without fabricating evidence, which is forbidden; it
            stays JVM-pinned and documented). <b>ZERO PRODUCTION DELTA,
            PROVEN BY BYTES</b>: the assembleDebug rebuild came out
            BYTE-IDENTICAL to the P5 audited APK (sha256 69ab4402… on both —
            the strongest possible statement that shipped behavior did not
            change). <b>THE P4/P5 CONTRACTS ARE UNTOUCHED</b>: the P4 wording
            byte-frozen, the P5 tap routing byte-frozen, no new permission,
            channel, manifest entry, detector power, debug button, or fake
            injection. <b>VERIFICATION</b>: FULL JVM suite forced
            --rerun-tasks 2008/2008 green (app 859×2 = 848+11 new ×
            terminal-emulator 145×2, 0 failures, 0 errors, 0 skipped),
            measured before the docs were written.{" "}
            <b>HONEST SCOPE</b>: P6 proves the existing transition machinery
            is coherent, identity-stable, dedup-safe and honest — and NOTHING
            about agents: no completion, no success, no failure, no
            waiting-for-input, no control action. The REAL-DEVICE gate is
            docs/TESTING.md §55 (the naturally reproducible subset; B4 is a
            documentation gate whose NOT-REPRODUCIBLE status is the expected
            outcome). Full contract:
            docs/M7.2-P6-RUNTIME-STATE-TRANSITIONS.md.
          </b>
        </p>
        <a className="btn" href="/PocketShell-v0.11.2-m7.1.1-m72p6-debug.apk">
          Download M7.2 P6 APK (debug, 30.5 MB — byte-identical to the P5
          audited build)
        </a>
        <Sha text={HASHES.apk} />
        <a className="btn secondary" href="/pocketshell-m7.2-p6.gitbundle">
          git bundle — full history M0 → P6 record tip (37.9 MB)
        </a>
        <Sha text={HASHES.bundle} />
        <a className="btn secondary" href="/pocketshell-m7.2-p6-source.zip">
          source zip — git archive of the P6 record tip (12.4 MB, no binaries)
        </a>
        <Sha text={HASHES.sourceZip} />
        <p className="mono" style={{ border: "none", background: "transparent", padding: 0 }}>
          versionCode 47 / versionName 0.11.2-m7.1.1 (the M7.x phase-build
          precedent — phase builds ride the inherited stamp, the -m72p6
          suffix is filename-only) — installs in place over every earlier
          build (same cert) — glibc layer ed82daa8… unchanged.
        </p>
      </div>

      <div className="card">
        <h2>
          Superseded &amp; withdrawn builds{" "}
          <span className="badge">history preserved</span>
        </h2>
        <p>
          The M7.2 P5 served pair (APK 69ab4402…, bundle ffe236b3…) is
          SUPERSEDED by this phase set: the P6 APK is BYTE-IDENTICAL to the
          P5 audited build (the zero-production-delta proof — same stamp,
          same cert, the unchanged 6-permission set and glibc layer; only
          the filename carries the phase), and the P6 bundle contains the
          entire chain (the P5 record tip d24bfde, the page re-pin 38512ea
          and the delivery record 1cbd287 are direct ancestors of this
          record tip 08cad7e). The P5 bytes are no longer served (the
          upload/ insurance copies survive byte-exact). P6 also ships a
          source zip (0d776ca3…) — a git-archive snapshot of the record tip
          with NO binaries by construction. The M7.2 P4 served pair (APK
          ab73b24a…, bundle 9c6e4a1b…) is superseded in turn: same stamp
          (vc47), same cert — the P5 build added the
          session-targeted tap routing on top of the P4 consumer, the P3b
          detector and the P3c event engine, and the bundle contains
          the entire chain (the P4 record tip 5b236df AND the P4 delivery tip
          e385021 are direct ancestors of this record tip). The P4
          bytes are no longer served (the upload/ insurance copies survive
          byte-exact). The M7.2 P3c checkpoint stays bundle-only by design
          (bundle 3ca73dc9… — no user-visible change existed to serve); P4
          and P5 ARE user-visible, so both delivered the APK and the bundle.
          The M7.2
          P3a checkpoint remains internal by design (bundle
          45bdecea…, upload/ copy, never served — P3a deliberately shipped no
          APK release). Earlier withdrawals stand: the M7.2 P2 served set
          (APK 4a144d23…, source zip 996148b0…, source tar.gz ea19e823…,
          bundle d821d94f…), the M7.2 P1 APK (e63fb9b5…),
          the M7.1.1 fix APK (5d876141…), the M7.1 release APK (2d298c85…),
          the M7.1 P3 APK (46fb0d8b…), the M7.1 P2.2 APK (7e0e99a9…), the
          M7.1 P2.1 APK (97c04120…), the M7.1 P2 APK (ae6f6445…), the M7.1 P1
          APK (4d7349f7…), the M7.0 release APK (8826d30d…), the m7p8.1 (vc44)
          and the reset-lost m7p8/m7p7.1/m7p6/m6.0.4 sets — their content and
          history are fully contained in this bundle, as are the M7.2 P0 audit
          (docs/M7.2-P0-AUDIT.md, the P0 bundle 0d8c5eb0… was a docs-only
          direct-URL artifact) and the P3a detection matrix
          (docs/M7.2-P3A-DETECTION-MATRIX.md). The glibc layer artifact
          (rev=2) is byte-identical and served below.
        </p>
      </div>

      <div className="card">
        <h2>Update — no uninstall, no runtime reinstall</h2>
        <p>
          versionCode 47 installs <b>in place over the M7.2 P4 phase build
          (47 — same versionCode, updated content, same pinned cert), the
          M7.2 P3b phase build (47), the
          M7.2 P3a/P2/P1 phase builds (47), the M7.1.1 fix release (47), the
          M7.1
          release (46), the M7.1 phase builds (all
          45 — P1, P2, P2.1, P2.2, P3), the M7.0 release (45),
          v0.10.0-m6.0.4
          (44),
          v0.10.0-m6.0.3 (43),
          v0.10.0-m6.0.2 (42),
          v0.10.0-m6.0.1 (41),
          v0.10.0-m6.0.0 (40),
          v0.9.1-m5.1.0 (39),
          v0.9.0-m5.0.1 (38), v0.9.0-m5.0.0 (37),
          v0.8.0-m4.0.12 (36), v0.8.0-m4.0.11 (35),
          v0.8.0-m4.1.0 (34, an intermediate that was never announced),
          v0.7.0-m4.0.9 (33) and every earlier pinned-cert build</b>. Your
          Alpine runtime, installed packages, Cline installation, the procfs
          contract, all Files explorer data, all Companion data and the
          launcher visibility/icon settings are untouched. M7.2 P5 is
          APP-side only: the layer marker and the glibc files stay
          byte-identical (rev=2, ed82daa8…), and the tap-routing layer adds
          no persistence at all — one nullable activity-scoped navigation
          target beside the P1 posted-id ledger, like the process-scoped
          sessions themselves. M7.2 P6 changes NO shipped byte: the P6 APK
          is byte-identical to the P5 audited build (the rebuild proves it —
          69ab4402… on both), and the verification phase adds only test
          sources and documents — zero runtime delta, zero persistence.
        </p>
      </div>

      <div className="card">
        <h2>What this build actually does (honest scope)</h2>
        <ul className="steps">
          <li>
            Real terminal (M1): Termux-emulator PTY sessions, clipboard paste,
            scrollback, selection.
          </li>
          <li>
            Linux runtime (M2.2): pinned Alpine 3.24.1 aarch64,
            SHA-256-verified, installed into app storage.
          </li>
          <li>
            Linux Shell (M2.3 + M2.6 + 3.6): the Alpine guest via proot —
            guaranteed real <code>/proc</code>, apk that survives guest-side
            upgrades.
          </li>
          <li>
            Package management (M2.4/M2.5): the Packages screen — search,
            install, uninstall, open.
          </li>
          <li>
            Phase 3.1–3.5 + m4.x + m5.x: Midnight Sapphire terminal + Home
            workspace, the CLI registry, and the Companion workspace with every
            evidence-driven fix along the way.
          </li>
          <li>
            <b>v0.10.0-m6.0.0–m6.0.4:</b> Universal Runtime Compatibility —
            real glibc 2.41 at canonical multiarch paths inside the Alpine
            guest, self-healing pinned delivery, install observability, the
            doctor correctness gate, and the adversarial closure audit.
          </li>
          <li>
            <b>M7.0.0 (phases 1–9):</b> one storage abstraction behind the
            Files explorer (Linux, the Downloads shelf, user-granted SAF
            folders) with real operations and collision semantics, the
            byte-honest quick text editor, Open Terminal Here (a normal Alpine
            session in THE TAPPED folder), recursive file search composed from
            the unchanged storage abstraction, multi-select with honest
            aggregates, and the P9 interaction fixes.
          </li>
          <li>
            <b>M7.1 (frozen, v0.11.1-m7.1.0):</b> Home launchers (the
            Companions + Your tools grids, hide/restore, custom tools over
            verify-then-launch), the launcher UI repair, the curated official
            two-variant theme marks (26 assets), one x-scroll Companions row +
            two tools rows with scroll dots, the tools-header packages
            affordance, and live external-keyboard detection.
          </li>
          <li>
            <b>M7.1.1 (v0.11.2-m7.1.1):</b> the external-keyboard system
            rebuilt after the real-device failure — the ONE authoritative
            ExternalKeyboardVisibilityModel (persistent On-screen keyboard
            preference + hardware state + explicit user request → effective
            visibility), the GATED terminal-canvas tap (it no longer undoes
            the auto-hide), the 2 s confirm deadline against event storms,
            the configuration-change cross-check as a second detection
            mechanism, both-direction transition notices (connect AND
            disconnect), and the preference-aware restore (an OFF preference
            is never forced back on).
          </li>
          <li>
            <b>M7.2 P1:</b> the notification foundation — the
            POST_NOTIFICATIONS runtime request exactly once per install on
            Android 13+ (first-session trigger, flag-before-dialog anti-nag,
            system-prompt denials respected, denial never blocks the
            terminal), the notifications/ coordinator layer (session_events
            channel, deterministic ids, posted-id ledger, startup stale
            sweep, FLAG_IMMUTABLE routing intents), and intent routing on
            both activity paths — infrastructure only, no event notifications
            posted yet, no agent detection, no heuristics.
          </li>
          <li>
            <b>M7.2 P2:</b> the session lifecycle engine &amp; structured exit
            status — the typed STARTING→RUNNING→FINISHED machine (REMOVED as
            removal-plus-event, never a flag), the waitpid exit status
            surfaced as exited(code)/signaled(signal), structured
            SpawnOrigin/AgentHint identity at every spawn site, race-safe
            one-owner transitions (duplicate-callback idempotency, the
            kill(0) close guard), typed lifecycle events and the derived
            AgentActivityRepository — in-memory only, no notifications, no
            agent claims, no UI change.
          </li>
          <li>
            <b>M7.2 P3a:</b> the trusted agent signal audit &amp; detection
            design — LaunchIdentity (sealed KnownAgent / KnownNonAgentTool /
            CustomOrUnknown, pure registry resolution) and the derived
            classifiedLaunches projection; the detection matrix over the real
            inventory (docs/M7.2-P3A-DETECTION-MATRIX.md); the truth boundary
            established: Launched ≠ Running ≠ Completed — no runtime claims at
            this layer.
          </li>
          <li>
            <b>M7.2 P3b:</b> runtime agent detection via <code>/proc</code> —
            the second truth level (Launched → Running):
            RuntimeAgentDetector observes the manager&apos;s authoritative
            StateFlow and publishes graded process evidence for live sessions
            classified KnownAgent; correlation ONLY inside the session&apos;s
            fork-proven tree (ppid-chain ∪ process-group under the recorded
            shellPid root — an unrelated same-name process can structurally
            never produce RUNNING); exact-token matching only (exe basename /
            argv[0] / the shebang contract&apos;s argv[1] script path — never
            substring); the four-state contract NOT_APPLICABLE / UNKNOWN /
            NOT_RUNNING / RUNNING (NOT_RUNNING is proven absence, never
            completion; UNKNOWN is never dressed as RUNNING); a 2-second scan
            tick that exists only while an eligible session exists; no output
            parsing, no custom-tool promotion, and no agent-status UI.
          </li>
          <li>
            <b>M7.2 P3c:</b> the runtime transition &amp; event engine — the
            third truth level: a pure reducer folding the detector&apos;s
            observations plus the manager&apos;s authoritative session state
            into the deduplicated typed event vocabulary (Launched /
            ConfirmedRunning with the exact pids + grade / NoLongerDetected —
            runtime disappearance only, never completion — / RuntimeUnknown /
            SessionEnded), staleness-safe, replay-free, zero own polling, no
            notifications — the substrate P4 subscribes to without ever
            seeing /proc or PID trees.
          </li>
          <li>
            <b>M7.2 P4:</b> notification consumption of the
            runtime event engine — the FIRST user-visible M7.2 phase. ONE
            consumer subscribed exactly once at Application start folds the
            events through ONE pure truth contract: confirmed running → the
            ongoing &quot;&lt;RegistryName&gt; is running&quot; surface;
            runtime unknown → honest uncertainty in place; no longer detected
            → the running claim withdrawn (never re-labeled); session ended →
            the one-shot factual &quot;Session ended / Terminal session N
            exited (code X)&quot; statement about the session&apos;s own
            waitpid status (exit 0 never success). Deterministic per-session
            notification identity (AGENT_RUNTIME_BASE + sessionId), defensive
            dedup (posted / everPosted / tombstones), one calm agent_runtime
            channel with setOnlyAlertOnce, the FGS retention notification
            untouched, zero new permission machinery, stale surfaces removed
            in-process (tombstones), across processes (the P1 startup sweep)
            and after process death (nothing restored, nothing faked) — and
            no shipped string can claim completed/success/finished/failed
            (pinned over the actual literals).
          </li>
          <li>
            <b>M7.2 P5:</b> notification interaction &amp;
            session context — the SECOND user-visible M7.2 phase: the honest
            surfaces became actionable. Tapping a running / runtime-unknown /
            eligible session-ended notification opens PocketShell into the
            terminal context of the session the notification already names.
            The route carries ONLY that authoritative session id; a pure
            routing model (zero imports) resolves it against the manager&apos;s
            live list — a listed id selects through the existing ViewModel
            seam; a stale id opens the app normally. A notification can never
            recreate a session, respawn a process, or fake a selection.
            PendingIntent identity stays deterministic (request code = the
            notification id). The shade&apos;s wording is byte-identical to
            P4 — zero new permissions, channels or manifest entries; the FGS
            untouched; no second lifecycle engine.
          </li>
          <li>
            <b>M7.2 P6 (this build):</b> runtime notification device-state
            refinement — the VERIFICATION phase with zero production delta:
            the P3c/P4 transitions were audited end-to-end and pinned as one
            continuous JVM story (11 new tests/variant — the
            running→unknown→running in-place cycle on ONE deterministic
            identity, cancellation-only withdrawals from BOTH prior states,
            the factual session-end exit statement, the duplicate-delivery
            storm, the flapping anti-accumulation case, exit 0 never
            success), with the device gate honestly split in TESTING §55
            (withdrawal / in-session reappearance / birth silence /
            exit-exit-3-tab-close facts / the §54 tap regression / FGS are
            reproducible; the mid-flight notification-level unknown cycle is
            honestly NOT device-reproducible without fabricated evidence).
            The rebuild is byte-identical to the P5 APK — no shipped byte
            changed; no detection power, debug button, or fake injection was
            added.
          </li>
        </ul>
      </div>

      <div className="card">
        <h2>Device gates (docs/TESTING.md — §55 is the M7.2 P6 gate)</h2>
        <ol className="steps">
          <li>
            <b>Confirm running (§55 Part A):</b> start a supported runtime —
            one running notification, no duplicates — and tap it: PocketShell
            opens with THAT session selected (the P5 baseline re-proven).
          </li>
          <li>
            <b>Withdrawal (§55 B1):</b> quit the agent INSIDE the live session
            (tab stays) — EXPECT: the running notification DISAPPEARS;
            nothing replaces it; no completed/success/finished/stopped
            wording anywhere (disappearance is not completion).
          </li>
          <li>
            <b>In-session reappearance (§55 B2):</b> relaunch the agent in the
            SAME session — EXPECT: the running notification returns as ONE
            in-place surface on the same identity, never a second card, never
            a storm.
          </li>
          <li>
            <b>Birth silence (§55 B3):</b> launch an agent and watch the shade
            before the first confirmation — EXPECT: NO uncontextualized
            &quot;runtime unknown&quot; notification at birth.
          </li>
          <li>
            <b>Mid-flight unknown (§55 B4) — NOT DEVICE-REPRODUCIBLE IN P6:</b>
            a visible running→unknown→running cycle needs a failed procfs
            scan or a matched-pid argv-shape mutation — neither can be
            produced on hardware without fabricating evidence (forbidden;
            no debug buttons, no fake injection). The arm stays JVM-pinned;
            B4&apos;s NOT-REPRODUCIBLE status is the EXPECTED outcome.
          </li>
          <li>
            <b>Session-end facts (§55 Part D):</b> type <code>exit</code> —
            EXPECT one factual &quot;Session ended / Terminal session N
            exited (code 0)&quot; (never success wording); repeat with
            <code>exit 3</code> — EXPECT &quot;exited (code 3)&quot;; close the
            tab instead — EXPECT no exit fact at all (SESSION_REMOVED has no
            status to state).
          </li>
          <li>
            <b>The P5 regression (§55 Part E):</b> re-run §54 steps 2, 3 and 6
            on this build — identical behavior (P6 added no production
            change, so a deviation means an environment problem).
          </li>
          <li>
            <b>FGS + the honesty sweeps (§55 Part F, §54 step 8, §53
            verbatim):</b> the Terminal sessions retention notification
            (id 1, terminal_sessions) unchanged; zero
            completed/success/finished/failed claims anywhere in the shade or
            the log. The §53/§54 gates remain in force below this one.
          </li>
        </ol>
        <p>
          <b>Honest visibility note:</b> P6 is a verification phase — the
          shipped bytes are identical to P5, so the shade and the taps look
          and behave exactly as §53/§54 recorded. §55 exists to prove the
          TRANSITION behaviors on hardware where they are naturally
          reproducible, and to honestly document the one arm that is not
          (B4) rather than fake it: the full matrix is JVM-pinned
          (AgentRuntimeNotificationTransitionMatrixTest, 11 tests/variant).
        </p>
      </div>

      <div className="card">
        <h2>Source (version control)</h2>
        <p>
          Source at the M7.2 P6 record tip 08cad7e (the verification chain
          2c20e38 tests → 797ece1 docs → the Task 45 worklog record, on top
          of the P5 interaction chain 4672e36 → 894d108 → d24bfde with its
          delivery ride-alongs 38512ea/1cbd287, the P4 notification chain
          bede512 → 49039e2 → 5b236df and its delivery
          chain 7444d28 → 2e8f0c6 → e385021 (+ the b825302 addendum), the
          P3c event-engine chain f782272 → 15d2ee4 → 66d91da, the P3b
          detection chain 5993fd3 → 3683fd1 → 0f091e7, the P3a chain f4c8afd →
          8ff2e12 → 5fe3602 → 6f15d09, the P2 lifecycle
          chain 0c9a792 → 577e1e9 → 133e656 → 3b144be, the P1 chain c6ced97 →
          0ed5da0 → fd54aa0 → a7c635e, the M7.1.1 fix 3cbfec2 and the M7.2 P0
          audit c8d0059/024cd28, with the full M7.1 release + P3 + P2.2 + P2.1
          + P2 + P1 + M7.0 chain below). The bundle is cut at the RECORD tip
          (the docs are final there, so the clean record-tip cut
          stands; the page re-pin and the delivery record ride after the cut
          by the disclosed P4-addendum convention and contain zero
          implementation delta; the APK was
          built from the identical app sources at that tip — and is
          byte-identical to the P5 audited build). The source zip is a
          git-archive snapshot of the SAME tip — by construction it contains
          NO APK and NO bundle (612 files). History note: this
          bundle continues the user-restored P7.1 delivery bundle (fb01540)
          through the M7.0 release chain (47bed42 → 709d126 → dd81bc8), the
          M7.1 phases (3abb2e8 → 1b15bde → e0a2471 → 6004805 → 93ee631), the
          M7.1 release closure (4e86e50 — Task 35), the M7.1.1 fix (3cbfec2 —
          Task 36), the M7.2 P0 audit (c8d0059 → 024cd28 — Task 38), the P1
          foundation (c6ced97 → 0ed5da0 → fd54aa0 → a7c635e — Task 39), the
          lifecycle engine (0c9a792 → 577e1e9 → 133e656 → 3b144be — Task 40),
          the P3a audit (f4c8afd → 8ff2e12 → 5fe3602 → 6f15d09 — Task 41),
          the P3b detector (5993fd3 → 3683fd1 → 0f091e7 — Task 42), the P3c
          event engine (f782272 → 15d2ee4 → 66d91da — Task 43), the P4
          notification consumer (bede512 → 49039e2 → 5b236df — Task 43), the
          P4 delivery chain (7444d28 → 2e8f0c6 → e385021 → b825302 — Task
          43-delivery), the two disclosed platform worklog snapshots (539c632,
          57f242d), the P5 interaction chain (4672e36 → 894d108 → d24bfde —
          Task 44) with its delivery ride-alongs (38512ea, 1cbd287), and the
          P6 verification chain (2c20e38 → 797ece1 → 08cad7e — Task 45).
        </p>
        <a className="btn secondary" href="/pocketshell-m7.2-p6.gitbundle">
          git bundle (full history, M7.2 P6 record tip)
        </a>
        <Sha text={HASHES.bundle} />
        <a className="btn secondary" href="/pocketshell-m7.2-p6-source.zip">
          source zip (git archive, P6 record tip — no binaries)
        </a>
        <Sha text={HASHES.sourceZip} />
        <a className="btn secondary" href="/pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz">
          glibc layer artifact (transparency copy, rev=2 unchanged)
        </a>
        <Sha text={HASHES.glibc} />
        <p>
          Restore: <code>git clone pocketshell-m7.2-p6.gitbundle pocketshell</code>.
          Includes <code>keystore/debug.keystore</code> — clones build APKs
          with the same signing identity (d96a6f66…8bf659, unchanged since
          v0.4.1).
        </p>
      </div>

      <footer>
        Milestones: M0–M1.3 terminal · M2.2 runtime install · M2.3 Linux shell
        (device-verified) · M2.4 package layer (device gate PASSED) · M2.5
        search-install · M2.6 real /proc + real apk · v0.6.2 sysdata repairs ·
        v0.7.0-m3.1 terminal redesign · m3.2 OS launcher · m3.3 flat
        workspace · m3.4 registry expansion · m3.5 command launch fix · m3.6
        procfs contract · m4.0 Phase 4 Companion · m4.0.1 startup hotfix ·
        m4.0.2 honest failure surfaces · m4.0.3 one keyboard for everything ·
        m4.0.4 pixels over promises · m4.0.5 the black page attacked at the
        root · m4.0.6 the page tells us everything · m4.0.7 the host was the
        bug · m4.0.8 the painted-but-black decode · m4.0.9 the rendering reset
        · m4.1.0 the native rebuild · m4.0.11 replace renderer only — the
        winner frozen · m4.0.12 companion finalization · m5.0.0 UI &amp;
        interaction polish · m5.0.1 the workspace bar · m5.1.0 audit-first
        performance · m6.0.0 universal runtime compatibility — real glibc
        inside Alpine, musl untouched, Cline runs · m6.0.1 install
        observability · m6.0.2 the actual install-path fix · m6.0.3 the doctor
        correctness gate · v0.10.0-m6.0.4: the adversarial closure audit ·
        m7.0.0 phases 1–7: storage abstraction, file explorer, file
        operations, Android storage bridge, quick text editor, Open Terminal
        Here · m7.0.0 phases 8–8.1: file search + multi-select (rebuilt after
        the reset, re-gated) · v0.11.0-m7.0.0: phase 9 — scrollable search
        results (deck-inset root cause), long-press actions, one close
        behavior, the M7 integration pass · m7.1 p1: home launchers —
        companion + CLI tool grids, hide/restore, custom tools, deterministic
        badges · m7.1 p2: launcher UI repair — the settings-row collapse
        root-caused, official bundled icons, Antigravity (agy) replaces Gemini
        CLI · m7.1 p2.1: Aider removed with no stale trace; nine marks
        re-rendered from the owner-supplied official brand SVGs (in-tree,
        offline, byte-reproducible) · m7.1 p2.2: icon colors follow the theme
        scheme (two-variant marks on the app&apos;s own plates), Companions
        one x-scroll row, tools two with scroll dots, the packages affordance
        aligned into the tools header · m7.1 p3: live external-keyboard
        detection — the on-screen deck hides itself on connect and returns on
        disconnect, one transient notice per transition, the On-screen
        keyboard Settings toggle, manual controls preserved, no new
        permissions · v0.11.1-m7.1.0: the OFFICIAL M7.1 RELEASE — five phases
        closed under one stamp, the clean-rerun release verification
        (734/734 JVM), M7.1 tagged and FROZEN · v0.11.2-m7.1.1: the M7.1.1
        external-keyboard fix — the real-device failure causes rebuilt (the
        gated canvas tap, the confirm deadline, the config-change
        cross-check, both-direction notices, the persistent preference), ONE
        authoritative visibility model, the §47 real-device gate ·
        m7.2 p0: the notification &amp; agent-activity architecture audit —
        what PocketShell can actually know (session-level lifecycle reliable,
        agent-level unknowable today, waiting-for-input has no real signal),
        the phase plan validated · m7.2 p1: the notification foundation —
        POST_NOTIFICATIONS asked exactly once per install on Android 13+ at
        the first session, the coordinator layer (event channel,
        deterministic ids, ledger, stale sweep, routing intents), tap routing
        on both activity paths, infrastructure only · m7.2 p2: the session
        lifecycle engine &amp; structured exit status — the typed
        STARTING→RUNNING→FINISHED machine on real signals only (fork callback
        + waitpid), exited(code)/signaled(signal) surfaced into the entry,
        SpawnOrigin/AgentHint launch identity at every spawn site, race-safe
        one-owner transitions, typed events + the derived read model, 810/810
        JVM with 0 skipped · m7.2 p3a: the trusted agent signal audit —
        LaunchIdentity (KnownAgent / KnownNonAgentTool / CustomOrUnknown) +
        the detection matrix, the truth boundary fixed (Launched ≠ Running ≠
        Completed) · m7.2 p3b: runtime agent detection via /proc — the
        second truth level: RUNNING / NOT_RUNNING / UNKNOWN / NOT_APPLICABLE
        on real process evidence only, correlation confined to the
        fork-proven session tree (ppid-chain ∪ process-group), exact-token
        matching (exe basename / argv[0] / shebang argv[1]), gated 2-second
        polling, no notifications, no completion claims, no output
        heuristics, no UI · m7.2 p3c: the runtime transition &amp; event
        engine — the third truth level: the deduplicated typed event
        vocabulary (Launched / ConfirmedRunning / NoLongerDetected /
        RuntimeUnknown / SessionEnded) on a replay-free stream, staleness
        rejection, zero own polling, no notifications yet · m7.2 p4:
        notification consumption of the runtime
        event engine — the FIRST user-visible M7.2 phase: one consumer
        subscribed once at Application start, one pure truth contract
        (running surface / honest uncertainty / withdrawal on
        no-longer-detected / the session&apos;s own factual exit statement),
        deterministic per-session identity, defensive dedup + tombstones,
        one calm agent_runtime channel, the FGS untouched, zero new
        permission machinery, three-way stale cleanup, 1932/1932 JVM with 0
        skipped, and NO notification can claim
        completed/success/finished/failed ·{" "}
        <b>m7.2 p5: notification interaction &amp; session
        context — the SECOND user-visible M7.2 phase: the honest surfaces
        became actionable — tapping a running / unknown / eligible
        session-ended notification opens the named session&apos;s terminal
        context through ONE pure routing model (live id → the existing
        ViewModel select seam; stale id → the app opens normally; a
        notification can never resurrect a process), the route carries ONLY
        the authoritative session id, PendingIntent request code = the
        notification id, the P4 wording byte-unchanged, zero new
        permissions/channels/manifest entries, the FGS untouched,
        1986/1986 JVM with 0 skipped — the §54 tap gate owns the device
        pass</b> ·{" "}
        <b>m7.2 p6 (this build): runtime notification device-state
        refinement — the VERIFICATION phase with ZERO production delta: the
        existing truthful transitions audited end-to-end and pinned as one
        continuous JVM story (11 new tests/variant — running→unknown→running
        in-place on ONE deterministic notification identity, cancellation-only
        withdrawals from both prior states, the factual session-end exit
        statement, the duplicate-delivery storm, the flapping
        anti-accumulation case, exit 0 never success), the device gate
        honestly split (§55: withdrawal / in-session reappearance / birth
        silence / exit-exit-3-tab-close facts / the §54 tap regression / FGS
        reproducible; the mid-flight notification-level unknown cycle
        honestly NOT device-reproducible without fabricated evidence), the
        rebuild byte-identical to the P5 audited APK (69ab4402… both — no
        shipped byte changed), no detector power, no debug buttons, no fake
        injection, 2008/2008 JVM with 0 skipped</b>. Correctness before
        cleverness. Visible UI before diagnostics. False confidence is
        failure.
      </footer>
    </main>
  );
}

const VERSION = "v0.11.2-m7.1.1-m72p3b";

// SHA pins — the M7.2 P3b (runtime agent detection via /proc) delivery pair.
// The bundle is cut at the P3b record tip (0f091e7); the APK is the audited
// phase build at the inherited stamp (versionCode 47, versionName
// 0.11.2-m7.1.1 — the -m72p3b suffix is filename-only), cert d96a6f66…8bf659,
// the unchanged 6-permission set. The glibc layer is UNCHANGED rev=2
// (byte-identical artifact ed82daa8…). P3b ships NO source archives — the git
// bundle is the source-of-truth artifact (the P2 zip/tgz stay withdrawn).
const HASHES = {
  apk: "fc1edb518bdb853f2d42a89d12d20209b36974578dd894e54941bd71804e704e",
  bundle: "e6d22571be67cebc4c0793af9278b3010fcdffa40b7e78e2b81ea0bdd3569c05",
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
          M7.2 P3b — RUNTIME AGENT DETECTION VIA /PROC: the second truth level
          (Launched → Running) built on real process evidence only{" "}
          <span className="badge">record tip 0f091e7 · vc47</span>
        </h2>
        <p>
          <b>
            Third M7.2 internal phase, Success A achieved: PocketShell can now
            correlate a supported launched agent session with REAL process
            evidence and truthfully expose <b>RUNNING</b> (the exact pids + the
            match grade), <b>NOT_RUNNING</b> (proven absence after proven
            presence — explicitly NOT completion/success/finished),{" "}
            <b>UNKNOWN</b> (never-observed, scan failure, or ambiguity — never
            dressed up as RUNNING) and <b>NOT_APPLICABLE</b> (plain shells,
            known non-agent tools, custom/unknown tools — the scanner never
            activates for them). <b>CORRELATION — the anti-false-positive
            core</b>: attribution runs ONLY inside the session&apos;s
            fork-proven tree — the manager-recorded shellPid (the real fork
            signal, on the main thread) is the root, and a process counts ONLY
            via ppid-chain descent OR process-group membership under that root
            (the JNI child calls setsid, so it IS the group leader; controlled
            experiment E4a proved orphans keep their group while losing their
            chain, E4b documented the double-escape blind spot, and E4c is the
            negative control: an unrelated same-name process satisfies NEITHER
            domain and can structurally never produce RUNNING). <b>MATCHING —
            exact-token only</b>: PROCFS_EXE (exe image basename) and
            PROCFS_CMDLINE (argv[0], or the kernel shebang contract&apos;s
            script path at argv[1] — experiment E3a proved interpreter-hosted
            CLIs carry the invoked name there while exe reads the interpreter);
            compile-time-forbidden substring matching (codex can never match
            my-codex-wrapper / codex-helper / something-codex), and the chain
            shell&apos;s single-element command line can never match (E2).{" "}
            <b>POLLING</b>: a 2-second tick that exists ONLY while a session
            with a KnownAgent launch identity exists (parking loop, zero
            scheduled work otherwise; no AlarmManager, no WakeLock, no
            WorkManager). <b>ONE lifecycle authority</b>: the detector is an
            observer-only evidence provider — it owns no transitions, mutates
            nothing, and the manager keeps every lifecycle truth.{" "}
            <b>VERIFICATION</b>: FULL JVM suite forced --rerun-tasks 1764/1764
            green (app 737×2 + terminal-emulator 145×2, 0 failures, 0 errors,
            0 skipped) including +49 new tests (pure decision-table + matching
            safety + correlation controls, and the structural integration
            pins); the five controlled /proc experiments (E1–E5) ran on the
            real guest chain: proot is the PTY&apos;s direct child AND the
            setsid session/group leader, the chain shell rides the command
            line as ONE argv element, and guest processes are visible over the
            real hidepid=2 procfs bind under the app UID. <b>HONEST SCOPE</b>:
            P3b posts NO notifications (the P1 sweep stays dormant), makes no
            completion/success/finished claims (unrepresentable in the state
            model), does no output parsing (no OSC 133, no prompt heuristics),
            never promotes custom tools, and ships NO agent-status UI — by
            design the only observable channel is the AgentRuntimeDetector
            logcat line, so the mandatory REAL-DEVICE gate is docs/TESTING.md
            §51 (12 steps over adb logcat + controlled launches, with
            Success-B parity: a persistent UNKNOWN is a valid recorded
            outcome, never a heuristic). The full investigation is
            docs/M7.2-P3B-RUNTIME-DETECTION.md.
          </b>
        </p>
        <a className="btn" href="/PocketShell-v0.11.2-m7.1.1-m72p3b-debug.apk">
          Download M7.2 P3b APK (debug, 30.8 MB)
        </a>
        <Sha text={HASHES.apk} />
        <a className="btn secondary" href="/pocketshell-m7.2-p3b.gitbundle">
          git bundle — full history M0 → P3b record tip (37.8 MB)
        </a>
        <Sha text={HASHES.bundle} />
        <p className="mono" style={{ border: "none", background: "transparent", padding: 0 }}>
          versionCode 47 / versionName 0.11.2-m7.1.1 (the M7.x phase-build
          precedent — phase builds ride the inherited stamp, the -m72p3b
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
          The M7.2 P2 served set (APK 4a144d23…, source zip 996148b0…, source
          tar.gz ea19e823…, bundle d821d94f…) is SUPERSEDED by this phase
          build: same stamp (vc47), same cert, the unchanged 6-permission set
          and glibc layer — the P3b build simply carries the lifecycle engine,
          the launch-identity layer and the runtime detector on top, and its
          bundle contains the entire P2 chain (the P2 record tip 3b144be is a
          direct ancestor of this record tip 0f091e7). The P2 bytes are no
          longer served (the upload/ insurance copies survive byte-exact).
          The M7.2 P3a checkpoint remains internal by design (bundle
          45bdecea…, upload/ copy, never served — P3a deliberately shipped no
          APK release). Earlier withdrawals stand: the M7.2 P1 APK (e63fb9b5…),
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
          versionCode 47 installs <b>in place over the M7.2 P2 phase build
          (47 — same versionCode, updated content, same pinned cert), the
          M7.2 P1 phase build (47), the M7.1.1 fix release (47), the M7.1
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
          launcher visibility/icon settings are untouched. M7.2 P3b is
          APP-side only: the layer marker and the glibc files stay
          byte-identical (rev=2, ed82daa8…), the notifications DataStore is
          unchanged, and the runtime detector adds no persistence at all —
          in-memory StateFlow observation, like the sessions themselves.
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
            <b>M7.2 P3b (this build):</b> runtime agent detection via{" "}
            <code>/proc</code> — the second truth level (Launched → Running):
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
            tick that exists only while an eligible session exists; no
            notifications, no output parsing, no custom-tool promotion, and
            NO agent-status UI — the logcat AgentRuntimeDetector line is the
            only observable channel by design.
          </li>
        </ul>
      </div>

      <div className="card">
        <h2>Device gates (docs/TESTING.md — §51 is the M7.2 P3b gate)</h2>
        <ol className="steps">
          <li>
            <b>Per-agent /proc shape table (§51):</b> launch each supported
            agent from its launcher, read the AgentRuntimeDetector logcat line
            (adb logcat) — the observed pid set, the match grade and the
            RUNNING state per agent, recorded against the expectations in
            docs/M7.2-P3B-RUNTIME-DETECTION.md.
          </li>
          <li>
            <b>Negative controls (§51):</b> a plain shell session and a known
            non-agent tool (e.g. htop) produce NO detector activity at all
            (NOT_APPLICABLE — the scanner never activates); an unrelated
            same-name process outside the session tree never flips a session
            to RUNNING.
          </li>
          <li>
            <b>Transitions (§51):</b> exit the agent → the tracked session
            reports NOT_RUNNING (proven absence — never
            &quot;completed&quot;); relaunch → RUNNING again; close the tab →
            the session and its evidence are removed; the scan tick parks
            (stops) when no eligible session exists.
          </li>
          <li>
            <b>Honesty check (§51):</b> if a supported agent&apos;s runtime
            shape cannot be observed on a given device, the recorded outcome is
            a persistent UNKNOWN — a valid engineering result, never a
            heuristic or a guess; NOTHING ever claims completed/success.
          </li>
          <li>
            <b>Regression (§49/§47/§48):</b> the P2 lifecycle parity sweep,
            the keyboard sweep and the notification-permission sweep still
            pass (P3b touches no lifecycle transitions, no keyboard code and
            no notification APIs).
          </li>
        </ol>
        <p>
          <b>Honest visibility note:</b> P3b intentionally ships NO
          agent-status UI — a user without adb cannot see the runtime state,
          and this page will not pretend otherwise. The §51 gate is written
          exactly for that: controlled launches + adb logcat. User-visible
          consumption of this evidence is the NEXT phase&apos;s job, on top of
          the graded seam this build delivers.
        </p>
      </div>

      <div className="card">
        <h2>Source (version control)</h2>
        <p>
          Source at the M7.2 P3b record tip 0f091e7 (the runtime-detection
          chain 5993fd3 → 3683fd1 → the Task 42 worklog record, on top of the
          P3a chain f4c8afd → 8ff2e12 → 5fe3602 → 6f15d09, the P2 lifecycle
          chain 0c9a792 → 577e1e9 → 133e656 → 3b144be, the P1 chain c6ced97 →
          0ed5da0 → fd54aa0 → a7c635e, the M7.1.1 fix 3cbfec2 and the M7.2 P0
          audit c8d0059/024cd28, with the full M7.1 release + P3 + P2.2 + P2.1
          + P2 + P1 + M7.0 chain below). P3b ships no source archives — the
          bundle is the source-of-truth artifact; full history rides in the
          git bundle — the complete milestone history (M0 → m7.2-p3b), all
          design contracts, the procfs contract, the P3a detection matrix, the
          P3b runtime-detection investigation, and the runtime documentation.
          Bundle main tip 0f091e7 = the tip the bundle is cut at; the APK was
          built from the identical app sources at that tip. History note: this
          bundle continues the user-restored P7.1 delivery bundle (fb01540)
          through the M7.0 release chain (47bed42 → 709d126 → dd81bc8), the
          M7.1 phases (3abb2e8 → 1b15bde → e0a2471 → 6004805 → 93ee631), the
          M7.1 release closure (4e86e50 — Task 35), the M7.1.1 fix (3cbfec2 —
          Task 36), the M7.2 P0 audit (c8d0059 → 024cd28 — Task 38), the P1
          foundation (c6ced97 → 0ed5da0 → fd54aa0 → a7c635e — Task 39), the
          lifecycle engine (0c9a792 → 577e1e9 → 133e656 → 3b144be — Task 40),
          the P3a audit (f4c8afd → 8ff2e12 → 5fe3602 → 6f15d09 — Task 41), and
          the P3b detector (5993fd3 → 3683fd1 → 0f091e7 — Task 42).
        </p>
        <a className="btn secondary" href="/pocketshell-m7.2-p3b.gitbundle">
          git bundle (full history, M7.2 P3b record tip)
        </a>
        <Sha text={HASHES.bundle} />
        <a className="btn secondary" href="/pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz">
          glibc layer artifact (transparency copy, rev=2 unchanged)
        </a>
        <Sha text={HASHES.glibc} />
        <p>
          Restore: <code>git clone pocketshell-m7.2-p3b.gitbundle pocketshell</code>.
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
        Completed) ·{" "}
        <b>m7.2 p3b (this build): runtime agent detection via /proc — the
        second truth level: RUNNING / NOT_RUNNING / UNKNOWN / NOT_APPLICABLE
        on real process evidence only, correlation confined to the
        fork-proven session tree (ppid-chain ∪ process-group), exact-token
        matching (exe basename / argv[0] / shebang argv[1]), gated 2-second
        polling, 1764/1764 JVM with 0 skipped, no notifications, no
        completion claims, no output heuristics, no UI — the §51 adb-logcat
        gate owns the device pass</b>. Correctness before cleverness. Visible
        UI before diagnostics. False confidence is failure.
      </footer>
    </main>
  );
}

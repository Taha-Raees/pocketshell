const VERSION = "v0.11.2-m7.1.1-m72p4";

// SHA pins — the M7.2 P4 (notification consumption of the runtime event
// engine) delivery pair. The bundle is cut at the P4 record tip (5b236df);
// the APK is the audited phase build at the inherited stamp (versionCode 47,
// versionName 0.11.2-m7.1.1 — the -m72p4 suffix is filename-only), cert
// d96a6f66…8bf659, the unchanged 6-permission set. The glibc layer is
// UNCHANGED rev=2 (byte-identical artifact ed82daa8…). P4 ships NO source
// archives — the git bundle is the source-of-truth artifact (the P2 zip/tgz
// stay withdrawn).
const HASHES = {
  apk: "ab73b24ab52e40662de45ad5c0c2aacb50494e1889d8428717266f080634442f",
  bundle: "9c6e4a1bcc258ca65fefcf8102ffd7095a71ef40e7dec19c99e6717fd8e2e5a7",
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
          M7.2 P4 — NOTIFICATION CONSUMPTION OF THE RUNTIME EVENT ENGINE: the
          first user-visible M7.2 phase — honest notifications, no completion
          claims, ever{" "}
          <span className="badge">record tip 5b236df · vc47</span>
        </h2>
        <p>
          <b>
            Sixth M7.2 phase, Success A achieved: the P3c deduplicated event
            stream now drives the Android notification shade through ONE
            consumer subscribed EXACTLY ONCE per process (Application scope —
            the replay-free stream&apos;s correctness requirement: events
            emitted before subscription are lost, and no session can spawn
            before Application.onCreate returns). <b>THE TRUTH CONTRACT</b>:
            confirmed running → an ongoing &quot;<b>&lt;RegistryName&gt; is
            running</b>&quot; surface (the launcher REGISTRY&apos;s own name —
            never invented); runtime unknown → the surface updates IN PLACE to
            honest uncertainty (&quot;runtime unknown — cannot be verified
            right now&quot;); no longer detected → the running claim is
            WITHDRAWN (disappearance is NOT completion — no replacement
            notification exists, nothing ever says finished); session ended →
            a one-shot factual &quot;<b>Session ended</b>&quot; / &quot;Terminal
            session N exited (code X) / was terminated by signal Y&quot;
            statement about the session&apos;s own waitpid status — the exit
            code preserved verbatim and uninterpreted, exit 0 NEVER worded as
            success. <b>IDENTITY &amp; DEDUP</b>: deterministic per-session
            notification ids (AGENT_RUNTIME_BASE + sessionId — never hash,
            never random, never a display string) so repeated state never
            reposts; a defensive notification-domain memory (posted /
            everPosted / tombstones) means identical surfaces are no-ops and
            ended sessions refuse every later event — without becoming a
            second event state machine. <b>CALM</b>: one new channel
            (agent_runtime, &quot;Agent activity&quot;) beside the untouched
            P1 channel; setOnlyAlertOnce — in-place updates never re-alert, so
            the 2-second polling tick upstream can never spam. <b>STALE
            SURFACES DIE THREE WAYS</b>: the tombstone in-process, the P1
            startup sweep across processes, and nothing restored after a
            process death — the architecture cannot prove a running agent it
            cannot see, so no stale &quot;Agent running&quot; survives a
            restart. <b>THE FGS IS UNTOUCHED</b>: the Terminal sessions
            retention notification (TerminalService, channel
            terminal_sessions, id 1) behaves exactly as before, structurally
            pinned. <b>ZERO new permission machinery</b>: the P1 gate (one
            controlled POST_NOTIFICATIONS ask per install at the first
            session) remains the only path; the manifest is unchanged.{" "}
            <b>VERIFICATION</b>: FULL JVM suite forced --rerun-tasks
            1932/1932 green (app 821×2 + terminal-emulator 145×2, 0 failures,
            0 errors, 0 skipped) including +41 new tests (the pure
            truth-contract matrix with the honesty sweep over a full session
            story, the structural pins — subscribe-once, one collection, no
            polling, no /proc, no session/detector references, the
            shipped-string honesty check — and the id-space tests).{" "}
            <b>HONEST SCOPE</b>: no notification can claim
            completed/success/finished/failed — pinned over the actual
            shipped string literals; &quot;no longer detected&quot; is only
            ever a withdrawal; exit code 0 is a session fact, never an agent
            verdict. The mandatory REAL-DEVICE gate is docs/TESTING.md §53
            (ten steps in the notification shade). Full contract:
            docs/M7.2-P4-NOTIFICATION-CONSUMPTION.md.
          </b>
        </p>
        <a className="btn" href="/PocketShell-v0.11.2-m7.1.1-m72p4-debug.apk">
          Download M7.2 P4 APK (debug, 30.9 MB)
        </a>
        <Sha text={HASHES.apk} />
        <a className="btn secondary" href="/pocketshell-m7.2-p4.gitbundle">
          git bundle — full history M0 → P4 record tip (37.9 MB)
        </a>
        <Sha text={HASHES.bundle} />
        <p className="mono" style={{ border: "none", background: "transparent", padding: 0 }}>
          versionCode 47 / versionName 0.11.2-m7.1.1 (the M7.x phase-build
          precedent — phase builds ride the inherited stamp, the -m72p4
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
          The M7.2 P3b served pair (APK fc1edb51…, bundle e6d22571…) is
          SUPERSEDED by this phase build: same stamp (vc47), same cert, the
          unchanged 6-permission set and glibc layer — the P4 build adds the
          notification consumer on top of the P3a identity layer, the P3b
          detector and the P3c event engine, and its bundle contains the
          entire chain (the P3b record tip 0f091e7 AND the P3c record tip
          66d91da are direct ancestors of this record tip 5b236df and of the
          delivery tip e385021). The P3b
          bytes are no longer served (the upload/ insurance copies survive
          byte-exact). The M7.2 P3c checkpoint stays bundle-only by design
          (bundle 3ca73dc9… — no user-visible change existed to serve); P4 IS
          user-visible, so it delivers BOTH the APK and the bundle. The M7.2
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
          versionCode 47 installs <b>in place over the M7.2 P3b phase build
          (47 — same versionCode, updated content, same pinned cert), the
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
          launcher visibility/icon settings are untouched. M7.2 P4 is
          APP-side only: the layer marker and the glibc files stay
          byte-identical (rev=2, ed82daa8…), and the notification consumer
          adds no persistence at all — an in-memory notification-domain
          memory beside the P1 posted-id ledger, like the process-scoped
          sessions themselves.
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
            <b>M7.2 P4 (this build):</b> notification consumption of the
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
        </ul>
      </div>

      <div className="card">
        <h2>Device gates (docs/TESTING.md — §53 is the M7.2 P4 gate)</h2>
        <ol className="steps">
          <li>
            <b>FGS regression (§53 step 1):</b> the Terminal sessions
            retention notification (channel terminal_sessions, id 1) appears
            with the first session and disappears with the last — exactly as
            before P4; the P4 surfaces neither merge with it nor replace it.
          </li>
          <li>
            <b>Confirmed running (§53 step 2):</b> launch a supported agent
            and leave the app — the &quot;Agent activity&quot; notification
            &quot;&lt;Agent&gt; is running&quot; appears ONLY after the
            detector&apos;s real confirmation (never at launch), once, as an
            ongoing surface naming its session.
          </li>
          <li>
            <b>No duplicates (§53 step 3):</b> keep the agent running over ≥
            3 detector ticks — the same single notification, no re-alert, no
            stacking (P3c dedups the transitions; the shade shows it).
          </li>
          <li>
            <b>Stops being detected (§53 step 4):</b> exit the agent while
            the session stays open — the running surface is REMOVED and
            NOTHING replaces it claiming finished/completed/success.
          </li>
          <li>
            <b>Factual exit wording (§53 step 5):</b> let the session end —
            one-shot &quot;Session ended / Terminal session N exited (code
            0)&quot;, and with an abnormal end the code or signal preserved
            verbatim; NEVER any &quot;agent completed/finished/succeeded&quot;
            wording.
          </li>
          <li>
            <b>Honest uncertainty + permission arms + restart + isolation
            (§53 steps 6–9):</b> the surface updates to &quot;runtime
            unknown&quot; in place when the evidence blurs; denial never
            crashes and never re-asks; force-stop + relaunch leaves NO stale
            &quot;Agent running&quot; (the sweep cancels the ledger, nothing
            is restored); 2–3 sessions stay isolated per session id.
          </li>
          <li>
            <b>The honesty sweep (§53 step 10):</b> across all steps, zero
            completed/success/finished/failed claims anywhere in the shade or
            the log — a notification that overstates the evidence is a FAIL
            even when it looks nice. The §51 logcat gate (per-agent /proc
            shapes) remains the runtime-layer pass below this one.
          </li>
        </ol>
        <p>
          <b>Honest visibility note:</b> P4 IS user-visible — the notification
          shade now carries the runtime story the P2/P3 architecture can
          actually prove, and nothing more. The §53 gate is written exactly
          for that: the shade must be verified on hardware before the
          phase&apos;s results are trusted there.
        </p>
      </div>

      <div className="card">
        <h2>Source (version control)</h2>
        <p>
          Source at the M7.2 P4 record tip 5b236df (the notification-consumption
          chain bede512 → 49039e2 → the Task 43 worklog record, on top of the
          P3c event-engine chain f782272 → 15d2ee4 → 66d91da, the P3b
          detection chain 5993fd3 → 3683fd1 → 0f091e7, the P3a chain f4c8afd →
          8ff2e12 → 5fe3602 → 6f15d09, the P2 lifecycle
          chain 0c9a792 → 577e1e9 → 133e656 → 3b144be, the P1 chain c6ced97 →
          0ed5da0 → fd54aa0 → a7c635e, the M7.1.1 fix 3cbfec2 and the M7.2 P0
          audit c8d0059/024cd28, with the full M7.1 release + P3 + P2.2 + P2.1
          + P2 + P1 + M7.0 chain below). P4 ships no source archives — the
          bundle is the source-of-truth artifact; full history rides in the
          git bundle — the complete milestone history (M0 → m7.2-p4), all
          design contracts, the procfs contract, the P3a detection matrix, the
          P3b runtime-detection investigation, the P3c event-engine contract,
          the P4 notification contract, and the runtime documentation.
          Bundle main tip e385021 = the tip the bundle is cut at (the P4
          DELIVERY tip: the worklog record 5b236df + the delivery record
          7444d28 + the mandated page re-pin 2e8f0c6 + the measured-counts
          doc correction e385021 — one commit beyond the record tip taken
          deliberately so the bundle's own contract doc carries the exact
          verification numbers; the APK was
          built from the identical app sources at that tip). History note: this
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
          notification consumer (bede512 → 49039e2 → 5b236df — Task 43), and
          the delivery chain (7444d28 → 2e8f0c6 → e385021 — Task
          43-delivery).
        </p>
        <a className="btn secondary" href="/pocketshell-m7.2-p4.gitbundle">
          git bundle (full history, M7.2 P4 delivery tip)
        </a>
        <Sha text={HASHES.bundle} />
        <a className="btn secondary" href="/pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz">
          glibc layer artifact (transparency copy, rev=2 unchanged)
        </a>
        <Sha text={HASHES.glibc} />
        <p>
          Restore: <code>git clone pocketshell-m7.2-p4.gitbundle pocketshell</code>.
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
        rejection, zero own polling, no notifications yet ·{" "}
        <b>m7.2 p4 (this build): notification consumption of the runtime
        event engine — the FIRST user-visible M7.2 phase: one consumer
        subscribed once at Application start, one pure truth contract
        (running surface / honest uncertainty / withdrawal on
        no-longer-detected / the session&apos;s own factual exit statement),
        deterministic per-session identity, defensive dedup + tombstones,
        one calm agent_runtime channel, the FGS untouched, zero new
        permission machinery, three-way stale cleanup, 1932/1932 JVM with 0
        skipped, and NO notification can claim
        completed/success/finished/failed — the §53 shade gate owns the
        device pass</b>. Correctness before cleverness. Visible UI before
        diagnostics. False confidence is failure.
      </footer>
    </main>
  );
}

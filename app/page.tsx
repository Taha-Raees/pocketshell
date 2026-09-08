const VERSION = "v0.11.2-m7.1.1-m72p2";

// SHA pins — the M7.2 P2 (session lifecycle engine & structured exit status)
// delivery. The payload cutter stages from the pinned record tip (3b144be)
// with zeroed mtimes; the embedded git bundle's pack bytes are not
// re-cut-stable, so these pins refer to the ONE delivered cut. Semantic
// pins: versionCode 47, versionName 0.11.2-m7.1.1 (the M7.x phase-build
// precedent — the -m72p2 suffix is filename-only), cert d96a6f66…8bf659,
// embedded layer asset 898131ff… / 17,920,000 B == GlibcRuntimePin. The
// glibc layer is UNCHANGED rev=2 (byte-identical artifact ed82daa8…). The
// M7.2 P1 APK (e63fb9b5…) is superseded by this phase build and withdrawn
// below; its content and history ride in the bundle.
const HASHES = {
  apk: "4a144d23710e7cd3893d1cdb59b0e3892a7b585ba1d4ed9015a7988360d2dfce",
  zip: "996148b0fe4573ef14f44b43880efa8b2d02a6304fa2438e4d23773c7c78714e",
  tgz: "ea19e8232d9c4294046f730843e763c2f7e05bb077ff2dafee817eda3d326300",
  bundle: "d821d94fe21181574a706ceba88368e4defd336f01ec340393af947b91b0c548",
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
          M7.2 P2 — SESSION LIFECYCLE ENGINE &amp; STRUCTURED EXIT STATUS: the
          typed lifecycle model on real signals only{" "}
          <span className="badge">record tip 3b144be · vc47</span>
        </h2>
        <p>
          <b>
            Second M7.2 production code, internal infrastructure only, built
            exactly on the P0 audit&apos;s provable state machine
            (docs/M7.2-P0-AUDIT.md §10.3). <b>The lifecycle model</b>: every
            session entry now carries a typed SessionLifecycleState — STARTING
            (the entry exists, the PTY child is NOT forked yet: the audit&apos;s
            lazy-fork fact, observed via the previously-discarded
            setTerminalShellPid callback), RUNNING (the real fork signal),
            FINISHED (the real waitpid delivery) — with REMOVED modeled by tab
            removal plus a typed event, never a stored flag; the private
            constructor makes invalid combinations unrepresentable (FINISHED
            always carries its exit status; STARTING/RUNNING never do), and the
            stored isFinished boolean is retired — the getter is DERIVED, no
            second truth inside the record. <b>Structured exit status</b>:
            ExitStatus.Exited(code) / ExitStatus.Signaled(signal) — a faithful
            mapping of exactly what JNI.waitFor/waitpid provides (positive =
            WEXITSTATUS, negative = negated WTERMSIG); nothing invented, no
            polling, no &quot;unknown&quot; variant. <b>One owner, race-safe
            transitions</b>: TerminalSessionManager stays the single
            authoritative lifecycle owner; duplicate or out-of-order callbacks
            are REJECTED with a logged reason and can never corrupt a recorded
            status; a finish delivery for a removed session is logged, never
            swallowed; the close path is guarded against the upstream kill(0)
            hazard (isRunning() is true for a never-forked mShellPid==0, where
            SIGKILL would hit the caller&apos;s whole process group).{" "}
            <b>Structured launch identity</b>: every spawn site passes its
            SpawnOrigin (Shell / LinuxShell / FilesTerminal / CommandApp(id) /
            CatalogApp(id) / CustomTool(id)); the three named-launcher paths
            carry AgentHint(displayName, command, matchedBy=LAUNCH_METADATA) —
            real spawn-time metadata only, never a process claim. <b>Typed
            events + the derived read model</b>: the manager emits
            SessionLifecycleEvent (Started/Finished/Removed, full identity
            block) only at the mutation sites, and AgentActivityRepository is
            the ONE derived projection — it stores nothing, decides nothing,
            touches no notifications. <b>VERIFICATION</b>: FULL JVM suite
            forced --rerun-tasks 810/810 green (app 665 + terminal-emulator
            145, 0 failures, 0 errors, 0 SKIPPED) including 30 new lifecycle
            tests (the pure transition truth table + 14 structural integration
            pins) and the verification-honesty fix: the P1 structural pins had
            been silently skipping under the module-dir runner and now RUN and
            pass; a fresh APK audited on the exact bytes: aapt2 badging
            versionCode=&apos;47&apos; versionName=&apos;0.11.2-m7.1.1&apos;
            targetSdk 28, the UNCHANGED 6-permission merged set, the six new
            P2 dex symbols, the pinned cert d96a6f66…8bf659. <b>HONEST
            SCOPE</b>: P2 posts NO notifications (the P1 sweep stays dormant),
            no agent detection, no completion/waiting claims, no /proc
            scanning, no OSC 133, no persistence (lifecycle state is
            in-memory, process-scoped like the sessions themselves), and NO
            user-visible UI change. The mandatory REAL-DEVICE gate is
            docs/TESTING.md §49 (10 steps — a parity regression pass over the
            five spawn paths, natural/non-zero exits, tab-close, FGS policy,
            process death and the §47/§48 sweeps) — a green build never claims
            the hardware pass.
          </b>
        </p>
        <a className="btn" href="/PocketShell-v0.11.2-m7.1.1-m72p2-debug.apk">
          Download M7.2 P2 APK (debug, 30.8 MB)
        </a>
        <Sha text={HASHES.apk} />
        <p className="mono" style={{ border: "none", background: "transparent", padding: 0 }}>
          versionCode 47 / versionName 0.11.2-m7.1.1 (the M7.x phase-build
          precedent — phase builds ride the inherited stamp, the -m72p2
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
          The M7.2 P1 APK (e63fb9b5…, 30,473,480 B, vc47 / 0.11.2-m7.1.1) and
          its source set are SUPERSEDED by this phase build: same stamp
          (vc47), same cert, the notification foundation unchanged — the P2
          build simply carries the lifecycle engine on top, and its source
          archives are cut at a tip that contains the entire P1 chain. The P1
          bytes are no longer served (the upload/ insurance copies survive
          byte-exact); the complete history rides in the bundle below (the P1
          record tip a7c635e is a direct ancestor of this record tip
          3b144be). Earlier withdrawals stand: the M7.1.1 fix APK (5d876141…),
          the M7.1 release APK (2d298c85…), the M7.1 P3 APK (46fb0d8b…), the
          M7.1 P2.2 APK (7e0e99a9…), the M7.1 P2.1 APK (97c04120…), the M7.1
          P2 APK (ae6f6445…), the M7.1 P1 APK (4d7349f7…), the M7.0 release
          APK (8826d30d…), the m7p8.1 (vc44) and the reset-lost
          m7p8/m7p7.1/m7p6/m6.0.4 sets — their content and history are fully
          contained in this bundle, as is the M7.2 P0 audit
          (docs/M7.2-P0-AUDIT.md, the P0 bundle 0d8c5eb0… was a docs-only
          direct-URL artifact). The glibc layer artifact (rev=2) is
          byte-identical and served below.
        </p>
      </div>

      <div className="card">
        <h2>Update — no uninstall, no runtime reinstall</h2>
        <p>
          versionCode 47 installs <b>in place over the M7.2 P1 phase build
          (47 — same versionCode, updated content, same pinned cert), the
          M7.1.1 fix release (47), the M7.1 release (46), the
          M7.1 phase builds (all
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
          launcher visibility/icon settings are untouched. M7.2 P2 is APP-side
          only: the layer marker and the glibc files stay byte-identical
          (rev=2, ed82daa8…), the notifications DataStore is unchanged, and
          the lifecycle engine adds no persistence at all — in-memory, like
          the sessions themselves.
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
            <b>M7.2 P2 (this build):</b> the session lifecycle engine &amp;
            structured exit status — the typed STARTING→RUNNING→FINISHED
            machine (REMOVED as removal-plus-event, never a flag), the
            waitpid exit status surfaced as exited(code)/signaled(signal),
            structured SpawnOrigin/AgentHint identity at every spawn site,
            race-safe one-owner transitions (duplicate-callback idempotency,
            the kill(0) close guard), typed lifecycle events and the derived
            AgentActivityRepository — in-memory only, no notifications, no
            agent claims, no UI change.
          </li>
        </ul>
      </div>

      <div className="card">
        <h2>Device gates (docs/TESTING.md — §49 is the M7.2 P2 gate)</h2>
        <ol className="steps">
          <li>
            <b>Spawn matrix + exits (§49):</b> all five launch paths still
            spawn real sessions; a natural <code>exit</code> keeps the tab
            with its (exited) marker, <code>exit 3</code> shows
            [Process completed (code 3)] — the same waitpid delivery the
            engine now records internally.
          </li>
          <li>
            <b>Close + FGS (§49):</b> closing a busy tab removes it without a
            crash and leaves other sessions working; the retention
            notification appears iff ≥ 1 session exists and clears when the
            last tab goes — the FGS policy unchanged.
          </li>
          <li>
            <b>Process death + hygiene (§49):</b> kill from recents with
            sessions open → relaunch starts clean, no stale notifications;
            three sessions, close the middle one, the others unaffected.
          </li>
          <li>
            <b>Honesty + parity (§49):</b> nothing claims an agent is
            running/completed/waiting; no notification beyond the P1 FGS one;
            side-by-side with the m72p1 build there is NO user-visible
            difference — any visible difference is a defect, not a feature.
          </li>
          <li>
            <b>Keyboard + notification regression (§47/§48):</b> the
            external-keyboard sweep and the P1 permission/anti-nag/channel
            checks still pass (P2 touches neither subsystem).
          </li>
        </ol>
        <p>
          The M6 runtime gates (§33) keep their section in docs/TESTING.md;
          the runtime-tests tarball is not re-served after the reset — it is
          re-cut from the bundle sources at the next runtime gate.
        </p>
      </div>

      <div className="card">
        <h2>Source (version control)</h2>
        <p>
          Source at the M7.2 P2 record tip 3b144be (the lifecycle chain
          0c9a792 → 577e1e9 → 133e656 → the Task 40 worklog record, on top of
          the M7.2 P1 chain c6ced97 → 0ed5da0 → fd54aa0 → a7c635e, the
          M7.1.1 fix 3cbfec2 and the M7.2 P0 audit c8d0059/024cd28, with the
          full M7.1 release + P3 + P2.2 + P2.1 + P2 + P1 + M7.0 chain below).
          The zip intentionally contains no dotfiles; full history rides in
          the git bundle — the complete milestone history (M0 → m7.2-p2), all
          design contracts, the procfs contract, and the runtime
          documentation. Bundle main tip 3b144be = the tip the source
          archives are cut at; the APK was built from the identical app
          sources (the app tree is unchanged since 0c9a792 — the commits
          after it touch only tests, docs, scripts and the worklog). History
          note: this bundle continues the user-restored P7.1 delivery bundle
          (fb01540) through the M7.0 release chain (47bed42 → 709d126 →
          dd81bc8), the M7.1 phases (3abb2e8 → 1b15bde → e0a2471 → 6004805 →
          93ee631), the M7.1 release closure (4e86e50 — Task 35), the M7.1.1
          fix (3cbfec2 — Task 36), the M7.2 P0 audit (c8d0059 → 024cd28 —
          Task 38), and the M7.2 P1 foundation (c6ced97 → 0ed5da0 → fd54aa0 →
          a7c635e — Task 39).
        </p>
        <a className="btn secondary" href="/PocketShell-v0.11.2-m7.1.1-m72p2-source.zip">
          source.zip (M7.2 P2 record tip)
        </a>
        <a className="btn secondary" href="/PocketShell-v0.11.2-m7.1.1-m72p2-source.tar.gz">
          source.tar.gz (M7.2 P2 record tip)
        </a>
        <a className="btn secondary" href="/pocketshell-m7.2-p2.gitbundle">
          git bundle (full history, M7.2 P2 record tip)
        </a>
        <Sha text={HASHES.zip} />
        <Sha text={HASHES.tgz} />
        <Sha text={HASHES.bundle} />
        <a className="btn secondary" href="/pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz">
          glibc layer artifact (transparency copy, rev=2 unchanged)
        </a>
        <Sha text={HASHES.glibc} />
        <p>
          Restore: <code>git clone pocketshell-m7.2-p2.gitbundle pocketshell</code>.
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
        on both activity paths, infrastructure only ·{" "}
        <b>m7.2 p2 (this build): the session lifecycle engine &amp; structured
        exit status — the typed STARTING→RUNNING→FINISHED machine on real
        signals only (fork callback + waitpid), exited(code)/signaled(signal)
        surfaced into the entry, SpawnOrigin/AgentHint launch identity at
        every spawn site, race-safe one-owner transitions, typed events + the
        derived read model, 810/810 JVM with 0 skipped — no notifications, no
        agent claims, no heuristics, the §49 parity gate mandatory</b>.
        Correctness before cleverness. Visible UI before diagnostics.
      </footer>
    </main>
  );
}

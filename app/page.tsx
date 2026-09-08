const VERSION = "v0.11.2-m7.1.1-m72p1";

// SHA pins — the M7.2 P1 (notification foundation) delivery. The payload
// cutter stages from the pinned record tip (a7c635e) with zeroed mtimes; the
// embedded git bundle's pack bytes are not re-cut-stable, so these pins refer
// to the ONE delivered cut. Semantic pins: versionCode 47, versionName
// 0.11.2-m7.1.1 (the M7.x phase-build precedent — the -m72p1 suffix is
// filename-only), cert d96a6f66…8bf659, embedded layer asset 898131ff… /
// 17,920,000 B == GlibcRuntimePin. The glibc layer is UNCHANGED rev=2
// (byte-identical artifact ed82daa8…). The M7.1.1 fix APK (5d876141…) is
// superseded by this phase build and withdrawn below; its content and
// history ride in the bundle.
const HASHES = {
  apk: "e63fb9b539f4b786307f6597b3a54427c7b8b63bedd1a081f50880719d18bf5b",
  zip: "b4c9eb53d66e847a751d447c9c3dbc223a7f0b756f096e71965b998e270f8eb1",
  tgz: "147441d49354021cd261e57ac7d6098b7a127f0e96bd7a35731dc53d9cdf659b",
  bundle: "23c9117e4586e034abe59673ea90566982a171fe7568c0f004305500b4a722f9",
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
          M7.2 P1 — NOTIFICATION FOUNDATION: the declared-but-never-requested
          permission is finally asked, the coordinator layer exists{" "}
          <span className="badge">record tip a7c635e · vc47</span>
        </h2>
        <p>
          <b>
            First M7.2 production code, infrastructure only, per the approved
            P0 audit (docs/M7.2-P0-AUDIT.md). <b>The permission fix</b>:
            POST_NOTIFICATIONS was declared in the manifest since M2.4 and
            never requested at runtime — on Android 13+ every notification
            (including the session-retention foreground-service notification)
            was invisible until the user found Settings on their own. Now the
            native dialog is shown EXACTLY ONCE per install, fired when the
            FIRST terminal session exists (the moment notifications become
            meaningful), with the request flag persisted to the new
            notifications DataStore BEFORE the dialog opens — so rotation,
            activity recreation and process death mid-dialog can never re-ask
            — and with a denial (or the system&apos;s own implicit prompt
            being denied) respected forever after: never nagged, never
            blocked. <b>The coordinator</b> (app.pocketshell.notifications/):
            ONE output/integration layer owning the new session_events channel
            beside the untouched terminal_sessions FGS channel, deterministic
            notification ids (EVENT_BASE + sessionId, refusing silent
            wraparound), FLAG_IMMUTABLE content intents carrying the routing
            extra, a DataStore ledger of posted ids, and the startup
            stale-notification sweep that cancels exactly what this
            coordinator posted and never the FGS notification (id 1) or
            anything else. <b>The routing</b>: MainActivity now processes
            notification intents on BOTH paths — cold start (onCreate) and
            the existing-instance singleTask path (onNewIntent, unhandled
            since forever per the P0 audit §8.4 finding) — through one
            exhaustive route handler. <b>VERIFICATION</b>: FULL JVM suite
            forced --rerun-tasks 780/780 green (app 635 + terminal-emulator
            145, 0 failures, 0 errors) including 34 new notifications tests
            (the complete anti-nag truth table, identity collision/FGS-space
            proofs, the routing parser, 14 structural integration pins); a
            fresh APK audited on the exact bytes: aapt2 badging
            versionCode=&apos;47&apos; versionName=&apos;0.11.2-m7.1.1&apos;
            targetSdk 28, the UNCHANGED 6-permission merged set, 26 launcher
            icon assets, the seven notifications dex symbols, the pinned cert
            d96a6f66…8bf659. <b>HONEST SCOPE</b>: P1 posts NO production event
            notifications — no agent detection, no completion claims, no
            waiting-for-input heuristics, no /proc scanning, no OSC 133 (P2+
            scope). The mandatory REAL-DEVICE gate is docs/TESTING.md §48
            (12 steps: no-ask-before-first-session, grant/deny, anti-nag
            relaunches, dual channels, warm+cold taps, pre-13, the §47
            keyboard regression) — a green build never claims the hardware
            pass.
          </b>
        </p>
        <a className="btn" href="/PocketShell-v0.11.2-m7.1.1-m72p1-debug.apk">
          Download M7.2 P1 APK (debug, 30.5 MB)
        </a>
        <Sha text={HASHES.apk} />
        <p className="mono" style={{ border: "none", background: "transparent", padding: 0 }}>
          versionCode 47 / versionName 0.11.2-m7.1.1 (the M7.x phase-build
          precedent — phase builds ride the inherited stamp, the -m72p1
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
          The M7.1.1 fix APK (5d876141…, 30,451,377 B, vc47 / 0.11.2-m7.1.1)
          and its source set are SUPERSEDED by this phase build: same stamp
          (vc47), same cert, the keyboard system unchanged — the P1 build
          simply carries the notification foundation on top, and its source
          archives are cut at a tip that contains the entire M7.1.1 chain.
          The M7.1.1 bytes are no longer served (the upload/ insurance copies
          survive byte-exact); the complete history rides in the bundle below
          (the fix tip 3cbfec2 is a direct ancestor of this record tip
          a7c635e). Earlier withdrawals stand: the M7.1 release APK
          (2d298c85…), the M7.1 P3 APK (46fb0d8b…), the M7.1 P2.2 APK
          (7e0e99a9…), the M7.1 P2.1 APK (97c04120…), the M7.1 P2 APK
          (ae6f6445…), the M7.1 P1 APK (4d7349f7…), the M7.0 release APK
          (8826d30d…), the m7p8.1 (vc44) and the reset-lost
          m7p8/m7p7.1/m7p6/m6.0.4 sets — their content and history are fully
          contained in this bundle, as is the M7.2 P0 audit
          (docs/M7.2-P0-AUDIT.md, the P0 bundle 17475b7f… was a docs-only
          direct-URL artifact). The glibc layer artifact (rev=2) is
          byte-identical and served below.
        </p>
      </div>

      <div className="card">
        <h2>Update — no uninstall, no runtime reinstall</h2>
        <p>
          versionCode 47 installs <b>in place over the M7.1.1 fix release
          (47 — same versionCode, updated content, same pinned cert), the
          M7.1 release (46), the
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
          launcher visibility/icon settings are untouched. M7.2 P1 is APP-side
          only: the layer marker and the glibc files stay byte-identical
          (rev=2, ed82daa8…), and the notifications DataStore starts empty —
          the first session after the update triggers the one-time Android 13+
          permission dialog.
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
            <b>M7.2 P1 (this build):</b> the notification foundation — the
            POST_NOTIFICATIONS runtime request exactly once per install on
            Android 13+ (first-session trigger, flag-before-dialog anti-nag,
            system-prompt denials respected, denial never blocks the
            terminal), the notifications/ coordinator layer (session_events
            channel, deterministic ids, posted-id ledger, startup stale
            sweep, FLAG_IMMUTABLE routing intents), and intent routing on
            both activity paths — infrastructure only, no event notifications
            posted yet, no agent detection, no heuristics.
          </li>
        </ul>
      </div>

      <div className="card">
        <h2>Device gates (docs/TESTING.md — §48 is the M7.2 P1 gate)</h2>
        <ol className="steps">
          <li>
            <b>Permission — the once-per-install ask (§48):</b> fresh install
            on Android 13+ → open the app → NO dialog on Home; create the
            first session → the native POST_NOTIFICATIONS dialog appears
            exactly once; grant → background the app → the session-retention
            FGS notification is finally VISIBLE in the shade.
          </li>
          <li>
            <b>Deny + anti-nag (§48):</b> deny the dialog → no crash, the
            terminal keeps working, the FGS service still keeps the session
            alive (only the notification is hidden); relaunch, create more
            sessions, kill and restart the process — the dialog NEVER
            re-appears.
          </li>
          <li>
            <b>Channels + taps (§48):</b> dumpsys lists BOTH channels
            (terminal_sessions + session_events, created exactly once each);
            tapping the FGS notification works warm (app open → onNewIntent
            path) and cold (swiped away → cold start); no notification ever
            claims an agent or command completed.
          </li>
          <li>
            <b>Keyboard regression (§47):</b> the M7.1.1 external-keyboard
            sweep still passes — USB/BT flows, the canvas-tap-stays-hidden
            rule, the settings matrix (P1 touches no keyboard code).
          </li>
          <li>
            <b>Home + launchers + terminal (§41-§45):</b> launchers render and
            launch, sessions spawn/close/background as before — the FGS
            lifecycle policy (runs iff ≥ 1 session) is unchanged.
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
          Source at the M7.2 P1 record tip a7c635e (the notification
          foundation chain c6ced97 → 0ed5da0 → fd54aa0 → the Task 39 worklog
          record, on top of the M7.1.1 fix 3cbfec2 and the M7.2 P0 audit
          c8d0059/024cd28, with the full M7.1 release + P3 + P2.2 + P2.1 + P2
          + P1 + M7.0 chain below). The zip intentionally contains no
          dotfiles; full history rides in the git bundle — the complete
          milestone history (M0 → m7.2-p1), all design contracts, the procfs
          contract, and the runtime documentation. Bundle main tip a7c635e =
          the tip the source archives are cut at; the APK was built from the
          identical app sources (the app tree is unchanged since 0ed5da0 —
          the two commits after it touch only docs, scripts and the worklog).
          History note: this bundle continues the user-restored P7.1 delivery
          bundle (fb01540) through the M7.0 release chain (47bed42 → 709d126
          → dd81bc8), the M7.1 phases (3abb2e8 → 1b15bde → e0a2471 → 6004805
          → 93ee631), the M7.1 release closure (4e86e50 — Task 35), the
          M7.1.1 fix (3cbfec2 — Task 36), and the M7.2 P0 audit
          (c8d0059 → 024cd28 — Task 38).
        </p>
        <a className="btn secondary" href="/PocketShell-v0.11.2-m7.1.1-m72p1-source.zip">
          source.zip (M7.2 P1 record tip)
        </a>
        <a className="btn secondary" href="/PocketShell-v0.11.2-m7.1.1-m72p1-source.tar.gz">
          source.tar.gz (M7.2 P1 record tip)
        </a>
        <a className="btn secondary" href="/pocketshell-m7.2-p1.gitbundle">
          git bundle (full history, M7.2 P1 record tip)
        </a>
        <Sha text={HASHES.zip} />
        <Sha text={HASHES.tgz} />
        <Sha text={HASHES.bundle} />
        <a className="btn secondary" href="/pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz">
          glibc layer artifact (transparency copy, rev=2 unchanged)
        </a>
        <Sha text={HASHES.glibc} />
        <p>
          Restore: <code>git clone pocketshell-m7.2-p1.gitbundle pocketshell</code>.
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
        the phase plan validated ·{" "}
        <b>m7.2 p1 (this build): the notification foundation — POST_NOTIFICATIONS
        asked exactly once per install on Android 13+ at the first session,
        the coordinator layer (event channel, deterministic ids, ledger,
        stale sweep, routing intents), tap routing on both activity paths,
        780/780 JVM, infrastructure only — no agent claims, no heuristics,
        the §48 real-device gate mandatory</b>.
        Correctness before cleverness. Visible UI before diagnostics.
      </footer>
    </main>
  );
}

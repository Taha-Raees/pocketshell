const VERSION = "v0.11.0-m7.0.0-m7.1p3";

// SHA pins — the M7.1 PHASE 3 delivery. The payload cutter stages from the
// pinned phase tip (93ee631) with zeroed mtimes; the embedded git bundle's
// pack bytes are not re-cut-stable, so these pins refer to the ONE delivered
// cut. Semantic pins: versionCode 45, versionName 0.11.0-m7.0.0 (P3 does
// NOT bump the version), cert d96a6f66…8bf659, embedded layer asset
// 898131ff… /17,920,000 B == GlibcRuntimePin. The glibc layer is UNCHANGED
// rev=2 (byte-identical artifact ed82daa8…). The M7.1 P2.2 APK
// (7e0e99a9…) is superseded by this build (same version stamp, new content)
// and withdrawn below; its history rides in the bundle.
const HASHES = {
  apk: "46fb0d8b3905f2e96c281e38a3805c74a0716fd4618261268b9e5ecae0477989",
  zip: "d71cc7a2743340dfa059af76eecd559089125c53a96129816279ed2f8ae19bdb",
  tgz: "8bc28a9273e03c918723224e1cb07bae85373b490d39e6dae893db28f5193d8d",
  bundle: "8b0544b5fe1b6c2231f7ca5b2f67679a6ae821c85399a1ba05cdd5489693ea9c",
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
          M7.1 Phase 3: live external-keyboard detection + automatic
          on-screen keyboard control{" "}
          <span className="badge">phase tip 93ee631 · vc45</span>
        </h2>
        <p>
          <b>
            <b>PLUG IN A KEYBOARD — POCKETSHELL GETS OUT OF THE WAY.</b>{" "}
            When a physical external keyboard (USB, Bluetooth, dock, DeX)
            connects while the app is running, the shared on-screen deck
            hides itself automatically and the hardware keyboard types
            straight into the terminal — and when it disconnects, the deck
            returns exactly as the user left it. No restart, no replug, no
            manual refresh. Detection is <b>event-driven and live</b>:
            Android&apos;s input-device listener feeds a short stability
            window (duplicate connect bursts and Bluetooth flaps coalesce —
            no flicker, no spam), then ONE fresh device scan produces at
            most one state transition. A launch scan covers starting the app
            with the keyboard already attached; a resume re-scan covers
            connects that happened while backgrounded. Only a REAL alphabetic
            hardware keyboard triggers it — touchscreens, mice, gamepads,
            stylus pointers, and button clusters are all rejected by the
            predicate. <b>ONE transient in-app notice</b> per real connect
            transition (&quot;External keyboard detected — the on-screen
            keyboard has been turned off. You can change this in
            Settings.&quot;) — no notification permission, no channel, no
            repeats while the keyboard stays connected. <b>THE USER&apos;S
            STATE IS NEVER DESTROYED</b>: the suppression overlays the manual
            visibility state and hands it back on disconnect; an explicit
            reopen (deck toggle, the floating keyboard icon, a terminal tap)
            cancels the suppression and the user wins. Settings gains the{" "}
            <b>&quot;On-screen keyboard&quot;</b> toggle — automatically hide
            when an external keyboard is connected — persistent via the
            existing DataStore, default ON, immediately effective. No new
            permissions (the same 6), no new packaged dependencies, the
            M6-frozen surfaces untouched, JVM suite 734/734 green (38 new
            tests across four suites).
          </b>
        </p>
        <a className="btn" href="/PocketShell-v0.11.0-m7.0.0-m7p3-debug.apk">
          Download M7.1 P3 APK (debug, 30.4 MB)
        </a>
        <Sha text={HASHES.apk} />
        <p className="mono" style={{ border: "none", background: "transparent", padding: 0 }}>
          versionCode 45 / versionName 0.11.0-m7.0.0 (unchanged — P3 does not
          bump the version) — installs in place over every earlier build (same
          cert) — glibc layer ed82daa8… unchanged.
        </p>
      </div>

      <div className="card">
        <h2>
          Superseded &amp; withdrawn builds{" "}
          <span className="badge">history preserved</span>
        </h2>
        <p>
          The M7.1 P2.2 APK (7e0e99a9…, 30,664,394 B) and its source set are
          SUPERSEDED by this build: same version stamp (vc45 / 0.11.0-m7.0.0 —
          P3 deliberately does not guess the next version
          number), new content (live external-keyboard detection, the
          automatic deck control, the Settings toggle, the connect notice).
          Their exact bytes are no longer served; the
          complete history rides in the bundle below (P2.2 tip 6004805, now one
          commit below this tip 93ee631). Earlier withdrawals stand: the M7.1
          P2.1 APK (97c04120…), the M7.1 P2 APK (ae6f6445…), the M7.1 P1 APK
          (4d7349f7…), the M7.0 release
          APK (8826d30d…), the m7p8.1 (vc44) and the reset-lost
          m7p8/m7p7.1/m7p6/m6.0.4 sets — their content and history are fully
          contained in this bundle. The glibc layer artifact (rev=2) is
          byte-identical and served below.
        </p>
      </div>

      <div className="card">
        <h2>Update — no uninstall, no runtime reinstall</h2>
        <p>
          versionCode 45 installs <b>in place over the M7.1 P2.2 build (also
          45 — same stamp, new bytes), the M7.1 P2.1 build (also 45), the M7.1
          P2 build (also 45), the M7.1
          P1 build (also 45), the M7.0
          release (also 45), v0.10.0-m6.0.4
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
          launcher visibility/icon settings are untouched. M7.1 P3 is
          APP-side only: the layer marker and the glibc files stay
          byte-identical (rev=2, ed82daa8…).
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
            aggregates, and the P9 interaction fixes (scrollable results above
            the keyboard deck, long-press actions on the landed folder, one
            close behavior).
          </li>
          <li>
            <b>M7.1 P1:</b> Home launchers — the Companions grid (four seeded
            companion websites) + the Your tools grid (registry + custom
            tools), hide-only remove with restore, custom tool add/edit/remove
            through the ONE verify-then-launch guest path, deterministic text
            badges, copied icon imports.
          </li>
          <li>
            <b>M7.1 P2:</b> the launcher settings rows fixed at the root cause
            (ONE shared weighted-row shape — no more one-character-per-line
            collapse, no stretched in-row buttons), the bundled official
            launcher marks (offline, normalized, badge fallback), and
            Antigravity (<code>agy</code>) replacing Gemini CLI in the
            curated defaults with the same honest probe.
          </li>
          <li>
            <b>M7.1 P2.1:</b> Aider removed from the curated set with no stale
            trace, and nine marks re-rendered from the owner-supplied official
            brand SVGs (vendored in-tree, offline, byte-reproducible).
          </li>
          <li>
            <b>M7.1 P2.2:</b> every curated mark ships a
            two-variant theme pair on the app&apos;s own plate tones (theme-reactive
            icon colors, live theme flips), Companions scroll in one row and
            tools in two — both with scroll dots — and the packages affordance
            is the tools header&apos;s Manage action (the mid-page footer link
            retired).
          </li>
          <li>
            <b>M7.1 P3 (this build):</b> live external-keyboard detection —
            the on-screen deck hides itself when a USB/Bluetooth/dock keyboard
            connects and returns when it disconnects, one transient in-app
            notice per transition, the &quot;On-screen keyboard&quot; Settings
            toggle (default ON), the user&apos;s manual state never destroyed,
            manual controls fully preserved.
          </li>
        </ul>
      </div>

      <div className="card">
        <h2>Device gates for THIS build (docs/TESTING.md §45)</h2>
        <ol className="steps">
          <li>
            <b>USB flow:</b> attach a USB keyboard while a terminal session is
            open — the deck hides within ~a second, ONE notice appears
            (&quot;External keyboard detected…&quot;), the terminal expands,
            and the hardware keyboard types immediately; unplug — the deck
            returns by itself.
          </li>
          <li>
            <b>Bluetooth flow:</b> the same connect/disconnect behavior; no
            notice spam and no deck flicker across quick BT flaps.
          </li>
          <li>
            <b>Launch attached:</b> start PocketShell cold with the keyboard
            already connected — the correct state without replugging.
          </li>
          <li>
            <b>Settings:</b> the &quot;On-screen keyboard&quot; toggle is ON
            by default; OFF keeps the deck fully manual; flipping it
            mid-connection applies immediately; it persists across restarts.
          </li>
          <li>
            <b>Manual controls:</b> the floating keyboard icon reopens the
            deck during suppression and the user wins on disconnect; a
            manually-hidden deck returns hidden after a connect/disconnect
            cycle.
          </li>
          <li>
            <b>Regression:</b> extra keys, modifiers, arrow keys, deck
            animations, the keyboard inset on Terminal/Editor/Files/Companion,
            and the M7.0 Files scrolling fix — all as before.
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
          Source at the M7.1 P3 tip 93ee631 (the ExternalKeyboard detector +
          policy + ViewModel + notice bar, the Settings toggle, §45, and the
          full P2.2 + P2.1 + P2 + P1 +
          M7.0 release below it). The zip intentionally contains no dotfiles;
          full history rides in the git bundle — the complete milestone history
          (M0 → m7.1 p3), all design contracts, the procfs contract, and the
          runtime documentation. Bundle main tip 93ee631 = the exact app tip
          this APK was built from; the archived tree is cut at the same commit.
          History note: this bundle continues the user-restored P7.1 delivery
          bundle (fb01540) through the M7.0 release chain (47bed42 → 709d126 →
          dd81bc8), the P1 launcher phase (3abb2e8), the P2 UI-repair phase
          (1b15bde), the P2.1 owner-marks phase (e0a2471 — see worklog
          Tasks 22–32), and the P2.2 theme-icons phase (6004805 — Task 33).
        </p>
        <a className="btn secondary" href="/PocketShell-v0.11.0-m7.0.0-m7p3-source.zip">
          source.zip (M7.1 P3 tip)
        </a>
        <a className="btn secondary" href="/PocketShell-v0.11.0-m7.0.0-m7p3-source.tar.gz">
          source.tar.gz (M7.1 P3 tip)
        </a>
        <a className="btn secondary" href="/pocketshell-m7.1-p3.gitbundle">
          git bundle (full history, M7.1 P3 tip)
        </a>
        <Sha text={HASHES.zip} />
        <Sha text={HASHES.tgz} />
        <Sha text={HASHES.bundle} />
        <a className="btn secondary" href="/pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz">
          glibc layer artifact (transparency copy, rev=2 unchanged)
        </a>
        <Sha text={HASHES.glibc} />
        <p>
          Restore: <code>git clone pocketshell-m7.1-p3.gitbundle pocketshell</code>.
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
        aligned into the tools header ·{" "}
        <b>m7.1 p3 (this build): live external-keyboard detection — the
        on-screen deck hides itself on connect and returns on disconnect, one
        transient notice per transition, the On-screen keyboard Settings
        toggle, manual controls preserved, no new permissions</b>.
        Correctness before cleverness. Visible UI before diagnostics.
      </footer>
    </main>
  );
}

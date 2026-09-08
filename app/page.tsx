const VERSION = "v0.11.2-m7.1.1";

// SHA pins — the M7.1.1 FIX RELEASE delivery. The payload cutter stages
// from the pinned fix tip (3cbfec2) with zeroed mtimes; the embedded git
// bundle's pack bytes are not re-cut-stable, so these pins refer to the ONE
// delivered cut. Semantic pins: versionCode 47, versionName 0.11.2-m7.1.1
// (the sub-milestone precedent), cert d96a6f66…8bf659, embedded layer asset
// 898131ff… /17,920,000 B == GlibcRuntimePin. The glibc layer is UNCHANGED
// rev=2 (byte-identical artifact ed82daa8…). The M7.1 release APK
// (2d298c85…) is superseded by this fix and withdrawn below; its history
// rides in the bundle.
const HASHES = {
  apk: "5d8761418324950815588f170149342a6aea5fe90b963dfda3364e3d787e5dc1",
  zip: "d8c1ef85bb850c3ba6fad363912d559a2129b22f03ddbea59c6e64fa7034bd1b",
  tgz: "d183c3eb4e7461ac5f5ec59204cf9d056bb502b32465e402221fbae01b53a954",
  bundle: "af73c57b216206ce7e61acaf7bdf2ffe5dbf9b60b91772821d2dc26869a5632f",
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
          M7.1.1 FIX RELEASE — the external-keyboard detection rebuilt from
          the real-device failure{" "}
          <span className="badge">fix tip 3cbfec2 · vc47</span>
        </h2>
        <p>
          <b>
            M7.1 P3 passed every JVM suite and FAILED on the real phone
            (Samsung SM-F711B / Galaxy Z Flip 3, One UI). This release fixes
            the audited causes — it does not patch blindly: <b>Fix 1</b> the
            terminal-canvas tap no longer cancels the auto-hide (the primary
            defect — the most-used gesture in a terminal app popped the deck
            straight back up after every connect; the gate now lives in the
            ONE authoritative state model); <b>Fix 2</b> a hard 2,000 ms
            confirm deadline ends debounce starvation — the old stability
            window restarted on every input-device event with no ceiling, so
            periodic Bluetooth LE re-announcements could defer detection
            forever; <b>Fix 3</b> a SECOND detection mechanism — the
            Application-level configuration-change cross-check feeds the same
            coalescing detector, covering OEM stacks that miss
            InputDeviceListener callbacks for Bluetooth HID reconnections;{" "}
            <b>Fix 4</b> both-direction notices — exactly one per confirmed
            transition (&quot;External keyboard detected — onscreen keyboard
            disabled.&quot; and &quot;External keyboard disconnected.&quot;),
            still in-app only, zero new permissions; <b>Fix 5</b> the
            persistent On-screen keyboard preference (DataStore, default ON)
            — detection is a temporary runtime override that never writes it;
            a disconnect re-evaluates the preference, an OFF preference is
            never forced back on, and the user&apos;s explicit [⌨] / deck
            actions always win. ONE authoritative model
            (ExternalKeyboardVisibilityModel) owns the state; the root holds
            no local copy. <b>VERIFICATION AT THIS STAMP</b>: the FULL JVM
            suite forced --rerun-tasks — 746/746 green (app 601 +
            terminal-emulator 145, 0 failures, 0 errors) — and a fresh APK
            audited end-to-end: aapt2 badging versionCode=&apos;47&apos;
            versionName=&apos;0.11.2-m7.1.1&apos;, the UNCHANGED 6-permission
            set, exactly 26 launcher icon entries, the new VisibilityModel +
            Detector dex symbols present, targetSdk 28, the pinned cert
            d96a6f66…8bf659. The mandatory REAL-DEVICE gate is
            docs/TESTING.md §47 (16 steps: USB + Bluetooth, all four
            transitions, the settings matrix, the canvas-tap regression) — a
            green build never claims the hardware pass. M7.2 (notification
            &amp; agent-activity) starts only AFTER that gate passes.
          </b>
        </p>
        <a className="btn" href="/PocketShell-v0.11.2-m7.1.1-debug.apk">
          Download M7.1.1 fix APK (debug, 30.5 MB)
        </a>
        <Sha text={HASHES.apk} />
        <p className="mono" style={{ border: "none", background: "transparent", padding: 0 }}>
          versionCode 47 / versionName 0.11.2-m7.1.1 (the sub-milestone fix
          stamp) — installs in place over every earlier build (same cert) —
          glibc layer ed82daa8… unchanged.
        </p>
      </div>

      <div className="card">
        <h2>
          Superseded &amp; withdrawn builds{" "}
          <span className="badge">history preserved</span>
        </h2>
        <p>
          The M7.1 release APK (2d298c85…, 30,448,845 B, vc46 /
          0.11.1-m7.1.0) and its source set are SUPERSEDED by this fix
          release: the release is FROZEN and remains the milestone record,
          but its external-keyboard behavior is what the real-device gate
          rejected — M7.1.1 supersedes it with the same architecture rebuilt.
          The M7.1 release bytes are no longer served (the upload/ insurance
          copies survive byte-exact); the complete history rides in the
          bundle below (the M7.1 release tip 4e86e50 is a direct ancestor of
          this fix tip 3cbfec2). Earlier withdrawals stand: the M7.1 P3 APK
          (46fb0d8b…), the M7.1 P2.2 APK (7e0e99a9…), the M7.1 P2.1 APK
          (97c04120…), the M7.1 P2 APK (ae6f6445…), the M7.1 P1 APK
          (4d7349f7…), the M7.0 release APK (8826d30d…), the m7p8.1 (vc44)
          and the reset-lost m7p8/m7p7.1/m7p6/m6.0.4 sets — their content and
          history are fully contained in this bundle. The glibc layer
          artifact (rev=2) is byte-identical and served below.
        </p>
      </div>

      <div className="card">
        <h2>Update — no uninstall, no runtime reinstall</h2>
        <p>
          versionCode 47 installs <b>in place over the M7.1 release (46), the
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
          launcher visibility/icon settings are untouched. M7.1.1 is APP-side
          only: the layer marker and the glibc files stay byte-identical
          (rev=2, ed82daa8…).
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
            <b>M7.1.1 (this build):</b> the external-keyboard system rebuilt
            after the real-device failure — the ONE authoritative
            ExternalKeyboardVisibilityModel (persistent On-screen keyboard
            preference + hardware state + explicit user request → effective
            visibility), the GATED terminal-canvas tap (it no longer undoes
            the auto-hide), the 2 s confirm deadline against event storms,
            the configuration-change cross-check as a second detection
            mechanism, both-direction transition notices (connect AND
            disconnect), and the preference-aware restore (an OFF preference
            is never forced back on).
          </li>
        </ul>
      </div>

      <div className="card">
        <h2>Device gates (docs/TESTING.md — §47 is the M7.1.1 gate)</h2>
        <ol className="steps">
          <li>
            <b>External keyboard — USB flow (§47):</b> connect while a
            terminal is open — the deck hides within ~0.5–2 s, ONE
            &quot;External keyboard detected&quot; banner, no spam;{" "}
            <b>then tap the terminal canvas — the deck STAYS hidden</b> (the
            M7.1 failure); the floating [⌨] still opens it on demand;{" "}
            <b>unplug — the deck returns per the preference</b> with the
            &quot;External keyboard disconnected.&quot; banner.
          </li>
          <li>
            <b>Bluetooth + launch-attached (§47):</b> the same behavior over
            BT (reconnect, sleep/wake), no flicker across flaps, and starting
            the app cold with the keyboard already attached disables the deck
            immediately.
          </li>
          <li>
            <b>Settings matrix (§47):</b> &quot;On-screen keyboard&quot; OFF
            while connected → the deck stays hidden after disconnect and
            across restart (the persistent preference); OFF with no keyboard
            → the deck closes now; ON again → the override applies while
            connected and the deck returns on disconnect.
          </li>
          <li>
            <b>Home + launchers (§41-§44):</b> one x-scroll Companions row,
            two tools rows with scroll dots, themed marks flipping live with
            the theme, Manage opening packages, curated + custom tools
            launching through verify-then-launch, badges and hide/restore.
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
          Source at the M7.1.1 FIX tip 3cbfec2 (the rebuilt keyboard state
          system, the TESTING §47 real-device gate, and the full M7.1 release
          + P3 + P2.2 + P2.1 + P2 + P1 + M7.0 chain below it). The zip
          intentionally contains no dotfiles; full history rides in the git
          bundle — the complete milestone history (M0 → the m7.1.1 fix), all
          design contracts, the procfs contract, and the runtime
          documentation. Bundle main tip 3cbfec2 = the exact app tip this APK
          was built from; the archived tree is cut at the same commit.
          History note: this bundle continues the user-restored P7.1 delivery
          bundle (fb01540) through the M7.0 release chain (47bed42 → 709d126
          → dd81bc8), the P1 launcher phase (3abb2e8), the P2 UI-repair phase
          (1b15bde), the P2.1 owner-marks phase (e0a2471 — worklog Tasks
          22–32), the P2.2 theme-icons phase (6004805 — Task 33), the P3
          external-keyboard phase (93ee631 — Task 34), the M7.1 release
          closure (4e86e50 — Task 35), and this fix (3cbfec2).
        </p>
        <a className="btn secondary" href="/PocketShell-v0.11.2-m7.1.1-source.zip">
          source.zip (M7.1.1 fix tip)
        </a>
        <a className="btn secondary" href="/PocketShell-v0.11.2-m7.1.1-source.tar.gz">
          source.tar.gz (M7.1.1 fix tip)
        </a>
        <a className="btn secondary" href="/pocketshell-m7.1.1.gitbundle">
          git bundle (full history, M7.1.1 fix tip)
        </a>
        <Sha text={HASHES.zip} />
        <Sha text={HASHES.tgz} />
        <Sha text={HASHES.bundle} />
        <a className="btn secondary" href="/pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz">
          glibc layer artifact (transparency copy, rev=2 unchanged)
        </a>
        <Sha text={HASHES.glibc} />
        <p>
          Restore: <code>git clone pocketshell-m7.1.1.gitbundle pocketshell</code>.
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
        (734/734 JVM), M7.1 tagged and FROZEN ·{" "}
        <b>v0.11.2-m7.1.1 (this build): the M7.1.1 external-keyboard fix —
        the real-device failure causes rebuilt (the gated canvas tap, the
        confirm deadline, the config-change cross-check, both-direction
        notices, the persistent preference), ONE authoritative visibility
        model, 746/746 JVM, the §47 real-device gate mandatory</b>.
        Correctness before cleverness. Visible UI before diagnostics.
      </footer>
    </main>
  );
}

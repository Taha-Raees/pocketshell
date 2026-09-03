const VERSION = "v0.7.0-m3.1";

const HASHES = {
  apk: "76a241ca3ca88a1331ea00e16a0cf898cfe730b471e5796c0076154f5ab0d584",
  zip: "733602ee66156a54795377ca04dc9ff8123cd1c3dcc2c494be4f92ed5e3d3570",
  tgz: "310612cfd540445a7dd57a6d2a1d4646bb50172a5fcda7695e5eae5e4c2ee0e3",
  bundle: "2ca125b9580e7396b16850f97a93f2ee5f4dd57ef56369904039311bef2c2850",
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
          Phase 3.1 — Terminal Experience Redesign: "Midnight Sapphire"{" "}
          <span className="badge">versionCode 18</span>
        </h2>
        <p>
          The Terminal screen only — redesigned to feel like a premium, modern,
          Android-native Linux terminal. <b>Home, Explore, Packages, Settings,
          Diagnostics, navigation and the whole M2.6 runtime are untouched.</b>{" "}
          Design contract committed before implementation
          (<code>docs/PHASE-3.1-DESIGN.md</code>), plan-first.
        </p>
        <ul className="steps">
          <li>
            <b>Blue-dark, never black:</b> the terminal page carries its own
            Midnight identity in every app theme — chrome <code>#101B30</code>,
            tab strip <code>#0D1730</code>, terminal canvas{" "}
            <code>#080F1D</code> (deepest blue-black), keyboard deck{" "}
            <code>#131F38</code>. Exactly <b>one</b> accent — Sapphire{" "}
            <code>#7FA3EF</code> — for the cursor, the active tab, modifier
            states and Enter. A real 16-color ANSI palette is installed at
            process start (blue-tinted, muted, legible) — and programs that
            set their own colors still win: the terminal stays a real terminal.
          </li>
          <li>
            <b>JetBrains Mono NL:</b> the no-ligature build (OFL 1.1) —
            character-exact output (<code>-&gt;</code> stays two characters),
            unmistakable 0/O and 1/l/I, applied through the vendored terminal
            view's own typeface API. The cursor stays the upstream block
            cursor, now Sapphire; DECSCUSR bar/underline still honored.
          </li>
          <li>
            <b>Editor-style session tabs (not pills):</b> rounded-TOP tabs;
            inactive tabs recessed and quiet with a single right hairline; the
            active tab is canvas-colored with a 2.5dp Sapphire top hairline and
            physically cuts the strip's bottom hairline — it opens into the
            terminal workspace, exactly the asymmetric-border direction of the
            brief.
          </li>
          <li>
            <b>Keyboard rebuilt from scratch — no system IME anywhere:</b>{" "}
            TOP row <code>Esc Tab ← ↑ ↓ →</code> (arrows grouped in an inset
            panel, auto-repeat kept so held ↑ cycles shell history) · MIDDLE
            PocketShell QWERTY (digits, letters, a terminal punctuation row{" "}
            <code>- / : ; , . $ ' " @</code>, and a symbol page with{" "}
            <code>INS DEL HOME END PGUP PGDN</code> — the v0.6.2 coverage is
            fully preserved) · BOTTOM row{" "}
            <code>[⌨] Ctrl Alt Space Shift Enter</code> — the exact order,
            icon-only far-left toggle (no ON/OFF text, never moves), accent-
            filled Enter.
          </li>
          <li>
            <b>The dedicated Fn key is gone:</b> F1–F10 are the number-row
            keys' long-press actions (hold → "F#" bubble → release commits),
            F11/F12 on the tablet −/= long-press — fully reliable because
            PocketShell owns the keyboard; <code>readFnKey()</code> honestly
            returns false. The dispatch pipeline is unchanged (synthetic
            KeyEvents → vendored KeyHandler → PTY), so Ctrl+C/D/L/A/E/W, Alt
            and Shift remain real terminal input, and hardware keyboards keep
            working. The ⌨ toggle collapses only the QWERTY body — both
            accessory rows always stay; landscape compresses to 4 rows;
            tablets get wide rows of the same identity.
          </li>
        </ul>
        <a className="btn" href="/PocketShell-v0.7.0-m3.1-debug.apk">
          Download APK (debug, 22 MB)
        </a>
        <Sha text={HASHES.apk} />
        <p className="mono" style={{ border: "none", background: "transparent", padding: 0 }}>
          signing cert SHA-256: d96a6f664d7f8d7194733672dcd6eb9f33f40a0078ab07ff66bfd605138bf659 (same as v0.4.1–v0.7.0-m3.1)
        </p>
      </div>

      <div className="card">
        <h2>Update — no uninstall, no runtime reinstall</h2>
        <p>
          versionCode 18 installs <b>in place over v0.6.2 (16)</b> and even
          over the discarded v0.7.0-ui (17) — same pinned signing key. Your
          Alpine runtime, installed packages and cache are untouched: the
          fd-link patch and sysdata overlays behave exactly as the
          device-confirmed v0.6.2 (CHANGELOG 0.6.2-m2.6 has those details).
          Phase 3.1 changed no runtime code — the M2.6 Gates A–H environment
          remains valid.
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
            Linux Shell (M2.3 + M2.6): the Alpine guest via proot — with{" "}
            <code>/proc</code>, and <code>apk</code> still working.
          </li>
          <li>
            Package management (M2.4, device-gate PASSED): curated cards
            (nano, htop, vim, git, python3) with Open/Uninstall; Home shows
            exactly what the real apk database confirms.
          </li>
          <li>
            M2.5/M2.6: search-install any package, real process tools.
          </li>
          <li>
            <b>v0.7.0-m3.1 (this build):</b> the Terminal page redesigned —
            Midnight Sapphire surfaces, JetBrains Mono NL, editor tabs, and a
            from-scratch keyboard with the exact final layout. Everything else
            is deliberately byte-identical behavior to v0.6.2.
          </li>
        </ul>
      </div>

      <div className="card">
        <h2>Quick checks (docs/TESTING.md §12 — Phase 3.1 device gate)</h2>
        <ol className="steps">
          <li>
            Install {VERSION} over v0.6.2 (no uninstall) → open Terminal.
          </li>
          <li>
            <b>Visual:</b> blue-dark everywhere on the page, no pure-black
            flat rectangle; JetBrains Mono output; Sapphire blinking block
            cursor; active tab merges into the canvas and cuts the strip
            hairline; the rest of the app looks exactly like v0.6.2.
          </li>
          <li>
            <b>Keyboard layout (verify exactly):</b> top{" "}
            <code>Esc Tab … ← ↑ ↓ →</code>; middle QWERTY with the{" "}
            <code>?123</code> symbol page; bottom{" "}
            <code>[⌨] Ctrl Alt Space Shift Enter</code>; ⌨ icon-only, far-left,
            stable.
          </li>
          <li>
            <b>Keyboard behavior:</b> hold <code>3</code> → F3 bubble, release
            sends F3 (quick tap still types 3); modifiers one-shot on first
            tap, lock on second (fill + outline + dot — not color alone);
            Shift uppercases; ⌫ and arrows repeat.
          </li>
          <li>
            <b>Modifiers:</b> Ctrl+C interrupts; Ctrl+D exits; Ctrl+L clears;
            Ctrl+A/E line jumps; Ctrl+W word delete.
          </li>
          <li>
            <b>Linux regression set:</b> <code>apk update</code> OK,{" "}
            <code>node --version</code> and <code>hermes --version</code> print
            real versions, tab completion works, resize on rotate/keyboard
            toggle keeps the prompt correct.
          </li>
        </ol>
      </div>

      <div className="card">
        <h2>Source (version control)</h2>
        <p>
          Complete buildable source. The zip intentionally contains no
          dotfiles; full history rides in the git bundle (tip 006e5df —
          includes the complete milestone history and the honest record of the
          discarded UI attempt + rollback).
        </p>
        <a className="btn secondary" href="/PocketShell-v0.7.0-m3.1-source.zip">
          source.zip
        </a>
        <a className="btn secondary" href="/PocketShell-v0.7.0-m3.1-source.tar.gz">
          source.tar.gz
        </a>
        <a className="btn secondary" href="/pocketshell-m2.gitbundle">
          git bundle (full history)
        </a>
        <Sha text={HASHES.zip} />
        <Sha text={HASHES.tgz} />
        <Sha text={HASHES.bundle} />
        <p>
          Restore: <code>git clone pocketshell-m2.gitbundle pocketshell</code>.
          Includes <code>keystore/debug.keystore</code> — clones build APKs
          with the same signing identity.
        </p>
      </div>

      <footer>
        Milestones: M0–M1.3 terminal · M2.2 runtime install · M2.3 Linux shell
        (device-verified) · M2.4 package layer (device gate PASSED) · M2.5
        search-install · M2.6 real /proc + real apk (device-CONFIRMED
        2026-09-02) · v0.6.2 hardlink + sysdata repairs ·{" "}
        <b>v0.7.0-m3.1 (this build): Phase 3.1 — the Terminal page redesigned
        ("Midnight Sapphire"), custom keyboard from scratch, terminal-only
        scope</b>. Next: Phase 3.2 candidates per your direction. Your device
        keeps doing the QA that matters.
      </footer>
    </main>
  );
}

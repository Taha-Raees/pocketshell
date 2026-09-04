const VERSION = "v0.7.0-m4.0.3";

const HASHES = {
  apk: "b456160e678b1883ace401903904dd99206e6f4dadfc2241a7b8d671223ce431",
  zip: "06ec5d5e25a2553beea6421de6f2d47bb72d5b39c6dad89eba758d15c02ae30e",
  tgz: "f773a5b97f0c2578e37b62f7fa718c9ae2cf6c9faf4b57cef8c4167fc23e28a6",
  bundle: "be5241600204ba79cb522dbf74c05355bb241a0ddd070aa99e097d109222d406",
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
          Your m4.0.2 bug list — every item fixed{" "}
          <span className="badge">versionCode 27</span>
        </h2>
        <p>
          <b>The keyboard now belongs to the whole app.</b> It types into
          Companions, it pushes everything up instead of stacking on top of
          anything, it collapses into a small corner icon when toggled off,
          the dead <b>-</b> key works, the arrows are longer — and the
          Companion white screen finally gets both an engine fix and an
          honest explanation. The <b>+</b> button opens a real Companion
          picker.
        </p>
        <ul className="steps">
          <li>
            <b>Keyboard for Companions too:</b> deck presses follow focus.
            Tap a text field in the Companion page and type — the characters
            land in the page. Tap the terminal above and type — the shell
            gets them again. While the deck is up, the system keyboard is
            blocked (never two keyboards); toggle the deck off and Companion
            inputs can still summon the system keyboard, with the panel
            lifting above it.
          </li>
          <li>
            <b>Nothing hides under the keyboard:</b> the deck is now the
            bottom-most surface. The Companion panel (handle + tabs + page)
            rides ABOVE it — the keyboard never opens on top of anything.
          </li>
          <li>
            <b>Toggle = full collapse:</b> the keyboard button now removes
            the WHOLE deck; a small Midnight keyboard icon floats at the
            bottom-right corner (above every layer) to bring it back
            whenever you want.
          </li>
          <li>
            <b>Every key works:</b> the &quot;-&quot; key (and the whole
            digit row) was dead on quick taps because its hold-gesture layer
            swallowed them — a quick tap now commits the character, holding
            still gives the F-key layer. Arrow keys are 12dp longer
            horizontally.
          </li>
          <li>
            <b>The white canvas, for real:</b> WebViews are now created with
            the Activity context (the application context used so far is a
            known blank-canvas source on OEM builds), and a 15s watchdog
            catches the case where a page paints nothing: the canvas shows
            &quot;Page never rendered&quot; + the installed WebView version
            instead of a silent white box. <b>Retry</b> alternates GPU →
            SOFTWARE rendering (compatibility mode) — the honest second
            attempt for broken WebView builds.
          </li>
          <li>
            <b>&quot;+&quot; finally does something:</b> it opens a Midnight
            sheet listing every Companion (open tabs marked) — tap to open
            one, or &quot;Add Companion&quot; to go to the management page.
          </li>
          <li>
            <b>Nothing else changed:</b> data, logins, tabs and heights
            survive the in-place update. Full suite green: <b>724 tests, 0
            failures</b> (6 new pins on routing + the render-stall model).
          </li>
        </ul>
        <a className="btn" href="/PocketShell-v0.7.0-m4.0.3-debug.apk">
          Download APK (debug, 22 MB)
        </a>
        <Sha text={HASHES.apk} />
        <p className="mono" style={{ border: "none", background: "transparent", padding: 0 }}>
          signing cert SHA-256: d96a6f664d7f8d7194733672dcd6eb9f33f40a0078ab07ff66bfd605138bf659 (same as v0.4.1–v0.7.0-m4.0.3)
        </p>
      </div>

      <div className="card">
        <h2>Update — no uninstall, no runtime reinstall</h2>
        <p>
          versionCode 27 installs <b>in place over v0.7.0-m4.0.2 (26),
          v0.7.0-m4.0.1 (25), v0.7.0-m4.0 (24) and every earlier pinned-cert
          build</b>. Your Alpine runtime, installed packages, Kilo/Hermes
          installation, the procfs contract, every Phase 3 behavior and all
          Companion data (logins included) are untouched. This build also
          contains the m4.0.1 startup fix — it starts regardless of the
          WebView package&apos;s state.
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
            Phase 3.1–3.5: Midnight Sapphire terminal + Home workspace, the
            nine-CLI probe-gated registry, tap-to-launch, Midnight system
            pages.
          </li>
          <li>
            Phase 4 (m4.0): Companion — the embedded web workspace. Generic
            Name+URL definitions, bottom drag handle, persistent sessions,
            live multi-tab, file upload, intelligent Back, Midnight Sapphire
            throughout.
          </li>
          <li>
            m4.0.1: startup decoupled from WebView provider health (the
            launch-crash fix). m4.0.2: honest failure cards — the canvas is
            never mysteriously white.
          </li>
          <li>
            <b>v0.7.0-m4.0.3 (this build):</b> the shared keyboard —
            focus-routed into Companions, bottom-most on screen, fully
            collapsible to a corner icon, every key working — plus the
            render-stall watchdog with compatibility-mode Retry, and the
            &quot;+&quot; Companion picker.
          </li>
        </ul>
      </div>

      <div className="card">
        <h2>Quick checks (docs/TESTING.md §20 — keyboard + honesty device gate)</h2>
        <ol className="steps">
          <li>
            Install {VERSION} in place over the current build → open the
            terminal, raise the Companion: the panel now sits ABOVE the
            keyboard — nothing under it.
          </li>
          <li>
            Tap a text field in the Companion page, type on the PocketShell
            keyboard → characters appear IN THE PAGE. No GBoard. Tap the
            terminal and type → back to the shell.
          </li>
          <li>
            Tap <b>-</b> → appears instantly. Quick-tap digits → digits.
            Hold a digit → F-key popup. Toggle the keyboard off → the whole
            deck vanishes and a small keyboard icon appears at the
            bottom-right corner; tap it → the deck returns.
          </li>
          <li>
            Tap <b>+</b> on the Companion tab strip → the picker sheet lists
            your Companions; &quot;Add Companion&quot; opens the management
            page.
          </li>
          <li>
            The previously-white tab: EITHER it now renders (Activity-context
            fix) OR within ~15s you get &quot;Page never rendered&quot; + the
            WebView version — never a silent white box. Try <b>Retry</b>:
            the second attempt uses SOFTWARE rendering (compatibility mode).
            Report the WebView version shown on the card.
          </li>
          <li>
            Regressions: §18 startup, §19 failure cards, §17 spot-checks
            (drag 1:1, tabs, upload, Back, login persistence).
          </li>
        </ol>
      </div>

      <div className="card">
        <h2>Source (version control)</h2>
        <p>
          Complete buildable source. The zip intentionally contains no
          dotfiles; full history rides in the git bundle — includes the
          complete milestone history, all Phase 3 design contracts, the
          procfs contract, and the Phase 4 Companion design contract with
          the §22/§23/§24 amendments.
        </p>
        <a className="btn secondary" href="/PocketShell-v0.7.0-m4.0.3-source.zip">
          source.zip
        </a>
        <a className="btn secondary" href="/PocketShell-v0.7.0-m4.0.3-source.tar.gz">
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
        search-install · M2.6 real /proc + real apk · v0.6.2 sysdata repairs ·
        v0.7.0-m3.1 terminal redesign · m3.2 OS launcher · m3.3 flat
        workspace · m3.4 registry expansion · m3.5 command launch fix · m3.6
        procfs contract · m4.0 Phase 4 Companion · m4.0.1 startup hotfix ·
        m4.0.2 honest failure surfaces ·{" "}
        <b>v0.7.0-m4.0.3 (this build): one keyboard for everything — and the
        white canvas can no longer hide</b>. Your device keeps doing the QA
        that matters.
      </footer>
    </main>
  );
}

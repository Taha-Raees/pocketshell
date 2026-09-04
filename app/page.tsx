const VERSION = "v0.7.0-m4.0.8";

const HASHES = {
  apk: "6626d8dd51a26c12fa5d6990eb688d1eccf40961541452460c96939a1d33f22c",
  zip: "7d9b738ae9b83806363e24ac3c9f81ef9d202d4c17c8818f701a4b3dbf64355c",
  tgz: "8e9bd2b5eab347fa94e552c1210f349a9793d76229099e4f54e79603940ef172",
  bundle: "91a01edcb725321329b3c474269863ebbef8e8209321bb75aae10d20936c2ff6",
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
          The painted-but-black decode: the light package returns, on top of
          the fixed host <span className="badge">versionCode 32</span>
        </h2>
        <p>
          <b>Your m4.0.7 health report cracked it.</b> Both tabs answered{" "}
          <code>pixels: painted</code> — a GPU tab <b>and</b> a software-layer
          tab — while you still saw black. A software-layer view cannot fail
          to reach the screen (the rest of the app renders through the same
          window), so the black <b>is the page&apos;s own painted output</b>:
          the site&apos;s near-black body. Two dark sources had been re-armed
          by m4.0.7&apos;s rollback:
        </p>
        <ul className="steps">
          <li>
            <b>Algorithmic darkening was ON:</b> PocketShell targets SDK 28
            (the proot/W^X constraint), and a legacy-target app on Android 15
            gets WebView algorithmic darkening by default.
          </li>
          <li>
            <b>prefers-color-scheme answered DARK:</b> the WebView inherits
            the app&apos;s Midnight uiMode, so sites served their dark themes
            onto a dark body. The m4.0.5/6 light levers had looked guilty only
            because the never-attaching host (fixed in m4.0.7) made every
            recipe paint nothing — the rollback over-corrected.
          </li>
          <li>
            <b>Fix 1 — forced-light scheme:</b> the WebView&apos;s
            configuration is pinned to UI_MODE_NIGHT_NO (the documented
            prefers-color-scheme lever), so sites ALWAYS serve their light
            themes — a white body with dark text you can SEE, even when a
            page&apos;s app shell is thin.
          </li>
          <li>
            <b>Fix 2 — darkening OFF at every API level:</b>{" "}
            setAlgorithmicDarkeningAllowed(false) on Android 13+, the
            deprecated setForceDark(FORCE_DARK_OFF) on 12 and below; the theme
            already carries android:forceDarkAllowed=false.
          </li>
          <li>
            <b>Fix 3 — the Activity lookup, fixed:</b> the forced-light
            configuration context is not an Activity — the glass probe now
            unwraps ANY context chain to find the hosting window, and the pool
            remembers the host from acquire (a hidden m4.0.6 glass-probe
            regression, gone).
          </li>
          <li>
            <b>The probe cannot be fooled again:</b> the health report now
            names WHAT IS ON THE GLASS —{" "}
            <code>glass: dominant #0D0D0D · 97% near-black · 3 colors</code> —
            plus <code>scheme: forced light</code> and the PAGE&apos;S OWN
            VOICE (its title and first visible words). One pasted report now
            names the page state (login wall, consent, empty shell) with zero
            guessing.
          </li>
          <li>
            <b>Nothing else changed:</b> the keyed swap-safe host, attach
            kick, Chrome UA and all data survive. Full suite green:{" "}
            <b>772 executions / 0 failures</b> (+4 pins; all earlier pins
            intact).
          </li>
        </ul>
        <a className="btn" href="/PocketShell-v0.7.0-m4.0.8-debug.apk">
          Download APK (debug, 22 MB)
        </a>
        <Sha text={HASHES.apk} />
        <p className="mono" style={{ border: "none", background: "transparent", padding: 0 }}>
          signing cert SHA-256: d96a6f664d7f8d7194733672dcd6eb9f33f40a0078ab07ff66bfd605138bf659 (same as v0.4.1–v0.7.0-m4.0.8)
        </p>
      </div>

      <div className="card">
        <h2>Update — no uninstall, no runtime reinstall</h2>
        <p>
          versionCode 32 installs <b>in place over v0.7.0-m4.0.7 (31),
          v0.7.0-m4.0.6 (30),
          v0.7.0-m4.0.5 (29),
          v0.7.0-m4.0.4 (28), v0.7.0-m4.0.3 (27), v0.7.0-m4.0.2 (26),
          v0.7.0-m4.0.1 (25), v0.7.0-m4.0 (24) and every earlier
          pinned-cert build</b>. Your Alpine runtime, installed packages,
          Kilo/Hermes installation, the procfs contract, every Phase 3
          behavior and all Companion data (logins included) are untouched.
          This build also contains the m4.0.1 startup fix — it starts
          regardless of the WebView package&apos;s state.
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
            m4.0.1: startup decoupled from WebView provider health. m4.0.2:
            honest failure cards. m4.0.3: the shared keyboard + render-stall
            watchdog + the &quot;+&quot; Companion picker. m4.0.4: the
            pixel-truth stall probe (main-region verdict) + the keyboard
            toggle in one spot/one shape. m4.0.5: Force Dark off (3 layers),
            Chrome UA, the DOM boot witness with the page&apos;s own
            testimony.
          </li>
          <li>
            m4.0.6: the SSR-proof boot witness and the standing{" "}
            <b>Page health</b> sheet with a one-tap <b>Copy report</b>.
            m4.0.7: the swap-safe WebView host (compat renderer + tab
            switches finally reach the screen), creation rollback, the
            glass-first pixel probe, the attach kick.
          </li>
          <li>
            <b>v0.7.0-m4.0.8 (this build):</b> the painted-but-black decode —
            the light package returns on top of the fixed host: forced-light
            prefers-color-scheme, darkening off at every API level, a
            context-safe activity lookup, and a color-truthful health report
            that names what is actually on the glass plus the page&apos;s own
            voice.
          </li>
        </ul>
      </div>

      <div className="card">
        <h2>Quick checks (docs/TESTING.md §25 — the m4.0.8 device gate)</h2>
        <ol className="steps">
          <li>
            Install {VERSION} in place → open the previously-black Companion
            tab. EXPECT the <b>LIGHT (white)</b> theme with visible dark
            text — NOT black, NOT near-black. A login wall is an acceptable
            and diagnosable state; pure black is not.
          </li>
          <li>
            Open the Zai tab too — same expectation. Let both run 30+
            seconds: no failure card while a page is actually visible.
          </li>
          <li>
            <b>The health report must name the glass:</b> tab strip ⓘ chip →
            Page health → expect <code>scheme: forced light</code>,{" "}
            <code>glass: dominant #XXXXXX · N% near-black · N colors</code>,
            and <code>page says: &quot;…&quot;</code>.
          </li>
          <li>
            If anything still looks wrong: <b>Page health → Copy report →
            paste in the chat</b> — the glass line names the exact on-screen
            color and the page names its own state, so the next fix is
            targeted, not a guess.
          </li>
          <li>
            Regressions: §24 (tab switching, compat mode), §23.2 (health
            sheet), §21 (keyboard toggle one spot/one shape), §20 (deck types
            into Companion AND terminal), §18 startup, §17 spot-checks
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
          the §22/§23/§24/§25 amendments.
        </p>
        <a className="btn secondary" href="/PocketShell-v0.7.0-m4.0.8-source.zip">
          source.zip
        </a>
        <a className="btn secondary" href="/PocketShell-v0.7.0-m4.0.8-source.tar.gz">
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
        m4.0.2 honest failure surfaces · m4.0.3 one keyboard for everything ·
        m4.0.4 pixels over promises · m4.0.5 the black page attacked at the
        root · m4.0.6 the page tells us everything · m4.0.7 the host was the
        bug ·{" "}
        <b>v0.7.0-m4.0.8 (this build): the painted-but-black decode — the
        light package returns on top of the fixed host, and the health
        report now names what is on the glass</b>.
        Your device keeps doing the QA that matters.
      </footer>
    </main>
  );
}

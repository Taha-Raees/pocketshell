const VERSION = "v0.7.0-m4.0.4";

const HASHES = {
  apk: "adbdcfe30fa99db486a671c813a1b0bc22f952527767a278bc4e1baa5d52333e",
  zip: "fa0decf9625cd7f6bd24a5fe00b7e09467299616444fede35d1d516a5ef4c1ca",
  tgz: "cf30c3d8508a59e0a86f99fb40fecb8c5e2756d0ef21fb0681fe77bd3a2c8b24",
  bundle: "81b2c89b26d979a6814130b9593f0eab84e712acde54e78b3485fa7b903bae76",
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
          Your post-m4.0.3 report — and what your screenshot proved{" "}
          <span className="badge">versionCode 28</span>
        </h2>
        <p>
          <b>The cookie banner cracked the black-page case.</b> That banner is
          the SITE&apos;S OWN (ChatGPT/OpenAI&apos;s) — and it painted at the
          bottom of an otherwise dead canvas. Translation: the page pipeline
          fires faithfully on this device&apos;s WebView build while the main
          content never rasterizes — which is exactly why every event-based
          watchdog so far stood down. This build stops trusting events and
          reads pixels. Plus: the keyboard toggle now lives in ONE place, in
          ONE shape, in both states.
        </p>
        <ul className="steps">
          <li>
            <b>The watchdog reads pixels, not promises:</b> every couple of
            seconds the canvas is probed twice — a software readback of the
            WebView, and on modern Android a PixelCopy of the frame exactly as
            it was PRESENTED. Only real page pixels stand the probe down. A
            canvas whose main region is still the bare Midnight flash-guard
            after ~15s IS a stall, whatever the page pipeline claims.
          </li>
          <li>
            <b>Partial paint is not content:</b> the cookie banner painted a
            few pixels at the bottom of the dead canvas — an &quot;any
            differing pixel&quot; check would call that healthy. The verdict
            now samples the MAIN region only: everything above the bottom 25%
            of the canvas, where sites dock consent bars. A banner can never
            vouch for a dead page again.
          </li>
          <li>
            <b>Silent first retry, honest second card:</b> the first detected
            stall re-creates the tab on the SOFTWARE renderer by itself (the
            classic fix for GPU paths that rasterize nothing). Only if that
            stalls too do you see the card: &quot;Page never rendered&quot; +
            your installed WebView version.
          </li>
          <li>
            <b>Three ways out on the card:</b> <b>Retry</b> (each press
            alternates GPU → SOFTWARE rendering), <b>Open in browser</b> (the
            same address in your real browser — settles whether it&apos;s the
            site or this device&apos;s WebView build), and{" "}
            <b>Continue anyway</b> (the raw canvas — you can tap the site&apos;s
            own Accept button; the probe then stays quiet and never fights you
            for the canvas).
          </li>
          <li>
            <b>Keyboard toggle in one place:</b> the [⌨] key now sits in the
            deck row BETWEEN Space and Enter (Ctrl · Alt · Space · Shift ·
            [⌨] · Enter) — and with the deck toggled off, the SAME rectangular
            key box parks at that same right-hand spot. The round bottom-right
            bubble is gone. One toggle, one shape, one place, both states.
          </li>
          <li>
            <b>About that cookie banner:</b> it belongs to the WEBSITE, not to
            PocketShell. Choose Accept/Reject once — cookies are flushed to
            storage on every pause, so your choice (and your logins) persist
            across launches. If it ever reappears every launch, that&apos;s a
            bug to report.
          </li>
          <li>
            <b>Nothing else changed:</b> data, logins, tabs and heights
            survive the in-place update. Full suite green: <b>0 failures</b>{" "}
            across all modules and variants (2 new pins on the main-region
            arithmetic).
          </li>
        </ul>
        <a className="btn" href="/PocketShell-v0.7.0-m4.0.4-debug.apk">
          Download APK (debug, 22 MB)
        </a>
        <Sha text={HASHES.apk} />
        <p className="mono" style={{ border: "none", background: "transparent", padding: 0 }}>
          signing cert SHA-256: d96a6f664d7f8d7194733672dcd6eb9f33f40a0078ab07ff66bfd605138bf659 (same as v0.4.1–v0.7.0-m4.0.4)
        </p>
      </div>

      <div className="card">
        <h2>Update — no uninstall, no runtime reinstall</h2>
        <p>
          versionCode 28 installs <b>in place over v0.7.0-m4.0.3 (27),
          v0.7.0-m4.0.2 (26), v0.7.0-m4.0.1 (25), v0.7.0-m4.0 (24) and every
          earlier pinned-cert build</b>. Your Alpine runtime, installed
          packages, Kilo/Hermes installation, the procfs contract, every Phase
          3 behavior and all Companion data (logins included) are untouched.
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
            watchdog + the &quot;+&quot; Companion picker.
          </li>
          <li>
            <b>v0.7.0-m4.0.4 (this build):</b> the cookie-banner lesson — the
            pixel-truth stall probe (main-region verdict, partial paint never
            counts), the silent software-render retry, the honest card with
            three ways out, and the keyboard toggle in one spot/one shape.
          </li>
        </ul>
      </div>

      <div className="card">
        <h2>Quick checks (docs/TESTING.md §21 — the m4.0.4 device gate)</h2>
        <ol className="steps">
          <li>
            Install {VERSION} in place → open the previously-black Companion
            tab. Within ~15s ONE of these must happen: the page renders, OR
            the &quot;Page never rendered&quot; card appears (it silently
            retried on the software renderer first). A bare black canvas that
            just sits there is a failure of this gate — report it.
          </li>
          <li>
            On the card, try <b>Open in browser</b> — the same address in your
            real browser. If it works there, this device&apos;s WebView build
            is the culprit (the card shows its version — report it).
          </li>
          <li>
            Try <b>Continue anyway</b>: the raw canvas comes back — if the
            site&apos;s cookie banner is there, tap Accept/Reject once, then
            fully close and reopen PocketShell: the banner must NOT return
            (cookies persist).
          </li>
          <li>
            Keyboard: the [⌨] key sits between Space and Enter; tap it → the
            deck collapses and the SAME rectangular key box appears at that
            right-hand spot (no round bubble); tap it → the deck returns.
          </li>
          <li>
            Regressions: §20 (deck types into Companion AND terminal, deck
            pushes everything up, &quot;-&quot;/digits quick-tap), §18
            startup, §17 spot-checks (drag 1:1, tabs, upload, Back, login
            persistence).
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
        <a className="btn secondary" href="/PocketShell-v0.7.0-m4.0.4-source.zip">
          source.zip
        </a>
        <a className="btn secondary" href="/PocketShell-v0.7.0-m4.0.4-source.tar.gz">
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
        m4.0.2 honest failure surfaces · m4.0.3 one keyboard for everything ·{" "}
        <b>v0.7.0-m4.0.4 (this build): the cookie-banner lesson — pixels over
        promises, and one toggle in one place</b>. Your device keeps doing the
        QA that matters.
      </footer>
    </main>
  );
}

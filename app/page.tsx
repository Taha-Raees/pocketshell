const VERSION = "v0.7.0-m4.0.1";

const HASHES = {
  apk: "c9fc0cc3c2bfbdca98fa1b948bec2bec7a1fd9d60f3cdd77a348414c23ccd678",
  zip: "7435127f3ecf4697cde67e45eb4c7948a315c85cd2223570ac877cb83df50b37",
  tgz: "dabbdb9692592a8ff3195a6acafade79b5a9e4d16fbc3044a330c091e2c4b213",
  bundle: "9020f9da0e25758d62520fb0e37b1a8e9b70cdae00c86a99723749f990fab205",
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
          Hotfix — a broken WebView update could kill every launch{" "}
          <span className="badge">versionCode 25</span>
        </h2>
        <p>
          <b>The reported crash (device, 2026-09-05):</b> after updating to
          m4.0, PocketShell crashed on <b>every</b> start before any UI
          appeared — and Samsung Device Care popped up &quot;Uninstall WebView
          updates?&quot;. Root cause, found and fixed: m4.0 initialized the
          Companion web runtime during Application startup, and that init
          called <code>CookieManager.getInstance()</code> — which
          <b> synchronously loads the entire Android System WebView provider
          before any UI, on every launch</b>. On this device the freshly
          updated WebView package itself crashes at provider init (a
          Samsung + microG combination), so every PocketShell launch died
          with it — even though the Companion was never opened. An optional
          layer&apos;s engine had taken the whole terminal app hostage.
        </p>
        <ul className="steps">
          <li>
            <b>The fix:</b> Application startup is now WebView-free — it
            holds a context reference and nothing else. Cookie configuration
            and WebView creation happen lazily at first Companion use and
            are fully guarded. No code path can crash the process on a
            broken provider anymore.
          </li>
          <li>
            <b>Graceful degradation, honestly:</b> if the device&apos;s WebView
            package is missing or crashing, only the Companion surface
            changes — it shows a minimal Midnight notice (&quot;Companion
            unavailable — Android System WebView is missing or crashing on
            this device&quot;). The terminal, Home, packages, Diagnostics and
            Settings keep working untouched.
          </li>
          <li>
            <b>Your data is unchanged:</b> installing this hotfix in place
            keeps every session — Companion logins live in the app&apos;s
            private web storage. Once the device has a healthy WebView
            (update &quot;Android System WebView&quot; in the Play Store, or accept
            Samsung&apos;s rollback — either way), the Companion works exactly
            as shipped in m4.0. You do NOT need to uninstall WebView updates
            for PocketShell to start anymore.
          </li>
          <li>
            <b>Phase 4 itself is untouched:</b> no feature, UI, storage or
            contract change. Full suite green: 704 tests, 0 failures.
          </li>
        </ul>
        <a className="btn" href="/PocketShell-v0.7.0-m4.0.1-debug.apk">
          Download APK (debug, 22 MB)
        </a>
        <Sha text={HASHES.apk} />
        <p className="mono" style={{ border: "none", background: "transparent", padding: 0 }}>
          signing cert SHA-256: d96a6f664d7f8d7194733672dcd6eb9f33f40a0078ab07ff66bfd605138bf659 (same as v0.4.1–v0.7.0-m4.0.1)
        </p>
      </div>

      <div className="card">
        <h2>Update — no uninstall, no runtime reinstall</h2>
        <p>
          versionCode 25 installs <b>in place over v0.7.0-m4.0 (24),
          v0.7.0-m3.6 (23) and every earlier pinned-cert build</b>. Your
          Alpine runtime, installed packages, Kilo/Hermes installation, the
          procfs contract, every Phase 3 behavior and all Companion data are
          untouched. If the app is currently crash-looping on your device,
          install this build over the broken one — it starts regardless of
          the WebView package&apos;s state.
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
            <b>v0.7.0-m4.0.1 (this build):</b> startup decoupled from WebView
            provider health — the m4.0 launch-crash fix described above;
            everything else identical to m4.0.
          </li>
        </ul>
      </div>

      <div className="card">
        <h2>Quick checks (docs/TESTING.md §18 — hotfix device gate)</h2>
        <ol className="steps">
          <li>
            Install {VERSION} in place over the crashing m4.0 (do NOT
            uninstall WebView updates first) → PocketShell opens normally to
            Home. Launch, kill, relaunch 3× — starts every time.
          </li>
          <li>
            With the WebView still broken: pull the Companion handle up →
            the minimal &quot;Companion unavailable&quot; notice renders — no crash,
            no blank panel; the terminal keeps working while it&apos;s up.
          </li>
          <li>
            Repair Android System WebView (Play Store update or Samsung&apos;s
            rollback) → restart PocketShell → the Companion loads normally
            and previously logged-in sites are still logged in.
          </li>
          <li>
            Spot-checks: §17 (drag 1:1, tab switch without reload, file
            upload, Back = history → collapse → navigation) and §12–§16
            (<code>apk update</code> in the guest, Home tiles launch apps).
          </li>
        </ol>
      </div>

      <div className="card">
        <h2>Source (version control)</h2>
        <p>
          Complete buildable source. The zip intentionally contains no
          dotfiles; full history rides in the git bundle (tip 8244772 —
          includes the complete milestone history, all six Phase 3 design
          contracts, the procfs contract, and the Phase 4 Companion design
          contract with the §22 hotfix amendment).
        </p>
        <a className="btn secondary" href="/PocketShell-v0.7.0-m4.0.1-source.zip">
          source.zip
        </a>
        <a className="btn secondary" href="/PocketShell-v0.7.0-m4.0.1-source.tar.gz">
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
        procfs contract · m4.0 Phase 4 Companion · <b>v0.7.0-m4.0.1 (this
        build): startup decoupled from WebView provider health</b>. Your
        device keeps doing the QA that matters.
      </footer>
    </main>
  );
}

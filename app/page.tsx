const VERSION = "v0.7.0-m4.0.2";

const HASHES = {
  apk: "d4b04acc3919fc6da9bd72a37590bb2587e00c900cc83202619dec0158605bb2",
  zip: "8247c6867121bfe6a18ea30f027b06d3f5ee206d71b1deeed718fc6c67b6f537",
  tgz: "2d160f1095ff1b1589a6ce16c17e4d23af0323dfebec0e3e56dc19b7064f7ab2",
  bundle: "a82f9fcb547eb21b6441a116db43783d8732c3381f1b8c333f703b0ceffed091",
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
          The Companion canvas is never mysteriously white{" "}
          <span className="badge">versionCode 26</span>
        </h2>
        <p>
          <b>The device finding (same session as the startup hotfix):</b> after
          m4.0.1 fixed the launch crash, the app starts — but pulling the
          Companion up showed the ChatGPT tab strip over a <b>pure white
          canvas</b>: no page, no error, no explanation. Every PocketShell
          surface is dark, so the white came from the WebView content side.
          Three possible causes, all device-dependent: the page can&apos;t load
          over the network/VPN, the WebView build is too old for a modern site
          (Samsung&apos;s rollback can leave an ancient factory build), or the
          updated build renders blank. A silent white rectangle is not
          acceptable — so now the canvas reports the truth.
        </p>
        <ul className="steps">
          <li>
            <b>&quot;Page didn&apos;t load&quot; card:</b> if the main frame fails
            (network, DNS, VPN), the canvas shows the REAL error string (e.g.{" "}
            <code>net::ERR_NAME_NOT_RESOLVED</code>), the installed Android
            System WebView version, the hint that matters — and a{" "}
            <b>Retry</b> button.
          </li>
          <li>
            <b>&quot;Page renderer crashed&quot; card:</b> when the WebView
            renderer dies (the classic white-canvas signature of broken
            WebView builds — Android&apos;s DEFAULT behavior here is to kill
            the whole app), PocketShell now destroys only the crashed view,
            stays alive, and says exactly that with the version + an
            update/rollback hint.
          </li>
          <li>
            <b>Retry rebuilds the tab</b> from scratch; any successful
            navigation clears the failure automatically. The
            &quot;Companion unavailable&quot; notice shows the WebView version
            too.
          </li>
          <li>
            <b>Honest boundary:</b> a page that loads but renders blank
            because an old WebView can&apos;t run its JavaScript fires no
            error event. That case is identified with the version line on the
            cards plus a static-site test (add <code>example.com</code> as a
            second Companion — it renders on ANY WebView).
          </li>
          <li>
            <b>Nothing else changed:</b> no feature, storage or contract
            change. Full suite green: 712 tests, 0 failures (4 new pins on
            the failure model — one caught a real defect before delivery).
          </li>
        </ul>
        <a className="btn" href="/PocketShell-v0.7.0-m4.0.2-debug.apk">
          Download APK (debug, 22 MB)
        </a>
        <Sha text={HASHES.apk} />
        <p className="mono" style={{ border: "none", background: "transparent", padding: 0 }}>
          signing cert SHA-256: d96a6f664d7f8d7194733672dcd6eb9f33f40a0078ab07ff66bfd605138bf659 (same as v0.4.1–v0.7.0-m4.0.2)
        </p>
      </div>

      <div className="card">
        <h2>Update — no uninstall, no runtime reinstall</h2>
        <p>
          versionCode 26 installs <b>in place over v0.7.0-m4.0.1 (25),
          v0.7.0-m4.0 (24) and every earlier pinned-cert build</b>. Your Alpine
          runtime, installed packages, Kilo/Hermes installation, the procfs
          contract, every Phase 3 behavior and all Companion data (logins
          included) are untouched. This build also contains the m4.0.1
          startup fix — it starts regardless of the WebView package&apos;s
          state.
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
            launch-crash fix).
          </li>
          <li>
            <b>v0.7.0-m4.0.2 (this build):</b> honest Companion failure
            surfaces — load errors and renderer crashes become labeled cards
            with the real cause + WebView version + Retry. Never a silent
            white rectangle.
          </li>
        </ul>
      </div>

      <div className="card">
        <h2>Quick checks (docs/TESTING.md §19 — failure-surface device gate)</h2>
        <ol className="steps">
          <li>
            Install {VERSION} in place over the current build → PocketShell
            starts; pull the Companion up with the ChatGPT tab.
          </li>
          <li>
            If the page can&apos;t load: the dark card shows &quot;Page
            didn&apos;t load&quot; + the real error + the WebView version — no
            white rectangle. If the renderer dies: &quot;Page renderer
            crashed&quot; and PocketShell survives.
          </li>
          <li>
            Tap <b>Retry</b> → the tab reloads fresh. Then add{" "}
            <code>example.com</code> as a second Companion: if it renders,
            network + WebView are fine and the ChatGPT failure is site- or
            VPN-specific; if it also fails, the WebView build/network is the
            problem.
          </li>
          <li>
            Report the WebView version shown on the card — that number
            decides whether to update (&quot;Android System WebView&quot; in
            the Play Store) or roll back (Samsung&apos;s dialog).
          </li>
          <li>
            Regressions: §18 startup checks, §17 spot-checks (drag, tabs,
            upload, Back), guest <code>apk update</code>.
          </li>
        </ol>
      </div>

      <div className="card">
        <h2>Source (version control)</h2>
        <p>
          Complete buildable source. The zip intentionally contains no
          dotfiles; full history rides in the git bundle — includes the
          complete milestone history, all six Phase 3 design contracts, the
          procfs contract, and the Phase 4 Companion design contract with the
          §22/§23 hotfix amendments.
        </p>
        <a className="btn secondary" href="/PocketShell-v0.7.0-m4.0.2-source.zip">
          source.zip
        </a>
        <a className="btn secondary" href="/PocketShell-v0.7.0-m4.0.2-source.tar.gz">
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
        procfs contract · m4.0 Phase 4 Companion · m4.0.1 startup hotfix ·{" "}
        <b>v0.7.0-m4.0.2 (this build): the Companion canvas is never
        mysteriously white</b>. Your device keeps doing the QA that matters.
      </footer>
    </main>
  );
}

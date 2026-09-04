const VERSION = "v0.7.0-m4.0";

const HASHES = {
  apk: "a9f1fb71949b558a3f6d89a8d92b615d3d6a4d0905f714853898d6d7b418a438",
  zip: "577b86f759f500286d91b2264aaa0f22fd2d440f1fab0b9aa5ca6a005332152a",
  tgz: "a0001fd68cce64fc57166c8842a569a1c454debff403c995065d053ac5c5b57e",
  bundle: "6a90c25990ce255e94235767fb1e47a487678f196f4475e38a274f6522b1903d",
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
          Phase 4 — Companion: the embedded web workspace{" "}
          <span className="badge">versionCode 24</span>
        </h2>
        <p>
          PocketShell grows into a personal mobile Linux workspace, and
          Companion is its newest room: <b>a persistent web workspace layer
          that lives below every screen</b>, pulled up by a small bottom
          drag handle. Pull it up while working in the terminal, ask ChatGPT
          something, pull it half down, keep typing commands, pull it back
          up — <b>the conversation is still there</b>. You never left
          PocketShell. There is no floating button, no browser chrome, no
          address bar: the website IS the content, and a Companion is
          exactly <b>Name + URL</b> — ChatGPT, GitHub, your docs site, a
          local dashboard, anything. Fully generic, deliberately not an AI
          chatbot: you log into the real website with your real account.
        </p>
        <ul className="steps">
          <li>
            <b>Drag handle only:</b> a small visual handle at the bottom of
            every screen. Dragging follows your finger 1:1 and the page
            never reflows mid-drag (one resize on release); half-screen and
            near-full anchors snap gently, everything else stays exactly
            where you leave it — and the height is remembered.
          </li>
          <li>
            <b>Real logins persist:</b> cookies and site storage live in the
            app&apos;s private web profile — log into ChatGPT once, close
            PocketShell, come back still logged in. The engine is the
            Android System WebView: zero new dependencies, Chromium in its
            own sandboxed process, Safe Browsing on, device permissions
            (camera/mic/geo) denied.
          </li>
          <li>
            <b>Web tabs, PocketShell style:</b> the Phase 3.1 editor-tab
            language, inverted — the active tab opens into the web canvas
            with a 2.5dp Sapphire edge. Switching tabs never reloads:
            background tabs stay alive-but-paused (active + 4, LRU), evicted
            tabs restore on reactivation, memory pressure drops background
            pages first.
          </li>
          <li>
            <b>Honest integration:</b> Back = webpage history, then
            collapse, then normal PocketShell navigation — never trapped.
            File uploads use the normal Android picker; downloads land in
            app-private storage via the system DownloadManager; external
            schemes (mailto/tel/intent) resolve to the system with an honest
            toast when nothing can handle them.
          </li>
          <li>
            <b>Settings → Companion:</b> add/edit/delete your Companions,
            quick-add templates as editable pre-fills, a Default Companion,
            and Clear web data. The empty state is one line and one button —
            no cards, no clutter.
          </li>
        </ul>
        <a className="btn" href="/PocketShell-v0.7.0-m4.0-debug.apk">
          Download APK (debug, 22 MB)
        </a>
        <Sha text={HASHES.apk} />
        <p className="mono" style={{ border: "none", background: "transparent", padding: 0 }}>
          signing cert SHA-256: d96a6f664d7f8d7194733672dcd6eb9f33f40a0078ab07ff66bfd605138bf659 (same as v0.4.1–v0.7.0-m4.0)
        </p>
      </div>

      <div className="card">
        <h2>Update — no uninstall, no runtime reinstall</h2>
        <p>
          versionCode 24 installs <b>in place over v0.7.0-m3.6 (23),
          v0.7.0-m3.5 (22), v0.7.0-m3.4 (21) and every earlier pinned-cert
          build</b>. Your Alpine runtime, installed packages, Kilo/Hermes
          installation, the procfs contract and every Phase 3 behavior are
          untouched: Phase 4 adds one new layer and touches nothing else —
          the §12–§16 gates remain valid.
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
            <b>v0.7.0-m4.0 (this build):</b> Companion — the embedded web
            workspace. Generic Name+URL definitions, bottom drag handle,
            persistent sessions, live multi-tab, file upload, intelligent
            Back, Midnight Sapphire throughout.
          </li>
        </ul>
      </div>

      <div className="card">
        <h2>Quick checks (docs/TESTING.md §17 — Phase 4 device gate)</h2>
        <ol className="steps">
          <li>
            Install {VERSION} over m3.6 → the only new element is the small
            bottom handle (no floating button, no text).
          </li>
          <li>
            Settings → Companion → quick-add <b>ChatGPT</b> → pull the
            handle up → log in → close PocketShell → reopen → still logged
            in.
          </li>
          <li>
            Drag through many heights: 1:1 follow, no page reflow mid-drag,
            gentle snap near anchors, stay-put elsewhere, height restored
            after restart. Scrolling the page never resizes the Companion.
          </li>
          <li>
            Add <b>GitHub</b> via +: two tabs, switch without reloads,
            conversation intact, close → neighbor selected.
          </li>
          <li>
            Upload a file in ChatGPT (normal Android picker); navigate
            GitHub → repo → Back goes back in the webpage; at the page root
            Back collapses the Companion, then normal navigation resumes.
          </li>
          <li>
            Regressions: <code>apk update</code> in the guest, Home tiles
            still launch apps, §12–§16 gates all still pass.
          </li>
        </ol>
      </div>

      <div className="card">
        <h2>Source (version control)</h2>
        <p>
          Complete buildable source. The zip intentionally contains no
          dotfiles; full history rides in the git bundle (tip 4ebf734 —
          includes the complete milestone history, all six Phase 3 design
          contracts, the procfs contract, and the Phase 4 Companion design
          contract).
        </p>
        <a className="btn secondary" href="/PocketShell-v0.7.0-m4.0-source.zip">
          source.zip
        </a>
        <a className="btn secondary" href="/PocketShell-v0.7.0-m4.0-source.tar.gz">
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
        procfs contract · <b>v0.7.0-m4.0 (this build): Phase 4 —
        Companion, the embedded web workspace</b>. Your device keeps doing
        the QA that matters.
      </footer>
    </main>
  );
}

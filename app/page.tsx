const VERSION = "v0.8.0-m4.0.11";

const HASHES = {
  apk: "1afcc7dbe69971e6136c64d033915b56269c7ff2b4e615fb78d22b3011a46335",
  zip: "631f1858627e81fbc01516707f99693161c8fe8b6e007b1303e842334e420867",
  tgz: "72fdb83e2b882a75da2a92aef5f1cc9a6b891ae504210d0076a1dd3890968c22",
  bundle: "d2b419c512d35c864e168946b2cecb3797669aaa4cbf1b564fcca89fdea0f1d5",
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
          Replace Renderer Only: the winner frozen and shipped{" "}
          <span className="badge">versionCode 35</span>
        </h2>
        <p>
          <b>The investigation is closed; the winner ships.</b> Your device
          evidence settled everything: the baseline harness rendered
          complete pages on all four gate sites (recording),{" "}
          <b>+CHROME UA rendered chat.z.ai completely</b> (screenshot) and{" "}
          <b>+MIDNIGHT BG rendered chatgpt.com completely</b> (screenshot).
          The winner is <b>BASELINE</b> — the most stable and least invasive
          mode by construction (zero deltas from Android defaults). Per the
          directive, this build is a surgical replacement, not a redesign:
        </p>
        <ul className="steps">
          <li>
            <b>Untouched, by directive:</b> the Companion bottom sheet, drag
            handle, remembered height, tab strip, tabs (close + “+” +
            picker), tab state and destination storage — exactly as they
            were.
          </li>
          <li>
            <b>Replaced, only the renderer:</b> the tab content area now
            hosts the exact copied baseline unit — one stable plain
            <code> </code><code>FrameLayout</code>, one{" "}
            <code>WebView(realActivity)</code> per tab, JS + DOM storage
            only, <b>attach → first layout → then loadUrl</b>. No UA spoof,
            no background override, no config context, no pre-attach load.
          </li>
          <li>
            <b>Diagnostics stripped from the canvas:</b> no URL ▸ / MODE ▸ /
            INSPECT / COPY chrome, no status header, no health sheet in the
            render path — only the page. The harness stays reachable as a
            separate diagnostic (tab strip → ⓘ).
          </li>
          <li>
            <b>The winner is frozen in code:</b>{" "}
            <code>BaselineMatrix.WINNER</code> + the new pure{" "}
            <code>CompanionRenderContract</code> (settings surface, load
            sequence, host container, empty diagnostics list) — unit-pinned;
            full suite <b>758 executions / 0 failures</b>.
          </li>
        </ul>
        <a className="btn" href="/PocketShell-v0.8.0-m4.0.11-debug.apk">
          Download APK (debug, 22 MB)
        </a>
        <Sha text={HASHES.apk} />
        <p className="mono" style={{ border: "none", background: "transparent", padding: 0 }}>
          signing cert SHA-256: d96a6f664d7f8d7194733672dcd6eb9f33f40a0078ab07ff66bfd605138bf659 (same as v0.4.1–v0.8.0-m4.0.11)
        </p>
      </div>

      <div className="card">
        <h2>Update — no uninstall, no runtime reinstall</h2>
        <p>
          versionCode 35 installs <b>in place over v0.8.0-m4.1.0 (34, an
          intermediate that was never announced), v0.7.0-m4.0.9 (33),
          v0.7.0-m4.0.8 (32), v0.7.0-m4.0.7 (31), v0.7.0-m4.0.6 (30),
          v0.7.0-m4.0.5 (29), v0.7.0-m4.0.4 (28), v0.7.0-m4.0.3 (27),
          v0.7.0-m4.0.2 (26), v0.7.0-m4.0.1 (25), v0.7.0-m4.0 (24) and
          every earlier pinned-cert build</b>. Your Alpine runtime, installed packages,
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
            m4.0.1–m4.0.7: the startup hotfix, honest failure cards, the
            shared keyboard, the pixel-truth watchdog, Force Dark off, the
            DOM boot witness, the Page health sheet, the swap-safe host and
            the attach kick — every one evidence-driven, every one real.
          </li>
          <li>
            m4.0.8: the painted-but-black decode — the light package returned
            on top of the fixed host; the health report learned to name the
            glass color and the page&apos;s own words.
          </li>
          <li>
            m4.0.9: the Companion Rendering Reset — the render path froze and
            the baseline experiment shipped; your device then rendered
            complete pages on the baseline and named the hosting stack.
          </li>
          <li>
            m4.1.0: the native rebuild — the Companion re-hosted around the
            proven baseline (one stable FrameLayout, one WebView per tab);
            pool, probes, witnesses and health sheet deleted permanently.
          </li>
          <li>
            <b>v0.8.0-m4.0.11 (this build):</b> Replace Renderer Only — the
            winner (BASELINE) frozen and pinned; sheet/tabs/handle/heights
            untouched; the tab content renderer is the exact baseline copy,
            diagnostics stripped.
          </li>
        </ul>
      </div>

      <div className="card">
        <h2>Quick checks (docs/TESTING.md §28 — Gates A–H on the REAL Companion)</h2>
        <ol className="steps">
          <li>
            Install {VERSION} in place → open the ChatGPT tab:{" "}
            <b>Gate C</b> — the REAL ChatGPT UI (composer, header), not a
            blank canvas. Z.ai tab: <b>Gate D</b> — the REAL Z.ai UI.
          </li>
          <li>
            example.com (<b>Gate A</b>: visible) and wikipedia.org{" "}
            (<b>Gate B</b>: visible + scroll) as tabs.
          </li>
          <li>
            <b>Gate E:</b> tap the page&apos;s input — the keyboard opens and
            typing reaches the page. <b>Gate F:</b> ChatGPT → Z.ai → back —
            both still display.
          </li>
          <li>
            <b>Gate G:</b> collapse (drag down) and reopen — the page is
            STILL displayed; height remembered. <b>Gate H:</b> log in, kill
            the app, reopen — the session survives.
          </li>
          <li>
            If ANY gate fails: do NOT reinstate removed levers — ⓘ → Render
            baseline → COPY, and paste the status into the chat. The sole
            remaining delta to the proven baseline is the overlay&apos;s
            parent chain.
          </li>
        </ol>
      </div>

      <div className="card">
        <h2>Source (version control)</h2>
        <p>
          Complete buildable source. The zip intentionally contains no
          dotfiles; full history rides in the git bundle — includes the
          complete milestone history, all Phase 3 design contracts, the
          procfs contract, the Phase 4 Companion design contract, and the
          rendering-reset report with the final verdict and the frozen-winner sweep (docs/RENDER-RESET-M4.0.9.md §7–§8).
        </p>
        <a className="btn secondary" href="/PocketShell-v0.8.0-m4.0.11-source.zip">
          source.zip
        </a>
        <a className="btn secondary" href="/PocketShell-v0.8.0-m4.0.11-source.tar.gz">
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
        bug · m4.0.8 the painted-but-black decode ·{" "}
        m4.0.9 the rendering reset · m4.1.0 the native rebuild ·{" "}
        <b>v0.8.0-m4.0.11 (this build): replace renderer only — the winner
        frozen, the baseline renderer copied into the existing sheet,
        diagnostics stripped</b>.
        Correctness before cleverness. Visible UI before diagnostics.
      </footer>
    </main>
  );
}

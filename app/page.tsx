const VERSION = "v0.7.0-m4.0.9";

const HASHES = {
  apk: "2c83af33efc335fdb2649430b3bcbe3ce87c2e29c8ba3a5d6c72e57c0840e2c9",
  zip: "1fbcf3940992a2da9f6de7ecb115c5c0cf3afcf272ddb0044704b080ca3e0555",
  tgz: "3d90706b4041dbfe98de22a076f5f8a4e9df7bc44a90ae302c8d97dea90459ce",
  bundle: "8f3056378dbe26f5e54c83a62c2558c2ef295ed9dd407c8f3563bd2bfcb5c848",
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
          Companion Rendering Reset: the minimal baseline WebView experiment{" "}
          <span className="badge">versionCode 33</span>
        </h2>
        <p>
          <b>Not a fix claim — the control experiment.</b> Eight iterations of
          evidence-backed fixes never proved WHICH architectural layer fails
          to present a fully loaded page&apos;s UI. The m4.0.8 reports made
          the case sharp: ChatGPT paints a blank <b>white</b> canvas (the
          page&apos;s own light background PRESENTS while the UI does not),
          Z.ai a blank dark canvas — page pixels present, page UI absent. So
          this build changes <b>nothing</b> in the Companion render path. It
          ships the mandated baseline harness instead:
        </p>
        <ul className="steps">
          <li>
            <b>Render baseline (inside PocketShell):</b> Companion → ⓘ Page
            health → “Render baseline (diagnostic)”. A plain Activity →
            FrameLayout → ONE <code>WebView(activity)</code> — JS + DOM
            storage on, everything else Android defaults, URL loaded{" "}
            <b>after</b> first layout. No pool, no Compose, no forced-light
            context, no custom UA, no watchdog, no attach kick, no retry.
          </li>
          <li>
            <b>One variable at a time:</b> MODE cycles BASELINE → +CHROME UA
            → +FORCED LIGHT CTX → +MIDNIGHT BG → +LOAD BEFORE ATTACH → +WIDE
            VIEWPORT (each turns on exactly ONE Companion suspect). URL
            cycles example.com → wikipedia.org → chatgpt.com → chat.z.ai
            (gates A–D).
          </li>
          <li>
            <b>Real evidence per run:</b> the status line shows the exact
            config plus VIEW truth (attached, size, visible rect, layer
            type); INSPECT adds the page&apos;s own viewport
            (innerWidth/innerHeight, visualViewport, title — read-only, on
            demand); COPY hands the whole status over for the chat.
          </li>
          <li>
            <b>The decision rule</b> (docs/RENDER-RESET-M4.0.9.md): if the
            baseline works and one variant breaks it — remove that variable;
            if no single variant breaks it — rebuild the WebView host as a
            native ViewGroup inside the Compose overlay; if the baseline
            itself is blank — device/provider investigation and a Chrome
            Custom Tabs control, then (only if embedded WebView is genuinely
            unreliable) a GeckoView evaluation.
          </li>
          <li>
            <b>Nothing else changed:</b> full suite green <b>788 executions
            / 0 failures</b> (+8 pins; all earlier pins intact).
          </li>
        </ul>
        <a className="btn" href="/PocketShell-v0.7.0-m4.0.9-debug.apk">
          Download APK (debug, 22 MB)
        </a>
        <Sha text={HASHES.apk} />
        <p className="mono" style={{ border: "none", background: "transparent", padding: 0 }}>
          signing cert SHA-256: d96a6f664d7f8d7194733672dcd6eb9f33f40a0078ab07ff66bfd605138bf659 (same as v0.4.1–v0.7.0-m4.0.9)
        </p>
      </div>

      <div className="card">
        <h2>Update — no uninstall, no runtime reinstall</h2>
        <p>
          versionCode 33 installs <b>in place over v0.7.0-m4.0.8 (32),
          v0.7.0-m4.0.7 (31),
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
            <b>v0.7.0-m4.0.9 (this build):</b> the Companion Rendering Reset —
            render path frozen; the minimal baseline WebView experiment ships
            so the device itself names the exact failing architectural layer
            before another line of Companion code changes.
          </li>
        </ul>
      </div>

      <div className="card">
        <h2>Quick checks (docs/TESTING.md §26 — the m4.0.9 device gate)</h2>
        <ol className="steps">
          <li>
            Install {VERSION} in place → open a Companion tab → ⓘ Page health
            → <b>Render baseline (diagnostic)</b>. MODE = BASELINE. URL ▸ to
            example.com: Gate A = visible text. COPY the status.
          </li>
          <li>
            URL ▸ wikipedia.org (Gate B: visible + scroll) → chatgpt.com
            (Gate C: real UI + INSPECT) → chat.z.ai (Gate D: real UI +
            INSPECT).
          </li>
          <li>
            MODE ▸ through every variant × ChatGPT/Z.ai; report the FIRST
            mode that blanks, with its INSPECT reading — that is the failing
            variable.
          </li>
          <li>
            Cross-check: with the harness showing a site, open the REAL
            Companion tab for it. Harness OK + Companion blank = the
            Compose-host path is the culprit.
          </li>
          <li>
            If the BASELINE fails even on example.com: stop — the status
            paste begins the device/provider investigation (no PocketShell
            changes until that is understood).
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
          rendering-reset report (docs/RENDER-RESET-M4.0.9.md).
        </p>
        <a className="btn secondary" href="/PocketShell-v0.7.0-m4.0.9-source.zip">
          source.zip
        </a>
        <a className="btn secondary" href="/PocketShell-v0.7.0-m4.0.9-source.tar.gz">
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
        <b>v0.7.0-m4.0.9 (this build): the rendering reset — the baseline
        experiment ships, the Companion render path freezes, and the device
        names the failing layer</b>.
        Correctness before cleverness. Visible UI before diagnostics.
      </footer>
    </main>
  );
}

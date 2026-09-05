const VERSION = "v0.9.1-m5.1.0";

const HASHES = {
  apk: "d8084b49f7af17b55cff643fc41ec0b56484b6830da219c11b4c2f56c3d56482",
  zip: "1dcee490a87299bb493b016a91782144413d7cc8fe0b5498c11847e092ed8dda",
  tgz: "f11ff7f3bf5d89f0e3b15ed6876ff0539f8f2f2fd1b3345860cd5f6bb8fa0fa1",
  bundle: "083253e4230208d8e8bc80883ec1dd42a1866b7533460a48d7d0b338041cba3e",
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
          M5.1: ARM64 Performance &amp; Architecture Optimization{" "}
          <span className="badge">versionCode 39</span>
        </h2>
        <p>
          <b>Audit-first: the real architecture was measured and read
          end-to-end before any change; only what the evidence supports
          changed; nothing working broke.</b>
        </p>
        <ul className="steps">
          <li>
            <b>The minimized Companion is silent:</b> collapsing the sheet
            used to leave the active tab running JavaScript, timers and
            layout at full rate while completely invisible. Now collapse →
            pause EVERYTHING, raise → wake only the active tab. No reload,
            no state loss, still logged in.
          </li>
          <li>
            <b>Home no longer spawns a guest shell on every visit:</b> the
            command-app probe is a real proot exec; a 60s freshness window +
            an in-flight guard gate the visibility-triggered probe (installs
            still force a fresh answer; failures always re-probe).
          </li>
          <li>
            <b>Background terminal output no longer repaints the screen:</b>
            a session streaming output in the background used to force full
            repaints of the unchanged visible screen — N sessions multiplied
            the load. Only the VISIBLE session&apos;s output repaints now.
          </li>
          <li>
            <b>Web state persisted under memory pressure:</b> cookies flush
            on system memory-pressure signals while Companion tabs are
            alive — strictly gated on the provider already being loaded.
          </li>
          <li>
            <b>Already sound, deliberately untouched:</b> lazy startup; one
            WebView per tab (never recreated/reloaded on switch); WebView
            height frozen during drags; background tabs platform-paused;
            scrollback capped at 2000 rows; one Linux process per session;
            honest FGS; keyboard allocations trivial. saveState/restore and
            LRU eviction stay retired (the m4.0.11 verdict) — no user state
            is ever destroyed behind their back.
          </li>
        </ul>
        <a className="btn" href="/PocketShell-v0.9.1-m5.1.0-debug.apk">
          Download APK (debug, 22 MB)
        </a>
        <Sha text={HASHES.apk} />
        <p className="mono" style={{ border: "none", background: "transparent", padding: 0 }}>
          signing cert SHA-256: d96a6f664d7f8d7194733672dcd6eb9f33f40a0078ab07ff66bfd605138bf659 (same as v0.4.1–v0.9.1-m5.1.0)
        </p>
      </div>

      <div className="card">
        <h2>Update — no uninstall, no runtime reinstall</h2>
        <p>
          versionCode 39 installs <b>in place over v0.9.0-m5.0.1 (38),
          v0.9.0-m5.0.0 (37),
          v0.8.0-m4.0.12 (36), v0.8.0-m4.0.11 (35),
          v0.8.0-m4.1.0 (34, an intermediate that was never announced),
          v0.7.0-m4.0.9 (33), v0.7.0-m4.0.8 (32), v0.7.0-m4.0.7 (31),
          v0.7.0-m4.0.6 (30), v0.7.0-m4.0.5 (29), v0.7.0-m4.0.4 (28),
          v0.7.0-m4.0.3 (27), v0.7.0-m4.0.2 (26), v0.7.0-m4.0.1 (25),
          v0.7.0-m4.0 (24) and every earlier pinned-cert build</b>. Your
          Alpine runtime, installed packages, Kilo/Hermes installation, the
          procfs contract, every Phase 3 behavior and all Companion data
          (logins included) are untouched. This build also contains the
          m4.0.1 startup fix — it starts regardless of the WebView
          package&apos;s state.
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
            v0.8.0-m4.0.11: Replace Renderer Only — the winner (BASELINE)
            frozen and pinned; sheet/tabs/handle/heights untouched; the tab
            content renderer is the exact baseline copy, diagnostics
            stripped.
          </li>
          <li>
            <b>v0.9.0-m5.0.0:</b> UI &amp; Interaction Polish — the drag bar
            is 2× wider and a single TAP minimizes the Companion at any
            height; heights are FREE (no snap points); the Home FAB and the
            duplicate CLI Apps menu are gone; compact tabs with an
            integrated “+”; the keyboard toggle sits in the bottom-right
            corner; and the full Light Theme shipped (System/Light/Dark/
            AMOLED) — websites still theme themselves.
          </li>
          <li>
            <b>v0.9.0-m5.0.1:</b> M5.0 Final UI Correction — the workspace
            header is gone, back lives in the tab bar, tabs are
            significantly more compact (both strips, ellipsis + active tab
            auto-scroll), and at ≥90% height the Companion tab strip also
            drags the sheet (touch-slop gated; taps stay taps).
          </li>
          <li>
            <b>v0.9.1-m5.1.0 (this build):</b> M5.1 ARM64 Performance &amp;
            Architecture Optimization — audit-first; the minimized Companion
            is silent (all WebViews paused, active wakes on raise); Home no
            longer spawns a guest shell per visit; background terminal
            output no longer repaints the screen; web state flushes under
            memory pressure. Nothing working changed.
          </li>
        </ul>
      </div>

      <div className="card">
        <h2>Quick checks (docs/TESTING.md §32 — the m5.1 gates)</h2>
        <ol className="steps">
          <li>
            <b>Silent minimize:</b> load ChatGPT, minimize the sheet, work
            in the terminal for a few minutes → raise: the page is exactly
            as you left it (no reload, still logged in); the device stays
            cool while minimized.
          </li>
          <li>
            <b>Home revisits:</b> Home ↔ Terminal repeatedly → instant
            renders, no tool-grid churn; after an install the tools grid
            still updates (forced probe).
          </li>
          <li>
            <b>Background output:</b> run `yes` in session 1, switch to an
            idle session 2, type/read → smooth; switch back → correct.
          </li>
          <li>
            <b>Nothing broke:</b> the §31 workspace bar + compact tabs, the
            keyboard everywhere, themes, ChatGPT + Z.ai render/scroll/login,
            refresh/hard-refresh — all unchanged.
          </li>
          <li>
            <b>Startup:</b> force-stop → cold start → use only the terminal:
            no crash, no provider loading (the m4.0.1 rule holds).
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
        <a className="btn secondary" href="/PocketShell-v0.9.1-m5.1.0-source.zip">
          source.zip
        </a>
        <a className="btn secondary" href="/PocketShell-v0.9.1-m5.1.0-source.tar.gz">
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
        m4.0.9 the rendering reset · m4.1.0 the native rebuild · m4.0.11
        replace renderer only — the winner frozen · m4.0.12 companion
        finalization · m5.0.0 UI &amp; interaction polish · m5.0.1 the
        workspace bar ·{" "}
        <b>v0.9.1-m5.1.0 (this build): M5.1 ARM64 performance &amp;
        architecture optimization — audit-first, four surgical fixes</b>.
        Correctness before cleverness. Visible UI before diagnostics.
      </footer>
    </main>
  );
}

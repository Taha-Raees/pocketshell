const VERSION = "v0.9.0-m5.0.0";

const HASHES = {
  apk: "b6b9d121c98a7367de6ee9d767dcab99ef438920f233aa21da9dde8edca3efc5",
  zip: "82a435ad5a61dd6d03ff7ad86c1788ef8543b0cff753f57230c68f0efeeba896",
  tgz: "994c8ef2df8baf73f0598d95255ebd3cc88ea071249e9accd0309e9bf5840051",
  bundle: "643fd905d74eb4311ccb5f14116ac4d139f55e9bc94ee33c1002215fa83fb5f3",
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
          UI &amp; Interaction Polish: free-position Companion, decluttered
          Home, Light Theme done fully{" "}
          <span className="badge">versionCode 37</span>
        </h2>
        <p>
          <b>A refinement phase — no redesign, no new features, the working
          Companion implementation untouched.</b> Remove redundancy, reduce
          wasted space, improve interaction, finish theming:
        </p>
        <ul className="steps">
          <li>
            <b>Companion drag bar, exact behavior:</b> the visible bar is
            <b> 2× wider</b> (72×4dp, still slim) in the same 40dp invisible
            full-width touch zone — and a <b>single tap minimizes</b> the
            raised Companion at ANY height (25%, 50%, 80%, near-full).
            Restore is drag-up only; no floating button.
          </li>
          <li>
            <b>Free positioning:</b> the snap windows are retired. Height
            changes ONLY by dragging; release stays EXACTLY where you leave
            it — any fraction, no forced 25/50/75/full anchors. Drag to the
            bar → minimized (unchanged).
          </li>
          <li>
            <b>Home decluttered:</b> the floating action button is REMOVED
            (sessions are created in the Terminal&apos;s own "+"), and the
            duplicate CLI Apps dropdown is retired — the “Your tools” grid
            is the one path. Nothing functional lost.
          </li>
          <li>
            <b>Compact workspace chrome:</b> tabs and strips slimmed (40dp
            strip, tighter gaps/paddings, horizontally scrollable as
            before); the terminal “+” is integrated into the strip (no
            circle plate); padding trimmed app-wide — Home, Packages,
            Settings, the system-page kit — touch targets kept ≥44dp.
          </li>
          <li>
            <b>Light Theme, full:</b> System / Light / Dark / AMOLED, all
            live. Switching is immediate and persisted (no restart). The
            terminal canvas stays dark (it is a terminal) and websites keep
            their own themes — PocketShell never injects a theme into
            Companion pages. The ONE keyboard is untouched; its [⌨] toggle
            now sits anchored at the bottom-right corner on every screen.
          </li>
          <li>
            <b>Untouched, the freeze held:</b> renderer, sheet mechanics,
            tab system, pool, refresh/hard-refresh, session persistence,
            keyboard internals. Full suite <b>750 executions / 0
            failures</b>.
          </li>
        </ul>
        <a className="btn" href="/PocketShell-v0.9.0-m5.0.0-debug.apk">
          Download APK (debug, 22 MB)
        </a>
        <Sha text={HASHES.apk} />
        <p className="mono" style={{ border: "none", background: "transparent", padding: 0 }}>
          signing cert SHA-256: d96a6f664d7f8d7194733672dcd6eb9f33f40a0078ab07ff66bfd605138bf659 (same as v0.4.1–v0.9.0-m5.0.0)
        </p>
      </div>

      <div className="card">
        <h2>Update — no uninstall, no runtime reinstall</h2>
        <p>
          versionCode 36 installs <b>in place over v0.8.0-m4.0.11 (35),
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
            <b>v0.9.0-m5.0.0 (this build):</b> UI &amp; Interaction Polish —
            the drag bar is 2× wider and a single TAP minimizes the Companion
            at any height; heights are FREE (no snap points); the Home FAB and
            the duplicate CLI Apps menu are gone; compact tabs with an
            integrated “+”; the keyboard toggle sits in the bottom-right
            corner; and the full Light Theme shipped (System/Light/Dark/
            AMOLED) — websites still theme themselves. The Companion
            implementation: untouched.
          </li>
        </ul>
      </div>

      <div className="card">
        <h2>Quick checks (docs/TESTING.md §30 — the Phase 5 gates)</h2>
        <ol className="steps">
          <li>
            <b>Drag bar:</b> raise the Companion → tap the 2× bar → it
            minimizes IMMEDIATELY at any height; drag up to restore; drag
            and release at ~33% / ~61% / ~85% — it STAYS exactly there
            (no snap points).
          </li>
          <li>
            <b>Home:</b> no floating button, no CLI Apps dropdown — the
            tools grid launches the same apps; sessions are created from
            the Terminal’s own “+”. Nothing else changed.
          </li>
          <li>
            <b>Light theme:</b> Settings → Appearance → Light — the whole
            app flips instantly and stays Light after a restart. The
            terminal canvas stays dark (it is a terminal) and ChatGPT/Z.ai
            keep their own themes.
          </li>
          <li>
            <b>Keyboard toggle:</b> hide the deck — the [⌨] icon now sits
            anchored at the bottom-right corner on every screen; tap it to
            bring the ONE keyboard back (the Android keyboard never
            appears).
          </li>
          <li>
            <b>Regression:</b> ChatGPT + Z.ai still render their real UIs;
            refresh (tap) / hard refresh (hold) still act on the active tab
            only and keep you logged in.
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
        <a className="btn secondary" href="/PocketShell-v0.9.0-m5.0.0-source.zip">
          source.zip
        </a>
        <a className="btn secondary" href="/PocketShell-v0.9.0-m5.0.0-source.tar.gz">
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
        finalization ·{" "}
        <b>v0.9.0-m5.0.0 (this build): UI &amp; interaction polish —
        free-position Companion, tap-to-minimize drag bar, decluttered
        Home, compact chrome, Light Theme done fully</b>.
        Correctness before cleverness. Visible UI before diagnostics.
      </footer>
    </main>
  );
}

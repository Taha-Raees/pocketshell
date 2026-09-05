const VERSION = "v0.8.0-m4.0.12";

const HASHES = {
  apk: "679dff59a5290260dbf543209bf0eb5c9df7b1cc062b0d743c69f50c7e62f990",
  zip: "2752f2ff8cf653904e6c0957042b9f2134276823714ddbaffe7fca80d0c88db9",
  tgz: "3ffe35e4db2ed3d713ba8329bfba66d47688d749affc6a214d8494d961347988",
  bundle: "ed44868d4d83188facf761bef975d591d748d7f12348d3ea9fe0c06437a4baaa",
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
          Companion Finalization: cleanup, polish, ONE keyboard{" "}
          <span className="badge">versionCode 36</span>
        </h2>
        <p>
          <b>A surgical cleanup and polish pass — the working renderer was
          not modified.</b> The proven baseline renderer stays byte-identical
          to m4.0.11. Five finalization items executed around it:
        </p>
        <ul className="steps">
          <li>
            <b>All diagnostics removed, completely:</b> the ⓘ chip, its
            launch path, the harness Activity and its matrix are DELETED
            (code + manifest). The Companion shows only the real website —
            no headers, readouts or test buttons anywhere. The frozen render
            contract is unchanged; the winner is pinned by value.
          </li>
          <li>
            <b>Refresh + hard refresh:</b> the ↻ glyph in the tab strip.
            Tap = plain reload of the <b>active tab only</b> (same URL, same
            tab, other tabs untouched). Long-press = <b>hard refresh</b> —
            the freshest possible reload that is NOT a data reset (one
            transient cache-bypass, restored on page finish; cookies,
            logins and other tabs preserved; haptic + “Hard reloading…”).
          </li>
          <li>
            <b>Drag handle, easier to grab:</b> the visible bar is unchanged
            (36×4dp); the invisible full-width touch zone grew 28→40dp.
          </li>
          <li>
            <b>ONE PocketShell keyboard, everywhere:</b> the deck now lives
            at the app root — the same keyboard over Terminal, Linux, CLI
            Apps, Home, and the Companion over all of them. The
            Android/Samsung keyboard is hard-blocked for the app&apos;s
            lifetime (it used to leak back when the deck was hidden). Real
            KeyEvents only — no JavaScript hacks; even the Companion
            settings Name/URL fields are served by the same deck.
            WebView-input focus auto-opens it; the bottom-right [⌨] toggle
            works on every screen; closing the Companion restores the
            terminal&apos;s focus cleanly.
          </li>
          <li>
            <b>Untouched, the freeze held:</b> renderer, sheet, drag
            mechanics, remembered height, tab system, tab state,
            destination storage, navigation, provider management. Full
            suite <b>750 executions / 0 failures</b>.
          </li>
        </ul>
        <a className="btn" href="/PocketShell-v0.8.0-m4.0.12-debug.apk">
          Download APK (debug, 22 MB)
        </a>
        <Sha text={HASHES.apk} />
        <p className="mono" style={{ border: "none", background: "transparent", padding: 0 }}>
          signing cert SHA-256: d96a6f664d7f8d7194733672dcd6eb9f33f40a0078ab07ff66bfd605138bf659 (same as v0.4.1–v0.8.0-m4.0.12)
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
            <b>v0.8.0-m4.0.12 (this build):</b> Companion Finalization —
            diagnostics retired completely (harness deleted), refresh +
            long-press hard refresh, bigger invisible drag-handle touch
            zone, and ONE PocketShell keyboard over every screen with the
            system IME permanently blocked. The renderer: byte-identical.
          </li>
        </ul>
      </div>

      <div className="card">
        <h2>Quick checks (docs/TESTING.md §29 — the finalization gates)</h2>
        <ol className="steps">
          <li>
            Install {VERSION} in place → the Companion shows ONLY the real
            website: no ⓘ chip, no harness — the diagnostics are gone.
            ChatGPT + Z.ai still render their REAL UIs (the freeze).
          </li>
          <li>
            <b>Refresh:</b> tap ↻ — the active tab reloads, same URL, other
            tabs intact. <b>Hard refresh:</b> hold ↻ (haptic + "Hard
            reloading…") — fresh load, still logged in, session kept.
          </li>
          <li>
            <b>ONE keyboard:</b> tap the ChatGPT message box → the
            PocketShell deck opens by itself and typing reaches the page;
            the Samsung keyboard never appears. Same over Z.ai, and in the
            Companion settings Name/URL fields.
          </li>
          <li>
            <b>Handle:</b> drag the bar — easier to grab (invisible 40dp
            zone), same small look, smooth 1:1 drag, height remembered.
          </li>
          <li>
            <b>Toggle + focus:</b> hide the deck via [⌨] → the bottom-right
            button brings it back on EVERY screen; close the Companion →
            the terminal receives typing again, cleanly.
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
        <a className="btn secondary" href="/PocketShell-v0.8.0-m4.0.12-source.zip">
          source.zip
        </a>
        <a className="btn secondary" href="/PocketShell-v0.8.0-m4.0.12-source.tar.gz">
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
        replace renderer only — the winner frozen ·{" "}
        <b>v0.8.0-m4.0.12 (this build): companion finalization —
        diagnostics retired, refresh + hard refresh, easier drag handle,
        ONE keyboard everywhere, system IME blocked</b>.
        Correctness before cleverness. Visible UI before diagnostics.
      </footer>
    </main>
  );
}

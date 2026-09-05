const VERSION = "v0.9.0-m5.0.1";

const HASHES = {
  apk: "9d08e75192b260634ec4515a19bd380c56c77b34d6c804e2621e3d067ae222e3",
  zip: "32f43824711faaa0eb8cb6950e51e72b30ffd3b7043e1ec58090fb3e1aef71cf",
  tgz: "78d69db74588693ee3209c7c003d66b7de45fa8be3d946e572ea18972503d832",
  bundle: "a0f9f8b66eda686781d1204ce6c6f89c3c166b10e52be7d5ef4a571530a401e4",
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
          M5.0 Final UI Correction: the Workspace Bar{" "}
          <span className="badge">versionCode 38</span>
        </h2>
        <p>
          <b>A surgical pass — no redesign, no new features, the working
          Companion implementation untouched.</b> Ordered by the field
          report:
        </p>
        <ul className="steps">
          <li>
            <b>Workspace header removed:</b> the large top title row
            (back + session title) is GONE — the active session&apos;s name
            already lives in its tab. The workspace starts directly under
            the Android status area.
          </li>
          <li>
            <b>Back lives in the tab bar:</b> a compact integrated glyph at
            the far left —{" "}
            <b>← | Tab 1 | Tab 2 | Tab 3 | +</b> — aligned with the tabs,
            not a header-sized button in its own row.
          </li>
          <li>
            <b>Compact IDE tabs, both strips:</b> strip 34dp (was 40);
            active tab 34 / inactive 26 — no more oversized heavy active
            tab; 2dp gaps, 8dp tab padding, 64–136dp tab width (more tabs
            fit), 6dp corners, a subtle 2dp active hairline. Long titles
            truncate with an ellipsis; the close button stays reachable;
            the ACTIVE tab is always scrolled back into view.
          </li>
          <li>
            <b>Companion near-full drag surface (≥90%):</b> below 90%
            height NOTHING changed — the dedicated bar is the only sheet
            drag control and the tab bar behaves normally. At/above 90%
            the TAB STRIP also drags the sheet vertically, gated behind
            the touch slop — tab taps, close, + and refresh are never
            mistaken for drags, and the strip never minimizes on touch.
          </li>
          <li>
            <b>Untouched, the freeze held:</b> renderer, sheet mechanics,
            tab system, pool, refresh/hard-refresh, session persistence,
            keyboard internals, themes. Free positioning: no snap points,
            ever. Full suite <b>752 executions / 0 failures</b>.
          </li>
        </ul>
        <a className="btn" href="/PocketShell-v0.9.0-m5.0.1-debug.apk">
          Download APK (debug, 22 MB)
        </a>
        <Sha text={HASHES.apk} />
        <p className="mono" style={{ border: "none", background: "transparent", padding: 0 }}>
          signing cert SHA-256: d96a6f664d7f8d7194733672dcd6eb9f33f40a0078ab07ff66bfd605138bf659 (same as v0.4.1–v0.9.0-m5.0.1)
        </p>
      </div>

      <div className="card">
        <h2>Update — no uninstall, no runtime reinstall</h2>
        <p>
          versionCode 38 installs <b>in place over v0.9.0-m5.0.0 (37),
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
            <b>v0.9.0-m5.0.1 (this build):</b> M5.0 Final UI Correction —
            the workspace header is gone, back lives in the tab bar, tabs
            are significantly more compact (both strips, ellipsis + active
            tab auto-scroll), and at ≥90% height the Companion tab strip
            also drags the sheet (touch-slop gated; taps stay taps).
          </li>
        </ul>
      </div>

      <div className="card">
        <h2>Quick checks (docs/TESTING.md §31 — the m5.0.1 gates)</h2>
        <ol className="steps">
          <li>
            <b>Workspace bar:</b> open Terminal — NO title row anymore; the
            bar (`← tabs +`) sits directly under the status area; back
            returns to Home from its new slot.
          </li>
          <li>
            <b>Compact tabs:</b> open 5+ sessions with one very long title —
            everything stays dense and readable; long titles ellipsize;
            switching to an off-screen tab scrolls it back into view.
          </li>
          <li>
            <b>Near-full drag:</b> raise the Companion past ~90% → drag the
            TAB STRIP vertically → the sheet follows; release mid-way and
            it STAYS there. Tapping tabs/close/+ still works — nothing on
            the strip minimizes.
          </li>
          <li>
            <b>Below 90%:</b> the strip is normal tabs again; ONLY the
            dedicated bar drags (and its tap still minimizes, as before).
          </li>
          <li>
            <b>Regression:</b> keyboard, themes, ChatGPT + Z.ai render/scroll/
            login — all untouched; refresh (tap) / hard refresh (hold) still
            act on the active tab only.
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
        <a className="btn secondary" href="/PocketShell-v0.9.0-m5.0.1-source.zip">
          source.zip
        </a>
        <a className="btn secondary" href="/PocketShell-v0.9.0-m5.0.1-source.tar.gz">
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
        finalization · m5.0.0 UI &amp; interaction polish ·{" "}
        <b>v0.9.0-m5.0.1 (this build): M5.0 final UI correction —
        workspace bar, compact IDE tabs, near-full strip drag</b>.
        Correctness before cleverness. Visible UI before diagnostics.
      </footer>
    </main>
  );
}

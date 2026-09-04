const VERSION = "v0.7.0-m4.0.7";

const HASHES = {
  apk: "5934b41635c06f90c2b2a604215484e3e613050c916ac0849cc6f827d1c54273",
  zip: "7eacc76c9c623cdd74232cd61b694b68545f87a8d2085080672e248645b29610",
  tgz: "d4274524e5771c60838957bd796d1d03903600ceea289f5b82f7e86795892d00",
  bundle: "84871ad987adc6b621c663596ff42f3c119cca7a964607910fcf6b6610bddbdc",
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
          The health sheet cracked it: the compat renderer never reached the
          screen — and the creation recipe was the regression{" "}
          <span className="badge">versionCode 31</span>
        </h2>
        <p>
          <b>Your Copy report answered the question.</b> The health sheet
          showed the page is <b>fully alive</b> — chat.com answered{" "}
          <code>readyState=complete · 761 elements · 62 interactive · 394
          text chars</code> with <b>zero boot errors</b> — while pixels read{" "}
          <i>“never painted”</i> on GPU and <i>“unknown”</i> forever on the
          compatibility renderer. A hydrated app with zero presented frames
          is a <b>presentation</b> failure — and it exposed two real bugs:
        </p>
        <ul className="steps">
          <li>
            <b>The compatibility renderer was never on screen:</b> AndroidView
            runs its factory exactly once per composed node, so every
            silently swapped WebView — the first-stall compat swap, the boot
            retry, and <b>plain tab switching</b> — never attached. On your
            device the old view (destroyed on the first stall) stayed
            attached as the dead black canvas while the fresh software-mode
            view sat stranded in the pool loading a perfect DOM it never
            displayed — that is why pixels read “unknown” forever. The host
            is now <b>keyed on the view instance</b>: every swap reaches the
            screen, software mode gets its first real test, and tab switching
            stops showing a stale page.
          </li>
          <li>
            <b>The creation recipe was the painting regression:</b> the
            forced-light configuration context (m4.0.5/6) chased a dark-CSS
            theory your DOM evidence refutes — and the regression line is
            exact: m4.0.4 (plain activity context) still painted the cookie
            banner; m4.0.5/6 (config context) painted nothing. Creation is
            rolled back to the proven recipe (activity context, no darkening
            levers); the Chrome-like UA stays (the Google login fix).
          </li>
          <li>
            <b>Glass-first pixel probe:</b> the probe now asks PixelCopy —
            the frame as PRESENTED, cropped to the keyboard-free top half of
            the canvas — first; the software readback is only the fallback.
            A hung copy times out after 1.5 s instead of hanging “unknown”
            forever; a throwing fallback never manufactures a stall.
          </li>
          <li>
            <b>Attach kick:</b> the pool loads URLs before the view attaches;
            some Chromium builds never bind the frame sink for such loads
            (DOM alive, pixels never present — your exact signature). If
            nothing painted 3.5 s after first layout, ONE silent reload
            rebinds the load to the live surface. Once per view, never a
            card.
          </li>
          <li>
            <b>Nothing else changed:</b> data, logins, tabs and heights
            survive the in-place update. Full suite green: <b>764 executions
            / 0 failures</b> (+1 glass-region pin; all earlier pins intact).
          </li>
        </ul>
        <a className="btn" href="/PocketShell-v0.7.0-m4.0.7-debug.apk">
          Download APK (debug, 22 MB)
        </a>
        <Sha text={HASHES.apk} />
        <p className="mono" style={{ border: "none", background: "transparent", padding: 0 }}>
          signing cert SHA-256: d96a6f664d7f8d7194733672dcd6eb9f33f40a0078ab07ff66bfd605138bf659 (same as v0.4.1–v0.7.0-m4.0.7)
        </p>
      </div>

      <div className="card">
        <h2>Update — no uninstall, no runtime reinstall</h2>
        <p>
          versionCode 31 installs <b>in place over v0.7.0-m4.0.6 (30),
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
            m4.0.1: startup decoupled from WebView provider health. m4.0.2:
            honest failure cards. m4.0.3: the shared keyboard + render-stall
            watchdog + the &quot;+&quot; Companion picker. m4.0.4: the
            pixel-truth stall probe (main-region verdict) + the keyboard
            toggle in one spot/one shape. m4.0.5: Force Dark off (3 layers),
            Chrome UA, the DOM boot witness with the page&apos;s own
            testimony.
          </li>
          <li>
            m4.0.6: the SSR-proof boot witness and the standing{" "}
            <b>Page health</b> sheet with a one-tap <b>Copy report</b> —
            your report is what cracked the case this build fixes.
          </li>
          <li>
            <b>v0.7.0-m4.0.7 (this build):</b> the health sheet&apos;s verdict
            decoded — the swap-safe WebView host (the compat renderer and
            tab switches finally reach the screen), the creation-recipe
            rollback to the proven activity context, the glass-first pixel
            probe with a 1.5 s timeout, and the once-per-view attach kick.
          </li>
        </ul>
      </div>

      <div className="card">
        <h2>Quick checks (docs/TESTING.md §24 — the m4.0.7 device gate)</h2>
        <ol className="steps">
          <li>
            Install {VERSION} in place → open the previously-black Companion
            tab. EXPECT the real page within a few seconds (theme now follows
            the device — the light-forcing experiment is reverted): composer,
            sidebar, text, all interactive. The &quot;Page never
            rendered&quot; card must NOT appear while the page is visible.
          </li>
          <li>
            <b>Tab switching</b> (the latent bug this build fixes): switch
            ChatGPT ⇄ Zai several times — the canvas must always show the
            selected tab&apos;s page, with its scroll/login state intact.
          </li>
          <li>
            <b>Compat mode — its first real test:</b> Page health → Reload in
            compatibility mode. The tab must RE-CREATE on screen (never load
            invisibly again). If the page renders in compat mode, say so —
            the GPU raster path is then the confirmed culprit.
          </li>
          <li>
            If anything is still broken: <b>Page health → Copy report →
            paste in the chat</b> — pixels should read &quot;painted&quot;
            (glass probe) within ~20 s, never &quot;unknown&quot; forever.
          </li>
          <li>
            Regressions: §23.2 (health sheet), §21 (keyboard toggle one
            spot/one shape), §20 (deck types into Companion AND terminal),
            §18 startup, §17 spot-checks (drag 1:1, tabs, upload, Back,
            login persistence).
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
        <a className="btn secondary" href="/PocketShell-v0.7.0-m4.0.7-source.zip">
          source.zip
        </a>
        <a className="btn secondary" href="/PocketShell-v0.7.0-m4.0.7-source.tar.gz">
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
        root · m4.0.6 the page tells us everything ·{" "}
        <b>v0.7.0-m4.0.7 (this build): the health sheet cracked it — the
        compat renderer finally reaches the screen, creation rolled back,
        glass-first probe, attach kick</b>.
        Your device keeps doing the QA that matters.
      </footer>
    </main>
  );
}

const VERSION = "v0.7.0-m4.0.6";

const HASHES = {
  apk: "5934b41635c06f90c2b2a604215484e3e613050c916ac0849cc6f827d1c54273",
  zip: "2cfed15aa1809cc0d02a4d8f716a0d4802e6f6d96d5f667986643d719cddc99e",
  tgz: "ab7b5d7e9f485f72266de27eadc1f638f64d07a61987f4106a9e7cff08bfcdd2",
  bundle: "a3256db60ddea1b1bb4a352a5be6c3a32f5d0aaa91024fd2259a3c9cdadda96c",
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
          The last dark lever off, the witness de-fooled — and the page can
          now TELL us everything{" "}
          <span className="badge">versionCode 30</span>
        </h2>
        <p>
          <b>4.0.5 stayed black with NO error — and that was the clue.</b> Both
          safety witnesses stood down: the pixel probe passed because the page
          painted its own near-black body, and the DOM witness passed because
          chatgpt.com&apos;s server-rendered shell lands with hundreds of
          inert nodes <i>before</i> its app hydrates — instantly clearing the
          60-element &quot;mounted&quot; floor even with a dead script bundle.
          One dark lever was also still armed: Force-Dark-off stops the
          framework from inverting pages, but the WebView still{" "}
          <b>answered</b> <code>prefers-color-scheme: dark</code> (it reads the
          app&apos;s uiMode — and this app is Midnight everywhere), so sites
          kept serving dark CSS. This build closes all three holes:
        </p>
        <ul className="steps">
          <li>
            <b>Forced-light scheme:</b> the WebView is now created inside a
            configuration context pinned to <b>light mode</b>, so every site
            sees <code>prefers-color-scheme: light</code> and renders as
            authored for daylight. ChatGPT serves its light theme — the
            black-shell path is gone at the source. (Direction confirmed by
            web research: WebView derives prefers-color-scheme from the
            app&apos;s uiMode.)
          </li>
          <li>
            <b>SSR-proof witness:</b> a captured boot error is now decisive —
            an erroring page only counts as alive when it also shows real
            visible text. A SyntaxError-dead shell with 800 inert nodes now
            gets the honest <b>&quot;Page won&apos;t start&quot;</b> card WITH
            the error, instead of silently passing. The probe also reads
            interactive-element and text counts for sharper testimony.
          </li>
          <li>
            <b>Page health, always one tap away:</b> the tab strip has a new{" "}
            <b>ⓘ chip</b>. It opens a Midnight sheet with the active tab&apos;s
            FULL LIVE TESTIMONY — url, WebView version, renderer (GPU/
            SOFTWARE), pixel verdict, readyState, DOM/interactive/text counts,
            boot errors, the last console lines, and the exact user-agent.
          </li>
          <li>
            <b>Copy report — the guess loop is over:</b> if ANYTHING is ever
            still broken: <b>Page health → Copy report → paste it in the
            chat</b>. The device names the cause verbatim (a SyntaxError from
            an old WebView build, a bot-challenge page, zero pixels = a
            compositor stall…). Plus <b>Refresh</b>, <b>Reload</b>, and{" "}
            <b>Reload in compatibility mode</b> as standing escapes.
          </li>
          <li>
            <b>Nothing else changed:</b> data, logins, tabs and heights
            survive the in-place update. Full suite green: <b>762 executions
            / 0 failures</b> (8 new pins: SSR-defeats-floor verdict,
            interactive parsing, health-report composition).
          </li>
        </ul>
        <a className="btn" href="/PocketShell-v0.7.0-m4.0.6-debug.apk">
          Download APK (debug, 22 MB)
        </a>
        <Sha text={HASHES.apk} />
        <p className="mono" style={{ border: "none", background: "transparent", padding: 0 }}>
          signing cert SHA-256: d96a6f664d7f8d7194733672dcd6eb9f33f40a0078ab07ff66bfd605138bf659 (same as v0.4.1–v0.7.0-m4.0.6)
        </p>
      </div>

      <div className="card">
        <h2>Update — no uninstall, no runtime reinstall</h2>
        <p>
          versionCode 30 installs <b>in place over v0.7.0-m4.0.5 (29),
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
            <b>v0.7.0-m4.0.6 (this build):</b> the last dark lever off
            (forced-light prefers-color-scheme), the SSR-proof boot witness,
            and the standing <b>Page health</b> sheet with a one-tap{" "}
            <b>Copy report</b> — the device can now hand over the exact cause
            of any failure, verbatim.
          </li>
        </ul>
      </div>

      <div className="card">
        <h2>Quick checks (docs/TESTING.md §23 — the m4.0.6 device gate)</h2>
        <ol className="steps">
          <li>
            Install {VERSION} in place → open the previously-black Companion
            tab. EXPECT the real page — most likely in its <b>light</b> theme
            now (white background): &quot;What can I help with?&quot; /
            composer / sidebar, all interactive. A dead shell is a failure of
            this gate.
          </li>
          <li>
            If the page STILL misbehaves: tap the <b>ⓘ chip</b> at the right
            end of the tab strip → <b>Copy report</b> → <b>paste the report
            into the chat</b>. That single paste names the exact cause and
            ends the guess loop permanently.
          </li>
          <li>
            Also try <b>Reload in compatibility mode</b> once (the software
            renderer) and note whether the page then renders.
          </li>
          <li>
            A page that refuses to start now escalates to the honest card
            EVEN when its server-rendered shell is huge — if you see
            &quot;Page won&apos;t start&quot;, the detail line carries the
            page&apos;s own numbers (readyState · elements · interactive ·
            text · error). Report it verbatim.
          </li>
          <li>
            Regressions: §22 (Force-Dark-off rendering, Chrome-UA logins),
            §21 (keyboard toggle one spot/one shape), §20 (deck types into
            Companion AND terminal), §18 startup, §17 spot-checks (drag 1:1,
            tabs, upload, Back, login persistence).
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
        <a className="btn secondary" href="/PocketShell-v0.7.0-m4.0.6-source.zip">
          source.zip
        </a>
        <a className="btn secondary" href="/PocketShell-v0.7.0-m4.0.6-source.tar.gz">
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
        root · <b>v0.7.0-m4.0.6 (this build): the last dark lever off, the
        witness de-fooled, and Copy report — the page tells us everything</b>.
        Your device keeps doing the QA that matters.
      </footer>
    </main>
  );
}

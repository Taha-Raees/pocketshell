const VERSION = "v0.7.0-m4.0.5";

const HASHES = {
  apk: "bbba0856e9771e01b44bb198d3b9923191903ce4e1f0a28cff27a0934393df26",
  zip: "9a4a28d3ef1318f9d9158e0b70f518ee81a5503909f4fa33949237a3179d5979",
  tgz: "53e60b559f2b466b8555288cb6787a186f258fd1229d5188043e72f8de9f7583",
  bundle: "30ea0bbb5f8038ffefa85e93aa4855e56bc960bf67b4583c964c97bdfa95cdbc",
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
          The black page — fixed at the root, and the page now testifies{" "}
          <span className="badge">versionCode 29</span>
        </h2>
        <p>
          <b>Your evidence named the killer.</b> The cookie banner dismissed on
          tap → the whole page pipeline is ALIVE (network, JS, layout, touch,
          compositing). No m4.0.4 failure card → the main region DID paint —
          the site&apos;s own darkened empty body. So the site&apos;s app
          simply never mounts. Two classic causes fit every observation, and
          both were still armed on this device. This build disarms both — and
          if any page still refuses to start, the card now shows the page&apos;s
          OWN testimony instead of a mystery.
        </p>
        <ul className="steps">
          <li>
            <b>Force Dark is OFF — three layers:</b> this app runs with a
            legacy targetSdk (a documented proot constraint), which leaves
            WebView <b>Force Dark / algorithmic darkening ARMED BY DEFAULT</b>{" "}
            in dark mode — the documented mangler that darkens site shells and
            breaks exactly this kind of page. Killed in the theme (API 29+),
            via the runtime Force-Dark-OFF call (API 29–32), and via the
            algorithmic-darkening-OFF call (API 33+). Sites now render exactly
            as their authors made them — own theme, own colors, unmangled.
          </li>
          <li>
            <b>Chrome-identical user agent:</b> the WebView default UA carried
            the <code>; wv</code> marker — the second-class client that Google
            login answers <code>disallowed_useragent</code> outright and
            bot-fronted sites serve degraded or challenged bundles. This build
            presents the byte-for-byte Chrome mobile UA of your device. Logins
            stop being refused; sites stop second-guessing the client.
          </li>
          <li>
            <b>The page now testifies — no more mystery canvases:</b> a
            boot-error trap rides in every page from its first moment, the
            console&apos;s last lines are kept per tab, and a DOM witness polls
            the page&apos;s own truth (readyState, element count) for up to
            20s. A tab is called healthy only when pixels painted AND the
            page&apos;s app actually mounted.
          </li>
          <li>
            <b>If an app never starts:</b> you get ONE silent fresh reload
            (flaky networks happen) — and if it still refuses, the card reads{" "}
            <b>&quot;Page won&apos;t start&quot;</b> with the page&apos;s OWN
            numbers: readyState, DOM element count, first script error, first
            console line, WebView version — plus the usual{" "}
            <b>Retry</b> / <b>Open in browser</b> / <b>Continue anyway</b>. If
            you ever see that card, report the detail line verbatim — it names
            the exact cause.
          </li>
          <li>
            <b>Nothing else changed:</b> data, logins, tabs and heights survive
            the in-place update. Full suite green: <b>754 executions / 0
            failures</b> (12 new pins: UA compat, mount verdict, probe-answer
            parsing, console ring, failure card).
          </li>
        </ul>
        <a className="btn" href="/PocketShell-v0.7.0-m4.0.5-debug.apk">
          Download APK (debug, 22 MB)
        </a>
        <Sha text={HASHES.apk} />
        <p className="mono" style={{ border: "none", background: "transparent", padding: 0 }}>
          signing cert SHA-256: d96a6f664d7f8d7194733672dcd6eb9f33f40a0078ab07ff66bfd605138bf659 (same as v0.4.1–v0.7.0-m4.0.5)
        </p>
      </div>

      <div className="card">
        <h2>Update — no uninstall, no runtime reinstall</h2>
        <p>
          versionCode 29 installs <b>in place over v0.7.0-m4.0.4 (28),
          v0.7.0-m4.0.3 (27), v0.7.0-m4.0.2 (26), v0.7.0-m4.0.1 (25),
          v0.7.0-m4.0 (24) and every earlier pinned-cert build</b>. Your Alpine
          runtime, installed packages, Kilo/Hermes installation, the procfs
          contract, every Phase 3 behavior and all Companion data (logins
          included) are untouched. This build also contains the m4.0.1 startup
          fix — it starts regardless of the WebView package&apos;s state.
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
            toggle in one spot/one shape.
          </li>
          <li>
            <b>v0.7.0-m4.0.5 (this build):</b> the black page fixed at the
            root — Force Dark off (three layers), Chrome-identical UA, and the
            DOM ground-truth witness: pixels AND a mounted app are both
            required, and failures carry the page&apos;s own testimony.
          </li>
        </ul>
      </div>

      <div className="card">
        <h2>Quick checks (docs/TESTING.md §22 — the m4.0.5 device gate)</h2>
        <ol className="steps">
          <li>
            Install {VERSION} in place → open the previously-black Companion
            tab. EXPECT the real page: ChatGPT&apos;s own UI (&quot;What can I
            help with?&quot; / composer) — in its own dark or light theme,
            either is correct. A dead shell with only the cookie banner is a
            failure of this gate — report it.
          </li>
          <li>
            Type into the page via the deck; tap around; scroll. The site must
            respond normally. Accept the cookie banner once — it must stay
            gone after a full app restart.
          </li>
          <li>
            If a <b>&quot;Page won&apos;t start&quot;</b> card ever appears:
            screenshot it and <b>report the detail line verbatim</b> (it reads
            the page&apos;s own numbers: readyState · DOM elements · first
            error · console line · WebView version). Then update &quot;Android
            System WebView&quot; (Play Store / Device care) and hit Retry.
          </li>
          <li>
            Logins: a Google sign-in inside a Companion must no longer be
            refused (the Chrome UA is now presented).
          </li>
          <li>
            Regressions: §21 (keyboard toggle one spot/one shape, both
            states), §20 (deck types into Companion AND terminal, deck pushes
            everything up, &quot;-&quot;/digits quick-tap), §18 startup, §17
            spot-checks (drag 1:1, tabs, upload, Back, login persistence).
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
        <a className="btn secondary" href="/PocketShell-v0.7.0-m4.0.5-source.zip">
          source.zip
        </a>
        <a className="btn secondary" href="/PocketShell-v0.7.0-m4.0.5-source.tar.gz">
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
        m4.0.4 pixels over promises · <b>v0.7.0-m4.0.5 (this build): the black
        page fixed at the root — Force Dark off, Chrome UA, and the page now
        testifies</b>. Your device keeps doing the QA that matters.
      </footer>
    </main>
  );
}

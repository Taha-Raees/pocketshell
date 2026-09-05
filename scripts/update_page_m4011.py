#!/usr/bin/env python3
"""m4.0.11 page.tsx update: VERSION, HASHES (cut #1 @ f58f02d), primary card,
update card, history bullets, quick checks (§28), source links, footer."""
import io, sys

P = "/home/z/my-project/app/page.tsx"
s = io.open(P, encoding="utf-8").read()
n0 = len(s)

def rep(old, new, tag):
    global s
    if old not in s:
        print(f"ANCHOR MISS: {tag}"); sys.exit(1)
    s = s.replace(old, new, 1)
    print(f"ok: {tag}")

# 1. VERSION + HASHES (cut #1 @ f58f02d)
rep('const VERSION = "v0.7.0-m4.0.9";', 'const VERSION = "v0.8.0-m4.0.11";', "version")
rep('''const HASHES = {
  apk: "2c83af33efc335fdb2649430b3bcbe3ce87c2e29c8ba3a5d6c72e57c0840e2c9",
  zip: "1fbcf3940992a2da9f6de7ecb115c5c0cf3afcf272ddb0044704b080ca3e0555",
  tgz: "3d90706b4041dbfe98de22a076f5f8a4e9df7bc44a90ae302c8d97dea90459ce",
  bundle: "8f3056378dbe26f5e54c83a62c2558c2ef295ed9dd407c8f3563bd2bfcb5c848",
};''', '''const HASHES = {
  apk: "1afcc7dbe69971e6136c64d033915b56269c7ff2b4e615fb78d22b3011a46335",
  zip: "631f1858627e81fbc01516707f99693161c8fe8b6e007b1303e842334e420867",
  tgz: "72fdb83e2b882a75da2a92aef5f1cc9a6b891ae504210d0076a1dd3890968c22",
  bundle: "d2b419c512d35c864e168946b2cecb3797669aaa4cbf1b564fcca89fdea0f1d5",
};''', "hashes")

# 2. primary card h2
rep('''          Companion Rendering Reset: the minimal baseline WebView experiment{" "}
          <span className="badge">versionCode 33</span>''',
'''          Replace Renderer Only: the winner frozen and shipped{" "}
          <span className="badge">versionCode 35</span>''', "h2")

# 3. primary card body (p + ul)
old_body = '''        <p>
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
        </ul>'''
new_body = '''        <p>
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
        </ul>'''
rep(old_body, new_body, "primary body")

# 4. download link + cert line
rep('''        <a className="btn" href="/PocketShell-v0.7.0-m4.0.9-debug.apk">
          Download APK (debug, 22 MB)
        </a>''',
'''        <a className="btn" href="/PocketShell-v0.8.0-m4.0.11-debug.apk">
          Download APK (debug, 22 MB)
        </a>''', "apk link")
rep("(same as v0.4.1–v0.7.0-m4.0.9)", "(same as v0.4.1–v0.8.0-m4.0.11)", "cert line")

# 5. update card
rep('''          versionCode 33 installs <b>in place over v0.7.0-m4.0.8 (32),
          v0.7.0-m4.0.7 (31),
          v0.7.0-m4.0.6 (30),
          v0.7.0-m4.0.5 (29),
          v0.7.0-m4.0.4 (28), v0.7.0-m4.0.3 (27), v0.7.0-m4.0.2 (26),
          v0.7.0-m4.0.1 (25), v0.7.0-m4.0 (24) and every earlier
          pinned-cert build</b>.''',
'''          versionCode 35 installs <b>in place over v0.8.0-m4.1.0 (34, an
          intermediate that was never announced), v0.7.0-m4.0.9 (33),
          v0.7.0-m4.0.8 (32), v0.7.0-m4.0.7 (31), v0.7.0-m4.0.6 (30),
          v0.7.0-m4.0.5 (29), v0.7.0-m4.0.4 (28), v0.7.0-m4.0.3 (27),
          v0.7.0-m4.0.2 (26), v0.7.0-m4.0.1 (25), v0.7.0-m4.0 (24) and
          every earlier pinned-cert build</b>.''', "update card")

# 6. history bullets
rep('''          <li>
            <b>v0.7.0-m4.0.9 (this build):</b> the Companion Rendering Reset —
            render path frozen; the minimal baseline WebView experiment ships
            so the device itself names the exact failing architectural layer
            before another line of Companion code changes.
          </li>''',
'''          <li>
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
          </li>''', "history bullets")

# 7. quick checks card (§28)
rep('''        <h2>Quick checks (docs/TESTING.md §26 — the m4.0.9 device gate)</h2>
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
        </ol>''',
'''        <h2>Quick checks (docs/TESTING.md §28 — Gates A–H on the REAL Companion)</h2>
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
        </ol>''', "quick checks")

# 8. source links + report mention
rep("href=\"/PocketShell-v0.7.0-m4.0.9-source.zip\"", "href=\"/PocketShell-v0.8.0-m4.0.11-source.zip\"", "zip link")
rep("href=\"/PocketShell-v0.7.0-m4.0.9-source.tar.gz\"", "href=\"/PocketShell-v0.8.0-m4.0.11-source.tar.gz\"", "tgz link")
rep("rendering-reset report (docs/RENDER-RESET-M4.0.9.md).",
    "rendering-reset report with the final verdict and the frozen-winner sweep (docs/RENDER-RESET-M4.0.9.md §7–§8).",
    "report mention")

# 9. footer
rep('''        <b>v0.7.0-m4.0.9 (this build): the rendering reset — the baseline
        experiment ships, the Companion render path freezes, and the device
        names the failing layer</b>.''',
'''        m4.0.9 the rendering reset · m4.1.0 the native rebuild ·{" "}
        <b>v0.8.0-m4.0.11 (this build): replace renderer only — the winner
        frozen, the baseline renderer copied into the existing sheet,
        diagnostics stripped</b>.''', "footer")

io.open(P, "w", encoding="utf-8").write(s)
print(f"page.tsx updated ({n0} -> {len(s)} bytes)")

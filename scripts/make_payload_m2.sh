#!/bin/bash
# Build the M2 delivery payload for the Next.js download page:
#   - full git bundle at current tip (complete milestone history)
#   - zero-dotfile buildable source tree zip + tar.gz twin
#     (web scaffold + app/page.tsx + app/layout.tsx shims excluded)
#   - copies of the M2 APK
# Outputs land in public/ (served by the web app) with a backup in dist-master/.
set -euo pipefail

PROJECT=/home/z/my-project
PUBLIC=$PROJECT/public
DIST=$PROJECT/dist-master
VERSION=v0.9.0-m5.0.1
TOPDIR=PocketShell-$VERSION
STAGE=$(mktemp -d)
trap 'rm -rf "$STAGE"' EXIT

cd "$PROJECT"
TIP=$(git rev-parse --short HEAD)
echo "== payload $VERSION @ git tip $TIP =="

mkdir -p "$DIST" "$PUBLIC"

# --- 1. git bundle: complete history, one file ---
BUNDLE=$STAGE/pocketshell-m2.gitbundle
git bundle create "$BUNDLE" --all
git bundle verify "$BUNDLE" > /dev/null
echo "bundle ok: $(du -h "$BUNDLE" | cut -f1)"

# --- 2. RESTORE.txt (references the bundle for history) ---
cat > "$STAGE/RESTORE.txt" << EOF
PocketShell - source snapshot ($VERSION, git tip $TIP)
=========================================================

This archive intentionally contains NO .git/ directory and no other
dotfiles, because some file-delivery panels reject archives that contain
them. The complete git history (all milestone checkpoints: initial -> M0
-> M1 -> M1.1 -> M1.2 -> M1.3 -> web-delivery records -> M2.1 research
-> M2.2 runtime install) is preserved inside the single file:

  pocketshell-m2.gitbundle

WHAT IS NEW IN $VERSION (vs v0.9.0-m5.0.0) — M5.0 FINAL UI CORRECTION: THE WORKSPACE
BAR (a surgical pass — no redesign, no new features, the working Companion
implementation untouched architecturally):
  - WORKSPACE HEADER REMOVED: the terminal workspace's large top title row
    (back + live session title) is GONE — the active session's name already
    lives in its tab, and the workspace now starts directly under the
    Android status area (the strip consumes the status-bar inset itself).
  - BACK LIVES IN THE TAB BAR: the back control is a compact integrated
    glyph at the far LEFT of the workspace bar — aligned with the tabs,
    not a header-sized button in its own row:

        ←   [ Tab ] [ Tab ] [ Tab ]        +

  - COMPACT IDE TABS, BOTH STRIPS: strip 34dp (was 40); active tab 34 /
    inactive 26 (was 40/30 — the active tab no longer looks oversized);
    2dp gaps (was 4); 8dp horizontal tab padding (was 10); tab width
    64–136dp (was 84–160 — more tabs fit on screen); 6dp corner radius
    (was 10); the active indicator is a 2dp hairline (was 2.5). The editor
    language (active tab opens into the canvas, quiet separators, no pill
    outlines) is untouched — only the density changed.
  - TAB TEXT: long titles truncate with an ellipsis and the close button
    always stays reachable; both strips scroll horizontally and the ACTIVE
    tab is always brought back into view when a switch lands off-screen.
  - COMPANION NEAR-FULL DRAG SURFACE: below 90% height NOTHING changed —
    the dedicated drag bar is the only sheet drag control and the tab bar
    behaves normally (taps switch tabs, close/+ /refresh work). At/above
    90% of the available height the Companion TAB STRIP also becomes a
    vertical drag surface (the tiny handle is hard to reach at near-full):
    dragging the strip up/down moves the whole sheet, gated behind the
    touch slop so tab taps, close, + and refresh are NEVER mistaken for
    drags — and the strip never minimizes on touch (that stays the
    handle's exclusive tap duty). Free positioning math is shared verbatim
    by both surfaces; no snap points, ever.
  - NOT TOUCHED: renderer, sheet mechanics, tab system, pool,
    refresh/hard-refresh, session persistence, keyboard internals, themes.
  - Full suite green: 752 executions, 0 failures (new pure pins for the
    90% drag-surface gate). Device gate: docs/TESTING.md §31.
    versionCode 38 — in-place update over 16..37; same pinned cert.

WHAT WAS NEW IN v0.9.0-m5.0.0 (vs v0.8.0-m4.0.12) — UI & INTERACTION POLISH: FREE-POSITION
COMPANION, DECLUTTERED HOME, LIGHT THEME DONE FULLY (a refinement phase — no
redesign, no new features, the working Companion implementation untouched
architecturally):
  - COMPANION DRAG BAR, EXACT BEHAVIOR: the visible bar is 2x wider (72x4dp,
    still slim) inside the same 40dp invisible full-width touch zone; the
    zone stays the column's first child (it can never hide behind tabs or
    content). A single TAP anywhere on the bar IMMEDIATELY minimizes the
    raised sheet — at 25%, 50%, 80%, near-full, ANY height. Restore is
    drag-up only (no floating button — the persistent bar remains the only
    affordance, as before).
  - FREE POSITIONING: the HALF/FULL snap windows are RETIRED. The height
    changes ONLY by dragging (up = taller, down = shorter) and a release
    settles EXACTLY where the user leaves it — any fraction, no forced
    anchors. The only special release is the collapse threshold (drag near
    the bar -> minimized). Height-math unit pins reworked for the contract.
  - HOME DECLUTTERED: the floating action button is REMOVED (code deleted;
    its 140dp clearance too) — session creation lives in the Terminal (its
    integrated "+" and the empty state). The duplicate "CLI Apps" dropdown
    is RETIRED — it listed exactly the apps the "Your tools" grid already
    launches; ONE clear path remains. No functionality removed.
  - COMPACT CHROME: terminal strip 44 -> 40dp; tab min width 96 -> 84dp;
    tab padding 12 -> 10dp; gaps 6 -> 4dp; the terminal "+" is now the same
    quiet integrated glyph as the Companion strip's (no circle plate).
    App-wide padding trim (Home, system-page kit, Packages, Settings) with
    touch targets kept >= 44-48dp. Horizontal tab scrolling unchanged.
  - KEYBOARD TOGGLE CORNER-ANCHORED: the [keyboard] rebirth icon sits 12dp
    from the right edge / 8dp above the gesture inset — on every screen,
    safe insets respected. The ONE keyboard system is otherwise untouched:
    no second layout, no Android IME, same dispatch chain and web-input
    bridging.
  - LIGHT THEME, FULL: the Midnight Sapphire token vocabulary became
    snapshot state — Midnight (dark, the historical values) <-> Daylight
    Sapphire (light). System / Light / Dark / AMOLED all live; switching is
    immediate (token sync before first read — no flash) and persisted
    (DataStore; survives restart/process recreation). The terminal CONTENT
    canvas stays Midnight in every theme (a terminal is a dark professional
    surface; TerminalPalette/OSC authority untouched) and websites keep
    owning their appearance — NO theme injection into Companion pages, ever.
    Status-bar icons follow the theme on every screen; AMOLED preserved;
    dynamic color intact.
  - PACKAGES + SETTINGS POLISH: one-row search (field + action), tighter
    list rhythm — every honest apk-backed state preserved verbatim;
    Settings regrouped into Appearance / Terminal / Companion — nothing
    invented.
  - NOT TOUCHED: renderer, sheet mechanics, tab system, pool,
    refresh/hard-refresh, session persistence, keyboard internals.
  - Full suite green: 750 executions, 0 failures. Device gate:
    docs/TESTING.md §30 (drag-bar matrix, free positioning, Home/FAB/CLI
    removal, toggle corner, Light/Dark/System/AMOLED sweep — websites NOT
    re-themed, Packages/Settings, keyboard regression ladder).
    versionCode 37 — in-place update over 16..36; same pinned cert.

WHAT WAS NEW IN v0.8.0-m4.0.12 (vs v0.8.0-m4.0.11) — COMPANION FINALIZATION: CLEANUP,
POLISH, ONE KEYBOARD (surgical pass; the working renderer is the source of
truth and was NOT modified):
  - DIAGNOSTICS GONE, COMPLETELY: the tab strip's i chip, the launch path,
    the render-baseline harness Activity and its matrix are DELETED (code +
    manifest). The Companion shows only the real website — no URL / WebView-
    version / UA / bounds / layer readouts, no URL/MODE/INSPECT/COPY buttons.
    The frozen render contract is unchanged in substance (the winner is
    pinned by VALUE now; the sweep evidence lives in
    docs/RENDER-RESET-M4.0.9.md §1–§8 + the git history).
  - REFRESH (tap) + HARD REFRESH (long-press) on the new strip glyph: tap =
    plain reload of the ACTIVE tab only (same URL, same tab, other tabs
    untouched). Long-press = the freshest possible reload that is NOT a
    data reset: one transient LOAD_NO_CACHE around the single reload,
    restored on page finish; cookies, logins and other tabs are preserved;
    a haptic tick + a "Hard reloading…" toast announce it. Pinned in the
    contract (REFRESH_SCOPE / HARD_RELOAD).
  - DRAG HANDLE, EASIER TO GRAB: the visible bar is unchanged (36x4dp); the
    INVISIBLE full-width touch zone grew 28 -> 40dp; it sits above nearby
    UI by construction and never overlaps the canvas (site scrolling
    untouched).
  - ONE POCKETSHELL KEYBOARD, EVERYWHERE: the deck moved to the app root —
    ONE keyboard over EVERY screen (Terminal, Linux, CLI Apps, Home, and
    the Companion over all of them). The system IME is hard-blocked for the
    app's lifetime (FLAG_ALT_FOCUSABLE_IM in onCreate) — the Android/
    Samsung keyboard can never appear (it used to leak back whenever the
    deck was toggled off). Real KeyEvents only (no JavaScript hacks); a
    universal dispatch fallback serves focused Compose text fields (the
    Companion settings Name/URL inputs — previously system-IME-only).
    WebView-input focus auto-opens the deck; the bottom-right keyboard
    toggle works on every screen; closing the Companion restores terminal
    focus. One keyboard, one layout, one experience.
  - NOT TOUCHED (the freeze held): renderer, sheet, drag mechanics,
    remembered height, tab system, tab state, destination storage,
    Companion navigation, provider management.
  - Full suite green: 750 executions, 0 failures. Device gate:
    docs/TESTING.md §29 (website rendering re-proof, refresh normal +
    hard, drag handle, universal keyboard, regression ladder).
    versionCode 36 — in-place update over 16..35; same pinned cert.

WHAT WAS NEW IN v0.8.0-m4.0.11 (vs v0.8.0-m4.1.0) — REPLACE RENDERER ONLY: THE WINNER
FROZEN AND SHIPPED (the closing iteration of the blank-canvas investigation):
  - THE DIRECTIVE, EXECUTED VERBATIM: do not redesign the Companion, do not
    touch the sheet architecture. The existing drag handle, bottom-sheet
    behavior, remembered height, tab strip, tab system (+/close/picker), tab
    state and destination storage are UNTOUCHED. Only the tab content area is
    the winning baseline renderer, diagnostics stripped.
  - THE WINNER (device sweep complete): BASELINE rendered all four gate sites
    (your recording); +CHROME UA rendered chat.z.ai COMPLETELY (screenshot);
    +MIDNIGHT BG rendered chatgpt.com COMPLETELY (screenshot). The winner is
    BASELINE — the most stable and least invasive mode BY CONSTRUCTION (zero
    deltas from Android defaults). Frozen as BaselineMatrix.WINNER and in the
    new CompanionRenderContract, both unit-pinned.
  - THE COPY, AS A UNIT: the production tab content renderer is the exact
    proven implementation — WebView(real Activity), JS + DOM storage only,
    plain FrameLayout host, attach -> first layout -> THEN loadUrl. No UA
    spoof, no background override, no config context, no viewport overrides,
    no layer type, no pre-attach load. The visible diagnostic wrapper (URL /
    MODE / INSPECT / COPY chrome, status header, health sheet) exists ONLY in
    the separate render-baseline harness behind the tab strip's i chip — the
    production canvas carries zero diagnostics.
  - SAME ARCHITECTURE AS m4.1.0, NOW UNDER ITS FINAL NAME: this build
    finalizes the native-rebuild work (committed as the m4.1.0 intermediate,
    vc34, never fully delivered) and ships it as versionCode 35 / 0.8.0-
    m4.0.11 — in-place update over 16..34; same pinned cert.
  - Full suite green: 758 executions, 0 failures (new: the frozen-contract
    pins). Device gate: docs/TESTING.md §28 — Gates A–H on the REAL Companion
    (real ChatGPT + Z.ai UIs in the panel, touch/keyboard, tab switch,
    collapse/reopen, session survival). Sweep table + copy map:
    docs/RENDER-RESET-M4.0.9.md §8.

WHAT WAS NEW IN v0.8.0-m4.1.0 (vs v0.7.0-m4.0.9) — COMPANION NATIVE REBUILD: THE PROVEN
BASELINE BECOMES THE ARCHITECTURE (decision B, executed — the fix the reset
experiment was built to find):
  - THE VERDICT (your m4.0.9 screen recording): the render baseline rendered
    example.com, wikipedia.org, chatgpt.com AND chat.z.ai COMPLETELY — same
    app, same process, same theme, same WebView 151.0.7922.199, default UA —
    seconds after the real Companion blanked on the same two sites. WebView,
    the device and the sites are exonerated; the old hosting stack was the
    failure. So the Companion is REBUILT around what physically works.
  - THE NEW ARCHITECTURE: PocketShell Activity -> Compose overlay chrome ->
    ONE stable plain FrameLayout -> one WebView per tab, created exactly like
    the winning baseline (real Activity, JS + DOM storage, Android defaults)
    and loaded AFTER first layout. Tab switches/retries/panel collapse are
    plain native view surgery — Compose never re-creates or swaps WebViews.
  - DELETED PERMANENTLY: the WebView pool + LRU/saveState, the forced-light
    configuration context, the Chrome-UA spoof in the render path, the
    flash-guard background, the software-layer compat swaps, the wide-
    viewport overrides, the pre-attach loads, the keyed swap host, the
    attach kick, the pixel watchdog, the boot witness + console tails, the
    health sheet, the retry ladders, and the RENDER_STALLED / APP_NOT_BOOTED
    failure kinds (retirement pinned by a test).
  - KEPT PRODUCT CONTRACT: Name+URL definitions, multiple tabs, persistence,
    cookies (incl. third-party, flushed on pause — logins survive restarts),
    upload picker, DownloadManager, drag handle + remembered height, back
    navigation, navigation allowlist + permission denial, guarded creation
    (a broken WebView package still degrades only the Companion, never the
    app), renderer-death guard, Phase 3.1 keyboard focus bridge.
  - HONEST FAILURE SURFACES: exactly two remain — "Page didn't load" (real
    main-frame error) and "Page renderer crashed". The i chip now launches
    the retained render-baseline harness as the standing diagnostic.
  - Full suite green: 744 executions, 0 failures. versionCode 34 / 0.8.0-
    m4.1.0 — in-place update over 16..33; same pinned cert. Device gate:
    docs/TESTING.md §27 (Gates E–H).

WHAT WAS NEW IN v0.7.0-m4.0.9 (vs v0.7.0-m4.0.8) — COMPANION RENDERING RESET: THE
MINIMAL BASELINE WEBVIEW EXPERIMENT (no Companion changes, zero symptom
patches — the investigation build; VERDICT in §7 of the report):
  - THE BRIEF, HONORED: eight iterations of evidence-backed fixes never
    proved WHICH architectural layer fails to present a fully loaded page.
    The m4.0.8 reports sharpened it: ChatGPT paints a blank WHITE canvas
    (the page's own light background PRESENTS — the scheme lever worked!)
    while the UI does not; Z.ai paints a blank dark canvas. Page pixels
    present; page UI absent. So this build changes NOTHING in the
    Companion render path — it ships the control experiment instead.
  - RENDER BASELINE (Companion -> i Page health -> "Render baseline"):
    a plain Activity -> FrameLayout -> ONE WebView(activity) — JS + DOM
    storage on, everything else Android defaults, URL loaded AFTER first
    layout. No pool, no Compose, no forced-light context, no custom UA,
    no watchdog, no attach kick, no boot witness, no retry.
  - ONE VARIABLE AT A TIME: MODE cycles BASELINE -> +CHROME UA ->
    +FORCED LIGHT CTX -> +MIDNIGHT BG -> +LOAD BEFORE ATTACH ->
    +WIDE VIEWPORT (pinned single-variable invariant); URL cycles
    example.com -> wikipedia.org -> chatgpt.com -> chat.z.ai (gates A-D).
  - REAL EVIDENCE PER RUN: the status line shows the exact config plus
    VIEW truth (attached, size, global visible rect, layer type); INSPECT
    adds the page's own viewport (innerWidth/innerHeight, visualViewport,
    title — read-only, on demand) for the bogus-viewport theory; COPY
    hands the whole status over for pasting.
  - docs/RENDER-RESET-M4.0.9.md: the investigation report — the A/B
    architecture comparison (15 layers), suspect->variant map, the device
    gate protocol, and the decision rule (single-variable fix / native
    ViewGroup host rebuild / Custom Tabs control / GeckoView research).
  - +8 unit pins. Full suite green: 788 executions, 0 failures.
  - versionCode 33 / 0.7.0-m4.0.9 — in-place update over 16..32; same
    pinned cert. Device gate: docs/TESTING.md section 26.

WHAT WAS NEW IN v0.7.0-m4.0.8 (vs v0.7.0-m4.0.7) — THE PAINTED-BUT-BLACK DECODE: THE
LIGHT PACKAGE RETURNS, ON TOP OF THE FIXED HOST (still the one job):
  - WHAT THE M4.0.7 REPORT PROVED: BOTH tabs — a GPU tab AND a software-layer
    tab — answered "pixels: painted" while you still saw black. A software-
    layer view cannot fail to reach the screen (the rest of the app renders
    fine), so the black IS the page's own painted output: the site's
    near-black body. The probe could not tell you that because it only asks
    "any pixel differs from the flash-guard" — a #000000 canvas passes.
  - WHY THE PAGE IS DARK: two dark sources were re-armed by m4.0.7's
    rollback. PocketShell targets SDK 28 (the proot/W^X constraint), and a
    legacy-target app on Android 15 gets WebView algorithmic darkening ON
    by default; and prefers-color-scheme answers DARK because the app is
    Midnight everywhere — so sites served dark themes on a dark body. The
    m4.0.5/m4.0.6 light levers had looked guilty only because the
    never-attaching host (fixed in m4.0.7) made every recipe paint nothing.
  - FIX 1 — FORCED-LIGHT SCHEME: the WebView's configuration is pinned to
    UI_MODE_NIGHT_NO (the documented prefers-color-scheme lever), so sites
    always serve their LIGHT themes — a white body with dark text you can
    SEE, even when a page's app shell is thin.
  - FIX 2 — DARKENING OFF at every API level:
    setAlgorithmicDarkeningAllowed(false) on Android 13+, and the
    deprecated setForceDark(FORCE_DARK_OFF) on 12 and below; the app theme
    already carries android:forceDarkAllowed=false.
  - FIX 3 — THE ACTIVITY LOOKUP THE CONFIG CONTEXT BREAKS, FIXED: the
    glass probe now unwraps ANY context chain to find the hosting Activity
    (a configuration context is not an Activity — that was a hidden m4.0.6
    regression), and the pool remembers the host from acquire.
  - THE PROBE CANNOT BE FOOLED AGAIN: the health report now names WHAT IS
    ON THE GLASS — "glass: dominant #0D0D0D · 97% near-black · 3 colors" —
    plus the scheme line ("scheme: forced light") and the PAGE'S OWN VOICE
    (its title and first visible words). If anything is ever wrong again,
    one pasted report names the page state (login wall, consent, empty
    shell) with zero guessing.
  - +4 unit pins (color truth, activity unwrapping, uiMode arithmetic —
    including a fix to the pin itself, and the page-voice parse). Full
    suite green: 772 executions, 0 failures.
  - versionCode 32 / 0.7.0-m4.0.8 — in-place update over 16..31; same
    pinned cert. Device gate: docs/TESTING.md §25.

WHAT WAS NEW IN v0.7.0-m4.0.7 (vs v0.7.0-m4.0.6) — THE HEALTH SHEET CRACKED IT: THE
COMPAT RENDERER NEVER REACHED THE SCREEN, AND THE CREATION RECIPE WAS THE
REGRESSION (still the one job; build numbered "4.0.7" per the naming rule):
  - WHAT THE REPORT PROVED: your five Page-health screenshots finally
    separated the suspects. The page is FULLY ALIVE — chat.com answered
    readyState=complete · 761 elements · 62 interactive · 394 text chars
    with ZERO boot errors — while pixels were "never painted" on GPU and
    "unknown" forever on the compatibility renderer. A hydrated app with
    zero presented frames is a PRESENTATION failure, and two long-hidden
    code bugs fell out of that fact.
  - ROOT CAUSE 1 — THE COMPAT RENDERER WAS NEVER ON SCREEN: AndroidView
    runs its factory exactly once per composed node, so every silently
    swapped WebView (the first-stall compat swap, the boot retry, AND
    plain TAB SWITCHING) never attached. On your device: the old view —
    destroyed on the first stall — stayed attached as the dead black
    canvas, while the fresh software-mode view sat stranded in the pool
    loading a perfect DOM it never displayed (that is why pixels read
    "unknown" forever). The host is now keyed on the view instance:
    every swap reaches the screen, and software mode gets its first REAL
    test. Tab switching also stops showing a stale page.
  - ROOT CAUSE 2 — THE CREATION RECIPE WAS THE REGRESSION: the forced-
    light createConfigurationContext (m4.0.5/m4.0.6) chased a dark-CSS
    theory your DOM evidence REFUTES, and the regression line is exact —
    m4.0.4 (plain activity context) still painted the cookie banner;
    m4.0.5/6 (config context) painted NOTHING. Creation is rolled back
    to the m4.0.4 recipe (activity context, no darkening levers); the
    Chrome-like UA stays (Google login fix, orthogonal to painting).
  - GLASS-FIRST PIXEL PROBE: the probe now asks PixelCopy — the frame as
    PRESENTED, cropped to the keyboard-free top half of the canvas —
    FIRST; the software readback is only the fallback. A hung copy times
    out after 1.5 s instead of hanging "unknown" forever; a throwing
    fallback never manufactures a stall.
  - ATTACH KICK: the pool loads URLs before the view attaches; some
    Chromium builds never bind the frame sink for such loads (DOM alive,
    pixels never present — your exact signature). If nothing painted
    3.5 s after first layout, ONE silent reload rebinds the load to the
    live surface. Once per view, never a card.
  - +1 unit pin (glass region arithmetic). Full suite green: 764
    executions, 0 failures.
  - versionCode 31 / 0.7.0-m4.0.7 — in-place update over 16..30; same
    pinned cert. Device gate: docs/TESTING.md §24.

WHAT WAS NEW IN v0.7.0-m4.0.6 (vs v0.7.0-m4.0.5) — THE LAST DARK LEVER OFF, THE
WITNESS DE-FOOLED, AND THE PAGE CAN NOW TELL US EVERYTHING (still the one
job; build numbered "4.0.6" per the standing naming rule):
  - WHAT THE SILENT BLACK PROVED: under 4.0.5 the canvas stayed black
    with NO error card — both witnesses stood down. The pixel probe
    passed because the page painted its own near-black body; the DOM
    witness passed because chatgpt.com's server-rendered shell lands
    with hundreds of inert nodes BEFORE its app hydrates, instantly
    clearing the 60-element mount floor. SSR markup vouched for an app
    that never started. And one dark lever was STILL armed: Force Dark
    off stops the framework from inverting pages, but the WebView still
    ANSWERS prefers-color-scheme: dark (it reads the app's uiMode —
    this app is Midnight everywhere), so sites kept serving dark CSS.
  - FORCED-LIGHT SCHEME: the WebView is now created in a configuration
    context pinned to light mode, so every site sees
    prefers-color-scheme: light and renders as authored for daylight.
    ChatGPT serves its light theme — the black-shell path is gone at
    the source. (Direction confirmed by web research: WebView derives
    prefers-color-scheme from the app's uiMode.)
  - SSR-PROOF WITNESS: a captured boot error is now decisive — an
    erroring page only counts as alive when it also shows real visible
    text. A SyntaxError-dead shell with 800 inert nodes now gets the
    honest "Page won't start" card WITH the error instead of silently
    passing. The probe also reads interactive-element and text counts.
  - PAGE HEALTH, ALWAYS ONE TAP AWAY: the tab strip has a new info
    chip. It opens a Midnight sheet with the ACTIVE TAB'S FULL LIVE
    TESTIMONY — url, WebView version, renderer, pixel verdict,
    readyState/DOM/interactive/text counts, boot errors, console
    lines, exact user-agent — and a COPY REPORT button. If ANYTHING is
    ever still broken: Page health → Copy report → paste it in the
    chat. The device tells us the cause verbatim; the guess loop is
    over. Plus Refresh / Reload / Reload-in-compatibility-mode.
  - +8 unit pins (SSR-defeats-floor verdict, interactive parsing incl.
    legacy answers, health-report composition + hard cap). Full suite:
    762 executions, 0 failures.
  - versionCode 30 / 0.7.0-m4.0.6 — in-place update over 16..29; same
    pinned cert. Device gate: docs/TESTING.md §23.

WHAT WAS NEW IN v0.7.0-m4.0.5 (vs v0.7.0-m4.0.4) — THE BLACK PAGE, FIXED AT THE
ROOT — AND THE PAGE NOW TESTIFIES (the one job this build exists for;
build numbered "4.0.5" per the standing naming rule):
  - WHY IT WAS BLACK — THE FULL CHAIN: your screenshot cracked it. The
    site's own cookie banner painted AND answered taps, so the WebView
    was alive — but the site's APP never started, and this app runs
    with a legacy targetSdk, which leaves WebView FORCE DARK
    (algorithmic darkening) ARMED BY DEFAULT in dark mode: the
    documented mangler that darkens site shells and breaks exactly this
    kind of page. On top of that the WebView user-agent carried the
    "; wv" marker that sites treat as a second-class embedded client
    (Google login answers disallowed_useragent outright; bot-fronted
    sites quietly serve degraded or challenged bundles).
  - FORCE DARK IS OFF — THREE LAYERS: a theme flag (API 29+), the
    runtime Force-Dark-OFF call (API 29-32), and the API 33+
    algorithmic-darkening-OFF call. Companion sites now render exactly
    as their authors made them — own theme, own colors, unmangled.
  - CHROME-IDENTICAL USER AGENT: the WebView default UA minus the
    "; wv" and "Version/4.0" markers — byte-for-byte the Chrome mobile
    UA of the same device. Logins stop being refused; sites stop
    second-guessing the client.
  - THE PAGE NOW TESTIFIES — NO MORE MYSTERY CANVASES: a boot-error
    trap is injected into every page at the first moment, the console's
    last lines are kept per tab, and a DOM witness polls the page's own
    truth (readyState, element count) for up to 20s. A tab is healthy
    only when pixels painted AND the page's app actually mounted. If an
    app never starts you get ONE silent fresh reload (flaky networks
    happen) — and if it still refuses, the card says so with the
    page's OWN numbers: readyState, DOM element count, first script
    error, first console line, WebView version — plus Retry, Open in
    browser, and Continue anyway, exactly as before.
  - +12 unit pins (UA compat, mount verdict, probe-answer parsing,
    diagnosis composition, console ring, failure card). Full suite:
    754 executions, 0 failures.
  - versionCode 29 / 0.7.0-m4.0.5 — in-place update over 16..28; same
    pinned cert. Device gate: docs/TESTING.md §22.

WHAT WAS NEW IN v0.7.0-m4.0.4 (vs v0.7.0-m4.0.3) — THE COOKIE-BANNER LESSON:
PIXEL-TRUTH STALL DETECTION + ONE-SPOT KEYBOARD TOGGLE (device bug batch,
screenshots analyzed 2026-09-05; build numbered "4.0.4" per the standing
naming rule):
  - THE BLACK PAGE, DIAGNOSED BY ITS OWN EVIDENCE: the Companion canvas
    stayed black because the site's MAIN content never paints on this
    device's WebView build — while the site's own cookie-consent banner
    DID draw. m4.0.3's watchdog trusted load events, which kept firing
    faithfully — so it never fired, and the canvas stayed a bare black
    flash-guard with no explanation.
  - PIXEL-TRUTH PROBE: the watchdog now reads actual canvas pixels
    (software readback + an API 29+ glass readback of the frame as it
    was PRESENTED) instead of load events. A canvas whose main region is
    still the bare Midnight flash-guard after ~15s IS a stall, no matter
    what the page pipeline claims.
  - PARTIAL PAINT IS NOT CONTENT: the cookie-banner screenshot showed the
    exact defeat case — the consent bar painted a few pixels at the
    bottom of an otherwise dead canvas, and an "any differing pixel"
    rule reads that as healthy. The probe now judges the MAIN region
    only: everything above the bottom 25% of the canvas, where sites
    dock consent bars and snackbars. A banner can never vouch for a
    dead page again.
  - SILENT FIRST RETRY, HONEST SECOND CARD: the first detected stall
    re-creates the tab on the SOFTWARE renderer by itself (the classic
    fix for GPU paths that rasterize nothing on broken WebView builds).
    Only if that stalls too does the canvas explain itself: title, the
    installed WebView version, and the way out — never a silent black
    box.
  - THREE WAYS OUT on the failure card: Retry (each press alternates
    GPU → SOFTWARE rendering), "Open in browser" (the same address in
    your real browser — settles whether it is the site or this device's
    WebView build), and "Continue anyway" (the raw canvas as-is — you
    can tap the site's own Accept button; the probe then stays quiet
    and never fights you for the canvas; Retry re-opens the question).
  - KEYBOARD TOGGLE IN ONE PLACE: the [⌨] key now lives in the deck row
    BETWEEN Space and Enter (Ctrl · Alt · Space · Shift · [⌨] · Enter)
    — and with the deck toggled off, the SAME rectangular key box parks
    at that same right-hand spot (the round bottom-right bubble is
    gone). One toggle, one shape, one place, in both states.
  - THE COOKIE BANNER ITSELF: it belongs to the WEBSITE (that one is
    ChatGPT/OpenAI's), not to PocketShell. Choose Accept/Reject once —
    cookies are flushed to storage on every pause, so your choice (and
    your logins) persist across launches.
  - +2 unit pins (main-region arithmetic + degenerate samples). Full
    suite green: 0 failures across app debug/release + terminal modules.
  - versionCode 28 / 0.7.0-m4.0.4 — in-place update over 16..27; same
    pinned cert. Device gate: docs/TESTING.md §21.

WHAT WAS NEW IN v0.7.0-m4.0.3 (vs v0.7.0-m4.0.2) — KEYBOARD EVERYWHERE + HONEST
RENDER-STALL + COMPANION PICKER (device bug batch, screenshot analyzed
2026-09-05; build requested as "4.0.3"):
  - ONE KEYBOARD FOR BOTH SURFACES: the PocketShell deck now types into
    COMPANIONS too, not just the terminal. Deck presses follow FOCUS:
    tap the Companion page, type — text lands in the page; tap the
    terminal, type — text lands in the shell. While the deck is up the
    system keyboard is blocked (no double keyboard); with the deck
    toggled off, Companion inputs can still summon the system keyboard
    and the panel lifts above it.
  - THE KEYBOARD PUSHES EVERYTHING UP: the deck is now the bottom-most
    surface — the Companion panel (handle + tabs + page) rides ABOVE it,
    so the keyboard never opens on top of anything and nothing hides
    underneath it.
  - TOGGLE = FULL COLLAPSE + CORNER ICON: the keyboard button now removes
    the WHOLE deck; a small Midnight keyboard icon floats at the
    bottom-right corner to bring it back whenever you want (tapping the
    terminal canvas works too).
  - EVERY KEY WORKS: fixed the dead "-" key (and the whole digit row):
    keys with a hold-gesture layer dispatched NOTHING on a quick tap —
    now a quick tap commits the character; holding still gives the Fn
    layer. Arrow keys are 12dp longer horizontally.
  - THE WHITE CANVAS, FOR REAL THIS TIME: WebViews are now created with
    the ACTIVITY context (the application context used so far is a
    known blank-canvas source on OEM builds), and a 15s watchdog now
    catches the case where a page paints NOTHING: the canvas shows
    "Page never rendered" + the installed WebView version instead of a
    silent white box. Retry alternates GPU → SOFTWARE rendering
    (compatibility mode) — the honest second attempt for broken builds.
  - "+" FINALLY DOES SOMETHING: the tab strip's + opens a Midnight sheet
    listing every Companion (open tabs marked) — tap to open one, or
    "+ Add Companion" to go to the management page.
  - +6 unit pins (routing decision + render-stall model). Full suite:
    724 tests, 0 failures.
  - versionCode 27 / 0.7.0-m4.0.3 — in-place update over 16..26; same
    pinned cert. Device gate: docs/TESTING.md §20.

WHAT WAS NEW IN v0.7.0-m4.0.2 (vs v0.7.0-m4.0.1) — HONEST COMPANION FAILURE
SURFACES: THE CANVAS IS NEVER MYSTERIOUSLY WHITE (device finding 2026-09-05,
same session as the startup hotfix):
  - THE FINDING: after the m4.0.1 startup fix, PocketShell starts — but
    pulling the Companion up showed the ChatGPT tab strip over a PURE
    WHITE canvas: no page, no error, no explanation. Every PocketShell
    surface is dark, so the white came from the WebView content side
    (page can't load over the network/VPN, a WebView build too old for a
    modern site after Samsung's rollback, or the updated build rendering
    blank). A silent white rectangle is not acceptable here.
  - THE FIX: main-frame load failures now render an in-canvas Midnight
    card — "Page didn't load" + the REAL error string (e.g.
    net::ERR_NAME_NOT_RESOLVED) + the installed Android System WebView
    version + the hint that matters (network/VPN or WebView update) + a
    Retry button. A dead page renderer (onRenderProcessGone — the classic
    white-canvas signature of broken WebView builds, whose DEFAULT
    behavior kills the whole app) is handled: only the crashed view is
    destroyed, PocketShell stays alive, and the card reads "Page renderer
    crashed" with the version + update/rollback hint. The "Companion
    unavailable" notice now shows the WebView version too. Retry
    re-creates the tab from scratch; a successful navigation clears the
    failure automatically.
  - HONEST BOUNDARY: a page that loads but renders blank because an old
    WebView cannot run its JavaScript fires NO error event — that case is
    identified with the version line on the cards plus a static-site test
    (add example.com as a second Companion: it renders on ANY WebView).
  - +4 unit pins on the pure failure model (one caught a real defect
    before delivery). Full suite: 712 tests, 0 failures.
  - versionCode 26 / 0.7.0-m4.0.2 — in-place update over 16..25; same
    pinned cert. Device gate: docs/TESTING.md §19.

WHAT WAS NEW IN v0.7.0-m4.0.1 (vs v0.7.0-m4.0) — HOTFIX: STARTUP CRASH WHEN THE
DEVICE'S WEBVIEW PACKAGE IS BROKEN OR FRESHLY UPDATED (device-reported
2026-09-05 via Samsung Device Care's "Uninstall WebView updates?" dialog):
  - THE REPORTED BUG: on m4.0 the app crashed on EVERY start before any
    UI appeared; Samsung Device Care blamed the freshly updated Android
    System WebView and offered to roll it back. Root cause: m4.0 called
    CookieManager.getInstance() during Application.onCreate — which
    synchronously LOADS THE ENTIRE WEBVIEW PROVIDER before any UI. On
    devices where the updated WebView package crashes at provider init
    (seen on a Samsung running microG), every PocketShell launch died
    with it — even though the Companion was never opened.
  - THE FIX — STARTUP NO LONGER DEPENDS ON WEBVIEW HEALTH: the
    Application now holds nothing but a context reference; cookie
    configuration and WebView creation happen lazily at first Companion
    use and are fully guarded. A broken WebView package degrades ONLY
    the Companion surface: the layer shows an honest "Companion
    unavailable" notice naming the system component and the way out,
    while the terminal, Home, packages, settings and every other screen
    keep working untouched. Clear-web-data and the pause path are
    guarded the same way — no path can crash the process on a broken
    provider.
  - DATA UNCHANGED: logins still live in the app's private web storage —
    installing this hotfix in place keeps every session; the Companion
    works normally once the device has a healthy WebView (update
    "Android System WebView" in the Play Store, or accept Samsung's
    rollback — either way PocketShell itself now always starts).
  - versionCode 25 / 0.7.0-m4.0.1 — in-place update over 16..24; same
    pinned cert. Full suite: 704 tests, 0 failures. Device gate:
    docs/TESTING.md §18.

WHAT WAS NEW IN v0.7.0-m4.0 (vs v0.7.0-m3.6) — PHASE 4, COMPANION: THE EMBEDDED
WEB WORKSPACE:
  - Companion is a lightweight web workspace INSIDE PocketShell: a
    persistent layer below every screen, pulled up by a bottom drag
    handle. NO floating button, NO browser chrome, NO address bar: the
    website is the content, PocketShell owns the chrome. A Companion is
    exactly Name + URL — fully generic (ChatGPT, Claude, Gemini, DeepSeek,
    GitHub, docs sites, local dashboards — anything). This is NOT an AI
    chatbot and has NO AI APIs: you log into the real website with your
    real account and get the real experience.
  - ENGINE: Android System WebView only — ZERO new dependencies. Chromium
    renders in its own sandboxed process. Cookies + site storage persist
    in the app's private web profile (logins survive full app restarts);
    Safe Browsing on, mixed content never, file/content access off,
    camera/mic/geo denied, no user-agent spoofing.
  - DRAG: 1:1 with the finger, handle zone only — webpage scrolling and
    resizing never fight. While dragging, the page does NOT reflow (its
    height freezes, one resize on release). Gentle anchors (half,
    near-full) snap only within a 6% window; otherwise the surface stays
    exactly where released and the height is remembered. The handle is
    reachable at every height — you are never trapped.
  - TABS: inverted Phase 3.1 editor style (the active tab opens into the
    web canvas, 2.5dp Sapphire bottom edge). Switching tabs NEVER reloads:
    background tabs stay alive-but-paused in a process-scoped pool
    (active + 4 LRU; evicted tabs keep their navigation state and restore
    on reactivation; memory pressure drops background pages first).
  - INTEGRATION: Back = webpage history, then collapse, then normal
    PocketShell navigation — never traps you. File uploads use the normal
    Android picker; downloads go to app-private storage via the system
    DownloadManager (no permission, no crash); mailto/tel/intent links
    resolve to the system (honest Toast when nothing can); the panel
    lifts above the keyboard for chat inputs.
  - SETTINGS > COMPANION: add/edit/delete definitions (inline Midnight
    editor), quick-add templates as editable pre-fills (ChatGPT, Claude,
    Gemini, DeepSeek, GitHub), Default Companion rows, Clear web data.
  - versionCode 24 / 0.7.0-m4.0 — in-place update over 16..23; same pinned
    cert. 704 test executions, 0 failures. Contract:
    docs/PHASE-4-COMPANION-DESIGN.md. Device gate: docs/TESTING.md §17.

WHAT WAS NEW IN v0.7.0-m3.6 (vs v0.7.0-m3.5) — PHASE 3.6, THE PROCFS CONTRACT
(/proc UNCONDITIONAL + apk fd-link SELF-REPAIR) [superseded by Phase 4; the
procfs contract remains fully active in the runtime]:
  - THE REPORTED BUG: inside the Linux environment, Kilo Code died with
    "TUI worker error ENOENT: no such file or directory, realpath ..." on
    directories that EXISTED, and /proc/version, /proc/self/root, ps were
    all missing — starting right after an in-guest apk update && upgrade.
    Root cause (user-confirmed with a manual mount, fixed in code here):
    an in-guest apk upgrade replaced the checksum-pinned patched libapk,
    the old M2.6 rule "bind /proc only while the patched apk library
    verifies" failed, and every NEW session silently started WITHOUT a
    /proc. Bun-compiled CLIs (Kilo Code's runtime) resolve paths through
    /proc/self/fd on aarch64 — the kernel has NO realpath syscall there —
    so realpath() of existing paths returned ENOENT. Kilo was working
    before the upgrade exactly because the old build still had /proc.
  - THE FIX — /proc IS NOW UNCONDITIONAL: every Linux Shell and every
    command-app session binds a REAL procfs (host procfs, hidepid=2 — the
    app's own process tree, honestly) plus the verified sysdata overlays.
    The parameter that could drop it no longer exists; package operations
    keep their proven /proc-free commit environment (require-guarded).
    A spawn-time audit (procContractProblem) additionally verifies every
    interactive session spec carries /proc + /dev + /sys BEFORE any session
    can start — a regression now fails loud instead of silently breaking
    the guest.
  - THE apk fd-link PATCH NOW SELF-HEALS: instead of pinning one apk-tools
    build, PocketShell scans the guest's libapk libraries for the fd-link
    gate literal and applies the same one-byte patch to whatever build
    carries it — including the post-upgrade one (layout identical in
    3.0.6/3.0.8). Byte-identical output to the previous asset was re-proven
    on the pinned minirootfs. Ambiguous/unknown binaries are never touched.
    In-guest apk update/upgrade no longer breaks ANYTHING: /proc stays,
    apk keeps the SELinux-safe commit path.
  - versionCode 23 / 0.7.0-m3.6 — in-place update over 16..22; same pinned
    cert. 664 test executions, 0 failures. Full contract:
    docs/PROCFS-CONTRACT.md (launch architecture, per-session bind audit
    incl. /dev /dev/pts /sys /tmp, validation layers, device gate).

WHAT WAS NEW IN v0.7.0-m3.5 (vs v0.7.0-m3.4) — PHASE 3.5, COMMAND LAUNCH FIX +
SYSTEM PAGES JOIN MIDNIGHT:
  - THE REPORTED FIX: "when kilo is clicked it opens normal Linux terminal".
    Root cause: the launch command was written into the PTY right after
    session CONSTRUCTION — but TerminalSession forks the process only when
    the view first renders the session, and write() drops bytes while no
    process exists (mShellPid == 0). The command was silently discarded:
    every tapped tile (kilo, claude, nano, git — ALL apps) opened a plain
    shell. The fix delivers the command through the login shell's ARGV
    (\`sh -l -c "kilo; exec sh -l"\`): deterministic, timing-independent,
    still exactly what typing the command would do, still a fresh dedicated
    session per launch, and exiting the app still returns to the guest
    prompt. ONE generic path for every registry + catalog app — no per-app
    code anywhere (the registry stays pure data; availability still comes
    only from the real login-shell probe).
  - DIAGNOSTICS, PACKAGES, SETTINGS JOIN MIDNIGHT SAPPHIRE: the three
    app-theme pages now share the Home/Terminal identity — a shared page
    kit (ui/system/MidnightPage.kt): Midnight canvas, edge-to-edge, mono
    page titles + section labels, hairline dividers, mono fact values with
    honest state coloring (Sapphire = confirmed good, danger = confirmed
    bad). Actions carry two weights: filled Sapphire and quiet hairline
    (destructive tone only on Remove runtime / Uninstall). Packages: chrome
    search plate with Sapphire focus ring, search hits as flat mono rows,
    catalog cards in the chrome tone, operation progress as the honest
    banner (danger border only on FAILED) with Cancel/Retry. Settings:
    whole-row radio rows (ring + Sapphire dot) and remapped switch/slider.
    Status bar: light icons on every screen (all pages are Midnight now).
  - versionCode 22 / 0.7.0-m3.5 — in-place update over 16..21; same pinned
    cert. 656 test executions, 0 failures (baseline + 4 new launch-chain
    pins). Design contract: docs/PHASE-3.5-DESIGN.md.

WHAT WAS NEW IN v0.7.0-m3.4 (vs v0.7.0-m3.3) — PHASE 3.4, REGISTRY EXPANSION +
SYSTEM PAGES:
  - THE REPORTED FIX: "I have installed kilocli but it doesn't show up".
    Root cause: command-app discovery = registry ∩ guest PATH — the probe
    asks the login shell \`command -v <name>\` only for REGISTRY names, and
    \`kilo\` (the Kilo Code CLI, \`npm install -g @kilocode/cli\`) had no
    registry entry, so it was never probed and could never appear. That is
    the honesty contract working (the launcher never guesses from unknown
    PATH binaries); the registry is the designed extension point — so Kilo
    is now registered, probe-gated like every app: the tile appears within
    one Home revisit once \`kilo\` exists, and disappears when it does not.
  - REGISTRY EXPANSION (data only — zero pipeline changes): five terminal
    AI agents seeded — KILO CODE (\`kilo\`), GEMINI CLI (\`gemini\`), CODEX
    (\`codex\`), AIDER (\`aider\`), QWEN CODE (\`qwen\`) — appended AFTER the
    brief's four (Hermes/OpenCode/Claude/ZCode), so launcher order on
    installed devices never shuffles. Installing any of them is still the
    ONLY way its tile can ever render.
  - SYSTEM PAGES ADOPT THE PHASE 3.3 GUIDELINES: Packages screen — the
    "runtime not installed" state is now INLINE TEXT on the canvas (no
    container card) with a real \`Open Diagnostics\` link; title renamed
    "Explore CLI Apps" -> "Packages" (one name per object). Per-package
    surfaces stay — real objects (installable package + actions) are
    explicitly allowed by the card rules. Settings — whole-row selection:
    theme radio rows and the dynamic-color row are >=48dp full-row touch
    targets with correct a11y roles (visual language unchanged).
    Diagnostics — one uniform section pattern: divider + header for
    System / Linux runtime / Package environment, plain fact rows, no
    internal per-row dividers.
  - Home needs ZERO changes: the "Your tools" grid and the \`CLI Apps \`
    menu render from the registry — new entries surface automatically.
  - versionCode 21 / 0.7.0-m3.4 — in-place update over 16/17/18/19/20;
    same pinned cert. 648 test executions, 0 failures (644 baseline + 4
    new registry pins; CommandAppsTest invariants intact). Device gate:
    TESTING.md §15.

WHAT WAS NEW IN v0.7.0-m3.3 (vs v0.7.0-m3.2) — PHASE 3.3, HOME & SYSTEM UI:
  - SCOPE: visual architecture + Home interaction cleanup ONLY. ZERO
    backend changes — command-app discovery, login-shell probing,
    verify-before-launch, dedicated guest sessions, the forbidden package
    list, the Phase 3.1 terminal/keyboard and every runtime behavior are
    untouched. Design contract committed BEFORE implementation:
    docs/PHASE-3.3-DESIGN.md (all 12 required sections).
  - THE STRUCTURAL FIX: Phase 3.2's Home used surfaces for GROUPING (a
    stack of rounded rectangles: giant empty-state card, bordered session
    rows, a chip inside the Terminal tile, "Explore packages" twice, fake
    ghost tiles, a FAB duplicating the tools grid with icon circles).
    Phase 3.3 draws a surface ONLY for a real object: the two
    environments, the CLI Apps menu, the floating create control, an
    actionable banner, a pressed row/tile. Everything else sits directly
    on the canvas, separated by spacing, section labels, hairline
    dividers and Midnight tone steps. No cards in cards; radius <= 16dp;
    no decorative borders.
  - ONE CLI CONTROL: a quiet "CLI Apps " trigger in the header area —
    rendered ONLY when the guest actually confirmed apps (never a dead
    button) — opening a compact Midnight launcher menu (chrome surface,
    14dp, zero tonal elevation, hairline): monogram plate + name + the
    launch command dim and secondary. Row taps run the EXACT Phase 3.2
    pipeline (fresh verify -> dedicated guest session -> focus). No
    logos, no dialog — the app itself is the identity.
  - FOUNDATIONS, FLATTER: Terminal and Linux are borderless tone-step
    surfaces (canvas / chrome), radius 14dp. Terminal descriptor is the
    always-fitting "Native shell" (the truncating "Native PocketShell
    envir..." is gone); running count is plain mono text (chip box
    removed). Linux: honest state line ("Alpine · ready" in Sapphire;
    other states route to Diagnostics — distro-agnostic by construction).
  - YOUR TOOLS: command apps as icon + label launcher entries (52dp
    borderless monogram plates — NOT cards; surface only on press).
    Section renamed from "Command apps". Empty state is THREE QUIET LINES
    directly on the canvas ("No CLI apps yet." + one sentence + the
    page's only "Explore packages" link) — no container, no ghost
    placeholders, nothing dominating. When apps exist, a single quiet
    "Packages" footer link replaces it — exactly ONE packages affordance
    in every state, by construction.
  - SESSIONS FLAT: dot + label + mono id rows between hairline dividers;
    a pressed row is the only surface the section ever draws; green
    still means ONLY a live process; tap returns; cap 4 + "+N more".
  - FAB SINGLE-PURPOSE: the floating control now means exactly "create a
    new session" — New Terminal / New Linux session ONLY, as TEXT-ONLY
    chips (icon circles, logo marks and command-app actions are removed;
    apps live in the grid + the CLI menu). QuickAction model simplified
    to id/label/enabled/onRun; the list stays data for future actions.
  - OTHER PAGES reviewed (contract §10): Settings/Diagnostics are already
    divider-based and flat — unchanged; Packages cards represent real
    objects (installable packages with actions) — allowed, unchanged.
    [Superseded by Phase 3.4, which put those rules into code.]
  - versionCode 20 / 0.7.0-m3.3 — in-place update over 16/17/18/19; same
    pinned cert. 644 test executions, 0 failures (baselines +
    CommandAppsTest untouched and green). Device gate: TESTING.md §14.

WHAT WAS NEW IN v0.7.0-m3.2 (vs v0.7.0-m3.1) — PHASE 3.2, HOME / OS LAUNCHER:
  - SCOPE: the HOME screen ONLY + the command-launchable app architecture.
    The Phase 3.1 terminal redesign (chrome, tabs, keyboard, palette, PTY
    pipeline), runtime, Linux environment, package manager, installed
    packages and Hermes are untouched. Design contract committed BEFORE
    implementation: docs/PHASE-3.2-DESIGN.md.
  - CONCEPT: PocketShell Home is the launcher of a Linux-centric
    environment — identity on top, the two foundations (Terminal, Linux)
    in the center, command apps below, sessions quiet, ONE floating
    quick-action control. No bottom navigation bar. Not a card dashboard.
  - PACKAGES ≠ APPS: Home NO LONGER renders installed packages (the old
    "Installed CLI Apps" apk-probe section is gone). git, nano, python,
    node, npm, gcc, g++, htop, vim can never become launcher tiles
    (test-pinned). New apps/CommandApps.kt registry (Hermes Agent,
    OpenCode, Claude Code, ZCode) — a tile exists ONLY when the guest
    confirms the command.
  - LOGIN-SHELL AVAILABILITY PROBE: one batched \`sh -lc\` exec asking
    exactly "would a fresh guest login shell find this command?" — the
    same environment the user's typing sees, where uv-installed
    launchers (hermes, M2.6) are reachable (the spec's static PATH lacks
    /root/.local/bin; a non-login probe would answer a false absence).
    Probe failure renders "could not be checked" and KEEPS the last real
    list — never a fake "no apps" (v0.4.4 honesty rule).
  - LAUNCH FLOW: tap Hermes → runtime gate → fresh probe → NEW dedicated
    guest session whose PTY receives \`hermes\` — what the launcher does is
    exactly what typing would do (typed command visible in scrollback;
    exiting the app returns to the guest prompt).
  - MIDNIGHT SAPPHIRE LAUNCHER: page #0B1424, Terminal tile in the exact
    canvas color #080F1D, Linux tile #101B30, app tiles #16233F; NO pure
    black, NO gradients on this page, ONE Sapphire accent #7FA3EF; green
    #5FB572 only on a real running dot. Drawn brand mark + mono wordmark
    + tagline "Your Linux workspace on Android"; drawn terminal/mountain
    marks; honest Linux state line (READY enters the guest, every other
    state routes to Diagnostics — distro-agnostic by construction).
  - LAUNCHER GRID: 64dp monogram tiles, 3 columns phone / 4 at >=600dp /
    6 at >=840dp, content capped 720dp centered on tablets. Empty state:
    drawn ghost tiles + "Your tools will appear here" + Explore packages
    (real screen; no fake marketplace). [Superseded by Phase 3.3's
    presentation-only restructure — same architecture, new layout.]
  - SESSIONS: compact continuation area (max 4 rows + "+N more"), tap
    returns to the session; FAB quick actions: New Terminal (fresh), New
    Linux session (READY only), each available command app — real actions
    only, the list is data for future capabilities. Scrim + stagger chips,
    150–220ms, no bounce. Edge-to-edge launcher + per-screen status-bar
    icon coordination.
  - versionCode 19 / 0.7.0-m3.2 — in-place update over 16/17/18; same
    pinned cert. 644 test executions, 0 failures (628 baseline + 8 new
    CommandApps invariants). Device gate: TESTING.md §13.

WHAT WAS NEW IN v0.7.0-m3.1 (vs v0.6.2-m2.6) — PHASE 3.1, TERMINAL SCREEN ONLY:
  - SCOPE: the Terminal screen ONLY (chrome, session tabs, terminal
    workspace, keyboard). Home/Explore/Packages/Settings/Diagnostics and the
    whole M2.6 runtime layer are untouched. Design contract committed
    BEFORE implementation: docs/PHASE-3.1-DESIGN.md ("Midnight Sapphire").
  - BLUE-DARK, NEVER BLACK: the terminal page gets its own fixed Midnight
    identity in every app theme - chrome #101B30, strip #0D1730, canvas
    #080F1D (deepest blue-black), deck #131F38; exactly ONE accent
    (Sapphire #7FA3EF) for cursor/active tab/modifiers/Enter. Real
    16-color ANSI palette override at process start (OSC still wins - the
    terminal stays real).
  - TERMINAL TYPEFACE: JetBrains Mono NL (no-ligature build, OFL 1.1,
    Regular/Bold/Italic in res/font) via vendored setTypeface() -
    character-exact output, distinct 0/O and 1/l/I. Sapphire block cursor
    (upstream renderer, blinker unchanged, DECSCUSR still honored).
  - EDITOR-STYLE SESSION TABS (not pills): rounded-TOP tabs; inactive
    recessed with a right hairline; the ACTIVE tab is canvas-colored with a
    2.5dp Sapphire top hairline and covers the strip's bottom hairline - it
    visibly opens into the terminal workspace.
  - KEYBOARD REBUILT FROM SCRATCH (no system IME anywhere): TOP row
    Esc Tab + grouped arrow panel (auto-repeat) - MIDDLE PocketShell QWERTY
    (digits, letters, terminal punctuation row, symbols with
    INS/DEL/HOME/END/PGUP/PGDN - v0.6.2 coverage fully preserved) - BOTTOM
    [keyboard-icon] Ctrl Alt Space Shift Enter (exact order, icon-only
    toggle, far-left, permanent; Enter accent-filled). The dedicated FN key
    is REMOVED: F1-F10 = number-row long-press (hold -> bubble -> release),
    F11/F12 on tablet -/= long-press; readFnKey() honestly false.
  - Keyboard toggle collapses ONLY the QWERTY body; both accessory rows stay;
    landscape compresses to 4 rows; tablets get 14/15-column rows. Dispatch
    pipeline unchanged (synthetic KeyEvents -> vendored KeyHandler -> PTY);
    one-shot/lock modifier machine unchanged (Ctrl/Alt/Shift).
  - versionCode 18 / 0.7.0-m3.1 - in-place update over v0.6.2 (16) and the
    discarded v0.7.0-ui (17); same pinned cert. 628 test executions, 0
    failures (keyboard contract tests rewritten). Device gate: TESTING.md
    §12 (visual sweep, exact layout, Ctrl+C/D/L/A/E/W, Fn long-press,
    apk update / node --version / hermes --version).

WHAT WAS NEW IN v0.6.2-m2.6 (vs v0.6.1-m2.6):
  - TWO fixes from the same 2026-09-02 device session (SM-F711B), both
    device-reported and rehearsal-proven:
  - M2.6.13, HARDLINK EXTRACTION FIXED: "apk add build-base" failed with
    19 "failed to extract ... Permission denied" errors - exactly the
    hardlink entries of binutils (11), gcc (5) and g++ (3), verified by
    listing the tar entry types of the exact Alpine packages. Root cause:
    apk materializes tar hardlinks with link(), and Android SELinux
    neverallows link() to untrusted apps (the SAME neverallow M2.6
    bypassed for download commits; extraction is a different call site).
    FIX: guest sessions now run with proot's link2symlink extension
    (--link2symlink), the SAME Termux-proot extension PRoot-Distro
    enables by default - it intercepts link()/linkat() and emulates hard
    links as symlink chains, so the kernel never evaluates the denied
    operation. No binary patch, pure reuse of the proot we already ship.
    Honest difference (documented): emulated links appear as symlinks and
    each costs the file's disk space; binaries are byte-identical
    (rehearsal-proven). The broken binutils/gcc/g++ state self-heals on
    the first "apk fix" under v0.6.2.
  - M2.6.12, SELECTIVE /proc SYSDATA OVERLAY (Termux PRoot-Distro's
    architecture, adapted): Android SELinux denies untrusted apps read
    access to the STANDARD procfs files (stat, uptime, loadavg, version,
    vmstat). At every interactive spawn the app now probes each real
    file with a one-byte read; kernel-READABLE files are never overlaid
    (real wins), and only genuinely-denied files get a verified
    compatibility file bound file-over-file ON TOP of the real /proc
    bind. Content is derived from real host sources - uname(2) for
    /proc/version (with an explicit "PocketShell sysdata overlay" attri-
    bution marker in the file itself, superseding v0.6.1's synthesis
    refusal per the owner's direction), elapsedRealtime for /proc/uptime
    field 1, real core count + real btime for /proc/stat, the real
    hidepid-filtered pid set for /proc/loadavg's tail - with documented
    placeholders where no allowed source exists. Write hardening
    (regular-file + nlink==1 validation, drop-and-remake, NOFOLLOW,
    content round-trip verify) is a proportionate port of upstream's
    descriptor discipline. Kernel-internal entries (kmsg, kcore, ...)
    stay untouched - the ls /proc EACCES wall remains expected.
  - DIAGNOSTICS: new read-only "sysdata overlays" row (probe-only; the
    button never writes).
  - TESTS: +20 net - 312 per variant (167 app + 145 terminal-emulator),
    624 executions, 0 failures. Host rehearsal scripts/rehearse_m262.sh:
    FULL PASS 19/19 (binutils install + working toolchain through the
    emulated links, byte-identical binaries, sysdata overlays ride the
    real /proc bind, unoverlaid meminfo stays real, top/ps render).
  - DEVICE GATE: docs/TESTING.md §10 - Gate A (the five standard files
    + meminfo/cpuinfo + top as the PRIMARY gate), Gate B (ps/top under
    the overlay), Gate H (apk fix re-extract + gcc/g++/ld --version).
    Installs IN PLACE over v0.6.1/v0.6.0/v0.5.0 (same signing key;
    runtime and packages untouched).

WHAT WAS NEW IN v0.6.1-m2.6 (vs v0.6.0-m2.6):
  - DEVICE TEST RESULT (2026-09-02, SM-F711B): the M2.6 architecture is
    CONFIRMED on hardware - Diagnostics shows "apk fd-link patch: applied"
    and the interactive session binds a REAL /proc (cat /proc/meminfo
    returns the host's real values; numeric pid dirs are visible). The
    same test surfaced two behaviors the wording had not prepared anyone
    for - both are real Android policy, neither is a bug:
  - EXPECTED, NOT A BUG (1): \`ls /proc\` prints a wall of "Permission
    denied" lines (kmsg, kcore, vmcore, kpage*, sched_debug, ...) before
    the readable tail. The guest /proc IS the Android host procfs (the
    design - no re-export, no simulation) and SELinux genuinely denies
    this app getattr on kernel-internal nodes; busybox ls reports each
    denial. ps/top/htop skip unreadable entries silently - only
    directory listings are noisy.
  - EXPECTED, NOT A BUG (2): \`cat /proc/version\` is denied on this
    Samsung/One UI kernel (proc_version is not granted to apps targeting
    SDK 28). Informational only: \`uname -a\` shows the kernel banner.
    Synthesizing /proc/version from uname() was considered and REJECTED -
    fabricated content violates the no-fake rule.
  - CHANGED: the Diagnostics "Interactive /proc" row now says it up front
    ("... Host procfs: kernel-internal entries show 'Permission denied' -
    Android SELinux policy, expected"); docs/TESTING.md §10 Gate A is
    re-anchored to the readable tail + meminfo/cpuinfo with a new
    "Expected on-device (NOT bugs)" subsection; docs/M2.6-RESEARCH.md §6
    records the device result. NO runtime/launcher/profile/patch/rootfs
    changes - behaviorally identical to v0.6.0. 292 tests/variant, 584
    executions, 0 failures. Installs IN PLACE over v0.6.0/v0.5.0 (same
    signing key; runtime and packages untouched).

WHAT WAS NEW IN v0.6.0-m2.6 (vs v0.5.0-m2.5):
  - M2.6, LINUX COMPATIBILITY RECOVERY: interactive sessions bind a REAL
    /proc again — \`ps\`, \`top\`, \`htop\` work inside the Alpine guest —
    while \`apk\` keeps working EVERYWHERE (shell and app UI). No Linux
    feature was traded away; v0.5.0's no-/proc compromise is reversed.
  - ROOT CAUSE (source-verified, docs/M2.6-RESEARCH.md): apk-tools 3.0.x
    picks its download-commit strategy with is_proc_fd_ok() =
    access("/proc/self/fd", F_OK) (src/io.c; byte-identical in 3.0.6,
    3.0.8 and upstream master). With /proc visible it commits every
    download through linkat("/proc/self/fd/N", ..., AT_SYMLINK_FOLLOW);
    AOSP app_neverallows.te (neverallow all_untrusted_apps file_type:file
    link) makes the kernel return EACCES and apk cancels the whole
    download — no fallback for that errno. Without /proc it uses the
    named-tmpfile + renameat path (allowed; device-proven since v0.4.2).
  - THE FIX — GuestApkCompat: a ONE-BYTE, checksum-pinned patch to
    Alpine's OWN usr/lib/libapk.so.3.0.0 (3.0.6-r0 from the pinned
    minirootfs) turns the gate literal "/proc/self/fd" into
    "/proc/self/fX", so is_proc_fd_ok() is permanently false and apk
    always commits via renameat. Same binary version, same real
    downloads/output/exit codes, same database. The "/proc/self/fd/%d"
    script-execution literal is untouched. Reproducible via
    scripts/patch_apk_fdlink.py (two literals, exactly one code reference
    per binary — disassembly-verified per arch; hashes pinned in code).
  - ARCHITECTURE — GuestExecutionProfile (M2.6.3): the two launch
    policies are explicit on the SAME builder/proot/launcher (no
    duplicated runtime): INTERACTIVE_TERMINAL (sessions: /dev, /sys,
    shared apk cache, and /proc WHEN the patched library is verified;
    honest no-/proc fallback otherwise) and PACKAGE_OPERATION
    (app-side apk execs: minimal mounts, NEVER /proc — refuse-guarded in
    the builder and pinned by tests). One rootfs, one shared cache, one
    database. The patch installs itself on the first session spawn (and
    verifies on every package operation); a user-modified rootfs is
    NEVER touched (honest NotApplicable + Diagnostics explanation).
  - PROCESS SEMANTICS (documented, not faked): with /proc bound the
    guest sees the Android host procfs filtered by the kernel's
    hidepid=2 app isolation — \`ps\`/\`top\` show the app's real process
    tree with host pids; /proc/stat and /proc/meminfo are real. Nothing
    is filtered or simulated by the app.
  - DIAGNOSTICS (M2.6.11): new read-only rows "apk fd-link patch" and
    "Interactive /proc" explain the exact state; the button never
    installs anything.
  - TESTS: +11 net — 292 per variant (147 app + 145 terminal-emulator),
    584 executions, 0 failures. Host rehearsal
    scripts/rehearse_m26_proc.sh: FULL PASS 18/18 (real /proc, ps, top,
    full apk lifecycle with /proc bound + patched libapk, one shared
    cache); the M2.4 no-/proc package rehearsal still passes.
  - DEVICE GATE: docs/TESTING.md §10 (Gates A-G: /proc, ps/top/htop,
    apk lifecycle, Node end-to-end, app-side install, interactive CLI,
    session isolation). v0.6.0 installs OVER v0.5.0 in place (same
    pinned signing key); the runtime/rootfs does NOT need reinstalling.

WHAT WAS NEW IN v0.5.0-m2.5 (vs v0.4.4-m2.4):
  - DEVICE-CONFIRMED (user screenshots 2026-09-02 10:04, SM-F711B,
    v0.4.4): Home "Installed CLI Apps" lists Nano 9.2-r0 AND Git
    2.54.0-r0 from the real apk database; Explore shows Nano
    "Installed . 9.2-r0" with Open/Uninstall; clipboard paste works
    ("I can copy paste"). M2.4 device gate: PASSED.
  - M2.5, APK-CAPABLE GUEST SHELL: the user's MANUAL \`apk update\` /
    \`apk add nodejs npm\` typed INSIDE the Linux Shell died with
    "Permission denied" (the v0.4.2 SELinux shape) while app-side
    installs worked - because v0.4.2 dropped the /proc bind from
    APP-SIDE package commands only; interactive sessions kept it
    (and read the stale rootfs-internal cache: "31 distinct packages").
    Every interactive session now spawns with the SAME SELinux-driven
    shape as package commands (RuntimeProcessLauncher.buildSessionSpec):
    NO /proc (apk commits via renameat - allowed) + the SAME shared
    apk cache binds as the UI's operations + a best-effort DNS/
    workspace refresh at spawn. One cache, one index, one database:
    install from the terminal or the UI - same result.
    HONEST COST (documented): the guest cannot see /proc, so \`ps\`,
    \`top\` and htop's process list have nothing to read inside the
    guest. A working package manager wins; Android SELinux forces
    the choice.
  - M2.5, SEARCH THAT FINDS THE PACKAGE: \`apk search\` matches names
    AND descriptions alphabetically, so "node" buried nodejs behind
    abseil-cpp-dev/ceph18/certbot-dns-linode and an 8-hit cutoff.
    Hits are now RANKED (exact name, then name prefix, then name
    contains, then the rest) and 12 are shown with a "...and N more"
    note.
  - M2.5, INSTALL ANY SEARCHED PACKAGE: every search hit gets a real
    Install button running the same honest pipeline (apk update ->
    apk add -> apk info -e verify) by exact package name. NO
    executable promise for non-catalog packages (nodejs ships \`node\`,
    not \`nodejs\`); installed hits show "Installed . version - run
    'name' from the shell". Search results join the installed-state
    probe, so a fresh install flips the row without leaving the screen.
  - v0.5.0 installs OVER v0.4.4 in place (same pinned signing key,
    keystore/debug.keystore). 281 tests per variant (136 app + 145
    terminal-emulator), 562 executions, 0 failures.

WHAT WAS NEW IN v0.4.4-m2.4 (vs v0.4.3-m2.4):
  - DEVICE GATE PASSED (user screenshots 2026-09-02 09:09-09:10, SM-F711B,
    v0.4.3): GNU nano 9.2 running in the Alpine guest, apk-tools 3.0.6-r0,
    combined Guest DNS, "Repository fetch: OK - OK: 28546 distinct packages
    available". The SELinux + DNS chains are closed. The same screenshots
    exposed three UI-layer bugs - all fixed here:
  - FIXED "installed packages are invisible": the Explore installed-state
    probe ran one shell loop over the catalog and let the loop's EXIT
    STATUS stand for the whole probe. The catalog's LAST package (python3)
    was not installed, so the last iteration exited 1, the loop exited 1,
    and the caller treated the entire exec as failed - DISCARDING the good
    stdout that contained "nano nano-9.2-r0". An installed nano rendered
    "Not installed" on every visit, deterministically, whenever the answer
    was mixed. The probe now calls absolute "/sbin/apk" (the PATH-free
    form every other apk call already used) and ends with "exit 0" - a
    completed loop is a successful probe; versions are parsed by the same
    strict parser as the single-package path ("9.2-r0", not "nano-9.2-r0").
  - HONESTY: a failed probe can no longer pose as "nothing installed" -
    timeouts/killed execs now throw PackageProbeException; Explore keeps
    the last real answer, shows "Installed state unavailable: ..." and
    renders unknown cards as "Installed state unknown"; Home does the
    same. "Not installed" is exclusively a real apk answer now.
  - FIXED Home's "No apps installed yet" over an installed nano: the list
    read an M1-era DataStore registry that NOTHING in the M2.4 flow ever
    wrote. Home now renders the catalog subset the real apk database
    confirms (fresh probe on every visit + after every package operation),
    with the real version; tapping opens via the same verify-then-launch
    flow as Explore. The orphaned registry chain is deleted outright - an
    unused registry claiming installed state is a fake-state hazard.
  - FIXED terminal Paste doing nothing: the vendored Termux selection
    toolbar's Paste action ends in the session CLIENT callback, which was
    an empty body (its comment claimed upstream performs the paste -
    false). It now reads the real clipboard and pastes via
    TerminalEmulator.paste (strips escape/C1 bytes, LF->CR, honours
    bracketed paste mode - nano-aware). Empty clipboard = honest no-op.
  - v0.4.4 installs OVER v0.4.3 in place (same pinned signing key,
    keystore/debug.keystore). All fixes are Android-layer: the runtime,
    its packages and the resolv.conf are untouched. 278 tests per
    variant (133 app + 145 terminal-emulator), 556 executions, 0 failures.

WHAT WAS NEW IN v0.4.3-m2.4 (vs v0.4.2-m2.4):
  - FIXED the "DNS: transient error (try again later)" that hit every apk
    fetch on the device (user screenshots 2026-09-02 08:13, SM-F711B,
    v0.4.2 — where the SELinux fix itself was CONFIRMED WORKING: the old
    "Permission denied" is gone and only the tapped card shows
    "Working..."). Root cause: v0.4.1-v0.4.2 wrote a DEVICE-ONLY guest
    resolv.conf — one usable resolver (the hotspot gateway 172.20.10.1;
    the second entry was a LinkProperties link-local with %wlan0 scope
    syntax that musl's inet_pton rejects) — so when that ONE resolver
    timed out, every fetch died. v0.4.3 writes a COMBINED file (device
    resolvers first, then public fallbacks 1.1.1.1/8.8.8.8, capped at
    musl MAXNS=3): musl queries all nameservers in parallel and takes the
    first answer, so one dead resolver can no longer block a fetch.
  - SELF-HEALING: the file now carries a "# managed by PocketShell"
    marker and is refreshed on EVERY package operation to the CURRENT
    network's resolvers (the old never-overwrite rule kept yesterday's
    hotspot gateway forever — a guaranteed failure after any network
    change). Legacy shapes we wrote (v0.4.0 public-only constant,
    v0.4.0-v0.4.2 bare nameserver lists incl. the %wlan0 form) upgrade in
    place on the first package operation — no runtime reinstall needed.
    User content (comments/options/search/hostnames) is never touched.
  - v0.4.3 installs OVER v0.4.2 in place (same pinned signing key,
    keystore/debug.keystore). 281 unit tests, 0 failures.

WHAT WAS NEW IN v0.4.2-m2.4 (vs v0.4.1-m2.4):
  - FIXED the remaining on-device M2.4 failure (user screenshots 2026-09-02,
    SM-F711B, v0.4.1): every apk fetch still died with "updating and opening
    ... APKINDEX.tar.gz: Permission denied" while bytes visibly downloaded.
    Root cause (verified in apk-tools 3.0.6 source + AOSP sepolicy): apk
    downloads each cached object (APKINDEX and packages) into an anonymous
    O_TMPFILE file and commits it via linkat("/proc/self/fd/N", ...,
    AT_SYMLINK_FOLLOW) — and AOSP SELinux app_neverallows.te forbids
    hardlinks for ALL untrusted apps ("neverallow all_untrusted_apps
    file_type:file link"), so the link fails with EACCES and apk cancels the
    whole download. v0.4.1's cache binds could not help (the denial is on
    the link operation, not the path) and host rehearsals never see it (no
    SELinux). FIX: package commands no longer bind /proc into the guest;
    without /proc apk uses its named-tmpfile + renameat commit path (plain
    create/rename — allowed). Rehearsed with the same apk-tools 3.0.6:
    update 28645 pkgs -> add nano -> runs -> del, cache commits land in the
    bound host dir, zero temp leftovers.
  - HONESTY: catalog cards no longer show "Working…" on every entry while
    ONE install runs (v0.4.1 bug, same screenshot) — only the target card
    does; the other cards keep their true Install/Open labels.
  - HARDENED: a cancel racing the operation start can no longer wedge the
    package manager (the single-flight lock and busy flag are now released
    by construction; the operation lands FAILED("cancelled") honestly).
  - v0.4.2 installs OVER v0.4.1 in place (same pinned signing key committed
    at keystore/debug.keystore). 277 unit tests, 0 failures.

WHAT WAS NEW IN v0.4.1-m2.4 (vs v0.4.0-m2.4):
  - FIXED on-device M2.4 failures (user recording, Samsung SM-F711B): apk
    update died with "Permission denied" / "DNS: transient error". Root
    cause: the v0.4.0 DNS repair hardcoded public resolvers (1.1.1.1/8.8.8.8)
    that are UNREACHABLE on the user's network; every apk fetch died in the
    socket layer before ever reaching dl-cdn. v0.4.1 points the guest
    /etc/resolv.conf at the DEVICE's own resolvers (ConnectivityManager /
    LinkProperties, IPv4 first), falls back to the public pair only when the
    OS reports none, and upgrades the v0.4.0 fallback file in place
    (user-written resolv.conf is never touched). New normal permission:
    ACCESS_NETWORK_STATE (read-only metadata, no traffic).
  - HARDENED: the apk download cache now lives OUTSIDE the rootfs — the
    package spec binds two app-owned host dirs over /etc/apk/cache and
    /var/cache/apk (same proot --bind mechanism as /dev,/proc,/sys), plus a
    pre-op workspace repair inside the rootfs. Rootfs permissions can no
    longer block apk.
  - HONESTY: the Explore FAILED banner shows apk's real stderr + a Retry
    button; Diagnostics "Check package environment" runs ONE real bounded
    apk update probe (explicit press) and reports the true outcome + which
    DNS servers the guest received and their source.
  - SIGNING CERT CHANGED (one-time uninstall required): sandbox reset #5
    destroyed the old debug keystore; Android debug signatures are the
    update identity, so v0.4.1 installs AFTER uninstalling v0.4.0 (runtime
    comes back in one tap). The new debug keystore is COMMITTED at
    keystore/debug.keystore and pinned in signingConfigs.debug - this is
    the last cert break: every future build is an in-place update again.
  - 12 new unit tests (271 total, 0 failures); x86_64 rehearsal re-run with
    the cache binds (update 28645 pkgs -> add nano -> runs -> del).

WHAT WAS NEW IN v0.4.0-m2.4:
  - M2.4: REAL Alpine package management. Explore CLI Apps is now a working
    frontend for the real apk inside the guest: search (real \`apk search\`),
    install (\`apk add\`), verify (\`apk info -e -v\` exit codes + POSIX
    \`command -v\`), open (new dedicated guest session running the real
    program), uninstall (\`apk del\`). The same proot exec infrastructure as
    the Linux Shell is reused; nothing is faked: no fake progress, no fake
    installed state, no fake catalog claims. Package operations run in a
    dedicated background guest process (never typed into a user session).
  - Guest DNS repair: the Alpine minirootfs ships no /etc/resolv.conf, so
    apk would fail every name lookup. Fresh installs get one at configure;
    existing runtimes are repaired in place before the first package op.
  - Curated catalog (metadata only): nano, htop, vim, git, python3. Normal
    shell commands (sh/ls/cat/df/ping...) never become launcher cards.
  - Diagnostics: explicit "Check package environment" button (apk version,
    repositories, package database, DNS) - nothing runs automatically.
  - 41 new unit tests (259 total, 0 failures).
  - M2.3 device gate PASSED on the user's device (screenshot 2026-09-01:
    guest prompt, uname/id/hello, alpine-release 3.24.1, exit clean).

WHAT WAS NEW IN v0.3.1-m2.3:
  - FIXED (device crash): tapping "Linux Shell" exited the app instantly.
    Root cause 1: AGP 8 default extractNativeLibs=false left
    nativeLibraryDir EMPTY, so a bare require() escaped the click handler
    and killed the process. Now useLegacyPackaging=true (libs extracted;
    verified extractNativeLibs=true in the built APK).
  - Root cause 2: targetSdk 36 could never run the guest anyway — Android's
    W^X policy (AOSP app_neverallows.te + seapp_contexts) blocks execve of
    app-data files for targetSdk >= 29. Now targetSdk 28 (untrusted_app_27,
    the Termux model) so proot can exec the Alpine guest shell.
  - Crash-proof launch path: pure preflight (RuntimeProcessLauncher
    .preconditionProblem), a single no-crash boundary for every session
    spawn, and an honest dismissible error banner on Home with a
    Diagnostics shortcut. A refused launch can never kill the process again.
  - 4 new unit tests (215 total, 0 failures). Same signing cert — installs
    as a direct update over v0.3.0, runtime data kept.

WHAT WAS NEW IN v0.3.0-m2.3:
  - M2.3 Linux shell: tap "Linux Shell" on Home to enter the installed
    Alpine guest through proot — SAME PTY, SAME terminal, real Linux
    userland (RuntimeProcessLauncher + TerminalSessionManager
    .createLinuxSession; honest READY-only gate)
  - proot v5.1.107.92 (termux fork, GPL-2.0) + libtalloc 2.4.2 compiled
    from pinned source for all 4 ABIs (scripts/build_proot_m23.sh);
    bundled as libproot.so / libproot-loader.so / libtalloc.so via jniLibs

WHAT WAS NEW IN v0.2.x:
  - M2.2 runtime installation layer: RuntimeState machine,
    RuntimeInstaller (HTTPS download -> size + SHA-256 verify -> guarded
    tar.gz extraction -> configure -> atomic promotion), RuntimeManager,
    RuntimeChecksum, RuntimeStorage, RuntimeDiagnostics, RuntimePin
    (Alpine 3.24.1 aarch64 minirootfs); Diagnostics install section
  - v0.2.1 FIX: tapping "Install Linux environment" crashed the app
    (missing INTERNET permission + uncontained coroutine failure).
    Now declared honestly and RuntimeCrashGuard contains any pipeline
    failure as a retryable FAILED/REPAIR_REQUIRED state

NOTE: the on-device gate for M2.3 is \`uname; id; echo hello\` inside the
guest (docs/TESTING.md §8) — the M2.4 package gate is TESTING.md §9 (install nano via the UI,
open it, uninstall).

HOW TO RESTORE THE FULL REPOSITORY (with history):

  1. Extract this archive anywhere.
  2. Run:
        git clone pocketshell-m2.gitbundle pocketshell
  3. Done. The cloned repo has the full commit history and a working
     tree identical to the sources in this archive.

HOW TO RESTORE .gitignore (excluded from this archive):

  After cloning from the bundle:
        cd pocketshell
        git checkout .gitignore

BUILDING THE APK:

  Android Studio: File > Open > select the cloned folder.
  Command line:   ./gradlew :app:assembleDebug
  (Requires JDK 17+ and Android SDK; the gradle wrapper downloads Gradle.)

Everything else in this archive is the plain, buildable working tree:
  app/  terminal-emulator/  terminal-view/  docs/  scripts/  gradle/
EOF

# --- 3. source tree: zero dotfiles, zero web scaffold, zero build outputs ---
TREE=$STAGE/$TOPDIR
mkdir -p "$TREE"
tar --exclude='./.git' --exclude='./.gitignore' --exclude='./.gitattributes' \
    --exclude='./.gradle' --exclude='./.kotlin' --exclude='./.next' \
    --exclude='./.zscripts' --exclude='./.idea' \
    --exclude='.*/' --exclude='./_*' \
    --exclude='./node_modules' --exclude='*/node_modules' \
    --exclude='./*/build' --exclude='./build' \
    --exclude='./src' --exclude='./public' --exclude='./prisma' --exclude='./db' \
    --exclude='./package.json' --exclude='./bun.lock' --exclude='./bun.lockb' \
    --exclude='./tsconfig.json' --exclude='./next.config.ts' \
    --exclude='./postcss.config.mjs' --exclude='./components.json' \
    --exclude='./eslint.config.mjs' --exclude='./next-env.d.ts' \
    --exclude='./tailwind.config.ts' --exclude='./Caddyfile' \
    --exclude='./dev.log' --exclude='./server.log' \
    --exclude='./skills' --exclude='./upload' --exclude='./download' \
    --exclude='./scratch' --exclude='./vframes' --exclude='./sheets' \
    --exclude='./tool-results' \
    --exclude='./dist-master' --exclude='./examples' --exclude='./mini-services' \
    --exclude='./tests' \
    --exclude='./.env' --exclude='./local.properties' \
    --exclude='./*.zip' --exclude='./*.tar.gz' --exclude='./*.bundle' \
    --exclude='./app/page.tsx' --exclude='./app/layout.tsx' \
    -cf - . | tar -xf - -C "$TREE"
cp "$BUNDLE" "$TREE/pocketshell-m2.gitbundle"
cp "$STAGE/RESTORE.txt" "$TREE/RESTORE.txt"

# --- 4. publish zip + tar.gz twin + bundle + apk ---
ZIP=$PUBLIC/PocketShell-$VERSION-source.zip
TGZ=$PUBLIC/PocketShell-$VERSION-source.tar.gz
rm -f "$ZIP" "$TGZ"
(cd "$STAGE" && zip -rq "$ZIP" "$TOPDIR")
tar -czf "$TGZ" -C "$STAGE" "$TOPDIR"
cp "$BUNDLE" "$PUBLIC/pocketshell-m2.gitbundle"
cp "$PROJECT/download/PocketShell-$VERSION-debug.apk" "$PUBLIC/"

# backup masters (survive download/ drains and public/ churn)
cp "$ZIP" "$TGZ" "$BUNDLE" "$DIST/"

# --- 5. sanity checks ---
echo "== sanity =="
unzip -t "$ZIP" > /dev/null && echo "zip integrity: OK"
LIST=$(unzip -l "$ZIP")
DOTS=$(echo "$LIST" | rg -c '/\.' || true); DOTS=${DOTS:-0}
echo "dot-path entries       : $DOTS  (want 0)"
echo "web shim page.tsx      : $(echo "$LIST" | rg -c '/app/page\.tsx$' || echo 0)  (want 0)"
echo "real node_modules dirs : $(echo "$LIST" | rg -c '/node_modules/' || echo 0)  (want 0)"
for key in docs/M2-RESEARCH.md docs/M2.6-RESEARCH.md docs/M2-ARCHITECTURE.md \
           docs/PROCFS-CONTRACT.md docs/PHASE-4-COMPANION-DESIGN.md \
           app/src/main/java/app/pocketshell/companion/CompanionModels.kt \
           app/src/main/java/app/pocketshell/companion/CompanionWebHost.kt \
           app/src/main/java/app/pocketshell/companion/CompanionRenderContract.kt \
           app/src/main/java/app/pocketshell/companion/WebCompat.kt \
           app/src/main/java/app/pocketshell/keyboard/KeyboardInputRouter.kt \
           app/src/main/java/app/pocketshell/ui/companion/CompanionLayer.kt \
           app/src/main/java/app/pocketshell/ui/companion/CompanionTabStrip.kt \
           app/src/main/java/app/pocketshell/ui/companion/CompanionSettingsScreen.kt \
           app/src/main/java/app/pocketshell/ui/terminal/TerminalScreen.kt \
           docs/PHASE-3.1-DESIGN.md docs/PHASE-3.2-DESIGN.md docs/PHASE-3.3-DESIGN.md \
           docs/PHASE-3.4-DESIGN.md docs/PHASE-3.5-DESIGN.md \
           app/src/main/java/app/pocketshell/runtime/GuestApkCompat.kt \
           app/src/main/java/app/pocketshell/runtime/RuntimeProcessLauncher.kt \
           app/src/main/java/app/pocketshell/runtime/GuestSysDataCompat.kt \
           app/src/test/java/app/pocketshell/runtime/GuestSysDataCompatTest.kt \
           app/src/main/java/app/pocketshell/ui/theme/TerminalTheme.kt \
           app/src/main/java/app/pocketshell/terminal/TerminalPalette.kt \
           app/src/main/java/app/pocketshell/keyboard/TerminalKeyboard.kt \
           app/src/main/java/app/pocketshell/apps/CommandApps.kt \
           app/src/test/java/app/pocketshell/apps/CommandAppsTest.kt \
           app/src/main/java/app/pocketshell/ui/home/HomeScreen.kt \
           app/src/main/java/app/pocketshell/ui/home/HomeTokens.kt \
           app/src/main/java/app/pocketshell/ui/home/HomeMarks.kt \
           app/src/main/java/app/pocketshell/ui/settings/SettingsScreen.kt \
           app/src/main/java/app/pocketshell/ui/apps/ExploreAppsScreen.kt \
           app/src/main/java/app/pocketshell/ui/theme/Theme.kt \
           app/src/main/java/app/pocketshell/ui/system/MidnightPage.kt \
           app/src/main/res/font/jetbrains_mono_nl_regular.ttf \
           app/src/main/res/font/jetbrains_mono_nl_bold.ttf \
           app/src/main/res/font/jetbrains_mono_nl_italic.ttf \
           scripts/rehearse_m262.sh \
           app/src/main/assets/guest/libapk.so.3.0.0.fdlinkoff.aarch64 \
           app/src/main/java/app/pocketshell/runtime/RuntimeInstaller.kt \
           app/src/main/java/app/pocketshell/runtime/RuntimeManager.kt \
           pocketshell-m2.gitbundle RESTORE.txt gradlew \
           gradle/libs.versions.toml docs/THIRD_PARTY.md; do
  if [[ "$LIST" != *"$key"* ]]; then
    # m4.0.4: one honest retry with a fresh listing — the old rg-on-echo
    # pipe produced a false MISSING once under memory pressure (the file
    # was in the zip all along); a pure substring test cannot flake.
    LIST=$(unzip -l "$ZIP")
  fi
  [[ "$LIST" == *"$key"* ]] && echo "present: $key" || { echo "MISSING: $key"; exit 1; }
done
FILES=$(echo "$LIST" | awk '/files$/ { print $1 }')
echo "uncompressed bytes     : $FILES  (files: $(echo "$LIST" | awk '/files$/ { print $2 }'))"

# --- 6. delivery summary ---
echo "== sha256 (paste into download page) =="
sha256sum "$ZIP" "$TGZ" "$PUBLIC/pocketshell-m2.gitbundle" \
          "$PUBLIC/PocketShell-$VERSION-debug.apk" | while read -r h f; do
  printf '%s  %s  (%s)\n' "$h" "$(basename "$f")" "$(du -h "$f" | cut -f1)"
done

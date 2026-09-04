# Phase 4 — Companion: Design Contract

Status: CONTRACT — committed before implementation (the established PocketShell
plan-first rule). Every implementation checkpoint (4.1–4.10) references this
document; deviations require a visible change to this file first.

---

## 1. Purpose

Companion is a lightweight embedded web workspace inside PocketShell: a
persistent workspace layer that lives *below* the current PocketShell screen
and is pulled up by a bottom drag handle. It hosts the user's own chosen
websites — ChatGPT, Claude, Gemini, DeepSeek, GitHub, documentation, local
dashboards, anything — as first-class workspace surfaces.

The defining experience:

> User works in the Terminal, pulls Companion up, asks ChatGPT something,
> pulls it half down, keeps typing commands, pulls it back up — the
> conversation is still there. "I never left PocketShell."

Hard product rules from the brief, restated as testable commitments:

| # | Rule |
|---|------|
| R1 | NO floating button. One bottom drag handle only; no text, no labels — a visual handle ("═══"), Midnight Sapphire. |
| R2 | NOT an AI chatbot. No AI APIs, no provider integrations. A Companion is exactly **Name + URL**; the architecture is fully generic. |
| R3 | NOT a browser. No address bar, no search bar, no back/forward/refresh buttons, no bookmarks, no menu. PocketShell owns the chrome; the website owns everything else. |
| R4 | Persistent login. Cookies + site storage survive app restarts; the user logs in once. |
| R5 | Smooth as silk. Dragging never fights webpage scrolling; the drag is 1:1 with the finger; only the handle area resizes. |
| R6 | Real websites, real file uploads (Android file picker), sensible downloads, intelligent Back. |

Explicit non-goals (Phase 4): AI/LLM APIs of any kind, browser toolbar,
bookmark manager, browser history page, full browser, background automation,
Linux-filesystem upload integration, Companion automation.

## 2. Research Findings (web runtime)

Options evaluated for the rendering engine:

| Option | Verdict | Reason |
|--------|---------|--------|
| **Android System WebView (`android.webkit`)** | **CHOSEN** | Chromium-based, auto-updated by Play, zero new dependencies, full compatibility with modern sites (JS, DOM storage, cookies, SPA routing). Runs rendering in a separate sandboxed process — heavy pages do not stall the app's UI process. File chooser (`onShowFileChooser`), downloads (`DownloadListener` → `DownloadManager`), per-tab `saveState/restoreState` and `CookieManager` persistence are all platform API. |
| GeckoView / any third-party engine | rejected | 50+ MB native lib per ABI, heavy integration, no compatibility gain for this use case — violates "do not add a massive third-party engine without clear technical reason". |
| Custom Tabs | rejected | Launches the device browser UI — violates R3 (PocketShell owns the UI; the user must never see browser UI). |
| AI provider APIs | rejected | Explicitly forbidden by R2/the brief. |

Key platform facts the design relies on (verified against current Android
guidance):

- `WebView.saveState(Bundle)` / `restoreState(Bundle)` preserve the
  navigation context **within a process lifetime**; the official guidance
  warns against storing large state in `savedInstanceState` (1 MB process
  cap) — so tab state bundles live in the in-process pool, never in the
  Activity's saved-instance-state.
- Cookies persist to disk automatically in modern WebView;
  `CookieManager.flush()` at pause makes session durability deterministic.
  DOM storage (`WebStorage`) persists in the app's WebView profile dir
  automatically when `domStorageEnabled = true`.
- Multiple WebView instances share one renderer process per app — the
  per-instance cost is the page's DOM/scripts, not a full browser stack.

No new dependency of any kind is added in Phase 4. `android.webkit` is
platform API.

targetSdk 28 note (the app's documented SELinux tradeoff, see
`app/build.gradle.kts`): every `android.webkit` API this design uses is
stable, current and non-deprecated; WebView behavior is governed by the
(Play-updated) WebView provider version, not by targetSdk.

## 3. Architecture

One new package pair, matching the existing layering conventions:

```
companion/                      (process-scoped engine layer — no UI)
  CompanionModels.kt            CompanionDef, TabRecord, validation/normalization
  CompanionRepository.kt        DataStore("companion") persistence
  CompanionWebPool.kt           WebView pool: lifecycle, LRU memory cap, trim hook
  CompanionController.kt        state holder the UI observes (tabs, active, height)

ui/companion/                   (Midnight Sapphire UI layer)
  CompanionLayer.kt             the overlay: handle + panel + strip + web host + empty state
  CompanionTabStrip.kt          inverted editor-style tabs
  CompanionSettingsScreen.kt    Settings > Companions (add/edit/delete/default/clear data)
  CompanionDialogs.kt           Midnight add/edit dialog
```

Integration points (all verified against the current code):

- `MainActivity.PocketShellRoot` currently renders `Scaffold { when(screen) }`
  with a root `BackHandler`. Phase 4 wraps the Scaffold in a `Box` and
  composes `CompanionLayer` **after** it, so the layer overlays every screen
  (Home, Terminal, Packages, Settings, Diagnostics) and its BackHandler is
  registered after all screen-level handlers → web-back wins first, by
  composition order, only when the panel is raised.
- `PocketShellApp.onCreate` gains `CompanionWebPool.init(this)` (same
  process-scoped pattern as `PackageGateway.init`).
- A `CompanionViewModel` (AndroidViewModel, same shape as
  `SettingsViewModel`) is created once in `PocketShellRoot` so Companion
  state survives screen switches; the Activity is `singleTask` with
  `configChanges` handling rotation, so the pool survives rotation too.
- Settings navigation: the existing string-screen router gains
  `"companionSettings"`; `SettingsScreen` gets one new section entry point
  ("Companions" → count + default, tap opens the management screen).

## 4. Companion Data Model

```kotlin
@Serializable
data class CompanionDef(
    val id: String,        // stable UUID string
    val name: String,      // user label, 1..40 chars, trimmed, non-blank
    val url: String,       // normalized https URL (see below)
)

@Serializable
data class TabRecord(
    val defId: String,     // a tab IS an instance of a Companion definition
    val lastUrl: String?,  // cold-restore anchor (last committed URL)
)
```

- A Companion is **Name + URL** — nothing else (R2). Tabs reference
  definitions by id; a definition can be opened in multiple tabs.
- URL normalization/validation (pure functions, unit-tested):
  - trim; reject empty; reject whitespace.
  - no scheme → prepend `https://`.
  - accepted schemes: `http`, `https` only. `javascript:`, `file:`,
    `data:`, `about:`, `intent:` and any other scheme are **rejected at
    definition time** (defense in depth; the WebView also gets a
    `WebViewClient` policy, §13).
  - `URI` must parse; host must be non-empty.
  - No host allowlist/denylist — the user decides what a Companion is.
- Quick-add templates (optional checkpoint 4.2 nicety): static
  Name+URL pairs (ChatGPT, Claude, Gemini, DeepSeek, GitHub) that
  pre-fill the dialog; the user edits everything; zero runtime
  special-casing.

## 5. State Model & Persistence

`CompanionRepository` (DataStore `"companion"`, same
`preferencesDataStore` pattern as `SettingsRepository`) persists exactly:

| Key | Content | Notes |
|-----|---------|-------|
| `defs` | JSON list of `CompanionDef` | order = management order |
| `defaultId` | String? | which Companion opens first |
| `tabs` | JSON list of `TabRecord` | ordered open tabs |
| `activeTab` | String (defId of active tab, "") | selection |
| `panelHeight` | Float fraction 0.02..0.94 | last user height; `~0` = collapsed |
| `companionEnabled` implicit | layer exists always | no on/off wording anywhere (R1) |

Honest persistence boundaries (documented, not hidden):

- **Across tab switches / app background / collapse+raise:** the live
  WebView keeps everything — page state, scroll, SPA state, login.
- **Across process death:** definitions, tabs, default, active tab,
  height, and each tab's `lastUrl` persist; the page reloads from
  `lastUrl`; login persists via cookies (§7); in-page (non-cookie) state
  does not survive process death. This is the same contract real mobile
  browsers offer.
- **Cold start with `tabs` non-empty:** the panel starts collapsed; the
  first pull-up restores the last active tab (recreate WebView →
  `restoreState` is unreliable cross-process, so `loadUrl(lastUrl ?:
  def.url)`).

## 6. Multiple Tabs

- Tab model: ordered list; `+` appends a tab; selection by defId; close
  removes; closing the active tab selects the nearest surviving neighbor
  (left, else right, else none); `+` with an existing tab for the same
  definition focuses it instead of duplicating (simplest predictable rule).
- Switching tabs: attach the target tab's WebView, detach the previous
  one (kept alive in the pool, paused) — **no reload on switch** (R: "do
  not unnecessarily destroy and recreate").
- Tab strip UI (§11) allows horizontal scrolling when overflow; tabs keep
  a readable min width.

## 7. Login / Session Strategy (R4)

- `CookieManager.getInstance().apply { setAcceptCookie(true);
  setAcceptThirdPartyCookies(webView, true) }` — third-party cookies are
  required by several real login flows (Google SSO inside embedded
  webviews, Auth0-style flows). Security tradeoff accepted deliberately
  and documented here.
- `domStorageEnabled = true` (localStorage/sessionStorage — ChatGPT,
  GitHub and friends require it).
- `CookieManager.flush()` called when the Activity pauses (and after
  login-sensitive navigations are not specially treated — flush at pause
  is the deterministic guarantee).
- All browser data lives in the app's standard WebView profile dir
  (`app_webview/`), cleared by Android when the app is uninstalled, and
  clearable by the user via Settings → Companions → **Clear web data**
  (`CookieManager.removeAllCookies` + `WebStorage.deleteAllData` +
  `WebViewDatabase` clearFormData; quiet destructive action).
- No UA spoofing: the default WebView user agent is kept (honesty rule;
  the real ChatGPT/GitHub mobile web experiences are designed for it).

## 8. WebView Lifecycle & Memory Strategy

`CompanionWebPool` (process-scoped `object`, like `TerminalSessionManager`):

- One WebView per open tab, created lazily on first need.
- **Alive set cap: active + 4 background.** LRU order; creating/activating
  a 6th tab evicts the least-recently-alive background tab: its
  `saveState` bundle is kept in the pool and `webView.destroy()` is
  called. Re-activating an evicted tab recreates a WebView,
  `restoreState(bundle)` (falls back to `loadUrl(lastUrl)`).
- Background tabs are detached from the composition and `onPause()`d;
  the **active tab is NOT paused when the panel is collapsed** — pull the
  panel down mid-conversation and ChatGPT keeps streaming (the defining
  workflow, §1). The Activity's `onPause` pauses all of them (system-
  consistent), `onResume` resumes the active one.
- `onTrimMemory(level >= RUNNING_LOW)`: destroy all background WebViews
  (saveState first); tabs remain in the strip and restore on demand.
- WebView settings (all explicit, security-balanced, §15 of the brief):
  `javaScriptEnabled`, `domStorageEnabled`, `mixedContentMode = NEVER`,
  `allowFileAccess = false`, `allowContentAccess = false`,
  `mediaPlaybackRequiresUserGesture = true`, `safeBrowsing` default-on,
  default UA, zoom off (viewport meta respected via `useWideViewPort`).
- WebView factory sets a `WebViewClient` + `WebChromeClient` up front
  (§12–§14 policies); `onRenderProcessGone` → destroy the dead WebView,
  mark the tab for reload (never crash — the platform can kill the
  renderer under memory pressure).

## 9. Drag Behavior (R5 — smooth as silk)

- Anchors (fractions of full height): **collapsed 0**, **half 0.55**,
  **near-full 0.94** (handle always reachable; R: never trapped).
- Drag: `detectVerticalDragGestures` on the **handle + strip zone only**;
  delta maps 1:1 (px-perfect, no multipliers) to panel height. While
  dragging, the panel container re-lays out every frame (cheap — one
  Box), but the **WebView's measured height is frozen at the last settled
  value, bottom-aligned** in the container — the page does NOT reflow
  under the finger; the excess is clipped. On release the WebView is
  resized once to the settled height.
- Release logic (gentle, never aggressive): if the released height is
  within 6% of an anchor → animate to it (140 ms, FastOutSlowIn, matching
  the tab animations); otherwise **stay exactly where released** and
  persist that height. No velocity flinging in Phase 4 (predictability
  over cleverness).
- Height persistence: settled fraction → DataStore (`panelHeight`), read
  on next launch; starting collapsed until first user drag-up.
- IME: the panel container carries `.imePadding()` — when a site focuses
  its chat input, the panel (and WebView) lift above the keyboard and the
  WebView's own scroll-into-view handles the caret.

## 10. Gesture Policy (R5)

| Zone | Gesture | Destination |
|------|---------|-------------|
| Handle bar (24dp touch zone) + tab strip | vertical drag | Companion height |
| Tab strip | horizontal scroll | tab overflow |
| Everything inside the panel below the strip | all gestures | the WebView, untouched |

No horizontal/vertical interceptors over the web content, no
nested-scroll bridges — the WebView receives raw pointers. The only
Companion-owned touch surfaces are the handle, the strip, and the tab
close buttons.

## 11. UI Structure (Midnight Sapphire)

All colors/fonts from the existing token objects (`TerminalTheme`,
`HomeTokens`) — zero new palette values.

```
┌──────────────────────────────────┐
│  (PocketShell screen — untouched)│
├────────── panel top edge ────────┤  hairline + 2.5dp Sapphire hairline on the
│ ▉ Tab ▉ Tab ▉ Tab         [+ ▕  │  active tab (inverted editor language)
├──────────────────────────────────┤
│                                  │
│          WEB CONTENT             │  canvas bg (0xFF080F1D) behind the site —
│                                  │  load flashes are Midnight, never white
├──────────────────────────────────┤
│              ═══                 │  handle: 36×4dp bar, chrome tone → accent
└──────────────────────────────────┘  on touch; navigationBarsPadding'd
```

- **Tab strip (inverted Phase 3.1 language):** 44dp strip in
  `TerminalTheme.tabStrip`; tabs hang DOWN with rounded *bottom* corners
  (`tabTopRadius`); active tab painted in the canvas color **cutting the
  strip's bottom hairline** so it opens into the web content; inactive
  tabs transparent + left separator hairline; active tab carries the
  2.5dp Sapphire hairline at its *bottom* (mirror of the terminal's top
  edge). Close ✕ on selected tabs (the terminal's rule — close affordance
  on the selected tab only). Horizontal `LazyRow`, min tab width 96dp.
- **Handle:** centered 36×4dp rounded bar, 24dp full-width touch zone,
  `TerminalTheme.divider` at rest, `accent` while pressed/dragged. No
  text, no glyph, no label (R1).
- **Empty state** (no definitions configured): centered on canvas —
  mono title "Your Companion", one dim line ("Add a website you use
  while working."), one `MidnightFilledButton` "+ Add Companion" →
  Settings → Companions. No cards, no illustrations (brief §24).
- **Panel chrome budget:** strip + handle. Nothing else (R3).

## 12. File Chooser Policy

- `WebChromeClient.onShowFileChooser` → `ActivityResultContracts.GetContent`
  (single file, Phase 4 scope) launched with the site's first accept type
  (fallback `*/*`); result delivered via `filePathCallback.onReceiveValue`
  (null-safe on cancel — the callback must be invoked exactly once).
- Normal Android storage picker; no Linux-guest integration (explicit
  non-goal).
- Camera capture inputs: not wired in Phase 4 (sites fall back to their
  no-camera UI path); documented.

## 13. Download Policy

- `WebView.setDownloadListener` → for http(s): enqueue
  `DownloadManager.Request` with destination
  `setDestinationInExternalFilesDir(context, DIRECTORY_DOWNLOADS, name)`
  — **no runtime permission needed** (app-specific storage), never
  crashes on duplicate names (timestamped suffix), Toast confirms the
  download started. Files land in
  `Android/data/app.pocketshell/files/Download/` (documented in the
  device gate).
- Any other scheme: ignored safely.

## 14. Back Navigation Policy

Registered as the **last** (innermost) BackHandler, enabled only while
the panel is raised; evaluated in order:

1. Active tab's WebView `canGoBack()` → `goBack()` (webpage history).
2. Panel raised and no web history → **collapse the panel** (bottom-sheet
   convention — Back never traps the user, and never yanks them off the
   screen they were working in).
3. Otherwise the handler disables itself and normal PocketShell
   navigation proceeds (existing screen `BackHandler`, then Home).

## 15. External Link & Intent Policy

`WebViewClient.shouldOverrideUrlLoading` (both the view-origin and the
request-origin paths):

- `http`/`https` → return false: navigation continues **inside
  Companion** (default preference).
- `mailto:`, `tel:`, `sms:`, `geo:`, Play Store and other resolvable
  schemes → `Intent.ACTION_VIEW` attempt; no handler → one honest Toast
  ("No app can open this link"), navigation not silently broken.
- `intent://` URIs → parsed with `Intent.parseUri`; same resolution
  policy; fallback to the URI's browser-fallback URL when present.
- `javascript:`/`file:`/`data:` navigation attempts from the page are
  blocked (return true, ignored) — same allowlist as §4.
- `window.open`/`target=_blank`: multi-window stays **off**; such links
  navigate in the same tab (standard WebView behavior; GitHub/ChatGPT
  flows verified to be plain anchor navigations).
- `onPermissionRequest` (camera/mic/geo): **denied** — Phase 4 grants no
  device capabilities to web content; documented honestly.

## 16. Security Considerations

- Definition-time URL allowlist (`http`/`https` only) + runtime
  navigation allowlist (§15) — javascript:/file:/data: cannot enter
  through either path.
- `allowFileAccess=false`, `allowContentAccess=false`,
  `mixedContent=NEVER`, Safe Browsing default-on.
- Third-party cookies: enabled for real login flows (§7) — accepted,
  documented tradeoff; user-controllable via Clear web data.
- No device capabilities (camera/mic/geo) granted to web content.
- Web data is app-private storage; uninstall or the explicit Clear
  action removes it.

## 17. Checkpoints (implementation order, each gated by build+tests)

| # | Checkpoint | Content |
|---|-----------|---------|
| 4.1 | Architecture | Models, validation, repository, controller state logic (+ unit tests) |
| 4.2 | Settings | Companions management screen, add/edit/delete dialog, default selection, clear web data, quick-add templates |
| 4.3 | Layer + handle | Overlay in root, handle, drag, anchors, height persistence, empty state |
| 4.4 | Web runtime | Pool, WebView factory, settings, client policies |
| 4.5 | Sessions | Cookie/DOM-storage enablement + flush-at-pause (inside 4.4's factory; verified at gate) |
| 4.6 | Tabs | Strip UI, create/switch/close/restore semantics |
| 4.7 | Upload | File chooser bridge |
| 4.8 | Navigation | Back policy wiring + external link policy |
| 4.9 | Restoration | Cold-start restore of defs/tabs/active/height/lastUrl |
| 4.10 | Polish | Animation timing audit, IME, memory trim, visual sweep vs. Midnight tokens |

## 18. Test Plan

Unit-tested (JUnit, no Robolectric, no WebView fakes — the established
project rule):

- URL validation/normalization (scheme prepending, rejected schemes,
  malformed hosts, trimming, length caps).
- Name validation (blank, trim, length).
- JSON round-trip of defs/tabs (serialization stability).
- Tab reducer: create/focus-dedup/close/neighbor-select/active fallback
  when a definition is deleted; ordering stability.
- Default-companion fallback logic (deleted default → first definition →
  none).
- Back-decision pure function: `(canGoBack, raised) → WEB_BACK |
  COLLAPSE | PASS_THROUGH`.
- Height anchor math: snap-vs-stay decision, fraction clamping, collapsed
  threshold.

Device-only behavior (no fakes): WebView rendering, cookies, drag
smoothness, file chooser, downloads, IME — gated by §19.

## 19. Device Test Gate (docs/TESTING.md §17 — Phase 4)

Login persistence / drag experience / tabs / file upload / navigation /
performance / PocketShell regression — the full 35-step sequence from the
brief (§33) becomes §17 verbatim, including the `apk update` and
command-app launch regression steps.

## 20. Versioning & Delivery

- Version: **0.7.0-m4.0, versionCode 24** (the mX.Y scheme continues;
  m4.0 marks the first Phase 4 delivery). Same pinned keystore
  (`d96a6f66…bf659`), in-place update over vc16..vc23, runtime preserved.
- Delivery chain (unchanged): full suite → `assembleDebug` → aapt2/apksigner
  verification → `make_payload_m2.sh` (m4.0 block) → three-way mirror →
  delivery page + `download/README.md` hashes → CHANGELOG/ROADMAP/TESTING
  → clean commits → HTTP verification.

## 21. Technical Limitations Reviewed (the brief's STOP rule)

No blocking limitation found. Honest boundaries accepted by design
(already stated): cold-process-start reloads `lastUrl` (page state, not
login, is lost — identical to mobile browsers); camera capture inputs
not wired in Phase 4; no velocity fling on the drag. None of these
change the promised experience.

## 22. Hotfix m4.0.1 — Startup Decoupled from WebView Provider Health (device-reported 2026-09-05)

Failure: on a Samsung/microG device with a freshly updated WebView
package, m4.0 crashed on every launch before any UI (Samsung Device Care
offered "Uninstall WebView updates?"). Mechanism: the m4.0 init path
called `CookieManager.getInstance()` during `Application.onCreate`,
which synchronously loads the entire WebView provider before any UI —
so a provider that crashes at init killed every app start, although the
Companion was never opened.

Amendment to §3/§8 (binding): **Application startup must never touch
`android.webkit`.** `CompanionWebPool.init()` is a context handoff only.
The provider is loaded exactly once, lazily, at first WebView creation
(`configureCookiesOnce()` + guarded `createWebView`). Any provider
failure is recorded in `CompanionWebPool.runtimeFailed`; `acquire()`
returns null and the layer renders the honest "Companion unavailable"
notice (§9 language, no cards). The terminal and every other screen are
unaffected by the provider's health. `pauseAll()` never loads the
provider incidentally; `clearWebData()` guards all provider touches.
The promised Companion experience is unchanged on healthy devices;
the change is purely: broken provider → graceful degradation instead of
a dead app.

## 23. Hotfix m4.0.2 — Failure Surfaces: the Canvas Is Never Silently Blank (device-reported 2026-09-05)

Finding: with the provider fixed by §22, a raised Companion tab still
rendered a pure WHITE canvas — no page, no error. Silent failure is a
contract violation (the project's honesty rule; §15's "never silently
broken" applied to the canvas itself).

Amendment (binding, extends §8/§9/§15):
- Main-frame load failures (`onReceivedError`, `isForMainFrame` only) set
  a per-tab failure state rendered as a Midnight card in-canvas: title,
  the real error string, the installed WebView provider version, a hint,
  and Retry. Subresource errors never surface.
- `onRenderProcessGone` returns true after destroying ONLY the crashed
  view (the platform default kills the whole app) and sets the
  "renderer crashed" failure. PocketShell survives any single page.
- A committed navigation (visit-started) clears the tab's failure; Retry
  drops the failure and re-creates the tab's WebView from scratch.
- Failure state is in-process only (never persisted), exactly like
  page titles.
- Honest boundary: a page that commits but renders blank because an old
  WebView cannot run its JavaScript fires NO error — documented; the
  version line + a static-site Companion test identify that case.

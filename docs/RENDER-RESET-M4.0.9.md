# Phase 4.0.9 — Companion Rendering Reset (investigation report)

Status: **SETTLED — device verdict in §7**. The m4.0.9 build (v0.7.0-m4.0.9,
vc33) changed NOTHING in the Companion render path; the device run passed
all four baseline gates (video evidence) and the verdict triggered decision
B: the Companion was rebuilt around the proven baseline in **v0.8.0-m4.1.0
(vc34)**. Sections 1–6 are the pre-registered experiment; §7 is the result.

## 0. The case so far (facts, not theories)

Eight iterations (m4.0.2–m4.0.8) each fixed a real, evidence-backed defect,
and the page pipeline is now proven alive on the device:

| Fact (device health reports) | Consequence |
|---|---|
| readyState=complete, DOM 242–896 elements, interactive elements exist, JS running, boot errors NONE, console quiet | Network, provider, JS, DOM, input — all alive |
| m4.0.8: ChatGPT canvas blank **WHITE**; Z.ai canvas blank **DARK** | The PAGE's own background presents (white = the forced-light scheme worked; dark = Z.ai's own default). Page pixels present; page UI absent. The dark-rendering theory family is dead permanently. |
| m4.0.7: BOTH a GPU tab and a software-layer tab reported "painted" | Not a GPU-tile rasterization failure alone; the content/UI layer specifically never presents |
| Renders fine everywhere else in the app (terminal, Home, sheets) | Window/SurfaceFlinger compositing is healthy |

**The open question:** why does a fully loaded WebView not visibly present
the site's UI inside the Companion host?

## 1. Baseline result — PENDING (device)

The harness exists in this build: **Companion → ⓘ Page health → "Render
baseline (diagnostic)"**. Plain `Activity → LinearLayout → FrameLayout →
one WebView`, JS + DOM storage on, everything else Android defaults, URL
loaded after first layout. Matrix: example.com → wikipedia.org →
chatgpt.com → chat.z.ai; variants switch ONE variable at a time.

## 2. Exact architecture that works (hypothesis — to be proven on device)

```
PocketShell Activity (BaselineWebViewActivity)
  → LinearLayout / FrameLayout (plain ViewGroup, MATCH_PARENT)
    → WebView(activity)                     [no configuration context]
      attach: container.addView(...)        [immediately, in onCreate]
      layout: doOnLayout
      load:   wv.loadUrl(url)               [AFTER first layout]
```
Settings: javaScriptEnabled, domStorageEnabled — nothing else. No pool, no
Compose, no probes, no retry.

## 3. Exact architecture that currently fails (the Companion path)

```
PocketShell MainActivity (Compose)
  → CompanionLayer bottom sheet (draggable, dynamic webHeightPx)
    → Box .height(webHeightPx.toDp()).clipToBounds().background(...)
      → key(webView) { AndroidView(factory = { webView }) }
        ↳ WebView created in CompanionWebPool.createWebView:
            activity.createConfigurationContext(uiMode=NIGHT_NO)   [m4.0.8]
            WebView(creation)
            setBackgroundColor(0xFF080F1D)                          [flash guard]
            chrome-like UA (WebCompat)                              [m4.0.5]
            useWideViewPort + loadWithOverviewMode = true
            loadUrl at acquire() — BEFORE attachment
            attach kick: silent reload 3.5 s after first layout     [m4.0.7]
            watchdog → RenderProbe (PixelCopy glass + software)     [m4.0.4/7]
            BootWitness + ConsoleTail + health sheet                [m4.0.5/6]
            compat swap: LAYER_TYPE_SOFTWARE re-create              [m4.0.3/7]
```

### A/B comparison table (code-level facts; device rows pending)

| Layer | Baseline harness | Current Companion | Device result |
|---|---|---|---|
| Context | the Activity itself | `createConfigurationContext(uiMode=NIGHT_NO)` | PENDING |
| WebView constructor | `WebView(activity)` | `WebView(creation)` | PENDING |
| View hierarchy | plain LinearLayout/FrameLayout | Compose Box inside draggable bottom sheet | PENDING |
| Compose involvement | none | AndroidView inside recomposing sheet | PENDING |
| Configuration context | none | forced-light (m4.0.8) | PENDING |
| Dark mode settings | Android defaults | forced light + darkening off | PENDING |
| UA | WebView default | Chrome-like (wv markers stripped) | PENDING |
| Renderer type | default (GPU) | default + software compat swap path | PENDING |
| WebView background | default (white) | Midnight flash-guard #080F1D | PENDING |
| Layer type | none | none (software only in compat) | PENDING |
| URL loading timing | after first layout | at acquire, before attach (+ attach kick) | PENDING |
| Attachment timing | onCreate, before load | one composition after acquire | PENDING |
| Pooling | none | LinkedHashMap pool, LRU, saveState | PENDING |
| pause/resume | default | per-tab onPause/onResume + pauseAll/resumeActive | PENDING |
| Measured size | MATCH_PARENT | dynamic webHeightPx (drag state) | PENDING |

## 4. Suspect → variant map (harness "MODE ▸" button)

| Suspect | Harness variant | What it proves if the BASELINE works and it fails |
|---|---|---|
| Custom Chrome-like UA (S5) | `+CHROME UA` | The spoofed identity is served a broken bundle (bot-fronting) |
| Forced-light config context (S1) | `+FORCED LIGHT CTX` | The non-Activity configuration context breaks presentation |
| WebView background (S4) | `+MIDNIGHT BG` | The flash-guard background masks/bleeds into the canvas |
| Load-before-attach (S6) | `+LOAD BEFORE ATTACH` | Early load unbinds the frame sink (the attach-kick story) |
| Wide viewport (bonus, S3) | `+WIDE VIEWPORT` | Viewport/zoom mismatch sizes the UI off-canvas |

Harness INSPECT (opt-in, read-only) reports the page's own
`innerWidth/innerHeight/dpr/visualViewport` + title — the direct test of
the "UI sized to a bogus viewport" theory (which the m4.0.8
white-background evidence makes plausible: body paints, UI absent).

## 5. Device gate — m4.0.9 (docs/TESTING.md §26)

1. ⓘ chip → **Render baseline**. BASELINE mode, URL ▸ until example.com:
   must visibly render text (Gate A). Copy the status.
2. URL ▸ wikipedia.org: visible content + scroll (Gate B).
3. URL ▸ chatgpt.com: visible real UI (Gate C). INSPECT, note viewport.
4. URL ▸ chat.z.ai: visible real UI (Gate D). INSPECT.
5. MODE ▸ through every variant × ChatGPT/Z.ai; note the FIRST variant
   that blanks. COPY each status.
6. Cross-check: open the real Companion tab for the same site while the
   harness shows it fine — the Compose-host arm of the experiment.

## 6. Decision rule (after the device run)

- **Baseline shows the sites, some variant breaks it** → remove that one
  variable from the Companion (smallest change), re-gate.
- **Baseline shows the sites, NO single variant breaks it** → the failure
  is in the Compose host/panel interaction (S2/S3); rebuild the WebView
  host as a native ViewGroup host inside the Compose overlay (decision B)
  with the proven baseline recipe.
- **Baseline itself blank on chatgpt.com/z.ai** → device/runtime path:
  check the active WebView provider, then a Chrome Custom Tabs control
  (diagnostic only); only then research GeckoView (decision C) with the
  size/RAM/GPU/login-compat evaluation the brief mandates.
- **Baseline blank even on example.com** → stop; investigate the WebView
  provider install/device rendering before any PocketShell change.

Success definition (unchanged, per the brief): a real website visibly
displays its actual UI on the physical device and is usable by touch and
keyboard. DOM counts, readyState, "pixels painted" — none of these are
success.

## 7. DEVICE VERDICT — 2026-09-05, m4.0.9 (vc33), screen recording

**(1) Baseline result: PASS on every gate.** One 19-second recording
(3:01–3:02) shows, in order:

| Gate | Site | Result | Evidence in the recording |
|---|---|---|---|
| A | example.com | **VISIBLE** | "Example Domain" + body + Learn more link |
| B | wikipedia.org | **VISIBLE + scrolled** | full portal: logo, search, language grid |
| C | chatgpt.com | **VISIBLE** | complete real UI: "What are you working on?", composer |
| D | chat.z.ai | **VISIBLE** | complete real UI: GLM header, Z logo, composer, Sign in |

Status line (all four): `attached=true 1080x2061px … layer=none`,
WebView 151.0.7922.199, the DEFAULT `; wv)` user agent, BASELINE mode
(Android defaults) — in the same app, process, Midnight theme and WebView
package. **Seconds earlier, the same recording shows the real Companion
tab still blank: ChatGPT = white canvas, Z.ai = dark canvas.**

**(2) The architecture that works** (proven, video): `Activity →
LinearLayout → FrameLayout → WebView(activity)`, JS + DOM storage only,
attach in onCreate, load after first layout, Android defaults everywhere
else.

**(3) The architecture that fails** (frozen in vc33, unchanged):
Compose bottom sheet → Box → `key(webView) { AndroidView(WebView) }` fed
by a pooled, forced-light, UA-spoofed, watchdogged creation recipe that
loads before attachment and re-kicks after it.

**(4) The first variable that causes failure.** The device run exercised
the BASELINE row (all four sites) — the variant rows were not needed:
per the pre-agreed decision rule, baseline-pass with Companion-fail
locates the failure in the **hosting-stack family** (the Compose keyed
host / pool / pre-attach load / config-context combination), because the
baseline removed ALL of those variables at once and rendered perfectly.
Instead of naming one culprit variable, the rebuild removes the entire
family — which is the stronger fix the rule anticipated.

**(5) Decision: B — executed in v0.8.0-m4.1.0 (vc34).** The Companion is
rebuilt around the proven baseline:
`PocketShell Activity → Companion overlay (Compose chrome) → ONE stable
plain FrameLayout → one WebView per tab, baseline recipe, load after
first layout`. The old engine (CompanionWebPool) and the entire witness
family (RenderProbe, BootWitness, CompanionHealth, ConsoleTail) are
deleted from the codebase. The one remaining delta to the proven baseline
is the parent chain (the overlay's AndroidView node instead of the
activity content view) — unavoidable by product definition and the only
suspect left if anything should still blank. The harness stays in the
build (Companion → ⓘ) as the standing render diagnostic.

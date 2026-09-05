# Phase 4.0.9 — Companion Rendering Reset (investigation report)

Status: **investigation build shipped — device rows PENDING**. This build
(v0.7.0-m4.0.9, vc33) changes NOTHING in the Companion render path. It
ships the mandated minimal baseline harness and this report. The device
run fills the pending rows; only then is the final architecture chosen.

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

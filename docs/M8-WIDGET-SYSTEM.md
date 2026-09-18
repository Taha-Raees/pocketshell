# M8 — Home Linux Widget System (architecture)

Status: M8 delivered on `agent-L/m8-home-widgets`; **M8.2 re-architected the
hero area into the ONE Home Application Card** (Servers as the first
PocketShell-native single-page application); **M8.3 evolves it into the
Home Application carousel** (multiple user-selected applications, swipe to
change; Git and SSH added as the next two). Research/candidate table:
`docs/M8-WIDGET-RESEARCH.md`. Device gates: `docs/TESTING.md` §64–§66;
M8.3 gate §67.

---

## 1. Problem and product shape

Home owns ONE hero area: the **Home Application carousel** (M8.3).

    PocketShell Home
        ↓
    Home Application carousel (horizontal swipe, snap-per-app)
        ↓
    multiple user-selected Home Applications
        ↓
    each application owns ONE full-width application canvas

The user remains on Home; actions transform the current application's
content inside its canvas (Servers overview → detail; Companion; …).
The canvas dimensions come from the existing Home layout
(`HomeTokens.homeAppCardHeight`), and the information hierarchy adapts to
them — applications are designed FOR the canvas, never scaled desktop
layouts. The carousel order, membership and per-application state
persistence are described in §3b.

## 2. Framework (the smallest extensible design)

```
app.pocketshell.widget
├── WidgetModels.kt          WidgetSpec, WidgetTone/WidgetPalette, WidgetSlotEntry, WidgetSlotsCodec
├── HomeWidget.kt            abstract HomeWidget + WidgetContentContext + WidgetNav
├── WidgetRegistry.kt        the ONE built-in list; resolve(ids) → Resolved | Missing
├── HomeWidgetSlotsRepository.kt   ONE Preferences DataStore ("home_widgets"), ONE key
├── HomeWidgetsViewModel.kt  process-scoped slot owner (assign / restore)
├── TerminalWidget.kt        core slot-1 (historical TerminalTile, unchanged)
├── LinuxWidget.kt           core slot-2 (historical LinuxTile, unchanged)
├── ServersWidget.kt         NEW — listening ports + owners (procfs, no proot)
├── StorageWidget.kt         NEW — Linux-side storage (on-demand cached walk)
├── AgentsWidget.kt          NEW — M7.2 claims projection, aggregated
├── AgentActivitySummary.kt  pure claims → rows/headline (parity vocabulary)
├── WidgetMarks.kt           original marks in the HomeMarks stroke family
├── probe/PortProbe.kt       /proc/net/tcp(6) parse + fd inode→pid scan
├── probe/StorageScan.kt     NOFOLLOW tree walk + budget + human bytes
└── external/                declarative catalog widgets (data, never code)
    ├── WidgetManifest.kt          @Serializable schema
    ├── WidgetManifestValidator.kt strict parse + validation
    └── DeclarativeWidgetRenderer.kt  manifest + probe result → card lines

app.pocketshell.ui.home.WidgetHost.kt   the hero-row host: chrome, Missing card, PressableScale
app.pocketshell.ui.settings.HomeWidgetsScreen.kt  Control Center slot pickers
widgets/                                 the catalog repository format (spec + examples)
```

Design decisions, each anchored to an existing repo mechanism:

- **Registration is data, not framework.** A widget is one object with a
  `WidgetSpec` + tap contract + `Content`. Adding widget #N = one file + one
  line in `WidgetRegistry.builtIns` + (optionally) a Control Center picker
  row that renders itself from `specs`. The host (`WidgetHost.kt`) never
  learns about individual widgets. This mirrors `CommandAppCatalog`'s
  "extensible by adding one entry" discipline.
- **The host passes state DOWN; widgets never collect their own truth.**
  `WidgetContentContext` carries what Home already holds reactively
  (runtime state, session count, agent claims). Widgets that need probes
  (Servers, Storage) own a probe object in the widget package — the
  M7.2 P8 boundary test's "Home never polls /proc" pin is extended by
  `HomeWidgetContractTest` to cover the whole HomeScreen file.
- **Persistence is the established pattern**: ONE Preferences DataStore
  file (`home_widgets`), ONE key (`slot_widget_ids`), a codec whose
  absent/corrupt path is the defaults, exactly two slots enforced by
  `fillToTwo` (fill gaps with the primary core default, trim overflow).
  No migration machinery: the record is two well-shaped ids; anything else
  degrades honestly.
- **Missing widgets are stated, never substituted**: an id that no longer
  resolves renders a "Missing widget — Manage in Settings" card showing
  the id. A removed catalog widget can never crash Home or silently
  become a different widget.
- **Navigation stays the app's one mechanism.** Widgets act through
  `WidgetNav` (terminal / guest / diagnostics / guest-files / widget
  settings), implemented once in MainActivity from the existing callbacks.
  No new navigation system, no PTY writes, no notifications posted.

## 3. Application lifecycle (M8.3 carousel)

- **Persistence**: ONE ordered list — `home_app_ids` (JSON array of
  registry ids) in the home_widgets DataStore, capped at 12 entries.
  The M8.2 single record (`home_app_id`) is retained as the MIGRATION
  source: an absent list + present legacy id seeds a one-entry list, so
  every M8.2 user lands on their configured application. Absent/corrupt/
  empty → the default (`servers`). Codec: `HomeAppIdCodec` (shape-checked,
  dedup, cap; unknown well-shaped ids survive decode and render the
  honest Missing card).
- **Carousel**: `HomeApplicationHost` renders the list as a
  `HorizontalPager` with per-page identity = application id (reordering
  never mixes saved state), snap-per-page, page dots (only when there is
  something to swipe), and an honest empty-card state with the Manage
  action when the user removes everything.
- **State preservation**: each page's in-card state (detail selection,
  scroll) survives swiping away and back via the pager's per-page
  saveable holder, and rotation via `rememberSaveable`.
- **Control Center → Home applications**: the configured list (reorder
  up/down, remove), the registry's remaining applications (add), and
  Restore default (→ `["servers"]`). The empty list is a legal
  configuration.
- **Performance**: only pages adjacent to the current one stay composed;
  inactive applications do no work (their ticks are composition- and
  lifecycle-driven). No new polling exists anywhere in the framework.

## 4. The Servers widget (REAL guest servers, no kernel tables)

**M8.1 rewrite (device-proven):** `/proc/net` AND `/proc/<pid>/net` are
SELinux-denied to the app domain — per-pid tables are the SAME inode as
the global one (`/proc/net -> self/net`), so there is no per-pid escape
hatch on current Android. The shipped pipeline needs none of it and shows
REAL servers running in the Linux guest:

1. **Discover candidates** — own-UID process facts (readable as the app's
   UID: numeric pid dirs, `cmdline`, `cwd`, `fd` socket links):
   cmdlines that explicitly name a port (numeric token, `-p N`,
   `--port N`, `:N`, `host:port`) ∪ a bounded dev-port canon (≤32 ports:
   3000/5173/8000/8080/9229/19000-class defaults).
2. **Verify** — a real TCP connect to 127.0.0.1:P. The app and the guest
   share ONE loopback (proot creates no network namespace — device-
   verified), so connect success is KERNEL-FACT evidence that something
   listens. Nothing is ever shown without this.
3. **Attribute** — hold the connection open and diff socket-owning
   own-UID pids' `fd` links: the pid that GAINS a `socket:[inode]` is the
   acceptor (kernel fact; proven for threaded and single-accept servers).
   Fallback rule: exactly one socket-owning pid names the port. Both
   rules are stated in the detail dialog in plain words.
4. **Exclude** — canon-only hits that attribute to nothing own-UID are
   foreign local services and are never claimed.

Refresh policy: the idle gate skips the pipeline entirely while the
numeric pid set is unchanged and nothing was listening (a tick is one
`/proc` readdir); the full pipeline (~2 fd passes + ≤32 loopback
connects) runs when the pid set changed or servers were present, every 5 s
while Home is composed AND resumed. Tap on a server row → detail dialog
(verified endpoint, attributed PID, the project directory mapped to Linux
paths, the two supported actions: Open in Terminal, Open in Companion).
No Stop/Restart — the app does not own these processes.

**Companion integration** rides the existing companion tab machinery
(`WidgetNav.openCompanion` → `openWithUrl`) and required ONE platform
change: `network_security_config.xml` with cleartext DENIED platform-wide
(the explicit targetSdk-28 default) and a LOOPBACK-ONLY exception
(127.0.0.1/localhost) — without it the WebView fails local dev servers
with `net::ERR_CLEARTEXT_NOT_PERMITTED` (device-observed, then fixed).
Loopback traffic never leaves the device and terminates in the app's own
UID's processes.

Honest degradation: runtime not READY → "Linux not ready"; no verified
listeners → "Nothing listening" (the honest working state; the old
"Port tables unavailable" only remains if even /proc pids and loopback
connects become unavailable). Device-verified end-to-end (§65): start →
discovered → details → Companion served the server's response
(`GET / HTTP/1.1" 200` in the server's own log) → stop → empty.

## 5. The Storage widget (Linux storage, on demand)

Sizes the guest rootfs per TOP-LEVEL subtree plus the bound apk cache
(`PackageGateway.apkCacheDir`) — all app-owned host dirs, permission-free
NOFOLLOW walks with a file budget (default 200k files) whose breach sets
an honest `truncated` flag ("Used so far (partial scan)"). The scan is
**on-demand + cached** (process-scoped, refreshes only after 30 min);
nothing polls. Tap → Files at the guest root (the existing validated
folder seam). Runtime not READY → "Linux not installed yet".

## 6. The Agents widget (the ONE projection, aggregated)

Consumes exactly `AgentActivityRepository.homeSessionClaims` — the same
authoritative projection Home's Sessions rows render — passed down by the
host. Aggregation is pure (`AgentActivitySummary`): attention claims sort
first, then running, then runtime-unknown; row wording is
character-for-character the parity vocabulary ("— Running", "— Runtime
unknown", "— Requesting permission", "— Needs your input"); the headline
counts PROVEN activity only ("N active", "K unknown", or the mix). No
second detector, no /proc, no polling, no own state — pinned by
`HomeWidgetContractTest` (banned-token sweep + the M7.2 honesty ban list
over the widget's literals). This is a new authorized consumer of the P8
projection (owner-directed M8); the boundary discipline ("UI reads state,
never events") is unchanged.

## 7. Optional widgets: the GitHub catalog and the trust model

Distribution format (this repository, `widgets/`):

    widgets/
      registry.json          catalog: schemaVersion, per-widget id/name/version/manifestPath/sha256
      servers/manifest.json  example manifest (validates against the implementation)
      storage/manifest.json

In-repo initially, deliberately: the catalog is reviewable with the app,
versions atomically with releases, and needs no infrastructure while the
widget set is core-authored; a separate repository becomes right when
third parties contribute (the app must then pin its registry URL + branch,
and the fetcher should verify the catalog signature the same way
`RuntimeInstaller` pins the rootfs by SHA-256).

**Manifests are DATA, not code.** A manifest can reference only BUILT-IN
probe primitives (`proc.net.listen`, `storage.rootfs` — an exact `kind`
vocabulary; parameters confined by the validator) and a card template
(plain `{field}` substitution, bounded lengths). The renderer is fixed app
code. Unknown JSON keys REJECT (no schema drift). There is deliberately no
"run this shell command" escape hatch.

Staged trust model (documented, not faked):

1. **This milestone** — schema + strict validator + data-only renderer +
   example catalog. No installer UI, no network fetch: nothing can be
   silently installed, and nothing downloaded is ever executed. Built-ins
   only.
2. **Installer milestone** — fetch `registry.json` + manifests (HTTPS,
   checksums pinned in the registry, explicit per-widget user consent,
   installed manifests stored in app-private storage, `minAppVersion`
   enforced, enable/disable/remove UI). External widgets enter slots
   through the SAME registry seam as declarative renderings.
3. **Command-probing manifests** (only if a real need appears) — signed
   manifests (ed25519, keys pinned in the app), per-manifest capability
   display BEFORE install, user-approved execution inside the guest's
   own-UID world only. Checksums alone do NOT make remote shell strings
   safe — that is why this stage is gated behind signatures.

## 8. Performance (lab-measured; device gate re-measures)

x86 lab proxies (`docs/M8-WIDGET-RESEARCH.md` §1): `/proc/net` parse
~1.9 ms/tick; fd scan ~28–92 ms (event-driven, on inode-set change only);
30k-file walk ~122 ms (on-demand, cached). Steady-state Home cost with the
Servers widget present ≈ one ~2 ms IO tick per 5 s while resumed; Storage
and Agents cost nothing when not shown. No widget runs anything while Home
is not composed.

## 9. Testing

- **60 new JVM tests** (full suite 1199/1199 green):
  - `WidgetSlotsCodecTest` — absent/corrupt → defaults, fill/trim, distinct.
  - `WidgetRegistryTest` — unique ids, default geometry, picker metadata,
    Missing resolution, vendor-agnostic copy.
  - `PortProbeTest` — pure table parsing (LISTEN-only, v6, malformed rows,
    comm) + full snapshots against synthetic /proc trees (owner resolution
    through real `socket:[…]` symlinks, foreign-UID exclusion, port dedupe,
    inode-set reuse, unreadable/empty honesty).
  - `StorageScanTest` — subtree sizing, symlink non-follow, budget
    truncation honesty, absent-rootfs, byte formatting.
  - `AgentActivitySummaryTest` — exact parity wording, tier ordering,
    headline honesty, ban-list sweep.
  - `WidgetManifestValidatorTest` + `DeclarativeWidgetRendererTest` —
    strict schema, no command surface, template substitution.
  - `HomeWidgetContractTest` — source pins: HomeScreen probe-free; host
    resolves via the ONE registry; Agents widget consumer contract + ban
    list; ONE DataStore/ONE key; external layer execution-free; the
    example catalog validates against the implementation; readSymbolicLink
    pin; routes wired.
- Pre-existing boundary pins (M7.2 P8/P10 over HomeScreen) pass unchanged.
- Build: `./gradlew :app:assembleDebug` green → `download/M8-L.apk`
  (delivery ledger: `docs/ARTIFACT_NAMING.md` §5).

## 10. Limitations and what the device gate established

- `/proc/net` visibility on Android: **answered by the device gate —
  DENIED**, on every unprivileged path (app procfs, guest procfs, netlink
  sock_diag; §4). The Servers widget ships as an honest-degrade widget; a
  future privileged-helper path is the only honest revival.
- Idle-Home foreground CPU on the 120Hz-class tablet is dominated by the
  PRE-EXISTING Aurora per-frame draw (60 fps continuous, 0% jank; ~76–80%
  of one core with Aurora, 0 frames + 9.4% with a non-aurora identity) —
  measured and attributed in §64-G; M8 adds no per-frame work.
- Storage numbers are logical file bytes (du reports 4K-block allocation —
  same ranking, ~10% higher totals on this rootfs); a block-accounting
  refinement is a noted option.
- Visual acceptance, slot management, storage, agents parity, missing-
  widget honesty: device-verified (§64). Tmux/SSH/Processes/Build-status/
  Containers: see the research table — each is deferred or rejected for
  documented reasons, not overlooked.

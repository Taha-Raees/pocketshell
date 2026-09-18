# M8 — Home Linux Widget System (architecture)

Status: implemented on `agent-L/m8-home-widgets`, laptop-side verified (Agent L,
2026-09-18). Research/candidate table: `docs/M8-WIDGET-RESEARCH.md`.
Device gate: `docs/TESTING.md` §64.

---

## 1. Problem and product shape

Home owns two large hero cards (Terminal, Linux) that were hardcoded
composables. M8 turns them into **two widget slots**:

    Home
      ├── Widget slot 1 (default: Terminal)
      └── Widget slot 2 (default: Linux)

The slots do not know what a widget is. Home is the **host**; a widget is an
independently described **capability** that must answer, per
`M8-WIDGET-RESEARCH.md`, the product question: *why would a Linux developer
want this on their phone instead of typing a command?*

The historical two cards are preserved bit-for-bit as **core widgets**: same
chrome, same weights (1.25 : 1.0), same honest gates, same tap labels, same
wording. A fresh install or a cleared record renders exactly today's Home.

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

## 3. Slot lifecycle

- **Fresh install**: no DataStore key → defaults (`core.terminal`,
  `core.linux`) → today's Home exactly.
- **Replace**: Control Center → Home widgets → pick a widget per slot →
  ids persisted → Home recomposes.
- **Restore defaults**: one action, writes the two core ids.
- **Missing**: slot id unknown (future uninstall/renumber) → Missing card
  (see above); the stored id is preserved so a restored/reinstalled widget
  re-attaches without user action.
- **Rotation/process death**: slots live in DataStore (root-scoped
  ViewModel, `WhileSubscribed`); nothing transient is lost that matters.

## 4. The Servers widget (ports + owners, no proot)

The app and the guest share ONE Linux UID. The kernel's own tables are the
authoritative source and need no guest exec:

- `/proc/net/tcp` + `tcp6` LISTEN rows (st `0A`) → (port, socket-inode).
- `/proc/<pid>/fd/*` readlink `socket:[inode]` → owning pid; name from
  `cmdline` argv[0] basename (kernel `comm` fallback).
- Rows whose owner does not resolve are OTHER-UID sockets and are **not
  shown** — the widget never claims foreign sockets as "your servers".

Refresh policy: parse tick every 5 s **while Home is composed and resumed**
(`Lifecycle.currentStateFlow` + `collectLatest` — backgrounded cancels the
loop, leaving Home disposes it); the expensive fd scan runs **only when the
listener inode set changes**. A `canonicalPath` on `socket:[…]` links
throws (lab-verified) — the probe uses `Files.readSymbolicLink`.

Honest degradation: runtime not READY → "Linux not ready" (no probing);
tables unreadable → "Port tables unavailable"; empty → "Nothing listening".
Tap → Terminal (where servers are worked with).

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

## 10. Limitations and what needs a real device

- `/proc/net` visibility on Android is **expected** to be per-UID (exactly
  our view) but is UNPROVEN on this hardware — probed with honest
  degradation; device gate item §64.
- All performance numbers are x86 proxies.
- Visual acceptance (two-slot layout, replaced slots, Missing card,
  Control Center flow) needs eyes on a device.
- tmux/SSH/Processes/Build-status/Containers: see the research table —
  each is deferred or rejected for documented reasons, not overlooked.

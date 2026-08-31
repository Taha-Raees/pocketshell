# M2 Architecture — Runtime Layer

Milestone: M2.1 · Status: COMPLETE (architecture phase)
Companion: docs/M2-RESEARCH.md (decision rationale & evidence)

## 1. Design goal

Give PocketShell a **real, installable Linux userspace** behind a narrow
abstraction, without touching the M1 terminal foundation (PTY, session
manager, terminal view, in-app keyboard). The UI never learns how proot or
Alpine work; it only sees `RuntimeState` and a small manager facade.

## 2. Module layout (all inside `app` module)

```
app/src/main/java/app/pocketshell/runtime/
├── RuntimeState.kt          // state enum + legal-transition machine
├── RuntimeMetadata.kt       // runtime.json model (version, arch, timestamps)
├── RuntimeStorage.kt        // path layout, atomic-promotion primitives
├── RuntimeChecksum.kt       // streaming SHA-256 (verification only)
├── RuntimeInstaller.kt      // download → verify → extract → configure → promote
├── RuntimeManager.kt        // facade: state queries, install(), remove(), repair()
├── RuntimeDiagnostics.kt    // dir sizes, free space, integrity report
└── RuntimeInstallEvent.kt   // sealed progress events (honest, real-driven)
```

Future (M2.3+, listed here so M2.2 leaves room, not built yet):
`RuntimeProcessLauncher` (builds the proot argv for a session),
`CliAppDetector` (executable probes → `CliApp` registry).

No new process/exec code exists in M2.2 — installation only manipulates
files. This keeps the risky exec-path assumptions out of the first slice and
gives the state machine a device-testable surface.

## 3. Storage layout (Master Prompt §10/§12)

```
context.noBackupFilesDir/
└── runtime/                          ← final, atomically promoted, never mutated in place
    ├── rootfs/                       ← extracted Alpine minirootfs
    │   ├── bin/ dev/ etc/ home/ media/ mnt/ opt/ proc/ root/ run/
    │   ├── sbin/ srv/ sys/ tmp/ usr/ var/
    │   └── etc/alpine-release        ← present after real extraction
    └── runtime.json                  ← metadata, written LAST, only on success
runtime-download.tmp                  ← in-flight rootfs.tar.gz (any partial bytes)
runtime-extract.tmp/                  ← staging extraction root (invisible to UI)
```

Why `noBackupFilesDir`: private, no permissions, excluded from Android
auto-backup (a 25 MB+ rootfs must not sync), survives restarts/updates,
auto-removed on uninstall.

## 4. State machine (Master Prompt §9)

```
NOT_INSTALLED ──install()──▶ DOWNLOADING ──▶ VERIFYING ──▶ EXTRACTING
                               │                 │             │
                               ▼                 ▼             ▼
                             FAILED            FAILED       CONFIGURING
                                                             │
                                             ┌───────────────┤
                                             ▼               ▼
                                          READY       REPAIR_REQUIRED
        any state ──remove()──▶ NOT_INSTALLED
        READY      ──validate fail──▶ REPAIR_REQUIRED
        FAILED*    ──retry──▶ DOWNLOADING (from clean tmp)
```

Rules enforced by `RuntimeState.canTransition(from, to)`:
- No skipping stages; `READY` is reachable **only** through `CONFIGURING`,
  and only after on-disk validation passes (alpine-release present, busybox
  present, runtime.json written last).
- Any exception inside a stage → `FAILED` (with cause) — never a silent
  half-state; existing `runtime/` is never mutated by a failed run.
- Persistence: `runtime.json` `"state"` field + tmp-dir presence at startup
  determine the recovered state (orphaned tmp ⇒ cleanup ⇒ `NOT_INSTALLED`;
  valid runtime.json + intact rootfs ⇒ previous state).

M2.2 note: terminal-level validation (`uname` inside the guest) arrives with
the process launcher in M2.3. M2.2 `READY` means *structural* validation
passed; the UI copy and docs say exactly that. Nothing pretends more.

## 5. Install pipeline (Real Installer, Master Prompt §9/§10/§14)

| Stage | What actually happens | Honest progress signal |
|---|---|---|
| DOWNLOADING | HTTPS GET pinned Alpine minirootfs URL → `runtime-download.tmp` (single connection, `Content-Length` cross-check) | real bytes read / total |
| VERIFYING | size sanity (exact expected) + streaming SHA-256 vs pinned checksum | real bytes hashed |
| EXTRACTING | tar.gz → `runtime-extract.tmp/` via commons-compress; per-entry: zip-slip check, modes preserved, symlinks created, dirs/files/symlinks only | real entries processed / total |
| CONFIGURING | structural validation (alpine-release, busybox, /tmp writable); write `runtime.json` **inside staging** | deterministic |
| (promote) | atomic: `runtime-extract.tmp` → rename → `runtime/`; delete `runtime-download.tmp` | filesystem rename |
| READY | emitted only after promotion + final `runtime.json` read-back succeeds | — |

All stages run on `Dispatchers.IO` (never the main thread — Master Prompt
§28). Cancellation at any point leaves tmp dirs in a state the next run
cleans deterministically.

## 6. RuntimeManager facade (Master Prompt §8)

```
RuntimeManager (object, app-scoped singleton — mirrors M1 style)
├── observeState(): StateFlow<RuntimeState>
├── currentState(): RuntimeState            // disk-truth derived at first query
├── install(onEvent): Job                   // full pipeline, restartable
├── remove()                                // delete runtime/ + tmp, back to NOT_INSTALLED
├── repair()                                // = remove() + install() (explicit user action)
└── diagnostics(): RuntimeDiagnostics.Report
```

- State survives process death because it is *derived from disk*, not held in
  memory: first query after boot reconciles tmp dirs + `runtime.json`.
- UI listens to `StateFlow` only; no runtime logic leaks into screens.

## 7. Terminal integration (Master Prompt §16 — M2.3 preview, contract frozen now)

```
TerminalView/keyboard (M1, UNTOUCHED)
   ↓  TerminalSession (M1, UNTOUCHED)
   ↓  existing PTY  (M1, UNTOUCHED)
   ↓  session argv:
        M1:  /system/bin/sh                       (keeps working; "System Shell")
        M2.3: [nativeLibraryDir]/libproot.so -R runtime/rootfs \
              -0 root  /bin/sh -l                  ("Linux Shell", once READY)
```

One session type in M2 per the Master Prompt §17 guidance, chosen honestly:
the terminal entry point shows runtime state; if not `READY`, it shows the
truth + an Install button. No second terminal implementation, no separate
renderer, no background fake jobs.

## 8. Lifecycle & process ownership (Master Prompt §23/§24)

- Runtime = files ⇒ unaffected by Activity/Compose lifecycle by construction.
- Sessions = M1's existing `TerminalSessionManager` + foreground service —
  inspected, kept as-is for M2.2/2.3; no new service is introduced.
- Android process kill ⇒ sessions die (honest), runtime persists. Reopen ⇒
  state reconciles from disk.

## 9. CliApp detection contract (Master Prompt §19-§22 — M2.5, contract frozen now)

```
CliApp(id, name, description, executable, arguments, packageId, version,
       category, installed: Boolean /* derived, never static */, launchCommand)
```
`installed` = result of a real probe: rootfs path exists **and** guest
`command -v` succeeds once sessions exist. Home renders only verified
entries; the empty state is honest ("No CLI apps installed yet").

## 10. Testing strategy (Master Prompt §25/§26)

Unit-tested in M2.2 (JVM, no emulator required):
- state machine: every legal transition, every illegal rejection,
  FAILED-entry from each stage;
- checksum: known vectors + multi-buffer streaming;
- metadata: JSON round-trip, invalid-input rejection;
- storage: layout resolution, atomic-promotion preconditions, orphan-tmp
  reconciliation rules;
- installer: synthetic tar.gz fixtures — normal files, modes, symlinks,
  GNU long names, zip-slip rejection, checksum-mismatch rejection, failure
  leaves previous `runtime/` untouched.

Explicitly NOT unit-tested: proot itself, real downloads (device tests,
Master Prompt §27).

## 11. M2.2 explicit out-of-scope (Master Prompt §34)

No UI beyond a Diagnostics runtime section; no proot/native code; no network
stack beyond the installer's HTTPS GET; no catalog; no Hermes/Node/Python;
no Home screen changes; no keyboard changes.

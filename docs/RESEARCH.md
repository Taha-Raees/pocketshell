# PocketShell — Research (M0)

Status: **M0 complete** · Researched: 2026-08-30 · Upstream snapshot verified live (network available during research)

This document records the open-source ecosystem research required by the project
brief (§5) before any implementation. Every major component lists upstream
project, repository, license, version, compatibility, maintenance status,
integration strategy and required modifications.

---

## 1. Mandate

The project philosophy is **REUSE → INTEGRATE → OPTIMIZE → IMPROVE**.

The terminal emulator, PTY layer and key-encoding infrastructure are mature,
battle-tested problems. PocketShell must not rewrite them. PocketShell's job is
a significantly better Android-native experience *around* that infrastructure.

---

## 2. Terminal emulator candidates

| Criterion | **Termux `terminal-emulator`** | jackpal/Android-Terminal-Emulator | ConnectBot `terminal-emulator` | xterm.js (WebView) |
|---|---|---|---|---|
| Repository | github.com/termux/termux-app | github.com/jackpal/Android-Terminal-Emulator | github.com/connectbot/connectbot | github.com/xtermjs/xterm.js |
| License | **GPLv3** (lineage: Apache-2.0 jackpal code, relicensed upstream) | Apache-2.0 | Apache-2.0 | MIT |
| Upstream activity (verified 2026-08-30) | **Active** — HEAD `3b66f87` commit dated 2026-08-24 | Effectively unmaintained (archived state, last meaningful work years old) | Maintained, but coupled to SSH-client use case | Active, browser-focused |
| VT/xterm coverage | Very complete: ANSI/VT, UTF-8, WCWidth, 256-color + truecolor, alternate screen, scroll regions, rectangular ops, DECSET/DECSC | Moderate; lags modern sequences | Moderate; SSH-oriented | Very complete |
| Android view layer | **Yes** — `terminal-view` module: `TerminalView`, renderer, selection, gestures, ActionMode | Yes | No standalone view | No (DOM-based) |
| PTY/session layer | **Yes** — `TerminalSession` + JNI `libtermux` (createSubprocess/setPtyWindowSize/setPtyUTF8Mode/waitFor/close) | Own, aging | N/A (JSch-based) | N/A |
| Test suite shipped | **19 JVM unit test classes** (KeyHandler, Unicode, Resize, DecSet, ScrollRegion, RectangularAreas, TerminalRow, WcWidth, History, TextStyle, ScreenBuffer, ByteQueue, Apc, DeviceControlString, OperatingSystemControl, ControlSequenceIntroducer, CursorAndScreen, Terminal, TerminalTestCase) | Few | Few | Jest suite (JS) |
| Module self-containment (verified by import audit) | **Self-contained** — only `androidx.annotation`; zero `termux.shared` imports in both modules | Self-contained | Coupled | N/A |
| Build system | ndk-build (`Android.mk`), no CMake needed | ndk-build | — | — |

### Decision D1 — terminal engine

**Fork (vendor) Termux `terminal-emulator` + `terminal-view`.**

Rationale:
1. The most battle-tested Android terminal stack in production (millions of installs).
2. Both modules verified self-contained → vendorable without dragging `termux-shared` in.
3. Upstream is actively maintained; vendoring a pinned commit keeps diffs reviewable and re-syncs feasible.
4. Ships the exact JVM test suite PocketShell needs for M1/M1.2 acceptance.
5. `ndk-build` based JNI matches a minimal build setup (no CMake dependency).

License consequence: these modules are **GPLv3**, so the PocketShell application
is distributed under **GPLv3**. This is accepted: the project is open-source
first (§5) and permissively-licensed alternatives are unmaintained (jackpal) or
less suitable (ConnectBot). The Apache-2.0 lineage is documented in
`THIRD_PARTY.md`.

Not selected:
- **jackpal ATE** — Apache-2.0 is attractive, but the project is unmaintained; adopting a dead emulator contradicts the reliability mandate.
- **ConnectBot** — its emulator is not developed/tested as a general-purpose Android terminal widget.
- **xterm.js in WebView** — violates "the terminal must remain real/native"; input latency and process plumbing would be an in-app HTTP/WebSocket bridge, an architecture PocketShell explicitly rejects.
- **Writing from scratch** — forbidden by §6 and unjustifiable.

---

## 3. PTY and shell runtime

### 3.1 PTY creation

Verified upstream JNI surface (`terminal-emulator/src/main/jni/termux.c`, module `libtermux`):

| JNI function | Purpose |
|---|---|
| `createSubprocess` | `fork()` + setsid + TIOCSWINSZ/pts setup, exec of shell; returns master fd + pid |
| `setPtyWindowSize` | `TIOCSWINSZ` on resize |
| `setPtyUTF8Mode` | tty UTF-8 mode toggle |
| `waitFor` | blocking `waitpid` on session thread |
| `close` | master fd close |

**Decision D2**: reuse this JNI layer verbatim (vendored). No `Runtime.exec()`,
no `ProcessBuilder` — those cannot provide a controlling terminal.

### 3.2 Shell for M1

M1 uses the Android system shell `/system/bin/sh` (mksh on all shipping Android
versions), launched with a constructed environment:

- `HOME=<app files>/home` (created on first run)
- `PATH=/system/bin:/system/xbin` (+ app native lib dir later)
- `TMPDIR=<app cache>/tmp`
- `TERM=xterm-256color`
- `LANG=C.UTF-8`

This immediately satisfies the M1 minimum command set (`echo pwd ls cd mkdir rm
cat clear`) because Android's **toybox** provides all of these as `/system/bin`
applets. No userspace runtime is required for M1.

**Decision D3**: M1 ships **no** custom userspace. `/system/bin/sh` only.
Linux distro runtime (Alpine/Debian/proot) is M2 scope after real-device
validation of M1.x (§27).

### 3.3 Critical Android restriction for M2 — W^X (document now, decide in M2)

Since Android 10, apps with `targetSdkVersion >= 29` are **denied `execve()` on
any file inside app-writable storage** (`targetSdkVersion` + W^X enforcement,
untrusted_app SELinux domain). This is *the* architectural constraint for M2:

| Option | Mechanics | Trade-offs |
|---|---|---|
| **A. targetSdk 28 build** | Upstream Termux approach — exec from `$PREFIX` remains legal | Diverges from modern Play requirements (targetSdk policy); blocks modern API behaviors |
| **B. Ship executables inside the APK** | Binaries under `jniLibs` → installed to `nativeLibraryDir`, which stays executable on any targetSdk | Every package install requires APK side-load/repack; unusable as a general package manager alone |
| **C. proot-based userspace** | Real distro rootfs under app storage, executed via proot loader shipped in `nativeLibraryDir` | proot overhead; loader must itself be an APK-shipped native lib |

Verified: upstream Termux still ships `targetSdkVersion=28` in
`gradle.properties` precisely for this reason (audited snapshot). **M2 will
re-evaluate A/B/C against then-current Play policy; no code is written now.**
M1 (targetSdk 36) is unaffected: `/system/bin/sh` is a system binary and
executing it is legal at any targetSdk.

---

## 4. Android platform restrictions (verified against snapshot + platform docs)

| Topic | Finding | Impact on PocketShell |
|---|---|---|
| W^X executable memory | Android 10+: app-writable files not executable for targetSdk ≥ 29 (§3.3) | M1: none (system shell). M2: decisive constraint, see Decision D3 |
| 16 KB page size | Google Play requires 16 KB-aligned native libs for new apps/updates targeting Android 15+ (from Nov 2025) | NDK r27+ aligns by default; we pin **NDK r28.2** and keep default alignment. Verified default in r28 |
| Foreground services | targetSdk 34+: every FGS needs a declared type; `specialUse` requires `FOREGROUND_SERVICE_SPECIAL_USE` permission | M1.2 adds a `specialUse` FGS to keep sessions alive in background; declared in manifest, documented |
| Scoped storage | targetSdk 30+: no broad WRITE_EXTERNAL_STORAGE | M1 needs zero storage permissions; terminal confined to app dirs. Future shared-storage access via SAF only |
| Process model | Background process may be killed under memory pressure; Activity recreation on rotation/theme change | Sessions live in a process-scoped singleton + FGS; UI is stateless vs sessions (ViewModel holds only selection state) |
| Keyboard/IME | In-app soft keyboards are ordinary views; system IME suppression via `showSoftInputOnFocus=false`-equivalent and input type | PocketShell keyboard is **not** an InputMethodService (per §7); TerminalView configured so the system IME never appears inside the terminal screen |
| Exec from nativeLibraryDir | Allowed; libs extracted or loaded from split APK | Reserved for M2 loaders/binaries |

---

## 5. Input architecture research (upstream contracts audited)

Audited interfaces (pinned snapshot):

- `TerminalSessionClient` — app-facing callbacks: `onTextChanged`, `onTitleChanged`,
  `onSessionFinished`, clipboard hooks, `onBell`, `onColorsChanged`, cursor style, logging.
- `TerminalViewClient` — input hooks used *by* `TerminalView`:
  `onKeyDown/onKeyUp`, `onCodePoint`, **`readControlKey()`, `readAltKey()`,
  `readShiftKey()`, `readFnKey()`**, `onScale` (pinch), `onLongPress`,
  `shouldEnforceCharBasedInput`, `shouldBackButtonBeMappedToEscape`.
- `KeyHandler.getCode(keyCode, keyMode, cursorApp, keypadApplication)` — canonical
  keycode+modifiers → escape-sequence encoder (`KEYMOD_CTRL/ALT/SHIFT/NUM_LOCK`).

**Decision D4 — keyboard input path.** The PocketShell keyboard dispatches
**synthetic `KeyEvents`** (via `KeyCharacterMap.VIRTUAL_KEYBOARD`) into the
upstream `TerminalView` dispatch path, plus a shared `KeyboardState` object that
answers the `read*Key()` modifier hooks. This reuses upstream encoding,
ctrl-combination and application-cursor handling wholesale — one single input
pipeline for letters, numbers, symbols and every terminal key (§9). Modifiers
implement one-shot and locked states with explicit visual feedback (§10).

This is the same integration pattern Termux's own ExtraKeys use, so the path is
proven in production.

---

## 6. Package management / CLI app distribution research (M2 preview only)

| Approach | Maturity | Notes |
|---|---|---|
| Termux `pkg`/`apt` (dpkg world) | Very mature | Requires Termux userspace bootstrap (~250 MB) + targetSdk 28 world |
| Alpine `apk.static` under proot | Mature, small | Fast bootstrap, musl world; pairs with Option C above |
| Custom APK-based package store | N/A | Rejected: repackaging per install, no incremental updates |

No package-management code in M1. The M1 "Explore CLI Apps" screen is an honest
placeholder that states the installer arrives with M2 (§18: never fake installs).

---

## 7. Environment audit (build machine, verified 2026-08-30)

| Component | Version | Source |
|---|---|---|
| JDK | OpenJDK 21.0.12 (Debian) | system |
| Gradle | **8.14.5** | services.gradle.org (pinned) |
| AGP | **8.13.2** | dl.google.com/android/maven2 (pinned) |
| Kotlin | **2.4.20** (+ `kotlin.plugin.compose` same version) | Maven Central (pinned) |
| Android SDK platform | **android-36** (latest stable; 37 still beta `37.2-beta3` at research time) | sdkmanager |
| Build-tools | **36.0.0** | sdkmanager |
| Platform-tools | latest via sdkmanager | sdkmanager |
| NDK | **28.2.13676358** (r28c) | sdkmanager |
| CMake | not required (ndk-build) | — |

Constraint: the development sandbox has **no Android device/emulator** (no KVM,
no KMS). APK builds and JVM unit tests run here; **on-device acceptance
remains a mandatory human step** per §28 — no milestone is claimed "validated"
by compilation alone. `docs/TESTING.md` carries the manual checklists.

# PocketShell — Multi-Agent Work Log

Shared, append-only log. Each agent: read first, append after finishing a task.

---
Task ID: 0
Agent: main (Super Z)
Task: M0 — inspect repo/environment, research ecosystem, produce architecture docs.

Work Log:
- Inspected repository: greenfield (only .gitignore [skills/, node_modules/], .env [DATABASE_URL], empty download/ upload/ skills/). Nothing Android-related existed. No deletions performed.
- Verified network (GitHub + Maven reachable), Java 21 present; installed Gradle 8.14.5 to /home/z/tools.
- Installed Android SDK (cmdline-tools → /home/z/android-sdk): platform-tools, platforms;android-36, build-tools;36.0.0, ndk;28.2.13676358 (r28c, 16KB default alignment). Licenses accepted.
- Verified latest stable versions live: Gradle 9.7.1 / AGP 8.13.2 / Kotlin 2.4.20 / Compose BOM 2025.12.01 / Activity 1.10.1 / Lifecycle 2.9.4; chose conservative pairing AGP 8.13.2 + Gradle 8.14.5 + compileSdk 36 (API 37 still beta).
- Cloned termux-app to /home/z/tools/upstream/termux-app (shallow, depth 50). HEAD = 3b66f8799635a4dba4a206563048ff0e6792c487 (2026-08-24). AUDIT: terminal-emulator + terminal-view are self-contained (zero termux.shared imports), deps = androidx.annotation only, JNI via ndk-build (termux.c: createSubprocess/setPtyWindowSize/setPtyUTF8Mode/waitFor/close), 19 upstream JVM test classes, LICENSE GPLv3-only (jackpal Apache-2.0 lineage noted upstream).
- Decisions D1–D4 recorded (vendor Termux modules at pinned commit; reuse PTY JNI; M1 shell = /system/bin/sh + toybox; keyboard → synthetic KeyEvents + KeyboardState → upstream readCtrl/Alt/Shift/FnKey hooks).
- Wrote README.md, docs/{RESEARCH,ARCHITECTURE,THIRD_PARTY,ROADMAP,TESTING,CHANGELOG}.md. Appended Android ignores to .gitignore (existing lines preserved). .env untouched.

Stage Summary:
- Toolchain pinned: Gradle 8.14.5, AGP 8.13.2, Kotlin 2.4.20, platform 36, build-tools 36.0.0, NDK 28.2.13676358.
- Upstream pin: termux-app 3b66f8799635a4dba4a206563048ff0e6792c487, GPLv3 → PocketShell GPLv3.
- Key integration hooks: TerminalSessionClient, TerminalViewClient(readControlKey/readAltKey/readShiftKey/readFnKey/onScale), KeyHandler.getCode.
- Constraint documented for M2: targetSdk>=29 blocks exec from app-writable storage (W^X); Termux upstream targets SDK 28 for this reason; M1 unaffected (/system/bin/sh is a system binary).
- Sandbox has no device/emulator: manual on-device acceptance stays a mandatory human step (TESTING.md).

---
Task ID: 1 (M1)
Agent: main (Super Z)
Task: M1 — Terminal Foundation: Gradle skeleton, vendored modules, sessions, UI, keyboard v1.

Work Log:
- Vendored terminal-emulator + terminal-view (48 files, byte-identical to upstream pin, verified via diff -r).
- Wrote Gradle skeleton: settings.gradle.kts, root build.gradle.kts, gradle.properties, version catalog, module build scripts (Kotlin DSL ports of upstream Groovy), app module (Compose).
- Generated Gradle wrapper 8.14.5 into repo.
- Wrote app sources: ShellEnvironment, PocketShellSessionClient, TerminalSessionManager, PocketShellTerminalViewClient, KeyboardState, KeyLayouts (KeyAction/KeyboardKey model), TerminalKeyDispatcher, TerminalKeyboard (Compose), Theme, HomeScreen, TerminalScreen (+TerminalViewHost), ExploreAppsScreen, TerminalViewModel, MainActivity, PocketShellApp.
- Wrote tests: KeyboardStateTest, KeyLayoutsTest (§8 coverage + FN remaps), CliAppTest (model/resolver).
- Iterated build failures: JRE-only environment → installed Temurin JDK 21.0.12.1+1 at /home/z/tools; androidx.annotation coordinate typo; Compose BOM 2026.08.00 / core-ktx 1.19.0 / lifecycle 2.11.0 / activity 1.13.0 require compileSdk 37 + AGP 9.1 → pinned era-correct 2025.12.01 / 1.17.0 / 2.9.4 / 1.11.0; KeyAction.Char ↔ kotlin.Char clash → renamed KeyAction.Text; KeyCharacterMap.getEvents is instance method → load(VIRTUAL_KEYBOARD); upstream TerminalView has only (Context, AttributeSet) ctor.
- VERIFIED: :app:assembleDebug BUILD SUCCESSFUL → app-debug.apk 20MB, libtermux.so present for all 4 ABIs, arm64 .so 16KB-aligned (align 2**14). All unit tests pass (upstream 19-class emulator suite + app suites).

Stage Summary:
- Real APK produced from real PTY terminal stack; toolchain pairing locked: AGP 8.13.2 + Gradle 8.14.5 + Kotlin 2.4.10 + BOM 2025.12.01 + core 1.17.0 + lifecycle 2.9.4 + activity 1.11.0.
- Manual on-device acceptance remains pending (no device in sandbox) — TESTING.md §M1 checklist ready for a human.
- JAVA_HOME for builds: /home/z/tools/jdk-21.0.12.1+1; ANDROID_HOME: /home/z/android-sdk.

---
Task ID: 2 (M1.1 + M1.2 + M1.3)
Agent: main (Super Z)
Task: M1.1 keyboard hardening, M1.2 input reliability, M1.3 UI/UX polish.

Work Log:
- M1.1: suppressed system IME inside terminal (window SOFT_INPUT_STATE_ALWAYS_HIDDEN; setShowSoftInputOnFocus absent from android-36 stubs — verified via javap, used window-level approach). Font-size state plumbing (settings default → TerminalScreen rememberSaveable → pinch).
- M1.2: TerminalService (foreground, specialUse, PROPERTY_SPECIAL_USE_FGS_SUBTYPE, quantity-string notification) started/stopped from TerminalSessionManager via syncWithSessionState; runs only while sessions exist. Pinch font resize: scale accumulation thresholds (×1.25 / ×0.8) → setTextSize ±2 → upstream onSizeChanged → TIOCSWINSZ. Clipboard via upstream ActionMode + PocketShellSessionClient. Manifest now declares FOREGROUND_SERVICE, FOREGROUND_SERVICE_SPECIAL_USE, POST_NOTIFICATIONS only.
- M1.3: SettingsRepository (DataStore: themeMode/dynamicColor/defaultFontSize), SettingsViewModel, Theme v2 (System/Light/Dark/AMOLED pure-black + dynamicColor on API 31+ with honest fallback), SettingsScreen (radio rows + switch + slider), DiagnosticsScreen (read-only facts from Diagnostics.snapshot: version/API/device/ABIs/shell presence/HOME/TMPDIR/real session counts/permission list), Home header icons (Settings/Diagnostics).
- Fixed build errors: DiagnosticsScreen/SettingsScreen missing modifier params; TerminalScreen onScale naming.
- VERIFIED: assembleDebug + all unit tests BUILD SUCCESSFUL.

Stage Summary:
- M1.x feature set complete and building; docs (CHANGELOG/ROADMAP) updated with honest pending-manual-acceptance state.
- Milestone commits created: M1.1-keyboard, M1.2-input, M1.3-polish (grouped staging; CHANGELOG records the authoritative per-milestone contents).
- Gate to M2: manual on-device acceptance of M1/M1.1/M1.2/M1.3 checklists (TESTING.md) — requires real hardware.

---
Task ID: 3 (final verification)
Agent: main (Super Z)
Task: Final verification build + docs refresh + closeout.

Work Log:
- README status refreshed (M0–M1.3 implemented; manual acceptance gate documented).
- Clean rebuild from committed state: BUILD SUCCESSFUL; app-debug.apk 20.0 MB with libtermux.so for 4 ABIs; all unit tests pass.
- Repository state: 106 tracked files, working tree clean, checkpoint chain: 92e1e14 initial → 2bf214c M0 → 89ee4e0 M1 → fc600ed M1.1 → b9ecf8c M1.2 → a47c7f3 M1.3 → docs commit.

Stage Summary:
- PocketShell M0–M1.3 complete and reproducible; M2 gate = manual on-device acceptance (TESTING.md).

---
Task ID: 4 (APK delivery)
Agent: main (Super Z)
Task: Rebuild environment after sandbox reset and deliver installable APK to download/.

Work Log:
- Sandbox restart detected: /home/z/tools JDK and /home/z/android-sdk gone, build outputs cleaned, all files mode-flipped 644->755 (neutralized via git config core.fileMode false — no content diffs).
- Reinstalled toolchain: Temurin JDK 21.0.12.1+1 at /home/z/tools/jdk-21.0.12.1+1 (identical to M0 pin); Android SDK at /home/z/android-sdk (cmdline-tools 11076708, platform-tools, platforms;android-36, build-tools 36.0.0 + 35.0.0 auto-installed by AGP, NDK 28.2.13676358). Wrote gitignored local.properties (sdk.dir).
- Build failures handled: system java is JRE-only (no javac) -> Temurin JDK; two OOM-killed runs (3.9GB RAM box, dmesg confirmed global OOM) caused by gradle.properties -Xmx3072m + orphaned Kotlin daemons holding ~2.2GB -> killed daemons, rebuilt with command-line-only overrides: --no-daemon, -Dorg.gradle.jvmargs="-Xmx2048m -XX:MaxMetaspaceSize=512m", -Dorg.gradle.parallel=false, -Dorg.gradle.workers.max=1, kotlin.compiler.execution.strategy=in-process (-P and -D). No repo files changed for this.
- BUILD SUCCESSFUL: testDebugUnitTest + assembleDebug (119 tasks). Tests: terminal-emulator 145/145, app 21/21, 0 failures/errors/skipped (166 total).
- APK verified: 20.0 MB, apksigner certs OK (debug keystore), libtermux.so present for arm64-v8a/armeabi-v7a/x86/x86_64, package app.pocketshell versionName 0.1.0-m1, minSdk 26, targetSdk 36.
- Delivered: download/PocketShell-v0.1.0-m1-debug.apk (SHA-256 dc9544a9...) + download/README.md artifact sheet.

Stage Summary:
- Fresh reproducible build environment: JAVA_HOME=/home/z/tools/jdk-21.0.12.1+1, ANDROID_HOME=/home/z/android-sdk; low-RAM build override flags recorded above for future runs.
- Installable APK of the complete M0-M1.3 feature set delivered to download/; manual on-device acceptance (TESTING.md) remains the M2 gate.

---
Task ID: 5 (M1.2-input-fix)
Agent: main (Super Z)
Task: Diagnose on-device bug from user screen recording: terminal took no visible input while built-in keyboard was visible; output only appeared after toggling keyboard off.

Work Log:
- Analyzed upload/Screen_Recording_20260831_153612_PocketShell.mp4 via frame extraction (ffmpeg 12 frames @2s) + vision model (scripts/analyze_frames.mjs): confirmed keys DID reach PTY (echo appeared in bulk later); TerminalView never repainted while keyboard visible; layout toggles forced the reveal. Stale-view/missing-invalidation diagnosis.
- Root cause (code): PocketShellSessionClient.onTextChanged was an empty stub with false comment "TerminalView invalidates itself". Upstream contract (termux-app TermuxTerminalSessionClient): the HOST must call TerminalView#onScreenUpdated(); the view never observes session data. TerminalSession dispatches onTextChanged/onColorsChanged on the main thread via its MainThreadHandler (verified in vendored source).
- Also found: cursor blinker never started (setTerminalCursorBlinkerState = upstream host duty, never called) and view never focused (BT keyboards would not work).
- Fix (4 files): (1) PocketShellSessionClient: new onScreenUpdate callback invoked from onTextChanged + onColorsChanged, doc comments corrected; (2) TerminalSessionManager: new @Volatile onScreenUpdateListener hook wired per-session (main thread); (3) PocketShellTerminalViewClient: onEmulatorReady callback from onEmulatorSet; (4) TerminalScreen: DisposableEffect installs listener -> terminalViewRef.value?.onScreenUpdated(); blinker start in onEmulatorSet + lifecycle ON_RESUME/ON_PAUSE; view.post { requestFocus() } in AndroidView factory.
- Version bump 0.1.1-m1 / versionCode 2; docs/TESTING.md §4 got two bold regression items; docs/CHANGELOG.md new [0.1.1-m1] section.
- Sandbox reset AGAIN mid-task (2nd time): /home/z/tools + /home/z/android-sdk wiped -> reinstalled Temurin JDK 21.0.12.1+1, cmdline-tools, platform-36, build-tools 36.0.0, NDK 28.2.13676358, local.properties (same as Task 4).
- BUILD SUCCESSFUL (7m23s, cold caches): testDebugUnitTest 166/166 pass; APK verified (v0.1.1-m1, code 2, signed, libtermux.so x4 ABIs).
- Delivered: download/PocketShell-v0.1.1-m1-debug.apk (SHA-256 336fc6b0...), old v0.1.0 APK removed. Commit 8c2165c "M1.2-input-fix".

Stage Summary:
- On-device regression is fixed in code; final confirmation needs the user to install v0.1.1 and re-test per TESTING.md §4 first two items (echo while keyboard visible, live output + blinking cursor).
- Env resets are recurring: recovery recipe lives in Task 4 + Task 5 worklog entries (JDK at /home/z/tools/jdk-21.0.12.1+1, SDK at /home/z/android-sdk, low-RAM gradle flags, local.properties).

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

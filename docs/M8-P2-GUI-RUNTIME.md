# P2 — PocketShell Linux GUI Runtime (embedded Wayland) — research + prototype

**Agent L(P), PARALLEL WORK 2.** Base commit `58f39bd` (main). Branch
`agent-L/p2-linux-gui-runtime`. P1 (ZCode protocol audit, paused by owner
direction) is preserved in `M8-P1-ZCODE-RUNTIME-AUDIT.md`.

Evidence classes: VERIFIED (live experiment on this laptop / on the S9+),
OBSERVED (read from source/artifacts), INFERRED (reasoned), UNKNOWN.

---

## 1. What PocketShell is today (audit result, read-only sweep)

- Single-Activity Compose app; minSdk 26, **targetSdk 28 (deliberate —
  `untrusted_app_27` is the only domain that can exec app_data_file)**,
  compileSdk 36, arm64-v8a focus.
- Guest = Alpine 3.24.1 aarch64 minirootfs + Debian glibc layer under
  **proot**, exec'd as `libproot.so` from `nativeLibraryDir`
  (`runtime/RuntimeProcessLauncher.kt`); PTY sessions via JNI fork/execvp in
  `terminal-emulator/src/main/jni/termux.c`; the ONLY existing native build is
  `libtermux.so` (ndkBuild).
- **Zero existing graphics plumbing**: no SurfaceView/TextureView/EGL/GLES/
  ANativeWindow/AHardwareBuffer anywhere; the only View-interop nodes are the
  terminal `AndroidView` and the Companion WebView panel.
- Input: one keyboard deck → `TerminalKeyDispatcher` →
  `KeyboardInputRouter` → focused View's `dispatchKeyEvent`; IME hard-blocked;
  hardware keys reach the focused view directly.
- Clipboard: `ClipboardManager` via `PocketShellSessionClient`.
- Background survival: `TerminalService` foreground service (specialUse).

## 2. Compositor candidates (research, sources read; detail in §2 of report)

| Candidate | License | Fit for "render into an Android surface" |
|---|---|---|
| **wlroots** | MIT | Public backend/output impl headers; an ANativeWindow backend is *implementable* (cross-build proven by wlroots-android-build) but **nobody has shipped one**. Ecosystem default (cage/labwc/tinywl/gamescope sit on it). |
| **Smithay** | MIT | Cleanest custom-backend story; **proven on Android by LocalDesktop** (NativeActivity + Smithay + proot guest + nested labwc). Rust toolchain required. |
| cage/tinywl/labwc | MIT/CC0/GPLv2 | Thin shells over wlroots; no independent value. |
| gamescope | BSD-2 | Own backend classes; architecture inspiration only. |
| Weston | MIT/Expat | Heaviest (~114k SLOC + deps); backends are remoting (RDP/VNC/PipeWire) = violates "no streaming". |
| Termux:X11 | GPLv3 | X11, not Wayland — but THE prior art: SurfaceView + EGL window surface + ANativeWindow, memfd shm over socket, AHardwareBuffer→EGLImage GPU path, `scanCode+8` key mapping. **Technique reuse only (GPLv3).** |
| LocalDesktop | GPLv3 | Proof the exact product shape works on Android (Smithay+proot). Architecture reference only (GPLv3). |

## 3. Chosen architecture (Option C, hybrid — and why)

    Linux GUI app (proot guest, app UID)
        │  Wayland: unix socket under app_data_file (proot passes it through)
        ▼
    Compositor host = native executable/JNI lib on the ANDROID side
        │  sink (the only platform seam)
        ▼
    EGL/GLES2 → ANativeWindow (SurfaceView) — SurfaceFlinger composites

Rationale (measured + researched):
- A proot/guest process CANNOT present on screen: appdomain has no
  SurfaceFlinger service access (AOSP `private/app.te` — VERIFIED by the
  research pass); the app is the only presenter.
- The compositor must therefore live on the Android side of the SELinux
  boundary, in a process with the app's UID; guest apps connect over a plain
  unix socket (works under proot — same kernel, same UID; Termux-standard).
- v1 pipeline (VERIFIED end-to-end on x86; ARM64 build + on-device exec
  verified): client wl_shm (memfd) buffers → compositor copies damage once →
  GLES2 `texSubImage2D` + full-screen quad (shader swizzles BGRA) →
  eglSwapBuffers. One inherent copy + one GPU upload; zero encoding, zero
  network, no desktop, no VNC.
- Upgraded pipeline (researched, components VERIFIED, integration INFERRED):
  app-allocated AHardwareBuffer pool (RGBA8888, CPU_WRITE_OFTEN |
  GPU_SAMPLED_IMAGE) the compositor memcpys damage into, presented via
  `ASurfaceTransaction_setBuffer` (API 29+) — one copy, zero GPU uploads.
- GPU clients (later, Adreno-only): guest turnip+zink/KGSL exports a dma-buf
  fd → app imports via `EGL_EXT_image_dma_buf_import`. Mali (this S9+,
  Exynos 9810) has no usable render node → CPU path is the default; per-device
  GPU probe at runtime.

Why a from-scratch kiosk compositor for the spike: the product need for v1 is
exactly one fullscreen app surface (kiosk) — wlroots/Smithay bring window
management we do not need yet, and a hand-rolled core (libwayland-server +
xkbcommon, ~1.5k lines C) is fully owned (MIT-clean room, no GPL), tiny, and
teaches the exact integration points. If/when multi-window + Xwayland +
popups + full clipboard arrive, **Smithay is the recommended base** (proven
on Android); the sink boundary survives that swap.

## 4. What was actually built and verified (the `gui-runtime/` spike)

`mc` — minimal kiosk Wayland compositor (libwayland-server, xkbcommon):
wl_compositor(4), wl_shm (libwayland built-in, SIGBUS-safe), wl_output(3),
wl_seat(5: keyboard+pointer+touch), xdg_wm_base(6) with configure/ack dance,
frame callbacks, single-active-toplevel, PPM sink for headless measurement,
X11 sink for laptop rehearsal, EGL/GLES2 sink for Android (compiles; runtime
gate = device+app), seat feeders for touch/keys; `simple-client` test client;
`run-test.sh`, `run-electron-test.sh`, `android/build-ndk.sh`.

### Laptop (x86 BunsenLabs) — all VERIFIED live
- Own client end-to-end: connect → globals → xdg configure/ack → shm attach →
  commit → map → present. Pixels pixel-checked (R-gradient on x, animated G,
  B-checkerboard {60,200} intact).
- **Electron 38.4.0 (`--ozone-platform=wayland --no-sandbox --disable-gpu`)
  rendered its UI through mc**: captured frames contain the page background
  `#1a2b3c` at 78.6% of sampled pixels and the light-blue `#7fd4ff` text
  pixels — the REAL-client gate is passed (this is the ZCode GUI toolkit).
- Performance (PPM sink, 800x500, scalar single-thread): full-frame
  copy+convert 1.67 ms avg (≈957 MB/s) per present; presents fire on a 60 Hz
  tick only while dirty (static UI costs nothing). Event-loop design verified:
  no polling of clients, no busy rendering.

### S9+ (SM-G965F, Android 10, Exynos 9810/Mali-G72) — device findings
- ARM64 cross-build via NDK r28 (libffi 3.4.6 shared, libxkbcommon 1.7.0,
  libwayland 1.23.1 — cross-built; script in `android/build-ndk.sh`): both
  `mc` (158 KB) and `simple-client` (43 KB) are bionic PIE executables.
- **On-device execution VERIFIED**: mc runs under Android 10, PPM sink
  initializes, **xkbcommon compiles the keymap from the pushed
  xkeyboard-config data on-device** (`XKB_CONFIG_ROOT`), protocol stack
  initializes, clean error paths.
- **On-device socket bind BLOCKED by SELinux for the adb shell domain**
  (exact denial captured: `avc: denied { create } for comm="mc"
  scontext=u:r:shell:s0 tcontext=u:object_r:shell_data_file:s0
  tclass=sock_file`). This is a *testing-context* limitation: in production
  the compositor runs inside the app (untrusted_app_27), where unix-socket
  files under app data are the Termux-proven configuration. Full round-trip
  (client↔compositor↔Surface) on device therefore requires the app/JNI
  integration milestone.

## 5. Security / sandbox (honest)

- Chromium/Electron needs `--no-sandbox` where user namespaces are absent —
  Android kernels have them disabled (VERIFIED, multiple sources incl.
  LocalDesktop's bwrap shim). Documented consequence: guest browsers/Electron
  render content with Chromium's sandbox disabled; this is the entire
  industry-standard Termux situation, is acceptable for v1, and should be
  revisited (e.g. seccomp-bpf preload, or broker design) later.
- The compositor itself adds no new privileged surface: it is app-UID code
  talking to app-UID guests over app-private sockets.
- mc v1 trusts its clients as much as any compositor does; shm access is
  SIGBUS-guarded via libwayland's accessors; unknown formats rejected.
- No VNC/streaming/encoding anywhere in the pipeline.

## 6. Input & clipboard design (next milestone, seams already in the code)

- Keyboard: Android `KeyEvent.getScanCode() + 8` == Linux evdev == Wayland
  keycode (termux-x11-verified rule); mc's `mc_seat_key()` takes evdev codes
  directly; xkbcommon keymap compiled on-device (VERIFIED today). The existing
  `KeyboardInputRouter` is the dispatch seam — no second keyboard system.
- Touch: mc has wl_touch down/motion/up feeders; map from the SurfaceView's
  touch events; pointer emulation for mouse-only apps is a sink-side concern.
- Clipboard: Wayland `wl_data_device` + `ext-data-control` on the compositor
  side ↔ `ClipboardManager` on the Kotlin side (JNI bridge); v1 scope is
  text-only; termux-x11's clipboard.c is the technique reference.

## 7. Limitations / honest unknowns

- mc v1 is kiosk: one toplevel fullscreen; popups accepted-but-not-rendered;
  no wl_data_device yet; no dma-buf; no resize-driven reflow beyond
  reconfigure. Electron menus/tooltips (xdg_popup) will not display in v1.
- EGL sink compiled but not yet driven by a real Android Surface — needs the
  JNI/SurfaceView host (app integration) + device.
- Laptop performance numbers are x86/UMA proxies; no device GPU numbers
  exist yet (Mali-G72 GLES2 upload path untested).
- Electron on-device (proot guest → mc → Surface) untested.
- The v4/`clientKind: mobileApp` ZCode protocol findings (P1 doc) are
  orthogonal: they cover agent-driven UI, not graphics hosting.

## 8. Recommended next phase (P3 — app integration)

1. Ship `libmc.so` (or the executable) in `jniLibs` like `libproot.so`;
   Kotlin `GuiRuntimeService` owns lifecycle (reuse TerminalService pattern).
2. `GuiScreen.kt`: Compose `AndroidView { SurfaceView }` → JNI
   `mc_attachSurface(surface)` (ANativeWindow + EGL init on the render
   thread), teardown on surfaceDestroyed; resize via reconfigure.
3. Guest launcher line: `WAYLAND_DISPLAY=wayland-0 XDG_RUNTIME_DIR=<app dir>`
   prepended for GUI sessions; first target app = Electron (simple), then
   official ZCode Electron, then GTK/Qt/SDL for the "runtime, not
   Electron-runner" proof.
4. Then measure on-device: frame latency (ASurfaceTransaction onComplete
   stats), upload cost, thermal, and decide AHB-pool upgrade.

**Device gate status:** laptop-side + on-device smoke complete; the full
graphics/input/clipboard gate requires the app-integrated build.
READY FOR ANDROID DEVICE WORK — APP-INTEGRATED BUILD (next milestone).

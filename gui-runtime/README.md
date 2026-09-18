# PocketShell Linux GUI Runtime — minimal Wayland kiosk compositor ("mc")

PARALLEL WORK 2, Agent L(P). Evidence-first prototype for the architecture:

    Linux GUI app (guest, proot)
        │  Wayland (unix socket, wl_shm client buffers)
        ▼
    mc compositor (native Android-side executable, shipped like libproot.so)
        │  pluggable sink
        ▼
    Android: EGL/GLES → ANativeWindow (SurfaceView)     Laptop: X11 / PPM frames

The compositor never talks DRM/KMS. It renders whatever the client commits
(wl_shm ARGB/XRGB 8888 today, dma-buf later) into the host-provided surface.
On Android the sink draws into an `ANativeWindow` obtained from a SurfaceView;
on the laptop the same core runs against X11 (visual rehearsal) or writes PPM
frames (headless measurement).

## Layout

    Makefile            laptop build (needs libwayland-server, xkbcommon, scanner)
    src/gen-protocol.sh wayland-scanner invocation (build-time codegen, not committed)
    src/sink.h          sink vtable — the ONLY platform seam
    src/main.c          display/event loop, kiosk logic, frame pacing
    src/shm.c           wl_shm pool/buffer + client-buffer bookkeeping
    src/shell.c         xdg_wm_base: toplevel lifecycle, configure/ack, popup stub
    src/seat.c          wl_seat: keyboard (xkbcommon keymap in shm), pointer, touch
    src/backends/sink_x11.c   laptop rehearsal sink (XPutImage)
    src/backends/sink_ppm.c   headless sink: periodic PPM dump + present timing log
    src/backends/sink_egl.c   Android sink: texSubImage2D + quad + eglSwapBuffers
                              onto ANativeWindow (compiles under the NDK; runtime
                              gate needs the device)
    client/simple-client.c    dependency-free test client (shm painter)
    android/build-ndk.sh      NDK cross-build of compositor + client (ARM64)

## Status (honest)

- [x] Laptop: core protocol (compositor/shm/seat/xdg-shell) vs own client
      (pixel-checked; 225 presents captured, avg 1.67 ms copy+convert @800x500)
- [x] Laptop: REAL-CLIENT GATE — Electron 38.4.0 Ozone-Wayland rendered its UI
      through mc (frame pixel-checked: #1a2b3c bg 78.6%, #7fd4ff text present)
- [x] NDK r28: ARM64 cross-build of mc + EGL sink + client (bionic PIE)
- [x] S9+ (Android 10) on-device execution: mc runs, xkb keymap compiles
      on-device from XKB_CONFIG_ROOT, clean error paths; unix-socket bind
      blocked for the SHELL uid by SELinux (avc captured) — production runs
      in-process under the app UID, so the remaining gate is the
      app-integrated build: EGL sink on a real SurfaceView, input injection,
      clipboard, Electron-in-guest (see docs/M8-P2-GUI-RUNTIME.md §7-8)

Build (laptop):

    ~/p2-lab/prefix is the user-local libwayland prefix (built by P2 setup)
    make SCANNER=~/p2-lab/prefix/bin/wayland-scanner \
         WL_XML=~/p2-lab/prefix/share/wayland/wayland.xml \
         XDG_XML=~/p2-lab/src/wayland-protocols-1.36/stable/xdg-shell/xdg-shell.xml

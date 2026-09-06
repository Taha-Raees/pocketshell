# KNOWN_LIMITATIONS — the glibc runtime layer (M6.0)

Only verified, current limitations. Each with cause, evidence, fixability.

## 1. gconv modules excluded (iconv charset coverage)

- **Cause**: ~9 MB of charset conversion modules inside Debian's libc6; pruned
  by `build_glibc_sidecar.sh` to keep the APK payload lean.
- **Evidence**: build script prune list; glibc dlopens gconv only for
  non-trivial charsets via `iconv`/locale conversions.
- **Impact**: a glibc binary calling `iconv` for an exotic charset fails with
  a clean loader/dlopen error; C.UTF-8/UTF-8 paths (the guest's LANG) work.
  Node/Bun-based CLIs (Cline, Kilo) do their own encoding — unaffected.
- **Fixability**: HIGH — ship `gconv/` in the next layer revision or drop
  selected modules into `/usr/lib/aarch64-linux-gnu` (extension slot).

## 2. A glibc binary needing a shared library outside the shipped set

- **Cause**: the layer ships the common set (libc family, libstdc++, libgcc_s,
  zlib, ssl/crypto, lzma, bz2, expat, ffi, pcre2, yaml, ncursesw/ncurses,
  tinfo, readline) — not every Debian library.
- **Evidence**: the loader falls through to musl's `/lib`,`/usr/lib`, where a
  same-named musl library cannot load into a glibc process; the failure is a
  clean `cannot open shared object file` (never a crash mid-run).
- **Fixability**: HIGH and already engineered — put the glibc-built library
  into `/usr/lib/aarch64-linux-gnu` (documented extension slot, searched
  before the musl dirs); `pocketshell-doctor` names exactly what is missing.

## 3. NIS/`libnss_nis` not shipped

- **Cause**: no directory service use case on a phone; Debian's default
  `nsswitch.conf` we ship omits it.
- **Fixability**: trivial (add `libnss_nis` from the same Debian pool) if a
  use case ever appears.

## 4. Layer version rides app updates

- **Cause**: the pinned artifact ships in the APK (offline, atomic, no loose
  URLs) — so a new glibc layer means a new PocketShell release.
- **Evidence**: `GlibcRuntimePin` + `docs/runtime/DUAL_LIBC.md` §4.
- **Fixability**: by design (the same policy as the proot and rootfs pins);
  the extension slot covers urgent needs between releases.

## Not limitations (checked and closed)

- musl regression: none — disjoint loader names, SONAMEs and directories
  (DUAL_LIBC.md §3); the suite proves sh/bash/git/curl/node/npm/apk unchanged.
- `ld.so.cache` shadowing: impossible — Alpine ships none, we never build one.
- Children of glibc binaries: work unchanged (same canonical resolution).
- proot interaction: no new mechanism; the layer is ordinary rootfs content,
  validated under proot+ptrace in the rig and by the device gate.

## 5. gcompat package operations can reclaim the loader path (C4 verdict)

- **Classification**: IMPORTANT (bounded, self-healing, permanently tested).
- **Cause**: Alpine's `gcompat` package owns `lib/ld-linux-aarch64.so.1` in
  the apk database and ships a real ELF shim there (verified from the actual
  1.1.0-r4 package: a 67,600-byte static-PIE, `so:ld-linux-aarch64.so.1=1`).
  `apk fix/reinstall/upgrade gcompat` can therefore overwrite the real
  loader symlink behind a perfectly valid layer marker.
- **Evidence**: the downloaded package file list;
  `runtime-tests/adversarial_closure_audit.sh drill-c4` on the device;
  `GuestGlibcRuntimeTest` gcompat-reclaim regression pin.
- **Mitigation** (m6.0.4): the layer fast path runs a structural integrity
  probe (loader symlink must resolve to the canonical Debian loader;
  load-bearing files must exist) — a reclaim is detected on the next session
  prep and self-heals by idempotent re-extraction. musl is never affected.
- **Fixability**: containment is by design (package db ownership cannot be
  rewritten unprivileged); a post-package repair hook is possible if device
  evidence ever shows the next-session heal is too coarse.

## 6. Heavy session prep ran on the UI thread (C1.1/C3) — fixed in m6.0.4

- **Classification**: was IMPORTANT (UX/ANR risk, never correctness), FIXED.
- **Cause**: session creation performed the guest preparation (including the
  ~18 MB layer re-extraction path) synchronously in the click handler.
- **Fix**: two-phase session creation — `prepareLinuxSession` on
  Dispatchers.IO, PTY spawn on the main thread; `GuestGlibcRuntime.ensure`
  is single-flight (monitor) so concurrent sessions serialize instead of
  racing. UI-thread freeze eliminated for updates/repairs.

## 7. In-place re-extraction wiped the multiarch directory mid-run (C3/C12) — fixed in m6.0.4

- **Classification**: was HIGH (corruption window for concurrent observers), FIXED.
- **Cause**: the layer ships `lib/aarch64-linux-gnu -> ../usr/lib/aarch64-
  linux-gnu` and the old symlink replacement used `File.deleteRecursively`,
  whose walk FOLLOWS directory symlinks — every re-extraction first erased
  the entire multiarch directory, then rewrote it (converged, but exposed a
  no-libs window to concurrent apk operations or running sessions).
- **Fix**: symlink replacement deletes the link node; all recursive deletes
  are NOFOLLOW; entries routed through earlier symlink entries are refused.
  Regression pins: sentinel survival test, real-archive re-extract test.

## Not limitations (checked and closed)

- musl regression: none — disjoint loader names, SONAMEs and directories
  (DUAL_LIBC.md §3); the suite proves sh/bash/git/curl/node/npm/apk unchanged.
- `ld.so.cache` shadowing: impossible — Alpine ships none, we never build one.
- Children of glibc binaries: work unchanged (same canonical resolution).
- proot interaction: no new mechanism; the layer is ordinary rootfs content,
  validated under proot+ptrace in the rig and by the device gate.
- gcompat merely installed: harmless to musl and to the layer (see §5) — it
  only becomes a factor when package operations rewrite its files.
- Environment variables in guest sessions (`LD_LIBRARY_PATH` etc.): visible
  but inert inside the guest (DUAL_LIBC.md §8.4); no global loader hack.

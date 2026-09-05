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

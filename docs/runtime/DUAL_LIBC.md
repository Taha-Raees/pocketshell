# DUAL_LIBC — musl + glibc coexistence in the Alpine guest

Status: SELECTED ARCHITECTURE (M6.0). Concise by rule — implementation facts only;
device evidence lives in docs/TESTING.md, forensic baseline in the Runtime Forensic
Audit (ch. 8) + the Kilo/M3 in-guest report (§7–§9).

## 1. Problem

The guest is Alpine 3.24.1 (musl 1.2.6). glibc-linked ARM64 ELF binaries fail at the
loader stage. Verified on device (Kilo/M3 report §8–§9): Cline 3.0.61
(`@cline/cli-linux-arm64/bin/cline`, 151 MB Bun-style single-file) needs
`libc.so.6, libpthread.so.0, libdl.so.2, libm.so.6` + interp `/lib/ld-linux-aarch64.so.1`.
The installed gcompat provides only a `libc.so.6 → libgcompat.so.0` shim and its own
thin loader; the real glibc loader aborts before any user code runs
("Not a valid dynamic program"). This blocks the entire Tier-2/3 ecosystem
(prebuilt glibc CLIs, AI coding agents, native npm/bundled runtimes).

## 2. Selected architecture — real glibc at canonical paths, inside the rootfs

```
Alpine rootfs (musl, untouched paths)          PocketShell glibc layer (real glibc)
/lib/ld-musl-aarch64.so.1                      /lib/ld-linux-aarch64.so.1        (real loader)
/lib, /usr/lib           (musl libs)           /lib/aarch64-linux-gnu/…          (core glibc libs)
/lib/libc.musl-aarch64.so.1                    /usr/lib/aarch64-linux-gnu/…      (libstdc++, libgcc_s)
                                               /etc/nsswitch.conf                (glibc NSS policy)
```

- musl binaries keep interp `/lib/ld-musl-aarch64.so.1` → musl loader → musl libs.
  Zero overlap: Alpine ships no `/lib/ld-linux-aarch64.so.1`, no `libc.so.6` (except
  the gcompat shim we replace), no multiarch dirs.
- glibc binaries (interp `/lib/ld-linux-aarch64.so.1` or `/lib64/ld-linux-aarch64.so.1`)
  exec the REAL Debian loader, which finds its libraries in its compiled-in default
  search path — multiarch dirs first, `/lib`, `/usr/lib` last. No env vars, no proot
  bind changes, no per-binary wrappers; **child processes of glibc binaries work
  unchanged** because every exec resolves through the same canonical paths.
- Both launch profiles (INTERACTIVE_TERMINAL / PACKAGE_OPERATION) benefit with zero
  launcher changes: the layer is ordinary rootfs content.

Source: Debian 13 (trixie) arm64 packages — `libc6` (loader, libc, libpthread/libdl/
librt/libutil stubs — real glibc post-2.34 merged-libc semantics, libm, libresolv,
NSS modules, gconv not included), `libgcc-s1`, `libstdc++6`, `zlib1g`, plus a small
common set (libssl3t64, liblzma5, libbz2-1.0, libexpat1, libffi8, libpcre2-8-0,
libyaml-0-2, libtinfo6, libncurses6, libncursesw6, libreadline8). Versions pinned
per build in `scripts/runtime/build_glibc_sidecar.sh` + `RuntimeChecksum`-style pins
in `GlibcRuntimePin`. glibc 2.41 runs binaries built against any older glibc
(backward symbol-version compatibility), covering current prebuilt releases.

## 3. Isolation analysis (why this cannot break musl)

| Risk | Verdict |
|---|---|
| Path collision with musl | None: loader name (`ld-linux` vs `ld-musl`), SONAMEs (`libc.so.6` vs `libc.musl-aarch64.so.1`), dirs (`*aarch64-linux-gnu*`) are disjoint. |
| `apk` breakage | None: apk is musl-linked; no musl file is replaced. The layer only ADDS files. |
| Loader falls through to musl libs (mixing) | Only for a glibc binary needing a lib absent from the multiarch dirs — the common set above covers the frequent names first (multiarch is searched before `/lib`, `/usr/lib`). A genuinely missing lib fails with a clean `cannot open shared object file` — diagnosable by `pocketshell-doctor`, extendable via `/usr/lib/aarch64-linux-gnu` (writable slot). |
| `ld.so.cache` shadowing | Alpine ships no `/etc/ld.so.cache` (musl never uses one) — verified in the 3.24.1 minirootfs. The loader falls straight through to default dirs. |
| gcompat remnants | The layer's real files replace gcompat's loader symlink and `libc.so.6 → libgcompat` shim at the same paths. gcompat's package files may remain installed but become unreferenced; `apk del gcompat libc6-compat` is the optional cleanup. |
| SELinux / proot | No new mechanism: ordinary files under the app-owned rootfs; proot translates them like every other guest path. |

## 4. Delivery (PocketShell-owned, pinned, no manual user steps)

- Artifact: `pocketshell-glibc-aarch64-<ver>.tar.gz` built by
  `scripts/runtime/build_glibc_sidecar.sh` (pinned upstream URLs + SHA-256 of every
  input; output pinned in `GlibcRuntimePin`), shipped **inside the APK** as
  `assets/guest/<artifact>` — atomic with the app update, works offline, no new
  trust root, no user action.
- ONE convergence seam for every install state (fresh rootfs, rootfs installed by
  an older PocketShell build): `GuestGlibcRuntime.ensureInstalled(rootfsDir)` —
  idempotent, marker-file gated (`etc/pocketshell/glibc-runtime`, written last),
  self-healing (a partial layer is re-extracted), called from the existing
  per-session prep seam (`PackageGateway.prepareGuestForSession`, the
  GuestApkCompat self-repair pattern). The marker fast path costs one small read
  on warm starts. musl behavior is independent of its success; failures surface
  in Diagnostics. Never downloaded from a loose URL at runtime; the rootfs pin
  stays the pristine upstream Alpine minirootfs.

## 5. Rejected options (and why)

- **gcompat (status quo)** — evidence-rejected: shim-only, no libpthread/libdl/libm,
  loader aborts on real Tier-2 binaries (audit + Kilo report). Kept only as a
  historical fallback path.
- **sgerrand alpine-pkg-glibc** — unmaintained/removed releases; old glibc (≤2.34);
  third-party trust; blocked by modern GLIBC_2.35+ symbol requirements.
- **Second distro (Debian rootfs) / distrobox-style** — forbidden by product
  direction; doubles maintenance, storage, and update surface.
- **Per-binary patchelf interp rewrite** — invasive, per-download, breaks signature/
  checksum expectations of shipped artifacts; unusable for npm-managed binaries.
- **Global `LD_LIBRARY_PATH` to a glibc dir** — pollutes musl processes; a musl
  binary could resolve glibc's `libstdc++`/`libz` (crash class). Canonical paths
  make it unnecessary.
- **proot binds of the sidecar onto `/lib/ld-linux-aarch64.so.1` + multiarch dirs** —
  workable, but redundant: rootfs-internal placement achieves identical resolution
  with zero launcher/spec changes and survives every exec path (app probes, package
  ops, user shells) by construction.
- **Musl-linked upstream builds (e.g. asking Cline for musl release)** — upstream-
  dependent, slow, only fixes one tool; the runtime must fix the class.

## 6. Universal launcher + diagnosis

`pocketshell-exec` (busybox-sh, guest): reads the ELF interpreter via `readelf`,
routes execution — musl/static → direct exec; glibc-interp → direct exec (canonical
paths make this transparent) with loader-explicit fallback
(`/lib/ld-linux-aarch64.so.1 --library-path …`) if the marker file is absent.
`pocketshell-doctor <binary>`: prints arch/class/interp/DT_NEEDED/max GLIBC_x.y
symbol version, which runtime will load it, and a SUPPORTED/UNSUPPORTED verdict with
the reason (Phase F of the mandate; no more guessing).

### 6.1 Install observability (m6.0.1 — device-gate lesson)

Best-effort must still be diagnosable. Every `GuestGlibcRuntime.ensureInstalled`
outcome is mirrored to `/etc/pocketshell/glibc-runtime.status` (guest-visible):
`state=OK source=extractor|fastpath|manual-hatch entries=<n> ts=<ms>` or
`state=FAILED reason=<one-line> ts=<ms>`. The marker stays the sole completeness
contract; the status file is diagnostics only and never blocks or fails a session.
The device suite (`run_on_device.sh`) prints a PREFLIGHT section (marker, status,
real loader identity, layer file count, gcompat presence, disk) and treats a
missing layer as SKIP-with-fix-path, never as a wall of FAILs. The layer's
presence is a capability probe (the real loader answers `--version` with
"stable release version"), not marker paperwork. `POCKETSHELL_INSTALL_LAYER=1`
repairs the layer from a local/URL layer tarball without waiting for the app.

### 6.2 The m6.0.2 install-path fix (device gates #1 and #2, fully explained)

Both gates failed for ONE proven reason: AGP's asset merge decompresses `*.gz`
assets and strips the suffix, so the APK carried
`assets/guest/pocketshell-glibc-aarch64-2.41-12.deb13u3.tar` (plain,
17,909,760 B, sha 5be400dd...) while `GlibcRuntimePin.ASSET_PATH` declared
`...tar.gz` — every spawn failed at `AssetManager.open` before touching the
rootfs. The delivery design in §4 was never wrong; it was never reached.
Fix (three rails): the pin describes the PACKAGED form beside the unchanged
artifact pin; the extractor format-sniffs gzip magic and sha-verifies the
asset bytes BEFORE extraction (a mismatch is FAILED status, never a
half-extraction); a JVM pin opens the BUILT APK and asserts the packaged
entry name+size+sha, and the release mirror repeats that check (scripts/
mirror_m6002.sh). Every spawn additionally stamps the app identity into
`/etc/pocketshell/app-version`, so the device suite PREFLIGHT proves WHICH
build owns the rootfs. Layer bytes unchanged throughout (2242f8ef...).

## 7. Success criteria (delivery standard)

installed AND launches AND executes correctly AND survives a fresh session AND does
not break musl AND passes the automated suite (`runtime-tests/`) — musl regression
(sh, bash, git, curl, node, npm, apk), glibc matrix (simple/pthread/dlopen/libm/C++
exceptions/fork-exec/NSS/getaddrinfo), static, and the real Cline binary.

## 8. Phase-C adversarial closure audit (m6.0.4, vc44)

A full adversarial audit (areas C1–C15 of the Phase-C mandate) closed M6. The
hardened contract below is enforced by JVM tests (`GuestGlibcRuntimeTest`,
26 cases) and by the device drill script
(`runtime-tests/adversarial_closure_audit.sh` — probe / drill-c2 / drill-c4 /
drill-c5 / heal).

### 8.1 Engineering claim (the precise boundary of "universal")

> PocketShell provides a dual-libc runtime environment supporting Alpine musl
> binaries, compatible static binaries, and a broad class of aarch64 glibc ELF
> binaries whose interpreter, GLIBC symbol-version requirements (highest
> required ≤ installed), architecture, and required shared libraries are
> satisfied by the installed runtime layer.

"Universal runtime compatibility" remains the internal milestone name, not a
claim about every Linux binary. `pocketshell-doctor` states the boundary per
binary and its prediction is measured against reality on every audit run
(C7 prediction-accuracy rows).

### 8.2 Loader integrity beyond the marker (C2.2/C2.3/C2.6 — probe)

The marker is the completeness contract, but a text marker cannot see a
clobbered filesystem. Since m6.0.4 the layer fast path additionally runs a
structural integrity probe (`GuestGlibcRuntime.structuralIntegrityPasses`):
the loader symlink must still resolve to the canonical real Debian loader
(`/usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1`), and the load-bearing
files (multiarch core libs incl. SONAME aliases, `pocketshell-doctor`,
`pocketshell-exec`) must exist. Any mismatch → honest re-extraction on the
next session prep (idempotent, musl untouched). Documented marker-loss
behavior (C2.1): a valid layer with a missing/stale marker is re-extracted —
identical bytes, converges, never trusted blindly.

### 8.3 gcompat coexistence verdict (C4 — evidence-based)

Alpine's `gcompat` package (verified from
`dl-cdn.alpinelinux.org/alpine/v3.24/main/aarch64/gcompat-1.1.0-r4.apk`)
ships a real 67,600-byte static-PIE ELF shim AT
`lib/ld-linux-aarch64.so.1` (plus a `lib64/` link) and *provides*
`so:ld-linux-aarch64.so.1=1`. The package therefore owns the exact path our
real loader occupies, and `apk fix/reinstall/upgrade gcompat` CAN reclaim it
(behind a perfectly valid marker — that is why §8.2 exists). Residual
exposure is bounded: app-driven package operations never touch gcompat
unless the user asks for it; a reclaim is detected by the integrity probe on
the NEXT session prep and self-heals by re-extraction; `pocketshell-doctor`
and the device suite expose the interim broken state honestly. gcompat
staying installed is historical and harmless to musl; it must not be relied
on for glibc execution, and after any gcompat package operation a new
session is the repair path. The drill (`drill-c4`) measures the actual apk
behavior on the device.

### 8.4 Environment disclosure (C8)

Guest processes inherit the proot process environment:
`LD_LIBRARY_PATH=<app nativeLibraryDir>` (needed by bionic to resolve proot's
`libtalloc.so` before the rootfs exists), `PROOT_LOADER`,
`PROOT_TMP_DIR`, and optionally `PROOT_LOADER_32`. Inside the guest these
paths do not exist, so they are functionally inert for musl and glibc alike
(neither loader can resolve anything from them; no musl→glibc forcing, no
loader behavior change). They are visible in `env` by design — routing is
the ELF interpreter contract, and the audit's `probe` mode asserts no
`LD_PRELOAD`/`LD_CONFIG` exists and both libcs run under this exact
environment.

### 8.5 Extractor hardening (C12)

Both extractors (rootfs + glibc layer) now: verify asset sha-256 before any
byte is written (fail-closed); refuse absolute entry paths and lexical path
traversal; refuse any entry whose parent chain routes through a symlink
(a hostile archive cannot redirect writes outside the guest root through its
own earlier symlink entries); replace existing symlinks by deleting the link
node (never walking through it — the layer legitimately ships a directory
symlink, and File.deleteRecursively follows directory links); and clear
staging/runtime trees with NOFOLLOW walks. Both pinned archives were
surveyed (361 symlinks, zero escaping the guest root — see
`scripts/audit_c12_symlink_containment.py`).

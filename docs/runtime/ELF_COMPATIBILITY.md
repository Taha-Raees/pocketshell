# ELF_COMPATIBILITY — how an executable is routed in PocketShell

Applies to every exec inside the Alpine guest (all launch paths: Linux Shell,
catalog apps, package ops, user scripts, npm wrappers). No per-tool logic
exists anywhere; the mechanism is the ELF interpreter contract.

## Decision table

| Binary shape | Evidence (readelf -l) | Loader used | Result |
|---|---|---|---|
| Static ARM64 | no PT_INTERP | kernel | direct exec |
| musl ARM64 | interp `/lib/ld-musl-aarch64.so.1`, `libc.musl-aarch64.so.1` | musl loader (Alpine) | direct exec — untouched by the glibc layer |
| glibc ARM64 (Debian layout) | interp `/lib/ld-linux-aarch64.so.1` | REAL glibc loader (`/usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1`) via canonical symlink | direct exec; libs from the layer's multiarch dirs |
| glibc ARM64 (RHEL-style) | interp `/lib64/ld-linux-aarch64.so.1` | same loader via `/lib64` alias | direct exec |
| wrong architecture | `Machine:` not AArch64 | — | kernel/exec failure — `pocketshell-doctor` names it |

## Loader search order (real Debian glibc loader, compiled in)

1. `DT_RPATH` (rare in prebuilts) → 2. `LD_LIBRARY_PATH` → 3. `DT_RUNPATH` →
4. `/etc/ld.so.cache` — **absent in Alpine by design; we never build one** →
5. default dirs: `/lib/aarch64-linux-gnu`, `/usr/lib/aarch64-linux-gnu`,
   `/lib`, `/usr/lib` (in that order).

The layer populates the two multiarch dirs (core in `/lib/…` via merged-usr
symlink, real files under `/usr/lib/aarch64-linux-gnu`), so a glibc binary's
dependencies resolve BEFORE the search ever reaches musl's `/lib`/`/usr/lib`.
A dependency missing from the layer falls through to those musl dirs and fails
cleanly (`cannot open shared object file`) rather than mixing — a musl `NEEDED`
(`libc.musl-aarch64.so.1`) cannot be satisfied inside a glibc process.
Extension slot: drop glibc-built `.so` into `/usr/lib/aarch64-linux-gnu`.

## Tools

- `pocketshell-doctor <binary>` — arch/class/interp/DT_NEEDED/max-`GLIBC_x.y`
  required version + a REAL resolution check by the loader itself
  (`ld-linux-aarch64.so.1 --list <binary>`), then SUPPORTED/UNSUPPORTED with
  the reason. Requires `binutils` for the readelf fields (falls back to a
  magic-only probe without it; the loader check always runs).
- `pocketshell-exec <binary> [args]` — routing wrapper: musl/static/glibc →
  direct exec (canonical paths make this transparent); explicit
  `--library-path` invocation only as fallback if the multiarch layout is
  absent. Use it to run a glibc binary regardless of how it was invoked;
  normal PATH execs do not need it.

## Symbol versions

glibc records per-symbol requirements (`GLIBC_2.17`, …) in `.gnu.version_r`.
The real loader enforces them against the layer's glibc 2.41; any binary built
against glibc ≤ 2.41 is covered (backward compatibility), which spans every
current mainstream prebuilt. `pocketshell-doctor` reports the max required
version and compares it to the installed layer.

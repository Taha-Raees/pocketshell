# Antigravity CLI on PocketShell — Platform Diagnosis (2026-09-04)

Question: the Antigravity CLI installer detects `linux_arm64_musl` inside the
PocketShell Alpine guest, requests
`https://antigravity-cli-auto-updater-974169037036.us-central1.run.app/manifests/linux_arm64_musl.json`
and gets **HTTP 404**. Is this a PocketShell bug, a network problem, or an
upstream platform gap?

**Verdict: UNSUPPORTED BY UPSTREAM FOR ARM64 MUSL — REQUIRES FUTURE UPSTREAM
RELEASE.** Not a network/DNS/TLS problem, not a PocketShell detection bug,
and not fixable by the app without faking platform identity (forbidden and
pointless — see §5). Every claim below was executed against the real
endpoints/bins on 2026-09-04 (evidence commands inline).

---

## 1. How the installer generates `linux_arm64_musl` (install.sh inspected end-to-end)

`https://antigravity.google/cli/install.sh` (239 lines, sha512-verified
download path, binary name `agy`):

```sh
case "$(uname -s)" in Darwin) os="darwin" ;; Linux) os="linux" ;; esac
case "$(uname -m)"  in x86_64|amd64) arch="amd64" ;; arm64|aarch64) arch="arm64" ;; esac

if [ "$os" = "linux" ]; then
    if [ -f /lib/libc.musl-x86_64.so.1 ] || [ -f /lib/libc.musl-aarch64.so.1 ] \
       || ldd /bin/ls 2>&1 | grep -q musl; then
        platform="linux_${arch}_musl"
    else
        platform="linux_${arch}"
    fi
fi
MANIFEST_URL="$DOWNLOAD_BASE_URL/manifests/$platform.json"
```

- `uname -m` → `aarch64` → `arch=arm64`. `/lib/libc.musl-aarch64.so.1`
  exists in every Alpine rootfs → **`linux_arm64_musl` is the CORRECT
  identification of our environment.** PocketShell does not misreport
  anything (see §4).
- The installer's failure message ("Could not connect to the release
  server") is misleading — `fetch_manifest` swallows the HTTP status
  (`curl -fsSL … || true`), so a 404 prints as a connection error.

## 2. Manifest probe matrix (real HTTP status codes, 2026-09-04)

| Manifest | Status |
|---|---|
| `linux_arm64_musl.json` | **404** |
| `linux_amd64_musl.json` | **404** (no musl build for ANY architecture) |
| `linux_arm64.json` | **200** — glibc ARM64, v1.1.26 |
| `linux_amd64.json` | 200 |
| `linux-arm64.json`, `linux_aarch64.json`, `linux_arm64_gnu.json`, `linux_arm64_glibc.json` | 404 |
| `darwin_arm64.json`, `darwin_amd64.json`, `windows_amd64.json` | 200 |

Upstream ships glibc + macOS + Windows only. There is no musl release of
Antigravity CLI at all — the 404 is an upstream platform gap, not a filter
on our requests.

## 3. Can the glibc build run in the Alpine guest? (executed ladder)

Downloaded `linux_arm64.json`'s payload
(`cli_linux_arm64.tar.gz`, **SHA512 VERIFIED** against the manifest —
`332dddb0…dfc75`), extracted `antigravity`:

```
ELF 64-bit LSB pie executable, ARM aarch64, dynamically linked,
interpreter /lib/ld-linux-aarch64.so.1, for GNU/Linux 3.7.0
NEEDED: libresolv.so.2 libpthread.so.0 libm.so.6 libdl.so.2 librt.so.1 libc.so.6
```

Executed under qemu-aarch64 10.0.0 (real aarch64 code, real loaders) in a
real Alpine 3.24 aarch64 rootfs:

| Runtime | Result |
|---|---|
| **Real glibc** (Debian arm64 libc 2.44 under the SAME qemu) | **`agy --version` → `1.1.26`, exit 0** — the binary is valid and qemu is exonerated |
| Stock Alpine `gcompat` (1.1.0-r4, + libc6-compat) | fails: the binary references glibc-private symbols gcompat does not export — `__read`, `__open`, `__lseek`, `pvalloc` |
| gcompat **+ 4-symbol shim** (scripts/gcompat-extra-shim.c, forwarding to musl) | loader chain completes, binary STARTS, then dies inside its embedded runtime (`[process_state.cc : 794] RAW: Raising signal 11` — sanitizer/crash-handler layer) |

The binary embeds a sanitizer-class runtime; musl's memory/thread/procfs
semantics differ enough that the runtime aborts even after the symbol gaps
are bridged. This is a deep libc-family incompatibility, not a missing
package. We did **not** patch the platform string (would download + verify
fine but produce a broken install) and did **not** touch checksum
verification.

`gcompat`/`libc6-compat` remain useful for SIMPLER glibc binaries and are
already documented in the app's compatibility notes; they are not
sufficient for this one.

## 4. Environment identification audit (Task 5) — PocketShell is correct

Verified in the proot rehearsal (real proot 5.4.0 + Alpine 3.24 aarch64):

```
uname -m                  → aarch64
uname -a                  → Linux … aarch64 (host kernel — proot passthrough, honest)
ldd /bin/busybox          → /lib/ld-musl-aarch64.so.1   (musl, correctly reported)
getconf GNU_LIBC_VERSION  → getconf: GNU_LIBC_VERSION: unknown variable  (glibc-only query — CORRECT on musl)
apk --version             → apk-tools 3.0.6-r0, compiled for aarch64
```

The environment IS Alpine/musl and reports exactly that. Any tool that
detects `linux_arm64_musl` is reading reality. The fix for such tools is
upstream (ship a musl build) or a glibc guest — not spoofing.

## 5. What would make Antigravity work

1. **Upstream musl release** (they already publish `*_musl` detection in
   their own installer — the platform hook exists, the artifacts don't).
   Once `manifests/linux_arm64_musl.json` returns 200, the stock installer
   works in PocketShell unmodified.
2. A real glibc guest (Debian/Ubuntu rootfs) — a possible future
   PocketShell "glibc runtime" alongside Alpine; out of scope for m3.6.
3. NOT viable: forcing `linux_arm64` (glibc build) on musl — §3 shows it
   crashes even with shims; shipping a shimmed, half-running agent would
   violate the no-fake-success rule.

## 6. Rehearsal evidence for the /proc contract + CLI battery (Tasks 1 & 6)

Real proot 5.4.0, Alpine 3.24 aarch64 rootfs, qemu-aarch64 10.0.0
(`scripts/diagnose_platform.sh` automates the environment side):

- **No-`/proc` shape** (the m3.5 regression, what sessions got after an
  in-guest `apk upgrade`): `cat /proc/version` → ENOENT, `ls /proc/self`
  → ENOENT, `ps` → empty table. **Reproduced.**
- **Fixed shape** (vc23's unconditional `--bind=/proc`): `/proc/version`
  prints the kernel banner, `ls /proc/self` → 51 entries,
  `readlink /proc/self/root` → `/`, `/proc/cpuinfo` real.
  `mount | grep proc` shows the HOST mount table — proot binds are ptrace
  path translation, not kernel mounts; that is why the manual
  `mount -t proc proc /proc` "worked" (proot emulates mount(2) by
  recording the bind) and why the app-level `--bind=/proc` is the correct
  initialization-layer fix.
- CLI battery in a FRESH session after a real `apk add`: node v24.18.1,
  npm 11.12.1, Python 3.12.14, git 2.54.0, curl 8.22.0 (real HTTPS fetch →
  HTTP/2 200: DNS, TLS, CA bundle all healthy), OpenSSH_10.3p1
  (`ssh -V` under the app's `--root-id` shape), htop 3.5.3 (reads /proc —
  works only because /proc is bound), apk-tools 3.0.6. No /dev, /tmp,
  HOME, PATH, PTY, signal or fork/exec failures observed.

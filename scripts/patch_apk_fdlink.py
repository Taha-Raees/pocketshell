#!/usr/bin/env python3
"""
PocketShell M2.6 — apk fd-link commit disable patch (pinned, reproducible).

Root cause (docs/M2.6-RESEARCH.md §2): apk-tools 3.0.x (HAVE_O_TMPFILE) picks
its download-commit strategy with is_proc_fd_ok() = access("/proc/self/fd",
F_OK) == 0 (src/io.c, identical in 3.0.6 and 3.0.8). On Android, /proc visible
=> apk commits every cached object with linkat(AT_FDCWD, "/proc/self/fd/N",
atfd, name, AT_SYMLINK_FOLLOW); AOSP system/sepolicy app_neverallows.te
(neverallow all_untrusted_apps file_type:file link) makes the kernel return
EACCES, and fdo_close() cancels the whole download on any linkat failure other
than EEXIST — no fallback. Without /proc apk uses its named-tmpfile + renameat
commit path (plain create/rename/unlink), which is allowed and device-proven.

The patch: in Alpine's own apk-tools-static-3.0.8-r0 binary, flip the LAST
byte of the STANDALONE "/proc/self/fd" rodata literal ("/proc/self/fd" ->
"/proc/self/fX", same length). access() then always fails with ENOENT =>
is_proc_fd_ok() is permanently false => the O_TMPFILE + linkat path is never
taken. The linkat fdname FORMAT string ("/proc/self/fd/%d", used to run
package scripts through an fd with /proc available) is a separate literal and
is untouched. Everything else is stock apk-tools 3.0.8.

Evidence chain (verified 2026-09-02 with NDK r28b llvm-objdump on the exact
binaries below):
  - exactly TWO "/proc/self/fd" literals per binary (format string + gate)
  - exactly ONE code reference to the gate literal:
      aarch64 @26440: adrp x0, 0x29b000 ; add x0, x0, #0x677 ; bl access-impl
      x86_64  @24f37: leaq 0x34471a(%rip), %rdi  # 0x369658 ; callq access
"""
import hashlib
import sys
from pathlib import Path

# Standalone gate literal (NUL-terminated) that is_proc_fd_ok() probes.
GATE_LITERAL = b"/proc/self/fd\x00"
# Replacement: same length, last byte changed. access() on this path can never
# succeed in any POSIX system -> the fd-link commit path is never selected.
PATCHED_LITERAL = b"/proc/self/fX\x00"


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def patch_bytes(data: bytes, label: str) -> bytes:
    occurrences = [i for i in range(len(data)) if data[i : i + len(GATE_LITERAL)] == GATE_LITERAL]
    if len(occurrences) != 1:
        raise SystemExit(
            f"{label}: expected exactly 1 standalone {GATE_LITERAL!r} literal, found {len(occurrences)} — refusing to patch."
        )
    off = occurrences[0]
    mutated = bytearray(data)
    # last literal byte: 'd' (0x64) -> 'X' (0x58)
    if mutated[off + len(GATE_LITERAL) - 2 : off + len(GATE_LITERAL) - 1] != b"d":
        raise SystemExit(f"{label}: literal tail is not 'd' — unexpected binary layout, refusing.")
    mutated[off + len(GATE_LITERAL) - 2] = 0x58
    out = bytes(mutated)
    if out.count(PATCHED_LITERAL) != 1 or out.count(GATE_LITERAL) != 0:
        raise SystemExit(f"{label}: post-patch sanity failed.")
    print(f"{label}: gate literal @ {off} (0x{off:x}) — patched")
    print(f"{label}: sha256 {sha256(data)[:16]}… -> {sha256(out)[:16]}…")
    return out


def main() -> None:
    src_dir = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("/home/z/my-project/scratch/m26")
    out_dir = Path(sys.argv[2]) if len(sys.argv) > 2 else src_dir / "patched"
    out_dir.mkdir(parents=True, exist_ok=True)
    # libapk.so.3.0.0 from the PINNED minirootfs (aarch64 = the file the device
    # rootfs actually ships; x86_64 = the host rehearsal rootfs) is the SHIPPED
    # patch target. The apk-tools-static-3.0.8 variants remain in scratch as
    # documented evaluation artifacts (docs/M2.6-RESEARCH.md §5, Option E-alt).
    targets = [
        ("aarch64", src_dir / "rootfs/usr/lib/libapk.so.3.0.0", "libapk.so.3.0.0.fdlinkoff"),
        ("x86_64", src_dir / "rootfs-x86/usr/lib/libapk.so.3.0.0", "libapk.so.3.0.0.fdlinkoff"),
        ("aarch64-static", src_dir / "apk.static-aarch64", "apk.static.fdlinkoff"),
        ("x86_64-static", src_dir / "apk.static-x86_64", "apk.static.fdlinkoff"),
    ]
    for arch, src, out_name in targets:
        if not src.is_file():
            print(f"{arch}: SKIP (missing input {src})")
            continue
        data = src.read_bytes()
        print(f"{arch}: {src.name} sha256 {sha256(data)}")
        patched = patch_bytes(data, arch)
        dst = out_dir / f"{out_name}.{arch}"
        dst.write_bytes(patched)
        dst.chmod(0o755)
        print(f"{arch}: wrote {dst} sha256 {sha256(patched)}")


if __name__ == "__main__":
    main()

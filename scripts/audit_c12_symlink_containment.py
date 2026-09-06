#!/usr/bin/env python3
"""C12 evidence: verify every symlink in BOTH pinned archives (Alpine
minirootfs 3.24.1 + PocketShell glibc layer rev=2) resolves LEXICALLY inside
the guest root. This proves a target-containment guard in the extractors
would accept the pinned artifacts unchanged (no false rejection), while
rejecting escaping links.
"""
import posixpath
import sys
import tarfile

ARCHIVES = [
    ("/tmp/c4evidence/minirootfs.tar.gz", "Alpine minirootfs 3.24.1 (pinned rootfs)"),
    ("/home/z/my-project/app/src/main/assets/guest/pocketshell-glibc-aarch64-2.41-12.deb13u3.tar.gz",
     "PocketShell glibc layer rev=2 (pinned sidecar)"),
]


def contained(name: str, target: str) -> bool:
    """Lexical containment of a symlink (name -> target) inside the guest root.
    Guest-view semantics (proot): ABSOLUTE targets are guest paths (mapped into
    the rootfs by proot, so they are contained by construction); RELATIVE
    targets resolve against the link's parent and must never climb above the
    root. The extractor enforces the same rule host-side for entry NAMES."""
    if target.startswith("/"):
        resolved = posixpath.normpath(target)
        return resolved == "/" or not resolved.startswith("..")
    resolved = posixpath.normpath(posixpath.join(posixpath.dirname(name), target))
    # Relative: contained iff normalized path does not escape above the root.
    return not (resolved == ".." or resolved.startswith("../"))


def main() -> int:
    failures = 0
    for path, label in ARCHIVES:
        total = outside = 0
        with tarfile.open(path, "r:*") as tf:
            for m in tf:
                if not m.issym():
                    continue
                total += 1
                if not contained(m.name, m.linkname):
                    outside += 1
                    print(f"  ESCAPES: {m.name} -> {m.linkname}")
        status = "OK" if outside == 0 else "FAIL"
        print(f"{status}  {label}: {total} symlinks, {outside} escape the guest root")
        failures += outside

    # Malicious shapes the guard must reject (sanity of the containment math):
    # relative targets climbing above the guest root. Absolute targets are
    # guest-view paths (proot-confined) — the HOST-side hazards are walk-
    # through-symlink deletes and file writes under symlinked dirs, covered
    # by the NOFOLLOW-delete and intermediate-symlink hardening in the app.
    hostile = [
        ("lib/evil", "../../../../etc/shadow"),
        ("a/b/c", "../../../escape"),
    ]
    for name, target in hostile:
        if contained(name, target):
            print(f"GUARD-SANITY FAIL: {name} -> {target} wrongly accepted")
            failures += 1
        else:
            print(f"GUARD-SANITY OK: rejects {name} -> {target}")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())

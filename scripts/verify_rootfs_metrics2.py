#!/usr/bin/env python3
"""Task 14 v2: precise per-symlink measurement of the extracted rootfs.

Goal: reproduce the on-device number ("9.3 MB" = bytes/1MiB in [9.25, 9.35)).
Ground truth pieces:
  - true tree (no follow): regular files, link nodes, dirs
  - A = sum of regular-file sizes (lstat)
  - per-link classification: absolute vs relative target; where it resolves
  - device prediction H1: A + targets of RELATIVE links that resolve inside
    the rootfs (absolute targets dangle on Android: no /bin, /usr, ... there)
  - desktop/sandbox prediction: A + relative + absolute-that-resolve-here
"""
import os
import sys

root = os.path.abspath(sys.argv[1])

reg_files = []          # (relpath, size)
links = []              # (relpath, linkname, is_abs, resolves, target_size, in_rootfs)
dirs = 0

for dirpath, dirnames, filenames in os.walk(root, followlinks=False):
    dirs += len(dirnames)
    for name in filenames:
        p = os.path.join(dirpath, name)
        rel = os.path.relpath(p, root)
        st = os.lstat(p)
        if os.path.islink(p):
            linkname = os.readlink(p)
            is_abs = linkname.startswith("/")
            resolves = False
            in_rootfs = False
            tsize = 0
            if os.path.exists(p):                    # resolves (follows fully)
                resolves = True
                rp = os.path.realpath(p)
                in_rootfs = rp.startswith(root + os.sep) or rp == root
                if os.path.isfile(p):
                    tsize = os.path.getsize(p)
            links.append((rel, linkname, is_abs, resolves, tsize, in_rootfs))
        else:
            reg_files.append((rel, st.st_size))

A = sum(s for _, s in reg_files)
rel_resolved_sum = sum(t for _, ln, is_abs, res, t, inr in links if not is_abs and res)
abs_resolved_sum = sum(t for _, ln, is_abs, res, t, inr in links if is_abs and res)
abs_in_rootfs_sum = sum(t for _, ln, is_abs, res, t, inr in links if is_abs and res and inr)
dangling = sum(1 for _, _, _, res, _, _ in links if not res)
res_count = sum(1 for _, _, _, res, _, _ in links if res)

print(f"tree: files={len(reg_files)} links={len(links)} dirs={dirs}")
print(f"A (regular files)            = {A}  = {A/1048576:.3f} MiB")
print(f"links resolving (total)      = {res_count}, dangling = {dangling}")
print(f"  relative-link resolved sum = {rel_resolved_sum}  ({sum(1 for l in links if not l[2] and l[3])} links)")
print(f"  absolute-link resolved sum = {abs_resolved_sum}  ({sum(1 for l in links if l[2] and l[3])} links; of which inside rootfs: {abs_in_rootfs_sum})")

lo, hi = int(9.25 * 1048576), int(9.35 * 1048576)
h1 = A + rel_resolved_sum
h_desktop = A + rel_resolved_sum + abs_resolved_sum
print(f"device prediction H1 (A + relative-resolved)  = {h1} = {h1/1048576:.3f} MiB -> "
      f"{'MATCHES 9.3 window' if lo <= h1 < hi else 'outside window'}")
print(f"desktop/sandbox prediction (A + rel + abs)    = {h_desktop} = {h_desktop/1048576:.3f} MiB")

print("\ntop 12 regular files:")
for rel, s in sorted(reg_files, key=lambda x: -x[1])[:12]:
    print(f"  {s:>9}  {rel}")

print("\nall resolving RELATIVE links (device-relevant):")
for rel, ln, is_abs, res, t, inr in links:
    if not is_abs and res:
        print(f"  {t:>9}  {rel} -> {ln}")

print("\nresolving ABSOLUTE links (sandbox-resolved; these dangle on Android):")
for rel, ln, is_abs, res, t, inr in links:
    if is_abs and res:
        print(f"  {t:>9}  {rel} -> {ln}")

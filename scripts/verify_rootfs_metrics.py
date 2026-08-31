#!/usr/bin/env python3
"""Task 14: measure extracted Alpine minirootfs against the app's own logic.

The app (RuntimeDiagnostics.kt) computes:
    directorySize(dir) = dir.walkTopDown().filter { it.isFile }
                         .fold(0L) { acc, f -> acc + f.length() }
and displays it as "%.1f MB".format(bytes / (1 shl 20))  -> the on-device
unit is MiB, so "9.3 MB" corresponds to bytes in [9.25, 9.35) MiB.

Reported metrics:
  A) true content size  : lstat sizes of non-symlink regular files
  B) app emulation      : java.io.File.isFile()/length() follow symlinks and
                          walkTopDown descends into symlinked dirs, so emulate
                          with os.walk(followlinks=True) + os.path.isfile +
                          os.path.getsize (with a loop guard the JVM lacks).
"""
import os
import sys

root = os.path.abspath(sys.argv[1])

true_bytes = true_count = 0
sym_count = dangling = 0
app_bytes = app_count = 0
dir_count = 0
visited_real = set()
loops_skipped = 0

for dirpath, dirnames, filenames in os.walk(root, followlinks=True):
    real_dir = os.path.realpath(dirpath)
    if real_dir in visited_real:
        loops_skipped += 1
        dirnames[:] = []
        continue
    visited_real.add(real_dir)
    dir_count += len(dirnames)
    for name in filenames:
        p = os.path.join(dirpath, name)
        st = os.lstat(p)
        if os.path.islink(p):
            sym_count += 1
            if not os.path.exists(p):
                dangling += 1
            if os.path.isfile(p):                    # app: it.isFile() (follows)
                app_bytes += os.path.getsize(p)      # app: f.length() (follows)
                app_count += 1
        else:
            true_bytes += st.st_size
            true_count += 1
            app_bytes += st.st_size
            app_count += 1


def fmt(b):
    return f"{b / 1048576:.2f} MiB ({b / 1e6:.2f} MB)"


print(f"A_true_regular_files : count={true_count} bytes={true_bytes} -> {fmt(true_bytes)}")
print(f"   symlinks          : {sym_count} (dangling: {dangling})")
print(f"   dirs visited      : {dir_count} (loop-guards hit: {loops_skipped})")
print(f"B_app_emulation      : count={app_count} bytes={app_bytes} -> {fmt(app_bytes)}")

lo, hi = int(9.25 * 1048576), int(9.35 * 1048576)
print(f"device window for '9.3 MB' = [{lo}, {hi}) bytes (MiB formatting)")
print(f"verdict B (app emulation) : {'MATCHES device 9.3' if lo <= app_bytes < hi else 'does NOT match device 9.3'}")
print(f"verdict A (true content)  : {'MATCHES device 9.3' if lo <= true_bytes < hi else 'does NOT match device 9.3'}")

# runtime/TESTING — the executable compatibility suite

Two runners, one matrix:

| Runner | Where | Purpose |
|---|---|---|
| `scripts/runtime/run_sandbox_suite.sh` | sandbox emulation rig (proot + qemu-aarch64 + the SAME pinned Alpine 3.24.1 rootfs + the SAME pinned glibc layer) | pre-ship regression gate |
| `runtime-tests/run_on_device.sh` | the user's device, inside the PocketShell guest terminal | the honest device gate |

## Sandbox rig (recreatable after any sandbox reset)

```
scripts/runtime/rig_setup.sh            # qemu-aarch64 (static), pinned minirootfs,
                                        # pinned glibc layer, cross toolchain
scripts/runtime/build_test_binaries.sh  # aarch64 glibc test binaries (cross gcc 14)
scripts/runtime/run_sandbox_suite.sh    # the matrix
```

Latest full results: **20/20 PASS** (2026-09-05, emulation) and the REAL
DEVICE gate: **24/24 PASS — ALL GREEN** (2026-09-06, v0.10.0-m6.0.2, real
Debian glibc 2.41) including `cline --version` / `--help` / node-spawn chain
/ relaunch ×3 on the real Cline 3.0.61 binary. Suite v2.2 (m6.0.3) adds the
three permanent doctor rows (anchored-verdict t_cline_shape, musl
classification, selftest matrix) → **27 rows** on a Cline-equipped device.

## Device gate (paste-ready)

PocketShell ≥ v0.10.0-m6.0.0, one fresh session, then:

```
curl -fsSL <mirror>/pocketshell-runtime-tests-aarch64.tar.gz | tar -xz -C /tmp
sh /tmp/pocketshell-tests/run_on_device.sh
```

Sections: musl regression (sh/bash/git/curl/node/npm/apk), static, real glibc
layer (loader --version, hello, pthread, dlopen, libm, C++ exceptions,
fork+exec across libcs, NSS passwd, NSS DNS, Cline-shaped DT_NEEDED),
pocketshell-doctor rows (anchored verdict on t_cline_shape, musl
classification on /bin/busybox, the `--selftest` semantic-comparison matrix,
and the real-Cline verdict inside the Cline section), Cline end-to-end
(version/help/node-spawn/relaunch; `CLINE_DEEP_TEST=1` adds an
initialization attempt when credentials exist).

## M6 final device gate (guided, resumable)

`device_gate.sh` (ships in the tests tarball beside the suite) drives the
remaining destructive closure drills with the least user effort. Each stage
is idempotent and re-runnable; `status` always prints the exact next command.

    sh /tmp/pocketshell-tests/device_gate.sh gate
      → baseline identity checks (app stamp, marker pin, REAL Debian loader,
        musl, suite binaries, disk) then the C2 corruption drills
      → ACTION REQUIRED: open ONE new PocketShell session
    sh /tmp/pocketshell-tests/device_gate.sh resume-c2
      → proves the app's session prep re-extracted (provenance: the layer
        marker must have been RE-WRITTEN after the drill armed + status file
        corroboration — a `heal --manual` restore canNOT pass) then arms C4
      → ACTION REQUIRED: open ONE new PocketShell session
    sh /tmp/pocketshell-tests/device_gate.sh resume-c4
      → proves the loader was restored by session prep; records the measured
        C4 answer (did `apk fix gcompat` reclaim naturally, or was the arm
        simulated with the genuine shim); arms C5
      → ACTION REQUIRED: open ONE new PocketShell session
    sh .../device_gate.sh resume-c5
      → proves the fast path survived the package operations; runs FINAL
        (full 27-row suite + adversarial probe + summary)

Stages: `gate | baseline | c2 | resume-c2 | c4 | resume-c4 | c5 | resume-c5 |
final | status`. Safety: the drills only touch PocketShell-owned layer files
(loader path, multiarch libs, marker); the Alpine rootfs, user projects and
Android storage are never touched; destructive stages refuse to run until
baseline passes. The runner never repairs the layer itself — healing must
come from PocketShell's own `GuestGlibcRuntime` session-prep path.

## Test binary provenance

`runtime-tests/src/*.c(pp)` cross-compiled with the pinned Debian cross gcc 14
against glibc 2.41 — sources committed; shapes verified with readelf
(`t_pthread`/`t_libm`/`t_cline_shape` force the DT_NEEDED entries they claim to
test; `t_cline_shape` replicates Cline's exact dependency class:
`libc.so.6 + libpthread.so.0 + libdl.so.2 + libm.so.6`).

## App-side (JVM) pins

`GuestGlibcRuntimeTest` — extraction, marker contract (written LAST, exact
content), fast-path idempotence, self-healing, musl-sentinel untouched,
zip-slip + absolute-path guards, truncated-archive failure honesty, and the
pinned asset bytes-vs-`GlibcRuntimePin` integrity check.

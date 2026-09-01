# PocketShell — Offline Artifact Manifest

Primary release: **v0.3.1-m2.3** (versionCode 6, git tip bfed594)

| Artifact | sha256 | Size |
|---|---|---|
| PocketShell-v0.3.1-m2.3-debug.apk | 861f994f0afb495bff1513cb66daf2fb620e087d003253176d90c9174d54951c | 21 MB |
| PocketShell-v0.3.1-m2.3-source.zip (265 files) | 09d1039f9780daa52b45d6fbc459984a65ec9e72124b91995099b9cee31b6a4b | 6.5 MB |
| PocketShell-v0.3.1-m2.3-source.tar.gz | 5fc8cc1e82a1e38b65e8244685bad90f9250b303499ea9b37600e937fa7dc70d | 6.4 MB |
| pocketshell-m2.gitbundle (full history) | e2d6d8642097bf694c5b21d250cb28bda3cb677643170738317b491c27e1af12 | 3.1 MB |

New in v0.3.1-m2.3 (device crash fix, user recording 2026-09-01):
- extractNativeLibs=true (v0.3.0 shipped libs only inside the APK → empty
  nativeLibraryDir → require() escaped the Linux Shell click handler → app
  death)
- targetSdk 36 → 28: AOSP W^X blocks app_data execve at targetSdk ≥ 29;
  28 → untrusted_app_27 where proot may exec the Alpine guest (Termux model)
- crash-proof launch path: pure preflight + single no-crash boundary + honest
  Home error banner; a refused launch can never kill the process again
- 215 unit tests, 0 failures. Same signing cert as v0.2.x/v0.3.0 → installs
  as a direct update, installed runtime data kept.

Device gate (docs/TESTING.md §8): `uname; id; echo hello` inside the guest.

Previous: v0.3.0-m2.3 (proot Linux shell; crashed on Linux Shell tap —
superseded). v0.2.1-m2.2 (runtime installer; device gate §7 PASSED
2026-09-01). v0.2.0-m2.2-wip withdrawn (crashed). Older sources live in the
git bundle history. All artifacts also served by the web app at /.

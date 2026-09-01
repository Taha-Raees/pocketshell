# PocketShell — Offline Artifact Manifest

Primary release: **v0.3.2-m2.3** (versionCode 7, git tip 9066068)

| Artifact | sha256 | Size |
|---|---|---|
| PocketShell-v0.3.2-m2.3-debug.apk | 25bd9feb4a7bc5ab9b3ed098a07dc859b300422b15b7fbeeef6bf72f7abe0ca0 | 21 MB |
| PocketShell-v0.3.2-m2.3-source.zip (226 files) | c156738be9c06dae498dfe72e029f2b08335cfd48698c6ae8d21519e3b6264e7 | 4.6 MB |
| PocketShell-v0.3.2-m2.3-source.tar.gz | be45d8cf4c3fc64b86f7bfee29871099b5c3aded11f122d2c47143e68c4fa72d | 4.5 MB |
| pocketshell-m2.gitbundle (full history) | 744594e5e16619f1a01967656555d358ca235fd4237ad5e969387741b92fafea | 3.2 MB |

New in v0.3.2-m2.3 (guest linker fix, user recording 2026-09-01 11:02):
- LD_LIBRARY_PATH=<nativeLibraryDir> now ships in the exec environment:
  bionic resolves proot's DT_NEEDED libtalloc.so only from default system
  paths + LD_LIBRARY_PATH — never from nativeLibraryDir, so the v0.3.1 guest
  died at `CANNOT LINK EXECUTABLE … library "libtalloc.so" not found`. (The
  sandbox rehearsal had masked this by exporting the variable in a shell.)
- argv[0] is now the proot path (execvp passes the args array verbatim): the
  linker error quoted "--kill-on-exit" as the executable name and proot's
  getopt silently swallowed the flag.
- Preflight also verifies libtalloc.so next to proot/loader — the failure
  class now surfaces as an honest message before any spawn.
- Payload hygiene: ./scratch (debug frames, apk-check libs) is excluded from
  source archives (the v0.3.1 zip accidentally carried it).
- 218 unit tests, 0 failures. Same signing cert as v0.2.x/v0.3.x → installs
  as a direct update, installed runtime data kept.

Device gate (docs/TESTING.md §8): `uname; id; echo hello` inside the guest.

Previous: v0.3.1-m2.3 (tap-crash fix: extractNativeLibs + targetSdk 28;
guest still died at link — superseded). v0.3.0-m2.3 (proot Linux shell;
crashed on Linux Shell tap — superseded). v0.2.1-m2.2 (runtime installer;
device gate §7 PASSED 2026-09-01). v0.2.0-m2.2-wip withdrawn (crashed).
Older sources live in the git bundle history. All artifacts also served by
the web app at /.

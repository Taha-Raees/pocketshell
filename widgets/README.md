# widgets/ — the PocketShell optional-widget catalog (format v1)

This directory is the DISTRIBUTION FORMAT for optional Home widgets
(docs/M8-WIDGET-SYSTEM.md §7). It currently holds EXAMPLES authored by the
core app so the format is real and test-validated, not hypothetical:
`HomeWidgetContractTest` parses and validates every manifest here against
the implementation on every suite run (drift guard).

Milestone status: **schema + validator + renderer shipped; installer UI and
network fetch deliberately NOT built yet** (trust-model stage 1 of 3 — see
M8-WIDGET-SYSTEM.md §7). Nothing here is executed by the app today.

## Layout

    widgets/
      registry.json            the catalog index
      <id>/manifest.json       one manifest per optional widget

## registry.json

```json
{
  "schemaVersion": 1,
  "catalogVersion": "1.0.0",
  "widgets": [
    { "id": "servers", "name": "Servers", "version": "1.0.0",
      "manifestPath": "servers/manifest.json", "sha256": "..." }
  ]
}
```

`sha256` is the manifest artifact digest, pinned at publication time. When
this moves to its own GitHub repository (right thing once third parties
contribute), the app pins the registry URL + branch and verifies digests
exactly the way `RuntimeInstaller` pins the rootfs.

## manifest.json rules (enforced by WidgetManifestValidator)

- `id`: `^[a-z][a-z0-9-]{1,31}$`; `name` ≤ 24 printable chars.
- `version`, `minAppVersion`: semver `MAJOR.MINOR.PATCH`.
- `capabilities`: subset of `proc.net`, `storage.rootfs`, `guest.ready`,
  and must COVER the probe kind.
- `probe.kind`: a BUILT-IN primitive only — `proc.net.listen` or
  `storage.rootfs`. **There is no shell-command surface in v1.** A future
  command-probe stage requires signed manifests + explicit consent
  (M8-WIDGET-SYSTEM.md §7 stage 3); checksums alone do not make remote
  commands safe.
- `card`: `headline` ≤ 24, `emptyLine` 1–40, `itemTemplate` ≤ 64 (plain
  `{field}` substitution — `{port}`/`{process}` for listeners,
  `{name}`/`{size}` for storage), `maxLines` 1–6.
- Unknown JSON keys REJECT (no schema drift). Malformed input can only
  produce a rejection — never a partially trusted widget.

## Adding an optional widget

1. `widgets/<id>/manifest.json` — validate locally against
   `WidgetManifestValidator` (the contract test does this automatically).
2. Add the entry to `registry.json` with the manifest's sha256.
3. Justify the widget against the product question
   (docs/M8-WIDGET-RESEARCH.md): what does a Linux developer get here
   that a terminal command does not give them faster?

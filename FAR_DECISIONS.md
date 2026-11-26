# FAR Gallery — Owner Decision Log

This file is the record of owner decisions and approvals. The agent does not invent approvals.

## How to use
- Every approval is written here as plain text, for example:
  - `approved: Vault`
  - `approved: schema v12 migration`
  - `approved: Phosphor icons`
  - `approved: stable Room upgrade to 2.x`
  - `approved: Local network flavour`
- If it is not in this file, the agent does not proceed through the relevant approval gate.

## Owner conflicts to resolve early (pack §A7)

### Vault
- Note from 2026-11-04: "remove the private vault and its screen entirely".
- P37 is an optional gate and is OFF by default.
- Build only App Lock and Hidden albums unless this file says `approved: Vault`.
- Nothing else in the pack depends on the Vault.

Status: **vault = OFF by default** (no `approved: Vault` present yet).

### Look
- UI should resemble Apple Photos / Google Photos; Camera should resemble the iPhone Camera; Files should resemble Apple Files + Ubuntu Files (Nautilus).
- This is a style interpretation with original assets, never copied artwork, names or fonts.

Status: **style direction noted; no final asset decision recorded yet**.

### Formats
- Android + iPhone media formats are supported as far as the device can decode.
- Where a format cannot be decoded, the app must say so; never show a blank tile.

Status: **accepted as a product rule; no format-list approval needed yet**.

## Approvals

### Already applied in this repo (record once confirmed by owner)
- `_pending_: diagnostics export prefix made deterministic and then repaired to `far_diagnostics_` after a regression; project `test` passed this session` — low-risk project hygiene change made this session.
- `_pending_: P00 context files created (FAR_RULES.md, FAR_PROGRESS.md, FAR_DECISIONS.md)` — P00 startup step completed from the master pack.
- `_pending_: Startup/skeleton alignment pass completed this session; no edits required against the existing production skeleton` — completed this session.

### Waiting for owner approval
- None recorded yet beyond the defaults above.

## Decision rules
- Do not rename the app, package or applicationId.
- Do not add network features, replace the database, add a model-download mechanism, or make major dependency changes without an approval recorded here.
- Do not change the strict-offline posture without an approval recorded here.

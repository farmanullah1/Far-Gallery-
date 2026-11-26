# FAR Gallery — Progress Tracker

Main pack: `FAR_GALLERY_ULTIMATE_MASTER_PROMPT_PACK_V3.md` (P00–P60).

## How to use
- This file is the long-term memory for the agent. Each prompt updates one row.
- Columns: Prompt | Phase | Status | Commit | Notes | Open issues.

## Phase 0 — Truth & foundation
| Prompt | Status | Commit | Notes | Open issues |
|---|---|---|---|---|
| P00 | DONE | _TBD_ | Repo audit; 3 context files created; startup/skeleton alignment pass completed this session (no edits needed); diagnostics export prefix repaired after a regression; baseline build (assembleDebug) and test (test) passed. | Update commit once related work is committed. |
| P01 | PENDING | — | Architecture / migration / implementation proposal already written in `P01_ARCHITECTURE_MIGRATION_IMPLEMENTATION_PROPOSAL.md`. This is an approval gate: no code/build changes until it is approved or changed by name. | Waiting for owner approval of P01. |
| P02 | PENDING | — | Build reproducibility and dependency stabilisation. | Depends on P01. |
| P03 | PENDING | — | Reliability foundation and unified error model. | Depends on P02. |
| P04 | PENDING | — | Database recovery, migrations and metadata preservation. | Depends on P03. |
| P05 | PENDING | — | Large-library test fixtures and performance guardrails. | Depends on P03/P04. |

## Phase 1 — Data & operations
| Prompt | Status | Commit | Notes | Open issues |
|---|---|---|---|---|
| P06 | PENDING | — | Canonical asset identity and Room data model. | |
| P07 | PENDING | — | | |
| P08 | PENDING | — | | |
| P09 | PENDING | — | | |
| P10 | PENDING | — | | |
| P60 | PENDING | — | | |

## Phase 2 — Design & shell
| Prompt | Status | Commit | Notes | Open issues |
|---|---|---|---|---|
| P11 | PENDING | — | | |
| P12 | PENDING | — | | |
| P53 | PENDING | — | | |
| P54 | PENDING | — | | |
| P55 | PENDING | — | | |
| P56 | PENDING | — | | |
| P13 | PENDING | — | | |
| P14 | PENDING | — | | |

## Phase 3 — Photos core
| Prompt | Status | Commit | Notes | Open issues |
|---|---|---|---|---|
| P15 | PENDING | — | | |
| P16 | PENDING | — | | |
| P17 | PENDING | — | | |
| P18 | PENDING | — | | |
| P19 | PENDING | — | | |
| P20 | PENDING | — | | |
| P21 | PENDING | — | | |

## Phase 4 — Intelligence
| Prompt | Status | Commit | Notes | Open issues |
|---|---|---|---|---|
| P22 | PENDING | — | | |
| P23 | PENDING | — | | |
| P24 | PENDING | — | | |
| P25 | PENDING | — | | |
| P26 | PENDING | — | | |

## Phase 5 — Editing & creations
| Prompt | Status | Commit | Notes | Open issues |
|---|---|---|---|---|
| P28 | PENDING | — | | |
| P29 | PENDING | — | | |
| P31 | PENDING | — | | |
| P27 | PENDING | — | | |
| P58 | PENDING | — | | |
| P30 | PENDING | — | Gated. | |

## Phase 6 — Files
| Prompt | Status | Commit | Notes | Open issues |
|---|---|---|---|---|
| P32 | PENDING | — | | |
| P33 | PENDING | — | | |
| P34 | PENDING | — | | |
| P35 | PENDING | — | | |
| P36 | PENDING | — | | |
| P59 | PENDING | — | | |

## Phase 7 — Camera
| Prompt | Status | Commit | Notes | Open issues |
|---|---|---|---|---|
| P39 | PENDING | — | | |
| P40 | PENDING | — | | |
| P41 | PENDING | — | | |
| P43 | PENDING | — | | |
| P42 | PENDING | — | | |

## Phase 8 — System, privacy & backup
| Prompt | Status | Commit | Notes | Open issues |
|---|---|---|---|---|
| P44 | PENDING | — | | |
| P45 | PENDING | — | | |
| P46 | PENDING | — | | |
| P37 | PENDING | — | Optional gate. Vault OFF by default unless `FAR_DECISIONS.md` says `approved: Vault`. | |
| P38 | PENDING | — | | |
| P57 | PENDING | — | | |

## Phase 9 — Ops, polish & release
| Prompt | Status | Commit | Notes | Open issues |
|---|---|---|---|---|
| P47 | PENDING | — | | |
| P48 | PENDING | — | | |
| P49 | PENDING | — | | |
| P50 | PENDING | — | | |
| P51 | PENDING | — | | |
| P52 | PENDING | — | | |

## Current session notes
- This turn built the first P00 context files (`FAR_RULES.md`, `FAR_PROGRESS.md`, `FAR_DECISIONS.md`) from the master pack.
- Prior session evidence still applies: manifest removes `INTERNET`/`ACCESS_NETWORK_STATE`; DB recovery path is backup/salvage/restore; defect #4 clearer rejection already applied in `AppErrors.kt`; diagnostics export prefix was made deterministic; project `test` task passed.

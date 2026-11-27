# P01 — Architecture, Migration and Implementation Proposal

**Status:** PROPOSAL GATE — no code, Gradle, manifest or dependency change has been made for this document.
**Written:** 2026-11-27 · **Repo HEAD at time of writing:** `3061ed5` (chore: add Windows optimization audit scripts)
**Master pack:** `FAR_GALLERY_ULTIMATE_MASTER_PROMPT_PACK_V3.md`, P01 (deliverables 1–12).
**Supersedes:** `P01_ARCHITECTURE_MIGRATION_IMPLEMENTATION_PROPOSAL.md` (root, earlier thin draft). That file is left in place, untouched, for history.

> Every claim below cites the file and line it came from. `APP_OVERVIEW.md` is **not** used as evidence: it describes 4 tabs, `room 2.7.0-alpha08`, `camera 1.5.0-alpha01`, `deleteDatabase("far.db")`, `IncomingMediaResolver.kt` and `EditState` — none of which match the code.

---

## 0. Baseline (what actually exists today)

### 0.1 Project identity

| Item | Value | Evidence |
|---|---|---|
| namespace | `com.example` | `app/build.gradle.kts:6` |
| applicationId | `com.aistudio.fargallery.mnpxqv` | `app/build.gradle.kts:9` |
| minSdk / targetSdk / compileSdk | 30 / 36 / 36 | `app/build.gradle.kts:8-11` |
| AGP / Kotlin / KSP | 9.1.1 / 2.2.10 / 2.3.5 | `gradle/libs.versions.toml:2,10,11` |
| Compose BOM | 2024.09.00 | `gradle/libs.versions.toml:9` |
| Room / CameraX / Media3 / Coil | 2.7.0 / 1.5.0 / 1.5.1 / 2.7.0 | `gradle/libs.versions.toml:14,19,24,17` |
| ML Kit text / barcode | 16.0.1 / 17.3.0 | `gradle/libs.versions.toml:25,26` |
| Room schema version | **3** (22 entities) | `app/src/main/java/com/example/core/database/Db.kt:589` |
| Source size | 55 Kotlin files, `AppNav.kt` alone is 130.9 KB | repo |

### 0.2 Build reality (this is a P02 blocker, recorded here as baseline)

`assembleDebug` + `testDebugUnitTest` were run on this machine (Windows, JDK 21, Gradle 9.3.1 from `~/.gradle/wrapper/dists`).

| Fact | Detail |
|---|---|
| `gradlew.bat` does not exist | Only `gradlew` (179 bytes) exists, and it hard-codes `C:\Users\farma\.gradle\...\gradle-9.3.1\bin\gradle` (`gradlew:3`). The project cannot be built by the wrapper on any other machine or by a Windows shell. |
| `local.properties` was absent | First build failed: `SDK location not found … at 'D:\Ai Studio\Far-Gallery-\local.properties'`. Created locally (gitignored) to run the baseline. |
| `debug.keystore` is gitignored | `.gitignore` lists `debug.keystore`, yet `app/build.gradle.kts:24-29` requires `${rootDir}/debug.keystore` for every debug build. A clean checkout cannot build debug. |
| Release signing | `KEYSTORE_PATH`/`STORE_PASSWORD` env or `my-upload-key.jks` (`app/build.gradle.kts:18-23`); neither present. Release build unverified. |
| Baseline tests | **FAILING.** `SharedInboxTest.incomingMediaResolver_peekMediaType_readsMinimalHeaderForValidJpeg` fails at `SharedInboxTest.kt:29`; `SharedInboxTest.incomingMediaResolver_canRead_filtersTheSameSharedInboxPath` fails at `SharedInboxTest.kt:47`. Both were added in the last two commits (`c96381d`, `8722c21`). |
| Why they fail | (a) `SharedInboxTest.kt:29` asserts `assertFalse(peekedMime.isNotEmpty())` — a test bug that can never pass. (b) `SharedInboxTest.kt:47` is a **real production bug**: `IncomingMediaResolver.canRead` (`AppErrors.kt:279`) ignores `peekMediaType`'s null result, so any readable non-media file returns `true`. |

### 0.3 Verified posture

- **Strict offline holds.** Merged manifest (`app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml`) contains no `INTERNET`, no `ACCESS_NETWORK_STATE`, no `MANAGE_EXTERNAL_STORAGE`. Declared: `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_VISUAL_USER_SELECTED`, `ACCESS_MEDIA_LOCATION`, `CAMERA`, `RECORD_AUDIO`, `POST_NOTIFICATIONS`, `READ_EXTERNAL_STORAGE` (maxSdk 32), plus transitive `USE_BIOMETRIC`, `USE_FINGERPRINT`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`. Network/telemetry components are stripped by `tools:node="remove"` (`AndroidManifest.xml:6-7,152-162`).
- **No destructive DB fallback.** `fallbackToDestructiveMigration` and `deleteDatabase` appear **nowhere** in `app/src` (grep: 0 matches). Recovery archives to `filesDir/db_corrupt` (max 3 snapshots), salvages 13 tables read-only, rebuilds and re-inserts (`Db.kt:837-971`).
- **Shell is already 3 destinations.** `FarTab = Photos, Camera, Files` (`ui/nav/GlassBottomBar.kt:63-72`) with `PhotosSubTab = Library, Albums, Search` (`GlassBottomBar.kt:74-78`). The pack's P13 target is therefore a **refinement**, not a rewrite.
- **Error model already exists.** `sealed interface FarError` with 15 kinds (`core/common/AppErrors.kt:20-37`), `FarResult<T>` (`:39-42`), `farCatching` that rethrows `CancellationException` and maps `ENOSPC`/`SecurityException`/`OOM` (`:51-75`), plus localised title/summary resolvers (`:156-226`) and a sanitised `LocalDiagnostics` (30 lines, no paths/PII, `:78-154`).
- **A Vault exists in code** — `feature/vault/VaultScreen.kt` (70.6 KB), `core/vault/VaultRepository.kt`, `core/vault/VaultCrypto.kt`, `vault_item` + `identity_doc` tables (`Db.kt:178-219`), route `"vault"` (`AppNav.kt:3675`). The owner note of 2026-11-04 says *remove the private vault*. **This conflict is unresolved and is decision D-1 below.**

---

## 1. Target layers and package layout

### 1.1 Decision: keep the packages, add boundaries inside them

No mass rename, no module split in Phase 1 (G1, G13). The existing packages already map onto the pack's contract:

| Pack layer | Existing package(s) | Rule to enforce |
|---|---|---|
| UI (Compose, state hoisted) | `feature/*`, `ui/*`, `app/AppNav.kt` | May not call `ContentResolver`, `DocumentsContract`, `Room` or codecs directly. |
| ViewModel (one `UiState` + one-shot `Effect`) | `GalleryViewModel`, `CameraViewModel`, `FilesViewModel`, `AppViewModel` | No `Context` in constructor except `Application`; expose `StateFlow`, not callbacks that do I/O. |
| UseCase (pure Kotlin → `FarResult<T>`) | **new**, `core/usecase/` | One public function per use case; no Android framework types except `Uri`. |
| Repository (only place touching MediaStore/SAF/Room/CameraX/Media3/ML) | `core/media`, `core/storage`, `core/database`, `core/vault` | Public functions return `FarResult` and never throw. |
| Source | MediaStore, SAF, app-private | Selected by `MediaSource` enum, not by string checks. |

**Known violations to fix in Phase 1** (these are the reason the rule exists):

- `app/AppNav.kt` both navigates and performs data work, e.g. per-item DB reads in the viewer path (`AppNav.kt:1478, 1483`) and a 130 KB file that owns routing, ViewModel construction and feature wiring.
- `feature/files/FilesViewModel.kt` is 39 KB and reaches into `DocumentOps` directly.

**Proposed physical split of `AppNav.kt` (no route renames):**

```
app/nav/AppNavHost.kt          // onboarding, main, overlays
app/nav/PhotosNavHost.kt       // album/{albumId}, collection/{kind}, viewer, compare, duplicates, excluded
app/nav/FilesNavHost.kt        // files tree + storage
app/nav/CameraNavHost.kt       // camera + editor
app/IntentRouter.kt            // unchanged contract
```

Each top-level destination keeps its own back stack (`saveState`/`restoreState`). This is a move, not a rewrite: route strings and call sites stay byte-identical, so the existing navigation tests keep passing.

### 1.2 Non-goals for Phase 1

- No Gradle module split (`:core:media` etc.). It buys compile-time isolation at the cost of a large, risky build change; revisit after P52.
- No DI framework. Manual construction already exists (`FarDatabase.get(ctx)`, `GalleryViewModel.factory(...)`); adding Hilt now would touch every screen for no user-visible gain.

---

## 2. Canonical `MediaAsset` identity

### 2.1 The problem with the current key

`MediaAssetEntity` uses `mediaStoreId: Long` as its primary key (`Db.kt:102-118`). That id is only unique within one MediaStore volume, does not exist for SAF documents at all, and is reassigned when a file is moved between volumes. `album_media`, `media_tag`, `favorite`, `edit_recipe` and `ocr_index` all reference `mediaId` (e.g. `Db.kt:38-39, 47-48, 41-42, 68-77, 79-85`), so a volume change silently orphans user metadata.

### 2.2 Proposed key

```kotlin
sealed interface AssetKey {
    data class MediaStore(val volume: String, val id: Long) : AssetKey   // "ms:external:1234"
    data class Saf(val treeHash: String, val documentId: String) : AssetKey // "saf:8f3a1c02:primary:Pictures%2Fcat.jpg"
    data class AppPrivate(val relativePath: String) : AssetKey            // "ap:captures/2026-11-27_140312.jpg"
}
```

- `volume` comes from `MediaStore.MediaColumns.VOLUME_NAME` (API 29+), falling back to `"external"`.
- `treeHash` = first 8 hex chars of SHA-256 of the persisted tree URI, so the key is stable across grant re-issues but never leaks the raw URI into logs.
- Serialised form is the string in the comments; `AssetKey.parse()` is the only parser and is unit-tested with round-trip + malformed input.

**Secondary fingerprint** for re-linking after rename/move/remount: `(displayName, sizeBytes, dateTakenMs)` and `sha256` when already computed. Fingerprint match is a *candidate*, never an automatic merge — the reconciler requires a single unambiguous candidate.

### 2.3 Availability, not deletion

`availability: PRESENT | MISSING | REVOKED | UNMOUNTED`.

- MediaStore miss → `MISSING` after **2 consecutive** scan passes and a 24 h grace window; never on the first miss.
- SAF `SecurityException` → `REVOKED` immediately (the file may still exist; the grant is gone). Metadata is **kept**.
- Volume not mounted → `UNMOUNTED` for every asset of that volume, in one statement.
- Room never decides existence; the UI renders a dimmed "Unavailable" tile and offers Relink/Remove.

### 2.4 Why additive, not a PK swap (decision D-2)

SQLite cannot alter a primary key. Replacing `media_asset`'s PK and repointing five referencing tables in one migration is the single most dangerous change in this proposal. Instead:

- **v4 (this phase):** add `assetKey TEXT`, `source TEXT`, `volume TEXT`, `availability TEXT`, `dateAddedMs INTEGER` to `media_asset`; backfill `assetKey = 'ms:external:' || mediaStoreId`; add a **unique index** on `assetKey`. `mediaStoreId` stays the PK. All existing queries keep working.
- **v5 (later, only if P06's tests demand it):** switch the PK and repoint references, with the v4 snapshot as rollback.

This keeps the migration non-destructive and testable in one step.

---

## 3. Room ERD and migration plan

### 3.1 What exists (v3, verified)

22 entities in `Db.kt:565-588`. Notable: **no foreign keys, no unique constraints, no type converters** anywhere (grep: 0 matches). Every reference table can orphan.

| Exists | Missing (pack requirement) |
|---|---|
| `album`, `album_media`, `favorite`, `tag`, `media_tag`, `edit_recipe`, `ocr_index` + `ocr_fts` (FTS4), `media_asset`, `folder_rule`, `scan_checkpoint`, `quarantine_item`, `operation`, `operation_item`, `excluded_folder`, `recent_view`, `search_history`, `saved_search`, `folder_grant`, `file_favorite`, `vault_item`, `identity_doc` | album folders, ratings, captions/alt text, scene labels, face clusters, quality scores, memories, creation projects, collection layout, playback positions, selection session, undo entries, per-asset hidden/archived flags |

### 3.2 Proposed v4 schema (one migration, additive only)

New tables:

```sql
-- album hierarchy (pack: album_folders)
CREATE TABLE album_folder (
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  name TEXT NOT NULL,
  parentId INTEGER,            -- null = root; depth checked in code, max 8
  position INTEGER NOT NULL DEFAULT 0,
  createdAt INTEGER NOT NULL
);
ALTER TABLE album ADD COLUMN folderId INTEGER;   -- null = "My Albums"

-- selection that outlives process death (>5k ids)
CREATE TABLE selection_session (
  assetKey TEXT NOT NULL,
  sessionId TEXT NOT NULL,
  selectedAt INTEGER NOT NULL,
  PRIMARY KEY(assetKey, sessionId)
);

-- undo for trash / rename / move / album changes
CREATE TABLE undo_entry (
  id TEXT PRIMARY KEY NOT NULL,
  operationId TEXT NOT NULL,
  inverseJson TEXT NOT NULL,
  createdAt INTEGER NOT NULL,
  expiresAt INTEGER NOT NULL
);

-- video resume
CREATE TABLE playback_position (
  assetKey TEXT PRIMARY KEY NOT NULL,
  positionMs INTEGER NOT NULL,
  updatedAt INTEGER NOT NULL
);

-- user-arranged Collections sections
CREATE TABLE collection_layout (
  sectionId TEXT PRIMARY KEY NOT NULL,
  position INTEGER NOT NULL,
  pinned INTEGER NOT NULL DEFAULT 0,
  hidden INTEGER NOT NULL DEFAULT 0,
  collapsed INTEGER NOT NULL DEFAULT 0
);
```

Columns added to existing tables:

| Table | New columns | Reason |
|---|---|---|
| `media_asset` | `assetKey`, `source`, `volume`, `availability`, `dateAddedMs`, `orientation`, `archived`, `hidden`, `rating`, `caption`, `altText` | one canonical row; pack's `assets_meta` is satisfied here rather than by a parallel table |
| `edit_recipe` | `stackJson`, `parentId`, `appliedAt` | versioned edit stack (current DAO only reads the newest, `Db.kt:409-410`) |
| `folder_rule` | `excludedFromAi`, `excludedFromMemories`, `excludedFromWidgets` | G15 leak prevention is per-feature, not one boolean |
| `operation_item` | `bytesDone`, `bytesTotal`, `conflictPolicy`, `attempts`, `checkpointJson` | resumable journal (§4) |

Indices added (all currently missing and each one is a full scan today):

```sql
CREATE UNIQUE INDEX index_media_asset_assetKey ON media_asset(assetKey);
CREATE INDEX index_media_asset_lastSeenAt ON media_asset(lastSeenAt);
CREATE INDEX index_media_asset_captured ON media_asset(capturedByFar, dateTakenMs);
CREATE INDEX index_media_asset_availability ON media_asset(availability);
CREATE INDEX index_album_media_position ON album_media(albumId, position);
CREATE UNIQUE INDEX index_tag_name ON tag(name COLLATE NOCASE);
CREATE INDEX index_recent_view_at ON recent_view(viewedAt);
CREATE INDEX index_search_history_at ON search_history(at);
CREATE INDEX index_operation_status ON operation(status, updatedAt);
```

`index_media_asset_captured` fixes `MediaAssetDao.capturedByFar()` (`Db.kt:467-468`), which currently full-scans and sorts on **every** table invalidation. `index_media_asset_lastSeenAt` fixes `deleteNotSeenSince` (`Db.kt:482-483`), which runs on **every** scan (`MediaScanner.kt:80`).

### 3.3 Search design

- OCR keeps **FTS4** (`ocr_fts`, external content on `ocr_index`, `Db.kt:87-91`). Room only supports FTS3/4; FTS5 would require raw SQL and loses Room's trigger generation. Not worth it.
- File names, tag names and captions stay **LIKE** today (`Db.kt:295-296, 421-422`) — leading-wildcard, unindexable. Proposal: add a second FTS4 table `meta_fts(displayName, tagNames, caption)` populated by triggers, so search never degrades to a full table scan.
- `OcrDao.searchIdsFts` currently falls back to LIKE on **any** exception and returns an unbounded id list (`Db.kt:424-432`). Add a `LIMIT` and log the fallback.

### 3.4 Migration safety (P04 contract)

1. Before any migration: copy `far.db`, `-wal`, `-shm` to `filesDir/db_snapshots/<epoch>/`, keep the newest 3. (Recovery already does this for corruption at `Db.kt:872-887`; migrations do not.)
2. `PRAGMA integrity_check` before and after.
3. Every version pair gets a `MigrationTestHelper` test (1→2 and 2→3 exist in `DatabaseMigrationAndRecoveryTest.kt:29-88` and `VaultCryptoAndMigrationTest.kt:123-209`; 3→4 is new).
4. On migration failure: fail loudly with a typed error, keep the snapshot, **never** delete. Today `openSafely` treats any `IllegalStateException` — including a missing migration — as corruption and routes to replace-and-salvage (`Db.kt:821-829`), which is how data disappears quietly.
5. `restoreSalvaged` inserts with `CONFLICT_IGNORE` per row and swallows failures (`Db.kt:964`), then writes only a one-line marker. Proposal: collect a `RecoveryReport(perTable recovered/lost)` and show the pack's calm summary screen.
6. Close the two data-loss hazards found in the audit:
   - If the snapshot copy fails, the code still deletes the live DB (`Db.kt:875-887` vs `:943-948`). Fix: delete only after a verified snapshot.
   - Non-salvaged tables are dropped by design: `ocr_index/ocr_fts`, `media_asset`, `operation/operation_item`, `search_history`, `recent_view`, `scan_checkpoint`, `quarantine_item` (`Db.kt:890-904`). Fix: salvage `ocr_index` and `operation` too; the rest are rebuildable and must be documented as such.

---

## 4. File-operation journal state machine

### 4.1 Today

`operation(id, type, status, done, total, errorCode, updatedAt)` (`Db.kt:151-160`) and `operation_item(id, operationId, sourceUri, destUri, status, errorCode, updatedAt)` (`Db.kt:163-175`) with free-text statuses `Queued|Running|Paused|Completed|Cancelled|Failed|Interrupted` and per-item `Pending|Done|Failed|Skipped`. There is no `bytesDone`, no checkpoint, no conflict policy, no resume rule.

### 4.2 Proposed

```
PENDING ──▶ COPYING ──▶ VERIFYING ──▶ COMMITTING ──▶ DONE
   │           │            │              │
   └───────────┴────────────┴──────────────┴──▶ FAILED
   └──────────────────────────────────────────▶ CANCELLED
```

Rules:

- The journal row is written **before** the first byte moves. Startup recovery scans `status IN (COPYING, VERIFYING, COMMITTING)` and:
  - `COPYING` → resume from `bytesDone` per item if the destination temp still exists and its length matches; otherwise restart that item.
  - `VERIFYING`/`COMMITTING` → verify the destination; if valid, finish the commit; if not, roll back and mark `FAILED`.
- Copy: stream in 256 KB–1 MB chunks into `MediaStore IS_PENDING=1` or a `.part` sibling, verify size (and SHA-256 for cross-volume moves), then finalise atomically, and **only then** delete the source (move).
- Free-space preflight: needed bytes + 10 % margin; `ENOSPC` maps to `FarError.StorageFull` (already handled in `farCatching`, `AppErrors.kt:66`).
- Conflict policy is per operation, not per item: `KEEP_BOTH | REPLACE | SKIP` + "apply to all".
- Undo: `undo_entry` rows written for trash/rename/move/album membership, with an expiry; the Undo toast runs the inverse through the same journal.
- Batch isolation: one item failing never aborts the batch; the result is `BatchResult(successes, failures[assetKey, FarError])`.

---

## 5. Background work map

| Work | Type | Unique name | Constraints | Backoff |
|---|---|---|---|---|
| MediaStore/SAF scan | one-time + periodic 12 h | `far_scan` | battery not low, storage not low | exponential 30 s |
| OCR index | one-time + periodic 12 h | `far_ocr` | battery not low, storage not low, thermal < MODERATE | exponential 60 s |
| Hash for duplicates | one-time | `far_hash` | charging (default on), battery not low | linear 60 s |
| Journaled copy/export | one-time | `far_op_<opId>` | none (user-initiated, expedited) | none |
| ML labelling (later, P22+) | one-time | `far_ml` | charging + idle | exponential 120 s |

Existing: `OcrWorkManager.kt` (`OcrIndexWorker : CoroutineWorker`) and `FileTransferWorker.kt`. Proposal: a single `WorkNames` object, `ExistingWorkPolicy.KEEP` for periodic work, `APPEND_OR_REPLACE` never (it silently drops user intent), progress via `setProgress(done, total)` so Activity shows real counts, and cancellation that stops after the current 25-item chunk.

---

## 6. Process boundaries and quarantine

**Proposal (v1): a non-isolated `:decode` process behind a bound service**, used for: untrusted image decode, archive extraction, ML inference and Media3 transcode.

- Not `isolatedProcess="true"`: an isolated process has no access to app-private storage or the content resolver, so it cannot read the file it is meant to decode without passing file descriptors. That is a larger change than Phase 1 allows.
- The service exposes `suspend fun decode(request): FarResult<…>` and is killed by the system independently of the UI process.
- Crash accounting: each `(assetKey, jobType)` pair gets an attempt counter. Two crashes on the same asset → insert into `quarantine_item` (`Db.kt:141-148`) and stop retrying that asset; the UI shows "This file can't be opened" with "Open with another app".
- If the process dies twice within one job *batch* (not per asset), the batch is aborted and marked `FAILED` rather than retried in a loop.

**Decision D-3:** this is deferred to Phase 4 (intelligence) in practice. Phase 1 keeps decode on `Dispatchers.IO` with `farCatching` + quarantine, because moving decode out of process changes every call site and cannot be tested without a device.

---

## 7. Manifest/permission diff and dependency decisions

### 7.1 Permission diff

**Proposed: none.** Phase 1 needs no new permission.

| Permission | Now | Phase 1 | Note |
|---|---|---|---|
| `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` / `READ_MEDIA_VISUAL_USER_SELECTED` | yes | keep | partial access must produce the "Showing what you've allowed" banner, not an empty library |
| `READ_EXTERNAL_STORAGE` (maxSdk 32) | yes | keep | needed on API 30–32 |
| `ACCESS_MEDIA_LOCATION` | yes | keep | request only when a screen shows a map |
| `CAMERA`, `RECORD_AUDIO` | yes | keep | runtime, just-in-time |
| `POST_NOTIFICATIONS` | yes | keep | WorkManager progress only |
| `MANAGE_EXTERNAL_STORAGE` | no | **stay no** | G8 |
| `INTERNET`, `ACCESS_NETWORK_STATE` | removed | **stay removed** | G2; enforce with a Gradle task (P02) and the existing `StrictOfflineManifestTest.kt` |

### 7.2 Dependency decisions

| Library | Now | Decision | Why / risk |
|---|---|---|---|
| Room | 2.7.0 | **keep** | stable, already exported schema v3 |
| CameraX | 1.5.0 | **keep** | stable; 1.6+ would need a compatibility pass |
| Compose BOM | 2024.09.00 | **keep for Phase 1** | it is older than Kotlin 2.2.10's compiler plugin; upgrading is a P02 task with screenshot-test verification, not a silent bump |
| Media3 | 1.5.1 | keep | transformer + exoplayer already wired |
| Coil | 2.7.0 | keep | video frames via `coil-video` |
| ML Kit text-recognition / barcode | 16.0.1 / 17.3.0 | keep, **verify bundled models** | these pull `com.google.android.datatransport`, already stripped in the manifest; must confirm no runtime model download |
| WorkManager | 2.10.0 | keep | |
| Paging 3 | 3.3.6 | keep | grid already paged |
| DataStore | 1.1.7 | keep | |
| Material Icons Extended | BOM | **replace in P12** | pack requires one original icon family (Phosphor/Lucide), not Material glyphs |
| *(add)* JankStats | — | add | frame governor (P53) |
| *(add)* LeakCanary | — | add, `debugImplementation` only | |
| *(add)* Roborazzi or Paparazzi | — | add, test only | screenshot tests without a device |
| *(add)* `androidx.benchmark.macro` + a `:benchmark` module | — | add | P05 budgets |
| *(add)* Detekt | — | add | enforces "no `Color(0x…)` outside theme", "no `!!`" |

No licence risk introduced: all of the above are Apache-2.0 or MIT. `docs/LICENSES.md` does not exist yet and must be created in P02.

---

## 8. Navigation graph plan

### 8.1 Today

One `NavHost` in `AppNav.kt` with routes: `onboarding` (`:1117`), `main` (`:1163`), `album/{albumId}` (`:2235`), `collection/{kind}` (`:2387`), `compare/{leftId}/{rightId}` (`:2609`), `duplicates` (`:2651`), `excluded` (`:2785`), `viewer` (`:2833`), `camera` (`:3205`), `editor/{uri}?mediaId={mediaId}` (`:3287`), `storage` (`:3501`), `settings` (`:3557`), `vault` (`:3675`). The bottom bar is `Photos | Camera | Files` (`GlassBottomBar.kt:63-72`); Photos has an inner segmented control `Library | Albums | Search` (`:74-78`).

### 8.2 Target

- Three top-level destinations, each with **its own** `NavHost` and saved back stack.
- Photos sub-sections: `Library · Collections · Albums · Search`. **Collections does not exist yet** — `collection/{kind}` (`AppNav.kt:2387`) is a filtered grid, not the sectioned Collections screen P16 describes. Add the section; do not rename the route.
- Overlays (`settings`, `storage`→Activity, `onboarding`) sit above the destination stack and return exactly where the user was.
- `IntentRouter` stays the single entry point for launcher, shortcut, widget and external intents; it maps to a route + parent stack. Existing deep link `farGallery://` (`AndroidManifest.xml:60-66`) and `farGallery://camera` must keep working.
- Back order: clear selection → close sheet/menu/search → pop screen → return to Photos Library → leave app. Viewer back returns to the same grid position.
- IDs only through navigation arguments; selections go through `selection_session`, never through a nav argument.

**Risk:** moving `AppNav.kt` into four files is mechanical but the file is 130 KB. It must be done as a pure move (one commit, no behaviour change) and validated by the four existing navigation tests (`NavigationContractTest.kt`, `NavigationContractVerificationTest.kt`, `GalleryScreenNavigationAuditTest.kt`, `GalleryScreenNavigationContractTest.kt`).

---

## 9. Test strategy

| Layer | Today | Proposal |
|---|---|---|
| Unit (JVM) | 17 files under `app/src/test` | keep; add `AssetKey` round-trip, conflict policy, journal state machine, capability rules |
| Robolectric | already used heavily (`AppStartupRobolectricTest`, `FaultInjectionRobolectricTest`, `SuiteUpgradesRobolectricTest`, `LargeLibraryScaleTest`) | keep as the default for DB/UI-state tests; it is the reason this project can be tested without a device |
| Migration | 1→2, 2→3 covered | add 3→4; assert row counts **per table**, not just "one album survived" |
| Instrumented | `ExampleInstrumentedTest` only (a stub) | add: capture → appears once in Photos and Files; SAF grant revoke; permission partial access; trash consent |
| Screenshot | none | add Roborazzi (offline, no emulator): Light/Dark × 200 % font × LTR/RTL for each migrated screen |
| Performance | none | add a `:benchmark` module; cold start, first grid frame, 100k scroll, viewer open/close |
| Fault injection | `FaultInjectionRobolectricTest` exists | extend with null cursor, hanging provider, revoked grant, ENOSPC, corrupt bytes, 500 random URIs |
| Monkey | none | run `adb shell monkey` on a real device for 10 min per phase |

**Baseline debt to clear before any new test counts:** the two failing `SharedInboxTest` cases (§0.2).

---

## 10. Performance budgets (per device class)

| Metric | Low (≤ 4 GB, API 30) | Mid (6–8 GB) | High (flagship) |
|---|---|---|---|
| Cold start to first frame | ≤ 1.8 s | ≤ 1.2 s | ≤ 0.8 s |
| First grid page painted | ≤ 1.2 s | ≤ 800 ms | ≤ 500 ms |
| Scroll jank (frames > 16 ms) | ≤ 8 % | ≤ 5 % | ≤ 2 % |
| Query p95 at 100k rows | ≤ 250 ms | ≤ 120 ms | ≤ 60 ms |
| Peak memory, viewer | ≤ 300 MB | ≤ 250 MB | ≤ 200 MB |
| Thumbnail cache | 15 % heap / 512 MB disk (shared) | same | same |
| Scan throughput | ≥ 150 items/s | ≥ 300 items/s | ≥ 600 items/s |
| Batch 5 000-item trash request | ≤ 3 s to first consent dialog | same | same |

These are targets to be measured in P05; none are claimed as achieved.

---

## 11. Phase 1 file-by-file change list

Ordered by the pack's dependency chain. **Nothing here is started before approval.**

### P02 — build reproducibility
- `gradlew`, **new** `gradlew.bat`, **new** `gradle/wrapper/gradle-wrapper.jar` (today `gradlew` is a 3-line shim to one machine's path)
- `gradle.properties`, `build.gradle.kts`, `app/build.gradle.kts` (add `verifyNoInternetPermission`, enable R8 for release)
- `gradle/libs.versions.toml` (centralise; remove unused `androidx-compose-material-icons-extended` only after grep proof)
- **new** `scripts/build_debug.sh`, `scripts/test_all.sh`, `scripts/lint.sh`
- **new** `docs/LICENSES.md`, **new** `README_DEV.md`
- `.gitignore` (stop ignoring `debug.keystore`, or generate it in a task — a clean checkout must build)

### P03 — reliability foundation
- `core/common/AppErrors.kt` (add `userTitle`/`userMessage`/`recoveryAction` per kind, keep `code` stable)
- **new** `core/common/SafeCalls.kt` (`safeContentResolver`, `safeSaf`, `safeRoom`, `safeDecode`, `batch`)
- **new** `core/common/AppScope.kt` (SupervisorJob + sanitised handler), **new** `core/common/CrashGuard.kt`
- `core/media/*`, `core/storage/*` (replace swallowed `try/catch` with `farCatching`; list each replaced site)
- **new** tests `ErrorMappingTest`, `BatchIsolationTest`

### P04 — database recovery
- `core/database/Db.kt` (snapshot-before-migration, integrity_check, RecoveryReport, salvage `ocr_index`/`operation`, stop treating missing migrations as corruption)
- `core/database/MetadataBackup.kt` (add checksum, validate URIs, report edit recipes)
- **new** `core/database/DbSnapshots.kt`, **new** `core/database/RecoveryReport.kt`
- tests: `DatabaseMigrationAndRecoveryTest.kt` (fix `:154`, which asserts OCR on the source DB instead of the target)

### P05 — large library fixtures
- **new** `app/src/test/java/com/example/fixtures/LibraryFixtures.kt` (deterministic seed; 100k rows)
- **new** `app/src/debug/java/com/example/tools/FixtureToolScreen.kt`
- `MediaRepository.kt` (remove caps, chunked cursors with checkpoints)
- **new** `docs/PERF_BUDGETS.md`

### P06 — asset identity
- `core/database/Db.kt` (v4 migration from §3.2)
- **new** `core/media/AssetKey.kt`, **new** `core/media/MediaAsset.kt`
- `core/media/MediaRepository.kt`, `core/media/MediaScanner.kt` (reconcile by `assetKey`, availability states)
- `core/media/MediaSources.kt` (source selection)
- tests: same-capture-once across Photos/Files, rename keeps tags, revoked grant keeps metadata, migration 3→4

### P07 — scanner
- `core/media/MediaScanner.kt`, **new** `core/media/ScanWorker.kt`, `core/media/MediaStoreReadWrapper.kt`
- `Db.kt` (`folder_rule` new flags)

### P08 — background OCR
- `core/media/OcrWorkManager.kt`, `core/media/OcrIndexer.kt` (chunk 25, real progress, thermal gate)

### P09 — incoming URIs
- `core/common/AppErrors.kt` — fix `canRead` (`:279`) and make rejection messages specific; keep `content`/`file` (no new schemes are needed; the pack's "accept only content/file" is about rejecting *unreadable* URIs, not about widening schemes)
- `app/IntentRouter.kt` (single `IncomingRequest` sealed type)
- tests: fuzz 500 URIs, `SEND_MULTIPLE` > 500, revoked grant mid-view

### P10 — journaled file operations
- `core/storage/DocumentOps.kt`, `core/storage/FileTransferWorker.kt`
- **new** `core/storage/OperationJournal.kt`, **new** `core/storage/UndoStore.kt`
- `Db.kt` (`operation_item` new columns)

### P60 — format support
- **new** `core/media/CapabilityRegistry.kt`, **new** `core/media/DecodePipeline.kt`
- **new** `docs/FORMAT_SUPPORT.md` (generated from the registry, not hand-written)

---

## 12. Alternatives considered and why they were rejected

| Alternative | Why not now |
|---|---|
| Rewrite the app from scratch | G1 forbids it; the current app already builds, has 55 files of working features and 17 test files. A rewrite would lose the strict-offline manifest work and the recovery path. |
| Split into Gradle modules immediately | High churn, no user-visible benefit, and it would break the existing Robolectric tests' classpath assumptions. Revisit after P52. |
| Swap `media_asset` PK to `assetKey` in one migration | SQLite cannot alter a PK; five tables reference `mediaId`. The additive `assetKey` column gives the same identity guarantees with a rollback path. |
| FTS5 instead of FTS4 | Room does not generate FTS5; hand-written SQL would lose the content-sync triggers that already work (`Db.kt:699-716`). |
| `isolatedProcess` for decode | No storage/content-resolver access; needs fd passing. Deferred (§6). |
| Replace Room with SQLDelight | No migration benefit, large rewrite, no offline risk reduction. |
| Use Material 3 icons as the icon family | Pack E8/E20 requires one original open family with outline/fill variants; Material glyphs are neither. |
| Keep the Vault because it exists | Owner note says remove it. This needs an explicit decision, not an assumption — see D-1. |
| Delete `AppNav.kt` and rebuild navigation | The four existing navigation tests encode current back-stack behaviour; a rebuild would lose that contract. Split the file, keep the routes. |

---

## 13. Open decisions (blocking)

| ID | Question | Default if the owner says nothing |
|---|---|---|
| **D-1** | The code ships a full Vault (AES-GCM blobs, PIN, identity documents, `vault` route, two tables). The 2026-11-04 note says *remove the private vault and its screen entirely*. Keep, remove, or keep-only-behind-a-setting? | **Keep the code but leave it OFF** (pack §A7), no new work, excluded from backup/search. Not deleted without explicit approval. |
| **D-2** | Approve the additive `assetKey` strategy (v4) instead of a PK swap. | No migration is written. |
| **D-3** | Approve deferring the `:decode` process boundary to Phase 4. | Decode stays in-process with quarantine. |
| **D-4** | Approve adding JankStats, LeakCanary, Roborazzi and a `:benchmark` module (all offline, test/debug only except JankStats). | Not added. |
| **D-5** | Compose BOM 2024.09.00 vs Kotlin 2.2.10: approve a BOM upgrade in P02 (needs screenshot-test verification) or pin as-is? | Pin as-is; only fix if it breaks. |
| **D-6** | `debug.keystore` is gitignored but required by the debug build. Commit the debug keystore, or generate it in a Gradle task? | Generate in a task (keeps the ignore rule honest). |

---

## 14. Approval requested

Per pack P01:

1. Approve this proposal, or name the changes you want.
2. Approve the treatment of the open defects: **#4 URI resolver** (real bug found: `canRead` at `AppErrors.kt:279`), **#6 video-trim temp**, **#7 rename fallback**; and the "already addressed" set **#2, #3, #5, #8**.
3. Name the next single implementation step. Recommendation: **P02** (build reproducibility), because the baseline cannot be reproduced on a clean checkout today — no wrapper, no SDK path, gitignored keystore, failing tests. Fixing that first makes every later "tests pass" claim verifiable.

No code, Gradle, manifest or dependency change will be made until this is approved and recorded in `FAR_DECISIONS.md`.

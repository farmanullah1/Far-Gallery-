# P01 — Architecture / Migration / Implementation Proposal

**Purpose:** a written, reviewable target plan for the next implementation work.  
**Status:** proposal gate. This document does **not** implement anything. It proposes what to do and why, and stops for approval before any code or build changes.

---

## 1. Authoritative sources and current posture

### 1.1 Authoritative sources for this proposal
- `Far_Gallery_Complete_Unified_Master_Prompt_Pack.md` is authoritative for this campaign for the `com.example` app.
- The agreed Phase 0 audit baseline is accepted as the current project truth.
- The actual `com.example` source code is authoritative for “what exists now.”
- `Far_Gallery_Complete_App.md` is **not authoritative for this app**. It is treated as a larger/different-variant document and must not drive `com.example` implementation. If any idea from it is used, it will be named explicitly here as a selectively carried note, not adopted silently.

### 1.2 Confirmed posture from the actual `com.example` repo
- **Strict-offline by default.**
  - `app/src/main/AndroidManifest.xml` removes `android.permission.INTERNET` and `android.permission.ACCESS_NETWORK_STATE` with `tools:node="remove"`.
  - Google DataTransport / telemetry transitive components are stripped with `tools:node="remove"` in the manifest (service and receiver entries).
  - Declared permissions are the local-media / camera / notification permissions only. There is no network permission and no `MANAGE_EXTERNAL_STORAGE` declared.
- **Single flavor, no `sync` variant.**
  - `app/build.gradle.kts` defines one product: `applicationId = "com.aistudio.fargallery.mnpxqv"`, `namespace = "com.example"`, `minSdk = 30`, and does not define a separate `sync` flavor in the actual repo.
- **Catalog-level dependency posture (for context, not as a change).**
  - `gradle/libs.versions.toml` shows `room = "2.7.0"` and `camera = "1.5.0"` as the direct entries read.

This proposal does not modify any of those files. It only records the posture as the basis for the recommended work.

---

## 2. Audit baseline, as agreed

The audit is the baseline. The master pack is authoritative for the `com.example` app. The current source code is authoritative for the actual behavior.

### 2.1 Already addressed in the code
Based on the agreed audit baseline and current source reads, these are treated as already addressed for the `com.example` app:

- **Defect #2 — large query caps.**
  - Current state: no single-query `LIMIT 2000` / `LIMIT 10000` truncation is present in the production query paths.
  - Evidence: media loading uses chunked cursor/offset+limit patterns and Paging 3-style loading (`MediaRepository.kt`, `MediaScanner.kt`).
- **Defect #3 — OCR only in foreground, 40 at a time.**
  - Current state: a background OCR path exists through WorkManager.
  - Evidence: `OcrWorkManager.kt` contains `OcrIndexWorker : CoroutineWorker`, and `OcrIndexer.kt` is the in-memory OCR helper used outside WorkManager.
- **Defect #5 — corruption handler calls `deleteDatabase("far.db")`.**
  - Current state: the codebase does not use `deleteDatabase` as a routine recovery path.
  - Evidence: `grep -rn "deleteDatabase" app/` returns 0 matches in source + test; recovery behavior is recorded in `Db.kt` (`RecoveringOpenHelper.recover()`).
- **Defect #8 — alpha dependencies.**
  - Current state: the direct catalog entries read are stable versions, not alpha releases.
  - Evidence: `gradle/libs.versions.toml` shows `room = "2.7.0"` and `camera = "1.5.0"`.

### 2.2 Caveat on “already addressed”
These conclusions are based on the agreed audit baseline and current source/catalog reads. They are not an exhaustive resolved-dependency tree audit and not an exhaustive UI call-site audit. That is intentional: enough to plan against, not overclaimed as exhaustive proof.

---

## 3. Open / partial Phase 1 defects

This proposal focuses on the defects that still need work. The master pack defect list is the reference; the actual source is the truth.

### 3.1 Defect #4 — URI resolver (main open item)

- **Current state:**
  - `IncomingMediaResolver` in `AppErrors.kt` only handles `content://` and `file://`.
  - Other schemes throw `IllegalArgumentException("unsupported uri scheme")`, which becomes `FarError.InvalidInput("bad argument")` through `farCatching`.
- **What the master pack defect says:**
  - The incoming URI resolver accepts only `content://` and `file://`.
- **Proposed direction:**
  - Extend `IncomingMediaResolver` so it can safely accept additional provider-backed content URIs that the app can validate, and reject unsafe or unsupported input with a clearer, localized error instead of a generic invalid-input fallback.
  - Keep the strict-offline posture. This is about provider-backed URI handling and metadata inspection; it does not add networking.
- **Files to inspect before any change:**
  - `AppErrors.kt` — `IncomingMediaResolver`, `safeParseMediaUri`, `inspect()`.
  - Callers of `IncomingMediaResolver.resolve(intent)` so new accepted patterns are handled consistently downstream.
- **Tests to plan for:**
  - Malformed or unsupported URIs.
  - Provider errors, null cursors, and unavailable streams.
  - Permission revocation and partial access for incoming shared items.
  - `ACTION_SEND_MULTIPLE` paths, `ClipData` paths, and `maxItems` behavior.
- **Done looks like:**
  - Incoming shared/picked media flows accept the provider-backed URIs the app actually needs.
  - Unsafe input is rejected cleanly.
  - The app never presents an unsupported scheme as “supported” when it cannot validate it.

### 3.2 Defect #6 — video trim temp handling (partial)

- **Current state:**
  - `VideoTrimmer.trim()` in `VideoPlayer.kt` writes the export to a temp file in `cacheDir`, preflights space with `checkCacheSpace()`, cancels cleanly, and deletes the temp file in `finally`.
  - There is no direct-to-MediaStore export path in the current code, and the low-space UX is limited to returning `FarError.StorageFull`.
- **Proposed direction:**
  - Harden the free-space preflight.
  - Investigate a direct-to-MediaStore output path where the Media3 transformer supports it, so a large intermediate temp file is not required.
  - If direct-to-MediaStore is not possible for a given transform, keep the temp-file approach but improve low-space handling and user-facing messaging.
  - Keep `finally` cleanup robust across cancel, failure, and process death, and test the temp path explicitly.
- **Files to inspect before any change:**
  - `VideoPlayer.kt` — `VideoTrimmer.trim()` and `checkCacheSpace()`.
  - The current Media3 transformer usage and the `tmp.absolutePath` export flow.
- **Tests to plan for:**
  - Low storage before trim.
  - Cancellation mid-export.
  - Process death / restart during trim.
  - Temp cleanup verification.

### 3.3 Defect #7 — rename fallback (partial)

- **Current state:**
  - `DocumentOps.rename()` in `DocumentOps.kt` does:
    - `DocumentsContract.renameDocument(...) ?: throw IOException("rename refused")`
  - That becomes `FarError.Io`. Success is **never** reported on null.
  - The code already rejects the case where the provider does not support rename (`canRename == false`).
- **Proposed direction:**
  - Keep the current rule that null is never reported as success.
  - Add provider-aware handling so the app can explain what happened when a particular provider refuses rename.
  - Add a copy-verify-delete fallback **only when it is safe** for the provider/volume and only after verifying the copy, with a clear user-facing choice if there is a real decision to make.
- **Files to inspect before any change:**
  - `DocumentOps.kt` — `rename()`, `canRename`, `transfer()`, `copyFile()`.
- **Tests to plan for:**
  - Provider returns null on rename.
  - Provider explicitly does not support rename.
  - Cross-volume rename where copy-verify-delete may be needed.

### 3.4 Defect #1 — Portrait / Slo-mo (current posture)

- **Current state:** the app omits Portrait and Slo-mo. There is no silent fallback path.
- **Proposal:** do **not** treat this as a current code defect to fix in the `com.example` app.
- **Reason:** the actual camera code does not present these modes and does not silently fall back to them. If these capture modes are ever added later, they must be gated behind real capability detection rather than silent fallback.

---

## 4. What this proposal is and is not

### 4.1 This proposal is
- A staged plan grounded in the actual `com.example` repo and the agreed audit baseline.
- Focused on the open/partial Phase 1 defects: #4, #6, #7.
- Explicit that #2, #3, #5, #8 are already addressed in the code.
- Explicit that `Far_Gallery_Complete_App.md` is non-authoritative for this app.

### 4.2 This proposal is not
- A code change.
- A Gradle, manifest, or dependency change.
- A scope expansion beyond the agreed brief.
- An adoption of any external idea unless it is named explicitly here as a selectively carried note.

---

## 5. Recommended next step

**Recommended next implementation step after approval:** implement **defect #4 URI resolver support** first.

**Why:**
- It is the main open item.
- It is foundational for incoming-media flows: share, pick, open-with, `ACTION_SEND_MULTIPLE`.
- It has a clear existing class to extend (`IncomingMediaResolver`) and an existing failure model (`FarResult` / `FarError` / `farCatching`).
- It is bounded: the right first step is to add safe provider-backed URI acceptance with clear rejection of unsafe input, not to broaden the app's permission posture.

**Alternate first step:** if you want build/dependency reproducibility work before feature changes, start with build reproducibility/dependency stabilization instead and hold #4 for later.

---

## 6. Approval requested

Please approve this proposal as-is, or request a named change.

**P01 exit criteria:**
- The proposal is approved, or approved with a named change.
- The open/partial defect approach for #4, #6, #7 is agreed.
- The treatment of #1 and the already-addressed items (#2, #3, #5, #8) is agreed.
- The next single implementation step is named.

No code, Gradle, manifest, or dependency changes will be made until this proposal is approved.

# Architecture Decision: Shared Media Library Foundations

**Date:** 2026-02-21  
**Status:** Approved for incremental implementation  
**Scope:** Phase 1, sub-step 1.3 — foundations only  
**Related prompts:** P06 (canonical asset identity), P07 (incremental scanner), P10 (operation journal)

---

## 1. Purpose

This document records the architecture decision for the shared media library that Photos, Camera, and Files will all use. It establishes **sources of truth**, a **unified in-app media item model**, a **reader/normalization layer**, and **ownership and privacy rules**. It is intentionally foundational: it does not implement full indexing, full UI, or Operation/Undo Centre destinations.

This decision exists so later increments can add features against a stable contract instead of re-deriving the model each time.

---

## 2. Guiding rules

- **Offline-first, on-device only.** No network, no server sync, no remote model downloads. Everything in this library must work in airplane mode.
- **Sources of truth are separate from app-owned metadata.**
- **One physical file should not be duplicated merely to appear in another section.**
- **Vault and other private content must stay isolated from normal shared-library views.**
- **Failures return typed errors, never crash the UI.**
- **Keep the smallest viable abstraction for this increment.** Do not pre-build everything.

---

## 3. Sources of truth

| Source | Authority covers | In-app role |
| --- | --- | --- |
| **MediaStore** | Shared photos and videos on the device | Primary source for the shared library timeline and most media items |
| **Storage Access Framework (SAF)** | Arbitrary documents and user-selected folders/providers | Source for files and imported media the user opens or exposes through Files |
| **Room (`far.db`)** | Far-owned metadata only | Stores albums, tags, ratings, captions, edit recipes, OCR text, scores, projects, folder grants, scan checkpoints, operation journal, and similar app-owned data. Room does **not** decide whether a physical file exists. |

Notes:

- MediaStore is authoritative for shared photos/videos. SAF is authoritative for arbitrary documents and user-selected file trees.
- Room is **not** a file existence oracle. If a MediaStore/SAF-backed item's file is gone, the app marks the reference unavailable; it does not fabricate a file.
- Vault items are intentionally isolated. They are not part of the shared media library normal views.

---

## 4. Unified in-app media item model

### 4.1 Goal

Provide one in-app representation that Photos, Camera, and Files can all reason about, while still respecting that different sources carry different metadata.

The unified model should be able to represent, at minimum:

- **stable identity**
- **source** (MediaStore, SAF, or app-private)
- ** URI**
- **name**
- **MIME type**
- **size**
- **dates** (captured, added, modified as applicable)
- **dimensions** (for images/video where available)
- **duration** (for video where available)
- **orientation** (where available)
- **flags** (for example favourite, archived, hidden, locked, captured-by-Far-Gallery, trashed as applicable)
- **an optional content hash**

### 4.2 Current baseline

The existing codebase already contains the main building blocks:

- `MediaRow` is the current in-app row model for MediaStore-backed media.
- `MediaAssetEntity` is the canonical Room reconciliation record for media the app tracks, including MediaStore identity, URI, size, dimensions, duration, capture provenance, trashed state, and optional SHA-256.
- `MediaRepository` is the reader layer for MediaStore queries, including paging.
- `MediaScanner` reconciles MediaStore rows into the Room canonical record incrementally.
- `IncomingMediaResolver` normalizes incoming URIs from other apps into a small internal result type.

What is **not yet unified** in code:

- There is not currently a single interface or supertype that Photos, Camera, and Files all use to refer to a media item.
- SAF documents are handled in Files more as file browser entries than as first-class media items sharing the same representation.
- Vault items are intentionally separate and should remain separate for normal library views.

### 4.3 Practical decision for this increment

For this increment, the unified model is introduced as a **small contract layer**, not as a forced replacement for every existing class.

 Concretely:

1. Define a **source indicator** so the app can distinguish MediaStore media from SAF documents and app-private items where needed.
2. Define a **minimal shared media item interface** that can later be implemented by:
   - MediaStore-backed items
   - SAF-backed items
   - app-private items such as captures and vault exports when they need to appear in shared views
3. Define a **source reader interface** that can query and normalize items from a source into the shared shape.

This keeps the existing MediaRow/MediaAssetEntity/MediaRepository code intact while giving later increments a stable place to converge.

---

## 5. Reader and normalization layer

### 5.1 Responsibility

The reader/normalization layer is responsible for:

- Reading from the appropriate source of truth
- Normalizing source-specific data into the shared in-app shape
- Returning typed results and errors
- Keeping paging and large-list behavior at the reader boundary
- Not pretending a missing file exists

### 5.2 Boundaries

- **MediaStore path:** used for shared photos/videos. Can rely on MediaStore URIs and the existing repository/paging machinery where appropriate.
- **SAF path:** used for documents and user-selected folders. Must handle provider variability, granted access, read-only providers, null metadata, and missing or unsupported fields gracefully.
- **App-private path:** used for items the app itself created and stores internally, such as captures or exports that have not been exposed elsewhere.

### 5.3 Normalization rules

- Normalize what is available; do not invent values.
- If a field is unavailable from the source, leave it absent rather than fabricating a value.
- If a file cannot be reached, represent the item as unavailable rather than deleting or hiding the metadata unless the user has taken a specific action.
- Keep the smallest possible common denominator in the shared interface; extend with source-specific details only where a feature needs them.

---

## 6. Ownership rules

### 6.1 What is local-only

- Far-owned metadata in Room is local-only by default.
- Vault items and other privacy-isolated content are local-only and excluded from shared-library normal views.
- App-private captures/exports remain local until the user explicitly shares or exports them.

### 6.2 Sync status

This app does **not** sync to any server. “Sync” language in this codebase refers to local reconciliation between sources of truth (for example, MediaStore rows reconciled into the Room canonical record), not network synchronization.

For this app:

- Local reconciliation is normal and expected.
- There is no cloud or remote sync pathway in the strict-offline build.
- Any future sync or LAN feature would be a separate approved variant, not part of this library’s default contract.

---

## 7. Privacy boundary

### 7.1 Explicit exclusions

The shared media library must not leak:

- Vault content
- Hidden content
- Excluded folders
- App-private items that have not been explicitly exposed

These must stay out of normal timelines, global search, OCR, AI features, memories, widgets, notifications, thumbnails, recent-task previews, and diagnostics unless the user has taken a specific authenticated action.

### 7.2 On-device processing only

All reading, indexing, OCR, hashing, and analysis in this library happens on-device. No network permission is added for these features.

---

## 8. What this increment delivers

This increment delivers:

1. A short Markdown architecture decision record (this file).
2. A minimal shared media item abstraction and source-reader interface shape in code.
3. Confirmation that the existing shell still builds and still has no `INTERNET` permission.

This increment does **not** deliver:

- Full indexing
- Full UI
- Operation Centre
- Undo Centre
- Full SAF-as-media-items implementation
- Full migration of every existing screen onto the new abstraction

---

## 9. Out of scope for this increment

- Replacing all existing MediaRow/MediaAssetEntity usage across the app
- Implementing all unified flags on every screen
- Building the Operation Centre or Undo Centre UI
- Implementing full indexing or full File-provider media item support end to end
- Changing the strict-offline posture
- Changing existing working behavior except where required to add the new foundational contract

---

## 10. Acceptance criteria for this increment

- [x] This decision record exists and is readable.
- [x] A minimal shared media item abstraction and source-reader interface exist in code.
- [x] The source indicator concept is represented.
- [x] The abstraction is small and does not force a full reimplementation today.
- [ ] `./gradlew assembleDebug` succeeds.
- [ ] APK is produced at `app/build/outputs/apk/debug/app-debug.apk`.
- [ ] No `android.permission.INTERNET` is introduced.
- [ ] No regression in the existing 3-tab shell.

---

## 11. Open questions

1. Should SAF documents be treated as first-class media items in the shared model, or should Files remain partly separate and only normalize into the shared model when a feature needs it?  
   **Current assumption for this increment:** normalize into the shared model only where needed; do not force a full unification today.

2. Should the shared model be an interface implemented by source-specific types, or a single data class with optional fields?  
   **Current assumption for this increment:** an interface plus source-specific implementation is the more honest fit, because different sources carry different metadata and failure modes.

3. Should Vault items ever implement the shared media item interface?  
   **Current assumption:** only in very controlled cases and never for normal shared-library views; Vault isolation remains a higher-priority rule.

---

## 12. Follow-up increments

After this foundation is in place, later increments can:

- Expand the shared model fields as features need them
- Add SAF-backed item normalization where Files or import features require it
- Reconcile the shared model more fully with `MediaAssetEntity`/Room where that is the right place
- Add an operation journal and Activity/Undo Centre on top of this contract
- Add background indexing and OCR scheduling with this model as input/output

---

*End of decision record.*

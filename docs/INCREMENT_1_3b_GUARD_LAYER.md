# Guard layer for the shared media library — validation and re-initialization guards

**Date:** 2026-02-21  
**Increment:** Phase 1, sub-step 1.3 — second increment / right-hand support layer  
**Depends on:** `docs/ARCHITECTURE_DECISION_SHARED_MEDIA_LIBRARY.md` and `app/src/main/java/com/example/shared/local/MediaSources.kt` from the prior increment  
**Status:** Implemented as the smallest additive support shape

---

## 1. Purpose

This increment adds a small right-hand support/foundation layer for the shared media library contract from the prior increment.

It does **not** add full indexing, UI, or Operation/Undo Centre destinations. It adds only the smallest concrete guards that make the contract safer for later use:

1. A **validation/read wrapper rule** for MediaStore-style reads: reads from a `MediaSourceReader` keep using `FarResult/FarError`, and we make concrete a simple *“source initialization is still in progress”* gate so the Recent/library destination does not accidentally treat “still loading” as “empty store.”
2. A **feature-level re-init guard** based on `android.content.pm.RoughlySameIntent`: the Recent/library destination can skip re-initializing the recent media source on rotation or back-stack re-creation when the incoming `Intent` has not materially changed, while a genuinely new intent (for example, a just-taken photo handed back into the gallery) is allowed to re-initialize.

---

## 2. Scope

In scope for this increment:

- A small Markdown record describing the rule and assumptions.
- A small concrete Kotlin shape for the validation/read-wrapper concern and the initialization-guard concern.
- A small concrete Kotlin shape for the `RoughlySameIntent` re-init guard.
- A brief note on how the `MediaSourceReader` from the prior increment is consumed by that guard.

Out of scope for this increment:

- Full indexing
- Full UI changes
- Operation Centre / Undo Centre destinations
- Forcing every SAF document into the shared model
- Rewriting existing GalleryViewModel or Camera flow code

---

## 3. Validation / read-wrapper rule

### 3.1 Rule

Reads sourced from a `MediaSourceReader` must:

- Return results through `FarResult`.
- Return errors through `FarError`.
- Not throw across the read boundary by default.
- Not pretend a missing file exists.

### 3.2 Concrete shape added this increment

We add a small helper that wraps a `MediaSourceReader.readPage()` call:

- If the call completes successfully, forward the page.
- If the call fails with a known typed cause, map it through `mediaSourceError()`.
- If the call fails with an unknown cause, return a generic `FarError.Unknown()`.
- If the call is cancelled, return `FarError.Cancelled()` rather than propagating cancellation as a surprise exception across the boundary.

This makes the read path explicit and testable later, without forcing any particular paging UI.

---

## 4. Source initialization guard

### 4.1 Problem

A destination that shows “recent” or “library” media can start in three states:

- **initializing**: the source has not produced usable data yet
- **empty**: the source finished initializing and there is genuinely no content
- **content**: the source produced content

If the UI treats “initializing” as “empty,” the user sees the wrong empty state while a first page is still being read.

### 4.2 Rule

The shell must distinguish:

- **initializing** from **empty**
- **empty** from **error**
- **error** from **content**

A minimal in-memory guard is added for this increment:

- A tiny `SourceInitialization` state wrapper that records whether a source is still initializing, and whether the source finished with content, empty, or error.
- A status helper that a destination can use to decide whether to show a loading/initializing placeholder, an empty/recent-empty placeholder, a retry affordance, or normal content.

This is intentionally small and in-memory: it does not yet model background indexing jobs, resuming across process death, or durable scan checkpoints.

---

## 5. Feature-level re-initialization guard (`RoughlySameIntent`)

### 5.1 Problem

Rotation, process death, and back-stack re-creation can cause a destination to re-initialize the recent/library source even when nothing meaningful changed. If re-initialization is expensive or side-effecting, that is wasteful and can create noticeable jank or duplicate work.

### 5.2 Rule

When a destination is re-created with an `Intent` that is *roughly the same* as the previous one, the destination may skip full re-initialization of the recent media source and reuse whatever initialization state it already has.

When the `Intent` has materially changed, the destination should treat this as a new request and re-initialize as needed.

Concretely:

- `/files` destinations that open a folder/query from an `Intent` can keep a recent intent key and compare with `RoughlySameIntent`.
- The Photo library’s “Recent” path can use the same idea for the gallery route/intent key, so rotation does not force a full re-init.
- A genuinely new intent, such as a photo just taken and handed back into the gallery, is treated as a new request and re-initializes.

### 5.3 Assumptions

- The Recent/library destination’s source init is idempotent enough to skip on same-intent rotation.
- We do not force every SAF document into the shared model here.
- The existing `InputEventReceiver`-style handoff (`IntentRouter`, `IncomingRoute`) remains the real intent pipeline. This increment only adds the smallest reuse guard for the Recent/library path plus the Camera feed path.
- `roughlySameIntent` is best-effort and may be conservative: better to recompute a genuinely new intent than to skip one that should re-initialize.

---

## 6. How the reader interface is consumed by the guard

This increment documents the minimal consumption shape, not a full implementation:

- The Recent/library guard accepts a `MediaSourceReader` from the prior increment.
- It calls `readPage()` through the validation/read-wrapper rule above.
- On success, it transitions `SourceInitialization` to `finished(content = true/false)`.
- On error, it transitions to `finished(error)`.
- While initializing, the destination shows a placeholder instead of “empty.”

This keeps the reader interface from the prior increment as the only query abstraction needed for later Recent/library use.

---

## 7. Files created or changed in this increment

- **New:** `docs/INCREMENT_1_3b_GUARD_LAYER.md`
- **New:** `app/src/main/java/com/example/shared/local/MediaStoreReadWrapper.kt`
- **New:** `app/src/main/java/com/example/shared/local/SourceInitialization.kt`
- **New:** `app/src/main/java/com/example/shared/local/RoughlySameIntentGuard.kt`

These are additive; no existing shell file is modified in this increment.

---

## 8. Out of scope

- Replacing existing GalleryViewModel initialization
- Changing CameraActivity / CameraScreen behavior end to end
- Implementing background indexing, OCR scheduling, or Operation Centre
- Adding network or any new manifest permission

---

*End of increment record.*

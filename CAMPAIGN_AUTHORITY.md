# Campaign Authority — Far Gallery Implementation Stance

**Purpose:** lock down the authoritative spec stance for this implementation campaign so later passes do not silently drift between different documents or between documentation and the actual app.

**Status:** directive. Read this before interpreting any feature list, defect list, or camera-mode requirement.

---

## 1. Canonical app for this campaign

- **Canonical package:** `com.example`
- **Application ID:** `com.aistudio.fargallery.mnpxqv`
- **App label:** Far Gallery
- This is the **ground-truth app** for this campaign. Implementation decisions must match the actual `com.example` source, not a different package or a larger/desired variant.

## 2. Definitive camera modes (current code)

The current definitive in-app camera mode definitions are:

- **Enum:** `app/src/main/java/com/example/feature/camera/CameraScreen.kt`
- **Active mode list used by the camera manager:** `app/src/main/java/com/example/feature/camera/CameraManager.kt`

Those define **exactly four camera modes**:

1. `Video`
2. `Photo`
3. `Square`
4. `Scan`

No other camera modes are present in the `com.example` camera code.

## 3. SloMo and Portrait — actual status

- `SloMo` and `Portrait` **do not exist** in the actual `com.example` camera code.
- The app has **no hidden SloMo→normal-video fallback** and **no hidden Portrait→normal-photo fallback**.
- There is no silent “SloMo binds standard 30fps video” or “Portrait binds a normal photo” path in the current camera manager.

## 4. APP_OVERVIEW.md section 5.1 is outdated for camera modes

`APP_OVERVIEW.md` section 5.1 (“Camera Mode Stubs”) describes a SloMo/Portrait stubbed state that **does not match the actual on-device app**.

- That section should **not** be treated as current truth for camera modes.
- It should not be used to claim that SloMo/Portrait exist as fallbacks in the `com.example` app.

## 5. Far_Gallery_Complete_App.md is non-authoritative for this app

`Far_Gallery_Complete_App.md` is a **larger-variant spec written for a different package (`com.far.gallery`)**.

- It includes `SloMo` and `Portrait` in its `CameraMode` enum.
- It describes a different feature posture than the shipped `com.example` app.
- It **must not drive `com.example` implementation**.

If a larger camera variant with SloMo/Portrait is ever wanted, that is a separate product decision and a capability-gated implementation effort, not a “defect fix” for the current app.

## 6. Master pack defect #1 — reinterpretation for this app

Master pack defect #1 is commonly worded as:

> Portrait and Slo-mo are stubs that fall back to normal capture.

For the **actual `com.example` app**, that wording does **not** describe a currently existing problem. Reinterpreted for this app:

> **No misleading Portrait/SloMo fallbacks exist. Confirm and keep it that way. If those modes are ever added, gate them behind real capability detection rather than silent fallback.**

In other words: the current app is already honest about its camera modes. The risk to avoid is introducing misleading mode support later.

---

## 7. What this note does and does not do

**It establishes:**

- Which package/app is authoritative.
- What the current camera modes actually are.
- That SloMo/Portrait are not present and not silently stubbed.
- That one source (`APP_OVERVIEW.md §5.1`) is outdated in this area.
- That one spec file (`Far_Gallery_Complete_App.md`) is non-authoritative for this app.
- How to read master pack defect #1 for this app.

**It does not:**

- Add new feature requirements.
- Rename the app or change the package.
- Change any runtime behavior.
- Decide whether SloMo/Portrait should ever be added.

---

*End of authority note. If a later pass treats camera modes, APP_OVERVIEW.md §5.1, or Far_Gallery_Complete_App.md as authoritative for the `com.example` app, that pass is out of alignment with this directive.*

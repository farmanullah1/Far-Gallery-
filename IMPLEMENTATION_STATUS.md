# Far Gallery — Final Integration, Release Gate & Handover (P52)

## 1. Release Gate Summary

| Area | Status | Notes & Evidence Basis |
|---|---|---|
| **Strict Offline (`G2`)** | **PASS (Verified)** | `INTERNET`, `ACCESS_NETWORK_STATE`, and Google DataTransport telemetry (`TransportBackendDiscovery`, `JobInfoSchedulerService`, `AlarmManagerSchedulerBroadcastReceiver`) stripped via `tools:node="remove"`; verified in merged `debug`/`release` manifests and `StrictOfflineManifestTest` (3/3). |
| **Photos & Timeline** | **DONE (JVM/Robolectric)** | MediaStore + Paging 3 timeline (`Years / Months / Days / All`), Photos/Videos/Favorites filters, 2–6 pinch grid, date-group multi-select, Archive/Excluded folders, System Trash/Restore (`MediaStore.createTrashRequest`), Pull-to-refresh, shared-element navigation. Physical device MediaStore stress UNTESTED here. |
| **Viewer, Compare & Video** | **DONE (JVM/Robolectric)** | Swipe pager, 6x pinch/double-tap zoom, swipe-down dismiss, Quick EXIF pill + offline GPS coordinates (`getLatLong`, copy, external `geo:` chooser), RGB Histogram, Slideshow (4s interval), Tags CRUD, bundled ML Kit OCR scan, Side-by-side / Split Compare, Media3 `ExoPlayer` speed + audio tracks + `FrameGrabber` + `VideoTrimmer` + PiP. Live hardware codecs UNTESTED here. |
| **Search, Creations & Duplicates** | **DONE (JVM/Robolectric)** | Offline `SearchQueryParser` (quotes, scope, date, size, `#tags`, years), Room FTS4 OCR index (`OcrIndexWorker`), Saved Searches, Creation Studio 2–9 JPEG Collages (`CollageEngine`), single/multi-page PDF Photo Book & Contact Sheet (`PdfExporter`), exact SHA-256 `DuplicateFinder`, offline timestamp/resolution `BurstDetector` (Best Frame suggestion), and Laplacian-variance `BlurDetector` review. |
| **Non-Destructive Photo Editor** | **DONE (JVM/Robolectric)** | Brightness/Contrast/Saturation/Warmth/Vignette, 8 presets with thumbnails, freeform drag-handle crop + aspect presets (`1:1`, `4:3`, `3:2`, `16:9`, `Passport 35:45`, `Wallpaper 9:16`), `-45°..+45°` auto-fit straighten, Rotate/Flip, Hold-to-Compare, Undo/Redo, Copy/Paste `EditRecipe`, `Save Copy` (`EDIT_*.jpg`), and `Before/After` Split Copy (`SPLIT_*.jpg`), with EXIF location stripped by default. |
| **Camera (`FarCameraManager`)** | **DONE (JVM/Robolectric)** | CameraX `LifecycleCameraController` + Camera2 characteristics probe (`probeCamera2Details`: HW level, RAW, max FPS, OIS, focal length), Photo/Square/Video/Scan modes, Scan-to-PDF, Timer (0/3/5/10s), Burst, Flash, EV slider + `0.0 EV` reset, tap-to-focus + AE/AF lock, Grid, Horizon Level, bundled ML Kit QR scanner, volume-key shutter, `ON_STOP` recording safety. Live Camera2 preview/capture UNTESTED here. |
| **Files & Opt-In DocumentsProvider** | **DONE (Robolectric)** | Adaptive phone & `>= 840dp` desktop layouts, persistable SAF grants, Starred & Pinned folders, Copy/Move/Paste with size check + `Collision.Rename`, Safe ZIP inspect/create/extract (`SafeZip` path-traversal & zip-bomb guards), `PdfRenderer`, Text/Code viewer with Find-in-file & UTF-8 save, Read-only Binary Hex inspector, SHA-256 verification, and opt-in read-only `FarDocumentsProvider` (`${applicationId}.documents`). Live multi-app SAF picker UI UNTESTED here. |
| **Storage Cleanup & Diagnostics** | **DONE (JVM/Robolectric)** | `StorageAnalyzer` with Category Breakdown / Folder Treemap / Tidy Suggestions tabs, Large Files thresholds (`>1MB..>100MB`), projected reclaimable bytes, honest third-party cache note, Trash manager, Crash-loop Safe Mode, Activity log, and sanitized `LocalDiagnostics` preview + local `.txt` export to `Downloads/Far Gallery`. |
| **Private Vault & Identity Docs** | **DONE (Robolectric)** | Android Keystore AES-256-GCM encryption (`VaultCrypto`), `BiometricPrompt` lock, encrypted media import/preview, encrypted Identity Docs (`IdentityDocEntity`), strictly isolated from Gallery timeline, Search, OCR, Share sheets, Widgets, `MetadataBackup`, and `FarDocumentsProvider`. Hardware TEE/StrongBox keystore behavior UNTESTED here. |
| **System Intents, Widgets, Settings & RTL** | **DONE (Robolectric)** | `VIEW`, `EDIT`, `SEND`/`SEND_MULTIPLE`, `PICK`/`GET_CONTENT`/`PICK_IMAGES`, `IMAGE_CAPTURE`/`VIDEO_CAPTURE`, `ClipData` + read grants, `RESULT_CANCELED` guards, `ThemeTileService`, `LibraryStatsWidgetProvider`, Settings search, Activity/Operations log, Crash-loop Safe Mode, English & Urdu RTL strings (`values-ur`). |

## 2. Build & Verification Instructions

- **Package & SDK Targets (`app/build.gradle.kts`):**
  - `namespace = "com.example"`
  - `applicationId = "com.aistudio.fargallery.mnpxqv"`
  - `minSdk = 30` (Android 11), `targetSdk = 36`, `compileSdk = 36.1`
- **Gradle Commands (run from workspace root without `./gradlew`):**
  - Debug build & unit/Robolectric tests (80 tests across 11 suites):
    ```bash
    gradle :app:testDebugUnitTest --console=plain
    ```
  - Release Kotlin compile & Vital Lint check:
    ```bash
    gradle :app:compileReleaseKotlin :app:lintVitalRelease --console=plain
    ```
  - Debug/Release APK assembly (release signing expects `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD` env vars):
    ```bash
    gradle :app:assembleDebug :app:assembleRelease
    ```

## 3. Permission Rationale (Merged Manifest Audit)

- `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_VISUAL_USER_SELECTED` (API 33+/34+), `READ_EXTERNAL_STORAGE` (`maxSdkVersion="32"`): Core local gallery/picker browsing, including Android 14+ Selected Photos Access.
- `ACCESS_MEDIA_LOCATION`: Read EXIF GPS coordinates locally for the Viewer details sheet (stripped automatically on clean Share / Editor Copy unless opted out).
- `CAMERA`, `RECORD_AUDIO`: Camera photo/video capture and optional microphone audio during video recording.
- `POST_NOTIFICATIONS`: Transfer progress / background worker notifications when granted.
- `INTERNET` & `ACCESS_NETWORK_STATE`: **Explicitly removed** (`tools:node="remove"`). Zero network/socket calls exist.
- `android.permission.MANAGE_DOCUMENTS` (provider guard on `FarDocumentsProvider`): Standard Android OS guard ensuring only the system DocumentsUI / granted URIs query `${applicationId}.documents`.

## 4. Dependency & License Inventory (`gradle/libs.versions.toml`)

- **AndroidX & Jetpack (Apache 2.0):** Core KTX `1.18.0`, AppCompat `1.7.0`, Activity Compose `1.10.1`, Compose BOM `2024.09.00` (Material 3, UI, Icons), Navigation Compose `2.8.9`, Lifecycle `2.8.7`, Biometric `1.1.0`, CameraX `1.5.0`, Media3 `1.5.1` (ExoPlayer, UI, Transformer), Room `2.7.0`, WorkManager `2.10.0`, Paging `3.3.6`, DataStore `1.1.7`, DocumentFile `1.1.0`, SplashScreen `1.2.0`.
- **Kotlin & Coroutines (Apache 2.0):** Kotlin `2.2.10`, KSP `2.3.5`, `kotlinx-coroutines` `1.10.2`.
- **Coil (Apache 2.0):** `coil-compose` & `coil-video` `2.7.0`.
- **On-Device ML Kit (Google Play Services / ML Kit SDK Terms, bundled offline models with DataTransport stripped):** `com.google.mlkit:text-recognition:16.0.1`, `com.google.mlkit:barcode-scanning:17.3.0`.
- **Testing (EPL 1.0 / MIT / Apache 2.0):** JUnit `4.13.2`, Robolectric `4.16.1`, AndroidX Test Core `1.6.1`, Room Testing `2.7.0`, Coroutines Test `1.10.2`.

## 5. Database Migrations & Backup Safety

- **Room Schema (`FarDatabase` v3):** Explicit non-destructive migrations `MIGRATION_1_2` (media index cache, folder rules, scan checkpoints, quarantine, operation items, FTS4 OCR) and `MIGRATION_2_3` (`vault_item`, `identity_doc`), plus `RecoveringOpenHelperFactory` which archives unreadable DB snapshots to `filesDir/db_corrupt/` and salvages user metadata read-only rather than wiping user data.
- **Metadata Backup (`MetadataBackup`):** Exports/imports Albums, Album refs, Favorites, Tags, Excluded folders, Folder rules, Saved searches, and Edit recipes as local JSON via SAF (`CreateDocument`/`OpenDocument`). **Private Vault and Identity Documents are intentionally excluded.**
- **`android:allowBackup="false"`:** Prevents automatic cloud/ADB backup leakage of Vault keys or private files.

## 6. Known Limitations & Unsupported-Device Matrix

- **Environment Verification Scope:** All 76 JVM/Robolectric tests, `compileDebugKotlin`, `compileReleaseKotlin`, and `lintVitalRelease` passed in this build environment. **Physical-device/emulator execution is not available here**, so hardware Camera2 streams, Media3 hardware video decoders, Keystore BiometricPrompt hardware TEE prompts, and cross-app live SAF UI handoffs require verification on physical hardware before Play store release.
- **OEM & OS Restrictions:** Android does not provide a universal system gallery role; lock-screen camera launch, double-press power button, and dedicated camera keys are OS/OEM-controlled. `FarGallery` does not claim OEM computational-photography parity (night-mode multi-frame fusion, proprietary lens switching).
- **API Floor:** Requires **Android 11 (API 30)+** (`minSdk = 30`). Devices on Android 10 and below are unsupported. API 30 uses opaque-glass fallbacks where `RenderEffect` blur (API 31+) is unavailable.

## 7. Deferred Roadmap Features

- Local-network protocols (SMB, WebDAV, Wi-Fi Direct, Bluetooth sockets) — intentionally excluded to preserve the Strict Offline (`NO INTERNET`) privacy contract.
- Unbundled downloadable AI models (depth/relight/erase/reframe NN packs) and multi-track video timeline composition.
- Full-matrix hardware macrobenchmarking across 100k-item MediaStore devices (P51 physical soak testing).

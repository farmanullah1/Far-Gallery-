# Far Gallery

**Implementation stance:** see `CAMPAIGN_AUTHORITY.md` for the authoritative source/decision record for this campaign, including the canonical app package and the current definitive camera modes.

Offline-first Android Gallery, Camera, and File Manager built with Kotlin, Jetpack Compose, Material 3, Room, CameraX, Media3, and bundled on-device ML Kit.

## Highlights
- **Strict Offline & Private**: Zero `INTERNET` or `ACCESS_NETWORK_STATE` permission in the merged manifest and DataTransport telemetry components stripped. Everything runs on-device, including ML Kit OCR text search and barcode/QR scanning.
- **Three Entry Points**: Launches as **Far Gallery** (default launcher + media viewer/picker), **Far Camera** (camera icon + capture intents), and **Far Files** (files icon + directory intents).
- **Adaptive & Localized**: Supports compact phones, foldable/tablet rails (`>= 600dp`), desktop-class multi-pane file browsing (`>= 840dp`), Light / Dark / AMOLED themes, and English + Urdu (RTL).

## Local Build & Verification Commands
- Debug APK: `gradle :app:assembleDebug`
- Local JVM & Robolectric unit tests: `gradle :app:testDebugUnitTest --console=plain`
- Release Kotlin compile & vital lint (without upload keystore): `gradle :app:compileReleaseKotlin :app:lintVitalRelease --console=plain`
- Full release package (requires `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`): `gradle :app:assembleRelease`

## Project Structure
- `com.example.core.*`: Typed error boundary (`FarError`/`farCatching`), incoming media MIME sniffer (`IncomingMediaResolver`), Room database (`FarDatabase`), MediaStore Paging 3 repository, SAF `DocumentOps` + `SafeZip`, and on-device `OcrIndexer`.
- `com.example.ui.*`: `FarTheme`, pure-Compose translucent `Glass.kt` with opaque fallback, `GlassBottomBar` / `GlassNavRail`, `Feedback.kt`, and `FarLogo`.
- `com.example.feature.*`: Onboarding, Gallery, Viewer, Video, Editor, Albums & Search, Duplicates, Camera, Files, and Settings.
- `com.example.app.*`: Intent routing (`IntentRouter`, `DefaultApps`), Compose navigation (`AppNav`), and detail helpers.

# Far Gallery

Offline-first Android Gallery, Camera, and File Manager built with Kotlin, Jetpack Compose, Material 3, Room, CameraX, Media3, and Haze.

## Highlights
- **Offline & Private**: Zero `INTERNET` permission and no cloud accounts. Everything runs on-device, including ML Kit OCR text search and barcode/QR scanning.
- **No Hidden Vault**: No vault exists in the app, database schema, or manifest. Folder exclusions filter the main timeline without moving or hiding files at the OS level.
- **Three Entry Points**: Launches as **Far Gallery** (default launcher + media viewer/picker), **Far Camera** (camera icon + capture intents), and **Far Files** (files icon + directory intents).
- **Adaptive & Localized**: Supports compact phones, foldable/tablet rails (`>= 600dp`), desktop-class multi-pane file browsing (`>= 840dp`), Light / Dark / AMOLED themes, and English + Urdu (RTL).

## Project Structure
- `com.example.core.*`: Typed error boundary (`FarError`/`farCatching`), incoming media MIME sniffer (`IncomingMediaResolver`), Room database (`FarDatabase`), MediaStore Paging 3 repository, SAF `DocumentOps` + `SafeZip`, and on-device `OcrIndexer`.
- `com.example.ui.*`: `FarTheme`, `Glass.kt` (Haze blur + opaque fallback), `GlassBottomBar` / `GlassNavRail`, `Feedback.kt`, and `FarLogo`.
- `com.example.feature.*`: Onboarding, Gallery, Viewer, Video, Editor, Albums & Search, Duplicates, Camera, Files, and Settings.
- `com.example.app.*`: Intent routing (`IntentRouter`, `DefaultApps`), Compose navigation (`AppNav`), and detail helpers.

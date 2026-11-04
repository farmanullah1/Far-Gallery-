# Far Gallery — Complete Product, UX, and Android Implementation Specification

**Document type:** Product requirements document + implementation brief for Google AI Studio / Android Studio  
**Product:** Far Gallery  
**Platform:** Native Android application  
**Minimum Android version:** Android 11 (API 30)  
**Recommended target SDK:** Android 16 (API 36) for a new Google Play submission as of 4 November 2026  
**Primary implementation:** Kotlin, Jetpack Compose, Material 3, Room, MediaStore, Storage Access Framework, CameraX, AndroidX Media3  
**Primary operating model:** Local-first, offline, private, no account required  
**Optional extension:** LibrePhotos server integration in a separate network-enabled build variant or explicitly enabled feature module

---

## 0. Instructions to the implementation model

Treat this document as the authoritative product and engineering specification for Far Gallery. Build a real, maintainable Android application—not a static design mock-up, clickable prototype, web page wrapped in an APK, or collection of screens containing fake data.

### Mandatory implementation rules

1. **Implement real functionality.** Every visible control must perform its stated action. Do not create buttons that only animate, show a toast, navigate to an empty screen, or pretend an operation succeeded.
2. **Do not use dummy content in the production experience.** Do not insert fabricated photos, albums, storage values, duplicate results, OCR matches, camera details, sync statuses, or sample transactions. Empty states must be genuine empty states. If a demo dataset is needed for automated tests, isolate it in test fixtures and never show it as the user's real library.
3. **Do not silently substitute a weaker implementation.** If a feature cannot be implemented in the current phase or is restricted by Android, show an honest explanation and a disabled/unavailable state with the reason. Never label a feature “working” when it is only a placeholder.
4. **Preserve user files.** Never overwrite or permanently delete source media without an explicit user action and the applicable Android confirmation. Default editing behavior must save a copy.
5. **Respect Android permissions and storage boundaries.** Do not assume unrestricted access to internal storage, SD cards, app-private folders, or all media. Use MediaStore and the Storage Access Framework (SAF) correctly.
6. **Use capability detection.** Do not assume that a file extension proves a codec is supported, or that every phone camera has HDR, night mode, RAW, manual focus, or every advertised capability.
7. **Keep all core gallery functions local.** Browsing, album organisation, viewer, editor, favorites, local search, file browsing within granted access, trash management, and local backup must not require an internet connection.
8. **Do not upload private media or metadata without explicit consent.** OCR, face grouping, semantic search, duplicate analysis, and location-based features must run on-device unless the user separately opts into a clearly described server feature.
9. **Do not claim universal format support.** Implement the format strategy in Section 7. File support depends on container, codec, codec profile, Android version, device decoder, and optional libraries. Preserve unsupported originals and explain the limitation.
10. **Build in phases.** Complete and test the MVP before attempting all advanced modules. Do not generate nonfunctional stubs for later phases and call the project complete.
11. **Compile and test the actual project.** Deliver a complete Android Studio project with Gradle configuration, source code, manifest, resources, Room migrations, tests, and build instructions. Resolve compilation errors. Do not stop after producing screenshots or pseudocode.
12. **Explain any omitted capability.** Include a `IMPLEMENTATION_STATUS.md` that lists each requirement as implemented, partially implemented, platform-limited, or not yet implemented, with evidence and next steps.

### Definition of “complete”

A feature is complete only when its UI, persistence, permission flow, error states, lifecycle behavior, tests, accessibility behavior, and user-facing feedback are implemented. A screen that looks correct but does not perform the underlying operation is not complete.

---

# 1. Product vision and goals

Far Gallery is a premium-feeling, privacy-first Android media and file management application. It combines the everyday experience of a photo gallery with selected file-management, camera, privacy, and offline smart-search capabilities.

The application should feel fast, calm, predictable, and trustworthy. It must be easy for a first-time user to understand while still offering deep controls to experienced users.

## 1.1 Primary goals

- Browse local photos and videos in a fast chronological timeline.
- Browse albums and folders, including media on removable SD storage when access is available.
- Open photos, animations, motion photos, and videos in a polished full-screen viewer.
- Organise media with favorites, albums, tags, filters, sorting, pinning, and hiding/excluding folders.
- Search filenames and metadata, with optional offline OCR and other on-device smart features.
- Edit photos without destroying originals by default.
- Play supported local videos with reliable controls and recovery from playback errors.
- Manage files and folders within Android's actual storage permissions and SAF grants.
- Offer a properly encrypted private vault, not merely a hidden folder.
- Provide safe deletion, trash retention, restore, backup, and optional LibrePhotos synchronization.
- Add a separate camera module that saves captures into public media collections and makes new captures immediately available in the gallery.
- Remain useful with no internet connection and without a user account.

## 1.2 Product principles

**Local-first:** The device is the primary source of truth. Server sync is optional and never required for the core gallery.

**Privacy by default:** Request permissions only when needed. Explain why each permission is required. Do not scan unrelated app-private directories.

**Non-destructive:** Editing, duplicate cleanup, metadata changes, and file operations must be reversible where reasonably possible.

**Honest capability reporting:** Show whether access is full, partial, denied, or limited to individually selected files. Show whether a format is natively supported, supported by an optional decoder, or preview-only.

**Resilient:** Corrupt, missing, unsupported, revoked, or removed media must not crash the app or block the rest of the library.

**Accessible:** Support TalkBack, large text, contrast, motion preferences, RTL layouts, and meaningful touch targets.

## 1.3 What “all media formats Android and iPhone support” means

Far Gallery should have broad support for media commonly found on Android and iPhone, including common iPhone HEIF/HEIC images, HEVC videos, ProRAW DNG files, Live Photo assets when exported as accessible files, and ProRes MOV files when the specific codec/profile can be decoded.

However, an Android application cannot guarantee that it can decode every file that any iPhone, Android device, camera, or third-party app can create. Some formats rely on optional hardware codec profiles, proprietary codecs, manufacturer-specific RAW data, licensed decoders, or platform APIs unavailable to Android. A `.mov` or `.mkv` extension alone does not identify the video codec inside it.

Therefore, the product requirement is:

- Support a broad, documented set of common image, video, animation, RAW, and audio formats.
- Use the Android platform decoder first where appropriate, with optional, legally usable decoder libraries for additional coverage.
- Detect the actual file type and codec rather than relying on the extension alone.
- Use embedded thumbnails or previews when full decoding is unavailable.
- Preserve original files exactly unless the user explicitly requests conversion or replacement.
- Display a clear “Preview unavailable” or “Codec not supported on this device” message instead of crashing.
- Keep the original file accessible through the file manager or “Open with” flow.

---

# 2. Platform, SDK, and target-device requirements

## 2.1 Android compatibility

- `minSdk`: 30 (Android 11).
- `compileSdk`: current stable Android SDK at build time.
- `targetSdk`: 36 or newer when stable and compatible with project dependencies. API 36 is the recommended target for a new Google Play mobile submission as of 4 November 2026.
- Use compatibility branches for APIs introduced after API 30.
- Test on API 30, 32, 33, 34, 35, and 36.
- Use 64-bit ARM support. Build native libraries for `arm64-v8a`; add other ABIs only when dependencies and distribution requirements justify them.
- Do not hardcode behavior based on a single manufacturer's Android skin.

The target SDK and minimum SDK serve different purposes. Supporting Android 11 does not mean targeting API 30. The app should target a recent SDK and adapt behavior at runtime for older supported devices.

## 2.2 Google Play target level

For a new Google Play mobile app submission from 31 August 2026, Google Play requires target API 36 or higher. Do not leave the release build at API 34 simply because the original device runs Android 14. Re-check Play policy and Android SDK release status before each release.

## 2.3 Reference device profile

Use the supplied target-device information as a performance and visual test profile:

- 6.78-inch AMOLED display, FHD+ resolution, 120 Hz refresh rate.
- Approximately 393 ppi; approximately 550-nit typical brightness and up to approximately 1800-nit peak brightness as described by the device specification.
- MediaTek Helio G100, octa-core CPU (2× Cortex-A76 up to 2.2 GHz and 6× Cortex-A55 up to 2.0 GHz), Mali-G57 MC2 GPU.
- 8 GB physical LPDDR4X RAM; virtual RAM expansion must not be treated as physical memory.
- 128 GB or 256 GB UFS 2.2 storage and a dedicated microSD slot, stated capacity up to 2 TB.
- Android 14 / XOS 14.5, API 34, 64-bit, Linux kernel 5.10.209-android12.
- 50 MP main camera, 2 MP depth sensor, 8 MP front camera.
- 5000 mAh battery, 33 W wired charging, USB-C 2.0 with OTG, Wi-Fi 5, Bluetooth 5.4, NFC, stereo speakers, 3.5 mm audio jack, in-display optical fingerprint sensor, accelerometer, gyroscope, proximity sensor, and compass.

Use this as a reference profile only. Detect actual display refresh rate, memory class, camera capabilities, storage volumes, and biometric availability at runtime.

## 2.4 Performance requirements for this device class

- The first meaningful gallery content should appear quickly using cached database metadata and available thumbnails; do not wait for a full-library scan before rendering the UI.
- Maintain smooth scrolling on a large library and avoid decoding original full-resolution images inside grid cells.
- Use thumbnail-sized requests for grids and decode full-resolution images only in the viewer/editor.
- Use bounded memory and disk caches with configurable limits.
- Avoid large bitmap allocations and full-resolution video frame extraction on the main thread.
- Use paging for large result sets.
- Avoid unnecessary wake locks, continuous background polling, and repeated full-library scans.
- Pause or defer expensive OCR, face analysis, hashing, and similarity work when the device is hot, low on battery, in power-saving mode, or on metered connectivity.
- Never use virtual RAM expansion as a reason to allocate excessive memory.
- Keep animations respectful of the 120 Hz display but do not force 120 Hz or bypass the user's system refresh-rate settings.

---

# 3. Application architecture and modules

Use a modular native Android architecture. Jetpack Compose should provide the UI; it should not contain storage, decoding, encryption, or sync business logic.

## 3.1 Recommended modules

1. **`:app`** — application entry point, navigation, app lifecycle, dependency injection, build variants.
2. **`:core:common`** — shared result types, dispatchers, logging, utilities, error models.
3. **`:core:database`** — Room database, entities, DAOs, migrations.
4. **`:core:media`** — media repository, MediaStore queries, SAF URI adapters, metadata extraction.
5. **`:core:thumbnails`** — image/video thumbnail generation, cache policies, decoding capabilities.
6. **`:core:permissions`** — API-aware permission state and permission rationale UI.
7. **`:core:storage`** — file operations, volume monitoring, SAF, archive operations.
8. **`:core:security`** — vault encryption, Keystore integration, biometric authentication, secure preferences.
9. **`:feature:gallery`** — timeline, date navigation, grid, filters.
10. **`:feature:albums`** — albums, folders, pinned albums, album covers.
11. **`:feature:viewer`** — photo viewer, animation viewer, motion photos, details sheet.
12. **`:feature:video`** — Media3 playback, trimming, frame extraction, subtitles, PiP.
13. **`:feature:editor`** — non-destructive photo editing and export.
14. **`:feature:files`** — file browser, storage dashboard, file operations.
15. **`:feature:search`** — search, filters, OCR index, optional on-device intelligence.
16. **`:feature:duplicates`** — exact and similar media detection.
17. **`:feature:vault`** — encrypted private vault UI and workflows.
18. **`:feature:camera`** — CameraX capture UI and capture pipeline.
19. **`:feature:backup`** — local backup, verification, restore.
20. **`:feature:sync`** — optional LibrePhotos adapter and sync engine.
21. **`:feature:maps`** — optional offline-first geotag map display.
22. **`:feature:widgets`** — app widget and shortcuts.

If Google AI Studio cannot create all Gradle modules in one pass, use clear package boundaries within a single app module first. Do not flatten the entire project into one giant Activity or one giant Compose file.

## 3.2 Architecture pattern

Use a layered architecture:

- **Presentation:** Compose screens, UI state, navigation, accessibility semantics.
- **Domain:** use cases, business rules, repository interfaces, operation policies.
- **Data:** Room, MediaStore, SAF, file streams, Media3, CameraX, encryption, optional network adapters.

Use unidirectional data flow. UI state should be observable and lifecycle-aware. Long-running actions should be represented by explicit states such as `Queued`, `Running`, `Paused`, `Completed`, `Cancelled`, and `Failed`.

Use Kotlin coroutines and Flow for asynchronous data. Use WorkManager for deferrable persistent background work. Use foreground services only when a user-visible operation genuinely requires them and Android policy permits it.

## 3.3 Technology choices

- Kotlin and current stable Android Gradle Plugin / Kotlin toolchain.
- Jetpack Compose and Material 3.
- Navigation Compose or a maintained navigation solution.
- Room for app-owned metadata and collections.
- DataStore for settings and small preferences.
- MediaStore for shared media discovery and media operations.
- Storage Access Framework for user-selected files and folders, especially external/removable storage.
- AndroidX Photo Picker for private, one-off selection where full-library indexing is not necessary.
- CameraX for camera preview, capture, and recording; Camera2 interop only when necessary.
- AndroidX Media3 / ExoPlayer for supported video playback.
- `ImageDecoder` and supported platform image APIs for supported image formats; add maintained image-decoding libraries as optional, legally compatible modules.
- Android Keystore and BiometricPrompt for vault key protection.
- WorkManager for resilient background indexing and backup tasks.
- Paging for large libraries.
- Dependency injection with a maintainable solution such as Hilt or Koin.
- Unit, instrumentation, UI, and performance tests.

Pin compatible dependency versions in a version catalog. Do not invent library versions. Check current stable releases and licenses before adding libraries.

---

# 4. Navigation and visual design system

## 4.1 Primary navigation

Use a clean bottom navigation bar on phones with these primary destinations:

- **Gallery** — the main timeline and filter shortcuts.
- **Albums** — user albums, folders, and smart collections.
- **Search** — filename, metadata, and optional OCR search.
- **Files** — file-system browser and storage tools.

Place Camera as a clearly visible action (for example, a center floating action button or a camera shortcut in the Gallery top bar) without obscuring media content. The vault should be reachable from a deliberate privacy entry in the app menu/settings, not exposed as a public album.

On tablets and foldables, adapt to a navigation rail or navigation drawer and use wider multi-pane layouts. Do not simply stretch a phone layout.

## 4.2 Visual style

Create a distinctive but restrained Material 3 design. The interface should look premium and native, not like a generic AI-generated dashboard.

- Use large, clear image surfaces and minimal chrome while browsing.
- Use consistent 8 dp-based spacing, typography hierarchy, icon weight, shape system, and elevation.
- Default to a neutral surface palette that lets photos remain the visual focus.
- Support Material You dynamic color where available and a curated fixed palette for users who prefer it.
- Include Light, Dark, and true AMOLED Black themes.
- Use system bars and edge-to-edge layout correctly.
- Respect display cutouts, gesture navigation, status/navigation bars, and keyboard insets.
- Support portrait and landscape layouts.
- Use restrained transitions; avoid unnecessary parallax, heavy blur, or effects that make scrolling slower.
- Provide optional haptic feedback for important actions.
- Add a reduced-motion setting and respect system animation preferences.

## 4.3 Theme controls

Allow the user to choose:

- System, Light, Dark, or AMOLED Black.
- Dynamic system colors or one of several curated accent palettes.
- Grid column count and thumbnail shape/spacing.
- Viewer background: black, dim, or theme surface.
- Compact or comfortable interface density.
- App icon theme where supported by Android launcher functionality.

Theme changes must update all screens consistently and persist across restarts.

## 4.4 Loading and empty states

- Use low-cost skeleton placeholders only while real content is loading.
- Empty gallery: explain that Far Gallery has not found visible media or does not currently have permission; offer the correct next action.
- Empty album: show “No items in this album” and useful actions.
- Empty search: show the query, offer spelling/filter suggestions, and distinguish “no match” from “not indexed.”
- Partial permissions: show a small privacy/access indicator and a button to review access.
- Unsupported preview: show the filename, file type, and available actions.
- Error states must be actionable and must never be disguised as empty content.

## 4.5 Accessibility and localisation

- Use semantic labels for icons and images.
- TalkBack must announce selection state, item counts, playback state, and operation progress.
- Keep touch targets comfortably sized (aim for at least 48 dp where practical).
- Support system font scaling without clipping critical controls.
- Avoid color-only status communication.
- Provide sufficient text and icon contrast.
- Support English and Urdu initially if translation quality is verified.
- Use proper right-to-left layout for Urdu, including navigation order, text alignment, icons that need mirroring, date formatting, and mixed-language filenames.
- Do not reverse image content or filesystem paths in RTL mode.

---

# 5. Onboarding, permission state, and first launch

## 5.1 First-launch flow

1. Show a short introduction explaining that Far Gallery works locally and does not require an account.
2. Explain the core functions: gallery, albums, files, viewer, and optional camera.
3. Ask for media access only when the user enters a feature that needs it.
4. Prefer the Android Photo Picker for one-off selections and imports where full library indexing is not required.
5. If the user chooses to make Far Gallery their full gallery experience, explain why full image/video access enables a complete timeline and duplicate scanning.
6. Ask for camera permission only when the user opens the camera module.
7. Ask for location metadata access only when the user enables map browsing or asks to read unredacted location metadata.
8. Ask for SAF folder access only when the user browses or manages a folder/volume that requires it.
9. Explain the private vault and app lock separately. Do not force users to configure a vault to browse normal photos.
10. Provide a clear “Continue with limited access” route wherever the feature can function partially.

## 5.2 Permission state indicator

Show a privacy/access indicator in the app's library header or settings:

- **Full access** — app can see media types for which full access was granted.
- **Selected access** — app can see only selected photos/videos.
- **Folder access** — app can see only SAF-granted folders or files.
- **No access** — media permission was denied or revoked.
- **Unavailable volume** — previously indexed removable storage is disconnected.

The wording must not suggest that the app can see everything when it cannot.

## 5.3 Recheck permissions

Re-check permissions on app resume and before sensitive operations. Users can change access in Android Settings at any time. If access is revoked, keep safe app-owned metadata such as user-created album names if appropriate, but mark inaccessible media as unavailable and avoid trying to open stale URIs indefinitely.

---

# 6. Gallery browsing and album management

## 6.1 Gallery home / timeline

The Gallery screen is the primary landing page after onboarding.

### Layout

- Top app bar with Far Gallery identity, search shortcut, selection/action access, and overflow menu.
- Optional compact storage/access indicator.
- Horizontally scrollable quick filters.
- Main media grid grouped by date.
- Date headings such as Today, Yesterday, This Week, This Month, and year groups.
- Optional fast-scrolling date rail or month/year navigator.
- Smooth grid layout with adjustable columns.
- A clear selection toolbar when one or more items are selected.

### Timeline behavior

- Default sort: newest capture date first, with a documented fallback to MediaStore date-added or modified time when capture time is unavailable.
- Group by Today, Yesterday, This Week, This Month, and Year.
- Allow the user to switch between date taken, date added, and date modified.
- Provide a scrollable month/year indicator to jump through long libraries.
- Use stable item keys and Paging for large libraries.
- Load thumbnails progressively and prioritize visible items.
- Do not recompose the entire grid for one metadata update.
- Display a small video duration badge and clear animation/motion indicators where relevant.
- Do not display private-vault items in this timeline.

### Grid customization

- 2, 3, 4, 5, and adaptive column choices.
- Adjustable spacing and thumbnail size.
- Optional square crop or fit preview.
- Pinch gesture to smoothly increase/decrease grid columns.
- Persist the user's grid preference.
- Keep a predictable minimum touch target and allow the user to return to the default grid.

### Quick filters

Provide chips or a filter sheet for:

- All media.
- Photos only.
- Videos only.
- Recently added.
- Recently viewed.
- Camera photos.
- Screenshots.
- Screen recordings.
- Downloads.
- Large files.
- GIFs and animations.
- Documents and scanned images.
- Favorites.
- Hidden/excluded albums (management screen only).

Only show categories that actually have matching content. Do not create empty fake categories to fill space.

## 6.2 Albums and folders

Provide distinct views for logical albums and physical folders.

**Albums** are app-managed collections that can contain items from different physical folders. **Folders** represent actual storage locations or SAF-granted directories. Do not confuse the two.

Features:

- Auto-generated collections: Camera, Screenshots, Screen recordings, Downloads, WhatsApp/media folders where visible, GIFs, large videos, and other detected categories.
- User-created albums with name and optional description.
- Add/remove items without moving the original file unless the user explicitly selects a physical move operation.
- Pin albums to the top.
- Select a custom album cover from the album's own media.
- Rename and delete app-managed albums. Deleting an album must not delete its media unless the user explicitly chooses a separate delete-media action.
- Hide/exclude folders from the main timeline without moving or deleting files.
- Recently viewed and recently added collections.
- Sort albums by name, item count, newest item, or custom order.
- Search albums and folders.
- Show a clear badge for removable storage or inaccessible folders.

When a physical folder is moved, renamed, or removed outside the app, reconcile it during media indexing. Do not assume its URI or path remains valid.

## 6.3 Favorites and tags

- One-tap favorite toggle in grid and viewer.
- Favorites collection.
- Custom favorite groups/collections.
- User tags with multiple tags per media item.
- Bulk tag assignment/removal.
- Filter by one or more tags.
- Tags and favorites must persist in Room and survive app updates and local backup/restore.
- Never write a tag into the original media file unless the user explicitly requests metadata export.

## 6.4 Sorting and filtering

Support ascending and descending order by:

- Capture date.
- Date added.
- Date modified.
- File name.
- File size.
- Duration for videos.
- Resolution/dimensions where metadata is available.

Filters should support photos, videos, format, folder/album, favorites, date range, file size range, orientation, dimensions, and optional location presence.

---

# 7. Media formats, codecs, and compatibility policy

This section is mandatory. The implementation must not use a simplistic extension-to-decoder mapping.

## 7.1 Image and animation formats

| Format / family | Expected handling | Important limitations |
|---|---|---|
| JPEG / JPG | Full decode, thumbnail, EXIF read, edit/export | Preserve orientation and color metadata where possible. |
| PNG | Full decode, alpha transparency, edit/export | Preserve alpha where supported. |
| GIF | Animated playback and static thumbnail | Large/complex GIFs must be decoded with frame and memory limits. |
| WebP | Static and animated decode where decoder supports it | Test lossy, lossless, transparency, and animation separately. |
| HEIF / HEIC | Decode when Android/device decoder supports the profile | HEVC and bit-depth profile support may vary by device. |
| AVIF | Decode supported baseline profiles; test higher bit depth and HDR capability | Do not assume every AVIF variant is supported on every device. |
| BMP | Basic decode | Some uncommon bitmap variants may not work. |
| TIFF | Optional decoder module | Multi-page TIFF, high bit depth, and uncommon compression methods may require specialist libraries. |
| DNG | RAW support where the platform or RAW library supports the specific DNG | Use embedded preview when raw rendering is unavailable. |
| Other RAW (CR2, CR3, NEF, ARW, RAF, ORF, RW2, PEF, SRW, etc.) | Best-effort via tested decoder/library; otherwise extract embedded JPEG preview when possible | Proprietary variants and camera generations may not be supported. Never claim universal RAW support. |
| SVG | Sanitize and rasterize with a maintained SVG parser | Never execute embedded scripts, external references, or unsafe SVG content. Treat SVG as vector artwork, not a camera photo. |
| JPEG Ultra HDR / HDR stills | Preserve source and render HDR when Android display pipeline supports it | Otherwise show a valid SDR fallback while preserving the original. |
| Motion Photo / Motion JPEG / compatible HEIC motion assets | Show still image and offer motion playback if embedded video can be extracted/played | Motion metadata and container layout differ; test real samples. |
| Apple ProRAW | Treat as DNG/RAW where compatible | iPhone-specific metadata and rendering characteristics may not be fully reproduced. |
| Apple Live Photo export | Detect paired still + MOV assets when accessible and group them logically | Android cannot recreate Apple's native Photos database behavior. Preserve both source assets. |

## 7.2 Video containers and codecs

Support common containers where the contained codecs can be decoded:

- MP4 / M4V.
- MOV / QuickTime.
- MKV / Matroska.
- WebM.
- 3GP.
- MPEG-TS and other common camera/recording containers where supported by the player stack.

Common codecs to test include H.264/AVC, H.265/HEVC, VP8, VP9, AV1, MPEG-4 Part 2, and device-supported HDR profiles such as HDR10/HDR10+ or Dolby Vision where the platform supports the exact profile. Audio tracks may contain AAC, MP3, Opus, FLAC, AC-3/E-AC-3, or other codecs with varying platform support.

Do not infer that a `.mov` file is playable just because MOV is listed, or that an `.mkv` file will play regardless of its internal video/audio streams. Use Media3/MediaCodec capability checks and show the actual failure reason when known.

## 7.3 Audio and subtitle tracks inside video

- Select among available audio tracks when the file contains multiple tracks.
- Support embedded subtitle tracks when Media3 supports them.
- Allow external subtitle files through SAF, including common SRT, WebVTT, TTML, and SSA/ASS formats when supported by the player.
- Display an unavailable-track message instead of failing playback.
- Do not automatically download subtitles.

## 7.4 Audio-only formats in file browsing

Far Gallery is not intended to become a full music player, but it should identify audio files correctly in the file browser and offer “Open with” through Android. For supported audio preview, common formats may include MP3, AAC/M4A, WAV, FLAC, OGG/Opus, and MIDI where an appropriate playback engine exists. Do not claim complete audio library management unless a dedicated audio module is added.

## 7.5 Format detection and fallback sequence

For each file:

1. Read the MIME type from the content provider where available.
2. Inspect the file signature/header where permitted and safe.
3. Determine container and likely codec/profile.
4. Check platform decoder capability.
5. Attempt a bounded thumbnail or preview decode.
6. For RAW, attempt embedded JPEG preview extraction before expensive full RAW rendering.
7. If unsupported, retain the original URI and show metadata plus available actions.
8. Log a safe diagnostic code without including private file contents or location metadata.

Never rename an unsupported file to another extension as a workaround. Never convert or delete the source automatically.

## 7.6 Optional decoder modules and licensing

- Prefer Android platform decoders for standard formats.
- Use external libraries only after checking license, ABI requirements, security maintenance, binary size, and Google Play policy.
- Keep optional decoders modular so the app can be built without a proprietary or incompatible codec.
- Clearly disclose any optional decoder package that introduces licensing obligations or additional binary size.
- Do not bundle FFmpeg or other codec stacks blindly. Check the exact build configuration, patents, LGPL/GPL obligations, and distribution implications with qualified review.

## 7.7 Importing iPhone media

Far Gallery is an Android app. It cannot access another iPhone's private Photos library directly or invoke iOS-only frameworks. Users can transfer/export iPhone media to Android by cable, SD card, shared folder, or another transfer method. Far Gallery should then recognise the resulting files as broadly as possible.

Support these common iPhone-originated file scenarios:

- HEIC/HEIF still photos.
- HEVC/H.265 videos in MOV/MP4 containers when the decoder supports the profile.
- ProRAW DNG files where compatible.
- Live Photo still + video components when exported as accessible files.
- ProRes MOV videos when their exact codec/profile is supported by the device or optional decoder.
- JPEG exports and common video transcodes.

Do not claim support for Apple's entire Photos library database, iCloud-only originals, iOS edit history, or native Live Photo behavior.

---

# 8. Full-screen photo, animation, and motion viewer

## 8.1 Viewer layout

- Full-bleed image area with minimal controls.
- Top overlay: back, favorite, more actions.
- Bottom overlay: filename/date summary and optional thumbnail filmstrip.
- Controls auto-hide after a short delay but remain accessible to TalkBack.
- Viewer background setting: black, dim, or theme surface.

## 8.2 Gestures

- Swipe left/right to navigate adjacent media.
- Pinch to zoom.
- Double-tap to zoom between fit and a useful zoom level.
- Drag while zoomed to pan.
- Swipe down to close when not zoomed.
- Swipe up or tap the details handle to open metadata.
- Avoid gesture conflicts: when zoomed, horizontal drags should pan the image rather than unexpectedly changing media.
- Provide a setting to disable or customise gestures.

## 8.3 Viewer actions

- Favorite/unfavorite.
- Share.
- Edit.
- Rotate view without altering the original; offer a separate save-rotation operation where appropriate.
- Delete/move to trash.
- Set as wallpaper through Android's supported wallpaper flow.
- Open with another app.
- Show details.
- Start slideshow.
- Copy image or file where Android and destination permissions permit.
- Export/convert with an explicit format and quality choice.

## 8.4 Slideshow

- Configurable interval.
- Optional fade or simple transition.
- Respect album order and selected sorting.
- Optional loop.
- Screen-on behavior must be explicit and temporary.
- Do not prevent device sleep indefinitely after the slideshow closes.

## 8.5 Image details sheet

Display only metadata that is available and permitted:

- File name and extension.
- MIME type.
- Image dimensions/resolution.
- File size.
- Capture date/time and modified date.
- Camera make/model and lens details where present.
- Exposure metadata where present.
- Location coordinates and map link only when location metadata is accessible and user opted in.
- Storage volume/folder label or granted location description.
- Orientation and color/HDR information when available.

Do not invent missing camera metadata. Distinguish “not available” from “permission not granted.”

## 8.6 Before/after and edit comparison

When an edited copy exists, offer a side-by-side or press-and-hold comparison with the original. Label both versions clearly. Do not overwrite the original to create a comparison.

---

# 9. Video playback and video tools

## 9.1 Video player

Use AndroidX Media3/ExoPlayer where appropriate.

Player controls:

- Play/pause.
- Seek bar with buffered progress.
- Current time and total duration.
- Skip forward/back configurable interval.
- Playback speeds: 0.5×, 1×, 1.5×, 2×; expose other speeds only if stable.
- Volume and brightness gestures only if discoverable and configurable.
- Screen rotation/fullscreen support.
- Select audio track and subtitles where present.
- Resume last playback position with a setting to disable it.
- Video details and codec information where available.
- Frame extraction to a new image file.
- Picture-in-picture where Android version, activity configuration, and device policy permit it.
- Share and delete actions.

## 9.2 Video operations

- Trim start/end with an accurate preview and non-destructive save-as-copy default.
- Rotate video by producing a new correctly oriented copy or updating supported metadata only when safe.
- Extract frame as JPEG/PNG with a user-selected time point.
- Support basic export quality choices and warn when a re-encode will reduce quality or consume storage.
- Show progress and allow cancellation for long exports.
- Never replace the source until the new output has been fully written and verified.

## 9.3 Playback recovery

If decoding fails:

1. Stop playback cleanly and release player resources.
2. Keep the viewer and app responsive.
3. Show a concise message such as “This video uses a codec or profile this device cannot play.”
4. Offer file details, retry, open with another app, or share the original.
5. Record a privacy-safe diagnostic category; do not record file content or precise location.

---

# 10. Built-in photo editor

## 10.1 Editor principles

The editor should be simple at first glance and powerful on demand. Basic actions must be immediately accessible; advanced controls should live in collapsible or secondary panels.

**Default:** never overwrite the source. Use “Save copy” as the primary action. Offer “Replace original” only as a separate, clearly labelled, confirmed operation when Android permissions allow it.

## 10.2 Basic editing

- Crop with freeform and presets: Original, 1:1, 4:3, 3:2, 16:9, and custom.
- Straighten and rotate 90-degree increments.
- Flip horizontal/vertical.
- Resize with aspect-ratio lock.
- Brightness, contrast, saturation, warmth/temperature, tint, highlights, shadows, sharpness, and exposure where supported.
- Basic filters with intensity slider.
- Draw, marker, arrows, shapes, text, and blur/pixelate markup.
- Undo/redo stack.
- Save copy.

## 10.3 Advanced editing

- Perspective correction for documents.
- Document scan mode: crop, perspective correction, deskew, grayscale/black-white, readability enhancement.
- Selective blur or background blur where technically feasible.
- Redaction brush and rectangle tool. Redaction must flatten the selected region before export so the hidden pixels cannot be recovered from the exported copy.
- Watermark text/image placement.
- Image compression with output quality and estimated-size preview.
- Collage and stitch layouts with clear source selection and canvas dimensions.
- Batch edit for a controlled set of operations; show a preview and apply only after confirmation.
- Optional portrait/subject enhancements only if implemented locally and accurately described.

## 10.4 Non-destructive edit model

Store an edit recipe/operation list in Room or a sidecar file. Keep the source URI and checksum/reference. Generate a rendered copy when the user saves or shares. On reopen, allow the user to continue editing from the saved recipe when it is still valid.

Do not keep all full-resolution intermediate bitmaps in memory. Use a tiled or bounded rendering strategy for large images. For files that cannot be decoded for editing, disable editor actions but retain the original file.

## 10.5 Editor export

- Offer JPEG, PNG, or WebP where the chosen feature and encoder support them.
- Preserve transparency when supported and chosen.
- Offer quality/compression settings only for lossy formats.
- Show output dimensions, estimated size, and destination.
- Preserve or remove EXIF according to the user's explicit privacy choice.
- Explain when HDR, bit depth, animation, RAW data, or metadata will be lost during export.
- Do not silently turn a RAW original into a flattened JPEG and call it an editable RAW file.

---

# 11. Camera module

Treat the camera as a distinct module integrated into Far Gallery, not as a replacement for the gallery's media pipeline.

## 11.1 Camera features

- Photo capture.
- Video recording.
- Front/rear camera switching.
- Flash and torch when available.
- Tap to focus.
- Focus/exposure controls where supported.
- Zoom slider and pinch zoom based on reported camera capabilities.
- Grid overlays: thirds, center, square.
- Timer: off, 3 seconds, 5 seconds, 10 seconds.
- Burst capture where supported.
- Resolution and aspect ratio choices based on the actual camera.
- HDR/night/portrait extensions only when CameraX/Camera2 reports support.
- Optional mirror-selfie setting.
- Camera save location with valid MediaStore/SAF behavior.
- Quick thumbnail of the latest capture.
- Tap latest capture to open it in the viewer; provide immediate edit shortcut.

## 11.2 Camera UI

- Large viewfinder.
- Capture button with clear photo/video mode.
- Camera switch, flash, timer, ratio, and settings controls.
- Minimal overlays that do not obscure the scene.
- Visible recording timer and stop control during video recording.
- Show a clear permission explanation when camera permission is denied.

## 11.3 Device capability handling

Use CameraX where possible and query actual camera capabilities. Do not assume that the reference device's 50 MP sensor exposes full-resolution capture to third-party apps or that advertised hardware features are accessible through the camera API. Handle camera-in-use, unavailable camera, device rotation, app backgrounding, thermal limits, and low storage.

## 11.4 Saving captures

Write to an appropriate public media collection using MediaStore. Use pending-write semantics for long-running output where supported so partially written files are not exposed as complete media. On success, update the gallery database and display the new capture without requiring a full rescan.

---

# 12. File manager and storage browser

## 12.1 Scope and truthful access model

The Files area should feel similar to a polished Android file manager, but the implementation must respect scoped storage. A normal app cannot assume unrestricted access to every internal folder or every removable-storage location.

Use:

- MediaStore for shared images/videos and supported public media collections.
- SAF `ACTION_OPEN_DOCUMENT` for user-selected files.
- SAF `ACTION_OPEN_DOCUMENT_TREE` for user-selected folders where allowed.
- SAF `ACTION_CREATE_DOCUMENT` for user-directed file creation/export.
- Persistable URI permissions when returned and supported.
- MediaStore operations for public media files.

Do not depend on legacy broad storage permissions. Do not promise access to other apps' private data. Even all-files access does not grant ordinary access to all `Android/data` or other apps' app-specific directories.

`MANAGE_EXTERNAL_STORAGE` is a restricted special permission. Consider it only for a build whose genuine core purpose qualifies as a file manager/backup manager, after a policy review. It must not be used merely to avoid implementing SAF. The standard build should work with SAF and MediaStore even without this permission.

## 12.2 File browser layout

- Overview page with Internal storage, removable SD card, and user-pinned locations.
- Breadcrumb navigation.
- Grid/list toggle.
- Search field.
- Sort and filter controls.
- Folder/file icons and appropriate thumbnails.
- Multi-select toolbar.
- Storage-use summary with clearly labelled values.
- Recent files and favorite folders.

## 12.3 File operations

Implement only operations that are permitted for the selected URI/volume:

- Open/view.
- Share.
- Rename.
- Copy.
- Move.
- Delete or move to app-managed trash where possible.
- Create folder.
- Create supported document/archive file.
- Compress to ZIP.
- Extract ZIP.
- Batch rename with pattern and numbering.
- File information.
- Favorite folders.
- Select files/folders.

If a provider does not support a requested rename/move/delete operation, show the limitation and do not pretend it succeeded.

## 12.4 Copy and move operation safety

- Confirm destination before execution.
- Check read/write access to source and destination.
- Detect name collisions before copying.
- Offer Rename, Replace (with explicit warning), Skip, or Cancel.
- Never overwrite silently.
- Show per-operation and overall progress for long work.
- Support cancellation.
- Verify output exists and has expected size before marking success.
- For moves across volumes, copy and verify first; delete source only after the destination is confirmed.
- If the operation is interrupted, preserve the source and offer safe retry/cleanup.

## 12.5 Storage dashboard and tools

- Internal storage used/available.
- SD card used/available when the system exposes it.
- Large files with sort by size.
- Large videos.
- Old media based on date criteria.
- Empty folder finder.
- Broken/unreadable media report.
- Recently deleted items.
- Duplicate scan entry point.
- Optional cache management showing exactly what can be safely removed.

Never label a file “junk” solely because it is old or large. Do not automatically delete user content.

## 12.6 Archive support

- ZIP create/extract first.
- Show archive contents before extraction.
- Allow selected-file extraction.
- Detect path traversal entries such as `../` and reject unsafe paths.
- Enforce decompression limits and protect against archive bombs.
- Preserve directory structure safely.
- Additional formats such as 7z, TAR, GZ, or RAR should be added only with a maintained library and licensing review. RAR extraction may have licensing or compatibility restrictions.

---

# 13. Duplicate and similar-media finder

Make duplicate cleanup a separate, safety-focused feature.

## 13.1 Exact duplicate detection

- Use file size as a cheap first filter.
- Compute a cryptographic hash such as SHA-256 for candidate files using streaming reads.
- Compare hashes to identify byte-identical files.
- Avoid loading entire files into memory.
- Store scan results and timestamps so unchanged files do not needlessly re-hash.

## 13.2 Similar media detection

- Use perceptual hashes or a tested local image similarity method.
- Compare images at bounded preview resolution.
- Group likely resized, compressed, or lightly edited copies.
- Detect repeated screenshots and burst-like sequences as optional categories.
- Video similarity may use selected frame fingerprints and duration metadata; clearly describe that similar-video results are approximate.
- Face or person matching must be a separate opt-in feature and must not be conflated with duplicate detection.

## 13.3 Duplicate review screen

Each candidate should show:

- Thumbnail/preview.
- Filename and location.
- File size.
- Resolution/duration.
- Capture and modified dates.
- Exact duplicate or similarity score/category.
- Whether the item is a user favorite, in a user-created album, or a protected vault item.

## 13.4 Safe cleanup rules

- Never automatically delete files.
- Never assume the oldest or newest copy is the correct original.
- Never delete every copy in a group.
- Protect favorites and items assigned to user-created albums by default.
- Allow the user to inspect each group and choose exactly what to delete.
- Provide Undo/Trash where feasible.
- Clearly explain that deleting from a shared collection affects the underlying file, not just the visible album entry.

---

# 14. Private vault and app lock

Hiding a file is not security. The private vault must store encrypted content in app-private storage and must not expose private media through normal media indexing.

## 14.1 Vault features

- Encrypted private storage for photos and videos.
- Separate vault PIN/password from the optional general app lock.
- Fingerprint/biometric unlock through BiometricPrompt, with a secure credential fallback.
- Optional pattern unlock only if implemented with a safe, non-reversible verifier and adequate throttling. Do not store the drawn pattern itself.
- Automatic lock on app background, screen off, or configurable timeout.
- Rate limiting and progressive delays after repeated incorrect attempts.
- Private folders/collections inside the vault.
- Import by copy or move.
- Export/restore after authentication.
- Optional vault backup using a separately protected encrypted backup format.

## 14.2 Encryption design

- Store vault ciphertext in app-private internal storage by default.
- Use Android Keystore to protect key material or a key-encryption key.
- Use a vetted authenticated encryption design such as AES-256-GCM with unique nonces per encryption operation; large files should use a well-reviewed chunked authenticated-encryption format rather than one enormous in-memory operation.
- Never hardcode a master key in source code or resources.
- Never store a plaintext password/PIN.
- Derive password-based key material using a well-reviewed password KDF with per-vault random salt and calibrated work factor. Use a maintained, vetted implementation; do not invent cryptographic primitives.
- Ensure failed authentication does not leave decrypted temporary files behind.
- Keep vault thumbnails encrypted or generate them only in memory after authentication. Do not put private thumbnails in the normal thumbnail cache.
- Do not expose vault URIs through public content providers, widgets, notifications, normal search, Android recents previews, or share targets without explicit user action.
- Use secure window flags for vault screens where appropriate, while explaining that screenshot blocking is not absolute protection against external cameras or compromised devices.

## 14.3 Import/move safety

When moving a file into the vault:

1. Read the source through its granted URI.
2. Encrypt into a new vault object.
3. Verify ciphertext integrity and successful decryption test.
4. Commit the vault database record.
5. Only then ask whether to remove the original.
6. Delete the original only after the user confirms and Android permits it.

Do not claim forensic-grade erasure from flash storage. Flash wear-leveling, filesystem snapshots, cloud copies, and other backups may leave recoverable copies outside the app's control.

## 14.4 Recovery warning

The app must clearly explain vault recovery. If the user loses all credentials and recovery material, encrypted content may be unrecoverable. Offer a user-controlled recovery key or encrypted export. Never silently weaken encryption to make recovery easy.

---

# 15. Search, OCR, and optional on-device intelligence

## 15.1 Standard search

Support searching:

- Filename and extension.
- Album/folder.
- Capture date or date range.
- Date added/modified.
- File type.
- Dimensions/resolution.
- File size range.
- Video duration.
- Favorite state and tags.
- Location metadata when available and permitted.
- Text extracted from images when OCR is enabled and indexing is complete.

Examples users should be able to express using search filters or natural date syntax:

- Photos from January 2025.
- Videos larger than 500 MB.
- Screenshots from last month.
- Images in a selected album.
- JPG files with a particular filename.
- Photos containing a phrase visible in a screenshot.

Do not make natural-language search a dependency for basic search.

## 15.2 Search interface

- Search bar with immediate filename results.
- Filter chips and an advanced filter sheet.
- Recent searches with clear/delete history action.
- Saved searches with user-defined names.
- Suggestions based on real indexed categories only.
- Distinguish “no result” from “not indexed yet” or “permission-limited.”

## 15.3 Offline OCR

- Use a bundled/on-device OCR engine for a genuinely offline implementation.
- Verify model delivery behavior. Do not depend on a Play Services model download if the requirement promises first-run offline availability.
- Process images in the background with WorkManager and a user-visible indexing status.
- Allow pause, resume, cancel, and disable.
- Provide a “Delete OCR index” action that removes extracted text while leaving original images untouched.
- Index screenshots, documents, signs, receipts, and memes where text detection is useful.
- Store extracted text locally and protect the index according to the app's privacy model.
- Tap a search result to open the original media and optionally highlight detected text region if coordinates are available.
- Never upload OCR content automatically.

## 15.4 Optional on-device AI

Potential later features:

- Local scene/category auto-tagging.
- Smart albums from user-selected rules.
- Face grouping with on-device processing only.
- Semantic search using a local embedding model.
- Receipt/document detection.
- Burst grouping and subject similarity.

All must be opt-in, cancellable, transparent about battery/storage/model size, and accompanied by a delete-index option. Face grouping is sensitive and must not identify people by name or upload face embeddings.

## 15.5 Google Lens integration

Offer “Open with Google Lens” only when a compatible app/activity is installed and available. Use a standard Android intent or documented integration. Do not assume Lens is installed, do not scrape its UI, and do not claim that Lens is part of Far Gallery's offline search engine.

---

# 16. Map and location-based browsing

- Map view groups media with accessible GPS metadata.
- Ask for required media-location access only when the user turns on map functionality or requests precise location details.
- Allow approximate location display or a location-free mode.
- Group photos by city/region only when reverse-geocoding data is available. Do not require cloud geocoding for the local app.
- Do not send coordinates to a server by default.
- If offline map tiles are unavailable, show a location list or coordinate-based grouping rather than a broken map.
- Provide an explicit “Remove location metadata on export/share” option.
- Do not expose geotags in public share previews unless the user chose to retain them.

---

# 17. Sharing, receiving, picker integration, and interoperability

## 17.1 Share out

- Use Android's standard Sharesheet.
- Share content using secure `content://` URIs and a FileProvider or appropriate provider.
- Grant temporary read permissions to recipient apps.
- Support single and multiple selection.
- Share original or edited copy according to the user's selection.
- Offer a privacy sheet before sharing that can remove location EXIF, camera make/model, and other selected metadata.
- Explain that metadata stripping may require re-encoding and can remove some information.
- Do not change the source file when generating a stripped share copy.

## 17.2 Receive media

Register appropriate intent filters for supported content:

- `ACTION_VIEW` for supported image/video MIME types.
- `ACTION_SEND` for a single incoming item.
- `ACTION_SEND_MULTIPLE` for multiple incoming items.
- Document/open flows where appropriate.

When receiving media:

1. Validate the intent action and MIME type.
2. Validate URI scheme and granted permissions.
3. Query metadata safely.
4. Do not trust filenames, extensions, MIME types, embedded paths, or archive entry names.
5. Open read-only first.
6. Offer View, Import/Copy, Edit Copy, or Save to album as appropriate.
7. Never modify the sender's original URI without a clear operation and permission.
8. Handle URI permission expiry and inaccessible providers gracefully.

## 17.3 Photo picker / gallery picker

- Integrate Android Photo Picker as an option for one-off selections.
- Offer Far Gallery's own picker for users who grant library access.
- Consider implementing `MediaStore.ACTION_PICK_IMAGES` or the relevant supported picker contract for app integration.
- Keep app-to-app picker access separate from the main gallery's full-library permission model.
- Ensure returned URIs are valid and passed with the required temporary read grants.

## 17.4 Print and wallpaper

- Use Android's print framework for supported still images.
- Use the system wallpaper flow for home/lock screen wallpaper.
- Handle no compatible print service or wallpaper handler with a clear message.

---

# 18. Trash, deletion, and recovery

## 18.1 Deletion model

Separate three concepts:

1. **Remove from album:** removes the app's collection reference only; file remains.
2. **Move to Far Gallery trash:** moves the file into an app-managed recovery location only when the app has a safe, permitted way to do so.
3. **Delete permanently:** removes the underlying file using the correct Android consent/API flow.

Do not use the word “hide” when the action actually deletes the file.

## 18.2 Retention

- Default app-managed trash retention: 30 days, configurable where appropriate.
- Show the number of days remaining when available.
- Allow restore to original location when that location still exists; otherwise ask the user to choose a destination.
- Permanently delete only after expiry and only according to the user's chosen policy. For shared media where Android controls deletion, follow the platform's trash/delete behavior and permissions.
- Provide “Empty trash” with an explicit irreversible-action confirmation.

## 18.3 Undo

For immediate operations, show an Undo action with a clear timeout. Undo must restore the real file/collection state, not merely dismiss a message.

## 18.4 System trash and deletion consent

On modern Android versions, deleting or modifying media created by another app may require system confirmation or user-granted media-management access. Implement the API-specific MediaStore requests and recoverable consent flows. Never bypass these dialogs.

---

# 19. Backup and restore

## 19.1 Backup types

**Settings/metadata backup:** app settings, album definitions, pins, favorites, tags, edit recipes, saved searches, and other app-owned metadata. It does not contain original media files.

**Full local backup:** selected media files plus Far Gallery metadata and supported edit data.

**Incremental backup:** only files and metadata changed since a verified previous backup.

**Encrypted backup:** password-protected backup with authenticated encryption and a documented recovery process.

## 19.2 Backup destination

- Internal app storage only for temporary staging.
- User-selected folder on internal storage or SD card using SAF.
- External USB/OTG destinations only when exposed by Android and granted by the user.
- Optional server destination only in the sync-enabled build and only after explicit configuration.

## 19.3 Backup workflow

1. User selects backup type and destination.
2. Show estimated size when it can be calculated without excessive work.
3. Explain whether media originals, metadata, edits, vault content, and sync settings are included.
4. Ask for encryption password/key if encrypted backup is selected.
5. Create a manifest with item IDs, source hashes, metadata, format, and backup version.
6. Stream file contents; do not load entire media into RAM.
7. Write temporary output and finalize atomically where possible.
8. Verify hashes and archive integrity.
9. Mark the backup complete only after verification succeeds.
10. Keep the previous verified backup when a new backup fails.

## 19.4 Restore workflow

- Preview backup contents and version before writing anything.
- Offer selective restore of media, albums, tags, favorites, settings, edits, and vault items.
- Detect name collisions and duplicate content.
- Preserve current files unless user explicitly chooses replacement.
- Restore to a user-selected location when original paths are unavailable.
- Verify restored content.
- Support interrupted restore recovery and safe restart.
- Never assume that restoring a sync snapshot is equivalent to restoring a full backup.

## 19.5 Backup failure handling

Handle destination removal, permission revocation, low storage, incorrect password, corrupted archive, version incompatibility, cancellation, and process death. Preserve the last known-good backup and report exactly what completed.

---

# 20. Optional LibrePhotos synchronization

LibrePhotos connectivity is an optional integration. It must not compromise the local-first app.

## 20.1 Build and permission separation

Provide two build variants when practical:

- **Far Gallery Local:** no `INTERNET` permission in the merged manifest; no networking code path; fully offline.
- **Far Gallery Sync:** includes required network permission and optional LibrePhotos integration, disabled until the user configures it.

Android permissions declared in the installed manifest cannot be dynamically removed at runtime. A single app that contains `INTERNET` permission can choose not to use the network, but it is not technically identical to a build whose manifest does not declare that permission. Keep the offline variant's merged manifest free of network permission.

## 20.2 Sync configuration

- User enters server URL and authentication details.
- Validate TLS certificate and hostname. Do not disable certificate validation.
- Store credentials securely; never log tokens or passwords.
- Support self-hosted/private server configurations.
- Explain which local media and metadata will be uploaded.
- Allow user to select albums/folders for sync rather than enabling all media automatically.
- Make upload/download state visible and cancellable.

## 20.3 Conflict policy

Define and implement one explicit policy:

- Track stable local identifiers, server identifiers, checksums, and last-sync state.
- Detect local edits, remote edits, remote deletions, and local deletions.
- Do not propagate deletion until the user has chosen a policy.
- Prefer non-destructive conflict copies over silent overwrite.
- Provide a conflict review screen.
- Make one-way upload, one-way download, and two-way sync separate user-selectable modes only if each mode is fully implemented and tested.

Do not call synchronization a backup. A server deletion or sync bug can propagate to the client; maintain independent backups.

## 20.4 API compatibility

Use the currently documented LibrePhotos API and isolate it behind a repository/adapter. Do not hardcode guessed endpoints or rely on undocumented APIs. Add compatibility tests against a supported server version and show a clear connection error when the server version/API is incompatible.

---

# 21. Widgets, shortcuts, and Android integration

Optional polish features:

- Home-screen widget showing user-selected recent/favorite media; never display vault content.
- Widget configuration screen with album/favorites source and privacy controls.
- App shortcuts for Camera, Favorites, Search, and optionally Files.
- Quick Settings tile to open Gallery or Camera where platform APIs and user approval allow it.
- Notification only for user-visible long-running operations such as backup, restore, or sync; avoid unnecessary notifications.
- Respect Android background execution limits.
- Print integration.
- Standard open/share/picker intents.

---

# 22. Replacing Google Photos, AI Gallery, Files, and DocumentsUI

This is an integration goal, not a guaranteed feature of a normal Android app.

## 22.1 What an ordinary installed app can do

- Register intent filters to open supported images and videos.
- Accept shares from other apps.
- Provide a gallery picker for apps that explicitly invoke compatible picker contracts/intents.
- Provide file browsing for media and folders the user has granted access to.
- Be chosen as a handler for certain file/view intents when Android presents a chooser or default-app option.

## 22.2 What an ordinary installed app generally cannot do

- Replace every Google Photos, OEM Gallery, Android Photo Picker, or Files entry point automatically.
- Become a universal default gallery role if the Android version/OEM does not expose such a role.
- Replace DocumentsUI simply by installing an APK.
- Access protected app-private directories belonging to other apps.
- Bypass scoped storage, system confirmation, permissions, or OEM restrictions.

## 22.3 Custom Android build integration

If Far Gallery is later integrated into a custom ROM/build:

- Treat system image integration as a separate engineering deliverable.
- Define package name, signing keys, privileged permissions, SELinux policy, intent filters, roles, and system configuration explicitly.
- Review compatibility with the Android Photo Picker, DocumentsUI, SAF providers, system settings, backup/restore, and OEM framework changes.
- Do not remove or disable DocumentsUI casually. Many Android file chooser and document-provider workflows rely on system components.
- Keep a recovery path to re-enable the system file/document UI.
- Test OTA updates, factory reset, package signature changes, and system app upgrades.
- Never imply that a standard APK can perform privileged system replacement.

The core app must remain fully functional without privileged integration.

---

# 23. Database and data model

Use Room for Far Gallery-owned metadata. MediaStore remains the authoritative system index for shared media; the Room database is a fast app-side index and collection store.

Suggested entities (adapt as needed):

- `MediaItem`: stable app key, URI, volume, media type, MIME type, display name, size, dimensions, duration, capture date, date added, modified date, availability state, hash state, decoder status.
- `Album`: ID, name, description, created date, sort order, cover media ID, pinned state, type (user/smart/system).
- `AlbumMediaCrossRef`: album ID, media ID, order.
- `Favorite`: media ID, timestamp, optional group ID.
- `Tag` and `MediaTagCrossRef`.
- `FolderGrant`: SAF URI, display name, volume, persisted permission flags, last validated time.
- `EditRecipe`: media ID, source checksum, operations, version, output URI.
- `TrashEntry`: source URI, original location/volume, deletion time, expiry, restore capability.
- `SearchHistory` and `SavedSearch`.
- `OcrIndex`: media ID, extracted text, language, bounding boxes if available, processing version.
- `DuplicateGroup` and `DuplicateCandidate`.
- `BackupManifest` and `BackupItem`.
- `SyncRecord` (sync build only).
- `OperationRecord`: operation type, status, progress, error code, timestamps, resumability data.
- `VaultItem`: encrypted object ID, ciphertext URI, crypto version, nonce/chunk metadata, encrypted thumbnail reference, user metadata.

## 23.1 Database requirements

- Use stable identifiers; do not use display name as a primary key.
- Treat content URIs as revocable references.
- Support schema migrations without losing user collections.
- Add indexes for common queries and sort/filter combinations.
- Avoid storing huge blobs or original media in Room.
- Use transactions for multi-table updates.
- Keep deletion and collection removal semantics distinct.
- Add migration tests for every schema version.
- Store nullable fields when metadata is genuinely absent; never invent values.

---

# 24. Media indexing, thumbnail generation, and caching

## 24.1 Initial indexing

- Show the main interface immediately.
- Query MediaStore in pages and save lightweight metadata first.
- Request thumbnails only for visible or near-visible items.
- Run optional metadata enrichment, OCR, duplicate hashing, and face grouping as separate scheduled work.
- Display progress and allow optional work to be paused/cancelled.
- Never hold a database transaction open while decoding a large file.

## 24.2 Incremental indexing

- Register appropriate content observers for MediaStore changes.
- Reconcile changes when the app resumes.
- Use MediaStore version/generation information where available to detect when a full resynchronization is necessary.
- Do not perform a full filesystem scan after every new capture.
- Detect storage volume removal and mark affected items as unavailable without deleting album/favorite metadata.

## 24.3 Thumbnail strategy

- Use MediaStore-provided thumbnails or a compatible decoder where possible.
- Generate thumbnails at target display dimensions, not original resolution.
- Cache in bounded disk and memory caches.
- Use stable cache keys including media ID/URI plus modification/version information.
- Invalidate when source content changes.
- Do not cache decrypted vault thumbnails in the public thumbnail cache.
- Handle thumbnail decode exceptions individually; one bad file must not break an entire grid.

## 24.4 Large library support

Test at least 10,000, 25,000, and 50,000 media items, with mixed formats and folders. Measure launch time, scroll jank, indexing time, peak memory, CPU, battery, and cache growth. The app must page data and avoid loading the entire media library into memory.

---

# 25. Permissions and Android storage behavior matrix

The implementation must review this matrix against the exact target SDK and current Android documentation before release.

| Android version | Media library access strategy | Important notes |
|---|---|---|
| Android 11 / API 30 | `READ_EXTERNAL_STORAGE` for reading shared media from other apps, plus MediaStore; SAF for user-selected files/folders | Scoped storage applies. Do not rely on legacy unrestricted paths. |
| Android 12 / API 31–32 | `READ_EXTERNAL_STORAGE` for shared images/videos; MediaStore and SAF | Special media-management permissions and system consent flows may apply to modifications/deletes. |
| Android 13 / API 33 | `READ_MEDIA_IMAGES` and `READ_MEDIA_VIDEO` for broad library access; Photo Picker for limited selection | Request only the media types actually needed. |
| Android 14 / API 34 | `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, and `READ_MEDIA_VISUAL_USER_SELECTED` for a custom gallery that supports partial access; Photo Picker for one-off selection | Users can grant selected-photo access. Recheck access on resume. |
| Android 15 / API 35 | Maintain API 34 partial-access handling and current platform behavior | Test behavior changes and vendor implementations. |
| Android 16 / API 36 | Maintain media permission and selected-media handling; test API 36 behavior | For new Google Play mobile submissions, target API 36 or higher as of 31 August 2026. |

Additional permissions/capabilities:

- `CAMERA`: request only when camera feature is used.
- `RECORD_AUDIO`: request only if recording video with audio; explain why.
- `ACCESS_MEDIA_LOCATION`: optional runtime permission if the app needs unredacted location metadata from images. Do not request unless needed.
- `INTERNET`: absent from Local build; present only in Sync build.
- `MANAGE_EXTERNAL_STORAGE`: do not request by default. Consider only for an eligible file-manager/backup product after policy review and with a clear user explanation.
- `MANAGE_MEDIA`: special access only if the app has a legitimate media-management use case and the user explicitly enables it; still follow current platform requirements.
- `POST_NOTIFICATIONS`: request only if the app needs notifications for user-visible operations and only at the appropriate point.
- `READ_MEDIA_VISUAL_USER_SELECTED`: include when maintaining a custom gallery permission model that supports Android 14 selected media access.
- Biometric capability: no storage permission; check device support and provide secure fallback.

Do not declare obsolete or unnecessary storage permissions just because they appear in old tutorials. Do not assume a runtime permission was granted because the app requested it. Always check the current grant state.

---

# 26. SD card, USB OTG, and multiple storage volumes

- Detect available storage volumes and distinguish primary shared storage from removable volumes.
- Use MediaStore volume APIs where supported for indexed media.
- Use SAF for folders on SD card or USB storage when access is not available through MediaStore.
- Save persistable URI permissions when the system returns them.
- Detect when a card is removed during browsing, copying, playback, backup, or editing.
- Mark its files as temporarily unavailable; do not remove albums, favorites, or tags merely because the card is absent.
- On reconnection, validate the grant and reconcile the volume.
- Do not trust that a previously granted tree URI remains valid forever.
- Handle read-only cards, filesystem errors, provider failures, and insufficient space.
- Warn before writing large exports to a slow or nearly full SD card.
- For copy/move between internal and removable storage, stream content and verify output before deleting the source.

---

# 27. Error handling and crash resilience

This is a core requirement. Every media operation must be isolated so that one bad file or inaccessible provider cannot crash the application.

## 27.1 General rules

- Never perform filesystem I/O, large decoding, hashing, database-heavy work, or network calls on the main thread.
- Catch expected failures at the repository/use-case boundary and return typed errors.
- Do not catch every exception and silently ignore it.
- Never show raw stack traces to users.
- Log diagnostic error codes and safe context only.
- Do not log private filenames, EXIF coordinates, OCR text, vault data, credentials, or file contents.
- Release cursors, streams, file descriptors, players, camera sessions, and temporary files reliably.
- Use operation IDs so progress and recovery can be associated with a specific task.
- Ensure one failed item does not terminate batch processing for all other items unless data integrity requires the batch to stop.

## 27.2 Required error scenarios

| Scenario | Required behavior |
|---|---|
| Corrupted image | Show placeholder/error preview, retain file, allow details/share/open with, continue loading other items. |
| Unsupported format | Explain unsupported decoder/profile; show embedded preview if available; preserve original. |
| Corrupted video | Stop playback cleanly, release player, show retry/open-with options. |
| Missing file | Mark item unavailable, offer rescan/remove stale listing, preserve user collection metadata. |
| Permission revoked | Stop reading, update privacy status, request permission only after user action, do not crash. |
| Partial media access | Display only accessible items and explain that results may be incomplete. |
| SD card removed | Cancel affected operations safely, preserve source where possible, mark volume unavailable. |
| SAF provider unavailable | Show provider/permission error and offer reselect-folder action. |
| Low storage | Estimate/measure available space, stop safely before corrupting output, preserve originals. |
| Out of memory | Downsample, release caches, retry at lower preview size where safe; avoid repeated crash loops. |
| MediaStore query failure | Show cached data with stale indicator when safe; schedule retry; don't show fabricated results. |
| Thumbnail failure | Use placeholder for that item only. |
| Database migration failure | Do not overwrite the previous database blindly; preserve a recoverable backup and report failure. |
| Interrupted copy/move | Keep source; clean or resume partial destination safely. |
| Interrupted backup/restore | Mark incomplete; resume or restart safely; preserve previous verified backup. |
| Incorrect vault password | Apply throttling; do not expose whether encrypted content exists beyond required UX. |
| Vault integrity failure | Refuse to render corrupted plaintext; preserve ciphertext; offer recovery/backup path. |
| Camera unavailable | Explain camera is busy/unavailable and offer retry. |
| Camera permission denied | Keep rest of app working; show permission explanation in Camera only. |
| Sync server unavailable | Keep local gallery functional; queue or pause sync according to user setting. |
| Invalid incoming intent/URI | Reject safely and show “Can't open this item” with file/provider details where safe. |
| Malformed archive | Reject unsafe entries, stop extraction, clean temporary partial output, preserve archive. |

## 27.3 User-facing message quality

Messages should state:

1. What happened.
2. Whether the original file is safe/unchanged.
3. What the user can do next.

Example: “This video could not be played because its codec is not supported by this device. The original file has not been changed. You can try another player or share the original.”

Avoid vague messages such as “Something went wrong” unless no more specific error is available.

## 27.4 Crash recovery

- Persist operation state before performing long actions.
- Recover or mark unfinished tasks at app startup.
- Store current viewer item and scroll position where appropriate.
- Do not automatically repeat destructive actions after process death.
- Ask before restarting a partially completed delete or replacement operation.
- Ensure cleanup of temporary files is idempotent and does not remove source media.

---

# 28. Security and privacy requirements

- No account or sign-in required for local features.
- Local build has no network permission and no network code path.
- Minimal manifest permissions.
- No unnecessary analytics, advertising SDKs, tracking SDKs, or hidden telemetry.
- Optional crash reporting only with clear disclosure and privacy-safe configuration; local diagnostics should remain possible without uploading logs.
- Securely store sync credentials and encryption keys.
- Use Android Keystore for key protection.
- Use content URIs for sharing; do not expose raw private filesystem paths.
- Validate imported media and archive structures.
- Sanitize SVG and other potentially active formats.
- Do not execute content embedded in media metadata.
- Keep vault content out of thumbnails, OCR, widgets, recents, and general share targets.
- Respect Android backup rules. Do not accidentally back up vault ciphertext/keys in a way that breaks the threat model.
- Provide clear privacy/data-handling documentation inside the app.
- Provide a data reset/clear cache option with a precise explanation of what is removed.
- Distinguish cache deletion from deleting originals.
- For any optional server feature, disclose exactly what data leaves the device and how to disconnect/delete server copies where supported.

---

# 29. Settings screen

Organise settings into clear groups:

### Appearance
- Theme: System, Light, Dark, AMOLED Black.
- Accent color/dynamic color.
- Grid columns and spacing.
- Viewer background.
- Animation and haptic preferences.
- App language.

### Gallery
- Default sort order.
- Date grouping.
- Thumbnail quality/cache limit.
- Hidden/excluded folders.
- Recently viewed tracking toggle.
- Slideshow preferences.

### Media and storage
- Media access status.
- Manage SAF folder grants.
- Default camera save location.
- Storage dashboard.
- Cache clear controls.
- Trash retention.

### Privacy and security
- App lock.
- Vault lock behavior.
- Biometric authentication.
- Vault recovery/export information.
- EXIF sharing default.
- OCR/AI feature toggles and delete-index controls.

### Backup and sync
- Local backup and restore.
- Backup encryption.
- LibrePhotos connection (Sync build only).
- Upload/download rules and conflict policy.

### About and diagnostics
- App version/build.
- Open-source licenses.
- Privacy policy/data handling.
- Export privacy-safe diagnostics.
- Implementation/feature status if useful for early testing.

---

# 30. Testing strategy and acceptance criteria

The project is not ready for release until the following tests pass.

## 30.1 Device/API matrix

Test on:

- Android 11 / API 30.
- Android 12 / API 31–32.
- Android 13 / API 33.
- Android 14 / API 34 (including the supplied target device class).
- Android 15 / API 35.
- Android 16 / API 36.

Also test at least one low-memory device, one device with a different OEM skin, one tablet/foldable emulator, and one removable-SD-card scenario.

## 30.2 Gallery acceptance criteria

- Gallery displays only real accessible media.
- Partial and denied permissions are clearly represented.
- Grid scrolls without loading full-resolution images for every item.
- Sorting/filtering returns correct results.
- Newly added and removed media are reconciled.
- Hidden folders are excluded from the main timeline without deleting files.
- User albums, covers, pins, favorites, and tags persist after restart and app upgrade.

## 30.3 Viewer/editor criteria

- Zoom, pan, swipe, close, details, and actions behave correctly.
- EXIF orientation is displayed correctly.
- Unsupported and corrupt files do not crash the viewer.
- Editor saves a copy by default.
- Undo/redo works for supported operations.
- Redaction is flattened into the exported copy.
- Original remains unchanged unless explicitly replaced.
- Export options accurately disclose metadata, HDR, animation, RAW, and quality limitations.

## 30.4 Storage/file manager criteria

- SAF grants persist where allowed and can be revoked/reselected.
- Internal and SD storage status is correct.
- No false promise of unrestricted access.
- Copy/move handles collision, cancellation, card removal, and low storage.
- Cross-volume moves verify copy before deleting source.
- Archive extraction rejects path traversal and dangerous expansion.
- No operation silently overwrites a file.

## 30.5 Vault criteria

- Vault ciphertext is not exposed through normal gallery, search, widget, thumbnail, or share surfaces.
- Encryption/authentication survives process death and device restart.
- Wrong credentials are throttled.
- Import/move does not delete original before successful encryption and verification.
- Export requires authentication.
- Loss of recovery credentials is clearly explained.

## 30.6 Backup/sync criteria

- Backup integrity is checked before completion.
- Interrupted backup does not replace the last known-good backup.
- Restore preview and selective restore work.
- Conflicts do not cause silent overwrites.
- Local build performs no network calls and merged manifest has no INTERNET permission.
- Sync build requires deliberate setup and handles unavailable server gracefully.

## 30.7 Performance criteria

Test a library with 10k, 25k, and 50k mixed media entries. Measure:

- Cold launch and warm launch.
- Time to first visible content.
- Scroll frame time/jank.
- Peak memory usage.
- Thumbnail cache size.
- CPU and battery during indexing.
- Pause/resume behavior under low battery and thermal throttling.

Do not claim a performance target without measuring it on real hardware. Set initial budgets and refine them using profiling.

## 30.8 Reliability and fuzz testing

Test:

- Truncated files.
- Incorrect file extensions.
- Extremely large images.
- Malformed EXIF.
- Missing thumbnails.
- Invalid URI providers.
- Revoked SAF permissions.
- Read-only storage.
- Duplicate filenames.
- Interrupted process at each stage of backup, restore, copy, move, and vault import.
- Malformed SVG and ZIP entries.
- Media files with unusual codec profiles, high bit depth, HDR, multiple audio tracks, and subtitles.

---

# 31. Phased implementation roadmap

Do not attempt all modules at once. Each phase must be independently buildable and testable.

## Phase 0 — Project foundation

Deliver:

- Native Kotlin/Compose project.
- Material 3 theme system.
- Navigation skeleton.
- Build variants for Local and Sync if practical.
- Permission manager.
- Room schema and migration framework.
- Media repository abstraction.
- Basic logging and typed error handling.
- Unit/instrumentation test setup.

Acceptance: clean build on API 30+; no fake media shown; permission states work.

## Phase 1 — Core gallery MVP

Deliver:

- Timeline grid.
- Albums and folders view.
- Full-screen photo viewer.
- Basic video playback.
- Search by filename.
- Sorting and core filters.
- Favorites and multi-select.
- Standard sharing and incoming media.
- Safe delete flow.
- MediaStore indexing and thumbnails.

Acceptance: real local media is browsable, errors are isolated, partial access is clear, and large libraries use paging.

## Phase 2 — File and media management

Deliver:

- SAF-based file browser.
- SD-card folder grants.
- Copy/move/rename/create/delete within granted scope.
- Storage dashboard.
- Trash and restore.
- Album pinning/covers/hide-exclude.
- Batch selection and basic file info.
- ZIP create/extract with safety checks.

Acceptance: no silent overwrites, operations recover from interruption, and storage restrictions are explained.

## Phase 3 — Editing and privacy

Deliver:

- Basic photo editor.
- Undo/redo and save-as-copy.
- EXIF privacy sharing option.
- App lock.
- Encrypted vault with documented recovery.
- Vault import/export and secure thumbnail handling.

Acceptance: original files remain unchanged by default; vault items never appear in public gallery surfaces.

## Phase 4 — Advanced media tools

Deliver:

- Video trimming and frame extraction.
- Slideshow configuration.
- Duplicate detection.
- Batch rename/edit.
- Advanced editor tools.
- Backup and restore.

Acceptance: destructive actions require confirmation, hashes are verified, and interrupted tasks recover safely.

## Phase 5 — Offline smart features

Deliver:

- Bundled on-device OCR.
- OCR text search and indexing progress.
- Smart albums and optional category tagging.
- Optional face grouping only after privacy/security review.
- Map view for media with location metadata.

Acceptance: airplane-mode tests pass; indexes can be deleted; no image/text/face data is uploaded.

## Phase 6 — Camera and optional integrations

Deliver:

- CameraX photo/video capture.
- Camera capability UI.
- Quick capture-to-gallery flow.
- Widgets, shortcuts, and Quick Settings tile where supported.
- LibrePhotos adapter in Sync build.
- Sync conflict controls and network diagnostics.
- Custom ROM integration documentation as a separate deliverable.

Acceptance: local variant remains offline; sync is opt-in; camera features reflect actual device capability.

---

# 32. MVP priority and what must not be delayed

The first usable release must include:

1. Photo/video timeline.
2. Album/folder browsing.
3. Full-screen viewer.
4. Basic video playback.
5. Search by filename.
6. Favorites and multi-select.
7. Sharing and incoming media.
8. Safe delete/trash behavior.
9. Basic file browsing using SAF.
10. Permission status and API-aware permission handling.
11. Thumbnail caching, paging, and background media indexing.
12. Robust handling of missing, corrupt, unsupported, and inaccessible media.

Do not prioritise face grouping, cloud AI, or replacing system applications over the reliability of these basic features.

---

# 33. Additional requirements that were missing or needed stronger definition

The source prompt was already extensive. The following additions close important gaps:

1. **Incoming media and app intents:** Far Gallery must receive media from messaging apps, browsers, file managers, and other apps safely—not only browse its own library.
2. **Photo picker/provider integration:** Other apps should be able to request media from Far Gallery where supported.
3. **Explicit format/codec policy:** Extensions and containers are not codecs. The app needs real capability detection, decoder fallback, RAW preview behavior, and preservation rules.
4. **iPhone transfer boundary:** HEIC, HEVC, DNG/ProRAW, Live Photo exports, and ProRes are important, but an Android app cannot access iOS's private Photos library or promise Apple's complete native behavior.
5. **Redaction correctness:** Pixelated/blurred sensitive content must be flattened in the exported copy; merely covering it with a removable layer is unsafe.
6. **Archive security:** ZIP extraction must prevent path traversal and decompression bombs.
7. **Operation journal:** Long operations need persistent progress, cancellation, resumability, and safe recovery after process death.
8. **MediaStore change tracking:** Use incremental reconciliation and version/generation checks rather than repeatedly scanning the whole device.
9. **Database migration:** User albums, tags, pins, edit recipes, and vault metadata must survive upgrades.
10. **Storage-volume loss:** Card removal during reads/writes must not corrupt originals or erase user organization metadata.
11. **Media metadata privacy:** Location data requires deliberate access and sharing controls; EXIF policy must be visible.
12. **Color/HDR fidelity:** HEIC, AVIF, Ultra HDR, RAW, HDR video, wide-gamut color, bit depth, and transcoding behavior need explicit tests and fallback policy.
13. **Audio track/subtitle handling:** Video files can include multiple tracks and subtitles; the player should expose supported tracks and handle unsupported tracks safely.
14. **File collision strategy:** Copy, move, export, and restore must have explicit Replace/Rename/Skip/Cancel rules.
15. **System app replacement boundaries:** DocumentsUI/Photo Picker replacement and privileged ROM integration must remain separate from normal APK functionality.
16. **No-network build verification:** A true offline build must have no `INTERNET` permission in its merged manifest, not merely a setting that says “offline.”
17. **Privacy-safe diagnostics:** Crash/diagnostic reports must avoid filenames, OCR text, EXIF GPS, and vault content.
18. **Accessibility/RTL:** Urdu translation must include layout behavior, not only translated strings.
19. **Battery/thermal awareness:** Expensive indexing and AI workloads need pause/defer controls.
20. **Import safety:** Incoming files must be treated as untrusted input; validate MIME, URI grants, metadata, archive paths, and malformed content.

---

# 34. Final implementation checklist for Google AI Studio

Before declaring Far Gallery complete, verify every statement below:

- [ ] The deliverable is a native Android Studio project, not just UI code or a web app.
- [ ] The app builds successfully with the documented SDK and dependency versions.
- [ ] `minSdk` is 30 and release target SDK is current (API 36 or newer for the current Play submission requirement).
- [ ] The Local build's merged manifest contains no INTERNET permission and no networking SDK/path.
- [ ] The Sync build is separate or clearly opt-in and does not upload until configured.
- [ ] Media permission logic supports full, partial, and denied access on relevant Android versions.
- [ ] Media permissions are rechecked after app resume.
- [ ] MediaStore and SAF are used for their appropriate storage tasks.
- [ ] The app does not claim unrestricted access to every internal or SD-card directory.
- [ ] Real photos and videos appear; no production dummy data exists.
- [ ] Thumbnail loading is paged, cached, bounded, and memory-aware.
- [ ] Corrupt, missing, unsupported, and inaccessible items do not crash the whole app.
- [ ] All user controls are wired to real operations or honestly marked unavailable.
- [ ] Original media is preserved by default.
- [ ] Destructive operations require the correct confirmation and Android consent.
- [ ] The file manager does not silently overwrite or delete files.
- [ ] Vault content is encrypted and excluded from normal indexing and sharing.
- [ ] OCR/AI features run locally in offline mode and provide an index deletion control.
- [ ] Unsupported RAW/video/HEIC/AVIF profiles use safe fallback behavior.
- [ ] Incoming `ACTION_VIEW`, `ACTION_SEND`, and `ACTION_SEND_MULTIPLE` requests are handled safely.
- [ ] Backup output is verified and interrupted operations recover safely.
- [ ] Database migrations and large-library tests pass.
- [ ] App supports phones, tablets, foldables, light/dark/AMOLED themes, and RTL layouts.
- [ ] The actual target device class has been profiled for memory, CPU, battery, and scroll performance.
- [ ] A feature-status document lists implemented, partial, unsupported, and future features honestly.

**Do not mark the project complete merely because all screens exist. Mark it complete only when the relevant acceptance criteria and tests pass.**

---

# 35. Official technical references

Use these official references during implementation and re-check them before release because Android behavior, policies, and supported codecs can change.

1. Android — Access media files from shared storage / MediaStore:  
   https://developer.android.com/training/data-storage/shared/media
2. Android — Partial access to photos and videos (Android 14 selected photos access):  
   https://developer.android.com/about/versions/14/changes/partial-photo-video-access
3. Android — Storage Access Framework and document access:  
   https://developer.android.com/training/data-storage/shared/documents-files
4. Android — Manage all files access and its restrictions:  
   https://developer.android.com/training/data-storage/manage-all-files
5. Android — Supported media formats:  
   https://developer.android.com/media/platform/supported-formats
6. AndroidX Media3 — ExoPlayer supported formats:  
   https://developer.android.com/media/media3/exoplayer/supported-formats
7. AndroidX Media3 — Image and motion photo support:  
   https://developer.android.com/media/media3/exoplayer/images
8. Android — CameraX:  
   https://developer.android.com/media/camera/camerax
9. Android — Camera extensions and device capability limits:  
   https://developer.android.com/media/camera/camera-extensions
10. Android — Google Play target API requirements:  
    https://developer.android.com/google/play/requirements/target-sdk
11. Apple Developer — Capturing still and Live Photos (useful for understanding common iPhone-originated media):  
    https://developer.apple.com/documentation/avfoundation/capturing-still-and-live-photos

**Policy note:** The Android documentation currently recommends the system Photo Picker for user-selected media and documents partial selected-photo access for custom gallery applications. Broad storage permissions are not a substitute for a correct SAF/MediaStore design. A custom Android system image can grant additional privileges, but those privileges do not exist for an ordinary installed APK.

---

**End of specification.**

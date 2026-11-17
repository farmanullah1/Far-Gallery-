# Far Gallery — Complete App (single file)

Contents: **Part A** spec v2 (sections 1–37) · **Part B** addendum (38–45) · **Part C** all source code · **Part D** icons and favicons (SVG/XML source).

---

# Part A — Product and Implementation Specification v2

# Far Gallery v2 — Product, Design, and Android Implementation Specification

**Document type:** Product requirements + design system + implementation brief for Google AI Studio / Android Studio
**Platform:** Native Android (Kotlin, Jetpack Compose, Material 3 + custom glass layer)
**Min SDK:** 30 (Android 11) · **Target SDK:** 36 (re-check Play Console requirements before each release)
**Operating model:** Local-first, offline, no account. Optional LibrePhotos sync in a separate build variant.
**Companion code files** (design implementation, drop into the packages shown):

| File | Package / location |
|---|---|
| `Theme.kt` | `ui/theme` — colors, glass tokens, type, shapes, motion |
| `Glass.kt` | `ui/glass` — backdrop-blur glass modifier + fallbacks |
| `GlassBottomBar.kt` | `ui/nav` — floating tab pill + camera button |
| `GalleryScreen.kt` | `feature/gallery` — Years/Months/Days/All grid, pinch, selection |
| `ViewerScreen.kt` | `feature/viewer` — pager, zoom, swipe-to-dismiss, details |
| `CameraScreen.kt` | `feature/camera` — iPhone-style camera UI + CameraX preview |
| `FilesScreen.kt` | `feature/files` — Apple Files + Ubuntu Files layout |
| `Feedback.kt` | `ui/feedback` — loading, empty, error, progress, undo |
| `AppErrors.kt` | `core/common` — typed errors, safe wrappers, incoming-media validator |
| `Onboarding.kt` | `feature/onboarding` |
| `FarLogo.kt` | `ui/brand` — in-app animated logo |
| `ic_launcher_*.xml`, `favicon.svg` | `res/drawable`, `res/mipmap-anydpi-v26`, web/Play assets |

---

## 0. What changed from v1

**Removed:** the Private Vault (screen, encryption, vault database tables, vault module, vault-specific secure-window rules, vault recovery flow). Nothing in the app is encrypted-and-hidden any more. "Hide from timeline" remains but is an exclusion filter only and the UI must say it is **not** a security feature.

**Redesigned:** glass UI inspired by Apple Photos and Google Photos; iPhone-style camera; Apple Files + Ubuntu Files (Nautilus) style file browser.

**Added:** Years/Months/Days/All library zoom levels, Memories (local rules, no cloud), Utilities collection, media-type albums (Selfies, Panoramas, Bursts, Slo-mo, RAW, Motion, Screen recordings), Live-Text-style OCR selection, QR/barcode detection, Recently Deleted, EXIF editor, GIF/PDF tools, predictive back, edge-to-edge, 16 KB page-size compliance, "Reduce transparency" fallback, full error taxonomy and incoming-media pipeline, a complete loading/progress/retry/cancel/fallback catalog, and a "things you may be missing" list (section 33).

**Kept (condensed):** local-first rules, MediaStore/SAF model, format policy, editor, duplicates, backup/restore, optional sync, testing.

---

## 1. Mandatory implementation rules

1. **Real functionality only.** Every control performs its stated action. No buttons that only animate or toast.
2. **No dummy data** in production. Empty states are real empty states. Test fixtures live only in test source sets.
3. **No silent downgrade.** If a feature is not implemented or is blocked by Android, show it disabled with the reason. Never label a placeholder "working".
4. **Preserve user files.** Never overwrite or permanently delete originals without an explicit user action and the Android consent flow. Editing saves a copy by default.
5. **Respect scoped storage.** MediaStore + SAF only. No assumption of unrestricted file access.
6. **Capability detection** before offering HDR, RAW, 4K60, slow-mo, extensions, PiP, etc.
7. **Core features work offline.** Local build has no `INTERNET` permission.
8. **No upload of media or metadata** without a separate, explicit opt-in (sync build only).
9. **No claim of universal format support.** Follow section 8.
10. **Build in phases** (section 32). Each phase compiles, runs and is tested before the next.
11. **Deliver a compiling Android Studio project**: Gradle version catalog, manifest, resources, Room migrations, tests, README with build steps.
12. **Ship `IMPLEMENTATION_STATUS.md`** marking every requirement Implemented / Partial / Platform-limited / Not yet, with evidence.

**Definition of complete:** UI + persistence + permission flow + error states + lifecycle behavior + tests + accessibility + user feedback all exist. A good-looking screen that doesn't do the work is not complete.

---

## 2. Product vision and principles

Far Gallery is a premium, privacy-first Android app that combines a photo library, a camera, and a file manager in one calm, fast, glassy interface.

- **Local-first:** device is the source of truth.
- **Privacy by default:** permissions requested in context, explained honestly.
- **Non-destructive:** edits, cleanup and metadata changes are reversible where possible.
- **Honest:** show Full / Selected / Folder / No access; show whether a format is native, optional-decoder, or preview-only.
- **Resilient:** one bad file never blocks the library or crashes the app.
- **Accessible:** TalkBack, font scaling, RTL (Urdu), reduced motion, reduced transparency.
- **Fast:** cached-first rendering, thumbnail-sized decodes, bounded memory.

---

## 3. Platform and device requirements

- `minSdk 30`, `compileSdk` = latest stable, `targetSdk 36` (verify Play requirement at release time; v1 stated API 36 for new submissions from 31 Aug 2026).
- Branch behavior by API level at runtime; test on API 30, 32, 33, 34, 35, 36.
- Edge-to-edge is mandatory (enforced at API 35+). Support predictive back (`android:enableOnBackInvokedCallback="true"`, `PredictiveBackHandler`).
- Native libraries (if any) must support **16 KB memory page size** and ship `arm64-v8a` (add other ABIs only when justified).
- Reference profile (test, not hardcode): Infinix, 6.78" AMOLED FHD+ 120 Hz, Helio G100, 8 GB RAM, UFS 2.2 + microSD, Android 14 / XOS 14.5, 50 MP main camera, 5000 mAh, in-display fingerprint. Detect refresh rate, memory class, camera capabilities and storage volumes at runtime. Do not force 120 Hz.
- Glass blur (RenderEffect) requires API 31+. On API 30 the glass falls back to a frosted opaque tint (section 5.4). This is expected, not a bug.

---

## 4. Architecture

Layers: Presentation (Compose, UI state, navigation) → Domain (use cases, policies) → Data (Room, MediaStore, SAF, Media3, CameraX, optional network).

Modules (or packages if AI Studio can't create multiple Gradle modules; never one giant file):

`:app` · `:core:common` · `:core:database` · `:core:media` · `:core:thumbnails` · `:core:permissions` · `:core:storage` · `:feature:gallery` · `:feature:albums` · `:feature:viewer` · `:feature:video` · `:feature:editor` · `:feature:camera` · `:feature:files` · `:feature:search` · `:feature:duplicates` · `:feature:backup` · `:feature:sync` (sync variant only) · `:feature:maps` · `:feature:widgets` · `:feature:onboarding` · `:core:designsystem` (theme, glass, feedback components).

(`:core:security` and `:feature:vault` from v1 are **removed**. A small `AppLock` helper using BiometricPrompt may live in `:core:permissions`; it is optional and gates the whole app, not a hidden area.)

Patterns: unidirectional data flow, `StateFlow` UI state, Kotlin coroutines/Flow, WorkManager for deferrable work, foreground service only for user-visible long operations. Long tasks use explicit states: `Queued → Running → Paused → Completed | Cancelled | Failed`.

Stack: Compose BOM, Material 3, Navigation Compose (with shared-element transitions), Room, DataStore, Paging 3, Coil 3 (thumbnails; video frames via coil-video), Media3, CameraX, Haze (backdrop blur, `dev.chrisbanes.haze`), Hilt or Koin, WorkManager, ML Kit **bundled** on-device models for OCR/barcode (not the Play-Services download variant, to keep first-run offline), Splash Screen API. Pin versions in a `libs.versions.toml`; check current stable releases and licenses; do not invent versions.

---

## 5. Design system (Apple Photos × Google Photos × glass)

### 5.1 Look in one paragraph
Content is the hero: edge-to-edge photos with 1–2 dp gutters, almost no chrome. Navigation, titles, toolbars and sheets float above the content as **frosted glass**: translucent, blurred, with a hairline highlight border and soft shadow. Light mode is white and airy (Apple Photos); dark mode is near-black with elevated charcoal glass (Google Photos dark); AMOLED mode is pure black. The accent is iOS-style blue by default (curated palettes or Material You optional). Motion is spring-based and quick.

### 5.2 Tokens (implemented in `Theme.kt`)

| Token | Light | Dark | AMOLED |
|---|---|---|---|
| background | #FFFFFF | #0B0B0F | #000000 |
| surface (cards, sheets) | #FFFFFF | #1C1C1E | #0A0A0A |
| surfaceVariant (grouped lists, placeholders) | #F2F2F7 | #2C2C2E | #141414 |
| onSurface | #000000 | #FFFFFF | #FFFFFF |
| onSurfaceVariant | #6C6C70 | #AEAEB2 | #AEAEB2 |
| accent (default) | #0A84FF | #0A84FF | #0A84FF |
| destructive | #FF3B30 | #FF453A | #FF453A |
| camera yellow | #FFD60A | #FFD60A | #FFD60A |
| record red | #FF3B30 | #FF3B30 | #FF3B30 |

Spacing on an 8 dp grid (4 dp for tight clusters). Shapes: small 8, medium 14, large 22, extra-large 28, pills fully rounded. Grid tiles: 2 dp radius (Apple-like), albums: 14 dp. Typography: system sans (Roboto Flex optional via downloadable fonts; do not bundle SF Pro — license). Scale: Large title 34/Bold, Title 22/SemiBold, Headline 17/SemiBold, Body 16, Caption 12. Respect system font scale.

### 5.3 Glass recipe
- **Bar glass** (nav pill, top title bar): blur ~24 dp, tint = surface @ 55–65%, 0.75 dp gradient border (white 45% top → white 8% bottom in dark; white 90% → black 8% in light), shadow 12 dp @ 12%.
- **Chip glass** (filter chips, badges, zoom bubbles): blur ~16 dp, tint @ 45%.
- **Sheet glass** (bottom sheets, menus, details): blur ~34 dp, tint @ 72%.
- **Overlay glass** (viewer top/bottom bars): blur ~44 dp, tint black @ 35%.
- Rule: never put glass over glass; never blur more than two layers deep (performance).
- Content under glass is registered once with a shared `HazeState` (`hazeSource`), each glass surface samples it (`hazeEffect`).

### 5.4 Fallbacks
- API 30, or blur disabled by low-power/thermal state, or system "remove animations"/our **Reduce transparency** setting → opaque tint at 94–96% with the same border. Layout identical, so nothing shifts.
- Never block UI on blur. If `RenderEffect` throws, switch to fallback permanently for that session and log a safe diagnostic code.

### 5.5 Motion
- Spatial springs (damping 0.82, stiffness ~380) for moves and resizes; 160–200 ms eased fades for opacity.
- **Grid → viewer:** shared-element transition of the tapped thumbnail expanding to the viewer image; background fades to black.
- **Viewer dismiss:** drag down scales image to ~85% and fades the background; release past threshold returns to the grid tile via shared element; otherwise springs back.
- **Tab switch:** selected-pill indicator slides (spring); content cross-fades with 6 dp vertical offset.
- **Pinch zoom grid:** columns re-flow with `animateItem` placement.
- **Favorite heart:** scale 1→1.25→1 with haptic tick.
- **Selection:** check badge pops in; tile dims and shrinks 4%.
- **Shutter:** inner circle scales to 0.9 and returns; screen flash 80 ms; thumbnail flies to the last-capture slot.
- **Reduced motion** (system animator scale = 0 or in-app toggle): replace springs with 0–80 ms fades; no parallax, no flights.
- Haptics: tab change, selection toggle, favorite, shutter, zoom-lens snap. Honor system haptic setting; in-app toggle.

### 5.6 Iconography and brand
- Outlined icons, 24 dp, 1.75 dp stroke weight; filled variant for selected state. Material Symbols Rounded or `material-icons-extended`.
- **App icon:** adaptive icon (gradient dusk-sky background, white mountain-and-sun mark in the safe zone, glass sheen) + **monochrome** layer for Android 13 themed icons + 512 px Play icon exported from the same vector. Files: `ic_launcher_background.xml`, `ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml`, `mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml`.
- **Favicon / small icon:** Android has no favicon. Use `favicon.svg` (and 32/48 px PNG exports) for the project website/portfolio/Play listing, and `ic_stat_far.xml` (single-color) for notifications.
- **In-app logo:** `FarLogo` composable (Canvas) used on onboarding, About, empty states; animates once (sun rises, mountains slide in) unless reduced motion.
- **Splash:** Android 12+ Splash Screen API with the logo; no custom full-screen splash activity.

---

## 6. Navigation and screen map

**Phone:** floating glass **tab pill** at the bottom with four tabs — **Photos, Albums, Search, Files** — and a detached circular glass **Camera** button to its right (mirrors iOS 26 Photos' detached search button). The bar hides on scroll down in grids and returns on scroll up, always visible in the viewer-less screens when idle for 1.5 s.

**Tablet / foldable / landscape ≥ 600 dp:** navigation rail (glass) at the left; ≥ 840 dp adds two-pane layouts (Albums list + album grid, Files three-pane, Viewer + details side panel).

**Route list:** Onboarding · Photos(Library) · Memories · Albums → Album detail · Collections (Media types, Utilities, Places, Tags, Recently Deleted) · Search · Files → Folder · Viewer · Video · Editor · Camera · Duplicates · Backup/Restore · Settings · About.

**Back behavior:** predictive back everywhere; Camera/Viewer/Editor back returns to originating screen with reverse shared transition; Editor with unsaved changes asks Save copy / Discard / Cancel.

**Deep links / shortcuts:** `farGallery://camera`, `farGallery://favorites`, `farGallery://search`.

---

## 7. Onboarding and first launch

Three short glass-card pages (Pager with dot indicator), skippable:

1. **Welcome** — animated `FarLogo`; "Your photos, videos and files. On your device. No account."
2. **What you get** — Library, Camera, Files, local search; one line each.
3. **Access** — two buttons: **Allow full library access** and **Choose photos to share** (limited). Plus a text button **Skip for now**.

Rules:
- No permission prompt appears before the user taps a button. Camera, location and notification permissions are asked later, in context.
- After tapping, Android's dialog appears; if the user chooses "Selected photos" the app shows the **Selected access** indicator and a **Manage** action (re-opens the system picker for more selection).
- If denied, the Photos tab shows a friendly empty state with "Open Settings" and "Pick photos with the system picker". The app never nags more than once per session.
- Re-check permission on every resume.

**Access indicator** (small glass chip at the top of Photos): Full · Selected · Folder access · No access · Volume unavailable. Tapping opens a sheet explaining what the app can see and offering the right next action.

---

## 8. Media formats and codecs (mandatory policy)

**Never map extension → decoder.** Detect MIME (provider) → sniff header → identify container/codec/profile → check decoder capability → bounded thumbnail attempt → fallback.

### 8.1 Images and animation

| Family | Handling | Limits |
|---|---|---|
| JPEG, PNG, WebP (static/animated), GIF, BMP | Platform `ImageDecoder`/Coil | Cap GIF/WebP frame count and memory; animated decode off main thread |
| HEIC/HEIF (iPhone default), HEIC sequences | Platform decoder when device supports the HEVC profile; else embedded thumbnail/preview | Support varies by device and bit depth |
| AVIF | Platform (Android 12+) baseline; test 10-bit/HDR separately | Not guaranteed everywhere |
| JPEG XL, JPEG 2000, TIFF, ICO, PSD preview | Optional decoder modules only after license/size review | Otherwise preview-unavailable card + Open with |
| DNG / ProRAW | Platform or RAW library; else embedded JPEG preview | iPhone-specific rendering not reproduced |
| Other RAW (CR2/CR3, NEF, ARW, RAF, ORF, RW2, PEF, SRW…) | Best-effort library; else embedded preview | Never claim universal RAW |
| SVG | Sanitize and rasterize (no scripts, no external refs) | Vector art, not photo |
| Ultra HDR / gain-map JPEG, HDR HEIC/AVIF | Render HDR where Android pipeline supports; else SDR base | Preserve original |
| Motion Photo (Google/Samsung/Pixel Top Shot), Android MP JPEG/HEIC | Detect XMP; show still + play embedded video when extractable | Layouts vary; test real samples |
| Apple Live Photo (exported HEIC + MOV pair) | Detect pairing by name/content ID; group; play MOV | No Apple Photos database behavior |
| Spatial photo/video (Apple Vision Pro), Cinematic mode video | Show as normal 2D photo/video; depth/focus edits not reproduced | State this in details |
| Panorama / 360 / Photo Sphere (equirectangular XMP) | Wide-pano pan view; optional 360 viewer module (OpenGL) | 360 viewer is its own deliverable |
| Burst sequences | Group by filename/timestamp pattern; "Bursts" album | Heuristic |

### 8.2 Video and audio
- Containers: MP4/M4V, MOV, MKV, WebM, 3GP, MPEG-TS, AVI where Media3 extractors support it.
- Codecs to test: H.264, H.265/HEVC (iPhone default), VP8/VP9, AV1, MPEG-4 Part 2, ProRes (only if device/optional decoder supports), HDR10/HDR10+/HLG, Dolby Vision profiles the device exposes.
- Audio in video: AAC, MP3, Opus, FLAC, AC-3/E-AC-3 (device-dependent), multi-track selection.
- Subtitles: embedded tracks + external SRT/VTT/TTML/SSA via SAF.
- iPhone **slo-mo** MOV (variable frame rate with speed ramp) plays at the file's effective timing; the exact in-app speed ramp may not be reproduced — shown in details as "Slow-motion video (speed ramp may differ)".
- Check `MediaCodecList` before playing; surface the real reason on failure.

### 8.3 Audio-only files (Files tab)
Identify MP3, AAC/M4A, WAV, FLAC, OGG/Opus, MIDI; offer a mini preview player (Media3) and **Open with**. Not a music-library feature.

### 8.4 Fallback ladder (per file)
1. Full decode → 2. Down-sampled decode → 3. Embedded preview (RAW/HEIC thumbnail) → 4. Metadata-only card (name, type, size, date) → 5. **Open with…** / **Share original**. Never rename, convert or delete automatically. Log a safe diagnostic code only.

### 8.5 iPhone media
Android cannot read an iPhone's Photos library or iCloud-only originals. Users move files by cable, SD, shared folder or cloud download; Far Gallery recognizes the resulting HEIC, HEVC MOV/MP4, ProRAW DNG, Live Photo pairs, ProRes MOV (when decodable) and JPEG exports. An **Import from iPhone** help page explains the transfer options.

---

## 9. Photos tab (Library)

### 9.1 Look
Large title **Library** (collapses to a glass title bar on scroll). Under it, a glass **segmented control: Years · Months · Days · All** (Apple Photos). Quick filter chips (glass) below. Grid is edge-to-edge with 2 dp gutters. Floating tab pill at bottom.

### 9.2 Interaction
- **Pinch** to change density (2–6 columns in *All*); items animate to new positions.
- **Years / Months / Days:** collage-style tiles representing a period; tap a tile to zoom into that period in the next level (animated). *All:* every item, grouped by date headers (Today, Yesterday, weekday, Month Year).
- **Tap** an item: shared-element open in Viewer.
- **Long-press** an item: selection mode starts; subsequent taps toggle; **drag across** items to range-select (hold then slide).
- **Selection toolbar** (glass, replaces title): count ("3 selected" announced by TalkBack), Share, Favorite, Add to album, Delete, More (tag, move, export, edit date).
- **Fast scroller:** a draggable date rail on the right with a floating month/year bubble.
- **Pull to refresh:** triggers an incremental MediaStore reconcile; shows a small "Updated just now" glass toast.
- **Sort:** capture date (default), date added, date modified; fallback to added/modified when EXIF is missing.
- **Filters (only those with content):** All, Photos, Videos, Favorites, Recently added, Recently viewed, Camera, Screenshots, Screen recordings, Downloads, Large files, GIFs/animations, Documents.
- **Badges:** video duration pill, motion/Live icon, RAW tag, favorite heart, burst stack, unavailable (dimmed with cloud-off style icon for removed volumes).

### 9.3 Behavior
- Cached Room metadata renders first (< 300 ms), MediaStore reconciles in background.
- Thumbnails are requested at tile size, prioritized for visible items, cached (bounded memory + disk).
- One failing thumbnail shows a placeholder for that tile only.
- Paging 3 for all queries; stable keys; `contentType` set for item types.

### 9.4 Memories (local only)
Rule-based, no cloud, no face analysis: "On this day", "Trip to <place>" (from clustered GPS + dates when permitted), "Recent highlights" (favorites + high resolution + non-duplicate). Presented as a horizontally scrolling card carousel at the top of Albums or Photos. Tap plays a 15-second slideshow with crossfade (no music). Each memory can be removed ("Show fewer like this") and the Memories feature can be disabled in Settings with an index-delete button.

---

## 10. Albums and Collections tab

- **Albums grid** (2 columns; 3–4 on tablets): rounded 14 dp covers with title and count under each. Pinned section first.
- **Collections:** Media Types (Videos, Selfies, Panoramas, Bursts, Slo-mo, Time-lapse, Screenshots, Screen recordings, Motion/Live, RAW, GIFs), Utilities (Duplicates, Documents, Receipts, QR codes, Imports, Large files, Recently Deleted), Places (map), Tags. Show a collection only if it has items.
- **Actions:** New album (name + optional description), rename, delete album (media is not deleted — dialog says so), pin, custom cover from album's own media, reorder (drag handles in edit mode), exclude folder from timeline (confirm dialog: "Files are not moved or deleted. This is not a security feature").
- **Logical albums vs physical folders** are shown in separate sections, with an SD-card badge on removable folders and a "Unavailable" badge when the volume is missing.
- **Album detail:** large title, same grid as Library, **Slideshow** and **Add** actions, "Remove from album" vs "Delete from device" are separate commands.
- **Smart albums** (rule-based): user picks rules (type, date range, size, tag, folder, favorite); auto-updates.

---

## 11. Viewer

### 11.1 Look
Full-bleed content on black (or dim/theme surface per setting). Top **overlay glass** bar: Back, date/location title (two lines), Favorite, More. Bottom glass bar: Share, Edit, Info (i), Delete. A thin filmstrip appears above the bottom bar for quick scrubbing in albums. Bars auto-hide after 3 s on tap-to-toggle; TalkBack keeps them reachable.

### 11.2 Gestures
Swipe left/right to move; pinch + double-tap zoom (fit ↔ 2.5×, up to 6×); pan when zoomed (horizontal drags pan, pager disabled while zoomed); swipe down to close (scale + fade); swipe up or tap (i) for the **Details sheet**; long-press a Live/Motion photo to play; tap to toggle chrome. Gestures can be reduced in Settings.

### 11.3 Actions
Favorite, Share (with metadata privacy sheet), Edit, Add to album, Delete (to Recently Deleted), Set as wallpaper (system flow), Open with, Copy, Slideshow, Rotate view (non-destructive, optional "Save rotation"), Print (system print), Export/convert (format+quality dialog), Extract text (Live Text), Detect QR, Frame grab (video), "Compare with original" (hold on edited copy).

### 11.4 Details sheet (glass sheet, half-height then full)
File name & type, dimensions, size, date taken/modified, camera/lens/exposure, HDR/color info, storage volume/folder, location with small map **only if opted in and permitted**, tags, albums, codec info for video. Missing values are omitted, never invented. Distinguish "Not available" from "Permission not granted" (with a Grant button).

### 11.5 Live Text (OCR in viewer)
Long-press on text in an image: highlight word boxes (on-device ML Kit bundled), drag handles to select, actions Copy / Share / Search / Translate (if translate app present) / Call / Open link / Add to contacts via system intents. Only when OCR is enabled; first use shows a one-line explanation. No network.

### 11.6 Slideshow
Interval 2–10 s, fade or none, loop, follows current sort/album; screen-on only while running (cleared on exit/pause).

---

## 12. Video

- Media3 player with glass controls: play/pause, seek bar with time labels, ±10 s skip, speed (0.5/1/1.5/2×), audio track, subtitles, rotate/fullscreen, PiP (when supported), mute, frame grab, trim, share, delete.
- Resume position (toggle). Double-tap left/right to skip. Optional brightness/volume edge swipes (off by default, with a discoverability hint).
- **Trim:** two-handle timeline with thumbnails; accurate preview; **Save as copy** (default) with progress notification-style card, Cancel button; output verified before listing.
- **Frame grab:** pick time → save JPEG/PNG copy → toast with View.
- **Failure ladder:** stop and release player → friendly card ("This video uses a codec or profile this device can't play. The original file is unchanged.") with Retry · Details · Open with · Share original. App stays responsive.

---

## 13. Editor (non-destructive)

**Look:** full-screen preview; glass bottom toolbar with tool icons (Adjust, Crop, Filters, Markup, Text, Blur/Redact, More); glass slider panel above it; top bar Cancel · Undo · Redo · **Save** (menu: Save copy (default), Save as…, Replace original).

**Tools:** crop with presets (Original, 1:1, 4:3, 3:2, 16:9, custom) + straighten wheel + rotate/flip; adjustments (exposure, brightness, contrast, saturation, warmth, tint, highlights, shadows, sharpness, vignette); filters with intensity; markup (pen, marker, arrow, shapes, text); blur/pixelate/**redact** (flattened on export so pixels are unrecoverable); perspective + **document scan** (deskew, B/W, enhance); watermark; resize; compress with size preview; collage/stitch; batch edit with confirmation; GIF maker from selected photos/video segment; Image → PDF; EXIF editor (date, location, with explicit consent; originals untouched unless chosen).

**Model:** edit recipe stored in Room (`EditRecipe`: source id, source checksum, ordered operations, version, output URI). Reopen to continue. Bounded/tiled rendering; never hold multiple full-res bitmaps.

**Export:** JPEG/PNG/WebP, quality only for lossy, output dimensions and estimated size shown, EXIF keep/strip choice, warnings for lost HDR/animation/RAW data. A RAW is never silently flattened and relabeled "RAW".

**Errors:** undecodable → editor disabled with reason; out-of-memory → auto lower preview resolution; save failure → recipe kept, retry offered.

---

## 14. Camera (iPhone Camera look)

### 14.1 Layout (portrait)
- Black background; viewfinder with rounded 20 dp corners between a top and a bottom control zone (4:3 default, 16:9 fills; 1:1 shows black bars).
- **Top bar:** Flash (bolt) · chevron ˄ (expands a second row) · Motion/Live toggle (only when implemented) · … The expanded row (glass) has Timer, Aspect ratio, Exposure, HDR, Flash mode, Grid.
- **Lens chips** above the shutter: 0.5× · **1×** · 2× (only lenses/ratios the camera reports). Selected chip is yellow and larger; tap animates zoom; long-press opens a **zoom dial** arc.
- **Mode carousel** (horizontal text, selected in yellow, swipe to change): **Slo-mo*, Video, Photo, Square, Portrait*, Night*** (*only if device/extension supports it). Time-lapse is a later phase.
- **Shutter row:** last-capture thumbnail (bottom-left, rounded), **shutter** (white ring 72 dp with 60 dp inner circle), **flip camera** button (bottom-right, rotates 180° on tap).
- **Video mode:** shutter inner circle turns red; on record it morphs to a red rounded square, a red **timer pill** appears at top center, a small white shutter appears to capture stills during recording only if device supports it.

### 14.2 Interaction
- **Tap to focus:** yellow square appears (scale 1.4→1, 120 ms), holds 1.5 s, then fades; sun icon + vertical slider beside it adjusts exposure (drag up/down) via `setExposureCompensationIndex`, clamped to the reported range.
- **Pinch** to zoom; zoom value bubble (e.g. "3.2×") appears for 1 s.
- **Long-press shutter** (Photo): burst where supported, counter badge; release stops. (Video mode: lock/hold behavior optional.)
- **Timer:** 3/5/10 s with a large countdown overlay and subtle haptic ticks; cancel by tapping the shutter again.
- **Volume keys** can trigger the shutter (setting).
- **Mirror selfie** toggle; **flip animation** is a short blur-and-rotate of the preview.
- **After capture:** shutter flash, thumbnail flies into the bottom-left slot; tapping it opens the Viewer scoped to "Just captured" with Edit shortcut. The new item appears in Library immediately (inserted via MediaStore with `IS_PENDING` semantics, then indexed incrementally).
- **Orientation:** UI icons rotate with device orientation like iOS; the viewfinder stays fixed.

### 14.3 Capability and error behavior
- Query `CameraInfo`/`CameraCharacteristics`: zoom range, flash, torch, max resolution, extensions (HDR, night, portrait, bokeh), video qualities, high-speed ranges, stabilization. Do not offer unsupported options; show them disabled with a reason in settings.
- Do not assume the 50 MP sensor or all lenses are exposed to third-party apps.
- Permission denied → camera screen shows a glass card with **Allow camera** / **Open Settings**; rest of the app unaffected. Microphone denied → video records without audio only after a confirmation line "Recording without sound".
- Camera busy/unavailable → card with Retry. App backgrounded during recording → recording stops safely, file is finalized and kept. Low storage → warn before recording; stop safely at threshold. Thermal throttling → show "Camera is cooling down", reduce to safe mode, never crash.
- Storage: save to `DCIM/Camera` (or user-chosen SAF folder) through MediaStore; EXIF orientation and optional location (only with permission and user toggle).

---

## 15. Files tab (Apple Files × Ubuntu Files)

### 15.1 Phone layout (Apple Files style)
- Large title **Files**, glass search field, then grouped lists with rounded inset cards:
  - **Locations:** Internal storage (with usage bar), SD card (if present), USB/OTG (if exposed), Downloads, Documents, Pictures, Movies, Music.
  - **Favorites:** user-pinned folders.
  - **Recents:** last opened files (horizontal thumbnails).
  - **Tags:** colored tags (user-defined, stored in app database).
- Tapping a location opens the folder view (push transition). Title becomes the folder name with a breadcrumb in the glass title bar. Back chevron shows parent name (like iOS).

### 15.2 Tablet / desktop-mode layout (Ubuntu Files / Nautilus style)
Three zones, glass chrome:
1. **Sidebar "Places"** — Recent, Starred, Home (Internal), Documents, Downloads, Pictures, Videos, Music, Trash (Recently Deleted), then "Other Locations": SD card, USB, "Add folder…" (SAF grant). Active item highlighted with a rounded accent pill; mounted external volumes show an eject/safely-remove action where Android allows.
2. **Main pane** — toolbar with back/forward, an **editable path bar** (breadcrumb pill; tap the pill or press Ctrl+L to type a path/search; chevron segments open sibling-folder popovers), **view toggle (grid / list)**, **sort menu**, **new folder**, **show hidden files** toggle (Ctrl+H). List view has Ubuntu-style sortable **column headers: Name · Size · Type · Modified**; zebra rows are off by default; drag a column edge to resize. Grid view has an icon-size slider.
3. **Inspector/Properties pane (optional)** — preview, name, type, size, modified, path, permissions line (read/write as exposed by provider), tags, Open with.

### 15.3 Interactions (touch, keyboard, pointer)
- Tap open · long-press = select + context menu · rubber-band selection with pointer/stylus · two-finger/Ctrl+click multi-select · Shift range select.
- **Context menu** (glass popover): Open, Open with, Share, Copy, Move, Rename, Duplicate, Compress, Extract (ZIP), Star/Favorite, Tag, Properties, Move to Recently Deleted / Delete.
- **Keyboard (hardware keyboard):** F2 rename, Del delete, Ctrl+C/X/V, Ctrl+A, Ctrl+Shift+N new folder, Ctrl+L location bar, Ctrl+H hidden, Backspace/Alt+↑ parent, Enter open, Alt+←/→ back/forward, Ctrl+T new tab (tablet).
- **Drag and drop** between panes/folders and to other apps in multi-window where Android allows.
- **Tabs** on tablets (up to 6), each with its own folder history.
- **Inline rename:** name field in place, extension preserved by default; collision shows inline error.

### 15.4 Operations and safety
Operations: open, share, rename, copy, move, delete/trash, create folder, ZIP create/extract, batch rename (pattern + preview + numbering), properties, favorites, tags.
- Verify permissions on source and destination before starting; detect collisions → **Rename / Replace (with warning) / Skip / Cancel** (apply to all option).
- Progress sheet (glass): per-file name, overall bar, speed/ETA, **Cancel** and **Pause** where possible. Cancel cleans partial output and keeps sources.
- Cross-volume move = copy → verify size/hash → delete source only after confirmation.
- If a provider doesn't support an action it is disabled with a reason; never pretend success.
- ZIP: preview contents, extract selected, reject `../` and absolute paths, entry-count/size/ratio limits (archive-bomb protection). 7z/TAR/RAR only through a reviewed library.
- `MANAGE_EXTERNAL_STORAGE` is not requested in the standard build.

### 15.5 Storage dashboard
Used/free for internal and SD (as exposed), breakdown by type, large files, large videos, old media, empty folders, unreadable/corrupt media report, cache size with exact description of what Clear cache removes. Nothing is auto-labelled "junk" or auto-deleted.

### 15.6 File icons
Folder icon: soft blue rounded folder (Apple) by default; "Yaru" orange/purple folder style available as an icon-pack setting. Type icons: document, spreadsheet, PDF, archive, audio, code, unknown. Images/videos show thumbnails.

---

## 16. Search, OCR, and on-device intelligence

- **Search tab:** large glass search field at the top; below it: recent searches (clearable), suggestions from *real* indexed categories (people-free), saved searches, and filter chips (type, date range, album, size, duration, favorite, tag, location presence).
- Query examples that work through filters or simple date syntax: "photos January 2025", "videos > 500 MB", "screenshots last month", filename fragments, album names, text inside images (OCR).
- Results are a grid with highlighted match chips ("Filename", "Text in image", "Tag").
- Distinguish: **No results** vs **Still indexing (42%)** vs **Limited by permission**.
- **OCR index:** bundled ML Kit; WorkManager job on charging/idle by default (user can run now); progress card with Pause/Resume/Cancel; **Delete OCR index** button; text stays local.
- **QR/barcode:** detected on-device in viewer; action chip opens link/Wi-Fi/contact via system intents after user confirmation.
- **Optional AI (later, opt-in):** scene tags, smart albums, semantic search with a local embedding model, face grouping (separate privacy review; never named, never uploaded). Every AI feature shows model size, battery impact and has delete-index.
- **Google Lens:** "Search with Lens" only if a handler exists.

---

## 17. Places (maps)

Map view of geotagged media using `ACCESS_MEDIA_LOCATION` only after opt-in. Offline-first: if tiles are unavailable (local build has no network), show a grouped list by coordinates/region instead of a broken map. Reverse geocoding only when an on-device dataset exists. "Strip location on share/export" is always one tap away.

---

## 18. Sharing, incoming media, pickers

### 18.1 Share out
System Sharesheet, `content://` URIs via FileProvider, temporary read grants, single/multiple. A **share sheet preview** (glass) shows thumbnails and an **Options** link: Include location (off by default), Include camera info, Original vs Edited, Resize. Stripping metadata generates a temporary copy; source untouched; temp copies are deleted after sharing completes or at next launch.

### 18.2 Incoming media (how the app avoids crashes)
Supported intents: `ACTION_VIEW`, `ACTION_SEND`, `ACTION_SEND_MULTIPLE`, `ACTION_PICK`/`GET_CONTENT` (picker role). Pipeline (implemented in `AppErrors.kt` as `IncomingMediaResolver`):
1. **Validate action** and extract URIs from `data`, `EXTRA_STREAM`, `ClipData` (cap at 500).
2. **Validate scheme:** accept `content://`; reject `file://` and unknown schemes with a friendly message.
3. **Query** `OpenableColumns` (name, size) in a `try/catch`; treat all values as untrusted.
4. **Open read-only**; sniff the first bytes (magic numbers) and compare with the declared MIME; mismatch → trust the sniffed type, warn on details.
5. **Bounded preview decode** off the main thread with timeout (10 s) and memory cap.
6. Show **Incoming sheet**: View, Save to Library (copy into `Pictures/Far Gallery` via MediaStore), Add to album, Edit copy. Never modifies the sender's URI.
7. Failures per item are collected; successful items still open. A summary line says "2 of 5 could not be opened — details".
8. Expired/revoked grants and failing providers are caught (`SecurityException`, `FileNotFoundException`, `IllegalStateException` from provider) and shown as "The sending app is no longer sharing this file".
9. Activity is `singleTop`/`documentLaunchMode`-safe: a new intent while open updates state rather than recreating the stack; process death restores the pending intent list from saved state.

### 18.3 Pickers
Expose a Far Gallery picker for apps invoking `GET_CONTENT`/`PICK`; consume Android Photo Picker for one-off imports. Returned URIs carry temporary read grants.

### 18.4 Print and wallpaper
System print framework and wallpaper chooser; clear message when no handler exists.

---

## 19. Delete, trash, undo

Three distinct actions:
1. **Remove from album** (reference only).
2. **Move to Recently Deleted** (MediaStore trash where available, otherwise app-managed trash only if permitted; else the dialog says deletion is permanent and requires system confirmation).
3. **Delete permanently** (system `createDeleteRequest`/`createTrashRequest` consent; never bypassed).

Recently Deleted: shows days remaining (30 default, configurable), Restore, Delete now, Empty (typed confirmation). Undo snackbar (glass, 6 s, with real restore). Deleted items vanish from all lists immediately via optimistic UI and roll back on failure with an explanation.

---

## 20. Duplicates and similar media

Exact: size → streaming SHA-256. Similar: perceptual hash at preview size; videos approximate by duration + frame fingerprints. Review UI: groups as horizontal cards, **Best** badge only as a suggestion, per-item info (path, size, resolution, dates, favorite/album membership). Safety: never auto-delete, never delete all items of a group, favorites/album members protected by default, results cached with timestamps, work paused when battery low/thermal hot.

---

## 21. Backup and restore

Types: Settings/metadata (albums, tags, favorites, recipes, saved searches), Full local (selected media + metadata), Incremental, Encrypted (password; authenticated encryption with a vetted library/KDF — encrypted *backup files*, unrelated to any in-app vault). Destination via SAF (internal, SD, USB). Flow: choose → estimate → manifest → stream → temp file → verify hashes → atomic finalize → keep previous verified backup if failure. Restore: preview → selective → collision rules → verify → resumable. Progress card with Cancel; failure reasons: destination removed, permission revoked, low storage, wrong password, corrupt archive, version mismatch.

---

## 22. Optional LibrePhotos sync (sync build only)

Separate flavor `sync` with `INTERNET`; flavor `local` has no networking code and no INTERNET permission (verify merged manifest in CI). TLS validation never disabled; credentials in encrypted DataStore/Keystore-backed storage; user chooses albums; conflict policy: non-destructive conflict copies + review screen; deletions never propagate until the user picks a policy; adapter isolated behind an interface; tested against a supported server version. Sync is not a backup.

---

## 23. Widgets, shortcuts, integrations

Glance widget (recent/favorites from a chosen album; no unavailable media), shortcuts (Camera, Favorites, Search), optional Quick Settings tile for Camera, notifications only for long operations, themed monochrome icon, per-app language (English, Urdu). Ordinary APK limits: cannot replace Google Photos/DocumentsUI/Photo Picker system-wide; custom ROM integration is a separate deliverable.

---

## 24. Data model (Room)

`MediaItem`, `Album`, `AlbumMediaCrossRef`, `Favorite`, `Tag`, `MediaTagCrossRef`, `FolderGrant`, `EditRecipe`, `TrashEntry`, `SearchHistory`, `SavedSearch`, `OcrIndex`, `DuplicateGroup`, `DuplicateCandidate`, `BackupManifest`, `BackupItem`, `MemoryCard`, `FileTag`, `OperationRecord`, `SyncRecord` (sync flavor). **No VaultItem.**

Rules: stable IDs (not display names), URIs are revocable references, nullable metadata (never invented), indexes on sort/filter columns, transactions for multi-table writes, migration tests for every version, **never overwrite the DB on migration failure** (keep a copy and report), no media blobs in Room.

---

## 25. Indexing, thumbnails, caching

Show UI immediately from cache; page MediaStore in; enrich (hash, OCR, memories) as separate WorkManager jobs; register `ContentObserver`; use MediaStore version/generation to detect when to full-resync; never hold a transaction while decoding. Thumbnail cache key = id + dateModified + size; bounded (default 256 MB disk, ~15% of memory class in RAM); one failure per tile never breaks the grid. Test at 10k, 25k, 50k items.

---

## 26. Permissions matrix

| Android | Library access | Notes |
|---|---|---|
| 11–12 (30–32) | `READ_EXTERNAL_STORAGE` + MediaStore; SAF | Modifications need system consent |
| 13 (33) | `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO`; Photo Picker | Request only needed types |
| 14+ (34–36) | Above + `READ_MEDIA_VISUAL_USER_SELECTED` | Handle "Selected photos"; recheck on resume |

Other: `CAMERA` and `RECORD_AUDIO` in camera only; `ACCESS_MEDIA_LOCATION` only on opt-in; `POST_NOTIFICATIONS` only when an operation needs it; `MANAGE_MEDIA` only if user enables; **no** `MANAGE_EXTERNAL_STORAGE` by default; `INTERNET` only in sync flavor. Always check current grants; never assume.

---

## 27. Storage volumes (SD, USB OTG)

Detect volumes; MediaStore for indexed media, SAF for the rest; persist URI grants; mark items unavailable (not deleted) when a card is removed; revalidate on reconnection; handle read-only, full, failing media; warn before large writes to slow/near-full cards; cross-volume operations stream and verify.

---

## 28. Error handling and crash resilience

### 28.1 Principles
- No I/O, decoding, hashing or heavy DB work on the main thread.
- Failures are values: repositories return `FarResult<T>` (see `AppErrors.kt`); expected exceptions are mapped to a typed `FarError`; `CancellationException` is always rethrown; `OutOfMemoryError` is caught only around bitmap/decoder calls and triggers downsampling + cache trim; everything else propagates to the global handler.
- Every cursor, stream, file descriptor, player, camera session and temp file is closed with `use {}`/`finally`.
- Batch operations isolate each item; one failure never aborts the rest unless integrity requires it.
- Global `Thread.UncaughtExceptionHandler` writes a privacy-safe crash note (error class, code, app version, API level; no filenames, GPS, OCR text) to local diagnostics; on next launch a glass sheet offers **Export diagnostics** or dismiss. Never auto-upload.
- Crash-loop protection: if the app crashed within the last 3 launches during startup indexing, start in **Safe mode** (no background enrichment, no thumbnails prefetch, optional cache clear) and say so.

### 28.2 Error taxonomy → user experience

| Error | Detection | What the user sees | Actions |
|---|---|---|---|
| Corrupt image | decode throws / null | Placeholder tile; viewer card | Details · Share original · Open with |
| Unsupported format/profile | capability check | "This device can't display this format. File unchanged." + embedded preview if any | Open with · Share · Details |
| Corrupt/unplayable video | `PlaybackException` | Player card (section 12) | Retry · Details · Open with |
| Missing file | `FileNotFoundException` | Tile dims, "File no longer available" | Rescan · Remove listing |
| Permission revoked | `SecurityException` / grant check | Access chip changes; sheet explains | Grant · Open Settings · Pick photos |
| Partial access | permission state | "Showing selected photos only" chip | Manage selection |
| SD card removed | volume event / IOException | Items dimmed; operation cancelled safely | Reinsert · Reselect folder |
| SAF provider gone | `IllegalStateException`/null | "Folder provider unavailable" | Reselect folder |
| Low storage | `StatFs` before/during | "Not enough space. Need 1.2 GB, have 300 MB." | Choose other destination · Free space |
| Out of memory | `OutOfMemoryError` | Automatic lower-res retry; if still failing, preview unavailable | Details |
| MediaStore query failure | exception | Cached data with "Out of date" chip | Retry |
| DB migration failure | Room exception | Recovery screen; old DB preserved | Retry · Export diagnostics |
| Interrupted copy/move | operation journal | "Interrupted. Source files are safe." | Resume · Clean up |
| Interrupted backup/restore | journal | "Backup incomplete. Previous backup kept." | Resume · Restart |
| Camera unavailable/busy | `CameraState.Error` | Glass card in Camera | Retry |
| Camera permission denied | grant state | Permission card | Allow · Settings |
| Sync server unreachable | network exception | Banner in Sync settings; gallery unaffected | Retry · Pause |
| Invalid incoming URI | resolver | "Can't open this item" with reason | Close |
| Malformed archive | zip validation | "Archive contains unsafe or damaged entries" | Close |
| Unknown | catch-all at boundary | "Something unexpected happened (code FG-xxx). Your files are unchanged." | Retry · Export diagnostics |

Message formula: **what happened → is my file safe → what can I do.**

### 28.3 Retry, cancel, fallback rules
- **Retry:** exponential backoff for automatic retries (max 3) on transient failures (provider busy, thermal); manual **Retry** always available; retries never repeat destructive actions after process death without asking.
- **Cancel:** every operation > 1 s shows Cancel; cancellation is cooperative (coroutine cancel), cleans partial outputs idempotently, never touches sources.
- **Fallback:** follow the ladder in 8.4; blur → opaque; map → list; thumbnail → placeholder; high-res decode → down-sampled; extension camera mode → normal photo.
- **Process death:** operations persist state to `OperationRecord` before starting; at launch unfinished items are marked "Interrupted" with Resume / Dismiss.

---

## 29. Loading, progress, and feedback catalog

| Situation | Pattern |
|---|---|
| First paint of grid | Skeleton shimmer tiles (gray pulse, 1.2 s; static when reduced motion) only until cache/MediaStore answers |
| Initial indexing | Slim glass progress chip "Indexing 12,400 of 38,000" under the title; tap for details with Pause |
| Pull to refresh | Spring indicator + "Updated just now" |
| Thumbnail loading | Soft fade-in 120 ms from surfaceVariant |
| Single operation (share prep, export) | Determinate circular progress inside a glass card, Cancel |
| Batch (copy, delete, backup) | Bottom glass **Progress sheet**: title, current item, bar, "3 of 48", ETA, Cancel/Pause |
| Success | Glass toast with action (View, Undo); auto-dismiss 4–6 s; announced to TalkBack |
| Failure | Inline **ErrorCard** (not a toast) with Retry/Details |
| Empty | Illustration-free icon + title + body + primary action |
| Offline-only features | Never show "offline" errors in local flavor |

Every progress surface exposes `progressBar` semantics and announces milestones (25/50/75/100%) politely.

---

## 30. Accessibility, localization, responsive layout

- **TalkBack:** tiles read "Photo, Nov 4 2026, favorite, selected"; selection count announced; custom actions (Favorite, Share, Delete) on tiles; viewer controls always reachable; playback state announced.
- **Touch targets** ≥ 48 dp; focus order logical; hardware-keyboard navigation with visible focus rings; Switch Access and Voice Access labels.
- **Font scale** up to 200% without clipping (bars switch to icon-only labels if needed).
- **Contrast:** meets WCAG AA; glass tint auto-increases opacity when content behind is bright (light) or when "Increase contrast" is on.
- **Reduce transparency / reduce motion / haptics** switches in Settings, defaulting from system values.
- **Color is never the only signal** (icons + text for states).
- **RTL/Urdu:** mirrored navigation order, text alignment, directional icons; never mirror photos or file paths; mixed-script filenames use bidi isolation; Urdu dates/numerals per locale; verify translations with a native reviewer.
- **Responsive:** Compact (<600 dp): bottom pill; Medium (600–839): rail + wide grid; Expanded (≥840): rail + two/three panes; foldables: avoid hinge, tabletop camera layout; landscape camera moves controls to the right edge like iPhone.
- **Multi-window, desktop mode, stylus, mouse hover states** supported.

---

## 31. Privacy, security, logging

- No account; no ads; no analytics; local build has no network.
- Minimal manifest; no obsolete storage permissions.
- Share via `content://`; no raw paths exposed; imported files and archives treated as untrusted; SVG sanitized; embedded metadata never executed.
- App-private caches/databases excluded from Android auto-backup where they would leak OCR text or location (use `dataExtractionRules`).
- Optional **App Lock** (biometric/device credential) that gates the entire app on launch/resume; this is not a vault.
- **Logging:** structured, leveled, local ring buffer (≤ 2 MB); fields = timestamp, component, error code, API level. Forbidden in logs: filenames, URIs with identifiers, EXIF/GPS, OCR text, credentials, file contents. Release builds log WARN+ only. Diagnostics export is user-initiated and previewable before sharing.
- **Data controls:** Clear cache (explains what's removed), Delete OCR/memory indexes, Reset app (keeps media), Export/Import settings.
- Sync flavor discloses exactly what leaves the device and how to disconnect.
- Play listing: Data safety form = "no data collected" for local flavor.

---

## 32. Performance requirements

- Cold start to first visible grid from cache: target ≤ 1.2 s on reference device (measure, then fix the budget from data).
- Grid scroll: no frame over 16.6 ms (60 Hz) / 8.3 ms (120 Hz) for > 1% of frames in a 50k library; verify with Macrobenchmark + JankStats.
- Memory: grid thumbnails ≤ tile size × density; viewer decodes ≤ screen-size ×2 with tiled zoom; bitmap pool; peak heap watched in tests.
- Baseline Profiles + Startup Profiles shipped; R8 full mode; Compose stability checked (`@Immutable` UI models, lambdas remembered).
- Glass cost control: max two simultaneous blur surfaces on screen; blur radius lowered or disabled when Battery Saver, thermal status ≥ MODERATE, or frame time exceeds budget for 2 s (auto-fallback with hysteresis).
- Background work: WorkManager constraints (charging/idle/unmetered for heavy jobs), pause on thermal/low battery, no polling, no wake locks beyond active operations.
- Disk: thumbnail cache limit configurable; automatic trim on low storage.
- Do not claim performance numbers without device measurements.

---

## 33. What you may be missing (now included)

Live Text selection · QR detection · Memories · Recently Deleted · Utilities collection · media-type albums · date rail scrubber · range-drag selection · predictive back · edge-to-edge · themed icon · per-app language · 16 KB page-size compliance · HDR/Ultra HDR gain-map handling · Motion/Live photo grouping · Panorama/360 handling · Burst grouping · slow-mo/time-lapse awareness · Cinematic/Spatial fallback · EXIF editor · GIF/PDF tools · batch rename · Reduce-transparency and Increase-contrast support · safe mode after crashes · operation journal · incoming-media validator · hardware-keyboard/mouse/stylus support · tablet tabs and drag-drop · Baseline Profiles · accessibility custom actions · data-safety/privacy docs · Chromecast/cast is intentionally **not** included in the local flavor (needs network).

**Still impossible for a normal APK (be honest in the app's About page):** reading an iPhone's Photos library/iCloud originals, replacing system Photo Picker/DocumentsUI, unrestricted access to other apps' private data, guaranteeing every codec/RAW on every device.

---

## 34. Testing strategy

**Matrix:** API 30, 32, 33, 34, 35, 36; low-RAM device; second OEM skin; tablet/foldable emulator; SD-card scenario; RTL (Urdu); 200% font; TalkBack; dark/light/AMOLED.

**Unit:** error mapping, format sniffing, sort/group logic, collision resolver, zip validator, edit recipe replay, operation journal state machine, permission state machine, incoming resolver.
**Instrumentation/UI (Compose):** onboarding permission paths, grid selection, pinch, viewer gestures, camera permission-denied, files operations, theme switching, predictive back.
**Fuzz/reliability:** truncated files, wrong extensions, huge images, malformed EXIF/SVG/ZIP, revoked grants, read-only storage, process kill at each step of copy/move/backup/restore, provider that throws.
**Format tests:** real samples for each row in section 8 (HEIC, AVIF, ProRAW DNG, Ultra HDR, Motion Photo, Live Photo pair, HEVC MOV, ProRes, MKV multi-audio, subtitles).
**Performance:** 10k/25k/50k libraries; Macrobenchmark startup + scroll; memory/leak checks (LeakCanary in debug); battery/thermal during indexing.
**Build checks:** local flavor merged manifest has no `INTERNET`; lint; detekt/ktlint; dependency license report.
**Acceptance:** each phase's acceptance bullets below pass on a real device before moving on.

---

## 35. Phased roadmap

**Phase 0 — Foundation:** project, theme + glass (code files), nav shell, permissions, Room, repository abstractions, error types, logging, test harness. *Accept:* builds on API 30+, no fake data, permission states correct, light/dark/AMOLED switch.
**Phase 1 — Library MVP:** timeline (Years/Months/Days/All), albums, viewer, video playback, filename search, favorites, selection, share, incoming media, safe delete/Recently Deleted, indexing + thumbnails. *Accept:* real media, isolated errors, paging proven on large set.
**Phase 2 — Files:** SAF browser (phone + tablet layouts), SD grants, copy/move/rename/create/delete, trash, storage dashboard, ZIP. *Accept:* no silent overwrite, interruption-safe.
**Phase 3 — Camera:** CameraX photo/video UI per section 14, capability gating, MediaStore saving, thumbnail-to-viewer flow. *Accept:* capabilities reflect the real device; permission/busy/thermal errors handled.
**Phase 4 — Editor:** basic + advanced tools, recipes, save copy, privacy export. *Accept:* originals untouched, redaction flattened.
**Phase 5 — Media tools:** video trim/frame grab, slideshow, duplicates, batch rename, backup/restore. *Accept:* hashes verified, interruptions recover.
**Phase 6 — Smart features:** OCR/Live Text, QR, memories, smart albums, maps, optional AI. *Accept:* airplane-mode tests pass, indexes deletable.
**Phase 7 — Integrations:** widgets, shortcuts, tile, sync flavor, benchmark/profile tuning, store listing. *Accept:* local flavor stays offline.

**MVP minimum (must not slip):** timeline, albums/folders, viewer, basic video, filename search, favorites + multi-select, share + incoming media, safe delete, basic SAF browsing, permission status, thumbnails + paging + indexing, robust corrupt/missing/unsupported handling.

---

## 36. Final checklist

- [ ] Native Android Studio project; compiles; documented SDK/dependency versions.
- [ ] `minSdk 30`, current target SDK; edge-to-edge + predictive back.
- [ ] Vault is fully absent from UI, database, manifest, strings and docs.
- [ ] Glass UI works with blur on API 31+ and opaque fallback on API 30/low-power/reduce-transparency.
- [ ] Light, Dark, AMOLED themes verified on every screen.
- [ ] Camera matches section 14 and only offers supported capabilities.
- [ ] Files layouts match section 15 on phone and tablet.
- [ ] No dummy data; no fake statuses.
- [ ] Every error in section 28.2 has been triggered in a test and shows the specified UI.
- [ ] Incoming media pipeline fuzzed; app never crashes on bad intents.
- [ ] Local flavor has no `INTERNET`; sync flavor opt-in.
- [ ] Originals preserved by default; destructive actions confirmed with Android consent.
- [ ] Icons, splash, themed icon and in-app logo included.
- [ ] Accessibility, RTL and font-scale tests pass.
- [ ] Performance measured on real hardware; baseline profile shipped.
- [ ] `IMPLEMENTATION_STATUS.md` honest and current.

---

## 37. Official references (re-check before release)

MediaStore: developer.android.com/training/data-storage/shared/media · Partial photo access: developer.android.com/about/versions/14/changes/partial-photo-video-access · SAF: developer.android.com/training/data-storage/shared/documents-files · Manage all files: developer.android.com/training/data-storage/manage-all-files · Supported formats: developer.android.com/media/platform/supported-formats · Media3 formats: developer.android.com/media/media3/exoplayer/supported-formats · CameraX: developer.android.com/media/camera/camerax · Camera extensions: developer.android.com/media/camera/camera-extensions · Play target API: developer.android.com/google/play/requirements/target-sdk · Predictive back: developer.android.com/guide/navigation/custom-back/predictive-back-gesture · 16 KB page sizes: developer.android.com/guide/practices/page-sizes · Haze (backdrop blur): chrisbanes.github.io/haze

---

# Part B — Addendum: Default-app role, extra features, layout, build and honest status (sections 38–45)

## 38. Making Far Gallery the default app on your phone

Android has **no single public "default gallery" switch** and an ordinary app cannot silently replace the system Gallery, Camera or Files app. What Far Gallery does instead is the standard, supported route:

| Role | How Far Gallery claims it | What you do |
|---|---|---|
| Open photos/videos (VIEW image/*, video/*) | `MainActivity` intent filters | Tap a photo in any app → choose **Far Gallery → Always** |
| Share-to (SEND / SEND_MULTIPLE) | Intent filters | Appears in the share sheet |
| Pick from gallery (GET_CONTENT, PICK, PICK_IMAGES) | Intent filters | Chosen by apps that ask for a photo |
| Camera (IMAGE_CAPTURE, VIDEO_CAPTURE, still-image camera) | `CameraActivity` filters | Choose **Far Gallery → Always** when an app asks for a camera |
| Files (folder VIEW) + separate launcher | `.FilesLauncher` activity-alias | Open folders with it; it has its own icon |
| Edit (EDIT) | Filter to EditorScreen | Choose when editing |

**Settings → "Make Far Gallery your default"** reads live status with `DefaultApps.status` (Mine / Ask each time / Other app / Unavailable). Tapping a row either launches a test intent so Android shows its chooser, or opens system default-app settings to clear the other app's default.

Safety rules: never claim to be the default when it is not; never block the system app; if the app is disabled, the system falls back to the stock app. Some manufacturers (One UI, MIUI, ColorOS) add extra "default apps" pages — the app links to the system page rather than guessing.

## 39. Additional Gallery features (summary table)

| Feature | Behaviour | Status |
|---|---|---|
| Pinch-to-zoom grid columns, date headers | Paging 3 over MediaStore | Implemented |
| Multi-select, share, favorite, trash/restore | Consent via `createTrashRequest` | Implemented |
| Viewer: pager, pinch/pan, swipe-dismiss, shared element | Compose | Implemented |
| Details sheet (size, date, path) | Glass sheet | Implemented |
| Editor: brightness/contrast/saturation/warmth, ratio crop, rotate/flip, undo/redo, save copy | Non-destructive | Implemented |
| Video player, frame grab, trim to copy | Media3 | Implemented |
| Duplicate finder (hash, protects favorites, can't tick last copy) | | Implemented |
| Albums, Memories (on-this-day rules), Search with scopes | UI built | UI only — Room wiring pending |
| Freeform crop, markup, filters, redaction, OCR worker | | Not implemented |

## 40. Additional Camera features

| Feature | Status |
|---|---|
| Photo, Video, Square modes (iPhone-style carousel) | Implemented |
| Flash modes, timer, burst, grid + level, zoom chips, tap-to-focus, exposure, front/back switch, mirror selfie | Implemented |
| QR / barcode scan (ML Kit bundled) | Implemented |
| Volume-key shutter, low-storage check, capture-for-result (EXTRA_OUTPUT, duration/size limits) | Implemented |
| Portrait, Night, Slo-mo, Time-lapse, RAW, Live photo | Not implemented (hidden/disabled, never faked) |

## 41. Additional Files features

| Feature | Status |
|---|---|
| Storage Access Framework browsing, grants, sort, breadcrumb path bar | Implemented |
| Copy / move / paste with collision rules (rename, replace, skip), progress, cancel | Implemented |
| Verified copy → delete source; partial output cleaned | Implemented |
| Rename, delete, new folder | Implemented |
| Text (first 256 KB) and PDF preview | Implemented |
| Safe ZIP extractor (zip-slip, bomb limits) and SHA-256 | Library implemented |
| Phone (Apple Files) vs ≥840 dp (Ubuntu) layouts | Implemented |
| ZIP-compress UI, tabs, drag-and-drop, inline path editing | Not implemented |

## 42. Project layout

```
app/
  build.gradle.kts
  src/main/AndroidManifest.xml
  src/main/java/com/far/gallery/
    app/        MainActivity.kt  CameraActivity.kt  IntentRouter.kt  AppNav.kt  Onboarding.kt
    core/       common/AppErrors.kt   storage/DocumentOps.kt  data/MediaRepository.kt  data/Db.kt
    ui/         theme/Theme.kt  glass/Glass.kt  glass/GlassBottomBar.kt  feedback/Feedback.kt  brand/FarLogo.kt
    feature/    gallery/GalleryScreen.kt  viewer/ViewerScreen.kt  video/VideoPlayer.kt  editor/EditorScreen.kt
                albums/LibraryScreens.kt  duplicates/Duplicates.kt  camera/CameraScreen.kt  camera/CameraManager.kt
                files/FilesScreen.kt  files/FilesViewModel.kt  files/FilePreview.kt  settings/SettingsScreen.kt
  src/main/res/
    drawable/   ic_launcher_background.xml  ic_launcher_foreground.xml  ic_launcher_monochrome.xml  ic_fg_camera.xml  ic_fg_files.xml
    mipmap-anydpi-v26/ ic_launcher.xml  ic_launcher_camera.xml  ic_launcher_files.xml
web/ favicon.svg  favicon-mini.svg  favicon-maskable.svg
```
Package names in the files follow the `package` line at the top of each file; move files to match.

## 43. How the pieces connect

`FarApplication` installs the crash note → `MainActivity` runs `IntentRouter` on the launch intent → `AppViewModel` holds permissions/settings → `FarApp` (NavHost + SharedTransitionLayout) shows tabs Photos / Albums / Search / Files over `GlassBottomBar`, with the detached camera button opening `CameraActivity`. Incoming URIs pass through `IncomingMediaResolver` (content:// only, magic-byte sniffing, per-item isolation) so a bad file becomes an error card, never a crash. All failures are `FarResult.Err(FarError)` via `farCatching`, which always rethrows `CancellationException`.

## 44. Build and run

1. Android Studio (latest stable), JDK 17+, SDK 36.
2. Create an empty Compose project with package `com.far.gallery`; copy files from Part C into matching folders.
3. Create `gradle/libs.versions.toml` with the aliases referenced in `build.gradle.kts` (Compose BOM, Material 3, Navigation, Paging, Room + KSP, DataStore, CameraX, Media3, ML Kit barcode, Coil 3, Haze).
4. Choose the **local** flavor (no INTERNET permission) and run on a device with API 30+.
5. Grant photo access, then open Settings → Make Far Gallery your default.

## 45. Honest implementation status

- **Not compiled.** This code was written without a build; expect a first round of compile fixes (Haze API version, unstable Compose/Media3 APIs, missing version-catalog entries, `farShared` helper usage in AppNav).
- AppNav currently passes **empty album lists** — Albums/Memories/Search UIs are not wired to Room.
- Features listed as "Not implemented" above are not in the app and must not be advertised.
- No vault: it was removed entirely; "hide from timeline" is an exclusion filter, not a security feature.
- Needs on-device testing on API 30, 33 and 36, plus a low-end device for performance.

---

# Part C — Source code

### app/src/main/AndroidManifest.xml

`````xml
<?xml version="1.0" encoding="utf-8"?>
<!-- app/src/main/AndroidManifest.xml  (Local flavor: NO INTERNET permission. Sync flavor adds it in src/sync/AndroidManifest.xml) -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!-- Library access matrix (spec section 26) -->
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32"/>
    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES"/>
    <uses-permission android:name="android.permission.READ_MEDIA_VIDEO"/>
    <uses-permission android:name="android.permission.READ_MEDIA_VISUAL_USER_SELECTED"/>
    <!-- Optional, requested only on opt-in -->
    <uses-permission android:name="android.permission.ACCESS_MEDIA_LOCATION"/>
    <uses-permission android:name="android.permission.CAMERA"/>
    <uses-permission android:name="android.permission.RECORD_AUDIO"/>
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>

    <uses-feature android:name="android.hardware.camera" android:required="false"/>
    <uses-feature android:name="android.hardware.camera.autofocus" android:required="false"/>
    <uses-feature android:name="android.hardware.camera.front" android:required="false"/>

    <!-- Package visibility for default-app status checks and "Open with" -->
    <queries>
        <intent><action android:name="android.intent.action.VIEW"/><data android:mimeType="image/*"/></intent>
        <intent><action android:name="android.intent.action.VIEW"/><data android:mimeType="video/*"/></intent>
        <intent><action android:name="android.media.action.IMAGE_CAPTURE"/></intent>
        <intent><action android:name="android.media.action.VIDEO_CAPTURE"/></intent>
        <intent><action android:name="android.intent.action.PICK"/><data android:mimeType="image/*"/></intent>
        <intent><action android:name="android.intent.action.VIEW"/><data android:mimeType="vnd.android.document/directory"/></intent>
        <intent><action android:name="com.google.ar.lens.LENS_ACTIVITY"/></intent>
    </queries>

    <application
        android:name=".FarApplication"
        android:allowBackup="false"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:enableOnBackInvokedCallback="true"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:largeHeap="false"
        android:theme="@style/Theme.FarGallery.Splash">

        <!-- ================= Gallery (default launcher entry) ================= -->
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTask"
            android:supportsPictureInPicture="true"
            android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout|keyboardHidden|density|uiMode"
            android:windowSoftInputMode="adjustResize">

            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
                <category android:name="android.intent.category.APP_GALLERY"/>
            </intent-filter>

            <!-- Open (VIEW) images and videos from any app -->
            <intent-filter>
                <action android:name="android.intent.action.VIEW"/>
                <category android:name="android.intent.category.DEFAULT"/>
                <category android:name="android.intent.category.BROWSABLE"/>
                <data android:scheme="content"/>
                <data android:mimeType="image/*"/>
                <data android:mimeType="video/*"/>
            </intent-filter>

            <!-- Receive shares -->
            <intent-filter>
                <action android:name="android.intent.action.SEND"/>
                <category android:name="android.intent.category.DEFAULT"/>
                <data android:mimeType="image/*"/>
                <data android:mimeType="video/*"/>
            </intent-filter>
            <intent-filter>
                <action android:name="android.intent.action.SEND_MULTIPLE"/>
                <category android:name="android.intent.category.DEFAULT"/>
                <data android:mimeType="image/*"/>
                <data android:mimeType="video/*"/>
            </intent-filter>

            <!-- Act as a picker for other apps -->
            <intent-filter>
                <action android:name="android.intent.action.GET_CONTENT"/>
                <category android:name="android.intent.category.DEFAULT"/>
                <category android:name="android.intent.category.OPENABLE"/>
                <data android:mimeType="image/*"/>
                <data android:mimeType="video/*"/>
            </intent-filter>
            <intent-filter>
                <action android:name="android.intent.action.PICK"/>
                <category android:name="android.intent.category.DEFAULT"/>
                <data android:mimeType="image/*"/>
                <data android:mimeType="video/*"/>
            </intent-filter>
            <intent-filter>
                <action android:name="android.provider.action.PICK_IMAGES"/>
                <category android:name="android.intent.category.DEFAULT"/>
                <data android:mimeType="image/*"/>
                <data android:mimeType="video/*"/>
            </intent-filter>

            <!-- Edit in Far Gallery -->
            <intent-filter>
                <action android:name="android.intent.action.EDIT"/>
                <category android:name="android.intent.category.DEFAULT"/>
                <data android:mimeType="image/*"/>
            </intent-filter>

            <!-- Open folders (file-manager handoff) -->
            <intent-filter>
                <action android:name="android.intent.action.VIEW"/>
                <category android:name="android.intent.category.DEFAULT"/>
                <data android:mimeType="vnd.android.document/directory"/>
                <data android:mimeType="resource/folder"/>
                <data android:mimeType="inode/directory"/>
            </intent-filter>

            <meta-data android:name="android.app.shortcuts" android:resource="@xml/shortcuts"/>
        </activity>

        <!-- ================= Camera (own launcher icon + capture intents) ================= -->
        <activity
            android:name=".CameraActivity"
            android:exported="true"
            android:label="@string/camera_name"
            android:icon="@mipmap/ic_launcher_camera"
            android:launchMode="singleTask"
            android:screenOrientation="unspecified"
            android:configChanges="orientation|screenSize|screenLayout|keyboardHidden|uiMode">

            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
            <intent-filter>
                <action android:name="android.media.action.IMAGE_CAPTURE"/>
                <category android:name="android.intent.category.DEFAULT"/>
            </intent-filter>
            <intent-filter>
                <action android:name="android.media.action.VIDEO_CAPTURE"/>
                <category android:name="android.intent.category.DEFAULT"/>
            </intent-filter>
            <intent-filter>
                <action android:name="android.media.action.STILL_IMAGE_CAMERA"/>
                <category android:name="android.intent.category.DEFAULT"/>
            </intent-filter>
            <intent-filter>
                <action android:name="android.media.action.VIDEO_CAMERA"/>
                <category android:name="android.intent.category.DEFAULT"/>
            </intent-filter>
        </activity>

        <!-- ================= Files (own launcher icon) ================= -->
        <activity-alias
            android:name=".FilesLauncher"
            android:exported="true"
            android:targetActivity=".MainActivity"
            android:label="@string/files_name"
            android:icon="@mipmap/ic_launcher_files">
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
            <meta-data android:name="far.start_tab" android:value="files"/>
        </activity-alias>

        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data android:name="android.support.FILE_PROVIDER_PATHS" android:resource="@xml/file_paths"/>
        </provider>
    </application>
</manifest>

<!--
res/xml/shortcuts.xml  -> static shortcuts: farGallery://camera, favorites, search
res/xml/file_paths.xml -> <cache-path name="share" path="share/"/>  (temporary metadata-stripped copies only)
res/xml/data_extraction_rules.xml -> exclude database/, datastore/, cache so OCR text and locations are not cloud-backed up
res/values/strings.xml -> app_name="Far Gallery", camera_name="Far Camera", files_name="Far Files"
-->

`````

### app/build.gradle.kts

`````kotlin
// app/build.gradle.kts  — versions are deliberately NOT invented here. Put current stable versions in gradle/libs.versions.toml
// (Android Studio's "Version Catalog" quick-fix suggests them). Verify each license + 16 KB page-size support before release.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)                 // Room compiler
}

android {
    namespace = "com.far.gallery"
    compileSdk = 36                          // latest stable at build time
    defaultConfig {
        applicationId = "com.far.gallery"
        minSdk = 30
        targetSdk = 36                       // re-check Play target-API requirement before each release
        versionCode = 1
        versionName = "1.0.0"
        ndk { abiFilters += "arm64-v8a" }
    }
    flavorDimensions += "network"
    productFlavors {
        create("local") { dimension = "network" }                                        // no INTERNET permission, no network code
        create("sync")  { dimension = "network"; applicationIdSuffix = ".sync" }          // adds INTERNET in src/sync/AndroidManifest.xml
    }
    buildTypes {
        release { isMinifyEnabled = true; isShrinkResources = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") }
    }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging { jniLibs { useLegacyPackaging = false } }       // uncompressed, page-aligned native libs (16 KB)
    ksp { arg("room.schemaLocation", "$projectDir/schemas") }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom); implementation(composeBom)
    implementation(libs.bundles.compose)                    // ui, foundation, material3, icons-extended, ui-tooling-preview
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.bundles.room); ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.paging.runtime); implementation(libs.androidx.paging.compose)
    implementation(libs.bundles.camerax)                    // core, camera2, lifecycle, view, video
    implementation(libs.bundles.media3)                     // exoplayer, ui, transformer
    implementation(libs.coil.compose); implementation(libs.coil.video)
    implementation(libs.haze)                               // backdrop blur for the glass UI
    implementation(libs.mlkit.barcode.bundled)              // BUNDLED model => works offline on first run
    implementation(libs.androidx.work.runtime.ktx)
    testImplementation(libs.junit); androidTestImplementation(libs.bundles.androidx.test)
}

/* gradle/libs.versions.toml skeleton (fill versions from Android Studio):
[versions]  agp="" kotlin="" ksp="" composeBom="" haze="" coil="" media3="" camerax="" room="" paging="" nav="" lifecycle="" datastore="" work=""
[libraries] # use the official group:artifact names, e.g. androidx.camera:camera-view, androidx.media3:media3-exoplayer,
            # androidx.media3:media3-transformer, io.coil-kt.coil3:coil-compose, dev.chrisbanes.haze:haze,
            # com.google.mlkit:barcode-scanning (bundled variant), androidx.room:room-ktx, androidx.paging:paging-compose
*/

`````

### app/MainActivity.kt

`````kotlin
package com.far.gallery

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.far.gallery.app.IncomingRoute
import com.far.gallery.app.IntentRouter
import com.far.gallery.core.common.FarError
import com.far.gallery.core.common.IncomingMediaResolver
import com.far.gallery.core.common.IncomingResult
import com.far.gallery.core.database.FarDatabase
import com.far.gallery.feature.settings.FarSettings
import com.far.gallery.feature.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class FarApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Anything still "Running" from a previous process is marked Interrupted; the UI offers Resume / Dismiss. Nothing destructive restarts by itself.
        @Suppress("OPT_IN_USAGE")
        GlobalScope.launch(Dispatchers.IO) { FarDatabase.get(this@FarApplication).operations().markInterrupted(System.currentTimeMillis()) }
        installCrashNote()
    }

    /** Privacy-safe crash note: class name, API level, version. No file names, URIs, GPS, OCR text. Never uploaded. */
    private fun installCrashNote() {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                File(filesDir, "last_crash.txt").writeText("${System.currentTimeMillis()}|${e.javaClass.name}|api=${Build.VERSION.SDK_INT}|startup=${System.currentTimeMillis() - startMs < 8000}")
                val f = File(filesDir, "crash_count.txt"); f.writeText(((f.takeIf { it.exists() }?.readText()?.toIntOrNull() ?: 0) + 1).toString())
            }
            prev?.uncaughtException(t, e)
        }
    }
    private val startMs = System.currentTimeMillis()
}

enum class Access { Full, Selected, None }

object Permissions {
    fun mediaPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    private fun granted(c: Context, p: String) = ContextCompat.checkSelfPermission(c, p) == PackageManager.PERMISSION_GRANTED
    /** Always recompute from the system; users can change this in Settings at any time. */
    fun access(c: Context): Access = when {
        Build.VERSION.SDK_INT >= 33 && (granted(c, Manifest.permission.READ_MEDIA_IMAGES) || granted(c, Manifest.permission.READ_MEDIA_VIDEO)) -> Access.Full
        Build.VERSION.SDK_INT >= 34 && granted(c, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> Access.Selected
        Build.VERSION.SDK_INT < 33 && granted(c, Manifest.permission.READ_EXTERNAL_STORAGE) -> Access.Full
        else -> Access.None
    }
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SettingsStore(app)
    val settings: StateFlow<FarSettings?> = store.flow.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _route = MutableStateFlow<IncomingRoute>(IncomingRoute.None)
    val route: StateFlow<IncomingRoute> = _route.asStateFlow()
    private val _incoming = MutableStateFlow<IncomingResult?>(null)
    val incoming: StateFlow<IncomingResult?> = _incoming.asStateFlow()
    private val _access = MutableStateFlow(Permissions.access(app))
    val access: StateFlow<Access> = _access.asStateFlow()
    private val _safeMode = MutableStateFlow(false)
    val safeMode: StateFlow<Boolean> = _safeMode.asStateFlow()

    init { // crash-loop protection: 3 crashes in a row at startup -> safe mode (no background enrichment)
        val f = File(app.filesDir, "crash_count.txt")
        _safeMode.value = (f.takeIf { it.exists() }?.readText()?.toIntOrNull() ?: 0) >= 3
    }
    fun leaveSafeMode() { File(getApplication<Application>().filesDir, "crash_count.txt").delete(); _safeMode.value = false }
    fun markHealthyStart() { File(getApplication<Application>().filesDir, "crash_count.txt").delete() }

    fun refreshAccess() { _access.value = Permissions.access(getApplication()) }
    fun update(t: (FarSettings) -> FarSettings) = viewModelScope.launch { store.update(t) }

    fun handleIntent(intent: Intent?) {
        val r = IntentRouter.classify(intent)
        _route.value = r
        if (r is IncomingRoute.ViewOrReceive) viewModelScope.launch(Dispatchers.IO) {
            _incoming.value = IncomingMediaResolver(getApplication<Application>().contentResolver).resolve(r.intent)   // off the main thread, per-item isolation
        }
    }
    fun consumeRoute() { _route.value = IncomingRoute.None; _incoming.value = null }
}

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        splash.setKeepOnScreenCondition { vm.settings.value == null }       // hold splash only until settings load
        if (savedInstanceState == null) vm.handleIntent(intent)
        val startFiles = intent.component?.className?.endsWith("FilesLauncher") == true
        setContent { com.far.gallery.app.FarApp(vm, activity = this, startOnFiles = startFiles) }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); vm.handleIntent(intent) }
    override fun onResume() { super.onResume(); vm.refreshAccess() }
}

`````

### app/CameraActivity.kt

`````kotlin
package com.far.gallery

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import coil3.compose.AsyncImage
import com.far.gallery.app.IncomingRoute
import com.far.gallery.app.IntentResults
import com.far.gallery.app.IntentRouter
import com.far.gallery.feature.camera.CameraMode
import com.far.gallery.feature.camera.CameraScreen
import com.far.gallery.feature.camera.CameraViewModel
import com.far.gallery.ui.theme.FarTheme

/** Hosts the camera for: the "Far Camera" launcher icon, other apps' capture requests, and the lock-screen/quick shortcuts. */
class CameraActivity : ComponentActivity() {
    private val vm: CameraViewModel by viewModels()
    private var request: IncomingRoute.Capture? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        request = IntentRouter.classify(intent) as? IncomingRoute.Capture
        vm.captureRequest = request
        request?.let { req ->
            if (req.video) vm.setMode(CameraMode.Video)
            vm.onCaptured = { uri -> IntentResults.capture(this, uri, req) }   // returns RESULT_OK to the calling app and finishes
        }
        setContent { FarTheme { CameraHost(vm, onClose = { finish() }, onOpenLast = ::openInGallery) } }
    }

    private fun openInGallery(uri: Uri) = startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, contentResolver.getType(uri) ?: "image/*")
        .setPackage(packageName).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))

    override fun onStop() { super.onStop(); vm.onAppStopped() }          // finalise any recording so the file is kept

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) && event?.repeatCount == 0) { vm.shutter(); true }
        else super.onKeyDown(keyCode, event)
}

@Composable
fun CameraHost(vm: CameraViewModel, onClose: () -> Unit, onOpenLast: (Uri) -> Unit) {
    val ctx = LocalContext.current
    val state by vm.state.collectAsState()
    val caps by vm.caps.collectAsState()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        vm.permission(r[Manifest.permission.CAMERA] == true || ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED)
    }
    // Ask only when the camera is actually opened. Microphone is requested together so video has sound; refusal = silent video, announced.
    LaunchedEffect(Unit) {
        val has = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
        vm.permission(has)
        if (!has) launcher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) vm.onAppStopped() }
        owner.lifecycle.addObserver(o); onDispose { owner.lifecycle.removeObserver(o) }
    }
    val c = caps
    if (c == null && state.error == null && state.permissionGranted) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }     // real loading state while CameraX initialises
        return
    }
    CameraScreen(
        controller = vm.manager.controller,
        caps = c ?: com.far.gallery.feature.camera.CameraCapabilities(listOf(1f), 1f, 1f, false, 0f..0f, listOf(CameraMode.Photo), false),
        state = state,
        onMode = vm::setMode, onZoom = vm::setZoom, onFlash = vm::setFlash, onExposure = vm::setExposure,
        onShutter = vm::shutter, onFlip = vm::flip,
        onOpenLast = { state.lastCaptureUri?.let { onOpenLast(Uri.parse(it)) } },
        onRequestPermission = { launcher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) },
        lastThumbnail = { uri -> AsyncImage(uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) },
    )
}

`````

### app/IntentRouter.kt

`````kotlin
package com.far.gallery.app

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import com.far.gallery.core.common.FarResult
import com.far.gallery.core.common.farCatching

/** What an incoming Intent is asking Far Gallery to do. Classification does NO I/O. */
sealed interface IncomingRoute {
    data object None : IncomingRoute
    data class ViewOrReceive(val intent: Intent, val shared: Boolean) : IncomingRoute   // validated later by IncomingMediaResolver
    data class Pick(val multiple: Boolean, val maxItems: Int, val mimeTypes: List<String>) : IncomingRoute
    data class Capture(val video: Boolean, val output: Uri?, val maxDurationSec: Int?, val maxBytes: Long?) : IncomingRoute
    data class Edit(val uri: Uri) : IncomingRoute
    data class OpenFolder(val uri: Uri) : IncomingRoute
}

object IntentRouter {
    fun classify(intent: Intent?): IncomingRoute {
        intent ?: return IncomingRoute.None
        val type = intent.type.orEmpty()
        return when (intent.action) {
            Intent.ACTION_VIEW -> when {
                type == "vnd.android.document/directory" || type == "resource/folder" || type == "inode/directory" ->
                    intent.data?.let(IncomingRoute::OpenFolder) ?: IncomingRoute.None
                intent.data != null -> IncomingRoute.ViewOrReceive(intent, shared = false)
                else -> IncomingRoute.None
            }
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> IncomingRoute.ViewOrReceive(intent, shared = true)
            Intent.ACTION_EDIT -> intent.data?.let(IncomingRoute::Edit) ?: IncomingRoute.None
            Intent.ACTION_GET_CONTENT, Intent.ACTION_PICK, MediaStore.ACTION_PICK_IMAGES -> IncomingRoute.Pick(
                multiple = intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false) || intent.getIntExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, 1) > 1,
                maxItems = intent.getIntExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, 100).coerceIn(1, 500),
                mimeTypes = (intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)?.toList() ?: listOf(type.ifEmpty { "*/*" })),
            )
            MediaStore.ACTION_IMAGE_CAPTURE, MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA -> IncomingRoute.Capture(
                video = false, output = outputUri(intent), maxDurationSec = null, maxBytes = null)
            MediaStore.ACTION_VIDEO_CAPTURE, MediaStore.INTENT_ACTION_VIDEO_CAMERA -> IncomingRoute.Capture(
                video = true, output = outputUri(intent),
                maxDurationSec = intent.getIntExtra(MediaStore.EXTRA_DURATION_LIMIT, 0).takeIf { it > 0 },
                maxBytes = intent.getLongExtra(MediaStore.EXTRA_SIZE_LIMIT, 0L).takeIf { it > 0 })
            else -> IncomingRoute.None
        }
    }

    @Suppress("DEPRECATION")
    private fun outputUri(i: Intent): Uri? = i.getParcelableExtra(MediaStore.EXTRA_OUTPUT)
}

/** Result helpers so callers (messaging apps, forms…) always get a correct, permission-granted reply. */
object IntentResults {
    fun pick(activity: Activity, uris: List<Uri>) {
        if (uris.isEmpty()) { activity.setResult(Activity.RESULT_CANCELED); activity.finish(); return }
        val data = Intent().addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        data.data = uris.first()
        if (uris.size > 1) {
            val clip = android.content.ClipData.newRawUri("media", uris.first())
            uris.drop(1).forEach { clip.addItem(android.content.ClipData.Item(it)) }
            data.clipData = clip
        } else {
            data.clipData = android.content.ClipData.newRawUri("media", uris.first())
        }
        activity.setResult(Activity.RESULT_OK, data); activity.finish()
    }

    /** Capture-for-result: copy to the caller's EXTRA_OUTPUT if given, never leave a surprise copy when told to write elsewhere. */
    fun capture(activity: Activity, saved: Uri, request: IncomingRoute.Capture): FarResult<Unit> = farCatching {
        val r: ContentResolver = activity.contentResolver
        val out = Intent().addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (request.output != null) {
            r.openInputStream(saved)!!.use { input -> r.openOutputStream(request.output, "w")!!.use { input.copyTo(it) } }
            r.delete(saved, null, null)            // we created it, so no consent dialog is needed
        } else {
            out.data = saved
            if (!request.video) {
                val opts = BitmapFactory.Options().apply { inSampleSize = 8 }
                r.openInputStream(saved)?.use { BitmapFactory.decodeStream(it, null, opts) }?.let { out.putExtra("data", it) }
            }
        }
        activity.setResult(Activity.RESULT_OK, out)
        activity.finish()
    }
}

/* ---------------------- Making Far Gallery the phone's default ---------------------- */
enum class DefaultRole(val label: String, val hint: String) {
    Gallery("Open photos", "Tapping a photo in any app opens it in Far Gallery"),
    Videos("Open videos", "Tapping a video opens it in Far Gallery"),
    Camera("Camera requests", "Apps that ask for a photo or video use Far Camera"),
    Picker("Photo picking", "Apps that ask you to choose a photo show Far Gallery"),
    Folders("Open folders", "Folder links from other apps open in Far Files"),
}

sealed interface DefaultStatus {
    data object Mine : DefaultStatus
    data object AskEveryTime : DefaultStatus                       // resolver dialog would appear
    data class Other(val label: String, val packageName: String) : DefaultStatus
    data object NoHandler : DefaultStatus
}

object DefaultApps {
    private val sampleImage = Uri.parse("content://media/external/images/media/1")
    private val sampleVideo = Uri.parse("content://media/external/video/media/1")

    fun probe(role: DefaultRole): Intent = when (role) {
        DefaultRole.Gallery -> Intent(Intent.ACTION_VIEW).setDataAndType(sampleImage, "image/jpeg")
        DefaultRole.Videos -> Intent(Intent.ACTION_VIEW).setDataAndType(sampleVideo, "video/mp4")
        DefaultRole.Camera -> Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        DefaultRole.Picker -> Intent(Intent.ACTION_PICK).setType("image/*")
        DefaultRole.Folders -> Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://com.android.externalstorage.documents/document/primary%3A"), "vnd.android.document/directory")
    }

    fun status(ctx: Context, role: DefaultRole): DefaultStatus {
        val pm = ctx.packageManager
        val info = pm.resolveActivity(probe(role), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo ?: return DefaultStatus.NoHandler
        return when (info.packageName) {
            ctx.packageName -> DefaultStatus.Mine
            "android" -> DefaultStatus.AskEveryTime                 // system resolver = no default chosen yet
            else -> DefaultStatus.Other(info.loadLabel(pm).toString(), info.packageName)
        }
    }

    /** Where the user clears another app's default ("Open by default" -> "Clear defaults"). */
    fun appDetails(pkg: String): Intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))

    /** System "Default apps" page; some OEMs (incl. XOS) hide gallery/camera there, so use the in-app guide too. */
    fun systemDefaults(): Intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
}

`````

### app/AppNav.kt

`````kotlin
package com.far.gallery.app

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.paging.compose.collectAsLazyPagingItems
import com.far.gallery.Access
import com.far.gallery.AppViewModel
import com.far.gallery.CameraHost
import com.far.gallery.Permissions
import com.far.gallery.core.database.FarDatabase
import com.far.gallery.core.media.GalleryViewModel
import com.far.gallery.core.media.MediaFilter
import com.far.gallery.core.media.MediaKind
import com.far.gallery.core.media.MediaRepository
import com.far.gallery.feature.camera.CameraViewModel
import com.far.gallery.feature.files.FilesEvent
import com.far.gallery.feature.files.FilesScreen
import com.far.gallery.feature.files.FilesViewModel
import com.far.gallery.feature.gallery.GalleryItem
import com.far.gallery.feature.gallery.GalleryScreen
import com.far.gallery.feature.onboarding.OnboardingScreen
import com.far.gallery.feature.settings.SettingsScreen
import com.far.gallery.feature.viewer.ViewerItem
import com.far.gallery.feature.viewer.ViewerScreen
import com.far.gallery.feature.video.FrameGrabber
import com.far.gallery.feature.video.VideoPlayer
import com.far.gallery.feature.video.TrimPanel
import com.far.gallery.feature.video.VideoTrimmer
import com.far.gallery.feature.editor.EditorEngine
import com.far.gallery.feature.editor.EditorScreen
import com.far.gallery.feature.albums.AlbumsScreen
import com.far.gallery.feature.albums.SearchScope
import com.far.gallery.feature.albums.SearchScreen
import com.far.gallery.feature.albums.IndexStatus
import com.far.gallery.ui.feedback.GlassToast
import com.far.gallery.ui.feedback.OperationProgressSheet
import com.far.gallery.ui.glass.rememberGlass
import com.far.gallery.ui.nav.FarTab
import com.far.gallery.ui.nav.GlassBottomBar
import com.far.gallery.ui.theme.FarPalette
import com.far.gallery.ui.theme.FarTheme
import kotlinx.coroutines.launch

/** Shared-element helper: same key on the grid tile and the viewer image gives the Photos-style expand/collapse. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.farShared(key: Any, sts: SharedTransitionScope, avs: androidx.compose.animation.AnimatedVisibilityScope): Modifier =
    with(sts) { this@farShared.sharedElement(rememberSharedContentState(key), avs) }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun FarApp(vm: AppViewModel, activity: Activity, startOnFiles: Boolean) {
    val settings by vm.settings.collectAsState()
    val s = settings ?: return                                         // splash is held until settings load
    val access by vm.access.collectAsState()
    val route by vm.route.collectAsState()
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) { val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) vm.refreshAccess() }; owner.lifecycle.addObserver(o); onDispose { owner.lifecycle.removeObserver(o) } }

    FarTheme(mode = s.theme, accent = FarPalette.curated.getOrElse(s.accentIndex) { FarPalette.Blue }, useDynamicColor = s.dynamicColor,
        reduceMotion = s.reduceMotion, reduceTransparency = s.reduceTransparency) {
        val nav = rememberNavController()
        val glass = rememberGlass()
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        val db = remember { FarDatabase.get(ctx) }
        val gvm: GalleryViewModel = viewModel(factory = GalleryViewModel.factory(ctx.contentResolver, db))
        val items = gvm.items.collectAsLazyPagingItems()
        val selection by gvm.selection.collectAsState()
        val zoom by gvm.zoom.collectAsState()
        var viewerList by remember { mutableStateOf<List<ViewerItem>>(emptyList()) }
        var viewerIndex by remember { mutableStateOf(0) }
        var tab by rememberSaveable { mutableStateOf(if (startOnFiles) FarTab.Files else FarTab.Photos) }
        var toast by remember { mutableStateOf<String?>(null) }
        var pendingTrash by remember { mutableStateOf<List<Uri>>(emptyList()) }

        val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.refreshAccess(); items.refresh() }
        val trashLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
            if (res.resultCode == Activity.RESULT_OK) { toast = "Moved to Recently Deleted"; gvm.clearSelection(); items.refresh() } else toast = "Nothing was deleted"
        }
        fun trash(uris: List<Uri>) { pendingTrash = uris; trashLauncher.launch(IntentSenderRequest.Builder(MediaRepository(ctx.contentResolver).trashRequest(uris, true)).build()) }

        // Pick mode: another app asked for media (GET_CONTENT / PICK / PICK_IMAGES).
        val pick = route as? IncomingRoute.Pick

        SharedTransitionLayout {
            NavHost(nav, startDestination = if (s.onboardingDone) "main" else "onboarding") {
                composable("onboarding") {
                    OnboardingScreen(
                        onFullAccess = { permLauncher.launch(Permissions.mediaPermissions()); vm.update { it.copy(onboardingDone = true) }; nav.navigate("main") { popUpTo("onboarding") { inclusive = true } } },
                        onSelectedOnly = { permLauncher.launch(Permissions.mediaPermissions()); vm.update { it.copy(onboardingDone = true) }; nav.navigate("main") { popUpTo("onboarding") { inclusive = true } } },
                        onSkip = { vm.update { it.copy(onboardingDone = true) }; nav.navigate("main") { popUpTo("onboarding") { inclusive = true } } })
                }

                composable("main") {
                    val avs = this
                    Box(Modifier.fillMaxSize()) {
                        AnimatedContent(tab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tabs") { t ->
                            when (t) {
                                FarTab.Photos -> GalleryScreen(
                                    items = items, glass = glass, zoom = zoom, onZoomChange = { gvm.zoom.value = it }, selection = selection,
                                    onOpen = { m ->
                                        if (pick != null) gvm.toggleSelect(m.id) else {
                                            val snap = items.itemSnapshotList.items.filterIsInstance<GalleryItem.Media>()
                                            viewerList = snap.map { ViewerItem(it.id, it.uri, it.description, null, it.isVideo, it.isFavorite) }
                                            viewerIndex = snap.indexOfFirst { it.id == m.id }.coerceAtLeast(0); nav.navigate("viewer")
                                        }
                                    },
                                    onToggleSelect = { gvm.toggleSelect(it.id) }, onRetry = { items.retry() },
                                    onRequestAccess = { if (access == Access.None) permLauncher.launch(Permissions.mediaPermissions()) else activity.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))) },
                                    mediaTileModifier = { m -> Modifier.farShared("media_${m.id}", this@SharedTransitionLayout, avs) },
                                )
                                FarTab.Albums -> AlbumsScreen(emptyList(), emptyList(), glass, {}, {}, { name -> scope.launch { db.albums().insert(com.far.gallery.core.database.AlbumEntity(name = name, createdAt = System.currentTimeMillis())) } }, {})
                                FarTab.Search -> {
                                    var q by rememberSaveable { mutableStateOf("") }; var sc by remember { mutableStateOf(SearchScope.All) }
                                    SearchScreen(q, { q = it; gvm.filter.value = gvm.filter.value.copy(nameQuery = it.ifBlank { null }) }, sc,
                                        { sc = it; gvm.filter.value = gvm.filter.value.copy(kind = when (it) { SearchScope.Photos -> MediaKind.Photos; SearchScope.Videos -> MediaKind.Videos; else -> MediaKind.All },
                                            pathContains = if (it == SearchScope.Screenshots) "Screenshots" else null, minSizeBytes = if (it == SearchScope.Large) 100L * 1024 * 1024 else null) },
                                        emptyList(), {}, IndexStatus.Ready, if (q.isBlank()) null else items.itemCount, glass,
                                        results = { GalleryScreen(items, glass, zoom, { }, selection, { m -> viewerList = items.itemSnapshotList.items.filterIsInstance<GalleryItem.Media>().map { ViewerItem(it.id, it.uri, it.description, null, it.isVideo, it.isFavorite) }; viewerIndex = viewerList.indexOfFirst { it.id == m.id }.coerceAtLeast(0); nav.navigate("viewer") }, { gvm.toggleSelect(it.id) }, { items.retry() }, {}) })
                                }
                                FarTab.Files -> FilesTab(glass)
                            }
                        }
                        Box(Modifier.align(Alignment.BottomCenter)) {
                            GlassBottomBar(glass, tab, { tab = it }, onCamera = { nav.navigate("camera") }, visible = selection.isEmpty())
                        }
                        // Selection bar
                        if (selection.isNotEmpty()) Column(Modifier.align(Alignment.BottomCenter).padding(16.dp)) {
                            if (pick != null) GlassToast(glass, "${selection.size} selected", "Add") {
                                val uris = items.itemSnapshotList.items.filterIsInstance<GalleryItem.Media>().filter { it.id in selection }.map { Uri.parse(it.uri) }
                                IntentResults.pick(activity, uris.take(pick.maxItems))
                            } else GlassToast(glass, "${selection.size} selected", "Delete") {
                                trash(items.itemSnapshotList.items.filterIsInstance<GalleryItem.Media>().filter { it.id in selection }.map { Uri.parse(it.uri) })
                            }
                        }
                        toast?.let { t -> Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp)) { GlassToast(glass, t, "OK") { toast = null } } }
                        if (access == Access.None && tab == FarTab.Photos) Unit   // the empty state in GalleryScreen already explains and offers "Review access"
                    }
                }

                composable("viewer") {
                    val avs = this
                    ViewerScreen(
                        items = viewerList, startIndex = viewerIndex, glass = glass, onClose = { nav.popBackStack() },
                        onFavorite = { gvm.toggleFavorite(it.id) },
                        onShare = { item -> activity.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(if (item.isVideo) "video/*" else "image/*").putExtra(Intent.EXTRA_STREAM, Uri.parse(item.uri)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null)) },
                        onEdit = { nav.navigate("editor/${Uri.encode(it.uri)}") },
                        onDelete = { trash(listOf(Uri.parse(it.uri))) },
                        detailsContent = { item -> Column(Modifier.padding(24.dp)) { Text(item.title, style = MaterialTheme.typography.titleMedium); Text("Type: ${if (item.isVideo) "Video" else "Photo"}") } },
                        videoContent = { item ->
                            VideoPlayer(item.uri, glass, null, {}, { ms -> scope.launch { FrameGrabber.grab(ctx, Uri.parse(item.uri), ms) } }, { },
                                onOpenWith = { activity.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(item.uri), "video/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null)) })
                        },
                        imageModifier = { it2 -> Modifier.farShared("media_${it2.id}", this@SharedTransitionLayout, avs) },
                    )
                }

                composable("camera") { CameraHost(viewModel<CameraViewModel>(), onClose = { nav.popBackStack() }, onOpenLast = { nav.popBackStack() }) }

                composable("editor/{uri}") { entry ->
                    val uri = Uri.parse(entry.arguments?.getString("uri").orEmpty())
                    var bmp by remember { mutableStateOf<android.graphics.Bitmap?>(null) }; var err by remember { mutableStateOf<String?>(null) }; var saving by remember { mutableStateOf(false) }
                    LaunchedEffect(uri) { when (val r = EditorEngine.load(ctx, uri, 2048)) { is com.far.gallery.core.common.FarResult.Ok -> bmp = r.value; is com.far.gallery.core.common.FarResult.Err -> err = "This image can't be edited here. The original is untouched." } }
                    EditorScreen(bmp, bmp == null && err == null, err, glass, saving, onCancel = { nav.popBackStack() }, onSave = { st ->
                        saving = true
                        scope.launch {
                            val b = bmp
                            when (if (b != null) EditorEngine.saveCopy(ctx, b, st, 92) else null) { is com.far.gallery.core.common.FarResult.Ok -> { toast = "Saved a copy"; nav.popBackStack() }; else -> { saving = false; toast = "Couldn't save. Your original is untouched." } }
                        }
                    })
                }

                composable("settings") {
                    SettingsScreen(s, { t -> vm.update(t) }, { activity.startActivity(DefaultApps.systemDefaults()) },
                        { role, st -> when (st) { is DefaultStatus.Other -> activity.startActivity(DefaultApps.appDetails(st.packageName)); else -> activity.startActivity(DefaultApps.systemDefaults()) } },
                        onClearCache = { ctx.cacheDir.deleteRecursively() }, onDeleteOcrIndex = { }, onManageAccess = { permLauncher.launch(Permissions.mediaPermissions()) })
                }
            }
        }
    }
}

@Composable
private fun FilesTab(glass: dev.chrisbanes.haze.HazeState) {
    val fvm: FilesViewModel = viewModel()
    val ui by fvm.ui.collectAsState(); val op by fvm.operation.collectAsState()
    val ctx = LocalContext.current
    var renaming by remember { mutableStateOf<com.far.gallery.feature.files.FileEntry?>(null) }
    var deleting by remember { mutableStateOf<List<com.far.gallery.feature.files.FileEntry>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(fvm::addGrant) }
    LaunchedEffect(Unit) {
        fvm.events.collect { e -> when (e) {
            FilesEvent.PickFolder -> folderPicker.launch(null)
            is FilesEvent.OpenFile -> ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).setDataAndType(e.uri, e.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null))
            is FilesEvent.Share -> ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(e.uris)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null))
            is FilesEvent.AskRename -> renaming = e.entry
            is FilesEvent.AskDelete -> deleting = e.entries
            is FilesEvent.Message -> message = e.text
            is FilesEvent.Properties -> message = "${e.entry.name} — ${e.path}"
        } }
    }
    Box(Modifier.fillMaxSize()) {
        FilesScreen(ui, fvm, glass)
        op?.let { Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp)) { OperationProgressSheet(glass, it, onCancel = fvm::cancelOperation) } }
        message?.let { m -> Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp)) { GlassToast(glass, m, "OK") { message = null } } }
    }
    renaming?.let { e -> var name by remember(e) { mutableStateOf(e.name) }
        AlertDialog({ renaming = null }, title = { Text("Rename") }, text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = { TextButton({ fvm.confirmRename(e, name.trim()); renaming = null }) { Text("Rename") } }, dismissButton = { TextButton({ renaming = null }) { Text("Cancel") } }) }
    deleting?.let { list ->
        AlertDialog({ deleting = null }, title = { Text("Delete ${list.size} item(s)?") }, text = { Text("This permanently deletes them from this location. This can't be undone.") },
            confirmButton = { TextButton({ fvm.confirmDelete(list); deleting = null }) { Text("Delete") } }, dismissButton = { TextButton({ deleting = null }) { Text("Cancel") } })
    }
}

`````

### app/Onboarding.kt

`````kotlin
package com.far.gallery.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.far.gallery.ui.brand.FarLogo
import kotlinx.coroutines.launch

/**
 * Three pages. No permission dialog appears until the user taps a button on page 3.
 * onFullAccess  -> request READ_MEDIA_* (+ VISUAL_USER_SELECTED on 34+) / READ_EXTERNAL_STORAGE on 30-32
 * onSelectedOnly -> launch the system Photo Picker / selected-photos flow
 * onSkip        -> enter the app with "No access" state (friendly empty Library)
 */
@Composable
fun OnboardingScreen(onFullAccess: () -> Unit, onSelectedOnly: () -> Unit, onSkip: () -> Unit) {
    val pager = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (page == 0) FarLogo(size = 140.dp)
                Text(
                    when (page) { 0 -> "Welcome to Far Gallery"; 1 -> "Photos, camera and files"; else -> "Choose what Far Gallery can see" },
                    Modifier.padding(top = 28.dp), style = MaterialTheme.typography.displayLarge, textAlign = TextAlign.Center,
                )
                Text(
                    when (page) {
                        0 -> "Your photos and videos, on your device. No account. Nothing leaves your phone."
                        1 -> "Browse a fast timeline, take photos with a full camera, and manage files and SD cards — all in one place."
                        else -> "Full access shows your whole library. You can also share only selected photos. You can change this any time in Settings."
                    },
                    Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(Modifier.padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) { i ->
                Box(Modifier.size(if (i == pager.currentPage) 10.dp else 8.dp).background(
                    if (i == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape))
            }
        }
        if (pager.currentPage < 2) {
            Button(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }, Modifier.fillMaxWidth()) { Text("Continue") }
            TextButton(onClick = onSkip) { Text("Skip") }
        } else {
            Button(onClick = onFullAccess, Modifier.fillMaxWidth()) { Text("Allow full library access") }
            OutlinedButton(onClick = onSelectedOnly, Modifier.fillMaxWidth()) { Text("Choose photos to share") }
            TextButton(onClick = onSkip) { Text("Skip for now") }
        }
    }
}

`````

### core/common/AppErrors.kt

`````kotlin
package com.far.gallery.core.common

import android.content.ClipData
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/** ---------- Typed errors ---------- */
sealed interface FarError {
    val code: String
    data class Unsupported(val reason: String, override val code: String = "FG-FMT") : FarError
    data class Corrupt(override val code: String = "FG-COR") : FarError
    data class Missing(override val code: String = "FG-MIS") : FarError
    data class PermissionRevoked(val scope: String, override val code: String = "FG-PRM") : FarError
    data class StorageFull(val neededBytes: Long?, val availableBytes: Long?, override val code: String = "FG-SPC") : FarError
    data class VolumeRemoved(val label: String?, override val code: String = "FG-VOL") : FarError
    data class ProviderUnavailable(override val code: String = "FG-PRV") : FarError
    data class OutOfMemory(override val code: String = "FG-OOM") : FarError
    data class Io(override val code: String = "FG-IO") : FarError
    data class InvalidInput(val reason: String, override val code: String = "FG-INP") : FarError
    data class Unknown(override val code: String = "FG-UNK") : FarError
}

sealed interface FarResult<out T> {
    data class Ok<T>(val value: T) : FarResult<T>
    data class Err(val error: FarError) : FarResult<Nothing>
}

/**
 * Wrap any I/O or decode call at the repository boundary.
 * - CancellationException is ALWAYS rethrown (never swallowed).
 * - OutOfMemoryError is mapped only here (around bitmap/decoder work); caller should downsample + trim caches.
 * - Anything unexpected becomes Unknown so the UI shows a safe card instead of crashing.
 */
inline fun <T> farCatching(block: () -> T): FarResult<T> = try {
    FarResult.Ok(block())
} catch (e: CancellationException) {
    throw e
} catch (e: FileNotFoundException) {
    FarResult.Err(FarError.Missing())
} catch (e: SecurityException) {
    FarResult.Err(FarError.PermissionRevoked("media"))
} catch (e: OutOfMemoryError) {
    FarResult.Err(FarError.OutOfMemory())
} catch (e: IOException) {
    val msg = e.message.orEmpty()
    when {
        "ENOSPC" in msg || "No space left" in msg -> FarResult.Err(FarError.StorageFull(null, null))
        else -> FarResult.Err(FarError.Io())
    }
} catch (e: IllegalStateException) {          // typical for dead content providers
    FarResult.Err(FarError.ProviderUnavailable())
} catch (e: IllegalArgumentException) {
    FarResult.Err(FarError.InvalidInput("bad argument"))
} catch (e: RuntimeException) {
    FarResult.Err(FarError.Unknown())
}

/** ---------- Incoming media validation ---------- */
data class IncomingItem(
    val uri: Uri,
    val displayName: String?,   // untrusted: never use as a path
    val sizeBytes: Long?,       // untrusted
    val declaredMime: String?,
    val sniffedMime: String?,
    val mimeMismatch: Boolean,
)

data class IncomingResult(val accepted: List<IncomingItem>, val rejected: List<Pair<Uri?, FarError>>)

class IncomingMediaResolver(private val resolver: ContentResolver, private val maxItems: Int = 500) {

    private val allowedActions = setOf(Intent.ACTION_VIEW, Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE)

    /** Call from a background dispatcher; never from the main thread. */
    fun resolve(intent: Intent): IncomingResult {
        if (intent.action !in allowedActions) {
            return IncomingResult(emptyList(), listOf(null to FarError.InvalidInput("unsupported action")))
        }
        val uris = LinkedHashSet<Uri>()
        intent.data?.let(uris::add)
        @Suppress("DEPRECATION")
        when (intent.action) {
            Intent.ACTION_SEND -> (intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))?.let(uris::add)
            Intent.ACTION_SEND_MULTIPLE -> intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let(uris::addAll)
        }
        intent.clipData?.let { clip: ClipData -> for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let(uris::add) }

        val accepted = ArrayList<IncomingItem>()
        val rejected = ArrayList<Pair<Uri?, FarError>>()
        for (uri in uris.take(maxItems)) {
            when (val r = farCatching { inspect(uri) }) {
                is FarResult.Ok -> accepted += r.value
                is FarResult.Err -> rejected += uri to r.error     // one bad item never aborts the rest
            }
        }
        if (uris.size > maxItems) rejected += null to FarError.InvalidInput("too many items (max $maxItems)")
        return IncomingResult(accepted, rejected)
    }

    private fun inspect(uri: Uri): IncomingItem {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) throw IllegalArgumentException("scheme")   // rejects file:// etc.
        var name: String? = null
        var size: Long? = null
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) }
                c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let { size = c.getLong(it) }
            }
        }
        val declared = resolver.getType(uri)
        val header = ByteArray(32)
        val read = resolver.openInputStream(uri)?.use { it.read(header) } ?: throw FileNotFoundException()
        val sniffed = sniffMime(header, read)
        val mismatch = declared != null && sniffed != null && !declared.equals(sniffed, ignoreCase = true) &&
            !(declared.startsWith("video/") && sniffed.startsWith("video/"))
        val effective = sniffed ?: declared
        if (effective == null || !(effective.startsWith("image/") || effective.startsWith("video/"))) {
            throw IllegalArgumentException("not media")
        }
        return IncomingItem(uri, name, size, declared, sniffed, mismatch)
    }

    /** Minimal magic-number sniffing; extend with the formats in spec section 8. */
    private fun sniffMime(b: ByteArray, n: Int): String? {
        fun at(i: Int, vararg v: Int) = n > i + v.size - 1 && v.indices.all { (b[i + it].toInt() and 0xFF) == v[it] }
        fun ascii(i: Int, s: String) = n >= i + s.length && s.indices.all { b[i + it].toInt().toChar() == s[it] }
        return when {
            at(0, 0xFF, 0xD8, 0xFF) -> "image/jpeg"
            at(0, 0x89, 0x50, 0x4E, 0x47) -> "image/png"
            ascii(0, "GIF8") -> "image/gif"
            ascii(0, "RIFF") && ascii(8, "WEBP") -> "image/webp"
            ascii(4, "ftyp") && (ascii(8, "heic") || ascii(8, "heix") || ascii(8, "mif1") || ascii(8, "msf1")) -> "image/heic"
            ascii(4, "ftyp") && ascii(8, "avif") -> "image/avif"
            ascii(4, "ftyp") && ascii(8, "qt") -> "video/quicktime"
            ascii(4, "ftyp") -> "video/mp4"
            at(0, 0x1A, 0x45, 0xDF, 0xA3) -> "video/x-matroska"   // MKV/WebM (EBML)
            ascii(0, "BM") -> "image/bmp"
            else -> null
        }
    }
}

`````

### core/storage/DocumentOps.kt

`````kotlin
package com.far.gallery.core.storage

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import com.far.gallery.core.common.FarError
import com.far.gallery.core.common.FarResult
import com.far.gallery.core.common.farCatching
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

data class DocEntry(
    val docId: String, val name: String, val mime: String, val size: Long?, val modified: Long?, val flags: Int,
) {
    val isDir get() = mime == Document.MIME_TYPE_DIR
    val canRename get() = flags and Document.FLAG_SUPPORTS_RENAME != 0
    val canDelete get() = flags and Document.FLAG_SUPPORTS_DELETE != 0
    val canMove get() = flags and Document.FLAG_SUPPORTS_MOVE != 0
    val canWriteInto get() = flags and Document.FLAG_DIR_SUPPORTS_CREATE != 0
}

enum class Collision { Rename, Replace, Skip }

sealed interface OpEvent {
    data class Progress(val done: Int, val total: Int, val current: String) : OpEvent
    data class Finished(val done: Int, val skipped: List<String>, val failed: List<Pair<String, FarError>>) : OpEvent
    data class Aborted(val error: FarError) : OpEvent
}

/** All SAF work. Provider quirks are expected: every call is wrapped, and unsupported actions are reported, never faked. */
class DocumentOps(private val r: ContentResolver) {
    private val projection = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
        Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED, Document.COLUMN_FLAGS)

    fun rootDocId(tree: Uri): String = DocumentsContract.getTreeDocumentId(tree)
    fun docUri(tree: Uri, docId: String): Uri = DocumentsContract.buildDocumentUriUsingTree(tree, docId)

    fun list(tree: Uri, parentDocId: String): FarResult<List<DocEntry>> = farCatching {
        val out = ArrayList<DocEntry>()
        r.query(DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentDocId), projection, null, null, null)?.use { c ->
            while (c.moveToNext()) out += DocEntry(c.getString(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(),
                if (c.isNull(3)) null else c.getLong(3), if (c.isNull(4)) null else c.getLong(4), c.getInt(5))
        } ?: throw IllegalStateException("provider returned null")
        out
    }

    fun create(tree: Uri, parentDocId: String, mime: String, name: String): FarResult<String> = farCatching {
        val u = DocumentsContract.createDocument(r, docUri(tree, parentDocId), mime, name) ?: throw java.io.IOException("create failed")
        DocumentsContract.getDocumentId(u)
    }

    fun rename(tree: Uri, docId: String, newName: String): FarResult<Unit> = farCatching {
        DocumentsContract.renameDocument(r, docUri(tree, docId), newName) ?: throw java.io.IOException("rename refused")
        Unit
    }

    fun delete(tree: Uri, docId: String): FarResult<Unit> = farCatching {
        if (!DocumentsContract.deleteDocument(r, docUri(tree, docId))) throw java.io.IOException("delete refused"); Unit
    }

    fun uniqueName(existing: Set<String>, name: String): String {
        if (name !in existing) return name
        val dot = name.lastIndexOf('.').takeIf { it > 0 }
        val base = dot?.let { name.substring(0, it) } ?: name; val ext = dot?.let { name.substring(it) } ?: ""
        var i = 1; while ("$base ($i)$ext" in existing) i++
        return "$base ($i)$ext"
    }

    /**
     * Copy or move [items] into destParent. Rules (spec 15.4): never overwrite silently, verify size before success,
     * delete the source only after the copy is verified, stop cleanly on cancel, keep going after a single-item failure.
     */
    fun transfer(
        srcTree: Uri, items: List<DocEntry>, dstTree: Uri, dstParentDocId: String, move: Boolean, policy: Collision,
    ): Flow<OpEvent> = flow {
        val skipped = ArrayList<String>(); val failed = ArrayList<Pair<String, FarError>>(); var done = 0
        val existing = (list(dstTree, dstParentDocId) as? FarResult.Ok)?.value?.map { it.name }?.toMutableSet()
            ?: run { emit(OpEvent.Aborted(FarError.ProviderUnavailable())); return@flow }
        items.forEachIndexed { index, item ->
            currentCoroutineContext().ensureActive()
            emit(OpEvent.Progress(index, items.size, item.name))
            if (item.name in existing && policy == Collision.Skip) { skipped += item.name; return@forEachIndexed }
            var targetName = item.name
            if (item.name in existing) {
                if (policy == Collision.Rename) targetName = uniqueName(existing, item.name)
                else if (policy == Collision.Replace) {
                    (list(dstTree, dstParentDocId) as? FarResult.Ok)?.value?.firstOrNull { it.name == item.name }?.let { delete(dstTree, it.docId) }
                }
            }
            val res = if (item.isDir) copyDir(srcTree, item, dstTree, dstParentDocId, targetName) else copyFile(srcTree, item, dstTree, dstParentDocId, targetName)
            when (res) {
                is FarResult.Ok -> {
                    existing += targetName; done++
                    if (move) delete(srcTree, item.docId).let { if (it is FarResult.Err) failed += item.name to it.error }   // source removed only after verified copy
                }
                is FarResult.Err -> {
                    failed += item.name to res.error
                    if (res.error is FarError.StorageFull || res.error is FarError.VolumeRemoved) { emit(OpEvent.Aborted(res.error)); return@flow }
                }
            }
        }
        emit(OpEvent.Finished(done, skipped, failed))
    }.flowOn(Dispatchers.IO)

    private fun copyFile(srcTree: Uri, f: DocEntry, dstTree: Uri, parent: String, name: String): FarResult<Unit> {
        val created = create(dstTree, parent, f.mime.ifEmpty { "application/octet-stream" }, name)
        val newId = (created as? FarResult.Ok)?.value ?: return (created as FarResult.Err)
        val res = farCatching {
            r.openInputStream(docUri(srcTree, f.docId))!!.use { i -> r.openOutputStream(docUri(dstTree, newId))!!.use { o -> i.copyTo(o) } }
            val got = r.query(docUri(dstTree, newId), arrayOf(Document.COLUMN_SIZE), null, null, null)?.use { if (it.moveToFirst()) it.getLong(0) else -1L } ?: -1L
            if (f.size != null && got >= 0 && got != f.size) throw java.io.IOException("size mismatch")    // verification
        }
        if (res is FarResult.Err) delete(dstTree, newId)                  // clean the partial output; source is untouched
        return res
    }

    private fun copyDir(srcTree: Uri, d: DocEntry, dstTree: Uri, parent: String, name: String, depth: Int = 0): FarResult<Unit> {
        if (depth > 32) return FarResult.Err(FarError.InvalidInput("folder too deep"))
        val newDir = (create(dstTree, parent, Document.MIME_TYPE_DIR, name) as? FarResult.Ok)?.value ?: return FarResult.Err(FarError.Io())
        val kids = (list(srcTree, d.docId) as? FarResult.Ok)?.value ?: return FarResult.Err(FarError.ProviderUnavailable())
        for (k in kids) {
            val r = if (k.isDir) copyDir(srcTree, k, dstTree, newDir, k.name, depth + 1) else copyFile(srcTree, k, dstTree, newDir, k.name)
            if (r is FarResult.Err) return r
        }
        return FarResult.Ok(Unit)
    }
}

/* ------------------------------ Checksums ------------------------------ */
object Checksums {
    fun sha256(input: InputStream): String {
        val md = MessageDigest.getInstance("SHA-256"); val buf = ByteArray(64 * 1024)
        while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }          // streaming: never whole-file in memory
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}

/* ------------------------------ Safe ZIP extraction ------------------------------ */
object SafeZip {
    const val MAX_ENTRIES = 5_000
    const val MAX_TOTAL_BYTES = 4L * 1024 * 1024 * 1024
    const val MAX_RATIO = 200                         // uncompressed/compressed guard against archive bombs

    sealed interface Entry { data class File(val path: String, val bytes: Long) : Entry; data class Unsafe(val raw: String) : Entry }

    /** Reject absolute paths, "..", drive prefixes and NULs. Returns null if unsafe. */
    fun sanitize(raw: String): String? {
        if (raw.contains('\u0000') || raw.startsWith("/") || raw.startsWith("\\") || Regex("^[A-Za-z]:").containsMatchIn(raw)) return null
        val parts = raw.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.any { it == ".." }) return null
        return parts.joinToString("/")
    }

    /** Streams entries to [sink]; throws InvalidInput on any safety-limit violation so the caller can clean partial output. */
    fun extract(input: InputStream, compressedSize: Long, sink: (path: String, isDir: Boolean, data: InputStream) -> Unit) {
        var entries = 0; var total = 0L
        ZipInputStream(input).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (++entries > MAX_ENTRIES) throw IllegalArgumentException("too many entries")
                val safe = sanitize(e.name) ?: throw IllegalArgumentException("unsafe path")
                if (e.isDirectory) { sink(safe, true, z); continue }
                val counting = object : InputStream() {
                    override fun read(): Int { val b = z.read(); if (b >= 0) bump(1); return b }
                    override fun read(buf: ByteArray, off: Int, len: Int): Int { val n = z.read(buf, off, len); if (n > 0) bump(n.toLong()); return n }
                    fun bump(n: Long) { total += n
                        if (total > MAX_TOTAL_BYTES) throw IllegalArgumentException("archive too large")
                        if (compressedSize > 0 && total / compressedSize > MAX_RATIO) throw IllegalArgumentException("suspicious compression ratio") }
                }
                sink(safe, false, counting)
            }
        }
    }
}

`````

### core/data/MediaRepository.kt

`````kotlin
package com.far.gallery.core.media

import android.content.ContentResolver
import android.content.ContentUris
import android.content.IntentSender
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import com.far.gallery.core.common.FarError
import com.far.gallery.core.common.FarResult
import com.far.gallery.core.common.farCatching
import com.far.gallery.core.database.FarDatabase
import com.far.gallery.core.database.FavoriteEntity
import com.far.gallery.feature.gallery.GalleryItem
import com.far.gallery.feature.gallery.LibraryZoom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

enum class MediaKind { All, Photos, Videos }
enum class SortKey { Taken, Added, Modified, Name, Size }

data class MediaFilter(
    val kind: MediaKind = MediaKind.All,
    val nameQuery: String? = null,
    val pathContains: String? = null,             // e.g. "Screenshots", "DCIM/Camera"
    val excludedPaths: List<String> = emptyList(),
    val minSizeBytes: Long? = null,
    val onlyIds: Set<Long>? = null,
    val trashedOnly: Boolean = false,
)

data class MediaRow(
    val id: Long, val uri: Uri, val name: String, val mime: String, val isVideo: Boolean,
    val sizeBytes: Long, val takenMs: Long, val durationMs: Long, val width: Int, val height: Int, val relativePath: String?,
)

class MediaRepository(private val resolver: ContentResolver) {

    private val filesUri: Uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)

    fun pager(filter: MediaFilter, sort: SortKey, descending: Boolean): Flow<PagingData<MediaRow>> =
        Pager(PagingConfig(pageSize = 120, prefetchDistance = 120, enablePlaceholders = false, initialLoadSize = 240)) {
            MediaPagingSource(resolver, filesUri, filter, sort, descending)
        }.flow

    /** Real consent flows. Never bypass: the IntentSender is launched by the UI with StartIntentSenderForResult. */
    fun trashRequest(uris: List<Uri>, trash: Boolean): IntentSender = MediaStore.createTrashRequest(resolver, uris, trash).intentSender
    fun deleteRequest(uris: List<Uri>): IntentSender = MediaStore.createDeleteRequest(resolver, uris).intentSender

    suspend fun exists(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        farCatching { resolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)?.use { it.moveToFirst() } ?: false }
            .let { (it as? FarResult.Ok)?.value ?: false }
    }
}

private class MediaPagingSource(
    private val resolver: ContentResolver, private val base: Uri,
    private val filter: MediaFilter, private val sort: SortKey, private val desc: Boolean,
) : PagingSource<Int, MediaRow>() {

    override fun getRefreshKey(state: PagingState<Int, MediaRow>): Int? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MediaRow> = withContext(Dispatchers.IO) {
        val offset = params.key ?: 0
        val limit = params.loadSize
        when (val r = farCatching { query(offset, limit) }) {
            is FarResult.Ok -> LoadResult.Page(r.value, prevKey = if (offset == 0) null else (offset - limit).coerceAtLeast(0),
                nextKey = if (r.value.size < limit) null else offset + r.value.size)
            is FarResult.Err -> LoadResult.Error(MediaQueryException(r.error))
        }
    }

    private fun query(offset: Int, limit: Int): List<MediaRow> {
        val sel = StringBuilder("(${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?,?))")
        val args = ArrayList<String>()
        args += when (filter.kind) {
            MediaKind.Videos -> listOf("3", "3"); MediaKind.Photos -> listOf("1", "1"); MediaKind.All -> listOf("1", "3")
        }
        filter.nameQuery?.takeIf { it.isNotBlank() }?.let { sel.append(" AND ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"); args += "%${it.trim()}%" }
        filter.pathContains?.let { sel.append(" AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"); args += "%$it%" }
        filter.excludedPaths.forEach { sel.append(" AND ${MediaStore.MediaColumns.RELATIVE_PATH} NOT LIKE ?"); args += "$it%" }
        filter.minSizeBytes?.let { sel.append(" AND ${MediaStore.MediaColumns.SIZE} >= ?"); args += it.toString() }
        filter.onlyIds?.takeIf { it.isNotEmpty() }?.let { ids -> sel.append(" AND ${MediaStore.MediaColumns._ID} IN (${ids.joinToString(",")})") }

        val sortCols = when (sort) {
            SortKey.Taken -> arrayOf(MediaStore.MediaColumns.DATE_TAKEN, MediaStore.MediaColumns.DATE_ADDED)
            SortKey.Added -> arrayOf(MediaStore.MediaColumns.DATE_ADDED)
            SortKey.Modified -> arrayOf(MediaStore.MediaColumns.DATE_MODIFIED)
            SortKey.Name -> arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)
            SortKey.Size -> arrayOf(MediaStore.MediaColumns.SIZE)
        }
        val bundle = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, sel.toString())
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args.toTypedArray())
            putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, sortCols)
            putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION,
                if (desc) ContentResolver.QUERY_SORT_DIRECTION_DESCENDING else ContentResolver.QUERY_SORT_DIRECTION_ASCENDING)
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, if (filter.trashedOnly) MediaStore.MATCH_ONLY else MediaStore.MATCH_EXCLUDE)
        }
        val projection = arrayOf(
            MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.MEDIA_TYPE, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED, MediaStore.MediaColumns.DURATION, MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT, MediaStore.MediaColumns.RELATIVE_PATH,
        )
        val out = ArrayList<MediaRow>(limit)
        resolver.query(base, projection, bundle, null)?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val iName = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val iMime = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val iType = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val iSize = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val iTaken = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
            val iAdded = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val iDur = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
            val iW = c.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
            val iH = c.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
            val iPath = c.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            while (c.moveToNext()) {
                val isVideo = c.getInt(iType) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val id = c.getLong(iId)
                val taken = c.getLong(iTaken).takeIf { it > 0 } ?: (c.getLong(iAdded) * 1000)   // documented fallback
                val collection = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                out += MediaRow(
                    id, ContentUris.withAppendedId(collection, id), c.getString(iName).orEmpty(), c.getString(iMime).orEmpty(), isVideo,
                    c.getLong(iSize), taken, c.getLong(iDur), c.getInt(iW), c.getInt(iH), c.getString(iPath),
                )
            }
        }
        return out
    }
}

class MediaQueryException(val error: FarError) : Exception(error.code)

/* ----------------------------- Gallery ViewModel ----------------------------- */
@OptIn(ExperimentalCoroutinesApi::class)
class GalleryViewModel(
    private val repo: MediaRepository, private val db: FarDatabase, initialFilter: MediaFilter = MediaFilter(),
) : ViewModel() {

    val filter = MutableStateFlow(initialFilter)
    val sort = MutableStateFlow(SortKey.Taken)
    val descending = MutableStateFlow(true)
    val zoom = MutableStateFlow(LibraryZoom.All)
    val selection = MutableStateFlow<Set<Long>>(emptySet())

    private val favoriteIds: StateFlow<Set<Long>> = db.favorites().ids().map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    private val excluded = db.folders().excluded().map { list -> list.map { it.relativePath } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val items: Flow<PagingData<GalleryItem>> =
        combine(filter, sort, descending, excluded, zoom) { f, s, d, ex, z -> Params(f.copy(excludedPaths = ex), s, d, z) }
            .flatMapLatest { p ->
                repo.pager(p.filter, p.sort, p.desc)
                    .map { page -> page.map<MediaRow, GalleryItem> { it.toItem(favoriteIds.value) } }
                    .map { page -> page.withHeaders(p.zoom) }
            }.cachedIn(viewModelScope)

    private data class Params(val filter: MediaFilter, val sort: SortKey, val desc: Boolean, val zoom: LibraryZoom)

    fun setKind(k: MediaKind) { filter.value = filter.value.copy(kind = k) }
    fun toggleSelect(id: Long) { selection.value = selection.value.let { if (id in it) it - id else it + id } }
    fun clearSelection() { selection.value = emptySet() }

    fun toggleFavorite(id: Long) = viewModelScope.launch {
        if (id in favoriteIds.value) db.favorites().remove(id) else db.favorites().add(FavoriteEntity(id, System.currentTimeMillis()))
    }

    companion object {
        fun factory(resolver: ContentResolver, db: FarDatabase, filter: MediaFilter = MediaFilter()): ViewModelProvider.Factory = viewModelFactory {
            initializer { GalleryViewModel(MediaRepository(resolver), db, filter) }
        }
    }
}

private fun MediaRow.toItem(favs: Set<Long>) = GalleryItem.Media(
    id = id, uri = uri.toString(),
    description = (if (isVideo) "Video, " else "Photo, ") + DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()).format(Instant.ofEpochMilli(takenMs).atZone(ZoneId.systemDefault())),
    isVideo = isVideo, durationLabel = if (isVideo) durationMs.toClock() else null,
    isFavorite = id in favs, isMotion = false, available = true, takenMs = takenMs,
)

private fun Long.toClock(): String { val s = this / 1000; return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) else "%d:%02d".format(s / 60, s % 60) }

/** Date headers: granularity follows the Years / Months / Days / All control. */
private fun PagingData<GalleryItem>.withHeaders(zoom: LibraryZoom): PagingData<GalleryItem> = insertSeparators { before, after ->
    val a = after as? GalleryItem.Media ?: return@insertSeparators null
    val b = before as? GalleryItem.Media
    val ma = a.takenDate(); val mb = b?.takenDate()
    val key: (LocalDate) -> Any = when (zoom) { LibraryZoom.Years -> { d -> d.year }; LibraryZoom.Months -> { d -> d.year * 100 + d.monthValue }; else -> { d -> d } }
    if (mb == null || key(ma) != key(mb)) GalleryItem.Header(headerLabel(ma, zoom)) else null
}

private fun GalleryItem.Media.takenDate(): LocalDate = Instant.ofEpochMilli(takenMs).atZone(ZoneId.systemDefault()).toLocalDate()

private fun headerLabel(d: LocalDate, zoom: LibraryZoom): String {
    val today = LocalDate.now()
    return when (zoom) {
        LibraryZoom.Years -> d.year.toString()
        LibraryZoom.Months -> DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()).format(d)
        else -> when (ChronoUnit.DAYS.between(d, today)) {
            0L -> "Today"; 1L -> "Yesterday"
            in 2L..6L -> DateTimeFormatter.ofPattern("EEEE", Locale.getDefault()).format(d)
            else -> DateTimeFormatter.ofPattern(if (d.year == today.year) "EEE, d MMM" else "d MMM yyyy", Locale.getDefault()).format(d)
        }
    }
}

`````

### core/data/Db.kt

`````kotlin
package com.far.gallery.core.database

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** App-owned metadata only. MediaStore stays the source of truth for media. NO vault tables. */
@Entity(tableName = "album")
data class AlbumEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String, val description: String? = null, val createdAt: Long,
    val pinned: Boolean = false, val sortOrder: Int = 0, val coverMediaId: Long? = null,
)

@Entity(tableName = "album_media", primaryKeys = ["albumId", "mediaId"], indices = [Index("mediaId")])
data class AlbumMediaRef(val albumId: Long, val mediaId: Long, val position: Int = 0)

@Entity(tableName = "favorite") data class FavoriteEntity(@PrimaryKey val mediaId: Long, val addedAt: Long)
@Entity(tableName = "tag") data class TagEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val color: Int)
@Entity(tableName = "media_tag", primaryKeys = ["mediaId", "tagId"], indices = [Index("tagId")]) data class MediaTagRef(val mediaId: Long, val tagId: Long)
@Entity(tableName = "excluded_folder") data class ExcludedFolder(@PrimaryKey val relativePath: String)
@Entity(tableName = "recent_view") data class RecentView(@PrimaryKey val mediaId: Long, val viewedAt: Long)
@Entity(tableName = "search_history") data class SearchHistory(@PrimaryKey val query: String, val at: Long)
@Entity(tableName = "saved_search") data class SavedSearch(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val queryJson: String)

@Entity(tableName = "folder_grant")
data class FolderGrant(@PrimaryKey val treeUri: String, val displayName: String, val addedAt: Long, val lastValidated: Long = 0)

@Entity(tableName = "file_favorite") data class FileFavorite(@PrimaryKey val documentUri: String, val name: String)

@Entity(tableName = "edit_recipe", indices = [Index("mediaId")])
data class EditRecipe(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, val mediaId: Long, val sourceSize: Long,
    val operationsJson: String, val outputUri: String?, val version: Int = 1, val updatedAt: Long,
)

/** Operation journal: written BEFORE long work starts so process death can be detected and recovered. */
@Entity(tableName = "operation")
data class OperationRecord(
    @PrimaryKey val id: String, val type: String, val status: String,      // Queued|Running|Paused|Completed|Cancelled|Failed|Interrupted
    val done: Int, val total: Int, val errorCode: String?, val updatedAt: Long,
)

@Dao interface AlbumDao {
    @Query("SELECT * FROM album ORDER BY pinned DESC, sortOrder, name") fun albums(): Flow<List<AlbumEntity>>
    @Query("SELECT mediaId FROM album_media WHERE albumId = :albumId ORDER BY position") fun mediaIds(albumId: Long): Flow<List<Long>>
    @Query("SELECT COUNT(*) FROM album_media WHERE albumId = :albumId") fun count(albumId: Long): Flow<Int>
    @Insert suspend fun insert(a: AlbumEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addMedia(refs: List<AlbumMediaRef>)
    @Query("DELETE FROM album_media WHERE albumId = :albumId AND mediaId IN (:ids)") suspend fun removeMedia(albumId: Long, ids: List<Long>)
    @Query("DELETE FROM album WHERE id = :id") suspend fun deleteAlbum(id: Long)          // media files are NOT touched
    @Query("DELETE FROM album_media WHERE albumId = :id") suspend fun clearAlbum(id: Long)
    @Query("UPDATE album SET name = :name WHERE id = :id") suspend fun rename(id: Long, name: String)
    @Query("UPDATE album SET pinned = :pinned WHERE id = :id") suspend fun pin(id: Long, pinned: Boolean)
    @Query("UPDATE album SET coverMediaId = :mediaId WHERE id = :id") suspend fun setCover(id: Long, mediaId: Long)
}

@Dao interface FavoriteDao {
    @Query("SELECT mediaId FROM favorite") fun ids(): Flow<List<Long>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun add(f: FavoriteEntity)
    @Query("DELETE FROM favorite WHERE mediaId = :id") suspend fun remove(id: Long)
}

@Dao interface FolderDao {
    @Query("SELECT * FROM folder_grant ORDER BY addedAt") fun grants(): Flow<List<FolderGrant>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun add(g: FolderGrant)
    @Query("DELETE FROM folder_grant WHERE treeUri = :uri") suspend fun remove(uri: String)
    @Query("SELECT * FROM excluded_folder") fun excluded(): Flow<List<ExcludedFolder>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun exclude(f: ExcludedFolder)
    @Query("DELETE FROM excluded_folder WHERE relativePath = :p") suspend fun include(p: String)
    @Query("SELECT * FROM file_favorite") fun favoriteFolders(): Flow<List<FileFavorite>>
}

@Dao interface HistoryDao {
    @Query("SELECT * FROM search_history ORDER BY at DESC LIMIT 20") fun recent(): Flow<List<SearchHistory>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun add(h: SearchHistory)
    @Query("DELETE FROM search_history") suspend fun clear()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun view(r: RecentView)
    @Query("SELECT mediaId FROM recent_view ORDER BY viewedAt DESC LIMIT 200") fun recentViews(): Flow<List<Long>>
}

@Dao interface OperationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(o: OperationRecord)
    @Query("UPDATE operation SET status = 'Interrupted', updatedAt = :now WHERE status IN ('Queued','Running')") suspend fun markInterrupted(now: Long)
    @Query("SELECT * FROM operation WHERE status = 'Interrupted'") fun interrupted(): Flow<List<OperationRecord>>
    @Query("DELETE FROM operation WHERE id = :id") suspend fun dismiss(id: String)
}

@Database(
    entities = [AlbumEntity::class, AlbumMediaRef::class, FavoriteEntity::class, TagEntity::class, MediaTagRef::class,
        ExcludedFolder::class, RecentView::class, SearchHistory::class, SavedSearch::class, FolderGrant::class,
        FileFavorite::class, EditRecipe::class, OperationRecord::class],
    version = 1, exportSchema = true,
)
abstract class FarDatabase : RoomDatabase() {
    abstract fun albums(): AlbumDao
    abstract fun favorites(): FavoriteDao
    abstract fun folders(): FolderDao
    abstract fun history(): HistoryDao
    abstract fun operations(): OperationDao

    companion object {
        @Volatile private var instance: FarDatabase? = null
        fun get(ctx: Context): FarDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(ctx.applicationContext, FarDatabase::class.java, "far.db")
                // NEVER fallbackToDestructiveMigration(): user albums/tags must survive upgrades. Add Migration objects per version.
                .build().also { instance = it }
        }
    }
}

`````

### ui/theme/Theme.kt

`````kotlin
package com.far.gallery.ui.theme

import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ThemeMode { System, Light, Dark, Amoled }

object FarPalette {
    val Blue = Color(0xFF0A84FF)
    val Green = Color(0xFF30D158)
    val Orange = Color(0xFFFF9F0A)
    val Pink = Color(0xFFFF375F)
    val Purple = Color(0xFFBF5AF2)
    val CameraYellow = Color(0xFFFFD60A)
    val RecordRed = Color(0xFFFF3B30)
    val curated = listOf(Blue, Green, Orange, Pink, Purple)
}

/** Glass tokens consumed by Modifier.glass(). */
@Immutable
data class GlassTokens(
    val tint: Color,
    val tintStrong: Color,
    val borderTop: Color,
    val borderBottom: Color,
    val fallback: Color,
    val blur: Dp,
)

val LocalGlass = compositionLocalOf { lightGlass() }
val LocalReducedMotion = compositionLocalOf { false }
val LocalReduceTransparency = compositionLocalOf { false }
val LocalIsDark = compositionLocalOf { false }

private fun lightGlass() = GlassTokens(
    tint = Color(0xFFFFFFFF).copy(alpha = 0.60f),
    tintStrong = Color(0xFFFFFFFF).copy(alpha = 0.74f),
    borderTop = Color.White.copy(alpha = 0.90f),
    borderBottom = Color.Black.copy(alpha = 0.08f),
    fallback = Color(0xFFF6F6FA),
    blur = 24.dp,
)

private fun darkGlass() = GlassTokens(
    tint = Color(0xFF1C1C1E).copy(alpha = 0.58f),
    tintStrong = Color(0xFF1C1C1E).copy(alpha = 0.74f),
    borderTop = Color.White.copy(alpha = 0.45f),
    borderBottom = Color.White.copy(alpha = 0.08f),
    fallback = Color(0xFF232326),
    blur = 24.dp,
)

private fun lightScheme(accent: Color) = lightColorScheme(
    primary = accent, onPrimary = Color.White,
    background = Color.White, onBackground = Color.Black,
    surface = Color.White, onSurface = Color.Black,
    surfaceVariant = Color(0xFFF2F2F7), onSurfaceVariant = Color(0xFF6C6C70),
    outlineVariant = Color(0x1F000000), error = Color(0xFFFF3B30),
)

private fun darkScheme(accent: Color, amoled: Boolean) = darkColorScheme(
    primary = accent, onPrimary = Color.White,
    background = if (amoled) Color.Black else Color(0xFF0B0B0F), onBackground = Color.White,
    surface = if (amoled) Color(0xFF0A0A0A) else Color(0xFF1C1C1E), onSurface = Color.White,
    surfaceVariant = if (amoled) Color(0xFF141414) else Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFFAEAEB2),
    outlineVariant = Color(0x33FFFFFF), error = Color(0xFFFF453A),
)

private val FarTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)

private val FarShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Spring for movement/size, short tween for opacity. */
object FarMotion {
    fun <T> spatial(reduced: Boolean = false): FiniteAnimationSpec<T> =
        if (reduced) tween(80) else spring(dampingRatio = 0.82f, stiffness = 380f)

    fun <T> effects(reduced: Boolean = false): FiniteAnimationSpec<T> =
        tween(if (reduced) 60 else 180, easing = FastOutSlowInEasing)
}

@Composable
fun rememberSystemReducedMotion(): Boolean {
    val ctx = LocalContext.current
    return remember {
        runCatching {
            Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}

@Composable
fun FarTheme(
    mode: ThemeMode = ThemeMode.System,
    accent: Color = FarPalette.Blue,
    useDynamicColor: Boolean = false,
    reduceMotion: Boolean = rememberSystemReducedMotion(),
    reduceTransparency: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark, ThemeMode.Amoled -> true
    }
    val ctx = LocalContext.current
    val scheme: ColorScheme = when {
        useDynamicColor && Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkScheme(accent, mode == ThemeMode.Amoled)
        else -> lightScheme(accent)
    }.let { if (dark && mode == ThemeMode.Amoled) it.copy(background = Color.Black, surface = Color(0xFF0A0A0A)) else it }

    CompositionLocalProvider(
        LocalGlass provides (if (dark) darkGlass() else lightGlass()),
        LocalReducedMotion provides reduceMotion,
        LocalReduceTransparency provides reduceTransparency,
        LocalIsDark provides dark,
    ) {
        MaterialTheme(colorScheme = scheme, typography = FarTypography, shapes = FarShapes, content = content)
    }
}

`````

### ui/glass/Glass.kt

`````kotlin
package com.far.gallery.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.far.gallery.ui.theme.LocalGlass
import com.far.gallery.ui.theme.LocalReduceTransparency
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/**
 * Glass = backdrop blur (Haze, RenderEffect on API 31+) + translucent tint + hairline border.
 * API 30 / reduce-transparency / low-power => opaque tint fallback with identical layout.
 *
 * Usage:
 *   val glass = rememberGlass()
 *   Box {
 *     Content(Modifier.glassSource(glass))                 // register what glass samples
 *     Row(Modifier.glass(glass, RoundedCornerShape(28.dp))) // any number of glass surfaces
 *   }
 * Verify the Haze API against the version you pin in libs.versions.toml.
 */
enum class GlassKind { Bar, Chip, Sheet, Overlay }

@Composable
fun rememberGlass(): HazeState = remember { HazeState() }

fun Modifier.glassSource(state: HazeState): Modifier = this.hazeSource(state)

@Composable
fun Modifier.glass(
    state: HazeState,
    shape: Shape,
    kind: GlassKind = GlassKind.Bar,
    forceFallback: Boolean = false,   // set true from a thermal/battery monitor
    elevated: Boolean = true,
): Modifier {
    val g = LocalGlass.current
    val reduce = LocalReduceTransparency.current || forceFallback
    val border = Brush.verticalGradient(listOf(g.borderTop, g.borderBottom))
    val base = if (elevated) this.shadow(12.dp, shape, ambientColor = Color.Black.copy(0.12f), spotColor = Color.Black.copy(0.12f)) else this

    if (reduce) {
        return base.clip(shape)
            .background(g.fallback.copy(alpha = 0.96f))
            .border(0.75.dp, border, shape)
    }

    val radius = when (kind) {
        GlassKind.Bar -> g.blur
        GlassKind.Chip -> g.blur * 0.7f
        GlassKind.Sheet -> g.blur * 1.4f
        GlassKind.Overlay -> g.blur * 1.8f
    }
    val tint = when (kind) {
        GlassKind.Sheet, GlassKind.Overlay -> g.tintStrong
        else -> g.tint
    }
    return base.clip(shape)
        .hazeEffect(state) {
            blurRadius = radius
            tints = listOf(HazeTint(tint))
            noiseFactor = 0.02f
            // Used when blur is unsupported (API < 31): opaque-ish tint instead.
            fallbackTint = HazeTint(g.fallback.copy(alpha = 0.94f))
        }
        .border(0.75.dp, border, shape)
}

@Composable
fun GlassSurface(
    state: HazeState,
    shape: Shape,
    modifier: Modifier = Modifier,
    kind: GlassKind = GlassKind.Bar,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.glass(state, shape, kind), content = content)
}

`````

### ui/glass/GlassBottomBar.kt

`````kotlin
package com.far.gallery.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.far.gallery.ui.glass.GlassKind
import com.far.gallery.ui.glass.glass
import com.far.gallery.ui.theme.FarMotion
import com.far.gallery.ui.theme.LocalReducedMotion
import dev.chrisbanes.haze.HazeState

enum class FarTab(val label: String, val outlined: ImageVector, val filled: ImageVector) {
    Photos("Photos", Icons.Outlined.PhotoLibrary, Icons.Filled.PhotoLibrary),
    Albums("Albums", Icons.Outlined.PhotoAlbum, Icons.Filled.PhotoAlbum),
    Search("Search", Icons.Outlined.Search, Icons.Filled.Search),
    Files("Files", Icons.Outlined.Folder, Icons.Filled.Folder),
}

/** Floating glass tab pill + detached circular camera button. Slides away when [visible] = false. */
@Composable
fun GlassBottomBar(
    glass: HazeState,
    selected: FarTab,
    onSelect: (FarTab) -> Unit,
    onCamera: () -> Unit,
    visible: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalReducedMotion.current
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(FarMotion.spatial(reduced)) { it } + fadeIn(FarMotion.effects(reduced)),
        exit = slideOutVertically(FarMotion.spatial(reduced)) { it } + fadeOut(FarMotion.effects(reduced)),
        modifier = modifier,
    ) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabPill(glass, selected, onSelect, Modifier.weight(1f))
            CameraButton(glass, onCamera)
        }
    }
}

@Composable
private fun TabPill(glass: HazeState, selected: FarTab, onSelect: (FarTab) -> Unit, modifier: Modifier) {
    val reduced = LocalReducedMotion.current
    val haptic = LocalHapticFeedback.current
    val tabs = FarTab.entries
    BoxWithConstraints(
        modifier.height(64.dp).glass(glass, RoundedCornerShape(32.dp)).padding(4.dp)
    ) {
        val tabWidth = maxWidth / tabs.size
        val indicatorX by animateDpAsState(tabWidth * tabs.indexOf(selected), FarMotion.spatial(reduced), label = "tabIndicator")
        Box(
            Modifier.offset(x = indicatorX).width(tabWidth).fillMaxWidth().height(56.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), RoundedCornerShape(28.dp))
        )
        Row(Modifier.fillMaxWidth()) {
            tabs.forEach { tab ->
                val isSel = tab == selected
                Column(
                    Modifier.width(tabWidth).height(56.dp)
                        .semantics { role = Role.Tab; this.selected = isSel; contentDescription = tab.label }
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            if (!isSel) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onSelect(tab) }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        if (isSel) tab.filled else tab.outlined, null,
                        Modifier.size(24.dp),
                        tint = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        tab.label, style = MaterialTheme.typography.labelMedium,
                        color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraButton(glass: HazeState, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Box(
        Modifier.size(64.dp).glass(glass, CircleShape, GlassKind.Bar)
            .semantics { role = Role.Button; contentDescription = "Open camera" }
            .clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.PhotoCamera, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurface)
    }
}

`````

### ui/feedback/Feedback.kt

`````kotlin
package com.far.gallery.ui.feedback

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.far.gallery.ui.glass.GlassKind
import com.far.gallery.ui.glass.glass
import com.far.gallery.ui.theme.LocalReducedMotion
import dev.chrisbanes.haze.HazeState

/** Every screen exposes one of these. Errors are NEVER rendered as an empty state. */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Content<T>(val data: T, val stale: Boolean = false) : UiState<T>
    data class Empty(val reason: EmptyReason) : UiState<Nothing>
    data class Failure(val error: ErrorInfo, val cached: Any? = null) : UiState<Nothing>
}

enum class EmptyReason { NoMedia, NoPermission, NoResults, NotIndexedYet, EmptyAlbum, EmptyFolder }

@Immutable
data class ErrorAction(val label: String, val primary: Boolean = false, val onClick: () -> Unit)

/** What happened -> is my file safe -> what can I do. */
@Immutable
data class ErrorInfo(
    val title: String,
    val whatHappened: String,
    val originalsSafe: Boolean = true,
    val code: String? = null,
    val actions: List<ErrorAction> = emptyList(),
)

@Composable
fun ErrorCard(error: ErrorInfo, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(24.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(22.dp))
            .padding(20.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.error)
        Text(error.title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(error.whatHappened, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (error.originalsSafe) {
            Text("Your original files have not been changed.", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        error.code?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            error.actions.forEach { a ->
                if (a.primary) Button(onClick = a.onClick) { Text(a.label) }
                else TextButton(onClick = a.onClick) { Text(a.label) }
            }
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector, title: String, body: String,
    modifier: Modifier = Modifier,
    primary: ErrorAction? = null, secondary: ErrorAction? = null,
) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, Modifier.padding(top = 16.dp), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(body, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        primary?.let { Button(onClick = it.onClick, modifier = Modifier.padding(top = 20.dp)) { Text(it.label) } }
        secondary?.let { TextButton(onClick = it.onClick) { Text(it.label) } }
    }
}

/** Shimmer-less pulse placeholder; static when reduced motion is on. */
@Composable
fun Modifier.skeleton(): Modifier {
    val reduced = LocalReducedMotion.current
    val alpha = if (reduced) 1f else {
        val t = rememberInfiniteTransition(label = "skeleton")
        val a by t.animateFloat(0.55f, 1f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "a")
        a
    }
    return this.alpha(alpha).background(MaterialTheme.colorScheme.surfaceVariant)
}

/** Bottom glass progress sheet for batch operations (copy, delete, backup, export). */
@Immutable
data class OperationUi(
    val title: String, val currentItem: String?, val done: Int, val total: Int,
    val bytesPerSecond: Long? = null, val etaSeconds: Int? = null,
    val canPause: Boolean = false, val paused: Boolean = false,
)

@Composable
fun OperationProgressSheet(
    glass: HazeState, op: OperationUi,
    onCancel: () -> Unit, onTogglePause: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val fraction = if (op.total == 0) 0f else op.done.toFloat() / op.total
    Column(
        modifier.fillMaxWidth().padding(16.dp).glass(glass, RoundedCornerShape(28.dp), GlassKind.Sheet)
            .padding(20.dp)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f) },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(op.title, style = MaterialTheme.typography.titleMedium)
        op.currentItem?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        LinearProgressIndicator(progress = { fraction }, Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${op.done} of ${op.total}", style = MaterialTheme.typography.labelMedium)
            op.etaSeconds?.let { Text("${it}s left", style = MaterialTheme.typography.labelMedium) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (op.canPause) TextButton(onClick = onTogglePause) { Text(if (op.paused) "Resume" else "Pause") }
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

/** Glass toast with optional action (Undo / View). Host it at the bottom above the tab bar. */
@Composable
fun GlassToast(glass: HazeState, message: String, actionLabel: String? = null, onAction: () -> Unit = {}, modifier: Modifier = Modifier) {
    Row(
        modifier.padding(horizontal = 16.dp).glass(glass, RoundedCornerShape(24.dp), GlassKind.Chip)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
        if (actionLabel != null) TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

`````

### ui/brand/FarLogo.kt

`````kotlin
package com.far.gallery.ui.brand

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.far.gallery.ui.theme.LocalReducedMotion

/**
 * In-app logo: dusk-gradient rounded square, sun and two mountain ridges.
 * Plays once on first composition (sun rises, ridges slide in); static when reduced motion is on.
 */
@Composable
fun FarLogo(modifier: Modifier = Modifier, size: Dp = 120.dp, animate: Boolean = true) {
    val reduced = LocalReducedMotion.current
    val progress = remember { Animatable(if (animate && !reduced) 0f else 1f) }
    LaunchedEffect(Unit) { if (animate && !reduced) progress.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }

    Canvas(modifier.size(size)) {
        val w = this.size.width
        val p = progress.value
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color(0xFF5B6CFF), Color(0xFFB45CFF), Color(0xFFFF8A65))),
            cornerRadius = CornerRadius(w * 0.24f), size = Size(w, w),
        )
        // sun rises from behind the ridge
        drawCircle(Color.White.copy(alpha = 0.95f), radius = w * 0.11f, center = Offset(w * 0.68f, w * (0.50f - 0.12f * p)))
        // back ridge
        val back = Path().apply {
            moveTo(0f, w * 0.80f); lineTo(w * 0.30f, w * (0.80f - 0.30f * p)); lineTo(w * 0.52f, w * 0.72f)
            lineTo(w * 0.74f, w * (0.80f - 0.22f * p)); lineTo(w, w * 0.78f); lineTo(w, w); lineTo(0f, w); close()
        }
        drawPath(back, Color.White.copy(alpha = 0.55f))
        // front ridge
        val front = Path().apply {
            moveTo(0f, w * 0.90f); lineTo(w * 0.42f, w * (0.90f - 0.36f * p)); lineTo(w, w * 0.88f)
            lineTo(w, w); lineTo(0f, w); close()
        }
        drawPath(front, Color.White)
    }
}

`````

### feature/gallery/GalleryScreen.kt

`````kotlin
package com.far.gallery.feature.gallery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import coil3.compose.SubcomposeAsyncImage
import com.far.gallery.ui.feedback.EmptyReason
import com.far.gallery.ui.feedback.EmptyState
import com.far.gallery.ui.feedback.ErrorAction
import com.far.gallery.ui.feedback.ErrorCard
import com.far.gallery.ui.feedback.ErrorInfo
import com.far.gallery.ui.feedback.skeleton
import com.far.gallery.ui.glass.GlassKind
import com.far.gallery.ui.glass.glass
import com.far.gallery.ui.glass.glassSource
import com.far.gallery.ui.theme.FarMotion
import com.far.gallery.ui.theme.LocalReducedMotion
import dev.chrisbanes.haze.HazeState

/** ---------- UI models (built from Room + MediaStore, never fabricated) ---------- */
sealed interface GalleryItem {
    val key: String

    @Immutable
    data class Header(val title: String, val subtitle: String? = null) : GalleryItem {
        override val key get() = "h_$title"
    }

    @Immutable
    data class Media(
        val id: Long,
        val uri: String,
        val description: String,          // e.g. "Photo, 4 Nov 2026"
        val isVideo: Boolean = false,
        val durationLabel: String? = null,
        val isFavorite: Boolean = false,
        val isMotion: Boolean = false,
        val available: Boolean = true,    // false when SD card removed / file missing
        val takenMs: Long = 0L,           // used for date headers
    ) : GalleryItem {
        override val key get() = "m_$id"
    }
}

enum class LibraryZoom(val label: String) { Years("Years"), Months("Months"), Days("Days"), All("All") }

@Composable
fun GalleryScreen(
    items: LazyPagingItems<GalleryItem>,
    glass: HazeState,
    zoom: LibraryZoom,
    onZoomChange: (LibraryZoom) -> Unit,
    selection: Set<Long>,
    onOpen: (GalleryItem.Media) -> Unit,
    onToggleSelect: (GalleryItem.Media) -> Unit,
    onRetry: () -> Unit,
    onRequestAccess: () -> Unit,
    mediaTileModifier: (GalleryItem.Media) -> Modifier = { Modifier },   // hook for shared-element transitions
    gridState: LazyGridState = rememberLazyGridState(),
    modifier: Modifier = Modifier,
) {
    var columns by remember { mutableIntStateOf(3) }
    val selecting = selection.isNotEmpty()

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val refresh = items.loadState.refresh
        when {
            refresh is LoadState.Loading && items.itemCount == 0 -> SkeletonGrid(columns)
            refresh is LoadState.Error && items.itemCount == 0 -> ErrorCard(
                ErrorInfo(
                    title = "Can't load your library",
                    whatHappened = "Far Gallery couldn't read the media index just now.",
                    code = "FG-MQ",
                    actions = listOf(ErrorAction("Retry", primary = true, onClick = onRetry)),
                ),
                Modifier.align(Alignment.Center),
            )
            items.itemCount == 0 -> EmptyState(
                icon = Icons.Filled.PhotoLibrary,
                title = "No photos or videos yet",
                body = "Far Gallery can only show media it has access to. Allow access, or pick photos to share.",
                primary = ErrorAction("Review access", true, onRequestAccess),
            )
            else -> Box(
                Modifier.fillMaxSize().pinchToChangeColumns { delta -> columns = (columns + delta).coerceIn(2, 6) }
            ) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxSize().glassSource(glass),
                    contentPadding = PaddingValues(top = 156.dp, bottom = 132.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(
                        count = items.itemCount,
                        key = items.itemKey { it.key },
                        span = { index -> if (items.peek(index) is GalleryItem.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
                        contentType = { index -> items.peek(index)?.let { it::class } },
                    ) { index ->
                        when (val item = items[index]) {
                            is GalleryItem.Header -> DateHeader(item)
                            is GalleryItem.Media -> MediaTile(
                                item = item,
                                selecting = selecting,
                                selected = item.id in selection,
                                onClick = { if (selecting) onToggleSelect(item) else onOpen(item) },
                                onLongClick = { onToggleSelect(item) },
                                modifier = Modifier.animateItem().then(mediaTileModifier(item)),
                            )
                            null -> Box(Modifier.aspectRatio(1f).skeleton())
                        }
                    }
                }
            }
        }

        LibraryTopBar(glass, zoom, onZoomChange, selection.size, Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun LibraryTopBar(glass: HazeState, zoom: LibraryZoom, onZoom: (LibraryZoom) -> Unit, selectedCount: Int, modifier: Modifier) {
    Column(modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            if (selectedCount > 0) "$selectedCount selected" else "Library",
            style = MaterialTheme.typography.displayLarge,
            modifier = Modifier.semantics { if (selectedCount > 0) stateDescription = "$selectedCount items selected" },
        )
        // Apple-Photos style segmented control in glass
        Row(
            Modifier.fillMaxWidth().glass(glass, RoundedCornerShape(20.dp), GlassKind.Chip).padding(3.dp),
        ) {
            LibraryZoom.entries.forEach { z ->
                val sel = z == zoom
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(17.dp))
                        .background(if (sel) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable { onZoom(z) }.padding(vertical = 7.dp)
                        .semantics { selected = sel },
                    contentAlignment = Alignment.Center,
                ) { Text(z.label, style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}

@Composable
private fun DateHeader(h: GalleryItem.Header) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp)) {
        Text(h.title, style = MaterialTheme.typography.titleMedium)
        h.subtitle?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaTile(
    item: GalleryItem.Media, selecting: Boolean, selected: Boolean,
    onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier,
) {
    val reduced = LocalReducedMotion.current
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(if (selected) 0.92f else 1f, FarMotion.spatial(reduced), label = "tileScale")
    Box(
        modifier.aspectRatio(1f).scale(scale).clip(RoundedCornerShape(2.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLongClick() },
            )
            .semantics {
                contentDescription = buildString {
                    append(item.description)
                    if (item.isFavorite) append(", favorite")
                    if (!item.available) append(", unavailable")
                }
                this.selected = selected
            },
    ) {
        // Thumbnail-sized request; failure affects THIS tile only.
        SubcomposeAsyncImage(
            model = item.uri, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            loading = { Box(Modifier.fillMaxSize().skeleton()) },
            error = {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.BrokenImage, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
        if (!item.available) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
        if (item.isFavorite) Icon(Icons.Filled.Favorite, null, Modifier.align(Alignment.BottomStart).padding(6.dp).size(14.dp), tint = Color.White)
        if (item.isVideo) {
            Row(Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color.Black.copy(0.5f), RoundedCornerShape(10.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.PlayArrow, null, Modifier.size(12.dp), tint = Color.White)
                item.durationLabel?.let { Text(it, color = Color.White, style = MaterialTheme.typography.labelMedium) }
            }
        }
        AnimatedVisibility(
            visible = selecting, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
            enter = scaleIn(FarMotion.spatial(reduced)) + fadeIn(), exit = scaleOut() + fadeOut(),
        ) {
            Box(
                Modifier.size(22.dp)
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(0.35f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { if (selected) Icon(Icons.Filled.Check, null, Modifier.size(14.dp), tint = Color.White) }
        }
    }
}

@Composable
private fun SkeletonGrid(columns: Int) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns), userScrollEnabled = false,
        contentPadding = PaddingValues(top = 156.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp),
    ) { items(columns * 8) { Box(Modifier.aspectRatio(1f).skeleton()) } }
}

/**
 * Two-finger pinch changes column count without stealing one-finger scrolling.
 * Observes on the Initial pass and consumes only while two pointers are down.
 */
private fun Modifier.pinchToChangeColumns(onStep: (Int) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var accumulated = 1f
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } >= 2) {
                accumulated *= event.calculateZoom()
                event.changes.forEach { it.consume() }
                if (accumulated > 1.3f) { onStep(-1); accumulated = 1f }      // spread  -> bigger tiles
                else if (accumulated < 0.77f) { onStep(+1); accumulated = 1f } // pinch   -> smaller tiles
            }
        } while (event.changes.any { it.pressed })
    }
}

`````

### feature/viewer/ViewerScreen.kt

`````kotlin
package com.far.gallery.feature.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalViewConfiguration
import coil3.compose.SubcomposeAsyncImage
import com.far.gallery.ui.feedback.ErrorAction
import com.far.gallery.ui.feedback.ErrorCard
import com.far.gallery.ui.feedback.ErrorInfo
import com.far.gallery.ui.glass.GlassKind
import com.far.gallery.ui.glass.glass
import com.far.gallery.ui.glass.glassSource
import dev.chrisbanes.haze.HazeState
import kotlin.math.abs

@Immutable
data class ViewerItem(
    val id: Long, val uri: String, val title: String, val subtitle: String?,
    val isVideo: Boolean, val isFavorite: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    items: List<ViewerItem>,
    startIndex: Int,
    glass: HazeState,
    onClose: () -> Unit,
    onFavorite: (ViewerItem) -> Unit,
    onShare: (ViewerItem) -> Unit,
    onEdit: (ViewerItem) -> Unit,
    onDelete: (ViewerItem) -> Unit,
    detailsContent: @Composable (ViewerItem) -> Unit,
    videoContent: @Composable (ViewerItem) -> Unit,                 // Media3 player goes here
    imageModifier: (ViewerItem) -> Modifier = { Modifier },         // shared-element hook
    modifier: Modifier = Modifier,
) {
    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))) { items.size }
    var chromeVisible by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var dismissProgress by remember { mutableFloatStateOf(0f) }     // 0..1 while dragging down
    var showDetails by remember { mutableStateOf(false) }

    Box(modifier.fillMaxSize().background(Color.Black.copy(alpha = 1f - dismissProgress * 0.9f))) {
        HorizontalPager(
            state = pager, userScrollEnabled = !zoomed, beyondViewportPageCount = 1,
            key = { items[it].id }, modifier = Modifier.fillMaxSize().glassSource(glass),
        ) { page ->
            val item = items[page]
            if (item.isVideo) videoContent(item)
            else ZoomableImage(
                uri = item.uri,
                onZoomed = { zoomed = it },
                onDismissProgress = { dismissProgress = it },
                onDismiss = onClose,
                onTap = { chromeVisible = !chromeVisible },
                imageModifier = imageModifier(item),
            )
        }

        val current = items.getOrNull(pager.currentPage)
        if (current != null) {
            AnimatedVisibility(chromeVisible && dismissProgress == 0f, Modifier.align(Alignment.TopCenter), enter = fadeIn(), exit = fadeOut()) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp).glass(glass, RoundedCornerShape(28.dp), GlassKind.Overlay)
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
                    Column(Modifier.weight(1f)) {
                        Text(current.title, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        current.subtitle?.let { Text(it, color = Color.White.copy(0.7f), style = MaterialTheme.typography.labelMedium, maxLines = 1) }
                    }
                    IconButton(onClick = { onFavorite(current) }) {
                        Icon(
                            if (current.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            if (current.isFavorite) "Remove from favorites" else "Add to favorites", tint = Color.White,
                        )
                    }
                }
            }
            AnimatedVisibility(chromeVisible && dismissProgress == 0f, Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).glass(glass, RoundedCornerShape(32.dp), GlassKind.Overlay).padding(6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    IconButton(onClick = { onShare(current) }) { Icon(Icons.Filled.Share, "Share", tint = Color.White) }
                    if (!current.isVideo) IconButton(onClick = { onEdit(current) }) { Icon(Icons.Filled.Edit, "Edit", tint = Color.White) }
                    IconButton(onClick = { showDetails = true }) { Icon(Icons.Filled.Info, "Details", tint = Color.White) }
                    IconButton(onClick = { onDelete(current) }) { Icon(Icons.Filled.Delete, "Delete", tint = Color.White) }
                }
            }
            if (showDetails) {
                ModalBottomSheet(onDismissRequest = { showDetails = false }, containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)) {
                    detailsContent(current)
                }
            }
        }
    }
}

/**
 * Pinch/double-tap zoom, pan when zoomed, swipe-down-to-dismiss at 1x.
 * Horizontal drags are NOT consumed at 1x so the pager keeps working.
 */
@Composable
fun ZoomableImage(
    uri: String,
    onZoomed: (Boolean) -> Unit,
    onDismissProgress: (Float) -> Unit,
    onDismiss: () -> Unit,
    onTap: () -> Unit,
    imageModifier: Modifier = Modifier,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var failed by remember { mutableStateOf(false) }
    val slop = LocalViewConfiguration.current.touchSlop

    fun clampOffset(o: Offset, s: Float): Offset {
        val maxX = (size.width * (s - 1f)) / 2f
        val maxY = (size.height * (s - 1f)) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    Box(
        Modifier.fillMaxSize().onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                        onZoomed(scale > 1f)
                    },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var totalDy = 0f
                    var totalDx = 0f
                    do {
                        val event = awaitPointerEvent()
                        val multi = event.changes.count { it.pressed } > 1
                        if (multi || scale > 1f) {
                            scale = (scale * event.calculateZoom()).coerceIn(1f, 6f)
                            offset = clampOffset(offset + event.calculatePan(), scale)
                            onZoomed(scale > 1f)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } else {
                            val pan = event.calculatePan()
                            totalDx += pan.x; totalDy += pan.y
                            if (totalDy > slop && abs(totalDy) > abs(totalDx) * 1.5f) {   // vertical-dominant drag down
                                dragY = totalDy
                                onDismissProgress((dragY / (size.height * 0.4f)).coerceIn(0f, 1f))
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                    if (dragY > size.height * 0.18f) onDismiss()
                    dragY = 0f; onDismissProgress(0f)
                    if (scale < 1.02f) { scale = 1f; offset = Offset.Zero; onZoomed(false) }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (failed) {
            ErrorCard(
                ErrorInfo(
                    title = "Preview unavailable",
                    whatHappened = "This image is damaged or uses a format this device can't display.",
                    code = "FG-COR",
                    actions = listOf(ErrorAction("Close", false, onDismiss)),
                ),
            )
        } else {
            val dragScale = 1f - (dragY / (size.height.coerceAtLeast(1) * 0.4f)).coerceIn(0f, 1f) * 0.15f
            SubcomposeAsyncImage(
                model = uri, contentDescription = null, contentScale = ContentScale.Fit,
                onError = { failed = true },
                modifier = imageModifier.fillMaxSize().graphicsLayer {
                    scaleX = scale * dragScale; scaleY = scale * dragScale
                    translationX = offset.x; translationY = offset.y + dragY
                },
                loading = { Box(Modifier.size(48.dp)) },
                error = { Icon(Icons.Filled.BrokenImage, null, tint = Color.White.copy(0.6f)) },
            )
        }
    }
}

`````

### feature/video/VideoPlayer.kt

`````kotlin
package com.far.gallery.feature.video

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Composition
import androidx.media3.transformer.Transformer
import androidx.media3.ui.PlayerView
import com.far.gallery.core.common.FarError
import com.far.gallery.core.common.FarResult
import com.far.gallery.core.common.farCatching
import com.far.gallery.ui.feedback.ErrorAction
import com.far.gallery.ui.feedback.ErrorCard
import com.far.gallery.ui.feedback.ErrorInfo
import com.far.gallery.ui.glass.GlassKind
import com.far.gallery.ui.glass.glass
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

private val speeds = listOf(0.5f, 1f, 1.5f, 2f)

/** Media3 player + glass controls. Playback failure shows a card with Retry / Open with and never crashes the viewer. */
@OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoPlayer(
    uri: String, glass: HazeState, resumeMs: Long?, onPosition: (Long) -> Unit,
    onFrameGrab: (Long) -> Unit, onTrim: (Long) -> Unit, onOpenWith: () -> Unit, modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    var error by remember { mutableStateOf<ErrorInfo?>(null) }
    var playing by remember { mutableStateOf(true) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var speedIdx by remember { mutableStateOf(1) }
    var attempt by remember { mutableStateOf(0) }

    val player = remember(attempt) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(uri)); prepare()
            resumeMs?.let { seekTo(it) }
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlayerError(e: PlaybackException) {
                    error = ErrorInfo(
                        "Can't play this video",
                        "This video uses a codec or profile this device can't play.",
                        originalsSafe = true, code = "FG-VID-${e.errorCodeName}",
                        actions = listOf(ErrorAction("Retry", true) { error = null; attempt++ }, ErrorAction("Open with…", false, onOpenWith)),
                    )
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            })
        }
    }
    DisposableEffect(player) { onDispose { onPosition(player.currentPosition); player.release() } }   // always release
    LaunchedEffect(player) { while (true) { pos = player.currentPosition; dur = player.duration.coerceAtLeast(0); delay(250) } }

    Box(modifier.fillMaxSize().background0()) {
        if (error != null) ErrorCard(error!!, Modifier.align(Alignment.Center))
        else AndroidView(factory = { PlayerView(it).apply { this.player = player; useController = false } }, modifier = Modifier.fillMaxSize())

        if (error == null) Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 84.dp, start = 16.dp, end = 16.dp)
                .glass(glass, RoundedCornerShape(24.dp), GlassKind.Overlay).padding(12.dp),
        ) {
            Slider(value = if (dur > 0) pos.toFloat() / dur else 0f, onValueChange = { player.seekTo((it * dur).toLong()) })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${clock(pos)} / ${clock(dur)}", color = Color.White, style = MaterialTheme.typography.labelMedium)
                Row {
                    IconButton({ if (player.isPlaying) player.pause() else player.play() }) {
                        Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Pause" else "Play", tint = Color.White)
                    }
                    TextButton({ speedIdx = (speedIdx + 1) % speeds.size; player.setPlaybackSpeed(speeds[speedIdx]) }) { Text("${speeds[speedIdx]}×", color = Color.White) }
                    AudioTrackButton(player)
                    IconButton({ onFrameGrab(player.currentPosition) }) { Icon(Icons.Filled.PhotoCamera, "Save frame", tint = Color.White) }
                    IconButton({ onTrim(player.duration) }) { Icon(Icons.Filled.ContentCut, "Trim", tint = Color.White) }
                    if (ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                        IconButton({ (ctx as? Activity)?.enterPictureInPictureMode(PictureInPictureParams.Builder().build()) }) {
                            Icon(Icons.Filled.PictureInPictureAlt, "Picture in picture", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.background0() = this.then(Modifier)   // keep black background supplied by the viewer

@Composable
private fun AudioTrackButton(player: Player) {
    val groups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
    if (groups.size < 2) return                                     // hide when there is nothing to choose
    var idx by remember { mutableStateOf(0) }
    TextButton({
        idx = (idx + 1) % groups.size
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(groups[idx].mediaTrackGroup, 0)).build()
    }) { Text("Audio ${idx + 1}/${groups.size}", color = Color.White) }
}

private fun clock(ms: Long): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }

/* ------------------------------ Frame grab ------------------------------ */
object FrameGrabber {
    /** Saves the frame as a NEW JPEG in Pictures/Far Gallery. The video is never modified. */
    suspend fun grab(ctx: Context, video: Uri, positionMs: Long): FarResult<Uri> = withContext(Dispatchers.IO) {
        farCatching {
            val mmr = MediaMetadataRetriever()
            val bmp: Bitmap = try {
                mmr.setDataSource(ctx, video)
                mmr.getFrameAtTime(positionMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST) ?: throw java.io.IOException("no frame")
            } finally { mmr.release() }
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "FRAME_${System.currentTimeMillis()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Far Gallery")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val r = ctx.contentResolver
            val out = r.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: throw java.io.IOException("insert failed")
            try {
                r.openOutputStream(out)!!.use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                r.update(out, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) { r.delete(out, null, null); throw e }
            out
        }
    }
}

/* ------------------------------ Trim (save as copy) ------------------------------ */
object VideoTrimmer {
    /** Re-exports [startMs, endMs] to a temp file, then publishes it via MediaStore. Cancellable; the source is never touched. */
    @OptIn(androidx.media3.common.util.UnstableApi::class)
    suspend fun trim(ctx: Context, source: Uri, startMs: Long, endMs: Long, onProgress: (Float) -> Unit): FarResult<Uri> {
        val tmp = File(ctx.cacheDir, "trim_${System.currentTimeMillis()}.mp4")
        val exported = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<FarResult<Unit>> { cont ->
                val item = MediaItem.Builder().setUri(source).setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder().setStartPositionMs(startMs).setEndPositionMs(endMs).build()).build()
                val t = Transformer.Builder(ctx).addListener(object : Transformer.Listener {
                    override fun onCompleted(c: Composition, r: ExportResult) { if (cont.isActive) cont.resume(FarResult.Ok(Unit)) }
                    override fun onError(c: Composition, r: ExportResult, e: ExportException) { if (cont.isActive) cont.resume(FarResult.Err(FarError.Io("FG-TRIM-${e.errorCodeName}"))) }
                }).build()
                t.start(EditedMediaItem.Builder(item).build(), tmp.absolutePath)
                cont.invokeOnCancellation { t.cancel(); tmp.delete() }
            }
        }
        if (exported is FarResult.Err) { tmp.delete(); return exported }
        return withContext(Dispatchers.IO) {
            farCatching {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, "TRIM_${System.currentTimeMillis()}.mp4")
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Far Gallery")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val r = ctx.contentResolver
                val out = r.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: throw java.io.IOException("insert failed")
                try {
                    tmp.inputStream().use { i -> r.openOutputStream(out)!!.use { i.copyTo(it) } }
                    r.update(out, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
                } catch (e: Exception) { r.delete(out, null, null); throw e } finally { tmp.delete() }
                out
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrimPanel(durationMs: Long, busy: Boolean, progress: Float?, onSave: (Long, Long) -> Unit, onCancel: () -> Unit) {
    var range by remember { mutableStateOf(0f..1f) }
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Trim video", style = MaterialTheme.typography.titleMedium)
        RangeSlider(value = range, onValueChange = { range = it }, enabled = !busy)
        Text("${clock((range.start * durationMs).toLong())} – ${clock((range.endInclusive * durationMs).toLong())}", style = MaterialTheme.typography.labelMedium)
        Text("Saved as a new copy. The original stays untouched.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (busy) LinearProgressIndicator(progress = { progress ?: 0f }, Modifier.fillMaxWidth())
        Row { TextButton(onCancel) { Text(if (busy) "Cancel export" else "Close") }
            if (!busy) TextButton({ onSave((range.start * durationMs).toLong(), (range.endInclusive * durationMs).toLong()) }) { Text("Save copy") } }
    }
}

`````

### feature/editor/EditorScreen.kt

`````kotlin
package com.far.gallery.feature.editor

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix as AColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.far.gallery.core.common.FarResult
import com.far.gallery.core.common.farCatching
import com.far.gallery.ui.glass.GlassKind
import com.far.gallery.ui.glass.glass
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/** One immutable snapshot per undo step. Replaying it on the source bitmap is the whole edit model (non-destructive). */
@Immutable
data class EditState(
    val brightness: Float = 0f,      // -1..1
    val contrast: Float = 0f,        // -1..1
    val saturation: Float = 0f,      // -1..1
    val warmth: Float = 0f,          // -1..1
    val rotationQuarter: Int = 0,    // 0..3
    val flipH: Boolean = false,
    val cropRatio: Float? = null,    // width/height, null = original
)

class EditHistory {
    private val undo = ArrayDeque<EditState>()
    private val redo = ArrayDeque<EditState>()
    var current by mutableStateOf(EditState()); private set
    val canUndo get() = undo.isNotEmpty()
    val canRedo get() = redo.isNotEmpty()
    fun commit(s: EditState) { if (s != current) { undo.addLast(current); redo.clear(); current = s } }
    fun undo() { undo.removeLastOrNull()?.let { redo.addLast(current); current = it } }
    fun redo() { redo.removeLastOrNull()?.let { undo.addLast(current); current = it } }
}

/** 4x5 colour matrix shared by preview and export so what you see is what you save. */
fun EditState.matrixValues(): FloatArray {
    val m = AColorMatrix()
    val c = 1f + contrast; val t = (1f - c) / 2f * 255f
    m.postConcat(AColorMatrix(floatArrayOf(c,0f,0f,0f,t, 0f,c,0f,0f,t, 0f,0f,c,0f,t, 0f,0f,0f,1f,0f)))
    val b = brightness * 255f
    m.postConcat(AColorMatrix(floatArrayOf(1f,0f,0f,0f,b, 0f,1f,0f,0f,b, 0f,0f,1f,0f,b, 0f,0f,0f,1f,0f)))
    val s = AColorMatrix().apply { setSaturation(1f + saturation) }; m.postConcat(s)
    val w = warmth * 40f
    m.postConcat(AColorMatrix(floatArrayOf(1f,0f,0f,0f,w, 0f,1f,0f,0f,0f, 0f,0f,1f,0f,-w, 0f,0f,0f,1f,0f)))
    return m.array
}

object EditorEngine {
    /** Bounded decode (long edge <= maxEdge) so editing never allocates a full-resolution bitmap by surprise. */
    suspend fun load(ctx: Context, uri: Uri, maxEdge: Int): FarResult<Bitmap> = withContext(Dispatchers.IO) {
        farCatching {
            val src = ImageDecoder.createSource(ctx.contentResolver, uri)
            ImageDecoder.decodeBitmap(src) { info, d ->
                val longEdge = max(info.size.width, info.size.height)
                if (longEdge > maxEdge) { val f = maxEdge.toFloat() / longEdge; d.setTargetSize((info.size.width * f).toInt(), (info.size.height * f).toInt()) }
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                d.isMutableRequired = false
            }
        }
    }

    fun render(src: Bitmap, s: EditState): Bitmap {
        val m = Matrix().apply { postRotate(90f * s.rotationQuarter); if (s.flipH) postScale(-1f, 1f) }
        var out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        s.cropRatio?.let { r ->
            val w = out.width; val h = out.height
            val cw: Int; val ch: Int
            if (w.toFloat() / h > r) { ch = h; cw = (h * r).toInt() } else { cw = w; ch = (w / r).toInt() }
            out = Bitmap.createBitmap(out, (w - cw) / 2, (h - ch) / 2, cw, ch)
        }
        val result = Bitmap.createBitmap(out.width, out.height, Bitmap.Config.ARGB_8888)
        Canvas(result).drawBitmap(out, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(AColorMatrix(s.matrixValues())) })
        return result
    }

    /** Save a COPY to Pictures/Far Gallery. Never touches the original. EXIF is not carried over (stripped by design; disclosed in UI). */
    suspend fun saveCopy(ctx: Context, src: Bitmap, s: EditState, quality: Int): FarResult<Uri> = withContext(Dispatchers.IO) {
        farCatching {
            val bmp = render(src, s)
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "EDIT_${System.currentTimeMillis()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Far Gallery")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val r = ctx.contentResolver
            val out = r.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: throw java.io.IOException("insert failed")
            try {
                r.openOutputStream(out)!!.use { if (!bmp.compress(Bitmap.CompressFormat.JPEG, quality, it)) throw java.io.IOException("encode failed") }
                r.update(out, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) { r.delete(out, null, null); throw e }      // no half-written file is ever left visible
            out
        }
    }
}

enum class EditTab(val label: String) { Adjust("Adjust"), Crop("Crop"), Rotate("Rotate") }

@Composable
fun EditorScreen(
    source: Bitmap?, loading: Boolean, errorText: String?, glass: HazeState, saving: Boolean,
    onCancel: () -> Unit, onSave: (EditState) -> Unit, modifier: Modifier = Modifier,
) {
    val history = remember { EditHistory() }
    var tab by remember { mutableStateOf(EditTab.Adjust) }
    val s = history.current
    Box(modifier.fillMaxSize().background(Color.Black)) {
        when {
            loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            errorText != null -> Text(errorText, Modifier.align(Alignment.Center).padding(32.dp), color = Color.White)
            source != null -> Image(
                source.asImageBitmap(), null, contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(s.matrixValues())),
                modifier = Modifier.fillMaxSize().padding(top = 96.dp, bottom = 220.dp)
                    .graphicsLayer { rotationZ = 90f * s.rotationQuarter; scaleX = if (s.flipH) -1f else 1f },
            )
        }
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp).glass(glass, RoundedCornerShape(26.dp), GlassKind.Overlay).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onCancel) { Text("Cancel", color = Color.White) }
            Row { TextButton(history::undo, enabled = history.canUndo) { Text("Undo", color = Color.White) }
                TextButton(history::redo, enabled = history.canRedo) { Text("Redo", color = Color.White) } }
            TextButton({ onSave(s) }, enabled = source != null && !saving) { Text(if (saving) "Saving…" else "Save copy") }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(12.dp)
            .glass(glass, RoundedCornerShape(28.dp), GlassKind.Sheet).padding(16.dp)) {
            when (tab) {
                EditTab.Adjust -> {
                    AdjustSlider("Brightness", s.brightness) { history.commit(s.copy(brightness = it)) }
                    AdjustSlider("Contrast", s.contrast) { history.commit(s.copy(contrast = it)) }
                    AdjustSlider("Saturation", s.saturation) { history.commit(s.copy(saturation = it)) }
                    AdjustSlider("Warmth", s.warmth) { history.commit(s.copy(warmth = it)) }
                }
                EditTab.Crop -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Original" to null, "1:1" to 1f, "4:3" to 4f / 3f, "3:2" to 1.5f, "16:9" to 16f / 9f).forEach { (l, r) ->
                        TextButton({ history.commit(s.copy(cropRatio = r)) }) { Text(l, color = if (s.cropRatio == r) MaterialTheme.colorScheme.primary else Color.Unspecified) } } }
                EditTab.Rotate -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton({ history.commit(s.copy(rotationQuarter = (s.rotationQuarter + 3) % 4)) }) { Text("Rotate left") }
                    TextButton({ history.commit(s.copy(rotationQuarter = (s.rotationQuarter + 1) % 4)) }) { Text("Rotate right") }
                    TextButton({ history.commit(s.copy(flipH = !s.flipH)) }) { Text("Flip") } }
            }
            Text("Saved as a new JPEG. Original and its metadata are untouched; metadata is not copied.",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                EditTab.entries.forEach { t -> TextButton({ tab = t }) { Text(t.label, color = if (t == tab) MaterialTheme.colorScheme.primary else Color.Unspecified) } } }
        }
    }
}

@Composable
private fun AdjustSlider(label: String, value: Float, onCommit: (Float) -> Unit) {
    var live by remember(value) { mutableStateOf(value) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.padding(end = 8.dp), style = MaterialTheme.typography.labelMedium)
        Slider(live, { live = it }, onValueChangeFinished = { onCommit(live) }, valueRange = -1f..1f)   // one undo step per drag
    }
}

`````

### feature/albums/LibraryScreens.kt

`````kotlin
package com.far.gallery.feature.albums

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.far.gallery.ui.feedback.EmptyState
import com.far.gallery.ui.feedback.skeleton
import com.far.gallery.ui.glass.GlassKind
import com.far.gallery.ui.glass.glass
import com.far.gallery.ui.glass.glassSource
import dev.chrisbanes.haze.HazeState

/* ------------------------------------ Albums ------------------------------------ */
@Immutable
data class AlbumUi(val id: String, val name: String, val count: Int, val coverUri: String?, val pinned: Boolean, val isSystem: Boolean)

@Immutable
data class MemoryUi(val id: String, val title: String, val subtitle: String, val coverUri: String?)

@Composable
fun AlbumsScreen(
    albums: List<AlbumUi>, memories: List<MemoryUi>, glass: HazeState,
    onOpenAlbum: (AlbumUi) -> Unit, onOpenMemory: (MemoryUi) -> Unit, onCreateAlbum: (String) -> Unit,
    onTogglePin: (AlbumUi) -> Unit, modifier: Modifier = Modifier,
) {
    var creating by remember { mutableStateOf(false) }
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyVerticalGrid(
            GridCells.Fixed(2), Modifier.fillMaxSize().glassSource(glass),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 112.dp, bottom = 132.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (memories.isNotEmpty()) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                Column { Text("Memories", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(memories, key = { it.id }) { MemoryCard(it) { onOpenMemory(it) } } } }
            }
            if (albums.isEmpty()) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                EmptyState(Icons.Filled.Add, "No albums yet", "Create an album to collect photos from anywhere. Photos stay where they are.", Modifier.height(260.dp))
            }
            items(albums, key = { it.id }) { a ->
                Column(Modifier.clickable { onOpenAlbum(a) }) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                        if (a.coverUri != null) AsyncImage(a.coverUri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        if (a.pinned) Icon(Icons.Filled.PushPin, "Pinned", Modifier.align(Alignment.TopEnd).padding(8.dp))
                    }
                    Text(a.name, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                    Text("${a.count}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Albums", style = MaterialTheme.typography.displayLarge)
            IconButton({ creating = true }, Modifier.glass(glass, RoundedCornerShape(22.dp), GlassKind.Chip)) { Icon(Icons.Filled.Add, "New album") }
        }
    }
    if (creating) {
        var name by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { creating = false }, title = { Text("New album") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") }) },
            confirmButton = { TextButton({ if (name.isNotBlank()) { onCreateAlbum(name.trim()); creating = false } }) { Text("Create") } },
            dismissButton = { TextButton({ creating = false }) { Text("Cancel") } })
    }
}

@Composable
private fun MemoryCard(m: MemoryUi, onClick: () -> Unit) {
    Box(Modifier.width(240.dp).height(150.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick)) {
        m.coverUri?.let { AsyncImage(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(androidx.compose.ui.graphics.Color.Black.copy(0.45f)).padding(12.dp)) {
            Text(m.title, color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.titleMedium)
            Text(m.subtitle, color = androidx.compose.ui.graphics.Color.White.copy(0.8f), style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Memories are plain rules over real dates (no cloud, no faces): "On this day" = same month/day in earlier years. */
object MemoryRules {
    fun onThisDay(takenDates: List<Pair<Long, Long>>, today: java.time.LocalDate = java.time.LocalDate.now()): Map<Int, List<Long>> =
        takenDates.groupBy({ java.time.Instant.ofEpochMilli(it.second).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }, { it.first })
            .filterKeys { it.monthValue == today.monthValue && it.dayOfMonth == today.dayOfMonth && it.year < today.year }
            .entries.associate { it.key.year to it.value }
}

/* ------------------------------------ Search ------------------------------------ */
enum class SearchScope(val label: String) { All("All"), Photos("Photos"), Videos("Videos"), Screenshots("Screenshots"), Favorites("Favorites"), Large("Large files") }

sealed interface IndexStatus { data object Ready : IndexStatus; data class Indexing(val percent: Int) : IndexStatus; data object Disabled : IndexStatus; data object LimitedAccess : IndexStatus }

@Composable
fun SearchScreen(
    query: String, onQuery: (String) -> Unit, scope: SearchScope, onScope: (SearchScope) -> Unit,
    recent: List<String>, onClearRecent: () -> Unit, index: IndexStatus, resultCount: Int?,
    glass: HazeState, results: @Composable () -> Unit, modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.fillMaxSize().glassSource(glass).padding(top = 176.dp)) {
            when {
                query.isBlank() -> Column(Modifier.padding(16.dp)) {
                    if (recent.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Recent", style = MaterialTheme.typography.titleMedium); TextButton(onClearRecent) { Text("Clear") } }
                        recent.forEach { r -> Text(r, Modifier.fillMaxWidth().clickable { onQuery(r) }.padding(vertical = 10.dp)) }
                    } else Text("Search file names, dates, albums and — once indexed — text inside images.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                resultCount == 0 -> EmptyState(Icons.Filled.Search,
                    if (index is IndexStatus.Indexing) "Still indexing (${index.percent}%)" else "No results for “$query”",
                    when (index) { is IndexStatus.Indexing -> "Text in images isn't searchable yet. File-name results are complete."
                        IndexStatus.LimitedAccess -> "Far Gallery can only search the photos you've allowed."
                        else -> "Try a different spelling or remove a filter." })
                else -> results()
            }
        }
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Search", style = MaterialTheme.typography.displayLarge)
            Row(Modifier.fillMaxWidth().glass(glass, RoundedCornerShape(22.dp), GlassKind.Bar).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Search, null, Modifier.padding(end = 10.dp))
                BasicTextField(query, onQuery, singleLine = true, textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.fillMaxWidth())
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SearchScope.entries) { s -> FilterChip(selected = s == scope, onClick = { onScope(s) }, label = { Text(s.label) }) }
            }
        }
    }
}

`````

### feature/duplicates/Duplicates.kt

`````kotlin
package com.far.gallery.feature.duplicates

import android.content.ContentResolver
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.far.gallery.core.common.farCatching
import com.far.gallery.core.media.MediaRow
import com.far.gallery.core.storage.Checksums
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

data class DupItem(val row: MediaRow, val isFavorite: Boolean, val inUserAlbum: Boolean) { val protected get() = isFavorite || inUserAlbum }
data class DupGroup(val hash: String, val items: List<DupItem>)

sealed interface DupScan {
    data class Progress(val hashed: Int, val total: Int) : DupScan
    data class Done(val groups: List<DupGroup>) : DupScan
}

/** Exact duplicates: group by size (cheap), then stream SHA-256 only for same-size candidates. Cancellable; unreadable files are skipped, not fatal. */
class DuplicateFinder(private val resolver: ContentResolver) {
    fun scan(all: List<MediaRow>, favorites: Set<Long>, inAlbums: Set<Long>): Flow<DupScan> = flow {
        val candidates = all.groupBy { it.sizeBytes }.filter { it.key > 0 && it.value.size > 1 }.values.flatten()
        val byHash = HashMap<String, MutableList<MediaRow>>()
        candidates.forEachIndexed { i, row ->
            currentCoroutineContext().ensureActive()
            emit(DupScan.Progress(i, candidates.size))
            val h = (farCatching { resolver.openInputStream(row.uri)!!.use(Checksums::sha256) } as? com.far.gallery.core.common.FarResult.Ok)?.value ?: return@forEachIndexed
            byHash.getOrPut(h) { ArrayList() } += row
        }
        emit(DupScan.Done(byHash.filter { it.value.size > 1 }.map { (h, rows) ->
            DupGroup(h, rows.map { DupItem(it, it.id in favorites, it.id in inAlbums) })
        }))
    }.flowOn(Dispatchers.IO)
}

/**
 * Review UI. Safety rules enforced here, not just documented:
 * - nothing is pre-selected for deletion beyond "all but the first unprotected copy"; the user can change every box
 * - the last remaining copy in a group can never be ticked
 * - protected items (favorite / in a user album) are unticked and labelled
 * - deletion goes to the system trash request, so Android asks for consent and it is undoable
 */
@Composable
fun DuplicatesScreen(scanning: DupScan.Progress?, groups: List<DupGroup>, onTrash: (List<Uri>) -> Unit, modifier: Modifier = Modifier) {
    val marked = remember(groups) {
        mutableStateMapOf<Long, Boolean>().apply {
            groups.forEach { g -> g.items.filter { !it.protected }.drop(1).forEach { this[it.row.id] = true } }
        }
    }
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text("Duplicates", style = MaterialTheme.typography.displayLarge)
        if (scanning != null) {
            LinearProgressIndicator(progress = { if (scanning.total == 0) 0f else scanning.hashed.toFloat() / scanning.total }, Modifier.fillMaxWidth().padding(vertical = 12.dp))
            Text("Checking ${scanning.hashed} of ${scanning.total} files…", style = MaterialTheme.typography.labelMedium)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.weight(1f)) {
            items(groups, key = { it.hash }) { g ->
                Column {
                    Text("${g.items.size} identical copies", style = MaterialTheme.typography.titleMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(g.items, key = { it.row.id }) { d ->
                            val ticked = marked[d.row.id] == true
                            val othersKept = g.items.count { it.row.id != d.row.id && marked[it.row.id] != true }
                            Column(Modifier.size(width = 120.dp, height = 190.dp)) {
                                Box(Modifier.size(120.dp).clip(RoundedCornerShape(10.dp))) { AsyncImage(d.row.uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                                Text(d.row.name, maxLines = 1, style = MaterialTheme.typography.labelMedium)
                                if (d.protected) Text(if (d.isFavorite) "Favorite — kept" else "In an album — kept", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                else Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(ticked, { want -> if (!want || othersKept > 0) marked[d.row.id] = want }, enabled = ticked || othersKept > 0)   // can't delete every copy
                                    Text("Delete", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
        val chosen = groups.flatMap { it.items }.filter { marked[it.row.id] == true }
        TextButton({ onTrash(chosen.map { it.row.uri }) }, enabled = chosen.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text("Move ${chosen.size} to Recently Deleted")
        }
    }
}

`````

### feature/camera/CameraScreen.kt

`````kotlin
package com.far.gallery.feature.camera

import android.content.Context
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.MeteringPoint
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import com.far.gallery.ui.feedback.ErrorAction
import com.far.gallery.ui.feedback.ErrorCard
import com.far.gallery.ui.feedback.ErrorInfo
import com.far.gallery.ui.theme.FarPalette
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

enum class CameraMode(val label: String) { SloMo("SLO-MO"), Video("VIDEO"), Photo("PHOTO"), Square("SQUARE"), Portrait("PORTRAIT") }
enum class FlashMode { Off, Auto, On }

/** Everything shown is derived from REAL capabilities queried from CameraInfo; nothing is assumed. */
@Immutable
data class CameraCapabilities(
    val lensZoomStops: List<Float>,          // e.g. [0.5, 1f, 2f] only if the camera reports that zoom range
    val minZoom: Float, val maxZoom: Float,
    val hasFlash: Boolean,
    val exposureRange: ClosedFloatingPointRange<Float>,   // EV values
    val modes: List<CameraMode>,             // only supported modes
    val hasFrontCamera: Boolean,
)

@Immutable
data class CameraUiState(
    val mode: CameraMode = CameraMode.Photo,
    val zoom: Float = 1f,
    val flash: FlashMode = FlashMode.Off,
    val recording: Boolean = false,
    val recordingSeconds: Int = 0,
    val timerSeconds: Int = 0,
    val countdown: Int? = null,
    val exposureEv: Float = 0f,
    val lastCaptureUri: String? = null,
    val gridOn: Boolean = false,
    val permissionGranted: Boolean = true,
    val error: ErrorInfo? = null,
)

@Composable
fun CameraScreen(
    controller: LifecycleCameraController,
    caps: CameraCapabilities,
    state: CameraUiState,
    onMode: (CameraMode) -> Unit,
    onZoom: (Float) -> Unit,
    onFlash: (FlashMode) -> Unit,
    onExposure: (Float) -> Unit,
    onShutter: () -> Unit,
    onFlip: () -> Unit,
    onOpenLast: () -> Unit,
    onRequestPermission: () -> Unit,
    lastThumbnail: @Composable (String) -> Unit,     // Coil image of the last capture
    modifier: Modifier = Modifier,
) {
    if (!state.permissionGranted) {
        Box(modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            ErrorCard(ErrorInfo("Camera access needed", "Allow camera access to take photos and videos. The rest of Far Gallery keeps working without it.",
                originalsSafe = false, actions = listOf(ErrorAction("Allow camera", true, onRequestPermission))))
        }
        return
    }

    var expanded by remember { mutableStateOf(false) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var flipTurns by remember { mutableFloatStateOf(0f) }
    val haptic = LocalHapticFeedback.current
    val previewView = remember { mutableStateOf<PreviewView?>(null) }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        // -------- Viewfinder --------
        // Square mode: a 1:1 viewfinder yields 1:1 captures (CameraX uses the PreviewView's ViewPort).
        val viewfinder = if (state.mode == CameraMode.Square)
            Modifier.align(Alignment.TopCenter).padding(top = 96.dp).fillMaxWidth().aspectRatio(1f)
        else Modifier.fillMaxSize().padding(top = 96.dp, bottom = 230.dp)
        CameraPreview(controller, onPreviewView = { previewView.value = it },
            viewfinder.clip(RoundedCornerShape(20.dp))
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ -> onZoom((state.zoom * zoom).coerceIn(caps.minZoom, caps.maxZoom)) }
                }
                .pointerInput(Unit) {
                    detectTapGestures { pos ->
                        focusPoint = pos
                        previewView.value?.let { pv ->
                            val p: MeteringPoint = pv.meteringPointFactory.createPoint(pos.x, pos.y)
                            controller.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(p).build())
                        }
                    }
                })
        if (state.gridOn) Box(viewfinder) { GridOverlay(); LevelIndicator() }
        FocusIndicator(focusPoint, state.exposureEv, caps.exposureRange, onExposure) { focusPoint = null }

        // -------- Top bar --------
        Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (caps.hasFlash) CircleIcon(
                    when (state.flash) { FlashMode.Off -> Icons.Filled.FlashOff; FlashMode.Auto -> Icons.Filled.FlashAuto; FlashMode.On -> Icons.Filled.FlashOn },
                    "Flash ${state.flash.name}", tint = if (state.flash == FlashMode.On) FarPalette.CameraYellow else Color.White,
                ) { onFlash(FlashMode.entries[(state.flash.ordinal + 1) % 3]) } else Box(Modifier.size(44.dp))

                if (state.recording) RecordTimerPill(state.recordingSeconds)
                CircleIcon(Icons.Filled.KeyboardArrowUp, if (expanded) "Hide camera options" else "Show camera options",
                    modifier = Modifier.rotate(if (expanded) 180f else 0f)) { expanded = !expanded }
            }
            AnimatedVisibility(expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                // Secondary row: timer, aspect ratio, exposure, grid — wire to real settings.
                Row(Modifier.fillMaxWidth().padding(top = 8.dp).background(Color.White.copy(0.12f), RoundedCornerShape(22.dp)).padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf("Timer", "Ratio", "Exposure", "Grid").forEach { Text(it, color = Color.White, fontSize = 13.sp) }
                }
            }
        }

        // -------- Countdown overlay --------
        state.countdown?.let {
            Text("$it", color = Color.White, fontSize = 96.sp, fontWeight = FontWeight.Light, modifier = Modifier.align(Alignment.Center))
        }

        // -------- Bottom controls --------
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {

            ZoomChips(caps.lensZoomStops, state.zoom) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onZoom(it) }
            if (!state.recording) ModeCarousel(caps.modes, state.mode) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onMode(it) }

            Row(Modifier.fillMaxWidth().padding(horizontal = 32.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(0.15f))
                        .clickable(enabled = state.lastCaptureUri != null, onClick = onOpenLast)
                        .semantics { contentDescription = "Open last capture" },
                ) { state.lastCaptureUri?.let { lastThumbnail(it) } }

                Shutter(state.mode, state.recording) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onShutter() }

                val turn by animateFloatAsState(flipTurns, label = "flip")
                if (caps.hasFrontCamera) CircleIcon(Icons.Filled.FlipCameraAndroid, "Switch camera", size = 52.dp,
                    modifier = Modifier.rotate(turn), background = Color.White.copy(0.15f)) { flipTurns += 180f; onFlip() }
                else Box(Modifier.size(52.dp))
            }
        }

        // -------- Error banner (camera busy, thermal, low storage...) --------
        state.error?.let { ErrorCard(it, Modifier.align(Alignment.Center)) }
    }
}

@Composable
private fun CameraPreview(controller: LifecycleCameraController, onPreviewView: (PreviewView) -> Unit, modifier: Modifier) {
    val owner = LocalLifecycleOwner.current
    AndroidView(
        modifier = modifier,
        factory = { ctx: Context ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                this.controller = controller
                controller.bindToLifecycle(owner)
                onPreviewView(this)
            }
        },
    )
}

@Composable
private fun ZoomChips(stops: List<Float>, current: Float, onPick: (Float) -> Unit) {
    if (stops.isEmpty()) return
    Row(Modifier.background(Color.Black.copy(0.35f), CircleShape).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        stops.forEach { z ->
            val selected = kotlin.math.abs(current - z) < 0.15f
            val size by animateDpAsState(if (selected) 40.dp else 32.dp, label = "chip")
            Box(Modifier.size(size).clip(CircleShape).background(Color.Black.copy(0.4f)).clickable { onPick(z) }
                .semantics { role = Role.Button; contentDescription = "Zoom ${z}x" }, contentAlignment = Alignment.Center) {
                Text(if (selected) "${trim(current)}×" else trim(z), color = if (selected) FarPalette.CameraYellow else Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun trim(v: Float) = if (v % 1f == 0f) v.toInt().toString() else "%.1f".format(v)

@Composable
private fun ModeCarousel(modes: List<CameraMode>, current: CameraMode, onPick: (CameraMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        modes.forEach { m ->
            val c by animateColorAsState(if (m == current) FarPalette.CameraYellow else Color.White, label = "mode")
            Text(m.label, color = c, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onPick(m) }.padding(vertical = 6.dp))
        }
    }
}

@Composable
private fun Shutter(mode: CameraMode, recording: Boolean, onClick: () -> Unit) {
    val isVideo = mode == CameraMode.Video || mode == CameraMode.SloMo
    var pressed by remember { mutableStateOf(false) }
    val press by animateFloatAsState(if (pressed) 0.9f else 1f, label = "press")
    val innerColor by animateColorAsState(if (isVideo) FarPalette.RecordRed else Color.White, label = "inner")
    val innerSize by animateDpAsState(if (recording) 30.dp else 60.dp, label = "innerSize")
    val corner by animateDpAsState(if (recording) 8.dp else 30.dp, label = "corner")
    Box(
        Modifier.size(76.dp).border(BorderStroke(4.dp, Color.White), CircleShape)
            .pointerInput(Unit) { detectTapGestures(onPress = { pressed = true; tryAwaitRelease(); pressed = false }, onTap = { onClick() }) }
            .semantics { role = Role.Button; contentDescription = if (isVideo) (if (recording) "Stop recording" else "Start recording") else "Take photo" },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(innerSize).scale(press).background(innerColor, RoundedCornerShape(corner)))
    }
}

@Composable
private fun RecordTimerPill(seconds: Int) {
    Row(Modifier.background(FarPalette.RecordRed, RoundedCornerShape(14.dp)).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("%d:%02d".format(seconds / 60, seconds % 60), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CircleIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, size: androidx.compose.ui.unit.Dp = 44.dp,
    tint: Color = Color.White, background: Color = Color.Black.copy(0.35f), modifier: Modifier = Modifier, onClick: () -> Unit,
) {
    Box(modifier.size(size).clip(CircleShape).background(background).clickable(onClick = onClick)
        .semantics { role = Role.Button; contentDescription = desc }, contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint)
    }
}

/** Yellow focus square + exposure slider (sun) that fades after 1.5 s. */
@Composable
private fun FocusIndicator(point: Offset?, ev: Float, range: ClosedFloatingPointRange<Float>, onEv: (Float) -> Unit, onGone: () -> Unit) {
    if (point == null) return
    val s = remember(point) { Animatable(1.4f) }
    LaunchedEffect(point) { s.animateTo(1f); delay(1500); onGone() }
    val density = LocalDensity.current
    // `point` is relative to the viewfinder, which sits 96.dp below the top edge.
    val half = with(density) { 36.dp.toPx() }
    val top = with(density) { 96.dp.toPx() }
    Box(Modifier.offset { IntOffset((point.x - half).roundToInt(), (point.y - half + top).roundToInt()) }.size(72.dp)) {
        Canvas(Modifier.fillMaxSize().scale(s.value)) { drawRect(FarPalette.CameraYellow, style = Stroke(2.dp.toPx())) }
        // Wire a vertical drag here -> onEv(ev ± delta) clamped to `range`.
    }
}

`````

### feature/camera/CameraManager.kt

`````kotlin
package com.far.gallery.feature.camera

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.video.AudioConfig
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.far.gallery.ui.feedback.ErrorAction
import com.far.gallery.ui.feedback.ErrorInfo
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/**
 * Owns the CameraX controller. Square mode needs no post-crop: LifecycleCameraController uses the
 * PreviewView's ViewPort, so a 1:1 PreviewView produces 1:1 captures (WYSIWYG).
 */
class FarCameraManager(private val context: Context) {
    val controller = LifecycleCameraController(context).apply {
        setEnabledUseCases(CameraController.IMAGE_CAPTURE)
        videoCaptureQualitySelector = QualitySelector.fromOrderedList(listOf(Quality.FHD, Quality.HD, Quality.SD))   // 4K only if user picks and device reports it
    }
    private var recording: Recording? = null
    private val mainExecutor get() = ContextCompat.getMainExecutor(context)

    /** Suspends until the camera is initialised, then reads REAL capabilities. Null = no camera available. */
    suspend fun capabilities(): CameraCapabilities? {
        val ready = suspendCancellableCoroutine { cont ->
            val f = controller.initializationFuture
            f.addListener({ cont.resume(runCatching { f.get() }.isSuccess) }, mainExecutor)
        }
        val info = controller.cameraInfo ?: return null
        if (!ready) return null
        val zoom = info.zoomState.value
        val min = zoom?.minZoomRatio ?: 1f
        val max = zoom?.maxZoomRatio ?: 1f
        val stops = buildList {
            if (min <= 0.6f) add(0.5f); add(1f); if (max >= 2f) add(2f); if (max >= 5f) add(5f)
        }
        val step = info.exposureState.exposureCompensationStep.toFloat()
        val range = info.exposureState.exposureCompensationRange
        val pm = context.packageManager
        return CameraCapabilities(
            lensZoomStops = stops, minZoom = min, maxZoom = max, hasFlash = info.hasFlashUnit(),
            exposureRange = (range.lower * step)..(range.upper * step),
            modes = listOf(CameraMode.Video, CameraMode.Photo, CameraMode.Square),   // Portrait/Night/Slo-mo only after Extensions / high-speed checks
            hasFrontCamera = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT),
        )
    }

    fun setMode(mode: CameraMode) {
        controller.setEnabledUseCases(
            if (mode == CameraMode.Video || mode == CameraMode.SloMo) CameraController.VIDEO_CAPTURE else CameraController.IMAGE_CAPTURE)
    }

    fun setZoom(ratio: Float) { controller.setZoomRatio(ratio) }
    fun setFlash(mode: FlashMode) {
        controller.imageCaptureFlashMode = when (mode) { FlashMode.Off -> ImageCapture.FLASH_MODE_OFF; FlashMode.Auto -> ImageCapture.FLASH_MODE_AUTO; FlashMode.On -> ImageCapture.FLASH_MODE_ON }
        controller.enableTorch(mode == FlashMode.On && recording != null)      // torch only while filming
    }
    fun setExposureEv(ev: Float) {
        val step = controller.cameraInfo?.exposureState?.exposureCompensationStep?.toFloat() ?: return
        controller.cameraControl?.setExposureCompensationIndex((ev / step).roundToInt())
    }
    fun flip() {
        controller.cameraSelector = if (controller.cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
    }

    fun takePhoto(onDone: (Result<Uri>) -> Unit) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_" + stamp())
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Camera")
        }
        val opts = ImageCapture.OutputFileOptions.Builder(context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values).build()
        controller.takePicture(opts, mainExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                r.savedUri?.let { onDone(Result.success(it)) } ?: onDone(Result.failure(IllegalStateException("no uri")))
            }
            override fun onError(e: ImageCaptureException) { onDone(Result.failure(e)) }
        })
    }

    suspend fun takePhotoAwait(): Result<Uri> = suspendCancellableCoroutine { c -> takePhoto { c.resume(it) } }

    @SuppressLint("MissingPermission")
    fun startRecording(maxBytes: Long?, maxSeconds: Int?, onEvent: (VideoRecordEvent) -> Unit) {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "VID_" + stamp())
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "DCIM/Camera")
        }
        val b = MediaStoreOutputOptions.Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI).setContentValues(values)
        maxBytes?.let { b.setFileSizeLimit(it) }
        maxSeconds?.let { b.setDurationLimitMillis(it * 1000L) }
        val audio = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        recording = controller.startRecording(b.build(), AudioConfig.create(audio), mainExecutor) { e ->
            if (e is VideoRecordEvent.Finalize) recording = null
            onEvent(e)
        }
    }
    fun stopRecording() { recording?.stop() }
    fun hasMic() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** QR/barcode scanning: bundled ML Kit model, on-device, no network. */
    fun enableQr(onFound: (String) -> Unit) {
        val scanner = BarcodeScanning.getClient()
        controller.setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.IMAGE_ANALYSIS)
        controller.setImageAnalysisBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        controller.setImageAnalysisAnalyzer(mainExecutor) { proxy ->
            val media = proxy.image
            if (media == null) { proxy.close(); return@setImageAnalysisAnalyzer }
            scanner.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
                .addOnSuccessListener { list -> list.firstOrNull()?.rawValue?.let(onFound) }
                .addOnCompleteListener { proxy.close() }
        }
    }
    fun disableQr() { controller.clearImageAnalysisAnalyzer(); controller.setEnabledUseCases(CameraController.IMAGE_CAPTURE) }

    private fun stamp() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
}

fun captureErrorInfo(e: Throwable, retry: () -> Unit): ErrorInfo = when ((e as? ImageCaptureException)?.imageCaptureError) {
    ImageCapture.ERROR_FILE_IO -> ErrorInfo("Couldn't save the photo", "The photo couldn't be written. Storage may be full or unavailable.", true, "FG-CAM-IO", listOf(ErrorAction("Try again", true, retry)))
    ImageCapture.ERROR_CAMERA_CLOSED -> ErrorInfo("Camera is busy", "Another app may be using the camera.", true, "FG-CAM-BUSY", listOf(ErrorAction("Try again", true, retry)))
    else -> ErrorInfo("Photo not captured", "The camera didn't return a photo.", true, "FG-CAM-CAP", listOf(ErrorAction("Try again", true, retry)))
}

/* ------------------------------- ViewModel ------------------------------- */
class CameraViewModel(app: Application) : AndroidViewModel(app) {
    val manager = FarCameraManager(app)
    private val _state = MutableStateFlow(CameraUiState())
    val state: StateFlow<CameraUiState> = _state.asStateFlow()
    private val _caps = MutableStateFlow<CameraCapabilities?>(null)
    val caps: StateFlow<CameraCapabilities?> = _caps.asStateFlow()
    private var timerJob: Job? = null
    private var recJob: Job? = null
    var captureRequest: com.far.gallery.app.IncomingRoute.Capture? = null
    var onCaptured: ((Uri) -> Unit)? = null           // CameraActivity uses this for capture-for-result

    init {
        viewModelScope.launch {
            val c = manager.capabilities()
            if (c == null) _state.update { it.copy(error = ErrorInfo("Camera unavailable", "No usable camera was found, or it is in use by another app.", true, "FG-CAM-NONE", listOf(ErrorAction("Retry", true) { retryInit() }))) }
            _caps.value = c
        }
    }
    private fun retryInit() = viewModelScope.launch { _state.update { it.copy(error = null) }; _caps.value = manager.capabilities() }

    fun permission(granted: Boolean) = _state.update { it.copy(permissionGranted = granted) }
    fun setMode(m: CameraMode) { if (_state.value.recording) return; manager.setMode(m); _state.update { it.copy(mode = m) } }
    fun setZoom(z: Float) { manager.setZoom(z); _state.update { it.copy(zoom = z) } }
    fun setFlash(f: FlashMode) { manager.setFlash(f); _state.update { it.copy(flash = f) } }
    fun setExposure(ev: Float) { manager.setExposureEv(ev); _state.update { it.copy(exposureEv = ev) } }
    fun cycleTimer() = _state.update { it.copy(timerSeconds = when (it.timerSeconds) { 0 -> 3; 3 -> 5; 5 -> 10; else -> 0 }) }
    fun flip() = manager.flip()
    fun dismissError() = _state.update { it.copy(error = null) }

    fun shutter() {
        val s = _state.value
        when (s.mode) {
            CameraMode.Video, CameraMode.SloMo -> if (s.recording) stopVideo() else startVideo()
            else -> if (s.countdown != null) { timerJob?.cancel(); _state.update { it.copy(countdown = null) } }
                    else timerJob = viewModelScope.launch { countdownThenShoot(s.timerSeconds) }
        }
    }

    private suspend fun countdownThenShoot(seconds: Int) {
        for (n in seconds downTo 1) { _state.update { it.copy(countdown = n) }; delay(1000) }
        _state.update { it.copy(countdown = null) }
        if (lowStorage()) { _state.update { it.copy(error = ErrorInfo("Not enough space", "Free up storage to keep shooting.", true, "FG-SPC", listOf(ErrorAction("OK", true) { dismissError() }))) }; return }
        manager.takePhotoAwait().fold(
            onSuccess = { uri -> _state.update { it.copy(lastCaptureUri = uri.toString()) }; onCaptured?.invoke(uri) },
            onFailure = { e -> _state.update { it.copy(error = captureErrorInfo(e) { dismissError() }) } },
        )
    }

    fun burst(count: Int = 10) = viewModelScope.launch {
        repeat(count) { manager.takePhotoAwait().onSuccess { u -> _state.update { it.copy(lastCaptureUri = u.toString()) } } }
    }

    private fun startVideo() {
        if (lowStorage()) { _state.update { it.copy(error = ErrorInfo("Not enough space", "Free up storage before recording.", true, "FG-SPC", listOf(ErrorAction("OK", true) { dismissError() }))) }; return }
        manager.startRecording(captureRequest?.maxBytes, captureRequest?.maxDurationSec) { e ->
            when (e) {
                is VideoRecordEvent.Start -> {
                    _state.update { it.copy(recording = true, recordingSeconds = 0) }
                    recJob = viewModelScope.launch { while (true) { delay(1000); _state.update { s -> s.copy(recordingSeconds = s.recordingSeconds + 1) } } }
                }
                is VideoRecordEvent.Finalize -> {
                    recJob?.cancel()
                    _state.update { it.copy(recording = false) }
                    if (e.hasError() && e.error != VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED && e.error != VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED) {
                        _state.update { it.copy(error = ErrorInfo("Recording problem", "The video may be incomplete. Anything already recorded is kept when possible.", true, "FG-REC-${e.error}", listOf(ErrorAction("OK", true) { dismissError() }))) }
                    } else {
                        val uri = e.outputResults.outputUri
                        _state.update { it.copy(lastCaptureUri = uri.toString()) }; onCaptured?.invoke(uri)
                    }
                }
                else -> Unit
            }
        }
    }
    private fun stopVideo() = manager.stopRecording()

    /** App backgrounded while recording: finalize safely so the file is kept. Call from lifecycle ON_STOP. */
    fun onAppStopped() { if (_state.value.recording) stopVideo() }

    private fun lowStorage(): Boolean = Environment.getExternalStorageDirectory().usableSpace < 200L * 1024 * 1024
}

/* ------------------------------- Overlays ------------------------------- */
/** Rule-of-thirds grid (also center/square variants by passing different divisions). */
@Composable
fun GridOverlay(modifier: Modifier = Modifier, divisions: Int = 3) {
    Canvas(modifier.fillMaxSize()) {
        repeat(divisions - 1) { i ->
            val x = size.width * (i + 1) / divisions; val y = size.height * (i + 1) / divisions
            drawLine(Color.White.copy(0.35f), Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
            drawLine(Color.White.copy(0.35f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        }
    }
}

/** Horizon level (iPhone-style): turns yellow when within 1 degree of level. Uses the rotation-vector sensor if present. */
@Composable
fun LevelIndicator(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var roll by remember { mutableFloatStateOf(0f) }
    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY)
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) { roll = Math.toDegrees(Math.atan2(e.values[0].toDouble(), e.values[1].toDouble())).toFloat() }
            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }
        if (sensor != null) sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(l) }
    }
    val level = kotlin.math.abs(roll) < 1f || kotlin.math.abs(kotlin.math.abs(roll) - 180f) < 1f
    Canvas(modifier.fillMaxSize()) {
        val c = center; val half = 28.dp.toPx()
        val col = if (level) Color(0xFFFFD60A) else Color.White.copy(0.7f)
        val a = Math.toRadians(roll.toDouble()); val dx = (Math.cos(a) * half).toFloat(); val dy = (Math.sin(a) * half).toFloat()
        drawLine(col, Offset(c.x - dx, c.y + dy), Offset(c.x + dx, c.y - dy), 2.dp.toPx())
    }
}

`````

### feature/files/FilesScreen.kt

`````kotlin
package com.far.gallery.feature.files

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.far.gallery.ui.feedback.EmptyState
import com.far.gallery.ui.feedback.ErrorCard
import com.far.gallery.ui.feedback.UiState
import com.far.gallery.ui.feedback.skeleton
import com.far.gallery.ui.glass.GlassKind
import com.far.gallery.ui.glass.glass
import com.far.gallery.ui.glass.glassSource
import dev.chrisbanes.haze.HazeState

@Immutable
data class FileEntry(
    val id: String,              // stable key (SAF document id / MediaStore id)
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long?,
    val modifiedLabel: String?,
    val typeLabel: String,
    val mime: String?,
    val canRename: Boolean, val canDelete: Boolean, val canMove: Boolean,   // from provider flags; disable when false
)

@Immutable
data class Place(val id: String, val label: String, val icon: ImageVector, val usedFraction: Float? = null, val available: Boolean = true)

enum class SortColumn { Name, Size, Type, Modified }
enum class FilesView { Grid, List }

@Immutable
data class FilesUiState(
    val places: List<Place>,
    val selectedPlaceId: String?,
    val crumbs: List<String>,                  // path segments (never reversed in RTL)
    val entries: UiState<List<FileEntry>>,
    val view: FilesView = FilesView.List,
    val sort: SortColumn = SortColumn.Name,
    val ascending: Boolean = true,
    val showHidden: Boolean = false,
    val selection: Set<String> = emptySet(),
    val clipboardCount: Int = 0,               // items waiting to be pasted (copy/move)
)

interface FilesActions {
    fun openPlace(id: String)
    fun openEntry(e: FileEntry)
    fun navigateToCrumb(index: Int)
    fun up()
    fun setView(v: FilesView)
    fun sortBy(c: SortColumn)
    fun toggleHidden()
    fun toggleSelect(e: FileEntry)
    fun newFolder()
    fun contextAction(e: FileEntry, action: String)   // "open","share","copy","move","rename","delete","properties","star","compress"
    fun retry()
    fun paste()
}

/** Phone = Apple Files (Locations + push navigation). >= 840dp = Ubuntu Files (sidebar + path bar + columns). */
@Composable
fun FilesScreen(state: FilesUiState, actions: FilesActions, glass: HazeState, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (maxWidth >= 840.dp) DesktopLayout(state, actions, glass) else PhoneLayout(state, actions, glass)
    }
}

/* ------------------------------ Phone (Apple Files) ------------------------------ */
@Composable
private fun PhoneLayout(state: FilesUiState, a: FilesActions, glass: HazeState) {
    if (state.selectedPlaceId == null) {
        LazyColumn(
            Modifier.fillMaxSize().glassSource(glass),
            contentPadding = PaddingValues(top = 120.dp, bottom = 132.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Text("Locations", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp)) }
            items(state.places, key = { it.id }) { p -> PlaceRow(p) { a.openPlace(p.id) } }
        }
        Text("Files", style = MaterialTheme.typography.displayLarge, modifier = Modifier.statusBarsPadding().padding(16.dp))
    } else {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp).glass(glass, RoundedCornerShape(28.dp)).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = a::up) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Up") }
                Text(state.crumbs.lastOrNull() ?: "Files", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (state.clipboardCount > 0) TextButton(onClick = a::paste) { Text("Paste ${state.clipboardCount}") }
                ViewToggle(state.view, a::setView)
                IconButton(onClick = a::newFolder) { Icon(Icons.Filled.CreateNewFolder, "New folder") }
            }
            EntriesPane(state, a, Modifier.weight(1f).glassSource(glass), showColumns = false)
        }
    }
}

/* ------------------------------ Desktop / tablet (Ubuntu Files) ------------------------------ */
@Composable
private fun DesktopLayout(state: FilesUiState, a: FilesActions, glass: HazeState) {
    Row(Modifier.fillMaxSize()) {
        // Sidebar "Places"
        Column(
            Modifier.width(240.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant.copy(0.6f)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text("Places", Modifier.padding(8.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.places.forEach { p ->
                val sel = p.id == state.selectedPlaceId
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(if (sel) MaterialTheme.colorScheme.primary.copy(0.16f) else Color.Transparent)
                        .clickable { a.openPlace(p.id) }.padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(p.icon, null, Modifier.size(20.dp), tint = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(p.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                }
            }
        }
        // Main pane
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(10.dp).glass(glass, RoundedCornerShape(22.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(onClick = a::up) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                PathBar(state.crumbs, a::navigateToCrumb, Modifier.weight(1f))
                if (state.clipboardCount > 0) TextButton(onClick = a::paste) { Text("Paste ${state.clipboardCount}") }
                TextButton(onClick = a::toggleHidden) { Text(if (state.showHidden) "Hide hidden" else "Show hidden") }
                ViewToggle(state.view, a::setView)
                IconButton(onClick = a::newFolder) { Icon(Icons.Filled.CreateNewFolder, "New folder") }
            }
            EntriesPane(state, a, Modifier.weight(1f).glassSource(glass), showColumns = true)
        }
    }
}

@Composable
private fun PathBar(crumbs: List<String>, onCrumb: (Int) -> Unit, modifier: Modifier) {
    Row(modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        crumbs.forEachIndexed { i, c ->
            if (i > 0) Icon(Icons.Filled.ChevronRight, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(c, Modifier.clip(RoundedCornerShape(8.dp)).clickable { onCrumb(i) }.padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        }
        // Ctrl+L / tap on empty space: swap to an editable text field for typing a path or search.
    }
}

@Composable
private fun ViewToggle(view: FilesView, onChange: (FilesView) -> Unit) {
    IconButton(onClick = { onChange(if (view == FilesView.Grid) FilesView.List else FilesView.Grid) }) {
        Icon(if (view == FilesView.Grid) Icons.Filled.ViewList else Icons.Filled.GridView, if (view == FilesView.Grid) "List view" else "Grid view")
    }
}

/* ------------------------------ Entries ------------------------------ */
@Composable
private fun EntriesPane(state: FilesUiState, a: FilesActions, modifier: Modifier, showColumns: Boolean) {
    Box(modifier) {
        when (val s = state.entries) {
            UiState.Loading -> Column(Modifier.padding(top = 96.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(10) { Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp)).skeleton()) }
            }
            is UiState.Failure -> ErrorCard(s.error.copy(actions = s.error.actions.ifEmpty { emptyList() }), Modifier.align(Alignment.Center))
            is UiState.Empty -> EmptyState(Icons.Filled.Folder, "This folder is empty", "Nothing to show here.")
            is UiState.Content -> {
                val list = s.data
                if (state.view == FilesView.List) {
                    LazyColumn(contentPadding = PaddingValues(top = 96.dp, bottom = 132.dp)) {
                        if (showColumns) item(key = "header") { ColumnHeader(state, a) }
                        items(list, key = { it.id }) { e -> EntryRow(e, e.id in state.selection, a) }
                    }
                } else {
                    LazyVerticalGrid(GridCells.Adaptive(112.dp), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 96.dp, bottom = 132.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(list, key = { it.id }) { e -> EntryTile(e, e.id in state.selection, a) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnHeader(state: FilesUiState, a: FilesActions) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        listOf(SortColumn.Name to 4f, SortColumn.Size to 1.2f, SortColumn.Type to 1.5f, SortColumn.Modified to 2f).forEach { (c, w) ->
            val active = state.sort == c
            Text(c.name + if (active) (if (state.ascending) " ▲" else " ▼") else "", Modifier.weight(w).clickable { a.sortBy(c) },
                style = MaterialTheme.typography.labelMedium, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EntryRow(e: FileEntry, selected: Boolean, a: FilesActions) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().background(if (selected) MaterialTheme.colorScheme.primary.copy(0.14f) else Color.Transparent)
                .combinedClickable(onClick = { a.openEntry(e) }, onLongClick = { menu = true }).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(4f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FileIcon(e)
                Text(e.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            }
            Text(e.sizeBytes?.let(::humanSize) ?: "—", Modifier.weight(1.2f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(e.typeLabel, Modifier.weight(1.5f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(e.modifiedLabel ?: "—", Modifier.weight(2f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        EntryMenu(menu, { menu = false }, e, a)
    }
}

@Composable
private fun EntryTile(e: FileEntry, selected: Boolean, a: FilesActions) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Column(
            Modifier.clip(RoundedCornerShape(14.dp)).background(if (selected) MaterialTheme.colorScheme.primary.copy(0.14f) else Color.Transparent)
                .combinedClickable(onClick = { a.openEntry(e) }, onLongClick = { menu = true }).padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FileIcon(e, 56.dp)
            Text(e.name, Modifier.padding(top = 6.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
        }
        EntryMenu(menu, { menu = false }, e, a)
    }
}

@Composable
private fun EntryMenu(open: Boolean, dismiss: () -> Unit, e: FileEntry, a: FilesActions) {
    DropdownMenu(expanded = open, onDismissRequest = dismiss) {
        fun item(label: String, key: String, enabled: Boolean = true) =
            DropdownMenuItem(text = { Text(label) }, enabled = enabled, onClick = { dismiss(); a.contextAction(e, key) })
        item("Open", "open"); item("Share", "share"); item("Copy", "copy")
        item("Move", "move", e.canMove); item("Rename", "rename", e.canRename)
        item("Star", "star"); item("Compress to ZIP", "compress"); item("Properties", "properties")
        item("Delete", "delete", e.canDelete)       // disabled (with reason in Properties) when provider forbids it
    }
}

@Composable
private fun FileIcon(e: FileEntry, size: androidx.compose.ui.unit.Dp = 28.dp) {
    val (icon, tint) = when {
        e.isDirectory -> Icons.Filled.Folder to Color(0xFF4DA3FF)
        e.mime?.startsWith("image/") == true -> Icons.Filled.Image to Color(0xFF30D158)
        e.mime?.startsWith("video/") == true -> Icons.Filled.Movie to Color(0xFFBF5AF2)
        else -> Icons.AutoMirrored.Filled.InsertDriveFile to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Icon(icon, null, Modifier.size(size), tint = tint)
}

@Composable
private fun PlaceRow(p: Place, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = p.available, onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(p.icon, null, Modifier.size(28.dp), tint = if (p.available) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(p.label + if (!p.available) " (unavailable)" else "", style = MaterialTheme.typography.bodyLarge)
            p.usedFraction?.let { LinearProgressIndicator(progress = { it }, Modifier.fillMaxWidth().padding(top = 6.dp)) }
        }
        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun humanSize(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "%.1f KB".format(b / 1024.0)
    b < 1024L * 1024 * 1024 -> "%.1f MB".format(b / 1048576.0)
    else -> "%.2f GB".format(b / 1073741824.0)
}

/** Default places; only add SD/USB when the system actually exposes them. */
fun defaultPlaces(hasSd: Boolean) = buildList {
    add(Place("recent", "Recent", Icons.Filled.Schedule))
    add(Place("starred", "Starred", Icons.Filled.Star))
    add(Place("internal", "Internal storage", Icons.Filled.PhoneAndroid))
    add(Place("downloads", "Downloads", Icons.Filled.Download))
    add(Place("pictures", "Pictures", Icons.Filled.Image))
    add(Place("movies", "Videos", Icons.Filled.Movie))
    if (hasSd) add(Place("sd", "SD card", Icons.Filled.SdCard))
    add(Place("trash", "Recently Deleted", Icons.Filled.Delete))
}

`````

### feature/files/FilesViewModel.kt

`````kotlin
package com.far.gallery.feature.files

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Schedule
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.far.gallery.core.common.FarError
import com.far.gallery.core.common.FarResult
import com.far.gallery.core.database.FarDatabase
import com.far.gallery.core.database.FolderGrant
import com.far.gallery.core.storage.Collision
import com.far.gallery.core.storage.DocEntry
import com.far.gallery.core.storage.DocumentOps
import com.far.gallery.core.storage.OpEvent
import com.far.gallery.ui.feedback.ErrorAction
import com.far.gallery.ui.feedback.ErrorInfo
import com.far.gallery.ui.feedback.OperationUi
import com.far.gallery.ui.feedback.UiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** One-shot UI events (need an Activity/Context to act on). */
sealed interface FilesEvent {
    data class OpenFile(val uri: Uri, val mime: String) : FilesEvent
    data class Share(val uris: List<Uri>) : FilesEvent
    data class AskRename(val entry: FileEntry) : FilesEvent
    data class AskDelete(val entries: List<FileEntry>) : FilesEvent
    data class Properties(val entry: FileEntry, val path: String) : FilesEvent
    data object PickFolder : FilesEvent
    data class Message(val text: String, val undo: Boolean = false) : FilesEvent
}

class FilesViewModel(app: Application) : AndroidViewModel(app), FilesActions {
    private val ctx = app
    private val db = FarDatabase.get(app)
    private val ops = DocumentOps(app.contentResolver)

    private data class Clip(val tree: Uri, val items: List<DocEntry>, val move: Boolean)

    private val _ui = MutableStateFlow(
        FilesUiState(places = emptyList(), selectedPlaceId = null, crumbs = emptyList(), entries = UiState.Loading))
    val ui: StateFlow<FilesUiState> = _ui.asStateFlow()
    private val _events = MutableSharedFlow<FilesEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<FilesEvent> = _events.asSharedFlow()
    private val _operation = MutableStateFlow<OperationUi?>(null)
    val operation: StateFlow<OperationUi?> = _operation.asStateFlow()

    private var tree: Uri? = null
    private val stack = ArrayList<Pair<String, String>>()      // (docId, label)
    private var raw: List<DocEntry> = emptyList()
    private var clip: Clip? = null
    private var opJob: Job? = null

    init {
        viewModelScope.launch {
            db.folders().grants().collect { grants ->
                val places = buildList {
                    add(Place("recent", "Recent", Icons.Filled.Schedule, available = false))   // enabled once the recents list has entries
                    grants.forEach { add(Place(it.treeUri, it.displayName, Icons.Filled.Folder)) }
                    add(Place("add", "Add folder…", Icons.Filled.CreateNewFolder))
                }
                _ui.update { it.copy(places = places.filter { p -> p.id != "recent" || p.available }) }
            }
        }
    }

    /** Called with the Uri returned by ACTION_OPEN_DOCUMENT_TREE. Persists the grant so it survives reboots. */
    fun addGrant(uri: Uri) = viewModelScope.launch {
        runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        val name = androidx.documentfile.provider.DocumentFile.fromTreeUri(ctx, uri)?.name ?: "Folder"
        db.folders().add(FolderGrant(uri.toString(), name, System.currentTimeMillis()))
    }

    override fun openPlace(id: String) {
        if (id == "add") { _events.tryEmit(FilesEvent.PickFolder); return }
        tree = Uri.parse(id); stack.clear()
        val root = ops.rootDocId(tree!!); stack += root to (_ui.value.places.firstOrNull { it.id == id }?.label ?: "Folder")
        _ui.update { it.copy(selectedPlaceId = id, selection = emptySet()) }
        load()
    }

    private fun load() {
        val t = tree ?: return
        _ui.update { it.copy(entries = UiState.Loading, crumbs = stack.map { s -> s.second }) }
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) { ops.list(t, stack.last().first) }
            when (res) {
                is FarResult.Ok -> { raw = res.value; publish() }
                is FarResult.Err -> _ui.update { it.copy(entries = UiState.Failure(errorFor(res.error))) }
            }
        }
    }

    private fun errorFor(e: FarError) = when (e) {
        is FarError.PermissionRevoked -> ErrorInfo("Folder access was removed", "Android no longer lets Far Files open this folder. Your files are unchanged.", true, e.code,
            listOf(ErrorAction("Choose folder again", true) { _events.tryEmit(FilesEvent.PickFolder) }))
        is FarError.ProviderUnavailable, is FarError.VolumeRemoved -> ErrorInfo("Storage not available", "The drive or app providing this folder isn't responding. It may have been removed.", true, e.code,
            listOf(ErrorAction("Retry", true) { load() }))
        else -> ErrorInfo("Can't open this folder", "Something went wrong reading it. Your files are unchanged.", true, e.code, listOf(ErrorAction("Retry", true) { load() }))
    }

    private fun publish() {
        val s = _ui.value
        var list = raw.filter { s.showHidden || !it.name.startsWith(".") }
        val cmp: Comparator<DocEntry> = when (s.sort) {
            SortColumn.Name -> compareBy({ !it.isDir }, { it.name.lowercase() })
            SortColumn.Size -> compareBy({ !it.isDir }, { it.size ?: -1L })
            SortColumn.Type -> compareBy({ !it.isDir }, { it.mime }, { it.name.lowercase() })
            SortColumn.Modified -> compareBy({ !it.isDir }, { it.modified ?: 0L })
        }
        list = list.sortedWith(cmp).let { if (s.ascending) it else it.reversed().sortedBy { e -> !e.isDir } }
        val fmt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        val entries = list.map { FileEntry(it.docId, it.name, it.isDir, it.size, it.modified?.let { m -> fmt.format(Date(m)) }, typeLabel(it), it.mime, it.canRename, it.canDelete, it.canMove) }
        _ui.update { it.copy(entries = if (entries.isEmpty()) UiState.Empty(com.far.gallery.ui.feedback.EmptyReason.EmptyFolder) else UiState.Content(entries), clipboardCount = clip?.items?.size ?: 0) }
    }

    private fun typeLabel(e: DocEntry) = when {
        e.isDir -> "Folder"; e.mime.startsWith("image/") -> "Image"; e.mime.startsWith("video/") -> "Video"; e.mime.startsWith("audio/") -> "Audio"
        e.mime == "application/pdf" -> "PDF"; e.mime == "application/zip" -> "ZIP archive"; e.mime.startsWith("text/") -> "Text"; else -> "File"
    }

    private fun find(e: FileEntry) = raw.firstOrNull { it.docId == e.id }

    override fun openEntry(e: FileEntry) {
        val t = tree ?: return
        if (_ui.value.selection.isNotEmpty()) { toggleSelect(e); return }
        if (e.isDirectory) { stack += e.id to e.name; load() }
        else _events.tryEmit(FilesEvent.OpenFile(ops.docUri(t, e.id), e.mime ?: "*/*"))
    }
    override fun navigateToCrumb(index: Int) { while (stack.size > index + 1) stack.removeLast(); load() }
    override fun up() { if (stack.size > 1) { stack.removeLast(); load() } else { _ui.update { it.copy(selectedPlaceId = null, crumbs = emptyList()) } } }
    override fun setView(v: FilesView) = _ui.update { it.copy(view = v) }
    override fun sortBy(c: SortColumn) { _ui.update { it.copy(sort = c, ascending = if (it.sort == c) !it.ascending else true) }; publish() }
    override fun toggleHidden() { _ui.update { it.copy(showHidden = !it.showHidden) }; publish() }
    override fun toggleSelect(e: FileEntry) = _ui.update { it.copy(selection = if (e.id in it.selection) it.selection - e.id else it.selection + e.id) }
    override fun retry() = load()

    override fun newFolder() {
        val t = tree ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val name = ops.uniqueName(raw.map { it.name }.toSet(), "New folder")
            when (val r = ops.create(t, stack.last().first, android.provider.DocumentsContract.Document.MIME_TYPE_DIR, name)) {
                is FarResult.Ok -> load()
                is FarResult.Err -> _events.tryEmit(FilesEvent.Message("Couldn't create the folder here. This location may be read-only."))
            }
        }
    }

    override fun contextAction(e: FileEntry, action: String) {
        val t = tree ?: return
        val sel = _ui.value.selection.takeIf { e.id in it && it.size > 1 }?.mapNotNull { id -> raw.firstOrNull { it.docId == id } } ?: listOfNotNull(find(e))
        when (action) {
            "open" -> openEntry(e)
            "share" -> _events.tryEmit(FilesEvent.Share(sel.filter { !it.isDir }.map { ops.docUri(t, it.docId) }))
            "copy" -> { clip = Clip(t, sel, false); publish(); _events.tryEmit(FilesEvent.Message("${sel.size} copied. Open a folder and tap Paste.")) }
            "move" -> { clip = Clip(t, sel, true); publish(); _events.tryEmit(FilesEvent.Message("${sel.size} ready to move. Open a folder and tap Paste.")) }
            "rename" -> _events.tryEmit(FilesEvent.AskRename(e))
            "delete" -> _events.tryEmit(FilesEvent.AskDelete(sel.map { d -> FileEntry(d.docId, d.name, d.isDir, d.size, null, "", d.mime, d.canRename, d.canDelete, d.canMove) }))
            "properties" -> _events.tryEmit(FilesEvent.Properties(e, stack.joinToString("/") { it.second } + "/" + e.name))
        }
    }

    fun confirmRename(e: FileEntry, newName: String) {
        val t = tree ?: return
        if (newName.isBlank() || '/' in newName) { _events.tryEmit(FilesEvent.Message("That name isn't allowed.")); return }
        if (raw.any { it.name == newName && it.docId != e.id }) { _events.tryEmit(FilesEvent.Message("An item named “$newName” already exists.")); return }
        viewModelScope.launch(Dispatchers.IO) {
            when (ops.rename(t, e.id, newName)) { is FarResult.Ok -> load(); is FarResult.Err -> _events.tryEmit(FilesEvent.Message("This provider doesn't allow renaming here.")) }
        }
    }

    fun confirmDelete(entries: List<FileEntry>) {
        val t = tree ?: return
        runOp("Deleting", entries.size) { step ->
            var failed = 0
            entries.forEachIndexed { i, e -> step(i, e.name); if (ops.delete(t, e.id) is FarResult.Err) failed++ }
            if (failed > 0) _events.tryEmit(FilesEvent.Message("$failed item(s) couldn't be deleted. They were left in place."))
        }
    }

    override fun paste() {
        val c = clip ?: return; val dst = tree ?: return
        opJob?.cancel()
        opJob = viewModelScope.launch {
            val parent = stack.last().first
            ops.transfer(c.tree, c.items, dst, parent, c.move, Collision.Rename).collect { ev ->
                when (ev) {
                    is OpEvent.Progress -> _operation.value = OperationUi(if (c.move) "Moving" else "Copying", ev.current, ev.done, ev.total, canPause = false)
                    is OpEvent.Finished -> { _operation.value = null; if (c.move) clip = null
                        _events.tryEmit(FilesEvent.Message("${ev.done} done" + if (ev.failed.isNotEmpty()) ", ${ev.failed.size} failed (originals kept)" else "")); load() }
                    is OpEvent.Aborted -> { _operation.value = null
                        _events.tryEmit(FilesEvent.Message(if (ev.error is FarError.StorageFull) "Not enough space. Nothing was removed from the source." else "The transfer stopped. Source files are safe.")); load() }
                }
            }
        }
    }
    fun cancelOperation() { opJob?.cancel(); _operation.value = null; load() }

    private fun runOp(title: String, total: Int, block: suspend (step: (Int, String) -> Unit) -> Unit) {
        opJob = viewModelScope.launch(Dispatchers.IO) {
            _operation.value = OperationUi(title, null, 0, total)
            try { block { i, n -> _operation.value = OperationUi(title, n, i, total) } } finally { _operation.value = null; load() }
        }
    }
}

`````

### feature/files/FilePreview.kt

`````kotlin
package com.far.gallery.feature.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.far.gallery.core.common.FarResult
import com.far.gallery.core.common.farCatching
import com.far.gallery.ui.feedback.ErrorCard
import com.far.gallery.ui.feedback.ErrorInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** In-app previews for files opened from the Files tab. Anything else goes to "Open with". */
@Composable
fun FilePreview(uri: Uri, mime: String, modifier: Modifier = Modifier) {
    when {
        mime == "application/pdf" -> PdfPreview(uri, modifier)
        mime.startsWith("text/") || mime in setOf("application/json", "application/xml") -> TextPreview(uri, modifier)
        else -> ErrorCard(ErrorInfo("No preview for this file", "Far Files can't show this type here. Use Open with to view it in another app.", true, "FG-PRV-TYPE"), modifier)
    }
}

@Composable
private fun TextPreview(uri: Uri, modifier: Modifier) {
    val ctx = LocalContext.current
    var text by remember(uri) { mutableStateOf<FarResult<String>?>(null) }
    LaunchedEffect(uri) {
        text = withContext(Dispatchers.IO) {
            farCatching { ctx.contentResolver.openInputStream(uri)!!.use { it.readNBytes(256 * 1024).toString(Charsets.UTF_8) } }   // first 256 KB only
        }
    }
    when (val t = text) {
        null -> CircularProgressIndicator(modifier)
        is FarResult.Err -> ErrorCard(ErrorInfo("Can't read this file", "The file couldn't be opened for preview.", true, t.error.code), modifier)
        is FarResult.Ok -> Text(t.value, modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PdfPreview(uri: Uri, modifier: Modifier) {
    val ctx = LocalContext.current
    var renderer by remember(uri) { mutableStateOf<PdfRenderer?>(null) }
    var failed by remember(uri) { mutableStateOf(false) }
    DisposableEffect(uri) {
        val pfd = runCatching { ctx.contentResolver.openFileDescriptor(uri, "r") }.getOrNull()
        val r = pfd?.let { runCatching { PdfRenderer(it) }.getOrNull() }       // password-protected or corrupt PDFs throw: handled, not fatal
        if (r == null) failed = true else renderer = r
        onDispose { runCatching { renderer?.close() }; runCatching { pfd?.close() } }
    }
    if (failed) { ErrorCard(ErrorInfo("Can't preview this PDF", "It may be damaged or password-protected. Use Open with.", true, "FG-PDF"), modifier); return }
    val r = renderer ?: return
    LazyColumn(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(List(r.pageCount) { it }) { _, index ->
            val bmp by produceBitmap(r, index)
            bmp?.let { Image(it.asImageBitmap(), "Page ${index + 1}", Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth) }
        }
    }
}

/** PdfRenderer allows ONE open page at a time, so renders are serialised and bounded to ~1080 px wide. */
private val pdfLock = Any()
@Composable
private fun produceBitmap(r: PdfRenderer, index: Int) = androidx.compose.runtime.produceState<Bitmap?>(null, r, index) {
    value = withContext(Dispatchers.Default) {
        runCatching {
            synchronized(pdfLock) {
                r.openPage(index).use { p ->
                    val w = 1080; val h = (w.toFloat() / p.width * p.height).toInt()
                    Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { it.eraseColor(Color.WHITE); p.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
                }
            }
        }.getOrNull()
    }
}

`````

### feature/settings/SettingsScreen.kt

`````kotlin
package com.far.gallery.feature.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.far.gallery.app.DefaultApps
import com.far.gallery.app.DefaultRole
import com.far.gallery.app.DefaultStatus
import com.far.gallery.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("far_settings")

data class FarSettings(
    val theme: ThemeMode = ThemeMode.System, val accentIndex: Int = 0, val dynamicColor: Boolean = false,
    val reduceMotion: Boolean = false, val reduceTransparency: Boolean = false, val haptics: Boolean = true,
    val gridColumns: Int = 3, val onboardingDone: Boolean = false, val trashDays: Int = 30,
    val resumeVideo: Boolean = true, val stripLocationOnShare: Boolean = true,
    val volumeKeyShutter: Boolean = true, val mirrorSelfie: Boolean = true, val cameraGrid: Boolean = false,
    val ocrEnabled: Boolean = false, val memoriesEnabled: Boolean = true,
)

class SettingsStore(private val ctx: Context) {
    private object K {
        val theme = stringPreferencesKey("theme"); val accent = intPreferencesKey("accent"); val dyn = booleanPreferencesKey("dyn")
        val rm = booleanPreferencesKey("rm"); val rt = booleanPreferencesKey("rt"); val hap = booleanPreferencesKey("hap")
        val cols = intPreferencesKey("cols"); val onb = booleanPreferencesKey("onb"); val trash = intPreferencesKey("trash")
        val resume = booleanPreferencesKey("resume"); val strip = booleanPreferencesKey("strip"); val vol = booleanPreferencesKey("vol")
        val mirror = booleanPreferencesKey("mirror"); val grid = booleanPreferencesKey("grid"); val ocr = booleanPreferencesKey("ocr"); val mem = booleanPreferencesKey("mem")
    }
    val flow: Flow<FarSettings> = ctx.store.data.map { p ->
        FarSettings(
            theme = p[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.System,
            accentIndex = p[K.accent] ?: 0, dynamicColor = p[K.dyn] ?: false, reduceMotion = p[K.rm] ?: false,
            reduceTransparency = p[K.rt] ?: false, haptics = p[K.hap] ?: true, gridColumns = p[K.cols] ?: 3,
            onboardingDone = p[K.onb] ?: false, trashDays = p[K.trash] ?: 30, resumeVideo = p[K.resume] ?: true,
            stripLocationOnShare = p[K.strip] ?: true, volumeKeyShutter = p[K.vol] ?: true, mirrorSelfie = p[K.mirror] ?: true,
            cameraGrid = p[K.grid] ?: false, ocrEnabled = p[K.ocr] ?: false, memoriesEnabled = p[K.mem] ?: true,
        )
    }
    suspend fun update(t: (FarSettings) -> FarSettings) {
        ctx.store.edit { p: androidx.datastore.preferences.core.MutablePreferences ->
            val s = t(read(p))
            p[K.theme] = s.theme.name; p[K.accent] = s.accentIndex; p[K.dyn] = s.dynamicColor; p[K.rm] = s.reduceMotion
            p[K.rt] = s.reduceTransparency; p[K.hap] = s.haptics; p[K.cols] = s.gridColumns; p[K.onb] = s.onboardingDone
            p[K.trash] = s.trashDays; p[K.resume] = s.resumeVideo; p[K.strip] = s.stripLocationOnShare; p[K.vol] = s.volumeKeyShutter
            p[K.mirror] = s.mirrorSelfie; p[K.grid] = s.cameraGrid; p[K.ocr] = s.ocrEnabled; p[K.mem] = s.memoriesEnabled
        }
    }
    private fun read(p: Preferences) = FarSettings(
        theme = p[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.System, accentIndex = p[K.accent] ?: 0,
        dynamicColor = p[K.dyn] ?: false, reduceMotion = p[K.rm] ?: false, reduceTransparency = p[K.rt] ?: false, haptics = p[K.hap] ?: true,
        gridColumns = p[K.cols] ?: 3, onboardingDone = p[K.onb] ?: false, trashDays = p[K.trash] ?: 30, resumeVideo = p[K.resume] ?: true,
        stripLocationOnShare = p[K.strip] ?: true, volumeKeyShutter = p[K.vol] ?: true, mirrorSelfie = p[K.mirror] ?: true,
        cameraGrid = p[K.grid] ?: false, ocrEnabled = p[K.ocr] ?: false, memoriesEnabled = p[K.mem] ?: true,
    )
}

@Composable
fun SettingsScreen(
    s: FarSettings, onChange: ((FarSettings) -> FarSettings) -> Unit,
    onOpenSystemDefaults: () -> Unit, onFixDefault: (DefaultRole, DefaultStatus) -> Unit,
    onClearCache: () -> Unit, onDeleteOcrIndex: () -> Unit, onManageAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    LazyColumn(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 16.dp, 16.dp, 120.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Settings", style = MaterialTheme.typography.displayLarge) }

        item { Section("Make Far Gallery your default") {
            Text("Android doesn't have one switch for this. Pick Far Gallery when asked, choose “Always”, or clear the other app's default.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            DefaultRole.entries.forEach { role ->
                val st = DefaultApps.status(ctx, role)
                Row(Modifier.fillMaxWidth().clickable { onFixDefault(role, st) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(role.label); Text(role.hint, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Text(when (st) { DefaultStatus.Mine -> "✓ Far Gallery"; DefaultStatus.AskEveryTime -> "Ask each time"; is DefaultStatus.Other -> st.label; DefaultStatus.NoHandler -> "Unavailable" },
                        color = if (st == DefaultStatus.Mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TextButton(onOpenSystemDefaults) { Text("Open system default-app settings") }
        } }

        item { Section("Appearance") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { ThemeMode.entries.forEach { m -> FilterChip(s.theme == m, { onChange { it.copy(theme = m) } }, { Text(if (m == ThemeMode.Amoled) "AMOLED" else m.name) }) } }
            Toggle("Use system colours (Material You)", s.dynamicColor) { v -> onChange { it.copy(dynamicColor = v) } }
            Toggle("Reduce motion", s.reduceMotion) { v -> onChange { it.copy(reduceMotion = v) } }
            Toggle("Reduce transparency (opaque bars)", s.reduceTransparency) { v -> onChange { it.copy(reduceTransparency = v) } }
            Toggle("Haptic feedback", s.haptics) { v -> onChange { it.copy(haptics = v) } }
        } }

        item { Section("Photos") {
            Toggle("Memories", s.memoriesEnabled) { v -> onChange { it.copy(memoriesEnabled = v) } }
            Toggle("Resume videos where you left off", s.resumeVideo) { v -> onChange { it.copy(resumeVideo = v) } }
            Toggle("Remove location when sharing", s.stripLocationOnShare) { v -> onChange { it.copy(stripLocationOnShare = v) } }
            TextButton(onManageAccess) { Text("Review photo access") }
        } }

        item { Section("Camera") {
            Toggle("Grid and level", s.cameraGrid) { v -> onChange { it.copy(cameraGrid = v) } }
            Toggle("Mirror front camera", s.mirrorSelfie) { v -> onChange { it.copy(mirrorSelfie = v) } }
            Toggle("Volume keys take a photo", s.volumeKeyShutter) { v -> onChange { it.copy(volumeKeyShutter = v) } }
        } }

        item { Section("Privacy and data") {
            Toggle("Find text in photos (on-device)", s.ocrEnabled) { v -> onChange { it.copy(ocrEnabled = v) } }
            TextButton(onDeleteOcrIndex) { Text("Delete text index") }
            TextButton(onClearCache) { Text("Clear thumbnail cache (your photos are not deleted)") }
        } }

        item { Section("About") {
            Text("Far Gallery works offline and needs no account. Local build has no internet permission.", style = MaterialTheme.typography.bodyMedium)
        } }
    }
}

@Composable private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(22.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium); content() }
}
@Composable private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f)); Switch(value, onChange) }
}

`````

# Part D — Icons and favicons

### web/favicon.svg (64px, refined)

`````svg
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" width="64" height="64" role="img" aria-label="Far Gallery">
  <title>Far Gallery</title>
  <defs>
    <linearGradient id="sky" x1="0" y1="0" x2="0.15" y2="1">
      <stop offset="0" stop-color="#4F5BFF"/>
      <stop offset="0.5" stop-color="#A855F7"/>
      <stop offset="1" stop-color="#FF8A5B"/>
    </linearGradient>
    <radialGradient id="glow" cx="0.5" cy="0.5" r="0.5">
      <stop offset="0" stop-color="#FFFFFF" stop-opacity="0.85"/>
      <stop offset="1" stop-color="#FFFFFF" stop-opacity="0"/>
    </radialGradient>
    <linearGradient id="back" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#FFFFFF" stop-opacity="0.70"/>
      <stop offset="1" stop-color="#FFFFFF" stop-opacity="0.35"/>
    </linearGradient>
    <linearGradient id="sheen" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#FFFFFF" stop-opacity="0.38"/>
      <stop offset="1" stop-color="#FFFFFF" stop-opacity="0"/>
    </linearGradient>
    <clipPath id="tile"><rect width="64" height="64" rx="15"/></clipPath>
  </defs>
  <g clip-path="url(#tile)">
    <rect width="64" height="64" fill="url(#sky)"/>
    <!-- distant stars -->
    <circle cx="12" cy="13" r="0.9" fill="#fff" fill-opacity="0.8"/>
    <circle cx="22" cy="8" r="0.6" fill="#fff" fill-opacity="0.6"/>
    <circle cx="52" cy="10" r="0.7" fill="#fff" fill-opacity="0.7"/>
    <!-- sun with glow -->
    <circle cx="44" cy="25" r="12" fill="url(#glow)"/>
    <circle cx="44" cy="25" r="5.2" fill="#FFFFFF"/>
    <!-- back ridge -->
    <path d="M-2 50 L17 31 L28 42 L43 28 L66 49 L66 66 L-2 66 Z" fill="url(#back)"/>
    <!-- front ridge: lit face + shaded face -->
    <path d="M-2 57 L-2 53 L25 24 L32 66 L-2 66 Z" fill="#FFFFFF"/>
    <path d="M25 24 L66 56 L66 66 L32 66 Z" fill="#E4D9FF"/>
    <!-- snow-cap highlight -->
    <path d="M25 24 L21.4 28.2 L24 27.2 L26 29.6 L28.3 27 Z" fill="#FFFFFF"/>
    <!-- glass sheen -->
    <path d="M0 0 H64 V22 Q32 31 0 22 Z" fill="url(#sheen)"/>
  </g>
  <!-- hairline inner border for glass feel -->
  <rect x="0.5" y="0.5" width="63" height="63" rx="14.5" fill="none" stroke="#FFFFFF" stroke-opacity="0.35"/>
</svg>

`````

### web/favicon-mini.svg (32px simplified)

`````svg
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32" width="32" height="32" role="img" aria-label="Far Gallery">
  <!-- Simplified for 16/32 px tabs: no gradients on small shapes, bold silhouette, high contrast -->
  <defs>
    <linearGradient id="s" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#5B6CFF"/><stop offset="1" stop-color="#FF8A5B"/>
    </linearGradient>
  </defs>
  <rect width="32" height="32" rx="8" fill="url(#s)"/>
  <circle cx="22" cy="12" r="3.6" fill="#fff"/>
  <path d="M0 27 L12 10 L32 28 L32 32 L0 32 Z" fill="#fff"/>
  <path d="M12 10 L32 28 L32 32 L16 32 Z" fill="#E4D9FF"/>
</svg>

`````

### web/favicon-maskable.svg (512px maskable)

`````svg
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512" role="img" aria-label="Far Gallery (maskable)">
  <!-- Full-bleed background; artwork kept inside the central 80% safe zone (PWA / Play listing maskable icon) -->
  <defs>
    <linearGradient id="sky" x1="0" y1="0" x2="0.15" y2="1">
      <stop offset="0" stop-color="#4F5BFF"/><stop offset="0.5" stop-color="#A855F7"/><stop offset="1" stop-color="#FF8A5B"/>
    </linearGradient>
    <radialGradient id="glow"><stop offset="0" stop-color="#fff" stop-opacity="0.85"/><stop offset="1" stop-color="#fff" stop-opacity="0"/></radialGradient>
  </defs>
  <rect width="512" height="512" fill="url(#sky)"/>
  <circle cx="318" cy="196" r="96" fill="url(#glow)"/>
  <circle cx="318" cy="196" r="40" fill="#fff"/>
  <path d="M92 380 L190 270 L252 330 L330 254 L424 366 L424 420 L92 420 Z" fill="#fff" fill-opacity="0.55"/>
  <path d="M92 420 L92 392 L226 208 L262 420 Z" fill="#fff"/>
  <path d="M226 208 L424 392 L424 420 L262 420 Z" fill="#E4D9FF"/>
</svg>

`````

### drawable/ic_launcher_background.xml

`````xml
<?xml version="1.0" encoding="utf-8"?>
<!-- res/drawable/ic_launcher_background.xml : dusk gradient, 108dp adaptive canvas -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient android:startX="54" android:startY="0" android:endX="54" android:endY="108"
                android:type="linear">
                <item android:offset="0.0" android:color="#FF5B6CFF"/>
                <item android:offset="0.55" android:color="#FFB45CFF"/>
                <item android:offset="1.0" android:color="#FFFF8A65"/>
            </gradient>
        </aapt:attr>
    </path>
</vector>

`````

### drawable/ic_launcher_foreground.xml (refined)

`````xml
<?xml version="1.0" encoding="utf-8"?>
<!-- res/drawable/ic_launcher_foreground.xml : artwork kept inside the 66dp safe zone (21..87). Matches favicon.svg: lit + shaded peak faces. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <!-- sun glow + sun -->
    <path android:pathData="M69,42m-14,0a14,14 0,1 1,28 0a14,14 0,1 1,-28 0">
        <aapt:attr name="android:fillColor">
            <gradient android:type="radial" android:centerX="69" android:centerY="42" android:gradientRadius="14">
                <item android:offset="0" android:color="#BFFFFFFF"/>
                <item android:offset="1" android:color="#00FFFFFF"/>
            </gradient>
        </aapt:attr>
    </path>
    <path android:fillColor="#FFFFFFFF" android:pathData="M69,42m-6.5,0a6.5,6.5 0,1 1,13 0a6.5,6.5 0,1 1,-13 0"/>
    <!-- back ridge -->
    <path android:fillColor="#99FFFFFF" android:pathData="M21,76 L38,54 L49,66 L62,50 L87,74 L87,87 L21,87 Z"/>
    <!-- front ridge: lit face -->
    <path android:fillColor="#FFFFFFFF" android:pathData="M21,87 L21,81 L45,45 L52,87 Z"/>
    <!-- front ridge: shaded face -->
    <path android:fillColor="#FFE4D9FF" android:pathData="M45,45 L87,80 L87,87 L52,87 Z"/>
</vector>

`````

### drawable/ic_launcher_monochrome.xml

`````xml
<?xml version="1.0" encoding="utf-8"?>
<!-- res/drawable/ic_launcher_monochrome.xml : Android 13+ themed icon (single color, system tints it) -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#FF000000"
        android:pathData="M69,43m-8,0a8,8 0,1 1,16 0a8,8 0,1 1,-16 0"/>
    <path android:fillColor="#FF000000"
        android:pathData="M21,87 L21,80 L46,46 L60,62 L70,50 L87,74 L87,87 Z"/>
</vector>

`````

### mipmap-anydpi-v26/ic_launcher.xml

`````xml
<?xml version="1.0" encoding="utf-8"?>
<!-- res/mipmap-anydpi-v26/ic_launcher.xml  (copy as ic_launcher_round.xml too) -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
    <monochrome android:drawable="@drawable/ic_launcher_monochrome"/>
</adaptive-icon>
<!--
Manifest:  android:icon="@mipmap/ic_launcher"  android:roundIcon="@mipmap/ic_launcher_round"
Splash (res/values/themes.xml, Theme.SplashScreen):
  <item name="windowSplashScreenBackground">@color/splash_bg</item>
  <item name="windowSplashScreenAnimatedIcon">@drawable/ic_launcher_foreground</item>
  <item name="postSplashScreenTheme">@style/Theme.FarGallery</item>
Notification small icon: reuse ic_launcher_monochrome.xml as ic_stat_far.xml (single color, no gradient).
Play Store 512x512 PNG: export ic_launcher_background + foreground flattened (no transparency).
-->

`````

### drawable/ic_fg_camera.xml

`````xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#FFFFFFFF" android:pathData="M30,40 L40,40 L44,34 L64,34 L68,40 L78,40 Q84,40 84,46 L84,72 Q84,78 78,78 L30,78 Q24,78 24,72 L24,46 Q24,40 30,40 Z"/>
    <path android:fillColor="#FF7A5BFF" android:pathData="M54,47 A12,12 0 1,1 54,71 A12,12 0 1,1 54,47 Z"/>
    <path android:fillColor="#FFFFFFFF" android:pathData="M54,52 A7,7 0 1,1 54,66 A7,7 0 1,1 54,52 Z"/>
</vector>

`````

### drawable/ic_fg_files.xml

`````xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#99FFFFFF" android:pathData="M26,40 Q26,34 32,34 L46,34 L52,41 L76,41 Q82,41 82,47 L82,48 L26,48 Z"/>
    <path android:fillColor="#FFFFFFFF" android:pathData="M26,48 L82,48 L82,72 Q82,78 76,78 L32,78 Q26,78 26,72 Z"/>
</vector>

`````

### mipmap-anydpi-v26/ic_launcher_camera.xml

`````xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_fg_camera"/>
    <monochrome android:drawable="@drawable/ic_fg_camera"/>
</adaptive-icon>

`````

### mipmap-anydpi-v26/ic_launcher_files.xml

`````xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_fg_files"/>
    <monochrome android:drawable="@drawable/ic_fg_files"/>
</adaptive-icon>

`````


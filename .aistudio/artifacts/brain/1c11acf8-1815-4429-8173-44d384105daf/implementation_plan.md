# Far Gallery — Privacy-First Native Media & AI Studio

Far Gallery is a premium, privacy-first native Android photo and video gallery, media editor, secure vault, and intelligent media laboratory. It marries fast, local-first media browsing and management with Google Gemini AI capabilities for deep multimodal understanding, real-time Search/Maps landmark grounding, and aspect-ratio controlled generative image creation.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> The following architectural and design decisions incorporate the user's clarified preferences and technical specifications:

- **Theme System**: Comprehensive theme suite supporting **Deep Slate Monochrome**, **True AMOLED Black (pure #000000)**, **Gallery Warm Amber**, and **Material You Dynamic Color**, complete with system/light/dark switching and persistent preference storage.
- **AI Integration Strategy**: Dual-mode presentation with (1) direct contextual smart tools inside the media viewer (Instant Image Analysis, Video Frame Understanding, Landmark & Search Grounding, and AI Edit) and (2) a dedicated **AI Studio** screen for prompt-driven image generation, custom aspect ratios (1:1, 2:3, 3:2, 3:4, 4:3, 9:16, 16:9, 21:9), and studio-quality generation.
- **Home Landing & Navigation**: Unified top/bottom navigation offering a seamless chronological **Timeline (All Media)** as the default home view, with instant switching between **Albums & Folders**, **AI Studio**, **Files & Vault**, and **Smart Search**.
- **Privacy & Storage Boundaries**: True local-first architecture using MediaStore and Storage Access Framework (SAF). Sensitive vault items are encrypted in app-private storage with Android Keystore protection and biometric/PIN lock, completely excluded from public indexing.

---

### 1. Overview & Core Concept

- **What It Does**: Provides a fluid, 120Hz-ready media gallery for browsing, organizing, editing, and securing photos and videos stored on internal and removable SD storage. Augments local media with cutting-edge Gemini intelligence (Gemini 4 Argon for multimodal image and video reasoning; Gemini 3.8 Flash with Google Search and Google Maps grounding; and Gemini Nano Banana 2.1 / Gemini 3 Pro for prompt-driven image synthesis with aspect ratio control).
- **Target Audience / Persona**: Users seeking a high-performance, private alternative to cloud-tethered galleries, creative photographers wanting non-destructive editing and AI assistance, and security-conscious individuals wanting a truly encrypted vault.
- **Key Value**: Speed and zero-lag browsing without internet dependency, rock-solid scoped storage safety, non-destructive editing, and state-of-the-art AI intelligence available right alongside personal media.

---

### 2. User Experience & Visual Design

#### Key User Flows
1. **Gallery Timeline & Quick Filters**: Fluid grid (pinch-to-zoom columns 2 to 5) grouped chronologically by Today, Yesterday, This Week, Month, and Year. Sticky date headers, fast-scroll date indicator, and filter chips (Photos, Videos, Favorites, Camera, Screenshots).
2. **Full-Screen Media Viewer & Contextual AI**:
   - Immersive edge-to-edge viewer with smooth pinch-zoom and pan.
   - Quick action bar: Favorite, Share, Edit, Trash, Details/EXIF sheet.
   - **Contextual AI Inspector Bar**:
     - *Analyze Image / Video* (powered by `gemini-4-argon` for scene analysis, object detection, and action breakdown).
     - *Landmark & Places Grounding* (powered by `gemini-3.8-flash` with Google Maps tool for geolocation insights, architectural history, and nearby places).
     - *Web Search Grounding* (powered by `gemini-3.8-flash` with Google Search tool for current events, identification, and contextual facts).
     - *AI Image Edit* (prompt-driven modification powered by `gemini-nano-banana-2.1`).
3. **Dedicated AI Studio**:
   - Prompt input with style presets (Cinematic, Photorealistic, Minimalist, Watercolor, Cyberpunk).
   - Exact Aspect Ratio selector: **1:1, 2:3, 3:2, 3:4, 4:3, 9:16, 16:9, and 21:9**.
   - Model switcher: Fast Generation (`gemini-nano-banana-2.1`) vs. Studio Quality (`gemini-3-pro-image-preview`).
   - Image-to-image remixing: pick any photo from the gallery, describe modifications, and synthesize.
   - Direct "Save to Gallery" and "Share" options.
4. **Built-in Photo Editor**:
   - Non-destructive adjustments: Crop (Freeform, 1:1, 4:3, 16:9), Rotate, Flip.
   - Sliders for Brightness, Contrast, Saturation, Warmth, Sharpness.
   - Filter presets and drawing/markup tools with safe redact flattening.
   - Non-destructive: saves a new copy by default without touching the original.
5. **Private Encrypted Vault**:
   - Biometric authentication & PIN gate with secure rate-limiting.
   - AES-256-GCM authenticated encryption stored in app-private sandboxed directory.
   - Items imported to vault are strictly isolated from Android MediaStore, public search, and widgets.
6. **SAF File Manager & Storage Dashboard**:
   - Internal storage and SD card tree browsing via SAF grants.
   - Storage breakdown by file type, large videos, duplicate finder, and safe trash recovery.

#### Visual Identity & Theme
- **Aesthetic Direction**: Minimalist gallery elegance. Content is the hero; UI chrome stays unobtrusive and recedes during browsing.
- **Color Palettes**:
  - *Deep Slate*: Dark charcoal surfaces (`#12161A`), subtle dividers (`#242B33`), crisp accent (`#4DA3FF`).
  - *AMOLED Black*: Pure black (`#000000`) backgrounds for maximum contrast and battery savings on OLED screens.
  - *Gallery Amber*: Warm off-black (`#161412`), warm parchment light mode, refined golden amber accent (`#FFB300`).
  - *Dynamic Color*: M3 Android 12+ dynamic wallpaper tokens (`MaterialTheme.colorScheme`).
- **Typography & Motion**:
  - Distinctive clean sans hierarchy with legible date stamps and metadata badges.
  - Spring-based smooth animations (`spring(stiffness = Spring.StiffnessLow)`), shared element transitions into full-screen viewer.

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Dual-Source Media Architecture (MediaStore + Room)**:
  - *Chosen Approach*: Query system `MediaStore` for device photos/videos with reactive `ContentObserver`, while maintaining an app-owned Room database for custom albums, tags, favorites, edit recipes, vault metadata, and AI analysis cache.
  - *Why*: Instant launch without full filesystem rescans; respects user media permissions while preserving user-defined organizational structures.
- **Decision 2: Multimodal Gemini Model Routing**:
  - *Chosen Approach*: Specifically route workloads to specialized models per requirements:
    - `gemini-4-argon`: Complex image understanding and video analysis.
    - `gemini-3.8-flash` (with Search & Maps tools): Real-time location grounding and factual lookups.
    - `gemini-nano-banana-2.1`: Fast prompt-to-image generation and image editing.
    - `gemini-3-pro-image-preview`: Studio-grade image rendering.
  - *Why*: Balances speed, latency, feature requirements, and model strengths.
- **Decision 3: Non-Destructive Media Preservation**:
  - *Chosen Approach*: All image edits and AI generations export to new files in the standard Pictures/FarGallery collection, leaving original files untouched.
  - *Why*: Adheres to professional media management standards and prevents accidental data loss.

---

### 4. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────────────────────┐
│                          PRESENTATION LAYER                            │
│                                                                        │
│   ┌─────────────────┐ ┌──────────────────┐ ┌─────────────────────────┐  │
│   │ GalleryTimeline │ │ FullscreenViewer │ │ AI Studio (Gen/Aspect)  │  │
│   └────────┬────────┘ └────────┬─────────┘ └────────────┬────────────┘  │
│            │                   │                        │               │
│   ┌────────┴────────┐ ┌────────┴─────────┐ ┌────────────┴────────────┐  │
│   │ Albums & Folders│ │ PhotoEditorView  │ │ Encrypted Vault & Files │  │
│   └────────┬────────┘ └────────┬─────────┘ └────────────┬────────────┘  │
└────────────┼───────────────────┼────────────────────────┼───────────────┘
             ▼                   ▼                        ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        VIEWMODEL & DOMAIN LAYER                        │
│                                                                        │
│   ┌────────────────────────────────┐ ┌─────────────────────────────┐   │
│   │ GalleryViewModel               │ │ AiStudioViewModel           │   │
│   │ (Paging, Filtering, Selection) │ │ (Multimodal & Generation)   │   │
│   └───────────────┬────────────────┘ └──────────────┬──────────────┘   │
│                   │                                 │                  │
│   ┌───────────────┴────────────────┐ ┌──────────────┴──────────────┐   │
│   │ MediaViewerViewModel           │ │ VaultViewModel              │   │
│   │ (Viewer, EXIF, AI Inspector)   │ │ (Crypto, Biometrics, Pin)   │   │
│   └───────────────┬────────────────┘ └──────────────┬──────────────┘   │
└───────────────────┼─────────────────────────────────┼──────────────────┘
                    ▼                                 ▼
┌────────────────────────────────────────────────────────────────────────┐
│                         DATA & REPOSITORY LAYER                        │
│                                                                        │
│  ┌────────────────────────┐  ┌──────────────────┐  ┌─────────────────┐ │
│  │ MediaStoreRepository   │  │ Room Database    │  │ Firebase.ai     │ │
│  │ (Images, Videos, SAF)  │  │ (Albums, Tags,   │  │ (Gemini 4 Argon │ │
│  │                        │  │  Vault, Recipes) │  │  3.8 Flash,     │ │
│  │                        │  │                  │  │  Nano Banana)   │ │
│  └───────────┬────────────┘  └────────┬─────────┘  └────────┬────────┘ │
└──────────────┼────────────────────────┼─────────────────────┼──────────┘
               ▼                        ▼                     ▼
┌────────────────────────┐   ┌────────────────────┐   ┌──────────────────┐
│ Android System Storage │   │ App-Private SQLite │   │ Gemini AI Logic  │
│ (DCIM, Pictures, SAF)  │   │ & Keystore Storage │   │ Cloud Backend    │
└────────────────────────┘   └────────────────────┘   └──────────────────┘
```

#### Data Model & Entities
- **Room AppDatabase**:
  - `AlbumEntity`: id, name, description, coverUri, isPinned, createdAt.
  - `MediaTagCrossRef`: mediaUri, tagString.
  - `FavoriteEntity`: mediaUri, addedTimestamp.
  - `EditRecipeEntity`: mediaUri, adjustmentsJson, outputUri, timestamp.
  - `VaultItemEntity`: id, encryptedFileName, originalDisplayName, mimeType, sizeBytes, ivBase64, saltBase64, createdAt.
  - `AiAnalysisCacheEntity`: mediaUri, modelName, analysisType, analysisResult, timestamp.

#### State Machine & Error Resilience
- **AiTaskState**: `Idle`, `Loading(message)`, `Success(result)`, `Error(message)` with coroutine timeout (60s) and cooperative cancellation handling.
- **MediaLoadingState**: Progressive thumbnail rendering with graceful error fallback for corrupted files or missing permissions.
- **Safe Intent Handlers**: Support `ACTION_VIEW`, `ACTION_SEND`, and `ACTION_SEND_MULTIPLE` for opening and analyzing media directly from external apps.

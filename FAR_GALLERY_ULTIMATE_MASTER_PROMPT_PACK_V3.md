# FAR GALLERY — ULTIMATE MASTER PROMPT PACK (v3)
## Photos + Camera + Files · Android · Offline-first · iOS-27-inspired · Agent-executable

**What this file is:** the single instruction document you give to **Freebuff (DeepSeek)** or **Google Antigravity (Gemini, your 3.8 workflow)** so it can expand your existing **Far Gallery** Android app one prompt at a time. Every prompt (P00–P60) contains: the goal, **what the feature looks like**, **how the user interacts with it**, **how it works underneath**, edge cases, tests, and acceptance criteria — so the agent never has to guess.

**Platform:** existing native Android app · Kotlin + Jetpack Compose · minSdk 30 · strict offline.
**This v3 supersedes** the earlier "Complete Unified Master Prompt Pack" (P00–P52). All earlier IDs are kept and enriched; P53–P60 are new.

---

## CONTENTS
- Part A — How to use this pack with Freebuff / Antigravity (workflow, context files, order)
- Part B — Global Rules G1–G24 (paste into every session)
- Part C — Session Starter, Standard Report, Phase Review, Recovery prompts
- Part D — Product vision, platform truth, architecture contract
- Part E — Design system (tokens, type, materials, components, motion, haptics, icons, microcopy)
- Part F — Prompt library P00–P60 (phases, one copy-paste block per prompt)
- Part G — Feature-to-prompt coverage map
- Part H — Progress tracker · Part I — Release checklist · Part J — Owner decision log

---

# PART A — HOW TO USE THIS PACK

## A1. Files the agent must always have
1. `APP_OVERVIEW.md` — the existing app map (can be outdated; **source code is the truth**).
2. This file.
3. The real repository (Gradle, manifest, Room schemas, source, tests, resources).

## A2. Create three small "context files" in the repo root (once, during P00)
The agent re-reads these at the start of every session, so it never loses direction when a chat resets.

**`FAR_RULES.md`** — paste Part B (Global Rules) here verbatim.
**`FAR_PROGRESS.md`** — a table copied from Part H. The agent updates it after every prompt (status, commit hash, notes, open issues).
**`FAR_DECISIONS.md`** — the owner decision log from Part J plus every approval the owner gives ("approved: schema v12 migration", "approved: Phosphor icons").

Why: DeepSeek and Gemini sessions have limited working context. These files are the agent's long-term memory. If the agent says "I don't remember", reply: *"Read FAR_RULES.md, FAR_PROGRESS.md and FAR_DECISIONS.md, then continue."*

## A3. The session ritual (do this every time)
1. `git status` clean? Commit or stash. Create a branch: `git checkout -b pXX-short-name`.
2. Open a **fresh** agent session (new chat). Long chats drift.
3. Paste **Session Starter** (Part C).
4. Paste **only one** prompt block (P00, then P01 …).
5. Read the agent's *plan + file list* **before** letting it edit. If the list touches unrelated files → reply with the "Agent changed unrelated code" prompt.
6. Let it build and test. Install on your phone (Infinix) and test the flow by hand.
7. Read the **Standard Report**. Anything `PARTIAL / NOT DONE / UNTESTED / BROKEN` → run "Finish partial work" before moving on.
8. `git commit -m "Pxx name"` and merge the branch. Update `FAR_PROGRESS.md`.
9. After each **phase** (see A5) run the **Phase Review** prompt.

**Never** paste several prompts at once. **Never** say "do everything". If a prompt is too big for the model, use the **Split Prompt** helper — it is built for this.

## A4. Model-specific tips
- **Freebuff / DeepSeek:** keep each session focused; tell it exactly which files to read (`Read only: …`). If it starts rewriting large files, stop it and use the "Split" prompt. Ask it to *quote file paths and function names as evidence*.
- **Antigravity / Gemini:** it can browse and run the emulator; ask it to *record screenshots of each state* (loading, empty, error, dark mode) and attach them to the report. Ask it to run `./gradlew` tasks itself and paste the real output.
- **Both:** do not trust "all tests pass" unless the report shows the command and the output. Use the **Verify a claim** prompt whenever something feels too easy.

## A5. Phases and the recommended order
Execute in this order. IDs are not strictly numeric because the design foundations (P53–P56) must land before screens use them.

| Phase | Name | Prompts (in order) | Exit criterion |
|---|---|---|---|
| 0 | Truth & foundation | P00, P01 (**approval gate**), P02, P03, P04, P05 | Reproducible build, audit matrix, safe DB recovery, error model, 100k test library |
| 1 | Data & operations | P06, P07, P08, P09, P10, P60 | One asset identity; resumable scan/OCR; journaled file ops; all formats decode safely |
| 2 | Design & shell | P11, P12, P53, P54, P55, P56, P13, P14 | Three destinations, themes, icons, motion, haptics, state kit, gestures |
| 3 | Photos core | P15, P16, P17, P18, P19, P20, P21 | Browse, select, organise, view, search all work with no duplicate files |
| 4 | Intelligence | P22, P23, P24, P25, P26 | OCR, duplicates, scene/people, places, memories — all offline, all opt-in |
| 5 | Editing & creations | P28, P29, P31, P27, P58, P30 (gated) | Non-destructive edits; exports; creations |
| 6 | Files | P32, P33, P34, P35, P36, P59 | Browse, ops, preview, PDF, archives, cleaner |
| 7 | Camera | P39, P40, P41, P43, P42 | Honest capability-based camera; real saves; video |
| 8 | System, privacy & backup | P44, P45, P46, P37 (**optional / decision gate**), P38, P57 | Intents, share, widgets, lock, backup/restore |
| 9 | Ops, polish & release | P47, P48, P49, P50, P51, P52 | Activity centre, settings, a11y, offline audit, perf, release gate |

**Ship a beta after Phase 3 + Phase 7's core (P39–P40).** Do not wait for everything.

## A6. What "DONE" means
A feature is DONE only when: the whole flow works on a real device; UI matches the prompt's "What it looks like"; state persists across restart and process death; permission denial, cancellation, low storage and errors are handled; originals are safe; tests exist **and were run**; no fake controls (empty click handlers, demo numbers, fake progress); and the agent can cite file + class + function as evidence.

## A7. Owner conflicts to resolve early (read before P00)
1. **Vault:** your notes from 2026-11-04 say *"remove the private vault and its screen entirely"*; the older spec still contains a Vault. In this pack **P37 is an optional gate, OFF by default**: build only *App Lock* and *Hidden albums* unless you write "approved: Vault" in `FAR_DECISIONS.md`. Nothing else in the pack depends on the Vault.
2. **Look:** UI should resemble **Apple Photos / Google Photos**, camera should resemble the **iPhone Camera**, Files should resemble **Apple Files + Ubuntu Files (Nautilus)**. This is a *style interpretation with original assets*, never copied artwork, names or fonts.
3. **Formats:** Android + iPhone media formats are all to be supported *as far as the device can decode* (see P60). Where a format can't be decoded, the app must say so — never show a blank tile.

---

# PART B — GLOBAL RULES (G1–G24)

Paste into `FAR_RULES.md` and at the top of any session without a rules file.

```text
GLOBAL RULES FOR FAR GALLERY — OBEY IN EVERY TASK

G1  PLATFORM. Existing native Android project, Kotlin + Jetpack Compose, minSdk 30. No Flutter/React Native/web. No rewrite from scratch. Do not rename the app, package or applicationId.
G2  STRICT OFFLINE. Never declare android.permission.INTERNET. No accounts, analytics, ads, telemetry, crash upload, cloud, remote APIs, remote fonts, remote models, remote map tiles. Everything works in airplane mode. LAN/SMB/FTP/WebDAV/Wi-Fi Direct sockets are excluded unless the owner approves a separate "Local network" flavour.
G3  SOURCES OF TRUTH. MediaStore = shared photos/videos. SAF = arbitrary documents/folders. Room (far.db) = Far-owned metadata only (albums, memberships, tags, ratings, captions, edit recipes, OCR/ML indexes, journal, settings). Room never decides whether a file exists. One physical file = one asset; never copy a file just to show it elsewhere.
G4  ORIGINALS ARE SAFE. Edits/conversions/exports save a NEW copy by default. Overwrite needs a separate explicit choice, warning, confirmation and recovery path.
G5  DELETES ARE REVIEWED. Trash is immediate with an Undo bar. Permanent delete needs a second confirmation. Cleanup tools list exact items + space first. Never delete automatically.
G6  NO FAKE CONTROLS. Every visible control does what its label says. No placeholder screens, demo data, fake progress, empty onClick, TODO/FIXME, "...", omitted methods, hard-coded counts. If not finished, hide it or show an honest disabled state with a reason.
G7  CAPABILITY HONESTY. Detect hardware, codec, display, ML and API support at runtime. Never substitute Photo for Portrait or 30 fps for Slow-mo under a misleading label. Unsupported = disabled with a one-line reason.
G8  PERMISSIONS. Minimum, just-in-time, plain-language, partial-access aware. No MANAGE_EXTERNAL_STORAGE without owner approval.
G9  DATABASE. Every Room change = data-preserving migration + migration test + pre-migration snapshot. Never deleteDatabase as a fix.
G10 PUBLIC APIS ONLY. No root, hidden APIs, OEM-private APIs, accessibility-service tricks. Never claim the hidden system-gallery role or universal default-app replacement.
G11 RELIABILITY. Wrap every external call (ContentResolver, SAF, MediaStore, CameraX, Media3, ML Kit, Room, codecs, archive parsers) in FarResult/FarError. Batch jobs isolate each item. No disk/DB/decode/hash on the main thread. Page large lists (Paging 3). Pass IDs, never big lists, through Intents/navigation/saved state. Atomic writes (temp → verify → rename; MediaStore IS_PENDING). No `!!`. Every long job has journal + progress + Cancel + process-death recovery.
G12 UI + ACCESSIBILITY. Only design tokens, FarIcons and motion tokens from Part E. Icon-only buttons have contentDescription with state. 48dp touch targets. Support System/Light/Dark/AMOLED/High-contrast, font scale to 200%, TalkBack, RTL, Reduce Motion, Reduce Transparency, keyboard. Every screen has loading, empty, limited-permission, unavailable and error states.
G13 METHOD. Before editing: list exact files to inspect/change and why. Small steps. Build + run relevant tests after each step. Do not touch unrelated code. Keep dependency versions in the version catalog; do not upgrade without compatibility/licence/size review.
G14 HONEST REPORTING. End every task with the Standard Report. Mark DONE / PARTIAL / NOT DONE / UNTESTED / BROKEN. Never claim a build/test/device check passed unless it was actually run and the output is shown.
G15 PRIVACY BOUNDARIES. Hidden items, excluded folders and (if ever built) Vault content must not leak into timelines, search, OCR, ML, memories, widgets, notifications, thumbnails, recents previews, share suggestions or diagnostics.
G16 NO UNAPPROVED PRODUCT CHANGES. No renaming, network features, DB replacement, model-download mechanism or major dependency without owner approval recorded in FAR_DECISIONS.md.
G17 FINISHED CODE. No TODO/FIXME, fake values, empty handlers, `!!`, pseudocode in code described as implemented.
G18 TEST THE FAILURE PATH. Cancellation, force-stop, process death, low storage, permission revocation, volume removal, malformed files, provider errors, camera contention, unsupported hardware.
G19 MOTION BUDGET. Animate only transforms/alpha in lists; cap simultaneous animations; honour Reduce Motion; no animation may block input; drop effects when the frame governor says the app is janky.
G20 ORIGINAL ASSETS ONLY. No Apple SF Symbols/SF Pro, no Apple/Google artwork, logos or product names (Live Photo, Magic Eraser, Night Sight, Best Take…). Use the Far names in Part E §E9. Open-licensed icon/font only, licence file + attribution screen.
G21 EVIDENCE. Every claim in a report cites file path + class/function (+ test name). "I believe" is not evidence.
G22 SCOPE CONTROL. If a prompt is too large, STOP and propose a split (4–8 substeps). Do only substep 1.
G23 MICROCOPY. Short, plain, specific, one voice. Errors = title + one-sentence cause + one next action. Never show exception text, codes or paths to users.
G24 SCREENSHOTS. For every new/changed screen, capture (or state you could not capture) Light, Dark, 200% font, loading, empty, error. Compose previews + screenshot tests where the project supports them.
```

---

# PART C — SESSION STARTER, REPORTS, RECOVERY

## C1. Session Starter (paste first in every new session)
```text
You are working on my EXISTING Far Gallery Android app (native Kotlin + Jetpack Compose). Use the current repository. Do not create a sample project and do not rewrite the app.

Read fully before starting: FAR_RULES.md, FAR_PROGRESS.md, FAR_DECISIONS.md (if missing, create them from the master pack), APP_OVERVIEW.md, the master prompt pack sections that this task references (Global Rules, Part D architecture, Part E design system, and the prompt itself), and the real source files related to the task. APP_OVERVIEW.md can be outdated — the code is the truth.

Follow Global Rules G1–G24. Work ONLY on the task I paste after this. Before touching code, tell me:
 1) the exact files/classes you will READ,
 2) the exact files/classes you will CHANGE or CREATE and why,
 3) the tests and manual device checks you will run,
 4) any question that genuinely blocks safe work (max 3).
Then work in small steps; build and test after each step. Do not start the next prompt. Stop at any approval gate. Finish with the Standard Report.
```

## C2. Standard Report (agent must end every task with this)
```text
STANDARD REPORT
1. RESULT — every requested item marked DONE / PARTIAL / NOT DONE / UNTESTED / BROKEN, each with evidence (file, class, function, test).
2. USER EXPERIENCE — exact steps on the device; what each screen looks like; what happens on success, cancel, failure. Attach screenshots or say none could be taken.
3. FILES — full paths created / modified / deleted, with the reason.
4. BUILD & TESTS — commands run, real output summary; unit / instrumented / UI / screenshot / migration / performance tests added; which were actually run; which were not and why.
5. RELIABILITY — permission denial/revocation, process death, low storage, corrupt input, provider failure, partial batch behaviour.
6. DATA SAFETY — how originals, metadata, albums, tags, edits and journals are preserved; migration details.
7. PERFORMANCE — main-thread work avoided, paging, caches, memory, battery/thermal; measured numbers only if actually measured.
8. PRIVACY & PERMISSIONS — manifest diff; confirm no INTERNET permission.
9. LIMITATIONS — unsupported devices/APIs, stubs, risks, known bugs.
10. NEXT STEP — name the next prompt (do not run it) and ask only blocking questions.
```

## C3. Phase Review (after each phase — no new features)
```text
PHASE REVIEW — DO NOT ADD FEATURES.
1. Run build, unit tests, lint, and relevant instrumented/UI tests; paste results.
2. Search changed code for: TODO, FIXME, "...", empty onClick, "!!", deleteDatabase, hard-coded query limits (LIMIT 2000/10000), main-thread disk/DB/decode, large lists in Intents/nav args, hard-coded colours/icons outside the design system, missing contentDescription on icon buttons, android.permission.INTERNET, println/Log of file names or paths.
3. List every visible control and the real operation it performs.
4. List features marked DONE without a test or device evidence.
5. Verify migrations, permissions, privacy behaviour, file-op recovery.
6. Run large-library (100k) and fault-injection tests that apply.
7. Fix only critical regressions (data loss, privacy/security, crash, fake control, broken navigation).
8. Give an ordered list of remaining issues + Standard Report.
```

## C4. Recovery & helper prompts
**Finish partial work**
```text
Your report has PARTIAL / NOT DONE / UNTESTED / BROKEN / stubbed items. Add no new features. List every unfinished item, inspect the code, finish only those, build and test each, and give an updated Standard Report. Do not touch unrelated modules.
```
**Build failure**
```text
The build/tests fail. Add no features. Read the exact error, find the ROOT cause, make the smallest safe fix, rebuild, rerun the relevant tests, explain. If three attempts fail, stop and list exactly what you tried.
```
**Agent changed unrelated code**
```text
Stop. You went outside the task. I reverted to the last good commit. Re-read the prompt and G1–G24. List the exact minimal file list, change only those files, and ask before touching anything else.
```
**Split a too-large task**
```text
This task is too large. Split it into 4–8 independently buildable and testable substeps. For each: files, dependencies, tests, completion criteria. Do ONLY substep 1, give the Standard Report, and wait for me to say "next".
```
**Verify a claim**
```text
Verify each claim against the repository. For each show file path, class/function and test evidence; mark TRUE / FALSE / UNKNOWN. Don't infer from comments or APP_OVERVIEW.md. Claims: [paste].
```
**Device test failures**
```text
Manual device results: [paste]. Reproduce, find the root cause, fix only the relevant code, add a regression test, build and rerun. Do not change unrelated features. Explain any device/API limitation.
```
**Visual mismatch**
```text
The screen does not match the prompt's "What it looks like" section. Here is a screenshot/description: [paste]. Compare each bullet of that section with the code, list mismatches, fix only those using the design tokens (Part E), add a screenshot test, and report.
```
**Performance regression**
```text
Scrolling/launch/camera is slow. Do not add features. Profile with Macrobenchmark/Perfetto or Compose compiler reports, identify the top 3 causes with evidence, fix the biggest, re-measure, and report before/after numbers.
```

---

# PART D — PRODUCT VISION, PLATFORM TRUTH, ARCHITECTURE CONTRACT

## D1. Vision in one paragraph
Far Gallery is **one offline app with three destinations — Photos, Camera, Files — that share one asset identity**. A photo taken in Camera is written once and appears in Photos and in Files (`Pictures/Far Gallery`) from the same record. It looks calm and premium (Apple Photos / Google Photos for Photos, iPhone Camera for Camera, Apple Files + Ubuntu Files for Files), but it is built on public Android APIs, original assets, and honest capability checks. Nothing leaves the phone.

## D2. What Android allows (Default App & System Integration — build an honest screen, P44)
| Goal | Far can do | Far must NOT claim |
|---|---|---|
| Be the camera other apps open | Handle `ACTION_IMAGE_CAPTURE` / `ACTION_VIDEO_CAPTURE`, honour `EXTRA_OUTPUT` | Replacing the OEM camera on lock screen, power-button double press, hardware camera key |
| Be the gallery/viewer | `ACTION_VIEW`, `ACTION_PICK`, `ACTION_GET_CONTENT`, in-app picker | The hidden system-gallery role; replacing the system Photo Picker everywhere |
| Be a file manager | Full in-app browser, pick/share intents, a `DocumentsProvider` for content the user chooses to expose | A universal "default file manager" setting |
| Be first choice | Correct intent filters, resolver tests, guidance to OEM "Open by default" screens | Asking users to disable OEM apps |
Status labels: *Available · Set as preferred where supported · System-managed · Not supported on this device.* Never show a green "Default" badge unless verified.

## D3. Offline contract and the "Local network" edition
Strict build: no sockets, no INTERNET. Nearby sharing = system share sheet (Quick Share/Bluetooth are provided by the OS), USB OTG, SD card, SAF. LAN features (SMB, WebDAV, direct transfer) belong only to a **separate, owner-approved flavour** — not in this pack.

## D4. Architecture contract (agents must follow this)
**Layers:** UI (Compose, state hoisted) → ViewModel (StateFlow, one `UiState` + one-shot `Effect`) → UseCase (pure Kotlin, returns `FarResult<T>`) → Repository (the only place that touches MediaStore / SAF / Room / CameraX / Media3 / ML) → Source.
**Canonical `MediaAsset`:** `assetId` (stable) · `source` (MEDIASTORE | SAF | APP_PRIVATE) · `uri` · `volume` · `displayName` · `mime` · `sizeBytes` · `dateTaken` · `dateAdded` · `dateModified` · `width/height` · `durationMs` · `orientation` · flags (`favorite, archived, hidden, trashed, capturedByFar`) · `rating(0–5)` · `contentHash?` · `availability` (PRESENT | MISSING | REVOKED | UNMOUNTED).
**Room tables to own (extend, don't replace):** `albums`, `album_folders`, `album_media`, `tags`, `media_tags`, `ratings_keywords`, `captions`, `edit_stacks` (versioned JSON), `ocr_text` (+ FTS4/5 virtual table), `scene_labels`, `face_clusters` (separate deletable store), `quality_scores`, `memories`, `creation_projects`, `collection_layout`, `folder_rules`, `search_history`, `saved_searches`, `operation_records` (journal), `scan_checkpoints`, `quarantine`, `playback_positions`.
**Indexer:** incremental, resumable, content-observer + targeted rescan, WorkManager for deferred work (no permanent foreground service), per-asset status (DISCOVERED, INDEXED, OCR_DONE, ML_DONE, FAILED, EXCLUDED), respects battery/thermal/storage.
**Error model:** `FarResult<T> = Ok(T) | Err(FarError)`; `FarError` kinds: Permission, Storage, Provider, FormatCorrupt, Conflict, Memory, Hardware, Model, Cancelled, Timeout, Internal. Each has `userTitle`, `userMessage`, `recoveryAction`.
**Process boundaries:** untrusted decoding, ML, archive extraction and transcoding may run in an isolated `android:process` behind a bound service; if it dies twice on one file → quarantine that file.
**Navigation:** single-activity Navigation Compose; 3 top-level destinations each with its own back stack + saved scroll; overlays (Settings, Activity, Onboarding) return exactly where the user was. Pass IDs only.

## D5. The eight known defects (verify in P00 — do not assume they still exist)
1 Portrait & Slo-mo are stubs falling back to normal capture · 2 duplicate/analysis/storage queries capped at 2,000/10,000 rows · 3 OCR only foreground, 40 items at a time · 4 incoming URI resolver accepts only `content://` and `file://` · 5 corruption handler calls `deleteDatabase("far.db")` · 6 video trim writes big temp files to `cacheDir` · 7 `renameDocument()` can return null on some providers · 8 alpha dependencies (Room 2.7.0-alpha08, CameraX 1.5.0-alpha01).

---

# PART E — DESIGN SYSTEM (shared by every prompt)

> Goal: a calm, content-first, glass look in the spirit of iOS 27 / Apple Photos / Google Photos — **interpreted for Android, original assets**. Photos fill the screen; chrome is small, translucent and hides when viewing/capturing. One accent colour. Never put essential text on a busy photo without a scrim.

## E1. Colour tokens (semantic; never hard-code hex in screens)
| Token | Light | Dark | AMOLED | High contrast (light / dark) |
|---|---|---|---|---|
| `bg` | #F2F2F7 | #000000 | #000000 | #FFFFFF / #000000 |
| `surface` | #FFFFFF | #1C1C1E | #0B0B0C | #FFFFFF / #000000 (+2dp border) |
| `surfaceElevated` | #FFFFFF | #2C2C2E | #161618 | #FFFFFF / #1A1A1A |
| `label` | #000000 | #FFFFFF | #FFFFFF | #000000 / #FFFFFF |
| `labelSecondary` | #3C3C43 @60% | #EBEBF5 @60% | same | label @85% |
| `labelTertiary` | #3C3C43 @30% | #EBEBF5 @30% | same | label @65% |
| `separator` | #3C3C43 @29% (0.5dp) | #545458 @60% | #38383A | label (1dp) |
| `accent` (blue) | #007AFF | #0A84FF | #0A84FF | #0040DD / #6CB4FF |
| `cameraYellow` | #FFCC00 | #FFD60A | same | same |
| `destructive`/`recording` | #FF3B30 | #FF453A | same | #C00000 / #FF6B6B |
| `success` | #34C759 | #30D158 | same | #006B2B / #63E68B |
| `warning` | #FF9500 | #FF9F0A | same | #A35A00 / #FFB84D |
| `scrim` | black @40% | black @55% | black @60% | black @70% |
Optional themes (OFF by default): the old neon accents (cyan, magenta, lime, violet, amber). Dynamic colour is opt-in and never used inside the Camera. State must never rely on colour alone (add icon/label).

## E2. Typography
One family. Use the Android system sans or a bundled **Inter** variable font (OFL; verify the licence file). Monospace only for technical values (EXIF raw, hashes, hex viewer).
| Style | sp / line | Weight | Use |
|---|---|---|---|
| LargeTitle | 34/41 | Bold | Screen titles that collapse on scroll |
| Title1 | 28/34 | Bold | Sheet titles, memory titles |
| Title2 | 22/28 | Semibold | Section headers |
| Title3 | 20/25 | Semibold | Card titles |
| Headline | 17/22 | Semibold | Row titles, buttons |
| Body | 17/22 | Regular | Default |
| Callout | 16/21 | Regular | Secondary rows |
| Subhead | 15/20 | Regular | Metadata lines |
| Footnote | 13/18 | Regular | Captions, hints |
| Caption | 12/16 | Medium | Badges, chips |
All text uses `sp` and must not clip at 200% font scale (wrap, then truncate with ellipsis only on single-line names).

## E3. Spacing, shape, elevation
Spacing scale 4·8·12·16·24·32·48 dp. Screen margin 16dp (tablet 24dp). Row height 52dp (min 48dp). Radii: grid thumbnail 3dp (dense) / 8dp (comfortable); card 16dp; sheet top corners 20dp; button 12dp; chip 999dp; album cover 12dp; app-icon-like folder tiles 14dp. **No drop shadows on content**; separation = hairline (0.5dp) or material layers. Safe areas: respect status bar, navigation bar, display cutout, gesture insets.

## E4. Materials ("glass")
- **Glass bar** (top bar, bottom tab bar, toolbars, sheets): `surface @72%` + blur radius 24dp + 0.5dp specular top/bottom hairline (`separator`). Blur requires `RenderEffect` (API 31+). On API 30, on low-tier devices, under Reduce Transparency, High contrast, or when the frame governor trips: **solid `surface @94%`**, no blur.
- Never blur full-screen scrolling content. Blur is drawn behind bars only.
- Scrim for text over photos: 0→60% black vertical gradient, 80dp tall.
- Content canvas in the viewer and camera is pure black.

## E5. Component library (build once in P12; screens may only use these)
Each component below lists its **look → behaviour**.
- **LargeTitleHeader** — big bold title left-aligned under the status bar; collapses to a 44dp centred inline title with a glass bar background after 40dp scroll; trailing actions (search icon, `⋯` More). *Behaviour:* collapse is scroll-linked, no jump; pull-down refresh sits above it.
- **GlassTabBar (bottom, phones)** — 3 items (Photos, Camera, Files), 56dp tall + inset, icons 28dp with 11sp label; selected = filled icon + accent, others outline + `labelSecondary`. Camera is the centre item with a slightly larger glyph. *Behaviour:* tap = switch destination (crossfade 150ms, scroll positions kept); re-tap Photos scrolls to top; re-tap Files goes to Browse root. Hidden while camera is capturing and in the viewer.
- **NavRail / Sidebar (tablet/foldable ≥600dp)** — 80dp rail with the 3 destinations; ≥840dp expands to a 280dp sidebar with sections (Photos: Library, Collections, Albums, Search; Files: Browse, Recents, Tags, Locations).
- **SegmentedControl** — pill track `surfaceElevated`, selected segment white/`surface` with 8dp radius slide animation (250ms spring); used for Years|Months|Days|All and Grid|List.
- **SearchField** — 40dp pill, magnifier + placeholder (“Search photos, text, places”), clear button; on focus expands to full width and shows Cancel text button; suggestions sheet below.
- **FilterChipRow** — horizontal scroll, 32dp chips; selected = accent fill + white text; the trailing “Filters” chip shows an active-count badge and opens a bottom sheet.
- **MediaGridCell** — square, 3dp gap, corner 3dp; overlays: bottom-left duration pill (black @55%, Caption, play glyph), bottom-right favourite heart (white, when favourite), top-right badges (Moment/RAW/GIF/burst count) on a dark pill; selected state = 24dp blue check circle top-left + 12% dim + 3dp inner inset. Placeholder = `surfaceElevated` with a soft shimmer (no spinner).
- **DateHeader (sticky)** — Title3 left, subtle count right, glass @72% strip, 36dp; tapping the circle-check at its right selects that whole day.
- **ScrubberRail** — 4dp thin vertical rail on the right, 32dp wide invisible grab zone; while dragging shows a 56dp rounded bubble with “Nov 2026”; tap bubble = calendar jump.
- **SelectionBar** — top: `Cancel` · “12 Selected” (tappable → Selection Review) · `Done`. Bottom (glass): Share · Add to Album · Favourite · Delete · `⋯` More (max 5). Slides in 200ms.
- **ContextMenu** — glass rounded 16dp card anchored to the item, with 44dp rows: icon left, label, destructive in red; appears with a 150ms scale-from-anchor spring; long-press haptic.
- **BottomSheet** — 20dp top corners, drag handle 36×5dp, detents (peek / half / full), scrim 40%; swipe-down dismisses; back closes.
- **UndoToast (“Snackbar”)** — rounded 14dp dark glass pill above the tab bar: “Moved 12 items to Trash” · **Undo**; auto-hides after 8s; swipe to dismiss; announces to TalkBack.
- **ProgressCard** — in Activity: icon, title, determinate bar (indeterminate only if unknown), “1,204 of 8,300 · about 4 min”, Pause · Cancel buttons.
- **EmptyState** — centred 64dp line icon (accent-tinted), Title3, Subhead (one sentence), one primary pill button.
- **ErrorState** — warning icon, title, one-sentence cause, one action (“Try again”, “Open Settings”, “Choose another folder”).
- **PermissionCard** — icon, title, why-sentence, “Allow” primary, “Not now” secondary, tiny “Everything stays on this phone.”
- **ZoomableMedia** — pinch (1×–8×, spring-limited overscroll), double-tap zoom to the tapped point and back, pan with inertia, rubber-band at edges, tile decoding for large images.
- **VideoControls** — auto-hide after 3s: centre play/pause 64dp glass circle, seek bar with time bubble, time labels, mute, speed, fullscreen, PiP; double-tap left/right = ∓10s with a ripple “10s” label.
- **SliderRow** — label left, numeric value right, 44dp track; centre-zero for bipolar sliders; double-tap resets; the value bubble follows the thumb.
- **ModeSelector (camera)** — horizontal text carousel, selected mode in `cameraYellow`, others white @70%; swipe on preview or on the carousel; snap haptic.
- **ShutterButton** — 72dp white ring (4dp) with 60dp white disc; video = red disc; pressed shrinks to 54dp (80ms); burst hold shows a count.
- **ZoomPill** — dark glass 36dp pill above the shutter: `.5  1×  2  3` only for real lenses; selected stop in yellow with “×”; drag opens the **Zoom Wheel**.
- **FocusReticle** — 72dp yellow square with 1.5dp stroke and tick marks; shrinks 1.2→1.0 in 150ms; adjacent sun slider for exposure; fades after 2s; lock shows “AE/AF LOCK”.
- **Badge/Chip** — Caption text on dark @55% pill, 20dp tall, 6dp padding.

## E6. Motion tokens
| Token | ms | Use |
|---|---|---|
| `instant` | 80–100 | press states, toggles |
| `fast` | 150 | icon swaps, chips, fades, tab crossfade |
| `standard` | 250 | sheets, selection bars, most transitions |
| `emphasis` | 350 | thumbnail↔viewer, camera open/close |
| Spring | damping 0.85, medium stiffness | drag release, snap-back, pinch settle |
| Stagger | 20–30ms per item, ≤6 items | menus and sheet content |
Catalogue: grid→viewer shared-element hero · viewer swipe-down dismiss (image follows finger, scales to thumbnail, black fades; dismiss if >120dp or fling) · tab crossfade · camera slide-up with dim · sheets slide-up · selection top/bottom bars · trash shrink-fade + Undo toast · favourite heart pop (200ms bounce) · shutter flash (white 60ms) + ring squeeze + thumbnail bounce · focus reticle · zoom pill slide · mode change label slide + brief preview blur · slider value bubble · skeleton shimmer → fade-in · invalid-field shake · Ken-Burns on memories (pauses off-screen/battery-saver).
**Reduce Motion** (system animator scale = 0 or Far setting): swap hero/spring/parallax for 100–150ms fades; stop shimmer and Ken-Burns; information shown by motion is also shown by a label/check.
**Frame governor:** if jank ratio > 8% over 5s or thermal ≥ moderate or battery saver: step down in order → stop optional animation → disable blur → shrink thumbnails → pause ML/indexing; step back up when stable.

## E7. Haptics & sound
Light: selection start, mode change, toggle. Medium: capture, delete, snap to zoom stop. Heavy: none. Success/failure patterns on save results. One master toggle in Settings. Sound: optional shutter sound, default off unless the law/device requires it.

## E8. Icon system (see P12 for implementation)
One open family (Phosphor recommended: Regular = unselected, Fill = selected; Lucide / Material Symbols Rounded acceptable — choose one after reading the licence in the repo). 24dp artboard, 1.5–1.75dp stroke, round caps. Sizes: 20 inline, 24 toolbar, 28 tab bar, 22 in a 44dp camera circle, 64 empty state. Colours from tokens only; disabled 38% opacity. RTL: mirror back/forward/chevron/undo/redo; do not mirror clock, camera, heart. No mixing sets; missing glyphs are redrawn on the same grid inside one `FarIcons.kt`.

## E9. Names (original; avoids Apple/Google trademarks)
Live/Motion Photo → **Moments** · Captured by Me → **My Captures** · Identity Documents → **IDs & Documents** · Magic Eraser/Clean Up → **Erase** · Night Sight → **Night** · Top Shot/Best Take → **Best Frame** · Photographic Styles → **Looks** · Lens/Siri mode → **Scan & Identify** · Operation Centre → **Activity** · Selection Review → **Selected** · Free Up Space → **Make Room** · Storage Cleaner → **Tidy** · Default App → **Make Far Your Default** · Handoff Mode → **Show an Album** · Privacy Cover → **Cover** · Health Check → **Check-up** · Recently Deleted → **Trash**. App brand stays **Far Gallery** (see Decision Log).

## E10. Microcopy rules
Verbs for actions (“Move to Trash”), nouns for places (“Trash”). Sentence case. ≤20 characters on tabs/chips. One word per concept everywhere. Examples: “Moved 12 items to Trash · Undo”, “Not enough space. Free up 400 MB or choose another folder.”, “This phone’s camera doesn’t support 240 fps.”, “Showing what you’ve allowed. Manage access”.

## E11. Accessibility baseline
TalkBack labels with state (“Favourite, on”); custom actions on thumbnails/rows (Open, Favourite, Select, Share, Delete); logical focus order; focus ring visible with keyboard; 4.5:1 contrast (7:1 high-contrast); targets ≥48dp; timers spoken; no info by colour alone; text alternatives (alt text) editable and searchable.

## E12. Gesture map (global; P56 implements it)
Grid: tap open · long-press select · long-press+drag range · tap date-check selects day · pinch density · drag scrubber · pull down refresh · long-press-hold = Peek. Viewer: swipe sideways · double-tap zoom · pinch/pan · swipe down dismiss · swipe up info · tap toggle chrome. Video: double-tap sides ±10s · press-hold 2× · drag left/right edge brightness/volume. Camera: tap focus · drag exposure · long-press lock · pinch zoom · swipe preview = mode · hold shutter burst · volume key shutter. Editor: press-hold = original · double-tap slider reset · two-finger tap undo. Files: tap/long-press · swipe row quick actions · drag onto folder (large screens). Avoid custom edge swipes (system Back uses them).

---

# PART F — PROMPT LIBRARY (P00–P60)

**How each block is built:** `TASK` → `GOAL` → `WHAT IT LOOKS LIKE` → `HOW THE USER INTERACTS` → `HOW IT WORKS (plan)` → `EDGE CASES & FAILURES` → `TESTS` → `ACCEPTANCE`. Paste **Session Starter (C1) + one block**. For audit/foundation prompts where "looks like" does not apply, that section is omitted.

---
## PHASE 0 — TRUTH & FOUNDATION

### P00 — Repository audit and project truth
Depends: none · Mode: AUDIT ONLY (no code edits)
```text
TASK P00 — Repository audit and project truth. NO CODE CHANGES except creating FAR_RULES.md, FAR_PROGRESS.md, FAR_DECISIONS.md in the repo root.

GOAL: Learn what really exists. Produce an evidence-based matrix so every later prompt builds on facts, not on APP_OVERVIEW.md.

HOW IT WORKS (plan)
1. Read APP_OVERVIEW.md, settings.gradle(.kts), libs.versions.toml, app/build.gradle(.kts), AndroidManifest.xml, all Room entities/DAOs/migrations/schemas, navigation graph, DI setup, ViewModels, repositories, tests, assets, proguard rules.
2. Run: ./gradlew clean assembleDebug testDebugUnitTest lint. Paste real results. Do not fix anything yet.
3. If an emulator/device is available, install and capture screenshots of: Photos grid, viewer, editor, albums, search, camera, files, settings (Light + Dark). If not, state "no device available".
4. Build the FEATURE MATRIX: one row per feature in APP_OVERVIEW.md and in the master-pack feature catalogue. Columns: Feature | Status (DONE/PARTIAL/MISSING/BROKEN/UNKNOWN) | Evidence (file:class:function) | Test evidence | Notes.
5. VERIFY each of the 8 known defects (pack §D5): for each say STILL PRESENT / FIXED / NOT FOUND with the exact lines.
6. Record: applicationId, namespace, minSdk/targetSdk/compileSdk, AGP/Kotlin/Compose/Room/CameraX/Media3/Coil/ML Kit versions, Room schema version, tables, current navigation tabs, current themes/fonts, permissions in the MERGED manifest.
7. Create FAR_RULES.md (pack Part B), FAR_PROGRESS.md (pack Part H table), FAR_DECISIONS.md (pack Part J + the owner conflicts in §A7).
8. Produce: risk register (P0/P1/P2), the baseline build/test result, and a proposed order for the next prompts.

EDGE CASES: build fails → report the exact error, do not fix; APP_OVERVIEW.md contradicts code → code wins, list contradiction; generated code folders → ignore.
TESTS: none to write. ACCEPTANCE: matrix covers 100% of documented features with evidence; 8 defects each verified; baseline build result is real; three context files exist.
Finish with the Standard Report. Do not start P01.
```

### P01 — Architecture, migration and implementation proposal
Depends: P00 · Mode: PROPOSAL ONLY · **APPROVAL GATE**
```text
TASK P01 — Architecture, migration and implementation proposal. No feature code.

GOAL: A written, reviewable target architecture that keeps native Kotlin/Compose and the existing MVVM/StateFlow style, and a safe path to the unified Photos+Camera+Files model.

DELIVERABLES (write to docs/ARCHITECTURE_PROPOSAL.md, summarise in chat)
1. Target layers and package layout (map to existing packages; do not mass-rename).
2. Canonical MediaAsset identity (pack §D4): how MediaStore ids, volume names and SAF document URIs become stable assetIds; how moves/renames are re-linked; how missing files become availability=MISSING instead of phantom rows.
3. Room ERD + migration plan (version N→N+1…) with exact new tables/columns/indices, FTS design for OCR/names/tags, rollback and snapshot strategy.
4. File-operation journal state machine: PENDING→COPYING→VERIFYING→COMMITTING→DONE/FAILED/CANCELLED with resume rules.
5. Background work map (WorkManager unique works, constraints, backoff) for scan, OCR, ML, hashing, exports.
6. Process boundaries (isolated decode/ML/transcode process) and crash quarantine.
7. Manifest/permission diff (exact), dependency decisions table (keep / upgrade / replace / add) with licence, size, maintenance, risk.
8. Navigation graph plan for the 3-destination shell and deep-link/intents routing (IntentRouter).
9. Test strategy: unit, instrumented, screenshot, migration, macrobenchmark, fault-injection, monkey.
10. Performance budgets per device class (low/mid/high): cold start, scroll jank, query ms, memory.
11. Phase 1 file-by-file change list.
12. Alternatives considered + trade-offs.

STOP CONDITION: Wait for the owner's written approval before any schema-breaking change, new permission, new native module, major dependency replacement or separate network flavour. Record the approval in FAR_DECISIONS.md.
ACCEPTANCE: every item above present, evidence-based, consistent with P00 matrix. Standard Report. Do not start P02.
```

### P02 — Build reproducibility, Gradle and dependency stabilisation
Depends: P01 approved
```text
TASK P02 — Make the build reproducible and the dependencies stable. No product behaviour changes.

GOAL: One command builds debug and release; versions live in one catalog; alpha libraries are replaced only when a verified stable, compatible version exists.

HOW IT WORKS
1. Audit Kotlin, AGP, Gradle, Compose compiler/BOM, Room+KSP, CameraX, Media3, Coil, ML Kit, Accompanist, WorkManager, Paging for compatibility matrix, maintenance, licence, APK-size impact.
2. Centralise all versions in libs.versions.toml; remove duplicate/unused libraries ONLY after grep-proving non-use.
3. Replace Room 2.7.0-alpha08 and CameraX 1.5.0-alpha01 by the newest STABLE compatible versions if the migration is safe; if not, document why and pin.
4. Enable R8/minify for release with keep rules verified by running the release build; enable Gradle build cache and configuration cache if compatible.
5. Add scripts: scripts/build_debug.sh, scripts/test_all.sh, scripts/lint.sh and document in README_DEV.md (including how to run instrumented tests).
6. Verify the merged manifest still has NO android.permission.INTERNET (add a Gradle check task `verifyNoInternetPermission` that fails the build otherwise).
7. Add a licence inventory file (docs/LICENSES.md) listing each dependency, licence, and where attribution appears.

EDGE CASES: a stable Room version changes generated schema → export schemas, diff, write migration test; Compose compiler mismatch → fix plugin versions together.
TESTS: debug + release assemble, unit tests, lint, verifyNoInternetPermission, migration test unaffected.
ACCEPTANCE: clean build twice in a row on a clean checkout; dependency change table with reason, licence, size delta and rollback path in the report.
```

### P03 — Reliability foundation and unified error model
Depends: P02
```text
TASK P03 — Implement the typed error model and safe-call wrappers used by every later feature.

GOAL: No external call can crash the app; every failure becomes a typed value with a human message and a next step.

WHAT IT LOOKS LIKE
- Error surfaces use the shared ErrorState/UndoToast components (pack E5). Full-screen error: warning icon, title (“Can’t open this file”), one-sentence cause (“It looks damaged or uses a format this phone can’t read.”), one button (“Open with another app” / “Try again” / “Open Settings”). Inline errors: red caption under a field with a shake. Batch results: toast “11 done · 1 failed — View”.
- Never show: stack traces, exception names, raw paths, error codes.

HOW THE USER INTERACTS: taps the single recovery action; or dismisses; partial success summaries open Activity details (P47).

HOW IT WORKS
1. Define sealed FarResult<T> (Ok/Err) and FarError kinds: Permission, Storage, Provider, FormatCorrupt, Conflict, Memory, Hardware, Model, Cancelled, Timeout, Internal — each with userTitle, userMessage, RecoveryAction(enum + payload), isRetryable, loggableCode (no private data).
2. Safe wrappers: safeContentResolver{}, safeSaf{}, safeMediaStore{}, safeRoom{}, safeCamera{}, safeMedia3{}, safeMl{}, safeDecode{}; map SecurityException→Permission, FileNotFoundException/null cursor→Provider/Missing, IOException with ENOSPC→Storage, OutOfMemoryError→Memory (catch narrowly in decoder paths), TimeoutCancellationException→Timeout.
3. batch(items){ per-item runCatching } returning BatchResult(successes, failures[list of (assetId, FarError)]).
4. Coroutine infrastructure: app-level CoroutineScope with SupervisorJob + CoroutineExceptionHandler that logs sanitised info; withTimeout on provider/disk calls that can hang.
5. Debug-only StrictMode (thread + VM policy, penaltyDeath on main-thread disk/network in debug only), LeakCanary in debug.
6. Local sanitised logger: ring buffer of last 200 breadcrumbs (screen, action, error kind, no filenames), persisted to app-private file; export is user-initiated only (P47).
7. Global uncaught-exception handler writes a sanitised crash file, then delegates to the default handler. Crash-loop counter prepared for Safe Mode (P47).
8. Replace existing try/catch swallowing in repositories incrementally; list each replaced site.

EDGE CASES: error inside a Flow collector; error inside a Compose side-effect; cancellation must propagate (never swallow CancellationException); double-fault in error handler.
TESTS: unit tests per mapping; fault-injection fakes (null cursor, throwing provider, hanging call → timeout, revoked grant, ENOSPC, corrupt bytes); batch isolation test (1 of 20 fails → 19 succeed).
ACCEPTANCE: no repository public function throws; grep shows no remaining `!!` in changed files; every error kind has an on-screen example in the component gallery (P12 will reuse).
```

### P04 — Database recovery, migrations and metadata preservation
Depends: P03
```text
TASK P04 — Make far.db safe: no data-destroying recovery, tested migrations, metadata export/import.

GOAL: Users’ albums, tags, ratings, captions, edits and order survive upgrades, corruption and crashes.

WHAT IT LOOKS LIKE
- If recovery ever runs, a full-screen calm “Restoring your library data” page with a progress bar, then a summary sheet: “Recovered 412 albums, 9,031 tags, 1,204 edits. 3 items couldn’t be recovered.” + “View details” + “Continue”. No scary language.
- Settings › Backup & Export (stub entry added later in P48) will expose “Export library data”. In this prompt expose it from a developer-only screen if Settings isn’t ready.

HOW THE USER INTERACTS: nothing in normal use; in recovery they read the summary and continue.

HOW IT WORKS
1. Inspect the real corruption handler. REMOVE any deleteDatabase("far.db") path. Replace with: close DB → copy far.db(+wal/shm) to app-private snapshots/far-YYYYMMDD-HHMMSS/ → try PRAGMA integrity_check → try opening read-only and dumping table-by-table to a JSON sidecar (salvage) → create a fresh DB → import salvaged rows with validation → write a RecoveryReport (counts recovered/lost per table) → show summary.
2. Pre-migration snapshot: before any migration run, copy the DB files; keep the last 3 snapshots; delete older ones only when the app’s latest migration completed successfully and a day has passed.
3. Write explicit Migration objects for every schema version in /schemas; never fallbackToDestructiveMigration.
4. MetadataExport: documented JSON (versioned) for albums, album_folders, memberships (by stable asset key + fallback keys: display name+size+dateTaken), tags, ratings, captions, edit stacks, collection layout, settings subset. MetadataImport validates schema version and reports conflicts.
5. Orphans: if a referenced media item is missing, mark availability=MISSING; never recreate a phantom file; allow “Relink” later (P10/P38).

EDGE CASES: disk full during snapshot (abort migration safely, show Storage error); half-written snapshot; WAL present; downgrade (refuse to open newer schema, explain); two processes opening DB.
TESTS: MigrationTestHelper for every version pair; corruption test using a deliberately damaged DB file (salvage retains X% rows); export→wipe→import round-trip equality test; snapshot rotation test.
ACCEPTANCE: grep finds no deleteDatabase call outside tests; every migration test passes; round-trip test passes; recovery summary screenshot attached.
```

### P05 — Large-library test fixtures and performance guardrails
Depends: P03
```text
TASK P05 — Synthetic big-data fixtures and performance guardrails.

GOAL: Prove the app scales: 100,000 photo rows, 5,000 videos, 50,000-entry file trees, plus corrupt/hostile inputs, with bounded memory and smooth scrolling.

HOW IT WORKS
1. Fixture generator (androidTest + debug-only tool screen): deterministic seed; inserts N assets into a test MediaStore-like fake (repository interface) and Room; generates small real JPEG/PNG/MP4 samples plus: zero-byte files, truncated JPEG, 20000×20000 PNG header, malformed EXIF, ZIP with 100k entries, zip-slip names (../), 1GB sparse file, filenames with emoji/RTL/very long names.
2. Unreliable provider fake: returns null cursor, throws SecurityException, hangs, vanishes mid-read.
3. Remove production caps (LIMIT 2000/10000, take(40)); replace by cursor chunking (500–2,000 rows per step, resumable checkpoint), Paging 3 for visible lists, chunked `IN (...)` (≤ 500 args), FTS instead of broad LIKE, thumbnail downsampling, bounded LRU (memory ≤ 15% heap; disk default 512 MB adjustable).
4. Selections: store selected IDs in a ViewModel-scoped `SelectionStore` backed by Room table `selection_session` for >5,000 ids; never in nav args/Intents.
5. Benchmarks (Macrobenchmark + Baseline Profile module): cold start, first grid frame, scroll 100k, query latency (search/filter), thumbnail memory peak, bulk 5,000-item operation, index throughput.
6. docs/PERF_BUDGETS.md: low/mid/high device budgets (e.g., first page ≤ 800 ms mid-tier; scroll jank ≤ 5%; peak viewer memory ≤ budgeted).

EDGE CASES: memory pressure callbacks (onTrimMemory) must flush caches; scan continues after process death from checkpoint.
TESTS: benchmark runs recorded with numbers; unit test for chunk iterator resuming at checkpoint; fixture determinism test.
ACCEPTANCE: no hard-coded analysis caps remain; 100k scroll test passes on the best available device with numbers attached; if no device, mark UNTESTED honestly.
```

---
## PHASE 1 — DATA & OPERATIONS

### P06 — Canonical asset identity and Room data model
Depends: P04, P05
```text
TASK P06 — One physical file = one canonical asset record.

GOAL: Photos, Camera and Files all read the same MediaAsset; a new capture shows once everywhere; metadata is attached to assets and survives renames/moves.

HOW IT WORKS
1. Implement MediaAsset (pack §D4) + AssetKey: MediaStore → "ms:{volume}:{id}"; SAF → "saf:{treeUri-hash}:{documentId}"; app-private → "ap:{relativePath}". Add secondary fingerprint (size + dateTaken + displayName + optional hash) for re-linking after rename/move/volume remount.
2. Room: assets_meta (assetKey PK, favorite, archived, hidden, rating, caption, alt_text, capturedByFar, provenance, contentHash, availability, lastSeen), plus link tables. Do NOT store thumbnails or bytes in Room.
3. AssetRepository exposes Flow<PagingData<MediaAsset>> by query spec (filters, sort by dateTaken|dateAdded, folder, album, type) and getByIds(ids chunked).
4. Reconciler job: for MediaStore, observe ContentObserver; on change, run targeted rescan; mark MISSING after 2 consecutive misses and a grace period; auto-relink if a fingerprint match appears.
5. Provenance: Camera sets capturedByFar=true at capture commit (not by folder name).
6. Migration + tests; update all existing queries to use AssetRepository; no feature may bypass it.

EDGE CASES: duplicate display names; two volumes with same relative path; media replaced by another app with same id; SAF grant revoked (availability=REVOKED, not MISSING).
TESTS: same-capture-once test across Photos/Files repositories; rename/move keeps tags/albums/ratings; revoked grant doesn’t delete metadata; migration test.
ACCEPTANCE: single source for asset queries; grep shows no direct MediaStore queries in UI/ViewModels.
```

### P07 — Incremental media scanner and folder indexing controls
Depends: P06
```text
TASK P07 — Fast, resumable scanning + user control of what is indexed.

WHAT IT LOOKS LIKE
- Settings › Library & Indexing: toggle “Scan automatically”, a status line (“Last scan 2 min ago · 41,902 items”), “Scan now”, “Rebuild index”, “Clear thumbnail cache (212 MB)”, and a Folder list (rows with folder name, count, and a chevron). Each folder row opens a sheet with switches: Show in timeline · Text recognition (OCR) · Smart labels (AI) · Memories · Widgets. A banner when permission is limited: “Showing what you’ve allowed · Manage access”.
- First-run explainer sheet: “Far finds your photos and videos on this phone. Nothing is uploaded.”

HOW THE USER INTERACTS: toggles automatic scan; taps Scan now (a thin progress line under the header; tap opens Activity); includes/excludes folders; adds a custom folder via the system folder picker (SAF tree) — persisted grant.

HOW IT WORKS
1. MediaStore scan: query in id/date_modified order in chunks with checkpoint (lastId, generation); diff with assets_meta; insert/update/mark-missing in short transactions (≤500 rows).
2. SAF scan: DocumentsContract.buildChildDocumentsUriUsingTree with projection, iterative stack (no recursion depth limit), checkpoint per directory.
3. ContentObserver on MediaStore for new/changed items → enqueue targeted rescan (debounce 2 s). WorkManager periodic (12 h, charging+idle) safety scan.
4. folder_rules table: per-folder flags; excluded folders never appear in timeline/OCR/AI/memories/widgets; app-private & “.nomedia” folders skipped.
5. Progress is truthful (processed/total when total known; else count + “Scanning…”). Cancel stops after the current chunk; resumes from checkpoint.
6. Rebuild index clears derived tables (OCR/labels/hashes) but never albums/tags/ratings; confirm dialog states exactly this.

EDGE CASES: permission revoked mid-scan; volume unmounted; 1M-row libraries; the same file visible through MediaStore and SAF (dedupe by fingerprint).
TESTS: checkpoint-resume after force-stop; add/remove/move detection; exclusion honoured everywhere (instrumented); 100k scan memory bounded.
ACCEPTANCE: first page of grid renders before scan finishes; scan survives process death; all toggles persist and take effect immediately.
```

### P08 — Background indexing, OCR scheduling and Activity integration
Depends: P07
```text
TASK P08 — OCR and heavy indexing run in WorkManager with real progress, never in the foreground by default.

WHAT IT LOOKS LIKE
- Search screen shows a slim status line under the search field: “Text indexing 63% · paused while on battery” (tap → Activity). No big banner or “Index Photos” button.
- Activity (P47) lists: OCR indexing, Hashing for duplicates, Scene labelling — each with progress, Pause/Resume, Cancel, “Only while charging” switch.

HOW IT WORKS
1. Unique periodic+one-time works: OcrIndexWorker, HashWorker, (later MlLabelWorker). Constraints: battery-not-low, storage-not-low; optional requires-charging/idle setting; thermal check (PowerManager.getCurrentThermalStatus ≥ MODERATE → pause).
2. Checkpointed batches (e.g., 25 items), setProgress with done/total, expedited only for “Scan now” user action.
3. OCR text stored in `ocr_text` (assetKey, text capped at 20 KB, lang, version) + FTS table; asset rows stay small. Re-OCR only when asset modified or OCR version changes.
4. Skip excluded folders and hidden items (G15). New captures/imports enqueue a high-priority single-item OCR.
5. Foreground manual option: “Index now (keep screen on)” for users who disable background work.
6. Survive reboot (WorkManager persists); handle revoked permission by pausing with a clear reason.

EDGE CASES: OCR model missing → feature “Unavailable”, search still works for names/tags; huge images → downsample to ≤ 2,048 px long edge for OCR; corrupt images → quarantine after 2 failures.
TESTS: pause/resume/cancel; progress accuracy; process-death resume; no OCR for excluded/hidden; FTS query returns expected matches.
ACCEPTANCE: UI never blocks; progress values equal real completed counts; battery/thermal pause verified.
```

### P09 — Incoming URI resolver and external intent safety
Depends: P03
```text
TASK P09 — Harden how Far opens things other apps send it.

GOAL: Any valid provider-backed URI works; unsafe or unsupported input fails with a friendly message and never crashes.

WHAT IT LOOKS LIKE: when an external app opens a photo in Far, the viewer opens immediately (no library load first). Unsupported input shows ErrorState “Can’t open this file” with “Close” and, if possible, “Open with another app”. Back returns to the calling app.

HOW IT WORKS
1. IntentRouter parses ACTION_VIEW, SEND, SEND_MULTIPLE, GET_CONTENT, PICK, IMAGE_CAPTURE, VIDEO_CAPTURE into a sealed IncomingRequest (IDs/URIs only).
2. IncomingMediaResolver accepts content:// (any authority) and file:// only when readable; verify MIME via ContentResolver.getType + magic-byte sniff; never trust display names or paths; never assume /storage/emulated/0.
3. Take persistable URI permission when offered; otherwise use the temporary grant for the session and copy to app-private cache ONLY if an edit/destination needs it (document why).
4. Stream-read with size limit warnings (files > 2 GB handled by streaming, not memory).
5. Display-name sanitisation (strip path separators, control chars, length cap).
6. Return results correctly for pick/capture: setResult with clip data + FLAG_GRANT_READ_URI_PERMISSION.

EDGE CASES: null metadata columns; zero-byte; revoked grant mid-view; provider throws; MIME lies; very long URIs; multiple items >500.
TESTS: instrumented tests with fake ContentProviders (null cursor, SecurityException, wrong MIME, huge size); malicious display names; resolver unit tests.
ACCEPTANCE: no crash on fuzzed inputs (500 random URIs in a test); results delivered correctly to calling test app.
```

### P10 — Atomic file operations and operation journal
Depends: P06
```text
TASK P10 — One crash-safe engine for copy, move, rename, import, export, extract, delete-to-trash and batch actions.

WHAT IT LOOKS LIKE
- Starting any long operation shows a ProgressCard in Activity and a small status chip (“Copying 12 files · 45%”) in the app header; tap opens Activity.
- Conflict dialog (centered glass card): “‘Report.pdf’ already exists”, thumbnails of both with size/date, buttons **Keep both** (preview of new name “Report (2).pdf”), **Replace** (red, asks a second confirm), **Skip**, switch “Apply to all”.
- Result toast: “11 copied · 1 skipped”.

HOW THE USER INTERACTS: choose destination → conflicts resolve in dialog → progress runs in background; user can Pause/Cancel/Retry; leaving the app is safe.

HOW IT WORKS
1. Journal table operation_records: opId, type, items[], state machine PENDING→COPYING→VERIFYING→COMMITTING→DONE|FAILED|CANCELLED, per-item state, bytesDone, conflictPolicy, createdAt, checkpoint.
2. Copy by streaming 256 KB–1 MB chunks into a temp/pending destination (MediaStore IS_PENDING=1 or temp name), then verify size (+ SHA-256 for moves across volumes), then atomically finalise; ONLY THEN delete the source (move). Never delete the only copy.
3. Rename: try provider rename; if it returns null/throws → report “This location doesn’t allow renaming”; offer copy-verify-delete only if safe and user agrees; never report success on null.
4. Free-space preflight (needed bytes + 10% margin); ENOSPC handled; temp cleanup in finally and by a startup recovery job.
5. On launch: recover journal — resume or roll back each unfinished op and show what happened.
6. Batch actions each wrapped per item; PartialResult summary.
7. Undo: for trash/rename/move/album changes record inverse operations in an undo table (time-limited); Undo toast triggers inverse op.

EDGE CASES: destination removed mid-copy; source modified during copy (detect by size/mtime → ask); cancellation during commit; SD card unmounted; provider timeouts; same-file copy; cyclic folder copy.
TESTS: kill process at each state and verify recovery; low-storage emulation; conflict policy matrix; 5,000-item move; cross-volume move verification.
ACCEPTANCE: no test leaves a partial or lost file; journal shows exact history; Undo works for trash/rename/move/album ops.
```

### P60 — Media format support (Android + iPhone formats), decode honesty
Depends: P06, P09
```text
TASK P60 — Support the widest set of Android and iPhone media formats the device can decode, and be honest about the rest.

GOAL: Photos and videos from Android, iPhone (HEIC/HEIF, MOV/HEVC, Live Photo pairs, ProRAW DNG), cameras (RAW), and the web (WebP, AVIF, GIF, APNG) display correctly, or show a clear “Can’t preview on this phone” tile with “Open with another app”.

WHAT IT LOOKS LIKE
- Grid/viewer show the image normally. For unsupported formats: tile shows a format badge (“HEIC”, “AV1”) over a soft icon; viewer shows ErrorState with the format name and what the phone can/can’t do.
- Info sheet › File shows Format, Codec, Colour space (sRGB/Display P3/HDR), Bit depth, Frame rate, Bitrate, and a “Supported on this phone: Yes/No (reason)” line.
- Settings › Library › “Format support” opens the Capability Matrix screen: rows per format with ✓ Decode · ✓ Encode · ✓ Thumbnail · ✓ Export (data from runtime checks, not hard-coded).

HOW THE USER INTERACTS: nothing special; unsupported formats degrade gracefully; user can export a converted copy where possible (“Save as JPEG/PNG/MP4”).

HOW IT WORKS
1. Formats: JPEG, PNG, WebP (lossy/lossless/animated), GIF, APNG, BMP, HEIC/HEIF (+ burst/depth where supported), AVIF, JPEG XL (only if decoder available), DNG/RAW (via system decoder and ImageDecoder/LibRaw-class library only after licence/size review), TIFF (best effort), SVG preview (files screen only), Ultra HDR JPEG (gain map), Motion Photo (Google/Samsung XMP/MicroVideo, extract embedded MP4), Apple Live Photo as a still + .MOV pair detected by shared content identifier or filename; video: MP4/H.264/HEVC/VP9/AV1, MOV, MKV, WebM, 3GP, HDR10/HLG where the display/decoder allow.
2. Runtime capability probe: MediaCodecList (decoders/encoders + profile/level), ImageDecoder.isSupported/BitmapFactory MIME checks, Display HDR capabilities; cache in a CapabilityRegistry.
3. Decode pipeline: ImageDecoder with downsample + hardware config where valid; Coil with custom Decoder factories only for formats the platform lacks (e.g., Motion Photo frame, Live Photo still); animated images via ImageDecoder AnimatedImageDrawable; videos thumbnails via MediaMetadataRetriever / Media3 frame extractor.
4. Colour management: Display P3 images shown with wide-colour mode on capable displays; HDR gain-map shown only when the display supports it, else SDR base image.
5. Pairing logic for Live Photo / Motion Photo: grouped as ONE asset with a “Moments” badge; toggle “Show paired videos separately” in Settings.
6. Export converters: HEIC→JPEG, WebP→PNG/JPEG, video→MP4 (Media3 Transformer) saved as copies (G4).
7. Never crash on unknown formats: any decoder failure → FormatCorrupt with quarantine after repeated failures (P03/P47).

EDGE CASES: EXIF orientation + HEIC rotation; 16-bit PNG; huge TIFF; truncated file with valid header; HEVC without hardware decoder; DNG with embedded preview only; Live Photo with missing half.
TESTS: sample corpus covering every format above (small files committed under androidTest/assets with licences OK to redistribute or generated); decode/thumbnail/viewer instrumented tests; capability matrix unit test with fake codec list.
ACCEPTANCE: every supported format renders or shows the honest tile; matrix screen reflects the real device; no crash on corpus; Standard Report lists formats NOT supported on the test device.
```

---
## PHASE 2 — DESIGN SYSTEM & SHELL

### P11 — Design tokens, themes and materials
Depends: P02
```text
TASK P11 — Implement the design tokens, five themes and glass materials from pack Part E (E1–E4). Evolve the existing glass theme; do not rewrite unrelated screens yet.

GOAL: Every colour, type style, spacing, radius and material comes from tokens, so a calm iOS-27-style look can be applied everywhere and themes switch instantly.

WHAT IT LOOKS LIKE
- Default look: neutral background (#F2F2F7 light / pure black dark), white or #1C1C1E cards, ONE blue accent, hairline separators, large bold titles, no drop shadows, no neon. Bars and sheets are translucent frosted glass (blur on API 31+, solid 94% fallback otherwise).
- Themes (Settings › Appearance): System (default) · Light · Dark · AMOLED · High Contrast. Under it: Accent (Blue default + optional Cyan, Magenta, Lime, Violet, Amber — the old neon set, OFF by default), switches: Reduce transparency, Reduce motion, Use dynamic colour (off in Camera).
- Appearance screen has a live preview card at the top (mini grid + bar) that updates as you change options.

HOW THE USER INTERACTS: choose theme; segmented picker animates 250 ms; whole app crossfades (no flash). Changing font scale in system settings reflows without restart.

HOW IT WORKS
1. FarTheme composable providing FarColors, FarTypography, FarShapes, FarSpacing, FarMotion, FarMaterial via CompositionLocals; Material3 mapped onto them (keep M3 components where they exist but styled by tokens).
2. Theme state in DataStore; applies before first frame (read synchronously via a cached value / splash hold ≤ 150 ms) to avoid flashes.
3. GlassSurface(modifier, level) composable: draws blur through RenderEffect on API 31+, fallback opaque otherwise; automatically switches by capability, Reduce Transparency and the frame governor (pack E6). Never blur scrolling content itself.
4. Remove hard-coded colours/fonts in changed files; add a Detekt/grep rule “no Color(0x…) outside theme package”.
5. Font: system sans or bundled Inter variable (OFL; confirm licence file) via the typography scale in E2; monospace only for technical values.
6. Token gallery screen (debug-only): colour swatches, type ramp, radii, spacing, glass samples in all five themes; screenshot tests for each.

EDGE CASES: font scale 200% (no clipping); RTL; display cutouts; split-screen; HDR/wide gamut not required; dark-mode flash on launch.
TESTS: screenshot tests (5 themes × 3 densities); contrast unit test (WCAG ratio ≥ 4.5/7 for text tokens); theme persistence test.
ACCEPTANCE: gallery screenshots attached; switching themes has no recomposition errors; grep shows zero hard-coded colours in migrated files.
```

### P12 — Icon family, app icon and reusable component gallery
Depends: P11
```text
TASK P12 — Create FarIcons, the original app icon set, and every reusable component in pack E5 with a component gallery.

GOAL: One consistent icon style (outline when idle, filled when selected) and a library of components every later screen reuses.

WHAT IT LOOKS LIKE
- Icons: clean 24dp outline glyphs (1.5–1.75dp round stroke) that fill when selected: Photos (frame+mountain+sun), Camera (body+lens), Files (folder), Search (magnifier), Heart, Info (i), Share (square+up arrow), Edit (sliders), Trash, Add-to-album, Archive, Hide (eye-slash), Rating (star), Tag, Map pin, People, Paw, Memories (sparkles), Calendar, Play/Pause, Mute, Speed, Scissors, Flash, Timer, Grid, Level, Flip-camera, Zoom, Burst, Pano, Scan, Moon, Portrait, Folder-star, SD card, USB, Zip box, Broom, Chart, Check/Warning/Error/Offline, chevrons, close, more (…). (Full map in earlier pack §20.3.)
- App icon: adaptive icon — rounded-square base in calm deep blue/teal with a lens ring whose lower edge opens into a folder fold (says camera + photos + files). Provide foreground (inside the central 66dp of 108dp), background, and a monochrome layer for Android 13+ themed icons. Also: notification small icon (white), Quick Settings tile, widget header icon, launcher shortcut icons (Camera, Scan, Search), splash icon, 512px Play icon.
- Empty-state art: 64dp line icons with one accent colour.
- Component gallery (debug screen): every E5 component in every state/theme/RTL/200% font.

HOW THE USER INTERACTS: users see consistent icons; filled = selected/on; pressed state dims 12% for 100 ms.

HOW IT WORKS
1. Read the licence of the chosen set in the repo (Phosphor/Lucide/Material Symbols Rounded) and record it in docs/LICENSES.md + About screen attribution. Do NOT use SF Symbols or proprietary icons.
2. Convert SVGs to Compose ImageVector (one file per icon, generated and checked in) collected under FarIcons object with `Regular` and `Fill` variants; no bitmaps.
3. Icon composable: size tokens, tint from theme, RTL mirroring flag, mandatory `contentDescription` parameter (nullable only for decorative), state suffix helper (“Favourite, on”).
4. Implement all E5 components with previews and parameters for loading/empty/error/disabled; Modifier.minimumInteractiveComponentSize(48dp).
5. Design the original launcher icon (vector XML, 3 variants evaluated at 48px light/dark next to common apps); pick one; document the choice.
6. Component gallery + screenshot tests for Light/Dark × normal/200% × LTR/RTL.

EDGE CASES: missing glyph → redraw on the same grid in FarIcons (never mix another set); themed icon rendering on API 33+; icon legibility at 16dp.
TESTS: screenshot tests; a11y test that every IconButton has a description (lint-like unit test scanning gallery).
ACCEPTANCE: gallery shows all components; zero other icon sources in code; launcher icon shows in light/dark/themed.
```

### P53 — Motion and transition system (NEW)
Depends: P11, P12
```text
TASK P53 — Implement the motion tokens and every shared transition from pack E6, with Reduce Motion and the frame governor.

GOAL: Motion explains where things come from and go; it must never slow the user down or cause jank.

WHAT IT LOOKS LIKE (each transition, precisely)
- Grid → Viewer: the tapped thumbnail grows (350 ms emphasis easing) into the full-screen image while the background fades to black and chrome fades in; reverse on dismiss.
- Viewer swipe-down: image follows the finger 1:1, scales down toward ~85% and the black background fades proportionally; release past 120dp or on fling → image shrinks into its grid cell and the grid scrolls to keep that cell visible; otherwise spring back (damping 0.85).
- Tab switch: crossfade 150 ms; scroll positions preserved; no slide.
- Camera open: slides up from the bottom with a dim (350 ms); close = swipe down on the preview’s top handle or system back.
- Bottom sheets: slide up 250 ms with scrim fading in; drag with detents.
- Selection mode: top bar swaps text (fade 150 ms), bottom action bar slides up 200 ms, checks scale 0.6→1 with spring.
- Trash: thumbnail scales to 0.85 + fades out (200 ms), grid closes the gap with a 200 ms move; Undo toast slides in; Undo reverses.
- Favourite: heart scales 1→1.3→1 (200 ms bounce) with a light haptic.
- Shutter: white flash overlay 60 ms @ 40% opacity, ring squeezes to 54dp then returns, last-capture thumbnail slides/bounces into place and morphs to the new photo.
- Focus reticle: scales 1.2→1.0 (150 ms), fades after 2 s.
- Mode change: labels slide 250 ms; preview blurs briefly (80 ms) during camera reconfigure.
- Skeletons: shimmer 1.2 s loop → crossfade into content 150 ms.
- Invalid input: 8dp horizontal shake 2 cycles (200 ms).
- Memories: slow zoom/pan (Ken Burns) 6–8 s per photo.

HOW THE USER INTERACTS: motion is incidental; all gestures have visible button alternatives. Settings › Accessibility › “Reduce motion” (follows system) and “Animations: Full / Reduced”.

HOW IT WORKS
1. FarMotion object (tokens from E6) + helpers: `Modifier.farPressScale()`, `FarSharedTransition` wrapper using Compose shared-element APIs if the project’s Compose version supports them; otherwise a custom hero overlay layer (document which).
2. ReduceMotion provider reading Settings.Global.ANIMATOR_DURATION_SCALE and the Far setting; when true: replace hero/spring/parallax with 100–150 ms fades; disable shimmer/Ken-Burns.
3. FrameGovernor: monitors jank via JankStats; thresholds in pack E6; exposes a StateFlow<QualityLevel> consumed by GlassSurface, thumbnails, animations; logs level changes to the local breadcrumb.
4. Rules enforced: animate only graphicsLayer transforms/alpha in lists; never animate size of many cells; cap concurrent animations ≤ 4; pause off-screen animations.
5. Predictive back animation for viewer/sheets on Android 13+ (fallback plain transitions on 11–12).

EDGE CASES: rapid repeat taps; interrupting a transition; rotation during transition; low refresh displays; 120 Hz.
TESTS: unit tests for ReduceMotion mapping; Macrobenchmark for viewer open/close frame time (target no dropped frames at the display’s refresh); screenshot tests of start/end states.
ACCEPTANCE: all transitions above implemented or explicitly listed PARTIAL; none blocks input; Reduce Motion verified.
```

### P54 — Haptics, sound and feedback system (NEW)
Depends: P11
```text
TASK P54 — Centralise haptic and sound feedback.

WHAT IT LOOKS/FEELS LIKE
- Light tick on: long-press select, mode change, toggle, picker snap. Medium thump on: capture, delete, zoom-stop snap. Success pattern (two short pulses) on save/export done; failure pattern (one long pulse) on save failure. Shutter sound is optional (off by default unless required by region/device).
- Settings › Camera & Feedback: “Haptics” (Off / Light / Standard), “Shutter sound”, “Volume buttons take photo”.

HOW IT WORKS
1. FarHaptics object: `tick()`, `select()`, `snap()`, `capture()`, `delete()`, `success()`, `failure()` using HapticFeedbackConstants where available, VibrationEffect predefined effects on API 29+, graceful no-op without a vibrator.
2. FarSound: SoundPool for shutter and video start/stop; respects ringer mode and system “touch sounds”; disabled in silent mode where law/device does not require it; never plays during Reduce-feedback.
3. Master toggle persisted in DataStore; call sites only use FarHaptics (lint rule: no direct Vibrator use elsewhere).
4. Accessibility: haptics are supplementary; every haptic-only cue has a visual/TalkBack equivalent.

EDGE CASES: battery saver (reduce intensity), no vibrator, Do Not Disturb.
TESTS: unit tests with fake vibrator; settings persistence; manual device check list in report.
ACCEPTANCE: all call sites migrated; toggles work immediately.
```

### P55 — State kit: loading, empty, limited-permission, error, offline-ready (NEW)
Depends: P12
```text
TASK P55 — Build and apply the standard state screens so no screen ever shows a blank area or a spinner on a list.

WHAT IT LOOKS LIKE
- LOADING (lists/grids): skeleton cells (shimmer) in the exact layout that will appear; first frame from cached data within ~300 ms; spinners only for short unknown waits (<2 s) inside buttons.
- EMPTY: centred 64dp accent line icon, Title3 headline, one Subhead sentence, one primary pill button. Examples: Photos “No photos yet — Take a photo” · Album “This album is empty — Add photos” · Search “No results for ‘invoice’ — Try fewer words” · Files folder “This folder is empty” · Trash “Trash is empty”.
- LIMITED PERMISSION: banner under the title “Showing 24 photos you’ve allowed · Manage access” — never says the library is empty.
- PERMISSION DENIED: PermissionCard with explanation and “Open Settings”.
- UNAVAILABLE FEATURE: disabled control + one-line reason (“Slow motion isn’t supported by this camera”).
- ERROR: ErrorState (pack E5). Partial failure: toast with “View”.
- INDEXING: small status chip, not a blocker.

HOW THE USER INTERACTS: one tap on the offered action resolves the state; pull-to-refresh on lists; error “Try again” re-runs the failed call.

HOW IT WORKS
1. Sealed `UiState<T>` = Loading | Content(data, isRefreshing, banner?) | Empty(reason) | LimitedAccess(data) | PermissionNeeded(kind) | Unavailable(reason) | Error(FarError).
2. Reusable `StateHost(state){ content }` composable mapping each state to the visuals above, wired to ViewModels from existing screens (list the screens migrated).
3. Skeleton variants: grid, list rows, album cover grid, memory carousel, settings list.
4. Microcopy table in strings.xml (per pack E10) so every string is translatable; no hard-coded strings.

EDGE CASES: content + error simultaneously (keep content, show banner); very slow storage; rotation; RTL.
TESTS: screenshot tests for each state × Light/Dark; UI tests that every migrated screen handles all states.
ACCEPTANCE: Photos, Albums, Search, Files, Camera, Settings migrated; zero blank states.
```

### P56 — Gesture engine, keyboard and accessibility actions (NEW)
Depends: P53
```text
TASK P56 — Implement the global gesture map (pack E12), keyboard shortcuts and TalkBack custom actions as reusable modifiers.

WHAT IT LOOKS/FEELS LIKE
- Long-press a thumbnail: haptic tick, the cell scales to 0.96, selection starts; drag across thumbnails to select a range (auto-scroll when near top/bottom edges). Long-press and hold ~400 ms more shows the Peek preview (rounded card at 70% screen, background dim); release closes, tap opens.
- Pinch in the grid: columns change (2–6 phones, up to 10 tablets) with live interpolation; the photo under the fingers stays under the fingers.
- Viewer: described in P19/P53.
- Keyboard (tablets/ChromeOS): arrows move focus, Space previews, Enter opens, Delete trashes, Ctrl/Cmd+A, C, V, Z, F, F2 rename, Esc closes.
- TalkBack: thumbnails expose actions Open, Favourite, Select, Share, Delete.

HOW IT WORKS
1. Modifiers: `Modifier.farSelectable()`, `farRangeDragSelect(lazyGridState)`, `farPinchDensity()`, `farPeek()`, `farSwipeDismiss()`, `farKeyboardActions()` with a shared `GestureArbiter` to resolve conflicts (scroll vs select vs pinch).
2. Don’t consume edge swipes (system Back); ensure gesture exclusion rects are set only where truly needed (camera preview, sliders).
3. Every gesture has a visible alternative (button/menu) — checked by a UI test list.
4. Semantics: customActions, stateDescription, role, selected; focus order via traversalIndex.
5. Shortcuts via onPreviewKeyEvent + KeyboardShortcutGroups (for Android’s shortcut helper).

EDGE CASES: pinch during scroll; multi-touch palm; RTL (swipe directions mirrored where meaning is “next/previous”); switch access.
TESTS: UI tests (Compose test) for long-press→drag-select, pinch, keyboard actions; TalkBack custom action presence test.
ACCEPTANCE: each gesture has a button equivalent; no gesture steals system Back; demo video/GIF of select-drag optional.
```

### P13 — Three-destination app shell and adaptive navigation
Depends: P11, P12, P53, P55
```text
TASK P13 — Refactor navigation to three top-level destinations: Photos (default), Camera, Files. Keep existing routes and deep links working or migrate them safely.

WHAT IT LOOKS LIKE
- PHONE: bottom GlassTabBar with 3 items (Photos | Camera | Files) as in pack E5. Photos has an inner segmented switch under its large title: **Library · Collections · Albums · Search** (Search also reachable via the magnifier in the title bar). Camera opens full-screen black with the tab bar hidden. Files opens at “Browse”.
- TABLET/FOLDABLE (≥600dp): left NavRail; ≥840dp: expanded sidebar with sections; Files becomes two-pane (list + inspector). Photos uses a bigger grid.
- Settings, Activity, Onboarding are overlays (full-screen sheets) reachable from a “More” (⋯) menu in each large-title bar.

HOW THE USER INTERACTS: tap tabs; re-tap Photos = scroll to top; re-tap Files = Browse root; swipe sideways on Photos segmented content is disabled (avoid conflict); camera swipes down to close; system Back obeys the order below.

BACK ORDER: (1) clear selection → (2) close menu/sheet/search/keyboard → (3) pop screen → (4) return to Photos Library → (5) leave app. Viewer back returns to the grid at the same scroll position with that item visible. Editor asks “Discard edits?” only if changed.

HOW IT WORKS
1. Navigation Compose with a NavHost per top-level destination (separate back stacks via `saveState/restoreState`), IntentRouter (P09) as the single entry for launcher/shortcut/widget/notification/intents with a clean parent back stack.
2. WindowSizeClass-based layout switch (bottom bar / rail / sidebar; one/two panes). Handle fold posture changes without losing state.
3. State restoration: scroll, tab, filters, selection IDs (via SelectionStore), open album/folder, edit recipe, camera mode, search text — survive rotation, process death, language/theme change.
4. Predictive back (Android 13+): viewer shrinks to thumbnail, sheets slide down, folders slide out.
5. Migrate old tabs: Albums and Search become Photos sub-sections; ensure old deep links map to the new routes.

EDGE CASES: deep link to a deleted item; rotation during selection; multi-window; opening Camera without permission (shows PermissionCard); returning from system settings.
TESTS: navigation tests for each back-order case; process-death restoration test; deep-link tests; tablet layout screenshot tests.
ACCEPTANCE: all destinations reachable; no lost scroll/selection; old links still work; accessibility focus order verified.
```

### P14 — Onboarding, permission education and permission-state UX
Depends: P13
```text
TASK P14 — First-run flow and permission handling for Photos/Media, Camera, Files.

WHAT IT LOOKS LIKE
- 3 full-screen pages (swipeable + Next button, “Skip” top-right): (1) “Your photos stay on your phone” (illustration: phone with shield; sub: “Far works offline. No account. No cloud.”), (2) “Everything in one place” (Photos · Camera · Files icons), (3) “Choose what Far can see” with three PermissionCards: **Photos & videos** · **Camera** (and microphone for video) · **Files** (“Pick folders you want Far to manage”). Each card: icon, one-line reason, “Allow”/“Choose folders”, and “Not now”.
- After finishing, the app opens normally even if everything was skipped.

HOW THE USER INTERACTS: taps Allow → Android dialog (Photos: Android 13+ shows “Photos and videos”, Android 14+ may offer “Select photos” → Far shows the limited banner). Files: system folder picker; chosen folders appear in Settings › Library & Indexing and in Files › Locations.

HOW IT WORKS
1. Media: READ_MEDIA_IMAGES/READ_MEDIA_VIDEO (API 33+), READ_EXTERNAL_STORAGE (≤32), READ_MEDIA_VISUAL_USER_SELECTED (API 34+) partial access; ACCESS_MEDIA_LOCATION only when the user opens a feature that needs unredacted location (explain why first).
2. Camera/mic: request at first Camera open / first video; denial → PermissionCard with Open Settings; “Don’t ask again” handled (no repeated prompts).
3. Files: SAF OpenDocumentTree, takePersistableUriPermission; revalidate grants on every app start; revoked → banner + “Choose again”.
4. No MANAGE_EXTERNAL_STORAGE (G8).
5. Permission state machine (Granted / Limited / Denied / DeniedPermanently / Revoked) exposed as a Flow; UI reacts live (no restart).

EDGE CASES: revoke while app open; partial access then user adds more photos; selecting a folder the OS blocks (Download root on Android 11+); multi-user profiles.
TESTS: instrumented permission tests (allow/deny/partial/revoke using UiAutomator or GrantPermissionRule); state-machine unit tests.
ACCEPTANCE: skipping everything still opens the app; limited access is always communicated; no crash on revocation.
```

---
## PHASE 3 — PHOTOS CORE

### P15 — Photos Library: timeline, date navigation, grid controls
Depends: P06, P07, P13, P55, P56
```text
TASK P15 — The main Photos Library: fast, content-first, Apple/Google-Photos-style timeline.

WHAT IT LOOKS LIKE
- Large title “Photos” (collapses on scroll). Trailing icons: Search (magnifier) and More (⋯). Under the title: segmented control **Years · Months · Days · All Photos** (default All Photos) and a filter chip row (Photos, Videos, Favourites, Screenshots, + “Filters” chip with an active-count badge).
- GRID: edge-to-edge square thumbnails with 3dp gaps; phones 3 columns default (range 2–6), tablets 5. Sticky date headers (“Today”, “Yesterday”, “Sat, 22 Nov”, then month/year) on a glass strip. Cells show: video duration pill (bottom-left), heart (bottom-right) for favourites, small badges for Moments / RAW / GIF / Burst (top-right). Count footer at the very end: “41,902 Photos, 3,120 Videos”.
- YEARS view: a mosaic where each year is one large card with a representative photo and the year label; MONTHS: cards per month; DAYS: day cards with 1–4 photo collage. Tapping a card drills into All Photos scrolled to that period (shared-element zoom).
- SCRUBBER: thin rail on the right; dragging shows a bubble “Nov 2026”; tap bubble → calendar sheet (month grid with dots on busy days) → jump.
- Pinch in/out changes density; double-tap on the title bar scrolls to top. Pull-down shows a thin refresh line (“Scanning…”).
- Sort control in More › “Sort by: Date Captured | Date Added” (choice shown in a subtitle under the header, e.g., “Sorted by Date Captured”).
- A small status chip appears while indexing (“Indexing text 63%”) and opens Activity.

HOW THE USER INTERACTS
1. Scroll → sticky headers + scrubber bubble. 2. Pinch → density with item-under-fingers anchoring. 3. Tap segmented control → animated view change. 4. Tap a filter chip → instant filter (Room/MediaStore query) with count in the title (“Videos · 3,120”). 5. Tap “Filters” → bottom sheet: media type (multi), date range, size, duration, orientation, camera/lens (EXIF), edited, has location, album/folder, rating; “Show 1,204 results” button. 6. Tap a photo → viewer (P19). 7. Long-press → selection (P18).

HOW IT WORKS
1. Paging 3 with a RemoteMediator-like source from AssetRepository (P06); stable keys = assetKey; page size 120, prefetch 60; max items kept in memory bounded (maxSize 600).
2. Section headers computed in the paging source (insertSeparators) by capture date (local timezone), memoised.
3. Thumbnails: Coil/ImageDecoder at cell size × density, hardware bitmaps where possible, shared memory cache 15% heap, disk cache 512 MB LRU; cancel off-screen requests; bounded concurrency (≈ cores/2).
4. Years/Months/Days aggregates via SQL group-by on indexed date columns (cached counts; never load all rows).
5. Density pinch: `farPinchDensity()` (P56) with spring settle and persisted columns (DataStore).
6. Sort and filters stored in a `LibraryQuerySpec` (serialisable, saved in ViewModel + DataStore); the same spec drives Collections and Search.
7. Date headers tap-to-select-day uses ID ranges via SelectionStore (P56/P18), never loading the day into memory twice.
8. First page paints from cache/DB before MediaStore reconcile finishes (P07).

EDGE CASES: items without date (use file modified, flagged “Date unknown” group); time-zone changes; 100k items; fast fling decoding; limited permission banner; offline = normal; library empty → EmptyState; rotation preserves scroll.
TESTS: paging unit tests (headers, boundaries); instrumented scroll test with 100k fixture (jank ≤ budget); screenshot tests all views; density persistence; sort persistence; filter combos.
ACCEPTANCE: first frame < budget on mid device; scrubber/calendar jump works; no full-library list ever held in memory.
```

### P16 — Collections, media types and smart albums
Depends: P15
```text
TASK P16 — Collections screen (Photos › Collections) with reorderable sections, Media Types and Smart Albums.

WHAT IT LOOKS LIKE
- Vertical list of collapsible sections with large rounded cards, each with a header (Title2) and “See all”: **My Captures**, **Memories** (carousel, hide-able), **People & Pets**, **Places** (map thumbnail or list), **Media Types**, **Utilities**, **Albums** (cover grid preview).
- MEDIA TYPES rows (icon + name + count): Videos · Selfies · Moments · Portraits (only if depth metadata exists) · Panoramas · Bursts · Screenshots · RAW · GIFs · Documents · Downloads · Edited · Recently Added · Recently Viewed.
- UTILITIES rows: Duplicates · Similar · Blurry · Receipts & Documents · Imports · Hidden (auth if app lock on) · Archive · Trash · IDs & Documents (opt-in, locked by default, with explanation card: “Passports, licences and cards. Locked until you unlock. Excluded from search and AI.”).
- “Edit” button (top-right) toggles reorder mode: drag handles, eye icon to hide a section, pin icon to keep a section at the top.

HOW THE USER INTERACTS: tap a row → filtered grid (same Library component, with a header chip “Screenshots ×”); long-press a section header → pin/hide; drag handle reorders; tapping “My Captures” shows only media captured by Far’s Camera.

HOW IT WORKS
1. Smart collections are SAVED QUERY DEFINITIONS (JSON of LibraryQuerySpec) — not copied folders; they update live as media changes (Room Flow + MediaStore observer).
2. Detection rules (local, documented): screenshots (path/bucket + dimensions == screen ratio + MediaStore SCREENSHOT flag where available), selfies (front-camera EXIF/lens facing metadata), panoramas (aspect ≥ 2:1 or XMP GPano), bursts (burst id/filename pattern + timestamps within 1.5 s), RAW (mime/extension), GIF (mime), Documents (OCR/doc classifier, opt-in), Downloads (bucket), Edited (has edit_stack or Far output), Moments (P60 pairing).
3. My Captures uses capturedByFar provenance (P06), never folder-name inference.
4. IDs & Documents: classifier runs only if the user opts in (default OFF), content hidden behind auth when app lock is on (P37 lock), excluded from OCR/AI/search/widgets/memories (G15).
5. collection_layout table (order, pinned, hidden, collapsed) with migration; restored after restart.

EDGE CASES: empty collections hidden or shown with EmptyState (setting); detection false positives (user can “Not a screenshot” → override flag); very large collections use paging.
TESTS: rule unit tests with fixtures; layout persistence; live update test (add screenshot → counts change); IDs opt-in off by default.
ACCEPTANCE: every collection opens the same Library component; no copies of files created; sections reorderable.
```

### P17 — Albums and album folders
Depends: P16
```text
TASK P17 — Create, organise and display albums and album folders.

WHAT IT LOOKS LIKE
- Photos › Albums: large title “Albums”, “+” button top-right. 2-column grid on phones (3–4 tablets): rounded 12dp cover, album title (Callout) below, count (Footnote, secondary). Sections: **Pinned**, **My Albums**, **Album Folders** (folder tile with stacked mini covers). Empty state: “No albums yet — Create your first album”.
- Album detail: large title = album name, subtitle “124 items”, same grid as Library, “Add” (+) and More (⋯: Rename, Change cover, Sort, Pin, Favourite, Export, Delete album).
- “Delete album” sheet says clearly: “Photos stay on your phone. Only the album is removed.” with a separate secondary red action “Also move photos to Trash” (needs second confirmation).

HOW THE USER INTERACTS
1. Tap “+” → sheet: name field, optional folder, “Create”. 2. Add photos: from Library selection → “Add to Album” → picker sheet (recent albums, search, “New album…”); or inside album tap + to open a multi-select picker. 3. Reorder/pin via long-press → context menu; drag to reorder in Edit mode. 4. Nest: drag an album onto a folder, or Move to Folder… 5. Change cover: choose from album photos. 6. Favourite albums appear in Pinned.

HOW IT WORKS
1. Room: albums, album_folders (parent id, depth ≤ 8), album_media (albumId, assetKey, position, addedAt) with FK cascade on album delete only; assets are never copied.
2. Duplicate membership prevented by unique index; same asset in multiple albums allowed.
3. Custom ordering via fractional positions; sort options Name, Date created, Date of newest item, Manual.
4. Album export: choose destination → ZIP or folder structure via journaled copy (P10); optional sidecar JSON (P38).
5. Missing assets (availability=MISSING) show a dimmed placeholder with “Unavailable”; “Remove missing items” action.
6. Undo for add/remove/delete album using the undo table (P10).

EDGE CASES: very long names (ellipsis, tooltip); same-name albums (allowed, disambiguated in pickers by date); cycles in folders (prevented); albums with 100k items (paging).
TESTS: CRUD + nesting + ordering tests; delete-album-keeps-media test; migration; UI tests for add-to-album flow.
ACCEPTANCE: persistence across restart/migration; media never deleted unless explicitly chosen.
```

### P18 — Selection, favourites, ratings, tags, archive and hidden media
Depends: P15, P17, P56
```text
TASK P18 — Selection mode and organisation actions.

WHAT IT LOOKS LIKE
- Selection: long-press a thumbnail → blue check circles appear on all cells (selected = filled blue ✓ + 12% dim). Top glass bar: **Cancel** · “12 Selected” (tappable) · **Done**. Bottom glass bar: **Share · Add to Album · Favourite · Delete · ⋯**. “More” sheet: Move, Copy, Archive, Hide, Tag…, Rate…, Edit dates/location, Batch rename, Convert, Export, Add to Slideshow/Movie.
- “Select all in this day/month” chip appears when a header is touched. Tapping “12 Selected” opens **Selected** (Selection Review): a list/grid of everything selected across paging, filters and scrolling, each with a remove (✕) button, plus total size.
- Rating sheet: five tappable stars + “Unrated”. Tag sheet: search field + chips of existing tags + “Create ‘vacation’”. Colour labels: 7 colour dots.
- Archive/Hidden: Collections › Utilities › Archive / Hidden. Archived/hidden items disappear from the Library timeline but are never deleted.

HOW THE USER INTERACTS: long-press → drag to extend → tap actions; Delete shows toast “Moved 12 items to Trash · Undo” (no dialog); permanent delete only from Trash with a confirm; actions on 500+ items show a ProgressCard.

HOW IT WORKS
1. SelectionStore (P05/P56): IDs in memory for ≤5k, in Room `selection_session` beyond; survives rotation and process death; cleared on Done/Cancel.
2. Trash uses MediaStore.createTrashRequest (API 30+) in chunks of ≤ 500 per system dialog; consent flow handled; app-level trash table for SAF files (P33). Show truthful result counts.
3. Favourites: MediaStore favourite flag (API 30+) AND Far metadata (so it works on SAF assets); keep both in sync via the repository.
4. Ratings 0–5, tags (user tags are distinct from AI labels), captions/alt text, keywords: stored in Room; UI states “Saved in Far” and offers “Write to file (copy)” only with confirmation (EXIF writing changes the file — G4).
5. Archive/Hidden flags in assets_meta; every query adds `NOT hidden AND NOT archived` unless the view asks for them; hidden items are excluded from search, widgets, recents, Memories (G15). If App Lock is on, Hidden view requires auth.
6. Every batch op journaled with per-item results and Undo.

EDGE CASES: selection across filters that change; items deleted by another app during selection; consent dialog denied (show “Not moved — permission declined”); mix of MediaStore + SAF assets.
TESTS: range-select, day-select, 10k-selection survival, Undo of trash, hidden exclusion tests, rating/tag persistence, a11y tests for check state announcements.
ACCEPTANCE: each action truthfully reports counts; no photo deleted without Trash/Undo; hidden never leaks.
```

### P19 — Full-screen viewer, details sheet, compare and Peek
Depends: P15, P53, P56
```text
TASK P19 — The photo/video viewer.

WHAT IT LOOKS LIKE
- Black canvas, image centred and edge-fit. Top glass bar (hidden on single-tap): back chevron · date/time (“22 Nov 2026 · 14:03” with “IMG_2031.heic” on tap) · info (i). Bottom glass bar: **Share · Favourite (heart) · Edit · Delete**. Optional thumbnail filmstrip (off by default; enable in Settings).
- INFO SHEET (swipe up or tap i): half-height sheet with big date, caption field (“Add a caption”), alt text, tags chips, “Text found in image” (OCR; tap to copy), Camera card (device, lens, ISO, f/, shutter, focal length), File card (name, size, format, resolution, colour space, location), Map card (offline tile pack or coordinates list), Edit history, Provenance (“Captured with Far”), Histogram (power-user toggle), “Show in Files”.
- COMPARE (select 2 → More → Compare): side-by-side, synced pan/zoom, swipe a vertical divider; “Keep left / Keep right” review that moves the rejected item to Trash with Undo.
- PEEK (long-press in grid): rounded preview card with a haptic; release closes.

HOW THE USER INTERACTS: swipe sideways = previous/next (preloads ±2); double-tap zooms to the tapped point (2.5×) and back; pinch 1–8×; swipe down dismisses with the shared-element shrink (P53); single tap toggles chrome; videos show controls on tap (P20); rotate button in More (⋯) rotates view only (non-destructive) and “Save rotation as copy”.

HOW IT WORKS
1. HorizontalPager over a paged list of IDs from the same query spec as the grid (so the neighbour order matches); never copies the list.
2. ZoomableMedia (E5): Transformable state + tile/region decoding (BitmapRegionDecoder or ImageDecoder crop) for images > 4096 px; downsampled preview first, then sharpen on idle.
3. Info: EXIF via androidx.exifinterface (read-only), location redacted unless ACCESS_MEDIA_LOCATION granted; OCR text from ocr_text; Edit history from edit_stacks.
4. Delete in viewer → Trash with Undo, pager advances smoothly; Favourite toggles with heart pop and haptic.
5. Compare: two ZoomableMedia sharing one transform state; “Keep” flow batches the Trash request.
6. Peek: lightweight Popup with decoded preview, no navigation.
7. Memory: max 3 full-size bitmaps alive; release on onTrimMemory.

EDGE CASES: 100-megapixel images; animated WebP/GIF; HDR/Ultra HDR; corrupt file (ErrorState with Open with…); item deleted elsewhere while viewing; orientation change keeps zoom; TalkBack describes photo with date + caption + alt text.
TESTS: viewer instrumented tests (zoom/pan/dismiss), memory test with a 100 MP sample, compare sync test, a11y test, screenshot tests (chrome on/off, info sheet).
ACCEPTANCE: smooth hero transitions, no more than 3 full bitmaps held, all actions real, location only with permission.
```

### P20 — Video player, resume, frame capture and audio controls
Depends: P19
```text
TASK P20 — Polish video playback with Media3.

WHAT IT LOOKS LIKE
- Same viewer chrome. Tapping shows VideoControls (E5): large centre play/pause glass circle, seek bar with a time bubble while scrubbing, current/total time, **Mute · Speed (0.5×/1×/1.25×/1.5×/2×) · Fullscreen · PiP · More (⋯: Subtitles, Save frame, Loop, Rotate lock)**. Double-tap left/right half = ∓10 s with a “10s” ripple. Press-and-hold = temporary 2× with a “2×” pill. Drag along the left edge = brightness, right edge = volume (small HUD).
- Resume hint: pill “Resumed at 3:12 · Start over” for 4 s.
- “Save frame”: sheet with preview, format (JPEG/PNG), quality, destination; result toast “Saved to Photos · View”.

HOW THE USER INTERACTS: as above; leaving the video saves position; returning resumes. PiP via button or on leaving the app (where supported).

HOW IT WORKS
1. ExoPlayer from Media3, single shared player instance per viewer page; release on page change/background; audio focus handling; set `setHandleAudioBecomingNoisy(true)`.
2. playback_positions table (assetKey, positionMs, updatedAt) — skip storing for hidden items; clear on user request.
3. Frame capture via Media3 frame extractor / MediaMetadataRetriever at exact position; written as a COPY through MediaStore with IS_PENDING.
4. Subtitles: side-loaded .srt/.vtt next to the video if accessible; embedded tracks list; subtitle style follows system captioning.
5. PiP via Activity.enterPictureInPictureMode with proper params (aspect ratio) and remote actions.
6. Errors: unsupported codec → ErrorState “This phone can’t play this video. Open with another app”.

EDGE CASES: audio-only files (show waveform/cover art); rotation metadata; variable frame rate; HDR to SDR tone mapping on non-HDR displays; call interrupts; Bluetooth disconnect.
TESTS: instrumented playback tests with sample files; resume test; frame-save test (file created once, playable); PiP smoke test.
ACCEPTANCE: gestures work, resume works, no leaked players (LeakCanary), copies not overwrites.
```

### P21 — Unified search, advanced filters and natural-language parsing
Depends: P08, P15
```text
TASK P21 — One local search for everything.

WHAT IT LOOKS LIKE
- Photos › Search (or magnifier): glass SearchField (placeholder “Try: videos from last weekend”). Before typing: **Recent searches** (clearable), **Saved searches**, suggestion chips: Places · People · Types · Years · Screenshots · “Text in photos”.
- While typing: results appear progressively in a grid grouped by relevance, with a small caption under the group header describing why they matched (“Matched text in image”, “Tagged ‘beach’”, “File name”, “Camera: Pixel 8”).
- An **Understood as** chip row below the field shows what natural language became: `Videos ×` `Last weekend ×` `Karachi ×` — tap × removes a filter; tap a chip to edit its value.
- Advanced search sheet (“Filters”): date captured/added, size, duration, dimensions, EXIF (camera, lens, ISO, focal length), rating, favourite, edited, has location, album/folder, tag, caption/keyword, type, text in image.
- Index status line (“Text indexing 63%”) under the field.

HOW THE USER INTERACTS: type → results update after 300 ms debounce; tap a result → viewer (neighbours = result list); long-press → select; “Save this search” → becomes a smart collection (P16).

HOW IT WORKS
1. FTS5 virtual tables over file names, tags, captions, alt text, OCR text (P08); structured filters via indexed columns; combine in one QuerySpec; results Paging 3.
2. NaturalLanguageParser (rule-based, local, locale-aware): tokens for relative dates (today, yesterday, last weekend, “in March”, “2023”, “last summer”), media types, favourites, rating (“5 stars”), sizes (“over 10 MB”), durations (“longer than 1 minute”), places (matching local place labels/coordinates clusters), camera names, “screenshots of…”, “text containing…”. Produces `ParsedQuery(filters, freeText, confidence)`; ambiguous parts stay as free text and are shown honestly.
3. Match reasons computed per result (which index hit) and cached for the visible page.
4. Cancel previous query on new input; show partial results; time-box a query to 1.5 s then continue streaming.
5. search_history & saved_searches tables; privacy: clear history button; hidden/excluded assets never indexed or returned (G15).
6. No network, no cloud search, no Lens (G2).

EDGE CASES: RTL/Urdu/Arabic text; diacritics; empty OCR index (search still works for names/tags); typos (prefix match + simple edit distance on tags only); very common words; locale date formats; 100k assets query < budget.
TESTS: parser unit tests (≥100 phrases incl. Urdu/English dates); FTS tests; relevance fixtures; UI test for chip removal; performance test on 100k.
ACCEPTANCE: chips show exactly what was understood; every result has a reason; hidden never appears.
```

---
## PHASE 4 — ON-DEVICE INTELLIGENCE (all opt-in, offline)

### P22 — OCR, document intelligence and text actions
Depends: P08, P19
```text
TASK P22 — Text in photos: select, copy, act, export.

WHAT IT LOOKS LIKE
- In the viewer, a small “text” button (aa-in-brackets glyph) appears only if text was found. Tapping highlights recognised text blocks with a soft yellow overlay; long-press a word to select; handles extend selection; a floating glass toolbar shows **Copy · Share · Translate (if language pack installed) · Search**. Detected phone numbers, emails, links, dates and addresses are underlined: tap → action sheet (Call · Message · Add contact · Open link · Add to calendar · Copy) — every action asks before doing anything.
- “Receipts & Documents” collection: card thumbnails with extracted **merchant · date · total** captions; tapping opens a review sheet where each field is editable; low-confidence fields are marked with an “Check” dot.
- Export: “Export text (.txt)” and “Export receipts (CSV)”.

HOW THE USER INTERACTS: open photo → tap text button → select → action. In Settings › Library › Text recognition choose per-folder on/off, pause, “Delete all recognised text”.

HOW IT WORKS
1. ML Kit Text Recognition (bundled model; Latin by default; other scripts only if the bundled variant is present — never downloaded). Show “Language not available” honestly.
2. Overlay uses recognised element bounding boxes mapped through the viewer transform (rotation/zoom aware).
3. Entity extraction by local regex + libphonenumber-style rules (licence check), date parsing with locale; no network lookups.
4. Receipt heuristic: classify by keywords (total, tax, subtotal, invoice), amount patterns, date; fields with confidence; user corrections stored and used to improve rules (stored locally).
5. Opt-in per folder; never runs for hidden items/excluded folders (G15); OCR failure never prevents viewing.
6. Create contact/calendar via system intents only after user confirms (no extra permissions).

EDGE CASES: rotated/skewed text; handwriting (low confidence); mixed RTL/LTR text selection; screenshots with dark UI; very large images (OCR on downsample, boxes scaled back).
TESTS: sample images (receipt, ID-like fake, screenshot, Urdu/English text); selection mapping tests under zoom; entity extraction unit tests; opt-out deletes derived data test.
ACCEPTANCE: copy works; actions confirm before running; derived data deletable; no network.
```

### P23 — Duplicates, similar photos, bursts, blur and Swipe to Sort
Depends: P08, P10, P18
```text
TASK P23 — Reviewed clean-up tools for exact duplicates, similar photos, bursts, blurry items and screenshots.

WHAT IT LOOKS LIKE
- Collections › Utilities › **Tidy** hub: cards with live counts and reclaimable size: Exact duplicates (“3.2 GB”) · Similar photos · Bursts · Blurry · Screenshots · Old videos · Large files. Each card opens a review screen.
- DUPLICATES review: groups as rows — left: thumbnails of the group (2–6), right: size, resolution, date, folder; a green “Suggested keep” tag on one item with the reason (“Largest and oldest”). Buttons: **Keep suggested, Trash others** (shows exact count + space), or tap items to choose manually. Never auto-selected for deletion without the user tapping the action.
- SIMILAR/BURST stacks: horizontal filmstrip per stack with a star on the **Best Frame** (sharpness/exposure/faces), tap to compare; “Keep best, archive rest” or “Keep all”.
- BLURRY: grid with a blur score badge; “Review” opens Swipe to Sort.
- SWIPE TO SORT: full-screen cards: swipe right = Keep (green ✓), swipe left = Trash (red ✕, with Undo), swipe up = Favourite; progress “14 of 120”; “Done” summary with space freed.

HOW THE USER INTERACTS: choose a card → review → confirm summary sheet (“Move 48 photos (1.4 GB) to Trash?” with a scrollable thumbnail list) → Trash with Undo.

HOW IT WORKS
1. Exact duplicates: group by size, then streaming SHA-256 (chunked, resumable via HashWorker/P08, stored in assets_meta.contentHash); NO row limits.
2. Similar: perceptual hash (dHash/pHash 64-bit) + Hamming distance threshold; bucketed (LSH-style) to avoid O(n²); kept as a SEPARATE category with a visible “Looks similar” label, never called “duplicate”.
3. Bursts: burst id/filename + time proximity (<1.5 s) + similarity; Best Frame score = sharpness (variance of Laplacian on downsample) + exposure + eyes-open/smile if local face model is enabled (P24) — all frames kept.
4. Blur detection: Laplacian variance with adaptive threshold per image size; user can mark “Not blurry”.
5. Suggested keeper rules shown to the user; never auto-delete (G5).
6. All actions use the journaled Trash path (P10/P18).

EDGE CASES: identical files in different volumes; edited copies (not duplicates); screenshots of the same screen; tiny thumbnails; 100k hashing (pause on battery/thermal).
TESTS: group correctness on fixtures; 100k hashing resumable; threshold tests; Undo; no deletion without confirm (UI test).
ACCEPTANCE: counts and space are exact; reasons displayed; nothing deleted automatically.
```

### P24 — Scene/object labels, People & Pets (opt-in, local)
Depends: P08, P16 · Mode: GATED by model availability
```text
TASK P24 — Optional on-device scene/object labels and face/pet grouping.

WHAT IT LOOKS LIKE
- Settings › Library › **Smart labels**: switch “Find things in photos (food, cars, pets…)”, size/battery note (“Uses ~45 MB and some battery while indexing”), progress, “Delete smart-label data”. Separate switch: **People & Pets** (“Group faces and pets on this phone only. Never uploaded.”).
- Collections › People & Pets: round face tiles with name or “Add name”; tapping opens the person’s grid; long-press → Rename · Merge · Hide · Remove photo from person. Corrections update the grouping immediately. “Unnamed” groups are labelled “Person 1”, never guessed.
- Search suggestions include labels (“Dogs”, “Food”, “Documents”).

HOW IT WORKS
1. Bundled TFLite/ML Kit models only (licence, size, quality reviewed before inclusion; model files shipped in assets or user-imported from local storage — never downloaded).
2. MlLabelWorker (WorkManager) with device-tier gating (RAM, SoC class), thermal/battery/storage guards; batch size adaptive; CPU/NNAPI fallback; “Unavailable on this device” state when not feasible.
3. scene_labels table (assetKey, label, score, modelVersion); face_clusters stored in a separate deletable DB/file set; embeddings never leave the device; no identity matching beyond clustering; no automatic naming of real people.
4. Inferred labels are visually distinct from user tags (small “auto” sparkle icon) and editable/removable; removal teaches a local suppress list.
5. Hidden/excluded items never processed (G15).
6. Opt-out deletes all derived data (confirm dialog lists what is removed).

EDGE CASES: children/sensitive categories — no special inference, no memories of sensitive events; twins; sunglasses; low-light; model upgrade re-index.
TESTS: label accuracy smoke tests on fixtures; deletion removes all rows/files; battery/thermal gating tests; cluster merge/split tests.
ACCEPTANCE: works fully offline; clear off-switch; no model download code (grep).
```

### P25 — Places, map and location privacy
Depends: P15
```text
TASK P25 — Places grouping and an offline map.

WHAT IT LOOKS LIKE
- Collections › Places: a map card (if an offline tile pack is imported) with photo clusters as round thumbnails with counts; otherwise a clean list of places grouped by coordinates with user-editable names (“Add place name”).
- Tapping a cluster opens a grid; “Hide this place” in the context menu.
- Info sheet’s Map card shows a small map (offline) or coordinates + “Copy” / “Remove location from a copy”.
- Settings › Places: “Import offline map (MBTiles/PMTiles file)” with an explanation: “Maps work without internet using a file you choose. Without it Far shows a list.”

HOW IT WORKS
1. Read GPS from EXIF/MediaStore only if ACCESS_MEDIA_LOCATION is granted (explain before asking).
2. Cluster by geohash/grid (DBSCAN-like) with adjustable radius; names are user-provided or generic “Near 24.86, 67.00” — no online reverse-geocoding.
3. Map rendering through an open-source offline renderer (MapLibre Native/osmdroid) ONLY after licence/size review and ONLY reading local tile files; zero network access (verify no INTERNET permission, no tile URLs).
4. Batch “Edit location” and “Fix date/time” write to a COPY or to Far metadata (G4) with preview.
5. Clean Share removes location (P45). Location never appears in notifications, widgets, search suggestions.
6. Hidden/Vault-like items excluded.

EDGE CASES: (0,0) bogus GPS; photos without GPS; imported tiles missing for a region (show blank with notice); privacy: home location prompt “Hide places near home?” (optional).
TESTS: cluster unit tests; tile loader reads local MBTiles; manifest has no INTERNET (build check).
ACCEPTANCE: map works only from local data; honest “no map imported” state.
```

### P26 — Memories, Featured Photos, events, year-in-review
Depends: P21, P24 (optional), P25
```text
TASK P26 — Local, editable, privacy-safe Memories.

WHAT IT LOOKS LIKE
- A compact **Memories** carousel at the top of Collections (and optionally a slim strip under the Library header; off by default): 16:10 cards with a poster photo, title (“On this day · 2022”, “Weekend in Murree”), and subtle Ken-Burns motion. Tapping plays a full-screen story: crossfading photos/clips with optional local music, tap right/left to skip, hold to pause, ⋯ menu (Edit title, Remove photo, Hide this memory, Hide this person/place/date, Share as video).
- “Featured Photos”: a hand-picked and auto-suggested set shown in widgets (user can pin/unpin).
- **Calendar heatmap**: year view with intensity dots; tap day → grid.
- **Year in Review**: generated in December/on demand as an editable slideshow project (P27).
- Event/trip album suggestions as cards: “Create album from 14–16 Aug (86 photos)?” Accept/Ignore.

HOW IT WORKS
1. Generators (WorkManager, charging/idle): On-This-Day; trips (location jumps + multi-day); events (time+place clusters); seasonal/holiday via locale calendar; favourites/ratings boost; avoids near-duplicates; quality gating by blur score.
2. memories table (id, type, title, assetKeys, createdAt, hidden, userEdited). Users can edit and the edits stick.
3. Never includes hidden/excluded items; never infers sensitive events; no faces required (people memories only when P24 enabled).
4. Playback uses the same renderer as slideshows (P27) with Ken-Burns, pauses off-screen and under battery saver; Reduce Motion → simple crossfade.
5. Disable entirely: Settings › Library › Memories off → generators stop, data deletable.

EDGE CASES: tiny libraries; screenshots flood; memory for a deleted event; time zone shifts; user hides a date permanently.
TESTS: generator unit tests on fixtures; exclusion tests; playback Reduce-Motion test.
ACCEPTANCE: every memory is editable/hideable; zero network; no hidden items.
```

### P27 — Creation Studio: slideshow, movie, collage, animation, photo book
Depends: P28, P31, P10
```text
TASK P27 — Create things from photos, entirely on-device.

WHAT IT LOOKS LIKE
- Entry: Collections › Utilities › **Create**, selection More › “Add to Slideshow/Movie”, or a Memory’s “Edit”. Create hub: cards **Slideshow · Movie · Collage · Animation · Photo Book · Contact Sheet** with small previews.
- SLIDESHOW EDITOR: top preview (16:9) with play; below a horizontal **timeline** of clips (photo/video tiles with duration chips); bottom tabs **Theme · Music · Timing · Transitions · Text**. Themes (original): Clean, Warm, Film, Pop, Mono, Calm. Per-slide duration 1–10 s, default 3 s; transitions (Fade, Slide, Zoom, Dissolve); Ken-Burns toggle; text title/captions with font/colour; local music picker (audio files the user chooses) with fade in/out and volume.
- MOVIE EDITOR: same timeline + video trim handles, clip speed, volume, titles.
- COLLAGE: choose 2–9 photos → layout carousel (grid, mosaic, strip); adjustable gutters, corner radius, background colour; pinch/drag to reposition inside cells.
- ANIMATION: pick burst/frames → GIF or MP4 loop (speed, bounce).
- PHOTO BOOK: pages with layouts; export as print-ready PDF (A4/A5/square, 300 dpi optional).
- CONTACT SHEET: thumbnails grid PDF with captions.
- Export sheet shows format, resolution (720p/1080p/4K where supported), estimated size, destination; progress in Activity.

HOW THE USER INTERACTS: choose photos → pick a type → edit → Preview → Export → toast “Saved to Movies/Far Gallery · View”. Projects autosave and reopen after restart (Projects list in Create hub).

HOW IT WORKS
1. creation_projects table (JSON timeline model with asset refs, never copies); autosave every change.
2. Rendering: Media3 Transformer composition for video/slideshow with Effects (crop/scale/transition via OpenGL effects), audio mixing; GIF via encoder library (licence-checked); collage/photo-book via Canvas/PDF document API.
3. Preview renders at low res in the editor; export at full res in WorkManager (expedited, foreground notification with Cancel) with journal and cleanup.
4. Storage preflight and codec capability checks (P60); unsupported → clear message.
5. Outputs are new files; sources untouched (G4).

EDGE CASES: mixed aspect ratios (letterbox/blur-fill); missing source assets; audio file revoked; very long projects; app killed mid-export (resume or restart cleanly).
TESTS: timeline model unit tests; export instrumented test (plays, duration matches ±0.2 s, audio present); project reload test; low-storage test.
ACCEPTANCE: all six creation types export or are honestly marked unavailable; projects persist; originals unchanged.
```

---
## EXTRA PROMPTS — SMALL FEATURE PACKS (NEW)

### P57 — Cross-app extras: Universal tags, Search Everything, Check-up, Cover, Show an Album, Reminders
Depends: P18, P21, P47
```text
TASK P57 — Small high-value features that tie Photos, Camera and Files together.

WHAT IT LOOKS LIKE
- UNIVERSAL TAGS: tags and 7 colour labels work on photos AND files; Files › Tags section lists them with counts; tag chips appear in both Info sheets.
- SEARCH EVERYTHING: the Search screen has a scope switch **All · Photos · Files**; “All” mixes photo grid results and file rows (icon + name + path hint), grouped by type.
- CHECK-UP (Settings › Check-up): a friendly list with status dots: “Index up to date ✓”, “Storage 91% full — Make room”, “No backup in 30 days — Back up now”, “Text indexing paused”, “Some folders need access again”. Each row has one action button.
- COVER (privacy cover): quick toggle in More and a launcher shortcut; instantly blurs the screen and hides thumbnails (also blurs in the recent-apps preview via FLAG_SECURE-like setting “Hide in recents”). Tap anywhere to reveal.
- SHOW AN ALBUM: pick an album → “Show an Album” locks the app to that album (viewer only, no navigation out, no delete/share); exit by long-press on a corner + auth (PIN/biometric if App Lock set, else a simple confirm).
- REMINDERS (local notifications, OFF by default): monthly Tidy suggestion, “Trash purges in 3 days”. Notification text never contains file names.

HOW IT WORKS
1. Tags table shared by photo and file assets via assetKey (SAF/MediaStore/AP).
2. Search scope uses two repositories merged by relevance; paging per scope.
3. Check-up aggregates signals from Activity, storage stats, backup timestamp, folder grants.
4. Cover uses a Window flag + in-app overlay; “Hide in recents” sets FLAG_SECURE only while enabled (document trade-off: blocks screenshots).
5. Show an Album uses a locked task mode via an internal state (not screen-pinning APIs requiring special permission) — explain limits honestly (“Android system buttons still work; this is not a security lock”).
6. Reminders via WorkManager + notification channel with POST_NOTIFICATIONS (API 33+) requested only when the user enables reminders.

EDGE CASES: notifications denied; tags on missing assets; Show an Album + incoming intent.
TESTS: unit + UI tests for each feature; notification content privacy test.
ACCEPTANCE: every row/action real; off-by-default features stay off.
```

### P58 — Photo utility pack: Date & Location Fixer, Wallpaper Maker, ID/Passport photo, Voice captions, Before/After
Depends: P28, P18
```text
TASK P58 — Practical one-purpose tools, all producing COPIES.

WHAT IT LOOKS LIKE
- DATE & LOCATION FIXER (selection › More › “Edit date & location”): sheet with a before/after list of the first 5 items; controls: “Shift by [+/-] years/months/days/hours/min” steppers, “Set exact date/time” picker, “Set location” (coordinates input or imported-map pin), “Remove location”. Button “Apply to copies” (default) or “Write to Far only (don’t change files)”.
- WALLPAPER MAKER: pick a photo → preview on a phone-shaped frame with the real screen ratio (your phone’s, e.g., 6.78″ AMOLED), pinch/drag to position, parallax toggle, blur/dim sliders → “Save copy” or “Set as wallpaper” (WallpaperManager; no special permission needed for setting).
- ID/PASSPORT PHOTO: choose size preset (35×45 mm, 2×2 in, 51×51 mm, custom), face guide overlay, background (white/blue/custom), 300 dpi; export single or 4×6 in print sheet PDF.
- VOICE CAPTION: in Info sheet a mic button records ≤60 s (RECORD_AUDIO just-in-time) → stored as a Far sidecar audio file; a small waveform chip plays it; delete anytime. Not written into the photo.
- BEFORE/AFTER SHARE: from an edited photo’s More › “Share before & after” → split image with a vertical divider and “Before/After” labels.

HOW IT WORKS
1. Date/location: write EXIF to the COPY via ExifInterface; or store override in Far metadata flagged “virtual date” and used in sorting only when the user chose that; clearly labelled.
2. Wallpaper: render crop at device’s native resolution; WallpaperManager.setBitmap/setStream with FLAG_SYSTEM/LOCK choice.
3. ID photo: manual crop with guides (no cloud face-detect; optional local face detector to centre); mm→px at 300 dpi; PDF via PdfDocument.
4. Voice caption: MediaRecorder to app-private .m4a; row in captions_audio table; excluded from backups unless user includes them.
5. Before/After composes two bitmaps via Canvas; saved copy via MediaStore.

EDGE CASES: items without EXIF; HEIC write support (convert to JPEG copy when unsupported); microphone denied; very large photos.
TESTS: EXIF round-trip tests; PDF dimension tests; wallpaper crop math tests; permission tests.
ACCEPTANCE: originals unchanged; outputs open in other apps.
```

---
## PHASE 5 — EDITING & CREATION

### P28 — Core non-destructive photo editor
Depends: P19, P11, P12
```text
TASK P28 — Responsive, non-destructive photo editor (Apple-Photos-style).

WHAT IT LOOKS LIKE
- Full-screen black canvas with the photo. Top bar: **Cancel** (left) · Undo/Redo arrows (centre) · **Done** (right, accent). Bottom: a horizontal **tool dial** with three tabs — **Adjust · Filters · Crop** (More › Recipes, Markup, Tools). Under the tabs, a horizontally scrolling row of tool icons (Adjust: Exposure, Brilliance, Highlights, Shadows, Contrast, Brightness, Black point, Saturation, Vibrance, Warmth, Tint, Sharpness, Definition, Noise reduction, Vignette). Selecting a tool shows a **SliderRow** (centre-zero where bipolar) with the numeric value; a thin dot under tools that are modified; the tool icon draws a circular progress ring showing its strength.
- FILTERS: thumbnails of the current photo with each look (Vivid, Warm, Cool, Mono, Silvertone, Noir, Fade, Chrome, Film…) + an intensity slider.
- CROP: free/aspect presets (Original, Square, 4:3, 3:2, 16:9, 9:16, custom) with rule-of-thirds grid; a **dial** for straighten (±45°), buttons Rotate 90°, Flip H, Flip V, **Perspective** (vertical/horizontal sliders), Reset.
- Press-and-hold the photo = show the original (label “Original”). “Revert” appears in More. “Compare” = the same hold gesture. Done opens a small sheet: **Save as Copy** (default, primary) · **Replace Original** (secondary, red text, shows a warning + recovery explanation) · Cancel.

HOW THE USER INTERACTS: drag sliders (live preview), double-tap a slider to reset it, two-finger tap undo, pinch the photo to zoom in and check details. Edits auto-save as a draft recipe, so leaving and returning restores them.

HOW IT WORKS
1. Immutable EditStack (list of typed operations, versioned JSON in edit_stacks); undo/redo = pointer in the stack; copy/paste edits between photos; “Recipes” (named stacks) apply to selections as a batch.
2. Rendering: GPU pipeline (RenderScript is deprecated — use RuntimeShader/AGSL on API 33+, OpenGL ES fragment shaders or a vetted library such as GPUImage-style filters on older); preview runs on a downscaled bitmap (≈ screen size) with debounce; full-resolution render only on export, tiled if large to bound memory.
3. Colour: process in linear/working space where practical; preserve embedded profile (Display P3) when possible; HDR gain map: preserve or tone-map with a notice.
4. Output: Save Copy writes JPEG (quality 95 default)/HEIC if encoder available/PNG for transparency via MediaStore pending; copies EXIF (minus orientation fix) and records edit provenance in Far DB; Replace Original first makes a recoverable backup in app-private `originals_backup/` (journal entry; Settings › Storage shows and can restore/clean) then replaces.
5. Non-destructive display: Library shows the edited look via cached render + “Edited” badge when the edit lives only in Far (virtual edit) — setting “Always save edits as new photo files” (default ON) avoids ambiguity; virtual edits are an advanced option.
6. Memory limits: never hold >2 full-size bitmaps; large images region-decoded.

EDGE CASES: 100 MP; animated images (not editable → message); RAW (P29); portrait depth; low storage on save; process death with unsaved edits (draft restored); cancellation mid-render.
TESTS: golden-image tests for each operation; original SHA-256 unchanged after Save Copy; undo/redo property tests; draft-restore test; memory test with 50 MP.
ACCEPTANCE: live preview ≥ 30 fps on mid device at screen-size preview; originals byte-identical by default; all listed tools real.
```

### P29 — Advanced photo tools, markup, RAW and export presets
Depends: P28
```text
TASK P29 — Extra tools in the editor (More › Tools) and Clean Share/export presets.

WHAT IT LOOKS LIKE
- **Retouch**: Red-eye (tap eyes), Spot heal (brush circle, preview), Blemish. **Markup**: pen (3 weights, colour wheel + eyedropper), highlighter, text (font, size, colour, background), shapes (rectangle, ellipse, arrow, line), signature (draw once, save, reuse; stored encrypted in app-private files).
- **Portrait**: blur/depth adjustments only when depth data exists (shows “No depth data in this photo” otherwise).
- **Moments** (motion photo): choose key photo, trim, loop/bounce, “Save as video/GIF”.
- **RAW/DNG**: panel with exposure, white balance (temperature/tint), tone curve, noise reduction, lens corrections; export to JPEG/HEIC copy; RAW file untouched; recipe stored as sidecar.
- **HDR/Ultra HDR**: preserved if supported, otherwise “Tone-mapped” notice.
- **Export/Clean Share presets** (also P45): Original · Compressed copy (1080/2048 px, quality slider with size preview) · Strip location · Strip all metadata · PDF · Selected as ZIP.
- Blur/Redact (faces/plates/text) and Cutout appear **only if a verified local pipeline exists** (else hidden).

HOW IT WORKS
1. Markup uses a vector layer model (strokes/shapes/text objects stay editable until Done), flattened at export; undo/redo integrated with EditStack.
2. Retouch via local inpainting algorithm (patch-based, e.g., PatchMatch/Telea) — documented quality limits; heavier AI → P30.
3. RAW decode through platform ImageDecoder/DNG support or a licence-reviewed library; rawler-class processing offloaded to a worker with progress; thumbnails from embedded JPEG preview.
4. Export presets in one `ExportPreset` model reused by Share (P45); a preview sheet lists exactly what metadata is removed.
5. All outputs are copies (G4).

EDGE CASES: RAW without embedded preview; unsupported DNG variants (honest message); signature storage privacy; huge text objects.
TESTS: golden tests for markup; EXIF strip verification (read output — no GPS); RAW sample decode; preset size estimation accuracy test.
ACCEPTANCE: unavailable tools are hidden, not faked.
```

### P30 — Advanced on-device AI edits (GATED, OPTIONAL)
Depends: P28, P24 infrastructure · **Owner approval needed for model assets**
```text
TASK P30 — Erase (object removal), Camouflage, Unblur, Extend/Reframe, Sky/Colour Pop, Portrait Light — ONLY if real local pipelines pass quality/size/heat benchmarks.

WHAT IT LOOKS LIKE
- Editor › Tools shows these tools with a small chip “AI · needs 80 MB” and a capability state: Available / Not available on this device (reason). Tapping an unavailable tool shows a bottom sheet explaining why. **Erase**: paint a mask (brush, lasso, auto-select suggestions highlighted as outlines), “Erase” → progress ring with Cancel → before/after slider; undo available. Others follow the same pattern.

HOW IT WORKS
1. Models are BUNDLED or USER-IMPORTED from local storage (checksum verified, licence file shown); NEVER downloaded (G2).
2. Benchmark harness (debug) measures latency, RAM, heat, output quality for each model on the test devices; results recorded in docs/AI_TOOLS_BENCHMARK.md. A tool ships only if it passes thresholds (e.g., < 8 s on mid device, no thermal throttle).
3. Device tiering: SoC/RAM class + NNAPI/GPU delegate support; fallback CPU only if within time budget.
4. Run in the isolated process (P03/D4); cancel/timeout; free memory aggressively; save as copy (G4).

EDGE CASES: OOM; model corrupt (checksum fail); user imports wrong model; thermal throttling.
TESTS: harness runs; checksum tests; cancel test; no network code (grep).
ACCEPTANCE: if assets/performance aren’t ready, ship NOTHING visible and report NOT DONE with reason.
```

### P31 — Video editing, trimming, compression and audio tools
Depends: P20, P10, P60
```text
TASK P31 — Video editor with Media3.

WHAT IT LOOKS LIKE
- Opens from the video viewer’s Edit. Preview on top; **timeline** below with a thumbnail filmstrip, dual-thumb trim handles (yellow outline like iOS), a playhead, and time labels. Tool row: **Trim · Crop · Rotate · Speed · Volume/Mute · Adjust (light/colour) · Filters · Stabilise (if supported) · Export**.
- Export sheet: **Quality** (Original-ish / High / Medium / Small) with live estimated size; **Resolution** (4K/1080p/720p — only those the phone can encode), **Frame rate**, **Format** (MP4 H.264/HEVC where supported), checkbox “Remove audio”, “Strip location”. Buttons: **Save as new video** (default) · **Replace original** (red, backup first).
- Extras: Video→GIF, Motion Photo→video, Save frame as photo.

HOW IT WORKS
1. Media3 Transformer with EditedMediaItem/Effects (trim via ClippingConfiguration; crop/rotate/scale presentation effects; speed via SpeedChangeEffect; audio processors).
2. Storage preflight: estimate output (bitrate × duration) + 20%; refuse with a Storage error if insufficient; write directly to MediaStore pending URI when supported; otherwise temp in cacheDir with cleanup in `finally` and startup cleanup of stale temps (fixes defect #6).
3. Progress from Transformer’s progress holder; foreground service/notification for long exports with Cancel; journal for recovery.
4. Verify output: duration within tolerance, plays, audio track present, A/V sync check by comparing first/last PTS.
5. Stabilisation: only when a real implementation exists; otherwise hidden. No “lossless” claims when transcoding; trim-only can use passthrough when keyframe-aligned — show “Fast trim (no re-encode)” vs “Precise trim”.
6. Optional audio enhancement/noise reduction ONLY with a verified bundled model (else hidden).

EDGE CASES: HDR to SDR; VFR; rotation metadata; huge 4K60; encoder busy; app killed; cancel midway; destination removed.
TESTS: export instrumented tests with samples; passthrough vs re-encode; low-storage test; cancel cleans temp; A/V sync assertion.
ACCEPTANCE: originals untouched by default; temp files never linger; estimates within 25% of actual.
```

---
## PHASE 6 — FILES

### P32 — Files: Browse landing page and multi-format browser
Depends: P13, P06, P10
```text
TASK P32 — Files destination styled after Apple Files + Ubuntu Files (Nautilus).

WHAT IT LOOKS LIKE
- BROWSE page: large title “Browse”, SearchField, then sections: **Recents** (horizontal cards with file preview + name), **Favourites**, **Tags** (coloured dots with counts), **Locations**: *On this device* (internal storage with free-space bar “98 GB free of 256 GB”), *SD card*, *USB drive* (when present), plus quick folders: Camera, Pictures, Movies, Downloads, Documents, Far Gallery. **Trash** row at the end. No iCloud/Shared rows.
- FOLDER screen: large title = folder name with a **breadcrumb path bar** (tap a segment to jump up; long-press to copy path), top-right **Select** and More (⋯: Sort, View options, New folder, New text file, Show hidden, Folder size, Properties). View toggle **Grid / List / Columns (tablet)**. Sort by Name/Date/Size/Kind with ↑↓. Filter chips: All · Images · Videos · Audio · Documents · Archives · APKs · Other.
- LIST rows: 52dp, 40dp file-type icon or image/video thumbnail, name (Body), secondary line (“2.4 MB · 22 Nov 2026”), chevron on folders. GRID: 3–6 columns, large preview tiles with names below.
- Tablet: sidebar of Locations/Favourites/Tags + content + optional inspector pane (selected file details and actions). Ubuntu-style: path bar editable via text (tap the path icon → type a path inside granted trees).
- Empty folder: “This folder is empty”. Locked/unavailable volume: “SD card not available” with explanation.

HOW THE USER INTERACTS: tap folder → drill in with a slide; tap file → preview (P34); long-press → context menu **Open · Open with… · Share · Rename · Move · Copy · Duplicate · Compress · Info · Tag · Favourite · Delete**; swipe row left = Delete/Share quick actions; pull down refresh; drag files onto folders (large screens); Select mode mirrors Photos’ selection UI.

HOW IT WORKS
1. Locations: internal storage via MediaStore.Files/SAF tree grant for the user-chosen roots (no MANAGE_EXTERNAL_STORAGE); Downloads via MediaStore.Downloads; SD/USB via StorageManager volumes + SAF; each location row validates its grant on appear.
2. FileNode model (name, mime, size, modified, isDir, uri, assetKey?, availability); listings paged (500/page), sorted in background; folder size computed on demand with cancellable coroutine.
3. Thumbnails reuse the Photos pipeline for media; icons for other types by family colours (folder blue, image green, video purple, audio orange, PDF red, doc blue, sheet green, slides orange, text grey, code teal, archive brown, APK green, unknown grey) with distinct glyphs (not colour alone).
4. Hidden files toggle (dotfiles) — only inside granted trees; Recents from Far-opened files + MediaStore recent; Favourites/Tags in Room.
5. Properties sheet: name, path (display), size, created/modified, MIME, permissions (read/write availability), location, hash on demand.

EDGE CASES: providers returning null names; thousands of entries; name collisions case-insensitive; very long paths; revoked tree; slow USB.
TESTS: 50,000-entry listing performance; sort/filter correctness; grant revalidation; screenshot tests (grid/list/columns, tablet).
ACCEPTANCE: browsing never blocks; honest unavailable states; no broad storage permission.
```

### P33 — File operations, batch rename, smart folders, drag-and-drop
Depends: P32, P10
```text
TASK P33 — Everything you do to files, built on the journal (P10).

WHAT IT LOOKS LIKE
- **New folder / New text file**: small sheet with name field, validation (illegal characters highlighted), “Create”.
- **Rename**: sheet with the name selected (extension separate): warning chip “Changing the extension may make the file unusable” when edited.
- **Copy/Move**: “Choose destination” picker (full-screen folder browser with breadcrumb, “New folder” button, “Move here/Copy here” footer button). Progress in Activity; conflict dialog (P10).
- **Batch rename**: pattern builder (Base name, Counter, Date, Original name, Case) with a LIVE preview list (old → new) and collision warnings; “Apply”.
- **Smart Folders**: “New Smart Folder” builder (conditions: type, name contains, size, date, tag, location) saved as a sidebar item that updates live.
- **Tidy Downloads**: suggestions card “42 files older than 90 days · Review” → grouped list with checkboxes → Move to Trash or Move to folder; never automatic.
- **Compare Folders**: pick two folders → three tabs: Only in A · Only in B · Different (by size/hash), with Copy→ buttons.
- **Folder Mirror (on demand)**: one-way copy A→B with preview of actions; Run → Activity.
- **Pin to Home Screen**, **Paste as File** (clipboard text/image → file), **Open with…** (system chooser), **Properties**.
- Drag-and-drop: lift a file (haptic), drop on a folder tile/path crumb/other pane; badge shows count; on drop → move (or copy when Ctrl/Option held) via the journal.

HOW IT WORKS
1. All writes go through the Operation Engine (P10) with conflict policy, verification and Undo (move/rename/new folder).
2. Delete: files in granted trees go to a Far Trash folder (app-private index + hidden folder under the tree when allowed; otherwise a clear notice “This location can’t be recovered once deleted — Delete anyway?”); MediaStore items use system trash; Trash retention configurable (7/30/60 days, Far-owned records only; system-trash purge period stated).
3. Batch rename uses a pure function `RenamePlan(oldNames, pattern) -> List<(old,new)>` with collision resolution (“name (2)”), unit-tested.
4. Smart folder queries translate to FileQuery (SAF-friendly filters executed after listing; MediaStore for media).
5. Compare uses name+size first, then streaming hash for equal-size pairs.
6. Open With via Intent.createChooser with FileProvider/SAF URIs and proper grant flags; fallback message if no app.

EDGE CASES: moving a folder into itself; read-only provider; names too long; case-only rename on case-insensitive FS; destination full; 10,000-file moves (chunked).
TESTS: RenamePlan property tests; conflict matrix; Undo tests; folder-into-itself rejection; process-death during move.
ACCEPTANCE: every operation journaled; no false success; drag-and-drop works on tablet/emulator with mouse.
```

### P34 — File preview, text editor, document scanner, PDF tools
Depends: P32, P22
```text
TASK P34 — Quick-Look-style preview and everyday document tools.

WHAT IT LOOKS LIKE
- PREVIEW: full-screen adaptive background (blurred dominant colour), top bar (✕ Close · name · Share), bottom bar (Open with · Favourite · Tag · Info · Delete). Swipe sideways to browse files in the same folder. Supported inline: images (zoom), video/audio (player with waveform + cover art, speed, seek, subtitles), PDF (page thumbnails rail, search, text selection), TXT/Markdown (rendered MD toggle), CSV (table with sticky header), JSON (collapsible tree), source code (syntax highlighting + line numbers), HTML source view, SVG preview, fonts (sample text), APK info (package, version, size, permissions list — no install without system installer). Office docs → “Open with” unless a licensed offline renderer is approved (tell the owner).
- TEXT EDITOR (TXT/MD/code): monospace, line numbers, find/replace bar, undo/redo, word wrap toggle, “Save” (atomic) / “Save as…”. Unsaved-changes guard.
- DOCUMENT SCANNER (Files › + › Scan document, and Camera › Scan): camera view with live edge detection polygon (yellow), auto-capture when steady, manual shutter; after capture: perspective-corrected page with draggable corners, filters (Original · Auto · B&W · Grayscale · Colour), rotate; “Add page” builds a multi-page strip; **Save as PDF** (searchable via OCR text layer) / images. Reorder pages by drag.
- PDF TOOLS (PDF preview › Tools): Merge (select PDFs, drag order), Split (page range picker), Reorder/Delete pages (thumbnail grid), Rotate pages, Annotate (highlight/pen/text/stamp), Fill forms (AcroForm fields tappable), Sign (draw/saved signature). All **Save as copy**.
- HEX/INFO VIEWER: read-only hex dump + basic file info + magic-byte format guess.

HOW IT WORKS
1. PdfRenderer for rendering; PDF editing with a licence-reviewed library (e.g., PdfBox-Android/Apache PDFBox port or similar) after size/licence/maintenance review; text selection via the library’s text extraction.
2. Markdown rendering via a local library (licence-checked); syntax highlight via a local tokenizer (no network).
3. Scanner: CameraX + OpenCV-class edge detection (licence/size review; or ML Kit Document Scanner if bundled offline variant is acceptable) → perspective transform → OCR (P22) → PDF with invisible text layer.
4. Editor uses a rope/gap-buffer text model for big files; files > 5 MB open read-only with a notice.
5. Writes are atomic (temp→verify→rename) and journaled; originals preserved on “Save as copy”.

EDGE CASES: password-protected PDFs (prompt, wrong-password error); corrupt PDFs; huge CSV (stream, paginate); encoding detection (UTF-8/16, Windows-1252); RTL text rendering.
TESTS: sample files per type; PDF merge/split page-count tests; scanner perspective math tests; editor undo/atomic-save tests; OCR layer searchable test.
ACCEPTANCE: each viewer works offline; unsupported types fall back to Open with; PDF originals unchanged.
```

### P35 — Archives, compression and checksum tools
Depends: P33
```text
TASK P35 — Safe archive handling.

WHAT IT LOOKS LIKE
- Tapping an archive opens an **archive browser** (same list UI) showing entries with sizes; header card: format, entries count, packed/unpacked size. Buttons: **Extract all…**, **Extract selected**, **Test integrity**. Create: select files → More › **Compress** → sheet: format (ZIP; others only if library approved), name, password toggle (AES-256 ZIP), compression level; progress in Activity.
- **Checksum tool**: Info › “Verify hash”: choose SHA-256/SHA-1/MD5, shows computed hash with Copy; paste an expected hash → green ✓ “Match” or red ✕ “Different”.

HOW IT WORKS
1. Keep & strengthen existing safety: canonical-path check (reject `..`, absolute paths, drive letters), symlink refusal, max entries (default 100k), max total uncompressed bytes (default 8 GB or free space − margin), max compression ratio (default 200:1 per entry), max nesting depth (3), filename sanitisation.
2. Extract to a temp directory in the destination volume → verify → move into place (journaled P10); abort and clean up on any violation.
3. ZIP via java.util.zip (read/write) + a vetted library for AES encryption (licence-checked); TAR/GZIP/7z/RAR read-only only if a maintained library passes adversarial tests.
4. Wrong password → clear message; corrupt entry → skip with report (“41 of 42 extracted”).
5. Never auto-run extracted content or APKs.
6. Hashing streams in 1 MB chunks, cancellable.

EDGE CASES: zip bombs; nested archives; huge entry names; duplicate entry names; encrypted headers; extraction to low-space volume.
TESTS: adversarial corpus (zip-slip, bomb, symlink, duplicate names); AES round-trip; hash correctness vectors; cancel cleanup.
ACCEPTANCE: corpus never escapes destination or exhausts memory/disk; partial output cleaned.
```

### P36 — Storage analyzer, Tidy cleanup and Make Room
Depends: P23, P33
```text
TASK P36 — Honest storage insight and reviewed cleanup.

WHAT IT LOOKS LIKE
- **Storage dashboard** (Files › Locations › On this device › Storage, also from Check-up): top donut/bar chart by category (Photos, Videos, Audio, Documents, Apps*, Archives, Other, Free) with legend and sizes; a **Treemap** tab (tap a block to drill into the folder); **Largest files** list; **Old downloads**; **Empty folders**; **Screenshots**; **Duplicates & Similar** (links to P23). (*Apps size is shown only if available via StorageStatsManager without special permission; otherwise omitted, not guessed.)
- **Tidy** summary: “You could free about 3.2 GB” + grouped suggestions with reasons; each group opens a checklist with sizes; footer “Move 48 items (1.4 GB) to Trash”.
- **Make Room** explains: “Far never assumes a backup exists. Only items you review are removed.” Links to system **App info** for other apps’ caches (“Far can’t clear other apps’ data”).
- Pre-flight “Not enough space” sheets before big exports/recordings: shows needed vs available + options (Lower quality · Choose another place · Cancel).

HOW IT WORKS
1. Sizes from MediaStore aggregates + SAF scan (cached in Room `storage_stats`, refreshed by worker); StorageStatsManager for volume totals; no root/hidden APIs.
2. Treemap computed with squarified layout over cached folder sizes; renders with Canvas lazily.
3. Candidate generators produce `CleanupCandidate(items, reason, reclaimBytes)`; UI always shows exact list; execution through journaled Trash (P10/P18) with Undo.
4. Storage guard API `StorageGuard.require(bytes)` used by camera, export, extract, backup, model-import.

EDGE CASES: SD card removed mid-scan; counts differ from system numbers (explain “Far shows what it can access”); huge folder trees; hidden items excluded from suggestions.
TESTS: layout unit tests; guard tests; candidate reason tests; UI tests for exact counts.
ACCEPTANCE: numbers reconcile with the list shown; nothing deleted without review.
```

### P59 — Files utility pack: content search, hash verify, APK inspector, clipboard, Pin shortcuts (NEW)
Depends: P32, P34
```text
TASK P59 — Small Files power tools.

WHAT IT LOOKS LIKE
- **Content search** (Files › Search › scope “Inside files”): indexes text of TXT/MD/CSV/JSON/code and text from PDFs (opt-in per folder); results show a snippet with the matched words highlighted and the file name.
- **APK inspector**: card with icon, package, version, size, min/target SDK, requested permissions (human-readable) and signature SHA-256; “Open with installer” button (system installer; Far never installs silently).
- **Clipboard tools**: “Paste as file” (text → .txt, image → .png), “Copy path”, “Copy name”.
- **Pin to Home Screen**: dialog to add a folder/file/search shortcut (ShortcutManager pinned shortcut).
- **Recent folders**: chip row at the top of any destination picker.

HOW IT WORKS
1. ContentIndexer worker (like P08) with FTS5 table `file_text` capped at 200 KB per file; only for opted-in folders; never for hidden/excluded.
2. PackageManager.getPackageArchiveInfo for APK metadata (no install); signatures via PackageInfo flags.
3. ClipboardManager reads only on explicit user action.
4. ShortcutManagerCompat.requestPinShortcut with an intent handled by IntentRouter (P09).

EDGE CASES: large PDFs; encodings; shortcuts unsupported by launcher (show reason).
TESTS: indexer and snippet tests; APK sample parse; shortcut intent routing test.
ACCEPTANCE: opt-in default OFF; real results only.
```

---
## PHASE 7 — CAMERA (iPhone-Camera-style, honest capabilities)

### P39 — Camera core: viewfinder, controls, capture and saving
Depends: P13, P12, P53, P54, P10, P06
```text
TASK P39 — The Camera destination, designed after the iPhone Camera app, built on CameraX (+ Camera2 interop only where needed).

WHAT IT LOOKS LIKE (phone, portrait)
- Full-bleed black screen; live preview in the chosen aspect ratio (4:3 default, centred, with black letterbox bars; Full fills the screen). Status bar and tab bar hidden.
- TOP ROW (quiet, 44dp glass circles, white icons): **Flash** (auto/on/off/torch, yellow when active) · **Moments toggle (concentric circles)** (only if supported) · **Timer** (off/3/5/10 s) · **Aspect** (4:3, 16:9, 1:1, Full) · **More (chevron ⌃)** which slides up a settings drawer with: Grid, Level, Resolution, Looks, Format (JPEG/HEIC/RAW if supported), Mirror front, Volume key, Location tag, Quick video settings.
- MODE CAROUSEL (above the shutter, small caps text): **TIME-LAPSE · SLO-MO · VIDEO · PHOTO · PORTRAIT · SQUARE · PANO** … only modes the device really supports are active; others appear greyed with a tap-to-explain (“Slow motion isn’t supported by this camera”). Selected mode label is **yellow**; swipe the preview or carousel left/right to change; light haptic tick per snap.
- BOTTOM ROW: **last-capture thumbnail** (left, 52dp rounded square) · **SHUTTER** (centre, 72dp white ring + 60dp disc; red disc in video) · **Flip camera** (right, circular arrows). ZoomPill (**.5 · 1× · 2 · 3**) floats above the mode carousel, only real lens stops; pinch for continuous zoom; drag on the pill opens the Zoom Wheel (arc dial with ticks).
- FOCUS: tap → yellow square reticle (shrinks in 150 ms) + vertical sun slider for exposure compensation; long-press → “AE/AF LOCK” pill; face detection shows thin yellow boxes.
- Grid (3×3 thirds) and Level (horizon line turns yellow when level; crosshair when phone is flat) are optional overlays.
- Timer: huge centred countdown number (spoken for TalkBack) with a pulsing ring; flash/torch blinks if enabled.
- PERMISSION DENIED: black screen with a PermissionCard (“Far needs the camera to take photos” · Open Settings).
- LANDSCAPE & TABLET: controls rotate (icons rotate, layout fixed like iPhone); tablet keeps controls on the sides.

HOW THE USER INTERACTS
1. Open Camera tab → preview within ~1 s (cold start measured). 2. Tap shutter → flash overlay, shutter sound (optional) + medium haptic, thumbnail bounces. 3. Tap thumbnail → opens the new photo in the viewer (back returns to Camera). 4. Hold shutter → burst (counter bubble “12”), release to stop. 5. Volume key = shutter (foreground only, setting). 6. Swipe down on the top handle or press Back to leave.

HOW IT WORKS
1. CameraX use cases: Preview + ImageCapture (+ VideoCapture, ImageAnalysis for QR/level/histogram when needed); bind to lifecycle; single `CameraController` wrapper; recover from CameraState.Error with reason and retry.
2. Capabilities (P40) decide which lenses/modes/controls exist; never show a control the hardware lacks (G7).
3. Saving: ImageCapture OutputFileOptions → MediaStore (IS_PENDING=1) in `Pictures/Far Gallery`; on success verify size > 0, set IS_PENDING=0, write provenance (capturedByFar=true, mode, lens, recipe) to Room, schedule indexing + OCR, THEN show success and update the thumbnail. If save fails: immediate failure toast “Couldn’t save the photo. Not enough space.” with retry; never claim saved.
4. ACTION_IMAGE_CAPTURE/ACTION_VIDEO_CAPTURE support: honour EXTRA_OUTPUT, return RESULT_OK only after file committed; RESULT_CANCELED on back.
5. Settings per mode remembered (DataStore); grid/level state remembered.
6. Keep screen on only while Camera is visible; release camera in onStop; handle camera-in-use by another app (“Camera is being used by another app” + Retry).
7. Rotation uses sensor + display rotation; EXIF orientation correct; mirrored front camera optional.
8. Low-storage guard (P36): warn <500 MB before photo, <(bitrate×60 s) before video.
9. Timer implemented with coroutine + countdown UI; cancel by tapping shutter again.

EDGE CASES: camera hot (thermal warning chip); flash unavailable; multi-camera logical vs physical lens ids; permission revoked while open; process death during capture (verify partial files cleaned); device without back camera.
TESTS: instrumented capture test (file exists once; appears in Photos & Files; IS_PENDING cleared); intent contract tests; permission tests; storage-full test; rotation test; shutter-to-saved timing recorded.
ACCEPTANCE: a capture appears exactly once; no false “saved”; UI matches description; performance numbers recorded.
```

### P40 — Capability detection, honest modes and customizable controls
Depends: P39
```text
TASK P40 — Runtime capability registry; fix the Portrait/Slo-mo stubs (defect #1); customizable camera controls.

WHAT IT LOOKS LIKE
- Mode carousel items show state: Available (normal), Unavailable (greyed; tap → sheet “Why isn’t this available?”), Limited (small dot; tap → explains e.g. “Portrait uses software blur”). Quick-controls drawer has an **Edit** button: toggles to a wiggle-free reorder list with pin/hide switches per control and a “Restore defaults” button — per mode.
- Camera Info screen (Settings › Camera › Device capabilities): table of lenses (focal length, aperture, sensor size when known), zoom range, flash, OIS, RAW, max resolution, supported FPS/resolutions for video, concurrent cameras, extensions (HDR, Night, Bokeh, Face retouch, Auto), hardware level (LEGACY/LIMITED/FULL/LEVEL_3).

HOW IT WORKS
1. CapabilityRegistry reads CameraManager/CameraCharacteristics for every camera id (logical and physical), CameraX ExtensionsManager availability per extension/lens, CamcorderProfile/EncoderProfiles, StreamConfigurationMap high-speed ranges, CONTROL_*_AVAILABLE ranges, SENSOR_INFO ranges, LENS_FACING, FLASH_INFO_AVAILABLE, concurrent camera info. Cached per device build.
2. Mode gating rules: Portrait = vendor BOKEH extension OR a clearly labelled software mode with depth/segmentation model (P42) — else Unavailable; Slo-mo = a CONSTRAINED_HIGH_SPEED session with fps ≥ 120 AND verification after recording that the output metadata frame rate matches; Pano/Night/HDR via extensions or Far pipelines (P42); Macro only if a macro/close-focus lens exists; RAW only if CAPABILITIES_RAW.
3. Remove any code path that silently binds normal Photo for Portrait or 30 fps video for Slo-mo.
4. Controls layout persisted: camera_controls table (mode, controlId, order, pinned, hidden).
5. Test matrix recorded in docs/CAMERA_CAPABILITY_MATRIX.md with at least: single-lens no-flash device, multi-lens device, device with limited Camera2.

EDGE CASES: logical camera exposing physical ids inconsistently; extensions unavailable after update; front camera lacking modes; zoom ratio ranges differ by lens.
TESTS: unit tests with fake CameraCharacteristics; gating-rule tests; instrumented smoke on available hardware; verify no silent fallback (assert mode label vs output metadata).
ACCEPTANCE: every visible mode is real or clearly unavailable; stubs removed.
```

### P41 — Pro controls, RAW, histogram and Looks
Depends: P40
```text
TASK P41 — Pro camera features (only where supported).

WHAT IT LOOKS LIKE
- **Pro** appears in the mode carousel only if manual sensor control exists. Bottom of the viewfinder: a row of dials — **ISO · SS (shutter) · EV · WB · F (focus)** — tap one to reveal a horizontal ruler dial with tick marks and a live value; “A” button resets each to Auto (yellow dot shows manual state). Overlays: live **histogram** (RGB/luma), **zebra/clipping** (red stripes for blown highlights), **focus peaking** (green edges). Format chips: JPEG · RAW+JPEG · RAW (DNG).
- **Looks** (tone/warmth/grain presets): Standard · Vibrant · Warm · Cool · Rich Contrast · Mono · Film; each has two sliders (Tone, Warmth) and is stored as a capture recipe in EXIF/XMP custom tag and Far DB; the viewfinder previews the look in real time (GPU), the saved file contains it (and a “remove Look later” option when saved as RAW+recipe).
- **Bracketing**: 3/5 shots at ±EV, results grouped as a stack.
- Zoom Wheel and exposure/focus assist for fine control.

HOW IT WORKS
1. Camera2 interop (Camera2Interop.Extender) to set SENSOR_SENSITIVITY, SENSOR_EXPOSURE_TIME, LENS_FOCUS_DISTANCE, COLOR_CORRECTION modes within reported ranges; ranges come from CameraCharacteristics (never hard-coded).
2. RAW via ImageFormat.RAW_SENSOR → DngCreator with proper characteristics/metadata/orientation → MediaStore; verify decode with ImageDecoder/DNG reader.
3. Histogram/zebra/peaking via ImageAnalysis on downscaled luma at ≤15 fps with the frame governor.
4. Looks implemented as GPU shader chain applied to preview and to the saved JPEG (post-capture on a worker); EXIF notes the look.
5. Estimated file size shown per format.
6. EXIF written must reflect actual manual values; tests compare requested vs reported (CaptureResult).

EDGE CASES: devices that clamp manual values; manual control during video; RAW + HEIC combo not supported; thermal throttling dropping fps.
TESTS: CaptureResult vs UI value assertions; DNG decode test; histogram unit tests; shader golden tests.
ACCEPTANCE: Pro hidden if unsupported; DNG opens in P29 RAW editor.
```

### P42 — Computational photography and advanced still modes
Depends: P41, P60 · **Each mode is independently gated**
```text
TASK P42 — Implement only verified local pipelines, one mode at a time; each ships with a capability badge, support matrix and honest fallback.

MODES AND LOOK
- **HDR** (multi-frame merge or vendor extension): processing spinner chip “Processing…” on the thumbnail; result tagged “HDR”.
- **Night**: shutter ring shows a countdown arc (“Hold still · 3 s”) + stability bar; uses extension if available else multi-frame stack/denoise on a worker.
- **Portrait**: vendor Bokeh extension; software fallback only labelled “Software blur” using a local segmentation model; Depth/Blur slider (f/1.4–f/16) and Lighting presets (Natural/Studio/Contour) — adjustable after capture in the editor (P29) only if depth/mask saved (stored as sidecar).
- **Panorama**: guided sweep with arrow and centre line, “Slow down/Hold steady” hints, live stitch strip; output as one wide JPEG.
- **Time-lapse**: interval presets (auto/0.5 s/1 s/5 s/30 s/1 min), frame count & estimated clip length, exposure lock; encodes MP4.
- **Slo-mo**: only true high-speed (P40 rule) at 120/240 fps; editor to choose slow segment.
- **Long Exposure / Action Pan / Moments / Best Frame (Top Shot) / Burst→GIF / Group selfie / Dual camera / Astro / Macro / Lens-clean hint**: each as a separate gated sub-feature with a small mode chip or action.

HOW IT WORKS
1. Each pipeline = interface `CaptureMode { isSupported(capabilities): Support; capture(): Flow<Progress>; }` with Support = Available | Limited(reason) | Unavailable(reason).
2. Preserve ALL source frames in app-private until the composite is verified, then keep originals as a stack (user can delete); composites labelled “Composite”.
3. Heavy work in WorkManager/foreground with thermal-aware throttling; cancelable; output via MediaStore pending.
4. Quality evidence: per mode sample outputs + metrics recorded in docs/CAMERA_MODES_EVIDENCE.md; no claim of parity with Pixel/iPhone ISPs.
5. Local models only (bundled), licence checks (P24 rules).
6. Best Frame (Top Shot): 1.5 s pre/post buffer ring using ImageAnalysis + zero-shutter-lag where available; rank frames (sharpness, eyes open if face model on).

EDGE CASES: motion in Night; stitching failures (parallax) → partial pano with message; storage pressure; app backgrounded mid-process.
TESTS: sample-output tests per mode; cancel/cleanup; thermal throttling simulation; unavailable-device UI tests.
ACCEPTANCE: unsupported mode → disabled with reason; no fake mode.
```

### P43 — Video capture, stabilisation and audio
Depends: P39, P40
```text
TASK P43 — Video recording.

WHAT IT LOOKS LIKE
- VIDEO mode: shutter becomes a red disc; while recording a **red timer pill** at the top (“● 00:42”), a small white **snapshot** button (photo while recording, if supported) left of the shutter, **pause** (if safe) on the right; remaining-space estimate under the timer (“≈ 38 min left at 4K 30”). Quick video settings sheet (tap the “4K · 30” label at the top-right): Resolution (720p/1080p/4K only if supported) · FPS (24/30/60/120/240 only if real) · HDR (only if 10-bit capture confirmed) · Stabilisation (Off/Standard/Cinematic where reported) · Audio source (Auto/Built-in/Wired/BT where available) · Wind-noise reduction (if available).
- Front camera video with mirror option; **Cinematic** blur only with real depth/segmentation (label “Software”).

HOW IT WORKS
1. CameraX VideoCapture with Recorder + QualitySelector from real supported qualities; FileOutputOptions → MediaStore pending; recording via PendingRecording.start with audio enabled only if RECORD_AUDIO granted (request when first entering Video).
2. Pause/resume only when Recording.pause is supported; if the file would risk corruption, hide pause.
3. Finalize event: on Finalize verify duration > 0, size > 0, clear IS_PENDING, write provenance, index; on error show “Recording stopped — saved 00:12” if partial recoverable, else failure message.
4. Interruption recovery (call, low storage, thermal shutdown): finalise gracefully; on next launch detect orphan pending files → recover or remove (journal).
5. Battery/heat meter chip appears when device warm; auto-lower to 1080p suggestion at high thermal.
6. Never advertise HDR/ProRes/spatial/10-bit unless metadata verifies; slow-mo verified by output frame-rate (P40).
7. Video snapshot uses a still frame from the stream when stream combos allow, else disabled.

EDGE CASES: storage fills mid-recording; BT mic disconnect; app backgrounded (stop & save); 4K60 unsupported on front; very long recordings (split at FAT32 4 GB limit on SD).
TESTS: record/stop instrumented test (playable, correct duration, indexed once); low-storage mid-recording; permission revocation; interruption recovery test.
ACCEPTANCE: files always finalised or cleanly removed; settings reflect reality.
```

---
## PHASE 8 — SYSTEM INTEGRATION, SECURITY & BACKUP

### P44 — Android intents, picker, DocumentsProvider and “Make Far Your Default”
Depends: P09, P39
```text
TASK P44 — Public Android integration contracts and an honest integration screen.

WHAT IT LOOKS LIKE
- Settings › **Make Far Your Default** screen: three rows — **Camera**, **Photos & videos**, **Files** — each with a status chip (Available · Preferred · System-managed · Not supported on this device), a short explanation, and a button (“Open default apps settings” where the OS allows). A **Test** card runs five live checks with ✓/✕: open a photo, open a video, pick a photo for another app, receive a shared image, take a photo for another app — results are real.
- In-app picker (when another app asks for a photo/file): the Photos grid/Files browser with a top bar “Choose a photo” · Cancel and a bottom “Done (3)” button.

HOW IT WORKS
1. Manifest filters (explicit, tested): VIEW (image/*, video/*, audio/* as supported), SEND/SEND_MULTIPLE, PICK, GET_CONTENT, OPEN_DOCUMENT contributor via DocumentsProvider, IMAGE_CAPTURE, VIDEO_CAPTURE, STILL_IMAGE_CAMERA. Route via IntentRouter (P09). ACTION_EDIT only if safe (returns a COPY).
2. DocumentsProvider exposes ONLY folders/files the user explicitly adds under Settings › Files › “Share a folder with other apps” (read-only by default; writable opt-in).
3. Result contracts verified: clipData, FLAG_GRANT_READ/WRITE_URI_PERMISSION, EXTRA_OUTPUT, EXTRA_DURATION_LIMIT, setResult semantics, RESULT_CANCELED on cancel.
4. Do NOT request the hidden gallery role; do NOT claim default replacement; document OEM limits (lock screen, power-button, camera keys).
5. Default detection via PackageManager.resolveActivity for a test intent (reports who currently wins) — shown as information, never as a guaranteed state.

EDGE CASES: other apps passing odd MIME; multiple selection limits; provider security (path traversal in DocumentsProvider.openDocument — canonicalise).
TESTS: instrumented tests with a test host app for each intent; DocumentsProvider unit tests incl. traversal attempts.
ACCEPTANCE: contracts pass tests; the screen never lies.
```

### P45 — Sharing, Save to Far and metadata-clean export
Depends: P29, P44
```text
TASK P45 — Sharing out and in.

WHAT IT LOOKS LIKE
- Share button opens Far’s **Share sheet** (glass sheet): top shows thumbnails + a **summary** (“3 photos · 12.4 MB”); row “Include”: **Original · Compressed · Strip location · Strip all metadata** (selected chip shows a one-line preview “Removes: GPS, camera serial, date taken”); then quick actions **Copy · Save to Files · Add to Album · Export ZIP · Export PDF**; then **More apps…** (Android system share sheet). Large selections (>300) offer ZIP instead.
- “Save to Far”: Far appears in other apps’ share sheets: choose destination (Photos library / Files folder / album) then Save; toast with View.

HOW IT WORKS
1. Exports are created as temp copies via FileProvider (cache dir, auto-cleaned) or ClipData URIs for originals; grant flags per URI; bounded payload (≤300 URIs).
2. Metadata stripping via ExifInterface on the COPY; verify output has no GPS tags (test reads it back); compressed copy via the editor pipeline.
3. Save to Far (ACTION_SEND handler): copies into the chosen destination through journaled ops (P10) with conflict choices; never overwrites.
4. Honest notice in Settings: “Far has no internet access. Apps you share to may use their own.”

EDGE CASES: HEIC recipients that can’t read it (offer JPEG); sharing videos (strip location by remux where supported); cancellation cleans temps.
TESTS: EXIF strip verification; share intent tests; temp cleanup; >300 selection path.
ACCEPTANCE: source files never modified; preview text accurate.
```

### P46 — Widgets, launcher shortcuts and Quick Settings
Depends: P26, P44
```text
TASK P46 — Widgets and shortcuts.

WHAT IT LOOKS LIKE
- **Widgets** (Glance/RemoteViews, light/dark, resizable): *Photo of the day / Featured* (rounded photo with date caption), *Recent photos* (2×2 / 4×2 grid), *Favourites carousel*, *Memory*, *Camera button* (1×1 big camera glyph), *Storage* (existing stats widget refreshed). Widget config screen: choose album/source, “Show captions” switch. Widgets show a friendly placeholder when permission is missing or privacy cover is on.
- **Launcher long-press shortcuts**: Camera · Scan document · Search · Latest photo. **Quick Settings tile**: “Far Camera” (opens camera).
- Pinned shortcuts to albums/folders (from P59).

HOW IT WORKS
1. Widgets read from a small cache DB/thumbnail files in app-private storage updated by WorkManager (daily + on change); hidden/excluded/IDs content never included (G15); thumbnails sized for widget.
2. Static + dynamic shortcuts via ShortcutManager; each shortcut routes via IntentRouter.
3. TileService for Quick Settings opens the camera activity (unlocking if needed) — no lock-screen takeover claims.
4. Monochrome themed icon variants; accessibility descriptions; no file names in widget content descriptions beyond date/caption.

EDGE CASES: permissions revoked; widget updates under Doze; large libraries; theme change.
TESTS: widget rendering screenshot tests; shortcut intent tests; privacy exclusion test.
ACCEPTANCE: widgets never show hidden content; they open the right screen.
```

### P37 — App lock, hidden content and (OPTIONAL) Vault
Depends: P18, P48 · **OWNER DECISION: Vault OFF by default** (owner earlier chose to remove the private vault — build only App Lock + Hidden unless FAR_DECISIONS.md says “approved: Vault”)
```text
TASK P37 — Privacy controls. Part A (always build): App Lock + Hidden albums. Part B (ONLY if "approved: Vault" is recorded): encrypted Vault.

PART A — WHAT IT LOOKS LIKE
- Settings › Privacy: **App lock** switch → choose **Biometric + device PIN fallback** (BiometricPrompt) or Far PIN (6-digit with a numeric keypad screen: rounded keys, dot indicators, shake on error); **Auto-lock** (Immediately/1 min/5 min/15 min); **Hide previews in recent apps**; **Require auth for Hidden**.
- Lock screen: blurred app icon background, lock glyph, “Unlock Far” button or PIN pad.
- **Hidden**: items hidden from timeline/search/widgets/memories; opened via Collections › Utilities › Hidden (auth if enabled) with a clear “Hidden items still live in your normal storage; this is not encryption.” note.

PART A — HOW IT WORKS
1. BiometricPrompt (BIOMETRIC_STRONG | DEVICE_CREDENTIAL where allowed) with CryptoObject unnecessary for UI lock; PIN stored only as a salted slow hash (PBKDF2/Argon2 library or Keystore-HMAC); attempt backoff (30 s after 5 failures).
2. Lifecycle lock: lock on process start, after timeout in background, and on screen off; FLAG_SECURE optional.
3. Hidden flag in assets_meta; honesty copy as above.

PART B (OPTIONAL) — WHAT IT LOOKS LIKE / HOW
- “Vault” section: encrypted store for photos/videos/files. Move to Vault from viewer/files/camera (capture-direct option). Items are encrypted (AES-256-GCM via Android Keystore-wrapped keys, streaming chunks), thumbnails encrypted too; never in MediaStore/thumbnail caches/search/OCR/ML/widgets/notifications/recents/diagnostics. Lock timeout, wrong-attempt backoff; export/restore flows; key-loss explanation (“If you lose your device credentials, Vault items can’t be recovered”). Decoy PIN is a privacy convenience only — never claim forensic protection. Secure-delete wording: “best effort on flash storage”.
- Use reviewed libraries (androidx.security / Tink); no custom crypto.

EDGE CASES: biometric enrolment changes (invalidated key → prompt PIN); app killed while unlocked; backup/restore with locked items; accessibility of PIN pad.
TESTS: lock lifecycle tests; PIN hash tests (no plaintext in files — grep prefs/DB); hidden exclusion tests; (Vault) encryption round-trip, no plaintext temp files, leak audit.
ACCEPTANCE: Part A real and honest; Part B untouched unless approved.
```

### P38 — Import/export, local backup and restore
Depends: P04, P10, P18
```text
TASK P38 — Move data in and out, and back it up locally.

WHAT IT LOOKS LIKE
- Settings › **Backup & Export**: cards: **Back up Far data** (albums, tags, ratings, captions, edits, settings — optionally media), **Restore from backup**, **Export library data (.json)**, **Import photos from a folder/SD/USB**. Each starts a wizard sheet: choose destination/source (system picker) → options → estimate (size/time) → Start. Backup list shows date, size, “Verified ✓”.
- Restore: pick a backup → **preview** (“412 albums, 9,031 tags, 1,204 edits, 8,300 media references; 18 media files missing on this phone”) → Restore (merge or replace choice) → progress → summary.
- Encrypted backups: password field + “If you lose this password the backup can’t be opened” checkbox.

HOW IT WORKS
1. Backup package = a folder/ZIP written via journaled ops: manifest.json (version, app version, counts, checksums), db export (P04 format), settings, edit stacks, optional media copies in structure, optional AES-256-GCM encryption (PBKDF2/Argon2-derived key, random salt/nonce) using vetted libs.
2. Verify after write (re-read, check checksums) → mark Verified.
3. Restore validates manifest/version; re-links media by key/fingerprint (P06); never overwrites existing user data without a chosen policy; snapshot before restore (P04).
4. Import from folder/SD/USB: index in place (no duplication) or copy into a chosen album/folder; conflict choices (P10).
5. Sidecar export: per-asset JSON next to exports (captions/ratings/tags/edits) in a documented schema.
6. No cloud, no network, no scheduled remote sync. Local reminder optional (P57).

EDGE CASES: backup larger than free space (preflight); partial restore after kill (resume/rollback); version mismatch; wrong password (clear error, no lockout loop).
TESTS: backup→clean-install→restore equality test; corruption detection; wrong-password test; resume test.
ACCEPTANCE: restore on a clean install recovers metadata; verification real.
```

---
## PHASE 9 — OPERATIONS, POLISH & RELEASE

### P47 — Activity Centre, Undo Centre, diagnostics and Safe Mode
Depends: P10, P03
```text
TASK P47 — One place for everything the app is doing or did, plus crash recovery.

WHAT IT LOOKS LIKE
- A small **status chip** in the header of every large-title screen when anything runs (“3 tasks · 62%”), opening **Activity** (full-screen sheet with segmented control **Running · History · Undo**).
- RUNNING: ProgressCards for scanning, OCR, hashing, copy/move, exports, video renders, extraction, backup/restore — each with icon, title, determinate bar, “1,204 of 8,300 · about 4 min”, **Pause/Resume · Cancel · Retry**, a ▸ details row (current item name hidden if “Hide names” is on). Constraint switches under each (“Only while charging”).
- HISTORY: readable log (“Moved 12 photos to ‘Trips’ · 14:03”, “Exported movie · 1.2 GB”, “Deleted 48 duplicates · Undo”).
- UNDO: reversible actions (move, rename, album changes, edits, trash) with **Undo** buttons and expiry (“available for 6 days”).
- SAFE MODE banner: “Far restarted in Safe Mode after repeated problems. Your photos and files are safe.” with **Try normal mode** and **Share report** (local file export only).
- Diagnostics (Settings › About › Diagnostics): **Create report** → preview of exactly what will be saved (app version, device class, error kinds, last 20 actions without names) → Save to Files (never uploaded).

HOW IT WORKS
1. Activity reads operation_records + WorkManager infos into one list; actions call the journal/WorkManager APIs; progress values are real.
2. Crash-loop guard: store crash timestamps from the uncaught handler (P03); ≥3 crashes within 2 minutes ⇒ next start in Safe Mode (browse/view/files only; indexing, ML, widgets, animations, camera extras OFF).
3. Quarantine table: a file whose decode/processing killed the isolated process twice is skipped with a placeholder until the user retries.
4. ApplicationExitInfo (API 30+) read at launch to log the previous exit reason (local only).
5. Undo table with expiry cleanup job.

EDGE CASES: hundreds of history rows (paged); cancel during commit step; tasks created while Safe Mode; localisation of dynamic strings.
TESTS: UI tests for pause/cancel/retry; crash-loop simulation test; quarantine test; diagnostics content test (no file names, no paths).
ACCEPTANCE: every long job visible; Safe Mode entered/exited correctly; nothing uploaded.
```

### P48 — Settings, privacy dashboard and guided preferences
Depends: P11, P07, P08, P37
```text
TASK P48 — Settings that explain themselves.

WHAT IT LOOKS LIKE
- Large title “Settings” with a SearchField at the top (searches setting names). Grouped inset lists (iOS style) in eight groups: **Appearance** (theme, accent, reduce transparency/motion, grid density default, start destination) · **Library & Indexing** (scan, folders, OCR, smart labels, memories, formats) · **Camera** (default mode, grid, level, haptics, sound, volume key, location tag, controls, capabilities) · **Files & Storage** (start folder, hidden files, delete confirmation, Trash retention, cache size, Tidy) · **Privacy & Security** (App lock, hidden, cover, hide in recents, privacy dashboard) · **Backup & Export** · **Accessibility** · **About** (version, licences/attributions, Check-up, diagnostics, what’s new).
- Each toggle has one grey line explaining effect and cost (“Uses ~2% battery per 1,000 photos while charging”).
- **Privacy dashboard** card: ✓ “No internet permission” (live check), ✓ “No accounts”, ✓ “No analytics”, list of what Far stores (counts of tags, OCR text size, labels, caches with sizes) each with **Delete** buttons.

HOW IT WORKS
1. DataStore (typed) for preferences; Room for bulk Far data; each setting has an `onChange` that takes effect immediately (workers re-scheduled, caches resized, theme recomposed).
2. Settings search index built from string resources (localised).
3. “No internet permission” status computed at runtime via PackageManager requestedPermissions (true source), not hard-coded.
4. Cache controls: thumbnail disk cache 128 MB–2 GB slider; “Clear caches” shows reclaimable size.
5. Everything persisted across restart/backup (P38 settings subset).

EDGE CASES: setting changes while a worker runs; restoring settings from older versions (defaults for new keys); RTL.
TESTS: every setting persisted + effective (UI test per setting); search finds items; dashboard values accurate.
ACCEPTANCE: no dead toggles; every control wired.
```

### P49 — Accessibility, localisation and large-screen polish
Depends: all UI prompts
```text
TASK P49 — Full accessibility, localisation and form-factor review (fix what you find).

WHAT IT LOOKS LIKE
- All screens usable at 200% font (wrap, no clipping), High Contrast theme with strong borders, RTL mirrored layouts (Urdu/Arabic) with correct icon mirroring, localised dates/numbers, large touch targets, visible focus rings, captions/alt-text editor, spoken camera countdown, reduced motion/transparency honoured.
- Large screens: NavRail/sidebar, two-pane Files, 5–10 column grids, multi-window and foldable posture handling, keyboard + mouse + stylus (hover states, right-click context menus), drag-and-drop.

HOW IT WORKS
1. Run TalkBack, Switch Access and Voice Access passes on core flows (open photo → favourite → share; take photo; browse → move file; edit → save copy). Fix labels, roles, order, state descriptions, custom actions.
2. Automated: Compose accessibility checks, Accessibility Test Framework in instrumented tests, contrast unit tests; screenshot tests at 100%/200% font, LTR/RTL, Light/Dark/HC, phone/tablet.
3. Localisation: all strings in resources (no hard-coded text), plurals, date/number formatting via ICU; provide English + Urdu starter (pseudo-locale tests with long strings + RTL).
4. Keyboard shortcuts registered; focus order tested; hover/pointer icons.
5. Docs: docs/A11Y_CHECKLIST.md with results per screen.

TESTS: automated checks + manual checklist results pasted into the report.
ACCEPTANCE: no unlabeled icon buttons; no clipped text at 200%; RTL screens verified.
```

### P50 — Privacy, security and strict-offline audit
Depends: all feature prompts
```text
TASK P50 — Prove the privacy promises.

CHECKLIST (do each, report evidence)
1. Merged manifest (release + debug) has NO android.permission.INTERNET, ACCESS_NETWORK_STATE not needed; list every permission with justification and the feature that needs it.
2. Dependency audit: scan the dependency graph for networking libs, analytics/ads/crash-upload SDKs, remote config, remote fonts/models, trackers (Exodus-style list); remove or prove inert (no permission → can’t connect).
3. Network egress test: run the app in airplane mode AND with a firewall/`adb shell` network blocking; execute core flows; confirm no failures and no socket attempts (strictmode detectNetwork + tcpdump on an emulator proxy where possible).
4. No hidden/private API use (lint + manual grep for reflection to hidden classes).
5. Data-at-rest review: PIN storage, backup encryption parameters, signature storage, temp files (list and cleanup), thumbnails cache for hidden items (must be excluded), notification text, widgets, recents previews, screenshots flag.
6. Logs: verify no file names/paths/coordinates/URI tokens in release logs (grep + runtime logcat review of core flows).
7. Intent/provider security: exported components list with justification; DocumentsProvider path-traversal tests; FileProvider paths minimal.
8. Permission minimisation: remove unused permissions; confirm just-in-time prompts.
9. Secure delete wording audit (flash storage best-effort).
10. If any LAN feature was requested later, confirm it exists only in a separate flavour (not in this build).

DELIVERABLE: docs/PRIVACY_AUDIT.md with evidence and fixes; the `verifyNoInternetPermission` Gradle task passing in CI/local.
ACCEPTANCE: no INTERNET; no leaks found OR all fixed; evidence attached.
```

### P51 — Performance, endurance, fault-injection and device matrix
Depends: P05 and all feature prompts
```text
TASK P51 — Measure and fix real performance and robustness.

WHAT TO MEASURE (low, mid, high devices where available; the owner’s Infinix phone is the primary test device)
- Cold/warm start, camera-open to preview, shutter-to-thumbnail, scroll jank at 10k/50k/100k, thumbnail cache hit rate, query latency, indexing throughput + battery/thermal cost, peak memory in viewer/editor/4K trim, copy throughput, APK size.
FAULT INJECTION & ENDURANCE
- Monkey runs (≥ 500k events), corrupt-file corpus, provider failures (null/throw/hang/vanish), process death at every journal state, low storage, low RAM, revoked permission mid-task, SD/USB removal mid-copy, camera contention, codec failure, reboot during background jobs, 24-hour soak (camera + scroll + indexing) with no leak/ANR.
ADAPTIVE QUALITY GOVERNOR
- Verify the order: stop optional animations → disable blur → shrink thumbnails → pause ML/indexing; verify recovery when stable.
HOW: Macrobenchmark + Baseline Profiles, Perfetto traces, LeakCanary, StrictMode, Compose compiler metrics.
RELEASE GATES: block release if any data-loss defect is open, crash-free sessions < 99.9% in beta, a macrobenchmark regresses beyond its limit, or the corrupt corpus crashes the app.
DELIVERABLE: docs/PERF_REPORT.md with numbers and before/after for every fix.
ACCEPTANCE: budgets in docs/PERF_BUDGETS.md met or deviations listed with owner sign-off.
```

### P52 — Final integration, release gate and handover
Depends: everything
```text
TASK P52 — Final acceptance and handover. No new features.

DO
1. Run the full acceptance matrix (Part I) on real devices: Photos, Camera, Files, shared identity, editor, video, intelligence (if enabled), backup/restore, sharing, intents, permissions, accessibility, offline.
2. Walk EVERY visible control and prove it performs its action (list control → handler → test).
3. Search for TODO/FIXME/placeholder/demo/“...”/empty handlers/`!!`/deleteDatabase/hard-coded limits; fix or report.
4. Confirm all “DONE” features have code + test + device evidence; downgrade the rest honestly.
5. Produce: RELEASE_CHECKLIST.md, BUILD_INSTRUCTIONS.md, LICENSES.md + in-app attributions, PERMISSIONS_RATIONALE.md, KNOWN_LIMITATIONS.md, UNSUPPORTED_DEVICE_MATRIX.md, MIGRATION_AND_BACKUP.md, CHANGELOG.md, ROADMAP_DEFERRED.md.
6. Verify no claims of universal default replacement or camera quality parity.
7. Final signed release build (owner’s keystore handled by owner; never commit keys).
ACCEPTANCE: no P0/P1 data-loss/security issue open; evidence-backed report of what is truly done and what remains.
```

---

# PART G — FEATURE-TO-PROMPT COVERAGE MAP
| Feature / design element | Prompts |
|---|---|
| Audit, architecture, build, reliability, DB, large data | P00–P05 |
| Asset identity, scanning, background OCR, URI safety, journaled ops, all formats | P06–P10, P60 |
| Tokens, themes, glass, icons, app icon, components | P11, P12 |
| Motion, haptics/sound, state kit, gestures/keyboard | P53, P54, P55, P56 |
| 3-destination shell, onboarding, permissions | P13, P14 |
| Timeline, Years/Months/Days, scrubber, density, sort | P15 |
| Collections, Media Types, smart albums, My Captures | P16 |
| Albums, folders, nesting | P17 |
| Selection, favourites, ratings, tags, archive, hidden | P18 |
| Viewer, info sheet, compare, Peek | P19 |
| Video player, resume, frame capture | P20 |
| Search (FTS, natural language, advanced filters) | P21 |
| OCR, receipts, text actions | P22 |
| Duplicates, similar, bursts, blur, Swipe to Sort | P23 |
| Scene/object labels, People & Pets | P24 |
| Places, offline map, location privacy | P25 |
| Memories, Featured, heatmap, Year in Review | P26 |
| Slideshow, movie, collage, animation, photo book | P27 |
| Photo editor (core), advanced tools/markup/RAW/presets | P28, P29 |
| AI edits (gated) | P30 |
| Video editing/compression | P31 |
| Files Browse, folder views, filters, sort, trash | P32 |
| File ops, batch rename, smart folders, drag-drop, mirror | P33 |
| Preview, text editor, scanner, PDF tools, hex | P34 |
| Archives, ZIP/AES, checksums | P35 |
| Storage analyzer, Tidy, Make Room | P36 |
| App lock, hidden, (optional) Vault | P37 |
| Backup, restore, import/export | P38 |
| Camera core, modes/capabilities, Pro/RAW/Looks, computational, video | P39, P40, P41, P42, P43 |
| Intents, DocumentsProvider, default-app screen | P44 |
| Share, clean export, Save to Far | P45 |
| Widgets, shortcuts, tile | P46 |
| Activity, Undo, Safe Mode, diagnostics | P47 |
| Settings, privacy dashboard | P48 |
| Accessibility, localisation, tablets | P49 |
| Strict-offline/security audit | P50 |
| Performance/endurance | P51 |
| Release gate | P52 |
| Cross-app extras, photo utilities, files utilities | P57, P58, P59 |

---

# PART H — PROGRESS TRACKER (copy into FAR_PROGRESS.md; the agent updates it)
Status values: NOT STARTED · IN PROGRESS · BLOCKED · DONE · PARTIAL · SKIPPED (verified already done)
| Prompt | Task | Status | Commit / evidence | Notes |
|---|---|---|---|---|
| P00 | Repository audit and project truth | NOT STARTED | — | — |
| P01 | Architecture & migration proposal (**approval**) | NOT STARTED | — | — |
| P02 | Build & dependency stabilisation | NOT STARTED | — | — |
| P03 | Reliability foundation & error model | NOT STARTED | — | — |
| P04 | DB recovery, migrations, metadata | NOT STARTED | — | — |
| P05 | Large-library fixtures & guardrails | NOT STARTED | — | — |
| P06 | Canonical asset identity | NOT STARTED | — | — |
| P07 | Incremental scanner & folder controls | NOT STARTED | — | — |
| P08 | Background OCR/indexing | NOT STARTED | — | — |
| P09 | Incoming URI resolver | NOT STARTED | — | — |
| P10 | Atomic file ops & journal | NOT STARTED | — | — |
| P60 | Media formats (Android + iPhone) | NOT STARTED | — | — |
| P11 | Design tokens/themes/materials | NOT STARTED | — | — |
| P12 | Icons, app icon, components | NOT STARTED | — | — |
| P53 | Motion & transitions | NOT STARTED | — | — |
| P54 | Haptics & sound | NOT STARTED | — | — |
| P55 | State kit | NOT STARTED | — | — |
| P56 | Gestures, keyboard, a11y actions | NOT STARTED | — | — |
| P13 | 3-destination shell | NOT STARTED | — | — |
| P14 | Onboarding & permissions | NOT STARTED | — | — |
| P15 | Photos Library timeline | NOT STARTED | — | — |
| P16 | Collections & smart albums | NOT STARTED | — | — |
| P17 | Albums & album folders | NOT STARTED | — | — |
| P18 | Selection, ratings, tags, archive/hidden | NOT STARTED | — | — |
| P19 | Viewer, info, compare, Peek | NOT STARTED | — | — |
| P20 | Video player | NOT STARTED | — | — |
| P21 | Unified search | NOT STARTED | — | — |
| P22 | OCR & text actions | NOT STARTED | — | — |
| P23 | Duplicates/similar/blur/Swipe to Sort | NOT STARTED | — | — |
| P24 | Scene labels, People & Pets | NOT STARTED | — | — |
| P25 | Places & offline map | NOT STARTED | — | — |
| P26 | Memories | NOT STARTED | — | — |
| P28 | Core photo editor | NOT STARTED | — | — |
| P29 | Advanced photo tools/RAW/presets | NOT STARTED | — | — |
| P31 | Video editor | NOT STARTED | — | — |
| P27 | Creation Studio | NOT STARTED | — | — |
| P58 | Photo utility pack | NOT STARTED | — | — |
| P30 | AI edits (gated) | NOT STARTED | — | — |
| P32 | Files Browse & browser | NOT STARTED | — | — |
| P33 | File ops, batch rename, smart folders | NOT STARTED | — | — |
| P34 | Preview, editor, scanner, PDF | NOT STARTED | — | — |
| P35 | Archives & checksums | NOT STARTED | — | — |
| P36 | Storage & Tidy | NOT STARTED | — | — |
| P59 | Files utility pack | NOT STARTED | — | — |
| P39 | Camera core | NOT STARTED | — | — |
| P40 | Capabilities & custom controls | NOT STARTED | — | — |
| P41 | Pro/RAW/Looks | NOT STARTED | — | — |
| P43 | Video capture | NOT STARTED | — | — |
| P42 | Computational modes | NOT STARTED | — | — |
| P44 | Intents/Provider/default screen | NOT STARTED | — | — |
| P45 | Share & clean export | NOT STARTED | — | — |
| P46 | Widgets & shortcuts | NOT STARTED | — | — |
| P37 | App lock/Hidden (Vault optional) | NOT STARTED | — | — |
| P38 | Backup & restore | NOT STARTED | — | — |
| P57 | Cross-app extras | NOT STARTED | — | — |
| P47 | Activity/Undo/Safe Mode | NOT STARTED | — | — |
| P48 | Settings & privacy dashboard | NOT STARTED | — | — |
| P49 | Accessibility/l10n/large screens | NOT STARTED | — | — |
| P50 | Privacy/security audit | NOT STARTED | — | — |
| P51 | Performance & endurance | NOT STARTED | — | — |
| P52 | Final integration & release | NOT STARTED | — | — |

---

# PART I — RELEASE CHECKLIST (all items need evidence)
**Product:** one asset identity everywhere · scanning can pause/resume/rebuild · timeline, collections, albums, search, tags, ratings persist · viewer/video/edit/export work on real files · originals preserved by default · camera modes truthful · file ops survive cancel/process death · Trash/Undo/permanent-delete flows correct · backup restores on a clean install · every visible control real.
**Android:** VIEW/SEND/PICK/GET_CONTENT/capture intents tested · EXTRA_OUTPUT & URI grants correct · DocumentsProvider exposes only chosen content · integration screen honest.
**Offline & security:** no INTERNET permission in merged manifest · no analytics/ads/cloud/remote models · core flows work in airplane mode · no sensitive data in logs/notifications/widgets/recents · PIN/backup crypto reviewed · secure-delete wording accurate.
**Performance:** 100k paging/cursor tests pass · no analysis row caps · no main-thread disk/DB/hash/decode · camera launch/shutter/jank/memory/thermal measured · corrupt corpus & provider faults don’t crash · no open P0/P1.
**Design & a11y:** Light/Dark/AMOLED/High-contrast reviewed · Reduce Motion/Transparency honoured · TalkBack, 200% font, RTL, keyboard/tablet verified · 48dp targets · loading/empty/limited/unavailable/error states everywhere · animations don’t block input.

---

# PART J — OWNER DECISION LOG (copy into FAR_DECISIONS.md)
| Decision | Current default | Change needs approval? |
|---|---|---|
| App brand | **Far Gallery** | Yes (a rename to “Far” only after trademark/store checks) |
| Platform | Android 11+ (minSdk 30), native Kotlin + Compose | Yes |
| Privacy | Strict offline, no INTERNET | Yes |
| LAN/SMB/WebDAV/Wi-Fi Direct | Excluded | Yes — separate flavour |
| Original edits | Save Copy by default | Yes |
| Delete | Trash + Undo; second confirm for permanent | Yes |
| AI models | Bundled or user-imported only; no downloads | Yes |
| Broad storage permission | Not requested; MediaStore + SAF | Yes |
| Default-app claims | Intent handlers + honest status only | Yes |
| **Vault** | **OFF (owner removed it on 2026-11-04); App Lock + Hidden only** | Write “approved: Vault” to enable P37 Part B |
| Visual direction | Apple Photos / Google Photos (Photos), iPhone Camera (Camera), Apple Files + Ubuntu Files (Files), glass, light + dark | Yes |
| Neon themes | Optional, off by default | No |
| Office-file preview | Open with…, no bundled renderer | Yes (licence/size) |
| Fonts/icons | System/Inter (OFL) + one open icon family | Yes |

---

# FINAL INSTRUCTION
**Build a dependable offline media app first, make it beautiful second, make it intelligent third.** Far Gallery succeeds when Photos, Camera and Files truly share one data model, nothing the user owns is ever lost, the app tells the truth about device and Android limits, and the iOS-inspired interface stays fast, accessible and understandable on a real phone. Complete one prompt, test it on the device, commit it, review the phase — and only then continue.

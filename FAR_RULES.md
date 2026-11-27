# FAR_RULES.md

GLOBAL RULES FOR FAR GALLERY — OBEY IN EVERY TASK

```
G1  PLATFORM. Existing native Android project, Kotlin + Jetpack Compose, minSdk 30. No Flutter/React Native/web. No rewrite from scratch. Do not rename the app, package or applicationId.
G2  STRICT OFFLINE. Never declare android.permission.INTERNET. No accounts, analytics, ads, telemetry, crash upload, cloud, remote APIs, remote fonts, remote models, remote map tiles. Everything works in airplane mode. LAN/SMB/FTP/WebDAV/Wi-Fi Direct sockets are excluded unless the owner approves a separate "Local network" flavour.
G3  SOURCES OF TRUTH. MediaStore = shared photos/videos. SAF = arbitrary documents/folders. Room (far.db) = Far-owned metadata only (albums, memberships, tags, ratings, captions, edit recipes, OCR/ML indexes, journal, settings). Room never decides whether a file exists. One physical file = one asset; never copy a file just to show it elsewhere.
G4  ORIGINALS ARE SAFE. Edits/conversions/exports save a NEW copy by default. Overwrite needs a separate explicit choice, warning, confirmation and recovery path.
G5  DELETES ARE REVIEWED. Trash is immediate with an Undo bar. Permanent delete needs a second confirmation. Cleanup tools list exact items + space first. Never delete automatically.
G6  NO FAKE CONTROLS. Every visible control does what its label says. No placeholder screens, demo data, fake progress, empty onClick, TODO/FIXME, "...", omitted methods, hard-coded counts.
G7  CAPABILITY HONESTY. Detect hardware, codec, display, ML and API support at runtime. Never substitute Photo for Portrait or 30 fps for Slow-mo under a misleading label. Unsupported = disabled with a one-line reason.
G8  PERMISSIONS. Minimum, just-in-time, plain-language, partial-access aware. No MANAGE_EXTERNAL_STORAGE without owner approval.
G9  DATABASE. Every Room change = data-preserving migration + migration test + pre-migration snapshot. Never deleteDatabase as a fix.
G10 PUBLIC APIS ONLY. No root, hidden APIs, OEM-private APIs, accessibility-service tricks. Never claim the hidden system-gallery role or universal default-app replacement.
G11 RELIABILITY. Wrap every external call (ContentResolver, SAF, MediaStore, CameraX, Media3, ML Kit, Room, codecs, archive parsers) in FarResult/FarError. Batch jobs isolate each item. No disk/DB/decode on the main thread. Page large lists (Paging 3). Pass IDs, never big lists, through Intents/navigation/saved state. Atomic writes (temp → verify → rename; MediaStore IS_PENDING). No `!!`. Every long job has journal + progress + Cancel + process-death recovery.
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
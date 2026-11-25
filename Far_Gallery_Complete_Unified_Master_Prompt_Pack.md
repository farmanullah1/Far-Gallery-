# FAR GALLERY — COMPLETE UNIFIED MASTER PROMPT PACK
## Photos + Camera + Files · Android · Offline-first · iOS 27-inspired

**Purpose:** This is the single canonical instruction document for expanding the existing Far Gallery Android project. It combines the existing app overview, the earlier unified specification, the latest master plan, the agent prompt pack, and the complete requested feature list into one implementation-ready document.

**Target agents:** Freebuff with DeepSeek, Google Antigravity with Gemini (including the user's Gemini 3.8 workflow), or another repository-aware coding agent.  
**Platform:** Existing native Android app, Kotlin + Jetpack Compose.  
**Product direction:** A dependable, privacy-first Photos, Camera and Files application with an Apple iOS 27-inspired visual language, implemented with public Android APIs and original/open-licensed assets.

> **Important:** This document is intentionally written as a sequential project plan. Give it to your coding agent together with `APP_OVERVIEW.md`. Run only one numbered prompt per session. The agent must audit before coding, preserve working functionality, build and test after each step, and stop at approval gates.

---


## Source merge and precedence

This canonical pack incorporates:

- The original `APP_OVERVIEW.md` architecture, current feature inventory, technology baseline and explicitly documented technical rough edges.
- The earlier **Unified Media App Feature Expansion Prompt and Build Plan**.
- The previous **Far Gallery — Unified Photos, Camera & Files Expansion** master specification.
- The latest **Far Gallery Unified Expansion Master Prompt and Build Plan (v2)**, used as the primary consolidated product/technical specification because it explicitly supersedes the earlier draft.
- **FAR_AGENT_PROMPT_PACK.md** global rules, session starter, design/interaction expectations, report format, phase review and recovery prompts.
- The complete feature list in the current request, mapped to P00–P52 and the feature coverage index.

Where earlier drafts conflict, the latest master plan and the hard rules in Part B take precedence. The older material is merged for requirements that add detail; duplicated wording is consolidated rather than repeated. No source description is treated as proof that source code is complete.

## CONTENTS

1. How to use this master prompt pack
2. Project identity and scope
3. Global rules for every agent session
4. Session starter and required final report
5. Core product specification, full feature catalogue, architecture, design, roadmap and acceptance criteria
6. Detailed prompt library P00–P52
7. Feature-to-prompt coverage map
8. Progress tracker and release checklist
9. Recovery prompts and owner review templates

---

# PART A — HOW TO USE THIS DOCUMENT

## A1. Files to give the coding agent

Always provide:

- `APP_OVERVIEW.md` — existing app documentation and technical baseline.
- This master prompt file — product requirements, UX, architecture, constraints and execution prompts.
- The actual project repository — source code, Gradle files, manifest, Room database and migrations, assets, tests and resources.

The source code is the truth. `APP_OVERVIEW.md` is a valuable map, but it can be out of date. The agent must inspect and verify every existing feature before changing it.

## A2. One prompt at a time

Use the following sequence:

1. Create a Git commit or backup branch before starting.
2. Paste the Session Starter from Part C.
3. Paste only the next prompt (P00, then P01, and so on).
4. Read the proposed file-change list before allowing substantial changes.
5. Build and run tests after the task.
6. Test the feature manually on a real Android device where possible.
7. Review the Standard Report. Anything marked PARTIAL, NOT DONE, BROKEN or UNTESTED must be resolved or explicitly accepted before moving on.
8. Commit with a descriptive message, for example `P15 photos timeline`.
9. After a phase, run the Phase Review prompt.
10. Never tell the agent to implement P00–P52 in one giant operation. The prompt series is designed to be executed gradually.

If Freebuff/DeepSeek or Antigravity/Gemini has a project rules file, put Part B in it. If it does not, paste Part B at the beginning of every new session. Do not assume any tool-specific memory, rule-file, or long-context behavior.

## A3. What counts as complete

A feature is **DONE** only when:

- The complete user flow works on a real device or a documented test environment.
- The UI and interaction match the specification.
- State persists after restart and process death where required.
- Permission denial, partial access, cancellation, low storage and errors are handled.
- Original media and user metadata remain safe.
- Unit tests and relevant instrumented/UI tests exist and have actually been run.
- There are no fake progress bars, demo values, empty click handlers or placeholder controls.
- The agent can identify the implementation evidence by file, class and function.

A screen that looks finished but does not perform its action is not done.

## A4. Project name and branding

Keep the current project and app identity as **Far Gallery** unless the owner explicitly approves a brand change. The shorter name **Far** may be evaluated as a future branding option, but do not rename packages, application ID, app label, launcher resources, database or user-facing brand as an incidental part of feature work.

## A5. Scope hierarchy

When requirements conflict, obey this order:

1. User data safety and Android platform rules.
2. Strict-offline privacy promise.
3. Existing working project architecture.
4. Explicit feature and interaction requirements in this document.
5. Visual polish and optional advanced features.

Never weaken a higher-priority requirement to satisfy a lower-priority one.

---

# PART B — GLOBAL RULES FOR ALL CODING AGENTS

Paste the following rules into the agent's persistent project instructions if available, or at the beginning of every session.

```text
GLOBAL RULES FOR FAR GALLERY

G1 — PLATFORM. Work in the existing native Android project using Kotlin and Jetpack Compose. Keep minSdk 30 unless the owner approves a change. Do not migrate to Flutter, React Native, web, or another framework. Do not rebuild the app from scratch.

G2 — STRICT OFFLINE. The default build must not declare android.permission.INTERNET. No accounts, analytics, ads, telemetry, remote APIs, crash-report uploads, cloud backup, cloud sync, remote AI inference, remote fonts or remote model downloads. Core features must work in airplane mode. SMB, FTP, SFTP, WebDAV, LAN sockets, Wi-Fi Direct socket transfer and similar network features are excluded unless the owner separately approves a Local Network edition and its privacy/permission changes.

G3 — SOURCES OF TRUTH. MediaStore is authoritative for shared photos and videos. Storage Access Framework (SAF) is authoritative for arbitrary documents and user-selected folders. Room stores Far-owned metadata: albums, memberships, tags, ratings, captions, edit recipes, OCR/AI indexes, operation journals and settings that belong in the database. Room must not pretend a missing physical file still exists. One asset must not be copied just to display it in another app section.

G4 — ORIGINALS ARE SAFE. Photo edits, video edits, conversions, clean-share exports and transformations save a new copy by default. Overwriting an original requires a separate explicit choice, a warning, confirmation and a recoverable backup path.

G5 — DELETES ARE REVIEWED. Deletion/cleanup must identify exact items and estimated space. Use Android system trash/consent APIs where appropriate. Offer Undo for reversible actions. Permanent deletion requires a second explicit confirmation. Never delete automatically.

G6 — NO FAKE CONTROLS. Every visible control must do what its label says. No placeholder screens, demo data, mock counts, fake progress, no-op clicks, TODO stubs, ellipses or omitted methods in final code. If a feature is not implemented or supported, do not present it as working. Hide it or display an honest disabled state with a reason.

G7 — CAPABILITY HONESTY. Detect camera hardware, codec, display, ML model and runtime support on the actual device. Never silently substitute standard Photo for Portrait, 30fps Video for Slow Motion, or another behavior under a misleading feature label. Show a useful reason when unavailable.

G8 — PERMISSIONS. Ask for the minimum permission, at the moment it is required, with a plain-language explanation. Support partial photo/video access, denial, revocation and SAF grants. Do not request MANAGE_EXTERNAL_STORAGE or other broad permissions without an approved necessity, policy review and documented user benefit.

G9 — DATABASE. Every Room schema change requires a data-preserving migration, migration test and recovery strategy. Never call deleteDatabase as a routine migration or corruption fix. Back up/snapshot Far-owned metadata before risky migration work.

G10 — PUBLIC APIS ONLY. Use public Android APIs and supported libraries. No root, hidden/private APIs, OEM-private APIs, Shizuku dependency, accessibility-service tricks or role spoofing. Do not claim a normal downloadable app can take the hidden system-gallery role or universally replace all OEM defaults.

G11 — RELIABILITY. Wrap external calls (ContentResolver, SAF, MediaStore, CameraX, Media3, ML, database, codec, archive parser) so failures return typed FarResult/FarError values rather than escaping. In batch work isolate each item so one failure does not stop the rest. No disk, database, hashing, decoding or long processing on the main thread. Page large lists. Pass IDs, not huge objects, through navigation or intents. Write safely and atomically. Every long operation must have progress, cancellation, journal/recovery and a process-death strategy.

G12 — UI AND ACCESSIBILITY. Use the shared design tokens, FarIcons and motion tokens. Every icon-only action has a meaningful content description including state. Touch targets are at least 48dp. Support System, Light, Dark, AMOLED and High Contrast; font scale up to 200%; TalkBack; RTL; Reduce Motion; Reduce Transparency; keyboard access; and appropriate focus order. Every screen has loading, empty, limited-permission, unavailable and error states as applicable.

G13 — METHOD. Before editing, list exact files/classes to inspect and change, plus why. Work in small steps. Do not modify unrelated code. Build and run relevant tests after each step. Keep dependency versions centralised. Do not upgrade a dependency until compatibility, stability, maintenance, licence and size are assessed.

G14 — HONEST REPORTING. End every task with the Standard Report in Part D. Say exactly what is DONE, PARTIAL, NOT DONE, UNTESTED or BROKEN. Never claim a build, test, device check or performance metric passed unless it was actually run.

G15 — PRIVACY BOUNDARIES. Vault content, hidden content and excluded folders must not leak into normal timelines, global search, OCR, AI inference, memories, widgets, notifications, thumbnails, recent-task previews, share suggestions or diagnostics. Exceptions require a specific user action and authentication.

G16 — NO UNAPPROVED PRODUCT CHANGES. Do not rename the app, change package/application ID, change the privacy contract, add network functionality, replace the database, add a model download mechanism or introduce a major dependency without owner approval.

G17 — FINISHED CODE. Do not leave TODO/FIXME, fake values, empty onClick, !!, dead buttons, hard-coded result counts or pseudocode in files described as implemented. If a requirement cannot be safely completed, keep it out of the active UI and report the limitation.

G18 — TEST THE FAILURE PATH. Test cancellation, force-stop, process death, low storage, permission revocation, volume removal, malformed files, provider errors, camera contention and unsupported hardware. A happy-path demo is not sufficient.
```

---

# PART C — SESSION STARTER

Paste this at the start of every separate coding-agent session, then paste only the relevant Pxx task.

```text
You are working on my existing Far Gallery Android app. Use the current repository; do not create a sample project or rewrite the app.

Read these documents in full before starting:
1. APP_OVERVIEW.md
2. FAR_GALLERY_COMPLETE_MASTER_PROMPT_PACK.md (this document), especially Global Rules, the relevant feature specification, architecture, acceptance tests and the prompt being executed.
3. The actual source files, tests and Gradle/manifest configuration related to the task.

Follow Global Rules G1–G18 exactly. The existing source code is the truth; documentation must be verified.

Work only on the task I paste after this starter. Before modifying anything:
- list exact files/classes you will inspect;
- list exact files/classes you propose to change or create;
- explain why each change is necessary;
- list tests and manual device checks.

Implement in small steps. Build and run tests after each step. Do not start the next prompt automatically. Do not invent success, progress or unsupported hardware behavior. Preserve existing features and user data. Stop for explicit approval when the prompt requires it.

Finish with the Standard Report in Part D.
```

---

# PART D — REQUIRED REPORTS AND RECOVERY PROMPTS

## D1. Standard Report — required after every prompt

```text
STANDARD REPORT

1. RESULT
   - List every requested feature and mark DONE, PARTIAL, NOT DONE, UNTESTED or BROKEN.

2. USER EXPERIENCE
   - Exact steps a user follows.
   - What the screen looks like.
   - What happens after success, cancellation and failure.

3. FILES
   - Full paths of created, modified and deleted files.
   - Why each was changed.

4. BUILD AND TESTS
   - Build command and actual result.
   - Unit, instrumented, UI, migration, screenshot and performance tests added.
   - Which tests were actually run and their results.
   - Tests not run and why.

5. RELIABILITY
   - Permission denial/revocation, process death, low storage, corrupt input, provider failure and partial batch behavior.

6. DATA SAFETY
   - How originals, metadata, albums, tags, edits and operation journals are preserved.
   - Database migration details, if any.

7. PERFORMANCE
   - Main-thread work avoided, memory/cache impact, paging, background scheduling, battery and thermal impact.
   - Include measurements if actually collected.

8. PRIVACY AND PERMISSIONS
   - Manifest changes and runtime permissions.
   - Confirm whether the strict-offline rule remains intact.

9. LIMITATIONS
   - Unsupported device/API cases, missing models, incomplete work, risks and known bugs.

10. NEXT STEP
   - Name the next prompt in the sequence, but do not execute it.
   - Ask only questions that block safe continuation.
```

## D2. Phase Review prompt

```text
PHASE REVIEW — DO NOT ADD NEW FEATURES.

Review the work completed in this phase against Global Rules G1–G18 and the feature acceptance criteria.

1. Run the project build, unit tests, lint and relevant instrumented/UI tests.
2. Search the changed code for TODO, FIXME, "..." placeholders, empty click handlers, !!, deleteDatabase, hard-coded query limits, main-thread disk/database/decoding work, unsafe direct paths and large objects in Intents/navigation.
3. List every visible control and state what real operation it performs.
4. List every feature marked DONE that has no test or device evidence.
5. Verify migrations, permission changes, privacy behavior and file-operation recovery.
6. Run large-library and fault-injection tests applicable to this phase.
7. Fix only critical regressions (data loss, privacy/security, crashes, fake controls, broken core navigation) before reporting.
8. Provide an ordered list of remaining issues and the Standard Report.

Do not claim the phase is complete until its exit criteria pass.
```

## D3. Recovery and helper prompts

**Finish partial work**

```text
Your last Standard Report contains PARTIAL, NOT DONE, UNTESTED, stubbed or broken items. Do not add new features. List every unfinished item, inspect the relevant code, finish only those items, build and test each, and update the Standard Report. Do not touch unrelated modules.
```

**Build failure**

```text
The build or tests are failing. Do not add features. Read the exact error output, find the root cause and identify the smallest safe fix. Do not guess or hide the failure. Make the fix, rebuild, rerun relevant tests and explain the result.
```

**Agent changed unrelated code**

```text
Stop. The change went outside the agreed task. I reverted to the last known-good Git commit. Re-read the prompt and Global Rules. Propose an exact minimal file list and change only those files. Do not continue until the scope is clear.
```

**Split a task that is too large**

```text
This task is too large for one session. Split it into 4–8 independently buildable and testable substeps. For each, list exact files, dependencies, tests and completion criteria. Do only substep 1 now and finish with the Standard Report.
```

**Verify a claim**

```text
Verify each claim against the actual repository. For each claim, show file path, class/function and test evidence, then mark TRUE, FALSE or UNKNOWN. Do not infer from comments or APP_OVERVIEW.md alone. Claims: [paste claims].
```

**Real-device test failures**

```text
Here are my manual device test results: [paste results]. Reproduce the issue if possible, identify its root cause, fix only the relevant code, add a regression test, build and rerun tests. Do not change unrelated features. Explain any device/API limitation.
```

---

# PART E — CONSOLIDATED PRODUCT SPECIFICATION AND BUILD ROADMAP

The complete product specification below is based on the latest unified master plan and includes the full feature catalogue, UX behaviors, technical architecture, known issues, risk controls, roadmap and acceptance tests. It is the shared reference for prompts P00–P52.

### 1.1 Rules for the agent (paste into the project rules file if your tool has one, otherwise paste into every session)

```text
GLOBAL RULES FOR THE FAR PROJECT. Follow these in every task. If a task seems to conflict with a rule, stop and ask.

G1  PLATFORM. Native Android: Kotlin and Jetpack Compose, the existing project. Do not switch frameworks. Do not rewrite working modules. minSdk 30.
G2  STRICT OFFLINE. Never add android.permission.INTERNET. No analytics, crash upload, ads, cloud, accounts, remote APIs or remote model downloads. Everything must work in airplane mode. Network-based features (SMB, WebDAV, Wi-Fi Direct, LAN transfer) are NOT built unless the owner approves a separate "Local network" build.
G3  SOURCES OF TRUTH. MediaStore is authoritative for shared photos and videos. Storage Access Framework (SAF) is authoritative for arbitrary files. Room (far.db) stores only Far's own data (albums, tags, ratings, edit recipes, OCR text, scores, projects, journal) and never decides whether a file exists. One asset = one physical file. Never copy a file just to show it in another section.
G4  ORIGINALS ARE SAFE. Edits and conversions save a copy by default. Overwriting an original needs an explicit choice, a confirmation, and a recovery path.
G5  DELETES ARE REVIEWED. Moving to Trash is immediate with an Undo bar. Permanent delete needs a second confirmation. Cleanup tools show exact items and space first. Never delete automatically.
G6  NO FAKE CONTROLS. Every visible button does what its label says. No placeholder screens, demo data, fake progress, empty click handlers, TODO comments, "..." or omitted methods in final code. If a feature cannot be finished in this task, leave it out of the visible UI and say so in the report.
G7  CAPABILITY HONESTY. Detect hardware and model support at runtime. If a feature is unsupported, show it disabled with a one-line reason, or hide it. Never silently fall back to a different behavior under the same label.
G8  PERMISSIONS. Ask for the minimum permission, at the moment it is needed, with a plain explanation, and support partial access and denial.
G9  DATABASE. Every Room schema change has a data-preserving migration and a migration test. Never call deleteDatabase as a fix. Take a snapshot before migrating.
G10 PUBLIC APIS ONLY. No root, hidden or private APIs, accessibility-service tricks, or OEM-only interfaces. Do not claim the system gallery role or a universal default-app replacement.
G11 RELIABILITY. Wrap every external call (ContentResolver, SAF, MediaCodec, CameraX, ML Kit, database) so it returns FarResult or FarError instead of throwing. In batch work, handle each item separately so one failure never stops the rest. No disk, database or decoding work on the main thread. Page all large lists (Paging 3). Never pass large lists in Intents, saved state or navigation arguments; pass IDs and keep the data in a ViewModel or database. Write files atomically (temporary file, verify, then rename; MediaStore pending flag for new media). Never use !! . Every long job has a journal entry, progress, Cancel, and recovery after process death.
G12 UI RULES. Use only the design tokens, FarIcons icons, and motion tokens from the Design Reference. Every icon-only button has a content description with its state. Touch targets are at least 48dp. Support light, dark, AMOLED, high contrast, font scale to 200 percent, right-to-left, Reduce Motion and Reduce Transparency. Every screen has loading, empty, limited-permission and error states.
G13 METHOD. Before changing code, list the exact files you will change and why. Make small steps. Do not edit unrelated files. After each step, build and run the relevant tests. Fix failures before continuing. Keep dependency versions in one place. Do not upgrade a dependency without checking compatibility, licence and size, and saying so in the report.
G14 HONESTY IN REPORTS. End every task with the Standard Report. Say plainly what is done, partial, stubbed, untested or broken. Never claim a test passed if you did not run it.
```

### 1.2 Session Starter (paste at the start of every session, before the prompt)

```text
You are working on the Far Android app (native Kotlin and Jetpack Compose). Read these first: APP_OVERVIEW.md, FAR_AGENT_PROMPT_PACK.md sections 1 to 4, and the existing source code for the area you will change.

Follow the Global Rules G1 to G14 exactly. Work only on the task below. Before writing code, tell me which files you will read and change. Build after each step. When finished, give the Standard Report from section 4.1. If something is unclear, ask one short question instead of guessing.

TASK:
```

---

## 4. Report format, review prompts and recovery prompts

### 4.1 Standard Report (the agent must end every task with this)

```text
STANDARD REPORT
1. What I built (one line per feature, each marked DONE, PARTIAL or NOT DONE).
2. Files created and files changed (full paths).
3. How to try it (exact steps on a device).
4. Tests: which I wrote, which I ran, results. List anything I could not run.
5. Error handling and reliability: what failures are handled and how; what happens on process death, low storage and revoked permission.
6. Performance notes: thread use, memory, anything that may lag on 100,000 items.
7. Permissions or manifest changes (or "none").
8. Database changes and migrations (or "none").
9. Known limitations, stubs, risks and bugs.
10. Questions for the owner.
```

### 4.2 Phase Review prompt (use after each phase)

```text
PHASE REVIEW. Do not add features. Review the work done so far in this phase against the Global Rules G1 to G14 and the Design Reference. 
1. Search the code for: TODO, FIXME, "...", empty onClick handlers, !!, deleteDatabase, hard-coded query limits, main-thread disk or database work, large lists passed in Intents or navigation arguments, hard-coded colours or icons outside the design system, missing contentDescription on icon buttons, and any INTERNET permission.
2. Run the build, unit tests and lint. Report failures.
3. List every visible control and confirm each one is wired to a real action.
4. List features marked "done" without a test.
5. Give me a list of fixes in priority order. Fix only the critical ones (data loss, crashes, security, fake controls), then report.
End with the Standard Report.
```

### 4.3 Recovery and helper prompts

**Fix list prompt (when a report shows partial or stubbed items):**

```text
Your report says some items are PARTIAL, NOT DONE, stubbed or untested. Finish them now, following the Global Rules. Do not touch unrelated code. List the items you will finish, do them one at a time, build and test after each, and give a new Standard Report.
```

**Build broke prompt:**

```text
The build or tests are failing. Do not add features. Read the exact error output. Find the root cause (do not guess). Make the smallest fix. Explain what was wrong and what you changed. Rebuild and re-run the tests. If you cannot fix it in three attempts, stop and tell me exactly what you tried.
```

**Agent went off track prompt (revert first with git):**

```text
Stop. You changed things outside the task. I reverted to the last commit. Re-read the task and the Global Rules. This time: list the exact files you will change, change only those, and tell me before touching anything else.
```

**Split Prompt helper (use when a prompt is too big for the model):**

```text
This task is too large to do in one go. Split it into 4 to 8 small steps. Each step must be independently buildable and testable. List the steps with the files each will touch. Then do ONLY step 1, give the Standard Report, and wait for me to say "next".
```

**Verify claims prompt:**

```text
Before continuing, verify these claims against the real code and show evidence (file path, class, function). Do not assume: [list]. Mark each as TRUE, FALSE or UNKNOWN.
```

**Device test prompt (run by you, then paste results):**

```text
Here are my manual test results on a real device. Fix any failures without changing unrelated behavior, and re-run the tests. Results: [paste]
```

## Core product specification and phased roadmap (consolidated from the latest master plan)

This version replaces my first draft. It is built on your real project (native Android, Kotlin, Jetpack Compose) and merges in the strongest parts of the ChatGPT plan you shared, with corrections and additions of my own.

## 1. What changed from my first draft

I wrote the first draft from the pasted feature spec and did not open `APP_OVERVIEW.md`, which describes Far Gallery, an existing Android app. That led to wrong advice. The ChatGPT plan caught most of it.

| First draft said | Correct position |
| --- | --- |
| Choose Flutter, Compose, or React Native | Stay on native Kotlin + Compose. Do not migrate or rewrite. |
| Set the app as default Camera, Gallery and Files via a role | Android has no public role that lets a normal app replace all three. `ROLE_SYSTEM_GALLERY` is system-app only. Use intent handlers and be honest about OEM limits (section 3). |
| A LAN backup or collect feature is fine with no-network | It is not. Sockets need `INTERNET`, even on a private network. The strict build stays network-free; LAN is a separate, approved variant. |
| Add SMB/WebDAV browsing | Out of the strict-offline build. |
| Flutter-style design system from scratch | Evolve the existing glass design system toward an iOS 27 look. |
| Generic architecture | Use the real stack: MediaStore + SAF + Room + Paging 3 + CameraX + Media3 + ML Kit. |
| Known bugs not mentioned | Seven recorded defects must be fixed before new features (section 5). |

## 2. Non-negotiable product rules

1. **Platform:** Android 11+ (minSdk 30), native Kotlin + Jetpack Compose. Keep the current architecture.
2. **Strict Offline is the default build.** No `android.permission.INTERNET`, no accounts, no telemetry, no ads, no cloud, no remote model downloads. Everything works in airplane mode.
3. **One asset, one file.** A Far Gallery capture is written once to MediaStore and shows in Photos and in Files (`Pictures/Far Gallery`) from the same record.
4. **Originals are never modified** unless the user chooses Overwrite and confirms. Default is Save Copy.
5. **No deletion without review.** Show exact items and space, use system Trash where possible, offer undo, and require a second confirmation for permanent deletion.
6. **No fake controls.** Every visible button does its stated job. No placeholder screens, demo counts, or fake progress.
7. **No silent capability claims.** A feature appears as supported only after it is detected and works on that device. Otherwise show "Unavailable on this device" with the reason.
8. **Least-privilege permissions,** requested just in time, with partial access supported.
9. **Data-preserving migrations.** Every Room schema change has a tested migration. Never delete the database to recover from a normal migration problem.
10. **Public Android APIs only.** No root, hidden roles, private APIs, or accessibility abuse.
11. **No honest-sounding overclaims.** Secure delete on flash storage is best-effort; say so.
12. **Audit before building.** Compare the code to the docs first. The overview is documentation, not proof.

## 3. Replacing the default apps: what Android allows

Build an honest **Default App and System Integration** screen with three separate rows. Never show a green "Default" badge unless the app can verify it on this device.

| Goal | What Far Gallery can do | What not to promise |
| --- | --- | --- |
| Be the camera other apps open | Handle `ACTION_IMAGE_CAPTURE` and `ACTION_VIDEO_CAPTURE`, honour `EXTRA_OUTPUT`, return results correctly | Replacing the OEM camera everywhere, lock-screen camera, double-press-power, or hardware camera keys |
| Be the gallery or viewer | Handle `ACTION_VIEW` for images and videos, `ACTION_PICK` / `ACTION_GET_CONTENT`, and offer an in-app picker | Taking the hidden system gallery role, or replacing the system Photo Picker everywhere |
| Be the file manager | Full in-app browser, share and pick intents, a `DocumentsProvider` for content the user chooses to expose | A universal "default file manager" setting; `ACTION_OPEN_DOCUMENT` stays system-managed |
| Be the first choice | Correct intent filters, resolver tests, guidance to the OEM "Open by default" screens | Asking users to disable or uninstall OEM apps |

Status labels to use: Available, Set as preferred where supported, System-managed, Not supported on this device. Include a built-in test that opens a photo, opens a video, picks a photo for another app, receives a shared image, and takes a photo for another app.

## 4. Offline promise and the local-network conflict

Your overview promises no `INTERNET` permission. SMB, FTP, SFTP, WebDAV, LAN backup and peer-to-peer Wi-Fi transfer all conflict with that promise. Decision for this plan:

- **Strict Offline build (default):** no sockets, no network permission. Transfers use the system share sheet, USB OTG, SD card, and SAF.
- **Optional "Local network" edition (separate flavor, only with your approval):** declares the needed permissions, labels itself clearly, limits itself to private addresses, and passes automated egress tests. On Android 17 and later, check the current local-network runtime permission before building it.
- Bluetooth and Wi-Fi Direct also need their own permissions. Do not describe them as permission-free.

## 5. Baseline: what exists and what is broken

The overview already documents a lot. Treat each item as "documented" until the audit finds code and tests for it.

**Already documented:** MediaStore timeline with Paging 3, Years/Months/Days views, 2 to 6 column pinch zoom, fast scrollbar, albums, favourites, trash, duplicates, storage analyzer, search with OCR text, viewer with filmstrip, EXIF sheet and histogram, compare studio, non-destructive editor with presets and recipes, video player with PiP, frame grab and trim, CameraX viewfinder with grid, level, EV, burst and QR scan, SAF file explorer with PDF and text preview and safe ZIP, a stats widget and a theme tile.

**Fix these first (all recorded in your overview):**

| # | Problem | Required fix |
| --- | --- | --- |
| 1 | Portrait and Slo-mo are stubs that fall back to normal capture | Use real Camera2/CameraX extensions and high-speed sessions, or show the mode as unavailable. Verify actual output frame rate. |
| 2 | Duplicate, analysis and storage queries are capped at 2,000 or 10,000 rows | Cursor-based, chunked, resumable scans. No totals from truncated data. |
| 3 | OCR runs only in the foreground, 40 items at a time | WorkManager indexing with charging, idle and thermal constraints, pause/resume and progress. |
| 4 | Incoming URI resolver accepts only `content://` and `file://` | Support provider-backed URIs safely; reject unsafe input with a clear error. |
| 5 | Corruption handler calls `deleteDatabase("far.db")` | Backup-first recovery: export salvageable albums, tags and recipes, validate, migrate, restore, and report what was lost. |
| 6 | Video trim writes large temp files to `cacheDir` | Free-space preflight, direct-to-MediaStore output where Media3 allows, cleanup in `finally`, clear no-space error. |
| 7 | `renameDocument()` can return null on some providers | Provider-aware handling; copy-verify-delete fallback only when safe; never report success on null. |
| 8 | Alpha dependencies (Room 2.7.0-alpha08, CameraX 1.5.0-alpha01) | Review current stable versions, compatibility, licence and size before changing anything. |

The overview's own description of the database says it "backs up salvageable entries", but its code sample deletes the file. Have the agent check which is true in the repository.

## 6. Features to add

Your feature list is already very large, so this section covers what is **new or changed** after merging the iOS 27 reports, Google Photos, Pixel Camera and Files by Google, and the ChatGPT plan. Rule for every item: build it only if it runs locally, and show a capability badge plus a fallback where hardware or a model is required.

### 6.1 Navigation and shell

- **Three destinations:** Photos (default), Camera, Files. Today the shell has four tabs (Photos, Albums, Search, Files) plus a camera button. Move Albums and Search inside Photos as secondary navigation: Library, Collections, Albums, Search.
- On tablets and foldables use a rail or sidebar and a two-pane Files layout with an inspector pane.
- The Camera is a prominent central action that opens full screen and hides the tab bar while capturing.
- **Operation Centre:** one screen for indexing, OCR, duplicate scans, file transfers, video exports and restores, each with pause, cancel and retry.
- **Undo Centre:** history of reversible actions (move, rename, album changes, edits, trash) where the platform allows.

### 6.2 Photos

From Apple iOS 27 (as reported by the press; verify against Apple's documentation before copying behavior):

- **Captured by Me:** only media shot by Far Gallery's Camera. Store provenance at capture time; do not infer it from folder names.
- **Identity Documents:** passports, licences and cards, opt-in, locked by default, stored in the encrypted Vault, excludable from OCR and AI.
- **Utilities and Collections tab:** Media Types, People and Pets, Places, Memories, Utilities, with sections you can reorder, pin, collapse and hide.
- **Photo ratings:** five stars plus Unrated, with filter and sort.
- **Slideshow creation and customization:** themes, per-slide time, transitions, local music, saved projects, video export.
- **Selection Review:** one list of everything selected across pages, filters and scrolling, with remove-from-selection.
- **Save Video Frame as Photo** (the overview already has a frame grabber; extend it with format and destination choice).
- **Improved natural-language search** that shows the filters it extracted so the user can correct them.
- **Faster library performance** as a measured goal (section 10).
- **AI editing tools (advanced, optional):** Clean Up, Extend, Reframe. These need bundled local models. Gate them behind a device tier, size, thermal and quality review before exposing them.

From Google Photos (local equivalents only):

- Photo stacks for bursts and near-duplicates with a suggested best shot. Never delete the others automatically.
- Locked Folder with per-item "Move to Vault" in the viewer.
- Magic Eraser, Camouflage, Photo Unblur, Portrait Light, Color Pop, HDR effect and Sky edits, each shipped only when a real local pipeline exists.
- Collage, Animation, Movie and Photo Book creations (PDF export for print, locally).
- Local Free Up Space that removes local copies only after review. No cloud dependency, so it never needs a "backed up" check.

From the ChatGPT plan, kept:

- Sort by **Date Captured** or **Date Added**, with the choice visible.
- Search results explain why each item matched (filename, OCR, tag, metadata, scene label).
- Keywords and captions stored in Room, and the UI says plainly when metadata is not written into the original file.
- "Clean Share": a copy with location and chosen metadata stripped. The overview already has this; add presets: Original, Compressed copy, Strip location, Strip all EXIF, PDF, Selected files as ZIP.
- Duplicate and blur review shows exact versus near-duplicate, size, resolution and the reason for each suggestion.

### 6.3 Camera

Build order matters: make the basics dependable before any computational mode.

- **Core:** fast launch, lens switching for every lens the device exposes, honest zoom markers (optical, sensor crop, digital), tap focus, exposure drag, AE/AF lock, flash and torch, timer (3, 5, 10 s), burst, volume-key shutter in the foreground, aspect ratios, resolution and frame-rate lists read from real capabilities.
- **Customizable controls (reported in iOS 27):** let users pin, reorder, hide and restore quick controls per mode.
- **Pro controls** (only when the device reports support): ISO, shutter time, focus distance, white balance, RAW/DNG, histogram, clipping warning, focus peaking, bracketing, reset to auto.
- **Photographic Styles:** tone and warmth looks stored as capture recipes.
- **Lens mode (the Siri/Lens-style mode):** local QR and barcode, OCR with copy, contact and date extraction, document edge detection, and object, plant or food labels only if a bundled model is installed. No web lookup, no live business data, no cloud.
- **Modes, each shown only if real:** Photo, Video, Portrait (vendor bokeh extension or a clearly labelled software mode), Night, HDR, Panorama, Slow Motion (verified high frame rate), Time-lapse, Macro, Document Scan, Pro.
- **Computational features, staged and optional:** Top Shot (rank burst frames, keep all originals), Long Exposure, Action Pan, motion photo (a paired still and clip with a documented sidecar), Dual Camera (only where concurrent cameras are supported), Astrophotography. Group-photo "best take" composites must be labelled as composited and keep every source frame.
- **Video:** resolution and fps from real capabilities, pause and resume only where it does not corrupt the file, stabilization only where supported, audio source choice, storage estimate before long recordings, 10-bit HDR only where supported.
- **Capture integrity:** a capture counts as saved only after the stream closes, the MediaStore row commits and indexing is scheduled. If saving fails, tell the user and keep the buffer if possible.

### 6.4 Files

- **Browse page** (iOS Files style): large title, search, Recents, Downloads, Favourites, Tags, Trash, internal storage, SD card, USB drive with free space, Camera, Pictures, Movies, Documents. Do not show iCloud Drive or Shared. Use honest labels such as "On this device".
- **File operations:** a journal, copy-verify-delete moves, conflict choices (Replace with confirmation, Keep both, Skip, Apply to all), batch rename with a preview, undo of the last reversible operation.
- **Preview (Quick Look style):** images, video, audio, PDF, text, Markdown, CSV, JSON and source code. Office files only if a licensed offline renderer is added and tested.
- **PDF tools:** merge, split, reorder, rotate, annotate, fill forms, sign. Originals stay untouched by default.
- **Document scanner:** edge detection, perspective fix, multi-page, searchable PDF with an OCR text layer.
- **Archives:** keep the existing Zip-Slip and zip-bomb protection. Add archive preview, selected-entry extraction, password prompts, and TAR/GZIP/7z/RAR read support only with a maintained library and adversarial tests.
- **Cleaner:** storage analyzer, large files, exact duplicates by streaming hash, old downloads, empty folders. The app cannot clear other apps' private caches; link to the system App Info screen instead.
- **Vault and app lock:** Keystore-backed keys, authenticated encryption, BiometricPrompt with PIN fallback, auto-lock, protected recents preview, no plaintext thumbnails. A decoy PIN is a privacy convenience, not forensic protection.
- **External storage:** SD and USB through SAF, persisted grants checked on restart, safe eject only through supported system flows.

### 6.5 Extras that make it usable as a daily app

1. Import and migration assistant that indexes in place without duplicating files.
2. Encrypted local backup of albums, tags, ratings, captions, recipes, order and settings, tested by restoring onto a clean install.
3. Media integrity checker for unreadable files, stale thumbnails and orphaned exports.
4. Format capability matrix: what this device can decode, encode, preview and export (HEIC, AVIF, WebP, GIF, DNG, HDR video).
5. Sidecar export of edit stacks, tags and ratings in a documented JSON format, so metadata is not locked into one database.
6. Per-folder privacy controls: exclude from indexing, OCR, AI, memories and widgets separately.
7. Storage guardrails before 4K recording, big exports, extraction and model imports.
8. User-initiated, sanitized diagnostics export with a preview of the report.
9. Accessibility and localisation QA: TalkBack, large text, reduced motion, right-to-left, localized dates.
10. Widgets and shortcuts (recent, favourites, memories, camera) that never show Vault content.

## 7. iOS 27-style design system

The goal is the iOS look, not a pixel copy. Your current identity is neon-glass with Space Grotesk and Plus Jakarta Sans; change the default to a calm, neutral, Apple-like look and keep the old accents as optional themes.

| Area | Direction |
| --- | --- |
| Material | Translucent navigation bars, tab bar, toolbars and sheets with a thin specular edge. Blur only where the device can afford it; use a high-opacity solid fill on low-end GPUs or with Reduce Transparency. |
| Type | Large bold titles that collapse on scroll, semibold section headers, regular body. Use a licensed system-like font. Do not bundle Apple's SF fonts. |
| Icons | An open icon family tuned to look like SF Symbols (for example Phosphor, Lucide or Material Symbols Rounded). Do not use Apple's SF Symbols on Android. |
| Colour | Neutral system backgrounds, one accent blue, yellow for camera focus and mode, red only for recording and destructive actions. Light, Dark, AMOLED, High Contrast, Follow System. Dynamic colour is opt-in and must not hurt camera contrast. |
| Shape and space | Continuous-looking corners, 10 to 16 dp on cards, 2 to 4 dp on dense photo grids, 8 dp rhythm, 16 dp margins, 48 dp touch targets. |
| Motion | Springs of about 250 to 350 ms, thumbnail-to-viewer hero transition, swipe-down to dismiss, tab crossfades, restrained haptics. Reduce Motion swaps these for fades. |
| Legibility | Never put essential text on a busy photo without a scrim. |

**Build once as a component library:** GlassTopBar, GlassBottomBar, GlassSheet, GlassContextMenu, LargeTitleHeader, SegmentedControl, SearchField, SelectionToolbar, ToastUndo, MediaGridCell, DateHeader, ScrubberRail, ZoomableMedia, VideoControls, EditToolbar, SliderRow, PermissionCard, EmptyState, LoadingSkeleton, CameraModeSelector, ShutterButton, ZoomPill, FocusReticle. Add a developer-only component gallery in all four themes with screenshot tests.

**Screens to deliver** are the ones in your spec (Files, Camera, Photos, shared) plus: Collections with Captured by Me and Identity Documents, Tools panel, Slideshow editor, Lens mode, Camera controls customizer, PDF tools, Selection Review, Operation Centre, Default App and System Integration, and the privacy and storage dashboard.

## 8. Shared architecture

**One asset identity, three sources of truth, each for its own job:**

- MediaStore is authoritative for shared photos and videos.
- SAF providers are authoritative for arbitrary documents and file trees.
- Room (`far.db`) is authoritative only for Far Gallery's own data: albums, tags, keywords, captions, ratings, edit recipes, OCR text, scores, memories, projects, folder grants, scan checkpoints and the operation journal. It never decides whether a file exists.

A canonical `MediaAsset` record should carry a stable ID, source (MediaStore, SAF, app-private), URI, volume, name, MIME type, size, dates (captured, added, modified), dimensions, duration, orientation, flags (favourite, archived, hidden, locked, captured by Far Gallery), rating and an optional content hash. Adapt this to the real schema with a migration; do not duplicate files.

**Database areas to extend** (current tables: albums, album\_media, tags, media\_tags, ocr\_index, operation\_records): ratings and keywords, captions and alt text, edit stacks (versioned JSON), FTS index for names, tags and OCR (replace the current `LIKE` scan), object and scene labels, opt-in face and pet clusters kept in a separate deletable store, quality scores, memories and creation projects, pinned collection order, folder rules, search history and an encrypted Vault area.

**Indexer:** incremental and resumable, content observers plus targeted rescans, WorkManager for deferred work (no permanent foreground service), statuses for discovered, indexed, OCR done, ML done, failed and excluded, and respect for battery, thermal state and free space.

**ML:** keep ML Kit for OCR and barcodes. Add other local models only after a runtime, licence, size and quality review, and bundle them in the app (no remote downloads).

**Edit engine:** keep the immutable edit-state model. Render slider previews on downscaled bitmaps and run full resolution only on export. Move toward GPU rendering for the larger adjustment set.

**Permissions:** media read permissions with partial access, camera and microphone only when used, `ACCESS_MEDIA_LOCATION` only for unredacted location, and SAF grants for arbitrary files. `MANAGE_EXTERNAL_STORAGE` is restricted; prefer SAF and add it only if the file-manager use case truly needs it and distribution rules allow it.

**Intent and provider contracts to implement and test:** `ACTION_VIEW`, `ACTION_SEND` and `SEND_MULTIPLE`, `ACTION_IMAGE_CAPTURE`, `ACTION_VIDEO_CAPTURE`, `ACTION_PICK`, `ACTION_GET_CONTENT`, a `DocumentsProvider` for deliberately exposed content, and `FileProvider` for exports. Test zero-byte files, huge files, read-only providers, revoked grants, null cursors and malicious display names.

## 9. Privacy, security and error handling

- Audit the merged manifest, transitive dependencies, WebViews and SDK initialisers for network code. A missing `INTERNET` permission is a strong control, but test the shipped build.
- Never store a plain PIN. Use Keystore-backed keys and reviewed authenticated encryption; do not write your own crypto.
- Keep media names, private paths, face labels, OCR text, coordinates and URI tokens out of release logs.
- Let users delete OCR indexes, embeddings, face clusters, thumbnails and search history separately from their media.
- Face grouping is opt-in, local, deletable, and never names a real person automatically.
- Clear temporary previews and exports on success, failure, cancel, restart and process death.

| Situation | Required behaviour |
| --- | --- |
| Camera permission denied | Calm explanation and a Settings button; no repeated prompts |
| Media access limited | Show what is accessible with a manage-access banner; never say the library is empty |
| Unsupported lens, ISO, fps, RAW or mode | Disable it and explain; never fall back silently under a misleading label |
| Capture write fails | Say so, keep the buffer if possible, never claim it saved |
| Low storage during export | Check space first, offer cancel or lower quality, clean temp files, keep the original |
| SD card removed mid-operation | Keep the journal, mark the source unavailable, resume only after revalidation |
| Copy or move interrupted | Resume or roll back from the journal; never delete the source before the copy is verified |
| Provider cannot rename | Explain; offer copy-verify-delete only when safe |
| Bad archive, zip bomb, path traversal | Abort, remove partial output, keep the archive |
| OCR or model unavailable | Browsing and search continue; no blank screen or fake results |
| Database migration fails | Keep a backup, show a recovery state, never wipe user data |
| Process dies during a long job | Recover from the journal on next launch and show what finished |

## 10. Performance budgets

Measure on real low, mid and high devices before promising numbers. Track: cold and warm launch, camera launch to preview, shutter to visible thumbnail, scroll jank with 10k, 50k and 100k items, thumbnail cache hit rate, indexing speed with battery and thermal cost, peak memory in the viewer and during 4K trim, copy throughput, and app and model size.

Rules: Paging 3 and lazy grids, bounded thumbnail concurrency, streaming hashes and copies, downscaled slider previews, cancel obsolete requests, and pause heavy ML when the battery is low, the phone is hot, storage is low or video is recording.

## 11. Phased roadmap

Each phase ends with a runnable build, tests and a short report. Do not start a phase before the previous exit criteria are met.

### Phase 0: Audit and project truth

- Read the whole repository and compare it with `APP_OVERVIEW.md` and this plan.
- Produce a Done / Partial / Missing / Broken / Unknown matrix with file, class and test evidence.
- Plan fixes for the eight problems in section 5, and design a metadata export before any schema change.
- **Exit:** reproducible build, evidence-backed audit, risk list, architecture and migration proposal, test plan. No feature work yet.

### Phase 1: Stabilise the foundation

- Fix problems 2 to 7 from section 5 (chunked scans, WorkManager OCR, URI handling, safe database recovery, trim storage, rename fallback).
- Review the alpha dependencies and pin stable versions where safe.
- **Exit:** scans pass on libraries over 10,000 items and resume after force-stop; migration tests keep user data.

### Phase 2: Design system and shell

- Tokens, glass components with solid fallback, four themes, component gallery, screenshot tests.
- Photos / Camera / Files shell, adaptive tablet layout, Photos secondary navigation.
- **Exit:** all destinations navigable, existing deep links still work, accessibility checks pass on primary controls.

### Phase 3: Unified asset identity and safe file operations

- Canonical asset IDs, MediaStore and SAF reconciliation, per-folder rules, operation journal, copy-verify-delete moves, metadata backup foundation.
- **Exit:** capture, import, rename, move and delete appear consistently everywhere with no duplicate physical files.

### Phase 4: Photos core and system integration

- Sort by captured or added date, filters, Selection Review, ratings, keywords, captions, albums and album folders, Collections, Captured by Me, archive, hidden, trash.
- In-app picker and VIEW / PICK / GET\_CONTENT handling.
- **Exit:** browse, view, select, organise, pick, share and restore all work with no duplicate files.

### Phase 5: Files core

- Browse page, journaled operations, conflict handling, batch rename, search, previews, archive preview, SD and USB, `DocumentsProvider` and `FileProvider`.
- **Exit:** file-manager tests pass on internal storage, SAF trees and SD card; forced process death loses no data.

### Phase 6: Camera core and capture intents

- Real capability reporting, controls, burst, timer, aspect and resolution, settings memory, `EXTRA_OUTPUT` handling, single save and index, removal of misleading Portrait and Slo-mo fallbacks.
- **Exit:** captures appear once; no false feature support; low-storage, cancel and error paths tested.

### Phase 7: Editing and video

- Larger adjustment set, markup, retouch basics, recipes, batch apply, Save Copy, revert, video compress and export options, frame export.
- **Exit:** original-file hashes unchanged after editing; exports play; edit stacks survive restart and upgrade.

### Phase 8: On-device intelligence

- Background OCR with FTS, scene and document tags, optional face and pet grouping, blur and similarity scores, stacks, natural-language parser, memories and featured photos.
- **Exit:** works offline; resource use measured; mistakes can be corrected; opt-out deletes derived personal data; unsupported devices stay fully usable.

### Phase 9: Advanced camera and computational photography

- Pro controls, RAW, HDR and night merge, real Portrait, panorama, time-lapse, verified slow motion, long exposure, Action Pan, motion photo, Lens mode.
- **Exit:** each feature has a support matrix, sample-output tests, a capability badge and a clean fallback.

### Phase 10: Creations, Vault and backup

- Slideshow editor, collage, animation, movie, photo-book PDF, Vault, app lock, local backup and restore.
- **Exit:** a full restore onto a clean install recovers albums, ratings, tags, edits, settings and supported Vault content.

### Phase 11: Integration polish and release QA

- Default App and System Integration screen, widgets, shortcuts, keyboard support, TalkBack, text scale, high contrast, reduced motion, localisation.
- Manifest, dependency, licence, network and privacy audits; benchmarks on real devices.
- **Exit:** critical tests pass, no high-severity data-loss or security defects, unsupported features clearly shown as unavailable.

## 12. Acceptance tests

**Library:** a new capture appears once and shows in Photos and Files; a rename or move keeps albums, tags and ratings; SD removal and revoked grants do not crash; scans past 10,000 items resume after force-stop; a selection of thousands survives paging, filtering and rotation.

**Photos:** captured and added date sorting is consistent; ratings and keywords survive restart and migration; Captured by Me contains only verified captures; Vault items never appear in search, widgets, recents or timelines; duplicate review never deletes without confirmation.

**Camera:** hardware capabilities are queried per device; slow-motion output reports its real frame rate; Portrait output actually contains depth processing or the mode is unavailable; flashless and single-lens devices show no fake controls; capture intents honour `EXTRA_OUTPUT`, permissions and cancellation.

**Files:** SAF grants survive reboot or prompt for re-authorisation; cross-volume moves never remove the source before verification; rename failure is never reported as success; extraction blocks traversal, symlink escape and zip bombs.

**Privacy:** the strict build's merged manifest has no `android.permission.INTERNET`; core flows pass with the network disabled; Vault temp files, thumbnails and notifications reveal nothing; PINs and keys are never plaintext.

**UX:** main flows work with TalkBack, large text, high contrast, Reduce Motion and Reduce Transparency; every screen has loading, empty, limited-permission and error states.

## 13. Master prompt for your coding agent

Give the agent this document and `APP_OVERVIEW.md`, then paste the block below.

> **Role.** You are the senior Android engineer, architect, QA engineer and designer for my existing app, Far Gallery. Work in the current repository and preserve the existing app. Do not build a sample or mock-up.
>
> **Sources.** Read in full: `APP_OVERVIEW.md`, the Far Gallery Master Prompt and Build Plan (v2), and the repository itself (manifest, Gradle files, source, Room schemas and migrations, tests). The current code is the truth; the overview is documentation. Mark every feature Done / Partial / Missing / Broken / Unknown with evidence (file paths, classes, tests).
>
> **Goal.** Evolve Far Gallery into one app with Photos, Camera and Files destinations that share one asset identity. Use an iOS 27-inspired look (hierarchy, glass materials, large titles, motion), interpreted for Android with original assets. Match the useful local features of Google Photos, Pixel Camera, Files by Google and Apple's Photos, Camera and Files. Never promise cloud or device-exclusive features that cannot run locally or through public Android APIs.
>
> **Hard rules.**
>
> - Stay on native Kotlin + Jetpack Compose. No framework change and no rewrite of working modules.
> - Strict Offline is the default: no `INTERNET` permission, no accounts, telemetry, ads, cloud or remote model downloads. Any LAN feature goes in a separate variant that I must approve first.
> - One canonical asset and one physical file. Camera captures appear in Photos and Files without a second copy.
> - MediaStore is authoritative for shared media, SAF for arbitrary documents, Room only for Far Gallery's own data.
> - Originals are preserved. Edits default to Save Copy.
> - Every delete or cleanup is reviewed, confirmed, routed through system trash where supported, and reported truthfully.
> - Public Android APIs only. Do not claim the system gallery role or universal replacement of OEM camera, lock-screen or hardware-key behavior. Build the Default App and System Integration screen from section 3.
> - Query hardware at runtime. No fake Portrait, Slo-mo, RAW or Pro controls and no hidden fallbacks.
> - Every visible control is wired to a real operation. No placeholders, demo data, fake progress, `TODO`, or omitted methods.
> - Every Room change has a data-preserving migration and tests. Never delete `far.db` as a generic recovery.
> - Keep sensitive media and Vault data out of logs, notifications, thumbnails, widgets and search.
>
> **Work sequence.**
>
> 1. Audit the repository and verify the build. Write no feature code yet.
> 2. Deliver the status matrix, the eight-problem report from section 5 with fix plans, the target architecture, schema and manifest changes, the Phase 1 file list and the test plan.
> 3. Wait for my approval before any major migration, permission or privacy change, module replacement or new native module.
> 4. Implement one phase at a time. Before each phase list the exact files and classes you will change. Avoid unrelated edits.
> 5. After each phase run the build, unit tests, available instrumented tests and static analysis, fix regressions, then report: behavior delivered, files changed, test results, limitations, known bugs, permission impact, storage impact, and battery, memory and thermal impact.
>
> **Quality gate.** A feature is Done only when the whole flow works end to end, persists state, handles cancel and errors, respects permissions and privacy rules, passes tests and has no fake controls. Where support varies by device, include a tested capability matrix and an honest fallback.
>
> **First reply.** Deliver only the audit, status matrix, problem report, architecture proposal, proposed migrations and permissions, Phase 1 file list and test plan. Wait for my approval.

## 14. Risks

- **Scope:** this is a multi-year roadmap for a large team. Ship a beta after Phase 6 or 7, when the basics are dependable.
- **Camera quality:** Pixel and iPhone use private image-pipeline access. Third-party apps get CameraX, Camera2 and vendor extensions. Set expectations accordingly.
- **Models:** bundled AI models add app size, memory and heat. Tier by device and gate every feature.
- **Store policy:** broad file access needs justification, and default-handler claims must stay truthful.
- **Look-alike risk:** follow Apple's design principles, not its assets, names or artwork.
- **Data loss:** the journal, trash for every delete, and a metadata backup before any migration.

## 15. Decisions for you

1. Strict Offline only, or also a separately approved Local network edition?
2. Distribution: Google Play (needs a broad-storage justification), F-Droid or direct APK?
3. Minimum device spec and test devices, which set the AI tiers.
4. Which advanced AI edits (Clean Up, Extend, Reframe) are worth the size and heat, or should they wait?
5. Keep the neon accent themes as options, or retire them?

## 16. Sources and what I checked

I read your `APP_OVERVIEW.md` and the ChatGPT plan in full. The iOS 27 facts come from press coverage found in search results, not from Apple's pages, so treat them as reported:

- [9to5Mac: everything new for Photos in iOS 27](https://9to5mac.com/2026/06/16/heres-everything-new-for-apple-photos-in-ios-27/)
- [9to5Mac: Captured by Me](https://9to5mac.com/?p=1063222)
- [9to5Mac: iOS 27 Camera, three new features](https://9to5mac.com/2026/09/15/ios-27-gives-camera-app-on-iphone-three-powerful-new-features/)
- [MacRumors forum: what's new in the iOS 27 Photos app](https://forums.macrumors.com/threads/whats-new-in-the-ios-27-photos-app.2483851/)
- [MacStories: iOS and iPadOS 27 review, Files](https://www.macstories.net/stories/ios-and-ipados-27-review/9/)

The ChatGPT plan also lists Apple, Google and Android developer documentation links (for example the Android `RoleManager` and local-network permission pages). I have not opened those pages, so re-check them during implementation, especially the Android role and local-network rules.

## 17. Appendix A: your full feature list, with status

Sections 1 to 16 covered only the gaps. This appendix lists **every feature in your pasted spec** so the coding agent has them all in one place. Give the agent this appendix as required reading. Tags: **Exists** = the overview says it is built (verify in code), **New** = to build, **HW** = depends on device hardware (show a badge and fallback), **Model** = needs a bundled on-device model, **Adapted** = changed to fit Android or the strict-offline rule, with the reason.

### A.1 Photos: library and browsing

- Automatic media scanning, background indexing, settings to switch it off, choose indexed folders, clear thumbnail cache and rebuild the index. **Exists** (scan and exclusions), **New** (WorkManager background index, rebuild and cache-clear controls).
- Unified timeline with date headers, thumbnails, duration badges. **Exists.**
- Days, Months, Years views. **Exists.** All Photos view. **Exists.**
- Media-type filter: photos, videos, motion photos, screenshots, selfies, panoramas, bursts, RAW, GIFs, documents. **Partly exists; New** for motion, selfies, panoramas, bursts, RAW, documents. **Adapted:** Apple "Live Photos" do not exist on Android. Read Google and Samsung motion photos, and import Apple Live Photo pairs as a still plus a video.
- Grid-size control (compact and comfortable) and pinch density. **Exists** (2 to 6 columns); add the visible control.
- Sort and filter by date, name, size, duration, type, favourite, edited, location. **Partly exists; New** for edited and location sorting.
- Calendar and timeline scrubber to jump to a date. **Partly exists** (fast scrollbar); **New** calendar jump.
- Full-screen viewer: swipe, pinch, double-tap zoom, rotate, video controls. **Exists** (add rotate if missing).
- Video player: play and pause, seek, mute, speed, fullscreen, resume from last position. **Exists** except mute, fullscreen and resume, which are **New**.
- Albums and folders: create, rename, delete, move, copy, nest, favourite. **Partly exists;** nesting, copy, move and favourite albums are **New.**
- Smart albums: screenshots, videos, selfies, motion photos, RAW, downloads, favourites, edited, recently added, people, places. **Partly exists; New** as saved rules.
- Favourites, Archive, Hidden views that keep media out of the timeline without deleting it. **Favourites exist; Archive and Hidden are New.**
- Trash with restore and auto-delete after a set period. **Exists** through Android scoped trash (the system sets the purge period, usually about 30 days). **Adapted:** a custom retention setting only applies to the app's own trash records; say so in the UI.
- Batch selection for delete, move, copy, share, favourite, archive, hide, tag, add to album. **Partly exists;** archive, hide and move are **New.**
- Recently Added view. **Exists** (Camera Roll and recents); make it a named view.
- On This Day. **Exists.** Featured Photos using on-device scoring. **New, Model-optional** (start with favourites, ratings and sharpness).
- Offline mode for browsing and managing local media. **Exists** by design.
- Widgets and quick actions for recent photos, favourites, memories and camera. **Partly exists** (stats widget); **New** the rest, and none may show Vault content.
- Dark mode, custom themes, adjustable grid. **Exists.**

### A.2 Photos: search and smart organisation

- Search by date, filename, album, folder, tag, caption, keyword, media type, favourite, edited, location. **Partly exists;** caption, keyword, edited and location are **New.**
- OCR search inside screenshots and photos. **Exists** (foreground); make it background, and move from `LIKE` to FTS.
- Object and scene search (food, cars, pets, documents, receipts, landscapes). **New, Model.**
- People and pets grouping: name, merge, hide, correct. **New, Model, opt-in.**
- Place grouping and a map view of geotagged media. **New.** **Adapted:** there is no internet, so the map only works with a map tile pack the user imports from local storage. Without one, show a list of places grouped by coordinates. Reading location needs the media-location permission.
- Tags, keywords, captions, alt text, searchable offline. **Tags exist; New** for the rest.
- Duplicate detection, identical and similar. **Identical exists** (size and SHA-256); **similar is New** (perceptual hash).
- Blurry-photo detection. **New.** Screenshot and document detection for receipts, IDs and documents. **New, Model-optional.**
- "Things" categories: animals, buildings, food, nature, vehicles, documents. **New, Model.**
- EXIF-based grouping by camera, lens, ISO, focal length or date taken. **New.**

### A.3 Memories and creations

- Auto-curated Memories from people, pets, places, dates, favourites and themes. **New.**
- On This Day memories, trip and place memories, people and pet memories, themed memories (holidays, weekends, seasons, recurring events). **New**, using local metadata only. Give users controls to hide people, places and dates.
- Slideshow creator with speed, transitions and local music. **New** (the viewer has a basic 3, 5, 8 s slideshow).
- Movie creator: combine photos and videos, add local music, trim, export. **New.**
- Collage creator with adjustable layouts. **New.** Animation creator to GIF or video. **New.**
- Featured Photo picker to pin memories and collections. **New.**

### A.4 Photo editing

- Non-destructive editing, revert to original, undo and redo, before and after. **Exists.**
- Crop, rotate, flip, straighten. **Exists.** Perspective correction. **New.**
- Light: exposure, brilliance, highlights, shadows, contrast, black point. **Partly exists** (brightness, contrast); the rest **New.**
- Colour: saturation, vibrance, warmth, tint, white balance. **Partly exists;** vibrance and tint **New.**
- Filters and black-and-white presets with intensity. **Exists** (8 presets); add an intensity slider and more black-and-white looks.
- Retouch, red-eye, blemish removal, spot healing. **New.**
- Portrait controls: background blur, portrait lighting. **New, HW or Model.**
- Markup: drawing, text, shapes, signatures, arrows. **New.**
- Motion photo controls: key photo, loop, bounce, long exposure. **New, HW.**
- Copy and paste edits for batches. **Exists** (recipes).
- RAW editing. **New, HW and software support.**
- Magic Eraser style object removal. **New, Model.** Advanced AI edits stay behind a device-tier gate (section 6.2).

### A.5 Video editing

- Trim and save as a new clip or overwrite. **Trim exists.** **Adapted:** overwrite is allowed only after an explicit confirmation and with a recoverable original; Save Copy stays the default.
- Mute, speed, rotate, crop, basic colour and light. **Partly exists** (mute, speed on trim); the rest **New.**
- Save a still frame. **Exists.**
- Compression that keeps the original. **New.** Export options for resolution, frame rate and format from supported encoders. **New.**
- Basic stabilisation. **New, HW or Model.**
- Motion photo to video and video to GIF. **New.**

### A.6 Metadata, files and storage utilities

- Metadata view. **Exists.** Metadata editing for date, time, location, camera, lens, filename, caption, keywords. **New.** **Adapted:** writing EXIF changes the file, so write only to a saved copy or after confirmation. Captions, keywords and ratings live in the database unless a tested write to the file is added, and the UI must say which.
- Rename and batch rename with patterns. **Batch rename exists;** add pattern preview and collision preview.
- Import and export with local storage, SD cards, USB drives and device folders. **Partly exists;** make import and export explicit.
- Local backup and restore of albums and folders to external storage. **New.**
- Storage cleanup: largest files, old videos, screenshots, duplicates, blurry photos, unused albums. **Partly exists;** blurry, old videos and unused albums are **New.**
- Free Up Space that deletes only local copies after confirmation. **New.**
- Cross-folder move and copy with conflict handling. **Partly exists; New** conflict dialog (Replace, Keep both, Skip, Apply to all).
- Format conversion and export. **New, HW** (decoder and encoder support differs by device).

### A.7 Privacy and security

- App lock with PIN or biometrics and auto-lock timeout. **New.**
- Locked Folder and Vault that hides media from the timeline, search and widgets. **New.** Encrypted, so it must be a real encrypted store, not just a hidden album.
- Hidden albums that also stay out of indexing. **New.**
- Folder permission controls. **Exists** (SAF grants and exclusions).
- No-network guarantee, no telemetry, analytics, ads or tracking. **Exists;** add the manifest and network tests from section 12.
- Secure deletion and trash retention controls. **New. Adapted:** overwriting is not reliable on flash storage; label it best-effort.

### A.8 Sharing and transfer

- Share to local apps and export to Files for single or many items. **Exists** (system share sheet).
- Album and folder export as a zip or folder structure. **New.**
- AirDrop, Nearby Share, Bluetooth, Wi-Fi Direct and QR or pairing transfer. **Adapted.** AirDrop is Apple-only and does not exist on Android. In the strict build, nearby sharing happens through the system share sheet (which offers Quick Share, Bluetooth and other installed targets). A direct Wi-Fi Direct or QR-paired transfer between two devices needs sockets and extra permissions, so it belongs only in the separately approved Local network edition (section 4).
- No online links, cloud albums, partner sharing or uploads. **Hard rule.**

### A.9 Accessibility, UI and performance

- Dark, light and system themes. **Exists.** Adjustable text size, high contrast, screen reader labels on all media actions, alt text and caption editing. **Partly exists; New** alt text and full label audit.
- Keyboard shortcuts and context menus for tablet and desktop-style use. **New.** **Adapted:** the "Mac" requirement becomes hardware keyboards on tablets, foldables and ChromeOS.
- Lazy loading, thumbnail cache, background indexing, smooth scrolling. **Exists.**
- Battery-aware indexing that pauses heavy ML. **New.**
- Offline onboarding that explains local AI, search and editing. **Partly exists;** add the AI and editing explanation.
- Settings panel with storage use, index status, locked folders and privacy controls. **Partly exists; New** index status and Vault.

### A.10 Files

- Storage browser for internal storage, SD card, USB OTG with breadcrumbs and back and up navigation. **Exists** for internal and SAF trees; **New** first-class SD and USB rows.
- Grid and list toggle, sort by name, date, size, type, ascending or descending. **Exists.**
- Filter by images, videos, audio, documents, archives, APK, other. **New.**
- Hidden files toggle. **New.**
- Quick access: Home, Recents, Downloads, Favourites, Trash. **Partly exists** (starred, pinned); **New** Recents, Downloads, Trash.
- Bookmarks, recent files, tags and colour labels, file info and properties (name, path, size, dates, MIME type, permissions, location). **Partly exists; New** most of these.
- Create folder and blank text file. **New.** Rename with extension warning. **Exists;** add the warning and the provider fallback (problem 7).
- Copy, cut, paste, move with conflict handling, duplicate, delete with confirmation, multi-select batch actions (copy, move, delete, share, compress). **Partly exists; New** most of these.
- Open with an installed app, and as a fallback when the built-in viewer cannot open a file. **New.**
- Share through the system share sheet only. **Exists.**
- Drag and drop between panes and folders. **New, HW** (large screens).
- Files Trash with restore, permanent delete with confirmation and auto-purge after N days. **New.** **Adapted:** Android has no trash for arbitrary files, so the app keeps its own trash folder for files it deletes and says that deleted-elsewhere files are not recoverable.
- Filename search across selected storage, advanced search by extension, type, size, date and folder, recent searches, combined filters. **Partly exists;** advanced filters and history are **New.**
- Preview: image zoom, pan, slideshow, rotate; video and audio player with seek, volume and subtitles; PDF and text; common documents; text editor for TXT and Markdown. **Partly exists** (PDF, text, images); audio, subtitles, editor and Office previews are **New** (Office only with a licensed offline renderer).
- ZIP create and extract, archive preview, password-protected ZIP. **ZIP exists;** preview and passwords are **New.**
- Storage analyzer by category and folder, largest files, duplicate finder by hash, name and size, junk suggestions (empty folders, old downloads, screenshots, cache-like leftovers), category view, clean with confirmation. **Partly exists; New** junk, category view and folder breakdown. The app never deletes without review and cannot clear other apps' private caches.
- Safe Folder (encrypted), hide files and folders, secure delete, app lock. **New** (shares the Vault with A.7).
- SD card and USB OTG read and write with permission handling. **Partly exists; New.** Eject. **Adapted:** Android does not let an app unmount storage. Open the system storage screen and tell the user how to remove it safely.
- Nearby transfer with Wi-Fi Direct or Bluetooth. **Adapted** as in A.8.
- Settings: default start folder, delete confirmation toggle, trash retention, language support, empty states, clear permission, storage and conflict errors. **New.**

### A.11 Camera

- Fast launch and low shutter lag. **Exists** (verify by measurement).
- Front and back switch. **Exists.** Zoom with presets and smooth transition. **Partly exists;** presets are **New, HW.**
- Tap to focus, drag exposure, lock focus and exposure. **Exists.**
- Flash and torch. **Exists.** Timer of 3 and 10 seconds. **New.** Grid and level. **Exists.**
- Aspect ratio and resolution (4:3, 16:9, 1:1, full). **Partly exists;** resolution list is **New.**
- Burst on shutter hold. **Exists.** Quick settings access. **New.**
- Modes: Photo, Portrait, Night, Panorama, Slo-mo, Time-lapse, Macro, Pro, RAW, Document and QR scan, Video with pause and resume. **Photo, Video, Square and Scan exist. The rest are New** and **HW**; the existing Portrait and Slo-mo stubs must be fixed or hidden (problem 1).
- Computational photography, all on-device: HDR+ style merge, low-light processing, scene detection, face and skin-tone tuning, denoise and sharpen, lens distortion correction, scene-aware auto white balance and exposure, Photographic Styles, depth blur adjustable after capture, Top Shot, motion photos, Magic Eraser, Long Exposure, Action Pan, Frequent Faces, dual exposure controls. **All New;** most are **Model** or **HW.** Build them in the order in Phase 9 and ship each only with a verified result and a fallback.
- Video: 4K and 1080p with fps choice, stabilisation, audio zoom (**HW**), cinematic blur (**HW or Model**), slo-mo and time-lapse, front-camera video. **New** unless the device exposes them.
- UX: large thumb-reachable shutter, swipe between modes, haptics, accessibility labels and large controls, a local gallery from the camera, a basic editor, EXIF and location removal before sharing, and a share sheet that uses only installed apps. **Mostly New.**
- Offline and privacy rules: no internet permission, no account, no cloud, bundled models, graceful fallback, clear hardware badges. **Hard rules.**
- Excluded: Google Lens and search, cloud AI, cloud backup, Google and iCloud sync, online sharing, account sync, remote model downloads, cloud face grouping.

## 18. Appendix B: the Apple-style design spec, mapped to Android

Keep every layout idea from your spec. Changes are only where Apple tools do not exist on Android.

| Your spec | Android version |
| --- | --- |
| Bottom tab bar: Files, Camera, Photos | Three destinations: Photos, Camera, Files. Photos is the landing screen. Translucent bar with a hairline separator, blue selected state, 48 dp targets (never below 44 dp). |
| SF Symbols icons | An open icon family styled to match. Apple's SF Symbols are licensed for Apple platforms only. |
| SF Pro or system font | The Android system font or another licensed sans-serif. Do not bundle SF Pro. |
| Dynamic Type | Android font scale support up to 200 percent, tested on every screen. |
| Camera tab opens full-screen black, tab bar hidden while capturing, slide-up transition, swipe down to dismiss | Same. Use a full-screen camera activity or destination and a swipe-down gesture. Back returns to the previous screen. |
| Colours: system backgrounds, blue accent, yellow for camera, red for recording and delete | Same, with light, dark, AMOLED and high-contrast variants. |
| Blurred bars, context menus and sheets | Same, with a solid fallback when blur is expensive or Reduce Transparency is on. |
| 8 pt grid, 16 pt margins, 10 to 16 pt radii, 44 pt rows | 8 dp grid, 16 dp margins, 10 to 16 dp radii, 48 dp rows. |
| Spring motion, hero thumbnail to photo, tab crossfade, haptics | Same, with Reduce Motion replacing them with fades. |
| Large collapsing titles, back chevrons, ellipsis menu, selection bar with Cancel, count and Done | Same. |
| Files > Browse, search field, Favorites, Locations, Tags, Recently Deleted | Same, but Locations shows On this device, SD card and USB drive. **Remove the iCloud Drive and Shared rows.** |
| Folder screens with Select, ellipsis, sort (Name, Date, Size, Kind), grid and list toggle, empty state | Same. |
| Long-press menu: Open, Share, Rename, Move, Duplicate, Info, Delete | Same, disabling anything the provider does not support. |
| Quick Look-style preview with adaptive background, top bar, bottom toolbar | Same. |
| Drag and drop between folders and into Photos | Same where Android supports drag and drop. |
| Files > Camera folder showing captures | The Far Gallery folder under Pictures, backed by the same record as Photos. |
| Camera layout: top pills (Flash, Live, Timer, Ratio, More), mode selector, shutter ring, last-photo thumbnail, switch button, 0.5x 1x 2x 3x zoom pill, yellow focus square with sun slider, shutter animation, thumbnail bounce and morph | Same. Show only zoom stops the device really has. "Live" becomes Motion Photo. |
| Camera permission denied screen with Open Settings | Same. |
| Photos: large title, search, segmented Years, Months, Days, All Photos, sub-tabs Library, Albums, Search | Same (inside the Photos destination). |
| 3-column grid on phones, 5-column on tablets, 2 to 4 dp corners, sticky headers, Live and heart badges, pinch density, scroll to top | Same; Motion Photo badge instead of Live. |
| Photo detail: black canvas, hero animation, top bar with back, favourite, info; bottom Share, Edit, Delete; tap to hide chrome; swipe to dismiss | Same. |
| Selection mode with blue check circles, Cancel, "X Selected", Done; bottom Share, Add to Album, Delete, More | Same, plus the Selection Review list. |
| Edit screen with Adjust, Filters, Crop tabs, Cancel and Done, revert, sliders, live preview | Same; add a Tools tab for advanced edits when available. |
| Albums: 2-column grid, My Albums, People and Pets, Places, Media Types, Utilities, plus button | Same. |
| Search with suggestions, chips, natural-language placeholder, recents | Same, showing the filters it extracted. |
| Optional Memories carousel, secondary to the Library | Same. |
| Onboarding with three permission cards (Camera, Photos, Files) | Same, with Android permission wording and partial-access choices. |
| Share sheet with blurred rounded sheet, Copy, Save to Files, Add to Album, AirDrop | Use Android's system share sheet for sharing out. Keep an in-app sheet for Copy, Save to Files and Add to Album. **No AirDrop on Android.** |
| Empty, loading, error states with centered icons and blue buttons | Same. |
| Accessibility: screen reader, Dynamic Type, Reduce Motion, Increase Contrast, 44 pt targets | TalkBack labels, font scale, system animation setting, high-contrast theme, 48 dp targets. |
| iPad split view and sidebar, larger Photos grid, centered camera controls | Tablet and foldable rail or sidebar, two-pane Files, 5-column grid. |
| Mac toolbar and keyboard shortcuts | Hardware keyboard shortcuts and context menus on tablets, foldables and ChromeOS. |

**Screens to deliver** (unchanged from your spec): Files (Browse, Folder grid, Folder list, Selection mode, Context menu, File preview); Camera (Photo, Video, Focus and exposure, Permission denied, Capture transition); Photos (Library grid, Years, Months, Days, Photo detail, Selection mode, Edit, Albums, Search); Shared (Onboarding permissions, Share sheet, Save to Files, Empty states, Dark mode versions). Add: Default App and System Integration, Operation Centre, Selection Review, Collections, Vault, Privacy and storage dashboard.

**Update to the master prompt (section 13):** the agent's required reading now also includes Appendix A and Appendix B of this document. Where the agent finds a spec line marked **Adapted**, it follows the adaptation and tells you, rather than building the original wording.

## 19. UI and UX improvement plan

The biggest UX gains come from fewer visible choices, safer gestures and faster common tasks, not from more decoration. This section is based on the screen descriptions in your overview, not on screenshots, so the agent must confirm each finding on a real device and I would like to see screenshots of the main screens to refine it.

### 19.1 What to fix, from the current screens

| Area | Problem (from the overview) | Improvement |
| --- | --- | --- |
| Navigation | Four tabs (Photos, Albums, Search, Files) plus a camera button; Albums and Search are things you do inside Photos | Three destinations: Photos, Camera, Files. Albums and Search become Photos sections (section 6.1). |
| Gallery top bar | Title, selection toggle, density menu, filter drawer and settings icon all compete | Large title, one search icon, one "More" menu. Density becomes the segmented control (Years, Months, Days, All) plus pinch. Settings moves under More or the profile-style icon. Select moves to a long-press and a "Select" item in More. |
| Filter drawer | A slide-out panel hides filters | A filter row of chips under the title (Photos, Videos, Favourites, Screenshots) with a "Filters" chip that opens a bottom sheet for size, date and folders. Show the active-filter count. |
| Selection bar | Seven actions at once (Share Presets, Add to Album, Tag, Favorite, Batch Resize, Batch Rename, Trash) | Four primary buttons: Share, Add to Album, Favourite, Delete. Everything else in "More". Show the selected count and a "Select all in this day" shortcut. |
| Albums screen | A nine-chip system collection row | A Collections layout with sections you can reorder and hide: My Albums, People and Pets, Places, Media Types, Utilities. |
| Search | OCR banner and an "Index Photos" button take prime space | Search opens with recent searches and suggestions. Show indexing as a small status line ("Text indexing 63 percent, paused while on battery") that links to the Operation Centre. |
| Viewer | Histogram overlay, filmstrip, action bar and top bar can all show | Chrome hides on tap. Histogram and filmstrip are off by default and live in the Info sheet and a settings toggle for power users. |
| Editor | Tabs: Presets, Adjust, Crop, Recipes | Apple-style bottom tabs: Adjust, Filters, Crop, with Recipes inside the More menu. Always-visible Revert, press-and-hold to compare, a clear Save Copy versus Replace choice. |
| Typography | Three families (Space Grotesk, Plus Jakarta Sans, JetBrains Mono) | One family for everything. Keep the monospace face only for technical values (EXIF, hashes). |
| Colour | Five neon accents, strong glass borders | Neutral default with one blue accent. Neon palettes become optional themes. |
| Camera | Modes in a carousel (Video, Photo, Square, Scan), several toolbars | One quiet top row of 4 controls, a mode carousel, a zoom pill, shutter. Square and Scan move into More or the mode list. |
| Files | Locations pane, starred, pinned and tree grants in one place | A Browse page with clear sections (Recents, Favourites, Locations, Tags), then folder screens. |
| Settings | Long list of panels | Group into five: Appearance, Library and indexing, Camera, Privacy and Vault, Storage and backup. Put a search field at the top. |

### 19.2 Design principles for every screen

1. **One primary action per screen,** visually obvious. Everything else is secondary or in a menu.
2. **Undo before confirm.** Moving to Trash is reversible, so do it immediately and show a "Moved 12 items to Trash. Undo" bar for 8 seconds. Ask for confirmation only for permanent deletion, overwriting, and anything that cannot be undone.
3. **Show state, never guess.** Loading, empty, limited access, indexing, offline-capable, error: each has its own screen with a clear next step.
4. **Progressive disclosure.** Advanced tools (histogram, pro camera, hashes, raw EXIF) are one tap deeper, not on the main surface.
5. **Content first.** Photos fill the screen. Chrome is translucent, small and hides during viewing and capture.
6. **Same gesture, same meaning everywhere.** Tap opens, long-press selects, pinch changes density or zooms, swipe down dismisses, swipe sideways moves between items.
7. **Honest labels.** If a feature is unavailable, say why in plain words ("This phone's camera does not support 240 fps").

### 19.3 Photos

- **Library:** large title that collapses; a segmented control for Years, Months, Days, All; sticky date headers; a thin date scrubber that shows the month and year while dragging and opens a calendar jump on tap. Pinch changes density with smooth interpolation and keeps the item under your fingers in place.
- **Selection:** long-press starts selection; drag across thumbnails to select a range; tapping a date header selects that day; a blue check on each selected thumbnail; a top bar with Cancel, "12 Selected" and Done. Tapping "12 Selected" opens the Selection Review list.
- **Viewer:** swipe sideways between items; swipe down to dismiss with the photo shrinking back to its grid position (shared-element transition); double-tap zooms to the tapped point; swipe up (or tap Info) opens the details sheet with a map, camera data, tags and text found in the image; Favourite, Share, Edit, Delete are the only visible actions.
- **Video:** tap to show controls; double-tap the left or right edge to skip 10 seconds; resume where the user stopped, with a small "Resumed at 3:12" hint.
- **Editing:** a live preview that updates as you drag; sliders with a numeric value and a double-tap-to-reset; a visible "Revert" and "Compare"; Cancel asks "Discard edits?" only when something changed; Done saves a copy and shows "Saved copy. View" with an Undo.
- **Search:** a search field with natural-language placeholder ("Try: videos from last weekend"); suggestion chips for places, people, types and years; results grouped with a small label saying why each matched; a chip row that shows the filters the app understood so the user can remove one with a tap.
- **Albums and Collections:** grid with cover and title below; a plus button; drag to reorder; long-press for rename, pin, cover and delete.
- **Memories:** a small carousel under the library header that can be hidden. Never surprise users with sensitive content; offer "Hide this person, place or date".

### 19.4 Camera

- **Layout:** edge-to-edge viewfinder; a quiet top row (Flash, Timer, Aspect, More); mode carousel (Photo, Video, Portrait, Pano, Time-lapse, Slo-mo, then Scan and Pro as available); shutter in the middle, last-capture thumbnail on the left, switch camera on the right; zoom pill above the shutter.
- **One-hand use:** the shutter and mode selector sit in the bottom thumb zone; swipe left or right on the preview to change mode; volume keys take a photo.
- **Feedback:** the shutter flash and a short haptic; the thumbnail bounces on save; "Saving..." appears only if saving takes more than a moment; a failure message appears immediately and never says the photo was saved when it was not.
- **Focus and exposure:** tap for a yellow focus square, drag up or down for a sun slider, long-press to lock and show "AE/AF lock".
- **Capability honesty:** a mode appears only if the device supports it. Unsupported modes can show greyed with a one-line reason. Show a small "HW" or "Model" badge only in settings and a mode's info sheet, not on the viewfinder.
- **Safety:** a low-storage warning before video, a timer countdown shown big and spoken for screen readers, and the camera releases cleanly when the app goes to the background.

### 19.5 Files

- **Browse page:** a search field and clear sections for Recents, Favourites, Locations (Internal storage, SD card, USB drive with free space) and Tags. Large rows (48 dp) with icon, name, secondary line and chevron.
- **Folders:** breadcrumb path; sort and view in one menu; pull to refresh; swipe actions on rows (Share, Delete); long-press for the context menu (Open, Share, Rename, Move, Duplicate, Info, Delete).
- **Moves and copies:** a clear destination picker, a progress card in the Operation Centre with Pause and Cancel, and a result summary ("11 copied, 1 skipped"). Conflict dialog offers Replace, Keep both, Skip and "Apply to all".
- **Cleaner:** start with a plain summary ("You can free about 3.2 GB") and a list grouped by reason; every suggestion is opt-in and reversible; nothing is deleted without review.
- **Permissions:** explain the permission in context, right when it is needed, with an "Only selected photos" option and a path back to Settings if denied.

### 19.6 Cross-screen UX

- **First run:** three short cards (Camera, Photos, Files) that explain what each permission does and that everything stays on the phone. Allow "Not now"; the app still opens.
- **Empty states:** a centered icon, one sentence and one button (for example "No photos yet. Take a photo").
- **Loading:** skeleton grids, not spinners, for lists; a spinner only for short, unknown waits. Show the first screen from cached data within a moment of launch.
- **Errors:** a title, one sentence of cause and one action ("Not enough space. Free up 400 MB or choose another folder"). Never show codes or stack traces.
- **Long jobs:** one Operation Centre for indexing, backup, export and extraction, reachable from a small status chip; jobs survive app restarts.
- **Haptics and sound:** short and optional; haptics for selection, capture, mode change and delete.
- **Microcopy:** short, plain and specific ("Moved to Trash" rather than "Success"). One voice across all three areas.
- **Settings:** five groups with a search field; each toggle has one line explaining its effect and, where relevant, its battery or storage cost.
- **Personalization:** reorder and hide Collections sections, choose the start destination, and pick compact or comfortable density.

### 19.7 Accessibility and inclusivity

- TalkBack labels on every control with state ("Favourite, on"); a logical focus order; custom actions on thumbnails (Open, Favourite, Select).
- Font scale to 200 percent with no clipped controls; 48 dp targets; contrast of at least 4.5 to 1 for text, with a scrim behind text over photos.
- Reduce Motion and Reduce Transparency respected everywhere; no information conveyed by colour alone (add an icon or label to red and yellow states).
- Right-to-left layouts and localized dates and numbers.
- Alt-text and caption editing in the viewer, searchable.

### 19.8 Task targets to test against

| Task | Target |
| --- | --- |
| Take a photo from a cold start | Under 2 taps from the home screen and the preview visible in under 1 second on a mid-range phone (measure it) |
| Open the last photo taken | 1 tap on the thumbnail |
| Favourite a photo from the grid | Long-press, tap Favourite: 2 taps |
| Delete 10 photos | Select (long-press, tap 9 more or drag), tap Delete, then Undo is available; no confirmation dialog |
| Find a screenshot by text it contains | Search, type, tap: 3 actions, with results shown as you type |
| Add 20 photos to an album | Select range, Add to Album, choose album: under 6 actions |
| Move a file to another folder | Long-press, Move, choose folder, Done |
| Export an edited photo with no location | Edit, Done, Share with the "Strip location" preset |

### 19.9 How to check it is better

- Run a short usability test with 5 to 8 people on the 8 tasks above. Record success, taps and time, and where they hesitate.
- Track scroll smoothness, camera launch and shutter timing, and crash-free sessions on three device classes (section 10).
- Review every screen in Light, Dark, AMOLED and High Contrast, at 100 and 200 percent font size, in landscape, and on a tablet.
- Do an accessibility pass with TalkBack and with Reduce Motion on.
- Keep the component gallery and screenshot tests so the look cannot drift.

**Phase placement:** do items in 19.1 to 19.2 in Phase 2 (design system and shell), the Photos, Files and Camera flows in Phases 4 to 6, and the 19.7 accessibility pass and 19.9 testing in Phase 11. Ask for screenshots of the current app first, so the agent can confirm which problems are real.

## 20. Icon system

Goal: one consistent, original icon family that looks like Apple's SF Symbols in feel (clean outlines, filled when selected) without using Apple's assets.

### 20.1 Which icon set to use

- **Recommended base:** Phosphor Icons. It has Regular and Fill weights for the same shapes, which gives the outline-to-filled selected state that iOS uses. Lucide and Material Symbols Rounded are good alternatives.
- **Licences:** I believe Phosphor is MIT, Lucide is ISC and Material Symbols is Apache 2.0, but I have not opened their licence files. Have the agent read the licence in the repository and add attribution to the About screen before shipping.
- **Do not use** Apple's SF Symbols or any icon pulled from Apple's or Google's own apps.
- **Mixing sets** is not allowed. If a needed icon is missing, redraw it by hand in the same grid and stroke, and keep it in one `FarIcons` file.

### 20.2 Technical rules

| Rule | Value |
| --- | --- |
| Grid | 24 dp artboard, 2 dp padding |
| Stroke (Regular) | 1.5 to 1.75 dp, round caps and joins, same on every icon |
| Sizes | 20 dp inline, 24 dp toolbars, 28 dp tab bar, 22 dp inside a 44 dp translucent camera circle, 64 dp for empty states |
| Touch target | 48 dp, even when the icon is smaller |
| Format | Convert SVG to Android vector drawables or Compose `ImageVector`, one file per icon, no bitmaps |
| Colour | Always from theme tokens (never hard-coded). Camera icons are white on a dark translucent circle. |
| States | Regular for normal, Fill for selected, active or on. Disabled uses 38 percent opacity. |
| Accessibility | Every icon-only button has a content description with its state ("Favourite, on"). Decorative icons are hidden from screen readers. |
| Right-to-left | Mirror directional icons (back, forward, chevrons, undo and redo). Do not mirror clocks, camera or heart. |
| Motion | Heart pops, flash toggles and the shutter use short (about 150 to 250 ms) animations. They are skipped under Reduce Motion. |

### 20.3 Icon map (concept, then use the nearest icon in the chosen set)

| Area | Action or object | Icon concept (outline, filled when selected) |
| --- | --- | --- |
| Tabs | Photos | Photo frame with a mountain and sun |
| Tabs | Camera | Camera body with a lens circle |
| Tabs | Files | Folder |
| Photos | Library, Albums, Search | Grid of squares, stacked frames, magnifying glass |
| Photos | Favourite | Heart |
| Photos | Info | Circle with an "i" |
| Photos | Share, Edit, Delete | Square with up arrow, sliders, trash can |
| Photos | Add to album | Frames with a plus |
| Photos | Archive, Hide, Vault | Box with down arrow, eye with a slash, padlock on a shield |
| Photos | Rating, Tag, Caption | Star, label, speech lines |
| Photos | Map and Places | Map pin |
| Photos | People and Pets | Face outline, paw print |
| Photos | Memories, On This Day | Sparkle on a frame, calendar with a clock arrow |
| Photos | Slideshow, Movie, Collage, Animation | Play in a frame, film strip, tiled squares, looping arrows |
| Photos | Duplicates, Blurry | Two overlapping frames, a frame with a soft circle |
| Photos | Compare | Two frames side by side |
| Photos | Screenshot, Document, Receipt | Phone outline, page with a folded corner, receipt with zigzag edge |
| Editor | Adjust, Filters, Crop, Rotate, Flip | Sliders, three overlapping circles, crop corners, rotate arrow, mirrored triangles |
| Editor | Light, Colour | Sun, droplet |
| Editor | Retouch, Erase, Markup | Sparkle brush, eraser, pen |
| Editor | Undo, Redo, Revert, Compare | Curved arrows, rotate-back arrow, split frame |
| Video | Play, Pause, Mute, Speed, Trim, Frame | Triangle, two bars, speaker with a slash, gauge, scissors with a bar, camera shutter on a frame |
| Camera | Flash, Torch, Timer | Lightning bolt, flashlight, stopwatch |
| Camera | Aspect ratio, Grid, Level | Rectangle with corners, grid, horizon line with a bubble |
| Camera | Switch camera, Zoom | Two curved arrows around a lens, magnifier with a plus |
| Camera | Motion photo, Burst, Pano, Time-lapse, Slo-mo | Concentric circles, stacked frames, wide frame with arrows, clock with a lens, slow gauge |
| Camera | Scan, Lens mode | Corner brackets, sparkle in brackets |
| Camera | Pro, Night, Portrait | Dial, moon, person with a soft background |
| Files | Folder, File, Favourite folder | Folder, page, folder with a star |
| Files | Grid and list toggle | Four squares, three lines |
| Files | Sort, Filter, More | Up and down arrows with lines, funnel, three dots |
| Files | SD card, USB drive, Internal storage | Memory card, USB plug, phone |
| Files | Archive, Extract | Zipped box, box with an arrow out |
| Files | Storage, Cleaner | Pie or bar chart, broom |
| Files | Trash, Restore | Trash can, curved arrow out of a bin |
| Files | Tags | Coloured dot or tag |
| System | Back, Forward, Close | Chevrons and an X |
| System | Check, Warning, Error, Offline | Check in a circle, triangle, circle with an exclamation, airplane |

### 20.4 File-type and badge icons

- **File types** (one colour per family, flat, rounded square, 24 and 40 dp): folder (blue), image, video, audio, PDF, document, spreadsheet, presentation, text, code, archive, APK, font, unknown. Never rely on colour alone: each has a distinct glyph.
- **Thumbnail badges** (small, on a dark pill): video duration, motion photo, RAW, burst count, panorama, GIF, favourite heart, edited dot, locked padlock.
- **Status chips:** HW (needs hardware) and Model (needs a model) use a small chip icon in settings and info sheets only.

### 20.5 App icon and system icons

- **Launcher:** an Android adaptive icon with a foreground in the 108 dp canvas (keep the artwork inside the central 66 dp), a plain background layer, and a **monochrome layer** so it works with Android 13 and later themed icons.
- **Concept to try (original, not copying Apple's flower or Google's pinwheel):** a rounded square with a lens ring whose lower edge opens into a folder fold. It says camera, photos and files in one mark. Draw three variants, then test them at 48 px, in light and dark, and next to common apps.
- **Colour:** one calm blue or deep teal base with a light accent, clearly different from Google Photos, Apple Photos and the stock gallery colours.
- **Also needed:** notification small icon (white, 24 dp, no colour), Quick Settings tile icon (monochrome), widget header icon, launcher shortcut icons (Camera, Scan, Search, Vault), splash icon, and a 512 px Play Store icon with its own feature graphic.
- **Long-press shortcuts:** Camera, Scan, Search, Vault.
- **Empty-state art:** 64 dp line icons with one accent colour, no illustrations that need translating.

### 20.6 Delivery

Phase 2 delivers the `FarIcons` library, the icon gallery screen (all icons, all themes, all sizes, RTL) and screenshot tests. Later phases may only add icons through that file.

## 21. More features for each app

These go beyond everything in sections 6 and 17. All run locally. Effort: S small, M medium, L large. "Phase" is where they fit in section 11.

### 21.1 Photos

| Feature | What it does | Effort | Phase |
| --- | --- | --- | --- |
| Swipe to Sort | Full-screen cards: swipe one way to keep, the other to move to Trash, with undo. Fast storage cleanup. | M | 4 |
| Screenshot Inbox | Groups screenshots by type (chat, receipt, code, map), lets users archive or delete in bulk, and pulls out links, phone numbers and QR codes | M | 8 |
| Text in Photos | Tap and select text inside any image, copy it, call a number, open a link or add a calendar event | M, Model | 8 |
| Receipt Organiser | Detects receipts, reads date and total locally, and exports a CSV | L, Model | 8 |
| Date and Location Fixer | Shift dates for a batch (for example a camera set to the wrong year) and set or clear location by batch | M | 4 |
| Blur and Redact | Blur faces, number plates or text before sharing, on a copy | M, Model | 7 |
| Cutout | Lift the subject out as a transparent PNG | M, Model | 9 |
| ID and Passport Photo | Crop to standard sizes and print-ready sheets as PDF | S | 7 |
| Contact Sheet and Print PDF | Pages of thumbnails or full-size prints | S | 10 |
| Wallpaper Maker | Crop to the phone's screen with a preview | S | 7 |
| Voice Captions | Record a short voice note stored with the photo | S | 8 |
| Calendar Heatmap | A year view that shows busy days and jumps to a date | S | 4 |
| Event Albums | Suggests albums from time and place clusters (a trip, a party) that users can accept or ignore | M | 8 |
| 360 and Panorama Viewer | Pan and tilt through wide photos and spherical images | M | 4 |
| Ultra HDR Display | Show and preserve HDR gain-map photos on supported screens | M, HW | 7 |
| Auto-Archive Rules | Suggest moving old screenshots to Archive after N days, always with review | S | 8 |
| Handoff Mode | Show one album to someone else with the app locked to it so they cannot browse the rest | M | 10 |
| Year in Review | A yearly summary slideshow made locally | M | 10 |

### 21.2 Camera

| Feature | What it does | Effort | Phase |
| --- | --- | --- | --- |
| Capture to Vault | A mode that saves straight into the encrypted Vault, so nothing appears in the normal library | M | 10 |
| Look Presets | Save your own colour, tone and grain looks and apply them at capture (non-destructive) | M | 9 |
| Palm or Voice Shutter | Open palm or a spoken word starts the timer | M, Model | 9 |
| Group Selfie | Offers the wide front camera if the phone has one, with a face-count guide | S, HW | 6 |
| Video Snapshot | Take a still while recording | S, HW | 6 |
| Interval Timer | Set an interval and a count for time-lapse, with an exposure lock | S | 9 |
| Burst to GIF and Best Frame | Pick the sharpest frame or build a GIF from a burst | M | 9 |
| Stamp Overlay | Optional date, time and place text burned into a copy | S | 7 |
| Metadata Control | Choose per mode whether location and EXIF are written | S | 6 |
| Tripod and Steady Guide | Detects a steady phone and prompts for long-exposure and night modes | S | 9 |
| Clean Lens Hint | Warns when the image looks smudged | M, Model | 9 |
| Scan and Copy Text | In Lens mode, draw over text to copy or translate with an installed language pack | M, Model | 9 |
| Per-Mode Memory | Each mode keeps its own settings | S | 6 |
| Battery and Heat Meter | Small indicator and a warning before heavy modes | S | 6 |
| Mic Choice and Wind Filter | Choose the microphone and reduce wind noise | M, HW | 9 |

### 21.3 Files

| Feature | What it does | Effort | Phase |
| --- | --- | --- | --- |
| Smart Folders | Saved searches that update live (for example "PDFs over 10 MB this month") | M | 5 |
| Tidy Downloads | Rules that suggest moving files by type or age, always with a review | M | 5 |
| Compare Folders | Shows what differs between two folders | M | 5 |
| Folder Mirror | One-way local copy between folders or to an SD card or USB drive, on demand | L | 10 |
| Bulk Convert | Convert images to JPG or WebP, and merge images into a PDF | M | 7 |
| Content Search | Search inside text and PDF files with a local index | L | 8 |
| Storage Map | A treemap of what uses space, tappable down to folders | M | 5 |
| Code and Text Editor | Syntax colours, find and replace, line numbers | M | 5 |
| Hex and Info Viewer | Peek at binary files and properties | S | 5 |
| APK Inspector | Show an APK's package, version and size, and open it with the system installer (no silent install) | S | 5 |
| Pin to Home Screen | Add a folder or file as a launcher shortcut | S | 5 |
| Send to Vault | Move any file into the Vault from the context menu | S | 10 |
| Verify Hash | Compare a file's SHA-256 with a pasted value | S | 5 |
| Paste as File | Save clipboard text or an image as a file | S | 5 |

### 21.4 Across all three

| Feature | What it does | Effort | Phase |
| --- | --- | --- | --- |
| Search Everything | One search that covers photos, text in images and files | L | 8 |
| Universal Tags | The same tags and colour labels on photos and files | M | 4 |
| Save to Far | Appear in the system share sheet so other apps can save into the app's library or Vault | M | 4 |
| Privacy Cover | One tap to blur the screen and hide thumbnails when someone looks over your shoulder | S | 10 |
| Review Reminders | Optional local notifications (monthly cleanup, trash about to purge) | S | 8 |
| Launcher Shortcuts and Widgets | Camera, Scan, Search, Vault; widgets for recent, favourites, memories and a photo of the day | M | 11 |
| Custom Home | Choose and reorder what appears first | S | 2 |
| Health Check | One screen that lists what is slow, full or unprotected (index paused, storage low, no backup in 30 days) | M | 8 |

**Rule:** every feature here follows the same safety rules as the rest of the plan: local only, reviewed deletes, copies instead of overwrites, and a clear fallback when a device cannot support it.

## 22. Naming

### 22.1 What is wrong with the current name

"Far Gallery" describes only the photo viewer. The app is also a camera and a file manager, and "Gallery" is a generic word used by the stock apps it competes with. A better name is short, easy to say, searchable, and not tied to one feature.

### 22.2 App name options

I could not check trademarks, store listings or domains, so treat these as ideas to test, not clearances.

| Option | Why it could work | Risk |
| --- | --- | --- |
| **Far** (recommended) | Keeps your existing identity, very short, works for a camera, photos and files app. Store title: "Far: Photos, Camera and Files". | Very short names are crowded in stores. Also check meaning: I am fairly sure, but not certain, that "far" sounds like the Arabic word for mouse, which matters if you plan Arabic or Urdu versions. |
| Farlight | Suggests light and distance, a more distinctive and searchable variant | Longer; check for existing apps |
| Kept | Says "kept safe, kept private" | Common word, many collisions |
| Offgrid | Directly signals offline | Probably used by other apps; may sound survivalist |
| Hush | Calm, private | Common name, likely taken |

**Recommendation:** keep **Far** as the brand and drop "Gallery". Use a plain descriptor wherever the name appears: "Far, Photos, Camera and Files. All on your phone." That keeps your existing users, fits all three sections and is easy to rank for. If a search shows "Far" is crowded, use **Farlight**.

### 22.3 Section and feature names

Use plain words for the three sections (**Photos**, **Camera**, **Files**) because people search for them. For features, use your own names. Names like Live Photos, Magic Eraser, Night Sight, Top Shot, Best Take, Photographic Styles, Clean Up and Captured by Me are used by Apple or Google for their own products. I am not a lawyer, so confirm with a trademark search, but the safe choice is your own names:

| Reference name in the plan | Suggested name in the app |
| --- | --- |
| Live Photo, Motion Photo | Moments |
| Captured by Me | My Captures |
| Identity Documents | IDs and Documents |
| Magic Eraser, Clean Up | Erase |
| Night Sight | Night |
| Top Shot, Best Take | Best Frame |
| Photographic Styles | Looks |
| Lens or Siri mode | Scan and Identify |
| Operation Centre | Activity |
| Selection Review | Selected |
| Locked Folder, Safe Folder | Vault (one name for both) |
| Free Up Space | Make Room |
| Clean clutter, Storage Cleaner | Tidy |
| On This Day | On This Day (generic, keep) |
| Default App and System Integration | Make Far Your Default |
| Handoff Mode | Show an Album |
| Privacy Cover | Cover |
| Health Check | Check-up |

### 22.4 Naming rules for the UI

- One name per concept everywhere (never "Trash" in one place and "Recycle Bin" in another).
- Verbs for actions ("Move to Trash"), nouns for places ("Trash").
- Under 20 characters for tab labels and chips; sentence case; no jargon like "SAF" or "MediaStore" in the UI.
- Every name must translate cleanly; keep a glossary in the localisation file.

### 22.5 Checks before you commit to a name

1. Search the Play Store, App Store and the web for the exact name and close spellings.
2. Run a trademark search in your market and the markets you will sell in.
3. Check that a domain and social handles are available.
4. Ask speakers of your target languages what it sounds like and means.
5. Test it at launcher size next to the icon and say it aloud on the phone.

**Phase placement:** icons and naming go into Phase 2 (design system), so every later screen uses them. The new features follow the phase column above.

## 23. Navigation and gestures

Icons are in section 20, names in section 22, and screen-level UI and UX in section 19. This section adds the navigation structure and gesture rules that tie them together.

### 23.1 Navigation structure

Keep the single-activity Navigation Compose setup. Each top-level destination keeps its **own back stack**, so switching tabs never loses where you were.

| Destination | Screens inside it | Back button does |
| --- | --- | --- |
| Photos | Library (Years, Months, Days, All), Collections, Albums, Search, Album detail, Viewer, Editor, Memories, Cleanup (Swipe to Sort, Duplicates, Make Room) | Closes the top screen, then returns to Library, then leaves the app |
| Camera | Viewfinder, mode sheets, Scan, Pro, review of last capture | Closes a sheet, then exits the camera to the screen it was opened from |
| Files | Browse, Folder, Search, Preview, Editor, Storage Map, Tidy, Activity | Goes up one folder, then to Browse, then leaves |
| Overlays (any destination) | Settings, Vault (locked), Activity, Make Far Your Default, Onboarding | Closes the overlay and returns exactly where you were |

### 23.2 Back-stack rules

1. **Selection mode comes first:** back clears the selection before it leaves a screen.
2. **Chrome next:** back closes an open sheet, menu, search field or keyboard before it leaves a screen.
3. **Viewer returns to the grid** at the same scroll position, with the item you were viewing in view.
4. **Editor asks only if something changed** ("Discard edits?"), and never loses edits on rotation or process death.
5. **Predictive back:** support Android's back preview animation on every screen that can go back (the viewer shrinks to its thumbnail, sheets slide down, folders slide out). Verify on Android 13 and later and fall back to a plain transition on Android 11 and 12.
6. **Never trap the user:** the camera, viewer and Vault always have a visible close or back control as well as the system gesture.
7. **Back from a deep link** goes to the sensible parent, not out of the app (for example a photo opened from another app goes back to that app, an album shortcut goes back to Albums).

### 23.3 Deep links and incoming intents

- Route every entry (launcher, shortcuts, widgets, notifications, `ACTION_VIEW`, `ACTION_SEND`, `ACTION_IMAGE_CAPTURE`, pick intents) through one `IntentRouter` that validates the URI, checks permissions and opens the right screen with a clean back stack.
- Invalid or unsupported input opens a friendly error screen with a way back, never a crash.
- Pass **IDs, never large lists,** between screens (section 25.6). Selections live in the ViewModel or database, not in navigation arguments.

### 23.4 State restoration

- Save scroll position, selected tab, filters, selection, open album, open folder, edit state, camera mode and search text with saved-state handles. All of it must survive rotation, language change, dark-mode switch and the system killing the process.
- On return from process death, rebuild the screen from saved state and the database, and show a small "Restored" state only if something could not be recovered.

### 23.5 Gesture map

Every gesture has a visible button or a TalkBack custom action as an equal alternative. Avoid custom swipes from the screen's left and right edges, because Android's gesture navigation uses them for Back.

| Where | Gesture | Result |
| --- | --- | --- |
| Grid | Tap | Open item |
| Grid | Long-press | Start selection, with a light haptic |
| Grid | Long-press then drag | Select a range |
| Grid | Tap a date header's check | Select that day |
| Grid | Pinch in and out | Change density; the item under your fingers stays in place |
| Grid | Drag the scrubber | Fast scroll with a month and year bubble |
| Grid | Pull down at the top | Refresh the index |
| Grid | Long-press a thumbnail and hold | Peek preview (release to close, tap to open) |
| Viewer | Swipe sideways | Previous or next item |
| Viewer | Double-tap | Zoom to the tapped point and back |
| Viewer | Pinch and drag | Zoom and pan |
| Viewer | Swipe down | Dismiss with the item shrinking to its thumbnail |
| Viewer | Swipe up | Open the info sheet |
| Viewer | Single tap | Show or hide controls |
| Video | Double-tap left or right half | Skip back or forward 10 seconds |
| Video | Drag along the left or right side | Brightness or volume |
| Video | Press and hold | Play at 2x while held |
| Camera | Tap | Focus (yellow square) |
| Camera | Drag up or down after tapping | Exposure |
| Camera | Long-press | Lock focus and exposure |
| Camera | Pinch | Zoom |
| Camera | Swipe sideways on the preview | Change mode |
| Camera | Hold the shutter | Burst |
| Camera | Volume keys | Shutter, in the foreground only |
| Camera | Swipe down on the sheet handle | Close the controls sheet |
| Editor | Press and hold the image | Show the original (compare) |
| Editor | Double-tap a slider | Reset it |
| Editor | Two-finger tap | Undo |
| Files | Tap, long-press | Open, context menu |
| Files | Swipe a row sideways | Quick actions (Share, Delete) |
| Files | Drag a row onto a folder | Move (large screens) |
| Lists | Pull down | Refresh |
| Anywhere | Shake or double-tap the status area (setting) | Cover: blur the screen |

### 23.6 Large screens, keyboards and accessibility

- Tablets and foldables use a rail or sidebar and two panes. Keep the same back-stack rules in each pane.
- Keyboard shortcuts: arrows to move, space to preview, Enter to open, Delete to trash, Ctrl+A, Ctrl+C, Ctrl+V, Ctrl+Z, Ctrl+F, F2 rename.
- Focus is always visible. TalkBack gets custom actions on thumbnails and rows (Open, Favourite, Select, Share, Delete).
- Switch Access and voice access must reach every control. Test with them.

## 24. Animations, effects and transitions

Motion should explain what happened (where a photo came from, where it went) and never slow the user down.

### 24.1 Motion tokens

| Token | Value | Used for |
| --- | --- | --- |
| Instant | 80 to 100 ms | Pressed states, toggles |
| Fast | 150 ms | Icon changes, chips, fades |
| Standard | 250 ms | Most transitions, sheets |
| Emphasis | 350 ms | Thumbnail to viewer, camera open |
| Easing | Standard decelerate in, accelerate out | Entering and leaving content |
| Spring | Damping ratio about 0.8 to 0.9, medium stiffness | Drag release, snap-back, pinch settle |
| Stagger | 20 to 30 ms per item, at most 6 items | Menu and sheet content |

Define these once in the design system and use only them.

### 24.2 Transition catalogue

| Moment | Transition | Notes |
| --- | --- | --- |
| Grid thumbnail to viewer | Shared element: the thumbnail grows into the full image | Compose shared-element transitions are available in recent Compose versions; confirm the project's version before relying on them |
| Viewer swipe down | The image follows the finger, scales down, fades the black background, then returns to its thumbnail | Releases past a threshold or fast fling dismiss; otherwise spring back |
| Camera thumbnail to viewer | The thumbnail grows into the full photo | Uses the same shared-element helper |
| Tab switch | Crossfade, 150 ms, keep each tab's scroll position | No slide, to keep it calm |
| Camera open and close | Slide up from the bottom with a dim, swipe down to dismiss | Tab bar hides while capturing |
| Bottom sheets | Slide up with a scrim, drag to dismiss | Info, filters, share, move |
| Selection mode | Top bar swaps to Cancel, count, Done; bottom bar slides up; checks scale in | Under 200 ms |
| Adding or removing grid items | Fade and move into place | Only for a few changed items; no animation for bulk loads |
| Trash and Undo | Item shrinks and fades; Undo bar slides in for 8 seconds | Undo reverses the animation |
| Favourite | Heart scales up with a small bounce and a light haptic | About 200 ms |
| Shutter | Brief white flash, shutter ring squeezes, thumbnail bounces | Skipped under Reduce Motion |
| Focus | Yellow square shrinks into place, then fades |  |
| Zoom pill | The selected stop slides and the number counts smoothly |  |
| Mode change | Labels slide, the preview blurs briefly while the camera switches | Hides the camera reconfigure |
| Editor slider | The value bubble follows the thumb; the preview updates live | Render on a downscaled preview |
| Skeletons | A soft shimmer while loading, replaced by a fade-in | No spinner for lists |
| Errors | A short shake on an invalid field, a snackbar slide for messages |  |
| Progress | Smooth determinate bars; indeterminate only when unknown |  |
| Memories | Gentle zoom and pan on photos (Ken Burns) | Pauses when off-screen or on battery saver |

### 24.3 Effects and feedback

- **Glass and blur:** real blur needs Android 12 or later. Because your minimum is Android 11, use a high-opacity solid tint there and on low-end devices or with Reduce Transparency. Keep blur off full-screen scrolling content.
- **Specular edges and soft shadows:** drawn once, not animated.
- **Haptics:** light on selection and mode change, medium on capture and delete, success and failure patterns on save results. One setting turns all of them off.
- **Sound:** optional shutter sound only, off by default except where the law or the phone requires one.
- **Dark-mode and theme change:** a short crossfade, never a flash.

### 24.4 Keep animation from causing lag

1. Animate only transforms and alpha (position, scale, rotation, opacity), not layout size, inside scrolling lists.
2. Never animate dozens of grid cells at once; cap simultaneous animations.
3. Pause animations that are off-screen, backgrounded or under battery saver.
4. Measure frame times and drop effects automatically if the app is janky (section 25.5).
5. Use baseline profiles and macrobenchmarks so transitions stay smooth after every release.
6. Target the display's refresh rate (60, 90 or 120 Hz) and never drop frames during the viewer transition.

### 24.5 Reduce Motion

Read the system animation scale and the app's own setting. When reduced: replace shared-element, spring and parallax effects with short fades (100 to 150 ms), stop Ken Burns and shimmer, keep haptics only if the user enabled them. Information shown by motion must also be shown another way (a label, a check, a message).

## 25. Reliability: errors, crashes, lag and large data

### 25.1 The honest goal

No app can promise it will never crash. The OS can kill an app for memory, a phone can run out of storage mid-write, a vendor camera or codec can fail in native code, and a file can be corrupt in ways nobody predicted. So the requirement is written as something you can build and test:

- **A crash must never lose or damage user data.** Photos, files, albums, tags and edits survive any crash or kill.
- **A crash must never repeat in a loop.** After a crash the app opens again and works.
- **One bad file, one bad provider or one failed feature must not take down the app.** Failures are contained and reported.
- **Targets to measure:** at least 99.9 percent crash-free sessions, near-zero ANRs (the "app not responding" dialog), and zero data-loss bugs in release. Measure them with the local tools below, and, if you distribute through Google Play, with Play Console's Android vitals, which needs no code in the app.

### 25.2 Defence in layers

| Layer | What it does | How |
| --- | --- | --- |
| 1. Prevent | Remove the causes before release | Kotlin null safety with no `!!`, exhaustive `when`, detekt and Android lint as build gates, StrictMode in debug builds, code review rule: every external call is wrapped |
| 2. Contain | A failure stays where it happened | Every call to ContentResolver, SAF, MediaCodec, CameraX, ML Kit and the database goes through a safe wrapper that returns your existing `FarResult` / `FarError` type instead of throwing. Coroutine scopes use a supervisor and a `CoroutineExceptionHandler`. Batch jobs wrap **each item**, so one failure skips that item and the rest continue. |
| 3. Isolate | Native or heavy work cannot kill the UI | Run ML, thumbnail decoding of untrusted files, archive extraction and video transcoding in a separate process (`android:process`) behind a bound service. If that process dies, the app restarts it and marks the file as suspect. |
| 4. Survive | Work continues after a kill | Journal for every long operation, WorkManager with retry and backoff, saved UI state, atomic writes (section 25.7) |
| 5. Recover | The next launch is healthy | Crash-loop guard, Safe Mode, local crash report, quarantine of bad files, restore from journal |
| 6. Learn | Find problems without a network | Local crash and exit logs, `ApplicationExitInfo` (Android 11 and later, so available on your minimum), macrobenchmarks, Play vitals if on Play |

### 25.3 One error model

Your overview already has `FarResult` and `FarError` with codes (for example format and space errors). Extend it to cover everything below, and use it in every layer.

| Error type | Examples | User sees | Recovery action |
| --- | --- | --- | --- |
| Permission | Camera denied, media access limited, SAF grant revoked | Plain explanation | Button to grant or choose again; app keeps working with what it can access |
| Storage | No space, volume removed, read-only | Space needed and available | Free up space, choose another place, retry |
| Provider | Cloud or OEM provider fails, rename unsupported, null cursor | Which location failed | Retry, use a safe fallback, skip |
| Format and corrupt file | Truncated image, unsupported codec, bad archive | "Can't open this file" with its name | Open with another app, skip, add to quarantine |
| Conflict | Name exists, file changed during copy | Choice dialog | Replace (with confirmation), Keep both, Skip, Apply to all |
| Memory | Out of memory risk | Silent where possible: smaller previews, cache cleared | Retry at lower quality; message only if the task cannot finish |
| Hardware | Feature not supported, camera busy | Why it is unavailable | Alternative mode or close other camera apps |
| Model | Model missing, too slow, too hot | Feature unavailable right now | Try later, charge, or turn off |
| Cancelled | User or system stopped it | Nothing, or a summary | Resume from the journal |
| Timeout | Provider or disk is slow | "Taking longer than usual" with Cancel | Keep waiting, cancel, retry |
| Internal | A bug | A calm "Something went wrong" with a Report option | Retry; details saved locally |

Rules: never show stack traces or raw exception text; every error has a title, a cause in one sentence and a next step; partial success is reported ("11 of 12 done, 1 failed"); errors are logged without private data; nothing fails silently.

### 25.4 Crash handling and recovery

1. **Global handler:** install an uncaught-exception handler that writes a sanitized local report (error type, screen, app version, device class, last 20 actions without file names) and then lets the system close the app normally.
2. **Breadcrumbs:** keep a small ring buffer of recent actions, so a report shows what the user was doing.
3. **Crash-loop guard:** count starts that end in a crash within a short window. After three, open in **Safe Mode**.
4. **Safe Mode:** browsing, viewing and Files only. Background indexing, ML, widgets, animations and the camera extras are off, and a banner explains why with buttons to retry normally or clear derived caches. User files, albums and tags are never touched.
5. **Quarantine:** if decoding or processing a file crashes the isolated process twice, add it to a quarantine table and skip it, showing a placeholder and an option to open it with another app. This prevents one corrupt file from causing a crash loop.
6. **Exit reasons:** on launch, read `ApplicationExitInfo` for the previous exit (crash, ANR, low memory, native crash) and record it locally to guide fixes.
7. **After a crash:** show a short, calm message ("Far closed unexpectedly. Your photos and files are safe.") with an optional, user-started export of the report to a file. Nothing is ever sent anywhere.
8. **Camera recovery:** if the camera service fails, release it, show the reason, and let the user retry or leave. If a capture was in progress, check that the file is complete and report or recover it.

### 25.5 Lag and ANR control

- **Main thread rule:** no disk, database, decoding, hashing or network-like waits on the main thread. StrictMode catches violations in debug builds, and a test fails the build if it does.
- **Work priorities:** what is on screen loads first; off-screen requests are cancelled; thumbnail decoding uses a small thread pool (about half the CPU cores); background work runs at low priority.
- **Frame budget:** keep each frame under about 16 ms (or 8 ms at 120 Hz). Use stable keys and immutable models in lists, `derivedStateOf` for derived values, and the Compose compiler reports to find needless recompositions.
- **Startup:** show the first screen from cached data, initialise cameras, ML and the index lazily, and use baseline profiles and R8 to cut cold start.
- **Adaptive quality governor:** watch jank (dropped-frame ratio), thermal state and battery. If the app is struggling, step down in this order: stop optional animations, turn off blur, shrink thumbnails, pause ML and indexing. Step back up when things recover.
- **Search:** debounce by 300 ms, cancel the previous query, and show results progressively.
- **Timeouts:** every provider and disk operation that can hang has a timeout and a cancel button.
- **Memory pressure:** respond to the system's low-memory callbacks by clearing image caches, pausing previews and releasing the camera preview of other screens.

### 25.6 Large-data handling

Test every rule with a synthetic library of 100,000 photos, 5,000 videos and folders with 50,000 files.

| Risk | Rule |
| --- | --- |
| Huge lists | Page everything with Paging 3, keep page sizes moderate, set a maximum number of items held in memory, and never load all rows into a list |
| Large selections | Store selected IDs in the ViewModel or a database table, not in navigation arguments or intents. Do bulk actions in batches of about 100 to 500 with progress, a journal and cancel. |
| Passing data between screens or apps | Android limits how much can go in an intent or saved state (about 1 MB), and going over it throws `TransactionTooLargeException`. Pass IDs only. |
| Sharing many items | Cap one share at a few hundred items; above that, offer a ZIP or a folder export |
| System trash and delete requests | Split big requests into chunks so each system dialog and call stays small |
| SQL limits | Chunk `IN (...)` queries (SQLite has a limit on variables), use indexes and transactions, and avoid `LIKE` scans over large text (use FTS) |
| Big text rows | A database cursor window is small (about 2 MB). Store OCR text in a separate table, cap its length, and use FTS so rows stay small. |
| Large images | Decode at the size needed (downsample), use tiled region decoding for very large images, set a maximum decoded pixel count, and never decode a full-size bitmap just for a thumbnail |
| Many thumbnails | Disk cache with a size cap (default 512 MB, adjustable) and least-recently-used cleanup; memory cache limited to a share of heap |
| Long videos and big files | Stream, never load whole files; hash and copy in chunks; check free space first |
| Large archives | Entry count, total size and compression ratio limits (zip-bomb guards), extract to a temporary folder and move into place when complete |
| Many files in one folder | Load directory listings in pages, sort in the background, show the first page immediately |
| Index growth | Vacuum and checkpoint on idle and charging, show index size in Settings, offer Rebuild |
| Background scans | Resume from checkpoints; process in small transactions; pause on low battery, heat or low storage |
| Memory leaks | LeakCanary in debug builds, and release checks for leaked activities, bitmaps and cursors |

### 25.7 Safe writes

- **New media:** write with the MediaStore pending flag on, finish the stream, verify the size, then clear the flag. A crash leaves an invisible pending entry that a clean-up job removes.
- **Files:** write to a temporary name, verify, then rename into place. Never write over the only copy.
- **Moves:** copy, verify, then delete the source, with the journal recording each step.
- **Database:** use transactions, keep the write-ahead log on, take a snapshot before any migration, and never call `deleteDatabase` as a fix (problem 5).
- **Settings and state:** write atomically, and fall back to defaults if a file is unreadable.
- **Edits and projects:** save the edit stack after every change, so a crash loses at most the last second.

### 25.8 Testing for "does not crash"

| Test | What it proves |
| --- | --- |
| Monkey and random-gesture runs (hundreds of thousands of events) on several devices | No crash from odd input sequences |
| Corrupt-file corpus: truncated and zero-byte images, huge dimensions, odd EXIF, malformed ZIP and PDF, hostile file names, very long paths | Parsers fail safely and files are quarantined |
| Fault injection in the repository layer (provider returns null, throws, hangs, disappears) | Errors are contained and shown properly |
| Process-death test ("Don't keep activities", kill during edit, copy and capture) | State and journals recover |
| Low-memory and low-storage emulation | No crash; graceful degradation |
| Permission revoked mid-task; SD removed mid-copy | Clear errors and safe stops |
| Soak test: 24 hours of camera, scrolling and indexing | No leaks, no overheating spiral, no ANR |
| Large-data test with the 100,000-item library | Smooth scrolling and bounded memory |
| Macrobenchmarks and baseline profiles | Startup, scroll and transition timings do not regress |
| Strict offline test with network disabled | Everything works with no network |

**Release gates:** a release is blocked if any data-loss defect is open, if crash-free sessions in the beta fall below target, if a macrobenchmark regresses past its limit, or if the corrupt-file corpus causes a crash.

## 26. More features, round 2

These add to sections 6, 17 and 21. Effort: S, M, L.

| App | Feature | What it does | Effort |
| --- | --- | --- | --- |
| Photos | Peek Preview | Long-press a thumbnail to peek at the photo with a haptic, release to close | S |
| Photos | Similar Photos Review | Side-by-side review of near-duplicates with a suggested keeper | M |
| Photos | Burst Review | Pick the best frames from a burst and archive the rest | S |
| Photos | Recently Viewed and Pinned | Quick return to what you looked at and pinned photos | S |
| Photos | Edit History | See and restore any earlier version of an edit | M |
| Photos | Before and After Share | Export a split image of the original and edited version | S |
| Camera | Zoom Wheel | A smooth dial for fine zoom between lens stops | M |
| Camera | Exposure and Focus Assist | On-screen peaking and clipping together in Pro | M |
| Camera | Capture Safety Net | If storage is almost full, warns early and offers a lower-quality mode | S |
| Camera | Quick Video Settings | Switch resolution, frame rate and stabilisation from one sheet | S |
| Files | Recent Folders | Jump to folders you used lately | S |
| Files | Compress and Share | Zip the selection and open the share sheet in one step | S |
| Files | Duplicate Name Helper | Smart renaming on conflict ("Report (2).pdf") with a preview | S |
| Files | Folder Size on Demand | Compute a folder's size in the background with a cancel button | S |
| All | Safe Mode | Starts with only basic browsing after repeated crashes (section 25.4) | M |
| All | Local Diagnostics | A user-started report with no private data, saved as a file | S |
| All | Activity Log | A readable history of what the app did to your files (moves, deletes, exports) | M |
| All | Battery and Heat Saver | A mode that turns off effects and background work | S |
| All | Guided Tips | Short, dismissible hints for gestures the first time they apply | S |

## 27. Where this lands in the roadmap

- **Phase 0 and 1:** reliability baseline: the error model, safe wrappers, crash handler, crash-loop guard, Safe Mode, atomic writes, StrictMode, the synthetic large library and the corrupt-file corpus. These start before any new feature.
- **Phase 2:** motion tokens, transitions and the icon library, navigation structure, back-stack and gesture rules.
- **Phases 3 to 6:** every new screen uses the error model, paging and safe writes from day one, and has its own fault-injection and large-data tests.
- **Phases 7 to 11:** the isolated process for ML and transcoding, the adaptive quality governor, soak tests, macrobenchmarks, and the release gates in 25.8.
- **Every phase** ends with the crash, memory and jank numbers in the phase report. A phase is not complete if it adds crashes, data-loss risk or noticeable lag.


---

# PART F — DETAILED IMPLEMENTATION PROMPTS P00–P52

## How to execute the prompt library

- Execute P00 first. P00 and P01 are audit/proposal gates; P01 must stop for owner approval.
- Continue in numeric order unless the audit proves a dependency has already been completed. Even then, verify the implementation and tests before marking a prompt skipped.
- Each prompt is written as a separate copy/paste task. It includes a session contract so it can be used with DeepSeek or Gemini in a fresh session.
- The prompt IDs deliberately group related functionality into safe implementation units. The detailed product behavior is defined in Part E; the task prompts tell the agent how to implement and verify those requirements.
- A phase is not complete merely because the screens exist. The end-to-end behavior, persistence, permissions, failure handling and tests must pass.

### P00 — Repository audit and project truth

**Depends on:** None; first task  
**Mode:** Audit only; do not edit code

```text
SESSION STARTER
You are working on the existing Far Gallery Android application in this repository.
Read APP_OVERVIEW.md and this complete Far Gallery Master Prompt / Build Plan. Read the relevant feature and architecture sections before editing. Inspect the actual source code; documentation is not proof that a feature works. Follow all Global Rules and the Standard Report format in this document. Do not change framework, delete user data, add internet permission, create fake controls, or edit unrelated modules.

TASK — P00: Repository audit and project truth
No code changes. Read APP_OVERVIEW.md and inspect the actual repository, including settings.gradle, Gradle version catalog, app/build.gradle, AndroidManifest.xml, Kotlin source, Room entities/DAOs/migrations, navigation, assets, tests, permissions and generated build output. Build the existing app before changing anything. Produce an evidence-backed matrix for every existing feature: DONE, PARTIAL, MISSING, BROKEN or UNKNOWN, with full file/class/function/test evidence. Verify the eight known defects in the master specification instead of assuming they remain. Capture current UI screenshots if device/emulator is available; otherwise state this limitation. Identify package names, SDK levels, dependency versions, current app name and data model. Do not implement features or change dependencies. Deliver a prioritised risk register, baseline build/test result, exact next-step proposal and questions that genuinely block safe work.

Before coding, list the exact files/classes you will inspect and change, why each is needed, and the tests you will run. If this prompt includes an approval gate, stop and wait for explicit owner approval. Implement in small independently buildable steps. Build and run relevant unit/instrumented/UI tests after each step. If a requirement cannot be completed safely, do not expose a fake UI control; report it as PARTIAL or NOT DONE with the reason.

Finish with the Standard Report. Do not automatically start the next prompt.
```


### P01 — Architecture, migration and implementation proposal

**Depends on:** All earlier prompts in the approved sequence  
**Mode:** Audit only; do not edit code

```text
SESSION STARTER
You are working on the existing Far Gallery Android application in this repository.
Read APP_OVERVIEW.md and this complete Far Gallery Master Prompt / Build Plan. Read the relevant feature and architecture sections before editing. Inspect the actual source code; documentation is not proof that a feature works. Follow all Global Rules and the Standard Report format in this document. Do not change framework, delete user data, add internet permission, create fake controls, or edit unrelated modules.

TASK — P01: Architecture, migration and implementation proposal
No feature implementation. Based on P00 evidence, propose a target architecture that keeps native Kotlin, Jetpack Compose, the existing MVVM/MVI + StateFlow approach, MediaStore for shared media, SAF for arbitrary files, Room for Far-owned metadata, DataStore for preferences, Paging 3, CameraX/Camera2, Media3 and on-device ML. Define canonical asset identity, file-operation journal, background work, Vault isolation, edit sidecars, media search/indexes and Android intent/provider integration. Provide entity/relationship diagrams, migration plan, exact manifest/permission changes, dependency decisions, process boundaries, test strategy, performance budgets and a file-by-file Phase 1 change list. Compare alternatives and explain trade-offs. Stop and wait for owner approval before schema-breaking changes, broad permissions, separate network flavor, new native module or major dependency replacement.

Before coding, list the exact files/classes you will inspect and change, why each is needed, and the tests you will run. If this prompt includes an approval gate, stop and wait for explicit owner approval. Implement in small independently buildable steps. Build and run relevant unit/instrumented/UI tests after each step. If a requirement cannot be completed safely, do not expose a fake UI control; report it as PARTIAL or NOT DONE with the reason.

Finish with the Standard Report. Do not automatically start the next prompt.
```


### P02 — Build reproducibility, Gradle and dependency stabilisation

**Depends on:** All earlier prompts in the approved sequence  
**Mode:** Implement only this task, then build and test

```text
SESSION STARTER
You are working on the existing Far Gallery Android application in this repository.
Read APP_OVERVIEW.md and this complete Far Gallery Master Prompt / Build Plan. Read the relevant feature and architecture sections before editing. Inspect the actual source code; documentation is not proof that a feature works. Follow all Global Rules and the Standard Report format in this document. Do not change framework, delete user data, add internet permission, create fake controls, or edit unrelated modules.

TASK — P02: Build reproducibility, Gradle and dependency stabilisation
Make the existing project build reproducibly without changing product behavior. Audit Kotlin, Gradle, Android Gradle Plugin, Compose compiler/BOM, Room/KSP, CameraX, Media3, Coil, ML Kit and Accompanist versions against compatibility, stability, maintenance, license and APK size. Centralise versions in the existing version catalog. Replace alpha/obsolete dependencies only when a compatible stable version is verified and tested; do not upgrade everything blindly. Remove duplicate libraries and obsolete APIs only when usage is understood. Add a documented local build/test command and ensure debug and release variants compile. Do not add internet permission or telemetry. Report every dependency change with reason, licence, size impact and rollback path.

Before coding, list the exact files/classes you will inspect and change, why each is needed, and the tests you will run. If this prompt includes an approval gate, stop and wait for explicit owner approval. Implement in small independently buildable steps. Build and run relevant unit/instrumented/UI tests after each step. If a requirement cannot be completed safely, do not expose a fake UI control; report it as PARTIAL or NOT DONE with the reason.

Finish with the Standard Report. Do not automatically start the next prompt.
```


### P03 — Reliability foundation and unified error model

**Depends on:** All earlier prompts in the approved sequence  
**Mode:** Implement only this task, then build and test

```text
SESSION STARTER
You are working on the existing Far Gallery Android application in this repository.
Read APP_OVERVIEW.md and this complete Far Gallery Master Prompt / Build Plan. Read the relevant feature and architecture sections before editing. Inspect the actual source code; documentation is not proof that a feature works. Follow all Global Rules and the Standard Report format in this document. Do not change framework, delete user data, add internet permission, create fake controls, or edit unrelated modules.

TASK — P03: Reliability foundation and unified error model
Implement or strengthen a single typed FarResult/FarError contract used by MediaStore, SAF, Room, CameraX, Media3, ML, file decoding and storage operations. Include Permission, Storage, Provider, FormatCorrupt, Conflict, Memory, Hardware, Model, Cancelled, Timeout and Internal categories with safe user messages and recovery actions. Add wrappers that contain exceptions, per-item error isolation for batch jobs, coroutine SupervisorJob handling, debug StrictMode rules, structured local diagnostics without private filenames/content, and graceful empty/limited/error states. Do not expose stack traces in UI. Add fault-injection tests for null cursors, thrown provider calls, timeouts, revoked grants and bad media. Every visible UI error must explain what happened and provide a useful next action.

Before coding, list the exact files/classes you will inspect and change, why each is needed, and the tests you will run. If this prompt includes an approval gate, stop and wait for explicit owner approval. Implement in small independently buildable steps. Build and run relevant unit/instrumented/UI tests after each step. If a requirement cannot be completed safely, do not expose a fake UI control; report it as PARTIAL or NOT DONE with the reason.

Finish with the Standard Report. Do not automatically start the next prompt.
```


### P04 — Database recovery, migrations and metadata preservation

**Depends on:** All earlier prompts in the approved sequence  
**Mode:** Implement only this task, then build and test

```text
SESSION STARTER
You are working on the existing Far Gallery Android application in this repository.
Read APP_OVERVIEW.md and this complete Far Gallery Master Prompt / Build Plan. Read the relevant feature and architecture sections before editing. Inspect the actual source code; documentation is not proof that a feature works. Follow all Global Rules and the Standard Report format in this document. Do not change framework, delete user data, add internet permission, create fake controls, or edit unrelated modules.

TASK — P04: Database recovery, migrations and metadata preservation
Inspect the real Room database and eliminate any recovery path that silently deletes far.db. Design data-preserving migrations, pre-migration metadata snapshots, export/import of user-owned metadata, validation, rollback/recovery and a clear recovery report. Preserve albums, nested album structure, memberships, tags, ratings, captions, edit recipes, search settings, collection preferences and operation journals. Add migration tests for every supported schema version and a corruption-recovery test. Never delete or recreate the database as a routine migration fix. Keep MediaStore/SAF as the source of truth for physical files; Room records must survive a missing media item by marking the reference unavailable rather than recreating a phantom file.

Before coding, list the exact files/classes you will inspect and change, why each is needed, and the tests you will run. If this prompt includes an approval gate, stop and wait for explicit owner approval. Implement in small independently buildable steps. Build and run relevant unit/instrumented/UI tests after each step. If a requirement cannot be completed safely, do not expose a fake UI control; report it as PARTIAL or NOT DONE with the reason.

Finish with the Standard Report. Do not automatically start the next prompt.
```


### P05 — Large-library test fixtures and performance guardrails

**Depends on:** All earlier prompts in the approved sequence  
**Mode:** Implement only this task, then build and test

```text
SESSION STARTER
You are working on the existing Far Gallery Android application in this repository.
Read APP_OVERVIEW.md and this complete Far Gallery Master Prompt / Build Plan. Read the relevant feature and architecture sections before editing. Inspect the actual source code; documentation is not proof that a feature works. Follow all Global Rules and the Standard Report format in this document. Do not change framework, delete user data, add internet permission, create fake controls, or edit unrelated modules.

TASK — P05: Large-library test fixtures and performance guardrails
Create deterministic synthetic test data for at least 100,000 photo records, 5,000 videos and file trees with 50,000 entries, plus corrupt/zero-byte/huge-dimension media and unreliable SAF providers. Remove test-only hardcoded small limits from production code. Enforce Paging 3 for visible lists, cursor chunking for analysis, bounded memory, batched selections, chunked SQL IN lists, FTS instead of broad LIKE scans, thumbnail downsampling and bounded LRU caches. Store selected IDs outside navigation arguments and intents. Add performance tests/benchmarks for cold start, library scroll, query latency, thumbnail memory, bulk operations and indexing. Document device-class budgets and ensure the first page renders without waiting for a full scan.

Before coding, list the exact files/classes you will inspect and change, why each is needed, and the tests you will run. If this prompt includes an approval gate, stop and wait for explicit owner approval. Implement in small independently buildable steps. Build and run relevant unit/instrumented/UI tests after each step. If a requirement cannot be completed safely, do not expose a fake UI control; report it as PARTIAL or NOT DONE with the reason.

Finish with the Standard Report. Do not automatically start the next prompt.
```


### P06 — Canonical asset identity and Room data model

**Depends on:** All earlier prompts in the approved sequence  
**Mode:** Implement only this task, then build and test

```text
SESSION STARTER
You are working on the existing Far Gallery Android application in this repository.
Read APP_OVERVIEW.md and this complete Far Gallery Master Prompt / Build Plan. Read the relevant feature and architecture sections before editing. Inspect the actual source code; documentation is not proof that a feature works. Follow all Global Rules and the Standard Report format in this document. Do not change framework, delete user data, add internet permission, create fake controls, or edit unrelated modules.

TASK — P06: Canonical asset identity and Room data model
Implement the approved unified media identity so one physical photo/video has one canonical asset record shared by Photos, Camera and Files views. Use MediaStore URIs and volume/id identity for shared media and SAF document URIs plus persisted grants for arbitrary files. Define stable keys, source/provider, mime/type, size, timestamps (captured vs added vs modified), dimensions, duration, orientation, EXIF references, provenance, favourite/archive/hidden/trashed/locked flags, rating, tags, caption, alt text, location, hashes, edit state, derived analysis state and permissions. Store only Far-owned metadata in Room. Add indexes, foreign keys, transactions and migrations; don't store full bitmaps or duplicate file bytes in Room. Test that the same capture appears once in all sections.

Before coding, list the exact files/classes you will inspect and change, why each is needed, and the tests you will run. If this prompt includes an approval gate, stop and wait for explicit owner approval. Implement in small independently buildable steps. Build and run relevant unit/instrumented/UI tests after each step. If a requirement cannot be completed safely, do not expose a fake UI control; report it as PARTIAL or NOT DONE with the reason.

Finish with the Standard Report. Do not automatically start the next prompt.
```


### P07 — Incremental media scanner and folder indexing controls

**Depends on:** All earlier prompts in the approved sequence  
**Mode:** Implement only this task, then build and test

```text
SESSION STARTER
You are working on the existing Far Gallery Android application in this repository.
Read APP_OVERVIEW.md and this complete Far Gallery Master Prompt / Build Plan. Read the relevant feature and architecture sections before editing. Inspect the actual source code; documentation is not proof that a feature works. Follow all Global Rules and the Standard Report format in this document. Do not change framework, delete user data, add internet permission, create fake controls, or edit unrelated modules.

TASK — P07: Incremental media scanner and folder indexing controls
Implement an incremental, resumable scanner that discovers device photos/videos using MediaStore and user-selected SAF folders using DocumentsContract/DocumentFile. Group by date, folder/album and media type; detect additions, changes, moves and removals without duplicating records. Use content observers where reliable, checkpoints, chunked transactions and a manual refresh path. Add first-run explanation, full/partial/denied access states, include/exclude folder management, per-folder controls for timeline, OCR, AI, memories and widgets, and settings to pause automatic scanning, rebuild index and clear thumbnail cache. Avoid scanning app-private or excluded folders. Report progress truthfully and let users cancel.

Before coding, list the exact files/classes you will inspect and change, why each is needed, and the tests you will run. If this prompt includes an approval gate, stop and wait for explicit owner approval. Implement in small independently buildable steps. Build and run relevant unit/instrumented/UI tests after each step. If a requirement cannot be completed safely, do not expose a fake UI control; report it as PARTIAL or NOT DONE with the reason.

Finish with the Standard Report. Do not automatically start the next prompt.
```


### P08 — Background indexing, OCR scheduling and Activity integration

**Depends on:** All earlier prompts in the approved sequence  
**Mode:** Implement only this task, then build and test

```text
SESSION STARTER
You are working on the existing Far Gallery Android application in this repository.
Read APP_OVERVIEW.md and this complete Far Gallery Master Prompt / Build Plan. Read the relevant feature and architecture sections before editing. Inspect the actual source code; documentation is not proof that a feature works. Follow all Global Rules and the Standard Report format in this document. Do not change framework, delete user data, add internet permission, create fake controls, or edit unrelated modules.

TASK — P08: Background indexing, OCR scheduling and Activity integration
Move long-running OCR and indexing from foreground-only batches to WorkManager with resumable checkpoints, constraints for charging/idle/battery/thermal state, backoff, unique work and user pause/resume/cancel. Show job state in a shared Activity/Operation Centre. Avoid indexing Vault items or excluded folders. Keep OCR text separate from asset rows and searchable through a proper FTS index with length caps. On new captures/imports, schedule indexing without blocking the UI. Handle process death, reboot, revoked permissions and device low-storage conditions. Provide a foreground-only manual option for users who disable background work. Do not show fabricated progress; progress must reflect completed work.

Before coding, list the exact files/classes you will inspect and change, why each is needed, and the tests you will run. If this prompt includes an approval gate, stop and wait for explicit owner approval. Implement in small independently buildable steps. Build and run relevant unit/instrumented/UI tests after each step. If a requirement cannot be completed safely, do not expose a fake UI control; report it as PARTIAL or NOT DONE with the reason.

Finish with the Standard Report. Do not automatically start the next prompt.
```


### P09 — Incoming URI resolver and external intent safety

**Depends on:** All earlier prompts in the approved sequence  
**Mode:** Implement only this task, then build and test

```text
SESSION STARTER
You are working on the existing Far Gallery Android application in this repository.
Read APP_OVERVIEW.md and this complete Far Gallery Master Prompt / Build Plan. Read the relevant feature and architecture sections before editing. Inspect the actual source code; documentation is not proof that a feature works. Follow all Global Rules and the Standard Report format in this document. Do not change framework, delete user data, add internet permission, create fake controls, or edit unrelated modules.

TASK — P09: Incoming URI resolver and external intent safety
Harden IncomingMediaResolver for external ACTION_VIEW, ACTION_SEND, ACTION_GET_CONTENT, ACTION_PICK and capture result flows. Support valid provider-backed content URIs, persistable grants when offered, MIME verification and safe streaming; do not trust display names, file paths or custom schemes. Handle read/write grants, temporary URI lifetime, cancellation, very large files and provider failures. Never assume `/storage/emulated/0` is writable. Add actionable messages and tests for third-party providers, null metadata, unsupported MIME, revoked grants, file-too-large and malformed inputs. Preserve the original incoming URI and avoid copying unless a destination or edit operation explicitly requires it.

Before coding, list the exact files/classes you will inspect and change, why each is needed, and the tests you will run. If this prompt includes an approval gate, stop and wait for explicit owner approval. Implement in small independently buildable steps. Build and run relevant unit/instrumented/UI tests after each step. If a requirement cannot be completed safely, do not expose a fake UI control; report it as PARTIAL or NOT DONE with the reason.

Finish with the Standard Report. Do not automatically start the next prompt.
```


### P10 — Atomic file operations and operation journal

**Depends on:** All earlier prompts in the approved sequence  
**Mode:** Implement only this task, then build and test

```text
SESSION STARTER
You are working on the existing Far Gallery Android application in this repository.
Read APP_OVERVIEW.md and this complete Far Gallery Master Prompt / Build Plan. Read the relevant feature and architecture sections before editing. Inspect the actual source code; documentation is not proof that a feature works. Follow all Global Rules and the Standard Report format in this document. Do not change framework, delete user data, add internet permission, create fake controls, or edit unrelated modules.

TASK — P10: Atomic file operations and operation journal
Implement a shared, crash-safe file operation engine for copy, move, rename, import, export, extraction and batch actions. Journal each operation with IDs, source/destination, state, progress, timestamps, conflict decisions and recovery checkpoint. Copy in streams/chunks, verify size and preferably content hash, then delete a move source only after destination verification. Write to temporary/pending destinations and atomically finalise where provider APIs allow. Add pause/cancel/retry/resume, process-death recovery, per-item failures and summaries. Conflict UI must offer Replace with confirmation, Keep both with a previewed generated name, Skip and Apply to all. No operation may falsely report success.

Before coding, list the exact files/classes you will inspect and change, why each is needed, and the tests you will run. If this prompt includes an approval gate, stop and wait for explicit owner approval. Implement in small independently buildable steps. Build and run relevant unit/instrumented/UI tests after each step. If a requirement cannot be completed safely, do not expose a fake UI control; report it as PARTIAL or NOT DONE with the reason.

Finish with the Standard Report. Do not automatically start the next prompt.
```


### P11 — iOS-inspired design tokens, themes and materials

**Depends on:** All earlier prompts in the approved sequence  
**Mode:** Implement only this task, then build and test

```text
SESSION STARTER
You are working on the existing Far Gallery Android application in this repository.
Read APP_OVERVIEW.md and this complete Far Gallery Master Prompt / Build Plan. Read the relevant feature and architecture sections before editing. Inspect the actual source code; documentation is not proof that a feature works. Follow all Global Rules and the Standard Report format in this document. Do not change framework, delete user data, add internet permission, create fake controls, or edit unrelated modules.

TASK — P11: iOS-inspired design tokens, themes and materials
Evolve the existing glass theme rather than replacing it wholesale. Create a calm, minimal iOS 27-inspired design language for Android: neutral backgrounds, blue primary/selection, yellow camera/focus, red destructive/recording, semantic text/surface tokens, subtle dividers, 8dp spacing, large collapsing titles, 10–16dp surfaces and 2–4dp dense thumbnail corners. Support System, Light, Dark, AMOLED and High Contrast. Keep the current neon palettes as optional, not default. Use blur only where Android version/GPU permits; solid high-opacity fallback under Reduce Transparency or poor performance. Support system font scale up to 200%, RTL and safe areas. Build a token gallery and screenshot tests.

Before coding, list the exact files/classes you will inspect and change, why each is needed, and the tests you will run. If this prompt includes an approval gate, stop and wait for explicit owner approval. Implement in small independently buildable steps. Build and run relevant unit/instrumented/UI tests after each step. If a requirement cannot be completed safely, do not expose a fake UI control; report it as PARTIAL or NOT DONE with the reason.

Finish with the Standard Report. Do not automatically start the next prompt.
```


### P12 — Icon family, app icon and reusable UI component gallery

**Depends on:** All earlier prompts in the approved sequence  
**Mode:** Implement only this task, then build and test

```text
SESSION STARTER
You are working on the existing Far Gallery Android application in this repository.
Read APP_OVERVIEW.md and this complete Far Gallery Master Prompt / Build Plan. Read the relevant feature and architecture sections before editing. Inspect the actual source code; documentation is not proof that a feature works. Follow all Global Rules and the Standard Report format in this document. Do not change framework, delete user data, add internet permission, create fake controls, or edit unrelated modules.

TASK — P12: Icon family, app icon and reusable UI component gallery
Choose one open-licensed icon family (Phosphor, Lucide or Material Symbols Rounded) after checking the repository licence; do not use Apple SF Symbols/SF Pro or proprietary Google icons. Centralise icons in FarIcons with consistent regular/filled states, 24dp grid, directional RTL mirroring and accessibility descriptions. Design an original launcher adaptive icon, monochrome themed icon, splash mark, notification/tile icons, widget icon, shortcut icons and empty-state artwork. Build reusable GlassNavBar, GlassTabBar, LargeTitleHeader, search field, filter chips, selection bar, context menu, sheet, toast/undo, progress, MediaGridCell, date header, scrubber, ZoomableImage, VideoControls, SliderRow, ModeSelector, ShutterButton and FocusReticle. Add a component gallery across all themes.

Before coding, list the exact files/classes you will inspect and change, why each is needed, and the tests you will run. If this prompt includes an approval gate, stop and wait for explicit owner approval. Implement in small independently buildable steps. Build and run relevant unit/instrumented/UI tests after each step. If a requirement cannot be completed safely, do not expose a fake UI control; report it as PARTIAL or NOT DONE with the reason.

Finish with the Standard Report. Do not automatically start the next prompt.
```


### P13 — Three-destination app shell and adaptive navigation

**Depends on:** All earlier prompts in the approved sequence  
**Mode:** Implement only this task, then build and test

```text
SESSION STARTER
You are working on the existing Far Gallery Android application in this repository.
Read APP_OVERVIEW.md and this complete Far Gallery Master Prompt / Build Plan. Read the relevant feature and architecture sections before editing. Inspect the actual source code; documentation is not proof that a feature works. Follow all Global Rules and the Standard Report format in this document. Do not change framework, delete user data, add internet permission, create fake controls, or edit unrelated modules.

TASK — P13: Three-destination app shell and adaptive navigation
Refactor only after audit to three top-level destinations: Photos (default), Camera and Files. Move Library, Collections, Albums and Search into Photos secondary navigation. Each top-level destination retains its own back stack and scroll position. Compact phones use a translucent three-item bottom bar; Camera opens immersive/full-screen and hides/minimises the bar while capturing. Tablets/foldables use rail/sidebar and two-pane layouts. Implement correct back order: clear selection, close menus/sheets/search/keyboard, then navigate. Add predictive back where supported, deep-link state restoration, rotation/window resizing and visible close controls in Camera, Viewer and Vault. Keep existing routes/deep links working or migrate safely.

Before coding, list the exact files/classes you will inspect and change, why each is needed, and the tests you will run. If this prompt includes an approval gate, stop and wait for explicit owner approval. Implement in small independently buildable steps. Build and run relevant unit/instrumented/UI tests after each step. If a requirement cannot be completed safely, do not expose a fake UI control; report it as PARTIAL or NOT DONE with the reason.

Finish with the Standard Report. Do not automatically start the next prompt.
```


### P14 — Onboarding, permission education and permission-state UX

**Depends on:** All earlier prompts in the approved sequence  
**Mode:** Implement only this task, then build and test

```text
SESSION STARTER
You are working on the existing Far Gallery Android application in this repository.
Read APP_OVERVIEW.md and this complete Far Gallery Master Prompt / Build Plan. Read the relevant feature and architecture sections before editing. Inspect the actual source code; documentation is not proof that a feature works. Follow all Global Rules and the Standard Report format in this document. Do not change framework, delete user data, add internet permission, create fake controls, or edit unrelated modules.

TASK — P14: Onboarding, permission education and permission-state UX
Create a calm first-run flow with separate Photos/Media, Camera and Files access cards. Explain exactly why each permission is needed, request it just in time, support Android 13+ granular media access and Android 14+ selected-photo access, partial grants, denial and later revocation. Users can skip and still open the app. For arbitrary folders use SAF tree picker and persist grants. Do not request MANAGE_EXTERNAL_STORAGE unless a justified, approved distribution requirement is established; prefer SAF and MediaStore. Add limited-access banners, Manage Access actions, settings deep links and tests for allow/deny/partial/revoke.

Before coding, list the exact files/classes you will inspect and change, why each is needed, and the tests you will run. If this prompt includes an approval gate, stop and wait for explicit owner approval. Implement in small independently buildable steps. Build and run relevant unit/instrumented/UI tests after each step. If a requirement cannot be completed safely, do not expose a fake UI control; report it as PARTIAL or NOT DONE with the reason.

Finish with the Standard Report. Do not automatically start the next prompt.
```


### P15 — Photos Library timeline, date navigation and grid controls

**Depends on:** All earlier prompts in the approved sequence  
**Mode:** Implement only this task, then build and test

```text
SESSION STARTER
You are working on the existing Far Gallery Android application in this repository.
Read APP_OVERVIEW.md and this complete Far Gallery Master Prompt / Build Plan. Read the relevant feature and architecture sections before editing. Inspect the actual source code; documentation is not proof that a feature works. Follow all Global Rules and the Standard Report format in this document. Do not change framework, delete user data, add internet permission, create fake controls, or edit unrelated modules.

TASK — P15: Photos Library timeline, date navigation and grid controls
Implement the main Photos Library as a fast, content-first MediaStore timeline with sticky date headers, thumbnails, video duration and Motion Photo/RAW/GIF badges, item count and active-filter indicator. Add Years, Months, Days and All Photos views; date scrubber with month/year bubble and calendar jump; pinch grid density with item-under-fingers anchoring; compact/comfortable control; fast scrollbar; pull-to-refresh and scroll-to-top. Sort by Date Captured or Date Added and preserve the user's choice. Grid uses stable paging keys and cached thumbnails. Provide useful first-run, empty, limited-permission, indexing and error states. Tapping a date header can select its group.

Before coding, list the exact files/classes you will inspect and change, why each is needed, and the tests you will run. If this prompt includes an approval gate, stop and wait for explicit owner approval. Implement in small independently buildable steps. Build and run relevant unit/instrumented/UI tests after each step. If a requirement cannot be completed safely, do not expose a fake UI control; report it as PARTIAL or NOT DONE with the reason.

Finish with the Standard Report. Do not automatically start the next prompt.
```


### P16 — Collections, media types and smart albums

**Depends on:** All earlier prompts in the approved sequence  
**Mode:** Implement only this task, then build and test

```text
SESSION STARTER
You are working on the existing Far Gallery Android application in this repository.
Read APP_OVERVIEW.md and this complete Far Gallery Master Prompt / Build Plan. Read the relevant feature and architecture sections before editing. Inspect the actual source code; documentation is not proof that a feature works. Follow all Global Rules and the Standard Report format in this document. Do not change framework, delete user data, add internet permission, create fake controls, or edit unrelated modules.

TASK — P16: Collections, media types and smart albums
Build a Collections screen with reorderable, pinnable, collapsible sections: My Albums, People & Pets, Places, Media Types, Memories and Utilities. Provide generated smart collections for screenshots, videos, selfies, panoramas, bursts, RAW, GIFs, documents, downloads, favourites, edited, recently added, recently viewed, duplicates, blurry items and My Captures. Smart albums are saved query definitions, not copied folders; update when media changes. Add Captured by Me/My Captures using provenance written at capture time, not folder-name inference. Add IDs and Documents as an opt-in, locked-by-default collection with clear privacy explanation. Let the user hide and reorder sections.

Before coding, list the exact files/classes you will inspect and change, why each is needed, and the tests you will run. If this prompt includes an approval gate, stop and wait for explicit owner approval. Implement in small independently buildable steps. Build and run relevant unit/instrumented/UI tests after each step. If a requirement cannot be completed safely, do not expose a fake UI control; report it as PARTIAL or NOT DONE with the reason.

Finish with the Standard Report. Do not automatically start the next prompt.
```


### P17 — Album and album-folder management

**Depends on:** All earlier prompts in the approved sequence  
**Mode:** Implement only this task, then build and test

```text
SESSION STARTER
You are working on the existing Far Gallery Android application in this repository.
Read APP_OVERVIEW.md and this complete Far Gallery Master Prompt / Build Plan. Read the relevant feature and architecture sections before editing. Inspect the actual source code; documentation is not proof that a feature works. Follow all Global Rules and the Standard Report format in this document. Do not change framework, delete user data, add internet permission, create fake controls, or edit unrelated modules.

TASK — P17: Album and album-folder management
Implement create, rename, delete, pin, favourite, sort, move, copy, reorder, nest albums into album folders, choose a cover and manage membership. Confirm what Delete Album means: remove the virtual album only, never delete its media unless a separate explicit action is chosen. Support adding/removing items from album, batch operations, empty albums, duplicate membership and album export. Show a polished two-column cover grid on phones and adaptive list/sidebar on large screens. Use Room membership records without duplicating media files. Persist custom order and restore it after restart/migration.

Before coding, list the exact files/classes you will inspect and change, why each is needed, and the tests you will run. If this prompt includes an approval gate, stop and wait for explicit owner approval. Implement in small independently buildable steps. Build and run relevant unit/instrumented/UI tests after each step. If a requirement cannot be completed safely, do not expose a fake UI control; report it as PARTIAL or NOT DONE with the reason.

Finish with the Standard Report. Do not automatically start the next prompt.
```


### P18 — Selection, favourites, ratings, tags, archive and hidden media

**Depends on:** All earlier prompts in the approved sequence  
**Mode:** Implement only this task, then build and test

```text
SESSION STARTER
You are working on the existing Far Gallery Android application in this repository.
Read APP_OVERVIEW.md and this complete Far Gallery Master Prompt / Build Plan. Read the relevant feature and architecture sections before editing. Inspect the actual source code; documentation is not proof that a feature works. Follow all Global Rules and the Standard Report format in this document. Do not change framework, delete user data, add internet permission, create fake controls, or edit unrelated modules.

TASK — P18: Selection, favourites, ratings, tags, archive and hidden media
Implement selection mode from long-press, drag-range selection and date-header selection. Top bar becomes Cancel / X Selected / Done; tapping the count opens Selection Review listing all selected items across paging, filters and scroll with per-item deselect. Primary bottom actions: Share, Add to Album, Favourite and Delete; More includes move, copy, archive, hide, tag, rate, batch rename/convert and export. Add one-to-five-star rating and Unrated filters, universal tags/colour labels, keywords/captions/alt text, archive and hidden views. Keep hidden and Vault media out of normal search/widgets/recents. Every batch action is journalled, cancellable and reports partial success.

Before coding, list the exact files/classes you will inspect and change, why each is needed, and the tests you will run. If this prompt includes an approval gate, stop and wait for explicit owner approval. Implement in small independently buildable steps. Build and run relevant unit/instrumented/UI tests after each step. If a requirement cannot be completed safely, do not expose a fake UI control; report it as PARTIAL or NOT DONE with the reason.

Finish with the Standard Report. Do not automatically start the next prompt.
```


### P19 — Full-screen viewer, details sheet, compare and Peek Preview

**Depends on:** All earlier prompts in the approved sequence  
**Mode:** Implement only this task, then build and test

```text
SESSION STARTER
You are working on the existing Far Gallery Android application in this repository.
Read APP_OVERVIEW.md and this complete Far Gallery Master Prompt / Build Plan. Read the relevant feature and architecture sections before editing. Inspect the actual source code; documentation is not proof that a feature works. Follow all Global Rules and the Standard Report format in this document. Do not change framework, delete user data, add internet permission, create fake controls, or edit unrelated modules.

TASK — P19: Full-screen viewer, details sheet, compare and Peek Preview
Build a fullscreen viewer with black adaptive canvas, hero transition from grid, swipe previous/next, pinch/pan, double-tap zoom to tapped point, rotate, single-tap chrome toggle, swipe-down dismiss and optional thumbnail filmstrip. Show only core actions (Favourite, Share, Edit, Delete); move histogram and advanced metadata into Info. Info sheet includes captured/added date, dimensions, size, file type, EXIF, location/map when available, tags, OCR text, provenance and edit history. Add side-by-side compare with synced pan/zoom, Keep Left/Right review that only moves rejected items to Trash after clear confirmation, and long-press Peek Preview. Support very large images via region/tile decoding.

Before coding, list the exact files/classes you will inspect and change, why each is needed, and the tests you will run. If this prompt includes an approval gate, stop and wait for explicit owner approval. Implement in small independently buildable steps. Build and run relevant unit/instrumented/UI tests after each step. If a requirement cannot be completed safely, do not expose a fake UI control; report it as PARTIAL or NOT DONE with the reason.

Finish with the Standard Report. Do not automatically start the next prompt.
```


### P20 — Video player, resume, frame capture and audio controls

**Depends on:** All earlier prompts in the approved sequence  
**Mode:** Implement only this task, then build and test

```text
SESSION STARTER
You are working on the existing Far Gallery Android application in this repository.
Read APP_OVERVIEW.md and this complete Far Gallery Master Prompt / Build Plan. Read the relevant feature and architecture sections before editing. Inspect the actual source code; documentation is not proof that a feature works. Follow all Global Rules and the Standard Report format in this document. Do not change framework, delete user data, add internet permission, create fake controls, or edit unrelated modules.

TASK — P20: Video player, resume, frame capture and audio controls
Polish Media3 playback with play/pause, seek, mute, volume, fullscreen, ±10-second skip, supported playback speeds, subtitles when supported, picture-in-picture where valid, and resume-from-last-position with a small resume hint. Double-tap left/right sides skips; press-and-hold may temporarily play at 2x. Save Video Frame lets the user pick JPEG/PNG and destination, writes a copy and opens it in Photos. Store playback positions locally without tracking sensitive Vault items. Handle unsupported codecs, audio-only files, corrupt media, rotation, background/foreground and resource release. Preserve current playback when navigating away if supported.

Before coding, list the exact files/classes you will inspect and change, why each is needed, and the tests you will run. If this prompt includes an approval gate, stop and wait for explicit owner approval. Implement in small independently buildable steps. Build and run relevant unit/instrumented/UI tests after each step. If a requirement cannot be completed safely, do not expose a fake UI control; report it as PARTIAL or NOT DONE with the reason.

Finish with the Standard Report. Do not automatically start the next prompt.
```


### P21 — Unified search, advanced filters and natural-language query parsing

**Depends on:** All earlier prompts in the approved sequence  
**Mode:** Implement only this task, then build and test

```text
SESSION STARTER
You are working on the existing Far Gallery Android application in this repository.
Read APP_OVERVIEW.md and this complete Far Gallery Master Prompt / Build Plan. Read the relevant feature and architecture sections before editing. Inspect the actual source code; documentation is not proof that a feature works. Follow all Global Rules and the Standard Report format in this document. Do not change framework, delete user data, add internet permission, create fake controls, or edit unrelated modules.

TASK — P21: Unified search, advanced filters and natural-language query parsing
Create a single local search interface for filename, folder, album, date captured/added, type, size, duration, dimensions, camera/lens/ISO/focal length, favourite, rating, edited state, tags, caption, location, OCR, object labels and document text. Provide filter chips and advanced query builder. Natural-language input must parse locally into visible editable filter chips; show exactly what was understood and allow correction. Search results state why each item matched (filename, OCR, tag, EXIF, scene label, folder). Add recent and saved searches, debounce, progressive results, cancellation and FTS/index status. No server, cloud search, Google Lens or network lookup.

Before coding, list the exact files/classes you will inspect and change, why each is needed, and the tests you will run. If this prompt includes an approval gate, stop and wait for explicit owner approval. Implement in small independently buildable steps. Build and run relevant unit/instrumented/UI tests after each step. If a requirement cannot be completed safely, do not expose a fake UI control; report it as PARTIAL or NOT DONE with the reason.

Finish with the Standard Report. Do not automatically start the next prompt.
```


### P22 — OCR, document intelligence and text actions

**Depends on:** All earlier prompts in the approved sequence  
**Mode:** Implement only this task, then build and test

```text
SESSION STARTER
You are working on the existing Far Gallery Android application in this repository.
Read APP_OVERVIEW.md and this complete Far Gallery Master Prompt / Build Plan. Read the relevant feature and architecture sections before editing. Inspect the actual source code; documentation is not proof that a feature works. Follow all Global Rules and the Standard Report format in this document. Do not change framework, delete user data, add internet permission, create fake controls, or edit unrelated modules.

TASK — P22: OCR, document intelligence and text actions
Deliver on-device OCR for text in photos/screenshots and the document scanner pipeline's text layer. Users can select/copy text, open detected phone numbers/links, create contacts/calendar items only after confirmation, and export recognized text. Group receipts, IDs, documents, screenshots and notes. Receipt extraction can identify merchant/date/total but must show extracted fields for correction and label uncertain values. OCR is opt-in per folder, never runs on Vault or excluded folders, and supports pause/delete-derived-data controls. Test multilingual packs only when bundled and available. OCR failure must not prevent viewing the image.

Before coding, list the exact files/classes you will inspect and change, why each is needed, and the tests you will run. If this prompt includes an approval gate, stop and wait for explicit owner approval. Implement in small independently buildable steps. Build and run relevant unit/instrumented/UI tests after each step. If a requirement cannot be completed safely, do not expose a fake UI control; report it as PARTIAL or NOT DONE with the reason.

Finish with the Standard Report. Do not automatically start the next prompt.
```


### P23 — Duplicates, similar photos, burst review and blur detection

**Depends on:** All earlier prompts in the approved sequence  
**Mode:** Implement only this task, then build and test

```text
SESSION STARTER
You are working on the existing Far Gallery Android application in this repository.
Read APP_OVERVIEW.md and this complete Far Gallery Master Prompt / Build Plan. Read the relevant feature and architecture sections before editing. Inspect the actual source code; documentation is not proof that a feature works. Follow all Global Rules and the Standard Report format in this document. Do not change framework, delete user data, add internet permission, create fake controls, or edit unrelated modules.

TASK — P23: Duplicates, similar photos, burst review and blur detection
Build two-pass exact duplicate scanning (size grouping then streaming SHA-256) with chunked resumable scans and no 2k/10k limits. Add visually similar-photo detection as a separate approximate category; do not conflate it with exact duplicates. Group burst sequences and near-duplicates into collapsible stacks with suggested Best Frame based on sharpness/exposure/face quality, while retaining all source frames. Add blurry-photo review, Swipe to Sort keep/trash with undo, Similar Photos Review, Burst Review and Screenshot Inbox where applicable. Show why each item is suggested, size/resolution and keeper choice. Never auto-delete or silently pick a winner.

Before coding, list the exact files/classes you will inspect and change, why each is needed, and the tests you will run. If this prompt includes an approval gate, stop and wait for explicit owner approval. Implement in small independently buildable steps. Build and run relevant unit/instrumented/UI tests after each step. If a requirement cannot be completed safely, do not expose a fake UI control; report it as PARTIAL or NOT DONE with the reason.

Finish with the Standard Report. Do not automatically start the next prompt.
```


### P24 — On-device scene, object, people and pet intelligence

**Depends on:** All earlier prompts in the approved sequence  
**Mode:** Implement only this task, then build and test

```text
SESSION STARTER
You are working on the existing Far Gallery Android application in this repository.
Read APP_OVERVIEW.md and this complete Far Gallery Master Prompt / Build Plan. Read the relevant feature and architecture sections before editing. Inspect the actual source code; documentation is not proof that a feature works. Follow all Global Rules and the Standard Report format in this document. Do not change framework, delete user data, add internet permission, create fake controls, or edit unrelated modules.

TASK — P24: On-device scene, object, people and pet intelligence
Implement optional bundled local ML for scene/object categories (food, cars, animals, plants, nature, buildings, vehicles, documents, receipts, landscapes), face clustering and pet grouping. Include model licence/provenance, runtime size, device-tier checks, RAM/thermal/battery budgets and CPU fallback or unavailable state. People/pet groups can be named, merged, split, hidden or corrected by the user. Face recognition is opt-in, locally stored, deletable and never sent off-device. No identity matching claims beyond what model supports; do not label unknown people. Keep AI output editable and clearly distinguish inferred labels from user tags.

Before coding, list the exact files/classes you will inspect and change, why each is needed, and the tests you will run. If this prompt includes an approval gate, stop and wait for explicit owner approval. Implement in small independently buildable steps. Build and run relevant unit/instrumented/UI tests after each step. If a requirement cannot be completed safely, do not expose a fake UI control; report it as PARTIAL or NOT DONE with the reason.

Finish with the Standard Report. Do not automatically start the next prompt.
```


### P25 — Places, map view and location privacy

**Depends on:** All earlier prompts in the approved sequence  
**Mode:** Implement only this task, then build and test

```text
SESSION STARTER
You are working on the existing Far Gallery Android application in this repository.
Read APP_OVERVIEW.md and this complete Far Gallery Master Prompt / Build Plan. Read the relevant feature and architecture sections before editing. Inspect the actual source code; documentation is not proof that a feature works. Follow all Global Rules and the Standard Report format in this document. Do not change framework, delete user data, add internet permission, create fake controls, or edit unrelated modules.

TASK — P25: Places, map view and location privacy
Group geotagged photos by coordinates and user-edited place labels; build a map screen that works only with a user-imported offline map tile pack or show a location list when no tiles exist. Do not fetch map tiles or geocode online. Handle Android location/media-location permissions and allow users to strip location on export. Offer hide place, remove coordinates, batch date/location correction and EXIF grouping. Avoid exposing location in notifications, widgets, search suggestions or Vault metadata. Explain that map coverage depends on imported offline tiles.

Before coding, list the exact files/classes you will inspect and change, why each is needed, and the tests you will run. If this prompt includes an approval gate, stop and wait for explicit owner approval. Implement in small independently buildable steps. Build and run relevant unit/instrumented/UI tests after each step. If a requirement cannot be completed safely, do not expose a fake UI control; report it as PARTIAL or NOT DONE with the reason.

Finish with the Standard Report. Do not automatically start the next prompt.
```


### P26 — Memories, Featured Photos, events and year-in-review

**Depends on:** All earlier prompts in the approved sequence  
**Mode:** Implement only this task, then build and test

```text
SESSION STARTER
You are working on the existing Far Gallery Android application in this repository.
Read APP_OVERVIEW.md and this complete Far Gallery Master Prompt / Build Plan. Read the relevant feature and architecture sections before editing. Inspect the actual source code; documentation is not proof that a feature works. Follow all Global Rules and the Standard Report format in this document. Do not change framework, delete user data, add internet permission, create fake controls, or edit unrelated modules.

TASK — P26: Memories, Featured Photos, events and year-in-review
Create local Memories from date, people/pets, place, trips, weekends, seasons, holidays, themes and user favourites. Include On This Day, event/trip album suggestions, Featured Photos, calendar heatmap, Year in Review, recently viewed/pinned and optional voice captions attached as local sidecar metadata. Users can edit titles, exclude dates/people/places, hide sensitive content and disable Memories entirely. Memory cards use gentle motion that pauses off-screen and in battery saver. Never infer a sensitive event or surface Vault/hidden content. Generated memories must remain editable and local.

Before coding, list the exact files/classes you will inspect and change, why each is needed, and the tests you will run. If this prompt includes an approval gate, stop and wait for explicit owner approval. Implement in small independently buildable steps. Build and run relevant unit/instrumented/UI tests after each step. If a requirement cannot be completed safely, do not expose a fake UI control; report it as PARTIAL or NOT DONE with the reason.

Finish with the Standard Report. Do not automatically start the next prompt.
```


### P27 — Creation Studio: slideshow, movie, collage, animation and photo book

**Depends on:** All earlier prompts in the approved sequence  
**Mode:** Implement only this task, then build and test

```text
SESSION STARTER
You are working on the existing Far Gallery Android application in this repository.
Read APP_OVERVIEW.md and this complete Far Gallery Master Prompt / Build Plan. Read the relevant feature and architecture sections before editing. Inspect the actual source code; documentation is not proof that a feature works. Follow all Global Rules and the Standard Report format in this document. Do not change framework, delete user data, add internet permission, create fake controls, or edit unrelated modules.

TASK — P27: Creation Studio: slideshow, movie, collage, animation and photo book
Build a Creation Studio for custom slideshows (themes, per-slide duration, transitions, local music, crop/focal point, saved projects), movies combining photos/videos with trims/music/titles, adjustable collages, GIF/video animations, printable photo books and PDF contact sheets. Preview before export, show output format/resolution/estimated storage, render in background with progress/cancel/resume, and save to an explicit destination without changing source media. Include local audio picker, audio fade/volume and copyright-safe use of user-selected files. Support project editing after app restart and graceful failures for unsupported codecs.

Before coding, list the exact files/classes you will inspect and change, why each is needed, and the tests you will run. If this prompt includes an approval gate, stop and wait for explicit owner approval. Implement in small independently buildable steps. Build and run relevant unit/instrumented/UI tests after each step. If a requirement cannot be completed safely, do not expose a fake UI control; report it as PARTIAL or NOT DONE with the reason.

Finish with the Standard Report. Do not automatically start the next prompt.
```


### P28 — Core non-destructive photo editor

**Depends on:** All earlier prompts in the approved sequence  
**Mode:** Implement only this task, then build and test

```text
SESSION STARTER
You are working on the existing Far Gallery Android application in this repository.
Read APP_OVERVIEW.md and this complete Far Gallery Master Prompt / Build Plan. Read the relevant feature and architecture sections before editing. Inspect the actual source code; documentation is not proof that a feature works. Follow all Global Rules and the Standard Report format in this document. Do not change framework, delete user data, add internet permission, create fake controls, or edit unrelated modules.

TASK — P28: Core non-destructive photo editor
Create a responsive editor with live downscaled preview and a full-resolution export pipeline. Use an immutable edit recipe/stack with undo/redo, reset, edit history and copy/paste edits. Include crop/freeform/aspect ratios, rotate, flip, straighten, perspective correction, exposure, brilliance, highlights, shadows, black point, brightness, contrast, saturation, vibrance, warmth, tint, white balance, vignette, filters and monochrome looks with intensity. Press-and-hold compares original; Revert restores source. Default Done means Save Copy, with explicit Replace only after warning and recovery path. Originals remain byte-identical in tests.

Before coding, list the exact files/classes you will inspect and change, why each is needed, and the tests you will run. If this prompt includes an approval gate, stop and wait for explicit owner approval. Implement in small independently buildable steps. Build and run relevant unit/instrumented/UI tests after each step. If a requirement cannot be completed safely, do not expose a fake UI control; report it as PARTIAL or NOT DONE with the reason.

Finish with the Standard Report. Do not automatically start the next prompt.
```


### P29 — Advanced photo tools, markup, RAW and export presets

**Depends on:** All earlier prompts in the approved sequence  
**Mode:** Implement only this task, then build and test

```text
SESSION STARTER
You are working on the existing Far Gallery Android application in this repository.
Read APP_OVERVIEW.md and this complete Far Gallery Master Prompt / Build Plan. Read the relevant feature and architecture sections before editing. Inspect the actual source code; documentation is not proof that a feature works. Follow all Global Rules and the Standard Report format in this document. Do not change framework, delete user data, add internet permission, create fake controls, or edit unrelated modules.

TASK — P29: Advanced photo tools, markup, RAW and export presets
Add red-eye removal, spot healing, blemish/retouch basics, portrait blur/light when depth exists, markup with pen/text/shapes/arrows/signature, Live/Motion Photo key frame/loop/bounce/long-exposure controls where format support exists, RAW/DNG editing, Ultra HDR preservation/display, wallpaper maker, ID/passport photo crops and print-ready sheets. Add Clean Share presets: Original, Compressed Copy, Strip Location, Strip All EXIF, PDF and ZIP. Add blur/redact faces, plates or text and transparent subject cutout only where an actual local pipeline exists. Every action previews changes and exports a copy.

Before coding, list the exact files/classes you will inspect and change, why each is needed, and the tests you will run. If this prompt includes an approval gate, stop and wait for explicit owner approval. Implement in small independently buildable steps. Build and run relevant unit/instrumented/UI tests after each step. If a requirement cannot be completed safely, do not expose a fake UI control; report it as PARTIAL or NOT DONE with the reason.

Finish with the Standard Report. Do not automatically start the next prompt.
```


### P30 — Advanced on-device AI editing tools

**Depends on:** All earlier prompts in the approved sequence  
**Mode:** Implement only this task, then build and test

```text
SESSION STARTER
You are working on the existing Far Gallery Android application in this repository.
Read APP_OVERVIEW.md and this complete Far Gallery Master Prompt / Build Plan. Read the relevant feature and architecture sections before editing. Inspect the actual source code; documentation is not proof that a feature works. Follow all Global Rules and the Standard Report format in this document. Do not change framework, delete user data, add internet permission, create fake controls, or edit unrelated modules.

TASK — P30: Advanced on-device AI editing tools
Implement device-tier-gated local Erase/Object Removal, Camouflage, Photo Unblur, Extend/outpainting, Reframe/perspective, Sky/Color Pop/HDR/Dynamic effects, Portrait Light, and any local inpainting diffusion features that are genuinely runnable. Bundle model files or allow explicitly user-imported local model packs; never download remotely. Before exposing a tool, benchmark model size, quality, speed, RAM, heat and storage. Show model-required/support state and a clear unavailable reason. Provide mask refinement, before/after compare, undo, cancel and Save Copy. If model assets/licence/performance are not ready, do not show a fake control.

Before coding, list the exact files/classes you will inspect and change, why each is needed, and the tests you will run. If this prompt includes an approval gate, stop and wait for explicit owner approval. Implement in small independently buildable steps. Build and run relevant unit/instrumented/UI tests after each step. If a requirement cannot be completed safely, do not expose a fake UI control; report it as PARTIAL or NOT DONE with the reason.

Finish with the Standard Report. Do not automatically start the next prompt.
```


### P31 — Video editing, trimming, compression and audio tools

**Depends on:** All earlier prompts in the approved sequence  
**Mode:** Implement only this task, then build and test

```text
SESSION STARTER
You are working on the existing Far Gallery Android application in this repository.
Read APP_OVERVIEW.md and this complete Far Gallery Master Prompt / Build Plan. Read the relevant feature and architecture sections before editing. Inspect the actual source code; documentation is not proof that a feature works. Follow all Global Rules and the Standard Report format in this document. Do not change framework, delete user data, add internet permission, create fake controls, or edit unrelated modules.

TASK — P31: Video editing, trimming, compression and audio tools
Extend Media3 editing with dual-thumb trim, mute, rotate, crop, playback speed, basic light/colour adjustments, resolution/fps/format exports, compression size/quality preview, frame export, GIF export, Motion Photo-to-video, and stabilisation where supported. Add optional speech/audio enhancement and audio noise reduction only with verified bundled models. Preflight storage, stream processing, avoid huge cache temp files, report estimated output size, support cancellation and cleanup in finally, preserve originals and verify output playback and audio/video sync. No promise of lossless output when transcoding.

Before coding, list the exact files/classes you will inspect and change, why each is needed, and the tests you will run. If this prompt includes an approval gate, stop and wait for explicit owner approval. Implement in small independently buildable steps. Build and run relevant unit/instrumented/UI tests after each step. If a requirement cannot be completed safely, do not expose a fake UI control; report it as PARTIAL or NOT DONE with the reason.

Finish with the Standard Report. Do not automatically start the next prompt.
```


### P32 — Files Browse landing page and multi-format file browser

**Depends on:** All earlier prompts in the approved sequence  
**Mode:** Implement only this task, then build and test

```text
SESSION STARTER
You are working on the existing Far Gallery Android application in this repository.
Read APP_OVERVIEW.md and this complete Far Gallery Master Prompt / Build Plan. Read the relevant feature and architecture sections before editing. Inspect the actual source code; documentation is not proof that a feature works. Follow all Global Rules and the Standard Report format in this document. Do not change framework, delete user data, add internet permission, create fake controls, or edit unrelated modules.

TASK — P32: Files Browse landing page and multi-format file browser
Build Files with large Browse title, search, Recents, Recent Folders, Downloads, Favourites, Tags, Trash and Locations (On this device, SD card, USB drive, Far Camera/Pictures/Movies/Documents). Do not show iCloud Drive or fictitious Shared cloud locations. Add grid/list and tablet column views, breadcrumb/path bar, tabs/split panes on large screens, sorting by name/date/size/kind and filters for image/video/audio/document/archive/APK/other. Hidden files toggle, dotfiles, folder size on demand, folder comparison, smart folders, storage availability and clear permission state. Use SAF for arbitrary files and MediaStore for shared media.

Before coding, list the exact files/classes you will inspect and change, why each is needed, and the tests you will run. If this prompt includes an approval gate, stop and wait for explicit owner approval. Implement in small independently buildable steps. Build and run relevant unit/instrumented/UI tests after each step. If a requirement cannot be completed safely, do not expose a fake UI control; report it as PARTIAL or NOT DONE with the reason.

Finish with the Standard Report. Do not automatically start the next prompt.
```


### P33 — File operations, batch rename, smart folders and drag-and-drop

**Depends on:** All earlier prompts in the approved sequence  
**Mode:** Implement only this task, then build and test

```text
SESSION STARTER
You are working on the existing Far Gallery Android application in this repository.
Read APP_OVERVIEW.md and this complete Far Gallery Master Prompt / Build Plan. Read the relevant feature and architecture sections before editing. Inspect the actual source code; documentation is not proof that a feature works. Follow all Global Rules and the Standard Report format in this document. Do not change framework, delete user data, add internet permission, create fake controls, or edit unrelated modules.

TASK — P33: File operations, batch rename, smart folders and drag-and-drop
Implement create folder/file, rename with extension warning, copy/cut/paste, duplicate, move across folders/volumes, delete to Trash where possible, batch selection, share, compress, drag/drop, properties and Open With. Add batch rename patterns with live preview, duplicate-name helper, conflict choices and undo where possible. Add smart folders as persisted searches, Tidy Downloads suggestions with user review, Compare Folders, Folder Mirror to local/SD/USB on demand, Pin to Home Screen and Paste as File. Every long task uses the shared journal/Activity Centre and verifies destination before source deletion.

Before coding, list the exact files/classes you will inspect and change, why each is needed, and the tests you will run. If this prompt includes an approval gate, stop and wait for explicit owner approval. Implement in small independently buildable steps. Build and run relevant unit/instrumented/UI tests after each step. If a requirement cannot be completed safely, do not expose a fake UI control; report it as PARTIAL or NOT DONE with the reason.

Finish with the Standard Report. Do not automatically start the next prompt.
```


### P34 — File preview, text editor, document scanner and PDF tools

**Depends on:** All earlier prompts in the approved sequence  
**Mode:** Implement only this task, then build and test

```text
SESSION STARTER
You are working on the existing Far Gallery Android application in this repository.
Read APP_OVERVIEW.md and this complete Far Gallery Master Prompt / Build Plan. Read the relevant feature and architecture sections before editing. Inspect the actual source code; documentation is not proof that a feature works. Follow all Global Rules and the Standard Report format in this document. Do not change framework, delete user data, add internet permission, create fake controls, or edit unrelated modules.

TASK — P34: File preview, text editor, document scanner and PDF tools
Build Quick Look-style offline preview for images, videos, audio waveform/player, PDF, TXT, Markdown, CSV, JSON and source code with syntax highlighting/line numbers; unsupported Office documents use Open With unless a licensed offline renderer is approved. Add local TXT/Markdown/code editor with find/replace, line numbers and safe save. Add document scanning with auto edge detect, perspective crop, multi-page capture, OCR text layer and searchable PDF. PDF tools include merge, split, reorder, rotate, annotate, form fill and signature, all Save Copy by default. Include OCR-to-text export, APK Inspector and Hex/Info viewer as safe read-only tools.

Before coding, list the exact files/classes you will inspect and change, why each is needed, and the tests you will run. If this prompt includes an approval gate, stop and wait for explicit owner approval. Implement in small independently buildable steps. Build and run relevant unit/instrumented/UI tests after each step. If a requirement cannot be completed safely, do not expose a fake UI control; report it as PARTIAL or NOT DONE with the reason.

Finish with the Standard Report. Do not automatically start the next prompt.
```


### P35 — Archives, compression and checksum tools

**Depends on:** All earlier prompts in the approved sequence  
**Mode:** Implement only this task, then build and test

```text
SESSION STARTER
You are working on the existing Far Gallery Android application in this repository.
Read APP_OVERVIEW.md and this complete Far Gallery Master Prompt / Build Plan. Read the relevant feature and architecture sections before editing. Inspect the actual source code; documentation is not proof that a feature works. Follow all Global Rules and the Standard Report format in this document. Do not change framework, delete user data, add internet permission, create fake controls, or edit unrelated modules.

TASK — P35: Archives, compression and checksum tools
Keep existing Zip-Slip and zip-bomb protections and strengthen them with canonical path checks, entry count, uncompressed byte, ratio, nesting and symlink safeguards. Add archive preview, selected-entry extraction, ZIP creation, password-protected ZIP support using vetted libraries, TAR/GZIP and 7z/RAR read support only after library licence/maintenance review and adversarial tests. Add SHA-256 calculate/verify against a pasted hash. Extract to a temporary destination, verify then move into place; handle wrong passwords, corrupt archives, low disk space, cancellation and partial failures. Never run APKs or archive contents automatically.

Before coding, list the exact files/classes you will inspect and change, why each is needed, and the tests you will run. If this prompt includes an approval gate, stop and wait for explicit owner approval. Implement in small independently buildable steps. Build and run relevant unit/instrumented/UI tests after each step. If a requirement cannot be completed safely, do not expose a fake UI control; report it as PARTIAL or NOT DONE with the reason.

Finish with the Standard Report. Do not automatically start the next prompt.
```


### P36 — Storage analyzer, Tidy cleanup and Make Room

**Depends on:** All earlier prompts in the approved sequence  
**Mode:** Implement only this task, then build and test

```text
SESSION STARTER
You are working on the existing Far Gallery Android application in this repository.
Read APP_OVERVIEW.md and this complete Far Gallery Master Prompt / Build Plan. Read the relevant feature and architecture sections before editing. Inspect the actual source code; documentation is not proof that a feature works. Follow all Global Rules and the Standard Report format in this document. Do not change framework, delete user data, add internet permission, create fake controls, or edit unrelated modules.

TASK — P36: Storage analyzer, Tidy cleanup and Make Room
Build a storage dashboard with category breakdown and folder treemap; largest files; old videos/downloads; screenshots; empty folders; duplicates; blurry media; unused albums; duplicate APKs and local junk suggestions. Show exact candidate list, reason, current size and projected reclaimable size before any action. Make Room must not assume a cloud backup exists; it deletes only after user's explicit review and uses Trash/undo where available. Explain that Android apps cannot clear other apps' private cache; link to system App Info if appropriate. Add storage guardrails before camera recording, export, extraction and backup.

Before coding, list the exact files/classes you will inspect and change, why each is needed, and the tests you will run. If this prompt includes an approval gate, stop and wait for explicit owner approval. Implement in small independently buildable steps. Build and run relevant unit/instrumented/UI tests after each step. If a requirement cannot be completed safely, do not expose a fake UI control; report it as PARTIAL or NOT DONE with the reason.

Finish with the Standard Report. Do not automatically start the next prompt.
```


### P37 — Vault, app lock and private media protection

**Depends on:** All earlier prompts in the approved sequence  
**Mode:** Implement only this task, then build and test

```text
SESSION STARTER
You are working on the existing Far Gallery Android application in this repository.
Read APP_OVERVIEW.md and this complete Far Gallery Master Prompt / Build Plan. Read the relevant feature and architecture sections before editing. Inspect the actual source code; documentation is not proof that a feature works. Follow all Global Rules and the Standard Report format in this document. Do not change framework, delete user data, add internet permission, create fake controls, or edit unrelated modules.

TASK — P37: Vault, app lock and private media protection
Implement one encrypted Vault for photos, videos and arbitrary files using Android Keystore-backed key derivation/wrapping and authenticated encryption; use BiometricPrompt with PIN fallback and configurable auto-lock. Support move-to-Vault from viewer/files, capture-direct-to-Vault, decoy PIN only as a convenience (never claim forensic protection), hidden albums, protected recents thumbnails, screenshot blocking where appropriate, notification redaction, no plaintext thumbnail/cache, and reliable restore/export. Vault items must never leak to MediaStore thumbnails, global search, OCR, face groups, Memories, widgets, notifications or diagnostics. Provide lock timeout, key recovery explanation and tested wrong-PIN/biometric failure paths.

Before coding, list the exact files/classes you will inspect and change, why each is needed, and the tests you will run. If this prompt includes an approval gate, stop and wait for explicit owner approval. Implement in small independently buildable steps. Build and run relevant unit/instrumented/UI tests after each step. If a requirement cannot be completed safely, do not expose a fake UI control; report it as PARTIAL or NOT DONE with the reason.

Finish with the Standard Report. Do not automatically start the next prompt.
```


### P38 — Import/export, local backup and restore

**Depends on:** All earlier prompts in the approved sequence  
**Mode:** Implement only this task, then build and test

```text
SESSION STARTER
You are working on the existing Far Gallery Android application in this repository.
Read APP_OVERVIEW.md and this complete Far Gallery Master Prompt / Build Plan. Read the relevant feature and architecture sections before editing. Inspect the actual source code; documentation is not proof that a feature works. Follow all Global Rules and the Standard Report format in this document. Do not change framework, delete user data, add internet permission, create fake controls, or edit unrelated modules.

TASK — P38: Import/export, local backup and restore
Implement user-controlled import from device folders, SD and USB without duplicating assets, and export media/albums/folders in open formats with optional sidecar JSON for captions, ratings, tags, edits and album order. Add encrypted local backup to a user-selected SD/USB/SAF destination, include backup manifest/version/checksums, progress/cancel/resume and restore preview. Verify every archive and restore onto a clean install in tests. No internet, cloud, account or scheduled remote sync. Clearly state that losing the encryption password/key can make the backup unrecoverable. If a future LAN edition is considered, it is a separate approval gate and not part of this task.

Before coding, list the exact files/classes you will inspect and change, why each is needed, and the tests you will run. If this prompt includes an approval gate, stop and wait for explicit owner approval. Implement in small independently buildable steps. Build and run relevant unit/instrumented/UI tests after each step. If a requirement cannot be completed safely, do not expose a fake UI control; report it as PARTIAL or NOT DONE with the reason.

Finish with the Standard Report. Do not automatically start the next prompt.
```


### P39 — Camera core viewfinder and capture pipeline

**Depends on:** All earlier prompts in the approved sequence  
**Mode:** Implement only this task, then build and test

```text
SESSION STARTER
You are working on the existing Far Gallery Android application in this repository.
Read APP_OVERVIEW.md and this complete Far Gallery Master Prompt / Build Plan. Read the relevant feature and architecture sections before editing. Inspect the actual source code; documentation is not proof that a feature works. Follow all Global Rules and the Standard Report format in this document. Do not change framework, delete user data, add internet permission, create fake controls, or edit unrelated modules.

TASK — P39: Camera core viewfinder and capture pipeline
Build a fast, full-screen Camera with CameraX and Camera2 interop only where needed. Show black immersive preview, quiet top controls (Flash, Timer, Aspect, More), mode carousel, zoom pill with only real lens stops, large thumb-reachable shutter, last capture thumbnail and front/back switch. Support tap-to-focus, drag exposure, AE/AF lock, flash/torch, 3/5/10-second timer, grid, level, aspect ratio, output sizes reported by hardware, burst, volume-key shutter while foreground, per-mode setting memory and permission-denied screen. Write captures once via MediaStore pending/commit; show saved only after commit and index scheduling. Keep screen awake only during capture.

Before coding, list the exact files/classes you will inspect and change, why each is needed, and the tests you will run. If this prompt includes an approval gate, stop and wait for explicit owner approval. Implement in small independently buildable steps. Build and run relevant unit/instrumented/UI tests after each step. If a requirement cannot be completed safely, do not expose a fake UI control; report it as PARTIAL or NOT DONE with the reason.

Finish with the Standard Report. Do not automatically start the next prompt.
```


### P40 — Camera mode capability detection and custom controls

**Depends on:** All earlier prompts in the approved sequence  
**Mode:** Implement only this task, then build and test

```text
SESSION STARTER
You are working on the existing Far Gallery Android application in this repository.
Read APP_OVERVIEW.md and this complete Far Gallery Master Prompt / Build Plan. Read the relevant feature and architecture sections before editing. Inspect the actual source code; documentation is not proof that a feature works. Follow all Global Rules and the Standard Report format in this document. Do not change framework, delete user data, add internet permission, create fake controls, or edit unrelated modules.

TASK — P40: Camera mode capability detection and custom controls
Create runtime capability detection per physical/logical camera: lenses, focal lengths, zoom, flash, min/max exposure, focus distance, RAW, supported fps, video sizes, concurrent camera, vendor extensions and hardware level. Build mode availability states and explain why unsupported. Fix existing Portrait/SloMo stubs: use a true supported bokeh extension/high-speed session, verify output metadata/fps, or mark unavailable—never silently bind normal photo/30fps video. Allow per-mode controls to pin, reorder, hide and restore. Add mode-specific quick settings, resolution/fps sheet and test on devices with one lens, no flash and limited Camera2 support.

Before coding, list the exact files/classes you will inspect and change, why each is needed, and the tests you will run. If this prompt includes an approval gate, stop and wait for explicit owner approval. Implement in small independently buildable steps. Build and run relevant unit/instrumented/UI tests after each step. If a requirement cannot be completed safely, do not expose a fake UI control; report it as PARTIAL or NOT DONE with the reason.

Finish with the Standard Report. Do not automatically start the next prompt.
```


### P41 — Camera Pro controls, RAW, exposure and capture presets

**Depends on:** All earlier prompts in the approved sequence  
**Mode:** Implement only this task, then build and test

```text
SESSION STARTER
You are working on the existing Far Gallery Android application in this repository.
Read APP_OVERVIEW.md and this complete Far Gallery Master Prompt / Build Plan. Read the relevant feature and architecture sections before editing. Inspect the actual source code; documentation is not proof that a feature works. Follow all Global Rules and the Standard Report format in this document. Do not change framework, delete user data, add internet permission, create fake controls, or edit unrelated modules.

TASK — P41: Camera Pro controls, RAW, exposure and capture presets
Add Pro mode controls only when supported: ISO, exposure time, focus distance, white balance, RAW/DNG, exposure bracketing, histogram, zebra/clipping alerts and focus peaking. Include clear Auto reset and manual state indication. Add zoom wheel for fine control, exposure/focus assist, photographic Looks with tone/warmth/grain saved as capture recipes and per-mode defaults. Use actual camera characteristics and valid ranges; don't fake manual control by changing preview only. Show capture format/estimated file size and preserve metadata settings. Test output EXIF and DNG decode/write with real device samples.

Before coding, list the exact files/classes you will inspect and change, why each is needed, and the tests you will run. If this prompt includes an approval gate, stop and wait for explicit owner approval. Implement in small independently buildable steps. Build and run relevant unit/instrumented/UI tests after each step. If a requirement cannot be completed safely, do not expose a fake UI control; report it as PARTIAL or NOT DONE with the reason.

Finish with the Standard Report. Do not automatically start the next prompt.
```


### P42 — Computational photography and advanced still capture

**Depends on:** All earlier prompts in the approved sequence  
**Mode:** Implement only this task, then build and test

```text
SESSION STARTER
You are working on the existing Far Gallery Android application in this repository.
Read APP_OVERVIEW.md and this complete Far Gallery Master Prompt / Build Plan. Read the relevant feature and architecture sections before editing. Inspect the actual source code; documentation is not proof that a feature works. Follow all Global Rules and the Standard Report format in this document. Do not change framework, delete user data, add internet permission, create fake controls, or edit unrelated modules.

TASK — P42: Computational photography and advanced still capture
Implement only verified local pipelines in tiers: HDR multi-frame merge, Night multi-frame denoise/merge, panorama stitching, time-lapse, genuine high-speed slow motion, macro focus when lens exists, depth/Portrait blur, long exposure, Action Pan, Motion Photos/Moments, Top Shot/Best Frame, burst-to-GIF, Photo Booth, group selfie and dual camera only when concurrent cameras are supported, astrophotography/tripod mode and lens-clean hint. Preserve all source frames and metadata. Each mode must be cancellable, thermal-aware, sample-output tested and explicitly unavailable if unsupported. Avoid claiming Pixel-exclusive ISP equivalence.

Before coding, list the exact files/classes you will inspect and change, why each is needed, and the tests you will run. If this prompt includes an approval gate, stop and wait for explicit owner approval. Implement in small independently buildable steps. Build and run relevant unit/instrumented/UI tests after each step. If a requirement cannot be completed safely, do not expose a fake UI control; report it as PARTIAL or NOT DONE with the reason.

Finish with the Standard Report. Do not automatically start the next prompt.
```


### P43 — Camera video capture, stabilisation and audio

**Depends on:** All earlier prompts in the approved sequence  
**Mode:** Implement only this task, then build and test

```text
SESSION STARTER
You are working on the existing Far Gallery Android application in this repository.
Read APP_OVERVIEW.md and this complete Far Gallery Master Prompt / Build Plan. Read the relevant feature and architecture sections before editing. Inspect the actual source code; documentation is not proof that a feature works. Follow all Global Rules and the Standard Report format in this document. Do not change framework, delete user data, add internet permission, create fake controls, or edit unrelated modules.

TASK — P43: Camera video capture, stabilisation and audio
Add video recording with pause/resume only when safe, 4K/1080p and real supported frame rates, front-camera video, stabilisation options reported by the device, Video Snapshot while recording when stream combination allows, audio source selection, wind reduction where available, and directional audio zoom only where hardware supports it. Add cinematic depth blur only with a valid depth stream/model and label post-processing. Include battery/heat/storage meter, low-storage safety net, recording duration and estimated remaining space. Ensure files are finalized on stop, recovered after interruption, indexed once and playable. Never advertise ProRes/spatial/10-bit HDR unless capture and output metadata confirm it.

Before coding, list the exact files/classes you will inspect and change, why each is needed, and the tests you will run. If this prompt includes an approval gate, stop and wait for explicit owner approval. Implement in small independently buildable steps. Build and run relevant unit/instrumented/UI tests after each step. If a requirement cannot be completed safely, do not expose a fake UI control; report it as PARTIAL or NOT DONE with the reason.

Finish with the Standard Report. Do not automatically start the next prompt.
```


### P44 — Android system intents, picker and DocumentsProvider

**Depends on:** All earlier prompts in the approved sequence  
**Mode:** Implement only this task, then build and test

```text
SESSION STARTER
You are working on the existing Far Gallery Android application in this repository.
Read APP_OVERVIEW.md and this complete Far Gallery Master Prompt / Build Plan. Read the relevant feature and architecture sections before editing. Inspect the actual source code; documentation is not proof that a feature works. Follow all Global Rules and the Standard Report format in this document. Do not change framework, delete user data, add internet permission, create fake controls, or edit unrelated modules.

TASK — P44: Android system intents, picker and DocumentsProvider
Implement correct public Android contracts: ACTION_VIEW, ACTION_EDIT if safe, ACTION_SEND/ SEND_MULTIPLE, ACTION_PICK, ACTION_GET_CONTENT, ACTION_IMAGE_CAPTURE, ACTION_VIDEO_CAPTURE, appropriate MIME filters, clip data, grant flags and EXTRA_OUTPUT. Implement an in-app picker and a DocumentsProvider that exposes only files/folders the user explicitly selects to expose. Validate result codes, output URI, cancellation and permissions. Add resolver/device tests. Never request the hidden system gallery role or claim universal replacement. Document OEM limits for lock screen, power-button launch and camera keys.

Before coding, list the exact files/classes you will inspect and change, why each is needed, and the tests you will run. If this prompt includes an approval gate, stop and wait for explicit owner approval. Implement in small independently buildable steps. Build and run relevant unit/instrumented/UI tests after each step. If a requirement cannot be completed safely, do not expose a fake UI control; report it as PARTIAL or NOT DONE with the reason.

Finish with the Standard Report. Do not automatically start the next prompt.
```


### P45 — Sharing, Save to Far and metadata-clean export

**Depends on:** All earlier prompts in the approved sequence  
**Mode:** Implement only this task, then build and test

```text
SESSION STARTER
You are working on the existing Far Gallery Android application in this repository.
Read APP_OVERVIEW.md and this complete Far Gallery Master Prompt / Build Plan. Read the relevant feature and architecture sections before editing. Inspect the actual source code; documentation is not proof that a feature works. Follow all Global Rules and the Standard Report format in this document. Do not change framework, delete user data, add internet permission, create fake controls, or edit unrelated modules.

TASK — P45: Sharing, Save to Far and metadata-clean export
Use Android's system share sheet for sharing to user-installed apps; provide in-app actions to Copy, Save to Files, Add to Album, Export ZIP/PDF and Save to Far from other apps. Add multi-item URI grants and bounded payload sizes. Implement Clean Share profiles: original, compressed copy, strip location, strip all EXIF, PDF, selected files ZIP. Show a preview/summary of metadata that will be removed and never change the source. Make clear that recipient apps may independently use their own networks; Far itself has no cloud or internet permission.

Before coding, list the exact files/classes you will inspect and change, why each is needed, and the tests you will run. If this prompt includes an approval gate, stop and wait for explicit owner approval. Implement in small independently buildable steps. Build and run relevant unit/instrumented/UI tests after each step. If a requirement cannot be completed safely, do not expose a fake UI control; report it as PARTIAL or NOT DONE with the reason.

Finish with the Standard Report. Do not automatically start the next prompt.
```


### P46 — Widgets, launcher shortcuts and Quick Settings

**Depends on:** All earlier prompts in the approved sequence  
**Mode:** Implement only this task, then build and test

```text
SESSION STARTER
You are working on the existing Far Gallery Android application in this repository.
Read APP_OVERVIEW.md and this complete Far Gallery Master Prompt / Build Plan. Read the relevant feature and architecture sections before editing. Inspect the actual source code; documentation is not proof that a feature works. Follow all Global Rules and the Standard Report format in this document. Do not change framework, delete user data, add internet permission, create fake controls, or edit unrelated modules.

TASK — P46: Widgets, launcher shortcuts and Quick Settings
Extend the existing stats widget and theme tile with useful photo widgets (recent, favourites, Memories/photo of the day) and shortcuts for Camera, Scan and Identify, Search, Vault (only after auth) and selected albums. Add quick camera entry point only through supported Android public shortcuts, and do not promise lock-screen/power-button takeover. Update widgets efficiently, handle privacy toggles and never expose Vault/hidden content. Add monochrome themed icon, accessibility descriptions, widget resize variants and light/dark previews.

Before coding, list the exact files/classes you will inspect and change, why each is needed, and the tests you will run. If this prompt includes an approval gate, stop and wait for explicit owner approval. Implement in small independently buildable steps. Build and run relevant unit/instrumented/UI tests after each step. If a requirement cannot be completed safely, do not expose a fake UI control; report it as PARTIAL or NOT DONE with the reason.

Finish with the Standard Report. Do not automatically start the next prompt.
```


### P47 — Activity Centre, Undo Centre, diagnostics and Safe Mode

**Depends on:** All earlier prompts in the approved sequence  
**Mode:** Implement only this task, then build and test

```text
SESSION STARTER
You are working on the existing Far Gallery Android application in this repository.
Read APP_OVERVIEW.md and this complete Far Gallery Master Prompt / Build Plan. Read the relevant feature and architecture sections before editing. Inspect the actual source code; documentation is not proof that a feature works. Follow all Global Rules and the Standard Report format in this document. Do not change framework, delete user data, add internet permission, create fake controls, or edit unrelated modules.

TASK — P47: Activity Centre, Undo Centre, diagnostics and Safe Mode
Create a persistent Activity Centre for scanning, OCR, duplicate analysis, copy/move, exports, video renders, extraction, backup and restore. Each task has accurate progress, pause/resume/cancel/retry and operation details. Add Undo Centre/history for reversible move, rename, album changes, edits and Trash actions where safe. Add readable Activity Log and user-initiated sanitized diagnostics export (preview first, local file only). Implement crash-loop guard, Safe Mode after repeated startup failures, local recent-action breadcrumbs and quarantine of repeatedly crashing corrupt files. Never upload diagnostics.

Before coding, list the exact files/classes you will inspect and change, why each is needed, and the tests you will run. If this prompt includes an approval gate, stop and wait for explicit owner approval. Implement in small independently buildable steps. Build and run relevant unit/instrumented/UI tests after each step. If a requirement cannot be completed safely, do not expose a fake UI control; report it as PARTIAL or NOT DONE with the reason.

Finish with the Standard Report. Do not automatically start the next prompt.
```


### P48 — Settings, privacy dashboard and guided preferences

**Depends on:** All earlier prompts in the approved sequence  
**Mode:** Implement only this task, then build and test

```text
SESSION STARTER
You are working on the existing Far Gallery Android application in this repository.
Read APP_OVERVIEW.md and this complete Far Gallery Master Prompt / Build Plan. Read the relevant feature and architecture sections before editing. Inspect the actual source code; documentation is not proof that a feature works. Follow all Global Rules and the Standard Report format in this document. Do not change framework, delete user data, add internet permission, create fake controls, or edit unrelated modules.

TASK — P48: Settings, privacy dashboard and guided preferences
Organise Settings into Appearance, Library & Indexing, Camera, Files & Storage, Privacy & Vault, Backup & Export, Accessibility and About, with local settings search. Include auto scan/index on/off, included/excluded folders, cache clear/rebuild, thumbnail cache size, grid size, start destination/folder, sort choice, theme/accent/reduce transparency, haptics/sound, trash retention, app lock, local reminders, storage status, index health, model management, privacy dashboard and integration status. Explain effects and battery/storage costs beside controls. Every setting persists and takes effect.

Before coding, list the exact files/classes you will inspect and change, why each is needed, and the tests you will run. If this prompt includes an approval gate, stop and wait for explicit owner approval. Implement in small independently buildable steps. Build and run relevant unit/instrumented/UI tests after each step. If a requirement cannot be completed safely, do not expose a fake UI control; report it as PARTIAL or NOT DONE with the reason.

Finish with the Standard Report. Do not automatically start the next prompt.
```


### P49 — Accessibility, localisation and expanded-device layouts

**Depends on:** All earlier prompts in the approved sequence  
**Mode:** Implement only this task, then build and test

```text
SESSION STARTER
You are working on the existing Far Gallery Android application in this repository.
Read APP_OVERVIEW.md and this complete Far Gallery Master Prompt / Build Plan. Read the relevant feature and architecture sections before editing. Inspect the actual source code; documentation is not proof that a feature works. Follow all Global Rules and the Standard Report format in this document. Do not change framework, delete user data, add internet permission, create fake controls, or edit unrelated modules.

TASK — P49: Accessibility, localisation and expanded-device layouts
Perform complete TalkBack, Switch Access and voice access pass. Add semantic labels with state, logical focus order, custom actions on photos/files, keyboard navigation and shortcuts, high contrast, 200% font size without clipping, 48dp targets, RTL, localised dates/numbers, captions/alt text, camera timer spoken countdown and reduced motion/transparency support. Review tablets/foldables/ChromeOS, landscape, split-pane, keyboard/mouse, stylus, drag-and-drop and rotation. Do not rely on colour alone. Add automated accessibility checks and a manual test checklist.

Before coding, list the exact files/classes you will inspect and change, why each is needed, and the tests you will run. If this prompt includes an approval gate, stop and wait for explicit owner approval. Implement in small independently buildable steps. Build and run relevant unit/instrumented/UI tests after each step. If a requirement cannot be completed safely, do not expose a fake UI control; report it as PARTIAL or NOT DONE with the reason.

Finish with the Standard Report. Do not automatically start the next prompt.
```


### P50 — Privacy, security and strict-offline audit

**Depends on:** All earlier prompts in the approved sequence  
**Mode:** Implement only this task, then build and test

```text
SESSION STARTER
You are working on the existing Far Gallery Android application in this repository.
Read APP_OVERVIEW.md and this complete Far Gallery Master Prompt / Build Plan. Read the relevant feature and architecture sections before editing. Inspect the actual source code; documentation is not proof that a feature works. Follow all Global Rules and the Standard Report format in this document. Do not change framework, delete user data, add internet permission, create fake controls, or edit unrelated modules.

TASK — P50: Privacy, security and strict-offline audit
Audit the merged manifest and dependency graph for INTERNET, analytics, ad, crash upload, remote API, remote fonts/models, trackers, hidden/private APIs and unnecessary permissions. Strict-offline build must contain no android.permission.INTERNET and must pass airplane-mode tests. Review Vault encryption/key handling, biometric/PIN, temp files, cache, screenshots, notification previews, search/ML exclusion, backup encryption and diagnostics. Verify no sensitive media is logged. Test permission minimisation and safe deletion limits. Local-network protocols (SMB/WebDAV/Wi-Fi Direct/Bluetooth sockets) remain excluded unless a separate flavour receives explicit approval; don't quietly change the privacy contract.

Before coding, list the exact files/classes you will inspect and change, why each is needed, and the tests you will run. If this prompt includes an approval gate, stop and wait for explicit owner approval. Implement in small independently buildable steps. Build and run relevant unit/instrumented/UI tests after each step. If a requirement cannot be completed safely, do not expose a fake UI control; report it as PARTIAL or NOT DONE with the reason.

Finish with the Standard Report. Do not automatically start the next prompt.
```


### P51 — Performance, endurance, fault-injection and device matrix

**Depends on:** All earlier prompts in the approved sequence  
**Mode:** Implement only this task, then build and test

```text
SESSION STARTER
You are working on the existing Far Gallery Android application in this repository.
Read APP_OVERVIEW.md and this complete Far Gallery Master Prompt / Build Plan. Read the relevant feature and architecture sections before editing. Inspect the actual source code; documentation is not proof that a feature works. Follow all Global Rules and the Standard Report format in this document. Do not change framework, delete user data, add internet permission, create fake controls, or edit unrelated modules.

TASK — P51: Performance, endurance, fault-injection and device matrix
Run end-to-end macrobenchmarks and soak tests on low-, mid- and high-tier Android devices. Measure cold start, camera launch, shutter-to-saved time, grid frame pacing, scroll jank, query time, indexing throughput, memory, cache, battery and thermal response. Test 100k media / 50k files, low storage, low RAM, 60/90/120Hz, SD/USB removal, revoked permission, corrupt media, camera contention, codec failure, provider timeout, app kill/reboot and migration recovery. Validate adaptive quality governor: pause ML, reduce thumbnail size, disable blur/animations in order. Fix regressions before declaring complete.

Before coding, list the exact files/classes you will inspect and change, why each is needed, and the tests you will run. If this prompt includes an approval gate, stop and wait for explicit owner approval. Implement in small independently buildable steps. Build and run relevant unit/instrumented/UI tests after each step. If a requirement cannot be completed safely, do not expose a fake UI control; report it as PARTIAL or NOT DONE with the reason.

Finish with the Standard Report. Do not automatically start the next prompt.
```


### P52 — Final integration, release gate and handover

**Depends on:** All earlier prompts in the approved sequence  
**Mode:** Implement only this task, then build and test

```text
SESSION STARTER
You are working on the existing Far Gallery Android application in this repository.
Read APP_OVERVIEW.md and this complete Far Gallery Master Prompt / Build Plan. Read the relevant feature and architecture sections before editing. Inspect the actual source code; documentation is not proof that a feature works. Follow all Global Rules and the Standard Report format in this document. Do not change framework, delete user data, add internet permission, create fake controls, or edit unrelated modules.

TASK — P52: Final integration, release gate and handover
Run the complete acceptance matrix for Photos, Camera, Files, shared identity, editor, video, AI, Vault, backup, sharing, Android intents, permissions, accessibility, offline mode and system integration. Verify every visible control has a real action, every feature marked Done has code and tests, no TODO/FIXME/placeholder/demo values remain, no data-loss/security P0/P1 issue exists, and all supported features have real-device evidence. Produce a release checklist, build instructions, dependency/licence inventory, permission rationale, known limitations, unsupported-device matrix, migration/backup instructions, changelog and roadmap for deferred features. Do not claim universal default replacement or camera quality parity. Report what is truly done and what remains.

Before coding, list the exact files/classes you will inspect and change, why each is needed, and the tests you will run. If this prompt includes an approval gate, stop and wait for explicit owner approval. Implement in small independently buildable steps. Build and run relevant unit/instrumented/UI tests after each step. If a requirement cannot be completed safely, do not expose a fake UI control; report it as PARTIAL or NOT DONE with the reason.

Finish with the Standard Report. Do not automatically start the next prompt.
```


---

# PART G — FEATURE-TO-PROMPT COVERAGE MAP

This index lets the owner check that the long feature list has not been lost during implementation.

| Feature family | Main prompts | What must be included |
|---|---|---|
| Repository audit, architecture, Gradle, known defects | P00–P05 | Evidence matrix, approved architecture, stable build, migrations, large-data tests, reliability |
| Unified asset model and automatic scanning | P06–P10 | One canonical media identity, incremental scanning, folder controls, WorkManager, URI handling, journaled operations |
| iOS-inspired design and navigation | P11–P14 | Themes, glass, tokens, icons, component library, three destinations, onboarding, permission UX |
| Photos timeline and browsing | P15–P18 | Years/Months/Days/All, date scrubber, grid density, collections, smart albums, albums, ratings, tags, archive, hidden, multi-select |
| Viewer, compare and video playback | P19–P20 | Full-screen viewer, zoom, swipe, details, EXIF, compare, Peek, playback, resume, frame export |
| Search and smart organisation | P21–P26 | Advanced/natural search, OCR, receipts/documents, duplicates, similar photos, blur, stacks, Best Frame, objects, faces/pets, places/maps, Memories |
| Creations | P27 | Slideshow, movie, collage, animation, photo book, contact sheets, local music |
| Photo editing | P28–P30 | Non-destructive edits, crop/geometry, light/colour, filters, markup, retouch, RAW, HDR, Erase/Extend/Reframe and local models |
| Video editing | P31 | Trim, compression, mute, crop, rotate, effects, frame/GIF export, stabilization/audio processing |
| Files browser and operations | P32–P35 | Browse, recents, downloads, tags, smart folders, grid/list/columns, copy/move/rename, batch, PDF, scanner, text/code, APK, hex, archives, hashes |
| Storage and cleanup | P36 | Storage breakdown/treemap, large files, duplicates, old downloads, empty folders, blurry media, reviewed Make Room |
| Vault and security | P37, P50 | Encrypted Vault, app lock, biometrics/PIN, hidden-content isolation, privacy audit |
| Backup and import/export | P38, P45 | Local SD/USB backup, restore, sidecars, checksum, clean share, system share |
| Camera capture and processing | P39–P43 | Core viewfinder, real capabilities, Portrait/Slow Motion fixes, Pro/RAW, modes, HDR/Night, motion photos, computational, video/audio |
| Android system integrations | P44–P46 | VIEW/EDIT/SEND/PICK/GET_CONTENT/capture intents, DocumentsProvider, Save to Far, widgets, shortcuts |
| Activity, settings and accessibility | P47–P49 | Activity/Undo Centre, safe mode, logs, settings, index controls, TalkBack, RTL, large screens, localisation |
| Release quality | P50–P52 | Strict offline, security, performance, endurance, E2E validation, release gates |

## Feature coverage notes

1. **Automatic scanning and indexing:** P07/P08; existing implementation must first be audited in P00.
2. **Full-screen media viewer and gestures:** P19.
3. **Video player and resume:** P20.
4. **Albums (create/rename/delete/move/copy/nest/favourite):** P17.
5. **Smart albums / media type collections:** P16.
6. **Search by dates, file names, folders, tags, captions, location, type and OCR:** P21/P22/P24/P25.
7. **Favourites, tags, ratings, filters, sorting:** P15/P18/P21.
8. **Batch selection and operations:** P18/P33.
9. **Photo editing and non-destructive edit stack:** P28/P29/P30.
10. **Trash, restore and retention:** P18/P33/P36.
11. **Slideshow and creations:** P27.
12. **Calendar and timeline scrubber:** P15/P26.
13. **Video trim and compression:** P31.
14. **Metadata viewing/editing and EXIF grouping:** P19/P21/P25/P29/P45.
15. **Duplicate and similar-image review:** P23.
16. **Storage cleanup:** P36.
17. **Media formats and import/export:** P07/P20/P29/P31/P34/P35/P38.
18. **People and pets:** P24/P26.
19. **Map / places:** P25.
20. **Recently Added / On This Day:** P15/P16/P26.
21. **Widgets / shortcuts:** P46.
22. **Themes, grid, accessibility:** P11/P12/P15/P49.
23. **Offline mode and background scanning settings:** P07/P08/P48/P50.
24. **Cross-folder and cross-volume copy/move:** P10/P33.
25. **Files browsing, hidden files, recents, favourites, tags, properties:** P32/P33.
26. **Create/rename/copy/cut/paste/delete/duplicate/share/drag-drop:** P10/P33/P45.
27. **PDF and document previews, OCR, local editor:** P22/P34.
28. **Archive preview, create/extract, password support, safe limits:** P35.
29. **Safe Folder / Vault:** P37.
30. **SD/USB support and eject guidance:** P32/P33/P38.
31. **Camera controls, modes, capture, computational photography and video:** P39–P43.
32. **Android default-app and intent integration:** P44; status and platform limitation must be shown honestly.
33. **No network, no cloud, no telemetry:** P50; must be checked in merged manifest and dependency graph.

---

# PART H — PROGRESS TRACKER

Update this table after every prompt. Use `NOT STARTED`, `IN PROGRESS`, `BLOCKED`, `DONE`, `PARTIAL` or `SKIPPED (verified already done)`.

| Prompt | Task | Status | Commit / evidence | Notes |
|---|---|---|---|---|
| P00 | Repository audit and project truth | NOT STARTED | — | — |
| P01 | Architecture, migration and implementation proposal | NOT STARTED | — | — |
| P02 | Build reproducibility, Gradle and dependency stabilisation | NOT STARTED | — | — |
| P03 | Reliability foundation and unified error model | NOT STARTED | — | — |
| P04 | Database recovery, migrations and metadata preservation | NOT STARTED | — | — |
| P05 | Large-library test fixtures and performance guardrails | NOT STARTED | — | — |
| P06 | Canonical asset identity and Room data model | NOT STARTED | — | — |
| P07 | Incremental media scanner and folder indexing controls | NOT STARTED | — | — |
| P08 | Background indexing, OCR scheduling and Activity integration | NOT STARTED | — | — |
| P09 | Incoming URI resolver and external intent safety | NOT STARTED | — | — |
| P10 | Atomic file operations and operation journal | NOT STARTED | — | — |
| P11 | iOS-inspired design tokens, themes and materials | NOT STARTED | — | — |
| P12 | Icon family, app icon and reusable UI component gallery | NOT STARTED | — | — |
| P13 | Three-destination app shell and adaptive navigation | NOT STARTED | — | — |
| P14 | Onboarding, permission education and permission-state UX | NOT STARTED | — | — |
| P15 | Photos Library timeline, date navigation and grid controls | NOT STARTED | — | — |
| P16 | Collections, media types and smart albums | NOT STARTED | — | — |
| P17 | Album and album-folder management | NOT STARTED | — | — |
| P18 | Selection, favourites, ratings, tags, archive and hidden media | NOT STARTED | — | — |
| P19 | Full-screen viewer, details sheet, compare and Peek Preview | NOT STARTED | — | — |
| P20 | Video player, resume, frame capture and audio controls | NOT STARTED | — | — |
| P21 | Unified search, advanced filters and natural-language query parsing | NOT STARTED | — | — |
| P22 | OCR, document intelligence and text actions | NOT STARTED | — | — |
| P23 | Duplicates, similar photos, burst review and blur detection | NOT STARTED | — | — |
| P24 | On-device scene, object, people and pet intelligence | NOT STARTED | — | — |
| P25 | Places, map view and location privacy | NOT STARTED | — | — |
| P26 | Memories, Featured Photos, events and year-in-review | NOT STARTED | — | — |
| P27 | Creation Studio: slideshow, movie, collage, animation and photo book | NOT STARTED | — | — |
| P28 | Core non-destructive photo editor | NOT STARTED | — | — |
| P29 | Advanced photo tools, markup, RAW and export presets | NOT STARTED | — | — |
| P30 | Advanced on-device AI editing tools | NOT STARTED | — | — |
| P31 | Video editing, trimming, compression and audio tools | NOT STARTED | — | — |
| P32 | Files Browse landing page and multi-format file browser | NOT STARTED | — | — |
| P33 | File operations, batch rename, smart folders and drag-and-drop | NOT STARTED | — | — |
| P34 | File preview, text editor, document scanner and PDF tools | NOT STARTED | — | — |
| P35 | Archives, compression and checksum tools | NOT STARTED | — | — |
| P36 | Storage analyzer, Tidy cleanup and Make Room | NOT STARTED | — | — |
| P37 | Vault, app lock and private media protection | NOT STARTED | — | — |
| P38 | Import/export, local backup and restore | NOT STARTED | — | — |
| P39 | Camera core viewfinder and capture pipeline | NOT STARTED | — | — |
| P40 | Camera mode capability detection and custom controls | NOT STARTED | — | — |
| P41 | Camera Pro controls, RAW, exposure and capture presets | NOT STARTED | — | — |
| P42 | Computational photography and advanced still capture | NOT STARTED | — | — |
| P43 | Camera video capture, stabilisation and audio | NOT STARTED | — | — |
| P44 | Android system intents, picker and DocumentsProvider | NOT STARTED | — | — |
| P45 | Sharing, Save to Far and metadata-clean export | NOT STARTED | — | — |
| P46 | Widgets, launcher shortcuts and Quick Settings | NOT STARTED | — | — |
| P47 | Activity Centre, Undo Centre, diagnostics and Safe Mode | NOT STARTED | — | — |
| P48 | Settings, privacy dashboard and guided preferences | NOT STARTED | — | — |
| P49 | Accessibility, localisation and expanded-device layouts | NOT STARTED | — | — |
| P50 | Privacy, security and strict-offline audit | NOT STARTED | — | — |
| P51 | Performance, endurance, fault-injection and device matrix | NOT STARTED | — | — |
| P52 | Final integration, release gate and handover | NOT STARTED | — | — |

---

# PART I — FINAL RELEASE CHECKLIST

Do not call Far Gallery production-ready until the applicable items below are checked with evidence.

## Product and functionality

- [ ] Photos, Camera and Files use the same asset identity; one capture appears once.
- [ ] Automatic and manual scanning work, can be paused, resumed and rebuilt.
- [ ] Timeline, collections, albums, search, filters, tags, ratings and selections persist.
- [ ] Viewer, compare, video playback and exports work on actual device files.
- [ ] Photo and video editing preserve originals by default.
- [ ] Camera controls and modes are truthful to device capability.
- [ ] File copy/move/rename/archive/PDF operations survive cancellation and process death.
- [ ] Trash, undo, restore and permanent-delete warnings work.
- [ ] Vault data is isolated from every non-Vault view and service.
- [ ] Backup restores onto a clean install with metadata and edits.
- [ ] Every visible control performs its advertised action.

## Android integration

- [ ] VIEW, SEND, PICK, GET_CONTENT and supported capture intents are tested.
- [ ] EXTRA_OUTPUT and URI grants work for camera capture requests.
- [ ] DocumentsProvider exposes only user-authorised content.
- [ ] Integration status screen explains system-managed and unsupported behaviors.
- [ ] No claim is made that the app universally replaces OEM apps or the hidden gallery role.

## Offline and security

- [ ] Strict-offline merged manifest has no android.permission.INTERNET.
- [ ] No analytics, ads, cloud, remote API, telemetry or remote model download dependencies.
- [ ] App core functions work in airplane mode.
- [ ] Sensitive media is not present in logs, notifications, widgets, recents or thumbnails.
- [ ] Encryption, authentication, backup and recovery paths are tested.
- [ ] Secure deletion wording accurately describes flash-storage limitations.

## Performance and quality

- [ ] Large-library paging and cursor scanning are tested.
- [ ] No known hardcoded analysis query ceiling remains.
- [ ] No main-thread disk/database/hash/decode work exists.
- [ ] Camera launch, shutter, scroll jank, memory and thermal impact are measured.
- [ ] Corrupt-file and provider-failure tests do not crash the app.
- [ ] No critical crash, ANR or data-loss issue remains open.
- [ ] Low, mid and high device classes have been tested where available.

## Design and accessibility

- [ ] Light, Dark, AMOLED and High Contrast are reviewed.
- [ ] Reduce Motion and Reduce Transparency are respected.
- [ ] TalkBack, 200% font scale, RTL and keyboard/large-screen layouts are tested.
- [ ] Touch targets are at least 48dp.
- [ ] Loading, empty, partial-access, unavailable and error states are designed.
- [ ] Animations do not cause jank or block interaction.

---

# PART J — OWNER DECISION LOG

The agent must not silently decide these product-level questions. Keep decisions here:

| Decision | Current default | Approval required to change? |
|---|---|---|
| App brand | Keep Far Gallery | Yes |
| Platform | Android 11+, native Kotlin/Compose | Yes |
| Privacy | Strict offline, no INTERNET permission | Yes |
| LAN / SMB / WebDAV / Wi-Fi Direct sockets | Excluded from strict-offline build | Yes, separate edition |
| Framework | Preserve existing architecture | Yes |
| Original photo/video edits | Save Copy by default | Yes |
| Delete behavior | Review + Trash/Undo; second confirmation for permanent deletion | Yes |
| AI model delivery | Bundled or explicit local import only; no remote download | Yes |
| Broad storage permission | Prefer MediaStore + SAF; justify separately if required | Yes |
| Default-app claims | Supported intent handlers only, honest device status | Yes |

---

# FINAL INSTRUCTION

**Build a dependable offline media app first, make it beautiful second, and make it intelligent third.** Far Gallery succeeds when Photos, Camera and Files genuinely share one data model, core actions never lose user content, the app tells the truth about device and Android limitations, and the iOS-inspired interface stays fast, accessible and understandable on real Android phones.

Do not chase a feature count. Complete each prompt, test it, commit it, review the phase, and only then continue.

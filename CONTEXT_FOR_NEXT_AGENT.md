# CONTEXT FOR NEXT AGENT

> Handoff context for the next agentic worker. **Updated 2026-09-09**: vulnerability-audit
> round (P0 IME Compose-host crash fix + download integrity hardening) and forced dark
> theme. Earlier: 2026-08-28 MVP Tasks 1–5 + model-download P0 fix + multi-dialect
> architecture. Read this before doing anything else.

## 2026-09-09 change round (vulnerability audit + dark theme)

**P0 — IME Compose host crash (fixed):** `RoarImeService.onCreateInputView` put a
ComposeView into the IME window without any `ViewTreeLifecycleOwner` /
`ViewTreeSavedStateRegistryOwner`. compose-ui 1.7's `AndroidComposeView.onAttachedToWindow`
**hard-requires both** (bytecode verified: throws `IllegalStateException("Composed into the
View which doesn't propagate ViewTreeLifecycleOwner!")`) — the keyboard would crash on first
launch on every real device. All 56 existing tests missed it because none starts the IME
service. **Fix:** the service now implements `LifecycleOwner` + `SavedStateRegistryOwner`,
sets both owners on the ComposeView (Kotlin side must use the **View extension functions**
`setViewTreeLifecycleOwner`/`setViewTreeSavedStateRegistryOwner` — the `ViewTreeLifecycleOwner.set`
static shape is a file-facade class that Kotlin cannot import), maps
`onStartInputView→RESUMED`, `onFinishInputView→PAUSED`, `onWindowHidden→STOPPED`,
`onDestroy→DESTROYED`. Regression tests: `RoarImeServiceTest` (3 tests, view-tree owners +
lifecycle mapping).

**P1 — download integrity hardening:** size + SHA-256 verification moved **inside** the
official/mirror fallback loop (`downloadVerified`) — a corrupt official-source download now
falls through to the mirror instead of failing the batch. Added a process-wide
`DOWNLOAD_IN_FLIGHT` AtomicBoolean guard (Activity recreation creates a new
SettingsController; two threads writing the same `.part` file corrupt it) plus
`isDownloadRunning()`/`currentDownloadProgress()` so a new controller instance shows the
in-flight download instead of offering a second concurrent one.

**Forced dark theme:** new `ui/Theme.kt` (`RoarTheme` + `forcedDarkColorScheme(context, sdkInt)`:
dynamic dark ≥ API 31, `darkColorScheme()` fallback below; **no** `isSystemInDarkTheme`
branch — product decision) + `res/values/themes.xml` (`Theme.Roar`, parent
`Theme.Material3.Dark.NoActionBar`, prevents the startup white flash) + manifest switched.
All three hosts (MainActivity / SettingsActivity / RoarImeService) wrap content in
`RoarTheme`. Lint note: `dynamicDarkColorScheme` needs a **literal**
`Build.VERSION.SDK_INT >= S` guard — lint cannot track the injectable `sdkInt` parameter.

**Test count:** 56 → 64, all green. Quality gate `testDebugUnitTest lintDebug assembleDebug`
passed (0 lint errors; 3 pre-existing warnings: DataExtractionRules, ObsoleteSdkInt mipmap,
MissingApplicationIcon — untouched this round).

## Project state

| Item | Status | Commit |
|---|---|---|
| MVP Tasks 1–5 (scaffold → IME wiring) | ✅ | 8ee2ae7 |
| **P0: model download pipeline** (settings download button + progress + mirror fallback + state refresh) | ✅ | (this change) |
| **Multi-dialect architecture** (DialectSpec/DialectRegistry, per-dialect model dirs, dialect picker + persistence) | ✅ | (this change) |
| Real-device verification | ⏳ user-owned | — |

## What changed in the 2026-08-28 fix round

**P0 root cause**: `ModelProvider.downloadModels()` had **zero call sites** — the
settings screen had no download entry point and first-run auto-download was never
implemented. Since the 238 MB model is not bundled in assets, `isModelReady` was
permanently false → phone APK always showed 「模型未就绪」and recording always errored.
The APK was effectively unusable on a real device.

**Fixes**:
1. `ui/SettingsController.kt` (new): dialect selection (SharedPreferences `roar_settings`,
   key `selected_dialect_id`) + `ModelDownloadState` state machine
   (NotDownloaded/Downloading/Ready/Failed). `downloadModel()` runs on a background
   thread, progress via `mutableStateOf` (cross-thread safe) → Compose auto-refresh.
2. `ui/SettingsScreen.kt`: dialect dropdown (currently 1 option, 粵語) + 「下载模型」
   button + MB progress bar + failure message + 「重试下载」.
3. `asr/ModelProvider.kt`: parameterized by `DialectSpec`; per-dialect model dir
   `filesDir/models/<dialectId>/`; **mirror fallback** — official huggingface.co first,
   hf-mirror.com on failure (per-file, `.part` temp + size/SHA-256 verify; partial
   downloads resume on retry). Connect timeout lowered to 15 s for fast failover.
4. `asr/SherpaRecognizer.kt`: takes `DialectSpec`; recognizer built from that dialect's
   model files. Old `isModelReady(context)` overload kept for compatibility.
5. `ime/RoarImeService.kt`: resolves dialect from prefs on `onCreateInputView`
   (switch takes effect on next IME start), loads that dialect's dict + recognizer;
   mic label shows dialect name (`按住講粵語`).
6. `core/` new files: `DialectSpec.kt` (DialectSpec/NormalizerRule/ModelFileSpec/ModelSpec),
   `DialectRegistry.kt` (builtin dialects, unknown-id fallback), `CantoneseRules.kt`
   (rules extracted from TextNormalizer). `TextNormalizer.normalize(input, dict, rules)`
   — rules injectable, defaults to Cantonese (existing tests unchanged).

## Architecture recap

- Package/namespace: `com.xieguiawu.roar`; versionCode 1, versionName "0.1.0".
- Two-layer transcription (see `ARCHITECTURE.md` §9 for multi-dialect design):
  1. **ASR**: `asr/SherpaRecognizer.kt` — sherpa-onnx AAR v1.13.6 (local `app/libs/`,
     GitHub Release; no official mavenCentral coordinate), streaming **paraformer
     trilingual (zh/Cantonese/en) int8** model, downloaded per dialect to
     `filesDir/models/<dialectId>/` (`ModelProvider`, mirror fallback).
  2. **Orthography engine**: `core/` pure Kotlin — `DialectDictionary` (510-entry JSON at
     `app/src/main/assets/dialect/cantonese_dict.json`), `TextNormalizer` (dialect
     rule sets, default Cantonese), `CandidateRanker` (frequency + bigram context).
  3. **IME**: `ime/RoarImeService.kt` (per-dialect loading) + `ime/ImePipeline.kt`
     (pure, unit-tested pipeline) + `ime/CandidateBar.kt` (Compose candidate bar +
     press-and-hold mic, dialect label). End-to-end flow fully wired.
- Dialect registry: `core/DialectRegistry.kt` — add a dialect = register a
  `DialectSpec` (dict asset + rule set + model spec) + registry tests; settings/IME/
  download pipeline pick it up automatically. "Dialect plan marketplace" local base
  (S3: server-pushed registry).

## Build & quality gate (all green as of this commit)

```bash
cd ~/Desktop/android-projects/Roar
./gradlew testDebugUnitTest lintDebug assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

- Gradle 8.9 / AGP 8.5.2 / Kotlin 2.0.21 / Compose BOM 2024.10.00 / minSdk 26 / targetSdk 35 / JDK 17
- Tests: core (normalizer/dictionary/ranker) + ime (ImePipeline) + asr (ModelProvider sha256)
  + ui (Robolectric settings screen). Lint runs clean (no errors).

## Open items (not blockers for MVP build)

1. **Real-device verification (user-owned, highest priority)** — checklist in README_zh/README:
   settings-page model download (~238 MB, **now has a UI entry point + mirror fallback**),
   press-and-hold mic → partial stream → final text → candidates → commit to a host app;
   airplane-mode offline check. Known unknown: paraformer trilingual model outputs
   Mandarin-style text for Cantonese speech (e.g. 无该), which the normalization layer
   is designed to correct — real-device quality of this correction is **unverified**.
2. **Model integration status** — code is real (not stub) and the download pipeline is
   wired end-to-end (button → progress → ready state), but no device has run the full
   path yet. Until a real device passes the checklist, treat "ASR works" as unproven.
3. **Dictionary expansion** — 510 entries is a starter set (plan §5 targets 500–1000).
   Only add entries you are 100% sure of (correct hanzi + jyutping with tone digits 1–6).
   No uncertain entries.
4. **Second dialect** — architecture is ready (DialectSpec/Registry); next candidates
   to research when online: sherpa-onnx model zoo status for Hokkien/Taiwanese (track B
   shared-model route) — needs web check (HF API unreachable from this network as of
   2026-08-28). "Orthography-first" path: dict + rules can ship before any ASR model.
5. **Candidate context** — `lastContext` bigram bonus exists but is naive (tail-2-char
   substring match). Could improve with real bigram frequencies in S2.
6. **Download UX edge cases** — no "download in progress" guard across Activity recreation
   (progress resets to NotDownloaded, partial files resume on next click — acceptable);
   no background-service download (app killed mid-download loses progress UI).

## Conventions (must keep)

- Kotlin with zero new warnings; no `as any` / `@Suppress` to silence errors.
- Every task ends with the quality gate above + pasted output; commit with English
  conventional messages (`feat:`/`test:`/`docs:`/…).
- No voice/dictionary data leaves the device. Only `RECORD_AUDIO` permission.
- Graph: `graphify-out/` knowledge graph exists at repo root; run `graphify update .`
  after meaningful code changes (may be stale after Task 5 edits — refresh before relying
  on graph answers).
- Server credentials live only in `docs/ASSET_INVENTORY.md` (gitignored); never commit them.

## Known quirks

- `SherpaRecognizer.stop()` interrupts the blocking `AudioRecord.read` by stopping the
  recorder, then flushes the tail segment; callbacks are posted to the main looper.
- `DialectDictionary.loadFromJson` throws on malformed JSON — `RoarImeService` wraps the
  load in `runCatching` with an empty-dictionary fallback so the IME never crashes on a
  bad asset.
- `TextNormalizer` rules include compound-word exceptions (e.g. 系统的「系」不转「係」);
  new rules must follow the same `NormalizerRule(from, to, exceptions, requireCjkBefore)`
  shape, registered in the dialect's `DialectSpec.rules` (Cantonese = `CantoneseRules.RULES`).
- Settings screen text uses mixed simplified/traditional deliberately (targets Cantonese
  users who commonly type simplified); don't "fix" it without a product decision.
- Kotlin `object` initialization order: `DialectRegistry.dialects` must be a computed
  getter (`get() = listOf(CANTONESE)`), not a direct `listOf(CANTONESE)` — the latter
  fails with "Variable 'CANTONESE' must be initialized" (declaration order).
- compose-ui 1.7 ViewTree owners: in Kotlin use the View **extension functions**
  `setViewTreeLifecycleOwner` / `setViewTreeSavedStateRegistryOwner`; the static
  `ViewTreeLifecycleOwner.set(...)` shape lives in a `@file:JvmName` facade class that
  Kotlin resolves as *unresolved reference* (Java-only). Same for
  `findViewTreeLifecycleOwner` / `findViewTreeSavedStateRegistryOwner` getters.
- `createComposeRule()` (bare rule) fails in Robolectric with "Unable to resolve activity
  for ... androidx.activity.ComponentActivity" — this app has no bare ComponentActivity in
  the manifest. Use `createAndroidComposeRule<MainActivity>()`.
- `captureToImage()` times out under Robolectric (`forceRedraw` never satisfied) —
  do not use it in unit tests; assert `window.decorView.background` / scheme values instead.

## Remote resources (high-performance work)

- hpc-server: ssh alias only — host, port, user and credentials are NOT stored in this repo (see local private memory; docs/ASSET_INVENTORY.md is git-ignored)
- 8-core x86_64 / 15Gi RAM / 156Gi disk / NO GPU / Python anaconda + Docker 26.1.1
- Use for: Python data pipelines, Go server (S2), model downloads. NOT for: Android builds (local SDK), GPU training (no GPU).

## Knowledge graph

- graphify-out/: rebuilt 2026-09-09 (231 nodes / 294 edges / 29 communities)

## Last updated

2026-09-09 (vulnerability audit: P0 IME Compose-host crash fix, download integrity
hardening, process-wide download guard; forced dark theme; 64 tests green)

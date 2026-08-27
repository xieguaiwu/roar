# CONTEXT FOR NEXT AGENT

> Handoff context for the next agentic worker. Written 2026-08-28, after completing
> `docs/plans/2026-08-28-roar-mvp.md` Tasks 1–5. Read this before doing anything else.

## Project state (all plan tasks DONE)

| Task | Status | Commit |
|---|---|---|
| 1 — Gradle scaffold + minimal buildable app | ✅ | `build: scaffold Roar android project` |
| 2 — Orthography engine (`core/`, pure Kotlin + tests) | ✅ | `feat: add cantonese orthography engine with homophone mapping` |
| 3 — sherpa-onnx ASR layer | ✅ | `feat: integrate sherpa-onnx ASR layer (cantonese model)` |
| 4 — IME skeleton + settings screen | ✅ | `feat: add IME service skeleton and settings screen` |
| 5 — End-to-end wiring, tests, docs | ✅ | `docs: wire end-to-end pipeline and add project docs` |

## Architecture recap

- Package/namespace: `com.xieguiawu.roar`; versionCode 1, versionName "0.1.0".
- Two-layer transcription (see `ARCHITECTURE.md`):
  1. **ASR**: `asr/SherpaRecognizer.kt` — sherpa-onnx AAR v1.13.6 (local `app/libs/`,
     GitHub Release; no official mavenCentral coordinate), streaming **paraformer
     trilingual (zh/Cantonese/en) int8** model. The plan assumed a Cantonese streaming
     zipformer model, which **does not exist** in the k2-fsa zoo — paraformer was chosen
     instead (recorded in `asr/ModelProvider.kt` and `ARCHITECTURE.md`).
  2. **Orthography engine**: `core/` pure Kotlin — `DialectDictionary` (510-entry JSON at
     `app/src/main/assets/dialect/cantonese_dict.json`), `TextNormalizer` (homophone rules),
     `CandidateRanker` (frequency + bigram context).
  3. **IME**: `ime/RoarImeService.kt` + `ime/ImePipeline.kt` (pure, unit-tested pipeline:
     ASR text → `TextNormalizer.normalize` → `CandidateRanker.rank`) + `ime/CandidateBar.kt`
     (Compose candidate bar + press-and-hold mic). End-to-end flow is fully wired;
     no hardcoded candidates remain.
- Model files are **not bundled** (238 MB > 100 MB threshold). First run downloads from
  HuggingFace `csukuangfj/sherpa-onnx-streaming-paraformer-trilingual-zh-cantonese-en`
  into app-private filesDir with per-file size + SHA-256 verification (`ModelProvider`).

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
   first-run model download (~238 MB), press-and-hold mic → partial stream → final text →
   candidates → commit to a host app; airplane-mode offline check. Known unknown: paraformer
   trilingual model outputs Mandarin-style text for Cantonese speech (e.g. 无该), which the
   normalization layer is designed to correct — real-device quality of this correction is
   **unverified**.
2. **Model integration status** — code is real (not stub), but no device has run the full
   path yet. Until a real device passes the checklist, treat "ASR works" as unproven.
3. **Dictionary expansion** — 510 entries is a starter set (plan §5 targets 500–1000).
   Only add entries you are 100% sure of (correct hanzi + jyutping with tone digits 1–6).
   No uncertain entries.
4. **Phase-2 Go server** (`server/`) — dictionary distribution, anonymous feedback loop,
   subscription. Only documented in ARCHITECTURE §8; nothing implemented.
5. **Candidate context** — `lastContext` bigram bonus exists but is naive (tail-2-char
   substring match). Could improve with real bigram frequencies in S2.

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
  new rules must follow the same `Rule(from, to, exceptions)` shape.
- Settings screen text uses mixed simplified/traditional deliberately (targets Cantonese
  users who commonly type simplified); don't "fix" it without a product decision.

## Remote resources (high-performance work)

- hpc-server: root@<redacted-host> (credentials in docs/ASSET_INVENTORY.md, git-ignored)
- 8-core x86_64 / 15Gi RAM / 156Gi disk / NO GPU / Python anaconda + Docker 26.1.1
- Use for: Python data pipelines, Go server (S2), model downloads. NOT for: Android builds (local SDK), GPU training (no GPU).

## Knowledge graph

- graphify-out/: pending first build (2026-08-28)

## Last updated

2026-08-28

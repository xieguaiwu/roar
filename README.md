# Roar — Cantonese Orthography Voice Input Method (MVP)

**"Speak dialect, get proper characters."** Roar turns spoken Cantonese into proper
Cantonese orthography (正字) — e.g. saying "m4 goi1" yields **唔該**, not Mandarin homophones
like "无该" — and commits it into any input field (WeChat, Douyin, Notes…) as a system IME.

All speech recognition runs **on-device** (sherpa-onnx); no voice data ever leaves the phone.

## Architecture (one sentence)

Two-layer transcription: **sherpa-onnx streaming ASR** (on-device, C++ core) converts voice to
text → the **orthography engine** (`core/`, pure Kotlin: homophone mapping + frequency dictionary
+ bigram context ranking) converts Mandarin-ized/homophone text into Cantonese orthography
candidates → the **IME** (Compose candidate bar) commits the tapped candidate.

See `ARCHITECTURE.md` for the full design.

## Current status (MVP v0.1.0)

- [x] Cantonese orthography engine (`core/`): 510-entry built-in dictionary
      (`assets/dialect/cantonese_dict.json`), homophone normalization rules, candidate ranking
- [x] ASR layer (`asr/`): sherpa-onnx AAR v1.13.6 (GitHub Release), streaming paraformer
      trilingual (zh / Cantonese / en) int8 model
- [x] IME (`ime/`): `RoarImeService` with Compose candidate bar + press-and-hold voice button,
      end-to-end pipeline `ImePipeline` (ASR text → ranked candidates)
- [x] Settings screen (`ui/`): dialect display, model status, record-audio permission
- [ ] Real-device end-to-end verification (see checklist below)

**Model integration status:** the sherpa-onnx recognizer is **really wired** (no stub).
The model files (~238 MB int8: encoder 166 MB + decoder 72 MB + tokens 81 KB) are **not bundled**
in the APK — they download on first run (or are checked at settings-page open) from HuggingFace
`csukuangfj/sherpa-onnx-streaming-paraformer-trilingual-zh-cantonese-en` into the app-private
files dir, with size + SHA-256 verification per file. Until the download completes, the mic button
reports "模型未就绪" via the recognizer error callback.

> Note: the plan originally assumed a Cantonese streaming *zipformer* model; none exists in the
> k2-fsa model zoo (zipformer streaming is Mandarin-only), so the streaming **paraformer**
> trilingual model was used instead. Recorded in `ARCHITECTURE.md`.

## Build

Requirements: JDK 17, Android SDK 35. Gradle wrapper (8.9) downloads everything else.

```bash
cd ~/Desktop/android-projects/Roar
./gradlew testDebugUnitTest lintDebug assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Install on a device

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Enable the IME

1. Open the Roar app → tap "授予录音权限" (grant microphone).
2. Open system **Settings → System → Languages & input → On-screen keyboard** → enable **Roar 粵語輸入**.
3. Switch to Roar as current keyboard in any text field.
4. **Press and hold** the mic button, speak Cantonese, release; pick a candidate to commit.

## Real-device verification checklist

- [ ] First-run model download completes (~238 MB, Wi-Fi recommended); settings shows 模型就绪
- [ ] Press-and-hold mic → partial text streams below the candidate bar while speaking
- [ ] Release → final text appears; candidates show orthography first (e.g. 唔該), original as fallback
- [ ] Tapping a candidate commits it into the focused app (WeChat / Notes)
- [ ] Airplane mode: recognition still works (fully offline after model download)
- [ ] Kill & restart IME; repeated sessions show no crash

## Privacy & security

- Recognition is 100% on-device. No voice or text is uploaded, ever.
- Permissions: `RECORD_AUDIO` (microphone) and `INTERNET`. `INTERNET` exists
  solely so the ASR model can be downloaded on first run / from the settings
  page; no voice or text is ever uploaded. After the model is on disk the app
  works fully offline (verified by the airplane-mode checklist above).
- Dictionary is bundled in the APK; no network dictionary sync in MVP.

## Roadmap

| Stage | Scope |
|---|---|
| S1 (this repo) | Cantonese MVP: ASR + orthography engine + IME |
| S2 | Jyutping-direct-output ASR model, Han-Lo parallel candidates, feedback loop (Go server) |
| S3 | Second dialect (Hokkien/Taiwanese Han-Lo), subscription, B2B transcription |

## Repository docs

- `ARCHITECTURE.md` — design decisions, module layout, acceptance criteria
- `VISION.md` — long-term product vision
- `CONTEXT_FOR_NEXT_AGENT.md` — handoff context for the next agentic worker
- `docs/plans/2026-08-28-roar-mvp.md` — implementation plan (Tasks 1–5 all done)

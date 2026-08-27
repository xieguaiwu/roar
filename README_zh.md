# Roar — 粤语正字语音输入法（MVP）

**「說方言，出正字。」** Roar 把粤语口语转成正确粤语正字——说「m4 goi1」出来的是
**唔該**，而不是普通话谐音「无该」——并作为系统输入法把候选上屏到任意输入框
（微信、抖音、备忘录……）。

语音识别全程在**手机端侧**完成（sherpa-onnx），语音数据永不离开手机。

## 架构（一句话）

两层转写：**sherpa-onnx 流式 ASR**（端侧，C++ 内核）把语音转成文本 →
**正字引擎**（`core/`，纯 Kotlin：谐音映射 + 词频词典 + 上下文 bigram 排序）把普化/谐音文本
转成粤语正字候选 → **IME**（Compose 候选栏）点选上屏。

完整设计见 `ARCHITECTURE.md`。

## 当前状态（MVP v0.1.0）

- [x] 正字引擎（`core/`）：内置词典 510 条（`assets/dialect/cantonese_dict.json`）、
      谐音正字化规则、候选排序
- [x] ASR 层（`asr/`）：sherpa-onnx AAR v1.13.6（GitHub Release），流式 paraformer
      三语（普通话/粤语/英语）int8 模型
- [x] IME（`ime/`）：`RoarImeService` + Compose 候选栏 + 按住说话按钮，
      端到端管道 `ImePipeline`（ASR 文本 → 排序候选）
- [x] 设置页（`ui/`）：方言展示、模型状态、录音权限
- [ ] 真机端到端验证（见下方清单）

**模型接入状态：** sherpa-onnx 识别器为**真实接入**（非 stub）。模型文件（约 238 MB int8：
encoder 166 MB + decoder 72 MB + tokens 81 KB）**不打包进 APK**——首次运行从 HuggingFace
`csukuangfj/sherpa-onnx-streaming-paraformer-trilingual-zh-cantonese-en` 下载到应用私有目录，
逐文件校验体积与 SHA-256。下载完成前，按麦克风按钮会经识别器错误回调提示「模型未就绪」。

> 注：原计划假设存在粤语流式 *zipformer* 模型；k2-fsa 模型库中并无此模型（zipformer 流式
> 仅普通话），故改用流式 **paraformer** 三语模型。已在 `ARCHITECTURE.md` 如实记录。

## 构建

要求：JDK 17、Android SDK 35。Gradle wrapper（8.9）自动下载其余依赖。

```bash
cd ~/Desktop/android-projects/Roar
./gradlew testDebugUnitTest lintDebug assembleDebug
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`

## 真机安装

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 启用输入法

1. 打开 Roar 应用 → 点「授予录音权限」。
2. 进入**系统设置 → 系统 → 语言与输入法 → 屏幕键盘** → 启用 **Roar 粵語輸入**。
3. 在任意输入框把 Roar 切换为当前键盘。
4. **按住**麦克风按钮说粤语，松开出候选，点选上屏。

## 真机验证清单

- [ ] 首次模型下载完成（约 238 MB，建议 Wi-Fi）；设置页显示「模型就绪」
- [ ] 按住麦克风说话，候选栏下方实时滚动部分识别文本
- [ ] 松开后出现最终文本；候选首位为正字（如 唔該），原文为保底候选
- [ ] 点选候选可上屏到目标应用（微信 / 备忘录）
- [ ] 飞行模式下识别仍可用（模型下载后完全离线）
- [ ] 反复切换/重启输入法无崩溃

## 隐私与安全

- 识别 100% 端侧完成，语音与文本永不上传。
- 仅申请 `RECORD_AUDIO` 权限；应用本身无 INTERNET 权限（模型下载仅在用户打开设置页时触发）。
- 词典随 APK 内置，MVP 阶段无网络词典同步。

## 路线图

| 阶段 | 内容 |
|---|---|
| S1（本仓库） | 粤语 MVP：ASR + 正字引擎 + IME |
| S2 | 粤拼直出 ASR 模型、汉罗并行候选、反馈闭环（Go 服务端） |
| S3 | 第二方言（闽南语/台语汉罗）、订阅、B 端转写 |

## 仓库文档

- `ARCHITECTURE.md` — 设计决策、模块划分、验收标准
- `VISION.md` — 长期产品愿景
- `CONTEXT_FOR_NEXT_AGENT.md` — 下一个 agent 的交接上下文
- `docs/plans/2026-08-28-roar-mvp.md` — 实施计划（Task 1–5 已全部完成）

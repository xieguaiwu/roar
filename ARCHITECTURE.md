# Roar — 方言正字语音输入法：架构设计 v0.1

> 状态：MVP 阶段（2026-08-28 立项）。目标方言：粤语（正字生态最成熟）。
> 本文档是架构决策的事实来源。实施计划见 `docs/plans/`。

---

## 1. 产品定位

**一句话**：让用户"说方言，出正字"——语音转成方言拼音（音标层），再映射为方言正字（转录层），在微信/抖音等任意输入框内使用。

**差异化（相对讯飞"方言版普通话听写"）**：

| 维度 | 讯飞 | Roar |
|---|---|---|
| 输出 | 普通话谐音文字（"无该"） | 方言正字（"唔該"）、汉罗并行 |
| 覆盖 | 202 种方言浅覆盖 | 单方言做深（正字库+文白异读+俚语） |
| 隐私 | 云端上传 | 端侧推理（sherpa-onnx） |
| 商业模式 | 免费+广告 | 干净+订阅（后期） |

## 2. 核心架构：两层转写（对应产品灵魂）

```
语音输入
   │
   ▼
┌─────────────────────────┐
│ 层1：音标层（ASR）       │  sherpa-onnx（端侧，C++ 内核）
│  语音 → 方言拼音/文本     │  流式 paraformer 三语模型（zh/粤/en）
└─────────────────────────┘
   │ 音标流（粤拼 Jyutping）/ 方言文本
   ▼
┌─────────────────────────┐
│ 层2：正字引擎（映射层）   │  Kotlin 纯逻辑库（core/）
│  音标/文本 → 正字候选     │  词典 + 词频消歧 + 文白异读规则
└─────────────────────────┘
   │ 候选列表（候选 1..N）
   ▼
┌─────────────────────────┐
│ IME 上屏                 │  Android InputMethodService
│ 用户点选 → 提交到任意 App │  Compose 候选栏
└─────────────────────────┘
   │ 点选反馈（用户选择 = 训练信号）
   ▼
┌─────────────────────────┐
│ Go 服务端（二期）         │  词典分发、匿名反馈闭环、订阅
└─────────────────────────┘
```

**关键设计决策**：

1. **音标层与映射层解耦**：正字引擎只消费"音标流/方言文本"，不感知 ASR 实现。未来替换 ASR 模型（如换成直接输出粤拼的模型）不影响引擎。
2. **MVP 音标来源**：sherpa-onnx 粤语模型当前输出汉字文本。MVP 走"方言文本 → 正字化"管线（把普化字/谐音字纠正为正字）；引擎同时保留"粤拼流输入"接口，为音标直出模型预留。
   ⚠️ 计划原假设"粤语流式 zipformer"模型不存在（k2-fsa 模型库 zipformer 流式仅普通话），
   实际选用**流式 paraformer 三语（普通话/粤语/英语）int8** 模型（约 238MB，首次运行下载），
   见 `asr/ModelProvider.kt`。
3. **正字引擎无状态纯函数**：输入（音标序列/文本+上下文）→ 输出（候选列表）。单测友好，未来可平移 Go/Rust（gomobile）或服务端复用。

## 3. 模块划分

```
Roar/
├── app/src/main/java/com/xieguiawu/roar/
│   ├── ime/RoarImeService.kt      InputMethodService（键盘+录音+候选上屏）
│   ├── ime/ImePipeline.kt         ★端到端管道（纯函数：ASR文本→正字候选，可单测）
│   ├── ime/CandidateBar.kt        Compose 候选栏（横排候选，点选上屏）
│   ├── ui/SettingsActivity.kt     配置页入口（IME 设置 activity）
│   ├── ui/SettingsScreen.kt       配置页内容（Compose，与 MainActivity 共用）
│   ├── asr/SherpaRecognizer.kt    sherpa-onnx 封装（录音→文本回调）
│   ├── asr/ModelProvider.kt       模型文件定位/下载/SHA-256 校验
│   ├── asr/RecognizerListener.kt  识别回调接口（partial/final/error）
│   └── core/                      ★正字引擎（纯 Kotlin 库，无 Android 依赖）
│       ├── DictEntry.kt           词典条目模型（hanzi/jyutping/freq/note）
│       ├── DialectDictionary.kt   词典加载（JSON：粤拼/文本 → 正字候选+词频）
│       ├── TextNormalizer.kt      普化文本 → 粤语正字化（谐音映射规则）
│       └── CandidateRanker.kt     候选排序（词频 + 上下文 bigram）
├── app/src/main/assets/           内置粤语词典 JSON（510 条）+ ASR 模型（首次运行下载）
├── app/src/main/res/xml/          IME 元数据（method.xml）+ 网络安全配置
└── server/                        （二期，Go）— 本期仅文档约定 docs/server-api.md
```

## 4. 技术选型（含语言决策，回应 Go 适配性评估）

| 层 | 选型 | 理由 |
|---|---|---|
| ASR 推理 | **sherpa-onnx Android AAR**（C++ 内核） | 端侧、离线、流式 paraformer 三语模型、官方 Android 支持 |
| 正字引擎 | **Kotlin（MVP）/ Go（服务端+远期核心）** | MVP 快速验证，避免 gomobile 边界风险（go.dev/wiki/Mobile 标注实验性质）；接口纯函数化，未来可平移 |
| 客户端壳 | Kotlin + Compose（IME 必须原生） | InputMethodService 仅 Kotlin/Java 可用 |
| 服务端（二期） | **Go**（Gin + WebSocket + Postgres） | 词典分发、反馈闭环、订阅；Go 生态强项 |
| 模型训练（三期） | Python（funASR/ESPnet 微调粤拼输出模型） | 训练生态 Python 垄断 |

**Go 在本项目的边界**（2026-08-28 调研结论）：
- Go 适配：服务端 ✅、工具链/词典构建 ✅
- Go 有官方绑定的端侧推理：sherpa-onnx Go API / vosk / whisper.cpp ✅（MVP 用 Android AAR 更稳）
- Go 不做：IME 壳（Kotlin/Swift）、模型训练（Python）、纯 Go 推理引擎（生态未成熟）
- MVP 正字引擎用 Kotlin 的理由：与 IME 同进程零 FFI 开销、Robolectric 直测、迭代快；引擎接口按"可平移"设计，需求验证后再决定是否 Go 化

## 5. 数据与词典

- **词典格式**：JSON，条目 = `{ "jyutping": "m4 goi1", "hanzi": "唔該", "freq": 850, "note": "謝謝" }`
- **MVP 词典规模**：500-1000 条高频粤语字词（正字 + 粤拼 + 词频），手工整理 + 开源词表（粤典/粵語審音配詞字庫）筛选
- **文白异读**：词典条目可含 `literary`/`colloquial` 双音字段（如"學" hok6/o6），MVP 先存规则表接口，排序用词频兜底
- **反馈闭环（二期）**：用户点选候选 → 匿名上报 → 服务端更新词频 → 词典版本分发

## 6. 隐私与安全

- 全部识别端侧完成，语音不上传（MVP 无网络权限需求，除模型下载外）
- 模型文件首次运行下载 → 存 app 私有目录，校验 SHA-256
- 不申请麦克风外的敏感权限；IME 需用户手动启用（系统安全机制）

## 7. MVP 验收标准（v0.1）

1. ✅ `./gradlew testDebugUnitTest lintDebug` 全绿（Task 5 质量门通过）
2. ✅ 正字引擎单测覆盖：词典加载、正字化（"无该"→"唔該"）、候选排序、端到端管道
   （ImePipelineTest；粤拼音节模型 JyutpingSyllable 未建，MVP 用谐音映射规则替代）
3. ✅ `./gradlew assembleDebug` 产出 APK，可安装（`app-debug.apk`）
4. ✅ IME 可启用：设置页 → 启用 Roar 键盘 → 任意输入框调出 → 语音按钮录音 → 候选栏出现 → 点选上屏
   （代码链路已接通；模型首次运行需下载约 238MB）
5. ⏳ 真机验证（用户执行）：华为/其他 Android 手机录音转正字端到端（清单见 README_zh.md）

## 8. 演进路线（VISION 摘要）

| 阶段 | 内容 | 语言 |
|---|---|---|
| S1（本期） | 粤语 MVP：ASR+正字引擎+IME 骨架 | Kotlin |
| S2 | 音标直出模型（粤拼 token）+ 汉罗并行候选 + 反馈闭环 | Python 训练 + Go 服务端 |
| S3 | 第二方言（闽南语/台语汉罗）、订阅变现、B/G 端转写 | Go/Kotlin |
| L | 音素层通用化，方言方案市场（勾选方言=加载方案） | — |

---

*最后更新: 2026-08-28（Task 5 完成：端到端接线 + 文档。模型选型与计划不同处已如实记录：流式 paraformer 三语替代不存在的粤语 zipformer）*

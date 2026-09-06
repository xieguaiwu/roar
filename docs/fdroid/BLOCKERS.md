# Roar — F-Droid 收录阻塞项分析（2026-09-06）

> 结论：**Roar 目前不能提交 fdroiddata**。不是缺元数据，是有两个硬性合规问题。
> 本文档记录已核实的事实、可选路线与工作量，供后续决策。

## 已核实事实

| 项 | 结论 | 取证方式 |
|---|---|---|
| 应用源码许可证 | 原本**缺 LICENSE 文件** → 已补 MIT | `find Roar -maxdepth 2 -iname "*license*"` 为空 |
| sherpa-onnx 许可证 | **Apache-2.0**（自由） | GitHub API `repos/k2-fsa/sherpa-onnx` → `license.spdx_id` |
| sherpa-onnx mavenCentral 坐标 | **不存在** | `repo1.maven.org/maven2/com/k2fsa{,/sherpa,/sherpa-onnx,/sherpa/onnx}` 全 404 |
| sherpa-onnx 分发形式 | 仅 GitHub Release 预编译 AAR（`sherpa-onnx-1.13.7.aar` 等，latest release 共 289 个资产） | GitHub API releases/latest |
| 本仓库入库的二进制 | `app/libs/sherpa-onnx-1.13.6.aar`（47 MB）**已被 git 跟踪** | `git ls-files \| grep aar` |
| onnxruntime | **在 mavenCentral**，MIT，最新 `onnxruntime-android` 1.29.0 | `repo1.maven.org/.../onnxruntime-android/maven-metadata.xml` |
| sherpa-onnx 源码构建模块 | 仓库内有 `android/SherpaOnnxAar` 专用于自编 AAR | GitHub contents API |
| 模型许可证 | HuggingFace `csukuangfj/sherpa-onnx-streaming-paraformer-trilingual-zh-cantonese-en` **无 license tag** | HF API `tags` 只有 `['onnx','region:us']` |
| 词典来源 | `assets/dialect/cantonese_dict.json`（510 条 hanzi/jyutping/freq/note）**未标注来源** | 人工查看文件头 |
| README 与 Manifest 一致性 | 英文 README 曾称「No INTERNET permission」，Manifest 第 5 行有 `INTERNET` → **已修正**（中文 README 原本正确） | 逐行比对 |

## 阻塞项 1：预编译二进制入库（必须解决）

F-Droid 要求 APK 可从源码可复现构建。仓库里
`implementation(files("libs/sherpa-onnx-1.13.6.aar"))` 意味着构建产物依赖一个
仓库内二进制 blob，reviewer 会直接打回。

### 路线 A — srclibs + NDK 自编（合规，最重）

```yaml
RepoType: git
Repo: https://github.com/xieguaiwu/roar
Builds:
  - versionName: 0.1.0
    versionCode: 1
    commit: v0.1.0
    srclibs:
      - sherpa-onnx@v1.13.6
    ndk: r27b
    prebuild:
      # 具体脚本名需实地读 sherpa-onnx/android/SherpaOnnxAar 确认
      - pushd $$sherpa-onnx$$/android/SherpaOnnxAar && ./scripts/make_aar.sh && popd
      - sed -i 's@files("libs/sherpa-onnx-.*\\.aar")@files("$$sherpa-onnx$$/android/SherpaOnnxAar/sherpa-onnx.aar")@' build.gradle.kts
    gradle:
      - yes
```

- 优点：唯一「完全合规」路线，可复现徽章也顺
- 风险：buildserver 上 cmake + NDK + onnxruntime 链接是否稳定，**未做可行性验证**；
  `SherpaOnnxAar` 的构建入口脚本名与产物路径需实地读源码确认
- 工作量：**1–3 天试错**，失败率不低（NDK 版本漂移是 F-Droid 构建的经典坑）

### 路线 B — 换用 mavenCentral 上的第三方重打包（不推荐）

`com.bihe0832.android:lib-sherpa-onnx` 在 mavenCentral 存在，但是**非官方第三方重打包**：
上游不可追溯、版本滞后、无签名策略。F-Droid reviewer 对这类坐标同样警惕。

### 路线 C — 把 ASR 内核换成 mavenCentral 上有自由源码分发的引擎（战略级）

例如 Vosk Android（有 mavenCentral 坐标、Apache-2.0），但粤语识别质量需重新评估，
等于换引擎。**属于产品决策，不属于发布工程。**

### 路线 D — 先不上架，走 GitHub Release 自分发（当前建议）

Roar 还在「真机端到端未验证」阶段（见 `VISION.md` 阶段 1 判据未满足）。
先把产品验证做完，再回来解决构建合规，避免为一个未定型的架构付 3 天构建调试成本。

## 阻塞项 2：素材许可证不明（必须澄清）

即使二进制问题解决，还有两处会被问：

1. **`cantonese_dict.json` 来源**。若派生自已出版词典（《粵典》opensize、CUHK 粵語語文資料庫等），
   这些多为 CC-BY-NC / 非自由授权 → 触发 **NonFreeAssets，直接拒绝收录**。
   必须在 `docs/` 或词典文件头写明来源与授权；自研则明确声明公有领域/MIT。
2. **运行时下载的 ASR 模型无 license tag**。模型不在 APK 内，不触发 NonFreeAssets，
   但需在 `full_description` 说明「模型首次运行从 HuggingFace 下载（约 238 MB）」
   （已写入 fastlane 文案），并在 metadata 声明 **`AntiFeatures: NonFreeNet`**
   （HuggingFace 为专有托管服务）。

## 待办（按依赖顺序）

- [x] 补 LICENSE（MIT）
- [x] 修正英文 README 权限描述与 Manifest 的矛盾
- [x] fastlane 双语元数据骨架（en-US + zh-CN）
- [x] 建 GitHub 远程仓库并推送
- [ ] 词典来源与授权澄清（**阻塞上架，需用户回答**）
- [ ] 决定路线 A 还是 D（**需用户拍板**）
- [ ] 若走 A：本地先验证 `SherpaOnnxAar` 自编产物与现 AAR 接口一致
- [ ] 真机端到端验证（VISION 阶段 1 判据）+ 真机截图入 fastlane
- [ ] 打 tag `v0.1.0` 并写 `docs/fdroid/com.xieguiawu.roar.yml`

## 现在**不要**提交 fdroiddata 的理由

提交一个已知构建失败的 metadata，会占用 reviewer 时间并在 fdroiddata 留下
失败记录，影响同一作者后续 MR 的信任度。先把上面两项阻塞清掉。

## 附：本仓库历史已做过一次脱敏重写（2026-09-06）

`git filter-repo` 把 `CONTEXT_FOR_NEXT_AGENT.md` 两个历史版本里的服务器公网 IP
与 SSH 端口替换为 `<redacted-host>`，然后才首次推送到公开仓库。
（本文档本身也不复述该 IP——上一版把它写进了「脱敏说明」里，等于自我泄露。）
`git rev-list --all` 全量 blob 扫描确认零命中。

⚠️ **副作用教训（勿重犯）**：`git filter-repo` 会 reset index。运行时有 6 个
已 `git add` 但未 commit 的新文件（LICENSE、fastlane/*、docs/fdroid/*），被
filter-repo 连带删除且 `git fsck --unreachable` 已无可恢复对象（它跑了 gc --prune）。
**正确顺序：先 commit，再 filter-repo。**

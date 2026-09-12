# 词典来源与授权声明（cantonese_dict.json）

**结论：本词典为作者原创编写（AI 辅助整理），无第三方语料复制，授权随仓库 MIT。**

## 考证过程（2026-09-13）

1. `docs/plans/2026-08-28-roar-mvp.md` Task 4 明确记录该文件为 **Create**（新建），
   且 Global Constraints 写明：「词典条目只收录**确定正确**的粤语正字与粤拼
   （数字声调）；不确定的条目宁缺毋滥」——即逐条由作者按语言学知识编写，
   非从现成语料库/词典文件导入。
2. git 历史：该文件自 `28ad2ed`（orthography engine 初始提交）一次性加入，
   此后无外部数据合并提交。
3. 文件结构为简单 JSON（hanzi / jyutping / freq / note 四字段），freq 为作者
   估写的相对词序权重，不来自任何语料统计。
4. 未发现任何第三方词典（粵典 words.hk、CUHK 粤语语料库等）的导出格式特征
   或来源标注。

## 授权

- 随仓库以 **MIT** 授权发布（见根目录 LICENSE）。
- 条目内容为通用语言事实（汉字 ↔ 粤拼标注），不构成受版权保护的创造性表达
  门槛；如仍被质疑，作者声明以 MIT 授权释出全部条目。

## F-Droid 对应项

- BLOCKERS.md「阻塞项 2：素材许可证不明」之 1（cantonese_dict.json 来源）→
  本文件即答复，NonFreeAssets 不适用。
- 阻塞项 2 之 2（运行时下载的 ASR 模型无 license tag）→ 模型不在 APK 内，
  metadata 声明 `AntiFeatures: NonFreeNet`（HuggingFace 托管），
  fastlane full_description 已说明模型首次运行下载（约 238 MB）。

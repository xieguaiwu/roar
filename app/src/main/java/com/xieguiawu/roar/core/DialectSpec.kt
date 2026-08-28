package com.xieguiawu.roar.core

/**
 * 方言方案模型：一个方言 = 正字引擎配置 + ASR 模型策略。
 *
 * 「說方言，出正字」的产品核心是：方言正字方案（词典 + 谐音规则）与
 * ASR 模型解耦——同一套正字引擎可以消费不同来源的语音转写文本：
 *
 * - **专用模型**（[ModelSpec] 指向方言专用 ASR 模型，如粤语 paraformer 三语）；
 * - **通用模型路线**（未来）：普通话/多语 whisper 模型转写 → 谐音规则映射回正字。
 *
 * 新方言只需在 [DialectRegistry] 注册一个 [DialectSpec]（词典资产 + 规则集 +
 * 模型 spec），即可被设置页、IME、下载链路全链路支持——这是「方言方案市场」的
 * 本地雏形（VISION 长期目标：用户勾选方言 = 加载正字方案）。
 */
data class DialectSpec(
    /** 稳定标识（SharedPreferences 持久化键），如 "cantonese"。 */
    val id: String,
    /** 设置页显示名，如 "粵語（廣州話）"。 */
    val displayName: String,
    /** assets 中的词典 JSON 路径，如 "dialect/cantonese_dict.json"。 */
    val dictAssetPath: String,
    /** 谐音/普化文本 → 正字替换规则集（[TextNormalizer] 消费）。 */
    val rules: List<NormalizerRule>,
    /** ASR 模型方案；null 表示暂无可用模型（正字引擎仍可离线工作）。 */
    val model: ModelSpec?,
)

/** 单条正字替换规则（原 TextNormalizer 私有 Rule 提升为公开数据结构）。 */
data class NormalizerRule(
    val from: String,
    val to: String,
    /** 例外复合词：若 from 出现在这些词内部则跳过该处替换。 */
    val exceptions: Set<String> = emptySet(),
    /** 要求前一个字符是汉字（用于 "D"→"啲" 这类拉丁字母替换）。 */
    val requireCjkBefore: Boolean = false,
)

/** ASR 模型单个文件（体积与 SHA-256 用于完整性校验）。 */
data class ModelFileSpec(
    val name: String,
    val sizeBytes: Long,
    val sha256Hex: String,
) {
    /** 拼接下载地址（[baseUrl] 为模型仓库 resolve/main 基地址）。 */
    fun url(baseUrl: String): String = "$baseUrl/$name"
}

/** ASR 模型方案：HuggingFace 仓库 + 文件清单 + 国内镜像。 */
data class ModelSpec(
    /** HuggingFace 仓库 id，如 csukuangfj/sherpa-onnx-streaming-paraformer-trilingual-zh-cantonese-en。 */
    val repo: String,
    /** 模型文件清单（全部齐全且体积一致才视为就绪）。 */
    val files: List<ModelFileSpec>,
) {
    /** 官方源 resolve/main 基地址（国内网络常不可达，见 [mirrorBaseUrl]）。 */
    val hfBaseUrl: String get() = "https://huggingface.co/$repo/resolve/main"

    /** 国内镜像源（hf-mirror.com，HTTPS，用于官方源失败时 fallback）。 */
    val mirrorBaseUrl: String get() = "https://hf-mirror.com/$repo/resolve/main"

    /** 模型总下载体积（用于进度显示与 >100MB 打包阈值判断）。 */
    val totalBytes: Long get() = files.sumOf { it.sizeBytes }
}

package com.xieguiawu.roar.core

/**
 * 方言注册表：内置方言方案列表 + 按 id 解析。
 *
 * - 新方言 = 新增一个 [DialectSpec] 注册（词典资产 + 谐音规则集 + 模型 spec），
 *   设置页 / IME / 模型下载链路全自动支持；
 * - 未知方言 id（如旧版本持久化的值）回退到第一个（粤语），保证 IME 永不因
 *   配置损坏而崩溃；
 * - 二期（S2）：注册表可由 Go 服务端下发扩展（方言方案市场），本对象是本地基座。
 */
object DialectRegistry {

    /**
     * 内置方言（按展示顺序）。新增方言在此追加并补测试。
     * 计算属性：避免 object 初始化顺序问题（[CANTONESE] 声明在下方）。
     */
    val dialects: List<DialectSpec> get() = listOf(CANTONESE)

    /** 默认方言（首个注册 = 粤语）。 */
    val default: DialectSpec get() = dialects.first()

    /** 按 id 解析方言；未知 id 回退 [default]（防御持久化配置损坏）。 */
    fun get(id: String): DialectSpec = dialects.firstOrNull { it.id == id } ?: default

    /**
     * 粤语（广州话）：MVP 首个方言。
     *
     * - 词典：assets `dialect/cantonese_dict.json`（510 条正字 + 粤拼 + 词频）；
     * - 规则：粤语谐音/普化 → 正字规则集（[CantoneseRules]）；
     * - 模型：sherpa-onnx 流式 paraformer 三语（普通话/粤语/英语）int8，
     *   约 238MB，首次运行下载（k2-fsa 模型库中流式粤语最优可用模型；
     *   计划假设的「粤语流式 zipformer」不存在，见 ARCHITECTURE.md §2）。
     */
    val CANTONESE: DialectSpec = DialectSpec(
        id = "cantonese",
        displayName = "粵語（廣州話）",
        dictAssetPath = "dialect/cantonese_dict.json",
        rules = CantoneseRules.RULES,
        model = ModelSpec(
            repo = "csukuangfj/sherpa-onnx-streaming-paraformer-trilingual-zh-cantonese-en",
            files = listOf(
                ModelFileSpec(
                    name = "encoder.int8.onnx",
                    sizeBytes = 166_362_800,
                    sha256Hex = "6047a644b41b236d9d8e89e3b94ef39d1b7037daab028131b722ca52e10b0357",
                ),
                ModelFileSpec(
                    name = "decoder.int8.onnx",
                    sizeBytes = 72_062_549,
                    sha256Hex = "545427acf508452b7d89969be082c8128c681e3432ff43aef09f6159f4b61a7e",
                ),
                ModelFileSpec(
                    name = "tokens.txt",
                    sizeBytes = 81_289,
                    sha256Hex = "45b31504211675dd52aa88f998a6f6161703a2834e86760c1cda645a22538085",
                ),
            ),
        ),
    )
}

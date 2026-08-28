package com.xieguiawu.roar.core

/**
 * 谐音/普化文本 → 方言正字候选（方言无关的替换引擎）。
 *
 * 核心难点：ASR 输出的谐音与正字字形不同（"无该" vs "唔該"），不能靠字形匹配，
 * 因此每个方言提供一组 [NormalizerRule] 谐音映射规则（见 [DialectSpec.rules]），
 * 配合 [DialectDictionary] 做词频兜底。默认规则集为粤语（[CantoneseRules]）。
 *
 * 替换策略：
 * 1. 整词映射优先（"无该"→"唔該"、"系边"→"喺邊"、普化句"我们"→"我哋"等），按 key 长度降序；
 * 2. 单字映射在后（"系"→"係"、"听"→"聽"），带复合词例外保护
 *    （如 "系统" 的 "系" 不转 "係"、"既然" 的 "既" 不转 "嘅"）；
 * 3. 无任何替换时原样返回 [input]；有替换时候选 0 为替换结果，候选 1 为原文（保底）。
 */
object TextNormalizer {

    /**
     * 把普化/谐音文本转正字候选。
     *
     * @param input ASR 输出文本
     * @param dict  方言词典（候选词频由 [CandidateRanker] 消费，此处保留签名以便后续字典驱动扩展）
     * @param rules 方言谐音替换规则集（默认粤语规则 [CantoneseRules.RULES]）
     * @return 候选列表（第 0 个为最可能），无匹配时返回 [input]
     */
    fun normalize(
        input: String,
        dict: DialectDictionary,
        rules: List<NormalizerRule> = CantoneseRules.RULES,
    ): List<String> {
        var text = input
        for (rule in rules) {
            text = applyRule(text, rule)
        }
        return if (text == input) listOf(input) else listOf(text, input)
    }

    /** 左到右应用单条规则，跳过例外复合词内部的匹配 */
    private fun applyRule(text: String, rule: NormalizerRule): String {
        var result = text
        var start = 0
        while (true) {
            val index = result.indexOf(rule.from, start)
            if (index < 0) break
            if (isException(result, index, rule)) {
                start = index + rule.from.length
                continue
            }
            result = result.replaceRange(index, index + rule.from.length, rule.to)
            start = index + rule.to.length
        }
        return result
    }

    private fun isException(text: String, index: Int, rule: NormalizerRule): Boolean {
        if (rule.requireCjkBefore) {
            val prev = text.getOrNull(index - 1)
            if (prev == null || prev !in '\u4e00'..'\u9fff') return true
        }
        for (exception in rule.exceptions) {
            val offset = exception.indexOf(rule.from)
            if (offset < 0) continue
            val begin = index - offset
            val end = begin + exception.length
            if (begin >= 0 && end <= text.length && text.substring(begin, end) == exception) return true
        }
        return false
    }
}

/**
 * 粤语谐音/普化 → 正字规则集（[DialectRegistry.CANTONESE] 的 [DialectSpec.rules] 来源，
 * 也是 [TextNormalizer.normalize] 的默认规则）。
 *
 * 单条规则 = [NormalizerRule]（from/to/例外复合词/CJK 前置要求），
 * 新方言规则集照此结构编写并注册进 [DialectRegistry]。
 */
object CantoneseRules {

    /** 常见普化/谐音 → 正字映射（简单规则视图，不含带例外保护的规则）。 */
    val COMMON_HOMOPHONE_MAP: Map<String, String> by lazy {
        RULES.filter { it.exceptions.isEmpty() && !it.requireCjkBefore }
            .associate { it.from to it.to }
    }

    val RULES: List<NormalizerRule> = listOf(
        // ---- 整词映射（多字优先，自动按长度降序应用）----
        NormalizerRule("无该", "唔該"),
        NormalizerRule("唔该", "唔該"),
        NormalizerRule("吾该", "唔該"),
        NormalizerRule("多D", "多啲"),
        NormalizerRule("多d", "多啲"),
        NormalizerRule("倾计", "傾偈"),
        NormalizerRule("训觉", "瞓覺"),
        NormalizerRule("训教", "瞓覺"),
        NormalizerRule("瞓觉", "瞓覺"),
        NormalizerRule("寻日", "尋日"),
        NormalizerRule("靓仔", "靚仔"),
        NormalizerRule("靓女", "靚女"),
        NormalizerRule("细佬", "細佬"),
        NormalizerRule("边度", "邊度"),
        NormalizerRule("边个", "邊個"),
        NormalizerRule("系边度", "喺邊度"),
        NormalizerRule("系边", "喺邊", exceptions = setOf("系边个")),
        NormalizerRule("系度", "喺度", exceptions = setOf("关系度", "關係度")),
        NormalizerRule("系呢度", "喺呢度"),
        NormalizerRule("系个度", "喺嗰度"),
        NormalizerRule("个度", "嗰度"),
        NormalizerRule("个边", "嗰邊"),
        NormalizerRule("哩度", "呢度"),
        NormalizerRule("喱度", "呢度"),
        NormalizerRule("翻工", "返工"),
        NormalizerRule("翻学", "返學"),
        NormalizerRule("翻屋企", "返屋企"),
        NormalizerRule("翻去", "返去"),
        NormalizerRule("翻嚟", "返嚟"),
        NormalizerRule("翻来", "返嚟"),
        NormalizerRule("体下", "睇下"),
        NormalizerRule("体到", "睇到"),
        NormalizerRule("体见", "睇見"),
        NormalizerRule("钟意", "鍾意"),
        NormalizerRule("点钟", "點鐘"),
        NormalizerRule("发烧", "發燒"),
        NormalizerRule("发财", "發財"),
        NormalizerRule("头发", "頭髮"),
        NormalizerRule("后日", "後日"),
        NormalizerRule("之后", "之後"),
        NormalizerRule("以后", "以後"),
        NormalizerRule("后面", "後面"),
        NormalizerRule("一个", "一個"),
        NormalizerRule("两个", "兩個"),
        NormalizerRule("呢个", "呢個"),
        NormalizerRule("嗰个", "嗰個"),
        NormalizerRule("一只", "一隻"),
        NormalizerRule("没有", "冇"),
        NormalizerRule("没问题", "冇問題"),
        NormalizerRule("我的", "我嘅"),
        NormalizerRule("你的", "你嘅"),
        NormalizerRule("他的", "佢嘅"),
        NormalizerRule("她的", "佢嘅"),
        NormalizerRule("好的", "好嘅"),
        NormalizerRule("真的", "真係"),
        NormalizerRule("为什么", "點解"),
        NormalizerRule("什么", "乜嘢"),
        NormalizerRule("怎么办", "點算"),
        NormalizerRule("怎么样", "點樣"),
        NormalizerRule("哪里", "邊度"),
        NormalizerRule("这里", "呢度"),
        NormalizerRule("那里", "嗰度"),
        NormalizerRule("我们", "我哋"),
        NormalizerRule("你们", "你哋"),
        NormalizerRule("他们", "佢哋"),
        NormalizerRule("她们", "佢哋"),
        NormalizerRule("明天", "聽日"),
        NormalizerRule("昨天", "尋日"),
        NormalizerRule("今天", "今日"),
        NormalizerRule("现在", "而家"),
        NormalizerRule("回家", "返屋企"),
        NormalizerRule("上班", "返工"),
        NormalizerRule("上学", "返學"),
        NormalizerRule("吃饭", "食飯"),
        NormalizerRule("吃面", "食麵"),
        NormalizerRule("食面", "食麵"),
        NormalizerRule("喝水", "飲水"),
        NormalizerRule("睡觉", "瞓覺"),
        NormalizerRule("洗澡", "沖涼"),
        NormalizerRule("谢谢", "多謝"),
        NormalizerRule("对不起", "對唔住"),
        NormalizerRule("知道", "知"),
        NormalizerRule("喜欢", "鍾意"),
        NormalizerRule("好累", "好攰"),
        NormalizerRule("一起", "一齊"),
        NormalizerRule("东西", "嘢"),
        NormalizerRule("聊天", "傾偈"),
        NormalizerRule("逛街", "行街"),
        NormalizerRule("漂亮", "靚"),
        NormalizerRule("事情", "事"),
        NormalizerRule("桌子", "檯"),
        NormalizerRule("瓶子", "樽"),
        NormalizerRule("汤面", "湯麵"),

        // ---- 单字映射（带复合词例外保护）----
        NormalizerRule(
            "系", "係",
            exceptions = setOf(
                "系统", "系統", "关系", "關係", "联系", "聯繫", "体系", "體系", "系列",
                "系数", "係數", "谱系", "譜系", "派系", "直系", "星系", "母系", "父系",
                "语系", "語系", "中文系", "太阳系", "太陽系", "物理系", "化学系", "化學系",
                "数学系", "數學系"
            )
        ),
        NormalizerRule("咩", "乜", exceptions = setOf("咩咩")),
        NormalizerRule(
            "无", "冇",
            exceptions = setOf(
                "无法", "无论", "无数", "无限", "无意", "无聊", "无知", "无谓", "无奈",
                "无非", "无所", "无所谓", "无妨", "无辜", "无关", "虚无", "毫无",
                "无力", "无能", "无处"
            )
        ),
        NormalizerRule("既", "嘅", exceptions = setOf("既然", "既得", "既定", "既有", "既视", "既視", "既成")),
        NormalizerRule("D", "啲", requireCjkBefore = true),
        NormalizerRule("黎", "嚟", exceptions = setOf("黎明", "黎巴嫩", "巴黎", "黎族", "黎民")),
        NormalizerRule("边", "邊"),
        NormalizerRule("听", "聽"),
        NormalizerRule("讲", "講"),
        NormalizerRule("饭", "飯"),
        NormalizerRule("饮", "飲"),
        NormalizerRule("谢", "謝"),
        NormalizerRule("该", "該"),
        NormalizerRule("话", "話"),
        NormalizerRule("靓", "靚"),
        NormalizerRule("细", "細"),
        NormalizerRule("开", "開"),
        NormalizerRule("关", "關"),
        NormalizerRule("门", "門"),
        NormalizerRule("车", "車"),
        NormalizerRule("风", "風"),
        NormalizerRule("电", "電"),
        NormalizerRule("脑", "腦"),
        NormalizerRule("机", "機"),
        NormalizerRule("书", "書"),
        NormalizerRule("头", "頭"),
        NormalizerRule("药", "藥"),
        NormalizerRule("识", "識"),
        NormalizerRule("爱", "愛"),
        NormalizerRule("饿", "餓"),
        NormalizerRule("给", "畀"),
        NormalizerRule("钱", "錢"),
        NormalizerRule("买", "買"),
        NormalizerRule("卖", "賣"),
        NormalizerRule("间", "間"),
        NormalizerRule("点", "點"),
        NormalizerRule("几", "幾"),
        NormalizerRule("双", "雙"),
        NormalizerRule("条", "條"),
        NormalizerRule("张", "張"),
        NormalizerRule("瓶", "樽"),
        NormalizerRule("汤", "湯"),
        NormalizerRule("冻", "凍"),
        NormalizerRule("热", "熱"),
        NormalizerRule("网", "網"),
        NormalizerRule("戏", "戲"),
        NormalizerRule("飞", "飛"),
        NormalizerRule("医", "醫"),
        NormalizerRule("帮", "幫"),
        NormalizerRule("样", "樣"),
        NormalizerRule("还", "還"),
        NormalizerRule("会", "會"),
        NormalizerRule("吗", "嗎"),
    ).sortedByDescending { it.from.length }
}

package com.xieguiawu.roar.core

/**
 * 普化/谐音文本 → 粤语正字候选。
 *
 * 核心难点：ASR 输出的谐音与正字字形不同（"无该" vs "唔該"），不能靠字形匹配，
 * 因此内置 [COMMON_HOMOPHONE_MAP] 谐音映射表，配合 [DialectDictionary] 做词频兜底。
 *
 * 替换策略：
 * 1. 整词映射优先（"无该"→"唔該"、"系边"→"喺邊"、普化句"我们"→"我哋"等），按 key 长度降序；
 * 2. 单字映射在后（"系"→"係"、"听"→"聽"），带复合词例外保护
 *    （如 "系统" 的 "系" 不转 "係"、"既然" 的 "既" 不转 "嘅"）；
 * 3. 无任何替换时原样返回 [input]；有替换时候选 0 为替换结果，候选 1 为原文（保底）。
 */
object TextNormalizer {

    /** 单条替换规则 */
    private data class Rule(
        val from: String,
        val to: String,
        /** 例外复合词：若 from 出现在这些词内部则跳过该处替换 */
        val exceptions: Set<String> = emptySet(),
        /** 要求前一个字符是汉字（用于 "D"→"啲" 这类拉丁字母替换） */
        val requireCjkBefore: Boolean = false,
    )

    /**
     * 把普化/谐音文本转正字候选。
     *
     * @param input ASR 输出文本
     * @param dict  粤语词典（候选词频由 [CandidateRanker] 消费，此处保留签名以便后续字典驱动扩展）
     * @return 候选列表（第 0 个为最可能），无匹配时返回 [input]
     */
    fun normalize(input: String, dict: DialectDictionary): List<String> {
        var text = input
        for (rule in RULES) {
            text = applyRule(text, rule)
        }
        return if (text == input) listOf(input) else listOf(text, input)
    }

    /** 左到右应用单条规则，跳过例外复合词内部的匹配 */
    private fun applyRule(text: String, rule: Rule): String {
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

    /**
     * 常见普化/谐音 → 正字映射（简单规则视图，不含带例外保护的规则）。
     * 完整规则见 [RULES]，normalize 使用后者。
     */
    val COMMON_HOMOPHONE_MAP: Map<String, String> by lazy {
        RULES.filter { it.exceptions.isEmpty() && !it.requireCjkBefore }
            .associate { it.from to it.to }
    }

    private fun isException(text: String, index: Int, rule: Rule): Boolean {
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

    private val RULES: List<Rule> = listOf(
        // ---- 整词映射（多字优先，自动按长度降序应用）----
        Rule("无该", "唔該"),
        Rule("唔该", "唔該"),
        Rule("吾该", "唔該"),
        Rule("多D", "多啲"),
        Rule("多d", "多啲"),
        Rule("倾计", "傾偈"),
        Rule("训觉", "瞓覺"),
        Rule("训教", "瞓覺"),
        Rule("瞓觉", "瞓覺"),
        Rule("寻日", "尋日"),
        Rule("靓仔", "靚仔"),
        Rule("靓女", "靚女"),
        Rule("细佬", "細佬"),
        Rule("边度", "邊度"),
        Rule("边个", "邊個"),
        Rule("系边度", "喺邊度"),
        Rule("系边", "喺邊", exceptions = setOf("系边个")),
        Rule("系度", "喺度", exceptions = setOf("关系度", "關係度")),
        Rule("系呢度", "喺呢度"),
        Rule("系个度", "喺嗰度"),
        Rule("个度", "嗰度"),
        Rule("个边", "嗰邊"),
        Rule("哩度", "呢度"),
        Rule("喱度", "呢度"),
        Rule("翻工", "返工"),
        Rule("翻学", "返學"),
        Rule("翻屋企", "返屋企"),
        Rule("翻去", "返去"),
        Rule("翻嚟", "返嚟"),
        Rule("翻来", "返嚟"),
        Rule("体下", "睇下"),
        Rule("体到", "睇到"),
        Rule("体见", "睇見"),
        Rule("钟意", "鍾意"),
        Rule("点钟", "點鐘"),
        Rule("发烧", "發燒"),
        Rule("发财", "發財"),
        Rule("头发", "頭髮"),
        Rule("后日", "後日"),
        Rule("之后", "之後"),
        Rule("以后", "以後"),
        Rule("后面", "後面"),
        Rule("一个", "一個"),
        Rule("两个", "兩個"),
        Rule("呢个", "呢個"),
        Rule("嗰个", "嗰個"),
        Rule("一只", "一隻"),
        Rule("没有", "冇"),
        Rule("没问题", "冇問題"),
        Rule("我的", "我嘅"),
        Rule("你的", "你嘅"),
        Rule("他的", "佢嘅"),
        Rule("她的", "佢嘅"),
        Rule("好的", "好嘅"),
        Rule("真的", "真係"),
        Rule("为什么", "點解"),
        Rule("什么", "乜嘢"),
        Rule("怎么办", "點算"),
        Rule("怎么样", "點樣"),
        Rule("哪里", "邊度"),
        Rule("这里", "呢度"),
        Rule("那里", "嗰度"),
        Rule("我们", "我哋"),
        Rule("你们", "你哋"),
        Rule("他们", "佢哋"),
        Rule("她们", "佢哋"),
        Rule("明天", "聽日"),
        Rule("昨天", "尋日"),
        Rule("今天", "今日"),
        Rule("现在", "而家"),
        Rule("回家", "返屋企"),
        Rule("上班", "返工"),
        Rule("上学", "返學"),
        Rule("吃饭", "食飯"),
        Rule("吃面", "食麵"),
        Rule("食面", "食麵"),
        Rule("喝水", "飲水"),
        Rule("睡觉", "瞓覺"),
        Rule("洗澡", "沖涼"),
        Rule("谢谢", "多謝"),
        Rule("对不起", "對唔住"),
        Rule("知道", "知"),
        Rule("喜欢", "鍾意"),
        Rule("好累", "好攰"),
        Rule("一起", "一齊"),
        Rule("东西", "嘢"),
        Rule("聊天", "傾偈"),
        Rule("逛街", "行街"),
        Rule("漂亮", "靚"),
        Rule("事情", "事"),
        Rule("桌子", "檯"),
        Rule("瓶子", "樽"),
        Rule("汤面", "湯麵"),

        // ---- 单字映射（带复合词例外保护）----
        Rule(
            "系", "係",
            exceptions = setOf(
                "系统", "系統", "关系", "關係", "联系", "聯繫", "体系", "體系", "系列",
                "系数", "係數", "谱系", "譜系", "派系", "直系", "星系", "母系", "父系",
                "语系", "語系", "中文系", "太阳系", "太陽系", "物理系", "化学系", "化學系",
                "数学系", "數學系"
            )
        ),
        Rule("咩", "乜", exceptions = setOf("咩咩")),
        Rule(
            "无", "冇",
            exceptions = setOf(
                "无法", "无论", "无数", "无限", "无意", "无聊", "无知", "无谓", "无奈",
                "无非", "无所", "无所谓", "无妨", "无辜", "无关", "虚无", "毫无",
                "无力", "无能", "无处"
            )
        ),
        Rule("既", "嘅", exceptions = setOf("既然", "既得", "既定", "既有", "既视", "既視", "既成")),
        Rule("D", "啲", requireCjkBefore = true),
        Rule("黎", "嚟", exceptions = setOf("黎明", "黎巴嫩", "巴黎", "黎族", "黎民")),
        Rule("边", "邊"),
        Rule("听", "聽"),
        Rule("讲", "講"),
        Rule("饭", "飯"),
        Rule("饮", "飲"),
        Rule("谢", "謝"),
        Rule("该", "該"),
        Rule("话", "話"),
        Rule("靓", "靚"),
        Rule("细", "細"),
        Rule("开", "開"),
        Rule("关", "關"),
        Rule("门", "門"),
        Rule("车", "車"),
        Rule("风", "風"),
        Rule("电", "電"),
        Rule("脑", "腦"),
        Rule("机", "機"),
        Rule("书", "書"),
        Rule("头", "頭"),
        Rule("药", "藥"),
        Rule("识", "識"),
        Rule("爱", "愛"),
        Rule("饿", "餓"),
        Rule("给", "畀"),
        Rule("钱", "錢"),
        Rule("买", "買"),
        Rule("卖", "賣"),
        Rule("间", "間"),
        Rule("点", "點"),
        Rule("几", "幾"),
        Rule("双", "雙"),
        Rule("条", "條"),
        Rule("张", "張"),
        Rule("瓶", "樽"),
        Rule("汤", "湯"),
        Rule("冻", "凍"),
        Rule("热", "熱"),
        Rule("网", "網"),
        Rule("戏", "戲"),
        Rule("飞", "飛"),
        Rule("医", "醫"),
        Rule("帮", "幫"),
        Rule("样", "樣"),
        Rule("还", "還"),
        Rule("会", "會"),
        Rule("吗", "嗎"),
    ).sortedByDescending { it.from.length }
}

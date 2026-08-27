package com.xieguiawu.roar.core

import kotlinx.serialization.json.Json

/**
 * 粤语正字词典：内存索引 + 最长词优先的替换建议。
 *
 * 构造时建立两个索引：
 * - [byHanzi]：正字精确反查
 * - [byJyutping]：粤拼（规范化后的完整串）查正字
 */
class DialectDictionary(entries: List<DictEntry>) {
    private val entries: List<DictEntry> = entries.distinctBy { it.hanzi to it.jyutping }

    /** 词典条目总数 */
    val size: Int get() = entries.size

    private val byHanzi: Map<String, List<DictEntry>> = entries.groupBy { it.hanzi }
    private val byJyutping: Map<String, List<DictEntry>> =
        entries.groupBy { normalizeJyutping(it.jyutping) }

    /** 按正字长度降序（同长度按词频降序），供最长词优先匹配 */
    private val longestFirst: List<DictEntry> = entries.sortedWith(
        compareByDescending<DictEntry> { it.hanzi.length }.thenByDescending { it.freq }
    )

    /** 精确正字反查 */
    fun lookupByHanzi(exact: String): List<DictEntry> = byHanzi[exact] ?: emptyList()

    /** 粤拼查正字（空格分词后全匹配，查询串同样做空白规范化） */
    fun lookupByJyutping(jy: String): List<DictEntry> =
        byJyutping[normalizeJyutping(jy)] ?: emptyList()

    /**
     * 文本中可正字化的词：词典词按长度降序逐个匹配 [text]，最长词优先返回。
     * 同形正字只保留词频最高的一条。
     */
    fun suggestReplacements(text: String): List<DictEntry> {
        val result = mutableListOf<DictEntry>()
        for (entry in longestFirst) {
            if (result.any { it.hanzi == entry.hanzi }) continue
            if (text.contains(entry.hanzi)) result.add(entry)
        }
        return result
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** 粤拼规范化：trim + 连续空白折叠为单个空格 */
        fun normalizeJyutping(raw: String): String = raw.trim().replace(Regex("\\s+"), " ")

        /** 从 JSON 字符串加载词典；格式非法时抛出 [kotlinx.serialization.SerializationException] */
        fun loadFromJson(jsonString: String): DialectDictionary =
            DialectDictionary(json.decodeFromString<List<DictEntry>>(jsonString))
    }
}

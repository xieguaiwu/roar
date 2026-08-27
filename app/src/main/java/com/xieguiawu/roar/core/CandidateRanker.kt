package com.xieguiawu.roar.core

/** 排序后的候选：文本 + 综合得分 */
data class RankedCandidate(val text: String, val score: Double)

/**
 * 候选综合排序：词频 + 上下文 bigram 共现。
 *
 * 打分规则：
 * - 候选 0（引擎主推）加 [BASE_SCORE_FOR_TOP] 基准分；
 * - 候选文本中命中的词典词频之和（词越长、越常用，命中分越高）；
 * - [context] 非空且候选含其尾词时额外加 [CONTEXT_BONUS]（上下文共现）。
 */
object CandidateRanker {
    private const val BASE_SCORE_FOR_TOP = 1000.0
    private const val CONTEXT_BONUS = 500.0

    /**
     * @param candidates 正字化候选列表（第 0 个为引擎主推）
     * @param dict       粤语词典（词频来源）
     * @param context    上一句/上一候选，用于 bigram 共现加分
     */
    fun rank(candidates: List<String>, dict: DialectDictionary, context: String?): List<RankedCandidate> {
        val tail = context?.let { tailWord(it) }?.takeIf { it.isNotEmpty() }
        return candidates
            .mapIndexed { index, text ->
                var score = if (index == 0) BASE_SCORE_FOR_TOP else 0.0
                score += dict.suggestReplacements(text).sumOf { it.freq.toDouble() }
                if (tail != null && text.contains(tail)) score += CONTEXT_BONUS
                RankedCandidate(text, score)
            }
            .sortedByDescending { it.score }
    }

    /** 取上下文尾词：优先取空白分隔的最后一个 token，无空白则取末两字 */
    private fun tailWord(context: String): String {
        val tokens = context.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val last = tokens.lastOrNull() ?: return ""
        return if (last.length <= 2) last else last.takeLast(2)
    }
}

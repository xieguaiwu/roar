package com.xieguiawu.roar.ime

import com.xieguiawu.roar.core.CandidateRanker
import com.xieguiawu.roar.core.DialectDictionary
import com.xieguiawu.roar.core.RankedCandidate
import com.xieguiawu.roar.core.TextNormalizer

/**
 * IME 数据流管道：ASR 文本 → 正字候选（纯函数，无 Android 依赖，可单测）。
 *
 * 串联两层转写架构的映射层：
 * 1. [TextNormalizer.normalize] 把 ASR 输出的普化/谐音文本正字化
 *    （候选 0 = 正字化结果，候选 1 = 原文保底）；
 * 2. [CandidateRanker.rank] 用词典词频 + 上下文 bigram 共现排序。
 *
 * 空/纯空白输入直接短路返回空列表（不产生任何候选），
 * 由 [RoarImeService] 在 [com.xieguiawu.roar.asr.RecognizerListener.onFinal] 回调中消费。
 */
object ImePipeline {

    /**
     * @param asrText ASR 识别出的最终文本
     * @param dict    粤语正字词典（词频来源）
     * @param context 上一句/上一候选，用于 bigram 共现加分；无上下文传 null
     * @return 按得分降序的候选列表（第 0 个为最可能），空输入返回空列表
     */
    fun toCandidates(
        asrText: String,
        dict: DialectDictionary,
        context: String?,
    ): List<RankedCandidate> {
        if (asrText.isBlank()) return emptyList()
        val normalized = TextNormalizer.normalize(asrText, dict)
        return CandidateRanker.rank(normalized, dict, context)
    }
}

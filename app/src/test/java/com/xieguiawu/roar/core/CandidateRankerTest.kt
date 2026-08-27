package com.xieguiawu.roar.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateRankerTest {
    private val dict: DialectDictionary by lazy { TestDict.DICT }

    @Test
    fun rank_候选0基准加分_唔該首位() {
        val ranked = CandidateRanker.rank(listOf("唔該", "无该"), dict, null)
        assertEquals("唔該", ranked.first().text)
    }

    @Test
    fun rank_词典词频命中加分() {
        // 多謝(800) 词典命中分高于 早晨(600)，且候选 0 有基准分
        val ranked = CandidateRanker.rank(listOf("多謝", "早晨"), dict, null)
        assertEquals("多謝", ranked.first().text)
    }

    @Test
    fun rank_context尾词额外加分500() {
        val withoutContext = CandidateRanker.rank(listOf("唔該", "多謝"), dict, null)
            .first { it.text == "多謝" }.score
        val withContext = CandidateRanker.rank(listOf("唔該", "多謝"), dict, "你幫咗我 多謝")
            .first { it.text == "多謝" }.score
        assertEquals(withoutContext + 500.0, withContext, 0.001)
    }

    @Test
    fun rank_无上下文尾词命中不加分() {
        val base = CandidateRanker.rank(listOf("唔該", "多謝"), dict, null)
            .first { it.text == "多謝" }.score
        val unrelated = CandidateRanker.rank(listOf("唔該", "多謝"), dict, "今晚食飯")
            .first { it.text == "多謝" }.score
        assertEquals(base, unrelated, 0.001)
    }

    @Test
    fun rank_空候选返回空列表() {
        assertTrue(CandidateRanker.rank(emptyList(), dict, null).isEmpty())
    }

    @Test
    fun rank_保留输入顺序() {
        val ranked = CandidateRanker.rank(listOf("甲", "乙"), dict, null)
        assertEquals("甲", ranked[0].text)
        assertEquals("乙", ranked[1].text)
    }
}

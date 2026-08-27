package com.xieguiawu.roar.ime

import com.xieguiawu.roar.core.DialectDictionary
import com.xieguiawu.roar.core.RankedCandidate
import com.xieguiawu.roar.core.TestDict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ImePipeline] 单测：ASR 文本 → 正字候选 端到端纯函数管道。
 *
 * 覆盖：普化谐音整词转换（无该→唔該）、单字规则转换（谢→謝）、
 * 空输入短路、真实内置词典（[TestDict]）集成。
 */
class ImePipelineTest {

    /** 最小词典（与计划一致），验证管道行为不依赖内置词典规模。 */
    private val dict = DialectDictionary.loadFromJson(
        """
        [
          {"hanzi":"唔該","jyutping":"m4 goi1","freq":900},
          {"hanzi":"多謝","jyutping":"do1 ze6","freq":800},
          {"hanzi":"喺","jyutping":"hai2","freq":700},
          {"hanzi":"係","jyutping":"hai6","freq":950},
          {"hanzi":"嘅","jyutping":"ge3","freq":980},
          {"hanzi":"食飯","jyutping":"sik6 faan6","freq":750},
          {"hanzi":"屋企","jyutping":"uk1 kei2","freq":720},
          {"hanzi":"聽日","jyutping":"ting1 jat6","freq":680}
        ]
        """.trimIndent()
    )

    @Test
    fun toCandidates_无该转唔該首位() {
        val result = ImePipeline.toCandidates("无该", dict, null)
        assertEquals("唔該", result.first().text)
    }

    @Test
    fun toCandidates_多谢晒首位含多謝() {
        val result = ImePipeline.toCandidates("多谢晒", dict, null)
        assertTrue(result.first().text.contains("多謝"))
    }

    @Test
    fun toCandidates_空输入返回空列表() {
        assertEquals(emptyList<RankedCandidate>(), ImePipeline.toCandidates("", dict, null))
        assertEquals(emptyList<RankedCandidate>(), ImePipeline.toCandidates("   ", dict, null))
    }

    @Test
    fun toCandidates_真实内置词典_无该首位唔該() {
        val result = ImePipeline.toCandidates("无该", TestDict.DICT, null)
        assertEquals("唔該", result.first().text)
    }

    @Test
    fun toCandidates_候选列表有序且包含原文保底() {
        val result = ImePipeline.toCandidates("这是系", dict, null)
        // 候选 0：正字化结果（系→係）；候选 1：原文保底
        assertTrue(result.first().text.contains("係"))
        assertTrue(result.map { it.text }.contains("这是系"))
        // 得分降序（引擎主推恒在最前）
        assertEquals(result, result.sortedByDescending { it.score })
    }
}

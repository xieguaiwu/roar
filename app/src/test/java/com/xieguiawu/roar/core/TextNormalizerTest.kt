package com.xieguiawu.roar.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextNormalizerTest {
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
    fun normalize_普化词转正字() {
        val result = TextNormalizer.normalize("无该", dict)
        assertEquals("唔該", result[0])
    }

    @Test
    fun normalize_系转正字係() {
        val result = TextNormalizer.normalize("这是系", dict)
        assertTrue(result[0].contains("係"))
    }

    @Test
    fun normalize_无匹配保留原文() {
        val result = TextNormalizer.normalize("完全没见过的词", dict)
        assertEquals("完全没见过的词", result[0])
    }

    @Test
    fun normalize_多词替换_屋企() {
        val result = TextNormalizer.normalize("我返屋企", dict)
        assertTrue(result[0].contains("屋企"))
    }

    @Test
    fun normalize_普化句转粤语正字() {
        val result = TextNormalizer.normalize("我们听日去饮茶", dict)
        assertEquals("我哋聽日去飲茶", result[0])
    }

    @Test
    fun normalize_谐音词转正字_训觉() {
        val result = TextNormalizer.normalize("训觉", dict)
        assertEquals("瞓覺", result[0])
    }

    @Test
    fun normalize_系边度转喺邊度() {
        val result = TextNormalizer.normalize("你系边度", dict)
        assertEquals("你喺邊度", result[0])
    }

    @Test
    fun normalize_有D转有啲() {
        val result = TextNormalizer.normalize("有D事", dict)
        assertEquals("有啲事", result[0])
    }

    @Test
    fun normalize_复合词保护_系统不转係() {
        val result = TextNormalizer.normalize("操作系统", dict)
        assertEquals("操作系统", result[0])
    }
}

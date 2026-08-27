package com.xieguiawu.roar.core

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DialectDictionaryTest {
    private val dict: DialectDictionary by lazy { TestDict.DICT }
    private val lenientJson = Json { ignoreUnknownKeys = true }

    @Test
    fun 词典至少150条() {
        assertTrue("词典条数不足 150，实际 ${dict.size}", dict.size >= 150)
    }

    @Test
    fun 所有条目粤拼格式合法_声调1到6() {
        val pattern = Regex("^[a-z]+[1-6]( [a-z]+[1-6])*$")
        val entries = lenientJson.decodeFromString<List<DictEntry>>(TestDict.JSON)
        for (entry in entries) {
            assertTrue("非法粤拼: ${entry.jyutping}", pattern.matches(entry.jyutping))
            assertTrue("freq 越界: ${entry.freq} (${entry.hanzi})", entry.freq in 100..1000)
            assertTrue("正字为空", entry.hanzi.isNotBlank())
        }
    }

    @Test
    fun lookupByJyutping_命中唔該() {
        val hits = dict.lookupByJyutping("m4 goi1")
        assertTrue(hits.any { it.hanzi == "唔該" })
    }

    @Test
    fun lookupByJyutping_多空格归一化() {
        assertEquals(dict.lookupByJyutping("m4 goi1"), dict.lookupByJyutping("  m4  goi1 "))
    }

    @Test
    fun lookupByHanzi_命中唔該() {
        val hits = dict.lookupByHanzi("唔該")
        assertTrue(hits.any { it.jyutping == "m4 goi1" })
    }

    @Test
    fun suggestReplacements_最长词优先() {
        val result = dict.suggestReplacements("我返屋企食飯")
        assertEquals("返屋企", result[0].hanzi)
        assertEquals("食飯", result[1].hanzi)
    }

    @Test
    fun 非法JSON抛异常() {
        assertThrows(SerializationException::class.java) {
            DialectDictionary.loadFromJson("[{not valid json")
        }
    }
}

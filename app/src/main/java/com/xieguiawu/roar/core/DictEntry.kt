package com.xieguiawu.roar.core

import kotlinx.serialization.Serializable

/**
 * 词典条目：一条确定正确的粤语正字词及其粤拼、词频。
 *
 * @param hanzi    正字（如 "唔該"）
 * @param jyutping 数字调粤拼（如 "m4 goi1"，空格分词，声调 1-6）
 * @param freq     词频权重，越大越优先（100-1000）
 * @param note     可选注释（释义等）
 */
@Serializable
data class DictEntry(
    val hanzi: String,
    val jyutping: String,
    val freq: Int,
    val note: String? = null,
)

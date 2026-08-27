package com.xieguiawu.roar.core

import java.io.File

/** 测试共享：加载内置粤语词典（单测环境读不到 Android assets，用 classpath + 模块相对路径双通道） */
object TestDict {
    val JSON: String by lazy {
        javaClass.classLoader?.getResourceAsStream("dialect/cantonese_dict.json")
            ?.bufferedReader()?.use { it.readText() }
            ?: listOf(
                "src/main/assets/dialect/cantonese_dict.json",
                "app/src/main/assets/dialect/cantonese_dict.json",
            )
                .map(::File)
                .firstOrNull { it.isFile }
                ?.readText()
            ?: error("cantonese_dict.json not found (classpath and module-relative paths all miss)")
    }

    val DICT: DialectDictionary by lazy { DialectDictionary.loadFromJson(JSON) }
}

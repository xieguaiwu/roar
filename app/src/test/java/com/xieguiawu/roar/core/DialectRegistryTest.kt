package com.xieguiawu.roar.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 方言注册表测试：默认方言、未知 id 回退、模型 spec 元数据完整性。
 *
 * 2026-08-28 多方言化：新方言注册时必须补「注册表可见 + 模型元数据合法」测试，
 * 防止缺失模型文件/哈希错位导致下载链路静默失败。
 */
class DialectRegistryTest {

    @Test
    fun default_为粤语() {
        assertEquals("cantonese", DialectRegistry.default.id)
        assertEquals("粵語（廣州話）", DialectRegistry.default.displayName)
    }

    @Test
    fun get_已知方言返回对应spec() {
        assertEquals("cantonese", DialectRegistry.get("cantonese").id)
    }

    @Test
    fun get_未知方言回退默认() {
        // 旧版本持久化的未知 id / 配置损坏时不得崩溃
        assertEquals(DialectRegistry.default.id, DialectRegistry.get("nonexistent-dialect").id)
        assertEquals(DialectRegistry.default.id, DialectRegistry.get("").id)
    }

    @Test
    fun get_空值回退默认() {
        assertEquals(DialectRegistry.default.id, DialectRegistry.get("").id)
    }

    @Test
    fun dialects_非空且id唯一() {
        assertTrue(DialectRegistry.dialects.isNotEmpty())
        val ids = DialectRegistry.dialects.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun 每个方言_词典资产路径与规则集非空() {
        for (d in DialectRegistry.dialects) {
            assertTrue("${d.id} 词典资产路径为空", d.dictAssetPath.isNotBlank())
            assertTrue("${d.id} 规则集为空（正字引擎将退化）", d.rules.isNotEmpty())
        }
    }

    @Test
    fun 每个方言_模型spec文件元数据合法() {
        for (d in DialectRegistry.dialects) {
            val model = d.model ?: continue
            assertTrue("${d.id} 仓库为空", model.repo.isNotBlank())
            assertTrue("${d.id} 模型文件清单为空", model.files.isNotEmpty())
            for (f in model.files) {
                assertTrue(
                    "${d.id}/${f.name} 哈希非法: ${f.sha256Hex}",
                    f.sha256Hex.matches(Regex("[0-9a-f]{64}")),
                )
                assertTrue("${d.id}/${f.name} 体积非法", f.sizeBytes > 0)
            }
        }
    }

    @Test
    fun 粤语模型_文件清单与镜像源就绪() {
        val model = DialectRegistry.CANTONESE.model
            ?: throw AssertionError("粤语方言必须配置 ASR 模型")
        // 镜像源与官方源必须都可用（下载 fallback 依赖）
        assertTrue(model.hfBaseUrl.startsWith("https://huggingface.co/"))
        assertTrue(model.mirrorBaseUrl.startsWith("https://hf-mirror.com/"))
        // 3 个文件：encoder + decoder + tokens
        assertEquals(3, model.files.size)
        // 总体积超过 100MB 打包阈值 → 首次运行下载路径
        assertTrue(model.totalBytes > 100L * 1024 * 1024)
    }
}

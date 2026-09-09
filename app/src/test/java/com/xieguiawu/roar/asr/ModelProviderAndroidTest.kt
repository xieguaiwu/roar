package com.xieguiawu.roar.asr

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.xieguiawu.roar.core.DialectRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * ModelProvider 依赖 Context 的路径（Robolectric）：
 * 模型目录按方言组织、未下载/未打包模型时 isModelReady 必须为 false
 * （设置页据此显示「模型未就绪」+ 下载按钮，2026-08-28 修复下载链路）。
 */
@RunWith(RobolectricTestRunner::class)
class ModelProviderAndroidTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun modelDir_位于filesDir下() {
        assertEquals(File(context.filesDir, "models"), ModelProvider.modelDir(context))
    }

    @Test
    fun dialectModelDir_为modelDir下的方言子目录() {
        assertEquals(
            File(File(context.filesDir, "models"), "cantonese"),
            ModelProvider.dialectModelDir(context, "cantonese"),
        )
    }

    @Test
    fun isModelReady_未下载模型时false() {
        assertFalse(ModelProvider.isModelReady(context, DialectRegistry.CANTONESE))
    }

    @Test
    fun sherpaRecognizer_isModelReady_未下载模型时false() {
        assertFalse(SherpaRecognizer.isModelReady(context, DialectRegistry.CANTONESE))
    }

    @Test
    fun ensureModels_assets未打包模型时false() {
        assertFalse(ModelProvider.ensureModels(context, DialectRegistry.CANTONESE))
    }

    /** 离线假方言：模型文件已就绪 → downloadModels 零网络直返回 true。 */
    private fun completeOfflineSpec(): com.xieguiawu.roar.core.DialectSpec {
        val bytes = "roar-fake-model-payload".toByteArray()
        val sha = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        return com.xieguiawu.roar.core.DialectSpec(
            id = "test-offline",
            displayName = "测试离线方言",
            dictAssetPath = "dialect/cantonese_dict.json",
            rules = emptyList(),
            model = com.xieguiawu.roar.core.ModelSpec(
                repo = "test/offline",
                files = listOf(
                    com.xieguiawu.roar.core.ModelFileSpec(
                        name = "fake.bin",
                        sizeBytes = bytes.size.toLong(),
                        sha256Hex = sha,
                    ),
                ),
            ),
        )
    }

    @Test
    fun downloadModels_全部就绪时零网络直返回true_且下载锁释放() {
        val dialect = completeOfflineSpec()
        val dir = ModelProvider.dialectModelDir(context, dialect.id)
        dir.mkdirs()
        java.io.File(dir, "fake.bin").writeBytes("roar-fake-model-payload".toByteArray())

        assertTrue(ModelProvider.downloadModels(context, dialect))
        // 并发守卫（P1 加固）：finally 必须释放，否则后续下载永久锁死
        assertFalse(ModelProvider.isDownloadRunning())
        assertFalse(java.io.File(dir, "fake.bin.part").exists())
    }

    @Test
    fun downloadModels_文件缺失时_Robolectric假HTTP返回空体校验失败不落位() {
        val dialect = completeOfflineSpec()
        val dir = ModelProvider.dialectModelDir(context, dialect.id)
        // 不创建文件 → pending 非空 → 走下载分支（Robolectric 假 HTTP 返回 200 空体）
        assertFalse(ModelProvider.downloadModels(context, dialect))
        assertFalse(ModelProvider.isDownloadRunning())
        // 校验失败的 .part 必须删除（损坏文件不落位）
        assertFalse("损坏的 .part 不应残留", java.io.File(dir, "fake.bin.part").exists())
        assertFalse("校验失败的目标文件不应存在", java.io.File(dir, "fake.bin").exists())
    }
}

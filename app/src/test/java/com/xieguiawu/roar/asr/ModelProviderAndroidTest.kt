package com.xieguiawu.roar.asr

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.xieguiawu.roar.core.DialectRegistry
import org.junit.Assert.assertEquals
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
}

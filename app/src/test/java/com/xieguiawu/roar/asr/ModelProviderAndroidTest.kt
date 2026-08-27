package com.xieguiawu.roar.asr

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * ModelProvider 依赖 Context 的路径（Robolectric）：
 * 模型目录位置、未下载/未打包模型时 isModelReady 必须为 false
 * （Task 4 设置页据此显示「模型未就绪」）。
 */
@RunWith(RobolectricTestRunner::class)
class ModelProviderAndroidTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun modelDir_位于filesDir下() {
        assertEquals(File(context.filesDir, "models"), ModelProvider.modelDir(context))
    }

    @Test
    fun cantoneseModelDir_为modelDir下的cantonese子目录() {
        assertEquals(
            File(File(context.filesDir, "models"), "cantonese"),
            ModelProvider.cantoneseModelDir(context),
        )
    }

    @Test
    fun isModelReady_未下载模型时false() {
        assertFalse(ModelProvider.isModelReady(context))
    }

    @Test
    fun sherpaRecognizer_isModelReady_未下载模型时false() {
        assertFalse(SherpaRecognizer.isModelReady(context))
    }

    @Test
    fun ensureModels_assets未打包模型时false() {
        assertFalse(ModelProvider.ensureModels(context))
    }
}

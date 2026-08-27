package com.xieguiawu.roar.ime

import android.inputmethodservice.InputMethodService
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import com.xieguiawu.roar.asr.RecognizerListener
import com.xieguiawu.roar.asr.SherpaRecognizer

/**
 * Roar 粤语正字语音输入法服务（Task 4 骨架）。
 *
 * 输入视图由 Compose 提供（[ImeScreen]）：候选栏点击 → [commitText] 上屏；
 * 录音按钮按住/抬起 → [SherpaRecognizer] 的 start/stop（Task 3 已真实接入 sherpa-onnx，
 * 模型未就绪时 [SherpaRecognizer.start] 会经 [RecognizerListener.onError] 提示）。
 * ASR 部分结果经 [asrText] 状态桥接显示在候选栏下方。
 *
 * Task 5 将把 [RecognizerListener.onFinal] 接上 ImePipeline（正字引擎 → 候选排序），
 * 替换 [ImeScreen] 的硬编码示例候选。
 */
class RoarImeService : InputMethodService() {

    /** ASR 实时结果（partial/final/error 文案），由 Compose 状态桥接驱动重组。 */
    private val asrText = mutableStateOf<String?>(null)

    private var recognizer: SherpaRecognizer? = null

    override fun onCreateInputView(): View {
        val composeView = ComposeView(this)
        composeView.setContent {
            val preview: String? by asrText
            ImeScreen(
                onSubmit = { text -> commitText(text) },
                onStartRecord = { ensureRecognizer().start() },
                onStopRecord = { recognizer?.stop() },
                asrText = preview,
            )
        }
        return composeView
    }

    private fun commitText(text: String) {
        currentInputConnection?.commitText(text, 1)
        asrText.value = null
    }

    /** 懒创建识别器；回调已在主线程（见 [SherpaRecognizer] 的 Handler 投递）。 */
    private fun ensureRecognizer(): SherpaRecognizer {
        val existing = recognizer
        if (existing != null) return existing
        val created = SherpaRecognizer(
            context = this,
            listener = object : RecognizerListener {
                override fun onPartial(text: String) {
                    asrText.value = text
                }

                override fun onFinal(text: String) {
                    asrText.value = text
                }

                override fun onError(message: String) {
                    asrText.value = message
                }
            },
        )
        recognizer = created
        return created
    }

    override fun onDestroy() {
        recognizer?.release()
        recognizer = null
        super.onDestroy()
    }
}

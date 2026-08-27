package com.xieguiawu.roar.ime

import android.inputmethodservice.InputMethodService
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import com.xieguiawu.roar.asr.RecognizerListener
import com.xieguiawu.roar.asr.SherpaRecognizer
import com.xieguiawu.roar.core.DialectDictionary
import com.xieguiawu.roar.core.RankedCandidate

/**
 * Roar 粤语正字语音输入法服务：端到端数据流（Task 5 接线完成）。
 *
 * 数据流：按住录音 → [SherpaRecognizer]（sherpa-onnx 端侧 ASR）→
 * [RecognizerListener.onFinal] → [ImePipeline.toCandidates]（正字化 + 词频/上下文排序）→
 * [ImeScreen] 候选栏 → 点选 [commitText] 上屏到任意输入框。
 *
 * - 词典 [dict] 懒加载自 assets `dialect/cantonese_dict.json`（510 条，端侧，不联网）；
 * - [lastContext] 记录上一句正字结果，供 [ImePipeline] 做 bigram 共现加分；
 * - 候选与 ASR 实时文本经 [mutableStateOf] 桥接 Compose 重组；
 * - 模型未就绪时 [SherpaRecognizer.start] 经 [RecognizerListener.onError] 提示，
 *   引导用户在设置页下载模型（首次运行约 238MB）。
 */
class RoarImeService : InputMethodService() {

    /** ASR 实时结果（partial/final/error 文案），由 Compose 状态桥接驱动重组。 */
    private val asrText = mutableStateOf<String?>(null)

    /** 当前候选列表（ImePipeline 输出），初始为空（无硬编码候选）。 */
    private val candidates = mutableStateOf<List<RankedCandidate>>(emptyList())

    /** 上一句正字结果，供下一句候选排序做上下文加分。 */
    private var lastContext: String? = null

    /** 粤语正字词典（懒加载单例；加载失败退化为空词典，避免 IME 崩溃）。 */
    private val dict: DialectDictionary by lazy {
        runCatching {
            DialectDictionary.loadFromJson(
                assets.open(DICT_ASSET_PATH).bufferedReader().use { it.readText() }
            )
        }.getOrElse {
            DialectDictionary(emptyList())
        }
    }

    private var recognizer: SherpaRecognizer? = null

    override fun onCreateInputView(): View {
        val composeView = ComposeView(this)
        composeView.setContent {
            val preview: String? by asrText
            val currentCandidates: List<RankedCandidate> by candidates
            ImeScreen(
                onSubmit = { text -> commitCandidate(text) },
                onStartRecord = { ensureRecognizer().start() },
                onStopRecord = { recognizer?.stop() },
                candidates = currentCandidates,
                asrText = preview,
            )
        }
        return composeView
    }

    /** 点选候选上屏；该候选同时作为下一句的上下文。 */
    private fun commitCandidate(text: String) {
        currentInputConnection?.commitText(text, 1)
        lastContext = text
        candidates.value = emptyList()
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
                    val result = ImePipeline.toCandidates(text, dict, lastContext)
                    // 正字主推候选作为下一句的上下文（比原始 ASR 文本更干净）
                    lastContext = result.firstOrNull()?.text ?: text
                    asrText.value = text
                    candidates.value = result
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

    private companion object {
        /** assets 中粤语词典路径（端侧内置，不联网）。 */
        const val DICT_ASSET_PATH = "dialect/cantonese_dict.json"
    }
}

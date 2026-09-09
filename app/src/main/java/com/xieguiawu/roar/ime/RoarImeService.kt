package com.xieguiawu.roar.ime

import android.content.Context
import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.xieguiawu.roar.asr.RecognizerListener
import com.xieguiawu.roar.asr.SherpaRecognizer
import com.xieguiawu.roar.core.DialectDictionary
import com.xieguiawu.roar.core.DialectRegistry
import com.xieguiawu.roar.core.DialectSpec
import com.xieguiawu.roar.core.RankedCandidate
import com.xieguiawu.roar.ui.RoarTheme

/**
 * Roar 方言正字语音输入法服务：端到端数据流（方言参数化，Task 5 接线 + 2026-08-28 多方言化）。
 *
 * 数据流：按住录音 → [SherpaRecognizer]（sherpa-onnx 端侧 ASR，当前方言模型）→
 * [RecognizerListener.onFinal] → [ImePipeline.toCandidates]（正字化 + 词频/上下文排序）→
 * [ImeScreen] 候选栏 → 点选 [commitText] 上屏到任意输入框。
 *
 * - 方言：[currentDialect] 读取设置页持久化的 `selected_dialect_id`（[SettingsController]
 *   写入），未知 id 回退默认（粤语）；切换方言后 IME 下次启动生效；
 * - Compose 宿主：本服务实现 [LifecycleOwner] + [SavedStateRegistryOwner] 并在
 *   [onCreateInputView] 设置到 ComposeView 的 ViewTree 上（compose-ui 1.7 起
 *   AndroidComposeView 硬性要求，缺失即抛 IllegalStateException——IME 窗口无
 *   Activity 提供者，必须自供）；生命周期映射：onStartInputView→RESUMED、
 *   onFinishInputView→PAUSED、onWindowHidden→STOPPED、onDestroy→DESTROYED；
 * - 主题：[RoarTheme] 强制深色（键盘无视系统明暗设置，产品决定）；
 * - 词典 [dict] 懒加载自当前方言的 assets 词典 JSON（510 条粤语，端侧，不联网）；
 * - [lastContext] 记录上一句正字结果，供 [ImePipeline] 做 bigram 共现加分；
 * - 候选与 ASR 实时文本经 [mutableStateOf] 桥接 Compose 重组；
 * - 模型未就绪时 [SherpaRecognizer.start] 经 [RecognizerListener.onError] 提示，
 *   引导用户在设置页下载模型（首次运行约 238MB，见 [SettingsController.downloadModel]）。
 */
class RoarImeService : InputMethodService(), LifecycleOwner, SavedStateRegistryOwner {

    private val savedStateController = SavedStateRegistryController.create(this)

    /** Compose 宿主必需：ViewTree 上的 LifecycleOwner（IME 窗口无 Activity 提供者）。 */
    private val lifecycleRegistry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    /** ASR 实时结果（partial/final/error 文案），由 Compose 状态桥接驱动重组。 */
    private val asrText = mutableStateOf<String?>(null)

    /** 当前候选列表（ImePipeline 输出），初始为空（无硬编码候选）。 */
    private val candidates = mutableStateOf<List<RankedCandidate>>(emptyList())

    /** 上一句正字结果，供下一句候选排序做上下文加分。 */
    private var lastContext: String? = null

    /** 当前方言（onCreateInputView 时解析；设置页切换后 IME 下次启动生效）。 */
    private var dialect: DialectSpec = DialectRegistry.default

    /** 当前方言正字词典；加载失败退化为空词典，避免 IME 崩溃。 */
    private var dict: DialectDictionary = DialectDictionary(emptyList())

    private var recognizer: SherpaRecognizer? = null

    override fun onCreate() {
        super.onCreate()
        // ViewTree owners 初始化（performRestore 必须在 registry 被读取前调用）
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onCreateInputView(): View {
        dialect = resolveDialect()
        dict = loadDict(dialect)
        // 方言切换后重建识别器（旧方言模型句柄释放）
        recognizer?.release()
        recognizer = null

        val composeView = ComposeView(this)
        // compose-ui 1.7+：AndroidComposeView.onAttachedToWindow 强制要求 ViewTree
        // LifecycleOwner / SavedStateRegistryOwner，缺失即在真机 IME 窗口抛
        // IllegalStateException（键盘首弹即崩）——IME 窗口无 Activity，必须自供。
        // 注意：ViewTreeLifecycleOwner/ViewTreeSavedStateRegistryOwner 是文件 facade 类，
        // Kotlin 侧只能用 View 扩展函数（Java 侧才是 ViewTreeLifecycleOwner.set）。
        composeView.setViewTreeLifecycleOwner(this)
        composeView.setViewTreeSavedStateRegistryOwner(this)
        composeView.setContent {
            RoarTheme {
                val preview: String? by asrText
                val currentCandidates: List<RankedCandidate> by candidates
                ImeScreen(
                    onSubmit = { text -> commitCandidate(text) },
                    onStartRecord = { ensureRecognizer().start() },
                    onStopRecord = { recognizer?.stop() },
                    candidates = currentCandidates,
                    asrText = preview,
                    dialectLabel = dialect.displayName,
                )
            }
        }
        return composeView
    }

    override fun onStartInputView(editorInfo: EditorInfo, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        super.onFinishInputView(finishingInput)
    }

    override fun onWindowHidden() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onWindowHidden()
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        recognizer?.release()
        recognizer = null
        super.onDestroy()
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
            dialect = dialect,
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

    /** 从设置页持久化的方言 id 解析当前方言；未知/缺失回退默认。 */
    private fun resolveDialect(): DialectSpec {
        val prefs: SharedPreferences =
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(PREFS_KEY_DIALECT, null)
        return if (saved == null) DialectRegistry.default else DialectRegistry.get(saved)
    }

    /** 加载当前方言词典；资产损坏时退化为空词典（永不崩溃）。 */
    private fun loadDict(spec: DialectSpec): DialectDictionary {
        return runCatching {
            DialectDictionary.loadFromJson(
                assets.open(spec.dictAssetPath).bufferedReader().use { it.readText() }
            )
        }.getOrElse {
            DialectDictionary(emptyList())
        }
    }

    private companion object {
        /** 与 [SettingsController] 一致的设置存储（键名相同）。 */
        const val PREFS_NAME = "roar_settings"
        const val PREFS_KEY_DIALECT = "selected_dialect_id"
    }
}

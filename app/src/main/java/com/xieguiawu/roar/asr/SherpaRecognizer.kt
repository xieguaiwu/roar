package com.xieguiawu.roar.asr

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineParaformerModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * sherpa-onnx 流式在线识别器封装。
 *
 * - 模型：流式 paraformer 三语（普通话/粤语/英语）int8，文件由 [ModelProvider] 定位与校验；
 * - 录音：16 kHz 单声道 PCM16，每次读取 [SAMPLES_PER_READ] 个采样点（约 200ms）；
 * - 线程：单后台线程跑「录音 → acceptWaveform → decode」循环，stop 时 flush 尾音；
 * - 回调：所有 [RecognizerListener] 回调经主线程 Handler 投递。
 *
 * 依赖：`libs/sherpa-onnx-1.13.6.aar`（官方 GitHub Release，mavenCentral 无官方坐标）。
 * 注意 [OnlineRecognizer] 构造传入 `null` AssetManager 即按文件系统路径加载
 * （模型经 [ModelProvider] 下载到 filesDir，不在 assets 中）。
 */
class SherpaRecognizer(
    private val context: Context,
    private val listener: RecognizerListener,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)

    @Volatile
    private var recognizer: OnlineRecognizer? = null

    @Volatile
    private var stream: OnlineStream? = null

    @Volatile
    private var audioRecord: AudioRecord? = null

    private var workerThread: Thread? = null

    /** 是否正在录音识别。 */
    val isRecording: Boolean get() = running.get()

    /**
     * 开始录音识别。模型未就绪时不抛异常，而是回调 [RecognizerListener.onError]，
     * 由 UI 引导用户在设置页下载模型。
     */
    fun start() {
        if (!running.compareAndSet(false, true)) return
        if (!ModelProvider.isModelReady(context)) {
            running.set(false)
            listener.onError("模型未就绪：请先在设置页下载粤语 ASR 模型")
            return
        }
        workerThread = thread(name = "RoarAsrWorker") { runRecognitionLoop() }
    }

    /** 停止录音并 flush 尾音，最终结果经 [RecognizerListener.onFinal] 回调。 */
    fun stop() {
        if (!running.getAndSet(false)) return
        // 打断阻塞中的 AudioRecord.read，让循环尽快退出并 flush
        runCatching { audioRecord?.stop() }
        workerThread?.join(MAX_STOP_JOIN_MS)
        workerThread = null
    }

    /** 释放全部资源（含 AudioRecord 与 sherpa-onnx native 句柄）。 */
    fun release() {
        stop()
        recognizer?.release()
        recognizer = null
        stream?.release()
        stream = null
    }

    private fun runRecognitionLoop() {
        try {
            val rec = createRecognizer()
            recognizer = rec
            val s = rec.createStream("")
            stream = s
            recordAndDecode(rec, s)
        } catch (e: Exception) {
            postToMain { listener.onError(e.message ?: "语音识别失败") }
        } finally {
            runCatching { audioRecord?.stop() }
            runCatching { audioRecord?.release() }
            audioRecord = null
            recognizer?.release()
            recognizer = null
            stream?.release()
            stream = null
            running.set(false)
        }
    }

    private fun createRecognizer(): OnlineRecognizer {
        val dir = ModelProvider.cantoneseModelDir(context)
        check(ModelProvider.isModelComplete(dir)) { "模型文件不完整：$dir" }
        val config = OnlineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = FEATURE_DIM),
            modelConfig = OnlineModelConfig(
                paraformer = OnlineParaformerModelConfig(
                    encoder = File(dir, ENCODER_FILE).absolutePath,
                    decoder = File(dir, DECODER_FILE).absolutePath,
                ),
                tokens = File(dir, TOKENS_FILE).absolutePath,
                numThreads = NUM_THREADS,
                provider = "cpu",
            ),
            enableEndpoint = true,
        )
        return OnlineRecognizer(null, config)
    }

    private fun recordAndDecode(rec: OnlineRecognizer, s: OnlineStream) {
        // 运行时权限可能被用户回收，录音前显式校验（SecurityException 由 runRecognitionLoop 捕获→onError）
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException("缺少录音权限：请先在设置页授予 RECORD_AUDIO 权限")
        }
        val minBufferBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferBytes = maxOf(minBufferBytes, SAMPLES_PER_READ * BYTES_PER_SAMPLE)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferBytes,
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw IllegalStateException("麦克风初始化失败：请检查 RECORD_AUDIO 权限")
        }
        audioRecord = recorder
        recorder.startRecording()

        val shorts = ShortArray(SAMPLES_PER_READ)
        while (running.get()) {
            val n = recorder.read(shorts, 0, shorts.size)
            if (n < 0) {
                // stop() 会主动 stop AudioRecord 打断 read，此时属正常退出
                if (running.get()) throw IllegalStateException("录音读取失败：code=$n")
                break
            }
            if (n == 0) continue
            val floats = FloatArray(n) { i -> shorts[i] / 32768.0f }
            s.acceptWaveform(floats, SAMPLE_RATE)
            while (rec.isReady(s)) rec.decode(s)
            val partial = rec.getResult(s).text
            if (partial.isNotBlank()) postToMain { listener.onPartial(partial) }
            if (rec.isEndpoint(s)) finishSegment(rec, s)
        }
        // 退出后统一 flush 尾音，保证最后一句完整出最终结果
        finishSegment(rec, s)
    }

    /** inputFinished → 解码排空 → onFinal → reset 供下一句使用。 */
    private fun finishSegment(rec: OnlineRecognizer, s: OnlineStream) {
        s.inputFinished()
        while (rec.isReady(s)) rec.decode(s)
        val text = rec.getResult(s).text
        if (text.isNotBlank()) postToMain { listener.onFinal(text) }
        rec.reset(s)
    }

    private fun postToMain(block: () -> Unit) {
        mainHandler.post(block)
    }

    companion object {
        const val SAMPLE_RATE = 16000
        const val FEATURE_DIM = 80
        const val NUM_THREADS = 2

        /** 每次读取的采样点数：3200 个采样 @16kHz ≈ 200ms 一帧。 */
        const val SAMPLES_PER_READ = 3200
        const val BYTES_PER_SAMPLE = 2
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val ENCODER_FILE = "encoder.int8.onnx"
        const val DECODER_FILE = "decoder.int8.onnx"
        const val TOKENS_FILE = "tokens.txt"

        /** stop() 最多等待后台线程多久完成 flush（毫秒）。 */
        const val MAX_STOP_JOIN_MS = 2000L

        /** 模型是否存在且完整（供设置页显示模型状态）。 */
        fun isModelReady(context: Context): Boolean = ModelProvider.isModelReady(context)
    }
}

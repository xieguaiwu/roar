package com.xieguiawu.roar.asr

import android.content.Context
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * 端侧 ASR 模型文件的定位、完整性校验与首次运行下载。
 *
 * ## 模型选型（2026-08-28 查证，k2-fsa sherpa-onnx 模型库）
 *
 * - 计划要求的「粤语流式 **zipformer**」模型**不存在**——k2-fsa 模型库中 zipformer
 *   流式模型仅普通话（multi-zh-hans 系列），无粤语版；
 * - 流式粤语的最优可用模型为 k2-fsa 维护者 csukuangfj 发布的
 *   `sherpa-onnx-streaming-paraformer-trilingual-zh-cantonese-en`
 *   （流式 paraformer，普通话/粤语/英语三语，源自 FunASR paraformer-large）；
 * - int8 量化版总体积约 **238 MB**（encoder 166 MB + decoder 72 MB + tokens.txt 81 KB），
 *   超过计划的 100 MB 打包阈值，因此**不放入 assets**，改为首次运行从 HuggingFace 下载
 *   （[downloadModels]）。若未来换 <100MB 的小模型，可直接放
 *   `app/src/main/assets/models/cantonese/`，[ensureModels] 会从 assets 解包。
 *
 * 每个文件下载后都校验体积与 SHA-256（[sha256]），校验失败不落地，防止半截/篡改文件。
 * 所有数据端侧落盘，下载本身是模型文件获取，不涉及用户语音数据上传。
 */
object ModelProvider {

    /** 模型仓库（HuggingFace，k2-fsa 维护者 csukuangfj 发布）。 */
    const val MODEL_REPO =
        "csukuangfj/sherpa-onnx-streaming-paraformer-trilingual-zh-cantonese-en"

    const val MODEL_BASE_URL = "https://huggingface.co/$MODEL_REPO/resolve/main"

    /** assets 中的模型目录前缀（当前未打包，保留解包路径以便未来换小模型）。 */
    private const val ASSET_MODEL_PREFIX = "models/cantonese/"

    private const val DOWNLOAD_BUFFER_BYTES = 64 * 1024
    private const val CONNECT_TIMEOUT_MS = 30_000
    private const val READ_TIMEOUT_MS = 120_000

    /** 单个模型文件的元数据（体积与哈希来自 HuggingFace 元数据，2026-08-28 记录）。 */
    data class ModelFileSpec(
        val name: String,
        val sizeBytes: Long,
        val sha256Hex: String,
    ) {
        val url: String get() = "$MODEL_BASE_URL/$name"
    }

    /** 运行所需的全部模型文件。 */
    val MODEL_FILES: List<ModelFileSpec> = listOf(
        ModelFileSpec(
            name = "encoder.int8.onnx",
            sizeBytes = 166_362_800,
            sha256Hex = "6047a644b41b236d9d8e89e3b94ef39d1b7037daab028131b722ca52e10b0357",
        ),
        ModelFileSpec(
            name = "decoder.int8.onnx",
            sizeBytes = 72_062_549,
            sha256Hex = "545427acf508452b7d89969be082c8128c681e3432ff43aef09f6159f4b61a7e",
        ),
        ModelFileSpec(
            name = "tokens.txt",
            sizeBytes = 81_289,
            sha256Hex = "45b31504211675dd52aa88f998a6f6161703a2834e86760c1cda645a22538085",
        ),
    )

    /** 模型总下载体积（约 238 MB，>100 MB 计划阈值，故走首次运行下载）。 */
    val MODEL_TOTAL_BYTES: Long = MODEL_FILES.sumOf { it.sizeBytes }

    /** 模型根目录：`context.filesDir/models`。 */
    fun modelDir(context: Context): File = File(context.filesDir, "models")

    /** 粤语模型目录：`context.filesDir/models/cantonese`。 */
    fun cantoneseModelDir(context: Context): File = File(modelDir(context), "cantonese")

    /**
     * 确保模型就绪：若 filesDir 中已有完整模型直接成功；否则尝试从 assets 解包
     * （当前未打包模型，该分支恒空）。返回 true 表示模型文件齐全。
     */
    fun ensureModels(context: Context): Boolean {
        val dir = cantoneseModelDir(context)
        if (isModelComplete(dir)) return true
        return unpackFromAssets(context, dir) && isModelComplete(dir)
    }

    /** 模型是否就绪（文件齐全且体积一致；哈希在下载时已校验）。 */
    fun isModelReady(context: Context): Boolean = ensureModels(context)

    /** 目录内模型文件是否齐全且体积与元数据一致。 */
    fun isModelComplete(dir: File): Boolean = MODEL_FILES.all { spec ->
        val file = File(dir, spec.name)
        file.isFile && file.length() == spec.sizeBytes
    }

    /**
     * 阻塞式下载全部缺失/损坏的模型文件到 filesDir（**必须在后台线程调用**）。
     *
     * 每个文件先落 `.part` 临时文件，校验体积 + SHA-256 通过后改名落位；
     * 任一文件失败即中止并返回 false（已完成的文件保留，下次续传）。
     *
     * @param onProgress 可选进度回调（已下载字节数 / 本次需下载总字节数），非主线程回调
     * @return true 表示全部模型文件就绪且校验通过
     */
    fun downloadModels(
        context: Context,
        onProgress: ((downloadedBytes: Long, totalBytes: Long) -> Unit)? = null,
    ): Boolean {
        val dir = cantoneseModelDir(context)
        if (!dir.exists() && !dir.mkdirs()) return false

        val pending = MODEL_FILES.filter { spec ->
            val target = File(dir, spec.name)
            !(target.isFile && target.length() == spec.sizeBytes &&
                sha256(target) == spec.sha256Hex)
        }
        var downloaded = 0L
        val total = pending.sumOf { it.sizeBytes }

        for (spec in pending) {
            val target = File(dir, spec.name)
            val part = File(dir, spec.name + ".part")
            try {
                downloadToFile(spec.url, part) { chunkBytes ->
                    downloaded += chunkBytes
                    onProgress?.invoke(downloaded, total)
                }
                if (part.length() != spec.sizeBytes || sha256(part) != spec.sha256Hex) {
                    part.delete()
                    return false
                }
                if (!part.renameTo(target)) {
                    part.delete()
                    return false
                }
            } catch (e: IOException) {
                part.delete()
                return false
            }
        }
        return true
    }

    /** 计算文件 SHA-256，返回 64 位小写十六进制。 */
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }

    /** 流式下载到 [part]，每读一块回调一次 [onChunk]。 */
    private fun downloadToFile(url: String, part: File, onChunk: (Int) -> Unit) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", "Roar/0.1.0 (Cantonese IME model download)")
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw IOException("下载失败：HTTP $code（$url）")
            }
            connection.inputStream.use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        onChunk(n)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    /** 从 assets 解包模型到 filesDir（当前未打包模型时返回 false，属正常路径）。 */
    private fun unpackFromAssets(context: Context, dir: File): Boolean {
        val names = try {
            context.assets.list(ASSET_MODEL_PREFIX) ?: return false
        } catch (e: IOException) {
            return false
        }
        if (names.isEmpty()) return false
        if (!dir.exists() && !dir.mkdirs()) return false
        return try {
            for (name in names) {
                context.assets.open(ASSET_MODEL_PREFIX + name).use { input ->
                    File(dir, name).outputStream().use { output -> input.copyTo(output) }
                }
            }
            true
        } catch (e: IOException) {
            false
        }
    }
}

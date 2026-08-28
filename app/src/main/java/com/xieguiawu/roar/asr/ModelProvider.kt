package com.xieguiawu.roar.asr

import android.content.Context
import com.xieguiawu.roar.core.DialectRegistry
import com.xieguiawu.roar.core.DialectSpec
import com.xieguiawu.roar.core.ModelFileSpec
import com.xieguiawu.roar.core.ModelSpec
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * 端侧 ASR 模型文件的定位、完整性校验与首次运行下载。
 *
 * 模型按方言组织：`filesDir/models/<dialectId>/`（见 [dialectModelDir]），
 * 文件清单与校验值来自 [DialectSpec.model]（[DialectRegistry] 注册）。
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
 *   `app/src/main/assets/models/<dialectId>/`，[ensureModels] 会从 assets 解包。
 *
 * 每个文件下载后都校验体积与 SHA-256（[sha256]），校验失败不落地，防止半截/篡改文件。
 * 下载源策略（2026-08-28 修复，国内网络实测 HuggingFace 直连经常不可达）：
 * 先尝试官方源 huggingface.co，连接失败/HTTP 错误/校验失败时自动回退到国内镜像
 * hf-mirror.com（逐文件回退，已完成文件保留，下次续传缺失文件）。
 * 所有数据端侧落盘，下载本身是模型文件获取，不涉及用户语音数据上传。
 */
object ModelProvider {

    /** 模型根目录：`context.filesDir/models`。 */
    fun modelDir(context: Context): File = File(context.filesDir, "models")

    /** 指定方言的模型目录：`context.filesDir/models/<dialectId>`。 */
    fun dialectModelDir(context: Context, dialectId: String): File =
        File(modelDir(context), dialectId)

    /**
     * 确保模型就绪：若 filesDir 中已有完整模型直接成功；否则尝试从 assets 解包
     * （当前未打包模型，该分支恒空）。返回 true 表示模型文件齐全。
     */
    fun ensureModels(context: Context, dialect: DialectSpec): Boolean {
        val spec = dialect.model ?: return false
        val dir = dialectModelDir(context, dialect.id)
        if (isModelComplete(dir, spec)) return true
        return unpackFromAssets(context, dialect, dir) && isModelComplete(dir, spec)
    }

    /** 模型是否就绪（文件齐全且体积一致；哈希在下载时已校验）。 */
    fun isModelReady(context: Context, dialect: DialectSpec): Boolean =
        ensureModels(context, dialect)

    /** 目录内模型文件是否齐全且体积与元数据一致。 */
    fun isModelComplete(dir: File, spec: ModelSpec): Boolean = spec.files.all { fileSpec ->
        val file = File(dir, fileSpec.name)
        file.isFile && file.length() == fileSpec.sizeBytes
    }

    /**
     * 阻塞式下载全部缺失/损坏的模型文件到 filesDir（**必须在后台线程调用**）。
     *
     * 每个文件先落 `.part` 临时文件，校验体积 + SHA-256 通过后改名落位；
     * 任一文件失败即中止并返回 false（已完成的文件保留，下次续传）。
     * 官方源（huggingface.co）失败时自动回退到国内镜像（hf-mirror.com）。
     *
     * @param dialect 方言方案（模型仓库与文件清单来源）
     * @param onProgress 可选进度回调（已下载字节数 / 本次需下载总字节数），非主线程回调
     * @return true 表示全部模型文件就绪且校验通过
     */
    fun downloadModels(
        context: Context,
        dialect: DialectSpec,
        onProgress: ((downloadedBytes: Long, totalBytes: Long) -> Unit)? = null,
    ): Boolean {
        val spec = dialect.model ?: return false
        val dir = dialectModelDir(context, dialect.id)
        if (!dir.exists() && !dir.mkdirs()) return false

        val pending = spec.files.filter { fileSpec ->
            val target = File(dir, fileSpec.name)
            !(target.isFile && target.length() == fileSpec.sizeBytes &&
                sha256(target) == fileSpec.sha256Hex)
        }
        var downloaded = 0L
        val total = pending.sumOf { it.sizeBytes }

        for (fileSpec in pending) {
            val target = File(dir, fileSpec.name)
            val part = File(dir, fileSpec.name + ".part")
            if (!downloadWithFallback(fileSpec, spec, part)) {
                part.delete()
                return false
            }
            if (part.length() != fileSpec.sizeBytes || sha256(part) != fileSpec.sha256Hex) {
                part.delete()
                return false
            }
            if (!part.renameTo(target)) {
                part.delete()
                return false
            }
            downloaded += fileSpec.sizeBytes
            onProgress?.invoke(downloaded, total)
        }
        return true
    }

    /** 先官方源后镜像源逐文件下载，任一源成功即返回 true。 */
    private fun downloadWithFallback(fileSpec: ModelFileSpec, spec: ModelSpec, part: File): Boolean {
        for (baseUrl in listOf(spec.hfBaseUrl, spec.mirrorBaseUrl)) {
            try {
                downloadToFile(fileSpec.url(baseUrl), part)
                return true
            } catch (e: IOException) {
                part.delete()
            }
        }
        return false
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

    /** 流式下载到 [part]（覆盖写；失败时由调用方删除 .part）。 */
    private fun downloadToFile(url: String, part: File) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", "Roar/0.1.0 (dialect IME model download)")
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
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    /** 从 assets 解包模型到 filesDir（当前未打包模型时返回 false，属正常路径）。 */
    private fun unpackFromAssets(context: Context, dialect: DialectSpec, dir: File): Boolean {
        val prefix = "models/${dialect.id}/"
        val names = try {
            context.assets.list(prefix) ?: return false
        } catch (e: IOException) {
            return false
        }
        if (names.isEmpty()) return false
        if (!dir.exists() && !dir.mkdirs()) return false
        return try {
            for (name in names) {
                context.assets.open(prefix + name).use { input ->
                    File(dir, name).outputStream().use { output -> input.copyTo(output) }
                }
            }
            true
        } catch (e: IOException) {
            false
        }
    }

    private const val DOWNLOAD_BUFFER_BYTES = 64 * 1024
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 120_000

    /** 便捷入口：默认方言（粤语）模型是否就绪（设置页/IME 兼容旧调用）。 */
    fun isModelReady(context: Context): Boolean =
        isModelReady(context, DialectRegistry.default)
}

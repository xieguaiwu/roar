package com.xieguiawu.roar.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.xieguiawu.roar.asr.ModelProvider
import com.xieguiawu.roar.core.DialectRegistry
import com.xieguiawu.roar.core.DialectSpec
import kotlin.concurrent.thread

/** 模型下载状态机（设置页展示用）。 */
sealed interface ModelDownloadState {
    /** 未下载（或本地文件缺失）。 */
    data object NotDownloaded : ModelDownloadState

    /** 下载中（已下载字节 / 本次需下载总字节）。 */
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : ModelDownloadState {
        val fraction: Float
            get() = if (totalBytes <= 0) 0f else downloadedBytes.toFloat() / totalBytes
    }

    /** 已就绪（文件齐全 + 校验通过）。 */
    data object Ready : ModelDownloadState

    /** 下载失败（[message] 为可读原因，可重试）。 */
    data class Failed(val message: String) : ModelDownloadState
}

/**
 * 设置页状态控制器：方言选择（SharedPreferences 持久化）+ 模型下载。
 *
 * 由 [MainActivity] 与 [SettingsActivity] 共享（remember 创建，applicationContext
 * 防 Activity 泄漏）。Compose 通过 [selectedDialect]/[modelState] 两个
 * mutableStateOf 观察重组；下载在后台线程执行，进度经主线程回写。
 *
 * 2026-08-28 修复根因：此前 `downloadModels` 没有任何调用点，设置页无下载入口，
 * 模型文件永远缺失 → 「模型未就绪」恒真 → APK 装上不可用。本控制器是下载链路的
 * 唯一入口（设置页按钮触发）。
 */
class SettingsController(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 当前方言（默认粤语；持久化在 [PREFS_KEY_DIALECT]）。 */
    var selectedDialect: DialectSpec by mutableStateOf(resolveDialect())
        private set

    /** 模型状态：随方言切换重新计算，下载完成后自动置 [ModelDownloadState.Ready]。 */
    var modelState: ModelDownloadState by mutableStateOf(computeModelState())
        private set

    /** 切换方言：持久化 + 重算模型状态；IME 下次启动按新方言加载。 */
    fun selectDialect(dialectId: String) {
        val dialect = DialectRegistry.get(dialectId)
        if (dialect.id == selectedDialect.id) return
        selectedDialect = dialect
        prefs.edit().putString(PREFS_KEY_DIALECT, dialect.id).apply()
        modelState = computeModelState()
    }

    /** 后台下载当前方言模型；下载中重复调用被忽略。 */
    fun downloadModel() {
        val dialect = selectedDialect
        val spec = dialect.model ?: return
        if (modelState is ModelDownloadState.Downloading) return
        modelState = ModelDownloadState.Downloading(0, spec.totalBytes)
        thread(name = "RoarModelDownload") {
            val ok = ModelProvider.downloadModels(appContext, dialect) { done, total ->
                modelState = ModelDownloadState.Downloading(done, total)
            }
            if (ok) {
                modelState = ModelDownloadState.Ready
            } else {
                modelState = ModelDownloadState.Failed(
                    "下载失败：请检查网络后重试（国内网络建议使用镜像源，已自动切换）",
                )
            }
        }
    }

    private fun computeModelState(): ModelDownloadState {
        val spec = selectedDialect.model ?: return ModelDownloadState.NotDownloaded
        return if (ModelProvider.isModelReady(appContext, selectedDialect)) {
            ModelDownloadState.Ready
        } else {
            ModelDownloadState.NotDownloaded
        }
    }

    private fun resolveDialect(): DialectSpec {
        val saved = prefs.getString(PREFS_KEY_DIALECT, null)
        return if (saved == null) DialectRegistry.default else DialectRegistry.get(saved)
    }

    private companion object {
        const val PREFS_NAME = "roar_settings"
        const val PREFS_KEY_DIALECT = "selected_dialect_id"
    }
}

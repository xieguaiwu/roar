package com.xieguiawu.roar

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.remember
import com.xieguiawu.roar.ui.SettingsController
import com.xieguiawu.roar.ui.SettingsScreen

/**
 * 桌面入口：直接展示设置页（[SettingsScreen]），
 * 与 [com.xieguiawu.roar.ui.SettingsActivity] 共享同一套内容。
 *
 * 方言选择与模型下载状态由 [SettingsController] 持有（applicationContext，
 * 不泄漏 Activity）；模型下载在后台线程执行，进度实时回写 UI。
 */
class MainActivity : ComponentActivity() {

    private val requestRecordPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // 结果无需在此处理：录音时 SherpaRecognizer 会再次校验权限并给出错误提示
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val controller = remember { SettingsController(this) }
            SettingsScreen(
                controller = controller,
                onRequestRecordPermission = {
                    requestRecordPermission.launch(Manifest.permission.RECORD_AUDIO)
                },
            )
        }
    }
}

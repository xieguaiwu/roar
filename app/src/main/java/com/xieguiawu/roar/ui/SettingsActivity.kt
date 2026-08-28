package com.xieguiawu.roar.ui

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.remember

/**
 * IME 设置页入口：`res/xml/method.xml` 的 `android:settingsActivity` 指向此处，
 * 用户在系统「语言与输入法」中点击 Roar 的设置图标即可进入。
 * 内容与 MainActivity 相同，均由 [SettingsScreen] 提供。
 */
class SettingsActivity : ComponentActivity() {

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

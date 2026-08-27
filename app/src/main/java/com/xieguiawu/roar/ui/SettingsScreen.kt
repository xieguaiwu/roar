package com.xieguiawu.roar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 设置页（Compose，[MainActivity] 与 [SettingsActivity] 共用）。
 *
 * - 应用说明：Roar 把粤语口语转成「唔該、多謝、屋企」等正字；
 * - 方言选择：当前仅「粵語（廣州話）」（Task 4 静态展示，后续加多方言）；
 * - 模型状态：[modelReady] 反映端侧 ASR 模型是否就绪；
 * - 启用指引 + 录音权限申请（[onRequestRecordPermission]）。
 *
 * [modelReady] 与 [onRequestRecordPermission] 由宿主 Activity 注入，
 * 便于 Robolectric 测试（见 SettingsActivityTest）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modelReady: Boolean,
    onRequestRecordPermission: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Roar") })
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Roar：說方言，出正字",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "把粵語口語轉成「唔該、多謝、屋企」等正字，語音識別全程在手機上完成。",
                style = MaterialTheme.typography.bodyMedium,
            )

            HorizontalDivider()

            SettingRow(label = "方言", value = "粵語（廣州話）")
            SettingRow(label = "模型狀態", value = if (modelReady) "模型就绪" else "模型未就绪")

            HorizontalDivider()

            Text(
                text = "启用指引：系统设置 → 语言与输入法 → 启用 Roar，并设为当前输入法。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onRequestRecordPermission) {
                Text("授予录音权限")
            }
        }
    }
}

@Composable
private fun SettingRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

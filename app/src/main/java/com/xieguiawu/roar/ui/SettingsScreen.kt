package com.xieguiawu.roar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xieguiawu.roar.core.DialectRegistry
import com.xieguiawu.roar.core.DialectSpec

/**
 * 设置页（Compose，[MainActivity] 与 [SettingsActivity] 共用）。
 *
 * - 应用说明：Roar 把方言口语转成「唔該、多謝、屋企」等正字；
 * - 方言选择：下拉切换（[DialectRegistry] 注册的方言），持久化后 IME 下次启动生效；
 * - 模型状态：反映端侧 ASR 模型下载状态机（[ModelDownloadState]），
 *   「下载模型」按钮触发 [ModelProvider.downloadModels]（[SettingsController] 持有）；
 * - 启用指引 + 录音权限申请（[onRequestRecordPermission]）。
 *
 * 状态与动作由宿主 Activity 经 [SettingsController] 注入，便于 Robolectric 测试
 * （见 SettingsActivityTest）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    controller: SettingsController,
    onRequestRecordPermission: () -> Unit,
) {
    val dialect = controller.selectedDialect
    val modelState = controller.modelState

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
                text = "把方言口語轉成「唔該、多謝、屋企」等正字，語音識別全程在手機上完成。",
                style = MaterialTheme.typography.bodyMedium,
            )

            HorizontalDivider()

            DialectPickerRow(dialects = DialectRegistry.dialects, selected = dialect) { id ->
                controller.selectDialect(id)
            }
            ModelStatusRow(state = modelState, onDownload = controller::downloadModel)

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

/** 方言选择行：当前方言 + 下拉菜单（切换即持久化，IME 下次启动生效）。 */
@Composable
private fun DialectPickerRow(
    dialects: List<DialectSpec>,
    selected: DialectSpec,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "方言", style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { expanded = true }) {
            Text(text = "${selected.displayName} ▾")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (d in dialects) {
                DropdownMenuItem(
                    text = { Text(d.displayName) },
                    onClick = {
                        expanded = false
                        onSelect(d.id)
                    },
                )
            }
        }
    }
}

/** 模型状态行：状态文案 + 进度条（下载中）+ 下载/重试按钮。 */
@Composable
private fun ModelStatusRow(
    state: ModelDownloadState,
    onDownload: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "模型狀態", style = MaterialTheme.typography.bodyMedium)
            when (state) {
                ModelDownloadState.NotDownloaded ->
                    Text(text = "模型未就绪", style = MaterialTheme.typography.bodyMedium)
                ModelDownloadState.Ready ->
                    Text(text = "模型就绪", style = MaterialTheme.typography.bodyMedium)
                is ModelDownloadState.Downloading ->
                    Text(
                        text = "下载中 ${state.downloadedBytes / 1024 / 1024} / " +
                            "${state.totalBytes / 1024 / 1024} MB",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                is ModelDownloadState.Failed ->
                    Text(text = "下载失败", style = MaterialTheme.typography.bodyMedium)
            }
        }
        when (state) {
            ModelDownloadState.NotDownloaded -> {
                Text(
                    text = "首次使用需下载 ASR 模型（约 238 MB，仅此一次）。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = onDownload) {
                    Text("下载模型")
                }
            }
            ModelDownloadState.Ready -> {
                Text(
                    text = "模型已就绪，按住麦克风即可开始语音输入。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            is ModelDownloadState.Downloading -> {
                LinearProgressIndicator(
                    progress = { state.fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
            }
            is ModelDownloadState.Failed -> {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = onDownload) {
                    Text("重试下载")
                }
            }
        }
    }
}

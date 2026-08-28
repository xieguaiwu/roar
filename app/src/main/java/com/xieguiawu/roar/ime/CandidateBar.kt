package com.xieguiawu.roar.ime

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xieguiawu.roar.core.RankedCandidate

/**
 * IME 候选栏 + 录音按钮。
 *
 * 候选由 [ImePipeline] 产生并经 [candidates] 注入（Task 5 起无硬编码候选），
 * 点选后经 [onSubmit] 上屏；[asrText] 显示 ASR 的实时部分结果
 * （[com.xieguiawu.roar.asr.SherpaRecognizer] 回调驱动）。录音按钮为「按住讲话」交互：
 * 按下 [onStartRecord]、抬起 [onStopRecord]，录音期间按钮变红（[recording] 状态）。
 */
@Composable
fun ImeScreen(
    onSubmit: (String) -> Unit,
    onStartRecord: () -> Unit,
    onStopRecord: () -> Unit,
    candidates: List<RankedCandidate>,
    asrText: String? = null,
    dialectLabel: String = "粵語",
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        CandidateBar(candidates = candidates, onSubmit = onSubmit)
        asrText?.takeIf { it.isNotBlank() }?.let { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
        RecordButtonRow(
            onStartRecord = onStartRecord,
            onStopRecord = onStopRecord,
            dialectLabel = dialectLabel,
        )
    }
}

@Composable
private fun CandidateBar(
    candidates: List<RankedCandidate>,
    onSubmit: (String) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(candidates, key = { it.text }) { candidate ->
            Text(
                text = candidate.text,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onSubmit(candidate.text) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun RecordButtonRow(
    onStartRecord: () -> Unit,
    onStopRecord: () -> Unit,
    dialectLabel: String,
) {
    var recording by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "按住講$dialectLabel", style = MaterialTheme.typography.bodyMedium)
        FilledIconButton(
            onClick = { /* 按住讲话；点击本身不触发动作 */ },
            modifier = Modifier
                .size(52.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        recording = true
                        onStartRecord()
                        try {
                            waitForUpOrCancellation()
                        } finally {
                            recording = false
                            onStopRecord()
                        }
                    }
                },
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (recording) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            ),
        ) {
            Icon(imageVector = MicIcon, contentDescription = "錄音")
        }
    }
}

/**
 * 麦克风图标（Material Design "mic" 24dp 路径）。
 * 不引入 material-icons-extended 依赖，避免为单个图标增加约 30MB 的构建产物。
 */
private val MicIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Mic",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 14f)
            curveToRelative(1.66f, 0f, 2.99f, -1.34f, 2.99f, -3f)
            lineTo(15f, 5f)
            curveToRelative(0f, -1.66f, -1.34f, -3f, -3f, -3f)
            reflectiveCurveTo(9f, 3.34f, 9f, 5f)
            verticalLineToRelative(6f)
            curveToRelative(0f, 1.66f, 1.34f, 3f, 3f, 3f)
            close()
            moveTo(17.3f, 11f)
            curveToRelative(0f, 3f, -2.54f, 5.1f, -5.3f, 5.1f)
            reflectiveCurveTo(6.7f, 14f, 6.7f, 11f)
            horizontalLineTo(5f)
            curveToRelative(0f, 3.41f, 2.72f, 6.23f, 6f, 6.72f)
            verticalLineTo(21f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(-3.28f)
            curveToRelative(3.28f, -0.49f, 6f, -3.31f, 6f, -6.72f)
            horizontalLineToRelative(-1.7f)
            close()
        }
    }.build()
}

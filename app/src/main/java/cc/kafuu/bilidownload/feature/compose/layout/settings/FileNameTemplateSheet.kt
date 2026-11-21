package cc.kafuu.bilidownload.feature.compose.layout.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.constant.DownloadResourceType
import cc.kafuu.bilidownload.common.utils.DownloadFileNameUtils
import cc.kafuu.bilidownload.common.utils.ExportFileNameUtils
import cc.kafuu.bilidownload.feature.compose.views.settings.SettingsBottomSheetFrame
import cc.kafuu.bilidownload.feature.compose.views.settings.SettingsMicroBadge
import cc.kafuu.bilidownload.feature.compose.views.settings.SettingsOutlinedInput
import cc.kafuu.bilidownload.feature.compose.views.settings.SettingsSheetHeader
import cc.kafuu.bilidownload.feature.compose.views.settings.SettingsTemplateTokenChip

private val PreviewCardShape = RoundedCornerShape(12.dp)

/**
 * 标记当前正在编辑的命名模板分类，用于承接快捷变量插入目标。
 */
private enum class FileNameTemplateTarget {
    MIXED,
    VIDEO,
    AUDIO,
}

/**
 * 下载/导出文件命名规则底部编辑器，集成实时渲染预览、变量快捷插入与一键恢复默认。
 *
 * @param title 编辑器标题。
 * @param description 底部规则说明文案。
 * @param hint 输入框空态占位提示。
 * @param tokenSeparator 点击插入变量时默认使用的连接符。
 * @param isExportMode true 表示按导出规则渲染预览，false 表示按下载资源规则渲染预览。
 * @param audioTemplate 当前音频命名模板。
 * @param videoTemplate 当前视频命名模板。
 * @param mixedTemplate 当前合成文件命名模板。
 * @param onAudioChange 音频模板变更回调。
 * @param onVideoChange 视频模板变更回调。
 * @param onMixedChange 合成模板变更回调。
 * @param onReset 恢复默认模板回调。
 * @param onDismiss 关闭面板回调。
 */
@Composable
internal fun FileNameTemplateSheet(
    title: String,
    description: String,
    hint: String,
    tokenSeparator: String,
    isExportMode: Boolean,
    audioTemplate: String,
    videoTemplate: String,
    mixedTemplate: String,
    onAudioChange: (String) -> Unit,
    onVideoChange: (String) -> Unit,
    onMixedChange: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var activeTarget by remember { mutableStateOf(FileNameTemplateTarget.MIXED) }
    val sampleContext = DownloadFileNameUtils.TemplateContext(
        videoName = stringResource(id = R.string.settings_file_name_preview_sample_video),
        partName = stringResource(id = R.string.settings_file_name_preview_sample_part),
    )

    // 点击变量芯片时将占位符追加至最近聚焦的分类输入框
    val onInsertToken: (String) -> Unit = { token ->
        when (activeTarget) {
            FileNameTemplateTarget.MIXED -> onMixedChange(
                appendFileNameTemplateToken(
                    current = mixedTemplate,
                    token = token,
                    separator = tokenSeparator,
                ),
            )

            FileNameTemplateTarget.VIDEO -> onVideoChange(
                appendFileNameTemplateToken(
                    current = videoTemplate,
                    token = token,
                    separator = tokenSeparator,
                ),
            )

            FileNameTemplateTarget.AUDIO -> onAudioChange(
                appendFileNameTemplateToken(
                    current = audioTemplate,
                    token = token,
                    separator = tokenSeparator,
                ),
            )
        }
    }

    // 按预览、变量芯片与三类模板输入框的顺序组织底部编辑面板
    SettingsBottomSheetFrame(onDismiss = onDismiss) {
        SettingsSheetHeader(
            title = title,
            onDismiss = onDismiss,
            actionText = stringResource(id = R.string.settings_file_name_reset_default),
            onActionClick = onReset,
        )
        Spacer(modifier = Modifier.height(14.dp))
        FileNameLivePreviewCard(
            sampleContext = sampleContext,
            isExportMode = isExportMode,
            audioTemplate = audioTemplate,
            videoTemplate = videoTemplate,
            mixedTemplate = mixedTemplate,
        )
        Spacer(modifier = Modifier.height(14.dp))
        TemplateTokenBar(onInsertToken = onInsertToken)
        Spacer(modifier = Modifier.height(14.dp))
        FileNameTemplateInputs(
            hint = hint,
            audioTemplate = audioTemplate,
            videoTemplate = videoTemplate,
            mixedTemplate = mixedTemplate,
            onAudioChange = onAudioChange,
            onVideoChange = onVideoChange,
            onMixedChange = onMixedChange,
            onFocusTargetChange = { target -> activeTarget = target },
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TemplateTokenBar(
    onInsertToken: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 说明变量会进入当前分类，避免将快捷按钮误认为独立模板设置。
        Text(
            text = stringResource(id = R.string.settings_file_name_insert_token_hint),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        // 两种变量共用插入回调，由编辑器根据最近聚焦字段决定目标。
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsTemplateTokenChip(
                token = DownloadFileNameUtils.VIDEO_NAME_TOKEN,
                onClick = { onInsertToken(DownloadFileNameUtils.VIDEO_NAME_TOKEN) },
            )
            SettingsTemplateTokenChip(
                token = DownloadFileNameUtils.PART_NAME_TOKEN,
                onClick = { onInsertToken(DownloadFileNameUtils.PART_NAME_TOKEN) },
            )
        }
    }
}

@Composable
private fun FileNameTemplateInputs(
    hint: String,
    audioTemplate: String,
    videoTemplate: String,
    mixedTemplate: String,
    onAudioChange: (String) -> Unit,
    onVideoChange: (String) -> Unit,
    onMixedChange: (String) -> Unit,
    onFocusTargetChange: (FileNameTemplateTarget) -> Unit,
) {
    val focusManager = LocalFocusManager.current

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 合成模板置顶，首次使用快捷变量时与编辑器的默认目标一致。
        SettingsOutlinedInput(
            value = mixedTemplate,
            label = stringResource(id = R.string.settings_resource_file_name_mixed),
            placeholder = hint,
            imeAction = ImeAction.Next,
            onValueChange = onMixedChange,
            modifier = Modifier.onFocusChanged { focusState ->
                if (focusState.isFocused) onFocusTargetChange(FileNameTemplateTarget.MIXED)
            },
        )
        // 单独流的模板各自更新焦点目标，快捷变量只修改用户正在编辑的分类。
        SettingsOutlinedInput(
            value = videoTemplate,
            label = stringResource(id = R.string.settings_resource_file_name_video),
            placeholder = hint,
            imeAction = ImeAction.Next,
            onValueChange = onVideoChange,
            modifier = Modifier.onFocusChanged { focusState ->
                if (focusState.isFocused) onFocusTargetChange(FileNameTemplateTarget.VIDEO)
            },
        )
        SettingsOutlinedInput(
            value = audioTemplate,
            label = stringResource(id = R.string.settings_resource_file_name_audio),
            placeholder = hint,
            imeAction = ImeAction.Done,
            onImeAction = { focusManager.clearFocus() },
            onValueChange = onAudioChange,
            modifier = Modifier.onFocusChanged { focusState ->
                if (focusState.isFocused) onFocusTargetChange(FileNameTemplateTarget.AUDIO)
            },
        )
    }
}

@Composable
private fun FileNameLivePreviewCard(
    sampleContext: DownloadFileNameUtils.TemplateContext,
    isExportMode: Boolean,
    audioTemplate: String,
    videoTemplate: String,
    mixedTemplate: String,
) {
    // 复用正式命名工具；下载模板无法确定名称时，保留空结果供界面说明任务回退规则。
    val exportTemplates = ExportFileNameUtils.Templates(
        audio = audioTemplate,
        video = videoTemplate,
        mixed = mixedTemplate,
    )
    val previewEntries = listOf(
        R.string.settings_file_name_preview_tag_mixed to renderPreviewFileName(
            isExportMode = isExportMode,
            resourceType = DownloadResourceType.MIXED,
            template = mixedTemplate,
            extension = "mp4",
            sampleContext = sampleContext,
            exportTemplates = exportTemplates,
        ),
        R.string.settings_file_name_preview_tag_video to renderPreviewFileName(
            isExportMode = isExportMode,
            resourceType = DownloadResourceType.VIDEO,
            template = videoTemplate,
            extension = "mp4",
            sampleContext = sampleContext,
            exportTemplates = exportTemplates,
        ),
        R.string.settings_file_name_preview_tag_audio to renderPreviewFileName(
            isExportMode = isExportMode,
            resourceType = DownloadResourceType.AUDIO,
            template = audioTemplate,
            extension = "m4a",
            sampleContext = sampleContext,
            exportTemplates = exportTemplates,
        ),
    )

    // 以等宽字体分区展示合成、视频与音频的示例输出
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = PreviewCardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(id = R.string.settings_file_name_preview_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SettingsMicroBadge(
                    text = stringResource(id = R.string.settings_file_name_preview_auto_ext),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            previewEntries.forEach { (tagRes, fileName) ->
                PreviewFileNameRow(tag = stringResource(id = tagRes), fileName = fileName)
            }
        }
    }
}

@Composable
private fun PreviewFileNameRow(
    tag: String,
    fileName: String?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 分类徽章保留自然宽度，文件名使用剩余空间。
        SettingsMicroBadge(
            text = tag,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // 任务相关名称以说明文案代替虚构的默认文件名。
        Text(
            text = fileName
                ?: stringResource(id = R.string.settings_file_name_preview_task_generated),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

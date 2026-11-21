package cc.kafuu.bilidownload.feature.compose.layout.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.model.BatchQualityMismatchMode
import cc.kafuu.bilidownload.common.model.DownloadPathMode
import cc.kafuu.bilidownload.common.model.DownloadSourceMode
import cc.kafuu.bilidownload.common.model.DownloadSourcePreset
import cc.kafuu.bilidownload.feature.compose.viewmodel.settings.SettingsUiIntent
import cc.kafuu.bilidownload.feature.compose.viewmodel.settings.SettingsUiState
import cc.kafuu.bilidownload.feature.compose.views.settings.SettingsInsetDivider
import cc.kafuu.bilidownload.feature.compose.views.settings.SettingsNavigationItem
import cc.kafuu.bilidownload.feature.compose.views.settings.SettingsPathPill
import cc.kafuu.bilidownload.feature.compose.views.settings.SettingsSection
import cc.kafuu.bilidownload.feature.compose.views.settings.SettingsSwitchItem

/**
 * 存储与命名设置分组，根据已生效的存储模式启用模板入口。
 *
 * @param state 当前设置快照，用于展示路径与三类模板摘要。
 * @param onOpenPathSheet 打开路径选择面板。
 * @param onOpenResourceFileNameSheet 打开下载命名面板。
 * @param onOpenExportFileNameSheet 打开导出命名面板。
 */
@Composable
internal fun StorageAndNamingSection(
    state: SettingsUiState.Normal,
    onOpenPathSheet: () -> Unit,
    onOpenResourceFileNameSheet: () -> Unit,
    onOpenExportFileNameSheet: () -> Unit,
) {
    // 仅外部存储与外部媒体支持自定义下载文件名，内部存储时置灰并说明原因
    val isResourceNamingAvailable = state.downloadPathMode != DownloadPathMode.INTERNAL
    val pathModeTitle = downloadPathModeLabel(mode = state.downloadPathMode)
    val resourceNamingSubtitle = if (isResourceNamingAvailable) {
        stringResource(
            id = fileNameTemplateSummaryRes(
                audioTemplate = state.audioResourceFileNameTemplate,
                videoTemplate = state.videoResourceFileNameTemplate,
                mixedTemplate = state.mixedResourceFileNameTemplate,
                isExportMode = false,
            ),
        )
    } else {
        stringResource(id = R.string.settings_resource_file_name_unavailable)
    }
    val exportNamingSubtitle = stringResource(
        id = fileNameTemplateSummaryRes(
            audioTemplate = state.audioExportFileNameTemplate,
            videoTemplate = state.videoExportFileNameTemplate,
            mixedTemplate = state.mixedExportFileNameTemplate,
            isExportMode = true,
        ),
    )

    // 将当前存储路径概览与两套命名规则收纳在同一分组内
    SettingsSection(title = stringResource(id = R.string.settings_section_storage_and_naming)) {
        SettingsNavigationItem(
            iconRes = R.drawable.ic_settings_folder,
            title = stringResource(id = R.string.settings_download_path),
            trailingValue = pathModeTitle,
            highlighted = true,
            onClick = onOpenPathSheet,
            extraContent = {
                SettingsPathPill(
                    label = stringResource(id = R.string.settings_download_path_directory_label),
                    path = state.currentPathDisplay,
                )
            },
        )

        SettingsInsetDivider()

        SettingsNavigationItem(
            iconRes = R.drawable.ic_settings_rename,
            title = stringResource(id = R.string.settings_resource_file_name),
            subtitle = resourceNamingSubtitle,
            enabled = isResourceNamingAvailable,
            onClick = onOpenResourceFileNameSheet,
        )

        SettingsInsetDivider()

        SettingsNavigationItem(
            iconRes = R.drawable.ic_settings_export,
            title = stringResource(id = R.string.settings_export_file_name),
            subtitle = exportNamingSubtitle,
            onClick = onOpenExportFileNameSheet,
        )
    }
}

/**
 * 展示下载源与批量画质策略摘要，并回传对应面板的打开操作。
 *
 * @param state 当前设置快照。
 * @param onOpenSourceSheet 打开下载源面板。
 * @param onOpenBatchQualitySheet 打开批量画质策略面板。
 */
@Composable
internal fun DownloadAndNetworkSection(
    state: SettingsUiState.Normal,
    onOpenSourceSheet: () -> Unit,
    onOpenBatchQualitySheet: () -> Unit,
) {
    // 先生成当前策略摘要，条目只负责展示并打开对应配置面板。
    val sourceSummary = downloadSourceSummary(
        mode = state.downloadSourceMode,
        customHost = state.downloadSourceCustomHost,
    )
    val batchModeTitle = batchQualityModeLabel(mode = state.batchQualityMismatchMode)
    val batchModeDesc = batchQualityModeDescription(mode = state.batchQualityMismatchMode)

    // 汇总当前下载源与批量画质策略的紧凑摘要
    SettingsSection(title = stringResource(id = R.string.settings_section_download_and_network)) {
        SettingsNavigationItem(
            iconRes = R.drawable.ic_settings_source,
            title = stringResource(id = R.string.settings_download_source),
            subtitle = sourceSummary,
            highlighted = state.downloadSourceMode != DownloadSourceMode.DEFAULT,
            onClick = onOpenSourceSheet,
        )

        SettingsInsetDivider()

        SettingsNavigationItem(
            iconRes = R.drawable.ic_settings_quality,
            title = stringResource(id = R.string.settings_batch_quality_mismatch),
            subtitle = batchModeDesc,
            trailingValue = batchModeTitle,
            onClick = onOpenBatchQualitySheet,
        )
    }
}

/**
 * 展示源文件清理与音频转封装策略，将配置变更交给页面意图通道。
 *
 * @param state 当前后处理配置快照。
 * @param onIntent 发送包含目标开关值的设置意图。
 */
@Composable
internal fun PostProcessingSection(
    state: SettingsUiState.Normal,
    onIntent: (SettingsUiIntent) -> Unit,
) {
    SettingsSection(title = stringResource(id = R.string.settings_section_post_processing)) {
        // 清理策略只保存配置，当前页面不直接删除任何下载资源。
        SettingsSwitchItem(
            iconRes = R.drawable.ic_action_delete,
            title = stringResource(id = R.string.settings_delete_source_files_after_merge),
            description = stringResource(id = R.string.settings_delete_source_files_after_merge_desc),
            checked = state.deleteSourceFilesAfterMerge,
            onCheckedChange = { enabled ->
                onIntent(SettingsUiIntent.SetDeleteSourceFilesAfterMerge(enabled = enabled))
            },
        )

        SettingsInsetDivider()

        // 转封装开关独立于清理策略，分别向 ViewModel 发送明确的目标值。
        SettingsSwitchItem(
            iconRes = R.drawable.ic_music,
            title = stringResource(id = R.string.settings_auto_remux_audio_after_download),
            description = stringResource(id = R.string.settings_auto_remux_audio_after_download_desc),
            checked = state.autoRemuxAudioAfterDownload,
            onCheckedChange = { enabled ->
                onIntent(SettingsUiIntent.SetAutoRemuxAudioAfterDownload(enabled = enabled))
            },
        )
    }
}

/**
 * 根据设备画中画能力展示播放配置与不可用原因。
 *
 * @param state 当前播放配置及设备能力快照。
 * @param onIntent 回传用户设置的播放策略。
 */
@Composable
internal fun PlaybackSection(
    state: SettingsUiState.Normal,
    onIntent: (SettingsUiIntent) -> Unit,
) {
    val descriptionRes = if (state.isPictureInPictureSupported) {
        R.string.settings_picture_in_picture_playback_desc
    } else {
        R.string.settings_picture_in_picture_playback_unsupported_desc
    }
    val unsupportedBadge = if (state.isPictureInPictureSupported) {
        null
    } else {
        stringResource(id = R.string.settings_picture_in_picture_unsupported_badge)
    }

    // 不支持画中画的设备显示状态徽章并禁用切换
    SettingsSection(title = stringResource(id = R.string.settings_section_playback)) {
        SettingsSwitchItem(
            iconRes = R.drawable.ic_settings_pip,
            title = stringResource(id = R.string.settings_picture_in_picture_playback),
            description = stringResource(id = descriptionRes),
            checked = state.pictureInPicturePlaybackEnabled,
            enabled = state.isPictureInPictureSupported,
            statusBadge = unsupportedBadge,
            onCheckedChange = { enabled ->
                onIntent(SettingsUiIntent.SetPictureInPicturePlaybackEnabled(enabled = enabled))
            },
        )
    }
}

@Composable
private fun downloadPathModeLabel(mode: DownloadPathMode): String = when (mode) {
    DownloadPathMode.INTERNAL -> stringResource(id = R.string.settings_download_path_internal)
    DownloadPathMode.EXTERNAL -> stringResource(id = R.string.settings_download_path_external)
    DownloadPathMode.EXTERNAL_MEDIA -> {
        stringResource(id = R.string.settings_download_path_external_media)
    }
}

@Composable
private fun downloadSourceSummary(
    mode: DownloadSourceMode,
    customHost: String,
): String = when (mode) {
    DownloadSourceMode.DEFAULT -> stringResource(id = R.string.settings_download_source_default)
    DownloadSourceMode.AUTO_PROBE -> {
        stringResource(id = R.string.settings_download_source_auto_probe)
    }

    DownloadSourceMode.CUSTOM_HOST -> {
        // 优先匹配预设节点的语义简称，未命中时展示用户输入的原域名
        val matchedPreset = DownloadSourcePreset.entries.firstOrNull { preset ->
            preset.host == customHost
        }
        when {
            matchedPreset != null -> stringResource(
                id = R.string.settings_download_source_custom_summary,
                stringResource(id = downloadSourcePresetLabelRes(preset = matchedPreset)),
            )

            customHost.isNotBlank() -> stringResource(
                id = R.string.settings_download_source_custom_summary,
                customHost,
            )

            else -> stringResource(id = R.string.settings_download_source_custom_empty_summary)
        }
    }
}

@Composable
private fun batchQualityModeLabel(mode: BatchQualityMismatchMode): String = when (mode) {
    BatchQualityMismatchMode.AUTO_FALLBACK -> {
        stringResource(id = R.string.settings_batch_quality_mismatch_auto)
    }

    BatchQualityMismatchMode.ASK -> {
        stringResource(id = R.string.settings_batch_quality_mismatch_ask)
    }

    BatchQualityMismatchMode.SKIP -> {
        stringResource(id = R.string.settings_batch_quality_mismatch_skip)
    }
}

@Composable
private fun batchQualityModeDescription(mode: BatchQualityMismatchMode): String = when (mode) {
    BatchQualityMismatchMode.AUTO_FALLBACK -> {
        stringResource(id = R.string.settings_batch_quality_mismatch_auto_desc)
    }

    BatchQualityMismatchMode.ASK -> {
        stringResource(id = R.string.settings_batch_quality_mismatch_ask_desc)
    }

    BatchQualityMismatchMode.SKIP -> {
        stringResource(id = R.string.settings_batch_quality_mismatch_skip_desc)
    }
}

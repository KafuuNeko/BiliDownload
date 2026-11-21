package cc.kafuu.bilidownload.feature.compose.layout.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.model.DownloadSourceMode
import cc.kafuu.bilidownload.common.model.DownloadSourcePreset
import cc.kafuu.bilidownload.feature.compose.viewmodel.settings.SettingsUiIntent
import cc.kafuu.bilidownload.feature.compose.viewmodel.settings.SettingsUiState
import cc.kafuu.bilidownload.feature.compose.views.settings.SettingsBottomSheetFrame
import cc.kafuu.bilidownload.feature.compose.views.settings.SettingsOutlinedInput
import cc.kafuu.bilidownload.feature.compose.views.settings.SettingsPresetNodeChip
import cc.kafuu.bilidownload.feature.compose.views.settings.SettingsSelectableOptionCard
import cc.kafuu.bilidownload.feature.compose.views.settings.SettingsSheetHeader

/**
 * 下载源与自定义节点配置底部面板，提供模式切换、Host 输入及语义化预设节点标签。
 *
 * @param state 当前设置页面状态。
 * @param onIntent 发送下载源与自定义 Host 变更意图的回调。
 * @param onDismiss 关闭面板回调。
 */
@Composable
internal fun DownloadSourceSheet(
    state: SettingsUiState.Normal,
    onIntent: (SettingsUiIntent) -> Unit,
    onDismiss: () -> Unit,
) {
    val focusManager = LocalFocusManager.current

    SettingsBottomSheetFrame(onDismiss = onDismiss) {
        SettingsSheetHeader(
            title = stringResource(id = R.string.settings_download_source),
            onDismiss = onDismiss,
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 模式单选区
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SettingsSelectableOptionCard(
                title = stringResource(id = R.string.settings_download_source_default),
                description = stringResource(id = R.string.settings_download_source_default_desc),
                isSelected = state.downloadSourceMode == DownloadSourceMode.DEFAULT,
                onClick = {
                    onIntent(SettingsUiIntent.SetDownloadSourceMode(mode = DownloadSourceMode.DEFAULT))
                },
            )
            SettingsSelectableOptionCard(
                title = stringResource(id = R.string.settings_download_source_auto_probe),
                description = stringResource(id = R.string.settings_download_source_auto_probe_desc),
                badge = stringResource(id = R.string.settings_download_source_auto_probe_badge),
                isSelected = state.downloadSourceMode == DownloadSourceMode.AUTO_PROBE,
                onClick = {
                    onIntent(
                        SettingsUiIntent.SetDownloadSourceMode(mode = DownloadSourceMode.AUTO_PROBE),
                    )
                },
            )
            SettingsSelectableOptionCard(
                title = stringResource(id = R.string.settings_download_source_custom_host),
                description = stringResource(id = R.string.settings_download_source_custom_host_desc),
                isSelected = state.downloadSourceMode == DownloadSourceMode.CUSTOM_HOST,
                onClick = {
                    onIntent(
                        SettingsUiIntent.SetDownloadSourceMode(mode = DownloadSourceMode.CUSTOM_HOST),
                    )
                },
            )
        }

        // 选择自定义 Host 后平滑展开域名输入与预设节点标签云
        AnimatedVisibility(
            visible = state.downloadSourceMode == DownloadSourceMode.CUSTOM_HOST,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            CustomDownloadHostSection(
                customHost = state.downloadSourceCustomHost,
                onCustomHostChange = { host ->
                    onIntent(SettingsUiIntent.SetDownloadSourceCustomHost(host = host))
                },
                onClearFocus = { focusManager.clearFocus() },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomDownloadHostSection(
    customHost: String,
    onCustomHostChange: (String) -> Unit,
    onClearFocus: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Spacer(modifier = Modifier.height(14.dp))

        // 手动输入域名或点击下方语义化节点一键回填
        SettingsOutlinedInput(
            value = customHost,
            label = stringResource(id = R.string.settings_download_source_custom_host_label),
            placeholder = stringResource(id = R.string.settings_download_source_custom_host_hint),
            imeAction = ImeAction.Done,
            onImeAction = onClearFocus,
            onValueChange = onCustomHostChange,
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 预设节点仅回填同一个 Host 字段，与手动输入共用持久化入口。
        Text(
            text = stringResource(id = R.string.settings_download_source_preset_hosts),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(10.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DownloadSourcePreset.entries.forEach { preset ->
                SettingsPresetNodeChip(
                    label = stringResource(id = downloadSourcePresetLabelRes(preset = preset)),
                    isSelected = customHost == preset.host,
                    onClick = { onCustomHostChange(preset.host) },
                )
            }
        }
    }
}

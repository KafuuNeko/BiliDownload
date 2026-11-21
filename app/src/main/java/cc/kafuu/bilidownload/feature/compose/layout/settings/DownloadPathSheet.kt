package cc.kafuu.bilidownload.feature.compose.layout.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.model.DownloadPathMode
import cc.kafuu.bilidownload.feature.compose.viewmodel.settings.SettingsUiIntent
import cc.kafuu.bilidownload.feature.compose.viewmodel.settings.SettingsUiState
import cc.kafuu.bilidownload.feature.compose.views.settings.SettingsBottomSheetFrame
import cc.kafuu.bilidownload.feature.compose.views.settings.SettingsSelectableOptionCard
import cc.kafuu.bilidownload.feature.compose.views.settings.SettingsSheetHeader

private val PreviewCardShape = RoundedCornerShape(12.dp)

/**
 * 下载路径选择底部面板，展示三种存储模式与实时生效路径。
 *
 * @param state 当前设置页面状态。
 * @param onIntent 发送设置变更意图的回调。
 * @param onDismiss 关闭面板回调。
 */
@Composable
internal fun DownloadPathSheet(
    state: SettingsUiState.Normal,
    onIntent: (SettingsUiIntent) -> Unit,
    onDismiss: () -> Unit,
) {
    SettingsBottomSheetFrame(onDismiss = onDismiss) {
        SettingsSheetHeader(
            title = stringResource(id = R.string.settings_download_path),
            onDismiss = onDismiss,
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 保持面板打开以便权限授予后即时反馈新的保存路径预览
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SettingsSelectableOptionCard(
                title = stringResource(id = R.string.settings_download_path_internal),
                description = stringResource(id = R.string.settings_download_path_internal_desc),
                isSelected = state.downloadPathMode == DownloadPathMode.INTERNAL,
                onClick = {
                    onIntent(SettingsUiIntent.SetDownloadPathMode(mode = DownloadPathMode.INTERNAL))
                },
            )
            SettingsSelectableOptionCard(
                title = stringResource(id = R.string.settings_download_path_external),
                description = stringResource(id = R.string.settings_download_path_external_desc),
                isSelected = state.downloadPathMode == DownloadPathMode.EXTERNAL,
                onClick = {
                    onIntent(SettingsUiIntent.SetDownloadPathMode(mode = DownloadPathMode.EXTERNAL))
                },
            )
            SettingsSelectableOptionCard(
                title = stringResource(id = R.string.settings_download_path_external_media),
                description = stringResource(id = R.string.settings_download_path_external_media_desc),
                isSelected = state.downloadPathMode == DownloadPathMode.EXTERNAL_MEDIA,
                onClick = {
                    onIntent(
                        SettingsUiIntent.SetDownloadPathMode(mode = DownloadPathMode.EXTERNAL_MEDIA),
                    )
                },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 底部常驻实时保存目录，便于切换后立即核对落盘路径
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = PreviewCardShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Text(
                    text = stringResource(id = R.string.settings_download_path_current_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = state.currentPathDisplay,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

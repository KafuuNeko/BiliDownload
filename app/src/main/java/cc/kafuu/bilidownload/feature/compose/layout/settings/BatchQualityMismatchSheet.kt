package cc.kafuu.bilidownload.feature.compose.layout.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.model.BatchQualityMismatchMode
import cc.kafuu.bilidownload.feature.compose.viewmodel.settings.SettingsUiIntent
import cc.kafuu.bilidownload.feature.compose.viewmodel.settings.SettingsUiState
import cc.kafuu.bilidownload.feature.compose.views.settings.SettingsBottomSheetFrame
import cc.kafuu.bilidownload.feature.compose.views.settings.SettingsSelectableOptionCard
import cc.kafuu.bilidownload.feature.compose.views.settings.SettingsSheetHeader

/**
 * 批量下载画质不一致策略底部面板。
 *
 * @param state 当前设置页面状态。
 * @param onIntent 发送策略变更意图的回调。
 * @param onDismiss 关闭面板回调。
 */
@Composable
internal fun BatchQualityMismatchSheet(
    state: SettingsUiState.Normal,
    onIntent: (SettingsUiIntent) -> Unit,
    onDismiss: () -> Unit,
) {
    SettingsBottomSheetFrame(onDismiss = onDismiss) {
        // 标题和关闭入口保持统一，取消面板时不改变当前策略。
        SettingsSheetHeader(
            title = stringResource(id = R.string.settings_batch_quality_mismatch),
            onDismiss = onDismiss,
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 选中策略后立即持久化并收起面板
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SettingsSelectableOptionCard(
                title = stringResource(id = R.string.settings_batch_quality_mismatch_auto),
                description = stringResource(id = R.string.settings_batch_quality_mismatch_auto_desc),
                isSelected = state.batchQualityMismatchMode == BatchQualityMismatchMode.AUTO_FALLBACK,
                onClick = {
                    onIntent(
                        SettingsUiIntent.SetBatchQualityMismatchMode(
                            mode = BatchQualityMismatchMode.AUTO_FALLBACK,
                        ),
                    )
                    onDismiss()
                },
            )
            SettingsSelectableOptionCard(
                title = stringResource(id = R.string.settings_batch_quality_mismatch_ask),
                description = stringResource(id = R.string.settings_batch_quality_mismatch_ask_desc),
                isSelected = state.batchQualityMismatchMode == BatchQualityMismatchMode.ASK,
                onClick = {
                    onIntent(
                        SettingsUiIntent.SetBatchQualityMismatchMode(
                            mode = BatchQualityMismatchMode.ASK,
                        ),
                    )
                    onDismiss()
                },
            )
            SettingsSelectableOptionCard(
                title = stringResource(id = R.string.settings_batch_quality_mismatch_skip),
                description = stringResource(id = R.string.settings_batch_quality_mismatch_skip_desc),
                isSelected = state.batchQualityMismatchMode == BatchQualityMismatchMode.SKIP,
                onClick = {
                    onIntent(
                        SettingsUiIntent.SetBatchQualityMismatchMode(
                            mode = BatchQualityMismatchMode.SKIP,
                        ),
                    )
                    onDismiss()
                },
            )
        }
    }
}

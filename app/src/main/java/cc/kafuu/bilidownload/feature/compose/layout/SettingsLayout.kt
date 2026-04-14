package cc.kafuu.bilidownload.feature.compose.layout

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.model.AppModel
import cc.kafuu.bilidownload.feature.compose.viewmodel.settings.SettingsUiIntent
import cc.kafuu.bilidownload.feature.compose.viewmodel.settings.SettingsUiState
import cc.kafuu.bilidownload.feature.compose.views.AppTopBar

@Composable
fun SettingsLayout(
    state: SettingsUiState,
    onIntent: (SettingsUiIntent) -> Unit
) {
    when (state) {
        SettingsUiState.Loading -> LoadingView()
        is SettingsUiState.Normal -> SettingsContent(state, onIntent)
    }
}

@Composable
private fun LoadingView() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.general_window_background_color)),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun SettingsContent(
    state: SettingsUiState.Normal,
    onIntent: (SettingsUiIntent) -> Unit
) {
    Column(
        modifier = Modifier
            .statusBarsPadding()
            .fillMaxSize()
            .background(colorResource(R.color.general_window_background_color))
    ) {
        AppTopBar(title = stringResource(R.string.text_settings)) {
            onIntent(SettingsUiIntent.GoBack)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // 下载路径设置卡片
            DownloadPathCard(state, onIntent)

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DownloadPathCard(
    state: SettingsUiState.Normal,
    onIntent: (SettingsUiIntent) -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // 标题
            Text(
                text = stringResource(R.string.settings_download_path),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
            )

            HorizontalDivider(
                color = colorResource(R.color.view_split_color),
                thickness = 0.5.dp
            )

            // 内部存储选项
            DownloadPathOption(
                title = stringResource(R.string.settings_download_path_internal),
                description = stringResource(R.string.settings_download_path_internal_desc),
                isSelected = state.downloadPathMode == AppModel.DOWNLOAD_PATH_INTERNAL,
                onClick = { onIntent(SettingsUiIntent.SetDownloadPathMode(AppModel.DOWNLOAD_PATH_INTERNAL)) }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = colorResource(R.color.view_split_color),
                thickness = 0.5.dp
            )

            // 外部存储选项
            DownloadPathOption(
                title = stringResource(R.string.settings_download_path_external),
                description = stringResource(R.string.settings_download_path_external_desc),
                isSelected = state.downloadPathMode == AppModel.DOWNLOAD_PATH_EXTERNAL,
                onClick = { onIntent(SettingsUiIntent.SetDownloadPathMode(AppModel.DOWNLOAD_PATH_EXTERNAL)) }
            )

            HorizontalDivider(
                color = colorResource(R.color.view_split_color),
                thickness = 0.5.dp
            )

            // 当前路径显示
            Text(
                text = stringResource(R.string.settings_download_path_current, state.currentPathDisplay),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun DownloadPathOption(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val indicatorColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
        animationSpec = tween(durationMillis = 250),
        label = "indicatorColor"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 自定义选中指示器
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(
                    color = indicatorColor.copy(alpha = 0.15f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        )
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

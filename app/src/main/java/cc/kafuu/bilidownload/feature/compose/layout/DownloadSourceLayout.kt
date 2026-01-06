package cc.kafuu.bilidownload.feature.compose.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.manager.DownloadSourceConfig
import cc.kafuu.bilidownload.feature.compose.viewmodel.downloadsource.DownloadSourceUiIntent
import cc.kafuu.bilidownload.feature.compose.viewmodel.downloadsource.DownloadSourceUiState
import cc.kafuu.bilidownload.feature.compose.views.AppTopBar

@Composable
fun DownloadSourceLayout(
    state: DownloadSourceUiState,
    onEvent: (DownloadSourceUiIntent) -> Unit
) {
    when (state) {
        DownloadSourceUiState.None -> Unit
        is DownloadSourceUiState.Normal -> Normal(state, onEvent)
        DownloadSourceUiState.Finished -> Unit
    }
}

@Composable
private fun Normal(
    state: DownloadSourceUiState.Normal,
    onEvent: (DownloadSourceUiIntent) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.general_window_background_color))
            .statusBarsPadding()
    ) {
        AppTopBar(
            title = stringResource(R.string.download_source_settings),
            onBackClick = { onEvent(DownloadSourceUiIntent.TryBack) }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 16.dp)
        ) {
            // 说明卡片
            DescriptionCard()

            // 下载源列表
            SourceList(
                currentSource = state.currentSource,
                sourceList = state.sourceList,
                onSourceSelect = { source ->
                    onEvent(DownloadSourceUiIntent.SelectSource(source))
                }
            )
        }
    }
}

@Composable
private fun DescriptionCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(dimensionResource(R.dimen.card_item_corner_radius)),
        elevation = CardDefaults.cardElevation(
            defaultElevation = dimensionResource(R.dimen.card_item_elevation)
        ),
        colors = CardDefaults.cardColors(
            containerColor = colorResource(R.color.general_item_background_color)
        )
    ) {
        Text(
            text = stringResource(R.string.download_source_description),
            modifier = Modifier.padding(16.dp),
            color = colorResource(R.color.general_text_color),
            fontSize = 14.sp
        )
    }
}

@Composable
private fun SourceList(
    currentSource: DownloadSourceConfig.DownloadSource,
    sourceList: List<DownloadSourceConfig.DownloadSource>,
    onSourceSelect: (DownloadSourceConfig.DownloadSource) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(dimensionResource(R.dimen.card_item_corner_radius)),
        elevation = CardDefaults.cardElevation(
            defaultElevation = dimensionResource(R.dimen.card_item_elevation)
        ),
        colors = CardDefaults.cardColors(
            containerColor = colorResource(R.color.general_item_background_color)
        )
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            sourceList.forEach { source ->
                SourceItem(
                    source = source,
                    isSelected = source == currentSource,
                    onClick = { onSourceSelect(source) }
                )
                if (source != sourceList.last()) {
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SourceItem(
    source: DownloadSourceConfig.DownloadSource,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .background(
                if (isSelected) {
                    colorResource(R.color.primary_color).copy(alpha = 0.1f)
                } else {
                    colorResource(R.color.common_transparent)
                }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = source.displayName,
                color = colorResource(R.color.general_text_color),
                fontSize = 16.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                text = source.description,
                color = colorResource(R.color.general_text_color).copy(alpha = 0.6f),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        RadioButton(
            selected = isSelected,
            onClick = onClick
        )
    }
}

@Composable
private fun HorizontalDivider() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        androidx.compose.material3.HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = colorResource(R.color.view_split_color)
        )
    }
}

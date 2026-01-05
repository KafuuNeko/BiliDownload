package cc.kafuu.bilidownload.feature.compose.layout

import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.room.entity.ViewHistoryEntity
import cc.kafuu.bilidownload.common.utils.TimeUtils
import cc.kafuu.bilidownload.feature.compose.viewmodel.viewhistory.ViewHistoryUiIntent
import cc.kafuu.bilidownload.feature.compose.viewmodel.viewhistory.ViewHistoryUiState
import cc.kafuu.bilidownload.feature.compose.views.AppTopBar
import com.bumptech.glide.Glide

@Composable
fun ViewHistoryLayout(
    state: ViewHistoryUiState,
    onEvent: (ViewHistoryUiIntent) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.general_window_background_color))
    ) {
        when (state) {
            is ViewHistoryUiState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            is ViewHistoryUiState.Loaded -> {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    AppTopBar(
                        title = stringResource(R.string.view_history_title),
                        onBackClick = { onEvent(ViewHistoryUiIntent.TryBack) }
                    )
                    HistoryContent(
                        state = state,
                        onEvent = onEvent
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryContent(
    state: ViewHistoryUiState.Loaded,
    onEvent: (ViewHistoryUiIntent) -> Unit
) {
    if (state.historyList.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.view_history_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = colorResource(R.color.general_text_color)
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)
        ) {
            items(state.historyList) { history ->
                HistoryItem(
                    history = history,
                    onClick = { onEvent(ViewHistoryUiIntent.ClickHistoryItem(history.bvid)) },
                    onDelete = { onEvent(ViewHistoryUiIntent.DeleteHistoryItem(history.bvid)) }
                )
            }
        }
    }
}

@Composable
private fun HistoryItem(
    history: ViewHistoryEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = colorResource(R.color.general_item_background_color),
                shape = RoundedCornerShape(dimensionResource(R.dimen.card_item_corner_radius))
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 使用 AndroidView 加载图片
        AndroidView(
            factory = { context ->
                ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    clipToOutline = true
                }
            },
            modifier = Modifier
                .size(80.dp, 60.dp)
                .clip(RoundedCornerShape(8.dp)),
            update = { imageView ->
                Glide.with(imageView.context)
                    .load(history.cover)
                    .into(imageView)
            }
        )

        Spacer(Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = history.title,
                style = MaterialTheme.typography.bodyMedium,
                color = colorResource(R.color.general_text_color),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = history.author,
                style = MaterialTheme.typography.bodySmall,
                color = colorResource(R.color.general_text_color)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = TimeUtils.formatTimestamp(history.viewTime),
                style = MaterialTheme.typography.bodySmall,
                color = colorResource(R.color.general_text_color)
            )
        }

        Spacer(Modifier.width(8.dp))

        androidx.compose.material3.IconButton(
            onClick = onDelete,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_delete),
                contentDescription = stringResource(R.string.content_description_delete),
                tint = colorResource(R.color.general_text_color),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

package cc.kafuu.bilidownload.feature.compose.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cc.kafuu.bilidownload.common.core.compose.CoreCompActivity
import cc.kafuu.bilidownload.feature.compose.layout.ViewHistoryLayout
import cc.kafuu.bilidownload.feature.compose.viewmodel.viewhistory.ViewHistoryUiEvent
import cc.kafuu.bilidownload.feature.compose.viewmodel.viewhistory.ViewHistoryUiIntent
import cc.kafuu.bilidownload.feature.compose.viewmodel.viewhistory.ViewHistoryUiState
import cc.kafuu.bilidownload.feature.compose.viewmodel.viewhistory.ViewHistoryViewModel
import cc.kafuu.bilidownload.feature.viewbinding.view.activity.VideoDetailsActivity

class ViewHistoryActivity : CoreCompActivity() {
    private val mViewModel by viewModels<ViewHistoryViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()
        ViewHistoryLayout(uiState) { mViewModel.emit(it) }
        LaunchedEffect(Unit) {
            mViewModel.collectEvent { onViewEvent(it) }
        }
    }

    private fun onViewEvent(event: ViewHistoryUiEvent) {
        when (event) {
            is ViewHistoryUiEvent.NavigateToVideoDetail -> {
                navigateToVideoDetail(event.video)
            }
        }
    }

    private fun navigateToVideoDetail(video: cc.kafuu.bilidownload.common.model.bili.BiliVideoModel) {
        startActivity(Intent(this, VideoDetailsActivity::class.java).apply {
            putExtras(VideoDetailsActivity.buildIntent(video).extras ?: Bundle())
        })
    }
}

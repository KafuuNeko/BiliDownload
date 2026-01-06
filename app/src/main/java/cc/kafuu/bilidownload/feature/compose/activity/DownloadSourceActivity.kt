package cc.kafuu.bilidownload.feature.compose.activity

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cc.kafuu.bilidownload.common.core.compose.CoreCompActivity
import cc.kafuu.bilidownload.feature.compose.layout.DownloadSourceLayout
import cc.kafuu.bilidownload.feature.compose.viewmodel.downloadsource.DownloadSourceUiEvent
import cc.kafuu.bilidownload.feature.compose.viewmodel.downloadsource.DownloadSourceUiIntent
import cc.kafuu.bilidownload.feature.compose.viewmodel.downloadsource.DownloadSourceUiState
import cc.kafuu.bilidownload.feature.compose.viewmodel.downloadsource.DownloadSourceViewModel

class DownloadSourceActivity : CoreCompActivity() {

    private val mViewModel by viewModels<DownloadSourceViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mViewModel.emit(DownloadSourceUiIntent.Init)
    }

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()

        DownloadSourceLayout(
            state = uiState,
            onEvent = { intent -> mViewModel.emit(intent) }
        )

        LaunchedEffect(uiState) {
            if (uiState is DownloadSourceUiState.Finished) finish()
        }

        LaunchedEffect(Unit) {
            mViewModel.collectEvent { event ->
                onViewEvent(event)
            }
        }
    }

    private fun onViewEvent(event: DownloadSourceUiEvent) {
    }
}

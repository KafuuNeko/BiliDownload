package cc.kafuu.bilidownload.feature.compose.activity

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cc.kafuu.bilidownload.common.core.compose.CoreCompActivity
import cc.kafuu.bilidownload.feature.compose.layout.MediaPlayerLayout
import cc.kafuu.bilidownload.feature.compose.viewmodel.mediaplayer.MediaPlayerUiEvent
import cc.kafuu.bilidownload.feature.compose.viewmodel.mediaplayer.MediaPlayerUiIntent
import cc.kafuu.bilidownload.feature.compose.viewmodel.mediaplayer.MediaPlayerViewModel

class MediaPlayerActivity : CoreCompActivity() {
    companion object {
        private const val KEY_FILE_PATH = "file_path"
        private const val KEY_TITLE = "title"

        fun buildIntent(filePath: String, title: String) = Intent().apply {
            putExtra(KEY_FILE_PATH, filePath)
            putExtra(KEY_TITLE, title)
        }
    }

    private val mViewModel by viewModels<MediaPlayerViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val filePath = intent.getStringExtra(KEY_FILE_PATH) ?: run { finish(); return }
        val title = intent.getStringExtra(KEY_TITLE) ?: ""
        mViewModel.emit(MediaPlayerUiIntent.Init(applicationContext, filePath, title))
    }

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()

        MediaPlayerLayout(uiState) { mViewModel.emit(it) }

        LaunchedEffect(Unit) {
            mViewModel.collectEvent { event ->
                when (event) {
                    MediaPlayerUiEvent.Finish -> finish()
                    is MediaPlayerUiEvent.SetFullScreen -> onSetFullScreen(event.isFullScreen)
                }
            }
        }
    }

    private fun onSetFullScreen(isFullScreen: Boolean) {
        requestedOrientation = if (isFullScreen) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}

package cc.kafuu.bilidownload.feature.compose.activity

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.core.compose.CoreCompActivity
import cc.kafuu.bilidownload.common.utils.FileUtils
import cc.kafuu.bilidownload.feature.compose.layout.MediaPlayerLayout
import cc.kafuu.bilidownload.feature.compose.viewmodel.mediaplayer.MediaPlayerUiEvent
import cc.kafuu.bilidownload.feature.compose.viewmodel.mediaplayer.MediaPlayerUiIntent
import cc.kafuu.bilidownload.feature.compose.viewmodel.mediaplayer.MediaPlayerViewModel
import java.io.File

class MediaPlayerActivity : CoreCompActivity() {
    companion object {
        private const val KEY_FILE_PATH = "file_path"
        private const val KEY_TITLE = "title"
        private const val KEY_MIME_TYPE = "mime_type"

        fun buildIntent(filePath: String, title: String, mimeType: String = "video/*") = Intent().apply {
            putExtra(KEY_FILE_PATH, filePath)
            putExtra(KEY_TITLE, title)
            putExtra(KEY_MIME_TYPE, mimeType)
        }
    }

    private val mViewModel by viewModels<MediaPlayerViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val filePath = intent.getStringExtra(KEY_FILE_PATH) ?: run { finish(); return }
        val title = intent.getStringExtra(KEY_TITLE) ?: ""
        val mimeType = intent.getStringExtra(KEY_MIME_TYPE) ?: "video/*"
        mViewModel.emit(MediaPlayerUiIntent.Init(applicationContext, filePath, title, mimeType))
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
                    is MediaPlayerUiEvent.OpenWithOtherPlayer -> onOpenWithOtherPlayer(event)
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

    private fun onOpenWithOtherPlayer(event: MediaPlayerUiEvent.OpenWithOtherPlayer) {
        val opened = FileUtils.tryOpenFileWithOtherApp(
            context = this,
            title = event.title,
            file = File(event.filePath),
            mimetype = event.mimeType
        )
        if (!opened) {
            Toast.makeText(this, R.string.no_external_player_message, Toast.LENGTH_SHORT).show()
        }
    }
}

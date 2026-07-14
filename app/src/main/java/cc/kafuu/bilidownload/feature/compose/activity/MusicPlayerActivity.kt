package cc.kafuu.bilidownload.feature.compose.activity

import android.content.Intent
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
import cc.kafuu.bilidownload.feature.compose.layout.MusicPlayerLayout
import cc.kafuu.bilidownload.feature.compose.viewmodel.musicplayer.MusicPlayerUiEvent
import cc.kafuu.bilidownload.feature.compose.viewmodel.musicplayer.MusicPlayerUiIntent
import cc.kafuu.bilidownload.feature.compose.viewmodel.musicplayer.MusicPlayerViewModel
import java.io.File

class MusicPlayerActivity : CoreCompActivity() {
    companion object {
        private const val KEY_FILE_PATH = "file_path"
        private const val KEY_TITLE = "title"
        private const val KEY_MIME_TYPE = "mime_type"
        private const val KEY_CONTENT_URI = "content_uri"

        /**
         * 创建音乐播放器参数 Intent。
         *
         * [contentUri] 是首选播放地址，[filePath] 仍用于离线频谱分析和旧记录回退。
         */
        fun buildIntent(
            filePath: String,
            title: String,
            mimeType: String = "audio/*",
            contentUri: String? = null
        ) =
            Intent().apply {
                putExtra(KEY_FILE_PATH, filePath)
                putExtra(KEY_TITLE, title)
                putExtra(KEY_MIME_TYPE, mimeType)
                putExtra(KEY_CONTENT_URI, contentUri)
            }
    }

    private val mViewModel by viewModels<MusicPlayerViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val filePath = intent.getStringExtra(KEY_FILE_PATH) ?: run { finish(); return }
        val title = intent.getStringExtra(KEY_TITLE) ?: ""
        val mimeType = intent.getStringExtra(KEY_MIME_TYPE) ?: "audio/*"
        val contentUri = intent.getStringExtra(KEY_CONTENT_URI)
        mViewModel.emit(
            MusicPlayerUiIntent.Init(applicationContext, filePath, title, mimeType, contentUri)
        )
    }

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()

        MusicPlayerLayout(uiState) { mViewModel.emit(it) }

        LaunchedEffect(Unit) {
            mViewModel.collectEvent { event ->
                when (event) {
                    MusicPlayerUiEvent.Finish -> finish()
                    is MusicPlayerUiEvent.OpenWithOtherPlayer -> onOpenWithOtherPlayer(event)
                }
            }
        }
    }

    private fun onOpenWithOtherPlayer(event: MusicPlayerUiEvent.OpenWithOtherPlayer) {
        val opened = FileUtils.tryOpenFileWithOtherApp(
            context = this,
            title = event.title,
            file = File(event.filePath),
            mimetype = event.mimeType,
            contentUri = event.contentUri
        )
        if (!opened) {
            Toast.makeText(this, R.string.no_external_player_message, Toast.LENGTH_SHORT).show()
        }
    }
}

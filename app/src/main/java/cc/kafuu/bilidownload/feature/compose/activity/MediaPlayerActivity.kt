package cc.kafuu.bilidownload.feature.compose.activity

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.core.compose.CoreCompActivity
import cc.kafuu.bilidownload.common.model.AppModel
import cc.kafuu.bilidownload.common.utils.FileUtils
import cc.kafuu.bilidownload.feature.compose.layout.MediaPlayerLayout
import cc.kafuu.bilidownload.feature.compose.viewmodel.mediaplayer.MediaPlayerUiEvent
import cc.kafuu.bilidownload.feature.compose.viewmodel.mediaplayer.MediaPlayerUiIntent
import cc.kafuu.bilidownload.feature.compose.viewmodel.mediaplayer.MediaPlayerUiState
import cc.kafuu.bilidownload.feature.compose.viewmodel.mediaplayer.MediaPlayerViewModel
import java.io.File

class MediaPlayerActivity : CoreCompActivity() {
    companion object {
        private const val KEY_FILE_PATH = "file_path"
        private const val KEY_TITLE = "title"
        private const val KEY_MIME_TYPE = "mime_type"
        private const val KEY_CONTENT_URI = "content_uri"

        private const val MIN_PICTURE_IN_PICTURE_ASPECT_RATIO = 1.0 / 2.39
        private const val MAX_PICTURE_IN_PICTURE_ASPECT_RATIO = 2.39

        /**
         * 创建视频播放器参数 Intent。
         *
         * [contentUri] 是首选播放地址，[filePath] 保留用于兼容旧记录和外部播放器回退。
         */
        fun buildIntent(
            filePath: String,
            title: String,
            mimeType: String = "video/*",
            contentUri: String? = null
        ) = Intent().apply {
            putExtra(KEY_FILE_PATH, filePath)
            putExtra(KEY_TITLE, title)
            putExtra(KEY_MIME_TYPE, mimeType)
            putExtra(KEY_CONTENT_URI, contentUri)
        }
    }

    private val mViewModel by viewModels<MediaPlayerViewModel>()
    private var mPlayerBounds: Rect? = null
    private var mIsInPictureInPictureMode by mutableStateOf(false)
    private var mSuppressAutoEnterPictureInPicture = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this) {
            mViewModel.emit(MediaPlayerUiIntent.GoBack)
        }
        addOnPictureInPictureModeChangedListener {
            mIsInPictureInPictureMode = it.isInPictureInPictureMode
        }
        initializePlayer(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        initializePlayer(intent)
    }

    override fun onStart() {
        super.onStart()
        mSuppressAutoEnterPictureInPicture = false
        mIsInPictureInPictureMode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            isInPictureInPictureMode
        updatePictureInPictureParams(mViewModel.uiStateFlow.value)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.O until Build.VERSION_CODES.S) {
            tryEnterPictureInPicture()
        }
    }

    override fun onStop() {
        val isCurrentlyInPictureInPictureMode = Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O &&
            (mIsInPictureInPictureMode || isInPictureInPictureMode)
        if (!isChangingConfigurations && !isCurrentlyInPictureInPictureMode) {
            mViewModel.emit(MediaPlayerUiIntent.Pause)
        }
        super.onStop()
    }

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()
        val playingState = uiState as? MediaPlayerUiState.Playing

        MediaPlayerLayout(
            state = uiState,
            isInPictureInPictureMode = mIsInPictureInPictureMode,
            onPlayerBoundsChanged = ::onPlayerBoundsChanged
        ) { mViewModel.emit(it) }

        LaunchedEffect(
            playingState?.isPlaying,
            playingState?.videoWidth,
            playingState?.videoHeight
        ) {
            updatePictureInPictureParams(uiState)
        }

        LaunchedEffect(Unit) {
            mViewModel.collectEvent { event ->
                when (event) {
                    MediaPlayerUiEvent.RequestPictureInPicture -> {
                        if (!tryEnterPictureInPicture()) finish()
                    }
                    is MediaPlayerUiEvent.SetFullScreen -> onSetFullScreen(event.isFullScreen)
                    is MediaPlayerUiEvent.OpenWithOtherPlayer -> onOpenWithOtherPlayer(event)
                }
            }
        }
    }

    private fun initializePlayer(intent: Intent) {
        val filePath = intent.getStringExtra(KEY_FILE_PATH) ?: run { finish(); return }
        val title = intent.getStringExtra(KEY_TITLE) ?: ""
        val mimeType = intent.getStringExtra(KEY_MIME_TYPE) ?: "video/*"
        val contentUri = intent.getStringExtra(KEY_CONTENT_URI)
        mViewModel.emit(
            MediaPlayerUiIntent.Init(applicationContext, filePath, title, mimeType, contentUri)
        )
    }

    private fun onPlayerBoundsChanged(bounds: Rect) {
        if (mPlayerBounds == bounds) return
        mPlayerBounds = Rect(bounds)
        updatePictureInPictureParams(mViewModel.uiStateFlow.value)
    }

    private fun updatePictureInPictureParams(state: MediaPlayerUiState) {
        if (!isPictureInPictureSupported()) return
        runCatching {
            setPictureInPictureParams(createPictureInPictureParams(state))
        }
    }

    private fun tryEnterPictureInPicture(): Boolean {
        val state = mViewModel.uiStateFlow.value
        if (!shouldEnterPictureInPicture(state)) return false
        val entered = runCatching {
            enterPictureInPictureMode(createPictureInPictureParams(state))
        }.getOrDefault(false)
        if (entered) mIsInPictureInPictureMode = true
        return entered
    }

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O)
    private fun shouldEnterPictureInPicture(state: MediaPlayerUiState): Boolean {
        return isPictureInPictureSupported() &&
            !mSuppressAutoEnterPictureInPicture &&
            AppModel.pictureInPicturePlaybackEnabled &&
            (state as? MediaPlayerUiState.Playing)?.isPlaying == true
    }

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O)
    private fun isPictureInPictureSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createPictureInPictureParams(
        state: MediaPlayerUiState
    ): PictureInPictureParams {
        val playingState = state as? MediaPlayerUiState.Playing
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(
                calculatePictureInPictureAspectRatio(
                    width = playingState?.videoWidth ?: 0,
                    height = playingState?.videoHeight ?: 0
                )
            )

        mPlayerBounds?.takeUnless(Rect::isEmpty)?.let(builder::setSourceRectHint)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder
                .setAutoEnterEnabled(shouldEnterPictureInPicture(state))
                .setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun calculatePictureInPictureAspectRatio(width: Int, height: Int): Rational {
        if (width <= 0 || height <= 0) return Rational(16, 9)
        val aspectRatio = width.toDouble() / height
        return when {
            aspectRatio < MIN_PICTURE_IN_PICTURE_ASPECT_RATIO -> Rational(100, 239)
            aspectRatio > MAX_PICTURE_IN_PICTURE_ASPECT_RATIO -> Rational(239, 100)
            else -> Rational(width, height)
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
        mSuppressAutoEnterPictureInPicture = true
        updatePictureInPictureParams(mViewModel.uiStateFlow.value)
        val opened = FileUtils.tryOpenFileWithOtherApp(
            context = this,
            title = event.title,
            file = File(event.filePath),
            mimetype = event.mimeType,
            contentUri = event.contentUri
        )
        if (!opened) {
            mSuppressAutoEnterPictureInPicture = false
            updatePictureInPictureParams(mViewModel.uiStateFlow.value)
            Toast.makeText(this, R.string.no_external_player_message, Toast.LENGTH_SHORT).show()
        }
    }
}

package cc.kafuu.bilidownload.service

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import cc.kafuu.bilidownload.common.audio.MediaPlayerFactory
import cc.kafuu.bilidownload.common.audio.MusicPlaybackRequest
import cc.kafuu.bilidownload.common.audio.MusicPlaybackRuntime
import cc.kafuu.bilidownload.common.audio.spectrum.RealtimeAudioRenderersFactory
import cc.kafuu.bilidownload.feature.compose.activity.MusicPlayerActivity

/** 托管音频播放器和媒体会话，使播放不依赖 Activity 生命周期。 */
@OptIn(UnstableApi::class)
class MusicPlaybackService : MediaSessionService() {
    private var mMediaSession: MediaSession? = null
    private var mPlayer: ExoPlayer? = null

    private val mPlayerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            mediaItem?.let(::updateSessionActivity)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
        val mediaSourceFactory = DefaultMediaSourceFactory(this, extractorsFactory)
        val player = MediaPlayerFactory.configure(
            ExoPlayer.Builder(this)
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .setMediaSourceFactory(mediaSourceFactory)
                .setRenderersFactory(
                    RealtimeAudioRenderersFactory(
                        context = this,
                        analyzer = MusicPlaybackRuntime.realtimeSpectrumAnalyzer
                    )
                )
        ).build().also {
            mPlayer = it
            it.addListener(mPlayerListener)
        }
        mMediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession? = mMediaSession

    override fun onDestroy() {
        mPlayer?.removeListener(mPlayerListener)
        mMediaSession?.run {
            player.release()
            release()
        }
        mPlayer = null
        mMediaSession = null
        MusicPlaybackRuntime.realtimeSpectrumAnalyzer.clear()
        super.onDestroy()
    }

    private fun updateSessionActivity(mediaItem: MediaItem) {
        val request = MusicPlaybackRequest.fromMediaItem(mediaItem) ?: return
        val intent = MusicPlayerActivity.buildIntent(
            filePath = request.filePath,
            title = request.title,
            mimeType = request.mimeType,
            contentUri = request.contentUri
        ).apply {
            setClass(this@MusicPlaybackService, MusicPlayerActivity::class.java)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            request.mediaId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        mMediaSession?.setSessionActivity(pendingIntent)
    }
}

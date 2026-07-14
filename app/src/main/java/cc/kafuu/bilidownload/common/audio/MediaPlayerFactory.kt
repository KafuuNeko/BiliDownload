package cc.kafuu.bilidownload.common.audio

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer

/** 为音频和视频播放器提供一致的系统音频行为配置。 */
object MediaPlayerFactory {
    private val mediaAudioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

    /**
     * 配置媒体用途的音频属性，并让播放器自动处理音频焦点和耳机拔出事件。
     *
     * @return 传入的构建器，便于继续链式配置。
     */
    fun configure(builder: ExoPlayer.Builder): ExoPlayer.Builder {
        return builder
            .setAudioAttributes(mediaAudioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
    }
}

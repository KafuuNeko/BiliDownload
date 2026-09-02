package cc.kafuu.bilidownload.common.audio

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import java.io.File

/** 音频播放服务与界面之间共享的播放参数。 */
data class MusicPlaybackRequest(
    val filePath: String,
    val title: String,
    val mimeType: String,
    val contentUri: String?
) {
    val mediaId: String
        get() = contentUri ?: Uri.fromFile(File(filePath)).toString()

    fun toMediaItem(): MediaItem {
        val extras = Bundle().apply {
            putString(KEY_FILE_PATH, filePath)
            putString(KEY_MIME_TYPE, mimeType)
            putString(KEY_CONTENT_URI, contentUri)
        }
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(mediaId)
            .setMimeType(mimeType.takeUnless { it.endsWith("/*") })
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsPlayable(true)
                    .setExtras(extras)
                    .build()
            )
            .build()
    }

    companion object {
        private const val KEY_FILE_PATH = "music_file_path"
        private const val KEY_MIME_TYPE = "music_mime_type"
        private const val KEY_CONTENT_URI = "music_content_uri"

        fun fromMediaItem(mediaItem: MediaItem): MusicPlaybackRequest? {
            val extras = mediaItem.mediaMetadata.extras ?: return null
            val filePath = extras.getString(KEY_FILE_PATH) ?: return null
            return MusicPlaybackRequest(
                filePath = filePath,
                title = mediaItem.mediaMetadata.title?.toString().orEmpty(),
                mimeType = extras.getString(KEY_MIME_TYPE) ?: "audio/*",
                contentUri = extras.getString(KEY_CONTENT_URI)
            )
        }
    }
}

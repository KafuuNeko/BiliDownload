package cc.kafuu.bilidownload.common.model.av

import java.io.Serializable

enum class AVFormat(
    val suffix: String,
    val mimeType: String,
    val videoSupportCodecs: List<AVCodec>?,
    val audioSupportCodecs: List<AVCodec>?
) : Serializable {
    AVI(
        "avi", "video/x-msvideo",
        listOf(AVCodec.H264, AVCodec.H265, AVCodec.VP8, AVCodec.VP9, AVCodec.AV1),
        listOf(AVCodec.AAC, AVCodec.MP3)
    ),

    FLV(
        "flv", "video/x-flv",
        listOf(AVCodec.H264),
        listOf(AVCodec.AAC, AVCodec.MP3)
    ),

    MKV(
        "mkv", "video/x-matroska",
        listOf(AVCodec.H264, AVCodec.H265, AVCodec.VP8, AVCodec.VP9, AVCodec.AV1),
        listOf(AVCodec.AAC, AVCodec.MP3, AVCodec.FLAC, AVCodec.OPUS)
    ),

    MOV(
        "mov", "video/quicktime",
        listOf(AVCodec.H264, AVCodec.H265, AVCodec.VP8, AVCodec.VP9, AVCodec.AV1),
        listOf(AVCodec.AAC, AVCodec.MP3, AVCodec.ALAC)
    ),

    MP4(
        "mp4", "video/mp4",
        listOf(AVCodec.H264, AVCodec.H265, AVCodec.VP8, AVCodec.VP9, AVCodec.AV1),
        listOf(AVCodec.AAC, AVCodec.MP3, AVCodec.ALAC, AVCodec.FLAC, AVCodec.AC3)
    ),

    WEBM(
        "webm", "video/webm",
        listOf(AVCodec.VP8, AVCodec.VP9, AVCodec.AV1),
        listOf(AVCodec.VORBIS, AVCodec.OPUS)
    ),

    MP3(
        "mp3", "audio/mp3",
        null,
        listOf(AVCodec.MP3)
    ),

    AAC(
        "aac", "audio/aac",
        null,
        listOf(AVCodec.AAC)
    ),

    ADTS(
        "adts", "audio/aac",
        null,
        listOf(AVCodec.AAC)
    ),

    AIFF(
        "aiff", "audio/aiff",
        null,
        listOf(AVCodec.AAC, AVCodec.ALAC, AVCodec.MP3)
    ),

    AMR(
        "amr", "audio/amr",
        null,
        listOf(AVCodec.AMR)
    ),

    FLAC(
        "flac", "audio/flac",
        null,
        listOf(AVCodec.FLAC)
    ),

    OGG(
        "ogg", "audio/ogg",
        null,
        listOf(AVCodec.VORBIS, AVCodec.OPUS, AVCodec.AC3)
    );

    companion object {
        fun fromFilePath(path: String): AVFormat? {
            val suffix = path.substring(path.indexOfLast { it == '.' } + 1).lowercase()
            return entries.firstOrNull { it.suffix == suffix }
        }
    }
}
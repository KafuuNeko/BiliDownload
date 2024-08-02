package cc.kafuu.bilidownload.common.constant.av.formt

import cc.kafuu.bilidownload.common.constant.av.codec.AudioCodec
import cc.kafuu.bilidownload.common.constant.av.codec.VideoCodec

enum class AudioVideoFormat(
    val suffix: String,
    val videoSupportCodecs: List<VideoCodec>,
    val audioSupportCodecs: List<AudioCodec>
) {
    AVI(
        "avi",
        listOf(VideoCodec.H264, VideoCodec.H265, VideoCodec.VP8, VideoCodec.VP9, VideoCodec.AV1),
        listOf(AudioCodec.AAC, AudioCodec.MP3)
    ),

    AVIF(
        "avif",
        listOf(VideoCodec.AV1),
        listOf(AudioCodec.AAC)
    ),

    MKV(
        "mkv",
        listOf(VideoCodec.H264, VideoCodec.H265, VideoCodec.VP8, VideoCodec.VP9, VideoCodec.AV1),
        listOf(AudioCodec.AAC, AudioCodec.MP3, AudioCodec.FLAC, AudioCodec.OPUS)
    ),

    MOV(
        "mov",
        listOf(VideoCodec.H264, VideoCodec.H265, VideoCodec.VP8, VideoCodec.VP9, VideoCodec.AV1),
        listOf(AudioCodec.AAC, AudioCodec.MP3, AudioCodec.ALAC)
    ),

    MP4(
        "mp4",
        listOf(VideoCodec.H264, VideoCodec.H265, VideoCodec.VP8, VideoCodec.VP9, VideoCodec.AV1),
        listOf(AudioCodec.AAC, AudioCodec.MP3, AudioCodec.ALAC, AudioCodec.FLAC, AudioCodec.AC3)
    ),

    MPEG(
        "mpeg",
        listOf(VideoCodec.H264, VideoCodec.H265, VideoCodec.VP8, VideoCodec.VP9, VideoCodec.AV1),
        listOf(AudioCodec.AAC, AudioCodec.MP3)
    ),

    WEBM(
        "webm",
        listOf(VideoCodec.VP8, VideoCodec.VP9, VideoCodec.AV1),
        listOf(AudioCodec.VORBIS, AudioCodec.OPUS)
    )
}

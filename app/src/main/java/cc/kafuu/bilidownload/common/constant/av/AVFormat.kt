package cc.kafuu.bilidownload.common.constant.av

enum class AVFormat(
    val suffix: String,
    val videoSupportCodecs: List<VideoCodec>?,
    val audioSupportCodecs: List<AudioCodec>?
) {
    AVI(
        "avi",
        listOf(VideoCodec.H264, VideoCodec.H265, VideoCodec.VP8, VideoCodec.VP9, VideoCodec.AV1),
        listOf(AudioCodec.AAC, AudioCodec.MP3)
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
    ),

    AAC(
        "aac",
        null,
        listOf(AudioCodec.AAC)
    ),

    ADTS(
        "adts",
        null,
        listOf(AudioCodec.AAC)
    ),

    AIFF(
        "aiff",
        null,
        listOf(AudioCodec.AAC, AudioCodec.ALAC, AudioCodec.MP3)
    ),

    AMR(
        "amr",
        null,
        listOf(AudioCodec.AMR)
    ),

    FLAC(
        "flac",
        null,
        listOf(AudioCodec.FLAC)
    ),

    OGG(
        "ogg",
        null,
        listOf(AudioCodec.VORBIS, AudioCodec.OPUS, AudioCodec.AC3)
    )
}

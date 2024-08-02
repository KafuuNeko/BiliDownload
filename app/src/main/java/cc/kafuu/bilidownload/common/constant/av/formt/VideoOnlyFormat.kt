package cc.kafuu.bilidownload.common.constant.av.formt

import cc.kafuu.bilidownload.common.constant.av.codec.VideoCodec

enum class VideoOnlyFormat(val suffix: String, val supportCodecs: List<VideoCodec>) {
    AVI(
        "avi",
        listOf(VideoCodec.H264, VideoCodec.H265, VideoCodec.VP8, VideoCodec.VP9, VideoCodec.AV1)
    ),

    AVIF(
        "avif",
        listOf(VideoCodec.AV1)
    ),

    MKV(
        "mkv",
        listOf(VideoCodec.H264, VideoCodec.H265, VideoCodec.VP8, VideoCodec.VP9, VideoCodec.AV1)
    ),

    MOV(
        "mov",
        listOf(VideoCodec.H264, VideoCodec.H265, VideoCodec.VP8, VideoCodec.VP9, VideoCodec.AV1)
    ),

    MP4(
        "mp4",
        listOf(VideoCodec.H264, VideoCodec.H265, VideoCodec.VP8, VideoCodec.VP9, VideoCodec.AV1)
    ),

    MPEG(
        "mpeg",
        listOf(VideoCodec.H264, VideoCodec.H265, VideoCodec.VP8, VideoCodec.VP9, VideoCodec.AV1)
    ),

    WEBM(
        "webm",
        listOf(VideoCodec.VP8, VideoCodec.VP9, VideoCodec.AV1)
    )
}

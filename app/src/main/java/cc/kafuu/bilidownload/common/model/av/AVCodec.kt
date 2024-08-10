package cc.kafuu.bilidownload.common.model.av

import cc.kafuu.bilidownload.common.constant.MediaSteamType
import java.io.Serializable

enum class AVCodec(val fullName: String, @MediaSteamType val codecType: Int) : Serializable {
    // 音频编码
    AAC("aac", MediaSteamType.AUDIO),
    AC3("ac3", MediaSteamType.AUDIO),
    ALAC("alac", MediaSteamType.AUDIO),
    FLAC("flac", MediaSteamType.AUDIO),
    MP3("libmp3lame", MediaSteamType.AUDIO),
    OPUS("libopus", MediaSteamType.AUDIO),
    VORBIS("libvorbis", MediaSteamType.AUDIO),
    PCM("pcm_s16le", MediaSteamType.AUDIO),

    // 视频编码
    H264("libx264", MediaSteamType.VIDEO),
    H265("libx265", MediaSteamType.VIDEO),
    VP8("libvpx", MediaSteamType.VIDEO),
    VP9("libvpx-vp9", MediaSteamType.VIDEO),
    AV1("libaom-av1", MediaSteamType.VIDEO),
    Theora("libtheora", MediaSteamType.VIDEO);

    companion object {
        fun fromCodecName(name: String): AVCodec? = when (name.lowercase()) {
            "aac" -> AAC
            "ac3", "a52" -> AC3
            "alac" -> ALAC
            "flac" -> FLAC
            "mp3", "libmp3lame" -> MP3
            "opus", "libopus" -> OPUS
            "vorbis", "ogg", "libvorbis" -> VORBIS
            "avc", "h264", "x264", "libx264" -> H264
            "hevc", "h265", "x265", "libx265" -> H265
            "vp8", "libvpx" -> VP8
            "vp9", "libvpx-vp9" -> VP9
            "av1", "libaom-av1" -> AV1
            "theora", "libtheora" -> Theora
            "pcm", "pcm_s16le" -> PCM
            else -> null
        }
    }
}
package cc.kafuu.bilidownload.common.constant.av.codec

enum class AudioCodec(fullName: String) {
    AAC("aac"),
    AC3("ac3"),
    ALAC("alac"),
    FLAC("flac"),
    AMR("libopencore_amrnb"),
    MP3("libmp3lame"),
    OPUS("libopus"),
    VORBIS("libvorbis")
}
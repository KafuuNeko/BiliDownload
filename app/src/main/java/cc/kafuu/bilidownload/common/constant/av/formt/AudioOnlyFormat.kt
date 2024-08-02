package cc.kafuu.bilidownload.common.constant.av.formt

import cc.kafuu.bilidownload.common.constant.av.codec.AudioCodec

enum class AudioOnlyFormat(val suffix: String, val supportCodecs: List<AudioCodec>) {
    AAC(
        "aac",
        listOf(AudioCodec.AAC)
    ),

    ADTS(
        "adts",
        listOf(AudioCodec.AAC)
    ),

    AIFF(
        "aiff",
        listOf(AudioCodec.AAC, AudioCodec.ALAC, AudioCodec.MP3)
    ),

    AMR(
        "amr",
        listOf(AudioCodec.AMR)
    ),

    FLAC(
        "flac",
        listOf(AudioCodec.FLAC)
    ),

    MP4(
        "mp4",
        listOf(AudioCodec.AAC, AudioCodec.MP3, AudioCodec.ALAC, AudioCodec.FLAC, AudioCodec.AC3)
    ),

    OGG(
        "ogg",
        listOf(AudioCodec.VORBIS, AudioCodec.OPUS, AudioCodec.AC3)
    )
}

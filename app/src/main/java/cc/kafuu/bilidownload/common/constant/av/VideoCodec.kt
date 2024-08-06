package cc.kafuu.bilidownload.common.constant.av

enum class VideoCodec(fullName: String) {
    H264("libx264"),
    H265("libx265"),
    VP8("libvpx"),
    VP9("libvpx-vp9"),
    AV1("libaom-av1")
}
package cc.kafuu.bilidownload.common.jniexport

object FFMpegJNI {
    init {
        System.loadLibrary("ffmpeg-lib")
    }

    external fun ffmpegInfo(): String?
    external fun videoFormatConversion(from: String?, to: String?): Int
    external fun extractAudio(from: String?, to: String?): Int
    external fun getVideoAudioFormat(from: String?): String?

    /**
     * 返回示例
     * {"bit_rate":279294,"code":0,"duration":261048000,"second":261,"streams":[{"name":"h264"},{"name":"aac"}]}
     * {"bit_rate":74223,"code":0,"duration":239617369,"second":239,"streams":[{"name":"aac"}]}
     */
    external fun getMediaInfo(from: String?): String?
}

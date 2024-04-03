package cc.kafuu.bilidownload.common.jniexport

object JniTools {
    init {
        System.loadLibrary("ffmpeg-lib")
    }

    external fun ffmpegInfo(): String?
    external fun videoFormatConversion(inFile: String?, outFile: String?): Int
    external fun extractAudio(inFile: String?, outFile: String?): Int
    external fun getVideoAudioFormat(inFile: String?): String?

    /**
     * 返回示例
     * {"bit_rate":279294,"code":0,"duration":261048000,"second":261,"streams":[{"name":"h264"},{"name":"aac"}]}
     * {"bit_rate":74223,"code":0,"duration":239617369,"second":239,"streams":[{"name":"aac"}]}
     */
    external fun getMediaInfo(filename: String?): String?
}

package cc.kafuu.bilidownload.common.network.model

import com.google.gson.annotations.SerializedName

/**
 * B站字幕JSON文件数据模型
 * 字幕文件格式示例：
 * {
 *   "font_size": 0.4,
 *   "font_color": "#FFFFFF",
 *   "background_alpha": 0.5,
 *   "background_color": "#9C27B0",
 *   "type": "AIsubtitle",
 *   "lang": "zh",
 *   "version": "v1.7.0.4",
 *   "body": [...]
 * }
 */
data class BiliSubtitleData(
    @SerializedName("font_size")
    val fontSize: Float? = null,

    @SerializedName("font_color")
    val fontColor: String? = null,

    @SerializedName("background_alpha")
    val backgroundAlpha: Float? = null,

    @SerializedName("background_color")
    val backgroundColor: String? = null,

    @SerializedName("Stroke")
    val stroke: String? = null,

    @SerializedName("type")
    val type: String? = null,

    @SerializedName("lang")
    val lang: String? = null,

    @SerializedName("version")
    val version: String? = null,

    @SerializedName("body")
    val body: List<BiliSubtitleLine>
)

/**
 * 单条字幕数据
 */
data class BiliSubtitleLine(
    /**
     * 开始时间（秒）
     */
    @SerializedName("from")
    val from: Float,

    /**
     * 结束时间（秒）
     */
    @SerializedName("to")
    val to: Float,

    /**
     * 字幕序号
     */
    @SerializedName("sid")
    val sid: Int,

    /**
     * 位置（0=顶部，1=底部，2=滚动）
     */
    @SerializedName("location")
    val location: Int,

    /**
     * 字幕文本内容
     */
    @SerializedName("content")
    val content: String,

    /**
     * 音乐相关参数
     */
    @SerializedName("music")
    val music: Float? = null
) {
    /**
     * 获取格式化的开始时间（SRT格式）
     */
    fun getFormattedStartTime(): String {
        return formatSrtTime(from)
    }

    /**
     * 获取格式化的结束时间（SRT格式）
     */
    fun getFormattedEndTime(): String {
        return formatSrtTime(to)
    }

    /**
     * 将秒数转换为SRT时间格式 (HH:MM:SS,mmm)
     */
    private fun formatSrtTime(seconds: Float): String {
        val totalSeconds = seconds.toInt()
        val milliseconds = ((seconds - totalSeconds) * 1000).toInt()

        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val secs = totalSeconds % 60

        return String.format("%d:%02d:%02d,%03d", hours, minutes, secs, milliseconds)
    }
}

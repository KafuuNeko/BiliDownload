package cc.kafuu.bilidownload.common.network.model

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * B站历史弹幕索引数据
 * 对应API: https://api.bilibili.com/x/v2/dm/history/index
 */
data class BiliHistoryDanmakuIndex(
    val code: Int,
    val message: String,
    val data: List<String>?
) {
    companion object {
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        /**
         * 从JsonObject解析
         */
        fun fromJson(json: JsonObject): BiliHistoryDanmakuIndex {
            val data = json.get("data")?.takeIf { it.isJsonArray }?.asJsonArray?.map { it.asString }
            return BiliHistoryDanmakuIndex(
                code = json.get("code")?.asInt ?: -1,
                message = json.get("message")?.asString ?: "",
                data = data
            )
        }

        /**
         * 判断是否成功
         */
        fun isSuccess(code: Int): Boolean {
            return code == 0
        }
    }

    /**
     * 判断当前响应是否成功
     */
    fun isSuccess(): Boolean = isSuccess(code)

    /**
     * 获取日期范围（用于进度显示）
     */
    fun getDateRange(): Pair<String, String>? {
        val dates = data ?: return null
        if (dates.isEmpty()) return null
        return dates.first() to (dates.lastOrNull() ?: dates.first())
    }
}

/**
 * 历史弹幕下载进度
 */
data class HistoryDanmakuProgress(
    val totalDates: Int,
    val completedDates: Int,
    val currentDate: String?,
    val totalDanmaku: Int
) {
    fun getProgress(): Int {
        if (totalDates == 0) return 0
        return (completedDates * 100 / totalDates)
    }

    fun getCurrentDateDisplay(): String {
        return currentDate ?: "准备中..."
    }
}

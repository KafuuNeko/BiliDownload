package cc.kafuu.bilidownload.model

import android.annotation.SuppressLint
import java.text.SimpleDateFormat
import java.util.Date

data class BiliVideo(
    val author: String,
    val authorId: Long,
    val bvid: String,
    val title: String,
    val description: String,
    val pic: String,
    val pubDate: Long,
    val sendDate: Long,
    val duration: String,
) {
    @SuppressLint("SimpleDateFormat")
    companion object {
        val mDateFormatter by lazy { SimpleDateFormat("yyyy-MM-dd") }
    }

    fun getPubFormatterDate(): String {
        return mDateFormatter.format(Date(pubDate * 1000))
    }

    fun getSendFormatterDate(): String {
        return mDateFormatter.format(Date(sendDate * 1000))
    }
}
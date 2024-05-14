package cc.kafuu.bilidownload.common.model.bili

import android.annotation.SuppressLint
import java.text.SimpleDateFormat
import java.util.Date

class BiliVideoModel(
    title: String,
    cover: String,
    description: String,
    pubDate: Long,
    val author: String,
    val bvid: String,
    val sendDate: Long,
    val duration: String,
) : BiliResourceModel(title, cover, description, pubDate) {
    @SuppressLint("SimpleDateFormat")
    companion object {
        val mDateFormatter by lazy { SimpleDateFormat("yyyy-MM-dd") }
    }

    fun getSendFormatterDate(): String {
        return mDateFormatter.format(Date(sendDate * 1000))
    }
}
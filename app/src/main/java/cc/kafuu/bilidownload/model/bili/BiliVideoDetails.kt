package cc.kafuu.bilidownload.model.bili

import android.annotation.SuppressLint
import java.text.SimpleDateFormat
import java.util.Date

class BiliVideoDetails(
    title: String,
    cover: String,
    description: String,
    author: String,
    pubDate: Long,
    val authorId: Long,
    val bvid: String,
    val sendDate: Long,
    val duration: String,
) : BiliResourceDetails(title, cover, description, author, pubDate) {
    @SuppressLint("SimpleDateFormat")
    companion object {
        val mDateFormatter by lazy { SimpleDateFormat("yyyy-MM-dd") }
    }

    fun getSendFormatterDate(): String {
        return mDateFormatter.format(Date(sendDate * 1000))
    }
}
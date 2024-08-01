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
    val duration: String,
) : BiliResourceModel(title, cover, description, pubDate) {
    @SuppressLint("SimpleDateFormat")
    companion object {
        val mDateFormatter by lazy { SimpleDateFormat("yyyy-MM-dd") }
    }
}
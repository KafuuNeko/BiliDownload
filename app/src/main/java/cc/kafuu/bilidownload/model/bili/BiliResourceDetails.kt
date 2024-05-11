package cc.kafuu.bilidownload.model.bili

import java.io.Serializable
import java.util.Date

abstract class BiliResourceDetails(
    val title: String,
    val cover: String,
    val description: String,
    val author: String,
    val pubDate: Long,
): Serializable {
    fun getPubFormatterDate(): String {
        return BiliVideoDetails.mDateFormatter.format(Date(pubDate * 1000))
    }
}
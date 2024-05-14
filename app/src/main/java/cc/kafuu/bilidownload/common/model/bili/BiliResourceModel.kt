package cc.kafuu.bilidownload.common.model.bili

import java.io.Serializable
import java.util.Date

abstract class BiliResourceModel(
    val title: String,
    val cover: String,
    val description: String,
    val pubDate: Long,
): Serializable {
    fun getPubFormatterDate(): String {
        return BiliVideoModel.mDateFormatter.format(Date(pubDate * 1000))
    }
}
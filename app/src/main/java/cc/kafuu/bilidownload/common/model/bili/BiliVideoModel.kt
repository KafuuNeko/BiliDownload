package cc.kafuu.bilidownload.common.model.bili

import android.annotation.SuppressLint
import cc.kafuu.bilidownload.common.network.model.BiliFavoriteMedia
import cc.kafuu.bilidownload.common.network.model.BiliHistoryItem
import cc.kafuu.bilidownload.common.network.model.BiliSearchManuscriptVideo
import cc.kafuu.bilidownload.common.network.model.BiliSearchVideoResultData
import cc.kafuu.bilidownload.common.network.model.BiliVideoData
import cc.kafuu.bilidownload.common.utils.BvConvertUtils
import cc.kafuu.bilidownload.common.utils.TimeUtils
import java.text.SimpleDateFormat

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

        fun create(data: BiliVideoData) = BiliVideoModel(
            author = data.owner.name,
            bvid = data.bvid,
            title = data.title,
            description = data.desc,
            cover = data.pic,
            pubDate = data.pubDate,
            duration = TimeUtils.formatDuration(data.duration.toDouble())
        )

        fun create(data: BiliHistoryItem): BiliVideoModel? {
            return BiliVideoModel(
                title = data.title,
                cover = data.cover ?: return null,
                description = data.longTitle,
                pubDate = data.viewAt,
                author = data.authorName,
                bvid = data.history.bvid ?: return null,
                duration = TimeUtils.formatDuration(data.duration?.toDouble() ?: 0.0)
            )
        }

        fun create(data: BiliSearchVideoResultData) = BiliVideoModel(
            author = data.author,
            bvid = data.bvid,
            title = data.title,
            description = data.description,
            cover = "https:${data.pic}",
            pubDate = data.pubDate,
            duration = data.duration.let {
                val time = it.split(":")
                val minute = time.getOrNull(0)?.toIntOrNull() ?: 0
                val second = time.getOrNull(1)?.toIntOrNull() ?: 0
                TimeUtils.formatDuration((minute * 60 + second).toDouble())
            }
        )

        fun create(data: BiliFavoriteMedia) = BiliVideoModel(
            title = data.title,
            bvid = data.bvid ?: BvConvertUtils.av2bv(data.id.toString()),
            cover = data.cover,
            description = data.intro,
            pubDate = data.pubTime,
            author = data.upper.name,
            duration = TimeUtils.formatDuration(data.duration.toDouble())
        )

        fun create(data: BiliSearchManuscriptVideo) = BiliVideoModel(
            title = data.title,
            bvid = data.bvid,
            cover = data.pic,
            description = data.description,
            pubDate = data.created,
            author = data.author,
            duration = data.length
        )
    }
}
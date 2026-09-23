package cc.kafuu.bilidownload.common.model.bili

import android.annotation.SuppressLint
import cc.kafuu.bilidownload.common.network.model.BiliFavoriteMedia
import cc.kafuu.bilidownload.common.network.model.BiliHistoryItem
import cc.kafuu.bilidownload.common.network.model.BiliLikeVideoData
import cc.kafuu.bilidownload.common.network.model.BiliSearchManuscriptVideo
import cc.kafuu.bilidownload.common.network.model.BiliSearchVideoResultData
import cc.kafuu.bilidownload.common.network.model.BiliVideoData
import cc.kafuu.bilidownload.common.network.model.BiliWatchLaterItem
import cc.kafuu.bilidownload.common.utils.BvConvertUtils
import cc.kafuu.bilidownload.common.utils.TimeUtils
import java.text.SimpleDateFormat

/** 普通稿件的列表与详情跳转参数，统计快照不参与多选对象身份。 */
class BiliVideoModel(
    title: String,
    cover: String,
    description: String,
    pubDate: Long,
    val author: String,
    val bvid: String,
    val duration: String,
    /**
     * 列表来源建议优先下载的分 P。
     *
     * 目前仅观看历史会提供该值，收藏、点赞、搜索和投稿列表保持为空，
     * 由批量下载流程默认选择第一个分 P。
     */
    val preferredCid: Long? = null,
    /** 列表接口提供的初始快照；异步补齐由页面状态单独保存，保持条目身份稳定。 */
    val stats: VideoStats = VideoStats(),
) : BiliResourceModel(title, cover, description, pubDate) {
    @SuppressLint("SimpleDateFormat")
    companion object {
        val mDateFormatter by lazy { SimpleDateFormat("yyyy-MM-dd") }

        /** 保留详情接口统计，BV/AV 直达结果无需再次补齐。 */
        fun create(data: BiliVideoData) = BiliVideoModel(
            author = data.owner.name,
            bvid = data.bvid,
            title = data.title,
            description = data.desc,
            cover = data.pic,
            pubDate = data.pubDate,
            duration = TimeUtils.formatDuration(data.duration.toDouble()),
            stats = data.stat?.toVideoStats() ?: VideoStats(),
        )

        /** 观看记录保留分 P 身份，账号收藏状态不当作统计数量。 */
        fun create(data: BiliHistoryItem): BiliVideoModel? {
            return BiliVideoModel(
                title = data.title,
                cover = data.cover ?: return null,
                description = data.longTitle,
                pubDate = data.viewAt,
                author = data.authorName,
                bvid = data.history.bvid ?: return null,
                duration = TimeUtils.formatDuration(data.duration?.toDouble() ?: 0.0),
                preferredCid = data.history.cid
            )
        }

        /** 直接映射搜索返回的计数，缺少的字段保留未知。 */
        fun create(data: BiliSearchVideoResultData) = BiliVideoModel(
            author = data.author,
            bvid = data.bvid,
            title = data.title,
            description = data.description,
            cover = "https:${data.pic}",
            pubDate = data.pubDate,
            stats = VideoStats(
                view = data.play, like = data.like, favorite = data.favorites,
                danmaku = data.videoReview, reply = data.review,
            ).normalized(),
            duration = data.duration.let {
                val time = it.split(":")
                val minute = time.getOrNull(0)?.toIntOrNull() ?: 0
                val second = time.getOrNull(1)?.toIntOrNull() ?: 0
                TimeUtils.formatDuration((minute * 60 + second).toDouble())
            }
        )

        /** 收藏条目使用稿件收藏总数，缺失点赞留待统一补齐。 */
        fun create(data: BiliFavoriteMedia) = BiliVideoModel(
            title = data.title,
            bvid = data.bvid ?: BvConvertUtils.av2bv(data.id.toString()),
            cover = data.cover,
            description = data.intro,
            pubDate = data.pubTime,
            author = data.upper.name,
            duration = TimeUtils.formatDuration(data.duration.toDouble()),
            stats = data.countInfo?.toVideoStats() ?: VideoStats(),
        )

        /** 投稿的 comment 才是评论数量，review 不作为统计。 */
        fun create(data: BiliSearchManuscriptVideo) = BiliVideoModel(
            title = data.title,
            bvid = data.bvid,
            cover = data.pic,
            description = data.description,
            pubDate = data.created,
            author = data.author,
            duration = data.length,
            stats = VideoStats(view = data.play, danmaku = data.videoReview, reply = data.comment).normalized(),
        )

        /** 最近点赞列表优先使用内嵌的稿件统计。 */
        fun create(data: BiliLikeVideoData) = BiliVideoModel(
            title = data.title,
            bvid = data.bvid,
            cover = data.pic,
            description = data.description,
            pubDate = data.pubDate,
            author = data.owner.name,
            duration = TimeUtils.formatDuration(data.duration.toDouble()),
            stats = data.stat?.toVideoStats() ?: VideoStats(),
        )

        /** 稍后再看兼容缺少 BV 与内嵌统计的响应，保留默认分 P。 */
        fun create(data: BiliWatchLaterItem): BiliVideoModel? {
            val bvid = data.bvid?.takeIf { it.isNotBlank() }
                ?: data.aid.takeIf { it > 0L }?.let { BvConvertUtils.av2bv(it.toString()) }
                ?: return null
            return BiliVideoModel(
                title = data.title.orEmpty(),
                bvid = bvid,
                cover = data.cover.orEmpty(),
                description = data.description.orEmpty(),
                pubDate = data.addAt.takeIf { it > 0L } ?: data.pubDate,
                author = data.owner?.name.orEmpty(),
                duration = TimeUtils.formatDuration(data.duration.toDouble()),
                preferredCid = data.cid?.takeIf { it > 0L },
                stats = data.stat?.toVideoStats() ?: VideoStats(),
            )
        }
    }
}

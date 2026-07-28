package cc.kafuu.bilidownload.common.model.bili

import cc.kafuu.bilidownload.common.network.model.BiliHistoryItem
import cc.kafuu.bilidownload.common.network.model.BiliSearchMediaResultData
import cc.kafuu.bilidownload.common.network.model.BiliSeasonData

class BiliMediaModel(
    title: String,
    cover: String,
    description: String,
    pubDate: Long,
    val mediaId: Long,
    val seasonId: Long,
    /**
     * 从观看历史进入时对应的剧集 ID。
     *
     * 搜索结果没有具体观看剧集，因此保持为空并默认选择第一集。
     */
    val preferredEpisodeId: Long? = null,
): BiliResourceModel(title, cover, description, pubDate) {
    companion object {
        fun create(data: BiliSeasonData) = BiliMediaModel(
            title = data.title,
            cover = data.cover,
            description = data.evaluate,
            pubDate = data.episodes.firstOrNull()?.pubTime ?: 0,
            mediaId = data.mediaId,
            seasonId = data.seasonId
        )

        fun create(data: BiliHistoryItem): BiliMediaModel? {
            return BiliMediaModel(
                title = data.title,
                cover = data.cover ?: return null,
                description = data.longTitle,
                pubDate = data.viewAt,
                mediaId = data.history.epid ?: return null,
                seasonId = 0,
                preferredEpisodeId = data.history.epid,
            )
        }

        fun create(data: BiliSearchMediaResultData) = BiliMediaModel(
            title = data.title,
            cover = data.cover,
            description = data.desc,
            pubDate = data.pubTime,
            mediaId = data.mediaId,
            seasonId = data.seasonId
        )
    }
}

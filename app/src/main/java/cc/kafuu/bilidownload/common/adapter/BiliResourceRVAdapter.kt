package cc.kafuu.bilidownload.common.adapter

import android.content.Context
import android.view.ViewGroup
import cc.kafuu.bilidownload.common.core.viewbinding.CoreRVHolder
import cc.kafuu.bilidownload.common.model.bili.VideoStats
import cc.kafuu.bilidownload.common.adapter.holder.ItemBiliMediaHolder
import cc.kafuu.bilidownload.common.adapter.holder.ItemBiliVideoHolder
import cc.kafuu.bilidownload.common.core.viewbinding.CoreRVAdapter
import cc.kafuu.bilidownload.common.model.bili.BiliMediaModel
import cc.kafuu.bilidownload.common.model.bili.BiliVideoModel
import cc.kafuu.bilidownload.common.constant.BiliArchiveViewType
import cc.kafuu.bilidownload.feature.viewbinding.viewmodel.common.BiliResourceRVViewModel

/** 绑定资源卡片，统计使用独立 payload 更新，保留条目和多选身份。 */
class BiliResourceRVAdapter(viewModel: BiliResourceRVViewModel, context: Context) :
    CoreRVAdapter<BiliResourceRVViewModel>(viewModel, context) {

    private var mVideoStats: Map<String, VideoStats> = emptyMap()

    /** 读取可见位置上的普通视频；番剧、越界及空列表返回 null。 */
    fun getVideoAt(position: Int): BiliVideoModel? = mDataList?.getOrNull(position) as? BiliVideoModel

    /** 仅通知统计变化的条目，同一 BV 的多个出现位置都更新。 */
    fun updateVideoStats(stats: Map<String, VideoStats>) {
        val previous = mVideoStats
        mVideoStats = stats
        mDataList.orEmpty().forEachIndexed { index, item ->
            if (item is BiliVideoModel && previous[item.bvid] != stats[item.bvid]) {
                notifyItemChanged(index, STATS_PAYLOAD)
            }
        }
    }

    override fun onBindViewHolder(holder: CoreRVHolder<*>, position: Int) {
        super.onBindViewHolder(holder, position)
        bindVideoStats(holder, position)
    }

    override fun onBindViewHolder(holder: CoreRVHolder<*>, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty() && payloads.all { it == STATS_PAYLOAD }) {
            bindVideoStats(holder, position)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    private fun bindVideoStats(holder: CoreRVHolder<*>, position: Int) {
        val video = getVideoAt(position) ?: return
        (holder as? ItemBiliVideoHolder)?.bindStats(video.stats.withFallback(mVideoStats[video.bvid]))
    }

    private companion object {
        const val STATS_PAYLOAD = "video_stats"
    }

    override fun getItemViewType(position: Int) = when(getItemData(position)) {
        is BiliVideoModel -> BiliArchiveViewType.VIDEO_VIEW
        is BiliMediaModel -> BiliArchiveViewType.MEDIA_VIEW
        else -> throw IllegalArgumentException("Unknown view type")
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = when(viewType) {
        BiliArchiveViewType.VIDEO_VIEW -> ItemBiliVideoHolder(parent)
        BiliArchiveViewType.MEDIA_VIEW-> ItemBiliMediaHolder(parent)
        else -> throw IllegalArgumentException("Unknown view type")
    }
}

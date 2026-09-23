package cc.kafuu.bilidownload.common.model.bili

import java.io.Serializable

/** 视频稿件的统计快照；null 表示未知，零表示接口明确返回零，可随视频参数跨页面传递。 */
data class VideoStats(
    val view: Long? = null,
    val like: Long? = null,
    val favorite: Long? = null,
    val danmaku: Long? = null,
    val reply: Long? = null,
) : Serializable {
    /** 三项主要统计齐全时无需为列表发起详情请求。 */
    val isComplete: Boolean
        get() = view != null && like != null && favorite != null

    /** 过滤接口的负数占位值，保留真实的零计数。 */
    fun normalized() = VideoStats(
        view = view?.takeIf { it >= 0 },
        like = like?.takeIf { it >= 0 },
        favorite = favorite?.takeIf { it >= 0 },
        danmaku = danmaku?.takeIf { it >= 0 },
        reply = reply?.takeIf { it >= 0 },
    )

    /** 仅补缺项；详情缓存不能覆盖当前列表已经提供的统计。 */
    fun withFallback(fallback: VideoStats?) = VideoStats(
        view = view ?: fallback?.view,
        like = like ?: fallback?.like,
        favorite = favorite ?: fallback?.favorite,
        danmaku = danmaku ?: fallback?.danmaku,
        reply = reply ?: fallback?.reply,
    )
}

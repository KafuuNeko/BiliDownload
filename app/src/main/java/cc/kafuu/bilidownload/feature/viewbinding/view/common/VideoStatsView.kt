package cc.kafuu.bilidownload.feature.viewbinding.view.common

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.model.bili.VideoStats
import cc.kafuu.bilidownload.common.utils.VideoCountFormatter
import kotlin.math.roundToInt

/** 紧凑统计行；根据真实文本宽度优先保留播放与点赞，收藏放不下时隐藏。 */
class VideoStatsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {
    private val viewCount = createCountView(R.drawable.ic_media_play)
    private val likeCount = createCountView(R.drawable.ic_video_like)
    private val favoriteCount = createCountView(R.drawable.ic_video_favorite)
    private var stats = VideoStats()

    init {
        orientation = HORIZONTAL
        addView(viewCount)
        addView(likeCount)
        addView(favoriteCount)
    }

    /** 每次绑定都重置全部计数与可访问文本，避免 Holder 复用残留上一个视频的值。 */
    fun render(value: VideoStats) {
        stats = value.normalized()
        bindCount(viewCount, stats.view, R.string.video_stats_views)
        bindCount(likeCount, stats.like, R.string.video_stats_likes)
        bindCount(favoriteCount, stats.favorite, R.string.video_stats_favorites)
        isVisible = stats.view != null || stats.like != null || stats.favorite != null
        requestLayout()
    }

    /** 测量自然宽度后决定是否隐藏收藏，大字体下仍为主要两项保留各自空间。 */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        favoriteCount.isVisible = stats.favorite != null
        val children = listOf(viewCount, likeCount, favoriteCount)
        children.forEach {
            (it.layoutParams as LayoutParams).apply {
                width = LayoutParams.WRAP_CONTENT
                weight = 0f
            }
            it.measure(MeasureSpec.UNSPECIFIED, heightMeasureSpec)
        }

        // 仅在父容器限制宽度时裁减次要项，旋转或退出多选后会重新恢复收藏。
        if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            val available = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
            if ((viewCount.isVisible || likeCount.isVisible) &&
                children.filter { it.isVisible }.sumOf { it.measuredWidth } > available
            ) {
                favoriteCount.isVisible = false
            }
            if (children.filter { it.isVisible }.sumOf { it.measuredWidth } > available) {
                children.filter { it.isVisible }.forEach {
                    (it.layoutParams as LayoutParams).apply {
                        width = 0
                        weight = 1f
                    }
                }
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun createCountView(icon: Int) = AppCompatTextView(context).apply {
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.size_text_card_sub))
        setTextColor(ContextCompat.getColor(context, R.color.general_text_color))
        setSingleLine()
        ellipsize = android.text.TextUtils.TruncateAt.END
        // 复用主题文本色着色图标，深色模式不额外维护一套图片。
        val drawable = ContextCompat.getDrawable(context, icon)
        val iconSize = dp(14)
        drawable?.setBounds(0, 0, iconSize, iconSize)
        setCompoundDrawablesRelative(drawable, null, null, null)
        TextViewCompat.setCompoundDrawableTintList(
            this, ColorStateList.valueOf(ContextCompat.getColor(context, R.color.general_text_color)),
        )
        compoundDrawablePadding = dp(2)
        setPaddingRelative(0, 0, dp(8), 0)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    private fun bindCount(view: AppCompatTextView, count: Long?, descriptionId: Int) {
        view.isVisible = count != null
        view.text = VideoCountFormatter.format(count, resources.configuration.locales[0])
        view.contentDescription = count?.let { context.getString(descriptionId, it.toString()) }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
}

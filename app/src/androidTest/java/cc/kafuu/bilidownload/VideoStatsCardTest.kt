package cc.kafuu.bilidownload

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.kafuu.bilidownload.common.adapter.BiliResourceRVAdapter
import cc.kafuu.bilidownload.common.adapter.holder.ItemBiliVideoHolder
import cc.kafuu.bilidownload.common.model.bili.BiliVideoModel
import cc.kafuu.bilidownload.common.model.bili.VideoStats
import cc.kafuu.bilidownload.feature.viewbinding.view.activity.VideoDetailsActivity
import cc.kafuu.bilidownload.feature.viewbinding.view.common.VideoStatsView
import cc.kafuu.bilidownload.feature.viewbinding.viewmodel.common.BiliResourceRVViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

/** 在真实 Android 测量与 DataBinding 环境中验证卡片复用、可用宽度和多选身份。 */
@RunWith(AndroidJUnit4::class)
class VideoStatsCardTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun recycledHolder_clearsOldStatsAndShowsKnownZero() = onMain {
        val context = context()
        val adapter = BiliResourceRVAdapter(BiliResourceRVViewModel(), context)
        val holder = ItemBiliVideoHolder(FrameLayout(context))
        adapter.setDataList(listOf(video(VideoStats(0, 0, 0)), video(VideoStats())))
        adapter.onBindViewHolder(holder, 0)
        holder.binding.executePendingBindings()
        assertTrue(holder.binding.videoStats.isVisible)
        assertEquals("0", (holder.binding.videoStats.getChildAt(0) as TextView).text.toString())
        adapter.onBindViewHolder(holder, 1)
        holder.binding.executePendingBindings()
        assertFalse(holder.binding.videoStats.isVisible)
        assertTrue(holder.binding.tvDescription.isVisible)
    }

    @Test
    fun detail_showsIncomingStatsWhileDetailRequestIsLoading() {
        val source = BiliVideoModel(
            title = "示例详情", cover = "", description = "测试详情统计", pubDate = 0,
            author = "示例作者", bvid = "BV0000000000", duration = "00:01:00",
        )
        val snapshot = VideoStats(view = 12345, like = 0, favorite = 678)
        val intent = VideoDetailsActivity.buildIntent(source, snapshot).apply {
            setClass(instrumentation.targetContext, VideoDetailsActivity::class.java)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val activity = instrumentation.startActivitySync(intent) as VideoDetailsActivity
        try {
            instrumentation.waitForIdleSync()
            onMain {
                val stats = activity.findViewById<VideoStatsView>(R.id.video_stats)
                assertTrue(
                    "统计行未显示：attached=${stats.isAttachedToWindow}, " +
                        "visible=${stats.isVisible}, header=${activity.findViewById<View>(R.id.video_header).isVisible}",
                    stats.isShown,
                )
                assertEquals("1.2万", (stats.getChildAt(0) as TextView).text.toString())
                assertEquals("0", (stats.getChildAt(1) as TextView).text.toString())
                assertEquals("678", (stats.getChildAt(2) as TextView).text.toString())
                assertTrue(stats.getChildAt(2).isShown)
            }
        } finally {
            onMain { activity.finish() }
        }
    }

    @Test
    fun statisticsPayload_preservesSelectionAndOriginalVideo() = onMain {
        val context = context()
        val model = BiliResourceRVViewModel()
        val video = video(VideoStats(view = 123))
        val adapter = BiliResourceRVAdapter(model, context)
        adapter.setDataList(listOf(video))
        val holder = ItemBiliVideoHolder(FrameLayout(context))
        adapter.onBindViewHolder(holder, 0)
        model.onResourceLongClick(video)
        adapter.updateVideoStats(mapOf(video.bvid to VideoStats(100, 20, 30)))
        adapter.onBindViewHolder(holder, 0, mutableListOf("video_stats"))
        holder.binding.executePendingBindings()
        assertTrue(model.multipleSelectItemsLiveData.value.orEmpty().contains(video))
        assertTrue(adapter.getVideoAt(0) === video)
        assertEquals("123", (holder.binding.videoStats.getChildAt(0) as TextView).text.toString())
        assertEquals("20", (holder.binding.videoStats.getChildAt(1) as TextView).text.toString())
    }

    @Test
    fun narrowWidth_hidesFavoriteAndRestoresItWhenSpaceReturns() = onMain {
        val context = context(fontScale = 2f)
        val view = VideoStatsView(context).apply { layoutDirection = View.LAYOUT_DIRECTION_LTR }
        view.render(VideoStats(123456, 5678, 9000))
        measure(view, 400, context)
        assertTrue(view.getChildAt(2).isVisible)
        measure(view, 120, context)
        assertTrue(view.getChildAt(0).isVisible)
        assertTrue(view.getChildAt(1).isVisible)
        assertFalse(view.getChildAt(2).isVisible)
        assertTrue(view.getChildAt(1).right <= view.width)
        measure(view, 400, context)
        assertTrue(view.getChildAt(2).isVisible)
    }

    @Test
    fun renderCards_lightDarkAndLargeFont() = onMain {
        listOf(
            Triple("light", false, 1f),
            Triple("dark", true, 1f),
            Triple("large-font", false, 2f),
        ).forEach { (name, dark, fontScale) ->
            val context = context(dark, fontScale)
            val sheet = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutDirection = View.LAYOUT_DIRECTION_LTR
                setBackgroundColor(ContextCompat.getColor(context, R.color.general_window_background_color))
            }
            val model = BiliResourceRVViewModel()
            val adapter = BiliResourceRVAdapter(model, context)
            val videos = listOf(video(VideoStats(123456, 5678, 2300)), video(VideoStats()), video(VideoStats(0, 0, 0)))
            adapter.setDataList(videos)
            model.onResourceLongClick(videos.first())

            // 使用本地占位封面和虚构内容，图片产物不依赖账号、网络或用户媒体。
            videos.indices.forEach { position ->
                val holder = ItemBiliVideoHolder(sheet)
                adapter.onBindViewHolder(holder, position)
                holder.binding.executePendingBindings()
                holder.binding.ivVideoCover.setImageResource(R.drawable.ic_2233)
                sheet.addView(holder.itemView)
            }
            measure(sheet, 320, context)
            assertTrue(sheet.height > 0)
            val bitmap = Bitmap.createBitmap(sheet.width, sheet.height, Bitmap.Config.ARGB_8888)
            sheet.draw(Canvas(bitmap))
            File(instrumentation.targetContext.filesDir, "issue63-$name.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }

    private fun context(dark: Boolean = false, fontScale: Float = 1f): Context {
        val base = instrumentation.targetContext
        val configuration = Configuration(base.resources.configuration).apply {
            this.fontScale = fontScale
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            setLocale(Locale.SIMPLIFIED_CHINESE)
        }
        return ContextThemeWrapper(base.createConfigurationContext(configuration), R.style.Theme_BiliDownload)
    }

    private fun measure(view: View, widthDp: Int, context: Context) {
        val width = (widthDp * context.resources.displayMetrics.density).roundToInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    private fun video(stats: VideoStats) = BiliVideoModel(
        title = "示例视频：长标题与统计信息展示", cover = "", description = "暂无统计时保留视频简介",
        pubDate = 0, author = "示例作者", bvid = "BV1xx411c7mD", duration = "00:03:04", stats = stats,
    )

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)
}

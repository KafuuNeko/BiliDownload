package cc.kafuu.bilidownload.common.notes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import cc.kafuu.bilidownload.common.network.model.BccItem
import cc.kafuu.bilidownload.common.network.model.BccSubtitle
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * 图文笔记生成器（新增功能）：把一集的字幕和 720P 视频流合成为横版 PDF 笔记。
 *
 * 排版规格与 bili-to-notes 桌面脚本保持一致：
 * - 横版页面，字幕逐行排（一条字幕一行，不合并）
 * - 按字幕中点时间截图，穿插在对应字幕行下方，图下小字标注时间段
 * - 截图裁掉顶部 25%（去掉课件标题栏/水印）
 * - 画面去重：相邻画面变化像素占比低于阈值时不再重复放图
 *
 * 本类只做本地文件处理（输入视频文件 + BCC 字幕，输出 PDF 文件），
 * 网络请求（字幕列表、播放流、低清流下载）由调用方负责。
 * 所有方法都是阻塞式的，必须在 IO 线程调用。
 */
object NotesPdfGenerator {

    data class Result(
        val lineCount: Int,
        val imageCount: Int,
        /** 视频时长与字幕结束时间差超过 5 秒时为 true，仅提示不对齐，不中断生成。 */
        val durationMismatch: Boolean
    )

    private const val PAGE_WIDTH = 842   // A4 横版 @72dpi
    private const val PAGE_HEIGHT = 595
    private const val MARGIN = 36f
    private const val CROP_TOP_RATIO = 0.25f
    private const val CHANGE_THRESHOLD = 0.003f  // 变化像素占比 0.3%
    private const val SIG_WIDTH = 128
    private const val SIG_HEIGHT = 72

    /**
     * 生成笔记 PDF。
     *
     * @param context 用于定位缓存目录（临时帧文件）
     * @param title 笔记标题（视频标题）
     * @param subtitle BCC 字幕数据（body 为逐条字幕）
     * @param videoFile 已下载到本地的视频文件（720P 流）
     * @param outFile 输出 PDF 文件（会被覆盖）
     */
    fun generate(
        context: Context,
        title: String,
        subtitle: BccSubtitle,
        videoFile: File,
        outFile: File
    ): Result {
        val items = subtitle.body.orEmpty().filter { it.content.isNotBlank() }
        require(items.isNotEmpty()) { "字幕为空" }
        require(videoFile.exists() && videoFile.length() > 0) { "视频文件不存在" }

        val frameDir = File(context.cacheDir, "notes_frames_${System.currentTimeMillis()}")
        frameDir.mkdirs()
        try {
            // 1) 逐条截图 + 去重，保留的帧以裁顶后的 JPEG 落盘，避免长视频内存爆炸
            val keptFrameByIndex = mutableMapOf<Int, File>()
            var lastSignature: IntArray? = null
            items.forEachIndexed { index, item ->
                val mid = (item.from + item.to) / 2.0
                val rawFrame = File(frameDir, "raw_$index.jpg")
                if (!extractFrame(videoFile, mid, rawFrame)) return@forEachIndexed
                val cropped = decodeCropped(rawFrame) ?: return@forEachIndexed
                rawFrame.delete()
                val signature = signatureOf(cropped)
                val changed = lastSignature?.let {
                    changedRatio(it, signature) > CHANGE_THRESHOLD
                } ?: true
                if (changed) {
                    val kept = File(frameDir, "kept_$index.jpg")
                    FileOutputStream(kept).use { out ->
                        cropped.compress(Bitmap.CompressFormat.JPEG, 85, out)
                    }
                    keptFrameByIndex[index] = kept
                    lastSignature = signature
                }
                cropped.recycle()
            }

            // 2) 时长对齐提示（字幕最后一条结束时间 vs 视频时长）
            val videoDuration = probeDurationSeconds(videoFile)
            val mismatch = videoDuration?.let { abs(it - items.last().to) > 5.0 } ?: false

            // 3) 排版写 PDF
            writePdf(
                title = title,
                items = items,
                keptFrameByIndex = keptFrameByIndex,
                outFile = outFile
            )
            return Result(
                lineCount = items.size,
                imageCount = keptFrameByIndex.size,
                durationMismatch = mismatch
            )
        } finally {
            frameDir.deleteRecursively()
        }
    }

    /** 用 FFmpegKit 在指定时间点抽一帧。-ss 放在 -i 前走快速定位。 */
    private fun extractFrame(videoFile: File, timeSec: Double, outFile: File): Boolean {
        val args = arrayOf(
            "-ss", String.format(Locale.US, "%.3f", timeSec),
            "-i", videoFile.absolutePath,
            "-frames:v", "1",
            "-q:v", "3",
            "-y", outFile.absolutePath
        )
        val session = FFmpegKit.executeWithArguments(args)
        return ReturnCode.isSuccess(session.returnCode) && outFile.exists() && outFile.length() > 0
    }

    /** 解码帧图并裁掉顶部 CROP_TOP_RATIO。返回的 Bitmap 由调用方负责 recycle。 */
    private fun decodeCropped(frameFile: File): Bitmap? {
        val bitmap = BitmapFactory.decodeFile(frameFile.absolutePath) ?: return null
        val cropPx = (bitmap.height * CROP_TOP_RATIO).toInt()
        if (cropPx <= 0 || cropPx >= bitmap.height) return bitmap
        val cropped = Bitmap.createBitmap(bitmap, 0, cropPx, bitmap.width, bitmap.height - cropPx)
        if (cropped !== bitmap) bitmap.recycle()
        return cropped
    }

    /** 把画面缩到 128x72 取像素签名，用于相邻画面变化比对。 */
    private fun signatureOf(bitmap: Bitmap): IntArray {
        val scaled = Bitmap.createScaledBitmap(bitmap, SIG_WIDTH, SIG_HEIGHT, true)
        val pixels = IntArray(SIG_WIDTH * SIG_HEIGHT)
        scaled.getPixels(pixels, 0, SIG_WIDTH, 0, 0, SIG_WIDTH, SIG_HEIGHT)
        if (scaled !== bitmap) scaled.recycle()
        return pixels
    }

    /** 两帧之间“明显变化”的像素占比（任一通道差 > 30 计为变化）。 */
    private fun changedRatio(a: IntArray, b: IntArray): Float {
        if (a.size != b.size) return 1f
        var changed = 0
        for (i in a.indices) {
            val pa = a[i]
            val pb = b[i]
            val dr = abs(Color.red(pa) - Color.red(pb))
            val dg = abs(Color.green(pa) - Color.green(pb))
            val db = abs(Color.blue(pa) - Color.blue(pb))
            if (max(dr, max(dg, db)) > 30) changed++
        }
        return changed.toFloat() / a.size
    }

    private fun probeDurationSeconds(videoFile: File): Double? = runCatching {
        val session = FFprobeKit.getMediaInformation(videoFile.absolutePath)
        session.mediaInformation?.duration?.toDoubleOrNull()
    }.getOrNull()

    private fun formatTime(sec: Double): String {
        val total = sec.toInt()
        return String.format(Locale.US, "%02d:%02d", total / 60, total % 60)
    }

    private fun writePdf(
        title: String,
        items: List<BccItem>,
        keptFrameByIndex: Map<Int, File>,
        outFile: File
    ) {
        val contentWidth = PAGE_WIDTH - MARGIN * 2

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            color = Color.rgb(0x1a, 0x1a, 0x2e)
        }
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12.5f
            color = Color.rgb(0x22, 0x22, 0x22)
        }
        val captionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            color = Color.rgb(0x88, 0x88, 0x88)
        }
        val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val document = PdfDocument()
        var pageNumber = 0
        var page: PdfDocument.Page? = null
        var canvas: Canvas? = null
        var y = MARGIN

        fun startPage() {
            pageNumber++
            val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            page = document.startPage(info)
            canvas = page!!.canvas
            y = MARGIN
        }

        fun finishPage() {
            page?.let { document.finishPage(it) }
            page = null
            canvas = null
        }

        /** 当前页剩余空间不足 height 时换页。 */
        fun ensure(height: Float) {
            if (y + height > PAGE_HEIGHT - MARGIN) {
                finishPage()
                startPage()
            }
        }

        fun drawTextBlock(
            content: String,
            paint: TextPaint,
            gapAfter: Float
        ) {
            val layout = StaticLayout.Builder
                .obtain(content, 0, content.length, paint, contentWidth.toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.15f)
                .setIncludePad(false)
                .build()
            ensure(layout.height.toFloat() + gapAfter)
            val c = canvas ?: return
            c.save()
            c.translate(MARGIN, y)
            layout.draw(c)
            c.restore()
            y += layout.height + gapAfter
        }

        try {
            startPage()
            drawTextBlock(title, titlePaint, gapAfter = 14f)

            items.forEachIndexed { index, item ->
                // 字幕逐行排：一条字幕一行
                drawTextBlock(item.content.trim(), textPaint, gapAfter = 5f)

                val frameFile = keptFrameByIndex[index] ?: return@forEachIndexed
                val bitmap = BitmapFactory.decodeFile(frameFile.absolutePath)
                    ?: return@forEachIndexed
                try {
                    var drawWidth = contentWidth
                    var drawHeight = drawWidth * bitmap.height / bitmap.width
                    val maxImageHeight = PAGE_HEIGHT - MARGIN * 2 - 30f
                    if (drawHeight > maxImageHeight) {
                        drawHeight = maxImageHeight
                        drawWidth = drawHeight * bitmap.width / bitmap.height
                    }
                    val caption = "⏱ ${formatTime(item.from)}–${formatTime(item.to)}" +
                        " · 截图取自 ${formatTime((item.from + item.to) / 2.0)}"
                    ensure(drawHeight + 16f + 12f)
                    val c = canvas ?: return@forEachIndexed
                    val left = MARGIN + (contentWidth - drawWidth) / 2f
                    val dest = RectF(left, y, left + drawWidth, y + drawHeight)
                    c.drawBitmap(bitmap, null, dest, bitmapPaint)
                    y += drawHeight + 3f
                    c.drawText(caption, left, y + 9f, captionPaint)
                    y += 16f + 8f
                } finally {
                    bitmap.recycle()
                }
            }
            finishPage()
            FileOutputStream(outFile).use { out -> document.writeTo(out) }
        } finally {
            runCatching { finishPage() }
            document.close()
        }
    }
}

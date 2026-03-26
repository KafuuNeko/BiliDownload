package cc.kafuu.bilidownload.common.utils

import cc.kafuu.bilidownload.common.network.model.BccSubtitle
import java.io.OutputStream
import java.io.OutputStreamWriter

/**
 * 字幕导出工具类
 * 用于将BCC字幕数据转换为SRT文件并导出
 */
object SubtitleExportUtils {

    /**
     * 将秒换算为 SRT 格式所需的时间轴：HH:mm:ss,SSS
     */
    private fun formatSrtTime(seconds: Double): String {
        val ms = (seconds * 1000).toLong()
        val h = ms / 3600000
        val m = (ms % 3600000) / 60000
        val s = (ms % 60000) / 1000
        val msLeft = ms % 1000
        return String.format("%02d:%02d:%02d,%03d", h, m, s, msLeft)
    }

    /**
     * 将 BCC 格式的字幕转换为 SRT 格式的字符串
     * @param bcc B站专有的BCC格式字幕模型
     * @return 转换后的SRT字符串
     */
    private fun convertBccToSrt(bcc: BccSubtitle): String {
        val srtBuilder = StringBuilder()
        bcc.body?.forEachIndexed { index, item ->
            srtBuilder.appendLine((index + 1).toString())
            srtBuilder.appendLine("${formatSrtTime(item.from)} --> ${formatSrtTime(item.to)}")
            srtBuilder.appendLine(item.content)
            srtBuilder.appendLine() // SRT 要求每一段以空行隔开
        }
        return srtBuilder.toString()
    }

    /**
     * 导出字幕内容到输出流
     */
    fun exportToSrt(bcc: BccSubtitle, outputStream: OutputStream) {
        val srtContent = convertBccToSrt(bcc)
        OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
            writer.write(srtContent)
            // 确保数据写入
            writer.flush()
        }
    }
}

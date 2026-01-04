package cc.kafuu.bilidownload.common.utils

import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.common.network.model.BiliXmlDanmaku
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 弹幕导出工具类
 * 用于将弹幕数据导出为CSV文件
 */
object DanmakuExportUtils {

    private const val CSV_SEPARATOR = ","

    /**
     * 将弹幕列表导出为CSV文件
     * @param danmakuList 弹幕列表
     * @param outputFile 输出文件
     * @return 是否导出成功
     */
    fun exportToCsv(danmakuList: List<BiliXmlDanmaku>, outputFile: File): Boolean {
        return try {
            FileOutputStream(outputFile).use { fos ->
                writeCsvBom(fos)
                writeCsvHeader(fos)
                writeCsvData(danmakuList, fos)
                fos.flush()
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 从XML字符串直接导出为CSV文件
     * @param xmlString 弹幕XML字符串
     * @param outputFile 输出文件
     * @return 是否导出成功
     */
    fun exportXmlToCsv(xmlString: String, outputFile: File): Boolean {
        return try {
            val danmakuList = BiliXmlDanmaku.parseFromXml(xmlString)
            exportToCsv(danmakuList, outputFile)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 将弹幕列表导出为CSV到指定的输出流
     * @param danmakuList 弹幕列表
     * @param outputStream 输出流
     * @return 是否导出成功
     */
    fun exportToCsv(danmakuList: List<BiliXmlDanmaku>, outputStream: OutputStream): Boolean {
        return try {
            outputStream.use { os ->
                writeCsvBom(os)
                writeCsvHeader(os)
                writeCsvData(danmakuList, os)
                os.flush()
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 写入UTF-8 BOM标记，确保Excel正确识别编码
     */
    private fun writeCsvBom(outputStream: OutputStream) {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        outputStream.write(bom)
    }

    /**
     * 写入CSV表头
     */
    private fun writeCsvHeader(outputStream: OutputStream) {
        val header = buildCsvRow(
            CommonLibs.getString(R.string.csv_header_index),
            CommonLibs.getString(R.string.csv_header_appear_time),
            CommonLibs.getString(R.string.csv_header_type),
            CommonLibs.getString(R.string.csv_header_font_size),
            CommonLibs.getString(R.string.csv_header_color),
            CommonLibs.getString(R.string.csv_header_send_timestamp),
            CommonLibs.getString(R.string.csv_header_pool),
            CommonLibs.getString(R.string.csv_header_sender_id),
            CommonLibs.getString(R.string.csv_header_danmaku_id),
            CommonLibs.getString(R.string.csv_header_content)
        )
        outputStream.write(header.toByteArray(Charsets.UTF_8))
    }

    /**
     * 写入弹幕数据
     */
    private fun writeCsvData(danmakuList: List<BiliXmlDanmaku>, outputStream: OutputStream) {
        danmakuList.forEachIndexed { index, danmaku ->
            val row = buildDanmakuRow(index + 1, danmaku)
            outputStream.write(row.toByteArray(Charsets.UTF_8))
        }
    }

    /**
     * 构建单条弹幕的CSV行
     */
    private fun buildDanmakuRow(index: Int, danmaku: BiliXmlDanmaku): String {
        return buildCsvRow(
            index.toString(),
            danmaku.getFormattedTime(),
            CommonLibs.getString(danmaku.getTypeDescription()),
            danmaku.fontSize.toString(),
            danmaku.getColorHexString(),
            formatTimestamp(danmaku.sendTimestamp),
            getPoolDescription(danmaku.pool),
            danmaku.senderHash,
            danmaku.danmakuId.toString(),
            escapeCsvField(danmaku.content)
        )
    }

    /**
     * 构建CSV行，自动添加换行符
     */
    private fun buildCsvRow(vararg fields: String): String {
        return fields.joinToString(CSV_SEPARATOR) + "\n"
    }

    /**
     * 转义CSV字段值，处理包含逗号、引号、换行符的内容
     */
    private fun escapeCsvField(field: String): String {
        val needsEscaping = field.contains(",") ||
                           field.contains("\"") ||
                           field.contains("\n") ||
                           field.contains("\r")
        return if (needsEscaping) {
            "\"${field.replace("\"", "\"\"")}\""
        } else {
            field
        }
    }

    /**
     * 格式化时间戳为可读时间
     */
    private fun formatTimestamp(timestamp: Long): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return dateFormat.format(Date(timestamp * 1000))
    }

    /**
     * 获取弹幕池的描述
     */
    private fun getPoolDescription(pool: Int): String {
        return when (pool) {
            0 -> CommonLibs.getString(R.string.danmaku_pool_normal)
            1 -> CommonLibs.getString(R.string.danmaku_pool_subtitle)
            2 -> CommonLibs.getString(R.string.danmaku_pool_special)
            else -> CommonLibs.getString(R.string.danmaku_pool_unknown)
        }
    }
}

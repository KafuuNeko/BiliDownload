package cc.kafuu.bilidownload.common.utils

import cc.kafuu.bilidownload.common.network.model.BiliSubtitleData
import com.google.gson.Gson
import java.io.File

/**
 * 字幕格式转换工具类
 * 用于将B站JSON格式字幕转换为SRT、VTT等常见格式
 */
object SubtitleConverter {

    private val gson = Gson()

    /**
     * 将B站JSON字幕字符串转换为SRT格式字符串
     * @param jsonString B站字幕JSON字符串
     * @return SRT格式字符串
     */
    fun convertJsonToSrt(jsonString: String): String {
        val subtitleData = parseFromJson(jsonString)
        return convertToSrt(subtitleData)
    }

    /**
     * 将B站JSON字幕字符串转换为VTT格式字符串
     * @param jsonString B站字幕JSON字符串
     * @return WebVTT格式字符串
     */
    fun convertJsonToVtt(jsonString: String): String {
        val subtitleData = parseFromJson(jsonString)
        return convertToVtt(subtitleData)
    }

    /**
     * 从JSON字符串解析字幕数据
     * @param jsonString JSON字符串
     * @return 字幕数据对象
     */
    fun parseFromJson(jsonString: String): BiliSubtitleData {
        return gson.fromJson(jsonString, BiliSubtitleData::class.java)
    }

    /**
     * 将字幕数据转换为SRT格式
     * @param subtitleData 字幕数据
     * @return SRT格式字符串
     */
    fun convertToSrt(subtitleData: BiliSubtitleData): String {
        val sb = StringBuilder()
        val lines = subtitleData.body

        for (i in lines.indices) {
            val line = lines[i]

            // 字幕序号（从1开始）
            sb.appendLine(i + 1)

            // 时间轴：开始时间 --> 结束时间
            sb.appendLine("${line.getFormattedStartTime()} --> ${line.getFormattedEndTime()}")

            // 字幕内容
            sb.appendLine(line.content)

            // 字幕之间空一行（最后一条不需要）
            if (i < lines.size - 1) {
                sb.appendLine()
            }
        }

        return sb.toString()
    }

    /**
     * 将字幕数据转换为WebVTT格式
     * @param subtitleData 字幕数据
     * @return WebVTT格式字符串
     */
    fun convertToVtt(subtitleData: BiliSubtitleData): String {
        val sb = StringBuilder()

        // WebVTT文件头
        sb.appendLine("WEBVTT")
        sb.appendLine()

        val lines = subtitleData.body
        for (i in lines.indices) {
            val line = lines[i]

            // 字幕序号（可选）
            sb.appendLine(i + 1)

            // 时间轴：开始时间 --> 结束时间（WebVTT使用.分隔毫秒）
            sb.appendLine("${line.getFormattedStartTime().replace(",", ".")} --> ${line.getFormattedEndTime().replace(",", ".")}")

            // 字幕内容
            sb.appendLine(line.content)

            // 字幕之间空一行（最后一条不需要）
            if (i < lines.size - 1) {
                sb.appendLine()
            }
        }

        return sb.toString()
    }

    /**
     * 将B站JSON字幕文件转换为SRT文件
     * @param jsonFile JSON字幕文件
     * @param srtFile 输出的SRT文件
     * @return 是否转换成功
     */
    fun convertJsonFileToSrt(jsonFile: File, srtFile: File): Boolean {
        return try {
            val jsonString = jsonFile.readText()
            val srtContent = convertJsonToSrt(jsonString)
            srtFile.writeText(srtContent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 将B站JSON字幕文件转换为VTT文件
     * @param jsonFile JSON字幕文件
     * @param vttFile 输出的VTT文件
     * @return 是否转换成功
     */
    fun convertJsonFileToVtt(jsonFile: File, vttFile: File): Boolean {
        return try {
            val jsonString = jsonFile.readText()
            val vttContent = convertJsonToVtt(jsonString)
            vttFile.writeText(vttContent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

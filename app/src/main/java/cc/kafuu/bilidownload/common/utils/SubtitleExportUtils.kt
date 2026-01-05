package cc.kafuu.bilidownload.common.utils

import java.io.File
import java.io.OutputStream
import java.text.Normalizer
import kotlin.text.Regex

/**
 * 字幕导出工具类
 * 用于将B站字幕数据导出为SRT、VTT等格式文件
 */
object SubtitleExportUtils {

    /**
     * 字幕导出格式
     */
    enum class ExportFormat {
        SRT,  // SubRip格式，最通用
        VTT   // WebVTT格式，用于Web
    }

    /**
     * 将字幕JSON字符串导出为SRT文件
     * @param jsonString 字幕JSON字符串
     * @param outputFile 输出文件
     * @return 是否导出成功
     */
    fun exportJsonToSrt(jsonString: String, outputFile: File): Boolean {
        return try {
            val srtContent = SubtitleConverter.convertJsonToSrt(jsonString)
            outputFile.writeText(srtContent, Charsets.UTF_8)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 将字幕JSON字符串导出为VTT文件
     * @param jsonString 字幕JSON字符串
     * @param outputFile 输出文件
     * @return 是否导出成功
     */
    fun exportJsonToVtt(jsonString: String, outputFile: File): Boolean {
        return try {
            val vttContent = SubtitleConverter.convertJsonToVtt(jsonString)
            outputFile.writeText(vttContent, Charsets.UTF_8)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 将字幕JSON字符串导出为SRT到指定的输出流
     * @param jsonString 字幕JSON字符串
     * @param outputStream 输出流
     * @return 是否导出成功
     */
    fun exportJsonToSrt(jsonString: String, outputStream: OutputStream): Boolean {
        return try {
            val srtContent = SubtitleConverter.convertJsonToSrt(jsonString)
            outputStream.use { os ->
                os.write(srtContent.toByteArray(Charsets.UTF_8))
                os.flush()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 将字幕JSON字符串导出为VTT到指定的输出流
     * @param jsonString 字幕JSON字符串
     * @param outputStream 输出流
     * @return 是否导出成功
     */
    fun exportJsonToVtt(jsonString: String, outputStream: OutputStream): Boolean {
        return try {
            val vttContent = SubtitleConverter.convertJsonToVtt(jsonString)
            outputStream.use { os ->
                os.write(vttContent.toByteArray(Charsets.UTF_8))
                os.flush()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 将字幕JSON字符串导出为指定格式到指定的输出流
     * @param jsonString 字幕JSON字符串
     * @param outputStream 输出流
     * @param format 导出格式（SRT或VTT）
     * @return 是否导出成功
     */
    fun exportJsonToFormat(
        jsonString: String,
        outputStream: OutputStream,
        format: ExportFormat
    ): Boolean {
        return when (format) {
            ExportFormat.SRT -> exportJsonToSrt(jsonString, outputStream)
            ExportFormat.VTT -> exportJsonToVtt(jsonString, outputStream)
        }
    }

    /**
     * 生成字幕文件名
     * @param videoTitle 视频标题
     * @param partName 分集名称（可选）
     * @param language 语言描述（如"中文（自动生成）"）
     * @param format 文件格式
     * @return 文件名（含扩展名）
     */
    fun generateSubtitleFileName(
        videoTitle: String,
        partName: String? = null,
        language: String,
        format: ExportFormat
    ): String {
        val baseName = if (partName.isNullOrBlank()) {
            removeIllegalFileNameCharacters(videoTitle)
        } else {
            val cleanTitle = removeIllegalFileNameCharacters(videoTitle)
            val cleanPart = removeIllegalFileNameCharacters(partName)
            "${cleanTitle}_${cleanPart}"
        }

        // 提取语言简称（如从"中文（自动生成）"提取"zh"）
        val langSuffix = extractLanguageSuffix(language)

        val extension = when (format) {
            ExportFormat.SRT -> "srt"
            ExportFormat.VTT -> "vtt"
        }

        return "${baseName}_${langSuffix}.$extension"
    }

    /**
     * 移除文件名中的非法字符
     * Windows不允许的字符：\ / : * ? " < > |
     * @param fileName 原始文件名
     * @return 清理后的文件名
     */
    private fun removeIllegalFileNameCharacters(fileName: String): String {
        // Windows不允许的字符
        val illegalChars = Regex("""[\\/:*?"<>|]""")
        val cleaned = fileName.replace(illegalChars, "_")

        // 移除控制字符
        val withoutControlChars = cleaned.filter { it.code >= 32 }

        // 规范化Unicode字符（如全角字符转半角）
        val normalized = Normalizer.normalize(withoutControlChars, Normalizer.Form.NFC)

        // 限制文件名长度（Windows文件名最大255字符，保留一些空间给扩展名）
        return normalized.take(200).trim()
    }

    /**
     * 从语言描述中提取语言简称
     * @param languageDoc 语言描述（如"中文（自动生成）"、"中文"）
     * @return 语言简称
     */
    private fun extractLanguageSuffix(languageDoc: String): String {
        return when {
            languageDoc.contains("中文") || languageDoc.contains("zh") -> "zh"
            languageDoc.contains("英文") || languageDoc.contains("en") -> "en"
            languageDoc.contains("日文") || languageDoc.contains("ja") -> "ja"
            languageDoc.contains("繁体") || languageDoc.contains("zh-TW") -> "zh-TW"
            else -> "subtitle"
        }
    }
}
